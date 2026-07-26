package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 压力/性能测试。
 *
 * 验证 reactive 系统在极端条件下的性能和正确性：
 * - 大量信号创建
 * - 深链 memo 传播
 * - 宽依赖 fan-out
 * - 大批量 batch
 * - 创建/销毁循环
 * - 复杂依赖图
 *
 * 注意：这些测试主要验证功能正确性（不崩溃、无泄漏），
 * 性能数据（耗时）仅打印不做硬性断言，避免环境差异导致 CI 失败。
 */
class StressTest {

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // 大量信号
    // ========================================================================

    @Test
    fun `create 10000 signals`() {
        val signals = (0 until 10_000).map { signal(it) }
        assertEquals(10_000, signals.size)
        for (i in 0 until 10_000) {
            assertEquals(i, signals[i].basicNode.read())
        }
    }

    @Test
    fun `write 10000 signals individually`() {
        val signals = (0 until 10_000).map { signal(0) }
        for (i in 0 until 10_000) {
            signals[i].basicNode.write(i)
        }
        for (i in 0 until 10_000) {
            assertEquals(i, signals[i].basicNode.read())
        }
    }

    // ========================================================================
    // 深链 Memo
    // ========================================================================

    @Test
    fun `deep chain of 100 lazy memos`() {
        var head by signal(0)
        var current: Memo<Int> = memo { head + 1 }
        repeat(99) {
            val prev = current
            current = memo { prev.basicNode.read() + 1 }
        }
        // 初始值：0 + 100 = 100
        assertEquals(100, current.basicNode.read())

        // 修改源头
        head = 1
        // 整个链传播
        assertEquals(101, current.basicNode.read())
    }

    @Test
    fun `deep chain of 100 eager memos`() {
        var head by signal(0)
        var current: Memo<Int> = memo(eager = true) { head + 1 }
        repeat(99) {
            val prev = current
            current = memo(eager = true) { prev.basicNode.read() + 1 }
        }
        assertEquals(100, current.basicNode.read())

        head = 1
        // eager 链立即全部重算
        assertEquals(101, current.basicNode.read())
    }

    @Test
    fun `deep chain propagation correctness`() {
        var head by signal(1)
        val depth = 200
        var current: Memo<Int> = memo { head }
        repeat(depth) {
            val prev = current
            current = memo { prev.basicNode.read() + 0 } // identity chain
        }
        assertEquals(1, current.basicNode.read())

        head = 42
        assertEquals(42, current.basicNode.read())
    }

    // ========================================================================
    // 宽依赖 Fan-out
    // ========================================================================

    @Test
    fun `one signal with 1000 effects`() {
        var source by signal(0)
        val results = IntArray(1000)
        val effects = (0 until 1000).map { i ->
            effect { results[i] = source }
        }

        // 初始值验证
        for (i in 0 until 1000) {
            assertEquals(0, results[i])
        }

        source = 42
        // 值已写入，effects 将在微任务中执行

        // 清理
        effects.forEach { it.dispose() }
    }

    @Test
    fun `one signal with 1000 memos`() {
        var source by signal(0)
        val memos = (0 until 1000).map { i ->
            memo { source + i }
        }

        for (i in 0 until 1000) {
            assertEquals(i, memos[i].basicNode.read())
        }

        source = 10
        for (i in 0 until 1000) {
            assertEquals(10 + i, memos[i].basicNode.read())
        }

        memos.forEach { it.dispose() }
    }

    // ========================================================================
    // 大批量 Batch
    // ========================================================================

    @Test
    fun `batch with 10000 writes`() {
        var count by signal(0)
        batch {
            for (i in 1..10_000) {
                count = i
            }
        }
        assertEquals(10_000, count)
    }

    @Test
    fun `batch with 10000 signal writes to 100 signals`() {
        val signals = (0 until 100).map { signal(0) }
        batch {
            for (i in 0 until 100) {
                for (j in 1..100) {
                    signals[i].basicNode.write(j)
                }
            }
        }
        for (i in 0 until 100) {
            assertEquals(100, signals[i].basicNode.read())
        }
    }

    // ========================================================================
    // 创建/销毁循环
    // ========================================================================

    @Test
    fun `create and dispose 1000 effects on same signal`() {
        val sSig = signal(0)
        var s by sSig
        repeat(1000) {
            val e = effect { s }
            e.dispose()
        }
        // 所有 effect 已 dispose，s 的 observers 为空
        assertTrue(sSig.basicNode.observers.isEmpty())
    }

    @Test
    fun `create and dispose 1000 memos on same signal`() {
        val sSig = signal(0)
        var s by sSig
        repeat(1000) {
            val m = memo { s * 2 }
            assertEquals(0, m.basicNode.read())
            m.dispose()
        }
        assertTrue(sSig.basicNode.observers.isEmpty())
    }

    // ========================================================================
    // 复杂图
    // ========================================================================

    @Test
    fun `complex graph with 500 nodes - correctness`() {
        // 构建 5 层 × 100 个节点的 memo 图
        var source by signal(1)
        val level1 = (0 until 100).map { memo { source + it } }
        val level2 = (0 until 100).map { i ->
            memo { level1[i].basicNode.read() + level1[(i + 1) % 100].basicNode.read() }
        }
        val level3 = (0 until 100).map { i ->
            memo { level2[i].basicNode.read() + level2[(i + 50) % 100].basicNode.read() }
        }
        val level4 = (0 until 100).map { i ->
            memo { level3[i].basicNode.read() + level3[(i + 25) % 100].basicNode.read() }
        }
        val level5 = memo { level4.sumOf { it.basicNode.read() } }

        // 初始值验证（每个 level1 = 1 + i，level2 = level1[i] + level1[i+1]...）
        val initialResult = level5.basicNode.read()
        assertTrue(initialResult > 0)

        // 修改源头
        source = 2
        val updatedResult = level5.basicNode.read()
        assertTrue(updatedResult > initialResult)

        // 清理
        (level1 + level2 + level3 + level4).forEach { it.dispose() }
        level5.dispose()
    }

    @Test
    fun `wide fan out and deep chain combined`() {
        var source by signal(0)
        // 10 条深度为 50 的链，都从同一个 source 出发
        val chains = (0 until 10).map { chainIndex ->
            var current: Memo<Int> = memo { source + chainIndex }
            repeat(50) {
                val prev = current
                current = memo { prev.basicNode.read() + 1 }
            }
            current
        }

        // 验证初始值
        for ((i, chain) in chains.withIndex()) {
            assertEquals(i + 50, chain.basicNode.read())
        }

        // 修改 source
        source = 100
        for ((i, chain) in chains.withIndex()) {
            assertEquals(100 + i + 50, chain.basicNode.read())
        }

        // 清理
        chains.forEach { it.dispose() }
    }

    // ========================================================================
    // 边界 - 空操作
    // ========================================================================

    @Test
    fun `large nested batch does not stack overflow`() {
        var s by signal(0)
        // 嵌套 batch 层级很深
        fun deepBatch(depth: Int) {
            if (depth <= 0) {
                s = 42
                return
            }
            batch {
                deepBatch(depth - 1)
            }
        }
        deepBatch(500)
        assertEquals(42, s)
    }
}
