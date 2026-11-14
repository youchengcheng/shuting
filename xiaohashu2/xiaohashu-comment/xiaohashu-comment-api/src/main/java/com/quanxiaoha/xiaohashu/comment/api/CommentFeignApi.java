package com.quanxiaoha.xiaohashu.comment.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "xiaohashu-comment", contextId = "commentInternalApi")
public interface CommentFeignApi {
    @PostMapping("/internal/comment/metadata")
    Response<List<CommentMetadataDTO>> metadata(@RequestBody CommentBatchQueryReqDTO request);

    @PostMapping("/internal/comment/previews")
    Response<List<CommentPreviewDTO>> previews(@RequestBody CommentBatchQueryReqDTO request);
}
