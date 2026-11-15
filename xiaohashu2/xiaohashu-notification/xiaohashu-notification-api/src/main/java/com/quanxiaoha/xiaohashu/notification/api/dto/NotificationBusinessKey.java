package com.quanxiaoha.xiaohashu.notification.api.dto;

import java.util.Set;

/**
 * 通知互动业务键
 */
public final class NotificationBusinessKey {
    // 支持生成通知的互动类型
    private static final Set<String> TYPES = Set.of("NOTE_LIKE", "COMMENT_LIKE", "NOTE_COLLECT", "COMMENT", "REPLY", "FOLLOW");

    private NotificationBusinessKey() {
    }

    /**
     * 使用互动类型、操作者和目标对象生成幂等业务键
     */
    public static String of(String type, Long actorId, Long objectId) {
        // 互动类型必须受支持，操作者和目标对象须为有效的正整数 ID
        if (type == null || !TYPES.contains(type) || actorId == null || actorId <= 0 || objectId == null || objectId <= 0) {
            throw new IllegalArgumentException("通知互动类型、操作者或目标对象无效");
        }
        // 同一用户对同一对象的同类互动生成相同业务键，用于重复投递去重
        return type + ":" + actorId + ":" + objectId;
    }
}
