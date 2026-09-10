package com.quanxiaoha.ai.robot.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/test")
@Slf4j
public class testCtroller {

    @Resource
    private ChatClient chatClient;

    @GetMapping(value = "/multimodel",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> test(@RequestParam(value = "message") String message) {
        //构建图片
        Media media = new Media(
                MimeTypeUtils.IMAGE_JPEG,
                new ClassPathResource("images/IMG_20221203_221229.jpg")
        );

        //构建消息
        UserMessage userMessage = UserMessage.builder()
                .text(message)
                .media(media)
                .build();

        //构建提示词
        Prompt prompt = new Prompt(List.of(userMessage));

        //调用模型
        Flux<String> content = chatClient.prompt(prompt).stream().content();

        return content;
    }

}
