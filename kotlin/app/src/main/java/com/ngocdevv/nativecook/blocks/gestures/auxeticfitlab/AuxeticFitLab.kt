package com.ngocdevv.nativecook.blocks.gestures.auxeticfitlab

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
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

private const val LOOP = AUXETIC_FIT_LAB_LOOP

/** Corner-hinged rigid squares expand together; the finger drives their real angle. */
@Composable
fun AuxeticFitLab(autoPlay: Boolean = false, previewTime: Double? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = remember(context.applicationContext) { AuxeticFitLabResources.load(context.applicationContext) }
    val painter = remember(resources) { AuxeticFitLabPainter(resources) }
    val driver = remember { AuxeticFitLabDriver() }
    val clock = remember { mutableDoubleStateOf(auxeticFitLabNow()) }
    var revision by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    val reduced = rememberAuxeticFitLabReduced()
    val latestAuto = rememberUpdatedState(autoPlay); val latestReduced = rememberUpdatedState(reduced)
    val latestPreview = rememberUpdatedState(previewTime)
    val view = LocalView.current; val density = LocalDensity.current; val lifecycle = LocalLifecycleOwner.current
    AuxeticFitLabSystemBars()
    fun wake(reschedule: Boolean = true) { clock.doubleValue = auxeticFitLabNow(); if (reschedule) revision++ }
    fun tick(angle: Double, now: Double) {
        if (driver.tick(angle, now)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun save() {
        if (driver.save(auxeticFitLabNow(), latestAuto.value, latestReduced.value, latestPreview.value)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); wake()
        }
    }
    fun adjust(direction: Int): Boolean {
        val now = auxeticFitLabNow()
        val changed = driver.adjust(direction, now, latestAuto.value, latestReduced.value, latestPreview.value)
        if (changed) { tick(driver.frame(now, latestAuto.value, latestReduced.value, latestPreview.value).angle, now); wake() }
        return changed
    }
    DisposableEffect(lifecycle, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { driver.clear(auxeticFitLabNow()); visible = false }
            else if (event == Lifecycle.Event.ON_START) { driver.epoch = auxeticFitLabNow(); visible = true }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); driver.clear(auxeticFitLabNow()) }
    }
    LaunchedEffect(reduced) { driver.clear(auxeticFitLabNow()); driver.reducedAngle = 42.0; clock.doubleValue = driver.epoch; revision++ }
    LaunchedEffect(autoPlay) { if (autoPlay && driver.session == null && driver.event == null) { driver.epoch = auxeticFitLabNow(); clock.doubleValue = driver.epoch } }
    LaunchedEffect(autoPlay, reduced, previewTime, revision, visible) {
        while (isActive && visible) {
            val now = auxeticFitLabNow()
            if (driver.complete(now)) revision++
            clock.doubleValue = now
            // A held capture is static until input changes; never run a detached clock.
            val frames = !reduced && previewTime == null && driver.session == null && (autoPlay || driver.event != null)
            if (frames) withFrameNanos { clock.doubleValue = it / 1_000_000_000.0 }
            else { val deadline = driver.nextDeadline() ?: break; delay(max(1L, ((deadline - now) * 1000).toLong())) }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = maxWidth.value / 393f; val pixels = scale * density.density
        val height = max(852f, maxHeight.value / scale)
        val bottom = max(0f, WindowInsets.safeDrawing.getBottom(density) / pixels - 34f)
        Box(Modifier.fillMaxSize().pointerInput(pixels, autoPlay, reduced, previewTime, visible) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!visible || latestReduced.value || latestPreview.value != null) { waitForUpOrCancellation(); return@awaitEachGesture }
                val now = auxeticFitLabNow()
                val hit = AuxeticFitLabMath.handles(driver.displayed(now, latestAuto.value, latestReduced.value, latestPreview.value))
                val x = (down.position.x / pixels).toDouble(); val y = (down.position.y / pixels).toDouble()
                var active: AuxeticFitLabOwner? = when {
                    hit.leftHit.contains(x, y) -> AuxeticFitLabOwner.Left
                    hit.rightHit.contains(x, y) -> AuxeticFitLabOwner.Right
                    else -> null
                }
                if (active == null && !hit.pinchHit.contains(x, y)) { waitForUpOrCancellation(); return@awaitEachGesture }
                var token: Int? = null; var normalEnd = false
                val tracker = VelocityTracker()
                var pair: Pair<PointerId, PointerId>? = null
                if (active != null) {
                    if (!driver.begin(active, x, now, latestAuto.value, latestReduced.value, latestPreview.value)) { waitForUpOrCancellation(); return@awaitEachGesture }
                    token = driver.session!!.generation
                    tracker.addPosition(down.uptimeMillis, down.position)
                    down.consume(); wake()
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val gesture = active
                        if (gesture == null) {
                            val first = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!first.pressed) break
                            val second = event.changes.firstOrNull { it.id != down.id && it.pressed && hit.pinchHit.contains((it.position.x / pixels).toDouble(), (it.position.y / pixels).toDouble()) }
                            if (second == null) continue
                            val distance = (first.position - second.position).getDistance().toDouble() / pixels
                            if (distance <= 1) continue
                            val stamp = auxeticFitLabNow()
                            if (!driver.begin(AuxeticFitLabOwner.Pinch, distance, stamp, latestAuto.value, latestReduced.value, latestPreview.value)) break
                            active = AuxeticFitLabOwner.Pinch; pair = first.id to second.id; token = driver.session!!.generation
                            tracker.addPosition(first.uptimeMillis, Offset(distance.toFloat(), 0f))
                            first.consume(); second.consume(); wake()
                            continue
                        }
                        if (gesture == AuxeticFitLabOwner.Pinch) {
                            val ids = pair ?: break
                            val a = event.changes.firstOrNull { it.id == ids.first } ?: break
                            val b = event.changes.firstOrNull { it.id == ids.second } ?: break
                            if (a.isConsumed || b.isConsumed) break
                            val coordinate = (a.position - b.position).getDistance().toDouble() / pixels
                            tracker.addPosition(a.uptimeMillis, Offset(coordinate.toFloat(), 0f))
                            val stamp = auxeticFitLabNow()
                            if (driver.changed(gesture, coordinate, tracker.calculateVelocity().x.toDouble(), stamp)) {
                                tick(driver.session!!.input.frame.angle, stamp); wake(false)
                            }
                            a.consume(); b.consume()
                            if (!a.pressed || !b.pressed) { normalEnd = true; break }
                        } else {
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val stamp = auxeticFitLabNow()
                            if (driver.changed(gesture, (change.position.x / pixels).toDouble(), (tracker.calculateVelocity().x / pixels).toDouble(), stamp)) {
                                tick(driver.session!!.input.frame.angle, stamp); wake(false)
                            }
                            change.consume()
                            if (!change.pressed) { normalEnd = true; break }
                        }
                    }
                } finally {
                    // A stopped lifecycle or changed reduced setting invalidates this owner.
                    if (token != null && driver.session?.generation == token) active?.let {
                        if (driver.finish(it, auxeticFitLabNow(), !normalEnd)) wake()
                    }
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val now = clock.doubleValue; val frame = driver.frame(now, autoPlay, reduced, previewTime)
                driver.rendered = frame; driver.renderedAt = now
                drawIntoCanvas { target -> val c = target.nativeCanvas; c.save(); c.scale(pixels, pixels)
                    painter.draw(c, frame, driver.saved, height, bottom); c.restore() }
            }
            AuxeticFitLabControls(driver, clock, autoPlay, reduced, previewTime, revision, scale, pixels, bottom, ::save, ::adjust)
        }
    }
}

@Composable
private fun AuxeticFitLabControls(driver: AuxeticFitLabDriver, clock: MutableDoubleState, autoPlay: Boolean, reduced: Boolean,
    preview: Double?, revision: Int, scale: Float, pixels: Float, bottom: Float, save: () -> Unit, adjust: (Int) -> Boolean) {
    fun Modifier.frame(x: Float, y: Float, w: Float, h: Float) = this.offset((x * scale).dp, (y * scale).dp).size((w * scale).dp, (h * scale).dp)
    Box(Modifier.frame(24f, 76f, 345f, 174f).semantics {
        contentDescription = "HINGE / MATERIAL LAB. Room to move. A rigid pattern with a softer fit. SAMPLE 01. Rotating squares."
    })
    Box(Modifier.frame(24f, 687f, 345f, 20f).semantics { contentDescription = "Pull a handle. Both directions grow." })
    val saved = driver.saved
    Box(Modifier.frame(24f, 755f - bottom, 345f, 54f).semantics { contentDescription = if (saved) "Fit saved" else "Save fit" }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = save))
    AuxeticFitLabLatticeTarget(driver, clock, autoPlay, reduced, preview, pixels, adjust)
    if (!reduced) for (side in listOf(AuxeticFitLabSide.Left, AuxeticFitLabSide.Right)) {
        Box(Modifier.offset {
            val handles = AuxeticFitLabMath.handles(driver.frame(clock.doubleValue, autoPlay, reduced, preview))
            val rect = if (side == AuxeticFitLabSide.Left) handles.leftHit else handles.rightHit
            IntOffset((rect.x * pixels).roundToInt(), (rect.y * pixels).roundToInt())
        }.size((44 * scale).dp, (60 * scale).dp).semantics {
            contentDescription = if (side == AuxeticFitLabSide.Left) "Left expansion handle" else "Right expansion handle"
            stateDescription = "Drag horizontally to expand both dimensions."
        })
    }
    @Suppress("UNUSED_VARIABLE") val eventRevision = revision
}

@Composable
private fun AuxeticFitLabLatticeTarget(driver: AuxeticFitLabDriver, clock: MutableDoubleState, autoPlay: Boolean,
    reduced: Boolean, preview: Double?, pixels: Float, adjust: (Int) -> Boolean) {
    val reading by remember(driver, autoPlay, reduced, preview) { derivedStateOf {
        val frame = driver.frame(clock.doubleValue, autoPlay, reduced, preview); val m = AuxeticFitLabMath.metrics(frame.angle)
        Triple(AuxeticFitLabPainter.number(m.extensionPercent), AuxeticFitLabPainter.number(m.openAreaPercent), frame.angle.toFloat())
    } }
    Box(Modifier.offset {
        val rect = AuxeticFitLabMath.handles(driver.frame(clock.doubleValue, autoPlay, reduced, preview)).pinchHit
        IntOffset((rect.x * pixels).roundToInt(), (rect.y * pixels).roundToInt())
    }.layout { measurable, _ ->
        val rect = AuxeticFitLabMath.handles(driver.frame(clock.doubleValue, autoPlay, reduced, preview)).pinchHit
        val w = (rect.width * pixels).roundToInt(); val h = (rect.height * pixels).roundToInt()
        val child = measurable.measure(Constraints.fixed(w, h)); layout(w, h) { child.place(0, 0) }
    }.semantics {
        contentDescription = "Auxetic lattice"
        stateDescription = "Extension ${reading.first} percent, open area ${reading.second} percent"
        progressBarRangeInfo = ProgressBarRangeInfo(reading.third.coerceIn(4f, 42f), 4f..42f)
        setProgress { value -> if (abs(value - reading.third) < 1e-5f) false else adjust(if (value > reading.third) 1 else -1) }
        customActions = listOf(CustomAccessibilityAction("Increase sample angle") { adjust(1) },
            CustomAccessibilityAction("Decrease sample angle") { adjust(-1) })
    })
}

@Composable
private fun rememberAuxeticFitLabReduced(): Boolean {
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
private fun AuxeticFitLabSystemBars() {
    val window = LocalActivity.current?.window ?: return; val view = LocalView.current
    DisposableEffect(window, view) {
        val c = WindowCompat.getInsetsController(window, view); val status = c.isAppearanceLightStatusBars; val navigation = c.isAppearanceLightNavigationBars
        c.isAppearanceLightStatusBars = true; c.isAppearanceLightNavigationBars = true; c.show(WindowInsetsCompat.Type.statusBars())
        onDispose { c.isAppearanceLightStatusBars = status; c.isAppearanceLightNavigationBars = navigation }
    }
}
