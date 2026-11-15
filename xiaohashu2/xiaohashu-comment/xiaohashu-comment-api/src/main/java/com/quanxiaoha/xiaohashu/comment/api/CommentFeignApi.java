package com.quanxiaoha.xiaohashu.comment.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/*
 * 评论内部服务接口
 * */
@FeignClient(name = "xiaohashu-comment", contextId = "commentInternalApi")
public interface CommentFeignApi {

    /*
     * 批量查询元数据
     * */
    @PostMapping("/internal/comment/metadata")
    Response<List<CommentMetadataDTO>> metadata(@RequestBody CommentBatchQueryReqDTO commentBatchQueryReqDTO);

    /*
     * 批量查询预览信息
     * */
    @PostMapping("/internal/comment/previews")
    Response<List<CommentPreviewDTO>> previews(@RequestBody CommentBatchQueryReqDTO commentBatchQueryReqDTO);
}
