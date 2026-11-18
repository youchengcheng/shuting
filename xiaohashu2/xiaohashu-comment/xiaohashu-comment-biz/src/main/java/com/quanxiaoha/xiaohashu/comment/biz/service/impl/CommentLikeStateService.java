package com.quanxiaoha.xiaohashu.comment.biz.service.impl;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentLikeDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 精确记录最近的点赞状态，避免布隆误判和 MQ 落库延迟影响再次切换。 */
@Service
@Slf4j
public class CommentLikeStateService {
    private static final long STATE_TTL_SECONDS = 60 * 60;
    private static final long SEND_TIMEOUT_MS = 3000;
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0", Long.class);

    @Resource private RedisTemplate<String, Object> redisTemplate;
    @Resource private CommentLikeDOMapper commentLikeDOMapper;
    @Resource private RocketMQTemplate rocketMQTemplate;

    public Map<Long, Boolean> findStates(Long userId, List<Long> commentIds) {
        List<Long> ids = commentIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Boolean> states = new HashMap<>();
        if (userId == null || ids.isEmpty()) {
            ids.forEach(id -> states.put(id, false));
            return states;
        }
        List<Object> cached = redisTemplate.opsForValue().multiGet(ids.stream()
                .map(id -> RedisKeyConstants.buildCommentLikeStateKey(userId, id)).toList());
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Object state = cached.get(i);
            if (state == null) {
                missing.add(ids.get(i));
            } else {
                states.put(ids.get(i), Boolean.parseBoolean(state.toString()));
            }
        }
        if (!missing.isEmpty()) {
            Set<Long> liked = new HashSet<>(commentLikeDOMapper.selectLikedCommentIds(userId, missing));
            missing.forEach(id -> states.put(id, liked.contains(id)));
        }
        return states;
    }

    public void setLiked(CommentDO comment, Long userId, boolean liked) {
        String stateKey = RedisKeyConstants.buildCommentLikeStateKey(userId, comment.getId());
        String lockKey = RedisKeyConstants.buildCommentLikeLockKey(userId, comment.getId());
        String token = UUID.randomUUID().toString();
        // 锁覆盖顺序发送及缓存写入；超时大于 MQ 发送超时，释放时校验持有者。
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, 15, TimeUnit.SECONDS))) {
            throw new BizException(ResponseCodeEnum.COMMENT_LIKE_PROCESSING);
        }
        try {
            Object cached = redisTemplate.opsForValue().get(stateKey);
            boolean current = cached == null
                    ? commentLikeDOMapper.selectCountByUserIdAndCommentId(userId, comment.getId()) > 0
                    : Boolean.parseBoolean(cached.toString());
            // 同一目标状态的重试成功返回，不重复通知或增减计数。
            if (current == liked) {
                return;
            }
            LikeUnlikeCommentMqDTO operation = LikeUnlikeCommentMqDTO.builder()
                    .commentId(comment.getId()).commentCreatorId(comment.getUserId()).noteId(comment.getNoteId())
                    .userId(userId).type(liked ? LikeUnlikeCommentTypeEnum.LIKE.getCode() : LikeUnlikeCommentTypeEnum.UNLIKE.getCode())
                    .createTime(LocalDateTime.now()).build();
            String destination = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":"
                    + (liked ? MQConstants.TAG_LIKE : MQConstants.TAG_UNLIKE);
            SendResult result = rocketMQTemplate.syncSendOrderly(destination,
                    MessageBuilder.withPayload(JsonUtils.toJsonString(operation)).build(), String.valueOf(userId), SEND_TIMEOUT_MS);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
            }
            // 只有 MQ 确认接收后才记录新状态。发送异常由接口返回失败，前端可恢复按钮。
            redisTemplate.opsForValue().set(stateKey, liked, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        } finally {
            try {
                redisTemplate.execute(UNLOCK, Collections.singletonList(lockKey), token);
            } catch (RuntimeException error) {
                log.warn("释放评论点赞锁失败，等待锁自动过期: {}", lockKey, error);
            }
        }
    }
}
