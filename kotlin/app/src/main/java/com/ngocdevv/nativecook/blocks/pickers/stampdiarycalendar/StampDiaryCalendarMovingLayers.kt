package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar.StampDiaryCalendarMotion as M
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * The collage, the stamps in their cells and the moving stamp, recorded into one graphics layer
 * (the header's and the menu's smoked glass blur that same layer) and drawn from it. Reads the pose
 * state in the draw phase only, so a frame never recomposes.
 */
@Composable
internal fun StampDiaryCalendarMovingLayers(
    state: StampDiaryCalendarState,
    space: StampDiaryCalendarSpace,
    art: StampDiaryCalendarArtwork,
    painter: StampDiaryCalendarPainter,
    mainLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    // One blurred copy of each soft stamp (the sharp one, its blurred layer, and the masked bottom blur).
    val softTop = List(2) { rememberGraphicsLayer() }
    val softInner = List(2) { rememberGraphicsLayer() }
    val softOuter = List(2) { rememberGraphicsLayer() }
    val size = IntSize(space.widthPx.toInt(), space.heightPx.toInt())
    Spacer(
        modifier
            .fillMaxSize()
            .drawWithContent {
                mainLayer.record(size = size) {
                    drawStamps(state, space, art, painter, softTop, softInner, softOuter, size)
                }
                drawLayer(mainLayer)
            },
    )
}

private fun DrawScope.drawStamps(
    state: StampDiaryCalendarState,
    space: StampDiaryCalendarSpace,
    art: StampDiaryCalendarArtwork,
    painter: StampDiaryCalendarPainter,
    softTop: List<GraphicsLayer>,
    softInner: List<GraphicsLayer>,
    softOuter: List<GraphicsLayer>,
    size: IntSize,
) {
    val pose = state.pose
    val actorMemory = state.actorMemory
    val t = StampDiaryCalendarTheme
    painter.begin()
    val aaFlat = 0.7 / space.s

    fun stampArgs(q: StampDiaryCalendarQuad, bitmap: Bitmap, dim: Double) = StampDiaryCalendarStampArgs(
        projection = StampDiaryCalendarProjection.quad(q),
        halfWidth = q.w / 2 + 2, halfHeight = q.h / 2 + 2,
        art = bitmap, mark = null, w = q.w, h = q.h, aa = aaFlat, dim = dim, holes = M.holesOfQuad(q),
    )

    // The landing presses the collage in a little, around the landing point.
    val landing = M.landingQuad(pose.memory)
    val pivot = Offset(space.x(landing.x + landing.w / 2).toFloat(), space.y(landing.y + landing.h / 2).toFloat())
    clipRect(space.x(-60.0).toFloat(), space.y(-120.0).toFloat(), space.x(t.REF_WIDTH + 60).toFloat(), space.y(t.HERO_BOTTOM).toFloat()) {
        scale((1 - pose.pileNudge).toFloat(), pivot) {
            var soft = 0
            for (item in StampDiaryCalendarData.pile) {
                val bitmap = art.full[item.art]
                val args = stampArgs(item.quad, bitmap, pose.pileDim)
                if (item.blurBottom <= 0.0) {
                    drawIntoCanvas { painter.stamp(it.nativeCanvas, space, args) }
                } else {
                    // Depth of field: soft at the top, softer towards the bottom of the hero.
                    val top = item.quad.y + item.quad.h * item.soft
                    val a = softTop[soft]
                    val b = softInner[soft]
                    val c = softOuter[soft]
                    soft += 1
                    a.renderEffect = blurOf(item.blurTop * space.s)
                    a.record(size = size) { drawIntoCanvas { painter.stamp(it.nativeCanvas, space, args) } }
                    drawLayer(a)
                    b.renderEffect = blurOf(item.blurBottom * space.s)
                    b.record(size = size) { drawIntoCanvas { painter.stamp(it.nativeCanvas, space, args) } }
                    c.compositingStrategy = CompositingStrategy.Offscreen
                    c.record(size = size) {
                        drawLayer(b)
                        val y0 = space.y(top).toFloat()
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = y0,
                                endY = y0 + (50 * space.s).toFloat(),
                                tileMode = TileMode.Clamp,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    drawLayer(c)
                }
            }
        }
    }

    // The stamps in their cells, hidden while their copy is out.
    StampDiaryCalendarData.memories.forEachIndexed { index, mem ->
        if (pose.active == 1.0 && pose.memory == index && actorMemory == index) return@forEachIndexed
        val c = M.cellCenter(mem.day, M.MAY_2026)
        val args = StampDiaryCalendarStampArgs(
            projection = StampDiaryCalendarProjection(c.x, c.y, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1e6),
            halfWidth = mem.w / 2 + 2, halfHeight = mem.h / 2 + 2,
            art = art.small[mem.art], mark = null, w = mem.w, h = mem.h, aa = aaFlat, dim = 1.0, holes = M.holesOf(index),
        )
        drawIntoCanvas { painter.stamp(it.nativeCanvas, space, args) }
    }

    if (pose.active == 1.0 && pose.memory == actorMemory && pose.opacity > 0) {
        val mem = StampDiaryCalendarData.memories[actorMemory]
        val pad = M.STAMP_PAD * maxOf(pose.w, pose.h)
        val depth = M.Flight.PERSPECTIVE
        val shadowProjection = StampDiaryCalendarProjection(
            pose.cx + 5 + 9 * pose.height, pose.cy + 8 + 16 * pose.height, pose.skew,
            pose.rotX * 0.6, pose.rotY * 0.6, pose.rotZ, pose.lift * 0.97, pose.lift * 0.97, depth,
        )
        // The actor group multiplies the opacity once more, as in the Skia version.
        val shadowOpacity = pose.opacity * pose.opacity * (0.2 + 0.35 * pose.height)
        val args = StampDiaryCalendarStampArgs(
            projection = StampDiaryCalendarProjection(
                pose.cx, pose.cy, pose.skew, pose.rotX, pose.rotY, pose.rotZ, pose.lift * pose.sx, pose.lift * pose.sy, depth,
            ),
            halfWidth = pose.w / 2 + pad, halfHeight = pose.h / 2 + pad,
            art = art.full[mem.art], mark = StampDiaryCalendarPostmark.bitmap(mem.day, 5, 2026),
            w = pose.w, h = pose.h, aa = 0.7 / (space.s * maxOf(0.5, pose.lift)), dim = 1.0,
            holes = M.holesOf(actorMemory),
            shadow = pose.peelShadow, curl = pose.curl,
            sheenPos = pose.sheenPos, sheenAmt = pose.sheenAmt, inkWet = pose.inkWet, inkOn = pose.inkOn,
            inkSpread = pose.inkSpread, inkThreshold = pose.inkThreshold, opacity = pose.opacity,
        )
        drawIntoCanvas {
            painter.shadow(it.nativeCanvas, space, shadowProjection, pose.w, pose.h, 2 + 12 * pose.height, shadowOpacity)
            painter.stamp(it.nativeCanvas, space, args)
        }
    }
}

private fun blurOf(sigmaPx: Double) = if (sigmaPx <= 0.0) null else BlurEffect(sigmaPx.toFloat(), sigmaPx.toFloat(), TileMode.Decal)
