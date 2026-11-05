import httpx
import pytest
from x_read.config import Settings
from x_read.models import SearchResult, SourceError
from x_read.services import Reader
from x_read.sources.official import FanqieSource
from x_read.sources.transport import Transport
from x_read.storage import Store


async def test_website_search_fallback_keeps_its_pagination_chain():
    calls = []
    def handler(request):
        calls.append(str(request.url))
        if request.url.host == 'novel.snssdk.com':
            return httpx.Response(503)
        page = request.url.params['page_index']
        return httpx.Response(200, json={'code': 0, 'data': {'search_book_data_list': [
            {'book_id': '123' if page == '0' else '456', 'book_name': '书'}], 'total_count': 11}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        source = FanqieSource(Transport(client, interval=0))
        first = await source.search('书')
        assert first.provider == 'official_website' and first.has_more is True
        assert len(calls) == 3
        second = await source.search('书', first.next_cursor)
        assert second.items[0].book_id == '456' and second.has_more is False
        assert len(calls) == 4 and 'page_index=1' in calls[-1]
        with pytest.raises(SourceError):
            await source.search('别的书', first.next_cursor)


async def test_unknown_total_is_not_guessed_from_item_count():
    def handler(request):
        if request.url.host == 'novel.snssdk.com':
            return httpx.Response(404)
        return httpx.Response(200, json={'code': 0, 'data': {'search_book_data_list': []}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        result = await FanqieSource(Transport(client, interval=0)).search('书')
        assert result.has_more is None and result.next_cursor is None


async def test_search_denied_does_not_fallback():
    calls = 0
    def handler(request):
        nonlocal calls
        calls += 1
        return httpx.Response(403)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(SourceError) as result:
            await FanqieSource(Transport(client, interval=0)).search('书')
        assert result.value.status == 403 and calls == 1


async def test_search_cache_bound_to_provider_instance(tmp_path):
    class Source:
        provider = 'official'
        def __init__(self):
            self.calls = 0
        async def search(self, query, cursor=None):
            self.calls += 1
            return SearchResult(items=[], provider=self.provider, has_more=False)
    store = Store(str(tmp_path / 'cache.db'))
    try:
        a, b = Source(), Source()
        reader = Reader(a, store, Settings())
        await reader.search('书', None)
        await reader.search('书', None)
        assert a.calls == 1
        await Reader(b, store, Settings()).search('书', None)
        assert b.calls == 1
    finally:
        store.close()
