package com.quanxiaoha.framework.notification.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 保存无法处理的来源通知事件，写入成功后才确认消息消费
 */
public class NotificationEventIsolationWriter {

    /**
     * 来源业务数据库操作模板
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 通知事件采集配置
     */
    private final NotificationOutboxProperties notificationOutboxProperties;

    /**
     * 事件来源服务名称
     */
    private final String producerService;

    /*
     * 初始化异常事件写入组件
     * */
    public NotificationEventIsolationWriter(JdbcTemplate jdbcTemplate, NotificationOutboxProperties notificationOutboxProperties, String producerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationOutboxProperties = notificationOutboxProperties;
        this.producerService = producerService;
    }

    /*
     * 保存原始消息和无法处理的原因
     * */
    public void save(String topic, String payload, String reason) {
        // 事件采集未启用时不保存隔离记录
        if (!notificationOutboxProperties.getEvents().isEnabled()) {
            return;
        }
        // 必须保存原始消息，便于确认问题及后续人工重放
        Objects.requireNonNull(payload, "原始通知消息不能为空");

        // 使用服务名称和消息主题标识事件来源
        String source = producerService + ":" + topic;
        if (source.length() > 64) {
            throw new IllegalArgumentException("通知隔离来源标识不能超过 64 个字符");
        }
        // 隔离原因按表字段长度截取，空原因使用中文默认说明
        String message = reason == null ? "来源通知事件无效" : reason;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }

        // 同一来源的相同消息只保存一次
        jdbcTemplate.update("INSERT INTO t_notification_event_error(source,event_identity,payload,reason,created_at) VALUES(?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE event_identity=event_identity",
                source, identity(source, payload), payload, message,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.SECONDS));
    }

    /*
     * 根据来源和原始消息生成去重标识
     * */
    public static String identity(String source, String payload) {
        try {
            // 相同来源的相同原始消息生成同一个摘要，避免重投写入多条隔离记录
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((source + "\n" + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("无法生成通知隔离消息摘要", impossible);
        }
    }
}
