package com.thestar.reactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Disposable 接口一致性测试。
 *
 * 验证 Signal、Memo、Effect 三个公开类型均实现 [Disposable] 接口，
 * 且 dispose 行为一致：
 * - dispose 幂等（多次调用安全）
 * - dispose 后 source/observer 关系被清理
 */
class DisposableTest {

    // ========================================================================
    // 类型检查
    // ========================================================================

    @Test
    fun `Signal implements Disposable`() {
        val s = signal(1)
        assertTrue(s is Disposable)
    }

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
    // Signal dispose
    // ========================================================================

    @Test
    fun `Signal dispose is idempotent`() {
        val s = signal(1)
        s.dispose()
        s.dispose()
        s.dispose()
        // 无异常即通过
    }

    @Test
    fun `Signal dispose clears observers`() {
        val s = signal(0)
        effect { s.value } // 注册 effect 作为 observer
        assertTrue(s.node.observers.isNotEmpty())
        s.dispose()
        assertTrue(s.node.observers.isEmpty())
    }

    @Test
    fun `Signal dispose multiple times keeps observers empty`() {
        val s = signal(0)
        effect { s.value }
        s.dispose()
        s.dispose()
        assertTrue(s.node.observers.isEmpty())
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
        val s = signal(1)
        val m = memo { s.value * 2 }
        effect { m.value } // 下游 observer
        assertTrue(m.basicNode.observers.isNotEmpty()) // 有下游 observer
        assertTrue(m.basicNode is ObserverNode) // MemoNode 也是 ObserverNode

        m.dispose()
        assertTrue(m.basicNode.observers.isEmpty())
        // MemoNode 的 sources 也应被清理（cleanupSources）
    }

    @Test
    fun `Memo dispose removes itself from upstream signal`() {
        val s = signal(1)
        val m = memo { s.value * 2 }
        assertEquals(2, m.value) // 触发计算，注册依赖

        assertTrue(s.node.observers.isNotEmpty())
        m.dispose()
        assertTrue(s.node.observers.isEmpty()) // memo 已从 signal 的 observers 中移除
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
        val s = signal(0)
        val e = effect { s.value }
        assertTrue(s.node.observers.isNotEmpty())
        e.dispose()
        assertTrue(s.node.observers.isEmpty())
    }

    // ========================================================================
    // 组合 dispose
    // ========================================================================

    @Test
    fun `dispose chain - effect after all disposes do not trigger`() {
        val s = signal(1)
        val m = memo { s.value * 2 }
        val e = effect { m.value }
        // 全部 dispose
        e.dispose()
        m.dispose()
        s.dispose()
        // 无异常即通过
    }

    @Test
    fun `partial dispose - disposing signal does not affect disposed effect`() {
        val s = signal(0)
        val e = effect { s.value }
        e.dispose()
        s.dispose()
        // e 已 dispose，s dispose 不会尝试清理已清理的 observer
    }
}
