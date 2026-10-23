package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private data class GiftCardPickerCloudLook(
    val scale: Float,
    val opacity: Float,
    val text: Float = 1f,
)

private data class GiftCardPickerSphereBlob(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
    val phase: Float,
)

private val GiftCardPickerSphereBlobs = listOf(
    GiftCardPickerSphereBlob(7.5f, -47.4f, 100f, Color(0xFFC2D63A), 1f, 0f),
    GiftCardPickerSphereBlob(40.2f, 22.3f, 62f, Color(0xFF4F86E6), 0.85f, 1.7f),
    GiftCardPickerSphereBlob(-39.8f, 23f, 86f, Color(0xFFF08A2C), 0.95f, 3.2f),
    GiftCardPickerSphereBlob(-21.3f, 5.7f, 46f, Color(0xFFEA4A2A), 0.6f, 4.4f),
    GiftCardPickerSphereBlob(4f, 6.9f, 40f, Color(0xFFD0679F), 0.7f, 5.6f),
)

private fun giftCardPickerCloudA(stage: Float): GiftCardPickerCloudLook {
    val amount = giftCardPickerClamp01(stage)
    val settle = 0.243f
    val vanish = 0.471f
    val scale = if (amount <= settle) {
        giftCardPickerLerp(1f, 0.93f, giftCardPickerEaseOut(amount / settle))
    } else {
        giftCardPickerLerp(
            0.93f,
            0.2f,
            giftCardPickerEaseIn((amount - settle) / (vanish - settle)),
        )
    }
    val opacity = if (stage >= 1f) 0f else 1f - giftCardPickerClamp01((amount - settle) / (vanish - settle))
    val textOpacity = 1f - giftCardPickerClamp01((amount - settle) / ((vanish - settle) * 0.8f))
    return GiftCardPickerCloudLook(scale, opacity, textOpacity)
}

private fun giftCardPickerSphere(stage: Float): GiftCardPickerCloudLook {
    if (stage <= 1f) {
        val amount = giftCardPickerClamp01(stage)
        val progress = giftCardPickerClamp01((amount - 0.3f) / (1f - 0.3f))
        return GiftCardPickerCloudLook(
            scale = giftCardPickerLerp(0.35f, 1f, giftCardPickerEaseOut(progress)),
            opacity = giftCardPickerClamp01((amount - 0.3f) / 0.12f),
        )
    }

    val expansion = giftCardPickerClamp01(stage - 1f)
    val scale = when {
        expansion <= 0.217f -> giftCardPickerLerp(1f, 0.93f, giftCardPickerEaseOut(expansion / 0.217f))
        expansion <= 0.383f -> giftCardPickerLerp(
            0.93f,
            0.6f,
            giftCardPickerEaseIn((expansion - 0.217f) / (0.383f - 0.217f)),
        )
        else -> giftCardPickerLerp(0.6f, 0.5f, giftCardPickerClamp01((expansion - 0.383f) / 0.2f))
    }
    val opacity = 1f - giftCardPickerClamp01((expansion - 0.333f) / (0.55f - 0.333f))
    return GiftCardPickerCloudLook(scale, opacity)
}

private fun giftCardPickerCloudB(stage: Float): GiftCardPickerCloudLook {
    val expansion = giftCardPickerClamp01(stage - 1f)
    val scale = giftCardPickerLerp(
        0.3f,
        1f,
        giftCardPickerEaseOut((expansion - 0.333f) / (1f - 0.333f)),
    )
    val opacity = if (stage <= 1f) 0f else giftCardPickerClamp01((expansion - 0.333f) / (0.55f - 0.333f))
    return GiftCardPickerCloudLook(scale, opacity)
}

@Composable
internal fun GiftCardPickerBackdrop(
    index: Int,
    state: GiftCardPickerMotionState,
) {
    val stock = GiftCardPickerStocks[index]
    val image = remember(index) {
        giftCardPickerBuildField(GiftCardPickerFields.Backdrops[index])
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.backdrops[index].value }
            .background(stock.backdropTint),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(18.dp, BlurredEdgeTreatment.Unbounded),
        ) {
            drawImage(
                image = image,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

@Composable
internal fun GiftCardPickerOrb(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    sx: Float,
    ky: Float,
    reduceMotion: Boolean,
) {
    val imageA = remember { giftCardPickerBuildField(GiftCardPickerFields.CloudA) }
    val imageB = remember { giftCardPickerBuildField(GiftCardPickerFields.CloudB) }
    val cloudX = GiftCardPickerLayout.Discover.OrbCenterX * sx
    val cloudY = (GiftCardPickerLayout.Discover.OrbCenterY - GiftCardPickerLayout.Sheet.Top) * ky
    val sphereX = GiftCardPickerLayout.Discover.SphereCenterX * sx
    val sphereY = (GiftCardPickerLayout.Discover.SphereCenterY - GiftCardPickerLayout.Sheet.Top) * ky
    val sphereRadius = GiftCardPickerLayout.Discover.SphereRadius * sx
    val titleTransform = TransformOrigin(cloudX / width, cloudY / height)
    val sphereTransform = TransformOrigin(sphereX / width, sphereY / height)

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(GiftCardPickerTheme.SheetLight)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val look = if (reduceMotion) {
                        val opacity = 1f - giftCardPickerClamp01((state.stage.value - 0.35f) / 0.3f)
                        GiftCardPickerCloudLook(1f, opacity)
                    } else {
                        giftCardPickerCloudA(state.stage.value)
                    }
                    alpha = look.opacity
                    scaleX = look.scale
                    scaleY = look.scale
                    transformOrigin = titleTransform
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(16.dp, BlurredEdgeTreatment.Unbounded),
            ) {
                drawImage(
                    image = imageA,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val look = if (reduceMotion) {
                        GiftCardPickerCloudLook(
                            1f,
                            giftCardPickerClamp01((state.stage.value - 1.35f) / 0.3f),
                        )
                    } else {
                        giftCardPickerCloudB(state.stage.value)
                    }
                    alpha = look.opacity
                    scaleX = look.scale
                    scaleY = look.scale
                    transformOrigin = titleTransform
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(16.dp, BlurredEdgeTreatment.Unbounded),
            ) {
                drawImage(
                    image = imageB,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val look = if (reduceMotion) {
                        val stageValue = state.stage.value
                        val opacity = if (stageValue <= 1f) {
                            giftCardPickerClamp01((stageValue - 0.35f) / 0.3f)
                        } else {
                            1f - giftCardPickerClamp01((stageValue - 1.35f) / 0.3f)
                        }
                        GiftCardPickerCloudLook(1f, opacity)
                    } else {
                        giftCardPickerSphere(state.stage.value)
                    }
                    alpha = look.opacity
                    scaleX = look.scale
                    scaleY = look.scale
                    transformOrigin = sphereTransform
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(sphereX * density, sphereY * density)
                val radius = sphereRadius * density
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFFE9E9EE),
                            0.7f to Color(0xFFEAEAEE),
                            0.93f to Color(0xFFECECF0),
                            1f to Color(0xFFEEEEF1),
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )

                val clip = Path().apply {
                    addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                }
                clipPath(clip) {
                    val rotation = if (reduceMotion) 0f else {
                        -((state.timeSeconds - GiftCardPickerMotionSpec.SpinAtSeconds) /
                            GiftCardPickerMotionSpec.SpinPeriodSeconds) * 360f
                    }
                    withTransform({ rotate(rotation, center) }) {
                        GiftCardPickerSphereBlobs.forEach { blob ->
                            val angle = (state.timeSeconds / GiftCardPickerMotionSpec.DriftPeriodSeconds) *
                                GiftCardPickerTau + blob.phase
                            val blobCenter = Offset(
                                center.x + (blob.x * sx + if (reduceMotion) 0f else sin(angle) * 4f) * density,
                                center.y + (blob.y * sx + if (reduceMotion) 0f else cos(angle) * 4f) * density,
                            )
                            val blobRadius = blob.radius * sx * density
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to blob.color.copy(alpha = blob.alpha),
                                        0.4f to blob.color.copy(alpha = blob.alpha * 0.8f),
                                        0.72f to blob.color.copy(alpha = blob.alpha * 0.42f),
                                        1f to blob.color.copy(alpha = 0f),
                                    ),
                                    center = blobCenter,
                                    radius = blobRadius,
                                ),
                                radius = blobRadius,
                                center = blobCenter,
                            )
                        }
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color(0x00E9E9EE),
                                0.55f to Color(0x00E9E9EE),
                                0.86f to Color(0xD9EAEAEF),
                                1f to Color(0xFFEEEEF1),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
        }

        GiftCardPickerOrbCopy(state, width, height, ky, cloudX, cloudY, reduceMotion)
    }
}

@Composable
private fun GiftCardPickerOrbCopy(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    ky: Float,
    cloudX: Float,
    cloudY: Float,
    reduceMotion: Boolean,
) {
    val titleStyle = androidx.compose.ui.text.TextStyle(
        fontSize = GiftCardPickerLayout.Discover.TitleSize.sp,
        lineHeight = GiftCardPickerLayout.Discover.TitleLine.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = Color(0xFF4E4A50),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    val subStyle = androidx.compose.ui.text.TextStyle(
        fontSize = GiftCardPickerLayout.Discover.SubSize.sp,
        lineHeight = 22.sp,
        color = Color(0xFF4A4648),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val stage = state.stage.value
                val look = if (reduceMotion) {
                    val opacity = 1f - giftCardPickerClamp01((stage - 0.35f) / 0.3f)
                    GiftCardPickerCloudLook(1f, opacity, opacity)
                } else {
                    giftCardPickerCloudA(stage)
                }
                alpha = look.text
                scaleX = look.scale
                scaleY = scaleX
                transformOrigin = TransformOrigin(cloudX / width, cloudY / height)
                blendMode = androidx.compose.ui.graphics.BlendMode.ColorDodge
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            },
    ) {
        GiftCardPickerOrbText(
            text = "Discover your\ngift amount",
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .offset(y = ((GiftCardPickerLayout.Discover.TitleY[0] - 62f) * ky -
                    GiftCardPickerLayout.Discover.TitleLine / 2f).dp)
                .fillMaxWidth(),
            style = titleStyle,
        )
        GiftCardPickerOrbText(
            text = "Then you'll choose a company",
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .offset(y = ((GiftCardPickerLayout.Discover.SubY - 62f) * ky - 11f).dp)
                .fillMaxWidth(),
            style = subStyle,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val stage = state.stage.value
                val look = if (reduceMotion) {
                    GiftCardPickerCloudLook(1f, giftCardPickerClamp01((stage - 1.35f) / 0.3f))
                } else {
                    giftCardPickerCloudB(stage)
                }
                alpha = look.opacity
                scaleX = look.scale
                scaleY = look.scale
                transformOrigin = TransformOrigin(cloudX / width, cloudY / height)
                blendMode = androidx.compose.ui.graphics.BlendMode.ColorDodge
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            },
    ) {
        GiftCardPickerOrbText(
            text = "USD\u20093",
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .offset(y = ((GiftCardPickerLayout.Discover.AmountY - 62f) * ky - 28f).dp)
                .fillMaxWidth(),
            style = titleStyle.copy(fontSize = GiftCardPickerLayout.Discover.AmountSize.sp, lineHeight = 56.sp),
        )
        GiftCardPickerOrbText(
            text = "Now it’s time to choose\nyour gift stock",
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .offset(y = ((GiftCardPickerLayout.Discover.NowY[0] - 62f) * ky - 12f).dp)
                .fillMaxWidth(),
            style = subStyle,
        )
    }
}

@Composable
private fun GiftCardPickerOrbText(
    text: String,
    modifier: Modifier,
    style: androidx.compose.ui.text.TextStyle,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = 2,
    )
}

private fun giftCardPickerBuildField(field: Array<IntArray>): ImageBitmap {
    val height = field.size
    val width = field.first().size
    val source = IntArray(width * height)
    field.forEachIndexed { row, colors ->
        colors.copyInto(source, destinationOffset = row * width)
    }
    return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
