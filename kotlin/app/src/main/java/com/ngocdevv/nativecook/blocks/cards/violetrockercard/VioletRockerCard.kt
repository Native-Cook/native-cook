package com.ngocdevv.nativecook.blocks.cards.violetrockercard

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.math.max

/** A native perspective rocker with a captured camera, wedge extrusion and two face textures. */
@Composable
fun VioletRockerCard(autoPlay: Boolean = false, previewTime: Double? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberVioletRockerCardReduced()
    val driver = remember { VioletRockerCardDriver() }
    val painter = remember(context.applicationContext) { VioletRockerCardPainter(context.applicationContext) }
    val clock = remember { mutableDoubleStateOf(violetRockerCardNow()) }
    var revision by remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val semanticOn = remember { mutableStateOf(reduced) }
    val dark = remember { mutableStateOf(reduced) }
    val preview = previewTime?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    VioletRockerCardSystemBars(dark.value)

    fun wake() { clock.doubleValue = violetRockerCardNow(); revision++ }
    fun light() { if (!reduced) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun select(target: Double): Boolean {
        if (!active || preview != null) return false
        if (driver.select(target, violetRockerCardNow(), autoPlay, reduced)) light()
        wake(); return true
    }
    DisposableEffect(lifecycle, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                active = false; driver.reset(violetRockerCardNow()); wake()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                driver.reset(violetRockerCardNow()); active = true; wake()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); driver.reset(violetRockerCardNow()) }
    }
    LaunchedEffect(autoPlay, reduced, previewTime) { driver.reset(violetRockerCardNow()); wake() }
    LaunchedEffect(autoPlay, reduced, preview, revision, active) {
        if (!active || preview != null || reduced || driver.held) return@LaunchedEffect
        while (isActive && !driver.held && (autoPlay || driver.moving)) {
            withFrameNanos {
                val now = violetRockerCardNow(); driver.complete(now, autoPlay, reduced); clock.doubleValue = now
            }
        }
    }
    Canvas(modifier.fillMaxSize().semantics {
        contentDescription = "Ambient light"
        stateDescription = if (semanticOn.value) "On" else "Off"
        role = Role.Button
        onClick("Toggle ambient light") { select(if (semanticOn.value) 0.0 else 1.0) }
        progressBarRangeInfo = ProgressBarRangeInfo(if (semanticOn.value) 1f else 0f, 0f..1f)
        setProgress { select(if (it >= 0.5f) 1.0 else 0.0) }
        customActions = listOf(CustomAccessibilityAction("Turn on") { select(1.0) }, CustomAccessibilityAction("Turn off") { select(0.0) })
    }.pointerInput(autoPlay, reduced, preview, active) {
        if (!active || preview != null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scale = size.width / 393.0
            val top = max(0.0, (size.height - 852 * scale) / 2)
            val start = down.position
            val startX = start.x / scale; val startY = (start.y - top) / scale
            val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis, start)
            var last = start; var normal = false
            if (!reduced && driver.begin(startX, startY, violetRockerCardNow(), autoPlay)) wake()
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    last = change.position; tracker.addPosition(change.uptimeMillis, last)
                    if (!reduced) {
                        if (!driver.held && driver.begin(startX, startY, violetRockerCardNow(), autoPlay)) wake()
                        if (driver.held) {
                            driver.move(last.x / scale, (last.y - top) / scale,
                                hypot((last.x - start.x).toDouble(), (last.y - start.y).toDouble()) / scale,
                                violetRockerCardNow(), autoPlay)
                            clock.doubleValue = violetRockerCardNow(); change.consume()
                        }
                    }
                    if (!change.pressed) { normal = true; break }
                }
            } finally {
                val now = violetRockerCardNow()
                if (reduced) { if (normal && driver.reducedTap(startX, startY, now)) wake() }
                else if (driver.held) {
                    val velocity = if (normal) tracker.calculateVelocity() else androidx.compose.ui.unit.Velocity.Zero
                    if (driver.finish(last.x / scale, (last.y - top) / scale, velocity.x / scale, velocity.y / scale, normal, now, autoPlay)) light()
                    wake()
                }
            }
        }
    }) {
        // Only draw state changes each frame; accessibility/theme updates occur at the threshold.
        val f = driver.frame(clock.doubleValue, autoPlay, reduced, preview)
        if (semanticOn.value != (f.n >= 0.5)) semanticOn.value = f.n >= 0.5
        if (dark.value != (f.n > 0.5)) dark.value = f.n > 0.5
        val c = drawContext.canvas.nativeCanvas
        c.drawColor(violetRockerCardColor(0xC2C2C2, 0x111113, f.n))
        val scale = size.width / 393f; val top = max(0f, (size.height - 852 * scale) / 2)
        c.save(); c.translate(0f, top); c.scale(scale, scale); c.clipRect(0f, 0f, 393f, 852f)
        painter.draw(c, f); c.restore()
    }
}

@Composable
private fun rememberVioletRockerCardReduced(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read(); onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun VioletRockerCardSystemBars(dark: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller, dark) {
        val oldStatus = controller.isAppearanceLightStatusBars; val oldNavigation = controller.isAppearanceLightNavigationBars
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.isAppearanceLightStatusBars = !dark; controller.isAppearanceLightNavigationBars = !dark
        onDispose { controller.isAppearanceLightStatusBars = oldStatus; controller.isAppearanceLightNavigationBars = oldNavigation }
    }
}
