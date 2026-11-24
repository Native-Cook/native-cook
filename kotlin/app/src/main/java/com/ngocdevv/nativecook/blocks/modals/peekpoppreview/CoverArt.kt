package com.ngocdevv.nativecook.blocks.modals.peekpoppreview

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.max
import kotlin.math.min

/**
 * An article's cover, drawn in code: a three-stop gradient and a few flat shapes, laid out for a box of
 * [artWidth] x [artHeight] dp (the open page's hero). Every smaller cover is this same drawing scaled to cover its box.
 */
internal class CoverPainter(private val cover: Cover, private val artWidth: Float, private val artHeight: Float) {
    private val base = Brush.linearGradient(
        colorStops = arrayOf(0f to cover.colors[0], 0.55f to cover.colors[1], 1f to cover.colors[2]),
        start = Offset.Zero,
        end = Offset(artWidth, artHeight),
    )

    // Light from the top left, a little weight at the bottom: printed card stock rather than a flat fill.
    private val light = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.20f),
            0.45f to Color.White.copy(alpha = 0f),
            1f to Color.Black.copy(alpha = 0.14f),
        ),
        start = Offset(artWidth * 0.1f, 0f),
        end = Offset(artWidth * 0.7f, artHeight),
    )
    private val clip = Path()

    /** Draws the cover into a box given in dp, scaled to cover it, centred and clipped. */
    fun DrawScope.drawInBox(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        val d = density
        val s = max(width / artWidth, height / artHeight)
        val left = (x + width / 2f - artWidth * s / 2f) * d
        val top = (y + height / 2f - artHeight * s / 2f) * d
        clip.rewind()
        val r = min(radius, min(width, height) / 2f) * d
        clip.addRoundRect(RoundRect(x * d, y * d, (x + width) * d, (y + height) * d, CornerRadius(r)))
        clipPath(clip) {
            translate(left, top) {
                scale(s * d, s * d, pivot = Offset.Zero) { drawArt() }
            }
        }
    }

    private fun DrawScope.drawArt() {
        val area = Size(artWidth, artHeight)
        drawRect(base, size = area)
        val cx = artWidth / 2f
        val cy = artHeight / 2f
        val unit = artHeight
        for (shape in cover.shapes) {
            val color = shape.color.copy(alpha = shape.opacity)
            when (shape) {
                is CoverShape.Circle -> drawCircle(
                    color = color,
                    radius = shape.size * unit / 2f,
                    center = Offset(cx + shape.x * unit, cy + shape.y * unit),
                )
                // A React Native border lies inside the box.
                is CoverShape.Ring -> {
                    val stroke = shape.stroke * unit
                    drawCircle(
                        color = color,
                        radius = (shape.size * unit - stroke) / 2f,
                        center = Offset(cx + shape.x * unit, cy + shape.y * unit),
                        style = Stroke(stroke),
                    )
                }
                is CoverShape.Bar -> {
                    val w = shape.width * unit
                    val h = shape.height * unit
                    val center = Offset(cx + shape.x * unit, cy + shape.y * unit)
                    rotate(shape.turn, pivot = center) {
                        val r = min(shape.radius * unit, min(w, h) / 2f)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(r),
                        )
                    }
                }
            }
        }
        drawRect(light, size = area)
    }
}
