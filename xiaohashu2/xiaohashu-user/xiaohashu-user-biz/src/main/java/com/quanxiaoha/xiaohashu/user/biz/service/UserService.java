package com.quanxiaoha.xiaohashu.user.biz.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.UpdateUserInfoReqVO;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.FindUserProfileReqVO;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.FindUserProfileRspVO;
import com.quanxiaoha.xiaohashu.user.dto.req.*;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;

import java.util.List;

/*
* 用户业务
* */
public interface UserService {

    /*
    * 更新用户信息
    * */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    /*
    * 用户注册
    * */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    /*
    * 根据手机号查询用户信息
    * */
    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /*
    * 更新密码
    * */
    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /*
    * 根据用户 ID 查询用户信息
    * */
    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);

    /*
    * 批量根据用户 ID 查询用户信息
    * */
    Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO);

    /**
     * 获取用户主页信息
     */
    Response<FindUserProfileRspVO> findUserProfile(FindUserProfileReqVO findUserProfileReqVO);

    /*
    * 删除用户的本地缓存（用户信息 + 主页资料）
    * 本地缓存在每个实例的 JVM 内，由广播消息触发各实例自行调用
    * */
    void deleteUserLocalCache(Long userId);
}
