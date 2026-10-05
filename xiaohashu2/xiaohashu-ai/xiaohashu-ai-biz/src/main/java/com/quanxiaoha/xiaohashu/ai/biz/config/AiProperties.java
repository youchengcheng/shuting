package com.quanxiaoha.xiaohashu.ai.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模块业务配置
 *
 * <p>统一前缀 {@code xiaohashu.ai}，所有可调参数都集中在这里，方便按环境覆盖。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "xiaohashu.ai")
public class AiProperties {

    /**
     * 送入大模型的单篇笔记正文最大字符数，超出部分截断
     */
    private int noteMaxContentChars = 8000;

    /**
     * 向量召回阶段返回的 chunk 数量（注意是 chunk 不是笔记）
     */
    private int recallTopK = 40;

    /**
     * 召回结果按 noteId 聚合后，最多保留多少篇笔记进入判优阶段
     */
    private int recallNoteLimit = 20;

    /**
     * 向量召回相似度下限（COSINE 距离下 0~1，越大越严格）
     */
    private double recallSimilarityThreshold = 0.25;

    /**
     * 大模型判优的通过阈值（0~100 分）
     */
    private int judgeThreshold = 60;

    /**
     * 判优通过后，最多取前 N 篇笔记做润色
     */
    private int judgeTopN = 3;

    /**
     * 判优阶段单次送入模型的候选笔记数量（分批调用，避免 prompt 过长）
     */
    private int judgeBatchSize = 8;

    /**
     * 润色结果最大字符数
     */
    private int polishMaxChars = 6000;

    /**
     * 索引与检索相关配置
     */
    private Index index = new Index();

    /**
     * pgvector 向量库配置
     */
    private Vector vector = new Vector();

    /**
     * Cassandra 笔记正文配置
     */
    private Cassandra cassandra = new Cassandra();

    @Data
    public static class Index {
        /**
         * 是否启用笔记索引能力（关闭后不再消费 MQ、不再写向量库）
         */
        private boolean enabled = true;

        /**
         * 应用启动时是否自动全量重建索引（数据量大时慎开）
         */
        private boolean rebuildOnStartup = false;

        /**
         * 是否开启每日定时全量重建兜底
         */
        private boolean rebuildEnabled = false;

        /**
         * 单个 chunk 的目标字符数
         */
        private int chunkSize = 400;

        /**
         * 相邻 chunk 的重叠字符数，避免语义被切断
         */
        private int chunkOverlap = 80;

        /**
         * 单篇笔记最多切分出的 chunk 数，防止超长笔记把向量库撑爆
         */
        private int maxChunksPerNote = 50;

        /**
         * 每日全量重建的 cron 表达式
         */
        private String rebuildCron = "0 30 3 * * ?";
    }

    @Data
    public static class Vector {
        /**
         * pgvector 所在 PostgreSQL 的 JDBC URL
         */
        private String url = "jdbc:postgresql://127.0.0.1:5432/robot";

        private String username = "postgres";

        private String password = "postgres";

        /**
         * 向量表名
         */
        private String tableName = "t_ai_note_vector_store";

        /**
         * 向量维度，必须与 embedding 模型输出维度一致
         */
        private int dimensions = 1536;

        /**
         * 索引类型：HNSW / IVFFLAT / NONE
         */
        private String indexType = "HNSW";

        /**
         * 距离类型：COSINE / EUCLIDEAN / NEGATIVE_INNER_PRODUCT
         */
        private String distanceType = "COSINE";

        /**
         * 是否允许应用自动建表 / 建扩展（生产环境建议先手工执行 db/vector_store.sql）
         */
        private boolean initializeSchema = true;
    }

    @Data
    public static class Cassandra {
        /**
         * Cassandra 节点地址，多个用逗号分隔
         */
        private String contactPoints = "127.0.0.1";

        private int port = 9042;

        /**
         * keyspace
         */
        private String keyspace = "xiaohashu";

        /**
         * 本地数据中心名称，单机默认 datacenter1
         */
        private String localDatacenter = "datacenter1";

        /**
         * 笔记正文表名
         */
        private String table = "note_content";
    }
}