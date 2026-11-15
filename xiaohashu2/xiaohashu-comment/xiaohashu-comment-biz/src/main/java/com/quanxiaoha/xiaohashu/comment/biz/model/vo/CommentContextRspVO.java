package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 相关评论查询结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentContextRspVO {

    /**
     * 评论展示模式
     */
    private String mode;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 目标评论 ID
     */
    private Long targetCommentId;

    /**
     * 一级评论 ID
     */
    private Long rootCommentId;

    /**
     * 一级评论所在页码
     */
    private Integer rootPageNo;

    /**
     * 回复评论所在页码
     */
    private Integer childPageNo;

    /**
     * 一级评论每页条数
     */
    private Integer rootPageSize;

    /**
     * 回复评论每页条数
     */
    private Integer childPageSize;

    /**
     * 评论不可用原因
     */
    private String unavailableReason;

    /**
     * 一级评论
     */
    private CommentContextItemVO root;

    /**
     * 目标评论
     */
    private CommentContextItemVO target;

    /**
     * 被回复评论
     */
    private CommentContextItemVO repliedTo;

}
