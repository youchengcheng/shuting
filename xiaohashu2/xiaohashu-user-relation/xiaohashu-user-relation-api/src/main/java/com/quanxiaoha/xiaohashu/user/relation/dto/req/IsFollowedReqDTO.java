package com.quanxiaoha.xiaohashu.user.relation.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验当前登录用户是否已关注目标用户
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IsFollowedReqDTO {

    /**
     * 目标用户 ID（被关注者）
     */
    @NotNull(message = "目标用户 ID 不能为空")
    private Long followUserId;

}
