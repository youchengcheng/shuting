package com.quanxiaoha.framework.notification.outbox;

import com.quanxiaoha.framework.notification.outbox.model.dto.NotificationOutboxClaimDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 通知 Outbox 事务和重试测试
 */
class NotificationOutboxTest {
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    NotificationOutboxProperties properties;
    JdbcNotificationOutboxWriter writer;
    LocalDateTime cutoff = LocalDateTime.of(2026, 11, 14, 0, 0);

    @BeforeEach
    void setup() {
        DriverManagerDataSource data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute("create table t_notification_outbox(id bigint auto_increment primary key,producer_service varchar(64),business_key varchar(191),event_id char(36) unique,payload longtext,state varchar(16),next_attempt_at datetime,attempts int,lease_owner varchar(64),lease_version bigint,lease_until datetime,last_error varchar(1000),created_at datetime,sent_at datetime,unique(producer_service,business_key))");
        jdbc.execute("create table source_relation(id bigint primary key)");
        jdbc.execute("create table t_notification_event_error(id bigint auto_increment primary key,source varchar(64),event_identity varchar(64),payload longtext,reason varchar(1000),created_at datetime,unique(source,event_identity))");
        properties = new NotificationOutboxProperties();
        properties.setEnabledAt(cutoff);
        writer = new JdbcNotificationOutboxWriter(jdbc, new ObjectMapper().registerModule(new JavaTimeModule()), properties, "xiaohashu-note");
    }

    NotificationEvent event(String key) {
        return NotificationEvent.builder().producerService("xiaohashu-note").businessKey(key).type("NOTE_COLLECT")
                .actorId(1L).noteId(2L).occurredAt(cutoff.plusSeconds(1))
                .recipients(List.of(NotificationRecipient.builder().userId(3L).role("OWNER").build())).build();
    }

    @Test
    void disabledCaptureDoesNotRequireTableOrTransaction() {
        jdbc.execute("drop table t_notification_outbox");
        assertDoesNotThrow(() -> writer.save(event("key")));
    }

    @Test
    void captureRequiresSourceTransactionAndExplicitCutoff() {
        properties.getEvents().setEnabled(true);
        assertThrows(IllegalStateException.class, () -> writer.save(event("key")));
        properties.setEnabledAt(null);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            writer.save(event("key"));
            return null;
        }));
    }

    @Test
    void rejectedSourceEventsPersistOriginalPayloadWithStableIdentity() {
        NotificationEventIsolationWriter isolation = new NotificationEventIsolationWriter(jdbc, properties, "xiaohashu-comment");
        properties.getEvents().setEnabled(true);
        isolation.save("PublishCommentTopic", "{bad original payload}", "invalid");
        isolation.save("PublishCommentTopic", "{bad original payload}", "repeated invalid");
        assertEquals(1, jdbc.queryForObject("select count(*) from t_notification_event_error", Integer.class));
        assertEquals("{bad original payload}", jdbc.queryForObject("select payload from t_notification_event_error", String.class));
        assertEquals(NotificationEventIsolationWriter.identity("xiaohashu-comment:PublishCommentTopic", "{bad original payload}"),
                jdbc.queryForObject("select event_identity from t_notification_event_error", String.class));
        jdbc.execute("drop table t_notification_event_error");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> isolation.save("PublishCommentTopic", "bad", "invalid"));
        properties.getEvents().setEnabled(false);
        assertDoesNotThrow(() -> isolation.save("PublishCommentTopic", "bad", "invalid"));
    }

    @Test
    void fractionalCutoffAndMissingSourceTimeAreRejected() {
        properties.getEvents().setEnabled(true);
        properties.setEnabledAt(cutoff.plusNanos(1));
        assertThrows(IllegalStateException.class, () -> properties.accepts(cutoff));
        properties.setEnabledAt(cutoff);
        assertThrows(IllegalArgumentException.class, () -> properties.accepts(null));
        NotificationEvent invalid = event("bad-time");
        invalid.setOccurredAt(null);
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(status -> {
            jdbc.update("insert into source_relation values(1)");
            writer.save(invalid);
            return null;
        }));
        assertEquals(0, jdbc.queryForObject("select count(*) from source_relation", Integer.class));
    }

    @Test
    void sourceAndOutboxRollbackTogether() {
        properties.getEvents().setEnabled(true);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            jdbc.update("insert into source_relation values(1)");
            writer.save(event("key"));
            throw new IllegalStateException("source transaction failed");
        }));
        assertEquals(0, jdbc.queryForObject("select count(*) from source_relation", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from t_notification_outbox", Integer.class));
    }

    @Test
    void firstEventSurvivesRedeliveryAndSentState() {
        properties.getEvents().setEnabled(true);
        NotificationEvent first = event("key");
        transaction.execute(status -> {
            writer.save(first);
            return null;
        });
        jdbc.update("update t_notification_outbox set state='SENT'");
        transaction.execute(status -> {
            writer.save(event("key"));
            return null;
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from t_notification_outbox", Integer.class));
        assertEquals(first.getEventId(), jdbc.queryForObject("select event_id from t_notification_outbox", String.class));
        assertEquals("SENT", jdbc.queryForObject("select state from t_notification_outbox", String.class));
    }

    @Test
    void cutoffUsesSourceTimeAtSecondPrecision() {
        properties.getEvents().setEnabled(true);
        NotificationEvent old = event("old");
        old.setOccurredAt(cutoff.minusSeconds(1).plusNanos(999999999));
        transaction.execute(status -> {
            writer.save(old);
            writer.save(event("new"));
            return null;
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from t_notification_outbox", Integer.class));
    }

    @Test
    void crashBeforeSendIsRecoveredOnlyAfterLeaseExpiry() {
        properties.getEvents().setEnabled(true);
        transaction.execute(status -> {
            writer.save(event("key"));
            return null;
        });
        Long id = jdbc.queryForObject("select id from t_notification_outbox", Long.class);
        RocketMQTemplate mq = mock(RocketMQTemplate.class);
        NotificationOutboxDispatcher crashed = new NotificationOutboxDispatcher(jdbc, transaction, mq, "xiaohashu-note");
        NotificationOutboxDispatcher recovery = new NotificationOutboxDispatcher(jdbc, transaction, mq, "xiaohashu-note");
        assertNotNull(crashed.claim(id));
        assertNull(recovery.claim(id));
        jdbc.update("update t_notification_outbox set lease_until=?", LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusSeconds(1));
        NotificationOutboxClaimDTO reclaimed = recovery.claim(id);
        assertNotNull(reclaimed);
        assertEquals(2L, reclaimed.getVersion());
        verifyNoInteractions(mq);
    }

    @Test
    void brokerFailureRetriesWithTheOriginalEventIdentity() {
        properties.getEvents().setEnabled(true);
        NotificationEvent original = event("key");
        transaction.execute(status -> {
            writer.save(original);
            return null;
        });
        RocketMQTemplate failing = mock(RocketMQTemplate.class);
        when(failing.syncSend(eq("NotificationEventTopic"), any(org.springframework.messaging.Message.class), eq(3000L)))
                .thenThrow(new IllegalStateException("broker unavailable"));
        new NotificationOutboxDispatcher(jdbc, transaction, failing, "xiaohashu-note").dispatch();
        assertEquals("PENDING", jdbc.queryForObject("select state from t_notification_outbox", String.class));
        assertNull(jdbc.queryForObject("select lease_owner from t_notification_outbox", String.class));
        assertEquals(original.getEventId(), jdbc.queryForObject("select event_id from t_notification_outbox", String.class));
        jdbc.update("update t_notification_outbox set next_attempt_at=?", cutoff);
        RocketMQTemplate recovered = mock(RocketMQTemplate.class);
        when(recovered.syncSend(eq("NotificationEventTopic"), any(org.springframework.messaging.Message.class), eq(3000L))).thenAnswer(call -> {
            org.springframework.messaging.Message<?> sent = call.getArgument(1);
            assertTrue(sent.getPayload().toString().contains(original.getEventId()));
            SendResult result = new SendResult();
            result.setSendStatus(SendStatus.SEND_OK);
            return result;
        });
        new NotificationOutboxDispatcher(jdbc, transaction, recovered, "xiaohashu-note").dispatch();
        assertEquals("SENT", jdbc.queryForObject("select state from t_notification_outbox", String.class));
        assertEquals(2, jdbc.queryForObject("select attempts from t_notification_outbox", Integer.class));
    }

    @Test
    void completionCannotOverwriteAReclaimedLeaseAndSendIsOutsideTransaction() {
        properties.getEvents().setEnabled(true);
        transaction.execute(status -> {
            writer.save(event("key"));
            return null;
        });
        RocketMQTemplate mq = mock(RocketMQTemplate.class);
        when(mq.syncSend(eq("NotificationEventTopic"), any(org.springframework.messaging.Message.class), eq(3000L))).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("update t_notification_outbox set lease_owner='other',lease_version=lease_version+1");
            SendResult result = new SendResult();
            result.setSendStatus(SendStatus.SEND_OK);
            return result;
        });
        new NotificationOutboxDispatcher(jdbc, transaction, mq, "xiaohashu-note").dispatch();
        assertEquals("SENDING", jdbc.queryForObject("select state from t_notification_outbox", String.class));
        assertEquals("other", jdbc.queryForObject("select lease_owner from t_notification_outbox", String.class));
        RocketMQTemplate otherMq = mock(RocketMQTemplate.class);
        new NotificationOutboxDispatcher(jdbc, transaction, otherMq, "xiaohashu-comment").dispatch();
        verifyNoInteractions(otherMq);
    }
}
