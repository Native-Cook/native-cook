package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class CityEventHeroPhotoRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class CityEventHeroUiLight(val amount: Float, val color: Color)

internal fun CityEventHeroPhotoRect(geometry: CityEventHeroGeometry, assets: CityEventHeroAssets): CityEventHeroPhotoRect {
    val card = geometry.box(CityEventHeroCard)
    val cover = max(card.width / assets.city.width, card.height / assets.city.height)
    val width = assets.city.width * cover
    val height = assets.city.height * cover
    val left = card.left + (card.width - width) / 2f
    val top = card.top - (height - card.height) * CityEventHeroTheme.layout.photoShift
    return CityEventHeroPhotoRect(left, top, width, height)
}

internal fun CityEventHeroSceneOpacity(frame: CityEventHeroFrame, tiltX: Float, tiltY: Float): Float {
    val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
    val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
    val show = if (frame.show >= 0f && frame.show < CityEventHeroShowEnd) 1f else 0f
    val amount = maxOf(
        frame.day,
        frame.night,
        abs(cameraX) / CityEventHeroSpace.ampX,
        abs(cameraY) / CityEventHeroSpace.ampY,
        frame.camZ,
        show,
    )
    return (amount / .02f).coerceIn(0f, 1f) * frame.still
}

internal fun CityEventHeroTip(
    rect: CityEventHeroPhotoRect,
    frame: CityEventHeroFrame,
    tiltX: Float,
    tiltY: Float,
): Triple<Float, Float, Float> {
    val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
    val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
    val zoom = frame.camZ * CityEventHeroSpace.dolly + abs(cameraX) * CityEventHeroSpace.coverX + abs(cameraY) * CityEventHeroSpace.coverY
    val scale = 1f + zoom * CityEventHeroSpace.tower
    val photoX = CityEventHeroSpace.focusX + (CityEventHeroTower.tipX - CityEventHeroSpace.focusX) * scale + cameraX * CityEventHeroSpace.tower
    val photoY = CityEventHeroSpace.focusY + (CityEventHeroTower.tipY - CityEventHeroSpace.focusY) * scale + cameraY * CityEventHeroSpace.tower
    val cover = rect.width / 850f
    return Triple(rect.left + photoX * cover, rect.top + photoY * cover, scale)
}

internal fun CityEventHeroUiLight(frame: CityEventHeroFrame): CityEventHeroUiLight {
    val fire = frame.fire
    val fireFade = (frame.night / CityEventHeroNight.fireFade).coerceIn(0f, 1f)
    var red = 0f
    var green = 0f
    var blue = 0f
    var weight = 0f
    if (fire >= 0f) {
        CityEventHeroFire.bursts.forEachIndexed { index, burst ->
            val amount = CityEventHeroFlashAt(burst, fire - burst.at) + if (index == 0) .6f * CityEventHeroFlareAt(fire) else 0f
            val color = Color(burst.color)
            red += color.red * amount
            green += color.green * amount
            blue += color.blue * amount
            weight += amount
        }
    }
    val led = FloatArray(16)
    CityEventHeroLedAt(frame.show, led)
    val ledSpill = led[11] * .35f
    red += CityEventHeroTheme.scene.spillText[0] * ledSpill
    green += CityEventHeroTheme.scene.spillText[1] * ledSpill
    blue += CityEventHeroTheme.scene.spillText[2] * ledSpill
    weight += ledSpill
    val amount = ((CityEventHeroFireLight(fire) + ledSpill) * fireFade).coerceIn(0f, 1f)
    val color = if (weight > 0f) {
        Color(
            (red / weight * 255f / 32f).roundToInt().times(32).coerceIn(0, 255) / 255f,
            (green / weight * 255f / 32f).roundToInt().times(32).coerceIn(0, 255) / 255f,
            (blue / weight * 255f / 32f).roundToInt().times(32).coerceIn(0, 255) / 255f,
        )
    } else {
        CityEventHeroTheme.night.rim
    }
    return CityEventHeroUiLight((amount * 64f).roundToInt() / 64f, color)
}

/** API 26–32 fallback: the same baked spatial layers and timeline, without per-pixel relighting. */
internal fun DrawScope.drawCityEventHeroSceneFallback(
    assets: CityEventHeroAssets,
    rect: CityEventHeroPhotoRect,
    frame: CityEventHeroFrame,
    tiltX: Float,
    tiltY: Float,
    opacity: Float,
) {
    if (opacity <= 0f) return
    val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
    val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
    val zoom = frame.camZ * CityEventHeroSpace.dolly + abs(cameraX) * CityEventHeroSpace.coverX + abs(cameraY) * CityEventHeroSpace.coverY
    drawCityEventHeroLayer(assets.fillImage, rect, CityEventHeroSpace.far, cameraX, cameraY, zoom, opacity)
    drawCityEventHeroLayer(assets.towerLayer, rect, CityEventHeroSpace.tower, cameraX, cameraY, zoom, opacity)
    drawCityEventHeroLayer(assets.nearLayer, rect, CityEventHeroSpace.near, cameraX, cameraY, zoom, opacity)

    if (frame.day > 0f) {
        val sun = CityEventHeroSun()
        CityEventHeroSunAt(frame.day, sun)
        drawRect(
            color = Color(0xFFFFA866).copy(alpha = opacity * sun.amount * .18f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            blendMode = BlendMode.Screen,
        )
    }
    if (frame.night > 0f) {
        val destination = IntOffset(rect.left.toInt(), rect.top.toInt())
        val destinationSize = IntSize(rect.width.toInt(), rect.height.toInt())
        drawRect(
            color = Color(0xFF00081B).copy(alpha = opacity * frame.night * .72f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
        )
        drawImage(assets.lightsImage, dstOffset = destination, dstSize = destinationSize, alpha = opacity * frame.night * .72f, blendMode = BlendMode.Screen, colorFilter = ColorFilter.tint(Color(1f, .74f, .42f), BlendMode.Modulate))
        drawImage(assets.bloomImage, dstOffset = destination, dstSize = destinationSize, alpha = opacity * frame.night * .32f, blendMode = BlendMode.Screen, colorFilter = ColorFilter.tint(Color(1f, .62f, .32f), BlendMode.Modulate))
    }
    drawCityEventHeroFallbackFacade(assets, rect, frame, tiltX, tiltY, opacity)
    drawCityEventHeroFallbackRelight(rect, frame, opacity)
}

internal fun DrawScope.drawCityEventHeroFallbackTowerPass(
    assets: CityEventHeroAssets,
    rect: CityEventHeroPhotoRect,
    frame: CityEventHeroFrame,
    tiltX: Float,
    tiltY: Float,
    opacity: Float,
) {
    val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
    val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
    val zoom = frame.camZ * CityEventHeroSpace.dolly + abs(cameraX) * CityEventHeroSpace.coverX + abs(cameraY) * CityEventHeroSpace.coverY
    drawCityEventHeroLayer(assets.towerLayer, rect, CityEventHeroSpace.tower, cameraX, cameraY, zoom, opacity)
    drawCityEventHeroLayer(assets.nearLayer, rect, CityEventHeroSpace.near, cameraX, cameraY, zoom, opacity)
}

private fun DrawScope.drawCityEventHeroLayer(
    image: ImageBitmap,
    rect: CityEventHeroPhotoRect,
    parallax: Float,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    alpha: Float,
) {
    val cover = rect.width / 850f
    val focus = Offset(rect.left + CityEventHeroSpace.focusX * cover, rect.top + CityEventHeroSpace.focusY * cover)
    val scale = 1f + zoom * parallax
    withTransform({
        translate(focus.x + cameraX * parallax * cover, focus.y + cameraY * parallax * cover)
        scale(scale, scale)
        translate(-focus.x, -focus.y)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
            dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawCityEventHeroFallbackFacade(
    assets: CityEventHeroAssets,
    rect: CityEventHeroPhotoRect,
    frame: CityEventHeroFrame,
    tiltX: Float,
    tiltY: Float,
    opacity: Float,
) {
    val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
    val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
    val zoom = frame.camZ * CityEventHeroSpace.dolly + abs(cameraX) * CityEventHeroSpace.coverX + abs(cameraY) * CityEventHeroSpace.coverY
    val scale = 1f + zoom * CityEventHeroSpace.tower
    val cover = rect.width / 850f
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val checkpoint = native.save()
        native.translate(rect.left, rect.top)
        native.scale(cover, cover)
        native.translate(CityEventHeroSpace.focusX + cameraX * CityEventHeroSpace.tower, CityEventHeroSpace.focusY + cameraY * CityEventHeroSpace.tower)
        native.scale(scale, scale)
        native.translate(-CityEventHeroSpace.focusX, -CityEventHeroSpace.focusY)
        assets.fallbackFacade.draw(native, frame, opacity)
        native.restoreToCount(checkpoint)
    }
}

/** Pre-33 approximates per-pixel light/depth with layered, colored radial lights and smoke. */
private fun DrawScope.drawCityEventHeroFallbackRelight(rect: CityEventHeroPhotoRect, frame: CityEventHeroFrame, opacity: Float) {
    if (frame.fire < 0f) return
    val unit = size.width / CityEventHeroRefWidth
    val tip = CityEventHeroTip(rect, frame, 0f, 0f)
    val fade = (frame.night / CityEventHeroNight.fireFade).coerceIn(0f, 1f) * opacity
    CityEventHeroFire.bursts.forEach { burst ->
        val age = frame.fire - burst.at
        if (age <= 0f) return@forEach
        val center = Offset(tip.first + burst.dx * unit * tip.third, tip.second + burst.dy * unit * tip.third)
        val light = CityEventHeroFlashAt(burst, age) * fade
        val radius = CityEventHeroFire.flashR * rect.width / 850f
        drawCircle(Brush.radialGradient(listOf(Color(burst.color).copy(alpha = light * .6f), Color.Transparent), center, radius), radius, center, blendMode = BlendMode.Plus)
        val smokeCenter = center - Offset(0f, CityEventHeroFire.smokeRise * age * density)
        val smokeRadius = CityEventHeroFire.smokeR * (.45f + .55f * (1f - exp(-age / .6f))) * density
        val smokeAlpha = CityEventHeroSegment(age, 0f, CityEventHeroFire.smokeIn) * exp(-age / CityEventHeroFire.smokeTau) * CityEventHeroTheme.scene.smokeAlpha * fade
        drawCircle(Brush.radialGradient(listOf(Color(.3f, .31f, .37f, smokeAlpha), Color.Transparent), smokeCenter, smokeRadius), smokeRadius, smokeCenter)
    }
}

internal fun DrawScope.drawCityEventHeroFireworks(
    glow: CityEventHeroSparkGlowPainter,
    rect: CityEventHeroPhotoRect,
    referenceUnit: Float,
    frame: CityEventHeroFrame,
    tiltX: Float,
    tiltY: Float,
) {
    val fire = frame.fire
    if (fire < 0f || frame.night < .01f) return
    val tipData = CityEventHeroTip(rect, frame, tiltX, tiltY)
    val tip = Offset(tipData.first, tipData.second)
    val unit = referenceUnit * tipData.third
    glow.begin(tip, unit)
    CityEventHeroFire.bursts.forEachIndexed { burstIndex, burst ->
        val age = fire - burst.at
        val shellTime = fire - (burst.at - CityEventHeroFire.rise)
        if ((burst.launchDx != 0f || burst.launchDy != 0f) && shellTime > 0f && shellTime < CityEventHeroFire.rise) {
            val headLeft = CityEventHeroShellLeft(shellTime)
            val coreLeft = CityEventHeroShellLeft((shellTime - .02f).coerceAtLeast(0f))
            val tailLeft = CityEventHeroShellLeft((shellTime - .1f).coerceAtLeast(0f))
            val start = Offset(tip.x + (burst.dx + burst.launchDx) * unit, tip.y + (burst.dy + burst.launchDy) * unit)
            val end = Offset(tip.x + burst.dx * unit, tip.y + burst.dy * unit)
            fun point(left: Float) = Offset(end.x + (start.x - end.x) * left, end.y + (start.y - end.y) * left)
            val fade = (frame.night / CityEventHeroNight.fireFade).coerceIn(0f, 1f)
            val hot = Color(CityEventHeroFire.bursts[0].hot)
            glow.line(point(tailLeft), point(headLeft), hot.copy(alpha = .38f * fade), 12f * unit)
            drawLine(hot.copy(alpha = fade), point(coreLeft), point(headLeft), 3.2f * unit, StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        if (age <= 0f) return@forEachIndexed
        repeat(burst.count) { spark ->
            val hash0 = CityEventHeroHash3(burstIndex, spark, 0)
            val hash1 = CityEventHeroHash3(burstIndex, spark, 1)
            val hash2 = CityEventHeroHash3(burstIndex, spark, 2)
            val hash3 = CityEventHeroHash3(burstIndex, spark, 3)
            val slot = (spark + .6f * (hash0 - .5f)) / burst.count
            val speedFactor = .8f + .3f * hash2
            val angle: Float
            val speed: Float
            if (burst.spread > 0f) {
                val side = 2f * slot - 1f
                angle = -PI.toFloat() / 2f + burst.spread * side
                speed = burst.speed * speedFactor * (1f - burst.upSlow * (1f - abs(side)))
            } else {
                val depth = 2f * hash3 - 1f
                angle = 2f * PI.toFloat() * slot
                speed = burst.speed * speedFactor * sqrt(1f - depth * depth)
            }
            val life = burst.life * (.75f + .35f * hash1)
            if (age >= life) return@repeat
            val remaining = 1f - age / life
            val crackle = age / life > 1f - burst.crackle
            if (crackle && CityEventHeroHash3(burstIndex, spark, 10 + floorValue(age * 30f)) > .55f) return@repeat
            val nightFade = (frame.night / CityEventHeroNight.fireFade).coerceIn(0f, 1f)
            val origin = Offset(tip.x + burst.dx * unit + cos(angle) * 30f * unit, tip.y + burst.dy * unit + sin(angle) * 30f * unit)
            fun point(at: Float): Offset {
                val drag = 1f - exp(-burst.drag * at)
                val range = speed * drag / burst.drag
                val x = cos(angle) * range
                val y = sin(angle) * range + burst.gravity * (at / burst.drag - drag / (burst.drag * burst.drag))
                return Offset(origin.x + x * unit, origin.y + y * unit)
            }
            val colorProgress = (age / burst.life).coerceIn(0f, 1f)
            val color = if (colorProgress < .25f) {
                cityEventHeroGammaMix(Color(burst.hot), Color(burst.color), colorProgress / .25f)
            } else {
                cityEventHeroGammaMix(Color(burst.color), Color(burst.ember), (colorProgress - .25f) / .75f)
            }
            val bucket = if (remaining > .5f) 0 else if (!crackle) 1 else 2
            val glowIn = CityEventHeroSegment(age, .08f, .23f)
            val coreIn = CityEventHeroSegment(age, 0f, .08f)
            val tail = point((age - burst.trail).coerceAtLeast(0f))
            val core = point((age - burst.trail * .4f).coerceAtLeast(0f))
            val head = point(age)
            val glowWidths = floatArrayOf(12f, 9f, 7f)
            val glowAlphas = floatArrayOf(.38f, .26f, .18f)
            val coreWidths = floatArrayOf(3.4f, 2.8f, 2.2f)
            val coreAlphas = floatArrayOf(1f, .8f, .6f)
            glow.line(tail, head, color.copy(alpha = glowAlphas[bucket] * nightFade * glowIn), glowWidths[bucket] * unit)
            drawLine(color.copy(alpha = coreAlphas[bucket] * nightFade * coreIn), core, head, coreWidths[bucket] * unit, StrokeCap.Round, blendMode = BlendMode.Plus)
        }
    }
    drawIntoCanvas { canvas -> glow.finish(canvas.nativeCanvas) }
}

private fun floorValue(value: Float): Int = kotlin.math.floor(value).toInt()

private fun cityEventHeroMix(from: Color, to: Color, progress: Float): Color = Color(
    from.red + (to.red - from.red) * progress,
    from.green + (to.green - from.green) * progress,
    from.blue + (to.blue - from.blue) * progress,
    from.alpha + (to.alpha - from.alpha) * progress,
)

// Reanimated interpolateColor defaults to gamma 2.2, including the heart ring.
internal fun cityEventHeroGammaMix(from: Color, to: Color, progress: Float): Color {
    fun channel(a: Float, b: Float): Float = (a.pow(2.2f) + (b.pow(2.2f) - a.pow(2.2f)) * progress).coerceAtLeast(0f).pow(1f / 2.2f)
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue))
}
