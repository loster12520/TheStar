# 为 TheStar 项目新增 4 个代码审阅 Subagent

## Context

TheStar 是一个 Kotlin Multiplatform (JS/IR) 响应式 UI 框架项目。目前项目没有 `.claude/agents/` 目录和任何自定义 subagent。为了系统化地提升代码质量，需要创建 4 个专门化代码审阅 subagent，分别聚焦于命名风格、代码质量、安全性、性能四个维度。

## 设计方案概要

### 文件结构

```
.claude/agents/
  code-review-naming.md        # 命名风格审查
  code-review-quality.md       # 代码质量审查
  code-review-security.md      # 安全性审查
  code-review-performance.md   # 性能审查
  code-review.md               # (可选) 父级编排 agent
```

### 关键设计决策

1. **独立使用**：4 个 agent 各自独立，通过 `Agent` 工具按名称调用。单个文件 / 模块可以只跑某一个维度的审查。
2. **模型选择**：命名、质量、性能使用 `sonnet`（平衡质量与速度）；安全使用 `opus`（需要更深层的推理发现边界 case）。
3. **结构化输出**：所有 agent 输出统一格式的报告，包含 Summary → Findings (按 CRITICAL / WARNING / INFO 分级) → Positive Observations。
4. **清单驱动**：每个 agent 的 system prompt 内嵌分类检查清单（checklist），确保审查覆盖全面且一致。
5. **父级编排（可选）**：`code-review.md` 负责一次调度 4 个子 agent 并合并报告，减少手动操作。

### 各 Agent 职责与核心检查项

#### 1. code-review-naming (命名风格) — model: sonnet

关注 Kotlin/JS 项目中的命名一致性、清晰度和约定遵循。

- PascalCase / camelCase 使用是否正确
- Signal/Memo/Effect 变量名是否语义清晰
- 测试命名是否使用反引号描述风格
- 中英混写术语是否一致
- JS 互操作变量命名是否合理 (`dynamic` 类型)

#### 2. code-review-quality (代码质量) — model: sonnet

关注代码结构、错误处理、Kotlin 惯用风格和响应式模式正确性。

- KDoc 覆盖度（公开 API 必须有 KDoc）
- `internal` 可见性使用是否恰当
- `try/finally` 状态恢复是否正确
- `Disposable` 模式是否幂等
- 防御性拷贝 `.toList()` 使用是否必要
- 是否存在重复代码、过长函数

#### 3. code-review-security (安全性) — model: opus

关注 JS 互操作安全、资源泄漏、上下文泄漏和状态一致性。

- `js(...)` 调用是否存在注入风险
- `dynamic` 类型边界是否安全
- `dispose()` 在所有路径下是否被调用
- `TrackingContext.currentObserver` 异常路径恢复
- `pendingEffects` 快照-清空模式是否正确
- 微任务异步执行是否存在竞态条件

#### 4. code-review-performance (性能) — model: sonnet

关注内存分配、集合操作、响应式图传播和 JS 互操作开销。

- 写操作前相等性检查是否到位
- Eager/Lazy memo 策略选择是否合理
- 热点路径是否有不必要的对象分配
- `resolvedPromise` 预创建单例是否被复用
- `dynamic` 属性访问是否在热点路径中
- batch 模式是否在合适的场景被使用

### Subagent 定义文件格式

每个 `.claude/agents/<name>.md` 文件使用 YAML frontmatter + Markdown body：

```markdown
---
name: code-review-naming
description: 审查 Kotlin/JS 项目的命名风格、一致性与清晰度
model: sonnet
tools: Read, Glob, Grep
---

# 命名风格审查 Agent

你是一个 Kotlin/JS 项目的命名风格审查专家...

## 审查清单

### Signal/Memo/Effect 命名
- [ ] ...
```

### 报告输出格式（所有 agent 统一）

```markdown
## Review Report: <agent-name>

### Summary
- Scope: <审查的文件/模块>
- Total findings: N
- CRITICAL: X | WARNING: Y | INFO: Z

### Findings

#### [CRITICAL] <category> — <title>
- **File**: `path/to/file.kt` (line N)
- **Issue**: ...
- **Suggestion**: ...

#### [WARNING] ...
#### [INFO] ...

### Positive Observations
- ...
```

### 可选父级编排 agent: code-review

- 读取目标文件
- 并行调度 4 个子 agent
- 合并报告、去重重叠发现
- 按严重程度排序，给出综合质量评分

## 各 Agent 详细检查清单

### code-review-naming（命名风格）

**Signal/Memo/Effect 命名**
- [ ] Signal 变量名是否以名词或名词短语命名，清晰表达其存储的值？
- [ ] Memo 派生信号变量名是否反映其计算含义（如 `double`, `greeting` 而非 `d`, `g`）？
- [ ] 属性委托的变量名是否与普通属性命名风格一致，不暴露实现细节？

**类/接口/类型命名**
- [ ] 公开 API 是否使用 PascalCase？
- [ ] 接口命名是否以能力为导向（如 `Disposable`，而非 `IDisposable`）？
- [ ] `internal` 类型命名是否与公开 API 风格一致？
- [ ] 类型参数命名是否遵循约定（单大写字母 `T`）？

**函数与方法命名**
- [ ] 函数名是否使用动词或动词短语（如 `dispose()`, `cleanupSources()`）？
- [ ] 读取型方法是否以 `get`/`read` 开头，写入型以 `set`/`write` 开头？
- [ ] 布尔返回型函数是否以 `is`/`has`/`should`/`can` 开头？

**测试命名**
- [ ] 测试函数是否使用反引号包裹的描述性名称？
- [ ] 测试名称是否遵循 given_when_then 或 should_when 风格？

**命名一致性**
- [ ] 同一概念在代码库中是否使用统一术语？
- [ ] 命名是否遵循 Kotlin 官方编码规范？

### code-review-quality（代码质量）

**KDoc 与文档**
- [ ] 所有公开 API 是否都有 KDoc？
- [ ] KDoc 是否包含 `@param`、`@return` 标签（必要时）？
- [ ] 示例代码是否准确且可编译？
- [ ] `internal` 实现是否有必要的行内注释说明设计意图？

**可见性与封装**
- [ ] 实现细节是否正确使用 `internal` 可见性？
- [ ] 公开 API 是否最小化？
- [ ] 构造函数可见性是否恰当？

**Kotlin 惯用风格**
- [ ] 是否有效利用属性委托、Lambda 表达式、扩展函数？
- [ ] `@Suppress` 注释是否有明确的理由说明？

**错误处理与异常安全**
- [ ] finally 块中状态恢复是否正确？
- [ ] `IllegalStateException` 等异常是否有清晰的消息文本？

**响应式模式正确性**
- [ ] `Disposable` 模式是否正确（幂等、清理所有引用）？
- [ ] 写操作前是否有相等性检查跳过传播？
- [ ] 集合迭代是否使用了防御性拷贝（`.toList()`）？
- [ ] batch 嵌套计数器是否正确处理？

### code-review-security（安全性）

**JS 互操作安全**
- [ ] `js(...)` 调用中是否包含不可信数据拼接？
- [ ] `dynamic` 类型的使用是否限于必需的 JS 互操作边界？
- [ ] 从 JS 侧接收的数据是否经过类型验证？
- [ ] 是否存在通过 `innerHTML` 注入 HTML 的风险？

**资源泄漏与生命周期**
- [ ] `Signal`、`Effect` 在所有路径下是否都有对应的 `dispose()`？
- [ ] `disposeNode()` 是否正确清理所有上游引用？
- [ ] `dispose()` 是否幂等？
- [ ] `pendingEffects` 中已释放的 effect 是否被清理？

**上下文泄漏**
- [ ] `TrackingContext.currentObserver` 是否在所有路径上被正确恢复（含异常路径）？
- [ ] `untrack {}` 中是否恢复了 `currentObserver`？
- [ ] batch 计数器是否存在整数溢出或负值风险？

**状态一致性与并发**
- [ ] `pendingEffects` 是否在 flush 前被正确快照？
- [ ] `scheduled` 布尔门控是否正确防止重复调度？
- [ ] 微任务异步执行中是否存在竞态条件？

### code-review-performance（性能）

**响应式图传播效率**
- [ ] 写操作前是否总是进行了相等性检查？
- [ ] Eager memo 使用场景是否合理？
- [ ] `markDirty` 递归是否对已脏节点进行了短路？

**集合操作优化**
- [ ] `.toList()` 防御性拷贝是否仅在必要时使用？
- [ ] 集合类型选择是否合理（`MutableSet` vs `MutableList`）？

**内存与对象分配**
- [ ] 热点路径中是否有不必要的临时对象分配？
- [ ] `resolvedPromise` 预创建单例是否被正确复用？
- [ ] Lambda 是否捕获了过多的上下文变量？

**调度器性能**
- [ ] 微任务调度三重门控是否有效？
- [ ] `flushEffects` 的快照-清空-执行模式是否能防止无限循环？

**JS 互操作性能**
- [ ] `dynamic` 属性访问是否在热点路径中？
- [ ] `js(...)` 调用频率是否经过优化？

**批处理效率**
- [ ] `batch {}` 是否在合适的场景中被使用？

## 实施步骤

1. 创建 `.claude/agents/` 目录
2. 按顺序创建 4 个 agent 定义文件（命名 → 质量 → 性能 → 安全）
3. 可选创建 `code-review.md` 父级编排 agent
4. 用 `core.kt` 或 `index.kt` 对每个 agent 做一次实际审阅验证，根据结果微调清单

## 验证方式

- 检查 `.claude/agents/` 下 4 个 `.md` 文件已创建
- 使用 `Agent` 工具分别调用每个 agent，审阅 `thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/core.kt`，确认返回结构化报告
