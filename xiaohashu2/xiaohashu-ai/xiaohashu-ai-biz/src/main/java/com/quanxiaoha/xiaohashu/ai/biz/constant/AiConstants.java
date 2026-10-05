package com.quanxiaoha.xiaohashu.ai.biz.constant;

/**
 * AI 模块常量
 */
public interface AiConstants {

    /**
     * 向量文档 ID 分隔符：{@code noteId_chunkIndex}
     */
    String VECTOR_ID_SEPARATOR = "_";

    /**
     * prompt 中单篇候选笔记正文的最大字符数（判优阶段用，比润色阶段更短）
     */
    int JUDGE_CHUNK_MAX_CHARS = 1200;

    /**
     * 聊天消息角色
     */
    String ROLE_USER = "user";
    String ROLE_ASSISTANT = "assistant";

    /**
     * 笔记索引状态
     */
    int INDEX_STATUS_DELETED = 0;
    int INDEX_STATUS_INDEXED = 1;

    /**
     * 笔记操作类型（与 note 模块 MQConstants / NoteOperateEnum 对齐）
     */
    int NOTE_OP_DELETE = 0;
    int NOTE_OP_PUBLISH = 1;
    int NOTE_OP_UPDATE = 2;

    /**
     * RocketMQ：笔记操作主题 / 标签（必须与 note 模块保持一致）
     */
    String TOPIC_NOTE_OPERATE = "NoteOperateTopic";
    String TAG_NOTE_PUBLISH = "publishNote";
    String TAG_NOTE_DELETE = "deleteNote";
    String TAG_NOTE_UPDATE = "updateNote";
}