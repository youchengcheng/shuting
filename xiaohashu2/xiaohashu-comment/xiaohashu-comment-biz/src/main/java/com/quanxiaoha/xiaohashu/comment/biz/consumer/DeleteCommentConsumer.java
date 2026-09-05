package com.quanxiaoha.xiaohashu.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.NoteCountDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.CommentLevelEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2024/8/30 11:27
 * @description: 删除评论 - 后续业务处理
 **/
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "xiaohashu_group_" + MQConstants.TOPIC_DELETE_COMMENT, // Group
        topic = MQConstants.TOPIC_DELETE_COMMENT // 消费的主题 Topic
)
public class DeleteCommentConsumer implements RocketMQListener<String>  {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    // 每秒创建 1000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(1000);

    @Override
    public void onMessage(String body) {
        // 令牌桶流控
        rateLimiter.acquire();

        log.info("## 【删除评论 - 后续业务处理】消费者消费成功, body: {}", body);

        CommentDO commentDO = JsonUtils.parseObject(body, CommentDO.class);

        // 评论级别
        Integer level = commentDO.getLevel();

        CommentLevelEnum commentLevelEnum = CommentLevelEnum.valueOf(level);

        switch (commentLevelEnum) {
            case ONE -> // 一级评论
                    handleOneLevelComment(commentDO);
            case TWO -> // 二级评论
                    handleTwoLevelComment(commentDO);
        }
    }

    /**
     * 一级评论处理
     * @param commentDO
     */
    private void handleOneLevelComment(CommentDO commentDO) {
        Long commentId = commentDO.getId();
        Long noteId = commentDO.getNoteId();

        // 关联评论删除（一级评论下所有子评论，都需要删除）
        int count = commentDOMapper.deleteByParentId(commentId);

        // 计数更新（笔记下总评论数）
        // 更新 Redis 缓存
        String redisKey = RedisKeyConstants.buildNoteCommentTotalKey(noteId);
        boolean hasKey = redisTemplate.hasKey(redisKey);

        if (hasKey) {
            // 笔记评论总数 -1
            redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_COMMENT_TOTAL, -(count + 1));
        }

        // 更新 t_note_count 计数表
        noteCountDOMapper.updateCommentTotalByNoteId(noteId, -(count + 1));

        // 一级评论及其子评论已经删除，废弃对应的子评论分页缓存
        redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(commentId));
    }

    /**
     * 二级评论处理
     * @param commentDO
     */
    private void handleTwoLevelComment(CommentDO commentDO) {
        Long commentId = commentDO.getId();

        // 1. 批量删除关联评论（递归查询回复评论，并批量删除）
        List<Long> replyCommentIds = Lists.newArrayList();
        recurrentGetReplyCommentId(replyCommentIds, commentId);

        // 被删除的行数
        int count = 0;
        if (CollUtil.isNotEmpty(replyCommentIds)) {
            count = commentDOMapper.deleteByIds(replyCommentIds);
        }

        Long parentCommentId = commentDO.getParentId();

        // 2. 更新一级评论的计数
        String redisKey = RedisKeyConstants.buildCountCommentKey(parentCommentId);

        boolean hasKey = redisTemplate.hasKey(redisKey);
        int deletedCommentTotal = count + 1;
        if (hasKey) {
            redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, -deletedCommentTotal);
        }

        // 同步更新数据库中的一级评论子评论总数，避免 Redis 失效后回源得到旧计数
        commentDOMapper.updateChildCommentTotal(parentCommentId, -deletedCommentTotal);

        // 二级评论也计入笔记评论总数，删除时同步扣减 Redis 和数据库计数
        String noteCommentTotalKey = RedisKeyConstants.buildNoteCommentTotalKey(commentDO.getNoteId());
        if (redisTemplate.hasKey(noteCommentTotalKey)) {
            redisTemplate.opsForHash().increment(noteCommentTotalKey,
                    RedisKeyConstants.FIELD_COMMENT_TOTAL, -deletedCommentTotal);
        }
        noteCountDOMapper.updateCommentTotalByNoteId(commentDO.getNoteId(), -deletedCommentTotal);

        // 3. 若是最早的发布的二级评论被删除，需要更新一级评论的 first_reply_comment_id
        // 查询一级评论
        CommentDO oneLevelCommentDO = commentDOMapper.selectByPrimaryKey(parentCommentId);
        Long firstReplyCommentId = oneLevelCommentDO.getFirstReplyCommentId();

        // 若删除的是最早回复的二级评论
        if (Objects.equals(firstReplyCommentId, commentId)) {
            // 查询数据库，重新获取一级评论最早回复的评论
            CommentDO earliestCommentDO = commentDOMapper.selectEarliestByParentId(parentCommentId);

            // 最早回复的那条评论 ID。若查询结果为 null, 则最早回复的评论 ID 为 null
            Long earliestCommentId = Objects.nonNull(earliestCommentDO) ? earliestCommentDO.getId() : null;
            // 更新其一级评论的 first_reply_comment_id
            commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(earliestCommentId, parentCommentId);
        }

        // 子评论删除会改变父评论的首条回复和数量，清理父评论详情缓存，避免返回旧数据
        // 同时使子评论分页 ZSET 失效，避免已级联删除的回复 ID 残留在缓存中
        redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(parentCommentId));
        redisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(parentCommentId));
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, parentCommentId,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.debug("==> 父评论本地缓存清理消息发送成功，commentId: {}", parentCommentId);
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.warn("==> 父评论本地缓存清理消息发送失败，commentId: {}", parentCommentId, throwable);
                    }
                });

        // 4. 重新计算一级评论的热度值
        Set<Long> commentIds = Sets.newHashSetWithExpectedSize(1);
        commentIds.add(parentCommentId);

        // 异步发送计数 MQ, 更新评论热度值
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentIds))
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论热度值更新】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论热度值更新】MQ 发送异常: ", throwable);
            }
        });
    }

    /**
     * 递归获取全部回复的评论 ID
     * @param commentIds
     * @param commentId
     */
    private void recurrentGetReplyCommentId(List<Long> commentIds, Long commentId) {
        List<CommentDO> replyCommentDOS = commentDOMapper.selectByReplyCommentId(commentId);

        if (CollUtil.isEmpty(replyCommentDOS)) return;

        // 一个评论可以被多条评论直接回复，递归收集每一棵回复树
        for (CommentDO replyCommentDO : replyCommentDOS) {
            Long replyCommentId = replyCommentDO.getId();
            commentIds.add(replyCommentId);
            recurrentGetReplyCommentId(commentIds, replyCommentId);
        }
    }


}
