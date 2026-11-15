package com.quanxiaoha.xiaohashu.comment.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentInternalQueryService;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * 评论内部查询接口
 * */
@RestController
@RequestMapping("/internal/comment")
public class CommentInternalController {

    @Resource
    private CommentInternalQueryService commentInternalQueryService;

    /*
     * 批量查询元数据
     * */
    @PostMapping("/metadata")
    public Response<List<CommentMetadataDTO>> metadata(@RequestBody CommentBatchQueryReqDTO commentBatchQueryReqDTO) {
        return Response.success(commentInternalQueryService.metadata(commentBatchQueryReqDTO));
    }

    /*
     * 批量查询预览信息
     * */
    @PostMapping("/previews")
    public Response<List<CommentPreviewDTO>> previews(@RequestBody CommentBatchQueryReqDTO commentBatchQueryReqDTO) {
        return Response.success(commentInternalQueryService.previews(commentBatchQueryReqDTO));
    }
}
