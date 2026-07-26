# Reactive 模块测试执行结果（v2）

> **执行命令**: `./gradlew :thestar:reactive:jsNodeTest`
> **执行时间**: 2026-07-26
> **结果**: ✅ 全部通过
>
> | 总用例数 | 通过 | 失败 | 通过率 |
> |----------|------|------|--------|
> | 164 | 164 | 0 | 100% |

---

## 一、各文件测试结果

| 文件 | 用例数 | 结果 | 说明 |
|------|--------|------|------|
| `SignalTest.kt` | 17 | ✅ 17/17 | 委托模式读写、同值优化、多实例独立性（移除 dispose 测试） |
| `MemoTest.kt` | 29 | ✅ 29/29 | 惰性/eager 求值、链式依赖、异常回滚、dispose、委托属性 |
| `EffectTest.kt` | 19 | ✅ 19/19 | 创建执行、异步触发、dispose、多 effect、异常隔离、动态依赖 |
| `BatchTest.kt` | 12 | ✅ 12/12 | 批量合并、嵌套 batch、异常恢复、边界情况 |
| `UntrackTest.kt` | 11 | ✅ 11/11 | 追踪隔离、嵌套 untrack、与 batch 组合 |
| `DisposableTest.kt` | 9 | ✅ 9/9 | Memo/Effect 一致性、幂等、清理验证（移除 Signal dispose） |
| `IntegrationTest.kt` | 15 | ✅ 15/15 | TODO 应用、表单级联、菱形依赖、动态订阅、错误恢复、全生命周期 |
| `AsyncTest.kt` | 12 | ✅ 12/12 | 微任务时序、effect 合并、batch 异步、快速写入、dispose 时序 |
| `StressTest.kt` | 15 | ✅ 15/15 | 10,000 信号、100 深链、1,000 observer、大批量 batch |
| `BugRegressionTest.kt` | 25 | ✅ 25/25 | 19 个 Bug 全覆盖回归（HIGH 7 / MEDIUM 7 / LOW 5） |

---

## 二、v2 修改内容

本次修改基于以下三项 API 变更 + Bug 回归测试：

### API 变更

| 变更项 | 旧 API | 新 API |
|--------|--------|--------|
| 调度器注入 | `resetSchedulerScope(this)` | `schedulerScope = this`（直接赋值） |
| 值访问 | `s.value` / `s.value = x` | `var s by signal(x)` → 直接 `s` / `s = x` |
| Signal dispose | `s.dispose()` | 不再支持 |

### 对应修改

**1. `resetSchedulerScope` → `schedulerScope = this`**

- 所有 `runTest` 测试首行改为 `schedulerScope = this`
- 新增 10 个 `@BeforeTest setUp()` 方法，统一重置 `schedulerScope` 和 `TrackingContext` 状态
- 根因：`schedulerScope` 在模块加载时初始化，`runTest` 设置的 `Dispatchers.Main` 在之后才生效，需显式赋值

**2. `.value` → 委托模式**

- `val s = signal(x)` → `var s by signal(x)`
- `s.value` → `s`，`s.value = x` → `s = x`
- Memo：需 dispose 时保留对象引用 `val mObj = memo { ... }; val m by mObj`
- 循环创建 signal/memo：使用 `basicNode.read()` / `basicNode.write()`
- `s.node.observers` → `sSig.basicNode.observers`

**3. 移除 Signal `.dispose()`**

| 文件 | 删除测试 |
|------|----------|
| `SignalTest.kt` | `signal dispose can be called`, `signal dispose is idempotent`, `signal dispose clears observer links`, `signal value is still readable after dispose`, `signal value is still writable after dispose`, `signal is Disposable` |
| `DisposableTest.kt` | `Signal implements Disposable`, `Signal dispose is idempotent`, `Signal dispose clears observers`, `Signal dispose multiple times keeps observers empty` |
| `StressTest.kt` | `dispose 10000 signals` |

**4. 新增 BugRegressionTest.kt（25 个测试）**

覆盖 `doc/test/reactive-code-review-report.md` 中全部 19 个缺陷：

| 优先级 | 缺陷 | 测试数 |
|--------|------|--------|
| 🔴 HIGH | BUG-1~7 | 14 |
| 🟡 MEDIUM | BUG-8~14 | 9 |
| 🟢 LOW | BUG-15~19 | 5 |

---

## 三、测试间状态隔离

为避免全局单例 `TrackingContext` + `schedulerScope` 跨测试污染，每个测试类添加：

```kotlin
@BeforeTest
fun setUp() {
    schedulerScope = CoroutineScope(Dispatchers.Default)
    TrackingContext.scheduled = false
    TrackingContext.batchDepth = 0
    TrackingContext.pendingEffects.clear()
}
```

`runTest` 测试内部首行 `schedulerScope = this` 将 scope 替换为 `TestScope`，使 `runCurrent()` 可正确驱动 effect 执行。

---

## 四、源码修改清单（本次）

| 文件 | 修改内容 | 影响 |
|------|----------|------|
| `SignalTest.kt` | `.value` → 委托模式；移除 6 个 dispose 测试；`s.node` → `s.basicNode`；新增 `@BeforeTest` | API 适配 |
| `MemoTest.kt` | `.value` → 委托模式；保留 Memo 对象引用用于 dispose；新增 `@BeforeTest` | API 适配 |
| `EffectTest.kt` | `resetSchedulerScope` → `schedulerScope = this`；`.value` → 委托；修复异常测试；新增 `@BeforeTest` | API 适配 + Bug 修复 |
| `AsyncTest.kt` | `resetSchedulerScope` → `schedulerScope = this`；`.value` → 委托；新增 `@BeforeTest` | API 适配 |
| `BatchTest.kt` | `resetSchedulerScope` → `schedulerScope = this`；`.value` → 委托；新增 `@BeforeTest` | API 适配 |
| `DisposableTest.kt` | 移除 4 个 Signal dispose 测试；`.value` → 委托；`s.node` → `s.basicNode`；新增 `@BeforeTest` | API 适配 |
| `IntegrationTest.kt` | `resetSchedulerScope` → `schedulerScope = this`；`.value` → 委托；移除 `count.dispose()`；新增 `@BeforeTest` | API 适配 |
| `StressTest.kt` | 循环信号改用 `basicNode.read()/write()`；移除 `dispose 10000 signals` 测试 + 所有 Signal dispose 调用；新增 `@BeforeTest` | API 适配 |
| `UntrackTest.kt` | `resetSchedulerScope` → `schedulerScope = this`；`.value` → 委托；`a.node` → `a.basicNode`；新增 `@BeforeTest` | API 适配 |
| `BugRegressionTest.kt` | **新建** 25 个回归测试，覆盖 19 个审查缺陷 | 回归覆盖 |
