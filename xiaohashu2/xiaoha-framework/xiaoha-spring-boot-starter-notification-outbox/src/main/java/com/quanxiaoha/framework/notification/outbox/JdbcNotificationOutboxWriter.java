package com.quanxiaoha.framework.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * 将通知事件写入业务数据库的 Outbox 表
 */
public class JdbcNotificationOutboxWriter implements NotificationOutboxWriter {

    /**
     * 来源业务数据库操作模板
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 通知事件序列化工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 通知事件采集配置
     */
    private final NotificationOutboxProperties notificationOutboxProperties;

    /**
     * 事件来源服务名称
     */
    private final String producerService;

    /*
     * 初始化 Outbox 写入组件
     * */
    public JdbcNotificationOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                       NotificationOutboxProperties notificationOutboxProperties, String producerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationOutboxProperties = notificationOutboxProperties;
        this.producerService = producerService;
    }

    /*
     * 在来源业务事务中保存通知事件
     * */
    @Override
    public void save(NotificationEvent event) {
        // 未开启事件采集时直接返回
        if (!notificationOutboxProperties.getEvents().isEnabled()) {
            return;
        }

        // Outbox 记录必须与来源业务使用同一个数据库事务
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || jdbcTemplate.getDataSource() == null || !TransactionSynchronizationManager.hasResource(jdbcTemplate.getDataSource())) {
            throw new IllegalStateException("通知 outbox 必须使用来源业务数据库的事务");
        }

        // 事件不能为空，禁止写入缺失的消息内容
        Objects.requireNonNull(event, "通知事件不能为空");
        // 只采集通知启用时间之后产生的事件
        if (!notificationOutboxProperties.accepts(event.getOccurredAt())) {
            return;
        }
        // 消息中的来源服务必须与实际应用名称一致，防止跨服务业务键混用
        if (!Objects.equals(producerService, event.getProducerService())) {
            throw new IllegalArgumentException("通知来源服务必须与 spring.application.name 一致");
        }
        // 业务键用于来源库去重，缺失时不能保存 outbox 记录
        if (event.getBusinessKey() == null || event.getBusinessKey().isBlank()) {
            throw new IllegalArgumentException("通知业务键不能为空");
        }
        // 自己对自己的互动可能没有接收者，此时无需投递通知
        if (event.getRecipients() == null || event.getRecipients().isEmpty()) {
            return;
        }
        // 未提供事件 ID 时在首次写入前生成，后续发送使用已保存的 ID
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }

        // 按秒保存原始事件时间，并序列化事件内容
        event.setOccurredAt(event.getOccurredAt().truncatedTo(ChronoUnit.SECONDS));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("无法序列化通知事件", ex);
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.SECONDS);

        // 保留首次事件和已发送记录，避免取消后重复操作再次产生通知
        jdbcTemplate.update("INSERT INTO t_notification_outbox (producer_service,business_key,event_id,payload,state,next_attempt_at,attempts,lease_version,created_at) "
                        + "VALUES (?,?,?,?,'PENDING',?,0,0,?) ON DUPLICATE KEY UPDATE business_key=business_key",
                producerService, event.getBusinessKey(), event.getEventId(), payload, now, now);
    }
}
