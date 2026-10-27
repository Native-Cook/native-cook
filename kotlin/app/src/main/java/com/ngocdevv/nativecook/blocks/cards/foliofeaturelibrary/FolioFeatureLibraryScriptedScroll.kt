package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.abs
import kotlin.math.min

/**
 * The block's programmatic `scrollTo({ y, animated })` and `scrollToEnd`, as a chain of moves evaluated at
 * a time instead of a native animation: each move starts from where the previous one ended, so a frozen
 * preview shows the same offset a running loop does. Offsets are in dp.
 *
 * A native animated scroll has no fixed length; the moves here take 0.35 s plus 1 s per 4000 dp, at
 * most 0.7 s, on the strong ease-out. Every gap between two moves of the script is longer than that.
 */
internal class FolioScriptedScroll(
    private val actual: () -> Float,
    private val limit: () -> Float,
) {
    private class Move(val startAt: Double, val target: Float, val toEnd: Boolean, val animated: Boolean)

    private val moves = ArrayList<Move>()
    private var base = 0f

    /** True while the script owns the scroll position; a touch calls [stop]. */
    var driving = false
        private set

    fun scrollTo(target: Float, at: Double, animated: Boolean) {
        begin()
        moves.add(Move(at, target, false, animated))
    }

    fun scrollToEnd(at: Double, animated: Boolean) {
        begin()
        moves.add(Move(at, 0f, true, animated))
    }

    /** `scrollTo({ y: value, animated: false })` from a clean start. */
    fun jump(value: Float) {
        moves.clear()
        base = value
        driving = true
    }

    fun stop() {
        driving = false
        moves.clear()
    }

    private fun begin() {
        if (driving) return
        moves.clear()
        base = actual()
        driving = true
    }

    /** The offset the script puts the scroll view at, in dp, at time [now]. */
    fun valueDp(now: Double): Float {
        val max = limit()
        var position = min(base, max)
        for (move in moves) {
            if (move.startAt > now) break
            val end = if (move.toEnd) max else min(move.target, max)
            val seconds = if (move.animated) min(0.7, 0.35 + abs(end - position) / 4000.0) else 0.0
            if (seconds <= 0.0 || now >= move.startAt + seconds) {
                position = end
                continue
            }
            val fraction = EaseOutStrong.transform(((now - move.startAt) / seconds).toFloat())
            return position + (end - position) * fraction
        }
        return position
    }
}
