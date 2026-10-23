package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

/**
 * Every motion value of the block, copied value for value from pull-cord-theme/motion.ts.
 * Times are in seconds unless the name ends in _MS; distances are in dp (the RN points).
 */
internal object PullCordThemeMotion {
    /** Fixed simulation step, so the finger and the autoplay give the same result. */
    const val STEP = 1.0 / 120.0

    /** Per frame: the sim keeps wall-clock time through dropped frames, so the loop stays 12 s. */
    const val MAX_STEPS = 30

    internal object Rope {
        const val gravity = 2600.0

        /** Velocity kept per step. */
        const val damping = 0.988
        const val passes = 10

        /** Inverse mass: the bead is four times a rope point. */
        const val beadWeight = 0.25

        /** Bead x from the anchor before rubber-banding. */
        const val sideReach = 72.0

        /** The bead may be lifted this far above rest (the rope goes slack). */
        const val liftReach = 90.0

        /** Finger velocity handed to the bead, clamped. */
        const val releaseSpeed = 1800.0

        /** At the loop seam the rope is snapped to rest when within this. */
        const val snapRest = 1.5
        const val settleSteps = 600
    }

    internal object Switch {
        /** Free travel before the rubber band. */
        const val linear = 36.0

        /** Rubber-band limit of the pull. */
        const val travel = 66.0

        /** Click: the theme toggles. */
        const val detent = 44.0

        /** The pull must come back above this before it can toggle again. */
        const val rearm = 20.0
        const val stiffness = 300.0
        const val damping = 26.0

        /** Critically damped under Reduce Motion. */
        const val reducedDamping = 46.0
    }

    internal object BulbMotion {
        /** The source's swing. */
        const val period = 6.0

        /** Points at the glass centre. */
        const val idleAmp = 14.0
        const val reducedIdleAmp = 0.0

        /** Ratio: a tugged bulb settles within the loop. */
        const val damping = 0.3

        /** Rad/s handed to the swing on release (about 20 pt more swing). */
        const val maxThrow = 0.02

        /** About 55 pt at the glass, the source swing's own reach. */
        const val maxAngle = 0.025
    }

    internal object Reveal {
        /** The circle grows from the bulb, out-quint. */
        const val toLight = 0.72

        /** The circle shrinks into the bulb, in-out cubic. */
        const val toDark = 0.62
        const val reducedFade = 0.2

        /** Past the farthest corner. */
        const val margin = 40.0

        /** Text recolours while the front crosses this band. */
        const val textBand = 70.0
    }

    internal object Level {
        /** Per second, the liquid's lag behind the pull. */
        const val follow = 16.0

        /** Per second after the click. */
        const val fill = 10.0
    }

    /** Neon re-ignition when the dark theme returns: three buzzing stutters, then on. */
    internal object Ignite {
        val times = doubleArrayOf(0.0, 0.05, 0.11, 0.19, 0.26, 0.36, 0.45, 0.6)
        val values = doubleArrayOf(0.1, 0.8, 0.15, 0.9, 0.3, 1.0, 0.6, 1.0)
    }

    internal object Haptic {
        const val GRAB = 1
        const val DETENT = 2
        const val HOME = 3
        const val TICK = 4
    }

    /** The gesture's feel. */
    internal object Feel {
        /** A selection haptic every 12 pt of pull. */
        const val tick = 12.0

        /** A downward release faster than this (pt/s) past flickMin also commits. */
        const val flickSpeed = 900.0
        const val flickMin = 24.0

        /** Rad/s: the switch's snap tugs the bulb into a swing of about 10 pt. */
        const val kick = 0.005

        /** Share of the reveal radius the light bleeds in while pulling (scrub). */
        const val preview = 0.2
    }

    /** The hero beat. */
    internal object Hero {
        /** Seconds: tungsten warms from ember (about 1800 K) to warm white. */
        const val ember = 0.9

        /** Two quick dips: [start, end] pairs, seconds after the commit. */
        val flickers = doubleArrayOf(0.12, 0.22, 0.34, 0.42)
        const val flickerDepth = 0.55

        /** Seconds: soft white flash on commit to light. */
        const val flash = 0.28
        const val flashPeak = 0.32

        /** The warm soft edge of the reveal. */
        const val edge = 34.0

        /** Neon re-ignition: three buzzing stutters. */
        val stutter = doubleArrayOf(0.05, 0.11, 0.19, 0.26, 0.36, 0.45)

        /** Neon hum: period in seconds, lowest brightness. */
        const val humPeriod = 3.0
        const val humLow = 0.92

        /** The glass highlight slides this far across a full swing. */
        const val spec = 26.0

        /** The bulb's cast shadow on the light wall. */
        const val shadowDx = 26.0
        const val shadowDy = 34.0
        const val shadowAlpha = 0.2
        const val shadowBlur = 10.0

        /** Rad of extra swing at full phone tilt. */
        const val tilt = 0.05
        const val motes = 26

        /** Seconds: Dark and Light label roll. */
        const val roll = 0.42
    }

    /** Autoplay: one 12 s loop (two swing periods). */
    const val LOOP_MS = 12_000.0

    /** The loop in seconds. */
    const val LOOP = LOOP_MS / 1000.0
    const val RESUME_MS = 3_500.0

    /** Where the script resumes when the theme is light. */
    const val LIGHT_REST_AT = 5.0

    /**
     * Scripted finger, offsets from the bead's rest position:
     * [start, end, from dx, from dy, to dx, to dy, down at start, up at end].
     */
    val SCRIPT: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.2, 2.0, 0.0, 0.0, -6.0, 30.0, 1.0, 0.0),
        doubleArrayOf(2.0, 2.35, -6.0, 30.0, -4.0, 18.0, 0.0, 0.0),
        doubleArrayOf(2.35, 2.8, -4.0, 18.0, 4.0, 64.0, 0.0, 0.0),
        doubleArrayOf(2.8, 2.95, 4.0, 64.0, 4.0, 64.0, 0.0, 1.0),
        doubleArrayOf(7.2, 7.75, 0.0, 0.0, 8.0, 60.0, 1.0, 0.0),
        doubleArrayOf(7.75, 7.9, 8.0, 60.0, 8.0, 60.0, 0.0, 1.0),
    )

    /** A tap on the bead plays one full pull through the same kernel. */
    val TAP_PULL: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.0, 0.3, 0.0, 0.0, 2.0, 60.0, 1.0, 0.0),
        doubleArrayOf(0.3, 0.4, 2.0, 60.0, 2.0, 60.0, 0.0, 1.0),
    )

    internal object Tap {
        const val slop = 8.0
        const val time = 0.25
    }

    /** The source video's own loop: the bulb lights in its first 0.75 s, then swings with a 6 s period. */
    const val REPLICA_LOOP_MS = 6000.0
    val REPLICA_IGNITE_TIMES = doubleArrayOf(0.0, 0.25, 0.5, 0.75)
    val REPLICA_IGNITE_VALUES = doubleArrayOf(0.08, 0.5, 0.5, 1.0)

    /** The source starts at its left extreme. */
    const val REPLICA_SWING_PHASE = -Math.PI / 2

    /** Seconds: fitted to the references (left at 0, right at 2.75, -0.88 at 5.25). */
    const val REPLICA_PERIOD = 5.8

    /** QA only, shipped value: pins the replica's clock to this time; -1 runs normally. */
    const val REPLICA_HOLD = -1.0

    /** QA only, shipped value: slows the whole simulation for slow-motion proof captures. */
    const val QA_SPEED = 1.0
}
