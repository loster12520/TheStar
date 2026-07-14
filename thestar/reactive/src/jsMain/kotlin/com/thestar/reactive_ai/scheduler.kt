package com.thestar.reactive_ai

// ============================================================================
// 微任务调度器 — 基于 Promise.resolve().then() 的 effect 批量执行
// ============================================================================

/**
 * 预创建的 resolved Promise 实例，避免每次调度时重复创建。
 * 使用 [js] 直接获取 JS 的 Promise.resolve()，类型为 [dynamic] 以简化互操作。
 */
private val resolvedPromise: dynamic = js("Promise.resolve()")

/**
 * 调度一次 effect flush（如果尚未调度）。
 *
 * 三重门控——以下任一条件为真则跳过：
 * 1. 当前在 [batch] 中（[TrackingContext.batchDepth] > 0）→ 等待 batch 结束再调度
 * 2. 已调度过（[TrackingContext.scheduled] == true）→ 避免重复
 * 3. 待执行队列为空 → 没有工作可做
 *
 * 在每次 [SignalNode.write] 和 [EffectNode.markDirty] 后被调用。
 *
 * 使用微任务（Promise.then）而非 setTimeout(0)——微任务在浏览器渲染前执行，
 * 确保所有 DOM 更新在同一帧内完成，避免闪烁。
 */
internal fun scheduleFlush() {
    if (TrackingContext.batchDepth > 0) return
    if (TrackingContext.scheduled) return
    if (TrackingContext.pendingEffects.isEmpty()) return

    TrackingContext.scheduled = true
    // 使用预创建的 resolved Promise；Kotlin lambda 编译为 JS 函数引用
    resolvedPromise.then({ flushEffects() })
}

/**
 * 刷新所有待执行的 effect（在微任务中由 Promise.then 回调触发）。
 *
 * 关键设计——先快照再清空：
 * 1. 从 [TrackingContext.pendingEffects] 中取出当前所有待执行 effect 的快照
 * 2. 清空队列（清空后才允许新 effect 入队）
 * 3. 逐个执行快照中的 effect
 *
 * 这样在 effect 执行期间又有新 effect 入队时，不会导致无限循环——
 * 新入队的 effect 将在下一次微任务中执行。
 */
private fun flushEffects() {
    TrackingContext.scheduled = false

    // 快照并清空（执行期间可能有新 effect 加入）
    val effects = TrackingContext.pendingEffects.toList()
    TrackingContext.pendingEffects.clear()

    for (effect in effects) {
        effect.execute()
    }
}
