package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentBO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentNotificationPersistenceTest {
    CommentDOMapper mapper = mock(CommentDOMapper.class);
    KeyValueRpcService kv = mock(KeyValueRpcService.class);
    NotificationOutboxWriter outbox = mock(NotificationOutboxWriter.class);
    CommentNotificationPersistenceService service = new CommentNotificationPersistenceService();
    @BeforeEach void setup() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>)call.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        ReflectionTestUtils.setField(service, "transactionTemplate", tx);
        ReflectionTestUtils.setField(service, "commentDOMapper", mapper);
        ReflectionTestUtils.setField(service, "keyValueRpcService", kv);
        ReflectionTestUtils.setField(service, "notificationOutboxWriter", outbox);
    }
    CommentBO row(Long id, Long actor) {
        return CommentBO.builder().id(id).noteId(10L).userId(actor).level(2).parentId(100L).replyCommentId(101L)
                .replyUserId(2L).createTime(LocalDateTime.of(2026,11,14,1,0)).isContentEmpty(false)
                .contentUuid(CommentNotificationPersistenceService.contentUuid(id)).content("content").build();
    }
    @Test void onlyExactlyInsertedIdsWriteContentAndNotifyWithReplyPriority() {
        CommentBO old = row(1L, 3L), fresh = row(2L, 3L);
        when(mapper.insertNew(old)).thenReturn(0); when(mapper.selectByPrimaryKey(1L)).thenReturn(CommentDO.builder().id(1L).build());
        when(mapper.insertNew(fresh)).thenReturn(1);
        Map<Long, PublishCommentMqDTO> source = Map.of(2L, PublishCommentMqDTO.builder().noteCreatorId(2L).build());
        assertEquals(1, service.persist(List.of(old,fresh), source));
        verify(kv).batchSaveCommentContent(List.of(fresh));
        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outbox).save(event.capture());
        assertEquals(2L, event.getValue().getCommentId());
        assertEquals(1, event.getValue().getRecipients().size());
        assertEquals("REPLY_TARGET", event.getValue().getRecipients().get(0).getRole());
    }
    @Test void selfInteractionIsRemovedAndOutboxFailuresPropagate() {
        CommentBO fresh = row(2L,2L); when(mapper.insertNew(fresh)).thenReturn(1);
        service.persist(List.of(fresh), Map.of(2L,PublishCommentMqDTO.builder().noteCreatorId(2L).build()));
        verifyNoInteractions(outbox);
        CommentBO another = row(3L,3L); when(mapper.insertNew(another)).thenReturn(1);
        doThrow(new IllegalStateException("outbox failed")).when(outbox).save(any());
        assertThrows(IllegalStateException.class, () -> service.persist(List.of(another),Map.of(3L,PublishCommentMqDTO.builder().noteCreatorId(2L).build())));
    }
    @Test void contentIdentityIsStableAcrossRedelivery() {
        assertEquals(CommentNotificationPersistenceService.contentUuid(12L), CommentNotificationPersistenceService.contentUuid(12L));
        assertNotEquals(CommentNotificationPersistenceService.contentUuid(12L), CommentNotificationPersistenceService.contentUuid(13L));
    }
}
