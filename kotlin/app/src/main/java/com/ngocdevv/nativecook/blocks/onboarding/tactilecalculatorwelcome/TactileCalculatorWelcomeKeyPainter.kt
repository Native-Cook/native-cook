package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Draws one key, the two `Svg` layers of `KeyButton`: the body (rim ring, lit and ink walls,
 * pinstripes, foot outline) and, over it, the cap that sinks with the pressure (face, glint and the
 * raised symbol). Everything is in the 216 by 150 key units of the SVG's `viewBox`, so line widths
 * scale with the key. One instance per key; it keeps the three paths that change with the pressure so
 * a frame allocates nothing.
 */
internal class TactileCalculatorWelcomeKeyPainter(private val spec: TactileCalculatorWelcomeKeySpec) {
    private val geometry = TactileCalculatorWelcomeGeometry
    private val litWall = Path()
    private val inkWall = Path()
    private val stripePath = Path()
    private val parts = geometry.glyphs.getValue(spec.operation)

    /**
     * Draws the key with its plate's origin at the scope's origin. [pxPerUnit] is the size of one
     * key unit in pixels (`scale` of the React Native file times the display density) and [pressure]
     * is 0 at rest and 1 with the cap fully down.
     */
    fun draw(scope: DrawScope, pxPerUnit: Float, pressure: Double) {
        val travel = TRAVEL * pressure
        buildWall(litWall, geometry.litChain, travel)
        buildWall(inkWall, geometry.inkChain, travel)
        buildStripes(stripePath, travel)
        with(scope) {
            scale(pxPerUnit, pxPerUnit, pivot = Offset.Zero) {
                drawBody(pressure)
                // The cap rides down by the travel, in key units, because the whole key is scaled.
                translate(left = 0f, top = travel.toFloat()) {
                    drawCap()
                }
            }
        }
    }

    /** The rim ring, the walls, the pinstripes and the foot outline: the first `Svg`. */
    private fun DrawScope.drawBody(pressure: Double) {
        drawPath(geometry.ringPath, OUTLINE, style = RING_STROKE)
        drawPath(litWall, spec.wall)
        drawPath(inkWall, INK)
        // The stripes fade out as the cap comes down over them (`strokeOpacity: 1 - pressure`).
        drawPath(stripePath, INK.copy(alpha = (1.0 - pressure).toFloat()), style = STRIPE_STROKE)
        drawPath(geometry.bodyFootPath, OUTLINE, style = OUTLINE_STROKE)
    }

    /** The face, its glint and the symbol: the second `Svg`, moved down with the pressure. */
    private fun DrawScope.drawCap() {
        drawPath(geometry.facePath, spec.face)
        drawPath(geometry.facePath, OUTLINE, style = OUTLINE_STROKE)
        drawPath(geometry.faceGlintPath, GLINT, style = GLINT_STROKE)
        drawGlyph()
    }

    /** `Glyph`: walls first, then the tops drawn twice so overlapping bars read as one outline. */
    private fun DrawScope.drawGlyph() {
        for (part in parts) {
            for (lit in part.lit) {
                drawPath(lit, spec.glyphWall)
                drawPath(lit, OUTLINE, style = GLYPH_WALL_STROKE)
            }
            for (ink in part.ink) {
                drawPath(ink, INK)
                drawPath(ink, OUTLINE, style = GLYPH_WALL_STROKE)
            }
        }
        for (part in parts) {
            drawPath(part.top, OUTLINE)
            drawPath(part.top, OUTLINE, style = GLYPH_TOP_STROKE)
        }
        for (part in parts) {
            drawPath(part.top, spec.face)
        }
    }

    /** `chainWall`: the chain at the cap's height, back along the same chain at ground level. */
    private fun buildWall(path: Path, chain: DoubleArray, top: Double) {
        path.rewind()
        var i = 0
        while (i < chain.size) {
            val x = chain[i].toFloat()
            val y = (chain[i + 1] + top).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i += 2
        }
        i = chain.size - 2
        while (i >= 0) {
            path.lineTo(chain[i].toFloat(), (chain[i + 1] + WALL).toFloat())
            i -= 2
        }
        path.close()
    }

    /** The pinstripes: a vertical line from the cap's height down to the foot at each stripe. */
    private fun buildStripes(path: Path, top: Double) {
        path.rewind()
        for (stripe in geometry.stripes) {
            path.moveTo(stripe.x.toFloat(), (stripe.y + top).toFloat())
            path.lineTo(stripe.x.toFloat(), (stripe.y + WALL).toFloat())
        }
    }

    private companion object {
        val INK = TactileCalculatorWelcomePalette.Ink
        val OUTLINE = TactileCalculatorWelcomePalette.Outline
        val GLINT = Color.White.copy(alpha = 0.34f)

        /** The stroke styles of the SVG, in key units. OUTLINE_STROKE is the 1.4 outline of the foot and of the face. */
        val RING_STROKE = Stroke(width = 1.1f)
        val STRIPE_STROKE = Stroke(width = 1.8f)
        val OUTLINE_STROKE = Stroke(width = 1.4f, join = StrokeJoin.Round)
        val GLINT_STROKE = Stroke(width = 1.5f, cap = StrokeCap.Round)
        val GLYPH_WALL_STROKE = Stroke(width = 1.3f, join = StrokeJoin.Round)
        val GLYPH_TOP_STROKE = Stroke(width = 2.6f, join = StrokeJoin.Round)

        /** `WALL`: the body's wall height. */
        const val WALL = TactileCalculatorWelcomeGeometry.DEPTH

        /** `TRAVEL`: how far the cap sinks, a little short of the wall so a rim of it stays visible. */
        const val TRAVEL = WALL - 0.6
    }
}
