package com.thestar.reactive

import com.thestar.reactive_ai.EffectNode

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
     * 用于在[ObservedNode.read]被调用时，能够获取到当前的观察者节点，并写入
     */
    var currentObserver: ObserverNode? = null
    var batchDepth: Int = 0
    val pendingEffects: MutableSet<EffectNode> = mutableSetOf()
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

internal interface ObserverNode : ReactiveNode {
    fun addSource(source: ReactiveNode)
    fun cleanupSources()
}

internal interface ObservedNode<T> : ReactiveNode {
    fun read(): T
    fun write(newValue: T): Boolean
}