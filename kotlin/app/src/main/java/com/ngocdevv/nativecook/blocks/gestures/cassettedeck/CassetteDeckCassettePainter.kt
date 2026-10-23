package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Chassis
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Material
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Pager
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Transport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One cassette with everything inside it, drawn as a flat plane under a perspective transform (RN `Cassette`).
 * The matrix is the source's `[perspective, translateX, translateY, rotateY]` about the cassette's centre,
 * so the sliding cassette keeps the same vanishing point the React Native view has. Dp units.
 */
internal class CassetteDeckCassettePainter(
    private val geometry: CassetteDeckGeometry,
    private val assets: CassetteDeckAssets,
) {
    private val s = geometry.s.toFloat()
    private val oy = geometry.oy.toFloat()
    private val frame = CassetteDeckTheme.cassette
    private val cassetteWidth = (frame.w * s).toFloat()
    private val cassetteHeight = (frame.h * s).toFloat()

    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val rect = RectF()
    private val shapeRadii = FloatArray(8)

    // RN `styles.edge` has a literal top of 6 and bottom of 10 (not scaled).
    private val edgeTop = 6f
    private val edgeBottom = cassetteHeight - 10f
    private val edgeWidth = (Material.edge * s).toFloat()
    private val edgeRadius = 6f * s

    private val titlePaint = textPaint(Typeface.DEFAULT_BOLD, CassetteDeckTheme.title.size, CassetteDeckTheme.title.tracking, CassetteDeckTheme.labelInk)
    private val artistPaint = textPaint(Typeface.DEFAULT, CassetteDeckTheme.artist.size, CassetteDeckTheme.artist.tracking, CassetteDeckTheme.labelMute)
    private val wordmarkPaint = textPaint(blackTypeface(), CassetteDeckTheme.wordmarkLabel.size, CassetteDeckTheme.wordmarkLabel.tracking, CassetteDeckTheme.wordmark)

    // The sheen's gradient in unit space of its own box (iOS CAGradientLayer semantics: start (0, 0.2), end (1, 0.8)).
    private val sheenShader = LinearGradient(
        0f, 0.2f, 1f, 0.8f, CassetteDeckTheme.sheen, CassetteDeckTheme.sheenStops, Shader.TileMode.CLAMP,
    )
    private val sheenMatrix = Matrix()

    fun draw(canvas: Canvas, sim: CassetteDeckSim, slot: Int) {
        val x = CassetteDeckSim.slotX(slot, sim.p)
        // A cassette a whole screen away is off screen (the edges are a few points wide).
        if (abs(x) >= 1.0) return

        val yawDegrees = if (sim.reduced) 0.0 else max(-Pager.rotateMax, min(Pager.rotateMax, -sim.pv * Pager.rotatePerSpeed))
        buildMatrix(x, yawDegrees, (sim.dip * Chassis.dipTravel + sim.rattle * Chassis.rattleTravel))
        val yaw = if (sim.reduced) 0.0 else max(-1.0, min(1.0, (-sim.pv * Pager.rotatePerSpeed) / Pager.rotateMax))

        val count = canvas.save()
        canvas.concat(matrix)
        if (sim.reduced) {
            // The cross-fade of Reduce Motion (RN `opacity: 0.35 + 0.65 * fade`).
            val alpha = ((0.35 + 0.65 * sim.fade) * 255.0).roundToInt().coerceIn(0, 255)
            canvas.saveLayerAlpha(-8f * s, -8f * s, cassetteWidth + 8f * s, cassetteHeight + 8f * s, alpha)
        }

        drawEdges(canvas, yaw)
        canvas.drawBitmap(assets.cassette, null, rect.apply { set(0f, 0f, cassetteWidth, cassetteHeight) }, bitmapPaint)
        drawTapePacks(canvas, sim, slot)
        drawReels(canvas, sim, slot)
        drawLabel(canvas, slot)
        drawSheen(canvas, sim, x, yaw)
        canvas.restoreToCount(count)
    }

    // RN `transform`: [{ perspective: 900 s }, { translateX: x width }, { translateY }, { rotateY }] about the centre.
    private fun buildMatrix(x: Double, yawDegrees: Double, travelY: Double) {
        val yaw = Math.toRadians(yawDegrees)
        val perspective = 900.0 * s
        matrixValues[Matrix.MSCALE_X] = cos(yaw).toFloat()
        matrixValues[Matrix.MSKEW_X] = 0f
        matrixValues[Matrix.MTRANS_X] = (x * geometry.width).toFloat()
        matrixValues[Matrix.MSKEW_Y] = 0f
        matrixValues[Matrix.MSCALE_Y] = 1f
        matrixValues[Matrix.MTRANS_Y] = (travelY * s).toFloat()
        // A point's depth is -u sin(yaw), so w = 1 - z / d = 1 + u sin(yaw) / d.
        matrixValues[Matrix.MPERSP_0] = (sin(yaw) / perspective).toFloat()
        matrixValues[Matrix.MPERSP_1] = 0f
        matrixValues[Matrix.MPERSP_2] = 1f
        matrix.setValues(matrixValues)
        matrix.preTranslate(-cassetteWidth / 2f, -cassetteHeight / 2f)
        matrix.postTranslate(frame.x.toFloat() * s + cassetteWidth / 2f, oy + frame.y.toFloat() * s + cassetteHeight / 2f)
    }

    // The shell's side faces show as it yaws: scaled from the edge that touches the cassette.
    private fun drawEdges(canvas: Canvas, yaw: Double) {
        val left = max(0.0, yaw).toFloat()
        if (left > 0f) {
            val x0 = -Material.edge.toFloat() * s + s
            drawEdge(canvas, x0, left, pivotX = x0 + edgeWidth, CassetteDeckTheme.edgeL, roundLeft = true)
        }
        val right = max(0.0, -yaw).toFloat()
        if (right > 0f) {
            val x0 = cassetteWidth - (-Material.edge.toFloat() * s + 3f * s) - edgeWidth
            drawEdge(canvas, x0, right, pivotX = x0, CassetteDeckTheme.edgeR, roundLeft = false)
        }
    }

    private fun drawEdge(canvas: Canvas, x0: Float, scaleX: Float, pivotX: Float, colors: IntArray, roundLeft: Boolean) {
        rect.set(x0, edgeTop, x0 + edgeWidth, edgeBottom)
        for (i in shapeRadii.indices) shapeRadii[i] = 0f
        val corners = if (roundLeft) intArrayOf(0, 1, 6, 7) else intArrayOf(2, 3, 4, 5)
        for (i in corners) shapeRadii[i] = edgeRadius
        shape.reset()
        shape.addRoundRect(rect, shapeRadii, Path.Direction.CW)
        edgePaint.shader = LinearGradient(x0, 0f, x0 + edgeWidth, 0f, colors, null, Shader.TileMode.CLAMP)
        val count = canvas.save()
        canvas.scale(scaleX, 1f, pivotX, 0f)
        canvas.drawPath(shape, edgePaint)
        canvas.restoreToCount(count)
    }

    // RN :401-412: the dark band through the label, with a tape pack around each hub (scaled from the pack's centre).
    private fun drawTapePacks(canvas: Canvas, sim: CassetteDeckSim, slot: Int) {
        val window = CassetteDeckTheme.window
        rect.set((window.x * s).toFloat(), (window.y * s).toFloat(), ((window.x + window.w) * s).toFloat(), ((window.y + window.h) * s).toFloat())
        val radius = (window.h / 2.0 * s).toFloat()
        val count = canvas.save()
        shape.reset()
        shape.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(shape)
        for (i in 0 until 2) {
            val tape = CassetteDeckSim.tapeRadius(if (i == 0) 1.0 - sim.prog[slot] else sim.prog[slot])
            val packScale = (tape / CassetteDeckTheme.tapePack.max).toFloat()
            val outer = (tape * s).toFloat()
            val cx = (CassetteDeckTheme.reels[i].cx * s).toFloat()
            val cy = (CassetteDeckTheme.reels[i].cy * s).toFloat()
            fillPaint.shader = null
            fillPaint.color = CassetteDeckTheme.tape
            canvas.drawCircle(cx, cy, outer, fillPaint)
            // A 1 pt border inside the box, scaled with it.
            val border = s * packScale
            ringPaint.color = CassetteDeckTheme.tapeEdge
            ringPaint.strokeWidth = border
            canvas.drawCircle(cx, cy, outer - border / 2f, ringPaint)
        }
        canvas.restoreToCount(count)
    }

    // RN :413-434: ghost hubs trail the reel while winding, then the reels themselves.
    private fun drawReels(canvas: Canvas, sim: CassetteDeckSim, slot: Int) {
        val omega = abs(sim.w[slot]) * Transport.baseOmega
        val blur = if (sim.reduced) 0.0 else max(0.0, min(1.0, (omega - Material.blurFrom) / Material.blurSpan))
        for (reel in 0 until 2) {
            if (blur <= 0.0) break
            for (n in 1..2) {
                val lag = (sim.w[slot] * Transport.baseOmega * Material.shutter * n) / Material.ghosts
                drawReel(canvas, reel, sim.ang[slot * 2 + reel] - lag, blur * (0.55 - 0.15 * n))
            }
        }
        for (reel in 0 until 2) drawReel(canvas, reel, sim.ang[slot * 2 + reel], 1.0)
    }

    private fun drawReel(canvas: Canvas, reel: Int, radians: Double, opacity: Double) {
        val alpha = (opacity * 255.0).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val half = (CassetteDeckTheme.REEL_SIZE / 2.0 * s).toFloat()
        val cx = (CassetteDeckTheme.reels[reel].cx * s).toFloat()
        val cy = (CassetteDeckTheme.reels[reel].cy * s).toFloat()
        bitmapPaint.alpha = alpha
        val count = canvas.save()
        canvas.rotate(Math.toDegrees(radians).toFloat(), cx, cy)
        canvas.drawBitmap(assets.reel, null, rect.apply { set(cx - half, cy - half, cx + half, cy + half) }, bitmapPaint)
        canvas.restoreToCount(count)
        bitmapPaint.alpha = 255
    }

    // RN :435-455: the cover, the title, the artist and the wordmark.
    private fun drawLabel(canvas: Canvas, slot: Int) {
        val track = CassetteDeckTheme.tracks[slot]
        val cover = CassetteDeckTheme.cover
        rect.set(
            (cover.x * s).toFloat(), (cover.y * s).toFloat(),
            ((cover.x + cover.size) * s).toFloat(), ((cover.y + cover.size) * s).toFloat(),
        )
        val count = canvas.save()
        shape.reset()
        shape.addRoundRect(rect, (cover.radius * s).toFloat(), (cover.radius * s).toFloat(), Path.Direction.CW)
        canvas.clipPath(shape)
        canvas.drawBitmap(assets.covers[track.cover], null, rect, bitmapPaint)
        canvas.restoreToCount(count)

        val title = CassetteDeckTheme.title
        canvas.drawText(track.title, (title.x * s).toFloat(), baseline(title.y, title.size), titlePaint)
        val artist = CassetteDeckTheme.artist
        canvas.drawText(track.artist, (artist.x * s).toFloat(), baseline(artist.y, artist.size), artistPaint)
        val wordmark = CassetteDeckTheme.wordmarkLabel
        val right = cassetteWidth - (wordmark.right * s).toFloat()
        canvas.drawText(CassetteDeckTheme.BRAND, right - wordmarkPaint.measureText(CassetteDeckTheme.BRAND), baseline(wordmark.y, wordmark.size), wordmarkPaint)
    }

    // RN's Text `top` is the top of the line box. The reference draws the system font, whose ascent is 0.952 em,
    // so the baseline sits that far below `top`; Roboto's own padding would put it about 1.7 pt lower at 16 pt.
    private fun baseline(top: Double, size: Double): Float = ((top + size * SYSTEM_ASCENT) * s).toFloat()

    // RN :456-466: a sheen fixed in the room slides over the plastic as the cassette moves.
    private fun drawSheen(canvas: Canvas, sim: CassetteDeckSim, x: Double, yaw: Double) {
        val opacity = if (sim.reduced) 0.0 else min(1.0, abs(x) * Material.sheenGain + abs(yaw))
        val alpha = (opacity * 255.0).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        // The clip box: left 1, top 1, right 3, bottom 6, radius 7.
        rect.set(s, s, cassetteWidth - 3f * s, cassetteHeight - 6f * s)
        val boxHeight = rect.height()
        val count = canvas.save()
        shape.reset()
        shape.addRoundRect(rect, 7f * s, 7f * s, Path.Direction.CW)
        canvas.clipPath(shape)
        // The gradient box: absolute inside the clip, left -0.5 cassette widths, 2 widths wide, full height.
        val boxLeft = rect.left - 0.5f * cassetteWidth + (-x * Material.sheenTravel * frame.w * s).toFloat()
        val boxWidth = 2f * cassetteWidth
        sheenMatrix.setScale(boxWidth, boxHeight)
        sheenMatrix.postTranslate(boxLeft, rect.top)
        sheenShader.setLocalMatrix(sheenMatrix)
        sheenPaint.shader = sheenShader
        sheenPaint.alpha = alpha
        canvas.drawRect(boxLeft, rect.top, boxLeft + boxWidth, rect.top + boxHeight, sheenPaint)
        canvas.restoreToCount(count)
    }

    private fun textPaint(typeface: Typeface, size: Double, tracking: Double, color: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = (size * s).toFloat()
            // RN letterSpacing is in points; Android's is in em.
            letterSpacing = (tracking / size).toFloat()
            this.color = color
        }
    }

    private fun blackTypeface(): Typeface {
        // The source's weight 900; below API 28 only bold exists.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 900, false) else Typeface.DEFAULT_BOLD
    }

    private companion object {
        const val SYSTEM_ASCENT = 0.952
    }
}
