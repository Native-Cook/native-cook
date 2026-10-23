package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.ui.graphics.Color
import kotlin.math.PI

private fun rgba(red: Int, green: Int, blue: Int, alpha: Double): Color {
    return Color(red / 255f, green / 255f, blue / 255f, alpha.toFloat())
}

/** One autoplay period in milliseconds. */
internal const val LOOP = 12_000.0

/** Canonical values mirrored from the approved React Native block. */
internal object Tokens {
    const val MARGIN = 72.0
    const val BLEED = 60.0
    const val SCRIPT_FONT = "cursive"
    const val UI_FONT = "sans-serif"
    const val LOOP_MS = LOOP

    object COLOR {
        val bg = Color(0xFF1B1A17)
        val bgDeep = Color(0xFF0B0A09)
        val ink = Color.White
        val inkSoft = rgba(255, 255, 255, 0.6)
        val inkFaint = rgba(255, 255, 255, 0.42)
        val inkGhost = rgba(255, 255, 255, 0.2)
        val pill = Color(0xFF1B1A16)
        val pillGhost = rgba(255, 255, 255, 0.04)
        val glass = rgba(255, 255, 255, 0.12)
        val glassBorder = rgba(255, 255, 255, 0.08)
        val trophy = rgba(245, 192, 74, 0.6)
        val stageLight = rgba(140, 168, 235, 0.42)
        val stageLightClear = rgba(140, 168, 235, 0.0)
        val foil = rgba(255, 255, 255, 0.85)
        val foilClear = rgba(255, 255, 255, 0.0)
        val rim = rgba(255, 255, 255, 0.22)
        val glare = rgba(255, 240, 220, 0.55)
        val glareClear = rgba(255, 240, 220, 0.0)
        val shadow = Color.Black
        val glow = rgba(214, 104, 44, 0.42)
        val backRule = rgba(243, 210, 122, 0.5)
        val backInk = rgba(255, 244, 226, 0.92)
        val backMute = rgba(255, 244, 226, 0.52)
        val pillSaved = rgba(243, 210, 122, 0.14)
        val pillSavedBorder = rgba(243, 210, 122, 0.5)
        val goldInk = Color(0xFFF3D27A)
        val finger = rgba(255, 255, 255, 0.32)
        val fingerRing = rgba(255, 255, 255, 0.7)
        val titleShadow = rgba(40, 16, 0, 0.55)
        val gold = listOf(
            Color(0xFF8A5A17),
            Color(0xFFF3D27A),
            Color(0xFFFFF4C8),
            Color(0xFFC8902E),
            Color(0xFF7A4B10),
        )
        val holo = listOf(
            Color(0xFFFF9FD2),
            Color(0xFFFFE7A0),
            Color(0xFFA9FFD0),
            Color(0xFF9FD8FF),
            Color(0xFFC8A8FF),
            Color(0xFFFF9FD2),
        )
        val back = listOf(
            rgba(38, 24, 16, 0.88),
            rgba(16, 11, 8, 0.96),
        )
        val backdropSide = listOf(
            rgba(27, 26, 23, 0.0),
            rgba(27, 26, 23, 0.4),
        )
        val backdropFade = listOf(
            rgba(27, 26, 23, 0.0),
            rgba(27, 26, 23, 0.35),
            bg,
        )
    }

    object TYPE {
        const val titleSize = 26.0
        const val titleLineHeight = 32.0
        const val titleWeight = 500
        const val dateSize = 14.0
        const val dateLineHeight = 18.0
        const val dateWeight = 500
        const val pillSize = 15.0
        const val pillWeight = 600
        const val awardSize = 13.0
        const val awardWeight = 500
        const val awardStrongWeight = 800
        const val bodySize = 15.0
        const val bodyLineHeight = 17.5
        const val bodyWeight = 400
        const val moreSize = 14.0
        const val moreWeight = 600
        const val posterTitle = 44.0
        const val posterTitleWeight = 700
        const val backTitle = 28.0
        const val backTitleWeight = 700
        const val credit = 7.5
        const val creditWeight = 500
        const val creditStrongWeight = 700
    }

    object LAYOUT {
        const val gutter = 25.0
        const val topBar = -5.0
        const val topBarInsetAdjustment = 8.0
        const val close = 36.0
        const val closeIcon = 20.0
        const val moreIcon = 18.0
        const val awardHeight = 30.0
        const val awardPadding = 12.0
        const val awardGap = 6.0
        const val awardIcon = 13.0
        const val cardTop = 69.0
        const val stageLight = 230.0
        const val backdropAspect = 520.0 / 402.0
        const val cardWidthRatio = 0.343
        const val cardAspect = 1.486
        const val cardRadius = 18.0
        const val titleGap = 18.0
        const val infoGap = 4.0
        const val pillHeight = 51.0
        const val pillGap = 13.0
        const val pillPadding = 20.0
        const val pillIcon = 18.0
        const val pillContentGap = 8.0
        const val pillsMarginTop = 19.0
        const val pillsMarginBottom = 21.0
        const val bodyGap = 20.0
        const val plateOverscan = 1.24
        const val titleY = 0.66
        const val titleSkew = -0.08
        const val fingerRadius = 22.0
        const val fingerStroke = 1.5
        const val moreGap = 4.0
        const val chevron = 14.0
        const val hitSlop = 6.0
        const val moreHitSlop = 8.0
    }

    object BACK {
        const val plateOpacity = 0.5
        const val plateBlur = 10.0
        const val titleY = 0.27
        const val ruleY = 0.33
        const val rule = 28.0
        const val ruleHeight = 1.0
        const val creditsY = 0.43
        const val lineStep = 11.0
    }

    object SPACE {
        const val xs = 4.0
        const val s = 8.0
        const val m = 12.0
        const val l = 16.0
        const val xl = 20.0
        const val x2 = 24.0
    }

    object COPY {
        data class Credit(
            val text: String,
            val strong: Boolean,
            val gap: Double = 0.0,
        )

        const val title = "Solenne"
        const val date = "April 24, 2026 · In 2 days"
        val award = listOf("1", " nomination, ", "2", " wins")
        const val watchlist = "Watchlist"
        const val added = "Added"
        const val watched = "Watch"
        const val body = "Discover the story of Solenne Marchetti, the voice that filled arenas from a single silver microphone, and the life she lived beyond the music: the towns, the losses and the songs she never released."
        const val more = "Show more"
        const val less = "Show less"
        const val posterLabel = "Poster of Solenne: a singer in a red stage jacket under warm arena lights"
        val credits = listOf(
            Credit(text = "A FILM BY", strong = false),
            Credit(text = "ILSE VARGA", strong = true),
            Credit(text = "NARRATED BY", strong = false, gap = 8.0),
            Credit(text = "TEO BRANDT", strong = true, gap = 8.0),
            Credit(text = "Documentary · 1 h 48 min", strong = false, gap = 16.0),
            Credit(text = "Marchetti Pictures", strong = false, gap = 16.0),
        )
    }

    object TILT {
        const val perHalfCard = 14.0 * PI / 180.0
        const val max = 18.0 * PI / 180.0
        const val rubber = 0.35
        const val sensorGain = 0.9
        const val sensorMax = 14.0 * PI / 180.0
        const val sensorSmoothing = 0.12
        const val driftAmplitude = 1.2 * PI / 180.0
        const val driftPeriodMs = 12_000.0
        const val perspective = 420.0
        const val lift = 1.34
        const val reducedMax = 4.0 * PI / 180.0
    }

    object PARALLAX {
        const val plate = -38.0
        const val figure = 26.0
        const val title = 42.0
        const val backdrop = -30.0
        const val glare = -2.2
        const val foil = 2.6
    }

    object FLIP {
        const val tapMs = 260.0
        const val slop = 8.0
        const val tapSpeed = 400.0
        const val rise = 0.05
    }

    object HOLO {
        const val travelX = 2.4
        const val travelY = 1.6
        const val flipTravel = 0.5
        const val period = 0.7
        const val rest = 0.18
        const val max = 0.75
        const val gain = 2.2
    }

    object EDGE {
        const val depth = 4.5
        const val fromDeg = 6.0 * PI / 180.0
        const val toDeg = 10.0 * PI / 180.0
    }

    object FLOAT {
        const val amplitude = 2.0
        const val periodMs = 6_000.0
    }

    object SPRING {
        const val flipDamping = 18.0
        const val flipStiffness = 140.0
        const val flipMass = 1.2
        const val releaseDamping = 9.0
        const val releaseStiffness = 120.0
        const val releaseMass = 1.0
        const val liftDamping = 16.0
        const val liftStiffness = 260.0
        const val liftMass = 1.0
        const val morphDamping = 14.0
        const val morphStiffness = 220.0
        const val morphMass = 1.0
        const val pressDamping = 20.0
        const val pressStiffness = 420.0
        const val pressMass = 1.0
    }

    object TIMING {
        const val reducedMs = 200.0
        const val maxFrameSeconds = 0.1
    }

    object QA {
        val hold: String? = null
        const val rest = 0.0
        const val a = 2_200.0
        const val b = 3_800.0
    }

    object AUTOPLAY {
        data class Tap(
            val at: Double,
            val target: String,
        )

        const val resumeDelayMs = 3_500.0
        const val holdStart = 800.0
        const val holdEnd = 5_200.0
        const val ampX = 1.1
        const val ampY = 0.75
        const val ramp = 600.0
        const val pressMs = 110.0
        const val fingerLeadMs = 280.0
        const val fingerFadeMs = 140.0
        const val velocitySampleMs = 16.0
        val taps = listOf(
            Tap(at = 6_000.0, target = "card"),
            Tap(at = 8_200.0, target = "card"),
            Tap(at = 9_600.0, target = "watch"),
            Tap(at = 10_400.0, target = "watch"),
        )
    }

    object MATERIAL {
        const val glowLeft = 0.06
        const val glowTop = 0.1
        const val glowWidth = 0.88
        const val glowHeight = 0.86
        const val glowBlur = 34.0
        const val glowLiftAlpha = 0.7
        const val glowDeadZoneDeg = 2.0
        const val glowTiltGain = 1.2
        const val glowShiftX = 40.0
        const val glowShiftY = 26.0
        const val glowTiltY = 30.0
        const val shadowBlur = 22.0
        const val shadowShiftX = -60.0
        const val shadowShiftY = 18.0
        const val shadowTiltY = -40.0
        const val shadowScale = 0.92
        const val shadowEdgeScale = 0.3
        const val shadowAlpha = 0.55
        const val shadowLiftAlpha = 0.6
        const val glareBaseX = 0.5
        const val glareBaseY = 0.3
        const val glareRadius = 0.9
        const val glareBaseAlpha = 0.18
        const val glareMaxExtraAlpha = 0.32
        const val glareTiltGain = 0.9
        const val foilHalfWidth = 40.0
        const val foilTop = 40.0
        const val foilBottom = 10.0
        const val titleShadowX = 1.5
        const val titleShadowY = 2.0
        const val rimWidth = 1.0
    }

    object MORPH {
        const val pressScale = 0.97
        const val pop = 0.04
        const val iconTurn = 90.0
        const val iconSmall = 0.5
    }
}
