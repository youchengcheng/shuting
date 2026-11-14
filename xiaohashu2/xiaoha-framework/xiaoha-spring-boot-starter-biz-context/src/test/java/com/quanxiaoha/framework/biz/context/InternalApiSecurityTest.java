package com.quanxiaoha.framework.biz.context;

import com.quanxiaoha.framework.biz.context.config.ContextAutoConfiguration;
import com.quanxiaoha.framework.biz.context.config.FeignContextAutoConfiguration;
import com.quanxiaoha.framework.biz.context.filter.InternalApiAuthenticationFilter;
import com.quanxiaoha.framework.biz.context.interceptor.InternalApiRequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiSecurityTest {

    private static final String TEST_TOKEN = "unit-test-credential";

    @Test
    void internalApiFailsClosedWithoutConfiguration() throws Exception {
        assertRejected(new InternalApiAuthenticationFilter(""), "/internal/note/batch", TEST_TOKEN, 503);
    }

    @Test
    void internalApiRejectsMissingWrongAndDuplicateCredentials() throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter(TEST_TOKEN);
        assertRejected(filter, "/internal/note/batch", null, 401);
        assertRejected(filter, "/internal/note/batch", "wrong", 401);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/note/batch");
        request.addHeader(InternalApiAuthenticationFilter.TOKEN_HEADER, TEST_TOKEN);
        request.addHeader(InternalApiAuthenticationFilter.TOKEN_HEADER, "wrong");
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void validCredentialPassesAndPublicPathsDoNotRequireIt() throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter(TEST_TOKEN);
        MockHttpServletRequest internal = new MockHttpServletRequest("POST", "/internal/note/batch");
        internal.addHeader(InternalApiAuthenticationFilter.TOKEN_HEADER, TEST_TOKEN);
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(internal, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called).isTrue();
        called.set(false);
        new InternalApiAuthenticationFilter("").doFilter(
                new MockHttpServletRequest("GET", "/note/detail"),
                new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called).isTrue();
    }

    @Test
    void internalPathWithContextOrMatrixParameterStillRequiresCredential() throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter(TEST_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/service/internal;version=1/note/batch");
        request.setContextPath("/service");
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void feignCredentialIsLimitedToInternalPathsAndOverridesCallerHeader() {
        InternalApiRequestInterceptor interceptor = new InternalApiRequestInterceptor(TEST_TOKEN);
        RequestTemplate internal = new RequestTemplate().uri("/internal/note/batch");
        internal.header(InternalApiAuthenticationFilter.TOKEN_HEADER, "caller-supplied");
        interceptor.apply(internal);
        assertThat(internal.headers().get(InternalApiAuthenticationFilter.TOKEN_HEADER)).containsExactly(TEST_TOKEN);
        RequestTemplate publicRequest = new RequestTemplate().uri("/note/detail");
        publicRequest.header(InternalApiAuthenticationFilter.TOKEN_HEADER, "caller-supplied");
        interceptor.apply(publicRequest);
        assertThat(publicRequest.headers()).doesNotContainKey(InternalApiAuthenticationFilter.TOKEN_HEADER);
        RequestTemplate similarPath = new RequestTemplate().uri("/internalized/test");
        interceptor.apply(similarPath);
        assertThat(similarPath.headers()).doesNotContainKey(InternalApiAuthenticationFilter.TOKEN_HEADER);
        assertThatThrownBy(() -> new InternalApiRequestInterceptor("").apply(internal))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Internal API credential is not configured");
    }

    @Test
    void autoConfigurationRegistersFilterAndCredentialInterceptor() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ContextAutoConfiguration.class, FeignContextAutoConfiguration.class))
                .withPropertyValues("internal.api.token=" + TEST_TOKEN)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("internalApiAuthenticationFilter");
                    assertThat(context.getBean("internalApiAuthenticationFilter", FilterRegistrationBean.class).getFilter())
                            .isInstanceOf(InternalApiAuthenticationFilter.class);
                    assertThat(context).hasSingleBean(InternalApiRequestInterceptor.class);
                });
    }

    @Test
    void feignAutoConfigurationCanLoadWithoutFeign() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("feign"))
                .withConfiguration(AutoConfigurations.of(FeignContextAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("feignRequestInterceptor");
                    assertThat(context).doesNotHaveBean("internalApiRequestInterceptor");
                });
    }

    private void assertRejected(InternalApiAuthenticationFilter filter, String path, String token, int status)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) {
            request.addHeader(InternalApiAuthenticationFilter.TOKEN_HEADER, token);
        }
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).doesNotContain(TEST_TOKEN);
    }
}
