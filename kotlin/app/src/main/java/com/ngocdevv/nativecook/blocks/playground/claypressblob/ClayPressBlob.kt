package com.ngocdevv.nativecook.blocks.playground.claypressblob

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** One clay a tap can recolour the blob to. */
data class ClayColour(val name: String, val hex: Long)

private val Palette = listOf(
    ClayColour("Terracotta", 0xFFD2623B),
    ClayColour("Cobalt", 0xFF3954B8),
    ClayColour("Moss", 0xFF6C9858),
    ClayColour("Marigold", 0xFFE3A12F),
    ClayColour("Rose", 0xFFDC7290),
    ClayColour("Lilac", 0xFF8E74CC),
)

// The studio: a seamless paper sweep tinted with the clay's own hue (a tonal product shot), lit
// from the upper left, with a fine grain. Its tones are the clay's hue at these saturations and
// lightnesses (a share of the clay's saturation, then lightness 0…1).
private class PaperTone(val s: Double, val l: Double)
private val PaperTop = PaperTone(0.34, 0.86)
private val PaperMid = PaperTone(0.4, 0.91)
private val PaperFloor = PaperTone(0.3, 0.8)
private val PaperInk = PaperTone(0.45, 0.24)
private const val GRAIN = 0.05f
// The stage the clay can be thrown around in, inside the header and the hint.
private const val HEADER = 124.0
private const val FOOTER = 66.0
// The header: the kicker line, then the clay's name, large, in a sturdy serif.
private const val NAME_SIZE = 46f
// The name's crossfade when the clay changes colour (Reanimated's FadeIn after a delay / FadeOut).
private const val LABEL_IN = 0.3
private const val LABEL_DELAY = 0.12
private const val LABEL_OUT = 0.18
// Contact shadows, in the paper's deep tone (alphas), and a satin sheen, not a gloss.
private const val SHADOW_WIDE = 0.24
private const val SHADOW_TIGHT = 0.36
private const val BOUNCE = 0.22
private const val SHEEN_BROAD = 0.22f
private const val SHEEN_HOT = 0.6f

// Light comes from the upper left: the inner rim darkens toward the lower right and brightens
// toward the upper left; the sheen sits on the ring point at SHEEN_ANGLE degrees, SHEEN_IN of the
// way in from the centre.
private const val SHEEN_ANGLE = -125.0
private const val SHEEN_IN = 0.56
// Each fingerprint has RIDGES loops, spaced RIDGE_STEP of the dent's radius.
private const val RIDGES = 6
private const val RIDGE_STEP = 0.12f

// autoPlay (seconds). Each stroke is one finger, placed in units of the clay's radius from its
// home: down at `at`, moving from `from` to `to` over `move` of its `dur`, up at the end. A stroke
// with `tap` recolours the clay to that entry instead of cycling.
private const val LOOP = 14.0

private class Stroke(
    val at: Double,
    val dur: Double,
    val fromX: Double,
    val fromY: Double,
    val toX: Double,
    val toY: Double,
    val move: Double,
    val easeIn: Boolean,
    val tap: Int = -1,
)

private val Script = listOf(
    // Two pokes that leave dents, then one on the right rim that notches it.
    Stroke(0.6, 0.6, 0.3, -0.34, 0.3, -0.34, 1.0, false),
    Stroke(1.7, 0.5, -0.36, 0.28, -0.36, 0.28, 1.0, false),
    Stroke(2.7, 0.6, 0.86, 0.14, 0.86, 0.14, 1.0, false),
    // A pull from the upper right, held, then let go.
    Stroke(4.0, 1.05, 0.72, -0.62, 1.18, -0.98, 0.72, false),
    // A tap to cobalt.
    Stroke(5.8, 0.09, 0.04, -0.06, 0.04, -0.06, 1.0, false, tap = 1),
    // A flick to the right: it squashes against the wall and wobbles back.
    Stroke(7.0, 0.15, -0.18, 0.1, 0.56, 0.02, 1.0, true),
    // A pinch across the middle, held, then let go.
    Stroke(9.2, 0.95, -0.9, 0.0, -0.68, 0.0, 0.5, false),
    Stroke(9.2, 0.95, 0.9, 0.0, 0.68, 0.0, 0.5, false),
    // A tap back to terracotta.
    Stroke(11.0, 0.09, -0.06, 0.05, -0.06, 0.05, 1.0, false, tap = 0),
)
private const val SCRIPT_ID = 1000L
// After a touch the loop waits this long, then starts over.
private const val USER_HOLD = 3.0

// Grain on paper, mottling in clay: per-half-point noise plus soft value noise, as light and dark
// specks whose strength is built into the colour.
private const val GRAIN_AGSL = """
uniform float strength;
uniform float scale;
uniform float mottle;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float v = (hash(floor(p * 2.0)) - 0.5) * 0.7 + (noise(p / scale) - 0.5) * mottle;
  float a = clamp(abs(v) * strength, 0.0, 1.0);
  return v > 0.0 ? half4(a, a, a, a) : half4(0.0, 0.0, 0.0, a);
}"""

// A paint's colour scales what its shader draws, so a shaded paint starts from opaque black.
private const val SHADED = 0xFF000000.toInt()

private fun clamp01(v: Double) = min(1.0, max(0.0, v))

private fun easeInOut(p: Double): Double {
    val t = clamp01(p)
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

/** Reanimated's default timing curve, ease in and out (quadratic). */
private fun quadInOut(p: Double): Double {
    val t = clamp01(p)
    return if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
}

private class Hsl(val h: Double, val s: Double, val l: Double)

private fun hexToHsl(hex: Long): Hsl {
    val r = ((hex shr 16) and 255) / 255.0
    val g = ((hex shr 8) and 255) / 255.0
    val b = (hex and 255) / 255.0
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    if (mx == mn) return Hsl(0.0, 0.0, l)
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    var h = when (mx) {
        r -> (g - b) / d + (if (g < b) 6 else 0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    h *= 60
    return Hsl(h, s, l)
}

/** A colour from hue (degrees), saturation and lightness (0…1), with alpha, as an ARGB int. */
private fun hsla(h: Double, s: Double, l: Double, a: Double): Int {
    val c = rgba(h, s, l, a)
    return Color(c[0].toFloat(), c[1].toFloat(), c[2].toFloat(), c[3].toFloat()).toArgb()
}

/** The same colour as four numbers 0…1, for mixing. */
private fun rgba(h: Double, s: Double, l: Double, a: Double): DoubleArray {
    val hh = ((h % 360) + 360) % 360 / 60
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs(hh % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        hh < 1 -> Triple(c, x, 0.0)
        hh < 2 -> Triple(x, c, 0.0)
        hh < 3 -> Triple(0.0, c, x)
        hh < 4 -> Triple(0.0, x, c)
        hh < 5 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    return doubleArrayOf(
        (r + m).coerceIn(0.0, 1.0),
        (g + m).coerceIn(0.0, 1.0),
        (b + m).coerceIn(0.0, 1.0),
        a.coerceIn(0.0, 1.0),
    )
}

/** Two colours mixed `p` of the way, straight through RGB (no detour through other hues). */
private fun mixRgb(a: DoubleArray, b: DoubleArray, p: Double): Int = Color(
    (a[0] + (b[0] - a[0]) * p).toFloat().coerceIn(0f, 1f),
    (a[1] + (b[1] - a[1]) * p).toFloat().coerceIn(0f, 1f),
    (a[2] + (b[2] - a[2]) * p).toFloat().coerceIn(0f, 1f),
    (a[3] + (b[3] - a[3]) * p).toFloat().coerceIn(0f, 1f),
).toArgb()

/** The tones a clay is lit with; mid-change, two clays' tones mixed `p` of the way. */
private class Tones(val from: Hsl, val to: Hsl = from, val p: Double = 0.0) {
    private inline fun tone(f: (Hsl) -> DoubleArray) = mixRgb(f(from), f(to), p)
    fun base(a: Double) = tone { rgba(it.h, it.s, it.l, a) }
    fun light(a: Double) = tone { rgba(it.h - 6, it.s * 0.8, it.l + (0.96 - it.l) * 0.5, a) }
    fun shade(a: Double) = tone { rgba(it.h + 8, min(1.0, it.s * 1.08), it.l * 0.56, a) }
    fun deep(a: Double) = tone { rgba(it.h + 12, it.s, it.l * 0.34, a) }
}

/** The paper tone of a clay: its hue at a share `t.s` of its saturation and lightness `t.l`. */
private fun paper(c: Hsl, t: PaperTone, a: Double = 1.0) = rgba(c.h, min(1.0, c.s * t.s), t.l, a)

/** The paper tone between two clays, `p` of the way, as an ARGB int. */
private fun paperMix(a: Hsl, b: Hsl, t: PaperTone, p: Double, alpha: Double = 1.0) = mixRgb(paper(a, t, alpha), paper(b, t, alpha), p)

/** The clay's state between frames: the simulation, the autoPlay clock and the colour label. */
private class ClayMotion {
    var sim: ClaySim? = null
    var clock = 0.0
    var looping = false
    var hold = 0.0
    val strokeDown = BooleanArray(Script.size)
    var label = 0
    var previousLabel = -1
    var elapsed = 0.0
    var labelAt = -10.0
}

/** Grain on API 33+ is the React Native shader in AGSL; below that, a tiled bitmap of its fine speckle. */
private class ClayGrain(strength: Float, scale: Float, mottle: Float) {
    val shader: Shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(GRAIN_AGSL).apply {
            setFloatUniform("strength", strength)
            setFloatUniform("scale", scale)
            setFloatUniform("mottle", mottle)
        }
    } else {
        speckle(strength)
    }
    private val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Matrix() else Matrix().apply { setScale(0.5f, 0.5f) }
    private val matrix = Matrix()

    /** Pins the pattern at (x, y), in the canvas's dp units. */
    fun at(x: Float, y: Float): Shader {
        matrix.set(base)
        matrix.postTranslate(x, y)
        shader.setLocalMatrix(matrix)
        return shader
    }

    private fun speckle(strength: Float): Shader {
        val n = 128
        val pixels = IntArray(n * n)
        for (j in 0 until n) for (i in 0 until n) {
            val h = sin(i * 127.1 + j * 311.7) * 43758.5453
            val v = (h - floor(h) - 0.5) * 0.7
            val a = (abs(v) * strength).coerceIn(0.0, 1.0)
            val alpha = (a * 255).roundToInt()
            pixels[j * n + i] = if (v > 0) (alpha shl 24) or 0xFFFFFF else alpha shl 24
        }
        val bitmap = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}

/** The paints and paths the scene reuses every frame. */
private class ClayPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val body = Path()
    val outside = Path().apply { fillType = Path.FillType.EVEN_ODD }
    val loops = Path()
    val rect = RectF()
    val paper = ClayGrain(GRAIN, 140f, 0.5f)
    val clay = ClayGrain(0.09f, 16f, 0.28f)
    private val blurs = HashMap<Float, BlurMaskFilter>()

    fun blur(sigma: Float): BlurMaskFilter? =
        if (sigma <= 0f) null else blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

    /** A plain paint of `color`, blurred by a Skia-style sigma. */
    fun solid(color: Int, sigma: Float): Paint = fill.apply {
        reset()
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
        maskFilter = blur(sigma)
    }
}

/**
 * A glossy blob of modelling clay on a pale paper studio sweep, lit from the upper left. It is a
 * soft body that keeps what you do to it for a while: a press sinks a dent with a fingerprint in
 * it, a drag pulls out a lump, a flick throws it against the edge of the screen where it flattens
 * and wobbles back, and a pinch squeezes it. A tap recolours the clay from the finger.
 */
@Composable
fun ClayPressBlob(
    modifier: Modifier = Modifier,
    /** The clays a tap cycles through, in order. */
    palette: List<ClayColour> = Palette,
    /** Pokes, pulls, recolours, throws and pinches the clay on a loop. */
    autoPlay: Boolean = false,
    /** Called with a clay when a tap recolours it. */
    onRecolour: ((ClayColour) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    // The palette, never empty: an empty one falls back to the six clays.
    val clays = palette.ifEmpty { Palette }
    val hsl = remember(clays) { clays.map { hexToHsl(it.hex) } }
    val motion = remember { ClayMotion() }
    val paints = remember { ClayPaints() }
    // Bumped once a frame; only what reads it (the canvas, the header's ink) draws again.
    val tick = remember { mutableIntStateOf(0) }
    val recolourCallback by rememberUpdatedState(onRecolour)

    fun feel(events: Int) {
        val constant = when {
            events and CLAY_EVENT_THUD != 0 -> HapticFeedbackConstants.VIRTUAL_KEY
            events and CLAY_EVENT_RECOLOUR != 0 -> HapticFeedbackConstants.CLOCK_TICK
            events and CLAY_EVENT_PRESS != 0 -> HapticFeedbackConstants.CLOCK_TICK
            events and CLAY_EVENT_RELEASE != 0 -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> return
        }
        view.performHapticFeedback(constant)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(paperMix(hsl[motion.label.coerceIn(0, hsl.size - 1)], hsl[motion.label.coerceIn(0, hsl.size - 1)], PaperMid, 0.0)))
            .semantics { contentDescription = "Clay study, ${clays[motion.label.coerceIn(0, clays.size - 1)].name} clay" },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = safeTop + HEADER
        val bottom = height - safeBottom - FOOTER

        LaunchedEffect(auto) {
            motion.looping = auto
            motion.clock = 0.0
            motion.hold = 0.0
        }

        LaunchedEffect(width, height, top, bottom, reducedMotion) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 30)
                    last = now
                    if (width <= 0 || dt <= 0) return@withFrameNanos
                    var s = motion.sim
                    if (s == null || s.right != width - 20 || s.bottom != bottom || s.top != top) {
                        // A new stage (first frame, a new layout) lays a fresh body down in the colour it had.
                        val kept = (s?.colourTo ?: 0).coerceIn(0, hsl.size - 1)
                        s = ClaySim(width, height, top, bottom, hsl.size, reducedMotion)
                        s.colourFrom = kept
                        s.colourTo = kept
                        motion.sim = s
                    }
                    motion.elapsed += dt
                    // The script: each stroke's finger is down while the loop is inside it.
                    if (motion.hold > 0) {
                        motion.hold -= dt
                        if (motion.hold <= 0) motion.clock = 0.0
                    } else if (motion.looping) {
                        val t = (motion.clock + dt) % LOOP
                        motion.clock = t
                        Script.forEachIndexed { i, k ->
                            val u = (t - k.at) / k.dur
                            val m = clamp01(u / k.move)
                            val e = if (k.easeIn) m * m else easeInOut(m)
                            val x = s.homeX + (k.fromX + (k.toX - k.fromX) * e) * s.r0
                            val y = s.homeY + (k.fromY + (k.toY - k.fromY) * e) * s.r0
                            if (u >= 0 && u < 1) {
                                if (!motion.strokeDown[i]) {
                                    s.touchDown(SCRIPT_ID + i, x, y, true, k.tap)
                                    motion.strokeDown[i] = true
                                } else {
                                    s.touchMove(SCRIPT_ID + i, x, y)
                                }
                            } else if (motion.strokeDown[i]) {
                                s.touchMove(SCRIPT_ID + i, x, y)
                                s.touchUp(SCRIPT_ID + i, false)
                                motion.strokeDown[i] = false
                            }
                        }
                    }
                    s.step(dt)
                    if (s.events != 0) {
                        if (s.events and CLAY_EVENT_RECOLOUR != 0) {
                            if (s.colourTo != motion.label) {
                                motion.previousLabel = motion.label
                                motion.label = s.colourTo
                                motion.labelAt = motion.elapsed
                            }
                            if (s.eventsByUser) clays.getOrNull(s.colourTo)?.let { recolourCallback?.invoke(it) }
                        }
                        if (s.eventsByUser) feel(s.events)
                        s.events = 0
                    }
                    tick.intValue++
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Every finger, from the moment it lands, so a press starts at once.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var first = true
                        do {
                            val event = if (first) currentEvent else awaitPointerEvent()
                            first = false
                            val s = motion.sim
                            for (c in event.changes) {
                                if (s == null) continue
                                val px = c.position.x / density.density.toDouble()
                                val py = c.position.y / density.density.toDouble()
                                val id = c.id.value
                                when {
                                    c.pressed && !c.previousPressed -> {
                                        // A real touch pauses the loop and lifts autoPlay's fingers.
                                        motion.hold = USER_HOLD
                                        s.cancelScripted()
                                        motion.strokeDown.fill(false)
                                        s.touchDown(id, px, py, false, -1)
                                    }
                                    c.pressed -> {
                                        if (c.position != c.previousPosition) {
                                            motion.hold = USER_HOLD
                                            s.touchMove(id, px, py)
                                        }
                                    }
                                    c.previousPressed -> s.touchUp(id, false)
                                }
                                c.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            tick.intValue
            val s = motion.sim ?: return@Canvas
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                drawClayScene(nc, s, width.toFloat(), height.toFloat(), hsl, paints)
                nc.restore()
            }
        }

        ClayChrome(motion, clays, hsl, safeTop, safeBottom, tick)
    }
}

/** The header and the hint, in the paper's ink, which turns with the clay's colour change. */
@Composable
private fun BoxScope.ClayChrome(
    motion: ClayMotion,
    clays: List<ClayColour>,
    hsl: List<Hsl>,
    safeTop: Float,
    safeBottom: Float,
    tick: State<Int>,
) {
    // The ink turns with the paper, over the same 0.75 s as the clay's colour change.
    tick.value
    val inkSim = motion.sim
    val ink = Color(
        if (inkSim == null) {
            paperMix(hsl[motion.label.coerceIn(0, hsl.size - 1)], hsl[motion.label.coerceIn(0, hsl.size - 1)], PaperInk, 0.0)
        } else {
            paperMix(
                hsl[inkSim.colourFrom.coerceIn(0, hsl.size - 1)],
                hsl[inkSim.colourTo.coerceIn(0, hsl.size - 1)],
                PaperInk,
                easeInOut(inkSim.recolour),
            )
        },
    )

    // The header: the kicker line, then the clay's name, large, in a sturdy serif.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = (safeTop + 18).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val kicker = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.6.sp)
        Row(Modifier.fillMaxWidth().alpha(0.72f), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("CLAY STUDY", style = kicker)
            BasicText("Nº 04", style = kicker)
        }
        Box(Modifier.height((NAME_SIZE * 1.18f).dp)) {
            // The clay's name crossfades: the old one leaves over 180 ms, the new one arrives
            // over 300 ms after 120 ms.
            val since = motion.elapsed - motion.labelAt
            val previous = motion.previousLabel
            if (previous in clays.indices && since < LABEL_OUT) {
                ClayName(clays[previous], ink, Modifier.alpha(1f - quadInOut(since / LABEL_OUT).toFloat()))
            }
            ClayName(
                clays[motion.label.coerceIn(0, clays.size - 1)],
                ink,
                Modifier.alpha(quadInOut((since - LABEL_DELAY) / LABEL_IN).toFloat()),
            )
        }
    }

    BasicText(
        "Press · Pull · Flick · Pinch · Tap to recolour",
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 26).dp)
            .alpha(0.6f),
        style = TextStyle(
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun ClayName(colour: ClayColour, ink: Color, modifier: Modifier = Modifier) {
    BasicText(
        colour.name,
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            color = ink,
            fontSize = NAME_SIZE.sp,
            lineHeight = (NAME_SIZE * 1.18f).sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
    )
}

/** A smooth closed curve through the ring (Catmull-Rom as cubics), added to `path`. */
private fun traceRing(path: Path, s: ClaySim) {
    val n = CLAY_POINTS
    val x = s.x
    val y = s.y
    path.moveTo(x[0].toFloat(), y[0].toFloat())
    for (i in 0 until n) {
        val i0 = (i + n - 1) % n
        val i2 = (i + 1) % n
        val i3 = (i + 2) % n
        path.cubicTo(
            (x[i] + (x[i2] - x[i0]) / 6).toFloat(),
            (y[i] + (y[i2] - y[i0]) / 6).toFloat(),
            (x[i2] - (x[i3] - x[i]) / 6).toFloat(),
            (y[i2] - (y[i3] - y[i]) / 6).toFloat(),
            x[i2].toFloat(),
            y[i2].toFloat(),
        )
    }
    path.close()
}

private fun drawShifted(c: android.graphics.Canvas, path: Path, dx: Float, dy: Float, paint: Paint) {
    c.save()
    c.translate(dx, dy)
    c.drawPath(path, paint)
    c.restore()
}

/** One finger's dent: a soft hollow lit from the upper left, a raised lip, and fading ridges. */
private fun drawDent(
    c: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    depth: Double,
    ridge: Double,
    seed: Double,
    t: Tones,
    paints: ClayPaints,
) {
    val d = depth * depth * (3 - 2 * depth)
    if (d < 0.01) return
    val r = (CLAY_DENT_RADIUS * (0.6 + 0.4 * d)).toFloat()
    // Light direction, toward the upper left.
    val lx = -0.62f
    val ly = -0.78f
    // The lip: clay pushed up around the dent, lit on its outer upper-left side.
    val lip = paints.stroke.apply {
        strokeWidth = 7f
        maskFilter = paints.blur(2.6f)
        color = android.graphics.Color.BLACK
        shader = LinearGradient(
            cx + lx * (r + 4), cy + ly * (r + 4), cx - lx * (r + 4), cy - ly * (r + 4),
            intArrayOf(t.light(0.55 * d), t.light(0.0), t.shade(0.35 * d)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawCircle(cx, cy, r + 3, lip)
    lip.shader = null
    // The hollow: in shadow on the side facing the light, catching it on the far wall.
    val hollow = paints.solid(android.graphics.Color.BLACK, 3.2f).apply {
        shader = LinearGradient(
            cx + lx * r, cy + ly * r, cx - lx * r, cy - ly * r,
            intArrayOf(t.deep(0.5 * d), t.shade(0.22 * d), t.base(0.0), t.light(0.5 * d)),
            floatArrayOf(0f, 0.35f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawCircle(cx, cy, r, hollow)
    hollow.shader = null
    if (ridge < 0.02) return
    // The fingerprint: broken, slightly oval loops, embossed by a dark and a light copy.
    val a = ridge * ridge
    val loops = paints.loops
    loops.reset()
    for (k in 1..RIDGES) {
        val rr = r * (0.12f + k * RIDGE_STEP)
        val start = (seed * 360 + k * 47).toFloat()
        val sweep = (290 - (k % 2) * 46 - ((seed * 97 * k) % 30)).toFloat()
        paints.rect.set(-rr, -rr * 0.8f, rr, rr * 0.8f)
        loops.addArc(paints.rect, start, sweep)
    }
    val line = paints.stroke.apply {
        strokeWidth = 1.1f
        maskFilter = null
    }
    val turn = (seed * 180 - 30).toFloat()
    c.save()
    c.translate(cx + 0.7f, cy + 0.9f)
    c.rotate(turn)
    line.color = t.light(0.5 * a)
    c.drawPath(loops, line)
    c.restore()
    c.save()
    c.translate(cx - 0.4f, cy - 0.5f)
    c.rotate(turn)
    line.color = t.deep(0.34 * a)
    c.drawPath(loops, line)
    c.restore()
}

/** The whole scene, in dp: the studio sweep, the clay's shadows, its body, dents, rims and sheen. */
private fun drawClayScene(
    c: android.graphics.Canvas,
    s: ClaySim,
    width: Float,
    height: Float,
    hsl: List<Hsl>,
    paints: ClayPaints,
) {
    val r0 = s.r0.toFloat()
    val homeX = s.homeX.toFloat()
    val homeY = s.homeY.toFloat()
    if (hsl.isEmpty()) return
    // The clay's colour and the paper's, mid-change: the paper turns with the change as a whole.
    val p = s.recolour
    val from = hsl[s.colourFrom.coerceIn(0, hsl.size - 1)]
    val to = hsl[s.colourTo.coerceIn(0, hsl.size - 1)]
    val e = easeInOut(p)
    // The studio.
    val wall = paints.solid(SHADED, 0f).apply {
        shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(paperMix(from, to, PaperTop, e), paperMix(from, to, PaperMid, e), paperMix(from, to, PaperFloor, e)),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, width, height, wall)
    wall.shader = RadialGradient(
        homeX - r0 * 0.5f, homeY - r0 * 0.9f, width * 0.95f,
        intArrayOf(Color(1f, 1f, 1f, 0.62f).toArgb(), Color(1f, 1f, 1f, 0f).toArgb()),
        null,
        Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, width, height, wall)
    wall.shader = paints.paper.at(0f, 0f)
    c.drawRect(0f, 0f, width, height, wall)
    wall.shader = null

    // Mid-change, the shading follows the mix while the new colour spreads from the tap.
    val fromTones = Tones(from)
    val toTones = Tones(to)
    val mixed = Tones(from, to, e)

    val body = paints.body
    body.reset()
    traceRing(body, s)
    val outside = paints.outside
    outside.reset()
    outside.fillType = Path.FillType.EVEN_ODD
    outside.addRect(-width, -height, width * 2, height * 2, Path.Direction.CW)
    traceRing(outside, s)

    // Shadows on the floor.
    drawShifted(c, body, 10f, 18f, paints.solid(paperMix(from, to, PaperInk, e, SHADOW_WIDE), 22f))
    drawShifted(c, body, 4f, 14f, paints.solid(mixed.base(BOUNCE), 26f))
    drawShifted(c, body, 2f, 4f, paints.solid(paperMix(from, to, PaperInk, e, SHADOW_TIGHT), 5f))

    // The body, with the new colour spreading from the tap as a soft-edged disc.
    c.save()
    c.clipPath(body)
    c.drawPath(body, paints.solid(fromTones.base(1.0), 0f))
    if (p < 1) {
        val ox = (s.cx + s.recolourX).toFloat()
        val oy = (s.cy + s.recolourY).toFloat()
        if (s.calm) {
            c.drawPath(body, paints.solid(toTones.base(p), 0f))
        } else {
            c.drawCircle(ox, oy, (e * r0 * 2.7).toFloat(), paints.solid(toTones.base(1.0), 12f))
        }
    }

    // Where the sheen sits: part way out toward the upper-left ring point, so it rides the shape.
    val n = CLAY_POINTS
    val k = (((SHEEN_ANGLE + 360) % 360) / 360 * n).roundToInt() % n
    val ax = (s.cx + (s.x[k] - s.cx) * SHEEN_IN - s.cvx * 0.018).toFloat()
    val ay = (s.cy + (s.y[k] - s.cy) * SHEEN_IN - s.cvy * 0.018).toFloat()
    // Volume: light falls off from the sheen; the far side turns into the shade.
    val vol = paints.solid(SHADED, 0f).apply {
        shader = RadialGradient(
            ax, ay, r0 * 2.1f,
            intArrayOf(mixed.light(0.5), mixed.light(0.0), mixed.shade(0.0), mixed.shade(0.6)),
            floatArrayOf(0f, 0.32f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawPath(body, vol)
    // Clay grain and mottling, carried with the body.
    vol.shader = paints.clay.at(s.cx.toFloat(), s.cy.toFloat())
    c.drawPath(body, vol)
    vol.shader = null

    for (d in s.dents) {
        if (d.live) drawDent(c, (s.cx + d.x).toFloat(), (s.cy + d.y).toFloat(), d.depth, d.ridge, d.seed, mixed, paints)
    }

    // The rim: dark toward the lower right, lit toward the upper left, and a thin bounce of floor
    // light right on the lower edge.
    drawShifted(c, outside, -8f, -10f, paints.solid(mixed.deep(0.62), 10f))
    drawShifted(c, outside, 6f, 7f, paints.solid(mixed.light(0.6), 7f))
    drawShifted(c, outside, -1.5f, -2f, paints.solid(mixed.light(0.28), 1.6f))

    // The sheen: a broad soft glow and a small hot spot, turned along the surface.
    val tx = s.x[(k + 1) % n] - s.x[(k + n - 1) % n]
    val ty = s.y[(k + 1) % n] - s.y[(k + n - 1) % n]
    val turn = (atan2(ty, tx) * 180 / PI).toFloat()
    c.save()
    c.translate(ax, ay)
    c.rotate(turn)
    paints.rect.set(-r0 * 0.36f, -r0 * 0.17f, r0 * 0.36f, r0 * 0.17f)
    c.drawOval(paints.rect, paints.solid(Color(1f, 1f, 1f, SHEEN_BROAD).toArgb(), 11f))
    paints.rect.set(-r0 * 0.15f, -r0 * 0.12f, r0 * 0.09f, -r0 * 0.02f)
    c.drawOval(paints.rect, paints.solid(Color(1f, 1f, 1f, SHEEN_HOT).toArgb(), 3.2f))
    c.restore()
    c.restore()
}
