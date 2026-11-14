package com.quanxiaoha.framework.notification.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/** A rejected source event is acknowledged only after this local database write succeeds. */
public class NotificationEventIsolationWriter {
    private final JdbcTemplate jdbc;
    private final NotificationOutboxProperties properties;
    private final String producerService;

    public NotificationEventIsolationWriter(JdbcTemplate jdbc, NotificationOutboxProperties properties, String producerService) {
        this.jdbc = jdbc; this.properties = properties; this.producerService = producerService;
    }

    public void save(String topic, String payload, String reason) {
        if (!properties.getEvents().isEnabled()) return;
        Objects.requireNonNull(payload, "original payload");
        String source = producerService + ":" + topic;
        if (source.length() > 64) throw new IllegalArgumentException("Source isolation identity exceeds 64 characters");
        String message = reason == null ? "Invalid source notification event" : reason;
        if (message.length() > 1000) message = message.substring(0, 1000);
        jdbc.update("INSERT INTO t_notification_event_error(source,event_identity,payload,reason,created_at) VALUES(?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE event_identity=event_identity",
                source, identity(source, payload), payload, message,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.SECONDS));
    }

    public static String identity(String source, String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((source + "\n" + payload).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
