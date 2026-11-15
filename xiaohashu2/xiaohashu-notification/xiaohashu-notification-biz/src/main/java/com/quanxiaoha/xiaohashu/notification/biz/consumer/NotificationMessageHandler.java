package com.quanxiaoha.xiaohashu.notification.biz.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationService;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationEventUtils;

import jakarta.annotation.Resource;

import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/*
 * 解析来源互动消息和已提交的通知事件
 * */
@Component
public class NotificationMessageHandler {

    /**
     * 消息内容解析工具
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 通知服务
     */
    @Resource
    private NotificationService notificationService;

    /**
     * 通知启用配置
     */
    @Resource
    private NotificationSettings notificationSettings;

    /*
     * 将笔记或评论点赞消息转换为通知事件
     * */
    public void rawLike(MessageExt message, boolean comment) {
        // 区分笔记点赞和评论点赞主题，保留原始消息供失败时隔离
        String source = comment ? "CommentLikeUnlikeTopic" : "LikeUnlikeTopic";
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        NotificationEvent event;
        try {
            // 解析消息对象和源发生时间，后续按同一启用边界过滤历史互动
            JsonNode value = parseTree(payload);
            LocalDateTime occurredAt = NotificationSettings.parseTime(text(value, "createTime"));
            // 历史评论点赞可能没有新增的定位字段，先按启用时间过滤
            if (occurredAt.isBefore(notificationSettings.cutoff())) {
                return;
            }
            // 取消点赞不产生通知，未知操作类型属于无法重试修复的消息
            String operation = text(value, "type");
            if ("0".equals(operation)) {
                return;
            }
            if (!"1".equals(operation)) {
                throw new IllegalArgumentException("点赞操作类型无效");
            }
            // 从原消息获取操作者、目标作者和笔记定位，逐项校验正整数 ID
            long actor = positiveId(value, "userId");
            long object = positiveId(value, comment ? "commentId" : "noteId");
            long owner = positiveId(value, comment ? "commentCreatorId" : "noteCreatorId");
            long note = comment ? positiveId(value, "noteId") : object;
            String type = comment ? "COMMENT_LIKE" : "NOTE_LIKE";
            // 构建稳定业务键和接收者，重投时复用同一互动身份
            event = NotificationEvent.builder()
                    .eventId(identity(source, payload))
                    .producerService(source)
                    .type(type)
                    .businessKey(NotificationBusinessKey.of(type, actor, object))
                    .actorId(actor)
                    .noteId(note)
                    .commentId(comment ? object : null)
                    .occurredAt(occurredAt)
                    .recipients(List.of(NotificationRecipient.builder()
                            .userId(owner)
                            .role("OWNER")
                            .build()))
                    .build();
            // 校验事件业务键、类型、接收者及定位字段之间的一致性
            NotificationEventUtils.validateEvent(event);
        } catch (IllegalArgumentException ex) {
            // 仅隔离确定无效的消息，隔离写入失败仍向外抛出供 MQ 重试
            notificationService.isolate(source, identity(source, payload), payload, ex.getMessage());
            return;
        }
        // 数据库及依赖异常交由 RocketMQ 重试，仅将确定无法处理的事件写入隔离表
        notificationService.consume(event);
    }

    /*
     * 处理来源业务事务已提交的通知事件
     * */
    public void committed(MessageExt message) {
        // 已提交事件来自独立主题，原始消息须保留用于幂等隔离
        String source = "NotificationEventTopic";
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        NotificationEvent event;
        try {
            // 先解析源时间，启用前的事件无需继续校验新增通知字段
            JsonNode value = parseTree(payload);
            LocalDateTime occurredAt = NotificationSettings.parseTime(text(value, "occurredAt"));
            if (occurredAt.isBefore(notificationSettings.cutoff())) {
                return;
            }
            // 将允许的源时间格式统一为事件 DTO 使用的日期格式
            ((ObjectNode) value).put("occurredAt",
                    occurredAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            try {
                event = JsonUtils.parseObject(value.toString(), NotificationEvent.class);
            } catch (Exception ex) {
                throw new IllegalArgumentException("已提交的通知事件内容无效", ex);
            }
            // 事件 ID 必须存在且不超过存储长度，避免无法追踪原始事件
            if (event == null || event.getEventId() == null || event.getEventId().isBlank() || event.getEventId().length() > 64) {
                throw new IllegalArgumentException("已提交的通知事件 ID 缺失或超长");
            }
            // 按通知类型校验事件应由哪个来源服务提交
            String producer = switch (event.getType() == null ? "" : event.getType()) {
                case "NOTE_COLLECT" -> "xiaohashu-note";
                case "COMMENT", "REPLY" -> "xiaohashu-comment";
                case "FOLLOW" -> "xiaohashu-user-relation";
                default -> throw new IllegalArgumentException("通知类型不属于已提交事件主题");
            };
            if (!producer.equals(event.getProducerService())) {
                throw new IllegalArgumentException("已提交的通知事件来源服务不匹配");
            }
            // 同时校验业务键、收件人角色及评论/笔记定位信息
            NotificationEventUtils.validateEvent(event);
        } catch (IllegalArgumentException ex) {
            // 永久无效的消息保存隔离记录，不能把数据库故障归为无效消息
            notificationService.isolate(source, identity(source, payload), payload, ex.getMessage());
            return;
        }
        // 校验完成后才写通知，事务或依赖故障继续向 MQ 传播以便重试
        notificationService.consume(event);
    }

    /*
     * 解析 JSON 消息并确认内容为对象
     * */
    private JsonNode parseTree(String payload) {
        try {
            // 消息必须为 JSON 对象，数组或标量无法按字段恢复互动身份
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException("通知事件内容必须为 JSON 对象");
            }
            return value;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("通知事件 JSON 格式不正确", ex);
        }
    }

    /*
     * 读取文本或整数类型的消息字段
     * */
    private static String text(JsonNode value, String key) {
        // 仅允许文本或整数表示，缺失、空值和其他 JSON 类型均判为无效
        JsonNode field = value.get(key);
        if (field == null || field.isNull() || !(field.isTextual() || field.isIntegralNumber())) {
            throw new IllegalArgumentException("通知字段缺失或类型无效：" + key);
        }
        return field.asText();
    }

    /*
     * 读取并校验正整数 ID
     * */
    private static long positiveId(JsonNode value, String key) {
        // ID 先按十进制格式限制，再校验正数及 long 范围
        String field = text(value, key);
        if (!field.matches("[0-9]{1,19}")) {
            throw new IllegalArgumentException("通知 ID 字段无效：" + key);
        }
        try {
            long id = Long.parseLong(field);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("通知 ID 字段无效：" + key);
        }
    }

    /*
     * 根据事件来源和原始消息生成稳定的事件标识
     * */
    private static String identity(String source, String payload) {
        try {
            // 来源和原始消息一起计算摘要，重复投递及隔离重试复用相同标识
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((source + "\n" + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法生成通知事件摘要标识", ex);
        }
    }
}
