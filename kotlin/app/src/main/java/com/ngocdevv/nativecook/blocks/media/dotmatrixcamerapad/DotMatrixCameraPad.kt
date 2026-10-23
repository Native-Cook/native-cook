package com.ngocdevv.nativecook.blocks.media.dotmatrixcamerapad

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Dot Matrix Camera Pad — a manual camera screen whose controls are hardware-like pieces: a rubbery
 exposure wheel that turns into a glowing 11 × 8 dot-matrix pad (shutter across, ISO up), an A/M
 switch beside it, a warmth wheel, and a viewfinder showing a night street that answers every
 setting: exposure and ISO brighten it, slow shutters stretch the cars' lights into trails, high
 ISO adds grain, warmth tints it. A port of the SwiftUI block (DotMatrixCameraPad.swift); the
 numbers are the same.

 Look: black screen; viewfinder (16 dp margins, 22 dp corners, white corner brackets) over the top
 62 %; lens chips 13 / 24 / 77 MM (amber #F4C02A when chosen) at its foot. Readouts (label 12 sp,
 mono value 15 sp) above two modules: a 48 × 86 drum wheel (8 dp ridges on a cylinder, an amber
 centre line) with a 26 × 58 A/M pill (orange #F04A12 knob), and the same for warmth. A glossy
 orange #EE4313 shutter in a white ring, a gallery thumbnail and a chevron button at the bottom.

 Pad: dots 1.3 dp, swelling to 3.9 dp and brightening near the hot spot (Gaussian, σ 16 dp), the
 hot spot's row and column lit as a crosshair, the chosen dot 3.2 dp warm white, a 46 dp orange
 glow and a faint finger ring while pressed. Wheel ⇄ pad: the capsule widens 48 → 120 dp and its
 corners 15 → 18 dp, the drum fades over the first half and the dots ripple in from the left over
 the second.

 autoPlay timeline (LOOP = 10 s), every step easeInOut (cubic) unless noted:
   0.6 – 1.8   exposure wheel turns +0.0 → +0.6 (the street brightens)
   2.3 – 2.55  A → M;  2.4 – 3.0 the wheel opens into the pad
   3.1 – 3.4   the finger lands on 1/60 · ISO 400 (glow in)
   3.4 – 4.6   slides to 1/8 · ISO 100 (long light trails), holds to 5.0
   5.0 – 6.4   arcs to 1/2000 · ISO 6400 (frozen cars, grain), 6.4 – 7.2 back to 1/60 · 400
   7.2 – 7.6   lifts (glow out);  7.5 – 7.75 M → A;  7.6 – 8.2 the pad closes into the wheel
   8.2 – 9.0   exposure back to +0.0
   9.2         the shutter: press 0.25 s, the viewfinder blinks (0.36 s), the thumbnail pops
   9.6 – 10    rest (same frame as 0)
 The cars cross a whole number of times per loop, and the grain is a hash of the frame number.

 Touch: drag a wheel up or down (8 dp per step: 0.1 stop or 100 K) with a tick haptic per step;
 tap an A/M switch to swap wheel and pad (0.25 s knob, 0.6 s morph); drag on the pad to place the
 hot spot (a haptic per cell; glow in 0.15 s, out 0.4 s on release); tap a lens chip to zoom
 (0.45 s); tap the shutter to take a shot. 2 s after the last touch everything eases back to rest
 over 0.7 s and the loop starts again.
 Reduced motion: no loop; the street is still (no traffic, fixed grain); changes jump, and a shot
 only blinks.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private val AMBER = Color(0.96f, 0.75f, 0.16f)

private class DmcpTween(val from: Double, val to: Double, val at: Double, val dur: Double) {
    constructor(v: Double) : this(v, v, 0.0, 1.0)
    fun value(now: Double) = from + (to - from) * DmcpModel.easeInOut((now - at) / dur)
}

private enum class DmcpZone { NONE, WHEEL_L, PAD, TOGGLE_L, WHEEL_R, TOGGLE_R, SHUTTER, CHIP0, CHIP1, CHIP2 }

private class DmcpController(val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var appeared = nowSeconds()
    var autoStart by mutableStateOf<Double?>(null)
    var returningAt by mutableStateOf<Double?>(null)
    var returningFrom = DmcpState()
    // The manual state.
    var ev by mutableDoubleStateOf(0.0)
    var warmth by mutableStateOf(DmcpTween(5200.0))
    var knob by mutableStateOf(DmcpTween(0.0))
    var morph by mutableStateOf(DmcpTween(0.0))
    var knobR by mutableStateOf(DmcpTween(0.0))
    var glow by mutableStateOf(DmcpTween(0.0))
    var zoom by mutableStateOf(DmcpTween(1.0))
    var px by mutableDoubleStateOf(DmcpModel.REST_X)
    var py by mutableDoubleStateOf(DmcpModel.REST_Y)
    var captureAt by mutableStateOf<Double?>(null)
    var resume: Job? = null

    fun now() = if (reduced) nowSeconds() else frame
    fun clock() = if (reduced) 0.0 else frame - appeared

    fun shown(now: Double): DmcpState {
        val start = autoStart
        if (start != null && now >= start && !reduced) return DmcpModel.state(now - start)
        val r = returningAt
        if (r != null) return DmcpState.mix(returningFrom, DmcpState(), DmcpModel.easeInOut((now - r) / 0.7))
        var press = 0.0
        var pop = 1.0
        var flash = 0.0
        val c = captureAt
        if (c != null) {
            val d = now - c
            if (!reduced) {
                if (d >= 0 && d < 0.25) press = sin(PI * d / 0.25)
                if (d >= 0.05 && d < 0.5) pop = 0.82 + 0.18 * DmcpModel.easeOutBack((d - 0.05) / 0.4)
            }
            if (d >= 0.04 && d < 0.4) flash = 0.85 * (1 - (d - 0.04) / 0.36).pow(2)
        }
        return DmcpState(
            ev, warmth.value(now), knob.value(now), morph.value(now), knobR.value(now), px, py, glow.value(now),
            zoom.value(now), press, flash, pop,
        )
    }

    fun takeOver(now: Double) {
        resume?.cancel()
        val s = shown(now)
        ev = s.ev
        warmth = DmcpTween(s.warmth)
        knob = DmcpTween(s.knob)
        morph = DmcpTween(s.morph)
        knobR = DmcpTween(s.knobR)
        glow = DmcpTween(s.glow)
        zoom = DmcpTween(s.zoom)
        px = s.px
        py = s.py
        autoStart = null
        returningAt = null
    }

    fun tween(from: Double, to: Double, at: Double, dur: Double) = DmcpTween(from, to, at, if (reduced) 0.001 else dur)
}

@Composable
fun DotMatrixCameraPad(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { DmcpController(reduced) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    var blinking by remember { mutableStateOf(false) }

    LaunchedEffect(c) {
        c.appeared = nowSeconds()
        if (autoPlay && !reduced) c.autoStart = c.appeared
        if (!reduced) while (true) withFrameNanos { c.frame = it / 1e9 }
    }
    // Under reduced motion the clock only runs for a shot's blink.
    LaunchedEffect(blinking) {
        if (blinking) {
            val end = nowSeconds() + 0.6
            while (nowSeconds() < end) withFrameNanos { c.frame = it / 1e9 }
            blinking = false
        }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val now = c.now()
            c.returningFrom = c.shown(now)
            c.returningAt = now
            c.autoStart = now + 0.7
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val density = LocalDensity.current.density
        val layout = remember(width, height, top, bottomInset) { DmcpModel.Layout(width, height, top, bottomInset) }

        fun s(): DmcpState = c.shown(if (reduced) (if (blinking) c.frame else nowSeconds()) else c.frame)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    val st = c.shown(nowSeconds())
                    contentDescription = "Camera. Exposure ${DmcpModel.evLabel(st.ev)}, shutter " +
                        "${DmcpModel.shutterLabels[(st.px * 10).roundToInt().coerceIn(0, 10)]}, ISO " +
                        "${DmcpModel.isoLabels[(st.py * 7).roundToInt().coerceIn(0, 7)]}, warmth ${st.warmth.roundToInt()} kelvin"
                }
                .pointerInput(c, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now0 = c.now()
                        c.takeOver(now0)
                        val p0 = down.position / density
                        val st = c.shown(now0)
                        fun hit(r: Rect, pad: Float = 8f) = r.inflate(pad).contains(p0)
                        val sc = layout.shutterCenter
                        var zone = when {
                            hit(layout.leftToggle(st.morph), 6f) -> DmcpZone.TOGGLE_L
                            hit(layout.rightToggle, 6f) -> DmcpZone.TOGGLE_R
                            hit(layout.leftCapsule(st.morph)) -> if (st.morph >= 0.5) DmcpZone.PAD else DmcpZone.WHEEL_L
                            hit(layout.rightCapsule) -> DmcpZone.WHEEL_R
                            hit(Rect(sc.x - 42, sc.y - 32, sc.x + 42, sc.y + 32)) -> DmcpZone.SHUTTER
                            else -> DmcpZone.NONE
                        }
                        if (zone == DmcpZone.NONE) {
                            for (i in 0 until 3) {
                                val cx = layout.chipX(i).toFloat()
                                val cy = layout.chipsY.toFloat()
                                if (hit(Rect(cx - 24, cy - 18, cx + 24, cy + 18), 0f)) zone = DmcpZone.entries[DmcpZone.CHIP0.ordinal + i]
                            }
                        }
                        val startEv = c.ev
                        val startWarmth = c.warmth.value(now0)
                        var last = when (zone) {
                            DmcpZone.WHEEL_L -> (c.ev * 10).roundToInt()
                            DmcpZone.WHEEL_R -> (startWarmth / 100).roundToInt()
                            else -> -1
                        }
                        fun step(v: Int) {
                            if (v != last) {
                                last = v
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        fun padAt(p: Offset) {
                            val r = layout.leftCapsule(1.0).deflate(10f)
                            c.px = DmcpModel.clamp01(((p.x - r.left) / r.width).toDouble())
                            c.py = DmcpModel.clamp01(((r.bottom - p.y) / r.height).toDouble())
                            step((c.px * 10).roundToInt() * 10 + (c.py * 7).roundToInt())
                        }
                        if (zone == DmcpZone.PAD) {
                            c.glow = c.tween(c.glow.value(now0), 1.0, now0, 0.15)
                            padAt(p0)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val p = change.position / density
                            val dy = (p.y - p0.y).toDouble()
                            when (zone) {
                                DmcpZone.WHEEL_L -> {
                                    change.consume()
                                    c.ev = min(2.0, max(-2.0, startEv - dy / 80))
                                    step((c.ev * 10).roundToInt())
                                }
                                DmcpZone.WHEEL_R -> {
                                    change.consume()
                                    val w = min(9000.0, max(2500.0, startWarmth - dy / 8 * 100))
                                    c.warmth = DmcpTween(w)
                                    val now = c.now()
                                    if (c.knobR.to < 1) c.knobR = c.tween(c.knobR.value(now), 1.0, now, 0.25)
                                    step((w / 100).roundToInt())
                                }
                                DmcpZone.PAD -> {
                                    change.consume()
                                    padAt(p)
                                }
                                else -> {}
                            }
                        }
                        val now = c.now()
                        when (zone) {
                            DmcpZone.WHEEL_L -> c.ev = (c.ev * 10).roundToInt() / 10.0
                            DmcpZone.WHEEL_R -> c.warmth = DmcpTween((c.warmth.to / 100).roundToInt() * 100.0)
                            DmcpZone.PAD -> c.glow = c.tween(c.glow.value(now), 0.0, now, 0.4)
                            DmcpZone.TOGGLE_L -> {
                                val to = if (c.knob.to < 0.5) 1.0 else 0.0
                                c.knob = c.tween(c.knob.value(now), to, now, 0.25)
                                c.morph = c.tween(c.morph.value(now), to, now + if (reduced) 0.0 else 0.1, 0.6)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            DmcpZone.TOGGLE_R -> {
                                val to = if (c.knobR.to < 0.5) 1.0 else 0.0
                                c.knobR = c.tween(c.knobR.value(now), to, now, 0.25)
                                if (to == 0.0) c.warmth = c.tween(c.warmth.value(now), 5200.0, now, 0.5)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            DmcpZone.SHUTTER -> {
                                c.captureAt = now
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                if (reduced) blinking = true
                            }
                            DmcpZone.CHIP0, DmcpZone.CHIP1, DmcpZone.CHIP2 -> {
                                val i = zone.ordinal - DmcpZone.CHIP0.ordinal
                                c.zoom = c.tween(c.zoom.value(now), DmcpModel.lensZooms[i], now, 0.45)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            DmcpZone.NONE -> {}
                        }
                        scheduleResume()
                    }
                },
        ) {
            val st = s()
            withTransform({ scale(density, density, Offset.Zero) }) {
                dmcpScreen(this, layout, st, c.clock(), measurer)
            }
        }

        // Readouts.
        Readout("Exposure", { DmcpModel.evLabel(s().ev) }, false, Modifier.centerAt(density) {
            val st = s()
            Triple(layout.leftCapsule(st.morph).center.x.toDouble(), layout.readoutY + 18, 1 - DmcpModel.smoothstep(0.0, 0.5, st.morph))
        })
        Readout("Shutter", { DmcpModel.shutterLabels[(s().px * 10).roundToInt().coerceIn(0, 10)] }, true, Modifier.centerAt(density) {
            val st = s()
            Triple(layout.leftCapsule(st.morph).left + 30.0, layout.readoutY + 18, DmcpModel.smoothstep(0.5, 1.0, st.morph))
        })
        Readout("ISO", { DmcpModel.isoLabels[(s().py * 7).roundToInt().coerceIn(0, 7)] }, true, Modifier.centerAt(density) {
            val st = s()
            Triple(layout.leftCapsule(st.morph).left + 92.0, layout.readoutY + 18, DmcpModel.smoothstep(0.5, 1.0, st.morph))
        })
        val warmAmber by remember(c) { derivedStateOf { s().knobR > 0.5 } }
        Readout("Warmth", { "${s().warmth.roundToInt()}K" }, warmAmber, Modifier.centerAt(density) {
            Triple(layout.rightCapsule.center.x.toDouble(), layout.readoutY + 18, 1.0)
        })

        // Top bar.
        val mono = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Canvas(Modifier.centerAt(density) { Triple(24.0 + 7, top + 22, 1.0) }.size(14.dp, 20.dp)) { dmcpBolt(this) }
        Canvas(Modifier.centerAt(density) { Triple(24.0 + 14 + 20 + 9, top + 22, 1.0) }.size(18.dp, 18.dp)) { dmcpGrid(this) }
        BasicText("30 FPS", style = mono, modifier = Modifier.centerAt(density, alignRight = true) { Triple(width - 24, top + 22, 1.0) })
        BasicText("HD", style = mono, modifier = Modifier.centerAt(density, alignRight = true) { Triple(width - 24 - 47 - 20, top + 22, 1.0) })

        // Lens chips.
        val zoomIndex by remember(c) {
            derivedStateOf { DmcpModel.lensZooms.indexOfFirst { abs(s().zoom - it) < 0.05 } }
        }
        for (i in 0 until 3) {
            val color = if (zoomIndex == i) Color(0.957f, 0.753f, 0.165f) else Color.White
            Column(
                Modifier.centerAt(density) { Triple(layout.chipX(i), layout.chipsY, 1.0) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(DmcpModel.lensLabels[i], style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                BasicText("MM", style = TextStyle(color = color, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
            }
        }
        Canvas(Modifier.centerAt(density) { Triple(layout.chipX(3), layout.chipsY, 1.0) }.size(16.dp, 18.dp)) { dmcpPerson(this) }
    }
}

/**
 * Places a child with its centre (or, with [alignRight], its right edge's middle) at a point in dp,
 * read at layout time, and fades it by the third value.
 */
private fun Modifier.centerAt(density: Float, alignRight: Boolean = false, at: () -> Triple<Double, Double, Double>): Modifier =
    this
        .graphicsLayer { alpha = at().third.toFloat().coerceIn(0f, 1f) }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val (x, y, _) = at()
                val left = if (alignRight) x * density - placeable.width else x * density - placeable.width / 2.0
                placeable.place(IntOffset(left.roundToInt(), (y * density - placeable.height / 2.0).roundToInt()))
            }
        }

@Composable
private fun Readout(label: String, value: () -> String, amber: Boolean, modifier: Modifier) {
    val text by remember { derivedStateOf(value) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(label, maxLines = 1, softWrap = false, style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium))
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        BasicText(
            text, maxLines = 1, softWrap = false,
            style = TextStyle(color = if (amber) AMBER else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
        )
    }
}

// MARK: Icons

private fun dmcpBolt(d: DrawScope) {
    val w = d.size.width
    val h = d.size.height
    val p = Path().apply {
        moveTo(w * 0.62f, 0f); lineTo(0f, h * 0.58f); lineTo(w * 0.48f, h * 0.58f)
        lineTo(w * 0.38f, h); lineTo(w, h * 0.42f); lineTo(w * 0.52f, h * 0.42f); close()
    }
    d.drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(1.6f * d.density, join = StrokeJoin.Round))
}

private fun dmcpGrid(d: DrawScope) {
    val w = d.size.width
    val h = d.size.height
    for (k in 1..2) {
        val f = k / 3f
        d.drawLine(Color.White.copy(alpha = 0.9f), Offset(w * f, 0f), Offset(w * f, h), 1.6f * d.density, StrokeCap.Round)
        d.drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, h * f), Offset(w, h * f), 1.6f * d.density, StrokeCap.Round)
    }
}

private fun dmcpPerson(d: DrawScope) {
    val w = d.size.width
    val h = d.size.height
    val p = Path().apply {
        addOval(Rect(w * 0.28f, 0f, w * 0.72f, w * 0.44f))
        moveTo(0f, h)
        cubicTo(0f, h * 0.45f, w, h * 0.45f, w, h)
    }
    d.drawPath(p, Color.White, style = Stroke(1.5f * d.density, cap = StrokeCap.Round))
}

// MARK: Drawing (in dp)

private fun rgb(r: Double, g: Double, b: Double, a: Double = 1.0) =
    Color(r.toFloat().coerceIn(0f, 1f), g.toFloat().coerceIn(0f, 1f), b.toFloat().coerceIn(0f, 1f), a.toFloat().coerceIn(0f, 1f))

private fun rrect(r: Rect, corner: Float) = Path().apply { addRoundRect(RoundRect(r, CornerRadius(corner, corner))) }

private fun dmcpScreen(d: DrawScope, l: DmcpModel.Layout, s: DmcpState, clock: Double, measurer: TextMeasurer) {
    d.drawRect(Color.Black, size = Size(l.w.toFloat(), l.h.toFloat()))
    // Viewfinder.
    val vf = l.viewfinder
    d.clipPath(rrect(vf, 22f)) { dmcpStreet(this, vf, s, clock) }
    dmcpBrackets(d, vf)
    // Behind the lens chips.
    d.drawRoundRect(
        Color.Black.copy(alpha = 0.32f), Offset((l.w / 2 - 122).toFloat(), (l.chipsY - 22).toFloat()), Size(244f, 44f), CornerRadius(22f, 22f),
    )

    // Left module.
    val cap = l.leftCapsule(s.morph)
    val corner = (15 + 3 * DmcpModel.clamp01(s.morph)).toFloat()
    val wheelA = 1 - DmcpModel.smoothstep(0.0, 0.5, s.morph)
    dmcpCapsule(d, cap, corner, s.morph)
    if (wheelA > 0.01) dmcpDrum(d, cap, s.ev * 10, true, wheelA)
    if (s.morph > 0.5) d.clipPath(rrect(cap, corner)) { dmcpPad(this, cap, s) }
    dmcpToggle(d, l.leftToggle(s.morph), s.knob, measurer)
    // Right module.
    dmcpCapsule(d, l.rightCapsule, 15f, 0.0)
    dmcpDrum(d, l.rightCapsule, (s.warmth - 5200) / 100, s.knobR > 0.5, 1.0)
    dmcpToggle(d, l.rightToggle, s.knobR, measurer)

    // Bottom row.
    dmcpShutter(d, l.shutterCenter, s.press)
    val gr = l.gallery
    d.withTransform({ scale(s.pop.toFloat(), s.pop.toFloat(), gr.center) }) {
        clipPath(rrect(gr, 10f)) { dmcpStreet(this, Rect(gr.left - 6, gr.top - 10, gr.left + 54, gr.top + 62), DmcpState(), 0.0) }
        drawPath(rrect(gr, 10f), Color.White.copy(alpha = 0.18f), style = Stroke(1f))
    }
    val cr = l.chevron
    d.drawCircle(rgb(0.11, 0.11, 0.12), cr.width / 2, cr.center)
    val chev = Path().apply {
        moveTo(cr.center.x - 7, cr.center.y + 3); lineTo(cr.center.x, cr.center.y - 4); lineTo(cr.center.x + 7, cr.center.y + 3)
    }
    d.drawPath(chev, Color.White, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun dmcpBrackets(d: DrawScope, r: Rect) {
    val i = 12f
    val a = 18f
    val p = Path()
    for (corner in listOf(
        floatArrayOf(r.left + i, r.top + i, 1f, 1f), floatArrayOf(r.right - i, r.top + i, -1f, 1f),
        floatArrayOf(r.left + i, r.bottom - i, 1f, -1f), floatArrayOf(r.right - i, r.bottom - i, -1f, -1f),
    )) {
        val (x, y, sx, sy) = corner
        p.moveTo(x, y + sy * a); p.lineTo(x, y); p.lineTo(x + sx * a, y)
    }
    d.drawPath(p, Color.White.copy(alpha = 0.85f), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The module's body: a rubber capsule, turning into dark glass as it becomes the pad. */
private fun dmcpCapsule(d: DrawScope, r: Rect, corner: Float, glass: Double) {
    val path = rrect(r, corner)
    d.drawPath(
        path,
        Brush.verticalGradient(
            0f to rgb(0.07, 0.07, 0.08), 0.5f to rgb(0.17, 0.17, 0.18), 1f to rgb(0.07, 0.07, 0.08), startY = r.top, endY = r.bottom,
        ),
    )
    if (glass > 0.01) {
        d.drawPath(path, Brush.linearGradient(listOf(rgb(0.20, 0.17, 0.14, glass), rgb(0.06, 0.06, 0.07, glass)), r.topLeft, r.bottomRight))
    }
    d.drawPath(path, Color.White.copy(alpha = 0.08f), style = Stroke(1f))
}

/** Ridges on a drum: 8 dp apart along the cylinder, foreshortened and fading towards its rim. */
private fun dmcpDrum(d: DrawScope, r: Rect, offset: Double, amber: Boolean, alpha: Double) {
    val radius = r.height / 2 * 1.05
    val base = floor(offset).toInt()
    for (k in (base - 9)..(base + 9)) {
        val y = (k - offset) * 8
        val theta = y / radius
        if (abs(theta) > 1.45) continue
        val cs = cos(theta)
        val yy = (r.center.y + sin(theta) * radius * 0.92).toFloat()
        val major = k % 5 == 0
        val inset = if (major) 8f else 12f
        d.drawLine(
            Color.White.copy(alpha = (alpha * if (major) 0.3 + 0.6 * cs else 0.12 + 0.42 * cs).toFloat()),
            Offset(r.left + inset, yy), Offset(r.right - inset, yy),
            ((if (major) 1.6 else 1.2) * (0.4 + 0.6 * cs)).toFloat(), StrokeCap.Round,
        )
    }
    val color = if (amber) AMBER.copy(alpha = alpha.toFloat()) else Color.White.copy(alpha = (0.8 * alpha).toFloat())
    d.drawLine(color, Offset(r.left + 7, r.center.y), Offset(r.right - 7, r.center.y), 2.2f, StrokeCap.Round)
    // A sheen along the drum's top.
    val inner = r.deflate(3f)
    d.drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = (0.07 * alpha).toFloat()), Color.Transparent), startY = r.top, endY = r.center.y),
        inner.topLeft, inner.size, CornerRadius(12f, 12f),
    )
}

/** The dot matrix: 11 columns of shutter speeds by 8 rows of ISO. */
private fun dmcpPad(d: DrawScope, r: Rect, s: DmcpState) {
    val reveal = DmcpModel.clamp01((s.morph - 0.5) * 2)
    val inner = r.deflate(10f)
    val sx = inner.width / (DmcpModel.COLS - 1)
    val sy = inner.height / (DmcpModel.ROWS - 1)
    val hx = inner.left + s.px * inner.width
    val hy = inner.bottom - s.py * inner.height
    val ci = (s.px * 10).roundToInt()
    val ri = (s.py * 7).roundToInt()
    val glow = s.glow
    val hot = Offset(hx.toFloat(), hy.toFloat())
    if (glow > 0.01) {
        d.drawCircle(
            Brush.radialGradient(listOf(rgb(0.98, 0.45, 0.10, 0.45 * glow * reveal), rgb(0.98, 0.45, 0.10, 0.0)), center = hot, radius = 46f),
            46f, hot,
        )
    }
    for (col in 0 until DmcpModel.COLS) {
        // Columns ripple in from the left.
        val a = DmcpModel.smoothstep(0.0, 1.0, reveal * 1.6 - col.toDouble() / (DmcpModel.COLS - 1) * 0.6)
        if (a < 0.01) continue
        for (row in 0 until DmcpModel.ROWS) {
            val x = inner.left + col * sx
            val y = inner.bottom - row * sy
            val d2 = (x - hx) * (x - hx) + (y - hy) * (y - hy)
            val g = exp(-d2 / (2 * 16 * 16)) * glow
            val line = if (col == ci || row == ri) 0.16 + 0.3 * glow else 0.0
            var radius = 1.3 + 2.6 * g + (if (line > 0) 0.3 else 0.0)
            var color = rgb(0.96, 0.60, 0.18, a * min(1.0, 0.2 + line + 0.8 * g))
            if (col == ci && row == ri) {
                radius = 3.2 + 0.7 * glow
                color = rgb(1.0, 0.86, 0.58, a * (0.75 + 0.25 * glow))
            }
            d.drawCircle(color, radius.toFloat(), Offset(x, y))
        }
    }
    if (glow > 0.01) d.drawCircle(Color.White.copy(alpha = (0.28 * glow * reveal).toFloat()), 15f, hot, style = Stroke(1.5f))
}

private fun dmcpToggle(d: DrawScope, r: Rect, knob: Double, measurer: TextMeasurer) {
    d.drawRoundRect(rgb(0.11, 0.11, 0.12), r.topLeft, r.size, CornerRadius(13f, 13f))
    val k = DmcpModel.clamp01(knob)
    d.drawCircle(rgb(0.94, 0.29, 0.07), 11f, Offset(r.center.x, (r.top + 15 + 28 * k).toFloat()))
    for ((i, letter) in listOf("A", "M").withIndex()) {
        val on = if (i == 0) 1 - k else k
        val layout = measurer.measure(
            letter,
            TextStyle(
                color = Color.White.copy(alpha = (0.5 + 0.5 * on).toFloat()), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            ),
            density = androidx.compose.ui.unit.Density(1f),
        )
        d.drawText(layout, topLeft = Offset(r.center.x - layout.size.width / 2f, r.top + 15 + 28 * i - layout.size.height / 2f))
    }
}

private fun dmcpShutter(d: DrawScope, c: Offset, press: Double) {
    val k = (1 - 0.08 * press).toFloat()
    d.withTransform({ scale(k, k, c) }) {
        val ring = Rect(c.x - 42, c.y - 32, c.x + 42, c.y + 32).deflate(1.5f)
        drawPath(rrect(ring, 23f), Color.White, style = Stroke(3f))
        val body = Rect(c.x - 35, c.y - 25, c.x + 35, c.y + 25)
        val path = rrect(body, 17f)
        drawPath(path, Brush.verticalGradient(listOf(rgb(1.0, 0.38, 0.14), rgb(0.88, 0.22, 0.05)), startY = body.top, endY = body.bottom))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), startY = c.y - 21, endY = c.y - 5),
            Offset(c.x - 27, c.y - 21), Size(54f, 16f), CornerRadius(8f, 8f),
        )
        drawPath(path, Color.Black.copy(alpha = (0.18 * press).toFloat()))
    }
}

// MARK: The night street

private fun dmcpStreet(d: DrawScope, r: Rect, s: DmcpState, clock: Double) {
    val w = r.width.toDouble()
    val h = r.height.toDouble()
    fun px(u: Double) = (r.left + u * w).toFloat()
    fun py(v: Double) = (r.top + v * h).toFloat()
    fun P(u: Double, v: Double) = Offset(px(u), py(v))
    fun R(u: Double, v: Double, uw: Double, vh: Double) = Rect(px(u), py(v), px(u + uw), py(v + vh))
    val shutter = DmcpModel.shutter(s.px)
    val iso = DmcpModel.iso(s.py)
    val full = R(-0.4, -0.4, 1.8, 1.8)

    d.withTransform({ scale(s.zoom.toFloat(), s.zoom.toFloat(), r.center) }) {
        // Sky, a glow over the city.
        drawRect(
            Brush.verticalGradient(listOf(rgb(0.04, 0.05, 0.13), rgb(0.12, 0.08, 0.20), rgb(0.30, 0.14, 0.22)), startY = py(0.0), endY = py(DmcpModel.HORIZON)),
            full.topLeft, full.size,
        )
        // Buildings with lit windows.
        for ((b, bld) in DmcpModel.buildings.withIndex()) {
            val x0 = bld[0]
            val bw = bld[1]
            val bh = bld[2]
            val top = DmcpModel.HORIZON - bh
            val br = R(x0, top, bw, bh + 0.05)
            drawRect(rgb(0.05, 0.05, 0.10), br.topLeft, br.size)
            var i = 0
            var wy = top + 0.03
            while (wy < DmcpModel.HORIZON - 0.03) {
                var wx = x0 + 0.018
                var j = 0
                while (wx < x0 + bw - 0.025) {
                    val hsh = DmcpModel.hash(b, i, j)
                    if (hsh > 0.58) {
                        val cool = DmcpModel.hash(j, b, i) > 0.82
                        val wr = R(wx, wy, 0.018, 0.022)
                        drawRect(if (cool) rgb(0.65, 0.82, 1.0, 0.75) else rgb(1.0, 0.76, 0.42, 0.55 + 0.4 * hsh), wr.topLeft, wr.size)
                    }
                    wx += 0.034
                    j += 1
                }
                wy += 0.046
                i += 1
            }
        }
        // Pavement and road.
        R(-0.4, DmcpModel.HORIZON, 1.8, 0.8).let { drawRect(rgb(0.06, 0.06, 0.08), it.topLeft, it.size) }
        R(-0.4, DmcpModel.HORIZON + 0.035, 1.8, 0.008).let { drawRect(rgb(0.18, 0.16, 0.20), it.topLeft, it.size) }
        var dash = -0.4
        while (dash < 1.4) {
            R(dash, 0.806, 0.06, 0.006).let { drawRect(rgb(0.55, 0.52, 0.45, 0.45), it.topLeft, it.size) }
            dash += 0.12
        }
        // Street lamps: poles, bokeh, and their streaks on the wet road.
        for (lamp in DmcpModel.lamps) {
            val lx = lamp[0]
            val ly = lamp[1]
            R(lx - 0.003, ly, 0.006, DmcpModel.HORIZON + 0.04 - ly).let { drawRect(rgb(0.12, 0.11, 0.15), it.topLeft, it.size) }
            R(lx - 0.03, DmcpModel.HORIZON + 0.05, 0.06, 0.3).let {
                drawRect(
                    Brush.verticalGradient(listOf(rgb(1.0, 0.70, 0.36, 0.22), rgb(1.0, 0.70, 0.36, 0.0)), startY = py(DmcpModel.HORIZON + 0.05), endY = py(DmcpModel.HORIZON + 0.35)),
                    it.topLeft, it.size,
                )
            }
            val glowR = (0.075 * w).toFloat()
            drawCircle(Brush.radialGradient(listOf(rgb(1.0, 0.74, 0.40, 0.65), rgb(1.0, 0.6, 0.3, 0.0)), center = P(lx, ly), radius = glowR), glowR, P(lx, ly))
            drawCircle(rgb(1.0, 0.95, 0.82), 3f, P(lx, ly))
        }
        // Cars, their lights dragged into trails by slow shutters.
        val trail0 = shutter * 14
        for (car in DmcpModel.cars) {
            val lane = car[0].toInt()
            val n = car[1]
            val dir = if (lane == 0) 1.0 else -1.0
            var f = (n * clock / DmcpModel.LOOP + car[2]) % 1.0
            if (f < 0) f += 1
            val x = if (lane == 0) -0.2 + 1.4 * f else 1.2 - 1.4 * f
            val y = DmcpModel.laneY[lane]
            // Each car's trail is its speed times the shutter time (exaggerated 14 ×).
            val trail = min(0.8, trail0 * n * 1.4 / DmcpModel.LOOP)
            val ghost = 1 - 0.75 * DmcpModel.smoothstep(0.02, 0.2, shutter)
            val bodyLen = 0.16
            val back = x - dir * bodyLen
            val left = min(x, back)
            R(left, y - 0.034, bodyLen, 0.036).let { drawRoundRect(rgb(0.12, 0.12, 0.16, ghost), it.topLeft, it.size, CornerRadius(5f, 5f)) }
            R(left + (if (dir > 0) 0.035 else 0.045), y - 0.062, bodyLen - 0.08, 0.032).let {
                drawRoundRect(rgb(0.10, 0.10, 0.14, ghost), it.topLeft, it.size, CornerRadius(6f, 6f))
            }
            R(left + (if (dir > 0) 0.042 else 0.052), y - 0.056, bodyLen - 0.094, 0.02).let {
                drawRoundRect(rgb(0.30, 0.30, 0.38, 0.6 * ghost), it.topLeft, it.size, CornerRadius(3f, 3f))
            }
            // Headlight at the front (white), tail light at the back (red).
            for (light in listOf(doubleArrayOf(x, 1.0, 0.95, 0.85, 3.4), doubleArrayOf(back, 1.0, 0.16, 0.10, 3.0))) {
                val lx = light[0]
                val start = P(lx, y - 0.012)
                val end = P(lx - dir * trail, y - 0.012)
                val col = rgb(light[1], light[2], light[3])
                val width = light[4].toFloat()
                drawLine(Brush.linearGradient(listOf(col.copy(alpha = 0f), col.copy(alpha = 0.28f)), end, start), end, start, width * 3, StrokeCap.Round)
                drawLine(Brush.linearGradient(listOf(col.copy(alpha = 0f), col.copy(alpha = 0.95f)), end, start), end, start, width, StrokeCap.Round)
            }
        }
        // Exposure: darken below the resting brightness, lift above it.
        val e = min(3.0, max(-3.0, DmcpModel.exposure(s)))
        if (e < 0) {
            drawRect(Color.Black.copy(alpha = (1 - 2.0.pow(e * 0.8)).toFloat()), full.topLeft, full.size)
        } else if (e > 0) {
            val k = 1 - 2.0.pow(-e * 0.8)
            drawRect(rgb(0.42 * k, 0.36 * k, 0.40 * k), full.topLeft, full.size, blendMode = BlendMode.Plus)
        }
        // Warmth.
        val t = min(1.0, max(-1.0, (s.warmth - 5200) / 2600))
        if (t > 0) drawRect(rgb(1.0, 0.55, 0.2, 0.16 * t), full.topLeft, full.size)
        if (t < 0) drawRect(rgb(0.25, 0.5, 1.0, 0.16 * -t), full.topLeft, full.size)
        // Grain, a hash of the frame number (24 a second).
        val count = (iso / 12800 * 700).toInt()
        val frame = floor(clock * 24).toInt() % 240
        for (i in 0 until count) {
            val gx = DmcpModel.hash(frame, i, 7)
            val gy = DmcpModel.hash(i, frame, 3)
            val bright = DmcpModel.hash(frame, 11, i) > 0.5
            drawRect(if (bright) Color.White.copy(alpha = 0.36f) else Color.Black.copy(alpha = 0.45f), P(gx, gy), Size(1.8f, 1.8f))
        }
    }
    // Vignette and the shot's blink.
    d.drawRect(
        Brush.radialGradient(
            (0.3f / 0.8f) to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), center = r.center, radius = r.height * 0.8f,
        ),
        r.topLeft, r.size,
    )
    if (s.flash > 0.001) d.drawRect(Color.Black.copy(alpha = s.flash.toFloat()), r.topLeft, r.size)
}
