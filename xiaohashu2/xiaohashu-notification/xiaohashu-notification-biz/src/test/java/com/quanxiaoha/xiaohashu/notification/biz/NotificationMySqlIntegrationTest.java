package com.quanxiaoha.xiaohashu.notification.biz;

import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.mapper.NotificationMapper;
import com.quanxiaoha.xiaohashu.notification.biz.model.*;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.*;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in only: the JDBC URL and server datadir must both prove a separate temporary instance. */
@EnabledIfSystemProperty(named = "notification.mysql.test.enabled", matches = "true")
class NotificationMySqlIntegrationTest {
    private static final AtomicLong USERS = new AtomicLong(ThreadLocalRandom.current().nextLong(40000, 1000000000));
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static NotificationMapper mapper;
    private NotificationChangePublisher changes;
    private NotificationPreviewService previews;
    private NotificationService service;
    private long user;

    @BeforeAll
    static void initializeOnlyVerifiedTemporaryDatabase() throws Exception {
        String url = System.getProperty("notification.mysql.test.url", "");
        String expectedDir = System.getProperty("notification.mysql.test.datadir", "").replace('\\', '/').replaceAll("/+$", "");
        assertThat(url).matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{4,5}/notification_review_test_[a-f0-9]{32}\\?.*");
        int port = Integer.parseInt(url.substring("jdbc:mysql://127.0.0.1:".length(), url.indexOf('/', "jdbc:mysql://127.0.0.1:".length())));
        assertThat(port).isNotIn(3306, 3307);
        assertThat(expectedDir).contains("/xiaohashu-notification/xiaohashu-notification-biz/target/mysql-review-");
        dataSource = new DriverManagerDataSource(url, "root", "");
        jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> server = jdbc.queryForMap("SELECT @@datadir AS data_dir, @@port AS server_port");
        assertThat(server.get("data_dir").toString().replace('\\', '/').replaceAll("/+$", "")).isEqualToIgnoringCase(expectedDir);
        assertThat(((Number) server.get("server_port")).intValue()).isEqualTo(port);
        new ResourceDatabasePopulator(new ClassPathResource("db/notification_module.sql")).execute(dataSource);
        // Real generated IDs exceed JavaScript's precise integer range throughout this suite.
        jdbc.execute("ALTER TABLE t_notification AUTO_INCREMENT = 9223372036854774000");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NotificationMapper.class);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(NotificationMapper.class);
    }

    @BeforeEach
    void setUp() {
        user = USERS.incrementAndGet();
        changes = mock(NotificationChangePublisher.class);
        previews = mock(NotificationPreviewService.class);
        when(previews.render(anyLong(), anyList(), any())).thenAnswer(i -> {
            List<NotificationRecord> rows = i.getArgument(1);
            NotificationUserState state = i.getArgument(2);
            return rows.stream().map(row -> new Item(Long.toString(row.getId()), Long.toString(row.getSeq()), row.getType(), row.getCategory(),
                    new Actor(Long.toString(row.getActorId()), "test", ""), row.getRecipientRole(), Long.toString(row.getNoteId()), null, null,
                    row.getOccurredAt().toString(), state.isRead(row), "AVAILABLE", new Preview("", "", "", ""))).toList();
        });
        service = newService(mapper);
    }

    @Test
    void concurrentDuplicateDeliveryCreatesOneUnreadOneSequenceAndOneRevision() throws Exception {
        runTogether(Collections.nCopies(6, () -> service.consume(like(user, 10))));
        Summary summary = service.summary(user);
        assertThat(summary.totalUnread()).isEqualTo(1);
        assertThat(summary.lastSeq()).isEqualTo("1");
        assertThat(summary.revision()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_notification WHERE recipient_id=?", Long.class, user)).isEqualTo(1);
        verify(changes, times(1)).publish(any());
        service.markRead(user, mapper.byBusinessKey(user, like(user, 10).getBusinessKey()).getId().toString());
        service.consume(like(user, 10));
        assertThat(service.summary(user).totalUnread()).isZero();
        assertThat(service.summary(user).revision()).isEqualTo("2");
    }

    @Test
    void concurrentDistinctActorsAreStoredIndividuallyWithoutGaps() throws Exception {
        List<Runnable> deliveries = new ArrayList<>();
        for (long actor = 10; actor < 21; actor++) {
            long captured = actor;
            deliveries.add(() -> service.consume(like(user, captured)));
        }
        runTogether(deliveries);
        assertThat(service.summary(user).totalUnread()).isEqualTo(11);
        assertThat(service.summary(user).lastSeq()).isEqualTo("11");
        assertThat(jdbc.queryForList("SELECT seq FROM t_notification WHERE recipient_id=? ORDER BY seq", Long.class, user))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
    }

    @Test
    void failureAfterInsertRollsBackBothNotificationAndStateAndDoesNotBroadcast() {
        NotificationMapper failing = mock(NotificationMapper.class, org.mockito.AdditionalAnswers.delegatesTo(mapper));
        doThrow(new DataAccessResourceFailureException("test failure after insert")).when(failing).advance(eq(user), anyLong());
        assertThatThrownBy(() -> newService(failing).consume(like(user, 10))).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(mapper.state(user)).isNull();
        assertThat(mapper.byBusinessKey(user, like(user, 10).getBusinessKey())).isNull();
        verifyNoInteractions(changes);
    }

    @Test
    void partialReplyDeliveryCommitsOneRecipientAndRetryCompletesOtherWithoutDuplicates() {
        long target = USERS.incrementAndGet();
        NotificationEvent event = NotificationServiceTest.event("REPLY", user);
        event.setRecipients(List.of(new NotificationRecipient(user, "OWNER"), new NotificationRecipient(target, "REPLY_TARGET")));
        NotificationMapper failing = mock(NotificationMapper.class, org.mockito.AdditionalAnswers.delegatesTo(mapper));
        AtomicBoolean failed = new AtomicBoolean();
        doAnswer(i -> {
            NotificationRecord row = i.getArgument(0);
            if (row.getRecipientId() == target && failed.compareAndSet(false, true)) {
                throw new DataAccessResourceFailureException("second recipient test failure");
            }
            return mapper.insert(row);
        }).when(failing).insert(any());
        NotificationService receiver = newService(failing);
        assertThatThrownBy(() -> receiver.consume(event)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(service.summary(user).totalUnread()).isEqualTo(1);
        assertThat(service.summary(target).totalUnread()).isZero();
        receiver.consume(event);
        assertThat(service.summary(user).lastSeq()).isEqualTo("1");
        assertThat(service.summary(target).lastSeq()).isEqualTo("1");
        verify(changes, times(2)).publish(any());
    }

    @Test
    void concurrentBulkReadAndNewNotificationLeaveNewSequenceUnreadAndNoOpDoesNotBumpVersion() throws Exception {
        service.consume(like(user, 10));
        runTogether(List.of(() -> service.markAllRead(user, "LIKE_COLLECTION", "1"), () -> service.consume(like(user, 11))));
        Summary after = service.summary(user);
        assertThat(after.totalUnread()).isEqualTo(1);
        assertThat(after.lastSeq()).isEqualTo("2");
        assertThat(after.revision()).isEqualTo("3");
        assertThat(after.readAllSeq()).containsEntry("LIKE_COLLECTION", "1").containsEntry("FOLLOW", "0");
        ReadAllResult noOp = service.markAllRead(user, "LIKE_COLLECTION", "1");
        assertThat(noOp.changed()).isFalse();
        assertThat(noOp.revision()).isEqualTo("3");
        List<NotificationRecord> rows = mapper.page(user, "LIKE_COLLECTION", 2, 0, 10);
        ReadState reads = service.readState(user, rows.stream().map(row -> row.getId().toString()).toList());
        Map<String, Boolean> flags = reads.items().stream().collect(java.util.stream.Collectors.toMap(ReadItem::notificationId, ReadItem::read));
        assertThat(flags).containsEntry(rows.get(0).getId().toString(), false).containsEntry(rows.get(1).getId().toString(), true);
    }

    @Test
    void cursorSnapshotPaginationExcludesLaterArrivalsAndGeneratedIdsStayStrings() {
        for (long actor = 10; actor < 15; actor++) service.consume(like(user, actor));
        Page first = service.list(user, "LIKE_COLLECTION", null, null, 2);
        assertThat(first.snapshotSeq()).isEqualTo("5");
        assertThat(first.items().get(0).notificationId()).hasSize(19);
        service.consume(like(user, 15));
        service.consume(like(user, 16));
        List<String> sequences = new ArrayList<>(first.items().stream().map(Item::seq).toList());
        Page current = first;
        while (current.hasMore()) {
            current = service.list(user, "LIKE_COLLECTION", current.nextCursor(), first.snapshotSeq(), 2);
            sequences.addAll(current.items().stream().map(Item::seq).toList());
        }
        assertThat(sequences).containsExactly("5", "4", "3", "2", "1");
        assertThat(service.list(user, "LIKE_COLLECTION", null, null, 2).items()).extracting(Item::seq).containsExactly("7", "6");
    }

    @Test
    void isolationAndOutboxBusinessUniquenessAreScopedToTheirDeclaredSource() {
        service.isolate("mysql-test", "identity-" + user, "{}", "invalid test payload");
        service.isolate("mysql-test", "identity-" + user, "{}", "invalid test payload");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_notification_event_error WHERE source=? AND event_identity=?", Long.class,
                "mysql-test", "identity-" + user)).isEqualTo(1);
        String sql = "INSERT IGNORE INTO t_notification_outbox(producer_service,business_key,event_id,payload,next_attempt_at,created_at) VALUES(?,?,?,'{}',NOW(),NOW())";
        jdbc.update(sql, "note-test", "key-" + user, UUID.randomUUID().toString());
        jdbc.update(sql, "note-test", "key-" + user, UUID.randomUUID().toString());
        jdbc.update(sql, "comment-test", "key-" + user, UUID.randomUUID().toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_notification_outbox WHERE business_key=?", Long.class, "key-" + user)).isEqualTo(2);
    }

    private NotificationService newService(NotificationMapper selectedMapper) {
        NotificationSettings settings = new NotificationSettings();
        settings.setEnabled(true);
        settings.setEnabledAt(NotificationServiceTest.CUTOFF.toString());
        return new NotificationService(selectedMapper, settings, changes, previews, new DataSourceTransactionManager(dataSource));
    }

    private NotificationEvent like(long recipient, long actor) {
        NotificationEvent event = NotificationServiceTest.event("NOTE_LIKE", recipient);
        event.setActorId(actor);
        event.setBusinessKey(NotificationBusinessKey.of("NOTE_LIKE", actor, event.getNoteId()));
        return event;
    }

    private void runTogether(List<Runnable> operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(6, operations.size()));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = operations.stream().<Future<?>>map(operation -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timeout");
                operation.run();
                return null;
            })).toList();
            start.countDown();
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
}
