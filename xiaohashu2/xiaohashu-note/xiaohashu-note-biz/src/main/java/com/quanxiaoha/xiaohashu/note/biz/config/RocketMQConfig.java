package com.quanxiaoha.xiaohashu.note.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/*
* RocketMQ 配置
* */
@Configuration
@Import(RocketMQAutoConfiguration.class)//手动引入自动配置类
public class RocketMQConfig {
}
