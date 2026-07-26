package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Memo 单元测试。
 *
 * 覆盖 [memo] 工厂函数与委托属性的全部行为：
 * - 惰性/eager 计算模式
 * - 多依赖、链式依赖、无依赖
 * - 值不变时的优化行为
 * - 回调异常时的依赖回滚
 * - dispose 生命周期
 */
class MemoTest {

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // 基本派生计算
    // ========================================================================

    @Test
    fun `memo computes derived value`() {
        var count by signal(2)
        val double by memo { count * 2 }
        assertEquals(4, double)
    }

    @Test
    fun `memo updates when upstream changes`() {
        var count by signal(2)
        val double by memo { count * 2 }
        assertEquals(4, double)
        count = 5
        assertEquals(10, double)
    }

    @Test
    fun `memo with string derivation`() {
        var name by signal("Alice")
        val greeting by memo { "Hello, ${name}!" }
        assertEquals("Hello, Alice!", greeting)
        name = "Bob"
        assertEquals("Hello, Bob!", greeting)
    }

    @Test
    fun `memo with list transformation`() {
        var numbers by signal(listOf(1, 2, 3))
        val doubled by memo { numbers.map { it * 2 } }
        assertEquals(listOf(2, 4, 6), doubled)
    }

    @Test
    fun `memo with boolean logic`() {
        var flag by signal(true)
        val negated by memo { !flag }
        assertEquals(false, negated)
        flag = false
        assertEquals(true, negated)
    }

    // ========================================================================
    // 惰性求值（默认 eager = false）
    // ========================================================================

    @Test
    fun `memo is lazy - does not compute until first read`() {
        var count by signal(0)
        var computeCount = 0
        val derived by memo { computeCount++; count * 2 }
        // 惰性 memo 创建时不计算
        assertEquals(0, computeCount)
        // 首次读取触发计算
        assertEquals(0, derived)
        assertEquals(1, computeCount)
    }

    @Test
    fun `memo does not recompute when upstream changes but not read`() {
        var count by signal(0)
        var computeCount = 0
        val derived by memo { computeCount++; count * 2 }
        assertEquals(0, derived)
        assertEquals(1, computeCount)

        // 修改上游但不读取 derived
        count = 1
        assertEquals(1, computeCount) // computeCount 不变

        // 现在读取，触发重算
        assertEquals(2, derived)
        assertEquals(2, computeCount)
    }

    @Test
    fun `memo recomputes only once per upstream change`() {
        var count by signal(0)
        var computeCount = 0
        val derived by memo { computeCount++; count * 2 }

        assertEquals(0, derived)
        assertEquals(1, computeCount)
        assertEquals(0, derived) // 重复读取不重算（缓存命中）
        assertEquals(1, computeCount)

        count = 1
        count = 2 // 多次修改，但只有一次 dirty 标记
        assertEquals(4, derived)
        assertEquals(2, computeCount) // 只重算一次
    }

    // ========================================================================
    // Eager 模式
    // ========================================================================

    @Test
    fun `memo eager computes immediately on creation`() {
        var count by signal(1)
        var computeCount = 0
        val derived by memo(eager = true) { computeCount++; count * 2 }
        assertEquals(1, computeCount) // 创建时即计算
        assertEquals(2, derived)
        assertEquals(1, computeCount) // 读取不重复计算
    }

    @Test
    fun `memo eager recomputes immediately on upstream change`() {
        var count by signal(1)
        var computeCount = 0
        val derived by memo(eager = true) { computeCount++; count * 2 }
        assertEquals(1, computeCount)

        count = 2
        // eager: 立即重算
        assertEquals(2, computeCount)
        assertEquals(4, derived) // 读取零延迟
        assertEquals(2, computeCount)
    }

    @Test
    fun `memo eager recomputes on each upstream change`() {
        var count by signal(0)
        var computeCount = 0
        val derived by memo(eager = true) { computeCount++; count * 2 }

        assertEquals(1, computeCount)
        assertEquals(0, derived)

        count = 1
        assertEquals(2, computeCount)
        assertEquals(2, derived)

        count = 2
        assertEquals(3, computeCount)
        assertEquals(4, derived)
    }

    @Test
    fun `memo eager with same value does not propagate dirty downstream`() {
        var count by signal(2)
        var computeCount = 0
        // 上游 memo：结果不变时不应传播
        val constantObj = memo(eager = true) { computeCount++; count - count } // 始终为 0
        val constant by constantObj
        assertEquals(1, computeCount)

        var downstreamCompute = 0
        val downstream by memo { downstreamCompute++; constant * 2 }

        assertEquals(0, downstream)
        assertEquals(1, downstreamCompute)

        count = 5 // 上游变化但 constant 结果不变（仍是 0）
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
    fun `memo with three or more dependencies`() {
        var a by signal(1)
        var b by signal(2)
        var c by signal(3)
        val product by memo { a * b * c }
        assertEquals(6, product)

        a = 2
        assertEquals(12, product)
        b = 3
        assertEquals(18, product)
        c = 4
        assertEquals(24, product)
    }

    // ========================================================================
    // 链式 Memo
    // ========================================================================

    @Test
    fun `chained lazy memos`() {
        var count by signal(2)
        val double by memo { count * 2 }
        val quadruple by memo { double * 2 }
        assertEquals(8, quadruple)
        count = 3
        assertEquals(12, quadruple)
    }

    @Test
    fun `chained eager memos`() {
        var count by signal(2)
        var dCompute = 0
        var qCompute = 0
        val double by memo(eager = true) { dCompute++; count * 2 }
        val quadruple by memo(eager = true) { qCompute++; double * 2 }

        assertEquals(1, dCompute)
        assertEquals(1, qCompute)
        assertEquals(8, quadruple)

        count = 3
        // 两个 eager memo 都立即重算
        assertEquals(2, dCompute)
        assertEquals(2, qCompute)
        assertEquals(12, quadruple)
    }

    @Test
    fun `mixed lazy and eager chain`() {
        var count by signal(2)
        var eagerCompute = 0
        var lazyCompute = 0
        val eager by memo(eager = true) { eagerCompute++; count * 2 }
        val lazy by memo { lazyCompute++; eager * 2 }

        assertEquals(1, eagerCompute)
        assertEquals(0, lazyCompute) // lazy 还没读取

        assertEquals(8, lazy)
        assertEquals(1, lazyCompute)

        count = 3
        // eager 立即重算
        assertEquals(2, eagerCompute)
        assertEquals(1, lazyCompute) // lazy 仍然不变（未读取）
        // 读取 lazy，触发重算
        assertEquals(12, lazy)
        assertEquals(2, lazyCompute)
    }

    // ========================================================================
    // 无依赖 Memo
    // ========================================================================

    @Test
    fun `memo with no dependencies returns constant`() {
        val constant by memo { 42 }
        assertEquals(42, constant)
        // 重复读取始终返回缓存值
        assertEquals(42, constant)
    }

    @Test
    fun `memo with no dependencies computed only once`() {
        var computeCount = 0
        val constant by memo { computeCount++; 42 }
        assertEquals(42, constant)
        assertEquals(1, computeCount)
        assertEquals(42, constant)
        assertEquals(1, computeCount) // 未重复计算
    }

    // ========================================================================
    // 异常处理
    // ========================================================================

    @Test
    fun `memo callback throws on first computation`() {
        val brokenMemo = memo<Int> { throw RuntimeException("compute error") }
        val ex = assertFailsWith<RuntimeException> {
            brokenMemo.basicNode.read()
        }
        assertTrue(ex.message?.contains("compute error") == true)
    }

    @Test
    fun `memo callback throws on recompute - old dependencies are preserved`() {
        var count by signal(1)
        var shouldThrow = false
        val derivedObj = memo {
            if (shouldThrow) throw RuntimeException("recompute error")
            count * 2
        }
        val derived by derivedObj

        // 首次计算成功
        assertEquals(2, derived)

        // 下次重算抛异常
        shouldThrow = true
        count = 2
        val ex = assertFailsWith<RuntimeException> {
            derivedObj.basicNode.read()
        }
        assertTrue(ex.message?.contains("recompute error") == true)
    }

    @Test
    fun `memo recovers after exception when upstream is fixed`() {
        var count by signal(1)
        var throwOnZero = false
        val derivedObj = memo {
            if (throwOnZero && count == 0) throw RuntimeException("zero not allowed")
            count * 2
        }
        val derived by derivedObj

        assertEquals(2, derived)

        // 触发异常
        throwOnZero = true
        count = 0
        assertFailsWith<RuntimeException> { derivedObj.basicNode.read() }

        // 修复上游
        throwOnZero = false
        count = 5
        assertEquals(10, derived)
    }

    // ========================================================================
    // Dispose
    // ========================================================================

    @Test
    fun `memo dispose is idempotent`() {
        var count by signal(1)
        val m = memo { count * 2 }
        assertEquals(2, m.basicNode.read())
        m.dispose()
        m.dispose() // 不应抛异常
    }

    @Test
    fun `memo value after dispose is still readable`() {
        var count by signal(1)
        val m = memo { count * 2 }
        assertEquals(2, m.basicNode.read())
        m.dispose()
        assertEquals(2, m.basicNode.read()) // 缓存值仍可读
    }

    @Test
    fun `memo value after dispose does not update on upstream change`() {
        var count by signal(1)
        val m = memo { count * 2 }
        assertEquals(2, m.basicNode.read())
        m.dispose()
        count = 5
        // dispose 后不再追踪上游变更，但缓存的旧值仍可读
        assertEquals(2, m.basicNode.read())
    }

    @Test
    fun `eager memo dispose stops automatic recomputation`() {
        var count by signal(1)
        var computeCount = 0
        val m = memo(eager = true) { computeCount++; count * 2 }
        assertEquals(1, computeCount)
        assertEquals(2, m.basicNode.read())

        m.dispose()
        count = 5
        // eager 但已 dispose，不应重算
        assertEquals(1, computeCount)
    }

    // ========================================================================
    // 类型与属性
    // ========================================================================

    @Test
    fun `memo returns Memo instance`() {
        var count by signal(1)
        val m = memo { count * 2 }
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
        var count by signal(2)
        val double by memo { count * 2 }
        assertEquals(4, double)
        count = 5
        assertEquals(10, double)
    }
}
