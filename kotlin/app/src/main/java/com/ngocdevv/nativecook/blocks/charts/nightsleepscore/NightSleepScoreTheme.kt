package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import androidx.compose.ui.graphics.Color

/** Tokens of the block, the same names and values as the React Native theme.ts (design points of a 402 pt screen). */
internal fun nightSleepScoreHex(hex: Long, alpha: Float = 1f) = Color(
    red = ((hex shr 16) and 255).toInt() / 255f, green = ((hex shr 8) and 255).toInt() / 255f,
    blue = (hex and 255).toInt() / 255f, alpha = alpha,
)

internal fun nightSleepScoreMix(day: Color, night: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        day.red + (night.red - day.red) * k, day.green + (night.green - day.green) * k,
        day.blue + (night.blue - day.blue) * k, day.alpha + (night.alpha - day.alpha) * k,
    )
}

internal class NightSleepScoreStages(val awake: Color, val rem: Color, val core: Color, val deep: Color) {
    fun color(stage: NightSleepScoreStage): Color = when (stage) {
        NightSleepScoreStage.AWAKE -> awake
        NightSleepScoreStage.REM -> rem
        NightSleepScoreStage.CORE -> core
        NightSleepScoreStage.DEEP -> deep
    }
}

internal object NightSleepScoreTheme {
    object Day {
        val ink = nightSleepScoreHex(0x101113)
        val secondary = nightSleepScoreHex(0x8D8D92)
        val divider = nightSleepScoreHex(0xE4E3EB)
        val border = Color.White.copy(alpha = .75f)
        val accent = nightSleepScoreHex(0x128CF0)
        val highlight = nightSleepScoreHex(0x128CF0)
        val stages = NightSleepScoreStages(nightSleepScoreHex(0xEF806B), nightSleepScoreHex(0x32B4D5), nightSleepScoreHex(0x128CF0), nightSleepScoreHex(0x4934A2))
    }

    object Night {
        val ink = nightSleepScoreHex(0xF4F5FF)
        val secondary = Color(214 / 255f, 220 / 255f, 1f, .62f)
        val divider = Color.White.copy(alpha = .13f)
        val border = Color.White.copy(alpha = .16f)
        val accent = nightSleepScoreHex(0x7CC0FF)
        val highlight = Color(186 / 255f, 210 / 255f, 1f, 1f)
        val stages = NightSleepScoreStages(nightSleepScoreHex(0xFF8F7A), nightSleepScoreHex(0x5CE2F6), nightSleepScoreHex(0x3F9DFF), nightSleepScoreHex(0xA58CFF))
        val trackRing = Color.White.copy(alpha = .09f)
        val playhead = Color.White
    }

    val page = nightSleepScoreHex(0xF1F0F6)
    val card = Color(246 / 255f, 246 / 255f, 1f, .96f)
    val teal = nightSleepScoreHex(0x13C9BB)
    val orange = nightSleepScoreHex(0xF67D65)
    val blue = nightSleepScoreHex(0x4E63F8)
    val shadow = nightSleepScoreHex(0x2F5BD6, .2f)

    object Card {
        const val width = 370f
        const val height = 317f
        const val radius = 28f

        object Chart {
            const val left = 207.8f
            const val top = 152f
            const val width = 145.5f
            const val height = 82f
            const val touchInset = 12f
            const val barHeight = 14f
            const val barRadius = 4f
            const val barGap = .7f
            fun level(stage: NightSleepScoreStage): Float = when (stage) {
                NightSleepScoreStage.AWAKE -> 7.7f
                NightSleepScoreStage.REM -> 28.8f
                NightSleepScoreStage.CORE -> 52.5f
                NightSleepScoreStage.DEEP -> 73.4f
            }
            val grid = listOf(18.7f, 40.3f, 63f)
            val gridColor = nightSleepScoreHex(0xD3D6E2)
            val gridDash = floatArrayOf(1.2f, 2.4f)
            val connectorColor = nightSleepScoreHex(0xA9CCF3, .62f)
            const val connectorWidth = 3.2f
            val coreTrack = nightSleepScoreHex(0xD2E4F8)
        }

        object Ring {
            const val cx = 304.8f
            const val cy = 66f
            const val radius = 39f
            const val stroke = 16f
        }

        object Meteor {
            const val fromX = 252f
            const val fromY = 16f
            const val toX = 160f
            const val toY = 64f
            const val tail = 44f
        }

        /** Card heights (0 top, 1 bottom) where each layer meets the night front. */
        object At {
            const val label = 28.0 / 317
            const val title = 47.0 / 317
            const val duration = 80.0 / 317
            const val durationValue = 100.0 / 317
            const val ring = 66.0 / 317
            const val divider1 = 134.0 / 317
            val legend = listOf(163.0 / 317, 185.0 / 317, 207.0 / 317, 229.0 / 317)
            const val chart = 193.0 / 317
            const val divider2 = 247.0 / 317
            const val readout = 276.0 / 317
            const val readoutSub = 298.0 / 317
        }
    }

    object Aurora {
        const val transitionHalfWidth = 1.4f
        const val core = 2.2f
        const val hotCore = .8f
        const val hotCoreOpacity = .55f
        const val glowWidth = 6f
        const val glowBlur = 3.5f
        const val glowOpacity = .75f
        class Curtain(val width: Float, val lift: Float, val blur: Float, val opacity: Float)
        val curtains = listOf(Curtain(14f, 8f, 6f, .55f), Curtain(28f, 19f, 11f, .3f))
        const val headGlow = 7f
        const val headGlowBlur = 5f
        const val headCore = 2.4f
        const val playheadWidth = 1f
    }

    object Rail {
        const val top = .7f
        const val height = 57.5f
        const val radius = 20f
        const val icon = 30.7f
        const val iconCentre = 28.3f
        const val textLeft = 54f
        const val titleTop = 10.3f
        const val valueTop = 29.3f
        const val textSize = 15f
        const val valueSize = 14.5f
        const val textLine = 18f

        object Heart {
            const val left = 16.7f
            const val width = 61.6f
            const val heartSize = 17.5f
            const val heartCentre = 22.3f
            const val lineLeft = 20f
            val lines = listOf(33.8f to 21.7f, 40.5f to 16f)
            const val lineHeight = 1.7f
        }

        val sleep = 82.7f to 109f
        val readiness = 195f to 140.3f
        val fitness = 340.7f to 112f
        val tile = Color.White
        val chip = nightSleepScoreHex(0xEFEEF3)
        val chipBorder = nightSleepScoreHex(0xCBCAD1)
        val chipRim = Color.White.copy(alpha = .85f)
        val line = nightSleepScoreHex(0xCFCFD5)
        val title = nightSleepScoreHex(0x8D8D92)
        val value = nightSleepScoreHex(0x101113)
        val heart = nightSleepScoreHex(0xEE3D83)
        val track = nightSleepScoreHex(0xDEDDE4)
        val gaugeLight = nightSleepScoreHex(0xB9D6EE)
        val gaugeBlue = nightSleepScoreHex(0x4A9BE0)
        val fitnessRed = nightSleepScoreHex(0xEE2D6E)
        val fitnessPink = nightSleepScoreHex(0xF7B3CB)
        val fitnessGreen = nightSleepScoreHex(0x55D45A)
        val fitnessCyan = nightSleepScoreHex(0x52C7EE)
    }

    object Tab {
        const val width = 290f
        const val height = 60f
        const val radius = 30f
        const val bottom = 22f
        const val padding = 4f
        const val item = 94f
        const val itemHeight = 52f
        const val icon = 26.5f
        const val iconCentre = 20.5f
        const val labelSize = 10.2f
        const val labelLine = 13f
        const val labelTop = 35.5f
        const val edgeHeight = 118f
        const val edgeSolidFrom = .36f
        const val edgeIntensity = 34f
        val lensGlassTint = Color(214 / 255f, 214 / 255f, 224 / 255f, .55f)
        val lensFallback = Color(226 / 255f, 226 / 255f, 233 / 255f, .72f)
        val lensSheen = Color.White.copy(alpha = .55f)
        const val lensSheenTo = .5f
        val lensRim = Color.White.copy(alpha = .9f)
        val outline = Color(60 / 255f, 60 / 255f, 90 / 255f, .12f)
        val glassTint = Color(222 / 255f, 228 / 255f, 250 / 255f, .6f)
        val fallback = Color(248 / 255f, 248 / 255f, 252 / 255f, .72f)
        val border = Color.White.copy(alpha = .9f)
        val active = nightSleepScoreHex(0x167FE8)
        val ink = nightSleepScoreHex(0x101113)
        val cardWhite = Color.White
        val shadow = Color.Black.copy(alpha = .12f)
    }

    /** Readiness gauge, measured on the pin: centre (304, 65.3) in the card. */
    object Gauge {
        const val cx = 304f
        const val cy = 65.3f
        const val r = 44.5f
        const val stroke = 11f
        const val from = .53f
        const val to = .97f
        const val lightFrom = .72f
        const val lightTo = .86f
        const val dot = .842f
        const val dotR = 3.4f
        const val numberBase = 5f
        const val numberSize = 30f
        val track = nightSleepScoreHex(0xE4E3EB)
        val light = nightSleepScoreHex(0xB9D6EE)
        val blue = nightSleepScoreHex(0x3A8DDE)
    }
}
