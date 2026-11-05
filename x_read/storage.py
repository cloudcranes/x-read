"""Synchronous SQLite persistence; cache failures never escape into core operations.

A Store owns one connection, serialized with a reentrant lock. File-backed
connections use WAL; SQLite's in-memory journal is retained for ``:memory:``.
Cache byte accounting covers the UTF-8 JSON payload (not SQLite file overhead).
"""
from contextlib import contextmanager
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sqlite3
import threading
import time
from typing import Iterator

from .models import Book


_SCHEMA_VERSION = 2
_MAX_OFFSET = 100_000_000
_HISTORY_LIMIT = 1000


def _location(chapter_id: str, offset: int) -> None:
    if not isinstance(chapter_id, str) or re.fullmatch(r"[0-9]{1,24}", chapter_id) is None:
        raise ValueError("chapter_id must contain 1 to 24 ASCII digits")
    if isinstance(offset, bool) or not isinstance(offset, int) or not 0 <= offset <= _MAX_OFFSET:
        raise ValueError(f"offset must be an integer between 0 and {_MAX_OFFSET}")


def _pagination(offset: int, limit: int) -> None:
    if type(offset) is not int or not 0 <= offset <= 9_223_372_036_854_775_807:
        raise ValueError("offset must be a non-negative SQLite integer")
    if type(limit) is not int or not 1 <= limit <= 200:
        raise ValueError("limit must be an integer between 1 and 200")


def _bookmark_id(value: str) -> None:
    if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{32}", value) is None:
        raise ValueError("bookmark_id must contain 32 lowercase hex characters")


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="microseconds")


def _identifier(value: str, name: str) -> None:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{name} must be a non-empty string")


class Store:
    def __init__(self, path: str, cache_budget_bytes: int = 256 * 1024 * 1024):
        if isinstance(cache_budget_bytes, bool) or not isinstance(cache_budget_bytes, int) or cache_budget_bytes < 0:
            raise ValueError("cache_budget_bytes must be a non-negative integer")
        if not isinstance(path, str) or not path:
            raise ValueError("path must be a non-empty string")
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._closed = False
        self.cache_budget_bytes = cache_budget_bytes
        self._conn = sqlite3.connect(path, timeout=5, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        try:
            self._conn.execute("PRAGMA busy_timeout=5000")
            self._switch_to_wal()
            self._conn.execute("PRAGMA synchronous=NORMAL")
            self._initialize_retrying()
        except Exception:
            self._conn.close()
            self._closed = True
            raise

    def _switch_to_wal(self) -> None:
        """切换 WAL。

        多个连接同时首次打开同一个新库时，`PRAGMA journal_mode=WAL` 需要独占访问，
        而 `busy_timeout` 并不总是覆盖这个协商过程，实测会偶发 `database is locked`。
        这里按退避重试；若最终仍是别的库独占着，读出当前模式即可（WAL 是持久属性，
        只要有一次设成功，后续连接读到的就是 wal）。
        """
        delay = 0.02
        for attempt in range(6):
            try:
                mode = self._conn.execute("PRAGMA journal_mode=WAL").fetchone()[0]
                if str(mode).lower() == "wal":
                    return
            except sqlite3.OperationalError as exc:
                if "locked" not in str(exc).lower() and "busy" not in str(exc).lower():
                    raise
                if attempt == 5:
                    return  # 另一连接已切成 wal，本连接无需再切
                time.sleep(delay)
                delay = min(delay * 2, 0.4)

    def _initialize_retrying(self) -> None:
        """建表/迁移同样要忍受并发首开：别人正在建表时重试即可，成功后版本一致。"""
        delay = 0.02
        for attempt in range(6):
            try:
                self._initialize()
                return
            except sqlite3.OperationalError as exc:
                if "locked" not in str(exc).lower() and "busy" not in str(exc).lower():
                    raise
                if attempt == 5:
                    raise
                time.sleep(delay)
                delay = min(delay * 2, 0.4)

    @contextmanager
    def _transaction(self) -> Iterator[sqlite3.Connection]:
        with self._lock:
            self._ensure_open()
            self._conn.execute("BEGIN IMMEDIATE")
            try:
                yield self._conn
                self._conn.commit()
            except BaseException:
                self._conn.rollback()
                raise

    def _ensure_open(self) -> None:
        if self._closed:
            raise RuntimeError("Store is closed")

    def _initialize(self) -> None:
        with self._transaction() as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version > _SCHEMA_VERSION:
                raise RuntimeError(f"Unsupported storage schema version: {version}")
            if version == 0:
                db.execute("""CREATE TABLE shelf (
                    source TEXT NOT NULL, book_id TEXT NOT NULL,
                    book_json TEXT NOT NULL, added_at TEXT NOT NULL,
                    PRIMARY KEY (source, book_id))""")
                # Intentionally independent of shelf: removal must retain progress.
                db.execute("""CREATE TABLE progress (
                    source TEXT NOT NULL, book_id TEXT NOT NULL,
                    chapter_id TEXT NOT NULL,
                    offset INTEGER NOT NULL CHECK (offset BETWEEN 0 AND 100000000),
                    updated_at TEXT NOT NULL, PRIMARY KEY (source, book_id))""")
                db.execute("""CREATE TABLE cache (
                    key TEXT PRIMARY KEY, value_json TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL CHECK (size_bytes >= 0),
                    expires REAL NOT NULL, last_used REAL NOT NULL,
                    type TEXT NOT NULL)""")
                db.execute("CREATE INDEX cache_expires ON cache(expires)")
                db.execute("CREATE INDEX cache_last_used ON cache(last_used)")
                version = 1
            if version == 1:
                db.execute("""CREATE TABLE bookmarks (
                    source TEXT NOT NULL, book_id TEXT NOT NULL,
                    bookmark_id TEXT NOT NULL, chapter_id TEXT NOT NULL,
                    offset INTEGER NOT NULL CHECK (offset BETWEEN 0 AND 100000000),
                    note TEXT NOT NULL, quote TEXT NOT NULL,
                    created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                    PRIMARY KEY (source, book_id, bookmark_id))""")
                db.execute("CREATE INDEX bookmarks_book ON bookmarks(source, book_id, created_at DESC)")
                db.execute("""CREATE TABLE reading_history (
                    source TEXT NOT NULL, book_id TEXT NOT NULL, chapter_id TEXT NOT NULL,
                    offset INTEGER NOT NULL CHECK (offset BETWEEN 0 AND 100000000),
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (source, book_id, chapter_id))""")
                db.execute("CREATE INDEX history_recent ON reading_history(updated_at DESC)")
                # Existing progress is retained unchanged. History starts at migration:
                # an old progress row does not prove when earlier chapters were read.
                db.execute(f"PRAGMA user_version={_SCHEMA_VERSION}")

    @staticmethod
    def _shelf_item(row: sqlite3.Row) -> dict:
        item = json.loads(row["book_json"])
        item["added_at"] = row["added_at"]
        item["progress"] = None
        if row["updated_at"] is not None:
            item["progress"] = {
                "source": row["source"], "book_id": row["book_id"],
                "chapter_id": row["chapter_id"], "offset": row["offset"],
                "updated_at": row["updated_at"],
            }
        return item

    def shelf_list(self) -> list[dict]:
        with self._lock:
            self._ensure_open()
            rows = self._conn.execute("""SELECT s.*, p.chapter_id, p.offset, p.updated_at
                FROM shelf s LEFT JOIN progress p USING (source, book_id)
                ORDER BY p.updated_at DESC, s.added_at DESC, s.source, s.book_id""").fetchall()
            return [self._shelf_item(row) for row in rows]

    def shelf_put(self, book: Book) -> dict:
        _identifier(book.source, "source")
        _identifier(book.book_id, "book_id")
        payload = book.model_dump_json()
        with self._transaction() as db:
            db.execute("""INSERT INTO shelf(source, book_id, book_json, added_at)
                VALUES (?, ?, ?, ?) ON CONFLICT(source, book_id)
                DO UPDATE SET book_json=excluded.book_json""",
                (book.source, book.book_id, payload, _utc_now()))
            row = db.execute("""SELECT s.*, p.chapter_id, p.offset, p.updated_at
                FROM shelf s LEFT JOIN progress p USING (source, book_id)
                WHERE s.source=? AND s.book_id=?""", (book.source, book.book_id)).fetchone()
            return self._shelf_item(row)

    def shelf_get(self, source: str, book_id: str) -> dict | None:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        with self._lock:
            self._ensure_open()
            row = self._conn.execute("""SELECT s.*, p.chapter_id, p.offset, p.updated_at
                FROM shelf s LEFT JOIN progress p USING (source, book_id)
                WHERE s.source=? AND s.book_id=?""", (source, book_id)).fetchone()
            return self._shelf_item(row) if row else None

    def shelf_refresh(self, book: Book) -> dict | None:
        _identifier(book.source, "source")
        _identifier(book.book_id, "book_id")
        payload = book.model_dump_json()
        with self._transaction() as db:
            changed = db.execute("UPDATE shelf SET book_json=? WHERE source=? AND book_id=?",
                                 (payload, book.source, book.book_id)).rowcount
            if not changed:
                return None
            row = db.execute("""SELECT s.*, p.chapter_id, p.offset, p.updated_at
                FROM shelf s LEFT JOIN progress p USING (source, book_id)
                WHERE s.source=? AND s.book_id=?""", (book.source, book.book_id)).fetchone()
            return self._shelf_item(row)

    def shelf_delete(self, source: str, book_id: str) -> bool:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        with self._transaction() as db:
            return db.execute("DELETE FROM shelf WHERE source=? AND book_id=?", (source, book_id)).rowcount > 0

    def progress_get(self, source: str, book_id: str) -> dict | None:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        with self._lock:
            self._ensure_open()
            row = self._conn.execute("SELECT * FROM progress WHERE source=? AND book_id=?", (source, book_id)).fetchone()
            return dict(row) if row else None

    def progress_put(self, source: str, book_id: str, chapter_id: str, offset: int) -> dict:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        _location(chapter_id, offset)
        with self._transaction() as db:
            result = {"source": source, "book_id": book_id, "chapter_id": chapter_id,
                      "offset": offset, "updated_at": _utc_now()}
            db.execute("""INSERT INTO progress(source, book_id, chapter_id, offset, updated_at)
                VALUES (:source, :book_id, :chapter_id, :offset, :updated_at)
                ON CONFLICT(source, book_id) DO UPDATE SET chapter_id=excluded.chapter_id,
                offset=excluded.offset, updated_at=excluded.updated_at""", result)
            db.execute("""INSERT INTO reading_history(source, book_id, chapter_id, offset, updated_at)
                VALUES (:source, :book_id, :chapter_id, :offset, :updated_at)
                ON CONFLICT(source, book_id, chapter_id) DO UPDATE SET
                offset=excluded.offset, updated_at=excluded.updated_at""", result)
            db.execute("""DELETE FROM reading_history WHERE rowid IN (
                SELECT rowid FROM reading_history ORDER BY updated_at DESC, rowid DESC
                LIMIT -1 OFFSET ?)""", (_HISTORY_LIMIT,))
        return result

    def bookmark_put(self, source: str, book_id: str, bookmark_id: str,
                     chapter_id: str, offset: int, note: str = "", quote: str = "") -> dict:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        _bookmark_id(bookmark_id)
        _location(chapter_id, offset)
        if not isinstance(note, str) or len(note) > 5000:
            raise ValueError("note must be a string of at most 5000 characters")
        if not isinstance(quote, str) or len(quote) > 2000:
            raise ValueError("quote must be a string of at most 2000 characters")
        now = _utc_now()
        result = dict(source=source, book_id=book_id, bookmark_id=bookmark_id,
                      chapter_id=chapter_id, offset=offset, note=note, quote=quote,
                      created_at=now, updated_at=now)
        with self._transaction() as db:
            db.execute("""INSERT INTO bookmarks(source, book_id, bookmark_id, chapter_id,
                offset, note, quote, created_at, updated_at)
                VALUES (:source, :book_id, :bookmark_id, :chapter_id, :offset, :note,
                        :quote, :created_at, :updated_at)
                ON CONFLICT(source, book_id, bookmark_id) DO UPDATE SET
                chapter_id=excluded.chapter_id, offset=excluded.offset, note=excluded.note,
                quote=excluded.quote, updated_at=excluded.updated_at""", result)
            row = db.execute("""SELECT * FROM bookmarks
                WHERE source=? AND book_id=? AND bookmark_id=?""",
                (source, book_id, bookmark_id)).fetchone()
            return dict(row)

    def bookmarks_list(self, source: str, book_id: str, offset: int = 0, limit: int = 50) -> dict:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        _pagination(offset, limit)
        # One transaction yields a consistent count/page across other connections.
        with self._transaction() as db:
            total = db.execute("SELECT COUNT(*) FROM bookmarks WHERE source=? AND book_id=?",
                               (source, book_id)).fetchone()[0]
            rows = db.execute("""SELECT * FROM bookmarks WHERE source=? AND book_id=?
                ORDER BY created_at DESC, bookmark_id DESC LIMIT ? OFFSET ?""",
                (source, book_id, limit, offset)).fetchall()
            return dict(items=[dict(row) for row in rows], total=total, offset=offset, limit=limit)

    def bookmark_delete(self, source: str, book_id: str, bookmark_id: str) -> bool:
        _identifier(source, "source")
        _identifier(book_id, "book_id")
        _bookmark_id(bookmark_id)
        with self._transaction() as db:
            return db.execute("DELETE FROM bookmarks WHERE source=? AND book_id=? AND bookmark_id=?",
                              (source, book_id, bookmark_id)).rowcount > 0

    def history_list(self, offset: int = 0, limit: int = 50) -> dict:
        _pagination(offset, limit)
        with self._transaction() as db:
            total = db.execute("SELECT COUNT(*) FROM reading_history").fetchone()[0]
            rows = db.execute("""SELECT * FROM reading_history
                ORDER BY updated_at DESC, rowid DESC LIMIT ? OFFSET ?""", (limit, offset)).fetchall()
            return dict(items=[dict(row) for row in rows], total=total, offset=offset, limit=limit)

    def history_clear(self) -> int:
        with self._transaction() as db:
            return db.execute("DELETE FROM reading_history").rowcount

    def cache_stats(self) -> dict:
        # Unlike best-effort cache reads, management failures must reach the caller.
        with self._lock:
            self._ensure_open()
            row = self._conn.execute("""SELECT COUNT(*) AS entries,
                COALESCE(SUM(size_bytes), 0) AS bytes,
                COALESCE(SUM(CASE WHEN expires<=? THEN 1 ELSE 0 END), 0) AS expired_entries
                FROM cache""", (time.time(),)).fetchone()
            return {**dict(row), "budget_bytes": self.cache_budget_bytes}

    def cache_clear(self, kind: str | None = None) -> int:
        if kind is not None:
            _identifier(kind, "kind")
        with self._transaction() as db:
            if kind is None:
                return db.execute("DELETE FROM cache").rowcount
            return db.execute("DELETE FROM cache WHERE type=?", (kind,)).rowcount

    def cache_get(self, key: str, allow_stale: bool = False) -> dict | None:
        """Return a miss on cache errors; expired entries remain available as stale.

        Expired entries are reclaimed on writes, so stale fallback is best effort.
        """
        try:
            with self._transaction() as db:
                row = db.execute("SELECT value_json, expires FROM cache WHERE key=?", (key,)).fetchone()
                if row is None:
                    return None
                stale = row["expires"] <= time.time()
                if stale and not allow_stale:
                    return None
                value = json.loads(row["value_json"])
                # 列表型缓存（热搜、相关推荐）同样是合法载荷：
                # 旧实现只认 dict，会把列表条目当场删掉，导致这两类缓存永远不命中。
                if not isinstance(value, (dict, list)):
                    db.execute("DELETE FROM cache WHERE key=?", (key,))
                    return None
                db.execute("UPDATE cache SET last_used=? WHERE key=?", (time.time(), key))
                return {"value": value, "stale": stale}
        except (sqlite3.Error, ValueError, TypeError, OverflowError, RuntimeError, RecursionError):
            return None

    def cache_put(self, key: str, value: dict | list, ttl: int) -> None:
        """Best-effort bounded cache; invalid/unserializable/oversized values skip.

        value 支持 dict 与 list：热搜/相关推荐的载荷是列表，只接受 dict 会让
        这两类缓存写入被静默丢弃。
        """
        try:
            if not isinstance(key, str) or not key or not isinstance(value, (dict, list)):
                return
            if isinstance(ttl, bool) or not isinstance(ttl, int) or ttl < 0:
                return
            payload = json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
            size = len(payload.encode("utf-8"))
            now = time.time()
            with self._transaction() as db:
                db.execute("DELETE FROM cache WHERE expires<=?", (now,))
                if size > self.cache_budget_bytes:
                    # Replacing an entry with an uncacheable value must not expose the old value.
                    db.execute("DELETE FROM cache WHERE key=?", (key,))
                    return
                db.execute("""INSERT INTO cache(key, value_json, size_bytes, expires, last_used, type)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(key) DO UPDATE SET
                    value_json=excluded.value_json, size_bytes=excluded.size_bytes,
                    expires=excluded.expires, last_used=excluded.last_used, type=excluded.type""",
                    (key, payload, size, now + ttl, now, key.split(":", 1)[0]))
                total = db.execute("SELECT COALESCE(SUM(size_bytes), 0) FROM cache").fetchone()[0]
                if total > self.cache_budget_bytes:
                    for row in db.execute("SELECT key, size_bytes FROM cache ORDER BY last_used, rowid").fetchall():
                        db.execute("DELETE FROM cache WHERE key=?", (row["key"],))
                        total -= row["size_bytes"]
                        if total <= self.cache_budget_bytes:
                            break
        except (sqlite3.Error, ValueError, TypeError, OverflowError, RuntimeError, RecursionError):
            return

    def health(self) -> bool:
        with self._lock:
            if self._closed:
                return False
            try:
                return self._conn.execute("SELECT 1").fetchone()[0] == 1
            except sqlite3.Error:
                return False

    def close(self) -> None:
        with self._lock:
            if not self._closed:
                self._conn.close()
                self._closed = True
