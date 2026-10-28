package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

/** The same one-clock applause contract used by the approved RN paper, light and shimmer. */
internal object TrophySavingsMilestoneCelebration {
    const val SETTLE = 0.96
    data class Frame(val clock: Double = 0.0, val progress: Double = 0.0,
        val wakeStarts: DoubleArray = DoubleArray(18) { -1.0 }, val contactStart: Double = -1.0,
        val generation: Int = 0, val reduced: Boolean = false, val active: Boolean = false, val cadence: Double = 1.0)
    private val thresholds = doubleArrayOf(0.12,0.23,0.08,0.18,0.76,0.91,0.28,0.4,0.2,0.62,0.74,0.54,0.11,0.83,0.04,0.84,0.29,0.16)
    private val loopStarts = DoubleArray(18) { index ->
        var low = 0.6; var high = 1.7
        repeat(15) {
            val mid = (low + high) * 0.5
            if (TrophySavingsMilestoneMotion.loop(mid) < thresholds[index]) low = mid else high = mid
        }
        (low + high) * 0.5
    }
    fun rest(generation: Int = 0, reduced: Boolean = false) = Frame(generation = generation, reduced = reduced)
    fun isSettled(frame: Frame): Boolean {
        if (!frame.active || frame.reduced) return true
        if (frame.progress > 0.000001) return false
        val last = maxOf(frame.contactStart, frame.wakeStarts.maxOrNull() ?: -1.0)
        return last < 0 || frame.clock >= last + SETTLE * frame.cadence
    }
    fun gallery(time: Double, generation: Int, reduced: Boolean = false): Frame {
        if (reduced) return rest(generation, true)
        val clock = ((time % 5) + 5) % 5
        return Frame(clock, TrophySavingsMilestoneMotion.loop(clock),
            DoubleArray(18) { if (clock >= loopStarts[it]) loopStarts[it] else -1.0 },
            if (clock >= 1.53) 1.53 else -1.0, generation, false, clock >= 0.6 && clock < 4.4, 2.4)
    }
    fun advance(previous: Frame, clock: Double, progress: Double, generation: Int): Frame {
        val p = TrophySavingsMilestoneMotion.clamp(progress)
        val forward = p > previous.progress + 0.000001
        var starts = previous.wakeStarts
        if (forward) for (i in 0 until 18) if (starts[i] < 0 && p >= thresholds[i]) {
            if (starts === previous.wakeStarts) starts = starts.copyOf()
            starts[i] = clock
        }
        return previous.copy(clock = clock, progress = p, generation = generation, wakeStarts = starts,
            contactStart = if (previous.contactStart < 0 && forward && p >= 0.88) clock else previous.contactStart,
            reduced = false, active = true)
    }
}
