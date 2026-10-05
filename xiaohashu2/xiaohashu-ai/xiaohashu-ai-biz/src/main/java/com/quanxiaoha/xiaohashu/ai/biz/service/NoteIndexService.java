package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.enums.IndexResultEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.IndexRebuildRspVO;

/**
 * 笔记检索索引服务
 *
 * <p>负责把 MySQL 元数据 + Cassandra 正文变成 pgvector 里的向量，以及反向的删除操作。</p>
 */
public interface NoteIndexService {

    /**
     * 为单篇笔记建立 / 更新索引（幂等，内容没变会自动跳过）
     */
    IndexResultEnum indexNote(Long noteId);

    /**
     * 删除单篇笔记的索引
     */
    void deleteNote(Long noteId);

    /**
     * 全量重建索引
     */
    IndexRebuildRspVO rebuildAll();

    /**
     * 当前已建索引的笔记数
     */
    long countIndexed();

    /**
     * 向量库中的 chunk 总数
     */
    long countVectors();
}