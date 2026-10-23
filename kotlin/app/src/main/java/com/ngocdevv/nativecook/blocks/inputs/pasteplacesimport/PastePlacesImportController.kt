package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.FILLED_AT
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.FOUND_AT
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.LOOP
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.PASTE_AT
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Presses
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.REDUCED_FADE
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.RESET_AT
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.RESUME_AFTER
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.REST_AGAIN
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.START_AT
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class PastePlacesImportController(autoPlay: Boolean, reducedMotion: Boolean) {
    private enum class Mode { IDLE, PLAY, AUTO }

    enum class Haptic { NONE, LIGHT, SUCCESS }

    data class TickResult(val changed: Boolean, val haptic: Haptic = Haptic.NONE)

    var time: Double = 0.0
        private set
    var reducedOpacity: Double = 1.0
        private set
    var pastePress: Double = 0.0
        private set
    var startPress: Double = 0.0
        private set
    var closePress: Double = 0.0
        private set

    val scriptedPresses: Boolean
        get() = mode == Mode.AUTO

    /** The text area holds the user's own text (typed or pasted). */
    var hasDraft = false

    /** The text area has the keyboard; autoPlay waits while it does or while it holds text. */
    var focused = false

    /**
     * Paste: the clipboard's text goes into the text area and the call returns true; false (empty clipboard)
     * pastes the sample note, animated. Set by the host.
     */
    var onPaste: () -> Boolean = { false }

    /** Start import with the user's own text: the host clears the text area (the demo imports the sample). */
    var onStartOwn: () -> Unit = {}

    private var mode = if (autoPlay && !reducedMotion) Mode.AUTO else Mode.IDLE
    private var autoPlayEnabled = autoPlay
    private var reduced = reducedMotion
    private var stopAt = FILLED_AT
    private var speed = 1.0
    private var thenReset = false
    private var byHand = false
    private var holding = false
    private var lifecycleActive = true
    private var downWasAuto = false
    private var downTarget = 0
    private var downAtNanos = 0L
    private var touchedAtNanos = Long.MIN_VALUE / 2
    private var lastTickNanos = 0L
    private var reducedFadeStartNanos: Long? = null
    private var reducedFadeStage = 0
    private var reducedFadeFrom = 1.0
    private var reducedTarget = 0.0
    private val pasteAnimation = PressAnimation()
    private val startAnimation = PressAnimation()
    private val closeAnimation = PressAnimation()

    fun setLifecycleActive(active: Boolean) {
        lifecycleActive = active
        holding = false
        pasteAnimation.snap(0.0)
        startAnimation.snap(0.0)
        closeAnimation.snap(0.0)
        pastePress = 0.0
        startPress = 0.0
        closePress = 0.0
        lastTickNanos = 0L
    }

    fun setAutoPlay(enabled: Boolean, nowNanos: Long) {
        autoPlayEnabled = enabled
        if (!reduced && enabled) {
            mode = Mode.AUTO
            lastTickNanos = nowNanos
        } else if (!enabled && mode == Mode.AUTO) {
            mode = Mode.IDLE
        }
    }

    fun setReducedMotion(enabled: Boolean, nowNanos: Long) {
        if (reduced == enabled) return
        reduced = enabled
        reducedFadeStartNanos = null
        reducedFadeStage = 0
        reducedFadeFrom = 1.0
        reducedOpacity = 1.0
        holding = false
        time = 0.0
        mode = if (!enabled && autoPlayEnabled) Mode.AUTO else Mode.IDLE
        lastTickNanos = nowNanos
    }

    fun touchDown(nowNanos: Long, target: Int): Boolean {
        if (!lifecycleActive) return false
        holding = true
        downWasAuto = mode == Mode.AUTO
        if (downWasAuto) mode = Mode.IDLE
        downTarget = target
        downAtNanos = nowNanos
        touchedAtNanos = nowNanos
        lastTickNanos = nowNanos
        if (!reduced && enabled(target, time)) {
            pressAnimation(target).animateTo(1.0, nowNanos, PastePlacesImportMotion.Press.inSeconds)
        }
        updatePresses(nowNanos)
        return true
    }

    fun touchUp(nowNanos: Long, target: Int): Haptic {
        if (!holding) return Haptic.NONE
        holding = false
        touchedAtNanos = nowNanos
        lastTickNanos = nowNanos
        releasePresses(nowNanos)
        val duration = (nowNanos - downAtNanos) / 1_000_000_000.0
        val accepted = target == downTarget && duration <= 0.8
        val wasAuto = downWasAuto
        downWasAuto = false
        downTarget = 0
        if (!accepted) return Haptic.NONE
        if (reduced) {
            if (enabled(target, time)) {
                if (target == 1 && onPaste()) return Haptic.NONE
                if (target == 2 && hasDraft && atRest(time)) onStartOwn()
                startReducedFade(nowNanos, target)
            }
            return Haptic.NONE
        }
        if (enabled(target, time)) return commit(target)
        if (wasAuto) finishAutomaticMove()
        return Haptic.NONE
    }

    fun touchCancel(nowNanos: Long) {
        holding = false
        downWasAuto = false
        downTarget = 0
        touchedAtNanos = nowNanos
        lastTickNanos = nowNanos
        releasePresses(nowNanos)
    }

    fun tick(nowNanos: Long): TickResult {
        if (!lifecycleActive) return TickResult(false)
        val oldTime = time
        val oldOpacity = reducedOpacity
        val oldPaste = pastePress
        val oldStart = startPress
        val oldClose = closePress
        updatePresses(nowNanos)

        if (reduced) {
            updateReducedFade(nowNanos)
            val changed = differs(oldOpacity, reducedOpacity) || pressesChanged(oldPaste, oldStart, oldClose)
            return TickResult(changed)
        }

        if (lastTickNanos == 0L) {
            lastTickNanos = nowNanos
            return TickResult(pressesChanged(oldPaste, oldStart, oldClose))
        }
        val elapsed = if (holding) 0.0 else (nowNanos - lastTickNanos).coerceAtLeast(0L) / 1_000_000_000.0
        lastTickNanos = nowNanos
        var success = false

        when (mode) {
            Mode.AUTO -> time = (time + elapsed) % LOOP
            Mode.PLAY -> {
                val previous = time
                time += elapsed * speed
                if (byHand && previous < LAST_LAND && time >= LAST_LAND) success = true
                if ((speed > 0.0 && time >= stopAt) || (speed < 0.0 && time <= stopAt)) {
                    time = stopAt
                    if (thenReset) {
                        thenReset = false
                        time = RESET_AT
                        stopAt = LOOP
                        speed = 1.0
                    } else {
                        if (time >= LOOP || (speed < 0.0 && time <= PASTE_AT)) time = 0.0
                        mode = Mode.IDLE
                    }
                }
            }
            Mode.IDLE -> {
                if (!holding && canResume() && nowNanos - touchedAtNanos >= (RESUME_AFTER * 1_000_000_000.0).toLong()) {
                    mode = Mode.AUTO
                    lastTickNanos = nowNanos
                }
            }
        }

        val oldQuiet = PastePlacesImportMotion.quietAt(oldTime)
        val newQuiet = PastePlacesImportMotion.quietAt(time)
        val phaseChanged = time != oldTime && (newQuiet < 0 || newQuiet != oldQuiet)
        val changed = phaseChanged || pressesChanged(oldPaste, oldStart, oldClose)
        return TickResult(changed, if (success) Haptic.SUCCESS else Haptic.NONE)
    }

    fun shouldTick(nowNanos: Long): Boolean {
        if (!lifecycleActive) return false
        if (hasActivePress(nowNanos)) return true
        if (reduced) return reducedFadeStartNanos != null
        if (holding) return false
        return when (mode) {
            Mode.AUTO, Mode.PLAY -> PastePlacesImportMotion.quietAt(time) < 0
            Mode.IDLE -> canResume() && nowNanos - touchedAtNanos >= (RESUME_AFTER * 1_000_000_000.0).toLong()
        }
    }

    fun nextWakeDelayNanos(nowNanos: Long): Long? {
        if (!lifecycleActive) return null
        if (hasActivePress(nowNanos) || reducedFadeStartNanos != null) return 0L
        if (reduced || holding) return null
        val quietIndex = PastePlacesImportMotion.quietAt(time)
        if ((mode == Mode.AUTO || mode == Mode.PLAY) && quietIndex >= 0) {
            val quietEnd = PastePlacesImportMotion.quiet[quietIndex].end
            val phaseEnd = if (mode == Mode.PLAY) min(quietEnd, stopAt) else quietEnd
            val seconds = ((phaseEnd - time) / abs(speed)).coerceAtLeast(0.0001)
            return (seconds * 1_000_000_000.0).toLong()
        }
        if (mode == Mode.IDLE && canResume()) {
            val wakeAt = touchedAtNanos + (RESUME_AFTER * 1_000_000_000.0).toLong()
            return (wakeAt - nowNanos).coerceAtLeast(0L)
        }
        return null
    }

    private fun canResume(): Boolean = autoPlayEnabled && !hasDraft && !focused

    private fun commit(target: Int): Haptic {
        if (target == 1) {
            if (onPaste()) {
                mode = Mode.IDLE
                return Haptic.LIGHT
            }
            time = PASTE_AT
            stopAt = FILLED_AT
            speed = 1.0
            thenReset = false
            byHand = false
            mode = Mode.PLAY
            return Haptic.LIGHT
        }
        if (target == 2) {
            val own = hasDraft && atRest(time)
            if (own) onStartOwn()
            time = if (own) START_AT else max(time, START_AT)
            stopAt = FOUND_AT
            speed = 1.0
            thenReset = false
            byHand = true
            mode = Mode.PLAY
            return Haptic.NONE
        }
        if (target == 3) {
            byHand = false
            if (time < START_AT) {
                stopAt = PASTE_AT
                speed = -1.6
                thenReset = false
            } else if (time < FOUND_AT) {
                stopAt = FOUND_AT
                speed = 2.5
                thenReset = true
            } else {
                time = max(time, RESET_AT)
                stopAt = LOOP
                speed = 1.0
                thenReset = false
            }
            mode = Mode.PLAY
            return Haptic.LIGHT
        }
        return Haptic.NONE
    }

    private fun finishAutomaticMove() {
        thenReset = false
        byHand = false
        speed = 1.0
        when {
            time > Presses.paste.start && time < FILLED_AT -> {
                stopAt = FILLED_AT
                mode = Mode.PLAY
            }
            time > Presses.start.start && time < FOUND_AT -> {
                stopAt = FOUND_AT
                mode = Mode.PLAY
            }
            time > Presses.close.start -> {
                stopAt = LOOP
                mode = Mode.PLAY
            }
            else -> mode = Mode.IDLE
        }
    }

    private fun startReducedFade(nowNanos: Long, target: Int) {
        updateReducedFade(nowNanos)
        reducedTarget = when (target) {
            1 -> FILLED_AT
            2 -> FOUND_AT
            else -> 0.0
        }
        reducedFadeFrom = reducedOpacity
        reducedFadeStage = 1
        reducedFadeStartNanos = nowNanos
    }

    private fun updateReducedFade(nowNanos: Long) {
        var start = reducedFadeStartNanos ?: return
        var elapsed = (nowNanos - start).coerceAtLeast(0L) / 1_000_000_000.0
        if (reducedFadeStage == 1) {
            if (elapsed < REDUCED_FADE) {
                reducedOpacity = PastePlacesImportMotion.lerp(reducedFadeFrom, 0.0, timingEase(elapsed / REDUCED_FADE))
                return
            }
            time = reducedTarget
            reducedOpacity = 0.0
            reducedFadeStage = 2
            start += (REDUCED_FADE * 1_000_000_000.0).toLong()
            reducedFadeStartNanos = start
            elapsed = (nowNanos - start).coerceAtLeast(0L) / 1_000_000_000.0
        }
        if (reducedFadeStage == 2 && elapsed < REDUCED_FADE) {
            reducedOpacity = timingEase(elapsed / REDUCED_FADE)
            return
        }
        reducedOpacity = 1.0
        reducedFadeStage = 0
        reducedFadeStartNanos = null
    }

    /** Reanimated withTiming's default easing: Easing.inOut(Easing.quad). */
    private fun timingEase(progress: Double): Double {
        val value = progress.coerceIn(0.0, 1.0)
        if (value < 0.5) return 2.0 * value * value
        val inverse = -2.0 * value + 2.0
        return 1.0 - inverse * inverse / 2.0
    }

    private fun enabled(target: Int, phase: Double): Boolean {
        if (target == 1) return atRest(phase)
        if (target == 2) return (phase >= FILLED_AT - 0.3 && phase < START_AT) || (hasDraft && atRest(phase))
        if (target == 3) return phase >= PASTE_AT && phase < REST_AGAIN
        return false
    }

    /** At rest: nothing of the sample flow on screen (the text area is the user's). */
    private fun atRest(phase: Double): Boolean = phase < Presses.paste.start || phase >= REST_AGAIN

    private fun pressAnimation(target: Int): PressAnimation = when (target) {
        1 -> pasteAnimation
        2 -> startAnimation
        else -> closeAnimation
    }

    private fun releasePresses(nowNanos: Long) {
        pasteAnimation.animateTo(0.0, nowNanos, PastePlacesImportMotion.Press.outSeconds)
        startAnimation.animateTo(0.0, nowNanos, PastePlacesImportMotion.Press.outSeconds)
        closeAnimation.animateTo(0.0, nowNanos, PastePlacesImportMotion.Press.outSeconds)
        updatePresses(nowNanos)
    }

    private fun updatePresses(nowNanos: Long) {
        pastePress = pasteAnimation.valueAt(nowNanos)
        startPress = startAnimation.valueAt(nowNanos)
        closePress = closeAnimation.valueAt(nowNanos)
    }

    private fun hasActivePress(nowNanos: Long): Boolean {
        return pasteAnimation.isActive(nowNanos) || startAnimation.isActive(nowNanos) || closeAnimation.isActive(nowNanos)
    }

    private fun pressesChanged(oldPaste: Double, oldStart: Double, oldClose: Double): Boolean {
        return differs(oldPaste, pastePress) || differs(oldStart, startPress) || differs(oldClose, closePress)
    }

    private fun differs(first: Double, second: Double): Boolean = abs(first - second) > 1e-6

    private class PressAnimation {
        private var from = 0.0
        private var to = 0.0
        private var startNanos = 0L
        private var durationNanos = 0L

        fun snap(value: Double) {
            from = value
            to = value
            startNanos = 0L
            durationNanos = 0L
        }

        fun animateTo(target: Double, nowNanos: Long, durationSeconds: Double) {
            from = valueAt(nowNanos)
            to = target
            startNanos = nowNanos
            durationNanos = (durationSeconds * 1_000_000_000.0).toLong()
        }

        fun valueAt(nowNanos: Long): Double {
            if (durationNanos <= 0L) return to
            val progress = ((nowNanos - startNanos).toDouble() / durationNanos).coerceIn(0.0, 1.0)
            return PastePlacesImportMotion.lerp(from, to, PastePlacesImportMotion.easeOut(progress))
        }

        fun isActive(nowNanos: Long): Boolean = durationNanos > 0L && nowNanos - startNanos < durationNanos
    }

    companion object {
        private val LAST_LAND = PastePlacesImportMotion.impactAt(PastePlacesImportTheme.NOTE.size - 2)
    }
}
