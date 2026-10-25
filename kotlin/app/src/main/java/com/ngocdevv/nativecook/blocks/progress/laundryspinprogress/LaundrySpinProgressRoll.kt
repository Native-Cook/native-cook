package com.ngocdevv.nativecook.blocks.progress.laundryspinprogress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Rolling readouts, as in the React Native and Swift versions: numbers roll digit by digit like an
 odometer and always come to rest on whole digits (the shown value springs to round(value)); a
 label rolls inside a clipped box (the old one leaves upwards, the new one comes from below).
 The value is read through a snapshotFlow and the digits are drawn in a draw lambda, so a moving
 readout never recomposes; a label recomposes once per change of index.
 */

private val LaundrySpinRollDigits = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val LaundrySpinRollEase = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private fun LaundrySpinColumnPos(shown: Float, k: Int): Float {
    if (k == 0) return ((shown % 10f) + 10f) % 10f
    val p = 10f.pow(k)
    val baseV = floor(shown / p)
    val carry = min(1f, max(0f, shown - baseV * p - (p - 1f)))
    return (((baseV % 10f) + 10f) % 10f) + carry
}

private fun LaundrySpinColumnVisible(shown: Float, k: Int, pad: Boolean): Float {
    if (k == 0 || pad) return 1f
    return min(1f, max(0f, shown - (10f.pow(k) - 1f)))
}

/**
 * A number that rolls to round([value]) whenever that changes. Leading zeros are hidden and the
 * digits close up towards the prefix; [center] keeps them centred (half the shift).
 */
@Composable
internal fun LaundrySpinRollNumber(
    value: () -> Float,
    digits: Int,
    line: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    suffixStyle: TextStyle = style,
    center: Boolean = false,
    pad: Boolean = false,
    still: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val digitStyle = remember(style) { style.merge(TextStyle(fontFeatureSettings = "tnum")) }
    val glyphs = remember(digitStyle) { Array(10) { measurer.measure(it.toString(), digitStyle) } }
    val pre: TextLayoutResult? = remember(prefix, suffixStyle) { prefix?.let { measurer.measure(it, suffixStyle, softWrap = false) } }
    val suf: TextLayoutResult? = remember(suffix, suffixStyle) { suffix?.let { measurer.measure(it, suffixStyle, softWrap = false) } }
    val colW = remember(glyphs) { glyphs.maxOf { it.size.width }.toFloat() }
    val shown = remember { Animatable(0f) }
    var label by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) {
        var first = true
        snapshotFlow { value().roundToInt() }.collectLatest { r ->
            label = r
            if (first || still) {
                first = false
                shown.snapTo(r.toFloat())
            } else {
                shown.animateTo(r.toFloat(), spring(dampingRatio = 0.86f, stiffness = 224f))
            }
        }
    }
    val density = LocalDensity.current
    val preW = (pre?.size?.width ?: 0).toFloat()
    val sufW = (suf?.size?.width ?: 0).toFloat()
    val widthDp = with(density) { (preW + colW * digits + sufW).toDp() }
    Box(
        modifier
            .size(widthDp, line.dp)
            .semantics { contentDescription = "${prefix ?: ""}$label${suffix ?: ""}" }
            .drawBehind {
                val lineH = line.dp.toPx()
                val g0 = glyphs[0]
                // the digits' baseline, with the glyph box centred in the line
                val base = (lineH - g0.size.height) / 2f + g0.firstBaseline
                val s = shown.value
                var hidden = 0f
                for (k in 1 until max(digits, 1)) hidden += 1f - LaundrySpinColumnVisible(s, k, pad)
                val shift = -hidden * colW * (if (center) 0.5f else 1f)
                pre?.let { drawText(it, topLeft = Offset(0f, base - it.firstBaseline)) }
                translate(left = preW + shift) {
                    for (c in 0 until digits) {
                        val k = digits - 1 - c
                        val x = c * colW
                        val a = LaundrySpinColumnVisible(s, k, pad)
                        if (a <= 0.001f) continue
                        val pos = LaundrySpinColumnPos(s, k)
                        clipRect(x, 0f, x + colW, lineH) {
                            val i0 = floor(pos).toInt()
                            for (i in i0..min(i0 + 1, 10)) {
                                val d = i % 10
                                val gl = glyphs[d]
                                val y = (i - pos) * lineH + base - gl.firstBaseline
                                drawText(gl, topLeft = Offset(x + (colW - gl.size.width) / 2f, y), alpha = a)
                            }
                        }
                    }
                    suf?.let { drawText(it, topLeft = Offset(colW * digits, base - it.firstBaseline)) }
                }
            },
    )
}

/**
 * A label that rolls to labels[index] whenever the index changes (a few times a loop). Two slots
 * take turns: the new text goes into the hidden slot, then the roll starts.
 */
@Composable
internal fun LaundrySpinRollLabel(
    labels: List<String>,
    index: () -> Int,
    line: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
    still: Boolean = false,
) {
    var slotA by remember { mutableIntStateOf(0) }
    var slotB by remember { mutableIntStateOf(0) }
    var front by remember { mutableIntStateOf(0) }
    val k = remember { Animatable(1f) }
    LaunchedEffect(still) {
        var prev = -1
        snapshotFlow { index() }.collectLatest { i ->
            val changed = if (prev < 0) i != 0 else i != prev
            prev = i
            if (!changed) return@collectLatest
            if (front == 0) {
                slotB = i
                front = 1
            } else {
                slotA = i
                front = 0
            }
            if (still) {
                k.snapTo(1f)
            } else {
                k.snapTo(0f)
                k.animateTo(1f, tween(380, easing = LaundrySpinRollEase))
            }
        }
    }
    val lineStyle = remember(style, line) {
        style.merge(
            TextStyle(
                lineHeight = line.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
        )
    }
    val longest = remember(labels) { labels.maxByOrNull { it.length } ?: "" }
    val current = labels.getOrElse(if (front == 0) slotA else slotB) { "" }
    Box(modifier.height(line.dp).clipToBounds().semantics { contentDescription = current }) {
        BasicText(longest, style = lineStyle, maxLines = 1, softWrap = false, modifier = Modifier.graphicsLayer { alpha = 0f })
        for (slot in 0..1) {
            BasicText(
                labels.getOrElse(if (slot == 0) slotA else slotB) { "" },
                style = lineStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val p = k.value
                    val lh = line.dp.toPx()
                    if (front == slot) {
                        alpha = p
                        translationY = (1f - p) * lh
                    } else {
                        alpha = 1f - p
                        translationY = -p * lh
                    }
                },
            )
        }
    }
}
