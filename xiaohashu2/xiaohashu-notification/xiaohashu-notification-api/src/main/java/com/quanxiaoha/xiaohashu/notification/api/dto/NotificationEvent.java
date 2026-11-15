package com.quanxiaoha.xiaohashu.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 互动通知事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 互动业务键
     */
    private String businessKey;

    /**
     * 事件来源服务
     */
    private String producerService;

    /**
     * 通知类型
     */
    private String type;

    /**
     * 互动用户 ID
     */
    private Long actorId;

    /**
     * 通知接收者
     */
    private List<NotificationRecipient> recipients;

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

}
