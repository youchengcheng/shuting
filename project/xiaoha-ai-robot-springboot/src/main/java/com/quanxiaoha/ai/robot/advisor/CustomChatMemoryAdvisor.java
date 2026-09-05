package com.quanxiaoha.ai.robot.advisor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.quanxiaoha.ai.robot.domain.dos.ChatMessageDO;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMessageMapper;
import com.quanxiaoha.ai.robot.model.vo.chat.AiChatReqVO;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/*
* 自定义会话记忆
* */
@Slf4j
public class CustomChatMemoryAdvisor implements StreamAdvisor {

    private final ChatMessageMapper chatMessageMapper;
    private final AiChatReqVO aiChatReqVO;
    private final int limit;

    public CustomChatMemoryAdvisor(ChatMessageMapper chatMessageMapper, AiChatReqVO aiChatReqVO, int limit) {
        this.chatMessageMapper = chatMessageMapper;
        this.aiChatReqVO = aiChatReqVO;
        this.limit = limit;
    }

    @Override
    public int getOrder() {
        return 2; // order 值越小，越先执行
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        log.info("## 自定义聊天记忆 Advisor...");

        //对话uuid
        String chatId = aiChatReqVO.getChatId();

        //查询数据库拉取最新的聊天记录
        List<ChatMessageDO> message = chatMessageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getChatUuid, chatId)//查询指定对话下的聊天记录
                .orderByDesc(ChatMessageDO::getCreateTime)//查询最新的消息
                .last(String.format("LIMIT %d", limit)));//仅查询limit条

        //按发布时间升序排列
        List<ChatMessageDO> sortedMessage = message.stream()
                .sorted(Comparator.comparing(ChatMessageDO::getCreateTime))//升序排列
                .toList();

        //所有消息
        List<Message> messageList = Lists.newArrayList();

        //将数据库记录转换为对应类型的消息
        for (ChatMessageDO chatMessageDO : sortedMessage) {
            //消息类型
            String type = chatMessageDO.getRole();
            if(Objects.equals(type,MessageType.USER.getValue())){
                Message userMessage = new UserMessage(chatMessageDO.getContent());//用户消息
                messageList.add(userMessage);
            } else if (Objects.equals(type,MessageType.ASSISTANT.getValue())) {
                Message assistantMessage = new AssistantMessage(chatMessageDO.getContent());//ai消息
                messageList.add(assistantMessage);
            }
        }

        //除了记忆消息，还需要添加当前用户消息
        messageList.addAll(chatClientRequest.prompt().getInstructions());

        //构建一个新的chatClientRequest请求对象
        ChatClientRequest request = chatClientRequest
                .mutate()//复制一份prompt
                //chatClientRequest.prompt().mutate().messages(messageList).build()重新构建prompt消息
                .prompt(chatClientRequest.prompt().mutate().messages(messageList).build())
                .build();

        return streamAdvisorChain.nextStream(request);
    }

}
