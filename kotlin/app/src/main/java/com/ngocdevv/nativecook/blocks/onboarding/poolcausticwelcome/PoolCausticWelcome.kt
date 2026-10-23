package com.ngocdevv.nativecook.blocks.onboarding.poolcausticwelcome

import android.annotation.TargetApi
import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOOP = 8.0
private fun nowSeconds() = System.nanoTime() / 1e9
private fun ease(raw: Double): Double { val u = raw.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }

private data class PoolCausticWelcomePose(
    var phase: Double = 0.0,
    var amplitude: Double = 0.0,
    var pointer: Offset = Offset(196.5f, 420f),
    var pointerStrength: Double = 0.0,
    var velocity: Offset = Offset.Zero,
    var ringOffset: Offset = Offset.Zero,
    var extraRingAngle: Double = 0.0,
) {
    val ringBob get() = Offset((4 * amplitude * sin(phase)).toFloat(), (3 * amplitude * sin(2 * phase)).toFloat())
    val ringCenter get() = Offset(127f, 738f) + ringBob + ringOffset
    val ringAngle get() = 8 * amplitude * sin(phase) + extraRingAngle
    val duckCenter get() = Offset((224 + 3 * amplitude * (cos(phase) - 1)).toFloat(), (353 + 2 * amplitude * sin(phase)).toFloat())
    val duckAngle get() = -6 * amplitude * sin(phase)
    fun clampRing() {
        val center = ringCenter
        ringOffset += Offset(center.x.coerceIn(54f, 339f), center.y.coerceIn(132f, 756f)) - center
    }
    fun returning(amount: Double): PoolCausticWelcomePose {
        if (amount <= 0) return this
        val pose = copy()
        pose.amplitude *= 1 - amount
        val shortPhase = atan2(sin(phase), cos(phase))
        pose.phase = if (amount >= 1) 0.0 else phase - shortPhase * amount
        pose.pointerStrength *= 1 - amount; pose.velocity *= (1 - amount).toFloat()
        pose.ringOffset *= (1 - amount).toFloat(); pose.extraRingAngle *= 1 - amount
        return pose
    }
}

private fun loopPose(raw: Double): PoolCausticWelcomePose {
    val t = ((raw % LOOP) + LOOP) % LOOP
    if (t < 1.4 || t >= 7.4) return PoolCausticWelcomePose()
    val a = when {
        t < 2.8 -> 0.65 * ease((t - 1.4) / 1.4)
        t < 3.7 -> 0.65 + 0.35 * ease((t - 2.8) / 0.9)
        t < 4.9 -> 1.0
        t < 6.5 -> 1 - 0.65 * ease((t - 4.9) / 1.6)
        else -> 0.35 * (1 - ease((t - 6.5) / 0.9))
    }
    return PoolCausticWelcomePose(phase = 2 * PI * ease((t - 1.4) / 6), amplitude = a)
}

private class PoolCausticWelcomeMotion {
    var frame by mutableDoubleStateOf(nowSeconds())
    var manualLive by mutableStateOf(false)
    var epoch = nowSeconds()
    var lastTouch = Double.NEGATIVE_INFINITY
    var touchBegan = Double.NEGATIVE_INFINITY
    var releasedAt = Double.NEGATIVE_INFINITY
    var dragStart = PoolCausticWelcomePose()
    var held = PoolCausticWelcomePose()
    var released = PoolCausticWelcomePose()
    var ringStart = Offset(127f, 738f)
    var ringVelocity = Offset.Zero
    var dragging = false
    var draggingRing = false
    var crest = false
    var slowDayStarted by mutableStateOf(false)
    var lastHaptic = Double.NEGATIVE_INFINITY
    var resume: Job? = null
    fun pose(reduced: Boolean, autoPlay: Boolean, previewTime: Double?): PoolCausticWelcomePose {
        val now = frame
        if (previewTime != null) return if (reduced) PoolCausticWelcomePose() else loopPose(previewTime)
        if (reduced) return PoolCausticWelcomePose(ringOffset = if (dragging) held.ringOffset else released.ringOffset)
        if (dragging) {
            val pose = held.copy()
            val caught = pose.ringCenter
            val activation = ease((now - touchBegan) / 0.25)
            pose.amplitude += (max(0.85, pose.amplitude) - pose.amplitude) * activation
            pose.pointerStrength = max(pose.pointerStrength, activation * 0.75)
            if (draggingRing) pose.ringOffset += caught - pose.ringCenter
            return pose
        }
        val age = now - lastTouch
        if (age < 2.6) {
            val releaseAge = max(0.0, now - releasedAt)
            val pose = released.copy()
            pose.pointerStrength *= exp(-releaseAge * 1.1); pose.velocity *= exp(-releaseAge * 4).toFloat()
            val spring = if (releaseAge >= 0.8) 0.0 else exp(-8 * releaseAge) * (cos(14 * releaseAge) + 8 / 14.0 * sin(14 * releaseAge))
            pose.ringOffset = released.ringOffset * spring.toFloat() + ringVelocity * (releaseAge * exp(-8 * releaseAge)).toFloat()
            pose.extraRingAngle *= spring; pose.clampRing()
            if (crest) { pose.amplitude += (1 - pose.amplitude) * ease(releaseAge / 0.55); pose.phase += PI * ease(releaseAge / 1.2) }
            return pose.returning(ease((age - 2) / 0.6))
        }
        if (!autoPlay) return PoolCausticWelcomePose()
        return loopPose(if (lastTouch == Double.NEGATIVE_INFINITY) now - epoch else age - 2.6)
    }
}

/** Analytic caustics refract one cached underwater surface; floating objects stay above it. */
@Composable
fun PoolCausticWelcome(modifier: Modifier = Modifier, autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced = poolCausticWelcomeReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val c = remember { PoolCausticWelcomeMotion() }
    val painter = remember { PoolCausticWelcomePainter() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(c, reduced, previewTime, autoPlay, c.manualLive) {
        if (reduced || previewTime != null || (!autoPlay && !c.manualLive)) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }
    DisposableEffect(c) { onDispose { c.resume?.cancel() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val status = controller?.isAppearanceLightStatusBars; val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true; controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }
    fun haptic() {
        val now = nowSeconds()
        if (now - c.lastHaptic > 0.16) { c.lastHaptic = now; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
    fun resumeLater() {
        c.resume?.cancel(); c.resume = scope.launch { delay(2650); if (!c.dragging) { c.manualLive = false; c.frame = nowSeconds() } }
    }
    fun startSlowDay() {
        c.frame = nowSeconds(); c.released = c.pose(reduced, autoPlay, previewTime).copy(pointerStrength = 0.0)
        c.releasedAt = c.frame; c.lastTouch = c.frame; c.ringVelocity = Offset.Zero
        c.manualLive = true; c.crest = true; c.slowDayStarted = true; haptic(); resumeLater()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF1F0E9)), contentAlignment = Alignment.Center) {
        val fit = min(maxWidth.value / 393, maxHeight.value / 852)
        Box(Modifier.requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = fit; scaleY = fit }
            .pointerInput(c, reduced, previewTime) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker(); tracker.addPointerInputChange(down)
                    val p0 = down.position / density
                    c.frame = nowSeconds()
                    val shown = c.pose(reduced, autoPlay, previewTime)
                    // The ring's circular 72 dp target sits above the overlapping CTA.
                    val isRingTarget = (p0 - shown.ringCenter).getDistance() < 36
                    val isButton = !isRingTarget && p0.x in 85f..307f && p0.y in 728.5f..772.5f
                    val near = Offset(p0.x.coerceIn(49f, 344f), p0.y.coerceIn(114f, 763f))
                    val inside = p0.x in 18f..375f && p0.y in 83f..794f && (p0 - near).getDistance() <= 31f
                    if (!isButton && !inside) return@awaitEachGesture
                    c.resume?.cancel()
                    if (!isButton) {
                        c.dragStart = shown.copy(); c.held = shown.copy(); c.ringStart = shown.ringCenter
                        c.draggingRing = (p0 - c.ringStart).getDistance() < 40
                        c.touchBegan = c.frame; c.dragging = true; c.crest = false
                        c.held.pointer = Offset(p0.x.coerceIn(26f, 367f), p0.y.coerceIn(91f, 786f))
                        c.lastTouch = c.frame; c.manualLive = true
                        if (c.draggingRing) haptic()
                    }
                    var delta = Offset.Zero
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            delta = (change.position - down.position) / density
                            if (!change.pressed) break
                            if (!isButton) {
                                val speed = tracker.calculateVelocity(); val location = change.position / density
                                c.held.phase = c.dragStart.phase + delta.x * 0.013 + delta.y * 0.007
                                c.held.amplitude = c.dragStart.amplitude
                                c.held.pointer = Offset(location.x.coerceIn(26f, 367f), location.y.coerceIn(91f, 786f))
                                c.held.pointerStrength = ease(delta.getDistance() / 12.0)
                                c.held.velocity = Offset((speed.x / density / 800).coerceIn(-1f, 1f), (speed.y / density / 800).coerceIn(-1f, 1f))
                                if (c.draggingRing) {
                                    val center = c.ringStart + delta
                                    c.held.ringOffset = Offset(center.x.coerceIn(54f, 339f), center.y.coerceIn(132f, 756f)) - Offset(127f, 738f) - c.held.ringBob
                                    c.held.extraRingAngle = (delta.x * 0.09).coerceIn(-14.0, 14.0)
                                }
                                c.lastTouch = nowSeconds(); c.frame = c.lastTouch; c.manualLive = true
                            }
                            change.consume()
                        }
                    } finally {
                        if (isButton) { if (delta.getDistance() < 12) startSlowDay() }
                        else {
                            c.frame = nowSeconds(); c.released = c.pose(reduced, autoPlay, previewTime).copy()
                            val speed = tracker.calculateVelocity()
                            c.ringVelocity = if (c.draggingRing && !reduced) Offset((speed.x / density).coerceIn(-1400f, 1400f), (speed.y / density).coerceIn(-1400f, 1400f)) else Offset.Zero
                            c.releasedAt = c.frame; c.lastTouch = c.frame; c.dragging = false; c.manualLive = true
                            if (c.draggingRing) haptic()
                            c.draggingRing = false; resumeLater()
                        }
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save(); canvas.scale(density, density)
                painter.draw(canvas, c.pose(reduced, autoPlay, previewTime), reduced, c.slowDayStarted)
                canvas.restore()
            }
            Box(Modifier.offset(18.dp, 83.dp).size(357.dp, 711.dp).semantics {
                contentDescription = "Afloat pool water. Drag to bend the light through the water"
            })
            Box(Modifier.offset(85.dp, 728.5.dp).size(222.dp, 44.dp).semantics {
                role = Role.Button
                contentDescription = if (c.slowDayStarted && reduced) "Your slow day begins" else "Start a slow day"
                onClick { startSlowDay(); true }
            })
            Box(Modifier.size(72.dp).graphicsLayer {
                val center = c.pose(reduced, autoPlay, previewTime).ringCenter
                translationX = (center.x - 36) * density; translationY = (center.y - 36) * density
            }.semantics { contentDescription = "Floating life ring. Drag the ring across the pool" })
        }
    }
}

/** All bitmap artwork is local and cached at the same 2× resolution as the Swift reference. */
private class PoolCausticWelcomePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val serif = Typeface.create("serif", Typeface.NORMAL)
    private val serifItalic = Typeface.create("serif", Typeface.ITALIC)
    private val pool = Path().apply { addRoundRect(18f, 83f, 375f, 794f, 31f, 31f, Path.Direction.CW) }
    private val base = bitmap(357, 711) { c -> base(c) }
    private val ring = bitmap(64, 64) { c -> c.translate(32f, 32f); ring(c) }
    private val duck = bitmap(64, 64) { c -> c.translate(32f, 32f); duck(c) }
    private val surface = if (Build.VERSION.SDK_INT >= 33) PoolCausticWelcomeShader(base) else null
    private val fallback = if (Build.VERSION.SDK_INT < 33) PoolCausticWelcomeMesh(base) else null
    private val ringShadows = bitmap(100, 80) { c ->
        c.translate(50f, 40f)
        shadow(c, Offset(3f, 17f), 59f, 23f, 0.30, 6f)
        shadow(c, Offset(1f, 4f), 53f, 29f, 0.12, 2f)
    }
    private val duckShadows = bitmap(100, 80) { c ->
        c.translate(50f, 40f)
        shadow(c, Offset(2f, 13f), 44f, 25f, 0.22, 5f)
        shadow(c, Offset(1f, 4f), 43f, 29f, 0.13, 1.8f)
    }
    private val labels = bitmap(393, 852) { c ->
        label(c, "A slower kind", 196.5f, 498f, 16f, 0xDCECE8, serifItalic, alpha = 0.88)
        label(c, "of day.", 196.5f, 516f, 16f, 0xDCECE8, serifItalic, alpha = 0.88)
    }
    private fun rgba(hex: Int, alpha: Double = 1.0) = AndroidColor.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    private fun fill(hex: Int, alpha: Double = 1.0) { paint.color = rgba(hex, alpha); paint.shader = null; paint.maskFilter = null; paint.style = Paint.Style.FILL }
    private fun stroke(hex: Int, width: Float, alpha: Double = 1.0) { fill(hex, alpha); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND }
    private fun bitmap(w: Int, h: Int, draw: (AndroidCanvas) -> Unit) = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888).also { val c = AndroidCanvas(it); c.scale(2f, 2f); draw(c) }
    private fun image(c: AndroidCanvas, image: Bitmap, box: RectF) { fill(0xFFFFFF); c.drawBitmap(image, null, box, paint) }
    private fun label(c: AndroidCanvas, word: String, x: Float, y: Float, size: Float, hex: Int, font: Typeface = normal, tracking: Float = 0f, left: Boolean = false, alpha: Double = 1.0, maxWidth: Float = Float.POSITIVE_INFINITY) {
        text.textSize = size; text.typeface = font; text.color = rgba(hex, alpha)
        text.letterSpacing = tracking / text.textSize
        var total = text.measureText(word)
        if (total > maxWidth) {
            text.textSize *= maxWidth / total
            text.letterSpacing = tracking / text.textSize
            total = text.measureText(word)
        }
        val cursor = if (left) x else x - total / 2
        val baseline = y - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2
        // Draw the complete shaped run: ligatures can assign zero advance to later characters.
        c.drawText(word, cursor, baseline, text)
    }
    private fun base(c: AndroidCanvas) {
        fill(0xFFFFFF); paint.shader = LinearGradient(0f, 0f, 0f, 711f, intArrayOf(rgba(0x579DE9), rgba(0x4898E4), rgba(0x3586D4)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 357f, 711f, paint)
        c.save(); c.translate(-18f, -83f)
        stroke(0xB2D9F0, 0.6f, 0.13)
        for (i in 0 until 9) c.drawLine(18f + i * 48, 83f, 18f + i * 48, 794f, paint)
        for (i in 0 until 16) c.drawLine(18f, 83f + i * 48, 375f, 83f + i * 48, paint)
        val palm = palm()
        fill(0x174AD2, 0.74); paint.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL); c.drawPath(palm, paint)
        c.save(); c.translate(7f, 10f); fill(0x174AD2, 0.14); paint.maskFilter = BlurMaskFilter(13f, BlurMaskFilter.Blur.NORMAL); c.drawPath(palm, paint); c.restore()
        for (y in floatArrayOf(552f, 590f)) {
            val rail = Path().apply { moveTo(2f, y); cubicTo(52f, y - 2, 112f, y + 1, 103f, y + 12) }
            stroke(0x376E99, 6f, 0.75); c.drawPath(rail, paint)
            stroke(0xC3DDE5, 3f, 0.95); c.drawPath(rail, paint)
        }
        for (x in floatArrayOf(20f, 41f, 62f, 83f, 101f)) {
            stroke(0x4B718D, 5f); c.drawLine(x, 552 + x * 0.08f, x - 2, 590 + x * 0.08f, paint)
            stroke(0xD6E0DF, 2.5f, 0.85); c.drawLine(x, 552 + x * 0.08f, x - 2, 590 + x * 0.08f, paint)
        }
        // Match the measured New York serif contours using Android's wider system serif.
        c.save(); c.translate(202f, 424.3f); c.scale(0.831f, 0.968f)
        label(c, "afloat", 0f, 0f, 126f, 0xDCECE8, serif, -8f, alpha = 0.66, maxWidth = 308f)
        c.restore()
        c.restore()
    }
    private fun palm(): Path {
        val p = Path().apply {
            moveTo(-47f, 725f); cubicTo(10f, 670f, 79f, 516f, 168f, 323f); lineTo(176f, 323f); cubicTo(94f, 522f, 32f, 680f, -30f, 737f); close()
        }
        val ends = listOf(Offset(-22f, 257f), Offset(11f, 189f), Offset(91f, 172f), Offset(190f, 174f), Offset(287f, 218f), Offset(350f, 298f), Offset(329f, 388f), Offset(268f, 469f), Offset(165f, 524f))
        for ((index, end) in ends.withIndex()) {
            val dx = end.x - 172; val dy = end.y - 323; val length = hypot(dx, dy); val nx = -dy / length; val ny = dx / length
            val bend = if (index % 2 == 0) 18f else -18f
            val mx = 172 + dx * 0.58f + nx * bend; val my = 323 + dy * 0.58f + ny * bend
            p.moveTo(172f, 323f); p.quadTo(mx + nx * 44, my + ny * 44, end.x, end.y); p.quadTo(mx - nx * 44, my - ny * 44, 172f, 323f); p.close()
        }
        return p
    }
    private fun ring(c: AndroidCanvas) {
        stroke(0xDEE4DC, 1.4f); c.drawCircle(0f, 0f, 22f, paint)
        val body = Path().apply { fillType = Path.FillType.EVEN_ODD; addCircle(0f, 0f, 19f, Path.Direction.CW); addCircle(0f, 0f, 10f, Path.Direction.CW) }
        c.save(); c.translate(0f, 3f)
        fill(0xA8796B); c.drawPath(body, paint); c.restore()
        fill(0xFFFFFF); paint.shader = LinearGradient(-16f, -18f, 18f, 18f, rgba(0xFFFDF0), rgba(0xD6DDD9), Shader.TileMode.CLAMP); c.drawPath(body, paint)
        fill(0xD76060)
        for (i in 0 until 4) {
            val start = i * 90f - 30; val end = start + 60
            val band = Path().apply {
                arcTo(RectF(-19f, -19f, 19f, 19f), start, 60f, true)
                lineTo((10 * cos(end * PI / 180)).toFloat(), (10 * sin(end * PI / 180)).toFloat())
                arcTo(RectF(-10f, -10f, 10f, 10f), end, -60f, false); close()
            }
            c.drawPath(band, paint)
        }
        stroke(0xFFFFFF, 0.8f, 0.37); c.drawCircle(0f, 0f, 18.5f, paint)
        stroke(0x6F8394, 0.8f, 0.35); c.drawCircle(0f, 0f, 10f, paint)
    }
    private fun duck(c: AndroidCanvas) {
        fill(0xFFFFFF); paint.shader = RadialGradient(-7f, -8f, 39f, intArrayOf(rgba(0xFFE767), rgba(0xFFD52F), rgba(0xEAB620)), floatArrayOf(2f / 39, 20.5f / 39, 1f), Shader.TileMode.CLAMP)
        c.drawOval(-24f, -23f, 24f, 26f, paint)
        stroke(0xFBE561, 1f, 0.8); c.drawOval(-24f, -23f, 24f, 26f, paint)
        fill(0xFFD733); c.drawOval(-18f, -22f, 5f, 2f, paint)
        fill(0xE97A20); c.drawPath(Path().apply { moveTo(-18f, -14f); quadTo(-28f, -25f, -9f, -22f); lineTo(-5f, -13f); close() }, paint)
        fill(0x5F4C20); c.drawOval(-14f, -13f, -11.5f, -10.5f, paint)
        stroke(0xE6BC22, 1.6f); c.drawPath(Path().apply { moveTo(0f, -1f); quadTo(19f, 1f, 11f, 17f) }, paint)
        fill(0xFFFFFF, 0.18); c.drawOval(-15f, -4f, -6f, 16f, paint)
    }
    private fun shadow(c: AndroidCanvas, center: Offset, w: Float, h: Float, alpha: Double, blur: Float) {
        fill(0x1C6098, alpha); paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        c.drawOval(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2, paint)
    }
    fun draw(c: AndroidCanvas, pose: PoolCausticWelcomePose, reduced: Boolean, started: Boolean) {
        fill(0x000000, 0.10); paint.maskFilter = BlurMaskFilter(13f, BlurMaskFilter.Blur.NORMAL); c.drawRoundRect(18f, 91f, 375f, 802f, 31f, 31f, paint)
        fill(0x000000, 0.08); paint.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL); c.drawRoundRect(18f, 84f, 375f, 795f, 31f, 31f, paint)
        c.save(); c.clipPath(pool); c.translate(18f, 83f)
        if (reduced) image(c, base, RectF(0f, 0f, 357f, 711f))
        else if (surface != null) { fill(0xFFFFFF); paint.shader = surface.update(pose); c.drawRect(0f, 0f, 357f, 711f, paint) }
        else fallback!!.draw(c, pose, paint)
        c.restore()
        stroke(0xFFFFFF, 2f); paint.shader = LinearGradient(18f, 83f, 375f, 794f, intArrayOf(rgba(0xFFFFFF, 0.24), 0x00FFFFFF, rgba(0x226BAE, 0.20)), null, Shader.TileMode.CLAMP); c.drawPath(pool, paint)
        c.save(); c.clipPath(pool)
        image(c, ringShadows, RectF(pose.ringCenter.x - 50, pose.ringCenter.y - 40, pose.ringCenter.x + 50, pose.ringCenter.y + 40))
        image(c, duckShadows, RectF(pose.duckCenter.x - 50, pose.duckCenter.y - 40, pose.duckCenter.x + 50, pose.duckCenter.y + 40))
        c.save(); c.translate(pose.ringCenter.x + 10, pose.ringCenter.y - 26); c.rotate(pose.ringAngle.toFloat() + 12); c.scale(1.18f, 0.78f); image(c, ring, RectF(-32f, -32f, 32f, 32f)); c.restore()
        c.save(); c.translate(pose.ringCenter.x, pose.ringCenter.y); c.rotate(pose.ringAngle.toFloat() - 14); c.scale(1.38f, 0.95f); image(c, ring, RectF(-32f, -32f, 32f, 32f)); c.restore()
        c.save(); c.translate(pose.duckCenter.x, pose.duckCenter.y); c.rotate(pose.duckAngle.toFloat() - 26); image(c, duck, RectF(-32f, -32f, 32f, 32f)); c.restore()
        c.restore()
        image(c, labels, RectF(0f, 0f, 393f, 852f))
        fill(0xFFFFFF, 0.13); c.drawRoundRect(85f, 733f, 307f, 768f, 17.5f, 17.5f, paint)
        stroke(0xFFFFFF, 1f, 0.45); c.drawRoundRect(85f, 733f, 307f, 768f, 17.5f, 17.5f, paint)
        label(c, if (started && reduced) "Your slow day begins" else "Start a slow day", 153f, 750.5f, 11f, 0xFFFFFF, medium, left = true, alpha = 0.95)
        stroke(0xFFFFFF, 1.1f, 0.95); c.drawLine(281f, 750.5f, 291f, 750.5f, paint); c.drawLine(287f, 746.5f, 291f, 750.5f, paint); c.drawLine(291f, 750.5f, 287f, 754.5f, paint)
        stroke(0xFDFDF6, 2f); c.drawPath(pool, paint)
    }
}

@TargetApi(33)
private class PoolCausticWelcomeShader(base: Bitmap) {
    private val shader = RuntimeShader(SOURCE)
    init {
        val input = BitmapShader(base, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        input.setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
        shader.setInputShader("uBase", input)
        shader.setFloatUniform("uSize", 357f, 711f)
    }
    fun update(pose: PoolCausticWelcomePose): RuntimeShader {
        shader.setFloatUniform("uPhase", pose.phase.toFloat()); shader.setFloatUniform("uAmplitude", pose.amplitude.toFloat())
        shader.setFloatUniform("uPointer", pose.pointer.x - 18, pose.pointer.y - 83)
        shader.setFloatUniform("uPointerStrength", pose.pointerStrength.toFloat()); shader.setFloatUniform("uVelocity", pose.velocity.x, pose.velocity.y)
        return shader
    }
    companion object {
        private const val SOURCE = """
uniform shader uBase;
uniform float2 uSize;
uniform float uPhase;
uniform float uAmplitude;
uniform float2 uPointer;
uniform float uPointerStrength;
uniform float2 uVelocity;
half4 main(float2 p) {
    float2 world = p + float2(18.0, 83.0);
    float k1 = 0.029 * world.x + 0.018 * world.y - uPhase;
    float k2 = -0.021 * world.x + 0.031 * world.y + 2.0 * uPhase;
    float k3 = 0.038 * world.x - 0.012 * world.y - 3.0 * uPhase;
    float2 slope = float2(0.029 * cos(k1) - 0.01155 * cos(k2) + 0.0095 * cos(k3),
                          0.018 * cos(k1) + 0.01705 * cos(k2) - 0.003 * cos(k3));
    float2 delta = p - uPointer;
    float lens = exp(-dot(delta, delta) / (115.0 * 115.0)) * uPointerStrength;
    float2 shift = slope * (60.0 + 200.0 * uAmplitude + 90.0 * lens) + uVelocity * (4.0 * lens);
    float titleBand = exp(-pow((world.y - 420.0) / 115.0, 2.0));
    shift.y += 7.0 * titleBand * sin(0.045 * world.x + uPhase);
    float2 q = clamp(p + shift, float2(0.5), uSize - 0.5);
    half4 sampled = uBase.eval(q);
    float3 col = float3(sampled.rgb);
    float softLight = pow(max(0.0, 0.55 + 0.25 * sin(k1 + k2) + 0.20 * cos(k2 - k3)), 8.0);
    float ridge = pow(max(0.0, 1.0 - abs(sin(k1 + k2 + 0.35 * sin(k3))) * 2.6), 4.0);
    float light = (0.65 * softLight + 0.35 * ridge) * (0.03 + 0.26 * uAmplitude + 0.10 * lens);
    col = mix(col, float3(0.615, 0.897, 0.956), min(light, 0.30));
    return half4(half3(col), sampled.a);
}
"""
    }
}

/** Below API 33 a two-dimensional inverse mesh bends the same cached title and palm together. */
private class PoolCausticWelcomeMesh(private val base: Bitmap) {
    private val columns = 36
    private val rows = 72
    private val count = (columns + 1) * (rows + 1)
    private val regular = FloatArray(count * 2)
    private val warped = FloatArray(count * 2)
    private val lights = IntArray(count)
    private val pale = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF9DE5F4.toInt()) }
    init { for (y in 0..rows) for (x in 0..columns) { val i = (y * (columns + 1) + x) * 2; regular[i] = x * 357f / columns; regular[i + 1] = y * 711f / rows } }
    private fun shift(x: Double, y: Double, pose: PoolCausticWelcomePose): Offset {
        val wx = x + 18; val wy = y + 83
        val k1 = 0.029 * wx + 0.018 * wy - pose.phase; val k2 = -0.021 * wx + 0.031 * wy + 2 * pose.phase; val k3 = 0.038 * wx - 0.012 * wy - 3 * pose.phase
        val dx = x - (pose.pointer.x - 18); val dy = y - (pose.pointer.y - 83)
        val lens = exp(-(dx * dx + dy * dy) / (115 * 115)) * pose.pointerStrength
        val a = 60 + 200 * pose.amplitude + 90 * lens
        return Offset(((0.029 * cos(k1) - 0.01155 * cos(k2) + 0.0095 * cos(k3)) * a + pose.velocity.x * 4 * lens).toFloat(), ((0.018 * cos(k1) + 0.01705 * cos(k2) - 0.003 * cos(k3)) * a + pose.velocity.y * 4 * lens + 7 * exp(-((wy - 420) / 115).pow(2)) * sin(0.045 * wx + pose.phase)).toFloat())
    }
    fun draw(c: AndroidCanvas, pose: PoolCausticWelcomePose, paint: Paint) {
        paint.shader = null; paint.maskFilter = null; paint.style = Paint.Style.FILL; paint.color = AndroidColor.WHITE
        c.drawBitmap(base, null, RectF(0f, 0f, 357f, 711f), paint)
        for (i in 0 until count) {
            val sx = regular[i * 2].toDouble(); val sy = regular[i * 2 + 1].toDouble()
            var x = sx; var y = sy
            repeat(2) { val d = shift(x, y, pose); x = sx - d.x; y = sy - d.y }
            warped[i * 2] = if (sx == 0.0 || sx == 357.0) sx.toFloat() else x.toFloat()
            warped[i * 2 + 1] = if (sy == 0.0 || sy == 711.0) sy.toFloat() else y.toFloat()
            val wx = sx + 18; val wy = sy + 83
            val k1 = 0.029 * wx + 0.018 * wy - pose.phase; val k2 = -0.021 * wx + 0.031 * wy + 2 * pose.phase; val k3 = 0.038 * wx - 0.012 * wy - 3 * pose.phase
            val dx = sx - (pose.pointer.x - 18); val dy = sy - (pose.pointer.y - 83)
            val lens = exp(-(dx * dx + dy * dy) / (115 * 115)) * pose.pointerStrength
            val soft = max(0.0, 0.55 + 0.25 * sin(k1 + k2) + 0.20 * cos(k2 - k3)).pow(8)
            val ridge = max(0.0, 1 - abs(sin(k1 + k2 + 0.35 * sin(k3))) * 2.6).pow(4)
            val light = min(0.30, (0.65 * soft + 0.35 * ridge) * (0.03 + 0.26 * pose.amplitude + 0.10 * lens))
            lights[i] = ((light * 255).roundToInt() shl 24) or 0x00FFFFFF
        }
        c.drawBitmapMesh(base, columns, rows, warped, 0, null, 0, paint)
        c.drawBitmapMesh(pale, columns, rows, regular, 0, lights, 0, paint)
    }
}

@Composable
private fun poolCausticWelcomeReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
