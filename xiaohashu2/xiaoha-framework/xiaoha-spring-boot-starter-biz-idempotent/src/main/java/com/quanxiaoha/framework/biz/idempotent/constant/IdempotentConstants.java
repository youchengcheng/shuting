package com.quanxiaoha.framework.biz.idempotent.constant;

import java.time.Duration;

/**
 * 幂等组件常量
 */
public class IdempotentConstants {

    /**
     * 幂等 Token 请求头名称
     */
    public static final String IDEMPOTENT_TOKEN_HEADER = "Idempotent-Token";

    /**
     * 幂等 Token Redis Key 前缀
     */
    private static final String IDEMPOTENT_TOKEN_KEY_PREFIX = "idempotent:token:";

    /**
     * PARAM 策略幂等 Key 前缀
     */
    private static final String IDEMPOTENT_PARAM_KEY_PREFIX = "idempotent:param:";

    /**
     * 幂等 Token 有效期：10 分钟
     */
    public static final Duration IDEMPOTENT_TOKEN_TTL = Duration.ofMinutes(10);

    /**
     * 构建幂等 Token Redis Key：idempotent:token:{userId}:{token}
     *
     * @param userId 用户 ID
     * @param token  幂等 Token
     * @return 完整的 Redis Key
     */
    public static String buildIdempotentTokenKey(Long userId, String token) {
        return IDEMPOTENT_TOKEN_KEY_PREFIX + userId + ":" + token;
    }

    /**
     * 构建 PARAM 策略幂等 Key：idempotent:param:{表达式结果}
     *
     * @param keyValue SpEL 表达式计算结果
     * @return 完整的 Redis Key
     */
    public static String buildIdempotentParamKey(String keyValue) {
        return IDEMPOTENT_PARAM_KEY_PREFIX + keyValue;
    }

}
