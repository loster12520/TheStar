package com.thestar.reactive_ai

import kotlin.reflect.KProperty

// ============================================================================
// Disposable — 资源释放接口
// ============================================================================

/**
 * 表示一个可释放的资源。
 *
 * 实现此接口的类型可以通过 [dispose] 方法清理其所持有的响应式依赖关系，
 * 防止内存泄漏和意外的副作用执行。
 */
interface Disposable {
    fun dispose()
}

// ============================================================================
// Signal<T> — 响应式值包装（公开 API）
// ============================================================================

/**
 * 响应式值的公开包装类型。
 *
 * 内部持有一个 [ReactiveNode]（实际为 [SignalNode] 或 [MemoNode]），
 * 通过 [value] 属性对外提供统一的读/写接口。
 *
 * ## 使用方式
 *
 * **直接读写：**
 * ```kotlin
 * val s = signal(42)
 * println(s.value)   // 42
 * s.value = 100
 * ```
 *
 * **属性委托（推荐）：**
 * ```kotlin
 * var count by signal(0)
 * count = 5          // 等价于 s.value = 5
 * println(count)     // 等价于 s.value
 * ```
 *
 * ## 类型约束
 *
 * `T : Any` 表示不支持可空类型（如 `Signal<String?>`）。
 * 如需表示可空值，请使用包装类或特殊哨兵值。
 *
 * ## 生命周期
 *
 * [Signal] 实现了 [Disposable]。[dispose] 会级联释放所有依赖此信号的
 * Memo 和 Effect 节点，必要时可主动调用以防止内存泄漏。
 *
 * @param node 内部响应式节点（SignalNode 用于可写信号，MemoNode 用于派生信号）
 */
class Signal<T : Any> internal constructor(
    private val node: ReactiveNode
) : Disposable {

    /**
     * 读取或写入响应式值。
     *
     * - **get**：根据内部节点类型分派到 [SignalNode.read] 或 [MemoNode.read]，
     *   并在读取过程中自动建立依赖追踪（如果当前处于 memo/effect 的追踪上下文中）。
     * - **set**：仅 [SignalNode] 支持写入；[MemoNode] 为只读（静默忽略写入）。
     */
    var value: T
        get() = when (node) {
            is SignalNode<*> -> {
                @Suppress("UNCHECKED_CAST")
                (node as SignalNode<T>).read()
            }
            is MemoNode<*> -> {
                @Suppress("UNCHECKED_CAST")
                (node as MemoNode<T>).read()
            }
            else -> throw IllegalStateException(
                "Unknown ReactiveNode type: ${node::class.simpleName}"
            )
        }
        set(newValue) {
            when (node) {
                is SignalNode<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (node as SignalNode<T>).write(newValue)
                }
                is MemoNode<*> -> {
                    // Memo 是派生值，不可从外部写入——静默忽略
                    // 未来可改为抛 UnsupportedOperationException
                }
                else -> throw IllegalStateException(
                    "Unknown ReactiveNode type: ${node::class.simpleName}"
                )
            }
        }

    // --- 属性委托支持 ---

    /**
     * 属性委托的读取操作。
     *
     * 使 `val x by signal(0)` / `val y by memo { ... }` 正常工作。
     * 委托给 [value] getter，自动建立依赖追踪。
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /**
     * 属性委托的写入操作。
     *
     * 使 `var x by signal(0)` 的 `x = 5` 正常工作。
     * 委托给 [value] setter，触发更新传播。
     */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    // --- 资源释放 ---

    /**
     * 级联释放此信号及其所有下游节点。
     *
     * - 对于 SignalNode：遍历所有下游 Memo/Effect 并释放，然后清空订阅者列表
     * - 对于 MemoNode：除释放下游外，还通过 [MemoNode.disposeNode] 清理上游依赖
     *
     * 释放后此信号不应再被使用。
     */
    override fun dispose() {
        when (node) {
            is SignalNode<*> -> {
                for (observer in node.observers.toList()) {
                    when (observer) {
                        is MemoNode<*> -> observer.disposeNode()
                        is EffectNode -> observer.disposeNode()
                    }
                }
                node.observers.clear()
            }
            is MemoNode<*> -> {
                for (observer in node.observers.toList()) {
                    when (observer) {
                        is MemoNode<*> -> observer.disposeNode()
                        is EffectNode -> observer.disposeNode()
                    }
                }
                node.observers.clear()
                node.disposeNode()
            }
        }
    }
}

// ============================================================================
// Effect — 副作用包装（公开 API）
// ============================================================================

/**
 * 副作用包装类型。
 *
 * 持有内部的 [EffectNode]，通过 [dispose] 方法取消订阅。
 * 当不再需要副作用时调用 [dispose] 以清理依赖关系并停止通知。
 *
 * ## 使用方式
 *
 * ```kotlin
 * val e = effect {
 *     console.log("count = $count")
 * }
 * // 不再需要时取消
 * e.dispose()
 * ```
 */
class Effect internal constructor(private val node: EffectNode) : Disposable {

    /**
     * 取消此副作用。
     *
     * 清理所有上游依赖引用，从待执行队列中移除，标记为已释放。
     * 释放后此 effect 将不再被任何 signal 变化触发。
     * 幂等操作——重复调用安全。
     */
    override fun dispose() {
        node.disposeNode()
    }
}
