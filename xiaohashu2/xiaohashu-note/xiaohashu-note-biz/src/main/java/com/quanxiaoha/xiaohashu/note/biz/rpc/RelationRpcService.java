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
     * 校验当前登录用户与目标用户的关注关系状态
     * 返回 0-未关注 / 1-已关注(单向) / 2-互相关注
     * 注意：必须在业务线程中调用，Feign 拦截器会从上下文取 userId 放进请求头
     * */
    public int isFollowed(Long targetUserId) {
        IsFollowedReqDTO isFollowedReqDTO = IsFollowedReqDTO.builder()
                .followUserId(targetUserId)
                .build();

        Response<Integer> response = relationFeignApi.isFollowed(isFollowedReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return 0; // 降级为未关注
        }

        return Objects.requireNonNullElse(response.getData(), 0);
    }

}
