package com.quanxiaoha.xiaohashu.notification.biz.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationRecord {
    private Long id;
    private String businessKey;
    private Long recipientId;
    private Long actorId;
    private String type;
    private String category;
    private String recipientRole;
    private Long noteId;
    private Long commentId;
    private Long replyCommentId;
    private LocalDateTime occurredAt;
    private Long seq;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
