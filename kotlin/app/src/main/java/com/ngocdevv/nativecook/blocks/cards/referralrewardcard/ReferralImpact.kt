package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Motion
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail

// Crossing the catch point triggers one impact. Clearing the scheduled values also clears
// these latches, so an interrupted flight cannot trigger a second catch or reward.
internal fun ReferralState.updateCatch() {
    val route = path
    if (
        !reduced &&
        route != null &&
        !caught &&
        comet.value >= route.launch / route.total &&
        comet.value < Trail.end
    ) {
        caught = true
        feedback(1)
        pop.set(1.0)
        pop.spring(0.0, Motion.pop, 0.8, now)
        wave.set(0.0)
        wave.timing(1.0, Motion.wave, now)
        kick.timing(1.0, Motion.press, now).thenSpring(0.0, Motion.kickReturn, 0.5)
        glow.timing(1.0, Motion.press, now).thenTiming(0.0, Motion.glowOut)
        arcEnd.timing(catchArc, route.orbit, now)
    }
    if (fillPending && !tierCaught && flash.value > 0.05) {
        tierCaught = true
        feedback(2)
    }
}

internal fun ReferralState.clearEffects() {
    pop.set(0.0)
    kick.set(0.0)
    glow.set(0.0)
    flash.set(0.0)
    bump.set(0.0)
    wave.set(1.0)
    crest.set(1.0)
    ripple.set(1.0)
    flight.set(Trail.end)
    arcStart.set(0.0)
    arcEnd.cancel()
    caught = false
    tierCaught = false
    fillPending = false
}
