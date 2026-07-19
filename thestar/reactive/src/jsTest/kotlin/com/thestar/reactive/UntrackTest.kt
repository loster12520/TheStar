package com.thestar.reactive

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Untrack 单元测试。
 *
 * 覆盖 [untrack] 函数的全部行为：
 * - 阻止依赖追踪
 * - 返回值正确
 * - 嵌套 untrack
 * - 与 batch 组合
 * - 边界情况
 */
class UntrackTest {

    // ========================================================================
    // 基本功能
    // ========================================================================

    @Test
    fun `untrack returns computed value`() {
        val count = signal(5)
        val result = untrack { count.value * 10 }
        assertEquals(50, result)
    }

    @Test
    fun `untrack prevents dependency tracking`() = runTest {
        val a = signal(0)
        var effectRuns = 0
        effect {
            untrack { a.value } // 不追踪 a
            effectRuns++
        }
        val afterInit = effectRuns
        assertEquals(1, afterInit)

        a.value = 1 // 不应触发 effect
        delay(1)
        assertEquals(1, effectRuns)
    }

    @Test
    fun `untrack read does not register observer`() {
        val a = signal(0)
        // 在 tracking context 中（effect 内），untrack 内的读取不应注册
        effect {
            untrack { a.value }
        }
        // a 的 node 不应有 observer（因为读取在 untrack 内）
        assertTrue(a.node.observers.isEmpty())
    }

    @Test
    fun `untrack within effect does not add to sources`() {
        val a = signal(0)
        effect {
            val v = untrack { a.value }
            assertEquals(0, v)
        }
        // 验证 a 没有 observer（untrack 内读取不注册依赖）
        assertTrue(a.node.observers.isEmpty())
    }

    // ========================================================================
    // 与 Effect 中追踪的混合
    // ========================================================================

    @Test
    fun `untrack mixed with tracked reads in same effect`() = runTest {
        val a = signal(0)
        val b = signal(0)
        var tracked = 0
        var untracked = 0
        effect {
            tracked = a.value // 追踪 a
            untracked = untrack { b.value } // 不追踪 b
        }
        assertEquals(0, tracked)
        assertEquals(0, untracked)

        // 只修改 b，不应触发 effect
        b.value = 5
        delay(1)
        // a 未变，effect 不应触发
        assertEquals(0, tracked)

        // 修改 a，触发 effect
        a.value = 1
        delay(1)
        assertEquals(1, tracked)
        assertEquals(5, untracked) // untrack 也更新了（因为 effect 整体重跑了）
    }

    // ========================================================================
    // 嵌套 Untrack
    // ========================================================================

    @Test
    fun `nested untrack still prevents tracking`() {
        val a = signal(0)
        var result = 0
        effect {
            result = untrack {
                untrack {
                    a.value
                }
            }
        }
        assertEquals(0, result)
        // 嵌套 untrack 仍然不注册依赖
        assertTrue(a.node.observers.isEmpty())
    }

    // ========================================================================
    // 与其他 API 组合
    // ========================================================================

    @Test
    fun `untrack with memo inside batch`() {
        val count = signal(1)
        val double = memo { count.value * 2 }
        var result = 0

        batch {
            count.value = 10
            // untrack 读 memo：获取当前缓存值（可能为脏值）
            result = untrack { double.value }
        }
        // batch 内同步：double 未重算（dirty），untrack 读取触发重算
        assertEquals(20, result)
    }

    @Test
    fun `untrack reads current value even if dirty`() {
        val count = signal(1)
        val double = memo { count.value * 2 }
        assertEquals(2, double.value)

        count.value = 10
        // double 现在是 dirty，untrack 读取仍获取正确值（触发重算）
        val result = untrack { double.value }
        assertEquals(20, result)
    }

    // ========================================================================
    // 边界情况
    // ========================================================================

    @Test
    fun `untrack with no signals returns plain value`() {
        val result = untrack { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `untrack outside any tracking context works normally`() {
        val s = signal(1)
        val result = untrack { s.value * 2 }
        assertEquals(2, result)
    }

    @Test
    fun `untrack with exception propagates`() {
        val ex = assertFailsWith<RuntimeException> {
            untrack { throw RuntimeException("untrack error") }
        }
        assertTrue(ex.message?.contains("untrack error") == true)
    }
}
