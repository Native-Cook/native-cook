package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

data class ForYouInsightsTypeStyle(
    val size: Double,
    val line: Double? = null,
    val weight: FontWeight,
    val tracking: Double,
)

object ForYouInsightsTheme {
    const val REF_WIDTH = 402.0
    const val REF_SAFE_TOP = 62.0

    object Colors {
        val page = Color(0xFFF2F1F6)
        val ink = Color(0xFF111113)
        val secondary = Color(0x993C3C43)
        val tertiary = Color(0x943C3C43)
        val meta = Color(0xA83C3C43)
        val railTitle = Color(0xB83C3C43)
        val separator = Color(0x213C3C43)
        val pink = Color(0xFFE648A5)
        val violet = Color(0xFF8B5CF6)
        val blue = Color(0xFF1E8BEA)
        val white = Color.White
        val tile = Color(0xFFF5F4FA)
        val tileBorder = Color(0x383C3C43)
        val tileHeart = Color.White
        val heart = Color(0xFFF23A7E)
        val heartLine = Color(0x333C3C43)
        val sleepTeal = Color(0xFF21C7B4)
        val sleepOrange = Color(0xFFF3826A)
        val sleepBlue = Color(0xFF4E63F8)
        val readyTrack = Color(0x213C3C43)
        val readyLight = Color(0xFFA9CCEB)
        val readyStrong = Color(0xFF3C8FDA)
        val fitMove = Color(0xFFEE2A6B)
        val fitExercise = Color(0xFF64D23C)
        val fitStand = Color(0xFF21C7D9)
        val ellipsisFill = Color(0x143C3C43)
        val pillFill = Color(0x1F767680)
        val pillEdge = Color(0xB3FFFFFF)
        val tabGlassTint = Color(0x9EFFFFFF)
        val tabFallback = Color(0xD1FAFAFC)
        val tabEdge = Color(0xCCFFFFFF)
        val closeDisc = Color(0xDBFFFFFF)
        val shadow = Color(0xFF1B1530)
        val caretGlow = Color(0xFFF062B8)
        val ghost = Color(0x29111113)
        val shimmer = listOf(
            Color.Transparent,
            Color(0xD9FFD6F0),
            Color(0xF2FFFFFF),
            Color(0xD9CDE2FF),
            Color.Transparent,
        )
    }

    object Type {
        val heading = ForYouInsightsTypeStyle(28.0, weight = FontWeight.SemiBold, tracking = -0.2)
        val label = ForYouInsightsTypeStyle(12.0, weight = FontWeight.Bold, tracking = 0.46)
        val tip = ForYouInsightsTypeStyle(21.0, 27.0, FontWeight.Bold, -0.24)
        val rowTitle = ForYouInsightsTypeStyle(18.0, 22.2, FontWeight.SemiBold, -0.5)
        val rowSummary = ForYouInsightsTypeStyle(15.5, 20.0, FontWeight.Normal, -0.15)
        val rowMeta = ForYouInsightsTypeStyle(14.0, weight = FontWeight.Medium, tracking = 0.0)
        val railTitle = ForYouInsightsTypeStyle(14.0, 18.0, FontWeight.Normal, -0.1)
        val railValue = ForYouInsightsTypeStyle(14.5, 19.0, FontWeight.Bold, -0.3)
        val railNumber = ForYouInsightsTypeStyle(12.5, weight = FontWeight.Bold, tracking = 0.0)
        val tab = ForYouInsightsTypeStyle(10.5, weight = FontWeight.SemiBold, tracking = 0.0)
        val readerTitle = ForYouInsightsTypeStyle(30.0, 36.0, FontWeight.Bold, -0.6)
        val readerMeta = ForYouInsightsTypeStyle(13.0, weight = FontWeight.SemiBold, tracking = 0.3)
        val readerLead = ForYouInsightsTypeStyle(18.0, 26.0, FontWeight.Medium, -0.2)
        val readerHeading = ForYouInsightsTypeStyle(19.0, 25.0, FontWeight.Bold, -0.3)
        val readerBody = ForYouInsightsTypeStyle(16.0, 24.0, FontWeight.Normal, -0.1)
        val sectionSubhead = ForYouInsightsTypeStyle(15.0, weight = FontWeight.Medium, tracking = -0.1)
        val habitTitle = ForYouInsightsTypeStyle(18.0, weight = FontWeight.SemiBold, tracking = -0.2)
        val habitBody = ForYouInsightsTypeStyle(15.0, 21.0, FontWeight.Normal, -0.1)
    }

    object Space {
        const val XXS = 4.0
        const val XS = 8.0
        const val SM = 12.0
        const val MD = 16.0
        const val LG = 20.0
        const val XL = 24.0
        const val XXL = 28.0
        const val SCREEN = 32.0
    }

    object Radius {
        const val TILE = 20.0
        const val IMAGE = 26.0
        const val HERO = 28.0
        const val TAB_BAR = 31.0
        const val TAB_PILL = 26.0
        const val ELLIPSIS = 10.0
        const val CLOSE = 18.0
        const val HABIT_ICON = 14.0
    }

    object Layout {
        const val RAIL_TOP = 62.0
        const val RAIL_HEIGHT = 58.0
        const val RAIL_INSET = Space.MD
        const val RAIL_GAP = Space.XXS
        const val RAIL_DIVIDER = 127.7
        val TILE_WIDTHS = listOf(62.7, 108.3, 134.0, 112.0)
        const val RAIL_RING = 33.0
        const val RAIL_RING_PAD = Space.SM
        const val RAIL_TEXT_GAP = 10.0
        const val RAIL_TITLE_TOP = 71.8
        const val RAIL_VALUE_TOP = 90.0
        const val HEART_TOP = 76.0
        const val HEART_SIZE = 20.0
        val HEART_LINES = listOf(21.0 to 97.5, 16.0 to 104.0)
        const val COLUMN = Space.SCREEN
        const val COLUMN_WIDTH = 338.0
        const val HEADING_TOP = 150.7
        const val HEADING_DIVIDER = 200.7
        const val LABEL_TOP = 218.0
        const val LABEL_HEIGHT = 20.0
        const val LABEL_ICON = 12.0
        const val LABEL_INSET = 1.5
        const val LABEL_ICON_GAP = Space.XXS
        const val ELLIPSIS_CENTER_X = 358.8
        const val ELLIPSIS_CENTER_Y = 227.2
        const val ELLIPSIS_SIZE = 20.0
        const val ELLIPSIS_GLYPH = 15.0
        const val TIP_TOP = 243.7
        const val TIP_LINES = 5
        const val TIP_DIVIDER = 394.7
        const val ROW_TOP = 411.0
        const val BROWSE_ROW_TOP = 254.0
        const val HABIT_TOP = 254.0
        const val HABIT_PITCH = 120.0
        const val HABIT_ICON = 44.0
        const val ROW_PITCH = 187.0
        const val ROW_TEXT_WIDTH = 151.0
        const val ROW_IMAGE_X = 216.0
        const val ROW_IMAGE_SIZE = 154.0
        const val ROW_SUMMARY_OFFSET = 70.3
        const val ROW_META_OFFSET = 135.7
        const val ROW_META_ICON = 14.0
        const val ROW_META_GAP = 5.0
        const val ROW_DIVIDER_OFFSET = 170.7
        const val TAB_BAR_TOP = 791.0
        const val TAB_BAR_WIDTH = 290.0
        const val TAB_BAR_HEIGHT = 62.0
        const val TAB_PILL_WIDTH = 97.0
        const val TAB_PILL_HEIGHT = 52.0
        const val TAB_PITCH = 91.5
        const val TAB_ICON = 27.0
        const val TAB_ICON_CENTER = 25.0
        const val TAB_LABEL_CENTER = 47.3
        const val HERO_TOP = 70.0
        const val HERO_INSET = Space.LG
        const val HERO_HEIGHT = 300.0
        const val CLOSE_SIZE = 36.0
        const val CLOSE_INSET = Space.SM
        const val READER_TEXT_INSET = Space.XXL
        const val READER_TITLE_GAP = Space.XL
        const val READER_META_GAP = Space.SM
        const val READER_SECTION_GAP = Space.XL
        const val READER_PARAGRAPH_GAP = Space.XS
        const val READER_BOTTOM = Space.SCREEN
        const val DIVIDER = 1.0
    }
}
