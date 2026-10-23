package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive

/** A glass checklist with a native fixed-step simulation. Touch pauses the optional 10.4 s demo. */
@Composable
fun GlowToggleChecklist(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val still = rememberReducedMotion()
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val density = LocalDensity.current.density
    val state = remember(autoPlay, still) { GlowChecklistState(autoPlay, still) }
    val renderer = remember(context) { GlowChecklistRenderer(context) }
    val touches = remember { ArrayDeque<GlowChecklistTouch>() }
    val frame = remember { mutableIntStateOf(0) }
    var accessibilityRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previous != null) controller.systemBarsBehavior = previous
        }
    }

    LaunchedEffect(state) {
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            state.events = GlowChecklistState.Events()
            while (touches.isNotEmpty()) GlowChecklistSimulation.step(state, touches.removeFirst(), 0.0)
            GlowChecklistSimulation.step(state, null, (now - last) / 1e9)
            last = now
            val events = state.events
            if (events.changed) accessibilityRevision++
            if (events.cap) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (events.onMask != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (events.offMask != 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (events.success) view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
            )
            frame.intValue++
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val k = minOf(1f, width / 402f, height / 700f)
        Canvas(Modifier.fillMaxSize()) {
            frame.intValue
            renderer.draw(drawContext.canvas.nativeCanvas, size.width / density, size.height / density, density, state)
        }
        // Recompose only for committed states. Per-frame positions are read in the layer phase.
        accessibilityRevision
        val expanded = state.openTarget != 0.0
        for (control in -1..3) {
            if (control >= 0 && !expanded) continue
            val cap = control == -1
            val targetWidth = (if (cap) 288 else 284) * k
            val targetHeight = (if (cap) 60 else 68) * k
            val checked = control >= 0 && state.knobs[control].on
            Box(
                Modifier.size(targetWidth.dp, targetHeight.dp)
                    .graphicsLayer {
                        frame.intValue
                        val capCy = height / 2 - 158 * k * state.open.toFloat()
                        val bottom = capCy + 30 * k + 316 * k * state.open.toFloat()
                        translationX = (width / 2 - targetWidth / 2) * density
                        translationY = ((if (cap) capCy else bottom - (376 - GlowChecklistRenderer.ROWS[control]) * k) - targetHeight / 2) * density
                    }
                    .semantics {
                        role = if (cap) Role.Button else Role.Switch
                        contentDescription = if (cap) "Success checklist" else GlowChecklistRenderer.LABELS[control].lowercase().replaceFirstChar { it.uppercase() }
                        stateDescription = if (cap) { if (expanded) "Expanded" else "Collapsed" } else if (checked) "On" else "Off"
                        if (!cap) toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                        onClick(if (cap) { if (expanded) "Closes the checklist" else "Opens the checklist" } else "Toggle") {
                            touches.addLast(GlowChecklistTouch(control, false, activate = true))
                            true
                        }
                    }
                    .pointerInput(state, control, k, density) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (state.present < 1 || (!cap && state.open < 0.98)) return@awaitEachGesture
                            down.consume()
                            touches.addLast(GlowChecklistTouch(control, true, k = k.toDouble()))
                            val start = down.position
                            var lastX = start.x
                            var lastTime = down.uptimeMillis
                            var velocity = 0.0
                            var dx = 0.0
                            var cancelled = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) {
                                        cancelled = true
                                        break
                                    }
                                    val p = change.position
                                    dx = (p.x - start.x).toDouble() / density
                                    val elapsed = (change.uptimeMillis - lastTime) / 1000.0
                                    if (change.pressed && p.x != lastX) {
                                        velocity = (p.x - lastX) / density / maxOf(1.0 / 240, elapsed)
                                        lastTime = change.uptimeMillis
                                        lastX = p.x
                                    }
                                    if (cap && (p.x < -16 * density || p.x > (targetWidth + 16) * density || p.y < -16 * density || p.y > (targetHeight + 16) * density)) cancelled = true
                                    if (!change.pressed && elapsed > 0.08) velocity = 0.0
                                    touches.addLast(GlowChecklistTouch(control, change.pressed, dx, velocity, k.toDouble(), cancelled))
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            } finally {
                                // Cancellation never commits; also clears realDown if the target leaves composition.
                                touches.addLast(GlowChecklistTouch(control, false, dx, 0.0, k.toDouble(), cancel = true))
                            }
                        }
                    },
            )
        }
    }
}
