# x-read

个人小说阅读程序，已接番茄小说与单页阅读前端。Python 3.11+ / FastAPI / httpx / SQLite。

## 当前能力

- 番茄官方轻量搜索，临时故障时尝试官网搜索；分页游标绑定关键词和实际链路。
- 官网书籍详情、分卷目录、网页可见章节；字体映射解码、HTML 清理、章节归属校验。
- 本地书架与阅读进度；退出重启仍保留，移出书架不删除进度。
- Web 阅读页：书架卡片显示上次阅读时间并可一键续读（按段落下标恢复位置），搜索支持游标翻页，目录只渲染一次并高亮当前章。
- Web 阅读页细节：操作失败用页面内提示而非弹窗，打开书籍有加载遮罩与作品简介，书卡显示简介摘要，标题栏随书籍/章节更新，返回书架保留滚动位置；访问令牌在页面内弹窗填写，安卓离线入口仅在 App 内展示。
- 本地书签、摘录与笔记；章节阅读历史；单书手动更新检查；缓存用量查询与按类型清理。
- 番茄官方小说排行榜（男频/女频、阅读榜/新书榜、多分类及分页），前端支持榜单浏览、翻页与一键阅读/加书架。
- 成功数据缓存、限流、请求合并、有限重试、过期缓存临时降级。
- REST API、OpenAPI、离线测试与显式在线冒烟。

**正文范围：** 官方网页模式返回 `completeness=website_visible`，不将网页可见正文视为 App 全文。锁定章节返回 403，未知字体或残余乱码报错。网页端不代理、不内置任何第三方内容服务；完整正文由 Android 客户端内置的内容核心读取。

安卓端不保存也不导入任何账号凭据；书架与阅读历史只存在本机。暂不含多用户。

## 本机已部署入口

局域网首页：`http://192.168.1.170:18081/`。按当前用户配置启用匿名访问，所有同网可达客户端共享书架与进度。前端静态外壳公开，设置访问令牌后API仍可恢复鉴权。

### 安卓 App 与下载

安卓客户端已改为**单 APK 独立版**：内置 Rust 内容核心（JNI 进程内调用）、不依赖本服务、无需服务器地址或令牌。

- **规格**：`com.alanmaster.xread.standalone`，versionName `3.9.1-standalone`，minSdk 24（Android 7.0）/ targetSdk 34，仅 arm64-v8a；release 包约 10 MB（R8 + 资源压缩后）。
- **不再由本服务分发安装包**：`web/x-read.apk` 已删除（那份是 16.6 MB 的服务端依赖旧版）。需要提供下载时设置 `X_READ_APK_PATH` 指向 `artifacts/x-read-standalone.apk`，否则 `/x-read.apk` 明确返回 404。
- **客户端特性**：原生 Compose（无 WebView）、离线阅读与批量下载（仅 Wi-Fi / 只下未读 / 容量上限）、阅读外观与翻页手势、书签与书摘（可导出 Markdown）、存储管理、替换规则（阅读页渲染时套用）、备份恢复、听书（系统 TTS，后台朗读 + 段落跟随）、官方有声书榜与真人音频播放（进度/倍速/定时）、短剧（刷剧、追剧记录与收藏）、新章提醒、书评广场（评分口径 + 内嵌作者回复）、分类浏览（可按名字筛选）、搜索历史（综合/小说/漫画/听书/短剧）、书籍与书摘卡片分享、整本导出 TXT、段评、作者主页、相关推荐、每日阅读目标与连续打卡、目录章节缓存标记与单章/范围下载、阅读器自动滚动与屏幕常亮、书架多选批量管理（分组/移出可撤销/批量缓存/标记读完）、首次启动引导、统一的触感反馈、书摘笔记与标签、已下载正文的跨书全文搜索、书籍状态/评分/标签、每日阅读提醒、阅读时自动预取后续章节、缓存窗口自动清理、下载队列当前书优先、书架下拉查新章与目录下拉重拉、书架按连载状态自动分组（连载中 / 已完结）。
- **源码与重编**：工程位于 `android/` 目录；运行 `bash scripts/build_apk.sh` 构建 release 包（需要 `SSH_PASS` 与 `XREAD_KEYSTORE_PASS` 环境变量）。
- **交给 GitHub 打包**：`.github/workflows/android.yml` 在 Actions 里直接出包——推 `main` 出 debug 包，打 `v*` 标签出签名 release 包并自动附到 Release，无需本机 SSH/Docker；签名材料放仓库 Secrets，配置步骤见 [安卓独立版说明](docs/ANDROID-STANDALONE.md#用-github-actions-打包)。
- **热更新**：设置 → 03 关于 → 热更新，启动时每天最多检查一次 GitHub Release，发现新版本可下载安装包并直接调起系统安装器；拉包慢可在同一面板切「下载加速」（ghfast.top / gh-proxy.com / ghproxy.net，只代理 APK，令牌不随之外发）；私有仓库需要在「更新源」里填一个只读 GitHub 令牌，细节见 [安卓独立版说明](docs/ANDROID-STANDALONE.md#热更新380)。
- **PWA 支持**：手机端浏览器访问时支持直接「添加到桌面/安装为独立应用」。

本机NAS的桥接端口转发实测超时，实际运行使用Host网络覆盖文件：

```bash
docker compose -f compose.yaml -f compose.host.yaml up -d --build
```

改完代码必须带 `--build` 重新构建：只用 `restart` 会继续跑旧镜像。曾出现运行中的容器
比仓库落后 194 行（缺路径参数二次校验、缺 WAL 初始化重试）。核对方式：

```bash
docker exec x-read-x-read-1 sh -c 'cd /app && find x_read -name "*.py" -o -name "*.html" | xargs md5sum' \
  | while read h f; do [ "$h" = "$(md5sum "$f" | cut -d' ' -f1)" ] || echo "DIFFERS: $f"; done
```


当前配置 `X_READ_BIND_ADDRESS=0.0.0.0`、`X_READ_PORT=18081`、`X_READ_ALLOW_ANONYMOUS=true`、`X_READ_API_TOKEN=`。监听全部IPv4接口以兼容本机EasyTier的`--no-tun`模式，健康检查使用回环地址且不经过代理。EasyTier节点应从同虚拟网的另一台设备访问 `http://10.0.0.5:18081/`；本机没有TUN接口时，自访问虚拟地址不作为连通性验收依据。此模式覆盖所有IPv4接口，保持当前用户要求的免令牌访问。默认启动配置仍保留非回环无令牌保护，只有显式开启匿名模式才放行。不要只用基础Compose重启本机部署，否则会回到桥接模式。

## 启动

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.lock
.venv/bin/pip install --no-deps -e .
.venv/bin/python -m x_read
```

默认 `http://127.0.0.1:8080`。健康检查 `/health`，交互文档 `/docs`，机器可读文档 `/openapi.json`。静态快照见 [OpenAPI](docs/openapi.json)。

`.env.example` 是配置示例，本地 Python **不自动读取 .env**；通过 shell 导出变量，或在进程管理器中配置。Docker Compose 会读取 `.env`。

### 访问令牌

```bash
export X_READ_API_TOKEN="$(openssl rand -hex 32)"
.venv/bin/python -m x_read
```

配置后，除 `/health` 与静态外壳外，API 与文档均要求 `Authorization: Bearer TOKEN`。Swagger 页面可用 curl 获取；交互页面访问本身也要 Header，适合通过能注入鉴权 Header 的本地客户端或反向代理使用。

```bash
curl -H "Authorization: Bearer $X_READ_API_TOKEN" \
  'http://127.0.0.1:8080/api/v1/search?q=%E6%88%91%E4%B8%8D%E6%98%AF%E6%88%8F%E7%A5%9E'
```

非回环监听默认要求令牌，显式 `X_READ_ALLOW_ANONYMOUS=true` 且令牌留空时允许匿名**读**；删除类操作（`DELETE /api/v1/history`、`DELETE /api/v1/cache`）在非回环绑定时必须有令牌，否则返回 403。仅回环绑定（单机自用）保持免令牌。CORS 默认关闭，按需配置明确 origin。启动推荐使用 `python -m x_read`，不要另加多 worker：搜索游标签名、合并队列与上游限流按单进程设计。

## HTTP API

成功格式 `{"data": ..., "meta": {"cached": false, "stale": false, "reason": null}}`。

失败格式 `{"error":{"code":"...","message":"...","request_id":"...","retry_after":null}}`，并使用真实 HTTP 状态。每个响应有 `X-Request-ID`。429 带 `Retry-After`。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/health` | 进程与数据库状态；不访问上游 |
| GET | `/api/v1/sources` | 书源及能力列表 |
| GET | `/api/v1/rankings/catalog?source=fanqie` | 官方榜单男女频分类树及榜单类型 |
| GET | `/api/v1/rankings?gender=male&type=reading&category=1141&page=1` | 排行榜书籍列表及分页 |
| GET | `/api/v1/search/hot` | 热搜推荐词列表；官方书源不支持，返回 501 |
| GET | `/api/v1/search?source=fanqie&q=关键词&cursor=...` | 搜索，q 最多 100 字符 |
| GET | `/api/v1/books/fanqie/{book_id}` | 详情 |
| GET | `/api/v1/books/fanqie/{book_id}/related` | 关联推荐作品；官方书源不支持，返回 501 |
| GET | `/api/v1/books/fanqie/{book_id}/reviews?offset=0&count=20` | 书籍评论；官方书源不支持，返回 501 |
| GET | `/api/v1/books/fanqie/{book_id}/chapters?offset=0&limit=100` | 目录，limit 1–200 |
| GET | `/api/v1/books/fanqie/{book_id}/chapters/{chapter_id}` | 正文、段落、前后章 |
| GET | `/api/v1/books/fanqie/{book_id}/chapters/{chapter_id}/reviews` | 段评数量概览；官方书源不支持，返回 501 |
| POST | `/api/v1/books/fanqie/{book_id}/prefetch` | 批量预取章节入本地缓存（body: `{"chapter_ids":[...]}`） |
| GET | `/api/v1/authors/fanqie/{author_id}` | 作者信息；官方书源不支持，返回 501 |
| GET | `/api/v1/authors/fanqie/{author_id}/books?offset=0&count=20` | 作者作品列表；官方书源不支持，返回 501 |
| GET | `/api/v1/bookshelf` | 本地书架，含阅读进度 |
| PUT | `/api/v1/bookshelf/fanqie/{book_id}` | 幂等加入书架，无 body |
| DELETE | `/api/v1/bookshelf/fanqie/{book_id}` | 移出书架，保留进度 |
| GET | `/api/v1/progress/fanqie/{book_id}` | 进度，未记录时 data=null |
| PUT | `/api/v1/progress/fanqie/{book_id}` | 保存进度 |

保存进度：

```bash
curl -X PUT -H 'Content-Type: application/json' \
  -d '{"chapter_id":"7276663560427471412","offset":120}' \
  http://127.0.0.1:8080/api/v1/progress/fanqie/7276384138653862966
```

`offset` 是**章内段落下标**（从 0 开始，Android 与 Web 都按此读写，恢复时滚动到该段）；允许 0–100000000 的 JSON 整数。服务校验章节在书籍目录中，但不为保存进度额外抓取正文校验长度。回退阅读位置会覆盖旧值，不假定阅读只能前进，也不猜测全书百分比。

上游 ID 全为数字字符串，避免 JS 大整数精度丢失。缺失元数据返回 null。目录顺序以官方分卷及原始数组为准，`order` 从 0 开始。

### 分页与缓存约定

- 搜索使用上游 `has_more`、offset 或 total；未知分页标记返回 null，不按条数猜测。
- 搜索游标签名并绑定关键词、链路，15 分钟有效；**服务重启失效，请重新搜索**。首屏搜索缓存最多 5 分钟，不跨实例复用；后续页每次校验游标。
- 目录从已获取的完整目录切片，返回 `total/offset/limit/has_more/next_offset`。
- 普通元数据默认 10 分钟，章节 7 天；缓存正文和元数据 JSON 负载总预算 256 MiB，LRU 淘汰。预算不含 SQLite 索引、空闲页与 WAL 文件开销，数据库磁盘占用可高于该值。
- 过期缓存仅在临时上游故障时尽力返回，并标记 `stale=true`；写缓存会清理已过期项，因此旧数据不保证可用。搜索不返回过期缓存，避免过期游标。
- 403、锁定、解析错误不会触发其它来源回退；仅临时网络或 502/503/504 故障允许使用过期缓存。搜索只使用官方两条链路。
- 上游串行请求间隔 500ms，最多 32 个不同待处理请求，同 URL/参数合并；排队 30 秒超时。单次网络尝试总时限 15 秒，临时故障最多重试一次；429 立即返回并尊重冷却期。
- 响应解压后上限 8 MiB。正文只返回纯文本，不执行或透传远程脚本。

## 阅读资料与维护接口

所有接口沿用 Bearer 鉴权与统一响应信封。OpenAPI 现声明 HTTPBearer，可供客户端生成器识别。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/books/fanqie/{book_id}/bookmarks?offset=0&limit=50` | 书签分页，包含 note/quote |
| PUT | `/api/v1/books/fanqie/{book_id}/bookmarks/{bookmark_id}` | 幂等保存或修改书签 |
| DELETE | `/api/v1/books/fanqie/{book_id}/bookmarks/{bookmark_id}` | 删除书签 |
| GET | `/api/v1/history?offset=0&limit=50` | 最近阅读章节，按最近时间倒序 |
| DELETE | `/api/v1/history` | 清空历史，不影响当前进度 |
| POST | `/api/v1/bookshelf/fanqie/{book_id}/check-updates` | 绕过详情缓存，手动检查更新并刷新书架元数据 |
| GET | `/api/v1/cache` | 缓存条目、JSON字节、过期条目和预算 |
| DELETE | `/api/v1/cache?kind=chapter` | 按 search/book/directory/chapter 清理；省略 kind 清理全部缓存 |

书签 ID 由客户端生成 UUID 的小写32位 hex，例如 Python `uuid.uuid4().hex`。PUT body：

```json
{"chapter_id":"7276663560427471412","offset":120,"note":"自己的笔记","quote":"摘录文本"}
```

note 上限5000字符，quote上限2000字符；文本仅按数据保存，不作为 HTML。章节须属于指定书籍；偏移语义同阅读进度。书签不依附书架，重复PUT保留created_at，删除书架不连带删除书签。

历史只由成功保存进度产生，普通GET正文不写历史，避免预取误记阅读。同一书籍章节去重更新，保留最近1000项，不记录完整正文。数据库首次从v1迁移到v2时保留原书架、进度、缓存；历史从迁移后开始，不臆造旧记录。

更新检查只操作已在书架中的一本书，不做后台轮询。返回 `updated=true/false/null`、`added_chapters`、新旧末章ID及checked_at；缺少足够字段时updated=null，而非断言未更新。字段变化表示观察到的目录元数据变化，未逐章核实新增正文。上游暂时失败即报错，不用旧缓存假装检查成功，也不会将并发移出书架的书重新加入。

缓存清理不影响书架、进度、书签或历史，不执行VACUUM；磁盘文件不保证立刻缩小。HTTP 404/405 现同样使用统一错误信封。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `X_READ_HOST` | `127.0.0.1` | 监听地址；未设置时回退读取 `X_READ_BIND_ADDRESS`（裸机 `python -m x_read` 与 Compose 行为一致）。非回环地址要求令牌 |
| `X_READ_PORT` | `8080` | 服务端口 |
| `X_READ_DB` | `data/x-read.sqlite3` | SQLite，含书架、进度、缓存 |
| `X_READ_API_TOKEN` | 空 | API Bearer 令牌 |
| `X_READ_METADATA_TTL` | `600` | 元数据缓存秒数，搜索额外上限300秒 |
| `X_READ_CHAPTER_TTL` | `604800` | 章节缓存秒数 |
| `X_READ_CACHE_BUDGET_BYTES` | `268435456` | 缓存 JSON 负载预算 |
| `X_READ_CORS_ORIGINS` | 空 | 逗号分隔的明确 origin，不接受 * |
| `X_READ_ALLOW_ANONYMOUS` | `false` | 非回环绑定且无令牌时是否允许匿名**读**；写/删仍需令牌 |
| `X_READ_APK_PATH` | 空 | 安卓安装包路径；为空时 `/x-read.apk` 返回 404 |

在 Docker 内 `127.0.0.1` 指容器自身。API 不接受任意上游 URL。令牌由部署者配置，不进入状态响应或访问日志。

## Docker

```bash
cp .env.example .env
# 编辑 .env，至少填写随机生成的 X_READ_API_TOKEN
docker compose up -d --build
curl http://127.0.0.1:8080/health
```

默认主机只映射到 127.0.0.1；镜像内监听 0.0.0.0 并以 uid 10001 运行。数据保存在 `reader-data` 命名卷，升级时保留该卷。

备份数据库建议停止服务后复制整个数据卷；或使用 SQLite backup API 创建一致快照，不要在运行时只拷贝主数据库而遗漏 WAL。`docker compose down -v` 会删除数据，勿作为日常停机命令。

当前已通过管理员SSH完成镜像构建和容器运行验收，实际本机使用上文Host网络覆盖；详见 [验证记录](docs/VERIFICATION.md)。

## 测试

```bash
.venv/bin/pip install -e '.[test]'
.venv/bin/python -m pytest -q
# 显式联网，少量样本，临时数据库退出即清理
.venv/bin/python scripts/smoke.py
```

离线测试无需访问番茄；在线冒烟不在默认测试集合中。上游可能改接口、字体或权限，离线测试通过不代表实时可用。验证证据见 [验证记录](docs/VERIFICATION.md)。

安卓侧另有 Kotlin 单测 405 项（`cd android && ./gradlew :app:testDebugUnitTest`）。Python 173 项与 Kotlin 405 项在 `android` 工作流里每次推送都会跑。

## 结构

```text
x_read/
  app.py              REST、鉴权、错误响应与生命周期
  config.py           环境变量校验
  models.py           统一模型
  services.py         缓存与书源编排
  storage.py          SQLite 书架、进度与有界缓存
  sources/            官方网页书源适配器
  data/font_dnmr.json 固定字体映射
reader.html           阅读页模板（服务端渲染后下发）
 tests/               离线测试
 scripts/             smoke.py、原生核心交叉编译、APK 构建与交付自检
 android/             独立版客户端（Kotlin/Compose + native-core Rust）
   app/src/main/      业务代码与资源
   app/src/test/      Kotlin 单测
   native-core/       Rust 内容核心（fqapi_core，GPL-3.0）
 .github/workflows/   android 工作流：测试 + 出包 + Release
 docs/                OpenAPI、验证记录与安卓独立版说明
```

参考来源及固定版本见 [第三方声明](THIRD_PARTY_NOTICES.md)。Python 服务端不含 GPL 内容核心；Android 客户端的 `native-core` 使用 fqapi_core（GPL-3.0），随 APK 分发时遵循其许可。未来增加书源实现 `BookSource` 四个方法，并扩充注册和模型来源即可；本期只注册 fanqie，不引入动态脚本书源执行器。
