#!/usr/bin/env bash
# 交付前自检：签名、清单、包内容、原生库页对齐。任何一项不过就非零退出。
# 本地发布（scripts/build_apk.sh）与 GitHub Actions 共用这一份检查，
# 避免「CI 通过但手工发出去的包没过检」这类分叉。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

APK="${1:-$ROOT_DIR/android/app/build/outputs/apk/release/app-release.apk}"
if [ ! -f "$APK" ]; then
  echo "verify_apk: APK not found: $APK" >&2
  exit 1
fi

# ---------- 签名：必须是 release 证书 ----------
# 铁律：release 包绝不接受 debug 签名。旧实现里 keystore 缺失会静默回退 debug，
# 产物照样叫 app-release.apk，没有任何环节拦得住。
APKSIGNER="${APKSIGNER:-}"
if [ -z "$APKSIGNER" ]; then
  for SEL in "${ANDROID_SDK_DIR:-}" "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" /tmp/android-sdk; do
    [ -n "$SEL" ] || continue
    CAND="$(ls "$SEL"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
    if [ -n "$CAND" ]; then APKSIGNER="$CAND"; break; fi
  done
fi
if [ -z "$APKSIGNER" ]; then
  APKSIGNER="$(command -v apksigner || true)"
fi
if [ -z "$APKSIGNER" ]; then
  echo 'REFUSING TO SHIP: apksigner not found (set APKSIGNER or ANDROID_SDK_ROOT)' >&2
  exit 1
fi

CERT="$("$APKSIGNER" verify --print-certs "$APK")"
echo "$CERT" | head -4
case "$CERT" in
  *"CN=Android Debug"*) echo 'REFUSING TO SHIP: APK is debug-signed' >&2; exit 1 ;;
esac

# ---------- 清单：不可 debuggable、不可 allowBackup ----------
# merged manifest 路径随 AGP 版本变化：找不到即视为无法校验，直接失败。
# 旧实现 `if [ -f ]` 没有 else，路径一变整段校验被静默跳过。
MERGED="${MERGED_MANIFEST:-$ROOT_DIR/android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml}"
if [ ! -f "$MERGED" ]; then
  MERGED="$(find "$ROOT_DIR/android/app/build/intermediates/merged_manifests/release" -name AndroidManifest.xml -print -quit 2>/dev/null || true)"
fi
if [ -z "${MERGED:-}" ] || [ ! -f "$MERGED" ]; then
  echo 'REFUSING TO SHIP: merged release manifest not found, cannot verify debuggable/allowBackup' >&2
  echo '（若清单路径已随 AGP 变更，用 MERGED_MANIFEST=<path> 指定）' >&2
  exit 1
fi
if grep -q 'android:debuggable="true"' "$MERGED"; then
  echo 'REFUSING TO SHIP: release manifest is debuggable' >&2
  exit 1
fi
if grep -q 'android:allowBackup="true"' "$MERGED"; then
  echo 'REFUSING TO SHIP: release manifest allows backup' >&2
  exit 1
fi

# ---------- 包内容 ----------
# 注意：不要用 `unzip -l | grep -q`，grep 提前退出会让 unzip 收到 SIGPIPE，
# 在 `set -o pipefail` 下整条管道被判定为失败（曾误报「原生库缺失」）。
ENTRIES="$(unzip -l "$APK")"
case "$ENTRIES" in
  *lib/arm64-v8a/libxread_native.so*) : ;;
  *) echo 'REFUSING TO SHIP: native core is missing from the APK' >&2; exit 1 ;;
esac
case "$ENTRIES" in
  *assets/licenses/LICENSE*) : ;;
  *) echo 'REFUSING TO SHIP: GPL licence text missing from the APK' >&2; exit 1 ;;
esac
case "$ENTRIES" in
  *assets/licenses/NOTICE*) : ;;
  *) echo 'REFUSING TO SHIP: GPL NOTICE missing from the APK' >&2; exit 1 ;;
esac

# ---------- 16KB page size ----------
# Android 15 起要求 .so 的 PT_LOAD p_align >= 0x4000。直接读包内 ELF 程序头，
# 避免把构建参数当成校验保证。
for ABI_DIR in arm64-v8a; do
TMP_SO="$(mktemp -t xread-so.XXXXXX)"
trap 'rm -f "$TMP_SO"' EXIT
unzip -p "$APK" "lib/$ABI_DIR/libxread_native.so" > "$TMP_SO"
python3 - "$TMP_SO" "$ABI_DIR" <<'PY'
import struct, sys
path = sys.argv[1]
data = open(path, 'rb').read()
if data[:4] != b'\x7fELF' or data[4] != 2:
    sys.exit('REFUSING TO SHIP: native core is not an ELF64 object')
(e_phoff,) = struct.unpack_from('<Q', data, 0x20)
(e_phentsize, e_phnum) = struct.unpack_from('<HH', data, 0x36)
aligns = []
for i in range(e_phnum):
    off = e_phoff + i * e_phentsize
    (p_type,) = struct.unpack_from('<I', data, off)
    (p_align,) = struct.unpack_from('<Q', data, off + 48)
    if p_type == 1:
        aligns.append(p_align)
if not aligns:
    sys.exit('REFUSING TO SHIP: native core has no loadable segment')
smallest = min(aligns)
if smallest < 0x4000:
    sys.exit(f'REFUSING TO SHIP: native core p_align=0x{smallest:x} < 0x4000 (16KB page size)')
print(f'native core [{sys.argv[2]}] p_align=0x{smallest:x} (16KB ok)')
PY
rm -f "$TMP_SO"
trap - EXIT
done

echo "verify_apk: OK ($(basename "$APK"), $(stat -c%s "$APK" 2>/dev/null || echo '?') bytes)"
