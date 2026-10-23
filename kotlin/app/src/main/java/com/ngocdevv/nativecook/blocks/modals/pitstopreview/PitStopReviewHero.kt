package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val pitStopReviewWheels = arrayOf(
    floatArrayOf(217.2f, 86.1f, 1f),
    floatArrayOf(52f, 86.1f, 1f),
    floatArrayOf(210.9f, 30.1f, 0.35f),
    floatArrayOf(57.5f, 31.9f, 0.35f),
)
private val pitStopReviewTyres = listOf(
    PitStopReviewMotion.Tyre(285.2, 120.8, 11.0, false, 1.0),
    PitStopReviewMotion.Tyre(279.2, 65.5, 23.0, false, -1.0),
    PitStopReviewMotion.Tyre(124.1, 120.9, 37.0, true, 1.0),
    PitStopReviewMotion.Tyre(129.3, 67.3, 53.0, true, -1.0),
)

@Composable
internal fun PitStopReviewHero(
    snapshot: State<PitStopReviewRuntime.Snapshot>,
    assets: PitStopReviewAssets,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val displayDensity = androidx.compose.ui.platform.LocalDensity.current.density
    val unit = displayDensity * scale
    val paints = remember(unit) { PitStopReviewHeroPaints(unit) }
    val frames = remember(assets) { assets.carFrames.map { image -> image.asAndroidBitmap() } }
    val shadowBitmap = remember(assets) { assets.carShadow.asAndroidBitmap() }
    Canvas(modifier) {
        val scene = snapshot.value.scene
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            paints.world.draw(canvas, scene, unit)
            val carX = PitStopReviewTheme.Hero.carX + PitStopReviewMotion.carX(scene).toFloat()
            val carY = PitStopReviewTheme.Hero.carY
            val carWidth = PitStopReviewTheme.Hero.carWidth
            val carHeight = PitStopReviewTheme.Hero.carHeight
            val destination = RectF(
                carX * unit,
                carY * unit,
                (carX + carWidth) * unit,
                (carY + carHeight) * unit,
            )

            val blur = PitStopReviewMotion.carBlur(scene).toFloat()
            paints.shadow.alpha = (255f * 0.9f * (1f - min(1f, blur / 4f))).roundToInt()
            canvas.drawBitmap(shadowBitmap, null, destination, paints.shadow)

            val pitchPose = ((PitStopReviewMotion.pitch(scene) + 1.5) / 0.75)
                .coerceIn(0.0, frames.size - 1.0001)
            val first = floor(pitchPose).toInt()
            val second = min(frames.lastIndex, first + 1)
            val secondAlpha = (pitchPose - first).toFloat()
            drawBlurredCar(canvas, frames[first], destination, 1f - secondAlpha, blur, paints)
            drawBlurredCar(canvas, frames[second], destination, secondAlpha, blur, paints)

            val streaks = PitStopReviewMotion.carStreaks(scene).toFloat()
            if (streaks > 0.001f) {
                canvas.save()
                canvas.clipRect(destination)
                repeat(14) { index ->
                    val y = destination.top + ((index * 17) % 100) / 100f * destination.height()
                    paints.streak.strokeWidth = if (index % 3 == 0) 1.2f * unit else 0.65f * unit
                    paints.streak.alpha = (255f * streaks * if (index % 3 == 0) 0.42f else 0.22f).roundToInt()
                    val start = destination.left - 20f * unit
                    val end = destination.right + ((index * 19) % 44) * unit
                    canvas.drawLine(start, y, end, y, paints.streak)
                }
                canvas.restore()
            }

            val trailStrength = PitStopReviewMotion.trail(scene).toFloat()
            val trailLength = max(0.2f, trailStrength)
            val trailX = carX + (-120f - PitStopReviewTheme.Hero.carX) * trailLength
            val trailWidth = 300f * trailLength
            paints.trail.alpha = (255f * trailStrength * trailStrength).roundToInt()
            canvas.drawBitmap(
                paints.trailBitmap,
                Rect(0, 0, paints.trailBitmap.width, paints.trailBitmap.height),
                RectF(
                    trailX * unit,
                    0f,
                    (trailX + trailWidth) * unit,
                    200f * unit,
                ),
                paints.trail,
            )

            drawDiscs(canvas, scene, carX, carY, unit, paints)
            drawSmoke(canvas, scene, unit, paints)
            drawGantry(canvas, scene, unit, paints)
        }
    }
}

private fun drawBlurredCar(
    canvas: android.graphics.Canvas,
    bitmap: android.graphics.Bitmap,
    destination: RectF,
    mix: Float,
    blur: Float,
    paints: PitStopReviewHeroPaints,
) {
    if (mix <= 0.001f) return
    if (blur <= 0.5f) {
        paints.car.alpha = (255f * mix).roundToInt()
        canvas.drawBitmap(bitmap, null, destination, paints.car)
        return
    }
    val samples = 7
    repeat(samples) { index ->
        val offset = (index / (samples - 1f) - 0.5f) * blur * destination.width() / PitStopReviewTheme.Hero.carWidth
        val shifted = RectF(destination).apply { this.offset(offset, 0f) }
        paints.car.alpha = (255f * mix / samples).roundToInt()
        canvas.drawBitmap(bitmap, null, shifted, paints.car)
    }
}

private fun drawDiscs(
    canvas: android.graphics.Canvas,
    scene: Double,
    carX: Float,
    carY: Float,
    unit: Float,
    paints: PitStopReviewHeroPaints,
) {
    val strength = PitStopReviewMotion.disc(scene).toFloat()
    if (strength <= 0.001f) return
    pitStopReviewWheels.forEach { wheel ->
        val alpha = (255f * strength * wheel[2]).roundToInt()
        canvas.save()
        canvas.translate((carX + wheel[0]) * unit, (carY + wheel[1]) * unit)
        canvas.scale(1f, 0.72f)
        paints.discBloom.alpha = alpha
        paints.discCore.alpha = alpha
        canvas.drawCircle(0f, 0f, 17f * unit, paints.discBloom)
        canvas.drawCircle(0f, 0f, 7.5f * unit, paints.discCore)
        canvas.restore()
    }
}

private fun drawSmoke(
    canvas: android.graphics.Canvas,
    scene: Double,
    unit: Float,
    paints: PitStopReviewHeroPaints,
) {
    val packed = PitStopReviewMotion.packPuffs(scene, pitStopReviewTyres)
    if (paints.smokeShader.draw(canvas, packed, unit)) return
    for (index in 0 until packed.count) {
        val offset = index * 4
        val x = packed.data[offset].toFloat() * unit
        val y = packed.data[offset + 1].toFloat() * unit
        val radius = packed.data[offset + 2].toFloat() * unit
        val alpha = packed.data[offset + 3].toFloat()
        paints.smoke.alpha = 255
        paints.smoke.shader = RadialGradient(
            x,
            y,
            max(radius, 0.5f),
            intArrayOf(Color.argb((255f * alpha).roundToInt(), 232, 230, 237), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, paints.smoke)
    }
    paints.smoke.shader = null
    paints.smoke.alpha = 255
}

private fun drawGantry(
    canvas: android.graphics.Canvas,
    scene: Double,
    unit: Float,
    paints: PitStopReviewHeroPaints,
) {
    val opacity = PitStopReviewMotion.gantry(scene).toFloat()
    if (opacity <= 0.001f) return
    val gantry = PitStopReviewTheme.Gantry
    val half = 2f * gantry.pitch + gantry.radius + gantry.housingPadding
    val rect = RectF(
        (gantry.centerX - half) * unit,
        (gantry.centerY - gantry.housingHeight / 2f) * unit,
        (gantry.centerX + half) * unit,
        (gantry.centerY + gantry.housingHeight / 2f) * unit,
    )
    paints.housing.color = Color.argb((255f * 0.92f * opacity).roundToInt(), 12, 11, 13)
    paints.housingStroke.color = Color.argb((255f * 0.16f * opacity).roundToInt(), 255, 255, 255)
    canvas.drawRoundRect(rect, gantry.housingHeight / 2f * unit, gantry.housingHeight / 2f * unit, paints.housing)
    canvas.drawRoundRect(rect, gantry.housingHeight / 2f * unit, gantry.housingHeight / 2f * unit, paints.housingStroke)
    repeat(5) { index ->
        val x = (gantry.centerX + (index - 2) * gantry.pitch) * unit
        val y = gantry.centerY * unit
        paints.lampOff.alpha = (255f * opacity).roundToInt()
        canvas.drawCircle(x, y, gantry.radius * unit, paints.lampOff)
        val on = PitStopReviewMotion.lightOn(scene, index) *
            (1.0 - PitStopReviewMotion.lightAway(scene, index)) * opacity
        if (on <= 0.001) return@repeat
        paints.lamp.alpha = 255
        paints.lamp.shader = RadialGradient(
            x,
            y,
            gantry.radius * 2.6f * unit,
            intArrayOf(
                Color.argb((255.0 * on).roundToInt(), 255, 210, 194),
                Color.argb((230.0 * on).roundToInt(), 255, 59, 48),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, gantry.radius * 2.6f * unit, paints.lamp)
    }
    paints.lamp.shader = null
    paints.lamp.alpha = 255
}
