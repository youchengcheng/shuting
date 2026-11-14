package com.quanxiaoha.xiaohashu.notification.biz.model;

import java.util.List;
import java.util.Map;

public final class NotificationResponses {
    private NotificationResponses() { }
    public record Actor(String userId, String nickname, String avatar) { }
    public record Preview(String title, String cover, String summary, String imageUrl) { }
    public record Item(String notificationId, String seq, String type, String category, Actor actor,
                       String recipientRole, String noteId, String commentId, String replyCommentId,
                       String occurredAt, boolean read, String contentStatus, Preview preview) { }
    public record Page(List<Item> items, String nextCursor, boolean hasMore, String snapshotSeq, String revision) { }
    public record Summary(Map<String, Long> unreadCounts, long totalUnread, String lastSeq,
                          String revision, Map<String, String> readAllSeq) { }
    public record ReadItem(String notificationId, boolean read) { }
    public record ReadState(List<ReadItem> items, String revision, Map<String, String> readAllSeq) { }
    public record ReadResult(boolean changed, String revision, Summary summary, String notificationId, boolean read) { }
    public record ReadAllResult(boolean changed, String revision, Summary summary, String category, String snapshotSeq) { }
    public record Version(String revision, String lastSeq) { }
}
