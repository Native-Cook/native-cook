package com.ngocdevv.nativecook.blocks.pickers.spinmorphicons

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The redesigned React Native reference, number for number: six radial profiles, 120 rays.
// Radius = mix(a, b, u) * 96 * (1 - 0.22 sin(pi u)); rotation = 2 pi u.
// Each 1.2 s step holds 0.55 s, then spins/morphs for 0.65 s with cubic in-out easing.
// The gradient icon sits at 44% of the viewport; its deep color and tint wash the UI.
// Swipe 90 dp per icon; release snaps for 0.35 s, holds 2 s, then resumes that icon's hold.

/** Seconds. */
const val LOOP = 7.2
private const val STEP = 1.2
private const val MORPH_START = 0.55
private const val RAYS = 120
private const val ICON_SIZE = 96.0
private const val MINI = 13.0
private const val PER_ICON = 90.0
private const val HOLD = 2.0
private const val SNAP = 0.35

private data class Focus(val name: String, val line: String, val rgb: Int, val deep: Int, val tint: Int)
private val FOCUSES = listOf(
    Focus("Hydrate", "Eight glasses, one at a time.", 0x19B5AE, 0x0B5D59, 0xDDF4F2),
    Focus("Grow", "Ten minutes of something new.", 0x3DB66A, 0x1D5E35, 0xE2F3E7),
    Focus("Energy", "Move before you sit down.", 0xFF9F1C, 0x8A4B00, 0xFFF0DA),
    Focus("Rest", "Lights out by eleven.", 0x8F7BF2, 0x3F2F9C, 0xECE8FD),
    Focus("Care", "Check in with someone you love.", 0xFF5A64, 0x9C1C27, 0xFFE5E7),
    Focus("Focus", "One task, phone face down.", 0x3D7BFF, 0x153D9C, 0xE2EBFF),
)

private data class Point(val x: Double, val y: Double)

private fun sampleCurve(n: Int, curve: (Double) -> Point) =
    List(n) { curve(it.toDouble() / n * PI * 2) }

/** The farthest positive ray/edge intersection, including the heart's two lobes. */
private fun castPolygon(points: List<Point>, angle: Double): Double {
    val dx = cos(angle)
    val dy = sin(angle)
    var best = 0.0
    points.forEachIndexed { i, p ->
        val q = points[(i + 1) % points.size]
        val ex = q.x - p.x
        val ey = q.y - p.y
        val den = dx * ey - dy * ex
        if (abs(den) >= 1e-9) {
            val t = (p.x * ey - p.y * ex) / den
            val s = (p.x * dy - p.y * dx) / den
            if (t > 0 && s >= 0 && s <= 1) best = max(best, t)
        }
    }
    return best
}

private fun profileOf(blur: Int, radius: (Double) -> Double): DoubleArray {
    val raw = DoubleArray(RAYS) { radius(it.toDouble() / RAYS * PI * 2 - PI / 2) }
    if (blur <= 0) return raw
    return DoubleArray(RAYS) { i ->
        var sum = 0.0
        for (k in -blur..blur) sum += raw[(i + k + RAYS) % RAYS]
        sum / (2 * blur + 1)
    }
}

private fun polygonProfile(points: List<Point>, blur: Int) =
    profileOf(blur) { castPolygon(points, it) }

/** Built once, never ray-cast on an animation frame. */
private object Profiles {
    val values = listOf(
        polygonProfile(sampleCurve(240) { t ->
            Point(0.82 * sin(t) * sin(t / 2), -0.98 * cos(t) + 0.05)
        }, 1),
        polygonProfile(run {
            val c = 0.634
            val radius = 1.184
            val half = atan2(1.0, c)
            val points = mutableListOf<Point>()
            for (i in 0..60) {
                val a = -half + 2 * half * i / 60
                points += Point(-c + radius * cos(a), -radius * sin(a))
            }
            for (i in 0..60) {
                val a = PI - half + 2 * half * i / 60
                points += Point(c + radius * cos(a), -radius * sin(a))
            }
            val k = sqrt(0.5)
            points.map { Point((it.x * k - it.y * k) * 0.95, (it.x * k + it.y * k) * 0.95) }
        }, 1),
        profileOf(0) { a -> 0.7 + 0.2 * max(0.0, cos(8 * a)).pow(2) },
        profileOf(1) { a ->
            val dx = cos(a)
            val dy = sin(a)
            var best = 0.0
            val puffs = arrayOf(
                doubleArrayOf(-0.5, 0.12, 0.42),
                doubleArrayOf(0.02, -0.16, 0.58),
                doubleArrayOf(0.52, 0.14, 0.4),
                doubleArrayOf(0.0, 0.2, 0.42),
            )
            for (p in puffs) {
                val b = dx * p[0] + dy * p[1]
                val disc = b * b - (p[0] * p[0] + p[1] * p[1] - p[2] * p[2])
                if (disc >= 0) best = max(best, b + sqrt(disc))
            }
            if (dy > 0) best = min(best, 0.54 / dy)
            best
        },
        polygonProfile(sampleCurve(240) { t ->
            val x = 16 * sin(t).pow(3)
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            Point(x / 17, -y / 17 - 0.1)
        }, 1),
        polygonProfile(listOf(Point(0.0, -0.95), Point(0.95, 0.68), Point(-0.95, 0.68)), 4),
    )
}

private fun easeInOutCubic(x: Double) =
    if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun smooth(x: Double): Double {
    val u = x.coerceIn(0.0, 1.0)
    return u * u * (3 - 2 * u)
}

private fun wrap(i: Double) = ((i % FOCUSES.size) + FOCUSES.size) % FOCUSES.size
private fun wrapped(i: Int) = ((i % FOCUSES.size) + FOCUSES.size) % FOCUSES.size
/** JavaScript Math.round, including negative half-integers. */
private fun rounded(x: Double) = floor(x + 0.5)

private fun indexAt(t: Double): Double {
    val k = floor(t / STEP)
    val local = t - k * STEP
    return k + easeInOutCubic(((local - MORPH_START) / (STEP - MORPH_START)).coerceIn(0.0, 1.0))
}

/** RN mixes and rounds the three sRGB bytes, rather than interpolating in a different space. */
private fun mixColor(a: Int, b: Int, u: Double): Color {
    fun channel(shift: Int) = rounded(((a shr shift) and 255) * (1 - u) + ((b shr shift) and 255) * u).toInt()
    return Color(channel(16), channel(8), channel(0))
}

private fun labelFade(index: Double, k: Int): Float {
    val distance = abs(wrap(index) - k)
    return smooth(min(distance, FOCUSES.size - distance) / 0.45).toFloat()
}

private class SpinMorphIconsClock {
    var index by mutableDoubleStateOf(0.0)
    var elapsed = 0.0
        private set
    private var origin = 0.0
    private var touching = false
    private var manual = false
    private var released = -1.0
    private var grabbed = 0.0
    private var from = 0.0
    private var snapTo = 0.0
    private var lastStep = 0.0

    fun advance(now: Double, autoPlay: Boolean) {
        elapsed = now
        if (touching) return
        if (manual) {
            val since = now - released
            if (since < SNAP) index = from + (snapTo - from) * easeInOutCubic((since / SNAP).coerceIn(0.0, 1.0))
            else {
                index = snapTo
                if (since > SNAP + HOLD) {
                    manual = false
                    origin = now - wrap(snapTo) * STEP
                }
            }
            return
        }
        index = if (autoPlay) indexAt((now - origin) % LOOP) else 0.0
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = rounded(index * 100) / 100
        lastStep = rounded(index)
    }

    fun drag(translationX: Double): Boolean {
        index = grabbed - translationX / PER_ICON
        val step = rounded(index)
        if (step == lastStep) return false
        lastStep = step
        return true
    }

    fun release() {
        touching = false
        from = index
        snapTo = rounded(index)
        released = elapsed
    }
}

private fun blend(index: Double, key: (Focus) -> Int): Color {
    val base = floor(index).toInt()
    return mixColor(key(FOCUSES[wrapped(base)]), key(FOCUSES[wrapped(base + 1)]), index - floor(index))
}

private fun outline(index: Double, scale: Double, spin: Double, cx: Float, cy: Float, path: android.graphics.Path) {
    val base = floor(index).toInt()
    val u = index - floor(index)
    val a = Profiles.values[wrapped(base)]
    val b = Profiles.values[wrapped(base + 1)]
    path.rewind()
    for (k in 0 until RAYS) {
        val r = (a[k] * (1 - u) + b[k] * u) * scale
        val angle = k.toDouble() / RAYS * PI * 2 - PI / 2 + spin
        val x = cx + (cos(angle) * r).toFloat()
        val y = cy + (sin(angle) * r).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/** Android's mask filter uses radius, whereas Skia's MakeBlur takes sigma. */
private fun solidBlur(sigma: Float) = BlurMaskFilter(
    ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.SOLID,
)

/** One reused software surface preserves Skia's solid mask blur on every supported API. */
private class SpinMorphIconsArtwork(private val density: Float) {
    val centre = (ICON_SIZE.toFloat() + 72f) * density
    private val bitmap = Bitmap.createBitmap(
        ceil(centre * 2).toInt(), ceil(centre * 2 + 18f * density).toInt(), Bitmap.Config.ARGB_8888,
    )
    private val canvas = android.graphics.Canvas(bitmap)
    private val path = android.graphics.Path()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = solidBlur(18f * density) }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    // The fixed highlight is blurred once, then clipped with an antialiased path each frame.
    private val highlight = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
        val radius = ICON_SIZE.toFloat() * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = rounded(255 * 0.32).toInt()
            maskFilter = solidBlur(14f * density)
        }
        android.graphics.Canvas(it).drawOval(
            centre - radius * 0.75f, centre - radius * 0.85f,
            centre + radius * 0.15f, centre - radius * 0.30f, paint,
        )
    }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(highlight, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private var previous = Double.NaN

    fun frame(index: Double): Bitmap {
        val i = wrap(index)
        if (i == previous) return bitmap
        previous = i
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val u = i - floor(i)
        val radius = ICON_SIZE.toFloat() * density
        outline(i, ICON_SIZE * (1 - 0.22 * sin(PI * u)) * density, PI * 2 * u, centre, centre, path)
        shadow.color = blend(i) { it.deep }.toArgb()
        shadow.alpha = rounded(255 * 0.32).toInt()
        canvas.save()
        canvas.translate(0f, 18f * density)
        canvas.drawPath(path, shadow)
        canvas.restore()

        val color = blend(i) { it.rgb }.toArgb()
        body.color = android.graphics.Color.WHITE
        body.alpha = 255
        body.shader = LinearGradient(
            centre - radius, centre - radius, centre + radius, centre + radius,
            intArrayOf(mixColor(color, 0xFFFFFF, 0.35).toArgb(), color, mixColor(color, 0, 0.18).toArgb()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, body)
        canvas.drawPath(path, shine)
        return bitmap
    }
}

/** The picker fills the viewport, including the visible system bars. */
@Composable
fun SpinMorphIcons(
    modifier: Modifier = Modifier,
    step: String = "STEP 2 OF 3",
    question: String = "What do you want\nto build first?",
    action: String = "Choose this focus",
    autoPlay: Boolean = false,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val clock = remember { SpinMorphIconsClock() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val headline = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "spinmorphicons/spinmorphicons_BricolageHeadline.ttf",
        )))
    }
    val inter = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "spinmorphicons/spinmorphicons_Inter-Medium.ttf",
        )))
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // The host handles size changes without recreating the Activity; restore dark system icons.
    DisposableEffect(view, configuration) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        controller?.show(WindowInsetsCompat.Type.statusBars())
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }
    LaunchedEffect(active, reduced) {
        if (!active || reduced) return@LaunchedEffect
        // Absolute active frame time; no accumulated animation values or per-frame composition.
        val base = clock.elapsed
        val first = withFrameNanos { it }
        while (true) withFrameNanos { clock.advance(base + (it - first) / 1e9, auto) }
    }

    val d = density.density
    val artwork = remember(d) { SpinMorphIconsArtwork(d) }
    val miniPath = remember { android.graphics.Path() }
    val miniPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val textPlatform = remember { PlatformTextStyle(includeFontPadding = false) }
    val lineHeight = remember { LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None) }

    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(blend(clock.index) { it.tint }) }) {
        val safeTop = WindowInsets.safeDrawing.getTop(density) / d
        val safeBottom = WindowInsets.safeDrawing.getBottom(density) / d
        val cy = maxHeight.value * 0.44f
        val stripY = maxHeight.value - safeBottom - 16f - 56f - 46f
        val labelTop = cy + 150f

        Box(Modifier.fillMaxSize().pointerInput(reduced, d) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                clock.begin()
                var panning = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - down.position
                        if (!panning && hypot(delta.x, delta.y) >= 4f * d) panning = true
                        if (panning) {
                            change.consume()
                            if (clock.drag(delta.x / d.toDouble())) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                } finally {
                    clock.release()
                }
            }
        }) {
            Column(Modifier.offset(y = (safeTop + 14f).dp).padding(horizontal = 24.dp).fillMaxWidth()) {
                BasicText(step, modifier = Modifier.graphicsLayer { alpha = 0.7f },
                    color = { blend(clock.index) { it.deep } },
                    style = TextStyle(fontFamily = inter, fontSize = 12.sp, letterSpacing = 2.sp,
                        platformStyle = textPlatform, lineHeightStyle = lineHeight))
                Spacer(Modifier.height(10.dp))
                BasicText(question, color = { blend(clock.index) { it.deep } },
                    style = TextStyle(fontFamily = headline, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp,
                        platformStyle = textPlatform, lineHeightStyle = lineHeight))
            }
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Focus icon. Swipe to change." }) {
                val index = clock.index
                drawIntoCanvas { target ->
                    val c = target.nativeCanvas
                    c.drawBitmap(artwork.frame(index), size.width / 2 - artwork.centre, cy * d - artwork.centre, null)
                    for (k in FOCUSES.indices) {
                        val distance = abs(wrap(index) - k)
                        val selected = 1 - smooth(min(distance, FOCUSES.size - distance) / 0.6)
                        val x = size.width / 2 + (k - (FOCUSES.size - 1) / 2f) * 50f * d
                        val y = stripY * d
                        if (selected > 0.01) {
                            miniPaint.color = android.graphics.Color.WHITE
                            miniPaint.alpha = rounded(255 * selected).toInt()
                            c.drawCircle(x, y, (23 * (0.7 + 0.3 * selected)).toFloat() * d, miniPaint)
                        }
                        val color = if (selected > 0.5) FOCUSES[k].rgb else FOCUSES[k].deep
                        miniPaint.color = color or (0xFF shl 24)
                        miniPaint.alpha = rounded(255 * (0.3 + 0.7 * selected)).toInt()
                        outline(k.toDouble(), MINI * (1 + 0.35 * selected) * d, 0.0, x, y, miniPath)
                        c.drawPath(miniPath, miniPaint)
                    }
                }
            }
            Box(Modifier.offset(y = labelTop.dp).padding(horizontal = 20.dp).fillMaxWidth().height(100.dp)) {
                FOCUSES.forEachIndexed { k, focus ->
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer {
                            val fade = labelFade(clock.index, k)
                            alpha = 1 - fade
                            translationY = 14f * d * fade
                            scaleX = 0.94f + 0.06f * (1 - fade)
                            scaleY = scaleX
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(focus.name, style = TextStyle(color = Color(focus.deep or (0xFF shl 24)),
                            fontFamily = headline, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-1.5).sp,
                            textAlign = TextAlign.Center, platformStyle = textPlatform, lineHeightStyle = lineHeight))
                        Spacer(Modifier.height(2.dp))
                        BasicText(focus.line, style = TextStyle(color = Color(focus.deep or (0xFF shl 24)).copy(alpha = 0.8f),
                            fontFamily = inter, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                            platformStyle = textPlatform, lineHeightStyle = lineHeight))
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 16f).dp)
                .fillMaxWidth().height(56.dp).drawBehind {
                    drawRoundRect(blend(clock.index) { it.deep }, cornerRadius = CornerRadius(28f * d))
                }.semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = TextStyle(color = Color.White, fontFamily = inter, fontSize = 17.sp,
                platformStyle = textPlatform))
        }
    }
}
