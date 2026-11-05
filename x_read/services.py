"""Reading operations and bounded successful-result caching."""
import hashlib
import json
import logging
import secrets
from datetime import datetime, timezone
from collections.abc import Awaitable, Callable
from typing import Any, TypeVar

from pydantic import BaseModel
from x_read.config import Settings
from x_read.models import (
    AuthorBooksResult,
    AuthorProfile,
    Book,
    BookReviewsResult,
    ChapterContent,
    ChapterReviewsResult,
    Directory,
    HotSearchItem,
    PrefetchResult,
    RankingCatalog,
    RankingPage,
    SearchResult,
    SourceError,
)
from x_read.sources.base import BookSource
from x_read.storage import Store

M = TypeVar("M", bound=BaseModel)
log = logging.getLogger("x_read")


class Reader:
    def __init__(self, source: BookSource, store: Store, settings: Settings):
        self.source, self.store, self.settings = source, store, settings
        # Provider cursors are signed with a per-instance key. Do not reuse a
        # persisted first page (containing an old cursor) after process restart.
        self._search_session = secrets.token_hex(16)

    @staticmethod
    def check_source(source: str):
        if source != "fanqie":
            raise SourceError("SOURCE_NOT_FOUND", "书源不存在", 404)

    async def _cached(self, kind: str, args: list[Any], model: type[M], call: Callable[[BookSource], Awaitable[M]], *, refresh: bool = False) -> tuple[M, dict]:
        identity = [self.source.provider, kind, args]
        key = kind + ":" + hashlib.sha256(json.dumps(identity, ensure_ascii=False).encode()).hexdigest()
        try:
            hit = None if refresh else self.store.cache_get(key)
            if hit:
                return model.model_validate(hit["value"]), {"cached": True, "stale": False}
        except Exception:
            log.warning("cache read failed")
        try:
            result = await call(self.source)
        except SourceError as error:
            if error.temporary and kind != "search":
                try:
                    hit = self.store.cache_get(key, allow_stale=True)
                    if hit:
                        return model.model_validate(hit["value"]), {"cached": True, "stale": True, "reason": error.code}
                except Exception:
                    log.warning("stale cache read failed")
            raise
        ttl = self.settings.chapter_ttl if kind == "chapter" else self.settings.metadata_ttl
        if kind == "search":
            ttl = min(ttl, 300)  # Always shorter than the provider's 900s cursor TTL.
        try:
            self.store.cache_put(key, result.model_dump(mode="json"), ttl)
        except Exception:
            log.warning("cache write failed")
        return result, {"cached": False, "stale": False}

    async def search(self, query: str, cursor: str | None):
        # Cursor validation/expiry must run even if an identical cursor was cached.
        if cursor:
            return await self.source.search(query, cursor), {"cached": False, "stale": False}
        return await self._cached("search", [self._search_session, query], SearchResult, lambda s: s.search(query))

    async def book(self, book_id: str, *, refresh: bool = False):
        return await self._cached("book", [book_id], Book, lambda s: s.get_book(book_id), refresh=refresh)

    async def check_updates(self, source: str, book_id: str):
        self.check_source(source)
        before = self.store.shelf_get(source, book_id)
        if before is None:
            raise SourceError("SHELF_NOT_FOUND", "该书尚未加入书架", 404)
        fresh, meta = await self.book(book_id, refresh=True)
        if meta["stale"]:
            raise SourceError("UPSTREAM_UNAVAILABLE", "更新检查未取得最新数据", 503)
        old_id, new_id = before.get("latest_chapter_id"), fresh.latest_chapter_id
        old_count, new_count = before.get("chapter_count"), fresh.chapter_count
        # Missing fields mean unknown, not 'no update'. A shrinking count is
        # metadata change, not evidence of newly published chapters.
        added = max(0, new_count - old_count) if old_count is not None and new_count is not None else None
        if old_id is not None and new_id is not None:
            updated = old_id != new_id
        elif added is not None:
            updated = added > 0
        else:
            updated = None
        saved = self.store.shelf_refresh(fresh)
        if saved is None:
            raise SourceError("SHELF_NOT_FOUND", "检查期间该书已移出书架", 404)
        return {"source": source, "book_id": book_id, "updated": updated,
                "added_chapters": added, "previous_latest_chapter_id": old_id,
                "latest_chapter_id": new_id, "book": fresh, "checked_at": datetime.now(timezone.utc).isoformat()}, meta

    async def directory(self, book_id: str):
        return await self._cached("directory", [book_id], Directory, lambda s: s.get_chapters(book_id))

    async def chapter(self, book_id: str, chapter_id: str):
        return await self._cached("chapter", [book_id, chapter_id], ChapterContent, lambda s: s.get_chapter(book_id, chapter_id))

    async def validate_chapter(self, book_id: str, chapter_id: str):
        directory, meta = await self.directory(book_id)
        if meta["stale"]:
            raise SourceError("UPSTREAM_UNAVAILABLE", "目录暂时不可刷新，请稍后保存进度", 503)
        if not any(ch.chapter_id == chapter_id for ch in directory.chapters):
            raise SourceError("CHAPTER_NOT_FOUND", "章节不属于指定书籍", 404)

    async def ranking_catalog(self, source: str = "fanqie"):
        self.check_source(source)
        if not hasattr(self.source, "ranking_catalog"):
            raise SourceError("UNSUPPORTED", "书源不支持排行榜", 501)
        return await self._cached("ranking_catalog", [], RankingCatalog, lambda s: s.ranking_catalog())

    async def rankings(self, source: str = "fanqie", gender: str = "male", type: str = "reading", category: str | None = None, page: int = 1, version: str | None = None):
        self.check_source(source)
        if not hasattr(self.source, "rankings"):
            raise SourceError("UNSUPPORTED", "书源不支持排行榜", 501)
        return await self._cached(
            "rankings",
            [gender, type, category, page, version],
            RankingPage,
            lambda s: s.rankings(gender=gender, type=type, category=category, page=page, version=version),
        )

    def _provider_for(self, method_name: str):
        return self.source if hasattr(self.source, method_name) else None

    async def hot_search(self, source: str = "fanqie"):
        self.check_source(source)
        provider = self._provider_for("get_hot_search")
        if not provider:
            raise SourceError("UNSUPPORTED", "书源不支持热搜", 501)
        identity = [provider.provider, None, "hot_search", []]
        key = "hot_search:" + hashlib.sha256(json.dumps(identity, ensure_ascii=False).encode()).hexdigest()
        hit = self.store.cache_get(key)
        if hit:
            return [HotSearchItem.model_validate(x) for x in hit["value"]], {"cached": True, "stale": False}
        try:
            res = await provider.get_hot_search()
        except SourceError as error:
            if error.temporary:
                hit = self.store.cache_get(key, allow_stale=True)
                if hit:
                    return [HotSearchItem.model_validate(x) for x in hit["value"]], {"cached": True, "stale": True, "reason": error.code}
            raise
        self.store.cache_put(key, [x.model_dump(mode="json") for x in res], 1800)
        return res, {"cached": False, "stale": False}

    async def related_books(self, source: str, book_id: str):
        self.check_source(source)
        provider = self._provider_for("get_related_books")
        if not provider:
            raise SourceError("UNSUPPORTED", "书源不支持相关推荐", 501)
        identity = [provider.provider, None, "related_books", [book_id]]
        key = "related_books:" + hashlib.sha256(json.dumps(identity, ensure_ascii=False).encode()).hexdigest()
        hit = self.store.cache_get(key)
        if hit:
            return [Book.model_validate(x) for x in hit["value"]], {"cached": True, "stale": False}
        try:
            res = await provider.get_related_books(book_id)
        except SourceError as error:
            if error.temporary:
                hit = self.store.cache_get(key, allow_stale=True)
                if hit:
                    return [Book.model_validate(x) for x in hit["value"]], {"cached": True, "stale": True, "reason": error.code}
            raise
        self.store.cache_put(key, [x.model_dump(mode="json") for x in res], self.settings.metadata_ttl)
        return res, {"cached": False, "stale": False}

    async def book_reviews(self, source: str, book_id: str, offset: int = 0, count: int = 20):
        self.check_source(source)
        provider = self._provider_for("get_book_reviews")
        if not provider:
            raise SourceError("UNSUPPORTED", "书源不支持书评", 501)
        return await self._cached("book_reviews", [book_id, offset, count], BookReviewsResult, lambda s: provider.get_book_reviews(book_id, offset, count))

    async def chapter_reviews(self, source: str, book_id: str, chapter_id: str):
        self.check_source(source)
        await self.validate_chapter(book_id, chapter_id)
        provider = self._provider_for("get_chapter_reviews")
        if not provider:
            raise SourceError("UNSUPPORTED", "书源不支持段评与章评", 501)
        return await self._cached("chapter_reviews", [chapter_id], ChapterReviewsResult, lambda s: provider.get_chapter_reviews(chapter_id))

    async def author_profile(self, source: str, author_id: str):
        self.check_source(source)
        provider = self._provider_for("get_author")
        if not provider:
            raise SourceError("UNSUPPORTED", "书源不支持作者主页", 501)
        return await self._cached("author_profile", [author_id], AuthorProfile, lambda s: provider.get_author(author_id))

    async def author_books(self, source: str, author_id: str, offset: int = 0, count: int = 20):
        self.check_source(source)
        provider = self._provider_for("get_author_books")
        if not provider:
            raise SourceError("UNSUPPORTED", "书源不支持作者作品集", 501)
        return await self._cached("author_books", [author_id, offset, count], AuthorBooksResult, lambda s: provider.get_author_books(author_id, offset, count))

    async def prefetch_chapters(self, source: str, book_id: str, chapter_ids: list[str]) -> tuple[PrefetchResult, dict]:
        self.check_source(source)
        if not chapter_ids:
            return PrefetchResult(prefetched_count=0, chapter_ids=[]), {"cached": False, "stale": False}
        directory, _ = await self.directory(book_id)
        valid_cids = {c.chapter_id for c in directory.chapters}
        target_cids = [cid for cid in chapter_ids if cid in valid_cids]
        if not target_cids:
            return PrefetchResult(prefetched_count=0, chapter_ids=[]), {"cached": False, "stale": False}

        needed_cids = []
        for cid in target_cids:
            identity = [self.source.provider, "chapter", [book_id, cid]]
            key = "chapter:" + hashlib.sha256(json.dumps(identity, ensure_ascii=False).encode()).hexdigest()
            if not self.store.cache_get(key):
                needed_cids.append(cid)

        if not needed_cids:
            return PrefetchResult(prefetched_count=0, chapter_ids=target_cids), {"cached": True, "stale": False}

        prefetched = []
        for cid in needed_cids:
            if cid not in prefetched:
                try:
                    await self.chapter(book_id, cid)
                    prefetched.append(cid)
                except Exception as e:
                    log.warning("prefetch chapter %s failed: %s", cid, e)

        return PrefetchResult(prefetched_count=len(prefetched), chapter_ids=target_cids), {"cached": False, "stale": False}

