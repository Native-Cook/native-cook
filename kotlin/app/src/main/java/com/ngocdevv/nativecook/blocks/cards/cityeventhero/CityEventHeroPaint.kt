package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

internal data class CityEventHeroGeometry(
    val unit: Float,
    val width: Float,
    val height: Float,
    val topInset: Float,
) {
    fun x(value: Float): Float = value * unit

    fun y(value: Float): Float = if (value < CityEventHeroTopGroupMax) {
        topInset + (value - CityEventHeroRefInset) * unit
    } else {
        height - (CityEventHeroRefHeight - value) * unit
    }

    fun box(box: CityEventHeroBox): Rect = Rect(x(box.x), y(box.y), x(box.x + box.w), y(box.y + box.h))
}

internal fun DrawScope.drawCityEventHero(
    assets: CityEventHeroAssets,
    geometry: CityEventHeroGeometry,
    frame: CityEventHeroFrame,
    reduced: Boolean,
    scenePainter: CityEventHeroApi33ScenePainter?,
    tiltX: Float,
    tiltY: Float,
) {
    val uiLight = CityEventHeroUiLight(frame)
    drawCityEventHeroBackdrop(assets, geometry, frame, scenePainter, tiltX, tiltY)
    drawCityEventHeroTopBar(geometry, uiLight)
    drawCityEventHeroGuests(assets, geometry, frame, reduced)
    drawCityEventHeroCopy(assets, geometry, frame, reduced, uiLight)
    drawCityEventHeroPresented(assets, geometry, uiLight)
    drawCityEventHeroActions(geometry, frame, reduced, uiLight)
    drawCityEventHeroTabBar(geometry)
}

private fun DrawScope.drawCityEventHeroBackdrop(
    assets: CityEventHeroAssets,
    geo: CityEventHeroGeometry,
    frame: CityEventHeroFrame,
    scenePainter: CityEventHeroApi33ScenePainter?,
    tiltX: Float,
    tiltY: Float,
) {
    val card = geo.box(CityEventHeroCard)
    fun backdropY(y: Float): Float = card.top + (y - CityEventHeroCard.y) * geo.unit
    val cardPath = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(card, geo.x(75f), geo.x(75f))) }
    clipPath(cardPath) {
        val cardWidth = card.width
        val cardHeight = card.height
        val cover = max(cardWidth / assets.city.width, cardHeight / assets.city.height)
        val photoWidth = assets.city.width * cover
        val photoHeight = assets.city.height * cover
        val left = card.left + (cardWidth - photoWidth) / 2f
        val top = card.top - (photoHeight - cardHeight) * .57f
        val photoRect = CityEventHeroPhotoRect(left, top, photoWidth, photoHeight)
        drawImage(
            image = assets.city,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(assets.city.width, assets.city.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(photoWidth.toInt(), photoHeight.toInt()),
        )
        val sceneOpacity = CityEventHeroSceneOpacity(frame, tiltX, tiltY)
        if (scenePainter != null && sceneOpacity > 0f) {
            drawIntoCanvas { canvas ->
                scenePainter.draw(canvas.nativeCanvas, photoRect, density, geo.unit, frame, tiltX, tiltY, mode = 0, alpha = sceneOpacity)
            }
        } else {
            drawCityEventHeroSceneFallback(assets, photoRect, frame, tiltX, tiltY, sceneOpacity)
        }
        drawCityEventHeroHourTitle(assets, geo, photoRect, frame, tiltX, tiltY)
        if (frame.title > .001f) {
            if (scenePainter != null && sceneOpacity > 0f) {
                drawIntoCanvas { canvas ->
                    scenePainter.draw(canvas.nativeCanvas, photoRect, density, geo.unit, frame, tiltX, tiltY, mode = 1, alpha = sceneOpacity)
                }
            } else {
                drawCityEventHeroFallbackTowerPass(assets, photoRect, frame, tiltX, tiltY, sceneOpacity)
            }
        }
        drawCityEventHeroFireworks(assets.sparkGlow, photoRect, geo.unit, frame, tiltX, tiltY)
        // RN draws haze over the photo but below both scrims and the additive backdrop glows.
        if (scenePainter != null) {
            drawIntoCanvas { canvas -> scenePainter.drawMist(canvas.nativeCanvas, geo, frame) }
        } else {
            drawCityEventHeroHaze(geo, frame)
        }
        drawRect(
            brush = Brush.linearGradient(listOf(CityEventHeroTheme.colors.scrimTop, CityEventHeroTheme.colors.scrimTopClear), Offset(0f, card.top), Offset(0f, geo.y(880f))),
            topLeft = Offset(0f, card.top),
            size = Size(geo.width, geo.y(880f) - card.top),
        )
        val bottom = backdropY(2376f)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0x001A282A),
                    310f / 926f to Color(0xFF1A282A),
                    410f / 926f to Color(0xFF151F20),
                    510f / 926f to Color(0xFF111718),
                    610f / 926f to Color(0xFF030F0F),
                    710f / 926f to Color(0xFF00090C),
                    810f / 926f to Color(0xFF000607),
                    880f / 926f to Color(0xFF030705),
                    922f / 926f to Color(0xFF060808),
                    1f to Color(0xFF060808),
                ),
                startY = backdropY(1450f),
                endY = bottom,
            ),
            topLeft = Offset(0f, backdropY(1450f)),
            size = Size(geo.width, bottom - backdropY(1450f)),
        )
        drawCityEventHeroGlow(geo.x(407f), backdropY(2031f), geo.x(239f), geo.x(205f), Color(0xFF381101))
        drawCityEventHeroGlow(geo.x(1104f), backdropY(2084f), geo.x(269f), geo.x(600f), Color(0xFF200900))
    }
    drawCityEventHeroText(assets.wordmark, CityEventHeroTheme.copy.wordmark, geo.x(63f), geo.y(306f), geo.x(75.8f), CityEventHeroWordmark, outline = CityEventHeroOutline, stroke = geo.x(23f))
}

private fun DrawScope.drawCityEventHeroGlow(cx: Float, cy: Float, sx: Float, sy: Float, color: Color) {
    val weights = floatArrayOf(1f, .882f, .607f, .325f, .135f, .044f, 0f)
    withTransform({
        translate(cx, cy)
        scale(1f, sy / sx)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                *weights.mapIndexed { index, weight -> index / 6f to color.copy(alpha = weight) }.toTypedArray(),
                center = Offset.Zero,
                radius = 3f * sx,
            ),
            radius = 3f * sx,
            center = Offset.Zero,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawCityEventHeroHourTitle(
    assets: CityEventHeroAssets,
    geo: CityEventHeroGeometry,
    rect: CityEventHeroPhotoRect,
    frame: CityEventHeroFrame,
    tiltX: Float,
    tiltY: Float,
) {
    if (frame.title <= .001f) return
    val fontScale = geo.width / CityEventHeroTheme.REF_PT
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = assets.headline
        textSize = CityEventHeroTitle.size * fontScale
        color = CityEventHeroTheme.scene.title.copy(alpha = CityEventHeroTheme.scene.title.alpha * frame.title).toArgb()
    }
    val suffixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = assets.headline
        textSize = CityEventHeroTitle.suffix * fontScale
        color = CityEventHeroTheme.scene.titleSoft.copy(alpha = CityEventHeroTheme.scene.titleSoft.alpha * frame.title).toArgb()
    }
    val glyphWidths = FloatArray(10)
    titlePaint.getTextWidths("0123456789", glyphWidths)
    val digitWidth = glyphWidths.maxOrNull() ?: titlePaint.measureText("0")
    val colonWidth = titlePaint.measureText(":")
    val clock = CityEventHeroClock()
    CityEventHeroClockAt(frame.day, frame.night, clock)
    val cover = rect.width / 850f
    val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
    val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
    val center = rect.left + CityEventHeroLed.mid * cover + cameraX * CityEventHeroSpace.title * cover
    val baseline = geo.y(CityEventHeroTitle.base) + cameraY * CityEventHeroSpace.title * cover
    val separator = CityEventHeroTitle.sep * fontScale
    val gap = 6f * fontScale
    val suffix = if (clock.pm) "PM" else "AM"

    fun digitOffset(value: Int): Float = (digitWidth - glyphWidths[value]) / 2f
    drawIntoCanvas { canvas ->
        if (clock.hour >= 10) {
            canvas.nativeCanvas.drawText("1", center - separator - 2f * digitWidth + digitOffset(1), baseline, titlePaint)
        }
        val hourOne = clock.hour % 10
        canvas.nativeCanvas.drawText(hourOne.toString(), center - separator - digitWidth + digitOffset(hourOne), baseline, titlePaint)
        canvas.nativeCanvas.drawText(":", center - colonWidth / 2f, baseline, titlePaint)
        val minuteTen = clock.minute / 10
        val minuteOne = clock.minute % 10
        canvas.nativeCanvas.drawText(minuteTen.toString(), center + separator + digitOffset(minuteTen), baseline, titlePaint)
        canvas.nativeCanvas.drawText(minuteOne.toString(), center + separator + digitWidth + digitOffset(minuteOne), baseline, titlePaint)
        canvas.nativeCanvas.drawText(suffix, center + separator + 2f * digitWidth + gap, baseline, suffixPaint)
    }
}

/** API 26-compatible deterministic fallback for RN's noise shader. */
private fun DrawScope.drawCityEventHeroHaze(geo: CityEventHeroGeometry, frame: CityEventHeroFrame) {
    val amount = .15f * frame.mist * (1f - (frame.night / CityEventHeroNight.grade).coerceIn(0f, 1f))
    if (amount <= 0f) return
    repeat(12) { index ->
        val phase = index * 1.91f + frame.drift * .22f
        val x = geo.x(50f + (index * 109f + sin(phase) * 95f) % 1180f)
        val y = geo.y(385f + (index % 4) * 115f + cos(phase * .7f) * 30f)
        drawOval(
            color = Color(.8f, .9f, .93f).copy(alpha = amount * (.035f + (index % 3) * .015f)),
            topLeft = Offset(x - geo.x(160f), y - geo.x(58f)),
            size = Size(geo.x(320f), geo.x(116f)),
        )
    }
}

private fun DrawScope.drawCityEventHeroTopBar(geo: CityEventHeroGeometry, light: CityEventHeroUiLight) {
    listOf(CityEventHeroGift, CityEventHeroDatePill, CityEventHeroSearch).forEach { box -> drawCityEventHeroGlass(geo.box(box), CityEventHeroGlassTop, light = light) }
    drawCityEventHeroIcon("gift", geo.x(627f), geo.y(258f), geo.x(39f), CityEventHeroWhite)
    drawCityEventHeroIcon("filter", geo.x(768f), geo.y(262f), geo.x(39f), CityEventHeroWhite)
    drawCityEventHeroIcon("search", geo.x(1088f), geo.y(261f), geo.x(37f), CityEventHeroWhite)
    drawCityEventHeroText(null, "By date", geo.x(832f), geo.y(295f), 14f * geo.width / 402f, CityEventHeroWhite, Typeface.create("sans-serif-medium", Typeface.NORMAL), letterSpacing = .15f * geo.width / 402f)
}

private fun DrawScope.drawCityEventHeroGuests(assets: CityEventHeroAssets, geo: CityEventHeroGeometry, frame: CityEventHeroFrame, reduced: Boolean) {
    val uiLight = CityEventHeroUiLight(frame)
    CityEventHeroGuests.forEach { guest ->
        val slot = CityEventHeroSlots.indexOf(guest)
        val fan = frame.fan[slot]
        val spot = frame.spot[slot]
        val target = CityEventHeroFannedSlots[slot]
        val centerX = geo.x(guest.cx + (target.cx - guest.cx) * fan)
        val centerY = geo.y(guest.cy + (target.cy - guest.cy) * fan) - geo.x(14f * CityEventHeroFlight(fan) + 18f * spot)
        val size = geo.x(guest.d)
        val rotation = target.rotation * fan
        val scale = 1f + .03f * CityEventHeroFlight(fan) + .07f * spot
        withTransform({
            translate(centerX, centerY)
            rotate(rotation)
            scale(scale, scale)
            translate(-size / 2f, -size / 2f)
        }) {
            val avatar = assets.guests[guest.img]
            val inset = geo.x(CityEventHeroTheme.space.ring)
            val mask = Path().apply { addOval(Rect(inset, inset, size - inset, size - inset)) }
            clipPath(mask) {
                drawImage(avatar, IntOffset.Zero, IntSize(avatar.width, avatar.height), IntOffset(inset.toInt(), inset.toInt()), IntSize((size - 2f * inset).toInt(), (size - 2f * inset).toInt()), alpha = frame.guestOpacity)
            }
            drawCircle(CityEventHeroWhite.copy(alpha = frame.guestOpacity), (size - inset) / 2f, Offset(size / 2f, size / 2f), style = Stroke(geo.x(6f)))
            if (uiLight.amount > 0f) {
                drawCircle(uiLight.color.copy(alpha = .85f * uiLight.amount * frame.guestOpacity), (size - inset) / 2f, Offset(size / 2f, size / 2f), style = Stroke(geo.x(6f)))
            }
        }
    }
}

private fun DrawScope.drawCityEventHeroCopy(
    assets: CityEventHeroAssets,
    geo: CityEventHeroGeometry,
    frame: CityEventHeroFrame,
    reduced: Boolean,
    light: CityEventHeroUiLight,
) {
    val scale = geo.width / 402f
    val from = "${CityEventHeroNames[frame.nameFrom]} and 17 more"
    val to = "${CityEventHeroNames[frame.nameTo]} and 17 more"
    val rise = if (reduced) 0f else geo.x(26f)
    val p = frame.nameProgress
    if (frame.nameFrom == frame.nameTo) {
        drawCityEventHeroCentered(null, from, geo.x(609f), geo.y(1692f), 16f * scale, CityEventHeroSoftInk, letterSpacing = .2f * scale)
    } else {
        drawCityEventHeroCentered(null, from, geo.x(609f), geo.y(1692f) - rise * p, 16f * scale, CityEventHeroSoftInk.copy(alpha = CityEventHeroSoftInk.alpha * (1f - p)), letterSpacing = .2f * scale)
        drawCityEventHeroCentered(null, to, geo.x(609f), geo.y(1692f) + rise * (1f - p), 16f * scale, CityEventHeroSoftInk.copy(alpha = CityEventHeroSoftInk.alpha * p), letterSpacing = .2f * scale)
    }
    val headlineGlow = CityEventHeroFireLight(frame.fire) * (frame.night / CityEventHeroNight.fireFade).coerceIn(0f, 1f)
    if (headlineGlow > 0f) {
        drawCityEventHeroCentered(assets.headline, CityEventHeroTheme.copy.headline, geo.x(605f), geo.y(1803f), 34.33f * scale, CityEventHeroWhite.copy(alpha = headlineGlow), shadow = CityEventHeroTheme.night.headlineGlow.copy(alpha = .95f * headlineGlow), shadowRadius = 18f * scale)
    }
    drawCityEventHeroCentered(assets.headline, CityEventHeroTheme.copy.headline, geo.x(605f), geo.y(1803f), 34.33f * scale, CityEventHeroWhite)
    drawCityEventHeroCentered(null, CityEventHeroTheme.copy.date, geo.x(609f), geo.y(1895f), 15.85f * scale, CityEventHeroDateInk, letterSpacing = .24f * scale)
}

private fun DrawScope.drawCityEventHeroPresented(assets: CityEventHeroAssets, geo: CityEventHeroGeometry, light: CityEventHeroUiLight) {
    drawCityEventHeroGlass(geo.box(CityEventHeroPresented), CityEventHeroGlassBottom, geo.x(48f), light)
    val thumb = geo.box(CityEventHeroThumb)
    val thumbPath = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(thumb, geo.x(36f), geo.x(36f))) }
    clipPath(thumbPath) {
        val crop = Rect(180f, 300f, 680f, 800f)
        drawImage(assets.city, IntOffset(crop.left.toInt(), crop.top.toInt()), IntSize(crop.width.toInt(), crop.height.toInt()), IntOffset(thumb.left.toInt(), thumb.top.toInt()), IntSize(thumb.width.toInt(), thumb.height.toInt()))
    }
    val scale = geo.width / 402f
    drawCityEventHeroText(null, "Presented by", geo.x(270f), geo.y(2053f), 13.85f * scale, CityEventHeroLabelInk, letterSpacing = .2f * scale)
    drawCityEventHeroText(null, "Dubai in Jan 2026", geo.x(271f), geo.y(2114f), 15.85f * scale, CityEventHeroWhite, Typeface.create("sans-serif-medium", Typeface.NORMAL), letterSpacing = .2f * scale)
    drawCityEventHeroIcon("chevCard", geo.x(1070f), geo.y(2050f), geo.x(20f), CityEventHeroChev)
}

private fun DrawScope.drawCityEventHeroActions(geo: CityEventHeroGeometry, frame: CityEventHeroFrame, reduced: Boolean, light: CityEventHeroUiLight) {
    drawCityEventHeroGlass(geo.box(CityEventHeroLike), CityEventHeroGlassBottom, light = light)
    drawCityEventHeroGlass(geo.box(CityEventHeroShare), CityEventHeroGlassBottom, light = light)
    drawCityEventHeroGlass(geo.box(CityEventHeroMore), CityEventHeroGlassBottom, light = light)
    val heartCenter = Offset(geo.x(116.5f), geo.y(2278f))
    if (!reduced && frame.ring > 0f && frame.ring < 1f) {
        val radius = geo.x(19.5f * (.9f + frame.ring))
        drawCircle(cityEventHeroGammaMix(CityEventHeroWhite, CityEventHeroTheme.night.rim, frame.night).copy(alpha = .9f * (1f - frame.ring)), radius, heartCenter, style = Stroke(geo.x(3.5f * (1f - frame.ring))))
    }
    val heartScale = frame.pop
    withTransform({
        translate(heartCenter.x, heartCenter.y)
        scale(heartScale, heartScale)
        translate(-heartCenter.x, -heartCenter.y)
    }) {
        drawCityEventHeroHeart(heartCenter, geo.unit, CityEventHeroWhite, fill = false)
        if (frame.like > .001f) drawCityEventHeroHeart(heartCenter, geo.unit, CityEventHeroWhite.copy(alpha = frame.like), fill = true)
    }
    drawCityEventHeroIcon("share", geo.x(285f), geo.y(2256f), geo.x(36f), CityEventHeroWhite)
    drawCityEventHeroIcon("more", geo.x(409f), geo.y(2274f), geo.x(33f), CityEventHeroWhite)
    val countP = frame.count
    val countRise = if (reduced) 0f else geo.x(22f)
    val countClip = Rect(geo.x(143f), geo.y(2246f), geo.x(209f), geo.y(2309f))
    clipPath(Path().apply { addRect(countClip) }) {
        drawCityEventHeroText(null, "1", geo.x(160f), geo.y(2292f) - countRise * countP, 14f * geo.width / 402f, CityEventHeroWhite.copy(alpha = 1f - countP), Typeface.create("sans-serif-medium", Typeface.NORMAL), letterSpacing = .15f * geo.width / 402f)
        drawCityEventHeroText(null, "2", geo.x(160f), geo.y(2292f) + countRise * (1f - countP), 14f * geo.width / 402f, CityEventHeroWhite.copy(alpha = countP), Typeface.create("sans-serif-medium", Typeface.NORMAL), letterSpacing = .15f * geo.width / 402f)
    }
    val view = geo.box(CityEventHeroView)
    val pressScale = if (reduced) 1f else 1f - .03f * frame.press
    val viewOpacity = if (reduced) 1f - .3f * frame.press else 1f
    withTransform({
        translate(view.center.x, view.center.y)
        scale(pressScale, pressScale)
        translate(-view.center.x, -view.center.y)
    }) {
        drawCityEventHeroGlass(view, CityEventHeroGlassBottom.copy(alpha = CityEventHeroGlassBottom.alpha * viewOpacity), light = light)
        if (!reduced && frame.sweep > 0f && frame.sweep < 1f) {
            val band = geo.x(110f)
            val lead = 1.05f * band
            val start = view.left - lead + (view.width + 1.65f * band) * frame.sweep
            clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(view, view.height / 2f, view.height / 2f)) }) {
                drawRect(Brush.linearGradient(listOf(CityEventHeroTheme.colors.sweepClear, CityEventHeroTheme.colors.sweepSoft, CityEventHeroTheme.colors.sweep, CityEventHeroTheme.colors.sweepSoft, CityEventHeroTheme.colors.sweepClear), Offset(start, view.top), Offset(start + band, view.top + band * tan(20.0 * PI / 180.0).toFloat())), Offset(view.left, view.top), Size(view.width, view.height))
            }
        }
        drawCityEventHeroText(null, "View event", geo.x(835f), geo.y(2292f), 13.85f * geo.width / 402f, CityEventHeroWhite.copy(alpha = viewOpacity), Typeface.create("sans-serif-medium", Typeface.NORMAL), letterSpacing = .2f * geo.width / 402f)
        drawCityEventHeroIcon("chevView", geo.x(1090f) + geo.x(10f) * frame.chevron, geo.y(2263f), geo.x(15f), CityEventHeroViewChev.copy(alpha = viewOpacity))
    }
}

private fun DrawScope.drawCityEventHeroTabBar(geo: CityEventHeroGeometry) {
    drawCityEventHeroIcon("home", geo.x(127f), geo.y(2426f), geo.x(65f), CityEventHeroWhite)
    drawCityEventHeroIcon("chat", geo.x(349f), geo.y(2429f), geo.x(65f), CityEventHeroWhite)
    drawCityEventHeroIcon("plus", geo.x(568f), geo.y(2427f), geo.x(69f), CityEventHeroWhite)
    drawCityEventHeroIcon("bell", geo.x(792f), geo.y(2427f), geo.x(65f), CityEventHeroWhite)
    drawCityEventHeroIcon("person", geo.x(1014f), geo.y(2427f), geo.x(63f), CityEventHeroWhite)
}

private fun DrawScope.drawCityEventHeroGlass(
    rect: Rect,
    color: Color,
    radius: Float = rect.height / 2f,
    light: CityEventHeroUiLight? = null,
) {
    drawRoundRect(color, Offset(rect.left, rect.top), Size(rect.width, rect.height), CornerRadius(radius))
    if (light != null && light.amount > 0f) {
        drawRoundRect(
            light.color.copy(alpha = .16f * light.amount),
            Offset(rect.left, rect.top),
            Size(rect.width, rect.height),
            CornerRadius(radius),
        )
    }
}

private fun DrawScope.drawCityEventHeroText(typeface: Typeface?, text: String, x: Float, baseline: Float, size: Float, color: Color, fallback: Typeface = Typeface.create("sans-serif", Typeface.NORMAL), outline: Color? = null, stroke: Float = 0f, letterSpacing: Float = 0f) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: fallback
            this.textSize = size
            this.color = color.toArgb()
            this.letterSpacing = letterSpacing / size
            if (outline != null) {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
                this.color = outline.toArgb()
                canvas.nativeCanvas.drawText(text, x, baseline, this)
                style = Paint.Style.FILL
                this.color = color.toArgb()
            }
        }
        canvas.nativeCanvas.drawText(text, x, baseline, paint)
    }
}

private fun DrawScope.drawCityEventHeroCentered(typeface: Typeface?, text: String, center: Float, baseline: Float, size: Float, color: Color, fallback: Typeface = Typeface.create("sans-serif", Typeface.NORMAL), letterSpacing: Float = 0f, shadow: Color? = null, shadowRadius: Float = 0f) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: fallback
            textSize = size
            this.color = color.toArgb()
            this.letterSpacing = letterSpacing / size
            if (shadow != null) setShadowLayer(shadowRadius, 0f, 0f, shadow.toArgb())
        }
        canvas.nativeCanvas.drawText(text, center - paint.measureText(text) / 2f, baseline, paint)
    }
}

private fun DrawScope.drawCityEventHeroHeart(center: Offset, unit: Float, color: Color, fill: Boolean) {
    val left = center.x - 19.5f * unit
    val top = center.y - 18f * unit
    val path = Path().apply {
        moveTo(left + 19.5f * unit, top + 33f * unit)
        cubicTo(left + 9f * unit, top + 25.5f * unit, left + 3.2f * unit, top + 20.2f * unit, left + 3.2f * unit, top + 12.8f * unit)
        cubicTo(left + 3.2f * unit, top + 7.6f * unit, left + 6.8f * unit, top + 3.2f * unit, left + 11.8f * unit, top + 3.2f * unit)
        cubicTo(left + 15f * unit, top + 3.2f * unit, left + 17.8f * unit, top + 5f * unit, left + 19.5f * unit, top + 8f * unit)
        cubicTo(left + 21.2f * unit, top + 5f * unit, left + 24f * unit, top + 3.2f * unit, left + 27.2f * unit, top + 3.2f * unit)
        cubicTo(left + 32.2f * unit, top + 3.2f * unit, left + 35.8f * unit, top + 7.6f * unit, left + 35.8f * unit, top + 12.8f * unit)
        cubicTo(left + 35.8f * unit, top + 20.2f * unit, left + 30f * unit, top + 25.5f * unit, left + 19.5f * unit, top + 33f * unit)
        close()
    }
    if (fill) drawPath(path, color)
    drawPath(path, color, style = Stroke(4.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawCityEventHeroIcon(name: String, x: Float, y: Float, size: Float, color: Color) {
    when (name) {
        "gift" -> {
            val unit = size / 39f
            val stroke = Stroke(4.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(color, Offset(x + 3f * unit, y + 9f * unit), Size(33f * unit, 9f * unit), CornerRadius(3f * unit), style = stroke)
            val lower = Path().apply {
                moveTo(x + 6.5f * unit, y + 18f * unit)
                lineTo(x + 6.5f * unit, y + 33.5f * unit)
                arcTo(Rect(x + 6.5f * unit, y + 29.5f * unit, x + 14.5f * unit, y + 37.5f * unit), 180f, -90f, false)
                lineTo(x + 28.5f * unit, y + 37.5f * unit)
                arcTo(Rect(x + 24.5f * unit, y + 29.5f * unit, x + 32.5f * unit, y + 37.5f * unit), 90f, -90f, false)
                lineTo(x + 32.5f * unit, y + 18f * unit)
            }
            drawPath(lower, color, style = stroke)
            drawLine(color, Offset(x + 19.5f * unit, y + 9f * unit), Offset(x + 19.5f * unit, y + 37.5f * unit), 4.8f * unit, StrokeCap.Butt)
            drawCircle(color, 1.6f * unit, Offset(x + 15.2f * unit, y + 2.6f * unit))
            drawCircle(color, 1.6f * unit, Offset(x + 23.8f * unit, y + 2.6f * unit))
        }
        "filter" -> {
            val unit = size / 39f
            drawLine(color, Offset(x + 18f * unit, y + 7.3f * unit), Offset(x + 37f * unit, y + 7.3f * unit), 5f * unit, StrokeCap.Round)
            drawCircle(color, 5.2f * unit, Offset(x + 10.2f * unit, y + 7.5f * unit), style = Stroke(4.6f * unit))
            drawLine(color, Offset(x + 2f * unit, y + 28.3f * unit), Offset(x + 19.5f * unit, y + 28.3f * unit), 5f * unit, StrokeCap.Round)
            drawCircle(color, 5.2f * unit, Offset(x + 28.7f * unit, y + 28.2f * unit), style = Stroke(4.6f * unit))
        }
        "search" -> {
            val unit = size / 37f
            drawCircle(color, 12.9f * unit, Offset(x + 14.8f * unit, y + 14.8f * unit), style = Stroke(4.6f * unit))
            drawLine(color, Offset(x + 24.4f * unit, y + 24.4f * unit), Offset(x + 34.2f * unit, y + 34.2f * unit), 4.8f * unit, StrokeCap.Round)
        }
        "share" -> {
            val unit = size / 36f
            val stroke = Stroke(4.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tray = Path().apply {
                moveTo(x + 3f * unit, y + 22f * unit)
                lineTo(x + 3f * unit, y + 31.5f * unit)
                arcTo(Rect(x + 3f * unit, y + 26.5f * unit, x + 13f * unit, y + 36.5f * unit), 180f, -90f, false)
                lineTo(x + 28f * unit, y + 36.5f * unit)
                arcTo(Rect(x + 23f * unit, y + 26.5f * unit, x + 33f * unit, y + 36.5f * unit), 90f, -90f, false)
                lineTo(x + 33f * unit, y + 22f * unit)
            }
            drawPath(tray, color, style = stroke)
            drawLine(color, Offset(x + 18f * unit, y + 24f * unit), Offset(x + 18f * unit, y + 3.5f * unit), 4.6f * unit, StrokeCap.Round)
            val arrow = Path().apply {
                moveTo(x + 9.5f * unit, y + 11.5f * unit)
                lineTo(x + 18f * unit, y + 3.2f * unit)
                lineTo(x + 26.5f * unit, y + 11.5f * unit)
            }
            drawPath(arrow, color, style = stroke)
        }
        "more" -> {
            val unit = size / 33f
            listOf(3.5f, 16.5f, 29.5f).forEach { center -> drawCircle(color, 3.5f * unit, Offset(x + center * unit, y + 3.5f * unit)) }
        }
        "chevView" -> {
            val unit = size / 15f
            val path = Path().apply {
                moveTo(x + 3.2f * unit, y + 3.2f * unit)
                lineTo(x + 12f * unit, y + 14f * unit)
                lineTo(x + 3.2f * unit, y + 24.8f * unit)
            }
            drawPath(path, color, style = Stroke(4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        "chevCard" -> {
            val unit = size / 20f
            val path = Path().apply {
                moveTo(x + 3.2f * unit, y + 3.2f * unit)
                lineTo(x + 16f * unit, y + 18f * unit)
                lineTo(x + 3.2f * unit, y + 32.8f * unit)
            }
            drawPath(path, color, style = Stroke(4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        "home" -> {
            val unit = size / 65f
            val path = Path().apply {
                moveTo(x + 32.5f * unit, y)
                cubicTo(x + 34.4f * unit, y, x + 36f * unit, y + .7f * unit, x + 37.5f * unit, y + 1.9f * unit)
                lineTo(x + 61f * unit, y + 20.5f * unit)
                cubicTo(x + 63.3f * unit, y + 22.3f * unit, x + 64.8f * unit, y + 25f * unit, x + 64.8f * unit, y + 28f * unit)
                lineTo(x + 64.8f * unit, y + 54.5f * unit)
                cubicTo(x + 64.8f * unit, y + 62.6f * unit, x + 59.5f * unit, y + 68.6f * unit, x + 52f * unit, y + 68.6f * unit)
                lineTo(x + 13f * unit, y + 68.6f * unit)
                cubicTo(x + 5.6f * unit, y + 68.6f * unit, x + .2f * unit, y + 62.6f * unit, x + .2f * unit, y + 54.5f * unit)
                lineTo(x + .2f * unit, y + 28f * unit)
                cubicTo(x + .2f * unit, y + 25f * unit, x + 1.7f * unit, y + 22.3f * unit, x + 4f * unit, y + 20.5f * unit)
                lineTo(x + 27.5f * unit, y + 1.9f * unit)
                cubicTo(x + 29f * unit, y + .7f * unit, x + 30.6f * unit, y, x + 32.5f * unit, y)
                close()
            }
            drawPath(path, color)
            val smile = Path().apply {
                moveTo(x + 21.8f * unit, y + 38.8f * unit)
                quadraticTo(x + 32.5f * unit, y + 47.5f * unit, x + 43.2f * unit, y + 38.8f * unit)
            }
            drawPath(smile, Color.Black, style = Stroke(5.2f * unit, cap = StrokeCap.Round))
        }
        "chat" -> {
            val unit = size / 65f
            val bubble = Path().apply {
                moveTo(x + 32.5f * unit, y + 2.4f * unit)
                cubicTo(x + 49.8f * unit, y + 2.4f * unit, x + 62.6f * unit, y + 15.2f * unit, x + 62.6f * unit, y + 32.5f * unit)
                cubicTo(x + 62.6f * unit, y + 49.8f * unit, x + 49.8f * unit, y + 62.6f * unit, x + 32.5f * unit, y + 62.6f * unit)
                lineTo(x + 13.5f * unit, y + 62.6f * unit)
                cubicTo(x + 7f * unit, y + 62.6f * unit, x + 2.4f * unit, y + 58f * unit, x + 2.4f * unit, y + 51.5f * unit)
                lineTo(x + 2.4f * unit, y + 32.5f * unit)
                cubicTo(x + 2.4f * unit, y + 15.2f * unit, x + 15.2f * unit, y + 2.4f * unit, x + 32.5f * unit, y + 2.4f * unit)
                close()
            }
            drawPath(bubble, color, style = Stroke(5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, 3.3f * unit, Offset(x + 23.7f * unit, y + 32f * unit))
            drawCircle(color, 3.3f * unit, Offset(x + 41.2f * unit, y + 32f * unit))
        }
        "plus" -> {
            val unit = size / 69f
            drawCircle(color, 31.6f * unit, Offset(x + 34.5f * unit, y + 34.5f * unit), style = Stroke(5f * unit))
            drawLine(color, Offset(x + 34.5f * unit, y + 22.5f * unit), Offset(x + 34.5f * unit, y + 46.5f * unit), 5f * unit, StrokeCap.Round)
            drawLine(color, Offset(x + 22.5f * unit, y + 34.5f * unit), Offset(x + 46.5f * unit, y + 34.5f * unit), 5f * unit, StrokeCap.Round)
        }
        "bell" -> {
            val unit = size / 65f
            val bell = Path().apply {
                moveTo(x + 2.4f * unit, y + 51.5f * unit)
                lineTo(x + 6f * unit, y + 40.5f * unit)
                lineTo(x + 6f * unit, y + 29f * unit)
                arcTo(Rect(x + 6f * unit, y + 2.5f * unit, x + 59f * unit, y + 55.5f * unit), 180f, 180f, false)
                lineTo(x + 59f * unit, y + 40.5f * unit)
                lineTo(x + 62.6f * unit, y + 51.5f * unit)
                close()
            }
            drawPath(bell, color, style = Stroke(5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val clapper = Path().apply {
                arcTo(Rect(x + 20.5f * unit, y + 44f * unit, x + 44.5f * unit, y + 67f * unit), 0f, 180f, true)
            }
            drawPath(clapper, color, style = Stroke(5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        "person" -> {
            val unit = size / 63f
            drawCircle(color, 13.8f * unit, Offset(x + 31.5f * unit, y + 16.3f * unit), style = Stroke(5f * unit))
            withTransform({
                translate(x, y)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawPath(CityEventHeroShoulders, color, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private val CityEventHeroShoulders = PathParser().parsePathString("M3.4 62.6 A28.2 21.5 0 0 1 59.6 62.6").toPath()
