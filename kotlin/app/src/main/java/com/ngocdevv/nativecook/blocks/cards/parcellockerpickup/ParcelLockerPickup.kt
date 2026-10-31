package com.ngocdevv.nativecook.blocks.cards.parcellockerpickup

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

/**
 * Full-screen locker pickup with a perspective hinge, parcel flight and receipt crossfade. Geometry
 * and gesture positions are dp; the native Canvas is scaled once by display density. The driver
 * owns per-frame pose changes; only receipt accessibility state returns to composition.
 */
@Composable
fun ParcelLockerPickup(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reducedMotion =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    val top = WindowInsets.statusBars.getTop(LocalDensity.current) / density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(LocalDensity.current) / density.toDouble()
    val art = remember(context) { ParcelLockerPickupArt(context) }
    val audio =
        remember(context, reducedMotion) {
            if (reducedMotion) null else ParcelLockerPickupAudio(context)
        }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val driver =
        remember(autoPlay, reducedMotion, audio) {
            ParcelLockerPickupDriver(
                { audio?.play(it) },
                { kind ->
                    if (Build.VERSION.SDK_INT >= 29)
                        vibrator?.vibrate(
                            VibrationEffect.createPredefined(
                                if (kind == 0) VibrationEffect.EFFECT_TICK
                                else if (kind == 2) VibrationEffect.EFFECT_HEAVY_CLICK
                                else VibrationEffect.EFFECT_CLICK
                            )
                        )
                    else
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(
                                if (kind == 2) 25 else 12,
                                if (kind == 2) 100 else 50,
                            )
                        )
                },
            )
        }
    val tick = remember { mutableIntStateOf(0) }
    // Only the receipt accessibility phase crosses back to composition.
    var receipt by remember { mutableStateOf(false) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity =
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previousLightStatusBars != null)
                controller?.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
    LaunchedEffect(driver, lifecycle, previewTime) {
        if (previewTime != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrameNanos = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val deltaTime =
                        min(
                            if (previousFrameNanos == 0L) 0.016
                            else (nanos - previousFrameNanos) / 1e9,
                            0.05,
                        )
                    previousFrameNanos = nanos
                    driver.step(deltaTime, autoPlay, reducedMotion)
                    tick.intValue++
                    val nextReceipt = driver.pose.sheet > 0.5
                    if (receipt != nextReceipt) receipt = nextReceipt
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { sceneSize = it }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(driver, reducedMotion, previewTime, top, bottom) {
                if (previewTime != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val geometry =
                        ParcelLockerPickupGeometry(
                            size.width / density.toDouble(),
                            size.height / density.toDouble(),
                            top,
                            bottom,
                        )
                    val startX = down.position.x / density.toDouble()
                    val startY = down.position.y / density.toDouble()
                    val sheetHeight = 248 + 52 * driver.pose.sheet
                    val sheetY = geometry.sheetBottom - sheetHeight
                    val door =
                        startX >= geometry.doorX - 30 &&
                            startX <= geometry.doorX + geometry.doorWidth + 30 &&
                            startY >= geometry.doorY - 20 &&
                            startY <= geometry.doorY + geometry.doorHeight + 20
                    // RN's two independent faces retain their own button targets during the
                    // crossfade.
                    val receiptFace = driver.pose.sheet > 0.5
                    val buttonY =
                        sheetY + if (receiptFace) art.receiptButtonY else art.pickupButtonY
                    val target =
                        if (door) 1
                        else if (
                            startX >= 38 &&
                                startX <= geometry.width - 38 &&
                                startY >= buttonY &&
                                startY <= buttonY + if (receiptFace) 50 else 52
                        ) {
                            if (receiptFace) 3 else 2
                        } else 0
                    if (target == 1) driver.interrupt()
                    var lastX = startX
                    var endX = startX
                    var endY = startY
                    var didPan = false
                    var elapsed = 0L
                    var released = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            elapsed = change.uptimeMillis - down.uptimeMillis
                            released = !change.pressed
                            endX = change.position.x / density.toDouble()
                            endY = change.position.y / density.toDouble()
                            if (target == 1 && change.pressed) {
                                driver.touchAt = driver.elapsedTime
                                val distance = hypot(endX - startX, endY - startY)
                                if (!didPan && distance >= 4) {
                                    didPan = true
                                    if (distance >= 6) {
                                        driver.interrupt()
                                        driver.take()
                                    } else if (
                                        driver.mode == 1 &&
                                            driver.elapsedTime - driver.openAt > 0.72
                                    ) {
                                        driver.dragging = true
                                        driver.dragAngle = driver.pose.angle
                                    }
                                }
                                if (driver.dragging)
                                    driver.dragAngle =
                                        (driver.dragAngle - (endX - lastX) * 0.9).coerceIn(
                                            0.0,
                                            118.0,
                                        )
                            }
                            lastX = endX
                            if (target != 0) change.consume()
                        } while (change.pressed)
                        val tap = hypot(endX - startX, endY - startY) < 6 && elapsed <= 500L
                        if (target == 1) {
                            if (released && !didPan && tap) {
                                driver.interrupt()
                                driver.take()
                            }
                        } else if (released && (target == 2 || target == 3)) {
                            val retained =
                                endX >= 18 &&
                                    endX <= geometry.width - 18 &&
                                    endY >= buttonY - 20 &&
                                    endY <= buttonY + (if (receiptFace) 50 else 52) + 30
                            if (retained) {
                                if (target == 2) driver.open() else driver.done()
                            }
                        }
                    } finally {
                        if (target == 1) {
                            driver.dragging = false
                            driver.touchAt = driver.elapsedTime
                        }
                        tick.intValue++
                    }
                }
            }
        ) {
            tick.intValue
            val geometry =
                ParcelLockerPickupGeometry(
                    size.width / density.toDouble(),
                    size.height / density.toDouble(),
                    top,
                    bottom,
                )
            val pose = previewTime?.let { ParcelLockerPickupMotion.script(it) } ?: driver.pose
            drawIntoCanvas {
                val native = it.nativeCanvas
                native.save()
                native.scale(density, density)
                art.draw(
                    native,
                    geometry,
                    pose,
                    previewTime ?: if (driver.automatic) driver.loopTime else driver.elapsedTime,
                )
                native.restore()
            }
        }
        val geometry =
            ParcelLockerPickupGeometry(
                sceneSize.width / density.toDouble(),
                sceneSize.height / density.toDouble(),
                top,
                bottom,
            )
        Box(
            Modifier.offset(x = (geometry.doorX - 30).dp, y = (geometry.doorY - 20).dp)
                .size((geometry.doorWidth + 60).dp, (geometry.doorHeight + 40).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Locker 14B"
                    stateDescription = "Once it is open, takes the parcel out"
                    onClick {
                        driver.interrupt()
                        driver.take()
                        true
                    }
                }
        )
        Box(
            Modifier.offset(
                    x = 38.dp,
                    y =
                        (geometry.sheetBottom -
                                (if (receipt) 300 - art.receiptButtonY
                                else 248 - art.pickupButtonY))
                            .dp,
                )
                .size((geometry.width - 76).dp, 52.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (receipt) "Done" else "Open locker 14B"
                    onClick {
                        if (receipt) driver.done() else driver.open()
                        true
                    }
                }
        )
        Box(
            Modifier.offset(x = 38.dp, y = (geometry.sheetBottom - if (receipt) 278 else 228).dp)
                .size((geometry.width - 76).dp, 80.dp)
                .semantics {
                    contentDescription =
                        if (receipt) "Picked up at 6:42 PM. Locker 14B closed."
                        else "Pickup code 4 8 1 7"
                }
        )
    }
}
