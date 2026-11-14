package com.quanxiaoha.framework.biz.context.config;

import com.quanxiaoha.framework.biz.context.interceptor.FeignRequestInterceptor;
import com.quanxiaoha.framework.biz.context.interceptor.InternalApiRequestInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignContextAutoConfiguration {

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor(){
        return new FeignRequestInterceptor();
    }

    @Bean
    public InternalApiRequestInterceptor internalApiRequestInterceptor(
            @Value("${internal.api.token:${INTERNAL_API_TOKEN:}}") String token) {
        return new InternalApiRequestInterceptor(token);
    }
}
