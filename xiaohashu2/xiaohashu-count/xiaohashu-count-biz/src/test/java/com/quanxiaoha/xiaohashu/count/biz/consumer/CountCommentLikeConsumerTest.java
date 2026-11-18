package com.quanxiaoha.xiaohashu.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.count.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.CountLikeUnlikeCommentMqDTO;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CountCommentLikeConsumerTest {
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    private final RocketMQTemplate mq = mock(RocketMQTemplate.class);
    private final CountCommentLikeConsumer consumer = new CountCommentLikeConsumer();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(consumer, "redisTemplate", redis);
        ReflectionTestUtils.setField(consumer, "rocketMQTemplate", mq);
        when(redis.hasKey(anyString())).thenReturn(true);
        when(redis.opsForHash()).thenReturn(hashes);
    }

    @AfterEach void closeBuffer() {
        ((BufferTrigger<?>) ReflectionTestUtils.getField(consumer, "bufferTrigger")).close();
    }

    @Test void likeUnlikeLikeUpdatesAndPublishesOneNetIncrement() throws Exception {
        ReflectionTestUtils.invokeMethod(consumer, "consumeMessage", List.of(
                operation(100L, 1), operation(100L, 0), operation(100L, 1)));

        verify(redis).hasKey(RedisKeyConstants.buildCountCommentKey(100L));
        verify(hashes).increment(RedisKeyConstants.buildCountCommentKey(100L),
                RedisKeyConstants.FIELD_LIKE_TOTAL, 1L);
        verifyNoMoreInteractions(hashes);
        assertEquals(Map.of(100L, 1), publishedCounts());
    }

    @Test void multipleCommentGroupsUpdateEachCommentOnceAndPublishOneBatch() throws Exception {
        ReflectionTestUtils.invokeMethod(consumer, "consumeMessage", List.of(
                operation(100L, 1), operation(200L, 0),
                operation(100L, 0), operation(100L, 1)));

        verify(redis).hasKey(RedisKeyConstants.buildCountCommentKey(100L));
        verify(redis).hasKey(RedisKeyConstants.buildCountCommentKey(200L));
        verify(hashes).increment(RedisKeyConstants.buildCountCommentKey(100L),
                RedisKeyConstants.FIELD_LIKE_TOTAL, 1L);
        verify(hashes).increment(RedisKeyConstants.buildCountCommentKey(200L),
                RedisKeyConstants.FIELD_LIKE_TOTAL, -1L);
        verifyNoMoreInteractions(hashes);
        assertEquals(Map.of(100L, 1, 200L, -1), publishedCounts());
    }

    private String operation(Long commentId, Integer type) {
        return JsonUtils.toJsonString(CountLikeUnlikeCommentMqDTO.builder()
                .userId(9L).commentId(commentId).type(type).build());
    }

    private Map<Long, Integer> publishedCounts() throws Exception {
        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        verify(mq).asyncSend(eq(MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB),
                messages.capture(), any(SendCallback.class));
        verifyNoMoreInteractions(mq);
        List<AggregationCountLikeUnlikeCommentMqDTO> counts = JsonUtils.parseList(
                (String) messages.getValue().getPayload(), AggregationCountLikeUnlikeCommentMqDTO.class);
        return counts.stream().collect(Collectors.toMap(
                AggregationCountLikeUnlikeCommentMqDTO::getCommentId,
                AggregationCountLikeUnlikeCommentMqDTO::getCount));
    }
}
