package com.quanxiaoha.xiaohashu.note.biz.consumer;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.framework.notification.outbox.NotificationOutboxProperties;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteCollectionDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectSourceNotificationTest {
    NoteCollectionDOMapper mapper=mock(NoteCollectionDOMapper.class);
    NotificationOutboxWriter outbox=mock(NotificationOutboxWriter.class);
    RocketMQTemplate mq=mock(RocketMQTemplate.class);
    CollectUnCollectNoteConsumer consumer=new CollectUnCollectNoteConsumer();
    com.quanxiaoha.framework.notification.outbox.NotificationEventIsolationWriter isolation=mock(com.quanxiaoha.framework.notification.outbox.NotificationEventIsolationWriter.class);
    String body;
    @BeforeEach void setup() {
        TransactionTemplate tx=mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>)call.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        NotificationOutboxProperties props=new NotificationOutboxProperties(); props.getEvents().setEnabled(true);
        props.setEnabledAt(LocalDateTime.of(2026,11,14,0,0));
        ReflectionTestUtils.setField(consumer,"transactionTemplate",tx); ReflectionTestUtils.setField(consumer,"notificationOutboxProperties",props);
        ReflectionTestUtils.setField(consumer,"noteCollectionDOMapper",mapper); ReflectionTestUtils.setField(consumer,"notificationOutboxWriter",outbox);
        ReflectionTestUtils.setField(consumer,"rocketMQTemplate",mq);
        ReflectionTestUtils.setField(consumer,"notificationEventIsolationWriter",isolation);
        body=JsonUtils.toJsonString(CollectUnCollectNoteMqDTO.builder().userId(1L).noteId(2L).noteCreatorId(3L).type(1).createTime(LocalDateTime.of(2026,11,14,1,0)).build());
    }
    @Test void malformedJsonAndTimeAreIsolatedBeforeAck() {
        for (String payload : java.util.List.of("{", "{\"userId\":1,\"noteId\":2,\"createTime\":\"invalid-time\"}")) {
            ReflectionTestUtils.invokeMethod(consumer,"handleCollectNoteTagMessage",payload);
            verify(isolation).save(anyString(),eq(payload),anyString());
        }
        verifyNoInteractions(mapper,outbox,mq);
    }

    @Test void missingTimeAcknowledgesOnlyAfterDurableIsolation() {
        String invalid=JsonUtils.toJsonString(CollectUnCollectNoteMqDTO.builder().userId(1L).noteId(2L).type(1).build());
        ReflectionTestUtils.invokeMethod(consumer,"handleCollectNoteTagMessage",invalid);
        verify(isolation).save(anyString(),eq(invalid),anyString()); verifyNoInteractions(mapper,outbox,mq);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("isolation DB failed")).when(isolation).save(anyString(),anyString(),anyString());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->ReflectionTestUtils.invokeMethod(consumer,"handleCollectNoteTagMessage",invalid));
    }

    @Test void activeRepeatPreservesCountBranchWithoutNotification() {
        when(mapper.selectRelationForUpdate(1L,2L)).thenReturn(NoteCollectionDO.builder().status(1).build());
        when(mapper.insertOrUpdate(any())).thenReturn(2);
        ReflectionTestUtils.invokeMethod(consumer,"handleCollectNoteTagMessage",body);
        verifyNoInteractions(outbox);
        verify(mq).asyncSend(anyString(),any(org.springframework.messaging.Message.class),any(org.apache.rocketmq.client.producer.SendCallback.class));
    }
    @Test void trueTransitionNotificationDoesNotDependOnOriginalCountBranch() {
        when(mapper.selectRelationForUpdate(1L,2L)).thenReturn(NoteCollectionDO.builder().status(0).build());
        when(mapper.insertOrUpdate(any())).thenReturn(0);
        ReflectionTestUtils.invokeMethod(consumer,"handleCollectNoteTagMessage",body);
        verify(outbox).save(any()); verifyNoInteractions(mq);
    }
    @Test void failedOutboxStopsCountAndPropagatesToMq() {
        when(mapper.insertOrUpdate(any())).thenReturn(1); doThrow(new IllegalStateException("outbox failed")).when(outbox).save(any());
        assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(consumer,"handleCollectNoteTagMessage",body));
        verifyNoInteractions(mq);
    }
}
