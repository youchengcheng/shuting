package com.quanxiaoha.ai.robot.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.google.common.collect.Lists;
import com.quanxiaoha.ai.robot.advisor.CustomChatMemoryAdvisor;
import com.quanxiaoha.ai.robot.advisor.CustomStreamLoggerAndMessage2DBAdvisor;
import com.quanxiaoha.ai.robot.advisor.NetworkSearchAdvisor;
import com.quanxiaoha.ai.robot.aspect.ApiOperationLog;
import com.quanxiaoha.ai.robot.config.ChatClientConfig;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMessageMapper;
import com.quanxiaoha.ai.robot.model.vo.chat.*;
import com.quanxiaoha.ai.robot.service.ChatService;
import com.quanxiaoha.ai.robot.service.SearXNGService;
import com.quanxiaoha.ai.robot.service.SearchResultContentFetcherService;
import com.quanxiaoha.ai.robot.tools.DateTimeTools;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.*;


/**
 * 对话
 **/
@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatController {

    @Resource
    private ChatService chatService;
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private SearXNGService searXNGService;
    @Resource
    private SearchResultContentFetcherService searchResultContentFetcherService;
    @Resource
    private ChatClient chatClient;


    /*
    * 新建会话
    * */
    @ApiOperationLog(description = "新建会话")
    @PostMapping("/new")
    public Response<?> newChat(@Valid @RequestBody NewChatReqVO newChatReqVO) {
        return chatService.newChat(newChatReqVO);
    }

    /*
    * 查询对话历史消息
    * */
    @PostMapping("/message/list")
    @ApiOperationLog(description = "查询对话历史消息")
    public PageResponse<FindChatHistoryMessagePageListRspVO> findChatMessagePageList(
            @RequestBody @Validated FindChatHistoryMessagePageListReqVO findChatHistoryMessagePageListReqVO) {
        return chatService.findChatHistoryMessagePageList(findChatHistoryMessagePageListReqVO);
    }

    /*
    * 查询历史对话
    * */
    @PostMapping("/list")
    @ApiOperationLog(description = "查询历史对话")
    public PageResponse<FindChatHistoryPageListRspVO> findChatHistoryPageList(
            @RequestBody @Validated FindChatHistoryPageListReqVO findChatHistoryPageListReqVO) {
        return chatService.findChatHistoryPageList(findChatHistoryPageListReqVO);
    }

    /*
    * 重命名对话摘要
    * */
    @PostMapping("/summary/rename")
    @ApiOperationLog(description = "重命名对话摘要")
    public Response<?> renameChatSummary(@RequestBody @Validated RenameChatReqVO renameChatReqVO) {
        return chatService.renameChatSummary(renameChatReqVO);
    }

    /*
    * 删除对话
    * */
    @PostMapping("/delete")
    @ApiOperationLog(description = "删除对话")
    public Response<?> deleteChat(@RequestBody @Validated DeleteChatReqVO deleteChatReqVO) {
        return chatService.deleteChat(deleteChatReqVO);
    }

    /**
     * 流式对话
     */
    @PostMapping(value = "/completion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationLog(description = "流式对话")
    public Flux<AIResponse> chat(@RequestBody @Validated AiChatReqVO aiChatReqVO) {
        // 用户消息
        String Message = aiChatReqVO.getMessage();
        // 模型名称
        String modelName = aiChatReqVO.getModelName();
        // 温度值
        Double temperature = aiChatReqVO.getTemperature();
        // 是否开启联网搜索
        boolean networkSearch = aiChatReqVO.getNetworkSearch();

        boolean isThink = Boolean.TRUE.equals(aiChatReqVO.getThink());

        // 动态设置调用的模型名称、温度值
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient
                .prompt()
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .extraBody(Map.of("enable_thinking",isThink))
                        .build())
                .tools(new DateTimeTools())
                .user(Message); // 用户提示词


        //advisor集合
        List<Advisor> advisors = Lists.newArrayList();

        //是否开启了互联网搜索
        if(networkSearch) {
            advisors.add(new NetworkSearchAdvisor(searXNGService,searchResultContentFetcherService));
        }else {
            // 添加自定义对话记忆 Advisor（以最新的 50 条消息作为记忆）
            advisors.add(new CustomChatMemoryAdvisor(chatMessageMapper, aiChatReqVO, 50));
        }

        //添加自定义打印流式对话日志advisor
        advisors.add(new CustomStreamLoggerAndMessage2DBAdvisor(chatMessageMapper,aiChatReqVO,transactionTemplate));

        //应用advisor集合
        chatClientRequestSpec.advisors(advisors);

        // 流式输出
        // doFinally：Flux 完成（onComplete / onError / cancel）时，统一 remove ThreadLocal，避免线程复用污染。
        return chatClientRequestSpec
                .stream()
                .chatResponse()
                .mapNotNull(chatResponse -> {// 构建返参 AIResponse
                    if (Objects.nonNull(chatResponse) && Objects.nonNull(chatResponse.getResult())) {
                        // 获取 AI 回复的消息
                        AssistantMessage message = chatResponse.getResult().getOutput();

                        // 获取正式回答
                        String text = message.getText();

                        // 获取推理内容（如果存在）
                        Object reasoningObj = message.getMetadata().get("reasoningContent");
                        String reasoningContent = reasoningObj != null ? reasoningObj.toString() : "";

                        // 构建响应对象
                        if (StringUtils.isNotBlank(reasoningContent)) {
                            // 返回思考过程
                            return AIResponse.builder().reasoning(reasoningContent).build();
                        }

                        return AIResponse.builder().v(text).build();
                    }
                    return null;
                });

    }

}

