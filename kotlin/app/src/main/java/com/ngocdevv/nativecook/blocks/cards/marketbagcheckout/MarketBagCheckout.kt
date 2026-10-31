package com.ngocdevv.nativecook.blocks.cards.marketbagcheckout

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.min
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen checkout in dp. Autoplay packs at 1.0 s, holds from 3.0–4.25 s, unpacks at 8.55 s and
 * returns to rest at 9.5 s in a 10-second loop; touch takes over the same pose.
 */
@Composable
fun MarketBagCheckout(autoPlay: Boolean = false) {
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
    val art = remember(context) { MarketBagCheckoutArt(context) }
    val audio =
        remember(context, reducedMotion) {
            if (reducedMotion) null else MarketBagCheckoutAudio(context)
        }
    val driver =
        remember(autoPlay, reducedMotion, audio) {
            MarketBagCheckoutDriver(
                { audio?.play(it) },
                { kind ->
                    view.performHapticFeedback(
                        when (kind) {
                            0 -> HapticFeedbackConstants.CLOCK_TICK
                            1 -> HapticFeedbackConstants.VIRTUAL_KEY
                            else -> HapticFeedbackConstants.CONFIRM
                        }
                    )
                },
            )
        }
    var frameTick by remember(driver) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val frameWake = remember(driver) { Channel<Unit>(Channel.CONFLATED) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity =
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previousLightStatusBars != null)
                controller.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrameNanos = 0L
            try {
                while (true) {
                    if ((!autoPlay || reducedMotion) && !driver.isMoving()) {
                        frameWake.receive()
                        previousFrameNanos = 0L
                    }
                    withFrameNanos { nanos ->
                        val deltaSeconds =
                            min(
                                if (previousFrameNanos == 0L) 0.016
                                else (nanos - previousFrameNanos) / 1e9,
                                0.05,
                            )
                        previousFrameNanos = nanos
                        driver.step(deltaSeconds, autoPlay && !reducedMotion)
                        frameTick++
                    }
                }
            } finally {
                audio?.stop()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = WindowInsets.statusBars.getTop(LocalDensity.current) / density.toDouble()
        val bottom =
            WindowInsets.navigationBars.getBottom(LocalDensity.current) / density.toDouble()
        val geometry =
            MarketBagCheckoutGeometry(
                maxWidth.value.toDouble(),
                maxHeight.value.toDouble(),
                top,
                bottom,
            )
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val frame = frameTick
            drawIntoCanvas {
                art.draw(it.nativeCanvas, geometry, density, driver.pose, driver.holding)
            }
        }
        Box(
            Modifier.offset(24.dp, (top + 32).dp).size(250.dp, 40.dp).semantics {
                heading()
                contentDescription = "Your basket"
            }
        )
        for (itemIndex in 0..4) {
            val item = MarketBagCheckoutMotion.items[itemIndex]
            Box(
                Modifier.offset(16.dp, (geometry.cardY + 12 + itemIndex * 46).dp)
                    .size(geometry.cardWidth.dp, 46.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription =
                            "${item.name}, ${item.meta}, $${"%.2f".format(java.util.Locale.US, item.price)}"
                        onClick("Puts it in the bag, or takes it out") {
                            driver.toggle(itemIndex)
                            frameWake.trySend(Unit)
                            frameTick++
                            true
                        }
                    }
                    .pointerInput(driver, itemIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                driver.toggle(itemIndex)
                                frameWake.trySend(Unit)
                                frameTick++
                            }
                        }
                    }
            )
        }
        Box(
            Modifier.offset(16.dp, geometry.buttonY.dp)
                .size(geometry.cardWidth.dp, 56.dp)
                .semantics {
                    @Suppress("UNUSED_VARIABLE") val currentFrame = frameTick
                    role = Role.Button
                    contentDescription =
                        if (driver.pose.paid >= 0 && driver.pose.reset < 0.5)
                            "Paid $11.40. Tap to start a new basket"
                        else "Hold to pay $11.40"
                    onClick {
                        driver.holdStart()
                        frameWake.trySend(Unit)
                        frameTick++
                        scope.launch {
                            delay(1250)
                            driver.holdEnd()
                            frameWake.trySend(Unit)
                            frameTick++
                        }
                        true
                    }
                }
                .pointerInput(driver) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        driver.holdStart()
                        frameWake.trySend(Unit)
                        frameTick++
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            driver.holdEnd()
                            frameWake.trySend(Unit)
                            frameTick++
                        }
                    }
                }
        )
    }
}
