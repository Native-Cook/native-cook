package com.ngocdevv.nativecook.blocks.progress.petalloaderspin

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seconds; identical to the React Native reference. */
const val LOOP = 6.0
private const val COUNT = 12
private const val RING = 118f
private const val DISC = 34f
private val BACKGROUND = Color(0xFF04030A)
private val LAVENDER = Color(0xFFB9A8FF)
private val STEPS = listOf("Mixing the light", "Adding depth", "Polishing the glass")
private val NO_FONT_PADDING = PlatformTextStyle(includeFontPadding = false)

private fun segment(t: Double, start: Double, end: Double): Double =
    ((t - start) / (end - start)).coerceIn(0.0, 1.0)

private fun easeInOutCubic(x: Double): Double =
    if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2

private fun percentage(t: Double): Double =
    easeInOutCubic(segment(t, 0.2, 5.4)) * (1 - segment(t, 5.6, LOOP))

private fun stepOpacity(t: Double, k: Int): Float {
    val start = k * 2.0
    val opacity = segment(t, start, start + 0.3) * (1 - segment(t, start + 1.7, start + 2))
    return max(opacity, if (k == 0) segment(t, 5.7, LOOP) else 0.0).toFloat()
}

private class Playback {
    var elapsed by mutableDoubleStateOf(0.0)
    var clock by mutableDoubleStateOf(0.0)
    var spin by mutableDoubleStateOf(0.0)
    var spinVelocity = 0.0
    var flipAt by mutableDoubleStateOf(-10.0)

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (autoPlay) clock = elapsed % LOOP
        // Keep the reference's frame-based integration and exact exponential velocity decay.
        spin += spinVelocity * dt
        spinVelocity *= exp(-2.5 * dt)
    }

    fun flip(): Double {
        val age = elapsed - flipAt
        return if (age < 0.6) easeInOutCubic(segment(age, 0.0, 0.6)) else 0.0
    }
}

/** Cached paints and a software-rasterized Gaussian glint, which also works below API 33. */
private class RingPainter(density: Float) {
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, RING * 2,
            intArrayOf(AndroidColor.argb(71, 110, 70, 255), AndroidColor.argb(0, 110, 70, 255)),
            null, Shader.TileMode.CLAMP,
        )
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glintBounds = RectF(-48f, -48f, 48f, 48f)
    val glint = Bitmap.createBitmap(
        (96 * density).roundToInt().coerceAtLeast(1),
        (96 * density).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        AndroidCanvas(bitmap).apply {
            scale(density, density)
            translate(48f, 48f)
            drawCircle(-DISC * 0.3f, -DISC * 0.35f, DISC * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                // Android's radius maps to sigma = 0.57735 * radius + 0.5.
                maskFilter = BlurMaskFilter((6f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            })
        }
    }

    fun draw(canvas: AndroidCanvas, t: Double, spin: Double, flip: Double, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, RING * 2, halo)
        canvas.restore()
        val drift = t / LOOP * PI * 2 + spin
        repeat(COUNT) { i ->
            val a = drift + i * PI * 2 / COUNT - PI / 2
            val x = cx + cos(a).toFloat() * RING
            val y = cy + sin(a).toFloat() * RING
            val turn = PI * 2 * (t / 2 - i.toDouble() / COUNT) + flip * PI
            val face = cos(turn)
            val squeeze = max(0.08, abs(face)).toFloat()
            val lit = max(0.0, face)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate((a * 180 / PI).toFloat())
            canvas.scale(1f, squeeze)
            body.alpha = 255
            body.shader = LinearGradient(
                -DISC, -DISC, DISC, DISC,
                intArrayOf(
                    AndroidColor.argb(((0.35 + 0.4 * lit) * 255).roundToInt(), (70 + 90 * lit).roundToInt(), (90 + 60 * lit).roundToInt(), 255),
                    AndroidColor.argb(82, 120, 40, 220),
                    AndroidColor.argb(128, 20, 10, 80),
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(0f, 0f, DISC, body)
            rim.color = AndroidColor.rgb(185, 168, 255)
            rim.alpha = ((0.55 + 0.45 * lit) * 255).roundToInt()
            rim.strokeWidth = 2f / squeeze
            canvas.drawCircle(0f, 0f, DISC - 1f, rim)
            if (lit > 0.2) {
                glintPaint.alpha = (0.5 * (lit - 0.2) * 255).roundToInt()
                canvas.drawBitmap(glint, null, glintBounds, glintPaint)
            }
            canvas.restore()
        }
    }
}

// The reference composites glass fills in linear light, but its halo, rim and glint in sRGB.
// Explicit compositing preserves its gradient stops and overlapping petals on Android too.
private const val GLASS_AGSL = """
uniform float density;
uniform float2 centre;
uniform float4 petals[12];
uniform shader glint;

float3 decodeRGB(float3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c));
}

float3 encodeRGB(float3 c) {
    return mix(c * 12.92, 1.055 * pow(max(c, 0.0), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}

half4 main(float2 pixelPosition) {
    float2 position = pixelPosition / density;
    float3 color = float3(4.0, 3.0, 10.0) / 255.0;
    float radius = length(position - centre);
    if (radius > 236.0) return half4(color, 1.0);
    color = mix(color, float3(110.0, 70.0, 255.0) / 255.0, 0.28 * (1.0 - radius / 236.0));
    // Every petal effect is within 70 dp of a centre on the 118 dp ring.
    if (radius < 48.0 || radius > 188.0) return half4(color, 1.0);
    for (int i = 0; i < 12; i++) {
        float4 petal = petals[i];
        float2 radial = petal.xy;
        float2 tangent = float2(-radial.y, radial.x);
        float2 delta = position - centre - radial * 118.0;
        if (dot(delta, delta) > 4900.0) continue;
        float inverseSqueeze = petal.z;
        float lit = petal.w;
        float2 local = float2(dot(delta, radial), dot(delta, tangent) * inverseSqueeze);
        float distanceSquared = dot(local, local);
        if (distanceSquared > 4900.0) continue;
        float d = sqrt(distanceSquared);
        float2 normal = local / max(d, 0.001);
        float aa = max(0.01, 0.5 / density * length(float2(normal.x, normal.y * inverseSqueeze)));
        float coverage = 1.0 - smoothstep(34.0 - aa, 34.0 + aa, d);
        if (coverage > 0.0) {
            float u = clamp((local.x + local.y + 68.0) / 136.0, 0.0, 1.0);
            float4 first = float4(floor(70.0 + 90.0 * lit + 0.5) / 255.0, floor(90.0 + 60.0 * lit + 0.5) / 255.0, 1.0, 0.35 + 0.4 * lit);
            float4 middle = float4(120.0 / 255.0, 40.0 / 255.0, 220.0 / 255.0, 0.32);
            float4 last = float4(20.0 / 255.0, 10.0 / 255.0, 80.0 / 255.0, 0.5);
            float4 body = u < 0.55 ? mix(first, middle, u / 0.55) : mix(middle, last, (u - 0.55) / 0.45);
            color = encodeRGB(mix(decodeRGB(color), decodeRGB(body.rgb), body.a * coverage));
        }
        float rim = 1.0 - smoothstep(-aa, aa, abs(d - 33.0) - inverseSqueeze);
        color = mix(color, float3(185.0, 168.0, 255.0) / 255.0, rim * (0.55 + 0.45 * lit));
        if (lit > 0.2) {
            float2 uv = local + 48.0;
            if (uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 96.0 && uv.y <= 96.0) {
                float alpha = glint.eval(uv * density).a * 0.5 * (lit - 0.2);
                color = mix(color, float3(1.0), alpha);
            }
        }
    }
    return half4(color, 1.0);
}
"""

@RequiresApi(33)
private class GlassShader(private val density: Float, glint: Bitmap) {
    private val petals = FloatArray(COUNT * 4)
    private val shader = RuntimeShader(GLASS_AGSL).apply {
        setFloatUniform("density", density)
        setInputShader("glint", BitmapShader(glint, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        })
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = this@GlassShader.shader }

    fun draw(canvas: AndroidCanvas, width: Float, height: Float, t: Double, spin: Double, flip: Double, cx: Float, cy: Float) {
        // These angles are uniform across every pixel; evaluate them only once per frame.
        val drift = t / LOOP * PI * 2 + spin
        repeat(COUNT) { i ->
            val angle = drift + i * PI * 2 / COUNT - PI / 2
            val face = cos(PI * 2 * (t / 2 - i.toDouble() / COUNT) + flip * PI)
            val offset = i * 4
            petals[offset] = cos(angle).toFloat()
            petals[offset + 1] = sin(angle).toFloat()
            petals[offset + 2] = (1.0 / max(0.08, abs(face))).toFloat()
            petals[offset + 3] = max(0.0, face).toFloat()
        }
        shader.setFloatUniform("centre", cx, cy)
        shader.setFloatUniform("petals", petals)
        // The root already supplies the same solid background outside the halo.
        val radius = RING * 2 * density
        canvas.drawRect(
            max(0f, cx * density - radius), max(0f, cy * density - radius),
            (cx * density + radius).coerceAtMost(width), (cy * density + radius).coerceAtMost(height), paint,
        )
    }
}

/**
 * Twelve radial glass discs; three face-turn waves and one orbit every six seconds.
 * Progress uses cubic easing over 0.2–5.4 s and returns over 5.6–6.0 s.
 * As in RN, a drag adds decaying spin without stopping playback; a tap flips for 0.6 s.
 * Reduced motion freezes the current frame and disables gestures, matching the reference.
 */
@Composable
fun PetalLoaderSpin(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    BlockSystemBars(darkIcons = false)
    val density = LocalDensity.current.density
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val playback = remember { Playback() }
    val painter = remember(density) { RingPainter(density) }
    val shader = remember(density, painter) {
        if (Build.VERSION.SDK_INT >= 33) GlassShader(density, painter.glint) else null
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reduced, autoPlay) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
            previous = now
            playback.advance(dt, autoPlay)
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val cx = constraints.maxWidth / density / 2
        val cy = constraints.maxHeight / density * 0.44f
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "A ring of glass petals, rendering. Drag round to spin, tap to flip." }
                .pointerInput(reduced, cx, cy) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastAngle = atan2(down.position.y / density - cy, down.position.x / density - cx).toDouble()
                        var panning = false
                        var cancelled = false
                        var last = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            last = change
                            if (!change.pressed) break
                            if (!panning && (change.position - down.position).getDistance() >= 4f * density) panning = true
                            if (panning) {
                                val angle = atan2(change.position.y / density - cy, change.position.x / density - cx).toDouble()
                                var delta = angle - lastAngle
                                if (delta > PI) delta -= PI * 2
                                if (delta < -PI) delta += PI * 2
                                playback.spin += delta
                                playback.spinVelocity = delta * 60
                                lastAngle = angle
                                change.consume()
                            }
                        }
                        // Match Gesture.Tap's default maximum duration and exclusive pan priority.
                        if (!cancelled && !panning && last.uptimeMillis - down.uptimeMillis <= 500L) {
                            playback.flipAt = playback.elapsed
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                    }
                },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    shader.draw(native, size.width, size.height, playback.clock, playback.spin, playback.flip(), cx, cy)
                } else {
                    // API 26–32 keeps the geometry and gestures, with standard sRGB fill blending.
                    native.save()
                    native.scale(density, density)
                    painter.draw(native, playback.clock, playback.spin, playback.flip(), cx, cy)
                    native.restore()
                }
            }
        }

        Row(
            Modifier.padding(horizontal = 24.dp).padding(top = insets.calculateTopPadding() + 8.dp)
                .fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lumen", style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, platformStyle = NO_FONT_PADDING))
            Spacer(Modifier.weight(1f))
            Text("Scene 4 of 6", style = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, platformStyle = NO_FONT_PADDING))
        }

        Row(
            Modifier.padding(top = (cy - 30).dp).fillMaxWidth().height(60.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { digit -> PercentDigit(digit, playback) }
            Text("%", Modifier.padding(start = 2.dp), style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, fontWeight = FontWeight.Bold, platformStyle = NO_FONT_PADDING))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp)
                .padding(bottom = insets.calculateBottomPadding() + 14.dp).fillMaxWidth(),
        ) {
            Text("Rendering “Glass Garden”", maxLines = 1, style = TextStyle(color = Color.White, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, platformStyle = NO_FONT_PADDING))
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(26.dp)) {
                STEPS.forEachIndexed { k, step ->
                    Text(
                        step,
                        Modifier.graphicsLayer {
                            val opacity = stepOpacity(playback.clock, k)
                            alpha = opacity
                            translationY = 8 * (1 - opacity) * density
                        },
                        style = TextStyle(color = LAVENDER, fontSize = 16.sp, platformStyle = NO_FONT_PADDING),
                    )
                }
            }
            Canvas(Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp)) {
                val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.1f), cornerRadius = radius)
                drawRoundRect(Color(0xFF8F7BFF), size = size.copy(width = size.width * percentage(playback.clock).toFloat()), cornerRadius = radius)
            }
            Box(
                Modifier.padding(top = 22.dp).fillMaxWidth().height(58.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Run in background", style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, platformStyle = NO_FONT_PADDING))
            }
        }
    }
}

/** The RN rolling columns snap to the selected row; all text layout is retained between frames. */
@Composable
private fun PercentDigit(digit: Int, playback: Playback) {
    val density = LocalDensity.current.density
    Box(Modifier.size(28.dp, 48.dp).clip(RoundedCornerShape(0.dp))) {
        Column(Modifier.graphicsLayer {
            val value = (percentage(playback.clock) * 100).roundToInt().toString().padStart(3, ' ')[digit]
            translationY = (if (value == ' ') 48f else -48f * value.digitToInt()) * density
        }.wrapContentHeight(Alignment.Top, unbounded = true)) {
            repeat(10) { n ->
                Box(Modifier.width(28.dp).height(48.dp), contentAlignment = Alignment.Center) {
                    Text(n.toString(), style = TextStyle(color = Color.White, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, fontFeatureSettings = "tnum", platformStyle = NO_FONT_PADDING))
                }
            }
        }
    }
}
