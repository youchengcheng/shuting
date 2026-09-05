package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询二级评论分页数据
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindChildCommentPageListReqVO {

    @NotNull(message = "父评论 ID 不能为空")
    private Long parentCommentId;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;
}
