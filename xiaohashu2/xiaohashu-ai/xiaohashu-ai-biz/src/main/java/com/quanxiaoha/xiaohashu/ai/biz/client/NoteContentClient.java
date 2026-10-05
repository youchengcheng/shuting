package com.quanxiaoha.xiaohashu.ai.biz.client;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.Row;
import com.quanxiaoha.xiaohashu.ai.biz.config.AiProperties;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * 笔记正文读取客户端（Cassandra）
 *
 * <p>笔记长文本存在 Cassandra 的 {@code xiaohashu.note_content} 表，主键是 UUID，
 * 只能按主键点查，所以检索必须先经过向量召回拿到 noteId，再用 contentUuid 回表取正文。</p>
 *
 * <p>Session 懒加载：只有真正要用正文时才建连接，Cassandra 没起也不影响应用启动。</p>
 */
@Component
@Slf4j
public class NoteContentClient {

    @Resource
    private AiProperties aiProperties;

    private volatile CqlSession session;

    /**
     * 查询语句（表名来自配置，非用户输入，拼接安全）
     */
    private volatile String findContentCql;

    /**
     * 按正文 UUID 点查笔记正文
     *
     * @return 正文；查不到或异常时返回 null
     */
    public String findContent(String contentUuid) {
        if (AiStringUtils.isBlank(contentUuid)) {
            return null;
        }
        try {
            Row row = session()
                    .execute(findContentCql(), UUID.fromString(contentUuid))
                    .one();
            return row == null ? null : row.getString("content");
        } catch (IllegalArgumentException e) {
            log.warn("## 非法的正文 UUID: {}", contentUuid);
            return null;
        } catch (Exception e) {
            log.error("## 查询笔记正文失败, contentUuid: {}", contentUuid, e);
            return null;
        }
    }

    private String findContentCql() {
        if (findContentCql == null) {
            findContentCql = "SELECT content FROM " + aiProperties.getCassandra().getTable() + " WHERE id = ?";
        }
        return findContentCql;
    }

    private CqlSession session() {
        if (session != null) {
            return session;
        }
        synchronized (this) {
            if (session != null) {
                return session;
            }
            session = buildSession();
            return session;
        }
    }

    private CqlSession buildSession() {
        AiProperties.Cassandra cassandra = aiProperties.getCassandra();

        CqlSessionBuilder builder = CqlSession.builder();
        for (String contactPoint : cassandra.getContactPoints().split(",")) {
            String cp = contactPoint.trim();
            if (AiStringUtils.isNotBlank(cp)) {
                builder.addContactPoint(new InetSocketAddress(cp, cassandra.getPort()));
            }
        }
        builder.withLocalDatacenter(cassandra.getLocalDatacenter());
        builder.withKeyspace(cassandra.getKeyspace());

        CqlSession cqlSession = builder.build();
        log.info("## Cassandra session 初始化完成, contactPoints: {}, keyspace: {}",
                cassandra.getContactPoints(), cassandra.getKeyspace());
        return cqlSession;
    }
}