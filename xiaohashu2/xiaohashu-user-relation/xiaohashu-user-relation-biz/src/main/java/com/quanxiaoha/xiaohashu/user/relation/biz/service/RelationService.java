package com.quanxiaoha.xiaohashu.user.relation.biz.service;

import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.vo.*;

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

}
