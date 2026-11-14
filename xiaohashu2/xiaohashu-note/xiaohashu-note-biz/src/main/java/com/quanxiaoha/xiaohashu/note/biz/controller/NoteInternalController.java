package com.quanxiaoha.xiaohashu.note.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteInternalQueryService;
import com.quanxiaoha.xiaohashu.note.dto.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/note")
public class NoteInternalController {
    @Resource private NoteInternalQueryService queries;
    @PostMapping("/metadata")
    public Response<List<NoteMetadataDTO>> metadata(@RequestBody NoteBatchQueryReqDTO request) { return Response.success(queries.metadata(request)); }
    @PostMapping("/previews")
    public Response<List<NotePreviewDTO>> previews(@RequestBody NoteBatchQueryReqDTO request) { return Response.success(queries.previews(request)); }
}
