package com.quanxiaoha.xiaohashu.notification.api.dto;

import java.util.Set;

public final class NotificationBusinessKey {
    private static final Set<String> TYPES = Set.of("NOTE_LIKE", "COMMENT_LIKE", "NOTE_COLLECT", "COMMENT", "REPLY", "FOLLOW");

    private NotificationBusinessKey() { }

    public static String of(String type, Long actorId, Long objectId) {
        if (type == null || !TYPES.contains(type) || actorId == null || actorId <= 0 || objectId == null || objectId <= 0) {
            throw new IllegalArgumentException("Invalid notification business identity");
        }
        return type + ":" + actorId + ":" + objectId;
    }
}
