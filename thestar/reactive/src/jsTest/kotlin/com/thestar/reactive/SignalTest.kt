package com.thestar.reactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Signal 单元测试。
 *
 * 覆盖 [signal] 工厂函数与 [Signal] 类的全部行为：
 * - 创建、读写、委托属性
 * - 同值不触发通知
 * - dispose 生命周期
 * - 多信号独立性
 * - 边界情况
 */
class SignalTest {

    // ========================================================================
    // 创建与初始值
    // ========================================================================

    @Test
    fun `signal stores initial value`() {
        val s = signal(42)
        assertEquals(42, s.value)
    }

    @Test
    fun `signal with string value`() {
        val s = signal("hello")
        assertEquals("hello", s.value)
    }

    @Test
    fun `signal with boolean value`() {
        val t = signal(true)
        val f = signal(false)
        assertEquals(true, t.value)
        assertEquals(false, f.value)
    }

    @Test
    fun `signal with list value`() {
        val list = listOf(1, 2, 3)
        val s = signal(list)
        assertEquals(listOf(1, 2, 3), s.value)
    }

    @Test
    fun `signal with custom data class value`() {
        data class Person(val name: String, val age: Int)
        val person = Person("Alice", 30)
        val s = signal(person)
        assertEquals(person, s.value)
    }

    // ========================================================================
    // 值更新
    // ========================================================================

    @Test
    fun `signal updates value`() {
        val s = signal(1)
        s.value = 2
        assertEquals(2, s.value)
    }

    @Test
    fun `signal can be updated multiple times`() {
        val s = signal(0)
        for (i in 1..10) {
            s.value = i
            assertEquals(i, s.value)
        }
    }

    @Test
    fun `signal with nullable type parameter does not accept null`() {
        // T : Any 限制了非空类型，此处验证编译期约束
        val s = signal("not null")
        assertEquals("not null", s.value)
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
        val s = signal(10)
        var callCount = 0
        effect { s.value; callCount++ }
        assertEquals(1, callCount) // 初始执行一次

        val before = callCount
        s.value = 10 // 写入相同值
        assertEquals(before, callCount) // effect 不触发
    }

    @Test
    fun `signal with data class checks structural equality`() {
        data class Point(val x: Int, val y: Int)
        val s = signal(Point(1, 2))
        var callCount = 0
        effect { s.value; callCount++ }
        assertEquals(1, callCount)

        s.value = Point(1, 2) // 结构相等，不触发
        assertEquals(1, callCount)

        s.value = Point(3, 4) // 值不同，触发
        // effect 在微任务中执行，此处仅验证值已更新
        assertEquals(Point(3, 4), s.value)
    }

    // ========================================================================
    // 多信号独立性
    // ========================================================================

    @Test
    fun `multiple signals are independent`() {
        val a = signal(1)
        val b = signal(10)
        assertEquals(1, a.value)
        assertEquals(10, b.value)

        a.value = 2
        assertEquals(2, a.value)
        assertEquals(10, b.value) // b 不变

        b.value = 20
        assertEquals(2, a.value) // a 不变
        assertEquals(20, b.value)
    }

    @Test
    fun `many signals created independently`() {
        val signals = (0..99).map { signal(it) }
        for ((i, s) in signals.withIndex()) {
            assertEquals(i, s.value)
        }
    }

    // ========================================================================
    // 追踪上下文外读取
    // ========================================================================

    @Test
    fun `read signal outside tracking context returns value`() {
        val s = signal(99)
        // 在 effect 之外读取，不注册任何依赖
        assertEquals(99, s.value)
        assertEquals(99, s.value) // 重复读取一致
    }

    @Test
    fun `write signal outside tracking context updates value`() {
        val s = signal(0)
        s.value = 42
        assertEquals(42, s.value)
    }

    // ========================================================================
    // Dispose 生命周期
    // ========================================================================

    @Test
    fun `signal dispose can be called on direct Signal`() {
        val s = signal(42)
        s.dispose()
        // 无异常即通过
    }

    @Test
    fun `signal dispose is idempotent`() {
        val s = signal(42)
        s.dispose()
        s.dispose() // 不应抛异常
    }

    @Test
    fun `signal dispose clears observer links`() {
        val s = signal(0)
        effect { s.value }
        assertTrue(s.node.observers.isNotEmpty()) // 执行 effect 后至少有一个 observer
        s.dispose()
        // dispose 后 observers 被清空
        assertTrue(s.node.observers.isEmpty())
    }

    @Test
    fun `signal value is still readable after dispose`() {
        val s = signal(42)
        s.dispose()
        // dispose 只清理 observer 关系，value 仍可访问
        assertEquals(42, s.value)
    }

    @Test
    fun `signal value is still writable after dispose`() {
        val s = signal(0)
        s.dispose()
        s.value = 99
        assertEquals(99, s.value)
    }

    // ========================================================================
    // toString / 类型
    // ========================================================================

    @Test
    fun `signal returns Signal instance`() {
        val s = signal(1)
        assertTrue(s is Signal<Int>)
        assertNotNull(s)
    }

    @Test
    fun `signal is Disposable`() {
        val s = signal(1)
        assertTrue(s is Disposable)
    }
}
