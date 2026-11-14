import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Read-only metadata and existence checks for dedicated notification test fixtures. */
public final class LiveNotificationFixtureSchema {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        Map<String, Object> report = map("mode", "read-only-fixture-schema-and-collisions",
                "checkedAt", Instant.now().toString(), "healthy", false);
        int exit = 1;
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            byte[] fixtureBytes = Files.readAllBytes(Path.of(args[0]));
            JsonNode fixture = JSON.readTree(fixtureBytes);
            report.put("fixtureSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixtureBytes)));
            report.put("runId", required(fixture, "runId"));
            Properties credentials = credentials();
            try (Connection connection = DriverManager.getConnection(env("NOTIFICATION_DB_URL"), credentials)) {
                connection.setReadOnly(true);
                Map<String, Object> tables = new LinkedHashMap<>();
                for (String table : List.of("t_user", "t_note")) {
                    List<Map<String, Object>> metadata = rows(connection,
                            "SELECT TABLE_NAME AS table_name, ENGINE AS engine, TABLE_COLLATION AS table_collation, AUTO_INCREMENT AS next_auto_increment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", table);
                    tables.put(table, map("exists", !metadata.isEmpty(), "table", metadata.isEmpty() ? null : metadata.get(0),
                            "columns", rows(connection, "SELECT COLUMN_NAME AS column_name, COLUMN_TYPE AS column_type, DATA_TYPE AS data_type, IS_NULLABLE AS is_nullable, COLUMN_DEFAULT AS column_default, CHARACTER_MAXIMUM_LENGTH AS max_length, EXTRA AS extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ORDINAL_POSITION", table),
                            "indexes", rows(connection, "SELECT INDEX_NAME AS index_name, NON_UNIQUE AS non_unique, SEQ_IN_INDEX AS seq_in_index, COLUMN_NAME AS column_name, SUB_PART AS sub_part FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? ORDER BY INDEX_NAME,SEQ_IN_INDEX", table),
                            "foreignKeys", rows(connection, "SELECT COLUMN_NAME AS column_name, REFERENCED_TABLE_NAME AS referenced_table, REFERENCED_COLUMN_NAME AS referenced_column FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND referenced_table_name IS NOT NULL", table),
                            "triggers", rows(connection, "SELECT TRIGGER_NAME AS trigger_name, EVENT_MANIPULATION AS event_manipulation, ACTION_TIMING AS action_timing FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table=?", table)));
                }
                report.put("tables", tables);
                Map<String, Object> collisions = new LinkedHashMap<>();
                boolean available = true;
                for (String actor : List.of("A", "B", "C")) {
                    JsonNode user = fixture.path("actors").path(actor);
                    Map<String, Object> check = map(
                            "userIdExists", exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE id=?)", required(user, "userId")),
                            "xiaohashuIdExists", exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE xiaohashu_id=?)", required(user, "xiaohashuId")),
                            "nicknameExists", exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE nickname=?)", required(user, "nickname")),
                            "phoneExists", exists(connection, "SELECT EXISTS(SELECT 1 FROM t_user WHERE phone=?)", required(user, "phone")));
                    available &= check.values().stream().noneMatch(Boolean.TRUE::equals);
                    collisions.put(actor, check);
                }
                boolean noteExists = exists(connection, "SELECT EXISTS(SELECT 1 FROM t_note WHERE id=?)", required(fixture.path("note"), "noteId"));
                collisions.put("noteIdExists", noteExists);
                report.put("collisions", collisions);
                report.put("candidateIdsAndNamesFree", available && !noteExists);
                JsonNode userMetadata = JSON.valueToTree(tables.get("t_user"));
                String nextId = userMetadata.path("table").path("next_auto_increment").asText();
                boolean counterSafe = !nextId.isBlank() && !"null".equals(nextId);
                if (counterSafe) {
                    java.math.BigInteger next = new java.math.BigInteger(nextId);
                    for (String actor : List.of("A", "B", "C")) {
                        java.math.BigInteger candidate = new java.math.BigInteger(required(fixture.path("actors").path(actor), "userId"));
                        counterSafe &= candidate.signum() > 0 && candidate.compareTo(next) < 0;
                    }
                }
                report.put("candidateIdsBelowNextAutoIncrement", counterSafe);
                report.put("healthy", true);
                exit = 0;
            } finally { credentials.clear(); }
        } catch (Exception error) {
            report.put("errorType", error.getClass().getSimpleName());
            if (error instanceof SQLException sql) {
                report.put("sqlState", sql.getSQLState()); report.put("sqlCode", sql.getErrorCode());
            }
        }
        try {
            String result = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            Files.writeString(Path.of(args[1]), result + System.lineSeparator(), StandardCharsets.UTF_8);
            System.out.println(result);
        } catch (Exception ignored) { System.err.println("Fixture schema report could not be written."); exit = 1; }
        System.exit(exit);
    }

    static Properties credentials() {
        Properties result = new Properties();
        result.setProperty("user", env("NOTIFICATION_DB_USER"));
        result.setProperty("password", env("NOTIFICATION_DB_PASSWORD"));
        result.setProperty("connectTimeout", "3000");
        result.setProperty("socketTimeout", "5000");
        result.setProperty("readOnlyPropagatesToServer", "true");
        DriverManager.setLoginTimeout(5);
        return result;
    }

    static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException();
        return value;
    }

    static String required(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException();
        return value.asText();
    }

    static boolean exists(Connection connection, String query, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setQueryTimeout(5);
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) { return result.next() && result.getBoolean(1); }
        }
    }

    private static List<Map<String, Object>> rows(Connection connection, String query, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setQueryTimeout(5);
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData columns = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns.getColumnCount(); i++) row.put(columns.getColumnLabel(i), result.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]);
        return result;
    }
}
