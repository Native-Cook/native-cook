package com.ngocdevv.nativecook.blocks.cards.donutflavourpicker

import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Faithful RN port: 290 dp doughnuts roll over a 215 dp gap, centred at 43% height.
// LOOP = 7.2 s: three 2.4 s stops, holding for 1.6 s and rolling for 0.8 s with
// ease-in-out cubic. Landing squash is 0.06 exp(-7t) sin(16t), for 0 < t <= 0.8.
// Drag: 215 dp/stop, selection haptics at rounded-index crossings; release snaps
// for 0.4 s, holds 2 s, then resumes at 1 s into the selected autoplay stop.
// Frame values are read only in Canvas; images, fonts and shadows decode once.

/** Seconds. */
const val LOOP = 7.2
private const val STOP = 2.4
private const val SWITCH = 1.6
private const val SIZE = 290f
private const val GAP = 215f
private const val SNAP = 0.4
private const val HOLD = 2.0
private const val INK = 0xFF2A1A14.toInt()

private data class DonutFlavour(
    val word: String, val name: String, val notes: String,
    val price: String, val tint: Int, val deep: Int,
)

private val flavours = listOf(
    DonutFlavour("Berry", "Strawberry glaze", "Brioche dough, fresh strawberry icing, white sprinkles.", "$3.20", 0xFFFBE1E8.toInt(), 0xFFB0244F.toInt()),
    DonutFlavour("Cocoa", "Dark chocolate", "Seventy per cent cocoa glaze with gold sugar pearls.", "$3.40", 0xFFF0E2D6.toInt(), 0xFF5A2E16.toInt()),
    DonutFlavour("Matcha", "Matcha drizzle", "Ceremonial matcha icing and a white chocolate drizzle.", "$3.60", 0xFFE2EFD8.toInt(), 0xFF3D6B2A.toInt()),
)

private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
private fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
private fun wrap(i: Int) = ((i % 3) + 3) % 3
private fun offset(j: Int, index: Double): Double {
    var r = j - ((index % 3 + 3) % 3)
    if (r > 1.5) r -= 3
    if (r <= -1.5) r += 3
    return r
}
private fun landing(t: Double) = if (t <= 0 || t > 0.8) 0.0 else 0.06 * exp(-7 * t) * sin(16 * t)
private fun jsRound(x: Double) = floor(x + 0.5)
private fun mix(a: Int, b: Int, u: Double): Int = Color.rgb(
    (Color.red(a) * (1 - u) + Color.red(b) * u).roundToInt(),
    (Color.green(a) * (1 - u) + Color.green(b) * u).roundToInt(),
    (Color.blue(a) * (1 - u) + Color.blue(b) * u).roundToInt(),
)

private data class DonutFrame(val index: Double = 0.0, val landed: Double = 1.0)

/** RN's elapsed/origin/manual state machine, driven by an active frame clock. */
private class DonutDriver {
    var elapsed = 0.0
    var origin = 0.0
    var touching = false
    var manual = false
    var released = -1.0
    var grabbed = 0.0
    var from = 0.0
    var snapTo = 0.0
    var lastStep = 0.0
    var frame by mutableStateOf(DonutFrame())

    fun tick(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching) return
        if (manual) {
            val since = elapsed - released
            if (since < SNAP) {
                frame = DonutFrame(from + (snapTo - from) * ease(since / SNAP), 1.0)
            } else {
                frame = DonutFrame(snapTo, since - SNAP)
                if (since > SNAP + HOLD) {
                    manual = false
                    origin = elapsed - wrap(snapTo.toInt()) * STOP - 1.0
                }
            }
            return
        }
        if (!autoPlay) return
        val t = ((elapsed - origin) % LOOP + LOOP) % LOOP
        val k = floor(t / STOP)
        val u = t - k * STOP
        frame = DonutFrame(k + ease(clamp((u - SWITCH) / (STOP - SWITCH))), u)
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = frame.index
        lastStep = jsRound(frame.index)
    }

    fun drag(translation: Float): Boolean {
        val i = grabbed - translation / GAP
        frame = DonutFrame(i, 1.0)
        val next = jsRound(i)
        if (next == lastStep) return false
        lastStep = next
        return true
    }

    fun finish() {
        touching = false
        from = frame.index
        snapTo = jsRound(frame.index)
        released = elapsed
    }
}

@Composable
fun DonutFlavourPicker(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    brand: String = "Glaze & Co.",
    action: String = "Add to box",
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driver = remember { DonutDriver() }
    val art = remember(context) { DonutArt(context) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val reduced = rememberDonutReducedMotion()
    var touches by remember { mutableIntStateOf(0) }
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    DonutSystemBars()

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reduced, autoPlay, if (autoPlay) 0 else touches) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (autoPlay || driver.manual) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                driver.tick(dt, autoPlay)
            }
        }
    }

    Canvas(
        modifier.fillMaxSize()
            .semantics { contentDescription = "Doughnut flavours. Swipe to roll to the next one. $brand. $action." }
            .pointerInput(reduced, density.density, safeBottom) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The RN footer sits over the full-screen pan detector.
                    if (down.position.y / density.density >= size.height / density.density - safeBottom - 180f) return@awaitEachGesture
                    driver.begin()
                    touches++
                    var activated = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = (change.position.x - down.position.x) / density.density
                            val dy = (change.position.y - down.position.y) / density.density
                            if (dx * dx + dy * dy >= 16f) activated = true
                            if (activated && change.pressed) {
                                if (driver.drag(dx)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                change.consume()
                            }
                        } while (change.pressed)
                    } finally {
                        driver.finish()
                    }
                }
            },
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(density.density, density.density)
        art.draw(canvas, size.width / density.density, size.height / density.density, safeTop, safeBottom, driver.frame, brand, action)
        canvas.restore()
    }
}

/** Static images/fonts/blurred shadow are decoded or rasterized once. */
private class DonutArt(context: android.content.Context) {
    private val root = "donutflavourpicker/donutflavourpicker_"
    private val italic = Typeface.createFromAsset(context.assets, root + "InstrumentSerif-Italic.ttf")
    private val serif = Typeface.createFromAsset(context.assets, root + "InstrumentSerif-Regular.ttf")
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private fun weighted(weight: Int): Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(sans, weight, false)
        else Typeface.create(if (weight == 600) "sans-serif-medium" else "sans-serif", if (weight == 600) Typeface.NORMAL else Typeface.BOLD)
    private val bold = weighted(700)
    private val heavy = weighted(800)
    private val semibold = weighted(600)
    private val images = listOf("strawberry", "chocolate", "matcha").map { name ->
        context.assets.open(root + "donut-$name.png").use { BitmapFactory.decodeStream(it)!! }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Skia drawImageRect uses nearest-neighbour sampling in the RN reference.
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val shadow = Bitmap.createBitmap(320, 130, Bitmap.Config.ARGB_8888).also { bitmap ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(56, 60, 30, 20)
            // Android radius -> sigma = radius * 0.57735 + 0.5; RN uses sigma 12.
            maskFilter = BlurMaskFilter((12f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
        AndroidCanvas(bitmap).drawOval(55.6f, 45f, 264.4f, 76.9f, p)
    }

    private fun textPaint(size: Float, face: Typeface, color: Int = INK, opacity: Double = 1.0, tracking: Float = 0f): Paint = paint.apply {
        typeface = face
        textSize = size
        this.color = color
        alpha = (Color.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
        letterSpacing = tracking / size
    }

    private fun line(c: AndroidCanvas, text: String, x: Float, top: Float, size: Float, face: Typeface,
                     color: Int = INK, opacity: Double = 1.0, tracking: Float = 0f, lineHeight: Float? = null) {
        val p = textPaint(size, face, color, opacity, tracking)
        val fm = p.fontMetrics
        val natural = fm.descent - fm.ascent
        // TextKit puts a shorter explicit line box against the descent; extra
        // leading in a taller line box is shared above and below the baseline.
        val leading = (lineHeight ?: natural) - natural
        val baseline = top - fm.ascent + if (leading < 0) leading else leading / 2
        c.drawText(text, x, baseline, p)
    }

    private fun width(text: String, size: Float, face: Typeface) = textPaint(size, face).measureText(text)
    private fun height(size: Float, face: Typeface): Float { val fm = textPaint(size, face).fontMetrics; return fm.descent - fm.ascent }
    private fun box(c: AndroidCanvas, left: Float, top: Float, width: Float, height: Float, color: Int) {
        paint.color = color
        paint.alpha = Color.alpha(color)
        rect.set(left, top, left + width, top + height)
        c.drawRoundRect(rect, 29f, 29f, paint)
    }

    fun draw(c: AndroidCanvas, width: Float, height: Float, safeTop: Float, safeBottom: Float,
             frame: DonutFrame, brand: String, action: String) {
        val index = frame.index
        val base = floor(index).toInt()
        val fraction = index - floor(index)
        val tint = mix(flavours[wrap(base)].tint, flavours[wrap(base + 1)].tint, fraction)
        val deep = mix(flavours[wrap(base)].deep, flavours[wrap(base + 1)].deep, fraction)
        c.drawColor(tint)
        val cx = width / 2
        val cy = height * 0.43f
        for (k in flavours.indices) {
            val r = offset(k, index)
            val f = flavours[k]
            line(c, f.word, (width - this.width(f.word, 168f, italic)) / 2 + (r * 140).toFloat(), cy - 100f,
                168f, italic, f.deep, 0.16 * (1 - clamp(abs(r) * 1.4)), lineHeight = 200f)
        }
        c.save()
        c.clipRect(24f, safeTop + 8, width - 24, safeTop + 42)
        line(c, "Doughnuts", 24f, safeTop + 8, 30f, italic)
        c.restore()
        line(c, brand, width - 24 - this.width(brand, 14f, bold), safeTop + 8 + (34 - this.height(14f, bold)) / 2,
            14f, bold, opacity = 0.6)

        val order = flavours.indices.sortedByDescending { abs(offset(it, index)) }
        for (j in order) {
            val r = offset(j, index).toFloat()
            val x = cx + r * GAP
            val scale = 1 - 0.5f * min(1f, abs(r))
            val s = SIZE * scale
            val squash = if (abs(r) < 0.02) landing(frame.landed).toFloat() else 0f
            val turn = r * GAP / (s / 2) * (180 / PI).toFloat()
            c.save()
            c.translate(x, cy + s * 0.4f)
            c.scale(scale, scale)
            c.drawBitmap(shadow, -160f, -45f, shadowPaint)
            c.restore()
            c.save()
            c.translate(x, cy + s / 2 * squash)
            c.scale(1 + squash, 1 - squash)
            c.rotate(turn)
            rect.set(-s / 2, -s / 2, s / 2, s / 2)
            c.drawBitmap(images[j], null, rect, imagePaint)
            c.restore()
        }

        val footerTop = height - safeBottom - 180f
        for (k in flavours.indices) {
            val opacity = 1 - smooth(abs(offset(k, index)) / 0.5)
            if (opacity <= 0) continue
            val f = flavours[k]
            val y = footerTop + (10 * (1 - opacity)).toFloat()
            line(c, f.name, 24f, y, 38f, serif, opacity = opacity, tracking = -0.5f)
            val nameBaseline = -textPaint(38f, serif).fontMetrics.ascent
            val priceBaseline = -textPaint(20f, heavy).fontMetrics.ascent
            line(c, f.price, width - 24 - this.width(f.price, 20f, heavy), y + nameBaseline - priceBaseline,
                20f, heavy, f.deep, opacity)
            val notesTop = y + this.height(38f, serif) + 4
            val maxWidth = min(300f, width - 48)
            val lines = wrapLines(f.notes, maxWidth)
            lines.forEachIndexed { n, text -> line(c, text, 24f, notesTop + n * 21, 15f, sans, opacity = 0.65 * opacity, lineHeight = 21f) }
        }
        val actionY = footerTop + 108
        box(c, 24f, actionY, 128f, 58f, Color.argb(179, 255, 255, 255))
        val stepLabels = listOf("−", "2", "+")
        val sizes = listOf(22f, 18f, 22f)
        val faces = listOf(semibold, heavy, semibold)
        val widths = stepLabels.indices.map { this.width(stepLabels[it], sizes[it], faces[it]) }
        val space = (128 - widths.sum()) / 3
        var x = 24 + space / 2
        stepLabels.indices.forEach { k ->
            line(c, stepLabels[k], x, actionY + (58 - this.height(sizes[k], faces[k])) / 2, sizes[k], faces[k])
            x += widths[k] + space
        }
        box(c, 164f, actionY, width - 188, 58f, deep)
        line(c, action, (164 + width - 24 - this.width(action, 17f, bold)) / 2,
            actionY + (58 - this.height(17f, bold)) / 2, 17f, bold, Color.WHITE)
    }

    private fun wrapLines(text: String, maxWidth: Float): List<String> {
        val p = textPaint(15f, sans)
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ')) {
            val next = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(next) > maxWidth && current.isNotEmpty()) { lines += current; current = word }
            else current = next
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}

@Composable
private fun rememberDonutReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun DonutSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}
