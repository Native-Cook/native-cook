package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import androidx.annotation.ColorInt

internal object PastePlacesImportTheme {
    const val REF_W = 1206f
    const val REF_PT = 402f
    const val REF_INSET = 186f
    const val ASCENT = 0.795f
    const val DESCENT = 0.24f
    const val SF_ASCENT = 0.953f
    const val SF_DESCENT = 0.241f
    const val HAIRLINE = 2f

    object Font {
        const val text = "pasteplacesimport/pasteplacesimport_Anybody-Text.ttf"
        const val extra = "pasteplacesimport/pasteplacesimport_Anybody-ExtraBold.ttf"
    }

    object Colors {
        @ColorInt const val ink = 0xFF000000.toInt()
        @ColorInt const val white = 0xFFFFFFFF.toInt()
        @ColorInt const val hint = 0xFF505050.toInt()
        @ColorInt const val counter = 0xFF5E5E5E.toInt()
        @ColorInt const val placeholder = 0xFFC2C2C6.toInt()
        @ColorInt const val glyph = 0xFF444444.toInt()
        @ColorInt const val cardBorder = 0xFFB8B8B8.toInt()
        @ColorInt const val buttonFill = 0x61313132
        val pageStops = listOf(
            0f to 0xFFF7F7F7.toInt(),
            100f to 0xFFF5F5F5.toInt(),
            200f to 0xFFF1F1F1.toInt(),
            300f to 0xFFECECEC.toInt(),
            500f to 0xFFECECEB.toInt(),
            800f to 0xFFEBEBEB.toInt(),
            1100f to 0xFFEDEDED.toInt(),
            1400f to 0xFFEDEDED.toInt(),
            1600f to 0xFFEBEBEB.toInt(),
            2000f to 0xFFEBEBEB.toInt(),
            2300f to 0xFFE9EAE9.toInt(),
            2622f to 0xFFE8E9E8.toInt(),
        )
        val glowBand = Glow(603f, 2270f, 960f, 340f, 0xFFD9E5EE.toInt(), 0.8f)
        val glowCorner = Glow(0f, 2650f, 600f, 440f, 0xFFD3E0E9.toInt(), 0.95f)
    }

    object Space {
        const val cardEdge = 98f
        const val buttonEdge = 90f
        const val cardPad = 89f
        const val counterLeft = 102f
        const val pasteGap = 33f
    }

    object Radius {
        const val card = 101f
        const val button = 94.5f
    }

    object Box {
        val card = RectToken(98f, 1271f, 1010f, 868f)
        val button = RectToken(90f, 2337f, 1026f, 189f)
        val close = CloseToken(1080f, 206f, 1116f, 243f, 6f)
        val apps = RectToken(401f, 371f, 403f, 239f)
        val clip = RectToken(508f, 2010.5f, 33.5f, 41.5f)
    }

    object Type {
        val h1 = TextBlock(
            Font.extra,
            25.1f,
            Colors.ink,
            listOf(
                WordLine(811.1f, listOf(Word("import", 175.1f, 0.77f), Word("places", 498f, 0.5f), Word("from", 802.1f, 0.86f))),
                WordLine(915.4f, listOf(Word("notes", 293f, 0.42f), Word("or", 561.1f, 0.42f), Word("texts", 676.2f, 0f))),
            ),
        )
        val hint = TextBlock(
            Font.text,
            12.6f,
            Colors.hint,
            listOf(
                WordLine(1039.6f, listOf(Word("works", 194.8f, -0.04f), Word("best", 331.1f, -0.79f), Word("when", 426.8f, 0.21f), Word("the", 546.5f, -0.95f), Word("places", 620.1f, -0.05f), Word("in", 760.5f, -0.12f), Word("the", 807.5f, -1.12f), Word("list", 880.5f, -0.45f), Word("are", 950.6f, -0.45f), Word("all", 1029.6f, 0.55f))),
                WordLine(1089.1f, listOf(Word("from", 383.4f, -1.06f), Word("the", 489.5f, -0.84f), Word("same", 562.5f, 0.05f), Word("region.", 681.1f, 0.22f))),
            ),
        )
        val tip = TextBlock(
            Font.text,
            12.6f,
            Colors.hint,
            listOf(WordLine(1189.3f, listOf(Word("tip", 338.5f, -0.73f), Word("include", 446.5f, 0.27f), Word("the", 602.5f, -1.07f), Word("city", 676f, -0.34f), Word("name", 758.1f, 0.1f)))),
        )
        val tipDash = RectToken(398.5f, 1176.7f, 38f, 3.9f)
        val placeholder = TextBlock(
            Font.text,
            14.5f,
            Colors.placeholder,
            listOf(WordLine(1412f, listOf(Word("paste", 184.8f, -0.63f), Word("your", 322.3f, -0.08f), Word("note", 440.8f, -0.74f), Word("or", 553.6f, -1.3f), Word("text", 616.4f, -1.52f), Word("here...", 715.8f, 0.14f)))),
        )
        val counter = TextBlock(
            Font.text,
            15.3f,
            Colors.counter,
            listOf(WordLine(2220.5f, listOf(Word("0/10,000", 100.4f, 1.38f), Word("characters", 329.8f, -1.07f)))),
        )
        val paste = SingleText("paste", 16.6f, -0.75f, Colors.ink, 2047.5f, 571f)
        val start = SingleText("start import", 16.6f, -0.08f, Colors.white, 2448.9f, null)
        val pin = PinToken("📍", 14.6f, 128f, 998.8f)
    }

    object MotionColors {
        @ColorInt const val noteInk = 0xFF262626.toInt()
        @ColorInt const val marker = 0x80FFD52E.toInt()
        @ColorInt const val detect = 0xFF3B78E7.toInt()
        @ColorInt const val detectTrail = 0x1A3B78E7
        @ColorInt const val buttonSolid = 0xFF1C1C1E.toInt()
        @ColorInt const val chipFill = 0xFFFFFFFF.toInt()
        @ColorInt const val chipBorder = Colors.cardBorder
        @ColorInt const val chipShadow = 0xFF000000.toInt()
    }

    val NOTE = listOf(
        NoteLine("weekend in ", "velmora", "", true),
        NoteLine("brunch at ", "oriel café", ""),
        NoteLine("sunset at ", "alto point", ""),
        NoteLine("", "tide market", ", sat only"),
        NoteLine("dinner at ", "fennel & fig", ""),
        NoteLine("drinks at ", "lantern bar", ""),
    )
    val NOTE_TEXT = NOTE.joinToString("\n") { it.pre + it.place + it.post }

    object Note {
        const val size = 14.5f
        const val left = 184.8f
        const val firstBase = 1412f
        const val pitch = 66f
        const val scanTop = 1355f
        const val scanBottom = 1790f
        const val scanTrail = 90f
        const val scanStroke = 4f
        val marker = MarkerToken(0.42f, 0.9f, 6f, 6f)
        val underline = UnderlineToken(7f, 4f)
    }

    /** Chips: lift off their words, then sit on the map as pins (label pills). ref px unless marked pt. */
    object Chip {
        const val height = 72f
        const val radius = 36f
        const val padLeft = 24f
        const val padRight = 30f
        const val pinGap = 9f
        const val size = 12.6f
        const val pinSize = 11f // pt
        /** Scale of a chip once it sits on the map. */
        const val mapScale = 0.8f
    }

    /** The editable text area over the card, ref px; `max` is the counter's limit (0/10,000). */
    object Input {
        const val left = 184.8f
        const val right = 98f + 1010f - 89f
        const val bottom = 1930f
        const val max = 10_000
        const val label = "Note to import"
    }

    object MapColors {
        @ColorInt const val land = 0xFFF3F0E9.toInt()
        @ColorInt const val park = 0xFFDEE9D4.toInt()
        @ColorInt const val water = 0xFFD3E2EE.toInt()
        @ColorInt const val shore = 0xFFC3D5E4.toInt()
        @ColorInt const val street = 0xFFFFFFFF.toInt()
        @ColorInt const val casing = 0xFFE3DED4.toInt()
        @ColorInt const val minor = 0xFFFBFAF6.toInt()
        @ColorInt const val creaseDark = 0x12000000
        @ColorInt const val creaseLight = 0xD9FFFFFF.toInt()
        @ColorInt const val route = 0xFF3B78E7.toInt()
        @ColorInt const val routeCasing = 0xFFFFFFFF.toInt()
        @ColorInt const val dot = 0xFF3B78E7.toInt()
        @ColorInt const val dotRing = 0xFFFFFFFF.toInt()
        @ColorInt const val shadow = 0xFF000000.toInt()
    }

    object MapShape {
        const val sea = "M 430 0 C 470 80 560 120 640 130 C 700 138 760 118 792 150 C 818 176 782 214 802 246 C 832 292 920 322 1010 382 L 1010 0 Z"
        const val river = "M 990 370 C 860 420 760 400 660 470 C 560 540 430 500 300 560 C 200 605 90 580 -10 600"
        val parks = listOf(
            Park(330f, 340f, 120f, 62f),
            Park(860f, 720f, 96f, 58f),
        )
        val major = listOf(
            "M -10 250 C 220 230 420 310 620 300 C 780 292 900 470 1020 520",
            "M 230 -10 C 250 300 330 560 420 880",
            "M -10 770 C 300 700 620 800 1020 690",
            "M 600 120 C 610 400 680 640 760 880",
        )
        const val minorAngle = -12f
        const val minorGap = 70f
        const val minorW = 6f
        const val majorW = 13f
        const val casingW = 18f
        const val riverW = 28f
        const val shoreW = 34f
        const val seaShoreW = 4f
        const val creaseW = 2f
    }

    /** The five places on the map (dot centres, card-local ref px), in the note's order. */
    val SPOTS = listOf(
        Spot(165f, 290f), // oriel cafe
        Spot(770f, 192f), // alto point
        Spot(640f, 452f), // tide market
        Spot(150f, 640f), // fennel & fig
        Spot(560f, 760f), // lantern bar
    )

    /** Pins on the map, pt at 402 pt wide. */
    object Pin {
        const val dotR = 4.4f
        const val coreR = 2.7f
        const val gap = 3f
        const val shadowRx = 9f
        const val shadowRy = 2.6f
        const val shadowBlur = 2.2f
        const val route = 2.4f
        const val routeCasing = 4.6f
    }

    val tipUnderline = RectToken(446.5f, 1199f, 420f, 4f)
    val found = FoundToken("found ", " place", "s")
    val digitAdvance = DigitAdvance(1186f / 2000f, 1309f / 2000f)
    val icons = IconsToken(
        notes = RectToken(383f, 358f, 213f, 212f),
        chat = RectToken(499f, 418f, 214f, 213f),
        doc = RectToken(665f, 373f, 142f, 190f),
    )

    data class Park(val cx: Float, val cy: Float, val rx: Float, val ry: Float)
    data class Spot(val x: Float, val y: Float)
    data class Glow(val cx: Float, val cy: Float, val rx: Float, val ry: Float, @ColorInt val color: Int, val opacity: Float)
    data class RectToken(val x: Float, val y: Float, val w: Float, val h: Float)
    data class CloseToken(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val stroke: Float)
    data class Word(val text: String, val x: Float, val letterSpacing: Float)
    data class WordLine(val baseline: Float, val words: List<Word>)
    data class TextBlock(val font: String, val size: Float, @ColorInt val color: Int, val lines: List<WordLine>)
    data class SingleText(val text: String, val size: Float, val letterSpacing: Float, @ColorInt val color: Int, val baseline: Float, val left: Float?)
    data class PinToken(val text: String, val size: Float, val left: Float, val top: Float)
    data class NoteLine(val pre: String, val place: String, val post: String, val city: Boolean = false)
    data class MarkerToken(val top: Float, val bottom: Float, val over: Float, val radius: Float)
    data class UnderlineToken(val gap: Float, val stroke: Float)
    data class FoundToken(val pre: String, val post: String, val plural: String)
    data class DigitAdvance(val text: Float, val extra: Float)
    data class IconsToken(val notes: RectToken, val chat: RectToken, val doc: RectToken)
}
