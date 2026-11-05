"""Bounded HTTP transport: serial pacing, coalescing and finite queue waits."""
import asyncio
import math
import time
from collections.abc import Mapping
from email.utils import parsedate_to_datetime

import httpx
from x_read.models import SourceError


class Transport:
    def __init__(self, client: httpx.AsyncClient | None = None, *, interval: float = 0.5,
                 timeout: float = 15, max_response_bytes: int = 8 * 1024 * 1024,
                 max_queue: int = 32, queue_timeout: float = 30):
        self.client = client or httpx.AsyncClient(follow_redirects=False, headers={
            'User-Agent': 'Mozilla/5.0', 'Referer': 'https://fanqienovel.com/'})
        self._owned = client is None
        self.interval, self.timeout = interval, timeout
        self.max_response_bytes, self.max_queue = max_response_bytes, max_queue
        self.queue_timeout = queue_timeout
        self._lock = asyncio.Lock()
        self._pending: dict[tuple, asyncio.Task] = {}
        self._next_at = 0.0
        self._cooldown = 0.0

    async def aclose(self):
        tasks = list(self._pending.values())
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        if self._owned:
            await self.client.aclose()

    async def get(self, url: str, *, params: Mapping | None = None) -> httpx.Response:
        key = (url, tuple(sorted((str(k), str(v)) for k, v in (params or {}).items())))
        task = self._pending.get(key)
        if task is None:
            if len(self._pending) >= self.max_queue:
                raise SourceError('LOCAL_BUSY', 'Source request queue is full', 503)
            task = asyncio.create_task(self._request(url, params))
            self._pending[key] = task
            def done(finished):
                self._pending.pop(key, None)
                if not finished.cancelled():
                    finished.exception()
            task.add_done_callback(done)
        return await asyncio.shield(task)

    @staticmethod
    def _retry_after(value, default=30):
        try:
            seconds = math.ceil(float(value))
        except (ValueError, TypeError, OverflowError):
            try:
                seconds = math.ceil(parsedate_to_datetime(value).timestamp() - time.time())
            except (ValueError, TypeError, OverflowError, AttributeError):
                return default
        return max(1, seconds)

    async def _request(self, url, params):
        try:
            await asyncio.wait_for(self._lock.acquire(), self.queue_timeout)
        except TimeoutError as exc:
            raise SourceError('LOCAL_BUSY', 'Source request queue timed out', 503) from exc
        try:
            for attempt in range(2):
                remaining = self._cooldown - time.monotonic()
                if remaining > 0:
                    raise SourceError('RATE_LIMITED', 'Upstream rate limit cooldown', 429, math.ceil(remaining))
                wait = self._next_at - time.monotonic()
                if wait > 0:
                    await asyncio.sleep(wait)
                self._next_at = time.monotonic() + self.interval
                try:
                    # Wall-clock timeout also bounds a server trickling bytes forever.
                    async with asyncio.timeout(self.timeout):
                        return await self._exchange(url, params, attempt)
                except _Retry:
                    continue
                except (TimeoutError, httpx.TimeoutException, httpx.NetworkError, httpx.RemoteProtocolError) as exc:
                    if attempt:
                        raise SourceError('UPSTREAM_UNAVAILABLE', 'Upstream connection failed', 502) from exc
                except httpx.HTTPError as exc:
                    raise SourceError('UPSTREAM_INVALID', 'Invalid upstream HTTP response') from exc
        finally:
            self._lock.release()
        raise SourceError('UPSTREAM_UNAVAILABLE', 'Upstream unavailable')

    async def _exchange(self, url, params, attempt):
        async with self.client.stream('GET', url, params=params, timeout=self.timeout) as response:
            if response.status_code == 429:
                delay = self._retry_after(response.headers.get('retry-after'))
                self._cooldown = time.monotonic() + delay
                raise SourceError('RATE_LIMITED', 'Upstream rate limit', 429, delay)
            if response.status_code in (502, 503, 504):
                raw_wait = response.headers.get('retry-after')
                delay = self._retry_after(raw_wait) if raw_wait else 0
                if attempt == 0 and delay <= 2:
                    if delay:
                        await asyncio.sleep(delay)
                    raise _Retry()
                raise SourceError('UPSTREAM_HTTP', 'Upstream server error', 502, delay or None)
            if response.status_code == 404:
                raise SourceError('NOT_FOUND', 'Upstream item not found', 404)
            if response.status_code in (401, 403):
                raise SourceError('UPSTREAM_ACCESS_DENIED', 'Upstream access denied', 403)
            if not 200 <= response.status_code < 300:
                raise SourceError('UPSTREAM_REJECTED', f'Upstream HTTP {response.status_code}', 502)
            length = response.headers.get('content-length')
            if length and length.isdigit() and int(length) > self.max_response_bytes:
                raise SourceError('UPSTREAM_INVALID', 'Response exceeds size limit')
            chunks, size = [], 0
            async for chunk in response.aiter_bytes():
                size += len(chunk)
                if size > self.max_response_bytes:
                    raise SourceError('UPSTREAM_INVALID', 'Response exceeds size limit')
                chunks.append(chunk)
            # aiter_bytes already decompresses the wire stream. Never decode twice.
            headers = {k: v for k, v in response.headers.items()
                       if k.lower() not in ('content-encoding', 'content-length', 'transfer-encoding')}
            return httpx.Response(response.status_code, headers=headers,
                                  content=b''.join(chunks), request=response.request)

    async def json(self, url: str, *, params: Mapping | None = None):
        response = await self.get(url, params=params)
        try:
            return response.json()
        except (ValueError, UnicodeError, RecursionError) as exc:
            raise SourceError('UPSTREAM_INVALID', 'Invalid upstream JSON') from exc

    async def text(self, url: str, *, params: Mapping | None = None) -> str:
        return (await self.get(url, params=params)).text

    get_json = json
    get_text = text


class _Retry(Exception):
    pass
