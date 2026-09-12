package com.quanxiaoha.xiaohashu.user.relation.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 取关用户 MQ 消息 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnfollowUserMqDTO {

    /**
     * 执行取关操作的用户 ID（当前用户）
     */
    private Long userId;

    /**
     * 被取关的用户 ID
     */
    private Long unfollowUserId;

    /**
     * 取关时间
     */
    private LocalDateTime createTime;
}