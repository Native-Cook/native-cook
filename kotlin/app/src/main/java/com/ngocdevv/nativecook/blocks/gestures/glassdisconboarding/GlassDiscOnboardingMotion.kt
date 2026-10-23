package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

/** Motion aliases backed entirely by Tokens.kt. */
internal object GlassDiscOnboardingMotion {
    object PULL {
        const val threshold = GlassDiscOnboardingTokens.PULL.threshold
        const val rubber = GlassDiscOnboardingTokens.PULL.rubber
        const val commitAt = GlassDiscOnboardingTokens.PULL.commitAt
        const val flickVelocity = GlassDiscOnboardingTokens.PULL.flickVelocity
        const val flickMin = GlassDiscOnboardingTokens.PULL.flickMin
        const val ringPeakAt = GlassDiscOnboardingTokens.PULL.ringPeakAt
        const val ringOutAt = GlassDiscOnboardingTokens.PULL.ringOutAt
        const val revealFrom = GlassDiscOnboardingTokens.PULL.revealFrom
        const val revealTo = GlassDiscOnboardingTokens.PULL.revealTo
        const val introOutBy = GlassDiscOnboardingTokens.PULL.introOutBy
        const val hintOutBy = GlassDiscOnboardingTokens.PULL.hintOutBy
        const val tickEvery = GlassDiscOnboardingTokens.PULL.tickEvery
    }

    object BACK {
        const val growBy = GlassDiscOnboardingTokens.BACK.growBy
        const val commitBy = GlassDiscOnboardingTokens.BACK.commitBy
        const val flickVelocity = GlassDiscOnboardingTokens.BACK.flickVelocity
        const val flickMin = GlassDiscOnboardingTokens.BACK.flickMin
    }

    object RISE {
        const val ms = GlassDiscOnboardingTokens.RISE.ms
    }

    object SPRING {
        object back {
            const val duration = GlassDiscOnboardingTokens.SPRING.backDuration
            const val dampingRatio = GlassDiscOnboardingTokens.SPRING.backDamping
        }

        object bead {
            const val duration = GlassDiscOnboardingTokens.SPRING.beadDuration
            const val dampingRatio = GlassDiscOnboardingTokens.SPRING.beadDamping
        }

        object dome {
            const val duration = GlassDiscOnboardingTokens.SPRING.domeDuration
            const val dampingRatio = GlassDiscOnboardingTokens.SPRING.domeDamping
        }
    }

    val EASE_OUT = GlassDiscOnboardingTokens.EASE_OUT

    object FADE {
        const val settle = GlassDiscOnboardingTokens.FADE.settle
        const val headlineMs = GlassDiscOnboardingTokens.FADE.headlineMs
        const val restDelay = GlassDiscOnboardingTokens.FADE.restDelay
        const val restMs = GlassDiscOnboardingTokens.FADE.restMs
        const val outMs = GlassDiscOnboardingTokens.FADE.outMs
        const val introBack = GlassDiscOnboardingTokens.FADE.introBack
        const val reducedMs = GlassDiscOnboardingTokens.FADE.reducedMs
    }

    object BURST {
        const val delayMs = GlassDiscOnboardingTokens.BURST.delayMs
        const val staggerMs = GlassDiscOnboardingTokens.BURST.staggerMs
        const val startScale = GlassDiscOnboardingTokens.BURST.startScale
        const val growOver = GlassDiscOnboardingTokens.BURST.growOver
        const val growMs = GlassDiscOnboardingTokens.BURST.growMs
        const val launchSpeed = GlassDiscOnboardingTokens.BURST.launchSpeed
    }

    object STICKERS {
        const val step = GlassDiscOnboardingTokens.STICKERS.step
        const val stiffness = GlassDiscOnboardingTokens.STICKERS.stiffness
        const val damping = GlassDiscOnboardingTokens.STICKERS.damping
        const val separation = GlassDiscOnboardingTokens.STICKERS.separation
        const val overlap = GlassDiscOnboardingTokens.STICKERS.overlap
        const val push = GlassDiscOnboardingTokens.STICKERS.push
        const val float = GlassDiscOnboardingTokens.STICKERS.float
        const val floatPeriodMs = GlassDiscOnboardingTokens.STICKERS.floatPeriodMs
        const val exitLift = GlassDiscOnboardingTokens.STICKERS.exitLift
        const val exitStiffness = GlassDiscOnboardingTokens.STICKERS.exitStiffness
        const val exitDamping = GlassDiscOnboardingTokens.STICKERS.exitDamping
        const val exitStaggerMs = GlassDiscOnboardingTokens.STICKERS.exitStaggerMs
        const val maxFrameMs = GlassDiscOnboardingTokens.STICKERS.maxFrameMs
    }

    /** One autoplay period in milliseconds; the loop starts and ends at rest. */
    const val LOOP = GlassDiscOnboardingTokens.LOOP_MS
    const val LOOP_MS = LOOP

    /** QA-only autoplay freeze; -1 means disabled in the shipped block. */
    const val DEBUG_FREEZE_MS = GlassDiscOnboardingTokens.DEBUG_FREEZE_MS

    object AUTOPLAY {
        const val resumeDelayMs = GlassDiscOnboardingTokens.AUTOPLAY.resumeDelayMs
        const val startX = GlassDiscOnboardingTokens.AUTOPLAY.startX
        const val startY = GlassDiscOnboardingTokens.AUTOPLAY.startY
        const val pullStart = GlassDiscOnboardingTokens.AUTOPLAY.pullStart
        const val pullEnd = GlassDiscOnboardingTokens.AUTOPLAY.pullEnd
        const val pullBy = GlassDiscOnboardingTokens.AUTOPLAY.pullBy
        const val sweepStart = GlassDiscOnboardingTokens.AUTOPLAY.sweepStart
        const val sweepEnd = GlassDiscOnboardingTokens.AUTOPLAY.sweepEnd
        const val backStart = GlassDiscOnboardingTokens.AUTOPLAY.backStart
        const val backEnd = GlassDiscOnboardingTokens.AUTOPLAY.backEnd
        const val backBy = GlassDiscOnboardingTokens.AUTOPLAY.backBy
    }

    object UPGRADE {
        const val nudge = GlassDiscOnboardingTokens.UPGRADE.nudge

        object nudgeSpring {
            const val duration = GlassDiscOnboardingTokens.UPGRADE.nudgeDuration
            const val dampingRatio = GlassDiscOnboardingTokens.UPGRADE.nudgeDamping
        }

        const val stretchPerSpeed = GlassDiscOnboardingTokens.UPGRADE.stretchPerSpeed
        const val stretchMax = GlassDiscOnboardingTokens.UPGRADE.stretchMax
        const val stretchFollow = GlassDiscOnboardingTokens.UPGRADE.stretchFollow
        const val dispersionPerSpeed = GlassDiscOnboardingTokens.UPGRADE.dispersionPerSpeed
        const val dispersionMax = GlassDiscOnboardingTokens.UPGRADE.dispersionMax
        const val glintEveryMs = GlassDiscOnboardingTokens.UPGRADE.glintEveryMs
        const val glintMs = GlassDiscOnboardingTokens.UPGRADE.glintMs
        const val glintFrom = GlassDiscOnboardingTokens.UPGRADE.glintFrom
        const val glintTo = GlassDiscOnboardingTokens.UPGRADE.glintTo
        const val glow = GlassDiscOnboardingTokens.UPGRADE.glow
        val pulses = GlassDiscOnboardingTokens.UPGRADE.pulses
        const val pulseMs = GlassDiscOnboardingTokens.UPGRADE.pulseMs
        const val pulseGrow = GlassDiscOnboardingTokens.UPGRADE.pulseGrow
        const val pulseSquash = GlassDiscOnboardingTokens.UPGRADE.pulseSquash
        const val wobblePerSpeed = GlassDiscOnboardingTokens.UPGRADE.wobblePerSpeed
        const val wobbleMax = GlassDiscOnboardingTokens.UPGRADE.wobbleMax
        const val fingerCarry = GlassDiscOnboardingTokens.UPGRADE.fingerCarry
        const val rise = GlassDiscOnboardingTokens.UPGRADE.rise
        const val lineStagger = GlassDiscOnboardingTokens.UPGRADE.lineStagger
        const val fieldDrift = GlassDiscOnboardingTokens.UPGRADE.fieldDrift
        const val fieldPeriodMs = GlassDiscOnboardingTokens.UPGRADE.fieldPeriodMs
        const val hintBase = GlassDiscOnboardingTokens.UPGRADE.hintBase
        const val pressIn = GlassDiscOnboardingTokens.UPGRADE.pressIn
        const val pressOut = GlassDiscOnboardingTokens.UPGRADE.pressOut
        const val pressScale = GlassDiscOnboardingTokens.UPGRADE.pressScale
    }
}
