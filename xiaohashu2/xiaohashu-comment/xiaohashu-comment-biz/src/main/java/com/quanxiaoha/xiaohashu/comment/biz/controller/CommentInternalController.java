package com.quanxiaoha.xiaohashu.comment.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentInternalQueryService;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/comment")
public class CommentInternalController {
    @Resource private CommentInternalQueryService queries;
    @PostMapping("/metadata")
    public Response<List<CommentMetadataDTO>> metadata(@RequestBody CommentBatchQueryReqDTO request) { return Response.success(queries.metadata(request)); }
    @PostMapping("/previews")
    public Response<List<CommentPreviewDTO>> previews(@RequestBody CommentBatchQueryReqDTO request) { return Response.success(queries.previews(request)); }
}
