package com.ngocdevv.nativecook.blocks.media.filmstriparcscrubber

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
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
 Filmstrip Arc Scrubber — a video player whose scrubber lifts into a curved 3D filmstrip of frames.
 A port of the SwiftUI block (FilmstripArcScrubber.swift); the numbers are the same.

 The "video" is a one-minute night drive drawn as a function of its time (FilmstripArcScrubberModel
 .kt): the light goes from golden hour through dusk to night, the sun sets by 0:30 and the moon
 rises after 0:34, stars come out, far mountains and near hills drift past at two speeds, lane
 dashes rush at the camera, lamp posts light up along the verge and a car's tail lights ride ahead.
 Scrubbing really rewinds and forwards it, and the strip's thumbnails are the same scene at their
 own times.

 Look: near-black #0A0A0C; the video fills the top 60 % (12 dp margins, 28 dp corners) with a title
 chip, an HD chip and a mono timecode; then the title (24 sp bold) and a line about the episode, a
 4 dp progress bar with a 14 dp thumb and times, and transport buttons (back 10, play/pause in a
 64 dp white circle, forward 10).

 The strip: frames every 2.5 s of video, each a 64 × 82 film cell (sprocket holes top and bottom, a
 56 × 60 picture), wrapped round a cylinder of radius 230 seen in perspective (focal 520), so the
 frames turn away (up to about 75°) and shrink towards the sides, darkening as they turn; a dock
 magnification (1 + 0.38 · e^(−d²/1.2)) swells the frames near the centre, where a fixed white
 magnifier frame with the time above it marks the chosen one. It lifts out of the bar 98 dp above
 it, each frame flying out of the thumb with a stagger (0.07 of the lift per frame from the centre),
 and slides 40 dp per frame under the finger. While it is up the title fades out and the video
 scrubs with a short horizontal motion blur. On release the chosen frame grows from the magnifier
 into the video (0.6 s, easeInOut), the strip folds back (0.5 s) and playback resumes.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 2.0   playing from 0:18
   2.0         the thumb is pressed: playback holds, the strip lifts (0.5 s, ease out back)
   2.7 – 4.5   dragged forward to 0:45 (night);  4.6 – 6.6 back to 0:15 (easeInOut)
   7.0         released: the 0:15 frame flies up into the video (0.6 s), playback resumes
   7.2 – 7.7   the strip folds back into the bar
   10.0        0:18 again, playing (the same frame as 0)

 Touch: press the bar and drag sideways to scrub (a tick haptic per frame), release to jump to the
 nearest frame; tap the video or the play button to pause or play; tap back or forward to skip
 10 s. 2 s after the last touch the video scrubs back to 0:18 (0.6 s) and the loop starts over.
 Reduced motion: no loop (paused at 0:18); the strip is flat and fades in, with no motion blur and
 no flight.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private val BG = Color(0.04f, 0.04f, 0.047f)

private class FasTween(val from: Double, val to: Double, val at: Double, val dur: Double, val back: Boolean) {
    constructor(v: Double) : this(v, v, 0.0, 1.0, false)
    fun value(now: Double): Double {
        val u = (now - at) / dur
        return from + (to - from) * if (back) FasModel.easeOutBack(u) else FasModel.easeInOut(u)
    }
}

private class FasController(val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    /** The scrub back to rest: [when, from time], or null. */
    var returning by mutableStateOf<DoubleArray?>(null)
    // The manual state.
    var time0 by mutableDoubleStateOf(FasModel.REST_TIME)
    var at by mutableDoubleStateOf(0.0)
    var playing by mutableStateOf(false)
    var lift by mutableStateOf(FasTween(0.0))
    var flyAt by mutableStateOf<Double?>(null)
    var flyTime by mutableDoubleStateOf(0.0)
    var speed by mutableDoubleStateOf(0.0)
    var speedAt by mutableDoubleStateOf(0.0)
    var busyUntil by mutableDoubleStateOf(0.0)
    var resume: Job? = null

    fun now() = frame
    fun time(now: Double) = if (playing) FasModel.wrap(time0 + now - at) else time0

    fun shown(now: Double): FasShown {
        val start = autoStart
        if (start != null && now >= start && !reduced) return FasModel.shown(now - start)
        val r = returning
        if (r != null) {
            val u = (now - r[0]) / 0.6
            return FasShown(r[1] + (FasModel.REST_TIME - r[1]) * FasModel.easeInOut(u), 0.0, 0.0, 0.0, 0.0, false)
        }
        var fly = 0.0
        val f = flyAt
        if (f != null && !reduced) {
            val u = (now - f) / 0.6
            if (u >= 0 && u < 1) fly = FasModel.easeInOut(u)
        }
        val sp = if (reduced) 0.0 else speed * exp(-(now - speedAt) * 6)
        return FasShown(time(now), lift.value(now), fly, flyTime, sp, playing)
    }

    fun takeOver(now: Double) {
        resume?.cancel()
        val s = shown(now)
        time0 = s.time
        at = now
        playing = s.playing
        lift = FasTween(s.lift)
        // A flight under way carries on (roughly where it was).
        if (s.fly > 0) {
            flyAt = now - s.fly * 0.6
            flyTime = s.flyTime
        }
        autoStart = null
        returning = null
    }
}

@Composable
fun FilmstripArcScrubber(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { FasController(reduced) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()

    LaunchedEffect(c) {
        val now = nowSeconds()
        c.frame = now
        c.time0 = FasModel.REST_TIME
        c.at = now
        if (autoPlay && !reduced) c.autoStart = now
        // Under reduced motion the clock only needs to run while something moves; it is cheap to keep.
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val now = c.now()
            c.returning = doubleArrayOf(now, c.shown(now).time)
            c.autoStart = now + 0.6
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(BG)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val density = LocalDensity.current.density
        val l = remember(width, height, top, bottomInset) { FasModel.Layout(width, height, top, bottomInset) }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    val s = c.shown(c.frame)
                    contentDescription = "Night Drive, episode 3, ${FasModel.clock(s.time)} of 1:00, ${if (s.playing) "playing" else "paused"}"
                }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now0 = c.now()
                        c.takeOver(now0)
                        val p = down.position / density
                        val ty = l.transportY
                        val zone = when {
                            p.y > l.barY - 36 && p.y < l.barY + 30 && p.x > l.barX - 20 && p.x < l.barX + l.barW + 20 -> 1
                            hypot(p.x - l.w / 2, p.y - ty) < 38 -> 2
                            hypot(p.x - (l.w / 2 - 96), p.y - ty) < 30 -> 3
                            hypot(p.x - (l.w / 2 + 96), p.y - ty) < 30 -> 4
                            l.video.contains(p) -> 5
                            else -> 0
                        }
                        var start = 0.0
                        var last = 0.0
                        var lastAt = now0
                        var frameIdx = 0
                        if (zone == 1) {
                            val t = c.time(now0)
                            c.time0 = t
                            c.playing = false
                            c.at = now0
                            c.lift = FasTween(c.lift.value(now0), 1.0, now0, if (reduced) 0.2 else 0.45, !reduced)
                            start = t
                            last = t
                            frameIdx = (t / FasModel.FRAME_STEP).roundToInt()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (zone != 1) continue
                            change.consume()
                            val now = c.now()
                            val dx = (change.position.x - down.position.x) / density
                            val t = min(FasModel.DURATION - 0.01, max(0.0, start + dx / 40 * FasModel.FRAME_STEP))
                            c.time0 = t
                            val dt = now - lastAt
                            if (dt > 0.001) {
                                c.speed = 0.6 * c.speed * exp(-(now - c.speedAt) * 6) + 0.4 * (t - last) / dt
                                c.speedAt = now
                            }
                            last = t
                            lastAt = now
                            val f = (t / FasModel.FRAME_STEP).roundToInt()
                            if (f != frameIdx) {
                                frameIdx = f
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        val now = c.now()
                        when (zone) {
                            1 -> {
                                val snap = min(FasModel.DURATION - FasModel.FRAME_STEP, (c.time0 / FasModel.FRAME_STEP).roundToInt() * FasModel.FRAME_STEP)
                                c.time0 = snap
                                c.at = now
                                c.playing = true
                                c.flyAt = now
                                c.flyTime = snap
                                c.lift = FasTween(c.lift.value(now), 0.0, now + if (reduced) 0.0 else 0.2, if (reduced) 0.2 else 0.5, false)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            2, 5 -> {
                                c.time0 = c.time(now)
                                c.at = now
                                c.playing = !c.playing
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            3, 4 -> {
                                val d = if (zone == 3) -10.0 else 10.0
                                c.time0 = FasModel.wrap(c.time(now) + d)
                                c.at = now
                                c.speed = d * 3
                                c.speedAt = now
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                        scheduleResume()
                    }
                },
        ) {
            val s = c.shown(c.frame)
            withTransform({ scale(density, density, Offset.Zero) }) {
                fasScreen(this, l, s, reduced, measurer)
            }
        }

        // Text.
        val timeNow by remember(c) { derivedStateOf { c.shown(c.frame).time } }
        fun liftNow() = FasModel.clamp01(c.shown(c.frame).lift)
        val v = l.video
        Box(
            Modifier
                .offset(x = (v.left + 16).dp, y = (v.top + 16).dp)
                .height(28.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Night Drive · Ep. 3", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        Box(Modifier.offset(x = (v.right - 16 - 60).dp, y = (v.top + 19).dp).width(60.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .height(22.dp)
                    .border(1.2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("HD", style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
        val timecode by remember(c) { derivedStateOf { FasModel.timecode(timeNow) } }
        Box(
            Modifier
                .offset(x = (v.left + 16).dp, y = (v.bottom - 16 - 24).dp)
                .height(24.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(timecode, style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace))
        }
        Column(
            Modifier
                .offset(x = 24.dp, y = (l.titleY + 28).dp)
                .width((width - 48).dp)
                .layout { measurable, constraints ->
                    // Centred on titleY + 28, like the SwiftUI frame.
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, -placeable.height / 2) }
                }
                .graphicsLayer { alpha = (1 - liftNow()).toFloat() },
        ) {
            BasicText("Night Drive", maxLines = 1, style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            BasicText(
                "Episode 3 · The coast road after midnight", maxLines = 1,
                style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp),
            )
        }
        val clocks by remember(c) { derivedStateOf { FasModel.clock(timeNow) to "-" + FasModel.clock(FasModel.DURATION - timeNow) } }
        Row(
            Modifier
                .offset(x = l.barX.dp, y = (l.barY + 12).dp)
                .width(l.barW.dp),
        ) {
            val style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
            BasicText(clocks.first, style = style)
            Spacer(Modifier.weight(1f))
            BasicText(clocks.second, style = style)
        }
        // The time above the magnifier.
        val label by remember(c) { derivedStateOf { FasModel.clock(timeNow) } }
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            IntOffset(
                                (l.w / 2 * density - placeable.width / 2.0).roundToInt(),
                                ((l.stripY - FasModel.CELL_H * 0.69 - 18) * density - placeable.height / 2.0).roundToInt(),
                            ),
                        )
                    }
                }
                .graphicsLayer {
                    val lift = liftNow()
                    alpha = (if (reduced) lift else FasModel.smoothstep(0.4, 1.0, lift)).toFloat()
                },
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
            }
        }
    }
}

// MARK: Drawing (in dp)

private fun rrect(r: Rect, corner: Float) = Path().apply { addRoundRect(RoundRect(r, CornerRadius(corner, corner))) }

/** Draws [block] on a layer at an opacity. */
private fun DrawScope.faded(bounds: Rect, alpha: Float, block: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha.coerceIn(0f, 1f) })
    block()
    canvas.restore()
}

private fun fasScreen(d: DrawScope, l: FasModel.Layout, s: FasShown, reduce: Boolean, measurer: TextMeasurer) {
    d.drawRect(BG, size = Size(l.w.toFloat(), l.h.toFloat()))
    // The video, smeared sideways while it is scrubbed.
    val v = l.video
    d.clipPath(rrect(v, 28f)) {
        FasModel.drawScene(this, v, s.time, false)
        val blur = if (reduce) 0.0 else FasModel.clamp01((abs(s.speed) - 1.5) / 10)
        if (blur > 0.02) {
            val dir = if (s.speed > 0) 1.0 else -1.0
            for (k in 1..3) {
                faded(v, (0.3 * blur).toFloat()) {
                    withTransform({ translate((-dir * k * 7 * blur).toFloat(), 0f) }) {
                        FasModel.drawScene(this, v, s.time - dir * k * 0.08, true)
                    }
                }
            }
        }
    }
    // Progress bar.
    val p = s.time / FasModel.DURATION
    val lift = FasModel.clamp01(s.lift)
    val by = l.barY.toFloat()
    d.drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(l.barX.toFloat(), by - 2), Size(l.barW.toFloat(), 4f), CornerRadius(2f, 2f))
    d.drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(l.barX.toFloat(), by - 2), Size((l.barW * p).toFloat(), 4f), CornerRadius(2f, 2f))
    val tx = (l.barX + l.barW * p).toFloat()
    val tr = (7 * (1 + 0.4 * lift)).toFloat()
    if (lift > 0.01) {
        d.drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = (0.25 * lift).toFloat()), Color.Transparent), center = Offset(tx, by), radius = 22f), 22f, Offset(tx, by))
    }
    d.drawCircle(Color.White, tr, Offset(tx, by))
    fasTransport(d, l, s.playing, measurer)
    fasStrip(d, l, s, reduce)
    // The chosen frame flying up into the video.
    if (s.fly > 0.001 && s.fly < 0.999) {
        val f = s.fly.toFloat()
        val from = magnifiedPicture(l)
        val r = Rect(
            from.left + (v.left - from.left) * f, from.top + (v.top - from.top) * f,
            from.right + (v.right - from.right) * f, from.bottom + (v.bottom - from.bottom) * f,
        )
        d.faded(r, (1 - FasModel.smoothstep(0.75, 1.0, s.fly)).toFloat()) {
            clipPath(rrect(r, 3 + 25 * f)) { FasModel.drawScene(this, r, s.flyTime, f < 0.5) }
        }
    }
}

/** Where the magnified centre frame's picture sits. */
private fun magnifiedPicture(l: FasModel.Layout): Rect {
    val mag = 1.38
    val cy = l.stripY + (FasModel.PIC_Y + FasModel.PIC_H / 2 - FasModel.CELL_H / 2) * mag
    val hw = FasModel.PIC_W * mag / 2
    val hh = FasModel.PIC_H * mag / 2
    return Rect((l.w / 2 - hw).toFloat(), (cy - hh).toFloat(), (l.w / 2 + hw).toFloat(), (cy + hh).toFloat())
}

private fun fasTransport(d: DrawScope, l: FasModel.Layout, playing: Boolean, measurer: TextMeasurer) {
    val cy = l.transportY.toFloat()
    val cx = (l.w / 2).toFloat()
    d.drawCircle(Color.White, 32f, Offset(cx, cy))
    if (playing) {
        d.drawRoundRect(Color.Black, Offset(cx - 9, cy - 11), Size(6f, 22f), CornerRadius(2f, 2f))
        d.drawRoundRect(Color.Black, Offset(cx + 3, cy - 11), Size(6f, 22f), CornerRadius(2f, 2f))
    } else {
        val tri = Path().apply { moveTo(cx - 7, cy - 12); lineTo(cx + 13, cy); lineTo(cx - 7, cy + 12); close() }
        d.drawPath(tri, Color.Black)
    }
    val white = Color.White.copy(alpha = 0.9f)
    for ((dir, x) in listOf(-1.0 to cx - 96.0, 1.0 to cx + 96.0)) {
        // A circular arrow round "10", open at the top; its head turns back (or on) over the top.
        val arc = Path()
        for (k in 0..40) {
            val a = (-60 + 310 * k / 40.0) * PI / 180
            val qx = (x + dir * -14 * cos(a)).toFloat()
            val qy = (cy + 14 * sin(a)).toFloat()
            if (k == 0) arc.moveTo(qx, qy) else arc.lineTo(qx, qy)
        }
        d.drawPath(arc, white, style = Stroke(2f, cap = StrokeCap.Round))
        // The head sits at the start (−60°), pointing along the arc away from it.
        val a0 = -60 * PI / 180
        val tipX = x + dir * -14 * cos(a0)
        val tipY = cy + 14 * sin(a0)
        val hx = -dir * sin(a0)
        val hy = -cos(a0)
        val head = Path().apply {
            moveTo((tipX + hx * 6).toFloat(), (tipY + hy * 6).toFloat())
            lineTo((tipX - hy * 4.5 * dir).toFloat(), (tipY + hx * 4.5 * dir).toFloat())
            lineTo((tipX + hy * 4.5 * dir).toFloat(), (tipY - hx * 4.5 * dir).toFloat())
            close()
        }
        d.drawPath(head, white)
        val ten = measurer.measure("10", TextStyle(color = white, fontSize = 10.sp, fontWeight = FontWeight.Bold), density = Density(1f))
        d.drawText(ten, topLeft = Offset((x - ten.size.width / 2.0).toFloat(), cy + 0.5f - ten.size.height / 2f))
    }
}

/** The filmstrip on its cylinder. */
private fun fasStrip(d: DrawScope, l: FasModel.Layout, s: FasShown, reduce: Boolean) {
    val lift = s.lift
    if (lift <= 0.001) return
    val c = s.time / FasModel.FRAME_STEP
    val cx = l.w / 2
    val cy = l.stripY + (1 - min(1.0, lift)) * 60
    val thumbX = l.barX + l.barW * s.time / FasModel.DURATION
    val thumbY = l.barY
    val last = (FasModel.DURATION / FasModel.FRAME_STEP).toInt() - 1
    val cells = ArrayList<Pair<Double, Int>>()
    for (k in max(0, floor(c).toInt() - 7)..min(last, floor(c).toInt() + 8)) cells.add((k - c) to k)
    cells.sortByDescending { abs(it.first) }
    for ((dd, k) in cells) {
        val theta = dd * FasModel.CELL_W / FasModel.RADIUS
        if (!reduce && abs(theta) > 1.32) continue
        if (reduce && abs(dd) > 3.4) continue
        val u = if (reduce) FasModel.clamp01(lift) else FasModel.easeInOut(lift * 1.5 - abs(dd) * 0.07)
        if (u <= 0.001) continue
        val mag = if (reduce) 1.0 else 1 + 0.38 * exp(-dd * dd / 1.2)
        val hw = FasModel.CELL_W / 2 * mag
        val hh = FasModel.CELL_H / 2 * mag
        // A point of the cell's plane (lx, ly from its centre) on screen.
        fun corner(lx: Double, ly: Double): Offset {
            if (reduce) return Offset((cx + dd * (FasModel.CELL_W + 4) + lx).toFloat(), (cy + ly).toFloat())
            val x = FasModel.RADIUS * sin(theta) + cos(theta) * lx
            val z = FasModel.RADIUS * (cos(theta) - 1) - sin(theta) * lx
            val kz = FasModel.FOCAL / (FasModel.FOCAL - z)
            return Offset((cx + x * kz).toFloat(), (cy + ly * kz).toFloat())
        }
        // Unfolding out of the thumb.
        val small = 0.12
        fun lerp(ax: Double, ay: Double, b: Offset) = Offset((ax + (b.x - ax) * u).toFloat(), (ay + (b.y - ay) * u).toFloat())
        val tl = lerp(thumbX - FasModel.CELL_W / 2 * small, thumbY - FasModel.CELL_H / 2 * small, corner(-hw, -hh))
        val tr = lerp(thumbX + FasModel.CELL_W / 2 * small, thumbY - FasModel.CELL_H / 2 * small, corner(hw, -hh))
        val bl = lerp(thumbX - FasModel.CELL_W / 2 * small, thumbY + FasModel.CELL_H / 2 * small, corner(-hw, hh))
        val a = (tr.x - tl.x) / FasModel.CELL_W.toFloat()
        val b = (tr.y - tl.y) / FasModel.CELL_W.toFloat()
        val cc = (bl.x - tl.x) / FasModel.CELL_H.toFloat()
        val dy = (bl.y - tl.y) / FasModel.CELL_H.toFloat()
        val matrix = android.graphics.Matrix().apply { setValues(floatArrayOf(a, cc, tl.x, b, dy, tl.y, 0f, 0f, 1f)) }
        val canvas = d.drawContext.canvas
        canvas.save()
        canvas.nativeCanvas.concat(matrix)
        val cellRect = Rect(0f, 0f, FasModel.CELL_W.toFloat(), FasModel.CELL_H.toFloat())
        d.faded(cellRect, (if (reduce) u else FasModel.smoothstep(0.0, 0.6, u)).toFloat()) {
            fasCell(this, k * FasModel.FRAME_STEP)
            val dark = if (reduce) 0.0 else 0.7 * FasModel.smoothstep(0.15, 1.3, abs(theta))
            if (dark > 0.01) drawRoundRect(Color.Black.copy(alpha = dark.toFloat()), Offset.Zero, cellRect.size, CornerRadius(3f, 3f))
        }
        canvas.restore()
    }
    // The magnifier over the chosen frame.
    val alpha = if (reduce) FasModel.clamp01(lift) else FasModel.smoothstep(0.4, 1.0, lift)
    val mag = if (reduce) 1.0 else 1.38
    val box = Rect(
        (cx - FasModel.CELL_W * mag / 2 - 3).toFloat(), (cy - FasModel.CELL_H * mag / 2 - 3).toFloat(),
        (cx + FasModel.CELL_W * mag / 2 + 3).toFloat(), (cy + FasModel.CELL_H * mag / 2 + 3).toFloat(),
    )
    d.drawPath(rrect(box, 8f), Color.White.copy(alpha = (0.95 * alpha).toFloat()), style = Stroke(2f))
}

/** One film cell, 64 × 82: black film, four sprocket holes along each edge, the picture. */
private fun fasCell(d: DrawScope, time: Double) {
    d.drawRoundRect(Color(0.06f, 0.06f, 0.07f), Offset.Zero, Size(FasModel.CELL_W.toFloat(), FasModel.CELL_H.toFloat()), CornerRadius(3f, 3f))
    for (j in 0 until 4) {
        val x = (6 + j * 14.5).toFloat()
        for (y in floatArrayOf(3f, (FasModel.CELL_H - 7.5).toFloat())) {
            d.drawRoundRect(Color.White.copy(alpha = 0.32f), Offset(x, y), Size(8.5f, 4.5f), CornerRadius(1.3f, 1.3f))
        }
    }
    val pic = Rect(FasModel.PIC_X.toFloat(), FasModel.PIC_Y.toFloat(), (FasModel.PIC_X + FasModel.PIC_W).toFloat(), (FasModel.PIC_Y + FasModel.PIC_H).toFloat())
    d.clipPath(rrect(pic, 2f)) { FasModel.drawScene(this, pic, time, true) }
}
