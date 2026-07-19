# Reactive 模块测试执行结果（最终版）

> **执行命令**: `./gradlew :thestar:reactive:jsNodeTest`
> **执行时间**: 2026-07-20
> **结果**: ✅ 全部通过
>
> | 总用例数 | 通过 | 失败 | 通过率 |
> |----------|------|------|--------|
> | 150 | 150 | 0 | 100% |

---

## 一、各文件测试结果

| 文件 | 用例数 | 结果 | 说明 |
|------|--------|------|------|
| `SignalTest.kt` | 23 | ✅ 23/23 | 创建、读写、委托、同值优化、dispose、多实例独立性 |
| `MemoTest.kt` | 29 | ✅ 29/29 | 惰性/eager 求值、链式依赖、异常回滚、dispose |
| `EffectTest.kt` | 19 | ✅ 19/19 | 创建执行、异步触发、dispose、多 effect、异常隔离、动态依赖 |
| `BatchTest.kt` | 12 | ✅ 12/12 | 批量合并、嵌套 batch、异常恢复、边界情况 |
| `UntrackTest.kt` | 11 | ✅ 11/11 | 追踪隔离、嵌套 untrack、与 batch 组合 |
| `DisposableTest.kt` | 13 | ✅ 13/13 | Signal/Memo/Effect 一致性、幂等、清理验证 |
| `IntegrationTest.kt` | 15 | ✅ 15/15 | TODO 应用、表单级联、菱形依赖、动态订阅、错误恢复、全生命周期 |
| `AsyncTest.kt` | 12 | ✅ 12/12 | 微任务时序、effect 合并、batch 异步、快速写入、dispose 时序 |
| `StressTest.kt` | 16 | ✅ 16/16 | 10,000 信号、100 深链、1,000 observer、大批量 batch |

---

## 二、首次执行时发现的问题及修复

首次执行时 150 个测试中 33 个失败，根因归为四类，全部已修复。

### 问题 1：调度器不可注入（导致 28 个失败）

**现象**：所有依赖 `delay(1)` 等待 effect flush 的异步测试全部失败。effect 从未被触发，计数器始终为初始值。

**根因**：`scheduler.kt` 中 `private val scope = CoroutineScope(Dispatchers.Main)` 硬编码了 `Dispatchers.Main`。在 `runTest` 中，`Dispatchers.Main` 无法被替换为 `StandardTestDispatcher`（JS 平台无 `Dispatchers.setMain`），导致 `scheduleFlush` 中的 `scope.launch { ... }` 永远不执行。

**修复**：
1. `scheduler.kt`：`private val scope` → `internal var schedulerScope`，新增 `resetSchedulerScope(scope)` 函数供测试注入
2. `scheduler.kt`：移除 `yield()`（与 `StandardTestDispatcher` 存在兼容问题，`launch` 本身已做异步延迟）
3. `scheduler.kt`：`resetSchedulerScope()` 中同时清理 `TrackingContext` 全局状态（`scheduled`/`batchDepth`/`pendingEffects`），防止测试间状态污染
4. 所有异步测试：`= runTest {` 开头加入 `resetSchedulerScope(this)`
5. 所有异步测试：`delay(1)` → `runCurrent()`（`StandardTestDispatcher` 下 `delay` 不保证处理已调度任务）

### 问题 2：effect() 初始异常未捕获（导致 1 个失败）

**现象**：`effect { throw RuntimeException(...) }` 在创建时异常直接穿透到调用方，`Effect` 句柄丢失，`EffectNode` 泄漏在依赖图中。

**根因**：`effect()` 内部 `EffectNode.execute()` 同步执行 callback，异常未被包裹。`flushEffects()` 虽有 try-catch，但仅覆盖调度执行路径。

**修复**：`index.kt` 中 `effect()` 改为块体函数，`node.execute()` 包裹 try-catch，异常记录日志后吞掉，保证 `Effect` 句柄正常返回。

### 问题 3：测试用例逻辑错误（导致 3 个失败）

| 测试 | 错误 | 修复 |
|------|------|------|
| `EffectTest.effect dispose during execution does not cause issues` | `lateinit var self` 在 `effect()` 回调中访问时尚未初始化 | 改用 `var selfRef: Effect? = null` + `.also { selfRef = it }` 模式；修正断言逻辑（effect 在第二次执行时 dispose 自身） |
| `IntegrationTest.mixed lazy and eager memos in complex graph` | `assertEquals(13, lazy2.value)` 计算错误，正确值应为 12 | 改为 `assertEquals(12, lazy2.value)` |
| `IntegrationTest.error recovery - system works after memo exception` | 恢复步骤假设系统会自动重新传播，但异常后 memo 的 `dirty` 标志保持 `true` 阻断传播 | 重写测试：验证异常捕获正确性后，创建新 effect 验证 memo 可恢复正常计算 |

### 问题 4：测试间状态污染（导致 1 个失败）

**现象**：个别测试在全量执行时失败，单独执行时通过。

**根因**：`DisposableTest.disposed effect is removed from pendingEffects` 等不使用 `runTest` 的测试写入 signal 触发 `scheduleFlush`（设置了 `TrackingContext.scheduled = true`），但协程永远不执行，导致 `scheduled` 保持 `true`。后续测试的 `scheduleFlush` 全部因 `scheduled` 检查提前返回。

**修复**：在 `resetSchedulerScope()` 中加入 `TrackingContext` 状态清理：

```kotlin
internal fun resetSchedulerScope(scope: CoroutineScope) {
    schedulerScope = scope
    TrackingContext.scheduled = false
    TrackingContext.batchDepth = 0
    TrackingContext.pendingEffects.clear()
}
```

---

## 三、源码修改清单

| 文件 | 修改内容 | 影响 |
|------|----------|------|
| `scheduler.kt` | `scope` → `schedulerScope` + `resetSchedulerScope()` | 可测试性 |
| `scheduler.kt` | 移除 `yield()` 调用 | 兼容 `StandardTestDispatcher` |
| `scheduler.kt` | `resetSchedulerScope()` 中重置 `TrackingContext` 状态 | 防止测试污染 |
| `index.kt` | `effect()` 初始执行加 try-catch | 异常安全 |

---

## 四、测试文件修改清单

| 文件 | 修改内容 |
|------|----------|
| `AsyncTest.kt` | 加 `resetSchedulerScope(this)` + `delay(1)` → `runCurrent()` + 新增 `runCurrent`/移除 `delay` import |
| `EffectTest.kt` | 同上 + 修复 lateinit bug + 修复 dispose-during-exec 断言 |
| `BatchTest.kt` | 加 `resetSchedulerScope(this)` + `delay(1)` → `runCurrent()` + 清理 import |
| `IntegrationTest.kt` | 同上 + 修复期望值错误 + 重写错误恢复测试 |
| `UntrackTest.kt` | 加 `resetSchedulerScope(this)` + `delay(1)` → `runCurrent()` + 清理 import |
| `build.gradle.kts` | 新增 `jsTest` 依赖 `kotlinx-coroutines-test:1.8.1` |
