package com.ngocdevv.nativecook.blocks.playground.jellygooglymascot

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * A faithful port of the RN toy: a 128 dp, 48-point jelly with delayed googly pupils.
 * LOOP = 8 s. Pokes at 1.6 / 4.3 / 7.0 s squash and blink the blob and crossfade its flavour.
 * Drag to be watched; tap to poke. After release, hold 2 s and hand back over 0.6 s.
 */
@Composable
fun JellyGooglyMascot(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    title: String = "Poke the\njelly",
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = jellyGooglyMascotReducedMotion()
    val motion = remember { JellyGooglyMascotMotion() }
    val painter = remember { JellyGooglyMascotPainter() }
    val tick = remember { mutableIntStateOf(0) }
    var touches by remember { mutableIntStateOf(0) }
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density

    DisposableEffect(view) {
        val window = jellyGooglyMascotActivity(context)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            oldStatus?.let { controller.isAppearanceLightStatusBars = it }
            oldNavigation?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }

    LaunchedEffect(autoPlay, reduced, lifecycle, touches) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (autoPlay || motion.manual) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                    previous = now
                    motion.advance(dt, autoPlay)
                    tick.intValue++
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val textMeasurer = rememberTextMeasurer()
        val copy = remember(title, maxWidth, density) {
            fun measure(text: String, size: Int, weight: FontWeight, tracking: Double = 0.0, height: Int? = null) = textMeasurer.measure(
                text,
                TextStyle(
                    fontSize = size.sp,
                    fontWeight = weight,
                    letterSpacing = tracking.sp,
                    lineHeight = (height ?: (size * 1.2).toInt()).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                constraints = Constraints(maxWidth = ((maxWidth.value - 48) * density.density).toInt().coerceAtLeast(1)),
            )
            listOf(
                measure("blobby", 20, FontWeight.Black, -0.5),
                measure("Playground", 14, FontWeight.SemiBold),
                measure(title, 46, FontWeight.Black, -1.8, 48),
                measure("Drag to be watched · tap to poke", 15, FontWeight.SemiBold),
            ) + jellyGooglyMascotFlavours.map { measure(it.name, 30, FontWeight.Black, -0.8) }
        }

        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "blobby. $title. A jelly blob with googly eyes. Drag to be watched, tap to poke." }
                .pointerInput(reduced, density.density) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.density
                        fun point(position: Offset) = Pair((position.x - size.width / 2f) / d.toDouble(), (position.y - size.height / 2f) / d.toDouble())
                        val initial = point(down.position)
                        motion.begin(initial.first, initial.second)
                        touches++
                        var canTap = true
                        var released = false
                        var last = down
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val position = point(change.position)
                                motion.move(position.first, position.second)
                                if ((change.position - down.position).getDistance() > 10 * d || event.changes.count { it.pressed } > 1) canTap = false
                                last = change
                                change.consume()
                                if (!change.pressed) { released = true; break }
                            } while (true)
                        } finally {
                            motion.end()
                            tick.intValue++
                        }
                        if (released && canTap && last.uptimeMillis - down.uptimeMillis <= 500) {
                            val position = point(last.position)
                            motion.tap(position.first, position.second)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        tick.intValue++
                    }
                },
        ) {
            tick.intValue
            val d = density.density
            val width = size.width / d
            val height = size.height / d
            val fl = motion.poke.flavour
            val base = floor(fl).toInt()
            val a = jellyGooglyMascotFlavours[base % 3]
            val b = jellyGooglyMascotFlavours[(base + 1) % 3]
            val room = Color(jellyGooglyMascotMix(a.room, b.room, fl - base))
            val ink = Color(jellyGooglyMascotMix(a.ink, b.ink, fl - base))
            drawRect(room)
            drawText(copy[0], ink, topLeft = Offset(24 * d, (top + 8 + 17) * d - copy[0].size.height / 2))
            drawText(copy[1], ink, alpha = 0.6f, topLeft = Offset(size.width - 24 * d - copy[1].size.width, (top + 8 + 17) * d - copy[1].size.height / 2))
            drawText(copy[2], ink, topLeft = Offset(24 * d, (top + 8 + 34 + 14) * d))
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(d, d)
            painter.draw(canvas, motion.clock, width / 2, height / 2, motion.target, motion.poke, autoPlay && !motion.manual)
            canvas.restore()
            val hintTop = size.height - (bottom + 24) * d - copy[3].size.height
            val namesTop = hintTop - 46 * d
            for (i in 0..2) {
                var distance = abs(fl - i)
                distance = min(distance, 3 - distance)
                val alpha = (1 - (distance / 0.5).coerceIn(0.0, 1.0)).toFloat()
                drawText(copy[4 + i], ink, alpha = alpha, topLeft = Offset((size.width - copy[4 + i].size.width) / 2, namesTop))
            }
            drawText(copy[3], ink, alpha = 0.6f, topLeft = Offset((size.width - copy[3].size.width) / 2, hintTop))
        }
    }
}

@Composable
private fun jellyGooglyMascotReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private tailrec fun jellyGooglyMascotActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> jellyGooglyMascotActivity(context.baseContext)
    else -> null
}
