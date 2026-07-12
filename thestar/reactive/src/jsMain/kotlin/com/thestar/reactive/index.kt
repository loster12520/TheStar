package com.thestar.reactive

/**
 * 获取信号的函数。
 *
 * 信号（Signal）， 是一种用于在应用程序中传递和处理事件的机制。它允许不同的组件之间进行通信，而无需直接依赖彼此。信号可以携带数据，并且可以被多个监听器订阅，从而实现事件驱动的编程模式。
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
 * @param data 初始数据，用于初始化信号的值。该数据可以是任何类型的对象。
 * @return 一个 Signal 对象，封装了初始数据，并提供了订阅和通知机制。
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> signal(data: T): Signal<T> {
    TODO()
}

/**
 * 获取派生信号的函数。
 *
 * 派生信号（Memo Signal），是一种基于现有信号计算得出的信号。它允许你定义一个依赖于其他信号的计算逻辑，并在依赖的信号发生变化时自动更新自身的值。派生信号通常用于计算和缓存复杂的状态，避免重复计算。
 *
 * 示例：
 *
 * ```kotlin
 * var count by signal(0)
 * val double by memo { count * 2 }
 * val greeting by memo { "Hello, ${name.value}!" }
 *
 * println(double)                  // 0 —— 注意此时 count 是 Int，直接参与算术
 * count = 5
 * println(double)                  // 10 —— 依赖变了，下次读取时自动重算
 * ```
 * @param function 一个返回值的函数，用于计算派生信号的值。该函数可以依赖于其他信号，当这些信号的值发生变化时，派生信号会自动重新计算。
 * @return 一个 Signal 对象，封装了计算逻辑，并提供了订阅和通知机制。当依赖的信号发生变化时，派生信号会自动更新自身的值，并通知所有订阅者。
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> memo(function: () -> T): Signal<T> {
    TODO()
}

/**
 * 实现副作用函数。
 *
 * 副作用（Effect），是指在程序执行过程中产生的对外部环境的影响。副作用函数允许你在信号的值发生变化时执行特定的操作，例如更新 UI、发送网络请求或记录日志。副作用函数通常用于处理那些不直接影响应用程序状态的操作。
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
 * val dispose = effect {
 *     // 这个 effect 返回一个 Disposable
 * }
 *
 * // 不再需要时取消
 * dispose.dispose()
 * ```
 *
 * @param function 一个回调函数，当依赖的信号发生变化时会被调用。该函数可以访问和使用其他信号的值，从而实现对外部环境的影响。
 * @return 一个 Effect 对象，封装了副作用逻辑，并提供了取消订阅的机制。当不再需要副作用时，可以调用 Effect 对象的 dispose 方法来取消订阅，从而避免不必要地计算和资源消耗。
 * @author lignting
 * @since 0.0.1
 */
fun effect(function: () -> Unit): Effect {
    TODO()
}

/**
 * 取消追踪函数。
 *
 * 取消追踪（Untrack），是指在执行特定操作时，暂时停止对信号的依赖追踪。这样可以避免在某些情况下触发不必要的副作用或重新计算，从而提高性能和效率。
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
 * @param function 一个回调函数，在该函数执行期间，信号的依赖追踪将被暂时禁用。这样可以确保在执行该函数时，不会触发不必要的副作用或重新计算。
 * @return 函数的返回值，类型为 T。该返回值可以是任何类型的对象，具体取决于传入的回调函数的返回值类型。
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> untrack(function: () -> T): T {
    TODO()
}

fun batch(function: () -> Unit) {
    TODO()
}