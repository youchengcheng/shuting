package com.quanxiaoha.framework.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class JdbcNotificationOutboxWriter implements NotificationOutboxWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NotificationOutboxProperties properties;
    private final String producerService;

    public JdbcNotificationOutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper,
                                       NotificationOutboxProperties properties, String producerService) {
        this.jdbc = jdbc; this.mapper = mapper; this.properties = properties; this.producerService = producerService;
    }

    @Override
    public void save(NotificationEvent event) {
        if (!properties.getEvents().isEnabled()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || jdbc.getDataSource() == null || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("Notification outbox requires the source database transaction");
        Objects.requireNonNull(event, "event");
        if (!properties.accepts(event.getOccurredAt())) return;
        if (!Objects.equals(producerService, event.getProducerService()))
            throw new IllegalArgumentException("Notification producerService must match spring.application.name");
        if (event.getBusinessKey() == null || event.getBusinessKey().isBlank())
            throw new IllegalArgumentException("Notification businessKey is required");
        if (event.getRecipients() == null || event.getRecipients().isEmpty()) return;
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        event.setOccurredAt(event.getOccurredAt().truncatedTo(ChronoUnit.SECONDS));
        String payload;
        try { payload = mapper.writeValueAsString(event); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Cannot serialize notification event", ex); }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.SECONDS);
        // Keep the first event and SENT rows: cancelling and repeating an action cannot notify again.
        jdbc.update("INSERT INTO t_notification_outbox (producer_service,business_key,event_id,payload,state,next_attempt_at,attempts,lease_version,created_at) "
                        + "VALUES (?,?,?,?,'PENDING',?,0,0,?) ON DUPLICATE KEY UPDATE business_key=business_key",
                producerService, event.getBusinessKey(), event.getEventId(), payload, now, now);
    }
}
