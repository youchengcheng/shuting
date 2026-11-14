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
 * 内部元数据接口只接受共享服务凭证；未配置凭证时关闭入口。
 */
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Internal-Token";
    private final byte[] expectedToken;

    public InternalApiAuthenticationFilter(String token) {
        expectedToken = StringUtils.hasText(token) ? token.getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !path.equals("/internal") && !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (expectedToken == null) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Internal API unavailable");
            return;
        }
        List<String> tokens = Collections.list(request.getHeaders(TOKEN_HEADER));
        if (tokens.size() != 1 || !MessageDigest.isEqual(expectedToken,
                tokens.get(0).getBytes(StandardCharsets.UTF_8))) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + status
                + "\",\"message\":\"" + message + "\"}");
    }
}
