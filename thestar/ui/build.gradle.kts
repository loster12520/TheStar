plugins {
    id("thestar.library")
}

kotlin {
    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":thestar:reactive"))
                implementation(project(":thestar:dom"))
                implementation(project(":thestar:style"))
            }
        }
    }
}
