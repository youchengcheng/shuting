package com.quanxiaoha.xiaohashu.comment.biz.rpc;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.note.api.NoteFeignApi;
import com.quanxiaoha.xiaohashu.note.dto.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NoteAccessService {
    @Resource private NoteFeignApi noteFeignApi;

    public NoteMetadataDTO requireAccessible(Long noteId, Long viewerId) {
        NoteMetadataDTO note = metadata(List.of(noteId), viewerId).get(noteId);
        if (note == null || !Boolean.TRUE.equals(note.getAccessible()))
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        return note;
    }

    public Map<Long, NoteMetadataDTO> metadata(List<Long> noteIds, Long viewerId) {
        Response<List<NoteMetadataDTO>> response = noteFeignApi.metadata(NoteBatchQueryReqDTO.builder().ids(noteIds).viewerId(viewerId).build());
        if (response == null || !response.isSuccess() || response.getData() == null)
            throw new IllegalStateException("Authoritative note metadata service is unavailable");
        return response.getData().stream().collect(Collectors.toMap(NoteMetadataDTO::getId, n -> n));
    }
}
