# x-read 安卓客户端 adb 实机测试与修复报告

> **历史记录（已过期）**：本报告描述的是 WebView 阅读壳时代（包名
> `com.alanmaster.xread`、依赖 NAS 服务端、APK 30 KB）。该形态已被
> **单 APK 独立版**（`com.alanmaster.xread.standalone`，原生 Compose +
> 内置 Rust 内容核心，release 包约 10 MB）取代，详见
> [docs/ANDROID-STANDALONE.md](../docs/ANDROID-STANDALONE.md)。
> 文中「30K」「/x-read.apk 均 200」等数字仅代表当时状态，不再成立。

- 应用：`com.alanmaster.xread`（x-read WebView 阅读壳）
- 设备：小米 M2012K11AC（alioth，Android 13 / SDK 33，1080×2400 @440dpi），序列 `bbd553fd`
- 服务端：NAS `192.168.1.170:18081`（Docker host-network），`/reader.html`、`/health`、`/x-read.apk` 均 200
- 构建产物：`x-read.apk`（30K，含本轮修复）、镜像 `x-read-x-read`（含修复后 `reader.html` 与同一 APK）
- 证据目录：`/vol3/1000/code/x-read/adb_test/`

## 一、问题清单与修复状态

| # | 问题 | 等级 | 修复 | 验证 |
|---|------|------|------|------|
| 1 | 服务地址/令牌无正常入口：`reader.html` 全文无 `AndroidApp`/`openConfig` 引用，配置框只能从错误页进入 | 中 | `reader.html` 书架头部新增「⚙ 设置」，`openAppConfig()` 调 `AndroidApp.openConfig()`；网页端回退为令牌 prompt | `16_fix_launch.png`、`17_fix_settings.png`（正常首屏点设置即出原生对话框）；浏览器内 prompt 取值写入 `localStorage.x_read_token` 成功 |
| 2 | 原生错误页与 Chromium 自带错误页叠加 | 中 | `showErrorScreen()` 先 `stopLoading()` 并 `webView.setVisibility(GONE)`；`loadCurrentUrl()`/`onPageStarted` 恢复 | `18_fix_error.png`、`23_fix_refused.png`：只剩应用自有错误页，无「网页无法打开」 |
| 3 | 离线点「重试连接」无任何反馈 | 低 | 新增 `beginRetrying()`：按钮置「重试中…」并禁用，正文置「正在重试连接，请稍候…」，错误层保持可见；失败回填「原因: net::…」 | `25_fix_retrying_hang.png`：长挂起期间错误层持续显示「正在重试连接，请稍候…」＋「重试中…」；失败后回填原因，`19_fix_retry_offline.png` |
| 4 | 默认地址硬编码在 Java 常量 | 低 | 迁移到 `res/values/strings.xml` 的 `default_server_url`，改发布环境只动资源 | 三处调用点全部改用 `getString(R.string.default_server_url)` |
| 5 | 应用不可调试（`run-as`/`adb root` 均不可用） | 信息 | 发布版设计使然，不修改 | —— |

附带改动：错误页正文追加「原因: <net:: 错误码>」；`onPageFinished` 仅在主框架未失败时收起错误层（`lastMainFrameFailed` 标记），避免 Chromium 错误页触发误收。

## 二、修复前后对照用例

| 用例 | 修复前 | 修复后 | 证据 |
|------|--------|--------|------|
| 首屏渲染 | 通过 | 通过（多出「⚙ 设置」） | `12_clean_launch.png` / `30_fix_final.png` |
| 正常界面改服务地址 | 无入口 | 通过 | `17_fix_settings.png` |
| 断网错误页 | 双层叠加 | 单层 + 原因行 | `09_err_native.png` / `18_fix_error.png` |
| 连接被拒 | —— | 单层 + `ERR_CONNECTION_REFUSED` | `23_fix_refused.png` |
| 离线点重试 | 无反馈 | 「重试中…」+「正在重试连接…」 | `19_fix_retry_offline.png` |
| 慢速/挂起连接重试 | 空白或陈旧画面 | 错误层持续可见直至结果 | `25_fix_retrying_hang.png` |
| 改回正确地址 | 通过 | 通过（错误层自动收起） | `26_fix_recovered.png` |
| Tab 切换 | 通过 | 通过 | `27_fix_tab_rank.png` |
| 搜索 | 通过 | 通过 | `28_fix_search.png`、`29_fix_results.png` |
| 开书→阅读视图（网页端） | 通过 | 通过：第1章 96 段、目录 367 章、`showShelf()` 回退正常 | 浏览器回归（console 0 error / 0 warning） |
| logcat 崩溃 | 无 | 无（`FATAL EXCEPTION` 计数 0） | 各轮 `logcat -d` |

## 三、改动文件

```
x_read/web/reader.html   +⚙ 设置按钮、+openAppConfig()（网页回退 prompt）   （reader.html 根副本同步）
android/app/src/main/res/values/strings.xml          +default_server_url、+retrying、+retrying_hint
android/app/src/main/java/.../MainActivity.java      showErrorScreen/loadCurrentUrl/beginRetrying/onPageStarted/onPageFinished/onBackPressed 分层
```

`onBackPressed` 的 JS 分层判据未改动；`openAppConfig` 追加于 `saveToken` 之后，`node --check` 语法校验通过。

## 四、已知非缺陷噪声

MIUI/Chromium 框架层：`Failed to read DnsConfig`、`Fenced frames cannot be enabled in this configuration.`、`SkFontMgr … fonts_customization.xml could not be opened`、`android_setCpu already initialized`、`base.dm` ziparchive、`PerfDebugMonitorImpl` ENOENT 栈。WebView 内核 155.0.8059.43。

## 五、结论

四项可修问题全部闭环并在实机复验：设置入口可用、错误页不再叠层且带原因码、重试有持续可见的进行中与失败反馈、默认地址已资源化。设备已复原（Wi-Fi 开启、全局代理 `:0`、应用停在正常首屏），镜像与分发的 APK 均与本地构建同源（md5 一致）。
