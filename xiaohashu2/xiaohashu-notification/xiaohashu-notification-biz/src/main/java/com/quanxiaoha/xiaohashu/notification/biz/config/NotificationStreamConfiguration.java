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

/*
 * 通知状态变更监听和定时任务配置
 * */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
public class NotificationStreamConfiguration {

    /*
     * 监听 Redis 状态变更频道，将变更推送到当前实例的连接
     * */
    @Bean
    public RedisMessageListenerContainer notificationChanges(RedisConnectionFactory redisConnectionFactory,
                                                            NotificationStreamHub notificationStreamHub, ObjectMapper objectMapper) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        redisMessageListenerContainer.addMessageListener((message, pattern) -> {
            try {
                JsonNode value = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
                notificationStreamHub.changed(Long.parseLong(value.path("userId").asText()), Long.parseLong(value.path("revision").asText()),
                        Long.parseLong(value.path("lastSeq").asText()));
            } catch (Exception ex) {
                log.debug("Ignored invalid notification change hint");
            }
        }, new ChannelTopic(NotificationChangePublisher.CHANNEL));
        return redisMessageListenerContainer;
    }
}
