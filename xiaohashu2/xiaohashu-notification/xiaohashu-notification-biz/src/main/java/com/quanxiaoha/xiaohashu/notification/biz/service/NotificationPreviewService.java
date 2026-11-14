package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.api.CommentFeignApi;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import com.quanxiaoha.xiaohashu.note.api.NoteFeignApi;
import com.quanxiaoha.xiaohashu.note.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.model.*;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.*;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class NotificationPreviewService {
    private final UserFeignApi users;
    private final NoteFeignApi notes;
    private final CommentFeignApi comments;

    public NotificationPreviewService(UserFeignApi users, NoteFeignApi notes, CommentFeignApi comments) {
        this.users = users;
        this.notes = notes;
        this.comments = comments;
    }

    public List<Item> render(long viewerId, List<NotificationRecord> records, NotificationUserState state) {
        if (records.isEmpty()) return List.of();
        Map<Long, FindUserByIdRspDTO> profiles = new HashMap<>();
        List<Long> actorIds = records.stream().map(NotificationRecord::getActorId).distinct().toList();
        for (int start = 0; start < actorIds.size(); start += 10) {
            try {
                Response<List<FindUserByIdRspDTO>> result = users.findByIds(FindUsersByIdsReqDTO.builder()
                        .ids(actorIds.subList(start, Math.min(start + 10, actorIds.size()))).build());
                if (result != null && result.isSuccess() && result.getData() != null) {
                    result.getData().forEach(profile -> profiles.put(profile.getId(), profile));
                }
            } catch (RuntimeException ignored) { /* Profiles can use neutral placeholders without exposing content. */ }
        }
        Map<Long, NotePreviewDTO> notePreviews = new HashMap<>();
        Map<Long, CommentPreviewDTO> commentPreviews = new HashMap<>();
        boolean noteLookupFailed = false;
        boolean commentLookupFailed = false;
        List<Long> noteIds = records.stream().map(NotificationRecord::getNoteId).filter(Objects::nonNull).distinct().toList();
        if (!noteIds.isEmpty()) {
            try {
                Response<List<NotePreviewDTO>> result = notes.previews(NoteBatchQueryReqDTO.builder().ids(noteIds).viewerId(viewerId).build());
                if (result == null || !result.isSuccess() || result.getData() == null) noteLookupFailed = true;
                else result.getData().forEach(note -> notePreviews.put(note.getId(), note));
            } catch (RuntimeException ex) { noteLookupFailed = true; }
        }
        List<Long> commentIds = records.stream().filter(record -> {
            NotePreviewDTO note = notePreviews.get(record.getNoteId());
            return note != null && Boolean.TRUE.equals(note.getAccessible());
        }).map(NotificationRecord::getCommentId).filter(Objects::nonNull).distinct().toList();
        if (!commentIds.isEmpty()) {
            try {
                Response<List<CommentPreviewDTO>> result = comments.previews(CommentBatchQueryReqDTO.builder().ids(commentIds).viewerId(viewerId).build());
                if (result == null || !result.isSuccess() || result.getData() == null) commentLookupFailed = true;
                else result.getData().forEach(comment -> commentPreviews.put(comment.getId(), comment));
            } catch (RuntimeException ex) { commentLookupFailed = true; }
        }
        List<Item> items = new ArrayList<>();
        for (NotificationRecord record : records) {
            FindUserByIdRspDTO profile = profiles.get(record.getActorId());
            Actor actor = new Actor(Long.toString(record.getActorId()), profile == null ? "用户" : profile.getNickName(),
                    profile == null ? "" : profile.getAvatar());
            String contentStatus = "AVAILABLE";
            Preview preview = new Preview("", "", "", "");
            if (!"FOLLOW".equals(record.getType())) {
                NotePreviewDTO note = notePreviews.get(record.getNoteId());
                if (noteLookupFailed) contentStatus = "TEMPORARY_UNAVAILABLE";
                else if (note == null || !Boolean.TRUE.equals(note.getAccessible())) contentStatus = "UNAVAILABLE";
                else if (record.getCommentId() != null) {
                    CommentPreviewDTO comment = commentPreviews.get(record.getCommentId());
                    if (commentLookupFailed) contentStatus = "TEMPORARY_UNAVAILABLE";
                    else if (comment == null || !Boolean.TRUE.equals(comment.getAccessible()) || !Objects.equals(comment.getNoteId(), record.getNoteId())) contentStatus = "UNAVAILABLE";
                    else preview = new Preview(text(note.getTitle()), text(note.getCover()), abbreviate(comment.getContent()), text(comment.getImageUrl()));
                } else preview = new Preview(text(note.getTitle()), text(note.getCover()), "", "");
            }
            // A denied/failed permission check must never retain even a formerly public title or cover.
            if (!"AVAILABLE".equals(contentStatus)) preview = new Preview("", "", "", "");
            items.add(new Item(Long.toString(record.getId()), Long.toString(record.getSeq()), record.getType(), record.getCategory(),
                    actor, record.getRecipientRole(), id(record.getNoteId()), id(record.getCommentId()), id(record.getReplyCommentId()),
                    record.getOccurredAt().toString(), state.isRead(record), contentStatus, preview));
        }
        return items;
    }

    private static String id(Long value) { return value == null ? null : Long.toString(value); }
    private static String text(String value) { return value == null ? "" : value; }
    private static String abbreviate(String value) {
        String text = text(value);
        return text.codePointCount(0, text.length()) <= 160 ? text : text.substring(0, text.offsetByCodePoints(0, 160));
    }
}
