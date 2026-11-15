package com.quanxiaoha.xiaohashu.note.biz.service;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.service.impl.NoteInternalQueryServiceImpl;
import com.quanxiaoha.xiaohashu.note.biz.util.NoteAccessUtils;
import com.quanxiaoha.xiaohashu.note.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoteInternalQueryTest {
    @Test void inaccessiblePreviewsNeverExposeTitleOrCover() {
        NoteDO privateNote = NoteDO.builder().id(1L).creatorId(2L).status(1).visible(1).title("private title").imgUris("private cover").build();
        assertFalse(NoteAccessUtils.canRead(privateNote,3L));
        assertTrue(NoteAccessUtils.canRead(privateNote,2L));
        NoteInternalQueryService service = new NoteInternalQueryServiceImpl();
        NoteDOMapper mapper = mock(NoteDOMapper.class); when(mapper.selectByIds(List.of(1L))).thenReturn(List.of(privateNote));
        ReflectionTestUtils.setField(service,"noteDOMapper",mapper);
        NotePreviewDTO preview=service.previews(NoteBatchQueryReqDTO.builder().ids(List.of(1L)).viewerId(3L).build()).get(0);
        assertFalse(preview.getAccessible()); assertNull(preview.getTitle()); assertNull(preview.getCover());
        privateNote.setStatus(2); assertFalse(NoteAccessUtils.canRead(privateNote,2L));
        privateNote.setStatus(1); privateNote.setVisible(null); assertFalse(NoteAccessUtils.canRead(privateNote,2L));
    }
}
