package com.ngocdevv.nativecook.blocks.animations.spectralcapsulespin

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

// Capsule radius and half the seam-to-seam length, as fractions of the screen width.
private const val RADIUS_RATIO = 0.172
private const val HALF_LENGTH_RATIO = 0.268
private const val CENTRE_RATIO = 0.44f
private const val CANVAS_HEIGHT = 440f
// Dp of drag along the capsule's diagonal for one radian of turn.
private const val DRAG_SCALE = 90.0
private const val COAST_TAU = 0.35
private const val HOLD_AFTER_TOUCH = 2.0
private const val SETTLE = 0.9
private const val REDUCED_PHI = 0.80
private const val MAX_SPIN = 14.0

private class ScsRelease(val phi: Double, val velocity: Double, val atNanos: Long)

/** The turn's state: plain fields for the gesture, states for what the canvas draws. */
private class ScsMotion {
    var originNanos = -1L
    var dragging = false
    var dragBase = 0.0
    var dragPhi = 0.0
    var release: ScsRelease? = null
    var detent = 0
    val phi = mutableDoubleStateOf(0.0)
    val hueShift = mutableDoubleStateOf(0.0)
    val warmGain = mutableDoubleStateOf(1.0)
    val wake = mutableIntStateOf(0)

    /** Sets the drawn pose for [now]; returns false once nothing will move any more. */
    fun step(now: Long, autoPlay: Boolean, reduced: Boolean): Boolean {
        if (originNanos < 0) originNanos = now
        val sinceOrigin = (now - originNanos) / 1e9
        if (reduced) {
            val w = 2 * PI * SpectralCapsuleSpinTimeline.loopTime(sinceOrigin) / LOOP
            phi.doubleValue = REDUCED_PHI
            hueShift.doubleValue = 0.6 * sin(w)
            warmGain.doubleValue = 0.85 + 0.15 * sin(w)
            return true
        }
        hueShift.doubleValue = 0.0
        warmGain.doubleValue = 1.0
        if (dragging) {
            phi.doubleValue = dragPhi
            return true
        }
        val r = release
        if (r != null) {
            val e = (now - r.atNanos) / 1e9
            fun coast(x: Double) = r.phi + r.velocity * COAST_TAU * (1 - exp(-x / COAST_TAU))
            if (e < HOLD_AFTER_TOUCH) {
                phi.doubleValue = coast(e)
                return true
            }
            val held = coast(HOLD_AFTER_TOUCH)
            val rest = round(held / PI) * PI
            val k = (e - HOLD_AFTER_TOUCH) / SETTLE
            if (k < 1) {
                phi.doubleValue = held + (rest - held) * SpectralCapsuleSpinTimeline.easeInOutCubic(k)
                return true
            }
            if (!autoPlay) {
                phi.doubleValue = rest
                return false
            }
            phi.doubleValue = rest + SpectralCapsuleSpinTimeline.phi(SpectralCapsuleSpinTimeline.loopTime(e - HOLD_AFTER_TOUCH - SETTLE))
            return true
        }
        if (!autoPlay) {
            phi.doubleValue = 0.0
            return false
        }
        phi.doubleValue = SpectralCapsuleSpinTimeline.phi(SpectralCapsuleSpinTimeline.loopTime(sinceOrigin))
        return true
    }

    fun detentOf(p: Double) = floor((p - PI / 2) / PI).toInt()
}

private val Background = Color(0xFF040508)

private fun style(size: Float, weight: FontWeight, color: Long, spacing: Float = 0f, line: Float, features: String? = null) = TextStyle(
    color = Color(color),
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = line.sp,
    textAlign = TextAlign.Center,
    fontFeatureSettings = features,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val BrandStyle = style(13f, FontWeight.SemiBold, 0xFFC9CFDB, spacing = 3f, line = 20f)
private val BatchStyle = style(13f, FontWeight.Normal, 0xFF6E7587, line = 20f, features = "tnum")
private val LabelStyle = style(12f, FontWeight.SemiBold, 0xFF7C8496, spacing = 2.4f, line = 16f)
private val TitleStyle = style(28f, FontWeight.SemiBold, 0xFFF1F3F8, line = 34f)
private val DetailStyle = style(15f, FontWeight.Normal, 0xFF8C93A4, line = 20f)

/**
 * A "formulating" waiting screen: a clear glass capsule turns end over end on near black. When a
 * cap swings towards the viewer it throws a band of spectrum, then a warm caustic, then shows as a
 * disc with the body's rings stacked behind it. Drag to turn it by hand.
 *
 * @param autoPlay turns by itself, one full turn every [LOOP] seconds.
 */
@Composable
fun SpectralCapsuleSpin(
    modifier: Modifier = Modifier,
    brand: String = "ORRIN",
    batch: String = "Batch 0427",
    label: String = "FORMULATING",
    title: String = "Your daily blend",
    detail: String = "Sealing four actives into one clear capsule.",
    autoPlay: Boolean = false,
) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val motion = remember { ScsMotion() }
    val renderer = remember { SpectralCapsuleSpinRenderer() }
    val backdrop = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    LaunchedEffect(autoPlay, reduced, motion.wake.intValue) {
        while (true) {
            val moving = withFrameNanos { now -> motion.step(now, autoPlay, reduced) }
            if (!moving) break
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val centreY = h * CENTRE_RATIO
        val bottom = h - safeBottom

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A clear capsule turning end over end. Drag to turn it." }
                .pointerInput(reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val start = down.position
                        var started = false
                        val u = SpectralCapsuleSpinGeometry.U
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta: Offset = change.position - start
                            if (!started && delta.getDistance() > 4 * density) {
                                started = true
                                motion.dragBase = motion.phi.doubleValue
                                motion.detent = motion.detentOf(motion.dragBase)
                                motion.release = null
                                motion.dragPhi = motion.dragBase
                                motion.dragging = true
                                motion.wake.intValue++
                            }
                            if (started) {
                                change.consume()
                                val along = (delta.x * u.x + delta.y * u.y) / density
                                val p = motion.dragBase - along / DRAG_SCALE
                                motion.dragPhi = p
                                val now = motion.detentOf(p)
                                if (now != motion.detent) {
                                    motion.detent = now
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        if (started) {
                            val v = tracker.calculateVelocity()
                            val along = (v.x * u.x + v.y * u.y) / density
                            val spin = (-along / DRAG_SCALE).coerceIn(-MAX_SPIN, MAX_SPIN)
                            motion.release = ScsRelease(motion.dragPhi, spin, System.nanoTime())
                            motion.dragging = false
                        }
                    }
                },
        ) {
            val phi = motion.phi.doubleValue
            val hue = motion.hueShift.doubleValue
            val gain = motion.warmGain.doubleValue
            val d = density
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(d, d)
                val warm = SpectralCapsuleSpinTimeline.Light(abs(sin(phi))).warm * gain
                backdrop.shader = RadialGradient(
                    w / 2, centreY, h * 0.62f,
                    intArrayOf(ScsPalette.argb(ScsPalette.VIGNETTE, 1.0), ScsPalette.argb(ScsPalette.BACKGROUND, 1.0)),
                    null, Shader.TileMode.CLAMP,
                )
                native.drawRect(0f, 0f, w, h, backdrop)
                backdrop.shader = RadialGradient(
                    w / 2, centreY, 260f,
                    intArrayOf(ScsPalette.argb(ScsPalette.HALO, 0.10 + 0.16 * warm), ScsPalette.argb(ScsPalette.HALO, 0.0)),
                    null, Shader.TileMode.CLAMP,
                )
                native.drawRect(0f, 0f, w, h, backdrop)
                native.translate(0f, centreY - CANVAS_HEIGHT / 2)
                renderer.draw(native, w, CANVAS_HEIGHT, phi, hue, gain, w * RADIUS_RATIO, w * HALF_LENGTH_RATIO)
                native.restore()
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (safeTop + 24f - 10f).dp)
                .height(20.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(brand, style = BrandStyle)
            Spacer(Modifier.weight(1f))
            Text(batch, style = BatchStyle)
        }

        CentredLine(label, LabelStyle, bottom - 148f, 16f)
        CentredLine(title, TitleStyle, bottom - 114f, 34f)
        CentredLine(detail, DetailStyle, bottom - 80f, 20f, maxWidth = 320f)

        Indicator(motion, Modifier.offset(x = (w / 2 - 27f).dp, y = (bottom - 40f - 10f).dp))
    }
}

@Composable
private fun CentredLine(text: String, style: TextStyle, centreY: Float, line: Float, maxWidth: Float = 0f) {
    Box(
        Modifier.fillMaxWidth().offset(y = (centreY - line / 2).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, modifier = if (maxWidth > 0f) Modifier.widthIn(max = maxWidth.dp) else Modifier)
    }
}

/** Two small capsules: each lights when its end of the big one faces the viewer. */
@Composable
private fun Indicator(motion: ScsMotion, modifier: Modifier) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val glow = remember { BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL) }
    Canvas(modifier.size(54.dp, 20.dp)) {
        val phi = motion.phi.doubleValue
        val lit = doubleArrayOf(
            SpectralCapsuleSpinTimeline.endOn(phi, PI / 2),
            SpectralCapsuleSpinTimeline.endOn(phi, 3 * PI / 2),
        )
        val d = density
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(d, d)
            for (i in 0..1) {
                val rect = RectF(i * 32f, 6f, i * 32f + 22f, 14f)
                paint.maskFilter = glow
                paint.color = ScsPalette.argb(0x4D7BFF, 0.9 * lit[i])
                native.drawRoundRect(rect, 4f, 4f, paint)
                paint.maskFilter = null
                paint.color = ScsPalette.argb(0x232836, 1.0)
                native.drawRoundRect(rect, 4f, 4f, paint)
                paint.color = ScsPalette.argb(0xDCE6FF, lit[i])
                native.drawRoundRect(rect, 4f, 4f, paint)
            }
            native.restore()
        }
    }
}
