package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.mapper.NotificationMapper;
import com.quanxiaoha.xiaohashu.notification.biz.model.*;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class NotificationService {
    private final NotificationMapper mapper;
    private final NotificationSettings settings;
    private final NotificationChangePublisher changes;
    private final NotificationPreviewService previews;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public NotificationService(NotificationMapper mapper, NotificationSettings settings, NotificationChangePublisher changes,
                               NotificationPreviewService previews, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.settings = settings;
        this.changes = changes;
        this.previews = previews;
        this.write = new TransactionTemplate(transactions);
        this.read = new TransactionTemplate(transactions);
        this.read.setReadOnly(true);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public void consume(NotificationEvent event) {
        if (event == null || event.getOccurredAt() == null) throw new IllegalArgumentException("Missing interaction time");
        if (event.getOccurredAt().isBefore(settings.cutoff())) return;
        validateEvent(event);
        Map<Long, String> recipients = new TreeMap<>();
        for (NotificationRecipient recipient : event.getRecipients()) {
            if (!recipient.getUserId().equals(event.getActorId())) {
                recipients.merge(recipient.getUserId(), recipient.getRole(),
                        (oldRole, newRole) -> "REPLY_TARGET".equals(newRole) ? newRole : oldRole);
            }
        }
        // Each recipient commits independently. MQ retries finish remaining recipients without resetting existing rows.
        for (Map.Entry<Long, String> recipient : recipients.entrySet()) {
            NotificationUserState changed = write.execute(status -> {
                mapper.ensureState(recipient.getKey());
                NotificationUserState state = mapper.lockState(recipient.getKey());
                if (mapper.byBusinessKey(recipient.getKey(), event.getBusinessKey()) != null) return null;
                NotificationRecord record = new NotificationRecord();
                record.setBusinessKey(event.getBusinessKey());
                record.setRecipientId(recipient.getKey());
                record.setActorId(event.getActorId());
                record.setType(event.getType());
                record.setCategory(NotificationCategory.forType(event.getType()).name());
                record.setRecipientRole(recipient.getValue());
                record.setNoteId(event.getNoteId());
                record.setCommentId(event.getCommentId());
                record.setReplyCommentId(event.getReplyCommentId());
                record.setOccurredAt(event.getOccurredAt());
                record.setSeq(Math.incrementExact(state.getLastSeq()));
                mapper.insert(record);
                mapper.advance(recipient.getKey(), record.getSeq());
                state.setLastSeq(record.getSeq());
                state.setRevision(Math.incrementExact(state.getRevision()));
                return state;
            });
            if (changed != null) changes.publish(changed);
        }
    }

    public Page list(long userId, String categoryValue, String cursor, String snapshotValue, Integer pageSize) {
        NotificationCategory category = category(categoryValue);
        int size = pageSize == null ? 20 : pageSize;
        if (size < 1 || size > 50) throw badRequest("Invalid page size");
        if (cursor != null && snapshotValue == null) throw badRequest("Cursor requires its snapshot");
        SnapshotPage result = read.execute(status -> {
            NotificationUserState state = state(userId);
            long snapshot = snapshotValue == null ? state.getLastSeq() : decimal(snapshotValue, false);
            if (snapshot > state.getLastSeq()) throw badRequest("Snapshot exceeds current user sequence");
            long before = 0;
            if (cursor != null) {
                try { before = NotificationCursor.decode(cursor, userId, category, snapshot).beforeSeq(); }
                catch (IllegalArgumentException ex) { throw badRequest(ex.getMessage()); }
            }
            List<NotificationRecord> records = mapper.page(userId, category.name(), snapshot, before, size + 1);
            boolean more = records.size() > size;
            List<NotificationRecord> page = new ArrayList<>(records.subList(0, Math.min(size, records.size())));
            String next = more ? new NotificationCursor(userId, category, snapshot, page.get(page.size() - 1).getSeq()).encode() : null;
            return new SnapshotPage(page, state, snapshot, next, more);
        });
        return new Page(previews.render(userId, result.records, result.state), result.cursor,
                result.more, Long.toString(result.snapshot), Long.toString(result.state.getRevision()));
    }

    public Summary summary(long userId) {
        return read.execute(status -> summaryOf(state(userId)));
    }

    public ReadState readState(long userId, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw badRequest("Specify 1 to 100 notification IDs");
        List<Long> parsed = ids.stream().map(id -> decimal(id, true)).distinct().toList();
        return read.execute(status -> {
            NotificationUserState state = state(userId);
            List<ReadItem> items = mapper.byIds(userId, parsed).stream()
                    .map(row -> new ReadItem(Long.toString(row.getId()), state.isRead(row))).toList();
            return new ReadState(items, Long.toString(state.getRevision()), watermarks(state));
        });
    }

    public ReadResult markRead(long userId, String idValue) {
        long id = decimal(idValue, true);
        ReadResult result = write.execute(status -> {
            mapper.ensureState(userId);
            NotificationUserState state = mapper.lockState(userId);
            NotificationRecord record = mapper.byId(userId, id);
            if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
            boolean changed = !state.isRead(record);
            if (changed) {
                mapper.markRead(userId, id);
                mapper.bumpRevision(userId);
                state.setRevision(Math.incrementExact(state.getRevision()));
            }
            Summary summary = summaryOf(state);
            return new ReadResult(changed, summary.revision(), summary, Long.toString(id), true);
        });
        if (result.changed()) changes.publish(versionState(userId, result.summary()));
        return result;
    }

    public ReadAllResult markAllRead(long userId, String categoryValue, String snapshotValue) {
        NotificationCategory category = category(categoryValue);
        long snapshot = decimal(snapshotValue, false);
        ReadAllResult result = write.execute(status -> {
            mapper.ensureState(userId);
            NotificationUserState state = mapper.lockState(userId);
            if (snapshot > state.getLastSeq()) throw badRequest("Snapshot exceeds current user sequence");
            boolean changed = snapshot > state.watermark(category);
            if (changed) {
                mapper.setWatermark(userId, category.name(), snapshot);
                switch (category) {
                    case LIKE_COLLECTION -> state.setLikeCollectionReadSeq(snapshot);
                    case COMMENT_REPLY -> state.setCommentReplyReadSeq(snapshot);
                    case FOLLOW -> state.setFollowReadSeq(snapshot);
                }
                state.setRevision(Math.incrementExact(state.getRevision()));
            }
            Summary summary = summaryOf(state);
            return new ReadAllResult(changed, summary.revision(), summary, category.name(), Long.toString(snapshot));
        });
        if (result.changed()) changes.publish(versionState(userId, result.summary()));
        return result;
    }

    public void isolate(String source, String identity, String payload, String reason) {
        // Failure here propagates to MQ: an invalid event is acknowledged only after its durable isolation succeeds.
        mapper.isolate(source, identity, payload, reason == null ? "Invalid notification event" : reason.substring(0, Math.min(1000, reason.length())));
    }

    private NotificationUserState state(long userId) {
        NotificationUserState state = mapper.state(userId);
        return state == null ? NotificationUserState.empty(userId) : state;
    }

    private Summary summaryOf(NotificationUserState state) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (NotificationCategory category : NotificationCategory.values()) {
            long count = mapper.unread(state.getRecipientId(), category.name(), state.watermark(category));
            counts.put(category.name(), count);
            total = Math.addExact(total, count);
        }
        return new Summary(counts, total, Long.toString(state.getLastSeq()), Long.toString(state.getRevision()), watermarks(state));
    }

    private static Map<String, String> watermarks(NotificationUserState state) {
        Map<String, String> values = new LinkedHashMap<>();
        for (NotificationCategory category : NotificationCategory.values()) values.put(category.name(), Long.toString(state.watermark(category)));
        return values;
    }

    private static NotificationUserState versionState(long userId, Summary summary) {
        NotificationUserState state = NotificationUserState.empty(userId);
        state.setLastSeq(Long.parseLong(summary.lastSeq()));
        state.setRevision(Long.parseLong(summary.revision()));
        return state;
    }

    public static void validateEvent(NotificationEvent event) {
        if (event == null || event.getOccurredAt() == null || event.getActorId() == null || event.getActorId() <= 0
                || event.getRecipients() == null || event.getRecipients().isEmpty() || event.getRecipients().size() > 2) {
            throw new IllegalArgumentException("Invalid notification event identity");
        }
        NotificationCategory.forType(event.getType());
        if (!"REPLY".equals(event.getType()) && event.getRecipients().size() != 1) {
            throw new IllegalArgumentException("Invalid notification recipient count");
        }
        for (NotificationRecipient recipient : event.getRecipients()) {
            if (recipient == null || recipient.getUserId() == null || recipient.getUserId() <= 0
                    || recipient.getRole() == null || !Set.of("OWNER", "REPLY_TARGET", "FOLLOW_TARGET").contains(recipient.getRole())) {
                throw new IllegalArgumentException("Invalid notification recipient");
            }
            boolean validRole = "FOLLOW".equals(event.getType()) ? "FOLLOW_TARGET".equals(recipient.getRole())
                    : "OWNER".equals(recipient.getRole()) || ("REPLY".equals(event.getType()) && "REPLY_TARGET".equals(recipient.getRole()));
            if (!validRole) throw new IllegalArgumentException("Recipient role does not match notification type");
        }
        Long objectId;
        switch (event.getType()) {
            case "NOTE_LIKE", "NOTE_COLLECT" -> objectId = event.getNoteId();
            case "COMMENT_LIKE", "COMMENT", "REPLY" -> {
                if (event.getNoteId() == null || event.getNoteId() <= 0) throw new IllegalArgumentException("Missing note route");
                if ("REPLY".equals(event.getType()) && (event.getReplyCommentId() == null || event.getReplyCommentId() <= 0)) {
                    throw new IllegalArgumentException("Missing reply route");
                }
                objectId = event.getCommentId();
            }
            case "FOLLOW" -> {
                if (event.getRecipients().size() != 1) throw new IllegalArgumentException("Invalid follow recipient count");
                objectId = event.getRecipients().get(0).getUserId();
            }
            default -> throw new IllegalArgumentException("Unknown notification type");
        }
        if (!NotificationBusinessKey.of(event.getType(), event.getActorId(), objectId).equals(event.getBusinessKey())) {
            throw new IllegalArgumentException("Notification business key does not match its event");
        }
    }

    private static NotificationCategory category(String value) {
        try { return NotificationCategory.valueOf(value); }
        catch (RuntimeException ex) { throw badRequest("Invalid notification category"); }
    }

    public static long decimal(String value, boolean positive) {
        try {
            if (value == null || !value.matches("[0-9]{1,19}")) throw new IllegalArgumentException();
            long parsed = Long.parseLong(value);
            if (positive && parsed == 0) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException ex) { throw badRequest("Invalid decimal identifier or sequence"); }
    }

    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record SnapshotPage(List<NotificationRecord> records, NotificationUserState state, long snapshot, String cursor, boolean more) { }
}
