package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.model.vo.AssistantSearchRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatMessageRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatRspVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 对话服务
 */
public interface ChatService {

    /**
     * 发一条消息，返回 AI 回答（同步）
     */
    AssistantSearchRspVO chat(Long userId, ChatReqVO reqVO);

    /**
     * 发一条消息，流式返回 AI 回答
     */
    Flux<String> chatStream(Long userId, ChatReqVO reqVO);

    /**
     * 会话列表
     */
    List<ChatRspVO> listChats(Long userId);

    /**
     * 会话消息列表（按时间正序）
     */
    List<ChatMessageRspVO> listMessages(Long userId, String chatUuid);

    /**
     * 删除会话
     */
    void deleteChat(Long userId, String chatUuid);
}