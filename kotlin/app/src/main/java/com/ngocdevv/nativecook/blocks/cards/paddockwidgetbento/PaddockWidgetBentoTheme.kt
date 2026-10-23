package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

/** Every colour, size and baseline of Paddock Widget Bento, in points on a 402-wide screen (theme.ts). */
internal object PaddockTheme {
    const val REFERENCE_WIDTH = 402f

    private fun rgb(r: Int, g: Int, b: Int, a: Float = 1f) = Color(r / 255f, g / 255f, b / 255f, a)

    val violet = rgb(50, 33, 252)
    val orange = rgb(254, 63, 3)
    val yellow = rgb(255, 218, 38)
    val cream = rgb(242, 238, 223)
    val statsGlow = rgb(42, 40, 40)
    val statsShade = rgb(20, 18, 21)
    val green = rgb(5, 208, 130)
    val blue = rgb(62, 157, 255)
    val red = rgb(247, 26, 33)
    val rose = rgb(253, 121, 120)
    val guideYellow = rgb(252, 254, 31)
    val micInk = rgb(255, 196, 178)
    val creamInk = rgb(242, 238, 223)
    val bannerShade = rgb(110, 0, 6, 0.92f)
    val bannerGlow = rgb(196, 8, 18, 0.62f)
    val bannerClear = rgb(220, 20, 28, 0.38f)
    val bannerTrail = rgb(167, 0, 9)
    val statsTrail = rgb(70, 66, 68)
    val ink75White = Color.White.copy(alpha = 0.75f)
    val ink55Black = Color.Black.copy(alpha = 0.55f)
    val closeFill = Color.Black.copy(alpha = 0.5f)
    val sealStroke = rgb(247, 26, 33, 0.18f)
    val stickerBack = rgb(246, 236, 232)
    val stickerFold = rgb(196, 172, 168)
    val stickerShadow = rgb(60, 0, 4)
    val dockFallback = rgb(28, 20, 22, 0.62f)
    val dockStroke = Color.White.copy(alpha = 0.22f)
    val dockLens = Color.White.copy(alpha = 0.20f)
    val dockLensStroke = Color.White.copy(alpha = 0.38f)
    val dockIcon = Color.White.copy(alpha = 0.94f)
    val dockIconActive = rgb(255, 226, 224)
    val pinnedLabel = rgb(235, 235, 245, 0.6f)

    /** Tile colours used by the pit-stop smear, by tile index. */
    val tileColors = listOf(violet, orange, yellow, cream, statsTrail, green, blue, red, guideYellow, bannerTrail)

    const val screenInset = 10f
    const val columnGap = 10f
    const val tileInset = 16f
    const val tileTextAfterMark = 60.5f
    const val tileMarkCenter = 31f
    const val pitcastInset = 14.5f
    const val pitcastArrowInset = 12.5f
    const val statsTextX = 85.3f
    const val statsCaptionX = 21.3f
    const val heroFirstX = 15.7f
    const val heroSecondX = 58.4f
    const val guideInset = 14.3f
    const val guideCloseInset = 12.7f
    const val bannerPad = 20f
    const val bannerTop = 28f
    const val bannerGap = 8f
    const val dockPad = 7f
    const val dockLensInset = 5f
    const val handleTrailing = 14f
    const val pinnedGap = 8f
    const val pinnedInset = 18f

    private const val COL = 186f
    private const val FULL = 382f
    private const val TOP = 54.5f
    private const val RIGHT = screenInset + COL + columnGap

    /** Left, top, width, height. */
    class R(val left: Float, val top: Float, val width: Float, val height: Float)

    val quick = R(10f, TOP, COL, 62f)
    val pitcast = R(RIGHT, TOP, COL, 210f)
    const val mic = 50f
    const val arrow = 18.5f
    const val glyphRow = 179.8f
    const val glyph = 20f
    val glyphX = floatArrayOf(25.7f, 62f, 99f)
    val stats = R(10f, 276.5f, FULL, 144f)
    val statsArt = R(254f, 15f, 106f, 110f)
    val statsIcon = R(25f, 27.5f, 41f, 51f)
    val glossary = R(10f, 432.5f, COL, 62f)
    val education = R(RIGHT, 432.5f, COL, 62f)
    val hero = R(10f, 506.5f, FULL, 128f)
    const val sealSize = 40.5f
    const val sealCenterX = 33f
    const val sealCenterY = 91.2f
    val guide = R(10f, 646.5f, FULL, 110f)
    const val closeSize = 18.5f
    const val closeCenterY = 21.5f
    val banner = R(10f, 768.5f, FULL, 170f)
    const val gridHeight = 938.5f
    const val eyebrow = 25.8f
    const val title = 44.8f
    val violetBaseline = floatArrayOf(32f, 47.5f)
    const val violetX = 71.7f
    const val pitcastTitle = 106.8f
    const val pitcastSubtitle = 131.4f
    const val pitcastNames = 152.5f
    const val statsEyebrow = 18.3f
    const val statsWord = 54.8f
    val caption = floatArrayOf(123.4f, 138.2f)
    const val heroFirst = 54f
    const val heroSecond = 108.8f
    const val guideTitle = 46.8f
    val guideHelper = floatArrayOf(70.7f, 87.9f)
    const val badgeWidth = 47.5f
    const val badgeHeight = 28.5f
    const val badgeCenterX = 38.5f
    const val badgeStroke = 2.5f
    const val chipWidth = 30f
    const val chipHeight = 19f
    const val chipTrailing = 14.7f
    const val doneWidth = 72f
    const val doneCenterX = 317f
    const val doneCenterY = 85.5f
    const val pinnedTop = 950.5f
    const val pinnedHeight = 64f
    const val pinnedPitch = 72f
    const val contentHeight = 950.5f
    const val contentMinHeight = 1010f
    const val bottomClearance = 118f
    const val dockWidth = 358f
    const val dockHeight = 62f
    const val dockBottom = 22f
    const val dockLensWidth = 74f
    const val dockLensHeight = 52f
    val touchSurface = R(10f, TOP, COL, 210f)
    val peelSpot = R(250f, 46f, 132f, 82f)

    const val radiusQuick = 31f
    const val radiusCard = 28f
    const val radiusMic = 13.5f
    const val radiusChip = 5.5f
    const val radiusPinned = 22f
    const val radiusLens = 26f

    const val eyebrowSize = 12.5f
    const val eyebrowTracking = -0.3f
    const val tileTitleSize = 16.5f
    const val tileTitleTracking = -0.2f
    const val violetSize = 19.5f
    const val violetTracking = -0.9f
    const val badgeSize = 13f
    const val cardIcon = 24f
    const val chipIcon = 13f
    const val handleSize = 16f
    const val pitcastIcon = 26f
    const val pitcastArrowIcon = 8f
    const val pitcastTitleSize = 32f
    const val pitcastSubtitleSize = 12.2f
    const val pitcastNamesSize = 10.2f
    const val statsIconSize = 50f
    const val statsEyebrowSize = 16f
    const val statsWordSize = 32.5f
    const val captionSize = 12f
    const val tileIcon = 26f
    const val heroGlyph = 61f
    const val heroSeal = 13f
    const val guideTitleSize = 28.2f
    const val guideHelperSize = 12f
    const val guideClose = 7.5f
    const val bannerTitleSize = 32f
    const val bannerSubtitleSize = 16f
    const val doneSize = 15f
    const val dockIconSize = 22f
}

internal enum class PaddockFace(val file: String, val weight: FontWeight) {
    Medium("medium", FontWeight.Medium),
    SemiBold("semibold", FontWeight.SemiBold),
    Bold("bold", FontWeight.Bold),
    ExtraBold("extrabold", FontWeight.ExtraBold),
    Black("black", FontWeight.Black),
}

/** Montserrat (OFL), bundled in the assets folder, once as Compose families and once as Android typefaces. */
internal class PaddockFonts(context: Context) {
    val families: Map<PaddockFace, FontFamily> = PaddockFace.entries.associateWith {
        FontFamily(Font("paddockwidgetbento/paddockwidgetbento_montserrat_${it.file}.ttf", context.assets, it.weight))
    }
    val typefaces: Map<PaddockFace, Typeface> = PaddockFace.entries.associateWith {
        Typeface.createFromAsset(context.assets, "paddockwidgetbento/paddockwidgetbento_montserrat_${it.file}.ttf")
    }
}

/** Text placed by its baseline: lines are measured once per (text, size, face) and drawn at `baseline - firstBaseline`. */
internal class PaddockTextBook(
    private val measurer: TextMeasurer,
    private val density: Density,
    val scale: Float,
    private val fonts: PaddockFonts,
) {
    private val cache = HashMap<String, TextLayoutResult>()
    val px: Float get() = scale * density.density

    fun layout(
        text: String,
        size: Float,
        face: PaddockFace?,
        tracking: Float = 0f,
        serifItalic: Boolean = false,
    ): TextLayoutResult {
        val key = "$text|$size|$face|$tracking|$serifItalic"
        return cache.getOrPut(key) {
            val style = with(density) {
                TextStyle(
                    fontSize = (size * px).toSp(),
                    letterSpacing = (tracking * px).toSp(),
                    fontFamily = if (serifItalic) FontFamily.Serif else fonts.families[face],
                    fontWeight = if (serifItalic) FontWeight.SemiBold else face?.weight,
                    fontStyle = if (serifItalic) FontStyle.Italic else null,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                )
            }
            measurer.measure(text, style, softWrap = false, maxLines = 1)
        }
    }

    /** Draws a line whose baseline is [baseline] points down the tile and whose left edge is [x] points in. */
    fun DrawScope.line(
        text: String, x: Float, baseline: Float, size: Float, face: PaddockFace?, color: Color,
        tracking: Float = 0f, alpha: Float = 1f, serifItalic: Boolean = false,
    ) {
        val result = layout(text, size, face, tracking, serifItalic)
        drawText(result, color = color, topLeft = Offset(x * px, baseline * px - result.firstBaseline), alpha = alpha)
    }

    fun width(text: String, size: Float, face: PaddockFace?, tracking: Float = 0f, serifItalic: Boolean = false): Float =
        layout(text, size, face, tracking, serifItalic).size.width / px
}
