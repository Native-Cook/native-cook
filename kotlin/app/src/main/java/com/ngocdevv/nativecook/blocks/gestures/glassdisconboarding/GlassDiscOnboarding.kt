package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pull a frosted glass dome upward to reveal Wavo, then drag its bead down to rewind. The exact same engine inputs
 * drive the deterministic autoplay loop. A real touch stops it; the engine resumes after the specified quiet time.
 */
@Composable
fun GlassDiscOnboarding(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val densityValue = density.density
    val view = LocalView.current
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    val tilt = rememberGlassDiscOnboardingTilt(enabled = !reducedMotion)
    val bubbleAtlas = remember(context) {
        requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.glassdisconboarding_bubbles)) {
            "Glass Disc Onboarding requires the baked 6 × 4 bubble atlas."
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val buttons = remember(widthPx, heightPx, densityValue) {
            glassDiscOnboardingButtonRects(widthPx.toFloat(), heightPx.toFloat(), densityValue)
        }
        val texture = remember(widthPx, heightPx, densityValue) {
            GlassDiscOnboardingTexture.make(widthPx, heightPx, densityValue)
        }
        val engine = remember(widthPx, heightPx, densityValue) {
            GlassDiscOnboardingEngine(
                widthPx = widthPx.toFloat(),
                heightPx = heightPx.toFloat(),
                density = densityValue,
                ready = true,
                autoPlay = autoPlay,
                reduceMotion = reducedMotion,
                onHaptic = { kind -> view.performHapticFeedback(glassDiscOnboardingHaptic(kind)) },
            )
        }
        val renderer = remember { GlassDiscOnboardingRenderer() }
        val ctas = remember { List(2) { GlassDiscOnboardingCtaState() } }
        val tick = remember { mutableLongStateOf(0L) }
        var community by remember(engine) { mutableStateOf(engine.snapshot().stage == GlassDiscOnboardingStage.Community) }

        SideEffect {
            engine.autoPlay = autoPlay
            engine.reduceMotion = reducedMotion
            engine.ready = true
        }
        LaunchedEffect(engine) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) {
                        0f
                    } else {
                        ((now - previous) / 1_000_000_000f).coerceIn(0f, GlassDiscOnboardingTokens.PORT.maximumFrameSeconds)
                    }
                    previous = now
                    engine.update(dt, now / 1_000_000L)
                    val stage = engine.snapshot().stage == GlassDiscOnboardingStage.Community
                    if (stage != community) community = stage
                    tick.longValue++
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (community) {
                            "${GlassDiscOnboardingTheme.COPY.headline} ${GlassDiscOnboardingTheme.COPY.sub.joinToString(" ")}" 
                        } else {
                            "${GlassDiscOnboardingTheme.COPY.intro.joinToString(" ")}. ${GlassDiscOnboardingTheme.COPY.hint}"
                        }
                        stateDescription = if (community) "Community screen" else "Introduction"
                        if (community) heading() else role = Role.Button
                        onClick(if (community) GlassDiscOnboardingTheme.COPY.back else GlassDiscOnboardingTheme.COPY.enter) {
                            val now = SystemClock.uptimeMillis()
                            if (community) engine.startOver(now) else engine.enter(now)
                            true
                        }
                    }
                    .pointerInput(engine, densityValue) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPointerInputChange(down)
                            val start = down.position
                            engine.onTouchDown(start.x, start.y, down.uptimeMillis)
                            var ended = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPointerInputChange(change)
                                    if (!change.pressed) {
                                        val velocity = tracker.calculateVelocity()
                                        engine.onTouchUp(velocity.y, cancelled = false, nowMillis = change.uptimeMillis)
                                        ended = true
                                        change.consume()
                                        break
                                    }
                                    if (change.position != change.previousPosition) {
                                        engine.onTouchMove(change.position.x, change.position.y, change.uptimeMillis)
                                    }
                                    change.consume()
                                }
                            } finally {
                                if (!ended) engine.onTouchUp(0f, cancelled = true, nowMillis = SystemClock.uptimeMillis())
                            }
                        }
                    },
            ) {
                // Snapshot reads stay in the draw scope: state progression invalidates only this canvas, not its UI tree.
                @Suppress("UNUSED_VARIABLE")
                val frameNumber = tick.longValue
                val frame = engine.snapshot()
                val native = drawContext.canvas.nativeCanvas
                GlassDiscOnboardingCopy.drawBackdrop(native, size.width, size.height, densityValue, frame)
                GlassDiscOnboardingCopy.drawUnderGlass(native, size.width, size.height, densityValue, frame, reducedMotion)
                val fingerLight = if (frame.finger.alpha > GlassDiscOnboardingTokens.VIEW.fingerTiltThreshold) {
                    (frame.finger.x - size.width / 2f) / max(size.width, 1f)
                } else {
                    0f
                }
                val lightX = (tilt.roll * GlassDiscOnboardingTokens.VIEW.tiltMultiplier + fingerLight).coerceIn(-1f, 1f)
                val lightY = (tilt.pitch * GlassDiscOnboardingTokens.VIEW.tiltMultiplier).coerceIn(-1f, 1f)
                renderer.draw(native, frame, texture, bubbleAtlas, lightX, lightY)
                GlassDiscOnboardingCopy.drawForeground(native, size.width, size.height, densityValue, frame)
                val ctaRise = if (reducedMotion) 0f else (1f - frame.restAlpha) * (1f - frame.restAlpha) * (1f - frame.restAlpha) * GlassDiscOnboardingMotion.UPGRADE.rise * densityValue
                drawGlassDiscOnboardingCtas(native, buttons, densityValue, frame.restAlpha, ctas, SystemClock.uptimeMillis(), ctaRise)
            }

            if (community) {
                val frame = engine.snapshot()
                val ctaRise = if (reducedMotion) 0f else (1f - frame.restAlpha) * (1f - frame.restAlpha) * (1f - frame.restAlpha) * GlassDiscOnboardingMotion.UPGRADE.rise * densityValue
                GlassDiscOnboardingCtaTouchTarget(buttons.light, GlassDiscOnboardingTheme.COPY.email, densityValue, ctaRise, ctas[0], engine)
                GlassDiscOnboardingCtaTouchTarget(buttons.dark, GlassDiscOnboardingTheme.COPY.phone, densityValue, ctaRise, ctas[1], engine)
            }
        }
    }
}

@Composable
private fun GlassDiscOnboardingCtaTouchTarget(rect: android.graphics.RectF, label: String, density: Float, offsetY: Float, state: GlassDiscOnboardingCtaState, engine: GlassDiscOnboardingEngine) {
    val target = android.graphics.RectF(rect).apply { offset(0f, offsetY) }
    Box(
        Modifier
            .size((target.width() / density).dp, (target.height() / density).dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .pointerInput(state, engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    engine.interruptAutoPlay(down.uptimeMillis, touchDown = true)
                    state.down(down.position.x + target.left, down.uptimeMillis)
                    var released = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                state.up(change.uptimeMillis)
                                engine.interruptAutoPlay(change.uptimeMillis, touchDown = false)
                                released = true
                                change.consume()
                                break
                            }
                            change.consume()
                        } while (true)
                    } finally {
                        if (!released) {
                            val now = SystemClock.uptimeMillis()
                            state.up(now)
                            engine.interruptAutoPlay(now, touchDown = false)
                        }
                    }
                }
            }
            .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) },
    )
}

private fun glassDiscOnboardingHaptic(kind: GlassDiscOnboardingHaptic): Int = when (kind) {
    GlassDiscOnboardingHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
    GlassDiscOnboardingHaptic.Commit -> HapticFeedbackConstants.CONTEXT_CLICK
    GlassDiscOnboardingHaptic.Land -> HapticFeedbackConstants.VIRTUAL_KEY
    GlassDiscOnboardingHaptic.Back -> HapticFeedbackConstants.CLOCK_TICK
}
