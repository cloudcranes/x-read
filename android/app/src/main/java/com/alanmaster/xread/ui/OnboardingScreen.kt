package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.ReaderPrefs
import kotlin.math.roundToInt

/**
 * 首启引导。
 *
 * 三步，一屏一步，随时可跳过：
 *  1. 阅读外观：直接写进 ReaderPrefs，读完第一句就是自己的字号。
 *  2. 恢复数据：老用户换机最常问的是「备份怎么导」，所以入口放在这里而不是设置深处。
 *  3. 完成：只讲三条最容易踩到的操作路径，不再重复功能列表。
 *
 * 只出现一次：结束后把 onboarded 写进 settings.json；本地已有书架/进度时根本不进来。
 */
@Composable
fun OnboardingScreen(
    prefs: ReaderPrefs,
    onPrefs: (ReaderPrefs) -> Unit,
    onImport: () -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    val systemDark = isSystemInDarkTheme()
    val palette = ReaderPalette.of(prefs.theme, systemDark)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Space.gutter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "拾页",
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) {
                    Text("跳过", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> AppearanceStep(prefs = prefs, onPrefs = onPrefs, palette = palette)
                    1 -> RestoreStep(onImport = onImport)
                    else -> ReadyStep(onDone = onDone)
                }
            }

            StepDots(step = step, count = 3)
            PrimaryPill(
                label = if (step < 2) "下一步" else "开始阅读",
                onClick = { if (step < 2) step++ else onDone() },
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/* --------------------------- 第一步：外观 --------------------------- */

@Composable
private fun AppearanceStep(
    prefs: ReaderPrefs,
    onPrefs: (ReaderPrefs) -> Unit,
    palette: ReaderPalette,
) {
    Column(Modifier.fillMaxSize()) {
        StepTitle(index = "01", title = "先把字调舒服", hint = "之后在阅读页右上角随时可改。")

        // 预览卡用阅读页自己的配色与正文排版，所见即所得。
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.lg),
            shape = RoundedCornerShape(Radius.sm),
            color = palette.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(Space.lg)) {
                Text(
                    text = "第一章 · 夜航",
                    fontFamily = FontFamily.Serif,
                    fontSize = (prefs.fontSize + 2f).sp,
                    color = palette.title,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "灯一盏盏熄灭，只剩下舷窗外的黑。她把书翻到折角那一页，" +
                        "听见纸页摩擦的声音比引擎还近。",
                    modifier = Modifier.padding(top = Space.sm),
                    fontFamily = FontFamily.Serif,
                    fontSize = prefs.fontSize.sp,
                    lineHeight = (prefs.fontSize * prefs.lineHeight).sp,
                    color = palette.text,
                )
            }
        }

        SectionLabel("背景")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            ReaderPalette.LABELS.forEach { (key, label) ->
                val swatch = ReaderPalette.of(key, isSystemInDarkTheme())
                val active = key == prefs.theme
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onPrefs(prefs.copy(theme = key)) }
                        .padding(vertical = Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(swatch.background)
                            .border(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(Radius.sm),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("文", fontFamily = FontFamily.Serif, fontSize = 13.sp, color = swatch.text)
                    }
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = Space.xxs),
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionLabel("字号 · ${prefs.fontSize.roundToInt()}")
        Slider(
            value = prefs.fontSize,
            onValueChange = { onPrefs(prefs.copy(fontSize = it)) },
            valueRange = ReaderPrefs.MIN_FONT..ReaderPrefs.MAX_FONT,
            steps = (ReaderPrefs.MAX_FONT - ReaderPrefs.MIN_FONT).toInt() - 1,
        )
    }
}

/* --------------------------- 第二步：恢复 --------------------------- */

@Composable
private fun RestoreStep(onImport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepTitle(index = "02", title = "把原来的数据带回来", hint = "换机或重装后，这里导一次就够。")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.lg),
            shape = RoundedCornerShape(Radius.none),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    text = "备份文件通常在「下载」目录下，名为 x-read-backup.json。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "包含书架、阅读进度、书签与书摘、统计、正文替换规则、追剧记录；不含正文与缓存，需要重新下载。",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SecondaryButton(
            text = "选择备份文件导入",
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md),
        )
        Text(
            text = "没有旧数据就直接下一步，不影响使用。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
    }
}

/* --------------------------- 第三步：完成 --------------------------- */

@Composable
private fun ReadyStep(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepTitle(index = "03", title = "三条最近的路", hint = "剩下的边读边发现就好。")
        listOf(
            "书架点书名直接进正文" to "长按书目可以分组、多选、批量缓存。",
            "工具页是数据的抽屉" to "阅读目标、缓存管理、书摘、备份都在这里。",
            "听书会跟着段落走" to "阅读页右上角开启，锁屏后仍在播。",
        ).forEachIndexed { i, (title, body) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (i == 0) Space.lg else Space.md),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "0${i + 1}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = Space.sm),
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = body,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/* --------------------------- 零件 --------------------------- */

@Composable
private fun StepTitle(index: String, title: String, hint: String) {
    Column(Modifier.padding(top = Space.xxl)) {
        Text(
            text = index,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Space.xs),
        )
        Text(
            text = hint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs),
    )
}

@Composable
private fun StepDots(step: Int, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == step) 7.dp else 5.dp)
                    .background(
                        if (i == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
