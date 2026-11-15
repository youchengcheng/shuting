package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

/**
 * 查询相关评论请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentContextReqVO {

    /**
     * 笔记 ID
     */
    @NotNull
    @Positive
    private Long noteId;

    /**
     * 评论 ID
     */
    @NotNull
    @Positive
    private Long commentId;

}
