import pytest
from fastapi.testclient import TestClient
from x_read.app import create_app
from x_read.config import Settings
from x_read.models import Book, Directory, ChapterSummary, SourceError


class Source:
    provider = 'test'
    def __init__(self):
        self.latest = '11'
        self.count = 1
        self.error = None
        self.calls = 0
    async def get_book(self, book_id):
        self.calls += 1
        if self.error:
            raise self.error
        return Book(book_id=book_id, title='书', chapter_count=self.count, latest_chapter_id=self.latest)
    async def get_chapters(self, book_id):
        return Directory(book_id=book_id, chapters=[ChapterSummary(chapter_id='11', title='章', order=0)], total=1, provider=self.provider)


@pytest.fixture
def ctx(tmp_path):
    source = Source()
    app = create_app(Settings(db_path=str(tmp_path/'state.db')), source=source)
    with TestClient(app) as c:
        yield c, source


def test_bookmarks_notes_and_scoping(ctx):
    c, _ = ctx
    path = '/api/v1/books/fanqie/1/bookmarks'
    item = path + '/' + 'a'*32
    body = {'chapter_id': '11', 'offset': 5, 'note': '笔记', 'quote': '<script>text is data</script>'}
    r = c.put(item, json=body)
    assert r.status_code == 200, r.text
    created = r.json()['data']['created_at']
    assert c.put(item, json={**body, 'note': '修改'}).json()['data']['created_at'] == created
    assert c.get(path).json()['data']['total'] == 1
    assert c.get('/api/v1/books/fanqie/2/bookmarks').json()['data']['total'] == 0
    assert c.put(item, json={**body, 'chapter_id': '99'}).status_code == 404
    assert c.put(item, json={**body, 'note': 'a'*5001}).status_code == 422
    assert c.put(item, json={**body, 'offset': True}).status_code == 422
    assert c.put(path+'/bad-id', json=body).status_code == 422
    assert c.delete(item).json()['data']['removed'] is True
    assert c.delete(item).json()['data']['removed'] is False


def test_history_records_progress_not_read_requests(ctx):
    c, _ = ctx
    path = '/api/v1/progress/fanqie/1'
    assert c.get('/api/v1/history').json()['data']['total'] == 0
    for offset in [1, 2]:
        assert c.put(path, json={'chapter_id': '11', 'offset': offset}).status_code == 200
    history = c.get('/api/v1/history').json()['data']
    assert history['total'] == 1 and history['items'][0]['offset'] == 2
    assert c.delete('/api/v1/history').json()['data']['removed'] == 1
    assert c.get(path).json()['data']['offset'] == 2


def test_update_check_bypasses_cache_preserves_shelf(ctx):
    c, source = ctx
    path = '/api/v1/bookshelf/fanqie/1'
    before = c.put(path).json()['data']
    source.latest, source.count = '12', 3
    checked = c.post(path+'/check-updates')
    assert checked.status_code == 200, checked.text
    data = checked.json()['data']
    assert data['updated'] is True and data['added_chapters'] == 2
    assert data['checked_at'] and source.calls == 2
    after = c.get('/api/v1/bookshelf').json()['data'][0]
    assert after['added_at'] == before['added_at'] and after['latest_chapter_id'] == '12'
    assert c.post(path+'/check-updates').json()['data']['updated'] is False
    source.error = SourceError('UPSTREAM_UNAVAILABLE','offline',502)
    assert c.post(path+'/check-updates').status_code == 503
    assert c.get('/api/v1/bookshelf').json()['data'][0]['latest_chapter_id'] == '12'
    c.delete(path)
    assert c.post(path+'/check-updates').status_code == 404


def test_unknown_update_is_not_no_update(ctx):
    c, source = ctx
    source.latest = source.count = None
    c.put('/api/v1/bookshelf/fanqie/1')
    r = c.post('/api/v1/bookshelf/fanqie/1/check-updates')
    assert r.json()['data']['updated'] is None
    assert r.json()['data']['added_chapters'] is None


def test_cache_management_and_error_envelope(ctx):
    c, _ = ctx
    c.put('/api/v1/bookshelf/fanqie/1')
    stats = c.get('/api/v1/cache').json()['data']
    assert stats['entries'] > 0 and stats['bytes'] > 0
    assert c.delete('/api/v1/cache?kind=book').json()['data']['removed'] > 0
    assert c.get('/api/v1/bookshelf').json()['data']
    assert c.delete('/api/v1/cache?kind=invalid').status_code == 422
    assert c.get('/missing').json()['error']['code'] == 'NOT_FOUND'
    assert c.post('/health').status_code == 405
    assert c.post('/health').json()['error']['request_id']


def test_new_routes_require_token(tmp_path):
    with TestClient(create_app(Settings(db_path=str(tmp_path/'a.db'),api_token='secret'),source=Source())) as c:
        for method,path in [('get','/api/v1/history'),('delete','/api/v1/history'),('get','/api/v1/cache'),('delete','/api/v1/cache'),('post','/api/v1/bookshelf/fanqie/1/check-updates'),('get','/api/v1/books/fanqie/1/bookmarks')]:
            assert getattr(c,method)(path).status_code == 401
        schema=c.get('/openapi.json',headers={'Authorization':'Bearer secret'}).json()
        assert 'HTTPBearer' in schema['components']['securitySchemes']
