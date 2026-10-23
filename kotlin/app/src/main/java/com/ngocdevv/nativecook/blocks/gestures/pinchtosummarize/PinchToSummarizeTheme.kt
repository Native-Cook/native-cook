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
    val summaryInkSoft = rgba(255, 255, 255, 0.8f)
    val creditInk = rgba(255, 255, 255, .94f)
    val creditInkSoft = rgba(255, 255, 255, .7f)
    val iconDisc = Color(0xFF3D63F2)
    val iconGlyph = Color(0xFFFFFFFF)
    val closeFill = rgba(255, 255, 255, 0.16f)
    val sweep = listOf(
        rgba(255, 255, 255, 0f),
        rgba(255, 255, 255, 0.55f),
        rgba(255, 255, 255, 0f),
    )
    val stack = Color(0xFFD8D9F4)
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
    val marker = Color(0xFFDCD4FF)
    val chipPaper = rgba(255, 255, 255, .94f)
    val chipPill = rgba(74, 53, 216, .7f)
    val veil = Color.White
    val entityFill = rgba(255, 255, 255, .14f)
    val entityBorder = rgba(255, 255, 255, .32f)
    val termFlash = rgba(255, 226, 150, .42f)
    val meterTrack = rgba(255, 255, 255, .24f)
    val meterFill = Color.White
    val sup = rgba(255, 255, 255, .62f)
    val depthGlass = rgba(22, 16, 64, .62f)
    val depthBorder = rgba(255, 255, 255, .18f)
    val depthLens = rgba(255, 255, 255, .94f)
    val depthInk = rgba(255, 255, 255, .72f)
    val depthInkActive = Color(0xFF17132A)
    val progressTrack = rgba(17, 18, 22, .10f)
    val progressFill = Color(0xFF3D63F2)
    val shareGlass = rgba(255, 255, 255, .12f)
    val shareBorder = rgba(255, 255, 255, .3f)
    val actionFill = rgba(255, 255, 255, .2f)
    val pointPress = rgba(255, 255, 255, .1f)
    val thinkingBase = rgba(255, 255, 255, .68f)
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
        fontWeight = FontWeight(600),
        fontFamily = sans,
        color = PtsColor.summaryInk,
    )
    val lead = textStyle(
        fontSize = 19f,
        lineHeight = 26f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        letterSpacing = -0.4f,
        color = PtsColor.summaryInk,
    )
    val itemTitle = textStyle(
        fontSize = 17f,
        lineHeight = 22f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        letterSpacing = -0.2f,
        color = PtsColor.summaryInk,
    )
    val itemBody = textStyle(
        fontSize = 15f,
        lineHeight = 20f,
        fontWeight = FontWeight(400),
        fontFamily = sans,
        color = PtsColor.summaryInkSoft,
    )
    val tldr = textStyle(24f, PtsColor.summaryInk, FontWeight(700), sans, 30f, -.6f)
    val entity = textStyle(13f, PtsColor.summaryInk, FontWeight(600), sans, 16f, -.1f)
    val creditTitle = textStyle(
        fontSize = 12f,
        lineHeight = 15f,
        fontWeight = FontWeight(600),
        fontFamily = sans,
        color = PtsColor.creditInk,
    )
    val creditSub = textStyle(12f, PtsColor.creditInkSoft, FontWeight(400), sans, 15f)
    val depth = textStyle(12f, PtsColor.depthInk, FontWeight(600), sans, 15f)
    val thinking = textStyle(
        fontSize = 15f,
        lineHeight = 20f,
        fontWeight = FontWeight(500),
        fontFamily = sans,
        color = PtsColor.thinkingBase,
    )
    val action = textStyle(15f, PtsColor.summaryInk, FontWeight(600), sans, 20f, -.2f)
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
    const val titleRowGap = 34f
    const val titleRowHeight = 16f
    const val shadowBlur = 18f
    const val glowBlur = 30f
    const val glowSpread = 22f
    const val glowRest = 0.35f
    const val glowThink = 0.35f
    const val shadowAlpha = 0.35f
    const val shadowDrop = 14f
    const val stackScale = .92f
    const val stackPeek = 10f
    const val stackFrom = .85f
    const val stackFade = .08f
    const val footerLift = 10f
    const val footerMin = 16f
    val creditPill = Size(width = 0f, height = 44f)
    const val creditPadLeft = 12f
    const val creditPadRight = 16f
    const val sweepWidth = 90f
    const val summaryGap = 24f
    const val textInset = 16f
    const val itemIndent = 34f
    const val iconDisc = 24f
    const val iconGlyph = 13f
    const val itemGap = 20f
    const val leadGap = 28f
    const val rowsBreath = 16f
    const val footerHeight = 44f
    const val closeSize = 44f
    const val closeGlyph = 18f
    const val deviceRadius = 48f
    const val rimWidth = 2.5f
    const val rimGlow = 10f
    const val fingerRadius = 22f
    const val markerPadX = 2f
    const val markerTop = 7f
    const val markerBottom = 2f
    const val markerRadius = 4f
    const val entityGap = 12f
    const val entityHeight = 28f
    const val entityPadX = 12f
    const val entityGapX = 8f
    const val meterSegW = 12f
    const val meterSegH = 4f
    const val meterGap = 3f
    const val meterLeft = 10f
    const val depthHeight = 32f
    const val depthPad = 3f
    const val depthLift = 92f
    const val progressHeight = 3f
    const val progressInset = 14f
    const val progressBottom = 10f
    const val shareRadius = 22f
    const val sharePad = 12f
    const val actionHeight = 44f
    const val actionGap = 10f
}

/** Copy strings and summary rows copied from the React Native theme.ts source. */
internal object PtsCopy {
    data class Paragraph(val leadIn: String? = null, val key: String? = null, val rest: String)
    data class Item(val icon: String, val title: String, val body: String, val source: Int, val meter: Int? = null)
    data class Level(val name: String, val lead: String, val tldr: Boolean, val points: List<Int>, val chips: List<Int>)
    data class Entity(val label: String, val term: String)

    const val wordmark = "MERIDIAN"
    const val kicker = "REVIEW"
    const val headline = "My Week Living Inside the Halo One Visor"
    const val dek = "Mara Okafor gets hands-on — and then some — in this in-depth Halo One review."
    const val byline = "BY MARA OKAFOR"
    const val date = "FEB 2, 2026"
    const val section = "GEAR REVIEWS"
    val body = listOf(
        Paragraph("THE HALO ONE", " is a computer you wear: a private cinema and an office in one visor, and the closest anyone has come to that old promise.", " I spent a week working, watching and travelling with it, and mostly forgot it was there."),
        Paragraph(key = "It has the best screen I have worn: text is as sharp as on a good laptop, and a film in a dark room beats my television.", rest = " I never reached for a controller, because my hands were enough."),
        Paragraph(key = "The split strap spreads the weight, but by the second hour I wanted a break.", rest = " The battery pack in a pocket is a cable you never quite forget."),
        Paragraph(key = "It starts at \$1,899, and the travel case you will want is extra.", rest = " That is laptop money for something most people will not wear all day yet."),
        Paragraph(rest = "Apps are the open question. The ones made for the visor are lovely; the rest float in flat windows, and you notice the seams within a day."),
        Paragraph(rest = "Calls are the surprise. People see a soft rendering of my face, which is uncanny for a minute and then simply useful, like a good webcam."),
        Paragraph(rest = "Travel is where it earns its keep. On a five-hour flight the cabin disappeared, and the battery lasted one film and most of a second."),
        Paragraph(rest = "Setup takes ten minutes, most of it fitting the light seal. Get that wrong and the edges glow; get it right and the room falls away."),
        Paragraph(rest = "Battery life is the honest weak spot: a little over two hours of mixed use, and the pack grows warm in a pocket by the end of a film."),
        Paragraph(rest = "Sound is better than it has any right to be. The speakers sit just off the ears, and in a quiet room nobody beside you hears much at all."),
        Paragraph(rest = "Typing works with any keyboard, and the visor floats a larger screen above it; for writing I preferred that to anything else on the trip."),
        Paragraph(rest = "Friends asked the same thing every time: does it feel lonely? Less than I feared, though handing it over to a guest takes a fiddly minute."),
        Paragraph(rest = "Would I buy one? Not this year. But I have stopped thinking of headsets as gadgets, and that is the real change."),
    )
    const val summarize = "Summarize"
    const val pageTitle = "Halo One review: my week inside the visor"
    const val thinking = "Summarizing for you…"
    const val credit = "Summarized for you"
    const val creditRead = "You read 50% · 14 min → 40 s"
    val items = listOf(
        Item("star", "Review sentiment", "The best screen Mara has worn: laptop-sharp text and films that beat her TV.", 1, 4),
        Item("body", "Comfort and fit", "The split strap spreads the weight, but she wanted a break after 2 hours.", 2),
        Item("pricetag", "Price", "Starts at \$1,899; the travel case is extra.", 3),
    )
    val levels = listOf(
        Level("Key points", "The Halo One is a computer you wear: a private cinema and an office in one visor, the closest yet to that old promise.", false, listOf(0, 1, 2), listOf(0, 1, 2, 3)),
        Level("Brief", "The Halo One is a \$1,899 computer you wear: a private cinema and an office in one visor, the closest yet to its promise.", false, listOf(1), listOf(0, -1, 1, 0)),
        Level("TL;DR", "The Halo One: the best wearable screen yet, at \$1,899, though you will want a break after 2 hours.", true, emptyList(), listOf(0, 0, 0, 0)),
    )
    val entities = listOf(
        Entity("Halo One", "Halo One"),
        Entity("\$1,899", "\$1,899"),
        Entity("2-hour comfort", "2 hours"),
    )
    val share = listOf("Copy summary", "Share")
}
