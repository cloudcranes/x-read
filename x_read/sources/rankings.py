"""Official website ranking contract, verified from the site's rank page/bundles.

gender: male=1, female=0; rankMold: reading=2, new=1.
Never substitute search results or fetch book details to build a ranking.
"""
import json
import re
import time
from datetime import datetime, timezone
from importlib.resources import files
from x_read.models import (
    Book,
    Model,
    RankedBook,
    RankingCatalog,
    RankingCategory,
    RankingGender,
    RankingPage,
    SourceError,
)
from .common import obj, identifier, integer, has_pua, invalid


GENDERS = {'male': 1, 'female': 0}
TYPES = {'reading': 2, 'new': 1}


def decode_text(value, font):
    if value is None:
        return None
    if not isinstance(value, str):
        raise invalid('Invalid ranking text')
    if has_pua(value):
        value = ''.join(font['mapping'].get(str(ord(c)), c) for c in value)
        if has_pua(value):
            raise SourceError('FONT_CHANGED', 'Unmapped ranking font character')
    return value


async def catalog(source):
    # Import here to keep official.py's adapter extension free of cyclic imports.
    from .official import initial_state
    cached = getattr(source, '_ranking_catalog', None)
    if cached and cached[0] > time.monotonic():
        return cached[1]
    state = initial_state(await source.transport.text(source.origin + '/rank'))
    css = obj(state.get('common')).get('css', '')
    font_ids = re.findall(r'font-family\s*:\s*[\"\']?([A-Za-z0-9_-]+)', css) if isinstance(css, str) else []
    if not font_ids or set(font_ids) != {source._font['font']}:
        raise SourceError('FONT_CHANGED', 'Unknown ranking font')
    categories = obj(obj(state.get('rank')).get('rankCategoryTypeList'))
    genders = []
    for key, name in [('male', '男频'), ('female', '女频')]:
        rows = categories.get(key)
        if not isinstance(rows, list) or not rows:
            raise invalid('Missing ranking categories')
        parsed = []
        seen = set()
        for row in rows:
            row = obj(row)
            cid = identifier(row.get('id'), upstream=True)
            title = decode_text(row.get('name'), source._font)
            if cid in seen or not title:
                raise invalid('Invalid ranking category')
            seen.add(cid)
            parsed.append(RankingCategory(id=cid, name=title))
        genders.append(RankingGender(id=key, name=name, categories=parsed))
    result = RankingCatalog(genders=genders, types=[{'id': 'reading', 'name': '阅读榜'}, {'id': 'new', 'name': '新书榜'}], font_id=source._font['font'])
    source._ranking_catalog = (time.monotonic()+600, result)
    return result


async def rankings(source, gender='male', type='reading', category=None, page=1, version=None):
    if gender not in GENDERS or type not in TYPES or isinstance(page, bool) or not isinstance(page, int) or not 1 <= page <= 100:
        raise SourceError('INVALID_ARGUMENT', 'Invalid ranking selection', 400)
    if version is not None and (not isinstance(version, str) or not re.fullmatch(r'[0-9]{1,12}', version)):
        raise SourceError('INVALID_ARGUMENT', 'Invalid ranking version', 400)
    menu = await catalog(source)
    choices = next(g.categories for g in menu.genders if g.id == gender)
    selected = next((c for c in choices if c.id == category), None) if category is not None else choices[0]
    if selected is None:
        raise SourceError('INVALID_ARGUMENT', 'Category is not available for this gender', 400)
    payload = obj(await source.transport.json(source.origin + '/api/rank/category/list', params={
        'app_id': 2503, 'rank_list_type': 3, 'offset': (page-1)*10, 'limit': 10,
        'category_id': selected.id, 'rank_version': version or '', 'gender': GENDERS[gender], 'rankMold': TYPES[type]}))
    if payload.get('code') not in (0, '0'):
        raise invalid('Ranking business error')
    data = obj(payload.get('data'))
    rows, total, current_version = data.get('book_list'), integer(data.get('total_num')), data.get('rankVersion')
    if not isinstance(rows, list) or len(rows) > 10 or total is None or not isinstance(current_version, str) or not re.fullmatch(r'[0-9]{1,12}', current_version):
        raise invalid('Invalid ranking metadata')
    if version and version != current_version:
        raise SourceError('RANK_VERSION_CHANGED', '排行榜版本已更新，请从第一页重新加载', 409)
    try:
        at = datetime.fromtimestamp(int(current_version), timezone.utc).isoformat()
    except (ValueError, OSError, OverflowError) as e:
        raise invalid('Invalid ranking timestamp') from e
    items, seen = [], set()
    for i, row in enumerate(rows):
        row = obj(row)
        bid = identifier(row.get('bookId'), upstream=True)
        rank = integer(row.get('currentPos'))
        if bid in seen or rank != (page-1)*10+i+1:
            raise invalid('Invalid ranking order or duplicate ID')
        seen.add(bid)
        title = decode_text(row.get('bookName'), source._font)
        if not title:
            raise invalid('Missing ranked book title')
        change = row.get('rankPosDiff')
        if change is not None and type_of(change) is not int:
            raise invalid('Invalid ranking change')
        latest = row.get('lastChapterItemId')
        if latest not in (None, '', '0'):
            identifier(latest, upstream=True)
        items.append(RankedBook(book_id=bid, title=title, author=decode_text(row.get('author'), source._font),
            description=decode_text(row.get('abstract'), source._font), cover_url=row.get('thumbUri'),
            category=selected.name, status=str(row['creationStatus']) if row.get('creationStatus') is not None else None,
            word_count=integer(row.get('wordNumber')), latest_chapter_id=latest,
            latest_chapter_title=decode_text(row.get('lastChapterTitle'), source._font),
            rank=rank, rank_change=change, reading_count=integer(row.get('read_count'))))
    more = page*10 < total
    if more and not items:
        raise invalid('Ranking pagination did not advance')
    return RankingPage(gender=gender, type=type, category=selected, items=items, page=page, total=total,
        has_more=more, next_page=page+1 if more else None, version=current_version,
        statistics_at=at, fetched_at=datetime.now(timezone.utc).isoformat())


# The public parameter is named type; keep built-in type for strict field checking.
type_of = type
