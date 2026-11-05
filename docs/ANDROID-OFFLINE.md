# 安卓原生能力（离线 / 下载 / 统计 / 替换 / 备份 / 朗读）

这些能力全部落在 APK 原生层（Kotlin），不依赖网页、不依赖 NAS，也不再有 WebView 桥：
旧实现是 `OfflineStore.java` + `AndroidBridge`（`@JavascriptInterface`）+ `assets/offline.html`，
已随原生重写一并删除，见 [ANDROID-NATIVE.md](ANDROID-NATIVE.md) 开头。

## 组成

| 文件 | 职责 |
|---|---|
| `data/OfflineRepo.kt` | 私有目录 `files/offline/`：书目元数据、章节正文、进度、统计、替换规则、书摘、搜索历史、备份导出/导入 |
| `data/Downloader.kt` | 自适应并发（1–8，AIMD 收敛）批量下载：拉目录 → 逐章取正文 → 落盘；可取消、可续传 |
| `data/LocalLibrary.kt` | 书架分组与入架时间（`files/library/shelf.json`） |
| `ui/ToolsScreen.kt` | 「我的 → 离线与工具」页：下载态、缓存列表、存储与残留、统计、替换、备份、朗读 |
| `data/TtsHolder.kt` / `data/TtsService.kt` | 系统 TTS 朗读：进程级 `TextToSpeech` 实例 + `mediaPlayback` 前台服务（锁屏继续读、通知栏控制） |
| `data/AudioPlaybackHolder.kt` / `data/PlaybackService.kt` | 真人音频播放的同等结构 |

## 存储布局（真机实测）

```
files/offline/
├─ books/<bookId>/meta.json              书目 + 全量章节清单（bookId/title/author/coverUrl/updated/chapters[]）
├─ books/<bookId>/c_<chapterId>.json     单章正文（chapterId/title/content/saved；漫画另有 images[]）
├─ progress.json                         每本一个 {chapterId, offset, chapterIndex, at, title, author, coverUrl, source}
├─ stats.json                            {seconds, words, chapters, days{}, since}
├─ settings.json                         {"reader":{…},"concurrency":N,"download":{…},"replaceRules":[…],"newChapterNotify":bool}
├─ marks.json                            "<bookId>:<chapterId>" → {title, items:[{i,t,k,at}]}
├─ tasks.json                            未完成的下载任务（不存在即没有可续传任务）
└─ search.json                           {"items":["赘婿","陆锋",…]}（最多 12 条，去重，最近搜的在最前）

files/library/shelf.json                 书架分组、入架时间、更新基线（与 offline/ 同级，备份一并带上）
```

`settings.json` 里 `reader` 段是阅读外观（`fontSize` / `lineHeight` / `theme` / `tapTurn` / `volumeTurn` /
`turnMode` / `paragraphComments` / `indent` / `paragraphGap` …），`concurrency` 是自适应下载窗口的收敛值。

导出位置：`/storage/emulated/0/Android/data/com.alanmaster.xread.standalone/files/`
（debug 包是 `com.alanmaster.xread.standalone.debug`）。这是应用外部私有目录，
无需任何存储权限，数据线可直接取。备份与书摘分别叫 `x-read-backup.json`、`x-read-marks.md`。

## 能力清单（`OfflineRepo` 公开方法）

| 方法 | 说明 |
|---|---|
| `offlineBooks()` / `sizeBytes()` / `bookSizes()` | 离线书目、离线库总占用、每本占用 |
| `putBook(book, chapters)` / `putChapter(...)` | 写书目（`meta.json`）与单章；落盘走「同目录临时文件 → fsync → rename」原子写，按文件粒度 `synchronized(lockFor(f))` 串行化 |
| `getChapter(bookId, chapterId)` / `hasChapter(...)` / `chapterCount(bookId)` | 读本地章节、判断是否已下、磁盘章节数 |
| `removeBook(bookId)` | 删本地缓存 |
| `saveTask(task)` / `task(bookId)` / `tasks()` / `deleteTask(bookId)` | 未完成任务的记账（展示时以磁盘章节数复核，不以记账为准） |
| `leftovers()` / `cleanLeftovers()` | 扫出并清理 `*.tmp` 与 `*.corrupt-*`（原子写中断与损坏隔离的遗留） |
| `saveProgress(...)` / `progress(bookId)` / `lastReadTimes()` / `lastSession()` | 本地进度（与后端进度并存，离线时以本地为准）。写入时顺带存书目快照（`title`/`author`/`coverUrl`/`source`），`lastSession()` 因此能直接还原出一个 `Book`——从发现或榜单点进去读、没加进书架的书也能在冷启动时恢复 |
| `addStats(seconds, words, chapters)` / `stats()` | 阅读时长与字数的**累加** |
| `restoreStats(backup)` | 统计的**覆盖式恢复**（导入备份专用，见下） |
| `readerPrefs()` / `saveReaderPrefs(...)` | 阅读外观 |
| `downloadPolicy()` / `saveDownloadPolicy(...)` | 仅 Wi-Fi、只下未读、存储上限 |
| `rules()` / `saveRules(list)` / `applyRules(text)` | 正文替换规则（写入即失效编译缓存） |
| `marks(bookId, chapterId)` / `addMark(...)` / `removeMark(...)` / `allMarks()` / `exportMarks(bookTitles)` | 书签与高亮；导出 `x-read-marks.md` |
| `searchHistory()` / `pushSearch(q)` / `clearSearchHistory()` | 搜索历史（去重、限量） |
| `exportBackup()` / `importBackup()` | 备份（书目+进度+统计+规则+书架+书摘，不含正文）；导入是**覆盖式恢复**，统计走 `restoreStats` 而非累加，连导两次结果不变 |

`OfflineRepo` 是进程级单例（`OfflineRepo.of(context)`）；构造器对同模块（含单测）可见，便于用临时目录测。

## 入口

- 「我的」底栏 →「离线与工具」（下载状态、未完成任务、下载策略、本地已下载、存储与残留、阅读统计、
  正文替换、书摘、备份与恢复、账号与同步、朗读）。
- 详情页「更多」→「下载全书」/「下载前 20 章」。
- 断网时读到的是本地章节，回落发生在 ViewModel 层而不是阅读页：
  打开书籍先走网络，失败且 `hasOffline(bookId)` 为真时切 `openOfflineBook()`（目录与正文都取自本地，提示「离线阅读」）；
  翻章时 `loadChapter` 先试网络，失败再 `offline.getChapter`，命中的章节提示「离线章节」。本地命中前**不会**跳过网络。

## 正文替换

工具页「正文替换」每行一条：`原文 => 替换`，正则写作 `/模式/ => 替换`（`regex:true`）。
规则存 `settings.json` 的 `replaceRules`，渲染段落时套用；系统朗读同样读替换后的文本。

## 阅读统计

`addStats` 是**累加**语义：每次 `flushStats` 把本次会话的增量加进 `seconds/words/chapters`，
同时 `days[今天] += seconds`，`since` 只在缺失时写入。因此「读了多久」是跨会话累计值。

## 备份与恢复

- 导出：`exportBackup()` 写 `x-read-backup.json`，含 `app/version/exported/books/progress/stats/replaceRules/shelf/marks`，**不含正文**。
- 导入：`importBackup()` 是「恢复」不是「合并累加」——统计走 `restoreStats` 按备份值整份覆盖（含 `days` 日明细与 `since`），
  书目/进度/规则补回，本地已有分组不被清空。同一份备份重复导入不会把阅读时长翻倍。

## 已验证（真机 Redmi M2012K11AC / Android 13）

```
下载        前 20 章 → 「完成 · 本地 20 章」「20/20 章 · 并发 4/8」「100%」
            全书   → 「完成 · 本地 937 章」「937/937 章 · 并发 7/8」，磁盘 938 个文件（937 章 + meta）
存储上限    点「1GB」→ settings.json download.maxBytes=1073741824，存储条显示「6.1 MB / 1.00 GB」
清理残留    造 junk1.tmp/junk2.tmp → 「残留文件 2 个 · 10 B」→ 点「清理残留」→「无残留文件」
替换规则    输入「测试 => 替换后」保存 → settings.json replaceRules 落盘；导入带规则的备份会整份替换
阅读统计    「27 分钟 / 3.4 万字 / 14 章」，近 7 天单日「2026-11-01 27 分」
导出备份    66063 字节，keys 齐全，stats 与设备一致
导入备份    设备 stats 4962 → 导入（备份内 1654）→ 1654；再导一次仍 1654
朗读        点「朗读本章」→ xread-tts「publish speaking=true msg=朗读中」+ 逐段 onStart/onDone
            点「停止」→「publish speaking=false msg=已停止」
正文分段    重下前 20 章后 `c_7598518941962488857.json` 变 `fmt: 2`、正文 89 个换行
            （改前 0 个，段落边界被折叠成空格），首段正确分成「“跑啊！”\n“老东西…」
```

## 边界

- 朗读是**后台可续**的：`TtsService` 是 `foregroundServiceType="mediaPlayback"` 的前台服务，
  锁屏或离开阅读页后继续读，通知栏可暂停/停止；章与章之间短暂静音有 6 秒缓冲，不误停服务。
  仅 Android 12+ 后台起前台服务受限时会降级成普通通知（不崩溃，也不保证继续读）。
- 工具页的「朗读本章」只读**当前打开的那一章**：冷启动后直接进「我的」时还没有当前章节，
  按钮不会静默失败，而是弹出「先打开一本书再朗读」的提示。要先在阅读器里打开一本书，回到
  工具页再点，才会开始朗读。
- 备份不含正文（控制体积）；整本迁移请重下。
- 备份导入是「恢复」不是「合并累加」：统计（含 `days` 日明细与 `since`）按备份值覆盖，
  书目/进度/规则补回，本地已有分组不被清空。同一份备份重复导入不会把阅读时长翻倍。
- 下载并发 1–8 自适应；长书目（千章）约需数分钟；可随时取消，已下章节保留。
- 批量下载的正文**保留段落分隔**：`chapters/full` 返回的已是带 `\n` 的纯文本，
  只有降级的 `chapters/batch` 才是原始 HTML，因此 `Api.chaptersBatch` 先用
  `ChapterText.looksLikeHtml` 判一次，只对真 HTML 走 `Html.fromHtml` —— 对纯文本
  再跑一遍会把换行当空白折叠，整章粘成一段，离线段落号与在线阅读、段评下标全部错位。
  章节文件带 `"fmt": 2` 标记；旧格式（无 `fmt`、正文被折叠过）的章节在下次下载时由
  `OfflineRepo.chapterNeedsRefresh` 挑出来重下，不需要手动删库。
- 上游章节名常带尾随空格（如 `"第1章 "`），书摘的章节名写入与读取两侧都 trim。
