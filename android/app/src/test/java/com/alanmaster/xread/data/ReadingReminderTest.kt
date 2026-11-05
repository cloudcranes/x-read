package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReadingReminderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun repo(): OfflineRepo = OfflineRepo(File(temp.newFolder(), "offline"), temp.newFolder())

    @Test
    fun defaultsAreEveningAndUnmetOnly() {
        val r = ReadingReminder()
        assertEquals(20, r.hour)
        assertEquals(30, r.minute)
        assertFalse(r.enabled)
        assertTrue(r.onlyIfUnmet)
        assertEquals("20:30", r.label())
    }

    @Test
    fun clampsHourAndMinute() {
        val r = ReadingReminder(enabled = true, hour = 31, minute = -5).normalized()
        assertEquals(23, r.hour)
        assertEquals(0, r.minute)
        assertEquals("23:00", r.label())
    }

    @Test
    fun labelPadsSingleDigits() {
        assertEquals("07:05", ReadingReminder.labelOf(7, 5))
        assertEquals("00:00", ReadingReminder.labelOf(0, 0))
    }

    @Test
    fun readsOldSettingsWithoutReminderKey() {
        assertFalse(repo().reminder().enabled)
    }

    @Test
    fun reminderSurvivesRoundTrip() {
        val root = File(temp.newFolder(), "offline")
        val export = temp.newFolder()
        OfflineRepo(root, export)
            .saveReminder(ReadingReminder(enabled = true, hour = 7, minute = 5, onlyIfUnmet = false))
        // 换实例重读，确认真的落盘而不是内存
        val read = OfflineRepo(root, export).reminder()
        assertTrue(read.enabled)
        assertEquals(7, read.hour)
        assertEquals(5, read.minute)
        assertFalse(read.onlyIfUnmet)
    }

    @Test
    fun savingClampsAndDoesNotCrashOnGarbage() {
        val r = repo()
        r.saveReminder(ReadingReminder(enabled = true, hour = 99, minute = 99))
        val read = r.reminder()
        assertEquals(23, read.hour)
        assertEquals(59, read.minute)
        assertEquals(23, read.toJson().optInt("hour"))
    }

    @Test
    fun jsonWithoutFieldsFallsBackToDefaults() {
        val parsed = ReadingReminder.from(JSONObject().put("enabled", true))
        assertTrue(parsed.enabled)
        assertEquals(20, parsed.hour)
        assertEquals(30, parsed.minute)
        assertTrue(parsed.onlyIfUnmet)
    }

    @Test
    fun missingJsonUsesDefaults() {
        assertEquals(ReadingReminder(), ReadingReminder.from(null))
    }
}
