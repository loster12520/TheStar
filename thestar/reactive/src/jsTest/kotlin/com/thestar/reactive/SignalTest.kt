package com.thestar.reactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SignalTest {
    
    @Test
    fun test() {
        println("SignalTest")
    }

//    @Test
//    fun `signal should store and retrieve a value via invoke`() {
//        val s = signal(42)
//        assertEquals(42, s())
//    }
//
//    @Test
//    fun `signal should update value via invoke setter`() {
//        val s = signal(1)
//        s(2)
//        assertEquals(2, s())
//    }
//
//    @Test
//    fun `delegated signal should support read and write`() {
//        var count by signal(0)
//        assertEquals(0, count)
//        count = 5
//        assertEquals(5, count)
//    }
//
//    @Test
//    fun `signal should notify subscribers on change`() {
//        val s = signal(0)
//        var notified = false
//        s.subscribe { notified = true }
//        s(1)
//        assertEquals(true, notified, "Subscriber should be notified after value change")
//    }
//
//    @Test
//    fun `signal should not notify if value is unchanged`() {
//        val s = signal(10)
//        var callCount = 0
//        s.subscribe { callCount++ }
//        s(10) // same value
//        assertEquals(0, callCount, "Should not notify when value is unchanged")
//    }
//
//    @Test
//    fun `signal subscribe should return disposable that unsubscribes`() {
//        val s = signal(0)
//        var callCount = 0
//        val dispose = s.subscribe { callCount++ }
//        s(1)
//        assertEquals(1, callCount)
//        dispose()
//        s(2)
//        assertEquals(1, callCount, "Should not be called after dispose")
//    }
}
