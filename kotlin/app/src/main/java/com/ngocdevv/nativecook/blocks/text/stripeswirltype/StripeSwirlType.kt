package com.ngocdevv.nativecook.blocks.text.stripeswirltype

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// A magazine's new-issue screen whose cover line, "MAKE / SOME / WAVES", is drawn only as
// horizontal stripes. A swirl field stirs them: around its centre the stripes twist into rings
// and arcs that break into coloured capsules, then the swirl unwinds (past straight and back) and
// the words read again. Dragging through the type stirs it with the finger. A port of the SwiftUI
// block, number for number.
//
// autoPlay timeline (LOOP = 8 s):
//   0.0-1.0   rest: straight stripes, legible words
//   0.8-1.15  a ghost finger ring fades in on the swirl's centre
//   1.0-4.6   the centre circles the words clockwise (1.25 turns, ease-in-out cubic)
//   1.0-3.3   the twist builds 0 -> 7.5 rad, the radius 60 -> 170 dp (ease-in-out cubic)
//   2.4-4.6   an extra spin of the core (2.2 rad, sin² bump)
//   3.3-4.4   peak: rings and colour
//   4.4-4.85  the ring fades out
//   4.4-6.8   the twist unwinds: 7.5 (1 - u)³ cos(1.8 π u), u = smoothstep over the phase, so it
//             dips about 1 rad the other way and lands at 0 with no speed; the radius shrinks back
//   6.8-8.0   rest, identical to t = 0
//
// The swirl is an AGSL shader (API 33+). Below that the words are drawn as stripes by clipping them
// to bands, and the stir becomes a sideways wobble of the bands.

/** Seconds. */
private const val LOOP = 8f
/** Twist at the peak, radians at the centre. */
private const val PEAK = 7.5f
private const val MIN_RADIUS = 60f
private const val MAX_RADIUS = 170f
private const val SPIN = 2.2f
/** The centre's path, as fractions of the screen width and of the type block's height. */
private const val ORBIT_X = 0.2f
private const val ORBIT_Y = 0.16f
private const val USER_HOLD = 2f
private const val UNWIND = 1.6f
private const val MARGIN = 24f
private const val GAP = 18f
private const val STRIPE_PERIOD = 6f
private const val STRIPE_HALF = 1.7f
/** The mask's blur, as a Gaussian sigma in dp. */
private const val MASK_BLUR = 1.6f

private val BACKGROUND = Color(0xFF100F0E)
private val INK = Color(0xFFF1ECE3)
private val RED = Color(0xFFE8432E)
private val BLUE = Color(0xFF2D63D8)
private val GREEN = Color(0xFF2C9F5B)
private val YELLOW = Color(0xFFF2B42C)
private val SKY = Color(0xFF49B3EA)

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private class Stir(
    /** The centre on the orbit, -1..1 on each axis. */
    val orbitX: Float,
    val orbitY: Float,
    val twist: Float,
    val radius: Float,
    val spin: Float,
    val ring: Float,
    val ringScale: Float,
)

private val REST = Stir(cos(-0.35f * PI.toFloat()), sin(-0.35f * PI.toFloat()), 0f, MIN_RADIUS, 0f, 0f, 0.8f)

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun easeOutCubic(t: Float) = 1 - (1 - t).pow(3)
private fun easeInCubic(t: Float) = t * t * t

/** From 1 to 0, dipping about 0.14 below zero on the way and landing with no speed. */
private fun unwound(x: Float): Float {
    val u = x * x * (3 - 2 * x)
    return (1 - u).pow(3) * cos(1.8f * PI.toFloat() * u)
}

private fun radiusForTwist(twist: Float) = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * min(1f, abs(twist) / PEAK)

/** The stir `t` seconds into the loop. */
private fun stirAt(t: Float): Stir {
    val build = easeInOutCubic(seg(t, 1.0f, 3.3f))
    val x = seg(t, 4.4f, 6.8f)
    val twist = if (t < 4.4f) PEAK * build else PEAK * unwound(x)
    val env = if (t < 4.4f) build else 1 - easeInOutCubic(x)
    val swing = sin(PI.toFloat() * seg(t, 2.4f, 4.6f))
    val phi = -0.35f * PI.toFloat() + 2.5f * PI.toFloat() * easeInOutCubic(seg(t, 1.0f, 4.6f))
    val ringIn = easeOutCubic(seg(t, 0.8f, 1.15f))
    val ringOut = easeInCubic(seg(t, 4.4f, 4.85f))
    return Stir(
        cos(phi), sin(phi), twist,
        MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * env,
        SPIN * swing * swing,
        ringIn * (1 - ringOut),
        0.8f + 0.2f * ringIn - 0.15f * ringOut,
    )
}

/** A number animating towards a target, sampled against the frame clock (nanoseconds). */
private class Tween(value: Float) {
    private var from = value
    private var to = value
    private var start = 0L
    private var duration = 0f

    fun value(at: Long): Float {
        if (duration <= 0f) return to
        val t = ((at - start) / 1e9f / duration).coerceIn(0f, 1f)
        return from + (to - from) * EASE_OUT.transform(t)
    }

    fun retarget(target: Float, at: Long, duration: Float) {
        from = value(at)
        to = target
        start = at
        this.duration = duration
    }

    fun snap(v: Float) {
        from = v
        to = v
        duration = 0f
    }
}

// The same function as the Metal one, in AGSL, in pixels (`scale` turns them into dp).
private const val FIELD_AGSL = """
uniform shader layer;
uniform float scale;
uniform float2 size;
uniform float2 centre;
uniform float twist;
uniform float radius;
uniform float spin;
layout(color) uniform half4 inkColor;
layout(color) uniform half4 backColor;
layout(color) uniform half4 c0;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;
layout(color) uniform half4 c4;

float hash1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float hash2(float2 v) { return fract(sin(dot(v, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 fragCoord) {
    float2 p = fragCoord / scale;
    float2 d = p - centre;
    float r = length(d);
    float R = max(radius, 1.0);
    float s = clamp(1.0 - r / R, 0.0, 1.0);
    float theta = twist * s * s + spin * s;
    float slope = r < R ? -(2.0 * twist * s + spin) / R : 0.0;
    float2 rhat = r > 0.001 ? d / r : float2(0.0);
    float cs = cos(theta);
    float sn = sin(theta);
    float2 q = centre + float2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y);
    float2 gy = float2(-sn, cs) - (cs * d.x + sn * d.y) * slope * rhat;
    float2 gx = float2(cs, sn) + (-sn * d.x + cs * d.y) * slope * rhat;
    float g2 = max(dot(gy, gy), 1e-4);
    float g = sqrt(g2);
    float gxl = max(length(gx), 1e-3);

    float2 uv = p / size - 0.5;
    float vig = 1.0 - 0.55 * smoothstep(0.25, 0.85, length(uv * float2(size.x / size.y, 1.0)) * 1.25);
    float grain = (hash2(floor(p * 2.0)) - 0.5) * 0.05;
    float3 col = float3(backColor.rgb) * vig + grain * 0.35;

    float period = 6.0;
    float aa = 0.45;
    float colour = smoothstep(0.5, 1.8, abs(theta));
    float broken = max(colour, smoothstep(1.2, 2.0, g));
    float base = floor(q.y / period);
    for (int k = -2; k <= 2; k++) {
        float row = base + float(k);
        float yk = (row + 0.5) * period;
        float dyk = q.y - yk;
        float dist = abs(dyk) / g;
        if (dist <= 1.7 + aa) {
            float2 st = -dyk * gy / g2;
            float sx = q.x + dot(gx, st);
            float m = float(layer.eval(float2(sx, yk) * scale).r);
            float halfT = 1.7 * smoothstep(0.22, 0.85, m);
            if (halfT >= 0.3) {
                float hr = hash1(row + 3.1);
                float len = 22.0 + 26.0 * hr;
                float xs = sx + hr * 97.0;
                float piece = floor(xs / len);
                float xm = (piece + 0.5) * len;
                float ex = max(abs(xs - xm) / gxl - ((0.5 * len - 1.2) / gxl - halfT), 0.0) * broken;
                float e = length(float2(ex, dist)) - halfT;
                float cover = 1.0 - smoothstep(-aa, aa, e);

                float pick = hash2(float2(piece, row));
                float3 tint = float3(c0.rgb);
                if (pick > 0.18) { tint = float3(c1.rgb); }
                if (pick > 0.36) { tint = float3(c2.rgb); }
                if (pick > 0.54) { tint = float3(c3.rgb); }
                if (pick > 0.72) { tint = float3(c4.rgb); }
                if (pick > 0.9) { tint = float3(inkColor.rgb); }
                col = mix(col, mix(float3(inkColor.rgb), tint, colour), cover);
            }
        }
    }
    return half4(half3(col), 1.0);
}
"""

@RequiresApi(33)
private class StripeSwirlTypeShader(density: Float) {
    val shader = RuntimeShader(FIELD_AGSL).apply {
        setColorUniform("inkColor", INK.toArgb())
        setColorUniform("backColor", BACKGROUND.toArgb())
        setColorUniform("c0", RED.toArgb())
        setColorUniform("c1", BLUE.toArgb())
        setColorUniform("c2", GREEN.toArgb())
        setColorUniform("c3", YELLOW.toArgb())
        setColorUniform("c4", SKY.toArgb())
    }

    // RenderEffect's blur radius r is a Gaussian of sigma 0.57735 r + 0.5.
    private val blurRadius = max(0.1f, (MASK_BLUR * density - 0.5f) / 0.57735f)
    private val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)

    fun effect(scale: Float, width: Float, height: Float, cx: Float, cy: Float, twist: Float, radius: Float, spin: Float): RenderEffect {
        shader.setFloatUniform("scale", scale)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("centre", cx, cy)
        shader.setFloatUniform("twist", twist)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("spin", spin)
        // The blur runs first, so the shader reads the blurred mask.
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "layer"), blur)
    }
}

/** A word's outline in px with its ink box's top-left corner at the origin, and that box. */
private class Outline(val path: android.graphics.Path, val width: Float, val height: Float)

private fun outline(word: String, typeface: Typeface, sizePx: Float): Outline {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePx
    }
    val path = android.graphics.Path()
    paint.getTextPath(word, 0, word.length, 0f, 0f, path)
    val box = RectF()
    @Suppress("DEPRECATION")
    path.computeBounds(box, true)
    path.offset(-box.left, -box.top)
    return Outline(path, box.width(), box.height())
}

/** The three words in px, and the block they fill (top and height). */
private class Block(val path: android.graphics.Path, val top: Float, val height: Float)

/**
 * The words, each fitted to the width less the margins and centred, stacked with [GAP] between
 * them and centred between [top] and [bottom] (scaled down if they do not fit). All in px.
 */
private fun block(lines: List<Pair<String, Typeface>>, width: Float, top: Float, bottom: Float, density: Float): Block {
    val target = width - 2 * MARGIN * density
    val gap = GAP * density
    val sizes = lines.map { (word, face) -> 100f * target / max(outline(word, face, 100f).width, 1f) }
    val natural = lines.indices.map { outline(lines[it].first, lines[it].second, sizes[it]).height }
    val total = natural.sum() + gap * (lines.size - 1)
    val k = min(1f, (bottom - top) / total)
    var y = top + (bottom - top - total * k) / 2
    val startY = y
    val path = android.graphics.Path()
    lines.forEachIndexed { i, (word, face) ->
        val o = outline(word, face, sizes[i] * k)
        path.addPath(o.path, (width - o.width) / 2, y)
        y += o.height + gap * k
    }
    return Block(path, startY, y - gap * k - startY)
}

/**
 * @param autoPlay Stirs the words by itself, every [LOOP] seconds.
 */
@Composable
fun StripeSwirlType(
    modifier: Modifier = Modifier,
    wordmark: String = "UNDERTOW",
    issue: String = "Issue 07 · Autumn",
    hint: String = "Drag through the type to stir it",
    headline: String = "Issue 07 is out.",
    subline: String = "Twelve stories about change, told slowly.",
    action: String = "Start reading",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val auto by rememberUpdatedState(autoPlay)

    val anton = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "stripeswirltype/stripeswirltype_Anton-Regular.ttf") }
            .getOrDefault(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
    }
    val serif = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "stripeswirltype/stripeswirltype_DMSerifDisplay-Regular.ttf") }
            .getOrDefault(Typeface.SERIF)
    }
    val antonFamily = remember(anton) { FontFamily(androidx.compose.ui.text.font.Typeface(anton)) }
    val serifFamily = remember(serif) { FontFamily(androidx.compose.ui.text.font.Typeface(serif)) }
    val lines = remember(anton, serif) { listOf("MAKE" to anton, "SOME" to serif, "WAVES" to anton) }
    val shader = remember(density) { if (Build.VERSION.SDK_INT >= 33) StripeSwirlTypeShader(density.density) else null }

    // The frame clock, in nanoseconds; read only in draw and layer blocks.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // True from a touch until the swirl has unwound and the loop has started over.
    var manual by remember { mutableStateOf(false) }
    val centreX = remember { Tween(0f) }
    val centreY = remember { Tween(0f) }
    val twist = remember { Tween(0f) }
    // When the finger's swirl started to unwind (0: not unwinding), and from which twist.
    var unwindStart by remember { mutableLongStateOf(0L) }
    var unwindFrom by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    LaunchedEffect(reducedMotion, manual, autoPlay) {
        if ((reducedMotion || !autoPlay) && !manual) return@LaunchedEffect
        while (true) withFrameNanos { clock.longValue = it.coerceAtLeast(clock.longValue) }
    }

    // 2 s after the last touch the swirl unwinds, and the loop starts over from rest.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay((USER_HOLD * 1000).toLong())
        if (dragging) return@LaunchedEffect
        unwindFrom = twist.value(clock.longValue)
        unwindStart = clock.longValue
        delay((UNWIND * 1000).toLong())
        if (dragging) return@LaunchedEffect
        unwindStart = 0L
        twist.snap(0f)
        origin = clock.longValue
        manual = false
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val d = density.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val safeTop = insets.calculateTopPadding().value
        val safeBottom = insets.calculateBottomPadding().value
        // Bottom copy, from the bottom up: 18 + button 54 + 22 + subline 20 + 10 + headline 32 + 22 + hint 16.
        val hintTop = heightPx / d - safeBottom - 194f
        val words = remember(lines, widthPx, heightPx, safeTop, safeBottom) {
            block(lines, widthPx, (safeTop + 70f) * d, (hintTop - 24f) * d, d)
        }
        val wordsPath = remember(words) { words.path.asComposePath() }
        val widthDp = widthPx / d
        val blockMidDp = (words.top + words.height / 2) / d
        val blockHeightDp = words.height / d

        // The swirl, in dp: its centre, twist, radius and spin, plus the ghost ring.
        class Field(val cx: Float, val cy: Float, val twist: Float, val radius: Float, val spin: Float, val ring: Float, val ringScale: Float)

        fun manualTwist(at: Long): Float {
            if (unwindStart != 0L) {
                val x = ((at - unwindStart) / 1e9f / UNWIND).coerceIn(0f, 1f)
                return unwindFrom * unwound(x)
            }
            return twist.value(at)
        }

        fun field(at: Long): Field {
            if (manual) {
                val tw = manualTwist(at)
                return Field(centreX.value(at), centreY.value(at), tw, radiusForTwist(tw), 0f, 0f, 1f)
            }
            val stir = if (auto && !reduced) stirAt((((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f)) else REST
            return Field(
                widthDp / 2 + stir.orbitX * ORBIT_X * widthDp,
                blockMidDp + stir.orbitY * ORBIT_Y * blockHeightDp,
                stir.twist, stir.radius, stir.spin, stir.ring, stir.ringScale,
            )
        }

        // The words: a mask (black, the words white) that the shader turns into stripes (API 33+),
        // or stripes clipped from the words, wobbling sideways while stirred, below that.
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = lines.joinToString(" ") { it.first } }
                .graphicsLayer {
                    if (shader != null && Build.VERSION.SDK_INT >= 33) {
                        val f = field(clock.longValue)
                        renderEffect = shader.effect(d, widthDp, heightPx / d, f.cx, f.cy, f.twist, f.radius, f.spin).asComposeRenderEffect()
                    }
                }
                .drawBehind {
                    if (shader != null) {
                        drawRect(Color.Black)
                        drawPath(wordsPath, Color.White)
                    } else {
                        val f = field(clock.longValue)
                        val amount = min(1f, abs(f.twist) / PEAK)
                        val period = STRIPE_PERIOD * d
                        var row = (words.top / period).toInt()
                        while (row * period < words.top + words.height) {
                            val y = (row + 0.5f) * period
                            val fall = (1 - abs(y / d - f.cy) / f.radius).coerceIn(0f, 1f)
                            val dx = amount * fall * 14f * d * sin(row * 0.55f + f.twist)
                            clipRect(0f, y - STRIPE_HALF * d, size.width, y + STRIPE_HALF * d) {
                                translate(dx, 0f) { drawPath(wordsPath, INK) }
                            }
                            row++
                        }
                    }
                },
        )

        // The ghost finger that stirs the words in the loop; a drag anywhere stirs them by hand.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(lines, widthPx, heightPx, reducedMotion) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now = clock.longValue
                        // Pick the swirl up where it is, so the touch never jumps.
                        val current = field(now)
                        manual = true
                        dragging = true
                        unwindStart = 0L
                        twist.snap(current.twist)
                        centreX.snap(current.cx)
                        centreY.snap(current.cy)
                        touches++
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var sign = 1f
                        var peaked = false
                        // The swirl moves to the finger at once, at its slowest stir.
                        centreX.retarget(down.position.x / d, now, 0.1f)
                        centreY.retarget(down.position.y / d, now, 0.1f)
                        twist.retarget(sign * PEAK * 0.35f, now, 0.35f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val t = clock.longValue
                            val px = change.position.x / d
                            val py = change.position.y / d
                            centreX.retarget(px, t, 0.1f)
                            centreY.retarget(py, t, 0.1f)
                            val v = tracker.calculateVelocity()
                            val vx = v.x / d
                            val vy = v.y / d
                            val speed = hypot(vx, vy)
                            // Stirring clockwise around the words twists one way, anticlockwise the other.
                            val cross = (px - widthDp / 2) * vy - (py - blockMidDp) * vx
                            if (speed > 40f) sign = if (cross >= 0f) 1f else -1f
                            val target = sign * PEAK * (0.35f + speed / 900f).coerceIn(0f, 1f)
                            twist.retarget(target, t, 0.35f)
                            if (!peaked && abs(twist.value(t)) > 7f) {
                                peaked = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        dragging = false
                        touches++
                    }
                }
                .drawBehind {
                    val f = field(clock.longValue)
                    if (f.ring <= 0f) return@drawBehind
                    val c = Offset(f.cx * d, f.cy * d)
                    val r = 22f * d * f.ringScale
                    drawCircle(INK.copy(alpha = 0.1f * f.ring), r, c)
                    drawCircle(INK.copy(alpha = 0.7f * f.ring), r, c, style = Stroke(1.5f * d))
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = MARGIN.dp),
        ) {
            Row(
                Modifier
                    .padding(top = (safeTop + 10f).dp)
                    .fillMaxWidth()
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(wordmark, modifier = Modifier.alignByBaseline(), style = TextStyle(fontFamily = antonFamily, fontSize = 20.sp, letterSpacing = 2.sp, color = INK))
                Spacer(Modifier.weight(1f))
                Text(issue, modifier = Modifier.alignByBaseline(), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.55f)))
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(INK.copy(alpha = 0.14f)),
            )

            Spacer(Modifier.weight(1f))

            Text(hint, modifier = Modifier.height(16.dp), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.45f)))
            Text(headline, modifier = Modifier.padding(top = 22.dp).height(32.dp), style = TextStyle(fontFamily = serifFamily, fontSize = 26.sp, color = INK))
            Text(
                subline,
                modifier = Modifier.padding(top = 10.dp).height(20.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 15.sp, color = INK.copy(alpha = 0.62f)),
            )
            Box(
                Modifier
                    .padding(top = 22.dp, bottom = (safeBottom + 18f).dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(INK, CircleShape)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = BACKGROUND))
            }
        }
    }
}
