package com.quanxiaoha.framework.biz.context.config;


import com.quanxiaoha.framework.biz.context.filter.HeaderUserId2ContextFilter;
import com.quanxiaoha.framework.biz.context.filter.InternalApiAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;


@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ContextAutoConfiguration {

    @Bean
    public FilterRegistrationBean<InternalApiAuthenticationFilter> internalApiAuthenticationFilter(
            @Value("${internal.api.token:${INTERNAL_API_TOKEN:}}") String token) {
        FilterRegistrationBean<InternalApiAuthenticationFilter> bean =
                new FilterRegistrationBean<>(new InternalApiAuthenticationFilter(token));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

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
