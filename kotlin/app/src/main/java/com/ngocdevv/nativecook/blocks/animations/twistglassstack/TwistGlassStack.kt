package com.ngocdevv.nativecook.blocks.animations.twistglassstack

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds. Matches the React Native reference, including continuous quarter-turn rotation. */
const val LOOP = 8.0
private const val PEAK = 0.42f
private const val HALF = 104f
private const val THICK = 14f
private const val FORE = 0.36f
private const val POINTS = 72
private const val HOLD = 2.0
private const val RETURN = 0.8
private val Background = Color(0xFFECECF1)
private val Ink = Color(0xFF17161F)
private val Soft = Color(0xFF73717F)
private val Track = Color(0xFFE2E1EA)
private val Bar = Color(0xFF7C5CFF)
private val Tops = intArrayOf(0xFFB49BFF.toInt(), 0xFFA7A2FF.toInt(), 0xFFA2B3FF.toInt(), 0xFF9CC6FF.toInt(), 0xFFA6DBFF.toInt())
private val Bodies = intArrayOf(0xFF7C4DFF.toInt(), 0xFF7F68FF.toInt(), 0xFF7D86FF.toInt(), 0xFF6FA2FF.toInt(), 0xFF6FC0FF.toInt())
private val Widths = floatArrayOf(0.86f, 1f, 1.06f, 0.98f, 0.84f)

private fun segment(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun cubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

// 0–.8 rest; .8–3.2 wind up; 3.2–4.8 one wobble; 4.8–7.2 unwind past
// straight; 7.2–8 rest. The gap opens from 38 to 52 dp with the twist.
private fun twistAt(t: Float): Float {
    if (t < 4.8f) {
        return PEAK * cubic(segment(t, 0.8f, 3.2f)) +
            0.05f * sin((PI * 2 * segment(t, 3.2f, 4.8f)).toFloat())
    }
    val u = smooth(segment(t, 4.8f, 7.2f))
    return PEAK * (1f - u).pow(3) * cos((1.6 * PI * u).toFloat())
}

/** Only draw lambdas observe the frame values; static copy never recomposes per frame. */
private class TwistGlassStackClock {
    var elapsed by mutableDoubleStateOf(0.0)
    var twist by mutableFloatStateOf(0f)
    var time by mutableFloatStateOf(0f)
    private var origin = 0.0
    private var touching = false
    private var manual = false
    private var released = -1.0
    private var userTwist = 0f

    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        when {
            touching -> twist = userTwist
            manual -> {
                val since = elapsed - released
                when {
                    since < HOLD -> twist = userTwist
                    since < HOLD + RETURN -> twist = userTwist * (1f - cubic(((since - HOLD) / RETURN).toFloat()))
                    else -> {
                        twist = 0f
                        manual = false
                        origin = elapsed
                    }
                }
            }
            else -> {
                time = if (autoPlay) ((elapsed - origin) % LOOP).toFloat() else 0f
                twist = if (autoPlay) twistAt(time) else 0f
            }
        }
    }

    fun begin() {
        touching = true
        manual = true
        userTwist = twist
    }

    fun drag(translationX: Float) {
        userTwist = (translationX / 260f).coerceIn(-0.7f, 0.7f)
    }

    fun release() {
        touching = false
        released = elapsed
    }
}

/** Cached paints, gradients and unit vertices; only the five small paths change per frame. */
private class TwistGlassStackRenderer(density: Float) {
    private val unitX = FloatArray(POINTS) { i ->
        cos(i * PI * 2 / POINTS).let { (sign(it) * sqrt(abs(it))).toFloat() }
    }
    private val unitZ = FloatArray(POINTS) { i ->
        sin(i * PI * 2 / POINTS).let { (sign(it) * sqrt(abs(it))).toFloat() }
    }
    private val path = Path()
    private val matrix = Matrix()
    private val gradients = Array(5) { i ->
        LinearGradient(0f, -1f, 0f, 1f, intArrayOf(android.graphics.Color.WHITE, Tops[i], Bodies[i]), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val tops = Array(5) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            shader = gradients[i]
            alpha = (0.86f * 255).roundToInt()
        }
    }
    private val bodies = Array(5) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Bodies[i]
            alpha = (0.78f * 255).roundToInt()
        }
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        alpha = (0.75f * 255).roundToInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // BlurMaskFilter is rasterized once so the same soft shadow also works on API 26.
    private val shadow = Bitmap.createBitmap((368 * density).roundToInt(), (148 * density).roundToInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3B2A8C.toInt()
            alpha = (0.18f * 255).roundToInt()
            maskFilter = BlurMaskFilter(((18f * density - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
        }
        AndroidCanvas(bitmap).drawOval(54 * density, 54 * density, 314 * density, 94 * density, paint)
    }
    private val shadowBounds = android.graphics.RectF()

    private fun layer(cx: Float, cy: Float, half: Float, angle: Float) {
        val ca = cos(angle)
        val sa = sin(angle)
        path.rewind()
        for (i in 0 until POINTS) {
            val x = unitX[i] * half
            val z = unitZ[i] * half
            val px = cx + x * ca - z * sa
            val py = cy + (x * sa + z * ca) * FORE
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    fun draw(canvas: AndroidCanvas, width: Float, height: Float, base: Float, twist: Float) {
        val cx = width / 2f
        val cy = height * 0.48f
        shadowBounds.set(cx - 184f, cy + 90f, cx + 184f, cy + 238f)
        canvas.drawBitmap(shadow, null, shadowBounds, shadowPaint)
        val open = (abs(twist) / PEAK).coerceAtMost(1f)
        val gap = 38f + 14f * open
        for (i in 4 downTo 0) {
            val y = cy + (i - 2) * gap
            val half = HALF * Widths[i] * (1f + 0.04f * open * sin(i * 1.7f))
            val angle = base + twist * (i - 2)
            layer(cx, y + THICK, half, angle)
            canvas.drawPath(path, bodies[i])
            matrix.setScale(1f, half * FORE * 1.2f)
            matrix.postTranslate(cx, y)
            gradients[i].setLocalMatrix(matrix)
            layer(cx, y, half, angle)
            canvas.drawPath(path, tops[i])
            canvas.drawPath(path, rim)
        }
    }
}

/** Five glass squircles below an Instrument Serif title, matching the React Native reference. */
@Composable
fun TwistGlassStack(
    modifier: Modifier = Modifier,
    brand: String = "Atelier",
    title: String = "Dreaming up\nyour scene…",
    prompt: String = "A quiet harbour at dawn, soft film grain, two boats",
    action: String = "Cancel",
    autoPlay: Boolean = false,
) {
    BlockSystemBars(darkIcons = true)
    val reducedMotion = rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val view = LocalView.current
    val clock = remember { TwistGlassStackClock() }
    val renderer = remember(density) { TwistGlassStackRenderer(density) }
    val serifFamily = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "twistglassstack/twistglassstack_InstrumentSerif-Italic.ttf",
        )))
    }
    val thumbnail = remember(context, density) { twistGlassStackThumbnail(context, density).asImageBitmap() }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val baseStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    LaunchedEffect(lifecycle, reducedMotion, autoPlay) {
        if (reducedMotion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                clock.frame(dt, autoPlay)
            }
        }
    }

    Box(modifier.fillMaxSize().background(Background)) {
        Canvas(
            Modifier.fillMaxSize()
                .drawWithCache {
                    val background = Brush.verticalGradient(listOf(Color(0xFFF5F2FF), Color(0xFFDCE2F6)))
                    val centre = Offset(size.width / 2f, size.height * 0.48f)
                    val aura = Brush.radialGradient(
                        listOf(Color(0xFF8C6EFF).copy(alpha = 0.22f), Color(0x008C6EFF)), centre, 220.dp.toPx(),
                    )
                    onDrawBehind {
                        drawRect(background)
                        drawCircle(aura, radius = 220.dp.toPx(), center = centre)
                    }
                }
                .semantics { contentDescription = "Glass layers twisting while the image is generated. Drag to twist them." }
                .pointerInput(reducedMotion, density) {
                    if (reducedMotion) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        clock.begin()
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                clock.drag((change.position.x - down.position.x) / density)
                                change.consume()
                            }
                        } finally {
                            clock.release()
                        }
                    }
                },
        ) {
            val base = if (autoPlay) (PI / 2 * clock.elapsed / LOOP).toFloat() else 0f
            val twist = clock.twist
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                renderer.draw(canvas, size.width / density, size.height / density, base, twist)
                canvas.restore()
            }
        }

        Row(
            Modifier.padding(top = safe.calculateTopPadding() + 10.dp, start = 24.dp, end = 24.dp).fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(brand, style = baseStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = Ink))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(22.dp).background(Ink, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("AI", style = baseStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White))
            }
        }

        Text(
            title,
            // Match CoreText's baseline for this face at 50/54, including its tall ascender.
            modifier = Modifier.padding(top = safe.calculateTopPadding() + 54.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth().offset(y = (-10.5).dp),
            style = baseStyle.copy(fontFamily = serifFamily, fontSize = 50.sp, lineHeight = 54.sp, letterSpacing = (-0.5).sp, color = Ink),
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 24.dp)
                // The RN bottom overlay owns these touches; they do not reach the glass.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .padding(bottom = safe.calculateBottomPadding() + 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .drawWithCache {
                        val shadow = twistGlassStackCardShadow(size.width.roundToInt(), size.height.roundToInt(), density)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        onDrawBehind {
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(shadow, -60f * density, -60f * density, paint) }
                        }
                    }
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp)).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(thumbnail, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("PROMPT · 62%", style = baseStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Soft))
                        Spacer(Modifier.height(4.dp))
                        Text(prompt, maxLines = 2, style = baseStyle.copy(fontSize = 15.sp, lineHeight = 20.sp, color = Ink))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                    drawRoundRect(Track, cornerRadius = CornerRadius(3.dp.toPx()))
                    // RN freezes this loop time while touching, holding and returning.
                    val x = (-120f + (size.width / density + 240f) * clock.time / LOOP.toFloat()) * density
                    drawRoundRect(Bar, Offset(x, 0f), Size(120.dp.toPx(), size.height), CornerRadius(3.dp.toPx()))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).border(1.5.dp, Color(0xFFCFCDD9), RoundedCornerShape(27.dp)).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = baseStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink))
            }
        }
    }
}
