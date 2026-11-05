# x-read UI 落地记录（v2「纸间」）

> 对应规格：`docs/ui-design-v2.md`。本文只记录**已落地的改动**与**验证方式**，不重复规格本身。
> 代码位置：`android/app/src/main/java/com/alanmaster/xread/ui/`

---

## 1. 令牌层

### 1.1 `Theme.kt`

| 改动 | 前 | 后 | 原因 |
|---|---|---|---|
| `Radius` 档位 | 5 档（6/8/14/18/24dp） | 4 档（8/14/18/24dp） | 同屏不允许超过两档圆角；6dp 档与 8dp 档在一屏里必然同时出现（芯片 + 封面），合并成 8dp |
| `Space.section` | 无 | `24.dp` | 区块间距此前散用 16/20dp |
| `AppShapes.extraSmall` | 6dp | 8dp | 跟随 Radius 收敛 |
| `BottomSheet` 阴影 | `shadowElevation = 6dp` | 仅 `tonalElevation` | 层级靠发丝线与遮罩，不靠投影堆叠 |

`Radius.xs` **已删除**，全项目引用改为 `Radius.sm`。

### 1.2 `ReaderPalette.kt`

只改 `dim` 槽（页眉、进度百分比、段评计数等 11–12sp 小字）。正文色不动。

| 方案 | dim 前 | dim 后 | 对比度前 → 后 |
|---|---|---|---|
| 米黄 paper | `#7B6E5E` | `#6B5F50` | 4.18:1 → **5.24:1** |
| 白 white | `#75706A` | `#6E6252` | 4.90:1 → **5.95:1** |
| 浅绿 mint | `#66756A` | `#5F6B60` | 4.07:1 → **4.68:1** |
| 夜色 dark | `#8A8C90` | `#B5ABA0` | 5.18:1 → **7.73:1** |

四个方案原来都低于或贴着 AA 4.5:1，米黄与浅绿明确不过。

阅读底色仍由用户显式挑选（`ReaderPrefs.theme`），**不跟随系统**；只有 `theme = "auto"` 才按系统深浅切到夜色。

---

## 2. 共享组件层

`Components.kt` 新增：

| 组件 | 签名 | 用途 |
|---|---|---|
| `MetaStat` | `(value, label, modifier, emphasized)` | 详情页三列数字块（大号数字 + 11sp 标签） |
| `MetaStatDivider` | `(modifier)` | 数字块之间的竖发丝线 |
| `RankRow` | `(rank, title, coverUrl, meta, modifier, badge, badgeEmphasized, onClick)` | 榜单行：衬线序号 + 封面 + 书名/元信息 + 右侧徽标 |
| `ProgressHairline` | `(progress, modifier, color, trackColor, showLabel, label)` | 阅读页常显 2dp 进度线；工具页存储占用条 |
| `SegmentedRow` | `(options, selected, onSelect, modifier)` | 行距 / 倍速 / 定时这类 2–3 选一。**不用** Material `SegmentedButton`：它自带 20dp 圆角，会多出第三档圆角 |
| `PaletteSwatch` | `(label, background, foreground, selected, onClick, modifier)` | 阅读底色色板（32dp 圆点 + 名称），选中用 2dp 外环而非改大小 |
| `ReaderMetrics` | `(textGutter, centerTapRatio, bottomActions)` | 阅读页版心常量，供 `ReaderChromeTest` 钉住 |

调整：

- `Tag`：10sp → 11sp/15sp 行高，圆角 6dp → 8dp，内边距 5dp → `Space.sm`。
- `BookCard`：书名 13sp → 14sp/20sp，元信息 11sp → 12sp/18sp。
- `SectionHeader`：顶部间距 20dp → `Space.section`（24dp）。

---

## 3. 页面层

见各节。

---

## 4. 测试

| 测试 | 守住什么 |
|---|---|
| `DesignTokensTest` | 圆角恰好 4 档且递增、最小 8dp；页面边距 16dp；间距在 4dp 网格上（`xxs=2dp` 是图标内部偏移，唯一例外） |
| `ReaderPaletteTest.dimSlotPassesAaContrast` | 四个阅读方案的 `dim` 槽 ≥ 4.5:1 |
| `ReaderChromeTest` | 正文边距 20dp；中央点按区 60%；底栏四动作 |

运行：`bash scripts/build_apk.sh` 里的 `:app:testDebugUnitTest`。

---

## 5. 构建链修复

镜像 `budtmo/docker-android:emulator_14.0` 自带的 `/opt/android` 只有 build-tools 36.0.0，
而 AGP 8.4 要求 34.0.0，构建会以
`Failed to install the following SDK components: build-tools;34.0.0` 失败。

`scripts/build_apk.sh` 改为：**宿主 SDK 存在 `/tmp/android-sdk/build-tools/34.0.0` 时挂进容器覆盖镜像自带 SDK**，
否则沿用镜像自带 SDK。宿主 SDK 路径可用 `ANDROID_SDK_DIR` 覆盖。

---

## 6. 非目标

- 不改 `data/` 层、`Api.kt`、`native-core/`、任何存储与落盘格式。
- 不改 `ReaderPrefs` 字段（`fontSize` / `lineHeight` / `theme` / `tapTurn` / `volumeTurn`）。
- 不引入新依赖；Material3 为唯一 UI 基础。
- 阅读页 `ReaderPalette` 与应用外壳主题保持解耦。
