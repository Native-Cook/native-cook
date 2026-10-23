package com.ngocdevv.nativecook.blocks.navigation.holoagendacarousel

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.*

@Composable
fun HoloAgendaCarouselDemo() {
    HoloAgendaCarousel(autoPlay = true)
}

/** An endless native agenda pager. A finger temporarily takes ownership of the one frame clock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HoloAgendaCarousel(autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val motion = remember(autoPlay, reduced) {
        HoloMotion(autoPlay, reduced) { impact ->
            view.performHapticFeedback(if (impact) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    val renderer = remember { HoloRenderer() }
    val frame = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val safeTop = WindowInsets.systemBarsIgnoringVisibility.getTop(density)/density.density
    val safeBottom = WindowInsets.systemBarsIgnoringVisibility.getBottom(density)/density.density
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(motion) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            motion.step((now-previous)/1_000_000f)
            frame.intValue++
            previous = now
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val k = min(1f, min(w/402f, (h-safeTop-safeBottom)/778f))
        val left = (w-402*k)/2
        val top = safeTop
        fun grid(n: Float) = floor(n*k/4+.5f)*4
        val selected = motion.selection
        val session = HoloTheme.sessions[selected]
        val isAdded = motion.selected != 0
        Canvas(Modifier.fillMaxSize().pointerInput(motion, k, top, left) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = (down.position.x/density.density-left)/k
                val y = down.position.y/density.density-top
                val onStage = y >= grid(96f) && y <= grid(96f)+320*k
                val onButton = x in 24f..378f && y >= grid(706f) && y <= grid(706f)+56*k
                val direction = if (abs(x-201)<96) 0 else sign(x-201).toInt()
                val start = motion.position
                motion.down(if (onStage) HoloTheme.index(HoloTheme.nearest(start)+direction) else -1, onButton)
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                var dragging = false
                var cancelled = false
                var up = down
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }
                        up = change
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val delta = (change.position-down.position)/(density.density*k)
                        if (!dragging && abs(delta.y)>14 && abs(delta.x)<8) cancelled = true
                        if (onStage && !cancelled && (dragging || abs(delta.x)>8)) {
                            dragging = true
                            motion.position = start-delta.x/124
                            motion.lastTouch = motion.elapsed
                            change.consume()
                        }
                    } while (up.pressed)
                    val travel = (up.position-down.position).getDistance()/(density.density*k)
                    when {
                        cancelled -> if (onStage) motion.settle(HoloTheme.nearest(motion.position).toFloat())
                        dragging -> {
                            val speed = -velocity.calculateVelocity().x/(density.density*k*124)
                            motion.settle(HoloTheme.releaseTarget(motion.position, start, speed), speed)
                        }
                        onStage && travel <= 8 -> motion.settle((HoloTheme.nearest(start)+direction).toFloat())
                        onButton && travel <= 8 -> motion.toggle()
                        onStage -> motion.settle(HoloTheme.nearest(motion.position).toFloat())
                    }
                } finally {
                    motion.release()
                }
            }
        }) {
            frame.intValue
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.translate(left*density.density, top*density.density)
            canvas.scale(k*density.density, k*density.density)
            val reveal = (motion.elapsed/200).coerceIn(0f, 1f)
            val layer = if (reveal < 1f) canvas.saveLayerAlpha(0f, 0f, 402f, 778f, (255*reveal).roundToInt()) else -1
            renderer.draw(canvas, motion) { value -> grid(value)/k }
            if (layer >= 0) canvas.restoreToCount(layer)
            canvas.restore()
        }
        Box(Modifier.offset(left.dp, (top+grid(96f)).dp).size((402*k).dp, (320*k).dp).semantics {
            contentDescription = "Agenda"
            stateDescription = "${session[2]}, ${selected+1} of 4"
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..3f, 2)
            customActions = listOf(
                CustomAccessibilityAction("Next session") {
                    motion.move(1)
                    true
                },
                CustomAccessibilityAction("Previous session") {
                    motion.move(-1)
                    true
                },
            )
            setProgress { value ->
                motion.move(value.roundToInt()-selected)
                true
            }
        })
        Box(Modifier.offset(left.dp, (top+grid(456f)).dp).size((402*k).dp, (180*k).dp).semantics {
            contentDescription = "${session[1]}. ${session[2]}. ${session[3]} ${session[4]}, ${session[5]}."
        })
        Box(Modifier.offset((left+24*k).dp, (top+grid(706f)).dp).size((354*k).dp, (56*k).dp).semantics {
            role = Role.Button
            contentDescription = if (isAdded) "In your agenda" else "Add to agenda"
            this.selected = isAdded
            onClick {
                motion.toggle()
                true
            }
        })
    }
}
