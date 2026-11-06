package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 热更新的纯逻辑：清单解析、版本比较、校验和、节流。
 *
 * 这些都是能在 JVM 上真跑的判断，网络与安装动作在 [AppUpdater] 里，不在这里假造。
 */
class AppUpdateTest {

    private val githubRelease = """
        {
          "tag_name": "v3.8.0",
          "name": "x-read 3.8.0",
          "body": "- 支持热更新\n- 修了几处排版",
          "assets": [
            {"name": "x-read-debug.apk", "browser_download_url": "https://example.com/debug.apk", "size": 111},
            {"name": "x-read-standalone.apk", "browser_download_url": "https://example.com/app.apk", "size": 222, "digest": "sha256:ABCDEF"},
            {"name": "update.json", "browser_download_url": "https://example.com/update.json", "size": 33}
          ]
        }
    """.trimIndent()

    @Test
    fun githubReleasePicksStandaloneAsset() {
        val info = AppUpdate.parse(githubRelease)
        assertEquals("3.8.0", info?.versionName)
        assertEquals("https://example.com/app.apk", info?.apkUrl)
        assertEquals(222L, info?.sizeBytes)
        assertEquals("abcdef", info?.sha256)
        assertTrue(info?.notes?.contains("热更新") == true)
    }

    @Test
    fun githubReleaseWithoutApkIsNotInstallable() {
        val json = """{"tag_name":"v3.8.0","assets":[{"name":"notes.txt","browser_download_url":"https://e/x"}]}"""
        assertNull(AppUpdate.parse(json))
    }

    @Test
    fun manifestVariantParsesUrlAndDigest() {
        val info = AppUpdate.parse(
            """{"version":"v3.9.1","versionCode":14,"apkUrl":"https://e/app.apk","notes":"修 bug","size":4096,"sha256":"AA11"}"""
        )
        assertEquals("3.9.1", info?.versionName)
        assertEquals(14, info?.versionCode)
        assertEquals("https://e/app.apk", info?.apkUrl)
        assertEquals("aa11", info?.sha256)
        assertEquals(4096L, info?.sizeBytes)
    }

    @Test
    fun brokenPayloadYieldsNullInsteadOfThrowing() {
        assertNull(AppUpdate.parse("not json at all"))
        assertNull(AppUpdate.parse("{}"))
    }

    @Test
    fun cleanVersionStripsPrefixAndSuffix() {
        assertEquals("3.8.0", AppUpdate.cleanVersion("v3.8.0-standalone"))
        assertEquals("3.8", AppUpdate.cleanVersion("V3.8"))
        assertEquals("", AppUpdate.cleanVersion("latest"))
    }

    @Test
    fun versionComparisonPadsMissingSegments() {
        assertEquals(0, AppUpdate.compareVersions("3.8", "3.8.0"))
        assertEquals(1, AppUpdate.compareVersions("3.10.0", "3.9.9"))
        assertEquals(-1, AppUpdate.compareVersions("3.7.0", "3.7.1"))
    }

    @Test
    fun versionCodeWinsWhenBothSidesHaveOne() {
        val release = ReleaseInfo(versionName = "3.8.0", versionCode = 13, apkUrl = "https://e/a.apk")
        assertTrue(AppUpdate.isNewer(release, "3.8.0", 12))
        assertFalse(AppUpdate.isNewer(release, "9.9.9", 13))
    }

    @Test
    fun fallsBackToVersionNameWithoutVersionCode() {
        val release = ReleaseInfo(versionName = "3.8.0", apkUrl = "https://e/a.apk")
        assertTrue(AppUpdate.isNewer(release, "3.7.0-standalone", 12))
        assertFalse(AppUpdate.isNewer(release, "3.8.0-standalone", 12))
    }

    @Test
    fun standaloneAssetOutranksDebug() {
        assertTrue(AppUpdate.assetScore("x-read-standalone.apk") > AppUpdate.assetScore("x-read-debug.apk"))
        assertTrue(AppUpdate.assetScore("x-read-debug.apk") < 0)
        assertTrue(AppUpdate.assetScore("xread-arm64.apk") > AppUpdate.assetScore("xread.apk"))
    }

    @Test
    fun debugOnlyReleaseIsIgnored() {
        val release = """
            {
              "tag_name": "v3.8.1",
              "assets": [
                {"name": "x-read-debug.apk", "browser_download_url": "https://example.com/debug.apk", "size": 1},
                {"name": "update.json", "browser_download_url": "https://example.com/update.json", "size": 2}
              ]
            }
        """.trimIndent()
        assertNull(AppUpdate.parseGitHub(JSONObject(release)))
    }

    @Test
    fun checkThrottleIsDaily() {
        val now = 1_700_000_000_000L
        assertTrue(AppUpdate.dueForCheck(now, 0L))
        assertFalse(AppUpdate.dueForCheck(now, now - 23 * 60 * 60 * 1000L))
        assertTrue(AppUpdate.dueForCheck(now, now - 24 * 60 * 60 * 1000L))
        // 时钟被回拨时按到期处理，否则会一直不查
        assertTrue(AppUpdate.dueForCheck(now, now + 60_000L))
    }

    @Test
    fun fileNameKeepsOnlySafeCharacters() {
        val release = ReleaseInfo(versionName = "3.8.0", apkUrl = "https://e/a.apk")
        assertEquals("x-read-3.8.0.apk", AppUpdate.fileName(release))
        // 版本号里混进路径分隔符或前导点，都不能拼出目录穿越的名字
        val weird = ReleaseInfo(versionName = "../../etc/3.8", apkUrl = "https://e/a.apk")
        assertEquals("x-read-etc-3.8.apk", AppUpdate.fileName(weird))
    }

    @Test
    fun sha256MatchesKnownDigest() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AppUpdate.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun emptyChecksumDoesNotBlockInstall() {
        assertTrue(AppUpdate.matchesSha256("", "anything"))
        assertTrue(AppUpdate.matchesSha256("sha256:ABC", "abc"))
        assertFalse(AppUpdate.matchesSha256("sha256:abc", "abd"))
    }

    @Test
    fun releaseJsonNotesAreTrimmed() {
        val root = JSONObject("""{"tag_name":"v3.8.0","body":"   ","assets":[{"name":"a.apk","browser_download_url":"https://e/a.apk"}]}""")
        assertEquals("", AppUpdate.parseGitHub(root)?.notes)
    }

    @Test
    fun directMirrorLeavesUrlUntouched() {
        val url = "https://github.com/cloudcranes/x-read/releases/download/v3.8.0/x-read-3.8.0.apk"
        assertEquals(url, AppUpdate.accelerate(url, ""))
        assertEquals(url, AppUpdate.accelerate(url, "not-a-mirror"))
    }

    @Test
    fun mirrorPrefixesTheWholeUrl() {
        val url = "https://github.com/cloudcranes/x-read/releases/download/v3.8.0/x-read-3.8.0.apk"
        assertEquals("https://ghfast.top/$url", AppUpdate.accelerate(url, "ghfast"))
        assertEquals("https://gh-proxy.com/$url", AppUpdate.accelerate(url, "ghproxy"))
    }

    @Test
    fun alreadyAcceleratedUrlIsNotPrefixedTwice() {
        val mirrored = "https://ghfast.top/https://github.com/a/b/releases/download/v1/x.apk"
        assertEquals(mirrored, AppUpdate.accelerate(mirrored, "ghfast"))
    }

    @Test
    fun plainHttpSourcesAreNotAccelerated() {
        // 自建清单常是内网 http，套上 https 反代会直接打不开。
        assertEquals("http://192.168.1.10/update.json", AppUpdate.accelerate("http://192.168.1.10/update.json", "ghfast"))
    }

    @Test
    fun unknownMirrorKeyFallsBackToDirect() {
        assertEquals("", AppUpdate.mirrorOf("gone").key)
        assertEquals("ghfast", AppUpdate.mirrorOf("ghfast").key)
    }
}
