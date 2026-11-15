package com.quanxiaoha.xiaohashu.notification.biz.util;

import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationBusinessKey;
import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;
import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationRecipient;
import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;

import java.util.Set;

/*
 * 通知互动事件校验工具
 * */
public final class NotificationEventUtils {

    private NotificationEventUtils() {
    }

    /*
     * 校验事件身份、接收者、定位信息和业务键
     * */
    public static void validateEvent(NotificationEvent event) {
        if (event == null || event.getOccurredAt() == null || event.getActorId() == null || event.getActorId() <= 0
                || event.getRecipients() == null || event.getRecipients().isEmpty() || event.getRecipients().size() > 2) {
            throw new IllegalArgumentException("通知事件身份信息不完整或无效");
        }
        // 验证互动类型后，再按类型检查接收者数量和目标定位
        NotificationCategoryEnum.forType(event.getType());
        if (!"REPLY".equals(event.getType()) && event.getRecipients().size() != 1) {
            throw new IllegalArgumentException("通知接收者数量不正确");
        }
        for (NotificationRecipient recipient : event.getRecipients()) {
            if (recipient == null || recipient.getUserId() == null || recipient.getUserId() <= 0
                    || recipient.getRole() == null || !Set.of("OWNER", "REPLY_TARGET", "FOLLOW_TARGET").contains(recipient.getRole())) {
                throw new IllegalArgumentException("通知接收者信息无效");
            }
            // 关注只允许目标用户角色，回复允许作者和被回复人两种角色
            boolean validRole = "FOLLOW".equals(event.getType()) ? "FOLLOW_TARGET".equals(recipient.getRole())
                    : "OWNER".equals(recipient.getRole()) || ("REPLY".equals(event.getType()) && "REPLY_TARGET".equals(recipient.getRole()));
            if (!validRole) {
                throw new IllegalArgumentException("接收者角色与通知类型不匹配");
            }
        }
        // 根据互动类型取业务对象 ID，笔记、评论和关注目标的业务键不能混用
        Long objectId;
        switch (event.getType()) {
            case "NOTE_LIKE", "NOTE_COLLECT" -> objectId = event.getNoteId();
            case "COMMENT_LIKE", "COMMENT", "REPLY" -> {
                if (event.getNoteId() == null || event.getNoteId() <= 0) {
                    throw new IllegalArgumentException("缺少通知所属笔记信息");
                }
                if ("REPLY".equals(event.getType()) && (event.getReplyCommentId() == null || event.getReplyCommentId() <= 0)) {
                    throw new IllegalArgumentException("缺少被回复评论的定位信息");
                }
                objectId = event.getCommentId();
            }
            case "FOLLOW" -> {
                if (event.getRecipients().size() != 1) {
                    throw new IllegalArgumentException("关注通知只能包含一个接收者");
                }
                objectId = event.getRecipients().get(0).getUserId();
            }
            default -> throw new IllegalArgumentException("未知的通知类型");
        }
        // 重新生成业务键进行比对，避免篡改目标或重复投递绕过去重约束
        if (!NotificationBusinessKey.of(event.getType(), event.getActorId(), objectId).equals(event.getBusinessKey())) {
            throw new IllegalArgumentException("通知业务键与事件内容不匹配");
        }
    }

}
