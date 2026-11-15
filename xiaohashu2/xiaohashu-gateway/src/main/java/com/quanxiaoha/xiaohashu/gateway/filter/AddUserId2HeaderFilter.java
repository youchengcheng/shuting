package com.quanxiaoha.xiaohashu.gateway.filter;

import com.quanxiaoha.framework.common.constant.GlobalConstants;
import com.quanxiaoha.xiaohashu.gateway.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 转发请求时，将用户 ID 添加到 Header 请求头中，透传给下游服务
 **/
@Component
@Slf4j
public class AddUserId2HeaderFilter implements GlobalFilter, Ordered {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Header 头中 Token 的 Key
     */
    private static final String TOKEN_HEADER_KEY = "Authorization";

    /**
     * Token 前缀
     */
    private static final String TOKEN_HEADER_VALUE_PREFIX = "Bearer ";

    /**
     * 内部服务凭证请求头
     */
    private static final String INTERNAL_TOKEN_HEADER_KEY = "X-Internal-Token";

    /**
     * 设置用户身份透传过滤器的执行顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }


    /**
     * 网关过滤器核心处理方法
     * @param exchange 一次请求的上下文，封装了ServerHttpRequest、ServerHttpResponse及自定义属性
     * @param chain 过滤器链，调用chain.filter(exchange)放行请求进入下一个过滤器/路由
     * @return Mono<Void> 响应式结果流，代表请求处理完成
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 清除客户端传入的用户 ID 和内部服务凭证
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(GlobalConstants.USER_ID);
                    headers.remove(INTERNAL_TOKEN_HEADER_KEY);
                }))
                .build();

        // 从请求头中获取 Token 数据
        List<String> tokenList = exchange.getRequest().getHeaders().get(TOKEN_HEADER_KEY);

        if (tokenList == null || tokenList.size() != 1) {
            // Token 缺失或存在多个值，继续传递清理后的请求
            return chain.filter(sanitizedExchange);
        }

        // 获取 Token 值
        String tokenValue = tokenList.get(0);
        // 校验 Token 前缀
        if (!tokenValue.startsWith(TOKEN_HEADER_VALUE_PREFIX)) {
            return chain.filter(sanitizedExchange);
        }
        // 去除 Token 前缀，并校验内容格式
        String token = tokenValue.substring(TOKEN_HEADER_VALUE_PREFIX.length());
        if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            return chain.filter(sanitizedExchange);
        }

        // 构建 Redis Key
        String tokenRedisKey = RedisKeyConstants.SA_TOKEN_TOKEN_KEY_PREFIX + token;
        // 查询 Redis, 获取用户 ID（Sa-Token 存储的 loginId 是字符串，反序列化后可能是 Integer/Long，不能直接强转 Integer）
        Object userIdObj = redisTemplate.opsForValue().get(tokenRedisKey);

        if (userIdObj == null) {
            // 若没有登录，则直接放行
            return chain.filter(sanitizedExchange);
        }

        // 统一转为 Long，兼容号段/雪花算法生成的大数 ID
        Long userId;
        try {
            userId = Long.valueOf(String.valueOf(userIdObj));
        } catch (NumberFormatException ex) {
            return chain.filter(sanitizedExchange);
        }
        // 过滤 Sa-Token 的过期、被顶替和被踢下线标记
        if (userId <= 0) {
            return chain.filter(sanitizedExchange);
        }

        log.info("## 当前登录的用户 ID: {}", userId);

        ServerWebExchange newExchange = sanitizedExchange.mutate()
                .request(builder ->
                        builder.header(GlobalConstants.USER_ID, String.valueOf(userId)))
                .build();// 将用户 ID 设置到请求头中
        return chain.filter(newExchange);
    }
}
