package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationDO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.FindNotificationItemRspVO;

import java.util.List;

/*
 * 通知预览服务
 * */
public interface NotificationPreviewService {

    /*
     * 查询互动用户及内容预览，并校验当前用户的访问权限
     * */
    List<FindNotificationItemRspVO> render(long viewerId, List<NotificationDO> records, NotificationUserStateDO state);
}
