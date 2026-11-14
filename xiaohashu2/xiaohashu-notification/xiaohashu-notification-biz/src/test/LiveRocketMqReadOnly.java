import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.admin.*;
import org.apache.rocketmq.common.protocol.body.*;
import org.apache.rocketmq.common.protocol.route.*;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** No RocketMQ client is started. Only these eight metadata request codes can be sent. */
public class LiveRocketMqReadOnly {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<Integer> READ_ONLY_CODES = Set.of(26, 28, 105, 106, 201, 202, 203, 208);
    private static final Map<String, String> TOPIC_GROUPS = new LinkedHashMap<>();
    private static int requestId;
    static {
        TOPIC_GROUPS.put("LikeUnlikeTopic", "xiaohashu_notification_note_like_v1");
        TOPIC_GROUPS.put("CommentLikeUnlikeTopic", "xiaohashu_notification_comment_like_v1");
        TOPIC_GROUPS.put("NotificationEventTopic", "xiaohashu_notification_committed_v1");
    }
    private record Response(int code, byte[] body) {}

    public static void main(String[] args) throws Exception {
        Map<String, Object> report = map("checkedAt", Instant.now().toString(), "mode", "read-only-metadata");
        try { report.put("rocketmq", inspect()); }
        catch (Exception error) { report.put("rocketmq", map("healthy", false, "errorType", error.getClass().getSimpleName())); }
        String result = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Files.writeString(Path.of(args[0]), result + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println(result);
    }

    private static Object inspect() throws Exception {
        String nameserver = env("ROCKETMQ_NAMESRV", "127.0.0.1:9876").split(";")[0];
        Response clusterResponse = request(nameserver, 106, Map.of());
        Map<String, Object> result = map("nameServer", nameserver, "clusterResponseCode", clusterResponse.code());
        if (clusterResponse.code() != 0) { result.put("healthy", false); return result; }
        ClusterInfo cluster = RemotingSerializable.decode(clusterResponse.body(), ClusterInfo.class);
        result.put("clusters", cluster.getClusterAddrTable());
        Map<String, Object> routes = new LinkedHashMap<>();
        for (String topic : TOPIC_GROUPS.keySet()) {
            Response response = request(nameserver, 105, Map.of("topic", topic));
            Map<String, Object> route = map("responseCode", response.code(), "exists", response.code() == 0);
            if (response.code() == 0) {
                TopicRouteData body = RemotingSerializable.decode(response.body(), TopicRouteData.class);
                List<Object> queues = new ArrayList<>();
                for (QueueData queue : body.getQueueDatas()) queues.add(map("brokerName", queue.getBrokerName(), "readQueues", queue.getReadQueueNums(), "writeQueues", queue.getWriteQueueNums(), "permission", queue.getPerm()));
                route.put("queues", queues);
            }
            routes.put(topic, route);
        }
        result.put("topics", routes);
        List<Object> brokers = new ArrayList<>();
        boolean anyHealthy = false;
        for (BrokerData broker : cluster.getBrokerAddrTable().values()) {
            String advertised = broker.getBrokerAddrs().get(0L);
            if (advertised == null) { brokers.add(map("brokerName", broker.getBrokerName(), "healthy", false, "masterAdvertised", false)); continue; }
            Map<String, Object> checked = checkBroker(broker.getBrokerName(), advertised);
            anyHealthy |= Boolean.TRUE.equals(checked.get("healthy"));
            brokers.add(checked);
        }
        result.put("brokers", brokers); result.put("healthy", anyHealthy); return result;
    }

    private static Map<String, Object> checkBroker(String expectedName, String advertised) {
        String address = env("ROCKETMQ_BROKER_ADDR", advertised);
        Map<String, Object> result = map("brokerName", expectedName, "advertisedAddress", advertised, "requestAddress", address);
        try {
            Response configResponse = request(address, 26, Map.of());
            result.put("configResponseCode", configResponse.code());
            if (configResponse.code() != 0) { result.put("healthy", false); return result; }
            Properties properties = new Properties();
            properties.load(new StringReader(new String(configResponse.body(), StandardCharsets.UTF_8)));
            Map<String, Object> selected = new LinkedHashMap<>();
            for (String key : List.of("brokerName", "brokerClusterName", "brokerRole", "listenPort", "brokerIP1", "fileReservedTime", "deleteWhen", "diskMaxUsedSpaceRatio", "cleanFileForciblyEnable", "autoCreateTopicEnable", "autoCreateSubscriptionGroup"))
                if (properties.containsKey(key)) selected.put(key, properties.getProperty(key));
            result.put("config", selected);
            if (!expectedName.equals(properties.getProperty("brokerName"))) {
                result.put("healthy", false); result.put("brokerNameMismatch", true); return result;
            }
            Response runtimeResponse = request(address, 28, Map.of());
            result.put("runtimeResponseCode", runtimeResponse.code());
            if (runtimeResponse.code() == 0) {
                Map<String, Object> runtime = new LinkedHashMap<>();
                KVTable table = RemotingSerializable.decode(runtimeResponse.body(), KVTable.class);
                for (String key : List.of("brokerVersionDesc", "brokerVersion", "bootTimestamp", "msgPutTotalTodayNow", "msgGetTotalTodayNow", "commitLogDiskRatio", "consumeQueueDiskRatio", "dispatchBehindBytes", "pageCacheLockTimeMills", "runtime"))
                    if (table.getTable().containsKey(key)) runtime.put(key, table.getTable().get(key));
                result.put("runtime", runtime);
            }
            Response groupsResponse = request(address, 201, Map.of());
            result.put("subscriptionGroupsResponseCode", groupsResponse.code());
            SubscriptionGroupWrapper groupTable = groupsResponse.code() == 0 ? RemotingSerializable.decode(groupsResponse.body(), SubscriptionGroupWrapper.class) : null;
            Map<String, Object> topics = new LinkedHashMap<>();
            for (var topicGroup : TOPIC_GROUPS.entrySet()) {
                String topic = topicGroup.getKey(), group = topicGroup.getValue();
                Map<String, Object> status = new LinkedHashMap<>();
                Response topicResponse = request(address, 202, Map.of("topic", topic));
                status.put("topicStatsResponseCode", topicResponse.code());
                if (topicResponse.code() == 0) {
                    TopicStatsTable stats = RemotingSerializable.decode(topicResponse.body(), TopicStatsTable.class);
                    List<Object> offsets = new ArrayList<>();
                    stats.getOffsetTable().entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getKey().getQueueId())).forEach(entry ->
                        offsets.add(map("queueId", entry.getKey().getQueueId(), "minOffset", entry.getValue().getMinOffset(), "maxOffset", entry.getValue().getMaxOffset(), "lastUpdateTimestamp", entry.getValue().getLastUpdateTimestamp())));
                    status.put("queueOffsets", offsets);
                }
                var groupConfig = groupTable == null ? null : groupTable.getSubscriptionGroupTable().get(group);
                Map<String, Object> groupStatus = map("group", group, "configurationPresent", groupConfig != null);
                if (groupConfig != null) {
                    groupStatus.put("consumeEnable", groupConfig.isConsumeEnable());
                    groupStatus.put("consumeFromMinEnable", groupConfig.isConsumeFromMinEnable());
                    Response consumeResponse = request(address, 208, Map.of("consumerGroup", group, "topic", topic));
                    groupStatus.put("consumeStatsResponseCode", consumeResponse.code());
                    if (consumeResponse.code() == 0) {
                        ConsumeStats stats = RemotingSerializable.decode(consumeResponse.body(), ConsumeStats.class);
                        List<Object> offsets = new ArrayList<>();
                        stats.getOffsetTable().entrySet().stream().filter(entry -> topic.equals(entry.getKey().getTopic())).sorted(Comparator.comparingInt(entry -> entry.getKey().getQueueId())).forEach(entry ->
                            offsets.add(map("queueId", entry.getKey().getQueueId(), "brokerOffset", entry.getValue().getBrokerOffset(), "consumerOffset", entry.getValue().getConsumerOffset(), "lastTimestamp", entry.getValue().getLastTimestamp())));
                        groupStatus.put("queueOffsets", offsets);
                    }
                    Response connectionResponse = request(address, 203, Map.of("consumerGroup", group));
                    groupStatus.put("connectionResponseCode", connectionResponse.code());
                    if (connectionResponse.code() == 0) {
                        ConsumerConnection connections = RemotingSerializable.decode(connectionResponse.body(), ConsumerConnection.class);
                        groupStatus.put("connectionCount", connections.getConnectionSet().size());
                        groupStatus.put("messageModel", String.valueOf(connections.getMessageModel()));
                        groupStatus.put("consumeFromWhere", String.valueOf(connections.getConsumeFromWhere()));
                        var subscription = connections.getSubscriptionTable().get(topic);
                        if (subscription != null) groupStatus.put("tagExpression", subscription.getSubString());
                    }
                }
                status.put("notificationGroup", groupStatus); topics.put(topic, status);
            }
            result.put("topics", topics); result.put("healthy", runtimeResponse.code() == 0); return result;
        } catch (Exception error) {
            result.put("healthy", false); result.put("errorType", error.getClass().getSimpleName()); return result;
        }
    }

    private static Response request(String address, int code, Map<String, String> fields) throws Exception {
        if (!READ_ONLY_CODES.contains(code)) throw new IllegalArgumentException("Non-metadata RocketMQ request forbidden");
        int opaque = ++requestId;
        byte[] header = JSON.writeValueAsBytes(map("code", code, "language", "JAVA", "version", 0, "opaque", opaque, "flag", 0, "extFields", fields, "serializeTypeCurrentRPC", "JSON"));
        String[] parts = address.split(":"); if (parts.length != 2) throw new IllegalArgumentException("Expected host:port");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 3000); socket.setSoTimeout(5000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(4 + header.length); output.writeInt(header.length); output.write(header); output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int length = input.readInt(); if (length < 4 || length > 16_000_000) throw new IOException("Invalid RocketMQ frame length");
            int headerMark = input.readInt(); if ((headerMark >>> 24) != 0) throw new IOException("Unexpected RocketMQ response serialization");
            int headerLength = headerMark & 0xFFFFFF;
            if (headerLength < 1 || headerLength > length - 4) throw new IOException("Invalid RocketMQ header length");
            byte[] responseHeader = input.readNBytes(headerLength); if (responseHeader.length != headerLength) throw new EOFException();
            JsonNode decoded = JSON.readTree(responseHeader);
            if (decoded.path("opaque").asInt() != opaque) throw new IOException("RocketMQ request identity mismatch");
            int bodyLength = length - 4 - headerLength;
            byte[] body = input.readNBytes(bodyLength); if (body.length != bodyLength) throw new EOFException();
            return new Response(decoded.path("code").asInt(), body);
        }
    }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]); return result;
    }
}
