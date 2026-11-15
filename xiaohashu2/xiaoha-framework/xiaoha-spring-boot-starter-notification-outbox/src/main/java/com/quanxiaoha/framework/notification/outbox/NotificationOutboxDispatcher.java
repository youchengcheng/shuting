package com.quanxiaoha.framework.notification.outbox;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import com.quanxiaoha.framework.notification.outbox.model.dto.NotificationOutboxClaimDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 定时读取 Outbox 记录并投递通知消息
 */
@Slf4j
public class NotificationOutboxDispatcher {

    /**
     * 来源业务数据库操作模板
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 领取待发送事件的事务模板
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * RocketMQ 消息发送模板
     */
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 事件来源服务名称
     */
    private final String producerService;

    /**
     * 当前投递实例的租约标识
     */
    private final String leaseOwner = UUID.randomUUID().toString();

    /*
     * 初始化通知投递组件
     * */
    public NotificationOutboxDispatcher(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                                       RocketMQTemplate rocketMQTemplate, String producerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.producerService = producerService;
    }

    /*
     * 扫描待发送事件，失败时延迟重试
     * */
    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        try {
            LocalDateTime now = now();
            // 查询待发送记录以及发送租约已过期的记录
            List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM t_notification_outbox WHERE producer_service=? AND "
                    + "((state='PENDING' AND next_attempt_at<=?) OR (state='SENDING' AND lease_until<=?)) ORDER BY id LIMIT 50",
                    Long.class, producerService, now, now);
            for (Long id : ids) {
                // 先在事务内领取租约，已经被其他实例领取的记录直接跳过
                NotificationOutboxClaimDTO notificationOutboxClaimDTO = claim(id);
                if (notificationOutboxClaimDTO == null) {
                    continue;
                }
                try {
                    // 领取事件的事务已提交，再执行 MQ 发送
                    SendResult result = rocketMQTemplate.syncSend("NotificationEventTopic",
                            MessageBuilder.withPayload(notificationOutboxClaimDTO.getPayload()).build(), 3000);
                    if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                        throw new IllegalStateException("消息服务器未确认通知消息已持久化");
                    }

                    // 仅当前租约持有者可以将记录更新为已发送
                    jdbcTemplate.update("UPDATE t_notification_outbox SET state='SENT',sent_at=?,lease_owner=NULL,lease_until=NULL,last_error=NULL "
                                    + "WHERE id=? AND producer_service=? AND state='SENDING' AND lease_owner=? AND lease_version=?",
                            now(), id, producerService, leaseOwner, notificationOutboxClaimDTO.getVersion());
                } catch (Exception ex) {
                    // 保存失败类型及原因，按字段长度截取后用于下次重试排查
                    String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                    if (error.length() > 1000) {
                        error = error.substring(0, 1000);
                    }
                    // 发送失败后释放租约，并按重试次数延迟下次发送
                    long delay = Math.min(300, 1L << Math.min(8, notificationOutboxClaimDTO.getAttempts()));
                    jdbcTemplate.update("UPDATE t_notification_outbox SET state='PENDING',next_attempt_at=?,lease_owner=NULL,lease_until=NULL,last_error=? "
                                    + "WHERE id=? AND producer_service=? AND state='SENDING' AND lease_owner=? AND lease_version=?",
                            now().plusSeconds(delay), error, id, producerService, leaseOwner, notificationOutboxClaimDTO.getVersion());
                    log.warn("Notification outbox send failed, id={}", id, ex);
                }
            }
        } catch (Exception ex) {
            log.error("Notification outbox scan failed for {}", producerService, ex);
        }
    }

    /*
     * 在事务中领取事件，更新租约和发送次数
     * */
    NotificationOutboxClaimDTO claim(Long id) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = now();
            // 只领取待发送或租约过期的记录，同时递增租约版本和发送次数
            int updated = jdbcTemplate.update("UPDATE t_notification_outbox SET state='SENDING',lease_owner=?,lease_version=lease_version+1,lease_until=?,attempts=attempts+1 "
                            + "WHERE id=? AND producer_service=? AND ((state='PENDING' AND next_attempt_at<=?) OR (state='SENDING' AND lease_until<=?))",
                    leaseOwner, now.plusSeconds(15), id, producerService, now, now);
            if (updated == 0) {
                return null;
            }
            // 返回当前持有者的消息和版本，后续状态更新须匹配同一租约
            return jdbcTemplate.queryForObject("SELECT payload,lease_version,attempts FROM t_notification_outbox WHERE id=? AND producer_service=? AND lease_owner=?",
                    (rs, row) -> new NotificationOutboxClaimDTO(rs.getString(1), rs.getLong(2), rs.getInt(3)), id, producerService, leaseOwner);
        });
    }

    /*
     * 获取上海时区的当前时间，精确到秒
     * */
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.SECONDS);
    }
}
