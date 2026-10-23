package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

/** Every motion value of the block (RN `motion.ts`). Times in seconds unless named _MS. */
internal object CassetteDeckMotion {
    // The simulation runs at a fixed step so the autoplay is identical on every device and every loop.
    const val STEP = 1.0 / 120.0

    // Keys: a spring-loaded cap that bottoms out on the chassis (restitution) and clicks there.
    object Key {
        const val pressStiffness = 1500.0 // finger force: ~70 ms to the bottom
        const val pressDamping = 34.0
        const val returnStiffness = 520.0 // the cap's spring pushing it back up
        const val returnDamping = 17.0 // under-damped: a small rebound at the top
        const val bottomRestitution = 0.18
        const val topRestitution = 0.32
        const val latchDepth = 0.86 // a latched play key rests just above the bottom, caught by the latch
        const val rearm = 0.45 // depth below which a key can click again
    }

    // Transport: the capstan motor spins the tape up with inertia; a brake and friction stop it in finite time.
    object Transport {
        const val spinUpTau = 0.32
        const val brake = 0.55 // constant deceleration (speed units per second)
        const val friction = 1.0 // viscous term (per second)
        const val wind = 6.0 // fast-forward / rewind speed in play-speed units
        const val engageTau = 0.15 // a loaded cassette's hubs take up the capstan speed
        const val baseOmega = 2.6 // rad/s of a reel whose tape pack is refRadius at play speed
        const val refRadius = 25.0
        const val progressRate = 1.0 / 900.0 // tape fraction per second at play speed
    }

    // Cassettes sit one screen width apart and are swapped like pages with momentum.
    object Pager {
        const val stiffness = 180.0
        const val damping = 24.0
        const val momentum = 0.18 // seconds of release velocity projected forward
        const val loadedWithin = 0.08 // pages from centre at which the deck engages a cassette
        const val rotateMax = 7.0 // degrees of yaw at full swipe speed
        const val rotatePerSpeed = 3.2 // degrees per page/s
    }

    // The deck answers each key with a dip of the chassis and a rattle of the cassette in its tray.
    object Chassis {
        const val dipStiffness = 900.0
        const val dipDamping = 30.0
        const val dipImpulse = 0.012 // pt of dip per pt/s of the key's impact speed
        const val dipTravel = 1.0 // pt per unit
        const val rattleStiffness = 420.0
        const val rattleDamping = 9.0
        const val rattleImpulse = 0.02
        const val rattleTravel = 1.0
        const val seatKick = 40.0
    }

    // Grab a loaded reel and turn it; the tape follows the finger and coasts on release.
    object Scrub {
        const val grabRadius = 34.0 // pt around a reel centre
        const val maxSpeed = 14.0 // play-speed units
        const val tickMinGap = 0.04 // seconds, at most one tooth tick per 40 ms
    }

    // Reels blur when winding; the shell catches a sheen as it slides.
    object Material {
        const val blurFrom = 4.0 // rad/s at which the ghost hubs start to show
        const val blurSpan = 8.0
        const val shutter = 1.0 / 60.0 // s of turn each ghost trails by (spread over the ghosts)
        const val ghosts = 1.0
        const val sheenTravel = 0.6 // of a cassette width per cassette width moved
        const val sheenGain = 3.0
        const val edge = 4.5 // pt: the shell's thickness seen when it yaws
        const val ledFlicker = 0.16 // a seeded level flicker while playing
        const val keyGlow = 0.55 // opacity of the red spill under a latched play key
    }

    object Light {
        const val ledTau = 0.06
        const val glyphOnTau = 0.04
        const val glyphOffTau = 0.22
    }

    // Autoplay: a scripted finger on one clock. The loop starts and ends stopped on the first cassette.
    const val LOOP_MS = 9000.0
    const val LOOP = LOOP_MS / 1000.0
    const val RESUME_MS = 3500.0

    object Script {
        val play = doubleArrayOf(0.7, 0.86)
        val next = doubleArrayOf(3.3, 3.62)
        val ff = doubleArrayOf(4.3, 4.9)
        val pause = doubleArrayOf(7.0, 7.16)

        // A flick to the right on the cassette brings the first one back: p goes 1 -> 1 - dist over time, accelerating.
        object Drag {
            const val start = 5.4
            const val time = 0.32
            const val dist = 0.3
            const val power = 1.6
        }
    }

    // Reduce Motion: no travel; changes cross-fade.
    const val REDUCED_FADE = 0.18

    // QA holds for the replica gate, at their shipped values (RN motion.ts:94-98).
    val QA_HOLD: String? = null // null | "rest" | "a" | "b"
    const val QA_B_PAGE = 0.599
    val QA_T: Double? = null
}
