package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private val scope = CoroutineScope(Dispatchers.Main)

internal fun scheduleFlush() {
    if (
        TrackingContext.batchDepth > 0 ||
        TrackingContext.scheduled ||
        TrackingContext.pendingEffects.isEmpty()
    )
        return
    
    TrackingContext.scheduled = true
    scope.launch {
        // 让出当前线程，将 flushEffects 安排在下一个微任务
        yield()
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