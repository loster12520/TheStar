// :thestar 父模块
// 公共构建配置由 buildSrc/src/main/kotlin/thestar.library.gradle.kts 提供
// 各子模块通过 plugins { id("thestar.library") } 引入

subprojects {
    group = "com.lignting.thestar"
    version = "0.0.1"
}
