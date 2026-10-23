package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal enum class NightSleepScoreTab(val label: String) {
    INSIGHTS("Insights"), LONGEVITY("Longevity"), BROWSE("Browse"),
}

/** The Material heart in a 24-unit box, as RN's HEART_PATH. */
internal fun nightSleepScoreHeartPath(): Path = Path().apply {
    moveTo(12f, 21.35f)
    lineTo(10.55f, 20f)
    cubicTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
    cubicTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
    cubicTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
    cubicTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
    cubicTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
    cubicTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20f)
    lineTo(12f, 21.35f)
    close()
}

/** Arc of a circle as a polyline, from/to in turns (0 = 3 o'clock, clockwise), so caps and direction are unambiguous. */
internal fun nightSleepScoreArc(cx: Float, cy: Float, radius: Float, from: Float, to: Float): Path {
    val path = Path()
    val steps = maxOf(2, Math.ceil(abs(to - from) * 360.0 / 1.5).toInt())
    for (i in 0..steps) {
        val a = (from + (to - from) * i / steps) * 2 * PI
        val x = cx + radius * cos(a).toFloat()
        val y = cy + radius * sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

// MARK: - Header rail

@Composable
internal fun NightSleepScoreMetricRail(scale: Float) {
    val R = NightSleepScoreTheme.Rail
    Box(Modifier.width((402 * scale).dp).height((62 * scale).dp).clearAndSetSemantics { }) {
        // Heart tile.
        val H = NightSleepScoreTheme.Rail.Heart
        Box(
            Modifier.offset((H.left * scale).dp, (R.top * scale).dp).size((H.width * scale).dp, (R.height * scale).dp)
                .clip(RoundedCornerShape((R.radius * scale).dp)),
        ) {
            Canvas(Modifier.size((H.width * scale).dp, (R.height * scale).dp)) {
                val k = scale * density
                drawRect(R.tile)
                drawScale(k, k, pivot = Offset.Zero) {
                    val size = H.heartSize
                    translate(H.width / 2 - size / 2, H.heartCentre - size / 2) {
                        drawScale(size / 24f, size / 24f, pivot = Offset.Zero) { drawPath(nightSleepScoreHeartPath(), R.heart) }
                    }
                    H.lines.forEach { (top, width) ->
                        drawRoundRect(R.line, Offset(H.lineLeft, top), Size(width, H.lineHeight), CornerRadius(H.lineHeight))
                    }
                }
            }
        }
        Chip(scale, R.sleep.first, R.sleep.second, "Sleep", "High") { SleepIcon(scale) }
        Chip(scale, R.readiness.first, R.readiness.second, "Readiness", "Ready") { ReadinessIcon(scale) }
        Chip(scale, R.fitness.first, R.fitness.second, "Fitness", "Steady") { FitnessIcon(scale) }
    }
}

@Composable
private fun Chip(scale: Float, left: Float, width: Float, title: String, value: String, icon: @Composable () -> Unit) {
    val R = NightSleepScoreTheme.Rail
    val shape = RoundedCornerShape((R.radius * scale).dp)
    Box(
        Modifier.offset((left * scale).dp, (R.top * scale).dp).size((width * scale).dp, (R.height * scale).dp).clip(shape),
    ) {
        Canvas(Modifier.size((width * scale).dp, (R.height * scale).dp)) {
            val k = scale * density
            drawRoundRect(R.chip, cornerRadius = CornerRadius(R.radius * k))
            drawRoundRect(R.chipBorder, cornerRadius = CornerRadius(R.radius * k), style = Stroke(.67f * density))
            val rim = 1.5f * density
            drawRoundRect(R.chipRim, Offset(rim / 2, rim / 2), Size(size.width - rim, size.height - rim), CornerRadius((R.radius - 1) * k), Stroke(rim))
        }
        Box(Modifier.offset(((R.iconCentre - R.icon / 2) * scale).dp, ((R.height / 2 - R.icon / 2) * scale).dp).size((R.icon * scale).dp)) { icon() }
        NightSleepScoreText(
            title, nightSleepScoreStyle(nightSleepScoreSp(R.textSize, scale), nightSleepScoreSp(R.textLine, scale)),
            Modifier.offset((R.textLeft * scale).dp, (R.titleTop * scale).dp), maxLines = 1,
        ) { R.title }
        NightSleepScoreText(
            value, nightSleepScoreStyle(nightSleepScoreSp(R.valueSize, scale), nightSleepScoreSp(R.textLine, scale), FontWeight.SemiBold),
            Modifier.offset((R.textLeft * scale).dp, (R.valueTop * scale).dp), maxLines = 1,
        ) { R.value }
    }
}

private const val RingR = 16.2f

/** A number drawn in the 40-unit icon box: its baseline y, centred on x = 20. */
@Composable
private fun IconNumber(scale: Float, text: String, baseline: Float, color: Color) {
    val R = NightSleepScoreTheme.Rail
    val unit = R.icon * scale / 40f
    NightSleepScoreText(
        text, nightSleepScoreStyle(nightSleepScoreSp(13f * R.icon / 40f, scale), nightSleepScoreSp(13f * R.icon / 40f * 1.2f, scale), FontWeight.Bold)
            .copy(textAlign = TextAlign.Center),
        Modifier.width((R.icon * scale).dp).offset(0.dp, ((baseline - 13f * .36f - 13f * .6f) * unit).dp),
    ) { color }
}

@Composable
private fun SleepIcon(scale: Float) {
    val R = NightSleepScoreTheme.Rail
    Canvas(Modifier.size((R.icon * scale).dp)) {
        val k = size.width / 40f
        drawScale(k, k, pivot = Offset.Zero) {
            fun seg(from: Float, to: Float, color: Color) =
                drawPath(nightSleepScoreArc(20f, 20f, RingR, from - .25f, to - .25f), color, style = Stroke(7.6f))
            seg(.01f, .19f, NightSleepScoreTheme.orange)
            seg(.22f, .69f, NightSleepScoreTheme.blue)
            seg(.72f, .99f, NightSleepScoreTheme.teal)
        }
    }
    IconNumber(scale, "84", 24.6f, R.value)
}

@Composable
private fun ReadinessIcon(scale: Float) {
    val R = NightSleepScoreTheme.Rail
    Canvas(Modifier.size((R.icon * scale).dp)) {
        val k = size.width / 40f
        drawScale(k, k, pivot = Offset.Zero) {
            val style = Stroke(6.4f, cap = StrokeCap.Round)
            drawPath(nightSleepScoreArc(20f, 21f, RingR, .44f, 1.06f), R.track, style = style)
            drawPath(nightSleepScoreArc(20f, 21f, RingR, .75f, .87f), R.gaugeLight, style = style)
            val a = .875 * 2 * PI
            drawCircle(R.gaugeBlue, 3.3f, Offset(20f + RingR * cos(a).toFloat(), 21f + RingR * sin(a).toFloat()))
        }
    }
    IconNumber(scale, "7", 30f, R.gaugeBlue)
}

@Composable
private fun FitnessIcon(scale: Float) {
    val R = NightSleepScoreTheme.Rail
    Canvas(Modifier.size((R.icon * scale).dp)) {
        val k = size.width / 40f
        drawScale(k, k, pivot = Offset.Zero) {
            drawCircle(R.fitnessPink, 16.6f, Offset(20f, 20f), style = Stroke(6.4f))
            drawPath(nightSleepScoreArc(20f, 20f, 16.6f, -.25f, -.25f + .86f), R.fitnessRed, style = Stroke(6.4f, cap = StrokeCap.Round))
            drawPath(nightSleepScoreArc(20f, 20f, 10.2f, -.25f, -.25f + .8f), R.fitnessGreen, style = Stroke(5.2f, cap = StrokeCap.Round))
            drawCircle(R.fitnessCyan, 4.6f, Offset(20f, 20f))
        }
    }
}

// MARK: - Scroll-edge blur

/** The feed blurs progressively as it reaches the bar (a blurred copy of the feed masked by a gradient). */
@Composable
internal fun NightSleepScoreScrollEdgeBlur(scale: Float, screenHeight: Float, feedLayer: GraphicsLayer, modifier: Modifier = Modifier) {
    val T = NightSleepScoreTheme.Tab
    val height = T.edgeHeight * scale
    val sigma = 10f * scale * LocalDensity.current.density
    val blurred = Build.VERSION.SDK_INT >= 31
    Canvas(
        modifier.fillMaxWidth().height(height.dp).graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            if (blurred) renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp)
        }.clearAndSetSemantics { },
    ) {
        val mask = Brush.verticalGradient(
            0f to Color.Transparent, T.edgeSolidFrom to Color.Black, 1f to Color.Black, startY = 0f, endY = size.height,
        )
        if (blurred) {
            translate(0f, -(screenHeight - height) * density) { drawLayer(feedLayer) }
            drawRect(mask, blendMode = BlendMode.DstIn)
        } else {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, T.edgeSolidFrom to NightSleepScoreTheme.page.copy(alpha = .55f), 1f to NightSleepScoreTheme.page.copy(alpha = .55f)))
        }
    }
}

// MARK: - Liquid Glass tab bar

private val NightSleepScoreEaseOut = CubicBezierEasing(.23f, 1f, .32f, 1f)

@Composable
private fun TabIcon(tab: NightSleepScoreTab, tint: Color, sizeDp: Float) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val k = size.width / 24f
        drawScale(k, k, pivot = Offset.Zero) {
            when (tab) {
                NightSleepScoreTab.INSIGHTS -> drawPath(nightSleepScoreHeartPath(), tint)
                NightSleepScoreTab.LONGEVITY -> {
                    drawCircle(tint, 10.4f, Offset(12f, 12f))
                    for (i in 0 until 8) {
                        val a = i * PI / 4 + PI / 8
                        drawLine(
                            NightSleepScoreTheme.Tab.cardWhite, Offset(12f, 12f),
                            Offset(12f + 11f * cos(a).toFloat(), 12f + 11f * sin(a).toFloat()), 1.7f, StrokeCap.Round,
                        )
                    }
                    drawCircle(NightSleepScoreTheme.Tab.cardWhite, 4f, Offset(12f, 12f))
                }
                NightSleepScoreTab.BROWSE -> for (row in 0..1) for (column in 0..1) {
                    drawRoundRect(tint, Offset(1.5f + column * 11f, 1.5f + row * 11f), Size(10f, 10f), CornerRadius(2.3f))
                }
            }
        }
    }
}

/**
 * The pin's floating tab bar: the feed refracts (blurs) under the capsule, and the selection lens slides to a tapped
 * tab on a spring, stretching as it travels. Still under Reduce Motion. Glass is a blurred copy of the feed on API 31+.
 */
@Composable
internal fun NightSleepScoreGlassTabBar(
    active: NightSleepScoreTab, scale: Float, still: Boolean, screenWidth: Float, screenHeight: Float,
    feedLayer: GraphicsLayer, onSelect: (NightSleepScoreTab) -> Unit, modifier: Modifier = Modifier,
) {
    val T = NightSleepScoreTheme.Tab
    val M = NightSleepScoreMotion.TabMotion
    val density = LocalDensity.current.density
    val barX = (screenWidth - T.width * scale) / 2f
    val barY = screenHeight - (T.bottom + T.height) * scale
    val lensX = remember { Animatable(active.ordinal.toFloat()) }
    val lensStretch = remember { Animatable(1f) }
    LaunchedEffect(active, still) {
        if (still) {
            lensX.snapTo(active.ordinal.toFloat())
            lensStretch.snapTo(1f)
            return@LaunchedEffect
        }
        lensX.animateTo(active.ordinal.toFloat(), spring(M.lensDamping, M.stiffness(M.lensDuration)))
    }
    LaunchedEffect(active, still) {
        if (still) return@LaunchedEffect
        if (lensX.value == active.ordinal.toFloat()) return@LaunchedEffect
        lensStretch.animateTo(M.stretch, tween(M.stretchMs, easing = NightSleepScoreEaseOut))
        lensStretch.animateTo(1f, spring(M.settleDamping, M.stiffness(M.settleDuration)))
    }
    val barShape = RoundedCornerShape((T.radius * scale).dp)
    val blurred = Build.VERSION.SDK_INT >= 31
    val sigma = 12f * scale * density
    Box(
        modifier.width((T.width * scale).dp).height((T.height * scale).dp)
            .shadow((12 * scale).dp, barShape, ambientColor = T.shadow, spotColor = T.shadow)
            .clip(barShape),
    ) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer { if (blurred) renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp) }
                .clearAndSetSemantics { },
        ) {
            translate(-barX * density, -barY * density) { drawLayer(feedLayer) }
        }
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
            drawRect(if (blurred) T.glassTint else T.fallback.copy(alpha = .9f))
            val k = scale * density
            drawRoundRect(T.border, cornerRadius = CornerRadius(T.radius * k), style = Stroke(1f * density))
            drawRoundRect(T.outline, cornerRadius = CornerRadius(T.radius * k), style = Stroke(.67f * density))
        }
        // The selection lens: a capsule of tinted glass with a sheen over its top half and a white rim.
        val lensShape = RoundedCornerShape((T.itemHeight / 2 * scale).dp)
        Box(
            Modifier.offset((T.padding * scale).dp, (T.padding * scale).dp).size((T.item * scale).dp, (T.itemHeight * scale).dp)
                .graphicsLayer {
                    translationX = lensX.value * T.item * scale * density
                    scaleX = lensStretch.value
                }
                .clip(lensShape).clearAndSetSemantics { },
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(if (blurred) T.lensGlassTint else T.lensFallback)
                drawRect(
                    Brush.verticalGradient(0f to T.lensSheen, T.lensSheenTo to Color.Transparent, 1f to Color.Transparent),
                )
                drawRoundRect(T.lensRim, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1f * density))
            }
        }
        Box(Modifier.offset((T.padding * scale).dp, (T.padding * scale).dp)) {
            NightSleepScoreTab.entries.forEach { tab ->
                val selected = tab == active
                val tint = if (selected) T.active else T.ink
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier.offset((tab.ordinal * T.item * scale).dp, 0.dp).size((T.item * scale).dp, (T.itemHeight * scale).dp)
                        .graphicsLayer {
                            val s = if (pressed) M.pressScale else 1f
                            scaleX = s
                            scaleY = s
                        }
                        .clickable(interactionSource = source, indication = null, role = Role.Tab) { onSelect(tab) }
                        .semantics { this.selected = selected; contentDescription = tab.label },
                ) {
                    Box(Modifier.align(Alignment.TopCenter).offset(0.dp, ((T.iconCentre - T.icon / 2) * scale).dp)) {
                        TabIcon(tab, tint, T.icon * scale)
                    }
                    NightSleepScoreText(
                        tab.label,
                        nightSleepScoreStyle(nightSleepScoreSp(T.labelSize, scale), nightSleepScoreSp(T.labelLine, scale), FontWeight.SemiBold)
                            .copy(textAlign = TextAlign.Center),
                        Modifier.fillMaxWidth().offset(0.dp, (T.labelTop * scale).dp), maxLines = 1,
                    ) { tint }
                }
            }
        }
    }
}
