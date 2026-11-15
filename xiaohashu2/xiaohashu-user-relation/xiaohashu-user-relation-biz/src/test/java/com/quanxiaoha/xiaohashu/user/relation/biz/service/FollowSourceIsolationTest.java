package com.quanxiaoha.xiaohashu.user.relation.biz.service;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.framework.notification.outbox.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.consumer.FollowUnfollowConsumer;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowSourceIsolationTest {
    @Test void missingTimeRequiresDurableIsolationBeforeAck() {
        FollowUnfollowConsumer consumer=new FollowUnfollowConsumer();
        NotificationOutboxProperties props=new NotificationOutboxProperties(); props.getEvents().setEnabled(true);
        NotificationEventIsolationWriter isolation=mock(NotificationEventIsolationWriter.class);
        FollowNotificationPersistenceService persistence=mock(FollowNotificationPersistenceService.class);
        ReflectionTestUtils.setField(consumer,"notificationOutboxProperties",props); ReflectionTestUtils.setField(consumer,"notificationEventIsolationWriter",isolation);
        ReflectionTestUtils.setField(consumer,"followNotificationPersistenceService",persistence);
        String payload=JsonUtils.toJsonString(FollowUserMqDTO.builder().userId(1L).followingId(2L).build());
        ReflectionTestUtils.invokeMethod(consumer,"handleFollowTagMessage",payload);
        verify(isolation).save(anyString(),eq(payload),anyString()); verifyNoInteractions(persistence);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("isolation DB failed")).when(isolation).save(anyString(),anyString(),anyString());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->ReflectionTestUtils.invokeMethod(consumer,"handleFollowTagMessage",payload));
    }
    @Test void malformedJsonAndTimeAreIsolatedBeforeAck() {
        FollowUnfollowConsumer consumer=new FollowUnfollowConsumer();
        NotificationOutboxProperties props=new NotificationOutboxProperties(); props.getEvents().setEnabled(true);
        NotificationEventIsolationWriter isolation=mock(NotificationEventIsolationWriter.class);
        ReflectionTestUtils.setField(consumer,"notificationOutboxProperties",props); ReflectionTestUtils.setField(consumer,"notificationEventIsolationWriter",isolation);
        for (String payload : java.util.List.of("{", "{\"userId\":1,\"followingId\":2,\"createTime\":\"invalid-time\"}")) {
            ReflectionTestUtils.invokeMethod(consumer,"handleFollowTagMessage",payload);
            verify(isolation).save(anyString(),eq(payload),anyString());
        }
    }

}
