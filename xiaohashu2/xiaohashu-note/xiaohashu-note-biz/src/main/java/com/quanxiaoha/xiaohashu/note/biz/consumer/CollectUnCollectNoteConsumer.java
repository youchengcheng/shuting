package com.quanxiaoha.xiaohashu.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.framework.notification.outbox.NotificationEventIsolationWriter;
import com.quanxiaoha.framework.notification.outbox.NotificationOutboxProperties;
import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteCollectionDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 笔记收藏、取消收藏 MQ 消费者
 */
@Component
@RocketMQMessageListener(consumerGroup = "xiaohashu_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, // Group 组
        topic = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, // 消费的主题 Topic
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
@Slf4j
public class CollectUnCollectNoteConsumer implements RocketMQListener<Message> {

    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private NotificationOutboxWriter notificationOutboxWriter;
    @Resource
    private NotificationOutboxProperties notificationOutboxProperties;
    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private NotificationEventIsolationWriter notificationEventIsolationWriter;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 幂等性: 通过联合唯一索引保证

        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();

        log.info("==> CollectUnCollectNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_COLLECT)) { // 收藏笔记
            handleCollectNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UN_COLLECT)) { // 取消收藏笔记
            handleUnCollectNoteTagMessage(bodyJsonStr);
        }
    }

    /**
     * 笔记收藏
     * @param bodyJsonStr
     */
    private void handleCollectNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO;
        try {
            collectUnCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);
            if (collectUnCollectNoteMqDTO == null) {
                throw new IllegalArgumentException("收藏消息内容不能为空");
            }
        } catch (Exception invalid) {
            if (!notificationOutboxProperties.getEvents().isEnabled()) {
                throw new IllegalArgumentException("互动源消息格式不正确", invalid);
            }
            notificationEventIsolationWriter.save(MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, bodyJsonStr, "Malformed collection payload");
            return;
        }

        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;

        // 用户ID
        Long userId = collectUnCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = collectUnCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = collectUnCollectNoteMqDTO.getCreateTime();
        // 通知启用后缺少源发生时间的消息不能用于生成通知，先持久化隔离
        if (notificationOutboxProperties.getEvents().isEnabled() && createTime == null) {
            notificationEventIsolationWriter.save(MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, bodyJsonStr, "Collection event has no source occurrence time");
            log.warn("Isolated collection event without source time: actorId={}, noteId={}", userId, noteId);
            return;
        }

        // 校验操作者、笔记 ID 和收藏类型，阻止错误身份进入通知链路
        if (notificationOutboxProperties.getEvents().isEnabled()
                && (userId == null || userId <= 0 || noteId == null || noteId <= 0 || !Objects.equals(type, 1))) {
            notificationEventIsolationWriter.save(MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, bodyJsonStr, "Invalid collection source identity or action");
            return;
        }

        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        // 添加或更新笔记收藏记录
        Integer insertedRows = transactionTemplate.execute(status -> {
            // 根据收藏状态变化判断是否产生通知，避免依赖数据库返回的影响行数
            boolean capture = notificationOutboxProperties.accepts(createTime);
            NoteCollectionDO previous = capture ? noteCollectionDOMapper.selectRelationForUpdate(userId, noteId) : null;
            int updated = noteCollectionDOMapper.insertOrUpdate(noteCollectionDO);
            if (capture && Objects.equals(type, 1)
                    && (previous == null || Objects.equals(previous.getStatus(), 0))) {
                Long creatorId = collectUnCollectNoteMqDTO.getNoteCreatorId();
                if (creatorId == null) {
                    NoteDO note = noteDOMapper.selectByPrimaryKey(noteId);
                    creatorId = note == null ? null : note.getCreatorId();
                }
                if (creatorId == null) {
                    // 历史消息无法查到笔记作者时，隔离通知事件
                    notificationEventIsolationWriter.save(MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, bodyJsonStr, "Legacy collection has no recoverable note owner");
                    log.warn("Isolated notification routing for legacy collection: actorId={}, noteId={}", userId, noteId);
                } else if (!Objects.equals(creatorId, userId)) {
                    notificationOutboxWriter.save(NotificationEvent.builder()
                            .producerService("xiaohashu-note")
                            .type("NOTE_COLLECT")
                            .businessKey(NotificationBusinessKey.of("NOTE_COLLECT", userId, noteId))
                            .actorId(userId)
                            .noteId(noteId)
                            .occurredAt(createTime)
                            .recipients(List.of(NotificationRecipient.builder()
                                    .userId(creatorId)
                                    .role("OWNER")
                                    .build()))
                            .build());
                }
            }
            return updated;
        });
        int count = insertedRows == null ? 0 : insertedRows;

        if (count == 0) return;

        // 更新数据库成功后，发送计数 MQ
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr)
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_COLLECT, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记收藏】MQ 发送异常: ", throwable);
            }
        });
    }


    /**
     * 笔记取消收藏
     * @param bodyJsonStr
     */
    private void handleUnCollectNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);

        if (Objects.isNull(unCollectNoteMqDTO)) return;

        // 用户ID
        Long userId = unCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = unCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = unCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = unCollectNoteMqDTO.getCreateTime();

        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        // 取消收藏：记录更新
        int count = noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(noteCollectionDO);

        if (count == 0) return;

        // 更新数据库成功后，发送计数 MQ
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr)
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_COLLECT, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记取消收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记取消收藏】MQ 发送异常: ", throwable);
            }
        });
    }
}
