package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Motion

// Drum targets accumulate instead of wrapping at nine, keeping every roll in its chosen
// direction. Replacing a reward pauses the current digits until the new head arrives.
internal fun ReferralState.roll(
    points: Int,
    delay: Double,
) {
    val rising = points > countValue
    countValue = points
    val columnCount = if (points >= 100) 3 else 2
    if (reduced) {
        columns.timing(columnCount.toDouble(), Motion.gentle, now, delay)
    } else {
        columns.spring(columnCount.toDouble(), Motion.columns, 0.85, now, delay)
    }
    var divisor = 1
    for (i in 0..2) {
        val digit = if (i < columnCount) points / divisor % 10 else -1
        divisor *= 10
        if (digit == digitLast[i]) continue
        val old = digitLast[i]
        digitLast[i] = digit
        turns[i].cancel()
        shown[i].cancel()
        shown[i].timing(if (digit < 0) 0.0 else 1.0, Motion.swap, now, delay + i * Motion.rollStagger)
        if (digit < 0) continue
        if (old < 0) {
            digitTargets[i] = digit.toDouble()
            turns[i].set(digit.toDouble())
            continue
        }
        digitTargets[i] += if (rising) (digit - old + 10) % 10 else -((old - digit + 10) % 10)
        turns[i].timing(
            digitTargets[i],
            if (reduced) Motion.labelIn else Motion.roll,
            now,
            delay + i * Motion.rollStagger,
            if (reduced) ReferralValue.easeOut else ReferralValue.easeInOut,
        )
    }
}
