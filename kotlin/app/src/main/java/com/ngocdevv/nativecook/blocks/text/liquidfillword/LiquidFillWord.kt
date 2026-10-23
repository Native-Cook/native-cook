package com.ngocdevv.nativecook.blocks.text.liquidfillword

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.MutableLongState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// A big word filled with three flat liquids. The letters are a mask over a field coloured by
// level: lime at the bottom, green above it and salmon on top, each with its own wavy surface,
// plus green blobs (salmon at their cores) swirling in the lime near its surface. The letter edges
// wobble a little, like a meniscus. Dragging up or down pours or drains it. A port of the SwiftUI
// block, number for number.
//
// autoPlay timeline (LOOP = 8 s), levels from 0 (bottom of the descender) to 1 (top of the
// ascenders); 1.15 is brim-full and -0.08 empty:
//   0.0-2.0   full and resting: lime, with green blobs swirling near the top
//   2.0-4.0   drains (ease-in-out cubic), only the lime showing; the blobs fade out by 2.8
//   4.0-4.5   empty: only the faint glass of the letters
//   4.5-7.1   pours: salmon first (4.5 + 1.6 s), green behind it (4.8 + 1.8 s), lime last
//             (5.1 + 2.0 s), each ease-in-out cubic; the blobs come back 6.4-7.6
//   7.1-8.0   full and resting again
// The surfaces slosh harder while the level moves (slosh 0.35 -> 1, a sine bump over each move).
// The swirl's phase runs once around per loop, so one period repeats exactly.

/** Seconds. */
private const val LOOP = 8f
private const val FULL = 1.15f
private const val EMPTY = -0.08f
private const val DRAIN_AT = 2f
private const val DRAIN_FOR = 2f
private val SALMON_POUR = 4.5f to 1.6f
private val GREEN_POUR = 4.8f to 1.8f
private val LIME_POUR = 5.1f to 2.0f
/** While draining, green and salmon sit this far under the lime, hidden. */
private const val HIDDEN = 0.25f
/** While a finger sets the level, green and salmon ride this far above the lime. */
private const val GREEN_BAND = 0.06f
private const val SALMON_BAND = 0.12f
/** Dp of drag for the whole range. */
private const val DRAG_RANGE = 300f
private const val USER_HOLD = 2f
private const val SETTLE = 1.2f

private val BACKGROUND = Color(0xFF93B0A1)
private val INK = Color(0xFF1F3329)
private val LIME = Color(0xFFCDD98F)
private val GREEN = Color(0xFF1DA07F)
private val SALMON = Color(0xFFE8967A)
/** The empty letters: a shade darker than the backdrop. */
private val GHOST = Color(0xFF86A495)

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private class Liquid(val lime: Float, val green: Float, val salmon: Float, val blobs: Float, val slosh: Float)

private val REST = Liquid(FULL, FULL, FULL, 1f, 0.35f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun level(t: Float, pour: Pair<Float, Float>) = EMPTY + (FULL - EMPTY) * easeInOutCubic(clamp01((t - pour.first) / pour.second))
private fun bump(t: Float, a: Float, b: Float) = sin(PI.toFloat() * clamp01((t - a) / (b - a)))

/** The liquids `t` seconds into the loop. */
private fun liquidAt(t: Float): Liquid {
    val slosh = 0.35f + 0.65f * (bump(t, 2.0f, 4.3f) + bump(t, 4.5f, 7.6f))
    val blobs = if (t < 4.25f) 1 - easeInOutCubic(clamp01((t - DRAIN_AT) / 0.8f)) else easeInOutCubic(clamp01((t - 6.4f) / 1.2f))
    if (t < 4.25f) {
        val l = FULL + (EMPTY - FULL) * easeInOutCubic(clamp01((t - DRAIN_AT) / DRAIN_FOR))
        return Liquid(l, l - HIDDEN, l - HIDDEN, blobs, slosh)
    }
    return Liquid(level(t, LIME_POUR), level(t, GREEN_POUR), level(t, SALMON_POUR), blobs, slosh)
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
}

// The same function as the Metal one, in AGSL, in pixels (`scale` turns them into dp).
private const val FILL_AGSL = """
uniform shader layer;
uniform float scale;
uniform float phase;
uniform float lime;
uniform float green;
uniform float salmon;
uniform float blobs;
uniform float slosh;
uniform float bottomY;
uniform float topY;
layout(color) uniform half4 limeColor;
layout(color) uniform half4 greenColor;
layout(color) uniform half4 salmonColor;
layout(color) uniform half4 ghostColor;

half4 main(float2 fragCoord) {
    float2 p = fragCoord / scale;
    float ph = phase;
    float2 w = float2(
        sin(p.y * 0.31 + p.x * 0.07 + 2.0 * ph) + 0.25 * sin(p.y * 0.73 - p.x * 0.19 - 3.0 * ph),
        cos(p.x * 0.27 - p.y * 0.05 + 3.0 * ph) + 0.25 * cos(p.x * 0.61 + p.y * 0.23 + 2.0 * ph)
    ) * 1.0;
    float a = float(layer.eval((p + w) * scale).a);
    float edge = smoothstep(0.35, 0.65, a);
    if (edge <= 0.0) { return half4(0.0); }

    float h = bottomY - topY;
    float u = (bottomY - p.y) / h;
    float aa = 0.9 / h;
    float amp = 0.025 + 0.045 * slosh;
    float wl = amp * (0.6 * sin(p.x * 0.045 + 2.0 * ph) + 0.4 * sin(p.x * 0.11 - 3.0 * ph + 1.3));
    float wg = amp * (0.6 * sin(p.x * 0.05 - 2.0 * ph + 2.1) + 0.4 * sin(p.x * 0.13 + 3.0 * ph + 0.4));
    float ws = amp * (0.6 * sin(p.x * 0.04 + 3.0 * ph + 4.0) + 0.4 * sin(p.x * 0.12 - 2.0 * ph + 2.6));
    float cl = 1.0 - smoothstep(lime + wl - aa, lime + wl + aa, u);
    float cg = 1.0 - smoothstep(green + wg - aa, green + wg + aa, u);
    float cs = 1.0 - smoothstep(salmon + ws - aa, salmon + ws + aa, u);

    float3 col = float3(ghostColor.rgb);
    col = mix(col, float3(salmonColor.rgb), cs);
    col = mix(col, float3(greenColor.rgb), cg);
    col = mix(col, float3(limeColor.rgb), cl);

    float b = sin(p.x * 0.05 + 2.0 * ph) + sin(p.y * 0.07 - ph + p.x * 0.025) + sin((p.x - p.y) * 0.04 + 3.0 * ph);
    float near = smoothstep(lime - 1.05, lime - 0.45, u);
    float tg = 3.1 - 2.4 * blobs * near;
    col = mix(col, float3(greenColor.rgb), smoothstep(tg - 0.04, tg + 0.04, b) * cl);
    float ts = 3.1 - 1.2 * blobs * near;
    col = mix(col, float3(salmonColor.rgb), smoothstep(ts - 0.04, ts + 0.04, b) * cl);

    return half4(half3(col * edge), half(edge));
}
"""

@RequiresApi(33)
private class LiquidFillWordShader {
    val shader = RuntimeShader(FILL_AGSL).apply {
        setColorUniform("limeColor", LIME.toArgb())
        setColorUniform("greenColor", GREEN.toArgb())
        setColorUniform("salmonColor", SALMON.toArgb())
        setColorUniform("ghostColor", GHOST.toArgb())
    }

    fun effect(scale: Float, phase: Float, liquid: Liquid, slosh: Float, bottomY: Float, topY: Float): RenderEffect {
        shader.setFloatUniform("scale", scale)
        shader.setFloatUniform("phase", phase)
        shader.setFloatUniform("lime", liquid.lime)
        shader.setFloatUniform("green", liquid.green)
        shader.setFloatUniform("salmon", liquid.salmon)
        shader.setFloatUniform("blobs", liquid.blobs)
        shader.setFloatUniform("slosh", slosh)
        shader.setFloatUniform("bottomY", bottomY)
        shader.setFloatUniform("topY", topY)
        return RenderEffect.createRuntimeShaderEffect(shader, "layer")
    }
}

/** The word's outline in px with its ink box's top-left corner at the origin, and that box. */
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

/**
 * @param autoPlay Drains and pours the word by itself, every [LOOP] seconds.
 */
@Composable
fun LiquidFillWord(
    modifier: Modifier = Modifier,
    word: String = "Liquid",
    label: String = "Fluid type",
    issue: String = "No. 07",
    caption: String = "Three liquids, one level.",
    levelLabel: String = "Level",
    hint: String = "Drag up or down to pour",
    autoPlay: Boolean = false,
) {
    LiquidFillWordSystemBars()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val auto by rememberUpdatedState(autoPlay)

    val typeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "liquidfillword/liquidfillword_PaytoneOne-Regular.ttf") }
            .getOrDefault(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
    }
    val family = remember(typeface) { FontFamily(androidx.compose.ui.text.font.Typeface(typeface)) }
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) LiquidFillWordShader() else null }

    // The frame clock, in nanoseconds; read only in draw and layer blocks and in the readout.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // True from a touch until the level is back at rest and the loop has started over.
    var manual by remember { mutableStateOf(false) }
    val level = remember { Tween(FULL) }
    val blobs = remember { Tween(1f) }
    val slosh = remember { Tween(0.35f) }
    var dragging by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun liquid(at: Long): Liquid {
        if (manual) {
            val l = level.value(at)
            return Liquid(l, l + GREEN_BAND, l + SALMON_BAND, blobs.value(at), slosh.value(at))
        }
        if (!auto || reduced) return REST
        val t = (((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f)
        return liquidAt(t)
    }

    LaunchedEffect(reducedMotion, manual) {
        if (reducedMotion && !manual) return@LaunchedEffect
        while (true) withFrameNanos { clock.longValue = it.coerceAtLeast(clock.longValue) }
    }

    // 2 s after the last touch the word fills back up, and the loop starts over from rest.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay((USER_HOLD * 1000).toLong())
        if (dragging) return@LaunchedEffect
        val now = clock.longValue
        level.retarget(FULL, now, SETTLE, EASE_IN_OUT)
        blobs.retarget(1f, now, SETTLE, EASE_IN_OUT)
        slosh.retarget(0.35f, now, SETTLE, EASE_IN_OUT)
        delay((SETTLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        origin = clock.longValue
        manual = false
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BACKGROUND)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val now = clock.longValue
                    val from = liquid(now).lime
                    manual = true
                    dragging = true
                    level.retarget(from, now, 0f, EASE_OUT)
                    blobs.retarget(0.5f, now, 0.4f, EASE_OUT)
                    slosh.retarget(1f, now, 0.4f, EASE_OUT)
                    touches++
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    var dy = 0f
                    var atLimit = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dy += change.positionChange().y / density.density
                        change.consume()
                        val target = (from - dy / DRAG_RANGE).coerceIn(EMPTY, FULL)
                        level.retarget(target, clock.longValue, 0.18f, EASE_OUT)
                        // A tap at either end of the range.
                        val limit = target >= FULL || target <= EMPTY
                        if (limit && !atLimit) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        atLimit = limit
                    }
                    dragging = false
                    touches++
                }
            },
    ) {
        val fullWidthPx = constraints.maxWidth.toFloat()
        val outline = remember(word, typeface, fullWidthPx) {
            val probe = outline(word, typeface, 100f).width
            val sizePx = min(110 * density.density, floor(100f * (fullWidthPx - 48 * density.density) / max(probe, 1f)))
            outline(word, typeface, sizePx)
        }
        val padPx = 24 * density.density
        val wordHeight = with(density) { (outline.height + 2 * padPx).toDp() }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier
                    .padding(top = insets.calculateTopPadding() + 14.dp)
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth(),
            ) {
                Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = INK))
                Spacer(Modifier.weight(1f))
                Text(issue, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.55f), fontFeatureSettings = "tnum"))
            }

            Spacer(Modifier.weight(1f))

            // The word: drawn white over a faint veil and coloured by the shader (API 33+), or
            // filled flat to its level below that.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(wordHeight)
                    .semantics { contentDescription = word }
                    .graphicsLayer {
                        if (shader != null && Build.VERSION.SDK_INT >= 33) {
                            val now = clock.longValue
                            val l = liquid(now)
                            val phase = if (reduced) 0f else (2 * PI * ((now / 1e9) % LOOP) / LOOP).toFloat()
                            renderEffect = shader.effect(
                                density.density, phase, l, if (reduced) 0f else l.slosh,
                                (padPx + outline.height) / density.density, padPx / density.density,
                            ).asComposeRenderEffect()
                        }
                    }
                    .drawBehind {
                        val left = (size.width - outline.width) / 2
                        val path = outline.path.asComposePath()
                        if (shader != null) {
                            // A veil over the whole layer, under the shader's edge threshold (0.35), so
                            // the shader sees the whole canvas.
                            drawRect(Color.White.copy(alpha = 0.2f))
                            translate(left, padPx) { drawPath(path, Color.White) }
                        } else {
                            val l = liquid(clock.longValue)
                            translate(left, padPx) {
                                drawPath(path, GHOST)
                                val surface = outline.height * (1 - clamp01(l.lime))
                                clipRect(0f, surface, outline.width, outline.height) {
                                    drawPath(path, LIME)
                                }
                            }
                        }
                    },
            )

            Text(
                caption,
                modifier = Modifier.padding(top = 6.dp).padding(horizontal = 28.dp),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.7f), textAlign = TextAlign.Center),
            )

            Spacer(Modifier.weight(1f))

            LiquidFillWordMeter(
                levelLabel, hint, family, clock,
                fraction = { clamp01(liquid(it).lime) },
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = insets.calculateBottomPadding() + 28.dp),
            )
        }
    }
}

@Composable
private fun LiquidFillWordMeter(
    levelLabel: String,
    hint: String,
    family: FontFamily,
    clock: MutableLongState,
    fraction: (Long) -> Float,
    modifier: Modifier = Modifier,
) {
    // Only this readout recomposes as the level moves.
    val f = fraction(clock.longValue)
    val percent = (f * 100).roundToInt()
    Column(modifier.fillMaxWidth().semantics { contentDescription = "$levelLabel $percent percent" }) {
        Text(levelLabel.uppercase(), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = INK.copy(alpha = 0.6f)))
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$percent", style = TextStyle(fontSize = 64.sp, fontFamily = family, color = INK), modifier = Modifier.alignByBaseline())
            Text("%", style = TextStyle(fontSize = 26.sp, fontFamily = family, color = INK), modifier = Modifier.alignByBaseline())
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(4.dp)
                .background(INK.copy(alpha = 0.12f), CircleShape),
        ) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(INK, CircleShape))
        }
        Text(hint, modifier = Modifier.padding(top = 14.dp), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.5f)))
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun LiquidFillWordSystemBars() {
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
