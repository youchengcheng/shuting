package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationTomcatConfiguration;
import com.quanxiaoha.xiaohashu.notification.biz.mapper.NotificationMapper;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.Version;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationUserState;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationStreamHub;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationTokenValidator;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SocketBufferHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationStreamHubTest {
    private final List<NotificationStreamHub> hubs = new ArrayList<>();

    @AfterEach
    void shutDownExecutors() {
        hubs.forEach(NotificationStreamHub::shutdown);
    }

    @Test
    void closingAnEmitterWhoseSendOwnsItsMonitorReturnsAndReleasesCapacity() throws Exception {
        BlockingEmitter slow = new BlockingEmitter();
        RecordingEmitter healthy = new RecordingEmitter();
        NotificationStreamHub hub = hub(1, slow, healthy);
        hub.connect(1, "first");
        assertThat(slow.started.await(2, TimeUnit.SECONDS)).isTrue();
        expireQueue(hub, 1);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1), hub::closeSlowClients);
            assertThat(connections(hub)).isEmpty();
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> hub.connect(2, "second"));
            eventually(() -> assertThat(healthy.events).contains("ready"));
            assertThat(slow.completed.getCount()).isEqualTo(1);
        } finally {
            slow.release.countDown();
        }
        assertThat(slow.completed.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void aSaturatedCompletionExecutorNeverRunsCompletionOnTheCallingThread() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        NotificationStreamHub hub = hub(1, emitter, new RecordingEmitter());
        hub.connect(1, "first");
        eventually(() -> assertThat(emitter.events).contains("ready"));
        Object connection = connections(hub).get(1L).values().iterator().next();
        eventually(() -> assertThat(((AtomicInteger) ReflectionTestUtils.getField(connection, "pending")).get()).isZero());
        ThreadPoolExecutor closer = (ThreadPoolExecutor) ReflectionTestUtils.getField(hub, "closer");
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(closer.getMaximumPoolSize());
        Runnable block = () -> {
            running.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        try {
            for (int i = 0; i < closer.getMaximumPoolSize(); i++) closer.execute(block);
            assertThat(running.await(2, TimeUnit.SECONDS)).isTrue();
            while (closer.getQueue().remainingCapacity() > 0) closer.execute(block);
            expireQueue(hub, 1);
            assertTimeoutPreemptively(Duration.ofSeconds(1), hub::closeSlowClients);
            assertThat(connections(hub)).isEmpty();
            assertThat(emitter.completed.getCount()).isEqualTo(1);
            assertThat(closer.getPoolSize()).isLessThanOrEqualTo(2);
            assertThat(closer.getQueue().size()).isLessThanOrEqualTo(256);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> hub.connect(2, "second"));
            assertThat(emitter.getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(5));
        } finally {
            release.countDown();
        }
    }

    @Test
    void finiteLifetimeRemovesAnOtherwiseHealthyConnection() {
        NotificationStreamHub hub = new NotificationStreamHub(mock(NotificationMapper.class),
                mock(NotificationTokenValidator.class), new NotificationSettings());
        hubs.add(hub);
        SseEmitter emitter = hub.connect(1, "first");
        Object connection = connections(hub).get(1L).values().iterator().next();
        ReflectionTestUtils.setField(connection, "createdAt", System.nanoTime() - TimeUnit.MINUTES.toNanos(6));
        assertTimeoutPreemptively(Duration.ofSeconds(1), hub::closeSlowClients);
        assertThat(connections(hub)).isEmpty();
        assertThat(emitter.getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(5));
    }

    @Test
    void anInstanceThatMissesTheChangeHintRecoversFromAuthoritativeHeartbeat() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationTokenValidator tokens = mock(NotificationTokenValidator.class);
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        NotificationStreamHub firstHub = hub(mapper, tokens, 2, first);
        NotificationStreamHub secondHub = hub(mapper, tokens, 2, second);
        firstHub.connect(1, "token");
        secondHub.connect(1, "token");
        eventually(() -> {
            assertThat(first.events).contains("ready");
            assertThat(second.events).contains("ready");
        });
        NotificationUserState authoritative = NotificationUserState.empty(1L);
        authoritative.setRevision(7);
        authoritative.setLastSeq(4);
        when(tokens.identities(any())).thenReturn(Map.of("token", 1L));
        when(mapper.states(any())).thenReturn(List.of(authoritative));
        firstHub.changed(1, 7, 4);
        secondHub.heartbeat();
        eventually(() -> {
            assertThat(first.versions).contains(new Version("7", "4"));
            assertThat(second.events).contains("changed");
            assertThat(second.versions).contains(new Version("7", "4"));
        });
    }

    @Test
    void tomcatNioWriteThatMakesNoProgressTimesOutUsingTheConfiguredLimit() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new NotificationTomcatConfiguration().notificationSocketTimeout().customize(factory);
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        factory.getTomcatConnectorCustomizers().forEach(customizer -> customizer.customize(connector));
        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        assertThat(protocol.getConnectionTimeout()).isEqualTo(10000);

        // Exercise Tomcat 10.1.5's actual blocking doWrite() with a channel that never accepts bytes.
        // NioEndpoint.setSocketOptions() initializes writeTimeout from this same connectionTimeout.
        NioChannel channel = mock(NioChannel.class);
        when(channel.getBufHandler()).thenReturn(new SocketBufferHandler(8, 8, false));
        TimedSocket socket = new TimedSocket(channel);
        socket.setWriteTimeout(protocol.getConnectionTimeout());
        assertTimeoutPreemptively(Duration.ofSeconds(13), () ->
                assertThatThrownBy(() -> socket.writeBlocked(ByteBuffer.wrap(new byte[]{1})))
                        .isInstanceOf(SocketTimeoutException.class));
    }

    @Test
    void anUnsupportedConnectorCannotSilentlyDisableTheWriteBound() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new NotificationTomcatConfiguration().notificationSocketTimeout().customize(factory);
        Connector connector = new Connector("org.apache.coyote.http11.Http11Nio2Protocol");
        assertThatThrownBy(() -> factory.getTomcatConnectorCustomizers()
                .forEach(customizer -> customizer.customize(connector)))
                .isInstanceOf(IllegalStateException.class);
    }

    private NotificationStreamHub hub(int capacity, RecordingEmitter... emitters) {
        return hub(mock(NotificationMapper.class), mock(NotificationTokenValidator.class), capacity, emitters);
    }

    private NotificationStreamHub hub(NotificationMapper mapper, NotificationTokenValidator tokens, int capacity,
                                      RecordingEmitter... emitters) {
        NotificationSettings settings = new NotificationSettings();
        settings.getStream().setMaxPerInstance(capacity);
        Deque<RecordingEmitter> available = new ArrayDeque<>(List.of(emitters));
        NotificationStreamHub hub = new NotificationStreamHub(mapper, tokens, settings) {
            @Override protected SseEmitter createEmitter() { return available.removeFirst(); }
        };
        hubs.add(hub);
        return hub;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, Object>> connections(NotificationStreamHub hub) {
        return (Map<Long, Map<String, Object>>) ReflectionTestUtils.getField(hub, "connections");
    }

    private void expireQueue(NotificationStreamHub hub, long userId) {
        Object connection = connections(hub).get(userId).values().iterator().next();
        ((AtomicLong) ReflectionTestUtils.getField(connection, "queuedSince"))
                .set(System.nanoTime() - TimeUnit.SECONDS.toNanos(11));
    }

    private static void eventually(Runnable assertion) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadline) {
            try { assertion.run(); return; }
            catch (AssertionError failure) { lastFailure = failure; }
            try { Thread.sleep(10); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for asynchronous SSE result", ex);
            }
        }
        throw new AssertionError("Asynchronous SSE result did not arrive within 2 seconds", lastFailure);
    }

    private static class RecordingEmitter extends SseEmitter {
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<Version> versions = new CopyOnWriteArrayList<>();
        final CountDownLatch completed = new CountDownLatch(1);
        RecordingEmitter() { super(TimeUnit.MINUTES.toMillis(5)); }

        @Override public synchronized void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(value -> {
                if (value.getData() instanceof Version version) versions.add(version);
                if (value.getData() instanceof String text && text.startsWith("event:"))
                    events.add(text.substring(6).split("\n", 2)[0]);
            });
        }
        @Override public synchronized void complete() { completed.countDown(); }
    }

    private static class BlockingEmitter extends RecordingEmitter {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        @Override public synchronized void send(SseEventBuilder builder) throws IOException {
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            super.send(builder);
        }
    }

    private static class TimedSocket extends NioEndpoint.NioSocketWrapper {
        TimedSocket(NioChannel channel) { super(channel, new NioEndpoint()); }
        @Override public void registerWriteInterest() { /* No writable signal: simulate a stalled peer. */ }
        void writeBlocked(ByteBuffer buffer) throws IOException { doWrite(true, buffer); }
    }
}
