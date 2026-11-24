package com.ngocdevv.nativecook.blocks.modals.coachmarkspotlight

// The reusable piece: a first-run tour over any screen. A dim layer with one soft cutout that morphs from
// target to target (one path driven by one progress value), and a tooltip bubble that slides after it,
// re-points its arrow, and carries step dots, Skip and Next. Next, a tap on the highlighted element or a
// swipe of the tooltip moves the tour; Skip / Done dismisses it.

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

internal val BUBBLE = Color(0xFFFFFDF9)
private val INK = Color(0xFF1E2420)
private val MUTE = Color(0xFF6E746F)
private val ACCENT = Color(0xFF2F7D5B)
internal const val NEXT_W = 84f
internal const val SKIP_W = 56f
internal const val FOOT_H = 36f
internal const val TIP_PAD = 18f

class CoachMotion {
    /** Fractional step of the cutout: 0 first target, 1 second, … */
    internal val p = CmsValue(0f)
    /** The tooltip's own copy of p, a beat behind (follow-through). */
    internal val tipP = CmsValue(0f)
    /** Swipe offset of the tooltip, dp. */
    internal val tipX = CmsValue(0f, eps = 0.05f)
    /** Dim layer and ring: 0 hidden, 1 shown. */
    internal val shown = CmsValue(1f)
    /** Tooltip: 0 hidden (scaled down), 1 shown. */
    internal val tipShow = CmsValue(1f)
    /** 1 = the cutout is a circle larger than the screen around the first target (the tour opening). */
    internal val iris = CmsValue(0f)
    /** The ring breathing out of the cutout: phase 0..1 and how much of it shows. */
    internal val pulse = CmsValue(0f)
    internal val pulseGate = CmsValue(0f)
    /** Press feedback on Next, Skip and the highlighted element (0..1). */
    internal val nextPress = CmsValue(0f)
    internal val skipPress = CmsValue(0f)
    internal val targetPress = CmsValue(0f)
    /** The step a finger asked for. */
    internal var step = 0
    /** 1 while the tour takes touches (shown), 0 lets them through to the app. */
    internal var visible = 1
    /** 1 while a script poses the tour; the first touch hands it to the finger. */
    internal var driven = 0
    internal var held = 0
    internal var touches = 0
    /** Reduce Motion: cuts and fades instead of travel. */
    internal var calm = 0
    internal var haptic: () -> Unit = {}

    internal val values = listOf(p, tipP, tipX, shown, tipShow, iris, pulse, pulseGate, nextPress, skipPress, targetPress)

    internal fun onAnimationStart(wake: () -> Unit) {
        values.forEach { it.onStart = wake }
    }

    /** Steps every running animation; true while any still runs. */
    internal fun advance(dt: Float): Boolean {
        var any = false
        for (v in values) {
            v.step(dt)
            if (v.active) any = true
        }
        return any
    }
}

class TipPlace(val x: Float, val y: Float, val arrowX: Float, val below: Float)

/** Where the tooltip sits for each target: under it in the top half of the screen, above it otherwise. */
fun tipPlaces(targets: List<CmsBox>, width: Float, height: Float, tipW: Float): List<TipPlace> = targets.map { b ->
    val cx = b.x + b.w / 2f
    val below = b.y + b.h / 2f < height / 2f
    val x = min(max(cx - tipW / 2f, 16f), width - 16f - tipW)
    val y = if (below) b.y + b.h + TIP_GAP + ARROW_H else b.y - TIP_GAP - ARROW_H - TIP_H
    val arrowX = min(max(cx - x, 28f), tipW - 28f)
    TipPlace(x, y, arrowX, if (below) 1f else 0f)
}

private fun springTo(value: CmsValue, to: Float, s: CmsSpring, calm: Int, velocity: Float, delay: Float) {
    if (calm == 1) {
        value.set(to)
        return
    }
    value.spring(to, s, velocity, delay)
}

/** Dismisses the tour (Skip, Done): the dim lifts, the tooltip shrinks away, touches reach the app. */
fun hideTour(m: CoachMotion) {
    val calm = m.calm
    m.shown.timing(0f, if (calm == 1) 0.2f else 0.36f, easing = ::easeInOutCubic)
    m.tipShow.timing(0f, if (calm == 1) 0.16f else 0.24f, easing = ::easeInCubic)
    springTo(m.tipX, 0f, DRAG_SPRING, calm, 0f, 0f)
    m.visible = 0
}

/** Moves the tour to step s (past the last one dismisses it); the tooltip follows TIP_LAG later. */
fun goToStep(m: CoachMotion, s: Int, count: Int, velocity: Float) {
    if (s < 0) {
        springTo(m.tipX, 0f, DRAG_SPRING, m.calm, velocity, 0f)
        return
    }
    if (s >= count) {
        hideTour(m)
        m.haptic()
        return
    }
    val calm = m.calm
    m.step = s
    springTo(m.p, s.toFloat(), MORPH_SPRING, calm, 0f, 0f)
    springTo(m.tipP, s.toFloat(), TIP_SPRING, calm, 0f, TIP_LAG)
    springTo(m.tipX, 0f, DRAG_SPRING, calm, velocity, 0f)
    m.haptic()
}

/** Opens the tour from the start: the dim fades in while the cutout irises down onto the first target. */
fun openTour(m: CoachMotion) {
    val calm = m.calm
    m.step = 0
    m.visible = 1
    m.tipX.set(0f)
    if (m.shown.get() < 0.5f) {
        m.p.set(0f)
        m.tipP.set(0f)
        m.iris.set(if (calm == 1) 0f else 1f)
        m.shown.timing(1f, 0.4f, easing = ::easeOutCubic)
        if (calm == 0) m.iris.spring(0f, IRIS_SPRING)
        m.tipShow.timing(1f, 0.4f, delaySeconds = if (calm == 1) 0f else 0.5f, easing = ::easeOutCubic)
    } else {
        springTo(m.p, 0f, MORPH_SPRING, calm, 0f, 0f)
        springTo(m.tipP, 0f, TIP_SPRING, calm, 0f, TIP_LAG)
        m.shown.timing(1f, 0.2f)
        m.tipShow.timing(1f, 0.2f)
        m.iris.timing(0f, 0.2f)
    }
}

/** A script let go: the tour settles on the step nearest to where it was, or stays dismissed. */
fun adoptTour(m: CoachMotion, count: Int) {
    if (m.driven == 0) return
    m.driven = 0
    val calm = m.calm
    m.nextPress.timing(0f, 0.12f)
    m.skipPress.timing(0f, 0.12f)
    m.targetPress.timing(0f, 0.12f)
    if (m.shown.get() < 0.5f || m.tipShow.get() < 0.5f) {
        hideTour(m)
        return
    }
    val s = m.p.get().roundToInt().coerceIn(0, count - 1)
    m.step = s
    m.visible = 1
    springTo(m.p, s.toFloat(), MORPH_SPRING, calm, 0f, 0f)
    springTo(m.tipP, s.toFloat(), TIP_SPRING, calm, 0f, 0f)
    springTo(m.tipX, 0f, DRAG_SPRING, calm, 0f, 0f)
    m.shown.timing(1f, 0.16f)
    m.tipShow.timing(1f, 0.16f)
    m.iris.timing(0f, 0.16f)
}

/** True once the tour rests on its first step, fully shown and still. */
internal fun tourAtRest(m: CoachMotion): Boolean =
    abs(m.p.get()) < 0.003f &&
        abs(m.tipP.get()) < 0.003f &&
        m.shown.get() > 0.997f &&
        m.tipShow.get() > 0.997f &&
        abs(m.iris.get()) < 0.003f &&
        abs(m.tipX.get()) < 0.5f

private fun grab(m: CoachMotion, count: Int) {
    m.touches += 1
    m.held = 1
    adoptTour(m, count)
}

private fun letGo(m: CoachMotion) {
    m.held = 0
}

/** Swipe resistance: free in a direction that moves the tour, rubbery in one that does not. */
private fun dragOffset(dx: Float, step: Int): Float {
    val free = if (dx > 0f) step > 0 else true
    if (free) return dx
    val a = abs(dx)
    return sign(dx) * (a * SWIPE_RUBBER / (1f + a / 160f))
}

private fun hitBox(b: CmsBox, x: Float, y: Float) = x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h

/** The pulse ring eases out as it grows. */
private fun easeOutPulse(x: Float): Float { val u = 1f - x; return 1f - u * u }

/** The tooltip's place at the current tipP (before scale), for hit testing. */
private class TipNow(val x: Float, val y: Float)

private fun tipNow(m: CoachMotion, xs: FloatArray, ys: FloatArray, belows: FloatArray): TipNow {
    val q = m.tipP.get()
    val show = m.tipShow.get()
    val below = along(belows, q)
    return TipNow(along(xs, q) + m.tipX.get(), along(ys, q) + (1f - show) * (if (below > 0.5f) -14f else 14f))
}

private val ARROW_UP = cmsSvg("M0 ${ARROW_H + 1} H$ARROW_W L${ARROW_W / 2 + 2} 2 Q${ARROW_W / 2} 0.4 ${ARROW_W / 2 - 2} 2 Z")
private val ARROW_DOWN = cmsSvg("M0 0 H$ARROW_W L${ARROW_W / 2 + 2} ${ARROW_H - 1} Q${ARROW_W / 2} ${ARROW_H + 0.6f} ${ARROW_W / 2 - 2} ${ARROW_H - 1} Z")

/** The tour layer. Put it over the screen it explains, full size (width and height in dp). */
@Composable
fun CoachMarks(
    steps: List<CoachStep>,
    targets: List<CmsBox>,
    motion: CoachMotion,
    width: Float,
    height: Float,
    reducedMotion: Boolean = false,
) {
    val m = motion
    val count = steps.size
    val tipW = min(width - 32f, TIP_MAX_W)
    val places = remember(targets, width, height, tipW) { tipPlaces(targets, width, height, tipW) }
    val xs = remember(places) { FloatArray(places.size) { places[it].x } }
    val ys = remember(places) { FloatArray(places.size) { places[it].y } }
    val arrowXs = remember(places) { FloatArray(places.size) { places[it].arrowX } }
    val belows = remember(places) { FloatArray(places.size) { places[it].below } }
    val haptics = LocalHapticFeedback.current
    SideEffect {
        m.calm = if (reducedMotion) 1 else 0
        m.haptic = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
    }
    val targetsNow by rememberUpdatedState(targets)
    val tipGeometry by rememberUpdatedState(arrayOf(xs, ys, belows))
    val tipWNow by rememberUpdatedState(tipW)

    // The cutout is erased from the dim with a blurred rounded rect (RN: an even-odd path with a blur mask);
    // a blurred rrect is drawn analytically on the GPU, a blurred full-screen path would be rasterised on the CPU.
    val cutPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.BLACK
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        }
    }
    val rect = remember { android.graphics.RectF() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(m) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (m.visible == 0) return@awaitEachGesture
                    val d = density
                    val px = down.position.x / d
                    val py = down.position.y / d
                    val (gx, gy, gb) = tipGeometry
                    val w = tipWNow
                    val tip = tipNow(m, gx, gy, gb)
                    val onTip = m.tipShow.get() > 0.3f && px >= tip.x && px <= tip.x + w && py >= tip.y - ARROW_H && py <= tip.y + TIP_H + ARROW_H
                    grab(m, count)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var phase = 0 // 0 undecided, 1 swipe, -1 failed
                    var travelled = 0f
                    var startX = 0f
                    var lifted = false
                    var upX = px
                    var upY = py
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = (change.position.x - down.position.x) / d
                            val dy = (change.position.y - down.position.y) / d
                            travelled = max(travelled, (change.position - down.position).getDistance() / d)
                            upX = change.position.x / d
                            upY = change.position.y / d
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                            if (onTip && phase == 0) {
                                if (abs(dy) >= 16f) phase = -1
                                else if (abs(dx) >= 10f) {
                                    phase = 1
                                    startX = m.tipX.get()
                                }
                            }
                            if (phase == 1) {
                                change.consume()
                                m.tipX.set(dragOffset(startX + dx, m.step))
                            }
                        }
                        if (phase == 1) {
                            val vx = if (lifted) tracker.calculateVelocity().x / d else 0f
                            val dx = m.tipX.get()
                            val s = m.step
                            if ((dx > SWIPE_STEP || vx > SWIPE_VELOCITY) && s > 0) goToStep(m, s - 1, count, vx)
                            else if (dx < -SWIPE_STEP || vx < -SWIPE_VELOCITY) goToStep(m, s + 1, count, vx)
                            else springTo(m.tipX, 0f, DRAG_SPRING, m.calm, vx, 0f)
                        } else if (lifted && onTip) {
                            // Next and Skip, with React Native's hit slop; the finger must lift on the button it pressed.
                            val lx = px - tip.x
                            val ly = py - tip.y
                            val ux = upX - tip.x
                            val uy = upY - tip.y
                            val footTop = TIP_H - TIP_PAD - FOOT_H
                            val nextL = w - TIP_PAD - NEXT_W
                            val skipL = nextL - 6f - SKIP_W
                            fun inNext(x: Float, y: Float) = x >= nextL - 6f && x <= nextL + NEXT_W + 6f && y >= footTop - 6f && y <= footTop + FOOT_H + 6f
                            fun inSkip(x: Float, y: Float) = x >= skipL - 8f && x <= skipL + SKIP_W + 8f && y >= footTop - 8f && y <= footTop + FOOT_H + 8f
                            if (m.visible == 1 && inNext(lx, ly) && inNext(ux, uy)) goToStep(m, m.step + 1, count, 0f)
                            else if (m.visible == 1 && inSkip(lx, ly) && inSkip(ux, uy)) hideTour(m)
                        } else if (lifted && !onTip && travelled <= 12f && m.visible == 1) {
                            val b = cutoutAt(targetsNow, m.p.get(), m.iris.get())
                            if (hitBox(b, upX, upY)) goToStep(m, m.step + 1, count, 0f)
                        }
                    } finally {
                        letGo(m)
                    }
                }
            },
    ) {
        // The dim with its soft cutout, the ring and the breathing pulse ring.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val dp = density
            val b = cutoutAt(targets, m.p.get(), m.iris.get())
            val shown = m.shown.get()
            if (shown > 0.001f) {
                drawRect(Color(16, 21, 28), alpha = DIM_ALPHA * shown)
                // Hardware canvases honour mask filters from API 28; older devices get a hard edge.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutPaint.maskFilter == null) {
                    cutPaint.maskFilter = BlurMaskFilter(sigmaToRadius(SOFT_EDGE * dp), BlurMaskFilter.Blur.NORMAL)
                }
                rect.set(b.x * dp, b.y * dp, (b.x + b.w) * dp, (b.y + b.h) * dp)
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(rect, b.r * dp, b.r * dp, cutPaint) }
            }
            val irisFade = clamp01(1f - m.iris.get() * 3f)
            val pulseA = PULSE_ALPHA * (1f - m.pulse.get()) * m.pulseGate.get() * shown * irisFade
            if (pulseA > 0.001f) {
                val g = RING_INSET + PULSE_GROW * easeOutPulse(m.pulse.get())
                drawRoundRect(
                    Color.White, Offset((b.x - g) * dp, (b.y - g) * dp), Size((b.w + 2 * g) * dp, (b.h + 2 * g) * dp),
                    CornerRadius((b.r + g) * dp), Stroke(1.5f * dp), alpha = min(1f, pulseA),
                )
            }
            val ringA = shown * (0.42f + 0.4f * m.targetPress.get()) * irisFade
            if (ringA > 0.001f) {
                val g = RING_INSET
                drawRoundRect(
                    Color.White, Offset((b.x - g) * dp, (b.y - g) * dp), Size((b.w + 2 * g) * dp, (b.h + 2 * g) * dp),
                    CornerRadius((b.r + g) * dp), Stroke(1.5f * dp), alpha = min(1f, ringA),
                )
            }
        }

        // The tooltip bubble.
        Box(
            Modifier
                .size(tipW.dp, TIP_H.dp)
                .graphicsLayer {
                    val q = m.tipP.get()
                    val show = m.tipShow.get()
                    val below = along(belows, q)
                    alpha = clamp01(show * TIP_OPACITY_GAIN)
                    translationX = (along(xs, q) + m.tipX.get()) * density
                    translationY = (along(ys, q) + (1f - show) * (if (below > 0.5f) -14f else 14f)) * density
                    val s = mix(show, TIP_FADE_SCALE, 1f)
                    scaleX = s
                    scaleY = s
                }
                .cssBoxShadow(rgba(8, 12, 18, 0.18f), 2.dp, 6.dp, 20.dp)
                .cssBoxShadow(rgba(8, 12, 18, 0.28f), 18.dp, 40.dp, 20.dp)
                .fillWithHighlight(BUBBLE, 20.dp, 0.95f)
                .drawBehind {
                    // The arrow re-points as the tooltip moves above or below its target.
                    val q = m.tipP.get()
                    val below = along(belows, q)
                    val ax = (along(arrowXs, q) - ARROW_W / 2f) * density
                    val up = clamp01((below - 0.5f) * 2f)
                    val down = clamp01((0.5f - below) * 2f)
                    if (up > 0f) {
                        // Scaled about its base, 1 dp inside the bubble's top edge.
                        translate(ax, density) {
                            scale(density, density * up, pivot = Offset.Zero) {
                                translate(0f, -(ARROW_H + 1f)) { drawPath(ARROW_UP, BUBBLE, alpha = up) }
                            }
                        }
                    }
                    if (down > 0f) {
                        translate(ax, (TIP_H - 1f) * density) {
                            scale(density, density * down, pivot = Offset.Zero) { drawPath(ARROW_DOWN, BUBBLE, alpha = down) }
                        }
                    }
                },
        ) {
            steps.forEachIndexed { i, step -> StepText(step, i, count, m, tipW) }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = TIP_PAD.dp, end = TIP_PAD.dp, bottom = TIP_PAD.dp)
                    .fillMaxWidth()
                    .height(FOOT_H.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(count, m, Modifier.weight(1f))
                Box(
                    Modifier
                        .size(SKIP_W.dp, FOOT_H.dp)
                        .graphicsLayer { alpha = 1f - 0.45f * m.skipPress.get() }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Skip the tour"
                            onClick { hideTour(m); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Skip", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MUTE))
                }
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(NEXT_W.dp, FOOT_H.dp)
                        .graphicsLayer {
                            val s = 1f - 0.07f * m.nextPress.get()
                            scaleX = s
                            scaleY = s
                        }
                        .cssBoxShadow(rgba(20, 60, 42, 0.25f), 1.dp, 2.dp, (FOOT_H / 2f).dp)
                        .fillWithHighlight(ACCENT, (FOOT_H / 2f).dp, 0.22f)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Next tip"
                            onClick { goToStep(m, m.step + 1, count, 0f); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val label = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF4FBF7))
                    Text("Next", Modifier.graphicsLayer { alpha = clamp01((count - 1.5f - m.tipP.get()) * 2f) }, style = label)
                    Text("Done", Modifier.graphicsLayer { alpha = clamp01((m.tipP.get() - (count - 1.5f)) * 2f) }, style = label)
                }
            }
        }
    }
}

@Composable
private fun StepText(step: CoachStep, index: Int, count: Int, m: CoachMotion, tipW: Float) {
    Column(
        Modifier
            .offset(TIP_PAD.dp, TIP_PAD.dp)
            .width((tipW - TIP_PAD * 2f).dp)
            .graphicsLayer {
                val d = m.tipP.get() - index
                alpha = clamp01(1f - abs(d) * 2f)
                translationY = -d * 8f * density
            },
    ) {
        Text(
            "${index + 1} of $count",
            Modifier.height(16.dp),
            style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, color = MUTE, fontFeatureSettings = "tnum"),
        )
        Text(
            step.title,
            Modifier.padding(top = 6.dp).height(24.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = INK),
        )
        Text(
            step.body,
            Modifier.padding(top = 4.dp).height(40.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = Color(0xFF3D443F)),
        )
    }
}

/** Step dots: 6 dp, the active one stretched to 20 dp and filled green. */
@Composable
private fun Dots(count: Int, m: CoachMotion, modifier: Modifier) {
    Canvas(modifier.height(6.dp)) {
        val q = m.tipP.get()
        val dp = density
        var x = 0f
        for (i in 0 until count) {
            val on = clamp01(1f - abs(q - i))
            val w = (6f + 14f * on) * dp
            val r = CornerRadius(3f * dp)
            drawRoundRect(Color(30, 36, 32).copy(alpha = 0.16f), Offset(x, 0f), Size(w, 6f * dp), r)
            if (on > 0f) drawRoundRect(ACCENT, Offset(x, 0f), Size(w, 6f * dp), r, alpha = on)
            x += w + 6f * dp
        }
    }
}
