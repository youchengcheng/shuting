package com.quanxiaoha.xiaohashu.ai.biz.controller;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.AssistantSearchRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatMessageRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.service.ChatService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiExceptionUtils;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 多轮对话
 */
@RestController
@RequestMapping("/ai/chat")
@Slf4j
public class ChatController {

    @Resource
    private ChatService chatService;

    /**
     * 发消息（同步）
     */
    @PostMapping("/send")
    @ApiOperationLog(description = "AI 对话")
    public Response<AssistantSearchRspVO> send(@Validated @RequestBody ChatReqVO reqVO) {
        return Response.success(chatService.chat(LoginUserContextHolder.getUserId(), reqVO));
    }

    /**
     * 发消息（SSE 流式）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatReqVO reqVO) {
        try {
            if (reqVO == null || AiStringUtils.isBlank(reqVO.getQuery())) {
                return Flux.just("[出错了] 提问内容不能为空");
            }
            return chatService.chatStream(LoginUserContextHolder.getUserId(), reqVO);
        } catch (Exception e) {
            log.warn("## AI 对话流式接口异常", e);
            return Flux.just("[出错了] " + AiExceptionUtils.resolveMessage(e));
        }
    }

    /**
     * 会话列表
     */
    @GetMapping("/list")
    public Response<List<ChatRspVO>> list() {
        return Response.success(chatService.listChats(LoginUserContextHolder.getUserId()));
    }

    /**
     * 会话消息列表
     */
    @GetMapping("/messages")
    public Response<List<ChatMessageRspVO>> messages(@RequestParam("chatUuid") String chatUuid) {
        return Response.success(chatService.listMessages(LoginUserContextHolder.getUserId(), chatUuid));
    }

    /**
     * 删除会话
     */
    @PostMapping("/delete")
    @ApiOperationLog(description = "删除 AI 会话")
    public Response<?> delete(@RequestParam("chatUuid") String chatUuid) {
        chatService.deleteChat(LoginUserContextHolder.getUserId(), chatUuid);
        return Response.success();
    }
}