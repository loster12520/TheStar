# TheStar Reactive 模块完整实现方案

## 1. 背景与目标

`thestar-reactive` 是 TheStar 框架的响应式核心层，基于 **Signal** 原语实现细粒度的数据依赖追踪与自动更新传播。API 设计参考 Solid.js，但以 Kotlin 惯用风格（属性委托、Lambda、类型安全）包装。

当前状态：`types.kt` 和 `index.kt` 中仅有 `TODO()` 存根，需要实现完整逻辑。

---

## 2. 文件结构

```
thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/
├── core.kt          // [新增] 内部响应式图核心：节点类型、全局追踪上下文
├── types.kt         // [重写] 公开类型：Disposable、Signal<T>、Effect
├── index.kt         // [重写] 顶层工厂函数：signal()、memo()、effect()、untrack()、batch()
├── scheduler.kt     // [新增] 微任务调度器（Effect 批量执行）
├── utils.kt         // [保留] 工具函数占位
```

> **原则**：`types.kt` + `index.kt` 是公开 API 边界，`core.kt` + `scheduler.kt` 是内部实现细节（`internal` 可见性）。

---

## 3. 整体架构图

```
                 ┌─────────────────────────────────────┐
                 │         TrackingContext (全局单例)     │
                 │  - currentObserver: ReactiveNode?    │
                 │  - batchDepth: Int                   │
                 │  - pendingEffects: MutableSet        │
                 └─────────────────────────────────────┘

┌───────────────────────┐    ┌───────────────────────┐    ┌───────────────────────┐
│     SignalNode<T>      │    │      MemoNode<T>       │    │      EffectNode        │
│  extends ReactiveNode  │    │  extends ReactiveNode  │    │  extends ReactiveNode  │
├───────────────────────┤    ├───────────────────────┤    ├───────────────────────┤
│  value: T              │    │  compute: () -> T      │    │  run: () -> Unit       │
│  observers: Set<>      │    │  sources: Set<>        │    │  sources: Set<>        │
│                        │    │  observers: Set<>      │    │                        │
│  write(v) → notify     │    │  observers: Set<>      │    │  execute() → schedule  │
│  read()  → track       │    │  read() → track+calc   │    │                        │
└───────────────────────┘    └───────────────────────┘    └───────────────────────┘
        ▲                            ▲
        │ wraps                       │ wraps
┌───────┴────────┐          ┌────────┴──────────┐
│  Signal<T>      │          │  Signal<T>         │   ← memo() 也返回 Signal
│  (public API)   │          │  (public API)      │
└────────────────┘          └───────────────────┘
```

**设计要点**：
- `SignalNode` 是数据源（可写），`MemoNode` 是派生节点（计算），`EffectNode` 是副作用节点
- `SignalNode` 和 `MemoNode` 都被 `Signal<T>`（公开类型）包装，对外提供统一的读写接口
- `memo()` 返回的 `Signal<T>` 的 `setValue` 为 no-op（派生值不可从外部写入）
- `EffectNode` 被 `Effect` 类包装，`Effect` 实现 `Disposable` 接口

---

## 4. 逐文件详细设计

---

### 4.1 `core.kt` — 内部响应式图核心

#### 4.1.1 `TrackingContext` 全局单例

```kotlin
internal object TrackingContext {
    var currentObserver: ReactiveNode? = null  // 当前正在执行的 memo/effect
    var batchDepth: Int = 0                     // batch() 嵌套层数
    val pendingEffects: MutableSet<EffectNode> = mutableSetOf()  // 待执行的 effect
    var scheduled: Boolean = false              // 是否已调度微任务
}
```

**职责**：
- `currentObserver`：当 memo/effect 的 Lambda 执行时，设为自身；Lambda 中对任何 signal 的 `.value` 读取会将此 observer 注册为订阅者
- `batchDepth`：信号写入时检查，> 0 则仅标记脏、不调度 effect
- `pendingEffects`：收集等待执行的 effect，在微任务中统一 flush

#### 4.1.2 `ReactiveNode` 抽象基类

```kotlin
internal abstract class ReactiveNode {
    val observers: MutableSet<ReactiveNode> = mutableSetOf()  // 谁依赖我
    var dirty: Boolean = false                                  // 值是否可能过期

    // 将自身标记为脏，并向下游传播
    open fun markDirty() {
        if (dirty) return          // 已标记，短路避免重复传播
        dirty = true
        for (observer in observers) {
            observer.markDirty()   // 递归标记下游
        }
    }
}
```

**关键设计**：
- `markDirty()` 带短路保护：已脏的节点不再递归，避免重复传播
- 下游传播是**急性的**（eager）：信号一写入，所有传递依赖立即被标记为脏

#### 4.1.3 `SignalNode<T>` — 数据源节点

```kotlin
internal class SignalNode<T>(initialValue: T) : ReactiveNode() {
    var value: T = initialValue

    fun read(): T {
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)          // 注册订阅者
            observer.addSource(this)         // 订阅者记录上游源
        }
        return value
    }

    fun write(newValue: T): Boolean {
        if (value == newValue) return false  // 值未变，跳过
        value = newValue
        for (observer in observers) {
            observer.markDirty()             // 向下游传播脏标记
        }
        scheduleFlush()           // 调度 effect 执行
        return true
    }
}
```

**细节**：
- `read()` 中 `observer.addSource(this)` 需要 `ReactiveNode` 有 `addSource` 方法，只有 `MemoNode` 和 `EffectNode` 才有 sources 集合。需要在基类提供空实现，子类覆写。
- `write()` 在检查 `batchDepth` 后再决定是否调度 effect——这个检查在 `scheduleFlush()` 内部进行
- 相等比较使用 Kotlin 的 `==`。JS 中基本类型按值比较，引用类型按引用比较——基础行为足够

#### 4.1.4 `MemoNode<T>` — 派生/计算节点

```kotlin
internal class MemoNode<T>(private val compute: () -> T, private val eager: Boolean = false) : ReactiveNode() {

    private val sources: MutableSet<ReactiveNode> = mutableSetOf()
    private var value: T? = null          // ? 是为了支持未初始化状态，实际使用时保证非空
    private var initialized: Boolean = false

    // --- 上游来源管理 ---
    override fun addSource(source: ReactiveNode) {
        sources.add(source)
    }

    private fun cleanupSources() {
        for (source in sources) {
            source.observers.remove(this)  // 从旧来源的订阅者列表移除自己
        }
        sources.clear()
    }

    // --- 脏标记（覆写父类，增加 eager 逻辑）---
    override fun markDirty() {
        if (dirty) return
        super.markDirty()  // 调用父类：设 dirty=true + 递归标记下游
        if (eager && initialized) {
            // eager 模式：立即重算并缓存新值，清除 dirty 标记
            recompute()
        }
    }

    // --- 读取值 ---
    fun read(): T {
        // 1. 依赖追踪
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)
            observer.addSource(this)
        }

        // 2. 如果脏或未初始化，重新计算
        if (dirty || !initialized) {
            recompute()
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    // --- 重新计算 ---
    private fun recompute() {
        cleanupSources()  // 先解除旧的依赖关系

        val prev = TrackingContext.currentObserver
        TrackingContext.currentObserver = this
        try {
            val newValue = compute()
            value = newValue
            dirty = false
            initialized = true
        } finally {
            TrackingContext.currentObserver = prev
        }
    }

    // --- 初始化 eager 模式（在工厂函数中调用）---
    internal fun initEager() {
        // 立即执行首次计算，建立对上游信号的依赖关系
        // 之后上游信号变化 → markDirty() → 因为 eager && initialized → 立即 recompute()
        recompute()
    }

    // --- 清理资源 ---
    internal fun disposeNode() {
        cleanupSources()
        observers.clear()
    }
}
```

**惰性求值流程**：
1. 首次 `read()` → `!initialized` → `recompute()`：设 `currentObserver = this` → 执行 `compute()` → 期间读取的信号将 this 注册为订阅者
2. 上游信号变化 → `this.markDirty()` → `dirty = true`（不立即计算）
3. 下次 `read()` → `dirty == true` → `recompute()`：清理旧 sources、重新执行 compute、建立新依赖

**活性求值流程（关键区别：无需内部 EffectNode！）**：
1. `initEager()` 调用 `recompute()` → 建立对上游信号的依赖关系
2. 上游信号变化 → `this.markDirty()` → `super.markDirty()` 设 dirty=true 并传播下游 → 因为 `eager && initialized` → 立即调用 `recompute()` 刷新缓存
3. 后续 `read()` 时 `dirty` 恒为 false → 直接返回缓存值，零等待

**设计要点**：
- 活性模式**不需要**内部 `EffectNode`。`MemoNode` 已经在依赖图的中间层：上游（信号）已经通过 `observers` 集合指向它，`markDirty()` 覆写足以在收到通知时立即重算
- `recompute()` 执行时会调用 `cleanupSources()` 清除旧依赖，然后执行 `compute()` 重建新依赖——这保证了依赖关系始终是最新的（例如条件分支导致依赖变化时）

#### 4.1.5 `EffectNode` — 副作用节点

```kotlin
internal class EffectNode(private val fn: () -> Unit) : ReactiveNode() {

    private val sources: MutableSet<ReactiveNode> = mutableSetOf()
    private var disposed: Boolean = false

    override fun addSource(source: ReactiveNode) {
        sources.add(source)
    }

    private fun cleanupSources() {
        for (source in sources) {
            source.observers.remove(this)
        }
        sources.clear()
    }

    // 覆写 markDirty：Effect 是叶子节点，直接将自身加入待执行队列
    override fun markDirty() {
        if (disposed) return
        TrackingContext.pendingEffects.add(this)   // 加入队列
        scheduleFlush()             // 确保微任务已调度
    }

    // 执行副作用
    fun execute() {
        if (disposed) return
        cleanupSources()

        val prev = TrackingContext.currentObserver
        TrackingContext.currentObserver = this
        try {
            fn()
        } finally {
            TrackingContext.currentObserver = prev
        }
    }

    // 取消订阅
    fun disposeNode() {
        if (disposed) return
        disposed = true
        cleanupSources()
        TrackingContext.pendingEffects.remove(this)
    }
}
```

**与 MemoNode 的区别**：
- `markDirty()` 不递归传播（effect 是图的叶子节点，没有下游）
- 进入 `pendingEffects` 集合，等待微任务批量执行
- `execute()` 由调度器在微任务中调用

#### 4.1.6 `addSource` 的多态处理

`ReactiveNode` 基类中，`SignalNode` 不需要 `sources`（它是纯源）。为保持接口统一：

```kotlin
internal abstract class ReactiveNode {
    val observers: MutableSet<ReactiveNode> = mutableSetOf()
    var dirty: Boolean = false

    open fun addSource(source: ReactiveNode) {}  // SignalNode 用空实现
    open fun markDirty() { ... }
}
```

`MemoNode` 和 `EffectNode` 覆写 `addSource` 实现真实逻辑。

---

### 4.2 `scheduler.kt` — 微任务调度器

```kotlin
package com.thestar.reactive

/**
 * 微任务调度器：将 pendingEffects 中的所有 effect 在单个微任务中批量执行。
 * 利用 Promise.resolve().then() 实现微任务调度，兼容浏览器和 Node.js。
 */

// 预创建 resolved Promise，避免每次调度都创建新 Promise 对象
private val resolvedPromise: dynamic = js("Promise.resolve()")

/**
 * 调度一次 effect flush（如果尚未调度）。
 * 在每次 Signal 写入后调用，受 batchDepth 门控。
 */
internal fun scheduleFlush() {
    if (TrackingContext.batchDepth > 0) return     // 在 batch 中，暂不调度
    if (TrackingContext.scheduled) return           // 已调度，避免重复
    if (TrackingContext.pendingEffects.isEmpty()) return

    TrackingContext.scheduled = true
    // 使用预创建的 resolved Promise 调度微任务
    // fn 是 Kotlin lambda，Kotlin/JS 编译器会自动将其转换为 JS 函数引用
    resolvedPromise.then(::flushEffects)
}

/**
 * 刷新所有待执行的 effect（在微任务中调用）。
 */
private fun flushEffects() {
    TrackingContext.scheduled = false

    // 快照当前队列并清空（执行期间可能有新 effect 加入）
    val effects = TrackingContext.pendingEffects.toList()
    TrackingContext.pendingEffects.clear()

    for (effect in effects) {
        effect.execute()
    }
}
```

**设计要点**：
- `resolvedPromise.then(::flushEffects)` 使用 Kotlin 函数引用，编译器自动转换为 JS 函数——无需 `@JsName` 注解
- `scheduleFlush()` 有**三重门控**：batch 中、已调度、队列为空——三种情况都跳过
- `flushEffects()` 先快照再清空队列，防止 effect 执行期间又有新 effect 加入导致死循环
- 使用 `Promise.resolve().then()` 而非 `setTimeout(0)`——微任务在浏览器渲染前执行，适合 DOM 更新场景

---

### 4.3 `types.kt` — 公开类型

#### 4.3.1 `Disposable` 接口

```kotlin
interface Disposable {
    fun dispose()
}
```

#### 4.3.2 `Signal<T>` 类

```kotlin
class Signal<T : Any> internal constructor(
    private val node: ReactiveNode  // 实际是 SignalNode<T> 或 MemoNode<T>
) : Disposable {

    // --- 直接读写 ---
    var value: T
        get() {
            // 根据 node 类型分派读取
            return when (node) {
                is SignalNode<*> -> (node as SignalNode<T>).read()
                is MemoNode<*>   -> (node as MemoNode<T>).read()
                else -> throw IllegalStateException("Unknown node type")
            }
        }
        set(newValue) {
            when (node) {
                is SignalNode<*> -> (node as SignalNode<T>).write(newValue)
                is MemoNode<*>   -> { /* Memo 不可从外部写入，no-op */ }
                else -> throw IllegalStateException("Unknown node type")
            }
        }

    // --- 属性委托支持 ---
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    // --- 取消订阅（释放整个信号链）---
    override fun dispose() {
        when (node) {
            is SignalNode<*> -> {
                for (observer in node.observers.toList()) {
                    when (observer) {
                        is MemoNode<*> -> observer.disposeNode()
                        is EffectNode -> observer.disposeNode()
                    }
                }
                node.observers.clear()
            }
            is MemoNode<*> -> {
                for (observer in node.observers.toList()) {
                    when (observer) {
                        is MemoNode<*> -> observer.disposeNode()
                        is EffectNode -> observer.disposeNode()
                    }
                }
                node.observers.clear()
                node.disposeNode()  // 清理上游依赖
            }
        }
    }
}
```

**关注点**：
- `Signal` 实现了 `Disposable`：可以释放整个信号链
- 内部通过 `when` 分派到 `SignalNode` 或 `MemoNode` 的方法，避免子类爆炸
- `MemoNode` 的 `set value` 是 no-op（静默忽略），这是故意设计——未来可改为抛异常
- `getValue`/`setValue` 让 `var x by signal(0)` 和 `val y by memo { ... }` 正常工作

#### 4.3.3 `Effect` 类

```kotlin
class Effect internal constructor(private val node: EffectNode) : Disposable {
    override fun dispose() {
        node.disposeNode()
    }
}
```

---

### 4.4 `index.kt` — 顶层工厂函数

#### 4.4.1 `signal()`

```kotlin
fun <T : Any> signal(data: T): Signal<T> {
    val node = SignalNode(data)
    return Signal(node)
}
```

**6 行，逻辑简单**——所有复杂性在 `SignalNode` 中。

#### 4.4.2 `memo()`

```kotlin
fun <T : Any> memo(eager: Boolean = false, function: () -> T): Signal<T> {
    val node = MemoNode(function, eager)
    if (eager) {
        node.initEager()
    }
    return Signal(node)
}
```

**注意参数顺序**：`eager` 有默认值，`function` 是尾随 Lambda，因此以下写法都合法：
```kotlin
val a by memo { count * 2 }                // 惰性（默认）
val b by memo(eager = true) { count * 2 }  // 活性
```

#### 4.4.3 `effect()`

```kotlin
fun effect(function: () -> Unit): Effect {
    val node = EffectNode(function)
    // 首次执行：建立依赖关系
    node.execute()
    return Effect(node)
}
```

**首次执行是必须的**：只有执行后才知道依赖哪些信号，后续信号变化才能触发 effect。

#### 4.4.4 `batch()`

```kotlin
fun batch(function: () -> Unit) {
    TrackingContext.batchDepth++
    try {
        function()
    } finally {
        TrackingContext.batchDepth--
        if (TrackingContext.batchDepth == 0) {
            // batch 结束，调度所有积压的 effect
            scheduleFlush()
        }
    }
}
```

**嵌套 batch 支持**：计数器机制，只有最外层 batch 结束时才 flush。

#### 4.4.5 `untrack()`

```kotlin
fun <T : Any> untrack(function: () -> T): T {
    val prev = TrackingContext.currentObserver
    TrackingContext.currentObserver = null  // 暂停追踪
    try {
        return function()
    } finally {
        TrackingContext.currentObserver = prev  // 恢复
    }
}
```

**核心原理**：将 `currentObserver` 临时设为 `null`，Lambda 内的 signal 读取就无法注册当前 observer。

---

### 4.5 `utils.kt` — 工具函数

保留现有内容（仅 `package` 声明），后续添加工具函数时在此文件中扩展。

---

## 5. 核心数据流详解

### 5.1 Signal 读取（依赖追踪）

```
用户代码: println(signal.value)  或  memo { signal.value }

    ┌─ Signal.value get()
    │    └─ SignalNode.read()
    │         ├─ observer = TrackingContext.currentObserver
    │         ├─ if (observer != null):
    │         │    ├─ this.observers.add(observer)    ← 注册：observer 依赖我
    │         │    └─ observer.addSource(this)        ← 反向：我也记录 observer 为下游
    │         └─ return this.value
    │
    └─ 如果 currentObserver 是某个 effect/memo，则依赖关系已建立
       如果 currentObserver 是 null（普通读取），则无副作用
```

### 5.2 Signal 写入（更新传播）

```
用户代码: signal.value = newValue  或  count = 5 (委托)

    Signal.value set()
      └─ SignalNode.write(newValue)
           ├─ if (value == newValue) return false   ← 值未变，短路
           ├─ value = newValue
           ├─ for (observer in observers):
           │    └─ observer.markDirty()
           │         ├─ if (dirty) return           ← 已脏，短路
           │         ├─ dirty = true
           │         └─ for (downstream in observers):
           │              └─ downstream.markDirty() ← 递归传播
           │                   ├─ 如果 downstream 是 MemoNode → 继续递归其下游
           │                   └─ 如果 downstream 是 EffectNode → 加入 pendingEffects
           └─ scheduleFlush()
                ├─ if (batchDepth > 0) return        ← 在 batch 中
                ├─ if (scheduled) return              ← 已调度
                └─ Promise.resolve().then(flushEffects)
```

### 5.3 Memo 读取（惰性求值）

```
用户代码: println(memo.value)

    Signal.value get()
      └─ MemoNode.read()
           ├─ observer = TrackingContext.currentObserver
           ├─ if (observer != null): 注册依赖关系（同上）
           ├─ if (dirty || !initialized):
           │    └─ recompute()
           │         ├─ cleanupSources()              ← 断开旧依赖
           │         ├─ prev = currentObserver
           │         ├─ currentObserver = this        ← 设自己为当前观察者
           │         ├─ newValue = compute()          ← 执行 Lambda，期间读取的信号会注册 this 为订阅者
           │         ├─ value = newValue
           │         ├─ dirty = false
           │         ├─ initialized = true
           │         └─ currentObserver = prev        ← 恢复
           └─ return value
```

### 5.4 Effect 执行流程

```
微任务触发 flushEffects()
  └─ for (effect in pendingEffects):
       └─ effect.execute()
            ├─ cleanupSources()                       ← 断开旧依赖
            ├─ prev = currentObserver
            ├─ currentObserver = this
            ├─ fn()                                   ← 执行用户回调
            │    └─ 期间读取的任何 signal 会将此 effect 注册为新订阅者
            └─ currentObserver = prev
```

### 5.5 batch 流程

```
batch {
    count = 100      ← SignalNode.write() → markDirty → scheduleFlush()
    │                   └─ batchDepth > 0，return（不调度）
    name.value = "X" ← SignalNode.write() → markDirty → scheduleFlush()
                        └─ batchDepth > 0，return（不调度）
}  ← batchDepth 降为 0 → scheduleFlush()
                            └─ 一个微任务，所有 effect 批量执行
```

### 5.6 untrack 流程

```
effect {
    renderName(name)           ← currentObserver = thisEffect → name 被追踪
    val snap = untrack {
        "${name}_ts"           ← currentObserver = null → name 读取不被追踪
    }
}                              ← currentObserver 恢复为 thisEffect
```

---

## 6. 边界情况处理

| 场景 | 处理方式 |
|---|---|
| 写入相同值 | `SignalNode.write()` 中 `==` 比较，相等则 `return false`，不传播 |
| 嵌套 `batch()` | 计数器机制，只有最外层结束时 flush |
| `batch()` 中异常 | `try/finally` 确保 `batchDepth--` 一定执行，防止死锁 |
| `untrack` 中异常 | `try/finally` 确保 `currentObserver` 一定能恢复 |
| 在追踪上下文外读 signal | `currentObserver == null`，跳过注册，直接返回值 |
| Effect 内嵌套 Effect | 内层 effect 执行时设自己为 `currentObserver`，外层被遮蔽；内层执行完后恢复外层。正确行为 |
| Memo 的 Lambda 不读任何信号 | `sources` 为空，`dirty` 永远不会被设为 true，首次计算后永远返回缓存值 |
| 释放 Effect | `dispose()` 清理所有上游的 observers 引用，从 `pendingEffects` 移除 |
| 释放 Signal | 级联 dispose 所有依赖它的 Memo 和 Effect |
| 循环依赖 | 不做运行时检测。由开发者负责避免。如果产生，会导致无限递归（栈溢出），这是和 Solid.js 一致的行为 |
| 泛型约束 `T : Any` | 不支持 `Signal<String?>`（可空类型）。这是 README 和现有存根的约定，如需可空信号用包装类或特殊值 |

---

## 7. 实现顺序

按依赖关系依次实现：

1. **`core.kt`** — `TrackingContext`、`ReactiveNode`、`SignalNode`、`MemoNode`、`EffectNode`
2. **`scheduler.kt`** — `scheduleFlush()`、`flushEffects()`
3. **`types.kt`** — `Disposable`、`Signal<T>`、`Effect`
4. **`index.kt`** — `signal()`、`memo()`、`effect()`、`batch()`、`untrack()`
5. **`SignalTest.kt`** — 更新测试用例

---

## 8. 测试策略

更新 `SignalTest.kt`，取消注释并改写为与新 API 一致的测试：

### 8.1 Signal 基础测试

```kotlin
@Test fun `signal stores initial value`() {
    val s = signal(42)
    assertEquals(42, s.value)
}

@Test fun `signal updates value`() {
    val s = signal(1)
    s.value = 2
    assertEquals(2, s.value)
}

@Test fun `delegated signal read and write`() {
    var count by signal(0)
    assertEquals(0, count)
    count = 5
    assertEquals(5, count)
}

@Test fun `signal no-op when same value`() {
    val s = signal(10)
    var callCount = 0
    effect { s.value; callCount++ }  // 初始执行一次
    val before = callCount
    s.value = 10  // 相同值
    assertEquals(before, callCount)  // effect 不触发
}
```

### 8.2 Memo 测试

```kotlin
@Test fun `memo computes derived value`() {
    var count by signal(2)
    val double by memo { count * 2 }
    assertEquals(4, double)
    count = 5
    assertEquals(10, double)
}

@Test fun `memo is lazy - does not recompute until read`() {
    var count by signal(0)
    var computeCount = 0
    val derived by memo { computeCount++; count * 2 }
    assertEquals(1, computeCount)  // 首次读取时计算
    count = 1  // 不改读，computeCount 不变
    assertEquals(1, computeCount)
    val v = derived  // 现在读取
    assertEquals(2, computeCount)
    assertEquals(2, v)
}

@Test fun `memo eager recomputes immediately`() {
    var count by signal(1)
    var computeCount = 0
    val derived by memo(eager = true) { computeCount++; count * 2 }
    assertEquals(1, computeCount)
    count = 2  // eager: 立即重算
    assertEquals(2, computeCount)
    assertEquals(4, derived)  // 读取零延迟
}
```

### 8.3 Effect 测试

```kotlin
@Test fun `effect runs on dependency change`() {
    var count by signal(0)
    var result = 0
    effect { result = count * 2 }
    assertEquals(0, result)  // 初始执行
    count = 3
    // effect 在微任务中执行，测试需要处理异步
    // 或在同步测试中手动触发 flush
}

@Test fun `effect dispose stops notifications`() {
    var count by signal(0)
    var callCount = 0
    val e = effect { count; callCount++ }
    val afterInit = callCount
    e.dispose()
    count = 1
    assertEquals(afterInit, callCount)
}
```

### 8.4 Batch 测试

```kotlin
@Test fun `batch groups multiple writes`() {
    var a by signal(0)
    var b by signal(0)
    var effectRuns = 0
    effect { a; b; effectRuns++ }
    val afterInit = effectRuns
    batch {
        a = 1
        b = 2
    }
    // batch 结束后 effect 只执行一次
    assertEquals(afterInit + 1, effectRuns)
}
```

### 8.5 Untrack 测试

```kotlin
@Test fun `untrack prevents dependency tracking`() {
    var a by signal(0)
    var tracked = 0
    var untracked = 0
    effect {
        tracked = a          // 追踪
        untracked = untrack { a }  // 不追踪
    }
    assertEquals(0, tracked)
    assertEquals(0, untracked)
    a = 5
    // tracked 更新了（effect 重新执行），untracked 也拿到了新值
    assertEquals(5, tracked)
    assertEquals(5, untracked)
}

@Test fun `untrack read does not trigger effect`() {
    var a by signal(0)
    var effectRuns = 0
    effect {
        untrack { a }   // 只在不追踪的上下文中读取
        effectRuns++
    }
    val afterInit = effectRuns
    a = 1  // 不应触发 effect（因为 a 的读取在 untrack 中）
    assertEquals(afterInit, effectRuns)
}
```

### 8.6 异步测试处理

Kotlin/JS 的 Mocha 支持异步测试。对于依赖微任务的 effect 测试：

```kotlin
@Test fun `effect runs asynchronously after signal change`() {
    var count by signal(0)
    var result = 0
    effect { result = count }
    
    count = 5
    // effect 尚未执行（在微任务队列中）
    // 使用 Promise 等待微任务
    js("return Promise.resolve().then(function() { console.assert(result === 5, 'effect should run'); })")
}
```

---

## 9. 验证方式

完成实现后，按以下步骤验证：

```bash
# 1. 编译 reactive 模块
./gradlew :thestar:reactive:compileKotlinJs

# 2. 运行全部测试
./gradlew :thestar:reactive:jsTest

# 3. 运行特定测试类
./gradlew :thestar:reactive:testClass -PtestClass=SignalTest

# 4. 确认 front 模块也能编译（验证 API 兼容性）
./gradlew :front:compileKotlinJs
```
