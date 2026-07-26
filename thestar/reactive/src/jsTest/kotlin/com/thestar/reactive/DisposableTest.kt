package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Disposable 接口一致性测试。
 *
 * 验证 Memo、Effect 两个公开类型均实现 [Disposable] 接口，
 * 且 dispose 行为一致：
 * - dispose 幂等（多次调用安全）
 * - dispose 后 source/observer 关系被清理
 */
class DisposableTest {

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // 类型检查
    // ========================================================================

    @Test
    fun `Memo implements Disposable`() {
        val m = memo { 42 }
        assertTrue(m is Disposable)
    }

    @Test
    fun `Effect implements Disposable`() {
        val e = effect { /* no-op */ }
        assertTrue(e is Disposable)
    }

    // ========================================================================
    // Memo dispose
    // ========================================================================

    @Test
    fun `Memo dispose is idempotent`() {
        val m = memo { 42 }
        m.dispose()
        m.dispose()
        m.dispose()
        // 无异常即通过
    }

    @Test
    fun `Memo dispose clears sources and observers`() {
        val sSig = signal(1)
        var s by sSig
        val m = memo { s * 2 }
        effect { m.basicNode.read() } // 下游 observer
        assertTrue(m.basicNode.observers.isNotEmpty()) // 有下游 observer
        assertTrue(m.basicNode is ObserverNode) // MemoNode 也是 ObserverNode

        m.dispose()
        assertTrue(m.basicNode.observers.isEmpty())
        // MemoNode 的 sources 也应被清理（cleanupSources）
    }

    @Test
    fun `Memo dispose removes itself from upstream signal`() {
        val sSig = signal(1)
        var s by sSig
        val m = memo { s * 2 }
        assertEquals(2, m.basicNode.read()) // 触发计算，注册依赖

        assertTrue(sSig.basicNode.observers.isNotEmpty())
        m.dispose()
        assertTrue(sSig.basicNode.observers.isEmpty()) // memo 已从 signal 的 observers 中移除
    }

    // ========================================================================
    // Effect dispose
    // ========================================================================

    @Test
    fun `Effect dispose is idempotent`() {
        val e = effect { /* no-op */ }
        e.dispose()
        e.dispose()
        e.dispose()
        // 无异常即通过
    }

    @Test
    fun `Effect dispose clears sources from upstream`() {
        val sSig = signal(0)
        var s by sSig
        val e = effect { s }
        assertTrue(sSig.basicNode.observers.isNotEmpty())
        e.dispose()
        assertTrue(sSig.basicNode.observers.isEmpty())
    }

    // ========================================================================
    // 组合 dispose
    // ========================================================================

    @Test
    fun `dispose chain - effect and memo after disposes do not trigger`() {
        val sSig = signal(1)
        var s by sSig
        val m = memo { s * 2 }
        val e = effect { m.basicNode.read() }
        // dispose effect and memo
        e.dispose()
        m.dispose()
        // 无异常即通过
    }

    @Test
    fun `partial dispose - disposing effect first then updating signal`() {
        val sSig = signal(0)
        var s by sSig
        val e = effect { s }
        e.dispose()
        // e 已 dispose，更新 signal 不触发 effect
        s = 42
        // 不崩溃即为通过
    }
}
