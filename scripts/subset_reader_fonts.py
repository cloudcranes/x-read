#!/usr/bin/env python3
"""把阅读器字体裁到 GB2312 一级常用字。

仓库里的 reader_kai/serif/sans 原本是完整的 6763 字集，三个合起来压掉
约 5 MB。阅读正文以现代简体小说为主，GB2312 一级字（3755 个）已经覆盖
日常用字；生僻字由 Android 的字体回退链用系统字体渲染，不会出现方块。

用法：
    python3 scripts/subset_reader_fonts.py [源字体目录]

源字体目录默认取 git 历史里的完整字体（运行前先 `git show HEAD~:path`
导出），输出覆盖 android/app/src/main/res/font/ 下的同名文件。
需要 fontTools：`pip install fonttools`。
"""
import os
import sys

from fontTools import subset

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_DIR = os.path.join(ROOT, "android/app/src/main/res/font")
NAMES = ["reader_kai", "reader_serif", "reader_sans"]


def common_chars() -> str:
    chars = set()
    # GB2312 一级汉字：0xB0A1-0xD7FE，覆盖 3755 个常用字。
    for hi in range(0xB0, 0xD8):
        for lo in range(0xA1, 0xFF):
            try:
                chars.add(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                continue
    # 应用自身文案里出现的字（标题、按钮、标点）必须留下，否则设置页会缺字。
    src = os.path.join(ROOT, "android/app/src/main")
    for base, _, files in os.walk(src):
        if os.path.join(os.sep, "font") in base:
            continue
        for name in files:
            if name.endswith((".kt", ".xml")):
                try:
                    with open(os.path.join(base, name), encoding="utf-8", errors="ignore") as f:
                        chars.update(f.read())
                except OSError:
                    pass
    chars.update(chr(i) for i in range(0x20, 0x7F))
    # 常用中文标点与全角符号
    chars.update("　、。〃々〆〇〈〉《》「」『』【】〔〕〖〗〘〙〚〛〜〝〞〟"
                 "〰〾〿–—‘’‛“”„‟…‧﹏.,!?;:'\"()[]{}<>+-=*/\\|@#$%^&_~`")
    return "".join(sorted(c for c in chars if ord(c) >= 0x20))


def main() -> int:
    source_dir = sys.argv[1] if len(sys.argv) > 1 else FONT_DIR
    text = common_chars()
    for name in NAMES:
        src = os.path.join(source_dir, f"{name}.ttf")
        dst = os.path.join(FONT_DIR, f"{name}.ttf")
        if not os.path.isfile(src):
            print(f"missing source font: {src}")
            return 1
        args = [
            src,
            f"--text={text}",
            f"--output-file={dst}",
            "--layout-features=kern,liga",
            "--drop-tables+=DSIG",
            "--no-hinting",
        ]
        subset.main(args)
        print(f"{name}: {os.path.getsize(src)} -> {os.path.getsize(dst)} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
