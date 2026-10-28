package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A text morph that diffs characters (MorphingText.tsx, Choose Your Ball's, copied so the block stays self-contained and given
// the reference's `weight` and `align` props). When the text changes, the letters the
// two strings share keep their identity and glide to their new place; the others leave (up,
// right, shrink, blur, fade) and the new ones arrive (rise, grow, sharpen, fade), each a little
// after the one before. The glyph blur is a RenderEffect, which needs Android 12; older systems
// morph without it.

/** Per-character delay through the string. */
private const val STAGGER_MS = 22
/** Lead time, so the outgoing letters have cleared before the new ones arrive. */
private const val ENTER_DELAY_MS = 110
/** dp the incoming glyph rises from, and where the outgoing one goes. */
private const val ENTER_RISE = 14f
private const val EXIT_UP = 12f
private const val EXIT_RIGHT = 8f
/** Scale a glyph starts and ends at. */
private const val SHRINK = 0.7f
private const val MOVE_MS = 260
private const val EXIT_MS = 240
/** Surviving glyphs wait, so the rewrite reads as a sequence and not as a slide. */
private const val GLIDE_DELAY_MS = 140
private const val GLIDE_MS = 320
/** From the baseline to the visual centre of a line of capitals, as a fraction of the font size. */
private const val CENTER_RATIO = 0.34f
/** Blur (sigma) of a glyph that is fully out, as a fraction of the font size. */
private const val BLUR_RATIO = 0.11f

/** Reanimated's default timing curve: Easing.inOut(Easing.quad). */
private val EaseInOutQuad = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (2f - 2f * t) * (2f - 2f * t) / 2f }

/** Reanimated 4's default spring (damping 120, mass 4, stiffness 900): critically damped. */
private val ENTER_SPRING = spring<Float>(
    dampingRatio = 120f / (2f * kotlin.math.sqrt(900f * 4f)),
    stiffness = 900f / 4f,
    visibilityThreshold = 0.001f,
)

private class GlyphCell(
    /** The character and its running count: the second L of a word stays the second L. */
    val key: String,
    val char: String,
    /** Position in the string, which drives the stagger. */
    val index: Int,
    /** Left edge within the canvas, in pixels. */
    val x: Float,
    val exiting: Boolean,
    val layout: TextLayoutResult,
)

/**
 * `fontSize` is a ceiling: the size is fitted once to the widest of `fitTexts`, so the type does
 * not resize as the text changes. `tracking` is added to every glyph's advance. Sizes are in dp
 * and do not follow the system's font scale, like the Skia text of the reference.
 */
@Composable
internal fun HabitatMorphingText(
    text: String,
    fitTexts: List<String>,
    fontSize: Dp,
    tracking: Dp,
    width: Dp,
    height: Dp,
    paddingLeft: Dp,
    color: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Black,
    center: Boolean = false,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)

    val typesetter = remember(measurer, density, fitTexts, fontSize, tracking, width, paddingLeft, color, weight, center) {
        with(density) {
            val probe = Typesetter(measurer, glyphStyle(color, fontSize.toSp(), weight), 0f, 0f, 0f, false)
            val usable = (width - paddingLeft).toPx()
            val widest = fitTexts.maxOfOrNull { probe.width(it) + tracking.toPx() * it.length } ?: 0f
            val fitted = if (widest <= usable || widest == 0f) fontSize else floor(fontSize.value * usable / widest).dp
            // Tracking scales with the fitted size, or a shrunk headline comes apart.
            val fittedTracking = tracking * (fitted / fontSize)
            Typesetter(measurer, glyphStyle(color, fitted.toSp(), weight), fittedTracking.toPx(), paddingLeft.toPx(), fitted.toPx(), center, width.toPx())
        }
    }

    // The glyphs on screen: those of the current text plus the ones still leaving.
    val book = remember { GlyphBook() }
    book.show(text, typesetter)

    val baselineY = with(density) { height.toPx() } / 2f + typesetter.fontSizePx * CENTER_RATIO

    Box(modifier.size(width, height)) {
        for (cell in book.cells) {
            key(cell.key) {
                CharGlyph(
                    cell = cell,
                    baselineY = baselineY,
                    fontSizePx = typesetter.fontSizePx,
                    reducedMotion = reducedMotion,
                    onExited = book::remove,
                )
            }
        }
    }
}

/**
 * Reconciles the glyphs on screen with the text. Whether a glyph enters, leaves or stays is
 * decided by comparing the new string with what is shown, so this is state and not a calculation.
 */
private class GlyphBook {
    var cells by mutableStateOf(emptyList<GlyphCell>())
        private set
    private var text: String? = null
    private var typesetter: Typesetter? = null

    fun show(text: String, typesetter: Typesetter) {
        if (this.text == text && this.typesetter === typesetter) return
        this.text = text
        this.typesetter = typesetter
        val next = typesetter.layout(text)
        val nextKeys = next.mapTo(HashSet()) { it.key }
        // A glyph already leaving keeps its cell, so its exit is never restarted.
        val leaving = cells.filter { it.key !in nextKeys }.map { old ->
            if (old.exiting) old else GlyphCell(old.key, old.char, old.index, old.x, true, old.layout)
        }
        cells = next + leaving
    }

    /** Drops a glyph whose exit has finished, unless it came back in the meantime. */
    fun remove(key: String) {
        cells = cells.filterNot { it.key == key && it.exiting }
    }
}

private fun glyphStyle(color: Color, fontSize: TextUnit, weight: FontWeight) = TextStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Lays a string out one glyph at a time: true advances plus the tracking, no kerning. */
private class Typesetter(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val trackingPx: Float,
    private val paddingLeftPx: Float,
    val fontSizePx: Float = 0f,
    private val center: Boolean = false,
    private val widthPx: Float = 0f,
) {
    private fun measure(char: String): TextLayoutResult = measurer.measure(char, style, softWrap = false, maxLines = 1)

    private fun advance(layout: TextLayoutResult): Float = layout.getLineRight(0) - layout.getLineLeft(0)

    fun width(text: String): Float = characters(text).sumOf { advance(measure(it)).toDouble() }.toFloat()

    fun layout(text: String): List<GlyphCell> {
        val counts = HashMap<String, Int>()
        val chars = characters(text)
        // Centred: the string's true advances plus its tracking are centred in the canvas.
        var cursor = if (center) {
            (widthPx - (chars.sumOf { advance(measure(it)).toDouble() }.toFloat() + trackingPx * chars.size)) / 2f
        } else paddingLeftPx
        return chars.mapIndexed { index, char ->
            val occurrence = counts.getOrDefault(char, 0)
            counts[char] = occurrence + 1
            val layout = measure(char)
            GlyphCell("$char#$occurrence", char, index, cursor, false, layout).also {
                cursor += advance(layout) + trackingPx
            }
        }
    }

    /** By code point, so a letter outside the basic plane stays one glyph. */
    private fun characters(text: String): List<String> =
        text.codePoints().toArray().map { String(Character.toChars(it)) }
}

/**
 * One glyph, and a state machine that can be retargeted: a glyph caught leaving that is wanted
 * again turns round from where it is. `motion` drives the offset and the scale, `fade` the
 * opacity and the blur, and `gx` the layout position, so a glide can run while a glyph enters.
 */
@Composable
private fun CharGlyph(
    cell: GlyphCell,
    baselineY: Float,
    fontSizePx: Float,
    reducedMotion: Boolean,
    onExited: (String) -> Unit,
) {
    val density = LocalDensity.current
    val gx = remember { Animatable(cell.x) }
    val motion = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }

    val rise = if (reducedMotion) 0f else with(density) { ENTER_RISE.dp.toPx() }
    val up = if (reducedMotion) 0f else with(density) { EXIT_UP.dp.toPx() }
    val right = if (reducedMotion) 0f else with(density) { EXIT_RIGHT.dp.toPx() }
    val shrink = if (reducedMotion) 1f else SHRINK
    val blurMax = if (reducedMotion) 0f else fontSizePx * BLUR_RATIO
    val stagger = if (reducedMotion) 0 else STAGGER_MS

    // The glide: to a new layout position, after a short wait. Nothing to do only when the glyph
    // is at rest there; a glide that was cut short has that target too, and is still on its way.
    LaunchedEffect(cell.x) {
        if (gx.value == cell.x && !gx.isRunning) return@LaunchedEffect
        if (!reducedMotion) delay(GLIDE_DELAY_MS.toLong())
        gx.animateTo(cell.x, tween(GLIDE_MS, easing = EaseInOutQuad))
    }

    // In and out, restarted only when the phase really flips.
    LaunchedEffect(cell.exiting) {
        if (!cell.exiting) {
            leaving = false
            val wait = if (reducedMotion) 0L else (ENTER_DELAY_MS + cell.index * stagger).toLong()
            launch {
                delay(wait)
                motion.animateTo(1f, ENTER_SPRING)
            }
            delay(wait)
            fade.animateTo(1f, tween(MOVE_MS, easing = EaseInOutQuad))
        } else {
            leaving = true
            val wait = (cell.index * stagger).toLong()
            launch {
                delay(wait)
                motion.animateTo(0f, tween(EXIT_MS, easing = EaseInOutQuad))
            }
            delay(wait)
            fade.animateTo(0f, tween(EXIT_MS, easing = EaseInOutQuad))
            // Reached only when the fade ran to its end: a glyph that came back is not removed.
            onExited(cell.key)
        }
    }

    val layout = cell.layout
    val top = baselineY - layout.firstBaseline
    // Scaled about the middle of the capitals, not the baseline, or a shrinking letter sinks.
    val pivotY = (layout.firstBaseline - fontSizePx * CENTER_RATIO) / layout.size.height

    Box(
        Modifier
            .size(with(density) { layout.size.width.toDp() }, with(density) { layout.size.height.toDp() })
            .graphicsLayer {
                val settled = motion.value
                val away = 1f - settled
                translationX = gx.value + if (leaving) right * away else 0f
                translationY = top + (if (leaving) -up else rise) * away
                val scale = shrink + (1f - shrink) * settled
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, pivotY)
                alpha = fade.value.coerceIn(0f, 1f)
                renderEffect = glyphBlur(blurMax * (1f - fade.value))
                clip = false
            }
            .drawBehind { drawText(layout) },
    )
}

/**
 * A Gaussian blur of `sigma` pixels, as Skia's BlurMask gives the reference's glyphs. The platform
 * takes a radius and blurs by 0.57735 · radius + 0.5, so it cannot blur by less than half a
 * pixel: from a quarter to a half the smallest blur is the nearest there is, and below a quarter
 * no blur is. Before Android 12 there is no RenderEffect, and the glyphs morph sharp.
 */
private fun glyphBlur(sigma: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || sigma < 0.25f) return null
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(MIN_BLUR_RADIUS)
    return BlurEffect(radius, radius, TileMode.Decal)
}

private const val MIN_BLUR_RADIUS = 0.001f
