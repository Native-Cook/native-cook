package com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding

import android.app.Activity
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.min

/** The podcast app's first screen, its mic swinging on its cable by itself. */
@Composable
fun MicCordOnboarding(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val densityValue = density.density
    val reduced = rememberReducedMotion()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val art = remember(context) { MicCordOnboardingArt(context) }
    val audio = remember(context, reduced) {
        if (reduced) null else MicCordOnboardingAudio(context)
    }

    fun buzz(kind: Int) {
        // CONTEXT_CLICK is the light touch cue used by the other native blocks; CLOCK_TICK is a
        // small grab cue. Both are one-shot and are meaningful moments rather than frame sounds.
        view.performHapticFeedback(
            if (kind == 1) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    fun startListening() {
        audio?.play("notes")
        buzz(1)
    }

    DisposableEffect(audio) {
        onDispose { audio?.close() }
    }

    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0B0F)),
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val state = remember(width, height, topInset) {
            MicCordOnboardingState(width, height, topInset.toDouble())
        }
        SideEffect { state.setReducedMotion(reduced) }

        LaunchedEffect(state, lifecycle, autoPlay, reduced) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous = 0L
                while (true) {
                    if (reduced && !state.awake) {
                        snapshotFlow { state.awake }.first { it }
                        previous = 0L
                    }
                    withFrameNanos { nanos ->
                        val dt = min(
                            if (previous == 0L) 0.016 else (nanos - previous) / 1e9,
                            0.5,
                        )
                        previous = nanos
                        state.step(dt, autoPlay && !reduced, reduced)
                    }
                }
            }
        }

        val canvasModifier = Modifier
            .fillMaxSize()
            .semantics {
                role = Role.Image
                contentDescription = "A pink microphone hanging on its cable. Drag it to swing it."
            }
            .pointerInput(state, reduced, densityValue, audio) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var end = down.position
                    val began = state.beginMicTouch(
                        (down.position.x / densityValue).toDouble(),
                        (down.position.y / densityValue).toDouble(),
                    )
                    if (began) {
                        audio?.play("grab")
                        buzz(0)
                    }
                    down.consume()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            end = change.position
                            tracker.addPosition(change.uptimeMillis, end)
                            if (began && change.pressed) {
                                state.updateMicTouch(
                                    (end.x / densityValue).toDouble(),
                                    (end.y / densityValue).toDouble(),
                                )
                            }
                            change.consume()
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (began) {
                            val velocity = tracker.calculateVelocity()
                            val result = state.endMicTouch(
                                (end.x / densityValue).toDouble(),
                                (end.y / densityValue).toDouble(),
                                (velocity.x / densityValue).toDouble(),
                                (velocity.y / densityValue).toDouble(),
                            )
                            if (result?.fast == true) audio?.play("swing")
                            buzz(1)
                        }
                    }
                }
            }

        Canvas(canvasModifier) {
            state.tick.intValue
            drawIntoCanvas { wrapper ->
                val canvas = wrapper.nativeCanvas
                canvas.save()
                canvas.scale(densityValue, densityValue)
                art.drawScene(
                    canvas = canvas,
                    width = size.width / densityValue,
                    height = size.height / densityValue,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    rope = state.rope,
                    time = state.time,
                    buttonPressed = state.buttonPressed,
                )
                canvas.restore()
            }
        }

        Box(
            modifier = Modifier
                .offset(x = 0.dp, y = (height * 0.56).dp)
                .size(width.dp, 120.dp)
                .semantics {
                    heading()
                    contentDescription = "A Quick & Easy Way to Make Your Own Podcast"
                },
        )

        Box(
            modifier = Modifier
                .offset(
                    x = ((width - 240.0) * 0.5).dp,
                    y = (height - bottomInset - 34.0 - 56.0).dp,
                )
                .size(240.dp, 56.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Start listening"
                    onClick(label = "Start listening") {
                        startListening()
                        true
                    }
                }
                .pointerInput(state, audio) {
                    detectTapGestures(
                        onPress = {
                            state.pressButton(true)
                            val released = tryAwaitRelease()
                            state.pressButton(false)
                            if (released) startListening()
                        },
                    )
                },
        )
    }
}
