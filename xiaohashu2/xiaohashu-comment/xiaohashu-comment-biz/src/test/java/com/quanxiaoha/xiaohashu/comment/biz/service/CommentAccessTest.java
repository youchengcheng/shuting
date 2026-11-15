package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.*;
import com.quanxiaoha.xiaohashu.comment.biz.service.impl.CommentInternalQueryServiceImpl;
import com.quanxiaoha.xiaohashu.comment.biz.service.impl.CommentReplyValidatorImpl;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import com.quanxiaoha.xiaohashu.note.dto.NoteMetadataDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentAccessTest {
    @Test void replyTargetAndRootMustBelongToTheRequestedNote() {
        CommentDOMapper mapper=mock(CommentDOMapper.class); CommentReplyValidator validator=new CommentReplyValidatorImpl();
        ReflectionTestUtils.setField(validator,"commentDOMapper",mapper);
        CommentDO target=CommentDO.builder().id(12L).noteId(1L).parentId(10L).level(2).build();
        when(mapper.selectByPrimaryKey(12L)).thenReturn(target);
        when(mapper.selectByPrimaryKey(10L)).thenReturn(CommentDO.builder().id(10L).noteId(2L).parentId(2L).level(1).build());
        assertThrows(BizException.class,()->validator.requireTarget(1L,12L));
        assertThrows(BizException.class,()->validator.requireTarget(2L,12L));
    }
    @Test void inaccessibleCommentsDoNotReadKvOrReturnBodyOrImage() {
        CommentDOMapper mapper=mock(CommentDOMapper.class); NoteAccessService access=mock(NoteAccessService.class);
        KeyValueRpcService kv=mock(KeyValueRpcService.class); CommentInternalQueryService service=new CommentInternalQueryServiceImpl();
        ReflectionTestUtils.setField(service,"commentDOMapper",mapper); ReflectionTestUtils.setField(service,"noteAccessService",access);
        ReflectionTestUtils.setField(service,"keyValueRpcService",kv);
        when(mapper.selectByCommentIds(List.of(1L))).thenReturn(List.of(CommentDO.builder().id(1L).noteId(2L).userId(3L).isContentEmpty(false).contentUuid("body").imageUrl("private image").build()));
        when(access.metadata(List.of(2L),4L)).thenReturn(Map.of(2L,NoteMetadataDTO.builder().id(2L).accessible(false).build()));
        CommentPreviewDTO preview=service.previews(CommentBatchQueryReqDTO.builder().ids(List.of(1L)).viewerId(4L).build()).get(0);
        assertFalse(preview.getAccessible()); assertNull(preview.getContent()); assertNull(preview.getImageUrl()); verifyNoInteractions(kv);
    }
}
