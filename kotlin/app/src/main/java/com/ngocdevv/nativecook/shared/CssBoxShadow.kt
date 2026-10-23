package com.ngocdevv.nativecook.shared

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp

/**
 * One CSS `box-shadow` layer (`0 offsetY blur color`, no spread) under a rounded box, drawn
 * outside the box only, like React Native's `boxShadow`. CSS blur radius is twice the sigma.
 * Stack the modifier for a multi-layer shadow; `strength` scales the colour's alpha per draw. From API 28; older devices draw no shadow.
 */
internal fun Modifier.cssBoxShadow(color: Color, offsetY: Dp, blur: Dp, radius: Dp, strength: () -> Float = { 1f }): Modifier = composed {
    val shadow = remember(color, blur) { SoftRoundRect(color, sigma = blur.value / 2f) }
    drawBehind {
        val widthDp = size.width / density
        val heightDp = size.height / density
        val box = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius.toPx())))
        }
        clipPath(box, ClipOp.Difference) {
            scale(density, density, pivot = Offset.Zero) {
                drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, offsetY.value, widthDp, heightDp, radius.value, strength()) }
            }
        }
    }
}
