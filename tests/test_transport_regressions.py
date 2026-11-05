"""Integration regressions discovered from live HTTP traffic."""
import gzip
import httpx
import pytest
from x_read.models import SourceError
from x_read.sources.transport import Transport


async def test_gzip_is_decoded_exactly_once():
    raw = b'{"code":0,"data":{"ret_data":[]}}'
    async def handler(request):
        return httpx.Response(200, headers={"Content-Encoding": "gzip"}, content=gzip.compress(raw))
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        transport = Transport(client, interval=0)
        assert (await transport.json("https://example.test/data"))["code"] == 0


async def test_denied_http_is_not_temporary():
    async def handler(request):
        return httpx.Response(403)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(SourceError) as result:
            await Transport(client, interval=0).get("https://example.test/data")
        assert result.value.status == 403
        assert result.value.temporary is False
