package com.quanxiaoha.xiaohashu.gateway;

import cn.dev33.satoken.exception.NotLoginException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.framework.common.constant.GlobalConstants;
import com.quanxiaoha.xiaohashu.gateway.auth.SaTokenConfigure;
import com.quanxiaoha.xiaohashu.gateway.exception.GlobalExceptionHandler;
import com.quanxiaoha.xiaohashu.gateway.filter.AddUserId2HeaderFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewayNotificationSecurityTest {

    private AddUserId2HeaderFilter filter;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        filter = new AddUserId2HeaderFilter();
        redisTemplate = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        ReflectionTestUtils.setField(filter, "redisTemplate", redisTemplate);
    }

    @Test
    void anonymousCannotSupplyUserIdentityOrInternalCredential() {
        ServerWebExchange forwarded = forward(request(null));
        assertNoTrustedHeaders(forwarded);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void malformedTokenCannotSupplyUserIdentity() {
        assertNoTrustedHeaders(forward(request("anythingBearer token")));
        assertNoTrustedHeaders(forward(request("Bearer ")));
        assertNoTrustedHeaders(forward(request("Bearer token extra")));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void unknownAndRevokedTokensCannotSupplyUserIdentity() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("Authorization:login:token:unit-test-token")).thenReturn(null, "-2", "not-a-user");
        assertNoTrustedHeaders(forward(request("Bearer unit-test-token")));
        assertNoTrustedHeaders(forward(request("Bearer unit-test-token")));
        assertNoTrustedHeaders(forward(request("Bearer unit-test-token")));
    }

    @Test
    void validTokenReplacesForgedIdentityAndPreservesLongId() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("Authorization:login:token:unit-test-token")).thenReturn("9223372036854775807");
        ServerWebExchange forwarded = forward(request("Bearer unit-test-token"));
        assertThat(forwarded.getRequest().getHeaders().get(GlobalConstants.USER_ID))
                .containsExactly("9223372036854775807");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Internal-Token")).isNull();
    }

    @Test
    void streamAuthenticationFailureReturns401Json() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/notification/stream"));
        handler().handle(exchange, new NotLoginException("Not logged in", null, null)).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
    }

    @Test
    void streamDependencyFailureReturns503AndPublicApiRetainsResponseFormat() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("Redis unavailable");
        MockServerWebExchange stream = MockServerWebExchange.from(MockServerHttpRequest.get("/notification/stream"));
        handler().handle(stream, failure).block();
        assertThat(stream.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(stream.getResponse().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(stream.getResponse().getBodyAsString().block()).contains("\"success\":false");
        MockServerWebExchange normal = MockServerWebExchange.from(MockServerHttpRequest.get("/note/detail"));
        handler().handle(normal, failure).block();
        assertThat(normal.getResponse().getStatusCode()).isNull();
        assertThat(normal.getResponse().getBodyAsString().block()).contains("\"success\":false");
    }

    @Test
    void saTokenKeepsDependencyCauseAndCommittedStreamIsNotRewritten() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("Redis unavailable");
        assertThatThrownBy(() -> new SaTokenConfigure().getSaReactorFilter().error.run(failure))
                .isInstanceOf(RuntimeException.class).hasCause(failure);
        MockServerWebExchange stream = MockServerWebExchange.from(MockServerHttpRequest.get("/notification/stream"));
        stream.getResponse().setComplete().block();
        assertThatThrownBy(() -> handler().handle(stream, failure).block()).isSameAs(failure);
        assertThat(stream.getResponse().getBodyAsString().block()).isEmpty();
    }

    private GlobalExceptionHandler handler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        return handler;
    }

    private MockServerWebExchange request(String authorization) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/note/detail")
                .header("userId", "1", "2")
                .header("X-Internal-Token", "forged-credential");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private ServerWebExchange forward(ServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();
        return forwarded.get();
    }

    private void assertNoTrustedHeaders(ServerWebExchange forwarded) {
        assertThat(forwarded.getRequest().getHeaders().getFirst(GlobalConstants.USER_ID)).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Internal-Token")).isNull();
    }
}
