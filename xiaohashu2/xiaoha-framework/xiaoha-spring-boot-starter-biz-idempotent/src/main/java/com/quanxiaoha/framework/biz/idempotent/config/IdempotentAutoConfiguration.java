package com.quanxiaoha.framework.biz.idempotent.config;

import com.quanxiaoha.framework.biz.idempotent.aspect.IdempotentAspect;
import com.quanxiaoha.framework.biz.idempotent.controller.IdempotentTokenController;
import com.quanxiaoha.framework.biz.idempotent.service.IdempotentService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 接口幂等组件自动配置
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class IdempotentAutoConfiguration {

    @Bean
    public IdempotentService idempotentService(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentService(stringRedisTemplate);
    }

    @Bean
    public IdempotentAspect idempotentAspect(IdempotentService idempotentService) {
        return new IdempotentAspect(idempotentService);
    }

    @Bean
    public IdempotentTokenController idempotentTokenController(IdempotentService idempotentService) {
        return new IdempotentTokenController(idempotentService);
    }

}
