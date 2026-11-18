package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentLikeDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.service.impl.CommentLikeStateService;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommentLikeStateTest {
    private static final long USER_ID = 9L;
    private static final long COMMENT_ID = 100L;
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private final CommentLikeDOMapper mapper = mock(CommentLikeDOMapper.class);
    private final RocketMQTemplate mq = mock(RocketMQTemplate.class);
    private final CommentLikeStateService service = new CommentLikeStateService();
    private final Map<String, Object> cached = new HashMap<>();
    private final CommentDO comment = CommentDO.builder().id(COMMENT_ID).noteId(1L).userId(3L).build();
    private final String stateKey = RedisKeyConstants.buildCommentLikeStateKey(USER_ID, COMMENT_ID);
    private final String lockKey = RedisKeyConstants.buildCommentLikeLockKey(USER_ID, COMMENT_ID);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        ReflectionTestUtils.setField(service, "commentLikeDOMapper", mapper);
        ReflectionTestUtils.setField(service, "rocketMQTemplate", mq);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq(lockKey), any(), eq(15L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(values.get(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
        doAnswer(call -> {
            cached.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), any(), anyLong(), eq(TimeUnit.SECONDS));
        SendResult sent = mock(SendResult.class);
        when(sent.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(mq.syncSendOrderly(anyString(), any(Message.class), eq(String.valueOf(USER_ID)), eq(3000L)))
                .thenReturn(sent);
    }

    @Test void likeUnlikeLikeUsesTheLatestStateWhileDatabaseStillReportsNoLike() {
        when(mapper.selectCountByUserIdAndCommentId(USER_ID, COMMENT_ID)).thenReturn(0);

        service.setLiked(comment, USER_ID, true);
        service.setLiked(comment, USER_ID, false);
        service.setLiked(comment, USER_ID, true);

        assertEquals(true, cached.get(stateKey));
        verify(mapper, times(1)).selectCountByUserIdAndCommentId(USER_ID, COMMENT_ID);
        ArgumentCaptor<String> destinations = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        verify(mq, times(3)).syncSendOrderly(destinations.capture(), messages.capture(),
                eq(String.valueOf(USER_ID)), eq(3000L));
        assertEquals(List.of(MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE,
                MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE,
                MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE), destinations.getAllValues());
        List<LikeUnlikeCommentMqDTO> operations = messages.getAllValues().stream()
                .map(message -> JsonUtils.parseObject((String) message.getPayload(), LikeUnlikeCommentMqDTO.class)).toList();
        assertEquals(List.of(1, 0, 1), operations.stream().map(LikeUnlikeCommentMqDTO::getType).toList());
        operations.forEach(operation -> {
            assertEquals(COMMENT_ID, operation.getCommentId());
            assertEquals(USER_ID, operation.getUserId());
            assertEquals(3L, operation.getCommentCreatorId());
            assertEquals(1L, operation.getNoteId());
        });
    }

    @Test void repeatingTheSameTargetDoesNotSendDuplicateMessages() {
        service.setLiked(comment, USER_ID, false);
        service.setLiked(comment, USER_ID, false);
        verifyNoInteractions(mq);

        service.setLiked(comment, USER_ID, true);
        service.setLiked(comment, USER_ID, true);
        service.setLiked(comment, USER_ID, false);
        service.setLiked(comment, USER_ID, false);

        assertEquals(false, cached.get(stateKey));
        verify(mq, times(2)).syncSendOrderly(anyString(), any(Message.class),
                eq(String.valueOf(USER_ID)), eq(3000L));
    }

    @Test void unsuccessfulSendKeepsThePreviousStateAndReleasesTheLock() {
        cached.put(stateKey, false);
        SendResult failed = mock(SendResult.class);
        when(failed.getSendStatus()).thenReturn(SendStatus.FLUSH_DISK_TIMEOUT);
        when(mq.syncSendOrderly(anyString(), any(Message.class), anyString(), anyLong())).thenReturn(failed);

        assertThrows(BizException.class, () -> service.setLiked(comment, USER_ID, true));

        assertEquals(false, cached.get(stateKey));
        verify(values, never()).set(eq(stateKey), any(), anyLong(), any(TimeUnit.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(lockKey)), any());
    }

    @Test void sendExceptionDoesNotRecordAnUnlikeAsSuccessful() {
        cached.put(stateKey, true);
        when(mq.syncSendOrderly(anyString(), any(Message.class), anyString(), anyLong()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThrows(IllegalStateException.class, () -> service.setLiked(comment, USER_ID, false));

        assertEquals(true, cached.get(stateKey));
        verify(values, never()).set(eq(stateKey), any(), anyLong(), any(TimeUnit.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(lockKey)), any());
    }

    @Test void competingRequestCannotReadOrChangeStateOrSendAnotherMessage() {
        when(values.setIfAbsent(eq(lockKey), any(), eq(15L), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThrows(BizException.class, () -> service.setLiked(comment, USER_ID, true));

        verify(values, never()).get(anyString());
        verifyNoInteractions(mapper, mq);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test void stateLookupUsesCachedFalseAndQueriesOnlyMissingDistinctIds() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L, 4L, 1L, null);
        List<String> keys = List.of(RedisKeyConstants.buildCommentLikeStateKey(USER_ID, 1L),
                RedisKeyConstants.buildCommentLikeStateKey(USER_ID, 2L),
                RedisKeyConstants.buildCommentLikeStateKey(USER_ID, 3L),
                RedisKeyConstants.buildCommentLikeStateKey(USER_ID, 4L));
        when(values.multiGet(keys)).thenReturn(Arrays.asList(true, false, null, null));
        when(mapper.selectLikedCommentIds(USER_ID, List.of(3L, 4L))).thenReturn(List.of(4L));

        assertEquals(Map.of(1L, true, 2L, false, 3L, false, 4L, true), service.findStates(USER_ID, ids));

        verify(mapper).selectLikedCommentIds(USER_ID, List.of(3L, 4L));
        verify(values, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test void anonymousAndEmptyLookupsDoNotQueryRedisOrDatabase() {
        assertEquals(Map.of(1L, false, 2L, false), service.findStates(null, Arrays.asList(1L, null, 2L, 1L)));
        assertEquals(Map.of(), service.findStates(USER_ID, List.of()));

        verifyNoInteractions(redis, mapper, mq);
    }
}
