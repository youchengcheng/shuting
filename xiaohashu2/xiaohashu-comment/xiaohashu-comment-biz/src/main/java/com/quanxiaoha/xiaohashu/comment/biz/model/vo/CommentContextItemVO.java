package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 相关评论信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentContextItemVO {

    /**
     * 评论 ID
     */
    private Long commentId;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 发布者用户 ID
     */
    private Long userId;

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

    /**
     * 头像
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片地址
     */
    private String imageUrl;

    /**
     * 发布时间
     */
    private LocalDateTime createTime;

    /**
     * 点赞数
     */
    private Long likeTotal;

    /**
     * 回复总数
     */
    private Long childCommentTotal;

    /**
     * 当前用户是否点赞
     */
    private Boolean liked;

}
