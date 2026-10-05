package com.quanxiaoha.xiaohashu.ai.biz.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置
 *
 * <p>ChatModel / EmbeddingModel 由 spring-ai-starter-model-openai 自动装配，
 * 底层走阿里云百炼的 OpenAI 兼容模式（base-url + api-key 见 application-dev.yml）。</p>
 */
@Configuration
@Slf4j
public class ChatClientConfig {

    /**
     * 通用 ChatClient：默认不带任何 advisor，系统提示词在每次调用时按场景传入
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        log.info("## 初始化 AI ChatClient, chatModel: {}", chatModel.getClass().getName());
        return ChatClient.builder(chatModel).build();
    }
}