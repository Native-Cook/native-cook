package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** The Ionicons font of the block (`assets/foliofeaturelibrary/foliofeaturelibrary_Ionicons.ttf`), provided by the root. */
internal val LocalFolioIonicons = staticCompositionLocalOf<Typeface> {
    error("FolioFeatureLibrary provides its icon font")
}

/**
 * The Ionicons the React Native block uses, by their Ionicons names, with the font's own codepoints (read
 * from the glyph names of the bundled font; `settings-sharp` F56D and `lock-closed` F3C7 in the same
 * reading match the Paper Deck Tutorial block).
 */
internal enum class FolioIcon(val glyph: String) {
    ChevronBack("\uF229"),
    ChevronForward("\uF23B"),
    ChevronDown("\uF232"),
    Add("\uF103"),
    ArrowDown("\uF12D"),
    Heart("\uF36A"),
    HeartOutline("\uF377"),
    Shuffle("\uF583"),
    GlobeOutline("\uF350"),
    ShareOutline("\uF572"),
    ChatbubbleOutline("\uF215"),
    CheckmarkCircleOutline("\uF21F"),
}

/**
 * `<Ionicons name size color />`: the glyph is text in the Ionicons font, [sizeDp] dp (the font size) in
 * [color], with its line box (ascent to descent) centred in a square of the same size, as a `Text` centres
 * its line.
 */
@Composable
internal fun FolioIconView(icon: FolioIcon, sizeDp: Float, color: Color, modifier: Modifier = Modifier) {
    val ionicons = LocalFolioIonicons.current
    val paint = remember(ionicons) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ionicons
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier.size(sizeDp.dp)) {
        paint.color = color.toArgb()
        paint.textSize = sizeDp.dp.toPx()
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(icon.glyph, size.width / 2f, baseline, paint) }
    }
}
