package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class SaveToCollectionRenderer(
    private val assets: SaveToCollectionAssets,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    fun renderStaticPage(layout: SaveToCollectionLayout, density: Float): Bitmap {
        val bitmap = bitmap(layout.width, layout.height, density)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        canvas.drawColor(SaveToCollectionTokens.Page)
        drawHeader(canvas, layout)
        drawChips(canvas, layout)
        layout.tiles.forEachIndexed { index, frame ->
            drawPhoto(canvas, assets.photos[index], frame, SaveToCollectionTokens.Radius, 1f)
        }
        drawBottomFade(canvas, layout)
        drawTabs(canvas, layout)
        return bitmap
    }

    fun renderSheet(layout: SaveToCollectionLayout, density: Float, fresh: Boolean): Bitmap {
        val panelHeight = layout.height - layout.sheetY + 40f
        val bitmap = bitmap(layout.width, panelHeight, density)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        drawGlass(canvas, RectF(0f, 0f, layout.width, panelHeight), SaveToCollectionTokens.SheetRadius, true)
        setFill(0x4DFFFFFF)
        canvas.drawRoundRect(RectF(layout.width / 2f - 18f, 8f, layout.width / 2f + 18f, 13f), 2.5f, 2.5f, fill)

        drawText(canvas, SaveToCollectionTokens.CollectionName, 16f, 29f, 22f, assets.semibold, SaveToCollectionTokens.Ink)
        val meta = if (fresh) "13 places · Updated just now" else "12 places · Updated 2 days ago"
        drawText(canvas, meta, 16f, 59f, 13f, assets.regular, SaveToCollectionTokens.Muted)
        drawGlass(canvas, RectF(layout.width - 52f, 29f, layout.width - 16f, 65f), 18f, false)
        drawGlyph(canvas, SaveGlyph.Close, layout.width - 34f, 47f, 18f, SaveToCollectionTokens.Ink, false, 2f)

        SaveToCollectionTokens.CollectionBefore.forEachIndexed { index, photo ->
            drawCollectionTile(canvas, photo, if (fresh) index + 1 else index, layout, 97f, false)
        }
        return bitmap
    }

    fun draw(
        canvas: Canvas,
        engine: SaveToCollectionEngine,
        sheetBefore: Bitmap,
        sheetFresh: Bitmap,
    ) {
        drawTileState(canvas, engine)
        drawBar(canvas, engine)
        drawToast(canvas, engine)
        drawCarried(canvas, engine)
        drawSheet(canvas, engine, sheetBefore, sheetFresh)
        drawFinger(canvas, engine)
    }

    private fun drawHeader(canvas: Canvas, layout: SaveToCollectionLayout) {
        val centerY = layout.safeTop + SaveToCollectionTokens.TitleHeight / 2f
        drawTextCenteredVertically(
            canvas,
            "Discover",
            SaveToCollectionTokens.Edge,
            centerY,
            28f,
            assets.semibold,
            SaveToCollectionTokens.Ink,
        )
        val avatar = RectF(layout.width - 52f, centerY - 18f, layout.width - 16f, centerY + 18f)
        drawGlass(canvas, avatar, 18f, false)
        drawTextCentered(canvas, "LK", avatar.centerX(), avatar.centerY(), 13f, assets.medium, SaveToCollectionTokens.Ink)
    }

    private fun drawChips(canvas: Canvas, layout: SaveToCollectionLayout) {
        val labels = listOf("For you", "Coast", "Mountains", "Rooms")
        var x = SaveToCollectionTokens.Edge
        val top = layout.safeTop + SaveToCollectionTokens.ChipsTop
        labels.forEachIndexed { index, label ->
            prepareText(14f, assets.medium, if (index == 0) SaveToCollectionTokens.Page else SaveToCollectionTokens.Muted)
            val width = text.measureText(label) + 28f
            val frame = RectF(x, top, x + width, top + SaveToCollectionTokens.ChipHeight)
            if (index == 0) {
                setFill(SaveToCollectionTokens.Ink)
                canvas.drawRoundRect(frame, 16f, 16f, fill)
            } else {
                setStroke(SaveToCollectionTokens.Hairline, 0.5f)
                canvas.drawRoundRect(frame, 16f, 16f, stroke)
            }
            drawTextCentered(canvas, label, frame.centerX(), frame.centerY(), 14f, assets.medium, if (index == 0) SaveToCollectionTokens.Page else SaveToCollectionTokens.Muted)
            x += width + 8f
        }
    }

    private fun drawBottomFade(canvas: Canvas, layout: SaveToCollectionLayout) {
        val top = layout.barY - 72f
        fill.style = Paint.Style.FILL
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f,
            top,
            0f,
            top + (layout.height - top) * 0.7f,
            intArrayOf(0x000A0A0B, SaveToCollectionTokens.Page),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, top, layout.width, layout.height, fill)
        fill.shader = null
    }

    private fun drawTabs(canvas: Canvas, layout: SaveToCollectionLayout) {
        val width = layout.width - SaveToCollectionTokens.Edge * 2f - SaveToCollectionTokens.SaveSize - 12f
        val frame = RectF(16f, layout.barY, 16f + width, layout.barY + SaveToCollectionTokens.BarHeight)
        drawGlass(canvas, frame, SaveToCollectionTokens.BarHeight / 2f, false)
        val itemWidth = 64f
        val available = width - 24f
        val gap = (available - itemWidth * 3f) / 2f
        val labels = listOf("Home", "Search", "Inbox")
        val glyphs = listOf(SaveGlyph.Home, SaveGlyph.Search, SaveGlyph.Inbox)
        labels.indices.forEach { index ->
            val left = frame.left + 12f + index * (itemWidth + gap)
            val centerX = left + itemWidth / 2f
            val color = if (index == 0) SaveToCollectionTokens.Ink else SaveToCollectionTokens.Muted
            drawGlyph(canvas, glyphs[index], centerX, layout.barY + 23f, 22f, color, false, if (index == 0) 2f else 1.75f)
            drawTextCentered(canvas, labels[index], centerX, layout.barY + 47f, 10f, assets.medium, color)
        }
    }

    private fun drawTileState(canvas: Canvas, engine: SaveToCollectionEngine) {
        if (engine.carriedIndex >= 0) {
            engine.layout.tiles.forEachIndexed { index, frame ->
                val alpha = if (index == engine.carriedIndex) 0.84f else 0.3f * engine.lift.value
                if (alpha > 0f) {
                    setFill(withAlpha(SaveToCollectionTokens.Page, alpha))
                    canvas.drawRoundRect(frame.rect(), SaveToCollectionTokens.Radius, SaveToCollectionTokens.Radius, fill)
                }
            }
        }
        val marked = engine.markedIndex
        if (marked >= 0 && engine.mark.value > 0f) {
            val tile = engine.layout.tiles[marked]
            val scale = 0.8f + 0.2f * engine.mark.value
            canvas.save()
            canvas.scale(scale, scale, tile.x + tile.width - 22f, tile.y + 22f)
            canvas.saveLayerAlpha(null, (255f * engine.mark.value).roundToInt().coerceIn(0, 255))
            val frame = RectF(tile.x + tile.width - 36f, tile.y + 8f, tile.x + tile.width - 8f, tile.y + 36f)
            drawGlass(canvas, frame, 14f, false)
            drawGlyph(canvas, SaveGlyph.Bookmark, frame.centerX(), frame.centerY(), 16f, SaveToCollectionTokens.Ink, true, 1.75f)
            canvas.restore()
            canvas.restore()
        }
    }

    private fun drawBar(canvas: Canvas, engine: SaveToCollectionEngine) {
        val pull = engine.pull.value
        val haloAlpha = 0.9f * pull
        if (haloAlpha > 0f) {
            setFill(withAlpha(Color.WHITE, 0.08f * haloAlpha))
            canvas.drawCircle(engine.layout.saveX, engine.layout.saveY, 50f * (1f + 0.3f * pull), fill)
        }
        val scale = engine.gulp.value * (1f + 0.12f * pull) * (1f - 0.06f * engine.press.value)
        canvas.save()
        canvas.scale(scale, scale, engine.layout.saveX, engine.layout.saveY)
        val frame = RectF(
            engine.layout.saveX - SaveToCollectionTokens.SaveSize / 2f,
            engine.layout.saveY - SaveToCollectionTokens.SaveSize / 2f,
            engine.layout.saveX + SaveToCollectionTokens.SaveSize / 2f,
            engine.layout.saveY + SaveToCollectionTokens.SaveSize / 2f,
        )
        drawGlass(canvas, frame, SaveToCollectionTokens.SaveSize / 2f, false)
        drawGlyph(canvas, SaveGlyph.Bookmark, frame.centerX(), frame.centerY(), 24f, SaveToCollectionTokens.Ink, true, 2f)

        val badge = RectF(frame.right - 18f, frame.top - 4f, frame.right + 4f, frame.top + 18f)
        setFill(SaveToCollectionTokens.Ink)
        canvas.drawRoundRect(badge, 11f, 11f, fill)
        canvas.saveLayerAlpha(null, (255f * engine.roll.value).roundToInt().coerceIn(0, 255))
        drawTextCentered(
            canvas,
            engine.count.toString(),
            badge.centerX(),
            badge.centerY() + 6f * (1f - engine.roll.value),
            11f,
            assets.semibold,
            SaveToCollectionTokens.Page,
        )
        canvas.restore()
        canvas.restore()
    }

    private fun drawToast(canvas: Canvas, engine: SaveToCollectionEngine) {
        val alpha = engine.toast.value
        if (alpha <= 0f) return
        prepareText(14f, assets.medium, SaveToCollectionTokens.Muted)
        val first = "Saved to "
        val strong = SaveToCollectionTokens.CollectionName
        val view = "View"
        val firstWidth = text.measureText(first)
        val strongWidth = text.measureText(strong)
        prepareText(14f, assets.semibold, SaveToCollectionTokens.Ink)
        val viewWidth = text.measureText(view)
        val width = 36f + firstWidth + strongWidth + 16f + viewWidth
        val top = engine.layout.barY - 56f + 8f * (1f - alpha)
        val left = (engine.layout.width - width) / 2f
        val frame = RectF(left, top, left + width, top + 44f)
        canvas.save()
        canvas.scale(0.96f + 0.04f * alpha, 0.96f + 0.04f * alpha, frame.centerX(), frame.centerY())
        canvas.saveLayerAlpha(null, (255f * alpha).roundToInt().coerceIn(0, 255))
        drawGlass(canvas, frame, 22f, false)
        val baseline = centeredBaseline(frame.centerY(), 14f, assets.medium)
        var x = frame.left + 18f
        drawTextAtBaseline(canvas, first, x, baseline, 14f, assets.medium, SaveToCollectionTokens.Muted)
        x += firstWidth
        drawTextAtBaseline(canvas, strong, x, baseline, 14f, assets.medium, SaveToCollectionTokens.Ink)
        drawTextAtBaseline(canvas, view, frame.right - 18f - viewWidth, baseline, 14f, assets.semibold, SaveToCollectionTokens.Ink)
        canvas.restore()
        canvas.restore()
    }

    private fun drawCarried(canvas: Canvas, engine: SaveToCollectionEngine) {
        val index = engine.carriedIndex
        if (index < 0) return
        val tile = engine.layout.tiles[index]
        val magnet = engine.pull.value * SaveToCollectionTokens.MagnetPull
        var x = engine.centerX.value + (engine.layout.saveX - engine.centerX.value) * magnet
        var y = engine.centerY.value + (engine.layout.saveY - engine.centerY.value) * magnet
        x += (engine.layout.saveX - x) * engine.drop.value
        y += (engine.layout.saveY - y) * engine.drop.value
        val scale = (1f + SaveToCollectionTokens.LiftScale * engine.lift.value) *
            (1f - SaveToCollectionTokens.MagnetShrink * engine.pull.value) * (1f - 0.86f * engine.drop.value)
        val alpha = if (engine.drop.value <= 0.55f) 1f else 1f - (engine.drop.value - 0.55f) / 0.45f
        if (alpha <= 0f) return
        y -= SaveToCollectionTokens.LiftRise * engine.lift.value
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(engine.lean.value)
        canvas.scale(scale, scale)
        val local = RectF(-tile.width / 2f, -tile.height / 2f, tile.width / 2f, tile.height / 2f)
        val shadowAlpha = 0.22f * engine.lift.value * alpha
        if (shadowAlpha > 0f) {
            setFill(withAlpha(Color.BLACK, shadowAlpha * 0.35f))
            canvas.drawRoundRect(local.expand(18f).offsetBy(0f, 16f), 24f, 24f, fill)
            setFill(withAlpha(Color.BLACK, shadowAlpha))
            canvas.drawRoundRect(local.expand(4f).offsetBy(0f, 3f), 17f, 17f, fill)
        }
        drawPhoto(canvas, assets.photos[index], local.frame(), SaveToCollectionTokens.Radius, alpha)
        canvas.restore()
    }

    private fun drawSheet(
        canvas: Canvas,
        engine: SaveToCollectionEngine,
        before: Bitmap,
        fresh: Bitmap,
    ) {
        val progress = engine.sheet.value
        if (progress <= 0f) return
        setFill(withAlpha(Color.BLACK, 0.5f * progress.coerceIn(0f, 1f)))
        canvas.drawRect(0f, 0f, engine.layout.width, engine.layout.height, fill)
        val travel = engine.layout.height - engine.layout.sheetY
        val top = engine.layout.sheetY + (1f - progress) * travel
        val isFresh = engine.markedIndex >= 0 || engine.count > SaveToCollectionTokens.CountBefore
        val panel = if (isFresh) fresh else before
        image.alpha = 255
        image.shader = null
        canvas.drawBitmap(panel, null, RectF(0f, top, engine.layout.width, top + panel.height / panel.densityScale(engine.layout.width)), image)
        if (isFresh) {
            val entering = engine.arrive.value
            canvas.save()
            val width = (engine.layout.width - SaveToCollectionTokens.Edge * 2f - SaveToCollectionTokens.Gap) / 2f
            val height = (width * 1.2f).roundToInt().toFloat()
            val centerX = SaveToCollectionTokens.Edge + width / 2f
            val centerY = top + 97f + height / 2f
            canvas.translate(0f, -14f * (1f - entering))
            canvas.scale(0.94f + 0.06f * entering, 0.94f + 0.06f * entering, centerX, centerY)
            canvas.saveLayerAlpha(null, (255f * entering).roundToInt().coerceIn(0, 255))
            drawCollectionTile(canvas, SaveToCollectionTokens.DemoTile, 0, engine.layout, top + 97f, true)
            canvas.restore()
            canvas.restore()
        }
    }

    private fun drawFinger(canvas: Canvas, engine: SaveToCollectionEngine) {
        val alpha = engine.ghostOpacity.value
        if (alpha <= 0f) return
        val scale = 1f - 0.12f * engine.ghostPress.value
        canvas.save()
        canvas.scale(scale, scale, engine.ghostX, engine.ghostY)
        setFill(withAlpha(SaveToCollectionTokens.Finger, alpha))
        canvas.drawCircle(engine.ghostX, engine.ghostY, SaveToCollectionTokens.FingerSize / 2f, fill)
        setStroke(withAlpha(SaveToCollectionTokens.FingerEdge, alpha), 1f)
        canvas.drawCircle(engine.ghostX, engine.ghostY, SaveToCollectionTokens.FingerSize / 2f - 0.5f, stroke)
        canvas.restore()
    }

    private fun drawCollectionTile(
        canvas: Canvas,
        photoIndex: Int,
        slot: Int,
        layout: SaveToCollectionLayout,
        gridTop: Float,
        justSaved: Boolean,
    ) {
        val width = (layout.width - SaveToCollectionTokens.Edge * 2f - SaveToCollectionTokens.Gap) / 2f
        val height = (width * 1.2f).roundToInt().toFloat()
        val x = SaveToCollectionTokens.Edge + (slot % 2) * (width + SaveToCollectionTokens.Gap)
        val y = gridTop + (slot / 2) * (height + SaveToCollectionTokens.SheetCaption + SaveToCollectionTokens.Gap)
        drawPhoto(canvas, assets.photos[photoIndex], SaveToCollectionFrame(x, y, width, height), SaveToCollectionTokens.Radius, 1f)
        if (justSaved) {
            prepareText(11f, assets.semibold, SaveToCollectionTokens.Ink)
            val tagWidth = text.measureText("JUST SAVED") + 16f
            val tag = RectF(x + 8f, y + height - 30f, x + 8f + tagWidth, y + height - 8f)
            setFill(0x9E0A0A0B.toInt())
            canvas.drawRoundRect(tag, 11f, 11f, fill)
            setStroke(SaveToCollectionTokens.Rim, 0.5f)
            canvas.drawRoundRect(tag, 11f, 11f, stroke)
            drawTextCentered(canvas, "JUST SAVED", tag.centerX(), tag.centerY(), 11f, assets.semibold, SaveToCollectionTokens.Ink)
        }
        val photo = SaveToCollectionTokens.Photos[photoIndex]
        drawText(canvas, photo.title, x, y + height + 8f, 12f, assets.medium, SaveToCollectionTokens.Ink)
        drawText(canvas, photo.place, x, y + height + 24f, 12f, assets.regular, SaveToCollectionTokens.Muted)
    }

    private fun drawPhoto(
        canvas: Canvas,
        bitmap: Bitmap,
        frame: SaveToCollectionFrame,
        radius: Float,
        alpha: Float,
    ) {
        val destination = frame.rect()
        setFill(SaveToCollectionTokens.Tile)
        canvas.drawRoundRect(destination, radius, radius, fill)
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width() / destination.height()
        val source = if (sourceRatio > destinationRatio) {
            val wanted = bitmap.height * destinationRatio
            val left = (bitmap.width - wanted) / 2f
            Rect(left.roundToInt(), 0, (left + wanted).roundToInt(), bitmap.height)
        } else {
            val wanted = bitmap.width / destinationRatio
            val top = (bitmap.height - wanted) / 2f
            Rect(0, top.roundToInt(), bitmap.width, (top + wanted).roundToInt())
        }
        image.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        image.shader = null
        canvas.save()
        val clip = Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, source, destination, image)
        canvas.restore()
    }

    private fun drawGlass(canvas: Canvas, frame: RectF, radius: Float, thick: Boolean) {
        setFill(if (thick) SaveToCollectionTokens.SheetFill else SaveToCollectionTokens.GlassFill)
        canvas.drawRoundRect(frame, radius, radius, fill)
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f,
            frame.top,
            0f,
            frame.centerY(),
            intArrayOf(0x24FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(frame, radius, radius, fill)
        fill.shader = null
        setStroke(SaveToCollectionTokens.Rim, 0.5f)
        canvas.drawRoundRect(frame, radius, radius, stroke)
    }

    private fun drawGlyph(
        canvas: Canvas,
        glyph: SaveGlyph,
        centerX: Float,
        centerY: Float,
        size: Float,
        color: Int,
        filled: Boolean,
        weight: Float,
    ) {
        val path = glyph.path()
        canvas.save()
        canvas.translate(centerX - size / 2f, centerY - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        if (filled && glyph == SaveGlyph.Bookmark) {
            setFill(color)
            canvas.drawPath(path, fill)
        }
        setStroke(color, weight * 24f / size)
        canvas.drawPath(path, stroke)
        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        top: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
    ) {
        prepareText(size, typeface, color)
        canvas.drawText(value, x, top - text.fontMetrics.ascent, text)
    }

    private fun drawTextCenteredVertically(
        canvas: Canvas,
        value: String,
        x: Float,
        centerY: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
    ) {
        drawTextAtBaseline(canvas, value, x, centeredBaseline(centerY, size, typeface), size, typeface, color)
    }

    private fun drawTextCentered(
        canvas: Canvas,
        value: String,
        centerX: Float,
        centerY: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
    ) {
        prepareText(size, typeface, color)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(value, centerX, centeredBaseline(centerY, size, typeface), text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun drawTextAtBaseline(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
    ) {
        prepareText(size, typeface, color)
        canvas.drawText(value, x, baseline, text)
    }

    private fun centeredBaseline(centerY: Float, size: Float, typeface: Typeface): Float {
        prepareText(size, typeface, SaveToCollectionTokens.Ink)
        return centerY - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2f
    }

    private fun prepareText(size: Float, typeface: Typeface, color: Int) {
        text.textSize = size
        text.typeface = typeface
        text.color = color
        text.alpha = Color.alpha(color)
        text.textAlign = Paint.Align.LEFT
        text.shader = null
    }

    private fun setFill(color: Int) {
        fill.style = Paint.Style.FILL
        fill.color = color
        fill.alpha = Color.alpha(color)
        fill.shader = null
    }

    private fun setStroke(color: Int, width: Float) {
        stroke.style = Paint.Style.STROKE
        stroke.color = color
        stroke.alpha = Color.alpha(color)
        stroke.strokeWidth = width
        stroke.shader = null
    }

    private fun bitmap(width: Float, height: Float, density: Float): Bitmap {
        return Bitmap.createBitmap(
            max(1, (width * density).roundToInt()),
            max(1, (height * density).roundToInt()),
            Bitmap.Config.ARGB_8888,
        ).apply { this.density = Bitmap.DENSITY_NONE }
    }
}

private enum class SaveGlyph {
    Home,
    Search,
    Inbox,
    Bookmark,
    Close;

    fun path(): Path {
        return when (this) {
            Home -> Path().apply {
                moveTo(4f, 10.5f); lineTo(12f, 4f); lineTo(20f, 10.5f); lineTo(20f, 19f)
                quadTo(20f, 20f, 19f, 20f); lineTo(14.5f, 20f); lineTo(14.5f, 14.5f)
                lineTo(9.5f, 14.5f); lineTo(9.5f, 20f); lineTo(5f, 20f); quadTo(4f, 20f, 4f, 19f); close()
            }
            Search -> Path().apply {
                addCircle(10.5f, 10.5f, 6.5f, Path.Direction.CW)
                moveTo(15.2f, 15.2f); lineTo(20f, 20f)
            }
            Inbox -> Path().apply {
                moveTo(4f, 13.5f); lineTo(6.2f, 6f); quadTo(6.6f, 5f, 7.6f, 5f)
                lineTo(16.4f, 5f); quadTo(17.4f, 5f, 17.8f, 6f); lineTo(20f, 13.5f)
                lineTo(20f, 18f); quadTo(20f, 19f, 19f, 19f); lineTo(5f, 19f)
                quadTo(4f, 19f, 4f, 18f); close()
                moveTo(4f, 13.5f); lineTo(8.5f, 13.5f); lineTo(9.7f, 15.5f)
                lineTo(14.3f, 15.5f); lineTo(15.5f, 13.5f); lineTo(20f, 13.5f)
            }
            Bookmark -> Path().apply {
                moveTo(7f, 4.5f); lineTo(17f, 4.5f); quadTo(18f, 4.5f, 18f, 5.5f)
                lineTo(18f, 20f); lineTo(12f, 15.8f); lineTo(6f, 20f); lineTo(6f, 5.5f)
                quadTo(6f, 4.5f, 7f, 4.5f); close()
            }
            Close -> Path().apply {
                moveTo(7f, 7f); lineTo(17f, 17f); moveTo(17f, 7f); lineTo(7f, 17f)
            }
        }
    }
}

private fun SaveToCollectionFrame.rect(): RectF = RectF(x, y, x + width, y + height)

private fun RectF.frame(): SaveToCollectionFrame = SaveToCollectionFrame(left, top, width(), height())

private fun RectF.expand(amount: Float): RectF = RectF(left - amount, top - amount, right + amount, bottom + amount)

private fun RectF.offsetBy(x: Float, y: Float): RectF = RectF(left + x, top + y, right + x, bottom + y)

private fun Bitmap.densityScale(targetWidth: Float): Float = width / targetWidth

private fun withAlpha(color: Int, alpha: Float): Int {
    val combined = (Color.alpha(color) * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (combined shl 24)
}
