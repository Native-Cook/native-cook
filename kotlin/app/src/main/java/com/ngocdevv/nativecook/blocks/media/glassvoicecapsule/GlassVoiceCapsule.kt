package com.ngocdevv.nativecook.blocks.media.glassvoicecapsule

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion

private fun glassVoiceCapsuleNow() = System.nanoTime() / 1e9

/**
 * Swift reference port: a thick glass mic swells into a voice-note capsule over bundled wool art.
 * LOOP is nine seconds. Tap to record/stop; drag follows a velocity-preserving spring. After two
 * idle seconds the note stops, settles, and restarts the demo. Nothing records or plays audio.
 * Reduced motion stops the automatic clock and uses an eased, still interactive recording state.
 */
@Composable
fun GlassVoiceCapsule(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val reduced = rememberLiveReducedMotion()
    val owner = LocalLifecycleOwner.current
    val controller = remember(autoPlay, reduced) { GlassVoiceCapsuleController(autoPlay, reduced, glassVoiceCapsuleNow()) }
    val painter = remember { GlassVoiceCapsulePainter(context) }
    val clock = remember { mutableDoubleStateOf(glassVoiceCapsuleNow()) }
    var awake by remember(controller) { mutableStateOf(false) }
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!active) {
                controller.reset(glassVoiceCapsuleNow())
                awake = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            controller.reset(glassVoiceCapsuleNow())
        }
    }
    BlockSystemBars(darkIcons = false)
    val playing = autoPlay && !reduced
    val running = active && (playing || awake)
    LaunchedEffect(running, controller) {
        if (!running) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanos ->
                val now = nanos / 1e9
                controller.tick(now)
                clock.doubleValue = now
            }
            if (!playing && !controller.awake) { awake = false; break }
        }
    }
    fun accessibleToggle(wantRecording: Boolean): Boolean {
        val now = glassVoiceCapsuleNow()
        if (controller.frame(now).recording == wantRecording) return false
        controller.toggle(now)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        awake = true; clock.doubleValue = now
        return true
    }
    Canvas(
        Modifier.fillMaxSize().background(Color(0xFF6E8DB3))
            .semantics {
                contentDescription = "Say it out loud. A glass voice-note capsule. Tap the mic to start a note; drag the glass to move it."
                customActions = listOf(
                    CustomAccessibilityAction("Start a voice note") { accessibleToggle(true) },
                    CustomAccessibilityAction("Stop recording") { accessibleToggle(false) },
                )
            }
            .pointerInput(controller, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width / density.toDouble()
                    val h = size.height / density.toDouble()
                    val start = GlassVoiceCapsulePoint(down.position.x / density.toDouble(), down.position.y / density.toDouble())
                    val beginAt = glassVoiceCapsuleNow()
                    val initial = controller.frame(beginAt)
                    val offset = if (reduced) GlassVoiceCapsulePoint() else controller.drag.value(beginAt)
                    val cx = w / 2 + offset.x
                    val cy = h * 488 / 852 + initial.floatY + offset.y
                    if (abs(start.x - cx) > initial.width / 2 + 12 || abs(start.y - cy) > initial.height / 2 + 12) return@awaitEachGesture
                    controller.begin(beginAt); controller.touching = true
                    awake = true; clock.doubleValue = beginAt
                    down.consume()
                    var delta = GlassVoiceCapsulePoint()
                    var released = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val now = glassVoiceCapsuleNow()
                            delta = GlassVoiceCapsulePoint((change.position.x - down.position.x) / density.toDouble(), (change.position.y - down.position.y) / density.toDouble())
                            if (change.pressed) {
                                controller.drag.retarget(delta, now, false)
                            } else {
                                released = true
                            }
                            change.consume()
                            clock.doubleValue = now
                        } while (event.changes.any { it.id == down.id && it.pressed })
                        val now = glassVoiceCapsuleNow()
                        val f = controller.frame(now)
                        val current = if (reduced) GlassVoiceCapsulePoint() else controller.drag.value(now)
                        if (released && hypot(delta.x, delta.y) < 8) {
                            val angle = (if (reduced) 0.0 else f.angle + (current.x / 14).coerceIn(-8.0, 8.0)) * PI / 180
                            val dx = start.x - w / 2 - current.x
                            val dy = start.y - h * 488 / 852 - f.floatY - current.y
                            val x = (dx * cos(angle) + dy * sin(angle)) / f.scale
                            val y = (-dx * sin(angle) + dy * cos(angle)) / f.scale
                            if (!f.recording || hypot(x - (f.width / 2 - 50), y) < 34 * f.droplet + 10) {
                                controller.toggle(now)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                    } finally {
                        val now = glassVoiceCapsuleNow()
                        controller.touching = false
                        controller.drag.retarget(GlassVoiceCapsulePoint(), now, true)
                        controller.scheduleResume(now)
                        clock.doubleValue = now
                    }
                }
            },
    ) {
        // Read animation state only in drawing; the host composable never recomposes per frame.
        val now = clock.doubleValue
        val f = controller.frame(now)
        val offset = if (reduced) GlassVoiceCapsulePoint() else controller.drag.value(now)
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(density, density)
        painter.draw(canvas, size.width / density.toDouble(), size.height / density.toDouble(), f, offset, reduced)
        canvas.restore()
    }
}
