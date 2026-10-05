package com.quanxiaoha.xiaohashu.ai.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RocketMQ 配置
 *
 * rocketmq-spring-boot 2.2.3 只在 META-INF/spring.factories 里注册了自动配置类，
 * 而 Spring Boot 3.x 已不再读取 spring.factories 中的 EnableAutoConfiguration，
 * 所以必须像仓库其它模块一样，显式 @Import 引入。
 */
@Configuration
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {
}