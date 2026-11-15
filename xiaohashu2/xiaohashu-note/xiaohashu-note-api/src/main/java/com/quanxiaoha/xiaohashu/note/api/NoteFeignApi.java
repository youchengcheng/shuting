package com.quanxiaoha.xiaohashu.note.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/*
 * 笔记内部服务接口
 * */
@FeignClient(name = "xiaohashu-note", contextId = "noteInternalApi")
public interface NoteFeignApi {

    /*
     * 批量查询元数据
     * */
    @PostMapping("/internal/note/metadata")
    Response<List<NoteMetadataDTO>> metadata(@RequestBody NoteBatchQueryReqDTO noteBatchQueryReqDTO);

    /*
     * 批量查询预览信息
     * */
    @PostMapping("/internal/note/previews")
    Response<List<NotePreviewDTO>> previews(@RequestBody NoteBatchQueryReqDTO noteBatchQueryReqDTO);
}
