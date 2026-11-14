package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.mapper.NotificationMapper;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.Version;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationUserState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
public class NotificationStreamHub {
    private static final long STREAM_LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private final NotificationMapper mapper;
    private final NotificationTokenValidator tokens;
    private final int perUserLimit;
    private final Semaphore capacity;
    private final ConcurrentHashMap<Long, Map<String, Connection>> connections = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> { Thread thread = new Thread(runnable, "notification-sse-send"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    // complete() shares the emitter lock with blocking send/flush. Never run it on a scheduler or Redis listener.
    private final ThreadPoolExecutor closer = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> { Thread thread = new Thread(runnable, "notification-sse-close"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());

    public NotificationStreamHub(NotificationMapper mapper, NotificationTokenValidator tokens, NotificationSettings settings) {
        this.mapper = mapper;
        this.tokens = tokens;
        this.perUserLimit = settings.getStream().getMaxPerUser();
        this.capacity = new Semaphore(settings.getStream().getMaxPerInstance());
    }

    public SseEmitter connect(long userId, String token) {
        if (!capacity.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notification stream capacity reached");
        Connection connection = new Connection(userId, token, createEmitter());
        try {
            connections.compute(userId, (id, existing) -> {
                Map<String, Connection> value = existing == null ? new ConcurrentHashMap<>() : existing;
                if (value.size() >= perUserLimit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many notification streams");
                value.put(connection.id, connection);
                return value;
            });
        } catch (RuntimeException ex) { capacity.release(); throw ex; }
        connection.emitter.onCompletion(() -> close(connection));
        connection.emitter.onTimeout(() -> close(connection));
        connection.emitter.onError(error -> close(connection));
        try {
            NotificationUserState state = mapper.state(userId);
            if (state == null) state = NotificationUserState.empty(userId);
            send(connection, "ready", version(state), false);
            return connection.emitter;
        } catch (RuntimeException ex) {
            close(connection);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notification state temporarily unavailable");
        }
    }

    public void changed(long userId, long revision, long lastSeq) {
        Map<String, Connection> local = connections.get(userId);
        if (local == null) return;
        for (Connection connection : local.values()) {
            long previous = connection.revision.getAndAccumulate(revision, Math::max);
            if (revision > previous) send(connection, "changed", new Version(Long.toString(revision), Long.toString(lastSeq)), false);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        List<Connection> active = connections.values().stream().flatMap(group -> group.values().stream()).toList();
        if (active.isEmpty()) return;
        try {
            Map<String, Long> identities = tokens.identities(active.stream().map(connection -> connection.token).toList());
            Map<Long, NotificationUserState> states = new HashMap<>();
            List<Long> userIds = active.stream().map(connection -> connection.userId).distinct().toList();
            for (int start = 0; start < userIds.size(); start += 100) {
                mapper.states(userIds.subList(start, Math.min(start + 100, userIds.size())))
                        .forEach(state -> states.put(state.getRecipientId(), state));
            }
            for (Connection connection : active) {
                if (!Objects.equals(identities.get(connection.token), connection.userId)) {
                    send(connection, "auth-expired", Map.of("reason", "login-expired"), true);
                    continue;
                }
                NotificationUserState state = states.getOrDefault(connection.userId, NotificationUserState.empty(connection.userId));
                long previous = connection.revision.getAndAccumulate(state.getRevision(), Math::max);
                send(connection, state.getRevision() > previous ? "changed" : "heartbeat", version(state), false);
            }
        } catch (RuntimeException ex) {
            log.warn("Notification stream dependency unavailable, active={}", active.size());
            active.forEach(connection -> send(connection, "service-unavailable", Map.of("reason", "dependency-unavailable"), true));
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void closeSlowClients() {
        long now = System.nanoTime();
        connections.values().forEach(group -> group.values().forEach(connection -> {
            long queued = connection.queuedSince.get();
            if ((queued > 0 && now - queued > TimeUnit.SECONDS.toNanos(10))
                    || now - connection.createdAt > TimeUnit.MILLISECONDS.toNanos(STREAM_LIFETIME_MILLIS)) close(connection);
        }));
    }

    private void send(Connection connection, String event, Object data, boolean closeAfter) {
        if (connection.closed.get()) return;
        if (closeAfter) {
            if (!connection.ending.compareAndSet(false, true)) return;
        } else if (connection.ending.get()) return;
        if (connection.pending.incrementAndGet() > 8) { connection.pending.decrementAndGet(); close(connection); return; }
        connection.queuedSince.compareAndSet(0, System.nanoTime());
        try {
            sender.execute(() -> {
                try {
                    if (!connection.closed.get() && (!connection.ending.get() || closeAfter)) {
                        connection.emitter.send(SseEmitter.event().name(event).data(data));
                    }
                } catch (IOException | RuntimeException ex) { close(connection); }
                finally {
                    if (connection.pending.decrementAndGet() == 0) connection.queuedSince.set(0);
                    if (closeAfter) close(connection);
                }
            });
        } catch (RejectedExecutionException ex) {
            connection.pending.decrementAndGet();
            close(connection);
        }
    }

    private void close(Connection connection) {
        if (!connection.closed.compareAndSet(false, true)) return;
        connections.computeIfPresent(connection.userId, (id, group) -> {
            group.remove(connection.id);
            return group.isEmpty() ? null : group;
        });
        capacity.release();
        try {
            closer.execute(() -> {
                try { connection.emitter.complete(); } catch (RuntimeException ignored) { }
            });
        } catch (RejectedExecutionException ex) {
            // Registry/capacity cleanup has already happened. The finite servlet async timeout is the fallback.
            // In particular, do not use CallerRunsPolicy or submit to the potentially blocked sender pool.
            log.debug("Notification stream completion queue full, userId={}", connection.userId);
        }
    }

    @PreDestroy
    public void shutdown() {
        connections.values().stream().flatMap(group -> group.values().stream()).toList().forEach(this::close);
        sender.shutdownNow();
        closer.shutdown();
    }

    protected SseEmitter createEmitter() { return new SseEmitter(STREAM_LIFETIME_MILLIS); }

    private static Version version(NotificationUserState state) { return new Version(Long.toString(state.getRevision()), Long.toString(state.getLastSeq())); }
    private static final class Connection {
        private final String id = UUID.randomUUID().toString();
        private final long userId;
        private final String token;
        private final SseEmitter emitter;
        private final long createdAt = System.nanoTime();
        private final AtomicLong revision = new AtomicLong(-1);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean ending = new AtomicBoolean();
        private final AtomicInteger pending = new AtomicInteger();
        private final AtomicLong queuedSince = new AtomicLong();
        private Connection(long userId, String token, SseEmitter emitter) { this.userId = userId; this.token = token; this.emitter = emitter; }
    }
}
