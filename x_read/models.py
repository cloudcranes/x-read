"""Provider-neutral API contract. All remote identifiers remain strings."""
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Book(Model):
    source: str = "fanqie"
    book_id: str
    title: str
    author: str | None = None
    description: str | None = None
    cover_url: str | None = None
    category: str | None = None
    status: str | None = None
    word_count: int | None = None
    chapter_count: int | None = None
    score: str | None = None
    latest_chapter_id: str | None = None
    latest_chapter_title: str | None = None


class SearchResult(Model):
    items: list[Book]
    next_cursor: str | None = None
    has_more: bool | None = None
    total: int | None = None
    provider: str


class ChapterSummary(Model):
    chapter_id: str
    title: str
    volume: str = ""
    order: int
    word_count: int | None = None
    locked: bool | None = None


class Directory(Model):
    source: str = "fanqie"
    book_id: str
    chapters: list[ChapterSummary]
    total: int
    provider: str


class ChapterContent(Model):
    source: str = "fanqie"
    book_id: str
    chapter_id: str
    title: str
    content: str
    paragraphs: list[str]
    previous_chapter_id: str | None = None
    next_chapter_id: str | None = None
    word_count: int | None = None
    provider: str
    completeness: Literal["website_visible", "unknown", "full"] = "unknown"


class ProgressInput(Model):
    chapter_id: str = Field(pattern=r"^[0-9]{1,24}$")
    offset: int = Field(default=0, ge=0, le=100_000_000, strict=True)


class BookmarkInput(ProgressInput):
    note: str = Field(default="", max_length=5000)
    quote: str = Field(default="", max_length=2000)


class RankingCategory(Model):
    id: str
    name: str


class RankingGender(Model):
    id: str
    name: str
    categories: list[RankingCategory]


class RankingCatalog(Model):
    source: str = "fanqie"
    provider: str = "official_rank"
    genders: list[RankingGender]
    types: list[dict[str, str]]
    page_size: int = 10
    font_id: str


class RankedBook(Book):
    rank: int
    rank_change: int | None = None
    reading_count: int | None = None


class RankingPage(Model):
    source: str = "fanqie"
    provider: str = "official_rank"
    gender: str
    type: str
    category: RankingCategory
    items: list[RankedBook]
    page: int
    page_size: int = 10
    total: int
    has_more: bool
    next_page: int | None
    version: str
    statistics_at: str
    fetched_at: str


class CommentReply(Model):
    reply_id: str
    text: str
    user_name: str
    user_avatar: str | None = None
    digg_count: int = 0
    create_time: int | None = None


class BookReview(Model):
    comment_id: str
    text: str
    user_name: str
    user_avatar: str | None = None
    score: str | None = None
    digg_count: int = 0
    reply_count: int = 0
    create_time: int | None = None
    replies: list[CommentReply] = []


class BookReviewsResult(Model):
    items: list[BookReview]
    total: int
    has_more: bool


class ParagraphReviewStat(Model):
    para_index: str
    count: int
    hot: bool = False


class ChapterReviewsResult(Model):
    item_id: str
    total_reviews: int
    paragraphs: list[ParagraphReviewStat]


class AuthorProfile(Model):
    source: str = "fanqie"
    author_id: str
    name: str
    avatar_url: str | None = None
    description: str | None = None
    fans_count: int | None = None


class AuthorBooksResult(Model):
    author: AuthorProfile
    items: list[Book]
    total: int


class HotSearchItem(Model):
    word: str
    is_hot: bool = False


class PrefetchInput(Model):
    chapter_ids: list[str] = Field(min_length=1, max_length=20)


class PrefetchResult(Model):
    prefetched_count: int
    chapter_ids: list[str]


class SourceError(Exception):
    def __init__(self, code: str, message: str, status: int = 502, retry_after: int | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status
        self.retry_after = retry_after

    @property
    def temporary(self) -> bool:
        return self.code in {"UPSTREAM_UNAVAILABLE", "UPSTREAM_HTTP", "LOCAL_BUSY"} and self.status in {502, 503, 504}
