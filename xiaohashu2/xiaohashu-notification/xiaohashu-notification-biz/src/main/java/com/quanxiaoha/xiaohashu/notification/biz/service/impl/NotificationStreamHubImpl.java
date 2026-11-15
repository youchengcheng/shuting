package com.quanxiaoha.xiaohashu.notification.biz.service.impl;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.mapper.NotificationDOMapper;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.NotificationVersionDTO;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationStreamHub;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationTokenValidator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/*
 * 管理通知 SSE 连接并推送状态版本
 * */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
public class NotificationStreamHubImpl implements NotificationStreamHub {

    /**
     * 单次连接最长存活时间
     */
    private static final long STREAM_LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(5);

    /**
     * 通知状态查询 Mapper
     */
    @Resource
    private NotificationDOMapper notificationDOMapper;

    /**
     * 登录凭证校验组件
     */
    @Resource
    private NotificationTokenValidator notificationTokenValidator;

    // 通知启用和连接数量配置
    @Resource
    private NotificationSettings notificationSettings;

    /**
     * 每个用户允许建立的最大连接数
     */
    private int perUserLimit;

    /**
     * 当前实例可用的连接名额
     */
    private Semaphore capacity;

    /**
     * 按用户保存当前实例的连接
     */
    private final ConcurrentHashMap<Long, Map<String, Connection>> connections = new ConcurrentHashMap<>();

    /**
     * SSE 消息发送线程池
     */
    private ThreadPoolExecutor sender;

    /**
     * SSE 连接关闭线程池，避免关闭操作阻塞定时任务和 Redis 监听线程
     */
    private ThreadPoolExecutor closer;

    /*
     * 注入完成后初始化连接名额和发送、关闭线程池
     * */
    @PostConstruct
    public void initialize() {
        // 读取每用户和每实例的连接数量限制
        perUserLimit = notificationSettings.getStream().getMaxPerUser();
        capacity = new Semaphore(notificationSettings.getStream().getMaxPerInstance());

        // 使用有界队列异步发送，发送积压时拒绝任务并关闭连接
        sender = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256), runnable -> {
                    Thread thread = new Thread(runnable, "notification-sse-send");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());

        // 关闭操作单独执行，避免阻塞发送线程、定时任务和 Redis 监听
        closer = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256), runnable -> {
                    Thread thread = new Thread(runnable, "notification-sse-close");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /*
     * 建立用户通知连接并发送当前状态版本
     * */
    @Override
    public SseEmitter connect(long userId, String token) {
        // 先领取当前实例的连接名额
        if (!capacity.tryAcquire()) {
            throw new BizException(ResponseCodeEnum.STREAM_CAPACITY_REACHED);
        }
        // 创建连接状态，后续注册失败时退还领取的名额
        Connection connection = new Connection(userId, token, createEmitter());
        try {
            connections.compute(userId, (id, existing) -> {
                Map<String, Connection> value = existing == null ? new ConcurrentHashMap<>() : existing;
                // 同一用户达到上限时拒绝登记新连接
                if (value.size() >= perUserLimit) {
                    throw new BizException(ResponseCodeEnum.STREAM_LIMIT_REACHED);
                }
                // 将连接按用户及连接 ID 保存到当前实例
                value.put(connection.id, connection);
                return value;
            });
        } catch (RuntimeException ex) {
            // 注册失败，连接尚未登记，直接退还实例名额
            capacity.release();
            throw ex;
        }
        // 连接结束、超时或异常时释放连接名额
        connection.emitter.onCompletion(() -> close(connection));
        connection.emitter.onTimeout(() -> close(connection));
        connection.emitter.onError(error -> close(connection));
        try {
            // 查询当前通知版本，首次连接向客户端发送 ready
            NotificationUserStateDO state = notificationDOMapper.state(userId);
            if (state == null) {
                state = NotificationUserStateDO.empty(userId);
            }
            send(connection, "ready", version(state), false);
            return connection.emitter;
        } catch (RuntimeException ex) {
            close(connection);
            throw new BizException(ResponseCodeEnum.NOTIFICATION_STATE_UNAVAILABLE);
        }
    }

    /*
     * 收到状态变更提示后，通知当前实例上的用户连接
     * */
    @Override
    public void changed(long userId, long revision, long lastSeq) {
        // 只推送给当前实例登记的该用户连接
        Map<String, Connection> local = connections.get(userId);
        if (local == null) {
            return;
        }
        for (Connection connection : local.values()) {
            // 原子保存最大版本，忽略重复或乱序的变更提示
            long previous = connection.revision.getAndAccumulate(revision, Math::max);
            if (revision > previous) {
                send(connection, "changed", new NotificationVersionDTO(Long.toString(revision), Long.toString(lastSeq)), false);
            }
        }
    }

    /*
     * 定时校验登录状态，并通过心跳补齐遗漏的状态变更
     * */
    @Scheduled(fixedDelay = 15000)
    @Override
    public void heartbeat() {
        // 取出当前实例连接快照，避免遍历过程中注册和关闭影响校验
        List<Connection> active = connections.values().stream().flatMap(group -> group.values().stream()).toList();
        if (active.isEmpty()) {
            return;
        }
        try {
            // 批量查询登录身份和用户通知状态
            Map<String, Long> identities = notificationTokenValidator.identities(active.stream().map(connection -> connection.token).toList());
            Map<Long, NotificationUserStateDO> states = new HashMap<>();
            List<Long> userIds = active.stream().map(connection -> connection.userId).distinct().toList();
            // 每批最多查询 100 个用户的数据库通知状态
            for (int start = 0; start < userIds.size(); start += 100) {
                notificationDOMapper.states(userIds.subList(start, Math.min(start + 100, userIds.size())))
                        .forEach(state -> states.put(state.getRecipientId(), state));
            }
            for (Connection connection : active) {
                // 登录过期时发送最后一条提示，并在发送后关闭连接
                if (!Objects.equals(identities.get(connection.token), connection.userId)) {
                    send(connection, "auth-expired", Map.of("reason", "login-expired"), true);
                    continue;
                }
                // 数据库状态比连接已知版本更新时发送 changed，否则发送心跳
                NotificationUserStateDO state = states.getOrDefault(connection.userId, NotificationUserStateDO.empty(connection.userId));
                long previous = connection.revision.getAndAccumulate(state.getRevision(), Math::max);
                send(connection, state.getRevision() > previous ? "changed" : "heartbeat", version(state), false);
            }
        } catch (RuntimeException ex) {
            log.warn("Notification stream dependency unavailable, active={}", active.size());
            active.forEach(connection -> send(connection, "service-unavailable", Map.of("reason", "dependency-unavailable"), true));
        }
    }

    /*
     * 关闭发送积压或存活时间已到上限的连接
     * */
    @Scheduled(fixedDelay = 5000)
    @Override
    public void closeSlowClients() {
        // 用单调时钟计算积压和连接存活时间
        long now = System.nanoTime();
        connections.values().forEach(group -> group.values().forEach(connection -> {
            // 已积压超过 10 秒或连接存活超过 5 分钟时清理
            long queued = connection.queuedSince.get();
            if ((queued > 0 && now - queued > TimeUnit.SECONDS.toNanos(10))
                    || now - connection.createdAt > TimeUnit.MILLISECONDS.toNanos(STREAM_LIFETIME_MILLIS)) {
                close(connection);
            }
        }));
    }

    /*
     * 将消息放入发送队列，必要时在发送后关闭连接
     * */
    private void send(Connection connection, String event, Object data, boolean closeAfter) {
        // 已关闭的连接不再接受发送任务
        if (connection.closed.get()) {
            return;
        }
        // 最后一条消息只能登记一次，登记后不再接受普通消息
        if (closeAfter) {
            if (!connection.ending.compareAndSet(false, true)) {
                return;
            }
        } else if (connection.ending.get()) {
            return;
        }
        // 单个连接发送积压超过上限时直接关闭
        if (connection.pending.incrementAndGet() > 8) {
            connection.pending.decrementAndGet();
            close(connection);
            return;
        }
        // 记录本轮队列首次积压时间，后续任务沿用同一起点
        connection.queuedSince.compareAndSet(0, System.nanoTime());
        try {
            // 由发送线程执行可能阻塞的 SSE 写入
            sender.execute(() -> {
                try {
                    if (!connection.closed.get() && (!connection.ending.get() || closeAfter)) {
                        connection.emitter.send(SseEmitter.event().name(event).data(data));
                    }
                } catch (IOException | RuntimeException ex) {
                    close(connection);
                } finally {
                    // 发送任务全部完成后清除本轮积压时间
                    if (connection.pending.decrementAndGet() == 0) {
                        connection.queuedSince.set(0);
                    }
                    // 最后一条消息发送完成后清理连接
                    if (closeAfter) {
                        close(connection);
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            connection.pending.decrementAndGet();
            close(connection);
        }
    }

    /*
     * 清理连接登记和名额，并异步完成连接
     * */
    private void close(Connection connection) {
        // 原子关闭标记保证登记和名额只清理一次
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        // 移除当前连接，用户已经没有连接时同时移除分组
        connections.computeIfPresent(connection.userId, (id, group) -> {
            group.remove(connection.id);
            return group.isEmpty() ? null : group;
        });
        // 先退还实例名额，再异步完成响应，避免慢客户端占住名额
        capacity.release();
        try {
            closer.execute(() -> {
                try {
                    connection.emitter.complete();
                } catch (RuntimeException ignored) {
                    // 连接结束过程中出现异常，无须再次清理登记和名额
                }
            });
        } catch (RejectedExecutionException ex) {
            // 登记和名额已清理，关闭队列已满时等待 Servlet 异步请求超时
            // 避免在当前线程关闭连接，或提交给可能阻塞的发送线程池
            log.debug("Notification stream completion queue full, userId={}", connection.userId);
        }
    }

    /*
     * 服务关闭时清理连接并停止线程池
     * */
    @PreDestroy
    @Override
    public void shutdown() {
        // 先清理所有连接，再停止发送线程池
        connections.values().stream().flatMap(group -> group.values().stream()).toList().forEach(this::close);
        sender.shutdownNow();
        // 关闭线程池继续处理已经提交的连接完成任务
        closer.shutdown();
    }

    /*
     * 创建具有超时时间的 SSE 连接
     * */
    protected SseEmitter createEmitter() {
        return new SseEmitter(STREAM_LIFETIME_MILLIS);
    }

    /*
     * 转换用户通知状态版本
     * */
    private static NotificationVersionDTO version(NotificationUserStateDO state) {
        return new NotificationVersionDTO(Long.toString(state.getRevision()), Long.toString(state.getLastSeq()));
    }

    /**
     * 当前实例保存的 SSE 连接状态
     */
    private static final class Connection {

        /**
         * 当前连接标识
         */
        private final String id = UUID.randomUUID().toString();

        /**
         * 连接所属用户 ID
         */
        private final long userId;

        /**
         * 建立连接时的登录凭证
         */
        private final String token;

        /**
         * SSE 响应连接
         */
        private final SseEmitter emitter;

        /**
         * 连接建立时间，使用单调时钟计算存活时长
         */
        private final long createdAt = System.nanoTime();

        /**
         * 已处理的通知状态版本
         */
        private final AtomicLong revision = new AtomicLong(-1);

        /**
         * 连接是否已清理
         */
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 是否正在发送最后一条消息
         */
        private final AtomicBoolean ending = new AtomicBoolean();

        /**
         * 尚未完成的发送任务数
         */
        private final AtomicInteger pending = new AtomicInteger();

        /**
         * 发送队列开始积压的时间
         */
        private final AtomicLong queuedSince = new AtomicLong();

        /*
         * 保存连接所属用户、登录凭证和 SSE 响应
         * */
        private Connection(long userId, String token, SseEmitter emitter) {
            this.userId = userId;
            this.token = token;
            this.emitter = emitter;
        }
    }
}
