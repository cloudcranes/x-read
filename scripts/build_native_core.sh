#!/usr/bin/env bash
# 内容核心（fqapi-core + JNI 适配层）交叉编译。
#
# 产出 arm64-v8a 原生核心。x86 模拟器不在此范围内：实测那边即使提供 x86_64
# 原生库，上游注册域名仍解析到 fake-IP 无人接管，请求照样超时。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${XREAD_NATIVE_TOOLS:-/tmp/xread-native-tools}"
export RUSTUP_HOME="${RUSTUP_HOME:-$TOOLS/rustup}"
export CARGO_HOME="${CARGO_HOME:-$TOOLS/cargo}"
export PATH="$CARGO_HOME/bin:$PATH"
NDK="${ANDROID_NDK_HOME:-$TOOLS/android-ndk-r27c}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
# Android 15 起要求 .so 支持 16 KB 页；NDK r27 默认仍是 4 KB 对齐，必须显式指定，
# 否则在 16 KB 页设备上加载直接失败。
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"
export CARGO_BUILD_JOBS="${CARGO_BUILD_JOBS:-4}"
export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$TOOLS/target}"

build_one() {
  local triple="$1" abi="$2"
  local upper lower linker
  upper="$(printf '%s' "$triple" | tr 'a-z-' 'A-Z_')"
  lower="$(printf '%s' "$triple" | tr '-' '_')"
  linker="$BIN/${triple}24-clang"
  if [ ! -x "$linker" ]; then
    echo "REFUSING TO BUILD: missing NDK linker $linker" >&2
    exit 1
  fi
  export "CARGO_TARGET_${upper}_LINKER=$linker"
  export "CC_${lower}=$linker"
  export "AR_${lower}=$BIN/llvm-ar"

  cargo build --features android --manifest-path "$ROOT/android/native-core/Cargo.toml" \
    --release --target "$triple"

  local dest="$ROOT/android/app/src/main/jniLibs/$abi"
  mkdir -p "$dest"
  # 卷上的旧产物是 000，容器里 cp 会 EACCES
  if [ -f "$dest/libxread_native.so" ]; then chmod u+w "$dest/libxread_native.so"; fi
  cp "$CARGO_TARGET_DIR/$triple/release/libxread_native.so" "$dest/"
  chmod 644 "$dest/libxread_native.so"
  echo "Native core ready: $abi"
}

# 只出 arm64：x86 模拟器上补原生库也解决不了上游注册域名不可达的问题，
# 双 ABI 只是把包体从 10MB 撑到 19MB。需要调试模拟器时再加一行 build_one 即可。
build_one aarch64-linux-android arm64-v8a
