package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ForYouInsightsActor(
    assets: ForYouInsightsAssets,
    actorIndex: () -> Int,
    actorFrame: () -> ForYouInsightsActorFrame,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val actorClip = remember { Path() }
    Canvas(modifier) {
        val actor = actorFrame()
        if (actor.visible <= 0.0 || actor.width <= 0.0 || actor.height <= 0.0) {
            return@Canvas
        }
        val spread = 1.0 + 0.25 * actor.lift
        val width = actor.width.toFloat() * density * spread.toFloat()
        val height = actor.height.toFloat() * density * spread.toFloat()
        val centerX = (actor.x + actor.width / 2.0).toFloat() * density
        val centerY = (actor.y + actor.height / 2.0 +
            ForYouInsightsMotion.Depth.SHADOW_OFFSET_MAX * actor.lift).toFloat() * density
        drawOval(
            brush = Brush.radialGradient(
                0f to ForYouInsightsTheme.Colors.shadow,
                0.55f to ForYouInsightsTheme.Colors.shadow,
                1f to ForYouInsightsTheme.Colors.shadow.copy(alpha = 0f),
                center = Offset(centerX, centerY),
                radius = max(width, height) / 2f,
            ),
            topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
            size = Size(width, height),
            alpha = (ForYouInsightsMotion.Depth.SHADOW_OPACITY * actor.lift).toFloat(),
        )
    }

    Canvas(
        modifier.graphicsLayer {
            val actor = actorFrame()
            alpha = actor.visible.toFloat().coerceIn(0f, 1f)
            rotationX = actor.tiltX.toFloat()
            rotationY = actor.tiltY.toFloat()
            cameraDistance = ForYouInsightsMotion.Depth.PERSPECTIVE.toFloat() * density
            transformOrigin = TransformOrigin(
                pivotFractionX = if (size.width > 0f) {
                    ((actor.x + actor.width / 2.0) * density / size.width).toFloat()
                } else {
                    0.5f
                },
                pivotFractionY = if (size.height > 0f) {
                    ((actor.y + actor.height / 2.0) * density / size.height).toFloat()
                } else {
                    0.5f
                },
            )
            scaleX = actor.scale.toFloat()
            scaleY = actor.scale.toFloat()
        },
    ) {
        val actor = actorFrame()
        if (actor.visible <= 0.0 || actor.width <= 0.0 || actor.height <= 0.0) {
            return@Canvas
        }
        val left = actor.x.toFloat() * density
        val top = actor.y.toFloat() * density
        val width = actor.width.toFloat() * density
        val height = actor.height.toFloat() * density
        val radius = actor.radius.toFloat() * density
        actorClip.reset()
        actorClip.addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
                cornerRadius = CornerRadius(radius),
            ),
        )
        clipPath(actorClip) {
            val index = actorIndex().coerceIn(assets.layers.indices)
            val article = ForYouInsightsContent.articles[index]
            val side = max(actor.width, actor.height) *
                (1.0 + ForYouInsightsMotion.Depth.OVERSCAN * actor.separation)
            article.depths.forEachIndexed { layerIndex, depth ->
                val shift = ForYouInsightsMotion.layerShift(
                    depth = depth,
                    separation = actor.separation,
                    tiltX = actor.tiltX,
                    tiltY = actor.tiltY,
                    width = actor.width,
                )
                val layerSize = side * shift.scale
                val layerLeft = actor.x + actor.width / 2.0 - layerSize / 2.0 + shift.dx
                val layerTop = actor.y + actor.height / 2.0 - layerSize / 2.0 + shift.dy
                drawImage(
                    image = assets.layers[index][layerIndex],
                    dstOffset = IntOffset(
                        x = (layerLeft * density).roundToInt(),
                        y = (layerTop * density).roundToInt(),
                    ),
                    dstSize = IntSize(
                        width = (layerSize * density).roundToInt().coerceAtLeast(1),
                        height = (layerSize * density).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.High,
                )
            }
            val sheenAlpha = ForYouInsightsMotion.Depth.SHEEN_OPACITY *
                minOf(
                    1.0,
                    abs(actor.tiltY) / ForYouInsightsMotion.Depth.DRIFT_TILT_Y * 0.7 +
                        actor.lift * 0.6,
                ) * actor.separation
            val sheenX = actor.x - actor.width * (actor.tiltY / 30.0)
            drawRect(
                brush = Brush.linearGradient(
                    0.3f to ForYouInsightsTheme.Colors.white.copy(alpha = 0f),
                    0.5f to ForYouInsightsTheme.Colors.white.copy(alpha = 0.9f),
                    0.7f to ForYouInsightsTheme.Colors.white.copy(alpha = 0f),
                    start = Offset(sheenX.toFloat() * density, top),
                    end = Offset((sheenX + actor.width * 2.0).toFloat() * density, top + height),
                ),
                topLeft = Offset((sheenX - actor.width * 0.5).toFloat() * density, top),
                size = Size(width * 2f, height),
                alpha = sheenAlpha.toFloat().coerceIn(0f, 1f),
            )
        }
    }
}

@Composable
fun ForYouInsightsWarmLayers(
    assets: ForYouInsightsAssets,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        assets.layers.flatten().forEachIndexed { index, image ->
            drawImage(
                image = image,
                dstOffset = IntOffset(index, 0),
                dstSize = IntSize(1, 1),
                alpha = 0.004f,
            )
        }
    }
}
