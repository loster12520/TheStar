# Reactive 模块测试案例清单

## 测试统计

> ✅ 全部测试通过。执行时间：2026-07-26。命令：`./gradlew :thestar:reactive:jsNodeTest`

| 层级 | 文件 | 用例数 | 已通过 | 失败 | 跳过 | 覆盖率 |
|------|------|--------|--------|------|------|--------|
| 单元测试 | `SignalTest.kt` | 17 | 17 | 0 | 0 | ✅ |
| 单元测试 | `MemoTest.kt` | 29 | 29 | 0 | 0 | ✅ |
| 单元测试 | `EffectTest.kt` | 19 | 19 | 0 | 0 | ✅ |
| 单元测试 | `BatchTest.kt` | 12 | 12 | 0 | 0 | ✅ |
| 单元测试 | `UntrackTest.kt` | 11 | 11 | 0 | 0 | ✅ |
| 单元测试 | `DisposableTest.kt` | 9 | 9 | 0 | 0 | ✅ |
| 综合测试 | `IntegrationTest.kt` | 15 | 15 | 0 | 0 | ✅ |
| 综合测试 | `AsyncTest.kt` | 12 | 12 | 0 | 0 | ✅ |
| 压力测试 | `StressTest.kt` | 15 | 15 | 0 | 0 | ✅ |
| Bug 回归 | `BugRegressionTest.kt` | 25 | 25 | 0 | 0 | ✅ |
| **合计** | **10 个文件** | **164** | **164** | **0** | **0** | **100%** |

---

## 一、单元测试

### `SignalTest.kt` — `signal()` / `Signal<T>`

> **v2 变更**：Signal 不再支持 `.dispose()` 和 `.value` 访问，全部改用 `var x by signal()` 委托模式。

| # | 测试用例 | 分类 |
|---|----------|------|
| 1 | `signal stores initial value` | 创建与初始值 |
| 2 | `signal with string value` | 创建与初始值 |
| 3 | `signal with boolean value` | 创建与初始值 |
| 4 | `signal with list value` | 创建与初始值 |
| 5 | `signal with custom data class value` | 创建与初始值 |
| 6 | `signal updates value` | 值更新 |
| 7 | `signal can be updated multiple times` | 值更新 |
| 8 | `signal with nullable type parameter does not accept null` | 值更新 |
| 9 | `delegated signal read and write` | 委托属性 |
| 10 | `delegated signal with multiple updates` | 委托属性 |
| 11 | `signal no-op when same value is written` | 同值不触发 |
| 12 | `signal with data class checks structural equality` | 同值不触发 |
| 13 | `multiple signals are independent` | 多信号独立性 |
| 14 | `many signals created independently` | 多信号独立性 |
| 15 | `read signal outside tracking context returns value` | 追踪上下文外 |
| 16 | `write signal outside tracking context updates value` | 追踪上下文外 |
| 17 | `signal factory returns Signal instance` | 类型 |

### `MemoTest.kt` — `memo()` / `Memo<T>`

> **v2 变更**：`.value` 改为委托模式（`val m by memo { ... }`）；需 dispose 时保留 `Memo` 对象引用，通过 `basicNode.read()` 读取值。

| # | 测试用例 | 分类 |
|---|----------|------|
| 1 | `memo computes derived value` | 基本派生 |
| 2 | `memo updates when upstream changes` | 基本派生 |
| 3 | `memo with string derivation` | 基本派生 |
| 4 | `memo with list transformation` | 基本派生 |
| 5 | `memo with boolean logic` | 基本派生 |
| 6 | `memo is lazy - does not compute until first read` | 惰性求值 |
| 7 | `memo does not recompute when upstream changes but not read` | 惰性求值 |
| 8 | `memo recomputes only once per upstream change` | 惰性求值 |
| 9 | `memo eager computes immediately on creation` | Eager 模式 |
| 10 | `memo eager recomputes immediately on upstream change` | Eager 模式 |
| 11 | `memo eager recomputes on each upstream change` | Eager 模式 |
| 12 | `memo eager with same value does not propagate dirty downstream` | Eager 模式 |
| 13 | `memo with multiple dependencies` | 多依赖 |
| 14 | `memo with three or more dependencies` | 多依赖 |
| 15 | `chained lazy memos` | 链式 Memo |
| 16 | `chained eager memos` | 链式 Memo |
| 17 | `mixed lazy and eager chain` | 链式 Memo |
| 18 | `memo with no dependencies returns constant` | 无依赖 |
| 19 | `memo with no dependencies computed only once` | 无依赖 |
| 20 | `memo callback throws on first computation` | 异常处理 |
| 21 | `memo callback throws on recompute - old dependencies are preserved` | 异常处理 |
| 22 | `memo recovers after exception when upstream is fixed` | 异常处理 |
| 23 | `memo dispose is idempotent` | Dispose |
| 24 | `memo value after dispose is still readable` | Dispose |
| 25 | `memo value after dispose does not update on upstream change` | Dispose |
| 26 | `eager memo dispose stops automatic recomputation` | Dispose |
| 27 | `memo returns Memo instance` | 类型 |
| 28 | `memo is Disposable` | 类型 |
| 29 | `delegated memo read` | 委托属性 |

### `EffectTest.kt` — `effect()` / `Effect`

> **v2 变更**：不再使用 `resetSchedulerScope()`；改用 `schedulerScope = this` + `@BeforeTest` 状态清理。effect 内 signal 读取改用委托模式。异常测试改为在 flush 中触发（effect 初始异常现在会穿透）。

| # | 测试用例 | 分类 |
|---|----------|------|
| 1 | `effect runs on creation` | 创建与初始执行 |
| 2 | `effect runs exactly once on creation` | 创建与初始执行 |
| 3 | `effect returns Effect instance` | 创建与初始执行 |
| 4 | `effect captures latest values from all read signals` | 创建与初始执行 |
| 5 | `effect triggers after signal write` | 异步触发 |
| 6 | `effect triggers once for multiple writes to same signal` | 异步触发 |
| 7 | `effect triggers independently for different signals` | 异步触发 |
| 8 | `effect dispose stops notifications` | Dispose |
| 9 | `effect dispose is idempotent` | Dispose |
| 10 | `disposed effect is removed from pendingEffects` | Dispose |
| 11 | `effect dispose clears source relationships` | Dispose |
| 12 | `multiple effects on same signal` | 多 Effect |
| 13 | `multiple effects fire in batch after signal change` | 多 Effect |
| 14 | `effect exception during flush does not prevent other effects` | 异常隔离 |
| 15 | `effect exception during initial execution is caught` | 异常隔离 |
| 16 | `effect dynamically changes dependencies across executions` | 依赖动态变化 |
| 17 | `effect with no signal reads runs once and does not re-trigger` | 边界情况 |
| 18 | `multiple dispose calls on effect do not corrupt state` | 边界情况 |
| 19 | `effect dispose during execution does not cause issues` | 边界情况 |

### `BatchTest.kt` — `batch()`

| # | 测试用例 | 分类 |
|---|----------|------|
| 1 | `batch groups multiple writes into single effect run` | 基本批量更新 |
| 2 | `batch updates values synchronously` | 基本批量更新 |
| 3 | `batch with multiple writes to same signal` | 基本批量更新 |
| 4 | `nested batch defers flush to outermost batch end` | 嵌套 Batch |
| 5 | `triple nested batch works correctly` | 嵌套 Batch |
| 6 | `nested batch does not lose updates` | 嵌套 Batch |
| 7 | `batch recovers batchDepth after exception` | 异常处理 |
| 8 | `nested batch recovers batchDepth after inner exception` | 异常处理 |
| 9 | `empty batch does not throw` | 边界情况 |
| 10 | `batch with no signals does not schedule flush` | 边界情况 |
| 11 | `batch returns normally for non-signal operations` | 边界情况 |
| 12 | `successive batches work independently` | 边界情况 |

### `UntrackTest.kt` — `untrack()`

| # | 测试用例 | 分类 |
|---|----------|------|
| 1 | `untrack returns computed value` | 基本功能 |
| 2 | `untrack prevents dependency tracking` | 基本功能 |
| 3 | `untrack read does not register observer` | 基本功能 |
| 4 | `untrack within effect does not add to sources` | 基本功能 |
| 5 | `untrack mixed with tracked reads in same effect` | 混合使用 |
| 6 | `nested untrack still prevents tracking` | 嵌套 |
| 7 | `untrack with memo inside batch` | 组合 |
| 8 | `untrack reads current value even if dirty` | 组合 |
| 9 | `untrack with no signals returns plain value` | 边界情况 |
| 10 | `untrack outside any tracking context works normally` | 边界情况 |
| 11 | `untrack with exception propagates` | 边界情况 |

### `DisposableTest.kt` — `Disposable` 接口

> **v2 变更**：Signal 不再实现 `Disposable`，移除 4 个 Signal dispose 测试。

| # | 测试用例 | 分类 |
|---|----------|------|
| 1 | `Memo implements Disposable` | 类型检查 |
| 2 | `Effect implements Disposable` | 类型检查 |
| 3 | `Memo dispose is idempotent` | Memo dispose |
| 4 | `Memo dispose clears sources and observers` | Memo dispose |
| 5 | `Memo dispose removes itself from upstream signal` | Memo dispose |
| 6 | `Effect dispose is idempotent` | Effect dispose |
| 7 | `Effect dispose clears sources from upstream` | Effect dispose |
| 8 | `dispose chain - effect and memo after disposes do not trigger` | 组合 dispose |
| 9 | `partial dispose - disposing effect first then updating signal` | 组合 dispose |

---

## 二、综合/场景测试

### `IntegrationTest.kt`

> **v2 变更**：全部 `.value` 改用委托模式，不再使用 `resetSchedulerScope()`。

| # | 测试用例 | 场景 |
|---|----------|------|
| 1 | `TODO app - add and complete tasks` | TODO 应用 |
| 2 | `TODO app - batch add multiple todos` | TODO 应用 |
| 3 | `cascading dropdown - province city district` | 表单级联 |
| 4 | `cascading dropdown with batch update` | 表单级联 |
| 5 | `diamond dependency - D updates only once when A changes` | 复杂依赖图 |
| 6 | `diamond dependency with eager memos` | 复杂依赖图 |
| 7 | `dynamic subscribe and unsubscribe` | 动态订阅 |
| 8 | `create and destroy many effects without memory leak` | 动态订阅 |
| 9 | `error recovery - memo throws then recovers` | 错误恢复 |
| 10 | `error recovery - system works after memo exception` | 错误恢复 |
| 11 | `full lifecycle - create update batch dispose` | 全生命周期 |
| 12 | `mixed lazy and eager memos in complex graph` | 混合模式 |
| 13 | `all value types work correctly - Int String Boolean List` | 值类型覆盖 |
| 14 | `custom class with equals works correctly` | 值类型覆盖 |
| 15 | `cross dependencies between effects and signals` | 多 Effect + Signal |

### `AsyncTest.kt`

> **v2 变更**：不再使用 `resetSchedulerScope()`，改用 `schedulerScope = this` + `@BeforeTest`。

| # | 测试用例 | 验证点 |
|---|----------|--------|
| 1 | `effect executes asynchronously after write` | 微任务时序 |
| 2 | `multiple writes before microtask are merged into single effect run` | effect 合并 |
| 3 | `effect does not re-execute in same microtask after initial flush` | effect 合并 |
| 4 | `batch flush happens after outermost batch ends` | batch 异步 |
| 5 | `batch effect execution order is correct` | batch 异步 |
| 6 | `two independent batches produce separate effect rounds` | 多 batch |
| 7 | `batch inside effect execution context` | 多 batch |
| 8 | `dispose effect before microtask prevents execution` | dispose 时序 |
| 9 | `dispose effect during microtask flush does not execute it` | dispose 时序 |
| 10 | `signal write from coroutine triggers effect` | 协程写入 |
| 11 | `rapid signal writes do not lose updates` | 快速写入 |
| 12 | `alternating writes and reads do not corrupt state` | 快速写入 |

---

## 三、压力/性能测试

### `StressTest.kt`

> **v2 变更**：循环创建的 signal 使用 `basicNode.read()`/`basicNode.write()`；移除 `dispose 10000 signals` 测试（Signal 不再支持 dispose）。

| # | 测试用例 | 规模 | 关注点 |
|---|----------|------|--------|
| 1 | `create 10000 signals` | 10,000 | 创建正确性 |
| 2 | `write 10000 signals individually` | 10,000 | 写入正确性 |
| 3 | `deep chain of 100 lazy memos` | 100 深链 | 传播正确性 |
| 4 | `deep chain of 100 eager memos` | 100 深链 | 传播正确性 |
| 5 | `deep chain propagation correctness` | 200 深链 | 传播正确性 |
| 6 | `one signal with 1000 effects` | 1,000 observer | 宽依赖 |
| 7 | `one signal with 1000 memos` | 1,000 派生 | 宽依赖 |
| 8 | `batch with 10000 writes` | 10,000 次写入 | 批处理 |
| 9 | `batch with 10000 signal writes to 100 signals` | 100×100 | 批处理 |
| 10 | `create and dispose 1000 effects on same signal` | 1,000 循环 | 无泄漏 |
| 11 | `create and dispose 1000 memos on same signal` | 1,000 循环 | 无泄漏 |
| 12 | `complex graph with 500 nodes - correctness` | 500 节点 | 正确性 |
| 13 | `wide fan out and deep chain combined` | 10×50 链 | 组合场景 |
| 14 | `large nested batch does not stack overflow` | 500 层嵌套 | 栈安全 |

---

## 四、Bug 回归测试

### `BugRegressionTest.kt` — 代码审查报告 19 个缺陷

> **新增**：针对 `doc/test/reactive-code-review-report.md` 中记录的 19 个缺陷编写回归测试。

| Bug | 测试用例 | 严重程度 |
|-----|----------|----------|
| BUG-1 | `eager memo recovers after recompute exception on next read` | 🔴 HIGH |
| BUG-1 | `eager memo recompute exception does not return stale value` | 🔴 HIGH |
| BUG-2 | `observer exception does not prevent other observers from being notified` | 🔴 HIGH |
| BUG-2 | `scheduleFlush is called even after observer exception` | 🔴 HIGH |
| BUG-3 | `memo that throws on first compute does not register ghost dependency` | 🔴 HIGH |
| BUG-4 | `circular memo dependency results in stack overflow` | 🔴 HIGH |
| BUG-4 | `simple circular memo chain does not crash system after recovery` | 🔴 HIGH |
| BUG-5 | `scheduleFlush recovers scheduled flag after scope cancellation` | 🔴 HIGH |
| BUG-5 | `effect system continues to work after multiple flush cycles` | 🔴 HIGH |
| BUG-6 | `resetSchedulerScope cancels old scope and works with new scope` | 🔴 HIGH |
| BUG-7 | `eager memo reentrancy guard prevents double recompute` | 🔴 HIGH |
| BUG-8 | `disposed memo does not re-register when read` | 🟡 MEDIUM |
| BUG-8 | `disposed memo does not propagate dirty to downstream` | 🟡 MEDIUM |
| BUG-9 | `effect initial exception propagates to caller` | 🟡 MEDIUM |
| BUG-9 | `effect with throwing callback is not silently zombie` | 🟡 MEDIUM |
| BUG-10 | `effect self-trigger loop is prevented by executing flag` | 🟡 MEDIUM |
| BUG-11 | `memo recompute state commit is atomic` | 🟡 MEDIUM |
| BUG-12 | `memo recovers dependency graph correctly after callback exception` | 🟡 MEDIUM |
| BUG-13 | `memo value equality check uses structural equality` | 🟡 MEDIUM |
| BUG-13 | `memo with data class uses structural equality` | 🟡 MEDIUM |
| BUG-14 | `schedulerScope works in test environment without manual injection` | 🟡 MEDIUM |
| BUG-15 | `observer notification pattern works correctly after refactoring` | 🟢 LOW |
| BUG-16 | `write returns meaningful value for batch optimization` | 🟢 LOW |
| BUG-17 | `dispose does not cause double cleanup issues` | 🟢 LOW |
| BUG-18 | `cleanupSourcesSafety source removal logic is correct` | 🟢 LOW |
| BUG-19 | `global TrackingContext does not leak state between independent tests` | 🟢 LOW |

---

## 五、覆盖矩阵

| | `signal` | `memo` | `effect` | `batch` | `untrack` | `Disposable` |
|---|---|---|---|---|---|---|
| 正常路径 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 异常路径 | — | ✅ | ✅ | ✅ | ✅ | — |
| 边界情况 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 异步行为 | — | — | ✅ | ✅ | — | — |
| Dispose | — | ✅ | ✅ | — | — | ✅ |
| 委托属性 | ✅ | ✅ | — | — | — | — |
| 多实例 | ✅ | ✅ | ✅ | ✅ | — | — |
| Bug 回归 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
