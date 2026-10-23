package com.ngocdevv.nativecook.blocks.navigation.editorialmenumorph

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.max
import kotlin.math.roundToInt

// The museum's sections: the word in the menu with its small count, the one photo that floats
// beside the word and then becomes its page's hero, the colour the menu turns to, and the page
// each one opens on the site underneath.

/**
 * @param count The small count set after the word in the menu.
 * @param photo The photo beside the word in the menu and the hero of its page: one and the same.
 * @param tint The menu's colour while this word is under the finger.
 * @param title The page's headline, already broken into its two lines.
 */
data class MenuSection(
    val word: String,
    val count: String,
    val photo: String,
    val tint: Long,
    val kicker: String,
    val title: List<String>,
    val caption: String,
    val note: String,
)

internal val MenuSections = listOf(
    MenuSection("Exhibitions", "12", "photo-1705873653125-75312a404bc4", 0xFF5B1F1B, "NOW ON VIEW · HALL C", listOf("The Weight", "of Stillness"), "Marble and plaster, 1880–1930", "Until 2 March"),
    MenuSection("Collection", "4,120", "photo-1705996952322-b070e7982606", 0xFF1D3247, "THE COLLECTION · 4,120 WORKS", listOf("Bodies, Busts", "and Fragments"), "Search by room, maker or century", "Open daily"),
    MenuSection("Architecture", "1911", "photo-1608590766866-ae8d39690bbf", 0xFF6A4A1E, "THE BUILDING · 1911", listOf("A Stair Without", "an End"), "Ninety-one steps by Ada Velde", "Tours at 11:00"),
    MenuSection("Visit", "10–18", "photo-1550773316-169760ebc0d1", 0xFF1D4943, "VISIT · KESTREL QUAY 4", listOf("Light Comes", "Down at Four"), "Tuesday to Sunday, 10:00–18:00", "Free on Thursdays"),
    MenuSection("Journal", "Nº 12", "photo-1680869839348-4c76c37ce0e0", 0xFF393826, "JOURNAL · ISSUE 12", listOf("Notes from the", "Plaster Room"), "Essays, interviews and casts", "New every month"),
)

internal val Paper = Color(0xFFEFE9DF)
internal val Ink = Color(0xFF16130F)
private val Muted = Color(0xFF7B6F60)
internal val BaseTint = Color(0xFF151310)
internal val CardTone = Color(0xFF2A2622)
/** What the hero shows while its photo loads, or if it never does. */
internal val HeroPlaceholder = Color(0xFFD9D0C2)

// The display serif is the system serif at Medium (it never reads thin); the sans is the system
// sans at Medium/SemiBold. iOS sets the same layout in Iowan Old Style and Avenir Next.
internal val Display = FontFamily.Serif
internal val DisplayWeight = FontWeight.Medium
internal val Sans = FontFamily.SansSerif

/** A size in dp as a text size, so the layout holds whatever the system font scale is. */
@Composable
internal fun dpSp(value: Float): TextUnit = with(LocalDensity.current) { value.dp.toSp() }

/** An Unsplash photo, cropped to [width] × [height] dp at 3x. */
internal fun photoUrl(id: String, width: Float, height: Float): String =
    "https://images.unsplash.com/$id?auto=format&fit=crop&w=${(width * 3f).roundToInt()}&h=${(height * 3f).roundToInt()}&q=80"

// The page, laid out by hand so the menu's card knows exactly where the hero is: the kicker row
// 74 dp below the safe area, two title lines of 48 dp, the hero 18 dp under them, 20 dp from the
// sides, at most 44% of the screen tall and never into the footer.
internal const val TITLE_SIZE = 40f
internal const val TITLE_LINE = 48f

internal class PageLayout(width: Float, height: Float, top: Float, bottom: Float) {
    val kickerY = top + 74f
    val titleY = kickerY + 28f
    val heroX = 20f
    val heroY = titleY + 2f * TITLE_LINE + 18f
    val heroW = max(1f, width - 40f)
    val heroH = max(1f, minOf(height * 0.44f, heroW * 1.08f, height - bottom - 110f - heroY))
    val captionY = heroY + heroH + 12f
}

// The page's own entrance, after a hero lands: one timeline from 0 to 1 over PAGE_IN_TIME s,
// linearly, each part easing inside its slice.
internal const val PAGE_IN_TIME = 0.9f
private val IndexRoll = 0f to 0.45f
private val KickerIn = 0f to 0.4f
private const val TITLE_FROM = 0.06f
private const val TITLE_STAGGER = 0.1f
private const val TITLE_SPAN = 0.5f
private val CaptionIn = 0.4f to 0.85f
private val FooterIn = 0.5f to 1f
private const val ROLL = 14f

private fun slice(t: Float, s: Pair<Float, Float>) = EaseOutStrong.transform(((t - s.first) / (s.second - s.first)).coerceIn(0f, 1f))

/** Reused objects for drawing a photo "cover" into a rect. */
internal class CoverPaints {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val matrix = Matrix()
    val path = android.graphics.Path()
}

/**
 * Draws [image] "cover" into the rect (0, 0, [w], [h]) in dp, zoomed about its centre: the card
 * and the hero share it, so the card hands off to the hero pixel for pixel.
 */
internal fun drawCover(canvas: android.graphics.Canvas, image: Bitmap, w: Float, h: Float, zoom: Float, p: CoverPaints) {
    if (image.width <= 0 || image.height <= 0 || w <= 0f || h <= 0f) return
    val fit = max(w / image.width, h / image.height) * zoom
    p.matrix.setScale(fit, fit)
    p.matrix.postTranslate((w - image.width * fit) / 2f, (h - image.height * fit) / 2f)
    p.paint.color = android.graphics.Color.BLACK
    p.paint.alpha = 255
    canvas.drawBitmap(image, p.matrix, p.paint)
}

/** The site under the menu: one section's page, as a quiet editorial layout. */
@Composable
internal fun MuseumPage(
    engine: MenuEngine,
    sections: List<MenuSection>,
    photos: Array<Bitmap?>,
    width: Float,
    height: Float,
    top: Float,
    bottom: Float,
    reduced: Boolean,
    paints: CoverPaints,
) {
    val index = engine.section.coerceIn(0, sections.size - 1)
    val previous = engine.previousSection.coerceIn(0, sections.size - 1)
    val s = sections[index]
    val l = PageLayout(width, height, top, bottom)
    val small = TextStyle(fontFamily = Sans, fontSize = dpSp(11f), lineHeight = dpSp(ROLL), fontWeight = FontWeight.SemiBold, letterSpacing = dpSp(1.2f), color = Ink, fontFeatureSettings = "tnum")
    // A headline line no wider than the page: the serif's widest line sets one size for both.
    val titleSize = TITLE_SIZE * engine.titleFit
    Box(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.offset(x = 20.dp, y = l.kickerY.dp).width((width - 40f).dp).height(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                s.kicker,
                Modifier.weight(1f).graphicsLayer {
                    engine.tick
                    val e = slice(engine.pageIn.value, KickerIn)
                    alpha = e
                    translationY = if (reduced) 0f else ((1f - e) * 10f).dp.toPx()
                },
                style = TextStyle(fontFamily = Sans, fontSize = dpSp(11f), fontWeight = FontWeight.SemiBold, letterSpacing = dpSp(2f), color = Muted),
                maxLines = 1,
                softWrap = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The section number: the old one rolls up and out as the new one rolls in from below.
                Box(Modifier.height(ROLL.dp).clipToBounds()) {
                    if (previous != index) {
                        BasicText(
                            "%02d".format(previous + 1),
                            Modifier.graphicsLayer {
                                engine.tick
                                val e = slice(engine.pageIn.value, IndexRoll)
                                if (reduced) alpha = 1f - e else translationY = (-e * ROLL).dp.toPx()
                            },
                            style = small,
                        )
                    }
                    BasicText(
                        "%02d".format(index + 1),
                        Modifier.graphicsLayer {
                            engine.tick
                            val e = if (previous == index) 1f else slice(engine.pageIn.value, IndexRoll)
                            if (reduced) alpha = e else translationY = ((1f - e) * ROLL).dp.toPx()
                        },
                        style = small,
                    )
                }
                BasicText(" / " + "%02d".format(sections.size), style = small)
            }
        }

        val titleStyle = TextStyle(
            fontFamily = Display,
            fontWeight = DisplayWeight,
            fontSize = dpSp(titleSize),
            lineHeight = dpSp(TITLE_LINE),
            letterSpacing = dpSp(-0.4f),
            color = Ink,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        for ((row, line) in s.title.withIndex()) {
            // One headline line, rising out of its own clipped baseline after the one above it.
            Box(
                Modifier
                    .offset(x = 20.dp, y = (l.titleY + row * TITLE_LINE).dp)
                    .size((width - 40f).dp, TITLE_LINE.dp)
                    .clipToBounds(),
            ) {
                BasicText(
                    line,
                    Modifier.graphicsLayer {
                        engine.tick
                        val from = TITLE_FROM + row * TITLE_STAGGER
                        val e = slice(engine.pageIn.value, from to from + TITLE_SPAN)
                        if (reduced) {
                            alpha = e
                        } else {
                            transformOrigin = TransformOrigin.Center
                            translationY = ((1f - e) * TITLE_LINE).dp.toPx()
                            rotationZ = (1f - e) * 3f
                        }
                    },
                    style = titleStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Canvas(Modifier.offset(x = l.heroX.dp, y = l.heroY.dp).size(l.heroW.dp, l.heroH.dp)) {
            engine.tick
            val shown = engine.heroShown.value
            if (shown <= 0.002f) return@Canvas
            drawIntoCanvas {
                val c = it.nativeCanvas
                c.saveLayerAlpha(0f, 0f, size.width, size.height, (shown * 255f).roundToInt())
                c.scale(density, density)
                paints.paint.color = android.graphics.Color.argb(255, 0xD9, 0xD0, 0xC2)
                c.drawRect(0f, 0f, l.heroW, l.heroH, paints.paint)
                photos.getOrNull(index)?.let { image -> drawCover(c, image, l.heroW, l.heroH, 1f, paints) }
                c.restore()
            }
        }

        Row(
            Modifier
                .offset(x = 20.dp, y = l.captionY.dp)
                .width((width - 40f).dp)
                .graphicsLayer {
                    engine.tick
                    val e = slice(engine.pageIn.value, CaptionIn)
                    alpha = e
                    translationY = if (reduced) 0f else ((1f - e) * 8f).dp.toPx()
                }
                .drawBehind { drawRect(Ink.copy(alpha = 0.35f), topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f)) }
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The caption takes what the note leaves, 12 dp clear of it.
            BasicText(
                s.caption,
                Modifier.weight(1f, fill = false).padding(end = 12.dp).alignByBaseline(),
                style = TextStyle(fontFamily = Display, fontWeight = DisplayWeight, fontStyle = FontStyle.Italic, fontSize = dpSp(15f), color = Ink),
                maxLines = 1,
                softWrap = false,
            )
            BasicText(s.note, Modifier.alignByBaseline(), style = TextStyle(fontFamily = Sans, fontSize = dpSp(12f), fontWeight = FontWeight.SemiBold, color = Muted))
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = (bottom + 18f).dp)
                .graphicsLayer {
                    engine.tick
                    alpha = slice(engine.pageIn.value, FooterIn)
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Plan a visit", style = TextStyle(fontFamily = Sans, fontSize = dpSp(15f), fontWeight = FontWeight.SemiBold, letterSpacing = dpSp(0.2f), color = Ink))
            Canvas(Modifier.size(22.dp, 12.dp)) {
                val k = density
                val arrow = Path().apply {
                    moveTo(1f * k, 6f * k)
                    lineTo(20f * k, 6f * k)
                    moveTo(15f * k, 1f * k)
                    lineTo(20f * k, 6f * k)
                    lineTo(15f * k, 11f * k)
                }
                drawPath(arrow, Ink, style = Stroke(width = 1.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
