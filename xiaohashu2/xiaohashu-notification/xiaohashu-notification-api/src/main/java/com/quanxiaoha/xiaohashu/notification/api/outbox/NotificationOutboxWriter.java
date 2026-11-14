package com.quanxiaoha.xiaohashu.notification.api.outbox;

import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;

/** Implementations persist an event in the caller's local business transaction. */
public interface NotificationOutboxWriter {
    void save(NotificationEvent event);
}
