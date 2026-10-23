package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.R

/** Exact visual and motion values mirrored from the approved React Native block. */
object SaveToCollectionTokens {
    val background = Color(0xFF000000)
    val ink = Color(0xFFFFFFFF)
    val mute = Color(0xFF8E8E93)
    val card = Color(0xFF1C1C1E)
    val cardEdge = Color(0x2EFFFFFF)
    val sheen = listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF))
    val badge = Color(0xFFFFFFFF)
    val badgeInk = Color(0xFF000000)
    val check = Color(0xFFFFFFFF)
    val checkRing = Color(0xD9FFFFFF)
    val dim = Color(0x8C000000)
    val glow = Color(0x2EFFFFFF)
    val fade = listOf(Color(0x00000000), Color(0xEB000000))
    val finger = Color(0x59FFFFFF)
    val glassEdge = Color(0x59FFFFFF)
    val fingerEdge = Color(0xBFFFFFFF)

    const val glowUnder = 28f
    const val glowUnderOpacity = 0.75f
    const val glowUnderDrop = 18f
    const val glassIntensity = 40

    const val spaceXs = 4f
    const val spaceS = 8f
    const val spaceM = 12f
    const val spaceL = 16f
    const val spaceXl = 24f
    const val spaceXxl = 32f

    const val gutter = 16f
    const val gap = 8f
    const val headerTop = 8f
    const val header = 44f
    const val cardRadius = 12f
    const val bookmarkWidth = 62f
    const val bookmarkHeight = 75f
    const val bookmarkY = 766f
    const val bookmarkRiseY = 393f
    const val dockFade = 160f
    const val badgeSize = 20f
    const val corner = 22f
    const val checkSize = 26f
    const val saveButtonHeight = 36f
    const val saveButtonRadius = 18f
    const val fingerSize = 44f
    const val cardDim = 0.65f
    const val cardDealStartScale = 0.9f
    const val cardPhaseStep = 1.7f
    const val cardBlurDepthDivisor = 10f
    const val bookmarkCornerFraction = 0.16f
    const val bookmarkNotchFraction = 0.26f
    const val bookmarkOpenScale = 0.18f
    const val bookmarkBounceScale = 0.22f
    const val bookmarkRiseScale = 0.14f
    const val bookmarkPressScale = 0.06f
    const val haloSize = 140f
    const val ringSize = 70f
    const val ringWidth = 1.5f
    const val ringOpacity = 0.55f
    const val ringScaleStart = 0.6f
    const val ringScaleTravel = 1.8f
    const val countRiseThreshold = 2f
    const val countRestDrop = 10f
    const val pillBackgroundAlpha = 0.06f
    const val pillEdgeHeight = 1f
    const val fingerPressScale = 0.18f

    const val titleFontSize = 28f
    const val titleLineHeight = 34f
    const val titleFontWeight = 700
    const val titleLetterSpacing = 0.2f
    const val actionFontSize = 17f
    const val actionLineHeight = 22f
    const val actionFontWeight = 600
    const val badgeFontSize = 12f
    const val badgeLineHeight = 14f
    const val badgeFontWeight = 700
    const val countFontSize = 15f
    const val countLineHeight = 20f
    const val countFontWeight = 600
    const val countLetterSpacing = -0.2f
    const val saveFontSize = 15f
    const val saveLineHeight = 20f
    const val saveFontWeight = 600

    val easeOut = SaveCubicBezier(0.23f, 1f, 0.32f, 1f)
    val easeInOut = SaveCubicBezier(0.77f, 0f, 0.175f, 1f)
    val riseEase = SaveCubicBezier(0.2f, 0f, 0.5f, 1f)

    const val saveHoldMs = 300
    const val saveLiftScale = 1.06f
    const val saveMagnetRadius = 150f
    const val saveMagnetPull = 0.45f
    const val saveMagnetShrink = 0.45f
    const val saveArc = 0.08f
    const val saveEndScale = 0.06f
    const val saveTiltPerVelocity = 0.012f
    const val saveTiltMax = 14f
    const val saveFlickSpeed = 900f
    const val saveFlickAim = 0.8f
    const val saveFloatAmp = 2.5f
    const val saveFloatPeriod = 3.25f
    const val saveTrail = 8
    const val saveTrailStep = 0.022f
    const val saveStreak = 1.4f
    const val saveStreakBlur = 16f
    const val flightLeadFadeStart = 0.92f
    const val flightLeadFadeSpan = 0.08f
    const val flightGhostOpacity = 0.55f
    const val flightGhostExponent = 0.7f
    const val flightGhostFadeIn = 4f
    const val flightLeadStretch = 0.5f
    const val flightSmearStretch = 1.2f
    const val flightGhostStretchStep = 0.4f
    const val flightSmearOpacity = 2.5f
    const val flightLookAhead = 0.02f

    const val streamCount = 46
    const val streamTotal = 2100
    const val streamPour = 0.75f
    const val streamFall = 0.3f
    const val streamSpread = 125f
    const val streamStretch = 0.6f
    const val streamSeed = 7
    const val streamWidth = 76f
    const val streamHeight = 188f
    const val streamCardWidth = 60f
    const val streamBigChance = 0.2f
    const val streamBigWidth = 90f
    const val streamBigWidthRange = 50f
    const val streamSmallWidth = 40f
    const val streamSmallWidthRange = 40f
    const val streamDurationMin = 0.75f
    const val streamDurationRange = 0.5f
    const val streamStartY = -100f
    const val streamStartYRange = 340f
    const val streamAlphaIn = 6f
    const val streamAlphaOutStart = 0.85f
    const val streamEndScale = 0.15f

    val springLift = SaveSpring(18f, 320f, 0.7f)
    val springBack = SaveSpring(20f, 220f, 1f)
    val springBounce = SaveSpring(8f, 420f, 0.6f)
    val springCheck = SaveSpring(16f, 380f, 0.6f)
    val springTilt = SaveSpring(16f, 220f, 0.6f)

    const val timingPressIn = 100
    const val timingPressOut = 160
    const val timingFlight = 560
    const val timingDropFlight = 300
    const val timingFlockStagger = 70
    const val timingBadgeRoll = 160
    const val timingDim = 140
    const val timingReduced = 200
    const val timingCornerIn = 220
    const val timingDeal = 380
    const val timingDealStagger = 110
    const val timingDealDrop = 18f
    const val timingNextPile = 600
    const val timingLandRing = 560
    const val timingCountIn = 260
    const val timingFlickFlight = 360
    const val timingRise = 1700
    const val timingRiseDown = 850
    const val timingRiseHold = 500

    const val replicaOnly = false
    val qaHoldAt: Double? = null
    const val loopMs = 13_000L
    const val resumeMs = 3_500L
    const val maxFrameSeconds = 0.1f
    const val demoFingerFade = 0.15f
    const val demoDragCard = "lemon"
    const val demoDragDown = 1.6f
    const val demoDragLifted = 1.9f
    const val demoDragEnd = 3.0f
    const val demoDragUp = 3.25f
    const val demoSaveAll = 4.2f
    const val demoRise = 5.05f
    const val demoRiseAgain = 9.45f
    const val demoSaveAllAgain = 8.6f
    const val demoDragArc = 40f
    const val demoPressIn = 120
    const val demoPressOut = 160
    const val bounceUp = 0.07f
    const val bounceSettle = 0.45f
    const val bounceFrequency = 24f

    const val designWidth = 402f
    const val designHeight = 874f
    const val softPad = 24f

    val cards = listOf(
        SaveCard("woman", "Orange afternoon", 100f, 25f, 98f, 102f, 30f, 0, R.drawable.savetocollection_art_orange_woman, R.drawable.savetocollection_soft_woman),
        SaveCard("violet", "Violet meadow", 143f, 153f, 100f, 72f, 4f, 0, R.drawable.savetocollection_art_violet_flowers, R.drawable.savetocollection_soft_violet),
        SaveCard("bloom", "Pastel bloom", 35f, 250f, 95f, 88f, 2f, 0, R.drawable.savetocollection_art_pastel_bloom, R.drawable.savetocollection_soft_bloom),
        SaveCard("figure", "Blue figure", 132f, 255f, 159f, 185f, 1f, 0, R.drawable.savetocollection_art_blue_figure, R.drawable.savetocollection_soft_figure),
        SaveCard("cat", "White cat", 27f, 370f, 125f, 147f, 1f, 0, R.drawable.savetocollection_art_white_cat, R.drawable.savetocollection_soft_cat),
        SaveCard("lemon", "Lemon", 182f, 368f, 188f, 150f, 1f, 0, R.drawable.savetocollection_art_lemon, R.drawable.savetocollection_soft_lemon),
        SaveCard("van", "White van", 105f, 495f, 178f, 162f, 0f, 0, R.drawable.savetocollection_art_white_van, R.drawable.savetocollection_soft_van),
        SaveCard("woman2", "Orange afternoon", 72f, 20f, 148f, 160f, 30f, 1, R.drawable.savetocollection_art_orange_woman, R.drawable.savetocollection_soft_woman2),
        SaveCard("violet2", "Violet meadow", 100f, 161f, 185f, 106f, 1f, 1, R.drawable.savetocollection_art_violet_flowers, R.drawable.savetocollection_soft_violet2),
        SaveCard("bloom2", "Pastel bloom", 0f, 255f, 172f, 175f, 2f, 1, R.drawable.savetocollection_art_pastel_bloom, R.drawable.savetocollection_soft_bloom2),
        SaveCard("jazz", "Slow jazz", 75f, 310f, 128f, 177f, 0f, 1, R.drawable.savetocollection_art_slow_jazz, R.drawable.savetocollection_soft_jazz),
        SaveCard("valley", "Green valley", 152f, 269f, 200f, 138f, 0f, 1, R.drawable.savetocollection_art_valley, R.drawable.savetocollection_soft_valley),
        SaveCard("bust", "Pop bust", 203f, 379f, 150f, 169f, 6f, 1, R.drawable.savetocollection_art_pop_bust, R.drawable.savetocollection_soft_bust),
    )

    val streaks = listOf(
        R.drawable.savetocollection_streak_orange_woman,
        R.drawable.savetocollection_streak_violet_flowers,
        R.drawable.savetocollection_streak_pastel_bloom,
        R.drawable.savetocollection_streak_blue_figure,
        R.drawable.savetocollection_streak_white_cat,
        R.drawable.savetocollection_streak_lemon,
        R.drawable.savetocollection_streak_white_van,
        R.drawable.savetocollection_streak_slow_jazz,
        R.drawable.savetocollection_streak_valley,
        R.drawable.savetocollection_streak_pop_bust,
    )

    const val bookmarkBottomInset = 3f
    const val bookmarkShoulder = 4.6f
    const val bookmarkShoulderInset = 0.6f
    const val haloScaleTravel = 0.6f
}
