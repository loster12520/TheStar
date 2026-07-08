# thestar-dom

## 简介

`thestar-dom` 是基于 `thestar-reactive` 的 UI 声明框架。它提供了一套 **Kotlin DSL** 来构建浏览器 DOM，底层采用**直接 DOM 操作**（无 Virtual DOM），将响应式信号的细粒度更新直接映射到 DOM 节点的精准变更。

设计目标：

- **直接 DOM 操作**：跳过 Virtual DOM 的 diff 开销，状态变化时只修改受影响的 DOM 节点
- **声明式 DSL**：用 Kotlin 类型安全的构建器表达 UI 结构，接近 HTML 的直觉但不失代码完整性
- **细粒度绑定**：每个响应式值被绑定到具体的 DOM 位置，变化时仅更新对应节点，不触发整个组件重渲染
- **零运行时模板编译**：DSL 即 Kotlin 代码，不需要模板编译器或额外的宏处理

## API 示例

### 静态元素

```kotlin
import com.thestar.dom.*
import com.thestar.dom.tags.*

div {
    h1 { text("Welcome to TheStar") }
    p { text("This is a static paragraph.") }
    ul {
        li { text("Item 1") }
        li { text("Item 2") }
        li { text("Item 3") }
    }
}
```

### 响应式绑定

```kotlin
val count by signal(0)

div {
    span { text { "Count: ${count()}" } }    // 自动更新
    button {
        text("+1")
        onClick { count(count() + 1) }
    }
}
// 点击按钮 → count 自增 → span 的文本自动更新
// 其他 DOM 节点（button 等）不受影响
```

### 属性绑定

```kotlin
val isDisabled by signal(false)

button {
    text("Submit")
    attr.disabled { isDisabled() }           // 响应式属性
    attr.className { if (isDisabled()) "btn-disabled" else "btn-active" }
    onClick { submit() }
}
```

### 条件渲染

```kotlin
val loggedIn by signal(false)

div {
    showIf({ loggedIn() }) {                 // 条件为 true 时才渲染
        span { text("Welcome back!") }
        button {
            text("Logout")
            onClick { loggedIn(false) }
        }
    }
    showIf({ !loggedIn() }) {
        button {
            text("Login")
            onClick { loggedIn(true) }
        }
    }
}
```

### 列表渲染

```kotlin
val items by signal(listOf("Alice", "Bob", "Charlie"))

ul {
    forEach({ items() }) { item, index ->     // 响应式列表
        li {
            text { "${index()}: ${item()}" }
        }
    }
}
// 未来可扩展为带 key 的 forEachIndexed，支持细粒度增删而非全量重建
```

### 事件处理

```kotlin
input {
    attr.type("text")
    attr.placeholder("Enter your name")
    onInput { event ->
        name.value = (event.target as HTMLInputElement).value
    }
}
```

### 挂载到页面

```kotlin
fun main() {
    // 将 DSL 渲染到 DOM 容器中
    render(document.body!!) {
        App()
    }
}
```

## 原理简述

### 整体流程

```
Kotlin DSL 代码
      │
      ▼
┌──────────────────┐
│  构建元素树       │  ← 运行时执行 DSL Lambda，递归创建 ElementBuilder
│  (ElementBuilder) │
└──────────────────┘
      │
      ▼
┌──────────────────┐
│  创建真实 DOM     │  ← 调用 document.createElement，建立节点树
│  节点并挂载       │
└──────────────────┘
      │
      ▼
┌──────────────────┐
│  绑定响应式更新   │  ← 对每个 text {}、attr.xxx {} 等动态绑定
│  (细粒度 Effect)  │     创建独立的 effect，仅更新其对应的 DOM 片段
└──────────────────┘
```

### 细粒度更新机制

与 React/Vue "状态变 → 组件重渲染 → VDOM diff → 更新 DOM" 的路径不同，`thestar-dom` 的更新路径是：

```
信号(Signal) 变化
    → 只通知直接绑定该信号的那个 effect
    → effect 中仅执行那一个 DOM 操作（如 textContent = "新值"）
    → 结束
```

举例：

```kotlin
div {
    h1 { text { "Hello, ${name()}!" } }       // effect A —— 绑定 name 信号
    p { text { "Count: ${count()}" } }         // effect B —— 绑定 count 信号
    span { text("Static footer") }             // 无 effect —— 纯静态文本
}
```

- `name` 变化 → 仅 effect A 触发 → 仅更新 `<h1>` 的 textContent
- `count` 变化 → 仅 effect B 触发 → 仅更新 `<p>` 的 textContent
- `<span>` 不受任何影响

### 元素构建器（ElementBuilder）

每个 HTML 元素对应一个 `ElementBuilder`：

```
div { ... }  →  DivBuilder
    ├── 持有对真实 HTMLElement 的引用
    ├── 管理子元素列表
    ├── 管理属性绑定
    └── 管理事件监听
```

`render()` 调用完成后，整个元素树就已创建并挂载到页面上。后续的更新全部通过 effect 回调中的直接 DOM 操作完成——**没有"重新渲染"这个概念**。

### 条件渲染（showIf）

`showIf` 内部创建一个 effect，监听条件信号：

- 条件为 `true` → 创建对应 DOM 节点并插入到正确位置
- 条件为 `false` → 从 DOM 树中移除节点（保留引用以便重新挂载）
- 可用 `<template>` 节点作为占位标记，记录 DOM 插入位置

### 列表渲染（forEach）

`forEach` 内部创建一个 effect 监听列表信号：

- **首版策略**：列表变化时全量重建该区域，简单可靠
- **后续优化**：引入 keyed 模式，对比新旧列表，仅对差异项增/删/移动 DOM 节点

### 与 Virtual DOM 框架的对比

| 维度 | Virtual DOM 框架 | thestar-dom |
|---|---|---|
| 更新粒度 | 组件级重渲染 → diff → 批量补丁 | 绑定级直接 DOM 操作 |
| 更新路径 | state → vdom tree → diff → patch | state → effect → `el.textContent = x` |
| 内存开销 | 额外维护 vdom 树 | 仅维护信号订阅关系 |
| 首屏渲染 | 创建 vdom → diff → 创建 DOM | 直接创建 DOM |
| 更新性能 | 组件越大 diff 越重 | 与组件大小无关，只与变化的绑定数量相关 |
