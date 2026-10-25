package com.ngocdevv.nativecook.blocks.gestures.abacusexpensecounter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Rolling amounts, meters and the week strip for the abacus screen. An amount is a row of digit
 wheels (0–9 and a second 0), each rolled by a spring when its digit changes, forward when the
 amount grows and backward when it shrinks, the lower wheels first (25 ms apart). Leading zeros
 fade out and the "$" slides in after them. The values are read in snapshotFlows and the motion
 in graphicsLayer, so nothing recomposes while beads move.
 */

private val Digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
/** Reanimated's { duration: 520, dampingRatio: 0.78 }, as a stiffness. */
private val Roll = spring<Float>(dampingRatio = 0.78f, stiffness = 160f)
private val AbacusInk = Color(0xFFF4ECE0)
private val AbacusInkSoft = Color(0xFFAD9E8E)

private fun digitAt(cents: Int, place: Int) = (max(0, cents) / 10.0.pow(place).toInt()) % 10

@Composable
private fun DigitWheel(cents: () -> Int, initial: Int, place: Int, order: Int, line: Int, width: Int, style: TextStyle, reduced: Boolean) {
    val pos = remember { Animatable(digitAt(initial, place).toFloat()) }
    val shown = remember { Animatable(if (place <= 2 || initial >= 10.0.pow(place).toInt()) 1f else 0f) }
    LaunchedEffect(Unit) {
        var last = initial
        var job: Job? = null
        snapshotFlow { cents() }.collect { v ->
            // Leading zeros fade (never the units of dollars).
            if (place > 2) {
                val visible = if (v >= 10.0.pow(place).toInt()) 1f else 0f
                if (shown.targetValue != visible) launch { if (reduced) shown.snapTo(visible) else shown.animateTo(visible, tween(200)) }
            }
            val before = last
            if (v == before) return@collect
            last = v
            val from = ((pos.value.roundToInt() % 10) + 10) % 10
            val to = digitAt(v, place)
            if (from == to) return@collect
            val delta = if (v > before) (to - from + 10) % 10 else -((from - to + 10) % 10)
            val target = (pos.value.roundToInt() + delta).toFloat()
            job?.cancel()
            job = launch {
                if (reduced) {
                    pos.snapTo(target)
                } else {
                    delay(order * 25L)
                    pos.animateTo(target, Roll)
                }
            }
        }
    }
    Box(
        Modifier
            .size(width.dp, line.dp)
            .graphicsLayer { alpha = if (place <= 2) 1f else shown.value }
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val p = ((pos.value % 10) + 10) % 10
                    translationY = -p * line * density
                },
        ) {
            for (d in Digits) Text(d, style = style, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.size(width.dp, line.dp).wrapContentHeight())
        }
    }
}

/** An amount in cents as rolling digit wheels: "$", the dollars, ".", the cents. */
@Composable
internal fun AbacusRollingAmount(cents: () -> Int, initial: Int, size: Float, color: Color, weight: FontWeight = FontWeight.Bold, intDigits: Int = 3, reduced: Boolean = false) {
    val line = (size * 1.18f).roundToInt()
    val width = (size * 0.6f).roundToInt()
    val style = TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFeatureSettings = "tnum",
        lineHeight = line.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    fun hiddenOf(v: Int): Int {
        var hidden = 0
        for (p in intDigits + 1 downTo 3) if (v < 10.0.pow(p).toInt()) hidden++
        return hidden
    }
    // The "$" moves right by one wheel for every leading zero that is hidden.
    val dollar = remember { Animatable(hiddenOf(initial) * width.toFloat()) }
    LaunchedEffect(Unit) {
        snapshotFlow { hiddenOf(cents()) * width.toFloat() }.collect { x ->
            launch { if (reduced) dollar.snapTo(x) else dollar.animateTo(x, tween(220)) }
        }
    }
    Row(verticalAlignment = Alignment.Top) {
        Text("$", style = style, maxLines = 1, modifier = Modifier.height(line.dp).wrapContentHeight().graphicsLayer { translationX = dollar.value * density })
        for (p in intDigits + 1 downTo 2) DigitWheel(cents, initial, p, p, line, width, style, reduced)
        Text(".", style = style, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.size((size * 0.3f).roundToInt().dp, line.dp).wrapContentHeight())
        DigitWheel(cents, initial, 1, 1, line, width, style, reduced)
        DigitWheel(cents, initial, 0, 0, line, width, style, reduced)
    }
}

/** A thin meter whose fill springs to value (0–1). */
@Composable
internal fun AbacusMeter(value: () -> Float, color: Color, height: Float, reduced: Boolean, modifier: Modifier = Modifier) {
    val fill = remember { Animatable(value().coerceIn(0.001f, 1f)) }
    LaunchedEffect(Unit) {
        snapshotFlow { value().coerceIn(0.001f, 1f) }.collect { v ->
            launch { if (reduced) fill.snapTo(v) else fill.animateTo(v, spring(dampingRatio = 0.8f, stiffness = 120f)) }
        }
    }
    val shape = RoundedCornerShape((height / 2).dp)
    Box(modifier.height(height.dp).clip(shape).background(Color(0x1AF4ECE0))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fill.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(shape)
                .background(color),
        )
    }
}

/** Cents spent Monday to Friday; their sum is the abacus' resting total. */
private val Past = intArrayOf(6210, 4830, 9140, 5500, 12960)
private val Days = listOf("M", "T", "W", "T", "F", "S", "S")
private const val SCALE = 14000f
private const val BAR_H = 56f
internal const val ABACUS_WEEK_H = 132f

/**
 * The week strip under the abacus: one bar per day. Monday to Friday are spent and fixed;
 * Saturday is today and grows with whatever the beads log beyond the resting total (a spring on
 * scaleY, from the bottom); Sunday is still to come (a dashed outline).
 */
@Composable
internal fun AbacusWeek(total: () -> Int, base: Int, color: Color, reduced: Boolean) {
    val today = { max(0, total() - base) }
    val bar = remember { Animatable(min(1f, max(0.02f, today() / SCALE))) }
    LaunchedEffect(Unit) {
        snapshotFlow { min(1f, max(0.02f, today() / SCALE)) }.collect { v ->
            launch { if (reduced) bar.snapTo(v) else bar.animateTo(v, spring(dampingRatio = 0.75f, stiffness = 120f)) }
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(ABACUS_WEEK_H.dp)
            .background(Color(0x0DF4ECE0), shape)
            .border(0.5.dp, Color(0x1AF4ECE0), shape)
            .drawBehind { drawLine(Color(0x0DFFFFFF), Offset(18.dp.toPx(), 0.5f), Offset(size.width - 18.dp.toPx(), 0.5f), strokeWidth = 1.dp.toPx()) }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("This week", color = AbacusInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Today", color = AbacusInkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                AbacusRollingAmount(today, 0, 15f, AbacusInk, FontWeight.SemiBold, reduced = reduced)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val slot = RoundedCornerShape(6.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(BAR_H.dp)
                            .then(
                                if (i == 6) {
                                    Modifier.drawBehind {
                                        val sw = 1.dp.toPx()
                                        drawRoundRect(
                                            Color(0x2EF4ECE0),
                                            topLeft = Offset(sw / 2, sw / 2),
                                            size = Size(size.width - sw, size.height - sw),
                                            cornerRadius = CornerRadius(6.dp.toPx()),
                                            style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                                        )
                                    }
                                } else {
                                    Modifier.clip(slot).background(Color(0x0AF4ECE0))
                                },
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (i < 5) Box(Modifier.fillMaxWidth().height((Past[i] / SCALE * BAR_H).roundToInt().dp).background(Color(0x38F4ECE0), slot))
                        if (i == 5) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(BAR_H.dp)
                                    .graphicsLayer {
                                        scaleY = bar.value
                                        transformOrigin = TransformOrigin(0.5f, 1f)
                                    }
                                    .background(color, slot),
                            )
                        }
                    }
                    Text(d, color = if (i == 5) color else AbacusInkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

