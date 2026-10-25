package com.ngocdevv.nativecook.blocks.progress.candleburntimer

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Candle Burn Timer — "Ember", a focus timer whose progress is a pillar candle burning down. Port of
 CandleBurnTimer.tsx. One AGSL shader draws the wall, table, brass dish, candle, pool, wick, drips,
 flame, glow and smoke (a drawn stand-in below Android 13). The wax height is the time left. Under
 it the time rolls like an odometer (25:00), a status line, and Pause / Reset.

 autoPlay (LOOP = 9 s, compressed time): 0–1.4 rest, 1.4–6.0 burn 25:00 → 00:00, 6.0–6.35 the flame
 goes out and smoke curls up, 6.6–7.4 "Session done", 7.4–8.4 a fresh candle rises, 8.4 it catches.

 Touch: drag sideways for wind (the flame leans with the drag's speed); a flick faster than
 1500 dp/s blows it out (light haptic); tap the candle to relight. Pause / Resume and Reset. The
 timer runs in real time. 2 s after the last touch the candle resets and the loop restarts at 0.
 Reduced motion: no loop, a still full candle at 25:00.
 */

internal val CandleBackground = Color(0xFF140E0B)
private val Ink = Color(0xFFF4E9DC)
private val Mute = Ink.copy(alpha = 0.6f)
private val Faint = Ink.copy(alpha = 0.36f)
private val Ember = Color(0xFFF0A25A)
private const val CANDLE_W = 108f
private const val DIGIT_H = 76f
private val Statuses = listOf("Focus · ends at 10:25", "Paused", "Blown out · tap the wick to relight", "Session done · take five")
/** Odometer columns of mm:ss: place (s) and base. */
private val Columns = listOf(600 to 10, 60 to 10, 10 to 6, 1 to 10)

private class CandleHolder(auto: Boolean) {
    val c = CandleState(auto)
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
}

@Composable
fun CandleBurnTimer(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // As in React Native the mode follows autoPlay, so under reduced motion the loop holds its first frame.
    val holder = remember(autoPlay, reduced) { CandleHolder(autoPlay) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(CANDLE_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize().background(CandleBackground)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2
        val baseY = (height * 0.62f).roundToInt().toFloat()
        val full = max(80f, min(250f, baseY - safeTop - 236))

        LaunchedEffect(holder, height, holder.wake.intValue) {
            if (height <= 0) return@LaunchedEffect
            val c = holder.c
            var last = -1L
            while (true) {
                val idle = withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 20)
                    last = now
                    c.now += dt
                    when (c.mode) {
                        CANDLE_AUTO -> {
                            if (auto) c.t = (c.t + dt) % LOOP
                            c.scriptFrame(c.t)
                            if (!auto) {
                                c.flick = 0.0
                                c.bend = 0.0
                            }
                        }
                        CANDLE_LIVE -> {
                            c.liveFrame(dt)
                            if (auto && c.now - c.lastTouch > CANDLE_IDLE_BEFORE_BACK) c.beginBack()
                        }
                        else -> if (c.backFrame(dt)) {
                            c.mode = CANDLE_AUTO
                            c.t = 0.0
                        }
                    }
                    if (c.buzz) {
                        c.buzz = false
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    holder.tick.intValue += 1
                    // A held first frame (reduced motion) needs no more frames until a touch.
                    c.mode == CANDLE_AUTO && !auto
                }
                if (idle) break
            }
        }

        fun touched() {
            val c = holder.c
            c.lastTouch = c.now
            if (c.mode != CANDLE_LIVE) c.mode = CANDLE_LIVE
            holder.wake.intValue += 1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "A burning candle that shows the time left. Drag sideways to blow on the flame; tap the candle to relight it."
                }
                .pointerInput(holder, cx, baseY, full) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        touched()
                        val c = holder.c
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var last = down.position
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            c.lastTouch = c.now
                            tracker.addPosition(change.uptimeMillis, change.position)
                            last = change.position
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            change.consume()
                            // Closer to the flame, stronger wind.
                            val x = change.position.x / d
                            val near = exp(-max(0.0, abs(x - cx).toDouble() - 60) / 140)
                            c.wind = tracker.calculateVelocity().x / d * near
                            if (abs(c.wind) > CANDLE_BLOW_AT && c.lit > 0.5 && c.blowOut()) c.buzz = true
                        }
                        c.lastTouch = c.now
                        if (released) {
                            val tap = hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d) < 10
                            val x = last.x / d
                            val y = last.y / d
                            val topY = baseY - full * (CANDLE_STUB + (1 - CANDLE_STUB) * c.remain).toFloat()
                            val onCandle = abs(x - cx) < CANDLE_W * 0.9f && y > topY - 110 && y < baseY + 20
                            if (tap && onCandle && c.relight()) c.buzz = true
                        }
                    }
                },
        ) {
            holder.tick.intValue
            val c = holder.c
            val d = density
            val wax = full * (CANDLE_STUB + (1 - CANDLE_STUB) * c.remain).toFloat()
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    val drips = FloatArray(8)
                    for (i in 0 until 2) {
                        // A drip hangs from the rim and moves down with it as the wax burns.
                        drips[i * 4] = (c.dx[i] * CANDLE_W * 0.5).toFloat()
                        drips[i * 4 + 1] = baseY - wax
                        drips[i * 4 + 2] = c.dripLength(i).toFloat()
                        drips[i * 4 + 3] = if (c.db[i] > -50) c.dripAlpha.toFloat() else 0f
                    }
                    val smokeStrength = c.smokeStrength()
                    shader.setFloatUniform("size", max(width, 1f), max(height, 1f))
                    shader.setFloatUniform("base", cx, baseY)
                    shader.setFloatUniform("candle", CANDLE_W, wax)
                    shader.setFloatUniform("flame", c.lit.toFloat(), c.bend.toFloat(), c.flick.toFloat(), max(c.lit, 0.25 * smokeStrength).toFloat())
                    shader.setFloatUniform(
                        "turb",
                        cos(c.phase).toFloat(), sin(c.phase).toFloat(), cos(c.phase * 1.7).toFloat(), sin(c.phase * 1.7).toFloat(),
                    )
                    shader.setFloatUniform("smoke", c.smokeAge().toFloat(), smokeStrength.toFloat())
                    shader.setFloatUniform("drips", drips)
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    candleFallback(width, height, cx, baseY, CANDLE_W, wax, c)
                }
            }
        }

        // Header.
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("EMBER", style = TextStyle(color = Ember, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Deep work", style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            BasicText("Draft the launch notes · session 3 of 4", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The timer: an odometer of mm:ss and the status line.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = (baseY + 40).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.semantics { contentDescription = "Time left" }, verticalAlignment = Alignment.CenterVertically) {
                CandleDigitColumn(holder, Columns[0].first, Columns[0].second)
                CandleDigitColumn(holder, Columns[1].first, Columns[1].second)
                BasicText(
                    ":",
                    Modifier.padding(horizontal = 2.dp).offset(y = (-8).dp),
                    style = TextStyle(color = Ink, fontSize = 56.sp, fontWeight = FontWeight.ExtraLight),
                )
                CandleDigitColumn(holder, Columns[2].first, Columns[2].second)
                CandleDigitColumn(holder, Columns[3].first, Columns[3].second)
            }
            Box(Modifier.fillMaxWidth().height(20.dp)) {
                Statuses.forEachIndexed { i, label ->
                    BasicText(
                        label,
                        Modifier.fillMaxWidth().graphicsLayer {
                            holder.tick.intValue
                            val w = holder.c.statusW[i].toFloat()
                            alpha = w.coerceIn(0f, 1f)
                            translationY = (1 - w) * 4 * density
                        },
                        style = TextStyle(color = Faint, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                    )
                }
            }
        }

        // Pause / Resume and Reset.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 24).dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(Ink.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                    .border(0.5.dp, Ink.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                    .semantics { contentDescription = "Pause or resume" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        touched()
                        holder.c.running = !holder.c.running
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(120.dp, 24.dp), contentAlignment = Alignment.Center) {
                    for (pause in listOf(true, false)) {
                        Row(
                            Modifier.graphicsLayer {
                                holder.tick.intValue
                                alpha = if (holder.c.running == pause) 1f else 0f
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Canvas(Modifier.size(12.dp, 14.dp)) {
                                val k = size.width / 12f
                                scale(k, k, pivot = Offset.Zero) {
                                    if (pause) {
                                        drawRoundRect(Ink, Offset(1f, 1f), Size(3.4f, 12f), CornerRadius(1.2f))
                                        drawRoundRect(Ink, Offset(7.6f, 1f), Size(3.4f, 12f), CornerRadius(1.2f))
                                    } else {
                                        drawPath(Path().apply { moveTo(2f, 1.5f); lineTo(11f, 7f); lineTo(2f, 12.5f); close() }, Ink)
                                    }
                                }
                            }
                            BasicText(if (pause) "Pause" else "Resume", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x2EF0A25A), spotColor = Color(0x66000000))
                    .background(Color(0xFFF1DCC0), RoundedCornerShape(26.dp))
                    .drawBehind {
                        val r = 26.dp.toPx()
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(r, 0.5.dp.toPx()), Offset(size.width - r, 0.5.dp.toPx()), 1.dp.toPx())
                    }
                    .semantics { contentDescription = "Reset the session" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        touched()
                        holder.c.resetCandle()
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Reset", style = TextStyle(color = Color(0xFF2A1A10), fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** One rolling digit column, driven by the seconds left; moves only in its graphics layer. */
@Composable
private fun CandleDigitColumn(holder: CandleHolder, place: Int, base: Int) {
    val style = remember {
        TextStyle(
            color = Ink,
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraLight,
            fontFeatureSettings = "tnum",
            textAlign = TextAlign.Center,
            lineHeight = DIGIT_H.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    Box(Modifier.size(42.dp, DIGIT_H.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    holder.tick.intValue
                    val s = max(holder.c.remain * CANDLE_SESSION, 0.0)
                    val whole = floor(s / place)
                    val digit = whole % base
                    // It rolls during the last second before its value changes (the units every second).
                    val r = s - whole * place
                    val roll = if (place == 1) r else max(0.0, r - (place - 1))
                    translationY = -((digit + candleEaseInOut(roll)) * DIGIT_H).toFloat() * density
                },
        ) {
            for (i in 0..base) {
                BasicText((i % base).toString(), Modifier.width(42.dp).requiredHeight(DIGIT_H.dp), style = style)
            }
        }
    }
}

/**
 * Below Android 13 (no AGSL): the same scene in gradients and paths: wall and table, the flame's
 * warm light, the brass dish, a cylinder of wax with its pool, the wick, two drips, the bent
 * teardrop flame with its core and glow, and a faint smoke column. Drawn in dp.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.candleFallback(
    w: Float,
    h: Float,
    cx: Float,
    baseY: Float,
    cw: Float,
    wax: Float,
    c: CandleState,
) {
    val hw = cw / 2
    val topY = baseY - wax
    val ery = cw * 0.13f
    val lit = c.lit.toFloat().coerceIn(0f, 1.2f)
    val bend = c.bend.toFloat()
    val glow = lit * (1f + 0.08f * c.flick.toFloat())
    val edge = baseY - 34
    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF130D0A), Color(0xFF0B0807)), 0f, edge), size = Size(w, edge))
    drawRect(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF21140D), Color(0xFF0D0806)), edge, h),
        topLeft = Offset(0f, edge),
        size = Size(w, h - edge),
    )
    val light = Offset(cx + bend * 10, topY - 30)
    drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFFF9E4D).copy(alpha = 0.24f * glow.coerceIn(0f, 1f)), Color.Transparent), light, 300f),
        300f,
        light,
    )
    // Brass dish.
    drawOval(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFC79450), Color(0xFF6B4A21)), baseY - 13, baseY + 13),
        topLeft = Offset(cx - hw - 30, baseY - 13),
        size = Size(cw + 60, 26f),
    )
    // Wax body and its pool.
    val body = androidx.compose.ui.graphics.Brush.horizontalGradient(
        0f to Color(0xFF6E6457), 0.25f to Color(0xFFEDE0CB), 0.6f to Color(0xFFD9CCB6), 1f to Color(0xFF5C5348),
        startX = cx - hw, endX = cx + hw,
    )
    drawRect(body, Offset(cx - hw, topY), Size(cw, wax))
    drawOval(body, Offset(cx - hw, baseY - ery * 0.4f), Size(cw, ery * 0.8f))
    drawOval(Color(0xFFF5E8D2).copy(alpha = 0.6f + 0.4f * glow.coerceIn(0f, 1f)), Offset(cx - hw, topY - ery), Size(cw, ery * 2))
    drawOval(Color(0xFFFFD999).copy(alpha = 0.55f * glow.coerceIn(0f, 1f)), Offset(cx - hw * 0.8f, topY - ery * 0.75f), Size(cw * 0.8f, ery * 1.5f))
    for (i in 0 until 2) {
        val a = if (c.db[i] > -50) c.dripAlpha.toFloat() else 0f
        val len = c.dripLength(i).toFloat()
        if (a < 0.01f || len < 0.5f) continue
        val x0 = cx + (c.dx[i] * cw * 0.5).toFloat()
        val y0 = topY + ery * 0.6f
        val y1 = topY + ery * 0.4f + len
        if (y1 <= y0) continue
        drawLine(Color(0xFFE6D7BF).copy(alpha = a), Offset(x0, y0), Offset(x0, y1), 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Color(0xFFE6D7BF).copy(alpha = a), 4.6f, Offset(x0, y1 + 1))
    }
    // Wick.
    drawLine(Color(0xFF140D0A), Offset(cx + bend * 1.5f, topY - 1), Offset(cx + bend * 1.5f + 1, topY - 12), 1.6f)
    if (lit > 0.01f) {
        val fh = 60f * lit * (1f + 0.07f * c.flick.toFloat())
        val fw = 11.5f * kotlin.math.sqrt(lit)
        val base = topY - 12
        val tip = Offset(cx + bend * 26f, base - fh)
        val flame = Path().apply {
            moveTo(tip.x, tip.y)
            cubicTo(cx + fw * 0.4f + bend * 10, base - fh * 0.55f, cx + fw * 1.1f, base - fw * 0.4f, cx, base + fw * 0.8f)
            cubicTo(cx - fw * 1.1f, base - fw * 0.4f, cx - fw * 0.4f + bend * 10, base - fh * 0.55f, tip.x, tip.y)
            close()
        }
        drawPath(
            flame,
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color(0xFFFF7024), 0.35f to Color(0xFFFF9E3D), 0.7f to Color(0xFFFFDB80), 0.9f to Color(0xFFFFF7E0), 1f to Color(0xFF4D6BFF),
                startY = tip.y,
                endY = base + fw * 0.8f,
            ),
            alpha = (lit * 1.4f).coerceIn(0f, 1f) * 0.95f,
        )
        val g = Offset(cx + bend * 12, topY - 34)
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFFFA84D).copy(alpha = 0.32f * glow.coerceIn(0f, 1f)), Color.Transparent), g, 110f),
            110f,
            g,
        )
    }
    val smoke = c.smokeStrength().toFloat()
    if (smoke > 0.01f) {
        val reach = c.smokeAge().toFloat() * 75f
        val path = Path().apply {
            moveTo(cx, topY - 8)
            var sy = 0f
            while (sy < reach && sy < 300f) {
                sy += 6f
                lineTo(cx + sin(sy * 0.045f - c.smokeAge().toFloat() * 2.2f) * (4f + sy * 0.12f), topY - 8 - sy)
            }
        }
        drawPath(path, Color(0xFF9E9994).copy(alpha = 0.45f * smoke), style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}
