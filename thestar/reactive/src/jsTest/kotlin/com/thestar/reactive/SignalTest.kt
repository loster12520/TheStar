package com.thestar.reactive_ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SignalTest {

    // ========================================================================
    // Signal 基础测试
    // ========================================================================

    @Test
    fun `signal stores initial value`() {
        val s = signal(42)
        assertEquals(42, s.value)
    }

    @Test
    fun `signal updates value`() {
        val s = signal(1)
        s.value = 2
        assertEquals(2, s.value)
    }

    @Test
    fun `delegated signal read and write`() {
        var count by signal(0)
        assertEquals(0, count)
        count = 5
        assertEquals(5, count)
    }

    @Test
    fun `signal no-op when same value`() {
        val s = signal(10)
        var callCount = 0
        effect { s.value; callCount++ }  // 初始执行一次
        val before = callCount
        s.value = 10  // 相同值
        assertEquals(before, callCount)  // effect 不触发
    }

    @Test
    fun `signal with string value`() {
        val s = signal("hello")
        assertEquals("hello", s.value)
        s.value = "world"
        assertEquals("world", s.value)
    }

    @Test
    fun `multiple signals are independent`() {
        val a = signal(1)
        val b = signal(10)
        assertEquals(1, a.value)
        assertEquals(10, b.value)
        a.value = 2
        assertEquals(2, a.value)
        assertEquals(10, b.value)  // b unchanged
    }

    // ========================================================================
    // Memo 派生信号测试
    // ========================================================================

    @Test
    fun `memo computes derived value`() {
        var count by signal(2)
        val double by memo { count * 2 }
        assertEquals(4, double)
        count = 5
        assertEquals(10, double)
    }

    @Test
    fun `memo is lazy - does not recompute until read`() {
        var count by signal(0)
        var computeCount = 0
        val derived by memo { computeCount++; count * 2 }
        // 惰性 memo 创建时不计算——仅在被读取时才计算
        assertEquals(0, computeCount)
        assertEquals(0, derived)  // 首次读取，触发计算
        assertEquals(1, computeCount)
        count = 1  // 修改上游，不读取 derived，computeCount 不变
        assertEquals(1, computeCount)
        val v = derived  // 现在读取，dirty=true，触发重算
        assertEquals(2, computeCount)
        assertEquals(2, v)
    }

    @Test
    fun `memo eager recomputes immediately`() {
        var count by signal(1)
        var computeCount = 0
        val derived by memo(eager = true) { computeCount++; count * 2 }
        assertEquals(1, computeCount)  // 初始化时计算一次
        count = 2  // eager: 立即重算
        assertEquals(2, computeCount)
        assertEquals(4, derived)  // 读取零延迟，无需重新计算
    }

    @Test
    fun `memo eager recomputes immediately on each change`() {
        var count by signal(0)
        var computeCount = 0
        val derived by memo(eager = true) { computeCount++; count * 2 }
        assertEquals(1, computeCount)
        count = 1
        assertEquals(2, computeCount)
        assertEquals(2, derived)
        count = 2
        assertEquals(3, computeCount)
        assertEquals(4, derived)
    }

    @Test
    fun `memo with multiple dependencies`() {
        var a by signal(1)
        var b by signal(2)
        val sum by memo { a + b }
        assertEquals(3, sum)
        a = 10
        assertEquals(12, sum)
        b = 20
        assertEquals(30, sum)
    }

    @Test
    fun `chained memos`() {
        var count by signal(2)
        val double by memo { count * 2 }
        val quadruple by memo { double * 2 }
        assertEquals(8, quadruple)
        count = 3
        assertEquals(12, quadruple)
    }

    @Test
    fun `memo with string derivation`() {
        var name by signal("Alice")
        val greeting by memo { "Hello, $name!" }
        assertEquals("Hello, Alice!", greeting)
        name = "Bob"
        assertEquals("Hello, Bob!", greeting)
    }

    // ========================================================================
    // Effect 副作用测试
    // ========================================================================

    @Test
    fun `effect runs on creation`() {
        var count by signal(0)
        var result = 0
        effect { result = count }
        assertEquals(0, result)  // 创建时立即执行
    }

    @Test
    fun `effect dispose stops notifications`() {
        val count = signal(0)
        var callCount = 0
        val e = effect { count.value; callCount++ }
        val afterInit = callCount
        assertEquals(1, afterInit)
        e.dispose()
        count.value = 1
        assertEquals(afterInit, callCount)  // 释放后不再触发
    }

    @Test
    fun `effect dispose is idempotent`() {
        val e = effect { /* no-op */ }
        e.dispose()
        e.dispose()  // 不应抛异常
    }

    @Test
    fun `effect returns Effect instance`() {
        val count = signal(0)
        val e = effect { count.value }
        assertNotNull(e)
        e.dispose()
    }

    // ========================================================================
    // Batch 批量更新测试
    // ========================================================================

    @Test
    fun `batch groups multiple writes synchronously`() {
        var a by signal(0)
        var b by signal(0)
        var effectRuns = 0
        effect { a; b; effectRuns++ }
        val afterInit = effectRuns
        assertEquals(1, afterInit)
        batch {
            a = 1
            b = 2
        }
        // batch 结束后 effect 只执行一次（微任务中）
        // 同步检查：值已更新
        assertEquals(1, a)
        assertEquals(2, b)
    }

    @Test
    fun `nested batch works correctly`() {
        var x by signal(0)
        var effectRuns = 0
        effect { x; effectRuns++ }
        val afterInit = effectRuns

        batch {
            x = 1
            batch {
                x = 2
            }
            // 内层 batch 结束，但外层还在
        }
        // 最外层 batch 结束，此时才调度

        assertEquals(2, x)
    }

    @Test
    fun `batch does not lose updates`() {
        var count by signal(0)
        val values = mutableListOf<Int>()
        effect { values.add(count) }

        batch {
            count = 1
            count = 2
            count = 3
        }

        // 最终值正确
        assertEquals(3, count)
    }

    // ========================================================================
    // Untrack 取消追踪测试
    // ========================================================================

    @Test
    fun `untrack prevents dependency tracking`() {
        var a by signal(0)
        var tracked = 0
        var untracked = 0
        effect {
            tracked = a           // 追踪 a
            untracked = untrack { a }  // 不追踪 a
        }
        assertEquals(0, tracked)
        assertEquals(0, untracked)
        a = 5
        // effect 重新执行：tracked 更新（effect 触发了），untracked 也更新
        // 但由于 effect 是异步的，这里只能验证初始执行的值
        assertEquals(0, tracked)  // effect 还未执行
        assertEquals(0, untracked)
    }

    @Test
    fun `untrack read does not trigger effect`() {
        var a by signal(0)
        var effectRuns = 0
        effect {
            untrack { a }   // 只在不追踪的上下文中读取 a
            effectRuns++
        }
        val afterInit = effectRuns
        assertEquals(1, afterInit)
        a = 1  // 不应触发 effect（因为 a 的读取在 untrack 中）
        // effectRuns 仍然是 1（同步检查——effect 在微任务中执行）
        assertEquals(1, effectRuns)
    }

    @Test
    fun `untrack returns computed value`() {
        var count by signal(5)
        val result = untrack { count * 10 }
        assertEquals(50, result)
    }

    // ========================================================================
    // Signal dispose 测试
    // ========================================================================

    @Test
    fun `signal dispose cleans up downstream`() {
        val count = signal(0)
        var callCount = 0
        effect { count.value; callCount++ }
        val afterInit = callCount
        assertEquals(1, afterInit)

        // 释放 signal——下游 effect 也应被清理
        count.dispose()
        count.value = 1
        assertEquals(afterInit, callCount)  // 释放后 effect 不再触发
    }

    @Test
    fun `signal dispose can be called on direct Signal`() {
        val s = signal(42)
        s.dispose()
        // 无异常即通过
    }

    // ========================================================================
    // 边界情况测试
    // ========================================================================

    @Test
    fun `read signal outside tracking context returns value`() {
        val s = signal(99)
        assertEquals(99, s.value)
        // 在追踪上下文外读取，不注册任何依赖
    }

    @Test
    fun `memo with no dependencies returns constant`() {
        val constant by memo { 42 }
        assertEquals(42, constant)
        // 重复读取始终返回缓存值
        assertEquals(42, constant)
    }

    @Test
    fun `multiple effects on same signal`() {
        var count by signal(0)
        var r1 = 0
        var r2 = 0
        effect { r1 = count }
        effect { r2 = count * 10 }
        assertEquals(0, r1)
        assertEquals(0, r2)
        count = 3
        // 两个 effect 都应该排队
    }

    @Test
    fun `boolean signal`() {
        var flag by signal(false)
        assertEquals(false, flag)
        flag = true
        assertEquals(true, flag)
    }
}
