package com.quanxiaoha.xiaohashu.user.relation.biz.service;

import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowNotificationPersistenceTest {
    FollowingDOMapper following = mock(FollowingDOMapper.class);
    FansDOMapper fans = mock(FansDOMapper.class);
    NotificationOutboxWriter outbox = mock(NotificationOutboxWriter.class);
    FollowNotificationPersistenceService service = new FollowNotificationPersistenceService();
    FollowUserMqDTO event = FollowUserMqDTO.builder().userId(1L).followingId(2L).createTime(LocalDateTime.of(2026,11,14,1,0)).build();
    @BeforeEach void setup() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>)call.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        ReflectionTestUtils.setField(service,"transactionTemplate",tx); ReflectionTestUtils.setField(service,"followingDOMapper",following);
        ReflectionTestUtils.setField(service,"fansDOMapper",fans); ReflectionTestUtils.setField(service,"notificationOutboxWriter",outbox);
    }
    @Test void duplicateIsNormalOnlyWhenBothRelationsExist() {
        when(following.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(following.selectRelationForUpdate(1L,2L)).thenReturn(FollowingDO.builder().userId(1L).followingUserId(2L).build());
        when(fans.selectRelationForUpdate(2L,1L)).thenReturn(FansDO.builder().userId(2L).fansUserId(1L).build());
        assertFalse(service.persist(event)); verifyNoInteractions(outbox);
        when(fans.selectRelationForUpdate(2L,1L)).thenReturn(null);
        assertThrows(DuplicateKeyException.class,()->service.persist(event));
    }
    @Test void outboxFailurePropagatesAfterBothSourceWrites() {
        when(following.insert(any())).thenReturn(1); when(fans.insert(any())).thenReturn(1);
        doThrow(new IllegalStateException("outbox failed")).when(outbox).save(any());
        assertThrows(IllegalStateException.class,()->service.persist(event));
        verify(following).insert(any()); verify(fans).insert(any());
    }
}
