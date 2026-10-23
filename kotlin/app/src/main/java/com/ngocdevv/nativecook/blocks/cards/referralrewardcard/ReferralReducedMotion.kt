package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Motion
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail

// Switching this preference during a flight keeps committed points but cancels its travel
// and pending impacts. Turning motion back on must never resurrect an earlier reward.
internal fun ReferralState.settleReducedMotion() {
    val destination = scroll.target
    clearEffects()
    path = null
    comet.set(Trail.end)
    launchingPeek = -1.0
    shape.set(0.0)
    label.set(0.0)
    capsuleShape.set(0.0)
    capsuleLabel.set(0.0)
    lock.set(0.0)
    scroll.timing(destination, Motion.gentle, now)
    follower.set(destination)
    peek.timing(if (open) 1.0 else 0.0, Motion.gentle, now)
    enter.timing(Motion.arrivalTotal, Motion.gentle, now)
    press.timing(if (down) press.value else 0.0, Motion.press, now)
    swap.timing(if (count == ReferralPalette.friendCount) 1.0 else 0.0, Motion.press, now)
    arcEnd.timing(ReferralMath.arc(count), Motion.swap, now)
    level.timing(ReferralMath.tier(count).toDouble(), Motion.swap, now)
    for (i in badges.indices) badges[i].timing(if (joined(i)) 1.0 else 0.0, Motion.gentle, now)
    columns.timing(if (count * ReferralPalette.pointsPerFriend >= 100) 3.0 else 2.0, Motion.gentle, now)
    for (i in turns.indices) {
        turns[i].timing(digitTargets[i], Motion.labelIn, now, delay = i * Motion.rollStagger.toDouble())
        shown[i].timing(
            if (digitLast[i] < 0) {
                0.0
            } else {
                1.0
            },
            Motion.swap,
            now,
            delay = i * Motion.rollStagger.toDouble(),
        )
    }
}
