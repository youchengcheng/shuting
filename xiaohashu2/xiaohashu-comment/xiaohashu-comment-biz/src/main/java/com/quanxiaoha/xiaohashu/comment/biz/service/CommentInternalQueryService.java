package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.xiaohashu.comment.biz.model.vo.CommentContextReqVO;
import com.quanxiaoha.xiaohashu.comment.biz.model.vo.CommentContextRspVO;
import com.quanxiaoha.xiaohashu.comment.dto.*;

import java.util.List;

/*
 * 评论内部查询服务
 * */
public interface CommentInternalQueryService {

    /*
     * 批量查询元数据
     * */
    List<CommentMetadataDTO> metadata(CommentBatchQueryReqDTO commentBatchQueryReqDTO);

    /*
     * 批量查询预览信息
     * */
    List<CommentPreviewDTO> previews(CommentBatchQueryReqDTO commentBatchQueryReqDTO);

    /*
     * 查询目标评论及相关评论
     * */
    CommentContextRspVO context(CommentContextReqVO commentContextReqVO);

}
