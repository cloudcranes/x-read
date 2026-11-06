#!/usr/bin/env bash
# Standalone native Android client: embedded Rust/JNI + Kotlin/Compose.
#
# 交付物是 **release** 包：debuggable=false（否则 run-as 可直接读到内容核心的
# pool.key 与解密后的音频缓存）、allowBackup=false、R8 精简。
#
# 顺序要求：**先构建、再自检、最后才发布到 artifacts/**。
# 旧实现先 cp 到交付路径再自检，自检失败时坏包已经躺在 artifacts/x-read-standalone.apk
# 与根目录 x-read.apk 上，CI 照样收走。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_TARGET="${SSH_TARGET:-root@127.0.0.1}"
: "${SSH_PASS:?Set SSH_PASS in the build environment (never embed it in the APK)}"
IMAGE="${BUILD_IMAGE:-registry-1.docker.io/budtmo/docker-android:emulator_14.0}"
APK_REL='android/app/build/outputs/apk/release/app-release.apk'

bash "$ROOT_DIR/scripts/build_native_core.sh"

# release 构建不签 debug 包，因此只要求 release keystore 存在。
# 旧实现检查 debug.keystore，而文档只教建 release.keystore，新机器照文档走必失败。
if [ ! -f "$ROOT_DIR/android/signing/release.keystore" ]; then
  echo 'REFUSING TO BUILD: android/signing/release.keystore is missing (see README 构建).' >&2
  echo 'A release build must never fall back to the debug signing config.' >&2
  exit 1
fi

echo '=== Build standalone Android client (release); no server URL or API token ==='
# 签名口令只经环境变量 + stdin 传入容器：不写源码、不进 argv、不进 APK。
# SSH 口令走 SSHPASS 环境变量（sshpass -e），同样不落 argv。
: "${XREAD_KEYSTORE_PASS:?Set XREAD_KEYSTORE_PASS for the release keystore}"
ALIAS="${XREAD_KEY_ALIAS:-xread}"
export SSHPASS="$SSH_PASS"

# 镜像自带的 /opt/android 只有 build-tools 36.0.0，而 AGP 8.4 要求 34.0.0，
# 缺组件时构建会以「Failed to install the following SDK components」失败。
# 宿主 SDK 若齐全（ANDROID_SDK_DIR，默认 /tmp/android-sdk）就挂进容器覆盖它；
# 没有就沿用镜像自带 SDK，不做安装。
SDK_VOL=""
if [ -d "${ANDROID_SDK_DIR:-/tmp/android-sdk}/build-tools/34.0.0" ]; then
  SDK_VOL=" -v ${ANDROID_SDK_DIR:-/tmp/android-sdk}:/opt/android:ro"
  echo "Using host Android SDK: ${ANDROID_SDK_DIR:-/tmp/android-sdk}"
fi
# 末尾必须带换行：远端 `read -r PW` 在 EOF 时返回非零，配合 `set -e` 会直接中止，
# 表现为 ssh 静默退出 1、一条输出都没有。
printf '%s\n' "$XREAD_KEYSTORE_PASS" | sshpass -e ssh \
  -o StrictHostKeyChecking=accept-new "$SSH_TARGET" "
   set -e
   read -r PW
   export XREAD_KEYSTORE_PASS=\"\$PW\"
   export XREAD_KEY_ALIAS='${ALIAS}'
   export XREAD_KEY_PASS=\"\${XREAD_KEY_PASS:-\$PW}\"
   docker run --rm --user 0 --entrypoint /opt/gradle/bin/gradle \
     -v '${ROOT_DIR}':/workspace \
     -v /tmp/gradle-8.7:/opt/gradle:ro \
     -v /tmp/jdk-21.0.13+9:/opt/jdk21:ro${SDK_VOL} \
     -v xread-gradle-cache:/root/.gradle \
     -w /workspace/android \
     -e JAVA_HOME=/opt/jdk21 -e GRADLE_USER_HOME=/root/.gradle \
     -e ANDROID_SDK_ROOT=/opt/android -e ANDROID_HOME=/opt/android \
     -e XREAD_KEYSTORE_PASS -e XREAD_KEY_ALIAS -e XREAD_KEY_PASS \
     ${IMAGE} --no-daemon :app:testDebugUnitTest :app:assembleRelease
   # 容器以 root 写出的产物在这个卷上是 000，宿主机 cp/校验会 EACCES；
   # 先放开读权限，失败也不影响正确性（包本来就不发）。
   docker run --rm --user 0 --entrypoint sh -v '${ROOT_DIR}':/workspace${SDK_VOL} ${IMAGE} -c '
     set -e
     chmod -R a+rX /workspace/android/app/build
     chmod 644 /workspace/${APK_REL}
   '"

APK="$ROOT_DIR/$APK_REL"
if [ ! -f "$APK" ]; then
  echo "release APK not found at $APK" >&2
  exit 1
fi

# ---------- 自检（全部在拷贝到交付路径之前执行）----------

# 校验逻辑抽到 scripts/verify_apk.sh：本地发布与 GitHub Actions 共用同一份，
# 避免「CI 检过的包」和「手工发的包」标准分叉。
bash "$ROOT_DIR/scripts/verify_apk.sh" "$APK"

# ---------- 自检通过，才发布 ----------
mkdir -p "$ROOT_DIR/artifacts"
TMP_OUT="$ROOT_DIR/artifacts/.x-read-standalone.apk.new"
cp "$APK" "$TMP_OUT"
mv -f "$TMP_OUT" "$ROOT_DIR/artifacts/x-read-standalone.apk"
cp "$ROOT_DIR/artifacts/x-read-standalone.apk" "$ROOT_DIR/x-read.apk"
chmod 644 "$ROOT_DIR/artifacts/x-read-standalone.apk" "$ROOT_DIR/x-read.apk"

echo "APK: artifacts/x-read-standalone.apk (release, no server deployment needed)"
