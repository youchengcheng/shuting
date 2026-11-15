package com.quanxiaoha.framework.biz.context.interceptor;

import com.quanxiaoha.framework.biz.context.filter.InternalApiAuthenticationFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.util.StringUtils;

/**
 * Feign 内部接口凭证拦截器
 */
public class InternalApiRequestInterceptor implements RequestInterceptor {

    /**
     * 内部服务凭证
     */
    private final String token;

    /*
     * 初始化内部服务凭证
     * */
    public InternalApiRequestInterceptor(String token) {
        this.token = token;
    }

    /*
     * 为内部接口调用添加服务凭证
     * */
    @Override
    public void apply(RequestTemplate template) {
        // 清除已有凭证，避免将其传递给普通接口
        template.removeHeader(InternalApiAuthenticationFilter.TOKEN_HEADER);
        String path = template.path();
        if (!path.equals("/internal") && !path.startsWith("/internal/")) {
            return;
        }
        // 内部接口调用前必须配置服务凭证
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("内部接口服务凭证尚未配置");
        }
        // 将当前服务配置的凭证写入请求头
        template.header(InternalApiAuthenticationFilter.TOKEN_HEADER, token);
    }
}
