package com.ngocdevv.nativecook.blocks.progress.healthagedial

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

internal const val HEALTH_AGE_DIAL_LOOP: Double = 10.0

internal data class HealthAgeDialAges(
    val health: Double,
    val actual: Double,
)

internal data class HealthAgeDialRingStop(
    val angle: Double,
    val color: Color,
)

internal data class HealthAgeDialPoint(
    val x: Double,
    val y: Double,
)

internal data class HealthAgeDialTextStyle(
    val size: Double,
    val weight: FontWeight,
    val color: Color,
    val line: Double? = null,
    val tracking: Double = 0.0,
)

internal data class HealthAgeDialInfoLayout(
    val cx: Double,
    val cy: Double,
    val diameter: Double,
) {
    val d: Double
        get() = diameter
}

internal data class HealthAgeDialHaloLayout(
    val cx: Double,
    val cy: Double,
    val radius: Double,
)

internal data class HealthAgeDialChartLayout(
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
    val columns: Int,
    val base: Double,
    val baseRight: Double,
    val labelBaseline: Double,
    val labelOffset: Double,
    val olderBaseline: Double,
    val youngerBaseline: Double,
    val guideX: Double,
    val dotRadius: Double,
    val dotRing: Double,
    val lineWidth: Double,
) {
    val dotR: Double
        get() = dotRadius
}

internal data class HealthAgeDialCardLayout(
    val top: Double,
    val height: Double,
)

internal data class HealthAgeDialCardIconLayout(
    val cy: Double,
    val diameter: Double,
    val glyph: Double,
)

internal data class HealthAgeDialPillLayout(
    val width: Double,
    val height: Double,
)

internal data class HealthAgeDialTabIconLayout(
    val heart: Double,
    val petal: Double,
    val grid: Double,
    val labelGap: Double,
)

internal data class HealthAgeDialFactor(
    val title: String,
    val years: Double,
    val body: String,
)

internal object HealthAgeDialColors {
    val page = Color(0xFFF1EFF8)
    val ink = Color(0xFF0E0E10)
    val secondary = Color(0xFF8B8A92)
    val label = Color(0xFF8E8D94)
    val years = Color(0xFF8D91A2)
    val blue = Color(0xFF0091FF)
    val chipFill = Color(red = 250f / 255f, green = 249f / 255f, blue = 253f / 255f, alpha = 0.72f)
    val chipBorder = Color.Black.copy(alpha = 0.12f)
    val chipSelected = Color(0xFFFDFDFE)
    val info = Color(0xFFD0CFD5)
    val infoGlyph = Color(0xFF1F1E23)
    val tick = Color(0xFFA0A8D6)
    val halo = Color(0xFFB9DBFA)
    val card = Color(0xFFFDFDFE)
    val sleep = Color(0xFF7366D8)
    val sleepTint = Color(0xFFF3F1FD)
    val sleepGlyph = Color(0xFF6452E4)
    val hrv = Color(0xFFD2376D)
    val hrvTint = Color(0xFFFDEEF3)
    val hrvGlyph = Color(0xFFE2477A)
    val cardBody = Color(0xFF2E2E33)
    val grid = Color(0xFFDCDBE3)
    val baseline = Color(0xFFBDBCC2)
    val axis = Color(0xFFC6C5CC)
    val guide = Color(0xFFCDCCD3)
    val line = Color(0xFFA5B3DD)
    val lineEnd = Color(0xFF9EC3EC)
    val fillTop = Color(red = 206f / 255f, green = 214f / 255f, blue = 245f / 255f, alpha = 0f)
    val fillBottom = Color(0xFFB6D5F7)
    val chartDot = Color(0xFF0A9ADB)
    val tabText = Color(0xFF141416)
    val tabSelected = Color(red = 200f / 255f, green = 199f / 255f, blue = 206f / 255f, alpha = 0.7f)
    val tabBlue = Color(0xFF1E86EE)
    val white = Color.White
}

internal object HealthAgeDialSpace {
    val textInset = 31.0
    val cardInset = 16.0
    val chipInset = 18.67
    val chipGap = 6.0
    val chipInsetTrailing = 20.0
    val chipPadLeading = 13.67
    val chipIconGap = 9.3
    val statColumn = 211.3
    val cardGap = 10.33
    val cardIconLeading = 16.67
    val cardTextLeading = 77.0
    val tabBottom = 23.0
}

internal object HealthAgeDialRadius {
    val chip = 20.0
    val card = 24.0
    val tab = 31.5
    val tabPill = 28.0
}

internal object HealthAgeDialType {
    val chipTitle = HealthAgeDialTextStyle(15.0, FontWeight.Normal, HealthAgeDialColors.secondary)
    val chipValue = HealthAgeDialTextStyle(14.3, FontWeight.SemiBold, HealthAgeDialColors.ink)
    val number = HealthAgeDialTextStyle(80.0, FontWeight.Bold, HealthAgeDialColors.blue, tracking = -3.0)
    val unit = HealthAgeDialTextStyle(15.2, FontWeight.SemiBold, HealthAgeDialColors.years)
    val title = HealthAgeDialTextStyle(32.7, FontWeight.Bold, HealthAgeDialColors.ink, line = 41.0)
    val body = HealthAgeDialTextStyle(21.85, FontWeight.Normal, HealthAgeDialColors.secondary, line = 28.0)
    val statLabel = HealthAgeDialTextStyle(15.3, FontWeight.Medium, HealthAgeDialColors.label)
    val statValue = HealthAgeDialTextStyle(19.5, FontWeight.Bold, HealthAgeDialColors.ink, tracking = -0.4)
    val heading = HealthAgeDialTextStyle(21.0, FontWeight.SemiBold, HealthAgeDialColors.ink)
    val axis = HealthAgeDialTextStyle(14.0, FontWeight.Normal, HealthAgeDialColors.axis)
    val guide = HealthAgeDialTextStyle(13.0, FontWeight.Normal, HealthAgeDialColors.guide)
    val cardTitle = HealthAgeDialTextStyle(15.0, FontWeight.SemiBold, HealthAgeDialColors.ink)
    val cardValue = HealthAgeDialTextStyle(28.0, FontWeight.Medium, HealthAgeDialColors.ink)
    val cardBody = HealthAgeDialTextStyle(15.0, FontWeight.Normal, HealthAgeDialColors.cardBody, line = 20.0)
    val tab = HealthAgeDialTextStyle(9.8, FontWeight.SemiBold, HealthAgeDialColors.tabText)
}

internal object HealthAgeDialBaseline {
    val chipTitle = 87.3
    val chipValue = 106.7
    val number = 325.8
    val unit = 355.3
    val title = 512.0
    val body = 586.8
    val statLabel = 718.9
    val statValue = 744.5
}

internal object HealthAgeDialLayout {
    const val pinWidth: Double = 402.0
    const val pinSafeTop: Double = 62.0

    object Chip {
        val top = 61.33
        val height = 61.33
        val icon = 32.7
        val ring = 31.0
        val leftShare = 177.33 / 357.33
    }

    object Dial {
        val cx = 200.0
        val cy = 305.33
        val radius = 133.33
        val r = radius
        val stroke = 40.33
        val start = -156.0
        val end = 156.0
        val tickLength = 20.0
        val tickWidth = 4.0
        val dot = 26.0
        val degreesPerYear = 14.36
        val degPerYear = degreesPerYear
        val maxYears = 9.9
        val canvasPad = 70.0
        val edgeSoftness = 1.8
    }

    val info = HealthAgeDialInfoLayout(357.67, 173.0, 25.0)
    val numberCenterX = 188.1
    val unitCenterX = 200.5
    val halo = HealthAgeDialHaloLayout(160.0, 300.0, 228.0)
    val historyHeading = 802.3
    val chart = HealthAgeDialChartLayout(
        top = 826.0,
        bottom = 946.67,
        left = 31.0,
        right = 316.0,
        columns = 6,
        base = 860.67,
        baseRight = 371.0,
        labelBaseline = 966.3,
        labelOffset = 6.3,
        olderBaseline = 846.7,
        youngerBaseline = 906.0,
        guideX = 330.0,
        dotRadius = 5.0,
        dotRing = 1.6,
        lineWidth = 3.0,
    )
    val raisingHeading = 1024.3
    val cards = listOf(
        HealthAgeDialCardLayout(1047.0, 161.67),
        HealthAgeDialCardLayout(1219.0, 161.67),
    )
    val cardIcon = HealthAgeDialCardIconLayout(43.0, 46.67, 24.0)
    val cardTitleBaseline = 31.6
    val cardValueBaseline = 64.6
    val cardBodyBaseline = 100.6
    val contentBottom = 1380.67

    object Tab {
        val width = 292.0
        val height = 63.0
        val item = 91.67
        val pillWidth = 99.0
        val pillHeight = 55.0
        val heart = 28.0
        val petal = 29.0
        val grid = 26.7
        val labelGap = 4.0
        val pill = HealthAgeDialPillLayout(width = pillWidth, height = pillHeight)
        val icon = HealthAgeDialTabIconLayout(
            heart = heart,
            petal = petal,
            grid = grid,
            labelGap = labelGap,
        )
    }

    val historyOffset = 550.0
}

internal val ringStops = listOf(
    HealthAgeDialRingStop(-164.0, Color(0xFFC3E3E7)),
    HealthAgeDialRingStop(-152.0, Color(0xFFA9DBE2)),
    HealthAgeDialRingStop(-138.0, Color(0xFF96D7E3)),
    HealthAgeDialRingStop(-122.0, Color(0xFF83D1EA)),
    HealthAgeDialRingStop(-104.0, Color(0xFF7AC7F2)),
    HealthAgeDialRingStop(-88.0, Color(0xFF7BC0F5)),
    HealthAgeDialRingStop(-70.0, Color(0xFF83BBF7)),
    HealthAgeDialRingStop(-50.0, Color(0xFF8DB5F6)),
    HealthAgeDialRingStop(-30.0, Color(0xFF9CB7F2)),
    HealthAgeDialRingStop(-10.0, Color(0xFFACB9F3)),
    HealthAgeDialRingStop(8.0, Color(0xFFB8C0EF)),
    HealthAgeDialRingStop(28.0, Color(0xFFC9C7EC)),
    HealthAgeDialRingStop(50.0, Color(0xFFD5D1E7)),
    HealthAgeDialRingStop(72.0, Color(0xFFDBD9E8)),
    HealthAgeDialRingStop(96.0, Color(0xFFDDDDE3)),
    HealthAgeDialRingStop(130.0, Color(0xFFDEDDE3)),
    HealthAgeDialRingStop(164.0, Color(0xFFE1DFE5)),
)

internal val historyPoints = listOf(
    HealthAgeDialPoint(0.0, 15.1),
    HealthAgeDialPoint(5.3, 14.6),
    HealthAgeDialPoint(10.7, 14.2),
    HealthAgeDialPoint(16.0, 14.4),
    HealthAgeDialPoint(21.3, 15.1),
    HealthAgeDialPoint(26.7, 15.8),
    HealthAgeDialPoint(32.0, 20.6),
    HealthAgeDialPoint(37.3, 24.9),
    HealthAgeDialPoint(42.7, 34.5),
    HealthAgeDialPoint(48.0, 37.8),
    HealthAgeDialPoint(53.3, 37.4),
    HealthAgeDialPoint(58.7, 36.3),
    HealthAgeDialPoint(64.0, 35.0),
    HealthAgeDialPoint(69.3, 35.0),
    HealthAgeDialPoint(74.7, 34.0),
    HealthAgeDialPoint(80.0, 28.2),
    HealthAgeDialPoint(85.3, 23.7),
    HealthAgeDialPoint(90.7, 22.3),
    HealthAgeDialPoint(96.0, 23.2),
    HealthAgeDialPoint(101.3, 24.7),
    HealthAgeDialPoint(106.7, 25.4),
    HealthAgeDialPoint(112.0, 31.1),
    HealthAgeDialPoint(117.3, 33.7),
    HealthAgeDialPoint(122.7, 30.3),
    HealthAgeDialPoint(128.0, 38.1),
    HealthAgeDialPoint(133.3, 40.9),
    HealthAgeDialPoint(138.7, 43.8),
    HealthAgeDialPoint(144.0, 45.7),
    HealthAgeDialPoint(149.3, 51.9),
    HealthAgeDialPoint(154.7, 55.8),
    HealthAgeDialPoint(160.0, 54.1),
    HealthAgeDialPoint(165.3, 53.3),
    HealthAgeDialPoint(170.7, 52.2),
    HealthAgeDialPoint(176.0, 51.0),
    HealthAgeDialPoint(181.3, 51.8),
    HealthAgeDialPoint(186.7, 53.9),
    HealthAgeDialPoint(192.0, 56.4),
    HealthAgeDialPoint(197.3, 58.4),
    HealthAgeDialPoint(202.7, 56.7),
    HealthAgeDialPoint(208.0, 55.5),
    HealthAgeDialPoint(213.3, 57.5),
    HealthAgeDialPoint(218.7, 59.4),
    HealthAgeDialPoint(224.0, 59.1),
    HealthAgeDialPoint(233.0, 58.3),
)

internal val months = listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr")

internal object HealthAgeDialCopy {
    val overviewTitle = "Overview"
    val overviewValue = "5 Excellent"
    val chipTitle = "Health Age"
    val chipValue = "-3.9 years"
    val unit = "YEARS"
    val title = "Build on Your Healthy Habits"
    val body = "Your current habits are serving you well. Keep leaning into what’s working to help your body stay younger for years to come."
    val healthAgeLabel = "Health Age"
    val healthAgeValue = "26.1 years"
    val actualAgeLabel = "Actual Age"
    val actualAgeValue = "30 years"
    val history = "Health Age History"
    val older = "Older"
    val younger = "Younger"
    val raising = "Raising Your Health Age"
    val factors = listOf(
        HealthAgeDialFactor(
            title = "Sleep Score",
            years = 1.3,
            body = "Your average sleep score of 70 suggests you could benefit from more restorative rest to help support healthy aging.",
        ),
        HealthAgeDialFactor(
            title = "Heart Rate Variability",
            years = 0.3,
            body = "Your overnight HRV of 38.5 signals your body is carrying some stress. Focus on steady recovery to help it bounce back.",
        ),
    )
    val factorTitles = factors.map { it.title }
    val factorYears = factors.map { it.years }
    val factorBodies = factors.map { it.body }
    val tabs = listOf("Insights", "Longevity", "Browse")
}

internal val AGES = HealthAgeDialAges(health = 26.1, actual = 30.0)
internal const val REST_YEARS: Double = -3.9

internal val PIN_WIDTH: Double = HealthAgeDialLayout.pinWidth
internal val PIN_SAFE_TOP: Double = HealthAgeDialLayout.pinSafeTop
internal val colors get() = HealthAgeDialColors
internal val space get() = HealthAgeDialSpace
internal val radius get() = HealthAgeDialRadius
internal val type get() = HealthAgeDialType
internal val baseline get() = HealthAgeDialBaseline
internal val layout get() = HealthAgeDialLayout
internal val copy get() = HealthAgeDialCopy

internal object HealthAgeDialTheme {
    val pinWidth: Double = HealthAgeDialLayout.pinWidth
    val pinSafeTop: Double = HealthAgeDialLayout.pinSafeTop
    val ages: HealthAgeDialAges = AGES
    val restYears: Double = REST_YEARS
    val Colors get() = HealthAgeDialColors
    val Space get() = HealthAgeDialSpace
    val Radius get() = HealthAgeDialRadius
    val TypeStyle get() = HealthAgeDialType
    val Baseline get() = HealthAgeDialBaseline
    val Layout get() = HealthAgeDialLayout
    val Copy get() = HealthAgeDialCopy
    val ringStops: List<HealthAgeDialRingStop> = com.ngocdevv.nativecook.blocks.progress.healthagedial.ringStops
    val historyPoints: List<HealthAgeDialPoint> = com.ngocdevv.nativecook.blocks.progress.healthagedial.historyPoints
    val months: List<String> = com.ngocdevv.nativecook.blocks.progress.healthagedial.months
}
