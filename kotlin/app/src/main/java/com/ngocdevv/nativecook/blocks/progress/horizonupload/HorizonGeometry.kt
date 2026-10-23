// Layout stays in points; only the outer canvas and antialiasing know device density.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import kotlin.math.*

internal class HorizonGeometry(val width: Float, val height: Float, top: Float, val bottom: Float, val density: Float, fontScale: Float) {
    val cardWidth = floor(min(width - 72, 360f) / 2) * 2
    val x = floor((width - cardWidth) / 2)
    val y = round(top + (height - top - bottom - 240) * .42f)
    val cx = x + cardWidth / 2
    val cy = y + 120
    val panelWidth = cardWidth - 48
    val barWidth = cardWidth - 72
    val apex = y + 206
    val reach = 560 * asin(min(width / 1120, 1f)) + 20
    val copyLeft = min(152f, cardWidth - 24 - 96)
    val fileScale = min(1f, (copyLeft - 24 - 12) / 116)
    val nameScale = fontScale.coerceAtMost(1.3f)
    private val fitScale = (barWidth - 18 - 10 - 8) / (78 + 114)
    private val numberBaseline = max(0f, (36 - 26 * 1.366f) / 2) + 26 * 1.066f
    private val labelAscent = (20 + 15 * (1.066f - .3f)) / 2
    val typeScale = min(min(fontScale, 1.3f), min(39 / (numberBaseline + 20 - labelAscent), if (cardWidth >= 303) max(1f, fitScale) else 1.3f))
    val figureTop = max(0f, 20 - 19 * typeScale)
    val baseline = figureTop + numberBaseline * typeScale
    val labelTop = baseline - labelAscent * typeScale
    val labelWidth = panelWidth - 12 - 78 * typeScale - 8 - 40
    val figureClip = min(36 * typeScale, 39 - figureTop)
    val glyphTop = baseline - 5.5f * typeScale - 9
}
