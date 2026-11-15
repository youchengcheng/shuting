package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知互动用户信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationActorRspVO {

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

}
