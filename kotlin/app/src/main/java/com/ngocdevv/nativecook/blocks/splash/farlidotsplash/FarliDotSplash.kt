package com.ngocdevv.nativecook.blocks.splash.farlidotsplash

import android.app.Activity
import android.content.res.AssetManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as FarliNativeCanvas
import android.graphics.Color as FarliNativeColor
import android.graphics.ColorSpace
import android.graphics.LinearGradient
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val LOOP = 8f

/** A wordmark becomes a falling dot, then launches an aperture into a travel feed. */
@Composable
fun FarliDotSplash(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(FarliDotSplashReduced(context.contentResolver)) }
    val playback = remember { FarliDotSplashPlayback() }
    val frameTime = remember { mutableFloatStateOf(0f) }
    var wakeClock by remember { mutableIntStateOf(0) }

    DisposableEffect(owner, context) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = FarliDotSplashReduced(context.contentResolver)
        }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = FarliDotSplashReduced(context.contentResolver)
            }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingsObserver
        )
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    LaunchedEffect(active, reduced, autoPlay, previewTime, wakeClock) {
        if (reduced || previewTime != null) {
            frameTime.floatValue = if (reduced) 4.8f else previewTime!!.coerceAtLeast(0f) % LOOP
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        frameTime.floatValue = playback.phase(autoPlay)
        if (!autoPlay && !playback.touching && playback.releasedAt == null) return@LaunchedEffect
        val elapsedAtStart = playback.elapsed
        val startedAt = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                playback.elapsed = elapsedAtStart + (now - startedAt) / 1e9
                if (playback.releasedAt?.let { playback.elapsed - it >= 2.6 } == true) {
                    playback.releasedAt = null
                    playback.launchOnRelease = false
                    playback.heldTime = 0f
                    playback.epoch = playback.elapsed
                }
                frameTime.floatValue = playback.phase(autoPlay)
            }
            if (!autoPlay && !playback.touching && playback.releasedAt == null) break
        }
    }

    val assets = context.applicationContext.assets
    val painter = remember(assets) { FarliDotSplashPainter(assets) }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            testTag = "farli-dot-splash"
            contentDescription = if (reduced) {
                "Farli travel home. Find your somewhere. The quiet side of everywhere. Explore the valley. " +
                    "A little closer: Amber Canyon, 12 local adventures. Moss Coast, 8 slow escapes."
            } else {
                "Farli splash and travel preview. Drag to scrub the splash, or tap to launch the travel feed. " +
                    "The quiet side of everywhere. Explore the valley. A little closer: " +
                    "Amber Canyon, 12 local adventures. Moss Coast, 8 slow escapes."
            }
            if (!reduced && previewTime == null) {
                customActions = listOf(CustomAccessibilityAction("Launch travel feed") {
                    playback.touching = false
                    playback.launchOnRelease = true
                    playback.releasedAt = playback.elapsed
                    frameTime.floatValue = 2.45f
                    wakeClock++
                    true
                })
            }
        }
        .pointerInput(reduced, previewTime, autoPlay) {
            if (reduced || previewTime != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                playback.heldTime = playback.phase(autoPlay)
                playback.releasedAt = null
                playback.launchOnRelease = false
                playback.touching = true
                var didScrub = false
                var passedLaunch = false
                var releasedNormally = false
                wakeClock++
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val distance = hypot(
                            (change.position.x - down.position.x).toDouble(),
                            (change.position.y - down.position.y).toDouble()
                        )
                        if (distance > 8 * density) {
                            didScrub = true
                            playback.heldTime = (change.position.x / size.width * 7.3f).coerceIn(0f, 7.3f)
                            frameTime.floatValue = playback.heldTime
                            if (playback.heldTime >= 2.82f && !passedLaunch) {
                                passedLaunch = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                    releasedNormally = true
                } finally {
                    playback.launchOnRelease = !didScrub && releasedNormally
                    if (playback.launchOnRelease) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    playback.touching = false
                    playback.releasedAt = playback.elapsed
                    frameTime.floatValue = playback.phase(autoPlay)
                }
            }
        }
        .drawWithCache {
            // Rasterize all feed text and decoded photos once for this viewport.
            val scale = min(size.width / 393f, size.height / 852f)
            val cacheScale = scale.coerceAtLeast(1f)
            val home = painter.renderHome(cacheScale)
            val destination = RectF(0f, 0f, 393f, 852f)
            onDrawBehind {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawColor(FarliDotSplashPainter.ORANGE)
                canvas.save()
                canvas.translate((size.width - 393 * scale) / 2, (size.height - 852 * scale) / 2)
                canvas.scale(scale, scale)
                painter.draw(canvas, frameTime.floatValue, home, destination)
                canvas.restore()
            }
        }
    ) {}
}

private fun FarliDotSplashReduced(resolver: android.content.ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun FarliDotSplashClamp(value: Float): Float = value.coerceIn(0f, 1f)
private fun FarliDotSplashSmooth(value: Float): Float {
    val u = FarliDotSplashClamp(value)
    return u * u * (3 - 2 * u)
}
private fun FarliDotSplashOut(value: Float): Float {
    val u = 1 - FarliDotSplashClamp(value)
    return 1 - u * u * u
}
private fun FarliDotSplashMix(a: Float, b: Float, u: Float): Float = a + (b - a) * u

private class FarliDotSplashPlayback {
    var elapsed = 0.0
    var epoch = 0.0
    var heldTime = 0f
    var touching = false
    var releasedAt: Double? = null
    var launchOnRelease = false

    fun phase(autoPlay: Boolean): Float {
        if (touching) return heldTime
        releasedAt?.let { release ->
            val age = (elapsed - release).coerceAtLeast(0.0).toFloat()
            val held = if (launchOnRelease) min(4.1f, 2.45f + age) else heldTime
            if (age < 2) return held
            if (age < 2.6f) return held * (1 - FarliDotSplashSmooth((age - 2) / 0.6f))
            return if (autoPlay) (age - 2.6f) % LOOP else 0f
        }
        return if (autoPlay) ((elapsed - epoch).coerceAtLeast(0.0) % LOOP).toFloat() else 0f
    }
}

private class FarliDotSplashPainter(assets: AssetManager) {
    companion object {
        val ORANGE = FarliNativeColor.rgb(240, 74, 45)
        private val PAPER = FarliNativeColor.rgb(255, 253, 247)
        private val INK = FarliNativeColor.rgb(34, 43, 40)
        private val SECONDARY = FarliNativeColor.rgb(110, 119, 112)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var homeCache: Bitmap? = null
    private val heroPhoto = decodePhoto(assets, "farlidotsplash_hero.jpg")
    private val canyonPhoto = decodePhoto(assets, "farlidotsplash_canyon.jpg")
    private val coastPhoto = decodePhoto(assets, "farlidotsplash_coast.jpg")
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(normal, 600, false) else medium
    private val glyphs = "FARLI".map { it.toString() }
    private val glyphSlots: List<Float> = run {
        paint.typeface = bold
        paint.textSize = 36f
        val widths = glyphs.map { paint.measureText(it) }
        var x = 196.5f - (widths.sum() + 6f) / 2
        widths.map { width -> (x + width / 2).also { x += width + 1.5f } }
    }

    private fun decodePhoto(assets: AssetManager, name: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        }
        return assets.open("farlidotsplash/$name").use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream, null, options)) { "Cannot decode bundled Farli photo: $name" }
        }.also { it.prepareToDraw() }
    }

    private fun ink(color: Int, alpha: Float = 1f): Paint = paint.apply {
        reset()
        isAntiAlias = true
        this.color = color
        this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
    }
    private fun color(hex: Int): Int = hex or (0xff shl 24)
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): Path = Path().apply {
        addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW)
    }
    private fun ellipse(x: Float, y: Float, w: Float, h: Float): Path = Path().apply {
        addOval(RectF(x, y, x + w, y + h), Path.Direction.CW)
    }
    private fun fill(c: FarliNativeCanvas, path: Path, color: Int, alpha: Float = 1f) {
        c.drawPath(path, ink(color, alpha))
    }
    private fun stroke(c: FarliNativeCanvas, path: Path, color: Int, width: Float, alpha: Float = 1f) {
        c.drawPath(path, ink(color, alpha).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }
    private fun gradient(c: FarliNativeCanvas, path: Path, colors: IntArray, x1: Float, y1: Float, x2: Float, y2: Float) {
        c.drawPath(path, ink(FarliNativeColor.WHITE).apply {
            shader = LinearGradient(x1, y1, x2, y2, colors, null, Shader.TileMode.CLAMP)
        })
    }
    private fun text(
        c: FarliNativeCanvas, value: String, x: Float, y: Float, size: Float, color: Int,
        typeface: Typeface = normal, align: Paint.Align = Paint.Align.LEFT, alpha: Float = 1f
    ) {
        val p = ink(color, alpha)
        p.typeface = typeface
        p.textSize = size
        p.textAlign = align
        c.drawText(value, x, y - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    }

    private fun mark(c: FarliNativeCanvas, scale: Float, opacity: Float) {
        if (opacity <= 0f) return
        c.save()
        c.translate(196.5f, 426f)
        c.scale(scale, scale)
        val stem = Path().apply {
            moveTo(-17f, 22f)
            lineTo(-17f, -11f)
            quadTo(-17f, -21f, -7f, -21f)
            lineTo(19f, -21f)
        }
        stroke(c, stem, PAPER, 10f, opacity)
        val arrow = Path().apply {
            moveTo(7f, -33f)
            lineTo(20f, -21f)
            lineTo(7f, -9f)
        }
        stroke(c, arrow, PAPER, 10f, opacity)
        fill(c, ellipse(-22f, 13f, 10f, 10f), ORANGE, opacity)
        c.restore()
    }

    fun draw(c: FarliNativeCanvas, t: Float, home: Bitmap, destination: RectF) {
        val returning = FarliDotSplashSmooth((t - 6.95f) / 0.35f)
        val markAlpha = if (t < 6.95f) 1 - FarliDotSplashSmooth((t - 0.9f) / 0.45f) else returning
        val markScale = if (t < 6.95f) 1 - 0.55f * FarliDotSplashSmooth((t - 0.9f) / 0.45f) else FarliDotSplashMix(0.45f, 1f, returning)
        mark(c, markScale, markAlpha)
        if (t > 0.98f && t < 2.45f) {
            glyphs.forEachIndexed { i, glyph ->
                val enter = FarliDotSplashOut((t - 0.98f - i * 0.035f) / 0.38f)
                val exit = FarliDotSplashSmooth((t - 1.95f - i * 0.025f) / 0.39f)
                c.save()
                c.translate(FarliDotSplashMix(196.5f, glyphSlots[i], enter) * (1 - exit) + 196.5f * exit, 426f)
                val scale = FarliDotSplashMix(0.65f, 1f, enter) * (1 - 0.82f * exit)
                c.scale(scale, scale)
                text(c, glyph, 0f, 0f, 36f, PAPER, bold, Paint.Align.CENTER, enter * (1 - exit))
                c.restore()
            }
        }
        var dotY = 426f
        var radius = 5f
        var stretch = 1f
        if (t >= 2.45f && t < 2.82f) {
            val u = FarliDotSplashClamp((t - 2.45f) / 0.37f)
            dotY = FarliDotSplashMix(426f, 586f, u * u)
            stretch = 1 + 0.7f * sin(PI.toFloat() * u) - 0.28f * u.pow(8)
        } else if (t >= 2.82f && t < 3.38f) {
            val u = FarliDotSplashOut((t - 2.82f) / 0.56f)
            dotY = FarliDotSplashMix(586f, 92f, u)
            radius = FarliDotSplashMix(5f, 11f, u)
            val launch = FarliDotSplashClamp((t - 2.82f) / 0.56f)
            stretch = 0.72f + 0.28f * FarliDotSplashSmooth(launch) + 0.6f * sin(PI.toFloat() * launch)
        } else if (t >= 6.5f) {
            val u = FarliDotSplashSmooth((t - 6.5f) / 0.45f)
            dotY = FarliDotSplashMix(92f, 426f, u)
            radius = FarliDotSplashMix(11f, 5f, u) * (1 - returning)
        }
        if (t >= 2.25f && t < 3.38f || t >= 6.5f && t < 7.3f) {
            radius *= FarliDotSplashSmooth((t - 2.25f) / 0.2f)
            fill(c, ellipse(196.5f - radius / sqrt(stretch), dotY - radius * stretch, 2 * radius / sqrt(stretch), 2 * radius * stretch), PAPER)
        }
        if (t >= 3.38f && t < 6.5f) {
            val open = FarliDotSplashOut((t - 3.38f) / 0.67f) * (1 - FarliDotSplashSmooth((t - 5.7f) / 0.8f))
            val aperture = FarliDotSplashMix(11f, 950f, open)
            c.save()
            c.clipPath(ellipse(196.5f - aperture, 92f - aperture, aperture * 2, aperture * 2))
            fill(c, rect(-1000f, -1000f, 2393f, 2852f), PAPER)
            c.drawBitmap(home, null, destination, bitmapPaint)
            c.restore()
        }
        val hint = if (t < 1.2f) 1 - FarliDotSplashSmooth((t - 0.9f) / 0.3f) else FarliDotSplashSmooth((t - 7.25f) / 0.3f)
        text(c, "Drag to explore", 196.5f, 776f, 11f, PAPER, medium, Paint.Align.CENTER, 0.72f * hint)
    }

    fun renderHome(scale: Float): Bitmap {
        val width = ceil(393 * scale).toInt()
        val height = ceil(852 * scale).toInt()
        homeCache?.let { if (it.width == width && it.height == height) return it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = FarliNativeCanvas(bitmap)
        canvas.scale(bitmap.width / 393f, bitmap.height / 852f)
        drawHome(canvas)
        homeCache = bitmap
        return bitmap
    }

    private fun drawHome(c: FarliNativeCanvas) {
        fill(c, rect(0f, 0f, 393f, 852f), PAPER)
        text(c, "farli", 24f, 87f, 26f, INK, bold)
        val sparkle = Path().apply {
            moveTo(355f, 74f)
            quadTo(355f, 85f, 366f, 85f)
            quadTo(355f, 85f, 355f, 96f)
            quadTo(355f, 85f, 344f, 85f)
            quadTo(355f, 85f, 355f, 74f)
        }
        fill(c, sparkle, ORANGE)
        fill(c, rect(24f, 112f, 345f, 46f, 23f), color(0xF1F2EA))
        stroke(c, ellipse(40f, 128f, 11f, 11f), SECONDARY, 1.5f)
        val search = Path().apply { moveTo(50f, 138f); lineTo(54f, 142f) }
        stroke(c, search, SECONDARY, 1.5f)
        text(c, "Where will you wander?", 65f, 135f, 13f, SECONDARY)
        text(c, "Find your", 24f, 195f, 32f, INK, bold)
        text(c, "somewhere.", 24f, 232f, 32f, INK, bold)
        text(c, "Small detours. Big memories.", 24f, 264f, 13f, SECONDARY)
        landscape(c)
        text(c, "A little closer", 24f, 584f, 21f, INK, semibold)
        text(c, "View all", 369f, 584f, 11f, ORANGE, semibold, Paint.Align.RIGHT)
        mini(c, 24f, false)
        mini(c, 202f, true)
        text(c, "Amber Canyon", 24f, 756f, 14f, INK, semibold)
        text(c, "Moss Coast", 202f, 756f, 14f, INK, semibold)
        text(c, "12 local adventures", 24f, 777f, 10f, SECONDARY)
        text(c, "8 slow escapes", 202f, 777f, 10f, SECONDARY)
        fill(c, rect(0f, 794f, 393f, 58f), PAPER)
        fill(c, rect(24f, 794f, 345f, 0.75f), color(0xE6E8E0))
        stroke(c, ellipse(58f, 803f, 14f, 14f), ORANGE, 1.6f)
        val needle = Path().apply { moveTo(61f, 813f); lineTo(69f, 807f); lineTo(66f, 815f); close() }
        fill(c, needle, ORANGE)
        val heart = Path().apply {
            moveTo(196.5f, 817f)
            cubicTo(180f, 807f, 190f, 798f, 189f, 807f)
            cubicTo(197f, 800f, 205f, 800f, 204f, 807f)
            quadTo(207f, 811f, 196.5f, 817f)
        }
        stroke(c, heart, SECONDARY, 1.5f)
        stroke(c, rect(321f, 803f, 14f, 14f, 3f), SECONDARY, 1.5f)
        stroke(c, rect(325f, 800f, 6f, 5f, 2f), SECONDARY, 1.5f)
    }

    private fun landscape(c: FarliNativeCanvas) {
        c.save()
        c.clipPath(rect(24f, 286f, 345f, 262f, 22f))
        c.drawBitmap(heroPhoto, null, RectF(24f, 286f, 369f, 548f), bitmapPaint)
        gradient(c, rect(24f, 286f, 345f, 96f), intArrayOf(0x38000000, FarliNativeColor.TRANSPARENT), 24f, 286f, 24f, 382f)
        gradient(c, rect(24f, 386f, 345f, 162f), intArrayOf(FarliNativeColor.TRANSPARENT, color(0x173E36) and 0x00ffffff or (199 shl 24)), 24f, 386f, 24f, 548f)
        text(c, "THE LONG WAY HOME", 43f, 310f, 9f, PAPER, semibold)
        text(c, "The quiet side", 43f, 474f, 24f, PAPER, bold)
        text(c, "of everywhere.", 43f, 502f, 24f, PAPER, bold)
        text(c, "Explore the valley  ↗", 43f, 530f, 11f, PAPER, medium, alpha = 0.92f)
        c.restore()
    }

    private fun mini(c: FarliNativeCanvas, x: Float, coast: Boolean) {
        c.save()
        c.clipPath(rect(x, 610f, 167f, 127f, 16f))
        c.drawBitmap(if (coast) coastPhoto else canyonPhoto, null, RectF(x, 610f, x + 167f, 737f), bitmapPaint)
        fill(c, ellipse(x + 133, 620f, 24f, 24f), PAPER, 0.95f)
        text(c, "↗", x + 145, 632f, 15f, INK, medium, Paint.Align.CENTER)
        c.restore()
    }
}
