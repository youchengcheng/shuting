package com.quanxiaoha.xiaohashu.note.biz.service;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.dto.*;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.util.*;

@Service
public class NoteInternalQueryService {
    @Resource private NoteDOMapper noteDOMapper;

    public List<NoteMetadataDTO> metadata(NoteBatchQueryReqDTO request) {
        return find(request).stream().map(note -> NoteMetadataDTO.builder().id(note.getId())
                .creatorId(note.getCreatorId()).status(note.getStatus()).visible(note.getVisible())
                .accessible(canRead(note, request.getViewerId())).build()).toList();
    }

    public List<NotePreviewDTO> previews(NoteBatchQueryReqDTO request) {
        return find(request).stream().map(note -> {
            boolean readable = canRead(note, request.getViewerId());
            String cover = null;
            if (readable && note.getImgUris() != null && !note.getImgUris().isBlank())
                cover = note.getImgUris().split(",", 2)[0];
            return NotePreviewDTO.builder().id(note.getId()).accessible(readable)
                    .title(readable ? note.getTitle() : null).cover(cover).build();
        }).toList();
    }

    private List<NoteDO> find(NoteBatchQueryReqDTO request) {
        if (request == null || request.getIds() == null || request.getIds().size() > 100)
            throw new IllegalArgumentException("Provide at most 100 note IDs");
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        return ids.isEmpty() ? Collections.emptyList() : noteDOMapper.selectByIds(ids);
    }

    public static boolean canRead(NoteDO note, Long viewerId) {
        return note != null && Objects.equals(note.getStatus(), 1)
                && (Objects.equals(note.getVisible(), 0)
                    || (Objects.equals(note.getVisible(), 1) && viewerId != null && Objects.equals(note.getCreatorId(), viewerId)));
    }
}
