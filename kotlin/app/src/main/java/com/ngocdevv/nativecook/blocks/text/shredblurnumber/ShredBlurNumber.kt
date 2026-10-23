package com.ngocdevv.nativecook.blocks.text.shredblurnumber

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// A festival poster on hot pink whose huge black number shreds into a comb of vertical strips,
// melts into a grainy blur, comes back out through the strips and settles sharp again, strip by
// strip from left to right. A port of the SwiftUI block, number for number. Swift samples the
// number through a Metal layer shader; here each of the 36 strips draws the number clipped to the
// strip through the same mapping (it is affine per strip), so it runs on every API level. The blur
// is a RenderEffect (API 31+) and the grain an AGSL brush over the whole screen (API 33+), strongest while it is blurred.
//
// autoPlay timeline (LOOP = 6 s). `amount` 0 is the clean number and 1 every strip shredded;
// strips start in turn over the left-to-right order (`lead` 0) or right-to-left (`lead` 1).
// `blur` 0-1 is 0-22 dp of Gaussian blur, a 12 % zoom towards the 9 and the grain (0.08 at rest,
// so the poster always has a faint living grain, up to 0.45).
//   0.0-1.2   rest: the clean number
//   1.2-2.1   shred in, left strips first (amount 0 -> 1, ease-in-out cubic)
//   2.1-2.6   the comb ripples (phase-driven)
//   2.5-3.1   blur in (blur 0 -> 1, ease-in-out quad); under it the strips fall back to 0.3 (2.8-3.2)
//   3.1-3.4   the grainy blob
//   3.4-3.9   blur out (ease-out cubic) while the strips come back (amount 0.3 -> 1, 3.4-3.8)
//   3.9       lead flips to right-to-left (every strip is at 1, so nothing moves)
//   3.9-4.4   the comb ripples
//   4.4-5.4   resolve, right strips last (amount 1 -> 0, ease-out cubic)
//   5.4-6.0   rest
// The ripple's phase runs once around per loop, so one period repeats exactly.
//
// Touch: a finger shreds the strips nearest to it first (amount 0.45, + 1 per 200 dp dragged
// sideways), the strips slide with it, and dragging up or down blurs (1 per 240 dp). 2 s after
// the finger lifts everything eases back over 0.8 s and the loop starts over.

/** Seconds. */
private const val LOOP = 6f
/** Dp of blur at `blur` 1. */
private const val MAX_BLUR = 22f
private const val ZOOM = 0.12f
/** The grain's strength at rest and at full blur. */
private const val GRAIN = 0.08f
private const val BLUR_GRAIN = 0.45f
private const val USER_HOLD = 2f
private const val SETTLE = 0.8f
private const val STRIPS = 36
/** Dp above and below the glyphs for the stretched strips. */
private const val PAD = 72f

private val BACKGROUND = Color(0xFFF063D4)
private val INK = Color(0xFF14090F)

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private class ShredState(val amount: Float, val lead: Float, val blur: Float)

private val REST = ShredState(0f, 0f, 0f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun easeInOutQuad(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
private fun easeOutCubic(t: Float) = 1 - (1 - t).pow(3)
private fun span(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

/** The screen `t` seconds into the loop. */
private fun stateAt(t: Float): ShredState {
    val blur = if (t < 3.4f) easeInOutQuad(span(t, 2.5f, 3.1f)) else 1 - easeOutCubic(span(t, 3.4f, 3.9f))
    val amount = when {
        t < 2.8f -> easeInOutCubic(span(t, 1.2f, 2.1f))
        t < 3.4f -> 1 - 0.7f * easeInOutCubic(span(t, 2.8f, 3.2f))
        t < 4.4f -> 0.3f + 0.7f * easeInOutCubic(span(t, 3.4f, 3.8f))
        else -> 1 - easeOutCubic(span(t, 4.4f, 5.4f))
    }
    return ShredState(amount, if (t < 3.9f) 0f else 1f, blur)
}

private fun fract(x: Float) = x - floor(x)

/** The Metal hash, in Kotlin floats. */
private fun hash1(n: Float): Float {
    var x = fract(n * 0.1031f)
    var y = fract(n * 1.37f * 0.1031f)
    var z = fract(n * 0.73f * 0.1031f)
    val d = x * (y + 33.33f) + y * (z + 33.33f) + z * (x + 33.33f)
    x += d
    y += d
    z += d
    return fract((x + y) * z)
}

/** One strip's mapping: it shows the layer point `(cx + (x - c) * span, h/2 + (y - h/2 - yoff) / stretch)`. */
private class Strip(val a: Float, val c: Float, val cx: Float, val span: Float, val stretch: Float, val yoff: Float)

/** The strip `i` of a canvas `w` × `h` (dp); the same numbers as the Metal shader. */
private fun strip(
    i: Int, w: Float, h: Float, amount: Float, lead: Float, focusX: Float, focusMix: Float, phase: Float, drift: Float,
): Strip {
    val n = STRIPS.toFloat()
    val sw = w / n
    val fi = i.toFloat()
    val c = (fi + 0.5f) * sw
    var order = fi / (n - 1) + (1 - 2 * fi / (n - 1)) * lead
    order += (clamp01(abs(c - focusX) / (w * 0.6f)) - order) * focusMix
    var a = clamp01(amount * 2.2f - 1.2f * order)
    a = a * a * (3 - 2 * a)
    if (a <= 0f) return Strip(0f, c, c, 1f, 1f, 0f)
    val h1 = hash1(fi + 1)
    val h2 = hash1(fi + 31)
    val h3 = hash1(fi + 71)
    val s1 = 0.5f + 0.5f * sin(fi * 0.35f + phase + 1f)
    val s2 = 0.5f + 0.5f * sin(fi * 0.22f - 2f * phase + 2f)
    val span = 1f + (1.4f + 2.2f * s1 + 0.8f * h1 - 1f) * a
    val cx = c + a * ((s2 - 0.5f) * 2f * sw + (h2 - 0.5f) * 1.6f * sw + drift * (0.5f + h3))
    val stretch = 1f + a * (0.3f + 1.6f * s2 + 0.6f * h2)
    val yoff = a * h * 0.10f * (sin(fi * 0.4f + 2f * phase + 1.3f) + 0.8f * (h1 - 0.5f))
    return Strip(a, c, cx, span, stretch, yoff)
}

/** A number animating towards a target, sampled against the frame clock (nanoseconds). */
private class Tween(value: Float) {
    private var from = value
    private var to = value
    private var start = 0L
    private var duration = 0f
    private var easing: Easing = EASE_OUT

    fun value(at: Long): Float {
        if (duration <= 0f) return to
        val t = clamp01((at - start) / 1e9f / duration)
        return from + (to - from) * easing.transform(t)
    }

    fun retarget(target: Float, at: Long, duration: Float, easing: Easing) {
        from = value(at)
        to = target
        start = at
        this.duration = duration
        this.easing = easing
    }

    fun set(value: Float) {
        from = value
        to = value
        duration = 0f
    }
}

// Film grain over the whole screen, the Metal function in AGSL: cells of 1.25 dp, a new pattern
// `frame` by `frame`, white and black specks up to `amount` × 0.5 opacity.
private const val GRAIN_AGSL = """
uniform float scale;
uniform float amount;
uniform float frame;

float hash2(float2 c) {
    float3 p3 = fract(float3(c.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / scale;
    float v = hash2(floor(p / 1.25) + float2(frame * 17.0, frame * 31.0)) - 0.5;
    float a = amount * abs(v);
    return v > 0.0 ? half4(half(a), half(a), half(a), half(a)) : half4(0.0, 0.0, 0.0, half(a));
}
"""

@RequiresApi(33)
private class ShredBlurNumberGrain {
    val shader = RuntimeShader(GRAIN_AGSL)
    val brush = ShaderBrush(shader)

    fun update(scale: Float, amount: Float, frame: Float): ShaderBrush {
        shader.setFloatUniform("scale", scale)
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("frame", frame)
        return brush
    }
}

/** The text's outline in px with its ink box's top-left corner at the origin, and that box. */
private class Outline(val path: android.graphics.Path, val width: Float, val height: Float)

private fun outline(text: String, typeface: Typeface, sizePx: Float): Outline {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePx
    }
    val path = android.graphics.Path()
    paint.getTextPath(text, 0, text.length, 0f, 0f, path)
    val box = RectF()
    @Suppress("DEPRECATION")
    path.computeBounds(box, true)
    path.offset(-box.left, -box.top)
    return Outline(path, box.width(), box.height())
}

/**
 * @param autoPlay Shreds, blurs and resolves the number by itself, every [LOOP] seconds.
 */
@Composable
fun ShredBlurNumber(
    modifier: Modifier = Modifier,
    number: String = "19",
    brand: String = "LATE FORMS",
    since: String = "SINCE 2007",
    kicker: String = "EDITION NINETEEN",
    headline: String = "Nineteen years\nof loud letters.",
    dates: String = "Oct 17 – 19",
    venue: String = "Pier Hall, Dock 4",
    action: String = "Get passes",
    hint: String = "Drag across the number",
    autoPlay: Boolean = false,
) {
    ShredBlurNumberSystemBars()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val auto by rememberUpdatedState(autoPlay)

    val typeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "shredblurnumber/shredblurnumber_ArchivoBlack-Regular.ttf") }
            .getOrDefault(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
    }
    val family = remember(typeface) { FontFamily(androidx.compose.ui.text.font.Typeface(typeface)) }
    val grain = remember { if (Build.VERSION.SDK_INT >= 33) ShredBlurNumberGrain() else null }

    // The frame clock, in nanoseconds; read only in draw and layer blocks.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // True from a touch until the number is back at rest and the loop has started over.
    var manual by remember { mutableStateOf(false) }
    val amount = remember { Tween(0f) }
    val blur = remember { Tween(0f) }
    val drift = remember { Tween(0f) }
    val focusMix = remember { Tween(0f) }
    var lead by remember { mutableFloatStateOf(0f) }
    var focusX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun state(at: Long): ShredState {
        if (manual) return ShredState(amount.value(at), lead, blur.value(at))
        if (!auto || reduced) return REST
        val t = (((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f)
        return stateAt(t)
    }

    /** Seconds into the loop (0 under reduced motion). */
    fun loopTime(at: Long): Float = if (reduced) 0f else (((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f)

    LaunchedEffect(reducedMotion, manual) {
        if (reducedMotion && !manual) return@LaunchedEffect
        while (true) withFrameNanos { clock.longValue = it.coerceAtLeast(clock.longValue) }
    }

    // 2 s after the last touch the number eases back to rest, and the loop starts over.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay((USER_HOLD * 1000).toLong())
        if (dragging) return@LaunchedEffect
        val now = clock.longValue
        amount.retarget(0f, now, SETTLE, EASE_IN_OUT)
        blur.retarget(0f, now, SETTLE, EASE_IN_OUT)
        drift.retarget(0f, now, SETTLE, EASE_IN_OUT)
        delay((SETTLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        origin = clock.longValue
        manual = false
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val mono = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BACKGROUND)
            .pointerInput(reducedMotion) {
                if (reducedMotion) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val now = clock.longValue
                    val current = state(now)
                    manual = true
                    dragging = true
                    amount.set(current.amount)
                    blur.set(current.blur)
                    lead = current.lead
                    drift.set(0f)
                    focusMix.set(0f)
                    focusMix.retarget(1f, now, 0.3f, EASE_OUT)
                    touches++
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    var atFull = false
                    var atBlur = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val at = clock.longValue
                        val dx = (change.position.x - down.position.x) / density.density
                        val dy = (change.position.y - down.position.y) / density.density
                        focusX = change.position.x / density.density
                        val a = min(1f, 0.45f + abs(dx) / 200f)
                        val b = min(1f, abs(dy) / 240f)
                        amount.retarget(a, at, 0.18f, EASE_OUT)
                        blur.retarget(b, at, 0.18f, EASE_OUT)
                        drift.retarget(dx * 0.35f, at, 0.18f, EASE_OUT)
                        // A tap when the strips are all out, and when the blur is full.
                        if (a >= 1f && !atFull) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (b >= 1f && !atBlur) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        atFull = a >= 1f
                        atBlur = b >= 1f
                    }
                    dragging = false
                    touches++
                }
            },
    ) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val glyphs = remember(number, typeface, widthDp, heightDp) {
            val probe = outline(number, typeface, 100f)
            val size = floor(min(100f * (widthDp - 48f) / maxOf(probe.width, 1f), 100f * (heightDp * 0.3f) / maxOf(probe.height, 1f)))
            outline(number, typeface, size * density.density)
        }
        val glyphPath = remember(glyphs) { glyphs.path.asComposePath() }
        val numberHeight = with(density) { glyphs.height.toDp() } + (2 * PAD).dp

        Column(Modifier.fillMaxSize()) {
            // Header
            Column(Modifier.padding(top = insets.calculateTopPadding() + 12.dp).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(brand, style = mono.copy(color = INK))
                    Spacer(Modifier.weight(1f))
                    Text(since, style = mono.copy(color = INK))
                }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.5.dp).background(INK))
            }

            Spacer(Modifier.weight(1f))

            // The number: an opaque pink canvas with the glyphs, drawn strip by strip while it is
            // shredded, then zoomed and blurred.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(numberHeight)
                    .semantics { contentDescription = number }
                    .graphicsLayer {
                        val s = state(clock.longValue)
                        val zoom = 1 + ZOOM * s.blur
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0.68f, 0.5f)
                        clip = false
                        // Swift blurs after the zoom; here the blur is zoomed with the layer.
                        renderEffect = if (s.blur > 0.001f && Build.VERSION.SDK_INT >= 31) {
                            val r = sigmaToRadius(MAX_BLUR * s.blur / zoom * density.density)
                            RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL).asComposeRenderEffect()
                        } else {
                            null
                        }
                        // Without a blur, fade a little instead.
                        alpha = if (Build.VERSION.SDK_INT >= 31) 1f else 1f - 0.5f * s.blur
                    }
                    .drawBehind {
                        val at = clock.longValue
                        val s = state(at)
                        val d = density.density
                        val w = size.width / d
                        val h = size.height / d
                        val phase = 2f * PI.toFloat() * loopTime(at) / LOOP
                        val mix = if (manual) focusMix.value(at) else 0f
                        val shift = if (manual) drift.value(at) else 0f
                        drawRect(BACKGROUND)
                        val left = (size.width - glyphs.width) / 2
                        val top = PAD * d
                        if (s.amount <= 0.0005f) {
                            translate(left, top) { drawPath(glyphPath, INK) }
                            return@drawBehind
                        }
                        val sw = w / STRIPS
                        for (i in 0 until STRIPS) {
                            val st = strip(i, w, h, s.amount, s.lead, focusX, mix, phase, shift)
                            clipRect(i * sw * d, 0f, (i + 1) * sw * d, size.height) {
                                // The inverse of the strip's mapping: x = c + (xs - cx) / span,
                                // y = h/2 + yoff + (ys - h/2) * stretch.
                                withTransform({
                                    translate(st.c * d, (h / 2 + st.yoff) * d)
                                    scale(1f / st.span, st.stretch, Offset.Zero)
                                    translate(-st.cx * d + left, -h / 2 * d + top)
                                }) {
                                    drawPath(glyphPath, INK)
                                }
                            }
                        }
                    },
            )

            Spacer(Modifier.weight(1f))

            // Footer
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = insets.calculateBottomPadding() + 16.dp),
            ) {
                Text(kicker, style = mono.copy(color = INK.copy(alpha = 0.7f)))
                Text(
                    headline,
                    modifier = Modifier.padding(top = 10.dp),
                    style = TextStyle(fontSize = 30.sp, fontFamily = family, letterSpacing = (-0.6).sp, lineHeight = 30.6.sp, color = INK),
                )
                Box(Modifier.padding(top = 20.dp).fillMaxWidth().height(1.5.dp).background(INK))
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dates, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = INK))
                        Text(venue, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.7f)))
                    }
                    Box(
                        Modifier
                            .height(48.dp)
                            .background(INK, CircleShape)
                            .padding(horizontal = 24.dp)
                            .semantics { role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(action, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BACKGROUND))
                    }
                }
                Text(
                    hint,
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.55f), textAlign = TextAlign.Center),
                )
            }
        }

        // Film grain: faint all the time (so the poster is never quite still), strong while the
        // number is blurred.
        if (grain != null && Build.VERSION.SDK_INT >= 33) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val at = clock.longValue
                        val s = state(at)
                        drawRect(grain.update(density.density, GRAIN + (BLUR_GRAIN - GRAIN) * s.blur, floor(loopTime(at) * 24f)))
                    },
            )
        }
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun ShredBlurNumberSystemBars() {
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
