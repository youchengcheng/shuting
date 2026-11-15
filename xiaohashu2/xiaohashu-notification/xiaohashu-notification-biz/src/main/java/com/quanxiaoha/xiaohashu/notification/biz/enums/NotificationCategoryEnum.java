package com.quanxiaoha.xiaohashu.notification.biz.enums;

/**
 * 通知分类
 */
public enum NotificationCategoryEnum {

    // 点赞和收藏
    LIKE_COLLECTION,

    // 评论和回复
    COMMENT_REPLY,

    // 关注
    FOLLOW;

    /*
     * 根据互动类型获取通知分类
     * */
    public static NotificationCategoryEnum forType(String type) {
        if (type == null) {
            throw new IllegalArgumentException("缺少通知类型");
        }

        return switch (type) {
            case "NOTE_LIKE", "COMMENT_LIKE", "NOTE_COLLECT" -> LIKE_COLLECTION;
            case "COMMENT", "REPLY" -> COMMENT_REPLY;
            case "FOLLOW" -> FOLLOW;
            default -> throw new IllegalArgumentException("未知的通知类型");
        };
    }
}
