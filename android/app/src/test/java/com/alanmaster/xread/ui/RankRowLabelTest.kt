package com.alanmaster.xread.ui

import com.alanmaster.xread.data.BookStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 榜单行的读屏文案。
 *
 * 拼接规则写进 composable 就只能靠真机读屏验证，
 * 而真机验证最容易漏的正是「人数为 0」和「没评分」这两种缺字段的行。
 */
class RankRowLabelTest {
    @Test fun readsRankTitleAndMeta() {
        val label = rankRowLabel(
            rank = 1,
            title = "十日终焉",
            author = "杀虫队队员",
            category = "悬疑脑洞",
            status = BookStatus.Finished,
            score = "9.9",
            readCount = 1_693_000,
            countLabel = "在读",
        )
        assertEquals("第 1 名，十日终焉，杀虫队队员，悬疑脑洞，完本，评分 9.9，169.3 万在读", label)
    }

    @Test fun skipsUnknownStatus() {
        val label = rankRowLabel(
            rank = 7,
            title = "某本书",
            author = "某人",
            category = "",
            status = BookStatus.Unknown,
            score = "",
            readCount = 0,
            countLabel = "在读",
        )
        assertEquals("第 7 名，某本书，某人", label)
    }

    @Test fun skipsMissingCount() {
        val label = rankRowLabel(
            rank = 12,
            title = "听书样本",
            author = "主播",
            category = "有声书",
            status = BookStatus.Serial,
            score = "",
            readCount = 0,
            countLabel = "在听",
        )
        assertEquals("第 12 名，听书样本，主播，有声书，连载", label)
        assertFalse(label.contains("在听"))
    }

    @Test fun usesCountLabelForAudio() {
        val label = rankRowLabel(
            rank = 3,
            title = "有声书",
            author = "主播",
            category = "有声书",
            status = BookStatus.Finished,
            score = "",
            readCount = 12_000,
            countLabel = "在听",
        )
        assertEquals("第 3 名，有声书，主播，有声书，完本，1.2 万在听", label)
    }

    @Test fun blankTitleDoesNotLeaveDoubleComma() {
        val label = rankRowLabel(
            rank = 2,
            title = "",
            author = "",
            category = "",
            status = BookStatus.Unknown,
            score = "",
            readCount = 0,
            countLabel = "在读",
        )
        assertEquals("第 2 名", label)
    }
}
