package com.quanxiaoha.xiaohashu.notification.biz.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
public class NotificationStreamConfiguration {
    @Bean
    public RedisMessageListenerContainer notificationChanges(RedisConnectionFactory factory, NotificationStreamHub hub, ObjectMapper json) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            try {
                JsonNode value = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
                hub.changed(Long.parseLong(value.path("userId").asText()), Long.parseLong(value.path("revision").asText()),
                        Long.parseLong(value.path("lastSeq").asText()));
            } catch (Exception ex) { log.debug("Ignored invalid notification change hint"); }
        }, new ChannelTopic(NotificationChangePublisher.CHANNEL));
        return container;
    }
}
