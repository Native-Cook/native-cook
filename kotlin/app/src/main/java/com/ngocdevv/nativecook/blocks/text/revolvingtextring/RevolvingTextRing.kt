package com.ngocdevv.nativecook.blocks.text.revolvingtextring

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Revolving Text Ring — an open-air festival poster whose name runs around a turning 3D ring. A
 port of the SwiftUI block (RevolvingTextRing.swift); the numbers are the same:

 Two bands of capitals are wrapped around a vertical cylinder of radius R (39 % of the screen
 width, at most 170 dp), seen from 22° above with a 1000 dp focal length: a big band
 "OPEN AIR • OPEN AIR • " and, under it, a small tracked band with the dates and place. Each
 band's font size is chosen so that its text goes round exactly once. Every glyph is a flat tile
 tangent to the cylinder, drawn with the affine map of the projection at its centre, so the
 letters on the far side read mirrored through the open ring. Glyphs are drawn back to front;
 a glyph's opacity runs from 0.3 (facing away) to 1 (facing the viewer).

 autoPlay timeline (LOOP = 8 s):
   rotation   constant, one turn every 4 s (two per loop), the front moving left like a marquee.
   0.0 … 3.2  light: paper (#EEEDE8) with near-black ink (#0E0E10).
   3.2 … 4.0  colours invert (ease-in-out): dark paper, light ink, the whole screen together.
   4.0 … 7.2  dark.
   7.2 … 8.0  back to light; t = 8 is the same frame as t = 0 (two whole turns, light).

 Touch: dragging sideways turns the ring under the finger (1 dp = 1/R rad), with a tick haptic
 every 30°; on release it keeps the finger's speed and slows with a 0.5 s time constant. A tap
 inverts the colours (0.6 s). 2 s after the last touch the ring eases to its starting angle and
 the light colours over 1 s, and the loop starts again.
 Reduced motion: the ring stands still in the light colours; dragging still turns it (no glide),
 and a tap cross-fades the colours.
 */

private const val LOOP = 8.0
private const val TURN = 4.0
private const val TILT = 22 * PI / 180
private const val FOCAL = 1000.0
private const val GLIDE = 0.5
private const val TICK = PI / 6
private const val SMALL_TRACKING = 0.32 // em
private const val CAP_HEIGHT = 0.688 // em, Archivo Black
/** The text box's centre sits this far above the baseline (ascent 0.878, descent 0.21 em). */
private const val BOX_CENTER = 0.334
private const val FONT_ASSET = "revolvingtextring/revolvingtextring_ArchivoBlack-Regular.ttf"

private val Paper = Color(0xFFEEEDE8)
private val Night = Color(0xFF0E0E10)

private fun nowSeconds() = System.nanoTime() / 1e9

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

private fun smoothstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
)

private class RevolvingTextRingController(val reduced: Boolean) {
    var autoStart by mutableStateOf<Double?>(null)
    /** The manual angle, and the glide after a release (when, speed in rad/s). */
    var theta by mutableDoubleStateOf(0.0)
    var releaseAt by mutableStateOf<Double?>(null)
    var releaseSpeed by mutableDoubleStateOf(0.0)
    /** The colours by hand: dark amount from [darkFrom] towards [darkTo] since [darkSince]. */
    var darkFrom by mutableDoubleStateOf(0.0)
    var darkTo by mutableDoubleStateOf(0.0)
    var darkSince by mutableDoubleStateOf(-10.0)
    /** The ease back to the start before the loop restarts: when, from, to, dark. */
    var returning by mutableStateOf<DoubleArray?>(null)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    fun angle(at: Double): Double {
        val start = autoStart
        if (start != null && at >= start && !reduced) return 2 * PI * (((at - start) % LOOP) / TURN)
        returning?.let { r ->
            val u = easeInOut(((at - r[0]) / 1.0).coerceIn(0.0, 1.0))
            return r[1] + (r[2] - r[1]) * u
        }
        val rel = releaseAt
        if (rel != null && !reduced) {
            val t = max(0.0, at - rel)
            return theta + releaseSpeed * GLIDE * (1 - exp(-t / GLIDE))
        }
        return theta
    }

    fun dark(at: Double): Double {
        val start = autoStart
        if (start != null && at >= start && !reduced) {
            val e = (at - start) % LOOP
            return when {
                e < 3.2 -> 0.0
                e < 4.0 -> easeInOut((e - 3.2) / 0.8)
                e < 7.2 -> 1.0
                else -> 1 - easeInOut((e - 7.2) / 0.8)
            }
        }
        returning?.let { r ->
            val u = easeInOut(((at - r[0]) / 1.0).coerceIn(0.0, 1.0))
            return r[3] * (1 - u)
        }
        val u = easeInOut(((at - darkSince) / 0.6).coerceIn(0.0, 1.0))
        return darkFrom + (darkTo - darkFrom) * u
    }

    /** Freezes whatever is playing into the manual state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        val a = angle(now)
        val d = dark(now)
        theta = a
        releaseAt = null
        returning = null
        autoStart = null
        darkFrom = d
        darkTo = d
        darkSince = now
    }
}

private data class RingGlyph(val char: Char, val big: Boolean, val phi: Double, val y: Double)

/** The ring's bands: every glyph's angle on the cylinder and its height. */
private class RingGeometry(big: String, small: String, width: Float, typeface: Typeface) {
    val radius = min(width * 0.39, 170.0)
    val bigSize: Double
    val smallSize: Double
    val glyphs: List<RingGlyph>
    /** Each character's advance at 1 dp. */
    val advance = mutableMapOf<Char, Double>()

    init {
        val paint = Paint().apply { this.typeface = typeface; textSize = 1000f }
        fun adv(ch: Char) = advance.getOrPut(ch) { paint.measureText(ch.toString()) / 1000.0 }
        val circumference = 2 * PI * radius
        // Each band's size makes its text go round exactly once.
        bigSize = circumference / big.sumOf { adv(it) }
        smallSize = circumference / (small.sumOf { adv(it) } + SMALL_TRACKING * small.length)
        val hBig = CAP_HEIGHT * bigSize
        val hSmall = CAP_HEIGHT * smallSize
        val gap = 0.45 * hSmall
        val yBig = -(hSmall + gap) / 2
        val ySmall = (hBig + gap) / 2
        val list = mutableListOf<RingGlyph>()
        var s = 0.0
        for (ch in big) {
            val w = adv(ch) * bigSize
            if (ch != ' ') list += RingGlyph(ch, true, (s + w / 2) / radius, yBig)
            s += w
        }
        s = 0.0
        for (ch in small) {
            val w = adv(ch) * smallSize
            if (ch != ' ') list += RingGlyph(ch, false, (s + w / 2) / radius, ySmall)
            s += w + SMALL_TRACKING * smallSize
        }
        glyphs = list
    }
}

/** Projects a point on the ring (x right, y down, z towards the viewer) seen from above: screen x, y and depth. */
private fun project(x: Double, y: Double, z: Double, cx: Double, cy: Double, out: DoubleArray) {
    val y2 = y * cos(TILT) + z * sin(TILT)
    val z2 = -y * sin(TILT) + z * cos(TILT)
    val p = FOCAL / (FOCAL - z2)
    out[0] = cx + x * p
    out[1] = cy + y2 * p
    out[2] = z2
}

@Composable
fun RevolvingTextRing(
    modifier: Modifier = Modifier,
    bigBand: String = "OPEN AIR • OPEN AIR • ",
    smallBand: String = "FRIDAY TO SUNDAY · RIVERSIDE PARK · GATES AT 4 PM · ",
    header: String = "OPEN AIR 26",
    dates: String = "JUN 26–28",
    title: String = "Three days by the river.",
    subtitle: String = "Forty artists, two stages and one long weekend outdoors.",
    action: String = "Get passes",
    autoPlay: Boolean = false,
    onAction: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val typeface = remember { Typeface.createFromAsset(context.assets, FONT_ASSET) }
    val family = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    val c = remember(reduced) { RevolvingTextRingController(reduced) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val now = nowSeconds()
            val a = c.angle(now)
            val target = Math.round(a / (2 * PI)) * 2 * PI
            c.returning = doubleArrayOf(now, a, target, c.dark(now))
            c.autoStart = now + 1.0
        }
    }

    // The status bar's icons follow the colours: dark on paper, light on night.
    val lightBars by remember(c) { derivedStateOf { c.dark(c.frame) < 0.5 } }
    RevolvingTextRingSystemBars(lightBars)

    val bg = ColorProducer { mix(Paper, Night, c.dark(c.frame).toFloat()) }
    val ink = ColorProducer { mix(Night, Paper, c.dark(c.frame).toFloat()) }

    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(bg()) }) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val ring = remember(width, bigBand, smallBand) { RingGeometry(bigBand, smallBand, width, typeface) }
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "$bigBand $smallBand" }
                .pointerInput(c, ring) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        c.takeOver(nowSeconds())
                        val start = c.theta
                        var lastTick = floor(start / TICK).toInt()
                        var moved = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            val d = (change.position - down.position) / density
                            if (hypot(d.x, d.y) > 6f) moved = true
                            if (abs(d.x) > 2f || abs(d.y) > 6f) {
                                c.theta = start - d.x / ring.radius
                                val t = floor(c.theta / TICK).toInt()
                                if (t != lastTick) {
                                    lastTick = t
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                change.consume()
                            }
                        }
                        val now = nowSeconds()
                        if (moved) {
                            if (!reduced) {
                                val vx = tracker.calculateVelocity().x / density
                                c.releaseSpeed = (-vx / ring.radius).coerceIn(-12.0, 12.0)
                                c.releaseAt = now
                            }
                        } else {
                            val d = c.dark(now)
                            c.darkFrom = d
                            c.darkTo = if (d > 0.5) 0.0 else 1.0
                            c.darkSince = now
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        scheduleResume()
                    }
                },
        ) {
            drawRing(c, ring, typeface, paint, density, ink(), height * 0.43f)
        }

        // Header.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 16).dp)
                .padding(horizontal = 24.dp),
        ) {
            val style = TextStyle(fontFamily = family, fontSize = 13.sp, letterSpacing = 1.5.sp)
            BasicText(header, style = style, color = ink, maxLines = 1)
            Spacer(Modifier.weight(1f))
            BasicText(dates, style = style, color = ink, maxLines = 1)
        }

        Column(
            Modifier
                .offset(x = 24.dp, y = (height - max(bottomInset, 20f) - 16 - 56 - 32 - 112).dp)
                .width((width - 48).dp),
        ) {
            BasicText(title, style = TextStyle(fontFamily = family, fontSize = 26.sp), color = ink, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            BasicText(
                subtitle,
                style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                color = { ink().copy(alpha = 0.6f) },
            )
        }

        Box(
            Modifier
                .offset(x = 24.dp, y = (height - max(bottomInset, 20f) - 16 - 56).dp)
                .width((width - 48).dp)
                .height(56.dp)
                .drawBehind { drawRoundRect(ink(), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)) }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onAction() },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = TextStyle(fontFamily = family, fontSize = 16.sp, letterSpacing = 0.5.sp), color = bg, maxLines = 1)
        }
    }
}

private fun DrawScope.drawRing(
    c: RevolvingTextRingController,
    ring: RingGeometry,
    typeface: Typeface,
    paint: Paint,
    density: Float,
    ink: Color,
    centerY: Float,
) {
    val angle = c.angle(c.frame)
    val cx = size.width / density / 2.0
    val cy = centerY.toDouble()
    val n = ring.glyphs.size
    val depth = DoubleArray(n)
    val tiles = Array(n) { FloatArray(7) }
    val p = DoubleArray(3)
    val pt = DoubleArray(3)
    val pu = DoubleArray(3)
    ring.glyphs.forEachIndexed { i, g ->
        val phi = g.phi - angle
        val sp = sin(phi)
        val cp = cos(phi)
        val x = ring.radius * sp
        val z = ring.radius * cp
        project(x, g.y, z, cx, cy, p)
        // The tile's axes: along the ring (tangent) and down the cylinder, by finite differences.
        project(x + cp, g.y, z - sp, cx, cy, pt)
        project(x, g.y + 1, z, cx, cy, pu)
        val facing = cp * cos(TILT)
        val opacity = 0.3 + 0.7 * smoothstep(-0.15, 0.35, facing)
        tiles[i] = floatArrayOf(
            (pt[0] - p[0]).toFloat(), (pu[0] - p[0]).toFloat(), p[0].toFloat(),
            (pt[1] - p[1]).toFloat(), (pu[1] - p[1]).toFloat(), p[1].toFloat(),
            opacity.toFloat(),
        )
        depth[i] = p[2]
    }
    val order = (0 until n).sortedBy { depth[it] }
    val canvas = drawContext.canvas.nativeCanvas
    val matrix = Matrix()
    val values = FloatArray(9)
    paint.typeface = typeface
    val argb = ink.toArgb()
    canvas.save()
    canvas.scale(density, density)
    for (i in order) {
        val g = ring.glyphs[i]
        val t = tiles[i]
        values[0] = t[0]; values[1] = t[1]; values[2] = t[2]
        values[3] = t[3]; values[4] = t[4]; values[5] = t[5]
        values[6] = 0f; values[7] = 0f; values[8] = 1f
        matrix.setValues(values)
        val size = if (g.big) ring.bigSize else ring.smallSize
        paint.textSize = size.toFloat()
        paint.color = argb
        paint.alpha = (255 * t[6]).roundToInt()
        val w = (ring.advance[g.char] ?: 0.6) * size
        canvas.save()
        canvas.concat(matrix)
        canvas.drawText(g.char.toString(), (-w / 2).toFloat(), (BOX_CENTER * size).toFloat(), paint)
        canvas.restore()
    }
    canvas.restore()
}

/**
 * The status and navigation bar icons are switched to dark while the screen is light (the
 * gallery's activity uses light ones) and put back when the block closes.
 */
@Composable
private fun RevolvingTextRingSystemBars(light: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
    DisposableEffect(window, view, light) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        onDispose {}
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
