package com.quanxiaoha.xiaohashu.user.relation.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUserByIdReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/*
* 用户服务
* */
@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /*
    * 查询用户信息
    * */
    public FindUserByIdRspDTO  findById(Long userId){
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);
        if(Objects.isNull(response.getData()) || !response.isSuccess()){
            return null;
        }

        return response.getData();
    }

    /*
    * 批量查询用户信息
    * */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds){
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(userIds);

        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);

        //判断查询是否成功，数据是否为空，集合是否为空
        if(Objects.isNull(response.getData()) || !response.isSuccess() || CollUtil.isEmpty(response.getData())){
            return null;
        }

        return response.getData();
    }

}
