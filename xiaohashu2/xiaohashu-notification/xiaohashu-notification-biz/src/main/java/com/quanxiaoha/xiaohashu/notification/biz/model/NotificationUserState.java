package com.quanxiaoha.xiaohashu.notification.biz.model;

import lombok.Data;

@Data
public class NotificationUserState {
    private Long recipientId;
    private long lastSeq;
    private long revision;
    private long likeCollectionReadSeq;
    private long commentReplyReadSeq;
    private long followReadSeq;

    public long watermark(NotificationCategory category) {
        return switch (category) {
            case LIKE_COLLECTION -> likeCollectionReadSeq;
            case COMMENT_REPLY -> commentReplyReadSeq;
            case FOLLOW -> followReadSeq;
        };
    }

    public boolean isRead(NotificationRecord record) {
        return record.getReadAt() != null || record.getSeq() <= watermark(NotificationCategory.valueOf(record.getCategory()));
    }

    public static NotificationUserState empty(Long recipientId) {
        NotificationUserState state = new NotificationUserState();
        state.setRecipientId(recipientId);
        return state;
    }
}
