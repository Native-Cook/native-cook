package com.ngocdevv.nativecook.blocks.playground.noodlenetorbit

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.*

// An evening breathing session on warm black. The red bead presses a cream rim into a woven
// band: its inner edge dents 64 dp, its outer edge bulges 12 dp, and four wavy strands along
// the band cross eight gently bent strands. Instrument Serif phase words sit inside the circle.
//
// LOOP = 8 s: rest 0–0.6; inhale 0.6–3 (cubic); hold 3–4.6 (3 dp tremble);
// exhale 4.6–7.2 (cubic); rest 7.2–8, identical to t = 0. The bead makes one clockwise turn
// over 0.6–7.2 (ease-in-out sine), the weave waves twice per loop. Phase words crossfade and
// rise 8 dp over 0.3 s. Touch holds for 2 s after release, returns to the top in 0.8 s, then
// restarts the loop. Reduced motion keeps the resting state and disables the gesture.
private const val LOOP = 8f
private const val INWARD = 64f
private const val BULGE = 12f
private const val BEAD_GAP = 9f
private const val MAX_PRESS = 1.15f
private const val ALONG = 6
private const val ACROSS = 8
private const val HOLD = 2.0
private const val RETURN = 0.8
private const val ASSETS = "noodlenetorbit/noodlenetorbit_"
private val BACKGROUND = Color(0xFF0D0B0A)
private val LIFT = Color(0xFF1E1915)
private val CREAM = Color(0xFFF3EDE4)
private val DIM = CREAM.copy(alpha = 0.5f)
private val FAINT = CREAM.copy(alpha = 0.18f)
private val BEAD = Color(0xFFFF3B2F)
private val WARM = Color(0xFFFF7A3D)
private val REST = -PI.toFloat() / 2
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun cubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun smooth(x: Float): Float {
    val u = x.coerceIn(0f, 1f)
    return u * u * (3 - 2 * u)
}
private data class Frame(val angle: Float = REST, val press: Float = 0f, val time: Float = 0f)
private fun beadAt(t: Float): Frame {
    val angle = REST + 2 * PI.toFloat() * (1 - cos(PI.toFloat() * seg(t, 0.6f, 7.2f))) / 2
    val tremble = sin(seg(t, 3f, 4.6f) * PI.toFloat()) * sin(t * 19) * 3
    return Frame(angle, cubic(seg(t, 0.6f, 3f)) * (1 - cubic(seg(t, 4.6f, 7.2f))) + tremble / INWARD, t)
}
private fun bandPoint(
    cx: Float, cy: Float, radius: Float, angle: Float, half: Float,
    inward: Float, bulge: Float, s: Float, q: Float,
): Offset {
    val env = sin(PI.toFloat() * s.coerceIn(0f, 1f)).pow(2)
    val inner = radius - inward * env
    val outer = radius + bulge * env
    val phi = angle - half + 2 * half * s
    val r = inner + (outer - inner) * q
    return Offset(cx + cos(phi) * r, cy + sin(phi) * r)
}

// A small software surface keeps Skia's Gaussian mask blur faithful on every supported API,
// including API 26–27, whose hardware canvas does not support BlurMaskFilter.
private class NoodleNetOrbitGlow {
    private val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888).apply {
        density = Bitmap.DENSITY_NONE
    }
    private val canvas = android.graphics.Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B2F.toInt() }
    private var lastBreath = Float.NaN
    fun image(breath: Float): Bitmap {
        if (breath != lastBreath) {
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            val sigma = 10f + 8f * breath
            paint.maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            paint.alpha = ((0.45f + 0.35f * breath) * 255).roundToInt()
            canvas.drawCircle(72f, 72f, 10f, paint)
            lastBreath = breath
        }
        return bitmap
    }
}

private fun DrawScope.drawNet(
    cx: Float, cy: Float, radius: Float, angle: Float, press: Float, phase: Float,
    rim: Path, weave: Path, glow: NoodleNetOrbitGlow,
) {
    val p = max(0f, press)
    val half = 0.55f + 0.25f * min(1f, p)
    val inward = INWARD * p
    val bulge = BULGE * p
    val breath = min(1f, p)
    val centre = Offset(cx, cy)
    drawCircle(
        Brush.radialGradient(
            0f to WARM.copy(alpha = 0.30f), 0.6f to WARM.copy(alpha = 0.08f),
            1f to WARM.copy(alpha = 0f), center = centre, radius = radius,
        ),
        radius * (0.7f + 0.3f * breath), centre, alpha = 0.25f + 0.75f * breath,
    )
    rim.reset()
    for (i in 0..160) {
        val phi = angle + half + (2 * PI.toFloat() - 2 * half) * i / 160
        val x = cx + cos(phi) * radius
        val y = cy + sin(phi) * radius
        if (i == 0) rim.moveTo(x, y) else rim.lineTo(x, y)
    }
    for (edge in 0..1) {
        for (i in 0..60) {
            val pt = bandPoint(cx, cy, radius, angle, half, inward, bulge, i / 60f, edge.toFloat())
            if (i == 0) rim.moveTo(pt.x, pt.y) else rim.lineTo(pt.x, pt.y)
        }
    }
    drawPath(rim, CREAM, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val show = smooth(p / 0.25f)
    if (show > 0.01f) {
        weave.reset()
        for (k in 1 until ALONG - 1) {
            val base = k.toFloat() / (ALONG - 1)
            for (i in 0..60) {
                val s = i / 60f
                val q = (base + 0.17f * sin(PI.toFloat() * 3 * s + k * 1.7f + phase)).coerceIn(0f, 1f)
                val pt = bandPoint(cx, cy, radius, angle, half, inward, bulge, s, q)
                if (i == 0) weave.moveTo(pt.x, pt.y) else weave.lineTo(pt.x, pt.y)
            }
        }
        for (j in 0 until ACROSS) {
            val s0 = (j + 0.5f) / ACROSS
            for (i in 0..20) {
                val q = i / 20f
                val s = s0 + 0.035f * sin(PI.toFloat() * 2 * q + j * 1.1f + phase)
                val pt = bandPoint(cx, cy, radius, angle, half, inward, bulge, s, q)
                if (i == 0) weave.moveTo(pt.x, pt.y) else weave.lineTo(pt.x, pt.y)
            }
        }
        drawPath(weave, CREAM.copy(alpha = 0.85f * show), style = Stroke(1.2f, cap = StrokeCap.Round))
    }
    val r = radius - BEAD_GAP - inward
    val bx = cx + cos(angle) * r
    val by = cy + sin(angle) * r
    drawIntoCanvas { it.nativeCanvas.drawBitmap(glow.image(breath), bx - 72f, by - 72f, null) }
    drawCircle(BEAD, 7f, Offset(bx, by))
}

@Composable
fun NoodleNetOrbit(
    modifier: Modifier = Modifier,
    brand: String = "unwind",
    pattern: String = "4 · 7 · 8",
    round: String = "ROUND 2 OF 4",
    session: String = "Evening wind-down · 6 min",
    action: String = "End session",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val serif = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(context.assets, "${ASSETS}InstrumentSerif-Regular.ttf")))
    }
    val italic = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(context.assets, "${ASSETS}InstrumentSerif-Italic.ttf")))
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val frame = remember { mutableStateOf(Frame()) }
    var touching by remember { mutableStateOf(false) }
    var userAngle by remember { mutableFloatStateOf(REST) }
    var userPress by remember { mutableFloatStateOf(0f) }
    var released by remember { mutableDoubleStateOf(-1.0) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var origin by remember { mutableDoubleStateOf(0.0) }
    val rim = remember { Path() }
    val weave = remember { Path() }
    val glow = remember { NoodleNetOrbitGlow() }
    LaunchedEffect(autoPlay, reduced, active) {
        if (reduced) { frame.value = Frame(); return@LaunchedEffect }
        if (!active) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            if (previous != 0L) elapsed += (now - previous) / 1e9
            previous = now
            val since = if (released >= 0) elapsed - released else -1.0
            if (touching) frame.value = Frame(userAngle, userPress)
            else if (since >= 0 && since < HOLD + RETURN) {
                val k = cubic(((since - HOLD) / RETURN).toFloat().coerceIn(0f, 1f))
                val delta = atan2(sin(REST - userAngle), cos(REST - userAngle))
                frame.value = Frame(userAngle + delta * k, userPress * (1 - k))
                origin = elapsed
            } else {
                released = -1.0
                frame.value = beadAt(if (autoPlay) ((elapsed - origin) % LOOP).toFloat() else 0f)
            }
        }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val noPadding = PlatformTextStyle(includeFontPadding = false)
    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val radius = min(w * 0.42f, 166f)
        val cx = w / 2
        val cy = h * 0.47f
        Box(Modifier.fillMaxSize().pointerInput(w, h, density, reduced) {
            if (reduced) return@pointerInput
            fun follow(p: Offset) {
                val dx = p.x / density - cx
                val dy = p.y / density - cy
                userAngle = atan2(dy, dx)
                userPress = ((radius - BEAD_GAP - hypot(dx, dy)) / INWARD).coerceIn(0f, MAX_PRESS)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                touching = true
                follow(down.position)
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        follow(change.position)
                        change.consume()
                    }
                } finally { touching = false; released = elapsed }
            }
        }) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Breathing circle. Drag the red bead to pull the net." }) {
                val f = frame.value
                scale(density, density, Offset.Zero) {
                    drawRect(Brush.radialGradient(listOf(LIFT, BACKGROUND), Offset(cx, cy), h * 0.6f))
                    drawNet(cx, cy, radius, f.angle, f.press, (PI * 4 * (elapsed - origin) / LOOP).toFloat(), rim, weave, glow)
                }
            }
            Row(
                Modifier.offset(y = insets.calculateTopPadding() + 8.dp).padding(horizontal = 24.dp).fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // This font's 990/-310 metrics exceed the explicit line height. Match RN iOS's
                // baseline by removing that excess (39 - 36 dp) from the glyph placement.
                Text(brand, Modifier.offset(y = (-3).dp), style = TextStyle(fontFamily = italic, color = CREAM, fontSize = 30.sp, lineHeight = 36.sp, platformStyle = noPadding))
                Box(Modifier.height(30.dp).border(1.dp, FAINT, CircleShape).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(pattern, style = TextStyle(color = CREAM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, platformStyle = noPadding))
                }
            }
            Text(
                round, Modifier.offset(y = (cy - radius - 56).dp).fillMaxWidth(),
                style = TextStyle(color = DIM, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, textAlign = TextAlign.Center, platformStyle = noPadding),
            )
            listOf("Inhale", "Hold", "Exhale").forEachIndexed { index, label ->
                Box(
                    Modifier.offset(y = (cy - 34).dp).fillMaxWidth().height(68.dp).graphicsLayer {
                        val t = frame.value.time
                        val hold = smooth((t - 3) / 0.3f)
                        val out = smooth((t - 4.6f) / 0.3f)
                        val inhale = smooth((t - 7.2f) / 0.3f)
                        val opacity = if (touching) { if (index == 1) 1f else 0f } else when (index) {
                            0 -> 1 - hold + inhale
                            1 -> hold * (1 - out)
                            else -> out * (1 - inhale)
                        }
                        alpha = opacity
                        translationY = 8f * (1 - opacity) * density
                    }, contentAlignment = Alignment.Center,
                ) {
                    // The same baseline correction at 58 sp: 58 * 1.3 - 68 = 7.4 dp.
                    Text(label, Modifier.offset(y = (-7.4).dp), style = TextStyle(fontFamily = italic, color = CREAM, fontSize = 58.sp, lineHeight = 68.sp, platformStyle = noPadding))
                }
            }
            Column(
                Modifier.offset(y = (cy + radius + 36).dp).padding(horizontal = 24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { k -> Box(Modifier.size(28.dp, 3.dp).background(if (k < 2) CREAM else FAINT, CircleShape)) }
                }
                Text(
                    session, Modifier.padding(top = 14.dp),
                    style = TextStyle(fontFamily = serif, color = DIM, fontSize = 20.sp, lineHeight = 26.sp, platformStyle = noPadding),
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = insets.calculateBottomPadding() + 18.dp)
                .height(52.dp).border(1.dp, FAINT, CircleShape).padding(horizontal = 34.dp).semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = TextStyle(color = CREAM, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, platformStyle = noPadding))
        }
    }
}
