package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class PtsMode {
    Reading,
    Thinking,
    Summary,
}

internal enum class PtsHaptic {
    Lock,
    Level,
    Commit,
    Ready,
    Restore,
    Lift,
}

private data class PtsPinchState(
    val active: Boolean = false,
    val out: Boolean = false,
    val startP: Float = 0f,
    val focalX: Float = 0f,
    val focalY: Float = 0f,
    val track: Float = 0f,
    val armed: Boolean = false,
    val scripted: Boolean = false,
)

private data class PtsScriptState(
    var time: Float = 0f,
    var paused: Boolean = false,
    var realDown: Boolean = false,
    var quiet: Float = 0f,
    var segment: Int = 0,
    var returningToRest: Boolean = false,
)

private sealed interface PtsGestureEvent {
    data object TouchDown : PtsGestureEvent
    data object TouchUp : PtsGestureEvent
    data class Begin(val x: Float, val y: Float) : PtsGestureEvent
    data class Move(val scale: Float, val x: Float, val y: Float) : PtsGestureEvent
    data class End(val velocity: Float, val cancelled: Boolean) : PtsGestureEvent
}

private object PtsAlwaysMotion : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/**
 * Motion owner for the block. Gesture and autoplay input both pass through [pinchBegin],
 * [pinchMove], and [pinchEnd], matching the React Native interaction kernel.
 */
internal class PtsState(
    private val scope: CoroutineScope,
) {
    val p = Animatable(0f)
    val lift = Animatable(0f)
    val driftX = Animatable(0f)
    val driftY = Animatable(0f)
    val pageAlpha = Animatable(1f)
    val think = Animatable(0f)
    val clock = Animatable(0f)
    val rim = Animatable(0f)
    val reveal = Animatable(0f)
    val summaryAlpha = Animatable(1f)
    val mark = Animatable(0f)
    val markAlpha = Animatable(1f)
    val dim = Animatable(0f)
    val wait = Animatable(0f)
    val depthAlpha = Animatable(0f)
    val levelPos = Animatable(0f)
    val bloomAt = Animatable(-1f)
    val fingerAX = Animatable(0f)
    val fingerAY = Animatable(0f)
    val fingerBX = Animatable(0f)
    val fingerBY = Animatable(0f)
    val fingerAlpha = Animatable(0f)
    val readScroll = Animatable(0f)

    var mode by mutableStateOf(PtsMode.Reading)
        private set

    var isScrollLocked by mutableStateOf(false)
        private set

    var level by mutableStateOf(0)
        private set

    val summaryText: String
        get() = buildString {
            append(PtsCopy.levels[level].lead)
            for (index in PtsCopy.levels[level].points) {
                val item = PtsCopy.items[index]
                append(' ')
                append(item.title)
                append(": ")
                append(item.body)
            }
        }

    private var stage = PtsStage(width = 1f, height = 1f, insetTop = 0f)
    private var ready = false
    private var autoPlay = true
    private var reduceMotion = false
    private var revealTotal = 0f
    private var savedScroll = 0f
    private var jobToken = 0
    private var pinchState = PtsPinchState()
    private val script = PtsScriptState()
    private var thinkJob: Job? = null
    private var performHaptic: (PtsHaptic) -> Unit = {}
    private var announceSummary: () -> Unit = {}

    private fun launchMotion(block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(context = PtsAlwaysMotion, block = block)

    fun update(
        stage: PtsStage,
        autoPlay: Boolean,
        reduceMotion: Boolean,
        revealTotal: Float,
        ready: Boolean,
    ) {
        this.stage = stage
        this.autoPlay = autoPlay
        this.reduceMotion = reduceMotion
        this.revealTotal = revealTotal
        this.ready = ready
    }

    fun updateRevealTotal(value: Float) {
        revealTotal = value
    }

    fun requestSummary() {
        launchMotion {
            touch(down = false)
            if (mode == PtsMode.Reading) collapse(velocity = 0f, scripted = false)
        }
    }

    fun requestArticle() {
        launchMotion {
            touch(down = false)
            if (mode != PtsMode.Reading) restore(velocity = 0f, scripted = false)
        }
    }

    fun requestSource(source: Int) {
        val sourceY = listOf(790f, 912f, 1_040f, 1_160f).getOrElse(source) { 790f }
        val target = (sourceY - stage.height * .38f).coerceAtLeast(0f)
        launchMotion {
            touch(down = false)
            if (mode != PtsMode.Reading) restore(velocity = 0f, scripted = false, targetScroll = target)
        }
    }

    internal fun installFeedback(
        haptic: (PtsHaptic) -> Unit,
        announcement: () -> Unit,
    ) {
        performHaptic = haptic
        announceSummary = announcement
    }

    internal fun clearFeedback() {
        performHaptic = {}
        announceSummary = {}
    }

    private fun changeMode(next: PtsMode) {
        if (mode == next) return
        mode = next
        if (next == PtsMode.Summary) announceSummary()
    }

    private fun buzz(kind: PtsHaptic, scripted: Boolean) {
        if (!scripted) performHaptic(kind)
    }

    private suspend fun arrive(token: Int, scripted: Boolean) {
        if (jobToken != token || mode != PtsMode.Thinking) return
        changeMode(PtsMode.Summary)
        launchMotion {
            if (reduceMotion) rim.snapTo(0f)
            else rim.animateTo(0f, ptsTween(PtsTiming.rimOut))
        }
        launchMotion {
            think.animateTo(
                targetValue = 0f,
                animationSpec = ptsTween(if (reduceMotion) PtsTiming.reducedFade else PtsFlight.landMs),
            )
            buzz(PtsHaptic.Ready, scripted)
            if (!reduceMotion) bloomAt.snapTo(clock.value)
        }
        launchMotion {
            if (reduceMotion) lift.snapTo(1f)
            else lift.animateTo(1f, ptsSpring(PtsSpring.lift))
        }
        if (!reduceMotion) {
            launchMotion { p.animateTo(1f, ptsSpring(PtsSpring.collapse)) }
            launchMotion {
                readScroll.animateTo(
                    0f,
                    tween(PtsFlight.scrollMs.toInt(), easing = ptsInOutEasing),
                )
            }
        }
        summaryAlpha.snapTo(1f)
        reveal.snapTo(0f)
        launchMotion {
            reveal.animateTo(
                targetValue = revealTotal,
                animationSpec = tween(
                    durationMillis = (if (reduceMotion) {
                        PtsTiming.reducedFade
                    } else {
                        revealTotal.toLong().coerceAtLeast(1L)
                    }).toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }

    private suspend fun collapse(velocity: Float, scripted: Boolean) {
        thinkJob?.cancel()
        val token = ++jobToken
        changeMode(PtsMode.Thinking)
        buzz(PtsHaptic.Commit, scripted)
        isScrollLocked = true
        savedScroll = readScroll.value
        driftX.stop()
        driftY.stop()
        launchMotion { driftX.animateTo(0f, ptsSpring(PtsSpring.drift)) }
        launchMotion { driftY.animateTo(0f, ptsSpring(PtsSpring.drift)) }

        wait.snapTo(0f)
        launchMotion {
            mark.animateTo(
                PtsMark.count.toFloat(),
                tween(
                    ((PtsMark.count - mark.value).coerceAtLeast(0f) * PtsMark.autoLockMs).toInt(),
                    easing = LinearEasing,
                ),
            )
        }
        launchMotion { dim.animateTo(PtsMark.dimMax, ptsTween(PtsTiming.thinkIn)) }
        launchMotion { depthAlpha.animateTo(0f, ptsTween(160)) }
        if (reduceMotion) {
            lift.snapTo(1f)
            launchMotion {
                pageAlpha.animateTo(0f, ptsTween(PtsTiming.reducedFade))
                p.snapTo(1f)
                pageAlpha.animateTo(1f, ptsTween(PtsTiming.reducedFade))
            }
        } else {
            lift.snapTo(0f)
            val hold = p.value.coerceIn(PtsHold.min, PtsHold.max)
            launchMotion {
                try {
                    val releaseVelocity = -velocity * PtsPinch.velocityToProgress
                    p.animateTo(
                        targetValue = hold,
                        animationSpec = ptsSpring(PtsSpring.hold, releaseVelocity),
                        initialVelocity = releaseVelocity,
                    )
                } catch (_: CancellationException) {
                    // A new pinch owns the value now.
                }
            }
            launchMotion {
                rim.animateTo(1f, ptsTween(PtsTiming.thinkIn))
            }
        }

        thinkJob = launchMotion {
            try {
                think.animateTo(1f, ptsTween(PtsTiming.thinkIn))
                wait.animateTo(PtsTiming.thinkMs.toFloat(), tween(PtsTiming.thinkMs.toInt(), easing = LinearEasing))
                if (jobToken != token || mode != PtsMode.Thinking) return@launchMotion
                think.animateTo(1f, ptsTween(PtsTiming.tick))
                if (QA_HOLD != "a") arrive(token, scripted)
            } catch (_: CancellationException) {
                // Restoring or another collapse superseded this answer.
            }
        }
    }

    private suspend fun restore(velocity: Float, scripted: Boolean, targetScroll: Float? = null) {
        thinkJob?.cancel()
        thinkJob = null
        ++jobToken
        changeMode(PtsMode.Reading)
        buzz(PtsHaptic.Restore, scripted)
        launchMotion {
            think.animateTo(0f, ptsTween(PtsTiming.reducedFade))
        }
        launchMotion {
            summaryAlpha.animateTo(0f, ptsTween(PtsTiming.reducedFade))
        }
        launchMotion { dim.animateTo(0f, ptsTween(PtsTiming.reducedFade)) }
        launchMotion { wait.animateTo(0f, ptsTween(PtsTiming.reducedFade)) }
        launchMotion {
            markAlpha.animateTo(1f, ptsTween(PtsMark.fadeDelay))
            markAlpha.animateTo(0f, ptsTween(PtsMark.fadeMs))
            mark.snapTo(0f)
            markAlpha.snapTo(1f)
        }
        launchMotion { driftX.animateTo(0f, ptsSpring(PtsSpring.drift)) }
        launchMotion { driftY.animateTo(0f, ptsSpring(PtsSpring.drift)) }
        launchMotion {
            readScroll.animateTo(
                targetScroll ?: savedScroll,
                tween(PtsFlight.scrollMs.toInt(), easing = ptsInOutEasing),
            )
        }

        if (reduceMotion) {
            rim.snapTo(0f)
            launchMotion {
                pageAlpha.animateTo(0f, ptsTween(PtsTiming.reducedFade))
                p.snapTo(0f)
                settleAtArticle()
                pageAlpha.animateTo(1f, ptsTween(PtsTiming.reducedFade))
            }
            return
        }

        launchMotion {
            rim.animateTo(
                PtsRim.pinchMax + PtsRim.armedBoost,
                ptsTween(PtsTiming.rimFlashIn),
            )
            rim.animateTo(0f, ptsTween(PtsTiming.rimFlashOut))
        }
        launchMotion {
            try {
                val releaseVelocity = -velocity * PtsPinch.velocityToProgress
                p.animateTo(
                    targetValue = 0f,
                    animationSpec = ptsSpring(PtsSpring.restore, releaseVelocity),
                    initialVelocity = releaseVelocity,
                )
                settleAtArticle()
            } catch (_: CancellationException) {
                // A new pinch owns the value now.
            }
        }
    }

    private suspend fun settleAtArticle() {
        lift.snapTo(0f)
        reveal.snapTo(0f)
        isScrollLocked = false
    }

    internal suspend fun pinchBegin(focalX: Float, focalY: Float, scripted: Boolean) {
        if (!ready) return
        p.stop()
        driftX.stop()
        driftY.stop()
        pinchState = PtsPinchState(
            active = true,
            out = mode != PtsMode.Reading,
            startP = p.value,
            focalX = focalX,
            focalY = focalY,
            track = p.value,
            scripted = scripted,
        )
        if (!pinchState.out) {
            isScrollLocked = true
            mark.stop()
            mark.snapTo(0f)
            markAlpha.snapTo(1f)
            dim.snapTo(0f)
            level = 0
            levelPos.snapTo(0f)
        }
    }

    internal suspend fun pinchMove(scale: Float, focalX: Float, focalY: Float) {
        val gesture = pinchState
        if (!gesture.active) return
        val raw = if (gesture.out) {
            gesture.startP - (scale - 1f) / (PtsPinch.fullOut - 1f)
        } else {
            gesture.startP + (1f - scale) / (1f - PtsPinch.fullIn)
        }
        val track = ptsRubber(raw)
        val armed = if (gesture.out) track < PtsPinch.restoreAt else track > PtsPinch.commitAt
        pinchState = gesture.copy(track = track, armed = armed)
        if (!gesture.out) {
            val progress = ptsClamp01(track / PtsMark.span)
            val nextMark = (progress / PtsMark.step).coerceAtMost(PtsMark.count.toFloat())
            val oldLocked = mark.value.toInt()
            val newLocked = nextMark.toInt()
            if (newLocked > oldLocked) buzz(PtsHaptic.Lock, gesture.scripted)
            mark.snapTo(if (reduceMotion) newLocked.toFloat() else nextMark)
            dim.snapTo(progress * PtsMark.dimMax)
            val wanted = when {
                track > PtsDepth.tldr -> 2
                track > PtsDepth.brief -> 1
                else -> 0
            }
            if (wanted != level) {
                val edge = if (maxOf(wanted, level) == 2) PtsDepth.tldr else PtsDepth.brief
                if (abs(track - edge) >= PtsDepth.hysteresis) {
                    level = wanted
                    levelPos.snapTo(wanted.toFloat())
                    buzz(PtsHaptic.Level, gesture.scripted)
                }
            }
            depthAlpha.snapTo(ptsClamp01((track - PtsDepth.showFrom) / PtsDepth.showSpan))
        }
        if (gesture.out) summaryAlpha.snapTo(ptsMix(1f, .5f, ptsClamp01((1f - track) / (1f - PtsPinch.restoreAt))))
        if (reduceMotion) return

        p.snapTo(track)
        driftX.snapTo((focalX - gesture.focalX) * PtsPinch.follow)
        driftY.snapTo((focalY - gesture.focalY) * PtsPinch.follow)
        if (gesture.out) {
            rim.snapTo(if (armed) PtsRim.pinchMax + PtsRim.armedBoost else 0f)
        } else {
            rim.snapTo(
                ptsClamp01(track / PtsPinch.commitAt) * PtsRim.pinchMax +
                    if (armed) PtsRim.armedBoost else 0f,
            )
        }
    }

    internal suspend fun pinchEnd(velocity: Float, cancelled: Boolean) {
        val gesture = pinchState
        if (!gesture.active) return
        pinchState = gesture.copy(active = false)
        val track = gesture.track
        if (!gesture.out) {
            val projected = track + ptsProject(-velocity / (1f - PtsPinch.fullIn))
            val commit = !cancelled && projected > PtsPinch.commitAt
            if (commit) {
                collapse(velocity, gesture.scripted)
            } else {
                isScrollLocked = false
                launchMotion {
                    rim.animateTo(0f, ptsTween(PtsTiming.rimLetGo))
                }
                launchMotion {
                    val releaseVelocity = -velocity * PtsPinch.velocityToProgress
                    p.animateTo(
                        targetValue = 0f,
                        animationSpec = ptsSpring(PtsSpring.restore, releaseVelocity),
                        initialVelocity = releaseVelocity,
                    )
                }
                launchMotion { driftX.animateTo(0f, ptsSpring(PtsSpring.drift)) }
                launchMotion { driftY.animateTo(0f, ptsSpring(PtsSpring.drift)) }
                launchMotion { dim.animateTo(0f, ptsTween(PtsMark.cancelMs)) }
                launchMotion {
                    markAlpha.animateTo(0f, ptsTween(PtsMark.cancelMs))
                    mark.snapTo(0f)
                    markAlpha.snapTo(1f)
                }
            }
            return
        }

        val projected = track + ptsProject(-velocity / (PtsPinch.fullOut - 1f))
        val restoreArticle = !cancelled && projected < PtsPinch.restoreAt
        if (restoreArticle) {
            restore(velocity, gesture.scripted)
        } else {
            launchMotion {
                rim.animateTo(0f, ptsTween(PtsTiming.rimLetGo))
            }
            launchMotion {
                summaryAlpha.animateTo(
                    targetValue = if (mode == PtsMode.Summary) 1f else 0f,
                    animationSpec = ptsTween(PtsTiming.reducedFade),
                )
            }
            launchMotion {
                val releaseVelocity = -velocity * PtsPinch.velocityToProgress
                p.animateTo(
                    targetValue = 1f,
                    animationSpec = ptsSpring(PtsSpring.collapse, releaseVelocity),
                    initialVelocity = releaseVelocity,
                )
            }
            launchMotion { driftX.animateTo(0f, ptsSpring(PtsSpring.drift)) }
            launchMotion { driftY.animateTo(0f, ptsSpring(PtsSpring.drift)) }
        }
    }

    internal suspend fun touch(down: Boolean) {
        if (down && !script.paused && pinchState.active && pinchState.scripted) {
            pinchEnd(velocity = 0f, cancelled = true)
        }
        if (down) {
            launchMotion {
                fingerAlpha.animateTo(0f, ptsTween(PtsTiming.fingerFade))
            }
        }
        script.paused = true
        script.realDown = down
        script.quiet = 0f
        script.segment = 0
        script.returningToRest = false
    }

    private suspend fun placeFingers(centerX: Float, centerY: Float, spread: Float) {
        val dx = 0.36f * spread / 2f
        val dy = 0.93f * spread / 2f
        fingerAX.snapTo(centerX + dx)
        fingerAY.snapTo(centerY - dy)
        fingerBX.snapTo(centerX - dx)
        fingerBY.snapTo(centerY + dy)
    }

    private suspend fun runScript(deltaMs: Float) {
        val time = script.time
        val readY = when {
            time < PtsAutoplay.readStart -> 0f
            time < PtsAutoplay.readStart + PtsAutoplay.readMs -> {
                val u = ptsEase((time - PtsAutoplay.readStart) / PtsAutoplay.readMs)
                PtsAutoplay.readTarget * u
            }
            time < PtsAutoplay.topStart -> PtsAutoplay.readTarget
            time < PtsAutoplay.topStart + PtsAutoplay.topMs -> {
                val u = ptsEase((time - PtsAutoplay.topStart) / PtsAutoplay.topMs)
                PtsAutoplay.readTarget * (1f - u)
            }
            else -> 0f
        }
        if (mode == PtsMode.Reading) readScroll.snapTo(readY)
        val inPinch = time >= PtsAutoplay.pinchInStart.toFloat() &&
            time < PtsAutoplay.pinchInEnd.toFloat()
        val outPinch = time >= PtsAutoplay.pinchOutStart.toFloat() &&
            time < PtsAutoplay.pinchOutEnd.toFloat()
        val segment = if (inPinch) 1 else if (outPinch) 2 else 0
        val centerX = stage.width / 2f
        val centerYIn = stage.height * 0.46f
        val centerYOut = PtsLayout.summaryTop + PtsLayout.cardHeight / 2f

        if (segment != script.segment) {
            if (script.segment != 0) {
                if (script.segment == 1) {
                    placeFingers(centerX, centerYIn, PtsAutoplay.spreadIn * PtsAutoplay.pinchInScale)
                    pinchMove(PtsAutoplay.pinchInScale, centerX, centerYIn)
                } else {
                    placeFingers(centerX, centerYOut, PtsAutoplay.spreadOut * PtsAutoplay.pinchOutScale)
                    pinchMove(PtsAutoplay.pinchOutScale, centerX, centerYOut)
                }
                pinchEnd(velocity = 0f, cancelled = false)
                launchMotion {
                    fingerAlpha.animateTo(0f, ptsTween(PtsTiming.fingerFade))
                }
            }
            if (segment != 0) {
                val centerY = if (segment == 1) centerYIn else centerYOut
                val spread = if (segment == 1) PtsAutoplay.spreadIn else PtsAutoplay.spreadOut
                placeFingers(centerX, centerY, spread)
                launchMotion {
                    fingerAlpha.animateTo(1f, ptsTween(PtsTiming.fingerFade))
                }
                pinchBegin(centerX, centerY, scripted = true)
            }
        }

        if (segment == 1) {
            val u = ptsEase(
                (time - PtsAutoplay.pinchInStart.toFloat()) /
                    (PtsAutoplay.pinchInEnd - PtsAutoplay.pinchInStart).toFloat(),
            )
            val scale = ptsMix(1f, PtsAutoplay.pinchInScale, u)
            placeFingers(centerX, centerYIn, PtsAutoplay.spreadIn * scale)
            pinchMove(scale, centerX, centerYIn)
        } else if (segment == 2) {
            val u = ptsEase(
                (time - PtsAutoplay.pinchOutStart.toFloat()) /
                    (PtsAutoplay.pinchOutEnd - PtsAutoplay.pinchOutStart).toFloat(),
            )
            val scale = ptsMix(1f, PtsAutoplay.pinchOutScale, u)
            placeFingers(centerX, centerYOut, PtsAutoplay.spreadOut * scale)
            pinchMove(scale, centerX, centerYOut)
        }

        var next = time + deltaMs
        if (QA_HOLD == "rest") next = 0f
        else if (QA_HOLD != null && time >= PtsAutoplay.qaHoldAt.toFloat()) next = time
        if (next >= LOOP.toFloat()) {
            next -= LOOP.toFloat()
            clock.snapTo(0f)
            readScroll.snapTo(0f)
        }
        script.time = next
        script.segment = segment
    }

    internal suspend fun runFrames() {
        var previousNanos = 0L
        while (scope.isActive) {
            val now = withFrameNanos { it }
            val rawDelta = if (previousNanos == 0L) 0f else (now - previousNanos) / 1_000_000f
            previousNanos = now
            val deltaMs = rawDelta.coerceAtMost(PtsTiming.maxFrameMs.toFloat())
            if (!ready) continue

            if (!reduceMotion && (p.value > 0.001f || think.value > 0f)) {
                clock.snapTo(clock.value + deltaMs)
            }
            // Product requirement: Reduced Motion pauses the deterministic preview completely.
            if (!autoPlay || reduceMotion) continue

            if (script.paused) {
                script.quiet = if (script.realDown) 0f else script.quiet + deltaMs
                val settledAtArticle = mode == PtsMode.Reading && !pinchState.active && abs(p.value) < 0.002f

                if (!script.returningToRest && script.quiet >= PtsAutoplay.resumeDelayMs) {
                    script.returningToRest = true
                    if (mode != PtsMode.Reading || !settledAtArticle) {
                        restore(velocity = 0f, scripted = true)
                    }
                }
                if (script.returningToRest && settledAtArticle) {
                    script.time = PtsAutoplay.resumeReading.toFloat()
                    script.paused = false
                    script.realDown = false
                    script.quiet = 0f
                    script.segment = 0
                    script.returningToRest = false
                }
                continue
            }
            runScript(deltaMs)
        }
    }
}

@Composable
internal fun rememberPtsState(
    stage: PtsStage,
    autoPlay: Boolean,
    reduceMotion: Boolean,
    revealTotal: Float,
    ready: Boolean,
): PtsState {
    val scope = rememberCoroutineScope()
    val state = remember { PtsState(scope) }
    SideEffect {
        state.update(
            stage = stage,
            autoPlay = autoPlay,
            reduceMotion = reduceMotion,
            revealTotal = revealTotal,
            ready = ready,
        )
    }
    LaunchedEffect(state) {
        state.runFrames()
    }
    return state
}

internal fun Modifier.ptsGestures(state: PtsState): Modifier = composed {
    val view = LocalView.current
    val inputDensity = LocalDensity.current.density
    SideEffect {
        state.installFeedback(
            haptic = { kind ->
                val feedback = when (kind) {
                    PtsHaptic.Lock,
                    PtsHaptic.Level,
                    -> HapticFeedbackConstants.CLOCK_TICK

                    PtsHaptic.Commit -> HapticFeedbackConstants.CONTEXT_CLICK
                    PtsHaptic.Ready -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.CONTEXT_CLICK
                    }

                    PtsHaptic.Restore,
                    PtsHaptic.Lift,
                    -> HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(feedback)
            },
            announcement = { view.announceForAccessibility("Summary ready") },
        )
    }
    DisposableEffect(state, view) {
        onDispose { state.clearFeedback() }
    }

    pointerInput(state, inputDensity) {
        coroutineScope {
            val events = Channel<PtsGestureEvent>(capacity = Channel.UNLIMITED)
            val processor = launch {
                for (event in events) {
                    when (event) {
                        PtsGestureEvent.TouchDown -> state.touch(down = true)
                        PtsGestureEvent.TouchUp -> state.touch(down = false)
                        is PtsGestureEvent.Begin -> state.pinchBegin(event.x, event.y, scripted = false)
                        is PtsGestureEvent.Move -> state.pinchMove(event.scale, event.x, event.y)
                        is PtsGestureEvent.End -> state.pinchEnd(event.velocity, event.cancelled)
                    }
                }
            }
            try {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    events.trySend(PtsGestureEvent.TouchDown)
                    var started = false
                    var ended = false
                    var initialSpan = 1f
                    var previousScale = 1f
                    var previousTime = 0L
                    var velocity = 0f

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (started && !ended) {
                                    events.trySend(PtsGestureEvent.End(velocity, cancelled = false))
                                    ended = true
                                }
                                break
                            }

                            if (!started && pressed.size >= 2) {
                                val first = pressed[0].position
                                val second = pressed[1].position
                                initialSpan = (first - second).getDistance().coerceAtLeast(1f)
                                previousTime = event.changes.maxOf { it.uptimeMillis }
                                events.trySend(
                                    PtsGestureEvent.Begin(
                                        x = (first.x + second.x) / (2f * inputDensity),
                                        y = (first.y + second.y) / (2f * inputDensity),
                                    ),
                                )
                                started = true
                            }

                            if (started && !ended) {
                                if (pressed.size < 2) {
                                    events.trySend(PtsGestureEvent.End(velocity, cancelled = false))
                                    ended = true
                                    continue
                                }
                                val first = pressed[0].position
                                val second = pressed[1].position
                                val scale = (first - second).getDistance() / initialSpan
                                val time = event.changes.maxOf { it.uptimeMillis }
                                val deltaSeconds = ((time - previousTime).coerceAtLeast(1L)) / 1_000f
                                velocity = (scale - previousScale) / deltaSeconds
                                previousScale = scale
                                previousTime = time
                                events.trySend(
                                    PtsGestureEvent.Move(
                                        scale = scale,
                                        x = (first.x + second.x) / (2f * inputDensity),
                                        y = (first.y + second.y) / (2f * inputDensity),
                                    ),
                                )
                                for (change in pressed) change.consume()
                            }
                        }
                    } finally {
                        if (started && !ended) {
                            events.trySend(PtsGestureEvent.End(velocity = 0f, cancelled = true))
                        }
                        events.trySend(PtsGestureEvent.TouchUp)
                    }
                }
            } finally {
                events.close()
                processor.join()
            }
        }
    }
}

private fun ptsBand(over: Float): Float =
    (over * PtsPinch.rubber) / (1f + PtsPinch.rubber * abs(over))

private fun ptsRubber(raw: Float): Float = when {
    raw > 1f -> 1f + ptsBand(raw - 1f)
    raw < 0f -> -ptsBand(-raw)
    else -> raw
}

private fun ptsProject(velocity: Float): Float =
    ((velocity / 1_000f) * PtsPinch.deceleration) / (1f - PtsPinch.deceleration)

private fun ptsEase(value: Float): Float {
    return ptsInOutEasing.transform(ptsClamp01(value))
}

private val ptsTimingEasing = CubicBezierEasing(.23f, 1f, .32f, 1f)
private val ptsInOutEasing = CubicBezierEasing(.77f, 0f, .175f, 1f)
private val ptsSheetEasing = CubicBezierEasing(.32f, .72f, 0f, 1f)

internal fun ptsSheetEase(value: Float): Float = ptsSheetEasing.transform(ptsClamp01(value))

private fun ptsTween(durationMillis: Number) = tween<Float>(
    durationMillis = durationMillis.toInt(),
    easing = ptsTimingEasing,
)

private fun ptsSpring(
    config: PtsSpring.Config,
    configuredVelocity: Float = 0f,
) = spring<Float>(
    dampingRatio = config.dampingRatio,
    stiffness = (600_000f / (config.duration * config.duration)).coerceIn(80f, 500f),
)
