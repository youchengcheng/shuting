package com.quanxiaoha.xiaohashu.notification.biz.model;

public enum NotificationCategory {
    LIKE_COLLECTION, COMMENT_REPLY, FOLLOW;

    public static NotificationCategory forType(String type) {
        if (type == null) throw new IllegalArgumentException("Missing notification type");
        return switch (type) {
            case "NOTE_LIKE", "COMMENT_LIKE", "NOTE_COLLECT" -> LIKE_COLLECTION;
            case "COMMENT", "REPLY" -> COMMENT_REPLY;
            case "FOLLOW" -> FOLLOW;
            default -> throw new IllegalArgumentException("Unknown notification type");
        };
    }
}
