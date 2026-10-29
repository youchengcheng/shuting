# 书亭（书亭）

基于 **Spring Cloud Alibaba** 微服务架构的内容社区后端（小红书风格），覆盖用户、认证、笔记、评论、点赞收藏、关注关系、计数、搜索、对象存储、分布式 ID，以及一个基于 **Spring AI + pgvector** 的笔记检索助手。

- JDK 17 / Maven 多模块

- 网关统一鉴权（Sa-Token），业务服务只信任网关透传的 `userId`

- 热点数据走 Redis + Caffeine，计数异步落库，搜索通过 Canal 订阅 binlog 增量同步

- AI 服务依赖版本独立（Spring Boot 3.4.5 + Spring AI 1.1.1），详见 [xiaohashu-ai/README.md](file:///c:/Users/YD/Desktop/shu/xiaohashu2/xiaohashu-ai/README.md)

## 技术栈

| 分类   | 选型                                                                                                           |
| ---- | ------------------------------------------------------------------------------------------------------------ |
| 微服务  | Spring Boot 3.0.2、Spring Cloud 2022.0.0、Spring Cloud Alibaba 2022.0.0.0、Nacos、OpenFeign、Spring Cloud Gateway |
| 认证   | Sa-Token 1.38.0（网关全局过滤器 + Redis 会话）                                                                          |
| 存储   | MySQL 8 + MyBatis + Druid、Redis、Cassandra（长正文）、Elasticsearch 7.3 + IK、PostgreSQL + pgvector                  |
| 消息队列 | RocketMQ 2.2.3（client 4.9.4）                                                                                 |
| 数据同步 | Canal 1.1.7（MySQL binlog → ES）                                                                               |
| 定时任务 | XXL-JOB 2.4.1（计数增量对账）                                                                                        |
| 缓存   | Caffeine 本地缓存 + Redis，延迟双删                                                                                   |
| 对象存储 | MinIO 8.2.1 / 阿里云 OSS 3.17.4                                                                                 |
| 接口幂等 | 自定义 `xiaoha-spring-boot-starter-biz-idempotent`（Token + 请求指纹）                                                |
| AI   | Spring AI 1.1.1、通义千问（OpenAI 兼容模式）、Embedding + pgvector                                                       |

## 模块说明

| 模块                                   | 端口   | 职责                                          |
| ------------------------------------ | ---- | ------------------------------------------- |
| `xiaohashu-gateway`                  | 8000 | 统一入口：路由转发、Sa-Token 登录校验、CORS、`userId` 请求头透传 |
| `xiaohashu-oss`                      | 8081 | 文件上传，支持 MinIO / 阿里云 OSS                     |
| `xiaohashu-user`                     | 8082 | 用户注册、资料查询与更新、修改密码                           |
| `xiaohashu-auth`                     | 8083 | 验证码登录、登出、密码修改                               |
| `xiaohashu-kv`                       | 8084 | 笔记正文、评论正文的键值存储（Cassandra）                   |
| `xiaohashu-distributed-id-generator` | 8085 | Leaf 号段 + 雪花 ID                             |
| `xiaohashu-note`                     | 8086 | 笔记发布/更新/删除、可见性、置顶、点赞、收藏、频道与话题、发现页/个人主页列表    |
| `xiaohashu-user-relation`            | 8087 | 关注/取关、关注列表、粉丝列表                             |
| `xiaohashu-count`                    | 8088 | 笔记点赞/收藏/评论数、用户关注/粉丝数聚合落库                    |
| `xiaohashu-ai`                       | 8090 | AI 笔记检索助手、单篇笔记解读、多轮对话（未接入 Nacos，网关固定地址路由）   |
| `xiaohashu-data-align`               | 8091 | 计数增量对账：XXL-JOB 定时把 Redis 日增量刷入 MySQL 日表     |
| `xiaohashu-search`                   | 8092 | ES 笔记/用户搜索、Canal binlog 增量同步、IK 热更新词典       |
| `xiaohashu-comment`                  | 8093 | 评论发布、二级评论、评论点赞、热评缓存                         |
| `xiaoha-framework`                   | -    | 公共基础组件（见下）                                  |

`xiaoha-framework` 子组件：

| 组件                                            | 说明                                                |
| --------------------------------------------- | ------------------------------------------------- |
| `xiaoha-common`                               | 统一响应 `Response` / `PageResponse`、业务异常、日期与 JSON 工具 |
| `xiaoha-spring-boot-starter-biz-context`      | 登录用户上下文，从网关透传的 `userId` 请求头取值                     |
| `xiaoha-spring-boot-starter-biz-operationlog` | `@ApiOperationLog` 接口日志切面                         |
| `xiaoha-spring-boot-starter-biz-idempotent`   | `@Idempotent` 接口幂等，提供 `/idempotent/token` 获取令牌    |
| `xiaoha-spring-boot-starter-jackson`          | Jackson 配置，Long 序列化为 String 防止前端精度丢失              |

## 整体架构

```
                         ┌──────────────────────────────┐
        Client ────────► │ xiaohashu-gateway :8000      │
                         │ 路由 / Sa-Token 鉴权 / CORS  │
                         └──────────────┬───────────────┘
                                        │ userId 请求头透传
   ┌──────────┬──────────┬─────────────┼─────────────┬──────────┬──────────┐
 auth:8083  user:8082  note:8086   comment:8093  relation:8087  search:8092  ai:8090
   │          │          │             │             │             │          │
   └──────────┴──────────┴──────┬──────┴─────────────┴─────────────┘          │
                               │                                             │
          ┌────────────────────┼──────────────────────┐                      │
          ▼                    ▼                      ▼                      ▼
       MySQL 8              Redis               Cassandra            Elasticsearch / pgvector
    （元数据/关系）      （缓存/计数/会话）        （长正文）         （搜索索引 / 向量索引）
                               ▲
                               │ 增量对账
                        data-align :8091（XXL-JOB）
```

## 核心链路

### 1. 登录与鉴权

1. `POST /auth/login` 验证码登录，`StpUtil.login(userId)` 颁发 token 并写入 Redis。
2. 网关 `SaReactorFilter` 拦截 `/**`，白名单（登录、验证码、注册、发现页、详情、搜索等）直接放行，其余校验登录态。
3. 校验通过后 `AddUserId2HeaderFilter` 把 `userId` 写入请求头，下游服务通过 `LoginUserContextHolder` 获取，不再重复解析 token。

### 2. 笔记读写

- **元数据**（标题、话题、作者、可见性、`contentUuid`）落 MySQL `t_note`。

- **正文**落 Cassandra `note_content`，通过 `xiaohashu-kv` 服务读写。

- 详情查询走「Caffeine 本地缓存 → Redis → MySQL/Cassandra」三级缓存；发布、更新、删除时发 MQ 做本地缓存失效与 Redis 延迟双删。

- 点赞/收藏先用 Redis Bitmap + Bloom Filter + Lua 脚本判重，再发 MQ 由计数服务异步处理。

### 3. 计数与对账

1. 点赞、收藏、关注、评论等行为发 MQ。
2. `xiaohashu-count` 消费后用 BufferTrigger 批量聚合，写入 Redis 并异步落 MySQL 计数表。
3. `xiaohashu-data-align` 通过 XXL-JOB 定时把 Redis 中的「今日增量」按分片刷入 MySQL 日增量表，保证缓存与库最终一致。

### 4. 搜索

- `xiaohashu-search` 通过 **Canal** 订阅 `xiaohashu.t_note`、`t_user`、`t_note_count` 的 binlog，增量写入 Elasticsearch。

- ES 索引只存结构化字段（标题、话题、作者、计数等），不存正文。

- IK 分词器支持热更新扩展词典：`ExtDictServiceImpl` 读取本地词典文件，经 `GET /search/ext/dict` 暴露给 IK 定时拉取。

### 5. AI 笔记助手

```
发布/更新/删除笔记 ──MQ(NoteOperateTopic)──► AI 消费 ──► 切块 ──► Embedding ──► pgvector
                                                                              │
用户提问 ──► 向量召回 topK ──► 按 noteId 聚合 ──► 大模型判优 ──► 回 Cassandra 取全文 ──► 润色输出
```

- 向量索引与笔记数据通过 `NoteOperateTopic`（`publishNote` / `updateNote` / `deleteNote`）保持增量一致。

- 检索不到相关笔记时降级为通用助手直答，响应中 `fromNotes=false`。

- 详细配置、接口与建表脚本见 [xiaohashu-ai/README.md](file:///c:/Users/YD/Desktop/shu/xiaohashu2/xiaohashu-ai/README.md)。

## 中间件依赖

以下为 dev 环境默认值，均在各自模块的 `config/application-dev.yml` 中配置。

| 组件            | 默认地址                                    | 用途                             |
| ------------- | --------------------------------------- | ------------------------------ |
| MySQL 8       | `127.0.0.1:3307`，库 `xiaohashu`          | 业务元数据、关系、计数、AI 业务表             |
| Redis         | `127.0.0.1:6379`                        | 缓存、计数、Sa-Token 会话、Bloom/Bitmap |
| RocketMQ      | `127.0.0.1:9876`                        | 点赞/收藏/关注/评论/笔记操作等异步消息          |
| Nacos         | `127.0.0.1:8848`，命名空间 `xiaohashu`       | 服务注册与发现                        |
| Cassandra     | `127.0.0.1:9042`，keyspace `xiaohashu`   | 笔记正文、评论正文                      |
| Elasticsearch | `127.0.0.1:9200`（需安装 IK）                | 笔记/用户搜索                        |
| Canal         | `127.0.0.1:11111`，destination `example` | MySQL binlog 订阅                |
| PostgreSQL    | `127.0.0.1:5432`，库 `robot`（需 pgvector）  | AI 向量库                         |
| XXL-JOB       | Admin `127.0.0.1:7777`，执行器端口 `10001`    | 计数增量对账                         |
| MinIO         | `127.0.0.1:9000`                        | 图片上传（也可切换阿里云 OSS）              |

## 快速开始

### 环境要求

- JDK 17、Maven 3.6+

- 上述中间件已启动（至少 MySQL、Redis、Nacos、RocketMQ；按需启用 ES / Canal / Cassandra / PG）

### 1. 配置

各服务的 dev 配置位于 `src/main/resources/config/application-dev.yml`，仓库中敏感项均为占位符，启动前需替换：

```yaml
spring:
  datasource:
    password: YOUR_DB_PASSWORD_HERE
  data:
    redis:
      password: YOUR_REDIS_PASSWORD_HERE
```

其他常见占位符：`YOUR_DRUID_DECRYPT_KEY_HERE`、`YOUR_DRUID_MONITOR_PASSWORD_HERE`、`YOUR_CANAL_PASSWORD_HERE`、`YOUR_MINIO_ACCESS_KEY_HERE`、`YOUR_ALIYUN_ACCESS_KEY_HERE`、`YOUR_XXL_JOB_TOKEN_HERE`。

搜索模块还需把 `elasticsearch.hotUpdateExtDict` 指向真实存在的 IK 扩展词典文件（默认示例为 `E:\docker\elasticsearch\config\analysis-ik\custom\mydict_hot_update.dic`）。

### 2. 初始化数据库

- 业务库 `xiaohashu` 需先建好基础表（用户、笔记、评论、关系、计数等）。

- AI 模块额外执行：

```bash
mysql -h127.0.0.1 -P3307 -uroot xiaohashu < xiaohashu-ai/xiaohashu-ai-biz/src/main/resources/db/ai_module.sql
```

- PostgreSQL 建库 `robot` 并安装 pgvector，向量表可由应用自动创建（也可手工执行 `db/vector_store.sql`）。

### 3. 构建

```bash
# 全量构建（跳过测试）
mvn clean install -Dmaven.test.skip=true

# 只构建某个模块及其依赖
mvn -pl xiaohashu-note/xiaohashu-note-biz -am install -Dmaven.test.skip=true
```

### 4. 启动

建议顺序：

1. `xiaohashu-distributed-id-generator`（其他服务依赖发号）
2. `xiaohashu-user`、`xiaohashu-auth`、`xiaohashu-kv`、`xiaohashu-oss`
3. `xiaohashu-note`、`xiaohashu-comment`、`xiaohashu-user-relation`、`xiaohashu-count`
4. `xiaohashu-search`、`xiaohashu-data-align`、`xiaohashu-ai`（可选）
5. `xiaohashu-gateway`

```bash
java -jar xiaohashu-gateway/target/xiaohashu-gateway-0.0.1-SNAPSHOT.jar
```

所有请求统一从网关 `http://127.0.0.1:8000` 进入，例如：

```
POST http://127.0.0.1:8000/auth/login
POST http://127.0.0.1:8000/note/publish
POST http://127.0.0.1:8000/search/note
POST http://127.0.0.1:8000/ai/assistant/search
```

## 网关路由

| 路由               | 目标服务                          | 前缀处理          |
| ---------------- | ----------------------------- | ------------- |
| `/auth/**`       | `xiaohashu-auth`              | StripPrefix=1 |
| `/user/**`       | `xiaohashu-user`              | 保留            |
| `/note/**`       | `xiaohashu-note`              | 保留            |
| `/idempotent/**` | `xiaohashu-note`              | 保留            |
| `/relation/**`   | `xiaohashu-user-relation`     | 保留            |
| `/oss/**`        | `xiaohashu-oss`               | StripPrefix=1 |
| `/comment/**`    | `xiaohashu-comment`           | 保留            |
| `/search/**`     | `xiaohashu-search`            | 保留            |
| `/ai/**`         | `http://127.0.0.1:8090`（固定地址） | 保留            |

> 未登录可访问的白名单接口：登录、发送验证码、注册、用户主页、频道/发现页/话题列表、笔记详情、评论列表、关注/粉丝列表、笔记与用户搜索。其余接口需携带 `Authorization: Bearer <token>`。
