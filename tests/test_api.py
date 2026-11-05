import pytest
from fastapi.testclient import TestClient
from x_read.app import create_app
from x_read.config import Settings
from x_read.models import Book, ChapterContent, ChapterSummary, Directory, SearchResult, SourceError
from x_read.storage import Store


class FakeSource:
    provider = "fake_official"

    def __init__(self):
        self.calls = 0
        self.failure = None

    def touch(self):
        self.calls += 1
        if self.failure:
            raise self.failure

    async def search(self, query, cursor=None):
        self.touch()
        if cursor and cursor != "next":
            raise SourceError("INVALID_CURSOR", "游标不匹配", 400)
        return SearchResult(items=[Book(book_id="7276384138653862966", title=query)], provider=self.provider, has_more=True, next_cursor="next")

    async def get_book(self, book_id):
        self.touch()
        return Book(book_id=book_id, title="测试书", author="作者")

    async def get_chapters(self, book_id):
        self.touch()
        return Directory(book_id=book_id, chapters=[ChapterSummary(chapter_id="11", title="第一章", order=1, volume="上卷"), ChapterSummary(chapter_id="12", title="第二章", order=2, volume="下卷", locked=True)], total=2, provider=self.provider)

    async def get_chapter(self, book_id, chapter_id):
        self.touch()
        if chapter_id == "12":
            raise SourceError("ACCESS_REQUIRED", "章节当前不可读", 403)
        if chapter_id != "11":
            raise SourceError("CHAPTER_NOT_FOUND", "章节不属于指定书籍", 404)
        return ChapterContent(book_id=book_id, chapter_id=chapter_id, title="第一章", content="测试内容", paragraphs=["测试内容"], provider=self.provider, completeness="website_visible")


@pytest.fixture
def setup(tmp_path):
    source = FakeSource()
    app = create_app(Settings(db_path=str(tmp_path / "reader.db")), source=source)
    with TestClient(app) as client:
        yield client, source


def test_health_and_schema(setup):
    c, _ = setup
    assert c.get("/health").json()["database"] == "ok"
    schema = c.get("/openapi.json").json()
    assert "/api/v1/search" in schema["paths"]
    assert c.get("/docs").status_code == 200


def test_search_and_caching(setup):
    c, source = setup
    a = c.get("/api/v1/search", params={"q": "测试"})
    assert a.status_code == 200
    assert a.json()["data"]["items"][0]["book_id"] == "7276384138653862966"
    b = c.get("/api/v1/search", params={"q": "测试"})
    assert b.json()["meta"]["cached"] is True
    assert source.calls == 1
    assert c.get("/api/v1/search", params={"q": "测试", "cursor": "wrong"}).status_code == 400


def test_directory_pagination(setup):
    c, _ = setup
    path = "/api/v1/books/fanqie/1/chapters"
    data = c.get(path, params={"limit": 1}).json()["data"]
    assert data["total"] == 2 and data["next_offset"] == 1
    assert data["chapters"][0]["volume"] == "上卷"
    assert c.get(path, params={"offset": 2}).json()["data"]["has_more"] is False
    assert c.get(path, params={"limit": 201}).status_code == 422


def test_read_chapter_and_failures(setup):
    c, _ = setup
    path = "/api/v1/books/fanqie/1/chapters/"
    assert c.get(path + "11").json()["data"]["completeness"] == "website_visible"
    denied = c.get(path + "12")
    assert denied.status_code == 403 and denied.json()["error"]["code"] == "ACCESS_REQUIRED"
    assert c.get(path + "13").status_code == 404


def test_shelf_progress_and_remove(setup):
    c, _ = setup
    shelf, progress = "/api/v1/bookshelf/fanqie/1", "/api/v1/progress/fanqie/1"
    first = c.put(shelf).json()["data"]
    second = c.put(shelf).json()["data"]
    assert first["added_at"] == second["added_at"]
    assert c.get(progress).json()["data"] is None
    saved = c.put(progress, json={"chapter_id": "11", "offset": 10})
    assert saved.status_code == 200
    assert c.get("/api/v1/bookshelf").json()["data"][0]["progress"]["offset"] == 10
    assert c.put(progress, json={"chapter_id": "999", "offset": 0}).status_code == 404
    assert c.put(progress, json={"chapter_id": "11", "offset": -1}).status_code == 422
    assert c.delete(shelf).json()["data"]["removed"] is True
    assert c.get(progress).json()["data"]["chapter_id"] == "11"
    assert c.get("/api/v1/bookshelf").json()["data"] == []


def test_validation_and_error_envelope(setup):
    c, _ = setup
    for url in ["/api/v1/search?q=%20", "/api/v1/books/fanqie/abc", "/api/v1/books/fanqie/1234567890123456789012345"]:
        response = c.get(url)
        assert response.status_code == 422
        assert response.json()["error"]["request_id"] == response.headers["X-Request-ID"]
    assert c.get("/api/v1/search?q=a&source=other").status_code == 404


def test_token_and_privacy(tmp_path):
    secret = "test-secret-123"
    app = create_app(Settings(db_path=str(tmp_path / "auth.db"), api_token=secret), source=FakeSource())
    with TestClient(app) as c:
        assert c.get("/health").status_code == 200
        for path in ["/docs", "/openapi.json", "/api/v1/bookshelf"]:
            assert c.get(path).status_code == 401
            assert c.get(path, headers={"Authorization": f"Bearer {secret}"}).status_code == 200
        assert secret not in c.get("/api/v1/sources", headers={"Authorization": f"Bearer {secret}"}).text
        assert c.get("/api/v1/bookshelf?token=" + secret).status_code == 401


def test_rate_limit_headers(setup):
    c, source = setup
    source.failure = SourceError("UPSTREAM_RATE_LIMIT", "请稍后重试", 429, 30)
    response = c.get("/api/v1/search?q=x")
    assert response.status_code == 429
    assert response.headers["Retry-After"] == "30"


def test_settings():
    with pytest.raises(ValueError):
        Settings(host="0.0.0.0")
    with pytest.raises(ValueError):
        Settings(cors_origins=("*",))
    assert Settings(host="::1").host == "::1"
