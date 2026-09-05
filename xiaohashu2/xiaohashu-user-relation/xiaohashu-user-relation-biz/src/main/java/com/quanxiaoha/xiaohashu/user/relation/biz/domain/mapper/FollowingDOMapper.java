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
    * 分页查询
    * */
    List<FollowingDO> selectPageListByUserId(@Param("userId") Long userId,@Param("offset") long offset,@Param("limit") long limit);

    /**
     * 查询关注用户列表
     */
    List<FollowingDO> selectAllByUserId(Long userId);


}