-- 为已经创建的通知表补充字段和表注释；新建表使用 notification_module.sql。
-- 共享数据库执行全部四条；拆库时仅在各库执行其已有表对应的 ALTER TABLE。
-- 字段类型、默认值、可空性及自增属性与原建表脚本一致。
-- 使用 INPLACE 和 LOCK=NONE 更新注释；不写入或删除业务数据，可重复执行。

ALTER TABLE t_notification_user_state
    MODIFY COLUMN recipient_id BIGINT NOT NULL COMMENT '通知接收者用户 ID',
    MODIFY COLUMN last_seq BIGINT NOT NULL DEFAULT 0 COMMENT '该用户最新通知序号，跨分类共享，初始为 0',
    MODIFY COLUMN revision BIGINT NOT NULL DEFAULT 0 COMMENT '通知状态版本，新增通知或已读状态变化时递增',
    MODIFY COLUMN like_collection_read_seq BIGINT NOT NULL DEFAULT 0 COMMENT '点赞收藏分类的全部已读水位，该分类 seq 不大于此值视为已读',
    MODIFY COLUMN comment_reply_read_seq BIGINT NOT NULL DEFAULT 0 COMMENT '评论回复分类的全部已读水位，该分类 seq 不大于此值视为已读',
    MODIFY COLUMN follow_read_seq BIGINT NOT NULL DEFAULT 0 COMMENT '关注分类的全部已读水位，该分类 seq 不大于此值视为已读',
    COMMENT='用户通知序号、状态版本和分类已读水位',
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE t_notification
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知记录自增主键',
    MODIFY COLUMN business_key VARCHAR(191) NOT NULL COMMENT '互动业务键，格式为类型:操作用户ID:业务对象ID，与接收者共同去重',
    MODIFY COLUMN recipient_id BIGINT NOT NULL COMMENT '通知接收者用户 ID',
    MODIFY COLUMN actor_id BIGINT NOT NULL COMMENT '触发互动的操作用户 ID',
    MODIFY COLUMN type VARCHAR(24) NOT NULL COMMENT '互动类型：NOTE_LIKE 笔记点赞，COMMENT_LIKE 评论点赞，NOTE_COLLECT 笔记收藏，COMMENT 评论，REPLY 回复，FOLLOW 关注',
    MODIFY COLUMN category VARCHAR(32) NOT NULL COMMENT '通知分类：LIKE_COLLECTION 点赞收藏，COMMENT_REPLY 评论回复，FOLLOW 关注',
    MODIFY COLUMN recipient_role VARCHAR(24) NOT NULL COMMENT '接收者角色：OWNER 笔记或评论作者，REPLY_TARGET 被回复用户，FOLLOW_TARGET 被关注用户',
    MODIFY COLUMN note_id BIGINT NULL COMMENT '关联笔记 ID，关注通知为空',
    MODIFY COLUMN comment_id BIGINT NULL COMMENT '关联评论 ID：评论或回复时为新发表记录，评论点赞时为被点赞评论，其他类型为空',
    MODIFY COLUMN reply_comment_id BIGINT NULL COMMENT '被回复评论 ID，非回复通知为空',
    MODIFY COLUMN occurred_at DATETIME NOT NULL COMMENT '原始互动发生时间，Asia/Shanghai 时区，精确到秒',
    MODIFY COLUMN seq BIGINT NOT NULL COMMENT '接收者内递增的通知序号，跨分类共享，用于排序、快照分页和已读水位',
    MODIFY COLUMN read_at DATETIME NULL COMMENT '单条标记已读时间，为空时仍需结合分类已读水位判断',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '通知记录入库时间',
    COMMENT='用户互动通知记录',
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE t_notification_outbox
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '发件箱记录自增主键',
    MODIFY COLUMN producer_service VARCHAR(64) NOT NULL COMMENT '来源业务服务名称，对应 spring.application.name',
    MODIFY COLUMN business_key VARCHAR(191) NOT NULL COMMENT '互动业务键，格式为类型:操作用户ID:业务对象ID，与来源服务共同去重并保留首次事件',
    MODIFY COLUMN event_id CHAR(36) NOT NULL COMMENT '通知事件唯一 ID，未提供时生成 UUID，首次入库后重投保持不变',
    MODIFY COLUMN payload LONGTEXT NOT NULL COMMENT '待投递的通知事件完整 JSON 内容',
    MODIFY COLUMN state VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：PENDING 待发送，SENDING 发送中，SENT 已发送',
    MODIFY COLUMN next_attempt_at DATETIME NOT NULL COMMENT '待发送记录下次允许尝试投递的时间',
    MODIFY COLUMN attempts INT NOT NULL DEFAULT 0 COMMENT '累计领取发送次数，包含首次领取和租约过期后的重新领取',
    MODIFY COLUMN lease_owner VARCHAR(64) NULL COMMENT '当前领取事件的投递实例 UUID，无租约时为空',
    MODIFY COLUMN lease_version BIGINT NOT NULL DEFAULT 0 COMMENT '每次领取时递增的租约版本，防止旧任务覆盖当前状态',
    MODIFY COLUMN lease_until DATETIME NULL COMMENT '当前发送租约到期时间，到期后允许重新领取',
    MODIFY COLUMN last_error VARCHAR(1000) NULL COMMENT '最近一次发送失败原因，发送成功后清空',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '事件写入来源业务数据库的时间',
    MODIFY COLUMN sent_at DATETIME NULL COMMENT '消息获得 MQ 成功确认后标记已发送的时间',
    COMMENT='来源业务事务内的通知事件发件箱',
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE t_notification_event_error
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '异常事件记录自增主键',
    MODIFY COLUMN source VARCHAR(64) NOT NULL COMMENT '事件来源：通知消费者为消息主题，来源业务服务为服务名:消息主题',
    MODIFY COLUMN event_identity VARCHAR(64) NOT NULL COMMENT '来源、换行符和原始消息的 SHA-256 十六进制摘要，用于同来源消息去重',
    MODIFY COLUMN payload LONGTEXT NOT NULL COMMENT '无法处理的原始消息内容',
    MODIFY COLUMN reason VARCHAR(1000) NOT NULL COMMENT '事件无法处理的原因，如参数校验失败或回复目标不存在',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '异常事件隔离入库时间',
    COMMENT='无法处理的通知事件隔离记录',
    ALGORITHM=INPLACE,
    LOCK=NONE;
