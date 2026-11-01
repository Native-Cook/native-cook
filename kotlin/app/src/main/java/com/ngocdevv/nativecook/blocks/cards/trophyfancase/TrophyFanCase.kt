package com.ngocdevv.nativecook.blocks.cards.trophyfancase

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The trophy case: a fan of running cards that turns one card over to reveal its splits. */
@Composable
fun TrophyFanCase(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BlockSystemBars(darkIcons = false)
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val lifecycle = owner.lifecycle
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val engine = remember { TrophyFanCaseEngine() }
    val renderer = remember(context) { TrophyFanCaseRenderer(context) }
    val audio = remember(context, reduced) { TrophyFanCaseAudio(context, enabled = !reduced) }
    val tick = remember { mutableLongStateOf(0L) }
    var interactionEpoch by remember { mutableIntStateOf(0) }
    val wake = rememberUpdatedState<() -> Unit> { interactionEpoch += 1 }

    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    DisposableEffect(audio) {
        onDispose { audio.close() }
    }
    DisposableEffect(lifecycle, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.touching = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(auto, reduced) {
        engine.autoPlay = auto
        engine.reduced = reduced
        engine.reset(reduced)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                contentDescription = "Trophy Case. Four running achievements. Swipe across the cards to lift one, then drag down or tap to return it."
            },
    ) {
        val width = maxWidth.value.toDouble()
        engine.autoPlay = auto
        engine.reduced = reduced
        engine.configure(width, safeTop.toDouble())
        val pointer = Modifier.pointerInput(engine, reduced, density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val unit = density.density
                val startX = (down.position.x / unit).toDouble()
                val startY = (down.position.y / unit).toDouble()
                val now = engine.clock
                val card = engine.cardAt(startX, startY)
                val current = engine.target
                engine.touch = TrophyFanCaseTouch(startX, startY, now, card, 0, -9)
                engine.touchStarted()
                engine.target = current.copy(spread = if (engine.hand.spread > 0.5 || engine.hand.sel >= 0) 1.0 else 0.0)
                wake.value.invoke()

                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                var finished = false
                try {
                    while (!finished) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val x = (change.position.x / unit).toDouble()
                        val y = (change.position.y / unit).toDouble()
                        val dx = x - startX
                        val dy = y - startY
                        engine.lastTouch = engine.clock

                        if (!change.pressed) {
                            val v = velocity.calculateVelocity()
                            finishTrophyFanCaseTouch(
                                engine = engine,
                                dx = dx,
                                dy = dy,
                                velocityX = v.x / unit,
                                velocityY = v.y / unit,
                                durationMs = change.uptimeMillis - down.uptimeMillis,
                                reduced = reduced,
                                audio = audio,
                                view = view,
                            )
                            change.consume()
                            finished = true
                            continue
                        }

                        val touch = engine.touch
                        if (engine.hand.sel >= 0) {
                            if (touch.card == engine.hand.sel && dy > 0.0) {
                                engine.hand.cards[engine.hand.sel].y = engine.selectedY + dy * 0.6
                            }
                        } else if (touch.mode == 1) {
                            engine.hand.hx = x
                            engine.hand.hy = y
                        } else {
                            if (engine.target.spread < 0.5) {
                                engine.target = engine.target.copy(spread = 1.0)
                                playTrophyFanCaseCue(audio, reduced, "fan")
                            } else {
                                val focus = ((x - engine.centerX) / 100.0 + 1.5).coerceIn(-0.4, 3.4)
                                val previousTarget = engine.target
                                engine.target = previousTarget.copy(focus = focus, focusOn = 1.0)
                                val detent = focus.roundToInt().coerceIn(0, 3)
                                if (detent != touch.detent) {
                                    engine.touch = touch.copy(detent = detent)
                                    playTrophyFanCaseCue(audio, reduced, "tick")
                                    performTrophyFanCaseHaptic(view, 0)
                                }
                                if (dy < -36.0 && touch.card >= 0) {
                                    engine.hand.held = detent
                                    engine.hand.hx = x
                                    engine.hand.hy = y
                                    engine.touch = touch.copy(mode = 1, card = detent, detent = detent)
                                    engine.target = previousTarget.copy(focusOn = 0.0)
                                    playTrophyFanCaseCue(audio, reduced, "lift")
                                    performTrophyFanCaseHaptic(view, 1)
                                }
                            }
                        }
                        change.consume()
                    }
                } finally {
                    if (!finished) {
                        val v = velocity.calculateVelocity()
                        finishTrophyFanCaseTouch(
                            engine = engine,
                            dx = 0.0,
                            dy = 0.0,
                            velocityX = v.x / unit,
                            velocityY = v.y / unit,
                            durationMs = 0L,
                            reduced = reduced,
                            audio = audio,
                            view = view,
                        )
                    }
                }
            }
        }

        LaunchedEffect(auto, reduced, lifecycle, interactionEpoch) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = -1L
                while (isActive) {
                    if (!auto && !engine.touching && !engine.hasMotion()) break
                    val frame = withFrameNanos { it }
                    val dt = if (lastFrame < 0L) 1.0 / 60.0 else min((frame - lastFrame) / 1e9, 0.05)
                    lastFrame = frame
                    engine.advance(dt)
                    tick.longValue += 1L
                }
            }
        }

        Box(Modifier.fillMaxSize().then(pointer)) {
            Canvas(Modifier.fillMaxSize()) {
                tick.longValue
                drawIntoCanvas { holder ->
                    val native = holder.nativeCanvas
                    native.save()
                    native.scale(density.density, density.density)
                    renderer.draw(
                        canvas = native,
                        width = size.width / density.density,
                        height = size.height / density.density,
                        safeTop = safeTop,
                        safeBottom = safeBottom,
                        engine = engine,
                    )
                    native.restore()
                }
            }
            TrophyFanCaseTrophyAccessibility(engine, tick, wake.value, 0)
            TrophyFanCaseTrophyAccessibility(engine, tick, wake.value, 1)
            TrophyFanCaseTrophyAccessibility(engine, tick, wake.value, 2)
            TrophyFanCaseTrophyAccessibility(engine, tick, wake.value, 3)
        }
    }
}

@Composable
private fun TrophyFanCaseTrophyAccessibility(
    engine: TrophyFanCaseEngine,
    tick: MutableLongState,
    wake: () -> Unit,
    index: Int,
) {
    val trophy = TrophyFanCaseTrophies[index]
    val density = LocalDensity.current.density
    val view = LocalView.current
    fun activate(): Boolean {
        engine.openCard(index)
        wake()
        performTrophyFanCaseHaptic(view, 1)
        return true
    }
    Box(
        Modifier
            .size(CARD_W.dp, CARD_H.dp)
            .graphicsLayer {
                tick.longValue
                val card = engine.hand.cards[index]
                val flip = Math.toRadians(card.f)
                translationX = ((card.x - CARD_W.toDouble() / 2.0) * density.toDouble()).toFloat()
                translationY = ((card.y - CARD_H.toDouble() / 2.0) * density.toDouble()).toFloat()
                rotationZ = card.r.toFloat()
                scaleX = (card.s * max(0.02, kotlin.math.abs(kotlin.math.cos(flip)))).toFloat()
                scaleY = (card.s * (1.0 + 0.06 * kotlin.math.abs(kotlin.math.sin(flip)))).toFloat()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${trophy.title}, ${trophy.time}"
                onClick(label = "Turns the card over", action = ::activate)
                customActions = listOf(
                    CustomAccessibilityAction("Turn the card over", ::activate),
                )
            },
    )
}

private fun finishTrophyFanCaseTouch(
    engine: TrophyFanCaseEngine,
    dx: Double,
    dy: Double,
    velocityX: Float,
    velocityY: Float,
    durationMs: Long,
    reduced: Boolean,
    audio: TrophyFanCaseAudio,
    view: android.view.View,
) {
    val touch = engine.touch
    val tap = hypot(dx, dy) < 8.0 && durationMs < 350L
    if (engine.hand.sel >= 0) {
        if (tap || dy > 60.0 || velocityY > 700.0) {
            engine.closeSelection()
            playTrophyFanCaseCue(audio, reduced, "flip")
            performTrophyFanCaseHaptic(view, 1)
        }
        engine.touchEnded()
        return
    }
    if (touch.mode == 1) {
        val index = engine.hand.held
        if (index >= 0) {
            engine.hand.held = -1
            val card = engine.hand.cards[index]
            if (card.ph == 1) card.ph = 0
            card.vx = velocityX.toDouble()
            card.vy = velocityY.toDouble()
            if (dy < -90.0 || velocityY < -600.0) {
                engine.choose(index)
                playTrophyFanCaseCue(audio, reduced, "flip")
                performTrophyFanCaseHaptic(view, 2)
            } else {
                engine.target = engine.target.copy(focusOn = 0.0)
            }
        }
        engine.touchEnded()
        return
    }
    if (tap) {
        if (engine.target.spread < 0.5) {
            engine.target = engine.target.copy(spread = 1.0)
            playTrophyFanCaseCue(audio, reduced, "fan")
            performTrophyFanCaseHaptic(view, 1)
        } else if (touch.card >= 0) {
            engine.choose(touch.card)
            playTrophyFanCaseCue(audio, reduced, "lift")
            performTrophyFanCaseHaptic(view, 2)
        }
    } else {
        engine.target = engine.target.copy(focusOn = 0.0)
    }
    engine.touchEnded()
}

private fun playTrophyFanCaseCue(audio: TrophyFanCaseAudio, reduced: Boolean, cue: String) {
    if (!reduced) audio.play(cue)
}

private fun performTrophyFanCaseHaptic(view: android.view.View, kind: Int) {
    if (!view.isAttachedToWindow || !view.isHapticFeedbackEnabled) return
    val constant = when (kind) {
        0 -> HapticFeedbackConstants.CLOCK_TICK
        1 -> HapticFeedbackConstants.CONTEXT_CLICK
        else -> HapticFeedbackConstants.LONG_PRESS
    }
    view.performHapticFeedback(constant)
}

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

