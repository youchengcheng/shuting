package com.quanxiaoha.ai.robot.advisor;

import com.quanxiaoha.ai.robot.domain.dos.ChatMessageDO;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMessageMapper;
import com.quanxiaoha.ai.robot.model.vo.chat.AiChatReqVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/*
* 自定义打印流式日志 Advisor
* */
@Slf4j
public class CustomStreamLoggerAndMessage2DBAdvisor implements StreamAdvisor {

    private final ChatMessageMapper chatMessageMapper;
    private final AiChatReqVO aiChatReqVO;
    private final TransactionTemplate transactionTemplate;

    public CustomStreamLoggerAndMessage2DBAdvisor(ChatMessageMapper chatMessageMapper,
                                                  AiChatReqVO aiChatReqVO,
                                                  TransactionTemplate transactionTemplate){
        this.chatMessageMapper = chatMessageMapper;
        this.aiChatReqVO = aiChatReqVO;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        // 对话 UUID
        String chatUuid = aiChatReqVO.getChatId();
        // 用户消息
        String userMessage = aiChatReqVO.getMessage();

        Flux<ChatClientResponse> chatClientResponseFlux = streamAdvisorChain.nextStream(chatClientRequest);

        //创建AI流式回答聚合容器（线程安全）
        AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());

        //创建AI流式推理过程聚合容器（线程安全）
        AtomicReference<StringBuilder> fullReasoning = new AtomicReference<>(new StringBuilder());

        //返回处理后的流
        return chatClientResponseFlux
                .doOnNext(response -> {//流接收到数据机进行处理
                    // 获取响应
                    ChatResponse chatResponse = response.chatResponse();

                    // 判空
                    if (Objects.nonNull(chatResponse) && Objects.nonNull(chatResponse.getResult())) {
                        // 获取 AI 回复的消息
                        AssistantMessage message = chatResponse.getResult().getOutput();

                        // 获取推理内容（如果存在，做 null 安全处理避免 NPE）
                        Object reasoningObj = message.getMetadata().get("reasoningContent");
                        String reasoningChunk = reasoningObj != null ? reasoningObj.toString() : "";

                        // 逐块收集正式回答
                        String chunk = message.getText();

                        if (StringUtils.isNotBlank(reasoningChunk)) {
                            //log.info("## reasoning chunk: {}", reasoningChunk);
                            fullReasoning.get().append(reasoningChunk);
                        }

                        // 若 chunk 块不为空，则追加到 fullContent 中
                        if (StringUtils.isNotBlank(chunk)) {
                            //log.info("## chunk: {}", chunk);
                            fullContent.get().append(chunk);
                        }
                    }
                })
                .doOnComplete(() -> {
                    //流完成后打印完整回答
                    String completeResponse = fullContent.get().toString();
                    log.info("\n==== FULL AI RESPONSE ====\n{}\n========================", completeResponse);

                    //流完成后打印完整推理过程
                    String completeReasoning = fullReasoning.get().toString();
                    log.info("\n==== FULL RESONING RESPONSE ====\n{}\n========================", completeReasoning);


                    //开启编程式事物
                    transactionTemplate.execute(status -> {
                        try {
                            //1.存储用户信息
                            chatMessageMapper.insert(ChatMessageDO.builder()
                                    .chatUuid(chatUuid)
                                    .content(userMessage)
                                    .role(MessageType.USER.getValue())//用户
                                    .createTime(LocalDateTime.now())
                                    .build());

                            //2.存储ai回答
                            chatMessageMapper.insert(ChatMessageDO.builder()
                                    .chatUuid(chatUuid)
                                    .content(completeResponse)
                                    .role(MessageType.ASSISTANT.getValue())//ai回答
                                    .reasoningContent(completeReasoning)//推理过程
                                    .createTime(LocalDateTime.now())
                                    .build());

                            return true;
                        }catch (Exception e) {
                            //标记事物为回滚
                            status.setRollbackOnly();
                            log.error("",e);
                        }
                        return false;
                    });
                })
                .doOnError(error -> {
                    //出错时打印已收集的部分
                    String partialResponse = fullReasoning.get().toString();
                    log.error("## Stream 流出现错误，已收集回答如下: {}", partialResponse, error);
                });
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        //值越小，越先执行
        return 99;
    }
}
