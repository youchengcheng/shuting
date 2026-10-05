-- =====================================================================
-- 小哈书 AI 模块建表脚本（业务库：MySQL xiaohashu）
-- 说明：AI 模块只读取 t_note，不修改它；下面三张表是 AI 模块自己的表。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 笔记检索索引镜像：记录每篇笔记在 pgvector 里被切成了多少 chunk
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_note_index` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id`       BIGINT       NOT NULL COMMENT '笔记 ID',
    `creator_id`    BIGINT       DEFAULT NULL COMMENT '发布者 ID',
    `title`         VARCHAR(255) DEFAULT NULL COMMENT '笔记标题',
    `topic_name`    VARCHAR(64)  DEFAULT NULL COMMENT '话题名称',
    `content_uuid`  VARCHAR(64)  DEFAULT NULL COMMENT '正文在 Cassandra 中的 UUID',
    `content_hash`  VARCHAR(64)  DEFAULT NULL COMMENT '标题+正文的 MD5，用于判断是否需要重建',
    `chunk_total`   INT          NOT NULL DEFAULT 0 COMMENT '切分出的 chunk 数量',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '0：已删除 1：已索引',
    `create_time`   DATETIME     DEFAULT NULL,
    `update_time`   DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_id` (`note_id`),
    KEY `idx_status_update_time` (`status`, `update_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 笔记检索索引镜像';

-- ---------------------------------------------------------------------
-- 2. 笔记 AI 解读结果缓存
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_note_analysis` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id`      BIGINT       NOT NULL COMMENT '笔记 ID',
    `user_id`      BIGINT       NOT NULL COMMENT '提问用户 ID',
    `title`        VARCHAR(255) DEFAULT NULL COMMENT '笔记标题',
    `content_hash` VARCHAR(64)  DEFAULT NULL COMMENT '内容指纹，内容变了缓存失效',
    `result`       MEDIUMTEXT   COMMENT 'AI 解读结果（Markdown）',
    `create_time`  DATETIME     DEFAULT NULL,
    `update_time`  DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_user` (`note_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记 AI 解读结果';

-- ---------------------------------------------------------------------
-- 3. AI 对话会话
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_chat` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uuid`        VARCHAR(64)  NOT NULL COMMENT '会话 UUID',
    `user_id`     BIGINT       NOT NULL COMMENT '归属用户',
    `title`       VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `create_time` DATETIME     DEFAULT NULL,
    `update_time` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uuid` (`uuid`),
    KEY `idx_user_update_time` (`user_id`, `update_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 对话会话';

-- ---------------------------------------------------------------------
-- 4. AI 对话消息
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_chat_message` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `chat_uuid`   VARCHAR(64) NOT NULL COMMENT '会话 UUID',
    `user_id`     BIGINT      NOT NULL COMMENT '归属用户',
    `role`        VARCHAR(16) NOT NULL COMMENT 'user / assistant',
    `content`     MEDIUMTEXT  COMMENT '消息内容',
    `note_refs`   VARCHAR(512) DEFAULT NULL COMMENT '本轮引用的笔记 ID，JSON 数组',
    `create_time` DATETIME    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_chat_uuid_create_time` (`chat_uuid`, `create_time`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 对话消息';