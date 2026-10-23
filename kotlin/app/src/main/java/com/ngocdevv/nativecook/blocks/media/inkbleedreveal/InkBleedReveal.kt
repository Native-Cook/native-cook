package com.ngocdevv.nativecook.blocks.media.inkbleedreveal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * @param photo Unsplash photo id, printed through the ink.
 * @param place Small caps over the print, e.g. a place.
 */
data class InkPlate(val photo: String, val title: String, val line: String, val place: String)

private val Plates = listOf(
    InkPlate("photo-1506536329413-d2f0d31ceb9f", "Still water", "Mornings on Lake Orsel, where every peak is printed twice.", "Lake Orsel"),
    InkPlate("photo-1433086966358-54859d0ed716", "Falling light", "Hollin Falls after three days of rain, loud enough to hush you.", "Hollin Falls"),
    InkPlate("photo-1596003903067-bf5762ad5c19", "The long mirror", "A tarn above the treeline that holds the whole sky still.", "Aster Tarn"),
    InkPlate("photo-1525824236856-8c0a31dfe3be", "White thread", "The upper cascade of the Vell, thin as a stitch in the moss.", "River Vell"),
)

private val Intro = InkPlate("", "Ink & Water", "Four quiet places, printed by hand. Drop a little ink to begin.", "Volume I")

// Photos are drawn about 370 dp wide on a phone; 1100 px covers that at 3x.
private fun photoUri(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1100&q=80"

private val Paper = Color(0xFFF3EFE7)
private val Ink = Color(0xFF161A23)
private val InkSoft = Color(0xFF59606E)

// The print: the wet area of the paper that takes the ink, inset from the screen.
private const val PRINT_SIDE = 18f
private const val PRINT_TOP = 56f // below the top inset
private const val PRINT_HEIGHT = 0.58f // share of the screen's height
private const val CROP_MARK = 12f // length of the crop marks at the print's corners
private const val CROP_GAP = 7f

// The ink drop: a bead hangs where it will fall, then drops.
private const val BEAD_RADIUS = 7f // dp, a light drop; a fully charged one is twice as wide
private const val BEAD_LIFT = 46f // a held bead rises this far above the finger, so the finger does not hide it
private const val LIFT_TIME = 0.35f // s; a tap lets the bead fall right under the finger
private const val BEAD_FOLLOW = 22f // 1/s, how quickly the hanging bead catches up with the finger
private const val HOLD_FULL = 1.2f // s held for a full, heavy drop
private const val BEAD_APPEAR = 0.22f // s
private const val FALL_TIME = 0.26f // s from release to contact, falling under gravity
private const val FALL_DRIFT = 0.08f // s of the finger's release speed the drop carries as it falls
private const val FALL_DRIFT_MAX = 48f // dp
// Satellite droplets thrown at contact, then soaking into the paper.
private const val SPLAT_COUNT = 9
private const val SPLAT_OUT = 0.28f // s
private const val SPLAT_SOAK = 1.5f // s

// The bleed: the ink front spreads from the drop, fast at first and slowing like ink in paper.
private const val BLEED_TIME = 2.3f // s for a light drop; a heavy one spreads up to 35% faster
private const val BAND = 40f // dp of dark wet ink behind the front before the photo comes through
private const val LOBES = 0.16f // lobe depth as a share of the front's radius

// Reduced motion: the next print crossfades in over this long.
private const val FADE_TIME = 0.6f
// The prints dry back to paper at the end of the loop.
private const val DRY_TIME = 1.1f

// autoPlay (seconds): from bare paper, a drop every 4.2 s at these spots (shares of the print),
// then the prints dry away and the paper rests.
private const val LOOP = 16f
private class ScriptDrop(val at: Float, val x: Float, val y: Float, val weight: Float)
private val Drops = listOf(
    ScriptDrop(0.35f, 0.34f, 0.32f, 0.45f),
    ScriptDrop(3.9f, 0.7f, 0.66f, 0.8f),
    ScriptDrop(7.45f, 0.28f, 0.74f, 0.35f),
    ScriptDrop(11.0f, 0.64f, 0.24f, 1f),
)
private const val DROP_HANG = 0.38f // s the auto bead hangs before it falls
private const val DRY_AT = 14.4f
// A touch pauses the loop this long; it then dries the paper and starts over.
private const val USER_HOLD = 7f

// Captions rise in once their ink has landed and leave quickly when the next one lands.
private const val CAPTION_DELAY = 0.16f
private const val CAPTION_IN = 0.52f
private const val CAPTION_RISE = 0.6f
private const val CAPTION_OUT = 0.22f

private val Roman = listOf("I", "II", "III", "IV")
private val CountWords = listOf("One", "Two", "Three", "Four")

// The same shader as the React Native block's: warm paper, the photo printed on it, and the ink
// front bleeding the next one in. `basePhoto` is the photo already printed and `topPhoto` the one
// the ink is bringing in, each cropped to fill the print.
private const val INK_AGSL = """
uniform shader basePhoto;
uniform shader topPhoto;
uniform float2 size;
uniform float4 print;
uniform float base;
uniform float top;
uniform float2 drop;
uniform float front;
uniform float seed;
uniform float dry;
uniform float blend;

float hash(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.07 + 17.3;
    a *= 0.5;
  }
  return v / 0.875;
}

// Warm paper: fine grain, a few long fibres and a soft cloudiness, darker towards the corners.
half3 paper(float2 p) {
  float grain = noise(p * 1.7) - 0.5;
  float fibre = noise(float2(p.x * 0.05 + p.y * 0.02, p.y * 0.9)) - 0.5;
  float cloud = noise(p * 0.012) - 0.5;
  half3 c = half3(0.953, 0.937, 0.906);
  c += half(grain * 0.03 + fibre * 0.02 + cloud * 0.03);
  float2 q = p / size - 0.5;
  c *= half(1.0 - dot(q, q) * 0.16);
  return c;
}

const half3 INK = half3(0.086, 0.102, 0.137);

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// The photo as pigment on this paper: a little less saturated, multiplied over the grain, with
// a thin darker line at the print's edge where the ink pooled as it dried.
half3 printed(half3 c, half3 sheet, float edge) {
  half l = dot(c, half3(0.299, 0.587, 0.114));
  c = mix(half3(l), c, 0.86) * 0.95 + 0.025;
  c *= sheet * 1.05;
  float e = (edge + 2.0) / 2.6;
  return mix(c, INK * 0.9 + c * 0.25, half(0.38 * exp(-e * e)));
}

half4 main(float2 p) {
  half3 sheet = paper(p);
  half3 col = sheet;

  float2 centre = print.xy + print.zw * 0.5;
  float edge = sdBox(p - centre, print.zw * 0.5 - 12.0, 34.0);
  edge += (fbm(p * 0.008 + 3.1) - 0.5) * 38.0;
  edge += (fbm(p * 0.05 + 7.7) - 0.5) * 7.0;
  float feather = (noise(p * 0.32) - 0.5) * 2.6;
  float inside = 1.0 - smoothstep(-1.0, 1.0, edge + feather);

  if (base > 0.5) {
    // Drying back to paper goes in blotches, not all at once.
    float blot = fbm(p * 0.008 + 11.0);
    float keep = smoothstep(blot - 0.03, blot + 0.03, dry * 1.3 - 0.15) * smoothstep(0.0, 0.45, dry);
    col = mix(col, printed(basePhoto.eval(p).rgb, sheet, edge), half(inside * keep));
  }

  float2 v = p - drop;
  float r = length(v);
  // Past the farthest the ragged front can reach, the new ink has not arrived: skip its noise.
  if (top > 0.5 && r < front * 1.18 + 34.0) {
    float2 dir = v / max(r, 0.001);
    float grow = smoothstep(0.0, 120.0, front);
    // Big soft lobes around the drop, blotches from the paper's own absorbency, and a fine
    // feathery fringe.
    float lobes = (fbm(dir * 1.5 + seed) - 0.5) * (front * 0.32 + 4.0);
    float blotch = (fbm(p * 0.022 + seed * 1.7) - 0.5) * 46.0 * grow;
    float fringe = (fbm(p * 0.21 + seed) - 0.5) * 9.0 * grow;
    float d = r + lobes + blotch + fringe - front;
    float wet = (1.0 - smoothstep(-0.9, 0.9, d)) * inside;
    // Behind the front the ink dries unevenly, letting the photo through in a mottle.
    float mottle = (fbm(p * 0.03 + seed * 2.3) - 0.5) * 24.0;
    float resolve = smoothstep(0.0, 40.0, -d + mottle);
    float k = (d + 2.5) / 3.2;
    float ring = exp(-k * k);
    half3 wash = mix(col, INK, 0.86);
    half3 layer = mix(wash, printed(topPhoto.eval(p).rgb, sheet, edge), half(resolve));
    layer = mix(layer, INK * 0.55, half(ring * 0.8));
    col = mix(col, layer, half(wet * blend));
  }
  return half4(col, 1.0);
}
"""

/**
 * A value moved by Reanimated-style timings. A delayed timing lets the one under way carry on
 * through the delay, as `withDelay` does, then starts from wherever that left the value.
 */
private class Timed(var value: Float) {
    private var from = 0f
    private var to = 0f
    private var time = 0f
    private var duration = 0f
    private var easing: Easing = LinearEasing
    private var running = false
    private var pendingTo = 0f
    private var pendingDuration = 0f
    private var pendingEasing: Easing = LinearEasing
    private var wait = -1f

    fun animate(target: Float, seconds: Float, curve: Easing, delay: Float = 0f) {
        if (delay > 0f) {
            pendingTo = target
            pendingDuration = seconds
            pendingEasing = curve
            wait = delay
            return
        }
        wait = -1f
        start(target, seconds, curve)
    }

    private fun start(target: Float, seconds: Float, curve: Easing) {
        from = value
        to = target
        time = 0f
        duration = seconds
        easing = curve
        running = true
    }

    fun step(dt: Float) {
        if (wait >= 0f) {
            wait -= dt
            if (wait < 0f) start(pendingTo, pendingDuration, pendingEasing)
        }
        if (!running) return
        time += dt
        val p = min(1f, time / max(duration, 0.0001f))
        value = from + (to - from) * easing.transform(p)
        if (p >= 1f) running = false
    }
}

/** Everything the frame loop, the touch and the picture share, kept off Compose state. */
private class InkMotion(val count: Int) {
    // The bead: 0 none, 1 hanging, 2 falling.
    var beadPhase = 0
    var beadX = 0f
    var beadY = 0f
    var aimX = 0f
    var aimY = 0f
    var beadShow = 0f
    var beadShowTime = 10f
    var beadWeight = 0f
    var beadHeld = 0f
    var beadZ = 1f
    var fallClock = 0f
    var fallX = 0f
    var fallY = 0f
    var fallDx = 0f
    var fallDy = 0f
    var holding = false
    var byUser = false

    // The prints and the ink.
    var base = -1
    var top = -1
    var dropX = 0f
    var dropY = 0f
    var progress = 0f
    var rate = 1f / BLEED_TIME
    var reach = 1f
    var seed = 0f
    var dry = 1f
    var drying = false
    var drops = 0

    // Droplets thrown at contact.
    var splatAge = 10f
    var splatX = 0f
    var splatY = 0f
    var splatDx = 0f
    var splatDy = 0f
    var splatWeight = 0f
    var splatSeed = 0f

    // 0 shows the intro, k + 1 the caption of plate k.
    var caption = 0
    var shownCaption = -1
    val opacity = Array(count + 1) { Timed(0f) }
    val lift = Array(count + 1) { Timed(12f) }

    var clock = 0f
    var looping = false
    var hold = 0f
    var ready = false
    val photos = arrayOfNulls<Bitmap>(count)

    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var safeTop = 0f
    // Seconds since the block appeared. The picture and the captions read it, so they are drawn
    // again every frame.
    var elapsed by mutableFloatStateOf(0f)

    val printX get() = PRINT_SIDE
    val printY get() = safeTop + PRINT_TOP
    val printW get() = width - PRINT_SIDE * 2f
    val printH get() = height * PRINT_HEIGHT
}

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** Ease-out for the ink front: off at full speed the moment the drop lands, slowing as the paper drinks it. */
private fun spread(u: Float): Float {
    val k = clamp01(u)
    return 1f - (1f - k) * (1f - k) * (1f - k)
}

/** A plain blue-grey wash that stands in for a photo that could not be loaded. */
private fun placeholderPrint(): Bitmap {
    val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, 96f, 0xFF5D6B7C.toInt(), 0xFF26303D.toInt(), Shader.TileMode.CLAMP)
    }
    android.graphics.Canvas(bitmap).drawRect(0f, 0f, 64f, 96f, paint)
    return bitmap
}

/** Whether a clock that went from `before` to `now`, wrapping at LOOP, passed `at`. */
private fun crossed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

private fun clampDrift(v: Float) = (v * FALL_DRIFT).coerceIn(-FALL_DRIFT_MAX, FALL_DRIFT_MAX)

/** Random in [0, 1) from a number, the same on every run (and as the React Native block's). */
private fun scatter(n: Double): Double {
    val s = sin(n * 91.345 + 47.853) * 43758.5453
    return s - floor(s)
}

/** How far the ink front must travel from `x, y` to have soaked every corner of the print. */
private fun reachFrom(m: InkMotion, x: Float, y: Float): Float {
    var far = 0f
    for (cx in floatArrayOf(m.printX, m.printX + m.printW)) {
        for (cy in floatArrayOf(m.printY, m.printY + m.printH)) far = max(far, hypot(cx - x, cy - y))
    }
    return (far + BAND + 60f) / (1f - LOBES * 0.6f)
}

/**
 * Ink over warm paper. A drop falls, and the next photo bleeds into the print through an organic,
 * fibrous ink front that spreads from where it landed, dark and wet at the edge before the image
 * comes through. Tap anywhere to drop ink; hold first for a heavier drop.
 *
 * @param plates Up to four plates, printed in turn.
 * @param autoPlay Drops ink at a few spots in turn, then lets the prints dry back to paper and starts over.
 * @param onReveal Called with a plate's index when its ink has fully spread.
 */
@Composable
fun InkBleedReveal(
    modifier: Modifier = Modifier,
    plates: List<InkPlate> = Plates,
    autoPlay: Boolean = false,
    onReveal: ((Int) -> Unit)? = null,
) {
    // An empty list falls back to the built-in plates, so there is always something to print.
    val plates = plates.ifEmpty { Plates }
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    // The ink takes four photos; fewer plates repeat, more are left out.
    val count = min(4, plates.size)
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val revealing by rememberUpdatedState(onReveal)
    val motion = remember(plates) { InkMotion(count) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect { motion.safeTop = safeTop }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
    }

    // Photos load one by one; the ink starts as soon as the first is in, and a photo that fails
    // prints as a plain blue-grey wash instead.
    LaunchedEffect(motion, context) {
        val loader = SingletonImageLoader.get(context)
        coroutineScope {
            for (i in 0 until count) {
                launch {
                    val request = ImageRequest.Builder(context)
                        .data(photoUri(plates[i % plates.size].photo))
                        .size(Size.ORIGINAL)
                        .allowHardware(false)
                        .build()
                    val bitmap = runCatching { (loader.execute(request) as? SuccessResult)?.image?.toBitmap() }.getOrNull()
                    motion.photos[i] = bitmap ?: placeholderPrint()
                    motion.ready = true
                }
            }
        }
    }

    fun hang(x: Float, y: Float, weight: Float) {
        val m = motion
        m.beadPhase = 1
        m.beadX = x
        m.beadY = y
        m.aimX = x
        m.aimY = y
        m.beadWeight = weight
        m.beadZ = 1f
        m.beadShow = 0f
        m.beadShowTime = 0f
    }

    /** Lets the bead go, carrying a little of the finger's speed. */
    fun release(vx: Float, vy: Float) {
        val m = motion
        if (m.beadPhase != 1) return
        m.fallX = m.beadX
        m.fallY = m.beadY
        m.fallDx = clampDrift(vx)
        m.fallDy = clampDrift(vy)
        m.fallClock = 0f
        m.beadPhase = 2
        // An unfinished bleed hurries to finish as this drop lands.
        if (m.top >= 0) m.rate = max(m.rate, (1f - m.progress) / FALL_TIME)
    }

    /** The drop hits the paper: the next photo starts to bleed in from here. */
    fun contact(x: Float, y: Float, user: Boolean) {
        val m = motion
        if (m.top >= 0) m.base = m.top
        val from = if (m.top >= 0) m.top else m.base
        val next = (from + 1 + count) % count
        val weight = m.beadWeight
        m.drops += 1
        m.top = next
        m.dropX = x
        m.dropY = y
        m.progress = 0f
        m.rate = if (reducedMotion) 1f / FADE_TIME else (0.85f + 0.35f * weight) / BLEED_TIME
        m.reach = reachFrom(m, x, y)
        m.seed = (scatter(m.drops.toDouble()) * 40).toFloat()
        val len = hypot(m.fallDx, m.fallDy)
        m.splatX = x
        m.splatY = y
        m.splatDx = if (len > 1f) m.fallDx / len else 0f
        m.splatDy = if (len > 1f) m.fallDy / len else 0f
        m.splatWeight = weight
        m.splatSeed = m.drops.toFloat()
        m.splatAge = 0f
        m.caption = next + 1
        m.beadPhase = 0
        if (user) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt <= 0f || m.width <= 0f) return@withFrameNanos

                // The script, which waits for the photos.
                if (m.hold > 0f) {
                    m.hold -= dt
                    if (m.hold <= 0f && m.looping) {
                        m.clock = 0f
                        if (m.base >= 0 || m.top >= 0) m.drying = true
                    }
                } else if (m.looping && m.ready) {
                    val before = m.clock
                    val t = (before + dt) % LOOP
                    m.clock = t
                    for (drop in Drops) {
                        if (crossed(before, t, drop.at) && m.beadPhase == 0) {
                            m.byUser = false
                            hang(m.printX + m.printW * drop.x, m.printY + m.printH * drop.y, drop.weight)
                        }
                        if (crossed(before, t, drop.at + DROP_HANG) && !m.byUser) release(0f, 0f)
                    }
                    if (crossed(before, t, DRY_AT) && m.base >= 0) m.drying = true
                }

                if (m.beadPhase != 0) {
                    m.beadShowTime += dt
                    m.beadShow = EaseOutStrong.transform(min(1f, m.beadShowTime / BEAD_APPEAR))
                }
                if (m.beadPhase == 1) {
                    // The hanging bead follows the finger and swells while it is held; under a
                    // finger it rises clear of it once the press turns into a hold.
                    if (m.holding) {
                        m.beadHeld += dt
                        m.beadWeight = min(1f, m.beadHeld / HOLD_FULL)
                    }
                    val lift = if (m.holding) BEAD_LIFT * min(1f, m.beadHeld / LIFT_TIME) else 0f
                    val k = 1f - exp(-dt * BEAD_FOLLOW)
                    m.beadX += (m.aimX - m.beadX) * k
                    m.beadY += (m.aimY - lift - m.beadY) * k
                } else if (m.beadPhase == 2) {
                    // Falling: height drops with the square of time, as under gravity.
                    m.fallClock += dt
                    val u = min(1f, m.fallClock / FALL_TIME)
                    m.beadZ = 1f - u * u
                    m.beadX = m.fallX + m.fallDx * u
                    m.beadY = m.fallY + m.fallDy * u
                    if (u >= 1f) contact(m.fallX + m.fallDx, m.fallY + m.fallDy, m.byUser)
                }

                if (m.top >= 0) {
                    val p = min(1f, m.progress + m.rate * dt)
                    m.progress = p
                    if (p >= 1f) {
                        // A print that is still drying away gives way to the new one.
                        m.drying = false
                        m.dry = 1f
                        m.base = m.top
                        m.top = -1
                        m.progress = 0f
                        revealing?.invoke(m.base)
                    }
                }

                if (m.drying) {
                    val d = max(0f, m.dry - dt / DRY_TIME)
                    m.dry = d
                    if (d <= 0f) {
                        m.drying = false
                        m.base = -1
                        m.dry = 1f
                        if (m.top < 0) m.caption = 0
                    }
                }
                if (m.drying && m.top < 0 && m.dry < 0.6f) m.caption = 0

                m.splatAge += dt
                stepCaptions(m, dt, reducedMotion)
                m.elapsed += dt
            }
        }
    }

    val paints = remember { InkPaints() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = "Ink and water: drop ink to reveal the next print"
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction("Drop ink") {
                        val m = motion
                        if (!m.ready || m.beadPhase == 2) return@CustomAccessibilityAction false
                        m.hold = USER_HOLD
                        m.byUser = true
                        hang(m.printX + m.printW / 2f, m.printY + m.printH / 2f, 0.5f)
                        m.beadShow = 1f
                        m.beadShowTime = BEAD_APPEAR
                        release(0f, 0f)
                        true
                    },
                )
            }
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m = motion
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    // A press anywhere hangs a bead under it, which follows it and falls on release.
                    val began = m.beadPhase != 2 && m.ready
                    if (began) {
                        m.hold = USER_HOLD
                        m.byUser = true
                        m.holding = true
                        m.beadHeld = 0f
                        hang(down.position.x / density.density, down.position.y / density.density, 0f)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        if (!change.pressed) break
                        if (m.holding) {
                            m.aimX = change.position.x / density.density
                            m.aimY = change.position.y / density.density
                        }
                    }
                    if (m.holding) {
                        m.holding = false
                        m.hold = USER_HOLD
                        val v = tracker.calculateVelocity()
                        release(v.x / density.density, v.y / density.density)
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        motion.height = height
        val printBottom = safeTop + PRINT_TOP + height * PRINT_HEIGHT

        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                // Read so the picture is drawn again every frame.
                motion.elapsed
                drawIntoCanvas { drawInk(it.nativeCanvas, motion, paints, reducedMotion) }
            }
        }

        val smallCaps = TextStyle(
            color = InkSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
        val entries = listOf(Intro) + plates.take(count)

        // The header: the journal's name, and the count of prints or the one shown.
        Box(
            Modifier
                .offset(y = (safeTop + 18f).dp)
                .fillMaxWidth()
                .padding(horizontal = (PRINT_SIDE + 2f).dp),
        ) {
            BasicText("FIELD JOURNAL", style = smallCaps)
            for (i in entries.indices) {
                val text = (if (i == 0) "${CountWords[count - 1]} prints" else "${Roman[i - 1]} / ${Roman[count - 1]}").uppercase()
                BasicText(
                    text,
                    style = smallCaps,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { captionLayer(motion, i) },
                )
            }
        }

        // The captions under the print.
        Box(
            Modifier
                .offset(y = (printBottom + 30f).dp)
                .fillMaxWidth()
                .padding(horizontal = (PRINT_SIDE + 4f).dp),
        ) {
            for (i in entries.indices) {
                val plate = entries[i]
                Column(Modifier.fillMaxWidth().graphicsLayer { captionLayer(motion, i) }) {
                    BasicText(plate.place.uppercase(), style = smallCaps, modifier = Modifier.padding(bottom = 6.dp))
                    BasicText(
                        plate.title,
                        style = TextStyle(
                            color = Ink,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            lineHeight = 42.sp,
                            letterSpacing = (-0.3).sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        ),
                    )
                    BasicText(
                        plate.line,
                        style = TextStyle(
                            color = InkSoft,
                            fontFamily = FontFamily.Serif,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        ),
                        modifier = Modifier.padding(top = 8.dp).widthIn(max = 330.dp),
                    )
                }
            }
        }

        BasicText(
            "Tap to drop ink · hold for a heavier drop",
            style = TextStyle(
                color = InkSoft.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (safeBottom + 22f).dp)
                .fillMaxWidth(),
        )
    }
}

/** A caption's fade and lift, read every frame. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.captionLayer(m: InkMotion, i: Int) {
    m.elapsed
    alpha = m.opacity[i].value
    translationY = m.lift[i].value * density
}

/** Restarts the captions' timings when another one is shown, as the animated style does. */
private fun stepCaptions(m: InkMotion, dt: Float, reduced: Boolean) {
    if (m.caption != m.shownCaption) {
        m.shownCaption = m.caption
        for (i in m.opacity.indices) {
            if (i == m.caption) {
                m.opacity[i].animate(1f, CAPTION_IN, EaseOutStrong, CAPTION_DELAY)
                if (reduced) m.lift[i].animate(0f, 0f, LinearEasing) else m.lift[i].animate(0f, CAPTION_RISE, EaseOutStrong, CAPTION_DELAY)
            } else {
                m.opacity[i].animate(0f, CAPTION_OUT, EaseOutStrong)
                m.lift[i].animate(if (reduced) 0f else -6f, if (reduced) 0f else CAPTION_OUT, EaseOutStrong)
            }
        }
    }
    for (i in m.opacity.indices) {
        m.opacity[i].step(dt)
        m.lift[i].step(dt)
    }
}

private class InkPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    val bead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -3f, -4f, BEAD_RADIUS * 2.4f,
            intArrayOf(Color(0xFF4A5266).toArgb(), Ink.toArgb(), Color(0xFF07080C).toArgb()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val marks = Path()
    var marksFor = Float.NaN
    val matrix = Matrix()
    // The shader and one image shader per photo, on Android 13 and later.
    val ink: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(INK_AGSL) else null
    val photoShaders = arrayOfNulls<BitmapShader>(4)
    var shadersFor = Float.NaN
    // The fallback: a blob path for the ink front.
    val blob = Path()
    val printClip = Path()
    var blur = -1f
}

private fun drawInk(canvas: android.graphics.Canvas, m: InkMotion, paints: InkPaints, reduced: Boolean) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val front = if (reduced) m.reach else m.reach * spread(m.progress)
    val blend = if (reduced) m.progress else 1f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        drawShaderInk(canvas, m, paints, front, blend)
    } else {
        drawFallbackInk(canvas, m, paints, front, blend)
    }

    // Crop marks at the print's corners.
    val px = m.printX
    val py = m.printY
    val pw = m.printW
    val ph = m.printH
    if (paints.marksFor != py + ph * 1000f + pw) {
        paints.marksFor = py + ph * 1000f + pw
        val path = paints.marks
        path.reset()
        val corners = arrayOf(floatArrayOf(px, py, -1f, -1f), floatArrayOf(px + pw, py, 1f, -1f), floatArrayOf(px, py + ph, -1f, 1f), floatArrayOf(px + pw, py + ph, 1f, 1f))
        for (c in corners) {
            path.moveTo(c[0] + c[2] * CROP_GAP, c[1])
            path.lineTo(c[0] + c[2] * (CROP_GAP + CROP_MARK), c[1])
            path.moveTo(c[0], c[1] + c[3] * CROP_GAP)
            path.lineTo(c[0], c[1] + c[3] * (CROP_GAP + CROP_MARK))
        }
    }
    paints.stroke.color = Ink.copy(alpha = 0.32f).toArgb()
    canvas.drawPath(paints.marks, paints.stroke)

    // The droplets, flung out further in the direction the drop was moving, then soaking in.
    val age = m.splatAge
    if (age <= SPLAT_OUT + SPLAT_SOAK) {
        val opacity = 0.92f * (1f - max(0f, (age - SPLAT_OUT) / SPLAT_SOAK))
        val u = min(1f, age / SPLAT_OUT)
        val out = 1f - (1f - u) * (1f - u) * (1f - u)
        paints.fill.color = Ink.copy(alpha = clamp01(opacity)).toArgb()
        val s = m.splatSeed.toDouble()
        for (index in 0 until SPLAT_COUNT) {
            val a = index.toDouble() / SPLAT_COUNT * PI * 2 + (scatter(s * 13 + index) - 0.5) * 2.2
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val lean = 1f + 0.9f * max(0f, ca * m.splatDx + sa * m.splatDy)
            val reach = scatter(s * 7 + index * 3).toFloat()
            val far = (10f + 46f * reach * reach) * (0.8f + 0.6f * m.splatWeight) * lean
            val r = (1.1f + 2.6f * scatter(s * 5 + index * 11).toFloat()) * (0.7f + 0.5f * m.splatWeight)
            canvas.drawCircle(m.splatX + ca * far * out, m.splatY + sa * far * out, r, paints.fill)
        }
    }

    // The bead: bigger and lifted while it hangs, its shadow sliding in under it as it falls.
    val show = if (m.beadPhase == 0) 0f else m.beadShow
    if (show <= 0f) return
    val z = m.beadZ
    val radius = BEAD_RADIUS * (1f + m.beadWeight) * (0.7f + 0.3f * m.beadShow)
    canvas.save()
    canvas.translate(m.beadX + 9f * z, m.beadY + 16f * z)
    canvas.scale(1f + 0.6f * z, 1f + 0.6f * z)
    val blur = 1.5f + z * 7f
    if (abs(paints.blur - blur) > 0.05f) {
        paints.blur = blur
        paints.shadow.maskFilter = BlurMaskFilter(blur * 1.7f, BlurMaskFilter.Blur.NORMAL)
    }
    paints.shadow.color = Ink.copy(alpha = clamp01(show * (0.32f - 0.14f * z))).toArgb()
    canvas.drawCircle(0f, 0f, radius, paints.shadow)
    canvas.restore()

    val breathe = if (m.beadPhase == 1) 1f + 0.04f * sin(m.clock * 9f + m.beadHeld * 7f) else 1f
    val stretch = if (m.beadPhase == 2) 1f + 0.18f * sin(PI.toFloat() * (1f - z)) else 1f
    canvas.save()
    canvas.translate(m.beadX, m.beadY - z * 10f)
    val scale = (1f + 0.45f * z) * breathe
    canvas.scale(scale, scale * stretch)
    paints.bead.alpha = (show * 255f).toInt()
    canvas.drawCircle(0f, 0f, radius, paints.bead)
    paints.fill.color = Color.White.copy(alpha = 0.75f * show).toArgb()
    canvas.drawCircle(-radius * 0.36f, -radius * 0.38f, radius * 0.28f, paints.fill)
    canvas.restore()
}

/** Where a photo lies when it is scaled to cover the print, centred on it. */
private fun coverMatrix(matrix: Matrix, image: Bitmap, x: Float, y: Float, w: Float, h: Float) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val fit = max(w / iw, h / ih)
    matrix.setScale(fit, fit)
    matrix.postTranslate(x + w / 2f - iw * fit / 2f, y + h / 2f - ih * fit / 2f)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun drawShaderInk(canvas: android.graphics.Canvas, m: InkMotion, paints: InkPaints, front: Float, blend: Float) {
    val shader = paints.ink as RuntimeShader
    val key = m.printY * 7f + m.printW * 3f + m.printH
    if (paints.shadersFor != key) {
        paints.shadersFor = key
        for (i in 0 until m.count) paints.photoShaders[i] = null
    }
    // Each photo becomes an image shader once, cropped to fill the print and clear past its edges.
    for (i in 0 until m.count) {
        val image = m.photos[i] ?: continue
        if (paints.photoShaders[i] != null) continue
        val s = BitmapShader(image, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        coverMatrix(paints.matrix, image, m.printX, m.printY, m.printW, m.printH)
        s.setLocalMatrix(paints.matrix)
        paints.photoShaders[i] = s
    }
    val base = if (m.base >= 0) paints.photoShaders[m.base] else null
    val top = if (m.top >= 0) paints.photoShaders[m.top] else null
    val fallback = paints.photoShaders.firstOrNull { it != null }
    // A child is always bound; the flags say which ones are read.
    val any = base ?: top ?: fallback
    if (any == null) {
        shader.setInputShader("basePhoto", android.graphics.LinearGradient(0f, 0f, 1f, 0f, 0, 0, Shader.TileMode.CLAMP))
        shader.setInputShader("topPhoto", android.graphics.LinearGradient(0f, 0f, 1f, 0f, 0, 0, Shader.TileMode.CLAMP))
    } else {
        shader.setInputShader("basePhoto", base ?: any)
        shader.setInputShader("topPhoto", top ?: any)
    }
    shader.setFloatUniform("size", m.width, m.height)
    shader.setFloatUniform("print", m.printX, m.printY, m.printW, m.printH)
    shader.setFloatUniform("base", if (base != null) 1f else 0f)
    shader.setFloatUniform("top", if (top != null) 1f else 0f)
    shader.setFloatUniform("drop", m.dropX, m.dropY)
    shader.setFloatUniform("front", front)
    shader.setFloatUniform("seed", m.seed)
    shader.setFloatUniform("dry", m.dry)
    shader.setFloatUniform("blend", blend)
    paints.fill.shader = shader
    paints.fill.color = android.graphics.Color.BLACK
    canvas.drawRect(0f, 0f, m.width, m.height, paints.fill)
    paints.fill.shader = null
}

/**
 * Below Android 13 there is no runtime shader: the paper is flat, the print a rounded rectangle,
 * and the ink a lobed blob that grows from the drop, dark for its first 40 dp before the photo
 * shows through, with a darker rim. The prints dry back to paper by fading.
 */
private fun drawFallbackInk(canvas: android.graphics.Canvas, m: InkMotion, paints: InkPaints, front: Float, blend: Float) {
    paints.fill.color = Paper.toArgb()
    canvas.drawRect(0f, 0f, m.width, m.height, paints.fill)
    val clip = paints.printClip
    clip.reset()
    clip.addRoundRect(m.printX + 12f, m.printY + 12f, m.printX + m.printW - 12f, m.printY + m.printH - 12f, 22f, 22f, Path.Direction.CW)
    canvas.save()
    canvas.clipPath(clip)
    val base = if (m.base >= 0) m.photos[m.base] else null
    if (base != null) {
        coverMatrix(paints.matrix, base, m.printX, m.printY, m.printW, m.printH)
        paints.photo.alpha = (clamp01(m.dry / 0.45f) * 255f).toInt()
        canvas.drawBitmap(base, paints.matrix, paints.photo)
    }
    val top = if (m.top >= 0) m.photos[m.top] else null
    if (top != null && front > 0f) {
        val alpha = clamp01(blend)
        blob(paints.blob, m.dropX, m.dropY, front, m.seed)
        paints.fill.color = Ink.copy(alpha = 0.9f * alpha).toArgb()
        canvas.drawPath(paints.blob, paints.fill)
        val inner = front - BAND
        if (inner > 0f) {
            canvas.save()
            blob(paints.blob, m.dropX, m.dropY, inner, m.seed)
            canvas.clipPath(paints.blob)
            coverMatrix(paints.matrix, top, m.printX, m.printY, m.printW, m.printH)
            paints.photo.alpha = (alpha * 255f).toInt()
            canvas.drawBitmap(top, paints.matrix, paints.photo)
            canvas.restore()
        }
        blob(paints.blob, m.dropX, m.dropY, front, m.seed)
        paints.stroke.color = Ink.copy(alpha = 0.8f * alpha).toArgb()
        paints.stroke.strokeWidth = 3f
        canvas.drawPath(paints.blob, paints.stroke)
        paints.stroke.strokeWidth = 0.8f
    }
    canvas.restore()
}

/** A lobed circle around `x, y`, its edge pushed in and out by a smooth wobble around the drop. */
private fun blob(path: Path, x: Float, y: Float, radius: Float, seed: Float) {
    path.reset()
    val steps = 72
    for (k in 0..steps) {
        val a = k.toFloat() / steps * 2f * PI.toFloat()
        val wobble = 0.5f * sin(a * 3f + seed) + 0.3f * sin(a * 5f + seed * 1.7f) + 0.2f * sin(a * 9f + seed * 2.3f)
        val r = radius * (1f + LOBES * 0.5f * wobble)
        val px = x + cos(a) * r
        val py = y + sin(a) * r
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
}
