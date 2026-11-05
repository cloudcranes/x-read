"""Explicit, low-frequency online smoke; temporary DB, no retained novel text.

Run: .venv/bin/python scripts/smoke.py
Uses the real API app in-process. It does not start a persistent server.
"""
import json
import os
import re
import tempfile
from pathlib import Path
from fastapi.testclient import TestClient
from x_read.app import create_app
from x_read.config import Settings

env_file = Path(__file__).resolve().parent.parent / ".env"
if env_file.exists():
    for line in env_file.read_text().splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())

BOOK = "7276384138653862966"
CHAPTER = "7276663560427471412"


def main():
    cfg = Settings.from_env()
    with tempfile.TemporaryDirectory(prefix="x-read-smoke-") as root:
        app = create_app(Settings(
            db_path=root + "/smoke.sqlite3",
            lite_url=cfg.lite_url,
            lite_token=cfg.lite_token,
            allow_anonymous=True
        ))
        with TestClient(app) as client:
            checks = [
                ("search", "/api/v1/search", {"q": "我不是戏神"}),
                ("detail", f"/api/v1/books/fanqie/{BOOK}", {}),
                ("directory", f"/api/v1/books/fanqie/{BOOK}/chapters", {"limit": 2}),
                ("chapter", f"/api/v1/books/fanqie/{BOOK}/chapters/{CHAPTER}", {}),
            ]
            cursor = None
            for label, path, params in checks:
                response = client.get(path, params=params)
                body = response.json()
                data = body.get("data", {})
                info = {"check": label, "status": response.status_code, "meta": body.get("meta")}
                if not response.is_success:
                    info["error"] = body.get("error")
                    print(json.dumps(info, ensure_ascii=False), flush=True)
                    raise SystemExit(1)
                if label == "search":
                    assert any(b["book_id"] == BOOK for b in data["items"])
                    info.update(count=len(data["items"]), has_more=data["has_more"])
                    cursor = data["next_cursor"]
                elif label == "detail":
                    assert data["book_id"] == BOOK
                    info.update(title=data["title"], author=data["author"])
                elif label == "directory":
                    assert data["chapters"][0]["chapter_id"] == CHAPTER
                    info.update(total=data["total"], returned=len(data["chapters"]))
                elif label == "chapter":
                    assert data["book_id"] == BOOK and data["chapter_id"] == CHAPTER
                    assert data["content"] and not re.search("[\ue000-\uf8ff]", data["content"])
                    info.update(characters=len(data["content"]), paragraphs=len(data["paragraphs"]), completeness=data["completeness"], pua=False)
                print(json.dumps(info, ensure_ascii=False), flush=True)
            if cursor:
                response = client.get("/api/v1/search", params={"q": "我不是戏神", "cursor": cursor})
                assert response.is_success, response.text
                print(json.dumps({"check": "search_page_2", "status": response.status_code, "count": len(response.json()["data"]["items"])}), flush=True)
            shelf = f"/api/v1/bookshelf/fanqie/{BOOK}"
            progress = f"/api/v1/progress/fanqie/{BOOK}"
            assert client.put(shelf).is_success
            response = client.put(progress, json={"chapter_id": CHAPTER, "offset": 10})
            assert response.is_success, response.text
            assert client.get(progress).json()["data"]["offset"] == 10
            bookmarks = f"/api/v1/books/fanqie/{BOOK}/bookmarks"
            bookmark = bookmarks + "/" + "a" * 32
            assert client.put(bookmark, json={"chapter_id": CHAPTER, "offset": 10, "note": "smoke"}).is_success
            assert client.get(bookmarks).json()["data"]["total"] == 1
            assert client.get("/api/v1/history").json()["data"]["total"] == 1
            update = client.post(shelf + "/check-updates")
            assert update.is_success, update.text
            assert client.get("/api/v1/cache").json()["data"]["entries"] > 0
            assert client.delete(bookmark).is_success
            assert client.delete("/api/v1/history").is_success
            print(json.dumps({"check": "bookmarks_history_updates_cache", "status": "passed", "updated": update.json()["data"]["updated"]}), flush=True)
            assert client.delete(shelf).is_success
            assert client.get(progress).json()["data"]["offset"] == 10
            print(json.dumps({"check": "shelf_progress", "status": "passed"}), flush=True)

            # Rankings verification
            cat_res = client.get("/api/v1/rankings/catalog")
            assert cat_res.is_success, cat_res.text
            cat = cat_res.json()["data"]
            assert len(cat["genders"]) == 2
            rank_res = client.get("/api/v1/rankings", params={"gender": "male", "type": "reading", "page": 1})
            assert rank_res.is_success, rank_res.text
            rank_data = rank_res.json()["data"]
            assert len(rank_data["items"]) == 10
            assert rank_data["items"][0]["rank"] == 1
            assert not re.search("[\ue000-\uf8ff]", rank_data["items"][0]["title"])
            print(json.dumps({"check": "rankings", "status": "passed", "total": rank_data["total"], "first_book": rank_data["items"][0]["title"]}), flush=True)

            # Novel extensions verification (Phase 1)
            hot_res = client.get("/api/v1/search/hot")
            assert hot_res.is_success, hot_res.text
            hot_items = hot_res.json()["data"]
            assert len(hot_items) > 0

            rel_res = client.get(f"/api/v1/books/fanqie/{BOOK}/related")
            assert rel_res.is_success, rel_res.text
            rel_items = rel_res.json()["data"]

            rev_res = client.get(f"/api/v1/books/fanqie/{BOOK}/reviews")
            assert rev_res.is_success, rev_res.text
            rev_data = rev_res.json()["data"]

            ch_rev_res = client.get(f"/api/v1/books/fanqie/{BOOK}/chapters/{CHAPTER}/reviews")
            assert ch_rev_res.is_success, ch_rev_res.text
            ch_rev_data = ch_rev_res.json()["data"]

            pre_res = client.post(f"/api/v1/books/fanqie/{BOOK}/prefetch", json={"chapter_ids": [CHAPTER]})
            assert pre_res.is_success, pre_res.text

            auth_res = client.get("/api/v1/authors/fanqie/4393263683600765")
            assert auth_res.is_success, auth_res.text

            print(json.dumps({
                "check": "novel_extensions",
                "status": "passed",
                "hot_count": len(hot_items),
                "related_count": len(rel_items),
                "reviews_count": len(rev_data.get("items", [])),
                "chapter_reviews_total": ch_rev_data.get("total_reviews", 0),
                "author_name": auth_res.json()["data"]["name"]
            }), flush=True)


if __name__ == "__main__":
    main()
