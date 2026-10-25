package com.ngocdevv.nativecook.blocks.media.slideprojectorgallery

import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/*
 Slide Projector Gallery — a photo-story screen ("Carousel Night — Summer '87"). A port of
 SlideProjectorGallery.tsx (the reference) and SlideProjectorGallery.swift; the numbers are the
 same. A carousel slide projector at the bottom throws the current slide onto a dark living-room
 wall, through a visible beam with drifting dust. Changing slides: the picture goes dark, the lamp
 stutters, the round tray turns one slot, the next slide drops in and the focus pulls from blurry
 to sharp. The wall, the slide and the beam are one AGSL shader (SlideProjectorGalleryShader.kt;
 below API 33 SlideProjectorGalleryFallback.kt); the timeline is SlideProjectorGalleryMotion.kt
 (LOOP = 9 s).

 Touch: swipe left / right (or the chevrons) for the next / previous slide, with the same change
 as the loop; past the first or last slide nothing changes (a reject haptic). Pinch to turn the
 focus ring: the spread from where the fingers landed defocuses either way, and a tick marks sharp
 focus. 2 s after the last touch the tray goes back to the first slide, focus sharp, and the loop
 restarts. Reduced motion: the first slide, sharp and still; a change is a plain dim-and-back.

 Per frame: the shader rect, 71 dust motes in four paths, the projector bitmap, two paths of
 tray mounts, the hub and the lens glow; the captions move in layers.
 */

private val SlideBg = Color(0xFF1A1411)
private val SlideInk = Color(0xFFF3E8DA)
private val SlideMute = Color(0xFFF3E8DA).copy(alpha = 0.56f)
private val SlideAccent = Color(0xFFF0B25E)
private const val HOLD_AFTER_TOUCH = 2f
private const val MOTES = 64
private const val BOKEH = 7
private const val ROW = 46f

private class SlideProjectorController(val auto: Boolean, val reduced: Boolean) {
    var frame by mutableFloatStateOf(0f)
    var wake by mutableIntStateOf(0)

    var clock = 0f
    var now = 0f
    /** 0 = autoPlay, 1 = the finger's slides, 2 = going back to the first slide. */
    var mode = 0
    var lastTouch = 0f
    var pinching = false
    var chFrom = 0
    var chTo = 0
    var chStart = -100f
    val userFocus = SlideProjectorAnim(0f)
    var pinchBase = 0f
    private var prevFocus = -1
    val pose = SlideProjectorPose()

    init { compute() }

    fun needsFrames() = auto || mode != 0 || userFocus.busy

    /** Loop time for the dust. */
    val dustT get() = if (reduced) 0f else if (mode == 0) clock else now % SlideProjectorMotion.LOOP

    private fun compute() {
        val p = pose
        if (mode == 0) {
            if (reduced) SlideProjectorMotion.rest(0, p) else SlideProjectorMotion.auto(clock, p)
        } else {
            val u = (now - chStart) / SlideProjectorMotion.CHANGE
            if (u >= 0f && u < 1f) SlideProjectorMotion.change(u, chFrom, chTo, p) else SlideProjectorMotion.rest(chTo, p)
            if (reduced && u >= 0f && u < 1f) {
                SlideProjectorMotion.rest(if (u < 0.5f) chFrom else chTo, p)
                p.bright = abs(1f - 2f * u)
            }
        }
        val uf = abs(userFocus.value)
        if (uf > p.focus) {
            p.focus = uf
            p.scale = 1f + 0.03f * min(1f, uf)
        }
    }

    fun tick(dt: Float, view: View) {
        now += dt
        when {
            mode == 0 -> if (auto) clock = (clock + dt) % SlideProjectorMotion.LOOP
            mode == 1 && !pinching && now - lastTouch > HOLD_AFTER_TOUCH && now - chStart > SlideProjectorMotion.CHANGE -> {
                mode = 2
                userFocus.tween(0f, 0.5f)
                if (chTo != 0) { chFrom = chTo; chTo = 0; chStart = now }
            }
            mode == 2 && now - chStart > SlideProjectorMotion.CHANGE && abs(userFocus.value) < 0.01f -> { clock = 0f; mode = 0 }
        }
        userFocus.step(dt)
        compute()
        val cur = if (pinching) (if (abs(userFocus.value) < 0.06f) 1 else 0) else -1
        if (cur == 1 && prevFocus == 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        prevFocus = cur
        frame = now
    }

    /** Take over from the loop where it is (on the nearest whole slide). */
    fun takeOver() {
        if (mode == 1) return
        val cur = if (mode == 0) round(pose.tray).toInt() else chTo
        chFrom = cur; chTo = cur; chStart = -100f; mode = 1
    }

    fun step(dir: Int, view: View) {
        takeOver()
        lastTouch = now
        if (now - chStart < SlideProjectorMotion.CHANGE * 0.6f) return
        val cur = chTo
        val to = (cur + dir).coerceIn(0, slideProjectorSlides.size - 1)
        if (to == cur) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            return
        }
        chFrom = cur; chTo = to; chStart = now
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun pinchBegin() {
        takeOver()
        pinching = true
        userFocus.cancel()
        pinchBase = userFocus.value
        lastTouch = now
    }

    fun pinchUpdate(scale: Float) {
        lastTouch = now
        userFocus.set((pinchBase + (scale - 1f) * 1.6f).coerceIn(-1f, 1f))
    }

    fun pinchEnd() { pinching = false; lastTouch = now }
}

private class SlideProjectorPainter(val L: SlideProjectorLayout, val art: SlideProjectorArt) {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) makeShader() else null
    private val fallback = if (shader == null) SlideProjectorFallback(L, art) else null
    private val paint = Paint()
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dust = Array(4) { Path() }
    private val dustPaint = arrayOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideArgb(255, 236, 200, 0.35f) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideArgb(255, 238, 206, 0.6f) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideArgb(255, 244, 222, 0.9f) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slideArgb(255, 236, 200, 0.14f)
            maskFilter = BlurMaskFilter(sigmaToRadius(2.4f), BlurMaskFilter.Blur.NORMAL)
        },
    )
    private val far = Path()
    private val near = Path()
    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideHex(0x7E7465) }
    private val nearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideHex(0xE8DCC5) }
    private val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(L.trayCx - L.trayRx * 0.12f, L.trayCy - L.trayRy * 0.2f, L.trayRx * 0.5f, intArrayOf(slideHex(0x5B5450), slideHex(0x2E2A28), slideHex(0x1C1918)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(L.lensX, L.lensY, 28f, intArrayOf(slideArgb(255, 240, 205, 0.85f), slideArgb(255, 214, 150, 0.25f), slideArgb(255, 200, 130, 0f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    private val m = FloatArray(4)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun makeShader(): RuntimeShader? = runCatching {
        RuntimeShader(SLIDE_PROJECTOR_AGSL).apply {
            val bs = BitmapShader(art.atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            bs.filterMode = BitmapShader.FILTER_MODE_LINEAR
            setInputShader("atlas", bs)
            setFloatUniform("size", L.W, L.H)
            setFloatUniform("proj", L.projCx, L.projTop, L.projW, L.projH)
            setFloatUniform("key", SLIDE_KEY_TOP, SLIDE_KEY_BOTTOM)
            setFloatUniform("lens", L.lensX, L.lensY)
            setFloatUniform("tile", SLIDE_TILE_W.toFloat(), SLIDE_TILE_H.toFloat())
        }
    }.getOrNull()

    fun draw(n: android.graphics.Canvas, c: SlideProjectorController) {
        val p = c.pose
        val s = shader
        if (s != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            s as RuntimeShader
            s.setFloatUniform("slide", p.slide.toFloat(), p.bright, p.drop, p.focus)
            s.setFloatUniform("look", p.flicker, p.scale, 0f, 0f)
            paint.shader = s
            n.drawRect(0f, 0f, L.W, L.H, paint)
        } else {
            fallback?.draw(n, p)
        }

        // Dust in the beam: one path per brightness band, plus a few soft out-of-focus motes.
        val lit = p.bright * p.flicker
        val half = L.projW * 0.5f * SLIDE_KEY_TOP
        for (d in dust) d.rewind()
        val t = c.dustT
        for (i in 0 until MOTES + BOKEH) {
            SlideProjectorMotion.mote(i, t, L.lensX, L.lensY, L.projTop + L.projH * 0.6f, half, m)
            val b = m[3] * lit
            if (b < 0.08f) continue
            if (i >= MOTES) dust[3].addCircle(m[0], m[1], 2.6f + m[2] * 1.4f, Path.Direction.CW)
            else dust[if (b > 0.6f) 2 else if (b > 0.3f) 1 else 0].addCircle(m[0], m[1], m[2], Path.Direction.CW)
        }
        for (k in 0 until 4) n.drawPath(dust[k], dustPaint[k])

        n.drawBitmap(art.body, null, android.graphics.RectF(0f, 0f, L.W, L.H), bmp)
        slideProjectorMounts(L, p.tray, SlideProjectorMotion.TRAY_SLOTS, far, near)
        n.drawPath(far, farPaint)
        n.drawOval(L.trayCx - L.trayRx * 0.42f, L.trayCy - L.trayRy * 0.42f, L.trayCx + L.trayRx * 0.42f, L.trayCy + L.trayRy * 0.42f, hub)
        n.drawPath(near, nearPaint)
        glow.alpha = (lit.coerceIn(0f, 1f) * 255f).toInt()
        n.drawCircle(L.lensX, L.lensY, 28f, glow)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlideProjectorGallery(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced

    BoxWithConstraints(modifier.fillMaxSize().background(SlideBg).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = max(
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value,
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding().value,
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val W = maxWidth.value
        val H = maxHeight.value
        val L = remember(W, H, top, bottom) { SlideProjectorLayout(W, H, top, bottom) }
        // The atlas does not depend on the layout: baked once, at once. The projector is keyed on
        // the layout, which follows the insets; the demo hides the status bar, so they animate for
        // a moment and the bake waits for them to settle instead of restarting on every frame.
        val atlas by produceState<android.graphics.Bitmap?>(null, context) {
            value = withContext(Dispatchers.Default) { slideProjectorAtlas(context, coroutineContext[Job]) }
        }
        val body by produceState<Pair<android.graphics.Bitmap, android.graphics.Bitmap?>?>(null, L) {
            delay(120)
            value = withContext(Dispatchers.Default) {
                slideProjectorBody(L, min(2f, density), needWall = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU, coroutineContext[Job])
            }
        }
        val art = remember(atlas, body) {
            val a = atlas
            val b = body
            if (a != null && b != null) SlideProjectorArt(a, b.first, b.second) else null
        }
        val c = remember(auto, reduced) { SlideProjectorController(auto, reduced) }
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
            val painter = remember(a) { SlideProjectorPainter(L, a) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "A slide projector showing summer photos on a wall. Swipe for the next slide, pinch to focus." }
                    .pointerInput(c) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            c.wake++
                            val vt = VelocityTracker()
                            vt.addPosition(down.uptimeMillis, down.position)
                            var pinch = false
                            var startDist = 1f
                            var active = false
                            var failed = false
                            var dx = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                if (!pinch && pressed.size >= 2) {
                                    pinch = true
                                    startDist = max(1f, (pressed[0].position - pressed[1].position).getDistance())
                                    c.pinchBegin()
                                }
                                if (pinch) {
                                    if (pressed.size >= 2) {
                                        c.pinchUpdate((pressed[0].position - pressed[1].position).getDistance() / startDist)
                                        pressed.forEach { it.consume() }
                                    } else if (c.pinching) {
                                        c.pinchEnd()
                                    }
                                } else {
                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                    if (ch != null) {
                                        vt.addPosition(ch.uptimeMillis, ch.position)
                                        dx = (ch.position.x - down.position.x) / density
                                        val dy = (ch.position.y - down.position.y) / density
                                        if (!active && !failed) {
                                            if (abs(dy) > 24f) failed = true else if (abs(dx) > 14f) active = true
                                        }
                                        if (active) ch.consume()
                                    }
                                }
                                if (pressed.isEmpty()) break
                            }
                            if (pinch) {
                                if (c.pinching) c.pinchEnd()
                            } else if (active && !failed) {
                                val vx = vt.calculateVelocity().x / density
                                if (abs(dx) > 44f || abs(vx) > 500f) c.step(if (dx < 0f) 1 else -1, view)
                            }
                        }
                    },
            ) {
                c.frame
                drawIntoCanvas { cv ->
                    val n = cv.nativeCanvas
                    n.save()
                    n.scale(density, density)
                    painter.draw(n, c)
                    n.restore()
                }
            }
        }

        Column(Modifier.offset(x = 24.dp, y = (top + 12f).dp).width((W - 48f).dp)) {
            BasicText("CAROUSEL NIGHT", style = TextStyle(color = SlideAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText("Summer '87", Modifier.padding(top = 4.dp), style = TextStyle(color = SlideInk, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("From Aunt Ivy's tray · 5 slides · 35 mm", Modifier.padding(top = 2.dp), style = TextStyle(color = SlideMute, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }

        Box(Modifier.offset(x = 24.dp, y = L.captionY.dp).width((W - 48f).dp).height(ROW.dp).clipToBounds()) {
            slideProjectorSlides.forEachIndexed { k, s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW.dp)
                        .graphicsLayer {
                            c.frame
                            val d = k - SlideProjectorMotion.roll(c.pose.tray)
                            alpha = max(0f, 1f - abs(d) * 1.8f)
                            translationY = d * ROW * density
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(s.title, style = TextStyle(color = SlideInk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("${k + 1} of 5 · ${s.meta}", style = TextStyle(color = SlideMute, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(
            Modifier.offset(x = 20.dp, y = (L.lensY - 34f).dp).width((W - 40f).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SlideProjectorRound(-1, "Previous slide") { c.wake++; c.step(-1, view) }
            SlideProjectorRound(1, "Next slide") { c.wake++; c.step(1, view) }
        }

        BasicText(
            "Swipe to change slides · pinch to focus",
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = (bottom + 12f).dp),
            style = TextStyle(color = SlideInk.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun SlideProjectorRound(dir: Int, label: String, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(130), label = "round")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(SlideInk.copy(alpha = 0.08f), CircleShape)
            .border(0.5.dp, SlideInk.copy(alpha = 0.22f), CircleShape)
            .semantics { role = Role.Button; contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val k = size.width / 18f
            val path = androidx.compose.ui.graphics.Path().apply {
                if (dir < 0) { moveTo(11.5f * k, 3.5f * k); lineTo(6f * k, 9f * k); lineTo(11.5f * k, 14.5f * k) }
                else { moveTo(6.5f * k, 3.5f * k); lineTo(12f * k, 9f * k); lineTo(6.5f * k, 14.5f * k) }
            }
            drawPath(path, SlideInk, style = Stroke(width = 2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
