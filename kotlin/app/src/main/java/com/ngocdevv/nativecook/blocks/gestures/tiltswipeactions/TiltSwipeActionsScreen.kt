package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

/**
 * What TiltSwipeActions.tsx keeps in state and shared values: the list, the flagged rows, the
 * Undo pill, the balance, the tab and the FAB menu, plus the callbacks the rows and the demo use.
 */
internal class TiltScreen(
    val autoPlay: Boolean,
    private val reduceMotion: Boolean,
    private val scope: CoroutineScope,
    private val playHaptic: (SwipeHaptic) -> Unit,
) {
    val clock = MotionClock()
    lateinit var demo: TiltDemoScript

    var rows by mutableStateOf(SPENDING.map { TiltRow(it) })
        private set
    var flagged by mutableStateOf<Set<String>>(emptySet())
        private set

    /** The deleted row and where it was, for Undo (`last`). */
    private var last: Pair<TiltTransaction, Int>? = null

    /** Kept while the pill fades out after Undo. */
    var pillTitle by mutableStateOf("")
        private set

    private var activeKey = ""
    private val rowStates = HashMap<String, TiltRowState>()

    /** 0 hidden, 1 shown; the pill's spring may overshoot it a little. */
    val pill = MotionValue(0f, clock)

    /** The deleted row's height above the pill, for the morph. */
    val pillFrom = MotionValue(0f, clock)
    private var pillTimer: Job? = null

    private var balance = BALANCE

    /** The balance as shown: it rolls to its new value (RollingAmount.tsx). */
    val shownBalance = MotionValue(BALANCE, clock)

    /** Tabs: 0 Overview (the cards), 2 Spending (the list). */
    var tab by mutableIntStateOf(2)
        private set
    val tabShown = MotionValue(1f, clock) // 1 = spending
    val lensX = MotionValue(TiltFlat.tabs[2], clock)

    var menuOpen by mutableStateOf(false)
        private set
    val menuProgress = MotionValue(0f, clock)

    fun register(state: TiltRowState) {
        rowStates[state.key] = state
    }

    fun unregister(state: TiltRowState) {
        if (rowStates[state.key] === state) rowStates.remove(state.key)
    }

    fun rowState(key: String): TiltRowState? = rowStates[key]

    fun createRowState(row: TiltRow): TiltRowState = TiltRowState(
        key = row.item.key,
        restoring = row.restoring,
        width = FLAT_WIDTH,
        reduceMotion = reduceMotion,
        clock = clock,
        setActiveKey = ::setActiveKey,
        onDelete = ::onDelete,
        playHaptic = playHaptic,
    )

    /** activeKey: the row that owns the touch; any other open row closes. */
    fun setActiveKey(key: String) {
        if (key == activeKey) return
        activeKey = key
        rowStates.values.toList().forEach { it.onActiveKey(key) }
    }

    fun selectTab(index: Int) {
        if (index == 1) return // the wallet tab's screen is not part of this block
        tab = index
        val target = if (index == 2) 1f else 0f
        if (reduceMotion) {
            tabShown.timing(target, TiltTiming.reduced)
        } else {
            tabShown.timing(target, TiltTiming.tab, EASE_OUT)
        }
        if (reduceMotion) {
            lensX.timing(TiltFlat.tabs[index], TiltTiming.reduced)
        } else {
            lensX.spring(TiltFlat.tabs[index], TiltSprings.lens)
        }
    }

    fun showMenu(open: Boolean) {
        menuOpen = open
        val target = if (open) 1f else 0f
        if (reduceMotion) {
            menuProgress.timing(target, TiltTiming.reduced)
        } else {
            menuProgress.spring(target, TiltSprings.menu)
        }
    }

    private fun showPill(on: Boolean) {
        val target = if (on) 1f else 0f
        if (reduceMotion) {
            pill.timing(target, TiltTiming.reduced)
        } else {
            pill.spring(target, TiltSprings.pill)
        }
    }

    private fun setBalance(next: Float) {
        balance = next
        if (reduceMotion) {
            shownBalance.set(next)
        } else {
            shownBalance.timing(next, TiltTiming.roll, EASE_OUT)
        }
    }

    private fun onDelete(key: String) {
        val index = rows.indexOfFirst { it.item.key == key }
        if (index < 0) return
        val item = rows[index].item
        rows = rows.filter { it.item.key != key }
        last = item to index
        pillTitle = item.title
        val rowCentre = TiltFlat.rowsTop + TiltLayout.row * (index + 0.5f)
        val pillCentre = PLANE_H - PILL_BOTTOM - TiltLayout.pillHeight / 2f
        pillFrom.set(rowCentre - pillCentre)
        pill.set(0f)
        showPill(true)
        setBalance(balance + amountOf(item))
        val scripted = autoPlay && !demo.paused
        if (!scripted) playHaptic(SwipeHaptic.Commit)
        pillTimer?.cancel()
        // The demo hides the pill with its own Undo tap, on the loop clock; by hand it times out.
        if (!scripted) {
            pillTimer = scope.launch {
                delay(TiltTiming.undoVisible.toLong())
                showPill(false)
            }
        }
    }

    fun onUndo() {
        val current = last
        if (current != null) {
            setBalance(balance - amountOf(current.first))
            val next = rows.toMutableList()
            next.add(min(current.second, next.size), TiltRow(current.first, restoring = true))
            rows = next
            last = null
        }
        showPill(false)
        pillTimer?.cancel()
    }

    fun onUndoPress() {
        playHaptic(SwipeHaptic.Undo)
        onUndo()
    }

    fun onFlag(key: String) {
        flagged = if (key in flagged) flagged - key else flagged + key
    }

    /** Demo restart: every row back, nothing flagged, no pill. */
    fun onReset() {
        rows = SPENDING.map { TiltRow(it) }
        flagged = emptySet()
        last = null
        setBalance(BALANCE)
        showPill(false)
    }

    fun dispose() {
        pillTimer?.cancel()
    }
}

/** RollingAmount.tsx: the balance rounded to one decimal, without a trailing ".0". */
internal fun formatBalance(value: Float): String {
    val rounded = floor(value * 10f + 0.5f) / 10f
    return String.format(Locale.US, "%.1f", rounded).removeSuffix(".0")
}
