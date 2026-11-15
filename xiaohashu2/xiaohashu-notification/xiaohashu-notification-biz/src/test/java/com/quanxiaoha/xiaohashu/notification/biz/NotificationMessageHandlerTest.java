package com.quanxiaoha.xiaohashu.notification.biz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.consumer.NotificationMessageHandler;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationService;

import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationMessageHandlerTest {
    private NotificationService notifications;
    private NotificationSettings settings;
    private NotificationMessageHandler handler;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        settings = new NotificationSettings();
        settings.setEnabledAt("2026-11-14T00:00:00");
        ObjectMapper json = new ObjectMapper();
        com.fasterxml.jackson.datatype.jsr310.JavaTimeModule time = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
        time.addDeserializer(java.time.LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        json.registerModule(time);
        // 使用项目的 Jackson 配置初始化 JsonUtils
        com.quanxiaoha.framework.common.util.JsonUtils.init(json);
        handler = new NotificationMessageHandler();
        ReflectionTestUtils.setField(handler, "objectMapper", json);
        ReflectionTestUtils.setField(handler, "notificationService", notifications);
        ReflectionTestUtils.setField(handler, "notificationSettings", settings);
    }

    @Test
    void historicalRawCommentLikeDoesNotRequireNewRoutingFields() {
        handler.rawLike(message("{\"createTime\":\"2026-11-13 23:59:59\"}"), true);
        handler.committed(message("{\"occurredAt\":\"2026-11-13T23:59:59\"}"));
        verifyNoInteractions(notifications);
    }

    @Test
    void boundaryRawLikeKeepsNineteenDigitIdsAndRoutesToCapturedOwner() {
        handler.rawLike(message("{\"createTime\":\"2026-11-14 00:00:00\",\"type\":1,\"userId\":\"9223372036854775806\",\"commentId\":\"9223372036854775805\",\"commentCreatorId\":20,\"noteId\":100}"), true);
        var event = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notifications).consume(event.capture());
        assertThat(event.getValue().getActorId()).isEqualTo(9223372036854775806L);
        assertThat(event.getValue().getCommentId()).isEqualTo(9223372036854775805L);
        assertThat(event.getValue().getRecipients().get(0).getUserId()).isEqualTo(20);
        assertThat(event.getValue().getBusinessKey()).isEqualTo("COMMENT_LIKE:9223372036854775806:9223372036854775805");
        verify(notifications, never()).isolate(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unlikeDoesNotCreateNotification() {
        handler.rawLike(message("{\"createTime\":\"2026-11-14 00:00:00\",\"type\":0}"), false);
        verifyNoInteractions(notifications);
    }

    @Test
    void invalidTimeMalformedJsonAndMissingRouteAreDurablyIsolated() {
        handler.rawLike(message("{\"createTime\":\"bad-time\"}"), true);
        handler.rawLike(message("{"), false);
        handler.rawLike(message("{\"createTime\":\"2026-11-14 00:00:00\",\"type\":1,\"userId\":10,\"commentId\":200}"), true);
        verify(notifications, times(3)).isolate(anyString(), matches("[0-9a-f]{64}"), anyString(), anyString());
        verify(notifications, never()).consume(any());
    }

    @Test
    void isolationFailurePropagatesAndMustNotAcknowledgeBadEvent() {
        doThrow(new DataAccessResourceFailureException("isolation database unavailable"))
                .when(notifications).isolate(anyString(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> handler.rawLike(message("{"), false)).isInstanceOf(DataAccessResourceFailureException.class);
        verify(notifications, never()).consume(any());
    }

    @Test
    void normalDatabaseFailurePropagatesWithoutMisclassifyingEventAsInvalid() {
        doThrow(new DataAccessResourceFailureException("notification database unavailable")).when(notifications).consume(any());
        assertThatThrownBy(() -> handler.rawLike(message("{\"createTime\":\"2026-11-14 00:00:00\",\"type\":1,\"userId\":10,\"noteId\":100,\"noteCreatorId\":20}"), false))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(notifications, never()).isolate(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cutoffConfigurationFailurePropagatesRatherThanDiscardingEvent() {
        settings.setEnabledAt("");
        assertThatThrownBy(() -> handler.rawLike(message("{\"createTime\":\"2026-11-14 00:00:00\"}"), false))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(notifications);
    }

    @Test
    void committedEventsAcceptBothTimeFormatsAndRejectUnexpectedProducer() {
        String event = "{\"eventId\":\"test-event\",\"businessKey\":\"NOTE_COLLECT:10:100\",\"producerService\":\"xiaohashu-note\",\"type\":\"NOTE_COLLECT\",\"actorId\":10,\"noteId\":100,\"recipients\":[{\"userId\":20,\"role\":\"OWNER\"}],\"occurredAt\":\"TIME\"}";
        handler.committed(message(event.replace("TIME", "2026-11-14T00:00:00")));
        handler.committed(message(event.replace("TIME", "2026-11-14 00:00:00")));
        handler.committed(message(event.replace("TIME", "2026-11-14T00:00:00").replace("xiaohashu-note", "unknown-service")));
        verify(notifications, times(2)).consume(any());
        verify(notifications).isolate(eq("NotificationEventTopic"), anyString(), anyString(), eq("已提交的通知事件来源服务不匹配"));
    }

    private static MessageExt message(String payload) {
        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
