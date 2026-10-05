package com.quanxiaoha.xiaohashu.ai.biz.consumer;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.ai.biz.constant.AiConstants;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteOperateMessage;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 笔记操作 MQ 消费者：增量同步笔记检索索引
 *
 * <p>消费 note 服务的 NoteOperateTopic：发布 / 更新 → 重建该笔记索引；删除 → 删除索引。
 * 每日还有一次全量重建兜底，防止消息丢失导致索引与数据不一致。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "xiaohashu.ai.index", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_ai_consumer_group",
        topic = AiConstants.TOPIC_NOTE_OPERATE,
        selectorExpression = AiConstants.TAG_NOTE_PUBLISH + " || " + AiConstants.TAG_NOTE_DELETE + " || " + AiConstants.TAG_NOTE_UPDATE
)
public class NoteIndexSyncConsumer implements RocketMQListener<String> {

    @Resource
    private NoteIndexService noteIndexService;

    @Override
    public void onMessage(String message) {
        log.info("## AI 收到笔记操作消息: {}", message);
        NoteOperateMessage noteMessage;
        try {
            noteMessage = JsonUtils.parseObject(message, NoteOperateMessage.class);
        } catch (Exception e) {
            log.error("## 笔记操作消息解析失败: {}", message, e);
            return;
        }
        if (noteMessage == null || noteMessage.getNoteId() == null || noteMessage.getType() == null) {
            log.warn("## 笔记操作消息字段不完整，忽略: {}", message);
            return;
        }

        Long noteId = noteMessage.getNoteId();
        if (AiConstants.NOTE_OP_DELETE == noteMessage.getType()) {
            noteIndexService.deleteNote(noteId);
            return;
        }
        if (AiConstants.NOTE_OP_PUBLISH == noteMessage.getType() || AiConstants.NOTE_OP_UPDATE == noteMessage.getType()) {
            noteIndexService.indexNote(noteId);
            return;
        }
        log.warn("## 未知的笔记操作类型: {}", noteMessage.getType());
    }
}