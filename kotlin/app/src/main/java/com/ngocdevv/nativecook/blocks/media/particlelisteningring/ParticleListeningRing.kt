package com.ngocdevv.nativecook.blocks.media.particlelisteningring

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import com.ngocdevv.nativecook.blocks.media.particlelisteningring.ListeningMotion as M

/*
 Particle Listening Ring — a song search that listens: a ring of coloured dots breathes with the
 voice it hears. A port of the SwiftUI block (ParticleListeningRing.swift); the numbers are the same.

 Look: a warm off-white screen (#FBFAF7) with a round back button top left, the caption "Hum,
 sing, or play the tune" (17 sp, #5E6168) centred at 15.5 % of the height, and in the middle
 (centre at 46 % of the height, radius 32 % of the width) a ring of 96 dots plus 16 that fall into
 the middle when it is loud. Four colours: coral #FF6B57, violet #6C4CF5, teal #19B39A, sun #FFB020.
 At the bottom a white mic button (60 dp, soft shadow) with "Hold anywhere to hum along" under it.

 Each dot (seeded, the same numbers in every stack): an angle around the ring with a little
 jitter, a colour, a rest radius 1–2.4 dp and a loud radius 2.6–7.2 dp. With the level a:
   radius on the ring  R·(1 + 0.03j)·(1 + 0.12a) + R·a·(0.16·n(θ, t) + 0.12·j2)
                       (n: sines of 3θ and 5θ drifting 2 and 3 times a loop)
   angle               θ + 0.18·sin(2πt/6) + 0.05·sin(2πkt/6 + φ)
   size                rest·(1 − 0.35a)·twinkle + growth(colour, t)·loud·pulse
 The voice has three swells (1.1 s violet, 2.45 s teal and sun, 3.85 s coral): the leading colour
 grows to full size, the others to a quarter, and a soft wash of the leading colours (16 %, radius
 1.6 R, from a point 0.35 R out in that colour's quarter) glows behind the ring. In the quiet dips
 half of the ring empties (dots fade to 10 % and shrink to 28 %), the empty half turning once a
 loop. The 16 inner dots fall up to 75 % of the way to the centre when the level passes 0.2–0.8.

 autoPlay timeline (LOOP = 6 s): 0–0.5 s a still, thin ring of specks; 0.5–5.4 s the three swells
 with dips between them; 5.4–6 s the thin ring again. Every motion runs a whole number of times per
 loop, so 6.0 is the frame at 0.0.

 Touch: hold anywhere and you are the voice: the level rises to 1 in 0.3 s (falls over 0.6 s after
 release), the colour of the quarter under the finger leads, and the dots near the finger's angle
 are pushed out by up to 22 % of the radius; dragging around moves the push (a light tick each time
 the leading colour changes). The script fades out in 0.25 s at the touch and comes back at the
 first loop start at least 2 s after the release (the ring is at rest there).
 Reduced motion: the ring stands still at the middle swell; touches do not move it.
 */

private val Background = Color(0xFFFBFAF7)
private val Ink = Color(0xFF2A2B30)
private val Caption = Color(0xFF5E6168)
private val Coral = Color(0xFFFF6B57)

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private class ListeningController(val autoPlay: Boolean, val reduced: Boolean) {
    var appear = nowSeconds()
    /** The script plays from this moment on; before it, it is faded out since [scriptCut]. */
    var scriptFrom by mutableDoubleStateOf(if (autoPlay) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY)
    var scriptCut by mutableDoubleStateOf(Double.NEGATIVE_INFINITY)
    /** A finger: x, y (dp) and when it came down. */
    var press by mutableStateOf<DoubleArray?>(null)
    /** The release: angle, when, the level reached. */
    var released by mutableStateOf<DoubleArray?>(null)
    var lastQuarter = -1
    var frame by mutableDoubleStateOf(nowSeconds())

    fun drive(now: Double, cx: Double, cy: Double): M.Drive {
        if (reduced) return M.Drive(2.45, 1.0, 0.0, 0.0)
        val e = now - appear
        val t = e - floor(e / M.LOOP) * M.LOOP
        val gain = if (now >= scriptFrom) 1.0 else 1 - M.easeOut((now - scriptCut) / 0.25)
        var touch = 0.0
        var angle = 0.0
        val p = press
        val r = released
        if (p != null) {
            touch = M.easeOut((now - p[2]) / 0.3)
            angle = atan2(p[1] - cy, p[0] - cx)
        } else if (r != null) {
            touch = r[2] * (1 - M.easeOut((now - r[1]) / 0.6))
            angle = r[0]
        }
        // A finger's voice flutters too.
        touch *= 1 + 0.15 * sin(2 * PI * 3.1 * e) + 0.08 * sin(2 * PI * 5.3 * e + 1)
        return M.Drive(t, gain, touch, angle)
    }
}

private fun quarter(x: Double, y: Double, cx: Double, cy: Double): Int {
    val q = atan2(y - cy, x - cx) / (2 * PI) + 1
    return ((q - floor(q)) * 4).toInt() % 4
}

@Composable
fun ParticleListeningRing(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { ListeningController(autoPlay, reduced) }
    val painter = remember { ListeningPainter() }
    ListeningSystemBars()

    LaunchedEffect(c) {
        c.appear = nowSeconds()
        if (reduced) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val cx = w / 2
        val cy = h * 0.46
        val radius = w * 0.32
        val micY = h - max(bottom.toDouble(), 16.0) - 78
        val density = LocalDensity.current.density

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Listening" }
                .pointerInput(c, cx, cy) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (reduced) return@awaitEachGesture
                        val now = nowSeconds()
                        val p0 = down.position / density
                        // The script fades out from what it shows now.
                        if (now >= c.scriptFrom) c.scriptCut = now
                        c.scriptFrom = Double.POSITIVE_INFINITY
                        c.press = doubleArrayOf(p0.x.toDouble(), p0.y.toDouble(), now)
                        c.released = null
                        c.lastQuarter = quarter(p0.x.toDouble(), p0.y.toDouble(), cx, cy)
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        var last = p0
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position / density
                            val pressedAt = c.press?.get(2) ?: now
                            c.press = doubleArrayOf(last.x.toDouble(), last.y.toDouble(), pressedAt)
                            val q = quarter(last.x.toDouble(), last.y.toDouble(), cx, cy)
                            if (q != c.lastQuarter) {
                                c.lastQuarter = q
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        val end = nowSeconds()
                        val pressedAt = c.press?.get(2) ?: now
                        val amount = M.easeOut((end - pressedAt) / 0.3)
                        c.released = doubleArrayOf(atan2(last.y - cy, last.x - cx), end, amount)
                        c.press = null
                        if (autoPlay) {
                            // The script comes back at the first loop start at least 2 s from now.
                            val e = end - c.appear + 2
                            c.scriptFrom = c.appear + ceil(e / M.LOOP) * M.LOOP
                        }
                    }
                },
        ) {
            painter.draw(this, c.drive(c.frame, cx, cy), cx, cy, radius, micY, top.toDouble(), density)
        }

        BasicText(
            "Hum, sing, or play the tune",
            Modifier.fillMaxWidth().offset(y = (h * 0.155 - 11).dp).height(22.dp),
            style = TextStyle(fontSize = 17.sp, color = Caption, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().offset(y = (micY + 44).dp), contentAlignment = Alignment.TopCenter) {
            BasicText(
                "Hold anywhere to hum along",
                style = TextStyle(fontSize = 13.sp, color = Caption.copy(alpha = 0.8f), textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/** The ring, the wash behind it, the back button and the mic button. */
private class ListeningPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val out = DoubleArray(5)
    private val path = Path()
    private val colors = M.colors.map { Color(it) }

    fun draw(scope: DrawScope, drive: M.Drive, cx: Double, cy: Double, radius: Double, micY: Double, top: Double, d: Float) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        scope.drawRect(Background)
        // A soft wash of the leading colours behind the ring, as strong as the voice.
        for (k in 0 until 4) {
            val w = min(1.0, M.lead(drive, k))
            if (w < 0.01) continue
            val a = k * PI / 2 + PI / 4
            val p = Offset(((cx + 0.35 * radius * cos(a)) * d).toFloat(), ((cy + 0.35 * radius * sin(a)) * d).toFloat())
            scope.drawRect(
                Brush.radialGradient(
                    listOf(colors[k].copy(alpha = (0.16 * w).toFloat()), colors[k].copy(alpha = 0f)),
                    center = p, radius = (radius * 1.6 * d).toFloat(),
                ),
            )
        }
        for (i in 0 until M.COUNT) {
            M.dot(i, drive, cx, cy, radius, out)
            val r = out[2]
            if (r < 0.15 || out[3] < 0.02) continue
            fill.color = colors[out[4].toInt()].copy(alpha = out[3].toFloat()).toArgb()
            canvas.drawCircle((out[0] * d).toFloat(), (out[1] * d).toFloat(), (r * d).toFloat(), fill)
        }

        // Back button.
        fill.color = Color(0xFFEFEDEA).toArgb()
        canvas.drawCircle(38 * d, ((top + 26) * d).toFloat(), 18 * d, fill)
        stroke.color = Ink.toArgb()
        stroke.strokeWidth = 2 * d
        val bx = 34f
        val by = (top + 19).toFloat()
        path.reset()
        path.moveTo((bx + 7) * d, (by + 1) * d)
        path.lineTo((bx + 1) * d, (by + 7) * d)
        path.lineTo((bx + 7) * d, (by + 13) * d)
        canvas.drawPath(path, stroke)

        // The mic button, with a soft ring that swells with the level.
        val level = min(1.0, drive.gain * M.level(drive.t) + drive.touch)
        val mx = (cx * d).toFloat()
        val my = (micY * d).toFloat()
        stroke.color = Coral.copy(alpha = (0.28 * level).toFloat()).toArgb()
        canvas.drawCircle(mx, my, ((30 + 14 * level) * d).toFloat(), stroke)
        shadow.color = Color.Black.copy(alpha = 0.08f).toArgb()
        // SwiftUI's shadow radius 12 is about a Gaussian sigma of 6.
        shadow.maskFilter = BlurMaskFilter(max(0.1f, (6 * d - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(mx, my + 4 * d, 30 * d, shadow)
        fill.color = android.graphics.Color.WHITE
        canvas.drawCircle(mx, my, 30 * d, fill)
        // A microphone in an 18 × 26 box: capsule, cradle and stand.
        val ox = (cx - 9).toFloat()
        val oy = (micY - 13).toFloat()
        canvas.drawRoundRect(RectF((ox + 5) * d, oy * d, (ox + 13) * d, (oy + 15) * d), 4 * d, 4 * d, stroke.apply { color = Ink.toArgb() })
        path.reset()
        path.moveTo((ox + 1) * d, (oy + 11) * d)
        path.cubicTo((ox + 1) * d, (oy + 22) * d, (ox + 17) * d, (oy + 22) * d, (ox + 17) * d, (oy + 11) * d)
        path.moveTo((ox + 9) * d, (oy + 19.5f) * d)
        path.lineTo((ox + 9) * d, (oy + 25) * d)
        canvas.drawPath(path, stroke)
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the block
 * is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun ListeningSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
