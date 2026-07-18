package com.thestar.reactive

import kotlin.reflect.KProperty

interface Disposable {
    fun dispose()
}

internal interface Observables<T : Any> : Disposable {
    val value: T
    
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

internal interface Writable<T : Any> : Observables<T> {
    override var value: T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

abstract class Basic<T : Any> internal constructor(
    internal val basicNode: ObservedNode<T>
) : Observables<T> {
    override val value: T
        get() = basicNode.read()
    
    override fun dispose() {
        basicNode.observers.filter {
            it is Disposable
        }.forEach {
            (it as Disposable).dispose()
        }
        basicNode.observers.clear()
        
        if (basicNode is Disposable) {
            basicNode.dispose()
        }
    }
}

class Signal<T : Any> internal constructor(
    internal val node: SignalNode<T>
) : Basic<T>(node), Writable<T> {
    override var value: T
        get() = node.read()
        set(newValue) {
            node.write(newValue)
        }
}

class Memo<T : Any> internal constructor(
    internal val node: MemoNode<T>
) : Basic<T>(node), Observables<T> {
    override val value: T
        get() = node.read()
}

class Effect internal constructor(
    private val node: EffectNode
) : Disposable {
    override fun dispose() {
        node.dispose()
    }
}