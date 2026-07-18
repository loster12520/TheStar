plugins {
    id("thestar.library")
}

kotlin {
    sourceSets {
        jsMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("io.github.oshai:kotlin-logging-js:8.0.4")
            }
        }
        
        commonMain {
            dependencies {
                implementation("io.github.oshai:kotlin-logging:8.0.4")
            }
        }
    }
}