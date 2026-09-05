package com.quanxiaoha.xiaohashu.comment.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.quanxiaoha.framework.common.constant.DateConstants;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentBO;
import com.quanxiaoha.xiaohashu.kv.api.KeyValueFeignApi;
import com.quanxiaoha.xiaohashu.kv.dto.rep.*;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindCommentContentRspDTO;
import jakarta.annotation.Resource;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
* KV 键值服务
* */
@Component
public class KeyValueRpcService {

    @Resource
    private KeyValueFeignApi keyValueFeignApi;

    /*
    * 批量存储评论内容
    * */
    public boolean batchSaveCommentContent(List<CommentBO> commentBOS){
        List<CommentContentReqDTO> comments = Lists.newArrayList();

        //BO 转 DTO
        commentBOS.forEach(commentBO -> {
            CommentContentReqDTO commentContentReqDTO = CommentContentReqDTO.builder()
                    .noteId(commentBO.getNoteId())
                    .content(commentBO.getContent())
                    .contentId(commentBO.getContentUuid())
                    .yearMonth(commentBO.getCreateTime().format(DateConstants.DATE_FORMAT_Y_M))
                    .build();
            comments.add(commentContentReqDTO);
        });

        //构建接口入参实体类
        BatchAddCommentContentReqDTO batchAddCommentContentReqDTO = BatchAddCommentContentReqDTO.builder()
                .comments(comments)
                .build();

        //调用 KV 存储服务
        Response<?> response = keyValueFeignApi.batchAddCommentContent(batchAddCommentContentReqDTO);

        // 若返参中 success 为 false, 则主动抛出异常，以便调用层回滚事务
        if (!response.isSuccess()) {
            throw new RuntimeException("批量保存评论内容失败");
        }
        return true;
    }

    /*
    * 批量查询评论内容
    * */
    public List<FindCommentContentRspDTO> batchFindCommentContent(Long noteId, List<FindCommentContentReqDTO> findCommentContentReqDTOS) {
        BatchFindCommentContentReqDTO batchFindCommentContentReqDTO = BatchFindCommentContentReqDTO.builder()
                .commentContentKeys(findCommentContentReqDTOS)
                .noteId(noteId)
                .build();

        Response<List<FindCommentContentRspDTO>> response = keyValueFeignApi.batchFindCommentContent(batchFindCommentContentReqDTO);

        if(!response.isSuccess() || Objects.isNull(response.getData()) || CollUtil.isEmpty(response.getData())){
            return null;
        }
        return response.getData();
    }

    /*
    * 删除评论内容
    * */
    public boolean deleteCommentContent(Long noteId, LocalDateTime createTime,String contentId) {
        //构建入参实体
        DeleteCommentContentReqDTO deleteCommentContentReqDTO = DeleteCommentContentReqDTO.builder()
                .noteId(noteId)
                .contentId(contentId)
                .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(createTime))
                .build();

        //调用 KV 存储服务
        Response<?> response = keyValueFeignApi.deleteCommentContent(deleteCommentContentReqDTO);

        if(!response.isSuccess()) {
            throw new RuntimeException("删除评论内容失败");
        }

        return true;
    }

}
