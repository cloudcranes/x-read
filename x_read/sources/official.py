"""Direct official search and website-visible chapter adapter."""
import json
import re
from importlib.resources import files
from x_read.models import ChapterContent, ChapterSummary, Directory, SearchResult, SourceError
from .common import (Cursor, book, business, has_pua, identifier, integer, invalid,
                     locked, obj, paragraphs, query_text)
from .transport import Transport


def initial_state(html):
    match = re.search(r'window\.__INITIAL_STATE__\s*=\s*', html)
    if not match:
        raise invalid('Missing initial page JSON')
    try:
        state, _ = json.JSONDecoder().raw_decode(html[match.end():])
        return obj(state)
    except (ValueError, TypeError, RecursionError) as exc:
        raise invalid('Invalid initial page JSON') from exc


class FanqieSource:
    provider = 'official'
    origin = 'https://fanqienovel.com'
    search_url = 'https://novel.snssdk.com/api/novel/channel/homepage/search/search/v1/'

    def __init__(self, transport: Transport | None = None):
        self.transport = transport or Transport()
        self._cursors = Cursor(self.provider)
        self._website_cursors = Cursor('official_website')
        self._font = json.loads(files('x_read').joinpath('data/font_dnmr.json').read_text(encoding='utf-8'))

    async def search(self, query, cursor=None):
        query = query_text(query)
        try:
            offset, _ = self._cursors.decode(cursor, query)
        except SourceError:
            # The second signature/key is accepted only for our website chain.
            offset, _ = self._website_cursors.decode(cursor, query)
            return await self._website_search(query, offset)
        try:
            return await self._primary_search(query, offset)
        except SourceError as error:
            # Never move an existing cursor to a different pagination protocol.
            if cursor or error.status not in (404, 502, 503, 504):
                raise
            return await self._website_search(query, 0)

    async def _primary_search(self, query, offset):
        data = business(await self.transport.json(self.search_url, params={'aid': '1967', 'q': query, 'offset': offset}))
        rows = data.get('ret_data')
        if not isinstance(rows, list):
            raise invalid('Missing official search results')
        items = [book(row) for row in rows]
        more = data.get('has_more') in (True, 1, '1')
        next_offset = integer(data.get('offset'))
        if more and (next_offset is None or next_offset <= offset):
            raise invalid('Invalid next search offset')
        return SearchResult(items=items, provider=self.provider, has_more=more,
            total=integer(data.get('total')), next_cursor=self._cursors.encode(query, next_offset) if more else None)

    async def _website_search(self, query, offset):
        data = business(await self.transport.json(self.origin + '/api/author/search/search_book/v1', params={
            'query_word': query, 'page_count': 10, 'page_index': offset // 10,
            'query_type': 0, 'filter': ''}))
        rows = data.get('search_book_data_list')
        if not isinstance(rows, list):
            raise invalid('Missing website search results')
        items = [book(row) for row in rows]
        total = integer(data.get('total_count'))
        more = data.get('has_more') in (True, 1, '1') if 'has_more' in data else (offset + len(items) < total if total is not None else None)
        if more and not items:
            raise invalid('Website search returned no advancing results')
        return SearchResult(items=items, provider='official_website', total=total, has_more=more,
            next_cursor=self._website_cursors.encode(query, offset + 10) if more else None)

    async def get_book(self, book_id):
        identifier(book_id)
        state = initial_state(await self.transport.text(self.origin + '/page/' + book_id))
        page = obj(state.get('page'))
        if page.get('bookId') != book_id:
            raise invalid('Book identity mismatch')
        mapped = {'book_id': page.get('bookId'), 'book_name': page.get('bookName'),
            'author': page.get('authorName') or page.get('author'), 'abstract': page.get('abstract'),
            'thumb_url': page.get('thumbUrl') or page.get('thumbUri'), 'word_number': page.get('wordNumber'),
            'creation_status': page.get('creationStatus'), 'chapter_count': page.get('chapterTotal'),
            'last_chapter_item_id': page.get('lastChapterItemId'), 'last_chapter_title': page.get('lastChapterTitle'),
            'category': page.get('category'), 'score': page.get('score')}
        return book(mapped)

    async def get_chapters(self, book_id):
        identifier(book_id)
        data = business(await self.transport.json(self.origin + '/api/reader/directory/detail', params={'bookId': book_id}))
        if data.get('bookId') is not None and data['bookId'] != book_id:
            raise invalid('Directory book mismatch')
        volumes = data.get('chapterListWithVolume')
        names = data.get('volumeNameList', [])
        if not isinstance(volumes, list) or not isinstance(names, list):
            raise invalid('Invalid directory volumes')
        chapters, seen = [], set()
        for vi, volume in enumerate(volumes):
            if not isinstance(volume, list):
                raise invalid('Invalid volume')
            for row in volume:
                row = obj(row)
                cid = identifier(row.get('itemId'), upstream=True)
                if cid in seen or (row.get('bookId') is not None and row['bookId'] != book_id):
                    raise invalid('Invalid chapter ownership or duplicate ID')
                seen.add(cid)
                title = row.get('title')
                if not isinstance(title, str):
                    raise invalid('Missing chapter title')
                lock_keys = ('isChapterLock', 'needPay', 'locked', 'is_locked')
                is_locked = any(row.get(k) in (True, 1, '1') for k in lock_keys)
                chapters.append(ChapterSummary(chapter_id=cid, title=title, order=len(chapters),
                    volume=str(names[vi]) if vi < len(names) else '', word_count=integer(row.get('wordNumber')),
                    locked=is_locked if any(k in row for k in lock_keys) else None))
        return Directory(book_id=book_id, chapters=chapters, total=len(chapters), provider=self.provider)

    async def get_chapter(self, book_id, chapter_id):
        identifier(book_id); identifier(chapter_id)
        state = initial_state(await self.transport.text(self.origin + '/reader/' + chapter_id))
        chapter = obj(obj(state.get('reader')).get('chapterData'))
        if chapter.get('bookId') != book_id or chapter.get('itemId') != chapter_id:
            raise invalid('Chapter identity or ownership mismatch')
        if any(chapter.get(k) in (True, 1, '1') for k in ('isChapterLock', 'needPay')) or locked(chapter):
            raise SourceError('CHAPTER_LOCKED', '该章节为番茄官方锁定章节（网页端仅前10章开放试读）', 403)
        title = chapter.get('title')
        if not isinstance(title, str):
            raise invalid('Missing chapter title')
        parts = paragraphs(chapter.get('content'))
        text = '\n'.join(parts)
        if not text:
            raise invalid('Empty website chapter')
        if has_pua(text):
            css = obj(state.get('common', {})).get('css', '')
            font = re.search(r'font-family\s*:\s*[\"\']?([A-Za-z0-9_-]+)', css) if isinstance(css, str) else None
            if not font or font.group(1) != self._font['font']:
                raise SourceError('FONT_CHANGED', 'Unknown website font')
            mapping = self._font['mapping']
            text = ''.join(mapping.get(str(ord(c)), c) for c in text)
            if has_pua(text):
                raise SourceError('FONT_CHANGED', 'Unmapped website font character')
            parts = text.splitlines()
        def adjacent(key):
            value = chapter.get(key)
            if value in (None, '', '0', 0):
                return None
            return identifier(value, upstream=True)
        return ChapterContent(book_id=book_id, chapter_id=chapter_id, title=title, content=text,
            paragraphs=parts, provider=self.provider, completeness='website_visible',
            previous_chapter_id=adjacent('preItemId'), next_chapter_id=adjacent('nextItemId'),
            word_count=integer(chapter.get('chapterWordNumber')))

    async def ranking_catalog(self):
        from .rankings import catalog
        return await catalog(self)

    async def rankings(self, gender='male', type='reading', category=None, page=1, version=None):
        from .rankings import rankings
        return await rankings(self, gender, type, category, page, version)

    async def aclose(self):
        await self.transport.aclose()
