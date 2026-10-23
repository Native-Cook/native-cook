package com.ngocdevv.nativecook.blocks.media.lighttableloupe

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Light Table Loupe — an original Native Cook design: a glowing light table of 35 mm slides and a loupe
 that magnifies the slide under it. A port of the SwiftUI block (LightTableLoupe.swift); the numbers are
 the same.

 Look: a black header ("CONTACT 04 · 12 FRAMES" in 12 sp mono caps, a red "1 MARKED" at the right when a
 slide is marked). The rest is the lit table: #EEF1F4 at the middle to #CFD6DD at the corners, a fine
 deterministic grain, and a soft sheen that crosses it. Twelve slide mounts in 3 × 4 (cardboard white
 #F7F6F2, 3 dp corners, 82 % as tall as wide, a window 74 % × 64 % of the mount with the frame number and
 "OCT 84" printed under it in 6.5 mono; 3 columns 14 dp apart, rows 20 dp apart) each hold a backlit
 transparency drawn as gradient art (LightTableLoupeModel.kt), its colour spilling onto the table. Below
 the grid, side by side and centred in the space left: a slide viewer (a dark body with a 1.4 : 1 screen
 that projects the slide under the loupe, with a faint flicker, or a dim lamp when the gate is empty; its
 caption under it) and the loupe's home, a dashed ring centred on the viewer's middle.

 The loupe: a black housing (128 dp across: a 9 dp ring, a 2 dp chrome lip) over a lens of 52 dp radius
 that shows the table 2.2 × larger about its centre, with grain, a vignette, a red and a blue fringe at the
 rim and a glass highlight; its shadow grows when it is lifted. Landing on a new slide pulls focus: 6 → 0
 of blur in 0.25 s (a RenderEffect, Android 12 and later). A mark is a red (#E5342C) grease-pencil circle
 drawn round the slide in 0.4 s; the marked slide shifts 3 dp and tilts 1.5°.

 autoPlay: LOOP = 9 s, timeline in LightTableLoupeModel.kt.
 Touch: drag the loupe (grabbed where it was touched; a drag that starts elsewhere brings it there, 64 dp
 above the finger, in 0.25 s); a tick at each new slide under it. Tap a slide, or the loupe, to mark or
 unmark the slide. 2 s after the last touch the loupe glides home, the marks are wiped and the loop starts
 again.
 Reduced motion (animations off): no loop, no sheen or breathing light, no focus pull; marks appear at once.
 */

private const val LENS = 52.0
private const val ZOOM = 2.2
private val RED = 0xFFE5342C.toInt()

private fun nowSeconds() = System.nanoTime() / 1e9

private fun argb(rgb: Long, alpha: Double = 1.0): Int =
    android.graphics.Color.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), ((rgb shr 16) and 255).toInt(), ((rgb shr 8) and 255).toInt(), (rgb and 255).toInt())

internal class LtlLayout(val width: Double, val height: Double, val top: Double, bottomInset: Double) {
    val header = top + 40
    val mountW = floor((width - 40 - 28) / 3)
    val mountH = Math.round(mountW * 0.82).toDouble()
    val gapX = 14.0
    val gapY = 20.0
    val gridX = (width - 3 * mountW - 2 * gapX) / 2
    val gridY = header + 22
    private val gridBottom = gridY + 4 * mountH + 3 * gapY
    private val bottom = height - max(bottomInset, 20.0)
    // The lower area: the viewer and its caption on the left, the loupe's home on the right, centred
    // together between the grid and the bottom.
    val parkX = width - 20 - 68
    private val viewerW = parkX - 68 - 16 - 20
    private val screenH = Math.round((viewerW - 20) / 1.4).toDouble()
    private val block = screenH + 20 + 10 + 40
    val viewerY = gridBottom + 24 + max(0.0, (bottom - gridBottom - 24 - block) / 2)
    val viewer = RectF(20f, viewerY.toFloat(), (20 + viewerW).toFloat(), (viewerY + screenH + 20).toFloat())
    val screen = RectF(viewer.left + 10, viewer.top + 10, viewer.right - 10, viewer.bottom - 10)
    val parkY = viewerY + (screenH + 20) / 2
    val captionY = viewerY + screenH + 20 + 10
    val viewerWidth = viewerW

    fun originX(i: Int) = gridX + (i % 3) * (mountW + gapX)
    fun originY(i: Int) = gridY + (i / 3) * (mountH + gapY)
    fun center(i: Int) = doubleArrayOf(originX(i) + mountW / 2, originY(i) + mountH / 2)
    fun window(i: Int): RectF {
        val ww = mountW * 0.74
        val wh = mountH * 0.64
        val x = originX(i) + (mountW - ww) / 2
        val y = originY(i) + mountH * 0.12
        return RectF(x.toFloat(), y.toFloat(), (x + ww).toFloat(), (y + wh).toFloat())
    }
    fun slideAt(x: Double, y: Double): Int? {
        for (i in 0 until 12) {
            val ox = originX(i)
            val oy = originY(i)
            if (x >= ox - gapX / 2 && x <= ox + mountW + gapX / 2 && y >= oy - gapY / 2 && y <= oy + mountH + gapY / 2) return i
        }
        return null
    }
}

private class LtlController(val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    var loupe by mutableStateOf<DoubleArray?>(null)
    /** [date, fromX, fromY, toX, toY, duration]. */
    var glide by mutableStateOf<DoubleArray?>(null)
    var grabX = 0.0
    var grabY = 0.0
    var grabbing = false
    var liftDate by mutableDoubleStateOf(-10.0)
    var liftUp by mutableStateOf(false)
    var focus by mutableStateOf(-1)
    var focusDate by mutableDoubleStateOf(-10.0)
    /** Slide → [date, on (1) or off (0)]. */
    val marks = mutableStateMapOf<Int, DoubleArray>()
    var moved = false
    var away = false
    var active by mutableStateOf(false)
    var resume: Job? = null

    val running get() = (autoStart != null && !reduced) || active

    fun frame(at: Double, l: LtlLayout): LtlFrame {
        val M = LightTableLoupeModel
        val start = autoStart
        if (start != null && at >= start) {
            val t = (at - start) % M.LOOP
            val f = M.frame(t, l.parkX, l.parkY) { l.center(it) }
            if (f.markAlpha > 0) f.marks[M.MARKED] = LtlMark(f.markDraw, f.markAlpha, f.markTilt)
            f.markCount = if (f.markAlpha > 0) 1 else 0
            if (reduced) { f.blur = 0.0; f.sheen = -1.0; f.pulse = 0.0 }
            return f
        }
        val f = LtlFrame()
        val p = loupe ?: doubleArrayOf(l.parkX, l.parkY)
        f.loupeX = p[0]; f.loupeY = p[1]
        val g = glide
        if (g != null) {
            val u = M.clamp((at - g[0]) / g[5])
            val q = M.arc(g[1], g[2], g[3], g[4], u, if (g[5] > 0.5) -0.18 else 0.0)
            f.loupeX = q[0]; f.loupeY = q[1]
        }
        val lu = M.clamp((at - liftDate) / 0.15)
        f.lift = if (liftUp) lu else 1 - lu
        if (focus >= 0) {
            f.focus = focus
            val since = at - focusDate
            f.blur = if (reduced) 0.0 else 6 * (1 - M.smoothstep(0.0, 0.25, since))
            f.caption = M.smoothstep(0.0, 0.25, since)
        }
        f.hint = if (focus >= 0) 0.0 else 1.0
        var count = 0
        for ((i, m) in marks) {
            val since = at - m[0]
            if (m[1] > 0) {
                val draw = if (reduced) 1.0 else M.clamp(since / 0.4)
                f.marks[i] = LtlMark(draw, if (reduced) M.clamp(since / 0.2) else 1.0, if (reduced) 0.0 else M.outBack(since / 0.3))
                count++
            } else {
                val a = 1 - M.clamp(since / 0.25)
                if (a > 0) f.marks[i] = LtlMark(1.0, a, if (reduced) 0.0 else 1 - M.inOut(since / 0.6))
            }
        }
        f.counter = min(1.0, count.toDouble())
        f.markCount = count
        f.flicker = sin(2 * PI * 2 * (at % 1000))
        return f
    }

    fun takeOver(now: Double, l: LtlLayout) {
        resume?.cancel()
        active = true
        val start = autoStart ?: return
        val t = (now - start) % LightTableLoupeModel.LOOP
        val f = LightTableLoupeModel.frame(t, l.parkX, l.parkY) { l.center(it) }
        autoStart = null
        loupe = doubleArrayOf(f.loupeX, f.loupeY)
        glide = null
        marks.clear()
        if (f.markAlpha > 0.5) marks[LightTableLoupeModel.MARKED] = doubleArrayOf(now - 1, 1.0)
        if (f.caption > 0.5) { focus = f.focus; focusDate = now - 1 } else { focus = -1; focusDate = -10.0 }
        liftDate = now - 1
        liftUp = f.lift > 0.5
    }
}

@Composable
fun LightTableLoupe(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { LtlController(reduced) }
    val painter = remember { LtlPainter() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.running) { while (c.running) withFrameNanos { c.frame = it / 1e9 } }

    fun scheduleResume(l: LtlLayout) {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2000)
            val now = nowSeconds()
            val f = c.frame(now, l)
            c.glide = doubleArrayOf(now, f.loupeX, f.loupeY, l.parkX, l.parkY, 0.8)
            c.loupe = doubleArrayOf(l.parkX, l.parkY)
            c.focus = -1
            c.focusDate = now
            for ((i, m) in c.marks.toMap()) if (m[1] > 0) c.marks[i] = doubleArrayOf(now, 0.0)
            delay(800)
            c.glide = null
            c.marks.clear()
            if (autoPlay && !reduced) {
                // Join the loop in its final rest.
                c.autoStart = nowSeconds() - 8.2
            } else {
                c.active = false
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFDDE2E7))) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val l = remember(maxWidth, maxHeight, top, bottomInset) { LtlLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottomInset) }
        val ink = Color(0xFF1A1C21)

        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Light table with twelve slides and a loupe" }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val sx = down.position.x / density.toDouble()
                        val sy = down.position.y / density.toDouble()
                        var now = nowSeconds()
                        c.takeOver(now, l)
                        val cur = c.frame(now, l)
                        c.moved = false
                        c.away = hypot(sx - cur.loupeX, sy - cur.loupeY) > 70
                        if (c.away) {
                            // A drag that starts away from the loupe brings it over, 64 dp above the finger.
                            c.grabX = 0.0; c.grabY = -64.0
                        } else {
                            c.grabX = cur.loupeX - sx; c.grabY = cur.loupeY - sy
                            c.liftDate = now; c.liftUp = true
                        }
                        var lx = sx
                        var ly = sy
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            lx = change.position.x / density.toDouble()
                            ly = change.position.y / density.toDouble()
                            now = nowSeconds()
                            val tx = lx + c.grabX
                            val ty = ly + c.grabY
                            if (!c.moved) {
                                if (hypot(lx - sx, ly - sy) <= 6) continue
                                c.moved = true
                                if (c.away) {
                                    val f = c.frame(now, l)
                                    c.glide = doubleArrayOf(now, f.loupeX, f.loupeY, tx, ty, 0.25)
                                    c.liftDate = now; c.liftUp = true
                                }
                            }
                            val g = c.glide
                            if (g != null && now - g[0] < g[5]) {
                                c.glide = doubleArrayOf(g[0], g[1], g[2], tx, ty, g[5])
                            } else {
                                c.glide = null
                                c.loupe = doubleArrayOf(tx, ty)
                            }
                            // A tick at each new slide under the loupe.
                            val i = l.slideAt(tx, ty) ?: -1
                            if (i != c.focus) {
                                c.focus = i
                                c.focusDate = now
                                if (i >= 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        now = nowSeconds()
                        if (c.liftUp) { c.liftDate = now; c.liftUp = false }
                        if (!c.moved) {
                            // A tap marks the slide under the finger, or under the loupe when the loupe was tapped.
                            val f = c.frame(now, l)
                            val i = if (c.away) l.slideAt(lx, ly) else l.slideAt(f.loupeX, f.loupeY)
                            if (i != null) {
                                val on = (c.marks[i]?.get(1) ?: 0.0) <= 0
                                c.marks[i] = doubleArrayOf(now, if (on) 1.0 else 0.0)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                        val g = c.glide
                        if (g != null) {
                            // Let a trip still under way finish where the finger left the loupe.
                            c.loupe = doubleArrayOf(g[3], g[4])
                            if (now - g[0] >= g[5]) c.glide = null
                        }
                        scheduleResume(l)
                    }
                },
        ) {
            // The table, and the loupe's shadow.
            Canvas(Modifier.fillMaxSize()) {
                val f = c.frame(c.frame, l)
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                painter.table(canvas, l, f)
                painter.loupeShadow(canvas, f)
                canvas.restore()
            }
            // The table, larger, under the glass (blurred while focus is pulled).
            val box = LENS * 1.04
            Canvas(
                Modifier
                    .size((box * 2).dp)
                    .graphicsLayer {
                        val f = c.frame(c.frame, l)
                        translationX = ((f.loupeX - box) * density).toFloat()
                        translationY = ((f.loupeY - box) * density).toFloat()
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && f.blur > 0.1) {
                            BlurEffect((f.blur * density).toFloat(), (f.blur * density).toFloat(), TileMode.Decal)
                        } else {
                            null
                        }
                    },
            ) {
                val f = c.frame(c.frame, l)
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                canvas.translate((box - f.loupeX).toFloat(), (box - f.loupeY).toFloat())
                painter.lens(canvas, l, f)
                canvas.restore()
            }
            // The housing.
            Canvas(Modifier.fillMaxSize()) {
                val f = c.frame(c.frame, l)
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                painter.housing(canvas, f)
                canvas.restore()
            }

            // Header.
            Box(Modifier.fillMaxWidth().height(l.header.dp).background(Color(0xFF0F0F12))) {
                Row(
                    Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomStart).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(
                        "CONTACT 04 · 12 FRAMES",
                        style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp),
                    )
                    Row(
                        Modifier.graphicsLayer { alpha = c.frame(c.frame, l).counter.toFloat() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(Color(RED), CircleShape))
                        val n = max(1, c.frame(c.frame, l).markCount)
                        BasicText(
                            "$n MARKED",
                            style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }

            // Caption, under the viewer.
            val captionWidth = l.viewerWidth.dp
            Column(
                Modifier.offset(x = 20.dp, y = l.captionY.dp).width(captionWidth).graphicsLayer { alpha = c.frame(c.frame, l).hint.toFloat().coerceIn(0f, 1f) },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                BasicText("VIEWER · GATE EMPTY", style = TextStyle(color = ink.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                BasicText(
                    "Drag the loupe over a slide",
                    style = TextStyle(color = ink.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 15.sp),
                )
            }
            val shown = c.frame(c.frame, l)
            val i = max(0, shown.focus)
            Column(
                Modifier.offset(x = 20.dp, y = l.captionY.dp).width(captionWidth).graphicsLayer {
                    val f = c.frame(c.frame, l)
                    alpha = if (f.focus >= 0) f.caption.toFloat() else 0f
                },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                BasicText("FRAME %02d · OCT 84".format(i + 1), style = TextStyle(color = ink.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                BasicText(LightTableLoupeModel.slides[i].title, style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Draws the table, the lens and the housing in dp, through android.graphics. */
private class LtlPainter {
    private val M = LightTableLoupeModel
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 6.5f
        color = argb(0x8A867C)
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.MONOSPACE, 500, false) else Typeface.MONOSPACE
    }
    private val mountShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(0xF7F6F2)
        setShadowLayer(2.5f, 0f, 1.5f, argb(0x000000, 0.14))
    }
    private var grainFor = -1.0
    private val grainDark = Path()
    private val grainLight = Path()
    private val lensDark = Path()
    private val lensLight = Path()

    private fun grain(l: LtlLayout) {
        if (grainFor == l.width) return
        grainFor = l.width
        grainDark.reset(); grainLight.reset()
        val panelH = l.height - l.header
        for (i in 0 until 700) {
            val x = (M.hash(i, 3) * l.width).toFloat()
            val y = (l.header + M.hash(i, 4) * panelH).toFloat()
            val p = if (M.hash(i, 5) > 0.5) grainDark else grainLight
            p.addOval(RectF(x - 0.6f, y - 0.6f, x + 0.6f, y + 0.6f), Path.Direction.CW)
        }
        lensDark.reset(); lensLight.reset()
        for (i in 0 until 400) {
            val a = M.hash(i, 7) * 2 * PI
            val d = sqrt(M.hash(i, 8)) * LENS
            val x = (d * cos(a)).toFloat()
            val y = (d * sin(a)).toFloat()
            val p = if (M.hash(i, 9) > 0.5) lensDark else lensLight
            p.addOval(RectF(x - 0.5f, y - 0.5f, x + 0.5f, y + 0.5f), Path.Direction.CW)
        }
    }

    fun table(canvas: android.graphics.Canvas, l: LtlLayout, f: LtlFrame) {
        grain(l)
        val panel = RectF(0f, l.header.toFloat(), l.width.toFloat(), l.height.toFloat())
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(panel.centerX(), panel.centerY(), (hypot(panel.width().toDouble(), panel.height().toDouble()) / 2).toFloat(),
            argb(0xEEF1F4), argb(0xCFD6DD), Shader.TileMode.CLAMP)
        canvas.drawRect(panel, fill)
        fill.shader = null
        // The light breathes ±1.5 %.
        fill.color = argb(0xFFFFFF, 0.03 + 0.03 * f.pulse)
        canvas.drawRect(panel, fill)
        fill.color = argb(0x000000, 0.05); canvas.drawPath(grainDark, fill)
        fill.color = argb(0xFFFFFF, 0.5); canvas.drawPath(grainLight, fill)
        // Colour spilling through each slide onto the table.
        for (i in 0 until 12) {
            val cc = l.center(i)
            val r = (l.mountW * 0.85).toFloat()
            val col = M.slides[i].bottom
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = RadialGradient(cc[0].toFloat(), cc[1].toFloat(), r, argb(col, 0.16), argb(col, 0.0), Shader.TileMode.CLAMP)
            canvas.drawCircle(cc[0].toFloat(), cc[1].toFloat(), r, fill)
        }
        fill.shader = null
        // The parking ring.
        stroke.strokeWidth = 1f
        stroke.color = argb(0x8E98A3, 0.7)
        stroke.pathEffect = DashPathEffect(floatArrayOf(4f, 5f), 0f)
        canvas.drawCircle(l.parkX.toFloat(), l.parkY.toFloat(), 68f, stroke)
        stroke.pathEffect = null
        viewer(canvas, l, f)
        for (i in 0 until 12) slide(canvas, l, i, f.marks[i])
        for ((i, m) in f.marks) mark(canvas, l, i, m)
        // A soft sheen crossing the glass.
        if (f.sheen >= 0) {
            val x = (-0.5 * l.width + 2 * l.width * f.sheen).toFloat()
            val a = 0.2 * sin(PI * f.sheen)
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = LinearGradient(x - 90, panel.top, x + 90, panel.top + 60,
                intArrayOf(argb(0xFFFFFF, 0.0), argb(0xFFFFFF, a), argb(0xFFFFFF, 0.0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(panel, fill)
            fill.shader = null
        }
    }

    private val viewerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(10f, 0f, 6f, argb(0x000000, 0.25)) }

    /** The slide viewer: a dark body, its screen projecting the slide under the loupe. */
    private fun viewer(canvas: android.graphics.Canvas, l: LtlLayout, f: LtlFrame) {
        val v = l.viewer
        val sc = l.screen
        viewerShadow.shader = LinearGradient(v.centerX(), v.top, v.centerX(), v.bottom, argb(0x24272D), argb(0x121418), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(v, 12f, 12f, viewerShadow)
        stroke.strokeWidth = 1f
        stroke.color = argb(0xFFFFFF, 0.08)
        canvas.drawRoundRect(RectF(v.left + 0.5f, v.top + 0.5f, v.right - 0.5f, v.bottom - 0.5f), 12f, 12f, stroke)
        fill.color = argb(0x0A0B0D)
        canvas.drawRoundRect(sc, 4f, 4f, fill)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(sc, 4f, 4f, Path.Direction.CW) })
        // A dim lamp when the gate is empty.
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(sc.centerX(), sc.centerY(), sc.width() * 0.6f, argb(0xFFFFFF, 0.10), argb(0xFFFFFF, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(sc, fill)
        fill.shader = null
        val a = if (f.focus >= 0) f.caption else 0.0
        if (a > 0.001) {
            val save = canvas.saveLayerAlpha(sc, (a * 255).roundToInt())
            art(canvas, sc, f.focus)
            canvas.restoreToCount(save)
        }
        // Lamp flicker and the projector's falloff.
        fill.color = argb(0xFFFFFF, 0.01 + 0.01 * f.flicker)
        canvas.drawRect(sc, fill)
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(sc.centerX(), sc.centerY(), (hypot(sc.width().toDouble(), sc.height().toDouble()) / 2).toFloat(),
            intArrayOf(argb(0x000000, 0.0), argb(0x000000, 0.0), argb(0x000000, 0.45)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(sc, fill)
        fill.shader = null
        canvas.restore()
        stroke.color = argb(0x2E3238)
        canvas.drawRoundRect(RectF(sc.left - 0.5f, sc.top - 0.5f, sc.right + 0.5f, sc.bottom + 0.5f), 4.5f, 4.5f, stroke)
    }

    private fun slide(canvas: android.graphics.Canvas, l: LtlLayout, i: Int, mark: LtlMark?) {
        val ox = l.originX(i).toFloat()
        val oy = l.originY(i).toFloat()
        val m = RectF(ox, oy, ox + l.mountW.toFloat(), oy + l.mountH.toFloat())
        val tilt = (mark?.tilt ?: 0.0).toFloat()
        canvas.save()
        if (tilt != 0f) {
            canvas.translate(3 * tilt, -2 * tilt)
            canvas.rotate(1.5f * tilt, m.centerX(), m.centerY())
        }
        canvas.drawRoundRect(m, 3f, 3f, mountShadow)
        stroke.strokeWidth = 0.5f
        stroke.color = argb(0x000000, 0.08)
        canvas.drawRoundRect(RectF(m.left + 0.25f, m.top + 0.25f, m.right - 0.25f, m.bottom - 0.25f), 3f, 3f, stroke)
        val w = l.window(i)
        stroke.strokeWidth = 2f
        stroke.color = argb(0xE2DED3)
        canvas.drawRoundRect(RectF(w.left - 1.5f, w.top - 1.5f, w.right + 1.5f, w.bottom + 1.5f), 2f, 2f, stroke)
        art(canvas, w, i)
        // Printed on the mount.
        val y = m.bottom - (m.bottom - w.bottom) / 2
        val fm = label.fontMetrics
        val base = y - (fm.ascent + fm.descent) / 2
        label.textAlign = Paint.Align.LEFT
        canvas.drawText("%02d".format(i + 1), w.left, base, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("OCT 84", w.right, base, label)
        canvas.restore()
    }

    /** A transparency, lit from behind. */
    private fun art(canvas: android.graphics.Canvas, r: RectF, index: Int) {
        val s = M.slides[index]
        canvas.save()
        canvas.clipRect(r)
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = LinearGradient(r.centerX(), r.top, r.centerX(), r.bottom, argb(s.top), argb(s.bottom), Shader.TileMode.CLAMP)
        canvas.drawRect(r, fill)
        if (index == M.AURORA) {
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = RadialGradient(r.left + 0.35f * r.width(), r.top + 0.40f * r.height(), 0.5f * r.width(), argb(0x38F2A0, 0.55), argb(0x38F2A0, 0.0), Shader.TileMode.CLAMP)
            canvas.drawRect(r, fill)
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = RadialGradient(r.left + 0.72f * r.width(), r.top + 0.22f * r.height(), 0.42f * r.width(), argb(0x8F5BFF, 0.40), argb(0x8F5BFF, 0.0), Shader.TileMode.CLAMP)
            canvas.drawRect(r, fill)
        }
        if (s.sunR > 0) {
            val cx = (r.left + s.sunX * r.width()).toFloat()
            val cy = (r.top + s.sunY * r.height()).toFloat()
            val rad = (s.sunR * r.width()).toFloat()
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = RadialGradient(cx, cy, rad * 3, argb(s.sunColor, s.glow), argb(s.sunColor, 0.0), Shader.TileMode.CLAMP)
            canvas.drawRect(r, fill)
            fill.shader = null
            fill.color = argb(s.sunColor)
            canvas.drawCircle(cx, cy, rad, fill)
        }
        fill.shader = null
        for (ridge in s.ridges) {
            val p = Path()
            p.moveTo(r.left, r.bottom)
            for (k in 0..24) {
                val u = k / 24.0
                val y = ridge.base + ridge.amp * sin(2 * PI * ridge.freq * u + ridge.phase)
                p.lineTo((r.left + u * r.width()).toFloat(), (r.top + y * r.height()).toFloat())
            }
            p.lineTo(r.right, r.bottom)
            p.close()
            fill.color = argb(ridge.color)
            canvas.drawPath(p, fill)
        }
        // Backlight: brighter in the middle, a soft dark edge where the film meets the mount.
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(r.centerX(), r.centerY(), (hypot(r.width().toDouble(), r.height().toDouble()) * 0.5).toFloat(),
            argb(0xFFFFFF, 0.22), argb(0xFFFFFF, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(r, fill)
        fill.shader = null
        stroke.color = argb(0x000000, 0.22); stroke.strokeWidth = 1.5f
        canvas.drawRect(RectF(r.left + 0.75f, r.top + 0.75f, r.right - 0.75f, r.bottom - 0.75f), stroke)
        stroke.color = argb(0x000000, 0.08); stroke.strokeWidth = 3f
        canvas.drawRect(RectF(r.left + 1.5f, r.top + 1.5f, r.right - 1.5f, r.bottom - 1.5f), stroke)
        canvas.restore()
    }

    /** The grease-pencil circle: two rough turns starting at the upper left. */
    private fun markPath(l: LtlLayout, i: Int, tilt: Double): Path {
        val c0 = l.center(i)
        val cx = c0[0] + 3 * tilt
        val cy = c0[1] - 2 * tilt
        val rx = l.mountW / 2 + 9
        val ry = l.mountH / 2 + 9
        val p = Path()
        val n = 72
        for (k in 0..n) {
            val th = 2 * PI * 1.12 * k / n
            val a = PI * 1.15 + th
            val j = 1 + 0.035 * sin(3 * th + 0.7) + 0.02 * sin(7 * th + 1.3) + th / (2 * PI) * 0.05
            val x = (cx + rx * j * cos(a)).toFloat()
            val y = (cy + ry * j * sin(a)).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        return p
    }

    private fun mark(canvas: android.graphics.Canvas, l: LtlLayout, i: Int, m: LtlMark) {
        if (m.draw <= 0 || m.alpha <= 0) return
        val full = markPath(l, i, m.tilt)
        val measure = PathMeasure(full, false)
        val path = Path()
        measure.getSegment(0f, (measure.length * m.draw).toFloat(), path, true)
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeWidth = 3f
        stroke.color = (RED and 0x00FFFFFF) or (((0.9 * m.alpha) * 255).toInt() shl 24)
        canvas.drawPath(path, stroke)
        canvas.save()
        canvas.translate(0.8f, 0.6f)
        stroke.strokeWidth = 1.2f
        stroke.color = (RED and 0x00FFFFFF) or (((0.45 * m.alpha) * 255).toInt() shl 24)
        canvas.drawPath(path, stroke)
        canvas.restore()
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeJoin = Paint.Join.MITER
    }

    private fun scale(f: LtlFrame) = 1 + 0.04 * f.lift - 0.04 * f.press

    fun loupeShadow(canvas: android.graphics.Canvas, f: LtlFrame) {
        val s = scale(f)
        fill.maskFilter = BlurMaskFilter((6 + 8 * f.lift).toFloat(), BlurMaskFilter.Blur.NORMAL)
        fill.color = argb(0x000000, 0.30 + 0.08 * f.lift)
        canvas.drawCircle((f.loupeX + 4 + 6 * f.lift).toFloat(), (f.loupeY + 8 + 10 * f.lift).toFloat(), (64 * s).toFloat(), fill)
        fill.maskFilter = null
    }

    /** The table, 2.2 × larger about the loupe's centre, inside the lens. */
    fun lens(canvas: android.graphics.Canvas, l: LtlLayout, f: LtlFrame) {
        val s = scale(f)
        val cx = f.loupeX.toFloat()
        val cy = f.loupeY.toFloat()
        canvas.save()
        val clip = Path().apply { addCircle(cx, cy, (LENS * s).toFloat(), Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.scale(ZOOM.toFloat(), ZOOM.toFloat(), cx, cy)
        table(canvas, l, f)
        canvas.restore()
    }

    fun housing(canvas: android.graphics.Canvas, f: LtlFrame) {
        val s = scale(f)
        val cx = f.loupeX.toFloat()
        val cy = f.loupeY.toFloat()
        val lens = (LENS * s).toFloat()
        canvas.save()
        val clip = Path().apply { addCircle(cx, cy, lens, Path.Direction.CW) }
        canvas.clipPath(clip)
        // Film grain under the glass.
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s.toFloat(), s.toFloat())
        fill.color = argb(0x000000, 0.12); canvas.drawPath(lensDark, fill)
        fill.color = argb(0xFFFFFF, 0.18); canvas.drawPath(lensLight, fill)
        canvas.restore()
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(cx, cy, lens, intArrayOf(argb(0x000000, 0.0), argb(0x000000, 0.0), argb(0x000000, 0.35)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, lens, fill)
        // A glass highlight.
        canvas.save()
        canvas.translate((cx - 17 * s).toFloat(), (cy - 23 * s).toFloat())
        canvas.rotate(-30f)
        val hw = (23 * s).toFloat()
        val hh = (12 * s).toFloat()
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = LinearGradient(0f, -hh, 0f, hh, argb(0xFFFFFF, 0.32), argb(0xFFFFFF, 0.0), Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(-hw, -hh, hw, hh), fill)
        canvas.restore()
        fill.shader = null
        canvas.restore()
        // Colour fringes at the rim.
        stroke.strokeWidth = 1.2f
        stroke.color = android.graphics.Color.argb(89, 255, 51, 51)
        canvas.drawCircle(cx - 0.6f, cy - 0.2f, lens - 0.8f, stroke)
        stroke.color = android.graphics.Color.argb(89, 51, 102, 255)
        canvas.drawCircle(cx + 0.6f, cy - 0.2f, lens - 0.8f, stroke)
        // Chrome lip and black housing.
        stroke.strokeWidth = 2.2f
        stroke.shader = LinearGradient(cx - 54, cy - 54, cx + 54, cy + 54, argb(0xF2F2F2), argb(0x7A7A7A), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, (53.5 * s).toFloat(), stroke)
        stroke.shader = null
        stroke.strokeWidth = 9f
        stroke.color = argb(0x141414)
        canvas.drawCircle(cx, cy, (59 * s).toFloat(), stroke)
        stroke.strokeWidth = 0.8f
        stroke.shader = LinearGradient(cx, cy - 64, cx, cy + 64, argb(0x5A5A5A), argb(0x1A1A1A), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, (63.6 * s).toFloat(), stroke)
        stroke.shader = null
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
