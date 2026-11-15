package com.quanxiaoha.xiaohashu.notification.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前通知请求的登录信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationSessionDTO {

    /**
     * 用户 ID
     */
    private long userId;

    /**
     * 登录 Token
     */
    private String token;

}
