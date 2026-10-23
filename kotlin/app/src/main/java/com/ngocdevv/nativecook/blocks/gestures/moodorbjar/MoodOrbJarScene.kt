package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Reference artwork used by the shipped React Native scene. */
internal class MoodOrbJarArt(
    val jarPlate: ImageBitmap,
    val lid: ImageBitmap,
    val orbs: List<ImageBitmap>,
    val pileAtlas: ImageBitmap,
)

@Composable
internal fun rememberMoodOrbJarArt(): MoodOrbJarArt {
    val jarPlate = ImageBitmap.imageResource(R.drawable.moodorbjar_jar_plate)
    val lid = ImageBitmap.imageResource(R.drawable.moodorbjar_lid)
    val awful = ImageBitmap.imageResource(R.drawable.moodorbjar_orb_awful_r)
    val bad = ImageBitmap.imageResource(R.drawable.moodorbjar_orb_bad_r)
    val neutral = ImageBitmap.imageResource(R.drawable.moodorbjar_orb_neutral_r)
    val good = ImageBitmap.imageResource(R.drawable.moodorbjar_orb_good_r)
    val great = ImageBitmap.imageResource(R.drawable.moodorbjar_orb_great_r)
    val pileAtlas = ImageBitmap.imageResource(R.drawable.moodorbjar_pile_atlas)
    return remember(jarPlate, lid, awful, bad, neutral, good, great, pileAtlas) {
        MoodOrbJarArt(
            jarPlate = jarPlate,
            lid = lid,
            orbs = listOf(awful, bad, neutral, good, great),
            pileAtlas = pileAtlas,
        )
    }
}

private const val ORB_IMAGE_SCALE = MoodOrbJarTokens.Style.orbImageScale

internal class MoodOrbJarScenePens {
    val cardShadow = SoftRoundRect(
        MoodOrbJarColors.cardShadow,
        MoodOrbJarTokens.Style.cardShadowBlur.toFloat(),
    )
    val aimPath = Path()
}

@Composable
internal fun rememberMoodOrbJarScenePens() = remember { MoodOrbJarScenePens() }

private fun DrawScope.drawImageFill(
    image: ImageBitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f,
) {
    withTransform({
        translate(x, y)
        scale(width / image.width, height / image.height, pivot = Offset.Zero)
    }) {
        drawImage(image, alpha = alpha)
    }
}

private fun DrawScope.drawOrbSprite(
    image: ImageBitmap,
    x: Float,
    y: Float,
    diameter: Float,
    degrees: Float,
    scaleX: Float,
    scaleY: Float,
    alpha: Float,
) {
    withTransform({
        translate(x, y)
        rotate(degrees, pivot = Offset.Zero)
        scale(
            scaleX * diameter / image.width,
            scaleY * diameter / image.height,
            pivot = Offset.Zero,
        )
    }) {
        drawImage(
            image = image,
            topLeft = Offset(-image.width / 2f, -image.height / 2f),
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawAtlasOrb(
    atlas: ImageBitmap,
    index: Int,
    x: Float,
    y: Float,
    diameter: Float,
    degrees: Float,
    scaleX: Float,
    scaleY: Float,
    alpha: Float,
) {
    withTransform({
        translate(x, y)
        rotate(degrees, pivot = Offset.Zero)
        scale(scaleX, scaleY, pivot = Offset.Zero)
    }) {
        clipRect(
            left = -diameter / 2,
            top = -diameter / 2,
            right = diameter / 2,
            bottom = diameter / 2,
        ) {
            drawImageFill(
                image = atlas,
                x = -diameter / 2 - index * diameter,
                y = -diameter / 2,
                width = diameter * PILE_N,
                height = diameter,
                alpha = alpha,
            )
        }
    }
}

private fun moodImage(art: MoodOrbJarArt, mood: Float): ImageBitmap {
    return art.orbs[mood.toInt().coerceIn(0, MoodOrbJarMoods.size - 1)]
}

/** Page, picker, source jar plate, live orb bodies, landing feedback and lid. */
internal fun DrawScope.drawMoodOrbJarScene(
    geometry: MoodOrbJarGeometry,
    frame: MoodOrbJarFrame,
    art: MoodOrbJarArt,
    pens: MoodOrbJarScenePens,
) {
    frame.readTick()
    val scale = geometry.s.toFloat()
    val width = geometry.w.toFloat()
    val height = geometry.h.toFloat()
    scale(density, density, pivot = Offset.Zero) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(MoodOrbJarColors.bg, MoodOrbJarColors.bgBottom),
                startY = 0f,
                endY = height,
            ),
            size = Size(width, height),
        )

        val cardY = (geometry.top + MoodOrbJarLayout.PICKER_TOP * geometry.s).toFloat()
        val cardX = MoodOrbJarLayout.SIDE.toFloat() * scale
        val cardWidth = width - cardX * 2
        val cardHeight = MoodOrbJarLayout.PICKER_HEIGHT.toFloat() * scale
        val cardRadius = MoodOrbJarLayout.CARD_RADIUS.toFloat() * scale
        drawCardShadow(
            pens = pens,
            x = cardX,
            y = cardY + MoodOrbJarTokens.Style.cardShadowY.toFloat() * scale,
            width = cardWidth,
            height = cardHeight,
            radius = cardRadius,
        )
        drawRoundRect(
            color = MoodOrbJarColors.card,
            topLeft = Offset(cardX, cardY),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(cardRadius),
        )
        val pickerDiameter = MoodOrbJarLayout.PICKER_ORB.toFloat() * scale * 2 * ORB_IMAGE_SCALE.toFloat()
        for (index in MoodOrbJarMoods.indices) {
            val slot = frame.slots[index]
            val orbScale = 0.9f + 0.1f * slot
            drawOrbSprite(
                image = art.orbs[index],
                x = geometry.slotX[index].toFloat(),
                y = geometry.slotY.toFloat(),
                diameter = pickerDiameter,
                degrees = 0f,
                scaleX = orbScale,
                scaleY = orbScale,
                alpha = slot,
            )
        }

        val rippleOn = frame.ripple[4]
        val rippleTime = frame.ripple[2] * MoodOrbJarRipple.DURATION
        val jarKnock = if (rippleOn > 0f) {
            MoodOrbJarKnock.AMPLITUDE * scale * exp(-MoodOrbJarKnock.DECAY * rippleTime) *
                sin(2 * PI * MoodOrbJarKnock.HERTZ * rippleTime)
        } else {
            0.0
        }
        withTransform({ translate(jarKnock.toFloat(), 0f) }) {
            val plateY = (MoodOrbJarTokens.REPLICA_PLATE_Y / 1206.0 * geometry.w).toFloat()
            val plateHeight = (MoodOrbJarTokens.REPLICA_PLATE_HEIGHT / 1206.0 * geometry.w).toFloat()
            drawImageFill(art.jarPlate, 0f, plateY, width, plateHeight)
            drawPool(geometry, frame, art)

            val rippleOpacity = rippleOn * (1 - frame.ripple[2]) * MoodOrbJarTokens.Style.rippleAlpha.toFloat()
            if (rippleOpacity > 0f) {
                drawCircle(
                    color = MoodOrbJarMoods[frame.ripple[3].toInt().coerceIn(0, MoodOrbJarMoods.size - 1)].base,
                    radius = (MoodOrbJarLayout.ORB + MoodOrbJarRipple.RADIUS * frame.ripple[2]).toFloat() * scale,
                    center = Offset(frame.ripple[0], frame.ripple[1]),
                    alpha = rippleOpacity,
                    style = Stroke(width = MoodOrbJarTokens.Style.rippleStroke.toFloat() * scale),
                )
            }

            if (frame.aimSize > 0) {
                pens.aimPath.rewind()
                var index = 0
                while (index + 1 < frame.aimSize) {
                    val radius = (
                        MoodOrbJarTokens.Style.aimStartRadius -
                            index * MoodOrbJarTokens.Style.aimRadiusStep
                        ).toFloat() * scale
                    pens.aimPath.addOval(
                        Rect(
                            center = Offset(frame.aim[index], frame.aim[index + 1]),
                            radius = radius,
                        ),
                    )
                    index += 2
                }
                drawPath(pens.aimPath, MoodOrbJarColors.aim)
            }

            val lidWidth = MoodOrbJarLayout.LID_WIDTH.toFloat() * scale
            val lidHeight = MoodOrbJarLayout.LID_HEIGHT.toFloat() * scale
            val hingeX = (geometry.lidX + MoodOrbJarLayout.LID_WIDTH * geometry.s / 2).toFloat()
            val hingeY = (geometry.lidY + MoodOrbJarLayout.LID_HEIGHT * geometry.s).toFloat()
            withTransform({
                translate(0f, -MoodOrbJarLid.LIFT.toFloat() * scale * frame.lid)
                rotate(
                    degrees = (-MoodOrbJarLid.TILT * frame.lid * 180.0 / PI).toFloat(),
                    pivot = Offset(hingeX, hingeY),
                )
            }) {
                drawImageFill(
                    image = art.lid,
                    x = geometry.lidX.toFloat(),
                    y = geometry.lidY.toFloat(),
                    width = lidWidth,
                    height = lidHeight,
                )
            }
        }
    }
}

private fun DrawScope.drawPool(
    geometry: MoodOrbJarGeometry,
    frame: MoodOrbJarFrame,
    art: MoodOrbJarArt,
) {
    val scale = geometry.s.toFloat()
    val orbRadius = MoodOrbJarLayout.ORB.toFloat() * scale
    val diameter = orbRadius * 2 * ORB_IMAGE_SCALE.toFloat()
    val trailGravity = MoodOrbJarThrow.GRAVITY.toFloat() * scale
    for (index in 0 until POOL) {
        val frameIndex = index * ORB_STRIDE
        val image = moodImage(art, frame.orbs[frameIndex + 5])
        if (frame.orbs[frameIndex + 9] == 1f) {
            for (copy in 0 until MoodOrbJarTrail.COPIES) {
                val tau = ((copy + 1) * MoodOrbJarTrail.GAP).toFloat()
                val x = frame.orbs[frameIndex] - frame.orbs[frameIndex + 7] * tau
                val y = frame.orbs[frameIndex + 1] - frame.orbs[frameIndex + 8] * tau +
                    0.5f * trailGravity * tau * tau
                val trailScale = 1 - tau * 3
                drawOrbSprite(
                    image = image,
                    x = x,
                    y = y,
                    diameter = diameter,
                    degrees = 0f,
                    scaleX = trailScale,
                    scaleY = trailScale,
                    alpha = MoodOrbJarTrail.alpha[copy],
                )
            }
        }
        val opacity = frame.orbs[frameIndex + 4]
        if (opacity <= 0f) {
            continue
        }
        val squash = frame.orbs[frameIndex + 6]
        val degrees = (frame.orbs[frameIndex + 2] * 180.0 / PI).toFloat()
        val orbScale = frame.orbs[frameIndex + 3]
        val x = frame.orbs[frameIndex]
        val y = frame.orbs[frameIndex + 1] + squash * orbRadius
        val scaleX = orbScale * (1 + squash)
        val scaleY = orbScale * (1 - squash)
        if (index < PILE_N) {
            drawAtlasOrb(
                atlas = art.pileAtlas,
                index = index,
                x = x,
                y = y,
                diameter = diameter,
                degrees = degrees,
                scaleX = scaleX,
                scaleY = scaleY,
                alpha = opacity,
            )
        } else {
            drawOrbSprite(
                image = image,
                x = x,
                y = y,
                diameter = diameter,
                degrees = degrees,
                scaleX = scaleX,
                scaleY = scaleY,
                alpha = opacity,
            )
        }
    }
}

private fun DrawScope.drawCardShadow(
    pens: MoodOrbJarScenePens,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            pens.cardShadow.draw(canvas.nativeCanvas, x, y, width, height, radius)
        }
        return
    }
    for (layer in 1..5) {
        val growth = layer * 2f
        drawRoundRect(
            color = MoodOrbJarColors.cardShadow,
            topLeft = Offset(x - growth, y - growth),
            size = Size(width + growth * 2, height + growth * 2),
            cornerRadius = CornerRadius(radius + growth),
            alpha = 0.3f,
        )
    }
}

internal fun DrawScope.drawMoodOrbJarGhost(
    geometry: MoodOrbJarGeometry,
    frame: MoodOrbJarFrame,
    art: MoodOrbJarArt,
) {
    frame.readTick()
    val alpha = frame.ghost[4]
    if (alpha <= 0f) {
        return
    }
    val diameter = MoodOrbJarLayout.ORB.toFloat() * 2 * geometry.s.toFloat() * ORB_IMAGE_SCALE.toFloat()
    scale(density, density, pivot = Offset.Zero) {
        val orbScale = frame.ghost[2]
        drawOrbSprite(
            image = moodImage(art, frame.ghost[3]),
            x = frame.ghost[0],
            y = frame.ghost[1],
            diameter = diameter,
            degrees = 0f,
            scaleX = orbScale,
            scaleY = orbScale,
            alpha = alpha,
        )
    }
}
