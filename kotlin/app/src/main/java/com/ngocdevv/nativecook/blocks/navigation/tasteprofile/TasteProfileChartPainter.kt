package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** The held tile grown into a bar chart (TileChart.tsx), drawn in the pin's ref px. */
internal class TasteProfileChartPainter(
    private val assets: TasteProfileAssets,
    private val renderer: TasteProfileRenderer,
    private val board: TasteProfileBoardPainter,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()

    private fun argb(color: Color, alpha: Double = 1.0): Int = AndroidColor.argb(
        (color.alpha * alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
        (color.red * 255f).roundToInt(),
        (color.green * 255f).roundToInt(),
        (color.blue * 255f).roundToInt(),
    )

    private fun barX(i: Int): Float {
        val gap = (TasteProfileChart.w - 2 * TasteProfileChart.pad - 6 * TasteProfileChart.barW) / 5
        return (TasteProfileChart.pad + i * (TasteProfileChart.barW + gap)).toFloat()
    }

    fun rectOf(tile: Int): RectF {
        val x0 = TasteProfileBoxes.tiles[tile]
        val x = if (tile == 3) x0 + TasteProfileBoxes.tileW - TasteProfileChart.w.toFloat() else x0
        return RectF(x, TasteProfileBoxes.tileY, x + TasteProfileChart.w.toFloat(), TasteProfileBoxes.tileY + TasteProfileChart.h.toFloat())
    }

    fun draw(canvas: Canvas, viewportWidth: Float, viewportHeight: Float, geo: TasteProfileGeo, frame: TasteProfileFrame) {
        val hold = frame.hold
        if (hold.tile < 0) return
        val tile = hold.tile
        val city = frame.change.to
        val reduced = frame.change.reduced
        val u = geo.unit
        val r = rectOf(tile)
        fill.shader = null
        fill.style = Paint.Style.FILL
        fill.color = argb(TasteProfileChart.veil, hold.e)
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, fill)

        canvas.save()
        canvas.translate(0f, geo.y0)
        canvas.scale(u, u)
        val e = if (reduced) (if (hold.e > 0.0) 1.0 else 0.0) else hold.e
        val t0x = TasteProfileBoxes.tiles[tile]
        val left = interpolate(t0x.toDouble(), r.left.toDouble(), e).toFloat()
        val top = interpolate(TasteProfileBoxes.tileY.toDouble(), r.top.toDouble(), e).toFloat()
        val width = interpolate(TasteProfileBoxes.tileW.toDouble(), r.width().toDouble(), e).toFloat()
        val height = interpolate(TasteProfileBoxes.tileH.toDouble(), r.height().toDouble(), e).toFloat()
        val scale = (1.0 - 0.03 * hold.press).toFloat()
        val radius = TasteProfileRadius.tile
        val centreX = left + width / 2f
        val centreY = top + height / 2f

        // The lifted shadow is a second layer that fades in (a shadow does not animate).
        val lift = if (reduced) hold.e else clamp01(hold.e)
        canvas.save()
        canvas.scale(scale, scale, centreX, centreY)
        listOf(Triple(48f, 18f, 0.05), Triple(32f, 18f, 0.06), Triple(16f, 12f, 0.07)).forEach { (spread, dy, alpha) ->
            fill.color = argb(Color.Black, alpha * lift * TasteProfileExpandMotion.shadowTo / 0.16)
            canvas.drawRoundRect(left - spread / 3f, top + dy - spread / 6f, left + width + spread / 3f, top + height + dy + spread / 6f, radius + spread / 3f, radius + spread / 3f, fill)
        }
        val cardOpacity = if (reduced && hold.press == 0.0) hold.e else 1.0
        fill.color = argb(Color.Black, TasteProfileExpandMotion.shadowFrom * 0.7 * cardOpacity)
        canvas.drawRoundRect(left - 2f, top + 3f, left + width + 2f, top + height + 8f, radius + 2f, radius + 2f, fill)
        fill.color = argb(TasteProfileColors.tileFill, cardOpacity)
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, fill)
        stroke.strokeWidth = max(geo.density / u, 2.4f)
        stroke.color = argb(TasteProfileColors.tileHairline, cardOpacity)
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, stroke)
        canvas.restore()

        // Header: the tile's own icon, number and label, standing in for the tile.
        canvas.save()
        val tileCx = t0x + TasteProfileBoxes.tileW / 2f
        val tileCy = TasteProfileBoxes.tileY + TasteProfileBoxes.tileH / 2f
        canvas.scale(scale, scale, tileCx, tileCy)
        renderer.drawTileIcon(canvas, tile)
        board.statGlyphsOf(tile, city).forEach { glyph ->
            renderer.drawGlyph(canvas, glyph, TasteProfileGlyphRuns.stats[tile], 1.0)
        }
        renderer.drawText(canvas, TasteProfileTileLabels[tile])
        canvas.restore()

        // Content: caption and bars, fading in with a small rise.
        val contentAlpha = clamp01((hold.e - 0.45) / 0.4)
        if (contentAlpha > 0.0) {
            canvas.save()
            canvas.translate(r.left, r.top + ((1.0 - hold.e) * 24.0).toFloat())
            canvas.saveLayerAlpha(null, (contentAlpha * 255.0).roundToInt())
            drawContent(canvas, city, tile, hold, reduced)
            canvas.restore()
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, city: Int, tile: Int, hold: TasteProfileHoldView, reduced: Boolean) {
        val ascent = ASCENT
        val cityName = if (CITIES[city] == "all") "all cities" else CITIES[city]
        configureText(assets.bodyTypeface, 10.2f * 3f, TasteProfileChart.caption, 1.0)
        canvas.drawText(
            "last 6 months · $cityName",
            TasteProfileChart.pad.toFloat(),
            (TasteProfileChart.captionBaseline - 30.0).toFloat() + ascent * 10.2f * 3f,
            text,
        )
        val bars = monthBars(city, tile)
        val maxValue = bars.max().toFloat()
        bars.forEachIndexed { i, value ->
            val left = barX(i)
            val h = max(6f, value / maxValue * TasteProfileChart.barMax.toFloat())
            val room = h + 24f
            val start = TasteProfileExpandMotion.barDelay + i * TasteProfileExpandMotion.barStagger
            val p = if (reduced) 1.0 else tasteProfileSpring(hold.since - start, TasteProfileExpandMotion.barSpring)
            val on = clamp01(1.0 - abs(hold.hl - i))
            val labelOn = clamp01(1.0 - abs(hold.hl - i) * 1.6)
            val bottom = TasteProfileChart.barsBottom.toFloat()
            canvas.save()
            canvas.clipRect(left, bottom - room, left + TasteProfileChart.barW.toFloat(), bottom)
            val dy = ((1.0 - p) * (h + 4f)).toFloat()
            val mix = on.toFloat()
            fill.shader = null
            fill.color = AndroidColor.rgb((0xDC * (1f - mix)).roundToInt(), (0xDC * (1f - mix)).roundToInt(), (0xDC * (1f - mix)).roundToInt())
            rect.set(left, bottom - h + dy, left + TasteProfileChart.barW.toFloat(), bottom + dy)
            val corner = TasteProfileChart.barW.toFloat() / 2.6f
            canvas.drawRoundRect(rect, corner, corner, fill)
            canvas.restore()

            val centre = left + TasteProfileChart.barW.toFloat() / 2f
            configureText(assets.extraTypeface, 12.3f * 3f, Color.Black, labelOn * clamp01(p * 1.4 - 0.4))
            text.textAlign = Paint.Align.CENTER
            val valueTop = (TasteProfileChart.barsBottom - h - TasteProfileChart.valueGap - 34.0).toFloat()
            canvas.drawText(value.toString(), centre, valueTop + dy + ascent * 12.3f * 3f, text)
            configureText(assets.bodyTypeface, 10.2f * 3f, TasteProfileChart.caption, 1.0)
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(MONTHS[i], centre, (TasteProfileChart.monthBaseline - 28.0).toFloat() + ascent * 10.2f * 3f, text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    private fun configureText(typeface: android.graphics.Typeface, size: Float, color: Color, alpha: Double) {
        text.shader = null
        text.typeface = typeface
        text.textSize = size
        text.letterSpacing = 0f
        text.textAlign = Paint.Align.LEFT
        text.color = argb(color, alpha)
    }
}
