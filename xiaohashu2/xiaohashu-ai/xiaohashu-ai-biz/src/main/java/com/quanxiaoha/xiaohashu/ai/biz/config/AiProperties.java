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
     * <p>从 40 降到 20：减少向量库扫描量和聚合开销。</p>
     */
    private int recallTopK = 20;

    /**
     * 召回结果按 noteId 聚合后，最多保留多少篇笔记进入判优阶段
     * <p>从 20 降到 12：减少送入大模型判优的笔记数量，加快响应。</p>
     */
    private int recallNoteLimit = 12;

    /**
     * 向量召回相似度下限（COSINE 距离下 0~1，越大越严格）
     * <p>从 0.25 提到 0.45：过滤掉明显不相关的噪声笔记，减少判优压力。</p>
     */
    private double recallSimilarityThreshold = 0.45;

    /**
     * 大模型判优的通过阈值（0~100 分）
     * <p>从 60 提到 70：只保留真正相关的笔记，弱相关不再混入。</p>
     */
    private int judgeThreshold = 70;

    /**
     * 判优阶段单次送入模型的候选笔记数量（分批调用，避免 prompt 过长）
     * <p>从 8 提到 12：减少 LLM 调用批次，12 篇候选只需 1 次调用。</p>
     */
    private int judgeBatchSize = 12;

    /**
     * 润色阶段送入模型的笔记正文总字符数上限
     *
     * <p>判优达标的笔记会「全量」参与润色，这里只做上下文长度的兜底保护，
     * 不限制引用笔记篇数。</p>
     */
    private int polishMaxInputChars = 24000;

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
         * 单个 chunk 的目标字符数（对半砍到 140，单 chunk 只含一个小话题点）
         */
        private int chunkSize = 100;

        /**
         * 相邻 chunk 的重叠字符数，避免语义被切断（建议占 chunkSize 的 15-20%）
         */
        private int chunkOverlap = 20;

        /**
         * 单篇笔记最多切分出的 chunk 数，防止超长笔记把向量库撑爆
         */
        private int maxChunksPerNote = 30;

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
