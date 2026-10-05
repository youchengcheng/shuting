# xiaohashu-ai（小哈书 AI 模块）

只做一件事：**用户用自然语言提问 → 检索全站已发布笔记 → AI 判断笔记是否真的对题 → 润色后返回给用户**。

## 1. 模块结构

```
xiaohashu-ai/                    聚合工程（packaging=pom）
└── xiaohashu-ai-biz/            可执行服务，端口 8090
```

### 为什么依赖版本不继承父工程

Spring AI 1.1.1 要求 Spring Boot >= 3.4，而 xiaohashu2 根 pom 锁定的是 Boot 3.0.2 + Spring Cloud 2022。
两者无法共存，所以：

- `xiaohashu-ai/pom.xml` 的 parent 直接指向 `spring-boot-starter-parent:3.4.5`；
- 依赖版本（Spring AI、MyBatis-Plus、OkHttp、Hutool…）全部由本模块自己管理；
- 只在 `xiaohashu2/pom.xml` 的 `<modules>` 里参与聚合构建，不继承根 pom 的任何 dependencyManagement。

## 2. 笔记正文在 Cassandra，检索怎么做

现状：

| 数据 | 存放位置 | 查询能力 |
| --- | --- | --- |
| 笔记元数据（标题、话题、作者、可见性、contentUuid） | MySQL `t_note` | 正常 SQL |
| 笔记正文（长文本） | Cassandra `note_content(id UUID 主键, content)` | **只能按主键点查** |
| ES `note` 索引 | 不含正文 | 只能搜标题等 |

所以「按内容语义检索」不能直接查 Cassandra，必须建一份**可检索副本**：

```
MySQL t_note（元数据） ─┐
                        ├─► 切块(NoteChunker) ─► 向量化 ─► pgvector (t_ai_note_vector_store)
Cassandra note_content ─┘                                   +
                                     MySQL t_ai_note_index（noteId → chunk 数，用于精准删除/覆盖）
```

检索走三级流水线：

1. **召回（向量）**：用户提问向量化后，从 pgvector 召回 topK 个 chunk（默认 40），按 noteId 聚合取 20 篇，回 MySQL 校验可见性。
2. **判优（大模型）**：把候选笔记片段批量送给 LLM 打分（0~100），过滤掉低于阈值（默认 60）的，按分数排序取 top 3。
3. **润色（大模型）**：对判优通过的笔记，用 `contentUuid` 回 Cassandra 取**全文**，交给 LLM 整理润色，标注「来源：《标题》」。

## 3. 索引怎么保持和笔记数据一致

- **增量（主）**：note 服务在「发布 / 更新 / 删除 / 修改可见性」时发 `NoteOperateTopic` 消息
  （Tag：`publishNote` / `updateNote` / `deleteNote`），本模块的 `NoteIndexSyncConsumer` 消费后
  重建或删除该笔记索引；内容 MD5 未变则自动跳过，不重复烧 embedding 额度。
- **全量（兜底）**：`POST /ai/assistant/index/rebuild` 手动触发；
  或配置 `xiaohashu.ai.index.rebuild-enabled=true` 开启每日定时重建；或 `rebuild-on-startup=true` 启动时重建。

## 4. 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/ai/health` | 存活探针 |
| GET | `/ai/whoami` | 当前登录用户探针 |
| POST | `/ai/assistant/search` | **核心**：检索 + 润色，返回回答 + 引用笔记 |
| POST | `/ai/assistant/search/stream` | 同上，SSE 流式返回润色过程 |
| POST | `/ai/assistant/related` | 只返回相关笔记，不润色 |
| GET | `/ai/assistant/index/stats` | 索引统计（笔记数 / 向量数） |
| POST | `/ai/assistant/index/rebuild` | 全量重建索引 |
| POST | `/ai/note/analyze` | 单篇笔记 AI 解读（带内容指纹缓存） |
| POST | `/ai/chat/send` | 多轮对话（同步，落库） |
| POST | `/ai/chat/stream` | 多轮对话（SSE） |
| GET | `/ai/chat/list` | 会话列表 |
| GET | `/ai/chat/messages?chatUuid=` | 会话消息 |
| POST | `/ai/chat/delete?chatUuid=` | 删除会话 |

> SSE 接口（`/ai/assistant/search/stream`、`/ai/chat/stream`）在失败时不会中断连接，
> 而是以一段 `[出错了] xxx` 的文本流返回，前端据此提示错误。

网关已加 `/ai/**` → `http://127.0.0.1:8090` 的固定地址路由（AI 服务未接入 Nacos），
并沿用 Sa-Token 登录校验，登录用户 ID 通过 `userId` 请求头透传到本模块。

## 5. 建表与配置

1. 业务库执行 `xiaohashu-ai-biz/src/main/resources/db/ai_module.sql`
   （`t_ai_note_index`、`t_ai_note_analysis`、`t_ai_chat`、`t_ai_chat_message`）。
2. PostgreSQL 建库 `robot` 并安装 pgvector 扩展；应用默认开启 `initialize-schema` 自动建
   `t_ai_note_vector_store`，也可先手工执行 `db/vector_store.sql`。
   > 注意：向量表用独立的 `t_ai_note_vector_store`，**不要复用**旧面试项目的 `t_vector_store`
   > （旧表主键是 uuid、且含历史面试向量，与本模块 `noteId:chunkIndex` 的文本主键不兼容）。
   > dev 环境已内置本机可用的默认值：MySQL `root/123456`、Redis 无密码、PG `postgres/postgres`、
   > Cassandra `127.0.0.1:9042/xiaohashu`，因此**只有 `DASHSCOPE_API_KEY` 是必填的**。

3. 环境变量（dev 环境都有默认值，按需覆盖）：

| 变量 | 说明 |
| --- | --- |
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key，**必填** |
| `DASHSCOPE_BASE_URL` | 默认 `https://dashscope.aliyuncs.com/compatible-mode` |
| `DASHSCOPE_CHAT_MODEL` | 默认 `qwen-plus` |
| `DASHSCOPE_EMBEDDING_MODEL` | 默认 `text-embedding-v4`（维度 1536，需与 `xiaohashu.ai.vector.dimensions` 一致） |
| `XIAOHASHU_DB_*` | 业务 MySQL（127.0.0.1:3307/xiaohashu） |
| `XIAOHASHU_VECTOR_DB_*` | pgvector 所在 PostgreSQL（127.0.0.1:5432/robot） |
| `CASSANDRA_*` | Cassandra 连接（127.0.0.1:9042 / xiaohashu） |
| `ROCKETMQ_NAME_SERVER` | 默认 `127.0.0.1:9876` |

> 注意：`rocketmq.name-server` 的前缀是 `rocketmq`，不是 `spring.rocketmq`；
> 且 `RocketMQAutoConfiguration` 由 `config/RocketMQConfig` 显式 `@Import`
> （rocketmq-spring-boot 2.2.3 用的是 Boot 3 已不读取的 `spring.factories`）。

## 6. 启动

```bash
# 首次：先建表
mysql -h127.0.0.1 -P3307 -uroot xiaohashu < xiaohashu-ai-biz/src/main/resources/db/ai_module.sql

# 构建（在 xiaohashu2 目录）
mvn -pl xiaohashu-ai/xiaohashu-ai-biz -am "-Dmaven.test.skip=true" install

# 运行
java -jar xiaohashu-ai/xiaohashu-ai-biz/target/xiaohashu-ai-biz-0.0.1-SNAPSHOT.jar
```

索引为空时先调一次 `POST /ai/assistant/index/rebuild` 把存量笔记灌进向量库。

**临时关闭索引**：`--xiaohashu.ai.index.enabled=false`
（RocketMQ 没起时消费者启动会失败导致应用起不来，与仓库其它模块行为一致；此时可用该开关先跑起来）。