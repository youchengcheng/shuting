# 转换小哈书后端 Javadoc 注释格式

## 摘要

将小哈书后端 97 个 Java 文件中匹配 pattern A 的类/包级别 Javadoc 注释，统一转换为 pattern B 简洁格式（仅保留 @description 文字）。删除 @author、@date、@version、@url 等所有标签。

## 当前状态分析

### 探索结论

- **范围**：仅后端 Java 文件，共 **97 个文件**，分布在 `xiaohashu2/`（95 个）和 `project/xiaoha-ai-robot-springboot/`（2 个）。
- **前端 `xiaohashu-vue3/` 无此类注释**，不涉及前端改动。
- **全部为类/接口/枚举/package 级别注释**，无方法级别注释匹配 pattern A。方法 @param 补充不在本次范围内。
- **26 个文件**额外带 `@url: www.quanxiaoha.com`，一并删除。
- **部分 @description 值为 "TODO"**，原样保留为 `/** * TODO */`。

### 注释格式变体

所有匹配项的共同特征：注释块包含 `@author: 犬小哈`，以 `/**` 开头、`**/` 结尾。变体如下：

**变体 1（标准，无 @url）** — 如 `JacksonAutoConfiguration.java`：
```
/**
 * @author: 犬小哈
 * @date: 2024/4/15 13:50
 * @version: v1.0.0
 * @description: 自动配置自定义的 Jackson
 **/
```

**变体 2（含 @url，标签顺序可能不同）** — 如 `JsonUtil.java`、`package-info.java`：
```
/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-08-14 16:27
 * @description: JSON 工具类
 **/
```

**变体 3（@description 为 TODO）** — 如 `AuthService.java`、`AuthController.java`：
```
/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: TODO
 **/
```

### 目标格式（pattern B）

```
/**
 * {description 值}
 */
```

注意：结束符从 `**/` 改为标准 `*/`；@description 的值原样填入（包括 TODO）。

## 实施方案

### 方式：脚本批量处理 + 抽样校验

由于涉及 97 个文件且替换规则统一，采用脚本批量处理，再抽样人工校验。

### 步骤 1：编写处理脚本

使用 Node.js 脚本（通过 Shell 执行），逻辑如下：

1. 遍历 Grep 已确定的 97 个文件路径列表。
2. 对每个文件，用正则匹配包含 `@author: 犬小哈` 的注释块：
   - 正则：`/\/\*\*[\s\S]*?@author:\s*犬小哈[\s\S]*?\*\*\//`
   - 该正则匹配从 `/**` 到 `**/` 的整块注释（含 `@author: 犬小哈`）。
3. 从匹配到的块中提取 `@description:` 后面的文字：
   - 正则：`/@description:\s*(.+)/`
   - 去除首尾空白和可能的前导 `* `。
4. 用提取到的 description 构造替换文本：
   ```
   /**
    * {description}
    */
   ```
5. 替换原注释块，写回文件。
6. 统计处理成功/失败的文件数，输出日志。

### 步骤 2：执行脚本

运行脚本，处理全部 97 个文件。

### 步骤 3：抽样校验

- 用 Grep 确认 `@author: 犬小哈` 在整个项目中已 0 匹配。
- 用 Grep 确认 `@url: www.quanxiaoha.com` 在项目中已 0 匹配（仅限原 97 文件范围内）。
- Read 抽查 3-5 个代表性文件，确认转换结果正确：
  - `JacksonAutoConfiguration.java`（标准变体）
  - `JsonUtil.java`（含 @url 变体）
  - `AuthService.java`（TODO 变体）
  - `package-info.java`（package 级别）

## 假设与决策

1. **仅处理 pattern A 注释**：不触碰方法级别 Javadoc、不补充 @param 说明（用户确认）。
2. **@url 一并删除**：26 个文件的 `@url: www.quanxiaoha.com` 连同 @author/@date/@version 一起移除（用户确认）。
3. **TODO 原样保留**：@description 值为 "TODO" 的，转为 `/** * TODO */`（用户确认）。
4. **结束符统一为 `*/`**：原注释的 `**/` 统一改为标准 `*/`。
5. **description 值原样使用**：不做语义改写，仅去除前后空白。
6. **一个文件仅一处匹配**：Grep 确认每个文件恰好 1 处，脚本对每文件只替换第一处即可。
7. **不修改其他代码**：仅替换注释块，不动类体、方法、import 等任何其他内容。

## 验证步骤

1. `Grep "@author: 犬小哈"` → 应返回 0 匹配。
2. `Grep "@url: www.quanxiaoha.com"` 在 97 文件范围内 → 应返回 0 匹配。
3. Read 5 个抽样文件，逐一核对注释格式符合 pattern B。
4. 确认脚本未误改非目标内容（比对替换前后行数差值合理）。

## 涉及文件清单（97 个，部分示例）

完整列表见 Grep 结果。按模块归类：
- `xiaoha-framework/xiaoha-common`（9 个）
- `xiaoha-framework/xiaoha-spring-boot-starter-jackson`（1 个）
- `xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog`（2 个，含 package-info）
- `xiaohashu-auth`（16 个，含 test）
- `xiaohashu-comment/xiaohashu-comment-biz`（3 个）
- `xiaohashu-count/xiaohashu-count-biz`（8 个）
- `xiaohashu-user-relation/xiaohashu-user-relation-biz`（5 个）
- `xiaohashu-user`（api + biz，6 个）
- `xiaohashu-kv`（api + biz，8 个）
- `xiaohashu-oss`（api + biz，3 个）
- `xiaohashu-note/xiaohashu-note-biz`（14 个）
- `xiaohashu-search/xiaohashu-search-biz`（5 个）
- `xiaohashu-gateway`（2 个）
- `xiaohashu-data-align`（1 个）
- `project/xiaoha-ai-robot-springboot`（2 个）
