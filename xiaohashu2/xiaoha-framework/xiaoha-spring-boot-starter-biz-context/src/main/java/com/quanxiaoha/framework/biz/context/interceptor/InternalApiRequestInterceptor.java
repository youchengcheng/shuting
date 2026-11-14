package com.quanxiaoha.framework.biz.context.interceptor;

import com.quanxiaoha.framework.biz.context.filter.InternalApiAuthenticationFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.util.StringUtils;

/**
 * 内部凭证仅用于 /internal 接口，不能传播给普通 Feign 请求。
 */
public class InternalApiRequestInterceptor implements RequestInterceptor {

    private final String token;

    public InternalApiRequestInterceptor(String token) {
        this.token = token;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(InternalApiAuthenticationFilter.TOKEN_HEADER);
        String path = template.path();
        if (!path.equals("/internal") && !path.startsWith("/internal/")) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Internal API credential is not configured");
        }
        template.header(InternalApiAuthenticationFilter.TOKEN_HEADER, token);
    }
}
