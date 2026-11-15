package com.quanxiaoha.xiaohashu.comment.biz.service.impl;

import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentBO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentNotificationPersistenceService;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/*
 * 评论及通知事件持久化服务实现
 * */
@Service
public class CommentNotificationPersistenceServiceImpl implements CommentNotificationPersistenceService {

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private NotificationOutboxWriter notificationOutboxWriter;

    /*
     * 在同一事务中保存业务数据和通知事件
     * */
    @Override
    public int persist(List<CommentBO> comments, Map<Long, PublishCommentMqDTO> sources) {
        // 与评论消费者的批次大小保持一致，限制单个事务写入的记录数量
        if (comments.size() > 30) {
            throw new IllegalArgumentException("一次最多保存 30 条评论");
        }
        // 评论元数据和通知事件在同一事务中保存
        Integer count = transactionTemplate.execute(status -> {
            List<CommentBO> inserted = new ArrayList<>();
            for (CommentBO comment : comments) {
                // 只收集真实新增的评论，重复 ID 必须能查询到已保存记录
                int changed = commentDOMapper.insertNew(comment);
                if (changed == 1) {
                    inserted.add(comment);
                } else if (changed != 0 || commentDOMapper.selectByPrimaryKey(comment.getId()) == null) {
                    throw new IllegalStateException("评论未成功保存，且未查询到已存在的评论 ID");
                }
            }
            // 仅为本次新增评论写入内容和通知事件
            List<CommentBO> contentRows = inserted.stream().filter(c -> Boolean.FALSE.equals(c.getIsContentEmpty())).toList();
            if (!contentRows.isEmpty()) {
                // RPC 保存正文失败时抛出异常，使本地评论和 outbox 写入一同回滚
                keyValueRpcService.batchSaveCommentContent(contentRows);
            }
            for (CommentBO comment : inserted) {
                saveEvent(comment, sources.get(comment.getId()));
            }
            return inserted.size();
        });
        return count == null ? 0 : count;
    }

    /*
     * 保存评论通知事件
     * */
    private void saveEvent(CommentBO comment, PublishCommentMqDTO source) {
        // 新增评论须保留消息中校验过的笔记作者信息，不能猜测通知接收者
        if (source == null || source.getNoteCreatorId() == null) {
            throw new IllegalStateException("新增评论缺少已校验的笔记作者信息");
        }
        // 二级评论产生回复通知，一级评论产生评论通知
        String type = Objects.equals(comment.getLevel(), 2) ? "REPLY" : "COMMENT";
        // 优先添加被回复用户，用户同时为笔记作者时保留回复通知身份
        Map<Long, String> recipients = new LinkedHashMap<>();
        if (Objects.equals(type, "REPLY") && comment.getReplyUserId() != null && comment.getReplyUserId() > 0) {
            recipients.put(comment.getReplyUserId(), "REPLY_TARGET");
        }
        // 补充笔记作者并排除评论发布者本人，接收者重复时只保留一条
        recipients.putIfAbsent(source.getNoteCreatorId(), "OWNER");
        recipients.remove(comment.getUserId());
        if (recipients.isEmpty()) {
            return;
        }
        // 将通知事件写入当前事务的 outbox，业务提交成功后再异步投递
        notificationOutboxWriter.save(NotificationEvent.builder()
                .producerService("xiaohashu-comment")
                .type(type)
                .businessKey(NotificationBusinessKey.of(type, comment.getUserId(), comment.getId()))
                .actorId(comment.getUserId())
                .noteId(comment.getNoteId())
                .commentId(comment.getId())
                .replyCommentId(comment.getReplyCommentId())
                .occurredAt(comment.getCreateTime())
                .recipients(recipients.entrySet().stream().map(r -> NotificationRecipient.builder()
                        .userId(r.getKey())
                        .role(r.getValue())
                        .build()).toList())
                .build());
    }
}
