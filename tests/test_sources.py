"""Offline wire-contract tests. No live upstream calls."""
import asyncio
import json
import time
import httpx
import pytest
from x_read.models import SourceError
from x_read.sources.common import Cursor
from x_read.sources.official import FanqieSource, initial_state
from x_read.sources.transport import Transport


def transport(handler, **kwargs):
    return Transport(httpx.AsyncClient(transport=httpx.MockTransport(handler)), interval=0, **kwargs)


def page(state):
    return '<script>window.__INITIAL_STATE__ = ' + json.dumps(state) + ';</script>'


def chapter(**updates):
    row = dict(bookId='123', itemId='456', title='第一章', content='<p>正文</p><p>第二段 &amp; 内容</p>', preItemId='0', nextItemId='789')
    row.update(updates)
    return {'reader': {'chapterData': row}, 'common': {'css': 'font-family:DNMrHsV173Pd4pgy;'}}


async def test_official_contract():
    def handler(request):
        path = request.url.path
        if path.endswith('/search/v1/'):
            assert request.url.params['q'] == '书名'
            return httpx.Response(200, json={'code': 0, 'data': {'ret_data': [{'book_id': '123', 'title': '书名'}], 'has_more': 1, 'offset': 10}})
        if path == '/page/123':
            return httpx.Response(200, text=page({'page': {'bookId': '123', 'bookName': '书名', 'wordNumber': '100'}}))
        if path == '/api/reader/directory/detail':
            return httpx.Response(200, json={'code': 0, 'data': {'chapterListWithVolume': [[{'itemId': '456', 'title': '第一章', 'wordNumber': 20}]], 'volumeNameList': ['卷一']}})
        return httpx.Response(200, text=page(chapter()))
    source = FanqieSource(transport(handler))
    result = await source.search('书名')
    assert result.items[0].book_id == '123' and result.next_cursor
    with pytest.raises(SourceError, match='cursor'):
        await source.search('另一书', result.next_cursor)
    assert (await source.get_book('123')).word_count == 100
    directory = await source.get_chapters('123')
    assert directory.chapters[0].volume == '卷一'
    content = await source.get_chapter('123', '456')
    assert content.completeness == 'website_visible'
    assert content.paragraphs == ['正文', '第二段 & 内容']
    assert content.next_chapter_id == '789' and content.previous_chapter_id is None
    await source.transport.client.aclose()


@pytest.mark.parametrize('update,code', [({'bookId': '999'}, 'UPSTREAM_INVALID'), ({'itemId': '999'}, 'UPSTREAM_INVALID'), ({'isChapterLock': True}, 'CHAPTER_LOCKED'), ({'needPay': '1'}, 'CHAPTER_LOCKED'), ({'content': ''}, 'UPSTREAM_INVALID'), ({'content': '<p>\ue000</p>'}, 'FONT_CHANGED')])
async def test_official_rejects_bad_chapters(update, code):
    t = transport(lambda _: httpx.Response(200, text=page(chapter(**update))))
    with pytest.raises(SourceError) as exc:
        await FanqieSource(t).get_chapter('123', '456')
    assert exc.value.code == code
    await t.client.aclose()


async def test_font_is_family_scoped_and_corrected():
    state = chapter(content='<p>&#xE3E8;\ue49f</p>')
    t = transport(lambda _: httpx.Response(200, text=page(state)))
    source = FanqieSource(t)
    result = await source.get_chapter('123', '456')
    assert result.content == 'D意'
    state['common']['css'] = 'font-family:Other;'
    with pytest.raises(SourceError) as exc:
        await source.get_chapter('123', '456')
    assert exc.value.code == 'FONT_CHANGED'
    await t.client.aclose()


def test_initial_state_never_executes_js():
    for text in ['window.__INITIAL_STATE__=function(){return {}}()', 'window.__INITIAL_STATE__={"page": undefined}', 'no state']:
        with pytest.raises(SourceError):
            initial_state(text)
    assert initial_state('window.__INITIAL_STATE__={"text":"}; evil()"}; evil()')['text'] == '}; evil()'


@pytest.mark.parametrize('token', ['', '!!!!', 'a' * 9000, 'eyJvIjoxfQ'])
def test_bad_cursor(token):
    with pytest.raises(SourceError):
        Cursor('official').decode(token, 'q')


def test_cursor_binding_expiry_and_schema(monkeypatch):
    cursor = Cursor('official')
    token = cursor.encode('query', 10)
    assert cursor.decode(token, 'query') == (10, '')
    with pytest.raises(SourceError):
        cursor.decode(token, 'other')
    with pytest.raises(SourceError):
        Cursor('elsewhere').decode(token, 'query')
    monkeypatch.setattr(time, 'time', lambda: 1)
    with pytest.raises(SourceError):
        cursor.decode(token, 'query')


async def test_transport_merge_retry_and_client_ownership():
    calls = 0
    async def handler(request):
        nonlocal calls
        calls += 1
        await asyncio.sleep(.01)
        return httpx.Response(503 if calls == 1 else 200, json={'ok': True})
    t = transport(handler)
    results = await asyncio.gather(*[t.json('https://example.test/x') for _ in range(5)])
    assert calls == 2 and all(r == {'ok': True} for r in results)
    await t.aclose()
    assert not t.client.is_closed
    await t.client.aclose()


async def test_transport_queue_and_cancellation():
    entered, release = asyncio.Event(), asyncio.Event()
    async def handler(request):
        entered.set()
        await release.wait()
        return httpx.Response(200, text='ok')
    t = transport(handler, max_queue=1)
    waiter = asyncio.create_task(t.text('https://example.test/a'))
    await entered.wait()
    with pytest.raises(SourceError) as exc:
        await t.text('https://example.test/b')
    assert exc.value.code == 'LOCAL_BUSY'
    shared = asyncio.create_task(t.text('https://example.test/a'))
    waiter.cancel()
    with pytest.raises(asyncio.CancelledError):
        await waiter
    release.set()
    assert await shared == 'ok'
    await t.client.aclose()


@pytest.mark.parametrize('response', [httpx.Response(200, text='12345'), httpx.Response(200, headers={'content-length': '999'}, text='x')])
async def test_transport_size_bound(response):
    t = transport(lambda _: response, max_response_bytes=4)
    with pytest.raises(SourceError):
        await t.get('https://example.test')
    await t.client.aclose()


async def test_transport_429_and_retry_cap():
    calls = 0
    def handler(request):
        nonlocal calls
        calls += 1
        return httpx.Response(429, headers={'retry-after': '2'})
    t = transport(handler)
    with pytest.raises(SourceError) as exc:
        await t.get('https://example.test')
    assert exc.value.retry_after == 2 and calls == 1 and t._cooldown > time.monotonic()
    with pytest.raises(SourceError) as cooled:
        await t.get('https://example.test/another')
    assert cooled.value.status == 429 and calls == 1
    await t.client.aclose()
    calls = 0
    t = transport(lambda _: httpx.Response(503))
    with pytest.raises(SourceError):
        await t.get('https://example.test')
    await t.client.aclose()


async def test_transport_transient_network_retry_only_once():
    calls = 0
    def handler(request):
        nonlocal calls
        calls += 1
        raise httpx.ReadTimeout('test')
    t = transport(handler)
    with pytest.raises(SourceError) as exc:
        await t.get('https://example.test')
    assert calls == 2 and exc.value.code == 'UPSTREAM_UNAVAILABLE'
    await t.client.aclose()


async def test_bad_ids_are_local():
    def handler(request):
        pytest.fail('Invalid IDs must not request network')
    t = transport(handler)
    for source in [FanqieSource(t)]:
        for value in ['../1', '1/2', 123, '1' * 25, '']:
            with pytest.raises(SourceError):
                await source.get_book(value)
    await t.client.aclose()
