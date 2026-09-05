package com.quanxiaoha.xiaohashu.kv.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.biz.domain.dataobject.CommentContentDO;
import com.quanxiaoha.xiaohashu.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import com.quanxiaoha.xiaohashu.kv.biz.domain.repository.CommentContentRepository;
import com.quanxiaoha.xiaohashu.kv.biz.service.CommentContentService;
import com.quanxiaoha.xiaohashu.kv.dto.rep.*;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindCommentContentRspDTO;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * 评论内容存储服务实现类
 * */
@Service
@Slf4j
public class CommentContentServiceImpl implements CommentContentService {

    @Resource
    private CassandraTemplate cassandraTemplate;
    @Resource
    private CommentContentRepository commentContentRepository;

    /*
     * 批量添加评论内容
     * */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {

        List<CommentContentReqDTO> comments = batchAddCommentContentReqDTO.getComments();

        //DTO 转 DO
        List<CommentContentDO> contentDOS = comments.stream().map(commentContentReqDTO -> {
            //构建主键类
            CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                    .noteId(commentContentReqDTO.getNoteId())
                    .yearMonth(commentContentReqDTO.getYearMonth())
                    .contentId(UUID.fromString(commentContentReqDTO.getContentId()))
                    .build();

            // DO 实体类
            CommentContentDO commentContentDO = CommentContentDO.builder()
                    .primaryKey(commentContentPrimaryKey)
                    .content(commentContentReqDTO.getContent())
                    .build();
            return commentContentDO;
        }).toList();

        //批量插入
        cassandraTemplate.batchOps()
                .insert(contentDOS)
                .execute();

        return Response.success();
    }

    /*
    * 批量查询评论内容
    * */
    @Override
    public Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO) {
        //归属的笔记 id
        Long noteId = batchFindCommentContentReqDTO.getNoteId();

        //查询评论的发布年月,内容,uuid
        List<FindCommentContentReqDTO> commentContentKeys = batchFindCommentContentReqDTO.getCommentContentKeys();

        //过滤出年月
        List<@NotBlank(message = "发布年月不能为空") String> yearMonths = commentContentKeys.stream()
                .map(FindCommentContentReqDTO::getYearMonth)
                .distinct()//去重
                .collect(Collectors.toList());

        //过滤出评论内容 UUID
        List<UUID> contentIds = commentContentKeys.stream()
                .map(commentContentKey -> UUID.fromString(commentContentKey.getContentId()))
                .distinct()
                .collect(Collectors.toList());

        //批量查询 Cassandra
        List<CommentContentDO> commentContentDOS = commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(noteId, yearMonths, contentIds);

        List<FindCommentContentRspDTO> findCommentContentRspDTOS = new ArrayList<>();
        if(CollUtil.isNotEmpty(commentContentDOS)){
            //DO 转为 DTO
            findCommentContentRspDTOS = commentContentDOS.stream()
                    .map(commentContentDO -> FindCommentContentRspDTO.builder()
                            .contentId(String.valueOf(commentContentDO.getPrimaryKey().getContentId()))
                            .content(commentContentDO.getContent())
                            .build())
                    .toList();
        }
        //反参
        return Response.success(findCommentContentRspDTOS);
    }

    /*
    * 删除评论正文
    * */
    @Override
    public Response<?> deleteCommentContent(DeleteCommentContentReqDTO deleteCommentContentReqDTO) {
        Long noteId = deleteCommentContentReqDTO.getNoteId();
        String yearMonth = deleteCommentContentReqDTO.getYearMonth();
        String contentId = deleteCommentContentReqDTO.getContentId();

        // 删除评论正文
        commentContentRepository.deleteByPrimaryKeyNoteIdAndPrimaryKeyYearMonthAndPrimaryKeyContentId(noteId, yearMonth, UUID.fromString(contentId));

        return Response.success();
    }

}