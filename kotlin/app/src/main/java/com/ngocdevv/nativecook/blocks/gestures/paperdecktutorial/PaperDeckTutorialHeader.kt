package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

private val TitleColor = Color(0xFFF8F7F7)
private val CountColor = Color(0xFF8E8A8D)
private val ChromeDisc = Color(0xFF2C2B2D)
private val CloseDisc = Color(0xFFF4F2F4)
private val CloseMark = Color(0xFF0A090A)
private const val GlyphColor = 0xFFD8D6D8.toInt()

/** `settings-sharp` and `lock-closed` in the Ionicons font. */
private const val GEAR_GLYPH = "\uF56D"
private const val LOCK_GLYPH = "\uF3C7"

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
private val LineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/**
 * `header`: the title ("Tutorial", or "This or that" once the tutorial is done) over its count ("2 / 6", or
 * "1 / 200"), centred, 26 dp below the top. The text swaps when the step changes. The gear, the lock and the
 * close disc that sit beside it are [PaperDeckTutorialHeaderButtons].
 */
@Composable
internal fun PaperDeckTutorialHeader(
    controller: PaperDeckTutorialController,
    assets: PaperDeckTutorialAssets,
    reduced: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val step by remember(controller) { derivedStateOf { controller.pose().step } }
    val titleFamily = remember(assets) { FontFamily(assets.interBold) }
    val countFamily = remember(assets) { FontFamily(assets.interRegular) }
    Box(modifier.fillMaxWidth().height(height).padding(horizontal = 10.dp)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 26.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PaperDeckTutorialSwapText(
                text = if (step == 6) "This or that" else "Tutorial",
                style = TextStyle(
                    color = TitleColor,
                    fontFamily = titleFamily,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.4f / 22f).em,
                    lineHeightStyle = LineHeight,
                ),
                fontSize = 22f,
                lineHeight = 27f,
                reduced = reduced,
                maxFontScale = 1.6f,
            )
            PaperDeckTutorialSwapText(
                text = if (step == 6) "1 / 200" else "${step + 1} / 6",
                style = TextStyle(
                    color = CountColor,
                    fontFamily = countFamily,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                    lineHeightStyle = LineHeight,
                ),
                fontSize = 15f,
                lineHeight = 19f,
                reduced = reduced,
                modifier = Modifier.padding(top = 6.dp),
                delayMs = 30,
                maxFontScale = 1.8f,
            )
        }
    }
}

/**
 * The two 48 dp touch targets at the header's corners (`close`, 29 dp above the header's top edge): the gear
 * on the left while the cover deck shows (back to the first card), and on the right the lock then (it does
 * nothing, "Unlock all decks") or, while the tutorial runs, the white close disc that finishes it. Touching
 * either holds the screen like any touch and lets it go on release. The glyphs scale with the system font
 * scale, as the icon font's `Text` does in React Native.
 */
@Composable
internal fun PaperDeckTutorialHeaderButtons(
    controller: PaperDeckTutorialController,
    assets: PaperDeckTutorialAssets,
    touchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val onDeck by remember(controller) {
        derivedStateOf {
            val pose = controller.pose()
            pose.step == 6 || (pose.step == 0 && pose.time < 0.85)
        }
    }
    val fontScale = LocalDensity.current.fontScale
    Box(modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
        if (onDeck) {
            PaperDeckTutorialPressable(
                label = "Restart tutorial",
                onPressIn = { if (touchEnabled) controller.buttonBegin() },
                onPressOut = { if (touchEnabled) controller.buttonEnd() },
                onPress = { if (touchEnabled) controller.restart() },
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
            ) {
                ChromeGlyphDisc(assets, GEAR_GLYPH, 13f * fontScale)
            }
        }
        PaperDeckTutorialPressable(
            label = if (onDeck) "Unlock all decks" else "Finish tutorial",
            onPressIn = { if (touchEnabled) controller.buttonBegin() },
            onPressOut = { if (touchEnabled) controller.buttonEnd() },
            onPress = { if (touchEnabled && !onDeck) controller.finish() },
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
        ) {
            if (onDeck) ChromeGlyphDisc(assets, LOCK_GLYPH, 12f * fontScale) else CloseDiscMark()
        }
    }
}

/** `chromeDisc`: a 27 dp disc of #2C2B2D with an Ionicons glyph of [sizeDp] in #D8D6D8 centred in it. */
@Composable
private fun ChromeGlyphDisc(assets: PaperDeckTutorialAssets, glyph: String, sizeDp: Float) {
    val paint = remember(assets) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = assets.icons
            textAlign = Paint.Align.CENTER
            color = GlyphColor
        }
    }
    Canvas(Modifier.size(27.dp)) {
        drawCircle(ChromeDisc)
        paint.textSize = sizeDp.dp.toPx()
        // The glyph's line box (its ascent to its descent) is centred in the disc, as a `Text` is.
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        drawIntoCanvas { it.nativeCanvas.drawText(glyph, size.width / 2f, baseline, paint) }
    }
}

/** `closeDisc`: a 27 dp white disc with a 12 dp cross, `M2 2l8 8M10 2 2 10`, stroke 2 with round caps. */
@Composable
private fun CloseDiscMark() {
    Canvas(Modifier.size(27.dp)) {
        drawCircle(CloseDisc)
        val unit = 1.dp.toPx()
        val left = (size.width - 12 * unit) / 2f
        val top = (size.height - 12 * unit) / 2f
        val stroke = 2 * unit
        drawLine(CloseMark, Offset(left + 2 * unit, top + 2 * unit), Offset(left + 10 * unit, top + 10 * unit), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(CloseMark, Offset(left + 10 * unit, top + 2 * unit), Offset(left + 2 * unit, top + 10 * unit), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
