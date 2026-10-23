package com.ngocdevv.nativecook.blocks.media.iridescentcdflip

import android.graphics.Matrix
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Iridescent CD Flip — a compact disc in a sci-fi HUD that turns over to show its rainbow data side.
 A port of the SwiftUI block (IridescentCdFlip.swift); the numbers are the same.

 Look: black with fixed film grain. A white chamfered frame (corners cut at the top right and
 bottom left) holds the disc, 0.78 × the frame across: the printed side is glossy black with
 warm light bands across its lower third, a fan of cold streaks at the right, a gold mono
 "MIRROR INDEX" and red and green hub circles; the data side is mirror grey with two hue cycles
 of diffraction colour that sweep round as it turns. Both have a clear hub, a hole and a rim, and
 a soft band of light that slides across with the turn. Mono captions sit above and below the
 frame and retype themselves (a glitch) at every flip. Under the frame: the jewel case with the
 same art, the track list, a level meter, a laser read-out and a "FLIP DISC" pill.

 The disc turns about its vertical axis, seen from 1000 dp away (the same projection as Core
 Animation's m34 = −1/1000 in the Swift version, applied here as a 3×3 matrix on the canvas);
 near edge-on a chrome edge shows.

 autoPlay timeline (LOOP = 8 s, IridescentCdFlipMotion.kt):
   0.0 – 1.2    printed side, swaying ±4° (one sine a loop) so the light slides.
   1.2          flip: 180° on a spring (ω 6.5, ζ 0.6: about 9 % overshoot and a wobble), captions retype.
   1.2 – 4.2    the data side, its rainbow sweeping with the sway.
   4.2          flip on round to 360°: the printed side again, captions retype.
   8.0          360° is 0°: the same frame as 0.0.

 Touch: drag sideways on the disc to turn it with the finger (half a turn per disc width); on
 release it springs to the nearest side, thrown by its speed (a haptic when it lands on the other
 side). Tap the disc or the pill to flip it. 2 s after the last touch it turns back to the printed
 side and the loop starts again.
 Reduced motion: no loop and no sway; a flip is a 0.3 s cross-fade between the sides, the disc
 never turns; dragging does nothing.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private class CdPose(
    val angle: Double,
    /** Reduced motion: how much of the data side shows (a cross-fade), else null. */
    val back: Double?,
    val printed: Boolean,
    val since: Double,
    val clock: Double,
)

private class CdController(val reduced: Boolean) {
    var autoStart by mutableStateOf<Double?>(null)
    /** Whole turns added to the loop's angle after a hand-off. */
    var angleOffset by mutableDoubleStateOf(0.0)
    /** The turn under the spring: since, from, velocity, to. */
    var turn by mutableStateOf(doubleArrayOf(-1e9, 0.0, 0.0, 0.0))
    var dragAngle by mutableStateOf<Double?>(null)
    var touching = false
    var busy by mutableStateOf(false)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    fun pose(at: Double): CdPose {
        val start = autoStart
        if (start != null && at >= start) {
            val t = (at - start) % CdMotion.LOOP
            val cap = CdMotion.loopCaption(t)
            return CdPose(CdMotion.loopAngle(t) + angleOffset, null, cap.first, cap.second, t)
        }
        val tr = turn
        val e = max(0.0, at - tr[0])
        if (reduced) {
            val showBack = !CdMotion.printedSide(tr[3])
            val f = CdMotion.clamp01(e / 0.3)
            return CdPose(0.0, if (showBack) f else 1 - f, !showBack, e, 0.0)
        }
        val drag = dragAngle
        val angle = drag ?: CdMotion.spring(tr[1], tr[2], tr[3], e)[0]
        return CdPose(angle, null, CdMotion.printedSide(tr[3]), if (drag == null) e else 10.0, at % (CdMotion.LOOP * 100))
    }

    fun velocity(at: Double): Double {
        val tr = turn
        return CdMotion.spring(tr[1], tr[2], tr[3], max(0.0, at - tr[0]))[1]
    }

    /** Freezes whatever plays into the hand-driven state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        val start = autoStart ?: return
        autoStart = null
        if (now < start) return
        val t = (now - start) % CdMotion.LOOP
        val a = CdMotion.loopAngle(t) + angleOffset
        turn = doubleArrayOf(now, a, 0.0, Math.round(a / 180) * 180.0)
    }
}

@Composable
fun IridescentCdFlip(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val c = remember(reduced) { CdController(reduced) }
    val scope = rememberCoroutineScope()
    val painter = remember { CdPainter() }

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.busy) {
        c.frame = nowSeconds()
        if (!reduced || c.busy) while (true) withFrameNanos { c.frame = nowSeconds() }
    }

    /** 2 s after the last touch: back to the printed side, then the loop. */
    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            if (c.touching) return@launch
            val now = nowSeconds()
            var target = c.turn[3]
            if (!CdMotion.printedSide(target)) target += 180
            if (target != c.turn[3]) c.turn = doubleArrayOf(now, c.pose(now).angle, c.velocity(now), target)
            // Start once the spring has settled (within a hundredth of a degree after 2.5 s).
            c.autoStart = max(now, c.turn[0] + 2.5)
            c.angleOffset = target
        }
    }

    fun flip() {
        val now = nowSeconds()
        c.takeOver(now)
        val p = c.pose(now)
        c.turn = doubleArrayOf(now, p.angle, if (reduced) 0.0 else c.velocity(now), c.turn[3] + 180)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (reduced) {
            c.busy = true
            scope.launch {
                delay(400)
                if (!c.touching) c.busy = false
            }
        }
        scheduleResume()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(CdBackground)) {
        val d = LocalDensity.current.density
        val w = maxWidth.value
        val h = maxHeight.value
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val s = w - 48
        val y0 = top + 40
        val r = 0.39f * s
        val cx = w / 2
        val cy = y0 + s / 2
        val caseTop = y0 + s + 44
        val pose by remember(c) { derivedStateOf { c.pose(c.frame) } }

        // Film grain, drawn once.
        Canvas(Modifier.fillMaxSize()) { scale(d, d, Offset.Zero) { cdGrain(w, h) } }

        // The HUD, the light the disc throws, the disc.
        val matrix = remember { Matrix() }
        Canvas(Modifier.fillMaxSize()) {
            val p = pose
            val angle = p.angle
            val cosA = cos(angle * PI / 180)
            val backAmount = p.back ?: (if (cosA < 0) 1.0 else 0.0)
            scale(d, d, Offset.Zero) {
                val glowColor = if (backAmount > 0.5) Color(0xFF8A7CFF) else Color(0xFFFF6A2B)
                val strength = (p.back?.let { 0.12 + 0.1 * it } ?: if (cosA < 0) 0.22 * -cosA else 0.12 * cosA).toFloat()
                drawRect(
                    Brush.radialGradient(
                        0f to glowColor.copy(alpha = strength), 0.235f to glowColor.copy(alpha = strength), 1f to glowColor.copy(alpha = 0f),
                        center = Offset(cx, cy), radius = r * 1.7f,
                    ),
                    size = Size(w, h),
                )
                drawPath(cdFrame(24f, y0, s, 0.18f * s), Color.White.copy(alpha = 0.85f), style = Stroke(1f))
                val headline = if (p.printed) "COMPACT DISC (CD) — PRINTED SIDE" else "DATA SIDE — READ BY LASER 780 NM"
                val footline = if (p.printed) "MIRROR INDEX — HALATION" else "BE READ WITH A BEAM OF LIGHT"
                with(painter) {
                    monoText(CdMotion.glitch(headline, p.since, if (p.printed) 1 else 2), 24f, y0 - 8, 10f, Color.White.copy(alpha = 0.85f), 1.5f, Paint.Align.LEFT, 2)
                    monoText(CdMotion.glitch(footline, p.since - 0.1, if (p.printed) 3 else 4), 24f + s, y0 + s + 8, 10f, Color.White.copy(alpha = 0.85f), 1.5f, Paint.Align.RIGHT, 1)
                }
                // Edge-on, the disc's chrome edge.
                val edge = abs(sin(angle * PI / 180)).toFloat()
                if (p.back == null && abs(cosA) < 0.4) {
                    val ew = 1.5f + 3 * edge
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFF9AD5), Color.White, Color(0xFF9B9EA6), Color(0xFF5BC8FF), Color.White),
                            startY = cy - r, endY = cy + r,
                        ),
                        Offset(cx - ew / 2, cy - r), Size(ew, 2 * r), CornerRadius(ew / 2),
                    )
                }
            }
            val canvas = drawContext.canvas.nativeCanvas
            val back = p.back
            if (back != null) {
                // Reduced motion: the two sides cross-fade, flat.
                for (side in 0..1) {
                    val a = if (side == 0) 1 - back else back
                    if (a <= 0.001) continue
                    canvas.saveLayerAlpha(0f, 0f, size.width, size.height, (255 * a).roundToInt())
                    canvas.translate((cx - r) * d, (cy - r) * d)
                    scale(d, d, Offset.Zero) { with(painter) { face(r, 0.0, side == 1) } }
                    canvas.restore()
                }
            } else {
                // The face, turned about the vertical axis through the disc's centre: a 3×3 projective matrix
                // from the face's square (0, 0, 2r, 2r) to the screen in pixels.
                val t = angle * PI / 180
                val pp = sin(t) / CdMotion.DEPTH
                val co = cos(t)
                // H (centred point to screen, dp) · A (face square to centred) · then × density.
                val h00 = co + cx * pp
                val h02 = cx.toDouble()
                val h10 = cy * pp
                val h12 = cy.toDouble()
                val h20 = pp
                val m = floatArrayOf(
                    (d * h00).toFloat(), 0f, (d * (h02 - h00 * r)).toFloat(),
                    (d * h10).toFloat(), d, (d * (h12 - h10 * r - r)).toFloat(),
                    h20.toFloat(), 0f, (1 - h20 * r).toFloat(),
                )
                matrix.setValues(m)
                canvas.save()
                canvas.concat(matrix)
                with(painter) { face(r, angle, cosA < 0) }
                canvas.restore()
            }
        }

        // Touches on the disc's square: drag to turn, tap to flip.
        Box(
            Modifier
                .offset(x = 24.dp, y = y0.dp)
                .size(s.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (pose.printed) "Compact disc, printed side" else "Compact disc, data side"
                }
                .pointerInput(c, r) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        c.touching = true
                        c.takeOver(nowSeconds())
                        val dragFrom = c.pose(nowSeconds()).angle
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (reduced) continue
                            val dx = (change.position.x - down.position.x) / d
                            if (!moved && abs(dx) > 6) {
                                moved = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            if (moved) {
                                change.consume()
                                c.dragAngle = dragFrom + dx / (2 * r) * 180
                            }
                        }
                        c.touching = false
                        val a = c.dragAngle
                        if (moved && a != null) {
                            val now = nowSeconds()
                            val v = tracker.calculateVelocity().x / d / (2 * r) * 180
                            val target = Math.round((a + v * 0.15) / 180) * 180.0
                            if (CdMotion.printedSide(target) != CdMotion.printedSide(dragFrom)) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            c.turn = doubleArrayOf(now, a, v.toDouble(), target)
                            c.dragAngle = null
                            scheduleResume()
                        } else {
                            flip()
                        }
                    }
                },
        )

        // The jewel case, the track list and the level meter.
        val monoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        val tracks = listOf(Triple("01", "Halation", "3:41"), Triple("02", "Glass Sea", "4:02"), Triple("03", "Afterimage", "2:58"), Triple("04", "Beam of Light", "5:10"))
        Row(Modifier.offset(x = 24.dp, y = caseTop.dp).width((w - 48).dp)) {
            Canvas(Modifier.size(150.dp, 134.dp)) {
                scale(d, d, Offset.Zero) {
                    val cw = 150f
                    val ch = 134f
                    drawRect(Color(0xFF0B0B0E), size = Size(cw, ch))
                    clipRect(14f, 0f, cw, ch) {
                        val aw = cw - 14
                        with(painter) {
                            streaks(14f, -aw * 0.08f, aw)
                            monoText("MIRROR INDEX · HALATION", 22f, ch * 0.6f, 6f, Color.White.copy(alpha = 0.6f), 1f, Paint.Align.LEFT, 0)
                        }
                    }
                    drawRect(Color.White.copy(alpha = 0.85f), Offset(0.5f, 0.5f), Size(cw - 1, ch - 1), style = Stroke(1f))
                    drawRect(Color.White.copy(alpha = 0.85f), Offset(10f, 0f), Size(1f, ch))
                    drawRect(Color.White.copy(alpha = 0.5f), Offset(13f, 0f), Size(1f, ch))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                tracks.forEachIndexed { i, tr ->
                    Box(Modifier.fillMaxWidth().height(26.dp)) {
                        Row(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
                            BasicText(tr.first, style = monoStyle.copy(color = Color.White.copy(alpha = 0.45f)))
                            Spacer(Modifier.width(8.dp))
                            BasicText(tr.second, style = monoStyle.copy(color = Color.White.copy(alpha = if (i == 0) 1f else 0.6f)), maxLines = 1, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(4.dp))
                            BasicText(tr.third, style = monoStyle.copy(color = Color.White.copy(alpha = 0.45f)))
                        }
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                    }
                }
                Box(Modifier.fillMaxWidth().height(30.dp)) {
                    BasicText(
                        "NOW PLAYING · 01",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = (1.0 / 9).em, color = Color.White.copy(alpha = 0.55f)),
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    // A level meter: each bar moves a whole number of times a loop.
                    Canvas(Modifier.align(Alignment.CenterEnd).size(37.dp, 14.dp)) {
                        val cycles = doubleArrayOf(3.0, 5.0, 4.0, 7.0, 6.0, 3.0, 5.0, 4.0)
                        val clock = pose.clock
                        for (b in 0 until 8) {
                            val level = if (reduced) 0.4 else 0.5 + 0.5 * sin(2 * PI * cycles[b] * clock / CdMotion.LOOP + b * 1.7)
                            val bh = ((3 + 11 * level) * d).toFloat()
                            drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(b * 5 * d, (size.height - bh) / 2), Size(2 * d, bh), CornerRadius(d))
                        }
                    }
                }
            }
        }

        // A HUD read-out: the laser's position sweeping over the disc's radius, once there and back a loop.
        Canvas(Modifier.offset(x = 24.dp, y = (caseTop + 134 + 40).dp).size((w - 48).dp, 46.dp)) {
            val p = pose
            val u = if (reduced) 0.3 else 0.5 - 0.42 * cos(2 * PI * p.clock / CdMotion.LOOP)
            scale(d, d, Offset.Zero) {
                val line = w - 48
                for (k in 0..40) {
                    val x = line * k / 40f
                    val tall = if (k % 5 == 0) 8f else 4f
                    drawRect(Color.White.copy(alpha = if (k % 5 == 0) 0.6f else 0.3f), Offset(x - 0.5f, 18 - tall), Size(1f, tall))
                }
                drawRect(Color.White.copy(alpha = 0.35f), Offset(0f, 18f), Size(line, 1f))
                val x = (line * u).toFloat()
                drawRect(Color(0xFFFF4B3A), Offset(x - 1, 4f), Size(2f, 20f))
                drawCircle(Color(0xFFFF4B3A), 3f, Offset(x, 18.5f))
                with(painter) {
                    monoText("LASER 780 NM", 0f, 32f, 9f, Color.White.copy(alpha = 0.55f), 1f, Paint.Align.LEFT, 1)
                    monoText("HUB  —  RIM", line, 32f, 9f, Color.White.copy(alpha = 0.55f), 1f, Paint.Align.RIGHT, 1)
                }
            }
        }

        // The flip pill.
        val flipAction by rememberUpdatedState({ flip() })
        var pressed by remember { mutableStateOf(false) }
        Box(
            Modifier
                .offset(x = ((w - 168) / 2).dp, y = (h - max(bottom, 16f) - 60).dp)
                .size(168.dp, 44.dp)
                .graphicsLayer {
                    val sc = if (pressed) 0.96f else 1f
                    scaleX = sc
                    scaleY = sc
                }
                .background(Color.White.copy(alpha = if (pressed) 0.14f else 0f), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Flip disc"
                    onClick { flipAction(); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { flipAction() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A circular arrow, drawn.
                Canvas(Modifier.size(14.dp)) {
                    val st = Stroke(1.6f * d, cap = StrokeCap.Round)
                    val rr = size.minDimension / 2 - 2 * d
                    val ctr = Offset(size.width / 2, size.height / 2)
                    drawArc(Color.White, 200f, 140f, false, Offset(ctr.x - rr, ctr.y - rr), Size(2 * rr, 2 * rr), style = st)
                    drawArc(Color.White, 20f, 140f, false, Offset(ctr.x - rr, ctr.y - rr), Size(2 * rr, 2 * rr), style = st)
                    for (a in doubleArrayOf(340.0, 160.0)) {
                        val ar = a * PI / 180
                        val tip = Offset(ctr.x + (rr * cos(ar)).toFloat(), ctr.y + (rr * sin(ar)).toFloat())
                        val head = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(tip.x + (3.2f * d * cos(ar + 2.2)).toFloat(), tip.y + (3.2f * d * sin(ar + 2.2)).toFloat())
                            moveTo(tip.x, tip.y)
                            lineTo(tip.x + (3.2f * d * cos(ar + 0.6 + PI)).toFloat(), tip.y + (3.2f * d * sin(ar + 0.6 + PI)).toFloat())
                        }
                        drawPath(head, Color.White, style = st)
                    }
                }
                Spacer(Modifier.width(10.dp))
                BasicText(
                    "FLIP DISC",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (2.0 / 12).em, color = Color.White),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
