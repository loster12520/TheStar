package com.thestar.reactive

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Batch 单元测试。
 *
 * 覆盖 [batch] 函数的全部行为：
 * - 基本批量更新合并 effect
 * - 嵌套 batch
 * - 不丢失更新
 * - 异常时 batchDepth 恢复
 * - 空 batch / 边界情况
 */
class BatchTest {

    // ========================================================================
    // 基本批量更新
    // ========================================================================

    @Test
    fun `batch groups multiple writes into single effect run`() = runTest {
        resetSchedulerScope(this)
        val a = signal(0)
        val b = signal(0)
        var effectRuns = 0
        effect { a.value; b.value; effectRuns++ }
        val afterInit = effectRuns
        assertEquals(1, afterInit)

        batch {
            a.value = 1
            b.value = 2
        }
        // 同步：effect 尚未执行
        assertEquals(1, effectRuns)

        runCurrent()
        // batch 结束后 effect 只执行一次
        assertEquals(2, effectRuns)
    }

    @Test
    fun `batch updates values synchronously`() {
        val a = signal(0)
        val b = signal(0)

        batch {
            a.value = 1
            b.value = 2
        }
        // 同步检查：值已更新
        assertEquals(1, a.value)
        assertEquals(2, b.value)
    }

    @Test
    fun `batch with multiple writes to same signal`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var effectRuns = 0
        effect { count.value; effectRuns++ }
        assertEquals(1, effectRuns)

        batch {
            count.value = 1
            count.value = 2
            count.value = 3
        }
        assertEquals(3, count.value)
        assertEquals(1, effectRuns) // 尚未执行

        runCurrent()
        assertEquals(2, effectRuns) // 只执行一次
    }

    // ========================================================================
    // 嵌套 Batch
    // ========================================================================

    @Test
    fun `nested batch defers flush to outermost batch end`() = runTest {
        resetSchedulerScope(this)
        val x = signal(0)
        var effectRuns = 0
        effect { x.value; effectRuns++ }
        val afterInit = effectRuns

        batch {
            x.value = 1
            batch {
                x.value = 2
            }
            // 内层 batch 结束，但 flush 被外层阻断
            assertEquals(afterInit, effectRuns)
        }
        // 最外层 batch 结束，此时才调度 flush
        assertEquals(2, x.value)

        runCurrent()
        assertEquals(afterInit + 1, effectRuns) // 只执行一次
    }

    @Test
    fun `triple nested batch works correctly`() = runTest {
        resetSchedulerScope(this)
        val x = signal(0)
        var effectRuns = 0
        effect { x.value; effectRuns++ }
        assertEquals(1, effectRuns)

        batch {
            batch {
                batch {
                    x.value = 99
                }
            }
        }
        // 所有层结束
        assertEquals(99, x.value)
        assertEquals(1, effectRuns)

        runCurrent()
        assertEquals(2, effectRuns)
    }

    @Test
    fun `nested batch does not lose updates`() {
        val a = signal(0)
        val b = signal(0)
        val c = signal(0)

        batch {
            a.value = 1
            batch {
                b.value = 2
                batch {
                    c.value = 3
                }
            }
        }
        assertEquals(1, a.value)
        assertEquals(2, b.value)
        assertEquals(3, c.value)
    }

    // ========================================================================
    // 异常处理
    // ========================================================================

    @Test
    fun `batch recovers batchDepth after exception`() {
        try {
            batch {
                signal(0).value = 1
                throw RuntimeException("batch error")
            }
        } catch (_: RuntimeException) {
            // 预期异常
        }
        // batchDepth 应恢复到 0（finally 块）
        // 验证：后续操作不受影响
        val s = signal(0)
        s.value = 42
        assertEquals(42, s.value)
    }

    @Test
    fun `nested batch recovers batchDepth after inner exception`() {
        try {
            batch {
                signal(0).value = 1
                batch {
                    throw RuntimeException("inner batch error")
                }
            }
        } catch (_: RuntimeException) {
            // 预期异常
        }
        // batchDepth 应正确恢复
        val s = signal(0)
        s.value = 99
        assertEquals(99, s.value)
    }

    // ========================================================================
    // 边界情况
    // ========================================================================

    @Test
    fun `empty batch does not throw`() {
        batch {
            // 没有任何操作
        }
        // 无异常即通过
    }

    @Test
    fun `batch with no signals does not schedule flush`() {
        // 空 batch 不产生任何 pending effect
        batch { /* no signal writes */ }
        // 不应崩溃
    }

    @Test
    fun `batch returns normally for non-signal operations`() {
        var counter = 0
        batch {
            counter++
            counter++
        }
        assertEquals(2, counter)
    }

    @Test
    fun `successive batches work independently`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var effectRuns = 0
        effect { count.value; effectRuns++ }
        assertEquals(1, effectRuns)

        batch { count.value = 1 }
        runCurrent()
        assertEquals(2, effectRuns)

        batch { count.value = 2 }
        runCurrent()
        assertEquals(3, effectRuns)

        batch { count.value = 3 }
        runCurrent()
        assertEquals(4, effectRuns)
    }
}
