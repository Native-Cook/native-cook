package com.ngocdevv.nativecook.blocks.text.yearphotocounter

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Carry On, an exhibition guide. Port of YearPhotoCounter.tsx, number for number.
// Layout: top x24 / safeTop+8 / h34; year y0.13H, four 80x110 windows,
// Archivo Expanded Black 92; print 252x312 at (W/2, H/2-9), border 10/bottom30;
// caption x32 / yH/2+168; timeline bottom safeBottom+18.
// LOOP 8.8 s: four 2.2 s stops, each resting 1.4 s then changing over 0.8 s.
// Digit i rolls forward for 0.5 s after 0.08*i s, ease-in-out cubic.
// Outgoing: x -420, turn -24 degrees, cubic in over 0.45 s.
// Incoming: starts 0.25 s into change, drops 160 from scale 1.15 over 0.55 s,
// ease-out back k=1.4. Background, captions and marker use the same index.
// Touch: 4 dp activation, 240 dp/stop, selection haptic at rounded steps;
// release snaps over 0.4 s, holds 2 s, then resumes at the selected era.
// Reduced motion holds the first era and disables the frame clock and gesture.

/** Seconds. */
private const val LOOP = 8.8
private const val STOP = 2.2
private const val CHANGE = 1.4
private const val SNAP = 0.4
private const val HOLD = 2.0
private const val PER = 240.0
private const val ASSETS = "yearphotocounter/yearphotocounter_"
private const val INK = 0xFF1E1A16.toInt()
private const val PRINT_MARGIN = 48f

private data class Era(val year: Int, val title: String, val text: String, val background: Int, val tilt: Float)
private val ERAS = listOf(
    Era(1905, "The box camera", "Folded wood and leather. One roll, eight pictures, a week to develop.", 0xFFECE5D8.toInt(), -5f),
    Era(1957, "The city scooter", "Mint paint, chrome trim and a spare wheel strapped to the back.", 0xFFE1ECE3.toInt(), 4f),
    Era(1984, "The pocket tape player", "Ninety minutes of music, and a pencil to wind the tape back.", 0xFFEFE0E4.toInt(), -3f),
    Era(2021, "The wireless earbuds", "Two grams each. Lost behind the sofa within a month.", 0xFFDFE4EE.toInt(), 6f),
)
private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
private fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun back(x: Double) = 1 + 2.4 * (x - 1).pow(3) + 1.4 * (x - 1).pow(2)
private fun wrap(i: Int) = ((i % ERAS.size) + ERAS.size) % ERAS.size
// Match JS Math.round, including negative half-integers.
private fun step(x: Double) = floor(x + 0.5).toInt()
private fun digit(year: Int, i: Int) = year / when (i) { 0 -> 1000; 1 -> 100; 2 -> 10; else -> 1 } % 10
private fun indexAt(t: Double): Double {
    val stop = floor(t / STOP)
    return stop + clamp((t - stop * STOP - CHANGE) / (STOP - CHANGE))
}
// RN interpolates integer sRGB channels, rather than a perceptual colour space.
private fun mix(a: Int, b: Int, u: Double): Int {
    fun channel(shift: Int) = (((a shr shift) and 255) * (1 - u) + ((b shr shift) and 255) * u).roundToInt()
    return NativeColor.rgb(channel(16), channel(8), channel(0))
}

private class Playback {
    var index by mutableDoubleStateOf(0.0)
    var manual by mutableStateOf(false)
    var elapsed = 0.0
    var origin = 0.0
    var touching = false
    var grabbed = 0.0
    var from = 0.0
    var snapTo = 0.0
    var released = -1.0
    var lastStep = 0

    fun frame(now: Double, autoPlay: Boolean) {
        elapsed = now
        if (touching) return
        if (manual) {
            val since = now - released
            index = if (since < SNAP) from + (snapTo - from) * ease(clamp(since / SNAP)) else snapTo
            if (since > SNAP + HOLD) {
                manual = false
                origin = now - wrap(snapTo.toInt()) * STOP
            }
            return
        }
        if (autoPlay) index = indexAt((now - origin) % LOOP)
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = index
        lastStep = step(index)
    }

    fun release() {
        touching = false
        from = index
        snapTo = step(index).toDouble()
        released = elapsed
    }
}

/** Text is measured once; native font metrics avoid TextView's extra font padding. */
private class Lettering(black: Typeface, mono: Typeface, width: Float) {
    val brand = paint(black, 16f)
    val meta = paint(mono, 12f, 0.6f)
    val digits = paint(black, 92f)
    val title = paint(black, 18f)
    val description = paint(mono, 13f, 0.72f)
    val label = paint(mono, 12f, 0.7f)
    val printLabel = paint(mono, 10f, 0.6f)
    val lines = ERAS.map { wrapText(it.text, description, width - 64) }
    val titleHeight = lineHeight(title)
    val labelHeight = lineHeight(label)
    val labelWidths = ERAS.map { label.measureText(it.year.toString()) }

    companion object {
        fun paint(face: Typeface, size: Float, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = size
            color = INK
            this.alpha = (alpha * 255).roundToInt()
        }
        fun lineHeight(paint: Paint) = ceil(paint.fontMetrics.run { descent - ascent + leading })
        private fun wrapText(text: String, paint: Paint, width: Float): List<String> {
            val lines = mutableListOf<String>()
            var line = ""
            for (word in text.split(' ')) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && paint.measureText(next) > width) {
                    lines += line
                    line = word
                } else line = next
            }
            if (line.isNotEmpty()) lines += line
            return lines
        }
    }
}

/** Pre-render each paper, photograph, label and shadow once. */
private fun printBitmap(assets: AssetManager, k: Int, density: Float, lettering: Lettering): Bitmap {
    val bitmap = Bitmap.createBitmap(((252 + 2 * PRINT_MARGIN) * density).roundToInt(), ((312 + 2 * PRINT_MARGIN) * density).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = NativeCanvas(bitmap)
    canvas.scale(density, density)
    canvas.translate(PRINT_MARGIN, PRINT_MARGIN)
    val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFBF8F1.toInt()
        setShadowLayer(14f, 0f, 8f, NativeColor.argb(56, 0, 0, 0))
    }
    canvas.drawRect(0f, 0f, 252f, 312f, paper)
    val photo = assets.open("${ASSETS}year-${ERAS[k].year}.jpg").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    val target = RectF(10f, 10f, 242f, 282f)
    val scale = max(target.width() / photo.width, target.height() / photo.height)
    val rect = RectF(target.centerX() - photo.width * scale / 2, target.centerY() - photo.height * scale / 2, target.centerX() + photo.width * scale / 2, target.centerY() + photo.height * scale / 2)
    val save = canvas.save()
    canvas.clipRect(target)
    canvas.drawBitmap(photo, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restoreToCount(save)
    photo.recycle()
    val label = "No. 0${k + 1} · ${ERAS[k].year}"
    val labelHeight = Lettering.lineHeight(lettering.printLabel)
    canvas.drawText(label, 12f, 312 - 9 - labelHeight - lettering.printLabel.fontMetrics.ascent, lettering.printLabel)
    return bitmap
}

private fun NativeCanvas.text(text: String, x: Float, top: Float, paint: Paint) {
    drawText(text, x, top - paint.fontMetrics.ascent, paint)
}

private fun NativeCanvas.drawPrint(bitmap: Bitmap, paint: Paint, width: Float, height: Float, opacity: Double, dx: Double, dy: Double, turn: Double, scale: Double) {
    if (opacity <= 0) return
    val save = save()
    translate(width / 2 + dx.toFloat(), height * 0.5f - 9 + dy.toFloat())
    rotate(turn.toFloat())
    scale(scale.toFloat(), scale.toFloat())
    paint.alpha = (255 * opacity).roundToInt().coerceIn(0, 255)
    drawBitmap(bitmap, null, RectF(-126 - PRINT_MARGIN, -156 - PRINT_MARGIN, 126 + PRINT_MARGIN, 156 + PRINT_MARGIN), paint)
    restoreToCount(save)
}

/** Full-screen, offline guide. [autoPlay] walks the four eras every [LOOP] seconds. */
@Composable
fun YearPhotoCounter(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current.density
    val playback = remember(reduced) { Playback() }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val black = remember(context) { Typeface.createFromAsset(context.assets, "${ASSETS}ArchivoExpanded-Black.ttf") }
    val mono = remember(context) { Typeface.createFromAsset(context.assets, "${ASSETS}JetBrainsMono-Medium.ttf") }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller?.isAppearanceLightStatusBars = status
            if (navigation != null) controller?.isAppearanceLightNavigationBars = navigation
        }
    }
    LaunchedEffect(active, reduced, autoPlay, playback.manual) {
        if (!active || reduced || (!autoPlay && !playback.manual)) return@LaunchedEffect
        val elapsed = playback.elapsed
        var first = 0L
        while (true) withFrameNanos { now ->
            if (first == 0L) first = now
            playback.frame(elapsed + (now - first) / 1e9, autoPlay)
        }
    }

    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth / density
        val height = constraints.maxHeight / density
        val lettering = remember(black, mono, width) { Lettering(black, mono, width) }
        val prints = remember(context, density, black, mono) { ERAS.indices.map { printBitmap(context.assets, it, density, lettering) } }
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Carry On. Room 3, 1905 to 2021. Swipe to explore the box camera, city scooter, pocket tape player and wireless earbuds." }
                .pointerInput(playback, reduced, density) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        playback.begin()
                        var moved = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val translation = (change.position - down.position) / density
                                if (!moved && hypot(translation.x, translation.y) < 4f) continue
                                moved = true
                                change.consume()
                                playback.index = playback.grabbed - translation.x / PER
                                val next = step(playback.index)
                                if (next != playback.lastStep) {
                                    playback.lastStep = next
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } finally {
                            playback.release()
                        }
                    }
                },
        ) {
            // Per-frame state is read only in this drawing, never in the static layout.
            val index = playback.index
            val base = floor(index).toInt()
            val fraction = index - floor(index)
            val a = wrap(base)
            val b = wrap(base + 1)
            val eased = ease(fraction)
            val canvas = drawContext.canvas.nativeCanvas
            val save = canvas.save()
            canvas.scale(density, density)
            canvas.drawColor(mix(ERAS[a].background, ERAS[b].background, eased))

            canvas.text("Carry On", 24f, safeTop + 8 + (34 - Lettering.lineHeight(lettering.brand)) / 2, lettering.brand)
            val meta = "Room 3 · 1905 – 2021"
            canvas.text(meta, width - 24 - lettering.meta.measureText(meta), safeTop + 8 + (34 - Lettering.lineHeight(lettering.meta)) / 2, lettering.meta)

            for (i in 0..3) {
                val old = digit(ERAS[a].year, i)
                val next = digit(ERAS[b].year, i)
                val steps = if (next >= old) next - old else next + 10 - old
                val local = clamp((fraction * 0.8 - 0.08 * i) / 0.5)
                val position = old + steps * ease(local)
                val x = (width - 320) / 2 + i * 80
                val y = height * 0.13f
                val clip = canvas.save()
                canvas.clipRect(x, y, x + 80, y + 110)
                val row = floor(position).toInt()
                val fm = lettering.digits.fontMetrics
                val baseline = (110 - (fm.descent - fm.ascent)) / 2 - fm.ascent
                for (r in row..min(row + 1, 19)) {
                    val text = (r % 10).toString()
                    canvas.drawText(text, x + (80 - lettering.digits.measureText(text)) / 2, y + ((r - position) * 110).toFloat() + baseline, lettering.digits)
                }
                canvas.restoreToCount(clip)
            }

            val out = clamp(fraction * 0.8 / 0.45).pow(3)
            canvas.drawPrint(prints[a], bitmapPaint, width, height, 1 - clamp((out - 0.7) / 0.3), -420 * out, 0.0, ERAS[a].tilt - 24 * out, 1.0)
            val incoming = clamp((fraction * 0.8 - 0.25) / 0.55)
            val landed = back(incoming)
            canvas.drawPrint(prints[b], bitmapPaint, width, height, clamp(incoming * 4), 0.0, -160 * (1 - landed), ERAS[b].tilt + 14 * (1 - landed), 1.15 - 0.15 * landed)

            for (k in ERAS.indices) {
                var distance = abs(index - k) % ERAS.size
                distance = min(distance, ERAS.size - distance)
                val opacity = 1 - clamp(distance / 0.5)
                if (opacity <= 0) continue
                lettering.title.alpha = (255 * opacity).roundToInt()
                lettering.description.alpha = (255 * 0.72 * opacity).roundToInt()
                val y = height * 0.5f + 168 + (8 * (1 - opacity)).toFloat()
                val title = ERAS[k].title
                canvas.text(title, (width - lettering.title.measureText(title)) / 2, y, lettering.title)
                val descY = y + lettering.titleHeight + 6
                val fm = lettering.description.fontMetrics
                val baseline = (19 - (fm.descent - fm.ascent)) / 2 - fm.ascent
                lettering.lines[k].forEachIndexed { row, line ->
                    canvas.drawText(line, (width - lettering.description.measureText(line)) / 2, descY + row * 19 + baseline, lettering.description)
                }
            }

            val trackWidth = width - 64
            val trackTop = height - safeBottom - 18 - lettering.labelHeight - 8 - 18
            fill.color = INK
            fill.alpha = (255 * 0.25).roundToInt()
            canvas.drawRect(32f, trackTop + 8, width - 32, trackTop + 10, fill)
            val gap = (trackWidth + 28 - lettering.labelWidths.sum()) / 3
            var labelX = 18f
            for (k in ERAS.indices) {
                val x = 32 + k / 3f * trackWidth
                fill.alpha = (255 * 0.45).roundToInt()
                canvas.drawRect(x - 1, trackTop + 4, x + 1, trackTop + 14, fill)
                canvas.text(ERAS[k].year.toString(), labelX, trackTop + 26, lettering.label)
                labelX += lettering.labelWidths[k] + gap
            }
            fill.alpha = 255
            val marker = a + (b - a) * eased
            canvas.drawCircle(32 + (marker / 3 * trackWidth).toFloat(), trackTop + 9, 7f, fill)
            canvas.restoreToCount(save)
        }
    }
}
