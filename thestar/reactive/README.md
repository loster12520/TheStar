# thestar-reactive

## 简介

`thestar-reactive` 是 TheStar 框架的响应式核心，基于 **Signal** 原语构建。它负责追踪数据依赖、在数据变化时自动传播更新，为上层模块（DOM、Style、UI）提供细粒度的响应式能力。

设计目标：

- **Kotlin 惯用风格**：利用 Kotlin 的属性委托、Lambda 表达式等语言特性，提供自然、简洁的 API
- **细粒度更新**：精确追踪每个依赖，值变化时仅通知真正使用它的代码，不做多余的重新计算
- **无魔法、无黑盒**：所有响应式行为都显式可控，没有隐藏的订阅或隐式转换
- **惰性求值**：派生值（Memo）仅在被读取且依赖已变更时才重新计算

## API 示例

### 基本信号（Signal）

```kotlin
import com.thestar.reactive.*

// 委托方式：像普通变量一样读写，推荐
var count by signal(0)
println(count)                   // 0
count = 5                        // 直接赋值，触发依赖更新
println(count)                   // 5

// 直接创建：通过 .value 手动读写
val name = signal("world")
println(name.value)              // "world"
name.value = "TheStar"
```

`by signal` 委托背后是 Kotlin 属性委托机制。`signal()` 返回的对象实现了 `ReadWriteProperty<Any?, T>`，`getValue` 中自动追踪当前观察者（依赖收集），`setValue` 中完成赋值并通知订阅者。对调用方来说，`count` 就是一个普通 `var`，完全不感知代理层的存在。

### 派生信号（Memo）

Memo 从一个或多个信号派生新值，依赖不变时读取的是缓存值，不会重复计算。

```kotlin
var count by signal(0)
val double by memo { count * 2 }
val greeting by memo { "Hello, ${name.value}!" }

println(double)                  // 0 —— 注意此时 count 是 Int，直接参与算术
count = 5
println(double)                  // 10 —— 依赖变了，下次读取时自动重算
```

**惰性求值（默认）**：Memo 自身脏了也不立即重算，等到下次被读取时才执行计算。适合计算开销大但不频繁读取的场景。

**活性求值（eager）**：传入 `eager = true` 后，Memo 内部创建一个 `effect`，一旦依赖变化立刻重算并把新值缓存好。后续读取时直接返回缓存值，零延迟。

```kotlin
// 默认惰性：只在被读取时计算
val lazyDouble by memo { count * 2 }

// 活性模式：依赖变化立刻重算，读取零开销
val eagerDouble by memo(eager = true) { count * 2 }
```

活性 Memo 适合"依赖频繁变化 + 下游频繁读取"的场景——虽然每次依赖变化都会触发计算，但读取时完全无等待。惰性 Memo 反之——依赖变化不产生开销，但每次脏读都要当场重算。

### 副作用（Effect）

```kotlin
// effect 在依赖变化时自动执行
effect {
    console.log("count = $count, double = $double")
}

count = 3   // 触发 effect，打印 "count = 3, double = 6"
count = 4   // 再次触发
```

### 取消订阅

```kotlin
val dispose = effect {
    // 这个 effect 返回一个 Disposable
}

// 不再需要时取消
dispose.dispose()
```

### 跳过追踪（Untrack）

在 `effect` 或 `memo` 内部，有些读取仅仅是为了快照当前值，不希望建立依赖。用 `untrack {}` 包裹后，其内部的信号读取不会触发追踪。

```kotlin
var name by signal("Alice")

effect {
    // 正常追踪：name 变化时 effect 触发
    renderName(name)

    // 旁路快照：仅打印日志，不追踪 name
    val snapshot = untrack { "${name}_${System.currentTimeMillis()}" }
    console.log(snapshot)
}
// effect 只绑定 renderName(name) 的读数；
// untrack 内部的 name 读取不计入依赖。
```

`untrack` 的常见场景：
- 配合 `memo` 使用，读取 memo 中不关心的字段，避免 memo 整体变更触发当前 effect
- 日志、埋点、调试输出等不应触发副作用的读取
- 需要读取信号的瞬时值但不希望后续变化干扰当前计算

### 批量更新（Batch）

```kotlin
// 在 batch 中多次修改仅触发一次依赖更新
batch {
    count = 100
    name.value = "batched"
}
// effect 只执行一次，拿到的是 batch 结束后的最终值
```

### 异步友好

```kotlin
// 信号可以在任意位置读写，effect 自动追踪调用栈
launch {
    var data by signal<String?>(null)
    // 异步获取数据后更新信号
    data = fetchFromApi()
}
// 任何依赖 data 的 memo / effect 都会自动更新
```

## 原理简述

### 核心模型：发布-订阅图

```
┌──────────┐    reads     ┌──────────┐    reads     ┌──────────┐
│  Signal  │ ◄─────────── │   Memo    │ ◄─────────── │  Effect   │
│  (源)    │ ────────────► │  (派生)   │ ────────────► │  (副作用) │
└──────────┘   notifies   └──────────┘   notifies   └──────────┘
      ▲                         ▲
      │         reads            │
      └─────────────────────────┘
```

- **Signal**（信号）：存储一个可随时间变化的值。内部维护一个订阅者列表，值变更时通知所有订阅者。
- **Memo**（派生）：用纯函数从其他响应式值派生新值。内部同时是订阅者（订阅上游）和被订阅者（被下游订阅）。默认惰性求值 + 缓存：只在依赖变脏且自己被读取时才重新计算。也支持 `eager = true` 活性模式，内部通过 `effect` 在依赖变化时立即重算。
- **Effect**（副作用）：纯订阅者。在依赖变化时执行回调。框架保证 effect 执行时捕获到的是最新的、一致的数据快照。

### 依赖自动追踪

核心技巧是 **全局执行上下文**。当 `memo {}` 或 `effect {}` 执行其 Lambda 时：

1. 框架将当前 Memo/Effect 设为"正在执行的观察者"
2. Lambda 内部对任何 Signal/Memo 的 `.value` 读取，会将该观察者注册为当前值的依赖
3. Lambda 执行完毕后，清除全局上下文

这套机制让你无需手动声明依赖——写什么就追踪什么，完全由**实际读取行为**决定依赖图。

`untrack {}` 的原理是在其执行期间临时暂停全局追踪上下文——内部的信号读取被屏蔽，不会把当前观察者注册为订阅者。上下文恢复后，后续读取正常追踪。

### 更新传播

```
Signal 写入 → 标记直接订阅者 (Memo/Effect) 为 "脏"
    → 脏 Memo 不立即重算，等待下次被读取
    → 脏 Effect 加入微任务队列，当前同步代码执行完后批量触发
```

- **写时标记**：Signal 写入只做标记，不立即执行任何重算
- **惰性 Memo（默认）**：在被读取时检查自身是否脏，是则先重算再返回——读时拉取
- **活性 Memo（eager）**：内部自动创建一个 `effect` 监听上游依赖，依赖变化后 `effect` 立即调用传入的回调重算并缓存新值——写时推送
- **异步调度 Effect**：Effect 在微任务（microtask）中批量执行，避免同一个 effect 在一次 batch 中被多次触发

### batch 的实现

`batch {}` 内部维护一个计数器。Signal 写入时检查是否在 batch 中：
- 在 batch 内：只标记脏，不调度 effect
- batch 结束后统一调度所有受影响的 effect

这样多次连续的信号修改只会产生一次 effect 执行，保证 UI 渲染的效率。

### 与 Solid.js 的对比

| 概念 | Solid.js | thestar-reactive |
|---|---|---|
| 可写信号 | `createSignal` | `signal()` |
| 派生值 | `createMemo` | `memo {}` |
| 副作用 | `createEffect` | `effect {}` |
| 批量更新 | `batch()` | `batch {}` |
| 跳过追踪 | `untrack()` | `untrack {}` |
| 属性委托读写 | — | `var x by signal(0); x = 1` |
| Kotlin 集成 | — | 属性委托、类型安全、可空类型 |
