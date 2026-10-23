package com.ngocdevv.nativecook.blocks.media.waterripplephoto

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** @param photo Unsplash photo id. */
data class RipplePhoto(val photo: String, val title: String, val place: String)

private val Photos = listOf(
    RipplePhoto("photo-1625934036482-2dccff707564", "Lavender, after rain", "Sorell Plateau · July"),
    RipplePhoto("photo-1493589976221-c2357c31ad77", "Blossom hour", "Hanaoka Canal · April"),
    RipplePhoto("photo-1593715857983-5531aa640471", "Rows of hum", "Valmont Ridge · June"),
    RipplePhoto("photo-1522748906645-95d8adfd52c7", "Petal weather", "Old Mill Lane · April"),
    RipplePhoto("photo-1625215393266-d09797f4a441", "The purple hour", "Linden Farm · August"),
    RipplePhoto("photo-1538943186303-104afadcbb16", "Pink quiet", "Kiyose Garden · March"),
)
// The water holds six photos; fewer repeat, more are left out.
private const val SLOTS = 6

// The photo fills a phone screen, about 400 dp wide; 1200 px covers that at 3x.
private fun photoUri(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1200&q=80"

private val Backdrop = Color(0xFF0D0C11)
private val Scrim = Color(0xFF08060E)

// Ripples: up to RIPPLES rings at once, each a packet of waves running out from where it was
// made at SPEED dp/s, WAVELENGTH apart, widening and fading as it goes.
private const val RIPPLES = 16
private const val SPEED = 190f // dp/s
private const val LIFE = 3.2f // s a ripple lives
private const val DAMP = 1.15f // 1/s, amplitude decay
// What a touch leaves: a strong ring where it lands, a lighter one every TRAIL_STEP dp it drags,
// and one every STILL_EVERY s while it rests on the water.
private const val TOUCH_STRENGTH = 1f
private const val TRAIL_STRENGTH = 0.5f
private const val TRAIL_STEP = 26f
private const val STILL_EVERY = 0.6f
private const val STILL_STRENGTH = 0.55f

// The swell to the next photo: a big ring from where the swipe let go, its front carrying the
// next photo. It runs on a critically damped spring toward a little past the farthest corner,
// taking the swipe's speed as its own.
private const val SWIPE_DISTANCE = 50f // dp up
private const val SWIPE_SPEED = 650f // dp/s up
private const val SWELL_OMEGA = 2.3f // 1/s, natural frequency of the spring
private const val SWELL_OVER = 1.18f // spring target, as a multiple of the distance that covers the screen
private const val SWELL_MIN_SPEED = 520f // dp/s, the swell's speed when the swipe was slow
private const val SWELL_MAX_SPEED = 1500f
private const val CROSSFADE = 0.5f // s, the change under reduced motion

// autoPlay: six beats of LOOP / 6 s; in each, four raindrops, then a swipe up to the next photo.
private const val LOOP = 21f
private const val BEAT = LOOP / SLOTS
private val Rain = floatArrayOf(0.25f, 0.8f, 1.35f, 1.8f)
private const val SWIPE_AT = 2.15f // s into a beat: a short drag up that leaves four ripples, then the swell
private const val SWIPE_TRAIL = 0.2f
private const val USER_HOLD = 4f

// Captions cross over as the swell starts.
private const val CAPTION_DELAY = 0.26f
private const val CAPTION_IN = 0.65f
private const val CAPTION_RISE = 0.75f
private const val CAPTION_OUT = 0.22f

// The entrance, once the first photo has arrived: it fades in over PHOTO_FADE s on a gentle
// ease-out (a quad, so the fade reads as a fade) while settling from PHOTO_SCALE to full size over
// PHOTO_SETTLE s on the strong ease-out; the header, the caption and the hint follow it
// in, each rising TEXT_RISE dp over TEXT_FADE s, TEXT_DELAYS s after the photo starts. The autoPlay
// script waits ENTRANCE s, so the first raindrop falls on a settled picture.
private const val PHOTO_FADE = 0.7f
private const val PHOTO_SETTLE = 1.4f
private const val PHOTO_SCALE = 1.06f
private const val TEXT_FADE = 0.7f
private const val TEXT_RISE = 10f
private val TextDelays = floatArrayOf(0.35f, 0.52f, 0.7f)
private const val ENTRANCE = 1.6f
private val FadeEase = Easing { t -> 1f - (1f - t) * (1f - t) }

/** Reanimated's default timing curve, an ease-in-out quad. */
private val InOutQuad = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }

// The same shader as the React Native block's: the photo as seen through moving water. For each
// ripple, the height of the surface is a wave packet around a ring of radius SPEED·age; its slope
// bends the light, shifting the image along the ring's radius, and its curvature focuses light
// into bright caustic lines. The swell to the next photo is one more, much larger packet whose
// front reveals the next photo.
private const val WATER_AGSL = """
uniform shader currentPhoto;
uniform shader nextPhoto;
uniform float2 size;
uniform float time;
uniform float4 ripples[16];
uniform float2 origin;
uniform float swell;
uniform float mixing;
uniform float still;

const float PI = 3.14159265;
const float K = 2.0 * PI / 24.0;

half4 main(float2 p) {
  float2 shift = float2(0.0);
  float light = 0.0;
  float motion = 1.0 - still;

  // A slow swell all over, so the surface is never quite still.
  shift += motion * 1.4 * float2(sin(p.y * 0.031 + time * 1.3), cos(p.x * 0.027 + time * 1.05));

  for (int i = 0; i < 16; i++) {
    float4 r = ripples[i];
    float age = time - r.z;
    if (r.w <= 0.0 || age < 0.0 || age > 3.2) continue;
    float2 v = p - r.xy;
    float d = length(v);
    float x = d - 190.0 * age;
    float width = 14.0 + 26.0 * age;
    float env = exp(-x * x / (width * width)) * r.w * exp(-1.15 * age) / sqrt(1.0 + d * 0.012);
    // Fades in over the first moments so a ring does not pop.
    env *= smoothstep(0.0, 0.06, age);
    float phase = x * K;
    shift += (v / max(d, 1.0)) * cos(phase) * env * 9.0 * motion;
    // Under reduced motion the rings only glint softly.
    light -= sin(phase) * env * (0.35 + 0.65 * motion);
  }

  // The swell's front, with a train of smaller waves behind it.
  float mask = mixing;
  if (swell > 0.0) {
    float2 v = p - origin;
    float d = length(v);
    float x = d - swell;
    float front = exp(-x * x / (60.0 * 60.0));
    float behind = x < 0.0 ? exp(x / 160.0) : 0.0;
    float wave = cos(x * 0.075) * front * 17.0 + sin(x * 0.15) * behind * 4.0;
    shift += (v / max(d, 1.0)) * wave * motion;
    light -= (sin(x * 0.075) * front * 0.38 + cos(x * 0.15) * behind * 0.22) * motion;
    float edge = x + sin(atan(v.y, v.x) * 7.0 + swell * 0.01) * 10.0;
    mask = 1.0 - smoothstep(-26.0, 26.0, edge);
  }

  float2 q = clamp(p + shift, float2(0.5), size - 0.5);
  half3 a = currentPhoto.eval(q).rgb;
  half3 col = a;
  if (mask > 0.001) col = mix(a, nextPhoto.eval(q).rgb, half(mask));

  // Caustics: bright where the surface focuses light, a little darker where it spreads it.
  float glint = max(light, 0.0);
  col += half3(1.0, 0.98, 0.95) * half(pow(glint, 1.4) * 0.3);
  col *= half(1.0 + min(light, 0.0) * 0.16);
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
    private var easing: Easing = InOutQuad
    private var running = false
    private var pendingTo = 0f
    private var pendingDuration = 0f
    private var pendingEasing: Easing = InOutQuad
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
private class WaterMotion(val count: Int) {
    var time = 0f
    // x, y, birth time, strength for each ripple slot; `head` is the next slot to write.
    val ripples = FloatArray(RIPPLES * 4).also { for (i in 0 until RIPPLES) it[i * 4 + 2] = -10f }
    var head = 0

    var current = 0
    var next = 1
    var originX = 0f
    var originY = 0f
    var swell = 0f
    var swellSpeed = 0f
    var swellCover = 1f
    var swelling = false
    var mixing = 0f
    var caption = 0
    var shownCaption = -1
    // The first caption starts in place; the others wait below.
    val opacity = Array(count) { Timed(if (it == 0) 1f else 0f) }
    val lift = Array(count) { Timed(if (it == 0) 0f else 14f) }

    var touching = false
    var fingerX = 0f
    var fingerY = 0f
    var trailX = 0f
    var trailY = 0f
    var stillFor = 0f

    var clock = 0f
    var looping = false
    var hold = 0f
    var ready = false
    // Each photo as it arrives; the swell never carries in one that is not there yet.
    val photos = arrayOfNulls<Bitmap>(SLOTS)
    // The entrance: the photo's opacity and its settle (0 … 1), and the header's, the caption's
    // and the hint's progress.
    val photoIn = Timed(0f)
    val photoSettle = Timed(0f)
    val textIn = Array(TextDelays.size) { Timed(0f) }

    // The screen, in dp.
    var width = 0f
    var height = 0f
    // Seconds since the block appeared. The picture and the captions read it, so they are drawn
    // again every frame.
    var elapsed by mutableFloatStateOf(0f)
}

/** Random in [0, 1) from a number, the same on every run (and as the React Native block's). */
private fun scatter(n: Double): Float {
    val s = sin(n * 91.345 + 47.853) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Whether a clock that went from `before` to `now`, wrapping at LOOP, passed `at`. */
private fun crossed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** The swell's radius that reaches every corner of the screen from `x, y`. */
private fun coverFrom(x: Float, y: Float, width: Float, height: Float) =
    hypot(max(x, width - x), max(y, height - y)) + 60f

/**
 * A photo that behaves like water. Touches send rings out across it that bend the image and
 * catch the light; dragging leaves a trail of them. Swiping up sends a big swell from the finger
 * that carries in the next photo.
 *
 * @param photos Six photos, shown in turn.
 * @param autoPlay Drops rain at scattered spots and swipes through every photo, then starts over.
 * @param onChange Called with a photo's index once it has filled the screen.
 */
@Composable
fun WaterRipplePhoto(
    modifier: Modifier = Modifier,
    photos: List<RipplePhoto> = Photos,
    autoPlay: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val count = min(SLOTS, photos.size)
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changing by rememberUpdatedState(onChange)
    val motion = remember(photos) { WaterMotion(count) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
    }

    // Each photo arrives on its own. The first one brings the screen in: the photo fades and
    // settles, the type follows it, and the script waits for the entrance to finish.
    LaunchedEffect(motion, context) {
        if (photos.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        coroutineScope {
            for (i in 0 until SLOTS) {
                launch {
                    val request = ImageRequest.Builder(context)
                        .data(photoUri(photos[i % photos.size].photo))
                        .size(Size.ORIGINAL)
                        .allowHardware(false)
                        .build()
                    val image = (loader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@launch
                    val m = motion
                    m.photos[i] = image
                    if (i == 0 && !m.ready) {
                        m.ready = true
                        m.photoIn.animate(1f, PHOTO_FADE, FadeEase)
                        m.photoSettle.animate(1f, PHOTO_SETTLE, EaseOutStrong)
                        for (k in m.textIn.indices) m.textIn[k].animate(1f, TEXT_FADE, EaseOutStrong, TextDelays[k])
                        m.hold = ENTRANCE
                    }
                }
            }
        }
    }

    /** Starts a ring at `x, y`. */
    fun ripple(x: Float, y: Float, strength: Float) {
        val m = motion
        val slot = m.head
        m.ripples[slot * 4] = x
        m.ripples[slot * 4 + 1] = y
        m.ripples[slot * 4 + 2] = m.time
        m.ripples[slot * 4 + 3] = strength
        m.head = (slot + 1) % RIPPLES
    }

    /** Sends the swell from `x, y` toward the next photo, starting at `speed` dp/s. */
    fun sweep(x: Float, y: Float, speed: Float, user: Boolean) {
        val m = motion
        if (m.swelling || m.mixing > 0f || count <= 0) return
        val upcoming = (m.current + 1) % count
        if (m.photos[upcoming] == null) return
        m.next = upcoming
        m.caption = m.next
        if (reducedMotion) {
            m.mixing = 0.001f
        } else {
            m.originX = x
            m.originY = y
            m.swell = 1f
            m.swellSpeed = speed.coerceIn(SWELL_MIN_SPEED, SWELL_MAX_SPEED)
            m.swellCover = coverFrom(x, y, m.width, m.height)
            m.swelling = true
            ripple(x, y, 1.2f)
        }
        if (user) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** The next photo has filled the screen: it becomes the current one. */
    fun settle() {
        val m = motion
        m.current = m.next
        m.swelling = false
        m.swell = 0f
        m.mixing = 0f
        changing?.invoke(m.current)
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                m.time += dt

                if (m.hold > 0f) {
                    m.hold -= dt
                    if (m.hold <= 0f) m.clock = 0f
                } else if (m.looping && m.ready && m.width > 0f) {
                    val before = m.clock
                    val t = (before + dt) % LOOP
                    m.clock = t
                    for (beat in 0 until SLOTS) {
                        val start = beat * BEAT
                        for (k in Rain.indices) {
                            if (crossed(before, t, start + Rain[k])) {
                                val n = (beat * 11 + k * 3).toDouble()
                                val x = m.width * (0.14f + 0.72f * scatter(n))
                                val y = m.height * (0.16f + 0.6f * scatter(n + 0.5))
                                ripple(x, y, 0.6f + 0.4f * scatter(n + 0.25))
                            }
                        }
                        // A drag up from low on the screen: four ripples, then the swell from where it let go.
                        val x = m.width * (0.35f + 0.3f * scatter((beat * 5 + 2).toDouble()))
                        for (k in 0 until 4) {
                            if (crossed(before, t, start + SWIPE_AT + k * SWIPE_TRAIL / 3f)) {
                                ripple(x, m.height * (0.8f - 0.06f * k), TRAIL_STRENGTH)
                            }
                        }
                        if (crossed(before, t, start + SWIPE_AT + SWIPE_TRAIL + 0.02f)) sweep(x, m.height * 0.6f, 760f, false)
                    }
                }

                // A finger resting on the water keeps sending out rings.
                if (m.touching) {
                    m.stillFor += dt
                    if (m.stillFor >= STILL_EVERY) {
                        m.stillFor = 0f
                        ripple(m.fingerX, m.fingerY, STILL_STRENGTH)
                    }
                }

                if (m.swelling) {
                    // Critically damped spring toward a little past the farthest corner.
                    val target = m.swellCover * SWELL_OVER
                    val w = SWELL_OMEGA
                    val v = m.swellSpeed + (w * w * (target - m.swell) - 2f * w * m.swellSpeed) * dt
                    m.swellSpeed = v
                    m.swell += v * dt
                    if (m.swell >= m.swellCover) settle()
                } else if (m.mixing > 0f) {
                    m.mixing = min(1f, m.mixing + dt / CROSSFADE)
                    if (m.mixing >= 1f) settle()
                }

                stepCaptions(m, dt, reducedMotion)
                m.photoIn.step(dt)
                m.photoSettle.step(dt)
                for (entrance in m.textIn) entrance.step(dt)
                m.elapsed += dt
            }
        }
    }

    // Fresh shaders for each set of photos.
    val paints = remember(motion) { WaterPaints() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            .clearAndSetSemantics {
                contentDescription = "Photo on water. Touch to make ripples, swipe up for the next photo"
                customActions = listOf(
                    CustomAccessibilityAction("Next photo") {
                        sweep(motion.width / 2f, motion.height / 2f, SWELL_MIN_SPEED, true)
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
                    val sx = down.position.x / density.density
                    val sy = down.position.y / density.density
                    if (m.ready) {
                        m.hold = USER_HOLD
                        m.touching = true
                        m.stillFor = 0f
                        m.fingerX = sx
                        m.fingerY = sy
                        m.trailX = sx
                        m.trailY = sy
                        ripple(sx, sy, TOUCH_STRENGTH)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    var lastX = sx
                    var lastY = sy
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        lastX = change.position.x / density.density
                        lastY = change.position.y / density.density
                        if (!change.pressed) break
                        if (!m.touching) continue
                        m.fingerX = lastX
                        m.fingerY = lastY
                        if (hypot(lastX - m.trailX, lastY - m.trailY) >= TRAIL_STEP) {
                            m.trailX = lastX
                            m.trailY = lastY
                            m.stillFor = 0f
                            ripple(lastX, lastY, TRAIL_STRENGTH)
                        }
                    }
                    if (m.touching) {
                        m.touching = false
                        m.hold = USER_HOLD
                        val up = sy - lastY
                        val speed = -tracker.calculateVelocity().y / density.density
                        if (up > SWIPE_DISTANCE && speed > SWIPE_SPEED) sweep(lastX, lastY, speed * 0.8f, true)
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        motion.height = height

        // The first photo fades in while settling to full size.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    motion.elapsed
                    val v = motion.photoSettle.value
                    alpha = motion.photoIn.value
                    val s = if (reducedMotion) 1f else PHOTO_SCALE + (1f - PHOTO_SCALE) * v
                    scaleX = s
                    scaleY = s
                },
        ) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                // Read so the picture is drawn again every frame.
                motion.elapsed
                drawIntoCanvas { drawWater(it.nativeCanvas, motion, paints, reducedMotion) }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height((safeTop + 110f).dp)
                .background(Brush.verticalGradient(listOf(Scrim.copy(alpha = 0.42f), Scrim.copy(alpha = 0f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((safeBottom + 260f).dp)
                .background(Brush.verticalGradient(listOf(Scrim.copy(alpha = 0f), Scrim.copy(alpha = 0.55f)))),
        )

        val brand = TextStyle(
            color = Color.White.copy(alpha = 0.86f),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 2.4.sp,
            fontFeatureSettings = "tnum",
        )
        Box(
            Modifier
                .offset(y = (safeTop + 16f).dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer { entranceLayer(motion, 0, reducedMotion) },
        ) {
            BasicText("STILL WATER", style = brand)
            for (i in 0 until count) {
                BasicText(
                    "%02d / %02d".format(i + 1, count),
                    style = brand,
                    modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { captionLayer(motion, i) },
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = (safeBottom + 74f).dp)
                .fillMaxWidth()
                .height(92.dp)
                .padding(horizontal = 24.dp)
                .graphicsLayer { entranceLayer(motion, 1, reducedMotion) },
        ) {
            for (i in 0 until count) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().graphicsLayer { captionLayer(motion, i) }) {
                    BasicText(
                        photos[i].place.uppercase(),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.78f),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                        ),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    BasicText(
                        photos[i].title,
                        style = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 38.sp,
                            lineHeight = 42.sp,
                            letterSpacing = (-0.8).sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        ),
                    )
                }
            }
        }

        BasicText(
            "Touch the water · swipe up for the next",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (safeBottom + 24f).dp)
                .fillMaxWidth()
                .graphicsLayer { entranceLayer(motion, 2, reducedMotion) },
        )
    }
}

/** Type over the photo: hidden until the photo arrives, then fading and rising in. */
private fun GraphicsLayerScope.entranceLayer(m: WaterMotion, i: Int, reduced: Boolean) {
    m.elapsed
    val v = m.textIn[i].value
    alpha = v
    translationY = if (reduced) 0f else (1f - v) * TEXT_RISE * density
}

/** A caption's fade and lift, read every frame. */
private fun GraphicsLayerScope.captionLayer(m: WaterMotion, i: Int) {
    m.elapsed
    alpha = m.opacity[i].value
    translationY = m.lift[i].value * density
}

/** Restarts the captions' timings when another one is shown, as the animated style does. */
private fun stepCaptions(m: WaterMotion, dt: Float, reduced: Boolean) {
    if (m.caption != m.shownCaption) {
        m.shownCaption = m.caption
        for (i in m.opacity.indices) {
            if (i == m.caption) {
                m.opacity[i].animate(1f, CAPTION_IN, EaseOutStrong, CAPTION_DELAY)
                if (reduced) m.lift[i].animate(0f, 0f, InOutQuad) else m.lift[i].animate(0f, CAPTION_RISE, EaseOutStrong, CAPTION_DELAY)
            } else {
                m.opacity[i].animate(0f, CAPTION_OUT, InOutQuad)
                m.lift[i].animate(if (reduced) 0f else -8f, if (reduced) 0f else CAPTION_OUT, InOutQuad)
            }
        }
    }
    for (i in m.opacity.indices) {
        m.opacity[i].step(dt)
        m.lift[i].step(dt)
    }
}

private class WaterPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val matrix = Matrix()
    // The shader and one image shader per photo, on Android 13 and later.
    val water: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(WATER_AGSL) else null
    val photoShaders = arrayOfNulls<BitmapShader>(SLOTS)
    var shadersFor = Float.NaN
    val clip = Path()
}

/** Where a photo lies when it is scaled to cover the screen, centred on it. */
private fun coverMatrix(matrix: Matrix, image: Bitmap, w: Float, h: Float) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val fit = max(w / iw, h / ih)
    matrix.setScale(fit, fit)
    matrix.postTranslate(w / 2f - iw * fit / 2f, h / 2f - ih * fit / 2f)
}

private fun drawWater(canvas: android.graphics.Canvas, m: WaterMotion, paints: WaterPaints, reduced: Boolean) {
    if (m.width <= 0f || !m.ready) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        drawShaderWater(canvas, m, paints, reduced)
    } else {
        drawFallbackWater(canvas, m, paints, reduced)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun drawShaderWater(canvas: android.graphics.Canvas, m: WaterMotion, paints: WaterPaints, reduced: Boolean) {
    val shader = paints.water as RuntimeShader
    val key = m.width * 10000f + m.height
    if (paints.shadersFor != key) {
        paints.shadersFor = key
        paints.photoShaders.fill(null)
    }
    for (i in 0 until SLOTS) {
        if (paints.photoShaders[i] != null) continue
        val image = m.photos[i] ?: continue
        // Cropped to fill the screen; the shader keeps its samples inside it.
        val s = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        coverMatrix(paints.matrix, image, m.width, m.height)
        s.setLocalMatrix(paints.matrix)
        paints.photoShaders[i] = s
    }
    // A photo still on its way stands in as the first one; the swell waits for it.
    val first = paints.photoShaders[0] ?: return
    val current = paints.photoShaders[m.current % SLOTS] ?: first
    val next = paints.photoShaders[m.next % SLOTS] ?: first
    shader.setInputShader("currentPhoto", current)
    shader.setInputShader("nextPhoto", next)
    shader.setFloatUniform("size", m.width, m.height)
    shader.setFloatUniform("time", m.time)
    shader.setFloatUniform("ripples", m.ripples)
    shader.setFloatUniform("origin", m.originX, m.originY)
    shader.setFloatUniform("swell", m.swell)
    shader.setFloatUniform("mixing", m.mixing)
    shader.setFloatUniform("still", if (reduced) 1f else 0f)
    paints.fill.shader = shader
    canvas.drawRect(0f, 0f, m.width, m.height, paints.fill)
    paints.fill.shader = null
}

/**
 * Below Android 13 there is no runtime shader: the photo stays flat, each ripple is a few thin
 * bright rings running out and fading, and the swell reveals the next photo inside a growing
 * circle with a bright rim.
 */
private fun drawFallbackWater(canvas: android.graphics.Canvas, m: WaterMotion, paints: WaterPaints, reduced: Boolean) {
    val first = m.photos[0] ?: return
    val current = m.photos[m.current % SLOTS] ?: first
    val next = m.photos[m.next % SLOTS] ?: first
    coverMatrix(paints.matrix, current, m.width, m.height)
    paints.photo.alpha = 255
    canvas.drawBitmap(current, paints.matrix, paints.photo)
    if (m.mixing > 0f) {
        coverMatrix(paints.matrix, next, m.width, m.height)
        paints.photo.alpha = (m.mixing * 255f).toInt()
        canvas.drawBitmap(next, paints.matrix, paints.photo)
    }
    if (m.swell > 0f) {
        canvas.save()
        paints.clip.reset()
        paints.clip.addCircle(m.originX, m.originY, m.swell, Path.Direction.CW)
        canvas.clipPath(paints.clip)
        coverMatrix(paints.matrix, next, m.width, m.height)
        paints.photo.alpha = 255
        canvas.drawBitmap(next, paints.matrix, paints.photo)
        canvas.restore()
        paints.ring.strokeWidth = 6f
        paints.ring.color = Color.White.copy(alpha = 0.35f).toArgb()
        canvas.drawCircle(m.originX, m.originY, m.swell, paints.ring)
    }
    paints.ring.strokeWidth = 1.5f
    for (i in 0 until RIPPLES) {
        val strength = m.ripples[i * 4 + 3]
        val age = m.time - m.ripples[i * 4 + 2]
        if (strength <= 0f || age < 0f || age > LIFE) continue
        val fade = strength * exp(-DAMP * age) * min(1f, age / 0.06f) * (if (reduced) 0.5f else 1f)
        for (k in 0 until 3) {
            val r = SPEED * age - k * 24f
            if (r <= 0f) continue
            paints.ring.color = Color.White.copy(alpha = (0.45f * fade / (1 + k)).coerceIn(0f, 1f)).toArgb()
            canvas.drawCircle(m.ripples[i * 4], m.ripples[i * 4 + 1], r, paints.ring)
        }
    }
}
