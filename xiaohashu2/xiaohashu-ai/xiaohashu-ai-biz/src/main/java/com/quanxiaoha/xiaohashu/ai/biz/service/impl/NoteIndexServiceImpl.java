package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.client.NoteContentClient;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.config.VectorStoreProvider;
import com.quanxiaoha.xiaohashu.ai.biz.constant.AiConstants;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteIndexDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteMetaDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.NoteIndexMapper;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.NoteMetaMapper;
import com.quanxiaoha.xiaohashu.ai.biz.enums.IndexResultEnum;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.index.NoteChunker;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.IndexRebuildRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteIndexService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

/**
 * 笔记检索索引服务实现
 */
@Service
@Slf4j
public class NoteIndexServiceImpl implements NoteIndexService {

    /**
     * 单次写入向量库的文档数（DashScope embedding 单次请求有条数上限，这里保守分批）
     */
    private static final int VECTOR_ADD_BATCH_SIZE = 10;

    /**
     * 元数据表分页扫描大小
     */
    private static final int SCAN_PAGE_SIZE = 200;

    @Resource
    private NoteMetaMapper noteMetaMapper;

    @Resource
    private NoteIndexMapper noteIndexMapper;

    @Resource
    private NoteContentClient noteContentClient;

    @Resource
    private NoteChunker noteChunker;

    @Resource
    private VectorStoreProvider vectorStoreProvider;

    @Resource
    private AiProperties aiProperties;

    @Resource(name = "noteIndexExecutor")
    private ThreadPoolTaskExecutor noteIndexExecutor;

    @Override
    public IndexResultEnum indexNote(Long noteId) {
        if (noteId == null || !aiProperties.getIndex().isEnabled()) {
            return IndexResultEnum.SKIPPED;
        }

        try {
            NoteMetaDO meta = noteMetaMapper.selectById(noteId);
            if (!isIndexable(meta)) {
                // 笔记已删除 / 下架 / 转私密 / 无正文，顺手把历史索引清掉
                deleteNote(noteId);
                return IndexResultEnum.SKIPPED;
            }

            String content = noteContentClient.findContent(meta.getContentUuid());
            if (AiStringUtils.isBlank(content)) {
                log.warn("## 笔记正文为空，跳过建索引, noteId: {}", noteId);
                deleteNote(noteId);
                return IndexResultEnum.SKIPPED;
            }

            String contentHash = AiStringUtils.md5(meta.getTitle() + "\n" + content);
            NoteIndexDO existing = noteIndexMapper.selectByNoteId(noteId);
            if (existing != null
                    && Objects.equals(existing.getStatus(), AiConstants.INDEX_STATUS_INDEXED)
                    && existing.getChunkTotal() != null
                    && existing.getChunkTotal() > 0
                    && Objects.equals(existing.getContentHash(), contentHash)) {
                log.info("## 笔记内容未变化，跳过重建, noteId: {}", noteId);
                return IndexResultEnum.SKIPPED;
            }

            List<String> chunks = noteChunker.chunk(meta.getTitle(), meta.getTopicName(), content);
            if (CollUtil.isEmpty(chunks)) {
                return IndexResultEnum.SKIPPED;
            }

            VectorStore vectorStore = vectorStoreProvider.get();

            // 先删旧向量，再写新向量，保证重复消费 / 更新场景幂等
            deleteVectors(vectorStore, noteId, existing == null ? null : existing.getChunkTotal());

            List<Document> documents = buildDocuments(noteId, meta, chunks);
            for (int i = 0; i < documents.size(); i += VECTOR_ADD_BATCH_SIZE) {
                List<Document> batch = documents.subList(i, Math.min(i + VECTOR_ADD_BATCH_SIZE, documents.size()));
                vectorStore.add(batch);
            }

            upsertIndex(meta, contentHash, chunks.size());
            log.info("## 笔记索引完成, noteId: {}, chunks: {}", noteId, chunks.size());
            return IndexResultEnum.INDEXED;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("## 笔记建索引失败, noteId: {}", noteId, e);
            return IndexResultEnum.FAILED;
        }
    }

    @Override
    public void deleteNote(Long noteId) {
        if (noteId == null) {
            return;
        }
        try {
            NoteIndexDO existing = noteIndexMapper.selectByNoteId(noteId);
            if (existing == null) {
                return;
            }
            deleteVectors(vectorStoreProvider.get(), noteId, existing.getChunkTotal());
            noteIndexMapper.updateById(NoteIndexDO.builder()
                    .id(existing.getId())
                    .status(AiConstants.INDEX_STATUS_DELETED)
                    .chunkTotal(0)
                    .updateTime(LocalDateTime.now())
                    .build());
            log.info("## 笔记索引已删除, noteId: {}", noteId);
        } catch (Exception e) {
            log.error("## 删除笔记索引失败, noteId: {}", noteId, e);
        }
    }

    @Override
    public IndexRebuildRspVO rebuildAll() {
        long start = System.currentTimeMillis();
        long total = 0;
        long indexed = 0;
        long skipped = 0;
        long failed = 0;

        long offset = 0;
        while (true) {
            List<NoteMetaDO> page = noteMetaMapper.selectIndexablePage(offset, SCAN_PAGE_SIZE);
            if (CollUtil.isEmpty(page)) {
                break;
            }
            total += page.size();

            List<Future<IndexResultEnum>> futures = new ArrayList<>(page.size());
            for (NoteMetaDO note : page) {
                futures.add(noteIndexExecutor.submit(() -> indexNote(note.getId())));
            }
            for (Future<IndexResultEnum> future : futures) {
                try {
                    IndexResultEnum result = future.get();
                    if (result == IndexResultEnum.INDEXED) {
                        indexed++;
                    } else if (result == IndexResultEnum.SKIPPED) {
                        skipped++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("## 重建索引任务执行异常", e);
                }
            }

            if (page.size() < SCAN_PAGE_SIZE) {
                break;
            }
            offset += SCAN_PAGE_SIZE;
        }

        long costMs = System.currentTimeMillis() - start;
        log.info("## 笔记索引全量重建完成, total: {}, indexed: {}, skipped: {}, failed: {}, costMs: {}",
                total, indexed, skipped, failed, costMs);
        return IndexRebuildRspVO.builder()
                .total(total)
                .indexed(indexed)
                .skipped(skipped)
                .failed(failed)
                .costMs(costMs)
                .build();
    }

    @Override
    public long countIndexed() {
        Long count = noteIndexMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<NoteIndexDO>lambdaQuery()
                        .eq(NoteIndexDO::getStatus, AiConstants.INDEX_STATUS_INDEXED));
        return count == null ? 0L : count;
    }

    @Override
    public long countVectors() {
        long total = 0;
        List<NoteIndexDO> list = noteIndexMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<NoteIndexDO>lambdaQuery()
                        .eq(NoteIndexDO::getStatus, AiConstants.INDEX_STATUS_INDEXED));
        for (NoteIndexDO item : list) {
            total += item.getChunkTotal() == null ? 0 : item.getChunkTotal();
        }
        return total;
    }

    private boolean isIndexable(NoteMetaDO meta) {
        return meta != null
                && Objects.equals(meta.getStatus(), 1)
                && Objects.equals(meta.getVisible(), 0)
                && Objects.equals(meta.getIsContentEmpty(), Boolean.FALSE)
                && AiStringUtils.isNotBlank(meta.getContentUuid());
    }

    private List<Document> buildDocuments(Long noteId, NoteMetaDO meta, List<String> chunks) {
        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>(4);
            metadata.put("noteId", String.valueOf(noteId));
            metadata.put("chunkIndex", i);
            metadata.put("title", meta.getTitle());
            metadata.put("topicName", meta.getTopicName());
            metadata.put("creatorId", meta.getCreatorId() == null ? null : String.valueOf(meta.getCreatorId()));
            documents.add(Document.builder()
                    .id(buildDocId(noteId, i))
                    .text(chunks.get(i))
                    .metadata(metadata)
                    .build());
        }
        return documents;
    }

    private String buildDocId(Long noteId, int chunkIndex) {
        return noteId + AiConstants.VECTOR_ID_SEPARATOR + chunkIndex;
    }

    /**
     * 删除某篇笔记的全部向量
     *
     * @param chunkTotal 已知 chunk 数时按 ID 精确删除；未知时退化为 metadata 条件删除
     */
    private void deleteVectors(VectorStore vectorStore, Long noteId, Integer chunkTotal) {
        if (chunkTotal != null && chunkTotal > 0) {
            List<String> ids = new ArrayList<>(chunkTotal);
            for (int i = 0; i < chunkTotal; i++) {
                ids.add(buildDocId(noteId, i));
            }
            vectorStore.delete(ids);
            return;
        }
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("noteId", String.valueOf(noteId))
                .build();
        vectorStore.delete(expression);
    }

    private void upsertIndex(NoteMetaDO meta, String contentHash, int chunkTotal) {
        NoteIndexDO existing = noteIndexMapper.selectByNoteId(meta.getId());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            noteIndexMapper.insert(NoteIndexDO.builder()
                    .noteId(meta.getId())
                    .creatorId(meta.getCreatorId())
                    .title(meta.getTitle())
                    .topicName(meta.getTopicName())
                    .contentUuid(meta.getContentUuid())
                    .contentHash(contentHash)
                    .chunkTotal(chunkTotal)
                    .status(AiConstants.INDEX_STATUS_INDEXED)
                    .createTime(now)
                    .updateTime(now)
                    .build());
            return;
        }
        noteIndexMapper.updateById(NoteIndexDO.builder()
                .id(existing.getId())
                .creatorId(meta.getCreatorId())
                .title(meta.getTitle())
                .topicName(meta.getTopicName())
                .contentUuid(meta.getContentUuid())
                .contentHash(contentHash)
                .chunkTotal(chunkTotal)
                .status(AiConstants.INDEX_STATUS_INDEXED)
                .updateTime(now)
                .build());
    }

}