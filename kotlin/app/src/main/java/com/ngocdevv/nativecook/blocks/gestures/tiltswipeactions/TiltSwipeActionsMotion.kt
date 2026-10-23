package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val TILT_SWIPE_RESUME = 2f
internal const val TILT_SWIPE_TIDY = 0.8f

internal class TiltSwipeActionsRowMotion {
    var pull by mutableFloatStateOf(0f)
    var tx by mutableFloatStateOf(0f)
    var split by mutableIntStateOf(0)
    var flagged by mutableStateOf(false)
    var deleted by mutableStateOf(false)
    var target by mutableIntStateOf(1)
    var pressed = false
    var armed = false
    var dragOriginTx = 0f

    fun reset() {
        pull = 0f
        tx = 0f
        split = 0
        flagged = false
        deleted = false
        target = 1
        pressed = false
        armed = false
        dragOriginTx = 0f
    }
}
internal sealed interface TiltSwipeActionsToast {
    data class Split(val row: Int, val count: Int) : TiltSwipeActionsToast
    data class Delete(val row: Int) : TiltSwipeActionsToast
}

internal class TiltSwipeActionsController(
    private val autoPlay: Boolean,
    val reducedMotion: Boolean,
) {
    val rows = List(Transactions.size) { TiltSwipeActionsRowMotion() }
    var tick by mutableFloatStateOf(0f)
        private set
    var scriptTime by mutableFloatStateOf(0f)
        private set
    var playing = false
        private set
    var touching = false
        private set
    var quiet = if (autoPlay && !reducedMotion) TILT_SWIPE_RESUME else 0f
        private set
    var tidy = if (autoPlay && !reducedMotion) 0f else -1f
        private set
    var toast: TiltSwipeActionsToast? by mutableStateOf(null)
        private set
    var toastAge by mutableFloatStateOf(0f)
        private set
    var menuOpen by mutableStateOf(false)
    var activeRow = -1
        private set

    fun step(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.05f)
        tick += dt
        if (toast != null && !playing) {
            toastAge += dt
            if (toastAge >= 4f) toast = null
        }
        if (!autoPlay || reducedMotion || touching) return
        if (!playing) {
            quiet += dt
            if (tidy < 0f && quiet >= TILT_SWIPE_RESUME) {
                resetRows()
                tidy = 0f
            }
            if (tidy >= 0f) {
                tidy += dt
                if (tidy >= TILT_SWIPE_TIDY) {
                    tidy = -1f
                    scriptTime = 0f
                    playing = true
                }
            }
            return
        }
        scriptTime += dt
        if (scriptTime >= LOOP) {
            scriptTime %= LOOP
            resetRows()
        }
    }

    fun touchStart(row: Int) {
        touching = true
        quiet = 0f
        tidy = -1f
        if (playing) {
            applyScriptAsManual(scriptTime)
            playing = false
        }
        activeRow = row
        if (row in rows.indices) {
            rows.forEachIndexed { index, state -> if (index != row && state.tx < -1f) state.tx = 0f }
            rows[row].pressed = true
            rows[row].dragOriginTx = rows[row].tx
        }
    }

    fun rowForSlot(slot: Int): Int = rows.indices.filterNot { rows[it].deleted }.getOrElse(slot) { -1 }

    fun drag(row: Int, dx: Float) {
        if (row !in rows.indices) return
        val state = rows[row]
        if (dx >= 0f && state.tx >= -1f) {
            val lastDetent = 72f + 3f * 60f
            state.pull = if (dx > lastDetent) lastDetent + (dx - lastDetent) * 0.35f else dx
            state.target = splitTarget(state.pull, state.target)
            state.tx = 0f
        } else {
            var x = min(0f, state.dragOriginTx + dx)
            val soft = -370f * 0.85f
            if (x < soft) x = soft + (x - soft) * 0.35f
            state.tx = x
            state.pull = 0f
            state.armed = -x > 370f * 0.55f
        }
    }

    fun touchEnd(row: Int, dx: Float, velocityX: Float, tapX: Float) {
        if (row in rows.indices) {
            val state = rows[row]
            if (abs(dx) < 8f) {
                settleTap(row, tapX)
            } else if (state.pull > 0f) {
                if (state.target >= 2) commitSplit(row, state.target) else state.pull = 0f
            } else {
                val width = 370f
                val revealed = -state.tx
                if (state.armed || (velocityX < -1400f && revealed > width * 0.4f)) {
                    state.deleted = true
                    state.tx = -400f
                    toast = TiltSwipeActionsToast.Delete(row)
                    toastAge = 0f
                } else if ((revealed > 78f && velocityX < 300f) || velocityX < -500f) {
                    state.tx = TiltSwipeActionsLayout.OPEN_X
                } else {
                    state.tx = 0f
                }
            }
            state.pressed = false
        }
        touching = false
        quiet = 0f
        activeRow = -1
    }

    fun cancelTouch() {
        if (activeRow in rows.indices) {
            val state = rows[activeRow]
            state.pull = 0f
            state.tx = if (-state.tx > 78f) TiltSwipeActionsLayout.OPEN_X else 0f
            state.pressed = false
        }
        touching = false
        quiet = 0f
        activeRow = -1
    }

    fun undo() {
        when (val current = toast) {
            is TiltSwipeActionsToast.Split -> rows[current.row].split = 0
            is TiltSwipeActionsToast.Delete -> {
                rows[current.row].deleted = false
                rows[current.row].tx = 0f
            }
            null -> Unit
        }
        toast = null
        toastAge = 0f
    }

    fun toggleMenu() {
        menuOpen = !menuOpen
    }

    fun closeMenu() {
        menuOpen = false
    }

    private fun settleTap(row: Int, x: Float) {
        val state = rows[row]
        if (state.tx > -1f) return
        val flagLeft = 370f - 144f
        val deleteLeft = flagLeft + 72f
        when {
            x in deleteLeft..(deleteLeft + 72f) -> {
                state.deleted = true
                state.tx = -400f
                toast = TiltSwipeActionsToast.Delete(row)
                toastAge = 0f
            }
            x in flagLeft..deleteLeft -> {
                state.flagged = !state.flagged
                state.tx = 0f
            }
            else -> state.tx = 0f
        }
    }

    private fun commitSplit(row: Int, count: Int) {
        val state = rows[row]
        state.split = count
        state.pull = 0f
        state.target = 1
        toast = TiltSwipeActionsToast.Split(row, count)
        toastAge = 0f
    }

    private fun splitTarget(pull: Float, previous: Int): Int {
        var result = previous.coerceIn(1, 5)
        for (count in 2..5) {
            val at = 72f + (count - 2) * 60f
            if (pull >= at) result = count
            if (pull < at - 8f && result >= count) result = count - 1
        }
        return result.coerceIn(1, 5)
    }

    private fun applyScriptAsManual(time: Float) {
        resetRows()
        if (time >= 2.9f && time < 5.75f) {
            rows[2].split = 3
            toast = TiltSwipeActionsToast.Split(2, 3)
            toastAge = max(0f, time - 3.06f)
        }
        if (time in 3.7f..7.22f) rows[3].tx = if (time < 4.25f) scriptedOpen(time) else TiltSwipeActionsLayout.OPEN_X
    }

    private fun resetRows() {
        rows.forEach(TiltSwipeActionsRowMotion::reset)
        toast = null
        toastAge = 0f
        menuOpen = false
    }

    fun scriptedPull(): Float = scriptedAlong(
        scriptTime,
        floatArrayOf(0.7f, 1.25f, 1.6f, 2.1f, 2.9f),
        floatArrayOf(0f, 100f, 100f, 170f, 170f),
    )

    fun scriptedOpen(time: Float = scriptTime): Float = scriptedAlong(
        time,
        floatArrayOf(3.7f, 4.25f),
        floatArrayOf(0f, -150f),
    )

    fun scriptSplitCount(): Int = when {
        scriptedPull() >= 132f -> 3
        scriptedPull() >= 72f -> 2
        else -> 1
    }

    fun scriptToast(): TiltSwipeActionsToast? =
        if (playing && scriptTime in 3.06f..<5.75f) TiltSwipeActionsToast.Split(2, 3) else toast

    fun scriptToastProgress(): Float {
        if (!playing) return if (toast == null) 0f else min(1f, toastAge / 0.28f) * min(1f, (4f - toastAge) / 0.14f)
        val t = scriptTime
        return when {
            t < 3.06f -> 0f
            t < 3.34f -> smooth((t - 3.06f) / 0.28f)
            t < 5.61f -> 1f
            t < 5.75f -> 1f - smooth((t - 5.61f) / 0.14f)
            else -> 0f
        }
    }

    fun scriptFinger(): Triple<Float, Float, Float>? {
        if (!playing) return null
        val t = scriptTime
        return when {
            t in 0.7f..2.9f -> Triple(16f + 193f + scriptedPull(), 24f, 1f)
            t in 3.7f..4.25f -> Triple(16f + 300f + scriptedOpen(), 0f, 1f)
            t in 5.6f..5.75f -> Triple(-40f, -40f, 1f)
            t in 7.1f..7.22f -> Triple(16f + 120f, 0f, 1f)
            else -> null
        }
    }

    private fun scriptedAlong(time: Float, times: FloatArray, values: FloatArray): Float {
        if (time <= times.first()) return values.first()
        for (index in 1 until times.size) {
            if (time <= times[index]) {
                val u = cosineEase((time - times[index - 1]) / (times[index] - times[index - 1]))
                return lerp(values[index - 1], values[index], u)
            }
        }
        return values.last()
    }
}
