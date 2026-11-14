package com.quanxiaoha.xiaohashu.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventId;
    private String businessKey;
    private String producerService;
    private String type;
    private Long actorId;
    private List<NotificationRecipient> recipients;
    private Long noteId;
    private Long commentId;
    private Long replyCommentId;
    private LocalDateTime occurredAt;
}
