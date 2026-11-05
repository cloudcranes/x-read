"""Offline storage contract tests; never touch a configured application DB."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import json
import sqlite3

import pytest

from x_read.models import Book
from x_read import storage
from x_read.storage import Store


@pytest.fixture
def store():
    instance = Store(":memory:")
    yield instance
    instance.close()


@pytest.fixture
def clock(monkeypatch):
    now = [1_000.0]
    monkeypatch.setattr(storage.time, "time", lambda: now[0])
    return now


def book(book_id="1", source="fanqie", **fields):
    return Book(source=source, book_id=book_id, title=f"Book {book_id}", **fields)


def test_empty_memory_health_close(store):
    assert store.health()
    assert store.shelf_list() == []
    assert store.progress_get("fanqie", "1") is None
    assert store.cache_get("missing") is None
    store.close()
    store.close()
    assert store.health() is False
    assert store.cache_get("missing") is None
    store.cache_put("key", {}, 10)
    with pytest.raises(RuntimeError, match="closed"):
        store.shelf_list()


def test_persistence_restart_and_sqlite_configuration(tmp_path):
    path = str(tmp_path / "nested" / "state.sqlite3")
    first = Store(path)
    original = first.shelf_put(book(author="Author", word_count=123))
    progress = first.progress_put("fanqie", "1", "001", 100_000_000)
    first.cache_put("directory:1", {"chapters": ["你好"]}, 60)
    first.close()
    second = Store(path)
    try:
        assert second.shelf_list() == [{**original, "progress": progress}]
        assert second.progress_get("fanqie", "1") == progress
        assert second.cache_get("directory:1") == {"value": {"chapters": ["你好"]}, "stale": False}
        assert second._conn.execute("PRAGMA journal_mode").fetchone()[0] == "wal"
        assert second._conn.execute("PRAGMA busy_timeout").fetchone()[0] == 5000
        assert second._conn.execute("PRAGMA user_version").fetchone()[0] == 2
    finally:
        second.close()


def test_shelf_idempotent_refresh_preserves_added_and_progress(store):
    original = store.shelf_put(book(author="old"))
    progress = store.progress_put("fanqie", "1", "10", 42)
    updated = store.shelf_put(Book(book_id="1", title="Updated", author="new"))
    assert updated["title"] == "Updated"
    assert updated["author"] == "new"
    assert updated["added_at"] == original["added_at"]
    assert updated["progress"] == progress
    assert len(store.shelf_list()) == 1
    assert datetime.fromisoformat(updated["added_at"]).utcoffset() == timedelta(0)
    assert datetime.fromisoformat(progress["updated_at"]).utcoffset() == timedelta(0)
    assert "percentage" not in progress


def test_delete_preserves_independent_progress(store):
    progress = store.progress_put("fanqie", "1", "2", 0)
    assert store.shelf_list() == []
    assert store.shelf_delete("fanqie", "1") is False
    store.shelf_put(book())
    assert store.shelf_delete("fanqie", "1") is True
    assert store.shelf_delete("fanqie", "1") is False
    assert store.shelf_list() == []
    assert store.progress_get("fanqie", "1") == progress
    assert store.shelf_put(book())["progress"] == progress


def test_source_identity_and_progress_update(store):
    store.shelf_put(book())
    store.shelf_put(book(source="other"))
    store.progress_put("fanqie", "1", "2", 20)
    result = store.progress_put("fanqie", "1", "3", 30)
    assert store.progress_get("fanqie", "1") == result
    assert store.progress_get("other", "1") is None
    assert len(store.shelf_list()) == 2
    assert store.shelf_delete("fanqie", "1")
    assert store.shelf_list()[0]["source"] == "other"


def test_shelf_order_progress_then_added(store, monkeypatch):
    stamps = iter(f"2026-01-01T00:00:0{i}.000000+00:00" for i in range(6))
    monkeypatch.setattr(storage, "_utc_now", lambda: next(stamps))
    for identifier in ("1", "2", "3", "4"):
        store.shelf_put(book(identifier))
    assert [item["book_id"] for item in store.shelf_list()] == ["4", "3", "2", "1"]
    store.progress_put("fanqie", "1", "1", 0)
    store.progress_put("fanqie", "2", "1", 0)
    assert [item["book_id"] for item in store.shelf_list()] == ["2", "1", "4", "3"]


@pytest.mark.parametrize("offset", [-1, 100_000_001, 1.5, "1", True, None])
def test_progress_offset_bounds(store, offset):
    with pytest.raises(ValueError):
        store.progress_put("fanqie", "1", "1", offset)
    assert store.progress_get("fanqie", "1") is None


@pytest.mark.parametrize("chapter", ["", "abc", "1" * 25, "１２", 1, None])
def test_progress_chapter_bounds(store, chapter):
    with pytest.raises(ValueError):
        store.progress_put("fanqie", "1", chapter, 0)


@pytest.mark.parametrize("source,book_id", [("", "1"), ("fanqie", ""), (" ", "1"), (None, "1")])
def test_identity_validation(store, source, book_id):
    with pytest.raises(ValueError):
        store.progress_put(source, book_id, "1", 0)


def test_cache_ttl_stale_boundary_and_expired_cleanup(store, clock):
    store.cache_put("chapter:1", {"text": "正文"}, ttl=10)
    assert store.cache_get("chapter:1") == {"value": {"text": "正文"}, "stale": False}
    clock[0] += 10
    assert store.cache_get("chapter:1") is None
    assert store.cache_get("chapter:1", allow_stale=True) == {"value": {"text": "正文"}, "stale": True}
    store.cache_put("chapter:2", {"text": "new"}, ttl=10)
    assert store.cache_get("chapter:1", allow_stale=True) is None
    assert store._conn.execute("SELECT COUNT(*) FROM cache").fetchone()[0] == 1
    store.cache_put("zero", {}, ttl=0)
    assert store.cache_get("zero") is None
    assert store.cache_get("zero", allow_stale=True)["stale"] is True


def test_cache_lru_and_utf8_budget(clock):
    value = {"text": "中文"}
    size = len(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
    store = Store(":memory:", cache_budget_bytes=2 * size)
    try:
        store.cache_put("chapter:a", value, 60)
        clock[0] += 1
        store.cache_put("chapter:b", value, 60)
        clock[0] += 1
        assert store.cache_get("chapter:a")
        clock[0] += 1
        store.cache_put("chapter:c", value, 60)
        assert store.cache_get("chapter:b") is None
        assert store.cache_get("chapter:a") and store.cache_get("chapter:c")
        rows = store._conn.execute("SELECT size_bytes, type FROM cache").fetchall()
        assert sum(row["size_bytes"] for row in rows) == 2 * size
        assert {row["type"] for row in rows} == {"chapter"}
    finally:
        store.close()


def test_cache_overwrite_size_and_oversized_value(clock):
    store = Store(":memory:", cache_budget_bytes=20)
    try:
        store.cache_put("key", {"v": "a"}, 60)
        store.cache_put("key", {"v": "b"}, 60)
        assert store.cache_get("key")["value"] == {"v": "b"}
        assert store._conn.execute("SELECT COUNT(*) FROM cache").fetchone()[0] == 1
        store.cache_put("too-big", {"v": "x" * 100}, 60)
        assert store.cache_get("too-big") is None
        assert store.cache_get("key")
        store.cache_put("key", {"v": "x" * 100}, 60)
        assert store.cache_get("key") is None
    finally:
        store.close()


def test_cache_zero_budget_and_invalid_data(store):
    zero = Store(":memory:", cache_budget_bytes=0)
    try:
        zero.cache_put("key", {}, 10)
        assert zero.cache_get("key") is None
    finally:
        zero.close()
    cyclic = {}
    cyclic["self"] = cyclic
    for value in ({"set": {1}}, {"nan": float("nan")}, cyclic):
        store.cache_put("bad", value, 10)
    for ttl in (-1, True, "10", 1.5, 10 ** 1000):
        store.cache_put("bad", {}, ttl)
    assert store.cache_get("bad") is None
    assert store.shelf_put(book())["book_id"] == "1"


def test_corrupt_cache_json_and_missing_table_do_not_break_core(store):
    store.cache_put("bad", {}, 60)
    store._conn.execute("UPDATE cache SET value_json='not-json' WHERE key='bad'")
    assert store.cache_get("bad") is None
    store._conn.execute("DROP TABLE cache")
    store.cache_put("key", {}, 60)
    assert store.cache_get("key") is None
    progress = store.progress_put("fanqie", "1", "1", 2)
    assert store.shelf_put(book())["progress"] == progress
    assert store.health()


def test_transaction_failure_rolls_back_and_connection_recovers(store):
    store._conn.execute("""CREATE TRIGGER fail_progress BEFORE INSERT ON progress
        BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
    with pytest.raises(sqlite3.IntegrityError, match="test failure"):
        store.progress_put("fanqie", "1", "1", 0)
    assert store.progress_get("fanqie", "1") is None
    store._conn.execute("DROP TRIGGER fail_progress")
    assert store.progress_put("fanqie", "1", "1", 0)["offset"] == 0


def test_threads_share_store_without_transaction_interleaving(store):
    def worker(index):
        identifier = str(index)
        initial = store.shelf_put(book(identifier))
        progress = store.progress_put("fanqie", identifier, str(index + 1), index)
        store.cache_put(f"chapter:{identifier}", {"id": index}, 60)
        assert store.cache_get(f"chapter:{identifier}")["value"] == {"id": index}
        assert store.shelf_put(book(identifier))["added_at"] == initial["added_at"]
        assert store.progress_get("fanqie", identifier) == progress

    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(worker, range(64)))
    assert len(store.shelf_list()) == 64
    assert all(item["progress"] is not None for item in store.shelf_list())


def test_independent_connections_concurrent_writes(tmp_path):
    path = str(tmp_path / "shared.sqlite3")
    stores = [Store(path), Store(path)]
    try:
        def worker(index):
            instance = stores[index % 2]
            instance.shelf_put(book(str(index)))
            instance.progress_put("fanqie", str(index), "1", index)
        with ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(worker, range(40)))
        assert len(stores[0].shelf_list()) == 40
        assert stores[0].shelf_list() == stores[1].shelf_list()
    finally:
        for instance in stores:
            instance.close()


def test_future_schema_rejected_without_modification(tmp_path):
    path = str(tmp_path / "future.sqlite3")
    with sqlite3.connect(path) as db:
        db.execute("PRAGMA user_version=99")
    with pytest.raises(RuntimeError, match="schema version"):
        Store(path)
    with sqlite3.connect(path) as db:
        assert db.execute("PRAGMA user_version").fetchone()[0] == 99
        assert db.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall() == []


@pytest.mark.parametrize("budget", [-1, True, 1.5, "1"])
def test_invalid_cache_budget(budget):
    with pytest.raises(ValueError):
        Store(":memory:", cache_budget_bytes=budget)
