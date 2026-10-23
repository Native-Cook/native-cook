package com.ngocdevv.nativecook.blocks.media.continuouszoomgrid

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 Continuous Zoom Grid — a photo library that zooms continuously from 3 large photos per row to a
 contact sheet of 20 per row and back, the way a pinch does it. A port of the SwiftUI block
 (ContinuousZoomGrid.swift); the numbers are the same.

 The zoom z (photos per row) is continuous; the grid is drawn from the two layouts either side of it
 in [3, 5, 9, 14, 20]. Each layout keeps its photos square (the screen's width / n) and is scaled by
 n / z about the anchor photo (the one under the fingers), whose centre stays at the same height;
 the closer layout fades in over the middle of each step (smoothstep 0.2 – 0.8 of the step in log
 space) while the other fades out. Small photos are their sky over their ground; from 22 dp their
 picture fades in (fully drawn at 44 dp), so the zoom reads like mipmaps cross-fading. Month chips
 sit on each month's first photo below 64 dp (gone by 64, full at 34); video lengths show on photos
 above 60 dp.

 The 3,000 photos are drawn in code from their index (ContinuousZoomGridModel.kt): one of eight
 palettes, one of eight kinds of picture (sun over the sea, hills in haze, city lights out of focus,
 dunes, pines in mist, petals close up, a building in low sun, a beach) and a few shape parameters,
 all from an integer hash; every picture gets a soft vignette. Photos come in runs that share a kind
 and a palette, and months are 30 to 109 photos long, newest first.

 Chrome: a white screen; a header (round back button, "Library" over the month in the middle of the
 screen, a Select pill) over a white fade; a floating glass control at the bottom (Years · Months ·
 All Photos) whose highlight follows the zoom.

 autoPlay timeline (LOOP = 8 s):
   0.0 – 1.0   rest: 3 per row, photo 901 centred at half height
   1.0 – 2.8   zoom out to 20 per row about it (z = 3 · (20/3)^e, e easeInOut)
   3.4 – 4.4   the grid is dragged down, until photo 906 sits where it rests at 3 per row
   4.6 – 6.4   zoom back in to 3 per row about photo 906 (cubic in, out with a 5 % overshoot)
   6.4 – 8.0   rest (the same frame as 0)

 Touch: pinch to zoom about the photo between the fingers (rubber band past 3 and 20 per row; on
 release it settles on the nearest layout in 0.35 s, ease out, with a light haptic); drag to scroll
 (thrown on release, 0.5 s ease out). 2 s after the last touch the grid zooms back to 3 per row
 (0.6 s), then scrolls back to its resting place (0.6 s) and the loop starts over.
 Reduced motion: no loop; a pinch still zooms but lands on the nearest layout at once, and drags
 scroll without the throw.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private val PAPER = Color(0.98f, 0.98f, 0.98f)
private val INK = Color(0.07f, 0.07f, 0.08f)

/** A tween that eases out (cubic). */
private class CzgTween(val from: Double, val to: Double, val at: Double, val dur: Double) {
    constructor(v: Double) : this(v, v, 0.0, 1.0)
    fun value(now: Double): Double {
        val u = CzgModel.clamp01((now - at) / dur)
        return from + (to - from) * (1 - (1 - u).pow(3))
    }
}

private class CzgController(val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    /** The ease back: [when, z, anchor, fy], or null. */
    var returning by mutableStateOf<DoubleArray?>(null)
    /** The manual zoom, kept as log z so a settle is even across layouts. */
    var zoom by mutableStateOf(CzgTween(ln(3.0)))
    var anchor by mutableIntStateOf(CzgModel.REST_ANCHOR)
    var fy by mutableStateOf(CzgTween(0.0))
    var resume: Job? = null

    fun now() = if (reduced) nowSeconds() else frame

    fun view(now: Double, w: Double, h: Double): CzgView {
        val start = autoStart
        if (start != null && now >= start && !reduced) return CzgModel.view(now - start, w, h)
        val r = returning
        if (r != null) {
            val t = now - r[0]
            val anchorR = r[2].toInt()
            if (t < 0.6) return CzgView(r[1] * (3 / r[1]).pow(CzgModel.easeInOut(t / 0.6)), anchorR, r[3])
            // At 3 per row, switch to the resting photo (seamless) and scroll it home.
            val from = r[3] + (CzgModel.row(CzgModel.REST_ANCHOR, 3) - CzgModel.row(anchorR, 3)) * w / 3
            val u = CzgModel.easeInOut((t - 0.6) / 0.6)
            return CzgView(3.0, CzgModel.REST_ANCHOR, from + (h * 0.5 - from) * u)
        }
        return CzgView(exp(zoom.value(now)), anchor, fy.value(now))
    }

    fun takeOver(now: Double, w: Double, h: Double) {
        resume?.cancel()
        val v = view(now, w, h)
        zoom = CzgTween(ln(v.z))
        anchor = v.anchor
        fy = CzgTween(v.fy)
        autoStart = null
        returning = null
    }
}

/** The nearest layout to z, in log space. */
private fun nearestLevel(z: Double): Int {
    var best = 0
    for (i in CzgModel.levels.indices) if (abs(ln(CzgModel.levels[i] / z)) < abs(ln(CzgModel.levels[best] / z))) best = i
    return best
}

/** The two layouts either side of z and their opacities. */
private fun layers(z: Double): List<Pair<Double, Double>> {
    val l = CzgModel.levels
    if (z <= l[0]) return listOf(l[0] to 1.0)
    if (z >= l[l.size - 1]) return listOf(l[l.size - 1] to 1.0)
    var k = 0
    while (!(z >= l[k] && z < l[k + 1])) k += 1
    val f = ln(z / l[k]) / ln(l[k + 1] / l[k])
    val b = CzgModel.smoothstep(0.2, 0.8, f)
    return listOf(l[k] to min(1.0, 2 * (1 - b)), l[k + 1] to b)
}

/** The month of the photo in the middle of the screen. */
private fun centreMonth(w: Double, h: Double, v: CzgView): String {
    val ls = layers(v.z)
    val n = if (ls.size > 1 && ls[1].second >= 0.5) ls[1].first else ls[0].first
    val cell = w / n * (n / v.z)
    val ni = n.toInt()
    val ra = CzgModel.row(v.anchor, ni)
    val row = ra + floor((h / 2 - v.fy) / cell + 0.5).toInt()
    return CzgModel.month((row * ni + ni / 2).coerceIn(0, CzgModel.COUNT - 1))
}

@Composable
fun ContinuousZoomGrid(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { CzgController(reduced) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    CzgSystemBars()

    BoxWithConstraints(modifier.fillMaxSize().background(PAPER)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val density = LocalDensity.current.density

        LaunchedEffect(c) {
            c.fy = CzgTween(height * 0.5)
            if (autoPlay && !reduced) c.autoStart = nowSeconds()
            if (!reduced) while (true) withFrameNanos { c.frame = it / 1e9 }
        }

        fun current() = c.view(if (reduced) nowSeconds() else c.frame, width, height)

        fun scheduleResume() {
            c.resume?.cancel()
            if (!autoPlay || reduced) return
            c.resume = scope.launch {
                delay(2000)
                val now = c.now()
                val v = c.view(now, width, height)
                c.returning = doubleArrayOf(now, v.z, v.anchor.toDouble(), v.fy)
                c.autoStart = now + 1.2
            }
        }

        /** Re-anchors on the photo under a point (only ever at rest on a layout, so nothing moves). */
        fun anchorAt(p: Offset, now: Double) {
            val v = c.view(now, width, height)
            val n = CzgModel.levels[nearestLevel(v.z)]
            if (abs(v.z - n) > 0.01) return
            val ni = n.toInt()
            val cell = width / n
            val ra = CzgModel.row(v.anchor, ni)
            val row = ra + floor((p.y - v.fy) / cell + 0.5).toInt()
            val col = (p.x / cell).toInt().coerceIn(0, ni - 1)
            c.anchor = (row * ni + col).coerceIn(0, CzgModel.COUNT - 1)
            c.fy = CzgTween(v.fy + (row - ra) * cell)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    val v = c.view(nowSeconds(), width, height)
                    contentDescription = "Photo library, ${v.z.toInt()} photos per row, ${centreMonth(width, height, v)}"
                }
                .pointerInput(c, width, height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pinchStart: Double? = null
                        var spanStart = 0f
                        var dragStart: Double? = null
                        var lastLevel = 0
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val a = pressed[0].position / density
                                val b = pressed[1].position / density
                                val span = (a - b).getDistance()
                                if (pinchStart == null) {
                                    val now = c.now()
                                    c.takeOver(now, width, height)
                                    anchorAt((a + b) / 2f, now)
                                    pinchStart = c.zoom.to
                                    spanStart = span
                                    lastLevel = nearestLevel(exp(c.zoom.to))
                                    dragStart = null
                                }
                                var lz = pinchStart - ln((span / spanStart).toDouble())
                                // Rubber band past the first and last layout.
                                val lo = ln(CzgModel.MIN_Z)
                                val hi = ln(CzgModel.MAX_Z)
                                if (lz < lo) lz = lo - 0.25 * (1 - exp((lz - lo) / 0.25))
                                if (lz > hi) lz = hi + 0.25 * (1 - exp(-(lz - hi) / 0.25))
                                c.zoom = CzgTween(lz)
                                val level = nearestLevel(exp(lz))
                                if (level != lastLevel) {
                                    lastLevel = level
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pinchStart == null) {
                                val change = pressed[0]
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val dy = (change.position.y - down.position.y) / density
                                if (dragStart == null && abs(dy) > 8f) {
                                    c.takeOver(c.now(), width, height)
                                    dragStart = c.fy.to - dy
                                }
                                val ds = dragStart
                                if (ds != null) {
                                    c.fy = CzgTween(ds + dy)
                                    change.consume()
                                }
                            }
                        }
                        val now = c.now()
                        val ps = pinchStart
                        val ds = dragStart
                        if (ps != null) {
                            val target = ln(CzgModel.levels[nearestLevel(exp(c.zoom.to))])
                            c.zoom = CzgTween(c.zoom.to, target, now, if (reduced) 0.001 else 0.35)
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            scheduleResume()
                        } else if (ds != null) {
                            val end = c.fy.to
                            val v = tracker.calculateVelocity().y / density
                            c.fy = if (reduced) CzgTween(end) else CzgTween(end, end + v * 0.25, now, 0.5)
                            scheduleResume()
                        }
                    }
                },
        ) {
            val v = current()
            withTransform({ scale(density, density, Offset.Zero) }) {
                czgGrid(this, width, height, top, v, measurer)
            }
        }

        // Header.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 4).dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
                    .background(Color.White, CircleShape)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(9.dp, 16.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.9f, size.height * 0.06f); lineTo(size.width * 0.1f, size.height * 0.5f); lineTo(size.width * 0.9f, size.height * 0.94f)
                    }
                    drawPath(p, INK, style = Stroke(2.2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Box(Modifier.weight(1f))
            Box(
                Modifier
                    .height(36.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Select", style = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        val month by remember(c) { derivedStateOf { centreMonth(width, height, current()) } }
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 6).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("Library", style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText(month, style = TextStyle(color = INK.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }

        // The control: its highlight follows the zoom (All Photos at 3 per row, Years at 20, log scale).
        val labels = listOf("Years", "Months", "All Photos")
        fun seg(): Double = 2 - 2 * CzgModel.clamp01(ln(current().z / CzgModel.MIN_Z) / ln(CzgModel.MAX_Z / CzgModel.MIN_Z))
        Box(
            Modifier
                .offset(x = (width / 2 - 135).dp, y = (height - max(bottomInset, 16.0) - 10 - 46).dp)
                .size(270.dp, 46.dp)
                .shadow(14.dp, RoundedCornerShape(23.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(23.dp))
                .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(23.dp)),
        ) {
            Box(
                Modifier
                    .offset(y = 5.dp)
                    .graphicsLayer { translationX = ((5 + seg() * 86.7) * density).toFloat() }
                    .size(88.dp, 36.dp)
                    .background(INK.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
            )
            Row(Modifier.padding(horizontal = 5.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 3) {
                    BasicText(
                        labels[i],
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        modifier = Modifier
                            .width(86.7.dp)
                            .graphicsLayer { alpha = (0.42 + 0.58 * max(0.0, 1 - abs(i - seg()))).toFloat() },
                    )
                }
            }
        }
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the block
 * is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun CzgSystemBars() {
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

// MARK: Drawing (in dp)

private fun color(c: CzgRGB, a: Double = 1.0) =
    Color(c.r.toFloat().coerceIn(0f, 1f), c.g.toFloat().coerceIn(0f, 1f), c.b.toFloat().coerceIn(0f, 1f), a.toFloat().coerceIn(0f, 1f))

private fun czgGrid(d: DrawScope, w: Double, h: Double, top: Double, v: CzgView, measurer: TextMeasurer) {
    d.drawRect(PAPER, size = Size(w.toFloat(), h.toFloat()))
    for ((n, alpha) in layers(v.z)) if (alpha > 0.001) czgLayer(d, w, h, n, v, alpha, measurer)
    // White fades under the header and the control.
    val fadeTop = (top + 84).toFloat()
    d.drawRect(
        Brush.verticalGradient(0f to PAPER.copy(alpha = 0.96f), 0.55f to PAPER.copy(alpha = 0.85f), 1f to PAPER.copy(alpha = 0f), startY = 0f, endY = fadeTop),
        size = Size(w.toFloat(), fadeTop),
    )
    d.drawRect(
        Brush.verticalGradient(listOf(PAPER.copy(alpha = 0f), PAPER.copy(alpha = 0.92f)), startY = (h - 140).toFloat(), endY = h.toFloat()),
        Offset(0f, (h - 140).toFloat()), Size(w.toFloat(), 140f),
    )
}

/** One layout of n photos per row, scaled by n / z about its anchor photo, at an opacity. */
private fun czgLayer(d: DrawScope, w: Double, h: Double, n: Double, v: CzgView, opacity: Double, measurer: TextMeasurer) {
    val ni = n.toInt()
    val cell = w / n
    val k = n / v.z
    val s = cell * k
    val ra = CzgModel.row(v.anchor, ni)
    val ca = CzgModel.col(v.anchor, ni)
    val ax = (ca + 0.5) * cell
    val gap = min(3.0, max(0.8, s * 0.022))
    val first = max(0, ra + floor((0 - v.fy) / s).toInt() - 1)
    val last = min((CzgModel.COUNT - 1) / ni, ra + ceil((h - v.fy) / s).toInt() + 1)
    if (first > last) return
    val detail = CzgModel.smoothstep(22.0, 44.0, s)
    val chipA = 1 - CzgModel.smoothstep(34.0, 64.0, s)
    val lengthA = CzgModel.smoothstep(60.0, 90.0, s)
    val chips = ArrayList<Pair<Offset, String>>()
    for (r in first..last) {
        val y = v.fy + (r - ra) * s
        for (col in 0 until ni) {
            val i = r * ni + col
            if (i >= CzgModel.COUNT) break
            val x = ax + ((col + 0.5) * cell - ax) * k
            if (x + s / 2 < 0 || x - s / 2 > w) continue
            val rect = Rect((x - s / 2 + gap / 2).toFloat(), (y - s / 2 + gap / 2).toFloat(), (x + s / 2 - gap / 2).toFloat(), (y + s / 2 - gap / 2).toFloat())
            if (detail < 0.999) {
                val (sky, ground, split) = CzgModel.tiny(i)
                d.drawRect(color(sky, opacity), rect.topLeft, rect.size)
                if (split < 1) {
                    val top = rect.top + rect.height * split.toFloat()
                    d.drawRect(color(ground, opacity), Offset(rect.left, top), Size(rect.width, rect.bottom - top))
                }
            }
            if (detail > 0.001) {
                val photo = CzgModel.photo(i)
                d.clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                    czgPicture(this, photo, rect, opacity * detail)
                    if (photo.duration > 0 && lengthA > 0.01) {
                        val layout = measurer.measure(
                            "0:%02d".format(photo.duration),
                            TextStyle(color = Color.White.copy(alpha = (opacity * detail * lengthA).toFloat()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            density = Density(1f),
                        )
                        drawText(layout, topLeft = Offset(rect.right - 7 - layout.size.width, rect.bottom - 6 - layout.size.height))
                    }
                }
            }
            if (chipA > 0.01 && CzgModel.monthStartSet.contains(i)) chips.add(Offset(rect.left + 4, rect.top + 4) to CzgModel.month(i))
        }
    }
    // Month chips over the first photo of each month.
    val a = (opacity * chipA).toFloat()
    for ((p, label) in chips) {
        val layout = measurer.measure(label, TextStyle(color = INK.copy(alpha = a), fontSize = 11.sp, fontWeight = FontWeight.SemiBold), density = Density(1f))
        val bw = layout.size.width + 14f
        d.drawRoundRect(Color.White.copy(alpha = 0.94f * a), p, Size(bw, 20f), CornerRadius(10f, 10f))
        d.drawText(layout, topLeft = Offset(p.x + (bw - layout.size.width) / 2, p.y + (20 - layout.size.height) / 2f))
    }
}

private fun czgPicture(d: DrawScope, photo: CzgPhoto, r: Rect, opacity: Double) {
    fun px(x: Double) = (r.left + x * r.width).toFloat()
    fun py(y: Double) = (r.top + y * r.height).toFloat()
    for (op in photo.ops) {
        when (op) {
            is CzgOp.Rect -> d.drawRect(color(op.c, op.a * opacity), Offset(px(op.x), py(op.y)), Size((op.w * r.width).toFloat(), (op.h * r.height).toFloat()))
            is CzgOp.Grad -> d.drawRect(
                Brush.verticalGradient(listOf(color(op.top, opacity), color(op.bottom, opacity)), startY = py(op.y), endY = py(op.y + op.h)),
                Offset(px(op.x), py(op.y)), Size((op.w * r.width).toFloat(), (op.h * r.height).toFloat()),
            )
            is CzgOp.Circle -> d.drawCircle(color(op.c, op.a * opacity), (op.r * r.width).toFloat(), Offset(px(op.x), py(op.y)))
            is CzgOp.Glow -> {
                val rr = (op.r * r.width).toFloat()
                val center = Offset(px(op.x), py(op.y))
                d.drawCircle(
                    Brush.radialGradient(listOf(color(op.c, op.a * opacity), color(op.c, op.a * 0.45 * opacity), color(op.c, 0.0)), center = center, radius = rr),
                    rr, center,
                )
            }
            is CzgOp.Poly -> {
                val path = Path()
                var j = 0
                while (j < op.pts.size) {
                    if (j == 0) path.moveTo(px(op.pts[j]), py(op.pts[j + 1])) else path.lineTo(px(op.pts[j]), py(op.pts[j + 1]))
                    j += 2
                }
                path.close()
                d.drawPath(path, color(op.c, op.a * opacity))
            }
        }
    }
    // A lens's falloff towards the corners.
    d.drawRect(
        Brush.radialGradient(
            (0.35f / 0.75f) to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = (0.22 * opacity).toFloat()),
            center = r.center, radius = r.width * 0.75f,
        ),
        r.topLeft, r.size,
    )
    if (photo.duration > 0) {
        d.drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = (0.35 * opacity).toFloat())), startY = py(0.7), endY = py(1.0)),
            Offset(r.left, py(0.7)), Size(r.width, r.height * 0.3f),
        )
    }
}
