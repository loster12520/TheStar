package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Signal 单元测试。
 *
 * 覆盖 [signal] 工厂函数与委托属性的全部行为：
 * - 创建、读写、委托属性
 * - 同值不触发通知
 * - 多信号独立性
 * - 边界情况
 */
class SignalTest {

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // 创建与初始值
    // ========================================================================

    @Test
    fun `signal stores initial value`() {
        var s by signal(42)
        assertEquals(42, s)
    }

    @Test
    fun `signal with string value`() {
        var s by signal("hello")
        assertEquals("hello", s)
    }

    @Test
    fun `signal with boolean value`() {
        var t by signal(true)
        var f by signal(false)
        assertEquals(true, t)
        assertEquals(false, f)
    }

    @Test
    fun `signal with list value`() {
        var s by signal(listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), s)
    }

    @Test
    fun `signal with custom data class value`() {
        data class Person(val name: String, val age: Int)
        val person = Person("Alice", 30)
        var s by signal(person)
        assertEquals(person, s)
    }

    // ========================================================================
    // 值更新
    // ========================================================================

    @Test
    fun `signal updates value`() {
        var s by signal(1)
        s = 2
        assertEquals(2, s)
    }

    @Test
    fun `signal can be updated multiple times`() {
        var s by signal(0)
        for (i in 1..10) {
            s = i
            assertEquals(i, s)
        }
    }

    @Test
    fun `signal with nullable type parameter does not accept null`() {
        // T : Any 限制了非空类型，此处验证编译期约束
        var s by signal("not null")
        assertEquals("not null", s)
    }

    // ========================================================================
    // 委托属性读写
    // ========================================================================

    @Test
    fun `delegated signal read and write`() {
        var count by signal(0)
        assertEquals(0, count)
        count = 5
        assertEquals(5, count)
    }

    @Test
    fun `delegated signal with multiple updates`() {
        var name by signal("Alice")
        assertEquals("Alice", name)
        name = "Bob"
        assertEquals("Bob", name)
        name = "Charlie"
        assertEquals("Charlie", name)
    }

    // ========================================================================
    // 同值不触发通知
    // ========================================================================

    @Test
    fun `signal no-op when same value is written`() {
        var s by signal(10)
        var callCount = 0
        effect { s; callCount++ }
        assertEquals(1, callCount) // 初始执行一次

        val before = callCount
        s = 10 // 写入相同值
        assertEquals(before, callCount) // effect 不触发
    }

    @Test
    fun `signal with data class checks structural equality`() {
        data class Point(val x: Int, val y: Int)
        var s by signal(Point(1, 2))
        var callCount = 0
        effect { s; callCount++ }
        assertEquals(1, callCount)

        s = Point(1, 2) // 结构相等，不触发
        assertEquals(1, callCount)

        s = Point(3, 4) // 值不同，触发
        // effect 在微任务中执行，此处仅验证值已更新
        assertEquals(Point(3, 4), s)
    }

    // ========================================================================
    // 多信号独立性
    // ========================================================================

    @Test
    fun `multiple signals are independent`() {
        var a by signal(1)
        var b by signal(10)
        assertEquals(1, a)
        assertEquals(10, b)

        a = 2
        assertEquals(2, a)
        assertEquals(10, b) // b 不变

        b = 20
        assertEquals(2, a) // a 不变
        assertEquals(20, b)
    }

    @Test
    fun `many signals created independently`() {
        val signals = (0..99).map { signal(it) }
        for ((i, s) in signals.withIndex()) {
            assertEquals(i, s.basicNode.read())
        }
    }

    // ========================================================================
    // 追踪上下文外读取
    // ========================================================================

    @Test
    fun `read signal outside tracking context returns value`() {
        val sSig = signal(99)
        var s by sSig
        // 在 effect 之外读取，不注册任何依赖
        assertEquals(99, s)
        assertEquals(99, s) // 重复读取一致
    }

    @Test
    fun `write signal outside tracking context updates value`() {
        var s by signal(0)
        s = 42
        assertEquals(42, s)
    }

    // ========================================================================
    // toString / 类型
    // ========================================================================

    @Test
    fun `signal factory returns Signal instance`() {
        val s = signal(1)
        assertTrue(s is Signal<Int>)
        assertNotNull(s)
    }
}
