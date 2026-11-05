from fastapi.testclient import TestClient
from x_read.app import create_app
from x_read.config import Settings


def test_public_reader_private_api(tmp_path):
    with TestClient(create_app(Settings(db_path=str(tmp_path/'reader.db'),api_token='private'))) as client:
        for path in ['/', '/reader.html']:
            response=client.get(path)
            assert response.status_code==200
            assert 'text/html' in response.headers['content-type']
            assert 'x-read 小说阅读' in response.text
            assert 'private' not in response.text
            assert 'displayBooks([DEMO_BOOK])' not in response.text
        assert client.get('/api/v1/bookshelf').status_code==401
        assert client.get('/api/v1/bookshelf',headers={'Authorization':'Bearer private'}).status_code==200
        assert client.get('/.env').status_code==404

        # APK 不再随包分发：未配置 X_READ_APK_PATH 时应明确 404 而不是发坏包
        apk_res = client.get('/x-read.apk')
        assert apk_res.status_code == 404
        assert apk_res.json()['error']['code'] == 'NOT_CONFIGURED'
        assert client.get('/manifest.webmanifest').status_code == 200
        assert client.get('/icon-192.png').status_code == 200



def test_native_app_has_no_webview():
    """App 必须为原生 Compose 界面：自家源码不得出现 WebView。"""
    from pathlib import Path
    root = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main"
    hits = []
    for f in (root / "java").rglob("*.kt"):
        text = f.read_text(encoding="utf-8")
        if "WebView" in text or "JavascriptInterface" in text:
            hits.append(str(f.relative_to(root)))
    assert not hits, f"仍有 WebView 依赖：{hits}"
    assert not (root / "assets" / "offline.html").exists(), "不应再内置依赖 WebView 的页面"

    gradle = (root.parent.parent / "build.gradle").read_text(encoding="utf-8")
    assert "androidx.compose" in gradle and "activity-compose" in gradle

    kt = list((root / "java").rglob("*.kt"))
    assert any(f.name == "MainActivity.kt" for f in kt), "缺少 Kotlin 入口"
    lay = root / "java" / "com" / "alanmaster" / "xread"
    for name in ["MainActivity.kt", "data/Api.kt", "data/LibraryViewModel.kt",
                 "data/OfflineRepo.kt", "data/Downloader.kt",
                 "ui/ShelfScreen.kt", "ui/DetailScreen.kt", "ui/ReaderScreen.kt",
                 "ui/ToolsScreen.kt", "ui/SearchScreen.kt", "ui/RankScreen.kt"]:
        assert (lay / name).is_file(), f"缺少 {name}"
