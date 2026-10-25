package com.ngocdevv.nativecook.blocks.sheets.rollerblindsheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.max
import kotlin.math.sin

/*
 Below Android 13 there is no RuntimeShader: the room and the fabric drawn with gradients and paths
 in dp. The room keeps its window (sky gradient, sun glow, hills and trees, frame and mullions, the
 sill), the sunlight patch on the wall and the dimming; the fabric keeps its linen colour, the
 window's glow through it with the mullions' shadows, and the roll's shade. No weave texture.
 */
internal class RollerBlindFallback {
    private val hills = Path()
    private val trees = Path()
    private val patch = Path()

    private fun frameBars(g: BlindGeo, block: (x: Float, y: Float, w: Float, h: Float) -> Unit) {
        val u = g.unit.toFloat()
        val x = g.winX.toFloat()
        val y = g.winY.toFloat()
        val w = g.winW.toFloat()
        val h = g.winH.toFloat()
        val edge = 12 * u
        val bar = 9 * u
        block(x, y, w, edge)
        block(x, y + h - edge, w, edge)
        block(x, y, edge, h)
        block(x + w - edge, y, edge, h)
        block(x + w / 2 - bar / 2, y, bar, h)
        block(x, y + h * 0.46f - bar / 2, w, bar)
    }

    fun DrawScope.drawRoom(g: BlindGeo, dim: Float) {
        val u = g.unit.toFloat()
        val w = g.w.toFloat()
        val h = g.h.toFloat()
        val wx = g.winX.toFloat()
        val wy = g.winY.toFloat()
        val ww = g.winW.toFloat()
        val wh = g.winH.toFloat()
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE8DFD1), Color(0xFFD1C4B5)), startY = 0f, endY = h))
        // the sunlight patch, thrown down and to the left
        val drop = wy + wh + 26 * u
        patch.reset()
        patch.moveTo(wx + 40 * u, drop)
        patch.lineTo(wx + ww - 10 * u, drop)
        patch.lineTo(wx + ww - 10 * u - 0.35f * (h - drop), h)
        patch.lineTo(wx + 40 * u - 0.35f * (h - drop), h)
        patch.close()
        drawPath(patch, Brush.verticalGradient(listOf(Color(0x33FFE3A0), Color(0x0DFFE3A0)), startY = drop, endY = h), alpha = 1 - dim)
        // the sky, the sun, the hills and trees
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF80ADE0),
                0.62f to Color(0xFFD6DBE0),
                0.9f to Color(0xFFFCD1A3),
                startY = wy,
                endY = wy + wh,
            ),
            Offset(wx, wy),
            Size(ww, wh),
        )
        val sun = Offset(wx + ww * 0.72f, wy + wh * 0.58f)
        drawCircle(Brush.radialGradient(listOf(Color(0x99FFD694), Color(0x00FFD694)), sun, wh * 0.35f), wh * 0.35f, sun)
        drawCircle(Color(0xFFFFF8E0), 6 * u, sun)
        hills.reset()
        trees.reset()
        hills.moveTo(wx, wy + wh)
        trees.moveTo(wx, wy + wh)
        val n = 40
        for (i in 0..n) {
            val lx = i / n.toFloat()
            val hy = 0.80f + 0.05f * sin(lx * 7f + 1f) + 0.03f * sin(lx * 17f)
            val ty = 0.90f + 0.012f * sin(lx * 63f) + 0.02f * sin(lx * 40f)
            hills.lineTo(wx + lx * ww, wy + hy * wh)
            trees.lineTo(wx + lx * ww, wy + ty * wh)
        }
        hills.lineTo(wx + ww, wy + wh)
        hills.close()
        trees.lineTo(wx + ww, wy + wh)
        trees.close()
        drawPath(hills, Color(0xBFB3B5CC))
        drawPath(trees, Color(0xFF5C6E6B))
        // the frame, the mullions and the sill
        val wood = Brush.verticalGradient(listOf(Color(0xFFF7F3ED), Color(0xFFDBD6CF)), startY = wy, endY = wy + wh)
        frameBars(g) { x, y, bw, bh -> drawRect(wood, Offset(x, y), Size(bw, bh)) }
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFFAF7F2), Color(0xFFCCC7BF)), startY = wy + wh, endY = wy + wh + 12 * u),
            Offset(wx - 14 * u, wy + wh),
            Size(ww + 28 * u, 12 * u),
        )
        drawRect(
            Brush.verticalGradient(listOf(Color(0x40000000), Color(0x00000000)), startY = wy + wh + 12 * u, endY = wy + wh + 26 * u),
            Offset(wx - 14 * u, wy + wh + 12 * u),
            Size(ww + 28 * u, 14 * u),
        )
        // the room dims (and cools) as the blind closes
        if (dim > 0.001f) drawRect(Color(0xFF2E3236), alpha = dim * 0.38f)
        drawRect(Brush.radialGradient(0.5f to Color(0x00000000), 1f to Color(0x2E000000), center = Offset(w / 2, h / 2), radius = max(w, h) * 0.7f))
    }

    fun DrawScope.drawFabric(g: BlindGeo, hem: Float, rollR: Float) {
        val u = g.unit.toFloat()
        val x0 = g.x0.toFloat()
        val x1 = g.x1.toFloat()
        val ry = g.rollerY.toFloat()
        clipRect(x0, ry, x1, hem) {
            drawRect(Color(0xFFD5C7AF), Offset(x0, ry), Size(x1 - x0, hem - ry))
            // the window glowing through the weave, the mullions as soft shadows
            val wx = g.winX.toFloat()
            val wy = g.winY.toFloat()
            val ww = g.winW.toFloat()
            val wh = g.winH.toFloat()
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFF2E6D2), Color(0xFFF3D9B8)), startY = wy, endY = wy + wh),
                Offset(wx, wy),
                Size(ww, wh),
            )
            val sun = Offset(wx + ww * 0.72f, wy + wh * 0.58f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFF0D8), Color(0x00FFF0D8)), sun, wh * 0.45f), wh * 0.45f, sun)
            frameBars(g) { x, y, bw, bh -> drawRect(Color(0x408A7458), Offset(x, y), Size(bw, bh)) }
            // the top of the fabric in the roll's shadow
            drawRect(
                Brush.verticalGradient(listOf(Color(0x59000000), Color(0x00000000)), startY = ry + rollR, endY = ry + rollR + 24 * u),
                Offset(x0, ry),
                Size(x1 - x0, rollR + 24 * u),
            )
        }
    }
}
