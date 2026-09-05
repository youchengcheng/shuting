package com.quanxiaoha.xiaohashu.user.biz.domain.mapper;


import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.UserDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.UserCountDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserDO record);

    int insertSelective(UserDO record);

    UserDO selectByPrimaryKey(Long id);

    /**
     * 根据手机号查询记录
     * @param phone
     * @return
     */
    UserDO selectByPhone(String phone);

    int updateByPrimaryKeySelective(UserDO record);

    int updateByPrimaryKey(UserDO record);

    /*
    * 批量查询用户信息
    * */
    List<UserDO> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 根据用户 ID 查询用户计数
     */
    UserCountDO selectUserCountByUserId(Long userId);
}
