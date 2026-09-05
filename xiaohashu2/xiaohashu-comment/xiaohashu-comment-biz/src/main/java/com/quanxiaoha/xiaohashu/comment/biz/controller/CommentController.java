package com.quanxiaoha.xiaohashu.comment.biz.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
* 评论
* */
@RestController
@RequestMapping("/comment")
@Slf4j
public class CommentController {

    @Resource
    private CommentService commentService;

    /*
    * 发布评论
    * */
    @PostMapping("/publish")
    @ApiOperationLog(description = "发布评论")
    public Response<?> pubilishComment(@Validated @RequestBody PublishCommentReqVO publishCommentReqVO){
        return commentService.publishComment(publishCommentReqVO);
    }

    /*
    * 评论分页查询
    * */
    @PostMapping("/list")
    @ApiOperationLog(description = "评论分页查询")
    public PageResponse<FindCommentItemRspVO> findCommentPageList(@Validated @RequestBody FindCommentPageListReqVO findCommentPageListReqVO) {
        return commentService.findCommentPageList(findCommentPageListReqVO);
    }

    /*
    * 二级评论分页查询
    * */
    @PostMapping("/child/list")
    @ApiOperationLog(description = "二级评论分页查询")
    public PageResponse<FindChildCommentItemRspVO> findChildCommentPageList(@Validated @RequestBody FindChildCommentPageListReqVO findChildCommentPageListReqVO) {
        return commentService.findChildCommentPageList(findChildCommentPageListReqVO);
    }

    /*
    * 评论点赞
    * */
    @PostMapping("/like")
    @ApiOperationLog(description = "评论点赞")
    public Response<?> likeComment(@Validated @RequestBody LikeCommentReqVO likeCommentReqVO){
        return commentService.likeComment(likeCommentReqVO);
    }

    /*
    * 评论取消点赞
    * */
    @PostMapping("/unlike")
    @ApiOperationLog(description = "评论取消点赞")
    public Response<?> unlikeComment(@Validated @RequestBody UnLikeCommentReqVO unLikeCommentReqVO) {
        return commentService.unlikeComment(unLikeCommentReqVO);
    }

    /*
    * 删除评论
    * */
    @PostMapping("/delete")
    @ApiOperationLog(description = "删除评论")
    public Response<?> deleteComment(@Validated @RequestBody DeleteCommentReqVO deleteCommentReqVO) {
        return commentService.deleteComment(deleteCommentReqVO);
    }

}
