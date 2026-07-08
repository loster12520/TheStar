# thestar-style

## 简介

`thestar-style` 是 TheStar 框架的样式方案——**预编译 CSS-in-Kotlin**。它让你用 Kotlin DSL 编写类型安全的样式，然后在编译期将静态部分提取为独立 CSS 文件，运行时仅处理动态部分。

设计目标：

- **类型安全**：颜色、尺寸、间距等都是 Kotlin 类型，杜绝拼写错误和无效值
- **零运行时静态样式**：编译期通过 KSP 将静态样式生成为标准 `.css` 文件，运行时直接使用类名，无额外开销
- **响应式样式**：依赖 `thestar-reactive` 的动态样式会在运行时自动更新（如主题切换、动画状态）
- **作用域隔离**：每个样式块生成唯一类名，不发生全局样式冲突
- **与 DOM 模块无缝集成**：`style {}` 生成的类名可直接传入 `thestar-dom` 的元素构建器

## API 示例

### 静态样式

```kotlin
import com.thestar.style.*
import com.thestar.style.values.*

// 定义一个样式块 —— 编译期提取为 .css 文件
val cardStyle by style {
    padding = 16.px
    borderRadius = 8.px
    border = "1px solid #e0e0e0"
    backgroundColor = Color.white

    // 伪类
    hover {
        boxShadow = "0 2px 8px rgba(0,0,0,0.15)"
    }

    // 子选择器
    child("h2") {
        fontSize = 20.px
        fontWeight = FontWeight.Bold
        margin = Spacing(0, 0, 8.px, 0)
    }
}

// 使用 —— cardStyle 在编译后就是一个类名字符串
div {
    attr.className(cardStyle)
    h2 { text("Card Title") }
    p { text("Card content goes here.") }
}
```

### 动态样式

```kotlin
val themeColor by signal(Color.blue)
val isExpanded by signal(false)

// 依赖信号的样式只在动态部分产生运行时开销
val panelStyle by style {
    backgroundColor = themeColor()          // ★ 动态值 —— 运行时响应式更新
    padding = 12.px                          //   静态值 —— 编译期提取

    transition = "all 0.3s ease"
    maxHeight by css { if (isExpanded()) 200.px else 0.px }  // ★ 动态
    opacity by css { if (isExpanded()) 1.0 else 0.0 }        // ★ 动态
}
```

### 样式继承与组合

```kotlin
// 基础样式可以复用
val baseButton by style {
    padding = Spacing(8.px, 16.px)
    borderRadius = 4.px
    border = Border.none
    cursor = Cursor.Pointer
    fontSize = 14.px
}

// 组合基础样式并覆盖 / 扩展
val primaryButton by style {
    extend(baseButton)                       // 继承 baseButton 的所有属性
    backgroundColor = Color.blue
    color = Color.white

    hover {
        backgroundColor = Color.darkBlue
    }
}
```

### 主题变量

```kotlin
// 定义主题变量表
val themeColors by themeVars {
    "primary" to Color.blue
    "success" to Color.green
    "danger" to Color.red
    "bg" to Color("#f5f5f5")
    "text" to Color("#333333")
}

// 样式引用主题变量
val themedCard by style {
    backgroundColor = var("bg")              // → 编译为 var(--bg)
    color = var("text")                       // → 编译为 var(--text)
    borderLeft = "3px solid ${var("primary")}" // → var(--primary) 参与值拼接
}

// 运行时切换主题
themeColors.set("primary", Color.purple)     // 所有引用 var(--primary) 的样式自动更新
```

### 全局样式（reset / normalize）

```kotlin
globalStyle {
    selector("*") {
        margin = 0
        padding = 0
        boxSizing = BoxSizing.BorderBox
    }

    selector("body") {
        fontFamily = "system-ui, sans-serif"
        lineHeight = 1.5
        color = Color("#333")
    }
}
```

## 原理简述

### 混合编译策略

```
Kotlin DSL 样式定义
      │
      ▼
┌─────────────────────────────────────────────────────┐
│                    KSP 编译期处理                      │
│                                                      │
│  ┌───────────────────┐    ┌───────────────────────┐  │
│  │  静态路径分析      │    │   生成 CSS 文件        │  │
│  │  (无 Signal 参与)  │ →  │   → thestar-style.css │  │
│  │  提取为纯 CSS      │    │   类名 = hash(样式内容) │  │
│  └───────────────────┘    └───────────────────────┘  │
│                                                      │
│  ┌───────────────────┐    ┌───────────────────────┐  │
│  │  动态路径分析      │    │   生成 Runtime 代码     │  │
│  │  (含 Signal 读取)  │ →  │   → StyleEffect 对象   │  │
│  │  保留为 Kotlin 代码│    │   运行时动态更新 CSSOM  │  │
│  └───────────────────┘    └───────────────────────┘  │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 静态部分：编译期提取

编译时，KSP 遍历所有 `style {}` 块：

1. 分析每个属性值是否为**编译期常量**（不依赖 Signal、不依赖运行时函数调用）
2. 常量属性提取为 CSS 规则，写入生成的 `.css` 文件
3. 对样式内容取 hash，生成唯一类名（如 `tss-a1b2c3d4`）
4. `val cardStyle` 编译后直接等于这个类名字符串

```kotlin
// 源码：
val cardStyle by style {
    padding = 16.px
    borderRadius = 8.px
}

// 编译输出：
//   .css  →  .tss-a1b2c3 { padding: 16px; border-radius: 8px; }
//   .kt   →  val cardStyle: ClassName = ClassName("tss-a1b2c3")
```

### 动态部分：运行时注入

对于依赖 Signal 的属性，KSP 无法在编译期确定其值，转而生成运行时代码：

```kotlin
// 源码：
val panelStyle by style {
    backgroundColor = themeColor()       // 动态
    padding = 12.px                       // 静态
}

// 编译输出：
//   .css  →  .tss-d4e5f6 { padding: 12px; }      ← 仅静态部分
//   .kt   →  运行时创建 StyleEffect：
//            - 类名 = "tss-d4e5f6"
//            - 动态绑定 = effect { 更新 .tss-d4e5f6 的 background-color }
```

动态绑定的更新通过 **CSSOM（`CSSStyleDeclaration`）** 直接操作某个类名下的样式规则，而非替换整个 `<style>` 标签，开销极低。

### extend 的实现

`extend(baseStyle)` 在编译期展开：

```
baseButton 的静态属性 → 合并到当前样式的静态 CSS
primaryButton 覆盖的属性 → 覆盖，CSS 中后出现的规则优先级更高
```

本质等价于 SASS 的 `@extend` 或 Less 的 mixin，但发生在 Kotlin 编译期，不依赖 CSS 预处理器。

### 主题变量的实现

```
themeVars { ... }
      │
      ▼
  编译期 → 生成 CSS 自定义属性块：
          :root { --primary: #0000ff; --success: #00ff00; ... }
      │
      ▼
  运行时 → themeVars.set() 通过 CSSOM 更新 :root 上的 CSS 变量值
          → 浏览器原生级联机制自动将所有引用该变量的元素更新
```

这利用了 CSS 自定义属性（CSS Variables）的原生能力：一次修改 `:root` 的 `--primary`，整个页面所有用到它的地方都自动反映新值，无需框架逐个通知。

### 作用域隔离（Hash 类名）

每个 `style {}` 块的内容（包括伪类、选择器）在编译期被序列化，然后取 hash：

```
style { padding: 16.px; borderRadius: 8.px; hover { ... } }
  → SHA256 前 8 位 → "a1b2c3d4"
  → 类名 "tss-a1b2c3d4"
```

- 相同样式内容 → 相同 hash → 共享同一个类名（自动去重）
- 不同样式内容 → 不同 hash → 不会互相覆盖
