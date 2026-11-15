package com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知记录
 */
@Data
public class NotificationDO {

    /**
     * 通知 ID
     */
    private Long id;

    /**
     * 互动业务键
     */
    private String businessKey;

    /**
     * 接收者用户 ID
     */
    private Long recipientId;

    /**
     * 互动用户 ID
     */
    private Long actorId;

    /**
     * 通知类型
     */
    private String type;

    /**
     * 通知分类
     */
    private String category;

    /**
     * 接收者角色
     */
    private String recipientRole;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 评论 ID
     */
    private Long commentId;

    /**
     * 被回复评论 ID
     */
    private Long replyCommentId;

    /**
     * 互动发生时间
     */
    private LocalDateTime occurredAt;

    /**
     * 通知序号
     */
    private Long seq;

    /**
     * 已读时间
     */
    private LocalDateTime readAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
