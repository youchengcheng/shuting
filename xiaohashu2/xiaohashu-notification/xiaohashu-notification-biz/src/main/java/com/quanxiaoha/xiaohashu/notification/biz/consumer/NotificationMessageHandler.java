package com.quanxiaoha.xiaohashu.notification.biz.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationService;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class NotificationMessageHandler {
    private final ObjectMapper json;
    private final NotificationService notifications;
    private final NotificationSettings settings;

    public NotificationMessageHandler(ObjectMapper json, NotificationService notifications, NotificationSettings settings) {
        this.json = json;
        this.notifications = notifications;
        this.settings = settings;
    }

    public void rawLike(MessageExt message, boolean comment) {
        String source = comment ? "CommentLikeUnlikeTopic" : "LikeUnlikeTopic";
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        NotificationEvent event;
        try {
            JsonNode value = parseTree(payload);
            LocalDateTime occurredAt = NotificationSettings.parseTime(text(value, "createTime"));
            // Historical comment likes need not contain the new route fields.
            if (occurredAt.isBefore(settings.cutoff())) return;
            String operation = text(value, "type");
            if ("0".equals(operation)) return;
            if (!"1".equals(operation)) throw new IllegalArgumentException("Invalid like operation");
            long actor = positiveId(value, "userId");
            long object = positiveId(value, comment ? "commentId" : "noteId");
            long owner = positiveId(value, comment ? "commentCreatorId" : "noteCreatorId");
            long note = comment ? positiveId(value, "noteId") : object;
            String type = comment ? "COMMENT_LIKE" : "NOTE_LIKE";
            event = NotificationEvent.builder().eventId(identity(source, payload)).producerService(source)
                    .type(type).businessKey(NotificationBusinessKey.of(type, actor, object)).actorId(actor)
                    .noteId(note).commentId(comment ? object : null).occurredAt(occurredAt)
                    .recipients(List.of(NotificationRecipient.builder().userId(owner).role("OWNER").build())).build();
            NotificationService.validateEvent(event);
        } catch (IllegalArgumentException ex) {
            notifications.isolate(source, identity(source, payload), payload, ex.getMessage());
            return;
        }
        // DB/dependency failures must reach RocketMQ; only deterministic bad events are isolated above.
        notifications.consume(event);
    }

    public void committed(MessageExt message) {
        String source = "NotificationEventTopic";
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        NotificationEvent event;
        try {
            JsonNode value = parseTree(payload);
            LocalDateTime occurredAt = NotificationSettings.parseTime(text(value, "occurredAt"));
            if (occurredAt.isBefore(settings.cutoff())) return;
            ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("occurredAt",
                    occurredAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            try { event = JsonUtils.parseObject(value.toString(), NotificationEvent.class); }
            catch (Exception ex) { throw new IllegalArgumentException("Invalid committed notification payload", ex); }
            if (event == null || event.getEventId() == null || event.getEventId().isBlank() || event.getEventId().length() > 64) {
                throw new IllegalArgumentException("Missing committed event ID");
            }
            String producer = switch (event.getType() == null ? "" : event.getType()) {
                case "NOTE_COLLECT" -> "xiaohashu-note";
                case "COMMENT", "REPLY" -> "xiaohashu-comment";
                case "FOLLOW" -> "xiaohashu-user-relation";
                default -> throw new IllegalArgumentException("Type does not belong to the committed event topic");
            };
            if (!producer.equals(event.getProducerService())) throw new IllegalArgumentException("Unexpected committed event producer");
            NotificationService.validateEvent(event);
        } catch (IllegalArgumentException ex) {
            notifications.isolate(source, identity(source, payload), payload, ex.getMessage());
            return;
        }
        notifications.consume(event);
    }

    private JsonNode parseTree(String payload) {
        try {
            JsonNode value = json.readTree(payload);
            if (value == null || !value.isObject()) throw new IllegalArgumentException("Expected an event object");
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("Malformed event JSON", ex); }
    }

    private static String text(JsonNode value, String key) {
        JsonNode field = value.get(key);
        if (field == null || field.isNull() || !(field.isTextual() || field.isIntegralNumber())) throw new IllegalArgumentException("Missing or invalid " + key);
        return field.asText();
    }

    private static long positiveId(JsonNode value, String key) {
        String field = text(value, key);
        if (!field.matches("[0-9]{1,19}")) throw new IllegalArgumentException("Invalid " + key);
        try {
            long id = Long.parseLong(field);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid " + key); }
    }

    private static String identity(String source, String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((source + "\n" + payload).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
