package com.quanxiaoha.framework.notification.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties(prefix = "notification")
public class NotificationOutboxProperties {
    private final Events events = new Events();
    private LocalDateTime enabledAt;

    public Events getEvents() { return events; }
    public LocalDateTime getEnabledAt() { return enabledAt; }
    public void setEnabledAt(LocalDateTime enabledAt) { this.enabledAt = enabledAt; }

    public boolean accepts(LocalDateTime occurredAt) {
        if (!events.enabled) return false;
        if (enabledAt == null) throw new IllegalStateException("notification.enabled-at must be configured before capture is enabled");
        if (enabledAt.getNano() != 0)
            throw new IllegalStateException("notification.enabled-at must be a whole-second Asia/Shanghai ISO local datetime");
        if (occurredAt == null)
            throw new IllegalArgumentException("Notification event requires its original source occurrence time");
        return !occurredAt.truncatedTo(ChronoUnit.SECONDS).isBefore(enabledAt);
    }

    public static class Events {
        private boolean enabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
