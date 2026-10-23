package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.ui.graphics.Color

/** Value-for-value Compose mirror of the accepted React Native theme.ts. */
internal object PitStopReviewTheme {
    const val REFERENCE_WIDTH = 402f
    const val ASCENT = 0.968f
    const val BAR_CENTER = 25.1f

    val ink = Color(0xFFF8F5FA)
    val muted = Color(0xFFB2ADB8)
    val background = Color.Black
    val panel = Color(0xFF1A181C)
    val violet = Color(0xFF361CFF)
    val orange = Color(0xFFFF4A08)
    val yellow = Color(0xFFFFE62E)
    val light = Color(0xFFFF3B30)
    val lightCore = Color(0xFFFFD2C2)
    val lightOff = Color(0xFF2B0C0B)
    val discHot = Color(0xFFFF6A1A)
    val discBloom = Color(0xFFE2340C)

    object ClubPass {
        const val left = 28.7f
        const val height = 28f
        const val badge = 17.7f
        const val padLeft = 5.15f
        const val padRight = 9.5f
        const val gap = 5.3f
        const val sparkleWidth = 8f
        const val sparkleHeight = 8.7f
        const val fontSize = 12f
        const val letterSpacing = -0.2f
        const val lineHeight = 15f
    }

    object Avatar {
        const val hit = 44f
        const val right = 19f
        const val ring = 40.6f
        const val photo = 33.3f
    }

    object Hero {
        const val top = 32f
        const val width = 402f
        const val height = 220f
        const val carX = 70f
        const val carY = 26f
        const val carWidth = 273.33f
        const val carHeight = 106.67f
        val trailBlooms = arrayOf(
            floatArrayOf(-80f, 36f, 220f, 140f, 20f, 0.95f),
            floatArrayOf(-60f, 54f, 180f, 104f, 12f, 0.70f),
        )
        val trailBloomColors = arrayOf(Color(0xFFFF2A0A), Color(0xFFFF5A1A))
        const val trailCoreX = -50f
        const val trailCoreY = 70f
        const val trailCoreWidth = 150f
        const val trailCoreHeight = 36f
        const val trailCoreBlur = 6f
        const val trailStreaks = 22
        const val trailStreakTop = 62f
        const val trailStreakSpan = 50f
    }

    object Gantry {
        const val centerX = 201f
        const val centerY = 178f
        const val pitch = 20f
        const val radius = 6.5f
        const val housingHeight = 20f
        const val housingPadding = 7f
    }

    object Pager {
        const val top = 422.3f
        const val height = 8f
        const val active = 24.7f
        const val dot = 3.3f
        const val gap = 8.8f
    }

    object TabBar {
        const val width = 358f
        const val height = 62f
        const val bottom = 25.5f
        const val padding = 7f
        const val icon = 22f
        const val lensWidth = 74f
        const val lensHeight = 52f
        val iconColor = Color.White.copy(alpha = 0.94f)
        val lensFill = Color.White.copy(alpha = 0.16f)
    }

    object Stars {
        const val row = 250f
        const val cell = 50f
        const val height = 48f
        const val box = 28f
        const val radius = 11f
    }

    object HeroText {
        const val centerX = 191f
        const val titleSize = 30.4f
        const val titleBaseline = 263f
        const val titleTracking = -0.3f
        const val subtitleSize = 14.3f
        const val subtitleBaseline = 293f
        const val subtitleLineHeight = 20.3f
        const val subtitleWidth = 330f
        const val ctaTop = 332.7f
        const val ctaWidth = 177.4f
        const val ctaHeight = 44.3f
        const val ctaSize = 15.3f
        const val ctaTracking = -0.1f
    }

    object Rail {
        const val top = 449.3f
        const val width = 278f
        const val height = 60.7f
        val fill = Color(0xFF131313)
        val stroke = Color(0xFF2D2D2D)
        const val iconY = 30.4f
        val iconX = floatArrayOf(101.3f, 167.7f, 234f, 300.5f)
        const val calendar = 25.7f
        const val controller = 25.7f
        const val shapes = 21.4f
        const val sparkle = 24f
        const val dividerHeight = 27f
        val dividerColor = Color.White.copy(alpha = 0.2f)
    }

    object Bento {
        const val top = 529f
        const val left = 10.7f
        const val tileWidth = 184f
        const val tileHeight = 60.3f
        const val tilePitch = 73.8f
        const val tileRadius = 30.15f
        const val pitcastLeft = 207.7f
        const val pitcastWidth = 183.3f
        const val pitcastHeight = 210f
        const val pitcastRadius = 28f
        const val pitcastInset = 14.5f
        const val pitcastArrowInset = 12.5f
        const val pitcastMic = 50f
        const val pitcastMicRadius = 13.5f
        const val pitcastArrow = 18.5f
        const val pitcastGlyphRow = 179.8f
        val pitcastGlyphX = floatArrayOf(25.7f, 62f, 99f)
        const val pitcastGlyph = 20f
        val violet = Color(0xFF3221FC)
        val orange = Color(0xFFFE3F03)
        val yellow = Color(0xFFFFDA26)
        val cream = Color(0xFFF2EEDF)
        val micInk = Color(0xFFFFC4B2)
        const val eyebrowSize = 12.5f
        const val eyebrowTracking = -0.3f
        const val titleSize = 16.5f
        const val titleTracking = -0.2f
        const val violetSize = 19.5f
        const val violetTracking = -0.9f
        const val badgeSize = 13f
        const val chipIcon = 13f
        const val cardIcon = 24f
        const val pitcastTitleSize = 32f
        const val pitcastSubtitleSize = 12.2f
        const val pitcastNamesSize = 10.2f
        const val pitcastIcon = 26f
        const val pitcastArrowIcon = 8f
        const val eyebrowBaseline = 25.8f
        const val titleBaseline = 44.8f
        val violetBaselines = floatArrayOf(32f, 47.5f)
        const val violetX = 71.7f
        const val pitcastTitleBaseline = 106.8f
        const val pitcastSubtitleBaseline = 131.4f
        const val pitcastNamesBaseline = 152.5f
        const val violetBadgeWidth = 47.5f
        const val violetBadgeHeight = 28.5f
        const val violetBadgeCenterX = 38.5f
        const val violetBadgeStroke = 2.5f
        const val orangeChipWidth = 30f
        const val orangeChipHeight = 19f
        const val orangeChipTrailing = 14.7f
        const val orangeChipRadius = 5.5f
        const val statsWidth = 380.3f
        const val statsHeight = 144f
        const val statsRadius = 28f
        val statsGlow = Color(0xFF2A2828)
        val statsShade = Color(0xFF141215)
        const val statsIconX = 25f
        const val statsIconY = 27.5f
        const val statsIconWidth = 41f
        const val statsIconHeight = 51f
        const val statsIconSize = 50f
        const val statsTextX = 85.3f
        const val statsEyebrowSize = 16f
        const val statsEyebrowBaseline = 18.3f
        const val statsWordSize = 32.5f
        const val statsWordBaseline = 54.8f
        const val statsCaptionX = 21.3f
        const val statsCaptionSize = 12f
        val statsCaptionBaselines = floatArrayOf(123.4f, 138.2f)
        const val tileInset = 16f
        const val tileTextAfterMark = 60.5f
        const val tileMarkCenter = 31f
    }

    val carGrade = floatArrayOf(
        1.12f, 0f, 0f, 0f, -0.17f * 255f,
        0f, 1.14f, 0f, 0f, -0.17f * 255f,
        0f, 0f, 1.19f, 0f, -0.16f * 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    object PageTone {
        const val height = 450f
        val colors = listOf(
            Color(0xFF131313),
            Color(0xFF121212),
            Color(0xFF050505),
            Color(0xFF010101),
            Color.Black,
        )
        val stops = floatArrayOf(0f, 0.53f, 0.71f, 0.86f, 1f)
    }
}
