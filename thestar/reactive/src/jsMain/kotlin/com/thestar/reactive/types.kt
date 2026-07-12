package com.thestar.reactive

import kotlin.reflect.KProperty

class Signal<T : Any>(val data: T) {
    var value: T? = null
    
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = TODO()
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        TODO()
    }
}

class Effect {
    fun dispose() {
        TODO()
    }
}