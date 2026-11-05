"""下载器契约：并发、续传以磁盘为准、任务状态可跨进程恢复。

行为验证在 Kotlin JVM 单测里（OfflineRepoSafetyTest：并发写入不丢键、
tasks 往返、章节计数忽略临时文件）。这里只保留结构性断言。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "android/app/src/main/java/com/alanmaster/xread"


def read(name: str) -> str:
    return (SRC / name).read_text(encoding="utf-8")


def test_downloader_runs_chapters_concurrently():
    text = read("data/Downloader.kt")
    assert "AdaptiveGate" in text
    assert "Dispatchers.IO" in text
    assert "coroutineScope" in text


def test_resume_is_derived_from_disk_not_from_saved_counter():
    text = read("data/Downloader.kt")
    assert "offline.hasChapter(" in text
    assert "offline.chapterCount(" in text


def test_task_state_persists_for_restart():
    repo = read("data/OfflineRepo.kt")
    for token in ["tasks.json", "fun saveTask", "fun task(", "fun tasks()", "fun deleteTask"]:
        assert token in repo, f"任务持久化缺少：{token}"
    text = read("data/Downloader.kt")
    for status in ["STATUS_RUNNING", "STATUS_PAUSED", "STATUS_FAILED", "STATUS_PARTIAL", "STATUS_DONE"]:
        assert status in text


def test_parallel_chapter_writes_use_a_real_lock():
    """putChapter 必须走原子写内核；锁在 write() 内按文件粒度加。"""
    repo = read("data/OfflineRepo.kt")
    write_idx = repo.index("private fun write(")
    body = repo[write_idx:write_idx + 400]
    assert "synchronized(lockFor(f))" in body, "写盘必须按目标文件加锁"
    assert "createTempFile" in body, "必须用唯一临时文件，固定 .tmp 会互相截断"
    chapter_idx = repo.index("fun putChapter")
    chapter_body = repo[chapter_idx:repo.index("\n    fun ", chapter_idx + 1)]
    assert "write(File(" in chapter_body


def test_tools_screen_offers_resume():
    ui = read("ui/ToolsScreen.kt")
    assert "未完成任务" in ui
    assert "项可续传" in ui
    assert "onResume" in ui
    main = read("MainActivity.kt")
    assert "vm.resumeDownload(" in main


def test_delete_book_also_drops_its_task():
    repo = read("data/OfflineRepo.kt")
    idx = repo.index("fun removeBook")
    assert "deleteTask(" in repo[idx:idx + 200]
