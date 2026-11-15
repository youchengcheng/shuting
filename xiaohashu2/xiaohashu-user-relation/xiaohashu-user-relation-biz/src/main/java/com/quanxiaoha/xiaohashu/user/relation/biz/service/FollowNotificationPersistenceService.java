package com.quanxiaoha.xiaohashu.user.relation.biz.service;

import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;

/*
 * 关注关系及通知事件持久化服务
 * */
public interface FollowNotificationPersistenceService {

    /*
     * 在同一事务中保存业务数据和通知事件
     * */
    boolean persist(FollowUserMqDTO followUserMqDTO);

}
