package com.quanxiaoha.xiaohashu.kv.biz.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.dto.rep.BatchAddCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.BatchFindCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.DeleteCommentContentReqDTO;

/*
* 评论内容存储服务
* */
public interface CommentContentService {

    /*
    * 批量添加评论内容
    * */
    Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

    /**
     * 批量查询评论内容
     */
    Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO);

    /**
     * 删除评论内容
     */
    Response<?> deleteCommentContent(DeleteCommentContentReqDTO deleteCommentContentReqDTO);

}
