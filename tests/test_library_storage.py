"""Library extension contracts, isolated SQLite databases only."""
from concurrent.futures import ThreadPoolExecutor
import json
import sqlite3

import pytest
from x_read import storage
from x_read.models import Book
from x_read.storage import Store


@pytest.fixture
def store():
    value = Store(":memory:")
    yield value
    value.close()


def book(book_id="1", source="fanqie", title="Old"):
    return Book(source=source, book_id=book_id, title=title)


def bookmark(store, value=1, **kwargs):
    return store.bookmark_put("fanqie", "1", f"{value:032x}", "10", value, **kwargs)


def make_v1(path):
    """Build the original v1 DDL, rather than testing migration against v2 itself."""
    with sqlite3.connect(path) as db:
        db.executescript("""
            CREATE TABLE shelf(source TEXT NOT NULL, book_id TEXT NOT NULL,
                book_json TEXT NOT NULL, added_at TEXT NOT NULL, PRIMARY KEY(source,book_id));
            CREATE TABLE progress(source TEXT NOT NULL, book_id TEXT NOT NULL,
                chapter_id TEXT NOT NULL, offset INTEGER NOT NULL CHECK(offset BETWEEN 0 AND 100000000),
                updated_at TEXT NOT NULL, PRIMARY KEY(source,book_id));
            CREATE TABLE cache(key TEXT PRIMARY KEY, value_json TEXT NOT NULL,
                size_bytes INTEGER NOT NULL CHECK(size_bytes>=0), expires REAL NOT NULL,
                last_used REAL NOT NULL, type TEXT NOT NULL);
            CREATE INDEX cache_expires ON cache(expires);
            CREATE INDEX cache_last_used ON cache(last_used);
            PRAGMA user_version=1;
        """)
        db.execute("INSERT INTO shelf VALUES(?,?,?,?)", ("fanqie", "1", book().model_dump_json(), "old-added"))
        db.execute("INSERT INTO progress VALUES(?,?,?,?,?)", ("fanqie", "1", "10", 123, "old-read"))
        db.execute("INSERT INTO cache VALUES(?,?,?,?,?,?)", ("book:1", '{"old":true}', 12, 10**12, 0, "book"))


def test_v1_migration_preserves_data_and_restart(tmp_path):
    path = tmp_path / "v1.db"
    make_v1(path)
    first = Store(str(path))
    original = first.shelf_get("fanqie", "1")
    assert original["added_at"] == "old-added"
    assert original["progress"]["offset"] == 123
    assert first.cache_get("book:1")["value"] == {"old": True}
    assert first._conn.execute("PRAGMA user_version").fetchone()[0] == 2
    assert first.history_list()["total"] == 0
    assert first.bookmarks_list("fanqie", "1")["total"] == 0
    saved = bookmark(first)
    first.close()
    second = Store(str(path))
    try:
        assert second.shelf_get("fanqie", "1") == original
        assert second.bookmarks_list("fanqie", "1")["items"] == [saved]
    finally:
        second.close()


def test_failed_migration_rolls_back_all_ddl_and_version(tmp_path):
    path = tmp_path / "conflict.db"
    make_v1(path)
    with sqlite3.connect(path) as db:
        db.execute("CREATE TABLE reading_history (sentinel TEXT)")
        db.execute("INSERT INTO reading_history VALUES('untouched')")
    with pytest.raises(sqlite3.OperationalError):
        Store(str(path))
    with sqlite3.connect(path) as db:
        assert db.execute("PRAGMA user_version").fetchone()[0] == 1
        assert db.execute("SELECT name FROM sqlite_master WHERE name='bookmarks'").fetchone() is None
        assert db.execute("SELECT * FROM reading_history").fetchall() == [("untouched",)]
        assert db.execute("SELECT offset FROM progress").fetchone()[0] == 123


def test_future_version_rejected_unchanged(tmp_path):
    path = tmp_path / "future.db"
    with sqlite3.connect(path) as db:
        db.execute("PRAGMA user_version=3")
        db.execute("CREATE TABLE future_data (value TEXT)")
        db.execute("INSERT INTO future_data VALUES('keep')")
    with pytest.raises(RuntimeError, match="schema version"):
        Store(str(path))
    with sqlite3.connect(path) as db:
        assert db.execute("PRAGMA user_version").fetchone()[0] == 3
        assert db.execute("SELECT * FROM future_data").fetchall() == [("keep",)]
        assert db.execute("SELECT COUNT(*) FROM sqlite_master WHERE name='bookmarks'").fetchone()[0] == 0


def test_bookmark_crud_pagination_scope_and_preserved_created(store, monkeypatch):
    stamps = iter(["2026-01-01T00:00:00+00:00", "2026-01-01T00:01:00+00:00", "2026-01-01T00:02:00+00:00"])
    monkeypatch.setattr(storage, "_utc_now", lambda: next(stamps))
    first = bookmark(store, note="note", quote="quote")
    second = bookmark(store, 2)
    updated = store.bookmark_put("fanqie", "1", first["bookmark_id"], "11", 9, note="updated")
    assert updated == {**first, "chapter_id": "11", "offset": 9, "note": "updated", "quote": "", "updated_at": "2026-01-01T00:02:00+00:00"}
    assert store.shelf_list() == []
    page = store.bookmarks_list("fanqie", "1", offset=1, limit=1)
    assert page == {"items": [updated], "total": 2, "offset": 1, "limit": 1}
    assert store.bookmarks_list("fanqie", "1")["items"][0] == second
    assert store.bookmarks_list("other", "1")["total"] == 0
    assert store.bookmarks_list("fanqie", "2")["total"] == 0
    assert store.bookmark_delete("other", "1", first["bookmark_id"]) is False
    assert store.bookmark_delete("fanqie", "1", first["bookmark_id"]) is True
    assert store.bookmark_delete("fanqie", "1", first["bookmark_id"]) is False


@pytest.mark.parametrize("change", [
    {"source": ""}, {"book_id": " "}, {"bookmark_id": "ABCDEF0123456789ABCDEF0123456789"},
    {"bookmark_id": "../bad"}, {"chapter_id": "abc"}, {"chapter_id": "１２"},
    {"offset": -1}, {"offset": True}, {"offset": 100000001}, {"offset": 1.5},
    {"note": 1}, {"note": "x" * 5001}, {"quote": None}, {"quote": "x" * 2001},
])
def test_bookmark_invalid_inputs_leave_no_data(store, change):
    args = dict(source="fanqie", book_id="1", bookmark_id="a" * 32, chapter_id="1", offset=0)
    args.update(change)
    with pytest.raises(ValueError):
        store.bookmark_put(**args)
    assert store.bookmarks_list("fanqie", "1")["total"] == 0


@pytest.mark.parametrize("offset,limit", [(-1, 1), (True, 1), (1.5, 1), ("1", 1), (0, 0), (0, 201), (0, True), (0, 1.5)])
def test_list_validation(store, offset, limit):
    with pytest.raises(ValueError):
        store.bookmarks_list("fanqie", "1", offset, limit)
    with pytest.raises(ValueError):
        store.history_list(offset, limit)


def test_bookmarks_remain_independent_when_shelf_deleted(store):
    store.shelf_put(book())
    value = bookmark(store)
    store.shelf_delete("fanqie", "1")
    assert store.bookmarks_list("fanqie", "1")["items"] == [value]


def test_progress_history_dedup_recent_order_scope_clear(store, monkeypatch):
    stamps = iter(f"2026-01-01T00:0{i}:00+00:00" for i in range(4))
    monkeypatch.setattr(storage, "_utc_now", lambda: next(stamps))
    store.progress_put("fanqie", "1", "10", 1)
    store.progress_put("fanqie", "1", "11", 2)
    newest = store.progress_put("fanqie", "1", "10", 3)
    other = store.progress_put("other", "1", "10", 4)
    assert store.history_list() == {"items": [other, newest, {**newest, "chapter_id": "11", "offset": 2, "updated_at": "2026-01-01T00:01:00+00:00"}], "total": 3, "offset": 0, "limit": 50}
    assert store.history_list(offset=1, limit=1)["items"] == [newest]
    assert store.progress_get("fanqie", "1") == newest
    assert store.history_clear() == 3
    assert store.history_clear() == 0
    assert store.progress_get("fanqie", "1") == newest
    assert store.progress_get("other", "1") == other


def test_history_upsert_failure_rolls_back_progress(store):
    old = store.progress_put("fanqie", "1", "10", 1)
    store._conn.execute("""CREATE TRIGGER fail_history BEFORE INSERT ON reading_history
        BEGIN SELECT RAISE(ABORT, 'history failed'); END""")
    with pytest.raises(sqlite3.IntegrityError, match="history failed"):
        store.progress_put("fanqie", "1", "11", 2)
    assert store.progress_get("fanqie", "1") == old
    assert store.history_list()["items"] == [old]
    store._conn.execute("DROP TRIGGER fail_history")
    assert store.progress_put("fanqie", "1", "11", 2)["offset"] == 2


def test_history_prune_failure_rolls_back_progress_and_new_history(store):
    old = store.progress_put("fanqie", "1", "10", 1)
    store._conn.execute("""CREATE TRIGGER fail_prune BEFORE DELETE ON reading_history
        BEGIN SELECT RAISE(ABORT, 'prune failed'); END""")
    # Temporarily lower the cap to exercise a failing prune without 1000 writes.
    original = storage._HISTORY_LIMIT
    storage._HISTORY_LIMIT = 1
    try:
        with pytest.raises(sqlite3.IntegrityError, match="prune failed"):
            store.progress_put("fanqie", "1", "11", 2)
    finally:
        storage._HISTORY_LIMIT = original
    assert store.progress_get("fanqie", "1") == old
    assert store.history_list()["items"] == [old]


def test_history_global_thousand_limit_and_reread(store, monkeypatch):
    tick = [0]
    def stamp():
        tick[0] += 1
        return f"2026-{tick[0]:06d}"
    monkeypatch.setattr(storage, "_utc_now", stamp)
    for i in range(1005):
        store.progress_put("a" if i % 2 else "b", str(i), str(i), i)
    listing = store.history_list(limit=200)
    assert listing["total"] == 1000 and len(listing["items"]) == 200
    ids = [r[0] for r in store._conn.execute("SELECT book_id FROM reading_history")]
    assert "0" not in ids and "4" not in ids and "5" in ids
    assert store.progress_get("b", "0")["offset"] == 0
    fresh = store.progress_put("b", "0", "0", 42)
    assert store.history_list(limit=1)["items"] == [fresh]
    assert store.history_list()["total"] == 1000


def test_cache_stats_expiry_and_exact_kind_clear_isolation(store, monkeypatch):
    now = [1000.0]
    monkeypatch.setattr(storage.time, "time", lambda: now[0])
    store.cache_put("book:1", {"text": "中文"}, 10)
    store.cache_put("books:2", {}, 20)
    store.cache_put("chapter:3", {}, 30)
    store.shelf_put(book())
    bookmark(store)
    saved = store.progress_put("fanqie", "1", "10", 1)
    expected = len(json.dumps({"text": "中文"}, ensure_ascii=False, separators=(",", ":")).encode()) + 4
    now[0] = 1010
    assert store.cache_stats() == {"entries": 3, "bytes": expected, "budget_bytes": store.cache_budget_bytes, "expired_entries": 1}
    assert store.cache_clear("book") == 1
    assert store.cache_clear("book") == 0
    assert store.cache_clear("book' OR 1=1 --") == 0
    assert store.cache_stats()["entries"] == 2
    assert store.cache_clear() == 2
    assert store.cache_stats()["bytes"] == 0
    assert store.shelf_get("fanqie", "1")["progress"] == saved
    assert store.history_list()["items"] == [saved]
    assert store.bookmarks_list("fanqie", "1")["total"] == 1


def test_cache_management_errors_are_not_swallowed(store):
    store._conn.execute("DROP TABLE cache")
    with pytest.raises(sqlite3.OperationalError):
        store.cache_stats()
    with pytest.raises(sqlite3.OperationalError):
        store.cache_clear()
    assert store.cache_get("x") is None
    store.cache_put("x", {}, 1)
    assert store.shelf_put(book())["book_id"] == "1"


def test_history_clear_failure_is_atomic_and_preserves_progress(store):
    saved = store.progress_put("fanqie", "1", "10", 1)
    store._conn.execute("""CREATE TRIGGER fail_clear BEFORE DELETE ON reading_history
        BEGIN SELECT RAISE(ABORT, 'clear failed'); END""")
    with pytest.raises(sqlite3.IntegrityError):
        store.history_clear()
    assert store.history_list()["items"] == [saved]
    assert store.progress_get("fanqie", "1") == saved


def test_shelf_refresh_only_existing_preserves_added_progress(store):
    assert store.shelf_get("fanqie", "1") is None
    assert store.shelf_refresh(book()) is None
    old = store.shelf_put(book())
    progress = store.progress_put("fanqie", "1", "10", 2)
    value = store.shelf_refresh(book(title="New"))
    assert value == {**old, "title": "New", "progress": progress}
    assert store.shelf_get("fanqie", "1") == value
    assert store.shelf_refresh(book(source="other")) is None
    store.shelf_delete("fanqie", "1")
    assert store.shelf_refresh(book(title="late response")) is None
    assert store.shelf_list() == []


def test_concurrent_library_writes_and_refresh_delete(store):
    def worker(i):
        store.shelf_put(book(str(i)))
        store.bookmark_put("fanqie", str(i), f"{i:032x}", "10", i)
        store.progress_put("fanqie", str(i), "10", i)
        store.shelf_delete("fanqie", str(i))
        assert store.shelf_refresh(book(str(i), title="late")) is None
        assert store.bookmarks_list("fanqie", str(i))["total"] == 1
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(worker, range(50)))
    assert store.shelf_list() == [] and store.history_list()["total"] == 50


def test_cache_clear_trigger_failure_rolls_back_and_keeps_library(store):
    store.cache_put("book:a", {}, 60)
    store.cache_put("book:b", {}, 60)
    store._conn.execute("""CREATE TRIGGER fail_cache_clear BEFORE DELETE ON cache
        WHEN OLD.key='book:b' BEGIN SELECT RAISE(ABORT, 'cache clear failed'); END""")
    with pytest.raises(sqlite3.IntegrityError, match="cache clear failed"):
        store.cache_clear()
    assert store.cache_stats()["entries"] == 2
    assert store.shelf_put(book())["book_id"] == "1"


def test_bookmark_upsert_failure_keeps_existing_row(store):
    original = bookmark(store, note="keep")
    store._conn.execute("""CREATE TRIGGER fail_bookmark BEFORE UPDATE ON bookmarks
        BEGIN SELECT RAISE(ABORT, 'bookmark failed'); END""")
    with pytest.raises(sqlite3.IntegrityError):
        bookmark(store, note="replace")
    assert store.bookmarks_list("fanqie", "1")["items"] == [original]


def test_separate_connection_delete_is_not_reversed_by_refresh(tmp_path):
    path = str(tmp_path / "refresh.db")
    first, second = Store(path), Store(path)
    try:
        first.shelf_put(book())
        assert second.shelf_delete("fanqie", "1")
        assert first.shelf_refresh(book(title="late")) is None
        assert second.shelf_get("fanqie", "1") is None
    finally:
        first.close()
        second.close()


def test_concurrent_v1_open_migrates_once(tmp_path):
    path = tmp_path / "shared.db"
    make_v1(path)
    def worker(_):
        value = Store(str(path))
        try:
            assert value.shelf_get("fanqie", "1")["added_at"] == "old-added"
            assert value._conn.execute("PRAGMA user_version").fetchone()[0] == 2
        finally:
            value.close()
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(worker, range(8)))

def test_concurrent_open_on_fresh_database_survives_locked(tmp_path):
    """多连接同时首开新库：WAL 切换与建表都需要容忍 database is locked。"""
    path = tmp_path / "fresh.db"

    def worker(_):
        value = Store(str(path))
        try:
            value.shelf_get("fanqie", "1")
            assert value._conn.execute("PRAGMA journal_mode").fetchone()[0].lower() == "wal"
            assert value._conn.execute("PRAGMA user_version").fetchone()[0] == 2
        finally:
            value.close()

    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(worker, range(12)))
