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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    @Resource(name = "aiTaskExecutor")
    private ThreadPoolTaskExecutor aiTaskExecutor;

    @Override
    public AssistantAnswerDTO answer(String query, String extraContext) {
        requireQuery(query);

        List<NoteCandidateDTO> matched = retrieveAndJudgeQuietly(query);
        String polishQuery = buildPolishQuery(query, extraContext);

        // 站内没有检索到相关笔记时不再报错，直接以 AI 助手身份回答
        if (CollUtil.isEmpty(matched)) {
            log.info("## 站内未命中相关笔记，降级为 AI 直答, query: {}", query);
            return AssistantAnswerDTO.builder()
                    .answer(notePolishService.answerDirectly(polishQuery))
                    .notes(Collections.emptyList())
                    .fromNotes(false)
                    .build();
        }

        loadFullContent(matched);
        String answer = notePolishService.polish(polishQuery, matched);
        return AssistantAnswerDTO.builder()
                .answer(answer)
                .notes(toNoteRefs(matched))
                .fromNotes(true)
                .build();
    }

    @Override
    public Flux<String> answerStream(String query, String extraContext) {
        requireQuery(query);

        List<NoteCandidateDTO> matched = retrieveAndJudgeQuietly(query);
        String polishQuery = buildPolishQuery(query, extraContext);

        if (CollUtil.isEmpty(matched)) {
            log.info("## 站内未命中相关笔记，降级为 AI 直答(流式), query: {}", query);
            return notePolishService.answerDirectlyStream(polishQuery);
        }

        loadFullContent(matched);
        return notePolishService.polishStream(polishQuery, matched);
    }

    @Override
    public AssistantAnswerDTO retrieve(String query) {
        requireQuery(query);

        List<NoteCandidateDTO> matched = retrieveAndJudge(query);
        if (CollUtil.isEmpty(matched)) {
            throw new BizException(ResponseCodeEnum.ASSISTANT_NO_RESULT);
        }
        loadFullContent(matched);
        return AssistantAnswerDTO.builder()
                .notes(toNoteRefs(matched))
                .fromNotes(true)
                .build();
    }

    private void requireQuery(String query) {
        if (AiStringUtils.isBlank(query)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
    }

    /**
     * 召回 + 判优，但检索侧的异常不向上抛
     *
     * <p>索引没建好、向量库连不上时，按「没找到相关笔记」处理，
     * 由上层降级为 AI 直接回答，保证用户始终能得到一个回答。</p>
     */
    private List<NoteCandidateDTO> retrieveAndJudgeQuietly(String query) {
        try {
            return retrieveAndJudge(query);
        } catch (BizException e) {
            log.warn("## 站内检索失败，降级为 AI 直答, errorCode: {}, message: {}",
                    e.getErrorCode(), e.getErrorMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("## 站内检索异常，降级为 AI 直答", e);
            return Collections.emptyList();
        }
    }

    /**
     * 召回 + 判优
     */
    private List<NoteCandidateDTO> retrieveAndJudge(String query) {
        if (AiStringUtils.isBlank(query)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        List<NoteCandidateDTO> candidates = noteRecallService.recall(query, aiProperties.getRecallNoteLimit());
        if (CollUtil.isEmpty(candidates)) {
            log.info("## 召回为空, query: {}", query);
            return new ArrayList<>();
        }
        // 不再按「最多引用几篇」截断：所有达到判优阈值的笔记都会参与润色
        List<NoteCandidateDTO> matched = noteJudgeService.judge(query, candidates);
        log.info("## 判优完成, 候选: {}, 达标: {}", candidates.size(), matched.size());
        return matched;
    }

    /**
     * 回 Cassandra 取命中笔记的正文（并行）
     *
     * <p>原实现串行逐条查询，命中 N 篇笔记就要 N 次网络往返；
     * 改为并行后整体耗时 ≈ 最慢的一次查询。</p>
     */
    private void loadFullContent(List<NoteCandidateDTO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>(notes.size());
        for (NoteCandidateDTO note : notes) {
            if (AiStringUtils.isNotBlank(note.getContent())) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    note.setContent(noteContentClient.findContent(note.getContentUuid()));
                } catch (Exception e) {
                    log.warn("## 拉取笔记正文失败, noteId: {}", note.getNoteId(), e);
                }
            }, aiTaskExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
