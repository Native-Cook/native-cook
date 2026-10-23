package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import androidx.compose.animation.core.Easing

internal object MoodOrbJarMotion {
    const val STEP = MoodOrbJarTokens.STEP
    const val MAX_STEPS = MoodOrbJarTokens.MAX_STEPS
    const val LOOP_MS = MoodOrbJarTokens.LOOP_MS
    const val RESUME_MS = MoodOrbJarTokens.RESUME_MS
    const val REPLICA_HOLD = MoodOrbJarTokens.REPLICA_HOLD
    const val QA_SPEED = MoodOrbJarTokens.QA_SPEED
}

internal val MoodOrbJarEase = Easing { t ->
    if (t < 0.5f) {
        2f * t * t
    } else {
        -1f + (4f - 2f * t) * t
    }
}

internal const val LOOP = MoodOrbJarMotion.LOOP_MS / 1000.0

internal object MoodOrbJarPile {
    const val COUNT = MoodOrbJarTokens.Pile.count
    const val SEED = MoodOrbJarTokens.Pile.seed
    const val SETTLE_STEPS = MoodOrbJarTokens.Pile.settleSteps
    const val HOME = MoodOrbJarTokens.Pile.home
    const val DAMPING = MoodOrbJarTokens.Pile.damping
    const val PASSES = MoodOrbJarTokens.Pile.passes
    const val REST_SPEED = MoodOrbJarTokens.Pile.restSpeed
    const val REST_TIME = MoodOrbJarTokens.Pile.restTime
    const val COLLISION_SCALE = MoodOrbJarTokens.Pile.collisionScale
}

internal object MoodOrbJarThrow {
    const val GRAVITY = MoodOrbJarTokens.Throw.gravity
    const val APEX = MoodOrbJarTokens.Throw.apex
    const val SOLVED = MoodOrbJarTokens.Throw.solved
    const val MIN_FLICK = MoodOrbJarTokens.Throw.minFlick
    const val LIFT = MoodOrbJarTokens.Throw.lift
    const val SPIN = MoodOrbJarTokens.Throw.spin
    const val REGROW = MoodOrbJarTokens.Throw.regrow
    const val MAX_SPEED = MoodOrbJarTokens.Throw.maxSpeed
}

internal object MoodOrbJarUndo {
    const val THRESHOLD = MoodOrbJarTokens.Undo.threshold
    const val RISE = MoodOrbJarTokens.Undo.rise
    const val FLY = MoodOrbJarTokens.Undo.fly
    const val ROW_STIFFNESS = MoodOrbJarTokens.Undo.rowSpringStiffness
    const val ROW_DAMPING = MoodOrbJarTokens.Undo.rowSpringDamping
}

internal object MoodOrbJarLid {
    const val OPEN = MoodOrbJarTokens.Lid.open
    const val LIFT = MoodOrbJarTokens.Lid.lift
    const val TILT = MoodOrbJarTokens.Lid.tilt
    const val STIFFNESS = MoodOrbJarTokens.Lid.stiffness
    const val DAMPING = MoodOrbJarTokens.Lid.damping
    const val HOLD = MoodOrbJarTokens.Lid.hold
}

internal object MoodOrbJarHaptic {
    const val GRAB = MoodOrbJarTokens.Haptic.grab
    const val THROW = MoodOrbJarTokens.Haptic.throwValue
    const val LAND = MoodOrbJarTokens.Haptic.land
    const val UNDO = MoodOrbJarTokens.Haptic.undo
    const val LAND_SOFT = MoodOrbJarTokens.Haptic.landSoft
    const val LAND_HARD = MoodOrbJarTokens.Haptic.landHard
}

internal object MoodOrbJarSoft {
    const val STIFFNESS = MoodOrbJarTokens.Soft.stiffness
    const val DAMPING = MoodOrbJarTokens.Soft.damping
    const val PER_SPEED = MoodOrbJarTokens.Soft.perSpeed
    const val MAX = MoodOrbJarTokens.Soft.max
    const val LANDING = MoodOrbJarTokens.Soft.landing
    const val TRIGGER = MoodOrbJarTokens.Soft.trigger
    const val HARD = MoodOrbJarTokens.Soft.hard
    const val SOFT = MoodOrbJarTokens.Soft.soft
}

internal object MoodOrbJarTilt {
    const val SLOSH = MoodOrbJarTokens.Tilt.slosh
    const val GLASS = MoodOrbJarTokens.Tilt.glass
}

internal object MoodOrbJarGhost {
    const val DURATION = MoodOrbJarTokens.Ghost.duration
    const val LIFT = MoodOrbJarTokens.Ghost.lift
    const val RISE = MoodOrbJarTokens.Ghost.rise
    const val ARC = MoodOrbJarTokens.Ghost.arc
    const val GROW = MoodOrbJarTokens.Ghost.grow
}

internal object MoodOrbJarRipple {
    const val DURATION = MoodOrbJarTokens.Ripple.duration
    const val RADIUS = MoodOrbJarTokens.Ripple.radius
}

internal object MoodOrbJarKnock {
    const val AMPLITUDE = MoodOrbJarTokens.JarKnock.amp
    const val DECAY = MoodOrbJarTokens.JarKnock.decay
    const val HERTZ = MoodOrbJarTokens.JarKnock.hz
}

internal object MoodOrbJarHop {
    const val EVERY = MoodOrbJarTokens.Hop.every
    const val PHASE = MoodOrbJarTokens.Hop.phase
    const val SPEED = MoodOrbJarTokens.Hop.speed
    const val LAST = MoodOrbJarTokens.Hop.last
}

internal object MoodOrbJarAim {
    const val POINTS = MoodOrbJarTokens.Aim.points
    const val STEP = MoodOrbJarTokens.Aim.step
}

internal object MoodOrbJarReduced {
    const val FADE = MoodOrbJarTokens.Reduced.fade
}

internal object MoodOrbJarScript {
    const val MOOD = MoodOrbJarTokens.Script.mood
    val pick = MoodOrbJarTokens.Script.pick
    val pickBy = MoodOrbJarTokens.Script.pickBy
    val release = MoodOrbJarTokens.Script.release
    val swipe = MoodOrbJarTokens.Script.swipe
    const val SWIPE_BY = MoodOrbJarTokens.Script.swipeBy
}

internal object MoodOrbJarTrail {
    const val COPIES = MoodOrbJarTokens.Trail.copies
    const val GAP = MoodOrbJarTokens.Trail.gap
    val alpha = MoodOrbJarTokens.Trail.alpha
}
