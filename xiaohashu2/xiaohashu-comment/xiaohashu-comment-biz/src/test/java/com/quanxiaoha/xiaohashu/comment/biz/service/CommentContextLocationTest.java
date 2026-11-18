package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.*;
import com.quanxiaoha.xiaohashu.comment.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.*;
import com.quanxiaoha.xiaohashu.comment.biz.service.impl.CommentInternalQueryServiceImpl;
import com.quanxiaoha.xiaohashu.comment.biz.service.impl.CommentLikeStateService;
import com.quanxiaoha.xiaohashu.comment.biz.service.impl.CommentReplyValidatorImpl;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentContextLocationTest {
    private final CommentDOMapper mapper = mock(CommentDOMapper.class);
    private final NoteAccessService access = mock(NoteAccessService.class);
    private final KeyValueRpcService kv = mock(KeyValueRpcService.class);
    private final UserRpcService users = mock(UserRpcService.class);
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ZSetOperations<String, Object> zset = mock(ZSetOperations.class);
    private final CommentLikeStateService likeStates = mock(CommentLikeStateService.class);
    private final CommentInternalQueryService service = new CommentInternalQueryServiceImpl();
    private final CommentDO root = comment(100L, 1, 1L);
    private final CommentDO child = comment(200L, 2, 100L);

    @BeforeEach void setup() {
        CommentReplyValidator validator = new CommentReplyValidatorImpl();
        ReflectionTestUtils.setField(validator, "commentDOMapper", mapper);
        ReflectionTestUtils.setField(service, "commentDOMapper", mapper);
        ReflectionTestUtils.setField(service, "commentLikeStateService", likeStates);
        when(likeStates.findStates(anyLong(), anyList())).thenReturn(Map.of());
        ReflectionTestUtils.setField(service, "commentReplyValidator", validator);
        ReflectionTestUtils.setField(service, "noteAccessService", access);
        ReflectionTestUtils.setField(service, "keyValueRpcService", kv);
        ReflectionTestUtils.setField(service, "userRpcService", users);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.reverseRank(anyString(), any())).thenReturn(null);
        when(zset.rank(anyString(), any())).thenReturn(null);
        when(users.findByIds(anyList())).thenReturn(List.of());
        when(mapper.selectByPrimaryKey(100L)).thenReturn(root);
        when(mapper.selectByPrimaryKey(200L)).thenReturn(child);
        when(mapper.selectRootCommentPosition(1L, 100L)).thenReturn(0L);
        when(mapper.selectChildCommentPosition(100L, 200L)).thenReturn(0L);
        LoginUserContextHolder.setUserId(9L);
    }

    @AfterEach void cleanup() { LoginUserContextHolder.remove(); }

    @Test void rootUsesTheSameCachedHotCommentRankAsNormalPagination() {
        when(zset.reverseRank(RedisKeyConstants.buildCommentListKey(1L), 100L)).thenReturn(10L);
        CommentContextRspVO result = service.context(request(100L));
        assertEquals(100L, result.getRootCommentId());
        assertEquals(2, result.getRootPageNo());
        assertNull(result.getChildPageNo());
        assertEquals(10, result.getRootPageSize());
        assertEquals(6, result.getChildPageSize());
        verify(mapper, never()).selectRootCommentPosition(anyLong(), anyLong());
    }

    @Test void childUsesItsReplyThreadRankAndKeepsTheRootPageHint() {
        when(zset.reverseRank(RedisKeyConstants.buildCommentListKey(1L), 100L)).thenReturn(19L);
        when(zset.rank(RedisKeyConstants.buildChildCommentListKey(100L), 200L)).thenReturn(12L);
        CommentContextRspVO result = service.context(request(200L));
        assertEquals(2, result.getRootPageNo());
        assertEquals(3, result.getChildPageNo());
        assertEquals(200L, result.getTarget().getCommentId());
        assertEquals(100L, result.getRoot().getCommentId());
        verify(mapper, never()).selectChildCommentPosition(anyLong(), anyLong());
    }

    @Test void contextReturnsTheLatestLikeStatesInOneBatch() {
        when(likeStates.findStates(9L, List.of(100L, 200L))).thenReturn(Map.of(100L, false, 200L, true));
        CommentContextRspVO result = service.context(request(200L));
        assertFalse(result.getRoot().getLiked());
        assertTrue(result.getTarget().getLiked());
        verify(likeStates).findStates(9L, List.of(100L, 200L));
    }

    @Test void databasePageBoundariesMatchTenRootsAndSixReplies() {
        for (long rank : new long[] { 0, 9, 10, 19, 20, 499, 500 }) {
            when(mapper.selectRootCommentPosition(1L, 100L)).thenReturn(rank);
            assertEquals((int) (rank / 10 + 1), service.context(request(100L)).getRootPageNo());
        }
        for (long rank : new long[] { 0, 5, 6, 11, 12, 59, 60 }) {
            when(mapper.selectChildCommentPosition(100L, 200L)).thenReturn(rank);
            assertEquals((int) (rank / 6 + 1), service.context(request(200L)).getChildPageNo());
        }
    }

    @Test void cacheRanksOutsideItsNormalPageWindowFallBackToDatabase() {
        when(zset.reverseRank(RedisKeyConstants.buildCommentListKey(1L), 100L)).thenReturn(500L);
        when(zset.rank(RedisKeyConstants.buildChildCommentListKey(100L), 200L)).thenReturn(60L);
        when(mapper.selectRootCommentPosition(1L, 100L)).thenReturn(511L);
        when(mapper.selectChildCommentPosition(100L, 200L)).thenReturn(72L);
        CommentContextRspVO result = service.context(request(200L));
        assertEquals(52, result.getRootPageNo());
        assertEquals(13, result.getChildPageNo());
    }

    @Test void cacheFailureDoesNotPreventDatabaseLocation() {
        when(redis.opsForZSet()).thenThrow(new IllegalStateException("cache unavailable"));
        when(mapper.selectRootCommentPosition(1L, 100L)).thenReturn(30L);
        when(mapper.selectChildCommentPosition(100L, 200L)).thenReturn(6L);
        CommentContextRspVO result = service.context(request(200L));
        assertEquals(4, result.getRootPageNo());
        assertEquals(2, result.getChildPageNo());
    }

    @Test void deletedTargetOrDeletedRootReturnsUnavailableWithoutContentsOrLocation() {
        when(mapper.selectByPrimaryKey(200L)).thenReturn(null);
        CommentContextRspVO targetDeleted = service.context(request(200L));
        assertEquals("COMMENT_UNAVAILABLE", targetDeleted.getUnavailableReason());
        assertNull(targetDeleted.getRootPageNo());
        when(mapper.selectByPrimaryKey(200L)).thenReturn(child);
        when(mapper.selectByPrimaryKey(100L)).thenReturn(null);
        CommentContextRspVO rootDeleted = service.context(request(200L));
        assertEquals("COMMENT_UNAVAILABLE", rootDeleted.getUnavailableReason());
        assertNull(rootDeleted.getTarget());
        assertNull(rootDeleted.getRootPageNo());
        verifyNoInteractions(kv, users, zset);
    }

    @Test void inaccessibleOrUnrelatedTargetsCannotExposeLocationOrContent() {
        doThrow(new BizException(com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum.COMMENT_CANT_OPERATE))
                .when(access).requireAccessible(1L, 9L);
        assertThrows(BizException.class, () -> service.context(request(100L)));
        verifyNoInteractions(mapper, users, kv, zset);
        reset(access);
        child.setNoteId(2L);
        assertThrows(BizException.class, () -> service.context(request(200L)));
        verifyNoInteractions(users, kv, zset);
    }

    private static CommentDO comment(long id, int level, long parent) {
        return CommentDO.builder().id(id).noteId(1L).parentId(parent).level(level).userId(3L)
                .isContentEmpty(true).heat(5D).build();
    }
    private static CommentContextReqVO request(long id) {
        return CommentContextReqVO.builder().noteId(1L).commentId(id).build();
    }
}
