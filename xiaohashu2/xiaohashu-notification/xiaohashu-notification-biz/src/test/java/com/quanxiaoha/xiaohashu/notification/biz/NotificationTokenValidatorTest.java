package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationTokenValidator;
import com.quanxiaoha.xiaohashu.notification.biz.service.impl.NotificationTokenValidatorImpl;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationTokenValidatorTest {
    @Test
    @SuppressWarnings("unchecked")
    void validatesExactUserAndDistinguishesExpiryFromRedisFailure() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("Authorization:login:token:unit-token")).thenReturn("9223372036854775806", "20", "-2")
                .thenThrow(new RedisConnectionFailureException("test Redis failure"));
        // 通过字段注入 Redis 测试依赖，生产类无须测试专用构造器
        NotificationTokenValidator validator = new NotificationTokenValidatorImpl();
        ReflectionTestUtils.setField(validator, "redisTemplate", redis);
        assertThatCode(() -> validator.require("unit-token", NotificationServiceTest.USER)).doesNotThrowAnyException();
        assertStatus(() -> validator.require("unit-token", NotificationServiceTest.USER), 401);
        assertStatus(() -> validator.require("unit-token", NotificationServiceTest.USER), 401);
        assertStatus(() -> validator.require("unit-token", NotificationServiceTest.USER), 503);
    }

    private static void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo("NOTIFICATION_" + status));
    }
}
