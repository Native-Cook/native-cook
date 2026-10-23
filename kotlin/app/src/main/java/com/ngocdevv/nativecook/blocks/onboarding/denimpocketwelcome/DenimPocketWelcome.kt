package com.ngocdevv.nativecook.blocks.onboarding.denimpocketwelcome

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOOP = 8.0
private fun denimNow() = System.nanoTime() / 1_000_000_000.0
private fun denimSmooth(x: Double): Double { val u = x.coerceIn(0.0, 1.0); return u * u * (3.0 - 2.0 * u) }
private fun denimColor(hex: Int, alpha: Double = 1.0) = (hex and 0xFFFFFF) or ((alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt() shl 24)
private fun denimProgress(time: Double): Double {
    val t = time % LOOP
    if (t < 1.3) return 0.0
    if (t < 1.63) return 0.10 * denimSmooth((t - 1.3) / 0.33)
    if (t < 1.88) return 0.10 * (1.0 - denimSmooth((t - 1.63) / 0.25))
    if (t < 3.75) return 0.86 * denimSmooth((t - 1.88) / 1.87)
    if (t < 4.22) return 0.86 + 0.14 * (1.0 - (1.0 - (t - 3.75) / 0.47).pow(3.0))
    if (t < 5.18) return 1.0
    return 1.0 - denimSmooth((t - 5.18) / 1.92)
}
private fun denimJoin(p: Double) = 116.0 + 650.0 * min(p / 0.86, 1.0) + 128.0 * denimSmooth((p - 0.86) / 0.14)
private fun denimEdge(y: Double, p: Double, side: Double): Double {
    val j = denimJoin(p)
    val gap = 10.0 + 175.0 * denimSmooth(p / 0.86)
    return 196.5 + side * (2.0 + if (y < j) gap * max(0.0, (j - y) / j).pow(0.78) else 0.0)
}

private data class DenimPocketWelcomeDrive(val p: Double, val tab: Double)
private class DenimPocketWelcomeMotion {
    var epoch = denimNow()
    var dragging = false
    var captured = 0.0
    var capturedTab = 0.0
    var finger = 0.0
    var tabDeflection = 0.0
    var releasedAt: Double? = null
    var releaseFrom = 0.0
    var releaseTarget = 0.0
    var releaseVelocity = 0.0
    var ready = false

    fun drive(now: Double, autoPlay: Boolean, reduced: Boolean, previewTime: Double?): DenimPocketWelcomeDrive {
        if (reduced) return DenimPocketWelcomeDrive(1.0, 0.0)
        var t = previewTime ?: (max(0.0, now - epoch) % LOOP)
        if (previewTime == null) {
            if (dragging) return DenimPocketWelcomeDrive(finger, tabDeflection)
            releasedAt?.let { stamp ->
                val age = max(0.0, now - stamp)
                if (age < 2.0) {
                    val delta = releaseFrom - releaseTarget
                    val spring = releaseTarget + delta * exp(-12.0 * age) * cos(12.0 * age) + releaseVelocity * age * exp(-12.0 * age)
                    return DenimPocketWelcomeDrive(spring.coerceIn(0.0, 1.0), tabDeflection * exp(-9.0 * age) * cos(13.0 * age))
                }
                if (age < 2.6) return DenimPocketWelcomeDrive(releaseTarget * (1.0 - denimSmooth((age - 2.0) / 0.6)), 0.0)
                t = max(0.0, age - 2.6) % LOOP
            }
        }
        val p = if (autoPlay || previewTime != null) denimProgress(t) else 0.0
        val angle = -14.0 * denimSmooth((t - 1.15) / 0.15) * (1.0 - denimSmooth((t - 1.88) / 1.97)) +
            9.0 * sin((t - 1.88) * 9.0) * denimSmooth((t - 1.88) / 0.2) * (1.0 - denimSmooth((t - 2.4) / 1.45))
        return DenimPocketWelcomeDrive(p, if (p > 0.98 || (!autoPlay && previewTime == null)) 0.0 else angle)
    }

    fun release(now: Double, from: Double, target: Double, velocity: Double) {
        dragging = false; releaseFrom = from; releaseTarget = target; releaseVelocity = velocity; releasedAt = now
    }

    fun readyAt(now: Double, reduced: Boolean) = ready && (reduced || releasedAt?.let { now - it < 2.6 } == true)
}

private data class DenimPocketWelcomeUi(val open: Boolean, val ready: Boolean, val percent: Int, val dragging: Boolean)

/** A moving metal slider disengages the teeth before its denim halves leave the journal setup. */
@Composable
fun DenimPocketWelcome(autoPlay: Boolean = false, previewTime: Double? = null, modifier: Modifier = Modifier) {
    val reduced = rememberDenimPocketWelcomeReducedMotion()
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val motion = remember { DenimPocketWelcomeMotion() }
    val clock = remember { mutableDoubleStateOf(denimNow()) }
    val currentAutoPlay = rememberUpdatedState(autoPlay)
    val currentReduced = rememberUpdatedState(reduced)
    val currentPreviewTime = rememberUpdatedState(previewTime)
    val sampleAt = remember(motion) {
        { now: Double -> motion.drive(now, currentAutoPlay.value, currentReduced.value, currentPreviewTime.value) }
    }
    var wake by remember { mutableIntStateOf(0) }
    val uiState = remember { mutableStateOf(DenimPocketWelcomeUi(reduced, false, if (reduced) 100 else 0, false)) }
    DenimPocketWelcomeSystemBars()

    LaunchedEffect(reduced) {
        motion.dragging = false; motion.releasedAt = null; motion.finger = 0.0; motion.tabDeflection = 0.0
        motion.epoch = denimNow(); clock.doubleValue = motion.epoch; wake++
    }

    LaunchedEffect(autoPlay, reduced, previewTime, wake) {
        fun updateUi(now: Double) {
            val p = sampleAt(now).p
            val next = DenimPocketWelcomeUi(p > 0.985 || reduced, motion.readyAt(now, reduced), (p * 10.0).roundToInt() * 10, motion.dragging)
            // Only coarse semantic or interactive thresholds enter composition, never frame time.
            if (next != uiState.value) uiState.value = next
        }
        clock.doubleValue = denimNow()
        updateUi(clock.doubleValue)
        if (reduced || previewTime != null || (!autoPlay && !motion.dragging && motion.releasedAt == null)) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { clock.doubleValue = it / 1_000_000_000.0; updateUi(clock.doubleValue) }
            val stamp = motion.releasedAt
            if (!motion.dragging && stamp != null && clock.doubleValue - stamp >= 2.6) {
                motion.epoch = stamp + 2.6
                motion.releasedAt = null; motion.finger = 0.0; motion.tabDeflection = 0.0
                updateUi(clock.doubleValue)
                if (!autoPlay) break
            }
        }
    }

    val release: (Double, Double, Double) -> Unit = { from, target, velocity ->
        if (currentPreviewTime.value == null) {
            val now = denimNow(); motion.release(now, from, target, velocity)
            clock.doubleValue = now; wake++
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
    val startJournal: () -> Unit = {
        if (currentPreviewTime.value == null) {
            motion.ready = true
            if (!currentReduced.value) release(1.0, 1.0, 0.0)
            else {
                clock.doubleValue = denimNow(); wake++
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val ui = uiState.value
        val scale = maxWidth.value / 393f
        val pixelScale = scale * density.density
        val logicalHeight = max(852f, maxHeight.value / scale)
        val safeBottom = WindowInsets.safeDrawing.getBottom(density) / pixelScale
        val bottom = max(0f, safeBottom - 34f)
        val painter = remember(context) { DenimPocketWelcomePainter(context) }
        DisposableEffect(painter) { onDispose { painter.close() } }
        val pose = remember(sampleAt, clock) { { sampleAt(clock.doubleValue) } }

        Box(Modifier.fillMaxSize().pointerInput(pixelScale, autoPlay, reduced, previewTime) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val initial = sampleAt(denimNow())
                val x = down.position.x / pixelScale
                val y = down.position.y / pixelScale
                if (currentReduced.value || currentPreviewTime.value != null || initial.p >= 0.985 ||
                    abs(x - 196.5) > 46.0 || abs(y - denimJoin(initial.p) - 26.0) > 62.0) {
                    waitForUpOrCancellation(); return@awaitEachGesture
                }
                val now = denimNow()
                motion.captured = initial.p; motion.capturedTab = initial.tab; motion.finger = initial.p; motion.tabDeflection = initial.tab; motion.dragging = true
                motion.releasedAt = null; motion.ready = false
                val origin = down.position
                val velocity = VelocityTracker().also { it.addPosition(down.uptimeMillis, down.position) }
                clock.doubleValue = now; wake++; down.consume()
                try {
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        motion.finger = (motion.captured + (change.position.y - origin.y) / pixelScale / 650.0).coerceIn(0.0, 1.0)
                        motion.tabDeflection = (motion.capturedTab + (change.position.x - origin.x) / pixelScale / 3.0).coerceIn(-18.0, 18.0)
                        clock.doubleValue = denimNow(); pressed = change.pressed; change.consume()
                    }
                } finally {
                    val vy = (velocity.calculateVelocity().y / pixelScale).toDouble()
                    if (motion.dragging && !currentReduced.value && currentPreviewTime.value == null) {
                        release(motion.finger, if (motion.finger >= 0.62 || vy >= 720.0) 1.0 else 0.0, vy.coerceIn(-1000.0, 1000.0) / 650.0)
                    } else { motion.dragging = false }
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val current = pose()
                drawIntoCanvas {
                    val canvas = it.nativeCanvas
                    canvas.save(); canvas.scale(pixelScale, pixelScale)
                    painter.draw(canvas, current, bottom.toDouble(), motion.readyAt(clock.doubleValue, currentReduced.value), currentReduced.value, logicalHeight)
                    canvas.restore()
                }
            }
            if (ui.open) {
                Box(Modifier.offset((28f * scale).dp, (119f * scale).dp).size((337f * scale).dp, ((530f - bottom) * scale).dp)
                    .semantics { contentDescription = "Dayloom. Keep a small moment. One photo, one line, one ordinary day. Find the thread. See the days that belong together. Make room for more. Your memories stay close, at your pace." })
                Box(Modifier.offset((20f * scale).dp, ((747f - bottom) * scale).dp).size((353f * scale).dp, (60f * scale).dp)
                    .semantics { contentDescription = if (ui.ready) "Journal ready" else "Start a journal" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = startJournal))
            }
            if (!reduced && previewTime == null && (!ui.open || ui.dragging)) {
                Box(Modifier.offset {
                    IntOffset((150.5f * pixelScale).roundToInt(), ((denimJoin(pose().p) - 36.0) * pixelScale).roundToInt())
                }.size((92f * scale).dp, (124f * scale).dp).semantics {
                    contentDescription = "Open journal cover. Drag the zipper downward to reveal your journal."
                    stateDescription = "${ui.percent} percent open"
                    role = Role.Button
                    onClick { release(pose().p, 1.0, 0.0); true }
                })
            }
        }
    }
}

@Composable
private fun rememberDenimPocketWelcomeReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun DenimPocketWelcomeSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.isAppearanceLightStatusBars = oldStatus; controller.isAppearanceLightNavigationBars = oldNavigation }
    }
}

private class DenimPocketWelcomeLayer(width: Int, height: Int, private val pad: Int = 16, draw: (NativeCanvas) -> Unit) {
    private val bitmap = Bitmap.createBitmap((width + pad * 2) * 3, (height + pad * 2) * 3, Bitmap.Config.ARGB_8888)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val w = width.toFloat()
    private val h = height.toFloat()
    init { val canvas = NativeCanvas(bitmap); canvas.scale(3f, 3f); canvas.translate(pad.toFloat(), pad.toFloat()); draw(canvas) }
    fun draw(canvas: NativeCanvas, x: Double, y: Double, alpha: Double) {
        if (alpha <= 0.0) return
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        rect.set((x - pad).toFloat(), (y - pad).toFloat(), (x + w + pad).toFloat(), (y + h + pad).toFloat())
        canvas.drawBitmap(bitmap, null, rect, paint)
    }
    fun close() = bitmap.recycle()
}

private class DenimPocketWelcomePainter(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { isSubpixelText = true }
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val rounded = Typeface.create("sans-serif-rounded", Typeface.NORMAL)
    private val rect = RectF()
    private val outline = Path()
    private val hem = Path()
    private val fold = Path()
    private val stitches = Path()
    private val teeth = Path()
    private val bevel = Path()
    private val recess = Path()
    private val transform = Matrix()
    private val matrixValues = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val toothTemplates = Array(2) { index -> Path().apply { addRoundRect(if (index == 0) -6f else -1.5f, -1.7f, if (index == 0) 1.5f else 6f, 1.7f, 0.7f, 0.7f, Path.Direction.CW) } }
    private val bevelTemplates = Array(2) { index -> Path().apply { addRect(if (index == 0) -6f else -1.5f, -1.7f, if (index == 0) 1.5f else 6f, -0.95f, Path.Direction.CW) } }
    private val recessTemplates = Array(2) { index -> Path().apply { addRect(if (index == 0) -6f else -1.5f, 1.1f, if (index == 0) 1.5f else 6f, 1.7f, Path.Direction.CW) } }
    private val body = Path().apply { addRoundRect(-15f, -20f, 15f, 23f, 6f, 6f, Path.Direction.CW) }
    private val pull = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRoundRect(-12f, -4f, 12f, 60f, 9f, 9f, Path.Direction.CW)
        addRoundRect(-4f, 43f, 4f, 53f, 3.5f, 3.5f, Path.Direction.CW)
    }
    private val bodyGradient = LinearGradient(-15f, -15f, 15f, 20f, intArrayOf(denimColor(0xAAB3B3), denimColor(0x717C7F), denimColor(0x455255)), null, Shader.TileMode.CLAMP)
    private val spineGradient = LinearGradient(-5f, 0f, 5f, 0f, intArrayOf(denimColor(0xAFB7B7), denimColor(0x6B7679), denimColor(0x859092)), null, Shader.TileMode.CLAMP)
    private val pullGradient = LinearGradient(-12f, 0f, 12f, 55f, intArrayOf(denimColor(0xAAB2B2), denimColor(0x727D7F), denimColor(0x536267)), null, Shader.TileMode.CLAMP)
    private var backgroundHeight = 852f
    private var background = LinearGradient(0f, 0f, 0f, 852f, intArrayOf(denimColor(0x087CFA), denimColor(0x279BF0), denimColor(0x72D3F3)), null, Shader.TileMode.CLAMP)
    private val denim = runCatching {
        context.assets.open("denimpocketwelcome/denimpocketwelcome_denim.jpg").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    private val calendar = DenimPocketWelcomeLayer(337, 201) { drawCalendar(it) }
    private val features = DenimPocketWelcomeLayer(353, 225) { drawFeatures(it) }
    private val start = DenimPocketWelcomeLayer(353, 60, 8) { drawButton(it, false) }
    private val ready = DenimPocketWelcomeLayer(353, 60, 8) { drawButton(it, true) }

    private fun fill(color: Int, shader: Shader? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
        paint.color = color; paint.alpha = 255; paint.shader = shader
        // A reused gradient paint must never keep the alpha of a previous translucent stroke.
        if (shader == null) paint.color = color
    }
    private fun line(color: Int, width: Float) { fill(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = width }
    private fun round(canvas: NativeCanvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, shader: Shader? = null) {
        fill(color, shader); canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
    private fun label(canvas: NativeCanvas, value: String, x: Float, cy: Float, size: Float, color: Int, bold: Boolean = false, centered: Boolean = false, tracking: Float = 0f, round: Boolean = false) {
        text.color = color; text.textSize = size; text.typeface = if (round) rounded else if (bold) semibold else regular
        text.textAlign = Paint.Align.LEFT
        val widths = FloatArray(value.length); text.getTextWidths(value, widths)
        val width = widths.sum() + tracking * (value.length - 1)
        var xx = if (centered) x - width / 2f else x
        val fm = text.fontMetrics; val baseline = cy - (fm.ascent + fm.descent) / 2f
        if (tracking == 0f) canvas.drawText(value, xx, baseline, text)
        else for (i in value.indices) { canvas.drawText(value, i, i + 1, xx, baseline, text); xx += widths[i] + tracking }
    }

    private fun drawCalendar(canvas: NativeCanvas) {
        val x = 120.5f
        fill(denimColor(0xE8E5C9)); paint.setShadowLayer(8f, 0f, 7f, denimColor(0x315F92, 0.20)); canvas.drawRoundRect(x, 0f, x + 96f, 108f, 18f, 18f, paint)
        fill(denimColor(0xE8E5C9)); paint.setShadowLayer(1.5f, 0f, 2f, denimColor(0x000000, 0.12)); canvas.drawRoundRect(x, 0f, x + 96f, 108f, 18f, 18f, paint)
        round(canvas, x + 9f, 22f, 78f, 73f, 8f, denimColor(0x158CFA))
        for (xx in floatArrayOf(20f, 66f)) {
            round(canvas, x + xx, 6f, 10f, 13f, 2f, denimColor(0x65716E))
            fill(denimColor(0xFFFFFF, 0.3)); canvas.drawRect(x + xx + 1f, 7f, x + xx + 3f, 15f, paint)
        }
        round(canvas, x + 33f, 101f, 30f, 2f, 1f, denimColor(0xC7BF9E))
        label(canvas, "Dayloom", 168.5f, 168.5f, 52f, denimColor(0xF0F1DD), centered = true, tracking = -1.6f, round = true)
    }

    private fun drawFeatures(canvas: NativeCanvas) {
        val titles = arrayOf("Keep a small moment", "Find the thread", "Make room for more")
        val captions = arrayOf("One photo, one line, one ordinary day.", "See the days that belong together.", "Your memories stay close, at your pace.")
        for (i in 0..2) {
            val y = i * 75f
            fill(denimColor(0x095D84, 0.65)); canvas.drawCircle(21f, y + 17f, 12f, paint)
            label(canvas, "${i + 1}", 21f, y + 17f, 12f, denimColor(0xF0F1DD), bold = true, centered = true)
            val width = drawFeatureIcon(canvas, i, 53f, y + 2f)
            label(canvas, titles[i], 53f + width + 5f, y + 9f, 14.5f, denimColor(0xF0F1DD), bold = true)
            label(canvas, captions[i], 53f, y + 31.5f, 12.5f, denimColor(0xF0F1DD, 0.64))
        }
    }

    private fun drawFeatureIcon(canvas: NativeCanvas, index: Int, x: Float, y: Float): Float {
        canvas.save(); canvas.translate(x, y)
        line(denimColor(0xF0F1DD), 1.1f); paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        when (index) {
            0 -> {
                canvas.drawRoundRect(0f, 2f, 16f, 13f, 2f, 2f, paint)
                canvas.drawCircle(8f, 7.5f, 3.2f, paint)
                canvas.drawLine(4f, 2f, 5.5f, 0f, paint); canvas.drawLine(5.5f, 0f, 10.5f, 0f, paint); canvas.drawLine(10.5f, 0f, 12f, 2f, paint)
            }
            1 -> for (row in 0..1) for (col in 0..1) canvas.drawRoundRect(col * 8f, row * 8f, col * 8f + 5.8f, row * 8f + 5.8f, 0.9f, 0.9f, paint)
            else -> {
                val path = Path().apply { moveTo(4f, 13.5f); lineTo(11f, 13.5f); quadTo(13f, 13.5f, 13.4f, 11.5f); lineTo(15f, 6f); quadTo(15.4f, 4f, 13.5f, 4f); lineTo(9.2f, 4f); lineTo(10f, 0.5f); quadTo(10f, -2f, 8.3f, -1.5f); lineTo(4f, 5f); close() }
                canvas.drawPath(path, paint); canvas.drawRoundRect(0f, 5f, 3.5f, 14f, 0.7f, 0.7f, paint)
            }
        }
        canvas.restore(); return if (index == 0) 16f else if (index == 1) 13.8f else 15f
    }

    private fun drawButton(canvas: NativeCanvas, isReady: Boolean) {
        round(canvas, 0f, 0f, 353f, 60f, 17f, denimColor(0x087FFA))
        line(denimColor(0xFFFFFF, 0.12), 0.8f); canvas.drawRoundRect(0f, 0f, 353f, 60f, 17f, 17f, paint)
        val value = if (isReady) "Journal ready" else "Start a journal"
        if (!isReady) label(canvas, value, 176.5f, 30f, 16f, denimColor(0xF0F1DD), bold = true, centered = true)
        else {
            text.textSize = 16f; text.typeface = semibold
            val width = text.measureText(value); val startX = (353f - width - 22f) / 2f
            line(denimColor(0xF0F1DD), 1.8f); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            val check = Path().apply { moveTo(startX + 1f, 30f); lineTo(startX + 5f, 34f); lineTo(startX + 13f, 25f) }
            canvas.drawPath(check, paint)
            label(canvas, value, startX + 22f, 30f, 16f, denimColor(0xF0F1DD), bold = true)
        }
    }

    fun draw(canvas: NativeCanvas, state: DenimPocketWelcomeDrive, bottom: Double, isReady: Boolean, reduced: Boolean, logicalHeight: Float) {
        if (backgroundHeight != logicalHeight) {
            backgroundHeight = logicalHeight
            background = LinearGradient(0f, 0f, 0f, logicalHeight, intArrayOf(denimColor(0x087CFA), denimColor(0x279BF0), denimColor(0x72D3F3)), null, Shader.TileMode.CLAMP)
        }
        canvas.drawColor(denimColor(0x72D3F3))
        fill(NativeColor.WHITE, background); canvas.drawRect(0f, 0f, 393f, logicalHeight, paint)
        val p = state.p
        if (p > 0.72 || reduced) {
            val reveal = if (reduced) 1.0 else denimSmooth((p - 0.72) / 0.28)
            calendar.draw(canvas, 28.0, 119.0 + 12.0 * (1.0 - reveal), reveal)
            features.draw(canvas, 20.0, 437.0 - bottom + 8.0 * (1.0 - reveal), reveal)
            (if (isReady) ready else start).draw(canvas, 20.0, 747.0 - bottom, reveal)
        }
        drawCover(canvas, p, state.tab)
    }

    private fun drawShadow(canvas: NativeCanvas, path: Path, color: Int, radius: Float, dx: Float, dy: Float, width: Float = 0f, content: Int = denimColor(0x123342)) {
        if (Build.VERSION.SDK_INT >= 28 || !canvas.isHardwareAccelerated) {
            fill(content); if (width > 0f) { paint.style = Paint.Style.STROKE; paint.strokeWidth = width }
            paint.setShadowLayer(radius, dx, dy, color); canvas.drawPath(path, paint)
        } else {
            val sourceAlpha = NativeColor.alpha(color) / 255.0
            canvas.save(); canvas.translate(dx, dy)
            for (i in 5 downTo 1) {
                line(denimColor(0x000000, sourceAlpha * 0.10), width + radius * i / 2f)
                canvas.drawPath(path, paint)
            }
            canvas.restore()
            fill(content); if (width > 0f) { paint.style = Paint.Style.STROKE; paint.strokeWidth = width }; canvas.drawPath(path, paint)
        }
    }

    private fun drawCover(canvas: NativeCanvas, p: Double, angle: Double) {
        if (p >= 1.0) return
        val depart = denimSmooth((p - 0.86) / 0.14)
        for (index in 0..1) {
            val side = if (index == 0) -1.0 else 1.0
            canvas.save(); canvas.translate((side * 238.0 * depart).toFloat(), (64.0 * depart).toFloat()); canvas.rotate((side * 2.5 * depart).toFloat())
            outline.rewind(); val outer = if (side < 0.0) -240f else 633f
            outline.moveTo(outer, -70f); outline.lineTo(denimEdge(-70.0, p, side).toFloat(), -70f)
            for (row in 0..60) { val y = row * 16.0; outline.lineTo(denimEdge(y, p, side).toFloat(), y.toFloat()) }
            outline.lineTo(outer, 960f); outline.close()
            drawShadow(canvas, outline, denimColor(0x000000, 0.42), 6f, (-side * 4).toFloat(), 2f)
            canvas.save(); canvas.clipPath(outline)
            if (denim != null) {
                fill(NativeColor.WHITE)
                for (row in 0 until 28) {
                    val y = row * 36.0 - 72.0
                    val shift = denimEdge(y + 18.0, p, side) - (196.5 + side * 2.0)
                    canvas.save(); canvas.clipRect(-300f, y.toFloat(), 700f, (y + 36.2).toFloat())
                    rect.set((-100.0 + shift * 0.4).toFloat(), -70f, (493.0 + shift * 0.4).toFloat(), 954f)
                    canvas.drawBitmap(denim, null, rect, paint); canvas.restore()
                }
            }
            canvas.restore()
            hem.rewind()
            for (i in 0..125) {
                val y = i * 7.2 - 36.0; val x = denimEdge(y, p, side) + side * 5.0
                if (i == 0) hem.moveTo(x.toFloat(), y.toFloat()) else hem.lineTo(x.toFloat(), y.toFloat())
            }
            transform.setTranslate((side * 10).toFloat(), 0f); fold.rewind(); hem.transform(transform, fold)
            drawShadow(canvas, fold, denimColor(0x000000, 0.26), 3f, (side * 2).toFloat(), 1f, 5f, denimColor(0x081B29, 0.45))
            line(denimColor(0x7396A3, 0.22), 1.1f); canvas.drawPath(fold, paint)
            line(denimColor(0x132127), 21f); canvas.drawPath(hem, paint)
            line(denimColor(0x3A4A4F, 0.8), 1f); canvas.drawPath(hem, paint)
            stitches.rewind(); teeth.rewind(); bevel.rewind(); recess.rewind()
            for (i in 0 until 126) {
                val y = i * 7.2 + (if (side > 0.0) 3.6 else 0.0) - 28.0
                val x = denimEdge(y, p, side)
                val slope = (denimEdge(y + 1.0, p, side) - denimEdge(y - 1.0, p, side)) / 2.0
                val a = -atan(slope); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                matrixValues[0] = ca; matrixValues[1] = -sa; matrixValues[2] = x.toFloat()
                matrixValues[3] = sa; matrixValues[4] = ca; matrixValues[5] = y.toFloat(); transform.setValues(matrixValues)
                teeth.addPath(toothTemplates[index], transform); bevel.addPath(bevelTemplates[index], transform); recess.addPath(recessTemplates[index], transform)
                val stitchX = x + side * 15.0
                stitches.moveTo(stitchX.toFloat(), (y - 1.5).toFloat()); stitches.lineTo((stitchX + slope * 3.0).toFloat(), (y + 1.5).toFloat())
            }
            line(denimColor(0x738386, 0.58), 0.6f); paint.strokeCap = Paint.Cap.ROUND; canvas.drawPath(stitches, paint)
            fill(denimColor(0x8B9698)); canvas.drawPath(teeth, paint)
            fill(denimColor(0xD5DAD6, 0.75)); canvas.drawPath(bevel, paint)
            fill(denimColor(0x34434A)); canvas.drawPath(recess, paint)
            canvas.restore()
        }
        canvas.save(); canvas.translate(196.5f, (denimJoin(p) + 64.0 * depart).toFloat())
        drawShadow(canvas, body, denimColor(0x000000, 0.18), 7f, 0f, 5f)
        drawShadow(canvas, body, denimColor(0x000000, 0.32), 2.5f, 0f, 1.5f)
        fill(NativeColor.WHITE, bodyGradient); canvas.drawPath(body, paint)
        line(denimColor(0x394A4E), 1f); canvas.drawPath(body, paint)
        round(canvas, -10f, -17f, 20f, 27f, 4f, denimColor(0x596568))
        round(canvas, -5.5f, -19f, 11f, 31f, 4f, NativeColor.WHITE, spineGradient)
        canvas.translate(0f, 11f); canvas.rotate(angle.toFloat())
        drawShadow(canvas, pull, denimColor(0x000000, 0.18), 7f, 0f, 5f)
        drawShadow(canvas, pull, denimColor(0x000000, 0.32), 2.5f, 0f, 1.5f)
        fill(NativeColor.WHITE, pullGradient); canvas.drawPath(pull, paint)
        line(denimColor(0x3B4A4D), 0.8f); canvas.drawPath(pull, paint)
        round(canvas, -7f, 3f, 14f, 28f, 4f, denimColor(0x5B686C))
        line(denimColor(0xFFFFFF, 0.14), 0.7f); canvas.drawRoundRect(-10.5f, -2f, 10.5f, 57f, 8f, 8f, paint)
        canvas.restore()
    }

    fun close() { calendar.close(); features.close(); start.close(); ready.close(); denim?.recycle() }
}
