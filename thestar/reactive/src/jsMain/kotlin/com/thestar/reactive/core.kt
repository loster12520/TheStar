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
    return try {
        callback()
    } finally {
        TrackingContext.currentObserver = prev
    }
}

internal interface ReactiveNode {
    fun markDirty()
}

internal interface ObservedNode<T> : ReactiveNode {
    val observers: MutableSet<ObserverNode>
    var value: T?
    var dirty: Boolean
    
    fun read(): T {
        val result = value ?: throw this::class.uninitializedException()
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)
            observer.addSource(this)
        }
        return result
    }
    
    fun dirtyObservers() {
        for (observer in observers.toList()) {
            try {
                observer.markDirty()
            } catch (e: Throwable) {
                logger.error(e) { "Error marking observer dirty" }
            }
        }
    }
    
    fun cleanupObservers() {
        for (observer in observers.toList()) {
            observer.sources.remove(this)
        }
        observers.clear()
    }
    
    override fun markDirty() {
        if (dirty) return
        dirty = true
        
        dirtyObservers()
    }
}

internal interface WritableNode<T> : ObservedNode<T> {
    fun write(newValue: T) {
        if (value == newValue) {
            logger.debug { "Writing new value already added" }
            return
        }
        value = newValue
        
        dirtyObservers()
        scheduleFlush()
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
    
    fun <T> cleanupSourcesSafety(callback: () -> T): T {
        val oldSources = sources.toSet()
        sources.clear()
        
        val result = try {
            callback()
        } catch (e: Throwable) {
            val newSources = sources.toSet()
            sources.clear()
            sources.addAll(oldSources)
            for (newSource in newSources) {
                if (newSource !in oldSources) {
                    newSource.observers.remove(this)
                }
            }
            throw e
        }
        
        for (oldSource in oldSources) {
            if (oldSource !in sources) {
                oldSource.observers.remove(this)
            }
        }
        return result
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
    private var disposed: Boolean = false
    
    init {
        // 如果是eager模式，则立刻更新一下值
        if (eager) {
            recompute()
        }
    }
    
    override fun markDirty() {
        if (dirty || disposed) return
        if (eager && initialized) {
            dirty = true
            try {
                val unchanged = recompute()
                if (!unchanged) {
                    dirtyObservers()
                }
            } catch (e: Throwable) {
                // 显示抛出，这里不修正dirty以便重新读取的时候可以再次进行更新
                throw e
            }
        } else {
            super.markDirty()
        }
        
    }
    
    override fun read(): T {
        if (disposed) return value ?: throw this::class.uninitializedException()
        if (dirty || !initialized) {
            recompute()
        }
        return super.read()
    }
    
    private fun recompute(): Boolean =
        cleanupSourcesSafety {
            changeCurrentObserver {
                val newValue = callback()
                val compare = value == newValue
                value = newValue
                compare
            }
        }.also {
            initialized = true
            dirty = false
        }
    
    override fun dispose() {
        if (disposed) return
        disposed = true
        cleanupSources()
        cleanupObservers()
    }
}

internal class EffectNode(
    private val callback: () -> Unit
) : BasicObserverNode(), ObserverNode, Disposable {
    private var executing: Boolean = false
    private var disposed: Boolean = false
    
    override fun markDirty() {
        if (disposed) return
        TrackingContext.pendingEffects.add(this)
        scheduleFlush()
    }
    
    fun execute() {
        if (disposed || executing) return
        executing = true
        try {
            cleanupSourcesSafety {
                changeCurrentObserver(callback)
            }
        } finally {
            executing = false
        }
    }
    
    override fun dispose() {
        if (disposed) return
        disposed = true
        cleanupSources()
        TrackingContext.pendingEffects.remove(this)
    }
}