import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Inserts only a reviewed, collision-free A/B/C fixture and its one note. Never logs tokens. */
public final class LiveNotificationFixtureSeed {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ACTORS = List.of("A", "B", "C");
    private static final String TOKEN_PREFIX = "Authorization:login:token:";

    public static void main(String[] args) {
        Map<String, Object> report = LiveNotificationFixtureSchema.map("mode", "insert-dedicated-fixture-only",
                "checkedAt", Instant.now().toString(), "healthy", false,
                "databaseCommitted", false, "redisSessionsCreated", 0);
        int exit = 1;
        boolean committed = false;
        JsonNode fixture = null;
        Map<String, String> tokens = new LinkedHashMap<>();
        try {
            if (args.length != 3) throw new IllegalArgumentException();
            byte[] bytes = Files.readAllBytes(Path.of(args[0]));
            fixture = JSON.readTree(bytes);
            JsonNode schema = JSON.readTree(Files.readAllBytes(Path.of(args[1])));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!schema.path("healthy").asBoolean() || !schema.path("candidateIdsAndNamesFree").asBoolean()
                    || !schema.path("candidateIdsBelowNextAutoIncrement").asBoolean()
                    || !hash.equals(schema.path("fixtureSha256").asText()) || !fixture.path("dedicated").asBoolean())
                throw new IllegalArgumentException();
            for (String table : List.of("t_user", "t_note")) {
                JsonNode metadata = schema.path("tables").path(table);
                if (!metadata.path("exists").asBoolean() || !"InnoDB".equals(metadata.path("table").path("engine").asText())
                        || !metadata.path("triggers").isEmpty() || !metadata.path("foreignKeys").isEmpty()) throw new IllegalArgumentException();
            }
            validateFixture(fixture, tokens);
            report.put("runId", text(fixture, "runId"));
            Properties credentials = LiveNotificationFixtureSchema.credentials();
            try (Connection connection = DriverManager.getConnection(LiveNotificationFixtureSchema.env("NOTIFICATION_DB_URL"), credentials)) {
                connection.setAutoCommit(false);
                try (Redis redis = new Redis()) {
                    recheck(connection, fixture);
                    for (String actor : ACTORS) insertUser(connection, fixture.path("actors").path(actor));
                    insertNote(connection, fixture.path("note"));
                    int created = 0;
                    for (String actor : ACTORS) {
                        String value = JSON.writeValueAsString(text(fixture.path("actors").path(actor), "userId"));
                        if (!"OK".equals(redis.command("SET", TOKEN_PREFIX + tokens.get(actor), value, "NX", "EX", "3600")))
                            throw new IOException();
                        report.put("redisSessionsCreated", ++created);
                    }
                    connection.commit();
                    committed = true;
                    report.put("databaseCommitted", true);
                    report.put("insertedUsers", 3);
                    report.put("insertedNotes", 1);
                    report.put("tokenTtlSeconds", 3600);
                    report.put("healthy", true);
                    exit = 0;
                } catch (Exception error) {
                    try { connection.rollback(); report.put("rollbackCompleted", true); }
                    catch (SQLException ignored) { report.put("rollbackCompleted", false); }
                    throw error;
                }
            } finally { credentials.clear(); }
        } catch (Exception error) {
            exit = 1;
            report.put("healthy", false);
            report.put("errorType", error.getClass().getSimpleName());
            if (error instanceof SQLException sql) { report.put("sqlState", sql.getSQLState()); report.put("sqlCode", sql.getErrorCode()); }
            if (!committed && fixture != null && tokens.size() == 3) {
                try (Redis redis = new Redis()) {
                    int removed = 0;
                    for (String actor : ACTORS) {
                        String expected = JSON.writeValueAsString(text(fixture.path("actors").path(actor), "userId"));
                        Object result = redis.command("EVAL", "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end", "1", TOKEN_PREFIX + tokens.get(actor), expected);
                        if (Long.valueOf(1).equals(result)) removed++;
                    }
                    report.put("failedRunSessionsRemoved", removed);
                } catch (Exception ignored) { report.put("sessionCleanupCompleted", false); }
            }
        } finally { tokens.clear(); }
        try {
            String result = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            Files.writeString(Path.of(args[2]), result + System.lineSeparator(), StandardCharsets.UTF_8);
            System.out.println(result);
        } catch (Exception ignored) { System.err.println("Sanitized fixture seed report could not be written."); exit = 1; }
        System.exit(exit);
    }

    private static String text(JsonNode node, String name) { return LiveNotificationFixtureSchema.required(node, name); }

    private static void validateFixture(JsonNode fixture, Map<String, String> tokens) {
        String prefix = text(fixture, "runPrefix");
        if (!prefix.startsWith("notification_live_") || prefix.length() > 22 || fixture.path("tokenTtlSeconds").asInt() != 3600) throw new IllegalArgumentException();
        Set<String> ids = new HashSet<>();
        for (String actor : ACTORS) {
            JsonNode user = fixture.path("actors").path(actor);
            String id = text(user, "userId");
            long value = Long.parseLong(id);
            if (value <= 0 || value > 9007199254740991L || !ids.add(id)
                    || !(prefix + "_" + actor).equals(text(user, "nickname"))
                    || text(user, "xiaohashuId").length() > 15 || !text(user, "phone").matches("[0-9]{11}")) throw new IllegalArgumentException();
            String tokenEnv = text(user, "tokenEnv");
            if (!("NOTIFICATION_LIVE_TOKEN_" + actor).equals(tokenEnv)) throw new IllegalArgumentException();
            String token = LiveNotificationFixtureSchema.env(tokenEnv);
            if (!token.matches("[A-Za-z0-9_-]{128}") || tokens.containsValue(token)) throw new IllegalArgumentException();
            tokens.put(actor, token);
        }
        JsonNode note = fixture.path("note");
        long noteId = Long.parseLong(text(note, "noteId"));
        if (noteId <= 0 || noteId > 9007199254740991L || !ids.add(text(note, "noteId"))
                || !text(note, "ownerUserId").equals(text(fixture.path("actors").path("A"), "userId"))
                || !text(note, "title").equals(prefix + "_note") || text(note, "title").length() > 64) throw new IllegalArgumentException();
    }

    private static void recheck(Connection connection, JsonNode fixture) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_user'")) {
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getString(1) == null) throw new SQLException("fixture counter guard", "23000");
                java.math.BigInteger next = new java.math.BigInteger(rows.getString(1));
                for (String actor : ACTORS) if (new java.math.BigInteger(text(fixture.path("actors").path(actor), "userId")).compareTo(next) >= 0)
                    throw new SQLException("fixture counter guard", "23000");
            }
        }
        for (String actor : ACTORS) {
            JsonNode user = fixture.path("actors").path(actor);
            if (LiveNotificationFixtureSchema.exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE id=?)", text(user, "userId"))
                    || LiveNotificationFixtureSchema.exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE xiaohashu_id=?)", text(user, "xiaohashuId"))
                    || LiveNotificationFixtureSchema.exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE nickname=?)", text(user, "nickname"))
                    || LiveNotificationFixtureSchema.exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE phone=?)", text(user, "phone"))) throw new SQLException("fixture collision", "23000");
        }
        if (LiveNotificationFixtureSchema.exists(connection, "SELECT EXISTS(SELECT 1 FROM t_note WHERE id=?)", text(fixture.path("note"), "noteId"))) throw new SQLException("fixture collision", "23000");
    }

    private static void insertUser(Connection connection, JsonNode user) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO t_user (id,xiaohashu_id,password,nickname,avatar,birthday,background_img,phone,sex,status,introduction,create_time,update_time,is_deleted) VALUES (?,?,NULL,?,NULL,NULL,NULL,?,0,0,?,NOW(),NOW(),b'0')")) {
            statement.setQueryTimeout(5);
            statement.setLong(1, Long.parseLong(text(user, "userId")));
            statement.setString(2, text(user, "xiaohashuId"));
            statement.setString(3, text(user, "nickname"));
            statement.setString(4, text(user, "phone"));
            statement.setString(5, "Dedicated notification live verification account");
            if (statement.executeUpdate() != 1) throw new SQLException();
        }
    }

    private static void insertNote(Connection connection, JsonNode note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO t_note (id,title,is_content_empty,creator_id,topic_id,topic_name,is_top,type,img_uris,video_uri,visible,create_time,update_time,status,content_uuid) VALUES (?,?,b'1',?,NULL,'',b'0',0,'',NULL,0,NOW(),NOW(),1,NULL)")) {
            statement.setQueryTimeout(5);
            statement.setLong(1, Long.parseLong(text(note, "noteId")));
            statement.setString(2, text(note, "title"));
            statement.setLong(3, Long.parseLong(text(note, "ownerUserId")));
            if (statement.executeUpdate() != 1) throw new SQLException();
        }
    }

    private static final class Redis implements AutoCloseable {
        private final Socket socket = new Socket();
        private final InputStream input;
        private final OutputStream output;
        Redis() throws Exception {
            try {
                socket.connect(new InetSocketAddress(LiveNotificationFixtureSchema.env("REDIS_HOST"), Integer.parseInt(LiveNotificationFixtureSchema.env("REDIS_PORT"))), 3000);
                socket.setSoTimeout(5000);
                input = socket.getInputStream(); output = socket.getOutputStream();
                String password = System.getenv("REDIS_PASSWORD");
                if (password != null && !password.isEmpty() && !"OK".equals(command("AUTH", password))) throw new IOException();
                if (!"OK".equals(command("SELECT", LiveNotificationFixtureSchema.env("REDIS_DATABASE")))) throw new IOException();
            } catch (Exception error) { socket.close(); throw error; }
        }
        Object command(String... values) throws IOException {
            output.write(("*" + values.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(bytes); output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.flush();
            int type = input.read(); String line = line();
            if (type == '+') return line;
            if (type == ':') return Long.parseLong(line);
            if (type == '$' && "-1".equals(line)) return null;
            // Redis server error text and request values may contain secrets; discard them.
            throw new IOException();
        }
        private String line() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int next;
            while ((next = input.read()) >= 0 && next != '\r') {
                if (bytes.size() >= 8192) throw new IOException();
                bytes.write(next);
            }
            if (next < 0 || input.read() != '\n') throw new EOFException();
            return bytes.toString(StandardCharsets.UTF_8);
        }
        public void close() throws IOException { socket.close(); }
    }
}
