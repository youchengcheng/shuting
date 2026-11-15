package com.quanxiaoha.xiaohashu.notification.biz.service.impl;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationTokenValidator;

import jakarta.annotation.Resource;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/*
 * 通知接口和 SSE 连接的登录凭证校验
 * */
@Service
public class NotificationTokenValidatorImpl implements NotificationTokenValidator {

    /**
     * Sa-Token 登录凭证的 Redis Key 前缀
     */
    private static final String TOKEN_PREFIX = "Authorization:login:token:";

    /**
     * 登录凭证查询模板
     */
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /*
     * 校验凭证是否属于当前登录用户
     * */
    @Override
    public void require(String token, long userId) {
        // 先校验凭证是否为空及长度是否合法
        if (token == null || token.isBlank() || token.length() > 4096) {
            throw new BizException(ResponseCodeEnum.LOGIN_REQUIRED);
        }

        // 从 Redis 获取凭证对应的用户 ID
        Object identity;
        try {
            identity = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        } catch (RuntimeException ex) {
            throw new BizException(ResponseCodeEnum.AUTHENTICATION_UNAVAILABLE);
        }
        // 凭证不存在或不属于当前用户时按登录过期处理
        if (!matches(identity, userId)) {
            throw new BizException(ResponseCodeEnum.LOGIN_EXPIRED);
        }
    }

    /*
     * 批量校验凭证与用户 ID 的对应关系
     * */
    @Override
    public Map<String, Boolean> validate(Map<String, Long> tokens) {
        if (tokens.isEmpty()) {
            return Map.of();
        }
        // 获取有效凭证的用户身份，逐个比较请求中的用户 ID
        Map<String, Long> actual = identities(tokens.keySet());
        Map<String, Boolean> result = new HashMap<>();
        tokens.forEach((token, userId) -> result.put(token, Objects.equals(actual.get(token), userId)));
        return result;
    }

    /*
     * 批量查询有效凭证对应的用户 ID
     * */
    @Override
    public Map<String, Long> identities(Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return Map.of();
        }
        // 凭证去重后批量查询，减少 Redis 调用次数
        List<String> names = new ArrayList<>(new LinkedHashSet<>(tokens));
        // 一次性查询去重后的凭证对应身份
        List<Object> identities = redisTemplate.opsForValue().multiGet(names.stream().map(token -> TOKEN_PREFIX + token).toList());
        // 批量结果不完整时拒绝使用，避免错配凭证和用户
        if (identities == null || identities.size() != names.size()) {
            throw new BizException(ResponseCodeEnum.AUTHENTICATION_UNAVAILABLE);
        }
        // 仅保留有效的正整数用户 ID
        Map<String, Long> result = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            Object identity = identities.get(i);
            if (identity == null) {
                continue;
            }
            try {
                long id = Long.parseLong(String.valueOf(identity));
                if (id > 0) {
                    // 凭证和身份按批量查询返回的相同位置对应
                    result.put(names.get(i), id);
                }
            } catch (NumberFormatException ignored) {
                // 非数字身份不作为有效登录用户返回
            }
        }
        return result;
    }

    /*
     * 比较 Redis 身份与当前用户 ID
     * */
    private static boolean matches(Object identity, long userId) {
        if (identity == null) {
            return false;
        }
        try {
            // Redis 身份允许字符串或数值，统一解析后比较
            return Long.parseLong(String.valueOf(identity)) == userId;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
