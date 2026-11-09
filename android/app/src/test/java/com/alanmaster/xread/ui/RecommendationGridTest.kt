package com.alanmaster.xread.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推荐网格的列数与高度。
 *
 * 旧实现列数交给 `GridCells.Adaptive(104.dp)`、高度却按 4 列写死，
 * 手机上实际只排得下 3 列，网格高度少算一行，最后一行被裁掉。
 */
class RecommendationGridTest {
    /** 392.7dp 屏宽减去左右 20dp，是 iQOO 这类 1080p 手机的真实可用宽度。 */
    private val phoneWidth = 352.7.dp

    @Test fun threeColumnsOnAPhone() {
        assertEquals(3, recommendationColumns(phoneWidth))
    }

    @Test fun twoColumnsOnASmallPhone() {
        assertEquals(2, recommendationColumns(280.dp))
    }

    @Test fun fiveColumnsOnATablet() {
        assertEquals(5, recommendationColumns(680.dp))
    }

    @Test fun neverGoesBelowTwoColumns() {
        assertEquals(2, recommendationColumns(120.dp))
    }

    @Test fun rowHeightFollowsCoverRatio() {
        val cardWidth = (phoneWidth - 12.dp * 2) / 3
        val expected = cardWidth / 0.70f + 66.dp
        assertEquals(expected.value, recommendationRowHeight(cardWidth).value, 0.01f)
    }

    @Test fun heightCountsEveryRow() {
        val rowHeight = recommendationRowHeight((phoneWidth - 12.dp * 2) / 3)
        // 7 本书 3 列 → 3 行；旧公式按 4 列算成 2 行。
        val expected = rowHeight * 3 + 20.dp * 2
        assertEquals(expected.value, recommendationGridHeight(7, phoneWidth).value, 0.01f)
    }

    @Test fun emptyGridHasNoHeight() {
        assertEquals(0f, recommendationGridHeight(0, phoneWidth).value, 0.01f)
    }

    @Test fun perfectMultipleOfColumnsDoesNotAddEmptyRow() {
        val rowHeight = recommendationRowHeight((phoneWidth - 12.dp * 2) / 3)
        assertEquals((rowHeight * 2 + 20.dp).value, recommendationGridHeight(6, phoneWidth).value, 0.01f)
    }

    @Test fun everyBookFitsInTheDeclaredHeight() {
        for (count in 1..30) {
            val columns = recommendationColumns(phoneWidth)
            val rows = count / columns + if (count % columns == 0) 0 else 1
            val rowHeight = recommendationRowHeight((phoneWidth - 12.dp * 2) / columns)
            val needed = rowHeight * rows + 20.dp * (rows - 1)
            assertTrue(
                "count=$count rows=$rows",
                recommendationGridHeight(count, phoneWidth) >= needed,
            )
        }
    }

    /**
     * 单个网格块的高度必须留在 Compose Constraints 可编码范围内。
     * 一次性把整条推荐流塞进一个 LazyVerticalGrid.height() 时，
     * 列表涨到上千本会触发 IllegalArgumentException（width=0 + 超大 height）。
     */
    @Test fun chunkHeightStaysWithinConstraintBudget() {
        val chunkHeight = recommendationGridHeight(RecommendationChunkSize, phoneWidth)
        assertTrue(
            "chunk=${RecommendationChunkSize} height=${chunkHeight.value}",
            chunkHeight.value < 6000f,
        )
    }
}
