package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.ui.graphics.Color
import kotlin.math.PI

private fun rgba(red: Int, green: Int, blue: Int, alpha: Double): Color {
    return Color(red / 255f, green / 255f, blue / 255f, alpha.toFloat())
}

internal object Tokens {
    const val MARGIN = 72.0
    const val BLEED = 60.0
    const val LOOP_MS = LOOP

    const val SERIF_ASSET = "tiltposterparallax/tiltposterparallax_InstrumentSerif-Regular.ttf"
    const val INTER_REGULAR_ASSET = "tiltposterparallax/tiltposterparallax_Inter-Regular.ttf"
    const val INTER_MEDIUM_ASSET = "tiltposterparallax/tiltposterparallax_Inter-Medium.ttf"
    const val INTER_SEMIBOLD_ASSET = "tiltposterparallax/tiltposterparallax_Inter-SemiBold.ttf"

    object COLOR {
        val bg = Color(0xFF0E0C10)
        val ink = Color(0xFFF5F1EA)
        val inkSoft = rgba(245, 241, 234, 0.62)
        val inkFaint = rgba(245, 241, 234, 0.42)
        val glass = rgba(255, 255, 255, 0.10)
        val glassBorder = rgba(255, 255, 255, 0.14)
        val play = Color(0xFFF5F1EA)
        val playInk = Color(0xFF16131A)
        val trophy = Color(0xFFE9C47A)
        val foil = rgba(255, 255, 255, 0.85)
        val foilClear = rgba(255, 255, 255, 0.0)
        val embossDark = rgba(20, 10, 20, 0.55)
        val embossLight = rgba(255, 236, 200, 0.35)
        val posterInk = rgba(246, 226, 200, 0.72)
        val rim = rgba(255, 255, 255, 0.20)
        val glare = rgba(255, 240, 220, 0.50)
        val glareClear = rgba(255, 240, 220, 0.0)
        val shadow = Color.Black
        val glow = rgba(230, 140, 90, 0.40)
        val edge = listOf(Color(0xFFE8DCC6), Color(0xFFB9A88C))
        val back = listOf(Color(0xFF221A22), Color(0xFF120E14))
        val backRule = rgba(243, 210, 122, 0.50)
        val backInk = rgba(255, 244, 226, 0.92)
        val backMute = rgba(255, 244, 226, 0.52)
        val pillSaved = rgba(243, 210, 122, 0.14)
        val pillSavedBorder = rgba(243, 210, 122, 0.50)
        val goldInk = Color(0xFFF3D27A)
        val gold = listOf(
            Color(0xFF8A5A17), Color(0xFFF3D27A), Color(0xFFFFF4C8), Color(0xFFC8902E), Color(0xFF7A4B10),
        )
        val holo = listOf(
            Color(0xFFFF9FD2), Color(0xFFFFE7A0), Color(0xFFA9FFD0),
            Color(0xFF9FD8FF), Color(0xFFC8A8FF), Color(0xFFFF9FD2),
        )
        val backdropFade = listOf(rgba(14, 12, 16, 0.15), rgba(14, 12, 16, 0.55), bg)
        val finger = rgba(255, 255, 255, 0.14)
        val fingerRing = rgba(255, 255, 255, 0.42)
    }

    object TYPE {
        const val titleSize = 36.0
        const val titleLineHeight = 44.0
        const val metaSize = 14.0
        const val metaLineHeight = 20.0
        const val pillSize = 15.0
        const val awardSize = 13.0
        const val bodySize = 15.0
        const val bodyLineHeight = 22.0
        const val moreSize = 14.0
        const val chipSize = 11.0
        const val chipLineHeight = 14.0
        const val posterTitle = 50.0
        const val posterLine = 46.0
        const val backTitle = 30.0
        const val credit = 8.0
    }

    object LAYOUT {
        const val gutter = 16.0
        const val topBar = 4.0
        const val close = 44.0
        const val closeIcon = 20.0
        const val shareIcon = 19.0
        const val awardHeight = 32.0
        const val awardPadding = 12.0
        const val awardGap = 6.0
        const val awardIcon = 14.0
        const val cardTop = 64.0
        const val backdropShare = 0.62
        const val cardWidthRatio = 0.60
        const val cardAspect = 1.48
        const val cardRadius = 16.0
        const val titleGap = 24.0
        const val infoGap = 4.0
        const val pillHeight = 52.0
        const val pillGap = 12.0
        const val pillPadding = 16.0
        const val pillIcon = 20.0
        const val pillContentGap = 8.0
        const val pillsMarginTop = 16.0
        const val pillsMarginBottom = 16.0
        const val formatsMarginTop = 16.0
        const val formatsPaddingTop = 16.0
        const val chipHeight = 24.0
        const val chipPadding = 8.0
        const val chipRadius = 6.0
        const val titleY = 0.20
        const val fingerRadius = 22.0
        const val fingerStroke = 1.0
        const val moreGap = 4.0
        const val chevron = 14.0
    }

    object BACK {
        const val titleY = 0.26
        const val ruleY = 0.31
        const val rule = 28.0
        const val creditsY = 0.41
        const val lineStep = 13.0
    }

    object COPY {
        data class Credit(val text: String, val strong: Boolean, val gap: Double = 0.0)

        const val title = "The Long Light"
        val posterTitle = listOf("The Long", "Light")
        const val posterCredit = "A FILM BY ILSE VARGA"
        const val meta = "2026  ·  Drama  ·  1 h 52 min  ·  12"
        val award = listOf("3", " festival awards")
        const val watchlist = "Watchlist"
        const val added = "Added"
        const val play = "Play trailer"
        const val body = "After her father’s funeral, a lighthouse keeper’s daughter walks the old coast path north to scatter his ashes, and finds the people he never mentioned waiting at every stop along the way."
        val credits = listOf(
            Credit("A FILM BY", false), Credit("ILSE VARGA", true),
            Credit("STARRING", false, 10.0), Credit("MARA OKAFOR   TEO BRANDT", true, 10.0),
            Credit("Drama · 1 h 52 min", false, 20.0), Credit("Northlight Pictures", false, 20.0),
        )
        val formats = listOf("4K", "HDR", "Dolby Atmos", "AD")
        const val more = "Show more"
        const val less = "Show less"
        const val posterLabel = "Poster of The Long Light: a walker on a ridge at dusk, a low sun behind the hills"
    }

    object TILT {
        const val perHalfCard = 14.0 * PI / 180.0
        const val max = 18.0 * PI / 180.0
        const val rubber = 0.35
        const val sensorGain = 0.9
        const val sensorMax = 14.0 * PI / 180.0
        const val sensorSmoothing = 0.12
        const val driftAmplitude = 1.2 * PI / 180.0
        const val driftPeriodMs = 10_000.0
        const val perspective = 420.0
        const val lift = 1.04
        const val reducedMax = 4.0 * PI / 180.0
    }

    object PARALLAX {
        val layers = listOf(-40.0, -30.0, -14.0, 10.0, 26.0)
        const val title = 44.0
        const val backdrop = -30.0
        const val glare = -2.2
        const val foil = 2.6
    }

    object FLIP {
        const val tapMs = 260.0
        const val slop = 8.0
        const val tapSpeed = 400.0
        const val rise = 0.03
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
        const val periodMs = 5_000.0
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
        const val maxFrameSeconds = 0.25
    }

    object QA {
        val hold: String? = null
        const val rest = 0.0
        const val a = 2_200.0
        const val b = 3_800.0
    }

    object AUTOPLAY {
        data class Tap(val at: Double, val target: String)

        const val resumeDelayMs = 2_000.0
        const val holdStart = 600.0
        const val holdEnd = 4_200.0
        const val ampX = 1.1
        const val ampY = 0.75
        const val ramp = 600.0
        const val pressMs = 110.0
        const val fingerLeadMs = 280.0
        const val fingerFadeMs = 140.0
        const val velocitySampleMs = 16.0
        val taps = listOf(
            Tap(4_900.0, "card"), Tap(6_800.0, "card"), Tap(8_000.0, "watch"), Tap(8_800.0, "watch"),
        )
    }

    object MATERIAL {
        const val glowBlur = 40.0
        const val shadowBlur = 24.0
        const val rimWidth = 1.0
    }

    object MORPH {
        const val pressScale = 0.97
        const val pop = 0.04
        const val iconTurn = 90.0
        const val iconSmall = 0.5
    }
}
