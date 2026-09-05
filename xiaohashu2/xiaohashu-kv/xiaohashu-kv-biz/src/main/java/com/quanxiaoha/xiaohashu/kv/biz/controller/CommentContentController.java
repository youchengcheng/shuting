package com.quanxiaoha.xiaohashu.kv.biz.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.biz.service.CommentContentService;
import com.quanxiaoha.xiaohashu.kv.dto.rep.BatchAddCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.BatchFindCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.DeleteCommentContentReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.tree.analysis.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
* 评论内容
* */
@RestController
@RequestMapping("/kv")
@Slf4j
public class CommentContentController {

    @Resource
    private CommentContentService commentContentService;

    /*
    * 批量存储评论内容
    * */
    @ApiOperationLog(description = "批量存储评论内容")
    @PostMapping("/comment/content/batchAdd")
    public Response<?> batchAddCommentContent(@Validated @RequestBody BatchAddCommentContentReqDTO batchAddCommentContentReqDTO){
        return commentContentService.batchAddCommentContent(batchAddCommentContentReqDTO);
    }

    /*
    * 批量查询评论内容
    * */
    @PostMapping("/comment/content/batchFind")
    @ApiOperationLog(description = "批量查询评论内容")
    public Response<?> batchFindCommentContent(@Validated @RequestBody BatchFindCommentContentReqDTO batchFindCommentContentReqDTO) {
        return commentContentService.batchFindCommentContent(batchFindCommentContentReqDTO);
    }

    /*
    * 删除评论内容
    * */
    @PostMapping(value = "/comment/content/delete")
    @ApiOperationLog(description = "删除评论内容")
    public Response<?> deleteCommentContent(@Validated @RequestBody DeleteCommentContentReqDTO deleteCommentContentReqDTO) {
        return commentContentService.deleteCommentContent(deleteCommentContentReqDTO);
    }

}
