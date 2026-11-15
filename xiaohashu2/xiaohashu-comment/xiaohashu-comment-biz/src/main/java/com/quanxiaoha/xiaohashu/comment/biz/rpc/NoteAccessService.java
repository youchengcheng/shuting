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

/*
 * 笔记访问权限查询
 * */
@Component
public class NoteAccessService {

    @Resource
    private NoteFeignApi noteFeignApi;

    /*
     * 查询笔记并校验访问权限
     * */
    public NoteMetadataDTO requireAccessible(Long noteId, Long viewerId) {
        // 通过笔记服务查询当前访问者的权限，禁止访问缺失或无权限的笔记
        NoteMetadataDTO note = metadata(List.of(noteId), viewerId).get(noteId);
        if (note == null || !Boolean.TRUE.equals(note.getAccessible())) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }
        return note;
    }

    /*
     * 批量查询元数据
     * */
    public Map<Long, NoteMetadataDTO> metadata(List<Long> noteIds, Long viewerId) {
        // 构建批量查询参数，由笔记服务按访问者身份判断可见性
        NoteBatchQueryReqDTO noteBatchQueryReqDTO = NoteBatchQueryReqDTO.builder()
                .ids(noteIds)
                .viewerId(viewerId)
                .build();
        // 远程查询失败时抛出异常，不把权限查询失败视为允许访问
        Response<List<NoteMetadataDTO>> response = noteFeignApi.metadata(noteBatchQueryReqDTO);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("笔记元数据服务暂时不可用");
        }
        return response.getData().stream().collect(Collectors.toMap(NoteMetadataDTO::getId, n -> n));
    }
}
