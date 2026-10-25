package com.ngocdevv.nativecook.blocks.text.letterpressheadline

import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Letterpress Headline — a print studio's new edition, "Press & Paper". On thick cotton paper the
 headline "Made by hand." is printed one letter at a time: a metal type block (its face bearing
 the mirrored letter) comes down, its shadow tightening, presses the letter into the paper in
 vermilion ink and lifts away; then an ink roller sweeps the second line on, and the lamp swings
 so the debossed walls catch the light. The loop is in LetterpressHeadlineMotion.kt (LOOP = 9 s).

 Touch: press and hold the paper and the platen presses every letter at once: the blocks come
 down in 0.25 s, then the impression deepens with the hold (full at about 1.1 s) and stays when
 you let go. Dragging swings the lamp (sideways = round, up and down = higher and lower,
 rubber-banded, sprung back on release). 2 s after the last touch the impression fades (0.9 s)
 and the loop starts again. Reduced motion: the printed page, still.
 */

private val Ink = Color(0xFF2B2622)
private val Mute = Color(0xFF7B6F62)
private val Vermilion = Color(0xFFB4442F)
private val Rule = Color(43, 38, 34, 64)
internal val LpPaperColor = Color(0xFFEFE8DC)
private const val IDLE = 2f
private const val RESET = 0.9f
// Holding: the blocks reach the paper after BLOCK_DOWN s; the impression is full HOLD_FULL s later.
private const val BLOCK_DOWN = 0.25f
private const val HOLD_FULL = 0.9f

private val SPECS = listOf(
    "Paper" to "600 gsm cotton, deckled",
    "Ink" to "Vermilion 032, oil based",
    "Press" to "Albion No. 3, 1904",
)

// Seconds from a fixed origin, so a float keeps millisecond precision however long the device has run.
private val LpOrigin = System.nanoTime()

private fun nowSeconds() = (System.nanoTime() - LpOrigin) / 1e9f

private class LpEngine(val reduced: Boolean, val count: Int) {
    var clock = 0f
    var user = false
    var lastTouch = 0f
    var resetAt = -1f
    var holding = false
    var holdStart = 0f
    var contact = 0
    var grabAz = 0f
    var grabEl = 0f
    val base = FloatArray(LP_MAX_GLYPHS)
    val fade = LpTrack(1f)
    var platen = 0f
    val platenTrack = LpTrack(0f)
    val block = LpTrack(0f)
    var ink = 0f
    val inkAlpha = LpTrack(1f)
    val az = LpTrack(0f)
    val el = LpTrack(0f)
    var running = false
    /** The loop took the screen back after a touch (the button's label resets). */
    var backToLoop = false

    private val pose = LpPose()
    private var poseAt = Float.NaN
    private var poseClock = Float.NaN

    fun busy() = holding || user

    fun pose(now: Float): LpPose {
        if (now == poseAt && clock == poseClock) return pose
        poseAt = now
        poseClock = clock
        if (reduced) {
            pose.setPrinted(count)
            return pose
        }
        if (!user) {
            pose.setLoop(clock, count)
            return pose
        }
        val f = fade.value(now)
        val pl = if (holding) platen else platenTrack.value(now)
        val b = if (holding) lpEaseInOut(min(1f, (now - holdStart) / BLOCK_DOWN)) else block.value(now)
        for (i in 0 until LP_MAX_GLYPHS) {
            pose.depth[i] = if (i < count) max(base[i] * f, pl) else 0f
            pose.block[i] = if (i < count) b else 0f
            pose.falling[i] = holding
        }
        pose.roller = 0f
        pose.rollerAlpha = 0f
        pose.ink = ink
        pose.inkAlpha = inkAlpha.value(now) * f
        pose.light = LP_LIGHT_AZ + az.value(now)
        pose.elevation = LP_LIGHT_EL + el.value(now)
        return pose
    }

    private fun touched(now: Float) {
        lastTouch = now
        resetAt = -1f
        if (user) return
        val p = LpPose().apply { setLoop(clock, count) }
        p.depth.copyInto(base)
        fade.set(1f)
        platen = 0f
        platenTrack.set(0f)
        block.set(0f)
        ink = p.ink
        inkAlpha.set(p.inkAlpha)
        az.set(p.light - LP_LIGHT_AZ)
        el.set(p.elevation - LP_LIGHT_EL)
        user = true
    }

    /** One frame; returns a haptic to play: 0 none, 1 light (contact), 2 selection (full). */
    fun step(now: Float, dt: Float, auto: Boolean): Int {
        if (!user) {
            if (auto) clock = (clock + dt) % LOOP
            return 0
        }
        if (holding) {
            val held = now - holdStart
            if (held >= BLOCK_DOWN) {
                platen = max(platen, lpEaseOut((held - BLOCK_DOWN) / HOLD_FULL))
                if (contact == 0) {
                    contact = 1
                    return 1
                } else if (contact == 1 && held >= BLOCK_DOWN + HOLD_FULL) {
                    contact = 2
                    return 2
                }
            }
            return 0
        }
        if (resetAt < 0f && now - lastTouch > IDLE) {
            fade.timing(now, 0f, RESET, LpEaseInOutCurve)
            platenTrack.timing(now, 0f, RESET, LpEaseInOutCurve)
            inkAlpha.timing(now, 0f, RESET, LpEaseInOutCurve)
            az.timing(now, 0f, RESET, LpEaseInOutCurve)
            el.timing(now, 0f, RESET, LpEaseInOutCurve)
            resetAt = now + RESET + 0.05f
        } else if (resetAt > 0f && now >= resetAt) {
            resetAt = -1f
            clock = 0f
            user = false
            backToLoop = true
        }
        return 0
    }

    fun begin(now: Float) {
        touched(now)
        holding = true
        holdStart = now
        contact = 0
        grabAz = az.value(now)
        grabEl = el.value(now)
    }

    fun drag(now: Float, dx: Float, dy: Float) {
        lastTouch = now
        az.set(grabAz + lpRubber(dx, 160f) / 160f * 1.3f)
        el.set(max(-0.4f, min(0.3f, grabEl - lpRubber(dy, 200f) / 400f)))
    }

    fun end(now: Float, velocityX: Float) {
        if (holding) {
            val b = lpEaseInOut(min(1f, (now - holdStart) / BLOCK_DOWN))
            block.set(b)
            platenTrack.set(platen)
        }
        holding = false
        lastTouch = now
        block.timing(now, 0f, 0.26f, LpEaseOutCurve)
        az.spring(now, 0f, 0.7f, 0.7f, velocity = velocityX / 160f * 0.5f)
        el.spring(now, 0f, 0.7f, 0.8f)
    }
}

private class LpArt(val paper: ImageBitmap?, val mask: android.graphics.Bitmap?)

/**
 * A print studio's edition page whose headline is letterpressed one letter at a time: type
 * blocks come down, deboss the cotton in vermilion and lift away; an ink roller lays the second
 * line on, and the lamp swings across the deboss. Hold the paper to press every letter at once;
 * drag to move the lamp.
 *
 * @param autoPlay prints, inks, lights and clears the headline by itself, on a loop of [LOOP] s.
 */
@Composable
fun LetterpressHeadline(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density
    BoxWithConstraints(modifier.fillMaxSize().background(LpPaperColor)) {
        val layout = remember(maxWidth, maxHeight, top, bottom) { lpLayout(maxWidth.value, maxHeight.value, top, bottom) }
        LpScene(layout, autoPlay && !reduced, reduced)
    }
}

@Composable
private fun LpScene(layout: LpLayout, auto: Boolean, reduced: Boolean) {
    val density = LocalDensity.current
    val d = density.density
    val view = LocalView.current
    val scale = min(d, 3f)
    val count = min(layout.glyphs.size, LP_MAX_GLYPHS)
    val engine = remember(reduced, layout) { LpEngine(reduced, count) }
    val tick = remember { mutableFloatStateOf(nowSeconds()) }
    val wake = remember { mutableIntStateOf(0) }
    var reserved by remember { mutableStateOf(false) }
    val shaderOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val art by produceState<LpArt?>(null, layout, scale) {
        value = withContext(Dispatchers.Default) {
            LpArt(lpBakePaper(layout, min(scale, 2f))?.asImageBitmap(), if (shaderOk) lpBuildMask(layout, scale) else null)
        }
    }
    val press = remember(art) {
        val mask = art?.mask
        if (mask != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(LP_PRESS_AGSL).apply {
                    setInputShader("mask", BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    setFloatUniform("origin", layout.region.x, layout.region.y)
                    setFloatUniform("scale", scale)
                    setFloatUniform("boxes", layout.boxes)
                    setFloatUniform("count", count.toFloat())
                    setFloatUniform("split", layout.split)
                }
            }.getOrNull()
        } else {
            null
        }
    }
    val pressBrush = remember(press) { press?.let { ShaderBrush(it) } }
    val paints = remember(layout) { LpPaints(layout) }

    LaunchedEffect(engine, auto, wake.intValue) {
        engine.running = true
        var last = -1L
        try {
            while (true) {
                val keepGoing = withFrameNanos { nanos ->
                    val now = (nanos - LpOrigin) / 1e9f
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    when (engine.step(now, dt, auto)) {
                        1 -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        2 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    if (engine.backToLoop) {
                        engine.backToLoop = false
                        reserved = false
                    }
                    tick.floatValue = now
                    auto || engine.busy()
                }
                if (!keepGoing) break
            }
        } finally {
            engine.running = false
        }
    }

    fun wakeUp() {
        tick.floatValue = nowSeconds()
        if (!engine.running) wake.intValue += 1
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val p = engine.pose(tick.floatValue)
            art?.paper?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            scale(d, d, pivot = Offset.Zero) {
                val r = layout.region
                if (press != null && pressBrush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    press.setFloatUniform("depth", p.depth)
                    press.setFloatUniform("light", p.light)
                    press.setFloatUniform("elevation", p.elevation)
                    drawRect(pressBrush, Offset(r.x, r.y), Size(r.w, r.h))
                } else {
                    lpDrawPressFallback(layout, count, p, paints)
                }
                lpDrawSecondAndRoller(layout, p, paints)
                for (i in 0 until count) lpDrawTypeBlock(layout, i, p, paints)
            }
        }

        // The paper: hold to print, drag to move the light.
        Box(
            Modifier
                .fillMaxWidth()
                .height((layout.detailsTop - 8f).dp)
                .semantics { contentDescription = "Paper. Press and hold to print the headline; drag to move the light." }
                .pointerInput(engine) {
                    if (engine.reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        engine.begin(nowSeconds())
                        wakeUp()
                        val start = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            engine.drag(nowSeconds(), (change.position.x - start.x) / d, (change.position.y - start.y) / d)
                        }
                        engine.end(nowSeconds(), tracker.calculateVelocity().x / d)
                        wakeUp()
                    }
                },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).offset { IntOffset(0, (layout.chromeTop * d).roundToInt()) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Press & Paper", style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp))
            Box(
                Modifier
                    .height(26.dp)
                    .border((1f / d).dp, Color(43, 38, 34, 89), RoundedCornerShape(13.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Edition 14", style = TextStyle(color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
        }
        BasicText(
            "NEW EDITION · AUTUMN 2027",
            Modifier.padding(start = 28.dp).offset { IntOffset(0, (layout.kickerTop * d).roundToInt()) },
            style = TextStyle(color = Vermilion, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp, fontFeatureSettings = "tnum"),
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).offset { IntOffset(0, (layout.specsTop * d).roundToInt()) }) {
            Box(Modifier.fillMaxWidth().height((1f / d).dp).background(Rule))
            for ((k, v) in SPECS) {
                Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(k, style = TextStyle(color = Mute, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp))
                    BasicText(v, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                }
                Box(Modifier.fillMaxWidth().height((1f / d).dp).background(Rule))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).offset { IntOffset(0, (layout.detailsTop * d).roundToInt()) },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                "12 prints · 300 × 400 mm",
                Modifier.alignByBaseline(),
                style = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
            BasicText("$180", Modifier.alignByBaseline(), style = TextStyle(color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            Modifier
                .padding(horizontal = 28.dp)
                .offset { IntOffset(0, (layout.buttonTop * d).roundToInt()) }
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    val s = if (pressed) 0.97f else 1f
                    scaleX = s
                    scaleY = s
                }
                .cssBoxShadow(Color(43, 30, 20, 77), 1.dp, 2.dp, 28.dp)
                .cssBoxShadow(Color(43, 30, 20, 46), 10.dp, 24.dp, 28.dp)
                .background(Color(0xFF2B2622), RoundedCornerShape(28.dp))
                .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(255, 255, 255, 31), 0.08f to Color.Transparent), RoundedCornerShape(28.dp))
                .clickable(interactionSource = interaction, indication = null) {
                    reserved = true
                    val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
                    view.performHapticFeedback(kind)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (reserved) "Print no. 7 reserved" else "Print edition",
                style = TextStyle(color = Color(0xFFF6EFE3), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            )
        }
        BasicText(
            "Hold the paper to print · drag to move the light",
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset { IntOffset(0, (layout.hintTop * d).roundToInt()) },
            style = TextStyle(color = Mute.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

/** Paints and fixed geometry of the moving parts, made once per layout. Coordinates are dp. */
private class LpPaints(l: LpLayout) {
    val headline = lpHeadlinePaint(l.size)
    val second = lpSecondPaint(l.secondSize).apply { color = 0xFF2A3140.toInt() }
    val rollFrom = l.secondX - 22f
    val rollTo = l.secondX + l.secondWidth + 22f
    val lineTop = l.secondBaselines[0] - l.secondSize - 10f
    val lineBottom = l.secondBaselines[1] + 10f
    val rollH = lineBottom - lineTop + 8f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val rollerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(89, 30, 20, 10)
        maskFilter = BlurMaskFilter(lpSigma(6f), BlurMaskFilter.Blur.NORMAL)
    }
    val grip = LinearGradient(-6f, 0f, 6f, 0f, intArrayOf(0xFF6B4426.toInt(), 0xFFA8754A.toInt(), 0xFF5A3A21.toInt()), null, Shader.TileMode.CLAMP)
    val rubber = LinearGradient(
        -13f, 0f, 13f, 0f,
        intArrayOf(0xFF17181C.toInt(), 0xFF3F434C.toInt(), 0xFF2A3140.toInt(), 0xFF121316.toInt()),
        floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP,
    )
    // Each letter's block: its rect and shaders, made once.
    val blockShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(107, 40, 26, 14) }
    val blurs = HashMap<Int, BlurMaskFilter>()
    val steel = l.glyphs.take(LP_MAX_GLYPHS).map { g ->
        val x = g.x - 4f
        val y = g.baseline - l.size * 0.82f
        LinearGradient(x, y, x + g.w + 8f, y + l.size * 1.08f, intArrayOf(0xFF6B6F78.toInt(), 0xFF43464D.toInt(), 0xFF2B2D32.toInt(), 0xFF3A3D43.toInt()), null, Shader.TileMode.CLAMP)
    }
    val bevel = l.glyphs.take(LP_MAX_GLYPHS).map { g ->
        val x = g.x - 4f
        val y = g.baseline - l.size * 0.82f
        LinearGradient(
            x, y, x + g.w + 8f, y + l.size * 1.08f,
            intArrayOf(android.graphics.Color.argb(89, 255, 255, 255), android.graphics.Color.argb(10, 255, 255, 255), android.graphics.Color.argb(102, 0, 0, 0)),
            null, Shader.TileMode.CLAMP,
        )
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }

    /** A blur for a sigma, quantised to 0.25 dp so a moving block reuses a few filters. */
    fun blur(sigma: Float): BlurMaskFilter {
        val key = (sigma * 4f).roundToInt()
        return blurs.getOrPut(key) { BlurMaskFilter(lpSigma(key / 4f), BlurMaskFilter.Blur.NORMAL) }
    }
}

private fun lpSigma(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

private fun alpha255(a: Float) = (a * 255f).roundToInt().coerceIn(0, 255)

/** The second line, inked up to the roller, and the roller itself. */
private fun DrawScope.lpDrawSecondAndRoller(l: LpLayout, p: LpPose, k: LpPaints) {
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        val inkA = p.inkAlpha * 0.92f
        if (inkA > 0.002f && p.ink > 0f) {
            n.save()
            n.clipRect(0f, k.lineTop, k.rollFrom + (k.rollTo - k.rollFrom) * p.ink, k.lineBottom)
            k.second.alpha = alpha255(inkA)
            for (i in LP_SECOND.indices) n.drawText(LP_SECOND[i], l.secondX, l.secondBaselines[i], k.second)
            n.restore()
        }
        if (p.rollerAlpha > 0.002f) {
            // The roller: a rubber cylinder on a steel frame with a wooden grip.
            n.save()
            n.translate(k.rollFrom + (k.rollTo - k.rollFrom) * p.roller, 0f)
            val a = p.rollerAlpha
            k.rollerShadow.alpha = alpha255(a * 0.35f)
            n.drawRoundRect(-11f, k.lineTop + 4f, 15f, k.lineTop + 4f + k.rollH, 13f, 13f, k.rollerShadow)
            k.fill.shader = null
            k.fill.color = 0xFF8C9097.toInt()
            k.fill.alpha = alpha255(a)
            n.drawRect(-1.5f, k.lineTop - 46f, 1.5f, k.lineTop + 4f, k.fill)
            k.fill.color = 0xFF000000.toInt()
            k.fill.alpha = alpha255(a)
            k.fill.shader = k.grip
            n.drawRoundRect(-6f, k.lineTop - 86f, 6f, k.lineTop - 42f, 6f, 6f, k.fill)
            k.fill.shader = k.rubber
            n.drawRoundRect(-13f, k.lineTop - 4f, 13f, k.lineTop - 4f + k.rollH, 13f, 13f, k.fill)
            k.fill.shader = null
            k.fill.color = android.graphics.Color.argb(alpha255(a * 0.18f), 255, 255, 255)
            n.drawRoundRect(-7f, k.lineTop, -4f, k.lineTop + k.rollH - 8f, 1.5f, 1.5f, k.fill)
            n.restore()
        }
    }
}

/** One metal type block: steel with a bevel, the letter in reverse on its face, a shadow below. */
private fun DrawScope.lpDrawTypeBlock(l: LpLayout, index: Int, p: LpPose, k: LpPaints) {
    val a = p.block[index]
    if (a <= 0.001f) return
    val opacity = if (p.falling[index]) min(1f, a / 0.3f) else a * a
    if (opacity <= 0.002f) return
    val g = l.glyphs[index]
    val x = g.x - 4f
    val y = g.baseline - l.size * 0.82f
    val w = g.w + 8f
    val h = l.size * 1.08f
    val cx = x + w / 2f
    val cy = y + h / 2f
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        // The shadow falls away from the lamp, further and softer the higher the block is.
        val lift = 1.5f + 16f * (1f - a)
        k.blockShadow.maskFilter = k.blur(2f + 9f * (1f - a))
        k.blockShadow.alpha = alpha255(0.42f * opacity)
        val sx = -cos(p.light) * lift
        val sy = -sin(p.light) * lift
        n.drawRoundRect(x + sx, y + sy, x + w + sx, y + h + sy, 3f, 3f, k.blockShadow)
        n.save()
        val s = 1f + 0.25f * (1f - a)
        n.translate(0f, -8f * (1f - a))
        n.scale(s, s, cx, cy)
        k.fill.color = 0xFF000000.toInt()
        k.fill.alpha = alpha255(opacity)
        k.fill.shader = k.steel[index]
        n.drawRoundRect(x, y, x + w, y + h, 3f, 3f, k.fill)
        k.fill.shader = null
        k.stroke.color = 0xFF000000.toInt()
        k.stroke.alpha = alpha255(opacity)
        k.stroke.shader = k.bevel[index]
        n.drawRoundRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, 2.5f, 2.5f, k.stroke)
        k.stroke.shader = null
        // The letter in reverse on the block's face.
        n.scale(-1f, 1f, cx, cy)
        k.headline.color = 0xFF1D1E22.toInt()
        k.headline.alpha = alpha255(opacity)
        n.drawText(g.ch, g.x + 0.8f, g.baseline + 0.8f, k.headline)
        k.headline.color = 0xFFB9BDC4.toInt()
        k.headline.alpha = alpha255(opacity)
        n.drawText(g.ch, g.x, g.baseline, k.headline)
        n.restore()
    }
}

/**
 * Below Android 13: each letter drawn with the paint, a brown and a light copy offset away from
 * and toward the lamp for the deboss's walls, then the vermilion ink, all by the letter's depth.
 */
private fun DrawScope.lpDrawPressFallback(l: LpLayout, count: Int, p: LpPose, k: LpPaints) {
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        val lx = cos(p.light)
        val ly = sin(p.light)
        for (i in 0 until count) {
            val dep = lpClamp01(p.depth[i])
            if (dep < 0.002f) continue
            val g = l.glyphs[i]
            val o = 0.9f * dep
            k.headline.color = android.graphics.Color.argb(alpha255(dep * 0.6f), 255, 252, 245)
            n.drawText(g.ch, g.x - lx * o, g.baseline - ly * o, k.headline)
            k.headline.color = android.graphics.Color.argb(alpha255(dep * 0.5f), 77, 56, 38)
            n.drawText(g.ch, g.x + lx * o, g.baseline + ly * o, k.headline)
            k.headline.color = android.graphics.Color.argb(alpha255(dep * 0.9f), 194, 65, 45)
            n.drawText(g.ch, g.x, g.baseline, k.headline)
        }
    }
}
