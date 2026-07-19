---
name: deep-review
description: 审查代码变更或指定模块，输出四维度综合审查报告
---

# /deep-review — 代码审查技能

你是 TheStar 项目的代码审查入口。你的职责是确定审查范围，然后委托给 `code-review` 编排 agent 进行四维度审查（命名、质量、安全、性能），最后将综合报告原封不动地输出给用户。

## 工作流程

### Step 1: 确定审查范围

根据用户输入解析要审查的文件列表：

**情况 A：无参数（默认）—— 审查所有未提交的变更**

按顺序执行以下命令：
1. `git diff --name-only HEAD` — 获取所有未提交变更（包括 staged 和 unstaged）的文件列表
2. 过滤，只保留 `.kt` 文件
3. 如果结果为空，提示用户"没有未提交的 Kotlin 代码变更。你可以使用 `/deep-review <路径或模块>` 指定审查范围。"并退出

如果未提交变更超过 20 个 `.kt` 文件，先列出文件清单，让用户确认是否继续。

**情况 B：用户指定了范围参数**

将用户描述作为审查范围，使用 Glob 定位目标文件：
- 如果参数是已存在的文件路径 → 直接使用
- 如果参数看起来是目录路径 → 用 Glob 扫描该目录下所有 `.kt` 文件（`<path>/**/*.kt`）
- 如果参数是模块名（如 `reactive`、`dom`、`style`、`ui`）→ 映射到对应目录 `thestar/<module>/src/**/*.kt`
- 如果参数是文件名（如 `core.kt`）→ 用 Glob 搜索匹配的文件
- 如果参数是 `--staged` → 使用 `git diff --cached --name-only` 获取暂存区变更

如果找不到任何匹配的文件，提示用户并给出最接近的建议。

### Step 2: 委托审查

使用 Agent 工具调用 `code-review` agent，将 Step 1 确定的文件列表作为输入传递。等待 agent 完成审查。

```
Agent(
  subagent_type: "code-review",
  description: "审查 <范围描述>",
  prompt: "请审查以下文件：<文件列表>"
)
```

**重要**：不要直接调用 4 个专项 agent（code-review-naming/quality/security/performance），只调用 code-review 编排 agent，由它负责并行调度和报告合并。

### Step 3: 输出报告

将 code-review agent 返回的综合报告**原封不动**地输出给用户。不要添加任何前缀、后缀、摘要或修改——用户需要看到完整的原始报告。

如果 code-review agent 返回了错误，将错误信息原样输出。

## 审查文件数量限制

- ≤ 20 个文件：直接审查
- > 20 个文件：先列出文件清单，等待用户确认后再审查
- = 0 个文件：提示无文件可审查，建议指定范围