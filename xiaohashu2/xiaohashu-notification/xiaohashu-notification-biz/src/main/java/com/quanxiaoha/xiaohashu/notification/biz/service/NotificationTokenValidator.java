package com.quanxiaoha.xiaohashu.notification.biz.service;

import java.util.Collection;
import java.util.Map;

/*
 * 通知登录凭证校验服务
 * */
public interface NotificationTokenValidator {

    /*
     * 校验凭证是否属于当前登录用户
     * */
    void require(String token, long userId);

    /*
     * 批量校验凭证与用户 ID 的对应关系
     * */
    Map<String, Boolean> validate(Map<String, Long> tokens);

    /*
     * 批量查询有效凭证对应的用户 ID
     * */
    Map<String, Long> identities(Collection<String> tokens);
}
