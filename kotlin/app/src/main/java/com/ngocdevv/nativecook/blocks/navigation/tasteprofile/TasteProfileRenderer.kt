package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class TasteProfileGeo(
    val unit: Float,
    val y0: Float,
    val fontScale: Float,
    val density: Float,
)

private fun Color.argb(alphaMultiplier: Double = 1.0): Int = AndroidColor.argb(
    (alpha * alphaMultiplier.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255),
    (red * 255f).roundToInt().coerceIn(0, 255),
    (green * 255f).roundToInt().coerceIn(0, 255),
    (blue * 255f).roundToInt().coerceIn(0, 255),
)

private fun path(data: String): Path = requireNotNull(PathParser.createPathFromPathData(data))

/** Cached native painter; all geometry below stays in the same 1206-ref coordinate system as RN. */
internal class TasteProfileRenderer(private val assets: TasteProfileAssets) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private var minimumStrokeRef = 1f

    private val rankGroup = TasteProfileRollGroup(
        layerOf = RANK.map { it.word },
        values = RANK_WORDS.indices.toList(),
        start = TasteProfileLabelMotion.delay,
        duration = TasteProfileLabelMotion.duration,
        stagger = 0.0,
        travel = TasteProfileLabelMotion.travel,
    )
    private val chevron = path("M29 4 L4 29 L29 54")
    private val send = path("M2.5 16 L39.5 2.5 L26 39.5 L18.5 22.5 Z M39.5 2.5 L18.5 22.5")
    private val share = path("M2.5 16 V33 A7.5 7.5 0 0 0 10 40.5 H33 A7.5 7.5 0 0 0 40.5 33 V16 M21.5 28 V3.5 M12 12.5 L21.5 3 L31 12.5")
    private val pin = path("M21 48.5 C11 38 3 30 3 20.5 A18 18 0 0 1 39 20.5 C39 30 31 38 21 48.5 Z")
    private val userBody = path("M4.5 37.5 V34 C4.5 28.5 9 27.5 18.5 27.5 C28 27.5 32.5 28.5 32.5 34 V37.5")
    private val thumb = path("M3 18 H11 V37 H3 Z M11 19 L17.5 18.5 L20.5 4.5 C21 2.8 23.5 3 24.5 4.5 C26.5 7.5 26 10.5 25.5 13.5 L25 16.5 H33.5 C36 16.5 37.5 18.5 37 21 L35 32.5 C34.5 35.5 32.5 37 30 37 H11")

    fun draw(
        canvas: Canvas,
        viewport: Size,
        geo: TasteProfileGeo,
        frame: TasteProfileFrame,
    ) {
        val change = frame.change
        val followProgress = frame.followProgress
        val followPress = frame.followPress
        minimumStrokeRef = geo.density / geo.unit
        drawPage(canvas, viewport, geo)
        canvas.save()
        canvas.translate(0f, geo.y0)
        canvas.scale(geo.unit, geo.unit)

        drawNavigation(canvas, followProgress, followPress)
        drawPortrait(canvas)
        drawIdentity(canvas, followProgress)
        drawBadges(canvas, change)
        drawLocation(canvas)
        drawChips(canvas, change, geo.unit)
        drawStats(canvas, frame.hold.tile)
        drawPhotos(canvas)

        canvas.restore()
    }

    private fun drawPage(canvas: Canvas, viewport: Size, geo: TasteProfileGeo) {
        val height = viewport.height.coerceAtLeast(1f)
        val colors = TasteProfileColors.pageStops.map { it.second.argb() }.toIntArray()
        var previous = 0f
        val positions = TasteProfileColors.pageStops.map { stop ->
            val position = ((geo.y0 + stop.first * geo.unit) / height).coerceIn(0f, 1f)
            max(previous, position).also { previous = it }
        }.toFloatArray()
        configureFill(Color.White)
        fill.shader = LinearGradient(0f, 0f, 0f, height, colors, positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, viewport.width, height, fill)
        fill.shader = null
    }

    private fun drawNavigation(canvas: Canvas, followProgress: Double, followPress: Double) {
        canvas.save()
        canvas.translate(TasteProfileBoxes.chevron.x, TasteProfileBoxes.chevron.y)
        configureStroke(Color.Black, TasteProfileBoxes.chevronStroke)
        canvas.drawPath(chevron, stroke)
        canvas.restore()

        val follow = TasteProfileBoxes.follow
        val pressScale = 1.0 - (1.0 - TasteProfilePress.scale) * followPress
        canvas.save()
        canvas.scale(pressScale.toFloat(), pressScale.toFloat(), follow.x + follow.w / 2f, follow.y + follow.h / 2f)
        val growth = TasteProfileFollowMotion.grow * followProgress
        drawNavPill(canvas, follow.x - growth.toFloat(), follow.y, follow.w + growth.toFloat(), follow.h)
        drawFollowContent(canvas, growth, followProgress)
        canvas.restore()

        drawNavPill(canvas, TasteProfileBoxes.send.x, TasteProfileBoxes.send.y, TasteProfileBoxes.send.w, TasteProfileBoxes.send.h)
        canvas.save()
        canvas.translate(TasteProfileBoxes.sendIcon.x, TasteProfileBoxes.sendIcon.y)
        configureStroke(Color.Black, 5f)
        canvas.drawPath(send, stroke)
        canvas.restore()

        drawNavPill(canvas, TasteProfileBoxes.share.x, TasteProfileBoxes.share.y, TasteProfileBoxes.share.w, TasteProfileBoxes.share.h)
        canvas.save()
        canvas.translate(TasteProfileBoxes.shareIcon.x, TasteProfileBoxes.shareIcon.y)
        configureStroke(Color.Black, 5f)
        canvas.drawPath(share, stroke)
        canvas.restore()
    }

    private fun drawNavPill(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val radius = TasteProfileRadius.nav
        // Compose has no cross-API equivalent of iOS view shadows, so two translucent layers keep
        // the same 5 pt radius / 3 pt offset envelope without relying on API-specific blur effects.
        configureFill(Color.Black, 0.035)
        canvas.drawRoundRect(x - 5f, y + 4f, x + width + 5f, y + height + 12f, radius + 5f, radius + 5f, fill)
        configureFill(Color.Black, 0.045)
        canvas.drawRoundRect(x - 2f, y + 5f, x + width + 2f, y + height + 8f, radius + 2f, radius + 2f, fill)
        configureFill(TasteProfileColors.chipFill)
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, fill)
        fill.shader = LinearGradient(0f, y, 0f, y + height, intArrayOf(AndroidColor.TRANSPARENT, 0x08000000, 0x0F000000), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = 255
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, fill)
        fill.shader = null
        configureStroke(TasteProfileColors.hairline, max(minimumStrokeRef, 1.6f))
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, stroke)
    }

    private fun drawFollowContent(canvas: Canvas, growth: Double, progress: Double) {
        val offset = -growth.toFloat()
        val icon = TasteProfileBoxes.followIcon
        canvas.save()
        canvas.translate(icon.x + offset, icon.y)
        configureStroke(Color.Black, 5f)
        canvas.drawCircle(17f, 8.8f, 6.2f, stroke)
        canvas.drawPath(userBody, stroke)
        val mark = Path().apply {
            moveTo(interpolate(34.7, 33.6, progress).toFloat(), interpolate(19.0, 19.6, progress).toFloat())
            lineTo(interpolate(47.0, 38.8, progress).toFloat(), interpolate(19.0, 25.4, progress).toFloat())
            moveTo(interpolate(40.9, 38.8, progress).toFloat(), interpolate(12.8, 25.4, progress).toFloat())
            lineTo(interpolate(40.9, 47.6, progress).toFloat(), interpolate(25.2, 12.6, progress).toFloat())
        }
        canvas.drawPath(mark, stroke)
        canvas.restore()

        drawText(canvas, TasteProfileText.follow.copy(x = TasteProfileText.follow.x + offset))
        val ingOpacity = clamp01(progress * 1.6 - 0.4)
        val ingX = FOLLOW_ING_X + offset + ((1.0 - progress) * -10.0).toFloat()
        drawText(canvas, TasteProfileText.follow.copy(value = "ing", x = ingX), ingOpacity)
    }

    private fun drawPortrait(canvas: Canvas) {
        val box = TasteProfileBoxes.capsule
        configureFill(Color.Black, 0.04)
        canvas.drawRoundRect(box.x - 5f, box.y + 5f, box.x + box.w + 5f, box.y + box.h + 14f, box.w / 2f + 5f, box.w / 2f + 5f, fill)
        configureFill(Color.Black, 0.06)
        canvas.drawRoundRect(box.x - 2f, box.y + 4f, box.x + box.w + 2f, box.y + box.h + 9f, box.w / 2f + 2f, box.w / 2f + 2f, fill)
        configureFill(Color.White)
        canvas.drawRoundRect(box.x, box.y, box.x + box.w, box.y + box.h, box.w / 2f, box.w / 2f, fill)
        val border = TasteProfileBoxes.capsuleBorder
        drawBitmapCover(canvas, assets.profile, RectF(box.x + border, box.y + border, box.x + box.w - border, box.y + box.h - border), box.w / 2f - border)
    }

    private fun drawIdentity(canvas: Canvas, followProgress: Double) {
        drawText(canvas, TasteProfileText.displayName)
        drawText(canvas, TasteProfileText.displayHandle)
        drawGlyphRun(canvas, TasteProfileGlyphRuns.n39)
        drawText(canvas, TasteProfileText.following)
        drawFollowers(canvas, followProgress)

        TasteProfileBoxes.avatars.forEachIndexed { index, avatar ->
            configureFill(Color.White)
            canvas.drawCircle(avatar.centerX, avatar.centerY, avatar.diameter / 2f, fill)
            val inner = avatar.diameter - 6f
            val target = RectF(avatar.centerX - inner / 2f, avatar.centerY - inner / 2f, avatar.centerX + inner / 2f, avatar.centerY + inner / 2f)
            drawBitmapCover(canvas, if (index == 0) assets.mutual1 else assets.mutual2, target, inner / 2f)
        }
        drawText(canvas, TasteProfileText.mutual2)
        drawText(canvas, TasteProfileText.mutual)
        drawText(canvas, TasteProfileText.friends)
    }

    private fun drawFollowers(canvas: Canvas, rawProgress: Double) {
        val progress = clamp01(rawProgress)
        val run = TasteProfileGlyphRuns.n60
        val six = run.glyphs[0]
        val zero = run.glyphs[1]
        val oneWidth = (17f * run.digitHeight / 36f).roundToInt().toFloat()
        val one = Triple('1', zero.second, oneWidth)
        val pad = 8f
        val travel = run.digitHeight + pad
        val windowLeft = zero.second - 4f
        val windowTop = run.baseline - run.digitHeight - pad
        val shift = zero.third - oneWidth

        drawGlyph(canvas, six, run, 1.0)
        canvas.save()
        canvas.clipRect(windowLeft, windowTop, windowLeft + zero.third + 8f, windowTop + run.digitHeight + 2f * pad)
        canvas.save()
        canvas.translate(0f, (-progress * travel).toFloat())
        drawGlyph(canvas, zero, run, 1.0 - progress)
        canvas.restore()
        canvas.save()
        canvas.translate(0f, ((1.0 - progress) * travel).toFloat())
        drawGlyph(canvas, one, run, progress)
        canvas.restore()
        canvas.restore()
        drawText(canvas, TasteProfileText.followers.copy(x = TasteProfileText.followers.x - shift * progress.toFloat()))
    }

    private fun drawBadges(canvas: Canvas, change: TasteProfileChange) {
        val coin = coinAt(change)
        val shadowScale = 1.0 - 0.25 * coin.lift
        val shadowWidth = TasteProfileSprites.coinShadowW * shadowScale.toFloat()
        val shadowRect = RectF(
            TasteProfileSprites.coinShadowCenterX - shadowWidth / 2f,
            TasteProfileSprites.coinShadowCenterY - TasteProfileSprites.coinShadowH / 2f,
            TasteProfileSprites.coinShadowCenterX + shadowWidth / 2f,
            TasteProfileSprites.coinShadowCenterY + TasteProfileSprites.coinShadowH / 2f,
        )
        fill.shader = RadialGradient(
            TasteProfileSprites.coinShadowCenterX,
            TasteProfileSprites.coinShadowCenterY,
            TasteProfileSprites.coinShadowW / 2f,
            intArrayOf(Color.Black.argb(TasteProfileCoinMotion.shadow * coin.lift), AndroidColor.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        fill.alpha = 255
        canvas.drawOval(shadowRect, fill)
        fill.shader = null

        val sprite = TasteProfileSprites.coin
        val scale = 1.0 + TasteProfileCoinMotion.grow * coin.lift
        val centerX = sprite.x + sprite.w / 2f
        val centerY = sprite.y + sprite.h / 2f - (TasteProfileCoinMotion.lift * coin.lift).toFloat()
        val width = sprite.w * scale.toFloat()
        val height = sprite.h * scale.toFloat()
        val destination = RectF(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
        val frame = coinFrame(coin.angle, coin.back)
        source.set(frame * sprite.w.toInt(), 0, (frame + 1) * sprite.w.toInt(), sprite.h.toInt())
        configureBitmap(1.0)
        canvas.drawBitmap(assets.coin, source, destination, bitmap)

        drawGem(canvas, 0.0)
        RANK_WORDS.forEachIndexed { layer, word ->
            val style = rollStyle(change, rankGroup, layer, 0)
            drawText(canvas, TasteProfileText.rankA.copy(value = word, baseline = TasteProfileText.rankA.baseline + style.y.toFloat()), style.opacity)
        }
        drawText(canvas, TasteProfileText.rankB)
        drawText(canvas, TasteProfileText.weekA)
        drawText(canvas, TasteProfileText.weekB)
    }

    private fun drawGem(canvas: Canvas, sheen: Double) {
        val sprite = TasteProfileSprites.gem
        val first = floor(sheen).toInt() % 12
        val second = (floor(sheen).toInt() + 1) % 12
        val fraction = sheen - floor(sheen)
        val destination = RectF(sprite.x, sprite.y, sprite.x + sprite.w, sprite.y + sprite.h)
        source.set(first * sprite.w.toInt(), 0, (first + 1) * sprite.w.toInt(), sprite.h.toInt())
        configureBitmap(1.0)
        canvas.drawBitmap(assets.gem, source, destination, bitmap)
        source.set(second * sprite.w.toInt(), 0, (second + 1) * sprite.w.toInt(), sprite.h.toInt())
        configureBitmap(fraction)
        canvas.drawBitmap(assets.gem, source, destination, bitmap)
        drawPixelDigit(canvas, '3', sprite.x + 61f, sprite.y + 63f)
        drawPixelDigit(canvas, '7', sprite.x + 110f, sprite.y + 63f)
    }

    private fun drawPixelDigit(canvas: Canvas, digit: Char, x: Float, y: Float) {
        val rows = if (digit == '3') {
            listOf("111111", "000011", "000011", "011111", "000011", "000011", "111111")
        } else {
            listOf("111111", "000011", "000110", "001100", "001100", "011000", "011000")
        }
        configureFill(Color.Black)
        rows.forEachIndexed { row, values ->
            values.forEachIndexed { column, value ->
                if (value == '1') canvas.drawRect(x + column * 6f, y + row * 6f, x + (column + 1) * 6f, y + (row + 1) * 6f, fill)
            }
        }
    }

    private fun drawLocation(canvas: Canvas) {
        val box = TasteProfileBoxes.pin
        canvas.save()
        canvas.translate(box.x, box.y)
        configureStroke(TasteProfileColors.pinIcon, 4.5f)
        canvas.drawPath(pin, stroke)
        canvas.drawCircle(21f, 20.5f, 6.5f, stroke)
        canvas.restore()
        drawText(canvas, TasteProfileText.location)
        drawText(canvas, TasteProfileText.bio)
    }

    private fun drawChips(canvas: Canvas, change: TasteProfileChange, unit: Float) {
        val baseEdges = pillAt(change)
        TasteProfileBoxes.chips.forEachIndexed { index, chip ->
            val coverage = coverage(baseEdges, index)
            val scale = chipScale(change, index)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat(), chip.x + chip.w / 2f, chip.y + chip.h / 2f)
            drawWhiteChip(canvas, chip, 1.0 - coverage)
            canvas.restore()
        }
        TasteProfileBoxes.chips.indices.forEach { index ->
            val scale = chipScale(change, index)
            val chip = TasteProfileBoxes.chips[index]
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat(), chip.x + chip.w / 2f, chip.y + chip.h / 2f)
            drawChipLabel(canvas, index, TasteProfileColors.chipText)
            canvas.restore()
        }

        val pressedEdges = pillEdges(change)
        val left = (pressedEdges.first * unit).roundToInt() / unit
        val right = (pressedEdges.second * unit).roundToInt() / unit
        canvas.save()
        val leftClip = Path().apply { addRoundRect(RectF(left, TasteProfileBoxes.chipY, left + REF_W, TasteProfileBoxes.chipY + TasteProfileBoxes.chipH), TasteProfileBoxes.chipH / 2f, TasteProfileBoxes.chipH / 2f, Path.Direction.CW) }
        val rightClip = Path().apply { addRoundRect(RectF(right - REF_W, TasteProfileBoxes.chipY, right, TasteProfileBoxes.chipY + TasteProfileBoxes.chipH), TasteProfileBoxes.chipH / 2f, TasteProfileBoxes.chipH / 2f, Path.Direction.CW) }
        canvas.clipPath(leftClip)
        canvas.clipPath(rightClip)
        configureFill(Color.Black)
        canvas.drawRect(0f, TasteProfileBoxes.chipY, REF_W, TasteProfileBoxes.chipY + TasteProfileBoxes.chipH, fill)
        fill.shader = LinearGradient(0f, TasteProfileBoxes.chipY, 0f, TasteProfileBoxes.chipY + 30f, intArrayOf(0x4DFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        fill.alpha = 255
        canvas.drawRect(0f, TasteProfileBoxes.chipY, REF_W, TasteProfileBoxes.chipY + 30f, fill)
        fill.shader = null
        TasteProfileBoxes.chips.indices.forEach { index ->
            val scale = chipScale(change, index)
            val chip = TasteProfileBoxes.chips[index]
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat(), chip.x + chip.w / 2f, chip.y + chip.h / 2f)
            drawChipLabel(canvas, index, Color.White)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawWhiteChip(canvas: Canvas, chip: TasteProfileBox, opacity: Double) {
        if (opacity <= 0.001) return
        val radius = chip.h / 2f
        configureFill(Color.Black, 0.025 * opacity)
        canvas.drawRoundRect(chip.x - 4f, chip.y + 4f, chip.x + chip.w + 4f, chip.y + chip.h + 11.5f, radius + 4f, radius + 4f, fill)
        configureFill(Color.Black, 0.035 * opacity)
        canvas.drawRoundRect(chip.x - 2f, chip.y + 4f, chip.x + chip.w + 2f, chip.y + chip.h + 7f, radius + 2f, radius + 2f, fill)
        configureFill(TasteProfileColors.chipFill, opacity)
        canvas.drawRoundRect(chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, radius, radius, fill)
        fill.shader = LinearGradient(0f, chip.y, 0f, chip.y + chip.h, intArrayOf(AndroidColor.TRANSPARENT, 0x06000000, 0x0F000000), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = (255.0 * opacity).roundToInt()
        canvas.drawRoundRect(chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, radius, radius, fill)
        fill.shader = null
    }

    private fun drawChipLabel(canvas: Canvas, index: Int, color: Color) {
        if (index == 0) {
            drawGlobe(canvas, color)
            drawGlyphRun(canvas, TasteProfileGlyphRuns.chipAll.copy(color = color))
            return
        }
        val spec = listOf(TasteProfileText.chipA, TasteProfileText.chipB, TasteProfileText.chipC)[index - 1]
        drawText(canvas, spec.copy(color = color))
    }

    private fun drawStats(canvas: Canvas, held: Int) {
        val tileXs = listOf(TasteProfileBoxes.tiles[0] - (TasteProfileBoxes.tileW + 19f)) + TasteProfileBoxes.tiles
        tileXs.forEachIndexed { tileIndex, x ->
            if (tileIndex - 1 == held) return@forEachIndexed
            configureFill(Color.Black, 0.018)
            canvas.drawRoundRect(x - 3f, TasteProfileBoxes.tileY + 3f, x + TasteProfileBoxes.tileW + 3f, TasteProfileBoxes.tileY + TasteProfileBoxes.tileH + 9f, TasteProfileRadius.tile + 3f, TasteProfileRadius.tile + 3f, fill)
            configureFill(TasteProfileColors.tileFill)
            canvas.drawRoundRect(x, TasteProfileBoxes.tileY, x + TasteProfileBoxes.tileW, TasteProfileBoxes.tileY + TasteProfileBoxes.tileH, TasteProfileRadius.tile, TasteProfileRadius.tile, fill)
            configureStroke(TasteProfileColors.tileHairline, max(minimumStrokeRef, 2.4f))
            canvas.drawRoundRect(x, TasteProfileBoxes.tileY, x + TasteProfileBoxes.tileW, TasteProfileBoxes.tileY + TasteProfileBoxes.tileH, TasteProfileRadius.tile, TasteProfileRadius.tile, stroke)
        }
        for (tile in 0 until 4) {
            if (tile == held) continue
            drawTileIcon(canvas, tile)
            drawText(canvas, TasteProfileTileLabels[tile])
        }
    }

    fun drawTileIcon(canvas: Canvas, tile: Int) {
        val box = TasteProfileBoxes.tileIcons[tile]
        when (tile) {
            0 -> drawStar(canvas, box)
            2 -> drawMeh(canvas, box)
            else -> drawThumb(canvas, box, tile == 3)
        }
    }

    private fun drawPhotos(canvas: Canvas) {
        drawText(canvas, TasteProfileText.viewAll)
        drawText(canvas, TasteProfileText.photos)

        drawBitmapCover(canvas, assets.big1, TasteProfileBoxes.photoL.rect(), TasteProfileRadius.photo)
        drawBitmapCover(canvas, assets.big2, TasteProfileBoxes.photoR.rect(), TasteProfileRadius.photo)
        val peek = TasteProfileBoxes.peek
        canvas.save()
        val peekPath = Path().apply { addRoundRect(peek.rect(), TasteProfileRadius.photo, TasteProfileRadius.photo, Path.Direction.CW) }
        canvas.clipPath(peekPath)
        fill.shader = LinearGradient(peek.x, peek.y, peek.x, peek.y + peek.h, intArrayOf(0xFF1A1E18.toInt(), 0xFF10110C.toInt(), 0xFF090B09.toInt(), 0xFF14130F.toInt(), 0xFF4B4A46.toInt(), 0xFF1A1C1C.toInt()), null, Shader.TileMode.CLAMP)
        fill.alpha = 255
        canvas.drawRect(peek.x, peek.y, peek.x + peek.w, peek.y + peek.h, fill)
        fill.shader = null
        canvas.restore()
    }

    private fun drawGlobe(canvas: Canvas, color: Color) {
        val box = TasteProfileBoxes.globe
        val center = box.w / 2f
        val radius = center - 2.5f
        canvas.save()
        canvas.translate(box.x, box.y)
        configureStroke(color, 5f)
        canvas.drawCircle(center, box.h / 2f, radius, stroke)
        canvas.drawOval(center - radius * 0.44f, box.h / 2f - radius, center + radius * 0.44f, box.h / 2f + radius, stroke)
        canvas.drawLine(center - radius, box.h / 2f, center + radius, box.h / 2f, stroke)
        canvas.drawLine(center - radius * 0.87f, box.h / 2f - radius * 0.5f, center + radius * 0.87f, box.h / 2f - radius * 0.5f, stroke)
        canvas.drawLine(center - radius * 0.87f, box.h / 2f + radius * 0.5f, center + radius * 0.87f, box.h / 2f + radius * 0.5f, stroke)
        canvas.restore()
    }

    private fun drawStar(canvas: Canvas, box: TasteProfileBox) {
        val star = Path()
        repeat(10) { index ->
            val radius = if (index % 2 == 0) 18.5 else 9.3
            val angle = (-90.0 + index * 36.0) * PI / 180.0
            val x = box.w / 2f + (radius * cos(angle)).toFloat()
            val y = 21.25f + (radius * sin(angle)).toFloat()
            if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        canvas.save()
        canvas.translate(box.x, box.y)
        configureStroke(Color.Black, 5.5f)
        canvas.drawPath(star, stroke)
        canvas.restore()
    }

    private fun drawThumb(canvas: Canvas, box: TasteProfileBox, down: Boolean) {
        canvas.save()
        canvas.translate(box.x, box.y)
        if (down) {
            canvas.translate(0f, box.h)
            canvas.scale(1f, -1f)
        }
        configureStroke(Color.Black, 5.5f)
        canvas.drawPath(thumb, stroke)
        canvas.restore()
    }

    private fun drawMeh(canvas: Canvas, box: TasteProfileBox) {
        val center = box.w / 2f
        canvas.save()
        canvas.translate(box.x, box.y)
        configureStroke(Color.Black, 5f)
        canvas.drawCircle(center, box.h / 2f, center - 2.5f, stroke)
        canvas.drawLine(center - 6.5f, box.h / 2f + 7.5f, center + 6.5f, box.h / 2f + 7.5f, stroke)
        configureFill(Color.Black)
        canvas.drawCircle(center - 6.5f, box.h / 2f - 4f, 2.6f, fill)
        canvas.drawCircle(center + 6.5f, box.h / 2f - 4f, 2.6f, fill)
        canvas.restore()
    }

    fun drawText(canvas: Canvas, spec: TasteProfileTextSpec, opacity: Double = 1.0) {
        if (opacity <= 0.001) return
        text.shader = null
        text.alpha = 255
        text.color = spec.color.argb(opacity)
        text.textSize = spec.size * 3f
        text.typeface = assets.typeface(spec.font)
        text.letterSpacing = if (spec.size > 0f) spec.letterSpacing / spec.size else 0f
        canvas.drawText(spec.value, spec.x, spec.baseline, text)
    }

    private fun drawGlyphRun(canvas: Canvas, run: TasteProfileGlyphRun, opacity: Double = 1.0) {
        run.glyphs.forEach { glyph -> drawGlyph(canvas, glyph, run, opacity) }
    }

    fun drawGlyph(canvas: Canvas, glyph: Triple<Char, Float, Float>, run: TasteProfileGlyphRun, opacity: Double) {
        if (opacity <= 0.001) return
        if (glyph.first == '1') {
            val left = glyph.second
            val top = run.baseline - run.digitHeight
            val one = Path().apply {
                moveTo(left + glyph.third * 16.5f / 17f, top)
                lineTo(left + glyph.third * 8f / 17f, top)
                lineTo(left, top + run.digitHeight * 4.5f / 36f)
                lineTo(left, top + run.digitHeight * 13f / 36f)
                lineTo(left + glyph.third * 8.5f / 17f, top + run.digitHeight * 13f / 36f)
                lineTo(left + glyph.third * 8.5f / 17f, top + run.digitHeight)
                lineTo(left + glyph.third * 16.5f / 17f, top + run.digitHeight)
                close()
            }
            configureFill(run.color, opacity)
            canvas.drawPath(one, fill)
            return
        }
        val metrics = requireNotNull(TasteProfileGlyphEm[glyph.first])
        val fontSize = run.size * 3f
        val horizontalScale = glyph.third / (metrics.second * fontSize)
        text.shader = null
        text.alpha = 255
        text.color = run.color.argb(opacity)
        text.textSize = fontSize
        text.typeface = assets.typeface(run.font)
        text.letterSpacing = 0f
        canvas.save()
        canvas.translate(glyph.second, run.baseline)
        canvas.scale(horizontalScale, 1f)
        canvas.drawText(glyph.first.toString(), -metrics.first * fontSize, 0f, text)
        canvas.restore()
    }

    fun drawBitmapCover(canvas: Canvas, image: Bitmap, destination: RectF, radius: Float) {
        val destinationRatio = destination.width() / destination.height()
        val sourceRatio = image.width.toFloat() / image.height.toFloat()
        if (sourceRatio > destinationRatio) {
            val width = (image.height * destinationRatio).roundToInt()
            val left = (image.width - width) / 2
            source.set(left, 0, left + width, image.height)
        } else {
            val height = (image.width / destinationRatio).roundToInt()
            val top = (image.height - height) / 2
            source.set(0, top, image.width, top + height)
        }
        canvas.save()
        if (radius > 0f) {
            val clip = Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }
            canvas.clipPath(clip)
        }
        configureBitmap(1.0)
        canvas.drawBitmap(image, source, destination, bitmap)
        canvas.restore()
    }

    private fun coverage(edges: Pair<Double, Double>, index: Int): Double {
        val target = chipEdges(index)
        return clamp01((min(edges.second, target.second) - max(edges.first, target.first)) / (target.second - target.first))
    }

    private fun chipScale(change: TasteProfileChange, index: Int): Double =
        if (change.pressedChip == index) 1.0 - (1.0 - TasteProfilePress.scale) * change.press else 1.0

    private fun pillEdges(change: TasteProfileChange): Pair<Double, Double> {
        val edges = pillAt(change)
        if (change.pressedChip != change.to || change.press == 0.0) return edges
        val inset = (edges.second - edges.first) * (1.0 - TasteProfilePress.scale) * change.press / 2.0
        return edges.first + inset to edges.second - inset
    }

    fun configureFill(color: Color, opacity: Double = 1.0) {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.alpha = 255
        fill.color = color.argb(opacity)
    }

    fun configureStroke(color: Color, width: Float, opacity: Double = 1.0) {
        stroke.style = Paint.Style.STROKE
        stroke.shader = null
        stroke.alpha = 255
        stroke.color = color.argb(opacity)
        stroke.strokeWidth = width
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
    }

    private fun configureBitmap(opacity: Double) {
        bitmap.shader = null
        bitmap.alpha = (255.0 * opacity.coerceIn(0.0, 1.0)).roundToInt()
        bitmap.isFilterBitmap = true
    }
}

private fun TasteProfileBox.rect(): RectF = RectF(x, y, x + w, y + h)

internal fun DrawScope.drawTasteProfile(
    renderer: TasteProfileRenderer,
    board: TasteProfileBoardPainter,
    geo: TasteProfileGeo,
    frame: TasteProfileFrame,
) {
    drawContext.canvas.nativeCanvas.let { canvas ->
        renderer.draw(canvas, size, geo, frame)
        board.draw(canvas, geo, frame)
    }
}
