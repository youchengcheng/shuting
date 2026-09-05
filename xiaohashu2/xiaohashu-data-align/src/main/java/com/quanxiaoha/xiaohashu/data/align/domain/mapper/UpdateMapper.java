package com.quanxiaoha.xiaohashu.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 更新
 */
public interface UpdateMapper {

    /**
     * 更新 t_user_count 计数表总关注数
     */
    int updateUserFollowingTotalByUserId(@Param("userId") long userId, 
                                         @Param("followingTotal") int followingTotal);
}
