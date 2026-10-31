package com.ngocdevv.nativecook.blocks.text.postermorphcarousel

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seconds. Five posters, each resting for .95 s, followed by a .65 s slat change. */
const val LOOP = 8.0
private const val STOP = 1.6
private const val CHANGE = 0.95
private const val SLATS = 6
private const val SLAT_DELAY = 0.05
private const val SLAT_RUN = 0.4
private const val COUNT = 5
private const val PER = 260.0
private const val HOLD = 2.0
private const val SNAP = 0.4
private val BACKGROUND = Color(0xFF141416)

private fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)
private fun easeOutCubic(value: Double) = 1.0 - (1.0 - value).pow(3)
private fun easeInOutCubic(value: Double) =
    if (value < 0.5) 4.0 * value * value * value else 1.0 - (-2.0 * value + 2.0).pow(3) / 2.0
private fun positiveModulo(value: Double, modulus: Double) = ((value % modulus) + modulus) % modulus
private fun jsRound(value: Double) = floor(value + 0.5)

private fun indexAt(time: Double): Double {
    val k = floor(time / STOP)
    return k + clamp01((time - k * STOP - CHANGE) / (STOP - CHANGE))
}

private fun slatAt(slat: Int, fraction: Double): Float =
    easeOutCubic(clamp01((fraction * (SLAT_RUN + SLAT_DELAY * (SLATS - 1)) - SLAT_DELAY * slat) / SLAT_RUN)).toFloat()

/** Only index is observable: the canvas and rolling counter read it without recomposing the UI. */
private class PosterMorphCarouselPlayback {
    var index by mutableDoubleStateOf(0.0)
    var elapsed = 0.0
    var origin = 0.0
    var touching = false
    var manual = false
    var released = -1.0
    var grabbed = 0.0
    var from = 0.0
    var snapTo = 0.0
    var lastStep = 0.0

    fun frame(delta: Double, autoPlay: Boolean) {
        elapsed += delta
        if (touching) return
        if (manual) {
            val since = elapsed - released
            index = if (since < SNAP) from + (snapTo - from) * easeInOutCubic(since / SNAP) else snapTo
            if (since > SNAP + HOLD) {
                manual = false
                origin = elapsed - positiveModulo(snapTo, COUNT.toDouble()) * STOP
            }
            return
        }
        if (autoPlay) index = indexAt(positiveModulo(elapsed - origin, LOOP))
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = index
        lastStep = jsRound(index)
    }

    fun release() {
        touching = false
        from = index
        snapTo = jsRound(index)
        released = elapsed
    }
}

private fun color(hex: String) = NativeColor.parseColor(hex)

/** Drawing helpers use the same local point coordinates and baseline placement as the RN pictures. */
private class PosterMorphCarouselArtwork(private val font: Typeface) {
    private val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textSize = 100f
        isSubpixelText = true
    }

    private class TextRun(val path: Path, val width: Float)
    private val runs = mutableMapOf<String, TextRun>()

    // Skia drawText is unshaped: advances at size 100 round separately. On iOS,
    // measureText returns the union of outward-rounded ink boxes plus 1 px AA padding.
    // Measuring Android's shaped text advance instead changes the fitted poster type.
    private fun run(text: String): TextRun = runs.getOrPut(text) {
        val path = Path()
        val ink = RectF()
        var hasInk = false
        var x = 0f
        text.forEach { char ->
            val glyph = Path()
            val string = char.toString()
            measure.getTextPath(string, 0, string.length, 0f, 0f, glyph)
            val bounds = RectF()
            @Suppress("DEPRECATION")
            glyph.computeBounds(bounds, true)
            if (!bounds.isEmpty) {
                val padded = RectF(floor(bounds.left) - 1 + x, floor(bounds.top) - 1, ceil(bounds.right) + 1 + x, ceil(bounds.bottom) + 1)
                if (hasInk) ink.union(padded) else ink.set(padded)
                hasInk = true
                path.addPath(glyph, x, 0f)
            }
            x += measure.measureText(string).roundToInt()
        }
        TextRun(path, ink.width())
    }

    private fun textWidth(text: String) = run(text).width
    private fun fit(text: String, width: Float) = width / textWidth(text) * 100f

    private fun word(c: NativeCanvas, text: String, x: Float, y: Float, size: Float, tint: Int = NativeColor.WHITE, shader: Shader? = null) {
        val paint = Paint(measure).apply {
            color = tint
            this.shader = shader
        }
        c.save()
        c.translate(x, y)
        c.scale(size / 100f, size / 100f)
        c.drawPath(run(text).path, paint)
        c.restore()
    }

    private fun fill(c: NativeCanvas, w: Float, h: Float, a: String, b: String) {
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, w * .4f, h, color(a), color(b), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, paint)
    }

    private fun blob(c: NativeCanvas, x: Float, y: Float, r: Float, light: String, deep: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x - r * .35f, y - r * .4f, r * 1.5f, color(light), color(deep), Shader.TileMode.CLAMP)
        }
        c.drawCircle(x, y, r, paint)
    }

    private fun soft(c: NativeCanvas, x: Float, y: Float, r: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NativeColor.argb(191, 255, 170, 40)
            // Android's radius is converted from the reference's Skia sigma (18 pt).
            maskFilter = BlurMaskFilter((18f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(x, y, r, paint)
    }

    private fun now(c: NativeCanvas, w: Float, h: Float) {
        fill(c, w, h, "#2F5BFF", "#62B6FF")
        blob(c, w * .3f, h * .22f, w * .32f, "#FFE07A", "#FF7A00")
        blob(c, w * .72f, h * .12f, w * .2f, "#FFD36B", "#F29100")
        soft(c, w * .85f, h * .45f, w * .16f)
        word(c, "2026", 20f, h * .64f, 34f)
        word(c, "NOW", 18f, h * .88f, fit("NOW", w - 36))
        word(c, "KILN 26", 20f, h - 18, 11f, NativeColor.argb(217, 255, 255, 255))
    }

    private fun next(c: NativeCanvas, w: Float, h: Float) {
        fill(c, w, h, "#FFFFFF", "#F4F1EC")
        blob(c, w * .25f, h * .15f, w * .2f, "#FFB15C", "#FF6A2B")
        blob(c, w * .5f, h * .2f, w * .24f, "#B48CFF", "#5B2BE8")
        blob(c, w * .78f, h * .12f, w * .18f, "#FF8AC4", "#E8207A")
        blob(c, w * .35f, h * .33f, w * .12f, "#FF9A9A", "#D8264A")
        c.drawRect(w * .16f, h * .62f, w * .26f, h, Paint().apply { color = color("#3D7BFF") })
        blob(c, w * .7f, h * .95f, w * .28f, "#FFE36B", "#FF9E1A")
        blob(c, w * .42f, h * .98f, w * .16f, "#FF6B8B", "#D11A4A")
        word(c, "Kiln", 24f, h * .43f, 18f, color("#5F6368"))
        word(c, "Next", 22f, h * .56f, fit("Next", w * .6f), color("#111111"))
    }

    private fun way(c: NativeCanvas, w: Float, h: Float) {
        fill(c, w, h, "#FF5FA2", "#FF9E2B")
        blob(c, w * .42f, h * .42f, w * .3f, "#7CFFB8", "#00A86B")
        blob(c, w * .7f, h * .6f, w * .26f, "#64E1FF", "#0072FF")
        blob(c, w * .3f, h * .7f, w * .24f, "#9BFF6B", "#1DB954")
        blob(c, w * .65f, h * .3f, w * .18f, "#5BE7C4", "#008F7A")
        val size = fit("The new", w - 40)
        word(c, "The new", 20f, h * .16f, size)
        word(c, "way to", w - 20 - textWidth("way to") * size / 100f, h * .56f, size)
        word(c, "build", 20f, h - 24, size)
    }

    private fun isNow(c: NativeCanvas, w: Float, h: Float) {
        fill(c, w, h, "#1C1C1F", "#121214")
        val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            strokeCap = Paint.Cap.ROUND
            color = color("#7CFF4D")
        }
        c.drawLine(w * .8f, h * .08f, w * .8f, h * .22f, plus)
        c.drawLine(w * .73f, h * .15f, w * .87f, h * .15f, plus)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 13f
            shader = SweepGradient(w * .52f, h * .16f, intArrayOf(color("#FF5FA2"), color("#FFD54D"), color("#6BB8FF"), color("#FF5FA2")), null)
        }
        c.drawCircle(w * .52f, h * .16f, w * .09f, ring)
        repeat(4) { i ->
            val a = i * Math.PI / 2 + Math.PI / 4
            blob(c, w * .66f + cos(a).toFloat() * 16, h * .27f + sin(a).toFloat() * 16, 15f, "#FFB84D", "#FF7A00")
        }
        val pixel = Paint().apply { color = color("#3D6BFF") }
        repeat(5) { i ->
            repeat(5) { j ->
                if ((i + j * 2) % 3 != 0) c.drawRect(w * .1f + i * 12, h * .1f + j * 12, w * .1f + i * 12 + 10, h * .1f + j * 12 + 10, pixel)
            }
        }
        val size = fit("now", w - 40)
        word(c, "is", (w - textWidth("is") * size / 100f) / 2, h * .68f, size)
        word(c, "now", 20f, h - 24, size, shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(color("#5BE7FF"), color("#B48CFF"), NativeColor.WHITE), null, Shader.TileMode.CLAMP))
    }

    private fun make(c: NativeCanvas, w: Float, h: Float) {
        fill(c, w, h, "#3B2BFF", "#5E8BFF")
        fun band(points: FloatArray, tint: String) {
            val path = Path().apply {
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                close()
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(tint) })
        }
        band(floatArrayOf(0f, h * .15f, w * .45f, 0f, w, h * .55f, w, h * .85f), "#FFC21A")
        band(floatArrayOf(0f, h * .6f, w * .5f, h * .45f, w * .4f, h, 0f, h), "#FF8AC4")
        fun tube(points: FloatArray, width: Float, a: String, b: String) {
            val path = Path().apply {
                moveTo(points[0], points[1])
                cubicTo(points[2], points[3], points[4], points[5], points[6], points[7])
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(0f, points[1] - width, 0f, points[1] + width, color(a), color(b), Shader.TileMode.MIRROR)
            }
            c.drawPath(path, paint)
        }
        tube(floatArrayOf(w * .15f, h * .38f, w * .4f, h * .1f, w * .75f, h * .2f, w * .7f, h * .45f), 54f, "#FF8A94", "#E81A3A")
        tube(floatArrayOf(w * .7f, h * .45f, w * .66f, h * .65f, w * .4f, h * .6f, w * .35f, h * .72f), 44f, "#FFC8E2", "#F06AA8")
        blob(c, w * .2f, h * .4f, 40f, "#8FB8FF", "#2F5BFF")
        word(c, "Make it", 20f, h - 28, fit("Make it", w - 40))
    }

    fun posters(w: Float, h: Float, density: Float): List<Bitmap> = List(COUNT) { index ->
        Bitmap.createBitmap(ceil(w * density).toInt(), ceil(h * density).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = NativeCanvas(bitmap)
            canvas.scale(density, density)
            when (index) {
                0 -> now(canvas, w, h)
                1 -> next(canvas, w, h)
                2 -> way(canvas, w, h)
                3 -> isNow(canvas, w, h)
                else -> make(canvas, w, h)
            }
            // Software clipPath has hard edges. Erase the outside with an antialiased
            // path instead, preserving the reference's smooth cached rounded corners.
            val outside = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(-1f, -1f, w + 1, h + 1, Path.Direction.CW)
                addRoundRect(0f, 0f, w, h, 18f, 18f, Path.Direction.CW)
            }
            canvas.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
    }
}

/** Cached software shadow: mask filters work consistently on every supported Android version. */
private fun posterShadow(w: Float, h: Float, density: Float): Bitmap {
    val bleed = 80f
    return Bitmap.createBitmap(ceil((w + bleed * 2) * density).toInt(), ceil((h + bleed * 2) * density).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = NativeCanvas(bitmap)
        canvas.scale(density, density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NativeColor.argb(128, 0, 0, 0)
            maskFilter = BlurMaskFilter((22f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(bleed + 10, bleed + 24, bleed + w - 10, bleed + 24 + h, 18f, 18f, paint)
    }
}

/**
 * A conference poster wall. Six delayed vertical slats reveal the next cached poster.
 * A horizontal drag scrubs 260 dp per poster, snaps for .4 s and holds for 2 s before autoplay.
 */
@Composable
fun PosterMorphCarousel(
    modifier: Modifier = Modifier,
    brand: String = "Kiln 26",
    action: String = "Get your ticket",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val playback = remember { PosterMorphCarouselPlayback() }
    val font = remember(context) { Typeface.createFromAsset(context.assets, "postermorphcarousel/postermorphcarousel_ArchivoExpanded-Black.ttf") }
    val family = remember(font) { FontFamily(androidx.compose.ui.text.font.Typeface(font)) }
    val artwork = remember(font) { PosterMorphCarouselArtwork(font) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BlockSystemBars(darkIcons = false)

    LaunchedEffect(lifecycle, reduced) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val delta = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                playback.frame(delta, auto)
            }
        }
    }

    val noPadding = PlatformTextStyle(includeFontPadding = false)
    val body = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .6f), platformStyle = noPadding)

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val width = constraints.maxWidth / density
        val height = constraints.maxHeight / density
        val w = width - 40f
        val h = (w * 1.325f).roundToInt().toFloat()
        val y = height * .48f - h / 2
        val posters = remember(artwork, w, h, density) { artwork.posters(w, h, density) }
        val shadow = remember(w, h, density) { posterShadow(w, h, density) }
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val cardRect = remember(w, h) { RectF(0f, 0f, w, h) }
        val shadowRect = remember(w, h) { RectF(-80f, -80f, w + 80f, h + 80f) }

        Box(
            Modifier.fillMaxSize().pointerInput(reduced, density) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    playback.begin()
                    var moved = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val translation = (change.position.x - down.position.x) / density
                            if (!moved && (change.position - down.position).getDistance() / density >= 4f) moved = true
                            if (moved) {
                                change.consume()
                                playback.index = playback.grabbed - translation / PER
                                val step = jsRound(playback.index)
                                if (step != playback.lastStep) {
                                    playback.lastStep = step
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                    } finally {
                        playback.release()
                    }
                }
            },
        ) {
            Row(
                Modifier.offset(x = 20.dp, y = insets.calculateTopPadding() + 8.dp).width(w.dp).height(34.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(brand, style = TextStyle(fontFamily = family, fontSize = 18.sp, color = Color.White, platformStyle = noPadding))
                Text("Poster wall", style = body.copy(fontSize = 14.sp, color = Color.White.copy(alpha = .55f)))
            }
            Text(
                "Five posters for five days of making",
                Modifier.offset(y = (y - 30).dp).fillMaxWidth().padding(horizontal = 20.dp),
                style = body.copy(textAlign = TextAlign.Center),
            )

            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Conference posters. Swipe to change poster." }) {
                val c = drawContext.canvas.nativeCanvas
                val index = playback.index
                val base = floor(index)
                val fraction = index - base
                val current = positiveModulo(base, COUNT.toDouble()).toInt()
                val next = (current + 1) % COUNT
                c.save()
                c.scale(density, density)
                c.translate(20f, y)
                c.drawBitmap(shadow, null, shadowRect, bitmapPaint)
                c.drawBitmap(posters[current], null, cardRect, bitmapPaint)
                if (fraction > 0) {
                    val sw = w / SLATS
                    repeat(SLATS) { slat ->
                        val progress = slatAt(slat, fraction)
                        if (progress > 0) {
                            c.save()
                            c.clipRect(slat * sw - .5f, 0f, (slat + 1) * sw + .5f, h)
                            c.translate(0f, (1 - progress) * h)
                            c.drawBitmap(posters[next], null, cardRect, bitmapPaint)
                            c.restore()
                        }
                    }
                }
                c.restore()
            }

            Row(
                Modifier.offset(x = 20.dp, y = (y + h + 14).dp).width(w.dp).height(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lisbon · 12–16 May 2026", style = body)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val counterPaint = remember(font) {
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 14f; color = NativeColor.WHITE; isSubpixelText = true }
                    }
                    val counterWidth = remember(counterPaint) { counterPaint.measureText("01") }
                    Canvas(Modifier.width(counterWidth.dp).height(20.dp)) {
                        val c = drawContext.canvas.nativeCanvas
                        c.save()
                        c.scale(density, density)
                        c.clipRect(0f, 0f, counterWidth, 20f)
                        c.translate(0f, -20f * positiveModulo(playback.index, COUNT.toDouble()).toFloat())
                        val baseline = 10f - (counterPaint.ascent() + counterPaint.descent()) / 2
                        repeat(COUNT + 1) { row -> c.drawText("0${row % COUNT + 1}", 0f, row * 20 + baseline, counterPaint) }
                        c.restore()
                    }
                    Text(" / 05", style = TextStyle(fontFamily = family, fontSize = 14.sp, color = Color.White.copy(alpha = .45f), platformStyle = noPadding))
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = insets.calculateBottomPadding() + 14.dp)
                .fillMaxWidth().height(58.dp).background(Color.White, RoundedCornerShape(29.dp)).semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = TextStyle(fontFamily = family, fontSize = 15.sp, color = BACKGROUND, platformStyle = noPadding))
        }
    }
}
