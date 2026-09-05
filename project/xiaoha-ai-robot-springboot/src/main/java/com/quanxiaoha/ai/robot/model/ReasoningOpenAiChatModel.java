/*
package com.quanxiaoha.ai.robot.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

*/
/**
 * 自定义的 OpenAI 兼容 ChatModel，用于正确返回流式推理内容 (reasoning_content)。
 *
 * <p>背景：Spring AI 2.0.0 的 {@code OpenAiChatModel} 在流式响应中，
 * {@code ChunkMerger.chunkToChatCompletion()} 只从 delta 复制了 content/refusal/toolCalls，
 * 没有把 OpenAI 兼容服务（阿里云百炼 / DeepSeek 等）在 delta 中返回的
 * {@code reasoning_content} 透传到最终的 ChatCompletionMessage，
 * 导致 {@code message.getMetadata().get("reasoningContent")} 始终为空。
 *
 * <p>本实现直接使用 OkHttp 调用 OpenAI 兼容的 {@code /chat/completions} (stream=true) 接口，
 * 逐行解析 SSE 数据，手动提取 {@code delta.reasoning_content} 与 {@code delta.content}，
 * 并以 {@code reasoningContent} 元数据键写入 AssistantMessage（键名与
 * Spring AI {@code OpenAiChatModel.REASONING_CONTENT} 保持一致），
 * 从而让上层 Advisor / Controller 能够正确收到推理内容，且不影响现有的
 * 会话记忆、联网搜索、落库等 Advisor 逻辑。
 *//*

@Slf4j
public class ReasoningOpenAiChatModel implements ChatModel {

    */
/** 推理内容在 AssistantMessage metadata 中的键名，与 Spring AI OpenAiChatModel 保持一致。 *//*

    private static final String REASONING_CONTENT = "reasoningContent";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    */
/**
     * 在调用前由 ChatController 写入"用户是否要求开启深度思考"的原始意图。
     * 这样 buildRequestBody 可以直接以真实意图为准构建上游参数，不再依赖 "body 里是否有 enable_thinking=true" 反推（不稳）。
     * 流式响应结束（或异常时）ChatController 负责 remove，避免线程池线程复用污染。
     *//*

    public static final ThreadLocal<Boolean> THINK_ENABLED_HOLDER = new ThreadLocal<>();

    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReasoningOpenAiChatModel(String baseUrl, String apiKey, OkHttpClient okHttpClient) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 当前业务仅使用流式对话，非流式调用不支持
        throw new UnsupportedOperationException("ReasoningOpenAiChatModel 仅支持流式调用 stream(Prompt)");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 1. 构建请求体
        ObjectNode requestBody = buildRequestBody(prompt);

        // 2. 拼接 chat completions 接口地址（application-dev.yml 中 baseUrl 已包含 /v1）
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

        log.info("## ReasoningOpenAiChatModel 流式请求: model={}, messages={}, url={}",
                requestBody.has("model") ? requestBody.get("model").asText() : null,
                requestBody.path("messages").size(),
                url);

        // 3. 异步发起请求，逐行解析 SSE
        return Flux.create(sink -> {
            Call call = okHttpClient.newCall(request);
            // 订阅被取消时，取消 HTTP 请求，避免连接泄漏
            sink.onCancel(() -> call.cancel());
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("## ReasoningOpenAiChatModel 请求失败", e);
                    sink.error(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response resp = response) {
                        ResponseBody body = resp.body();
                        if (!resp.isSuccessful()) {
                            String errBody = body != null ? body.string() : "";
                            sink.error(new RuntimeException("OpenAI 兼容接口请求失败: HTTP "
                                    + resp.code() + " " + resp.message() + ", body=" + errBody));
                            return;
                        }
                        if (body == null) {
                            sink.error(new RuntimeException("OpenAI 兼容接口响应体为空"));
                            return;
                        }
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                // 跳过空行、注释行 (:)、event: 等非数据行
                                continue;
                            }
                            String data = line.substring(5).trim(); // 去掉 "data:" 前缀
                            if (data.isEmpty() || "[DONE]".equals(data)) {
                                continue;
                            }
                            handleSseData(data, sink);
                        }
                        sink.complete();
                    } catch (Exception e) {
                        log.error("## ReasoningOpenAiChatModel 解析响应失败", e);
                        sink.error(e);
                    }
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    */
/**
     * 解析单条 SSE 的 data JSON，提取推理内容与正式回答并下发。
     *//*

    private void handleSseData(String data, FluxSink<ChatResponse> sink) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode delta = choices.get(0).path("delta");

            String reasoning = textual(delta.get("reasoning_content"));
            String content = textual(delta.get("content"));

            // 推理内容与正式回答通常出现在不同阶段，分开发送，
            // 避免上层 Controller 中 "先判断 reasoning 再返回 content" 的逻辑互相覆盖。
            if (StringUtils.isNotBlank(reasoning)) {
                sink.next(buildChatResponse("", reasoning));
            }
            if (StringUtils.isNotBlank(content)) {
                sink.next(buildChatResponse(content, ""));
            }
        } catch (Exception e) {
            log.warn("## 解析 SSE 数据失败，跳过该行: {}", data, e);
        }
    }

    */
/**
     * 安全读取文本节点：仅当节点存在且为文本类型时返回其值，否则返回空串。
     * （避免 delta 里 "content": null 时被 asText() 误判为 "null" 字符串）
     *//*

    private String textual(JsonNode node) {
        return (node != null && node.isTextual()) ? node.asText() : "";
    }

    private ChatResponse buildChatResponse(String content, String reasoning) {
        Map<String, Object> properties = new HashMap<>();
        // 始终写入 reasoningContent 键（可能为空串），保证上层 get("reasoningContent") 不会 NPE
        properties.put(REASONING_CONTENT, reasoning);
        AssistantMessage message = AssistantMessage.builder()
                .content(content)
                .properties(properties)
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    */
/**
     * 根据 Prompt（含 Advisor 增强后的历史消息）与运行时 options 构建请求体。
     * 
     * 深度思考控制逻辑（严格对齐阿里云百炼官方文档 2026-08-18 版）：
     *  - 混合思考模型（qwen3.8-max / qwen3.7-max / deepseek-v4-pro 等）默认开启思考模式；
     *    显式关闭必须传 "enable_thinking": false（注意：不传或 remove 掉该字段，上游会用默认 true，反而开启！）。
     *  - MiniMax 系列：关闭必须传 "thinking": "disabled"。
     *  - thinking_budget=0 作为额外关闭保险（关闭时传，开启时不传）；
     *    reasoning_effort="high" 作为开启时增强（百炼 Qwen/DeepSeek 系列识别）。
     *//*

    private ObjectNode buildRequestBody(Prompt prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("stream", true);

        ChatOptions options = prompt.getOptions();
        if (options != null) {
            if (options.getModel() != null) {
                body.put("model", options.getModel());
            }
            if (options.getTemperature() != null) {
                body.put("temperature", options.getTemperature());
            }
            // extraBody 仅存在于 OpenAiChatOptions 上（对应 OpenAI 兼容的额外请求体参数，如 enable_thinking）
            if (options instanceof OpenAiChatOptions openAiOptions) {
                Map<String, Object> extraBody = openAiOptions.getExtraBody();
                if (extraBody != null && !extraBody.isEmpty()) {
                    extraBody.forEach(body::putPOJO);
                }
            } else {
                // 说明：Spring AI Advisor 链（CustomChatMemoryAdvisor 等）会把 Prompt.options 从 OpenAiChatOptions
                // 重写成 DefaultChatOptions，导致这里 instanceof 失败；但我们已通过 THINK_ENABLED_HOLDER ThreadLocal
                // 把深度思考意图作为真源注入，model/temperature 等通用字段仍能从 ChatOptions 接口读取，
                // 因此此处不影响功能，降级为 DEBUG 避免控制台反复告警刷日志。
                log.debug("## 当前 Prompt options 非 OpenAiChatOptions ({}), 跳过 extraBody 读取（深度思考已由 ThreadLocal 真源接管）",
                        options.getClass().getName());
            }
        }

        // 以用户真实意图为准覆盖上游参数（不再反推 body 中零散字段）
        final Boolean intent = THINK_ENABLED_HOLDER.get();
        final boolean thinkEnabled = Boolean.TRUE.equals(intent); // null 视为 false

        if (thinkEnabled) {
            // —— 开启深度思考 ——
            body.put("enable_thinking", true);                    // 阿里云百炼/通用：开启
            body.put("reasoning_effort", "high");                 // 百炼 Qwen/DeepSeek：开启强度
            body.remove("disable_reasoning");                     // 避免与启用开关冲突
            body.remove("thinking_budget");                       // 预算=0 会关闭，开启时不传
            // 注意：不要设置 "thinking" 字段。百炼统一兼容接口里它要求是 JSON 对象，
            // 传字符串（如 "disabled"/"adaptive"）会直接 HTTP 400 'thinking' must be json_object。
            body.remove("thinking");
        } else {
            // —— 关闭深度思考（百炼官方：对混合模型显式传 enable_thinking=false 就关闭）——
            body.put("enable_thinking", false);                   // 百炼/豆包/DeepSeek：官方显式关闭
            body.put("thinking_budget", 0);                       // 额外保险：推理 token 预算归零
            body.remove("reasoning_effort");                      // reasoning_effort 仅开启模式有效（high），关闭时移除避免误识别
            body.remove("disable_reasoning");                     // 非标准字段，避免未知副作用
            // 同上：百炼兼容模式不要传字符串 "thinking" 字段（会 400）。MiniMax 关闭语义不在这一层实现。
            body.remove("thinking");
        }

        // messages
        ArrayNode messages = body.putArray("messages");
        for (Message message : prompt.getInstructions()) {
            ObjectNode msg = messages.addObject();
            msg.put("role", message.getMessageType().getValue());
            String text = message.getText();
            msg.put("content", text != null ? text : "");
        }

        // 调试日志（INFO 级别）：打印最终发给上游的 body 摘要
        log.info("## 发往上游 body 摘要: thinkEnabled={}, enable_thinking={}, reasoning_effort={}, thinking_budget={}, model={}, stream={}, messagesLen={}",
                thinkEnabled,
                body.path("enable_thinking").asBoolean(false),
                body.path("reasoning_effort").isMissingNode() ? "<missing>" : body.path("reasoning_effort").asText(),
                body.path("thinking_budget").isMissingNode() ? "<missing>" : body.path("thinking_budget").asLong(-1),
                body.path("model").asText("?"),
                body.path("stream").asBoolean(),
                body.path("messages").size());

        return body;
    }
}
*/
