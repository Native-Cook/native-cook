package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.hypot

@Composable
fun CityEventHero(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = rememberReducedMotion()
    val screenReader = rememberCityEventHeroScreenReader(context)
    val looping = autoPlay && !reducedMotion && !screenReader
    val assets = remember(context) { CityEventHeroAssets(context.applicationContext) }
    val controller = remember(looping) { CityEventHeroController(looping) }
    val scenePainter = remember(assets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            CityEventHeroApi33ScenePainter(assets)
        } else {
            null
        }
    }
    var revision by remember { mutableIntStateOf(0) }
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner, controller) {
        val updateLifecycle = {
            lifecycleActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            controller.setLifecycleActive(lifecycleActive, System.nanoTime())
        }
        updateLifecycle()
        val observer = LifecycleEventObserver { _, _ -> updateLifecycle() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.setLifecycleActive(false, System.nanoTime())
        }
    }

    DisposableEffect(controller, reducedMotion, lifecycleActive) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravity = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var previousTimestamp = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val delta = if (previousTimestamp == 0L) 0f else (event.timestamp - previousTimestamp).coerceAtLeast(0L) / 1_000_000_000f
                previousTimestamp = event.timestamp
                controller.updateGravity(event.values[0] / 9.81f, event.values[1] / 9.81f, delta, reducedMotion)
                revision++
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (lifecycleActive && !reducedMotion && gravity != null) {
            sensorManager?.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(CityEventHeroPage)) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
        val geo = remember(width, height, topInset) { CityEventHeroGeometry(width / CityEventHeroRefWidth, width, height, topInset) }

        LaunchedEffect(controller, looping, reducedMotion, lifecycleActive) {
            if (!lifecycleActive) return@LaunchedEffect
            while (currentCoroutineContext().isActive) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (controller.tick(now, looping, reducedMotion)) revision++
                    if (controller.consumeShowHaptic(now)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                }
            }
        }

        fun performGuest() {
            val now = System.nanoTime()
            controller.takeOver(now)
            controller.tapGuests(now, -1)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            revision++
        }

        fun performLike() {
            val now = System.nanoTime()
            controller.takeOver(now)
            controller.tapLike(now)
            if (controller.liked) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            revision++
        }

        fun performView() {
            val now = System.nanoTime()
            controller.takeOver(now)
            controller.setViewPressed(now, false, commit = true, reduced = reducedMotion)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            revision++
        }

        fun performSky() {
            val now = System.nanoTime()
            controller.takeOver(now)
            controller.toggleEvening(now, reducedMotion)
            revision++
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = "Dubai Jan 2026 event"
                    customActions = listOf(
                        CustomAccessibilityAction("City sky. Run the day into the night.") {
                            performSky()
                            true
                        },
                        CustomAccessibilityAction("Guests: Amira and 17 more. Show guests.") {
                            performGuest()
                            true
                        },
                        CustomAccessibilityAction("Like") {
                            performLike()
                            true
                        },
                        CustomAccessibilityAction("View event. Lights up the tower.") {
                            performView()
                            true
                        },
                    )
                }
                .pointerInput(controller, geo, reducedMotion, density) {
                    val panIntent = with(density) { 10.dp.toPx() }
                    val tapDistance = with(density) { 12.dp.toPx() }
                    val guestHitSlop = with(density) { 6.dp.toPx() }
                    val pixelsPerDp = density.density
                    val card = geo.box(CityEventHeroCard)
                    val scrubX = card.width / pixelsPerDp * CityEventHeroNight.scrubX
                    val scrubY = card.height / pixelsPerDp * CityEventHeroNight.scrubY
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val now = System.nanoTime()
                        controller.beginGesture(now)
                        val target = cityEventHeroTarget(down.position.x, down.position.y, geo, controller)
                        var viewPressed = target == CityEventHeroHit.VIEW
                        if (viewPressed) controller.setViewPressed(now, true, reduced = reducedMotion)
                        revision++
                        var panActivated = false
                        var skyDragActive = false
                        var cancelled = false
                        var maxDistance = 0f
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                        try {
                            while (up == null && !cancelled) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    cancelled = true
                                    break
                                }
                                if (change.isConsumed) {
                                    cancelled = true
                                    break
                                }
                                val totalX = change.position.x - down.position.x
                                val totalY = change.position.y - down.position.y
                                maxDistance = maxOf(maxDistance, hypot(totalX, totalY))
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (!panActivated && maxDistance > panIntent) panActivated = true
                                if (target == CityEventHeroHit.SKY && panActivated && change.pressed) {
                                    if (!skyDragActive) {
                                        controller.startSkyDrag(System.nanoTime(), reducedMotion)
                                        skyDragActive = true
                                    }
                                    controller.scrubSky(
                                        now = System.nanoTime(),
                                        translationX = totalX / pixelsPerDp,
                                        translationY = totalY / pixelsPerDp,
                                        scrubX = scrubX,
                                        scrubY = scrubY,
                                        reduced = reducedMotion,
                                    )
                                    change.consume()
                                    revision++
                                }
                                if (!change.pressed) up = change
                            }
                            val release = System.nanoTime()
                            val finish = up
                            if (skyDragActive) {
                                if (!cancelled) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    controller.finishSkyDrag(
                                        now = release,
                                        velocityX = velocity.x / pixelsPerDp,
                                        velocityY = velocity.y / pixelsPerDp,
                                        scrubX = scrubX,
                                        scrubY = scrubY,
                                        reduced = reducedMotion,
                                    )
                                } else {
                                    controller.cancelSkyDrag(release)
                                }
                                skyDragActive = false
                            }
                            val tapSucceeded = finish != null && !cancelled && !panActivated &&
                                maxDistance <= tapDistance && finish.uptimeMillis - down.uptimeMillis <= 800L
                            if (tapSucceeded && finish != null) {
                                when (target) {
                                    CityEventHeroHit.SKY -> controller.toggleEvening(release, reducedMotion)
                                    CityEventHeroHit.GUESTS -> {
                                        val guest = cityEventHeroGuestAt(
                                            finish.position.x,
                                            finish.position.y,
                                            geo,
                                            controller,
                                            guestHitSlop,
                                        )
                                        val selecting = controller.fanned && guest >= 0 && guest != controller.spotlight
                                        controller.tapGuests(release, guest)
                                        view.performHapticFeedback(if (selecting) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    CityEventHeroHit.LIKE -> {
                                        controller.tapLike(release)
                                        if (controller.liked) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    CityEventHeroHit.VIEW -> {
                                        controller.setViewPressed(release, false, commit = true, reduced = reducedMotion)
                                        viewPressed = false
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    CityEventHeroHit.NONE -> Unit
                                }
                            }
                        } finally {
                            val release = System.nanoTime()
                            if (skyDragActive) controller.cancelSkyDrag(release)
                            if (viewPressed) controller.setViewPressed(release, false, reduced = reducedMotion)
                            controller.endGesture(release)
                            revision++
                        }
                    }
                },
        ) {
            // Reading revision here invalidates only this draw layer; no screen-level frame recomposition.
            if (revision >= 0) {
                drawCityEventHero(
                    assets = assets,
                    geometry = geo,
                    frame = controller.frame,
                    reduced = reducedMotion,
                    scenePainter = scenePainter,
                    tiltX = controller.tiltX,
                    tiltY = controller.tiltY,
                )
            }
        }
    }
}

@Composable
private fun rememberCityEventHeroScreenReader(context: Context): Boolean {
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var enabled by remember(manager) { mutableStateOf(manager.isEnabled && manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration ->
            enabled = touchExploration && manager.isEnabled
        }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose {
            manager.removeTouchExplorationStateChangeListener(listener)
        }
    }
    return enabled
}

private enum class CityEventHeroHit { NONE, SKY, GUESTS, LIKE, VIEW }

private fun cityEventHeroTarget(x: Float, y: Float, geo: CityEventHeroGeometry, controller: CityEventHeroController): CityEventHeroHit {
    if (y in geo.y(1400f)..geo.y(1720f)) return CityEventHeroHit.GUESTS
    if (geo.box(CityEventHeroLike).contains(androidx.compose.ui.geometry.Offset(x, y))) return CityEventHeroHit.LIKE
    if (geo.box(CityEventHeroView).contains(androidx.compose.ui.geometry.Offset(x, y))) return CityEventHeroHit.VIEW
    if (y >= geo.y(345f) && y < geo.y(1400f)) return CityEventHeroHit.SKY
    return CityEventHeroHit.NONE
}

private fun cityEventHeroGuestAt(
    x: Float,
    y: Float,
    geo: CityEventHeroGeometry,
    controller: CityEventHeroController,
    hitSlop: Float,
): Int {
    if (!controller.fanned) return -1
    var selected = -1
    var distance = Float.MAX_VALUE
    CityEventHeroSlots.indices.forEach { slot ->
        val guest = CityEventHeroSlots[slot]
        val fan = CityEventHeroFannedSlots[slot]
        val centerX = geo.x(fan.cx)
        val centerY = geo.y(fan.cy)
        val current = hypot(x - centerX, y - centerY)
        if (current <= geo.x(guest.d / 2f) + hitSlop && current < distance) {
            selected = slot
            distance = current
        }
    }
    return selected
}
