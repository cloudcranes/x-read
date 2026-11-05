"""自适应并发与错误分类：结构性契约。

行为已由 Kotlin JVM 单测覆盖（AdaptiveConcurrencyTest / AdaptiveGateTest /
ErrorClassTest / OfflineRepoSafetyTest 的并发写入用例）。这里只断言那些
「必须成立、又无法在 Python 里运行 Kotlin」的结构性事实，不再用源码字符串
当成行为验证——那种断言写法写坏了也能绿灯。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "android/app/src/main/java/com/alanmaster/xread"


def read(name: str) -> str:
    return (SRC / name).read_text(encoding="utf-8")


def test_aimd_bounds_are_declared_in_one_place():
    text = read("data/AdaptiveConcurrency.kt")
    assert "DEFAULT_MIN = 1" in text and "DEFAULT_MAX = 8" in text
    assert "const val DEFAULT_START" in text


def test_downloader_goes_through_adaptive_gate_not_fixed_semaphore():
    text = read("data/Downloader.kt")
    assert "AdaptiveGate" in text
    assert "kotlinx.coroutines.sync.Semaphore" not in text
    assert "gate.acquire()" in text and "gate.release()" in text


def test_permanent_failures_do_not_shrink_the_window():
    """永久失败（上游不提供）必须走 skipped 分支，不调用 recordFailure。"""
    text = read("data/Downloader.kt")
    assert "Outcome.SKIPPED" in text and "Outcome.FAILED" in text
    skipped_block = text[text.index("Outcome.SKIPPED"):]
    skipped_block = skipped_block[:skipped_block.index("Outcome.FAILED")]
    assert "recordFailure" not in skipped_block, "跳过章节不应压缩并发窗口"


def test_downloader_distinguishes_incomplete_from_done():
    """有章节没拿到时不能标 DONE，否则「可续传」列表会把任务过滤掉。"""
    text = read("data/Downloader.kt")
    assert "STATUS_PARTIAL" in text
    assert "STATUS_DONE" in text


def test_backoff_happens_after_releasing_the_permit():
    text = read("data/Downloader.kt")
    release_at = text.index("gate.release()")
    backoff_at = text.index("AdaptiveGate.backoff")
    assert backoff_at > release_at, "退避必须在归还许可之后，否则降并发形同虚设"


def test_error_code_outranks_http_status():
    text = read("data/ErrorClass.kt")
    assert "PERMANENT" in text and "UPSTREAM_INVALID" in text


def test_jvm_unit_tests_cover_the_pure_modules():
    tests = ROOT / "android/app/src/test/java/com/alanmaster/xread/data"
    names = {p.name for p in tests.glob("*.kt")}
    for required in ("AdaptiveConcurrencyTest.kt", "ErrorClassTest.kt",
                     "OfflineRepoSafetyTest.kt", "AudioPlayerMathTest.kt"):
        assert required in names, f"缺少 JVM 单测：{required}"


def test_ui_shows_live_window_and_resumable_state():
    ui = read("ui/ToolsScreen.kt")
    assert "download.concurrency" in ui
    assert "可续传" in ui


def test_concurrency_window_is_remembered_across_restart():
    repo = read("data/OfflineRepo.kt")
    assert "fun savedConcurrency()" in repo
    assert "fun saveConcurrency(" in repo
    dl = read("data/Downloader.kt")
    assert "offline.savedConcurrency()" in dl
    assert dl.count("persistWindow()") >= 3

def test_reader_renders_paragraph_comment_bubbles():
    """段评分布必须在正文里渲染，否则数据层白拿。"""
    ui = read("ui/ReaderScreen.kt")
    assert "ParagraphBubble" in ui
    assert "bubblesByChapter" in ui
    assert "authorCommented" in ui
    main = read("MainActivity.kt")
    assert "bubblesByChapter = state.bubblesByChapter" in main
