# 小哈书（xiaohashu）× AI 助手融合实施方案（v1.1 执行版）

> 版本：v1.1（代码核对版）
> 日期：2026-09-05
> 状态：待执行
> 适用范围：xiaohashu2（Java 后端）+ xiaohashu-vue3（Vue3 前端）+ project/xiaoha-ai-robot（AI 后端/前端）
> 说明：本版已对三个项目源码逐一核对（模块结构、版本、鉴权链路、接口、表结构、前端 API/路由），修正 v1.0 与代码不一致之处，并补充数据隔离、密钥安全、SSE 网关超时等遗漏项。本文件为唯一执行依据，旧 v1.0 文件请归档。

---

## 0. 方案摘要与总体策略

### 0.1 融合目标

- 把 project 中的整套 AI 助手能力（对话聊天、联网搜索、深度思考、AI 客服/RAG 分片上传）以原生模块形态并入 xiaohashu2 + xiaohashu-vue3。
- 融合完成后只保留一套后端（xiaohashu2）与一套前端（xiaohashu-vue3），project 不再单独运行，源码归档。
- 小红书原有业务功能零改动、零破坏；AI 对话数据与小红书用户 ID 强绑定，实现用户级数据隔离。
- AI 接口复用小红书 Sa-Token 鉴权，废弃 AI 项目自带的无鉴权形态。

### 0.2 总体策略（定案）

采用“后端新增微服务模块 + 前端新增页面模块”的隔离式融合，不改动小红书业务模块源码：

| 层 | 策略 | 说明 |
| --- | --- | --- |
| 后端 | 新增 xiaohashu-ai（含 xiaohashu-ai-biz 业务模块） | 仿 xiaohashu-note 两级结构；独立声明 Spring Boot 3.4.5，不继承父 POM 的 3.0.2，避开 Spring Cloud Alibaba 2022.0.0.0 冲突 |
| 服务发现 | AI 服务不注册 Nacos，网关固定 IP 路由 | http://127.0.0.1:8090，版本隔离、回滚最简 |
| 鉴权 | 复用网关 Sa-Token + userId 请求头透传 | /ai/** 不进白名单，强制登录；AI 模块引入 biz-context starter 自动接管用户上下文 |
| 数据库 | 对话等 4 张业务表迁入 MySQL xiaohashu（新增表）；向量库保留 PG robot | 双数据源见 4.2；备选方案见决策 D1 |
| 前端 | 新增 /ai/* 页面与依赖 | 复用小红书 axios（自动带 token）、Pinia、Tailwind；Ant Design Vue 按需加载；登录沿用小红书弹窗 |
| 收尾 | project 停运并归档 | 验收通过后执行 |

### 0.3 三个决策点（执行前拍板，默认已给）

| 编号 | 决策点 | 默认建议（方案按此编写） | 备选 |
| --- | --- | --- | --- |
| D1 | AI 业务表落库位置 | 迁 MySQL xiaohashu，业务数据统一运维 | 方案 B：业务表继续留 PG robot，仅加 user_id；改动最小、无双数据源，代价是双库运维 |
| D2 | 客服知识库是否按用户隔离 | 按用户隔离：文件表加 user_id、(user_id,file_md5) 唯一，向量元数据带 userId 并检索过滤 | 全局共享知识库：需管理员角色与后台界面，v1 不推荐 |
| D3 | AI 页面布局形态 | v1 独立全屏工作台路由（保留 AI 自身 Layout/Sidebar），入口放小红书左导航 | 直接嵌入 BasicLayout children：需重构 AI 页面布局，v2 再评估 |

---
## 一、项目现状分析（源码核对结论）

### 1.1 小红书后端 xiaohashu2

**架构与版本（核对自根 pom.xml）**

- Spring Cloud Alibaba 微服务：Spring Boot 3.0.2、Spring Cloud Alibaba 2022.0.0.0、Spring Cloud 2022.0.0、JDK 17。
- 根 POM 模块：xiaoha-framework（common / biz-context / biz-operationlog / jackson）、xiaohashu-gateway、auth、oss、user、kv、distributed-id-generator、note、user-relation、count、data-align、search、comment。
- 业务模块为两级结构（如 xiaohashu-note/xiaohashu-note-biz），包名 com.quanxiaoha.xiaohashu.*。
- 网关 xiaohashu-gateway：端口 8000；路由 lb:// 各微服务；Sa-Token 1.38.0 SaReactorFilter 全量拦截 + 白名单放行登录/注册/公开浏览接口；AddUserId2HeaderFilter（GlobalFilter）从 Redis 取 token 对应用户并写入请求头 userId（GlobalConstants.USER_ID="userId"，统一转 Long）。
- 用户上下文：xiaoha-spring-boot-starter-biz-context 通过 AutoConfiguration.imports 自动装配；HeaderUserId2ContextFilter 把请求头 userId 写入 LoginUserContextHolder（ThreadLocal，请求结束清理）。
- 数据库：MySQL 127.0.0.1:3307/xiaohashu（Druid 连接池，密码为 Druid 加密串+config.decrypt 公钥，参考 xiaohashu-note/xiaohashu-note-biz 的 application-dev.yml）；Redis 127.0.0.1:6379；Nacos 127.0.0.1:8848（namespace xiaohashu）；RocketMQ 127.0.0.1:9876。
- 服务端口示例：gateway 8000、note-biz 8086，其余业务 808x。
- 根 POM 已托管 sa-token、druid 1.2.23、mysql-connector 8.0.29、guava、hutool、commons-lang3 等版本，AI 模块可复用版本号。
- 业务模块使用 xiaoha-common 的 Response/PageResponse/统一异常；@ApiOperationLog 来自 xiaoha-spring-boot-starter-biz-operationlog。
- 关键事实：网关白名单只放过公开接口，新增的 /ai/** 默认被拦截要求登录，无需改 SaTokenConfigure。

### 1.2 小红书前端 xiaohashu-vue3

- Vue 3.5 + Vite 6 + Pinia（已启用 pinia-plugin-persistedstate）+ Tailwind CSS v4（@tailwindcss/vite）+ @vueuse；无 UI 组件库。
- src/axios.js：baseURL /api；请求拦截器从 stores/user.js 读 token 注入 Authorization: Bearer xxx；响应拦截器对 401 自动 logout 并提示。
- 路由：createWebHistory；BasicLayout（AppHeader+AppSidebar+内容区）下挂 /discover、/notifications、/user/profile/:userId、/search、/user/:userId/relation。
- 登录形态：无独立登录路由页，登录为弹窗组件 components/auth/LoginModal.vue（props.visible + emit('update:visible')，支持 v-model:visible），由 AppSidebar、NoteCard、Profile 等通过 inject('showLoginModal') 打开。
- AppSidebar.vue：左导航含 发现/发布/登录按钮/我，isLoggedIn 来自 userStore；AI 入口菜单加在此处，未登录点击需弹登录框。
- Vite dev proxy：/api → http://127.0.0.1:8000（rewrite 去掉 /api 前缀），请求直达网关。

### 1.3 AI 后端 xiaoha-ai-robot-springboot

**架构与版本（核对自 pom.xml / application-dev.yml）**

- 单模块 Spring Boot：parent 3.4.5；pom 中 java.version 目前为 21（.idea 语言级别 JDK_21）。v1.0 声称的“JDK17 已验证”在仓库内无直接证据，融合第一步必须重新冒烟验证（见 4.0 门禁）。
- Spring AI 1.1.1（openai / pgvector / markdown reader / MCP stdio client）、MyBatis Plus 3.5.12（mybatis-plus-spring-boot3-starter，注解式 Mapper 无 XML）、HikariCP、p6spy（dev 用 P6SpyDriver 包装 PG 驱动）、OkHttp、Jsoup、log4j2。
- 端口 8080；dev 数据源 PostgreSQL localhost:5432/robot（postgres/postgres）；表：t_chat、t_chat_message、t_ai_customer_service_file_storage、t_file_chunk_info、t_vector_store（pgvector，由 starter 管理）。
- 大模型：阿里云百炼 OpenAI 兼容端点，对话模型由前端下发（qwen3.8-max 等为展示映射），embedding qwen3.7-text-embedding（1536 维）；客服模型 deepseek-v4-flash、温度 0。
- 外部依赖：SearXNG http://localhost:8888/search（联网搜索，取 10 条）；MCP stdio 高德地图（cmd /c npx @amap/amap-maps-mcp-server）。
- 客服文件落盘：D:\critic\xiaoha-ai-robot（主目录+chunk 分片目录）；分片合并后异步事件（AsyncEventConfig + AiCustomerServiceMdUploadedListener）解析入库并写向量库。
- 包名 com.quanxiaoha.ai.robot；自带独立 Response/PageResponse/BizException/ResponseCodeEnum/GlobalExceptionHandler/ApiOperationLogAspect/CorsConfig/JacksonConfig，融合时统一替换为小红书体系。
- 无任何鉴权：接口裸奔；前端甚至硬编码直连 http://localhost:8080。
- 4 张业务表均无 user_id；文件表 uk_file_md5 全局唯一；t_vector_store 元数据无 userId 维度（多用户必须改造，否则越权/互斥）。

**AI 后端接口清单（原路径 → 融合后路径，均加 /ai 前缀走网关）**

| 原路径（8080） | 融合后路径 | 说明 |
| --- | --- | --- |
| POST /chat/new | /ai/chat/new | 新建会话（入参 message，返回 uuid+summary） |
| POST /chat/list | /ai/chat/list | 历史会话分页（当前无条件全表查，需按用户过滤） |
| POST /chat/message/list | /ai/chat/message/list | 历史消息分页（按 chatUuid 查，无归属校验，需补） |
| POST /chat/summary/rename | /ai/chat/summary/rename | 重命名（按 t_chat.id） |
| POST /chat/delete | /ai/chat/delete | 删除会话（按 uuid） |
| POST /chat/completion（SSE） | /ai/chat/completion | 流式对话（message/modelName/chatId/networkSearch/think/temperature） |
| POST /customer-service/file/check | /ai/customer-service/file/check | 秒传校验（fileMd5） |
| POST /customer-service/file/upload-chunk | /ai/customer-service/file/upload-chunk | 分片上传 |
| POST /customer-service/file/merge-chunk | /ai/customer-service/file/merge-chunk | 分片合并 |
| POST /customer-service/md/delete | /ai/customer-service/md/delete | 删除知识文件 |
| POST /customer-service/md/list（别名 /file/list） | /ai/customer-service/md/list | 知识文件分页 |
| POST /customer-service/md/update | /ai/customer-service/md/update | 改名/备注 |
| POST /customer-service/chat/completion（SSE，别名 /completion） | /ai/customer-service/chat/completion | 客服流式问答（RAG） |

### 1.4 AI 前端 xiaoha-ai-robot-vue3

- Vue 3.5 + Vite 6 + Pinia（含持久化）+ Tailwind v4 + Ant Design Vue 4.2.6（unplugin-vue-components + AntDesignVueResolver({ importStyle:false }) 按需）+ vite-plugin-svg-icons。
- 专用依赖：@microsoft/fetch-event-source、markdown-it、markdown-it-highlightjs、highlight.js、spark-md5、filesize。
- 路由：hash 模式；/（Index 欢迎页）、/chat/:chatId（chatId=会话 uuid）、/customer-service/chat。
- 结构：layouts/Layout.vue（全屏：自身 Sidebar+内容插槽）、components/{Sidebar,ChatInputBox,StreamMarkdownRender,SvgIcon,LoadingDots}.vue、views/{Index,ChatPage,CustomerServiceChatPage}.vue、stores/chatStore.js（模型/联网/思考开关持久化）、api/{chat,customerService}.js、assets/icons/*.svg（15 个）、assets/{base,main}.css。
- 自身 axios.js：baseURL /api、timeout 7s，不带 token；ChatPage 的 SSE 用 fetchEventSource 直连 http://localhost:8080/chat/completion，无代理无鉴权——融合后必须走网关并带 token。
- 交互事实：新建会话先调 /chat/new 拿 uuid 再跳 /chat/:uuid；删除用 uuid、重命名用数字 id；客服页有文件分片上传（spark-md5 秒传）。

### 1.5 环境事实（本机）

- 已安装 JDK 17（C:\Program Files\Java\jdk-17）与 JDK 21；AI 项目当前按 JDK 21 编译运行。
- MySQL 3307、PostgreSQL 5432、Redis 6379、Nacos 8848、SearXNG 8888 均为本机 dev 服务。
- workspace 内 nginx-1.18.0 现为遗留演示配置（8080/hmdp），与当前 dev 运行方式（vite proxy → 网关 8000）无关；生产若用 nginx 反代需按 R25 补 SSE 配置。
- 三个项目均可独立运行，runtime-logs 目录记录了小红书各服务启动方式（auth/comment/gateway/note/relation/search/user 的 out/err 日志），可作为融合后统一启动参考。

---
## 二、融合前检查清单（含验证命令与通过标准）

> 执行人：后端工程师；约 0.5 人日。全部通过后方可进入第四章。

| # | 检查项 | 验证方法 | 通过标准 |
| --- | --- | --- | --- |
| C1 | JDK17 工具链 | "C:\Program Files\Java\jdk-17\bin\java" -version | 输出 17.x |
| C2 | AI 后端 JDK17 冒烟（M0 门禁） | 以 JDK17 编译并启动 AI 项目（或新建模块后验证，见 4.0），跑一次流式对话 | 编译/启动/对话均成功，结果写入融合执行日志 |
| C3 | AI 原服务基线可用 | 启动 8080，前端完整走对话+客服流程 | 全部正常（作为融合后对比基线） |
| C4 | MySQL 3307 xiaohashu 可写 | 用 note 模块同款加密配置执行 SELECT 1 | 可连接、有建表权限 |
| C5 | PG robot + pgvector | psql -U postgres -d robot -c "SELECT * FROM pg_extension WHERE extname='vector';" | pgvector 已安装，t_vector_store 可访问 |
| C6 | SearXNG 可用 | 访问 http://localhost:8888/search?q=test&format=json | 返回 JSON 结果 |
| C7 | 百炼 Key 有效 | 分别调一次 chat 与 embedding | 成功，记录可用模型清单 |
| C8 | Nacos xiaohashu 正常 | 现有服务注册发现正常 | 小红书服务全部健康 |
| C9 | 端口 8090 空闲 | netstat -ano | findstr :8090 | 无监听 |
| C10 | xiaoha-framework 制品可解析 | mvn -pl xiaoha-framework install（或本地仓库已有） | com.quanxiaoha:* starter 可被新模块引用 |
| C11 | 密钥轮换 | 见 4.7 | 百炼/高德 key 已轮换并改环境变量 |
| C12 | D1/D2/D3 决策签字 | 评审会 | 后端/前端/产品确认 |
| C13 | 历史数据处置决策 | 见 6.3 | 明确“丢弃”或“挂测试账号导入” |
| C14 | 备份就绪 | mysqldump xiaohashu 全库 + pg_dump robot + 三项目打 git tag（pre-ai-fusion） | 备份文件与 tag 均存在 |

---

## 三、风险点与深度考量

### 3.1 后端

| 编号 | 风险 | 应对 |
| --- | --- | --- |
| R1 | Spring Boot 版本冲突 | AI 需 Boot 3.3+（Spring AI 1.1.1），父 POM 为 3.0.2。xiaohashu-ai-biz 独立引入 Boot 3.4.5 依赖并显式版本，不继承父 POM 的 boot 依赖管理；不引入 spring-cloud-alibaba/nacos/openfeign。模块间 classpath 隔离，互不干扰 |
| R2 | JDK 版本（现为 21） | M0 门禁强制 JDK17 编译+启动冒烟；若有 JDK21 专属 API 编译错误逐点替换（预期无，Spring AI 1.x 官方支持 Java 17） |
| R3 | 双数据源（MySQL 主+PG 向量） | pgvector 自动装配会绑定唯一 DataSource。MySQL 设 @Primary 供 MyBatis-Plus；PG 手动声明 DataSource/JdbcTemplate 并手工构造 PgVectorStore（或排除 PgVectorStoreAutoConfiguration 后自定义），见 4.2.3 |
| R4 | MyBatis 体系混用 | 小红书用 mybatis-spring-boot-starter+XML，AI 用 MP 注解式。模块隔离不冲突；AI 模块统一 MP 3.5.12（starter + mybatis-plus-jsqlparser 分页插件） |
| R5 | Druid 加密密码 | xiaohashu 密码为加密串。AI 模块复制 note 模块 druid starter 依赖与 config.decrypt 配置，或向运维要明文改用环境变量注入（推荐后者） |
| R6 | 响应体/异常体系不一致 | AI 自带 Response/PageResponse/异常/AOP 全部替换为 xiaoha-common + biz-operationlog（见 4.5）；AI 的 ResponseCodeEnum 改为实现 BaseExceptionInterface |
| R7 | 异步线程丢 userId | 文件合并后解析为 @Async 事件；SSE 为 reactor 线程。一律入口取 userId 显式传参，禁止跨线程依赖 ThreadLocal（见 7.3） |
| R8 | 日志/监控 | AI log4j2 模板保留，日志目录统一到 xiaohashu2/logs；接入操作日志 AOP；dev 保留 p6spy 慢 SQL 观察 |

### 3.2 前端

| 编号 | 风险 | 应对 |
| --- | --- | --- |
| R9 | 路由/布局冲突 | AI 原 hash+自绘 Layout。按 D3 用独立全屏路由（改动小、保留体验），入口放 AppSidebar；若以后嵌入 BasicLayout 需重构 AI 三段式布局，避免三层侧栏 |
| R10 | SSE 直连 8080 无 token | ChatPage 硬编码 http://localhost:8080/chat/completion。改为 /api/ai/chat/completion 并注入 Authorization（5.4） |
| R11 | 组件库/样式冲突 | AntD 按需加载（复制 AI 现成 unplugin 配置）；AI 的 base.css/main.css 只迁移 AI 页面所需部分，检查无同名全局类覆盖；图标目录合并避免同名 svg |
| R12 | 依赖版本分歧 | 两项目 Vue3.5/pinia3/tailwind4 一致，axios 1.7.9 vs 1.12.2。以 xiaohashu-vue3 现有依赖为准，只新增 AI 独有依赖；升级 axios 前先回归小红书接口 |
| R13 | 401 处理 | AI 原代码无 401 处理。全部请求走小红书 axios（自动 logout）；SSE onerror 识别 401 触发登录弹窗 |
| R14 | 登录形态差异 | 小红书无登录路由页。AI 菜单与页面守卫在未登录时弹 LoginModal（v-model:visible），不跳页面（5.5） |

### 3.3 数据库

| 编号 | 风险 | 应对 |
| --- | --- | --- |
| R15 | 用户数据隔离 | 4 张业务表全部加 user_id；所有查询/删除/重命名强制带 user_id；消息查询先校验会话归属（6.1） |
| R16 | 客服知识库越权 | 原 uk_file_md5 全局唯一、列表全局可见。按 D2 改 (user_id,file_md5) 唯一，文件接口按用户过滤；RAG 检索限定本人向量（6.2） |
| R17 | 双库事务一致性 | 业务表(MySQL)与向量(PG)跨库。合并流程先写 MySQL 业务态再写向量，失败补偿删除/状态回退；不追求跨库强事务 |
| R18 | 历史数据无主 | 存量数据无 user_id。默认丢弃（原型数据），或整体挂测试账号后导入（6.3） |
| R19 | pgvector 性能 | topK 限 3；限制单知识库规模；元数据 userId 上建索引辅助过滤 |

### 3.4 鉴权/接口/安全

| 编号 | 风险 | 应对 |
| --- | --- | --- |
| R20 | 未登录可访问 | /ai/** 不加白名单，网关统一 401；测试必须覆盖 |
| R21 | 越权操作 | delete/rename/message-list/文件接口全部先验归属再操作（7.4 矩阵逐接口落实） |
| R22 | 密钥入库泄露（核对发现） | application-dev.yml 含百炼 sk-ws-... key；mcp-servers-config.json 含高德 key。立即轮换；新配置只允许 ${ENV} 引用 |
| R23 | Prompt 注入/滥用 | 消息长度上限 4000 字符校验；SSE 接口按用户限流（v1 至少长度校验+频率日志）；客服文档仅解析 md 文本 |
| R24 | MCP/npx 副作用 | stdio 每次拉起 node 进程，失败可能影响 ChatClient 装配。加 ai.mcp.enabled 开关，默认关闭或仅 dev 开启，生产不启用高德工具 |
| R25 | SSE 被缓冲/超时截断 | 网关 httpclient 响应超时默认较短可能掐流；nginx 默认 buffering 破坏流式。网关放宽 response-timeout（4.6.3）；nginx 配 proxy_buffering off + proxy_read_timeout 600s；前端做断线提示 |
| R26 | 大模型依赖可用性 | 百炼/SearXNG 故障只影响 AI。SearXNG 失败时联网搜索降级为普通问答；网关 /ai/** 错误隔离于小红书业务 |
| R27 | 大文件上传 | AI 模块 multipart 限制单分片 5MB、单文件 20MB、请求 50MB；分片断点续传已具备 |
| R28 | Long 精度 | 复用小红书 jackson starter 的 Long 序列化规则，删除 AI 自带 JacksonConfig |

### 3.5 部署

| 编号 | 风险 | 应对 |
| --- | --- | --- |
| R29 | 服务发现缺失 | AI 不入 Nacos，网关固定 IP。dev 可用；集群化/换 IP 属后续 SCA 升级范围，不在本期 |
| R30 | 进程治理 | 8090 常驻进程按 runtime-logs 同款方式托管（out/err 日志、启动脚本） |
| R31 | project 残留 | 验收后停用 AI 8080 与 AI 前端 dev server；源码先归档再移除（第十章） |
| R32 | 范围失控 | 双数据源+隔离改造最复杂。M0 门禁先行，按第十一章里程碑逐项验收，任一步失败按第九章回滚 |

---
## 四、后端 xiaohashu2 分步融合计划

### 4.0 步骤 0：基线冒烟（M0 门禁，约 0.5 人日）

1. 拉基线：git 打 tag `pre-ai-fusion`（三个项目）。
2. 确认 AI 项目原功能全绿（对照 C3 清单走一遍）。
3. JDK17 冒烟：临时将 AI 项目 pom 的 `java.version` 改为 17，用 JDK17 执行 `mvn -DskipTests package` 并启动、跑一次对话；通过后把验证结论（截图/日志路径）写入执行记录。**此结果决定 R2 是否成立**，通过后才继续。
4. 冒烟后把 AI 项目代码恢复到原状（本步骤只验证，不改业务代码）。

### 4.1 步骤 1：新建模块骨架（约 0.5 人日）

目标结构（仿 xiaohashu-note 两级）：

```
xiaohashu2/
├── pom.xml
└── xiaohashu-ai/
    ├── pom.xml                 # packaging=pom
    └── xiaohashu-ai-biz/
        ├── pom.xml
        └── src/main/java/com/quanxiaoha/xiaohashu/ai/biz/...
```

执行动作：

1. 根 pom.xml `<modules>` 追加 `<module>xiaohashu-ai</module>`。
2. 新建 `xiaohashu-ai/pom.xml`：parent 指向根 pom，packaging=pom，modules 含 xiaohashu-ai-biz。
3. 新建 `xiaohashu-ai-biz/pom.xml`：parent 指向 xiaohashu-ai。关键依赖如下（版本在自身 properties 声明，不依赖父 POM 的 boot 管理）：
   - `spring-boot-starter-web`（3.4.5，排除默认 logging）+ `spring-boot-starter-log4j2`（3.4.5）
   - `spring-ai-bom`（1.1.1, import）+ `spring-ai-starter-model-openai` + `spring-ai-starter-vector-store-pgvector` + `spring-ai-markdown-document-reader` + `spring-ai-starter-mcp-client-webflux`
   - `mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser`（3.5.12）
   - mysql 驱动（版本复用根 POM 的 8.0.29 或显式声明）；postgresql 驱动（随 Boot 3.4.5 管理版本）
   - Druid：与 note 模块一致（复制其 druid starter 依赖与版本 1.2.23；或改用 Hikari + 环境变量明文密码）
   - 小红书通用组件：xiaoha-common、xiaoha-spring-boot-starter-biz-context、xiaoha-spring-boot-starter-jackson、xiaoha-spring-boot-starter-biz-operationlog（版本随根 POM revision）
   - AI 原有工具：okhttp 4.12.0、jsoup 1.17.2、hutool、commons-lang3、commons-io、guava
   - **不要引入**：spring-cloud-alibaba / nacos-discovery / openfeign / spring-cloud-starter-gateway
4. 新建启动类 `XiaohashuAiBizApplication`（@SpringBootApplication + @MapperScan("...ai.biz.domain.mapper")），包名 `com.quanxiaoha.xiaohashu.ai.biz`。
5. 先以空模块跑通 `mvn -pl xiaohashu-ai/xiaohashu-ai-biz -am install` 与启动，确认与既有模块共存无版本冲突。

### 4.2 步骤 2：配置文件（约 0.5 人日）

资源目录沿用小红书惯例：`src/main/resources/config/{application.yml, application-dev.yml}`。

**application.yml（要点）**

```yaml
server:
  port: 8090
spring:
  application:
    name: xiaohashu-ai
  profiles:
    active: dev
logging:
  config: classpath:log4j2.xml   # 从 AI 项目复制模板，日志目录改到 xiaohashu2/logs
```

**application-dev.yml（要点）**

```yaml
spring:
  # ===== MySQL 主数据源：对话/文件业务表（密码推荐环境变量，或复制 note 模块 Druid 解密配置）=====
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3307/xiaohashu?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: ${MYSQL_PWD:}
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 50MB

  ai:
    openai:
      base-url: ${AI_BASE_URL:https://ws-ms4u3sopffjgsuxk.cn-beijing.maas.aliyuncs.com/compatible-mode}
      api-key: ${AI_API_KEY:}
      embedding:
        options:
          model: qwen3.7-text-embedding
          dimensions: 1536
    mcp:   # 默认关闭，规避 R24（开关属性名以 Spring AI 1.1.1 实际为准，如 spring.ai.mcp.client.stdio.enabled）
      client:
        stdio:
          servers-configuration: classpath:/mcp-servers-config.json

# ===== PG 向量数据源（独立配置，代码里手工构造 PgVectorStore）=====
pgvector:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://127.0.0.1:5432/robot
    username: postgres
    password: ${PG_PWD:}

okhttp:
  connect-timeout: 50000
  read-timeout: 300000
  write-timeout: 150000

searxng:
  url: http://localhost:8888/search
  count: 10

customer-service:
  file-storage-path: D:\xiaohashu2\data\ai-robot      # 从 D:\critic\xiaoha-ai-robot 迁移过来，统一数据目录
  chunk-path: D:\xiaohashu2\data\ai-robot\chunk
  model: deepseek-v4-flash
  temperature: 0.0
```

**双数据源装配要点（4.2.3）**

- `@Primary` MySQL `DataSource` 供 MyBatis-Plus（SqlSessionFactory、分页插件 MybatisPlusInterceptor 只挂主源）。
- 独立 `@Bean("pgDataSource")` + `JdbcTemplate`；手工 `@Bean PgVectorStore`：`PgVectorStore.builder(pgJdbcTemplate, embeddingModel).dimensions(1536).distanceType(CosineDistance).indexType(Hnsw).schemaValidationEnabled(true)`，并在构造前保证 t_vector_store 存在（builder 会自动建表）。
- 移除 p6spy 包装（原 dev 用 P6SpyDriver），需要 SQL 观察时在 MySQL 侧用 Druid 慢 SQL 配置，PG 侧临时开 PG 日志。
- pgvector 自动装配的排除类名与 MCP 开关属性名以所依赖 starter jar 内实际类名为准（融合时先核对再写死）。
- mcp-servers-config.json 中高德 key 用 `${AMAP_MAPS_API_KEY}` 占位（stdio env 支持环境变量引用需在配置中写死读取位置，若不支持则整体改为 dev-only 文件，生产不加载）。

### 4.3 步骤 3：代码迁移（约 1 人日，纯搬运）

包映射：`com.quanxiaoha.ai.robot.*` → `com.quanxiaoha.xiaohashu.ai.biz.*`，目录结构与原项目一一对应：

```
com.quanxiaoha.xiaohashu.ai.biz/
├── XiaohashuAiBizApplication.java
├── advisor/       4 个 Advisor 原样迁移（CustomChatMemoryAdvisor、CustomStreamLoggerAndMessage2DBAdvisor、NetworkSearchAdvisor、CustomerServiceAdvisor）
├── aspect/        删除（改用小红书 @ApiOperationLog + xiaoha starter）
├── config/        ChatClientConfig、OkHttpConfig、MybatisPlusConfig、ThreadPoolConfig、AsyncEventConfig、PgVectorConfig(新)
├── controller/    ChatController、AiCustomerServiceController
├── domain/dos/    ChatDO(+userId)、ChatMessageDO、AiCustomerServiceFileStorageDO(+userId)、FileChunkInfoDO(+userId)
├── domain/mapper/ 4 个 Mapper（改默认方法加 user 条件，见 4.4）
├── enums/         ResponseCodeEnum(改为实现 BaseExceptionInterface)、AiCustomerServiceFileStatusEnum
├── event/         AiCustomerServiceMdUploadedEvent(+userId)、listener/AiCustomerServiceMdUploadedListener(+userId 显式传参)
├── model/{dto,vo}/ 原样迁移，AiChatReqVO 不新增 userId 字段（userId 由后端入口取，不信任前端）
├── reader/        MarkdownReader
├── service/       ChatService、CustomerService 及 impl
├── tools/         DateTimeTools
└── utils/         仅保留 AI 独有（JsonUtil/StringUtil 视需要）；Response/PageResponse/BizException/BaseExceptionInterface 全部删除改用 xiaoha-common
```

搬运纪律：

- Mapper XML 不存在（注解式），无 XML 迁移。
- 删除 CorsConfig（同源走网关）、JacksonConfig（统一小红书序列化）、aspect 包、自有 Response/PageResponse/异常工具。
- 所有数据库字段映射核对 6.1 的 MySQL 表结构（含类型差异：PG 无 unsigned，MySQL datetime 等）。
- CustomerServiceImpl 中文件读写路径改为新配置目录，并把存量文件（若需保留）从 D:\critic\xiaoha-ai-robot 拷贝过去。

### 4.4 步骤 4：用户隔离改造（核心，约 1.5 人日）

统一规则：**userId 一律来自服务端 LoginUserContextHolder（网关透传），任何前端入参都不作为身份依据**。

| 接口/代码 | 改造点 |
| --- | --- |
| ChatDO | 新增 `userId`（Long）；建表唯一键/索引见 6.1 |
| ChatMessageDO | 新增冗余 userId（便于审计/治理）；消息落库点（CustomStreamLoggerAndMessage2DBAdvisor）写入 userId |
| ChatServiceImpl.newChat | `Long userId = LoginUserContextHolder.getUserId()` 写入 ChatDO |
| ChatServiceImpl.findChatHistoryPageList | Mapper 条件 `.eq(ChatDO::getUserId, userId)` |
| ChatServiceImpl.findChatHistoryMessagePageList | 先按 chatUuid 查 t_chat 并校验 `chat.userId == 当前 userId`，不通过抛 BizException；再查消息 |
| ChatServiceImpl.renameChatSummary / deleteChat | 更新/删除条件同时带 id/uuid + userId；影响行数为 0 视为越权或不存在，返回统一错误 |
| ChatController.completion（SSE） | 入口校验会话归属（chatId 对应会话属于当前用户）后才开始流式；进入方法即取 userId，放入 Advisor 参数链（见 7.3） |
| ChatMessageMapper / ChatMapper | 默认方法签名加 userId 参数或调用前由 Service 层先验归属 |
| AiCustomerServiceFileStorageDO / FileChunkInfoDO | 新增 userId；唯一键 (user_id, file_md5) |
| CustomerService.checkFile / uploadChunk / mergeChunk / md/list / md/update / md/delete | 全部带 userId 条件；md/list 只返回本人文件；删除/更新先验归属；merge 后异步解析事件携带 userId |
| AiCustomerServiceMdUploadedListener | 事件对象加 userId 字段，异步线程不读 ThreadLocal |
| CustomerServiceAdvisor | similaritySearch 增加 `userId` 元数据过滤（Filter 表达式，见 6.2），只检索当前用户知识库 |
| CustomChatMemoryAdvisor / CustomStreamLoggerAndMessage2DBAdvisor | 消息读写均基于已校验归属的 chatUuid，无需再按用户过滤；若实现中直接查 ChatMessageMapper，保持 chatUuid 维度即可 |

### 4.5 步骤 5：响应体与异常统一（并入 4.3/4.4 执行）

- Controller/Service 返回值类型改为 `com.quanxiaoha.framework.common.response.Response` / `PageResponse`。
- AI 原 `ResponseCodeEnum` 迁移为枚举并实现 `com.quanxiaoha.framework.common.exception.BaseExceptionInterface`；错误码避免与小红书已有 code 冲突（统一新增 AI 段位，如 30xxx）。
- 删除 AI 自带 GlobalExceptionHandler，改用 xiaoha-common 的全局异常处理或按小红书其他业务模块同款方式注册。
- `@ApiOperationLog` 改用 xiaoha-spring-boot-starter-biz-operationlog 的注解（同名，替换 import 即可）。
- 校验注解继续使用 jakarta.validation（AI 模块已依赖 starter-validation）。

### 4.6 步骤 6：网关接入（约 0.5 人日）

1. `xiaohashu-gateway/src/main/resources/application.yml` routes 追加（**小红书既有路由不动**）：

```yaml
- id: ai
  uri: http://127.0.0.1:8090
  predicates:
    - Path=/ai/**
  filters:
    - StripPrefix=1
    - PreserveHostHeader
```

2. SaTokenConfigure：**不改白名单**，/ai/** 默认强制登录。
3. AddUserId2HeaderFilter 零改动，自动为 /ai/** 透传 userId 头。
4. SSE 超时放宽（application.yml 全局 httpclient 配置）：

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        response-timeout: 300s   # 放宽默认 30s，避免长流被掐
```

5. 重启网关后验证：未登录访问 /ai/chat/list 返回 401；登录后正常。

### 4.7 步骤 7：密钥安全化与配置治理（约 0.5 人日）

- 轮换百炼 API Key、高德 MCP Key（两把 key 均已出现在源码，按已泄露处理）。
- 新模块 yml 全部 `${ENV}` 占位：AI_API_KEY / AI_BASE_URL / MYSQL_PWD / PG_PWD / AI_MCP_ENABLED。
- mcp-servers-config.json 不再携带明文 key；生产环境不加载 MCP 配置（R24）。
- 启动脚本（dev/生产）统一维护一份环境变量清单，与 runtime-logs 启动方式放一起。

### 4.8 步骤 8：后端联调与自测

- 按第八章 8.1 P0 清单逐项自测；通过后交前端联调。
- 补充单测（可选但建议）：会话归属校验（他人 chatId 访问返回失败）、按用户列表隔离、文件越权删除。AI 项目原无测试基建，至少为 Service 层越权用例补 3~5 个。

---
## 五、前端 xiaohashu-vue3 分步融合计划

### 5.0 布局决策落地（D3）

- v1：/ai/chat 与 /ai/customer-service 作为**独立全屏路由**（不进 BasicLayout），整体复用 AI 的 Layout.vue（自身 Sidebar 含：新建对话、历史会话、AI 客服切换）。小红书 AppSidebar 增加“AI 助手”菜单跳转 /ai/chat；AI Sidebar 增加“返回发现页”（/discover）入口，实现两套界面互跳。
- 理由：保留 AI 成熟交互（三段式工作台），改动面最小；避免三层侧栏与双重滚动条。
- 演进（v2，不在本期）：将 AI 会话列表压缩为页面内二级栏并嵌入 BasicLayout，实现完全同一外壳。

### 5.1 步骤 1：依赖与构建配置（约 0.5 人日）

1. package.json dependencies 新增（版本对齐 AI 项目当前 lock）：
   - ant-design-vue ^4.2.6、@microsoft/fetch-event-source ^2.0.1、markdown-it ^14.1.0、markdown-it-highlightjs ^4.2.0、highlight.js ^11.11.1、spark-md5 ^3.0.2、filesize ^11.0.13
   - devDependencies 新增：unplugin-vue-components ^28.7.0、vite-plugin-svg-icons ^2.0.1（AI 项目现有版本可直接复制）
2. vite.config.js 合并 AI 项目的 plugins 段：
   - `Components({ resolvers: [AntDesignVueResolver({ importStyle: false })] })`
   - `createSvgIconsPlugin({ iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')], symbolId: 'icon-[dir]-[name]', inject: 'body-last', customDomId: '__svg__icons__dom__' })`
   - 保留小红书原有 /api proxy（目标 8000 不变）。
3. main.js 追加 `import 'virtual:svg-icons-register'`。
4. `npm install` 后先跑一次小红书页面冒烟（确认 Tailwind/AntD 不冲突）。

### 5.2 步骤 2：文件迁移映射（约 0.5 人日）

| AI 项目（源） | xiaohashu-vue3（目标） | 处理 |
| --- | --- | --- |
| src/assets/icons/*.svg（15 个） | src/assets/icons/ | 复制；检查与小红书图标无同名冲突 |
| src/components/Sidebar.vue | src/components/ai/Sidebar.vue | 复制+改造：会话区用户化（已由后端隔离）、加“返回发现页/退出登录” |
| src/components/ChatInputBox.vue | src/components/ai/ChatInputBox.vue | 复制 |
| src/components/StreamMarkdownRender.vue | src/components/ai/StreamMarkdownRender.vue | 复制 |
| src/components/SvgIcon.vue | src/components/ai/SvgIcon.vue | 复制（小红书当前无同名组件；如与后续图标命名冲突再改名） |
| src/components/LoadingDots.vue | src/components/ai/LoadingDots.vue | 复制 |
| src/layouts/Layout.vue | src/layouts/AiLayout.vue | 复制改名（与 BasicLayout 区分）；原 slot 插槽改为 `<router-view/>` 承载子页，Sidebar 引用改 ai 目录 |
| src/views/ChatPage.vue | src/views/ai/ChatPage.vue | 复制+改造（见 5.3） |
| src/views/CustomerServiceChatPage.vue | src/views/ai/CustomerServiceChatPage.vue | 复制+改造（见 5.3） |
| src/views/Index.vue | 不迁移 | AI 欢迎首页入口废弃，由小红书导航直达 ChatPage |
| src/stores/chatStore.js | src/stores/chat.js | 复制改名（persist 键不变或改 chat），注册进 stores/index.js |
| src/api/chat.js | src/api/ai/chat.js | 路径加 /ai 前缀，复用小红书 axios |
| src/api/customerService.js | src/api/ai/customerService.js | 同上 |
| src/axios.js / src/main.js / src/router/index.js | 不迁移 | 统一用小红书版本 |
| src/assets/{base.css,main.css} | 按需并入 | 只抽取 AI 页面依赖的样式片段，包一层 .ai-root 作用域类，禁止全局覆盖 |

### 5.3 步骤 3：页面与 API 改造（约 1 人日）

**API 层（src/api/ai/chat.js 示例形态）**

```js
import axios from '@/axios'   // 小红书 axios：自动带 Bearer token、401 自动登出

export function newChat(message) {
  return axios.post('/ai/chat/new', { message })
}
export function findChatMessagePageList(current, size, chatId) {
  return axios.post('/ai/chat/message/list', { current, size, chatId })
}
export function findHistoryChatPageList(current, size) {
  return axios.post('/ai/chat/list', { current, size })
}
export function deleteChat(uuid) {
  return axios.post('/ai/chat/delete', { uuid })
}
export function renameChat(id, summary) {
  return axios.post('/ai/chat/summary/rename', { id, summary })
}
```

customerService.js 同规则：/ai/customer-service/file/check、/file/upload-chunk、/file/merge-chunk、/md/list、/md/update、/md/delete、/chat/completion。

**SSE 统一封装（新增 src/utils/aiStream.js，核心形态）**

```js
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useUserStore } from '@/stores/user'

export function aiStream(url, data, handlers) {
  const userStore = useUserStore()
  return fetchEventSource(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userStore.token}`,
    },
    body: JSON.stringify(data),
    openWhenHidden: true,
    onmessage: (ev) => handlers.onMessage?.(JSON.parse(ev.data)),
    onerror: (err) => {
      if (err?.status === 401) { handlers.onUnauthorized?.(); return }
      handlers.onError?.(err)
    },
    signal: handlers.signal,
  })
}
```

调用点（ChatPage / CustomerServiceChatPage 各一处）：原 `http://localhost:8080/chat/completion` 与 `/customer-service/...` 全部替换为 `/api/ai/chat/completion`、`/api/ai/customer-service/chat/completion`。

**页面改造点**

- ChatPage：路由由 /chat/:chatId 改 /ai/chat/:chatId；新建会话后跳 /ai/chat/:uuid；未登录态处理见 5.4；401 中断时提示并打开登录框。
- CustomerServiceChatPage：同样走新路径与 token；模型/温度选择保留（若后端仍支持）。
- AiLayout/Sidebar：新增“返回发现页”与“退出登录”（调 userStore.logout 后跳 /discover），登录信息用 userStore.profile。
- chatStore：保持模型/联网/思考开关持久化逻辑；注意原 models 列表仅作展示映射，融合后按实际可调用模型校对一遍。

### 5.4 步骤 4：登录门禁（约 0.5 人日）

小红书登录是弹窗形态（无 /login 页），按 R14 落地：

1. AppSidebar.vue 在导航区新增菜单项（复刻“发现”的 router-link 样式）：图标用迁移的 ai-robot-logo.svg，文案“AI 助手”，to=/ai/chat；未登录（!isLoggedIn）点击时执行现有 handleShowLogin 流程（inject showLoginModal 置 true），不跳转。
2. AI 页面路由守卫（router/index.js 增加 beforeEach）：访问 /ai/* 且 userStore.token 为空时：置一个全局标志（如 localStorage/URL query 或简单地在目标组件内处理）——**推荐简单可靠做法**：在 AiLayout 挂载时检查 token，为空则在本页面渲染 `<LoginModal v-model:visible="loginVisible" />`（直接 import components/auth/LoginModal.vue）；登录成功（watch userStore.token）后自动进入。
3. SSE 收到 401 时同样弹出登录框并停止流。

### 5.5 步骤 5：路由注册与回归（约 0.5 人日）

router/index.js 追加 /ai 顶级路由（与 BasicLayout 平级，子页挂在 AiLayout 下）：

```js
{
  path: '/ai',
  component: () => import('@/layouts/AiLayout.vue'),   // 全屏工作台外壳（Sidebar + router-view）
  children: [
    { path: 'chat', name: 'AIChatIndex', component: () => import('@/views/ai/ChatPage.vue') },
    { path: 'chat/:chatId', name: 'AIChatDetail', component: () => import('@/views/ai/ChatPage.vue') },
    { path: 'customer-service', name: 'AICustomerService', component: () => import('@/views/ai/CustomerServiceChatPage.vue') },
  ]
}
```

（路由结构以 ChatPage 对 :chatId 的依赖为准做最终微调；页面标题 meta.title=AI 助手。）

回归：npm run dev 起小红书，逐项过第八章 8.2/8.3 前端清单。

---
## 六、数据库迁移融合计划

### 6.1 MySQL（xiaohashu 库）新增 4 张表（全部带 user_id，不动任何原表）

```sql
-- 1) AI 会话表
CREATE TABLE `t_chat` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `uuid`        varchar(64)  NOT NULL COMMENT '会话UUID',
  `user_id`     bigint       NOT NULL COMMENT '小红书用户ID（网关透传）',
  `summary`     varchar(100) DEFAULT NULL COMMENT '会话摘要',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uuid` (`uuid`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_update` (`user_id`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- 2) AI 会话消息表（归属由 t_chat 间接保证）
CREATE TABLE `t_chat_message` (
  `id`               bigint      NOT NULL AUTO_INCREMENT,
  `chat_uuid`        varchar(64) NOT NULL COMMENT '所属会话UUID',
  `user_id`          bigint      NOT NULL COMMENT '冗余用户ID，便于数据治理与审计',
  `content`          text        COMMENT '消息内容',
  `role`             varchar(20) NOT NULL COMMENT 'user/assistant',
  `reasoning_content` text       COMMENT '深度思考内容',
  `create_time`      datetime    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_uuid` (`chat_uuid`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_chat_create` (`chat_uuid`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- 3) AI 客服知识文件表（按用户隔离：唯一键从 file_md5 改为 user_id+file_md5）
CREATE TABLE `t_ai_customer_service_file_storage` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `user_id`         bigint       NOT NULL COMMENT '文件归属用户',
  `file_md5`        varchar(64)  NOT NULL,
  `file_name`       varchar(255) DEFAULT NULL,
  `file_path`       varchar(500) DEFAULT NULL,
  `file_size`       bigint       DEFAULT NULL,
  `total_chunks`    int          DEFAULT NULL,
  `uploaded_chunks` int          DEFAULT NULL,
  `status`          tinyint      DEFAULT NULL COMMENT '0上传中 1解析中 2可用 3失败',
  `remark`          varchar(500) DEFAULT NULL,
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file_md5` (`user_id`,`file_md5`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服知识文件表';

-- 4) 分片信息表（归属随文件）
CREATE TABLE `t_file_chunk_info` (
  `id`           bigint      NOT NULL AUTO_INCREMENT,
  `user_id`      bigint      NOT NULL COMMENT '上传用户',
  `file_md5`     varchar(64) NOT NULL,
  `chunk_number` int         NOT NULL,
  `chunk_path`   varchar(500) DEFAULT NULL,
  `chunk_size`   bigint      DEFAULT NULL,
  `create_time`  datetime    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_md5_chunk` (`user_id`,`file_md5`,`chunk_number`),
  KEY `idx_file_md5` (`file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分片信息表';
```

执行方式：数据库变更脚本入库管理（如 xiaohashu2 项目 docs/sql/ai_fusion_v1.sql），与 4.1 模块同 PR 评审；执行前先跑 C14 备份。

### 6.2 PostgreSQL 向量库改造（robot 库）

- t_vector_store 保留原结构，**新增 userId 维度通过 metadata(json) 实现**，不改 starter 默认表结构：
  - 文件解析入库时，每条 Document metadata 写入 `userId = <Long>`。
  - CustomerServiceAdvisor 检索时用元数据过滤：`filter = "userId == '<当前userId>'"`（Spring AI pgvector 支持 metadata filter 表达式），只召回本人知识片段。
- 若实现上发现 filter 性能不达预期，再对 metadata 中 userId 建 GIN 索引（pgvector 文档化做法），或 v2 演进为独立 user 维度列。
- 向量写入失败补偿：合并流程“MySQL 标记解析中 → 向量写入成功 → 标记可用；失败 → 状态置失败并可重试”，与 R17 一致。

### 6.3 存量数据迁移（可选，默认丢弃）

- 决策：原型库数据默认不导入（无用户归属、无业务价值）；若产品要求保留，仅迁移 t_chat/t_chat_message：
  1. pg_dump 导出 → 转换：t_chat 每行指定测试账号 user_id（UPDATE 补列）→ 导入 MySQL；
  2. t_chat_message 按 chat_uuid 关联补 user_id；
  3. 文件与向量数据不迁移（涉及磁盘路径与用户隔离，迁移成本高、收益低）。
- 无论是否迁移，robot 库先整体 pg_dump 备份再操作。

### 6.4 数据访问规范（写进编码约定）

- 所有 AI 业务查询必须带 user_id 或经归属校验；Mapper 默认方法禁止出现无 user 条件的“全表”变体暴露到 Service。
- 删除=物理删除（沿用 AI 现状）但必须带 user_id 条件；后续如需软删再按小红书 DeletedEnum 规范演进。

---

## 七、鉴权体系统一改造计划

### 7.1 目标链路（改造后唯一形态）

```
浏览器/AI 页面
  → axios/fetch-event-source 带 Authorization: Bearer <小红书token>
  → Vite/nginx /api 反代
  → 网关 8000
      SaReactorFilter：/ai/** 强制登录校验（不在白名单）
      AddUserId2HeaderFilter：Redis 查 token → 写请求头 userId
  → xiaohashu-ai 8090
      HeaderUserId2ContextFilter（biz-context 自动装配）：userId → LoginUserContextHolder
  → Controller/Service：LoginUserContextHolder.getUserId() → 数据隔离
```

### 7.2 网关层（改动仅 2 处）

1. application.yml 加 ai 路由（4.6.1）。
2. 不做白名单放行、不改 SaTokenConfigure、不改 AddUserId2HeaderFilter。

### 7.3 AI 微服务层（关键规则）

- 同步接口：Controller 内 `Long userId = LoginUserContextHolder.getUserId()`，Service 方法签名传 userId（不依赖 ThreadLocal 传递到 Service 深处亦可，但推荐入口即取）。
- SSE（reactor 线程）：进入 Controller 时同步线程取 userId 并完成会话归属校验，再通过 request 参数/Advisor 构造参数显式传递给流式链路与 CustomStreamLoggerAndMessage2DBAdvisor/CustomChatMemoryAdvisor；**禁止在流式回调里读 ThreadLocal**。
- @Async 事件：AiCustomerServiceMdUploadedEvent 增加 userId 字段，Listener 显式使用；发送事件前在同步入口已取好 userId。
- 兜底防护：AI 模块再挂一道 Servlet Filter 校验（可选）：凡 /chat/**、/customer-service/** 请求无有效 userId 头直接 401（防御网关误配/漏配），与业务代码无关、可随时下线。

### 7.4 越权防护矩阵（测试与代码评审共同依据）

| 接口 | 防护规则 |
| --- | --- |
| /ai/chat/new | 新建：写 user_id=当前用户 |
| /ai/chat/list | 查询：user_id=当前用户 |
| /ai/chat/message/list | 先验 chatUuid 归属=当前用户，再查消息 |
| /ai/chat/completion | 流式前验 chatUuid 归属；他人会话直接拒绝 |
| /ai/chat/summary/rename、/ai/chat/delete | 按 id/uuid+user_id 条件更新/删除，影响行数 0 → 越权/不存在统一报错 |
| /ai/customer-service/file/check、upload-chunk、merge-chunk | 全程 user_id=当前用户；(user_id,file_md5) 维度 |
| /ai/customer-service/md/list、md/update、md/delete | 只操作本人文件；删除/更新前验归属 |
| /ai/customer-service/chat/completion | 向量检索按 userId 过滤 |

### 7.5 前端侧规则

- 普通请求：复用小红书 axios（自动带 token、401 自动登出）。
- SSE：aiStream 封装手动注入 Authorization（5.3）。
- 文件分片上传：走小红书 axios 的 FormData（自动带 token）。
- 登出：AI 页面“退出登录”调 userStore.logout() 并跳 /discover；chatStore 的持久化数据（模型/开关）可保留，会话列表下次登录按新用户加载（后端已隔离）。

---
## 八、联调、功能测试校验清单

> 执行顺序：8.1 后端 → 8.2 前后端联调 → 8.3 数据隔离专项 → 8.4 小红书回归。P0 不通过不得进入下一阶段。

### 8.1 后端 P0

- [ ] 8090 启动成功（JDK17 编译运行，日志无 ERROR）；xiaohashu-ai 与既有模块共存无依赖冲突
- [ ] 网关 /ai/** 转发正常；PreserveHostHeader/StripPrefix 生效
- [ ] 未登录访问 /ai/chat/list、/ai/chat/new、/ai/chat/completion 均返回 401
- [ ] 登录后 /ai/chat/new 成功，MySQL t_chat 写入正确的 user_id
- [ ] 双数据源：MyBatis-Plus 全部落在 MySQL；PgVectorStore 落在 PG robot；无串库
- [ ] /ai/chat/completion SSE：事件逐块返回、正常结束、错误事件可识别；网关长连接不被 30s 掐断（挂 2 分钟流验证）
- [ ] 对话记忆（50 条窗口）、联网搜索（SearXNG）、深度思考（reasoning 字段）均正常
- [ ] 客服链路：check 秒传 → upload-chunk（5 分片）→ merge-chunk → 异步解析 → 向量入库 → RAG 问答命中
- [ ] 越权用例：用户 B 用用户 A 的 chatId/uuid/文件 id 调 list/delete/rename/completion 全部失败

### 8.2 前端联调 P0

- [ ] npm run dev 无报错；AntD 按需组件正常渲染；AI 图标 sprite 正常
- [ ] AppSidebar“AI 助手”入口可见；未登录点击弹登录框；登录后进入 /ai/chat
- [ ] 新建对话 → 跳 /ai/chat/:chatId → 流式逐字渲染（Markdown/代码高亮）→ 会话列表出现新会话
- [ ] 历史会话加载/翻页、重命名、删除正常
- [ ] 模型选择、联网搜索、深度思考开关生效（请求体字段正确）
- [ ] 客服页文件分片上传/秒传/删除/问答正常；上传中断可续传
- [ ] SSE 401 场景：token 失效后提问 → 停止流并弹登录框
- [ ] 刷新 /ai/chat/:chatId 深链正常（history 路由无 404）

### 8.3 数据隔离专项（P0）

- [ ] 用户 A/B 各自建会话，互不可见列表与消息
- [ ] 用户 A 上传知识文件，用户 B 的文件列表为空；B 无法 check/合并/删除 A 的 md5
- [ ] RAG 检索：B 提问只命中 B 的知识库（用不同内容文件验证答案不串）
- [ ] 客服问答不返回他人文件内容（向量过滤生效）
- [ ] 数据库抽查：t_chat/t_chat_message/t_ai_customer_service_file_storage/t_file_chunk_info 的 user_id 均与 token 用户一致

### 8.4 小红书回归（P0 全量）

- [ ] 登录/验证码注册流程正常（auth）
- [ ] 发现页频道/笔记列表/搜索正常（note/search）
- [ ] 笔记发布（图片 OSS 上传）、详情、点赞收藏评论回复（note/comment/count）
- [ ] 关注/粉丝/用户主页（relation/user）
- [ ] 未登录浏览公开内容、登录拦截逻辑无变化
- [ ] 网关其余路由（auth/user/note/relation/oss/comment/search）全部无回归
- [ ] 前端构建 npm run build 成功，产物大小可接受（AntD 按需后预估增量 < 1MB gzip）

### 8.5 性能与安全抽测（P1）

- [ ] SSE 并发 5 路对话，网关/8090 无阻塞（对比小红书接口响应无劣化）
- [ ] 单消息超长（>4000 字符）被拦截
- [ ] /ai/** 请求头伪造 userId 直接调 8090 无效（兜底 Filter 生效，如 7.3 已启用）
- [ ] 日志脱敏：不打印 token/API Key；SQL 日志不打印完整消息体（可选）

---

## 九、风险回滚预案

### 9.1 设计前提

融合为“新增模块 + 新增表 + 新增路由/页面”，小红书业务源码零改动（唯一触碰点：根 pom modules、网关 yml 追加路由、前端 package/vite/router/侧边栏/样式），因此每一层均可独立回滚，互不牵连。

### 9.2 回滚触发条件（满足任一即启动）

1. xiaohashu-ai 启动失败且 30 分钟内无法修复（M0 已冒烟，概率低）；
2. SSE 经网关出现系统性截断/阻塞，影响面无法收敛；
3. 出现用户数据隔离失效（可复现越权）；
4. 小红书业务功能出现回归且定位到融合改动；
5. 数据库双写出现不可控不一致（向量库与业务表大面积错位）。

### 9.3 分层回滚操作表

| 层 | 操作 | 影响 | 耗时 |
| --- | --- | --- | --- |
| 后端服务 | 停止 xiaohashu-ai（8090） | AI 不可用，小红书正常 | 1 分钟 |
| 网关 | 注释/删除 ai 路由 → 重启网关 | AI 不可达，小红书正常 | 2 分钟 |
| 前端 | git revert 融合提交（或移除 /ai 路由与侧边栏项、回退 package.json/vite 配置）→ 重新 build | 小红书恢复原界面 | 10 分钟 |
| 数据库 | 新增 4 表：DROP TABLE t_chat,t_chat_message,t_ai_customer_service_file_storage,t_file_chunk_info；PG 侧：删除新增向量元数据或重建库级快照 | 不影响小红书任何原表/原数据 | 5 分钟 |
| 配置 | 环境变量/密钥占位改动回退 | 无 | 5 分钟 |
| 全量 | 恢复 C14 备份（mysqldump/pg_dump）+ git checkout pre-ai-fusion tag | 回到融合前状态 | 30 分钟 |

### 9.4 回滚执行要求

- 回滚决策人：融合负责人（默认后端负责人），启动回滚后 15 分钟内完成 9.3 前三层。
- 每层回滚后立即跑 8.4 回归冒烟（登录+发现页+发笔记），确认小红书健康后再处理 AI 问题复盘。
- 回滚演练：在 M3（后端联调）与 M5（前端联调）结束时各做一次“停 AI + 注释路由”演练，固化操作清单（命令+预期结果）到融合执行日志。
- 数据库 DROP 前必须保留当次快照（含 6.1 执行前备份），回滚后不立即物理删除备份，保留 7 天。

---
## 十、融合完成后的最终目录结构

### 10.1 后端 xiaohashu2（最终）

```
xiaohashu2/
├── pom.xml                          # modules 新增 xiaohashu-ai
├── xiaoha-framework/                # 不变
├── xiaohashu-gateway/               # application.yml 新增 ai 路由 + httpclient 超时
│   └── src/main/resources/application.yml
├── xiaohashu-ai/                    # ★ 新增（原 AI 后端迁入）
│   ├── pom.xml
│   └── xiaohashu-ai-biz/
│       ├── pom.xml                  # Spring Boot 3.4.5 独立版本，JDK17
│       └── src/main/
│           ├── java/com/quanxiaoha/xiaohashu/ai/biz/
│           │   ├── XiaohashuAiBizApplication.java
│           │   ├── advisor/         # 4 个 Advisor（含 userId 过滤）
│           │   ├── config/          # ChatClientConfig/MybatisPlusConfig/OkHttpConfig/PgVectorConfig/ThreadPoolConfig/AsyncEventConfig
│           │   ├── controller/      # ChatController/AiCustomerServiceController
│           │   ├── domain/{dos,mapper}/
│           │   ├── enums/ event/ model/{dto,vo}/
│           │   ├── reader/ service/{impl}/ tools/ utils/
│           │   └── filter/          # （可选）兜底 userId 校验 Filter
│           └── resources/
│               ├── config/{application.yml,application-dev.yml}
│               ├── log4j2.xml
│               └── mcp-servers-config.json   # 无明文 key，生产不加载
├── xiaohashu-auth/ xiaohashu-user/ xiaohashu-note/ xiaohashu-comment/ ...  # 全部不变
└── docs/sql/ai_fusion_v1.sql        # ★ 新增：6.1 建表脚本（入库管理）
```

### 10.2 前端 xiaohashu-vue3（最终）

```
xiaohashu-vue3/
├── package.json                     # +AI 依赖（antd/fetch-event-source/markdown-it 等）
├── vite.config.js                   # +AntD 按需 +svg-icons（保留原 /api proxy）
└── src/
    ├── main.js                      # +svg-icons-register
    ├── axios.js                     # 不变（复用）
    ├── router/index.js              # +/ai/chat、/ai/customer-service 顶级路由
    ├── layouts/BasicLayout.vue      # 不变
    ├── layouts/AiLayout.vue         # ★ 由 AI Layout.vue 迁入（自身工作台侧栏）
    ├── components/layout/AppSidebar.vue   # +AI 助手菜单（登录门）
    ├── components/ai/               # ★ Sidebar/ChatInputBox/StreamMarkdownRender/SvgIcon/LoadingDots
    ├── components/auth/LoginModal.vue     # 不变（AI 页复用）
    ├── stores/user.js               # 不变
    ├── stores/chat.js               # ★ 由 chatStore.js 迁入
    ├── views/ai/                    # ★ ChatPage.vue / CustomerServiceChatPage.vue
    ├── api/ai/{chat,customerService}.js   # ★ 新路径 + 复用小红书 axios
    ├── utils/aiStream.js            # ★ SSE 封装（带 token/401 处理）
    └── assets/icons/                # +AI 15 个 svg（无同名冲突）
```

### 10.3 数据库（最终）

```
MySQL 127.0.0.1:3307/xiaohashu
├── 原有表：t_user/t_note/t_comment/... 全部不变
└── 新增表：t_chat / t_chat_message / t_ai_customer_service_file_storage / t_file_chunk_info（均带 user_id）

PostgreSQL 127.0.0.1:5432/robot
└── t_vector_store（pgvector，结构不变；metadata 带 userId，检索过滤）
```

### 10.4 运行拓扑（最终）

```
xiaohashu-vue3 (5173 dev / dist+nginx 生产)
   └─ /api → Gateway 8000（Sa-Token 鉴权 + userId 透传）
        ├─ lb:// auth / user / note / relation / oss / comment / search ...（原有不变）
        └─ http://127.0.0.1:8090 xiaohashu-ai
             ├─ MySQL xiaohashu（对话/文件业务表）
             └─ PG robot（向量库）
                  外部：阿里云百炼（对话+embedding）、SearXNG 8888、（可选 dev 高德 MCP）
```

### 10.5 运行进程清单（融合后）

| 进程 | 端口 | 说明 |
| --- | --- | --- |
| Nacos / MySQL 3307 / PG 5432 / Redis 6379 | 基础设施 | 原有 |
| xiaohashu-gateway | 8000 | 原有 +ai 路由 |
| auth/user/note/relation/oss/comment/search 等 | 808x | 原有不变 |
| xiaohashu-ai | 8090 | ★ 新增 |
| xiaohashu-vue3 | 5173 | 唯一前端 |
| ~~AI 后端 8080 / AI 前端~~ | 停用 | 验收后关闭 |
| SearXNG | 8888 | 外部依赖（AI 功能） |

### 10.6 收尾清理（验收通过后）

1. 停用 project 两个应用（8080 后端、AI 前端 dev/build 产物）。
2. project 目录整体 git 归档（tag ai-fusion-archive / 移动至 archive 目录），确认无引用后再移除工作副本；最终交付仅 xiaohashu2 + xiaohashu-vue3。
3. 删除融合过程中产生的临时验证产物（如 4.0 冒烟改动），保留执行日志与 C14 备份。

---

## 十一、里程碑与工作量（建议排期）

| 里程碑 | 内容 | 对应章节 | 预估 | 验收标准 |
| --- | --- | --- | --- | --- |
| M0 | 预检与 JDK17 冒烟 | 二、4.0 | 0.5 人日 | C1~C14 全绿、门禁通过 |
| M1 | 后端模块骨架+配置 | 4.1、4.2 | 1 人日 | 空模块可启动，无版本冲突 |
| M2 | 代码迁移+隔离改造 | 4.3、4.4、4.5 | 2~3 人日 | 编译通过，Service 单测越权用例绿 |
| M3 | 网关接入+后端联调 | 4.6、8.1 | 1 人日 | 8.1 P0 全绿，回滚演练 1 次 |
| M4 | 前端迁移改造 | 5.1~5.4 | 1.5 人日 | 页面可用，SSE 带 token 正常 |
| M5 | 前端联调+隔离专项 | 8.2、8.3 | 1 人日 | 8.2/8.3 P0 全绿，回滚演练 1 次 |
| M6 | 回归+安全抽测 | 8.4、8.5 | 1 人日 | 小红书回归无差异，抽测通过 |
| M7 | 收尾清理与归档 | 10.5、10.6 | 0.5 人日 | project 停运归档，文档闭环 |

合计约 8.5~10.5 人日（不含外部依赖故障等待）。

---

## 附录 A：后端迁移对照总表（供直接执行）

| AI 项目文件 | 目标 | 动作 |
| --- | --- | --- |
| XiaohaAiRobotSpringbootApplication | ai/biz/XiaohashuAiBizApplication | 重命名+改包名+@MapperScan |
| advisor/*（4） | ai/biz/advisor/* | 迁移；CustomerServiceAdvisor 加 userId 过滤；记忆/落库 Advisor 保持 chatUuid 维度 |
| config/ChatClientConfig、OkHttpConfig、MybatisPlusConfig、ThreadPoolConfig、AsyncEventConfig | ai/biz/config/* | 迁移；ChatClientConfig 增加 Advisor 装配参数传递 userId |
| config/CorsConfig | 删除 | 同源走网关 |
| config/JacksonConfig | 删除 | 统一 xiaoha jackson starter |
| controller/*（2） | ai/biz/controller/* | 迁移；RequestMapping 不变；路径前缀由网关 /ai 提供；completion 入口加归属校验 |
| domain/dos/*（4） | ai/biz/domain/dos/* | 迁移；ChatDO/AiCustomerServiceFileStorageDO/FileChunkInfoDO 加 userId；ChatMessageDO 加 userId(冗余) |
| domain/mapper/*（4） | ai/biz/domain/mapper/* | 迁移；默认方法加 userId 条件；(user_id,file_md5) 语义 |
| event/*（事件+Listener） | ai/biz/event/* | 迁移；事件加 userId 字段 |
| enums/* | ai/biz/enums/* | ResponseCodeEnum 改为实现 BaseExceptionInterface；code 段避开小红书现有值 |
| exception/* | 删除 | 用 xiaoha-common |
| model/vo/* | ai/biz/model/vo/* | 迁移（含分页查询 VO） |
| reader/MarkdownReader | ai/biz/reader | 迁移 |
| service/*+impl | ai/biz/service/impl | 迁移+隔离改造（4.4） |
| tools/DateTimeTools、utils/{JsonUtil,StringUtil} | ai/biz/... | 按需迁移；utils/Response、PageResponse 删除 |
| aspect/ApiOperationLog* | 删除 | 改用 xiaoha-spring-boot-starter-biz-operationlog |
| resources/application*.yml | config/{application,application-dev}.yml | 重写（4.2） |
| resources/log4j2.xml、mcp-servers-config.json | 迁移+脱敏（4.7） | 迁移 |

## 附录 B：前端迁移对照总表（供直接执行）

| AI 项目文件 | 目标 | 动作 |
| --- | --- | --- |
| api/chat.js、api/customerService.js | api/ai/*.js | 路径加 /ai；改 import '@/axios'（小红书） |
| stores/chatStore.js | stores/chat.js | 迁移+注册进 stores/index.js |
| layouts/Layout.vue | layouts/AiLayout.vue | 迁移；Sidebar import 路径改 components/ai |
| components/{Sidebar,ChatInputBox,StreamMarkdownRender,SvgIcon,LoadingDots}.vue | components/ai/* | 迁移；Sidebar 加返回/退出 |
| views/{ChatPage,CustomerServiceChatPage}.vue | views/ai/* | 迁移；SSE 地址与鉴权改造（5.3） |
| views/Index.vue | 不迁移 | 入口废弃 |
| assets/icons/*.svg | assets/icons/ | 复制 |
| assets/{base,main}.css | 合并进小红书样式 | 抽取+作用域化 |
| main.js / router / axios.js / vite.config.js | 不整体迁移 | 按 5.1/5.4/5.5 增量改造 |

---

> 本方案以“新增模块、零侵入业务、逐层可回滚”为原则；所有标 ★ 的目录/文件为融合新增项，其余均为小红书既有内容，验收后唯一交付物为：xiaohashu2（含 xiaohashu-ai）+ xiaohashu-vue3（含 /ai 模块）。





