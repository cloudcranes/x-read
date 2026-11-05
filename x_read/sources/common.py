"""Strict wire validation and opaque, signed search cursors."""
import base64
import hashlib
import hmac
import json
import re
import secrets
import time
from html.parser import HTMLParser
from x_read.models import Book, SourceError


def invalid(message='Unexpected upstream response'):
    return SourceError('UPSTREAM_INVALID', message)


def identifier(value, *, upstream=False):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]{1,24}', value):
        raise invalid('Invalid upstream identifier') if upstream else SourceError('INVALID_ID', 'Invalid identifier', 400)
    return value


def obj(value):
    if not isinstance(value, dict):
        raise invalid()
    return value


def integer(value):
    if isinstance(value, bool) or value is None:
        return None
    try:
        n = int(value)
        return n if n >= 0 else None
    except (ValueError, TypeError):
        return None


def locked(row):
    return any(row.get(k) in (True, 1, '1', 'true') for k in ('is_locked', 'locked', 'is_paid', 'need_pay'))


def book(row):
    row = obj(row)
    bid = identifier(row.get('book_id'), upstream=True)
    title = row.get('book_name') or row.get('title') or row.get('name')
    if not isinstance(title, str) or not title.strip():
        raise invalid('Missing book title')
    def text(*keys):
        for key in keys:
            if isinstance(row.get(key), (str, int)) and not isinstance(row.get(key), bool):
                return str(row[key])
        return None
    return Book(book_id=bid, title=title, author=text('author', 'author_name'),
                description=text('abstract', 'description'), cover_url=text('thumb_url', 'cover_url'),
                category=text('category', 'category_name'), status=text('book_status', 'creation_status'),
                word_count=integer(row.get('word_number') or row.get('word_count')),
                chapter_count=integer(row.get('chapter_count')), score=text('score'),
                latest_chapter_id=text('last_chapter_item_id'), latest_chapter_title=text('last_chapter_title'))


class Cursor:
    def __init__(self, provider, ttl=900):
        self.provider, self.ttl = provider, ttl
        self.secret = secrets.token_bytes(32)

    def encode(self, query, offset, passback=''):
        raw = json.dumps({'v': 1, 'p': self.provider, 'q': query, 'o': offset,
                          'b': passback, 't': int(time.time())}, separators=(',', ':')).encode()
        return base64.urlsafe_b64encode(raw + hmac.digest(self.secret, raw, 'sha256')).decode().rstrip('=')

    def decode(self, token, query):
        if token is None:
            return 0, ''
        try:
            if not isinstance(token, str) or len(token) > 8192 or not re.fullmatch(r'[A-Za-z0-9_-]+', token):
                raise ValueError
            payload = base64.b64decode(token + '=' * (-len(token) % 4), altchars=b'-_', validate=True)
            raw, sig = payload[:-32], payload[-32:]
            if not hmac.compare_digest(sig, hmac.digest(self.secret, raw, 'sha256')):
                raise ValueError
            data = json.loads(raw)
            if set(data) != {'v', 'p', 'q', 'o', 'b', 't'} or data['v'] != 1 or data['p'] != self.provider or data['q'] != query:
                raise ValueError
            if type(data['o']) is not int or not 0 <= data['o'] <= 1000000:
                raise ValueError
            if type(data['t']) is not int or not 0 <= time.time() - data['t'] <= self.ttl:
                raise ValueError
            if not isinstance(data['b'], str) or len(data['b']) > 4096:
                raise ValueError
            return data['o'], data['b']
        except (ValueError, TypeError, KeyError, UnicodeError):
            raise SourceError('INVALID_CURSOR', 'Invalid or expired search cursor', 400) from None


def query_text(query):
    if not isinstance(query, str) or not query.strip() or len(query) > 200:
        raise SourceError('INVALID_QUERY', 'Invalid search query', 400)
    return query.strip()


class Text(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts, self.hidden = [], 0
    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style'):
            self.hidden += 1
        if tag in ('p', 'br', 'div') and not self.hidden:
            self.parts.append('\n')
    def handle_endtag(self, tag):
        if tag in ('script', 'style') and self.hidden:
            self.hidden -= 1
        if tag in ('p', 'div') and not self.hidden:
            self.parts.append('\n')
    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def paragraphs(content):
    if not isinstance(content, str):
        raise invalid('Missing chapter content')
    parser = Text()
    parser.feed(content)
    return [p.strip() for p in ''.join(parser.parts).splitlines() if p.strip()]


def has_pua(text):
    return any(0xe000 <= ord(c) <= 0xf8ff or 0xf0000 <= ord(c) <= 0xffffd or 0x100000 <= ord(c) <= 0x10fffd for c in text)


def business(payload):
    payload = obj(payload)
    if payload.get('code') not in (0, '0') or payload.get('success') is False:
        raise invalid('Upstream business error')
    return obj(payload.get('data'))
