package com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper;

import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FansDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FollowingDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowingDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(FollowingDO record);

    int insertSelective(FollowingDO record);

    FollowingDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(FollowingDO record);

    int updateByPrimaryKey(FollowingDO record);

    /*
    * 查询用户关注列表
    * */
    List<FollowingDO> selectByUserId(Long userId);

    /*
    * 取消关注
    * */
    int deleteByUserIdAndFollowingUserId(@Param("userId") Long userId, @Param("unfollowUserId") Long unfollowUserId);

    /*
    * 查询关注总数
    * */
    int selectCountByUserId(Long userId);

    /*
    * 校验是否已关注
    * */
    int selectCountByUserIdAndFollowingUserId(@Param("userId") Long userId,
                                              @Param("followingUserId") Long followingUserId);

    /*
    * 分页查询
    * */
    List<FollowingDO> selectPageListByUserId(@Param("userId") Long userId,@Param("offset") long offset,@Param("limit") long limit);

    /**
     * 查询关注用户列表
     */
    List<FollowingDO> selectAllByUserId(Long userId);

    /**
     * 查询 userId 关注了 targetUserIds 中的哪些人（返回被关注的 user_id 列表）
     */
    List<Long> selectFollowedUserIds(@Param("userId") Long userId,
                                     @Param("targetUserIds") List<Long> targetUserIds);

    /**
     * 查询 userIds 中哪些人关注了 targetUserId（返回关注了 targetUserId 的 user_id 列表）
     */
    List<Long> selectUserIdsWhoFollowTarget(@Param("targetUserId") Long targetUserId,
                                            @Param("userIds") List<Long> userIds);


}
