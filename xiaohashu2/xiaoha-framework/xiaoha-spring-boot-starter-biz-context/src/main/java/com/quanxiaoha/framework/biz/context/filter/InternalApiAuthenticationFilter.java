package com.quanxiaoha.framework.biz.context.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

/**
 * 内部接口鉴权过滤器
 */
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 内部服务凭证请求头
     */
    public static final String TOKEN_HEADER = "X-Internal-Token";

    /**
     * 当前服务配置的内部凭证
     */
    private final byte[] expectedToken;

    /*
     * 初始化内部服务凭证
     * */
    public InternalApiAuthenticationFilter(String token) {
        expectedToken = StringUtils.hasText(token) ? token.getBytes(StandardCharsets.UTF_8) : null;
    }

    /*
     * 普通接口不执行内部鉴权
     * */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 使用应用内规范化路径判断内部入口，防止路径表现形式绕过鉴权
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !path.equals("/internal") && !path.startsWith("/internal/");
    }

    /*
     * 校验内部请求携带的服务凭证
     * */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 未配置服务凭证，关闭内部接口入口
        if (expectedToken == null) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "内部接口暂时不可用");
            return;
        }
        // 请求头只能携带一个凭证，并与当前服务配置进行比较
        List<String> tokens = Collections.list(request.getHeaders(TOKEN_HEADER));
        if (tokens.size() != 1 || !MessageDigest.isEqual(expectedToken,
                tokens.get(0).getBytes(StandardCharsets.UTF_8))) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "内部接口凭证校验失败");
            return;
        }
        // 凭证校验通过，放行请求
        filterChain.doFilter(request, response);
    }

    /*
     * 返回内部接口鉴权失败的响应
     * */
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + status
                + "\",\"message\":\"" + message + "\"}");
    }
}
