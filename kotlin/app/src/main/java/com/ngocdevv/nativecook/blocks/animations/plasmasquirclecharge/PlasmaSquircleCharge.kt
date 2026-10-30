package com.ngocdevv.nativecook.blocks.animations.plasmasquirclecharge

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Seconds; identical to the React Native reference. */
const val LOOP = 6f
private const val RING_W = 156f
private const val RING_H = 116f
private const val CORNER = 74f
private const val RISE = 1.4
private const val HOLD = 2.0
private const val FALL = 1.0
private val Background = Color(0xFF05040B)
private val Ink = Color(0xFFF4F2FF)
private val Dim = Color(0xFF8A86A3)
private val Glow = Color(0xFFA898FF)
private val Copy = TextStyle(
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
private fun segment(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun levelAt(t: Float): Float {
    val x = segment(t, .8f, 2f)
    val ease = if (x < .5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    val build = .6f * ease + .4f * (1 - (1 - segment(t, 2f, 2.4f)).pow(3))
    return build * (1 - segment(t, 4.4f, 5.4f).pow(3))
}
private fun roomAt(t: Float, k: Int) = smooth(segment(t, 2.45f + .18f * k, 2.7f + .18f * k)) *
    (1 - smooth(segment(t, 4.4f + .1f * k, 4.9f + .1f * k)))

/** Frame values are read only in drawing/layer lambdas, never in screen composition. */
private class PlasmaSquircleChargePlayback(reduced: Boolean) {
    val level = mutableFloatStateOf(if (reduced) 1f else 0f)
    val phase = mutableFloatStateOf(0f)
    val rooms = List(3) { mutableFloatStateOf(if (reduced) 1f else 0f) }
    private var elapsed = 0.0
    private var origin = 0.0
    private var released = -1.0
    private var manual = false
    private var touching = false

    fun press() {
        touching = true
        manual = true
    }

    fun release() {
        touching = false
        released = elapsed
    }

    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val t = ((elapsed - origin) % LOOP).toFloat()
        phase.floatValue = t / LOOP
        if (touching || manual) {
            if (touching) level.floatValue = (level.floatValue + dt / RISE).toFloat().coerceAtMost(1f)
            else if (elapsed - released > HOLD) level.floatValue = (level.floatValue - dt / FALL).toFloat().coerceAtLeast(0f)
            val l = level.floatValue
            rooms[0].floatValue = smooth((l - .9f) / .04f)
            rooms[1].floatValue = smooth((l - .94f) / .04f)
            rooms[2].floatValue = smooth((l - .98f) / .02f)
            if (!touching && l <= 0f) {
                manual = false
                origin = elapsed
            }
            return
        }
        level.floatValue = if (autoPlay) levelAt(t) else 0f
        rooms.forEachIndexed { k, light -> light.floatValue = if (autoPlay) roomAt(t, k) else 0f }
    }
}

private const val PLASMA = """
uniform float2 centre;
uniform float2 halfSize;
uniform float corner;
uniform float cover;
uniform float charge;
uniform float phase;
uniform float p3;

const float TAU = 6.28318530718;

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float roundBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 xy) {
  float2 p = xy - centre;
  float a = atan(p.y, p.x);
  float d = roundBox(p, halfSize, corner);
  float T = phase * TAU;

  // The wandering of the arc: three waves round the outline, each turning a whole number of
  // times per loop.
  float amp = 1.2 + 3.4 * charge;
  float wobble = amp * (0.5 * sin(a * 7.0 + T * 3.0) + 0.3 * sin(a * 13.0 - T * 5.0) + 0.2 * sin(a * 29.0 + T * 11.0));
  float e = d + wobble;

  // How much of the outline is lit: from the middles of the sides towards the corners.
  float da = abs(mod(a + 0.785398, 1.570796) - 0.785398);
  float reach = cover * 0.86;
  float lit = cover >= 0.999 ? 1.0 : smoothstep(reach + 0.05, reach - 0.05, da) * step(0.001, cover);

  float crackle = 0.72 + 0.28 * sin(a * 41.0 + T * 23.0) * sin(a * 17.0 - T * 17.0);

  float core = exp(-abs(e) / 1.2);
  float halo = exp(-abs(e) / 10.0);
  float bloom = 1.0 / (1.0 + (d / 40.0) * (d / 40.0));
  float rest = exp(-abs(d) / 0.9) * 0.14;
  // The stage: a violet pool of light under the hub, a little brighter when charged.
  float2 s = (p - float2(0.0, 40.0)) / float2(230.0, 150.0);
  float stage = exp(-dot(s, s) * 1.6);

  float3 white = float3(0.95, 0.93, 1.0);
  float3 violet = float3(0.60, 0.48, 1.0);
  float3 blue = float3(0.30, 0.28, 0.95);
  float3 bg = float3(0.020, 0.016, 0.043);

  float on = charge * lit * crackle;
  float3 col = bg + float3(0.10, 0.07, 0.24) * stage * (0.55 + 0.45 * charge);
  col += white * rest;
  col += violet * halo * on * 0.9;
  col += white * core * on * 1.3;
  col += blue * bloom * charge * charge * 0.38;
  col = 1.0 - exp(-col * 1.4);
  return half4(half3(p3 > 0.5 ? toP3(col) : col), 1.0);
}
"""

@RequiresApi(33)
private class PlasmaSquircleChargeField {
    private val shader = RuntimeShader(PLASMA)
    private val paint = Paint().apply { this.shader = this@PlasmaSquircleChargeField.shader }

    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, level: Float, phase: Float) {
        val cover = smooth(level / .6f)
        shader.setFloatUniform("centre", width / 2, height * .52f)
        shader.setFloatUniform("halfSize", RING_W, RING_H)
        shader.setFloatUniform("corner", CORNER)
        shader.setFloatUniform("cover", cover)
        shader.setFloatUniform("charge", .55f * cover + .45f * smooth((level - .6f) / .4f))
        shader.setFloatUniform("phase", phase)
        // Android emits sRGB. Only the reference's iOS Skia output needs P3 conversion.
        shader.setFloatUniform("p3", 0f)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/**
 * Rest 0–.8; crawl .8–2; ignite 2–2.4; hold to4.4; down to5.4; rest to6.
 * Rooms stagger on from2.45 (.18s apart), off from4.4 (.1s apart).
 * Hold rises in1.4s, release waits2s then falls in1s; reduced motion shows Online.
 */
@Composable
fun PlasmaSquircleCharge(
    modifier: Modifier = Modifier,
    brand: String = "lumen",
    idle: String = "Hold to wake",
    online: String = "Online",
    detail: String = "Lights, music and climate wake together.",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val fonts = remember(context) {
        listOf("ArchivoExpanded-Black", "Archivo-Medium", "Archivo-SemiBold").map { face ->
            FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
                context.assets, "plasmasquirclecharge/plasmasquirclecharge_$face.ttf",
            )))
        }
    }
    val black = fonts[0]
    val medium = fonts[1]
    val semiBold = fonts[2]
    val artwork = remember(context, density) { PlasmaSquircleChargeArtwork(context.assets, density) }
    val playback = remember(reduced) { PlasmaSquircleChargePlayback(reduced) }
    val field = remember { if (Build.VERSION.SDK_INT >= 33) PlasmaSquircleChargeField() else null }
    val fallback = remember { if (Build.VERSION.SDK_INT < 33) PlasmaSquircleChargeFallback() else null }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(autoPlay, reduced, active, playback) {
        if (reduced || !active) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
            previous = now
            playback.frame(dt, autoPlay)
        }
    }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(
        modifier.fillMaxSize().background(Background).pointerInput(reduced, playback) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                playback.press()
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                try {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        val pressed = event.changes.any { it.id == down.id && it.pressed }
                    } while (pressed)
                } finally { playback.release() }
            }
        },
    ) {
        val cy = maxHeight.value * .52f
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "$brand hub. Hold to wake the home." }) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val saved = native.save()
                native.scale(density, density)
                val level = playback.level.floatValue
                val phase = playback.phase.floatValue
                if (field != null && Build.VERSION.SDK_INT >= 33) {
                    field.draw(native, size.width / density, size.height / density, level, phase)
                } else fallback?.draw(native, size.width / density, size.height / density, level, phase)
                artwork.draw(native, size.width / density, size.height / density, level)
                native.restoreToCount(saved)
            }
        }
        Column(Modifier.offset(y = safe.calculateTopPadding() + 8.dp).padding(horizontal = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(brand, color = Ink, fontFamily = black, fontSize = 20.sp, letterSpacing = (-.3).sp, style = Copy)
                Text("Home · 3 rooms", color = Dim, fontFamily = medium, fontSize = 13.sp, style = Copy)
            }
            Text(
                "Wake the\nhome.", color = Ink, fontFamily = black, fontSize = 38.sp, lineHeight = 44.sp,
                letterSpacing = (-.8).sp, style = Copy, modifier = Modifier.padding(top = 18.dp),
            )
        }
        Box(Modifier.offset(y = (cy + 150).dp).fillMaxWidth().height(30.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                idle, color = Ink, fontFamily = black, fontSize = 22.sp, lineHeight = 30.sp,
                letterSpacing = (-.2).sp, style = Copy,
                modifier = Modifier.graphicsLayer { alpha = 1 - smooth((playback.level.floatValue - .75f) / .25f) },
            )
            Text(
                online, color = Ink, fontFamily = black, fontSize = 22.sp, lineHeight = 30.sp,
                letterSpacing = (-.2).sp, style = Copy,
                modifier = Modifier.graphicsLayer { alpha = smooth((playback.level.floatValue - .75f) / .25f) },
            )
        }
        Text(
            detail, color = Dim, fontFamily = medium, fontSize = 14.sp, lineHeight = 20.sp,
            textAlign = TextAlign.Center, style = Copy,
            modifier = Modifier.offset(y = (cy + 186).dp).padding(horizontal = 32.dp).fillMaxWidth(),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp)
                .padding(bottom = safe.calculateBottomPadding() + 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("Living room", "Kitchen", "Studio").forEachIndexed { k, name ->
                Column(
                    Modifier.weight(1f).height(88.dp).drawBehind {
                        val light = playback.rooms[k].floatValue
                        drawRoundRect(Color(0xFF100E1A), cornerRadius = CornerRadius(20.dp.toPx()))
                        drawRoundRect(
                            if (light > .5f) Glow.copy(alpha = .55f) else Color(0xFF25213A),
                            topLeft = Offset(.5.dp.toPx(), .5.dp.toPx()),
                            size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                            cornerRadius = CornerRadius(19.5.dp.toPx()), style = Stroke(1.dp.toPx()),
                        )
                    }.padding(13.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Canvas(Modifier.size(10.dp).graphicsLayer {
                        val scale = 1 + .25f * sin(PI.toFloat() * playback.rooms[k].floatValue)
                        scaleX = scale
                        scaleY = scale
                    }) {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            val saved = native.save()
                            native.scale(density, density)
                            artwork.drawDot(native, playback.rooms[k].floatValue)
                            native.restoreToCount(saved)
                        }
                    }
                    Text(name, color = Ink, fontFamily = semiBold, fontSize = 13.sp, style = Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(16.dp)) {
                        Text(
                            "Off", color = Dim, fontFamily = medium, fontSize = 12.sp, lineHeight = 16.sp, style = Copy,
                            modifier = Modifier.graphicsLayer { alpha = 1 - playback.rooms[k].floatValue },
                        )
                        Text(
                            "On", color = Glow, fontFamily = medium, fontSize = 12.sp, lineHeight = 16.sp, style = Copy,
                            modifier = Modifier.graphicsLayer { alpha = playback.rooms[k].floatValue },
                        )
                    }
                }
            }
        }
    }
}
