## THESTAR

### 项目简介

本项目为学习项目，目标是使用kotlin尝试实现一套完整的前端框架。

主要模仿目标为Solid.js，性能第一、开发体验第二、可读性第三

### 项目架构

整个项目大致分为以下模块：

- thestar-reactive: 基于signal的响应式框架，能够提供简单可靠的响应式服务
- thestar-dom: 基于thestar-reactive的ui框架，允许用户使用DSL来编写ui
- thestar-style: 基于预编译的css-in-kotlin样式框架，提供模块化样式服务，
- thestar-ui: 基于上述几个模块的组件库，客制化友好、高性能、稳定可用

### 设计理念

TODO: 这里只是占位符，后续会在这里长篇大论（可能？）

### TODO列表

- [ ] thestar-reactive
    - [x] 设计工作
    - [ ] 完善代码
      - [ ] [core.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/core.kt)
      - [ ] [scheduler.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/scheduler.kt)
      - [ ] [index.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/index.kt)
      - [ ] [types.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/types.kt)
    - [ ] 完善测试
        - [ ] [core.kt](thestar/reactive/src/jsTest/kotlin/com/thestar/reactive/core.kt)
        - [ ] [scheduler.kt](thestar/reactive/src/jsTest/kotlin/com/thestar/reactive/scheduler.kt)
        - [ ] [index.kt](thestar/reactive/src/jsTest/kotlin/com/thestar/reactive/index.kt)
        - [ ] [types.kt](thestar/reactive/src/jsTest/kotlin/com/thestar/reactive/types.kt)
    - [ ] 完善注释
        - [ ] [core.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/core.kt)
        - [ ] [scheduler.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/scheduler.kt)
        - [ ] [index.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/index.kt)
        - [ ] [types.kt](thestar/reactive/src/jsMain/kotlin/com/thestar/reactive/types.kt)
    - [ ] 优化性能
    - [ ] 完善文档
- [ ] thestar-dom
    - [ ] 设计工作
    - [ ] 完善代码
    - [ ] 完善测试
    - [ ] 完善注释
    - [ ] 优化性能
    - [ ] 完善文档
- [ ] thestar-style
    - [ ] 设计工作
    - [ ] 完善代码
    - [ ] 完善测试
    - [ ] 完善注释
    - [ ] 优化性能
    - [ ] 完善文档
- [ ] thestar-ui
    - [ ] 设计工作
    - [ ] 完善代码
    - [ ] 完善测试
    - [ ] 完善注释
    - [ ] 优化性能
    - [ ] 完善文档