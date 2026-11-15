package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知列表项
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationItemRspVO {

    /**
     * 通知 ID
     */
    private String notificationId;

    /**
     * 通知序号
     */
    private String seq;

    /**
     * 通知类型
     */
    private String type;

    /**
     * 通知分类
     */
    private String category;

    /**
     * 互动用户
     */
    private NotificationActorRspVO actor;

    /**
     * 接收者角色
     */
    private String recipientRole;

    /**
     * 笔记 ID
     */
    private String noteId;

    /**
     * 评论 ID
     */
    private String commentId;

    /**
     * 被回复评论 ID
     */
    private String replyCommentId;

    /**
     * 互动发生时间
     */
    private String occurredAt;

    /**
     * 是否已读
     */
    private boolean read;

    /**
     * 内容可访问状态
     */
    private String contentStatus;

    /**
     * 内容预览
     */
    private NotificationPreviewRspVO preview;

}
