package com.ngocdevv.nativecook.blocks.cards.filmnegativecard

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** The insets animate for a moment while the demo hides the status bar: bakes keyed on them wait this long to settle. */
private const val SETTLE_MS = 120L

/*
 Film Negative Card — a photo-lab screen ("Darkroom Club — Roll 12, frame 7") with a strip of
 35 mm colour negative lying on a dark desk above a light table. A port of FilmNegativeCard.tsx
 (the reference) and FilmNegativeCard.swift; the numbers are the same. Where the strip overlaps
 the lit panel the negative inverts into the positive photo (one AGSL shader,
 FilmNegativeCardShader.kt; below API 33 FilmNegativeCardFallback.kt), the light's soft edge
 sweeping across the frame; a loupe drifts over the face while it rests there. Timeline in
 FilmNegativeCardMotion.kt (LOOP = 8 s).

 Touch: drag the strip (vertical follows the finger, rubber-banded past the desk and the table;
 sideways it rubber-bands back). Release: a spring (0.45 s, damping ratio 0.78, carrying the
 finger's velocity) to the light or back to its resting place, whichever the projected position
 (0.12 s of velocity) is nearer. Landing on the light brings the loupe in after 0.28 s. Haptics:
 a tick when the strip passes halfway, a light tap when it lands on the light. 2 s after the last
 touch it eases back (0.9 s) and the loop starts again at t = 0.
 Reduced motion: the strip rests on the light showing the positive; no loop, no dragging.

 Per frame: one Canvas draws two pre-blurred shadow bitmaps, the strip's shader rect, twelve edge
 labels and the loupe ring; the desk and the dimmed roll behind are a separate, cached layer.
 */

private val FilmBg = Color(0xFF16110F)
private val FilmInk = Color(0xFFF4EADB)
private val FilmMute = Color(0xFFF4EADB).copy(alpha = 0.58f)
private val FilmAccent = Color(0xFFF2A65A)
private const val LIFT_SCALE = 0.018f
private const val HOLD_AFTER_TOUCH = 2f
private const val ROLL_H = 16f

private class FilmNegativeController(val L: FilmNegativeLayout, val auto: Boolean, val reduced: Boolean) {
    /** The one Compose state the frame loop writes: read only in draw and layer lambdas. */
    var frame by mutableFloatStateOf(0f)
    var wake by mutableIntStateOf(0)

    var clock = 0f
    var now = 0f
    /** 0 = autoPlay timeline, 1 = the finger's (or its spring's), 2 = easing back to rest. */
    var mode = 0
    var touching = false
    var lastTouch = 0f
    val manY = FilmNegativeAnim(0f, 0.01f)
    val manX = FilmNegativeAnim(0f, 0.01f)
    val manLift = FilmNegativeAnim(0f)
    val manLoupe = FilmNegativeAnim(0f)
    var grabY = 0f
    var grabX = 0f
    private var prevHalf = -1
    private val pose = FloatArray(5)

    /** [strip top, x offset, lift, loupe opacity, loupe dx, loupe dy] */
    val v = FloatArray(6)

    init { compute() }

    val onAmount get() = FilmNegativeMotion.clamp01((v[0] - L.restY) / max(1f, L.onY - L.restY))
    val scale get() = 1f + LIFT_SCALE * v[2]
    val tx get() = L.stripX + v[1] + (L.stripW / 2f) * (1f - scale)
    val ty get() = v[0] + (L.stripH / 2f) * (1f - scale)

    fun needsFrames() = auto || mode != 0 || manY.busy || manX.busy || manLift.busy || manLoupe.busy

    private fun compute() {
        if (mode == 0) {
            FilmNegativeMotion.pose(if (reduced) 3.02f else clock, pose)
            v[0] = L.restY + pose[0] * (L.onY - L.restY)
            v[1] = 0f; v[2] = pose[1]; v[3] = pose[2]; v[4] = pose[3]; v[5] = pose[4]
        } else {
            v[0] = manY.value; v[1] = manX.value; v[2] = manLift.value; v[3] = manLoupe.value; v[4] = 0f; v[5] = 0f
        }
    }

    fun tick(dt: Float, view: View) {
        now += dt
        if (mode == 0) {
            if (auto) clock = (clock + dt) % FilmNegativeMotion.LOOP
        } else if (mode == 1 && !touching && now - lastTouch > HOLD_AFTER_TOUCH) {
            mode = 2
            val ease = FilmNegativeMotion.bezier(0.65f, 0f, 0.35f, 1f)
            manX.tween(0f, 0.9f, ease)
            manLoupe.tween(0f, 0.25f)
            manY.tween(L.homeY, 0.9f, ease) { clock = 0f; mode = 0 }
        }
        manY.step(dt); manX.step(dt); manLift.step(dt); manLoupe.step(dt)
        compute()
        val cur = if (mode == 1 && touching) (if (onAmount > (1f + FilmNegativeMotion.REST_ON) / 2f) 1 else 0) else -1
        if (prevHalf != -1 && cur != -1 && cur != prevHalf) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        prevHalf = cur
        frame = now
    }

    fun hit(y: Float) = y > v[0] - 16f && y < v[0] + L.stripH + 16f

    fun begin() {
        touching = true
        lastTouch = now
        manY.cancel(); manX.cancel()
        if (mode != 1) {
            manY.set(v[0]); manX.set(v[1]); manLoupe.set(v[3]); manLift.set(v[2])
            mode = 1
        }
        grabY = manY.value
        grabX = manX.value
        manLift.spring(1f, 0.35f, 0.8f)
        manLoupe.tween(0f, 0.16f)
        compute()
    }

    fun move(dx: Float, dy: Float) {
        lastTouch = now
        manY.set(FilmNegativeMotion.rubber(grabY + dy, L.restY - 4f, L.onY + 4f))
        manX.set(FilmNegativeMotion.rubber(grabX + dx, 0f, 0f))
        compute()
    }

    fun end(vx: Float, vy: Float, view: View) {
        touching = false
        lastTouch = now
        val projected = manY.value + vy * 0.12f
        val toLight = projected > (L.homeY + L.onY) / 2f
        manY.spring(if (toLight) L.onY else L.homeY, 0.45f, 0.78f, vy)
        manX.spring(0f, 0.4f, 0.8f, vx)
        manLift.spring(0f, 0.45f, 0.85f)
        if (toLight) {
            manLoupe.tween(1f, 0.32f, FilmNegativeMotion.bezier(0.23f, 1f, 0.32f, 1f), after = 0.28f)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

/** The strip's shader (API 33+) or its canvas fallback, plus the edge print. */
private class FilmNegativeStripPainter(private val L: FilmNegativeLayout, private val art: FilmNegativeArt, roll: Boolean) {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) makeShader(roll) else null
    private val fallback = if (shader == null) FilmNegativeFallback(L, art.photo) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 7.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        color = filmNegativeArgb(58, 24, 8, 0.8f)
    }
    private val lit = Paint(dark).apply { color = filmNegativeArgb(255, 226, 182, 0.82f) }

    /** Edge print: x, y and text per label; the roll behind counts four frames lower. */
    private val labels: List<Triple<Float, Float, String>> = buildList {
        val yTop = L.perfTop - L.perfH / 2f - 3.5f
        val yBot = L.perfBottom + L.perfH / 2f + 9f
        val shift = if (roll) 4 else 0
        for (i in -1..1) {
            val x0 = L.frame.left + i * (L.frame.width() + L.gap)
            add(Triple(x0 + 6f, yTop, "LUMA 400"))
            add(Triple(x0 + L.frame.width() * 0.64f, yTop, "${7 + i - shift}"))
            add(Triple(x0 + L.frame.width() * 0.12f, yBot, "${7 + i - shift}"))
            add(Triple(x0 + L.frame.width() * 0.58f, yBot, "${7 + i - shift}A"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun makeShader(roll: Boolean): RuntimeShader? = runCatching {
        RuntimeShader(FILM_NEGATIVE_AGSL).apply {
            val bs = BitmapShader(art.photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            bs.filterMode = BitmapShader.FILTER_MODE_LINEAR
            setInputShader("photo", bs)
            setFloatUniform("imgSize", art.photo.width.toFloat(), art.photo.height.toFloat())
            setFloatUniform("strip", 0f, 0f, L.stripW, L.stripH)
            setFloatUniform("frame", L.frame.left, L.frame.top, L.frame.width(), L.frame.height())
            setFloatUniform("gap", L.gap)
            setFloatUniform("perf", L.perfPitch, L.perfW, L.perfH, L.perfPhase)
            setFloatUniform("rows", L.perfTop, L.perfBottom)
            setFloatUniform("roll", if (roll) 1f else 0f)
            setFloatUniform("light", -9999f, -9999f, 1f, 1f)
            setFloatUniform("loupe", 0f, 0f, 1f, 0f)
            setFloatUniform("sheen", L.restY - 80f)
        }
    }.getOrNull()

    /** Draws the strip in strip space. */
    fun draw(c: android.graphics.Canvas, light: RectF, lx: Float, ly: Float, lr: Float, la: Float, sheen: Float, roll: Boolean) {
        val s = shader
        if (s != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            s as RuntimeShader
            if (!roll) {
                s.setFloatUniform("light", light.left, light.top, light.width(), light.height())
                s.setFloatUniform("loupe", lx, ly, lr, la)
                s.setFloatUniform("sheen", sheen)
            }
            paint.shader = s
            c.drawRect(0f, 0f, L.stripW, L.stripH, paint)
        } else {
            fallback?.draw(c, light, lx, ly, lr, la, roll)
        }
        for (l in labels) c.drawText(l.third, l.first, l.second, dark)
        if (!roll) {
            c.save()
            c.clipRect(light)
            for (l in labels) c.drawText(l.third, l.first, l.second, lit)
            c.restore()
        }
    }
}

private val filmBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

private fun drawSoft(c: android.graphics.Canvas, s: FilmNegativeSoft, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    filmBitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    val kx = w / s.w
    val ky = h / s.h
    c.drawBitmap(s.bitmap, null, RectF(x - s.pad * kx, y - s.pad * ky, x + w + s.pad * kx, y + h + s.pad * ky), filmBitmapPaint)
    filmBitmapPaint.alpha = 255
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmNegativeCard(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced

    BoxWithConstraints(modifier.fillMaxSize().background(FilmBg).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = max(
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value,
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding().value,
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val W = maxWidth.value
        val H = maxHeight.value
        val L = remember(W, H, top, bottom) { FilmNegativeLayout(W, H, top, bottom) }
        val art by produceState<FilmNegativeArt?>(null, L) {
            delay(SETTLE_MS)
            value = withContext(Dispatchers.Default) { filmNegativeBake(context, L, min(3f, density)) }
        }
        val c = remember(L, auto, reduced) { FilmNegativeController(L, auto, reduced) }
        val ready = art != null

        LaunchedEffect(c, ready, c.wake) {
            if (!ready) return@LaunchedEffect
            var last = -1L
            do {
                withFrameNanos { n ->
                    val dt = if (last < 0) 0f else min(0.05f, (n - last) / 1e9f)
                    last = n
                    c.tick(dt, view)
                }
            } while (c.needsFrames())
        }

        val a = art
        if (a != null) {
            val rollPainter = remember(a) { FilmNegativeStripPainter(L, a, roll = true) }
            val stripPainter = remember(a) { FilmNegativeStripPainter(L, a, roll = false) }
            // The desk and the rest of the roll: static, drawn once into a cached layer.
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawIntoCanvas { cv ->
                    val n = cv.nativeCanvas
                    n.save()
                    n.scale(density, density)
                    n.drawBitmap(a.desk, null, RectF(0f, 0f, L.W, L.H), filmBitmapPaint)
                    n.translate(L.stripX + 6f, L.restY - 10f)
                    n.rotate(Math.toDegrees(-0.022).toFloat())
                    n.scale(0.95f, 0.95f)
                    n.saveLayerAlpha(-60f, -60f, L.stripW + 60f, L.stripH + 60f, (0.62f * 255).toInt())
                    drawSoft(n, a.rollShadow, 4f, 10f, L.stripW - 8f, L.stripH, 0.5f)
                    rollPainter.draw(n, RectF(-9999f, -9999f, -9998f, -9998f), 0f, 0f, 1f, 0f, L.restY - 80f, roll = true)
                    n.restore()
                    n.restore()
                }
            }
            val ring = remember { FilmNegativeRing() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "A strip of colour negative film. Drag it onto the light table to see it as a positive photo." }
                    .pointerInput(c) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (reduced || !c.hit(down.position.y / density)) return@awaitEachGesture
                            c.begin()
                            c.wake++
                            val vt = VelocityTracker()
                            vt.addPosition(down.uptimeMillis, down.position)
                            val start = down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                vt.addPosition(ch.uptimeMillis, ch.position)
                                if (!ch.pressed) break
                                ch.consume()
                                c.move((ch.position.x - start.x) / density, (ch.position.y - start.y) / density)
                            }
                            val vel = vt.calculateVelocity()
                            c.end(vel.x / density, vel.y / density, view)
                        }
                    },
            ) {
                c.frame
                drawFilm(c, L, a, stripPainter, ring, density)
            }
        }

        // Header.
        Column(Modifier.offset(x = 24.dp, y = (top + 12f).dp).width((W - 48f).dp)) {
            BasicText("DARKROOM CLUB", style = TextStyle(color = FilmAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp))
            BasicText("Roll 12, frame 7", Modifier.padding(top = 4.dp), style = TextStyle(color = FilmInk, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("LUMA 400 · 36 exposures · developed Oct 14", Modifier.padding(top = 2.dp), style = TextStyle(color = FilmMute, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .height(24.dp)
                        .background(FilmAccent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, FilmAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(6.dp).background(FilmAccent, CircleShape))
                    Box(Modifier.height(ROLL_H.dp).clipToBounds()) {
                        // Unbounded, or the column is measured to one row and POSITIVE gets no height.
                        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer {
                            c.frame
                            translationY = -ROLL_H * density * FilmNegativeMotion.smoothstep(0.66f, 0.86f, c.onAmount)
                        }) {
                            val chip = TextStyle(color = FilmInk, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                            BasicText("NEGATIVE", Modifier.height(ROLL_H.dp), style = chip)
                            BasicText("POSITIVE", Modifier.height(ROLL_H.dp), style = chip)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                BasicText("f/2.8 · 1/250 s · 50 mm", style = TextStyle(color = FilmMute, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
            }
        }

        // Footer: the price and the order button.
        Row(
            Modifier.offset(x = 24.dp, y = L.footerY.dp).width((W - 44f).dp).height(FILM_FOOTER_H.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("Lustre print, 4 × 6 in", style = TextStyle(color = FilmMute, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                BasicText("$0.45 · ready Thursday", style = TextStyle(color = FilmInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
            }
            FilmNegativeCta(view)
        }
    }
}

@Composable
private fun FilmNegativeCta(view: View) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(130), label = "cta")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .cssBoxShadow(Color(0f, 0f, 0f, 0.35f), 1.dp, 2.dp, 24.dp)
            .cssBoxShadow(Color(242 / 255f, 166 / 255f, 90 / 255f, 0.22f), 10.dp, 24.dp, 24.dp)
            .height(48.dp)
            .background(FilmAccent, RoundedCornerShape(24.dp))
            .drawWithContent {
                drawContent()
                // inset 0 1px 0 rgba(255,255,255,0.35)
                val r = 24.dp.toPx()
                clipPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }) {
                    drawRect(Color.White.copy(alpha = 0.35f), Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                }
            }
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    tryAwaitRelease()
                    pressed = false
                })
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Order print", style = TextStyle(color = Color(0xFF2B1608), fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

/** The loupe's chrome ring. */
private class FilmNegativeRing {
    val chrome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        shader = SweepGradient(
            0f, 0f,
            intArrayOf(filmNegativeHex(0x8C8782), filmNegativeHex(0xF3F0EA), filmNegativeHex(0x6A6662), filmNegativeHex(0xD9D4CC), filmNegativeHex(0x7D7873), filmNegativeHex(0xEDE9E2), filmNegativeHex(0x8C8782)),
            null,
        )
    }
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = filmNegativeArgb(20, 14, 10, 0.6f) }
    val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = filmNegativeArgb(255, 250, 240, 0.35f) }
}

private fun DrawScope.drawFilm(c: FilmNegativeController, L: FilmNegativeLayout, a: FilmNegativeArt, strip: FilmNegativeStripPainter, ring: FilmNegativeRing, density: Float) {
    drawIntoCanvas { cv ->
        val n = cv.nativeCanvas
        val v = c.v
        n.save()
        n.scale(density, density)
        // Shadows on the desk / table: tight contact + soft ambient that grows with lift.
        val grow = 6f * v[2]
        drawSoft(n, a.ambient, L.stripX + v[1] - grow / 2f, v[0] + 8f + 14f * v[2], L.stripW + grow, L.stripH, 0.5f - 0.18f * v[2] - 0.2f * c.onAmount)
        drawSoft(n, a.contact, L.stripX + v[1] + 2f, v[0] + 1.5f + 3f * v[2], L.stripW - 4f, L.stripH, 0.75f * (1f - FilmNegativeMotion.clamp01(v[2])))

        val s = c.scale
        val tx = c.tx
        val ty = c.ty
        val P = L.panel
        val light = RectF((P.left - tx) / s, (P.top - ty) / s, (P.left - tx) / s + P.width() / s, (P.top - ty) / s + P.height() / s)
        val F = L.frame
        val lx = F.left + F.width() * 0.66f + v[4]
        val ly = F.top + F.height() * 0.4f + v[5]
        val lr = F.height() * 0.3f
        n.save()
        n.translate(tx, ty)
        n.scale(s, s)
        strip.draw(n, light, lx, ly, lr, v[3], v[0], roll = false)
        n.restore()

        // The loupe's chrome ring, in screen space.
        val la = v[3]
        if (la > 0.001f) {
            val cx = tx + lx * s
            val cy = ty + ly * s
            val r = lr * s * (0.94f + 0.06f * la)
            val alpha = (la * 255f).toInt().coerceIn(0, 255)
            n.saveLayerAlpha(cx - r - 40f, cy - r - 40f, cx + r + 40f, cy + r + 48f, alpha)
            drawSoft(n, a.ringShadow, cx - (r + 4f), cy + 7f - (r + 4f), (r + 4f) * 2f, (r + 4f) * 2f, 0.42f)
            n.save()
            n.translate(cx, cy)
            n.drawCircle(0f, 0f, r + 4f, ring.chrome)
            n.restore()
            n.drawCircle(cx, cy, r - 0.5f, ring.inner)
            n.drawCircle(cx, cy, r + 8.4f, ring.outer)
            n.restore()
        }
        n.restore()
    }
}
