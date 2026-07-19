# Reactive 模块测试执行结果分析

> **执行命令**: `./gradlew :thestar:reactive:jsNodeTest`
> **执行时间**: 2026-07-19
> **总用例数**: 150
> **通过**: 117
> **失败**: 33
> **通过率**: 78%

---

## 一、失败用例汇总

| # | 文件 | 测试用例 | 错误类型 | 实际值 vs 期望值 | 归类 |
|---|------|----------|----------|------------------|------|
| 1 | AsyncTest | `effect executes asynchronously after write` | AssertionError | actual=0, expected=42 | 调度器不可测 |
| 2 | AsyncTest | `multiple writes before microtask are merged into single effect run` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 3 | AsyncTest | `effect does not re-execute in same microtask after initial flush` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 4 | AsyncTest | `batch flush happens after outermost batch ends` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 5 | AsyncTest | `batch effect execution order is correct` | AssertionError | actual=2, expected=4 | 调度器不可测 |
| 6 | AsyncTest | `two independent batches produce separate effect rounds` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 7 | AsyncTest | `batch inside effect execution context` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 8 | AsyncTest | `dispose effect during microtask flush does not execute it` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 9 | AsyncTest | `signal write from coroutine triggers effect` | AssertionError | actual=0, expected=99 | 调度器不可测 |
| 10 | AsyncTest | `rapid signal writes do not lose updates` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 11 | AsyncTest | `alternating writes and reads do not corrupt state` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 12 | BatchTest | `batch groups multiple writes into single effect run` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 13 | BatchTest | `batch with multiple writes to same signal` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 14 | BatchTest | `nested batch defers flush to outermost batch end` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 15 | BatchTest | `triple nested batch works correctly` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 16 | BatchTest | `successive batches work independently` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 17 | EffectTest | `effect triggers after signal write` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 18 | EffectTest | `effect triggers once for multiple writes to same signal` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 19 | EffectTest | `effect triggers independently for different signals` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 20 | EffectTest | `multiple effects on same signal` | AssertionError | actual=0, expected=3 | 调度器不可测 |
| 21 | EffectTest | `multiple effects fire in batch after signal change` | AssertionError | assertTrue failed | 调度器不可测 |
| 22 | IntegrationTest | `TODO app - add and complete tasks` | AssertionError | actual=0, expected=3 | 调度器不可测 |
| 23 | IntegrationTest | `TODO app - batch add multiple todos` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 24 | IntegrationTest | `cascading dropdown with batch update` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 25 | IntegrationTest | `dynamic subscribe and unsubscribe` | AssertionError | actual=1, expected=2 | 调度器不可测 |
| 26 | IntegrationTest | `error recovery - system works after memo exception` | AssertionError | actual=2, expected=-1 | 调度器不可测 |
| 27 | IntegrationTest | `full lifecycle - create update batch dispose` | AssertionError | actual=0, expected=25 | 调度器不可测 |
| 28 | IntegrationTest | `cross dependencies between effects and signals` | AssertionError | actual=11, expected=12 | 调度器不可测 |
| 29 | UntrackTest | `untrack mixed with tracked reads in same effect` | AssertionError | actual=0, expected=1 | 调度器不可测 |
| 30 | EffectTest | `effect exception does not prevent other effects` | RuntimeException | effect error 未被捕获 | **代码 Bug** |
| 31 | EffectTest | `effect dispose during execution does not cause issues` | UninitializedPropertyAccessException | lateinit self not initialized | **测试 Bug** |
| 32 | EffectTest | `effect dynamically changes dependencies across executions` | AssertionError | actual=1, expected=10 | 调度器不可测 |
| 33 | IntegrationTest | `mixed lazy and eager memos in complex graph` | AssertionError | actual=12, expected=13 | **测试 Bug** |

---

## 二、失败根因分类分析

### 类别 A：调度器不可测试 —— 28 个失败（85%）

**根本原因**：`scheduler.kt` 中硬编码了 `CoroutineScope(Dispatchers.Main)`，而测试使用 `kotlinx-coroutines-test` 的 `runTest` 时，`Dispatchers.Main` 不会被替换为 `StandardTestDispatcher`（JS 平台无 `Dispatchers.setMain` 支持）。因此 effect 的异步 flush 永远无法在 `runTest` 控制的时间内执行。

**源码位置**：`src/jsMain/kotlin/com/thestar/reactive/scheduler.kt:8`
```kotlin
private val scope = CoroutineScope(Dispatchers.Main)
```

**影响链路**：
```
Signal.write() → scheduleFlush()
                   → scope.launch { yield(); flushEffects() }
                       ↑ 运行在真实 Dispatchers.Main 上
                       ↑ runTest 的 delay() 不会推进真实 Main 调度器

batch() 结束时调用 scheduleFlush()
                   → 同上，永远不执行
```

**受影响的断言模式**：
- 写入 signal 后 `delay(1)` 再检查 effect 已执行 → **失败**（effect 从未执行）
- batch 后 `delay(1)` 再检查 effect 只执行了一次 → **失败**（effect 从未执行）
- 检查 effect 计数器从 1 变成 2 → **失败**（始终为 1）

**结论**：这是 **代码设计缺陷**，不是测试写错。`scheduler.kt` 的 `scope` 应当可注入，以便测试环境替换为测试调度器。需要重构 `scheduleFlush` 使其接受可配置的 `CoroutineScope`。

**修复建议**（不修改代码，仅给方案）：
1. 将 `scope` 提取为 `internal var`，允许测试注入 `TestScope`
2. 或者在 `scheduleFlush` 中检查是否有 override 的 scope
3. 或者引入 `internal fun setSchedulerScope(scope: CoroutineScope)` 供测试配置

---

### 类别 B：代码 Bug —— 1 个失败（3%）

**失败用例**：`EffectTest.`effect exception does not prevent other effects``

**错误信息**：`RuntimeException: effect error` — 异常未被捕获，直接从 `effect()` 调用中抛出。

**源码位置**：`src/jsMain/kotlin/com/thestar/reactive/index.kt:13`
```kotlin
fun effect(callback: () -> Unit): Effect =
    Effect(EffectNode(callback).also { it.execute() })
```

**分析**：
- `effect()` 在创建 `Effect` 时同步调用 `EffectNode.execute()`
- `execute()` 内部调用 `cleanupSourcesSafety` → `changeCurrentObserver(callback)`
- 如果 callback 抛出异常，会穿透 `cleanupSourcesSafety`（该方法只做依赖回滚，不吞异常），继续向上穿透 `execute()`，最终从 `effect()` 抛出
- 而 `flushEffects()` 中的 try-catch（scheduler.kt:34-38）只保护**调度执行**的 effect，不保护**初始执行**的 effect

**为什么这是 Bug**：
`effect()` 的文档和语义承诺创建一个副作用并返回 Effect 句柄。初始执行时的异常导致 `effect()` 抛出意味着：
1. 调用方连 `Effect` 句柄都拿不到，无法 dispose
2. `EffectNode` 对象已被创建但引用丢失，可能造成依赖泄漏
3. 与 Solid.js 等参考实现的语义不一致（它们通常吞掉初始异常或将其推迟）

**结论**：这是 **代码 Bug**。`effect()` 应当在初始执行时也包裹 try-catch（类似 `flushEffects()` 的做法），或者至少保证返回 Effect 句柄。

**修复建议**：
```kotlin
fun effect(callback: () -> Unit): Effect {
    val node = EffectNode(callback)
    try {
        node.execute()
    } catch (e: Throwable) {
        logger.error(e) { "Error during effect initialization" }
    }
    return Effect(node)
}
```

---

### 类别 C：测试 Bug —— 2 个失败（6%）

#### C1：`EffectTest.`effect dispose during execution does not cause issues``

**错误信息**：`UninitializedPropertyAccessException: lateinit property self has not been initialized`

**原因**：测试代码逻辑错误。

```kotlin
lateinit var self: Effect
var execCount = 0
self = effect {
    execCount++
    if (execCount == 1) {
        self.dispose() // ← 此时 self 尚未初始化！因为 effect() 还在执行中
    }
    count.value
}
```

`effect()` 内部调用 `EffectNode.execute()` → 执行 callback → callback 中访问 `self` → 但 `self = effect(...)` 的赋值要等 `effect()` **返回**后才发生。所以 callback 中 `self` 是未初始化的 `lateinit`。

**结论**：**测试写错了**。需要用其他方式持有 Effect 引用（例如通过 `var` + `also` 或使用 capture list）。

**修复方案**（测试侧）：
```kotlin
var selfRef: Effect? = null
val self = effect {
    execCount++
    if (execCount == 1) {
        selfRef?.dispose() // 通过可空引用访问
    }
    count.value
}.also { selfRef = it }
```

#### C2：`IntegrationTest.`mixed lazy and eager memos in complex graph``

**错误信息**：`AssertionError: Expected <13>, actual <12>`

**原因**：测试期望值计算错误。测试中的注释自己也写了 `(1+1) + (1*10) = 12`，但断言写了 `assertEquals(13, lazy2.value)`。

```kotlin
// source = signal(1)
// lazy1 = memo { source.value + 1 }         → 1+1 = 2
// eager1 = memo(eager=true) { source.value * 10 } → 1*10 = 10
// lazy2 = memo { lazy1.value + eager1.value }     → 2+10 = 12  ← 正确值
// 但测试写的是：
assertEquals(13, lazy2.value) // Bug：应该是 12
```

**结论**：**测试期望值写错了**，应为 `12`。

---

### 类别 D：同一 Bug 的额外影响 —— 2 个失败（6%）

#### D1：`EffectTest.`effect dynamically changes dependencies across executions``

**表面错误**：`AssertionError: Expected <10>, actual <1>`

**根因**：仍然是调度器不可测问题。修改 `toggle.value = false` 后 `delay(1)` 不能触发 effect flush，因此 `result` 保持初始值 1。但此测试设计得非常复杂（多个嵌套 effect、两次 delay），所以单独列出。

#### D2：`UntrackTest.`untrack mixed with tracked reads in same effect``

**表面错误**：`AssertionError: Expected <1>, actual <0>`

**根因**：仍然是调度器不可测问题。修改 `a.value = 1` 后 `delay(1)` 不能触发 effect flush，所以 `tracked` 保持 0。

---

## 三、总结

| 类别 | 数量 | 占比 | 说明 |
|------|------|------|------|
| **代码设计缺陷**（调度器不可注入） | 28 | 85% | `scheduler.kt` 硬编码 `Dispatchers.Main`，测试无法控制 |
| **代码 Bug**（effect 初始异常未捕获） | 1 | 3% | `effect()` 未 try-catch 初始执行异常 |
| **测试 Bug**（逻辑错误） | 2 | 6% | lateinit 时序错误 + 期望值计算错误 |
| **受调度器 Bug 间接影响**（复杂测试） | 2 | 6% | 表现为其他断言失败，根因仍是调度器问题 |

### 核心结论

1. **85% 的失败源于同一个代码设计问题**：`scheduler.kt` 的 `CoroutineScope(Dispatchers.Main)` 硬编码导致所有依赖 `delay()` 等待 effect flush 的测试都无法工作。这是本次测试编写过程中暴露出的最重要的可测试性问题。

2. **1 个真正的代码 Bug**：`effect()` 初始执行时的异常未被捕获，会直接抛给调用方，导致 Effect 句柄丢失。

3. **2 个测试 Bug**：测试用例中的逻辑错误（lateinit 时序、数学错误），与代码无关。

4. **117 个通过**的用例覆盖了所有同步行为：Signal/Memo 的创建读写、惰性/eager 求值、dispose、batch 的值同步更新、untrack 追踪隔离、类型系统等，这些测试均为有效测试。

### 最优先修复项

1. **`scheduler.kt` 重构** — 使 `CoroutineScope` 可注入，这是让 28 个异步测试通过的前提
2. **`index.kt:effect()` 加 try-catch** — 修复初始异常未被吞掉的 Bug
3. **修复 2 个测试用例的逻辑错误**
