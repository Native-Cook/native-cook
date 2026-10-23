package com.ngocdevv.nativecook.blocks.onboarding.mascotsocialtour

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs

/** A horizontal swipe scrubs the same four stories shown by the deterministic autoPlay loop. */
@Composable
fun MascotSocialTour(
    autoPlay: Boolean = false,
    renderTime: Double? = null,
    onLogin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val renderer = remember(context) { MascotSocialTourRenderer(context) }
    val clock = remember { MascotSocialTourClock() }
    // Only drawing and semantics read this clock. It never recomposes the screen per frame.
    val time = remember { mutableDoubleStateOf(if (reducedMotion) 8.55 else 0.0) }
    var touchSerial by remember { mutableIntStateOf(0) }
    val currentAutoPlay by rememberUpdatedState(autoPlay)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentRenderTime by rememberUpdatedState(renderTime)
    val currentOnLogin by rememberUpdatedState(onLogin)

    fun shown(now: Long = System.nanoTime()): Double = clock.shown(now, currentAutoPlay, currentReducedMotion, currentRenderTime)
    fun changed() {
        time.doubleValue = shown()
        touchSerial += 1
    }
    fun lightHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun selectPage(delta: Int): Boolean {
        val page = MascotSocialTourMotion.pages(shown()).active
        clock.pause(MascotSocialTourMotion.anchors[(page + delta).coerceIn(0, 3)])
        lightHaptic()
        changed()
        return true
    }

    DisposableEffect(view) {
        val controller = context.mascotSocialTourActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null) {
                if (status != null) controller.isAppearanceLightStatusBars = status
                if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
            }
        }
    }

    LaunchedEffect(autoPlay, reducedMotion, renderTime, touchSerial) {
        time.doubleValue = shown()
        if (renderTime != null || reducedMotion || (!autoPlay && clock.manualTime == null)) return@LaunchedEffect
        do {
            withFrameNanos { time.doubleValue = shown(it) }
        } while (autoPlay || clock.manualTime != null)
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val sx = maxWidth.value / 393f
        val sy = maxHeight.value / 852f
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    val velocity = VelocityTracker()
                    fun release() {
                        val current = clock.manualTime ?: clock.dragOrigin
                        val speed = velocity.calculateVelocity().x / (size.width / 393.0)
                        // The same 350 ms prediction horizon is used by the React Native port.
                        val predictedTravel = clock.translation + speed * 0.35
                        val projected = (clock.dragOrigin - (clock.translation + 0.18 * (predictedTravel - clock.translation)) / 80).coerceIn(0.0, 9.4)
                        val target = MascotSocialTourMotion.anchors.minBy { abs(it - projected) }
                        clock.releaseFrom = current
                        clock.releaseTo = target
                        clock.manualTime = if (currentReducedMotion) target else current
                        clock.dragging = false
                        clock.releasedAt = System.nanoTime()
                        lightHaptic()
                        changed()
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            clock.dragOrigin = shown()
                            clock.translation = 0.0
                            clock.manualTime = clock.dragOrigin
                            clock.dragging = true
                            velocity.resetTracking()
                            velocity.addPosition(System.nanoTime() / 1_000_000, position)
                            changed()
                        },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, amount ->
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, change.position)
                        clock.translation += amount / (size.width / 393.0)
                        clock.manualTime = (clock.dragOrigin - clock.translation / 80).coerceIn(0.0, 9.4)
                        time.doubleValue = shown()
                    }
                }
                .semantics {
                    val page = MascotSocialTourMotion.pages(time.doubleValue).active
                    contentDescription = MascotSocialTourMotion.titles[page].replace('\n', ' ') + ". " + MascotSocialTourMotion.bodies[page].replace('\n', ' ')
                    stateDescription = "Page ${page + 1} of 4"
                    customActions = listOf(
                        CustomAccessibilityAction("Previous story") { selectPage(-1) },
                        CustomAccessibilityAction("Next story") { selectPage(1) },
                    )
                },
        ) {
            val t = time.doubleValue
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.scale(size.width / 393f, size.height / 852f)
                renderer.draw(canvas, t)
                canvas.restore()
            }
        }
        // The illustration and labels share one drawing coordinate system; the real button
        // keeps a 79 × 44 pt target, keyboard focus and a native accessibility action.
        Box(
            Modifier.offset((293.5f * sx).dp, (67f * sy).dp)
                .size((79f * sx).dp, (44f * sy).dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    clock.pause(shown())
                    lightHaptic()
                    changed()
                    currentOnLogin?.invoke()
                }
                .semantics { contentDescription = "Login" },
        )
    }
}

private tailrec fun Context.mascotSocialTourActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.mascotSocialTourActivity()
    else -> null
}
