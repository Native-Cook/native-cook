package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// The score, in design points and milliseconds. Result callbacks are inline:
// the same pure functions can return test values or fill existing draw storage.
internal object AuraFlow {
    object Drop {
        const val halo = 6.0
        const val haloFull = 9.5
        const val weighDuration = 220.0
        const val weighDampingRatio = 0.6
        const val sag = 6.0
        const val othersScale = 0.92
        const val othersOpacity = 0.55
        const val aside = 3.0
        const val formDuration = 400.0
        const val formDampingRatio = 0.7
    }

    object Pull {
        const val dead = 12.0
        const val perStep = 6.5
        const val amountStep = 10.0
        const val amountMax = 100.0
        const val amountDefault = 60.0
        const val sideways = 28.0
        const val rubber = 0.3
    }

    object Neck {
        const val root = 13.0
        const val rootLong = 19.0
        const val waistShort = 9.0
        const val waistLong = 4.0
        const val blend = 12.0
    }

    object Send {
        const val snap = 140.0
        const val hang = 90.0
        const val gravity = 1700.0
        const val fallMin = 140.0
        const val stretch = 0.12
        const val flatten = 0.22
        const val squash = 50.0
        const val ringDecay = 110.0
        const val ringPeriod = 300.0
        const val absorbAfter = 60.0
        const val absorb = 220.0
        const val sinkBy = 3.0
        const val shrinkTo = 0.86
        const val rippleSpeed = 460.0
        const val rippleLife = 700.0
        const val rimFlash = 2.2
        const val lipKick = 3.0
        const val lipRingDuration = 500.0
        const val lipRingDampingRatio = 0.45
        const val cushionDip = 0.06
        const val cushionRecover = 600.0
        const val waveSpeed = 330.0
        const val waveLife = 520.0
        const val budDelay = 380.0
        const val budFrom = 0.5
        const val budDuration = 520.0
        const val budDampingRatio = 0.55
        const val unlock = 300.0
        const val sink = 300.0
    }

    object Pills {
        const val sendWide = 164.0
        const val receiveRound = 52.0
        const val kiss = 8.0
        const val morphDuration = 400.0
        const val morphDampingRatio = 0.85
    }

    object Nudge {
        const val depth = 4.0
        const val stagger = 45.0
        const val each = 320.0
    }

    object Enter {
        const val hold = 260.0
        const val smooth = 50.0
        const val reveal = 140.0
        const val delay = 60.0
        const val stagger = 55.0
    }

    object Cue {
        const val first = 2400L
        const val every = 6000L
    }

    object Views {
        const val pullSlop = 6.0
        const val routeTravel = 14.0
        const val routeDuration = 220.0
        const val chipFade = 180.0
        const val chipBlur = 2.0
        const val hintFade = 160.0
        const val sentHold = 2000L
        const val arrowExit = 12.0
        const val arrowExitTime = 160.0
        const val arrowEnter = 8.0
        const val arrowEnterTime = 200.0
        const val arrowEnterDelay = 60.0
        const val receiptFade = 180.0
        const val departureFade = 150.0
    }
}

internal const val AURA_LAND_Y = 241.0

internal fun auraSlotX(index: Int) = 181.0 + (index - 2) * AuraPalette.Lip.pitch

internal fun auraEase(value: Double): Double {
    val time = value.coerceIn(0.0, 1.0)
    return time * time * (3 - 2 * time)
}

internal fun auraSpring(elapsed: Double, duration: Double, dampingRatio: Double): Double {
    if (elapsed <= 0) return 0.0
    val time = elapsed / duration
    val omega = 2 * PI
    val zeta = dampingRatio.coerceIn(0.05, 0.999)
    val damped = omega * sqrt(1 - zeta * zeta)
    val envelope = exp(-zeta * omega * time)
    return 1 - envelope * (cos(damped * time) + zeta * omega / damped * sin(damped * time))
}

internal fun auraRubber(travel: Double, limit: Double): Double {
    if (travel <= limit) return travel
    return limit + (travel - limit) * AuraFlow.Pull.rubber
}

internal fun auraPullLimit() = AuraFlow.Pull.dead +
    (AuraFlow.Pull.amountMax / AuraFlow.Pull.amountStep - 0.5) * AuraFlow.Pull.perStep

internal fun auraAmountAt(depth: Double): Double {
    if (depth < AuraFlow.Pull.dead) return 0.0
    val steps = floor((depth - AuraFlow.Pull.dead) / AuraFlow.Pull.perStep) + 1
    return (steps * AuraFlow.Pull.amountStep).coerceIn(AuraFlow.Pull.amountStep, AuraFlow.Pull.amountMax)
}

internal fun auraDepthFor(amount: Double) = AuraFlow.Pull.dead +
    (amount.coerceIn(AuraFlow.Pull.amountStep, AuraFlow.Pull.amountMax) / AuraFlow.Pull.amountStep - 0.5) *
    AuraFlow.Pull.perStep

internal fun auraDropHalo(weight: Double) = AuraFlow.Drop.halo +
    (AuraFlow.Drop.haloFull - AuraFlow.Drop.halo) * weight.coerceIn(0.0, 1.15)

internal fun auraNeckRoot(depth: Double) = AuraFlow.Neck.root +
    (AuraFlow.Neck.rootLong - AuraFlow.Neck.root) * auraEase(depth / auraPullLimit())

internal fun auraSinkDepth(elapsed: Double, depth: Double): Double {
    val time = (elapsed / AuraFlow.Send.sink).coerceIn(0.0, 1.0)
    val answer = 1 - (1 - time) * (1 - time) * (1 - time)
    return depth * (0.2 * answer + 0.8 * time.pow(2.4))
}

internal fun auraEnterLength() = AuraFlow.Enter.delay +
    AuraFlow.Enter.stagger * (AuraPalette.Lip.count - 1) + AuraFlow.Send.budDuration

internal fun auraEnterScale(index: Int, elapsed: Double): Double {
    val since = elapsed - AuraFlow.Enter.delay - index * AuraFlow.Enter.stagger
    if (since <= 0) return 0.0
    return AuraFlow.Send.budFrom + (1 - AuraFlow.Send.budFrom) *
        auraSpring(since, AuraFlow.Send.budDuration, AuraFlow.Send.budDampingRatio)
}

internal fun auraEnterOpacity(index: Int, elapsed: Double) =
    ((elapsed - AuraFlow.Enter.delay - index * AuraFlow.Enter.stagger) / 120).coerceIn(0.0, 1.0)

internal fun auraNeckWaist(depth: Double): Double {
    val stretched = (depth / auraPullLimit()).coerceIn(0.0, 1.0)
    return AuraFlow.Neck.waistShort +
        (AuraFlow.Neck.waistLong - AuraFlow.Neck.waistShort) * auraEase(stretched)
}

internal inline fun <T> auraFriendPose(
    index: Int,
    chosen: Int,
    formed: Double,
    result: (dx: Double, dy: Double, scale: Double, opacity: Double, halo: Double) -> T,
): T {
    if (chosen < 0 || formed == 0.0) return result(0.0, 0.0, 1.0, 1.0, 0.0)
    if (index == chosen) {
        return result(0.0, AuraFlow.Drop.sag * formed, 1.0, 1.0,
            AuraFlow.Drop.halo * formed.coerceIn(0.0, 1.2))
    }
    val away = (index - chosen).toDouble()
    val back = formed.coerceIn(0.0, 1.0)
    return result(
        sign(away) * AuraFlow.Drop.aside * formed / abs(away),
        0.0,
        1 + (AuraFlow.Drop.othersScale - 1) * back,
        1 + (AuraFlow.Drop.othersOpacity - 1) * back,
        0.0,
    )
}

internal inline fun <T> auraLipPose(
    index: Int,
    chosen: Int,
    formed: Double,
    leaving: Int,
    unformed: Double,
    result: (dx: Double, dy: Double, scale: Double, opacity: Double, halo: Double) -> T,
): T {
    if (index == leaving && index != chosen) {
        return result(0.0, AuraFlow.Drop.sag * unformed, 1.0, 1.0,
            AuraFlow.Drop.halo * unformed.coerceIn(0.0, 1.2))
    }
    return auraFriendPose(index, chosen, formed) { dx, dy, scale, opacity, halo ->
        if (index == chosen || leaving < 0) {
            result(dx, dy, scale, opacity, halo)
        } else {
            val back = max(formed, unformed).coerceIn(0.0, 1.0)
            result(dx, dy, 1 + (AuraFlow.Drop.othersScale - 1) * back,
                1 + (AuraFlow.Drop.othersOpacity - 1) * back, halo)
        }
    }
}

internal fun auraNudgeLength() = AuraFlow.Nudge.stagger * (AuraPalette.Lip.count - 1) + AuraFlow.Nudge.each

internal fun auraNudgeDip(index: Int, elapsed: Double): Double {
    val local = (elapsed - index * AuraFlow.Nudge.stagger) / AuraFlow.Nudge.each
    if (elapsed < 0 || local <= 0 || local >= 1) return 0.0
    return AuraFlow.Nudge.depth * sin(PI * local)
}

internal inline fun <T> auraSendMoments(
    fromY: Double,
    result: (fallStart: Double, fallTime: Double, impact: Double, unlock: Double, bud: Double, end: Double) -> T,
): T {
    val fall = max(AURA_LAND_Y - fromY, 0.0)
    val fallTime = max(sqrt(2 * fall / AuraFlow.Send.gravity) * 1000, AuraFlow.Send.fallMin)
    val impact = AuraFlow.Send.hang + fallTime
    val bud = impact + AuraFlow.Send.budDelay
    return result(
        AuraFlow.Send.hang, fallTime, impact, impact + AuraFlow.Send.unlock, bud,
        max(max(bud + AuraFlow.Send.budDuration, impact + AuraFlow.Send.rippleLife),
            max(AuraFlow.Send.snap + AuraFlow.Send.cushionRecover, AuraFlow.Send.snap + AuraFlow.Send.waveLife)),
    )
}

internal inline fun <T> auraSendPose(
    elapsed: Double,
    fromY: Double,
    result: (neck: Double, lipKick: Double, y: Double, tall: Double, wide: Double,
        presence: Double, taken: Double, rippleRadius: Double, rippleStrength: Double,
        rim: Double, cushion: Double, waveRadius: Double, waveStrength: Double,
        bud: Double, budOpacity: Double) -> T,
): T = auraSendMoments(fromY) { fallStart, fallTime, impact, _, bud, _ ->
    val time = max(elapsed, 0.0)
    val falling = ((time - fallStart) / fallTime).coerceIn(0.0, 1.0)
    val sinceImpact = time - impact
    val landed = sinceImpact >= 0
    val y = fromY + (AURA_LAND_Y - fromY) * falling * falling
    val stretch = AuraFlow.Send.stretch * sin(PI * falling)
    val ringing = max(sinceImpact - AuraFlow.Send.squash, 0.0)
    val flat = if (landed) {
        AuraFlow.Send.flatten * auraEase(sinceImpact / AuraFlow.Send.squash) *
            exp(-ringing / AuraFlow.Send.ringDecay) * cos(2 * PI * ringing / AuraFlow.Send.ringPeriod)
    } else 0.0
    val taken = if (landed) auraEase((sinceImpact - AuraFlow.Send.absorbAfter) / AuraFlow.Send.absorb) else 0.0
    val ripple = if (landed) (sinceImpact / AuraFlow.Send.rippleLife).coerceIn(0.0, 1.0) else 0.0
    val sinceSnap = time - AuraFlow.Send.snap
    val wave = (sinceSnap / AuraFlow.Send.waveLife).coerceIn(0.0, 1.0)
    val sinceBud = time - bud
    result(
        1 - auraEase(time / AuraFlow.Send.snap),
        if (sinceSnap > 0) -AuraFlow.Send.lipKick *
            (1 - auraSpring(sinceSnap, AuraFlow.Send.lipRingDuration, AuraFlow.Send.lipRingDampingRatio)) else 0.0,
        y + AuraFlow.Send.sinkBy * taken,
        (1 + stretch - flat) * (1 + (AuraFlow.Send.shrinkTo - 1) * taken),
        (1 - stretch * 0.7 + flat * 0.9) * (1 + (AuraFlow.Send.shrinkTo - 1) * taken),
        1 - taken,
        taken,
        if (landed) AuraFlow.Send.rippleSpeed * sinceImpact / 1000 else 0.0,
        if (landed) (1 - ripple) * (1 - ripple) else 0.0,
        if (landed) 1 + (AuraFlow.Send.rimFlash - 1) * (1 - ripple) * (1 - ripple) else 1.0,
        if (sinceSnap > 0) 1 - AuraFlow.Send.cushionDip * (1 - auraEase(sinceSnap / AuraFlow.Send.cushionRecover))
        else 1 - AuraFlow.Send.cushionDip * auraEase(time / AuraFlow.Send.snap),
        if (sinceSnap > 0) AuraFlow.Send.waveSpeed * sinceSnap / 1000 else 0.0,
        if (sinceSnap > 0) (1 - wave) * (1 - wave) else 0.0,
        if (sinceBud > 0) AuraFlow.Send.budFrom + (1 - AuraFlow.Send.budFrom) *
            auraSpring(sinceBud, AuraFlow.Send.budDuration, AuraFlow.Send.budDampingRatio) else 0.0,
        (sinceBud / 120).coerceIn(0.0, 1.0),
    )
}

internal inline fun <T> auraActionFrames(
    wide: Double,
    result: (settingsX: Double, swapX: Double, receiveX: Double, sendX: Double,
        settingsWidth: Double, swapWidth: Double, receiveWidth: Double, sendWidth: Double, kiss: Double) -> T,
): T {
    val receive = AuraPalette.Actions.receiveWidth +
        (AuraFlow.Pills.receiveRound - AuraPalette.Actions.receiveWidth) * wide
    val send = AuraPalette.Actions.sendWidth +
        (AuraFlow.Pills.sendWide - AuraPalette.Actions.sendWidth) * wide
    val settingsX = AuraPalette.inset
    val swapX = settingsX + AuraPalette.Actions.settingsWidth + AuraPalette.Actions.gap
    val receiveX = swapX + AuraPalette.Actions.swapWidth + AuraPalette.Actions.gap
    val sendX = receiveX + receive + AuraPalette.Actions.gap
    return result(settingsX, swapX, receiveX, sendX,
        AuraPalette.Actions.settingsWidth, AuraPalette.Actions.swapWidth, receive, send,
        AuraFlow.Pills.kiss * sin(PI * wide.coerceIn(0.0, 1.0)))
}
