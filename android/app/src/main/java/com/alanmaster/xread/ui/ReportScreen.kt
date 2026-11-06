package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.GoalMath
import com.alanmaster.xread.data.ReadingGoal
import com.alanmaster.xread.data.ReadingReview
import com.alanmaster.xread.data.ReportDay
import com.alanmaster.xread.data.ReportMonth
import com.alanmaster.xread.data.ReportPeriod
import com.alanmaster.xread.data.ReportSummary
import org.json.JSONObject
import java.util.Locale

/**
 * 阅读报告：把 `stats.days` 按月/年摊开，日历热力图 + 关键数字 + 分享卡入口。
 *
 * 为什么单独一页：工具页的「回顾」只有三个数字，跨月的感受接不住。
 * 这里能回答的是「这个月哪几天读了」「有没有断」「比上个月多还是少」。
 *
 * 所有聚合都走 [ReadingReview]，页面只负责画——闰年、月长、跨年这些
 * 边界在 `ReadingReviewTest` 里钉死，不在这里重算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    stats: JSONObject,
    goal: ReadingGoal = ReadingGoal(),
    onBack: () -> Unit,
    onShare: (ReportSummary, List<Pair<String, Long>>) -> Unit = { _, _ -> },
) {
    val days = remember(stats) { stats.optJSONObject("days") }
    val today = remember { ReadingReview.currentPeriod() }
    var yearly by remember { mutableStateOf(false) }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.month ?: 1) }

    val period = ReportPeriod(year, if (yearly) null else month)
    val summary = remember(days, period, goal) { ReadingReview.summarize(days, period, goal) }
    val previous = remember(days, period, goal) {
        ReadingReview.summarize(days, period.previous(), goal)
    }
    val monthGrid = remember(days, period) {
        if (yearly) emptyList() else ReadingReview.monthGrid(days, period.year, month)
    }
    val yearBars = remember(days, period) {
        if (yearly) ReadingReview.yearMonths(days, period.year) else emptyList()
    }
    val shareBars: List<Pair<String, Long>> = remember(monthGrid, yearBars, yearly) {
        if (yearly) {
            yearBars.map { it.month.toString() to it.seconds }
        } else {
            monthGrid.filter { it.inPeriod }.map { "${it.dayOfMonth}" to it.seconds }
        }
    }
    val canGoNext = if (period.yearly) period.year < today.year
    else period.year < today.year || (period.year == today.year && month < (today.month ?: 1))

    Scaffold(
        topBar = {
            XReadTopBar(
                title = "阅读报告",
                subtitle = period.longLabel,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onShare(summary, shareBars) }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "分享阅读报告",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { PeriodSwitcher(period, yearly, canGoNext, onYearly = { yearly = it }, onShift = { step -> shift(year, month, yearly, step)?.let { (y, m) -> year = y; month = m } }) }
            item { HeroCard(summary, previous) }
            item { CalendarCard(monthGrid, yearBars, yearly, period) }
            item { InsightsCard(summary, previous) }
            item {
                SecondaryButton(
                    text = if (summary.totalSeconds > 0) "生成分享卡" else "这个月还没有可以分享的记录",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter),
                    enabled = summary.totalSeconds > 0,
                    onClick = { onShare(summary, shareBars) },
                )
            }
        }
    }
}

/** 上一个/下一个周期。到底了返回 null，按钮据此变灰。 */
private fun shift(year: Int, month: Int, yearly: Boolean, step: Int): Pair<Int, Int>? {
    if (yearly) return (year + step) to month
    val base = java.util.Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1)
    }
    base.add(java.util.Calendar.MONTH, step)
    return base.get(java.util.Calendar.YEAR) to (base.get(java.util.Calendar.MONTH) + 1)
}

@Composable
private fun PeriodSwitcher(
    period: ReportPeriod,
    yearly: Boolean,
    canGoNext: Boolean,
    onYearly: (Boolean) -> Unit,
    onShift: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onShift(-1) },
            modifier = Modifier.semantics { contentDescription = "上一个周期" },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = period.longLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
        IconButton(
            onClick = { if (canGoNext) onShift(1) },
            enabled = canGoNext,
            modifier = Modifier.semantics { contentDescription = "下一个周期" },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (canGoNext) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        listOf(false to "按月", true to "按年").forEach { (y, label) ->
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (yearly == y) FontWeight.SemiBold else FontWeight.Normal,
                color = if (yearly == y) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Space.md)
                    .clickable { onYearly(y) },
            )
        }
    }
}

@Composable
private fun ReportCardShell(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            content()
        }
    }
}

@Composable
private fun HeroCard(summary: ReportSummary, previous: ReportSummary) {
    ReportCardShell {
        Row(verticalAlignment = Alignment.Bottom) {
            val (value, unit) = when {
                summary.totalSeconds >= 3600 ->
                    String.format(Locale.US, "%.1f", summary.hours) to "小时"
                summary.totalSeconds > 0 -> "${summary.totalSeconds / 60}" to "分钟"
                else -> "0" to "分钟"
            }
            Text(
                text = value,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                fontSize = 13.sp,
                color = ink3,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.sm),
            )
        }
        Text(
            text = if (summary.activeDays == 0) {
                "${summary.period.longLabel}还没有读完的十分钟。"
            } else {
                "在 ${summary.activeDays} 天里翻开过书，平均每天 ${summary.averageMinutesPerActiveDay} 分钟。"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.totalSeconds > 0) {
            val delta = summary.totalSeconds - previous.totalSeconds
            val text = when {
                previous.totalSeconds == 0L -> "上一次统计期没有记录。"
                delta == 0L -> "和上一个周期一样多。"
                delta > 0 -> "比上一个周期多 ${formatSpan(delta)}。"
                else -> "比上一个周期少 ${formatSpan(-delta)}。"
            }
            Text(
                text = text,
                fontSize = 11.sp,
                color = if (delta > 0) MaterialTheme.colorScheme.primary else ink3,
            )
        }
    }
}

private fun formatSpan(seconds: Long): String = when {
    seconds >= 3600 -> String.format(Locale.US, "%.1f", seconds / 3600.0) + " 小时"
    else -> "${seconds / 60} 分钟"
}

@Composable
private fun CalendarCard(
    monthGrid: List<ReportDay>,
    yearBars: List<ReportMonth>,
    yearly: Boolean,
    period: ReportPeriod,
) {
    val max = if (yearly) {
        yearBars.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
    } else {
        monthGrid.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
    }
    ReportCardShell {
        SectionHeader(
            title = if (yearly) "${period.year} 年的形状" else "${period.month ?: 1} 月的形状",
            hint = if (yearly) "每月累计" else "每天累计",
        )
        if (yearly) {
            YearBars(yearBars, max)
        } else {
            WeekdayHeader()
            MonthHeatmap(monthGrid, max)
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                text = label,
                fontSize = 10.sp,
                color = ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthHeatmap(grid: List<ReportDay>, max: Long) {
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    grid.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
            week.forEach { day ->
                if (!day.inPeriod) {
                    Box(Modifier.weight(1f).aspectRatio(1f))
                } else {
                    val ratio = (day.seconds.toFloat() / max).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (day.seconds <= 0) empty else accent.copy(alpha = 0.18f + 0.82f * ratio))
                            .semantics {
                                contentDescription = "${day.dayOfMonth} 日，" +
                                    if (day.seconds <= 0) "没有阅读" else "${day.seconds / 60} 分钟"
                            },
                    )
                }
            }
            // 最后一周不满 7 天时补位，保持格子大小一致
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun YearBars(months: List<ReportMonth>, max: Long) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { m ->
            val ratio = (m.seconds.toFloat() / max).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .semantics {
                            contentDescription = "${m.month} 月，" +
                                if (m.seconds <= 0) "没有阅读" else "${m.seconds / 60} 分钟"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(track)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((96 * ratio).coerceAtLeast(if (m.seconds > 0) 4f else 0f).dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(accent)
                    )
                }
                Text(text = m.label, fontSize = 9.sp, color = ink3)
            }
        }
    }
}

@Composable
private fun InsightsCard(summary: ReportSummary, previous: ReportSummary) {
    val goalOn = summary.metDays > 0
    ReportCardShell {
        SectionHeader(title = "这一段时间", hint = "")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetaStat(
                value = "${summary.longestStreak}",
                label = "最长连续天",
                modifier = Modifier.weight(1f),
                emphasized = summary.longestStreak > 1,
            )
            MetaStatDivider()
            MetaStat(
                value = if (goalOn) "${summary.metDays}" else "—",
                label = if (goalOn) "达标天数" else "未开目标",
                modifier = Modifier.weight(1f),
            )
            MetaStatDivider()
            MetaStat(
                value = if (summary.bestDaySeconds > 0) "${summary.bestDaySeconds / 60}" else "—",
                label = "最长一天/分",
                modifier = Modifier.weight(1f),
            )
        }
        if (summary.bestDayKey != null) {
            Text(
                text = "读得最多的一天是 ${summary.bestDayKey}，${summary.bestDaySeconds / 60} 分钟。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (previous.totalSeconds > 0 && summary.activeDays > 0) {
            Text(
                text = "上一个周期读了 ${previous.activeDays} 天。",
                fontSize = 11.sp,
                color = ink3,
            )
        }
    }
}
