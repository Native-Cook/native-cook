package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import kotlin.math.PI

/** Every timing, spring and interaction token from the React Native reference. */
internal object GumballMotion {
    const val STEP: Double = 1.0 / 120.0
    const val MAX_STEPS: Int = 30

    object PILE {
        const val settleSteps: Int = 900
        const val settleGravity: Double = 1400.0
        const val home: Double = 40.0
        const val damping: Double = 5.0
        const val passes: Int = 3
        const val kick: Double = 260.0
        const val tremble: Double = 0.35
    }

    object CRANK {
        const val grab: Double = 70.0
        val detent: Double = PI / 2.0
        val turn: Double = PI * 2.0
        const val back: Double = 0.1

        object overshoot {
            const val stiffness: Double = 300.0
            const val damping: Double = 14.0
            const val kick: Double = 1.6
        }

        const val press: Double = 1.06
    }

    object DISPENSE {
        const val drop: Double = 0.34
        const val bounce: Double = 0.18
        const val rollStart: Double = 0.56
        const val roll: Double = 0.5
        const val wobbleStart: Double = 1.1
        const val wobble: Double = 0.3
        const val split: Double = 1.4
        const val splitLength: Double = 0.5
        const val cardRise: Double = 0.45
        const val done: Double = 1.9
    }

    object COLLECT {
        const val duration: Double = 0.6

        object bagBounce {
            const val stiffness: Double = 380.0
            const val damping: Double = 12.0
            const val kick: Double = -3.0
        }
    }

    object HAPTIC {
        const val grab: Int = 1
        const val detent: Int = 2
        const val ratchet: Int = 3
        const val dispense: Int = 4
        const val open: Int = 5
        const val collect: Int = 6
    }

    object REDUCED {
        const val fade: Double = 0.2
    }

    /** Autoplay period. The alias is kept for Kotlin renderers that consume a loop duration. */
    const val LOOP: Long = 12_000L
    const val LOOP_MS: Long = 12_000L
    const val RESUME_MS: Long = 3_500L

    object SCRIPT {
        val turn = doubleArrayOf(1.0, 2.7)
        val turnAngle: Double = PI * 2.0 + 0.18
        const val turnRadius: Double = 52.0
        const val collectAt: Double = 7.8
    }

    const val REPLICA_LOOP_MS: Long = 10_000L

    object REPLICA_T {
        val turn = doubleArrayOf(2.8, 3.6)
        const val kick: Double = 3.0
        val out = doubleArrayOf(3.8, 5.0)
        val back = doubleArrayOf(9.0, 9.6)
    }

    const val REPLICA_HOLD: Double = -1.0

    object REPLICA_CAMERA {
        val a = doubleArrayOf(1.0946, -15.3, 13.9)
        val b = doubleArrayOf(1.117, -49.3, -18.1)
        val t = doubleArrayOf(3.8, 4.3, 5.5)
    }

    object KNOB {
        const val grab: Double = 80.0
        const val friction: Double = 2.2
        const val catchSpin: Double = 0.8
        const val commitSpin: Double = 3.0
        const val finish: Double = 0.35
        const val maxSpin: Double = 9.0
        const val press: Double = 1.06
        const val backMax: Double = 0.3
    }

    object UP_T {
        const val squash: Double = 0.18
        val out = doubleArrayOf(0.18, 1.18)
        const val confetti: Double = 0.35
        val morph = doubleArrayOf(1.7, 2.25)
        const val done: Double = 2.4
        const val collect: Double = 0.8
        const val coinFly: Double = 0.45
        const val coinStagger: Double = 0.04
        const val coins: Int = 8
        val couponOut = doubleArrayOf(0.45, 0.8)
        const val back: Double = 0.5
        const val sweep: Double = 0.9
        const val idleSweep: Double = 4.0
    }

    object CONFETTI {
        const val count: Int = 24
        const val speed: Double = 560.0
        const val spread: Double = 1.2
        const val gravity: Double = 1100.0
        const val life: Double = 1.6
        val size = doubleArrayOf(8.0, 16.0)
        const val spin: Double = 7.0
    }

    object COINS {
        const val from: Double = 1290.0
        const val add: Double = 50.0
        const val countSteps: Int = 5
        const val countStep: Double = 70.0
    }

    const val UP_LOOP_MS: Long = 12_000L

    object UP_SCRIPT {
        val turn = doubleArrayOf(1.0, 2.5)
        val angle: Double = PI * 2.0 + 0.2
        const val radius: Double = 48.0
        const val collectAt: Double = 7.8
    }

    const val QA_SPEED: Double = 1.0

    /** Parallax offsets are screen pixels at the two measured camera poses. */
    object REPLICA_PARALLAX {
        object globe {
            val a = doubleArrayOf(-9.0, 15.0)
            val b = doubleArrayOf(-7.0, -8.0)
        }

        object fruitR {
            val a = doubleArrayOf(-24.0, -43.0)
            val b = doubleArrayOf(-8.0, -32.0)
            val c = doubleArrayOf(1110.0, 2060.0)
            const val r: Double = 420.0
            const val blur: Double = 3.5
        }

        object fruitL {
            val a = doubleArrayOf(0.0, -20.0)
            val b = doubleArrayOf(0.0, -6.0)
            val c = doubleArrayOf(100.0, 2200.0)
            const val r: Double = 300.0
            const val blur: Double = 2.0
        }
    }
}
