package com.quanxiaoha.framework.biz.context.config;


import com.quanxiaoha.framework.biz.context.filter.HeaderUserId2ContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class ContextAutoConfiguration {

    /*
    * 用 FilterRegistrationBean 包装，告诉spring HeaderUserId2ContextFilter是一个过滤器
    * */
    @Bean
    public FilterRegistrationBean<HeaderUserId2ContextFilter> filterFilterRegistrationBean() {
        // 1. 创建过滤器实例
        HeaderUserId2ContextFilter filter = new HeaderUserId2ContextFilter();
        // 2. 用包装类包装过滤器
        FilterRegistrationBean<HeaderUserId2ContextFilter> bean = new FilterRegistrationBean<>(filter);
        // 3. 交给 Spring
        return bean;
    }
}
