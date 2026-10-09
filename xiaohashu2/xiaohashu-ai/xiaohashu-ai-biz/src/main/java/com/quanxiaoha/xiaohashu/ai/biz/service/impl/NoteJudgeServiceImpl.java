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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
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

    @Resource(name = "aiTaskExecutor")
    private ThreadPoolTaskExecutor aiTaskExecutor;

    @Override
    public List<NoteCandidateDTO> judge(String query, List<NoteCandidateDTO> candidates) {
        if (CollUtil.isEmpty(candidates)) {
            return Collections.emptyList();
        }
        int batchSize = Math.max(1, aiProperties.getJudgeBatchSize());

        // 分批并行调用大模型：候选多时串行会累计十几秒，并行后整体耗时≈最慢的一批
        List<Future<List<NoteCandidateDTO>>> futures = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += batchSize) {
            List<NoteCandidateDTO> batch = new ArrayList<>(candidates.subList(i, Math.min(i + batchSize, candidates.size())));
            futures.add(aiTaskExecutor.submit(() -> judgeBatch(query, batch)));
        }

        List<NoteCandidateDTO> scored = new ArrayList<>(candidates.size());
        for (Future<List<NoteCandidateDTO>> future : futures) {
            try {
                scored.addAll(future.get());
            } catch (Exception e) {
                log.error("## 笔记判优批次失败", e);
            }
        }

        int threshold = aiProperties.getJudgeThreshold();
        // 不做篇数截断：达到阈值就全部返回，交给润色阶段统一整理
        return scored.stream()
                .filter(item -> item.getJudgeScore() != null && item.getJudgeScore() >= threshold)
                .sorted(Comparator.comparing(NoteCandidateDTO::getJudgeScore).reversed())
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

    /**
     * 判优阶段提供给模型的最大片段数（含最佳片段）
     */
    private static final int JUDGE_MAX_CHUNKS = 4;

    private String buildCandidatesText(List<NoteCandidateDTO> batch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            NoteCandidateDTO candidate = batch.get(i);
            sb.append("【候选").append(i + 1).append("】noteId=").append(candidate.getNoteId()).append('\n');
            sb.append("标题：").append(AiStringUtils.isBlank(candidate.getTitle()) ? "无标题" : candidate.getTitle()).append('\n');
            // 话题名提供分类语义，帮助模型快速判断相关性
            if (AiStringUtils.isNotBlank(candidate.getTopicName())) {
                sb.append("话题：").append(candidate.getTopicName()).append('\n');
            }
            // 提供多个命中片段，避免单片段信息不全导致误判
            List<String> topChunks = pickTopChunks(candidate, JUDGE_MAX_CHUNKS);
            for (int j = 0; j < topChunks.size(); j++) {
                sb.append("片段").append(j + 1).append("：")
                        .append(AiStringUtils.truncate(topChunks.get(j), AiConstants.JUDGE_CHUNK_MAX_CHARS))
                        .append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 从候选笔记的所有命中片段中挑出前 N 个不同的片段（最佳片段排第一），作为判优上下文。
     * <p>单片段可能恰好不包含用户关注的关键词，提供多个片段能显著降低误判概率。</p>
     */
    private List<String> pickTopChunks(NoteCandidateDTO candidate, int n) {
        List<String> chunks = candidate.getChunks();
        if (CollUtil.isEmpty(chunks)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(n);
        // 最佳片段排第一
        String best = candidate.getBestChunk();
        if (AiStringUtils.isNotBlank(best)) {
            result.add(best);
        }
        // 再补充其他不同的片段
        for (String chunk : chunks) {
            if (result.size() >= n) {
                break;
            }
            if (!result.contains(chunk)) {
                result.add(chunk);
            }
        }
        return result;
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
