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
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 通知 Outbox 自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(NotificationOutboxProperties.class)
public class NotificationOutboxAutoConfiguration {

    /*
     * 注册通知事件写入组件
     * */
    @Bean
    @ConditionalOnMissingBean(NotificationOutboxWriter.class)
    public NotificationOutboxWriter notificationOutboxWriter(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            NotificationOutboxProperties notificationOutboxProperties,
            @Value("${spring.application.name}") String producerService) {
        return new JdbcNotificationOutboxWriter(jdbcTemplate, objectMapper, notificationOutboxProperties, producerService);
    }

    /*
     * 注册异常通知事件写入组件
     * */
    @Bean
    @ConditionalOnMissingBean(NotificationEventIsolationWriter.class)
    public NotificationEventIsolationWriter notificationEventIsolationWriter(
            JdbcTemplate jdbcTemplate, NotificationOutboxProperties notificationOutboxProperties,
            @Value("${spring.application.name}") String producerService) {
        return new NotificationEventIsolationWriter(jdbcTemplate, notificationOutboxProperties, producerService);
    }

    /*
     * 开启事件采集后注册定时投递任务
     * */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "notification.events.enabled", havingValue = "true")
    @EnableScheduling
    static class DispatchConfiguration {

        /*
         * 注册通知投递组件，启动时检查通知启用时间
         * */
        @Bean
        NotificationOutboxDispatcher notificationOutboxDispatcher(
                JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                RocketMQTemplate rocketMQTemplate,
                @Value("${spring.application.name}") String producerService,
                NotificationOutboxProperties notificationOutboxProperties) {
            // 启用采集前必须配置明确的通知启用时间
            notificationOutboxProperties.accepts(LocalDateTime.now());
            return new NotificationOutboxDispatcher(jdbcTemplate, new TransactionTemplate(transactionManager),
                    rocketMQTemplate, producerService);
        }
    }
}
