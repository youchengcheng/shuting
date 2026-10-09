package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.config.VectorStoreProvider;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteMetaDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.NoteMetaMapper;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteRecallService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import com.quanxiaoha.xiaohashu.ai.biz.util.KeywordExtractor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 笔记召回服务实现
 *
 * <p>流程：向量库按 chunk 相似度召回 topK → 按 noteId 聚合（保留最高分片段）→ 回 MySQL 补元数据。</p>
 */
@Service
@Slf4j
public class NoteRecallServiceImpl implements NoteRecallService {

    @Resource
    private VectorStoreProvider vectorStoreProvider;

    @Resource
    private NoteMetaMapper noteMetaMapper;

    @Resource
    private AiProperties aiProperties;

    @Override
    public List<NoteCandidateDTO> recall(String query, int noteLimit) {
        if (AiStringUtils.isBlank(query)) {
            return Collections.emptyList();
        }
        int topK = Math.max(1, aiProperties.getRecallTopK());
        int limit = noteLimit > 0 ? noteLimit : aiProperties.getRecallNoteLimit();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(aiProperties.getRecallSimilarityThreshold())
                .build();

        VectorStore vectorStore = vectorStoreProvider.get();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        if (CollUtil.isEmpty(documents)) {
            log.info("## 向量召回为空, query: {}", query);
            return Collections.emptyList();
        }

        // 按 noteId 聚合：同一篇笔记只保留最高分片段 + 全部命中片段
        Map<Long, NoteCandidateDTO> merged = new LinkedHashMap<>();
        for (Document document : documents) {
            Long noteId = parseNoteId(document);
            if (noteId == null) {
                continue;
            }
            double score = document.getScore() == null ? 0D : document.getScore();
            NoteCandidateDTO candidate = merged.computeIfAbsent(noteId, id -> NoteCandidateDTO.builder()
                    .noteId(id)
                    .score(score)
                    .bestChunk(document.getText())
                    .chunks(new ArrayList<>())
                    .build());
            candidate.getChunks().add(document.getText());
            if (score > (candidate.getScore() == null ? 0D : candidate.getScore())) {
                candidate.setScore(score);
                candidate.setBestChunk(document.getText());
            }
        }

        List<NoteCandidateDTO> candidates = new ArrayList<>(merged.values());
        candidates.sort(Comparator.comparing(NoteCandidateDTO::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        if (candidates.size() > limit) {
            candidates = candidates.subList(0, limit);
        }

        // 关键词硬过滤：向量检索只看语义相似度，对精确关键词（品牌名、地名、型号）不敏感，
        // 这里用用户提问中的关键词做二次过滤，过滤掉"话题沾边但不含核心关键词"的噪声笔记
        List<String> keywords = KeywordExtractor.extract(query);
        int beforeFilter = candidates.size();
        if (CollUtil.isNotEmpty(keywords)) {
            candidates = filterByKeywords(candidates, keywords);
        }

        fillNoteMeta(candidates);
        log.info("## 向量召回完成, 命中 chunk: {}, 聚合笔记: {}, 关键词过滤后: {} (关键词: {})",
                documents.size(), beforeFilter, candidates.size(), keywords);
        return candidates;
    }

    private Long parseNoteId(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object noteId = metadata.get("noteId");
        if (noteId == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(noteId).trim());
        } catch (NumberFormatException e) {
            log.warn("## 非法的 noteId 元数据: {}", noteId);
            return null;
        }
    }

    /**
     * 回 MySQL 补齐标题 / 话题 / 作者 / 正文 UUID，并过滤掉已被删除或转为私密的笔记
     */
    private void fillNoteMeta(List<NoteCandidateDTO> candidates) {
        if (CollUtil.isEmpty(candidates)) {
            return;
        }
        List<Long> noteIds = candidates.stream().map(NoteCandidateDTO::getNoteId).collect(Collectors.toList());
        List<NoteMetaDO> metas = noteMetaMapper.selectBatchIds(noteIds);
        Map<Long, NoteMetaDO> metaMap = metas.stream()
                .collect(Collectors.toMap(NoteMetaDO::getId, meta -> meta, (a, b) -> a));

        List<NoteCandidateDTO> result = new ArrayList<>(candidates.size());
        for (NoteCandidateDTO candidate : candidates) {
            NoteMetaDO meta = metaMap.get(candidate.getNoteId());
            if (!stillVisible(meta)) {
                log.info("## 候选笔记已不可见，跳过, noteId: {}", candidate.getNoteId());
                continue;
            }
            candidate.setTitle(meta.getTitle());
            candidate.setTopicName(meta.getTopicName());
            candidate.setCreatorId(meta.getCreatorId());
            candidate.setContentUuid(meta.getContentUuid());
            result.add(candidate);
        }
        candidates.clear();
        candidates.addAll(result);
    }

    private boolean stillVisible(NoteMetaDO meta) {
        return meta != null
                && Objects.equals(meta.getStatus(), 1)
                && Objects.equals(meta.getVisible(), 0);
    }

    /**
     * 关键词硬过滤：候选笔记的标题/话题/所有命中片段中，至少包含一个用户提问的关键词才保留。
     *
     * <p>向量检索对精确关键词（品牌名、地名、型号）不敏感，容易召回"话题沾边但不相关"的笔记，
     * 这里用关键词做硬性兜底，把完全不含核心关键词的噪声过滤掉。</p>
     *
     * @param candidates 候选笔记列表
     * @param keywords   用户提问提取的关键词
     * @return 过滤后的候选笔记
     */
    private List<NoteCandidateDTO> filterByKeywords(List<NoteCandidateDTO> candidates, List<String> keywords) {
        if (CollUtil.isEmpty(candidates) || CollUtil.isEmpty(keywords)) {
            return candidates;
        }
        List<NoteCandidateDTO> result = new ArrayList<>(candidates.size());
        for (NoteCandidateDTO candidate : candidates) {
            if (containsAnyKeyword(candidate, keywords)) {
                result.add(candidate);
            }
        }
        // fallback：如果关键词过滤后全部被过滤掉，说明关键词提取可能不准或笔记内容不含显式关键词，
        // 此时退回原列表，交给判优阶段用大模型判断，避免漏召回
        if (result.isEmpty()) {
            log.info("## 关键词过滤后无候选，退回原列表, 关键词: {}", keywords);
            return candidates;
        }
        return result;
    }

    /**
     * 判断候选笔记是否包含至少一个关键词
     * <p>匹配范围：标题 + 话题 + 所有命中片段（分片时已把标题/话题拼入片段，所以 chunks 已覆盖大部分信息）。</p>
     */
    private boolean containsAnyKeyword(NoteCandidateDTO candidate, List<String> keywords) {
        StringBuilder text = new StringBuilder();
        if (AiStringUtils.isNotBlank(candidate.getTitle())) {
            text.append(candidate.getTitle()).append(' ');
        }
        if (AiStringUtils.isNotBlank(candidate.getTopicName())) {
            text.append(candidate.getTopicName()).append(' ');
        }
        if (CollUtil.isNotEmpty(candidate.getChunks())) {
            for (String chunk : candidate.getChunks()) {
                text.append(chunk).append(' ');
            }
        }
        String haystack = text.toString().toLowerCase();
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}