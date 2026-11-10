package com.ngocdevv.nativecook.blocks.cards.eyewearlenstint

import android.app.Activity
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlin.math.hypot

@Composable
fun EyewearLensTint(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val driver = remember(auto, reduced) { EyewearLensTintDriver(auto, reduced) }
    val art = remember(context) { EyewearLensTintArt(context) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val tick = remember { mutableIntStateOf(0) }

    val safeTop = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val safeBottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()

    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }


    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val l = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            EyewearLensTintLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop, safeBottom)
        }
        val px = density.density

        LaunchedEffect(driver, lifecycle, l) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous = 0L
                while (true) {
                    if (!driver.running) {
                        wake.receive()
                        previous = 0L
                    }
                    withFrameNanos { nanos ->
                        val delta = if (previous == 0L) 0.016 else ((nanos - previous) / 1e9).coerceIn(0.0, 0.5)
                        previous = nanos
                        driver.step(delta, l)
                        tick.intValue += 1
                    }
                }
            }
        }

        fun haptic(kind: Int) {
            view.performHapticFeedback(kind)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription =
                        "Round titanium sunglasses. Lenses tinted ${EyewearLensTintArt.names[if (driver.frame.pour > 0.5) driver.frame.to else driver.frame.from]}."
                    stateDescription = "Drag a tint from the right onto a lens."
                    customActions = listOf(
                        CustomAccessibilityAction("Increment") {
                            driver.chooseByAccessibility(1)
                            haptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            wake.trySend(Unit)
                            true
                        },
                        CustomAccessibilityAction("Decrement") {
                            driver.chooseByAccessibility(-1)
                            haptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            wake.trySend(Unit)
                            true
                        },
                    )
                }
                .pointerInput(driver, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x.toDouble() / px.toDouble()
                        val startY = down.position.y.toDouble() / px.toDouble()
                        if (startY < l.y(140.0) || startY > l.cardTop - 60.0) {
                            return@awaitEachGesture
                        }

                        driver.beginDrag()
                        var grabbed = -2
                        for (i in 0..3) {
                            if (hypot(startX - l.dropX, startY - l.dropY(i)) < 26.0) grabbed = i
                        }
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var currentX = startX
                        var currentY = startY
                        if (grabbed >= 0) {
                            driver.setCarry(grabbed, currentX, currentY, begin = true)
                            haptic(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        wake.trySend(Unit)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) { change.consume(); break }
                            currentX = change.position.x.toDouble() / px.toDouble()
                            currentY = change.position.y.toDouble() / px.toDouble()
                            if (grabbed >= 0) {
                                driver.setCarry(grabbed, currentX, currentY - 28.0)
                            } else {
                                driver.setGlare((currentX - startX) / 140.0)
                            }
                            change.consume()
                            wake.trySend(Unit)
                        }

                        val velocity = velocityTracker.calculateVelocity()
                        val velocityX = velocity.x.toDouble() / px.toDouble()
                        val velocityY = velocity.y.toDouble() / px.toDouble()
                        driver.endDrag()
                        if (grabbed < 0) {
                            driver.springGlare(velocityX)
                        } else {
                            val carryX = driver.frame.carryX
                            val carryY = driver.frame.carryY
                            var target: Int? = null
                            if (hypot(carryX - l.lensAX, carryY - l.lensAY) < l.lensR * 1.25) target = 0
                            if (hypot(carryX - l.lensBX, carryY - l.lensBY) < l.lensR * 1.25) target = 1
                            driver.releaseCarry(
                                grabbed,
                                target,
                                carryX,
                                carryY,
                                l.dropX,
                                l.dropY(grabbed),
                                velocityX,
                                velocityY,
                            )
                            if (target != null) haptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        wake.trySend(Unit)
                    }
                },
        ) {
            tick.intValue
            drawIntoCanvas { canvas ->
                art.draw(canvas.nativeCanvas, l, driver.frame, density.density)
            }
        }

        // Transparent semantic targets preserve the same accessible controls as the RN version.
        Box(
            Modifier
                .offset((32.0 * l.sx - 22.0).dp, (l.y(90.0) - 22.0).dp)
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Menu"
                    onClick { true }
                },
        )
        Box(
            Modifier
                .offset((372.0 * l.sx - 22.0).dp, (l.y(90.0) - 22.0).dp)
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Search"
                    onClick { true }
                },
        )
        Box(
            Modifier
                .offset((l.bagX).dp, l.y(748.0).dp)
                .size(l.bagW.dp, l.bagH.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Add to bag"
                    onClick { true }
                },
        )
        Box(
            Modifier
                .offset((l.downX - l.downR).dp, (l.downY - l.downR).dp)
                .size((l.downR * 2.0).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "More about these sunglasses"
                    onClick { true }
                },
        )
        Box(
            Modifier
                .offset((l.wishRight - (14.0 + 6.0 + art.wishlistWidth)).dp, (l.y(768.0) - 8.0).dp)
                .size((14.0 + 6.0 + art.wishlistWidth).dp, 28.dp)
                .semantics {
                    contentDescription = "Add to wishlist"
                },
        )
    }
}
