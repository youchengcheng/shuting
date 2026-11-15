package com.quanxiaoha.xiaohashu.notification.biz.rpc;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 用户服务 RPC 调用
 * */
@Component
@Slf4j
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /*
     * 批量查询通知发起者的用户资料
     * */
    public Map<Long, FindUserByIdRspDTO> findByIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        // 构建用户服务的批量查询参数，每批数量由调用方控制
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(userIds);
        Response<List<FindUserByIdRspDTO>> response;
        try {
            response = userFeignApi.findByIds(findUsersByIdsReqDTO);
        } catch (RuntimeException ex) {
            log.warn("通知发起者资料查询失败", ex);
            throw new BizException(ResponseCodeEnum.USER_RPC_QUERY_FAILED);
        }

        // 查询失败交由调用方降级，成功返回空列表表示没有用户资料
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BizException(ResponseCodeEnum.USER_RPC_QUERY_FAILED);
        }
        Map<Long, FindUserByIdRspDTO> userProfiles = new HashMap<>();
        for (FindUserByIdRspDTO findUserByIdRspDTO : response.getData()) {
            userProfiles.put(findUserByIdRspDTO.getId(), findUserByIdRspDTO);
        }
        return userProfiles;
    }
}
