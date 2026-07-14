package com.thestar.reactive_ai

// ============================================================================
// 顶层工厂函数 — 响应式系统的公开入口
// ============================================================================

/**
 * 创建一个可写响应式信号。
 *
 * 信号（Signal），是一种用于在应用程序中传递和处理事件的机制。它允许不同的组件
 * 之间进行通信，而无需直接依赖彼此。信号可以携带数据，并且可以被多个监听器订阅，
 * 从而实现事件驱动的编程模式。
 *
 * 示例：
 *
 * ```kotlin
 * import com.thestar.reactive.*
 *
 * // 委托方式：像普通变量一样读写，推荐
 * var count by signal(0)
 * println(count)                   // 0
 * count = 5                        // 直接赋值，触发依赖更新
 * println(count)                   // 5
 *
 * // 直接创建：通过 .value 手动读写
 * val name = signal("world")
 * println(name.value)              // "world"
 * name.value = "TheStar"
 * ```
 *
 * @param data 初始数据，用于初始化信号的值。该数据可以是任何非空类型的对象。
 * @return 一个 [Signal] 对象，封装了初始数据，并提供了订阅和通知机制。
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> signal(data: T): Signal<T> {
    val node = SignalNode(data)
    return Signal(node)
}

/**
 * 创建一个派生信号（Memo）。
 *
 * 派生信号（Memo Signal），是一种基于现有信号计算得出的信号。它允许你定义一个
 * 依赖于其他信号的计算逻辑，并在依赖的信号发生变化时自动更新自身的值。派生信号
 * 通常用于计算和缓存复杂的状态，避免重复计算。
 *
 * ## 求值策略
 *
 * - **惰性求值（默认）**：仅在读取 [Signal.value] 且上游数据已变化时才重新计算。
 *   适合计算成本较高、不一定每次都需要最新值的场景。
 * - **活性求值（eager = true）**：上游数据变化时立即重算并缓存新值，读取零延迟。
 *   适合需要保证数据始终同步、且计算成本较低的场景。
 *
 * 示例：
 *
 * ```kotlin
 * var count by signal(0)
 *
 * // 惰性求值（默认）
 * val double by memo { count * 2 }
 *
 * // 活性求值：上游变化立即重算
 * val triple by memo(eager = true) { count * 3 }
 *
 * val greeting by memo { "Hello, ${name.value}!" }
 *
 * println(double)                  // 0 —— 注意此时 count 是 Int，直接参与算术
 * count = 5
 * println(double)                  // 10 —— 依赖变了，下次读取时自动重算
 * ```
 *
 * @param eager 是否启用活性求值模式，默认为 false（惰性求值）
 * @param function 一个返回值的函数，用于计算派生信号的值。该函数可以依赖于其他信号，
 *   当这些信号的值发生变化时，派生信号会自动重新计算。
 * @return 一个 [Signal] 对象，封装了计算逻辑，并提供了订阅和通知机制。
 *   当依赖的信号发生变化时，派生信号会自动更新自身的值，并通知所有订阅者。
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> memo(eager: Boolean = false, function: () -> T): Signal<T> {
    val node = MemoNode(function, eager)
    if (eager) {
        node.initEager()
    }
    return Signal(node)
}

/**
 * 创建一个副作用（Effect）。
 *
 * 副作用（Effect），是指在程序执行过程中产生的对外部环境的影响。副作用函数允许你
 * 在信号的值发生变化时执行特定的操作，例如更新 UI、发送网络请求或记录日志。副作用
 * 函数通常用于处理那些不直接影响应用程序状态的操作。
 *
 * Effect 在创建时立即执行一次以建立依赖关系，之后当依赖的任何信号发生变化时，
 * 会在微任务中异步重新执行。同一微任务内的多次变化会被合并为一次执行。
 *
 * 示例：
 *
 * ```kotlin
 * // effect 在依赖变化时自动执行
 * effect {
 *     console.log("count = $count, double = $double")
 * }
 *
 * count = 3   // 触发 effect，打印 "count = 3, double = 6"
 * count = 4   // 再次触发
 *
 * // 取消订阅
 * val e = effect {
 *     // ...
 * }
 *
 * // 不再需要时取消
 * e.dispose()
 * ```
 *
 * @param function 一个回调函数，当依赖的信号发生变化时会被调用。该函数可以访问和
 *   使用其他信号的值，从而实现对外部环境的影响。
 * @return 一个 [Effect] 对象，封装了副作用逻辑，并提供了通过 [Disposable.dispose]
 *   取消订阅的机制。当不再需要副作用时，调用 dispose 来取消订阅，从而避免不必要的
 *   计算和资源消耗。
 * @author lignting
 * @since 0.0.1
 */
fun effect(function: () -> Unit): Effect {
    val node = EffectNode(function)
    // 首次执行：建立依赖关系
    node.execute()
    return Effect(node)
}

/**
 * 取消追踪函数。
 *
 * 取消追踪（Untrack），是指在执行特定操作时，暂时停止对信号的依赖追踪。这样可以
 * 避免在某些情况下触发不必要的副作用或重新计算，从而提高性能和效率。
 *
 * 典型场景：在 effect 内部需要读取信号值但不希望将此次读取纳入依赖追踪时使用——
 * 例如快照日志、调试输出、或一次性计算。
 *
 * 示例：
 * ```kotlin
 * var name by signal("Alice")
 *
 * effect {
 *     // 正常追踪：name 变化时 effect 触发
 *     renderName(name)
 *
 *     // 旁路快照：仅打印日志，不追踪 name
 *     val snapshot = untrack { "${name}_${System.currentTimeMillis()}" }
 *     console.log(snapshot)
 * }
 * // effect 只绑定 renderName(name) 的读数；
 * // untrack 内部的 name 读取不计入依赖。
 * ```
 *
 * @param function 一个回调函数，在该函数执行期间，信号的依赖追踪将被暂时禁用。
 *   这样可以确保在执行该函数时，不会触发不必要的副作用或重新计算。
 * @return 函数的返回值，类型为 T。该返回值可以是任何类型的对象，具体取决于传入的
 *   回调函数的返回值类型。
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> untrack(function: () -> T): T {
    val prev = TrackingContext.currentObserver
    TrackingContext.currentObserver = null
    try {
        return function()
    } finally {
        TrackingContext.currentObserver = prev
    }
}

/**
 * 批量更新函数。
 *
 * 批量更新（Batch），是指在执行多个信号写入时，暂时推迟副作用的执行，
 * 直到批量操作完成后再统一触发。这对于需要同时更新多个相关状态、但只希望
 * 副作用执行一次的场景非常有用。
 *
 * 支持嵌套 batch：内部 batch 的计数器叠加，只有最外层 batch 结束时才
 * 真正调度 effect 执行。
 *
 * 示例：
 * ```kotlin
 * var firstName by signal("John")
 * var lastName by signal("Doe")
 *
 * effect {
 *     console.log("$firstName $lastName")
 * }
 *
 * // 不使用 batch：两次写入触发两次 effect（中间状态）
 * firstName = "Jane"   // effect: "Jane Doe"
 * lastName = "Smith"   // effect: "Jane Smith"
 *
 * // 使用 batch：两次写入只触发一次 effect（最终状态）
 * batch {
 *     firstName = "Alice"
 *     lastName = "Wonder"
 * }
 * // effect: "Alice Wonder"（仅一次）
 * ```
 *
 * @param function 批量操作回调。所有在回调内的 signal 写入将被收集，
 *   回调结束后统一调度 effect 执行。
 * @author lignting
 * @since 0.0.1
 */
fun batch(function: () -> Unit) {
    TrackingContext.batchDepth++
    try {
        function()
    } finally {
        TrackingContext.batchDepth--
        if (TrackingContext.batchDepth == 0) {
            // 最外层 batch 结束，调度所有积压的 effect
            scheduleFlush()
        }
    }
}
