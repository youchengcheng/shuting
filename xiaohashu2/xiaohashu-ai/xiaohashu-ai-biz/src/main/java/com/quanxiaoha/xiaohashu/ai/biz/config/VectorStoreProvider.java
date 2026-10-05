package com.quanxiaoha.xiaohashu.ai.biz.config;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * pgvector 向量库提供者
 *
 * <p>为什么不直接把 PgVectorStore 声明成 Bean？</p>
 * <ul>
 *     <li>AI 模块的主数据源是业务 MySQL，如果再声明一个 PG DataSource Bean 会让 Spring Boot
 *     的 DataSource 自动装配产生歧义；</li>
 *     <li>向量库属于「用到才需要」的外部依赖，做成懒加载后，即使本地没起 PostgreSQL，
 *     只是检索类接口报错，应用本身仍能正常启动。</li>
 * </ul>
 */
@Component
@Slf4j
public class VectorStoreProvider {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private AiProperties aiProperties;

    private volatile VectorStore vectorStore;

    /**
     * 懒加载获取向量库
     */
    public VectorStore get() {
        if (vectorStore != null) {
            return vectorStore;
        }
        synchronized (this) {
            if (vectorStore != null) {
                return vectorStore;
            }
            try {
                vectorStore = build();
                log.info("## pgvector 向量库初始化完成, table: {}", aiProperties.getVector().getTableName());
            } catch (Exception e) {
                log.error("## pgvector 向量库初始化失败", e);
                throw new BizException(ResponseCodeEnum.INDEX_NOT_READY);
            }
            return vectorStore;
        }
    }

    private VectorStore build() {
        AiProperties.Vector vector = aiProperties.getVector();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(vector.getUrl());
        dataSource.setUsername(vector.getUsername());
        dataSource.setPassword(vector.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setPoolName("ai-pgvector-pool");
        dataSource.setMaximumPoolSize(4);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(5000);
        // PG 不可用时不要在获取连接阶段直接抛异常，交给上层统一兜底
        dataSource.setInitializationFailTimeout(-1);

        PgVectorStore store = PgVectorStore.builder(new JdbcTemplate(dataSource), embeddingModel)
                .vectorTableName(vector.getTableName())
                .dimensions(vector.getDimensions())
                .distanceType(resolveDistanceType(vector.getDistanceType()))
                .indexType(resolveIndexType(vector.getIndexType()))
                .idType(PgVectorStore.PgIdType.TEXT)
                .initializeSchema(vector.isInitializeSchema())
                .vectorTableValidationsEnabled(false)
                .build();
        // 手工创建的对象不会触发 Spring 的 InitializingBean 回调，这里显式调用，完成建表 / 建索引
        store.afterPropertiesSet();
        return store;
    }

    private PgVectorStore.PgDistanceType resolveDistanceType(String distanceType) {
        if (distanceType == null) {
            return PgVectorStore.PgDistanceType.COSINE_DISTANCE;
        }
        switch (distanceType.trim().toUpperCase()) {
            case "EUCLIDEAN":
                return PgVectorStore.PgDistanceType.EUCLIDEAN_DISTANCE;
            case "NEGATIVE_INNER_PRODUCT":
                return PgVectorStore.PgDistanceType.NEGATIVE_INNER_PRODUCT;
            case "COSINE":
            default:
                return PgVectorStore.PgDistanceType.COSINE_DISTANCE;
        }
    }

    private PgVectorStore.PgIndexType resolveIndexType(String indexType) {
        if (indexType == null) {
            return PgVectorStore.PgIndexType.HNSW;
        }
        switch (indexType.trim().toUpperCase()) {
            case "NONE":
                return PgVectorStore.PgIndexType.NONE;
            case "IVFFLAT":
                return PgVectorStore.PgIndexType.IVFFLAT;
            case "HNSW":
            default:
                return PgVectorStore.PgIndexType.HNSW;
        }
    }
}