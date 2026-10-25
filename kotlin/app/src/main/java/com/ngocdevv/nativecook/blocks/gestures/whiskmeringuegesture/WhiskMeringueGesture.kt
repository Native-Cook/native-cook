package com.ngocdevv.nativecook.blocks.gestures.whiskmeringuegesture

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Whisk Meringue Gesture — a baking app's step ("Whisk — Step 3 of 6: stiff peaks"): a glass bowl
 of egg whites seen from above. Draw circles in the bowl and the whisk follows the finger on a
 spring; the faster it circles, the faster the whites foam (clear → foamy → soft → firm → stiff),
 the contents swirl behind it and the progress ring advances. Stop, and the foam holds while its
 peaks slowly relax; two seconds after the last touch the bowl eases back to fresh whites (0.9 s)
 and the loop starts again from t = 0.

 One Canvas holds the bowl (AGSL, WhiskMeringueShader.kt; drawn stand-in below Android 13) and the
 whisk (two paths rebuilt per frame, with a blurred shadow); a small Canvas draws the ring.
 Reduced motion: the stiff meringue, still; the whisk still answers the finger.
 */

internal val WhiskBackground = Color(0xFFE4DED5)
private val Ink = Color(0xFF2A2520)
private val Mute = Color(0x992A2520)
private val Accent = Color(0xFFD98E2B)
private val Card = Color(0xFFFBF7F0)
private const val IDLE = 2.0
private const val RETURN = 0.9
private const val MODE_AUTO = 0
private const val MODE_HAND = 1
private const val MODE_RETURN = 2
private const val WORK = 0.028
private const val STIFF = 260.0
private const val DAMP = 26.0
private const val RING = 64f
private const val CARD_H = 96f

private class WhiskState {
    var now = -1.0
    var mode = MODE_AUTO
    var autoStart = 0.0
    var lastTouch = 0.0
    var touching = false
    var hand = whiskRestPose()
    var vx = 0.0
    var vy = 0.0
    val target = doubleArrayOf(0.0, 0.0)
    var returnFrom = whiskRestPose()
    var returnStart = 0.0
    var pose = whiskRestPose()
    var phase = 0.0
    var lastStage = -1
    var ready = false
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
}

private fun WhiskState.step(now: Double, auto: Boolean, reduced: Boolean) {
    val prev = this.now
    val dt = if (prev < 0) 0.0 else min(0.05, now - prev)
    if (prev < 0) autoStart = now
    this.now = now
    if (mode == MODE_HAND) {
        val p = hand.copyOf()
        if (touching) {
            vx += ((target[0] - p[F_HX]) * STIFF - vx * DAMP) * dt
            vy += ((target[1] - p[F_HY]) * STIFF - vy * DAMP) * dt
        } else {
            vx *= exp(-dt * 10)
            vy *= exp(-dt * 10)
        }
        val ox = p[F_HX]
        val oy = p[F_HY]
        p[F_HX] += vx * dt
        p[F_HY] += vy * dt
        // how far the whisk turned round the bowl's middle this frame
        var dA = atan2(p[F_HY], p[F_HX]) - atan2(oy, ox)
        if (dA > PI) dA -= PI * 2
        if (dA < -PI) dA += PI * 2
        val reach = min(1.0, hypot(p[F_HX], p[F_HY]) / 0.25)
        val work = abs(dA) * reach
        if (!reduced) p[F_FOAM] = min(1.0, p[F_FOAM] + work * WORK)
        p[F_SWIRL] += dA * 0.62 * reach
        val omega = if (dt > 0) work / dt else 0.0
        p[F_AGIT] += (min(1.0, omega / (2.3 * PI * 2)) - p[F_AGIT]) * min(1.0, dt * 6)
        val want = max(0.0, (p[F_FOAM] - 0.45) / 0.55)
        p[F_PEAK] += ((if (touching) want else want * 0.6) - p[F_PEAK]) * min(1.0, dt * 1.5)
        p[F_LIFT] *= exp(-dt * 6)
        p[F_LPEAK] *= exp(-dt * 0.5)
        hand = p
        if (!touching && now - lastTouch > IDLE) {
            mode = MODE_RETURN
            returnStart = now
            returnFrom = p
        }
    } else if (mode == MODE_RETURN && now - returnStart >= RETURN) {
        mode = MODE_AUTO
        autoStart = now
    }
    val rest = if (reduced) whiskStiffPose() else whiskRestPose()
    val t = ((now - autoStart) % LOOP + LOOP) % LOOP
    pose = when {
        mode == MODE_HAND -> hand
        mode == MODE_RETURN -> whiskMix(returnFrom, rest, whiskEaseInOut(min(1.0, (now - returnStart) / RETURN)))
        !auto -> rest
        else -> whiskPoseAt(t)
    }
    phase = if (mode == MODE_AUTO) t / LOOP * PI * 2 * 9 else (now % 100) * PI * 2
    ready = true
}

/** A stroke paint with a blur, for the whisk's soft shadow (mask filters need API 28 on a hardware canvas). */
private fun whiskShadowPaint(width: Float, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = Color(0xFF3A2C1E).toArgb()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

/**
 * A bowl of egg whites. Draw circles in the bowl to whisk them into stiff peaks.
 *
 * @param autoPlay whisks the whites to stiff peaks, lifts a peak and starts over, on a 9 s loop.
 */
@Composable
fun WhiskMeringueGesture(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember(reduced) { WhiskState() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(WHISK_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { WhiskFallback() }
    val wires = remember { Path() }
    val handle = remember { Path() }
    val wiresShadow = remember { Path() }
    val handleShadow = remember { Path() }

    BoxWithConstraints(modifier.fillMaxSize().background(WhiskBackground)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val g = remember(w, h, safeTop, safeBottom) {
            WhiskGeo(w, h, safeTop + 150.0, h - safeBottom - 16 - CARD_H - 12)
        }
        val handlePaint = remember(g.unit) { whiskShadowPaint((13 * g.unit).toFloat(), 5f) }
        val wirePaint = remember { whiskShadowPaint(2.6f, 2.5f) }

        LaunchedEffect(state, auto) {
            while (true) {
                withFrameNanos { n ->
                    state.step(n / 1e9, auto, reduced)
                    if (state.mode == MODE_HAND) {
                        val s = whiskStageOf(state.pose[F_FOAM])
                        if (state.lastStage >= 0 && s != state.lastStage) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        state.lastStage = s
                    } else {
                        state.lastStage = -1
                    }
                    state.tick.intValue += 1
                }
                // At rest without autoPlay (or under reduced motion) nothing moves until a finger lands.
                if (state.mode == MODE_AUTO && !auto) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A bowl of egg whites. Draw circles in the bowl to whisk them into stiff peaks." }
                .pointerInput(state, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        if (state.mode != MODE_HAND && state.ready) {
                            state.hand = state.pose.copyOf()
                            state.vx = 0.0
                            state.vy = 0.0
                        }
                        state.mode = MODE_HAND
                        state.touching = true
                        state.lastTouch = state.now
                        whiskFingerInBowl(g, (down.position.x / d).toDouble(), (down.position.y / d).toDouble(), state.target)
                        state.wake.intValue += 1
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                whiskFingerInBowl(g, (c.position.x / d).toDouble(), (c.position.y / d).toDouble(), state.target)
                                state.lastTouch = state.now
                            }
                        } finally {
                            state.touching = false
                            state.lastTouch = state.now
                        }
                    }
                },
        ) {
            state.tick.intValue
            if (!state.ready) return@Canvas
            val d = density
            val p = state.pose
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", w.toFloat(), h.toFloat())
                    shader.setFloatUniform("bowl", g.cx.toFloat(), g.cy.toFloat(), g.r.toFloat(), g.unit.toFloat())
                    shader.setFloatUniform("foam", p[F_FOAM].toFloat(), p[F_PEAK].toFloat(), p[F_SWIRL].toFloat(), p[F_AGIT].toFloat())
                    shader.setFloatUniform("whisk", p[F_HX].toFloat(), p[F_HY].toFloat(), p[F_LIFT].toFloat(), state.phase.toFloat())
                    shader.setFloatUniform("lpeak", p[F_LX].toFloat(), p[F_LY].toFloat(), p[F_LPEAK].toFloat(), 0f)
                    drawRect(brush, Offset.Zero, Size(w.toFloat(), h.toFloat()))
                } else {
                    with(fallback) { draw(g, p) }
                }
                val l = p[F_LIFT]
                whiskPaths(g, p[F_HX], p[F_HY], l, 0.0, 0.0, wires, handle)
                whiskPaths(g, p[F_HX], p[F_HY], l, (6 + 22 * l) * g.unit, (9 + 30 * l) * g.unit, wiresShadow, handleShadow)
                val shadowAlpha = (0.2 - 0.08 * l).toFloat()
                drawIntoCanvas { cv ->
                    val a = (shadowAlpha * 255).roundToInt().coerceIn(0, 255)
                    handlePaint.alpha = a
                    wirePaint.alpha = a
                    cv.nativeCanvas.drawPath(handleShadow.asAndroidPath(), handlePaint)
                    cv.nativeCanvas.drawPath(wiresShadow.asAndroidPath(), wirePaint)
                }
                val u = g.unit.toFloat()
                drawPath(handle, Color(0xFF646B71), style = Stroke(width = 13 * u, cap = StrokeCap.Round))
                drawPath(handle, Color(0xFFD9DEE1), style = Stroke(width = 4 * u, cap = StrokeCap.Round))
                drawPath(wires, Color(0xFF6A7177), style = Stroke(width = 2.6f, join = StrokeJoin.Round))
                drawPath(wires, Color(0xFFF1F4F5), style = Stroke(width = 1f, join = StrokeJoin.Round))
            }
        }

        val hair = (1f / screen.density).dp
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp),
        ) {
            BasicText("WHISK · STEP 3 OF 6", style = TextStyle(color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            BasicText(
                "Stiff peaks",
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            )
            BasicText(
                "Beat in quick circles until the whites hold a peak that stands up straight.",
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp),
                style = TextStyle(color = Mute, fontSize = 15.sp, lineHeight = 21.sp),
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (chip in listOf("4 egg whites", "Pinch of salt", "About 4 min")) {
                    BasicText(
                        chip,
                        modifier = Modifier
                            .background(Color(0xB3FFFCF6), RoundedCornerShape(14.dp))
                            .border(hair, Color(0x242A2520), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = TextStyle(color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 16).dp)
                .fillMaxWidth()
                .height(CARD_H.dp)
                .cssBoxShadow(Color(0x1F3C2814), 1.dp, 3.dp, 24.dp)
                .cssBoxShadow(Color(0x243C2814), 14.dp, 32.dp, 24.dp)
                .background(Card, RoundedCornerShape(24.dp))
                .border(hair, Color(0x1A2A2520), RoundedCornerShape(24.dp))
                .drawBehind {
                    val r = 24.dp.toPx()
                    drawLine(Color(0xE6FFFFFF), Offset(r, 1.dp.toPx()), Offset(size.width - r, 1.dp.toPx()), 1.dp.toPx())
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(RING.dp), contentAlignment = Alignment.Center) {
                val sweep = remember {
                    Brush.sweepGradient(
                        0f to Color(0xFFF4D27A),
                        1f / 3 to Color(0xFFE9A93F),
                        2f / 3 to Color(0xFFD98E2B),
                        1f to Color(0xFFF4D27A),
                    )
                }
                Canvas(Modifier.size(RING.dp)) {
                    state.tick.intValue
                    val s = 6.dp.toPx()
                    val inset = 5.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(Color(0x172A2520), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width = s))
                    val f = state.pose[F_FOAM].toFloat().coerceIn(0f, 1f)
                    if (f > 0.0005f) {
                        // the sweep starts at 12 o'clock, like the React Native arc from −90°
                        rotate(-90f) {
                            drawArc(sweep, 0f, 359.9f * f, false, Offset(inset, inset), arcSize, style = Stroke(width = s, cap = StrokeCap.Round))
                        }
                    }
                }
                WhiskMeringueRollNumber(
                    value = { state.tick.intValue; (state.pose[F_FOAM] * 100).toFloat() },
                    digits = 3,
                    line = 18f,
                    style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    suffix = "%",
                    suffixStyle = TextStyle(color = Mute, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    center = true,
                    still = reduced,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("SOFT · FIRM · STIFF", style = TextStyle(color = Mute, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
                WhiskMeringueRollLabel(
                    labels = WhiskStages,
                    index = { state.tick.intValue; whiskStageOf(state.pose[F_FOAM]) },
                    line = 24f,
                    style = TextStyle(color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold),
                    still = reduced,
                )
                BasicText(
                    "Tip the bowl: stiff whites stay put.",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Mute, fontSize = 13.sp),
                )
            }
        }
    }
}
