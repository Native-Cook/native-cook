package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

// Android 8–12 use an explicit plain surface: ring holes, a selected ring,
// a readable pane and pressed pillows. There is no liquid neck or ripple.
internal class AuraPlainSurface {
    private val cushion = Path().apply { fillType = PathFillType.EvenOdd }
    private val nativeCushion = cushion.asAndroidPath()
    private val hero = android.graphics.Path().apply {
        addRoundRect(12f, 12f, 350f, 168f,
            floatArrayOf(32f, 32f, 32f, 32f, 40f, 40f, 40f, 40f), android.graphics.Path.Direction.CW)
    }
    private val shellColor = Color(0xFF17142A)
    private val heroColor = Color(0xFF8A4FD8)
    private val pillowColor = Color(0xFF2B273E)
    private val accentColor = Color(0xFF6330B9)
    private val ringColor = Color(0xFFB98FF5)
    private val paneFill = Color.White.copy(alpha = 0.045f)
    private val paneLine = Color.White.copy(alpha = 0.16f)
    private val line = Stroke(1f)
    private val ring = Stroke(3f)

    fun draw(scope: DrawScope, uniforms: AuraUniforms, drive: AuraDrive, width: Float) {
        with(scope) {
            val factor = width / 362f
            withTransform({ scale(factor, factor, Offset.Zero) }) {
                drawRoundRect(shellColor, size = Size(362f, 362f), cornerRadius = CornerRadius(44f))
                nativeCushion.set(hero)
                nativeCushion.fillType = android.graphics.Path.FillType.EVEN_ODD
                for (index in 0..4) {
                    val at = index * 4
                    val radius = uniforms.lipPoses[at + 2] / (width / density / 362f)
                    if (radius > 0) {
                        val x = uniforms.lipPoses[at] / (width / density / 362f)
                        val y = uniforms.lipPoses[at + 1] / (width / density / 362f)
                        nativeCushion.addCircle(x, y, radius, android.graphics.Path.Direction.CW)
                    }
                }
                // Clip the even-odd path to the cushion: holes extending below
                // its edge must not turn into filled discs outside the body.
                drawContext.canvas.save()
                drawContext.canvas.clipRect(12f, 12f, 350f, 168f)
                drawPath(cushion, heroColor)
                drawContext.canvas.restore()
                drawRoundRect(paneFill, Offset(12f, 201f), Size(338f, 80f), CornerRadius(24f))
                drawRoundRect(paneLine, Offset(12.5f, 201.5f), Size(337f, 79f), CornerRadius(23.5f), style = line)
                if (drive.chosen >= 0) {
                    val x = uniforms.dropPose[0] / (width / density / 362f)
                    val y = uniforms.dropPose[1] / (width / density / 362f)
                    val presence = uniforms.dropShape[2].coerceIn(0f, 1f)
                    val halfWidth = 22.5f * uniforms.dropShape[0]
                    val halfHeight = 22.5f * uniforms.dropShape[1]
                    drawOval(ringColor, Offset(x - halfWidth, y - halfHeight),
                        Size(halfWidth * 2, halfHeight * 2), alpha = presence, style = ring)
                }
                for (index in 0..3) {
                    val at = index * 4
                    val unit = width / density / 362f
                    val inset = uniforms.pressValues[index].coerceIn(0f, 1f) * 1.5f
                    val x = uniforms.actionRects[at] / unit + inset
                    val y = uniforms.actionRects[at + 1] / unit + inset
                    val actionWidth = uniforms.actionRects[at + 2] / unit - inset * 2
                    val height = uniforms.actionRects[at + 3] / unit - inset * 2
                    drawRoundRect(if (index == 3) accentColor else pillowColor,
                        Offset(x, y), Size(actionWidth, height), CornerRadius(height / 2))
                }
            }
        }
    }
}
