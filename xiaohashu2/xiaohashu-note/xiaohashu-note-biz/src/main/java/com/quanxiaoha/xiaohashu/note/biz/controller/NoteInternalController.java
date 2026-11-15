package com.quanxiaoha.xiaohashu.note.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteInternalQueryService;
import com.quanxiaoha.xiaohashu.note.dto.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * 笔记内部查询接口
 * */
@RestController
@RequestMapping("/internal/note")
public class NoteInternalController {

    @Resource
    private NoteInternalQueryService noteInternalQueryService;

    /*
     * 批量查询元数据
     * */
    @PostMapping("/metadata")
    public Response<List<NoteMetadataDTO>> metadata(@RequestBody NoteBatchQueryReqDTO noteBatchQueryReqDTO) {
        return Response.success(noteInternalQueryService.metadata(noteBatchQueryReqDTO));
    }

    /*
     * 批量查询预览信息
     * */
    @PostMapping("/previews")
    public Response<List<NotePreviewDTO>> previews(@RequestBody NoteBatchQueryReqDTO noteBatchQueryReqDTO) {
        return Response.success(noteInternalQueryService.previews(noteBatchQueryReqDTO));
    }
}
