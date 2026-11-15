package com.quanxiaoha.xiaohashu.notification.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/*
 * RocketMQ 自动配置
 * RocketMQ 2.2.x 使用 spring.factories，在 Spring Boot 3 中需要显式导入
 * */
@Configuration
@Import(RocketMQAutoConfiguration.class)
public class NotificationRocketMqConfiguration {
}
