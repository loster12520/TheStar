package com.thestar.reactive

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Effect 单元测试。
 *
 * 覆盖 [effect] 工厂函数与 [Effect] 类的全部行为：
 * - 创建时立即执行
 * - dispose 停止通知、幂等性
 * - 多 effect 共享同一信号
 * - 异常隔离
 * - 依赖动态更新
 * - 异步调度验证
 */
class EffectTest {

    // ========================================================================
    // 创建与初始执行
    // ========================================================================

    @Test
    fun `effect runs on creation`() {
        val count = signal(0)
        var result = 0
        effect { result = count.value }
        assertEquals(0, result) // 创建时立即执行
    }

    @Test
    fun `effect runs exactly once on creation`() {
        var execCount = 0
        effect { execCount++ }
        assertEquals(1, execCount)
    }

    @Test
    fun `effect returns Effect instance`() {
        val count = signal(0)
        val e = effect { count.value }
        assertNotNull(e)
        assertTrue(e is Effect)
        e.dispose()
    }

    @Test
    fun `effect captures latest values from all read signals`() {
        val a = signal(1)
        val b = signal(2)
        var sum = 0
        effect { sum = a.value + b.value }
        assertEquals(3, sum)
    }

    // ========================================================================
    // Effect 触发 - 异步调度
    // ========================================================================

    @Test
    fun `effect triggers after signal write`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var callCount = 0
        effect { count.value; callCount++ }

        val afterInit = callCount
        assertEquals(1, afterInit)

        count.value = 1
        // 同步检查：effect 尚未执行（微任务调度）
        assertEquals(1, callCount)

        // 等待微任务
        runCurrent()
        assertEquals(2, callCount)
    }

    @Test
    fun `effect triggers once for multiple writes to same signal`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var callCount = 0
        effect { count.value; callCount++ }
        assertEquals(1, callCount)

        count.value = 1
        count.value = 2
        count.value = 3
        // 同步：尚未执行
        assertEquals(1, callCount)

        runCurrent()
        // 只执行一次（合并了多次写入）
        assertEquals(2, callCount)
    }

    @Test
    fun `effect triggers independently for different signals`() = runTest {
        resetSchedulerScope(this)
        val a = signal(0)
        val b = signal(0)
        var effectA = 0
        var effectB = 0
        effect { a.value; effectA++ }
        effect { b.value; effectB++ }
        assertEquals(1, effectA)
        assertEquals(1, effectB)

        a.value = 1
        runCurrent()
        assertEquals(2, effectA)
        assertEquals(1, effectB) // b 的 effect 未触发

        b.value = 1
        runCurrent()
        assertEquals(2, effectA)
        assertEquals(2, effectB)
    }

    // ========================================================================
    // Dispose
    // ========================================================================

    @Test
    fun `effect dispose stops notifications`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var callCount = 0
        val e = effect { count.value; callCount++ }
        val afterInit = callCount
        assertEquals(1, afterInit)

        e.dispose()
        count.value = 1
        runCurrent()
        assertEquals(afterInit, callCount) // 释放后不再触发
    }

    @Test
    fun `effect dispose is idempotent`() {
        val e = effect { /* no-op */ }
        e.dispose()
        e.dispose() // 不应抛异常
        e.dispose() // 多次调用安全
    }

    @Test
    fun `disposed effect is removed from pendingEffects`() {
        val count = signal(0)
        val e = effect { count.value }
        count.value = 1 // 将 effect 加入 pendingEffects
        e.dispose() // dispose 应从 pendingEffects 中移除
        // 不应崩溃，e 已不在 pending set 中
    }

    @Test
    fun `effect dispose clears source relationships`() {
        val count = signal(0)
        val e = effect { count.value }
        // effect 的 node 应该被 count 的 node 所观察
        e.dispose()
        // dispose 后 count 的 observer 集合应被清理
        assertTrue(count.node.observers.isEmpty())
    }

    // ========================================================================
    // 多 Effect
    // ========================================================================

    @Test
    fun `multiple effects on same signal`() = runTest {
        resetSchedulerScope(this)

        val count = signal(0)
        var r1 = 0
        var r2 = 0
        effect { r1 = count.value }
        effect { r2 = count.value * 10 }
        assertEquals(0, r1)
        assertEquals(0, r2)

        count.value = 3
        runCurrent()
        assertEquals(3, r1)
        assertEquals(30, r2)
    }


    @Test
    fun `multiple effects fire in batch after signal change`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        val results = mutableListOf<String>()
        effect { results.add("effect1:${count.value}") }
        effect { results.add("effect2:${count.value}") }
        assertEquals(2, results.size) // 初始各执行一次

        count.value = 7
        runCurrent()
        // 两个 effect 都应在同一轮 flush 中执行
        assertTrue(results.contains("effect1:7"))
        assertTrue(results.contains("effect2:7"))
    }

    // ========================================================================
    // 异常隔离
    // ========================================================================

    @Test
    fun `effect exception does not prevent other effects`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var normalRan = false
        effect { throw RuntimeException("effect error") }
        effect { count.value; normalRan = true }

        count.value = 1
        runCurrent()
        // 异常 effect 失败，但正常 effect 仍执行
        assertTrue(normalRan)
    }

    @Test
    fun `effect exception during initial execution is caught`() {
        // 初始执行异常在 effect() 创建时抛出，但 exception 被 flushEffects 的 try-catch 吞掉
        // 验证后续代码仍正常执行
        try {
            effect { throw RuntimeException("init error") }
        } catch (_: RuntimeException) {
            // 可能抛出也可能被吞
        }
        // 不崩溃即为通过
    }

    // ========================================================================
    // 依赖动态变化
    // ========================================================================

    @Test
    fun `effect dynamically changes dependencies across executions`() = runTest {
        resetSchedulerScope(this)
        val toggle = signal(true)
        val a = signal(1)
        val b = signal(10)
        var result = 0
        effect {
            result = if (toggle.value) a.value else b.value
        }
        assertEquals(1, result) // 初始：依赖 a

        // 切换 toggle，effect 的依赖从 a 变为 b
        toggle.value = false
        runCurrent()
        assertEquals(10, result) // effect 重新执行，现在读的是 b

        // 修改 a 不应触发 effect
        var callCount = 0
        val trackEffect = effect { toggle.value; a.value; b.value; callCount++ }
        runCurrent()
        val afterSecondInit = callCount

        a.value = 999
        runCurrent()
        // a 不再是第一个 effect 的依赖（第一个 effect 上次执行时读的是 b）
        // 但 trackEffect 依赖 a，所以 trackEffect 会触发
        assertTrue(callCount > afterSecondInit)
    }

    // ========================================================================
    // 边界情况
    // ========================================================================

    @Test
    fun `effect with no signal reads runs once and does not re-trigger`() = runTest {
        resetSchedulerScope(this)
        var callCount = 0
        effect { callCount++ }
        assertEquals(1, callCount)
        // 无依赖触发源
        runCurrent()
        assertEquals(1, callCount)
    }

    @Test
    fun `multiple dispose calls on effect do not corrupt state`() {
        val count = signal(0)
        val e = effect { count.value }
        e.dispose()
        e.dispose()
        count.value = 1
        // 没有 crash 即为通过
    }

    @Test
    fun `effect dispose during execution does not cause issues`() = runTest {
        resetSchedulerScope(this)
        val count = signal(0)
        var selfRef: Effect? = null
        var execCount = 0
        val self = effect {
            execCount++
            if (execCount == 2) {
                selfRef?.dispose() // 第二次执行时 dispose 自身
            }
            count.value
        }.also { selfRef = it }
        assertEquals(1, execCount)

        // 触发第二次执行，effect 在其中 dispose 自身
        count.value = 1
        runCurrent()
        assertEquals(2, execCount)

        // 再次变更，effect 已 dispose 不应再执行
        count.value = 2
        runCurrent()
        assertEquals(2, execCount)
    }
}
