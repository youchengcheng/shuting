package com.quanxiaoha.xiaohashu.note.biz.rpc;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUserByIdReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import cn.hutool.core.collection.CollUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /*
    * 根据用户 ID 查询用户信息
    * */
    public FindUserByIdRspDTO findById(Long userId){

        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if(Objects.isNull(response) || !response.isSuccess()){
            return null;
        }
        return response.getData();
    }

    /*
    * 批量根据用户 ID 查询用户信息
    * */
    public Map<Long, FindUserByIdRspDTO> findByIds(List<Long> userIds){

        if(CollUtil.isEmpty(userIds)){
            return Collections.emptyMap();
        }

        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(userIds);

        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);

        if(Objects.isNull(response) || !response.isSuccess() || CollUtil.isEmpty(response.getData())){
            return Collections.emptyMap();
        }

        return response.getData().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FindUserByIdRspDTO::getId, Function.identity()));
    }

}
