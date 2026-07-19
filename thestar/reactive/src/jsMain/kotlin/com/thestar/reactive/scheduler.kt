package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 调度器使用的协程作用域。
 *
 * 默认为 [Dispatchers.Main]。测试环境可通过 [resetSchedulerScope] 替换为
 * 测试调度器（如 `runTest` 提供的 `TestScope`）。
 */
internal var schedulerScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

/**
 * 重置调度器作用域（仅供测试使用）。
 *
 * 调用示例（在 `runTest` 中）：
 * ```kotlin
 * @Test
 * fun test() = runTest {
 *     resetSchedulerScope(this)  // this 即 TestScope
 *     // ... 测试逻辑，delay() 将正确驱动 effect flush
 * }
 * ```
 */
internal fun resetSchedulerScope(scope: CoroutineScope) {
    schedulerScope = scope
    // 重置全局状态，防止前一个测试留下的脏状态污染当前测试
    TrackingContext.scheduled = false
    TrackingContext.batchDepth = 0
    TrackingContext.pendingEffects.clear()
}

internal fun scheduleFlush() {
    if (
        TrackingContext.batchDepth > 0 ||
        TrackingContext.scheduled ||
        TrackingContext.pendingEffects.isEmpty()
    )
        return

    TrackingContext.scheduled = true
    schedulerScope.launch {
        flushEffects()
    }
}

private fun flushEffects() {
    TrackingContext.scheduled = false
    
    // 快照并清空（执行期间可能有新 effect 加入）
    val effects = TrackingContext.pendingEffects.toList()
    TrackingContext.pendingEffects.clear()
    
    for (effect in effects) {
        try {
            effect.execute()
        } catch (e: Throwable) {
            logger.error { "Error while flushing effect: $e" }
        }
    }
}