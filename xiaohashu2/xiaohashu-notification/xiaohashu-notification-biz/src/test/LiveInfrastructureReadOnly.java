import com.alibaba.druid.filter.config.ConfigTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Fixed metadata probes only: no business-row reads, DDL, registration or consumers. */
public class LiveInfrastructureReadOnly {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLES = List.of("t_notification_user_state", "t_notification",
            "t_notification_outbox", "t_notification_event_error");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static Map<?, ?> config;
    private static Path root;

    public static void main(String[] args) throws Exception {
        loadDevConfig(Path.of(args[0]));
        boolean createMissing = args.length > 2 && "--create-missing".equals(args[2]);
        Map<String, Object> report = map("checkedAt", Instant.now().toString(), "mode", createMissing ? "create-only-missing-reviewed-notification-tables" : "read-only-metadata");
        report.put("mysql", probe(createMissing ? LiveInfrastructureReadOnly::createMissingTables : LiveInfrastructureReadOnly::mysql));
        report.put("redis", probe(LiveInfrastructureReadOnly::redis));
        report.put("nacos", probe(LiveInfrastructureReadOnly::nacos));
        String result = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Files.writeString(Path.of(args[1]), result + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println(result);
    }

    private static void loadDevConfig(Path backendRoot) throws IOException {
        root = backendRoot.toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(root.resolve("xiaohashu-note/xiaohashu-note-biz/src/main/resources/config/application-dev.yml"))) {
            config = new Yaml(new SafeConstructor()).load(input);
        }
    }

    /** Only LiveRuntimeCredentials invokes this; its stdout must be captured in memory. */
    static Map<String, Object> runtimeCredentials(Path backendRoot) throws Exception {
        loadDevConfig(backendRoot);
        RedisAccess access = resolveRedisAccess();
        return map("NOTIFICATION_DB_URL", cfg("spring", "datasource", "url"),
                "NOTIFICATION_DB_USER", cfg("spring", "datasource", "username"),
                "NOTIFICATION_DB_PASSWORD", mysqlPassword(),
                "REDIS_HOST", cfg("spring", "data", "redis", "host"),
                "REDIS_PORT", cfg("spring", "data", "redis", "port"),
                "REDIS_PASSWORD", access.password(), "SPRING_DATA_REDIS_PASSWORD", access.password(),
                "REDIS_DATABASE", cfg("spring", "data", "redis", "database"),
                "NACOS_SERVER_ADDR", "127.0.0.1:8848", "NACOS_NAMESPACE", "xiaohashu");
    }

    private interface Check { Object run() throws Exception; }
    private static Object probe(Check check) {
        try { return check.run(); }
        catch (Exception error) {
            // Exception messages may contain credentials, URLs or server-returned data.
            Map<String, Object> result = map("healthy", false, "errorType", error.getClass().getSimpleName());
            if (error instanceof SQLException sql) {
                result.put("sqlState", sql.getSQLState()); result.put("sqlCode", sql.getErrorCode());
                String diagnostic = String.valueOf(sql.getMessage()).toLowerCase(Locale.ROOT);
                result.put("errorReason", diagnostic.contains("public key retrieval") ? "public-key-retrieval-disallowed" :
                        diagnostic.contains("access denied") ? "authentication-rejected" :
                        diagnostic.contains("unknown database") ? "schema-not-found" : "sql-connection-or-query-failure");
            }
            if (error instanceof RedisFailure redis) { result.put("stage", redis.stage); result.put("redisErrorCode", redis.code); }
            return result;
        }
    }

    private static String cfg(String... path) {
        Object value = config;
        for (String item : path) {
            if (!(value instanceof Map<?, ?> values)) { value = null; break; }
            Object selected = values.get(item);
            if (selected == null) for (var entry : values.entrySet()) if (normalized(entry.getKey().toString()).equals(normalized(item))) { selected = entry.getValue(); break; }
            value = selected;
        }
        return resolve(value == null ? "" : value.toString(), 0);
    }
    private static String normalized(String key) { return key.replaceAll("[-_.]", "").toLowerCase(Locale.ROOT); }
    private static String resolve(String value, int depth) {
        if (depth > 10) throw new IllegalStateException("Configuration placeholder recursion");
        Matcher placeholders = Pattern.compile("\\$\\{([^{}:]+)(?::([^{}]*))?}").matcher(value);
        StringBuffer resolved = new StringBuffer(); boolean changed = false;
        while (placeholders.find()) {
            String replacement = System.getenv(placeholders.group(1));
            if (replacement == null) replacement = placeholders.group(2);
            if (replacement == null) throw new IllegalStateException("Unresolved configuration placeholder");
            placeholders.appendReplacement(resolved, Matcher.quoteReplacement(replacement)); changed = true;
        }
        placeholders.appendTail(resolved);
        return changed ? resolve(resolved.toString(), depth + 1) : resolved.toString();
    }
    private static String mysqlPassword() throws Exception {
        String password = cfg("spring", "datasource", "password");
        String properties = cfg("spring", "datasource", "druid", "connectionProperties");
        Map<String, String> druid = new HashMap<>();
        for (String entry : properties.split(";")) { int equals = entry.indexOf('='); if (equals > 0) druid.put(entry.substring(0, equals).trim(), entry.substring(equals + 1).trim()); }
        if ("true".equalsIgnoreCase(druid.get("config.decrypt"))) {
            String publicKey = druid.get("config.decrypt.key");
            if (publicKey == null || publicKey.isBlank()) throw new IllegalStateException("Druid decryption key absent");
            return ConfigTools.decrypt(publicKey, password);
        }
        return password;
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key); return value == null ? fallback : value;
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]);
        return result;
    }

    private static Properties dbCredentials() throws Exception {
        Properties credentials = new Properties();
        credentials.setProperty("user", cfg("spring", "datasource", "username"));
        credentials.setProperty("password", mysqlPassword());
        credentials.setProperty("connectTimeout", "3000");
        credentials.setProperty("socketTimeout", "5000");
        credentials.setProperty("readOnlyPropagatesToServer", "true");
        return credentials;
    }

    private static Object mysql() throws Exception {
        String url = cfg("spring", "datasource", "url");
        Properties credentials = dbCredentials();
        DriverManager.setLoginTimeout(5);
        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            connection.setReadOnly(true);
            Map<String, Object> result = map("healthy", true, "credentials", "note-dev-with-druid-decryption-in-memory");
            result.put("server", rows(connection, "SELECT VERSION() AS version, DATABASE() AS schema_name, @@port AS server_port, @@transaction_read_only AS session_read_only"));
            Map<String, Object> tables = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (String table : TABLES) {
                List<Map<String, Object>> metadata = rows(connection,
                        "SELECT TABLE_NAME AS table_name, ENGINE AS engine, TABLE_COLLATION AS table_collation FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", table);
                if (metadata.isEmpty()) { missing.add(table); tables.put(table, map("exists", false)); continue; }
                tables.put(table, map("exists", true, "table", metadata.get(0),
                        "columns", rows(connection, "SELECT COLUMN_NAME AS column_name, COLUMN_TYPE AS column_type, IS_NULLABLE AS is_nullable, COLUMN_DEFAULT AS column_default, EXTRA AS extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ORDINAL_POSITION", table),
                        "indexes", rows(connection, "SELECT INDEX_NAME AS index_name, NON_UNIQUE AS non_unique, SEQ_IN_INDEX AS seq_in_index, COLUMN_NAME AS column_name, SUB_PART AS sub_part, INDEX_TYPE AS index_type FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? ORDER BY INDEX_NAME,SEQ_IN_INDEX", table)));
            }
            result.put("missingTables", missing);
            result.put("tables", tables);
            result.put("ddlComparison", compareDdl(tables));
            return result;
        } finally { credentials.clear(); }
    }

    @SuppressWarnings("unchecked")
    private static Object createMissingTables() throws Exception {
        Map<String, Object> before = (Map<String, Object>) mysql();
        JsonNode snapshot = JSON.valueToTree(before);
        List<String> missing = new ArrayList<>();
        for (JsonNode table : snapshot.path("missingTables")) missing.add(table.asText());
        if (missing.isEmpty()) { before.put("createdTables", List.of()); return before; }
        for (JsonNode difference : snapshot.path("ddlComparison").path("differences")) {
            if (!difference.asText().endsWith(": table missing")) {
                before.put("schemaPreparationBlocked", "existing-table-incompatible"); return before;
            }
        }
        String ddl = Files.readString(root.resolve("xiaohashu-notification/xiaohashu-notification-biz/src/main/resources/db/notification_module.sql"));
        Matcher definitions = Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS\\s+(\\w+)\\s*\\((.*?)\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4(?:\\s+COMMENT\\s*=\\s*'(?:''|\\\\.|[^'\\\\])*')?;").matcher(ddl);
        Map<String, String> statements = new LinkedHashMap<>();
        while (definitions.find()) {
            if (!TABLES.contains(definitions.group(1)) || statements.put(definitions.group(1), definitions.group()) != null)
                throw new IllegalStateException("Unexpected review DDL table");
        }
        if (!statements.keySet().equals(new HashSet<>(TABLES))) throw new IllegalStateException("Review DDL must define exactly four expected tables");
        List<String> created = new ArrayList<>();
        Map<String, Object> creationError = null;
        Properties credentials = dbCredentials();
        try (Connection connection = DriverManager.getConnection(cfg("spring", "datasource", "url"), credentials)) {
            for (String table : missing) {
                // Recheck immediately before CREATE. Existing tables are never altered.
                if (!rows(connection, "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", table).isEmpty()) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(10); statement.executeUpdate(statements.get(table)); created.add(table);
                } catch (SQLException error) {
                    creationError = map("errorType", error.getClass().getSimpleName(), "sqlState", error.getSQLState(), "sqlCode", error.getErrorCode());
                    break;
                }
            }
        } finally { credentials.clear(); }
        Map<String, Object> after = (Map<String, Object>) mysql();
        after.put("createdTables", created);
        after.put("existingTablesChanged", false);
        if (creationError != null) after.put("creationError", creationError);
        return after;
    }
    private static List<Map<String, Object>> rows(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                ResultSetMetaData metadata = rows.getMetaData();
                while (rows.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) row.put(metadata.getColumnLabel(i), rows.getObject(i));
                    result.add(row);
                }
                return result;
            }
        }
    }

    private static Object compareDdl(Map<String, Object> tables) throws IOException {
        String ddl = Files.readString(root.resolve("xiaohashu-notification/xiaohashu-notification-biz/src/main/resources/db/notification_module.sql"));
        List<String> differences = new ArrayList<>();
        Matcher creates = Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS\\s+(\\w+)\\s*\\((.*?)\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4(?:\\s+COMMENT\\s*=\\s*'(?:''|\\\\.|[^'\\\\])*')?;").matcher(ddl);
        int compared = 0;
        while (creates.find()) {
            compared++;
            String table = creates.group(1);
            JsonNode actual = JSON.valueToTree(tables.get(table));
            if (!actual.path("exists").asBoolean()) { differences.add(table + ": table missing"); continue; }
            if (!"InnoDB".equals(actual.path("table").path("engine").asText())) differences.add(table + ": engine differs from InnoDB");
            if (!actual.path("table").path("table_collation").asText().startsWith("utf8mb4_")) differences.add(table + ": charset differs from utf8mb4");
            Map<String, JsonNode> columns = new HashMap<>();
            for (JsonNode column : actual.path("columns")) columns.put(column.path("column_name").asText(), column);
            Map<String, List<JsonNode>> indexes = new LinkedHashMap<>();
            for (JsonNode index : actual.path("indexes")) indexes.computeIfAbsent(index.path("index_name").asText(), key -> new ArrayList<>()).add(index);
            Set<String> expectedColumns = new HashSet<>();
            for (String definition : creates.group(2).split("\\R")) {
                definition = definition.trim().replaceAll(",$", "");
                Matcher column = Pattern.compile("^(\\w+)\\s+(BIGINT|INT|VARCHAR\\(\\d+\\)|CHAR\\(\\d+\\)|DATETIME|LONGTEXT)\\s+(NOT NULL|NULL)(.*)$", Pattern.CASE_INSENSITIVE).matcher(definition);
                if (column.matches()) {
                    String name = column.group(1); expectedColumns.add(name);
                    JsonNode found = columns.get(name);
                    if (found == null) { differences.add(table + "." + name + ": column missing"); continue; }
                    String type = found.path("column_type").asText().toLowerCase(Locale.ROOT).replaceAll("^(bigint|int)\\(\\d+\\)$", "$1");
                    if (!type.equals(column.group(2).toLowerCase(Locale.ROOT))) differences.add(table + "." + name + ": type differs, actual=" + type);
                    String nullable = column.group(3).equalsIgnoreCase("NULL") ? "YES" : "NO";
                    if (!nullable.equals(found.path("is_nullable").asText())) differences.add(table + "." + name + ": nullability differs");
                    boolean autoIncrement = column.group(4).toUpperCase(Locale.ROOT).contains("AUTO_INCREMENT");
                    if (autoIncrement != found.path("extra").asText().contains("auto_increment")) differences.add(table + "." + name + ": auto_increment differs");
                    Matcher defaultValue = Pattern.compile("(?i)\\bDEFAULT\\s+('([^']*)'|(-?\\d+))").matcher(column.group(4));
                    if (defaultValue.find()) {
                        String expected = defaultValue.group(2) == null ? defaultValue.group(3) : defaultValue.group(2);
                        if (!expected.equals(found.path("column_default").asText())) differences.add(table + "." + name + ": default differs");
                    }
                    continue;
                }
                Matcher index = Pattern.compile("^(PRIMARY KEY|UNIQUE KEY \\w+|KEY \\w+)\\s*\\(([^)]+)\\)$").matcher(definition);
                if (index.matches()) {
                    boolean unique = !index.group(1).startsWith("KEY ");
                    List<String> expected = Arrays.stream(index.group(2).split(",")).map(String::trim).toList();
                    boolean found = indexes.values().stream().anyMatch(items ->
                            (items.get(0).path("non_unique").asInt() == 0) == unique &&
                            items.stream().allMatch(item -> item.path("sub_part").isNull()) &&
                            items.stream().map(item -> item.path("column_name").asText()).toList().equals(expected));
                    if (!found) differences.add(table + ": missing compatible " + (unique ? "unique " : "") + "index(" + String.join(",", expected) + ")");
                }
            }
            for (var column : columns.entrySet()) if (!expectedColumns.contains(column.getKey()) &&
                    "NO".equals(column.getValue().path("is_nullable").asText()) && column.getValue().path("column_default").isNull() &&
                    !column.getValue().path("extra").asText().contains("auto_increment"))
                differences.add(table + "." + column.getKey() + ": extra required column can block inserts");
        }
        if (compared != TABLES.size()) differences.add("review DDL did not contain exactly four expected table definitions");
        return map("ddlCompatible", differences.isEmpty(), "differences", differences);
    }

    private record RedisAccess(String password, Map<String, Object> metadata) {}
    private static RedisAccess resolveRedisAccess() throws Exception {
        try {
            Map<String, Object> metadata = redisWithPassword("");
            metadata.put("unauthenticatedPing", "PONG");
            metadata.put("authentication", "not-required");
            return new RedisAccess("", metadata);
        } catch (RedisFailure noAuthentication) {
            String configured = cfg("spring", "data", "redis", "password");
            if (configured.isEmpty()) throw noAuthentication;
            Map<String, Object> metadata = redisWithPassword(configured);
            metadata.put("unauthenticatedPing", noAuthentication.code);
            return new RedisAccess(configured, metadata);
        }
    }
    private static Object redis() throws Exception { return resolveRedisAccess().metadata(); }
    private static Map<String, Object> redisWithPassword(String password) throws Exception {
        String host = cfg("spring", "data", "redis", "host");
        int port = Integer.parseInt(cfg("spring", "data", "redis", "port"));
        String database = cfg("spring", "data", "redis", "database");
        if (database.isBlank()) database = "0";
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000); socket.setSoTimeout(3000);
            InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream();
            if (!password.isEmpty()) redisCommand(input, output, "AUTH", "AUTH", password);
            String pong = redisCommand(input, output, "PING", "PING");
            redisCommand(input, output, "SELECT", "SELECT", database);
            String info = redisCommand(input, output, "INFO", "INFO", "server");
            Map<String, Object> selected = new LinkedHashMap<>();
            Set<String> allowed = Set.of("redis_version", "redis_mode", "os", "arch_bits", "tcp_port", "uptime_in_seconds");
            for (String line : info.split("\\R")) { int colon = line.indexOf(':'); if (colon > 0 && allowed.contains(line.substring(0, colon))) selected.put(line.substring(0, colon), line.substring(colon + 1)); }
            if (!"PONG".equals(pong)) throw new RedisFailure("PING", "UNEXPECTED_RESPONSE");
            return map("healthy", true, "host", host, "port", port, "selectedDatabase", Integer.parseInt(database),
                    "authentication", password.isEmpty() ? "not-supplied" : "accepted", "server", selected);
        }
    }
    private static class RedisFailure extends IOException {
        final String stage, code;
        RedisFailure(String stage, String code) { super("Redacted Redis failure"); this.stage = stage; this.code = code; }
    }
    private static String redisCommand(InputStream input, OutputStream output, String stage, String... words) throws IOException {
        output.write(("*" + words.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String word : words) {
            byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII)); output.write(bytes); output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        output.flush(); int type = input.read(); String line = line(input);
        if (type == '-') {
            String code = line.split(" ", 2)[0];
            throw new RedisFailure(stage, Set.of("NOAUTH", "WRONGPASS", "ERR", "NOPERM").contains(code) ? code : "PROTOCOL_REJECTION");
        }
        if (type == '+') return line;
        if (type == '$') {
            int size = Integer.parseInt(line); if (size < 0 || size > 2_000_000) throw new IOException("Invalid Redis size");
            byte[] bytes = input.readNBytes(size); if (bytes.length != size) throw new EOFException();
            if (input.read() != '\r' || input.read() != '\n') throw new IOException("Invalid Redis framing");
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IOException("Unexpected Redis response");
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int next; while ((next = input.read()) >= 0 && next != '\r') { if (buffer.size() >= 8192) throw new IOException("Invalid line size"); buffer.write(next); }
        if (next < 0 || input.read() != '\n') throw new EOFException();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static Object nacos() throws Exception {
        String address = env("NACOS_SERVER_ADDR", "127.0.0.1:8848");
        String namespace = env("NACOS_NAMESPACE", "xiaohashu");
        String group = env("NACOS_GROUP", "DEFAULT_GROUP");
        String base = (address.contains("://") ? address : "http://" + address) + "/nacos";
        Map<String, Object> result = map("namespace", namespace, "group", group);
        HttpResponse<String> readiness = get(base + "/v1/console/health/readiness");
        result.put("readinessStatus", readiness.statusCode());
        HttpResponse<String> namespaces = get(base + "/v1/console/namespaces");
        result.put("namespacesStatus", namespaces.statusCode());
        if (namespaces.statusCode() == 200) {
            List<Object> matches = new ArrayList<>();
            for (JsonNode item : JSON.readTree(namespaces.body()).path("data")) if (namespace.equals(item.path("namespace").asText()) || namespace.equals(item.path("namespaceShowName").asText()))
                matches.add(map("namespace", item.path("namespace").asText(), "name", item.path("namespaceShowName").asText()));
            result.put("matchingNamespaces", matches);
        }
        List<String> services = new ArrayList<>(); int count = 0;
        for (int page = 1; page <= 20; page++) {
            HttpResponse<String> listing = get(base + "/v1/ns/service/list?pageNo=" + page + "&pageSize=100&namespaceId=" + enc(namespace) + "&groupName=" + enc(group));
            result.put("serviceListStatus", listing.statusCode());
            if (listing.statusCode() != 200) { result.put("healthy", false); return result; }
            JsonNode body = JSON.readTree(listing.body()); count = body.path("count").asInt();
            for (JsonNode service : body.path("doms")) services.add(service.asText());
            if (services.size() >= count) break;
        }
        result.put("healthy", readiness.statusCode() == 200);
        result.put("serviceCount", count); result.put("services", services);
        Map<String, Object> instances = new LinkedHashMap<>();
        for (String service : List.of("xiaohashu-notification", "xiaohashu-gateway", "xiaohashu-user", "xiaohashu-note", "xiaohashu-comment", "xiaohashu-user-relation")) {
            HttpResponse<String> response = get(base + "/v1/ns/instance/list?namespaceId=" + enc(namespace) + "&groupName=" + enc(group) + "&serviceName=" + enc(service) + "&healthyOnly=false");
            List<Object> hosts = new ArrayList<>();
            if (response.statusCode() == 200) for (JsonNode host : JSON.readTree(response.body()).path("hosts"))
                hosts.add(map("ip", host.path("ip").asText(), "port", host.path("port").asInt(), "healthy", host.path("healthy").asBoolean(), "enabled", host.path("enabled").asBoolean(), "cluster", host.path("clusterName").asText()));
            instances.put(service, map("httpStatus", response.statusCode(), "hosts", hosts));
        }
        result.put("instances", instances); return result;
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
