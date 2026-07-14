package com.thestar.reactive_ai

// ============================================================================
// TrackingContext — 全局单例，管理响应式图的追踪上下文
// ============================================================================

/**
 * 全局追踪上下文单例。
 *
 * 职责：
 * - [currentObserver]：当前正在执行的 memo/effect 节点。Lambda 中对任何 signal 的读取
 *   会将此 observer 注册为订阅者。为 null 时表示不在任何追踪上下文中。
 * - [batchDepth]：batch() 嵌套计数器。> 0 时 signal 写入不立即调度 effect，而是积压到
 *   最外层 batch 结束时统一 flush。
 * - [pendingEffects]：收集等待执行的 effect 节点，在微任务中统一执行。
 * - [scheduled]：是否已有微任务在排队，避免重复调度。
 *
 * @author lignting
 * @since 0.0.1
 */
internal object TrackingContext {
    var currentObserver: ReactiveNode? = null
    var batchDepth: Int = 0
    val pendingEffects: MutableSet<EffectNode> = mutableSetOf()
    var scheduled: Boolean = false
}

// ============================================================================
// ReactiveNode — 响应式节点抽象基类
// ============================================================================

/**
 * 响应式图的节点基类。
 *
 * 每个节点维护：
 * - [observers]：依赖本节点的下游节点集合（"谁在订阅我"）
 * - [dirty]：本节点的缓存值是否可能已过期
 *
 * 子类：
 * - [SignalNode]：纯数据源，不覆写 [addSource]（空实现），不依赖上游
 * - [MemoNode]：派生节点，覆写 [addSource] 维护上游来源集合
 * - [EffectNode]：副作用叶子，覆写 [addSource] 维护上游来源集合，覆写 [markDirty] 入队
 */
internal abstract class ReactiveNode {
    val observers: MutableSet<ReactiveNode> = mutableSetOf()
    var dirty: Boolean = false

    /**
     * 记录上游来源节点。
     * [SignalNode] 使用空实现（它是纯源，无上游）。
     * [MemoNode] 和 [EffectNode] 覆写以维护 [sources] 集合。
     */
    open fun addSource(source: ReactiveNode) {}

    /**
     * 将自身及所有下游标记为"脏"。
     *
     * 短路保护：如果已标记为脏则直接返回，避免在依赖图中重复传播。
     * 递归向下游传播：每个 observer 也调用其 [markDirty]，形成级联。
     *
     * [SignalNode] 不调用自己的 [markDirty]——它在 [SignalNode.write] 中直接迭代
     * observers 并调用各 observer 的 [markDirty]，因为信号自身永远是"干净"的。
     *
     * [MemoNode] 覆写以在 eager 模式下立即重算。
     * [EffectNode] 覆写以将自身加入待执行队列。
     */
    open fun markDirty() {
        if (dirty) return
        dirty = true
        // 迭代副本：下游 markDirty 可能触发 recompute → cleanupSources
        // 从而修改本节点的 observers 集合，避免 ConcurrentModificationException
        for (observer in observers.toList()) {
            observer.markDirty()
        }
    }
}

// ============================================================================
// SignalNode<T> — 数据源节点（可写）
// ============================================================================

/**
 * 响应式数据源节点。
 *
 * 代表一个可读写的原子值。当值发生变化时，自动通知所有订阅者（MemoNode / EffectNode）
 * 标记脏并调度副作用执行。
 *
 * 相等性检查：[write] 中使用 Kotlin `==` 比较新旧值，相等则跳过传播。
 * JS 中基本类型按值比较，引用类型按引用比较。
 */
internal class SignalNode<T>(initialValue: T) : ReactiveNode() {

    var value: T = initialValue

    /**
     * 读取当前值，并在此过程中建立依赖追踪。
     *
     * 如果当前存在 [TrackingContext.currentObserver]（即正在某个 memo/effect 的
     * Lambda 中执行），则将当前 observer 注册为本节点的订阅者，同时通知 observer
     * 记录本节点为其上游来源。双向注册保证了依赖图的可遍历性。
     *
     * 如果不在任何追踪上下文中（普通读取），仅返回值，无副作用。
     */
    fun read(): T {
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)
            observer.addSource(this)
        }
        return value
    }

    /**
     * 写入新值，触发更新传播。
     *
     * 流程：
     * 1. 相等性检查：值未变则短路返回 false
     * 2. 更新内部值
     * 3. 遍历所有订阅者，调用其 [markDirty] 向下游传播脏标记
     * 4. 调用 [scheduleFlush] 调度 effect 微任务执行
     *
     * @return true 表示值确实发生了变化并触发了传播
     */
    fun write(newValue: T): Boolean {
        if (value == newValue) return false
        value = newValue
        // 迭代副本：eager MemoNode 的 markDirty → recompute → cleanupSources
        // 会修改本节点的 observers 集合，避免 ConcurrentModificationException
        for (observer in observers.toList()) {
            observer.markDirty()
        }
        scheduleFlush()
        return true
    }
}

// ============================================================================
// MemoNode<T> — 派生/计算节点（惰性或活性求值）
// ============================================================================

/**
 * 响应式派生/计算节点。
 *
 * 基于上游 signal 或其他 memo 的值进行计算。支持两种求值策略：
 * - **惰性（默认）**：仅在 [read] 被调用且 dirty 时重新计算
 * - **活性（eager = true）**：上游变化时立即重算并缓存新值，read 零延迟
 *
 * 关键设计：
 * - 每次 [recompute] 时会先清理旧的依赖关系（[cleanupSources]），然后执行计算 Lambda。
 *   这保证了条件分支导致依赖变化时，依赖图始终是最新的。
 * - 活性模式不需要内部 EffectNode：MemoNode 已在依赖图中间层，上游的 observers 集合
 *   已指向它，[markDirty] 覆写足以在收到通知时立即重算。
 * - 值存储使用 `T?` 以支持未初始化状态（构造后到首次计算之间）。
 *
 * @param compute 计算函数，在其中读取其他 signal/memo 的值以建立依赖
 * @param eager 是否使用活性求值策略
 */
internal class MemoNode<T>(
    private val compute: () -> T,
    private val eager: Boolean = false
) : ReactiveNode() {

    private val sources: MutableSet<ReactiveNode> = mutableSetOf()
    private var value: T? = null
    private var initialized: Boolean = false

    // --- 上游来源管理 ---

    override fun addSource(source: ReactiveNode) {
        sources.add(source)
    }

    /**
     * 清理所有上游依赖关系。
     * 从每个上游 source 的 observers 集合中移除自己，然后清空本地 sources。
     */
    private fun cleanupSources() {
        for (source in sources) {
            source.observers.remove(this)
        }
        sources.clear()
    }

    // --- 脏标记 ---

    /**
     * 覆写父类以支持 eager 模式。
     *
     * 1. 短路检查：已脏则直接返回
     * 2. 调用父类 [ReactiveNode.markDirty]：设 dirty=true + 递归标记下游
     * 3. 如果 eager 且已初始化：立即重算并缓存新值，清除 dirty 标记
     *
     * 惰性模式下 dirty 保持 true，直到下次 [read] 时才触发 [recompute]。
     */
    override fun markDirty() {
        if (dirty) return
        super.markDirty()
        if (eager && initialized) {
            recompute()
        }
    }

    // --- 读取值 ---

    /**
     * 读取派生值。
     *
     * 流程：
     * 1. 依赖追踪：如果当前有 observer，注册双向依赖关系
     * 2. 如果 dirty 或尚未初始化，重新计算
     * 3. 返回缓存值
     */
    fun read(): T {
        // 依赖追踪
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)
            observer.addSource(this)
        }

        // 惰性求值：仅在需要时计算
        if (dirty || !initialized) {
            recompute()
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    // --- 核心计算逻辑 ---

    /**
     * 重新执行计算 Lambda。
     *
     * 步骤：
     * 1. [cleanupSources] — 断开旧的依赖关系
     * 2. 保存并替换 [TrackingContext.currentObserver] 为自身
     * 3. 执行 [compute] Lambda——期间读取的任何 signal/memo 会将 this 注册为订阅者
     * 4. 更新缓存值、清除 dirty、标记已初始化
     * 5. 在 finally 中恢复 currentObserver
     */
    private fun recompute() {
        cleanupSources()

        val prev = TrackingContext.currentObserver
        TrackingContext.currentObserver = this
        try {
            val newValue = compute()
            value = newValue
            dirty = false
            initialized = true
        } finally {
            TrackingContext.currentObserver = prev
        }
    }

    // --- 初始化与清理 ---

    /**
     * 初始化 eager 模式。
     * 立即执行首次计算，建立对上游信号的依赖关系。
     * 之后上游信号变化 → [markDirty] → 因为 eager && initialized → 立即 [recompute]。
     */
    internal fun initEager() {
        recompute()
    }

    /**
     * 清理节点资源。
     * 断开所有上游依赖，清空下游订阅者列表。
     */
    internal fun disposeNode() {
        cleanupSources()
        observers.clear()
    }
}

// ============================================================================
// EffectNode — 副作用节点（响应式图的叶子节点）
// ============================================================================

/**
 * 响应式副作用节点。
 *
 * Effect 是依赖图的叶子：它依赖上游信号/memo，但没有下游订阅者。
 * 当上游数据变化时，effect 不立即执行，而是加入 [TrackingContext.pendingEffects]
 * 队列，由调度器在微任务中批量执行。
 *
 * 设计要点：
 * - [markDirty] 不递归传播（叶子无下游），只将自身入队
 * - [execute] 在微任务中由调度器调用，执行前清理旧依赖、执行后重建新依赖
 * - [disposeNode] 清理所有依赖引用并从待执行队列中移除
 */
internal class EffectNode(private val fn: () -> Unit) : ReactiveNode() {

    private val sources: MutableSet<ReactiveNode> = mutableSetOf()
    private var disposed: Boolean = false

    override fun addSource(source: ReactiveNode) {
        sources.add(source)
    }

    private fun cleanupSources() {
        for (source in sources) {
            source.observers.remove(this)
        }
        sources.clear()
    }

    /**
     * Effect 是叶子节点，markDirty 不向下游传播。
     * 仅将自身加入待执行队列，并确保微任务已调度。
     */
    override fun markDirty() {
        if (disposed) return
        TrackingContext.pendingEffects.add(this)
        scheduleFlush()
    }

    /**
     * 执行副作用回调。
     *
     * 流程：
     * 1. 已释放则跳过
     * 2. 清理旧依赖关系
     * 3. 设自身为 currentObserver
     * 4. 执行用户回调——期间读取的 signal/memo 会将此 effect 注册为订阅者
     * 5. 恢复 currentObserver
     */
    fun execute() {
        if (disposed) return
        cleanupSources()

        val prev = TrackingContext.currentObserver
        TrackingContext.currentObserver = this
        try {
            fn()
        } finally {
            TrackingContext.currentObserver = prev
        }
    }

    /**
     * 释放 effect：清理所有依赖引用，标记为已释放，从待执行队列移除。
     * 幂等操作——重复调用安全。
     */
    fun disposeNode() {
        if (disposed) return
        disposed = true
        cleanupSources()
        TrackingContext.pendingEffects.remove(this)
    }
}
