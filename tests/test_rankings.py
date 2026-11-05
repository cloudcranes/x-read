import json
import httpx
import pytest
from fastapi.testclient import TestClient
from x_read.app import create_app
from x_read.config import Settings
from x_read.models import SourceError
from x_read.sources.official import FanqieSource
from x_read.sources.transport import Transport


def make_rank_html(font="DNMrHsV173Pd4pgy", categories=None):
    if categories is None:
        categories = {
            "male": [{"id": "1141", "name": "西方奇幻"}],
            "female": [{"id": "1139", "name": "古风世情"}],
        }
    state = {
        "common": {"css": f"@font-face{{font-family:{font};src:url(...);}}"},
        "rank": {"rankCategoryTypeList": categories},
    }
    return f"<html><head><script>window.__INITIAL_STATE__ = {json.dumps(state)};</script></head><body></body></html>"


def make_rank_api_response(books=None, total=100, version="1729872000"):
    if books is None:
        books = [
            {
                "bookId": "7276384138653862966",
                "bookName": "我不是戏神",
                "author": "三九音域",
                "abstract": "赤色流星划过天际后...",
                "thumbUri": "https://example.com/cover.jpg",
                "creationStatus": 1,
                "wordNumber": 4000000,
                "currentPos": 1,
                "rankPosDiff": 0,
                "read_count": 270000,
                "lastChapterItemId": "7634141276480225817",
                "lastChapterTitle": "完本感言。",
            }
        ]
    return {
        "code": 0,
        "data": {
            "book_list": books,
            "total_num": total,
            "rankVersion": version,
        },
    }


def make_handler(font="DNMrHsV173Pd4pgy", categories=None, books=None, total=100, version="1729872000"):
    def handler(request: httpx.Request):
        url = str(request.url)
        if "/rank" in url and "/api/" not in url:
            return httpx.Response(200, text=make_rank_html(font=font, categories=categories))
        if "/api/rank/category/list" in url:
            return httpx.Response(200, json=make_rank_api_response(books=books, total=total, version=version))
        return httpx.Response(404)
    return handler


async def test_rankings_source_catalog_and_page():
    handler = make_handler()
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        source = FanqieSource(Transport(client, interval=0))
        cat = await source.ranking_catalog()
        assert len(cat.genders) == 2
        assert cat.genders[0].id == "male"
        assert cat.genders[0].categories[0].id == "1141"
        assert cat.genders[0].categories[0].name == "西方奇幻"
        assert cat.genders[1].id == "female"
        assert cat.genders[1].categories[0].id == "1139"

        page = await source.rankings(gender="male", type="reading", category="1141", page=1)
        assert page.total == 100
        assert len(page.items) == 1
        assert page.items[0].book_id == "7276384138653862966"
        assert page.items[0].title == "我不是戏神"
        assert page.items[0].author == "三九音域"
        assert page.items[0].rank == 1
        assert page.has_more is True
        assert page.next_page == 2
        assert page.version == "1729872000"


async def test_rankings_source_font_error():
    handler = make_handler(font="UnknownFont")
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        source = FanqieSource(Transport(client, interval=0))
        with pytest.raises(SourceError) as exc:
            await source.ranking_catalog()
        assert exc.value.code == "FONT_CHANGED"


async def test_rankings_version_mismatch():
    handler = make_handler(version="1729872000")
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        source = FanqieSource(Transport(client, interval=0))
        with pytest.raises(SourceError) as exc:
            await source.rankings(gender="male", type="reading", page=2, version="1729800000")
        assert exc.value.code == "RANK_VERSION_CHANGED"


def test_rankings_api_endpoints(tmp_path):
    handler = make_handler()
    client_mock = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    source = FanqieSource(Transport(client_mock, interval=0))

    app = create_app(Settings(db_path=str(tmp_path / "rank.db")), source=source)
    with TestClient(app) as client:
        # Check source capabilities
        sources_res = client.get("/api/v1/sources")
        assert sources_res.status_code == 200
        assert "rankings" in sources_res.json()["data"][0]["capabilities"]

        # Catalog
        cat_res = client.get("/api/v1/rankings/catalog")
        assert cat_res.status_code == 200
        data = cat_res.json()["data"]
        assert len(data["genders"]) == 2
        assert data["genders"][0]["id"] == "male"

        # Rankings
        rank_res = client.get("/api/v1/rankings?gender=male&type=reading&page=1")
        assert rank_res.status_code == 200
        r_data = rank_res.json()["data"]
        assert r_data["total"] == 100
        assert r_data["items"][0]["title"] == "我不是戏神"
        assert r_data["items"][0]["rank"] == 1

        # Caching check
        rank_res2 = client.get("/api/v1/rankings?gender=male&type=reading&page=1")
        assert rank_res2.status_code == 200
        assert rank_res2.json()["meta"]["cached"] is True

        # Validation errors
        assert client.get("/api/v1/rankings?gender=other").status_code == 422
        assert client.get("/api/v1/rankings?type=invalid").status_code == 422
        assert client.get("/api/v1/rankings?page=0").status_code == 422
        assert client.get("/api/v1/rankings?source=unknown").status_code == 404
