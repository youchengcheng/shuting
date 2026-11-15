package com.quanxiaoha.xiaohashu.notification.biz.rpc;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.api.NoteFeignApi;
import com.quanxiaoha.xiaohashu.note.dto.NoteBatchQueryReqDTO;
import com.quanxiaoha.xiaohashu.note.dto.NotePreviewDTO;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 笔记服务 RPC 调用
 * */
@Component
@Slf4j
public class NoteRpcService {

    @Resource
    private NoteFeignApi noteFeignApi;

    /*
     * 查询当前用户可访问的笔记预览
     * */
    public Map<Long, NotePreviewDTO> findPreviews(long viewerId, List<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }

        // 透传当前查看者，笔记服务负责判断内容访问权限
        NoteBatchQueryReqDTO noteBatchQueryReqDTO = new NoteBatchQueryReqDTO();
        noteBatchQueryReqDTO.setIds(noteIds);
        noteBatchQueryReqDTO.setViewerId(viewerId);
        Response<List<NotePreviewDTO>> response;
        try {
            response = noteFeignApi.previews(noteBatchQueryReqDTO);
        } catch (RuntimeException ex) {
            log.warn("通知笔记预览查询失败", ex);
            throw new BizException(ResponseCodeEnum.NOTE_RPC_QUERY_FAILED);
        }

        // 查询失败与笔记不存在分别处理，避免将依赖异常当作内容删除
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BizException(ResponseCodeEnum.NOTE_RPC_QUERY_FAILED);
        }
        Map<Long, NotePreviewDTO> notePreviews = new HashMap<>();
        for (NotePreviewDTO notePreviewDTO : response.getData()) {
            notePreviews.put(notePreviewDTO.getId(), notePreviewDTO);
        }
        return notePreviews;
    }
}
