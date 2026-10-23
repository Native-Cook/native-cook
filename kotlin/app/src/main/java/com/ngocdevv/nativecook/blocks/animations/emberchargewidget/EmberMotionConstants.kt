package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

// Names and units mirror M in ember-motion.ts: milliseconds except ignite/loop/bar.
// The owner converts milliseconds at its boundary; integration always uses seconds.
internal object M {
    // A continuous hold repeats charge → hold → cool without requiring another touch.
    const val charge = 2200f
    const val chargeReduced = 900f
    const val hold = (loop.held - loop.charged) * 1000
    const val cool = 800f
    const val ignite = 1.1f
    const val press = 120f
    const val pressRelease = 60f
    const val enter = 320f
    const val resume = 2500f
    const val stripeIdle = 18f
    const val stripeMax = 260f

    object loop {
        const val rest = 0.8f
        const val charged = 3f
        const val held = 5.4f
        const val end = 6.2f
    }

    object release {
        const val duration = 450f
        const val dampingRatio = 0.75f
    }

    // Critical damping follows quickly, then carries release velocity home without ringing.
    object aim {
        object follow {
            const val duration = 240f
            const val dampingRatio = 1f
        }

        object release {
            const val duration = 480f
            const val dampingRatio = 1f
        }

        const val x = 0.3f
        const val y = 0.2f
    }

    // One visible sampling window, after shader warm-up and the entrance.
    object quality {
        const val window = 2000f
        const val frame = 24f
        const val allowed = 6
    }

    // Unit-mass meniscus spring and the tube ignition morph, in seconds.
    object bar {
        const val stiffness = 360f
        const val damping = 16f
        const val drag = 0.04f
        const val fuseIn = 0.065f
        const val fuseOut = 0.24f
        const val ringDecay = 9f
        const val ringFrequency = 15f
        const val sampleHz = 120f
        const val minimumDelta = 1f / 240
        const val minimumBend = -8f
        const val maximumBend = 10f
        const val positionEpsilon = 0.001f
        const val velocityEpsilon = 0.01f
    }

    // Scale the four floor shadows as heat rises; the outline stays fixed.
    object rise {
        const val rest = 1f
        const val hot = 1.28f
    }

    // The liquid lean keeps momentum from press, early release and ignition.
    object slosh {
        const val stiffness = 90f
        const val damping = 7f
        const val press = 0.35f
        const val release = -0.5f
        const val ignite = 1.1f
    }

    // Additional timings from the reference's owner/surface/count, not new choreography.
    const val finalizePress = 160f
    const val enterReduced = 200f
    const val resumeCompletion = 1f
    const val touchSpread = 240f
    const val touchFade = 320f
    const val countRoll = 0.24f
    const val countBounce = 0.15f
    const val bpmRoll = 0.32f
    const val bpmBounce = 0.2f
    const val minimumCharge = 0.001f
    const val restEpsilon = 0.001f
    const val countStride = 97
    const val hapticStride = 500
    const val endpointEpsilon = 0.5f
    const val heatUp = 16f
    const val heatDown = 5f
    const val stripePower = 1.5f
    const val bpmChargeStart = 0.9f

    object frame {
        const val initial = 0.016f
        const val missing = 0.1f
        const val minimum = 0.001f
        const val maximum = 0.05f
        const val liveThreshold = 0.034f
        const val liveFrames = 6
    }
}
