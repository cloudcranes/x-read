import pytest
from x_read.config import Settings
from x_read.models import Book, SourceError
from x_read.services import Reader


class MemoryCache:
    """按 key 存取的假缓存。

    旧实现完全忽略 key（任何 key 都返回同一个 hit），因此永远测不出
    缓存键冲突，也掩盖了「热搜/相关推荐写不进缓存」那类问题。
    """
    def __init__(self):
        self.entries = {}
        self.expired_keys = set()
        self.puts = []
    @property
    def hit(self):
        return next(iter(self.entries.values()), None)
    @hit.setter
    def hit(self, value):
        self.entries = {"*": value} if value is not None else {}
    @property
    def expired(self):
        return bool(self.expired_keys)
    @expired.setter
    def expired(self, value):
        self.expired_keys = set(self.entries) if value else set()
    def cache_get(self, key, allow_stale=False):
        if key not in self.entries:
            return None
        stale = key in self.expired_keys
        if stale and not allow_stale:
            return None
        return {"value": self.entries[key], "stale": stale}
    def cache_put(self, key, value, ttl):
        self.puts.append((key, value))
        self.entries[key] = value


class Provider:
    provider = "test"
    failure = None
    async def get_book(self, book_id):
        if self.failure:
            raise self.failure
        return Book(book_id=book_id, title="test")


async def test_expired_data_served_only_for_temporary_failure():
    cache, provider = MemoryCache(), Provider()
    reader = Reader(provider, cache, Settings())
    await reader.book("1")
    cache.expired = True
    provider.failure = SourceError("UPSTREAM_UNAVAILABLE", "offline", 502)
    book, meta = await reader.book("1")
    assert book.book_id == "1" and meta["stale"] is True
    provider.failure = SourceError("ACCESS_REQUIRED", "locked", 403)
    with pytest.raises(SourceError):
        await reader.book("1")


async def test_cache_failures_do_not_break_reading():
    class BrokenCache:
        def cache_get(self, *args, **kwargs):
            raise OSError("disk")
        def cache_put(self, *args, **kwargs):
            raise OSError("disk")
    book, meta = await Reader(Provider(), BrokenCache(), Settings()).book("1")
    assert book.book_id == "1" and meta["cached"] is False


class HotProvider:
    provider = "test"
    def __init__(self):
        self.calls = 0
    async def get_hot_search(self):
        from x_read.models import HotSearchItem
        self.calls += 1
        return [HotSearchItem(word="热词", is_hot=True)]
    async def get_related_books(self, book_id):
        self.calls += 1
        return [Book(book_id="9", title="相关")]


async def test_hot_search_is_cached_by_key():
    """热搜载荷是列表：缓存层只接受 dict 时会静默丢弃，每次都打上游。"""
    cache, provider = MemoryCache(), HotProvider()
    reader = Reader(provider, cache, Settings())
    first, meta = await reader.hot_search()
    assert meta["cached"] is False and provider.calls == 1
    second, meta2 = await reader.hot_search()
    assert meta2["cached"] is True, "第二次取热搜必须命中缓存"
    assert provider.calls == 1, "命中缓存时不应再打上游"
    assert [x.word for x in second] == [x.word for x in first]
    assert cache.puts and isinstance(cache.puts[0][1], list), "列表载荷必须能写进缓存"


async def test_related_books_is_cached_by_key():
    cache, provider = MemoryCache(), HotProvider()
    reader = Reader(provider, cache, Settings())
    await reader.related_books("fanqie", "1")
    assert provider.calls == 1
    _, meta = await reader.related_books("fanqie", "1")
    assert meta["cached"] is True
    assert provider.calls == 1
