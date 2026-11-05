# x-read UI 重设计任务书（Software Architect）

## 目标
为已有的 Android 中文网络小说阅读 App（x-read）设计一套**可落地、可构建、可真机验证**的 UI 规格。
交付物是一份 Markdown 规格（建议最终写入 `docs/ui-design-spec.md`），不是从零写代码。

## 项目位置
- 根目录：`/vol3/1000/code/x-read`
- UI 源码目录：`android/app/src/main/java/com/alanmaster/xread/ui/`
  - `Theme.kt`、`Components.kt`、`ReaderPalette.kt`、`ReaderScreen.kt`、`ShelfScreen.kt`、
    `DetailScreen.kt`、`SearchScreen.kt`、`RankScreen.kt`、`CategoryScreen.kt`、`TocScreen.kt`、
    `ReviewScreen.kt`、`AuthorScreen.kt`、`MarksScreen.kt`、`AudioScreen.kt`、`ToolsScreen.kt`、`SettingsSheet.kt`
- 导航/状态宿主：`android/app/src/main/java/com/alanmaster/xread/MainActivity.kt`
  （`private sealed interface Screen` 定义了全部路由，见文件内 123-136 行）
- 已有设计文档：`docs/ui-design.md`（已含一轮审计 + 一套提案：Space/Radius 4dp 网格、浅色「宣纸」暖白方案、
  深色方案、ReaderPalette 四种护眼底色、带衬线正文的 AppTypography）
- 构建：`bash scripts/build_apk.sh` → `artifacts/x-read-standalone.apk`
- 真机：`adb` 设备 `bbd553fd` 已连接（arm64，1080×2400，density 440）

## 硬约束
- **不改** `data/` 层、`Api.kt`、`native-core/`、任何存储/落盘格式。
- **不改** `ReaderPrefs` 字段（`fontSize` / `lineHeight` / `theme` / `tapTurn` / `volumeTurn`）。
- 只用 Material3；**不引入新依赖**。
- 阅读页的 `ReaderPalette` 与应用外壳主题必须解耦（阅读底色由用户显式挑选，不跟随系统）。

## 现状（请先读真实文件再下结论）
- `Theme.kt`：`Space`（4dp 基网格）、`Radius`、`AppTypography`（衬线正文）、`LightColors`/`DarkColors`。
- `Components.kt`：`BookCover`、`BookCard`、`ShelfBookCard`、`BookGrid`、`ChipRow`、`SelectChip`、`Tag`、
  `SectionHeader`、`SectionTitle`、`EmptyHint`、`Loading`、`DividerLine`、`BottomSheet`、
  `PrimaryButton`、`SecondaryButton`、`XReadTopBar`。
- `ReaderScreen.kt`：**已经是沉浸式**——无 `Scaffold`，顶栏/查找条/底栏/设置面板全部包在
  `AnimatedVisibility` 里，由屏幕中部点击切换 `chromeVisible`；正文由自定义 `theContent()` 逐段渲染。
- `ReaderPalette.kt`：7 个色槽（background/text/title/dim/accent/rule/highlight），
  方案 `auto`/`paper`/`white`/`mint`/`dark`。

## 交付物

### 1. 分屏 IA + 布局规格
覆盖：书架 Shelf、发现（榜单 Rank + 分类 Category）、搜索 Search、书籍详情 Detail、目录 Toc、
阅读器 Reader、书评 Reviews、作者 Author、书签 Marks、朗读 Audio、工具 Tools、设置 Settings。
每屏给出：
- 内容层级（信息架构）
- 主操作 vs 次要操作
- 布局骨架：网格/列表/翻页、吸顶区（sticky header）、底部栏
- 空态 / 加载态 / 错误态的处理

### 2. 设计令牌契约（design token contract）
- 精确的颜色角色（浅色 + 深色）：语义名 → 具体用途 → 色值
- 字号阶梯：哪个角色用哪个 sp
- 间距阶梯、圆角、层级（elevation）
- **基于现有 `Space`/`Radius`/`AppTypography`/`ReaderPalette` 收敛与细化**，不要另起一套平行系统

### 3. 阅读器详规（核心屏）
- 全出血正文列的版心、边距、段间距（中文排版）
- 顶部/底部 chrome 的显隐与行为
- 阅读内设置面板：字体族、字号、行距、背景、翻页方式
- 章内查找（find-in-chapter）
- 主题色板与阅读底色的关系

### 4. 组件清单
`Components.kt` 需要新增/改动的组件，给出函数签名。

### 5. 实施计划
分阶段，**每阶段可独立出包、独立真机验证**，标注验证方法。

### 6. 风险与非目标
明确列出不要碰的东西、可能回归的地方。

## 输出要求
- 先读真实文件，再下结论；引用真实路径与符号名。
- **果断**：给出唯一选择，不要罗列多个选项。
- 中文叙述可以，代码标识符保留英文。
- **不要修改任何文件**，只产出规格文本。
