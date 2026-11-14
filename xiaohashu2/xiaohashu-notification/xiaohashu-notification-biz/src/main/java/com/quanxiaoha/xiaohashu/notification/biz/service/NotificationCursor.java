package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationCategory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record NotificationCursor(long recipientId, NotificationCategory category, long snapshotSeq, long beforeSeq) {
    public String encode() {
        String value = recipientId + ":" + category + ":" + snapshotSeq + ":" + beforeSeq;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static NotificationCursor decode(String value, long recipientId, NotificationCategory category, long snapshot) {
        try {
            if (value == null || value.length() > 256) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 4) throw new IllegalArgumentException();
            NotificationCursor cursor = new NotificationCursor(Long.parseLong(parts[0]), NotificationCategory.valueOf(parts[1]),
                    Long.parseLong(parts[2]), Long.parseLong(parts[3]));
            if (cursor.recipientId != recipientId || cursor.category != category || cursor.snapshotSeq != snapshot
                    || cursor.beforeSeq <= 0 || cursor.beforeSeq > snapshot) throw new IllegalArgumentException();
            return cursor;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid notification cursor");
        }
    }
}
