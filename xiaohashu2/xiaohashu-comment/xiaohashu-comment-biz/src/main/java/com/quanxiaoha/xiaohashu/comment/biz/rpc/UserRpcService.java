package com.quanxiaoha.xiaohashu.comment.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.apache.catalina.LifecycleState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/*
* 用户服务
* */
@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /*
    * 批量查询用户信息
    * */
    public List<FindUserByIdRspDTO> findByIds(List<Long> ids){

        //判断id是否为空
        if(CollUtil.isEmpty(ids)) return null;

        //构建入参
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(ids.stream().distinct().collect(Collectors.toList()));

        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);

        if(!response.isSuccess() || Objects.isNull(response.getData()) || CollUtil.isEmpty(response.getData())){
            return null;
        }

        return response.getData();




    }

}
