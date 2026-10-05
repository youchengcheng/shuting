package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;
import com.quanxiaoha.xiaohashu.ai.biz.prompts.NotePrompts;
import com.quanxiaoha.xiaohashu.ai.biz.service.NotePolishService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 笔记润色服务实现
 */
@Service
@Slf4j
public class NotePolishServiceImpl implements NotePolishService {

    @Resource
    private ChatClient chatClient;

    @Resource
    private AiProperties aiProperties;

    @Override
    public String polish(String query, List<NoteCandidateDTO> notes) {
        if (CollUtil.isEmpty(notes)) {
            throw new BizException(ResponseCodeEnum.ASSISTANT_NO_RESULT);
        }
        String content = chatClient.prompt()
                .system(NotePrompts.POLISH_SYSTEM)
                .user(NotePrompts.buildPolishUserPrompt(query, buildNotesText(notes)))
                .call()
                .content();
        if (AiStringUtils.isBlank(content)) {
            throw new BizException(ResponseCodeEnum.AI_SERVICE_ERROR);
        }
        return AiStringUtils.truncate(content, aiProperties.getPolishMaxChars() * 4);
    }

    @Override
    public Flux<String> polishStream(String query, List<NoteCandidateDTO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return Flux.error(new BizException(ResponseCodeEnum.ASSISTANT_NO_RESULT));
        }
        return chatClient.prompt()
                .system(NotePrompts.POLISH_SYSTEM)
                .user(NotePrompts.buildPolishUserPrompt(query, buildNotesText(notes)))
                .stream()
                .content();
    }

    private String buildNotesText(List<NoteCandidateDTO> notes) {
        int maxChars = Math.max(500, aiProperties.getNoteMaxContentChars());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            NoteCandidateDTO note = notes.get(i);
            sb.append("【笔记").append(i + 1).append("】ID: ").append(note.getNoteId())
                    .append("  标题：").append(AiStringUtils.isBlank(note.getTitle()) ? "无标题" : note.getTitle())
                    .append('\n');
            sb.append("正文：\n")
                    .append(AiStringUtils.truncate(note.getContent(), maxChars))
                    .append("\n\n");
        }
        return sb.toString();
    }
}