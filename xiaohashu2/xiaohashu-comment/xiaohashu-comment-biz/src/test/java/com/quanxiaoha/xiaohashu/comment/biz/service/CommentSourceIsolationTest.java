package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.framework.notification.outbox.*;
import com.quanxiaoha.xiaohashu.comment.biz.consumer.Comment2DBConsumer;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.NoteAccessService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentSourceIsolationTest {
    Comment2DBConsumer consumer=new Comment2DBConsumer();
    CommentDOMapper mapper=mock(CommentDOMapper.class);
    NotificationEventIsolationWriter isolation=mock(NotificationEventIsolationWriter.class);
    CommentReplyValidator validator=mock(CommentReplyValidator.class);
    NoteAccessService access=mock(NoteAccessService.class);
    CommentNotificationPersistenceService persistence=mock(CommentNotificationPersistenceService.class);
    @BeforeEach void setup() {
        NotificationOutboxProperties props=new NotificationOutboxProperties(); props.getEvents().setEnabled(true); props.setEnabledAt(LocalDateTime.of(2026,11,14,0,0));
        ReflectionTestUtils.setField(consumer,"notificationProperties",props); ReflectionTestUtils.setField(consumer,"isolationWriter",isolation);
        ReflectionTestUtils.setField(consumer,"commentDOMapper",mapper); ReflectionTestUtils.setField(consumer,"replyValidator",validator);
        ReflectionTestUtils.setField(consumer,"noteAccessService",access); ReflectionTestUtils.setField(consumer,"notificationPersistence",persistence);
    }
    PublishCommentMqDTO event() { return PublishCommentMqDTO.builder().commentId(1L).noteId(2L).creatorId(3L).noteCreatorId(4L).createTime(LocalDateTime.of(2026,11,14,1,0)).build(); }
    MessageExt message(String payload,int retries) { MessageExt msg=new MessageExt(); msg.setBody(payload.getBytes(StandardCharsets.UTF_8)); msg.setReconsumeTimes(retries); return msg; }
    @Test void malformedJsonAndUnparseableTimeAreDurablyIsolated() {
        for (String payload : List.of("{", "{\"commentId\":1,\"noteId\":2,\"creatorId\":3,\"createTime\":\"invalid-time\"}")) {
            assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,consumer.consume(List.of(message(payload,0))));
            verify(isolation).save(anyString(),eq(payload),anyString());
        }
    }

    @Test void missingTimePersistsOriginalPayloadBeforeAckAndRetriesIfIsolationFails() {
        PublishCommentMqDTO dto=event(); dto.setCreateTime(null); String payload=JsonUtils.toJsonString(dto);
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,consumer.consume(List.of(message(payload,0))));
        verify(isolation).save(anyString(),eq(payload),anyString());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("isolation unavailable")).when(isolation).save(anyString(),anyString(),anyString());
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,consumer.consume(List.of(message(payload,0))));
    }
    @Test void absentReplyGetsBoundedRetryThenDurableIsolation() {
        PublishCommentMqDTO dto=event(); dto.setReplyCommentId(5L); String payload=JsonUtils.toJsonString(dto);
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,consumer.consume(List.of(message(payload,4)))); verifyNoInteractions(isolation);
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,consumer.consume(List.of(message(payload,5)))); verify(isolation).save(anyString(),eq(payload),anyString());
    }
    @Test void badReplyOwnershipIsIsolatedButMetadataDependencyFailureRetries() {
        PublishCommentMqDTO dto=event(); dto.setReplyCommentId(5L);
        when(mapper.selectByCommentIds(List.of(5L))).thenReturn(List.of(CommentDO.builder().id(5L).noteId(2L).build()));
        when(validator.requireRoot(eq(2L),any())).thenThrow(new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE));
        String payload=JsonUtils.toJsonString(dto);
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,consumer.consume(List.of(message(payload,0)))); verify(isolation).save(anyString(),eq(payload),anyString());
        reset(isolation); dto.setReplyCommentId(null); dto.setNoteCreatorId(null);
        when(access.requireAccessible(2L,3L)).thenThrow(new IllegalStateException("metadata unavailable"));
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,consumer.consume(List.of(message(JsonUtils.toJsonString(dto),0)))); verifyNoInteractions(isolation);
    }
}
