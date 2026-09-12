package com.quanxiaoha.xiaohashu.user.relation.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.relation.constant.ApiConstants;
import com.quanxiaoha.xiaohashu.user.relation.dto.req.IsFollowedReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface RelationFeignApi {

    String PREFIX = "/relation";

    /*
    * 校验当前登录用户是否已关注目标用户
    * */
    @PostMapping(value = PREFIX + "/isFollowed")
    Response<Boolean> isFollowed(@RequestBody IsFollowedReqDTO isFollowedReqDTO);

}
