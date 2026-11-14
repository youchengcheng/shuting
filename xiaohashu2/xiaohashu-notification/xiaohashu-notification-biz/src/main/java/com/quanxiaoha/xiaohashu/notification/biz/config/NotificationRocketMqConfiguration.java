package com.quanxiaoha.xiaohashu.notification.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// RocketMQ 2.2.x exposes spring.factories; Boot 3 requires this explicit import.
@Configuration
@Import(RocketMQAutoConfiguration.class)
public class NotificationRocketMqConfiguration {
}
