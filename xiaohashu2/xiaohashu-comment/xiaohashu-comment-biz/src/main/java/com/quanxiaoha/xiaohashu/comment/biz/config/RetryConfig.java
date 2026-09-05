package com.quanxiaoha.xiaohashu.comment.biz.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/*
* spring retry 配置类
* */
@Configuration
public class RetryConfig {

    @Resource
    private RetryProperties retryProperties;

    @Bean
    public RetryTemplate retryTemplate(){
        RetryTemplate retryTemplate = new RetryTemplate();

        //定义重试策略（最多三次）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        //最大重试次数
        retryPolicy.setMaxAttempts(retryProperties.getMaxAttempts());

        //定义间隔策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        //初始间隔时间 2000ms
        backOffPolicy.setInitialInterval(retryProperties.getInitInterval());
        //每次间隔时间乘2
        backOffPolicy.setMultiplier(retryProperties.getMultiplier());

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

}
