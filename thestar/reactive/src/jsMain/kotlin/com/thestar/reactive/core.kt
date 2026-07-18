package com.thestar.reactive

/**
 * 一个空[ObserverNode]常量,简化[untrack]的调用。
 *
 * @author lignting
 * @since 0.0.1
 * @see untrack
 */
internal val emptyObserver: ObserverNode? = null

/**
 * 全局追踪上下文单例。
 *
 * 负责维护全局统一的状态管理器，用于追踪响应式图中的依赖关系和调度副作用。
 *
 * @author lignting
 * @since 0.0.1
 */
internal object TrackingContext {
    
    /**
     * 当前正在创建的观察者节点。
     *
     * 用于在[ObservedNode.read]被调用时，能够获取到当前的观察者节点进行保存。
     *
     * @author lignting
     * @since 0.0.1
     */
    var currentObserver: ObserverNode? = emptyObserver
    
    /**
     * 目前[batch]的深度计数器。
     *
     * 进入或者退出[batch]时会增加或减少该计数器，用于判断是否需要立即调度副作用。[batchDepth] > 0 时而是积压到最外层 [batch] 结束时统一 [scheduleFlush]。
     *
     * @author lignting
     * @since 0.0.1
     */
    var batchDepth: Int = 0
    
    /**
     * 当前生效的[EffectNode]池。
     *
     * [effect]调用时，会往池内注入其[EffectNode]；调用[disposeNode]时，会从池内移除其[EffectNode]。
     *
     * @author lignting
     * @since 0.0.1
     */
    val pendingEffects: MutableSet<EffectNode> = mutableSetOf()
    
    /**
     * 是否有effectNode正在执行。
     *
     * 避免多个effect串行执行，导致重复调度。
     *
     * @author lignting
     * @since 0.0.1
     */
    var scheduled: Boolean = false
}

internal fun <T : Any> ObserverNode?.changeCurrentObserver(callback: () -> T): T {
    val prev = TrackingContext.currentObserver
    TrackingContext.currentObserver = this
    try {
        return callback()
    } finally {
        TrackingContext.currentObserver = prev
    }
}

internal interface ReactiveNode {
    fun markDirty(updateNext: Boolean = true)
}

internal interface ObservedNode<T> : ReactiveNode {
    val observers: MutableSet<ObserverNode>
    var value: T?
    var dirty: Boolean
    
    fun read(): T {
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)
            observer.addSource(this)
        }
        return value ?: throw this::class.uninitializedException()
    }
    
    override fun markDirty(updateNext: Boolean) {
        if (dirty) return
        dirty = true
        
        if (updateNext) {
            for (observer in observers.toList()) {
                observer.markDirty()
            }
        }
    }
}

internal interface WritableNode<T> : ObservedNode<T> {
    fun write(newValue: T): Boolean {
        if (value == newValue) return false
        value = newValue
        
        for (observer in observers.toList()) {
            observer.markDirty()
        }
        scheduleFlush()
        return true
    }
}

internal interface ObserverNode : ReactiveNode {
    val sources: MutableSet<ObservedNode<*>>
    
    fun addSource(source: ObservedNode<*>) {
        sources.add(source)
    }
    
    fun cleanupSources() {
        for (source in sources) {
            source.observers.remove(this)
        }
        sources.clear()
    }
}

internal abstract class BasicObservedNode<T> : ObservedNode<T> {
    override val observers: MutableSet<ObserverNode> = mutableSetOf()
    override var dirty: Boolean = false
}

internal abstract class BasicObserverNode : ObserverNode {
    override val sources: MutableSet<ObservedNode<*>> = mutableSetOf()
}

internal class SignalNode<T>(
    initialValue: T
) : BasicObservedNode<T>(), WritableNode<T> {
    override var value: T? = initialValue
}

internal class MemoNode<T>(
    private val eager: Boolean = false,
    private val callback: () -> T,
) : BasicObservedNode<T>(), ObserverNode, Disposable {
    override val sources: MutableSet<ObservedNode<*>> = mutableSetOf()
    override var value: T? = null
    private var initialized: Boolean = false
    
    init {
        // 如果是eager模式，则立刻更新一下值
        if (eager) {
            recompute()
        }
    }
    
    override fun markDirty(updateNext: Boolean) {
        if (dirty) return
        val recomputeResult = if (eager && initialized) {
            recompute()
        } else false
        super.markDirty(!recomputeResult)
    }
    
    override fun read(): T {
        if (dirty || !initialized) {
            recompute()
        }
        return super.read()
    }
    
    private fun recompute(): Boolean {
        cleanupSources()
        return changeCurrentObserver {
            val newValue = callback()
            val compare = value?.equals(newValue) ?: false
            value = newValue
            compare
        }.also {
            initialized = true
            dirty = false
        }
    }
    
    override fun dispose() {
        cleanupSources()
        observers.clear()
    }
}

internal class EffectNode(
    private val callback: () -> Unit
) : BasicObserverNode(), ObserverNode, Disposable {
    private var disposed: Boolean = false
    
    override fun markDirty(updateNext: Boolean) {
        if (disposed) return
        TrackingContext.pendingEffects.add(this)
        scheduleFlush()
    }
    
    fun execute() {
        if (disposed) return
        cleanupSources()
        
        changeCurrentObserver(callback)
    }
    
    override fun dispose() {
        if (disposed) return
        disposed = true
        cleanupSources()
        TrackingContext.pendingEffects.remove(this)
    }
}