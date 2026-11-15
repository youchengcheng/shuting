package com.quanxiaoha.xiaohashu.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知接收者
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecipient {

    /**
     * 接收者用户 ID
     */
    private Long userId;

    /**
     * 接收者角色
     */
    private String role;

}
