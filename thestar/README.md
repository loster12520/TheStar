# thestar

TheStar 框架的四个核心库模块，全部基于 **Kotlin Multiplatform** 构建，目标平台为 **JS (IR)**。

## 子模块简介

| 模块 | 路径 | 简介 |
|------|------|------|
| **thestar-reactive** | `reactive/` | 基于 Signal 的响应式框架，提供数据依赖追踪与自动更新传播。是其他三个模块的底层依赖。 |
| **thestar-dom** | `dom/` | 基于 thestar-reactive 的 UI 声明框架。提供 Kotlin DSL 构建浏览器 DOM，采用直接 DOM 操作（无 Virtual DOM），将信号细粒度映射到 DOM 变更。 |
| **thestar-style** | `style/` | 预编译 CSS-in-Kotlin 样式方案。静态样式编译期提取为 `.css` 文件，动态样式运行时通过 CSSOM 更新，支持作用域隔离与主题变量。 |
| **thestar-ui** | `ui/` | 基于上述三个模块的组件库。提供 Button、TextField、Card、Dialog 等开箱即用的 UI 组件，客制化友好、高性能、稳定可用。 |

### 依赖关系

```
thestar-reactive   ← 底层：响应式核心
    ↑
    ├── thestar-dom      ← 依赖 reactive
    ├── thestar-style    ← 依赖 reactive
    └── thestar-ui       ← 依赖 reactive + dom + style
```

## 常用命令

所有命令在项目根目录（`TheStar/`）下执行。将 `<module>` 替换为 `reactive`、`dom`、`style` 或 `ui`。

### 编译

```bash
# 编译指定模块
./gradlew :thestar:<module>:compileKotlinJs

# 编译全部 thestar 模块
./gradlew :thestar:reactive:compileKotlinJs \
          :thestar:dom:compileKotlinJs \
          :thestar:style:compileKotlinJs \
          :thestar:ui:compileKotlinJs

# 编译整个项目（含 front）
./gradlew build
```

### 测试

```bash
# 运行全部测试
./gradlew :thestar:<module>:jsTest

# 运行全部测试（简写别名）
./gradlew :thestar:<module>:testClass

# 按类名筛选测试（注：当前 Kotlin/JS Mocha 实现中会运行全部测试，筛选功能待完善）
./gradlew :thestar:<module>:testClass -PtestClass=SignalTest
```

### 打包与发布

```bash
# 打包为 JS 库产物（.klib）
./gradlew :thestar:<module>:jsJar

# 发布到本地 Maven 仓库（~/.m2）
./gradlew :thestar:<module>:publishToMavenLocal

# 发布所有 thestar 模块到本地 Maven
./gradlew :thestar:reactive:publishToMavenLocal \
          :thestar:dom:publishToMavenLocal \
          :thestar:style:publishToMavenLocal \
          :thestar:ui:publishToMavenLocal
```

### 在其他项目中使用

发布到本地 Maven 后，其他 Gradle 项目可以通过以下方式引入：

```kotlin
// build.gradle.kts
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.thestar:reactive:0.1-SNAPSHOT")
    implementation("com.thestar:dom:0.1-SNAPSHOT")
    implementation("com.thestar:style:0.1-SNAPSHOT")
    implementation("com.thestar:ui:0.1-SNAPSHOT")
}
```

或在 TheStar 项目内部通过 project 依赖直接引用：

```kotlin
implementation(project(":thestar:reactive"))
```

## 构建配置

四个子模块共享统一的构建配置，由 `buildSrc/src/main/kotlin/thestar.library.gradle.kts` 约定插件提供：

- **Kotlin 版本**：2.1.0
- **Gradle 版本**：8.8
- **目标平台**：JS (IR)，同时支持 `browser` 和 `nodejs`
- **二进制模式**：`binaries.library()`（库模式，非可执行文件）
- **测试运行器**：Mocha（Node.js 环境）
- **发布插件**：`maven-publish`

各子模块的 `build.gradle.kts` 只需声明：

```kotlin
plugins {
    id("thestar.library")
}
```

## Yarn 离线配置

项目使用本地离线 Yarn 镜像（`yarn-offline/` 目录），避免在中国大陆网络环境下访问 GitHub 的 SSL 问题。此配置通过约定插件自动应用到所有 thestar-* 模块。

## 开发指南

### 新增模块

1. 在 `thestar/` 下创建子目录
2. 创建 `build.gradle.kts`，应用 `id("thestar.library")`
3. 创建源码目录 `src/jsMain/kotlin/com/thestar/<name>/`
4. 在 `settings.gradle.kts` 中添加 `include(":thestar:<name>")`

### 添加示例代码（KDoc @sample）

示例代码放在 `src/jsExample/kotlin/` 下，用于 KDoc `@sample` 注解引用。此目录下的代码不会被编译进 JS 库产物，但会打包进 sources JAR，供 IDE 查看源码时展示。

```kotlin
// src/jsExample/kotlin/com/thestar/reactive/SignalSample.kt
package com.thestar.reactive

fun signalUsage() {
    var count by signal(0)
    println(count)
    count = 5
}
```

```kotlin
// 在 KDoc 中引用示例：
/**
 * 获取信号的方法。
 * @sample com.thestar.reactive.signalUsage
 */
fun <T : Any> signal(data: T): Signal<T> { ... }
```

**原理**：`jsExample` 是 KMP 自定义源码集（`dependsOn(jsMain)`），编译产物独立于 `jsMain`，不进入 klib/JAR；但 `sourcesJar` 任务会自动收集所有源码集的源文件。

### 添加测试

测试代码放在 `src/jsTest/kotlin/` 下，使用 `kotlin.test` 断言库：

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class MyTest {
    @Test
    fun `my test case`() {
        assertEquals(42, compute())
    }
}
```

## 各模块详细文档

- [thestar-reactive](./reactive/README.md) — 响应式核心 API 与原理
- [thestar-dom](./dom/README.md) — UI 声明框架设计
- [thestar-style](./style/README.md) — CSS-in-Kotlin 编译方案
- [thestar-ui](./ui/README.md) — 组件库设计
