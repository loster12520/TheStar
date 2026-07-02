plugins {
    kotlin("multiplatform") version "2.1.0"
}

group = "com.thestar.front"
version = "0.1-SNAPSHOT"

// 覆盖 Yarn 下载地址，解决国内网络访问 GitHub 的 SSL 问题
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().downloadBaseUrl =
        "file:///C:/code/project/kotlin/TheStar/yarn-offline"
}

kotlin {
    js(IR) {
        browser {
            // 开发服务器在此目标下运行
        }
        binaries.executable()  // 生成可执行的 JS bundle
    }

    sourceSets {
        jsMain {
            dependencies {
                // 后续按需添加 npm 依赖
            }
        }
    }
}
