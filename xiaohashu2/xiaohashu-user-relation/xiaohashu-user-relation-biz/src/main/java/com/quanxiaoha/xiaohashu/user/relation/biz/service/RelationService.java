package com.quanxiaoha.xiaohashu.user.relation.biz.service;

import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.user.relation.dto.req.IsFollowedReqDTO;

public interface RelationService {

    /*
    * 关注用户
    * */
    Response<?> follow(FollowUserReqVO followUserReqVO);

    /*
    * 取消关注
    * */
    Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO);

    /*
    * 查询关注列表
    * */
    PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO);

    /*
    * 查询粉丝列表
    * */
    PageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO findFansListReqVO);

    /*
    * 校验当前登录用户与目标用户的关注关系状态
    * 返回 0-未关注 / 1-已关注(单向) / 2-互相关注
    * */
    Response<Integer> isFollowed(IsFollowedReqDTO isFollowedReqDTO);

}
