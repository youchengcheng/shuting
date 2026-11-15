package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.controller.*;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.exception.GlobalExceptionHandler;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationAuthUtils;
import org.springframework.test.util.ReflectionTestUtils;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.FindNotificationSummaryRspVO;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
    private NotificationService service;
    private NotificationTokenValidator tokens;
    private NotificationSettings settings;
    private MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        LoginUserContextHolder.remove();
        service = mock(NotificationService.class);
        tokens = mock(NotificationTokenValidator.class);
        settings = new NotificationSettings();
        settings.setEnabled(true);
        ObjectProvider<NotificationStreamHub> streams = mock(ObjectProvider.class);
        // 与生产代码一致，通过字段注入为登录工具和 Controller 提供依赖
        NotificationAuthUtils auth = new NotificationAuthUtils();
        ReflectionTestUtils.setField(auth, "notificationTokenValidator", tokens);
        ReflectionTestUtils.setField(auth, "notificationSettings", settings);
        NotificationController controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", service);
        ReflectionTestUtils.setField(controller, "notificationAuthUtils", auth);
        ReflectionTestUtils.setField(controller, "notificationStreams", streams);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearContext() { LoginUserContextHolder.remove(); }

    @Test
    void externalUserHeaderWithoutTrustedContextDoesNotIdentifyRecipient() throws Exception {
        mvc.perform(get("/notification/summary").header("userId", "20").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verifyNoInteractions(service, tokens);
    }

    @Test
    void tokenMustMatchTrustedContextBeforeReadingAnyNotifications() throws Exception {
        LoginUserContextHolder.setUserId("20");
        doThrow(new BizException(ResponseCodeEnum.LOGIN_EXPIRED)).when(tokens).require("other-user-token", 20);
        mvc.perform(get("/notification/summary").header("Authorization", "Bearer other-user-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void validSessionUsesOnlyItsOwnUserIdAndPreservesStringVersions() throws Exception {
        LoginUserContextHolder.setUserId(Long.toString(NotificationServiceTest.USER));
        when(service.summary(NotificationServiceTest.USER)).thenReturn(new FindNotificationSummaryRspVO(Map.of(), 0,
                "9223372036854775806", "9223372036854775805", Map.of()));
        mvc.perform(get("/notification/summary").header("userId", "1").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lastSeq").value("9223372036854775806"));
        verify(tokens).require("unit-token", NotificationServiceTest.USER);
        verify(service).summary(NotificationServiceTest.USER);
    }

    @Test
    void duplicateOrCommaSeparatedAuthorizationIsRejected() throws Exception {
        LoginUserContextHolder.setUserId("20");
        mvc.perform(get("/notification/summary").header("Authorization", "Bearer one", "Bearer two")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notification/summary").header("Authorization", "Bearer one,Bearer two")).andExpect(status().isUnauthorized());
        verifyNoInteractions(tokens, service);
    }

    @Test
    void streamAuthAndDependencyErrorsAreJsonWithCorrectHttpStatus() throws Exception {
        mvc.perform(get("/notification/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        LoginUserContextHolder.setUserId("20");
        doThrow(new BizException(ResponseCodeEnum.AUTHENTICATION_UNAVAILABLE))
                .when(tokens).require("unit-token", 20);
        mvc.perform(get("/notification/stream").header("Authorization", "Bearer unit-token").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verifyNoInteractions(service);
    }

    @Test
    void disabledServiceFailsClosedAfterAuthentication() throws Exception {
        LoginUserContextHolder.setUserId("20");
        settings.setEnabled(false);
        mvc.perform(post("/notification/read").header("Authorization", "Bearer unit-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"notificationId\":\"1\"}"))
                .andExpect(status().isServiceUnavailable());
        verify(tokens).require("unit-token", 20);
        verifyNoInteractions(service);
    }
}
