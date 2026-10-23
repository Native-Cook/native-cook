package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import androidx.compose.ui.graphics.Color

internal object PriceTagWelcomeTheme {
    const val REF_W = 1206.0
    const val REF_H = 2622.0
    const val REF_PT = 402.0
    const val ASCENT = 0.953

    object Colors {
        val page = Color(0xFFFCFBFC)
        val ink = Color(0xFF1C1C1E)
        val sub = Color(0xFF5E6260)
        val price = Color(0xFF111113)
        val tag = Color.White
        val button = Color(0xFF0387F5)
        val buttonRim = Color(0xD9D7FAFF)
        val buttonText = Color.White
        val etch = Color(0xB8264E70)
        val etchLight = Color(0xF2FFFFFF)
        val scrape = Color(0xF5F8FCFF)
        val scrapeEdge = Color(0x991C3A5C)
        val shadow = Color.Black
        val iconBacking = Color(0xFFB1E589)
    }

    data class Glow(
        val cx: Double,
        val cy: Double,
        val sigma: Double,
        val peak: Triple<Double, Double, Double>,
    )

    val glow = listOf(
        Glow(306.8, 576.4, 267.0, Triple(233.2, 242.4, 253.7)),
        Glow(964.0, 737.3, 244.0, Triple(252.7, 238.5, 228.3)),
        Glow(430.9, 1329.8, 302.0, Triple(234.3, 246.4, 238.8)),
    )
    val GLOW_BASE = Triple(251.8, 251.2, 251.5)

    data class TypeSpec(
        val size: Double,
        val weight: Int,
        val color: Color,
        val letterSpacing: Double,
    )

    object Type {
        val headline = TypeSpec(38.5, 600, Colors.ink, 0.0)
        val sub = TypeSpec(18.5, 400, Colors.sub, 0.0)
        val price = TypeSpec(21.0, 500, Colors.price, 0.0)
        val wait = TypeSpec(15.0, 600, Colors.etch, 0.0)
        val timer = TypeSpec(14.0, 600, Colors.scrape, 0.2)
        val button = TypeSpec(21.8, 600, Colors.buttonText, 0.0)
    }

    object Space {
        const val outline = 12.0
        const val canvasPad = 24.0
        const val stickerGap = 96.0
        const val screenInsetButton = 98.0
        const val headlineLine = 143.0
        const val subLine = 75.0
        const val tagPad = 18.0
        const val frostMargin = 42.0
        const val etchDrop = 9.0
        const val hit = 132.0
    }

    object Frost {
        val iceHi = Triple(236.0, 245.0, 252.0)
        val iceMid = Triple(186.0, 214.0, 238.0)
        val iceDeep = Triple(74.0, 122.0, 168.0)
        val coolTint = Triple(200.0, 224.0, 255.0)
        const val cell = 6.5
        const val tagCell = 3.5
        const val tagAlpha = 0.86
        const val halo = 0.55
    }

    object Radius {
        const val icon = 43.0
    }

    data class Tag(
        val cx: Double,
        val cy: Double,
        val w: Double,
        val h: Double,
        val tilt: Double,
    )

    data class Item(
        val key: String,
        val x: Double,
        val y: Double,
        val w: Double,
        val h: Double,
        val tag: Tag,
        val price: String,
        val name: String,
    )

    val items = listOf(
        Item("headphones", 24.0, 1616.0, 288.0, 324.0, Tag(225.0, 1870.0, 210.0, 88.0, -4.5), "\$579", "Headphones"),
        Item("laptop", 330.0, 1606.0, 324.0, 312.0, Tag(553.0, 1882.0, 240.0, 92.0, 5.5), "\$1599", "Laptop"),
        Item("shirt", 702.0, 1620.0, 300.0, 312.0, Tag(947.0, 1870.0, 160.0, 90.0, -4.0), "\$54", "Polo shirt"),
        Item("bag", 1050.0, 1620.0, 324.0, 312.0, Tag(1318.0, 1870.0, 186.0, 90.0, 3.0), "\$238", "Tote bag"),
        Item("camera", 1410.0, 1626.0, 324.0, 300.0, Tag(1672.0, 1862.0, 200.0, 90.0, -3.5), "\$849", "Camera"),
    )

    const val PERIOD = 1764.0
    const val WRAP_LEFT = -360.0

    object Row {
        const val top = 1590.0
        const val bottom = 1990.0
    }

    object Pivot {
        const val x = -0.2
        const val y = -0.5
    }

    object Layout {
        object Icon {
            const val x = 486.0
            const val y = 656.0
            const val w = 237.0
            const val h = 236.0

            object Box {
                const val x = 510.0
                const val y = 680.0
                const val w = 189.0
                const val h = 188.0
            }
        }

        val headlineBaselines = doubleArrayOf(1044.0, 1187.0)
        val subBaselines = doubleArrayOf(1339.0, 1414.0)

        object Button {
            const val x = 98.0
            const val w = 1010.0
            const val h = 211.0
            const val fromBottom = 379.0
            const val baseline = 131.0
        }

        data class TimerPosition(val fx: Double, val fy: Double)

        object Timer {
            val laptop = TimerPosition(0.56, 0.33)
            val other = TimerPosition(0.5, 0.44)
        }
    }

    object Copy {
        const val appName = "Interpause"
        val headline = listOf("Welcome to", "Interpause")
        val sub = listOf("Stop impulse purchases, understand", "the urge, and decide with clarity.")
        const val wait = "Wait 24h"
        const val countdown = 24 * 3600
        const val button = "Get started"
        const val a11yItemHint = "Double-tap to freeze the price for 24 hours, again to thaw it. Drag sideways to browse."
        const val a11yFrozen = "frozen, wait 24 hours before buying"
    }

    const val WAIT_EM = 4.15
}
