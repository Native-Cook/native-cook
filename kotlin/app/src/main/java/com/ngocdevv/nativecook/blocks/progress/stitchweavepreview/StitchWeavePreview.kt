package com.ngocdevv.nativecook.blocks.progress.stitchweavepreview

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

private const val LOOP = STITCH_WEAVE_PREVIEW_LOOP
private fun stitchWeaveNow() = System.nanoTime() / 1_000_000_000.0

internal class StitchWeavePreviewDriver {
    var epoch = stitchWeaveNow()
    var dragging = false
    var captured = StitchWeavePreviewPose.Rest
    var finger = captured
    var action: StitchWeavePreviewAction? = null
    var pattern = 0
    var yarn = 0
    var materialBase = StitchWeavePreviewMath.solidMaterial(0, 0)
    var materialStarted: Double? = null
    var materialRows = 3.0
    var saved = false
    var saveStarted: Double? = null
    var lastDetent = 0
    var lastHaptic = -100.0
    var displayedPose = StitchWeavePreviewPose.Rest
    var displayedAt = -100.0

    fun pose(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): StitchWeavePreviewPose = when {
        reduced -> StitchWeavePreviewPose.Finished
        preview != null -> StitchWeavePreviewMath.authored(max(0.0, preview))
        dragging -> finger
        action != null -> action!!.pose(now)
        autoPlay -> StitchWeavePreviewMath.authored(max(0.0, now - epoch) % LOOP)
        else -> StitchWeavePreviewPose.Rest
    }
    fun visible(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?) =
        if (now - displayedAt < .2) displayedPose else pose(now, autoPlay, reduced, preview)
    fun reset(now: Double, reduced: Boolean, preview: Double?): Double =
        if (reduced || preview != null || dragging) 0.0 else action?.let { StitchWeavePreviewMath.io((now - it.stamp - 2) / .65) } ?: 0.0
    fun freezeMaterial(now: Double, reduced: Boolean, preview: Double?) {
        val age = materialStarted?.let { max(0.0, now - it) } ?: -1.0
        val reset = reset(now, reduced, preview)
        repeat(48) { row ->
            val wave = StitchWeavePreviewMath.materialMix(row, age, materialRows, reduced)
            repeat(6) { channel ->
                val target = if (channel == pattern || channel == yarn + 3) 1.0 else 0.0
                val value = materialBase[row * 6 + channel] * (1 - wave) + target * wave
                materialBase[row * 6 + channel] = (value * (1 - reset) + if (channel == 0 || channel == 3) reset else 0.0).toFloat()
            }
        }
        materialStarted = null
    }
    fun begin(now: Double, from: StitchWeavePreviewPose, target: Double, velocity: Double, kick: Double, saving: Boolean) {
        if (!saving) { saved = false; saveStarted = null }
        action = StitchWeavePreviewAction(now, from, target, velocity, kick, saving); dragging = false
    }
    fun finish(now: Double, velocity: Double, cancelled: Boolean) {
        val m = StitchWeavePreviewMath
        val projected = m.clamp(finger.p + velocity * .12)
        val snapped = m.clamp((floor(3 + 45 * projected + .5) - 3) / 45)
        begin(now, finger, snapped, if (cancelled) 0.0 else velocity,
            if (cancelled) 0.0 else (velocity * 4.5).coerceIn(-14.0, 14.0), false)
    }
    fun complete(now: Double, reduced: Boolean): Boolean {
        var changed = false
        action?.let { a -> if (!dragging && now - a.stamp >= 2.65) {
            epoch = a.stamp + 2.65; action = null; pattern = 0; yarn = 0
            materialBase = StitchWeavePreviewMath.solidMaterial(0, 0); materialStarted = null
            saved = false; saveStarted = null; changed = true
        } }
        materialStarted?.let { if (now - it >= if (reduced) .18 else .38) {
            materialBase = StitchWeavePreviewMath.solidMaterial(pattern, yarn); materialStarted = null; changed = true
        } }
        saveStarted?.let { if (now - it >= .48 && !dragging) { saved = true; saveStarted = null; changed = true } }
        return changed
    }
    fun nextDeadline(reduced: Boolean): Double? = listOfNotNull(
        action?.stamp?.plus(2.65), materialStarted?.plus(if (reduced) .18 else .38), saveStarted?.plus(.48)).minOrNull()
}

/** Feed yarn forms interlocking courses; motif and fiber color stay in the knitted material. */
@Composable
fun StitchWeavePreview(autoPlay: Boolean = false, previewTime: Double? = null, modifier: Modifier = Modifier) {
    val resources = remember { StitchWeavePreviewResources.shared }
    val driver = remember { StitchWeavePreviewDriver() }
    val painter = remember(resources) { StitchWeavePreviewPainter(resources) }
    val reduced = rememberStitchWeavePreviewReduced()
    val clock = remember { mutableDoubleStateOf(stitchWeaveNow()) }
    var revision by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    val latestAuto = rememberUpdatedState(autoPlay)
    val latestReduced = rememberUpdatedState(reduced)
    val latestPreview = rememberUpdatedState(previewTime)
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    StitchWeavePreviewSystemBars()

    fun redraw() { clock.doubleValue = stitchWeaveNow(); revision++ }
    fun choose(pattern: Int, yarn: Int) {
        val now = stitchWeaveNow(); val p = driver.visible(now, latestAuto.value, latestReduced.value, latestPreview.value)
        driver.freezeMaterial(now, latestReduced.value, latestPreview.value)
        driver.pattern = pattern; driver.yarn = yarn; driver.materialRows = 3 + 45 * p.p
        driver.materialStarted = now; driver.saved = false; driver.saveStarted = null
        if (!latestReduced.value) driver.begin(now, p, p.p, 0.0, 0.0, false)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); redraw()
    }
    fun save() {
        val now = stitchWeaveNow(); driver.freezeMaterial(now, latestReduced.value, latestPreview.value)
        if (latestReduced.value) driver.saved = true else {
            val p = driver.visible(now, latestAuto.value, latestReduced.value, latestPreview.value)
            driver.begin(now, p, 1.0, 0.0, 0.0, true); driver.saveStarted = now
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); redraw()
    }
    DisposableEffect(owner, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                driver.freezeMaterial(stitchWeaveNow(), latestReduced.value, latestPreview.value)
                driver.dragging = false; driver.action = null; driver.materialStarted = null; driver.saveStarted = null
                visible = false
            } else if (event == Lifecycle.Event.ON_START) { driver.epoch = stitchWeaveNow(); visible = true }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            driver.dragging = false; driver.action = null; driver.materialStarted = null; driver.saveStarted = null
            painter.close()
        }
    }
    LaunchedEffect(reduced) {
        driver.dragging = false; driver.action = null; driver.materialStarted = null; driver.saveStarted = null
        driver.materialBase = StitchWeavePreviewMath.solidMaterial(driver.pattern, driver.yarn)
        driver.epoch = stitchWeaveNow(); clock.doubleValue = driver.epoch; revision++
    }
    LaunchedEffect(autoPlay) { if (autoPlay && !driver.dragging && driver.action == null) { driver.epoch = stitchWeaveNow(); clock.doubleValue = driver.epoch } }
    LaunchedEffect(autoPlay, reduced, previewTime, revision, visible) {
        while (isActive && visible) {
            val now = stitchWeaveNow()
            if (driver.complete(now, reduced)) revision++
            clock.doubleValue = now
            val frames = driver.materialStarted != null || (!reduced && previewTime == null && (autoPlay || driver.dragging || driver.action != null))
            if (frames) withFrameNanos { clock.doubleValue = it / 1_000_000_000.0 }
            else {
                val next = driver.nextDeadline(reduced) ?: break
                delay(max(1L, ((next - now) * 1000).toLong()))
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = maxWidth.value / 393f
        val pixelScale = scale * density.density
        val logicalHeight = max(852f, maxHeight.value / scale)
        val bottom = max(0f, WindowInsets.safeDrawing.getBottom(density) / pixelScale - 34f)
        val stateRevision = revision
        // Capture event-driven primitive values so cached semantic lambdas invalidate with controls.
        val controlPattern = driver.pattern
        val controlYarn = driver.yarn
        val controlSaved = driver.saved
        Box(Modifier.fillMaxSize().pointerInput(pixelScale, autoPlay, reduced, previewTime) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val now = stitchWeaveNow()
                val current = driver.visible(now, latestAuto.value, latestReduced.value, latestPreview.value)
                val center = resources.projector(current).project(.5, 1 - (3 + 45 * current.p) / 48).y.coerceIn(220.0, 559.0)
                val x = down.position.x / pixelScale; val y = down.position.y / pixelScale
                if (latestReduced.value || latestPreview.value != null || abs(x - 196.5) > 174.5 || abs(y - center) > 36) {
                    waitForUpOrCancellation(); return@awaitEachGesture
                }
                driver.captured = current; driver.finger = current
                driver.freezeMaterial(now, latestReduced.value, latestPreview.value)
                driver.dragging = true; driver.action = null; driver.saved = false; driver.saveStarted = null
                driver.lastDetent = ((3 + 45 * current.p) / 6).toInt()
                val origin = down.position; val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                down.consume(); clock.doubleValue = now; revision++
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val dy = (change.position.y - origin.y) / pixelScale
                        val dx = (change.position.x - origin.x) / pixelScale
                        val raw = driver.captured.p - dy / 290
                        val p = StitchWeavePreviewMath.clamp(raw)
                        val base = driver.captured.moved(p)
                        val over = (raw - p) * 290
                        val twist = (dx / 18).toDouble().coerceIn(-6.0, 6.0)
                        driver.finger = base.copy(hem = base.hem - (over * .45 / (1 + abs(over) / 75)).coerceIn(-35.0, 35.0),
                            rotation = base.rotation.copy(y = base.rotation.y + twist),
                            lagRotation = base.lagRotation.copy(y = base.lagRotation.y + twist))
                        val stamp = stitchWeaveNow(); val detent = ((3 + 45 * p) / 6).toInt()
                        if (detent != driver.lastDetent && stamp - driver.lastHaptic >= .125) {
                            driver.lastDetent = detent; driver.lastHaptic = stamp
                            view.performHapticFeedback(if (p >= 1) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
                        }
                        clock.doubleValue = stamp; change.consume()
                        if (!change.pressed) { released = true; break }
                    }
                } finally {
                    if (driver.dragging && !latestReduced.value && latestPreview.value == null) {
                        val vy = (velocity.calculateVelocity().y / pixelScale).toDouble().coerceIn(-900.0, 900.0)
                        driver.finish(stitchWeaveNow(), -vy / 290, !released)
                        clock.doubleValue = stitchWeaveNow(); revision++
                    } else driver.dragging = false
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val now = clock.doubleValue
                val pose = driver.pose(now, autoPlay, reduced, previewTime)
                driver.displayedPose = pose; driver.displayedAt = now
                drawIntoCanvas { c ->
                    val canvas = c.nativeCanvas
                    canvas.save(); canvas.scale(pixelScale, pixelScale)
                    painter.draw(canvas, pose, driver, now, reduced, previewTime, logicalHeight, bottom)
                    canvas.restore()
                }
            }
            Box(Modifier.offset((24 * scale).dp, (76 * scale).dp).size((345 * scale).dp, (93 * scale).dp)
                .semantics { contentDescription = "THREAD / STUDIO. Make something soft. A small pattern, one row at a time." })
            StitchWeavePreviewProgressTarget(driver, resources, clock, autoPlay, reduced, previewTime, scale, pixelScale) { delta ->
                if (!reduced && previewTime == null) {
                    val now = stitchWeaveNow(); val current = driver.visible(now, autoPlay, reduced, previewTime)
                    driver.freezeMaterial(now, reduced, previewTime)
                    driver.begin(now, current, StitchWeavePreviewMath.clamp(current.p + delta), 0.0, 0.0, false)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); redraw()
                }
            }
            repeat(3) { i ->
                val patternNames = arrayOf("Sprig", "Ripple", "Plain")
                Box(Modifier.offset(((24 + 112 * i) * scale).dp, (662.5f * scale).dp).size((104 * scale).dp, (44 * scale).dp)
                    .semantics { contentDescription = patternNames[i]; selected = controlPattern == i }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { choose(i, driver.yarn) })
                val yarnNames = arrayOf("Cream yarn", "Moss yarn", "Slate yarn")
                Box(Modifier.offset(((236 + 44 * i) * scale).dp, ((711 - bottom) * scale).dp).size((44 * scale).dp)
                    .semantics { contentDescription = yarnNames[i]; selected = controlYarn == i }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { choose(driver.pattern, i) })
            }
            Box(Modifier.offset((24 * scale).dp, ((755 - bottom) * scale).dp).size((345 * scale).dp, (54 * scale).dp)
                .semantics { contentDescription = if (controlSaved) "Pattern saved" else "Save pattern" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = ::save))
        }
        // Event/deadline revisions update semantic controls; frame clock is read only in draw/placement.
        @Suppress("UNUSED_VARIABLE") val keepSemanticRevision = stateRevision
    }
}

@Composable
private fun StitchWeavePreviewProgressTarget(driver: StitchWeavePreviewDriver, resources: StitchWeavePreviewResources,
    clock: MutableDoubleState, autoPlay: Boolean, reduced: Boolean, preview: Double?, scale: Float, pixelScale: Float,
    adjust: (Double) -> Unit) {
    val percent by remember(driver, autoPlay, reduced, preview) { derivedStateOf {
        floor((3 + 45 * driver.pose(clock.doubleValue, autoPlay, reduced, preview).p) / 48 * 100).toInt()
    } }
    Box(Modifier.offset {
        val pose = driver.pose(clock.doubleValue, autoPlay, reduced, preview)
        val center = resources.projector(pose).project(.5, 1 - (3 + 45 * pose.p) / 48).y.coerceIn(220.0, 559.0)
        IntOffset((22 * pixelScale).roundToInt(), ((center - 36) * pixelScale).roundToInt())
    }.size((349 * scale).dp, (72 * scale).dp).semantics {
        contentDescription = if (reduced) "Pattern preview" else "Knit progress"
        stateDescription = percent.toString() + " percent"
        if (!reduced && preview == null) {
            customActions = listOf(CustomAccessibilityAction("Knit five rows") { adjust(5.0 / 45); true },
                CustomAccessibilityAction("Unknit five rows") { adjust(-5.0 / 45); true })
        }
    })
}

@Composable
private fun rememberStitchWeavePreviewReduced(): Boolean {
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
private fun StitchWeavePreviewSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars; val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false; controller.isAppearanceLightNavigationBars = false
        controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.isAppearanceLightStatusBars = status; controller.isAppearanceLightNavigationBars = navigation }
    }
}
