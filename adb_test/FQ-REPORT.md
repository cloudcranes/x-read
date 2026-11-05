# fanqie-lite 能力糅合：实施与验证报告

日期：2026-10-27（Asia/Shanghai）
目标：把 `fanqie-lite-api` 的 46 端点 / 57 路径能力并入 x-read（后端透传 + 前端工作台），并在手机实机验证。

## 一、后端：旁路透传层

新增 `x_read/lite_proxy.py`，`app.py` 挂三条路由：

| 路由 | 行为 |
|---|---|
| `GET /api/v1/fq/catalog` | 端点族清单（10 族 / 57 路径）+ 媒体前缀 |
| `GET\|POST /api/v1/fq/{path}` | 转发旁路 `/api/v1/{path}`，query 原样带过，JSON 原样返回（`X-FQ-Path` 标记），仅重写 `/src/` → `/api/v1/fq-media/` |
| `GET\|HEAD /api/v1/fq-media/{file}` | 流式转发 `/src/{file}`，透传 `Range`/`Content-Range`/`ETag`，`Referrer-Policy: no-referrer` |

- 白名单只放行内容族（search/category/books/novels/chapters/videos/series/comments/recommend/manga/audio/authors/rank/items/forum-id）；`admin`、`healthz`、`readyz`、`api/v1/account/*` 一律 404。
- 写路由仅 `comments`/`videos`/`series` 三族，其余 405。
- 旁路令牌只注入独立 `httpx` 客户端，绝不发往 fanqienovel.com；`?token=` 只对媒体路由生效。
- 媒体上限 1 GiB、JSON 16 MiB、请求体 16 KiB；`..`、`//`、反斜杠一律拒绝。

## 二、前端：内容工作台

`reader.html` 增设「内容工作台」：书架顶部筹码行（手机可用）＋左侧栏分组（桌面可用），面板内另有分区条可随时切换八区。

| 分区 | 端点链路 | 桌面 | 手机 |
|---|---|---|---|
| 推荐流 | `recommend/homepage`（tab_type 2/16） | ✅ 24 卡 | ✅ 已触发 `/recommend/homepage` |
| 短剧 | `recommend/channels` → `series/{id}` → `videos/{vid}` → `/src/*.mp4` | ✅ 60 集、`<video>` 可播 | ✅ 已播（`readyState` 级请求可见） |
| 短剧剧评 | `series/{id}/comments` + `comments/{cid}/digg`、`/reply` | ✅ 20 条、点赞回「已赞」 | ✅ 剧评已拉取 |
| 漫画 | `search?tab_type=8` → `books/{id}/directory` → `manga/{item_id}` | ✅ 13 部、130 图 | ✅ 目录→图集→图片流 |
| 听书 | `search?tab_type=2` → `directory` → `audio/books/{id}/chapters/{cid}` | ✅ 20 部、`<audio>` 有源 | ✅ 45 章后取播放地址 |
| 分类 | `category?category_tab=1/2` | ✅ 60 项（上游仅给 id 与封面） | —— |
| 榜单 | 原生 `/api/v1/rankings`（旁路 `rank/{id}` 需 session 握手，留端点台） | ✅ 10 卡带封面 | ✅ 10 卡带封面 |
| 全站搜索 | `search?tab_type=1/3/11/8/2` | ✅ 20 书 + 10 剧 | ✅ 面板就绪 |
| 端点台 | 任意白名单路径直调 + catalog 清单 | ✅ 57 路径 | ✅ 按下即调 |

小说主链路未改：榜单卡片点击仍进原有阅读视图（实测 419 章目录、正文 96 段）。

## 三、测试

```
.venv/bin/python -m pytest -q      # 153 passed（原 142 + 新增 test_fq_proxy.py 11 项）
```

新增 `tests/test_fq_proxy.py`：透传原样性、`/src/` 重写、族白名单、写族限制、媒体流与 `HEAD`、
`?token=` 生效范围、catalog 完整性。桩对象复用 `lite_proxy` 的真实校验，只替换网络层。

## 四、部署与接口实证（生产 18081）

```
health                200  {"status":"ok"}
fq/catalog            200
fq/search/hot         200  161856B
fq/books/{id}/directory 200 659575B
fq/media  Range 0-63  206  64B
fq/comments/c1/digg   200  （写路由）
fq/admin              404  （白名单外）
```

手机侧证据（x-read 容器日志）：`recommend/homepage`、`recommend/channels`、`series/{id}`、
`series/{id}/comments`、`videos/{vid}`、`/src/*.mp4 206`、`books/{id}/directory`、`manga/{item_id}`、
`/src/img_*.jpg`、`search?...tab_type=8`、`search?...tab_type=2`、`audio/books/{id}/chapters/{cid}`。

## 五、改动文件

```
x_read/lite_proxy.py                  新增：白名单透传 + 媒体流 + /src/ 重写
x_read/app.py                          新增：/api/v1/fq/catalog、/fq/{path}(GET,POST)、/fq-media/{file}
x_read/web/reader.html                 +内容工作台（CSS/HTML/JS ~700 行，八区）
reader.html                            同步副本
tests/test_fq_proxy.py                新增 11 项契约测试
docs/FQ-PROXY.md                      新增：旁路路由与前端对接说明
```

## 六、已知边界

1. 短剧/听书封面与播放源为上游 CDN 直链或旁路缓存，首次播放需服务端解密（实测 1 秒至数十秒），面板给「解密中」提示。
2. 分类上游只下发 `id` 与封面，无名称，故分类区只作浏览与跳转。
3. 旁路 `rank/{id}` 需要 `session_id`/`rank_version` 握手，故榜单区走 x-read 原生排行榜；旁路榜单留在端点台。
4. 播放与图集流量经本服务转发，媒体缓存由旁路自管（512 MiB LRU）。

## 七、对照 fqapp（github.com/ch6vip/fqapp@350eb83）补齐

以本家页面与解析逻辑为参照，补三处：

| 项 | fqapp 依据 | 本项目落地 | 验证 |
|---|---|---|---|
| 真榜单 | `lib/models/rank.dart` `fromHomepagePayload`：榜目录藏于首页卡片的 `rank_with_category_data`，`cell_id_str` 为榜 id，`rank_algo_list` 为榜种，`sub_info_list` 为分类 | 榜单区先取首页→找该卡片→得 `rankId=7098235271900037133`，6 榜种（推荐/完本/巅峰/新书/漫剧/短剧）×16 分类，条目走 `rank/{id}?algo_type=&rank_sub_info_id=` | 桌面：推荐榜 30 卡、切新书榜 30 卡；手机：`rank/7098235271900037133?algo_type=101&rank_sub_info_id=0` 与 `=5` 均实证 |
| 书籍详情 | `lib/pages/detail_page.dart` | 新增「书籍详情」面板：封面、作者、评分、字数、分类、简介、「开始阅读／作者主页／书评」，目录 40 章、书评 10 条、相关推荐 | 桌面：神通者 9.4 分·46 万字·目录 60·书评 10；手机：`books/{id}`＋`directory`＋`reviews`＋`related` 四条请求 |
| 作者主页 | `lib/pages/author_page.dart` | 新增「作者主页」面板：头像、粉丝数、作品数、简介、作品集 | 桌面：天蚕土豆 696,839 粉丝·1 部·作品 2 卡 |
| 听书字幕 | `lib/pages/listen_mode_page.dart`、`player_page.dart` 的字幕/文稿 | 章节播放后拉 `chapters/{cid}/timeline`，空则回落 `audio/wkcontent` | 桌面：字幕·文稿 6000 字；手机：`chapters/…/timeline` 已请求 |

未取者：离线缓存与批量下载、正文替换、备份恢复、阅读/播放进度同步、统计页——这些属客户端本地状态，与本次「端点糅合」不同域。

## 八、原生层补记（离线 / 下载 / 统计 / 替换 / 备份 / 朗读）

> **本节已作废（2026-10-27 的当时记录）**：下表列出的 `OfflineStore.java`、`Downloader.java`、
> `AndroidBridge`、`assets/offline.html` 都已随后续的纯原生重写删除，磁盘上不再存在。
> 当前实现见 [ANDROID-NATIVE.md](../docs/ANDROID-NATIVE.md) 与 [ANDROID-OFFLINE.md](../docs/ANDROID-OFFLINE.md)。
> 下文保留原始记录仅为追溯当时的验证过程。

前一版只把能力做在网页层，APK 仍是 WebView 壳，断网即空。本轮补进原生层：

| 文件 | 职责 |
|---|---|
| `android/app/src/main/java/com/alanmaster/xread/OfflineStore.java` | 私有目录 `files/offline/`：书目、章节正文、进度、统计、替换规则、备份 |
| `android/app/src/main/java/com/alanmaster/xread/Downloader.java` | 3 线程批量下载（目录 200 一页 → 逐章正文 → 落盘），可取消 |
| `android/app/src/main/assets/offline.html` | **内置离线阅读页**，断网可开、可读、可删 |
| `MainActivity.java` `AndroidBridge` | 新增 22 个 `@JavascriptInterface` 方法 |
| `res/values/strings.xml` | `offline_read`＝离线阅读；错误页新增该按钮 |

### 实机自检（bbd553fd，`xread-console`）

```
selftest.result {"offlineAvailable":true,"info":{"bytes":213016,"books":1,"downloading":false},
"offlineBooks":1,"firstChapters":20,"downloadRunning":false,
"progress":"{\"chapterId\":\"chap-1\",\"offset\":42,\"at\":1793032286660}",
"stats":{"seconds":60,"words":1500,"chapters":3,"days":{"2026-10-27":60}},
"rules":"[{\"pattern\":\"测试\",\"replacement\":\"替换后\",\"regex\":false,\"enabled\":true}]",
"replaced":"这是替换后文本",
"exportPath":"/storage/emulated/0/Android/data/com.alanmaster.xread/files/x-read-backup.json",
"importResult":"导入完成：书目 1，进度 1","ttsAvailable":true,"speakOk":true,
"localChapter":"book:7665193065501445145"}
```

### 断网实测

`svc wifi disable` → 重开 → 原生错误页「无法连接到 x-read 服务」，按钮变为
`重试连接 [155,1411][397,1543]`、`离线阅读 [397,1411][639,1543]`、`修改服务地址 [639,1411][925,1543]`。
点「离线阅读」：内置页列出「深链下载 7665193065501445145 · 20 章 · 未知作者」；
点「阅读」→「第0章 新书感言」，`1 / 172 · 38 段`，可滚读至「希望这个故事，再陪伴大家三年！」。
网络恢复后「‹ 书架」两次回在线书架。

### 测试

`pytest -q` → **154 passed**（新增 `test_offline_assets_and_bridge_surface`：核内置页存在、
14 个桥方法齐备、`file:///android_asset/offline.html` 在案、仓库与下载器关键方法齐全）。

### 边界

备份不含正文；后台朗读未做（仅前台 TTS）；千章书目下载需数分钟，可取消且已下章节保留。
