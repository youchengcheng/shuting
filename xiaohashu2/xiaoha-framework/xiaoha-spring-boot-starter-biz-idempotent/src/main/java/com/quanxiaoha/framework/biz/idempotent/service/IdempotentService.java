package com.quanxiaoha.framework.biz.idempotent.service;

import com.quanxiaoha.framework.biz.idempotent.constant.IdempotentConstants;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * 幂等组件 Redis 服务：负责两种幂等策略的原子读写
 * <p>
 * TOKEN 策略：生成令牌（SETEX）、消费令牌（DEL）；
 * PARAM 策略：窗口占位（SET NX EX）。
 */
public class IdempotentService {

    private final StringRedisTemplate stringRedisTemplate;

    public IdempotentService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成幂等 Token，并以 idempotent:token:{userId}:{token} 为 Key 写入 Redis，TTL 10 分钟
     *
     * @param userId 当前登录用户 ID
     * @return 幂等 Token
     */
    public String generate(Long userId) {
        String token = UUID.randomUUID().toString();
        String key = IdempotentConstants.buildIdempotentTokenKey(userId, token);
        stringRedisTemplate.opsForValue().set(key, "1", IdempotentConstants.IDEMPOTENT_TOKEN_TTL);
        return token;
    }

    /**
     * 原子消费幂等 Token：单条 DEL 命令完成判定，删除成功即为首次请求
     *
     * @param userId 当前登录用户 ID
     * @param token  请求头携带的幂等 Token
     * @return true：首次请求（放行）；false：Token 已被消费或已过期（拒绝）
     */
    public boolean consume(Long userId, String token) {
        String key = IdempotentConstants.buildIdempotentTokenKey(userId, token);
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    /**
     * PARAM 策略：原子占位（单条 SET NX EX 命令），占位成功即为窗口期内首次请求
     *
     * @param key 已构建完成的幂等 Key
     * @param ttl 占位有效期
     * @return true：占位成功（放行）；false：窗口期内已存在（拒绝）
     */
    public boolean tryAcquire(String key, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
    }

}
