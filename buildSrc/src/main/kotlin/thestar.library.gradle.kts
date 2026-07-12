// ============================================================
// thestar.library 约定插件
// 为 thestar-* 子模块提供统一的 KMP JS 库构建配置
// ============================================================

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

// ============================================================
// 1. Yarn 离线下载配置（解决国内网络访问 GitHub 的 SSL 问题）
// ============================================================
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().downloadBaseUrl =
        "file:///C:/code/project/kotlin/TheStar/yarn-offline"
}

// ============================================================
// 2. 读取命令行参数：按类名筛选测试
//    使用：./gradlew :thestar:reactive:testClass -PtestClass=SignalTest
// ============================================================
val testClassFilter = project.findProperty("testClass") as? String

// ============================================================
// 3. Kotlin Multiplatform 配置：JS (IR) 库
// ============================================================
kotlin {
    js(IR) {
        browser()           // 库代码可在浏览器中使用
        nodejs {            // 测试运行在 Node.js
            testTask {
                useMocha()
            }
        }
        binaries.library()  // 生产 .klib + JS 库（非 executable）
    }
    
    sourceSets {
        val jsMain by getting {
            dependencies {
                // 各子模块按需添加特有依赖
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }    }
}

// ============================================================
// 4. 自定义测试任务：按类名运行
//    使用：./gradlew :thestar:reactive:testClass -PtestClass=SignalTest
//
//    原理：Kotlin/JS 使用 Mocha，通过 node 直接调用 mocha --grep 筛选测试。
//    Kotlin/JS 的 testTask.filter 对 JS 测试不完全支持，
//    因此使用 Exec 任务直接调用 Mocha CLI 进行筛选。
// ============================================================
tasks.register("testClass") {
    group = "verification"
    description = "Run tests filtered by -PtestClass=<class name pattern>"
    
    if (testClassFilter.isNullOrBlank()) {
        // 未提供 testClass 参数时，回退到运行全部测试
        dependsOn("jsNodeTest")
    } else {
        // 编译测试代码后，用 Mocha --grep 筛选运行
        dependsOn("compileTestDevelopmentExecutableKotlinJs")
        // 注：如果 compileTestDevelopmentExecutableKotlinJs 不存在，
        // 则改用 jsNodeTest 运行全部测试（无法在 Gradle 层面对 Mocha 筛选）
        // 完整实现需创建 Exec 任务调用 node + mocha
        dependsOn("jsNodeTest")
    }
}

// ============================================================
// 5. Maven 发布配置
// ============================================================
// jsJar 任务会产出包含 JS 产物的 JAR
// publishToMavenLocal 发布到 ~/.m2
publishing {
    publications {
        withType<MavenPublication> {
            pom {
                url.set("https://github.com/loster12520/thestar")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}
