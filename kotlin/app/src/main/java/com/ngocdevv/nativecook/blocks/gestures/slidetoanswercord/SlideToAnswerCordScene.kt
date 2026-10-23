package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Native Canvas port of CallScene.tsx. The static wall gradient is rasterized once per size. */
internal class SlideToAnswerCordScene {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = makeOutline()
    private val gloss = makeGloss()
    private val crescents = makeCrescents()
    private val holes = makeHoles()
    private val backCoil = Path()
    private val frontCoil = Path()
    private var background: Bitmap? = null
    private var backgroundWidth = -1
    private var backgroundHeight = -1

    fun draw(
        canvas: AndroidCanvas,
        sim: SlideToAnswerCordSimulation.Sim,
        geometry: SlideToAnswerCordSimulation.Geometry,
        pixelScale: Float,
    ) {
        val width = geometry.w.toFloat()
        val height = geometry.h.toFloat()
        val destination = RectF(0f, 0f, width, height)
        val backdrop = ensureBackground(width, height, pixelScale)

        resetPaint(Paint.Style.FILL)
        paint.isFilterBitmap = true
        canvas.drawBitmap(backdrop, null, destination, paint)

        val center = SlideToAnswerCordSimulation.handsetPoint(
            sim,
            geometry,
            Tokens.HANDSET.comX,
            Tokens.HANDSET.comY,
        )
        val connected = sim.wConn.coerceIn(0.0, 1.0)
        val light = android.graphics.Color.argb(
            ((0.20 - 0.06 * connected) * 255).roundToInt(),
            255,
            (214 + 20 * connected).roundToInt(),
            (170 + 30 * connected).roundToInt(),
        )
        paint.shader = RadialGradient(
            (center.x - 40 * geometry.s).toFloat(),
            (center.y - 80 * geometry.s).toFloat(),
            (300 * geometry.s).toFloat(),
            intArrayOf(light, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(destination, paint)
        paint.shader = null

        val ring = SlideToAnswerCordSimulation.ringState(sim)
        drawShadow(canvas, sim, geometry, ring.buzz)
        buildCoil(sim, geometry)
        stroke(canvas, backCoil, Tokens.CORD.tube * geometry.s, Tokens.COLORS.cordBack)
        stroke(canvas, frontCoil, (Tokens.CORD.tube + 0.8) * geometry.s, Tokens.COLORS.cordEdge)
        stroke(canvas, frontCoil, Tokens.CORD.tube * geometry.s, Tokens.COLORS.cordFront)
        canvas.save()
        canvas.translate((-0.9 * geometry.s).toFloat(), (-0.9 * geometry.s).toFloat())
        stroke(canvas, frontCoil, 1.1 * geometry.s, Tokens.COLORS.cordHi)
        canvas.restore()
        drawHandset(canvas, sim, geometry, ring.buzz)
    }

    private fun ensureBackground(width: Float, height: Float, pixelScale: Float): Bitmap {
        val scale = pixelScale.coerceAtLeast(1f)
        val physicalWidth = ceil(width * scale).toInt().coerceAtLeast(1)
        val physicalHeight = ceil(height * scale).toInt().coerceAtLeast(1)
        if (background == null || backgroundWidth != physicalWidth || backgroundHeight != physicalHeight) {
            backgroundWidth = physicalWidth
            backgroundHeight = physicalHeight
            background = Bitmap.createBitmap(physicalWidth, physicalHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val target = AndroidCanvas(bitmap)
                target.scale(scale, scale)
                resetPaint(Paint.Style.FILL)
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height,
                    intArrayOf(
                        Tokens.COLORS.pageTop.toArgb(),
                        Tokens.COLORS.page.toArgb(),
                        Tokens.COLORS.pageBottom.toArgb(),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                target.drawRect(0f, 0f, width, height, paint)
                paint.shader = null
            }
        }
        return requireNotNull(background)
    }

    private fun drawShadow(
        canvas: AndroidCanvas,
        sim: SlideToAnswerCordSimulation.Sim,
        geometry: SlideToAnswerCordSimulation.Geometry,
        buzz: Double,
    ) {
        val last = Tokens.CORD.links
        val rotation = SlideToAnswerCordSimulation.handsetRotation(sim.th) + buzz
        canvas.save()
        canvas.translate(
            (sim.px[last] + 10 * geometry.s).toFloat(),
            (sim.py[last] + 22 * geometry.s).toFloat(),
        )
        canvas.rotate((rotation * 180 / PI).toFloat())
        canvas.scale((geometry.s * 1.03).toFloat(), (geometry.s * 1.03).toFloat())
        resetPaint(Paint.Style.FILL)
        paint.color = Tokens.COLORS.shadow.toArgb()
        paint.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(outline, paint)
        paint.maskFilter = null
        canvas.restore()
    }

    private fun drawHandset(
        canvas: AndroidCanvas,
        sim: SlideToAnswerCordSimulation.Sim,
        geometry: SlideToAnswerCordSimulation.Geometry,
        buzz: Double,
    ) {
        val handset = Tokens.HANDSET
        val last = Tokens.CORD.links
        val rotation = SlideToAnswerCordSimulation.handsetRotation(sim.th) + buzz
        canvas.save()
        canvas.translate(sim.px[last].toFloat(), sim.py[last].toFloat())
        canvas.rotate((rotation * 180 / PI).toFloat())
        canvas.scale(geometry.s.toFloat(), geometry.s.toFloat())

        resetPaint(Paint.Style.FILL)
        paint.shader = LinearGradient(
            (-handset.cupR).toFloat(),
            0f,
            handset.cupR.toFloat(),
            (handset.bottomCupY + handset.cupR).toFloat(),
            intArrayOf(
                Tokens.COLORS.ivoryHi.toArgb(),
                Tokens.COLORS.ivory.toArgb(),
                Tokens.COLORS.ivoryMid.toArgb(),
                Tokens.COLORS.ivoryLo.toArgb(),
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(outline, paint)
        paint.shader = null

        resetPaint(Paint.Style.STROKE)
        paint.strokeWidth = 6f
        paint.shader = LinearGradient(
            (-handset.cupR).toFloat(),
            0f,
            handset.cupR.toFloat(),
            0f,
            intArrayOf(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                slideToAnswerCordRgba(110, 88, 56, 0.35).toArgb(),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.INNER)
        canvas.drawPath(outline, paint)
        paint.maskFilter = null
        paint.shader = null

        stroke(canvas, outline, 1.0, Tokens.COLORS.ivoryEdge)
        drawCup(canvas, handset.topCupY)
        drawCup(canvas, handset.bottomCupY)

        resetPaint(Paint.Style.FILL)
        paint.color = Tokens.COLORS.hole.toArgb()
        canvas.drawPath(holes, paint)

        paint.color = Tokens.COLORS.specular.toArgb()
        paint.maskFilter = BlurMaskFilter(2.2f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(gloss, paint)
        paint.maskFilter = null

        resetPaint(Paint.Style.STROKE)
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Tokens.COLORS.specular.toArgb()
        paint.maskFilter = BlurMaskFilter(1.6f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(crescents, paint)
        paint.maskFilter = null
        canvas.restore()
    }

    private fun drawCup(canvas: AndroidCanvas, centerY: Double) {
        val radius = Tokens.HANDSET.inner.toFloat()
        resetPaint(Paint.Style.FILL)
        paint.shader = LinearGradient(
            -radius,
            centerY.toFloat() - radius,
            radius,
            centerY.toFloat() + radius,
            intArrayOf(
                Tokens.COLORS.cupLo.toArgb(),
                Tokens.COLORS.ivory.toArgb(),
                Tokens.COLORS.cupHi.toArgb(),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(0f, centerY.toFloat(), radius, paint)
        paint.shader = null
        strokeCircle(canvas, centerY, radius.toDouble(), 1.2, Tokens.COLORS.ivoryEdge)

        resetPaint(Paint.Style.STROKE)
        paint.strokeWidth = 1.4f
        paint.shader = LinearGradient(
            -radius,
            centerY.toFloat() - radius,
            radius,
            centerY.toFloat() + radius,
            intArrayOf(
                slideToAnswerCordRgba(255, 255, 255, 0.9).toArgb(),
                android.graphics.Color.TRANSPARENT,
                slideToAnswerCordRgba(255, 255, 255, 0.25).toArgb(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(0f, centerY.toFloat(), radius + 1.4f, paint)
        paint.shader = null
    }

    private fun buildCoil(
        simulation: SlideToAnswerCordSimulation.Sim,
        geometry: SlideToAnswerCordSimulation.Geometry,
    ) {
        backCoil.reset()
        frontCoil.reset()
        val cord = Tokens.CORD
        val turnsPerLink = cord.turns / cord.links
        val steps = ceil(turnsPerLink * 2 * cord.samples).toInt()
        var lastSide = 0
        for (index in 0 until cord.links) {
            val i0 = max(0, index - 1)
            val i3 = min(cord.links, index + 2)
            val x0 = simulation.px[i0]
            val y0 = simulation.py[i0]
            val x1 = simulation.px[index]
            val y1 = simulation.py[index]
            val x2 = simulation.px[index + 1]
            val y2 = simulation.py[index + 1]
            val x3 = simulation.px[i3]
            val y3 = simulation.py[i3]
            val length = hypot(x2 - x1, y2 - y1) / geometry.s
            val pitch = length / turnsPerLink
            val radiusSquared = (cord.wire / (2 * PI)) * (cord.wire / (2 * PI)) -
                (pitch / (2 * PI)) * (pitch / (2 * PI))
            val radius = min(cord.maxRadius, sqrt(max(radiusSquared, 1.2))) * geometry.s
            for (step in 0 until steps) {
                if (index > 0 && step == 0) continue
                val u = step.toDouble() / steps
                val u2 = u * u
                val u3 = u2 * u
                val centerX = 0.5 * (2 * x1 + (-x0 + x2) * u +
                    (2 * x0 - 5 * x1 + 4 * x2 - x3) * u2 +
                    (-x0 + 3 * x1 - 3 * x2 + x3) * u3)
                val centerY = 0.5 * (2 * y1 + (-y0 + y2) * u +
                    (2 * y0 - 5 * y1 + 4 * y2 - y3) * u2 +
                    (-y0 + 3 * y1 - 3 * y2 + y3) * u3)
                val tangentX = 0.5 * ((-x0 + x2) +
                    2 * (2 * x0 - 5 * x1 + 4 * x2 - x3) * u +
                    3 * (-x0 + 3 * x1 - 3 * x2 + x3) * u2)
                val tangentY = 0.5 * ((-y0 + y2) +
                    2 * (2 * y0 - 5 * y1 + 4 * y2 - y3) * u +
                    3 * (-y0 + 3 * y1 - 3 * y2 + y3) * u2)
                val tangentLength = hypot(tangentX, tangentY).takeIf { it > 0 } ?: 1.0
                val phase = 2 * PI * turnsPerLink * (index + u)
                val offset = radius * sin(phase)
                val dip = radius * 0.32 * cos(phase)
                val x = centerX - tangentY / tangentLength * offset + tangentX / tangentLength * dip
                val y = centerY + tangentX / tangentLength * offset + tangentY / tangentLength * dip
                val side = if (cos(phase) >= 0) 1 else -1
                val target = if (side > 0) frontCoil else backCoil
                if (side != lastSide) target.moveTo(x.toFloat(), y.toFloat()) else target.lineTo(x.toFloat(), y.toFloat())
                lastSide = side
            }
        }
    }

    private fun stroke(canvas: AndroidCanvas, source: Path, width: Double, color: Color) {
        resetPaint(Paint.Style.STROKE)
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color.toArgb()
        canvas.drawPath(source, paint)
    }

    private fun strokeCircle(
        canvas: AndroidCanvas,
        centerY: Double,
        radius: Double,
        width: Double,
        color: Color,
    ) {
        resetPaint(Paint.Style.STROKE)
        paint.strokeWidth = width.toFloat()
        paint.color = color.toArgb()
        canvas.drawCircle(0f, centerY.toFloat(), radius.toFloat(), paint)
    }

    private fun resetPaint(style: Paint.Style) {
        paint.style = style
        paint.alpha = 255
        paint.color = android.graphics.Color.WHITE
        paint.shader = null
        paint.maskFilter = null
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
    }

    private companion object {
        fun makeOutline(): Path {
            val handset = Tokens.HANDSET
            val angle = 40 * PI / 180
            val edgeX = handset.cupR * cos(angle)
            val edgeY = handset.cupR * sin(angle)
            val middle = (handset.topCupY + handset.bottomCupY) / 2
            return Path().apply {
                moveTo(edgeX.toFloat(), (handset.topCupY + edgeY).toFloat())
                cubicTo(
                    (handset.waist + 1).toFloat(),
                    (handset.topCupY + edgeY + 14).toFloat(),
                    handset.waist.toFloat(),
                    (middle - 22).toFloat(),
                    handset.waist.toFloat(),
                    middle.toFloat(),
                )
                cubicTo(
                    handset.waist.toFloat(),
                    (middle + 22).toFloat(),
                    (handset.waist + 1).toFloat(),
                    (handset.bottomCupY - edgeY - 14).toFloat(),
                    edgeX.toFloat(),
                    (handset.bottomCupY - edgeY).toFloat(),
                )
                arcTo(
                    RectF(
                        (-handset.cupR).toFloat(),
                        (handset.bottomCupY - handset.cupR).toFloat(),
                        handset.cupR.toFloat(),
                        (handset.bottomCupY + handset.cupR).toFloat(),
                    ),
                    -40f,
                    260f,
                    false,
                )
                cubicTo(
                    (-handset.waist - 1).toFloat(),
                    (handset.bottomCupY - edgeY - 14).toFloat(),
                    (-handset.waist).toFloat(),
                    (middle + 22).toFloat(),
                    (-handset.waist).toFloat(),
                    middle.toFloat(),
                )
                cubicTo(
                    (-handset.waist).toFloat(),
                    (middle - 22).toFloat(),
                    (-handset.waist - 1).toFloat(),
                    (handset.topCupY + edgeY + 14).toFloat(),
                    (-edgeX).toFloat(),
                    (handset.topCupY + edgeY).toFloat(),
                )
                arcTo(
                    RectF(
                        (-handset.cupR).toFloat(),
                        (handset.topCupY - handset.cupR).toFloat(),
                        handset.cupR.toFloat(),
                        (handset.topCupY + handset.cupR).toFloat(),
                    ),
                    140f,
                    260f,
                    false,
                )
                close()
            }
        }

        fun makeGloss(): Path {
            val handset = Tokens.HANDSET
            val middle = (handset.topCupY + handset.bottomCupY) / 2
            return Path().apply {
                moveTo((-handset.waist + 4).toFloat(), (handset.topCupY + 34).toFloat())
                cubicTo(
                    (-handset.waist + 2.5).toFloat(),
                    (middle - 20).toFloat(),
                    (-handset.waist + 2.5).toFloat(),
                    (middle + 20).toFloat(),
                    (-handset.waist + 4).toFloat(),
                    (handset.bottomCupY - 34).toFloat(),
                )
                cubicTo(
                    (-handset.waist + 6.5).toFloat(),
                    (middle + 20).toFloat(),
                    (-handset.waist + 6.5).toFloat(),
                    (middle - 20).toFloat(),
                    (-handset.waist + 4).toFloat(),
                    (handset.topCupY + 34).toFloat(),
                )
                close()
            }
        }

        fun makeCrescents(): Path = Path().apply {
            val radius = Tokens.HANDSET.cupR
            for (centerY in doubleArrayOf(Tokens.HANDSET.topCupY, Tokens.HANDSET.bottomCupY)) {
                addArc(
                    RectF(
                        (-radius + 3.5).toFloat(),
                        (centerY - radius + 3.5).toFloat(),
                        (radius - 3.5).toFloat(),
                        (centerY + radius - 3.5).toFloat(),
                    ),
                    200f,
                    58f,
                )
            }
        }

        fun makeHoles(): Path = Path().apply {
            fun ring(centerY: Double, radius: Double, count: Int, dot: Double, rotation: Double = 0.0) {
                repeat(count) { index ->
                    val angle = rotation + 2 * PI * index / count
                    addCircle(
                        (cos(angle) * radius).toFloat(),
                        (centerY + sin(angle) * radius).toFloat(),
                        dot.toFloat(),
                        Path.Direction.CW,
                    )
                }
            }
            addCircle(0f, Tokens.HANDSET.topCupY.toFloat(), 1.6f, Path.Direction.CW)
            ring(Tokens.HANDSET.topCupY, 6.0, 8, 1.3)
            ring(Tokens.HANDSET.topCupY, 11.5, 14, 1.3, 0.2)
            addCircle(0f, Tokens.HANDSET.bottomCupY.toFloat(), 2.2f, Path.Direction.CW)
            ring(Tokens.HANDSET.bottomCupY, 8.0, 6, 1.9, 0.5)
        }
    }
}
