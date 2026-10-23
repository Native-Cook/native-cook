package com.ngocdevv.nativecook.blocks.media.cameracarouselfan

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Camera Carousel Fan — a camera shelf: a glossy 3D camera floats over black with eight instant
 photos orbiting it on a tilted ring; picking another camera tumbles the camera end over end and
 the photos flip over to that camera's film look. A port of the SwiftUI block
 (CameraCarouselFan.swift); the numbers are the same.

 The camera is drawn, not loaded: each model is a handful of extruded rounded rectangles and
 circles (CameraCarouselFanModel.kt), rotated and projected in perspective (focal 760 dp) and
 painted back to front with smooth Lambert + Blinn shading; lenses get a glass gradient.

 The selection is one number p (0 … 4, the camera index). Everything follows from it:
   tumble    φ = (p − round p) · 180°, about the x axis, with 0.35 φ about y; the model swaps at
             the edge-on moment (|p − round p| = ½). Seen from 12° above and 10° to the left, idle
             yaw ±7° (period 4 s) and a 4 dp bob (period 4 s).
   ring      radius 172, tilted 15°, turns by −135° per camera (+ ±6° idle sway, period 8 s); each
             photo faces the viewer with a yaw of 0.55·sin(angle) and its own tilt.
   photos    each photo flips about its own vertical axis by (p − round p) · 180°, 0.06 s after the
             one before it, and shows the round(p) camera's film look.
   labels    name and spec fade and rise 6 dp between cameras; the thumbnail strip slides 72 dp per
             camera under a fixed outline, the nearest thumbnail scaled to 1.15.

 autoPlay timeline (LOOP = 8 s), every move easeInOutBack (≈4 % wind-up and overshoot):
   0.0 – 1.2 rest on OKA 35 · 1.2 – 2.4 flip to RF-2 · 2.4 – 3.6 rest · 3.6 – 4.8 flip to PIP ·
   4.8 – 5.9 rest · 5.9 – 7.4 spin back through RF-2 to OKA 35 · 7.4 – 8.0 rest (= 0.0).

 Touch: dragging sideways turns the camera with the finger (a screen's 80 % per camera, rubber band
 past the ends); release springs (period 0.55 s, damping 0.72) to the nearest camera, thrown 0.18 s
 ahead. Tapping the camera goes to the next one; tapping a thumbnail goes to it. A light haptic on
 each camera passed and on landing. 2 s after the last touch the shelf eases back to OKA 35 and the
 loop starts over.
 Reduced motion: no loop, no tumble, no orbit and no flips; changing camera cross-fades.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun indexOf(p: Double) = p.roundToInt().coerceIn(0, 4)

/** What the screen shows at a moment. */
internal class CcfShown(val p: Double, val clock: Double, val idle: Double, val photoP: DoubleArray)

private class CcfController(val reduced: Boolean, val autoPlay: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    var idleOrigin by mutableDoubleStateOf(nowSeconds())
    var takeoverAt by mutableStateOf<Double?>(null)
    var manualP by mutableDoubleStateOf(0.0)
    /** [when, from, velocity, to]. */
    var spring by mutableStateOf<DoubleArray?>(null)
    /** [when, from, duration]. */
    var returning by mutableStateOf<DoubleArray?>(null)
    var dragging by mutableStateOf(false)
    var resume: Job? = null

    fun shown(at: Double): CcfShown {
        val start = autoStart
        if (start != null && at >= start && !reduced) {
            val t = at - start
            return CcfShown(CcfTimeline.p(t), t, 1.0, DoubleArray(8) { CcfTimeline.p(t - it * CcfTimeline.STAGGER) })
        }
        val r = returning
        if (r != null && start != null) {
            val u = (at - r[0]) / r[2]
            val p = r[1] + (0 - r[1]) * CcfTimeline.easeInOutBack(u)
            return CcfShown(p, at - start, CcfTimeline.smoothstep(0.0, 1.0, u), DoubleArray(8) { p })
        }
        var p = manualP
        val sp = spring
        if (sp != null && !dragging) p = CcfTimeline.spring(at - sp[0], sp[1], sp[2], sp[3])
        val take = takeoverAt
        val idle = if (take != null) 1 - CcfTimeline.smoothstep(0.0, 0.4, at - take) else if (autoPlay) 0.0 else 1.0
        return CcfShown(p, at - idleOrigin, if (reduced) 0.0 else idle, DoubleArray(8) { p })
    }

    fun takeOver(now: Double) {
        resume?.cancel()
        val s = shown(now)
        manualP = s.p
        autoStart?.let { idleOrigin = it }
        if (autoStart != null || returning != null || takeoverAt == null) takeoverAt = now
        autoStart = null
        returning = null
        spring = null
    }

    fun settle(now: Double, target: Double, velocity: Double) {
        if (reduced) {
            manualP = target
            spring = null
        } else {
            spring = doubleArrayOf(now, manualP, velocity, target)
        }
    }
}

@Composable
fun CameraCarouselFan(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { CcfController(reduced, autoPlay) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        c.idleOrigin = nowSeconds()
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        if (!reduced) while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val now = nowSeconds()
            val from = c.shown(now).p
            val dur = 0.5 + 0.3 * abs(from)
            c.manualP = from
            c.spring = null
            c.returning = doubleArrayOf(now, from, dur)
            c.autoStart = now + dur
            c.takeoverAt = null
        }
    }

    fun go(k: Int) {
        val now = nowSeconds()
        c.takeOver(now)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        c.settle(now, k.toDouble(), 0.0)
        scheduleResume()
    }

    // The value every piece of the screen follows; under reduced motion it changes only with state.
    fun p(): Double = c.shown(if (reduced) nowSeconds() else c.frame).p
    val index by remember(c) { derivedStateOf { indexOf(c.shown(if (reduced) 0.0 else c.frame).p) } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF080809))) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density

        // The camera and its photos.
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Camera ${CcfCameras.all[index].name}"
                    role = Role.Button
                }
                .pointerInput(c) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        c.takeOver(nowSeconds())
                        c.dragging = true
                        val start = c.manualP
                        var last = indexOf(start)
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = (change.position.x - down.position.x) / density
                            if (!moved && abs(dx) > 6f) moved = true
                            if (moved) {
                                change.consume()
                                var p = start - dx / (width * 0.8)
                                // Rubber band past the first and last camera.
                                if (p < 0) p = -0.35 * (1 - exp(p / 0.35))
                                if (p > 4) p = 4 + 0.35 * (1 - exp(-(p - 4) / 0.35))
                                c.manualP = p
                                val i = indexOf(p)
                                if (i != last) {
                                    last = i
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        val now = nowSeconds()
                        c.dragging = false
                        val current = c.manualP
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        if (moved) {
                            val v = -tracker.calculateVelocity().x / density / (width * 0.8)
                            val near = Math.round(current).toDouble()
                            val thrown = Math.round(current + v * 0.18).toDouble()
                            val target = min(near + 1, max(near - 1, thrown)).coerceIn(0.0, 4.0)
                            c.settle(now, target, v)
                        } else {
                            c.settle(now, ((indexOf(current) + 1) % 5).toDouble(), 0.0)
                        }
                        scheduleResume()
                    }
                },
        ) {
            val s = c.shown(if (reduced) nowSeconds() else c.frame)
            withTransform({ scale(density, density, Offset.Zero) }) {
                ccfScene(this, width, height, s, reduced)
            }
        }

        // Header.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 14).dp)
                .padding(horizontal = 24.dp),
        ) {
            val style = TextStyle(
                color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            BasicText("CAMERA SHELF", style = style.copy(letterSpacing = 2.sp))
            Spacer(Modifier.weight(1f))
            BasicText("%02d / 05".format(index + 1), style = style)
        }

        // Name and spec.
        val cam = CcfCameras.all[index]
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (height * 0.63).dp)
                .graphicsLayer {
                    val pv = p()
                    val a = 1 - CcfTimeline.smoothstep(0.08, 0.42, abs(pv - Math.round(pv)))
                    alpha = a.toFloat()
                    translationY = (6 * (1 - a) * density).toFloat()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                cam.name, maxLines = 1,
                style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                cam.spec, maxLines = 1,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp,
                ),
            )
        }

        // The thumbnail strip: thumbnails slide 72 dp per camera under a fixed outline.
        val stripY = height * 0.755
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithFade(),
        ) {
            for (k in 0 until 5) {
                Column(
                    Modifier
                        .offset(x = (width / 2 - 32).dp, y = (stripY - 22).dp)
                        .width(64.dp)
                        .graphicsLayer {
                            val near = max(0.0, 1 - abs(k - p()))
                            translationX = ((k - p()) * 72 * density).toFloat()
                            alpha = (0.4 + 0.6 * near).toFloat()
                        }
                        .semantics { contentDescription = CcfCameras.all[k].name; role = Role.Button }
                        .pointerInput(k) { detectTapGestures { go(k) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        Modifier
                            .size(56.dp, 44.dp)
                            .graphicsLayer {
                                val near = max(0.0, 1 - abs(k - p()))
                                scaleX = (1 + 0.15 * near).toFloat()
                                scaleY = scaleX
                            },
                    ) {
                        withTransform({ scale(density, density, Offset.Zero) }) {
                            val m = CcfM3.y(-0.45) * CcfM3.x(0.22)
                            val center = Offset(28f, 24f)
                            ccfFaces(CcfCameras.all[k], m, 0.26, 0.0, center, 1.0).sortedBy { it.z }.forEach { ccfDraw(this, it) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.graphicsLayer {
                        val near = max(0.0, 1 - abs(k - p()))
                        alpha = (0.35 + 0.65 * near).toFloat()
                    }) {
                        BasicText(
                            CcfCameras.all[k].name, maxLines = 1, softWrap = false,
                            style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .offset(x = (width / 2 - 35).dp, y = (stripY - 29).dp)
                    .size(70.dp, 58.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp)),
            )
        }

        // Footer.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (height - max(bottomInset.toDouble(), 16.0) - 12 - 44).dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Swipe to change camera",
                style = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .semantics { contentDescription = "Close"; role = Role.Button }
                    .pointerInput(Unit) { detectTapGestures { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(14.dp, 8.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.12f)
                        lineTo(size.width * 0.5f, size.height * 0.88f)
                        lineTo(size.width * 0.92f, size.height * 0.12f)
                    }
                    drawPath(path, Color.Black, style = Stroke(width = 2.4f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
    }
}

/** The strip fades out towards both sides of the screen (2 % – 22 % and 78 % – 98 %). */
private fun Modifier.drawWithFade(): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Transparent, 0.02f to Color.Transparent, 0.22f to Color.Black,
            0.78f to Color.Black, 0.98f to Color.Transparent, 1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

// MARK: Rendering

internal class CcfM3(val r: DoubleArray) {
    fun apply(v: CcfV3) = CcfV3(
        r[0] * v.x + r[1] * v.y + r[2] * v.z,
        r[3] * v.x + r[4] * v.y + r[5] * v.z,
        r[6] * v.x + r[7] * v.y + r[8] * v.z,
    )

    operator fun times(b: CcfM3): CcfM3 {
        val o = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            o[i * 3 + j] = r[i * 3] * b.r[j] + r[i * 3 + 1] * b.r[3 + j] + r[i * 3 + 2] * b.r[6 + j]
        }
        return CcfM3(o)
    }

    companion object {
        fun x(a: Double) = CcfM3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, kotlin.math.cos(a), -sin(a), 0.0, sin(a), kotlin.math.cos(a)))
        fun y(a: Double) = CcfM3(doubleArrayOf(kotlin.math.cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, kotlin.math.cos(a)))
        fun z(a: Double) = CcfM3(doubleArrayOf(kotlin.math.cos(a), -sin(a), 0.0, sin(a), kotlin.math.cos(a), 0.0, 0.0, 0.0, 1.0))
    }
}

private const val FOCAL = 760.0
private val LIGHT = CcfV3(-0.5, 0.75, 0.6).normalized()
private val HALFWAY = (LIGHT + CcfV3(0.0, 0.0, 1.0)).normalized()

private fun ccfColor(c: CcfV3, alpha: Double = 1.0) = Color(
    c.x.coerceIn(0.0, 1.0).toFloat(), c.y.coerceIn(0.0, 1.0).toFloat(), c.z.coerceIn(0.0, 1.0).toFloat(),
    alpha.coerceIn(0.0, 1.0).toFloat(),
)

private fun shade(c: CcfV3, gloss: Double, n: CcfV3): CcfV3 {
    val lambert = max(0.0, n dot LIGHT)
    val spec = max(0.0, n dot HALFWAY).pow(28) * gloss * 0.85
    val rim = max(0.0, 1 - abs(n.z)).pow(4) * 0.08
    val k = 0.26 + 0.84 * lambert
    return CcfV3(c.x * k + spec + rim, c.y * k + spec + rim, c.z * k + spec + rim)
}

private fun project(q: CcfV3, center: Offset): Offset {
    val k = FOCAL / (FOCAL - q.z)
    return Offset((center.x + q.x * k).toFloat(), (center.y - q.y * k).toFloat())
}

private val GLASS_STOPS: List<List<Pair<Float, CcfV3>>> = listOf(
    listOf(0f to CcfV3(0.99, 0.99, 0.98), 0.35f to CcfV3(0.90, 0.88, 0.85), 0.75f to CcfV3(0.60, 0.58, 0.57), 1f to CcfV3(0.28, 0.27, 0.28)),
    listOf(0f to CcfV3(0.60, 0.66, 0.82), 0.18f to CcfV3(0.22, 0.26, 0.38), 0.6f to CcfV3(0.05, 0.06, 0.10), 1f to CcfV3(0.02, 0.02, 0.03)),
    listOf(0f to CcfV3(0.58, 0.88, 0.84), 0.2f to CcfV3(0.12, 0.36, 0.37), 0.65f to CcfV3(0.03, 0.08, 0.09), 1f to CcfV3(0.01, 0.02, 0.02)),
)

/** A camera's visible face, projected, with its depth. */
internal class CcfDrawn(
    val z: Double,
    val path: Path,
    val brush: Brush,
    val stroke: Boolean,
    /** A faint light edge on caps that face the viewer, like a bevel. */
    val rim: Double,
) {
    val decals = ArrayList<Pair<Path, Brush>>()
}

internal fun ccfFaces(cam: CcfCamera, m: CcfM3, scale: Double, bob: Double, center: Offset, alpha: Double): List<CcfDrawn> {
    val faces = cam.faces
    val out = ArrayList<CcfDrawn>()
    val slot = IntArray(faces.size) { -1 }
    fun view(p: CcfV3): CcfV3 {
        val q = m.apply(p * scale)
        return CcfV3(q.x, q.y + bob, q.z)
    }
    for ((i, f) in faces.withIndex()) {
        val nv = m.apply(f.normal)
        val cv = view(f.center)
        if ((nv dot (CcfV3(0.0, 0.0, FOCAL) - cv)) <= 0) continue
        if (f.parent >= 0 && slot[f.parent] < 0) continue
        val path = Path()
        f.pts.forEachIndexed { j, p ->
            val o = project(view(p), center)
            if (j == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
        val brush: Brush
        val n0 = f.n0
        val n1 = f.n1
        if (f.glass >= 0) {
            val u = f.axis cross f.normal
            val focus = project(view(f.center + (f.axis - u) * (0.32 * f.half)), center)
            val k = FOCAL / (FOCAL - cv.z)
            brush = Brush.radialGradient(
                *GLASS_STOPS[f.glass].map { it.first to ccfColor(it.second, alpha) }.toTypedArray(),
                center = focus, radius = (f.half * 1.45 * scale * k).toFloat(),
            )
        } else if (n0 != null && n1 != null) {
            val a = project(view((f.pts[0] + f.pts[3]) / 2.0), center)
            val b = project(view((f.pts[1] + f.pts[2]) / 2.0), center)
            val c0 = ccfColor(shade(f.color, f.gloss, m.apply(n0)), alpha)
            val c1 = ccfColor(shade(f.color, f.gloss, m.apply(n1)), alpha)
            brush = if (hypot(b.x - a.x, b.y - a.y) < 0.5f) SolidColor(c0) else Brush.linearGradient(listOf(c0, c1), a, b)
        } else {
            val base = shade(f.color, f.gloss, nv)
            val topP = project(view(f.center + f.axis * f.half), center)
            val bottomP = project(view(f.center - f.axis * f.half), center)
            brush = Brush.linearGradient(
                listOf(ccfColor(base * 1.12 + CcfV3(0.02, 0.02, 0.02), alpha), ccfColor(base * 0.86, alpha)), topP, bottomP,
            )
        }
        if (f.parent >= 0) {
            out[slot[f.parent]].decals.add(path to brush)
            slot[i] = slot[f.parent]
        } else {
            slot[i] = out.size
            val rim = if (f.glass < 0 && n0 == null) 0.16 * max(0.0, nv dot LIGHT) else 0.0
            out.add(CcfDrawn(cv.z + f.bias * scale, path, brush, f.glass < 0, rim))
        }
    }
    return out
}

internal fun ccfDraw(d: DrawScope, f: CcfDrawn) {
    d.drawPath(f.path, f.brush)
    if (f.stroke) d.drawPath(f.path, f.brush, style = Stroke(width = 0.7f))
    if (f.rim > 0.01) d.drawPath(f.path, Color.White.copy(alpha = f.rim.toFloat()), style = Stroke(width = 1f))
    for ((p, b) in f.decals) d.drawPath(p, b)
}

/** The whole picture, in dp. */
private fun ccfScene(d: DrawScope, w: Double, h: Double, s: CcfShown, reduce: Boolean) {
    val scale = w / 393
    val center = Offset((w / 2).toFloat(), (h * 0.35).toFloat())
    val cams = CcfCameras.all
    val i = indexOf(s.p)
    val frac = s.p - Math.round(s.p)
    val tau = 2 * PI

    // Spotlight in the accent of the cameras either side of p.
    val lo = floor(s.p).toInt().coerceIn(0, 4)
    val hi = min(4, lo + 1)
    val mixT = (s.p - lo).coerceIn(0.0, 1.0)
    val accent = cams[lo].accent * (1 - mixT) + cams[hi].accent * mixT
    d.drawRect(Color(0xFF080809), size = androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()))
    d.drawRect(
        Brush.radialGradient(
            listOf(ccfColor(accent, 0.26), ccfColor(accent, 0.08), ccfColor(accent, 0.0)),
            center = Offset(center.x, (center.y - 10 * scale).toFloat()), radius = (270 * scale).toFloat(),
        ),
        size = androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
    )
    // A faint floor under the camera.
    d.withTransform({
        translate(center.x, (center.y + 118 * scale).toFloat())
        scale(1f, 0.16f, Offset.Zero)
    }) {
        val r = (150 * scale).toFloat()
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0f)), center = Offset.Zero, radius = r),
            radius = r, center = Offset.Zero,
        )
    }

    // The camera.
    val phi = if (reduce) 0.0 else frac * PI
    val idleYaw = s.idle * 7 * PI / 180 * sin(tau * s.clock / 4)
    val bob = s.idle * 4 * sin(tau * s.clock / 4 + 1) * scale
    val m = CcfM3.y(idleYaw - 0.18) * CcfM3.x(0.21) * CcfM3.x(phi) * CcfM3.y(0.35 * phi)
    val alpha = if (reduce) 1 - 2 * abs(frac) else 1.0
    val faces = ccfFaces(cams[i], m, scale * 1.06, bob, center, alpha)

    // The photos on their ring.
    val ring = 172 * scale
    val tilt = CcfM3.x(0.26)
    val sway = if (reduce) 0.0 else -s.p * 0.75 * PI + s.idle * 0.1 * sin(tau * s.clock / 8)
    class Placed(val c: CcfV3, val ex: CcfV3, val ey: CcfV3, val look: Int, val dim: Double, val fade: Double)
    val placed = ArrayList<Placed>()
    for (k in 0 until 8) {
        val a = sway + k * tau / 8
        val pk = s.photoP[k]
        val kr = Math.round(pk).toDouble()
        val flip = if (reduce) 0.0 else (pk - kr) * PI
        val photoBob = s.idle * 3 * sin(tau * s.clock / 4 + k) * scale
        val c = tilt.apply(CcfV3(ring * sin(a), CcfPhotos.heights[k] * scale, ring * kotlin.math.cos(a))) +
            CcfV3(0.0, photoBob + bob * 0.5, 0.0)
        val rot = tilt * CcfM3.y(0.55 * sin(a) + flip) * CcfM3.z(CcfPhotos.rolls[k] * PI / 180)
        val look = cams[kr.toInt().coerceIn(0, 4)].look
        val dim = 0.42 + 0.58 * CcfTimeline.smoothstep(-ring, ring * 0.7, c.z)
        placed.add(
            Placed(
                c, rot.apply(CcfV3(1.0, 0.0, 0.0)), rot.apply(CcfV3(0.0, 1.0, 0.0)), look, dim,
                if (reduce) 1 - 2 * abs(pk - kr) else 1.0,
            ),
        )
    }

    // Back to front: faces are drawn by their index (>= 0), photos by -1 - k.
    val order = ArrayList<Pair<Double, Int>>()
    faces.forEachIndexed { j, f -> order.add(f.z to j) }
    placed.forEachIndexed { k, p -> order.add(p.c.z to (-1 - k)) }
    order.sortBy { it.first }
    for ((_, id) in order) {
        if (id >= 0) {
            ccfDraw(d, faces[id])
            continue
        }
        val k = -1 - id
        val pl = placed[k]
        val pw = 64 * scale
        val ph = 78 * scale
        val tl = project(pl.c - pl.ex * (pw / 2) + pl.ey * (ph / 2), center)
        val tr = project(pl.c + pl.ex * (pw / 2) + pl.ey * (ph / 2), center)
        val bl = project(pl.c - pl.ex * (pw / 2) - pl.ey * (ph / 2), center)
        val a = (tr.x - tl.x) / 64f
        val b = (tr.y - tl.y) / 64f
        val cc = (bl.x - tl.x) / 78f
        val dd = (bl.y - tl.y) / 78f
        val matrix = android.graphics.Matrix().apply { setValues(floatArrayOf(a, cc, tl.x, b, dd, tl.y, 0f, 0f, 1f)) }
        val canvas = d.drawContext.canvas
        canvas.save()
        canvas.nativeCanvas.concat(matrix)
        ccfPhoto(d, k, pl.look, a * dd - b * cc > 0, pl.fade)
        d.drawRoundRect(
            Color.Black.copy(alpha = ((1 - pl.dim) * pl.fade).toFloat()),
            size = androidx.compose.ui.geometry.Size(64f, 78f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
        )
        canvas.restore()
    }
}

/** One instant photo in a 64 × 78 frame: a 56 × 56 picture and a written caption. */
private fun ccfPhoto(d: DrawScope, scene: Int, look: Int, front: Boolean, fade: Double) {
    val f = fade.toFloat()
    d.drawRoundRect(
        Color(0.96f, 0.95f, 0.92f, f), size = androidx.compose.ui.geometry.Size(64f, 78f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
    )
    if (!front) {
        d.drawRect(Color.Black.copy(alpha = 0.08f * f), Offset(8f, 30f), androidx.compose.ui.geometry.Size(48f, 1.5f))
        return
    }
    val box = Rect(4f, 4f, 60f, 60f)
    fun c(v: CcfV3) = ccfColor(CcfPhotos.look(v, look), fade)
    fun px(x: Double) = (box.left + x * 56).toFloat()
    fun py(y: Double) = (box.top + y * 56).toFloat()
    d.clipRect(box.left, box.top, box.right, box.bottom) {
        for (op in CcfPhotos.scenes[scene]) {
            when (op) {
                is CcfOp.Rect -> drawRect(c(op.c), Offset(px(op.x), py(op.y)), androidx.compose.ui.geometry.Size((op.w * 56).toFloat(), (op.h * 56).toFloat()))
                is CcfOp.Grad -> drawRect(
                    Brush.verticalGradient(listOf(c(op.top), c(op.bottom)), startY = py(op.y), endY = py(op.y + op.h)),
                    Offset(px(op.x), py(op.y)), androidx.compose.ui.geometry.Size((op.w * 56).toFloat(), (op.h * 56).toFloat()),
                )
                is CcfOp.Circle -> drawCircle(c(op.c), (op.r * 56).toFloat(), Offset(px(op.x), py(op.y)))
                is CcfOp.Poly -> {
                    val path = Path()
                    op.pts.forEachIndexed { j, p -> if (j == 0) path.moveTo(px(p.x), py(p.y)) else path.lineTo(px(p.x), py(p.y)) }
                    path.close()
                    drawPath(path, c(op.c))
                }
            }
        }
        // A soft sheen across the print.
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.16f * f), Color.White.copy(alpha = 0f)),
                Offset(px(0.0), py(0.0)), Offset(px(0.6), py(0.6)),
            ),
            Offset(box.left, box.top), box.size,
        )
    }
    // The caption, as if written in pen.
    val r1 = androidx.compose.ui.geometry.CornerRadius(1.1f, 1.1f)
    d.drawRoundRect(Color.Black.copy(alpha = 0.28f * f), Offset(7f, 66f), androidx.compose.ui.geometry.Size(26f, 2.2f), r1)
    d.drawRoundRect(Color.Black.copy(alpha = 0.16f * f), Offset(7f, 71f), androidx.compose.ui.geometry.Size(14f, 2f), androidx.compose.ui.geometry.CornerRadius(1f, 1f))
    d.drawCircle(Color.Black.copy(alpha = 0.18f * f), 2.5f, Offset(54.5f, 68.5f))
}
