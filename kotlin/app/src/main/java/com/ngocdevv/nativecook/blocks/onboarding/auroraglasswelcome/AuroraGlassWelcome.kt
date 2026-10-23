package com.ngocdevv.nativecook.blocks.onboarding.auroraglasswelcome

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The horizon: mountains over a black beach, keyed out of their bright sky so the aurora shows
// through, and reflected in the wet sand.
private const val HORIZON = "https://images.unsplash.com/photo-1511300636408-a63a89df3482?auto=format&fit=crop&w=1200&h=720&q=80"
private const val HORIZON_WIDTH = 1.5f // times the screen's width
private const val HORIZON_TOP = 0.31f // share of the height where the photo's top sits, so the peaks rise behind the card
private const val HORIZON_RISE = 26f // dp the horizon rises into place on entrance
private const val HORIZON_ASPECT = 1200f / 720f
private const val GROUND_FADE = 110f // dp over which the photo's foot melts into the night ground
private val Night = Color(0xFF040815)
private val Ink = Color(0xFFF3F6FF)
private val Green = Color(0xFF6DFFBF)
private val Soft = Ink.copy(alpha = 0.62f)
private val Faint = Ink.copy(alpha = 0.45f)

// Tonight's activity, hourly from 18:00 to 04:00; the marker sits on the peak.
private val Activity = floatArrayOf(1.6f, 2.2f, 3.0f, 4.4f, 6.3f, 5.6f, 4.7f, 5.1f, 3.8f, 2.7f, 2.0f)
private const val NOW_INDEX = 4
private val Hours = listOf("18:00", "21:00", "00:00", "03:00")
private const val CHART_HEIGHT = 92f
private class Stat(val value: String, val label: String)
private val Stats = listOf(Stat("12%", "Cloud cover"), Stat("18%", "Moonlight"), Stat("05:40", "Dark until"))

// The self-playing loop: the card tilts left, then right, LOOP s round, while the aurora's flow
// comes back to where it started on the same period.
private const val LOOP = 10f
private const val AUTO_YAW = 9f // deg
private const val AUTO_PITCH = 4f // deg
private const val AUTO_RESUME = 3000L // ms after a touch
private const val AUTO_FADE = 900 // ms for the autoPlay tilt to come back in
private const val PULSES = 5f // the "now" marker pulses this many times a loop

// Dragging: the card tilts TILT_RATE deg per dp, freely up to MAX_TILT, then rubber-bands; let go,
// it springs flat carrying the finger's speed. The aurora bends towards the finger meanwhile.
private const val TILT_RATE = 0.09f
private const val MAX_TILT = 16f
private const val RUBBER = 0.5f
private const val RELEASE_PERIOD = 0.7f // s
private const val RELEASE_DAMPING = 0.55f
private const val RELEASE_REDUCED_PERIOD = 0.3f // s, without overshoot
private const val PERSPECTIVE = 900f // dp
private const val PAN_SLOP = 2f // dp

// Entrance, s from appearing: the sky lights up, the horizon rises, the headline climbs out of its
// lines, the card lifts in and its curve draws on, then the buttons.
private const val SKY_IN = 1.6f
private const val BAR_AT = 0.15f
private val LineAt = floatArrayOf(0.26f, 0.36f)
private const val LINE_IN = 0.76f
private const val SUB_AT = 0.52f
private const val CARD_AT = 0.6f
private const val CARD_IN = 0.85f
private const val CARD_RISE = 40f
private const val CURVE_AT = 0.95f
private const val CURVE_IN = 1.15f
private const val STATS_AT = 1.15f
private const val STAT_STAGGER = 0.08f
private const val CTA_AT = 1.0f
private const val ITEM_IN = 0.6f

// Frosted glass on Android 12+: the sky behind the card, blurred this much.
private const val FROST = 22f // dp

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val EaseInOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private const val TAU = (PI * 2).toFloat()

// Night sky with three aurora curtains at three depths, the same shader as the React Native and
// SwiftUI versions (Android 13+).
private const val AURORA_AGSL = """
uniform float2 size;
uniform float phase;
uniform float2 pointer;
uniform float pull;
uniform float reveal;

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
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
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = p * 2.03 + float2(13.1, 7.7);
    a *= 0.5;
  }
  return v;
}

float3 curtain(float2 uv, float2 p, float hem, float sway, float2 drift, float2 shimmer, float3 low, float3 mid, float3 high, float tall, float soft, float seed) {
  float wave = fbm(float2(p.x * 1.15 + seed, seed * 0.37) + drift);
  float line = hem + (wave - 0.5) * sway + 0.028 * sin(p.x * 2.7 + seed + drift.x * 2.0);
  float d = line - uv.y;
  float edge = smoothstep(-soft, soft * 0.35, d);
  float body = 0.55 * exp(-max(d, 0.0) / tall) + 0.9 * exp(-max(d, 0.0) / (tall * 0.22));
  float fold = 0.5 + 0.5 * sin(p.x * 6.5 + wave * 8.0 + seed + drift.y * 1.5);
  fold = 0.45 + 0.55 * fold * fold;
  float rays = noise(float2(p.x * 34.0 + shimmer.x * 2.5 + seed, uv.y * 1.4 + shimmer.y));
  rays = 0.18 + 1.5 * rays * rays;
  float strength = smoothstep(0.22, 0.78, fbm(float2(p.x * 1.5 - seed, 2.0) + drift * 0.6));
  float hemGlow = exp(-abs(d) / (soft * 2.2 + 0.004)) * 0.65;
  float glow = (edge * body * fold * rays * 1.9 + hemGlow * edge) * (0.25 + 1.0 * strength);
  float h = clamp(max(d, 0.0) / (tall * 2.6), 0.0, 1.0);
  float3 tone = mix(low, mid, smoothstep(0.0, 0.32, h));
  tone = mix(tone, high, smoothstep(0.3, 1.0, h));
  return tone * glow;
}

half4 main(float2 xy) {
  float2 uv = xy / size;
  float aspect = size.x / size.y;
  float2 p = float2(uv.x * aspect, uv.y);
  float2 toward = float2(pointer.x * aspect, pointer.y) - p;
  float near = exp(-dot(toward, toward) * 2.2);
  float2 bend = toward * near * pull;

  float2 drift = float2(cos(phase), sin(phase)) * 0.55;
  float2 shimmer = float2(cos(phase * 2.0 + 1.3), sin(phase * 2.0 + 1.3)) * 0.4;

  // Sky: deep navy overhead, a little lighter and greener towards the horizon, with a faint airglow.
  float3 sky = mix(float3(0.012, 0.02, 0.06), float3(0.03, 0.07, 0.15), smoothstep(0.0, 0.5, uv.y));
  sky += float3(0.02, 0.07, 0.08) * exp(-abs(uv.y - 0.47) * 10.0);

  // Stars, fewer and dimmer low down, twinkling on whole turns of the loop.
  float2 cell = floor(xy / 22.0);
  float hs = hash(cell);
  float2 at = (cell + 0.2 + 0.6 * float2(hash(cell + 3.1), hash(cell + 7.7))) * 22.0;
  float star = step(0.88, hs) * smoothstep(1.3, 0.15, length(xy - at));
  star *= (0.5 + 0.5 * sin(phase * 3.0 + hs * 40.0)) * smoothstep(0.55, 0.12, uv.y) * (0.4 + 0.6 * hash(cell + 1.7));

  float3 light = float3(0.0);
  // Far: high, faint, soft, red-violet crowned.
  float2 pf = p + bend * 0.12;
  light += 0.42 * curtain(uv + float2(0.0, bend.y * 0.04), pf, 0.3, 0.16, drift * 0.7, shimmer * 0.7,
    float3(0.18, 0.75, 0.55), float3(0.45, 0.3, 0.85), float3(0.75, 0.18, 0.35), 0.24, 0.03, 11.7);
  // Middle.
  float2 pm = p + bend * 0.22;
  light += 0.7 * curtain(uv + float2(0.0, bend.y * 0.08), pm, 0.37, 0.18, -drift.yx, shimmer.yx,
    float3(0.25, 0.95, 0.7), float3(0.2, 0.6, 0.85), float3(0.55, 0.3, 0.95), 0.2, 0.016, 5.3);
  // Near: low, bright, crisp hem, green-white turning violet.
  float2 pn = p + bend * 0.34;
  light += curtain(uv + float2(0.0, bend.y * 0.12), pn, 0.45, 0.16, drift, shimmer,
    float3(0.55, 1.0, 0.72), float3(0.2, 0.92, 0.62), float3(0.5, 0.32, 1.0), 0.15, 0.008, 0.0);

  light *= reveal;
  float3 c = sky + light * 1.5 + float3(star) * (1.0 - clamp(length(light) * 1.5, 0.0, 1.0)) * (0.35 + 0.65 * reveal);
  c = 1.0 - exp(-c * 1.4);
  c += (hash(xy + phase) - 0.5) / 255.0;
  return half4(half3(c), 1.0);
}
"""

/** A key that turns the photo into a night silhouette: dark ground stays, bright sky goes clear. */
private val Silhouette = floatArrayOf(
    0.03f, 0.06f, 0.01f, 0f, 0.004f * 255f,
    0.03f, 0.06f, 0.01f, 0f, 0.012f * 255f,
    0.04f, 0.08f, 0.02f, 0f, 0.035f * 255f,
    -0.53f, -1.79f, -0.18f, 0f, 1.6f * 255f,
)

private fun rubber(value: Float): Float {
    val over = abs(value) - MAX_TILT
    if (over <= 0f) return value
    return sign(value) * (MAX_TILT + (RUBBER * MAX_TILT * over) / (MAX_TILT + RUBBER * over))
}

/** How far an element `at` s after appearing has come in, eased out over `time`. */
private fun entered(since: Float, at: Float, time: Float = ITEM_IN) = EaseOutStrong.transform(((since - at) / time).coerceIn(0f, 1f))

/** A tilt angle that follows the finger, then springs back to flat carrying the finger's speed. */
private class Tilt {
    private var from = 0f
    private var velocity = 0f
    private var t = -1f
    private var omega = TAU / RELEASE_PERIOD
    private var zeta = RELEASE_DAMPING
    var value = 0f
        private set

    fun hold(angle: Float) {
        value = angle
        t = -1f
    }

    fun release(velocity: Float, reduced: Boolean) {
        from = value
        this.velocity = velocity
        omega = TAU / if (reduced) RELEASE_REDUCED_PERIOD else RELEASE_PERIOD
        zeta = if (reduced) 1f else RELEASE_DAMPING
        t = 0f
    }

    fun step(dt: Float) {
        if (t < 0f) return
        t += dt
        value = if (zeta >= 1f) {
            (from + (velocity + omega * from) * t) * exp(-omega * t)
        } else {
            val wd = omega * sqrt(1f - zeta * zeta)
            exp(-zeta * omega * t) * (from * cos(wd * t) + (velocity + zeta * omega * from) / wd * sin(wd * t))
        }
        if (t > 4f) {
            value = 0f
            t = -1f
        }
    }
}

/** The aurora's clock, the time since the screen appeared and the card's tilt. */
private class AuroraMotion {
    var clock by mutableFloatStateOf(0f)
    var since by mutableFloatStateOf(0f)
    val yaw = Tilt()
    val pitch = Tilt()
    val gain = Animatable(0f)
    val pull = Animatable(0f)
    val pressed = Animatable(0f)
    var touchX = 0.5f
    var touchY = 0.4f
    var width = 0f
    var height = 0f
    var reduced = false
    // Where the card sits on the screen, for the frosted copy of the sky behind it.
    var cardX = 0f
    var cardY = 0f

    private val t get() = clock / LOOP * TAU
    val yawNow get() = yaw.value + AUTO_YAW * sin(t) * gain.value
    val pitchNow get() = pitch.value + AUTO_PITCH * sin(2f * t) * gain.value
    val reveal get() = entered(since, 0f, if (reduced) 0.6f else SKY_IN)
}

/** The aurora shader (Android 13+) and the horizon, drawn behind the screen and again, blurred, behind the card. */
private class SkyPainter {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(AURORA_AGSL) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(Silhouette))
    }
    private val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val rect = RectF()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun aurora(width: Float, height: Float, phase: Float, px: Float, py: Float, pull: Float, reveal: Float): Shader {
        val s = shader as RuntimeShader
        s.setFloatUniform("size", width, height)
        s.setFloatUniform("phase", phase)
        s.setFloatUniform("pointer", px, py)
        s.setFloatUniform("pull", pull)
        s.setFloatUniform("reveal", reveal)
        return s
    }

    fun draw(canvas: android.graphics.Canvas, m: AuroraMotion, horizon: Bitmap?) {
        val width = m.width
        val height = m.height
        if (width <= 0f || height <= 0f) return
        val t = m.clock / LOOP * TAU
        val k = m.gain.value
        val pullNow = m.pull.value
        val reveal = m.reveal
        // Under autoPlay the light leans with the card; a finger takes over from it.
        val ax = 0.5f + 0.32f * sin(t)
        val px = ax + (m.touchX - ax) * pullNow
        val py = 0.3f + (m.touchY - 0.3f) * pullNow
        val pull = max(pullNow, 0.5f * k)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            paint.shader = aurora(width, height, t, px, py, pull, reveal)
            canvas.drawRect(0f, 0f, width, height, paint)
        } else {
            plainSky(canvas, width, height, t, px * width, py * height, pull, reveal)
        }

        val horizonW = width * HORIZON_WIDTH
        val horizonH = horizonW / HORIZON_ASPECT
        val horizonTop = height * HORIZON_TOP
        if (horizon != null) {
            // Keyed into a silhouette, fading in over its top 16%, rising into place as the sky lights up.
            val rise = if (m.reduced) 0f else (1f - reveal) * HORIZON_RISE
            val top = horizonTop + rise
            rect.set((width - horizonW) / 2f, top, (width + horizonW) / 2f, top + horizonH)
            val layer = canvas.saveLayerAlpha(0f, top, width, top + horizonH, (min(1f, reveal * 1.6f) * 255f).toInt())
            canvas.drawBitmap(horizon, null, rect, photo)
            fade.shader = LinearGradient(
                0f, top, 0f, top + horizonH * 0.16f,
                Color.Black.copy(alpha = 0f).toArgb(), Color.Black.toArgb(), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, top, width, top + horizonH, fade)
            canvas.restoreToCount(layer)
        }
        // The photo's foot melts into the night ground.
        val groundTop = horizonTop + horizonH - 2f
        paint.shader = LinearGradient(
            0f, groundTop - GROUND_FADE, 0f, groundTop,
            Night.copy(alpha = 0f).toArgb(), Night.toArgb(), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, groundTop - GROUND_FADE, width, height, paint)
        paint.shader = null
    }

    // Below Android 13 there are no runtime shaders: soft green and violet glows drift over the
    // night instead, on the same loop, and lean towards the finger.
    private fun plainSky(canvas: android.graphics.Canvas, width: Float, height: Float, t: Float, px: Float, py: Float, pull: Float, reveal: Float) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, height * 0.5f,
            Color(0xFF03050F).toArgb(), Color(0xFF081226).toArgb(), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        val blobs = arrayOf(
            // x, y (shares of the screen), radius (share of the width), phase
            floatArrayOf(0.3f, 0.36f, 0.6f, 0f),
            floatArrayOf(0.75f, 0.28f, 0.5f, 2.1f),
            floatArrayOf(0.55f, 0.42f, 0.55f, 4.2f),
        )
        val colors = intArrayOf(0x8C3DFFA0.toInt(), 0x708C5CFF, 0x7036E6B0)
        blobs.forEachIndexed { i, b ->
            var cx = width * (b[0] + 0.12f * sin(t + b[3]))
            var cy = height * (b[1] + 0.03f * cos(t * 2f + b[3]))
            cx += (px - cx) * 0.25f * pull
            cy += (py - cy) * 0.25f * pull
            val r = width * b[2]
            val alpha = ((colors[i] ushr 24) * reveal).toInt().coerceIn(0, 255)
            val colour = (alpha shl 24) or (colors[i] and 0x00FFFFFF)
            paint.shader = RadialGradient(cx, cy, r, colour, colors[i] and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.save()
            canvas.scale(1f, 0.45f, cx, cy)
            canvas.drawCircle(cx, cy, r, paint)
            canvas.restore()
        }
        paint.shader = null
    }
}

/**
 * A welcome screen under a live aurora: green and violet curtains at three depths ripple over a
 * night sky above a mountain horizon, behind a headline and a frosted glass card with tonight's
 * forecast. Dragging tilts the card in 3D, a sheen sliding across its glass, and the aurora bends
 * towards the finger; let go, the card springs flat.
 *
 * @param autoPlay Tilts the forecast card left and right by itself while the aurora flows.
 */
@Composable
fun AuroraGlassWelcome(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onStart: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = remember { AuroraMotion() }
    val painter = remember { SkyPainter() }
    var horizon by remember { mutableStateOf<Bitmap?>(null) }
    val autoNow by rememberUpdatedState(auto)
    motion.reduced = reducedMotion

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(auto) {
        motion.gain.animateTo(if (auto) 1f else 0f, tween(AUTO_FADE, easing = LinearEasing))
    }
    LaunchedEffect(context) {
        val request = ImageRequest.Builder(context).data(HORIZON).allowHardware(false).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) as? SuccessResult }.getOrNull()
        horizon = result?.image?.toBitmap()
    }
    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.04f)
                last = now
                // Under reduced motion the sky still moves, at half speed.
                motion.clock = (motion.clock + dt * if (reducedMotion) 0.5f else 1f) % LOOP
                if (motion.since < 10f) motion.since += dt
                motion.yaw.step(dt)
                motion.pitch.step(dt)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Night)
            .pointerInput(motion, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = density.density
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var yawStart = 0f
                    var pitchStart = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        // One finger tilts the card; a second one is ignored.
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        if (!change.pressed) break
                        val tx = (change.position.x - down.position.x) / d
                        val ty = (change.position.y - down.position.y) / d
                        if (!dragging) {
                            if (hypot(tx, ty) < PAN_SLOP) continue
                            dragging = true
                            // The self-playing tilt hands over to the finger where it is.
                            yawStart = motion.yawNow
                            pitchStart = motion.pitchNow
                            motion.yaw.hold(yawStart)
                            motion.pitch.hold(pitchStart)
                            scope.launch { motion.gain.snapTo(0f) }
                            scope.launch { motion.pull.animateTo(1f, tween(250, easing = EaseOutStrong)) }
                            scope.launch { motion.pressed.animateTo(1f, tween(160, easing = EaseOutStrong)) }
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        motion.yaw.hold(rubber(tx * TILT_RATE + yawStart))
                        motion.pitch.hold(rubber(-ty * TILT_RATE + pitchStart))
                        motion.touchX = change.position.x / d / max(1f, motion.width)
                        motion.touchY = change.position.y / d / max(1f, motion.height)
                    }
                    if (!dragging) return@awaitEachGesture
                    val v = tracker.calculateVelocity()
                    val vx = if (v.x.isFinite()) v.x / d else 0f
                    val vy = if (v.y.isFinite()) v.y / d else 0f
                    motion.yaw.release(vx * TILT_RATE, reducedMotion)
                    motion.pitch.release(-vy * TILT_RATE, reducedMotion)
                    scope.launch { motion.pull.animateTo(0f, tween(1200, easing = EaseOutStrong)) }
                    scope.launch { motion.pressed.animateTo(0f, tween(220, easing = EaseOutStrong)) }
                    if (autoNow) {
                        scope.launch {
                            delay(AUTO_RESUME)
                            motion.gain.animateTo(1f, tween(AUTO_FADE, easing = EaseInOutQuad))
                        }
                    }
                }
            },
    ) {
        motion.width = maxWidth.value
        motion.height = maxHeight.value

        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { painter.draw(it.nativeCanvas, motion, horizon) }
            }
        }

        // The brand and tonight's weather.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 10f).dp)
                .height(32.dp)
                .graphicsLayer { alpha = entered(motion.since, BAR_AT) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("SOLVIND", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.6.sp))
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .height(30.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer {
                            val s = sin(motion.clock / LOOP * PI.toFloat() * PULSES)
                            alpha = 0.5f + 0.5f * s * s
                        }
                        .background(Green, CircleShape),
                )
                BasicText(
                    "Clear · −4°",
                    style = TextStyle(color = Ink.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
        }

        // The headline, each line climbing out of a slot at its own baseline.
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = (safeTop + 74f).dp)) {
            listOf("Chase the", "northern light.").forEachIndexed { i, text ->
                Box(Modifier.fillMaxWidth().height(50.dp).clipToBounds()) {
                    BasicText(
                        text,
                        maxLines = 1,
                        style = TextStyle(color = Ink, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp),
                        modifier = Modifier.graphicsLayer {
                            val shown = entered(motion.since, LineAt[i], LINE_IN)
                            alpha = if (reducedMotion) shown else min(1f, shown * 2f)
                            translationY = if (reducedMotion) 0f else (1f - shown) * 50.dp.toPx()
                        },
                    )
                }
            }
            BasicText(
                "Live forecasts, wake-up alerts and quiet spots far from the city glow.",
                style = TextStyle(color = Soft, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 310.dp)
                    .graphicsLayer {
                        val shown = entered(motion.since, SUB_AT)
                        alpha = shown
                        translationY = if (reducedMotion) 0f else (1f - shown) * 12.dp.toPx()
                    },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 18.dp, end = 18.dp, bottom = (safeBottom + 10f).dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ForecastCard(motion, painter, horizon, reducedMotion)
            Column(
                Modifier.graphicsLayer {
                    val shown = entered(motion.since, CTA_AT)
                    alpha = shown
                    translationY = if (reducedMotion) 0f else (1f - shown) * 12.dp.toPx()
                },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StartButton(onStart)
                val signIn by rememberUpdatedState(onSignIn)
                BasicText(
                    "I already have an account",
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Button }
                        .pointerInput(Unit) { detectTapGestures(onTap = { signIn?.invoke() }) }
                        .padding(vertical = 2.dp),
                    style = TextStyle(color = Ink.copy(alpha = 0.72f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** The frosted forecast card: tonight's Kp, the activity curve, three numbers. */
@Composable
private fun ForecastCard(motion: AuroraMotion, painter: SkyPainter, horizon: Bitmap?, reducedMotion: Boolean) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                val at = it.positionInRoot()
                motion.cardX = at.x / density.density
                motion.cardY = at.y / density.density
            }
            .graphicsLayer {
                val e = entered(motion.since, CARD_AT, CARD_IN)
                alpha = e
                translationY = (if (reducedMotion) 0f else (1f - e) * CARD_RISE).dp.toPx()
                rotationX = motion.pitchNow
                rotationY = motion.yawNow
                cameraDistance = PERSPECTIVE.dp.toPx() / 72f
                val scale = 1f + 0.015f * motion.pressed.value - if (reducedMotion) 0f else (1f - e) * 0.04f
                scaleX = scale
                scaleY = scale
            }
            .shadow(28.dp, shape, ambientColor = Color(0, 2, 10), spotColor = Color(0, 2, 10))
            .clip(shape)
            // The edge: a hairline brightest along the top, where the light catches it.
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.35f to Color.White.copy(alpha = 0.14f),
                    0.75f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.2f),
                ),
                shape,
            ),
    ) {
        // Frosted glass: the sky behind the card, blurred on Android 12+, under a dark cold tint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(FROST.dp.toPx(), FROST.dp.toPx(), TileMode.Clamp)
                    },
            ) {
                scale(density.density, density.density, pivot = Offset.Zero) {
                    translate(-motion.cardX, -motion.cardY) {
                        drawIntoCanvas { painter.draw(it.nativeCanvas, motion, horizon) }
                    }
                }
            }
            Box(Modifier.matchParentSize().background(Color(8, 12, 28).copy(alpha = 0.3f)))
        } else {
            Box(Modifier.matchParentSize().background(Color(14, 22, 46).copy(alpha = 0.82f)))
        }
        Box(Modifier.matchParentSize().background(Color(10, 18, 44).copy(alpha = 0.3f)))
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.13f),
                        0.28f to Color.White.copy(alpha = 0.03f),
                        0.6f to Color.White.copy(alpha = 0f),
                    ),
                ),
        )
        // The sheen slides across the glass opposite the tilt, brighter the more it is tilted.
        Canvas(Modifier.matchParentSize()) {
            val yaw = motion.yawNow
            val pitch = motion.pitchNow
            val alpha = min(1f, 0.2f + hypot(yaw, pitch) / MAX_TILT)
            val w = size.width + 440.dp.toPx()
            val h = size.height + 160.dp.toPx()
            val left = -220.dp.toPx() - yaw / MAX_TILT * 220.dp.toPx()
            val top = -80.dp.toPx() + pitch / MAX_TILT * 80.dp.toPx()
            drawRect(
                Brush.linearGradient(
                    0.32f to Color.White.copy(alpha = 0f),
                    0.5f to Color.White.copy(alpha = 0.16f * alpha),
                    0.68f to Color.White.copy(alpha = 0f),
                    start = Offset(left, top + h * 0.15f),
                    end = Offset(left + w, top + h * 0.85f),
                ),
            )
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("TONIGHT · VÍK", style = TextStyle(color = Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(
                            "Kp 6.3",
                            style = TextStyle(color = Ink, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, fontFeatureSettings = "tnum"),
                        )
                        BasicText("High", style = TextStyle(color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.padding(bottom = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("21:40 – 01:10", style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    BasicText("Best window", style = TextStyle(color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ActivityChart(motion, reducedMotion)
                Row(Modifier.fillMaxWidth()) {
                    Hours.forEachIndexed { i, hour ->
                        BasicText(hour, style = TextStyle(color = Faint, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                        if (i < Hours.size - 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.16f)))
            Row(Modifier.fillMaxWidth()) {
                Stats.forEachIndexed { i, stat ->
                    Row(
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val shown = entered(motion.since, STATS_AT + i * STAT_STAGGER)
                                alpha = shown
                                translationY = if (reducedMotion) 0f else (1f - shown) * 8.dp.toPx()
                            },
                    ) {
                        if (i > 0) {
                            Box(Modifier.width(0.5.dp).height(38.dp).background(Color.White.copy(alpha = 0.16f)))
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(stat.value, style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                            BasicText(stat.label, style = TextStyle(color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

/** Tonight's activity: a smooth curve that draws on, its area filling in, a marker that pops onto the peak and pulses. */
@Composable
private fun ActivityChart(motion: AuroraMotion, reducedMotion: Boolean) {
    val line = remember { Path() }
    val area = remember { Path() }
    val trimmed = remember { Path() }
    val measure = remember { PathMeasure() }
    var builtFor by remember { mutableFloatStateOf(-1f) }
    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT.dp)) {
        val w = max(1f, size.width)
        val h = size.height
        val top = 10.dp.toPx()
        val xs = FloatArray(Activity.size) { it / (Activity.size - 1f) * w }
        val ys = FloatArray(Activity.size) { top + (1f - Activity[it] / 7f) * (h - top) }
        if (builtFor != w) {
            builtFor = w
            line.reset()
            line.moveTo(xs[0], ys[0])
            for (i in 0 until Activity.size - 1) {
                val i0 = max(0, i - 1)
                val i3 = min(Activity.size - 1, i + 2)
                line.cubicTo(
                    xs[i] + (xs[i + 1] - xs[i0]) / 6f, ys[i] + (ys[i + 1] - ys[i0]) / 6f,
                    xs[i + 1] - (xs[i3] - xs[i]) / 6f, ys[i + 1] - (ys[i3] - ys[i]) / 6f,
                    xs[i + 1], ys[i + 1],
                )
            }
            area.reset()
            area.addPath(line)
            area.lineTo(w, h)
            area.lineTo(0f, h)
            area.close()
            measure.setPath(line, false)
        }
        val since = motion.since
        val drawn = if (reducedMotion) 1f else EaseInOut.transform(((since - CURVE_AT) / CURVE_IN).coerceIn(0f, 1f))
        val popAt = if (reducedMotion) CURVE_AT else CURVE_AT + CURVE_IN * 0.55f
        val markerT = since - popAt
        val marker = when {
            markerT <= 0f -> 0f
            reducedMotion -> min(1f, markerT / 0.4f)
            else -> {
                // A spring to 1 over about 0.5 s with damping ratio 0.5.
                val omega = TAU / 0.5f
                val zeta = 0.5f
                val wd = omega * sqrt(1f - zeta * zeta)
                1f - exp(-zeta * omega * markerT) * (cos(wd * markerT) + zeta * omega / wd * sin(wd * markerT))
            }
        }
        val nowX = xs[NOW_INDEX]
        val nowY = ys[NOW_INDEX]

        // Faint guides.
        for (f in floatArrayOf(0.33f, 0.66f)) {
            drawRect(Color.White.copy(alpha = 0.1f), Offset(0f, top + f * (h - top)), androidx.compose.ui.geometry.Size(w, 0.5.dp.toPx()))
        }
        drawRect(Ink.copy(alpha = 0.22f * marker.coerceIn(0f, 1f)), Offset(nowX - 0.5.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(1.dp.toPx(), h))
        // The area is uncovered left to right with the line.
        clipRect(0f, 0f, w * drawn, h) {
            drawPath(area, Brush.verticalGradient(listOf(Green.copy(alpha = 0.32f), Green.copy(alpha = 0f)), startY = top, endY = h))
        }
        val lineAlpha = if (reducedMotion) marker.coerceIn(0f, 1f) else if (drawn > 0.001f) 1f else 0f
        if (lineAlpha > 0f) {
            trimmed.reset()
            measure.getSegment(0f, measure.length * max(0.001f, drawn), trimmed, true)
            drawPath(
                trimmed,
                Brush.horizontalGradient(0f to Color(0xFF4FD3C4), 0.4f to Green, 0.7f to Color(0xFF9CF5D2), 1f to Color(0xFF6F8CFF), startX = 0f, endX = w),
                alpha = lineAlpha,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        // The marker and its pulse.
        val pulse = (motion.clock / LOOP * PULSES) % 1f
        if (!reducedMotion) {
            drawCircle(
                Green,
                radius = (4.5f + 13f * pulse).dp.toPx(),
                center = Offset(nowX, nowY),
                alpha = (0.55f * (1f - pulse) * marker.coerceIn(0f, 1f)).coerceIn(0f, 1f),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        val dot = 4.5f * max(0f, marker)
        if (dot > 0f) {
            drawCircle(Color.White, radius = dot.dp.toPx(), center = Offset(nowX, nowY))
            drawCircle(Green, radius = dot.dp.toPx(), center = Offset(nowX, nowY), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** The white pill, dipping to 0.97 under the finger. */
@Composable
private fun StartButton(onPress: (() -> Unit)?) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pressed = remember { Animatable(1f) }
    val press by rememberUpdatedState(onPress)
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = pressed.value
                scaleY = pressed.value
            }
            .background(Color.White, shape)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch { pressed.animateTo(0.97f, tween(110, easing = EaseOutStrong)) }
                        tryAwaitRelease()
                        scope.launch { pressed.animateTo(1f, tween(160, easing = EaseOutStrong)) }
                    },
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        press?.invoke()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Get started",
            style = TextStyle(color = Color(0xFF08102A), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        )
    }
}
