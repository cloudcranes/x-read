"""独立 APK 契约：无服务地址配置、内置内容核心、本地数据、许可随包。"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "android" / "app"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def test_no_server_url_or_token_configuration_remains():
    sources = list((APP / "src/main/java").rglob("*.kt"))
    joined = "\n".join(read(p) for p in sources)
    for forbidden in ["server_url", "server_token", "192.168.1.170", "/api/v1/bookshelf"]:
        assert forbidden not in joined, f"仍有服务端配置残留：{forbidden}"
    # 内容核心走 JNI 进程内直调，唯一还在发 HTTP 的是热更新（拉 release 清单、下 APK）。
    networked = [p.name for p in sources if "HttpURLConnection" in read(p)]
    assert networked in ([], ["AppUpdater.kt"]), networked
    if networked:
        updater = read(APP / "src/main/java/com/alanmaster/xread/data/AppUpdater.kt")
        # 令牌最多带去 GitHub，别顺着代理地址漏给第三方加速通道。
        assert "isGitHubHost" in updater
    assert "settings.gradle"  # 项目仍为 Gradle 构建
    assert not (APP / "src/main/assets/offline.html").exists()


def test_content_core_is_embedded_and_listenerless():
    # 预编译的 .so 不入库（由 scripts/build_native_core.sh 产出），干净克隆下只校验源码契约。
    lib = APP / "src/main/jniLibs/arm64-v8a/libxread_native.so"
    if lib.is_file():
        assert lib.stat().st_size > 1_000_000
    rust = read(ROOT / "android/native-core/src/lib.rs")
    assert "ListenMode::Disabled" in rust
    assert "init_core" in rust and "runtime::request" in rust
    assert "TcpListener" not in rust and "bind(" not in rust


def test_kotlin_bridge_and_local_storage():
    core = read(APP / "src/main/java/com/alanmaster/xread/data/NativeCore.kt")
    assert 'external fun nativeInit' in core
    assert 'external fun nativeRequest' in core
    assert 'external fun nativeCancel' in core
    assert 'System.loadLibrary("xread_native")' in core
    library = read(APP / "src/main/java/com/alanmaster/xread/data/LocalLibrary.kt")
    assert "shelf.json" in library and "migrateDownloads" in library
    repo = read(APP / "src/main/java/com/alanmaster/xread/data/OfflineRepo.kt")
    for f in ["progress.json", "stats.json", "settings.json", "tasks.json"]:
        assert f in repo


def test_reading_stats_are_actually_recorded():
    vm = read(APP / "src/main/java/com/alanmaster/xread/data/LibraryViewModel.kt")
    # 定义与调用点都要在：载入章节时计数，离开阅读视图时结算。
    assert "private fun countReading(words: Int)" in vm
    assert "fun flushStats(force: Boolean = false)" in vm
    assert vm.count("countReading(c.paragraphs") == 1
    assert "offline.addStats(seconds, pendingWords, pendingChapters)" in vm
    main = read(APP / "src/main/java/com/alanmaster/xread/MainActivity.kt")
    # 返回键、顶栏返回、进工具页三处即时结算
    assert main.count("vm.flushStats") >= 3
    # 进后台/销毁走 pauseReading：既结算又停表，否则后台时间会计入阅读时长
    assert main.count("vm.pauseReading") >= 2
    assert "fun pauseReading()" in vm
    assert "readingSince = 0L" in vm


def test_gpl_license_ships_with_the_apk():
    assets = APP / "src/main/assets/licenses"
    assert (assets / "LICENSE").is_file()
    assert (assets / "NOTICE").is_file()
    text = read(assets / "LICENSE")
    assert "GNU GENERAL PUBLIC LICENSE" in text


def test_standalone_build_is_arm64_and_signed_with_stable_key():
    gradle = read(APP / "build.gradle")
    assert "arm64-v8a" in gradle
    assert "signingConfigs" in gradle
    assert "com.alanmaster.xread.standalone" in gradle
    build = read(ROOT / "scripts/build_apk.sh")
    assert "build_native_core.sh" in build
    assert "SSH_PASS:?" in build
    assert "docker" in build


def test_reviews_and_author_and_toc_screens_exist():
    ui = APP / "src/main/java/com/alanmaster/xread/ui"
    for name in ["ReviewScreen.kt", "AuthorScreen.kt", "TocScreen.kt"]:
        assert (ui / name).is_file(), f"缺少 {name}"
    api = read(APP / "src/main/java/com/alanmaster/xread/data/Api.kt")
    for fn in ["suspend fun bookComments(", "suspend fun paragraphBubbles(", "suspend fun author("]:
        assert fn in api, f"缺少 {fn}"
    # 书评走 books/{id}/comments 的 offset 分页：
    # books/{id}/reviews 要求 sort 传字符串枚举，核心层却序列化成 int，
    # 实测 sort=0 直接 400「sort is not smart_hot」，所以那条路已删。
    assert "count=$count&offset=$offset" in api
    assert "suspend fun reviews(" not in api
    vm = read(APP / "src/main/java/com/alanmaster/xread/data/LibraryViewModel.kt")
    # 偏移未推进即判定到底，避免原地反复取同一页
    assert "page.nextOffset > st.commentsOffset" in vm
    detail = read(ui / "DetailScreen.kt")
    # 相关推荐须在目录之前，目录只预览前 12 章
    assert detail.index("相关推荐") < detail.index("TocHeader(")
    assert re.search(r"chapters\.take\(\d+\)", detail)
