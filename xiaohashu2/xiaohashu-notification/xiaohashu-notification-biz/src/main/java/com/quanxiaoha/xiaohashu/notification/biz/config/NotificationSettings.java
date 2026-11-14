package com.quanxiaoha.xiaohashu.notification.biz.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationSettings {
    private boolean enabled;
    private String enabledAt;
    private Stream stream = new Stream();

    @Data
    public static class Stream {
        private int maxPerUser = 10;
        private int maxPerInstance = 1000;
    }

    @PostConstruct
    public void validate() {
        if (enabled) cutoff();
        if (stream.maxPerUser < 1 || stream.maxPerInstance < 1) throw new IllegalStateException("Invalid notification stream limits");
    }

    public LocalDateTime cutoff() {
        if (enabledAt == null || enabledAt.isBlank()) throw new IllegalStateException("NOTIFICATION_ENABLED_AT is required before enabling notifications");
        LocalDateTime result = parseTime(enabledAt);
        if (result.getNano() != 0) throw new IllegalStateException("Notification cutoff must use whole seconds in Asia/Shanghai");
        return result;
    }

    public static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing interaction time");
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (java.time.format.DateTimeParseException ex) {
            try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)); }
            catch (java.time.format.DateTimeParseException invalid) { throw new IllegalArgumentException("Invalid interaction time", invalid); }
        }
    }
}
