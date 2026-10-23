package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class GemOrbCollectLayout(
    val width: Float,
    val height: Float,
    val unit: Float,
    val scale: Float,
    val typeScale: Float,
    val gemX: Float,
    val gemY: Float,
    val gemRadius: Float,
    val tallyY: Float,
    val titleTop: Float,
    val bodyTop: Float,
    val capsuleLeft: Float,
    val capsuleTop: Float,
    val capsuleWidth: Float,
    val capsuleHeight: Float,
    val buttonLeft: Float,
    val buttonTop: Float,
    val buttonWidth: Float,
    val buttonHeight: Float,
) {
    val miniGemX = capsuleLeft + 24f * unit
    val miniGemY = capsuleTop + capsuleHeight / 2f

    companion object {
        fun create(width: Float, height: Float, safeTop: Float, safeBottom: Float, density: Float): GemOrbCollectLayout {
            val logicalWidth = width / density
            val logicalHeight = height / density
            val scale = min(logicalWidth / 402f, logicalHeight / 874f) * density
            val typeScale = max(scale / density, 0.86f) * density
            val usable = height - safeTop - safeBottom
            val radius = (104f * scale).roundToInt().toFloat()
            val gemY = (safeTop + usable * 0.296f).roundToInt().toFloat()
            val titleTop = (safeTop + usable * 0.599f).roundToInt().toFloat()
            val bodyTop = titleTop + (34f * typeScale).roundToInt() + 8f * density
            val capsuleHeight = 44f * density
            val capsuleWidth = 120f * density
            val capsuleTop = bodyTop + (24f * typeScale).roundToInt() + 24f * density
            val buttonHeight = (if (logicalHeight < 700f) 48f else 56f) * density
            return GemOrbCollectLayout(
                width = width,
                height = height,
                unit = density,
                scale = scale,
                typeScale = typeScale,
                gemX = width / 2f,
                gemY = gemY,
                gemRadius = radius,
                tallyY = gemY + radius + (54f * scale).roundToInt(),
                titleTop = titleTop,
                bodyTop = bodyTop,
                capsuleLeft = ((width - capsuleWidth) / 2f).roundToInt().toFloat(),
                capsuleTop = capsuleTop,
                capsuleWidth = capsuleWidth,
                capsuleHeight = capsuleHeight,
                buttonLeft = 24f * density,
                buttonTop = height - safeBottom - 24f * density - buttonHeight,
                buttonWidth = width - 48f * density,
                buttonHeight = buttonHeight,
            )
        }
    }
}
