package com.ngocdevv.nativecook.blocks.cards.liquidchargecapsule

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val LOOP = 7.5

/** A glass cell whose liquid and submerged bolt share one deterministic wave field. */
@Composable
fun LiquidChargeCapsule(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiquidChargeCapsuleReduced()
    val art = remember(context.applicationContext) { LiquidChargeCapsuleArt(context.applicationContext) }
    val driver = remember { LiquidChargeCapsuleDriver() }
    // This clock is read only in the draw lambda, so frames do not recompose the screen.
    val clock = remember { mutableDoubleStateOf(liquidChargeCapsuleNow()) }
    var revision by remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LiquidChargeCapsuleSystemBars()

    fun wake() {
        clock.doubleValue = liquidChargeCapsuleNow()
        revision++
    }

    DisposableEffect(lifecycle, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                driver.reset(liquidChargeCapsuleNow())
                active = true
                wake()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                active = false
                driver.reset(liquidChargeCapsuleNow())
                wake()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            driver.reset(liquidChargeCapsuleNow())
        }
    }
    LaunchedEffect(autoPlay, reduced, previewTime) {
        driver.reset(liquidChargeCapsuleNow())
        wake()
    }
    LaunchedEffect(autoPlay, reduced, previewTime, revision, active) {
        if (!active || reduced || previewTime != null) return@LaunchedEffect
        while (isActive && (autoPlay || driver.hasTouch)) {
            withFrameNanos {
                val now = liquidChargeCapsuleNow()
                driver.complete(now)
                clock.doubleValue = now
            }
        }
    }

    Canvas(
        modifier.fillMaxSize().background(Color.Black)
            .semantics {
                contentDescription = "Charge reserve. Drag to stir the liquid."
                stateDescription = "Half full"
            }
            .pointerInput(reduced, previewTime, active, autoPlay) {
                if (reduced || previewTime != null || !active) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = down.position
                    val scale = size.width / 393.0
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val delta = change.position - origin
                            if (!driver.held && hypot(delta.x.toDouble(), delta.y.toDouble()) > 3 * scale) {
                                driver.begin(liquidChargeCapsuleNow(), autoPlay)
                                wake()
                            }
                            if (driver.held) {
                                driver.dragX = (delta.x / scale).coerceIn(-110.0, 110.0)
                                driver.dragY = (delta.y / scale).coerceIn(-130.0, 130.0)
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        if (driver.finish(liquidChargeCapsuleNow())) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            wake()
                        }
                    }
                }
            },
    ) {
        val now = clock.doubleValue
        val frame = driver.frame(now, autoPlay, reduced, previewTime)
        val scale = size.width / 393f
        val artWidth = 138f * scale
        val artHeight = 259f * scale
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.translate(size.width / 2f - 2.5f * scale - artWidth / 2f, size.height / 2f + 19f * scale - artHeight / 2f)
        canvas.scale(artWidth / 140f, artHeight / 262f)
        art.draw(canvas, frame.phase, frame.tilt, frame.level)
        canvas.restore()
    }
}

private data class LiquidChargeCapsuleFrame(val phase: Double, val tilt: Double, val level: Double)

private class LiquidChargeCapsuleDriver {
    private var epoch = liquidChargeCapsuleNow()
    private var lastTouch: Double? = null
    private var touchStarted = 0.0
    private var touchPhase = 0.0
    private var returnPhase = 0.0
    var held = false
        private set
    var dragX = 0.0
    var dragY = 0.0
    val hasTouch get() = lastTouch != null

    fun reset(now: Double) {
        epoch = now
        lastTouch = null
        held = false
        dragX = 0.0
        dragY = 0.0
    }

    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, previewTime: Double?): LiquidChargeCapsuleFrame {
        if (reduced) return LiquidChargeCapsuleFrame(0.0, 0.0, 0.0)
        val t = (previewTime ?: if (autoPlay) max(0.0, now - epoch) else 0.0) % LOOP
        val automaticPhase = liquidChargeCapsuleSmooth((t - 0.75) / 6.0) * PI * 2
        val touched = lastTouch
        if (touched == null) return LiquidChargeCapsuleFrame(automaticPhase, 0.0, 0.0)
        val age = now - touched
        val gain = if (held) 1.0 else 1.0 - liquidChargeCapsuleSmooth((age - 2.0) / 0.55)
        val livePhase = touchPhase + (now - touchStarted) * PI * 2 / LOOP
        return LiquidChargeCapsuleFrame(livePhase * gain + returnPhase * (1 - gain), dragX * gain * 0.13, dragY * gain * 0.16)
    }

    fun begin(now: Double, autoPlay: Boolean) {
        touchPhase = frame(now, autoPlay, false, null).phase
        touchStarted = now
        lastTouch = now
        held = true
    }

    fun finish(now: Double): Boolean {
        if (!held) return false
        val future = touchPhase + (now - touchStarted + 2.55) * PI * 2 / LOOP
        returnPhase = ceil(future / (PI * 2)) * PI * 2
        lastTouch = now
        held = false
        return true
    }

    fun complete(now: Double) {
        if (!held && lastTouch?.let { now - it > 2.55 } == true) reset(now)
    }
}

private fun liquidChargeCapsuleNow() = System.nanoTime() / 1_000_000_000.0
private fun liquidChargeCapsuleSmooth(value: Double): Double {
    val x = value.coerceIn(0.0, 1.0)
    return x * x * (3 - 2 * x)
}

private class LiquidChargeCapsuleArt(context: Context) {
    private val glass = context.assets.open("liquidchargecapsule/liquidchargecapsule_glass-shell.png").use { BitmapFactory.decodeStream(it) }
    private val metal = context.assets.open("liquidchargecapsule/liquidchargecapsule_metal-bolt.png").use { BitmapFactory.decodeStream(it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val water = Path()
    private val top = Path()
    private val frontWater = Path()
    private val shell = Path().apply { addRoundRect(RectF(1f, 1f, 139f, 261f), 69f, 69f, Path.Direction.CW) }
    private val inside = Path().apply { addRoundRect(RectF(6f, 6f, 134f, 256f), 64f, 64f, Path.Direction.CW) }
    private val edge = Path().apply { addRoundRect(RectF(7f, 7f, 133f, 255f), 63f, 63f, Path.Direction.CW) }
    private val left = RectF(3f, 50f, 7f, 209f)
    private val metalFrame = RectF(-8.15f, 3.9f, 146.75f, 284.03f)
    private val glassFrame = RectF(-27.2f, -26.4f, 169.3f, 290.6f)
    private val multiply = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        181f / 255f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 208f / 255f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))

    private fun color(hex: Int, alpha: Double = 1.0) = NativeColor.argb((alpha * 255).toInt().coerceIn(0, 255), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)

    private fun fill(canvas: NativeCanvas, path: Path, shader: Shader) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = NativeColor.WHITE
        paint.alpha = 255
        paint.shader = shader
        canvas.drawPath(path, paint)
    }

    private fun image(canvas: NativeCanvas, bitmap: Bitmap?, bounds: RectF, filter: ColorMatrixColorFilter? = null) {
        if (bitmap == null) return
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.alpha = 255
        paint.colorFilter = filter
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    fun draw(canvas: NativeCanvas, phase: Double, tilt: Double, level: Double) {
        fill(canvas, shell, linear(0f, 0f, 140f, 262f, intArrayOf(color(0x121715, 0.5), NativeColor.BLACK, color(0x111713, 0.24))))
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.85f
        paint.color = color(0xC6D4CC, 0.17)
        canvas.drawPath(shell, paint)

        val innerSave = canvas.save()
        canvas.clipPath(inside)
        fun surface(x: Double): Float {
            val u = (x - 6) / 128
            return (121 + level + 2.3 * sin(u * PI * 2) - 14 * sin(u * PI * 2 + phase) + 3.2 * sin(u * PI * 4 - phase * 2) + tilt * (u - 0.5)).toFloat()
        }
        water.rewind()
        water.moveTo(4f, surface(4.0))
        for (n in 1..66) { val x = 4.0 + n * 2; water.lineTo(x.toFloat(), surface(x)) }
        water.lineTo(136f, 262f)
        water.lineTo(4f, 262f)
        water.close()
        fill(canvas, water, linear(70f, (111 + level).toFloat(), 70f, 260f,
            intArrayOf(color(0x78FF00), color(0x03CA08), color(0x00530C), color(0x000301)), floatArrayOf(0f, 0.18f, 0.4f, 1f)))

        top.rewind()
        top.moveTo(5f, surface(5.0))
        for (n in 1..65) { val x = 5.0 + n * 2; top.lineTo(x.toFloat(), surface(x)) }
        for (n in 65 downTo 0) {
            val x = 5.0 + n * 2
            top.lineTo(x.toFloat(), (141 + level + 8 * sin((x - 5) / 130 * PI * 2 + phase) + tilt * ((x - 5) / 130 - 0.5)).toFloat())
        }
        top.close()
        fill(canvas, top, linear(14f, 110f, 128f, 139f, intArrayOf(color(0x64FA00), color(0xFFFF0A), color(0xD9FF00), color(0x5FFC00))))
        image(canvas, metal, metalFrame)

        val submergedSave = canvas.save()
        frontWater.rewind()
        frontWater.moveTo(4f, (141 + level).toFloat())
        for (n in 1..66) {
            val x = 4.0 + n * 2
            val u = (x - 5) / 130
            frontWater.lineTo(x.toFloat(), (141 + level + 8 * sin(u * PI * 2 + phase) + tilt * (u - 0.5)).toFloat())
        }
        frontWater.lineTo(136f, 262f)
        frontWater.lineTo(4f, 262f)
        frontWater.close()
        canvas.clipPath(frontWater)
        canvas.translate((sin(phase) * 1.2).toFloat(), 0f)
        image(canvas, metal, metalFrame, multiply)
        fill(canvas, water, linear(70f, 120f, 70f, 262f, intArrayOf(color(0x03F000, 0.1), color(0x001703, 0.5))))
        canvas.restoreToCount(submergedSave)

        for (i in 0 until 7) {
            val hash = (sin((i + 1) * 12.9898) * 43758.5453) % 1
            val x = 25 + abs(hash) * 70
            val cycle = phase / (PI * 2) + i * 0.173
            val rise = cycle - floor(cycle)
            val alpha = min(1.0, rise / 0.12) * min(1.0, (1 - rise) / 0.22)
            val y = 212 - rise * 67
            val radius = 1.1 + (i % 3) * 0.55
            paint.reset()
            paint.isAntiAlias = true
            paint.color = NativeColor.WHITE
            paint.alpha = 255
            paint.shader = RadialGradient((x + radius).toFloat(), (y + radius).toFloat(), (radius * 1.4).toFloat(),
                intArrayOf(color(0x68FF40, alpha * 0.35), color(0x00A313, 0.0)), null, Shader.TileMode.CLAMP)
            canvas.drawOval(RectF(x.toFloat(), y.toFloat(), (x + radius * 2).toFloat(), (y + radius * 2).toFloat()), paint)
        }
        canvas.restoreToCount(innerSave)

        image(canvas, glass, glassFrame)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = NativeColor.WHITE
        paint.alpha = 255
        paint.shader = linear(4f, 50f, 4f, 209f, intArrayOf(color(0xE3F3E8, 0.0), color(0xB1C6B8, 0.18), color(0x44E84E, 0.27), color(0xA5C7B0, 0.0)))
        canvas.drawRoundRect(left, 2f, 2f, paint)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = NativeColor.WHITE
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.shader = linear(0f, 0f, 140f, 262f, intArrayOf(color(0xF3FFF3, 0.05), NativeColor.TRANSPARENT, color(0x1DFB20, 0.24), NativeColor.TRANSPARENT))
        canvas.drawPath(edge, paint)
    }
}

@Composable
private fun rememberLiquidChargeCapsuleReduced(): Boolean {
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
private fun LiquidChargeCapsuleSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}
