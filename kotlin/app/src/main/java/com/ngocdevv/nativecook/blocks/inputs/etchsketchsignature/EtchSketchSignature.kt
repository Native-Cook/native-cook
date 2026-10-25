package com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.LOOP
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.SWEEP_END
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.clamp01
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.easeInOut
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.etchAt
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.homeEase
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.seg
import com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature.EtchSketchSignatureMotion.shake
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The insets animate for a moment while the demo hides the status bar: bakes keyed on them wait this long to settle. */
private const val SETTLE_MS = 120L

/*
 Etch Sketch Signature — a proof-of-delivery screen whose signature pad is a red etch-a-sketch.
 A port of the React Native block (EtchSketchSignature.tsx); the numbers are the same.

 autoPlay (LOOP = 10 s, EtchSketchSignatureMotion.kt): rests on the signed "J" 0 – 1.2 s; the toy
 shakes 1.2 – 2.6 s while the powder resettles from the top (1.4 – 2.4 s) and wipes the line and
 the stylus is wound home; the pen draws the signature again 2.9 – 8.6 s; rest to 10 s.

 Touch: drag up and down on the left knob to move the stylus across, on the right knob up and
 down; one finger on the screen sets a target the stylus chases; two fingers moved 24 dp, or
 Clear, shake it clear over 1.4 s. 2 s after the last touch the line is shaken off and the stylus
 wound home (0.9 s), then the loop picks up where the screen is clear. Reduced motion: the loop
 does not play and the signed pad rests; the toy still works.

 Drawing: the frame, bezel, label and knob bodies are baked once into a bitmap off the main
 thread; per frame only the powder screen (one AGSL shader), the line (a polyline stroked twice
 with an AGSL shader), the stylus and the knobs' ridges are drawn, under one wobble transform.
 Below API 33 the screen is the shader evaluated once on the CPU at rest, the sweep band a
 gradient and the line's ragged erase a clip path.
 */

/** Seconds after the last touch before the loop starts again. */
private const val IDLE = 2f
/** Stylus travel per dp of finger travel on a knob. */
private const val KNOB_GAIN = 0.9f
/** A user clear: wobble and sweep, seconds. */
private const val CLEAR_LEN = 1.4f
private const val MAX_PTS = 6000

private class EtchController(val g: EtchGeo, val looping: Boolean) {
    var clock by mutableFloatStateOf(0f)
    var user by mutableStateOf(false)
    var sx by mutableFloatStateOf(0f)
    var sy by mutableFloatStateOf(0f)
    /** A clear in progress, 0…1, and how long it takes. */
    var clear by mutableFloatStateOf(0f)
    var clearing by mutableStateOf(false)
    var clearLen = CLEAR_LEN
    /** The finger's line (x, y interleaved); [count] floats are used. */
    val pts = FloatArray(MAX_PTS + 4)
    var count by mutableIntStateOf(0)
    /** Bumped on every touch so the frame loop starts again. */
    var wake by mutableIntStateOf(0)

    // Read only by the frame loop and the gestures.
    var active = false
    var idle = 0f
    var following = false
    var tx = 0f
    var ty = 0f
    var returning = false
    var retT = 0f
    var retX = 0f
    var retY = 0f

    fun etch(): EtchSketchSignatureMotion.Etch {
        if (!user) return etchAt(clock, g.sig, g.sy, g.sh)
        val c = clear
        val sw = easeInOut(seg(c, 0.12f, 0.85f))
        val sweeping = clearing
        return EtchSketchSignatureMotion.Etch(
            drawn = 0f,
            sx = sx,
            sy = sy,
            sweep = if (sweeping) g.sy - 30 + (g.sh + 60) * sw else -1e4f,
            band = if (sweeping) sin(PI.toFloat() * sw) else 0f,
            wobble = if (sweeping) shake(c * CLEAR_LEN, CLEAR_LEN) else 0f,
            signed = if (count > 8 && !sweeping) 1f else 0f,
        )
    }

    private fun push(x: Float, y: Float) {
        if (count >= MAX_PTS) return
        pts[count] = x
        pts[count + 1] = y
        count += 2
    }

    /** Moves the stylus, scraping a line behind it. */
    fun scrape(x: Float, y: Float) {
        val nx = min(g.sx + g.sw - 6, max(g.sx + 6, x))
        val ny = min(g.sy + g.sh - 6, max(g.sy + 6, y))
        val px = sx
        val py = sy
        if (hypot(nx - px, ny - py) < 0.8f) return
        sx = nx
        sy = ny
        if (clearing) return
        if (count == 0) push(px, py)
        push(nx, ny)
    }

    /** The finger takes over: what the loop has drawn becomes the finger's own line. */
    fun takeOver() {
        idle = 0f
        returning = false
        wake += 1
        if (user) return
        val e = etchAt(clock, g.sig, g.sy, g.sh)
        count = 0
        if (e.sweep < -1000 && e.drawn > 0) {
            val s = g.sig
            for (k in 0 until s.count) {
                if (s.run[k] > e.drawn) break
                push(s.pts[2 * k], s.pts[2 * k + 1])
            }
            push(e.sx, e.sy)
        }
        sx = e.sx
        sy = e.sy
        clear = 0f
        clearing = false
        user = true
    }

    /** Shake: the toy wobbles and the powder resettles from the top, wiping the line. */
    fun clearAll(seconds: Float) {
        if (clearing) return
        clearing = true
        clear = 0f
        clearLen = seconds
    }

    fun step(dt: Float) {
        if (!user) {
            if (looping) clock = (clock + dt) % LOOP
            return
        }
        if (following) {
            // On the screen the stylus chases the finger, as fast as two quick hands could turn the knobs.
            val k = min(1f, dt * 16)
            scrape(sx + (tx - sx) * k, sy + (ty - sy) * k)
        }
        if (clearing) {
            clear += dt / clearLen
            if (clear >= 1f) {
                count = 0
                clearing = false
                clear = 0f
            }
        }
        if (returning) {
            retT += dt / 0.9f
            val s = homeEase(min(1f, retT))
            sx = retX + (g.restX - retX) * s
            sy = retY + (g.restY - retY) * s
            if (retT >= 1f) {
                // Back in the loop where the screen is clear and the pen about to set off.
                clock = SWEEP_END
                user = false
                returning = false
            }
        } else if (!active) {
            idle += dt
            if (idle > IDLE) {
                returning = true
                retT = 0f
                retX = sx
                retY = sy
                if (count > 0) clearAll(0.9f)
            }
        }
    }
}

/** Paths, paints and shaders reused by every frame. */
private class EtchPainter {
    val line = Path()
    val screenShader: RuntimeShader?
    val coreShader: RuntimeShader?
    val lipShader: RuntimeShader?
    val screenBrush: ShaderBrush?
    val coreBrush: ShaderBrush?
    val lipBrush: ShaderBrush?
    val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val screenPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    var screenFor: Any? = null
    val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        strokeCap = Paint.Cap.ROUND
        color = Color(120 / 255f, 110 / 255f, 95 / 255f, 0.45f).toArgb()
    }
    val ridges = etchKnobRidges(EtchGeo.KNOB_R)
    val rect = RectF()
    val clip = Path()

    init {
        val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        screenShader = if (ok) runCatching { RuntimeShader(ETCH_SCREEN_AGSL) }.getOrNull() else null
        coreShader = if (ok) runCatching { RuntimeShader(ETCH_LINE_AGSL) }.getOrNull() else null
        lipShader = if (ok) runCatching { RuntimeShader(ETCH_LINE_AGSL) }.getOrNull() else null
        screenBrush = screenShader?.let { ShaderBrush(it) }
        coreBrush = coreShader?.let { ShaderBrush(it) }
        lipBrush = lipShader?.let { ShaderBrush(it) }
    }
}

@Composable
fun EtchSketchSignature(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val looping = autoPlay && !reduced
    val painter = remember { EtchPainter() }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFECEAE5))) {
        val density = LocalDensity.current.density
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val g = remember(maxWidth, maxHeight, top, bottom) { EtchGeo(maxWidth.value, maxHeight.value, top, bottom) }
        val c = remember(g, looping) { EtchController(g, looping) }
        var art by remember(g, density) { mutableStateOf<EtchArt?>(null) }
        val needsScreen = painter.screenShader == null

        LaunchedEffect(g, density) {
            delay(SETTLE_MS)
            art = withContext(Dispatchers.Default) { etchBake(g, density, needsScreen) }
        }
        // One frame loop: the loop's clock, or the finger's state until the toy is back at rest.
        val wake = c.wake
        LaunchedEffect(c, art != null, wake) {
            if (art == null) return@LaunchedEffect
            var last = -1L
            while (c.user || c.looping) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else min((now - last) / 1e9f, 1f / 30)
                    last = now
                    c.step(dt)
                }
            }
        }

        fun feel() {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Signature pad. Draw with your finger, or turn the knobs." }
                .pointerInput(c, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x = down.position.x / density
                        val y = down.position.y / density
                        val onScreen = x >= g.sx && x <= g.sx + g.sw && y >= g.sy && y <= g.sy + g.sh
                        val knob = when {
                            abs(x - g.knobLX) <= 44 && abs(y - g.knobY) <= 44 -> 0
                            abs(x - g.knobRX) <= 44 && abs(y - g.knobY) <= 44 -> 1
                            else -> -1
                        }
                        if (!onScreen && knob < 0) return@awaitEachGesture
                        c.takeOver()
                        c.active = true
                        down.consume()
                        if (onScreen) {
                            c.tx = x
                            c.ty = y
                            c.following = true
                        } else {
                            c.following = false
                            feel()
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            c.idle = 0f
                            if (onScreen) {
                                if (pressed.size > 1) {
                                    // Two fingers shake the toy clear.
                                    c.following = false
                                    val first = event.changes.firstOrNull { it.id == down.id } ?: pressed[0]
                                    val moved = hypot(first.position.x - down.position.x, first.position.y - down.position.y) / density
                                    if (moved > 24 && !c.clearing) {
                                        c.clearAll(CLEAR_LEN)
                                        feel()
                                    }
                                } else {
                                    c.following = true
                                    c.tx = pressed[0].position.x / density
                                    c.ty = pressed[0].position.y / density
                                }
                            } else {
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: pressed[0]
                                val d = ch.positionChange()
                                val dx = d.x / density
                                val dy = d.y / density
                                if (knob == 0) c.scrape(c.sx - dy * KNOB_GAIN + dx * 0.3f, c.sy)
                                else c.scrape(c.sx, c.sy + dy * KNOB_GAIN - dx * 0.3f)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        c.following = false
                        c.active = false
                    }
                },
        ) {
            scale(density, density, Offset.Zero) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFFF4F3F0), Color(0xFFECEAE5), Color(0xFFE2DFD8)),
                        start = Offset.Zero, end = Offset(g.w * 0.3f, g.h),
                    ),
                    size = Size(g.w, g.h),
                )
                val a = art ?: return@scale
                val e = c.etch()
                val cx = g.fx + g.fw / 2
                val cy = g.fy + g.fh / 2
                withTransform({
                    translate(e.wobble * 140, 0f)
                    rotate(Math.toDegrees(e.wobble.toDouble()).toFloat(), Offset(cx, cy))
                }) {
                    drawToy(g, c, a, e, painter)
                }
            }
        }

        Texts(g, top)

        // Clear and Confirm delivery.
        Row(
            Modifier.offset(x = 16.dp, y = g.buttonsTop.dp).width((g.w - 32).dp).height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EtchButton(
                "Clear", EtchInk, 1f,
                Modifier
                    .background(Color(0x0F1F2328), RoundedCornerShape(26.dp))
                    .border(1.dp, Color(0x1F1F2328), RoundedCornerShape(26.dp)),
                alpha = { 1f },
            ) {
                c.takeOver()
                c.clearAll(CLEAR_LEN)
                feel()
            }
            EtchButton(
                "Confirm delivery", Color(0xFFF7F5F2), 2f,
                Modifier
                    .cssBoxShadow(Color(0x330A0C10), 10.dp, 22.dp, 26.dp)
                    .cssBoxShadow(Color(0x590A0C10), 1.dp, 2.dp, 26.dp)
                    .background(EtchInk, RoundedCornerShape(26.dp)),
                alpha = { 0.45f + 0.55f * clamp01(c.etch().signed) },
            ) {}
        }
    }
}

/** The toy in its own (unwobbled) coordinates: baked frame, powder screen, line, stylus, knob grips. */
private fun DrawScope.drawToy(g: EtchGeo, c: EtchController, a: EtchArt, e: EtchSketchSignatureMotion.Etch, p: EtchPainter) {
    drawIntoCanvas { canvas ->
        p.rect.set(g.artLeft, g.artTop, g.artLeft + g.artW, g.artTop + g.artH)
        canvas.nativeCanvas.drawBitmap(a.frame, null, p.rect, p.bitmapPaint)
    }

    // The powder screen.
    val shader = p.screenShader
    if (shader != null && p.screenBrush != null) {
        shader.setFloatUniform("rect", g.sx, g.sy, g.sw, g.sh)
        shader.setFloatUniform("tilt", e.wobble)
        shader.setFloatUniform("sweep", e.sweep)
        shader.setFloatUniform("band", e.band)
        drawRoundRect(p.screenBrush, Offset(g.sx, g.sy), Size(g.sw, g.sh), CornerRadius(14f))
    } else {
        val bmp = a.screen
        if (bmp != null) {
            if (p.screenFor !== bmp) {
                p.screenFor = bmp
                p.screenPaint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setScale(g.sw / bmp.width, g.sh / bmp.height); postTranslate(g.sx, g.sy) })
                }
            }
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(g.sx, g.sy, g.sx + g.sw, g.sy + g.sh, 14f, 14f, p.screenPaint) }
        }
        if (e.band > 0.001f) {
            // The resettling band: a soft bright stripe at the front.
            val stops = 9
            val colors = List(stops) { i ->
                val k = -2f + 4f * i / (stops - 1)
                Color.White.copy(alpha = (0.045f * e.band * exp(-k * k)).coerceIn(0f, 1f))
            }
            p.clip.reset()
            p.clip.addRoundRect(androidx.compose.ui.geometry.RoundRect(g.sx, g.sy, g.sx + g.sw, g.sy + g.sh, CornerRadius(14f)))
            clipPath(p.clip) {
                drawRect(
                    Brush.verticalGradient(colors, startY = e.sweep - 44, endY = e.sweep + 44),
                    topLeft = Offset(g.sx, e.sweep - 44), size = Size(g.sw, 88f), blendMode = BlendMode.Plus,
                )
            }
        }
    }

    // The line: the scripted signature up to its drawn length, or the finger's points.
    val path = p.line
    path.rewind()
    var any = false
    if (!c.user) {
        val s = g.sig
        val drawn = e.drawn
        if (drawn > 0.5f) {
            any = true
            path.moveTo(s.pts[0], s.pts[1])
            for (k in 1 until s.count) {
                if (s.run[k] <= drawn) {
                    path.lineTo(s.pts[2 * k], s.pts[2 * k + 1])
                    continue
                }
                val f = (drawn - s.run[k - 1]) / max(0.0001f, s.run[k] - s.run[k - 1])
                path.lineTo(s.pts[2 * k - 2] + (s.pts[2 * k] - s.pts[2 * k - 2]) * f, s.pts[2 * k - 1] + (s.pts[2 * k + 1] - s.pts[2 * k - 1]) * f)
                break
            }
        }
    } else {
        val n = c.count
        if (n >= 4) {
            any = true
            path.moveTo(c.pts[0], c.pts[1])
            var i = 2
            while (i < n) {
                path.lineTo(c.pts[i], c.pts[i + 1])
                i += 2
            }
        }
    }
    if (any) {
        val core = p.coreShader
        val lip = p.lipShader
        if (core != null && lip != null && p.coreBrush != null && p.lipBrush != null) {
            lip.setFloatUniform("sweep", e.sweep)
            lip.setFloatUniform("ink", 0.97f, 0.98f, 1f, 0.6f)
            core.setFloatUniform("sweep", e.sweep)
            core.setFloatUniform("ink", 0.16f, 0.17f, 0.19f, 1f)
            translate(0f, 1.3f) {
                drawPath(path, p.lipBrush, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawPath(path, p.coreBrush, style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val draw: DrawScope.() -> Unit = {
                translate(0f, 1.3f) {
                    drawPath(path, Color(0.97f, 0.98f, 1f, 0.55f), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(path, Color(0.16f, 0.17f, 0.19f, 0.91f), style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (e.sweep > -1000) {
                // The ragged front as a clip: the line is kept below it.
                p.clip.reset()
                p.clip.moveTo(g.sx - 4, g.sy + g.sh + 4)
                var x = g.sx - 4
                while (x <= g.sx + g.sw + 4) {
                    p.clip.lineTo(x, e.sweep + (etchHash(floor(x * 0.25f) + 3, 3f) - 0.5f) * 26)
                    x += 4
                }
                p.clip.lineTo(g.sx + g.sw + 4, g.sy + g.sh + 4)
                p.clip.close()
                clipPath(p.clip) { draw() }
            } else {
                draw()
            }
        }
    }

    // The stylus.
    drawCircle(Color(0xFF3A3D42), 2.4f, Offset(e.sx, e.sy))
    drawCircle(Color.White.copy(alpha = 0.5f), 1f, Offset(e.sx, e.sy))

    // The knobs' grips and dots turn: the left one with x, the right one with y.
    val r = EtchGeo.KNOB_R
    for (i in 0..1) {
        val kx = if (i == 0) g.knobLX else g.knobRX
        val deg = etchKnobDegrees(if (i == 0) e.sx - g.restX else e.sy - g.restY)
        rotate(deg, Offset(kx, g.knobY)) {
            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                n.save()
                n.translate(kx, g.knobY)
                n.drawLines(p.ridges, p.ridgePaint)
                n.restore()
            }
            drawCircle(Color(0xFFC9302A), 2.6f, Offset(kx, g.knobY - r + 13))
        }
    }
}

@Composable
private fun Texts(g: EtchGeo, top: Float) {
    Row(
        Modifier.offset(x = 20.dp, y = (top + 12).dp).width((g.w - 40).dp).height(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("PROOF OF DELIVERY", style = TextStyle(color = EtchMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
        BasicText("Step 2 of 2", style = TextStyle(color = EtchMute, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
    }
    Column(Modifier.offset(x = 20.dp, y = (top + 42).dp).width((g.w - 40).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            "Sign for parcel #A-2291",
            style = TextStyle(color = EtchInk, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            maxLines = 1,
        )
        BasicText("Dana Whitfield · 14 Larch Lane, Apt 3", style = TextStyle(color = EtchMute, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
    BasicText(
        "Turn the knobs or draw · two fingers shake it clear",
        Modifier.offset(x = 20.dp, y = (g.fy + g.fh + 14).dp).width((g.w - 40).dp),
        style = TextStyle(color = EtchMute, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
    )
}

@Composable
private fun RowScope.EtchButton(
    label: String,
    ink: Color,
    weight: Float,
    look: Modifier,
    alpha: () -> Float,
    action: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(action)
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .graphicsLayer {
                this.alpha = alpha()
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val s = if (pressed) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .then(look)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { currentAction(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { currentAction() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}
