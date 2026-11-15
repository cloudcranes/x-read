package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.DanmakuItem

/**
 * 弹幕流层 位置由外部逐帧时钟驱动，单条弹幕只在 graphicsLayer 内做位移，
 * 避免每 200–250ms 整层重组造成的肉眼掉帧。
 */
@Composable
internal fun DanmakuFlowLayer(
    positionMs: State<Long>,
    items: List<DanmakuItem>,
    modifier: Modifier = Modifier,
    windowMs: Long = 4000L,
    leadMs: Long = 0L,
    laneCount: Int = 3,
    topInset: Dp = 16.dp,
    laneGap: Dp = 26.dp,
    maxItems: Int = Int.MAX_VALUE,
    fontSize: TextUnit = 12.sp,
    travelFactor: Float = 1.3f,
) {
    val activeDanmaku by remember(items, windowMs, leadMs, maxItems) {
        derivedStateOf {
            val now = positionMs.value
            items.asSequence()
                .filter {
                    val start = it.offsetMs - leadMs
                    now >= start && now <= start + windowMs
                }
                .take(maxItems)
                .toList()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        activeDanmaku.forEach { item ->
            val lane = (item.id.hashCode() and 0x7fffffff) % laneCount
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .offset(y = topInset + laneGap * lane)
                    .graphicsLayer {
                        val start = item.offsetMs - leadMs
                        val progress = ((positionMs.value - start).toFloat() / windowMs)
                            .coerceIn(0f, 1f)
                        translationX = widthPx * (1f - progress * travelFactor)
                    }
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
