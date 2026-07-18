package com.thestar.reactive

fun <T : Any> signal(data: T): Signal<T> =
    Signal(SignalNode(data))

fun <T : Any> memo(
    eager: Boolean = false,
    callback: () -> T
): Memo<T> =
    Memo(MemoNode(eager, callback))

fun effect(callback: () -> Unit): Effect =
    Effect(EffectNode(callback).also { it.execute() })

fun <T : Any> untrack(callback: () -> T): T =
    emptyObserver.changeCurrentObserver(callback)

fun batch(callback: () -> Unit) {
    TrackingContext.batchDepth++
    try {
        callback()
    } finally {
        TrackingContext.batchDepth--
        if (TrackingContext.batchDepth == 0) {
            // 最外层 batch 结束，调度所有积压的 effect
            scheduleFlush()
        }
    }
}