# x-read 功能增强计划书（v3.4 → v3.6）

> 基线：`3.3.1-standalone`（versionCode 7，minSdk 24 / targetSdk 34，仅 arm64-v8a）
> 现状：**v3.4 已实现**（`3.4.0-standalone` / versionCode 8，2026-11-05），F1–F6 全部落地；**v3.5 已实现**（`3.5.0-standalone` / versionCode 9，2026-11-05），F7–F10 落地、F11 未做；**v3.6 已实现**（`3.6.0-standalone` / versionCode 10，2026-11-05），F16–F19 全部落地，394 个单测通过；旧的「流动」四项（F12–F15）搁置。**后续追加 v3.7**（`3.7.0-standalone` / versionCode 12，2026-11-05）：书架按连载状态自动分组，384 个单测通过；**v3.8**（`3.8.0-standalone` / versionCode 13，2026-11-05）：整包热更新（应用内自更新）+ 下载加速通道，403 个单测通过；**v3.8.1**（`3.8.1-standalone` / versionCode 14，2026-11-06）：仓库与 Release 公开、更新源免令牌，跳过 debug/unsigned 资源，404 个单测通过。
> 范围：Android 独立版（`android/app/src/main/java/com/alanmaster/xread/`）。
> 服务端 `x_read/` 本期不动：客户端已内嵌 Rust 内容核心，不依赖自建 API。

---

## 一、结论先行

现有产品在「内容获取」上已经很厚：榜单、分类、搜索、听书、短剧、段评、书评全都有。真正的短板不在内容，而在**阅读这件事本身的可控感与成长感**。三条主线按价值排序：

1. **离线是黑盒** —— 批量下载只能「整本 / 前 N 章」，目录页看不到哪章已在本地，用户无法单章补缓存，也无法只清掉某一章。
2. **统计只有总量** —— 只有累计分钟/万字/章节和近 7 天柱状，没有「今天读了多少」「连续坚持了几天」，读完没有反馈闭环。
3. **书摘只能看不能写** —— `Mark` 只有 `text + kind`，没有笔记和标签；本地已下载的正文也没有任何跨书检索入口。

对应三个版本：**v3.4「节奏」**（目标 + 章节级缓存 + 阅读器自动化）、**v3.5「索引」**（笔记 + 全文搜索 + 书籍状态）、**v3.6「流动」**（同步 + 小组件 + 分享）。

---

## 二、现状盘点

| 能力域 | 已实现 | 关键落点 |
|---|---|---|
| 阅读 | 连续双向阅读、章内查找、段评气泡、四种背景、字体/行距/段距、点按与音量键翻页、段落记忆 | `ui/ReaderScreen.kt`(2798)、`data/ReaderPrefs.kt` |
| 听书 | 系统 TTS 前后台、段落跟随、倍速、定时关闭、官方有声书榜与真人音频 | `data/TtsService.kt`、`ui/AudioScreen.kt` |
| 书架 | 分组、排序（最近/入架/书名）、卡片/列表视图、查更新、长按菜单 | `ui/ShelfScreen.kt`(819) |
| 离线 | 整本/前 N 章下载、仅 Wi-Fi、只下未读、容量上限、断点续传、存储管理 | `data/Downloader.kt`、`data/OfflineRepo.kt`(1053) |
| 产出 | 书签/高亮/书摘跨书列表、Markdown 导出、1080 PNG 分享卡、整本 TXT | `data/Marks.kt`、`ui/MarksScreen.kt` |
| 发现 | 热榜、新书、猜你喜欢、漫画、短剧、抖音式刷剧、分类（194 个）、书评/作者页 | `ui/DiscoverScreen.kt`(771) 等 |
| 数据 | 总秒/总字/总章 + 近 7 天秒数；进度、设置、替换规则、追剧记录 | `data/OfflineRepo.kt:391 addStats` |
| 备份 | 手动导出/导入 JSON（书目/进度/书摘/统计/规则/追剧），不含正文 | `data/OfflineRepo.kt:857 exportBackup`（version=2） |

**明确缺失**（已用源码检索确认，全为 0 命中）：阅读目标/打卡、笔记与标签、全文检索、批量选择、章节级缓存标记、屏幕常亮、自动滚动、WebDAV/云同步、桌面小组件、首启引导、触感反馈。

---

## 三、设计约束（所有版本必须遵守）

1. **单一数据源**：能推导的不新建文件。例如「连续天数」直接从 `stats.json.days` 推导，不另存一份可能失同步的 streak。
2. **原子写**：新增数据一律走 `OfflineRepo.write()` / `mutate()`，损坏文件保持现有「隔离另存 + lastWarning」策略。
3. **备份向前兼容**：`exportBackup` 的 `version` 从 2 升到 3，导入端必须继续吃下 v2；新增字段缺失时按默认值处理，不得丢旧数据。
4. **零新依赖**：v3.4 / v3.5 不新增 Gradle 依赖；v3.6 的 Glance、security-crypto 需单独立项评审（APK 体积与 16KB 对齐都要复验）。
5. **设计 token 不扩容**：只用 `Space` / `Radius`（0 / 10 / 20）/ `Elevation` / `Theme`，不引入第四档圆角或新主色。
6. **离线优先**：除内容请求外不新增网络行为；任何后台任务失败都必须有可操作的文案。

---

## 四、v3.4「节奏」——让阅读有刻度

### 实施记录（2026-11-05，`3.4.0-standalone`）

| 项 | 落点 |
| --- | --- |
| F1 | `data/GoalMath.kt`（纯函数：达标/连续/回看窗口）、`data/OfflineRepo.kt` 的 `readingGoal()` / `saveReadingGoal()`、`ToolsScreen` 的「阅读目标」卡（Canvas 环形进度 + 10/20/30/45/60 分钟 chips） |
| F2 | `OfflineRepo.downloadedChapters()` / `removeChapter()` / `chapterKey()`（`companion`，UI 可调）、`Downloader.startRange()`、`TocScreen` 行尾缓存圆点 + 长按菜单 + 「已缓存 N」 |
| F3 | `ReaderPrefs.keepAwake`（默认开，`LocalView.keepScreenOn`）与 `ReaderPrefs.autoScroll`（off/慢/常速/快 = 0/14/26/42 px/s，`withFrameNanos` 逐帧 `scrollBy`，触摸即暂停，右下角「自动/继续」胶囊） |
| F4 | 书架长按菜单「多选 / 设置分组」；选择模式顶栏与底部动作条（分组 / 缓存 / 读完 / 移出）；移出走 5 秒 Snackbar 撤销（整条 `ShelfItem` 备份，`LocalLibrary.restore` 写回）；批量缓存用 `cacheQueue` 串行泵 |
| F5 | `ui/OnboardingScreen.kt` 三步（外观 / 导入备份 / 三条路径），`settings.json` 的 `onboarded`；本地已有书架或进度时 `needsOnboarding()` 直接返回 false |
| F6 | `ui/Feedback.kt` 的 `rememberHaptic()` 三档（light/medium/confirm），尊重系统触感开关与 TalkBack |

实施中与原计划的偏差，以及原因：

- **`OfflineRepo.addStats` 保持返回 `Boolean`**。原计划让它返回「今日累计秒数」以便判断是否跨过目标线，但既有 `OfflineRepoSafetyTest` 把返回值当写盘成功标志用。改成在 ViewModel 里维护 `todayKey` / `todayCache` 内存缓存：结算成功就累加，跨日归零，`loadReadingGoal()` 初始化一次，比每次翻页回读 `stats.json` 便宜。
- **`HapticFeedbackType.Confirm` 在当前 Compose 版本不存在**，`confirm()` 用 `LongPress` 顶上，不再区分两档强度。
- **自动滚动不套「跨章自动续读」的新逻辑**：触底续章原本就有（`onAppendNext`），自动滚动到章尾只是暂停，续上后再滚，避免两套逻辑同时拉章节。
- **F5 的「导入备份」不自动跳步**：导入结果由既有 Snackbar 反馈，引导页保持当前步，失败也不挡路。

### F1 每日阅读目标与连续打卡

**价值**：把「累计 1654 分钟」变成「今天还差 7 分钟」，形成每日回访理由。

**数据**
- `settings.json` 新增 `readingGoal: { enabled: Boolean, minutes: Int }`，默认关闭、30 分钟。
- **不新增 streak 文件**：`stats.json.days` 已有 `yyyy-MM-dd → 秒`，直接推导。

**纯函数**（新增 `data/GoalMath.kt`，便于单测）
```
fun todaySeconds(days: JSONObject, today: String): Long
fun metOn(days: JSONObject, goalMinutes: Int, date: String): Boolean
fun streak(days: JSONObject, goalMinutes: Int, today: String): Int   // 今天未达标时从昨天往回数
```

**接入**
- `LibraryViewModel.flushStats()` 结算后比较结算前后的今日秒数，跨界时置 `goalJustMet` 一次性事件。
- `ToolsScreen` 统计区顶部插入进度环（今日 23/30 分钟）+「连续 N 天」徽章；未开启目标时整块隐藏。
- 阅读页达标时用现有 `message` 通道提示一次（不弹窗、不打断翻页）。

**交互**：目标 10 / 20 / 30 / 45 / 60 分钟 + 自定义（1–240）；可在工具页随时改。

**验收**
- 跨零点：23:59 读到 00:01，时长分别记入两天。
- 改目标后今日进度与连续天数即时重算（历史按新目标重算，文档写明这是有意为之）。
- 断更一天后 streak 归零；闰日、跨年、时区（本地日期）各有单测。

**风险**：低。唯一需要注意的是 `days` 只保留有阅读记录的日期，缺失日期即断档——这正好是想要的口径。

---

### F2 目录缓存状态 + 单章/范围下载

**价值**：现在下载是整本黑盒；用户想知道「我离线到第几章了」「能不能只补这一章」。

**数据**
- `OfflineRepo.downloadedChapterIds(bookId): Set<String>`：扫 `books/<id>/c_*.json`，按书缓存并在 `putChapter` / `removeChapter` 后失效；`chapterCount` 已有同款扫描可复用。
- `OfflineRepo.removeChapter(bookId, chapterId)`：单章删除（目前只有整本 `removeBook`）。

**下载器**
- `Downloader.startRange(book, chapters, from, to)`：复用现有 AIMD 并发、预算与续传逻辑，只替换 `pending` 的筛选区间；`start(book, maxChapters)` 改为它的特例，不复制循环。

**UI**（`ui/TocScreen.kt:74` 函数签名新增两个参数）
- 行尾 6dp 圆点：已缓存=实心弱色；未缓存=空心；下载中=主色脉冲。
- 工具行右侧显示「已缓存 120 / 939」。
- 长按行弹菜单：`缓存本章` / `缓存后 50 章` / `删除本章缓存`（仅已缓存时出现）。
- 当前章的三重标识（主色 + 左竖条 + 底色）保持不变，缓存标记不得抢主色。

**验收**
- 939 章目录滚动不掉帧（`Set` 查询 O(1)，不在 composition 里做 IO）。
- 下载中断后重进目录，标记与实际文件一致。
- 删除单章后计数、容量页、备份导出同步更新。

**风险**：中。`TocScreen` 是纯展示组件，新增参数会波及 `MainActivity` 调用点；改动集中在签名与一行渲染。

---

### F3 阅读器自动化

**价值**：长时间阅读时不必每屏抬手；这也是「听书之外」第二种免手操作。

**内容**
1. **屏幕常亮**：进入阅读页设置 `FLAG_KEEP_SCREEN_ON`，离开清除；设置项默认开（`ReaderPrefs.keepAwake`）。
2. **自动滚动**：`ReaderPrefs.autoScroll`（off / slow / normal / fast，约 12 / 24 / 40 px/s）。
   - 用现有 `LazyListState.scrollBy`，每帧小步、恒定速度，不做惯性。
   - 任一触摸、音量键、弹层/菜单/段评打开即暂停；恢复由用户点右下角「自动」胶囊。
   - 滚到章末自动 `appendNextChapter()`，接上后继续；失败则停在章末并提示重试。
3. **亮度**：暂不做应用内亮度覆盖（系统亮度已够用，且各 ROM 行为不一）；只保留常亮。

**UI**：阅读设置面板新增「自动滚动」四档；开启时右下角显示小胶囊「自动 · 点击暂停」。

**验收**
- 连续自动滚动 10 分钟，段落定位不错位、不跳章。
- 段评气泡打开期间必须停；关闭后不自动恢复（避免误滚）。
- 退出阅读页后不残留常亮标记。

**风险**：中。与双向连续阅读、段落记忆的交互需要真机验证（低端机掉帧会让速度看起来不均匀）。

---

### F4 书架批量管理

**价值**：书多了以后逐本长按分组/删除很折磨。

**交互**
- 入口放在现有长按菜单里的「多选」，**不改变**现有长按即菜单的习惯（避免破坏肌肉记忆）。
- 选择模式：顶部栏变「已选 N 本 / 全选 / 取消」，卡片右上角勾选圈。
- 操作：移动到分组、移出书架、缓存全书、标记已读完、取消。
- 移出书架走现有 `removeFromShelf`，随后 5 秒 Snackbar 可撤销（先备份 `ShelfItem`，撤销即写回）。

**验收**
- 200 本批量移出在 IO 线程完成，无 ANR。
- 撤销窗口内写回分组与入架时间不丢。
- 选择模式下「查更新」「排序」禁用并给出原因。

**风险**：低。

---

### F5 首次启动引导

**价值**：现在首启直接落在空书架，用户不知道「点右上角搜索」「番茄源」这些前提。

**内容**：3 步，一屏一步，可跳过。
1. 阅读外观：背景四选一 + 字号滑杆（直接写 `ReaderPrefs`）。
2. 恢复数据：说明备份文件位置，提供「导入备份」。
3. 完成：一句话说明离线与听书入口。

**数据**：`settings.json.onboarded = true`；已存在书架/进度数据时直接跳过引导（老用户升级不打扰）。

**验收**：仅首启出现一次；跳过后不再出现；导入失败不阻塞进入。

---

### F6 反馈统一（触感 + Snackbar）

- 新增 `ui/Feedback.kt`：`rememberHaptic()` 封装 `HapticFeedbackType`（TextHandleMove / LongPress / Confirm）。
- 落点：翻页成功（轻）、长按菜单（中）、加入书架/下载完成/目标达成（确认）。
- 统一 Snackbar 宿主放 `MainActivity`，逐步替换页面内散落的 `message` 文本展示（按页面分批，不一次性大改）。
- **尊重系统「减少动画/关闭触感」**：读 `Settings.System.HAPTIC_FEEDBACK_ENABLED` 与 `AccessibilityManager`。

**验收**：同一动作不出现「Snackbar + 页内提示」双份；TalkBack 能读到提示文本。

---

## 五、v3.5「索引」——让产出可积累、可找回

### F7 笔记（书摘附注 + 标签）

**数据**（`data/Marks.kt`）
- `Mark` 增 `note: String = ""`、`tags: List<String> = emptyList()`。
- `OfflineRepo.markJson()` 写入两字段；读取缺字段按空处理，**旧 marks.json 不需要迁移**。
- 备份 `version = 3`；导入 v2 时字段缺失按默认值，不报错。

**UI**（`ui/MarksScreen.kt`）
- 点击条目 → 底部编辑面板：原文（只读）、笔记（多行，上限 2000 字）、标签（输入 + 常用标签 chips）。
- 列表行显示笔记摘要（2 行截断）与标签；新增筛选：全部 / 书签 / 高亮 / 有笔记。
- 跨书列表支持按书过滤（已有数据结构，补一个下拉即可）。

**导出**：Markdown 每条改为
```
> 原文……
— 第 12 章 · 2026-11-05

笔记：……
标签：#人物 #伏笔
```

**验收**
- 500 字笔记编辑不卡顿；输入 300ms 防抖落盘。
- 删除摘录时笔记一并删除（同一对象）。
- 导出格式与旧版共存：无笔记的条目输出与现在完全一致。

**风险**：低。

---

### F8 跨书全文搜索

**价值**：离线书攒多了以后，「那句台词在哪本书里」目前无解。

**范围**：仅搜**已下载正文**（`books/<id>/c_*.json`），不触发网络。

**实现**（新增 `data/FullTextSearch.kt`）
- `fun search(query: String): Flow<SearchProgress>`：遍历 `offlineBooks()` → 逐章读 JSON → 段落级 `contains`（忽略大小写，中文不分词）。
- 每 20 章 `yield()` 一次；`currentCoroutineContext().isActive` 检查取消；单次最多 200 条命中。
- 结果结构：`bookId / bookTitle / chapterId / chapterTitle / paragraphIndex / snippet(±20 字)`。
- 输入防抖 300ms；空 query 不出结果。

**入口**：`SearchScreen` 顶部新增「书库」tab（无离线书时隐藏，避免空 tab）。

**结果页**：按书分组，命中词高亮；点击直达阅读页并定位到该段（复用 `openMark` 的定位路径）。

**性能预算**：单本 1000 章（约 3 MB）在中端机 < 2 s 出首屏；搜索期间列表滚动不掉帧（纯 IO 线程 + 批量回主线程）。

**验收**
- 搜索中返回键立即取消，不残留后台读盘。
- 无命中、无离线书各有明确空态。
- 正文替换规则**不参与**搜索（搜的是原文，避免用户找不到自己看到的字）。

**风险**：中。书多时 IO 抖动；预算兜底方案见 F15。

---

### F9 书籍状态 / 评分 / 标签

**数据**：`library/shelf.json` 每条增 `status`（want/reading/done）、`rating`（0–5）、`tags: []`。缺失按 `reading` 处理，保证老数据语义不变。

**交互**
- 书架筛选 chip 增加「想读 / 在读 / 读完」（与现有媒体类型 chip 分两行，避免一行塞满）。
- 详情页操作条：状态切换 + 星级 + 标签编辑。
- 状态不自动改写，只在读到末章时提示一次「标记为读完？」——自动判定会把「跳着看」误伤。

**验收**：状态与阅读进度互不覆盖；备份恢复保留；筛选与分组可叠加。

---

### F10 每日阅读提醒

- 设置项：开关 + 时间（默认 20:30）+「仅未达标时提醒」（依赖 F1）。
- 复用现有 `UpdateCheckReceiver` 的 AlarmManager 模式，独立 notification id 与 channel，避免与新章提醒互相覆盖。
- 关闭开关即 `cancel`；设备重启后由现有开机重排逻辑一并恢复。

**验收**：改时间后旧闹钟被取消；同一天不重复提醒；未授权通知时开关置灰并给出去授权入口。

---

### F11 平板 / 横屏双栏（待评估）

宽 ≥ 720dp 时阅读页双栏，按段落边界分栏、不截断段落。涉及 `ReaderFlow` 排版与翻页手势，风险高于本期其它项，建议单独立项，不默认进 v3.5。

---

### 实施记录（2026-11-05，`3.5.0-standalone`）

| 项 | 落点 |
| --- | --- |
| F7 | `data/Marks.kt` 的 `note` / `tags` / `NOTE_LIMIT` / `TAG_LIMIT` / `splitTags()` / `normalizeTags()`；`OfflineRepo.setMarkNote()` / `markOf()` / `exportMarks()`（批注条目引用块）；`ui/MarksScreen.kt` 两行筛选 + `NoteSheet` 300ms 防抖 |
| F8 | `data/FullTextSearch.kt`（`TextHit` / `SearchProgress` / 200 条上限 / 每 20 章 yield）；VM `searchLibrary()` / `openTextHit()`；`ui/SearchScreen.kt` 的「书库」chip + `LibraryResults` |
| F9 | `ShelfItem.status/rating/tags` + `STATUS_LABELS`；`LocalLibrary.setStatus/setRating/setTags`；`ShelfSort.apply(..., status)`；`ui/ShelfScreen.kt` 状态 chip 行；`ui/DetailScreen.kt` 的「我的标记」块 |
| F10 | `data/ReadingReminder.kt`（`ReminderScheduler` 0x7EA4 / `ReminderReceiver` / channel `xread_reminder`）；`settings.json` 的 `reminder`；`ToolsScreen` 的「每日提醒」卡 + `TimePicker` |
| F11 | 未做（按计划单独立项） |

偏差与取舍：

- **没有开机广播接收器**，计划书写的「设备重启后由现有开机重排逻辑恢复」不成立——现有实现只靠
  `MainActivity` 启动时重排。代价是重启后到下次打开应用前不响；相比如新增一个 `BOOT_COMPLETED`
  接收器（多一个权限面与后台入口），选择了不扩权。
- 计划书要求「未授权通知时开关置灰」，实际做成**开关仍可点、点开即请求权限、被拒后卡内红字提示 +
  「去授权」**：开关置灰会让用户找不到求助入口。
- 全文搜索按「已落盘章节」扫，而不是按目录扫：目录里 939 章、实际下载 50 章时，搜目录会给出点不开的结果。
- 状态/评分/标签只写本地 `shelf.json`，不参与备份以外的任何同步；`ShelfItem` 旧数据缺字段时按
  `reading` / 0 / 空列表兜底。

## 六、v3.6「随身」——让离线成为默认，而不是提前准备

### 为什么换方向

原来的 v3.6 是「流动」：WebDAV 同步、桌面小组件、分享卡与月报、FTS5 索引。四条都有明确价值，
但都建立在同一个前提上——**用户已经能顺畅读完**。而现在的实际断点更靠前：

1. 离线是「用户任务」：要么整本下载，要么在目录里一章一章点。读者必须先想到「我要出门」，
   否则地铁里翻到下一章就是加载失败。v3.4 已把缓存做到章节级、v3.5 已把数据做成可检索，
   中间缺的正是「自动把窗口内的内容放到本地，并把窗口外的清掉」这一层调度。
2. 排队下载没有优先级：正在读的书和随手加入的八本书抢同一个下载器，先来先得。
3. 读不下去的时候没有出路：章末失败只留一句错误，用户得自己回目录、自己找、自己点。

所以 v3.6 只做一件事：**把「提前准备」从用户手里拿走**。不新增依赖，全部复用 `Downloader`、
章节文件与 `DownloadPolicy`。

### F16 阅读预取（读哪几章，就备哪几章）

- 设置项「自动预取」：关 / 后 1 章 / 后 3 章 / 后 5 章 / 后 10 章，缺省 3。
- 触发点：打开书、进入新章、章末续接、从目录跳转——都算「当前窗口变了」，重新算一次缺口。
- 缺口 = 当前章往后 N 章里**磁盘上没有**的章，按顺序排进预取队列，一次只跑一本、一个任务。
- 预取是**可抢占的低优先级任务**：用户点「缓存本章 / 缓存全书 / 下载」时立刻让路（`awaitIdle` 之后
  再恢复），永不与手动任务抢并发窗口。
- 网络：遵守现有 `wifiOnly`；移动网络下预取直接跳过，不提示。失败静默重试一次，不弹 Snackbar。
- **验收**：只缓存了当前章的书，飞行模式下连续后翻 3 章全部可读；预取期间界面无任何提示，
  但目录页圆点会逐个变实。

### F17 缓存窗口与自动清理

- 规则：保留「当前章前 2 章 ～ 后 N 章」，超出窗口的**预取章**自动删除。
  手动的整本 / 范围下载**永不自动删**——用户按下下载就是明确意图，不能被后台清理掉。
- 实现：`putChapter()` 增可选标记 `auto: true`（缺字段视为 false，旧文件无需迁移）；
  清理只针对 `auto == true` 且不在窗口内的章。
- 时机：章节切换后、预取任务收尾后各扫一次；单本书一次最多删 50 章，避免长任务卡界面。
- 与容量上限的关系不变：`maxBytes` 仍然兜底，超限时优先删最旧的 auto 章。
- **验收**：连读 50 章后本地只剩窗口内的 auto 章；手动下载的章数量一条不差；
  删章后目录圆点、「已缓存 N」、存储统计同步更新。

### F18 下载队列：当前书先下

- 现在 `cacheBooks()` 是 FIFO。改成：手动发起时，**如果这本书就是当前打开的书，插到队首**。
- 工具页「下载状态」卡补一行队列信息：「正在下 A（12/50）· 后面还有 2 本」。
- `cancelDownloads()` 同时清空队列（已有行为），文案说明「已取消队列里的 N 本」。
- **验收**：连续给 3 本书点缓存，正在读的那本先完成；取消后不再有任务偷偷启动。

### F19 读不下去时给一条出路

- 阅读页章末：下一章既没缓存、请求也失败时，压一条细提示「下一章还没下载 · 缓存后 5 章」，
  点击即 `startRange(book, 当前+1, 5)`，成功后原地续接，不跳页。
- 目录页工具行右侧的「已缓存 N / M」可点，弹出「补全当前窗口」——按预取设置一次补齐缺口。
- **验收**：提示只在真正读不下去时出现；恢复网络后提示自动消失，不残留。

### 优先级与依赖

| 顺序 | 项 | 依赖 | 价值 |
| --- | --- | --- | --- |
| 1 | F16 预取 | 现有章节缓存 | 直接消掉「断网读不了」 |
| 2 | F17 清理 | F16 的 auto 标记 | 防预取把存储吃满 |
| 3 | F19 出路 | F16 | 兜住预取没覆盖到的场景 |
| 4 | F18 队列 | 现有 `cacheBooks()` | 顺手，改动最小 |

### 明确不做

- 不做后台常驻服务或 WorkManager：预取只在前台阅读时跑，进程被杀就停——电量与包体积都不值。
- 不做「自动整本下载」：那会把存储和流量决策从用户手里拿走，和 F17 的窗口策略也冲突。
- 不做预取进度条：预取应当是无感的，看见进度条反而变成负担。

### 旧 v3.6 的处置

F12（WebDAV 同步）、F13（Glance 小组件）、F14（分享卡与月报）、F15（FTS5 索引）**搁置**，
分别等「跨设备诉求出现」「主屏入口确有缺口」「分享频次起来」「离线书 > 30 本」再议。
不是否决，是不排进这一版。

### 实施记录（2026-11-05，`3.6.0-standalone`）

| 项 | 落点 |
| --- | --- |
| F16 预取 | `data/Prefetcher.kt`（独立于 `Downloader` 的单任务循环，手动下载时挂起让路）；`DownloadPolicy.prefetchChapters` 与 `PREFETCH_CHOICES/LABELS`；`LibraryViewModel.afterChapter()` 在 `loadChapter` 成功、`appendNextChapter` 成功、`prependPreviousChapter` 成功三处触发；设置面板「阅读时预取」分段 |
| F17 窗口清理 | `OfflineRepo.putChapter(..., auto: Boolean = false)` + 每本书一个 `auto.json` 索引（`autoChapters/markAuto/trimAutoChapters`）；`removeChapter` 同时摘掉索引项；`LibraryViewModel.trimAutoWindow()` 用「前 2 章 ～ 后 N 章」算保留集，单次最多删 50 章 |
| F19 出路 | 阅读页 `flow-error` 块加「缓存后 5 章」→ `vm.cacheNext()`（`startRange` + `awaitIdle` + `appendNextChapter`）；目录工具行加「补全」→ `vm.fillWindow()` |
| F18 队列 | `cacheBooks()` 把当前打开的书插到队首；`UiState.cacheQueueSize` 驱动工具页「后面还有 N 本排队」；`cancelDownloads()` 清空后提示取消了几本 |

**实现偏差（有意为之）**

1. 预取**没有并进 `Downloader`**。两者优先级相反：塞进同一个单任务循环，要么预取顶掉用户任务，要么用户任务饿死预取。
   让路方式定为「挂起等待」而不是「取消重排」——用户下载跑完，预取从断点继续，不必重算缺口。
2. 「标记预取章」用**一本书一个 `auto.json`**，不写进章节 JSON：判来源要读整章正文，939 章就是几十 MB 解析，而索引文件只有几百字节。
3. 清理只在**进章 / 续接 / 退章**时扫一次，单次上限 50 章；没有后台定时清理——不做常驻服务是本节的前提。
4. F19 的目录入口做成工具行右侧「补全」按钮，而不是把「已缓存 N」变成可点文本：那串字还要承担「筛选后还剩多少」的说明职责，做成按钮反而看不懂。
5. 排队插队**只对当前打开的书生效**，其余仍先来先得；不做全量优先级排序，避免用户看不出谁先谁后。

### 追加：书架按连载状态自动分组（2026-11-05，`3.7.0-standalone`）

原文的 v3.6 只管「读得下去」，没管「找得到」。书架上的书一多，手工分组要用户自己建、自己搬，
书完结了还得再搬一次——分类会腐坏，于是分类本身就被放弃了。

- 新增 `data/ShelfGrouping.kt`：`BUILTIN`（`@serial` 连载中 / `@finished` 已完结）、`matches(item, group)`
  按 `Book.status` 判定、`summarize(items)` 只返回有书的分组。
- `LibraryViewModel.ShelfSort.apply()`：`byGroup` 分内置/手工两支；内置分组内退回「最近」排序。
- `ui/ShelfScreen.kt`：`ShelfToolbar` 菜单加「按连载状态」区块（label + 本数 + 选中打勾），
  `ShelfScreen` 传 `builtinGroups = ShelfGrouping.summarize(state.shelfAll)`（`MainActivity` 组装）。
- 单测 `ShelfGroupingTest`（10 项）：未知状态算连载、完本判定、空组不进菜单、内置组与手工组并存、
  `ShelfSort` 分组与状态过滤叠加。
- 有意不做：不落盘分组、不做「连载中 → 已完结」的历史归档、不在书架上加常驻分组标题行。

### 追加：整包热更新（2026-11-05，`3.8.0-standalone`）

v3.7 之后改一行 UI 也得让用户自己去 Release 页找包重装——应用自己不知道「我是不是旧版」。

- 新增 `data/AppUpdate.kt`（纯逻辑）：`parse()` 同时吃 GitHub Release JSON 与自建 `update.json`；
  `assetScore` 挑 standalone / arm64 并惩罚 debug、unsigned；`compareVersions` + `isNewer`（两边都有 versionCode 时以它为准）；
  `sha256`；`dueForCheck`（每天一次，时钟回拨按到期处理）。
- 新增 `data/AppUpdater.kt`：`StateFlow` 状态机（检查中 / 下载中带进度 / 包已就绪），下载落 `cacheDir/update/` 先写 `.part` 再改名，
  清单带 sha256 时校验不过就删包；安装走 `FileProvider` + `ACTION_VIEW`，Android 8+ 未授权先跳「安装未知应用」。
- `ui/SettingsSheet.kt` 的「03 关于」加 `UpdatePanel`（开关、更新源与令牌编辑、状态行、进度条、检查/下载/安装按钮）；
  `MainActivity` 启动时 `hydrate()` + `autoCheckOnLaunch()`；`OfflineRepo` 增 10 个偏好
  （`hotUpdateEnabled` / `updateSource` / `updateToken` / `lastUpdateCheckAt` / `pendingUpdate`）。
- CI：`v*` 标签且产物是签名包时才生成 `update.json`（versionName / versionCode / url / size / sha256）并随 APK 附到 Release，debug 包不产清单。
- 下载加速：`AppUpdate.MIRRORS` 给四条通道（直连 / ghfast.top / gh-proxy.com / ghproxy.net），前缀只拼在 APK 直链上；
  `AppUpdater` 先试加速、失败自动退回直连，`isGitHubHost` 保证令牌不会发给第三方代理。
- 单测 `AppUpdateTest`（20 项）：两种清单来源、坏载荷返回 null、版本清洗与逐段比较、versionCode 优先、资产打分、每日节流、文件名去目录穿越、sha256 校验，
  以及加速前缀、重复前缀、http 源不代理、未知通道回退直连。
- 有意不做：不做 dex 补丁 / 插件化（没有补丁分发通道，签名托管成本高于收益）；不做静默安装（需要系统签名）；不在后台偷偷下包。

## 七、横向 UX 优化（随版本搭车，不单独立项）

| 项 | 现状 | 改法 | 版本 |
|---|---|---|---|
| 骨架屏 | 首屏与详情是空白 + 转圈 | 书架/详情/榜单用灰块占位，避免布局跳动 | 3.4 |
| 空态 | `EmptyHint` 只有一行字 | 补图标 + 一个行动按钮（去搜索 / 去分类） | 3.4 |
| 错误分级 | `ErrorClass` 已有分类 | 统一「可重试 / 需换网 / 内容下架」三档文案与按钮 | 3.4 |
| 触感 | 无 | 见 F6 | 3.4 |
| 无障碍 | 部分图标无 `contentDescription` | 全量审计；动态字体放到 1.3× 不截断 | 3.4–3.5 |
| 动效 | 少量 `AnimatedVisibility` | 页面转场与列表入场统一 200ms；尊重「减少动画」 | 3.5 |
| 搜索 | 有历史（12 条） | 加热词与结果高亮；历史支持单条删除 | 3.5 |
| 章末 | 自动接下一章 | 章末插入「本章完 · 下一章」卡片（可关） | 3.5 |
| 通知 | 新章提醒单一渠道 | 统一渠道管理页，可分别关闭 | 3.5 |

---

## 八、优先级矩阵

| 功能 | 用户价值 | 实现成本 | 风险 | 版本 |
|---|---|---|---|---|
| F1 目标与打卡 | 高 | 低 | 低 | 3.4 |
| F2 章节级缓存 | 高 | 中 | 中 | 3.4 |
| F3 自动滚动/常亮 | 中高 | 中 | 中 | 3.4 |
| F4 书架批量 | 中 | 中 | 低 | 3.4 |
| F5 首启引导 | 中 | 低 | 低 | 3.4 |
| F7 笔记 | 高 | 中 | 低 | 3.5 |
| F8 全文搜索 | 高 | 中高 | 中 | 3.5 |
| F9 状态/评分 | 中 | 低 | 低 | 3.5 |
| F10 阅读提醒 | 中 | 低 | 低 | 3.5 |
| F16 阅读预取 | 高 | 中 | 中 | 3.6 |
| F17 缓存窗口 | 高 | 中 | 中高 | 3.6 |
| F18 队列优先 | 中 | 低 | 低 | 3.6 |
| F19 读不下去的出路 | 中高 | 低 | 低 | 3.6 |
| F12 WebDAV / F13 小组件 / F14 卡片月报 / F15 FTS5 | — | — | — | 搁置 |

**已完成**：v3.4 按 F1 → F2 → F4 → F5 → F6/横向 → F3 顺序落地；v3.5 按 F7 → F8 → F9 → F10 落地。
**v3.6 建议顺序**：F16 → F17 → F19 → F18（预取先跑通，清理与出路才有对象；队列改动最小放最后）。

---

## 九、技术规范

**存储**
- 新文件仅在必要时应运而生：`goals` 不建文件（推导），`notes` 并入 `marks.json`，`status/rating/tags` 并入 `shelf.json`。
- 所有写入继续走 `write()`（`.tmp` + rename）与 `mutate()`；新增字段读取一律 `opt*` 带默认值。

**备份**
- `version: 2 → 3`；新增 `marks[].note/tags`、`shelf[].status/rating/tags`、`settings.readingGoal`。
- 导入端按「有则覆盖、无则保留」的现有语义扩展，v2 备份导入后行为与现在完全一致。

**性能预算**
- 冷启动到书架可交互 < 1 s（现有基线不退化）。
- 目录滚动 60 fps；全文搜索单本首屏 < 2 s。
- 新增后台任务全部 `Dispatchers.IO`，主线程不做 `stat` / `readText`。

**测试**
- 纯函数单测：`GoalMath`（跨零/跨年/断档）、`downloadedChapterIds` 失效、`FullTextSearch` 匹配与取消、`Mark` 序列化兼容。
- 现有 40+ JVM 测试全绿是合并前提：`./gradlew :app:testDebugUnitTest`。
- 手工回归清单见下。

**构建**
- 不改 `scripts/build_apk.sh` 的自检项；新增依赖时需同步确认 `minifyEnabled` 的 keep 规则与 `.so` 16KB 对齐。
- 版本号：v3.4 = versionCode 8 / `3.4.0-standalone`。

---

## 十、发布前验收清单

1. `./gradlew :app:testDebugUnitTest` 全绿。
2. `bash scripts/build_apk.sh` 产出签名包，脚本自检通过。
3. 手工回归：
   - 冷启动 → 恢复上次阅读 → 连续读到下一章 → 退出重进位置一致。
   - 下载整本 → 断网 → 目录标记正确、可离线读 → 单章删除后标记消失。
   - 目标达成提示只出现一次；改目标后今日进度重算。
   - 自动滚动遇到段评气泡必须停。
   - 备份导出 → 清数据 → 导入 → 分组/书摘/统计/笔记全在。
   - 搜索「书库」tab 能定位到段落；替换规则不影响搜索结果。
4. 覆盖安装升级（保留 `com.alanmaster.xread.standalone` 数据）不丢书架与进度。

---

## 十一、明确不做

- **不新增书源**：内容核心固定番茄，多源涉及协议与合规成本，收益不成正比。
- **不做账号体系 / 云书架**：无后端，账号只会制造「登录了却不同步」的预期落差。
- **不引入 WebView**：现有全原生渲染是性能与体积优势，也是独立版的立身之本。
- **不改内容核心协议**：`android/native-core` 保持 vendored 状态，只做 JNI 适配层变更。
- **不做应用内亮度覆盖**：各 ROM 行为不一，收益低于维护成本。
