package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Motion
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail
import kotlin.math.max

// A join commits immediately. Its visual rewards are scheduled on the shared frame clock,
// so another invitation can replace pending effects without losing the committed friend.
internal fun ReferralState.invite(selected: Int?) {
    if (selected != null && joined(selected)) return
    val at = ReferralMath.nearest(scroll.value)
    var steps = if (selected == null) 0 else ReferralMath.index(selected - at + 3) - 3
    if (selected == null) while (joined(ReferralMath.index(at + steps))) steps++
    val who = ReferralMath.index(at + steps)
    val old = count
    val next = old + 1
    val filled = ReferralMath.tier(next) > ReferralMath.tier(old)
    val route =
        if (selected == null) {
            footerPath
        } else {
            ReferralPath(
                width - Size.actionHeight - actionWidth / 2 - (width / 2 - 66) * (1 - launchingPeek),
                railTop + Size.cy,
                width / 2,
                railTop + Size.cy,
            )
        }
    if (!reduced && route == null) return
    joinedMask = joinedMask or (1 shl who)
    count = next
    hand = false
    announce(who, next, false)
    swap.timing(
        if (next == ReferralPalette.friendCount) 1.0 else 0.0,
        if (reduced) Motion.press else Motion.swap,
        now,
    )
    val closing = if (peek.value > 0) Motion.peekCloseBeforeCatch else 0.0
    changePeek(false)
    clearEffects()
    level.set(ReferralMath.tier(old).toDouble())
    if (reduced) {
        scroll.timing((at + steps).toDouble(), Motion.gentle, now)
        roll(next * ReferralPalette.pointsPerFriend, 0.0)
        badges[who].timing(1.0, Motion.gentle, now)
        arcEnd.timing(
            if (filled && next < ReferralPalette.friendCount) 0.0 else ReferralMath.arc(next),
            Motion.swap,
            now,
        )
        level.timing(ReferralMath.tier(next).toDouble(), Motion.swap, now)
        feedback(if (filled) 2 else 1)
        return
    }
    checkNotNull(route)
    path = route
    fromCapsule = selected != null
    if (fromCapsule) {
        shape.set(0.0)
        label.set(0.0)
        capsuleShape.set(0.0)
        capsuleLabel.set(0.0)
    } else {
        launchingPeek = -1.0
    }
    val travel = if (steps == 0) 0.0 else Motion.travel + (steps - 1) * 60
    val launch = max(max(travel, Motion.collapse), closing - route.launch)
    val landed = launch + route.total
    scroll.spring((at + steps).toDouble(), Motion.inviteSettle, 1.0, now)
    comet.set(-launch / route.total)
    comet.timing(Trail.end, launch + route.total * Trail.end, now, curve = ReferralValue.linear)
    if (fromCapsule) {
        capsuleShape.timing(1.0, Motion.collapse, now)
        capsuleLabel.timing(1.0, Motion.labelOut, now)
    } else {
        shape.timing(1.0, Motion.collapse, now).thenSpring(
            0.0,
            Motion.wave,
            0.8,
            launch + Motion.launchClearance - Motion.collapse,
        )
        label.timing(1.0, Motion.labelOut, now).thenTiming(
            0.0,
            Motion.labelIn,
            launch + Motion.launchClearance - Motion.labelOut,
        )
    }
    badges[who].spring(1.0, Motion.badge, 0.6, now, delay = launch + route.launch + 40)
    roll(next * ReferralPalette.pointsPerFriend, landed - Motion.countLead)
    ripple.timing(0.0, 0.0, now, delay = landed - 30).thenTiming(1.0, Motion.wave, curve = ReferralValue.linear)
    bump.timing(1.0, Motion.pointBump, now, delay = landed).thenSpring(0.0, Motion.pop, 0.55)
    if (ReferralMath.arc(old) == 0.0) arcEnd.set(0.0)
    catchArc = if (filled) 1.0 else ReferralMath.arc(next)
    if (!filled) return
    rewardIndex = ReferralMath.tier(next) - 1
    fillPending = true
    val rewardLaunch = landed + Motion.tierAfter
    val arrive = rewardLaunch + Motion.flight
    flight
        .timing(
            0.0,
            0.0,
            now,
            delay = rewardLaunch,
        ).thenTiming(Trail.end, Motion.flightWithTail, curve = ReferralValue.linear)
    flash.timing(1.0, Motion.labelOut, now, delay = arrive).thenTiming(0.0, Motion.tierFlash)
    crest.timing(0.0, 0.0, now, delay = arrive).thenTiming(1.0, Motion.tierCrest)
    level.spring(ReferralMath.tier(next).toDouble(), Motion.kickReturn, 0.58, now, delay = arrive)
    if (next < ReferralPalette.friendCount) {
        arcStart.timing(1.0, Motion.drain, now, delay = arrive + Motion.drainAfter, curve = ReferralValue.easeInOut)
    }
}

internal fun ReferralState.reset() {
    val at = ReferralMath.nearest(scroll.value)
    val back = ReferralMath.index(2 - at + 3) - 3
    val old = count
    joinedMask = 3
    count = 2
    changePeek(false)
    clearEffects()
    launchingPeek = -1.0
    shape.set(0.0)
    label.set(0.0)
    capsuleShape.set(0.0)
    capsuleLabel.set(0.0)
    comet.set(Trail.end)
    for (i in 2..6) badges[i].timing(0.0, Motion.resetBadge, now)
    roll(50, if (reduced) 0.0 else Motion.press)
    feedback(0)
    swap.timing(0.0, if (reduced) Motion.press else Motion.swap, now)
    if (reduced) {
        scroll.timing((at + back).toDouble(), Motion.gentle, now)
        arcEnd.timing(2.0 / 3, Motion.swap, now)
        level.timing(0.0, Motion.swap, now)
    } else {
        scroll.spring((at + back).toDouble(), Motion.kickReturn, 0.9, now)
        if (ReferralMath.arc(old) == 0.0) arcEnd.set(0.0)
        arcEnd.timing(2.0 / 3, Motion.pop, now, curve = ReferralValue.easeInOut)
        level.timing(0.0, Motion.wave, now)
    }
}
