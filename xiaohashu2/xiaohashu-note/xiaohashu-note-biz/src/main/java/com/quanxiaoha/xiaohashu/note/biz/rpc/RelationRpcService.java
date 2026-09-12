package com.quanxiaoha.xiaohashu.note.biz.rpc;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.relation.api.RelationFeignApi;
import com.quanxiaoha.xiaohashu.user.relation.dto.req.IsFollowedReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Slf4j
public class RelationRpcService {

    @Resource
    private RelationFeignApi relationFeignApi;

    /*
     * 校验当前登录用户是否已关注目标用户
     * 注意：必须在业务线程中调用，Feign 拦截器会从上下文取 userId 放进请求头
     * */
    public boolean isFollowed(Long targetUserId) {
        IsFollowedReqDTO isFollowedReqDTO = IsFollowedReqDTO.builder()
                .followUserId(targetUserId)
                .build();

        Response<Boolean> response = relationFeignApi.isFollowed(isFollowedReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }

        return Boolean.TRUE.equals(response.getData());
    }

}
