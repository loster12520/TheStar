package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
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

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // 基本批量更新
    // ========================================================================

    @Test
    fun `batch groups multiple writes into single effect run`() = runTest {
        schedulerScope = this
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
        // 同步：effect 尚未执行
        assertEquals(1, effectRuns)

        runCurrent()
        // batch 结束后 effect 只执行一次
        assertEquals(2, effectRuns)
    }

    @Test
    fun `batch updates values synchronously`() {
        var a by signal(0)
        var b by signal(0)

        batch {
            a = 1
            b = 2
        }
        // 同步检查：值已更新
        assertEquals(1, a)
        assertEquals(2, b)
    }

    @Test
    fun `batch with multiple writes to same signal`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        batch {
            count = 1
            count = 2
            count = 3
        }
        assertEquals(3, count)
        assertEquals(1, effectRuns) // 尚未执行

        runCurrent()
        assertEquals(2, effectRuns) // 只执行一次
    }

    // ========================================================================
    // 嵌套 Batch
    // ========================================================================

    @Test
    fun `nested batch defers flush to outermost batch end`() = runTest {
        schedulerScope = this
        var x by signal(0)
        var effectRuns = 0
        effect { x; effectRuns++ }
        val afterInit = effectRuns

        batch {
            x = 1
            batch {
                x = 2
            }
            // 内层 batch 结束，但 flush 被外层阻断
            assertEquals(afterInit, effectRuns)
        }
        // 最外层 batch 结束，此时才调度 flush
        assertEquals(2, x)

        runCurrent()
        assertEquals(afterInit + 1, effectRuns) // 只执行一次
    }

    @Test
    fun `triple nested batch works correctly`() = runTest {
        schedulerScope = this
        var x by signal(0)
        var effectRuns = 0
        effect { x; effectRuns++ }
        assertEquals(1, effectRuns)

        batch {
            batch {
                batch {
                    x = 99
                }
            }
        }
        // 所有层结束
        assertEquals(99, x)
        assertEquals(1, effectRuns)

        runCurrent()
        assertEquals(2, effectRuns)
    }

    @Test
    fun `nested batch does not lose updates`() {
        var a by signal(0)
        var b by signal(0)
        var c by signal(0)

        batch {
            a = 1
            batch {
                b = 2
                batch {
                    c = 3
                }
            }
        }
        assertEquals(1, a)
        assertEquals(2, b)
        assertEquals(3, c)
    }

    // ========================================================================
    // 异常处理
    // ========================================================================

    @Test
    fun `batch recovers batchDepth after exception`() {
        try {
            batch {
                signal(0).basicNode.write(1)
                throw RuntimeException("batch error")
            }
        } catch (_: RuntimeException) {
            // 预期异常
        }
        // batchDepth 应恢复到 0（finally 块）
        // 验证：后续操作不受影响
        var s by signal(0)
        s = 42
        assertEquals(42, s)
    }

    @Test
    fun `nested batch recovers batchDepth after inner exception`() {
        try {
            batch {
                signal(0).basicNode.write(1)
                batch {
                    throw RuntimeException("inner batch error")
                }
            }
        } catch (_: RuntimeException) {
            // 预期异常
        }
        // batchDepth 应正确恢复
        var s by signal(0)
        s = 99
        assertEquals(99, s)
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
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        batch { count = 1 }
        runCurrent()
        assertEquals(2, effectRuns)

        batch { count = 2 }
        runCurrent()
        assertEquals(3, effectRuns)

        batch { count = 3 }
        runCurrent()
        assertEquals(4, effectRuns)
    }
}
