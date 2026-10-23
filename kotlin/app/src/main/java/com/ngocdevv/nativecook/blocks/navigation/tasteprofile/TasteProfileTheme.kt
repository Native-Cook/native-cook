package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import androidx.compose.ui.graphics.Color

internal const val REF_W = 1206f
internal const val REF_PT = 402f
internal const val REF_INSET = 186f
internal const val ASCENT = 0.795f

internal object TasteProfileColors {
    val ink = Color.Black
    val white = Color.White
    val hairline = Color(0xFFD2D2D2)
    val tileFill = Color(0xFFF8F8F8)
    val tileHairline = Color(0xFFDEDEDE)
    val chipFill = Color(0xFFFEFEFE)
    val chipText = Color(0xFF8A8A89)
    val pinIcon = Color(0xFF525252)
    val dotOn = Color(0xFF1A1A18)
    val dotOff = Color(0xFF979895)
    val pageStops = listOf(
        0f to Color(0xFFF6F6F6),
        90f to Color(0xFFF6F6F6),
        180f to Color(0xFFF3F3F3),
        270f to Color(0xFFF1F1F1),
        360f to Color(0xFFEFEFEF),
        450f to Color(0xFFECECEC),
        630f to Color(0xFFECECEC),
        900f to Color(0xFFEBEBEB),
        1500f to Color(0xFFEBEBEB),
        1800f to Color(0xFFE9EBE9),
        2250f to Color(0xFFE9EAE8),
        2520f to Color(0xFFE8E8E8),
        2622f to Color(0xFFE8E8E8),
    )
}

internal object TasteProfileSpace {
    const val edge = 36.5f
    const val tileEdge = 38.5f
    const val tileGap = 19.5f
    const val navGap = 25f
    const val photoGap = 21f
    const val chipGap = 24f
}

internal object TasteProfileRadius {
    const val nav = 28f
    const val tile = 39f
    const val photo = 54f
    const val pill = 999f
}

internal enum class TasteProfileFont {
    BODY,
    SEMI,
    EXTRA,
}

internal data class TasteProfileTextSpec(
    val value: String,
    val font: TasteProfileFont,
    val x: Float,
    val baseline: Float,
    val size: Float,
    val letterSpacing: Float,
    val color: Color,
)

internal object TasteProfileText {
    val name = TasteProfileTextSpec("Sean Zhang", TasteProfileFont.EXTRA, 428.2f, 431f, 21.66f, 0.52f, Color.Black)
    val handle = TasteProfileTextSpec("@seanzhang", TasteProfileFont.BODY, 429.4f, 503f, 15.24f, 0.65f, Color(0xFF505051))
    val following = TasteProfileTextSpec("following", TasteProfileFont.BODY, 497.4f, 590f, 14.35f, 0.39f, Color(0xFF262527))
    val followers = TasteProfileTextSpec("followers", TasteProfileFont.BODY, 825.4f, 590f, 14.35f, 0.07f, Color(0xFF262527))
    val mutual2 = TasteProfileTextSpec("2", TasteProfileFont.BODY, 550.7f, 678f, 12.3f, 0f, Color(0xFF221F22))
    val mutual = TasteProfileTextSpec("mutual", TasteProfileFont.BODY, 583.2f, 678f, 12.28f, 0.24f, Color(0xFF221F22))
    val friends = TasteProfileTextSpec("friends", TasteProfileFont.BODY, 725.4f, 678f, 12.24f, 0f, Color(0xFF221F22))
    val rankA = TasteProfileTextSpec("new york", TasteProfileFont.BODY, 565.5f, 785f, 9.74f, -0.04f, Color(0xFF474C4F))
    val rankB = TasteProfileTextSpec("rank", TasteProfileFont.BODY, 565.5f, 822f, 9.74f, -0.24f, Color(0xFF474C4F))
    val weekA = TasteProfileTextSpec("week", TasteProfileFont.BODY, 978.1f, 785f, 9.74f, -0.01f, Color(0xFF474C4F))
    val weekB = TasteProfileTextSpec("streak", TasteProfileFont.BODY, 977.8f, 822f, 9.74f, -0.32f, Color(0xFF474C4F))
    val location = TasteProfileTextSpec("New York", TasteProfileFont.BODY, 109.2f, 953f, 13.97f, 0.18f, Color(0xFF4E4F4E))
    val bio = TasteProfileTextSpec("Dabbling in the arts", TasteProfileFont.BODY, 39.2f, 1033f, 14.39f, 0.16f, Color(0xFF262725))
    val chipA = TasteProfileTextSpec("new york", TasteProfileFont.EXTRA, 328.1f, 1206f, 15.71f, 0.11f, TasteProfileColors.chipText)
    val chipB = TasteProfileTextSpec("london", TasteProfileFont.EXTRA, 702.5f, 1206f, 15.66f, 0.2f, TasteProfileColors.chipText)
    val chipC = TasteProfileTextSpec("paris", TasteProfileFont.EXTRA, 1006.1f, 1206f, 15.81f, -0.86f, TasteProfileColors.chipText)
    val favorites = TasteProfileTextSpec("favorites", TasteProfileFont.BODY, 93.5f, 1428f, 11.39f, -0.22f, Color(0xFF6A6A6A))
    val recommend = TasteProfileTextSpec("recommend", TasteProfileFont.BODY, 356.3f, 1428f, 11.43f, 0.09f, Color(0xFF6A6A6A))
    val okay = TasteProfileTextSpec("okay", TasteProfileFont.BODY, 706.9f, 1428f, 11.43f, 0.03f, Color(0xFF6A6A6A))
    val disliked = TasteProfileTextSpec("disliked", TasteProfileFont.BODY, 968.9f, 1428f, 11.43f, 0.19f, Color(0xFF6A6A6A))
    val viewAll = TasteProfileTextSpec("view all", TasteProfileFont.BODY, 756.2f, 1983f, 12.28f, 0.33f, Color.Black)
    val photos = TasteProfileTextSpec("photos", TasteProfileFont.BODY, 1013.2f, 1983f, 12.28f, 0.03f, Color.Black)
    val follow = TasteProfileTextSpec("follow", TasteProfileFont.SEMI, 760.4f, 263f, 12.24f, -0.35f, Color.Black)

    val displayName = name.copy(value = "Theo Hogan", x = 427.7f, letterSpacing = 0.32f)
    val displayHandle = handle.copy(value = "@theohogan", letterSpacing = 0.62f)
}

internal data class TasteProfileBox(val x: Float, val y: Float, val w: Float, val h: Float)
internal data class TasteProfileAvatar(val diameter: Float, val centerX: Float, val centerY: Float)
internal data class TasteProfileDot(val centerX: Float, val diameter: Float, val color: Color)

internal object TasteProfileBoxes {
    val chevron = TasteProfileBox(45f, 220f, 33f, 58f)
    const val chevronStroke = 8f
    val follow = TasteProfileBox(648.5f, 200f, 262f, 97.7f)
    val followIcon = TasteProfileBox(685f, 229f, 49f, 41f)
    val send = TasteProfileBox(935.5f, 200f, 98f, 97.7f)
    val sendIcon = TasteProfileBox(965f, 228f, 42f, 42f)
    val share = TasteProfileBox(1058.7f, 200f, 97.5f, 97.7f)
    val shareIcon = TasteProfileBox(1086f, 228f, 43f, 43f)
    val capsule = TasteProfileBox(34.5f, 365f, 373f, 500f)
    const val capsuleBorder = 6f
    val avatars = listOf(
        TasteProfileAvatar(59f, 460f, 665f),
        TasteProfileAvatar(59f, 509.5f, 665f),
    )
    val rank = TasteProfileBox(425f, 736f, 120f, 122f)
    val streak = TasteProfileBox(800f, 731f, 160f, 125f)
    val pin = TasteProfileBox(47f, 914f, 42f, 51f)
    val chips = listOf(
        TasteProfileBox(36.5f, 1136f, 226f, 105f),
        TasteProfileBox(286f, 1136f, 344f, 105f),
        TasteProfileBox(660f, 1136f, 271f, 105f),
        TasteProfileBox(962f, 1136f, 228f, 105f),
    )
    const val chipY = 1136f
    const val chipH = 105f
    val globe = TasteProfileBox(88f, 1165f, 50f, 50f)
    val tiles = listOf(38.5f, 325f, 612f, 899.5f)
    const val tileW = 267.5f
    const val tileY = 1296f
    const val tileH = 171f
    val tileIcons = listOf(
        TasteProfileBox(101f, 1334f, 40f, 40f),
        TasteProfileBox(381f, 1334f, 40f, 40f),
        TasteProfileBox(676f, 1334f, 40f, 40f),
        TasteProfileBox(984f, 1334f, 39f, 40f),
    )
    val strip = TasteProfileBox(36.5f, 1516f, 1131f, 419.5f)
    val dots = listOf(
        TasteProfileDot(70f, 19f, TasteProfileColors.dotOn),
        TasteProfileDot(100.5f, 19f, TasteProfileColors.dotOff),
        TasteProfileDot(131.5f, 19f, Color(0xFF959593)),
        TasteProfileDot(162.5f, 11f, Color(0xFF9A9997)),
    )
    const val dotsY = 1969.5f
    val photoL = TasteProfileBox(35f, 2063.5f, 557.5f, 557.5f)
    val photoR = TasteProfileBox(614f, 2063.5f, 556f, 557.5f)
    val peek = TasteProfileBox(1193.5f, 2063.5f, 80f, 557.5f)
}

internal data class TasteProfileGlyphRun(
    val font: TasteProfileFont,
    val size: Float,
    val baseline: Float,
    val digitHeight: Float,
    val color: Color,
    val glyphs: List<Triple<Char, Float, Float>>,
)

internal object TasteProfileGlyphRuns {
    val n39 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 15.64f, 590f, 32f, Color(0xFF2E2D30), listOf(Triple('3', 431f, 25f), Triple('9', 459f, 25f)))
    val n60 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 15.64f, 590f, 32f, Color(0xFF2E2D30), listOf(Triple('6', 760f, 24f), Triple('0', 787f, 25f)))
    val t125 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 17.58f, 1370f, 36f, Color.Black, listOf(Triple('1', 156f, 17f), Triple('2', 175f, 31f), Triple('5', 209f, 33f)))
    val t764 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 17.65f, 1370f, 36f, Color.Black, listOf(Triple('7', 435f, 31f), Triple('6', 467f, 34f), Triple('4', 503f, 35f)))
    val t183 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 17.58f, 1370f, 36f, Color.Black, listOf(Triple('1', 731f, 17f), Triple('8', 750f, 33f), Triple('3', 786f, 33f)))
    val t17 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 17.71f, 1370f, 36f, Color.Black, listOf(Triple('1', 1038f, 17f), Triple('7', 1056f, 30f)))
    val c1470 = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 13.7f, 1983f, 28f, Color.Black, listOf(Triple('1', 909f, 20f), Triple('4', 932f, 22f), Triple('7', 958f, 20f), Triple('0', 982f, 21f)))
    val chipAll = TasteProfileGlyphRun(TasteProfileFont.EXTRA, 16.14f, 1207f, 38f, Color.White, listOf(Triple('a', 155f, 28f), Triple('l', 187f, 9f), Triple('l', 200f, 8f)))
    val stats = listOf(t125, t764, t183, t17)
}

internal val TasteProfileGlyphEm = mapOf(
    '0' to (0.028f to 0.572f),
    '2' to (0.035f to 0.582f),
    '3' to (0.032f to 0.59f),
    '4' to (0.022f to 0.598f),
    '5' to (0.028f to 0.58f),
    '6' to (0.028f to 0.585f),
    '7' to (0.012f to 0.56f),
    '8' to (0.032f to 0.575f),
    '9' to (0.035f to 0.588f),
    'a' to (0.028f to 0.625f),
    'l' to (0.052f to 0.2f),
)

internal data class TasteProfileSprite(val x: Float, val y: Float, val w: Float, val h: Float, val frames: Int)

internal object TasteProfileSprites {
    val coin = TasteProfileSprite(401f, 712f, 168f, 170f, 28)
    val gem = TasteProfileSprite(776f, 707f, 208f, 173f, 12)
    const val coinShadowCenterX = 485f
    const val coinShadowCenterY = 866f
    const val coinShadowW = 96f
    const val coinShadowH = 16f
}

internal const val FOLLOW_ING_X = 871.1f

internal val digitWidths = mapOf('0' to 33f, '1' to 17f, '2' to 31f, '3' to 33f, '4' to 35f, '5' to 33f, '6' to 34f, '7' to 31f, '8' to 33f, '9' to 33f)
internal const val DIGIT_GAP = 3f

internal fun layoutTasteProfileNumber(value: String, x0: Float, digitHeight: Float): List<Triple<Char, Float, Float>> {
    val scale = digitHeight / 36f
    var x = x0
    return value.map { character ->
        val width = kotlin.math.round(requireNotNull(digitWidths[character]) * scale)
        Triple(character, x, width).also { x += width + DIGIT_GAP }
    }
}

internal object TasteProfileChart {
    const val w = 267.5 * 2 + 19.5
    const val h = 560.0
    const val pad = 38.5
    const val barW = 54.0
    const val barMax = 196.0
    const val barsBottom = 560.0 - 84
    const val monthBaseline = 560.0 - 40
    const val captionBaseline = 228.0
    const val valueGap = 14.0
    val barColor = Color(0xFFDCDCDC)
    val barOn = Color(0xFF000000)
    val caption = Color(0xFF6A6A6A)
    val veil = Color(0x8CEBEBEB)
}

internal val TasteProfileTileLabels = listOf(
    TasteProfileText.favorites,
    TasteProfileText.recommend,
    TasteProfileText.okay,
    TasteProfileText.disliked,
)
