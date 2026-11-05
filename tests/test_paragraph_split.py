"""段落切分对拍：同一份 fixture 同时被 Kotlin 的 ParagraphsTest 使用。"""
import json
from pathlib import Path

import pytest

from x_read.sources.common import paragraphs

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "paragraphs.json"


def cases():
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", cases(), ids=lambda c: c["name"])
def test_paragraph_split_matches_fixture(case):
    assert paragraphs(case["text"]) == case["out"]


def test_entities_are_decoded_before_splitting():
    """实体解码属于 HTML 阶段（Python convert_charrefs / Android Html.fromHtml），
    不进共享 fixture：Kotlin 的 Paragraphs.split 只做纯文本切分。"""
    assert paragraphs("a&amp;b\nc") == ["a&b", "c"]
    assert paragraphs("<p>甲</p><p>乙</p>") == ["甲", "乙"]
