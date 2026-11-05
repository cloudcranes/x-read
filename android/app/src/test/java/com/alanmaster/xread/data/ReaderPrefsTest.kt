package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReaderPrefsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun normalizesOutOfRangeValues() {
        val p = ReaderPrefs(fontSize = 99f, lineHeight = 0.1f, theme = "neon").normalized()
        assertEquals(ReaderPrefs.MAX_FONT, p.fontSize, 0.001f)
        assertEquals(ReaderPrefs.MIN_LINE, p.lineHeight, 0.001f)
        assertEquals("paper", p.theme)
    }

    @Test fun keepsValidValuesUntouched() {
        val p = ReaderPrefs(fontSize = 22f, lineHeight = 2.0f, theme = "dark").normalized()
        assertEquals(22f, p.fontSize, 0.001f)
        assertEquals(2.0f, p.lineHeight, 0.001f)
        assertEquals("dark", p.theme)
    }

    @Test fun roundTripsThroughDisk() {
        val root = File(temp.newFolder(), "offline")
        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(repo.saveReaderPrefs(ReaderPrefs(fontSize = 23f, lineHeight = 2.1f,
            theme = "mint", tapTurn = false, volumeTurn = false)))

        val back = OfflineRepo(root, temp.newFolder()).readerPrefs()
        assertEquals(23f, back.fontSize, 0.001f)
        assertEquals(2.1f, back.lineHeight, 0.001f)
        assertEquals("mint", back.theme)
        assertFalse(back.tapTurn)
        assertFalse(back.volumeTurn)
    }


    @Test fun clampsParagraphGap() {
        assertEquals(ReaderPrefs.MAX_GAP, ReaderPrefs(paragraphGap = 9f).normalized().paragraphGap, 0.001f)
        assertEquals(ReaderPrefs.MIN_GAP, ReaderPrefs(paragraphGap = 0f).normalized().paragraphGap, 0.001f)
        assertEquals(0.45f, ReaderPrefs(paragraphGap = 0.45f).normalized().paragraphGap, 0.001f)
    }

    @Test fun keepsIndentAndGapAcrossDisk() {
        val root = File(temp.newFolder(), "offline-gap")
        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(repo.saveReaderPrefs(ReaderPrefs(indent = false, paragraphGap = 1.05f)))

        val back = OfflineRepo(root, temp.newFolder()).readerPrefs()
        assertFalse(back.indent)
        assertEquals(1.05f, back.paragraphGap, 0.001f)
    }

    @Test fun normalizesUnknownFontToDefault() {
        assertEquals("default", ReaderPrefs(font = "comic-sans").normalized().font)
        assertEquals("serif", ReaderPrefs(font = "serif").normalized().font)
    }

    @Test fun migratesLegacyLantingFontToKai() {
        // 旧版「type」= MIUI 私有字体族 mitype，非小米机型上根本不存在；
        // 读回来要换成随包自带的楷体，而不是悄悄退回默认。
        assertEquals("kai", ReaderPrefs(font = "type").normalized().font)
        assertEquals("楷体", ReaderPrefs.FONT_LABELS["kai"])
        assertFalse(ReaderPrefs.FONT_LABELS.containsKey("type"))
    }
    @Test fun acceptsFollowSystemTheme() {
        // auto 必须能过 normalized，否则存盘后会被改回 paper，跟随系统就失效了
        assertEquals("auto", ReaderPrefs(theme = "auto").normalized().theme)
        assertTrue("auto" in ReaderPrefs.THEMES)
        assertTrue(ReaderPrefs.THEME_LABELS.containsKey("auto"))
    }

    @Test fun searchHistoryKeepsNewestDedupedAndCapped() {
        val repo = OfflineRepo(temp.newFolder("offline-h"), temp.newFolder("export-h"))
        assertTrue(repo.searchHistory().isEmpty())
        repo.pushSearch("甲")
        repo.pushSearch("乙")
        // 重复搜索只提位，不产生第二条
        repo.pushSearch("甲")
        assertEquals(listOf("甲", "乙"), repo.searchHistory())
        (1..20).forEach { repo.pushSearch("词$it") }
        assertEquals(12, repo.searchHistory().size)
        assertEquals("词20", repo.searchHistory().first())
        assertTrue(repo.clearSearchHistory())
        assertTrue(repo.searchHistory().isEmpty())
    }

    @Test fun normalizesUnknownTurnModeToSlide() {
        assertEquals("slide", ReaderPrefs(turnMode = "curl").normalized().turnMode)
        assertEquals("simulate", ReaderPrefs(turnMode = "simulate").normalized().turnMode)
        assertTrue("none" in ReaderPrefs.TURN_MODES)
        assertEquals(
            listOf("仿真", "滑动", "无动画"),
            ReaderPrefs.TURN_LABELS.values.toList(),
        )
    }

    @Test fun normalizesUnknownAutoScrollToOff() {
        assertEquals("off", ReaderPrefs(autoScroll = "warp").normalized().autoScroll)
        assertEquals("fast", ReaderPrefs(autoScroll = "fast").normalized().autoScroll)
        // 速度表必须严格递增，否则「慢」档会滚得比「常速」还快
        val speeds = ReaderPrefs.AUTO_LABELS.keys.map { ReaderPrefs.AUTO_SPEEDS.getValue(it) }
        assertEquals(speeds.sorted(), speeds)
        assertEquals(0f, speeds.first(), 0.001f)
        assertEquals(
            listOf("关", "慢", "常速", "快"),
            ReaderPrefs.AUTO_LABELS.values.toList(),
        )
    }

    @Test fun keepsAutoScrollAndKeepAwakeAcrossDisk() {
        val root = File(temp.newFolder(), "offline-scroll")
        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(repo.saveReaderPrefs(ReaderPrefs(autoScroll = "normal", keepAwake = false)))

        val back = OfflineRepo(root, temp.newFolder()).readerPrefs()
        assertEquals("normal", back.autoScroll)
        assertFalse(back.keepAwake)
    }

    @Test fun keepAwakeDefaultsOn() {
        // 默认常亮：新装用户读两页就黑屏是最容易被当成「卡死」的体验
        assertTrue(ReaderPrefs().keepAwake)
        assertEquals("off", ReaderPrefs().autoScroll)
    }

    @Test fun keepsTurnModeAcrossDisk() {
        val root = File(temp.newFolder(), "offline-turn")
        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(repo.saveReaderPrefs(ReaderPrefs(turnMode = "none")))
        assertEquals("none", OfflineRepo(root, temp.newFolder()).readerPrefs().turnMode)
    }

    @Test fun defaultsWhenSettingsMissing() {
        val repo = OfflineRepo(File(temp.newFolder(), "offline"), temp.newFolder())
        assertEquals(ReaderPrefs(), repo.readerPrefs())
    }
}
