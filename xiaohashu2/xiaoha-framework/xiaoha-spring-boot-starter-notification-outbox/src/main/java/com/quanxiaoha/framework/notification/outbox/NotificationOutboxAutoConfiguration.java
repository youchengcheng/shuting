package com.quanxiaoha.framework.notification.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration
@EnableConfigurationProperties(NotificationOutboxProperties.class)
public class NotificationOutboxAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(NotificationOutboxWriter.class)
    public NotificationOutboxWriter notificationOutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper,
             NotificationOutboxProperties properties, @Value("${spring.application.name}") String producerService) {
        return new JdbcNotificationOutboxWriter(jdbc, mapper, properties, producerService);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationEventIsolationWriter.class)
    public NotificationEventIsolationWriter notificationEventIsolationWriter(JdbcTemplate jdbc,
            NotificationOutboxProperties properties, @Value("${spring.application.name}") String producerService) {
        return new NotificationEventIsolationWriter(jdbc, properties, producerService);
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "notification.events.enabled", havingValue = "true")
    @EnableScheduling
    static class DispatchConfiguration {
        @Bean
        NotificationOutboxDispatcher notificationOutboxDispatcher(JdbcTemplate jdbc, PlatformTransactionManager manager,
                RocketMQTemplate rocketMQ, @Value("${spring.application.name}") String producerService,
                NotificationOutboxProperties properties) {
            properties.accepts(java.time.LocalDateTime.now()); // Refuse capture without an explicit cutoff.
            return new NotificationOutboxDispatcher(jdbc, new TransactionTemplate(manager), rocketMQ, producerService);
        }
    }
}
