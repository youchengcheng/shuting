package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.client.NoteContentClient;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.AssistantAnswerDTO;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteRefVO;
import com.quanxiaoha.xiaohashu.ai.biz.service.AssistantService;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteJudgeService;
import com.quanxiaoha.xiaohashu.ai.biz.service.NotePolishService;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteRecallService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 笔记助手编排实现
 */
@Service
@Slf4j
public class AssistantServiceImpl implements AssistantService {

    @Resource
    private NoteRecallService noteRecallService;

    @Resource
    private NoteJudgeService noteJudgeService;

    @Resource
    private NotePolishService notePolishService;

    @Resource
    private NoteContentClient noteContentClient;

    @Resource
    private AiProperties aiProperties;

    @Override
    public AssistantAnswerDTO answer(String query, Integer topN, String extraContext) {
        List<NoteCandidateDTO> matched = retrieveAndJudge(query, topN);
        if (CollUtil.isEmpty(matched)) {
            throw new BizException(ResponseCodeEnum.ASSISTANT_NO_RESULT);
        }
        loadFullContent(matched);

        String polishQuery = buildPolishQuery(query, extraContext);
        String answer = notePolishService.polish(polishQuery, matched);
        return AssistantAnswerDTO.builder()
                .answer(answer)
                .notes(toNoteRefs(matched))
                .build();
    }

    @Override
    public Flux<String> answerStream(String query, Integer topN, String extraContext) {
        List<NoteCandidateDTO> matched = retrieveAndJudge(query, topN);
        if (CollUtil.isEmpty(matched)) {
            return Flux.error(new BizException(ResponseCodeEnum.ASSISTANT_NO_RESULT));
        }
        loadFullContent(matched);

        String polishQuery = buildPolishQuery(query, extraContext);
        return notePolishService.polishStream(polishQuery, matched);
    }

    @Override
    public AssistantAnswerDTO retrieve(String query, Integer topN) {
        List<NoteCandidateDTO> matched = retrieveAndJudge(query, topN);
        if (CollUtil.isEmpty(matched)) {
            throw new BizException(ResponseCodeEnum.ASSISTANT_NO_RESULT);
        }
        loadFullContent(matched);
        return AssistantAnswerDTO.builder()
                .notes(toNoteRefs(matched))
                .build();
    }

    /**
     * 召回 + 判优
     */
    private List<NoteCandidateDTO> retrieveAndJudge(String query, Integer topN) {
        if (AiStringUtils.isBlank(query)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        int limit = topN != null && topN > 0 ? topN : aiProperties.getJudgeTopN();

        List<NoteCandidateDTO> candidates = noteRecallService.recall(query, aiProperties.getRecallNoteLimit());
        if (CollUtil.isEmpty(candidates)) {
            log.info("## 召回为空, query: {}", query);
            return new ArrayList<>();
        }
        List<NoteCandidateDTO> matched = noteJudgeService.judge(query, candidates, limit);
        log.info("## 判优完成, 候选: {}, 达标: {}", candidates.size(), matched.size());
        return matched;
    }

    /**
     * 回 Cassandra 取命中笔记的正文
     */
    private void loadFullContent(List<NoteCandidateDTO> notes) {
        for (NoteCandidateDTO note : notes) {
            if (AiStringUtils.isNotBlank(note.getContent())) {
                continue;
            }
            note.setContent(noteContentClient.findContent(note.getContentUuid()));
        }
    }

    private String buildPolishQuery(String query, String extraContext) {
        if (AiStringUtils.isBlank(extraContext)) {
            return query;
        }
        return "以下是本次对话的历史上下文（供理解指代，不要直接复述）：\n" + extraContext
                + "\n\n用户当前的问题：\n" + query;
    }

    private List<NoteRefVO> toNoteRefs(List<NoteCandidateDTO> notes) {
        List<NoteRefVO> refs = new ArrayList<>(notes.size());
        for (NoteCandidateDTO note : notes) {
            refs.add(NoteRefVO.builder()
                    .noteId(note.getNoteId() == null ? null : String.valueOf(note.getNoteId()))
                    .title(note.getTitle())
                    .topicName(note.getTopicName())
                    .creatorId(note.getCreatorId() == null ? null : String.valueOf(note.getCreatorId()))
                    .score(note.getScore())
                    .judgeScore(note.getJudgeScore())
                    .reason(note.getJudgeReason())
                    .build());
        }
        return refs;
    }
}