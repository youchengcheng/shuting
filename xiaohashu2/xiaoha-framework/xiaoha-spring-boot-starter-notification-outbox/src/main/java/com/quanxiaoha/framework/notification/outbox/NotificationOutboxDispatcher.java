package com.quanxiaoha.framework.notification.outbox;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public class NotificationOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RocketMQTemplate rocketMQ;
    private final String producerService;
    private final String owner = UUID.randomUUID().toString();

    public NotificationOutboxDispatcher(JdbcTemplate jdbc, TransactionTemplate transaction,
                                       RocketMQTemplate rocketMQ, String producerService) {
        this.jdbc = jdbc; this.transaction = transaction; this.rocketMQ = rocketMQ; this.producerService = producerService;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        try {
            LocalDateTime now = now();
            List<Long> ids = jdbc.queryForList("SELECT id FROM t_notification_outbox WHERE producer_service=? AND "
                    + "((state='PENDING' AND next_attempt_at<=?) OR (state='SENDING' AND lease_until<=?)) ORDER BY id LIMIT 50",
                    Long.class, producerService, now, now);
            for (Long id : ids) {
                Claimed claimed = claim(id);
                if (claimed == null) continue;
                try {
                    // The claim transaction has completed before the network call.
                    SendResult result = rocketMQ.syncSend("NotificationEventTopic",
                            MessageBuilder.withPayload(claimed.payload()).build(), 3000);
                    if (result == null || result.getSendStatus() != SendStatus.SEND_OK)
                        throw new IllegalStateException("Broker did not confirm notification persistence");
                    jdbc.update("UPDATE t_notification_outbox SET state='SENT',sent_at=?,lease_owner=NULL,lease_until=NULL,last_error=NULL "
                                    + "WHERE id=? AND producer_service=? AND state='SENDING' AND lease_owner=? AND lease_version=?",
                            now(), id, producerService, owner, claimed.version());
                } catch (Exception ex) {
                    String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                    if (error.length() > 1000) error = error.substring(0, 1000);
                    long delay = Math.min(300, 1L << Math.min(8, claimed.attempts()));
                    jdbc.update("UPDATE t_notification_outbox SET state='PENDING',next_attempt_at=?,lease_owner=NULL,lease_until=NULL,last_error=? "
                                    + "WHERE id=? AND producer_service=? AND state='SENDING' AND lease_owner=? AND lease_version=?",
                            now().plusSeconds(delay), error, id, producerService, owner, claimed.version());
                    log.warn("Notification outbox send failed, id={}", id, ex);
                }
            }
        } catch (Exception ex) { log.error("Notification outbox scan failed for {}", producerService, ex); }
    }

    Claimed claim(Long id) {
        return transaction.execute(status -> {
            LocalDateTime now = now();
            int updated = jdbc.update("UPDATE t_notification_outbox SET state='SENDING',lease_owner=?,lease_version=lease_version+1,lease_until=?,attempts=attempts+1 "
                            + "WHERE id=? AND producer_service=? AND ((state='PENDING' AND next_attempt_at<=?) OR (state='SENDING' AND lease_until<=?))",
                    owner, now.plusSeconds(15), id, producerService, now, now);
            if (updated == 0) return null;
            return jdbc.queryForObject("SELECT payload,lease_version,attempts FROM t_notification_outbox WHERE id=? AND producer_service=? AND lease_owner=?",
                    (rs, row) -> new Claimed(rs.getString(1), rs.getLong(2), rs.getInt(3)), id, producerService, owner);
        });
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.SECONDS); }
    record Claimed(String payload, long version, int attempts) { }
}
