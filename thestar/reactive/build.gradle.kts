plugins {
    id("thestar.library")
}

kotlin {
    sourceSets {
        jsMain {
            dependencies {
                // reactive 是底层模块，暂无额外依赖
            }
        }
    }
}
