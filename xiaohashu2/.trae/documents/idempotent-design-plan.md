# 小红书后端项目幂等性接入方案

> 基于实际代码库探索生成,贴合 `xiaohashu-auth / xiaohashu-note / xiaohashu-comment / xiaohashu-user-relation / xiaohashu-oss / xiaohashu-gateway` 模块现状。
> 方案文档型输出,不直接改动业务代码,作为评审与后续开发的依据。

---

## 〇、当前基础设施盘点(已落地/可复用)

| 项 | 现状 | 文件参考 |
|---|---|---|
| 自定义注解范式 | `@ApiOperationLog(description="")` + `ApiOperationLogAspect`(`@Around` 环绕,`@Pointcut("@annotation(...)")` 切点),独立 starter 模块 | [ApiOperationLog.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/java/com/quanxiaoha/framework/biz/operationlog/aspect/ApiOperationLog.java)<br>[ApiOperationLogAspect.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/java/com/quanxiaoha/framework/biz/operationlog/aspect/ApiOperationLogAspect.java) |
| Redis 工具 | 直接注入 `RedisTemplate<String,Object>`,无封装工具类 | [VerificationCodeServiceImpl.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/service/impl/VerificationCodeServiceImpl.java) |
| RocketMQ 消费者幂等 | 注释明确"通过联合唯一索引保证",`RateLimiter.create(5000)` 削峰 | [LikeUnlikeNoteConsumer.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-note/xiaohashu-note-biz/src/main/java/com/quanxiaoha/xiaohashu/note/biz/consumer/LikeUnlikeNoteConsumer.java)<br>[FollowUnfollowConsumer.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/consumer/FollowUnfollowConsumer.java) |
| 网关层 | `AddUserId2HeaderFilter` 仅做 token→userId 透传,**未做防重** | [AddUserId2HeaderFilter.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-gateway/src/main/java/com/quanxiaoha/xiaohashu/gateway/filter/AddUserId2HeaderFilter.java) |
| 发送验证码 | 已用 Redis key + `hasKey()` 防频繁发送,**存在 check-then-set race condition** | [VerificationCodeServiceImpl.java#L48-L68](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/service/impl/VerificationCodeServiceImpl.java) |
| 登录 | `StpUtil.login(userId)` 颁发 token,**无重复请求防护** | [AuthServiceImpl.java#L125-L132](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/service/impl/AuthServiceImpl.java) |
| 发布笔记 | `NoteController.publishNote` 直连 `noteService.publishNote`,**无防重** | [NoteController.java#L30-L34](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-note/xiaohashu-note-biz/src/main/java/com/quanxiaoha/xiaohashu/note/biz/controller/NoteController.java) |
| 中间件依赖 | Spring Boot 3.0.2 / Spring Cloud Alibaba 2022.0.0.0 / Sa-Token 1.38.0 / MySQL+Druid / Redis / RocketMQ;**未引入 Redisson** | [pom.xml](file:///d:/critic/xiaoshuting/xiaohashu2/pom.xml) |

> 关键约束:Redis 已就绪,RocketMQ 已就绪,**Redisson 未引入**(方案设计需考虑是否新增依赖)。
> 关键架构:Spring Cloud Alibaba 微服务,Sa-Token 鉴权,网关层 AddUserId2HeaderFilter 已透传 userId。

---

## 一、业务场景排查(全量写接口分类)

> 强幂等:重复请求会产生不可恢复的业务损害(数据冗余、计数错乱、外部副作用)。
> 弱幂等:重复请求有副作用但可恢复或天然覆盖(状态覆盖、二次删除报错)。

### 1.1 必须做(强幂等,重复请求会产生不可恢复的业务损害)

| 接口 | 路径 | 重复请求产生的业务问题 | 现有防护 | 优先级 |
|---|---|---|---|---|
| 发送验证码 | `POST /verification/code/send` | (1) 短信通道被刷,短信费用浪费(每条约 0.04 元);(2) 对用户造成短信轰炸,客诉风险;(3) 阿里云短信通道被风控封禁 | Redis `hasKey`(有 race condition) | P0 |
| 用户登录/注册 | `POST /login` | (1) 验证码登录下新用户重复创建(若 RPC 不幂等);(2) 多次颁发 token,旧 token 失效但日志混乱;(3) 密码错误次数计数失真,撞库风险;(4) 风控审计日志重复 | 无 | P0 |
| 发布笔记 | `POST /note/publish` | (1) 同一笔记内容重复落库,数据冗余;(2) 重复发 MQ 消息,计数翻倍;(3) 用户主页出现重复笔记,体验差 | 无 | P0 |
| 发布评论 | `POST /comment/publish` | (1) 同一评论重复落库;(2) 评论计数翻倍;(3) 通知重复推送 | 需确认 | P1 |
| 笔记点赞 | `POST /note/like` | (1) HTTP 层不防重会触发 MQ 重复消息;(2) 计数错乱(虽有 DB 唯一索引兜底,但计数表+1可能重复) | MQ 消费端唯一索引 | P1 |
| 取消点赞 | `POST /note/unlike` | 同上 | 同上 | P1 |
| 收藏/取消收藏 | `POST /note/collect` `/uncollect` | 同点赞逻辑 | MQ 消费端唯一索引 | P1 |
| 关注/取关 | `POST /relation/follow` `/unfollow` | (1) 关系表数据冗余(虽有联合唯一索引兜底);(2) 计数表+1重复;(3) 通知重复 | MQ 消费端联合唯一索引 | P1 |
| 修改密码 | `POST /password/update` | (1) 重复提交触发风控告警误报;(2) 密码错误次数计数失真 | 无 | P2 |

### 1.2 建议做(弱幂等,重复请求有副作用但可恢复)

| 接口 | 路径 | 重复请求产生的业务问题 | 建议 |
|---|---|---|---|
| 更新笔记 | `POST /note/update` | 多次更新幂等(覆盖语义),但若含+1计数字段则错乱 | 视字段而定,无计数可不做 |
| 修改可见性 | `POST /note/visible` `/visible/onlyme` | 状态翻转,重复请求可能错乱(若实现为 toggle) | 若为 set 状态可不做 |
| 笔记置顶 | `POST /note/top` | 重复请求无副作用(状态置顶) | 不需要 |
| 删除笔记 | `POST /note/delete` | 二次删除报错,体验差 | 前端禁用即可 |
| 删除评论 | `POST /comment/delete` | 同上 | 同上 |
| 评论点赞/取消点赞 | `POST /comment/like` `/unlike` | 同笔记点赞 | 复用笔记点赞方案 |

### 1.3 可选做(影响小或天然幂等)

| 接口 | 路径 | 说明 |
|---|---|---|
| 退出登录 | `POST /logout` | SaToken 重复 logout 无副作用,天然幂等 |
| 文件上传 | `POST /file/upload` | 用户重传同图可接受,如需去重应走后台 MD5 比对清理,而非 HTTP 层幂等 |

### 1.4 不需要做(读接口天然幂等)

`/note/detail`、`/note/published/list`、`/note/isLikedAndCollectedData`、`/relation/follow/list`、`/relation/fans/list`、`/relation/check-followed`、所有评论分页查询。

---

## 二、幂等实现方案(5 套,均适配现有业务)

### 方案对比矩阵

| 方案 | 强幂等 | 前端改造 | 新依赖 | 开发成本 | 适用业务 |
|---|---|---|---|---|---|
| A. Token 机制 | ✅ 强 | ✅ 需要 | 无 | 中(1-2人日/接口) | 发布笔记、发布评论 |
| B. 唯一索引兜底 | ✅ 强 | ❌ 无 | 无 | 低(0.5人日/接口) | 点赞、收藏、关注 |
| C. Redis 防重窗口 | ⚠️ 弱 | ❌ 无 | 无 | 低(0.5人日/接口) | 验证码、登录、改密 |
| D. 分布式锁 | ✅ 强 | ❌ 无 | Redisson | 中(1人日/接口+依赖) | 发布笔记(备选) |
| E. 请求指纹 | ⚠️ 弱 | ❌ 无 | 无 | 低(0.5人日) | 发布笔记(兜底) |

---

### 方案 A:Token 机制(前端申请-后端校验,强幂等)

**核心原理**:前端在提交业务前先调 `GET /idempotent/token` 拿一次性 token(UUID),业务请求 Header 带 `Idempotent-Token`,后端 Redis `DEL key` 返回影响行数=1 才放行(原子性)。

**实现步骤**:
1. 新建 `IdempotentController` 暴露 `GET /idempotent/token`,生成 UUID,`SET key "" EX 600`(10分钟)。
2. 自定义注解 `@Idempotent(policy=TOKEN)`,切面拦截带注解的方法。
3. 切面读 Header token,拼 key `idempotent:token:{userId}:{token}`,执行 `redisTemplate.delete(key)`,返回 false 抛 `BizException(IDEMPOTENT_TOKEN_INVALID)`。
4. 业务方法执行,若抛异常则不补 token(让重试拿新 token)。
5. 灰度期:Header 缺失 token 的请求先记录日志放行,不直接拒绝。

**适用业务**:发布笔记(强幂等)、发布评论。

**优点**:
- 强幂等,token 一次性消费天然防重放。
- 前后端协同,业界标准做法。
- 无新中间件依赖(仅 Redis)。

**缺点**:
- 需前端改造(多一次请求)。
- token 失效场景需前端处理。
- 用户填写表单超过 10 分钟需重新申请 token。

**技术成本**:
- 开发:1-2 人日/接口 + 切面公共部分 1 人日。
- 维护:低,切面通用。
- 中间件:仅 Redis(已有)。

**完整伪代码**:

```java
// === 1. Token 申请接口 ===
@RestController
public class IdempotentController {
    @Resource private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/idempotent/token")
    public Response<String> issueToken() {
        String userId = StpUtil.getLoginIdAsString();
        String token = IdUtil.simpleUUID();
        String key = "idempotent:token:" + userId + ":" + token;
        redisTemplate.opsForValue().set(key, "", 10, TimeUnit.MINUTES);
        return Response.success(token);
    }
}

// === 2. 业务接口加注解 ===
@Idempotent(policy = IdempotentPolicy.TOKEN)
@PostMapping("/note/publish")
public Response<?> publishNote(@RequestBody PublishNoteReqVO vo) { ... }

// === 3. 切面拦截 ===
private void checkToken(String userId, Idempotent anno) {
    String token = request.getHeader("Idempotent-Token");
    if (StrUtil.isBlank(token)) {
        // 灰度期:缺失 token 记录日志放行;正式期:throw
        log.warn("[灰度] Idempotent token missing, userId={}", userId);
        return;
    }
    String key = "idempotent:token:" + userId + ":" + token;
    // 原子操作:DEL 返回影响行数,=1 表示首次提交,=0 表示重复
    if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
        throw new BizException(ResponseCodeEnum.IDEMPOTENT_TOKEN_INVALID);
    }
}
```

**前端联调(Vue3)**:
```javascript
// 发布笔记按钮
async handlePublish() {
  this.loading = true;
  try {
    const { data: token } = await axios.get('/idempotent/token');
    await axios.post('/note/publish', this.formData, {
      headers: { 'Idempotent-Token': token }
    });
    ElMessage.success('发布成功');
  } catch (e) {
    if (e.code === 'IDEMPOTENT_TOKEN_INVALID') {
      // token 失效,重新申请并提示用户
      ElMessage.warning('请勿重复提交,正在刷新...');
    }
  } finally {
    this.loading = false;
  }
}
```

---

### 方案 B:业务唯一键 + 数据库唯一索引(DB 兜底,强幂等)

**核心原理**:业务维度组合生成自然唯一键,依赖 DB `UNIQUE KEY` 兜底,捕获 `DuplicateKeyException` 转业务异常。本项目 MQ 消费端已采用此模式。

**实现步骤**:
1. 评估接口是否有自然唯一键(如 `user_id+note_id+type` 表示一次点赞)。
2. 建表加 `UNIQUE KEY uk_user_note_type (user_id, note_id, type)`。
3. Service 内 `try { insert } catch (DuplicateKeyException e) { return success()/throw 重复提交 }`。
4. 注意:计数表 `+1` 操作需在 `insert` 成功后执行,避免重复计数。

**适用业务**:点赞、收藏、关注(已有实践);**不适合**发布笔记(无自然唯一键)、登录、验证码。

**优点**:
- 简单可靠,DB 终极兜底,强一致。
- 无需中间件。
- 失败可恢复(`DuplicateKeyException` 后业务可继续)。

**缺点**:
- 仅适合有自然唯一键场景。
- `DuplicateKeyException` 捕获需精确,误吞会掩盖真实 bug。
- 高并发下大量 insert 冲突会消耗 DB 连接。

**技术成本**:
- 开发:0.5 人日/接口(加唯一索引 + 异常捕获)。
- 维护:极低。
- 中间件:无。

**完整伪代码**:

```java
@Service
public class NoteLikeServiceImpl implements NoteLikeService {
    @Resource private NoteLikeMapper noteLikeMapper;
    @Resource private NoteCountMapper noteCountMapper;

    @Transactional(rollbackFor = Exception.class)
    public Response<?> likeNote(Long userId, Long noteId) {
        try {
            // 1. 插入点赞记录(唯一索引兜底)
            noteLikeMapper.insert(NoteLikeDO.builder()
                .userId(userId).noteId(noteId).build());
            // 2. 计数+1(只在 insert 成功后)
            noteCountMapper.incrLikeCount(noteId, 1);
            return Response.success();
        } catch (DuplicateKeyException e) {
            // 幂等返回:重复点赞视为成功
            log.info("重复点赞,忽略: userId={}, noteId={}", userId, noteId);
            return Response.success();
        }
    }
}
```

**建表 SQL 示例**:
```sql
ALTER TABLE note_like
ADD UNIQUE KEY uk_user_note (user_id, note_id);
```

---

### 方案 C:Redis 防重窗口(状态机防重,弱幂等)

**核心原理**:以 `业务键+用户ID` 为 Redis key,`SETNX + EX` 原子操作,N 秒内重复请求直接拒绝。窗口过期后允许再次提交。**关键:修复现有验证码 `hasKey` race condition,改为 `setIfAbsent` 原子操作。**

**实现步骤**:
1. 切面拼 key `idempotent:window:{bizKey}:{userId}`。
2. `redisTemplate.opsForValue().setIfAbsent(key, "1", expire, SECONDS)`,返回 false 则拒绝。
3. 业务执行结束**不主动删 key**(让窗口自然过期),或成功后删除以允许下次操作。
4. 修复 `VerificationCodeServiceImpl` 的 `hasKey + set` race,改为 `setIfAbsent`。

**适用业务**:发送验证码(已有,需修复 race)、登录请求防连点、修改密码防连点。

**优点**:
- 实现简单,无前端改造。
- 无新依赖。
- 业务语义清晰("N 秒内不重复")。

**缺点**:
- 弱幂等,窗口期内拒绝合理重试。
- 窗口外仍可能重复(需配合业务状态校验)。
- 窗口时长需业务调优。

**技术成本**:
- 开发:0.5 人日/接口。
- 维护:低。
- 中间件:无。

**完整伪代码**(含验证码 race 修复):

```java
// === 验证码 race 修复 ===
@Override
public Response<?> send(SendVerificationCodeReqVO vo) {
    String phone = vo.getPhone();
    String key = RedisKeyConstants.buildVerificationCodeKey(phone);

    // 生成验证码
    String code = RandomUtil.randomNumbers(6);

    // ✅ 原子操作:setIfAbsent 同时完成"判断+设置"
    Boolean ok = redisTemplate.opsForValue()
        .setIfAbsent(key, code, 3, TimeUnit.MINUTES);
    if (!Boolean.TRUE.equals(ok)) {
        throw new BizException(VERIFICATION_CODE_SEND_FREQUENTLY);
    }

    // 异步发短信(若失败,验证码已在 Redis,用户仍可使用)
    threadPoolTaskExecutor.submit(() -> {
        aliyunSmsHelper.sendMessage("阿里云短信测试", "SMS_154950909",
            phone, String.format("{\"code\":\"%s\"}", code));
    });

    return Response.success();
}

// === 登录防连点 ===
@Idempotent(policy = IdempotentPolicy.REDIS_WINDOW,
            key = "#vo.phone", expireSeconds = 5)
@PostMapping("/login")
public Response<String> loginAndRegister(@RequestBody UserLoginReqVO vo) { ... }

// === 切面 ===
private void checkWindow(String userId, ProceedingJoinPoint jp, Idempotent anno) {
    String bizKey = parseKey(anno, jp);  // SpEL 解析 #vo.phone
    String key = "idempotent:window:" + userId + ":" + bizKey;
    if (!Boolean.TRUE.equals(
            redisTemplate.opsForValue()
                .setIfAbsent(key, "1", anno.expireSeconds(), TimeUnit.SECONDS))) {
        throw new BizException(ResponseCodeEnum.IDEMPOTENT_DUPLICATE, anno.message());
    }
}
```

---

### 方案 D:分布式锁(Redisson RLock,串行化+状态校验)

**核心原理**:对 `业务键+用户ID` 加分布式锁,锁内执行"查业务状态→未操作则执行→已操作则返回",天然防并发。

**实现步骤**:
1. 引入 Redisson 依赖(当前 pom 未引入,需新增 `redisson-spring-boot-starter`)。
2. 配置 Redisson client(基于 Redis 连接)。
3. 切面 `RLock lock = redissonClient.getLock(key); boolean locked = lock.tryLock(0, 10, SECONDS);`。
4. 锁内调用业务方法(业务方法自含状态校验,如"已发布则返回")。
5. finally `if (lock.isHeldByCurrentThread()) lock.unlock()`。

**适用业务**:发布笔记(同用户瞬时连点)、修改密码(防并发)。

**优点**:
- 灵活,可与业务状态校验结合实现强幂等。
- 锁粒度可控。
- 不需要前端改造。

**缺点**:
- 引入 Redisson 新依赖。
- 锁有性能开销。
- 锁失效场景(主从切换、GC 停顿)需评估。
- 锁内业务需自带状态校验,否则锁释放后仍可能重复。

**技术成本**:
- 开发:1 人日/接口 + 引入依赖评估 0.5 人日。
- 维护:中(锁的监控、续期配置)。
- 中间件:新增 Redisson(基于 Redis)。

**完整伪代码**:

```java
// === pom.xml 新增 ===
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.20.0</version>
</dependency>

// === application.yml ===
spring:
  redis:
    host: ${REDIS_HOST}
    port: 6379
redisson:
  config: |
    singleServerConfig:
      address: "redis://${REDIS_HOST}:6379"

// === 业务接口 ===
@Idempotent(policy = IdempotentPolicy.REDIS_LOCK,
            key = "#vo.title", expireSeconds = 10)
@PostMapping("/note/publish")
public Response<?> publishNote(@RequestBody PublishNoteReqVO vo) {
    // 业务内自带状态校验(锁内查重)
    return noteService.publishNote(vo);
}

// === 切面 ===
private Object lockAndProceed(ProceedingJoinPoint jp, String userId, Idempotent anno) throws Throwable {
    String bizKey = parseKey(anno, jp);
    String key = "idempotent:lock:" + userId + ":" + bizKey;
    RLock lock = redissonClient.getLock(key);
    // tryLock:waitTime=0(不等待),leaseTime=expireSeconds(自动释放)
    boolean locked = lock.tryLock(0, anno.expireSeconds(), TimeUnit.SECONDS);
    if (!locked) {
        throw new BizException(ResponseCodeEnum.IDEMPOTENT_CONCURRENT);
    }
    try {
        return jp.proceed();
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

---

### 方案 E:请求指纹防重(MD5 of body,弱幂等)

**核心原理**:对请求体(去掉时间戳字段)计算 MD5 指纹,Redis 缓存 N 秒内已见指纹,命中即拒绝。

**实现步骤**:
1. 切面序列化方法入参为 JSON(复用 `JsonUtils`),计算 MD5。
2. key=`idempotent:fingerprint:{userId}:{md5}`,`setIfAbsent` + TTL 60s。
3. 命中拒绝,未命中放行并记录。
4. 注意:序列化需保证字段顺序稳定(VO 字段顺序固定)。

**适用业务**:发布笔记、发布评论(防相同内容连点)。

**优点**:
- 无需前端改造。
- 无需业务唯一键。
- 无新依赖。

**缺点**:
- 弱幂等,只防相同内容连点。
- 用户改一个字就会绕过。
- 误拒合理修改(短窗口可接受)。
- 序列化顺序敏感(VO 字段变动会失效)。

**技术成本**:
- 开发:0.5 人日。
- 维护:低。
- 中间件:无。

**完整伪代码**:

```java
private void checkFingerprint(String userId, ProceedingJoinPoint jp, Idempotent anno) {
    // 1. 序列化入参为 JSON(字段顺序由 Jackson 保证)
    String body = JsonUtils.toJsonString(jp.getArgs());
    // 2. 计算 MD5(含 userId 防跨用户误撞)
    String md5 = DigestUtils.md5Hex(body + "|" + userId);
    String key = "idempotent:fp:" + userId + ":" + md5;
    // 3. setIfAbsent 原子操作
    if (!Boolean.TRUE.equals(
            redisTemplate.opsForValue()
                .setIfAbsent(key, "1", anno.expireSeconds(), TimeUnit.SECONDS))) {
        throw new BizException(ResponseCodeEnum.IDEMPOTENT_DUPLICATE, anno.message());
    }
}
```

---

## 三、三大业务选型与理由

### 1. 发送验证码 `/verification/code/send`

| | 方案 | 理由 |
|---|---|---|
| **推荐** | 方案 C(Redis 防重窗口) | (1) 已有实现基础,只需把 `hasKey+set` 改为 `setIfAbsent` 原子操作修复 race condition;(2) 业务语义就是"N 分钟内不重复发送",防重窗口天然适配;(3) 无前端改造;(4) 业务上验证码 3 分钟过期,窗口与验证码 TTL 一致 |
| 备选 | 方案 E(指纹) | 若未来想按"同一手机号+IP 指纹"防刷更严,可用指纹兜底 |

**关键改造点**:`VerificationCodeServiceImpl.send()` 的 L48-L52 `hasKey` + L68 `set` 改为单次 `setIfAbsent`。

### 2. 用户登录 `/login`

| | 方案 | 理由 |
|---|---|---|
| **推荐** | 方案 C(Redis 防重窗口,key=手机号+IP) | (1) 登录无业务唯一键;(2) 登录态本身由 SaToken 颁发,重复登录会覆盖旧 token 但不会数据错乱;(3) 真正要防的是"连点+撞库刷接口",防重窗口 5-10 秒足够;(4) 不影响"切换设备登录"等正常场景(不同 IP 各自有窗口) |
| 备选 | 方案 A(Token) | 若未来对接支付/风控强要求,可升级为 token 机制 |

**关键设计**:key 用 `phone + IP`,避免同一用户多设备并发登录被误拒;TTL 设 5 秒(短窗口,不影响正常重试)。

### 3. 发布笔记 `/note/publish`

| | 方案 | 理由 |
|---|---|---|
| **推荐** | 方案 A(Token 机制) | (1) 笔记无自然唯一键,需要强幂等;(2) 前端按钮置灰 + 后端 token 兜底是业界标准做法;(3) 发布是低频高价值操作,多一次 token 请求可接受;(4) 10 分钟 TTL 覆盖用户填写表单时长 |
| 备选 | 方案 D(分布式锁)+ 方案 E(指纹)组合 | 若前端无法改造,则用"分布式锁防并发 + 指纹防 60 秒内连点"组合;但需引入 Redisson |

**关键改造点**:
- 后端:新建 `IdempotentController` 暴露 token 申请接口。
- 前端:发布按钮 `:loading="true"` + 提交前先调 `/idempotent/token`。
- 灰度:Header 缺失 token 的请求先放行,1 周后强制要求。

---

## 四、边界问题处理

### 4.1 各场景幂等策略

| 场景 | 处理策略 |
|---|---|
| **网络超时** | (1) 网关层超时 30s,业务层超时 10s,Redis 超时 1s;(2) 前端设合理超时(10s),超时不自动重试;(3) 后端 token 机制下,重试携带相同 token 会被拒,前端提示"操作可能成功,请刷新确认" |
| **重试** | (1) 区分业务重试与网络重试:业务重试必须用新 token;(2) 网络重试(Same token)被后端幂等拒绝;(3) HTTP 客户端不建议自动重试 POST 请求 |
| **前端重复点击** | (1) 前端按钮 `:loading="true"`+`disabled`(Vue3 项目已有规范);(2) 后端 token/锁兜底;(3) 防抖(debounce 500ms)作为第三道防线 |
| **MQ 重复消费** | (1) 沿用现有模式:消费者端依赖 DB 联合唯一索引 + 捕获 `DuplicateKeyException`;(2) `LikeUnlikeNoteConsumer`、`FollowUnfollowConsumer` 已实现,新业务沿用;(3) 消费者侧不再额外加 Redis 防重,DB 兜底足够 |

### 4.2 Token 失效与过期设计

| 项 | 设计 |
|---|---|
| Token TTL | 10 分钟(用户填写表单的合理上限) |
| Token 失效响应 | 业务码 `IDEMPOTENT_TOKEN_INVALID`,前端收到后自动重新申请 token 并提示用户重新提交 |
| Token 一次性 | `DEL key` 返回影响行数判定,消费即失效,天然防重放 |
| Token 与登录态 | key 拼接 `userId`,未登录场景(如登录本身)用 `phone+IP` 拼接 |
| Token 池 | 不需要预生成,实时申请即可,Redis 容量充足(每 token 占用约 100 字节) |
| Token 清理 | Redis TTL 自动过期,无需手动清理 |

### 4.3 唯一索引 / 锁 / 缓存取舍

| 条件 | 优先选 | 理由 |
|---|---|---|
| 有自然业务唯一键(如 user+note+type) | **DB 唯一索引** | 终极兜底,强一致,无需中间件,失败可恢复 |
| 无唯一键,需短时防连点 | **Redis 防重窗口/指纹** | 轻量,无新依赖,适合弱幂等 |
| 无唯一键,需强一致+串行化 | **分布式锁 + 业务状态校验** | 锁内查状态,但需评估 Redisson 引入 |
| 跨服务长流程 | **Token 机制** | 前后端协同,强幂等,但前端需改造 |
| 高并发写且可接受最终一致 | **唯一索引 + MQ 削峰** | 已有实践(LikeUnlikeNoteConsumer) |

> **本项目取向**:能上唯一索引的(点赞/收藏/关注)已落地,继续沿用;无法上唯一索引的(验证码/登录/发布笔记)按上述推荐方案。

### 4.4 改造注意事项与灰度

| 项 | 策略 |
|---|---|
| **兼容老请求** | (1) Token 机制上线时,Header 缺失 `Idempotent-Token` 的请求先记录日志放行(灰度期内),不直接拒绝;(2) 验证码 race 修复对老请求透明;(3) 灰度开关可用配置中心 Nacos 控制 |
| **灰度上线** | (1) 按"验证码 → 登录 → 发布笔记"顺序逐接口灰度;(2) 每个接口灰度 1-2 天观察 `IDEMPOTENT_*` 业务码命中频次;(3) 灰度比例从 10% → 50% → 100% |
| **回滚预案** | (1) 切面以注解为切点,下线只需移除方法上的 `@Idempotent` 注解或切面 Bean,无需改业务代码;(2) Nacos 配置开关一键关闭切面 |
| **测试点** | (1) 单接口连点 10 次,只应成功 1 次;(2) 并发 10 线程同 token,只 1 个成功;(3) token 过期后重新申请可用;(4) MQ 重复消息消费幂等;(5) 网络抖动下前端重试不会产生重复数据;(6) 灰度期内老请求(无 token)正常通过;(7) Redis 宕机时切面降级(放行 + 告警,不阻塞业务);(8) 跨用户相同内容不被误拒(指纹含 userId) |

---

## 五、自定义注解实现思路(快速接入)

### 5.1 模块结构(参照 `xiaoha-spring-boot-starter-biz-operationlog`)

```
xiaoha-framework/
└── xiaoha-spring-boot-starter-biz-idempotent/
    ├── pom.xml
    └── src/main/java/com/quanxiaoha/framework/biz/idempotent/
        ├── Idempotent.java              # 注解定义
        ├── IdempotentPolicy.java        # 策略枚举
        ├── IdempotentAspect.java        # 切面实现
        ├── IdempotentController.java    # Token 申请接口
        ├── IdempotentKeyParser.java     # SpEL 解析器
        └── autoconfigure/
            └── IdempotentAutoConfiguration.java
```

### 5.2 注解定义(参照 `@ApiOperationLog` 范式)

```java
package com.quanxiaoha.framework.biz.idempotent;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Idempotent {
    /** 幂等策略 */
    IdempotentPolicy policy() default IdempotentPolicy.REDIS_WINDOW;

    /** 过期时间(秒),默认 60s */
    int expireSeconds() default 60;

    /** 防重 key 表达式(SpEL),仅 REDIS_LOCK/FINGERPRINT/REDIS_WINDOW 使用 */
    String key() default "";

    /** 错误提示信息 */
    String message() default "请勿重复提交";
}

public enum IdempotentPolicy {
    TOKEN,           // 方案 A:前端 token
    UNIQUE_INDEX,    // 方案 B:仅标记,实际由 DB 兜底(切面不拦截)
    REDIS_WINDOW,    // 方案 C:防重窗口
    REDIS_LOCK,      // 方案 D:Redisson 分布式锁
    FINGERPRINT      // 方案 E:请求指纹
}
```

### 5.3 切面骨架(参照 `ApiOperationLogAspect`)

```java
package com.quanxiaoha.framework.biz.idempotent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.ResponseCodeEnum;
import com.quanxiaoha.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.MethodBasedEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class IdempotentAspect {
    @Resource private RedisTemplate<String, Object> redisTemplate;
    @Resource(required = false) private RedissonClient redissonClient;  // 可选,按需引入
    private final ExpressionParser parser = new SpelExpressionParser();

    @Pointcut("@annotation(com.quanxiaoha.framework.biz.idempotent.Idempotent)")
    public void idempotent() {}

    @Around("idempotent()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Idempotent anno = getIdempotent(joinPoint);
        String userId = resolveUserId();

        switch (anno.policy()) {
            case TOKEN        -> checkToken(userId, anno);
            case REDIS_WINDOW -> checkWindow(userId, joinPoint, anno);
            case REDIS_LOCK   -> return lockAndProceed(joinPoint, userId, anno);
            case FINGERPRINT  -> checkFingerprint(userId, joinPoint, anno);
            case UNIQUE_INDEX -> { /* 标记用,DB 兜底,不拦截 */ }
        }
        return joinPoint.proceed();
    }

    private void checkToken(String userId, Idempotent anno) {
        HttpServletRequest request = currentRequest();
        String token = request.getHeader("Idempotent-Token");
        if (StrUtil.isBlank(token)) {
            // 灰度期:缺失 token 记录日志放行;正式期:throw
            log.warn("[灰度] Idempotent token missing, userId={}", userId);
            return;
        }
        String key = "idempotent:token:" + userId + ":" + token;
        if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
            throw new BizException(ResponseCodeEnum.IDEMPOTENT_TOKEN_INVALID);
        }
    }

    private void checkWindow(String userId, ProceedingJoinPoint jp, Idempotent anno) {
        String bizKey = parseKey(anno, jp);
        String key = "idempotent:window:" + userId + ":" + bizKey;
        if (!Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", anno.expireSeconds(), TimeUnit.SECONDS))) {
            throw new BizException(ResponseCodeEnum.IDEMPOTENT_DUPLICATE, anno.message());
        }
    }

    private Object lockAndProceed(ProceedingJoinPoint jp, String userId, Idempotent anno) throws Throwable {
        if (redissonClient == null) {
            log.warn("RedissonClient not configured, fallback to no-op");
            return jp.proceed();
        }
        String bizKey = parseKey(anno, jp);
        String key = "idempotent:lock:" + userId + ":" + bizKey;
        RLock lock = redissonClient.getLock(key);
        if (!lock.tryLock(0, anno.expireSeconds(), TimeUnit.SECONDS)) {
            throw new BizException(ResponseCodeEnum.IDEMPOTENT_CONCURRENT);
        }
        try {
            return jp.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void checkFingerprint(String userId, ProceedingJoinPoint jp, Idempotent anno) {
        String body = JsonUtils.toJsonString(jp.getArgs());
        String md5 = DigestUtil.md5Hex(body + "|" + userId);
        String key = "idempotent:fp:" + userId + ":" + md5;
        if (!Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", anno.expireSeconds(), TimeUnit.SECONDS))) {
            throw new BizException(ResponseCodeEnum.IDEMPOTENT_DUPLICATE, anno.message());
        }
    }

    private String parseKey(Idempotent anno, ProceedingJoinPoint jp) {
        if (StrUtil.isBlank(anno.key())) {
            return jp.getSignature().toShortString();
        }
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        EvaluationContext ctx = new MethodBasedEvaluationContext(
            jp.getTarget(), method, jp.getArgs(),
            new DefaultParameterNameDiscoverer());
        return parser.parseExpression(anno.key()).getValue(ctx, String.class);
    }

    private String resolveUserId() {
        try {
            return Optional.ofNullable(StpUtil.getLoginIdAsString())
                .orElse(currentRequest().getRemoteAddr());
        } catch (Exception e) {
            return currentRequest().getRemoteAddr();
        }
    }

    private Idempotent getIdempotent(ProceedingJoinPoint jp) {
        Method m = ((MethodSignature) jp.getSignature()).getMethod();
        return m.getAnnotation(Idempotent.class);
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
            .getRequest();
    }
}
```

### 5.4 错误码定义

```java
// 在 framework-common 的 ResponseCodeEnum 中补充
IDEMPOTENT_TOKEN_INVALID("10001", "提交令牌已失效,请重新提交"),
IDEMPOTENT_DUPLICATE("10002", "请勿重复提交"),
IDEMPOTENT_CONCURRENT("10003", "操作过于频繁,请稍后再试");
```

### 5.5 业务接入示例

```java
// 发送验证码(修复 race + 防重窗口)
@Idempotent(policy = IdempotentPolicy.REDIS_WINDOW,
            key = "#vo.phone", expireSeconds = 180)
@PostMapping("/verification/code/send")
public Response<?> send(@RequestBody SendVerificationCodeReqVO vo) { ... }

// 用户登录(防连点 5 秒)
@Idempotent(policy = IdempotentPolicy.REDIS_WINDOW,
            key = "#vo.phone", expireSeconds = 5)
@PostMapping("/login")
public Response<String> loginAndRegister(@RequestBody UserLoginReqVO vo) { ... }

// 发布笔记(强幂等 Token)
@Idempotent(policy = IdempotentPolicy.TOKEN, expireSeconds = 600)
@PostMapping("/note/publish")
public Response<?> publishNote(@RequestBody PublishNoteReqVO vo) { ... }
```

---

## 六、不适合做幂等的接口(避免过度设计)

| 接口 | 理由 |
|---|---|
| 所有 GET 查询接口(`/detail`、`/published/list`、`/isLikedAndCollectedData`、关注/粉丝列表等) | 读操作天然幂等,重复请求无副作用 |
| `POST /logout` | SaToken 重复 logout 无副作用,天然幂等 |
| `POST /file/upload` | 用户重传同图是合理行为,如需去重应走后台 MD5 比对清理,而非 HTTP 层幂等 |
| `POST /note/update`、`/note/visible`、`/note/top` | 状态覆盖式更新天然幂等(除非含+1计数字段),无需额外防重 |
| `POST /note/delete`、`/comment/delete` | 二次删除返回错,前端处理即可,DB 已有约束 |
| 数据对齐/统计类内部接口 | 不在用户路径,已有 `UNIQUE KEY` 兜底 |

> **强幂等 vs 弱幂等划分**:涉及"创建数据/外部副作用/计数"的(验证码、登录、发布笔记、发布评论、点赞、收藏、关注)属强幂等,必须做;涉及"状态覆盖/删除"的属弱幂等,按业务影响评估。

---

## 七、落地步骤建议(供执行参考,本计划不直接改代码)

1. 新建 `xiaoha-spring-boot-starter-biz-idempotent` 模块,实现 `@Idempotent` 注解 + `IdempotentAspect` + `IdempotentController`(`/idempotent/token`)。
2. 修复 [VerificationCodeServiceImpl.java](file:///d:/critic/xiaoshuting/xiaohashu2/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/service/impl/VerificationCodeServiceImpl.java) 的 `hasKey+set` race,改为 `setIfAbsent` 原子操作。
3. `/login` 加 `@Idempotent(policy=REDIS_WINDOW, key="#vo.phone", expireSeconds=5)`。
4. `/note/publish` 加 `@Idempotent(policy=TOKEN, expireSeconds=600)`,前端发布按钮置灰 + Header 携带 token。
5. 灰度:验证码 → 登录 → 发布笔记,逐接口观察 1-2 天。
6. 后续:发布评论、修改密码按需补齐。

---

## 八、假设与决策

- **假设**:前端 Vue3 工程可配合改造(发布笔记需多调一次 `/idempotent/token`,登录/验证码无需改造)。
- **假设**:Redis 已部署并稳定,RocketMQ 已部署。
- **决策**:暂不引入 Redisson,方案 D 仅作为发布笔记 token 无法落地时的备选;验证码与登录优先用方案 C(无新依赖)。
- **决策**:MQ 消费端幂等沿用现有"联合唯一索引 + DuplicateKeyException"模式,不在 HTTP 切面层重复实现。
- **决策**:本计划为方案文档,不直接改动业务代码;待用户确认后再进入实施阶段。
