"""HTTP interface. Start via python -m x_read to enforce binding settings."""
import hmac
import logging
import re
import sqlite3
import uuid
from contextlib import asynccontextmanager
import pathlib
from typing import Annotated, Generic, TypeVar
from urllib.parse import quote

from fastapi import Depends, FastAPI, Header, Path, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse, Response, StreamingResponse, FileResponse
from importlib.resources import files
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from starlette.exceptions import HTTPException as StarletteHTTPException
from pydantic import BaseModel

from x_read import __version__
from x_read.config import Settings, is_loopback
from x_read.models import (
    AuthorBooksResult,
    AuthorProfile,
    Book,
    BookmarkInput,
    BookReviewsResult,
    ChapterContent,
    ChapterReviewsResult,
    HotSearchItem,
    Model,
    PrefetchInput,
    PrefetchResult,
    ProgressInput,
    RankingCatalog,
    RankingPage,
    SearchResult,
    SourceError,
)
from x_read.services import Reader
from x_read.storage import Store

T = TypeVar("T")
Identifier = Annotated[str, Path(pattern=r"^[0-9]{1,24}$")]

# 请求体上限。旧实现只在 forward() 里把 body 全量读进内存之后才判 16KB，
# 而 Pydantic 路由（progress/bookmarks/prefetch）根本没有上限：监听局域网 +
# 匿名模式下，一个超大 body 就能把这个单进程打爆。
MAX_BODY_BYTES = 16 * 1024


def _too_large_response() -> JSONResponse:
    request_id = uuid.uuid4().hex
    return JSONResponse(
        {"error": {"code": "INVALID_ARGUMENT", "message": "请求体过大",
                   "request_id": request_id, "retry_after": None}},
        status_code=413,
        headers={"X-Request-ID": request_id, "Connection": "close"},
    )


class BodyLimitMiddleware:
    """在 ASGI 层拦截超大请求体。

    先按 Content-Length 预判（省掉读取），再对分块上传累计计数；
    两者任一超限都在应用读到 body 之前返回 413。
    """

    def __init__(self, app, max_bytes: int):
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        for name, value in scope.get("headers", []):
            if name == b"content-length":
                try:
                    declared = int(value)
                except ValueError:
                    await _too_large_response()(scope, receive, send)
                    return
                if declared > self.max_bytes:
                    await _too_large_response()(scope, receive, send)
                    return

        total = 0
        too_large = False
        replaced = False

        async def limited_receive():
            nonlocal total, too_large
            message = await receive()
            if message["type"] == "http.request":
                total += len(message.get("body", b""))
                if total > self.max_bytes:
                    # 不抛异常：异常会穿过 BaseHTTPMiddleware 被改写成 400。
                    # 截断 body 并记标记，响应写出时统一替换成 413。
                    too_large = True
                    return {"type": "http.request", "body": b"", "more_body": False}
            return message

        async def guarded_send(message):
            nonlocal replaced
            if too_large:
                if message["type"] == "http.response.start":
                    if not replaced:
                        replaced = True
                        await _too_large_response()(scope, receive, send)
                    return
                if replaced:
                    return
            await send(message)

        await self.app(scope, limited_receive, guarded_send)


class Meta(BaseModel):
    cached: bool = False
    stale: bool = False
    reason: str | None = None


class Envelope(BaseModel, Generic[T]):
    data: T
    meta: Meta


class DirectoryPage(Model):
    source: str
    book_id: str
    chapters: list[dict]
    total: int
    provider: str
    offset: int
    limit: int
    has_more: bool
    next_offset: int | None


def create_app(settings: Settings | None = None, *, source=None, store=None) -> FastAPI:
    settings = settings or Settings.from_env()

    @asynccontextmanager
    async def lifespan(app):
        from x_read.sources.official import FanqieSource
        from x_read.sources.transport import Transport
        settings.ensure_directory()
        local_store = store or Store(settings.db_path, cache_budget_bytes=settings.cache_budget_bytes)
        transport = None
        primary = source
        if primary is None:
            transport = Transport()
            primary = FanqieSource(transport)
        app.state.store = local_store
        app.state.reader = Reader(primary, local_store, settings)
        try:
            yield
        finally:
            if transport:
                await transport.aclose()
            if store is None:
                local_store.close()

    app = FastAPI(title="x-read", version=__version__, lifespan=lifespan,
                  description="Personal reading backend. Website-visible text is not necessarily App full text.",
                  docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = settings
    # 最外层：超大请求体在读入内存之前就拒掉
    app.add_middleware(BodyLimitMiddleware, max_bytes=MAX_BODY_BYTES)
    if settings.cors_origins:
        app.add_middleware(CORSMiddleware, allow_origins=list(settings.cors_origins), allow_methods=["GET", "PUT", "POST", "DELETE"], allow_headers=["Authorization", "Content-Type"])

    @app.middleware("http")
    async def request_id(request: Request, call_next):
        request.state.request_id = uuid.uuid4().hex
        response = await call_next(request)
        response.headers["X-Request-ID"] = request.state.request_id
        response.headers["Cache-Control"] = "private, no-store"
        return response

    def error_response(request, status, code, message, retry_after=None):
        request_id = getattr(request.state, "request_id", uuid.uuid4().hex)
        headers = {"X-Request-ID": request_id}
        if retry_after is not None:
            headers["Retry-After"] = str(retry_after)
        return JSONResponse({"error": {"code": code, "message": message, "request_id": request_id, "retry_after": retry_after}}, status_code=status, headers=headers)

    @app.exception_handler(SourceError)
    async def source_error(request, error):
        return error_response(request, error.status, error.code, error.message, error.retry_after)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, error):
        # Do not echo untrusted request bodies (which may include credentials).
        return error_response(request, 422, "INVALID_ARGUMENT", "参数格式或范围错误")

    @app.exception_handler(sqlite3.Error)
    async def database_error(request, error):
        logging.getLogger("x_read").error("database operation failed: %s", type(error).__name__)
        return error_response(request, 503, "STORAGE_UNAVAILABLE", "本地存储暂时不可用")

    @app.exception_handler(Exception)
    async def internal_error(request, error):
        logging.getLogger("x_read").error("request failed: %s", type(error).__name__)
        return error_response(request, 500, "INTERNAL_ERROR", "服务内部错误")

    bearer = HTTPBearer(auto_error=False)

    async def authenticate(credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)]):
        token = credentials.credentials if credentials else ""
        if settings.api_token and not hmac.compare_digest(token.encode(), settings.api_token.encode()):
            raise SourceError("AUTH_REQUIRED", "请输入有效的访问令牌", 401)

    @app.exception_handler(StarletteHTTPException)
    async def http_error(request, error):
        code = "NOT_FOUND" if error.status_code == 404 else "HTTP_ERROR"
        return error_response(request, error.status_code, code, "接口不存在" if error.status_code == 404 else "HTTP请求错误")

    def reader(request: Request):
        return request.app.state.reader

    def db(request: Request):
        return request.app.state.store

    def wrap(pair):
        value, meta = pair
        return {"data": value, "meta": meta}

    def _is_local_only() -> bool:
        """仅监听回环时才允许无令牌执行写/删；绑定到局域网就必须有令牌。"""
        return is_loopback(settings.host)

    async def authorize_destructive(
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)]):
        """删除类操作（清空历史/清空缓存）：局域网绑定时需要令牌。"""
        if not settings.api_token:
            if _is_local_only():
                return
            raise SourceError("AUTH_REQUIRED",
                              "监听局域网时必须配置访问令牌才能执行删除操作（X_READ_API_TOKEN）", 403)
        token = credentials.credentials if credentials else ""
        if not hmac.compare_digest(token.encode(), settings.api_token.encode()):
            raise SourceError("AUTH_REQUIRED", "请输入有效的访问令牌", 401)

    protected = [Depends(authenticate)]
    destructive = [Depends(authorize_destructive)]

    @app.get("/", response_class=HTMLResponse, include_in_schema=False)
    @app.get("/reader.html", response_class=HTMLResponse, include_in_schema=False)
    async def reader_page():
        # Public shell only; all private data stays behind API authentication.
        # FileResponse 走线程读盘：旧实现每次请求都在事件循环里 read_text 118KB。
        page = pathlib.Path(str(files("x_read").joinpath("web/reader.html")))
        if not page.is_file():
            return JSONResponse({"error": {"code": "NOT_FOUND", "message": "阅读页未打包"}},
                                status_code=404)
        return FileResponse(page, media_type="text/html; charset=utf-8",
                            headers={"X-Content-Type-Options": "nosniff", "Referrer-Policy": "no-referrer"})

    @app.api_route("/x-read.apk", methods=["GET", "HEAD"], include_in_schema=False)
    async def download_apk():
        """下载安卓安装包。

        APK 不再随 Python 包分发——内置的那份早已被淘汰（服务端依赖版、
        lib/ 里连内容核心都没有）。改为由 X_READ_APK_PATH 指向真实产物，
        未配置时明确返回 404，而不是发一个坏包出去。
        """
        if not settings.apk_path:
            return JSONResponse(
                {"error": {"code": "NOT_CONFIGURED",
                           "message": "未配置安装包路径（设置 X_READ_APK_PATH 指向 artifacts/x-read-standalone.apk）"}},
                status_code=404)
        # 注意：本文件里的裸 Path 是 fastapi.Path（路径参数声明），
        # 用它构造文件路径会抛「Path parameters cannot have a default value」。
        p = pathlib.Path(settings.apk_path)
        if not p.is_file():
            return JSONResponse({"error": {"code": "NOT_FOUND", "message": f"安装包不存在：{p}"}},
                                status_code=404)
        # 10MB 安装包不读进内存：FileResponse 分块发送并正确处理 HEAD/Range。
        return FileResponse(p, media_type="application/vnd.android.package-archive",
                            filename="x-read.apk")

    @app.api_route("/manifest.webmanifest", methods=["GET", "HEAD"], include_in_schema=False)
    @app.api_route("/manifest.json", methods=["GET", "HEAD"], include_in_schema=False)
    async def manifest_file():
        p = files("x_read").joinpath("web/manifest.webmanifest")
        # 非 editable 安装时打包遗漏会走到这里：返回 404 而不是 FileNotFoundError → 500
        if not p.is_file():
            return JSONResponse({"error": {"code": "NOT_FOUND", "message": "manifest 未打包"}},
                                status_code=404)
        return FileResponse(pathlib.Path(str(p)), media_type="application/manifest+json")

    def png_asset(name: str):
        p = files("x_read").joinpath(f"web/{name}")
        if not p.is_file():
            return JSONResponse({"error": {"code": "NOT_FOUND", "message": f"{name} 未打包"}},
                                status_code=404)
        return FileResponse(pathlib.Path(str(p)), media_type="image/png")

    @app.api_route("/icon-192.png", methods=["GET", "HEAD"], include_in_schema=False)
    async def icon_192():
        return png_asset("icon-192.png")

    @app.api_route("/icon-512.png", methods=["GET", "HEAD"], include_in_schema=False)
    async def icon_512():
        return png_asset("icon-512.png")

    @app.get("/health")
    def health(store=Depends(db)):
        if not store.health():
            raise SourceError("STORAGE_UNAVAILABLE", "数据库检查失败", 503)
        # apk 只反映「是否配置了下载路径」，前端据此决定是否显示下载入口，
        # 避免每次打开页面都用 HEAD 打到未配置的 404 地址。
        return {"status": "ok", "version": __version__, "database": "ok",
                "apk": bool(settings.apk_path)}

    @app.get("/openapi.json", dependencies=protected, include_in_schema=False)
    async def openapi():
        return app.openapi()

    @app.get("/docs", dependencies=protected, include_in_schema=False)
    async def docs():
        from fastapi.openapi.docs import get_swagger_ui_html
        return get_swagger_ui_html(openapi_url="/openapi.json", title="x-read API")

    @app.get("/api/v1/sources", dependencies=protected)
    async def sources(r=Depends(reader)):
        capabilities = ["search", "detail", "directory", "website_visible_chapter"]
        if hasattr(r.source, "rankings"):
            capabilities.append("rankings")
        return {"data": [{"id": "fanqie", "name": "番茄小说", "provider": r.source.provider,
                          "capabilities": capabilities}],
                "meta": {"cached": False, "stale": False}}

    @app.get("/api/v1/rankings/catalog", dependencies=protected, response_model=Envelope[RankingCatalog])
    async def ranking_catalog(source: str = "fanqie", r=Depends(reader)):
        r.check_source(source)
        return wrap(await r.ranking_catalog(source))

    @app.get("/api/v1/rankings", dependencies=protected, response_model=Envelope[RankingPage])
    async def rankings(
        source: str = "fanqie",
        gender: Annotated[str, Query(pattern=r"^(male|female)$")] = "male",
        type: Annotated[str, Query(pattern=r"^(reading|new)$")] = "reading",
        category: Annotated[str | None, Query(pattern=r"^[0-9]{1,24}$")] = None,
        page: Annotated[int, Query(ge=1, le=100)] = 1,
        version: Annotated[str | None, Query(pattern=r"^[0-9]{1,12}$")] = None,
        r=Depends(reader)
    ):
        r.check_source(source)
        return wrap(await r.rankings(source, gender=gender, type=type, category=category, page=page, version=version))

    @app.get("/api/v1/search/hot", dependencies=protected, response_model=Envelope[list[HotSearchItem]])
    async def hot_search(source: str = "fanqie", r=Depends(reader)):
        return wrap(await r.hot_search(source))

    @app.get("/api/v1/search", dependencies=protected, response_model=Envelope[SearchResult])
    async def search(q: Annotated[str, Query(min_length=1, max_length=100)], source: str = "fanqie", cursor: Annotated[str | None, Query(max_length=4096)] = None, r=Depends(reader)):
        r.check_source(source)
        if not q.strip():
            raise SourceError("INVALID_ARGUMENT", "搜索词为空", 422)
        return wrap(await r.search(q.strip(), cursor))

    @app.get("/api/v1/books/{source}/{book_id}", dependencies=protected, response_model=Envelope[Book])
    async def book(source: str, book_id: Identifier, r=Depends(reader)):
        r.check_source(source)
        return wrap(await r.book(book_id))

    @app.get("/api/v1/books/{source}/{book_id}/chapters", dependencies=protected, response_model=Envelope[DirectoryPage])
    async def chapters(source: str, book_id: Identifier, offset: Annotated[int, Query(ge=0, le=100000)] = 0, limit: Annotated[int, Query(ge=1, le=200)] = 100, r=Depends(reader)):
        r.check_source(source)
        directory, meta = await r.directory(book_id)
        selected = directory.chapters[offset:offset + limit]
        more = offset + len(selected) < directory.total
        return {"data": {**directory.model_dump(), "chapters": [c.model_dump() for c in selected], "offset": offset, "limit": limit, "has_more": more, "next_offset": offset + len(selected) if more else None}, "meta": meta}

    @app.get("/api/v1/books/{source}/{book_id}/chapters/{chapter_id}", dependencies=protected, response_model=Envelope[ChapterContent])
    async def chapter(source: str, book_id: Identifier, chapter_id: Identifier, r=Depends(reader)):
        r.check_source(source)
        return wrap(await r.chapter(book_id, chapter_id))

    @app.get("/api/v1/books/{source}/{book_id}/related", dependencies=protected, response_model=Envelope[list[Book]])
    async def related_books(source: str, book_id: Identifier, r=Depends(reader)):
        return wrap(await r.related_books(source, book_id))

    @app.get("/api/v1/books/{source}/{book_id}/reviews", dependencies=protected, response_model=Envelope[BookReviewsResult])
    async def book_reviews(source: str, book_id: Identifier, offset: Annotated[int, Query(ge=0, le=100000)] = 0, count: Annotated[int, Query(ge=1, le=100)] = 20, r=Depends(reader)):
        return wrap(await r.book_reviews(source, book_id, offset, count))

    @app.get("/api/v1/books/{source}/{book_id}/chapters/{chapter_id}/reviews", dependencies=protected, response_model=Envelope[ChapterReviewsResult])
    async def chapter_reviews(source: str, book_id: Identifier, chapter_id: Identifier, r=Depends(reader)):
        return wrap(await r.chapter_reviews(source, book_id, chapter_id))

    @app.post("/api/v1/books/{source}/{book_id}/prefetch", dependencies=protected, response_model=Envelope[PrefetchResult])
    async def prefetch_chapters(source: str, book_id: Identifier, body: PrefetchInput, r=Depends(reader)):
        return wrap(await r.prefetch_chapters(source, book_id, body.chapter_ids))

    @app.get("/api/v1/authors/{source}/{author_id}", dependencies=protected, response_model=Envelope[AuthorProfile])
    async def author_profile(source: str, author_id: Identifier, r=Depends(reader)):
        return wrap(await r.author_profile(source, author_id))

    @app.get("/api/v1/authors/{source}/{author_id}/books", dependencies=protected, response_model=Envelope[AuthorBooksResult])
    async def author_books(source: str, author_id: Identifier, offset: Annotated[int, Query(ge=0, le=100000)] = 0, count: Annotated[int, Query(ge=1, le=100)] = 20, r=Depends(reader)):
        return wrap(await r.author_books(source, author_id, offset, count))

    @app.get("/api/v1/bookshelf", dependencies=protected)
    def shelf(store=Depends(db)):
        return {"data": store.shelf_list(), "meta": {"cached": False, "stale": False}}

    @app.put("/api/v1/bookshelf/{source}/{book_id}", dependencies=protected)
    async def shelf_put(source: str, book_id: Identifier, r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        value, meta = await r.book(book_id)
        return {"data": store.shelf_put(value), "meta": meta}

    @app.delete("/api/v1/bookshelf/{source}/{book_id}", dependencies=protected)
    def shelf_delete(source: str, book_id: Identifier, r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        return {"data": {"removed": store.shelf_delete(source, book_id)}, "meta": {"cached": False, "stale": False}}

    @app.get("/api/v1/progress/{source}/{book_id}", dependencies=protected)
    def progress(source: str, book_id: Identifier, r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        return {"data": store.progress_get(source, book_id), "meta": {"cached": False, "stale": False}}

    @app.put("/api/v1/progress/{source}/{book_id}", dependencies=protected)
    async def progress_put(source: str, book_id: Identifier, body: ProgressInput, r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        await r.validate_chapter(book_id, body.chapter_id)
        return {"data": store.progress_put(source, book_id, body.chapter_id, body.offset), "meta": {"cached": False, "stale": False}}

    def local(data):
        return {"data": data, "meta": {"cached": False, "stale": False}}

    @app.get("/api/v1/books/{source}/{book_id}/bookmarks", dependencies=protected)
    def bookmarks(source: str, book_id: Identifier, offset: Annotated[int, Query(ge=0, le=100000)] = 0, limit: Annotated[int, Query(ge=1, le=200)] = 50, r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        return local(store.bookmarks_list(source, book_id, offset, limit))

    @app.put("/api/v1/books/{source}/{book_id}/bookmarks/{bookmark_id}", dependencies=protected)
    async def bookmark_put(source: str, book_id: Identifier, bookmark_id: Annotated[str, Path(pattern=r"^[0-9a-f]{32}$")], body: BookmarkInput, r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        await r.validate_chapter(book_id, body.chapter_id)
        return local(store.bookmark_put(source, book_id, bookmark_id, body.chapter_id, body.offset, body.note, body.quote))

    @app.delete("/api/v1/books/{source}/{book_id}/bookmarks/{bookmark_id}", dependencies=protected)
    def bookmark_delete(source: str, book_id: Identifier, bookmark_id: Annotated[str, Path(pattern=r"^[0-9a-f]{32}$")], r=Depends(reader), store=Depends(db)):
        r.check_source(source)
        return local({"removed": store.bookmark_delete(source, book_id, bookmark_id)})

    @app.get("/api/v1/history", dependencies=protected)
    def history(offset: Annotated[int, Query(ge=0, le=100000)] = 0, limit: Annotated[int, Query(ge=1, le=200)] = 50, store=Depends(db)):
        return local(store.history_list(offset, limit))

    @app.delete("/api/v1/history", dependencies=destructive)
    def history_clear(store=Depends(db)):
        return local({"removed": store.history_clear()})

    @app.post("/api/v1/bookshelf/{source}/{book_id}/check-updates", dependencies=protected)
    async def check_updates(source: str, book_id: Identifier, r=Depends(reader)):
        return wrap(await r.check_updates(source, book_id))

    @app.get("/api/v1/cache", dependencies=protected)
    def cache_stats(store=Depends(db)):
        return local(store.cache_stats())

    @app.delete("/api/v1/cache", dependencies=destructive)
    def cache_clear(kind: Annotated[str | None, Query(pattern=r"^(search|book|directory|chapter)$")] = None, store=Depends(db)):
        return local({"removed": store.cache_clear(kind)})

    return app
