package com.quanxiaoha.xiaohashu.user.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户计数（t_user_count 查询用）
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCountDO {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 粉丝数
     */
    private Long fansTotal;

    /**
     * 关注数
     */
    private Long followingTotal;

    /**
     * 笔记总数
     */
    private Long noteTotal;

    /**
     * 获赞数
     */
    private Long likeTotal;

    /**
     * 收藏数
     */
    private Long collectTotal;
}
