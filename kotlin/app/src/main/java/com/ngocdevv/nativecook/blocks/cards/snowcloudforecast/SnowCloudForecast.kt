package com.ngocdevv.nativecook.blocks.cards.snowcloudforecast

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// Faithful port of the React Native reference: a #060914 → #1B2547 night sky, a 150 dp
// hairline temperature, and a translucent hourly panel. Fixed dp geometry, a 320 dp cloud, and 72
// six-armed snowflakes falling 380 dp in 2 s. A 90-slot history (30 Hz) remembers the cloud's
// position and emission when each flake was born, so dragging leaves a falling trail.
//
// autoPlay, seconds in LOOP:
//   0.0–0.8 rest, 4 dp sinusoidal bob throughout the loop
//   0.8–1.4 damped cloud breath; retain the reference's exact breath expression
//   1.2–2.0 smoothstep emission 0 → 1; 2.0–4.0 full snow
//   4.0–4.8 smoothstep emission 1 → 0; 4.8–6.8 remaining flakes fall and fade
//   6.8–8.0 rest; all periodic values return to their initial values at 8 s
// Touch uses absolute x, clamped to centre ±110 dp, and emits fully. After release emission
// fades over 0.6 s; x holds 2 s, returns in 0.8 s (ease-in-out cubic), then restarts the loop.

/** Seconds. */
const val LOOP = 8.0
private const val FALL = 2.0
private const val DROP = 380.0
private const val CLOUD = 320f
private const val HISTORY = 90
private const val RATE = 30
private const val HOLD = 2.0
private const val RETURN = 0.8

private val DIM = Color(0xFF8D8F98)
private val FLAKE = Color(0xFFDCEBFF)
private val SKY_TOP = Color(0xFF060914)
private val SKY_BOTTOM = Color(0xFF1B2547)
private val NOW = Color.White.copy(alpha = 0.12f)
private val RULE = Color.White.copy(alpha = 0.12f)
private val CLOUD_MARK = Color(0xFF5B5E68)

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun smooth(x: Double): Double {
    val u = clamp01(x)
    return u * u * (3 - 2 * u)
}
private fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun intensityAt(t: Double) = smooth(seg(t, 1.2, 2.0)) * (1 - smooth(seg(t, 4.0, 4.8)))
private fun breathAt(t: Double): Double {
    val x = seg(t, 0.8, 1.4)
    return if (x <= 0 || x >= 1) {
        0.02 * smooth(seg(t, 1.4, 1.6)) * (1 - smooth(seg(t, 4.8, 6.0)))
    } else {
        0.04 * sin(x * PI) + 0.02 * smooth(x)
    }
}

private class SnowCloudForecastFlake(private val index: Int) {
    // Use Double before casting: the reference's seeded hash depends on the low bits.
    private fun hash(n: Int): Double {
        val x = sin((index + 1) * 91.345 + n * 47.853) * 43758.5453
        return x - floor(x)
    }
    val phase = hash(1)
    val dx = (hash(2) - 0.5) * 260
    val radius = 3.5 + 5 * hash(3)
    val sway = 5 + 10 * hash(4)
    val threshold = 0.05 + 0.9 * hash(5)
    val spin = (hash(6) - 0.5) * 4
    val path = Path().apply {
        for (k in 0 until 6) {
            val a = k * PI / 3
            val ca = cos(a)
            val sa = sin(a)
            moveTo(0f, 0f)
            lineTo((ca * radius).toFloat(), (sa * radius).toFloat())
            val bx = ca * radius * 0.6
            val by = sa * radius * 0.6
            val barb = radius * 0.32
            moveTo(bx.toFloat(), by.toFloat())
            lineTo((bx + cos(a + 0.7) * barb).toFloat(), (by + sin(a + 0.7) * barb).toFloat())
            moveTo(bx.toFloat(), by.toFloat())
            lineTo((bx + cos(a - 0.7) * barb).toFloat(), (by + sin(a - 0.7) * barb).toFloat())
        }
    }
}

/** Plain frame state: only [clock] is observable, and it is read exclusively by the Canvas. */
private class SnowCloudForecastMotion(val cx: Double) {
    val clock = mutableDoubleStateOf(0.0)
    var elapsed = 0.0
    var origin = 0.0
    var tau = 0.0
    var cloudX = cx
    var bob = 0.0
    var breath = 0.0
    var touching = false
    var manual = false
    var released = -1.0
    var fingerX = cx
    val xs = DoubleArray(HISTORY) { cx }
    val intensities = DoubleArray(HISTORY)
    private var lastSample = -1
    private var previousIntensity = 0.0

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val now = elapsed
        var x = cx
        var intensity = 0.0
        if (touching) {
            x = fingerX
            intensity = 1.0
        } else if (manual) {
            val since = now - released
            val k = easeInOutCubic(clamp01((since - HOLD) / RETURN))
            x = fingerX + (cx - fingerX) * k
            intensity = 1 - smooth(since / 0.6)
            if (since > HOLD + RETURN) {
                manual = false
                origin = now
            }
        } else if (autoPlay) {
            val t = (now - origin) % LOOP
            intensity = intensityAt(t)
            breath = breathAt(t)
        }
        tau = (now - origin) % LOOP
        bob = sin(tau / LOOP * PI * 2) * 4
        val sample = floor(now * RATE).toInt()
        // At normal frame rates this writes exactly the RN slot. Fill skipped slots too:
        // an emulator below 30 fps must not revive samples from three seconds earlier.
        val first = max(lastSample + 1, sample - HISTORY + 1)
        for (i in first..sample) {
            val k = (i - lastSample).toDouble() / max(1, sample - lastSample)
            val slot = i % HISTORY
            xs[slot] = cloudX + (x - cloudX) * k
            intensities[slot] = previousIntensity + (intensity - previousIntensity) * k
        }
        val slot = sample % HISTORY
        xs[slot] = x
        intensities[slot] = intensity
        lastSample = sample
        previousIntensity = intensity
        cloudX = x
        clock.doubleValue = now
    }

    fun touch(x: Float) {
        fingerX = x.toDouble().coerceIn(cx - 110, cx + 110)
    }
}

private data class SnowCloudForecastHour(val time: String, val temperature: String, val snow: Boolean)
private val HOURS = listOf(
    SnowCloudForecastHour("Now", "−8°", true),
    SnowCloudForecastHour("15", "−8°", true),
    SnowCloudForecastHour("16", "−9°", true),
    SnowCloudForecastHour("17", "−9°", false),
    SnowCloudForecastHour("18", "−10°", false),
    SnowCloudForecastHour("19", "−11°", true),
    SnowCloudForecastHour("20", "−11°", false),
)

private fun textStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
fun SnowCloudForecast(
    modifier: Modifier = Modifier,
    city: String = "Northfield",
    other: String = "Halden",
    temperature: String = "−8°",
    range: String = "↑ 2°   ↓ −11°",
    summary: String = "Snow is on its way",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val cloud = remember(context) {
        context.assets.open("snowcloudforecast/snowcloudforecast_cloud.png").use { BitmapFactory.decodeStream(it)!! }
            .apply { prepareToDraw() }
    }
    val flakes = remember { List(72) { SnowCloudForecastFlake(it) } }
    val snowPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(220, 235, 255)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }
    val imagePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val imageBounds = remember { RectF(-CLOUD / 2, -CLOUD / 2, CLOUD / 2, CLOUD / 2) }
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = safeArea.calculateTopPadding().value
    val safeBottom = safeArea.calculateBottomPadding().value
    val cloudTop = safeTop + 20f
    val snowTop = cloudTop + CLOUD * 0.66f

    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SKY_TOP, SKY_BOTTOM)))) {
        val screenWidth = maxWidth
        val motion = remember(screenWidth.value) { SnowCloudForecastMotion(screenWidth.value / 2.0) }
        LaunchedEffect(motion, active, reduced, autoPlay) {
            // Like RN, an idle interactive block still keeps its clock; reduced motion stops it.
            if (!active || reduced) return@LaunchedEffect
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                motion.advance(dt, autoPlay)
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(motion, reduced, density) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    motion.touching = true
                    motion.manual = true
                    motion.touch(down.position.x / density)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            motion.touch(change.position.x / density)
                            change.consume()
                        }
                    } finally {
                        motion.touching = false
                        motion.released = motion.elapsed
                    }
                }
            },
        ) {
            // Explicit line origins preserve RN's negative temperature margin and range gap.
            Box(Modifier.offset(x = 24.dp, y = (snowTop + 60f).dp).width(screenWidth - 48.dp)) {
                // Roboto's thin numerals sit 18 dp below SF's ink in the same 150/156 line.
                Text(temperature, modifier = Modifier.offset(x = (-6).dp, y = (-18).dp), maxLines = 1, style = textStyle(150, 156, FontWeight.Thin).copy(letterSpacing = (-8).sp))
                Text(range, modifier = Modifier.offset(y = 152.dp), maxLines = 1, style = textStyle(16, 22, FontWeight.SemiBold))
                Text(summary, modifier = Modifier.offset(y = 178.dp), maxLines = 1, style = textStyle(30, 36, FontWeight.Light).copy(letterSpacing = (-0.5).sp))
            }
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "$city, $temperature, $summary" }) {
                val now = motion.clock.doubleValue
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                for (flake in flakes) {
                    val q = motion.tau / FALL + flake.phase
                    val p = q - floor(q)
                    val born = now - p * FALL
                    val slot = ((floor(born * RATE).toInt() % HISTORY) + HISTORY) % HISTORY
                    val intensity = motion.intensities[slot]
                    if (intensity < flake.threshold) continue
                    val alpha = smooth(p / 0.1) * (1 - smooth((p - 0.75) / 0.25)) * clamp01((intensity - flake.threshold) * 6)
                    if (alpha <= 0.01) continue
                    val x = motion.xs[slot] + flake.dx * (0.55 + 0.45 * p) + sin(p * PI * 2.6 + flake.phase * 6.28) * flake.sway
                    val y = snowTop + p * DROP
                    snowPaint.alpha = (alpha * 0.95 * 255).toInt().coerceIn(0, 255)
                    snowPaint.strokeWidth = max(1.0, flake.radius * 0.22).toFloat()
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    canvas.rotate((p * flake.spin * 180 / PI).toFloat())
                    canvas.drawPath(flake.path, snowPaint)
                    canvas.restore()
                }
                canvas.save()
                canvas.translate(motion.cloudX.toFloat(), cloudTop + CLOUD / 2 + motion.bob.toFloat())
                val scale = (1 + motion.breath).toFloat()
                canvas.scale(scale, scale)
                canvas.drawBitmap(cloud, null, imageBounds, imagePaint)
                canvas.restore()
                canvas.restore()
            }
            Row(
                Modifier.offset(y = (safeTop + 10f).dp).fillMaxWidth().height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(city, style = textStyle(17, 21, FontWeight.Bold))
                Text(other, style = textStyle(17, 21, FontWeight.Medium, DIM))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = (safeBottom + 14f).dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                // RN's border participates in layout in addition to its 14 dp padding.
                .padding(15.dp),
        ) {
            Text("Snow from 14:00 · 4 cm by evening", modifier = Modifier.padding(start = 4.dp, bottom = 10.dp), style = textStyle(13, 16, FontWeight.SemiBold, DIM))
            Box(Modifier.fillMaxWidth().height((1f / density).dp).background(RULE))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HOURS.forEachIndexed { i, hour ->
                    Column(
                        Modifier.width(46.dp)
                            .background(if (i == 0) NOW else Color.Transparent, RoundedCornerShape(23.dp))
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(hour.time, style = textStyle(13, 16, FontWeight.Medium, if (i == 0) Color.White else DIM))
                        Canvas(Modifier.padding(vertical = 10.dp).size(14.dp)) {
                            if (hour.snow) {
                                drawCircle(FLAKE, radius = size.width / 2 - 1.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx()))
                            } else {
                                drawCircle(CLOUD_MARK)
                            }
                        }
                        Text(hour.temperature, style = textStyle(14, 17, FontWeight.Medium))
                    }
                }
            }
        }
    }
}
