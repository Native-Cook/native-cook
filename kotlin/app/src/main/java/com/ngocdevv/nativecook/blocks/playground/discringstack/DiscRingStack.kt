package com.ngocdevv.nativecook.blocks.playground.discringstack

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Faithful port of the React Native Token Hall: metal-rimmed tokens squeezed by cos(yaw).
// LOOP = 9 s, row speed 24 dp/s, six pops of 1.5 s each:
// 0-.4 rise 150 dp and turn from 66 degrees to face-on, ease-out-back (k=1.4);
// .4-.9 hold with a damped 2 degree wobble; .9-1.3 sink, ease-in-out cubic;
// 1.3-1.5 rest. Radius 64 dp, spacing 36 dp, scale at the peak 1.3.
// Drag after 6 dp to slide; tap to pop the middle token with a light haptic.
// After 2 s without touch, resume at the reference's quantized resting phase.
// Reduced motion shows the initial resting wall and disables animation and gestures.

/** Seconds. */
private const val LOOP = 9.0
private const val POP = 1.5
private const val GAP = 36f
private const val R = 64f
private const val YAW = 66f
private const val RISE = 150f
private const val HOLD = 2.0
private const val SPEED = 24.0
private val BACKGROUND = Color(0xFF07070A)
// RN Skia interpolates RGB and alpha toward transparent black separately. Android's
// premultiplied gradient needs intermediate stops to preserve that darker falloff.
private val SPOTLIGHT_COLORS = IntArray(33) { i ->
    val v = 1f - i / 32f
    android.graphics.Color.argb((255 * 0.28f * v).roundToInt(), (120 * v).roundToInt(),
        (130 * v).roundToInt(), (170 * v).roundToInt())
}

private data class DiscRingStackToken(val name: String, val face: Int, val deep: Int, val count: Int)
private val TOKENS = listOf(
    DiscRingStackToken("Star token", 0xFFFFB23F.toInt(), 0xFFE8650F.toInt(), 12),
    DiscRingStackToken("Bolt token", 0xFF5AA8FF.toInt(), 0xFF1F4FD8.toInt(), 7),
    DiscRingStackToken("Leaf token", 0xFF3FD58A.toInt(), 0xFF0F8A4A.toInt(), 21),
    DiscRingStackToken("Heart token", 0xFFFF6A6A.toInt(), 0xFFC8202E.toInt(), 4),
    DiscRingStackToken("Moon token", 0xFFA98BFF.toInt(), 0xFF5B2BD8.toInt(), 9),
    DiscRingStackToken("Crown token", 0xFFE8C27A.toInt(), 0xFFA8762A.toInt(), 2),
)

private fun tokenIndex(slot: Int) = ((slot % 6) + 6) % 6
// JavaScript's Math.round rounds negative half-values toward positive infinity too.
private fun roundedSlot(shift: Float) = floor(shift / GAP + 0.5f).toInt()
private fun cubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private data class DiscRingStackPop(val up: Float = 0f, val wobble: Float = 0f)
private fun popAt(x: Double): DiscRingStackPop {
    if (x < 0 || x > 1.3) return DiscRingStackPop()
    if (x < 0.4) {
        val u = (x / 0.4).toFloat() - 1f
        return DiscRingStackPop(1 + 2.4f * u.pow(3) + 1.4f * u.pow(2))
    }
    if (x < 0.9) {
        val u = (x - 0.4).toFloat()
        return DiscRingStackPop(1f, 2 * sin(u * 14) * exp(-u * 2))
    }
    return DiscRingStackPop(1 - cubic(((x - 0.9) / 0.4).toFloat().coerceIn(0f, 1f)))
}

private data class DiscRingStackWall(val shift: Float = 0f, val slot: Int = 0, val up: Float = 0f, val wobble: Float = 0f)

/** The frame state is observed only by Canvas and graphicsLayer, never by screen composition. */
private class DiscRingStackTimeline {
    var wall by mutableStateOf(DiscRingStackWall())
    var elapsed = 0.0
    private var origin = 0.0
    private var manual = false
    var touching = false
    var lastTouch = -10.0
    var userShift = 0f
    private var userPop = -10.0
    private var userSlot = 0

    fun takeOver() {
        if (!manual) {
            manual = true
            userShift = wall.shift
            userSlot = wall.slot
            userPop = if (wall.up > 0.001f) elapsed - 0.9 else -10.0
        }
        lastTouch = elapsed
    }

    fun tap() {
        takeOver()
        userSlot = roundedSlot(userShift)
        userPop = elapsed
    }

    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (manual) {
            val x = elapsed - userPop
            val pop = popAt(x)
            wall = DiscRingStackWall(userShift, userSlot, pop.up, pop.wobble)
            if (!touching && elapsed - lastTouch > HOLD && (x > 1.3 || x < 0)) {
                manual = false
                val s = ((userShift.toDouble() % 216) + 216) % 216
                val t = floor((s / SPEED) / POP) * POP + 1.31
                origin = elapsed - t
                userShift = 0f
            }
            return
        }
        if (!autoPlay) return
        val t = (((elapsed - origin) % LOOP) + LOOP) % LOOP
        val k = floor(t / POP).toInt()
        val pop = popAt(t - k * POP)
        wall = DiscRingStackWall((SPEED * t).toFloat(), k, pop.up, pop.wobble)
    }
}

private fun iconPath(k: Int) = Path().apply {
    when (k) {
        0 -> {
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val r = if (i % 2 == 0) 1.0 else 0.45
                val x = (cos(a) * r).toFloat()
                val y = (sin(a) * r).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        1 -> {
            moveTo(0.2f, -1f); lineTo(-0.55f, 0.12f); lineTo(-0.02f, 0.12f)
            lineTo(-0.25f, 1f); lineTo(0.55f, -0.15f); lineTo(0.02f, -0.15f)
        }
        2 -> {
            moveTo(-0.7f, 0.75f)
            cubicTo(-0.9f, -0.3f, 0f, -0.95f, 0.85f, -0.85f)
            cubicTo(0.95f, 0f, 0.3f, 0.9f, -0.7f, 0.75f)
        }
        3 -> {
            moveTo(0f, 0.9f)
            cubicTo(-1.1f, 0.1f, -0.9f, -0.95f, 0f, -0.45f)
            cubicTo(0.9f, -0.95f, 1.1f, 0.1f, 0f, 0.9f)
        }
        4 -> {
            moveTo(0.2f, -0.95f)
            cubicTo(-0.75f, -0.85f, -1f, 0.35f, -0.35f, 0.8f)
            cubicTo(0.15f, 1.1f, 0.75f, 0.85f, 0.95f, 0.35f)
            cubicTo(0.3f, 0.55f, -0.35f, 0.05f, 0.2f, -0.95f)
        }
        else -> {
            moveTo(-0.9f, 0.6f); lineTo(-0.95f, -0.55f); lineTo(-0.45f, -0.05f)
            lineTo(0f, -0.8f); lineTo(0.45f, -0.05f); lineTo(0.95f, -0.55f); lineTo(0.9f, 0.6f)
        }
    }
    close()
}

/** Reuse paths, paints and local-coordinate shaders; each token changes only its transform. */
private class DiscRingStackRenderer {
    private val icons = List(6, ::iconPath)
    private val side = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-R, -R, R, R,
            intArrayOf(0xFFF4F5F8.toInt(), 0xFF9EA1AC.toInt(), 0xFFE6E7EC.toInt(), 0xFF6E717C.toInt()),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val faces = TOKENS.map { token ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(-R * 0.3f, -R * 0.35f, R * 1.3f,
                intArrayOf(token.face, token.deep), null, Shader.TileMode.CLAMP)
        }
    }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF; style = Paint.Style.STROKE
    }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -R, 0f, 0f, intArrayOf(0x59FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    }
    private val slot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59282A32; style = Paint.Style.STROKE; strokeWidth = 1.5f; strokeCap = Paint.Cap.BUTT
    }
    private val lip = Paint().apply { color = 0xFFD9DBE2.toInt() }
    private var cachedWidth = 0f
    private var cachedHeight = 0f

    private fun token(c: Canvas, k: Int, x: Float, y: Float, yawDegrees: Float, scale: Float) {
        val yaw = yawDegrees * PI.toFloat() / 180
        val sx = max(0.06f, cos(yaw))
        val r = R * scale
        val edge = 14 * scale * sin(yaw)
        // The reference uses world-relative coordinates for this gradient, even inside the transform.
        side.shader = LinearGradient(x, y - r, x, y + r,
            intArrayOf(0xFF8D8F99.toInt(), 0xFF3C3E46.toInt(), 0xFFA7A9B3.toInt()), null, Shader.TileMode.CLAMP)
        c.save()
        c.translate(x + edge, y)
        c.scale(sx, 1f)
        c.drawCircle(0f, 0f, r, side)
        c.restore()
        c.save()
        c.translate(x, y)
        c.scale(sx * scale, scale)
        c.drawCircle(0f, 0f, R, rim)
        c.drawCircle(0f, 0f, R * 0.82f, faces[k])
        inner.strokeWidth = 2f / scale
        c.drawCircle(0f, 0f, R * 0.7f, inner)
        c.save()
        c.scale(R * 0.38f, R * 0.38f)
        c.drawPath(icons[k], white)
        c.restore()
        c.drawCircle(0f, 0f, R * 0.82f, gloss)
        c.restore()
    }

    fun draw(c: Canvas, wall: DiscRingStackWall, width: Float, height: Float) {
        val cx = width / 2
        val top = height * 0.64f
        val baseY = top - 30
        if (cachedWidth != width || cachedHeight != height) {
            cachedWidth = width; cachedHeight = height
            spot.shader = RadialGradient(cx, top - 60, width * 0.8f,
                SPOTLIGHT_COLORS, null, Shader.TileMode.CLAMP)
            slot.shader = LinearGradient(0f, top, 0f, top + 150,
                intArrayOf(0xFFB9BBC3.toInt(), 0xFF8A8D96.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, width, height, spot)
        val first = floor((wall.shift - cx - 80) / GAP).toInt()
        val last = ceil((wall.shift + cx + 80) / GAP).toInt()
        var popped: Int? = null
        for (j in last downTo first) {
            if (j == wall.slot && wall.up > 0.001f) { popped = j; continue }
            token(c, tokenIndex(j), cx + j * GAP - wall.shift, baseY, YAW, 1f)
        }
        c.drawRect(0f, top, width, top + 150, slot)
        for (j in first..last) {
            if (j % 2 != 0) continue
            val x = cx + j * GAP - wall.shift - GAP / 2
            c.drawLine(x, top, x, top + 150, seam)
        }
        c.drawRect(0f, top, width, top + 3, lip)
        popped?.let { j ->
            val lift = max(0f, wall.up)
            token(c, tokenIndex(j), cx + j * GAP - wall.shift, baseY - RISE * wall.up,
                YAW * (1 - min(1f, lift)) + wall.wobble, 1 + 0.3f * min(1.1f, lift))
        }
    }
}

private fun copyStyle(size: Int, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f, lineHeight: Int = size) = TextStyle(
    color = Color.White.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight,
    lineHeight = lineHeight.sp, letterSpacing = 0.sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun DiscRingStack(autoPlay: Boolean = false, modifier: Modifier = Modifier, title: String = "Your token wall") {
    val density = LocalDensity.current.density
    val reduced = rememberReducedMotion()
    val timeline = remember { DiscRingStackTimeline() }
    val renderer = remember { DiscRingStackRenderer() }
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LaunchedEffect(lifecycle, autoPlay, reduced) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { at ->
                timeline.frame(if (previous == 0L) 0.0 else (at - previous) / 1e9, autoPlay)
                previous = at
            }
        }
    }

    Box(modifier.fillMaxSize().background(BACKGROUND)) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "A row of arcade tokens. Drag to slide, tap to pop one." }
                .pointerInput(reduced, density) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var grabbed = 0f
                        var last = down.position
                        var released = false
                        var duration = 0L
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                last = change.position
                                duration = change.uptimeMillis - down.uptimeMillis
                                val translation = last - down.position
                                if (change.pressed && !dragging && translation.getDistance() >= 6f * density) {
                                    timeline.takeOver()
                                    timeline.touching = true
                                    grabbed = timeline.userShift
                                    dragging = true
                                }
                                if (dragging) {
                                    change.consume()
                                    timeline.userShift = grabbed - translation.x / density
                                    timeline.lastTouch = timeline.elapsed
                                }
                                if (!change.pressed) { released = true; break }
                            }
                        } finally {
                            if (dragging) {
                                timeline.touching = false
                                timeline.lastTouch = timeline.elapsed
                            }
                        }
                        if (!dragging && released && duration <= 500 && (last - down.position).getDistance() < 6f * density) {
                            timeline.tap()
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        }
                    }
                },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density, density)
                renderer.draw(native, timeline.wall, size.width / density, size.height / density)
                native.restore()
            }
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + 8.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Token Hall", style = copyStyle(18, FontWeight.ExtraBold))
                Spacer(Modifier.weight(1f))
                Text("55 tokens", style = copyStyle(14, FontWeight.SemiBold, 0.55f))
            }
            Text(title, Modifier.padding(top = 16.dp), style = copyStyle(36, FontWeight.Black, lineHeight = 40).copy(letterSpacing = (-1).sp))
            Text("Every game you win drops one in. Something to watch forever.",
                Modifier.padding(top = 8.dp), style = copyStyle(16, alpha = 0.6f, lineHeight = 22))
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = insets.calculateBottomPadding() + 14.dp)) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                TOKENS.forEachIndexed { k, token ->
                    Row(
                        Modifier.fillMaxSize().graphicsLayer {
                            val wall = timeline.wall
                            val on = if (tokenIndex(wall.slot) == k) (wall.up * 1.4f).coerceIn(0f, 1f) else 0f
                            alpha = on
                            translationY = 10 * (1 - on) * density
                        }.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(22.dp).background(Color(token.face), CircleShape))
                        Text(token.name, Modifier.padding(start = 12.dp).weight(1f), style = copyStyle(17, FontWeight.Bold))
                        Text("× ${token.count}", style = copyStyle(17, FontWeight.ExtraBold, 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(58.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text("Trade tokens", style = copyStyle(17, FontWeight.Bold).copy(color = BACKGROUND))
            }
        }
    }
}
