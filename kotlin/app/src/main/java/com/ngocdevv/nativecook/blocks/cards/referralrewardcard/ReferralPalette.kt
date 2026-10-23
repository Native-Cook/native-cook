package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.ui.graphics.Color

// Clear glass takes its color from the friend under the lens. Secondary copy lets some
// of that light through; captions need more ink because they sit above the brightest lamp.
internal object ReferralPalette {
    object Tone {
        val page = Color.Black
        val night = 0xFF0C0C0D.toInt()
        val ink = android.graphics.Color.WHITE
        val sub = 0xB8FFFFFF.toInt()
        val subAlpha = 0.72f
        val noteAlpha = 0.9f
        val pageHintAlpha = 0.6f
        val ringTrackAlpha = 0.26

        // The capsule is a darker pane set into the glass.
        val pane = 0xFF040408.toInt()
        val paneAlpha = 0.46
    }

    // Sizes are in dp. These proportions fit a 370 dp card while keeping the lens readable.
    object Size {
        val cardMax = 370
        val cardMargin = 16
        val cardRadius = 32
        val cardPad = 24

        // The rail spans the full card; its lens is centered at cy.
        val railHeight = 212
        val cy = 112
        val avatar = 52
        val pitch = 56

        // The neighboring avatars tuck six dp under the ring.
        val tuck = 6

        // The focused row leaves a six dp seam between portraits in transit.
        val focus = 72
        val lensPitch = 78
        val lens = 37
        val ring = 42
        val ringStroke = 1.5f
        val arcStroke = 2

        // Four diagonal ticks start this far from the lens center.
        val tickFrom = 55
        val tickLen = 23
        val pointsRise = 75
        val pointsFont = 30
        val pointsLine = 36

        // The button becomes the waiting dot, which is also the comet's first head.
        val buttonDot = 12
        val buttonHeight = 48
        val actionHeight = 44
        val glassBleed = 96

        // Reward discs overlap by two dp, leaving each icon clear.
        val tierDiameter = 30
        val tierStep = 28
        val tiersWidth = 126
    }

    // All choreography is measured in milliseconds, including fractional path durations.
    object Motion {
        // Arrival of the card's three parts.
        val stagger = 80
        val enter = 380

        // A press may need to bring the next friend under the lens first.
        val travel = 300.0
        val wave = 420.0

        // The points begin rolling just before the head lands.
        val countLead = 40
        val roll = 520.0
        val rollStagger = 45

        // A second comet leaves the points after a tier fills, then reaches its mark.
        val tierAfter = 200
        val flight = 440
        val drainAfter = 260
        val drain = 520.0

        // Autoplay leaves time for the ordinary join, tier reward, or final rest to settle.
        val beat = 2000
        val tierBeat = 2800
        val restBeat = 3200

        // One light turn lasts one autoplay loop: three beats, two tier beats, and a rest.
        val drift = 14800
        val gentle = 200.0
        val press = 120.0
        val swap = 240.0
        val collapse = 140.0
        val labelOut = 100.0
        val launchClearance = 120
        val labelIn = 220.0
        val peekOpen = 520.0
        val peekClose = 360.0
        val peekCloseBeforeCatch = 540.0
        val settle = 380.0
        val flick = 400.0
        val follower = 90.0
        val arrivalTotal = 540.0
        val warmup = 320
        val startupDeadline = 1520
        val smoothFrame = 34
        val firstBeat = 1400
        val pop = 520.0
        val kickReturn = 560.0
        val glowOut = 720.0
        val inviteSettle = 440.0
        val badge = 480.0
        val pointBump = 110.0
        val tierFlash = 640.0
        val tierCrest = 620.0
        val resetBadge = 180.0
        val columns = 460.0

        // The 440 ms flight continues through the trailing fifth of its path clock.
        val flightWithTail = 528.0
    }

    object Trail {
        val samples = 40
        val gap = 0.005
        val size = 3.2

        // The clock runs past one so the entire tail can enter the target.
        val end = 1.2
    }

    val pointsPerFriend = 25

    // A tier fills when this many friends have joined.
    val tiers = intArrayOf(3, 5, 7)

    // The card opens with two friends already joined, worth fifty points.
    val joinedAtStart = 2
    val friendCount = 7
}
