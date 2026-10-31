package com.ngocdevv.nativecook.blocks.onboarding.wavyframeauction

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun WavyFrameAuction(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val families = remember(context) {
        listOf("Outfit-Medium", "Inter-Medium", "Inter-SemiBold").mapIndexed { index, name ->
            FontFamily(Font("wavyframeauction/wavyframeauction_$name.ttf", context.assets, weight = if (index == 2) FontWeight.SemiBold else FontWeight.Medium))
        }
    }
    val resolver = LocalFontFamilyResolver.current
    val titleFace = resolver.resolve(families[0], FontWeight.Medium).value as Typeface
    val mediumFace = resolver.resolve(families[1], FontWeight.Medium).value as Typeface
    val boldFace = resolver.resolve(families[2], FontWeight.SemiBold).value as Typeface
    val art = remember(titleFace, mediumFace, boldFace) { WavyFrameAuctionArt(titleFace, mediumFace, boldFace) }
    val audio = remember(context, reduced) { if (reduced) null else WavyFrameAuctionAudio(context) }
    val driver = remember(autoPlay, reduced) { WavyFrameAuctionDriver() }
    val revision = remember(driver) { mutableIntStateOf(0) }
    val open = remember(driver) { mutableStateOf(false) }
    val top = WindowInsets.systemBars.getTop(density) / density.density
    val bottom = WindowInsets.systemBars.getBottom(density) / density.density
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun cue(to: Double) {
        audio?.play(if (to > 0.5) "unfold" else "fold")
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.5)
                previous = nanos
                run {
                    driver.step(dt, autoPlay && !reduced, reduced)
                    revision.intValue++
                    val showing = driver.s > 0.6
                    if (open.value != showing) open.value = showing
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = if (open.value) "Lumen Tide number 42, current bid 0.34 ETH. Swipe down to close." else "Swipe up to open the auction"
        onClick {
            driver.last = driver.now; driver.target = if (driver.target > 0.5) 0.0 else 1.0; driver.v = 0.0
            cue(driver.target); true
        }
    }.pointerInput(driver, reduced) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            val startS = driver.s
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, start)
            driver.held = true; driver.last = driver.now
            var activated = false
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - start
                    if (delta.getDistance() / density.density >= 4) activated = true
                    driver.last = driver.now
                    if (activated && change.pressed) driver.s = (startS - delta.y / density.density / 360).coerceIn(0.0, 1.0)
                    change.consume()
                } while (change.pressed)
            } finally {
                driver.last = driver.now; driver.held = false
                driver.v = -tracker.calculateVelocity().y / density.density / 360.0
                val to = if (driver.s + driver.v * 0.18 > 0.5) 1.0 else 0.0
                if (to != driver.target) cue(to)
                driver.target = to
            }
        }
    }) {
        revision.intValue
        drawIntoCanvas {
            val c = it.nativeCanvas
            c.save(); c.scale(density.density, density.density)
            val phase = 2 * PI * (if (autoPlay && !reduced) driver.t else driver.now) / WavyFrameAuctionMotion.LOOP
            art.draw(c, size.width / density.density.toDouble(), size.height / density.density.toDouble(), top.toDouble(), bottom.toDouble(), driver.s, phase, driver.lift, driver.seconds)
            c.restore()
        }
    }
}
