package com.quanxiaoha.ai.robot.advisor;

import com.quanxiaoha.ai.robot.model.dto.SearchResultDTO;
import com.quanxiaoha.ai.robot.service.SearXNGService;
import com.quanxiaoha.ai.robot.service.SearchResultContentFetcherService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/*
* 联网搜索 Advisor
* */
@Slf4j
public class NetworkSearchAdvisor implements StreamAdvisor {

    private final SearXNGService searXNGService;
    private final SearchResultContentFetcherService searchResultContentFetcherService;

    /**
     * 联网搜索提示词模板
     */
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            ## 用户问题
            {question}
            
            ## 上下文
            上下文信息如下，由以下符号包围:
            ---------------------
            {context}
            ---------------------
            
            请根据上下文内容来回复用户：
            
            ## 任务要求
            
            1. 综合分析上下文内容，提取与用户问题直接相关的核心信息
            2. 特别关注匹配度较高的结果
            3. 对矛盾信息进行交叉验证，优先采用多个来源证实的信息
            4. 请避免使用诸如 “根据上下文……” 或 “所提供的信息……” 这类表述
            5. 当上下文内容不足或存在知识缺口时，再考虑使用本身已拥有的先验知识
            6. 在关键信息后标注对应的来源，包含编号与页面跳转链接，格式如 [<a href="https://www.douyin.com/shipin/7532759629252544512" target="_blank">来源1</a>]
            """);

    public NetworkSearchAdvisor(SearXNGService searXNGService, SearchResultContentFetcherService searchResultContentFetcherService) {
        this.searXNGService = searXNGService;
        this.searchResultContentFetcherService = searchResultContentFetcherService;
    }


    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        //获取用户输入的提示词
        Prompt prompt = chatClientRequest.prompt();
        UserMessage userMessage = prompt.getUserMessage();

        //调用SearXNG 获取搜索结果
        List<SearchResultDTO> search = searXNGService.search(userMessage.getText());

        //并发请求，获取搜索结果页面的内容
        CompletableFuture<List<SearchResultDTO>> resultsFuture = searchResultContentFetcherService.batchFetch(search, 7, TimeUnit.SECONDS);

        //阻塞等待所有搜索请求结束
        List<SearchResultDTO> results = resultsFuture.join();

        //过滤获取失败的内容
        List<SearchResultDTO> successfulResults = results.stream()
                .filter(result -> StringUtils.isNotBlank(result.getContent()))
                .toList();

        //构建搜索结果上下文信息
        String searchContext = buildContext(successfulResults);

        //填充提示词占位符，转换为prompt提示词对象
        //chatClientRequest.prompt().getOptions()保留之前的prompt配置信息
        Prompt newPrompt = DEFAULT_PROMPT_TEMPLATE.create(Map.of("question", userMessage.getText(), "context", searchContext),
                chatClientRequest.prompt().getOptions());

        log.info("## 重新构建的增强提示词: {}", newPrompt.getUserMessage().getText());

        //重新构建chatclientrequest，设置重新构建的“增强提示词”
        ChatClientRequest newChatClientRequest = ChatClientRequest.builder()
                .prompt(newPrompt)
                .build();

        return streamAdvisorChain.nextStream(newChatClientRequest);
    }

    /*
     * 构建上下文
     * */
    private String buildContext(List<SearchResultDTO> successfulResults) {
        int i = 1;
        StringBuilder builder = new StringBuilder();
        for (SearchResultDTO searchResultDTO : successfulResults) {
            builder.append(String.format("""
                        ### 来源 %s | 相关性: %s
                        - 页面链接: %s 
                        - 页面文本:
                        %s
                        \n
                        """,i,searchResultDTO.getScore(),searchResultDTO.getUrl(),searchResultDTO.getContent()));
            i++;
        }
        return builder.toString();
    }



    @Override
    public String getName() {
        // 获取类名称
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 1; // order 值越小，越先执行
    }
}
