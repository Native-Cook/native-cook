package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

/** fhr-theme.ts and fhr-art.ts: measured on the 1206 × 2622 reference; every coordinate is a point of 402 × 874. */
internal object FrostHeartRewardsTheme {
    data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {
        val cx get() = x + w / 2
        val cy get() = y + h / 2
    }
    data class Sprite(val x: Double, val y: Double, val w: Double, val h: Double, val rotation: Double)
    data class Font(val size: Double, val weight: Int, val tracking: Double)
    data class Label(val text: String, val x: Double, val baseline: Double)
    data class Pill(val text: String, val x: Double, val y: Double, val w: Double, val h: Double, val r: Double, val baseline: Double, val rotation: Double = 0.0)
    data class Chip(val value: Int, val body: Box, val baseline: Double, val font: Font)

    const val REGULAR = 0; const val MEDIUM = 1; const val SEMIBOLD = 2; const val BOLD = 3

    // Palette.
    const val BACKGROUND = 0xFF131313.toInt()
    const val PANEL = 0xFF1A1A1C.toInt()
    const val CHIP = 0xFF25242A.toInt()
    const val CARD = 0xFF25242A.toInt()
    const val TEXT = 0xFFFFFEFF.toInt()
    const val BODY = 0xFFC0BFC5.toInt()
    const val TIMER = 0xFFB7B6BC.toInt()
    const val MUTED = 0xFF6A6A6A.toInt()
    const val PINK = 0xFFF924EE.toInt()
    const val VIOLET = 0xFFBE48F8.toInt()
    const val ORANGE = 0xFFFC7B20.toInt()
    const val TAB = 0xFF5B5962.toInt()
    const val ACTIVE_TAB = 0xFFC575F3.toInt()
    const val BADGE_RED = 0xFFFF0050.toInt()
    const val BLACK = 0xFF131313.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    // Type.
    val NAV = Font(18.3, SEMIBOLD, -0.35)
    val NAV_BRAND = Font(15.4, BOLD, -0.55)
    val PILL = Font(13.3, SEMIBOLD, -0.35)
    val BALANCE = Font(47.0, BOLD, 1.5)
    val NEXT_TITLE = Font(12.0, SEMIBOLD, 0.05)
    val REMAINING = Font(20.0, SEMIBOLD, -0.35)
    val FRACTION = Font(12.3, SEMIBOLD, 0.0)
    val AMOUNT = Font(19.3, SEMIBOLD, -0.1)
    val HEADING = Font(15.3, BOLD, 0.15)
    val FILTER = Font(12.0, BOLD, 0.1)
    val TASK_TITLE = Font(15.3, BOLD, -0.1)
    val TASK_BODY = Font(13.3, REGULAR, 0.05)
    val TASK_REWARD = Font(12.7, BOLD, -0.5)
    val TASK_TIMER = Font(12.3, BOLD, 0.1)
    val CART_BADGE = Font(13.0, REGULAR, -1.0)
    val LEVEL = Font(17.7, SEMIBOLD, -0.3)

    // Generated alpha layers (fhr-art.ts). Sprite rotations are degrees, the slab's radians.
    val BEAR = Sprite(63.5, 126.0, 230.0, 198.0, 2.0)
    val HEART = Sprite(58.0, 179.0, 251.0, 201.0, -0.5)
    val RAISED_PAW = Sprite(249.0, 174.0, 68.0, 83.0, 168.0)
    val FRONT_PAW = Sprite(54.5, 288.5, 90.0, 94.5, 0.0)
    val SLAB = Box(116.0, 250.0, 143.0, 82.0)
    const val SLAB_ROTATION = -0.36

    /** Neutral-preserving hue rotation with chroma retained in the purple body (Skia 4 × 5, offsets 0..1). */
    val AMETHYST = floatArrayOf(0.58f, 0.42f, 0f, 0f, 0f, -0.16f, 1.12f, 0.04f, 0f, 0f, 0.75f, -0.25f, 0.50f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    /** Small source-raster calibration shared by the sprites and the crystal backdrop. */
    val BEAR_MATRIX = floatArrayOf(.956408f, 0f, 0f, 0f, .019412f, 0f, 1.032831f, 0f, 0f, .024f, 0f, 0f, 1.030621f, 0f, -.005808f, 0f, 0f, 0f, 1f, 0f)
    val PAW_MATRIX = floatArrayOf(.570302f, .130380f, .057030f, 0f, .073195f, .012287f, .323003f, .008781f, 0f, .079719f, .087032f, .052223f, .635386f, 0f, .045082f, 0f, 0f, 0f, 1f, 0f)
    val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    /** a ∘ b for 4 × 5 colour matrices (b applied first). Nesting filters would replace, not compose. */
    fun compose(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (i in 0 until 4) for (j in 0 until 5) {
            var v = if (j == 4) a[i * 5 + 4] else 0f
            for (k in 0 until 4) v += a[i * 5 + k] * b[k * 5 + j]
            out[i * 5 + j] = v
        }
        return out
    }

    /** Android's ColorMatrix takes its offsets in 0..255, Skia's in 0..1. */
    fun android(m: FloatArray) = m.copyOf().also { for (i in 0 until 4) it[i * 5 + 4] = m[i * 5 + 4] * 255f }

    // Layout.
    val BACK = "M29 79 L22 86 L29 93"
    val CHAT = "M366 76 Q364 76 364 79 L364 90 Q364 92 367 92 L370 92 L370 97 L377 92 L383 92 Q386 92 386 89 L386 79 Q386 76 383 76 Z M369 81 L381 81 M369 85 L378 85"
    val HOME = "M30 821 L30 818 L39 810 Q41 808 43 810 L49 815 M31 820 L31 830 Q31 832 34 832 L40 832 M46 819 A6 6 0 1 0 46 831 A6 6 0 1 0 46 819 M50 829 L54 833"
    val LIST = "M111 812 L130 812 M111 820 L130 820 M111 828 L130 828"
    val WALLET = "M192 814 L198 809 L210 813 M192 813 L211 813 Q213 813 213 815 L213 829 Q213 831 211 831 L193 831 Q191 831 191 829 L191 816 Q191 813 194 813 M207 821 L213 821"
    val CART = "M272 808 L276 822 L293 817 L289 811 L275 811 M278 827 A1 1 0 1 0 278 829 M288 827 A1 1 0 1 0 288 829"
    val PROFILE = "M375 820 A11 11 0 1 0 353 820 A11 11 0 1 0 375 820 M364 812 A3.5 3.5 0 1 0 364 819 A3.5 3.5 0 1 0 364 812 M357 827 Q364 819 371 827"
    val CLOCK = "M94 752 A4 4 0 1 0 94 760 A4 4 0 1 0 94 752 M94 753 L94 756 L97 757 M92 750 L96 750 M91 760 L90 762 M97 760 L98 762"
    const val CHECK = "M-3 0 L-0.5 2.5 L4 -3.5"

    val TITLE = Label("my", 166.0, 91.67)
    val BRAND = Pill("lumi", 199.5, 75.33, 40.5, 19.33, 10.0, 90.67)
    val POINT_BRANDS = listOf(
        Pill("lumi", 16.33, 370.67, 27.67, 17.67, 8.8, 383.67, -5.0),
        Pill("wow", 44.33, 370.67, 35.0, 17.67, 8.8, 383.67, 4.0),
    )
    val NEXT_TITLE_LABEL = Label("To the next level", 190.67, 380.67)
    val UP = Box(273.67, 393.67, 27.67, 17.0)
    val AMOUNT_LABEL = Label("and \$150", 193.0, 441.33)
    val OF150 = Label("of \$150", 291.0, 442.33)
    val DAILY_HEADING = Label("UPs every day!", 16.33, 491.67)
    val CHIPS = listOf(
        Chip(5, Box(15.33, 510.67, 48.33, 36.33), 535.0, Font(15.7, BOLD, 0.0)),
        Chip(8, Box(69.33, 510.67, 48.33, 36.33), 535.0, Font(16.0, BOLD, 0.0)),
        Chip(10, Box(123.33, 510.67, 48.33, 36.33), 535.0, Font(16.3, SEMIBOLD, 1.0)),
        Chip(15, Box(177.67, 510.67, 48.0, 36.33), 535.0, Font(15.6, BOLD, 1.67)),
        Chip(20, Box(231.33, 510.67, 48.33, 36.33), 535.0, Font(15.4, BOLD, 1.0)),
        Chip(25, Box(285.33, 510.67, 48.33, 36.33), 535.0, Font(16.0, BOLD, 0.67)),
        Chip(50, Box(339.33, 510.67, 48.33, 36.33), 535.33, Font(16.0, BOLD, 0.0)),
    )
    const val CHIP_RADIUS = 18.17
    val FILTER_PANEL = Box(3.0, 579.33, 396.0, 294.67)
    const val FILTER_PANEL_RADIUS = 29.33
    val FILTER_PILLS = listOf(
        Pill("All", 15.33, 595.0, 59.33, 36.67, 18.33, 619.0) to true,
        Pill("Deals", 82.33, 595.0, 122.67, 37.0, 18.5, 619.0) to false,
        Pill("Pharmacy", 212.0, 594.67, 79.67, 37.33, 18.67, 619.0) to false,
        Pill("Beauty", 299.0, 595.0, 93.0, 37.0, 18.5, 619.0) to false,
    )
    val TASK_CARD = Box(15.0, 647.0, 373.0, 127.33)
    const val TASK_RADIUS = 25.0
    val TASK_ICON = Box(27.67, 659.0, 49.33, 49.33)
    val TASK_TITLES = listOf(Label("Discover more at lumi:", 88.67, 675.67), Label("sportswear essentials", 88.67, 694.33))
    val TASK_LINE1 = Label("See what's in our latest collection", 88.67, 718.67)
    val TASK_LINE2 = Label("and choose a new favourite", 88.67, 736.67)
    val TASK_REWARD_PILL = Pill("+200", 311.0, 661.0, 36.0, 18.33, 8.5, 675.0, -6.0)
    val TASK_REWARD_UP = Pill("UP", 348.33, 661.0, 27.33, 17.0, 8.5, 674.0, 3.0)
    val TASK_TIMER_LABEL = Label("2h 58m", 107.0, 760.33)
    val PEEK = Box(18.0, 784.0, 367.0, 125.0)
    const val PEEK_RADIUS = 26.0
    val TAB_BAR = Box(3.0, 797.0, 396.0, 77.0)
    val CART_BADGE_PILL = Pill("15", 281.33, 800.67, 22.67, 19.67, 10.0, 815.67)

    // The rays of the page background (RAYS_SKSL with every animated term at rest).
    val RAY_ANGLES = doubleArrayOf(-173.0, -158.0, -143.0, -130.0, -119.0, -108.0, -96.0, -84.0, -71.0, -58.0, -44.0, -29.0, -15.0, -3.0)
    val RAY_STRENGTHS = doubleArrayOf(0.45, 0.55, 0.48, 0.72, 0.61, 0.6, 0.76, 0.59, 0.72, 0.55, 0.68, 0.69, 0.38, 0.33)

    // The metal level tag.
    const val BADGE = "M302 154 C326 165 348 191 352 218"
    const val BADGE_BASELINE = "M298 158 C322 169 344 195 348 222"
    const val BADGE_HIGHLIGHT = "M298 158.65 C322 169.65 344 195.65 348 222.65"
    val TAG_VIEW = Box(280.0, 136.0, 92.0, 112.0)
}
