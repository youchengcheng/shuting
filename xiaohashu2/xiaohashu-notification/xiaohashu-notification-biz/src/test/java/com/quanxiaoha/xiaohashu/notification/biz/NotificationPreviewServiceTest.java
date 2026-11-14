package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.api.CommentFeignApi;
import com.quanxiaoha.xiaohashu.comment.dto.CommentBatchQueryReqDTO;
import com.quanxiaoha.xiaohashu.comment.dto.CommentPreviewDTO;
import com.quanxiaoha.xiaohashu.note.api.NoteFeignApi;
import com.quanxiaoha.xiaohashu.note.dto.NoteBatchQueryReqDTO;
import com.quanxiaoha.xiaohashu.note.dto.NotePreviewDTO;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationRecord;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.Item;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.Preview;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationUserState;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationPreviewService;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

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
        service = new NotificationPreviewService(users, notes, comments);
        when(notes.previews(any())).thenReturn(Response.success(List.of(note(true))));
        when(comments.previews(any())).thenReturn(Response.success(List.of(comment(true))));
    }

    @Test
    void commentInteractionsUseTheEventCommentForBothTextAndImage() {
        CommentPreviewDTO repliedTo = CommentPreviewDTO.builder().id(30L).noteId(10L).accessible(true)
                .content("original comment").imageUrl("original-image").build();
        when(comments.previews(any())).thenReturn(Response.success(List.of(repliedTo, comment(true))));
        for (String type : List.of("COMMENT_LIKE", "COMMENT", "REPLY")) {
            Item item = render(VIEWER, row(type));
            assertThat(item.contentStatus()).isEqualTo("AVAILABLE");
            assertThat(item.preview()).isEqualTo(new Preview("note title", "note-cover", "event comment", "comment-image"));
            assertThat(item.commentId()).isEqualTo("20");
            assertThat(item.replyCommentId()).isEqualTo("REPLY".equals(type) ? "30" : null);
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
            Item item = render(VIEWER, row(type));
            assertThat(item.preview().summary()).isEmpty();
            assertThat(item.preview().imageUrl()).isEqualTo("comment-image");
            assertThat(item.preview().cover()).isEqualTo("note-cover");
        }
    }

    @Test
    void textOnlyCommentDoesNotBorrowTheNoteCoverAsItsImage() {
        CommentPreviewDTO textOnly = comment(true);
        textOnly.setImageUrl(null);
        when(comments.previews(any())).thenReturn(Response.success(List.of(textOnly)));
        Item item = render(VIEWER, row("COMMENT_LIKE"));
        assertThat(item.preview().summary()).isEqualTo("event comment");
        assertThat(item.preview().imageUrl()).isEmpty();
        assertThat(item.preview().cover()).isEqualTo("note-cover");
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
        assertThat(render(VIEWER, row("REPLY")).preview().imageUrl()).isEqualTo("comment-image");
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
        assertThat(render(VIEWER, row("COMMENT_LIKE")).preview().imageUrl()).isEqualTo("comment-image");
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
    void historicalNotificationStillUsesItsPersistedCommentIdAndDate() {
        NotificationRecord historical = row("COMMENT_LIKE");
        historical.setOccurredAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        Item item = render(VIEWER, historical);
        assertThat(item.occurredAt()).isEqualTo("2024-01-02T03:04:05");
        assertThat(item.commentId()).isEqualTo("20");
        assertThat(item.preview().summary()).isEqualTo("event comment");
        assertThat(item.preview().imageUrl()).isEqualTo("comment-image");
    }

    @Test
    void noteInteractionsKeepTheirNotePreviewAndHaveNoCommentImage() {
        for (String type : List.of("NOTE_LIKE", "NOTE_COLLECT")) {
            Item item = render(VIEWER, row(type));
            assertThat(item.preview()).isEqualTo(new Preview("note title", "note-cover", "", ""));
        }
        verifyNoInteractions(comments);
    }

    @Test
    void followHasNoContentPreviewOrContentLookups() {
        NotificationRecord follow = row("FOLLOW");
        follow.setNoteId(null);
        Item item = render(VIEWER, follow);
        assertThat(item.preview()).isEqualTo(new Preview("", "", "", ""));
        assertThat(item.contentStatus()).isEqualTo("AVAILABLE");
        verifyNoInteractions(notes, comments);
    }

    private Item render(long viewer, NotificationRecord record) {
        return service.render(viewer, List.of(record), NotificationUserState.empty(viewer)).get(0);
    }

    private static void assertHidden(Item item, String status) {
        assertThat(item.contentStatus()).isEqualTo(status);
        assertThat(item.preview()).isEqualTo(new Preview("", "", "", ""));
    }

    private static NotePreviewDTO note(boolean accessible) {
        return NotePreviewDTO.builder().id(10L).title("note title").cover("note-cover").accessible(accessible).build();
    }

    private static CommentPreviewDTO comment(boolean accessible) {
        return CommentPreviewDTO.builder().id(20L).noteId(10L).parentId(15L).replyCommentId(30L)
                .content("event comment").imageUrl("comment-image").accessible(accessible).build();
    }

    private static NotificationRecord row(String type) {
        NotificationRecord record = new NotificationRecord();
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
