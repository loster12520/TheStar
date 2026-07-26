package com.thestar.reactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 综合/场景测试。
 *
 * 模拟真实使用场景，验证多个 reactive API 协同工作的正确性：
 * - TODO 应用
 * - 表单级联
 * - 复杂依赖图
 * - 动态订阅/取消订阅
 * - 错误恢复
 * - 全生命周期
 * - 混合模式（lazy + eager + effect）
 */
class IntegrationTest {

    @BeforeTest
    fun setUp() {
        schedulerScope = CoroutineScope(Dispatchers.Default)
        TrackingContext.scheduled = false
        TrackingContext.batchDepth = 0
        TrackingContext.pendingEffects.clear()
    }

    // ========================================================================
    // TODO 应用场景
    // ========================================================================

    @Test
    fun `TODO app - add and complete tasks`() = runTest {
        schedulerScope = this
        data class Todo(val id: Int, val text: String, val done: Boolean)

        var todos by signal(listOf<Todo>())
        val activeCount by memo { todos.count { !it.done } }
        val doneCount by memo { todos.count { it.done } }
        var lastActiveCount = -1
        effect { lastActiveCount = activeCount }

        // 初始状态
        assertEquals(0, activeCount)
        assertEquals(0, doneCount)
        runCurrent()
        assertEquals(0, lastActiveCount)

        // 添加任务
        todos = listOf(
            Todo(1, "Buy milk", false),
            Todo(2, "Write tests", false),
            Todo(3, "Read book", false),
        )
        runCurrent()
        assertEquals(3, activeCount)
        assertEquals(0, doneCount)
        assertEquals(3, lastActiveCount)

        // 完成一个任务
        todos = todos.map { if (it.id == 1) it.copy(done = true) else it }
        runCurrent()
        assertEquals(2, activeCount)
        assertEquals(1, doneCount)
    }

    @Test
    fun `TODO app - batch add multiple todos`() = runTest {
        schedulerScope = this
        data class Todo(val id: Int, val text: String)
        var todos by signal(listOf<Todo>())
        var effectRuns = 0
        val count by memo { todos.size }
        effect { count; effectRuns++ }
        assertEquals(1, effectRuns) // 初始执行

        batch {
            todos = todos + Todo(1, "Task 1")
            todos = todos + Todo(2, "Task 2")
            todos = todos + Todo(3, "Task 3")
        }
        assertEquals(3, count)
        assertEquals(1, effectRuns) // batch 未结束

        runCurrent()
        assertEquals(2, effectRuns) // batch 结束后只执行一次
    }

    // ========================================================================
    // 表单级联场景
    // ========================================================================

    @Test
    fun `cascading dropdown - province city district`() {
        var province by signal("Zhejiang")
        val cities by memo {
            when (province) {
                "Zhejiang" -> listOf("Hangzhou", "Ningbo", "Wenzhou")
                "Jiangsu" -> listOf("Nanjing", "Suzhou", "Wuxi")
                else -> emptyList()
            }
        }
        var city by signal("Hangzhou")
        val districts by memo {
            when (city) {
                "Hangzhou" -> listOf("Xihu", "Gongshu", "Binjiang")
                "Nanjing" -> listOf("Xuanwu", "Gulou", "Jianye")
                else -> emptyList()
            }
        }

        assertEquals(listOf("Hangzhou", "Ningbo", "Wenzhou"), cities)
        assertEquals(listOf("Xihu", "Gongshu", "Binjiang"), districts)

        // 切换省份
        province = "Jiangsu"
        // city 仍是 "Hangzhou"，但 Jiangsu 下没有 Hangzhou -> districts 为空
        assertEquals(listOf("Nanjing", "Suzhou", "Wuxi"), cities)

        // 选择 Jiangsu 下的城市
        city = "Nanjing"
        assertEquals(listOf("Xuanwu", "Gulou", "Jianye"), districts)
    }

    @Test
    fun `cascading dropdown with batch update`() = runTest {
        schedulerScope = this
        var province by signal("Zhejiang")
        var city by signal("Hangzhou")
        val cities by memo { when (province) {
            "Zhejiang" -> listOf("Hangzhou", "Ningbo")
            "Jiangsu" -> listOf("Nanjing", "Suzhou")
            else -> emptyList()
        }}
        var effectRuns = 0
        effect { cities; effectRuns++ }
        assertEquals(1, effectRuns)

        // batch 同时切换省份和城市
        batch {
            province = "Jiangsu"
            city = "Nanjing"
        }
        runCurrent()
        assertEquals(2, effectRuns) // cities 的 effect 只执行一次
    }

    // ========================================================================
    // 复杂依赖图 - 菱形依赖
    // ========================================================================

    @Test
    fun `diamond dependency - D updates only once when A changes`() {
        // A -> B -> D
        // A -> C -> D
        var a by signal(2)
        var bComputeCount = 0
        var cComputeCount = 0
        var dComputeCount = 0

        val b by memo { bComputeCount++; a * 2 }       // A -> B
        val c by memo { cComputeCount++; a + 10 }      // A -> C
        val d by memo { dComputeCount++; b + c } // B+C -> D

        assertEquals(4, b)   // 2*2
        assertEquals(12, c)  // 2+10
        assertEquals(16, d)  // 4+12
        assertEquals(1, bComputeCount)
        assertEquals(1, cComputeCount)
        assertEquals(1, dComputeCount)

        a = 3
        // 先读 D，触发重算链
        assertEquals(6, b)   // 3*2
        assertEquals(13, c)  // 3+10
        assertEquals(19, d)  // 6+13
        // 每个 memo 各重算一次
        assertEquals(2, bComputeCount)
        assertEquals(2, cComputeCount)
        assertEquals(2, dComputeCount)
    }

    @Test
    fun `diamond dependency with eager memos`() {
        var a by signal(2)
        var dComputeCount = 0

        val b by memo(eager = true) { a * 2 }
        val c by memo(eager = true) { a + 10 }
        val d by memo { dComputeCount++; b + c }

        assertEquals(16, d)
        assertEquals(1, dComputeCount)

        a = 3
        // eager memos 立即重算，d 的 dirty 标记传播
        assertEquals(19, d)
        assertEquals(2, dComputeCount) // d 只重算一次
    }

    // ========================================================================
    // 动态订阅 / 取消订阅
    // ========================================================================

    @Test
    fun `dynamic subscribe and unsubscribe`() = runTest {
        schedulerScope = this
        var s1 by signal(1)
        var s2 by signal(10)
        var s3 by signal(100)

        var effectRuns = 0
        // 模拟动态创建的 effect
        val effects = mutableListOf<Effect>()

        // 创建 effect 订阅 s1 和 s2
        effects.add(effect { s1; s2; effectRuns++ })
        runCurrent()
        assertEquals(1, effectRuns)

        // 变更 s1 触发 effect
        s1 = 2
        runCurrent()
        assertEquals(2, effectRuns)

        // 取消第一个 effect，创建新的订阅 s3
        effects[0].dispose()
        effects.add(effect { s3; effectRuns++ })
        runCurrent()
        assertEquals(3, effectRuns) // 新 effect 初始执行一次

        // 变更 s1（第一个 effect 已 dispose，不应再触发）
        s1 = 3
        runCurrent()
        assertEquals(3, effectRuns) // 不变

        // 变更 s3 应触发新 effect
        s3 = 200
        runCurrent()
        assertEquals(4, effectRuns)
    }

    @Test
    fun `create and destroy many effects without memory leak`() {
        val sSig = signal(0)
        var s by sSig
        repeat(100) {
            val e = effect { s }
            e.dispose()
        }
        // 所有 effect 已 dispose，s 的 observers 应被清空
        assertTrue(sSig.basicNode.observers.isEmpty())
    }

    // ========================================================================
    // 错误恢复
    // ========================================================================

    @Test
    fun `error recovery - memo throws then recovers`() {
        var source by signal(1)
        var shouldThrow = false
        val computedObj = memo {
            if (shouldThrow) throw RuntimeException("temporary error")
            source * 10
        }
        val computed by computedObj

        assertEquals(10, computed) // 正常计算

        // 触发异常
        shouldThrow = true
        source = 2
        try { computedObj.basicNode.read() } catch (_: RuntimeException) { /* expected */ }

        // 修复并恢复
        shouldThrow = false
        source = 5
        assertEquals(50, computed)
    }

    @Test
    fun `error recovery - system works after memo exception`() = runTest {
        schedulerScope = this
        var a by signal(1)
        var shouldThrow = false
        val brokenObj = memo {
            if (shouldThrow) throw RuntimeException("broken")
            a * 2
        }
        val broken by brokenObj
        var effectResult = 0
        effect {
            try {
                effectResult = broken
            } catch (_: RuntimeException) {
                effectResult = -1
            }
        }
        runCurrent()
        assertEquals(2, effectResult)

        // 触发异常：memo 回调和 effect 都正确处理了异常
        shouldThrow = true
        a = 10
        runCurrent()
        assertEquals(-1, effectResult)

        // 恢复：关闭异常开关后，创建一个新的 effect 验证 memo 仍可正常计算
        shouldThrow = false
        var recoveredResult = 0
        effect { recoveredResult = broken }
        runCurrent()
        assertEquals(20, recoveredResult) // a=10, 10*2=20
    }

    // ========================================================================
    // 全生命周期
    // ========================================================================

    @Test
    fun `full lifecycle - create update batch dispose`() = runTest {
        schedulerScope = this
        // 创建
        var count by signal(0)
        val double by memo { count * 2 }
        val triple by memo { count * 3 }
        var effectValue = 0
        val e = effect { effectValue = double + triple }

        runCurrent()
        assertEquals(0, effectValue)

        // 更新
        count = 5
        runCurrent()
        assertEquals(25, effectValue) // 10 + 15

        // batch 更新
        batch {
            count = 10
        }
        runCurrent()
        assertEquals(50, effectValue) // 20 + 30

        // dispose (Signal 不再支持 dispose，只清理 effect)
        e.dispose()
    }

    // ========================================================================
    // 混合模式 - lazy + eager
    // ========================================================================

    @Test
    fun `mixed lazy and eager memos in complex graph`() {
        var source by signal(1)

        val lazy1 by memo { source + 1 }
        val eager1 by memo(eager = true) { source * 10 }
        val lazy2 by memo { lazy1 + eager1 }
        val eager2 by memo(eager = true) { lazy2 * 2 }

        assertEquals(12, lazy2)  // lazy1(2) + eager1(10) = 12
        assertEquals(2, lazy1)
        assertEquals(10, eager1)
        assertEquals(12, lazy2)
        assertEquals(24, eager2)

        source = 2
        // eager1 立即重算 = 20
        // eager2: depends on lazy2 which is dirty, triggers recompute chain
        assertEquals(3, lazy1)
        assertEquals(20, eager1)
        assertEquals(23, lazy2)
        assertEquals(46, eager2)
    }

    // ========================================================================
    // 值类型覆盖
    // ========================================================================

    @Test
    fun `all value types work correctly - Int String Boolean List`() {
        var intSig by signal(42)
        var strSig by signal("hello")
        var boolSig by signal(true)
        var listSig by signal(listOf(1, 2, 3))

        val intMemo by memo { intSig * 2 }
        val strMemo by memo { strSig.uppercase() }
        val boolMemo by memo { !boolSig }
        val listMemo by memo { listSig.reversed() }

        assertEquals(84, intMemo)
        assertEquals("HELLO", strMemo)
        assertEquals(false, boolMemo)
        assertEquals(listOf(3, 2, 1), listMemo)
    }

    @Test
    fun `custom class with equals works correctly`() {
        data class Counter(val count: Int)

        var s by signal(Counter(0))
        var effectRuns = 0
        effect { s; effectRuns++ }
        assertEquals(1, effectRuns)

        // 结构相等，不触发
        s = Counter(0)
        assertEquals(1, effectRuns)

        // 值不同，触发
        s = Counter(1)
        // 值已更新
        assertEquals(Counter(1), s)
    }

    // ========================================================================
    // 多个 effect 与多个 signal 的组合
    // ========================================================================

    @Test
    fun `cross dependencies between effects and signals`() = runTest {
        schedulerScope = this
        var a by signal(1)
        var b by signal(10)
        var sum = 0
        var product = 0

        effect { sum = a + b }
        effect { product = a * b }

        assertEquals(11, sum)
        assertEquals(10, product)

        a = 2
        runCurrent()
        assertEquals(12, sum)
        assertEquals(20, product)

        b = 20
        runCurrent()
        assertEquals(22, sum)
        assertEquals(40, product)
    }
}
