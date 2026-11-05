# 安卓原生客户端（Kotlin + Compose + MVVM）

App 已改为**纯原生界面**，不再使用 WebView。旧 WebView 壳（Java + `AndroidBridge` + 内置 HTML）
连同 `android/app/src/main/assets/` 一并删除。

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 / UI | Kotlin 1.9.24 + Jetpack Compose（Material 3）+ Compose BOM 2024.06.00 |
| 架构 | MVVM：`LibraryViewModel`（StateFlow 单一 UiState）→ Compose 无状态屏幕 |
| 网络 | `HttpURLConnection` + `org.json`，协程 `Dispatchers.IO`；零第三方 HTTP 库 |
| 图片 | Coil 2.6.0（`AsyncImage`） |
| 离线 | `OfflineRepo`：`files/offline/` 下书目、章节、进度、统计、替换规则、备份 |
| 下载 | `Downloader`：**自适应并发 1–8**（`AdaptiveConcurrency` AIMD：起始 4，连续成功加性增、失败乘性减半并退避；`AdaptiveGate` 按窗口放行，已在跑的请求不打断），`StateFlow<DownloadState>` 回报，可取消；**断点续传**以磁盘章节文件为准 |
| 朗读 | 系统 `TextToSpeech`（中文，前台） |
| 构建 | AGP 8.4.0 / Gradle 8.7 / JDK 21 / minSdk 24 / targetSdk 34 |

## 目录

```
android/app/src/main/java/com/alanmaster/xread/
├─ MainActivity.kt          Compose 入口、OnBackPressedDispatcher、TTS 生命周期
├─ data/
│  ├─ Models.kt             Book / Chapter / ChapterContent / ShelfItem + JSON 扩展
│  ├─ Api.kt                服务端 HTTP：书架、搜索、热搜、榜单、书籍、目录、正文、进度
│  ├─ OfflineRepo.kt        本地仓库：章节、进度、统计、替换规则、备份导入导出
│  ├─ Downloader.kt         批量下载
│  └─ LibraryViewModel.kt   全部状态与动作
└─ ui/
   ├─ Theme.kt              羊皮纸色板（明/暗）
   ├─ Components.kt         BookCard / BookGrid / ChipRow / 空态 / 加载
   ├─ ShelfScreen.kt        书架（热搜词条 + 网格 + 顶栏动作）
   ├─ SearchScreen.kt       搜索
   ├─ RankScreen.kt         榜单（男女频 × 阅读/新书）
   ├─ DetailScreen.kt       详情（封面、简介、目录、相关、加入书架、下载）
   ├─ ReaderScreen.kt       阅读（抽屉目录、字号、上下章）
   ├─ ToolsScreen.kt        离线与工具（下载态、缓存列表、统计、替换、备份、朗读）
   └─ SettingsSheet.kt      服务地址与令牌
```

## 无 WebView 之证

```
$ grep -rn "WebView" android/app/src/main/          # 无输出
$ pytest tests/test_reader_page.py::test_native_app_has_no_webview -q
1 passed
```

（`classes5.dex` 中 `android/webkit/WebView` 字样来自 Compose 内 `MimeTypeMap` 的同包引用，
非本应用调用；自家源码零引用。）

## 下载并发与断点续传

**并发（自适应）**：窗口由 `AdaptiveConcurrency`（AIMD）动态调整——连续满窗成功 +1（上限 8），
失败减半（下限 1），连续失败递增退避（400ms→4s 封顶）。`AdaptiveGate` 据此放行新请求：
窗口收缩只让新请求排队，**不打断在跑的请求**。实测 20 章 14 秒 → 3 秒，整本稳定跑在 8 并发。

**窗口跨进程留存**：收敛值写入 `files/offline/settings.json` 的 `concurrency`，下次启动以此为起点
（仍受 1–8 约束，AIMD 会按当时网络再收敛）。落盘只在值变化时发生，避免每章一次 IO。

**失败须分类**（`ErrorClass`）：只有可重试的失败才收缩窗口。上游缺章
（`Missing initial page JSON`）被服务端包成 `502 + UPSTREAM_INVALID`，故**错误码优先于状态码**：
判为永久性 → 记入 skipped 并跳过，不降并发。用户取消（`CancellationException`）同样不算失败。
早期未分类时，一次缺章即把窗口从 8 拖到 1，为本次修正的核心缺陷。

**续传不记账，以磁盘为准**：每次启动都重新拉目录，再按 `offline.hasChapter(bookId, chapterId)`
过滤出缺失章节。因此进程被杀、断网、手动取消后再次下载，都只补缺的那部分；无需偏移量文件，
也不会因并发中断导致计数错位。
`tasks.json` 只记 `{maxChapters,total,done,status,updated}` 供界面标注「未完成任务（可续传）」，
其 `done` 展示时一律用 `offline.chapterCount()` 复核。

界面入口：工具页「未完成任务（可续传）」区块 →「续传」；`Downloader.resume()` 沿用原
`maxChapters`，目录若变则以新目录为准。

**并发写盘**：`OfflineRepo.putChapter` 标 `@Synchronized`，`write()` 走 `.tmp` + `renameTo`，
避免并行写同一文件或读到半截 JSON。

## 自适应用例（Kotlin JVM 单测 14 项）

```bash
# 构建脚本已串联 :app:testDebugUnitTest
bash scripts/build_apk.sh
```

`AdaptiveConcurrencyTest`（9 项）：起始窗口、上下界钳制、满窗加性增、不减过 min、
失败重置连击、退避递增与封顶、成功清退避。
`ErrorClassTest`（7 项）：网络/限流可重试、`502+UPSTREAM_INVALID` 判永久、内容错误判永久、
未知码回落状态码、错误码优先于状态码。

## 返回键

用 `ComponentActivity.onBackPressedDispatcher` 注册回调（predictive back 兼容）：
详情 → 书架、阅读 → 详情、工具/搜索/榜单 → 书架、书架处两次退出（Toast「再按一次退出 x-read」）。
`AndroidManifest` 置 `android:enableOnBackInvokedCallback="false"`，避开平台与 Compose 双通道竞争。

## 接口映射

| 屏幕 | 端点 |
|---|---|
| 书架 | `GET /api/v1/bookshelf` |
| 热搜 | `GET /api/v1/search/hot` |
| 搜索 | `GET /api/v1/search?q=` |
| 榜单 | `GET /api/v1/rankings?gender=&type=` |
| 详情 | `GET /api/v1/books/{source}/{id}` + `/chapters` + `/related` |
| 阅读 | `GET /api/v1/books/{source}/{id}/chapters/{cid}` |
| 进度 | `PUT /api/v1/progress/{source}/{id}` |
| 书架增删 | `PUT|DELETE /api/v1/bookshelf/{source}/{id}` |

## 实机验证（bbd553fd，Android 13）

```
书架        序列：吃神者 / 十日终焉 / 神通者 / 热搜词条
详情        作者 不要大脑要小脑 · 422 万字 · 共 2003 章 · 简介 · 目录
阅读        第1章 暗世纪元，觉醒吃神者序列，正文分段渲染
翻章        下一章 → 第2章 双喜临门
目录抽屉    共 2003 章可滚
榜单        男频/女频 × 阅读/新书，10 卡带封面
下载        前20章 → 「完成 · 本地 20 章  20/20」（14s → 3s）
自适应升窗    整本下载：并发 4 → 8（连续成功后加性增）
服务停摆降窗  docker stop 服务后：并发 8 → 1（乘性减 + 退避）
服务恢复升窗  重启服务后：并发 1 → 6 → 8
缺章不降窗    40 处 UPSTREAM_INVALID 记为 skipped，并发稳定在 8
取消不降窗    取消后「已取消 · 本地 323 章，可续传（并发 8）」
窗口跨重启    收敛到 8 后 force-stop 重启，续传首帧即「并发 8」而非回落到 4
取消续传    取消后「已取消 · 本地 195 章，可续传」+ 任务卡「195/2003」
重启示续    force-stop 重启后任务卡仍在，点「续传」继续追加
断网读书    195 章在本地，svc wifi disable 后仍可读第1章正文
离线列表    「序列：吃神者 · 20 章 · 不要大脑要小脑」
断网阅读    svc wifi disable 后点「读」仍显示第1章正文，可翻至第2章
返回键      dispatcher: true；详情→书架；书架两次退出
崩溃        FATAL EXCEPTION 计 0
```

## 构建

```bash
bash scripts/build_apk.sh     # 容器内 Gradle 8.7 + JDK 21，产物 artifacts/x-read-standalone.apk（release）
```
