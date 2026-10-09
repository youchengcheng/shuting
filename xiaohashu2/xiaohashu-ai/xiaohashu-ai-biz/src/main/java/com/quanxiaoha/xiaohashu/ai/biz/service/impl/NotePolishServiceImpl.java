package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;
import com.quanxiaoha.xiaohashu.ai.biz.prompts.NotePrompts;
import com.quanxiaoha.xiaohashu.ai.biz.service.NotePolishService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import com.quanxiaoha.xiaohashu.ai.biz.util.DateTimeTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalTime;
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

    @Override
    public String answerDirectly(String query) {
        String content = chatClient.prompt()
                .system(NotePrompts.DIRECT_SYSTEM)
                .user(NotePrompts.buildDirectUserPrompt(query))
                .tools(new DateTimeTools())
                .call()
                .content();
        if (AiStringUtils.isBlank(content)) {
            throw new BizException(ResponseCodeEnum.AI_SERVICE_ERROR);
        }
        return AiStringUtils.truncate(content, aiProperties.getPolishMaxChars() * 4);
    }

    @Override
    public Flux<String> answerDirectlyStream(String query) {
        return chatClient.prompt()
                .system(NotePrompts.DIRECT_SYSTEM)
                .user(NotePrompts.buildDirectUserPrompt(query))
                .tools(new DateTimeTools())
                .stream()
                .content();
    }

    private String buildNotesText(List<NoteCandidateDTO> notes) {
        int perNoteMax = Math.max(500, aiProperties.getNoteMaxContentChars());
        int totalBudget = Math.max(perNoteMax, aiProperties.getPolishMaxInputChars());
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < notes.size(); i++) {
            NoteCandidateDTO note = notes.get(i);
            int remaining = totalBudget - used;
            if (remaining <= 200) {
                log.info("## 润色输入已达长度上限，剩余 {} 篇笔记不再展开", notes.size() - i);
                break;
            }
            String content = AiStringUtils.truncate(note.getContent(), Math.min(perNoteMax, remaining));
            String block = "【笔记" + (i + 1) + "】ID: " + note.getNoteId()
                    + "  标题：" + (AiStringUtils.isBlank(note.getTitle()) ? "无标题" : note.getTitle())
                    + "\n正文：\n" + content + "\n\n";
            sb.append(block);
            used += block.length();
        }
        return sb.toString();
    }
}
