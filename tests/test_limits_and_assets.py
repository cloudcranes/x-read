"""请求体上限与静态资源路由的回归测试。"""
from fastapi.testclient import TestClient

from x_read.app import MAX_BODY_BYTES, create_app
from x_read.config import Settings


def client_for(tmp_path, **kwargs):
    return TestClient(create_app(Settings(db_path=str(tmp_path / "limits.db"), **kwargs)))


def test_oversized_body_is_rejected_before_reading(tmp_path):
    with client_for(tmp_path) as client:
        response = client.put("/api/v1/progress/fanqie/7276384138653862966",
                              content=b"x" * (MAX_BODY_BYTES + 1))
        assert response.status_code == 413
        assert response.json()["error"]["message"] == "请求体过大"
        assert response.headers["X-Request-ID"]


def test_oversized_chunked_body_is_rejected(tmp_path):
    def chunks():
        for _ in range(4):
            yield b"y" * (MAX_BODY_BYTES // 2)

    with client_for(tmp_path) as client:
        response = client.put("/api/v1/progress/fanqie/7276384138653862966", content=chunks())
        assert response.status_code == 413


def test_body_at_the_limit_is_not_rejected(tmp_path):
    with client_for(tmp_path) as client:
        response = client.post("/api/v1/fq/search/hot", content=b"x" * MAX_BODY_BYTES)
        assert response.status_code != 413


def test_apk_route_serves_configured_file(tmp_path):
    apk = tmp_path / "x-read.apk"
    payload = b"PK\x03\x04" + b"\x00" * 2048
    apk.write_bytes(payload)
    with client_for(tmp_path, apk_path=str(apk)) as client:
        response = client.get("/x-read.apk")
        assert response.status_code == 200
        assert response.headers["content-type"] == "application/vnd.android.package-archive"
        assert "attachment" in response.headers["content-disposition"]
        assert response.content == payload


def test_static_assets_are_served_with_explicit_media_types(tmp_path):
    with client_for(tmp_path) as client:
        page = client.get("/reader.html")
        assert page.status_code == 200
        assert page.headers["content-type"].startswith("text/html")
        assert page.headers["x-content-type-options"] == "nosniff"
        manifest = client.get("/manifest.webmanifest")
        assert manifest.status_code == 200
        assert manifest.headers["content-type"] == "application/manifest+json"
        icon = client.get("/icon-192.png")
        assert icon.status_code == 200
        assert icon.headers["content-type"] == "image/png"
