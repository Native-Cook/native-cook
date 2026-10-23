package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private fun rgba(red: Int, green: Int, blue: Int, alpha: Float): Color =
    Color(red = red / 255f, green = green / 255f, blue = blue / 255f, alpha = alpha)

/** Every colour, copied from the React Native theme.ts source. */
internal object PtsColor {
    val page = Color(0xFFFFFFFF)
    val pageClear = rgba(255, 255, 255, 0f)
    val ink = Color(0xFF111216)
    val inkSoft = Color(0xFF5B5E68)
    val rule = Color(0xFFE7E7EC)
    val panel = Color(0xFF8E90A8)
    val panelLow = Color(0xFF3D4049)
    val panelInk = Color(0xFFFFFFFF)
    val panelInkSoft = rgba(255, 255, 255, 0.78f)
    val pill = Color(0xFFFFFFFF)
    val pillInk = Color(0xFF111216)
    val toolbar = Color(0xFFF1F1F4)
    val toolbarInk = Color(0xFF2A2C33)
    val stackIcon = listOf(
        Color(0xFFFF9A3D),
        Color(0xFFFF4F8B),
        Color(0xFF3D63F2),
    )
    val accent = Color(0xFF3D63F2)

    // The gradient field (top-left cobalt, violet middle, magenta-pink right, cobalt bottom).
    val fieldCobalt = Color(0xFF2F45F0)
    val fieldIndigo = Color(0xFF4A35D8)
    val fieldViolet = Color(0xFF8A43D6)
    val fieldMagenta = Color(0xFFE0569A)
    val fieldDeep = Color(0xFF1E2BB8)

    // The rim's sweep: cold to warm, then back to cold so the seam is invisible.
    val rim = listOf(
        Color(0xFF7FD4FF),
        Color(0xFF7C8CFF),
        Color(0xFFC77DFF),
        Color(0xFFFF7EB6),
        Color(0xFFFFC58A),
        Color(0xFF7FD4FF),
    )
    val summaryInk = Color(0xFFFFFFFF)
    val summaryInkSoft = rgba(255, 255, 255, 0.82f)
    val summaryArrive = Color(0xFFD3C6FF)
    val iconDisc = Color(0xFF3D63F2)
    val iconGlyph = Color(0xFFFFFFFF)
    val closeFill = rgba(255, 255, 255, 0.16f)
    val sweep = listOf(
        rgba(255, 255, 255, 0f),
        rgba(255, 255, 255, 0.55f),
        rgba(255, 255, 255, 0f),
    )
    val ghost = rgba(255, 255, 255, 0.55f)
    val pageShadow = Color(0xFF120A40)
    val cardGlow = Color(0xFFB9A6FF)
    val mote = Color(0xFFFFFFFF)
    val bloom = Color(0xFFE9E2FF)

    // The reader app's own icon in the footer credit.
    val creditIcon = listOf(
        Color(0xFFFF7A9C),
        Color(0xFFFF4F8B),
        Color(0xFF8E5BFF),
    )
    val closeInk = Color(0xFFFFFFFF)
    val finger = rgba(255, 255, 255, 0.34f)
    val fingerRing = rgba(255, 255, 255, 0.75f)
}

private val noFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun textStyle(
    fontSize: Float,
    color: Color,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize.sp,
    lineHeight = lineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    color = color,
    textAlign = textAlign,
    platformStyle = noFontPadding,
)

/** Text styles copied from the React Native theme.ts source. */
internal object PtsType {
    private val serif = FontFamily.Serif
    private val sans = FontFamily.SansSerif

    val wordmark = textStyle(
        fontSize = 25f,
        fontWeight = FontWeight(900),
        fontFamily = sans,
        letterSpacing = 0.4f,
        color = PtsColor.ink,
    )
    val pill = textStyle(
        fontSize = 8f,
        fontWeight = FontWeight(800),
        fontFamily = sans,
        letterSpacing = 0.8f,
        color = PtsColor.pillInk,
    )
    val headline = textStyle(
        fontSize = 32f,
        lineHeight = 36f,
        fontWeight = FontWeight(700),
        fontFamily = serif,
        color = PtsColor.panelInk,
        textAlign = TextAlign.Center,
    )
    val dek = textStyle(
        fontSize = 15f,
        lineHeight = 19f,
        letterSpacing = -0.3f,
        fontWeight = FontWeight(700),
        fontFamily = serif,
        color = PtsColor.panelInkSoft,
        textAlign = TextAlign.Center,
    )
    val byline = textStyle(
        fontSize = 8f,
        fontWeight = FontWeight(700),
        fontFamily = sans,
        letterSpacing = 0.8f,
        color = PtsColor.panelInkSoft,
        textAlign = TextAlign.Center,
    )
    val section = textStyle(
        fontSize = 13f,
        fontWeight = FontWeight(800),
        fontFamily = sans,
        letterSpacing = 1.1f,
        color = PtsColor.ink,
    )
    val dropCap = textStyle(
        fontSize = 56f,
        lineHeight = 56f,
        fontWeight = FontWeight(700),
        fontFamily = serif,
        color = PtsColor.ink,
    )
    val body = textStyle(
        fontSize = 17f,
        lineHeight = 26f,
        fontFamily = serif,
        color = PtsColor.ink,
    )
    val toolbarLabel = textStyle(
        fontSize = 13f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        color = PtsColor.toolbarInk,
    )
    val cardTitle = textStyle(
        fontSize = 11f,
        fontWeight = FontWeight(700),
        fontFamily = sans,
        color = PtsColor.summaryInk,
    )
    val lead = textStyle(
        fontSize = 19f,
        lineHeight = 26f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        letterSpacing = -0.3f,
        color = PtsColor.summaryInk,
    )
    val itemTitle = textStyle(
        fontSize = 18f,
        lineHeight = 30f,
        fontWeight = FontWeight(700),
        fontFamily = sans,
        letterSpacing = -0.2f,
        color = PtsColor.summaryInk,
    )
    val itemBody = textStyle(
        fontSize = 17f,
        lineHeight = 22f,
        fontWeight = FontWeight(400),
        fontFamily = sans,
        color = PtsColor.summaryInkSoft,
    )
    val credit = textStyle(
        fontSize = 10f,
        lineHeight = 13f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        color = PtsColor.summaryInkSoft,
    )
    val thinking = textStyle(
        fontSize = 14f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        color = PtsColor.summaryInk,
    )
}

/** Every layout value, measured in points on the 402 x 874 reference screen. */
internal object PtsLayout {
    data class Size(val width: Float, val height: Float)

    const val gutter = 16f
    const val mastheadHeight = 42f
    const val panelInset = 8f
    const val panelPadTop = 27f
    const val panelPadX = 20f
    const val pillHeight = 16f
    const val pillPadX = 8f
    const val photoAspect = 262f / 386f
    const val pillGap = 21f
    const val dekGap = 12f
    const val photoGap = 17f
    const val dotGap = 46f
    const val bylineGap = 12f
    const val panelPadBottom = 42f
    const val panelRadius = 12f
    const val bodyGap = 96f
    const val toolbarCenterFromBottom = 45f
    val toolbarPill = Size(width = 56f, height = 30f)
    const val toolbarRound = 30f
    const val photoInset = 12f
    const val toolbarHeight = 52f
    const val toolbarFade = 28f
    const val toolbarPillHeight = 36f
    const val toolbarIcon = 44f
    const val thinkWidthRatio = 0.55f
    const val thinkHeight = 222f
    const val cardWidthRatio = 0.64f
    const val cardHeight = 270f
    const val cardRadius = 18f
    const val cardTopTrim = 6f
    const val thinkTop = 134f
    const val summaryTop = 125f
    const val titleRowGap = 22f
    const val thinkingGap = 39f
    const val ghostInset = 12f
    const val shadowBlur = 18f
    const val glowBlur = 30f
    const val glowSpread = 22f
    const val glowRest = 0.35f
    const val glowThink = 0.35f
    const val shadowAlpha = 0.35f
    const val shadowDrop = 14f
    const val ghostPeek = 12f
    const val footerBottom = 14f
    val creditPill = Size(width = 176f, height = 40f)
    const val creditPad = 8f
    const val sweepWidth = 90f
    const val summaryGap = 26f
    const val textInset = 16f
    const val itemIndent = 34f
    const val iconDisc = 24f
    const val itemGap = 27f
    const val leadGap = 42f
    const val footerHeight = 44f
    const val closeSize = 32f
    const val deviceRadius = 48f
    const val rimWidth = 2.5f
    const val rimGlow = 10f
    const val scanHeight = 64f
    const val fingerRadius = 22f
}

/** Copy strings and summary rows copied from the React Native theme.ts source. */
internal object PtsCopy {
    data class Item(val icon: String, val title: String, val body: String)

    const val wordmark = "MERIDIAN"
    const val kicker = "REVIEW"
    const val headline = "My Week Living Inside the Halo One Visor"
    const val dek = "Mara Okafor gets hands-on — and then some — in this in-depth Halo One review."
    const val byline = "BY MARA OKAFOR"
    const val date = "FEB 2, 2026"
    const val section = "GEAR REVIEWS"
    val body = listOf(
        "Years before the Halo One went on sale, its makers promised a computer you could wear all afternoon. After a week of working, watching and travelling with it, I can report that they are closer than anyone has been, and still not quite there.",
        "The first surprise is the picture. Text is as sharp as on a good laptop, and a film on the virtual screen in a dark room is better than my television. The second is how little I thought about controllers: I never had any.",
        "Then there is the weight. The split strap helps, but by the third hour the visor makes itself known on the bridge of the nose, and the battery pack in a pocket is a cable you never forget.",
    )
    const val summarize = "Summarize"
    const val pageTitle = "Halo One review: my week inside the visor"
    const val thinking = "Summarizing for you…"
    val credit = listOf(
        "Summarized for you",
        "by Meridian Reader · 14 min → 40 s",
    )
    const val lead = "The Halo One is a game-changing headset computer with a handful of killer apps, including a private movie theater experience and the ability to work anywhere. It offers a razor-sharp virtual display and immersive spatial sound, but needs a personalized fit to be comfortable."
    val items = listOf(
        Item("pricetag", "Price", "The Halo One starts at $1,899; the travel case is extra."),
        Item("star", "Review sentiment", "Mara calls it the best screen she has worn, though not for everyone yet."),
        Item("body", "Comfort and fit", "The split strap spreads the weight; two hours before she needed a break."),
    )
}
