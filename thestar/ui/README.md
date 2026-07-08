# thestar-ui

## 简介

`thestar-ui` 是 TheStar 框架的组件库，构建于 `thestar-reactive`、`thestar-dom`、`thestar-style` 之上。它提供了一系列开箱即用的 UI 组件，兼顾**高性能**、**客制化友好**和**稳定可用**。

设计目标：

- **组合优于继承**：组件通过组合实现，每个组件是一个工厂函数，返回可用于 DSL 的构建块
- **样式可覆盖**：每个组件暴露语义化的 class 名或 style props，允许用户从外部覆盖任意样式
- **主题驱动**：组件外观由 `thestar-style` 的主题变量控制，一套变量切换即可改变整个组件库的外观
- **按需引入**：组件各自独立，不引入未使用的代码（依赖 Kotlin/JS 的 DCE）
- **可访问性内置**：键盘导航、ARIA 属性、焦点管理等作为组件的默认行为

## API 示例

### Button（按钮）

```kotlin
import com.thestar.ui.*

// 基础用法
Button {
    text("Click me")
    onClick { handleClick() }
}

// 带变体
Button(variant = ButtonVariant.Primary) {
    text("Save")
    onClick { save() }
}

Button(variant = ButtonVariant.Danger, size = ButtonSize.Small) {
    text("Delete")
    disabled = isDeleting()                  // 响应式 disabled
    onClick { confirmDelete() }
}
```

### TextField（输入框）

```kotlin
val name by signal("")

TextField {
    value = name()                           // 受控组件
    placeholder = "Enter your name"
    onChange { name(it) }                    // it 是新的输入值
}

TextField(variant = TextFieldVariant.TextArea) {
    value = bio()
    placeholder = "Tell us about yourself"
    rows = 4
    onChange { bio(it) }
}
```

### Card（卡片）

```kotlin
Card {
    header {
        h3 { text("Getting Started") }
    }
    body {
        p { text("Follow these steps to get started with TheStar.") }
    }
    footer {
        Button(variant = ButtonVariant.Primary) {
            text("Learn more →")
        }
    }
}
```

### 布局组件

```kotlin
// Flex 布局
Flex(direction = FlexDirection.Row, gap = 16.px, wrap = FlexWrap.Wrap) {
    Card { /* ... */ }
    Card { /* ... */ }
    Card { /* ... */ }
}

// Grid 布局
Grid(columns = 3, gap = 24.px) {
    // 3 列网格自动排列
    items.forEach { item ->
        Card {
            body { text(item.title) }
        }
    }
}

// Stack 布局（层叠）
Stack {
    img { attr.src(backgroundUrl) }
    div { text("Overlay text") }
}
```

### Dialog / Modal（对话框）

```kotlin
val showDialog by signal(false)

Dialog(open = showDialog) {
    header { text("Confirm Action") }
    body { text("Are you sure you want to proceed?") }
    footer {
        Button(variant = ButtonVariant.Default) {
            text("Cancel")
            onClick { showDialog(false) }
        }
        Button(variant = ButtonVariant.Primary) {
            text("Confirm")
            onClick {
                performAction()
                showDialog(false)
            }
        }
    }
}
```

### 自定义组件

```kotlin
// 定义自定义 props
data class UserCardProps(
    val name: String,
    val avatar: String,
    val role: String,
)

// 自定义组件 —— 本质是返回 DSL 块的函数
fun UserCard(props: UserCardProps) = component {
    Card {
        body {
            Flex(direction = FlexDirection.Row, gap = 12.px) {
                img {
                    attr.src(props.avatar)
                    attr.className(userCardStyle.avatar)
                }
                div {
                    h4 { text(props.name) }
                    span { text(props.role) }
                }
            }
        }
    }
}

// 使用 —— 与内置组件完全一致的用法
UserCard(UserCardProps(
    name = "Alice",
    avatar = "/avatars/alice.png",
    role = "Developer"
))
```

### 主题定制

```kotlin
// 组件库的所有颜色、间距、圆角等通过主题变量控制
Theme(provider = myTheme) {
    // 内部所有组件自动使用 myTheme 的变量值
    App()
}

// 一个主题定义
val myTheme = Theme(
    colors = ThemeColors(
        primary = Color("#6C5CE7"),
        success = Color("#00B894"),
        danger = Color("#FF7675"),
        bg = Color("#FFFFFF"),
        text = Color("#2D3436"),
    ),
    spacing = ThemeSpacing(
        unit = 8.px,
    ),
    radii = ThemeRadii(
        sm = 4.px,
        md = 8.px,
        lg = 16.px,
    ),
)
```

## 原理简述

### 组件模型

```
┌─────────────────────────────────────────┐
│               Component                  │
│                                          │
│  Props (data class)                      │
│  ┌─────────────────────────────────┐    │
│  │  variant, size, disabled, etc.  │    │
│  └─────────────────────────────────┘    │
│              │                           │
│              ▼                           │
│  ┌─────────────────────────────────┐    │
│  │  component { ... }  DSL 块      │    │
│  │  ┌──────────┐  ┌─────────────┐  │    │
│  │  │  DOM      │  │  Style      │  │    │
│  │  │  (结构)   │  │  (外观)     │  │    │
│  │  └──────────┘  └─────────────┘  │    │
│  └─────────────────────────────────┘    │
│                                          │
│  Slots: header, body, footer, ...       │
└─────────────────────────────────────────┘
```

每个 UI 组件由三层组成：

1. **Props**：外部输入，定义组件的行为配置。Props 可以是普通值或响应式 Signal——因为 `thestar-dom` 的细粒度 Effect 会在每个绑定点独立追踪依赖，所以传入 Signal 也不触发组件级重渲染。

2. **DOM 结构**：通过 `thestar-dom` 的 DSL 构建，定义了组件的语义结构（如 `<button>`、`<input>`、`<div>` 的组合）。结构是**静态**的——组件挂载后不会因为 props 变化而重建 DOM 树。

3. **样式**：通过 `thestar-style` 定义，大部分为静态样式（编译期提取），少数动态样式（如 variant 切换、disabled 状态）通过响应式样式绑定。

### 响应式 Props

```kotlin
// disabled 是一个 Signal<Boolean>
Button {
    disabled = isDeleting()
    //           ↑ 不是布尔值，而是一个 Signal
}
```

组件内部：

```kotlin
fun Button(props: ButtonProps) = component {
    // 将 isDeleting() 的动态值绑定到 disabled 属性
    button {
        attr.disabled { props.disabled }         // 细粒度 effect
        attr.className { buttonStyle.className } // 静态类名
        // ...
    }
    // 当 isDeleting 变化时，只有 disabled 属性被更新，Button 本身不重渲染
}
```

### 插槽（Slots）

组件通过 Kotlin Lambda 参数暴露插槽：

```kotlin
// Card 的定义
data class CardProps(
    val header: (ElementBuilder.() -> Unit)? = null,
    val body: ElementBuilder.() -> Unit,
    val footer: (ElementBuilder.() -> Unit)? = null,
)

fun Card(props: CardProps) = component {
    div(className = cardStyle) {
        props.header?.let { headerFn ->
            div(className = cardStyle.header) { headerFn() }
        }
        div(className = cardStyle.body) { props.body() }
        props.footer?.let { footerFn ->
            div(className = cardStyle.footer) { footerFn() }
        }
    }
}
```

Lambda 在组件创建时执行一次，内部 DSL 如加入响应式绑定仍可细粒度更新。

### 主题系统

```
Theme.kt (主题定义)
      │
      ▼
  themeVars (编译期生成)
      │
      ├─→ CSS 自定义属性 :root { --primary: ... }
      │
      └─→ 组件 style {} 引用 var("primary")
               │
               ▼
          编译为 var(--primary)
               │
               ▼
          浏览器原生继承 → 切换主题变量 = 全页自动更新
```

`Theme` 组件本身是一个 **上下文提供者**，使用 `thestar-reactive` 的 Context 机制将主题变量向下传递。子组件通过 `var("primary")` 引用主题变量，编译为 CSS 自定义属性引用（如 `var(--primary)`），而非硬编码颜色值。

### 新增组件的开发范式

1. 定义 `Props` data class（包含 variant、size 等枚举和内容插槽）
2. 用 `thestar-style` 定义组件的样式块
3. 在 `component {}` 中用 `thestar-dom` 的 DSL 编写 DOM 结构
4. 将 Props 的值绑定到对应的 DOM 属性/样式上
5. 暴露为一个顶层函数，作为组件使用

组件开发者不需要了解 `thestar-reactive` 的内部机制——只需要知道传入的值可以是响应式的，其他都自动处理。
