package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The expanding mask of the `MaskedView` (`expansionMask`): a rounded window, in pixels, that the
 * whole screen is clipped to. A data class, so a window that has not moved is the same shape and
 * the layer is not invalidated.
 */
internal data class SpectrumNewsCarouselWindow(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(left, top, right, bottom, CornerRadius(radius)))
}
