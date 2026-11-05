# x-read 独立版（单 APK，无需服务器）

`versionName 3.7.0-standalone`（versionCode 12），包名 `com.alanmaster.xread.standalone`，仅 arm64-v8a，minSdk 24 / targetSdk 34。
内容核心（fqapp / fanqie-lite 的 Rust 业务层）内置在 APK 里，经 JNI 进程内直连上游内容提供方；
**不开本地端口，不需要 NAS、服务端地址或访问令牌**。

## 组成

| 层 | 实现 |
|---|---|
| 内容核心 | `android/native-core`：`fqapi_core`（GPL-3.0，源自 ch6vip/fqapp，vendored）+ JNI 适配层 |
| 适配层 | `src/lib.rs`：`init_core(..., ListenMode::Disabled)`，无监听器；`runtime::request` 直调分发器 |
| Kotlin 桥 | `data/NativeCore.kt`：`nativeInit/nativeRequest/nativeCancel`，协程取消传导到 Rust |
| 解析边界 | `data/ContentParser.kt`：上游 JSON → `Book/Chapter/ChapterContent`，纯 Kotlin 可离线单测 |
| 书架 | `data/LocalLibrary.kt`：`files/library/shelf.json`，原子写（`.tmp` + rename） |
| 进度/统计/规则/备份/下载 | `data/OfflineRepo.kt`（原有能力，改为可注入目录，便于 JVM 测试） |
| 界面 | Kotlin + Compose（无 WebView） |
| 书评 | `ReviewScreen`：最多赞/最新、五星、点赞/回复数、相对时间；**只能靠 cursor 翻页** |
| 作者页 | `AuthorScreen`：头像、粉丝、简介、作品集 |
| 目录页 | `TocScreen`：全量目录 + 标题筛选 + **章号直达**；详情页只预览 12 章 |
| 相关推荐 | 详情页简介之后、目录之前横向滚动（按书名去重、排除当前书） |
| 阅读增强 | 顶部进度条 + 百分比、章内查找（命中高亮、上下跳转）、按段落记忆阅读位置、**双向连续阅读**（流两头都能自动接章）、**目录章号直达**（输 `800` 直接滚到第 800 章） |

数据目录（应用私有，无需存储权限）：

```
files/library/shelf.json            书架
files/offline/books/<id>/*.json     章节缓存
files/offline/{progress,stats,settings,tasks}.json
files/content-core/                 内容核心：pool.key、config/、device_pool.json、src/、rust.log
```

## 构建

```bash
# 一次性：Rust + NDK（已装在 /tmp/xread-native-tools）
export RUSTUP_HOME=/tmp/xread-native-tools/rustup CARGO_HOME=/tmp/xread-native-tools/cargo
$CARGO_HOME/bin/rustup target add aarch64-linux-android
# NDK r27c 解压到 /tmp/xread-native-tools/android-ndk-r27c

# 一次性：release 签名（后续覆盖安装不再需要卸载）
mkdir -p android/signing
/tmp/jdk-21.0.13+9/bin/keytool -genkeypair -keystore android/signing/release.keystore \
  -storetype PKCS12 -alias xread -keyalg RSA -keysize 4096 -validity 10950 \
  -dname 'CN=x-read, OU=standalone, O=alanmaster, C=CN'

SSH_PASS=... XREAD_KEYSTORE_PASS=... bash scripts/build_apk.sh
```

产物：`artifacts/x-read-standalone.apk`（**release** 包，约 10 MB，含 8.0 MB `.so`）。

构建脚本**先自检、后发布**（旧实现先 `cp` 到 `artifacts/` 再校验，自检失败时坏包已经
躺在交付路径上）。任一不满足即拒绝出包，且不会留下产物：
- 合并清单不得含 `android:debuggable="true"`；找不到合并清单直接失败（不再静默跳过）
- 合并清单不得含 `android:allowBackup="true"`
- APK 内必须有 `lib/arm64-v8a/libxread_native.so` /
  `assets/licenses/LICENSE` / `assets/licenses/NOTICE`
- `.so` 的 `PT_LOAD p_align >= 0x4000`（Android 15 的 16KB 页要求）
- `apksigner verify` 必须通过，且证书不得是 `CN=Android Debug`

构建前置条件只有 `android/signing/release.keystore`；**不需要** `debug.keystore`。
缺 release keystore 时 `signingConfig` 留空，产物为 `app-release-unsigned.apk`，
脚本找不到 `app-release.apk` 即失败——不再像旧实现那样静默回退到 debug 签名。

口令只经环境变量 + stdin 进容器，不写源码、不进 argv、不进 APK。
`SSH_PASS` 经 `sshpass -e`（环境变量 `SSHPASS`）传入，同样不落 argv。

### 签名变更需卸载（会丢数据）

debug 与 release 签名不同，`adb install -r` 会报
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`。切换签名必须先卸载，数据会清空。
如需保留数据：先用旧包在「工具 → 导出备份」，装新包后再「导入备份」。
卸载前也可 `adb shell run-as <pkg> tar -c files > backup.tar`（仅 debuggable 包可用）。

### 交付包的安全属性（实测）

| 项 | 值 |
|---|---|
| `pkgFlags` | `HAS_CODE ALLOW_CLEAR_USER_DATA`（**无** `DEBUGGABLE`、**无** `ALLOW_BACKUP`） |
| `run-as` | `run-as: package not debuggable` —— 无法直接读 `content-core/pool.key` |
| 备份 | `allowBackup=false` + `fullBackupContent=false` + `data_extraction_rules` 全排除 |
| 明文流量 | `usesCleartextTraffic=false`；NSC 只信任系统 CA |
| 签名证书 | `CN=x-read, OU=standalone, O=alanmaster, C=CN`（RSA 4096 / SHA384，10 年期） |
| `.so` 页对齐 | `LOAD ... 0x4000`（16 KB，满足 Android 15 要求） |
| 前台服务 | `PlaybackService` 声明 `foregroundServiceType="mediaPlayback"`，`exported=false` |
| 通知渠道 | `xread_playback`（IMPORTANCE_LOW）；播放时 `isForeground=true`，停止后通知与前台状态一并清除 |
| 音频焦点 | 播放/朗读各自申请 `AUDIOFOCUS_GAIN`（`USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`）；用户暂停即释放；被来电或其它播放器抢占时暂停，焦点回来后自动续播 |

## 已验证（真机 Redmi M2012K11AC / Android 13，序列 bbd553fd）

| 项 | 环境 | 结果 |
|---|---|---|
| 冷启动 | 无服务器 | `xread-core: ready transport=jni listener=false` |
| 热搜 | 仅移动数据（Wi-Fi 关闭） | 真实热词渲染 |
| 搜索 | 仅移动数据 | 「十日终焉」返回真实书单 |
| 详情/目录 | 仅移动数据 | 评分 9.9、320 万字、**1496 章** |
| 正文 | 仅移动数据 | 第1章分 9 段渲染，可翻章、调字号、开目录抽屉 |
| 书架 | 仅移动数据 | 加入/移出即时生效，`files/library/shelf.json` 落盘 |
| 下载 | 仅移动数据 | 「前20章」→「完成 · 本地 20 章 20/20」 |
| 榜单 | 仅移动数据 | 男频阅读榜 10+ 卡带封面 |
| 阅读统计 | 读后离开阅读页 | `stats.json`：`{"seconds":37,"words":4702,"chapters":2,"days":{"2026-10-27":37}}` |
| 离线阅读 | Wi-Fi 与移动数据全关 | 已下 20 章正文照常显示 |
| 断点续传 | 旧缓存 396 章 | 工具页列出「396/2003 · paused」，可续传 |
| 备份导出 | 本地 | `x-read-backup.json` 226 KB：2 书目 + 2 进度 + 116 秒统计，无正文 |
| 备份导入 | 本地 | Toast「导入完成」 |
| 旧数据 | 并存安装 | 旧包 `com.alanmaster.xread` 与其数据未被卸载，仅复制 `files/offline/` 到新包 |


## 3.2 新增（均为本机能力，不依赖新接口）

| 功能 | 说明 | 落点 |
|---|---|---|
| 阅读外观 | 米黄/白/浅绿/夜色四种背景、字号 14–30sp、行距 1.2–2.4，持久化到 `settings.json` | `ReaderPrefs`、`ReaderPalette`、`SettingsSheet` |
| 翻页 | 点按左右 30% 区域按屏翻页；音量键翻页（开关可关） | `ReaderScreen`、`ReaderPager`、`MainActivity.dispatchKeyEvent` |
| 书签 / 高亮 | 长按段落选择高亮或加书签；顶栏 🔖 给当前段落加书签；高亮有底色、书签有标记 | `Marks`、`OfflineRepo.marks/addMark/removeMark` |
| 书摘 | 跨书列表、跳回原文、删除、导出 `x-read-marks.md`；随备份一起迁移。章节名存盘与读取都 trim（上游常带尾随空格） | `MarksScreen`、`OfflineRepo.exportMarks` |
| 存储管理 | 每本书占用、离线库合计、清理 `*.tmp` 与 `*.corrupt-*` 残留 | `OfflineRepo.bookSizes/leftovers/cleanLeftovers` |
| 后台朗读 | 系统 TTS 由 `TtsService` 前台托管，锁屏/离开页面继续读；通知栏暂停/停止 | `TtsHolder`、`TtsService` |
| 朗读跟随 | 正在朗读的段落高亮并自动滚入视野 | `ReaderScreen`（`ttsParagraph`） |
| 下载策略 | 仅 Wi-Fi、只下未读、512MB/1GB/2GB 容量上限（超限转「可续传」） | `DownloadPolicy`、`Downloader` |
| 新章提醒 | 每天 20:00 左右用 `AlarmManager` 非精确闹钟检查，有新章才发通知 | `UpdateCheck.kt` |
| 段落切分 | 抽成纯函数 `Paragraphs.split`，与 Python 侧共用 `tests/fixtures/paragraphs.json` 对拍 | `Paragraphs`、`ParagraphsTest`、`test_paragraph_split.py` |
| 书评广场 | 改走 `books/{id}/comments`：offset 分页、内嵌作者回复、`19.5万人点评` 口径；顺带修掉 `books/{id}/reviews` 传 `sort=0` 被上游 400「sort is not smart_hot」导致「最多赞」永远空白的问题 | `Api.bookComments`、`SocialParser.bookComments`、`ReviewScreen` |
| 分类浏览 | `category` 的 194 个分类，点进去用推荐榜（algo 101）× 分类 id 出书单 | `CategoryScreen`、`Api.categories/openCategory` |
| 分享 | 详情页「享」调 `books/{id}/share` 取文案与落地页；书摘「分享」在本地画 1080 宽 PNG 卡片经 `FileProvider` 发出 | `Api.share`、`MarkCard`、`file_paths.xml` |
| 整本导出 | 离线章节按目录顺序拼成 TXT 写到外部目录，只写有正文的章节 | `OfflineRepo.exportBookText`、`ToolsScreen` |
| 分类筛选 | 分类页顶部按名字过滤，194 项不必靠滚动找 | `CategoryScreen` |
| 搜索历史 | 最近 12 条存 `search.json`，同词提位不重复，可一键清空 | `OfflineRepo.searchHistory/pushSearch`、`SearchScreen` |
| 书评折叠 | 超过 6 行折起，按 `hasVisualOverflow` 判断是否给「展开」，不靠字数猜 | `ReviewScreen` |
| 跟随系统 | 阅读背景新增 `auto`：系统深色用夜色，否则米黄 | `ReaderPrefs`、`ReaderPalette` |

### 3.2.1 听书 / 短剧（后续补上的能力）

| 功能 | 说明 | 落点 |
|---|---|---|
| 官方听书榜 | 发现页「官方听书」→ 一页 12 本有声书（上游只给一页，没有「加载更多」）；点开选音色（主播）→ 章节列表 → 后台播放 | `Api.audioFeed/audioTones/audioChapters/audioChapter`、`AudioFeedScreen`、`AudioScreen` |
| 搜索里的听书 | 搜索页加「听书」tab（上游 `tab_type=2`）；上游短剧是 11，原先把短剧错标成 2，已改 | `ContentParser.searchPage`、`SearchScreen` |
| 短剧追剧与收藏 | 播放过的短剧落盘（最多 20 部）、收藏落盘（最多 100 部），都在书架「短剧」页可见，并随备份迁移；三个播放页（剧场、抖音流、滑动流）都能直接收藏 | `OfflineRepo.dramaRecent/dramaFavorites`、`ShelfScreen`、`DramaScreen/DramaDouyinScreen/DramaSwipeScreen` |
| 正文替换 | 「正文替换」里的规则在**阅读页渲染时**套用（不是只在朗读时生效）；先切段再逐段替换，保证段落数不变，段评/书签/进度下标不错位；磁盘上存的仍是上游原文，删掉规则后正文能恢复 | `OfflineRepo.applyRules`、`LibraryViewModel.renderable` |
| 新章通知权限 | Android 13+ 打开「每天检查新章并通知」时先申请通知权限，没授权会明确提示，而不是开关打开却收不到 | `MainActivity.requestNotificationPermissionIfNeeded` |
| 阅读页收书 | 阅读页顶栏「更多」里可直接加入/移出书架，不必退回详情页 | `ReaderScreen.ReaderTopBar`、`MainActivity.onToggleShelf` |
| 弹幕反馈 | 发弹幕成功会提示「弹幕已发送」；失败会撤掉刚插入的那条并说明原因 | `LibraryViewModel.sendDanmaku` |

短剧页原来显示的点赞数（写死的「1.3 万」）已删除：上游没有短剧点赞数（`digg_cnt` 恒为 0，也没有点赞写接口），双击只剩红心动效；同理删掉了点不动的「追剧」加号。短剧弹幕的上游要求番茄登录态，没登录时发不出去，应用会撤掉自己那条并说明原因，而不是假装发出去了。

分类页的榜单只做推荐位筛选（`rank_sub_info_id`），不是严格分类过滤——实测「玄幻」榜里会混进都市高武，
所以页面上写明「来自推荐榜按该分类的推荐位」，不谎称是精确分类列表。

**书评接口的选择**：`books/{id}/reviews` 要求 `sort` 传字符串枚举（`smart_hot` / `time_desc`），
核心层的 body 却把它序列化成 int，实测 `sort=0` 直接 400、`sort=2` 也 400，只有 `sort=1` 能过——
「最多赞」永远拿不到数据。改用 `books/{id}/comments` 后不提 sort，默认即最赞，且自带 `reply_list` 与 `score_cnt`。
`comments/{cid}/replies` 实测上游 400，所以回复只展示首屏内嵌的几条，不摆「展开全部」的假按钮。

AlarmManager 而非 WorkManager：构建环境的 Gradle 缓存中没有 `androidx.work`，加依赖会打断离线构建；非精确重复闹钟不需要 `SCHEDULE_EXACT_ALARM` 权限。

## 3.3 网络可达性（代理与超时）

只发布 `arm64-v8a` 一份原生核心。曾经同时打包 `x86_64`（让模拟器走原生库、不经 houdini 转译），
实测确认那并不能解决问题：即便 `primaryCpuAbi` 已经是 `x86_64`，上游注册域名依然不可达，
多出的 9MB 没有收益，因此回退为单 ABI。

首次启动要走向上游注册设备。注册入口是 `https://log.snssdk.com/service/2/device_register/`，
而很多模拟器/受限网络的 DNS 会把 `*.snssdk.com` 解析到 `198.18.0.0/15` 的 fake-IP；
没有本地代理接管时该地址无人应答，请求会一直挂到超时。实测对照：

| 环境 | 解析结果 | `POST /service/2/device_register/` | 结果 |
|---|---|---|---|
| 真机（有 tun0 代理） | 198.18.2.193 | 200，0.20s | 正常出推荐/榜单/分类/书评 |
| MuMu 模拟器（无代理） | 198.18.2.193 | 超时 | 取不到任何内容 |

因此做了三件事：

| 改动 | 说明 | 落点 |
|---|---|---|
| 注册 12 秒、整体 20 秒 | 旧行为是 60 秒后才失败，首屏干等一分钟没有任何反馈；12 秒是单步上限，20 秒是 `dispatch` 的整体兜底（正常请求实测 < 2 秒） | `device/register.rs`（`REGISTER_TIMEOUT`）、`native-core/src/lib.rs` |
| 系统代理转交 core | 读 `Settings.Global.HTTP_PROXY` 写进 `config.json` 的 `proxy`，core 构建 HTTP 客户端时套用；关掉代理会清掉旧值，避免沿用失效代理 | `NativeCore.applySystemProxy`、`config.rs`、`upstream/http.rs` |
| 失败原因上屏 | core 透出的裸 `timeout` 翻成「网络超时（内容域名在当前网络下不可达）」，并在空态提示配置代理 | `readableFailure`、首页空态 |

日志只在慢请求（>2s）与 5xx、失败时落盘，正常翻页不再写盘；每次请求的完整 URL 降到 trace 级别。

`proxy` 只影响 core 发出的请求，未配置时行为与之前完全一致；配错代理只记一条 warn 并退回直连，
不会让客户端起不来。

**仍未解决的是网络本身**：模拟器没有本地代理时，`*.snssdk.com` 依然不可达，应用只能快速失败并说明原因。
要让这类环境可用，需要给模拟器配可用的 HTTP 代理（应用会读取系统代理），或把模拟器网络改成桥接。

## 3.4 阅读节奏与批量操作（`3.4.0-standalone`）

**阅读目标**：`settings.json` 的 `readingGoal`（`enabled` + 分钟）。连续天数与今日进度全部从
`stats.json` 的 `days` 推导，不另存计数器——改目标后历史按新目标重算，这是有意的：
目标代表「现在想要的强度」，不是当年的历史事实。达标提示只在「本次结算跨过目标线」时发一次，
`todaySeconds` 由 ViewModel 内存缓存维护（跨日归零），避免每次翻页回读 `stats.json`。

**目录缓存**：`OfflineRepo.downloadedChapters(bookId)` 扫 `books/<id>/c_*.json` 并按书缓存，
`putChapter` / `removeChapter` / `removeBook` 后失效。目录行尾 6dp 圆点三态（已缓存实心弱色 /
未缓存空心 / 下载中主色脉冲），长按可「缓存本章 / 缓存后 50 章 / 删除本章缓存」。
`Downloader.startRange(book, from, count)` 与整本下载共用 AIMD 并发、预算检查与断点续传，
只是把待下区间换成章号切片；tasks.json 仍按「下到第几章」记账，续传语义不变。

**自动滚动与常亮**：`ReaderPrefs.autoScroll`（off/慢/常速/快 = 0/14/26/42 px/s）用
`withFrameNanos` 逐帧 `scrollBy`，不足 1px 的余量累计到下一帧——「慢」档每帧才 0.15px，
直接取整会一直是 0。任何触摸、翻页、长按都会置为暂停，右下角出现「自动/继续」胶囊；
滚到章尾停下，等既有的触底续章把下一章接上来。
`ReaderPrefs.keepAwake` 默认开，直接改 `LocalView.keepScreenOn`，离开阅读页还原。

**书架批量**：长按书目先出菜单（「多选」/「设置分组」），多选态顶栏是「已选 N 本 / 全选 / 取消」，
底部动作条是「分组 / 缓存 / 读完 / 移出」。移出书架走整条 `ShelfItem` 备份 + 5 秒 Snackbar 撤销
（`LocalLibrary.restore` 写回分组、入架时间与更新基线），批量缓存用 `cacheQueue` 串行泵，
每本先 `awaitIdle()` 再 `start()`——顺序反了会被「已有任务在跑」直接丢弃。

**首启引导**：`ui/OnboardingScreen.kt` 三步（外观 / 导入备份 / 三条路径），标记写在
`settings.json` 的 `onboarded`。`needsOnboarding()` 还会看本地有没有书架与进度：
老用户升级上来没有标记，但只要有痕迹就不弹，避免挡路。

**触感反馈**：`ui/Feedback.kt` 的 `rememberHaptic()` 收口三档（翻页/勾选=轻，长按菜单=中，
加入书架/下载完成/目标达成=确认），读系统 `HAPTIC_FEEDBACK_ENABLED` 与 TalkBack 触摸探索状态，
任一关闭即静音。

## 3.5 索引与回顾（`3.5.0-standalone`）

这一版不含新内容源，全部是把「读过的」变成「找得回的」。四处落点都不引入新依赖。

### 书摘笔记与标签

- `Mark` 增 `note`（上限 2000 字）与 `tags`（上限 6 个）；`marks.json` 里写作 `n` / `g` 两个可选键，
  **空笔记不写键**——没有批注的旧条目序列化结果与 v3.4 逐字节一致，老备份照常可读。
- `OfflineRepo.setMarkNote()` 按 (段落, 类型) 定位改写，找不到返回 false，界面提示「这条书摘已不在了」。
- 书摘页顶部两行筛选：全部 / 书签 / 高亮 / 有笔记，加一个按书过滤的下拉（只有跨书时出现）。
- 点条目底部弹出编辑面板：原文只读、笔记多行、标签输入 + 常用标签 chips（从已有标签里取前 12 个）。
  **边写边存，300ms 防抖**，不弹「已保存」，不打断输入。
- 导出 Markdown：没有批注的条目仍是 `- 【章节】原文` 一行；有批注的改成引用块
  `> 原文` / `— 【章节】 yyyy-MM-dd` / 空行 / `笔记：…` / `标签：#人物 #伏笔`。多行笔记逐行加 `> `。
- 备份 `version` 升到 3；导入不校验版本，缺 `n` / `g` 的条目按空处理。

### 跨书全文搜索

- `data/FullTextSearch.kt`：只扫**真正落盘**的章（`downloadedChapters ∩ offlineChapters`），
  目录里存在但没下载的章不算——这样结果永远点得开，也不会在搜索时偷偷联网。
- 段落级 `contains(ignoreCase = true)`，中文不切词；命中处前后各取 20 字做摘要。
- 每扫 20 章发一次进度快照（界面显示「已扫描 X / Y 章」），协程取消即停；单次最多 200 条。
- 搜索框 300ms 防抖；空关键词不出结果；**替换规则不参与搜索**（搜的是原文，不是渲染后的文本）。
- 结果按书分组，命中词描底色加粗，点击直达阅读页并定位到那一段（复用书摘的 `pendingJump` 路径）。

### 书籍状态、评分与标签

- `ShelfItem` 增 `status`（want / reading / done，缺省按 reading）、`rating`（0–5，0 = 未评分）、`tags`，
  全部存在本地 `library/shelf.json`，不上传。
- 书架工具栏下方多一行状态 chip（全部 / 想读 / 在读 / 读完），与媒体类型、分组、排序叠加生效。
- 详情页书架按钮下方是「我的标记」：三个状态 chip + 五颗星（再点当前星级清零）+ 标签块（可增改）。
- 状态**不会被进度自动改写**：读者可能停在第 3 章但已弃书，反过来也可能先标读完再回头补看。

### 每日阅读提醒

- `data/ReadingReminder.kt`：开关 + 时间（默认 20:30）+「仅未达标时提醒」（依赖阅读目标）。
- `ReminderScheduler` 用 `setInexactRepeating(RTC_WAKEUP, …, INTERVAL_DAY)`，请求码 `0x7EA4`；
  改时间时先 `cancel` 同一个 `PendingIntent` 再重排，旧闹钟不会残留。
- 通知走独立 channel `xread_reminder` 与 id `0x7EA4`，与新章提醒（`xread_updates` / `0x7EA3`）互不覆盖。
- 文案按进度分三档：没开目标 / 今天还没读 / 还差 N 分钟达标。
- 应用**没有开机广播接收器**，所以闹钟在每次启动时按当前设置重排一次；改时间、开关都立即生效。

## 3.6 随身：自动预取与窗口清理（`3.6.0-standalone`）

这一版只做一件事：把「提前准备」从用户手里拿走。不新增依赖，全部复用 `Downloader`、
章节文件与 `DownloadPolicy`。

### 阅读预取（F16）

- 设置项在「工具 → 下载策略 → 阅读时预取」：关 / 1 / 3 / 5 / 10 章，缺省 **3**。设置里存的是归一化后的档位，
  非法值（旧配置、手改文件）一律落回 3。
- 触发点：`loadChapter` 成功、`appendNextChapter` 成功、`prependPreviousChapter` 成功。三者都走
  `LibraryViewModel.afterChapter(book, index)`，重算「当前章后 N 章里磁盘上没有的章」。
- 实现在独立的 `data/Prefetcher.kt`，**没有并进 `Downloader`**：两者优先级相反，塞进同一个单任务循环，
  要么预取顶掉用户任务，要么用户任务饿死预取。让路方式是**挂起等待**（每 600ms 复查一次
  `downloader.state.running`），用户下载跑完，预取从断点继续。
- 一次只发一个请求，间隔 250ms；遵守 `wifiOnly`（移动网络下直接返回，不提示）；失败静默跳过。
  缺口签名没变就不重启任务，翻一章不会把预取队首重来。

### 缓存窗口与自动清理（F17）

- 保留「当前章前 2 章 ～ 后 N 章」。窗口外的**预取章**在进章 / 续接 / 退章时各扫一次，单次最多删 50 章。
- 预取标记存在每本书一个 `auto.json`（`{"ids": [...]}`），**不写进章节 JSON**：判来源要读整章正文，
  939 章就是几十 MB 解析，而索引文件只有几百字节。手工下载同一章会把它从索引里摘掉。
- `putChapter(..., auto: Boolean = false)` 是唯一入口；`removeChapter` 同时摘索引项，避免清理逻辑
  反复尝试删一个不存在的文件。
- 手动整本 / 范围下载的章不在索引里，**永不自动删**——用户按下下载就是明确意图。

### 读不下去时的出路（F19）

- 阅读页章末错误块：除「点击重试」外多一个「缓存后 5 章」，走 `vm.cacheNext()`——
  `startRange` → `awaitIdle` → 原地 `appendNextChapter()`，不用退目录。
- 目录工具行右侧「补全」：`vm.fillWindow()` 按当前窗口算范围后一次补齐（已缓存的章会被下载器跳过）。

### 下拉刷新的语义（3.6.1）

- **书架**：下拉不再是「重读本地 shelf.json」——那是本机文件，拉十次也是同一个结果。
  现在下拉 = **查新章**：先重读书架条目，再对全部架内书跑一次更新检查，
  结果落地才收起指示器（「已是最新」或「N 本有新章」）。分类/分组过滤不影响检查范围（用 `shelfAll`）。
- **目录**：新增下拉重拉目录。追更的书目录一直变长，读者在目录页拉一下就能看到新章，
  不用退回详情页再进来。刷新后提示「新增 N 章」/「目录已是最新」，缓存圆点同步刷新。

### 下载队列优先（F18）

- `cacheBooks()` 把**当前打开的书**插到队首，其余保持先来先得（不做全量优先级排序，用户看不出谁先谁后）。
- 工具页「下载状态」新增「后面还有 N 本排队」，由 `UiState.cacheQueueSize` 驱动。
- `cancelDownloads()` 清空队列后提示「已清空排队中的 N 本」，同时停掉预取。

### 书架按连载状态自动分组（3.7.0）

- 书架排序菜单里新增「按连载状态」一栏：**连载中 / 已完结**，每行带本数。它排在手工分组之后，
  因为自动分组改不动也删不掉，摆在上面会让人误以为能编辑。
- 分组**不写盘**，按 `Book.creationStatus`（`BookStatus`）现算，见 `data/ShelfGrouping.kt`。
  书一完结，查一次更新分类就自己换过去，用户不用搬书；手工分组仍然是 `shelf.json` 的 `group` 字段，两套并存。
- `creationStatus` 缺失的书算「连载中」——与 `BookStatus.of()` 的口径一致：宁可把完本留在连载组，
  也不要把还在更的书标成完结。0 本的分组不进菜单。
- 内置分组的键是 `@serial` / `@finished`（带 `@` 前缀），不会和用户手输的分组名撞车。
  选中内置分组时排序退回「最近」（`maxOf(lastReadAt, addedAt)` 倒序）——组内再按手工分组名排没有意义。
- `ShelfSort.apply()` 的 `status` 过滤与内置分组叠加：既按连载状态分组，又只看「想读/在读/读完」时顺序是
  先过滤状态、再过滤分组。

## 用 GitHub Actions 打包

本机打 release 包依赖 SSH + Docker（`scripts/build_apk.sh`）；不想开那套环境时，仓库自带的工作流可以直接出包。

- 工作流：`.github/workflows/android.yml`，两个 job 并行。
  - `python-tests`：`pip install -e '.[test]'` + `pytest -q`（173 项，不需要 `.env`）。
  - `android-apk`：JDK 21 → Gradle（`./gradlew`，wrapper 已随仓库提交）→ NDK r27c → Rust `aarch64-linux-android` →
    `scripts/build_native_core.sh` → `:app:testDebugUnitTest` + `assembleRelease` → `scripts/verify_apk.sh` → 上传 artifact。
- 触发：推 `main`、PR 到 `main`、打 `v*` 标签、手动 `workflow_dispatch`。
- Secrets（Settings → Secrets and variables → Actions）：

  | Secret | 是否必需 | 说明 |
  | --- | --- | --- |
  | `XREAD_KEYSTORE_BASE64` | 出 release 包必需 | `base64 -w0 android/signing/release.keystore` 的结果 |
  | `XREAD_KEYSTORE_PASS` | 同上 | keystore 口令 |
  | `XREAD_KEY_ALIAS` | 可选 | 默认 `xread` |
  | `XREAD_KEY_PASS` | 可选 | 默认与 keystore 口令相同 |

- 没有 `XREAD_KEYSTORE_BASE64` 时工作流只出 debug 包：PR 与 fork 也能跑通，且绝不回退 debug 签名冒充 release。
- 打 `v3.7.0` 这类标签时，APK 会附到同名 GitHub Release；其余情况在 Actions 页面的 `x-read-apk` artifact 里取。
- 交付自检与本地发布共用 `scripts/verify_apk.sh`（非 debug 签名、清单不可 debuggable/allowBackup、包内含原生核心与 GPL 文本、`.so` 的 `PT_LOAD` 对齐 ≥ 16 KB），CI 通过就意味着手工流程也会通过。

## 测试

```bash
.venv/bin/python -m pytest -q          # 173 passed
bash scripts/build_apk.sh              # 内含 :app:testDebugUnitTest，产出 release 包
```

Kotlin JVM 单测 **384 项**（`android/app/src/test/java/...`）：
解析（搜索分 tab 去重、长编号、205 章全量、榜单目录嵌套、热词）、本地书架（重开保留、一次性迁移、损坏文件不覆盖）、
统计累计与按日、替换规则顺序、备份往返（含恢复到空仓库、**同一份备份连导两次统计不翻倍**、`days`/`since` 一并恢复）、自适应并发 9 项、
数据安全回归 34 项（损坏文件另存不清空、并发写入不丢键、任务计数往返、章节计数忽略临时文件、旧格式章节被挑出重下、
整本导出按目录顺序、无正文不产空文件、正文重复标题去重、
导入秒数不再除 1000、排序按最近阅读、备份带上书架分组、导入不覆盖本地分组、替换规则缓存与失效、
**书摘章节名写入与读取两侧都 trim**、
**最近一次阅读取 `at` 最新的一条、只带 id 的写入不抹掉已存书目快照、缺章节或缺时间戳的记录不算会话**）、
音频与朗读 8 项（百分比往返、时间格式、utteranceId 解析）、错误分类 7 项（含字符串错误按文案判永久）、
书评 / 分类 / 分享解析 5 项（老接口 offset 分页、内嵌回复与作者标记、脏数据跳过、分类顺序、分享空载荷）、
跟随系统与搜索历史 6 项（auto 主题不被归一化、auto 按系统深浅取色、搜索历史去重限量清空、分类过滤中文子串）、
榜单分类折叠 5 项（收起只留一行且保留当前选中、选中不在行内时顶掉最后一个、展开全铺、分类本来就少时折叠、未知选中回落到普通前 5 个）、
书架自动分组 10 项（未知连载状态归连载、只按上游完本判定、空组不进菜单、菜单顺序、内置组与手工组并存、
分组叠加状态过滤）。

Python 侧 173 项：其中 Android 契约只断言**结构性事实**（核心内置且无监听器、GPL 许可随包、
arm64 + release 签名、原子写内核在位），不再用源码字符串冒充行为测试。

Kotlin 侧同样如此：曾有一批只 `readText()` 扫源码找字符串的「守卫测试」（7 个文件、20 例，
如 `ShelfPullRefreshGuardTest`、`ReaderRetryGuardTest`），它们对任何重构零容忍、却证明不了行为，
已随 3.7.0 一并删除；保留的都是真跑逻辑的用例（纯函数、解析器、落盘往返）。

## 内容页验证（真机，Wi-Fi 关闭、移动数据）

| 页 | 证据 |
|---|---|
| 书评 | 「书评 · 十日终焉（437346 条）」；首条「就你一只猫 ★★★★★ 阅读208小时后点评 👍3368 💬106 2 个月前」 |
| 书评翻页 | 日志 `page items=20 fresh=20 advanced=true total=139`——连续 6 页各 +20、无重复 |
| 作者页 | 「杀虫队队员 · 281 万 粉丝 · 2 部作品」，作品含《十日终焉》《传说管理局》 |
| 目录 | 「目录 · 1496」；「查看全部 1496 章」进入全量列表；输入 `100` 筛出「第100章 韩一墨的担忧」等 |
| 相关推荐 | 「相关推荐 · 2」：齐夏视角重述全文【十日终焉】 |

## 阅读增强验证（真机）

| 项 | 结果 |
|---|---|
| 进度条 / 百分比 | 顶部进入 2%，滚到中部 85% |
| 位置记忆 | 滚到 85% 后返回详情再进：仍 85%（`progress.json` `offset:57`）；连读到第 20 章后强杀重进，书架显示「已读至第 20 章 · 917 章未读」，点「继续」落在第 20 章 |
| 书架直达 | 书架点书名（不是右侧「继续」）直接进阅读页，落在上次读到的第 500 章第 25 段，**不经过详情页**；阅读页返回键回书架。长按书目 →「分组」对话框（新增「查看书籍详情」）可进详情页；发现 / 榜单 / 搜索的书目点击仍先进详情 |
| 冷启动恢复 | 读到「第500章 狭路相逢！」并滚到第 25 段后 `am force-stop`，重新拉起 App **直接落在阅读页同一章**（章名、首段一致，无需点书架）；按返回键回到书架且不会再次自动跳转。进度记录超过一天（`SESSION_RESTORE_TTL_MS`）或在架/离线都查不到这本书时，冷启动停在书架 |
| 换章归零 | 点「下一章」→「第2章 说谎」，进度 3%（新章开头） |
| 双向连续阅读 | 目录跳到第 2 章后向上拉一次：第 2 章页眉仍在屏内（镜头不跳），上方接出第 1 章正文；继续上滑到流顶是「第1章」页眉，再拉一次无反应、无循环（`previousChapterId` 返回 null 即停手）；随后一路下滑依次经过第 2/3/4 章，说明接上一章之后向下追加仍然正常 |
| 热搜词只在聚焦时露出 | 搜索页未点输入框时只有「最近搜索」，没有「大家在搜」；点进输入框才出现 `HOT` 区与 16 个热搜词（龙族 / 十日终焉 / 斩神 / 我不是戏神 …），点「龙族」直接出 20 本结果；失焦后该区消失。发现页不再有「大家在搜」区块，热榜 01 / 新书速递 02 序号顺移 |
| 目录定位 | 读到第 750 章时打开阅读页目录抽屉：首行就是「第750章 四阶上品妖魂到手！」（改前恒从「第1章」开始，因为 `LazyColumn` 没接 `state`，`scrollToItem` 作用在一个没人渲染的 `LazyListState` 上，永远等不到布局）；详情页进全量目录同样定位到「第 750 章」并显示 CURRENT 条 |
| 章号直达 | 939 章的目录里，抽屉输入框输 `800` → 列表首行变「第800章 死灵森海」（不筛列表，相邻章仍在）；点该行直接读第 800 章。清空后回到当前章。全量目录同样：输 `900` → 首行「第900章 又见木雨鸳！」，统计条仍是「共 939 章」不加「匹配」；切到倒序后该章仍在屏内；输越界数字（`90099`）走标题筛选 → 「没有匹配的章节」；带叉一键清空回到当前章 |
| 章内查找 | 打开查找面板可输入；命中段落以强调色显示，`↑/↓` 在命中项间跳转，右侧显示 `n/m` |
| 测试 | `ReaderFindTest` 3 项（命中下标有序、空查询无命中、无匹配为空）、`ResumeParagraphTest` 14 项（同章保留、换章归零、无记录归零、负数钳制、**换章必须立刻落盘、首次进书不被节流、同章等待间隔**）、`PreviousChapterTest` 3 项（优先用章节自带的 previousId、离线章回退目录前一项、第一章上面没有东西）、`SessionRestoreTest` 2 项（一天内恢复、过期/无记录/时钟回拨不恢复） |

## 上游分页的坑（已修）

`books/{id}/reviews` 的 `offset` / `passback` / `page` 三个参数**均无效**，实测都返回同一页；
必须回传 `common_list_info.cursor`（JSON 字符串）才推进。原实现用 offset，配合「去重后为空仍算有更多」
的判定，会永久卡在「加载中…」。现改为 cursor + 三重终止条件：
`page.hasMore && advanced && fresh.isNotEmpty()`。

## 已知限制

- 仅 `arm64-v8a`。x86 模拟器或 32 位机型无法安装此包，需另加 ABI。
- 首次联网需在手机上完成设备身份注册（约数秒），其间热搜可能返回「device request failed」，
  稍后刷新即可；这是上游一次性注册，不影响随后使用。
- 书源固定为番茄一家；榜单分类只取上游提供的「全部」，不伪造男女频专属榜。
- 备份不含正文与下载缓存；迁移到新机需重下。
- 短剧弹幕发不出去：上游要求番茄登录态，而应用不保存任何账号凭据。
  应用会撤掉刚插入的那条弹幕并提示原因；不登录就发不了，只能看别人的弹幕。
- 真人音频支持后台播放与通知栏控制（上一集/播放暂停/下一集/停止，点击回应用）；
  未授予通知权限时后台播放仍可用，只是看不到控制条。
- 系统 TTS 朗读仍只在前台运行，未绑定后台播放。
- 内容核心为 GPL-3.0 衍生作品，随包附 `assets/licenses/LICENSE`、`NOTICE`；
  分发二进制须同时提供对应源码（即 `android/native-core`）。
- 旧版 `com.alanmaster.xread`（需 NAS）仍留在手机上，可自行卸载；其数据已备份于
  `artifacts/pre-standalone/`（该目录被 gitignore，含用户私有数据，勿提交）。
