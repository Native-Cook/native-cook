package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel

/**
 * View state of the block. The per-frame story lives in the controller; `pose` and `emptyAlpha`
 * are Compose state that only the draw and layer phases read, so a frame never recomposes.
 */
internal class StampDiaryCalendarState {
    val controller = StampDiaryCalendarController()
    val wake = Channel<Unit>(Channel.CONFLATED)

    var pose by mutableStateOf(controller.pose)
    var emptyAlpha by mutableFloatStateOf(0f)

    /** The memory the moving layer has rendered (a cell stamp hides only once its actor can show). */
    var actorMemory by mutableIntStateOf(StampDiaryCalendarController.AUTO_MEMORY)
    var emptyDay by mutableIntStateOf(0)
    var month by mutableStateOf(StampDiaryCalendarMotion.MAY_2026)
    var menuOpen by mutableStateOf(false)

    fun wakeUp() {
        wake.trySend(Unit)
    }

    fun configure(autoPlay: Boolean, reduceMotion: Boolean, screenReader: Boolean) {
        controller.autoOn = autoPlay && !reduceMotion && !screenReader
        controller.quiet = reduceMotion
        controller.may = StampDiaryCalendarMotion.isMay2026(month)
        wakeUp()
    }

    fun openMenu(onHaptic: () -> Unit) {
        menuOpen = true
        onHaptic()
    }

    fun go(next: StampDiaryCalendarMotion.Month, onHaptic: () -> Unit) {
        menuOpen = false
        month = next
        emptyDay = 0
        controller.may = StampDiaryCalendarMotion.isMay2026(next)
        onHaptic()
        wakeUp()
    }

    companion object {
        fun clock(): Double = System.nanoTime() / 1e9
    }
}
