#!/usr/bin/env bash
# 渲染 393×873 手机稿 → 786×1746 PNG（@2x）
#
# 用法： ./render.sh <绝对路径.html> <输出.png>
# 依赖： Chrome/Chromium（可用 CHROME 环境变量覆盖）、Python3 + Pillow
#
# 为什么按 500 宽渲染再裁：Chrome headless 的最小窗口宽度是 500，
# 直接 --window-size=393 会被撑到 500，导致截图上多出右侧空白。
set -euo pipefail

SRC="${1:?用法: ./render.sh <绝对路径.html> <输出.png>}"
OUT="${2:?用法: ./render.sh <绝对路径.html> <输出.png>}"

if [ -z "${CHROME:-}" ]; then
  for c in \
    "$HOME/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome" \
    "$(command -v chromium || true)" \
    "$(command -v chromium-browser || true)" \
    "$(command -v google-chrome || true)" \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  do
    [ -n "$c" ] && [ -x "$c" ] && { CHROME="$c"; break; }
  done
fi
[ -n "${CHROME:-}" ] || { echo "找不到 Chrome/Chromium，请设置 CHROME 环境变量" >&2; exit 1; }

"$CHROME" --headless --disable-gpu --no-sandbox --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=500,873 \
  --screenshot="$OUT.raw.png" "file://$SRC" 2>/dev/null

python3 - "$OUT.raw.png" "$OUT" <<'PY'
import sys
from PIL import Image
raw, out = sys.argv[1], sys.argv[2]
im = Image.open(raw).convert('RGB')
# @2x 后左上角 786×1746 即 393×873 的实际内容
im.crop((0, 0, 786, 1746)).save(out, optimize=True)
print(out, Image.open(out).size)
PY
rm -f "$OUT.raw.png"
