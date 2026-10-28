package com.ngocdevv.nativecook.blocks.onboarding.fighterprofiletour

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

internal val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

// Roster geometry in stage points (dp). Every card is 95; the selection ring sits on top of one.
internal const val ROSTER_TOP = 164f
internal const val ROW_X = 20f
internal const val ROW_WIDTH = 353f
internal const val ROW_HEIGHT = 95f
internal const val ROW_PITCH = 107f
internal const val ART_BOX_X = 206f
internal const val ART_BOX_TOP = -14f
internal val HERO_BOX = StageRect(45.8f, 181.3f, 287.3f, 287.3f)
internal val PINNED_BOX = StageRect(166f, 38.4f, 55f, 55f)
internal val HALO = StageRect(74f, 206f, 246f, 246f)
internal const val STAT_CARD = 118f
internal const val STAT_GAP = 10f
internal val STAT_SCROLL = FIGHTERS[0].stats.size * (STAT_CARD + STAT_GAP) - STAT_GAP - 353f

internal fun rowTop(index: Float) = ROSTER_TOP + index * ROW_PITCH

/** Where a fighter's art sits on its own roster card, in stage points. */
internal fun rowArtRect(fighter: Fighter, index: Int): StageRect {
    val fit = containIn(fighter, fighter.rowFit)
    return StageRect(ART_BOX_X + fit.x, rowTop(index.toFloat()) + ART_BOX_TOP + fit.y, fit.width, fit.height)
}

internal enum class CommitKind { Continue, Skip, Select }

/**
 * All the shared values and actions of the React Native component in one place. Everything the UI
 * reads per frame is a [Motion] or Compose state, so it is read in draw / graphicsLayer lambdas
 * only; actions read the fields of this object when they run, never a copy from an older composition.
 */
@Stable
internal class TourController {
    // Props mirrored each composition.
    var autoPlay = true
    var controlled = false
    var onComplete: (() -> Unit)? = null
    var haptic: () -> Unit = {}

    /** Stage width in screen dp (WIDTH * scale), for drag distance. */
    var stageWidth = WIDTH

    var reduced by mutableStateOf(false)
    var activeStep by mutableIntStateOf(1)
    var completed by mutableStateOf(false)
    var selected by mutableIntStateOf(0)

    val progress = Motion(0f)
    val sweep = Motion(-1f)
    val ringRow = Motion(0f)
    val weights = List(FIGHTERS.size) { Motion(if (it == 0) 1f else 0f) }
    val firstExpanded = Motion(1f)
    val secondExpanded = Motion(1f)
    var firstTarget by mutableFloatStateOf(1f)
    var secondTarget by mutableFloatStateOf(1f)
    val stripManual = Motion(-1f)

    var playing = false
    var clockActive = false
    var routeFocused = false
    private var interrupted = false
    private var resumeDelay = 0f
    private var dragStart = 0f
    private var dragOrigin = 0f
    private var gestureActive = false
    private var stripStart = 0f

    fun time(): Float = sceneTime(progress.value, reduced)

    fun stripOffset(): Float {
        if (stripManual.value >= 0f) return stripManual.value
        return if (reduced) 0f else STAT_SCROLL * ramp(time(), 0.405f, 0.445f, true)
    }

    /** One frame: advance every animation, then the loop clock (the `useFrameCallback`). */
    fun tick(dtMs: Float) {
        val dt = min(dtMs, 100f)
        progress.step(dt)
        sweep.step(dt)
        ringRow.step(dt)
        weights.forEach { it.step(dt) }
        firstExpanded.step(dt)
        secondExpanded.step(dt)
        stripManual.step(dt)
        if (clockActive && playing) {
            val elapsed = min(dtMs, 48f)
            if (resumeDelay > 0f) {
                resumeDelay = maxOf(0f, resumeDelay - elapsed)
            } else {
                progress.set((progress.value + elapsed / (LOOP * 1000f)) % 1f)
            }
        }
        val step = stepAt(time())
        if (step != activeStep) activeStep = step
    }

    private fun springProgress(target: Float, ratio: Float, velocity: Float? = null) {
        if (reduced) progress.set(target) else progress.spring(target, 400f, ratio, velocity, overshootClamping = true)
    }

    fun settleToHold(velocity: Float = 0f, animated: Boolean = true) {
        val target = nearestHold(progress.value, velocity)
        if (!animated) {
            progress.set(target)
            return
        }
        springProgress(target, if (abs(velocity) > 0f) 0.8f else 1f, velocity)
    }

    fun pauseForInterruption() {
        interrupted = true
        gestureActive = false
        clockActive = false
        progress.cancel()
        sweep.cancel()
    }

    fun restoreAfterInterruption() {
        if (interrupted && !controlled) {
            settleToHold(0f, false)
            playing = autoPlay
            resumeDelay = if (autoPlay) 2000f else 0f
        }
        interrupted = false
        clockActive = routeFocused && autoPlay && !controlled
    }

    private fun shimmer() {
        if (!reduced) {
            sweep.set(-0.15f)
            sweep.timing(1.2f, 220f, androidx.compose.animation.core.LinearEasing)
        }
    }

    fun commit(kind: CommitKind) {
        haptic()
        shimmer()
        playing = false
        resumeDelay = 0f
        clockActive = false
        stripManual.set(-1f)
        val raw = progress.value
        val current = sceneTime(raw, false)
        if (raw >= 0.84f) progress.set(current) // Same visible pose, forward manual coordinate.
        val currentStep = stepAt(sceneTime(current, reduced))
        if (currentStep == 3 && kind == CommitKind.Continue) {
            springProgress(0.72f, 1f)
            completed = true
            onComplete?.invoke()
            return
        }
        val target = if (kind == CommitKind.Skip || currentStep != 1) 0.72f else 0.40f
        // Retarget the current pose; retaining the prior spring also retains its velocity.
        springProgress(target, 1f)
    }

    /** Picking another card moves the ring and recolours the page; picking the chosen one starts. */
    fun pick(index: Int) {
        if (index == selected) {
            commit(CommitKind.Select)
            return
        }
        haptic()
        playing = false
        clockActive = false
        resumeDelay = 0f
        selected = index
        completed = false
        if (reduced) ringRow.set(index.toFloat()) else ringRow.spring(index.toFloat(), 380f, 0.82f)
        weights.forEachIndexed { fighter, weight ->
            val to = if (fighter == index) 1f else 0f
            if (reduced) weight.set(to) else weight.timing(to, 420f, EASE_OUT)
        }
    }

    fun toggle(second: Boolean) {
        val expanded = if (second) secondExpanded else firstExpanded
        val target = if (second) secondTarget else firstTarget
        haptic()
        playing = false
        clockActive = false
        if (progress.value >= 0.84f) progress.set(sceneTime(progress.value, false))
        springProgress(0.72f, 1f)
        val next = if (target > 0.5f) 0f else 1f
        if (second) secondTarget = next else firstTarget = next
        if (reduced) expanded.set(next) else expanded.timing(next, 180f, EASE_OUT)
    }

    private fun dragCommitted() {
        clockActive = false
        completed = false
        haptic()
    }

    private fun dragCancelled() {
        clockActive = false
    }

    // Root pan (maxPointers 1, activeOffsetX +-14, failOffsetY +-18).
    fun panStart(translationX: Float) {
        dragOrigin = translationX
        gestureActive = true
        playing = false
        resumeDelay = 0f
        progress.cancel()
        val start = sceneTime(progress.value, false)
        dragStart = start
        progress.set(start)
    }

    fun panUpdate(translationX: Float) {
        progress.set(clamp(dragStart - (translationX - dragOrigin) / stageWidth * 0.72f, 0f, 0.72f))
    }

    fun panEnd(velocityX: Float) {
        gestureActive = false
        settleToHold(clamp(-velocityX / stageWidth * 0.72f, -3f, 3f))
        dragCommitted()
    }

    fun panCancel() {
        // Failed taps never activated this pan and must leave their Pressable action untouched.
        if (gestureActive) {
            gestureActive = false
            settleToHold()
            dragCancelled()
        }
    }

    // Stat strip pan (maxPointers 1, activeOffsetX +-8, failOffsetY +-14).
    fun statsStart() {
        stripStart = stripOffset()
        stripManual.set(stripOffset())
        playing = false
        resumeDelay = 0f
    }

    fun statsUpdate(translationX: Float) {
        val raw = stripStart - translationX
        val edge = clamp(raw, 0f, STAT_SCROLL)
        stripManual.set(edge + (raw - edge) / (1f + abs(raw - edge) / 60f))
    }

    fun statsEnd(velocityX: Float) {
        val projected = stripManual.value - velocityX * 0.18f
        val pitch = STAT_CARD + STAT_GAP
        val snapped = clamp(floor(projected / pitch + 0.5f) * pitch, 0f, STAT_SCROLL)
        if (reduced) stripManual.set(snapped) else stripManual.spring(snapped, 420f, 0.9f, -velocityX)
    }
}
