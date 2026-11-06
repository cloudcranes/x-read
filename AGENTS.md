# 协作规则

## 发版

发一次版本＝四件事一起做完，缺一件都算没发：

1. **版本号**：`android/app/build.gradle` 的 `versionCode` 加一、`versionName` 换成新版本（形如 `4.1.0-standalone`）。同一版本号内可以继续提交修复，对外发布必须换新号。
2. **文档**：`README.md` 的版本行，`docs/ANDROID-STANDALONE.md` 头部版本句 + 对应版本的章节与体积表。
3. **提交并推送**：`git push origin main`。
4. **打 tag 并推送**：`git tag -a v4.1.0 -m "拾页 4.1.0" && git push origin v4.1.0`。tag 名是去掉 `-standalone` 的版本号，必须打在包含上面版本号改动的提交上。

为什么要第 4 步：CI 只在 `v*` tag 上出签名 release 包并生成 `update.json`（`.github/workflows/android.yml:8`、`:117-163`），不推 tag 就没有热更新清单，应用内更新永远看不到新版本。已推送的 tag 不移动、不删除；发错了就升 `versionCode` 打新 tag。

验证：`git ls-remote --tags origin` 能看到新 tag，GitHub Actions 上对应 run 绿，Release 里有 `x-read-standalone.apk` 与 `update.json`。

## 构建

本机（无 Docker）：JDK 在 `/tmp/jdk-21.0.13+9`，SDK 在 `/tmp/android-sdk`，`GRADLE_USER_HOME` 必须指到可写目录（默认 home 不可写）。

```bash
JAVA_HOME=/tmp/jdk-21.0.13+9 ANDROID_HOME=/tmp/android-sdk \
GRADLE_USER_HOME=/vol1/@appshare/Harness/.gradle \
./gradlew --offline :app:testDebugUnitTest :app:compileDebugKotlin
```

签名包走 `scripts/build_apk.sh`（需要 Docker 与 `XREAD_KEYSTORE_PASS`），不在本机跑。
