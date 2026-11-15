package com.quanxiaoha.xiaohashu.notification.biz.config;

import jakarta.annotation.PostConstruct;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * 通知服务启用和 SSE 连接限制配置
 * */
@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationSettings {

    /**
     * 是否启用通知服务
     */
    private boolean enabled;

    /**
     * 通知启用时间
     */
    private String enabledAt;

    /**
     * SSE 连接配置
     */
    private Stream stream = new Stream();

    /**
     * SSE 连接数量限制
     */
    @Data
    public static class Stream {

        /**
         * 每个用户的最大连接数
         */
        private int maxPerUser = 10;

        /**
         * 每个服务实例的最大连接数
         */
        private int maxPerInstance = 1000;
    }

    /*
     * 启动时校验通知启用时间和连接限制
     * */
    @PostConstruct
    public void validate() {
        if (enabled) {
            cutoff();
        }
        if (stream.maxPerUser < 1 || stream.maxPerInstance < 1) {
            throw new IllegalStateException("通知连接数量限制必须大于零");
        }
    }

    /*
     * 获取精确到秒的通知启用时间
     * */
    public LocalDateTime cutoff() {
        if (enabledAt == null || enabledAt.isBlank()) {
            throw new IllegalStateException("启用通知服务前必须配置通知启用时间");
        }
        LocalDateTime result = parseTime(enabledAt);
        if (result.getNano() != 0) {
            throw new IllegalStateException("通知启用时间必须使用上海时区的整秒时间");
        }
        return result;
    }

    /*
     * 解析 ISO 格式或项目现有格式的事件时间
     * */
    public static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少互动发生时间");
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (java.time.format.DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } catch (java.time.format.DateTimeParseException invalid) {
                throw new IllegalArgumentException("互动发生时间格式不正确", invalid);
            }
        }
    }
}
