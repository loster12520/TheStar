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