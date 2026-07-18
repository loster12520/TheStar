package com.thestar.reactive


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
    var currentObserver: ObserverNode? = null
    
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

internal interface ReactiveNode {
    val observers: MutableSet<ReactiveNode>
    var dirty: Boolean
    
    fun markDirty() {
        if (dirty) return
        dirty = true
        // 迭代副本：下游 markDirty 可能触发 recompute → cleanupSources
        // 从而修改本节点的 observers 集合，避免 ConcurrentModificationException
        for (observer in observers.toList()) {
            observer.markDirty()
        }
    }
}

internal interface ObservedNode<T> : ReactiveNode {
    var value: T?
    
    fun read(): T {
        val observer = TrackingContext.currentObserver
        if (observer != null) {
            observers.add(observer)
            observer.addSource(this)
        }
        return value ?: throw this::class.uninitializedException()
    }
    
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
    val sources: MutableSet<ReactiveNode>
    
    fun addSource(source: ReactiveNode) {
        sources.add(source)
    }
    
    fun cleanupSources() {
        for (source in sources) {
            source.observers.remove(this)
        }
        sources.clear()
    }
}

internal open class BasicNode : ReactiveNode {
    override val observers: MutableSet<ReactiveNode> = mutableSetOf()
    override var dirty: Boolean = false
}

internal class SignalNode<T>(initialValue: T) : BasicNode(), ObservedNode<T> {
    override var value: T? = initialValue
}

internal class MemoNode<T>(
    private val compute: () -> T,
    private val eager: Boolean = false
) : BasicNode(), ObservedNode<T>, ObserverNode {
    override val sources: MutableSet<ReactiveNode> = mutableSetOf()
    override var value: T? = null
}

internal class EffectNode(private val fn: () -> Unit) : BasicNode(), ObserverNode {
    override val sources: MutableSet<ReactiveNode> = mutableSetOf()
}