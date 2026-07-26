package com.thestar.reactive

import kotlin.reflect.KProperty

interface Disposable {
    fun dispose()
}

internal interface Observables<T : Any?> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}

internal interface Writable<T : Any?> : Observables<T> {
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

abstract class BasicObservables<T : Any?> internal constructor(
    internal open val basicNode: ObservedNode<T>
) : Observables<T> {
    internal open val value: T
        get() = basicNode.read()
    
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

abstract class BasicWritable<T : Any?> internal constructor(
    override val basicNode: SignalNode<T>
) : BasicObservables<T>(basicNode), Writable<T> {
    override var value: T
        get() = basicNode.read()
        set(newValue) {
            basicNode.write(newValue)
        }
    
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

class Signal<T : Any?> internal constructor(
    node: SignalNode<T>
) : BasicWritable<T>(node)

class Memo<T : Any?> internal constructor(
    private val node: MemoNode<T>
) : BasicObservables<T>(node), Disposable {
    override fun dispose() {
        node.dispose()
    }
}

class Effect internal constructor(
    private val node: EffectNode
) : Disposable {
    override fun dispose() {
        node.dispose()
    }
}