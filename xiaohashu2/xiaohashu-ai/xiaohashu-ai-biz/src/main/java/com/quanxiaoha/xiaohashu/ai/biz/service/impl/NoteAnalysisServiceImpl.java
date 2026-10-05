package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.client.NoteContentClient;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteAnalysisDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteMetaDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.NoteAnalysisMapper;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.NoteMetaMapper;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteAnalysisReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteAnalysisRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.prompts.NotePrompts;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteAnalysisService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 单篇笔记 AI 解读服务实现
 */
@Service
@Slf4j
public class NoteAnalysisServiceImpl implements NoteAnalysisService {

    @Resource
    private NoteMetaMapper noteMetaMapper;

    @Resource
    private NoteAnalysisMapper noteAnalysisMapper;

    @Resource
    private NoteContentClient noteContentClient;

    @Resource
    private ChatClient chatClient;

    @Override
    public NoteAnalysisRspVO analyze(Long userId, NoteAnalysisReqVO reqVO) {
        Long noteId = reqVO.getNoteId();
        NoteMetaDO meta = noteMetaMapper.selectById(noteId);
        if (meta == null || !Objects.equals(meta.getStatus(), 1)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 私密笔记只有作者本人能解读
        if (Objects.equals(meta.getVisible(), 1) && !Objects.equals(meta.getCreatorId(), userId)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        if (AiStringUtils.isBlank(meta.getContentUuid())) {
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_EMPTY);
        }

        String content = noteContentClient.findContent(meta.getContentUuid());
        if (AiStringUtils.isBlank(content)) {
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_EMPTY);
        }

        String contentHash = AiStringUtils.md5(meta.getTitle() + "\n" + content);
        NoteAnalysisDO cached = selectAnalysis(noteId, userId);
        boolean forceRefresh = Boolean.TRUE.equals(reqVO.getForceRefresh());
        if (!forceRefresh && cached != null
                && AiStringUtils.isNotBlank(cached.getResult())
                && Objects.equals(cached.getContentHash(), contentHash)) {
            log.info("## 命中笔记解读缓存, noteId: {}, userId: {}", noteId, userId);
            return buildRsp(meta, cached.getResult(), true);
        }

        String result;
        try {
            result = chatClient.prompt()
                    .system(NotePrompts.ANALYZE_SYSTEM)
                    .user(NotePrompts.buildAnalyzeUserPrompt(meta.getTitle(), meta.getTopicName(), content))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("## 笔记解读调用大模型失败, noteId: {}", noteId, e);
            throw new BizException(ResponseCodeEnum.NOTE_ANALYSIS_FAIL);
        }
        if (AiStringUtils.isBlank(result)) {
            throw new BizException(ResponseCodeEnum.NOTE_ANALYSIS_FAIL);
        }

        saveAnalysis(meta, userId, contentHash, result, cached);
        return buildRsp(meta, result, false);
    }

    private NoteAnalysisDO selectAnalysis(Long noteId, Long userId) {
        return noteAnalysisMapper.selectOne(Wrappers.<NoteAnalysisDO>lambdaQuery()
                .eq(NoteAnalysisDO::getNoteId, noteId)
                .eq(NoteAnalysisDO::getUserId, userId)
                .last("limit 1"));
    }

    private void saveAnalysis(NoteMetaDO meta, Long userId, String contentHash, String result, NoteAnalysisDO cached) {
        LocalDateTime now = LocalDateTime.now();
        if (cached == null) {
            noteAnalysisMapper.insert(NoteAnalysisDO.builder()
                    .noteId(meta.getId())
                    .userId(userId)
                    .title(meta.getTitle())
                    .contentHash(contentHash)
                    .result(result)
                    .createTime(now)
                    .updateTime(now)
                    .build());
            return;
        }
        noteAnalysisMapper.updateById(NoteAnalysisDO.builder()
                .id(cached.getId())
                .title(meta.getTitle())
                .contentHash(contentHash)
                .result(result)
                .updateTime(now)
                .build());
    }

    private NoteAnalysisRspVO buildRsp(NoteMetaDO meta, String result, boolean fromCache) {
        return NoteAnalysisRspVO.builder()
                .noteId(String.valueOf(meta.getId()))
                .title(meta.getTitle())
                .topicName(meta.getTopicName())
                .creatorId(meta.getCreatorId() == null ? null : String.valueOf(meta.getCreatorId()))
                .result(result)
                .fromCache(fromCache)
                .build();
    }
}