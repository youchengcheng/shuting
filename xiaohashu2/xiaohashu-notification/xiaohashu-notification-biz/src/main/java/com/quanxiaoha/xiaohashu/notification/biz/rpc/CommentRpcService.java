package com.quanxiaoha.xiaohashu.notification.biz.rpc;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.api.CommentFeignApi;
import com.quanxiaoha.xiaohashu.comment.dto.CommentBatchQueryReqDTO;
import com.quanxiaoha.xiaohashu.comment.dto.CommentPreviewDTO;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 评论服务 RPC 调用
 * */
@Component
@Slf4j
public class CommentRpcService {

    @Resource
    private CommentFeignApi commentFeignApi;

    /*
     * 查询当前用户可访问的评论预览
     * */
    public Map<Long, CommentPreviewDTO> findPreviews(long viewerId, List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }

        // 评论服务再次校验当前查看者对笔记和评论的访问权限
        CommentBatchQueryReqDTO commentBatchQueryReqDTO = new CommentBatchQueryReqDTO();
        commentBatchQueryReqDTO.setIds(commentIds);
        commentBatchQueryReqDTO.setViewerId(viewerId);
        Response<List<CommentPreviewDTO>> response;
        try {
            response = commentFeignApi.previews(commentBatchQueryReqDTO);
        } catch (RuntimeException ex) {
            log.warn("通知评论预览查询失败", ex);
            throw new BizException(ResponseCodeEnum.COMMENT_RPC_QUERY_FAILED);
        }

        // 查询失败保留为临时不可用，成功返回空列表表示评论已不存在
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BizException(ResponseCodeEnum.COMMENT_RPC_QUERY_FAILED);
        }
        Map<Long, CommentPreviewDTO> commentPreviews = new HashMap<>();
        for (CommentPreviewDTO commentPreviewDTO : response.getData()) {
            commentPreviews.put(commentPreviewDTO.getId(), commentPreviewDTO);
        }
        return commentPreviews;
    }
}
