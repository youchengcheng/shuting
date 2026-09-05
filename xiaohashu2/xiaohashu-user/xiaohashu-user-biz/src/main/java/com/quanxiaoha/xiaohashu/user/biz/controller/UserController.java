package com.quanxiaoha.xiaohashu.user.biz.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.FindUserProfileReqVO;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.FindUserProfileRspVO;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.UpdateUserInfoReqVO;
import com.quanxiaoha.xiaohashu.user.biz.service.UserService;
import com.quanxiaoha.xiaohashu.user.dto.req.*;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    /*
    * 修改用户信息
    * */
    @PostMapping(value = "/update",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> updateUserInfo(@Validated UpdateUserInfoReqVO updateUserInfoReqVO){
        return userService.updateUserInfo(updateUserInfoReqVO);
    }

    /*
    * 查询用户主页资料（userId 为空时查询当前登录用户）
    * */
    @PostMapping(value = "/profile")
    public Response<FindUserProfileRspVO> findUserProfile(@Validated @RequestBody FindUserProfileReqVO findUserProfileReqVO) {
        return userService.findUserProfile(findUserProfileReqVO);
    }

    /*
    * 用户注册
    * */
    @PostMapping("/register")
    @ApiOperationLog(description = "用户注册")
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO){
        return userService.register(registerUserReqDTO);
    }

    /*
    * 根据手机号查询用户信息
    * */
    @PostMapping("/findByPhone")
    @ApiOperationLog(description = "根据手机号查询用户信息")
    public Response<FindUserByPhoneRspDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO){
        return userService.findByPhone(findUserByPhoneReqDTO);
    }

    /*
    * 更新密码
    * */
    @PostMapping("/password/update")
    @ApiOperationLog(description = "更新密码")
    public Response<?> findByPhone(@Validated @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO){
        return userService.updatePassword(updateUserPasswordReqDTO);
    }

    /*
    * 根据用户 ID 查询用户信息
    * */
    @PostMapping("/findById")
    @ApiOperationLog(description = "根据用户 ID 查询用户信息")
    public Response<FindUserByIdRspDTO> findById(@Validated @RequestBody FindUserByIdReqDTO findUserByIdReqDTO){
        return userService.findById(findUserByIdReqDTO);
    }

    /*
    * 批量查询用户信息
    * */
    @PostMapping("/findByIds")
    @ApiOperationLog(description = "批量查询用户信息")
    public Response<List<FindUserByIdRspDTO>> findByIds(@Validated @RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        return userService.findByIds(findUsersByIdsReqDTO);
    }

}
