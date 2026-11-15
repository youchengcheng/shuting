# 通知模块：代码审阅说明

本目录是首版通知模块的实现，当前交付用于代码 review。默认未启用通知消费与源事件捕获；SQL 不会自动执行。审核通过后再安排环境配置、建表和启用。

## 1. 推荐审阅顺序

| 顺序 | 入口 | 核对内容 |
|---|---|---|
| 1 | `xiaohashu-notification-api/.../dto/NotificationEvent.java` | 六种类型、事件时间、操作者及收件人、笔记与评论路由 |
| 2 | `xiaohashu-notification-biz/.../service/NotificationService.java` | 唯一业务键、排除自己、回复双收件人去重、用户序号、已读快照 |
| 3 | `xiaohashu-notification-biz/.../consumer/` | 原点赞独立消费、cutoff、坏事件隔离、数据库失败重试 |
| 4 | `../xiaoha-framework/xiaoha-spring-boot-starter-notification-outbox/` | 本地事务写事件、租约、重投与发送后崩溃恢复 |
| 5 | 源服务 `CommentNotificationPersistenceService`、`FollowNotificationPersistenceService`、`CollectUnCollectNoteConsumer` | 仅真实新增/正向关系成功落库后写 outbox |
| 6 | `NotificationPreviewService`、源服务内部查询与评论权限检查 | 私密、删除、依赖失败时隐藏标题、封面与评论摘要 |
| 7 | `NotificationController`、网关和 biz-context | Bearer 与登录用户绑定、内部令牌、SSE 错误响应 |
| 8 | `../../xiaohashu-vue3/src/stores/notification.js`、`useNotificationStream.js`、通知页面 | 账号切换、迟到响应、单请求合并、逐条列表、相关评论定位 |

包路径统一以 `src/main/java/com/quanxiaoha/` 为前缀。完整改动清单、执行过的验证及审阅补丁在 `codex_doc/2026-11-14-通知模块代码审阅说明.md`。

本轮验证：通知 40 项（含真实 MySQL 7 项）、源服务/outbox 29 项、网关/内部鉴权 14 项、前端 13 项，共 96 项实际测试通过。通知 14 模块、源代码镜像 28 模块编译与测试编译通过，前端生产构建通过。复跑说明见 `xiaohashu-notification-biz/src/test/README.md`。

## 2. 已实现的业务规则

- 类型：`NOTE_LIKE`、`COMMENT_LIKE`、`NOTE_COLLECT`、`COMMENT`、`REPLY`、`FOLLOW`。
- 分类：`LIKE_COLLECTION`、`COMMENT_REPLY`、`FOLLOW`，所有通知逐条存储和展示。
- 去重键：`TYPE:actorId:objectId`；评论/回复对象是新 `commentId`，关注对象是被关注用户。通知表再以收件人参与唯一约束。
- 一级评论提醒笔记作者；回复提醒被回复者及笔记作者。去掉操作者本人，收件人相同保留 `REPLY_TARGET` 语义。
- 取消互动保留历史；再次进行同类互动不再生成第二条提醒。首版不清理通知、outbox 已发送记录和去重事实。
- 评论赞携带 `commentCreatorId` 和 `noteId`。评论在消费前被删除仍可生成历史通知。
- 不可访问的对象保留通知和未读数，预览置空；点击仍执行本人单条已读，再提示无法查看。
- 通知列表按用户内 `seq` 倒序。发生时间用于展示日期，迟到事件获得新的序号，不会被此前全部已读吞掉。

点赞分支直接订阅原 MQ 的正向事件，不等待原点赞关系消费者。原点赞关系合并算法、计数算法以及原 API 异步投递方式保持原逻辑。因此进入 MQ 的点赞可以生成通知；未成功进入原 MQ 的互动仍可能缺失。

## 3. 投递与事务边界

### 原点赞

| Topic | Tag | 通知专用消费组 |
|---|---|---|
| `LikeUnlikeTopic` | `Like` | `xiaohashu_notification_note_like_v1` |
| `CommentLikeUnlikeTopic` | `Like` | `xiaohashu_notification_comment_like_v1` |
| `NotificationEventTopic` | 所有 | `xiaohashu_notification_committed_v1` |

前三个消费者仅在 `notification.enabled=true` 时创建。独立组不会抢走原关系或计数消费者的数据。新组从 broker 保留的最早数据读取并按原发生时间过滤；已有组继续其原 offset。

### 收藏、关注、评论与回复

源数据库的业务行和 `t_notification_outbox` 在同一本地事务提交。outbox 写失败使业务事务回滚，原消费者重试。

领取任务：`PENDING → SENDING`，增加 `lease_version`、设置 15 秒租约，事务完成后进行最长 3 秒的同步发送。收到 `SEND_OK` 后按领取人及版本标记 `SENT`；失败退避，最长约 256 秒。过期 `SENDING` 可重新领取。

消息已经发送而进程在标记 `SENT` 前退出时会重复发送；通知唯一约束保证重复投递不会重置已读或增加未读。一个回复的两个收件人分别提交，失败重投补齐尚未提交的收件人。

确定的坏事件先落到对应数据库的 `t_notification_event_error` 才确认消费。通知端使用来源及原始内容的 SHA-256 作为隔离标识；隔离落库失败和正常事件的数据库异常交给 MQ 重试。通知消费者最多重试 16 次后使用 broker 死信机制，需运维监控和回放。

## 4. 用户序号、分页与已读

同一用户的新增通知、单条已读、分类全部已读均锁定 `t_notification_user_state` 行。

- 首次新增：`last_seq + 1`、`revision + 1`，通知行与用户状态一起提交。
- 列表首次取用户最新 `last_seq` 为 `snapshotSeq`。后续页使用相同分类、快照和游标，限定 `seq <= snapshotSeq`。
- 单条已读：只能访问本人记录，幂等。其他用户的通知返回 404。
- 全部已读：推进当前分类水位至请求 `snapshotSeq`，有效已读为 `read_at != NULL 或 seq <= 分类水位`。不逐行修改历史通知。
- 快照外新增仍然未读；重复/较旧的全部已读请求不会倒退水位。
- `read-state` 用于校准迟到分页结果，最多 100 个本人通知 ID。

列表/摘要/已读状态查询采用 MySQL 可重复读事务，保持序号、版本、未读数的一致快照。内容预览在快照事务外批量查询，权限失败时保守隐藏。

## 5. HTTP 契约

普通响应沿用 `{success,data,message,errorCode}`。通知 ID、路由 ID、序号和版本均返回十进制字符串。分页大小 1–50，默认 20。分类必须是上面的英文枚举值。

| 方法与路径 | 请求 | data |
|---|---|---|
| POST `/notification/list` | `{category,cursor?,snapshotSeq?,pageSize?}` | `{items,nextCursor,hasMore,snapshotSeq,revision}` |
| GET `/notification/summary` | 无 | `{unreadCounts,totalUnread,lastSeq,revision,readAllSeq}` |
| POST `/notification/read` | `{notificationId}` | `{changed,revision,summary,notificationId,read:true}` |
| POST `/notification/read-all` | `{category,snapshotSeq}` | `{changed,revision,summary,category,snapshotSeq}` |
| POST `/notification/read-state` | `{notificationIds}`，1–100 | `{items:[{notificationId,read}],revision,readAllSeq}` |
| GET `/notification/stream` | Bearer | SSE |

列表条目：

```json
{
  "notificationId": "101",
  "seq": "8",
  "type": "REPLY",
  "category": "COMMENT_REPLY",
  "actor": {"userId": "42", "nickname": "用户", "avatar": ""},
  "recipientRole": "REPLY_TARGET",
  "noteId": "9",
  "commentId": "31",
  "replyCommentId": "30",
  "occurredAt": "2026-11-14T12:00:00",
  "read": false,
  "contentStatus": "AVAILABLE",
  "preview": {"title": "笔记标题", "cover": "", "summary": "评论内容"}
}
```

`contentStatus` 为 `AVAILABLE`、`UNAVAILABLE` 或 `TEMPORARY_UNAVAILABLE`。后两者的 preview 三个字段均为空字符串。接收人始终取可信登录上下文并与 Bearer 的 Redis 登录身份再次绑定，请求不接受任意 recipientId。

HTTP 状态：参数错误 400、登录无效 401、本人记录不存在 404、单用户 SSE 超限 429、依赖故障或模块关闭 503。建立 SSE 前的错误返回 JSON；已提交的流通过控制事件结束。

### 评论上下文

`POST /comment/context` 请求 `{noteId,commentId}`，返回 `{mode:"RELATED",noteId,targetCommentId,unavailableReason,root,target,repliedTo}`。评论项包含 `commentId,userId,noteId,parentId,replyCommentId,replyUserId,level,avatar,nickname,content,imageUrl,createTime,likeTotal,childCommentTotal,liked`。

根评论、目标和被回复评论均校验属于请求笔记及相同评论线程。前端相关评论模式高亮目标，允许切回全部评论；浮层关闭保留通知背景和滚动位置。

## 6. SSE 与前端状态

`ready`、`changed`、`heartbeat` 都携带 `{revision,lastSeq}`。它们仅提醒刷新，以 REST 返回的通知、摘要及已读状态为准。

- Redis Pub/Sub 跨实例广播变化；广播失败不回滚已提交通知，15 秒心跳从数据库校准。
- 每用户最多 10 个流，每实例 1000 个；发送线程池及队列有上限，慢连接关闭后释放容量。
- 流最长 5 分钟，之后由前端自动重连校准。通知启用时要求 Tomcat HTTP/1.1 NIO connector；本项目 Tomcat 10.1.5 的 connectionTimeout=10000 同时限制 socket 写等待，其他 connector 会拒绝启动。
- 关闭先移除连接并释放容量，再由独立 2 线程、256 队列的关闭执行器完成；队列满不在调度/广播线程等待 emitter 锁，有限 servlet 生命周期兜底。
- 15 秒心跳检查 token 与连接用户仍绑定；过期发送 `auth-expired` 关闭，依赖故障发送 `service-unavailable` 关闭。
- 前端用 fetch Bearer 流；45 秒无数据重连，1–30 秒退避，重连、重新可见时拉取 REST。
- 账号退出或切换提升 session generation，取消旧请求与旧流并清空通知内存。旧请求即使返回也不能覆盖新账号。
- 变化提示不取消正在翻页的请求；旧页仍可加入，但其已读状态以较新版本和水位校准。
- 新通知更新角标及“有新通知”入口，不强制跳到列表顶部。

## 7. 审核通过后的配置与启用步骤

本节供之后落实时使用，本次代码交付没有执行这些步骤。

1. 确认数据库边界：通知库需要 `t_notification_user_state`、`t_notification`、`t_notification_event_error`；每个源库需要本地 `t_notification_outbox` 和源坏事件隔离表。如当前共享数据库，仅创建一次。SQL 位于 `xiaohashu-notification-biz/src/main/resources/db/notification_module.sql`，没有自动迁移。`CREATE TABLE IF NOT EXISTS` 不会更新已有表的注释；已有表可执行 `xiaohashu-notification-biz/src/main/resources/db/notification_module_comments.sql`，为四张表补齐字段注释和表注释。
2. 各服务配置同一个 `INTERNAL_API_TOKEN`，通过安全环境变量注入。网关删除外部同名头，Feign 只对 `/internal/` 添加令牌。内部查询直连缺少配置返回 503，错误/重复令牌返回 401。
3. 配置 DB、Redis、Nacos、RocketMQ；通知服务环境变量见 `application.properties`，不含生产密码。通知默认采用现有 Nacos namespace `xiaohashu`、group `DEFAULT_GROUP`；`NACOS_NAMESPACE`/`NACOS_GROUP` 覆盖时需与网关和源服务同步。源服务端口仅内网可访问，公开业务请求通过网关；内部共享令牌不能替代源服务端口的网络隔离。网关代理需允许 SSE、关闭缓冲，并使用大于心跳间隔的流空闲超时。
4. 先部署仍关闭的源服务及通知服务版本，确保所有实例都能补全评论赞字段。
5. 所有源服务和通知服务 JVM 使用 Asia/Shanghai 时区（例如启动参数 `-Duser.timezone=Asia/Shanghai`），因为原互动事件的 LocalDateTime 不携带偏移量。选择未来统一整秒边界，使用 ISO 示例格式 `notification.enabled-at=2026-11-20T10:00:00`，各服务保持一致。示例时间并非本次上线决定。
6. 在边界前准备源服务 `notification.events.enabled=true` 与通知服务 `notification.enabled=true`，确保全部旧源实例已替换，并给新消费组留足过滤保留历史、追平积压的时间。读取最早保留 offset 并过滤 cutoff；若 broker 已无法保留边界数据，应停止启用并补充回放方案。
7. 验证六类真实互动、两实例 SSE、多设备已读、Redis 中断恢复、MQ 重启/死信回放及正常依赖下 5 秒目标，再宣布启用完成。

关闭源捕获后成功落库的动作不会自动回填；不能将简单关闭开关视作无损停机。升级/暂停投递时保留 outbox 数据，并在操作前明确是否允许记录空窗。重启与扩容复用既有通知消费组名，不修改原消费组 offset。

首版无需通知数据清理；数据归档、消息保留期、broker 刷盘/复制和业务流量下的容量压测另行评估。测试中的独立 MySQL 与前端测试夹具不替代真实 Redis/RocketMQ/Nacos 多实例联调。
