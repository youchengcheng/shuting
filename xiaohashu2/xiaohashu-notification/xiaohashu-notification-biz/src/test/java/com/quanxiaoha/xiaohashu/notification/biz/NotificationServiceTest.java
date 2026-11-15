package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.*;
import com.quanxiaoha.xiaohashu.notification.biz.domain.mapper.NotificationDOMapper;
import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationCursorUtils;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationEventUtils;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationParamUtils;
import org.springframework.test.util.ReflectionTestUtils;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.NotificationCursorDTO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;
import com.quanxiaoha.xiaohashu.notification.biz.service.impl.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 14, 0, 0);
    static final long USER = 9223372036854775806L;
    private NotificationDOMapper mapper;
    private NotificationChangePublisher changes;
    private NotificationPreviewService previews;
    private PlatformTransactionManager transactions;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationDOMapper.class);
        changes = mock(NotificationChangePublisher.class);
        previews = mock(NotificationPreviewService.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        NotificationSettings settings = new NotificationSettings();
        settings.setEnabled(true);
        settings.setEnabledAt(CUTOFF.toString());
        NotificationServiceImpl impl = new NotificationServiceImpl();
        ReflectionTestUtils.setField(impl, "notificationDOMapper", mapper);
        ReflectionTestUtils.setField(impl, "notificationSettings", settings);
        ReflectionTestUtils.setField(impl, "notificationChangePublisher", changes);
        ReflectionTestUtils.setField(impl, "notificationPreviewService", previews);
        ReflectionTestUtils.setField(impl, "transactionManager", transactions);
        impl.initTransactions();
        service = impl;
    }

    @Test
    void firstEventAllocatesOneSequenceAndPublishesOnlyAfterCommit() {
        when(mapper.lockState(USER)).thenReturn(state(USER, 7, 11));
        service.consume(event("NOTE_LIKE", USER));
        var record = org.mockito.ArgumentCaptor.forClass(NotificationDO.class);
        verify(mapper).insert(record.capture());
        assertThat(record.getValue().getSeq()).isEqualTo(8);
        assertThat(record.getValue().getCategory()).isEqualTo("LIKE_COLLECTION");
        assertThat(record.getValue().getReadAt()).isNull();
        verify(mapper).advance(USER, 8);
        var order = inOrder(transactions, changes);
        order.verify(transactions).commit(any());
        var changed = org.mockito.ArgumentCaptor.forClass(NotificationUserStateDO.class);
        order.verify(changes).publish(changed.capture());
        assertThat(changed.getValue().getLastSeq()).isEqualTo(8);
        assertThat(changed.getValue().getRevision()).isEqualTo(12);
    }

    @Test
    void duplicateDoesNotAllocateSequenceResetReadOrPublish() {
        NotificationDO existing = row(USER, 1, 7, "LIKE_COLLECTION");
        existing.setReadAt(CUTOFF);
        when(mapper.lockState(USER)).thenReturn(state(USER, 7, 12));
        when(mapper.byBusinessKey(USER, event("NOTE_LIKE", USER).getBusinessKey())).thenReturn(existing);
        service.consume(event("NOTE_LIKE", USER));
        verify(mapper, never()).insert(any());
        verify(mapper, never()).advance(anyLong(), anyLong());
        verify(mapper, never()).markRead(anyLong(), anyLong());
        verifyNoInteractions(changes);
        assertThat(existing.getReadAt()).isEqualTo(CUTOFF);
    }

    @Test
    void selfInteractionIsExcludedAndDuplicateReplyRecipientPrefersReplyRole() {
        NotificationEvent self = event("NOTE_LIKE", 10);
        service.consume(self);
        verifyNoInteractions(mapper, changes, transactions);
        NotificationEvent reply = event("REPLY", USER);
        reply.setRecipients(List.of(new NotificationRecipient(USER, "OWNER"), new NotificationRecipient(USER, "REPLY_TARGET")));
        when(mapper.lockState(USER)).thenReturn(state(USER, 0, 0));
        service.consume(reply);
        var inserted = org.mockito.ArgumentCaptor.forClass(NotificationDO.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRecipientRole()).isEqualTo("REPLY_TARGET");
        verify(changes, times(1)).publish(any());
    }

    @Test
    void partialReplyRetryCompletesOnlyRecipientWhoseTransactionFailed() {
        long owner = 20, target = 30;
        NotificationEvent reply = event("REPLY", owner);
        reply.setRecipients(List.of(new NotificationRecipient(owner, "OWNER"), new NotificationRecipient(target, "REPLY_TARGET")));
        when(mapper.lockState(anyLong())).thenAnswer(i -> state(i.getArgument(0), 0, 0));
        when(mapper.byBusinessKey(owner, reply.getBusinessKey())).thenReturn(null, row(owner, 1, 1, "COMMENT_REPLY"));
        AtomicInteger targetAttempts = new AtomicInteger();
        doAnswer(i -> {
            NotificationDO record = i.getArgument(0);
            if (record.getRecipientId() == target && targetAttempts.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("temporary database failure");
            }
            return 1;
        }).when(mapper).insert(any());
        assertThatThrownBy(() -> service.consume(reply)).isInstanceOf(DataAccessResourceFailureException.class);
        service.consume(reply);
        verify(mapper, times(1)).advance(owner, 1);
        verify(mapper, times(1)).advance(target, 1);
        verify(transactions).rollback(any());
        var notifications = org.mockito.ArgumentCaptor.forClass(NotificationUserStateDO.class);
        verify(changes, times(2)).publish(notifications.capture());
        assertThat(notifications.getAllValues()).extracting(NotificationUserStateDO::getRecipientId).containsExactly(owner, target);
    }

    @Test
    void cutoffIgnoresHistoricalIncompleteEventButAcceptsExactBoundary() {
        NotificationEvent old = new NotificationEvent();
        old.setOccurredAt(CUTOFF.minusSeconds(1));
        service.consume(old);
        verifyNoInteractions(mapper, changes, transactions);
        when(mapper.lockState(USER)).thenReturn(state(USER, 0, 0));
        service.consume(event("COMMENT_LIKE", USER));
        verify(mapper).insert(any());
        NotificationEvent malformedNew = new NotificationEvent();
        malformedNew.setOccurredAt(CUTOFF);
        assertThatThrownBy(() -> service.consume(malformedNew)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mutationFailureRollsBackAndNeverPublishes() {
        when(mapper.lockState(USER)).thenReturn(state(USER, 1, 1));
        when(mapper.insert(any())).thenThrow(new DataAccessResourceFailureException("database failure"));
        assertThatThrownBy(() -> service.consume(event("NOTE_LIKE", USER))).isInstanceOf(DataAccessResourceFailureException.class);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        verifyNoInteractions(changes);
    }

    @Test
    void singleReadIsScopedToOwnerAndWatermarkMakesItANoOp() {
        NotificationUserStateDO state = state(USER, 10, 12);
        state.setLikeCollectionReadSeq(8);
        when(mapper.lockState(USER)).thenReturn(state);
        when(mapper.byId(USER, 1L)).thenReturn(row(USER, 1, 7, "LIKE_COLLECTION"));
        ReadNotificationRspVO result = service.markRead(USER, "1");
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getRevision()).isEqualTo("12");
        assertThat(result.isRead()).isTrue();
        verify(mapper, never()).markRead(anyLong(), anyLong());
        verify(mapper, never()).bumpRevision(anyLong());
        verifyNoInteractions(changes);
        assertThatThrownBy(() -> service.markRead(USER, "2"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo("NOTIFICATION_404"));
        verify(mapper).byId(USER, 2L);
    }

    @Test
    void bulkReadMovesOnlyChosenCategoryAndRepeatedOlderSnapshotCannotRegressIt() {
        NotificationUserStateDO state = state(USER, 10, 12);
        state.setLikeCollectionReadSeq(3);
        state.setFollowReadSeq(4);
        when(mapper.lockState(USER)).thenReturn(state);
        ReadAllNotificationRspVO changed = service.markAllRead(USER, "LIKE_COLLECTION", "8");
        assertThat(changed.isChanged()).isTrue();
        assertThat(changed.getRevision()).isEqualTo("13");
        assertThat(changed.getSummary().getReadAllSeq()).containsEntry("LIKE_COLLECTION", "8").containsEntry("FOLLOW", "4");
        assertThat(state.isRead(row(USER, 1, 8, "LIKE_COLLECTION"))).isTrue();
        assertThat(state.isRead(row(USER, 2, 9, "LIKE_COLLECTION"))).isFalse();
        ReadAllNotificationRspVO stale = service.markAllRead(USER, "LIKE_COLLECTION", "7");
        assertThat(stale.isChanged()).isFalse();
        assertThat(stale.getRevision()).isEqualTo("13");
        verify(mapper, times(1)).setWatermark(USER, "LIKE_COLLECTION", 8);
        verify(changes, times(1)).publish(any());
        assertThatThrownBy(() -> service.markAllRead(USER, "FOLLOW", "11")).isInstanceOf(BizException.class);
    }

    @Test
    void bulkReadFailureRollsBackWithoutPublishing() {
        when(mapper.lockState(USER)).thenReturn(state(USER, 10, 12));
        when(mapper.setWatermark(USER, "FOLLOW", 8)).thenThrow(new DataAccessResourceFailureException("database failure"));
        assertThatThrownBy(() -> service.markAllRead(USER, "FOLLOW", "8")).isInstanceOf(DataAccessResourceFailureException.class);
        verify(transactions).rollback(any());
        verifyNoInteractions(changes);
    }

    @Test
    void pageKeepsNineteenDigitSnapshotAndCursorBoundToUserCategoryAndSnapshot() {
        long snapshot = Long.MAX_VALUE - 1;
        when(mapper.state(USER)).thenReturn(state(USER, snapshot, Long.MAX_VALUE - 2));
        when(mapper.state(USER - 1)).thenReturn(state(USER - 1, snapshot, Long.MAX_VALUE - 2));
        List<NotificationDO> records = List.of(row(USER, 11, snapshot, "LIKE_COLLECTION"), row(USER, 12, snapshot - 1, "LIKE_COLLECTION"));
        when(mapper.page(USER, "LIKE_COLLECTION", snapshot, 0, 2)).thenReturn(records);
        when(previews.render(eq(USER), anyList(), any())).thenReturn(List.of());
        FindNotificationPageRspVO page = service.list(USER, "LIKE_COLLECTION", null, null, 1);
        assertThat(page.getSnapshotSeq()).isEqualTo("9223372036854775806");
        assertThat(page.getRevision()).isEqualTo("9223372036854775805");
        assertThat(page.isHasMore()).isTrue();
        assertThat(NotificationCursorUtils.decode(page.getNextCursor(), USER, NotificationCategoryEnum.LIKE_COLLECTION, snapshot).getBeforeSeq()).isEqualTo(snapshot);
        assertThatThrownBy(() -> service.list(USER - 1, "LIKE_COLLECTION", page.getNextCursor(), page.getSnapshotSeq(), 1)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.list(USER, "FOLLOW", page.getNextCursor(), page.getSnapshotSeq(), 1)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.list(USER, "LIKE_COLLECTION", page.getNextCursor(), Long.toString(snapshot - 1), 1)).isInstanceOf(BizException.class);
    }

    @Test
    void identifiersAndPagingRejectAmbiguousOrOverflowingInput() {
        assertThat(NotificationParamUtils.parseDecimal(Long.toString(Long.MAX_VALUE), true)).isEqualTo(Long.MAX_VALUE);
        for (String invalid : List.of("-1", "+1", "1.0", " 1", "9223372036854775808", "0")) {
            assertThatThrownBy(() -> NotificationParamUtils.parseDecimal(invalid, true)).isInstanceOf(BizException.class);
        }
        assertThatThrownBy(() -> service.list(USER, "FOLLOW", "cursor", null, 1)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.list(USER, "FOLLOW", null, null, 51)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.readState(USER, List.of())).isInstanceOf(BizException.class);
    }

    @Test
    void routesBusinessIdentityAndRecipientRolesMustAgree() {
        for (String type : List.of("NOTE_LIKE", "COMMENT_LIKE", "NOTE_COLLECT", "COMMENT", "REPLY", "FOLLOW")) {
            assertThatCode(() -> NotificationEventUtils.validateEvent(event(type, USER))).doesNotThrowAnyException();
        }
        NotificationEvent badKey = event("NOTE_LIKE", USER);
        badKey.setBusinessKey("NOTE_LIKE:10:999");
        assertThatThrownBy(() -> NotificationEventUtils.validateEvent(badKey)).isInstanceOf(IllegalArgumentException.class);
        NotificationEvent missingNote = event("COMMENT_LIKE", USER);
        missingNote.setNoteId(null);
        assertThatThrownBy(() -> NotificationEventUtils.validateEvent(missingNote)).isInstanceOf(IllegalArgumentException.class);
        NotificationEvent missingReply = event("REPLY", USER);
        missingReply.setReplyCommentId(null);
        assertThatThrownBy(() -> NotificationEventUtils.validateEvent(missingReply)).isInstanceOf(IllegalArgumentException.class);
        NotificationEvent wrongRole = event("FOLLOW", USER);
        wrongRole.setRecipients(List.of(new NotificationRecipient(USER, "OWNER")));
        assertThatThrownBy(() -> NotificationEventUtils.validateEvent(wrongRole)).isInstanceOf(IllegalArgumentException.class);
    }

    static NotificationUserStateDO state(long user, long seq, long revision) {
        NotificationUserStateDO state = NotificationUserStateDO.empty(user);
        state.setLastSeq(seq);
        state.setRevision(revision);
        return state;
    }

    static NotificationDO row(long user, long id, long seq, String category) {
        NotificationDO record = new NotificationDO();
        record.setId(id); record.setRecipientId(user); record.setSeq(seq); record.setCategory(category);
        record.setActorId(10L); record.setType("LIKE_COLLECTION".equals(category) ? "NOTE_LIKE" : "REPLY");
        record.setNoteId(100L); record.setCommentId("COMMENT_REPLY".equals(category) ? 200L : null);
        record.setRecipientRole("OWNER"); record.setOccurredAt(CUTOFF);
        return record;
    }

    static NotificationEvent event(String type, long recipient) {
        long object = "FOLLOW".equals(type) ? recipient : SetOfComments.isComment(type) ? 200 : 100;
        return NotificationEvent.builder().eventId("00000000-0000-0000-0000-000000000001").producerService("unit-test")
                .type(type).actorId(10L).noteId("FOLLOW".equals(type) ? null : 100L)
                .commentId(SetOfComments.isComment(type) ? 200L : null).replyCommentId("REPLY".equals(type) ? 150L : null)
                .businessKey(NotificationBusinessKey.of(type, 10L, object)).occurredAt(CUTOFF)
                .recipients(List.of(new NotificationRecipient(recipient, "FOLLOW".equals(type) ? "FOLLOW_TARGET" : "OWNER"))).build();
    }

    private static class SetOfComments {
        static boolean isComment(String type) { return List.of("COMMENT_LIKE", "COMMENT", "REPLY").contains(type); }
    }
}
