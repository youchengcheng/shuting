package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.constant.AiConstants;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;
import com.quanxiaoha.xiaohashu.ai.biz.prompts.NotePrompts;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteJudgeService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记判优服务实现
 */
@Service
@Slf4j
public class NoteJudgeServiceImpl implements NoteJudgeService {

    @Resource
    private ChatClient chatClient;

    @Resource
    private AiProperties aiProperties;

    @Override
    public List<NoteCandidateDTO> judge(String query, List<NoteCandidateDTO> candidates, int topN) {
        if (CollUtil.isEmpty(candidates)) {
            return Collections.emptyList();
        }
        int batchSize = Math.max(1, aiProperties.getJudgeBatchSize());
        List<NoteCandidateDTO> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i += batchSize) {
            List<NoteCandidateDTO> batch = candidates.subList(i, Math.min(i + batchSize, candidates.size()));
            try {
                scored.addAll(judgeBatch(query, batch));
            } catch (Exception e) {
                log.error("## 笔记判优批次失败, batchSize: {}", batch.size(), e);
            }
        }

        int threshold = aiProperties.getJudgeThreshold();
        int limit = topN > 0 ? topN : aiProperties.getJudgeTopN();
        return scored.stream()
                .filter(item -> item.getJudgeScore() != null && item.getJudgeScore() >= threshold)
                .sorted(Comparator.comparing(NoteCandidateDTO::getJudgeScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<NoteCandidateDTO> judgeBatch(String query, List<NoteCandidateDTO> batch) {
        String candidatesText = buildCandidatesText(batch);
        String content = chatClient.prompt()
                .system(NotePrompts.JUDGE_SYSTEM)
                .user(NotePrompts.buildJudgeUserPrompt(query, candidatesText))
                .call()
                .content();
        if (AiStringUtils.isBlank(content)) {
            throw new BizException(ResponseCodeEnum.AI_SERVICE_ERROR);
        }

        Map<Long, JSONObject> scoreMap = parseScores(content);
        for (NoteCandidateDTO candidate : batch) {
            JSONObject item = scoreMap.get(candidate.getNoteId());
            if (item == null) {
                continue;
            }
            Integer score = item.getInt("score");
            candidate.setJudgeScore(score);
            candidate.setJudgeReason(item.getStr("reason"));
        }
        return batch;
    }

    private String buildCandidatesText(List<NoteCandidateDTO> batch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            NoteCandidateDTO candidate = batch.get(i);
            sb.append("【候选").append(i + 1).append("】noteId=").append(candidate.getNoteId()).append('\n');
            sb.append("标题：").append(AiStringUtils.isBlank(candidate.getTitle()) ? "无标题" : candidate.getTitle()).append('\n');
            sb.append("片段：")
                    .append(AiStringUtils.truncate(candidate.getBestChunk(), AiConstants.JUDGE_CHUNK_MAX_CHARS))
                    .append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 解析大模型返回的评分 JSON，兼容「裸数组」和「包在对象里」两种形态
     */
    private Map<Long, JSONObject> parseScores(String content) {
        String json = AiStringUtils.extractJson(content);
        if (AiStringUtils.isBlank(json)) {
            throw new BizException(ResponseCodeEnum.AI_RESPONSE_PARSE_ERROR);
        }

        JSONArray array = null;
        if (json.charAt(0) == '[') {
            array = JSONUtil.parseArray(json);
        } else {
            JSONObject object = JSONUtil.parseObj(json);
            for (Object value : object.values()) {
                if (value instanceof JSONArray) {
                    array = (JSONArray) value;
                    break;
                }
            }
        }
        if (array == null) {
            throw new BizException(ResponseCodeEnum.AI_RESPONSE_PARSE_ERROR);
        }

        Map<Long, JSONObject> scoreMap = new HashMap<>(array.size());
        for (Object element : array) {
            if (!(element instanceof JSONObject)) {
                continue;
            }
            JSONObject item = (JSONObject) element;
            Long noteId = item.getLong("noteId");
            if (noteId != null) {
                scoreMap.put(noteId, item);
            }
        }
        return scoreMap;
    }
}