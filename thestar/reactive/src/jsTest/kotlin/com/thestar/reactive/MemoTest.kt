package com.thestar.reactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Memo 单元测试。
 *
 * 覆盖 [memo] 工厂函数与 [Memo] 类的全部行为：
 * - 惰性/eager 计算模式
 * - 多依赖、链式依赖、无依赖
 * - 值不变时的优化行为
 * - 回调异常时的依赖回滚
 * - dispose 生命周期
 */
class MemoTest {

    // ========================================================================
    // 基本派生计算
    // ========================================================================

    @Test
    fun `memo computes derived value`() {
        val count = signal(2)
        val double = memo { count.value * 2 }
        assertEquals(4, double.value)
    }

    @Test
    fun `memo updates when upstream changes`() {
        val count = signal(2)
        val double = memo { count.value * 2 }
        assertEquals(4, double.value)
        count.value = 5
        assertEquals(10, double.value)
    }

    @Test
    fun `memo with string derivation`() {
        val name = signal("Alice")
        val greeting = memo { "Hello, ${name.value}!" }
        assertEquals("Hello, Alice!", greeting.value)
        name.value = "Bob"
        assertEquals("Hello, Bob!", greeting.value)
    }

    @Test
    fun `memo with list transformation`() {
        val numbers = signal(listOf(1, 2, 3))
        val doubled = memo { numbers.value.map { it * 2 } }
        assertEquals(listOf(2, 4, 6), doubled.value)
    }

    @Test
    fun `memo with boolean logic`() {
        val flag = signal(true)
        val negated = memo { !flag.value }
        assertEquals(false, negated.value)
        flag.value = false
        assertEquals(true, negated.value)
    }

    // ========================================================================
    // 惰性求值（默认 eager = false）
    // ========================================================================

    @Test
    fun `memo is lazy - does not compute until first read`() {
        val count = signal(0)
        var computeCount = 0
        val derived = memo { computeCount++; count.value * 2 }
        // 惰性 memo 创建时不计算
        assertEquals(0, computeCount)
        // 首次读取触发计算
        assertEquals(0, derived.value)
        assertEquals(1, computeCount)
    }

    @Test
    fun `memo does not recompute when upstream changes but not read`() {
        val count = signal(0)
        var computeCount = 0
        val derived = memo { computeCount++; count.value * 2 }
        assertEquals(0, derived.value)
        assertEquals(1, computeCount)

        // 修改上游但不读取 derived
        count.value = 1
        assertEquals(1, computeCount) // computeCount 不变

        // 现在读取，触发重算
        assertEquals(2, derived.value)
        assertEquals(2, computeCount)
    }

    @Test
    fun `memo recomputes only once per upstream change`() {
        val count = signal(0)
        var computeCount = 0
        val derived = memo { computeCount++; count.value * 2 }

        assertEquals(0, derived.value)
        assertEquals(1, computeCount)
        assertEquals(0, derived.value) // 重复读取不重算（缓存命中）
        assertEquals(1, computeCount)

        count.value = 1
        count.value = 2 // 多次修改，但只有一次 dirty 标记
        assertEquals(4, derived.value)
        assertEquals(2, computeCount) // 只重算一次
    }

    // ========================================================================
    // Eager 模式
    // ========================================================================

    @Test
    fun `memo eager computes immediately on creation`() {
        val count = signal(1)
        var computeCount = 0
        val derived = memo(eager = true) { computeCount++; count.value * 2 }
        assertEquals(1, computeCount) // 创建时即计算
        assertEquals(2, derived.value)
        assertEquals(1, computeCount) // 读取不重复计算
    }

    @Test
    fun `memo eager recomputes immediately on upstream change`() {
        val count = signal(1)
        var computeCount = 0
        val derived = memo(eager = true) { computeCount++; count.value * 2 }
        assertEquals(1, computeCount)

        count.value = 2
        // eager: 立即重算
        assertEquals(2, computeCount)
        assertEquals(4, derived.value) // 读取零延迟
        assertEquals(2, computeCount)
    }

    @Test
    fun `memo eager recomputes on each upstream change`() {
        val count = signal(0)
        var computeCount = 0
        val derived = memo(eager = true) { computeCount++; count.value * 2 }

        assertEquals(1, computeCount)
        assertEquals(0, derived.value)

        count.value = 1
        assertEquals(2, computeCount)
        assertEquals(2, derived.value)

        count.value = 2
        assertEquals(3, computeCount)
        assertEquals(4, derived.value)
    }

    @Test
    fun `memo eager with same value does not propagate dirty downstream`() {
        val count = signal(2)
        var computeCount = 0
        // 上游 memo：结果不变时不应传播
        val constant = memo(eager = true) { computeCount++; count.value - count.value } // 始终为 0
        assertEquals(1, computeCount)

        var downstreamCompute = 0
        val downstream = memo { downstreamCompute++; constant.value * 2 }

        assertEquals(0, downstream.value)
        assertEquals(1, downstreamCompute)

        count.value = 5 // 上游变化但 constant 结果不变（仍是 0）
        // eager constant 重算了但值没变，不应传播到 downstream
        assertEquals(2, computeCount)
        // downstream 不应重算（因为 constant 值没变）
        assertEquals(1, downstreamCompute)
    }

    // ========================================================================
    // 多依赖
    // ========================================================================

    @Test
    fun `memo with multiple dependencies`() {
        val a = signal(1)
        val b = signal(2)
        val sum = memo { a.value + b.value }
        assertEquals(3, sum.value)

        a.value = 10
        assertEquals(12, sum.value)

        b.value = 20
        assertEquals(30, sum.value)
    }

    @Test
    fun `memo with three or more dependencies`() {
        val a = signal(1)
        val b = signal(2)
        val c = signal(3)
        val product = memo { a.value * b.value * c.value }
        assertEquals(6, product.value)

        a.value = 2
        assertEquals(12, product.value)
        b.value = 3
        assertEquals(18, product.value)
        c.value = 4
        assertEquals(24, product.value)
    }

    // ========================================================================
    // 链式 Memo
    // ========================================================================

    @Test
    fun `chained lazy memos`() {
        val count = signal(2)
        val double = memo { count.value * 2 }
        val quadruple = memo { double.value * 2 }
        assertEquals(8, quadruple.value)
        count.value = 3
        assertEquals(12, quadruple.value)
    }

    @Test
    fun `chained eager memos`() {
        val count = signal(2)
        var dCompute = 0
        var qCompute = 0
        val double = memo(eager = true) { dCompute++; count.value * 2 }
        val quadruple = memo(eager = true) { qCompute++; double.value * 2 }

        assertEquals(1, dCompute)
        assertEquals(1, qCompute)
        assertEquals(8, quadruple.value)

        count.value = 3
        // 两个 eager memo 都立即重算
        assertEquals(2, dCompute)
        assertEquals(2, qCompute)
        assertEquals(12, quadruple.value)
    }

    @Test
    fun `mixed lazy and eager chain`() {
        val count = signal(2)
        var eagerCompute = 0
        var lazyCompute = 0
        val eager = memo(eager = true) { eagerCompute++; count.value * 2 }
        val lazy = memo { lazyCompute++; eager.value * 2 }

        assertEquals(1, eagerCompute)
        assertEquals(0, lazyCompute) // lazy 还没读取

        assertEquals(8, lazy.value)
        assertEquals(1, lazyCompute)

        count.value = 3
        // eager 立即重算
        assertEquals(2, eagerCompute)
        assertEquals(1, lazyCompute) // lazy 仍然不变（未读取）
        // 读取 lazy，触发重算
        assertEquals(12, lazy.value)
        assertEquals(2, lazyCompute)
    }

    // ========================================================================
    // 无依赖 Memo
    // ========================================================================

    @Test
    fun `memo with no dependencies returns constant`() {
        val constant = memo { 42 }
        assertEquals(42, constant.value)
        // 重复读取始终返回缓存值
        assertEquals(42, constant.value)
    }

    @Test
    fun `memo with no dependencies computed only once`() {
        var computeCount = 0
        val constant = memo { computeCount++; 42 }
        assertEquals(42, constant.value)
        assertEquals(1, computeCount)
        assertEquals(42, constant.value)
        assertEquals(1, computeCount) // 未重复计算
    }

    // ========================================================================
    // 异常处理
    // ========================================================================

    @Test
    fun `memo callback throws on first computation`() {
        val memo = memo<Int> { throw RuntimeException("compute error") }
        val ex = assertFailsWith<RuntimeException> {
            memo.value
        }
        assertTrue(ex.message?.contains("compute error") == true)
    }

    @Test
    fun `memo callback throws on recompute - old dependencies are preserved`() {
        val count = signal(1)
        var shouldThrow = false
        val derived = memo {
            if (shouldThrow) throw RuntimeException("recompute error")
            count.value * 2
        }

        // 首次计算成功
        assertEquals(2, derived.value)

        // 下次重算抛异常
        shouldThrow = true
        count.value = 2
        val ex = assertFailsWith<RuntimeException> {
            derived.value
        }
        assertTrue(ex.message?.contains("recompute error") == true)
    }

    @Test
    fun `memo recovers after exception when upstream is fixed`() {
        val count = signal(1)
        var throwOnZero = false
        val derived = memo {
            if (throwOnZero && count.value == 0) throw RuntimeException("zero not allowed")
            count.value * 2
        }

        assertEquals(2, derived.value)

        // 触发异常
        throwOnZero = true
        count.value = 0
        assertFailsWith<RuntimeException> { derived.value }

        // 修复上游
        throwOnZero = false
        count.value = 5
        assertEquals(10, derived.value)
    }

    // ========================================================================
    // Dispose
    // ========================================================================

    @Test
    fun `memo dispose is idempotent`() {
        val count = signal(1)
        val m = memo { count.value * 2 }
        assertEquals(2, m.value)
        m.dispose()
        m.dispose() // 不应抛异常
    }

    @Test
    fun `memo value after dispose is still readable`() {
        val count = signal(1)
        val m = memo { count.value * 2 }
        assertEquals(2, m.value)
        m.dispose()
        assertEquals(2, m.value) // 缓存值仍可读
    }

    @Test
    fun `memo value after dispose does not update on upstream change`() {
        val count = signal(1)
        val m = memo { count.value * 2 }
        assertEquals(2, m.value)
        m.dispose()
        count.value = 5
        // dispose 后不再追踪上游变更，但缓存的旧值仍可读
        assertEquals(2, m.value)
    }

    @Test
    fun `eager memo dispose stops automatic recomputation`() {
        val count = signal(1)
        var computeCount = 0
        val m = memo(eager = true) { computeCount++; count.value * 2 }
        assertEquals(1, computeCount)
        assertEquals(2, m.value)

        m.dispose()
        count.value = 5
        // eager 但已 dispose，不应重算
        assertEquals(1, computeCount)
    }

    // ========================================================================
    // 类型与属性
    // ========================================================================

    @Test
    fun `memo returns Memo instance`() {
        val count = signal(1)
        val m = memo { count.value * 2 }
        assertTrue(m is Memo<Int>)
        assertNotNull(m)
    }

    @Test
    fun `memo is Disposable`() {
        val m = memo { 42 }
        assertTrue(m is Disposable)
    }

    // ========================================================================
    // 委托属性与 Memo
    // ========================================================================

    @Test
    fun `delegated memo read`() {
        val count = signal(2)
        val double by memo { count.value * 2 }
        assertEquals(4, double)
        count.value = 5
        assertEquals(10, double)
    }
}
