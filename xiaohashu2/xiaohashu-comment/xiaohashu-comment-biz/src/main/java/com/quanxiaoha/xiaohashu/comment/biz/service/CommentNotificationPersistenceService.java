package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentBO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CommentNotificationPersistenceService {
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private CommentDOMapper commentDOMapper;
    @Resource private KeyValueRpcService keyValueRpcService;
    @Resource private NotificationOutboxWriter notificationOutboxWriter;

    public static String contentUuid(Long commentId) {
        return UUID.nameUUIDFromBytes(("xiaohashu-comment:" + commentId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public int persist(List<CommentBO> comments, Map<Long, PublishCommentMqDTO> sources) {
        if (comments.size() > 30) throw new IllegalArgumentException("Comment persistence batch must contain at most 30 rows");
        Integer count = transactionTemplate.execute(status -> {
            List<CommentBO> inserted = new ArrayList<>();
            for (CommentBO comment : comments) {
                int changed = commentDOMapper.insertNew(comment);
                if (changed == 1) inserted.add(comment);
                else if (changed != 0 || commentDOMapper.selectByPrimaryKey(comment.getId()) == null)
                    throw new IllegalStateException("Comment insert did not persist a row or identify an existing ID");
            }
            List<CommentBO> contentRows = inserted.stream().filter(c -> Boolean.FALSE.equals(c.getIsContentEmpty())).toList();
            if (!contentRows.isEmpty()) keyValueRpcService.batchSaveCommentContent(contentRows);
            for (CommentBO comment : inserted) saveEvent(comment, sources.get(comment.getId()));
            return inserted.size();
        });
        return count == null ? 0 : count;
    }

    private void saveEvent(CommentBO comment, PublishCommentMqDTO source) {
        if (source == null || source.getNoteCreatorId() == null)
            throw new IllegalStateException("New comment has no authoritative note routing");
        String type = Objects.equals(comment.getLevel(), 2) ? "REPLY" : "COMMENT";
        // Add the reply recipient first so overlapping ownership keeps REPLY_TARGET semantics.
        Map<Long, String> recipients = new LinkedHashMap<>();
        if (Objects.equals(type, "REPLY") && comment.getReplyUserId() != null && comment.getReplyUserId() > 0)
            recipients.put(comment.getReplyUserId(), "REPLY_TARGET");
        recipients.putIfAbsent(source.getNoteCreatorId(), "OWNER");
        recipients.remove(comment.getUserId());
        if (recipients.isEmpty()) return;
        notificationOutboxWriter.save(NotificationEvent.builder().producerService("xiaohashu-comment").type(type)
                .businessKey(NotificationBusinessKey.of(type, comment.getUserId(), comment.getId()))
                .actorId(comment.getUserId()).noteId(comment.getNoteId()).commentId(comment.getId())
                .replyCommentId(comment.getReplyCommentId()).occurredAt(comment.getCreateTime())
                .recipients(recipients.entrySet().stream().map(r -> NotificationRecipient.builder().userId(r.getKey()).role(r.getValue()).build()).toList()).build());
    }
}
