package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal data class MoodOrbJarEntry(
    val id: Int,
    val mood: Int,
    val tags: String,
    val time: String,
    val fresh: Boolean,
)

internal val MoodOrbJarStartEntries = MoodOrbJarCopy.entries.mapIndexed { index, entry ->
    MoodOrbJarEntry(
        id = -1 - index,
        mood = entry.mood,
        tags = entry.tags,
        time = entry.time,
        fresh = false,
    )
}

private const val VISIBLE_ROWS = 3
private const val ROW_STRIDE = MoodOrbJarLayout.ENTRY_HEIGHT + MoodOrbJarLayout.ENTRY_GAP

private class MoodOrbJarRow(
    entry: MoodOrbJarEntry,
    index: Int,
    animateIn: Boolean,
) {
    val id = entry.id
    var entry by mutableStateOf(entry)
    val y = Animatable((index * ROW_STRIDE).toFloat())
    val alpha = Animatable(if (animateIn) 0f else 1f)
    val enterY = Animatable(if (animateIn) MoodOrbJarTokens.Style.entryRise.toFloat() else 0f)
    var bound by mutableStateOf(false)
    var exiting by mutableStateOf(false)
}

private fun morphColor(progress: Float, from: Color, to: Color): Color {
    val interpolation = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)

    fun blend(start: Float, end: Float): Float {
        val linearStart = start.toDouble().pow(2.2)
        val linearEnd = end.toDouble().pow(2.2)
        val mixed = linearStart + (linearEnd - linearStart) * interpolation
        return (Math.round(mixed.pow(1 / 2.2) * 255.0) / 255.0).toFloat()
    }

    return Color(
        red = blend(from.red, to.red),
        green = blend(from.green, to.green),
        blue = blend(from.blue, to.blue),
        alpha = 1f,
    )
}

@Composable
internal fun MoodOrbJarEntryList(
    entries: List<MoodOrbJarEntry>,
    frame: MoodOrbJarFrame,
    width: Double,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = entries.take(VISIBLE_ROWS)
    val rows = remember { mutableStateListOf<MoodOrbJarRow>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        visible.forEachIndexed { index, entry ->
            val bound = index == 0 && entry.fresh
            val existing = rows.firstOrNull { row -> row.id == entry.id && !row.exiting }
            val row = existing ?: MoodOrbJarRow(
                entry = entry,
                index = index,
                animateIn = !bound,
            ).also { added ->
                rows.add(added)
            }
            row.entry = entry
            row.bound = bound
            if (existing == null && !bound) {
                scope.launch {
                    row.alpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = MoodOrbJarTokens.Style.entryEnterMs,
                            easing = MoodOrbJarEase,
                        ),
                    )
                }
                scope.launch {
                    row.enterY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = MoodOrbJarTokens.Style.entryEnterMs,
                            easing = MoodOrbJarEase,
                        ),
                    )
                }
            } else if (existing != null) {
                scope.launch {
                    row.y.animateTo(
                        targetValue = (index * ROW_STRIDE).toFloat(),
                        animationSpec = tween(
                            durationMillis = MoodOrbJarTokens.Style.entryMoveMs,
                            easing = MoodOrbJarEase,
                        ),
                    )
                }
            }
        }
        for (row in rows.toList()) {
            if (row.exiting || visible.any { entry -> entry.id == row.id }) {
                continue
            }
            row.exiting = true
            scope.launch {
                row.alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = MoodOrbJarTokens.Style.entryExitMs,
                        easing = MoodOrbJarEase,
                    ),
                )
                rows.remove(row)
            }
        }
    }

    val listHeight = if (visible.isEmpty()) {
        0.0
    } else {
        MoodOrbJarLayout.ENTRY_HEIGHT * visible.size + MoodOrbJarLayout.ENTRY_GAP * (visible.size - 1)
    }
    Box(
        modifier
            .width(width.dp)
            .height(listHeight.dp),
    ) {
        for (row in rows) {
            key(row.id) {
                MoodOrbJarEntryRow(
                    row = row,
                    frame = frame,
                    onUndo = onUndo,
                )
            }
        }
    }
}

@Composable
private fun MoodOrbJarEntryRow(
    row: MoodOrbJarRow,
    frame: MoodOrbJarFrame,
    onUndo: () -> Unit,
) {
    val entry = row.entry
    val mood = MoodOrbJarMoods[entry.mood]
    val localDensity = LocalDensity.current
    val dotBorder = with(localDensity) {
        (MoodOrbJarTokens.Style.dotsStroke.toFloat() / density).dp
    }
    val growth = {
        if (row.bound) {
            frame.grow.floatValue
        } else {
            1f
        }
    }
    val description = "${mood.name}, ${entry.tags}, ${entry.time}"
    Box(
        Modifier
            .fillMaxWidth()
            .height(MoodOrbJarLayout.ENTRY_HEIGHT.dp)
            .graphicsLayer {
                translationY = (row.y.value + row.enterY.value) * density
                alpha = row.alpha.value
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (row.bound) {
                        min(1f, -frame.rowX.floatValue / MoodOrbJarTokens.Style.underFull.toFloat())
                    } else {
                        0f
                    }
                }
                .clip(RoundedCornerShape(MoodOrbJarLayout.CARD_RADIUS.dp))
                .background(MoodOrbJarColors.danger)
                .padding(end = MoodOrbJarTokens.Style.undoRight.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = MoodOrbJarCopy.UNDO,
                color = MoodOrbJarColors.dangerInk,
                fontSize = MoodOrbJarTokens.Style.undoSize.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (row.bound) {
                        frame.rowX.floatValue * density
                    } else {
                        0f
                    }
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (entry.fresh) {
                        customActions = listOf(
                            CustomAccessibilityAction(MoodOrbJarCopy.UNDO) {
                                onUndo()
                                true
                            },
                        )
                    }
                }
                .drawBehind {
                    val progress = growth()
                    val eased = 1f - (1f - progress).pow(3)
                    val avatarBox = (MoodOrbJarLayout.ENTRY_ORB * 2 + 32).dp.toPx()
                    val surface = if (row.bound) {
                        morphColor(progress, mood.light, MoodOrbJarColors.card)
                    } else {
                        MoodOrbJarColors.card
                    }
                    drawRoundRect(
                        color = surface,
                        size = Size(
                            width = avatarBox + (size.width - avatarBox) * eased,
                            height = size.height,
                        ),
                        cornerRadius = CornerRadius(MoodOrbJarLayout.CARD_RADIUS.dp.toPx()),
                        alpha = if (row.bound) min(1f, progress * 4) else 1f,
                    )
                },
        ) {
            Image(
                painter = painterResource(mood.art),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset(
                        x = MoodOrbJarSpace.CARD_PAD.dp,
                        y = MoodOrbJarSpace.AVATAR_TOP.dp,
                    )
                    .size((MoodOrbJarLayout.ENTRY_ORB * 2).dp)
                    .graphicsLayer {
                        alpha = if (row.bound && growth() <= 0f) {
                            0f
                        } else {
                            1f
                        }
                    }
                    .clip(CircleShape),
            )
            Column(
                Modifier
                    .offset(
                        x = (
                            MoodOrbJarSpace.CARD_PAD +
                                MoodOrbJarLayout.ENTRY_ORB * 2 +
                                MoodOrbJarTokens.Style.avatarTextGap
                            ).dp,
                        y = MoodOrbJarTokens.Style.entryTextTop.dp,
                    )
                    .graphicsLayer {
                        alpha = contentAlpha(row, growth())
                    },
            ) {
                Text(
                    text = mood.name,
                    color = MoodOrbJarColors.ink,
                    fontSize = MoodOrbJarTokens.Style.entryTitleSize.sp,
                    lineHeight = MoodOrbJarTokens.Style.entryTitleLineHeight.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = MoodOrbJarTokens.Style.entryTitleTracking.sp,
                )
                Text(
                    text = entry.tags,
                    color = MoodOrbJarColors.secondary,
                    fontSize = MoodOrbJarTokens.Style.entryLineSize.sp,
                    lineHeight = MoodOrbJarTokens.Style.entryLineHeight.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MoodOrbJarTokens.Style.entryLineGap.dp),
                )
                Text(
                    text = entry.time,
                    color = MoodOrbJarColors.secondary,
                    fontSize = MoodOrbJarTokens.Style.entryLineSize.sp,
                    lineHeight = MoodOrbJarTokens.Style.entryLineHeight.sp,
                    modifier = Modifier.padding(top = MoodOrbJarTokens.Style.entryLineGap.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = MoodOrbJarTokens.Style.dotsTop.dp,
                        end = MoodOrbJarTokens.Style.dotsRight.dp,
                    )
                    .size(MoodOrbJarTokens.Style.dotsSize.dp)
                    .graphicsLayer {
                        alpha = contentAlpha(row, growth())
                    }
                    .clip(RoundedCornerShape(MoodOrbJarTokens.Style.dotsRadius.dp))
                    .background(Color.White)
                    .border(
                        width = dotBorder,
                        color = MoodOrbJarColors.sheetLine,
                        shape = RoundedCornerShape(MoodOrbJarTokens.Style.dotsRadius.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MoodOrbJarTokens.Style.dotsGap.dp),
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(MoodOrbJarTokens.Style.dotSize.dp)
                                .background(Color(0xFF2A2A2E), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private fun contentAlpha(row: MoodOrbJarRow, growth: Float): Float {
    if (!row.bound) {
        return 1f
    }
    return max(0f, (growth - 0.45f) / 0.55f)
}
