package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;

/*
 * 评论回复归属校验
 * */
public interface CommentReplyValidator {

    /*
     * 校验被回复评论归属
     * */
    CommentDO requireTarget(Long noteId, Long replyId);

    /*
     * 校验一级评论与笔记的归属
     * */
    CommentDO requireRoot(Long noteId, CommentDO target);
}
