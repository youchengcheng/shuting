package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationUserState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
public class NotificationChangePublisher {
    public static final String CHANNEL = "notification:changed:v1";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public NotificationChangePublisher(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }

    public void publish(NotificationUserState state) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(Map.of("userId", Long.toString(state.getRecipientId()),
                    "revision", Long.toString(state.getRevision()), "lastSeq", Long.toString(state.getLastSeq()))));
        } catch (RuntimeException | JsonProcessingException ex) {
            // This runs after commit. Missing hints are recovered by the stream heartbeat and REST calibration.
            log.warn("Notification change hint unavailable, userId={}, revision={}", state.getRecipientId(), state.getRevision());
        }
    }
}
