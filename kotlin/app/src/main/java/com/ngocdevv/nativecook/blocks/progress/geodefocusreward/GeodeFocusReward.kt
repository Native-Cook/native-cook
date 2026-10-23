package com.ngocdevv.nativecook.blocks.progress.geodefocusreward

import android.database.ContentObserver
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
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

private const val LOOP = GEODE_FOCUS_REWARD_LOOP

/** An earned stone opens to reveal the mineral that was inside all along. */
@Composable
fun GeodeFocusReward(autoPlay: Boolean = false, previewTime: Double? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = remember(context.applicationContext) { GeodeFocusRewardResources.load(context) }
    val driver = remember { GeodeFocusRewardDriver() }
    val painter = remember(resources) { GeodeFocusRewardPainter(resources) }
    val clock = remember { mutableDoubleStateOf(geodeFocusNow()) }
    var revision by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    val reduced = rememberGeodeFocusRewardReduced()
    val latestAuto = rememberUpdatedState(autoPlay); val latestReduced = rememberUpdatedState(reduced); val latestPreview = rememberUpdatedState(previewTime)
    val view = LocalView.current; val density = LocalDensity.current; val owner = LocalLifecycleOwner.current
    GeodeFocusRewardSystemBars()
    fun wake() { clock.doubleValue = geodeFocusNow(); revision++ }
    fun action(name: String) {
        val now = geodeFocusNow(); val current = driver.visible(now, latestAuto.value, latestReduced.value, latestPreview.value)
        driver.press = null
        when (name) {
            "keep" -> { driver.kept = true; driver.event = GeodeFocusRewardEvent(now, current, GeodeFocusRewardEventKind.Hold) }
            "replay" -> { driver.kept = false; driver.fractureTicked = false
                if (latestReduced.value) { driver.quietReplay = now; driver.event = GeodeFocusRewardEvent(now, GeodeFocusRewardMath.reduced, GeodeFocusRewardEventKind.Hold) }
                else driver.event = GeodeFocusRewardEvent(now, current, GeodeFocusRewardEventKind.Replay) }
            "reveal" -> { if (latestReduced.value || latestPreview.value != null) return; driver.fractureTicked = true
                driver.event = GeodeFocusRewardEvent(now, current, GeodeFocusRewardEventKind.Release, true, yawTarget = current.hero.manualYaw) }
            "close" -> { driver.event = GeodeFocusRewardEvent(now, current, GeodeFocusRewardEventKind.Hold); driver.setCollapsed(true, now, if (latestReduced.value) .18 else .24) }
            "reopen" -> { driver.setCollapsed(false, now, if (latestReduced.value) .18 else .24); driver.event = GeodeFocusRewardEvent(now, current, GeodeFocusRewardEventKind.Hold) }
        }
        view.performHapticFeedback(if (name == "keep" || name == "reveal") HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
        wake()
    }
    DisposableEffect(owner, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { driver.clear(geodeFocusNow()); visible = false }
            else if (event == Lifecycle.Event.ON_START) { driver.epoch = geodeFocusNow(); visible = true }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); driver.clear(geodeFocusNow()) }
    }
    LaunchedEffect(reduced) { driver.clear(geodeFocusNow()); clock.doubleValue = driver.epoch; revision++ }
    LaunchedEffect(autoPlay) { if (autoPlay && driver.press == null && driver.event == null) { driver.epoch = geodeFocusNow(); clock.doubleValue = driver.epoch } }
    LaunchedEffect(autoPlay, reduced, previewTime, revision, visible) {
        while (isActive && visible) {
            val now = geodeFocusNow()
            if (driver.complete(now, reduced)) revision++
            val progress = driver.frame(now, autoPlay, reduced, previewTime).hero.p
            if (driver.press != null && !reduced && !driver.fractureTicked && driver.previousProgress < .62 && progress >= .62) {
                driver.fractureTicked = true; view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            driver.previousProgress = progress; clock.doubleValue = now
            val frames = driver.fading(now) || (!reduced && previewTime == null && !driver.collapsed && (autoPlay || driver.press != null || driver.event != null))
            if (frames) withFrameNanos { clock.doubleValue = it / 1_000_000_000.0 }
            else { val deadline = driver.nextDeadline() ?: break; delay(max(1L, ((deadline - now) * 1000).toLong())) }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = maxWidth.value / 393f; val pixels = scale * density.density
        val height = max(852f, maxHeight.value / scale)
        val bottom = max(0f, WindowInsets.safeDrawing.getBottom(density) / pixels - 34f)
        Box(Modifier.fillMaxSize().pointerInput(pixels, autoPlay, reduced, previewTime) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x / pixels; val y = down.position.y / pixels
                if (latestReduced.value || latestPreview.value != null || driver.collapsed || x !in 80f..313f || y !in 310f..595f) { waitForUpOrCancellation(); return@awaitEachGesture }
                val now = geodeFocusNow(); driver.capture = driver.visible(now, latestAuto.value, latestReduced.value, latestPreview.value)
                driver.press = now; driver.dx = 0.0; driver.dy = 0.0; driver.yaw = GeodeFocusRewardYaw(); driver.event = null; driver.fractureTicked = false; driver.previousProgress = driver.capture.hero.p
                val origin = down.position; val velocity = VelocityTracker(); velocity.addPosition(down.uptimeMillis, down.position)
                down.consume(); clock.doubleValue = now; revision++
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        driver.dx = ((change.position.x - origin.x) / pixels).toDouble(); driver.dy = ((change.position.y - origin.y) / pixels).toDouble()
                        val stamp = geodeFocusNow(); val speed = velocity.calculateVelocity()
                        driver.yaw.follow((speed.x / pixels * .006).toDouble().coerceIn(-5.0, 5.0), stamp)
                        clock.doubleValue = stamp; change.consume()
                        if (!change.pressed) { released = true; break }
                    }
                } finally {
                    if (driver.press != null && !latestReduced.value && latestPreview.value == null) {
                        val stamp = geodeFocusNow(); val pressure = if (stamp - driver.press!! > .12) .62 else 0.0
                        val progressVelocity = (pressure - velocity.calculateVelocity().y / pixels / 210).coerceIn(-1.6, 1.6)
                        driver.finish(stamp, if (released) progressVelocity else 0.0, !released, latestAuto.value, latestReduced.value, latestPreview.value)
                        clock.doubleValue = stamp; revision++
                    }
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val now = clock.doubleValue; val frame = driver.frame(now, autoPlay, reduced, previewTime)
                driver.displayed = frame; driver.displayedAt = now
                drawIntoCanvas { target -> val c = target.nativeCanvas; c.save(); c.scale(pixels, pixels); painter.draw(c, frame, driver, now, reduced, height, bottom); c.restore() }
            }
            GeodeFocusRewardControls(driver, clock, autoPlay, reduced, previewTime, revision, scale, pixels, ::action)
        }
    }
}

@Composable
private fun GeodeFocusRewardControls(driver: GeodeFocusRewardDriver, clock: MutableDoubleState, autoPlay: Boolean, reduced: Boolean,
    preview: Double?, revision: Int, scale: Float, pixels: Float, action: (String) -> Unit) {
    val gates by remember(driver, autoPlay, reduced, preview) { derivedStateOf {
        val f = driver.frame(clock.doubleValue, autoPlay, reduced, preview)
        Pair(f.copy.resultAlpha[3] >= .5, f.copy.resultAlpha[3] >= .8)
    } }
    val kept = driver.kept; val collapsed = driver.collapsed
    fun Modifier.frame(x: Float, y: Float, w: Float, h: Float) = this.offset((x * scale).dp, (y * scale).dp).size((w * scale).dp, (h * scale).dp)
    if (collapsed) {
        Box(Modifier.frame(108.5f, 416f, 176f, 48f).semantics { contentDescription = "View stone" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { action("reopen") })
    } else {
        GeodeFocusRewardHeroTarget(driver, clock, autoPlay, reduced, preview, scale, action)
        GeodeFocusRewardCaption(driver, clock, autoPlay, reduced, preview, scale)
        listOf(18f to "Close reward", 318f to "Skip reward").forEach { (x, title) ->
            Box(Modifier.frame(x, 138f, 44f, 44f).semantics { contentDescription = title }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { action("close") })
        }
        if (gates.first) {
            listOf(32f to 626f, 134f to 679f).forEachIndexed { i, (x, y) ->
                Box(Modifier.offset {
                    val offset = driver.frame(clock.doubleValue, autoPlay, reduced, preview).copy.resultY[3]
                    IntOffset((x * pixels).roundToInt(), ((y + offset) * pixels).roundToInt())
                }.size(((if (i == 0) 329 else 125) * scale).dp, (44 * scale).dp)
                    .semantics { contentDescription = if (i == 1) "Replay" else if (kept) "Added to collection" else "Keep stone" }
                    .clickable(enabled = gates.second, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { action(if (i == 0) "keep" else "replay") })
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val eventRevision = revision
}

@Composable
private fun GeodeFocusRewardHeroTarget(driver: GeodeFocusRewardDriver, clock: MutableDoubleState, autoPlay: Boolean,
    reduced: Boolean, preview: Double?, scale: Float, action: (String) -> Unit) {
    val percent by remember(driver, autoPlay, reduced, preview) { derivedStateOf { (driver.frame(clock.doubleValue, autoPlay, reduced, preview).hero.p * 100).toInt() } }
    Box(Modifier.offset((80 * scale).dp, (310 * scale).dp).size((233 * scale).dp, (285 * scale).dp).semantics {
        contentDescription = "Mosslight stone"; stateDescription = if (reduced) "Seven sessions completed" else "$percent percent revealed"
        if (!reduced && preview == null) onClick("Reveal stone") { action("reveal"); true }
    })
}
@Composable
private fun GeodeFocusRewardCaption(driver: GeodeFocusRewardDriver, clock: MutableDoubleState, autoPlay: Boolean,
    reduced: Boolean, preview: Double?, scale: Float) {
    val caption by remember(driver, autoPlay, reduced, preview) { derivedStateOf {
        val f = driver.frame(clock.doubleValue, autoPlay, reduced, preview)
        if (f.copy.restAlpha >= .5) "Stone earned. Hold to reveal your stone."
        else if (f.copy.resultAlpha[0] >= .5) "New stone. Mosslight. Seven focused sessions. A little more room to think." else ""
    } }
    if (caption.isNotEmpty()) Box(Modifier.offset((40 * scale).dp, (174 * scale).dp).size((313 * scale).dp, (110 * scale).dp).semantics { contentDescription = caption })
}

@Composable
private fun rememberGeodeFocusRewardReduced(): Boolean {
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
private fun GeodeFocusRewardSystemBars() {
    val window = LocalActivity.current?.window ?: return; val view = LocalView.current
    DisposableEffect(window, view) {
        val c = WindowCompat.getInsetsController(window, view); val status = c.isAppearanceLightStatusBars; val navigation = c.isAppearanceLightNavigationBars
        c.isAppearanceLightStatusBars = false; c.isAppearanceLightNavigationBars = false; c.show(WindowInsetsCompat.Type.statusBars())
        onDispose { c.isAppearanceLightStatusBars = status; c.isAppearanceLightNavigationBars = navigation }
    }
}
