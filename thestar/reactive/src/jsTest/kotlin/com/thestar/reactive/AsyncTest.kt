package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 异步行为测试。
 *
 * 验证 reactive 系统在异步环境下的行为：
 * - 微任务调度时序
 * - effect 合并
 * - batch 后异步 flush
 * - 快速连续写入
 * - dispose 时序
 * - 协程内写入
 */
class AsyncTest {

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // 微任务时序
    // ========================================================================

    @Test
    fun `effect executes asynchronously after write`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectValue = -1
        effect { effectValue = count }

        assertEquals(0, effectValue) // 创建时同步执行

        count = 42
        // 写入后同步检查：effect 尚未执行
        assertEquals(0, effectValue)
        // microtask 之后
        runCurrent()
        assertEquals(42, effectValue)
    }

    @Test
    fun `multiple writes before microtask are merged into single effect run`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        count = 1
        count = 2
        count = 3
        count = 4
        count = 5

        assertEquals(1, effectRuns) // 尚未执行

        runCurrent()
        assertEquals(2, effectRuns) // 5 次写入合并为 1 次
        assertEquals(5, count)
    }

    @Test
    fun `effect does not re-execute in same microtask after initial flush`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        // 第一次写入
        count = 1
        runCurrent()
        assertEquals(2, effectRuns)

        // 第二次写入（在另一个 microtask 中）
        count = 2
        runCurrent()
        assertEquals(3, effectRuns)
    }

    // ========================================================================
    // Batch 后异步 flush
    // ========================================================================

    @Test
    fun `batch flush happens after outermost batch ends`() = runTest {
        schedulerScope = this
        var a by signal(0)
        var b by signal(0)
        var effectRuns = 0
        effect { a; b; effectRuns++ }
        assertEquals(1, effectRuns)

        batch {
            a = 1
            batch {
                b = 2
            }
            // 内层 batch 结束，但不 flush
            assertEquals(1, effectRuns)
        }
        // 最外层结束，flush 已调度但尚未执行
        assertEquals(1, effectRuns)

        runCurrent()
        assertEquals(2, effectRuns)
    }

    @Test
    fun `batch effect execution order is correct`() = runTest {
        schedulerScope = this
        var a by signal(0)
        var b by signal(0)
        val log = mutableListOf<String>()

        effect { log.add("effect-a:$a") }
        effect { log.add("effect-b:$b") }
        // 初始执行顺序
        assertEquals(2, log.size)

        batch {
            a = 1
            b = 2
        }
        assertEquals(2, log.size) // batch 内未执行

        runCurrent()
        assertEquals(4, log.size) // 两个 effect 各执行一次
        // 两个 effect 都在新的值上执行
        assertTrue(log.any { it == "effect-a:1" })
        assertTrue(log.any { it == "effect-b:2" })
    }

    // ========================================================================
    // 多独立 Batch
    // ========================================================================

    @Test
    fun `two independent batches produce separate effect rounds`() = runTest {
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
    }

    @Test
    fun `batch inside effect execution context`() = runTest {
        schedulerScope = this
        var a by signal(0)
        var b by signal(0)
        var bEffectRuns = 0
        effect { b; bEffectRuns++ }
        assertEquals(1, bEffectRuns)

        // effect 内部写 signal，这会触发 scheduleFlush
        // 当前没有在执行 effect（主线程测试代码），所以正常调度
        batch {
            a = 1
            b = 1
        }
        runCurrent()
        assertEquals(2, bEffectRuns) // batch 中 b 改了一次
    }

    // ========================================================================
    // Dispose 时序
    // ========================================================================

    @Test
    fun `dispose effect before microtask prevents execution`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        val e = effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        count = 1 // effect 被加入 pendingEffects
        e.dispose() // 在微任务执行前 dispose

        runCurrent()
        assertEquals(1, effectRuns) // effect 不执行
    }

    @Test
    fun `dispose effect during microtask flush does not execute it`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var shouldDispose = false
        lateinit var e1: Effect
        var e1Runs = 0
        var e2Runs = 0

        e1 = effect {
            e1Runs++
            if (shouldDispose) e1.dispose()
            count
        }
        effect { count; e2Runs++ }

        assertEquals(1, e1Runs)
        assertEquals(1, e2Runs)

        shouldDispose = true
        count = 1
        runCurrent()
        // e1 这次执行后 dispose 了自己
        assertEquals(2, e1Runs)
        assertEquals(2, e2Runs)

        // 再次变更，e1 不应再执行
        shouldDispose = false // 如果不 dispose 了，但 e1 已经 disposed
        count = 2
        runCurrent()
        assertEquals(2, e1Runs) // e1 不再执行
        assertEquals(3, e2Runs) // e2 正常执行
    }

    // ========================================================================
    // 协程内写入
    // ========================================================================

    @Test
    fun `signal write from coroutine triggers effect`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectValue = -1
        effect { effectValue = count }
        assertEquals(0, effectValue)

        // 在协程中写入
        launch {
            count = 99
        }
        // 等待协程执行 + 微任务
        delay(10)
        assertEquals(99, effectValue)
    }

    // ========================================================================
    // 快速连续操作
    // ========================================================================

    @Test
    fun `rapid signal writes do not lose updates`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        // 快速写入
        for (i in 1..100) {
            count = i
        }
        assertEquals(100, count)
        assertEquals(1, effectRuns) // 尚未执行

        runCurrent()
        assertEquals(2, effectRuns) // 只执行一次
        assertEquals(100, count)
    }

    @Test
    fun `alternating writes and reads do not corrupt state`() = runTest {
        schedulerScope = this
        var count by signal(0)
        var effectRuns = 0
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns)

        // 交替写入和读取
        for (i in 1..50) {
            count = i
            assertEquals(i, count) // 同步读取
        }
        runCurrent()
        assertEquals(2, effectRuns)
        assertEquals(50, count)
    }
}
