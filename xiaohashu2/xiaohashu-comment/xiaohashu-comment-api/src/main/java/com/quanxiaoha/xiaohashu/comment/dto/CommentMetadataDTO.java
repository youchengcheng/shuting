package com.quanxiaoha.xiaohashu.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentMetadataDTO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 发布者用户 ID
     */
    private Long creatorId;

    /**
     * 父级 ID
     */
    private Long parentId;

    /**
     * 被回复评论 ID
     */
    private Long replyCommentId;

    /**
     * 被回复用户 ID
     */
    private Long replyUserId;

    /**
     * 评论层级
     */
    private Integer level;

}
