package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.api.CommentFeignApi;
import com.quanxiaoha.xiaohashu.comment.dto.CommentBatchQueryReqDTO;
import com.quanxiaoha.xiaohashu.comment.dto.CommentPreviewDTO;
import com.quanxiaoha.xiaohashu.note.api.NoteFeignApi;
import com.quanxiaoha.xiaohashu.note.dto.NoteBatchQueryReqDTO;
import com.quanxiaoha.xiaohashu.note.dto.NotePreviewDTO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationDO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.FindNotificationItemRspVO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.NotificationPreviewRspVO;
import com.quanxiaoha.xiaohashu.notification.biz.rpc.CommentRpcService;
import com.quanxiaoha.xiaohashu.notification.biz.rpc.NoteRpcService;
import com.quanxiaoha.xiaohashu.notification.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationPreviewService;
import com.quanxiaoha.xiaohashu.notification.biz.service.impl.*;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationPreviewServiceTest {
    private static final long VIEWER = 1L;
    private UserFeignApi users;
    private NoteFeignApi notes;
    private CommentFeignApi comments;
    private NotificationPreviewService service;

    @BeforeEach
    void setUp() {
        users = mock(UserFeignApi.class);
        notes = mock(NoteFeignApi.class);
        comments = mock(CommentFeignApi.class);
        // 使用真实 RPC 包装验证请求参数和远端失败降级，不为生产类保留测试构造器
        UserRpcService userRpcService = new UserRpcService();
        NoteRpcService noteRpcService = new NoteRpcService();
        CommentRpcService commentRpcService = new CommentRpcService();
        ReflectionTestUtils.setField(userRpcService, "userFeignApi", users);
        ReflectionTestUtils.setField(noteRpcService, "noteFeignApi", notes);
        ReflectionTestUtils.setField(commentRpcService, "commentFeignApi", comments);
        service = new NotificationPreviewServiceImpl();
        ReflectionTestUtils.setField(service, "userRpcService", userRpcService);
        ReflectionTestUtils.setField(service, "noteRpcService", noteRpcService);
        ReflectionTestUtils.setField(service, "commentRpcService", commentRpcService);
        when(users.findByIds(any())).thenReturn(Response.success(List.of()));
        when(notes.previews(any())).thenReturn(Response.success(List.of(note(true))));
        when(comments.previews(any())).thenReturn(Response.success(List.of(comment(true))));
    }

    @Test
    void commentInteractionsUseTheEventCommentForBothTextAndImage() {
        CommentPreviewDTO repliedTo = CommentPreviewDTO.builder().id(30L).noteId(10L).accessible(true)
                .content("original comment").imageUrl("original-image").build();
        when(comments.previews(any())).thenReturn(Response.success(List.of(repliedTo, comment(true))));
        for (String type : List.of("COMMENT_LIKE", "COMMENT", "REPLY")) {
            FindNotificationItemRspVO item = render(VIEWER, row(type));
            assertThat(item.getContentStatus()).isEqualTo("AVAILABLE");
            assertThat(item.getPreview()).isEqualTo(new NotificationPreviewRspVO("note title", "note-cover", "event comment", "comment-image"));
            assertThat(item.getCommentId()).isEqualTo("20");
            assertThat(item.getReplyCommentId()).isEqualTo("REPLY".equals(type) ? "30" : null);
        }
        ArgumentCaptor<CommentBatchQueryReqDTO> requests = ArgumentCaptor.forClass(CommentBatchQueryReqDTO.class);
        verify(comments, times(3)).previews(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.getIds()).containsExactly(20L);
            assertThat(request.getViewerId()).isEqualTo(VIEWER);
        });
    }

    @Test
    void imageOnlyCommentAndReplyKeepTheirImageWithoutUsingTheNoteCover() {
        CommentPreviewDTO imageOnly = comment(true);
        imageOnly.setContent(null);
        when(comments.previews(any())).thenReturn(Response.success(List.of(imageOnly)));
        for (String type : List.of("COMMENT_LIKE", "COMMENT", "REPLY")) {
            FindNotificationItemRspVO item = render(VIEWER, row(type));
            assertThat(item.getPreview().getSummary()).isEmpty();
            assertThat(item.getPreview().getImageUrl()).isEqualTo("comment-image");
            assertThat(item.getPreview().getCover()).isEqualTo("note-cover");
        }
    }

    @Test
    void textOnlyCommentDoesNotBorrowTheNoteCoverAsItsImage() {
        CommentPreviewDTO textOnly = comment(true);
        textOnly.setImageUrl(null);
        when(comments.previews(any())).thenReturn(Response.success(List.of(textOnly)));
        FindNotificationItemRspVO item = render(VIEWER, row("COMMENT_LIKE"));
        assertThat(item.getPreview().getSummary()).isEqualTo("event comment");
        assertThat(item.getPreview().getImageUrl()).isEmpty();
        assertThat(item.getPreview().getCover()).isEqualTo("note-cover");
    }

    @Test
    void deniedOrDeletedCommentAndWrongNoteNeverExposeAnyPreviewFields() {
        CommentPreviewDTO wrongNote = comment(true);
        wrongNote.setNoteId(99L);
        for (List<CommentPreviewDTO> response : List.of(List.of(comment(false)), List.of(wrongNote), List.<CommentPreviewDTO>of())) {
            when(comments.previews(any())).thenReturn(Response.success(response));
            assertHidden(render(VIEWER, row("COMMENT_LIKE")), "UNAVAILABLE");
        }
    }

    @Test
    void permissionRevocationClearsPreviouslyReadableContentAndAvoidsCommentLookup() {
        assertThat(render(VIEWER, row("REPLY")).getPreview().getImageUrl()).isEqualTo("comment-image");
        when(notes.previews(any())).thenReturn(Response.success(List.of(note(false))));
        assertHidden(render(VIEWER, row("REPLY")), "UNAVAILABLE");
        verify(comments, times(1)).previews(any());
        verify(notes, times(2)).previews(any());
    }

    @Test
    void eachViewerGetsAFreshAuthoritativeCommentPermissionCheck() {
        when(comments.previews(any())).thenAnswer(invocation -> {
            CommentBatchQueryReqDTO request = invocation.getArgument(0);
            return Response.success(List.of(comment(request.getViewerId() == VIEWER)));
        });
        assertThat(render(VIEWER, row("COMMENT_LIKE")).getPreview().getImageUrl()).isEqualTo("comment-image");
        assertHidden(render(2L, row("COMMENT_LIKE")), "UNAVAILABLE");
        ArgumentCaptor<CommentBatchQueryReqDTO> requests = ArgumentCaptor.forClass(CommentBatchQueryReqDTO.class);
        verify(comments, times(2)).previews(requests.capture());
        assertThat(requests.getAllValues()).extracting(CommentBatchQueryReqDTO::getViewerId).containsExactly(1L, 2L);
        ArgumentCaptor<NoteBatchQueryReqDTO> noteRequests = ArgumentCaptor.forClass(NoteBatchQueryReqDTO.class);
        verify(notes, times(2)).previews(noteRequests.capture());
        assertThat(noteRequests.getAllValues()).extracting(NoteBatchQueryReqDTO::getViewerId).containsExactly(1L, 2L);
    }

    @Test
    void failedNoteLookupClearsThePreviewAndNeverReadsCommentContent() {
        when(notes.previews(any())).thenThrow(new IllegalStateException("note metadata unavailable"));
        assertHidden(render(VIEWER, row("COMMENT_LIKE")), "TEMPORARY_UNAVAILABLE");
        verifyNoInteractions(comments);
    }

    @Test
    void failedCommentLookupClearsEvenTheAvailableNoteTitleAndCover() {
        when(comments.previews(any())).thenReturn(Response.fail());
        assertHidden(render(VIEWER, row("REPLY")), "TEMPORARY_UNAVAILABLE");
    }

    @Test
    void failedOrMissingNoteResponsesRemainDifferentFromASuccessfulEmptyLookup() {
        // 不完整和失败响应属于临时异常；成功空列表才表示笔记已经不存在
        when(notes.previews(any())).thenReturn(Response.fail(), Response.success(null), null, Response.success(List.of()));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertHidden(render(VIEWER, row("COMMENT_LIKE")), "TEMPORARY_UNAVAILABLE");
        }
        assertHidden(render(VIEWER, row("COMMENT_LIKE")), "UNAVAILABLE");
        verifyNoInteractions(comments);
    }

    @Test
    void failedOrMissingCommentResponsesRemainDifferentFromASuccessfulEmptyLookup() {
        when(comments.previews(any())).thenReturn(Response.fail(), Response.success(null), null, Response.success(List.of()));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertHidden(render(VIEWER, row("REPLY")), "TEMPORARY_UNAVAILABLE");
        }
        assertHidden(render(VIEWER, row("REPLY")), "UNAVAILABLE");
    }

    @Test
    void oneFailedUserBatchPreservesOtherProfilesAndStillQueriesTheNextBatch() {
        when(users.findByIds(any())).thenAnswer(invocation -> {
            FindUsersByIdsReqDTO request = invocation.getArgument(0);
            if (request.getIds().get(0) == 11L) {
                throw new IllegalStateException("user service unavailable");
            }
            return Response.success(request.getIds().stream().map(userId -> FindUserByIdRspDTO.builder()
                    .id(userId).nickName("user-" + userId).avatar("avatar-" + userId).build()).toList());
        });
        List<NotificationDO> records = LongStream.rangeClosed(1, 21).mapToObj(actorId -> {
            NotificationDO follow = row("FOLLOW");
            follow.setActorId(actorId);
            follow.setNoteId(null);
            return follow;
        }).toList();

        List<FindNotificationItemRspVO> items = service.render(VIEWER, records, NotificationUserStateDO.empty(VIEWER));
        assertThat(items.subList(0, 10)).allSatisfy(item -> {
            assertThat(item.getActor().getNickname()).isEqualTo("user-" + item.getActor().getUserId());
        });
        assertThat(items.subList(10, 20)).allSatisfy(item -> assertThat(item.getActor().getNickname()).isEqualTo("用户"));
        assertThat(items.get(20).getActor().getNickname()).isEqualTo("user-21");
        ArgumentCaptor<FindUsersByIdsReqDTO> requests = ArgumentCaptor.forClass(FindUsersByIdsReqDTO.class);
        verify(users, times(3)).findByIds(requests.capture());
        assertThat(requests.getAllValues()).extracting(request -> request.getIds().size()).containsExactly(10, 10, 1);
        verifyNoInteractions(notes, comments);
    }

    @Test
    void historicalNotificationStillUsesItsPersistedCommentIdAndDate() {
        NotificationDO historical = row("COMMENT_LIKE");
        historical.setOccurredAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        FindNotificationItemRspVO item = render(VIEWER, historical);
        assertThat(item.getOccurredAt()).isEqualTo("2024-01-02T03:04:05");
        assertThat(item.getCommentId()).isEqualTo("20");
        assertThat(item.getPreview().getSummary()).isEqualTo("event comment");
        assertThat(item.getPreview().getImageUrl()).isEqualTo("comment-image");
    }

    @Test
    void noteInteractionsKeepTheirNotePreviewAndHaveNoCommentImage() {
        for (String type : List.of("NOTE_LIKE", "NOTE_COLLECT")) {
            FindNotificationItemRspVO item = render(VIEWER, row(type));
            assertThat(item.getPreview()).isEqualTo(new NotificationPreviewRspVO("note title", "note-cover", "", ""));
        }
        verifyNoInteractions(comments);
    }

    @Test
    void followHasNoContentPreviewOrContentLookups() {
        NotificationDO follow = row("FOLLOW");
        follow.setNoteId(null);
        FindNotificationItemRspVO item = render(VIEWER, follow);
        assertThat(item.getPreview()).isEqualTo(new NotificationPreviewRspVO("", "", "", ""));
        assertThat(item.getContentStatus()).isEqualTo("AVAILABLE");
        verifyNoInteractions(notes, comments);
    }

    private FindNotificationItemRspVO render(long viewer, NotificationDO record) {
        return service.render(viewer, List.of(record), NotificationUserStateDO.empty(viewer)).get(0);
    }

    private static void assertHidden(FindNotificationItemRspVO item, String status) {
        assertThat(item.getContentStatus()).isEqualTo(status);
        assertThat(item.getPreview()).isEqualTo(new NotificationPreviewRspVO("", "", "", ""));
    }

    private static NotePreviewDTO note(boolean accessible) {
        return NotePreviewDTO.builder().id(10L).title("note title").cover("note-cover").accessible(accessible).build();
    }

    private static CommentPreviewDTO comment(boolean accessible) {
        return CommentPreviewDTO.builder().id(20L).noteId(10L).parentId(15L).replyCommentId(30L)
                .content("event comment").imageUrl("comment-image").accessible(accessible).build();
    }

    private static NotificationDO row(String type) {
        NotificationDO record = new NotificationDO();
        record.setId(100L); record.setSeq(1L); record.setRecipientId(VIEWER); record.setActorId(5L);
        record.setType(type); record.setRecipientRole("REPLY".equals(type) ? "REPLY_TARGET" : "OWNER");
        record.setCategory("FOLLOW".equals(type) ? "FOLLOW" : List.of("COMMENT", "REPLY").contains(type) ? "COMMENT_REPLY" : "LIKE_COLLECTION");
        record.setNoteId(10L);
        record.setCommentId(List.of("COMMENT_LIKE", "COMMENT", "REPLY").contains(type) ? 20L : null);
        record.setReplyCommentId("REPLY".equals(type) ? 30L : null);
        record.setOccurredAt(LocalDateTime.of(2026, 11, 14, 12, 0));
        return record;
    }
}
