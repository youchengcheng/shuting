package com.quanxiaoha.xiaohashu.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论预览信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentPreviewDTO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 父级 ID
     */
    private Long parentId;

    /**
     * 被回复评论 ID
     */
    private Long replyCommentId;

    /**
     * 发布者用户 ID
     */
    private Long creatorId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片地址
     */
    private String imageUrl;

    /**
     * 当前用户是否可访问
     */
    private Boolean accessible;

}
