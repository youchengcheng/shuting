package com.quanxiaoha.framework.notification.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 通知事件采集配置
 */
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
public class NotificationOutboxProperties {

    /**
     * 事件采集开关
     */
    private final Events events = new Events();

    /**
     * 通知启用时间，早于该时间的事件不进行采集
     */
    private LocalDateTime enabledAt;

    /*
     * 校验配置，并判断事件是否在通知启用后产生
     * */
    public boolean accepts(LocalDateTime occurredAt) {
        // 未开启采集时不校验事件时间，保持业务链路可独立运行
        if (!events.enabled) {
            return false;
        }
        // 开启采集前必须固定统一边界，不能自动把当前时间当作启用时间
        if (enabledAt == null) {
            throw new IllegalStateException("开启通知事件采集前必须配置 notification.enabled-at");
        }
        // 启用时间须为上海时区的整秒时间，避免各来源服务边界不一致
        if (enabledAt.getNano() != 0) {
            throw new IllegalStateException("notification.enabled-at 必须为上海时区的整秒本地日期时间");
        }
        // 消息必须保留原发生时间，不得用投递或消费时间代替
        if (occurredAt == null) {
            throw new IllegalArgumentException("通知事件必须携带原始互动发生时间");
        }
        // 启用边界当秒的事件也需要采集，早于边界的历史互动不补发
        return !occurredAt.truncatedTo(ChronoUnit.SECONDS).isBefore(enabledAt);
    }

    /**
     * 通知事件开关配置
     */
    @Getter
    @Setter
    public static class Events {

        /**
         * 是否开启事件采集
         */
        private boolean enabled = false;
    }
}
