# x-read（拾页）

安卓上的番茄小说阅读器「拾页」：单个 APK，内容核心（Rust）打包在应用内，**不需要服务器、地址或令牌**。翻页、离线缓存、听书、短剧全部在本机完成，阅读数据只存在应用私有目录。

| | |
|---|---|
| 版本 | `3.9.1-standalone`（versionCode 17，应用内名称「拾页」） |
| 包名 | `com.alanmaster.xread.standalone` |
| 适配 | minSdk 24 / targetSdk 34，仅 arm64-v8a |
| 体积 | release 约 10 MB（其中 `.so` 约 8 MB） |
| 界面 | Kotlin + Jetpack Compose，无 WebView |

## 安装

从 [Releases](https://github.com/cloudcranes/x-read/releases) 下载 `x-read-standalone.apk` 直接安装；打 `v*` 标签时由 CI 出正式签名包。

签名更换后 `adb install -r` 会失败（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），此时必须先卸载，**卸载会清掉书架、进度与缓存**。同签名的后续版本直接覆盖安装即可保留数据。

## 功能

**阅读**

- 按段落记忆位置，双向连续阅读（滚到正文两头都会自动接上下章），顶部进度条与百分比。
- 章内查找（命中高亮、上下跳转）、跨书全文搜索、目录章号直达（输 `800` 直接滚到第 800 章）。
- 手感：页边距与字距、屏内亮度（不改变系统亮度）、翻页触感、自动滚动与常亮、跨章章名浮现。
- 离线阅读：批量下载（仅 Wi-Fi / 只下载未读 / 容量上限）、阅读预取、缓存窗口自动清理。

**书架**

- 按连载状态自动分组，显示上次阅读时间并一键续读。
- 多选批量管理：分组、移出（可撤销）、批量缓存、标记读完。
- 下拉查新章、目录下拉重拉。

**书籍与社区**

- 详情页、目录（全量 + 标题筛选 + 章号直达）、相关推荐（按书名去重、排除当前书）、作者页。
- 书评（最多赞 / 最新、五星、点赞与回复数）、段评。
- 书签与书摘笔记（标签、导出 Markdown）、整本导出 TXT、书籍卡片分享。

**发现**

- 搜索：综合 / 小说 / 漫画 / 听书 / 短剧，搜索历史，热度词只在聚焦搜索框时出现。
- 排行榜、分类浏览与筛选、书评广场。
- 短剧：滑动刷剧、追剧记录与收藏。
- 有声书榜单与真人音频播放，支持进度、倍速与定时停止。

**提醒与统计**

- 新章提醒、每日阅读提醒、阅读目标与连续打卡；三者互不覆盖。
- 阅读统计（时长、字数、连续天数）。

**设置**

- 正文替换规则、备份与恢复、存储管理。
- 热更新：每天最多查一次 GitHub Release，下载后直接调起系统安装器；拉包慢可切下载加速（ghfast.top / gh-proxy.com / ghproxy.net，只代理 APK，令牌不外发），私有仓库在「更新源」填只读令牌。

逐版改动见提交记录与 [Releases](https://github.com/cloudcranes/x-read/releases)。

## 构建

需要 JDK 21、Android SDK、NDK r27c 与 Rust `aarch64-linux-android` 目标：

```bash
export RUSTUP_HOME=/path/to/rustup CARGO_HOME=/path/to/cargo
$CARGO_HOME/bin/rustup target add aarch64-linux-android
bash scripts/build_native_core.sh     # 交叉编译 native-core，产出 jniLibs/arm64-v8a/libxread_native.so
```

签名只需一份 release keystore（`android/signing/release.keystore`），没有它签名配置留空、产物是未签名包：

```bash
keytool -genkeypair -keystore android/signing/release.keystore -storetype PKCS12 \
  -alias xread -keyalg RSA -keysize 4096 -validity 10950 \
  -dname 'CN=x-read, OU=standalone, O=alanmaster, C=CN'

SSH_PASS=... XREAD_KEYSTORE_PASS=... bash scripts/build_apk.sh
```

产物 `artifacts/x-read-standalone.apk`。脚本**先自检、后发布**，任一不满足就不留产物：清单里不得有 `debuggable` / `allowBackup`；必须含 `lib/arm64-v8a/libxread_native.so`、`assets/licenses/LICENSE`、`assets/licenses/NOTICE`；`.so` 的 `PT_LOAD p_align >= 0x4000`（Android 15 的 16 KB 页要求）；`apksigner verify` 通过且证书不是 `CN=Android Debug`。口令只经环境变量与 stdin 进容器，不落源码、argv 或 APK。交付自检用 `bash scripts/verify_apk.sh`。

日常跑单测与编译不需要这套环境：

```bash
cd android && ./gradlew :app:testDebugUnitTest    # Kotlin 单测（纯 JVM，离线可跑）
cd android && ./gradlew :app:assembleDebug
```

不想在本地准备 Rust/NDK/Docker，可以直接交给 CI：`.github/workflows/android.yml` 单 job `android-apk`，推 `main` 就跑单测出包并把 APK 留成 artifact（配了签名材料出 release 包，否则出 debug 包）；打 `v*` 标签额外生成 `update.json` 热更新清单，和签名包一起附到 Release。签名材料放 Secrets（`XREAD_KEYSTORE_BASE64`、`XREAD_KEYSTORE_PASS`、`XREAD_KEY_ALIAS`、`XREAD_KEY_PASS`），缺哪个就退化成 debug 包。

## 数据与隐私

不登录、不导入任何账号凭据，没有统计上报，内容核心不开本地端口（`ListenMode::Disabled`）。全部数据在应用私有目录，卸载即清空：

```text
files/library/shelf.json            书架
files/offline/books/<id>/*.json     章节缓存
files/offline/{progress,stats,settings,tasks}.json
files/content-core/                 内容核心：pool.key、config/、device_pool.json、src/、rust.log
```

联网只发生在这几处：内容核心直接连内容提供方、热更新查 GitHub Release、封面与图片加载。正文范围限定在官方网页可见部分，锁定章节返回 403，未知字体或残余乱码直接报错。

## 结构

```text
android/
  app/src/main/java/com/alanmaster/xread/
    MainActivity.kt     导航与页面切换
    data/               内容核心桥、解析、本地库、下载、预取、TTS、提醒、统计、更新
    ui/                 Compose 页面：书架 / 发现 / 阅读 / 目录 / 详情 / 书评 / 短剧 / 设置
  app/src/test/         Kotlin 单测（48 个文件，纯 JVM）
  native-core/          Rust 内容核心：fqapi_core（GPL-3.0，vendored）+ JNI 适配层
scripts/                build_native_core.sh / build_apk.sh / verify_apk.sh
.github/workflows/      android 工作流：出包 + Release
```

## 已舍弃的形态

早期版本还有一套「Python 服务端 + Web 阅读页」的形态，现已不再维护。为免误用，相关代码保留在仓库仅作参考，**不随本次发布迭代**：`x_read/`（服务端）、`scripts/smoke.py`。现在只需要 APK。

## 许可

`native-core` 使用 vendored 的 fqapi_core（GPL-3.0，源自 ch6vip/fqapp），随 APK 分发时遵循其许可，许可原文与声明随包内置于 `assets/licenses/`。参考来源与固定版本见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
