package com.ngocdevv.nativecook.blocks.media.morphingprintlayout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Morphing Print Layout — a film picker where one white print frame morphs between formats. A port of the
 SwiftUI block (MorphingPrintLayout.swift); the numbers are the same.

 Look: a white top strip (nav bar: back arrow, "Select Film" with a small black "?" badge, a sliders and a
 share glyph; under it a one-line pager of format names in 24 sp medium, the selected one black, the
 others grey, and five 6 dp dots). Below it an orange stage, #F4B043 at the top to #EE4D0F at the bottom,
 with the print frame centred in it (white, corner 3 dp, a warm shadow), its square wells filled with six
 summer photos. Under the frame a caption for the format; at the bottom an export button and a white
 "Choose photos" pill.

 Layouts, morph and autoPlay timeline (LOOP = 7 s): MorphingPrintLayoutModel.kt. The names slide with the
 page (each centred by its measured width, 20 dp apart), their ink blending from black to grey over one
 page; the caption crossfades over half a page each way, one caption at a time.

 Touch: drag sideways anywhere to scrub the page (170 dp a page, rubber-banded at 35 % past the ends); a
 tick at each format. On release it springs to the nearest format, thrown a quarter of the way by the
 finger's speed (the travel a 0.4 s glide at that speed would add). Tapping a name goes to it. 2 s after
 the last touch the frame sweeps back to Single and the loop starts again.
 Reduced motion (animations off): no loop and no scattering; a drag still scrubs, and release snaps at once.
 */

private const val PAGE_WIDTH = 170.0
private val NAMES = listOf("palm", "shore", "sunrise", "village", "pool", "boardwalk")

private fun nowSeconds() = System.nanoTime() / 1e9

private class MplLayout(val width: Double, val height: Double, val top: Double, bottomInset: Double) {
    val k = min(width, 430.0) / 393
    val stripBottom = top + 112
    val bottomY = height - max(bottomInset, 20.0) - 34
    val centerX = width / 2
    val centerY = (stripBottom + bottomY - 66) / 2
    val captionY = centerY + 186 * k
}

private class MplController(val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    var dragFrom by mutableStateOf<Double?>(null)
    var dragOffset by mutableDoubleStateOf(0.0)
    /** [date, from, to, sweep (1) or spring (0)]. */
    var glide by mutableStateOf<DoubleArray?>(null)
    var resting by mutableDoubleStateOf(0.0)
    var lastTick = 0
    var active by mutableStateOf(false)
    var resume: Job? = null

    val running get() = (autoStart != null && !reduced) || active

    fun page(at: Double): Double {
        val M = MorphingPrintLayoutModel
        val start = autoStart
        if (start != null && at >= start) return M.page((at - start) % M.LOOP)
        val from = dragFrom
        if (from != null) return rubber(from - dragOffset / PAGE_WIDTH)
        val g = glide
        if (g != null) {
            val u = at - g[0]
            val e = if (reduced) 1.0 else if (g[3] > 0) M.critical(u) else M.spring(u)
            return g[1] + (g[2] - g[1]) * e
        }
        return resting
    }

    fun rubber(p: Double): Double {
        val last = (MorphingPrintLayoutModel.formats.size - 1).toDouble()
        if (p < 0) return p * 0.35
        if (p > last) return last + (p - last) * 0.35
        return p
    }
}

private fun centerAt(p: Double, xs: List<Double>): Double {
    val n = xs.size
    val a = min(n - 2, max(0, floor(p).toInt()))
    return xs[a] + (xs[a + 1] - xs[a]) * (p - a)
}

@Composable
fun MorphingPrintLayout(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val photos = remember {
        NAMES.map { n -> context.assets.open("morphingprintlayout/morphingprintlayout_$n.jpg").use { BitmapFactory.decodeStream(it) } }
    }
    val c = remember(reduced) { MplController(reduced) }
    val painter = remember(photos) { MplPainter(photos) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val nameStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium)
    val ink = Color(0xFF121214)
    MplSystemBars()

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.running) { while (c.running) withFrameNanos { c.frame = it / 1e9 } }

    fun scheduleResume() {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2000)
            if (!autoPlay || reduced) {
                c.active = false
                return@launch
            }
            val now = nowSeconds()
            val p = c.page(now)
            if (p > 0.01) {
                c.glide = doubleArrayOf(now, p, 0.0, 1.0)
                c.resting = 0.0
                delay(1000)
            }
            c.glide = null
            // Join the loop in its final rest.
            c.autoStart = nowSeconds() - 6.9
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val l = remember(maxWidth, maxHeight, top, bottomInset) { MplLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottomInset) }
        val names = remember(measurer) { MorphingPrintLayoutModel.formats.map { measurer.measure(it.name, nameStyle) } }
        val centers = remember(names, density) {
            var x = 0.0
            names.map { r ->
                val w = r.size.width / density.toDouble()
                val cx = x + w / 2
                x += w + 20
                cx
            }
        }

        // Stage, frame and touch.
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Print layout preview" }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        c.resume?.cancel()
                        c.active = true
                        val p0 = c.page(nowSeconds())
                        c.autoStart = null
                        c.glide = null
                        c.dragFrom = p0
                        c.dragOffset = 0.0
                        c.lastTick = p0.roundToInt()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            last = change.position
                            c.dragOffset = ((change.position.x - down.position.x) / density).toDouble()
                            val p = c.page(nowSeconds())
                            val k = p.roundToInt()
                            if (k != c.lastTick && p >= -0.2 && p <= MorphingPrintLayoutModel.formats.size - 1 + 0.2) {
                                c.lastTick = k
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        val now = nowSeconds()
                        val dx = (last.x - down.position.x) / density
                        val dy = (last.y - down.position.y) / density
                        val p = c.rubber(p0 - dx / PAGE_WIDTH)
                        c.dragFrom = null
                        var target: Double
                        if (abs(dx) < 6 && abs(dy) < 6) {
                            // A tap: on a name, go to it.
                            target = Math.round(p).toDouble()
                            val y = last.y / density
                            if (y < l.stripBottom && y > l.top + 44) {
                                val x = last.x / density
                                val cp = centerAt(p, centers)
                                for (i in centers.indices) {
                                    val cx = l.width / 2 + centers[i] - cp
                                    if (abs(x - cx) < names[i].size.width / density / 2 + 10) target = i.toDouble()
                                }
                            }
                        } else {
                            val vx = tracker.calculateVelocity().x / density
                            target = Math.round(p - vx * 0.4 / PAGE_WIDTH * 0.25).toDouble()
                        }
                        target = min((MorphingPrintLayoutModel.formats.size - 1).toDouble(), max(0.0, target))
                        if (target != Math.round(p).toDouble() || (abs(dx) < 6 && abs(dy) < 6)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        c.glide = doubleArrayOf(now, p, target, 0.0)
                        c.resting = target
                        scheduleResume()
                    }
                },
        ) {
            val sb = (l.stripBottom * density).toFloat()
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFF4B043), Color(0xFFEE4D0F)), startY = sb, endY = size.height),
                topLeft = Offset(0f, sb),
            )
            val p = c.page(c.frame)
            painter.draw(drawContext.canvas.nativeCanvas, p, l, density, reduced)
        }

        // Caption, one at a time.
        MorphingPrintLayoutModel.formats.forEachIndexed { i, f ->
            BasicText(
                f.caption,
                Modifier
                    .fillMaxWidth()
                    .offset(y = (l.captionY - 11).dp)
                    .graphicsLayer { alpha = max(0.0, 1 - 2 * abs(i - c.page(c.frame))).toFloat() },
                style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }

        // Bottom bar.
        Row(
            Modifier.fillMaxWidth().height(48.dp).offset(y = (l.bottomY - 24).dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                MplGlyph(MplGlyphKind.EXPORT, Color.White, 1.8f, Modifier.size(22.dp))
            }
            Box(Modifier.weight(1f).height(48.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("Choose photos", style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        // The white strip: nav bar, names, dots.
        Box(Modifier.fillMaxWidth().height(l.stripBottom.dp).background(Color.White)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).offset(y = l.top.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                MplGlyph(MplGlyphKind.BACK, ink, 1.8f, Modifier.size(22.dp))
                Box(Modifier.weight(1f))
                MplGlyph(MplGlyphKind.SLIDERS, ink, 1.6f, Modifier.size(22.dp))
                MplGlyph(MplGlyphKind.SHARE, ink, 1.6f, Modifier.size(22.dp))
            }
            Row(
                Modifier.fillMaxWidth().height(44.dp).offset(y = l.top.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                BasicText("Select Film", style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Box(Modifier.padding(start = 6.dp).size(15.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) {
                    BasicText("?", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val p = c.page(c.frame)
                val cp = centerAt(p, centers)
                val y = ((l.top + 44 + 26) * density).toFloat()
                for (i in names.indices) {
                    val d = min(1.0, abs(i - p))
                    val g = (0.07 + (0.77 - 0.07) * d).toFloat()
                    val r = names[i]
                    val cx = ((l.width / 2 + centers[i] - cp) * density).toFloat()
                    drawText(r, color = Color(g, g, g), topLeft = Offset(cx - r.size.width / 2f, y - r.size.height / 2f))
                }
                // Dots.
                val n = names.size
                val dotY = ((l.top + 44 + 53) * density).toFloat()
                val step = 14 * density
                val x0 = size.width / 2 - step * (n - 1) / 2
                for (i in 0 until n) {
                    val a = max(0.0, 1 - abs(i - p))
                    val g = (0.84 - 0.77 * a).toFloat()
                    drawCircle(Color(g, g, g), 3 * density, Offset(x0 + step * i, dotY))
                }
            }
        }
    }
}

/** The print frame and its wells for a fractional page, through android.graphics. */
private class MplPainter(val photos: List<Bitmap>) {
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: android.graphics.Canvas, p: Double, l: MplLayout, density: Float, reduced: Boolean) {
        val s = MorphingPrintLayoutModel.shape(p, reduced)
        val k = l.k
        val w = s.w * k
        val h = s.h * k
        val ox = l.centerX - w / 2
        val oy = l.centerY - h / 2
        canvas.save()
        canvas.scale(density, density)
        // The big frame, fading while the prints are loose.
        val frameAlpha = 1 - min(1.0, s.loose * 1.6)
        if (frameAlpha > 0.001) {
            frame.alpha = (frameAlpha * 255).toInt()
            frame.setShadowLayer(22f, 0f, 12f, android.graphics.Color.argb((0.28 * frameAlpha * 255).toInt(), 115, 31, 0))
            canvas.drawRoundRect(RectF(ox.toFloat(), oy.toFloat(), (ox + w).toFloat(), (oy + h).toFloat()), 3f, 3f, frame)
        }
        // Wells, from the last to the first, so a well that splits off comes out from under its neighbour.
        for (i in s.shown - 1 downTo 0) {
            val wl = s.wells[i]
            val rect = RectF((ox + wl.x * k).toFloat(), (oy + wl.y * k).toFloat(), (ox + (wl.x + wl.s) * k).toFloat(), (oy + (wl.y + wl.s) * k).toFloat())
            canvas.save()
            if (s.loose > 0 && i < 4) {
                val sc = MorphingPrintLayoutModel.scatter[i]
                canvas.translate((sc[0] * k * s.loose).toFloat(), (sc[1] * k * s.loose).toFloat())
                canvas.rotate((sc[2] * s.loose).toFloat(), rect.centerX(), rect.centerY())
                // Its own little print border.
                val b = (8 * k * s.loose).toFloat()
                border.setShadowLayer(14f, 0f, 8f, android.graphics.Color.argb((0.3 * s.loose * 255).toInt(), 115, 31, 0))
                canvas.drawRoundRect(RectF(rect.left - b, rect.top - b, rect.right + b, rect.bottom + (22 * k * s.loose).toFloat()), 2f, 2f, border)
            }
            val clip = Path().apply { addRoundRect(rect, 2f, 2f, Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(photos[i], null, rect, image)
            canvas.restore()
        }
        canvas.restore()
    }
}

private enum class MplGlyphKind { BACK, SLIDERS, SHARE, EXPORT }

/** Line glyphs on a 24 × 24 grid, scaled to the size. */
@Composable
private fun MplGlyph(kind: MplGlyphKind, color: Color, width: Float, modifier: Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24
        val path = androidx.compose.ui.graphics.Path()
        fun m(x: Float, y: Float) = path.moveTo(x * k, y * k)
        fun ln(x: Float, y: Float) = path.lineTo(x * k, y * k)
        when (kind) {
            MplGlyphKind.BACK -> { m(20f, 12f); ln(4f, 12f); m(10f, 6f); ln(4f, 12f); ln(10f, 18f) }
            MplGlyphKind.SLIDERS -> for ((y, x) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
                m(3f, y); ln(21f, y)
                path.addOval(androidx.compose.ui.geometry.Rect((x - 2) * k, (y - 2) * k, (x + 2) * k, (y + 2) * k))
            }
            MplGlyphKind.SHARE -> {
                m(12f, 3.5f); ln(12f, 15f)
                m(7.5f, 8f); ln(12f, 3.5f); ln(16.5f, 8f)
                m(8f, 11f); ln(5f, 11f); ln(5f, 21f); ln(19f, 21f); ln(19f, 11f); ln(16f, 11f)
            }
            MplGlyphKind.EXPORT -> {
                m(12f, 4f); ln(12f, 15f)
                m(7.5f, 10.5f); ln(12f, 15f); ln(16.5f, 10.5f)
                m(4f, 15f); ln(4f, 20f); ln(20f, 20f); ln(20f, 15f)
            }
        }
        drawPath(path, color, style = Stroke(width * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The top strip is white, so the status bar gets dark icons while the block is open. */
@Composable
private fun MplSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = true
        onDispose { controller.isAppearanceLightStatusBars = statusBars }
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
