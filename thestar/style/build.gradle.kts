plugins {
    id("thestar.library")
}

kotlin {
    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":thestar:reactive"))
            }
        }
    }
}
