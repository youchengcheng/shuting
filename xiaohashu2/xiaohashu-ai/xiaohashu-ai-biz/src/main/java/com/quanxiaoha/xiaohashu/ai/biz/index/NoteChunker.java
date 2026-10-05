package com.quanxiaoha.xiaohashu.ai.biz.index;

import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记切块器
 *
 * <p>Cassandra 只能按主键点查，做不了全文检索，所以把笔记正文切成带重叠的小块，
 * 每块单独向量化后存进 pgvector，检索时按块召回、再按 noteId 聚合。</p>
 */
@Component
@Slf4j
public class NoteChunker {

    /**
     * 中文断句符，切块时尽量在句子边界切开
     */
    private static final char[] BOUNDARIES = {'\n', '。', '！', '？', '；', '，', '.', '!', '?', ';', ','};

    @Resource
    private AiProperties aiProperties;

    /**
     * 把一篇笔记切成若干个 chunk（每个 chunk 都带标题和话题上下文）
     */
    public List<String> chunk(String title, String topicName, String content) {
        List<String> result = new ArrayList<>();
        if (AiStringUtils.isBlank(content)) {
            return result;
        }

        AiProperties.Index index = aiProperties.getIndex();
        int size = Math.max(100, index.getChunkSize());
        int overlap = Math.min(Math.max(0, index.getChunkOverlap()), size / 2);

        String normalized = content.replace("\r\n", "\n").trim();
        String header = buildHeader(title, topicName);

        List<String> pieces = split(normalized, size, overlap, index.getMaxChunksPerNote());
        for (String piece : pieces) {
            result.add(header + piece);
        }
        return result;
    }

    private String buildHeader(String title, String topicName) {
        StringBuilder header = new StringBuilder();
        header.append("标题：").append(AiStringUtils.isBlank(title) ? "无标题" : title.trim()).append('\n');
        if (AiStringUtils.isNotBlank(topicName)) {
            header.append("话题：").append(topicName.trim()).append('\n');
        }
        header.append("正文：\n");
        return header.toString();
    }

    private List<String> split(String text, int size, int overlap, int maxChunks) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length() && pieces.size() < maxChunks) {
            int end = Math.min(start + size, text.length());
            if (end < text.length()) {
                int boundary = findBoundary(text, start + size / 2, end);
                if (boundary > 0) {
                    end = boundary;
                }
            }
            String piece = text.substring(start, end).trim();
            if (AiStringUtils.isNotBlank(piece)) {
                pieces.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        if (pieces.size() >= maxChunks) {
            log.warn("## 笔记内容过长，已截断为 {} 个 chunk", maxChunks);
        }
        return pieces;
    }

    /**
     * 在 [from, to) 区间内从后往前找一个断句位置
     */
    private int findBoundary(String text, int from, int to) {
        for (int i = to; i > from; i--) {
            char c = text.charAt(i - 1);
            for (char boundary : BOUNDARIES) {
                if (c == boundary) {
                    return i;
                }
            }
        }
        return -1;
    }
}