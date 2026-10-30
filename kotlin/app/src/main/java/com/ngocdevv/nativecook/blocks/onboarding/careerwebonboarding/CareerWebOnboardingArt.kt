package com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.Frame
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.links
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.linkOn
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.C
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.Copy
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.ROLE_SIZE
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.ROPE_W
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.CTA_SIZE
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.arrow
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.button
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.marker
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.nodes
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.pops
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.title

internal data class CareerWebOnboardingLabel(
    val text: String,
    val typeface: Typeface,
    val size: Double,
    val width: Double,
    val advances: FloatArray,
)

internal data class CareerWebOnboardingResources(
    val lines: Array<CareerWebOnboardingLabel>,
    val dream: CareerWebOnboardingLabel,
    val roles: Array<Array<CareerWebOnboardingLabel>?>,
    val cta: CareerWebOnboardingLabel,
    val people: List<Bitmap>,
)

internal data class CareerWebOnboardingLive(
    val px: DoubleArray,
    val py: DoubleArray,
    val grow: DoubleArray,
    val vib: DoubleArray,
    val hold: Int,
    val press: Double,
    val shoot: Double,
    val pick: DoubleArray,
)

/** Native Canvas renderer matching cwo-draw.ts in the 402 × 874 reference space. */
internal class CareerWebOnboardingArt(context: Context) {
    private val semi = Typeface.createFromAsset(context.assets, "careerwebonboarding/careerwebonboarding_Poppins-SemiBold.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "careerwebonboarding/careerwebonboarding_Poppins-Medium.ttf")
    private val people = listOf("a", "b", "c").map { suffix ->
        context.assets.open("careerwebonboarding/careerwebonboarding_person-$suffix.webp").use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream))
        }
    }
    val resources: CareerWebOnboardingResources = makeResources()

    private fun makeLabel(typeface: Typeface, text: String, size: Double): CareerWebOnboardingLabel {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size.toFloat()
            isSubpixelText = true
        }
        val advances = FloatArray(text.length)
        for (index in text.indices) {
            val glyphWidth = FloatArray(1)
            p.getTextWidths(text, index, index + 1, glyphWidth)
            advances[index] = glyphWidth[0]
        }
        return CareerWebOnboardingLabel(text, typeface, size, advances.sum().toDouble(), advances)
    }

    private fun makeResources(): CareerWebOnboardingResources {
        val probe = makeLabel(semi, Copy.lines[0], 100.0)
        val size = 100.0 * title.width0 / probe.width
        return CareerWebOnboardingResources(
            lines = Copy.lines.map { makeLabel(semi, it, size) }.toTypedArray(),
            dream = makeLabel(semi, Copy.dream, size),
            roles = nodes.map { node -> node.label?.map { makeLabel(medium, it, ROLE_SIZE) }?.toTypedArray() }.toTypedArray(),
            cta = makeLabel(semi, Copy.cta, CTA_SIZE),
            people = people,
        )
    }

    private fun alphaColor(base: Int, alpha: Double = 1.0): Int {
        return (alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255) shl 24 or (base and 0x00FFFFFF)
    }

    private fun paint(base: Int = C.white, alpha: Double = 1.0): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = alphaColor(base, alpha)
    }

    private fun drawLabel(canvas: Canvas, label: CareerWebOnboardingLabel, x: Double, centerY: Double, anchor: Double, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = label.typeface
            textSize = label.size.toFloat()
            textAlign = Paint.Align.LEFT
            isSubpixelText = true
            this.color = color
        }
        var advance = 0.0
        val origin = x - label.width * anchor
        val baseline = (centerY + label.size * 0.36).toFloat()
        for (index in label.text.indices) {
            canvas.drawText(label.text, index, index + 1, (origin + advance).toFloat(), baseline, p)
            advance += label.advances[index]
        }
    }

    private fun drawTitle(canvas: Canvas, resources: CareerWebOnboardingResources, frame: Frame) {
        val white = paint(C.white)
        resources.lines.forEachIndexed { index, label ->
            drawLabel(canvas, label, title.x, title.lines[index], 0.0, white.color)
        }
        val dreamX = title.x + resources.lines[2].width
        drawLabel(canvas, resources.dream, dreamX + 8.0, title.lines[2], 0.0, white.color)
        if (frame.mark <= 0.002) return

        val k = frame.mark
        val endX = marker.x0 + (marker.x1 - marker.x0) * frame.mark
        val band = Path().apply {
            moveTo(marker.x0.toFloat(), marker.top0.toFloat())
            lineTo(endX.toFloat(), (marker.top0 + (marker.top1 - marker.top0) * k).toFloat())
            lineTo(endX.toFloat() + 3.0f, ((marker.top0 + (marker.top1 - marker.top0) * k + marker.bot0 + (marker.bot1 - marker.bot0) * k) / 2.0).toFloat())
            lineTo(endX.toFloat(), (marker.bot0 + (marker.bot1 - marker.bot0) * k).toFloat())
            lineTo(marker.x0.toFloat(), marker.bot0.toFloat())
            close()
        }
        canvas.drawPath(band, paint(C.highlight))
        canvas.save()
        canvas.clipPath(band)
        drawLabel(canvas, resources.dream, dreamX + 8.0, title.lines[2], 0.0, paint(C.ink).color)
        canvas.restore()
    }

    private fun drawArrow(canvas: Canvas, frame: Frame) {
        val stroke = paint(C.white).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (frame.arrow > 0.003) {
            val path = Path().apply {
                moveTo(arrow.ax.toFloat(), arrow.ay.toFloat())
                quadTo(arrow.cx.toFloat(), arrow.cy.toFloat(), arrow.bx.toFloat(), arrow.by.toFloat())
            }
            val clipped = Path()
            if (frame.arrow < 0.999) {
                val measure = PathMeasure(path, false)
                measure.getSegment(0.0f, (measure.length * frame.arrow).toFloat(), clipped, true)
            } else clipped.set(path)
            canvas.drawPath(clipped, stroke)
        }
        if (frame.arrow > 0.97) {
            val k = 1.0 + 0.35 * frame.head
            val tx = arrow.bx - arrow.cx
            val ty = arrow.by - arrow.cy
            val length = hypot(tx, ty)
            val ux = tx / length
            val uy = ty / length
            val headLength = arrow.head * k
            val s = sin(0.55)
            val co = cos(0.55)
            val head = Path().apply {
                moveTo((arrow.bx - headLength * (ux * co - uy * s)).toFloat(), (arrow.by - headLength * (uy * co + ux * s)).toFloat())
                lineTo(arrow.bx.toFloat(), arrow.by.toFloat())
                lineTo((arrow.bx - headLength * (ux * co + uy * s)).toFloat(), (arrow.by - headLength * (uy * co - ux * s)).toFloat())
            }
            canvas.drawPath(head, stroke)
        }
        val popScale = 1.0 + 0.45 * frame.pops
        pops.forEach { pop ->
            val mx = (pop.x0 + pop.x1) / 2.0
            val my = (pop.y0 + pop.y1) / 2.0
            canvas.drawLine(
                (mx + (pop.x0 - mx) * popScale).toFloat(),
                (my + (pop.y0 - my) * popScale).toFloat(),
                (mx + (pop.x1 - mx) * popScale).toFloat(),
                (my + (pop.y1 - my) * popScale).toFloat(),
                stroke,
            )
        }
    }

    private fun drawLinks(canvas: Canvas, live: CareerWebOnboardingLive, homeLengths: DoubleArray, t: Double) {
        val stroke = paint(C.rope).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        for (link in links.indices) {
            val on = linkOn(link, live.grow)
            if (on <= 0.005) continue
            val a = links[link][0]
            val b = links[link][1]
            val ax = live.px[a]
            val ay = live.py[a]
            val bx = ax + (live.px[b] - ax) * on
            val by = ay + (live.py[b] - ay) * on
            val dx = bx - ax
            val dy = by - ay
            val length = hypot(dx, dy).takeUnless { it == 0.0 } ?: 1.0
            val full = hypot(live.px[b] - ax, live.py[b] - ay)
            val width = ROPE_W * max(0.5, min(1.2, kotlin.math.sqrt(homeLengths[link] / full)))
            val nx = -dy / length
            val ny = dx / length
            val swing = live.vib[link]
            val path = Path().apply {
                moveTo(ax.toFloat(), ay.toFloat())
                quadTo(((ax + bx) / 2.0 + nx * swing).toFloat(), ((ay + by) / 2.0 + ny * swing).toFloat(), bx.toFloat(), by.toFloat())
            }
            stroke.strokeWidth = width.toFloat()
            canvas.drawPath(path, stroke)
            if (on >= 0.999) {
                val u = (t / 3 + link * 0.29) % 1
                val qx = (ax + bx) / 2 + nx * swing
                val qy = (ay + by) / 2 + ny * swing
                val sx = (1 - u) * (1 - u) * ax + 2 * (1 - u) * u * qx + u * u * bx
                val sy = (1 - u) * (1 - u) * ay + 2 * (1 - u) * u * qy + u * u * by
                val signal = paint(C.highlight).apply {
                    shader = android.graphics.RadialGradient(sx.toFloat(), sy.toFloat(), 9f, intArrayOf(alphaColor(C.highlight, 0.9 * sin(Math.PI * u)), alphaColor(C.highlight, 0.0)), null, android.graphics.Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(sx.toFloat(), sy.toFloat(), 9f, signal)
            }
            if (on < 0.999) canvas.drawCircle(bx.toFloat(), by.toFloat(), (width * 0.85).toFloat(), paint(C.rope))
        }
    }

    private fun drawNodes(canvas: Canvas, resources: CareerWebOnboardingResources, live: CareerWebOnboardingLive) {
        var person = 0
        for (index in nodes.indices) {
            val node = nodes[index]
            val x = live.px[index]
            val y = live.py[index]
            val held = if (index == live.hold) 1.0 else 0.0
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            val scale = 1.0 + 0.06 * held
            canvas.scale(scale.toFloat(), scale.toFloat())
            if (node.kind == CareerWebOnboardingTheme.NodeKind.PERSON) {
                val ring = live.pick[person]
                if (ring >= 0.0) {
                    val ringPaint = paint(C.ring, 1.0 - ring).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = (3.0 * (1.0 - ring) + 0.5).toFloat()
                    }
                    canvas.drawCircle(0.0f, 0.0f, (node.r + 4.0 + 16.0 * ring).toFloat(), ringPaint)
                }
                canvas.drawCircle(0.0f, 0.0f, node.r.toFloat(), paint(node.background))
                val clip = Path().apply { addCircle(0.0f, 0.0f, node.r.toFloat(), Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(clip)
                val image = resources.people[person]
                canvas.drawBitmap(image, null, RectF(-node.r.toFloat(), -node.r.toFloat(), node.r.toFloat(), node.r.toFloat()), paint())
                canvas.restore()
                person++
            } else {
                canvas.drawCircle(0.0f, 0.0f, node.r.toFloat(), paint(C.white))
                resources.roles[index]?.let { labels ->
                    drawLabel(canvas, labels[0], 0.0, -9.5, 0.5, paint(C.ink).color)
                    drawLabel(canvas, labels[1], 0.0, 9.5, 0.5, paint(C.ink).color)
                }
            }
            canvas.restore()
        }
    }

    private fun drawButton(canvas: Canvas, resources: CareerWebOnboardingResources, frame: Frame, live: CareerWebOnboardingLive) {
        val scale = 1.0 - 0.04 * live.press
        canvas.save()
        canvas.translate((button.x + button.w / 2.0).toFloat(), (button.y + button.h / 2.0).toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat())
        canvas.translate(-(button.x + button.w / 2.0).toFloat(), -(button.y + button.h / 2.0).toFloat())
        canvas.drawRoundRect(RectF(button.x.toFloat(), button.y.toFloat(), (button.x + button.w).toFloat(), (button.y + button.h).toFloat()), (button.h / 2.0).toFloat(), (button.h / 2.0).toFloat(), paint(C.white))
        drawLabel(canvas, resources.cta, button.x + (button.w - button.dotR * 2.0 - 10.0) / 2.0 + 4.0, button.y + button.h / 2.0, 0.5, paint(C.ink).color)
        val dotX = button.dotX + 2.0 * frame.nudge
        canvas.drawCircle(dotX.toFloat(), button.dotY.toFloat(), (button.dotR * (1.0 + 0.04 * frame.nudge)).toFloat(), paint(C.ink))
        val clip = Path().apply { addCircle(dotX.toFloat(), button.dotY.toFloat(), (button.dotR - 2.0).toFloat(), Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        var offset = 5.0 * frame.nudge
        if (live.shoot >= 0.0) {
            offset = if (live.shoot < 0.5) 40.0 * (live.shoot / 0.5) * (live.shoot / 0.5) else -40.0 * (1.0 - (live.shoot - 0.5) / 0.5) * (1.0 - (live.shoot - 0.5) / 0.5)
        }
        val arrowPaint = paint(C.white).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo((button.dotX - 8.0 + offset).toFloat(), button.dotY.toFloat())
            lineTo((button.dotX + 8.0 + offset).toFloat(), button.dotY.toFloat())
            moveTo((button.dotX + 2.5 + offset).toFloat(), (button.dotY - 5.5).toFloat())
            lineTo((button.dotX + 8.0 + offset).toFloat(), button.dotY.toFloat())
            lineTo((button.dotX + 2.5 + offset).toFloat(), (button.dotY + 5.5).toFloat())
        }
        canvas.drawPath(path, arrowPaint)
        canvas.restore()
        canvas.restore()
    }

    fun drawScene(canvas: Canvas, width: Float, height: Float, frame: Frame, live: CareerWebOnboardingLive, homeLengths: DoubleArray) {
        canvas.drawColor(C.bg)
        val scale = width.toDouble() / CareerWebOnboardingTheme.REF_W
        val dy = height.toDouble() - CareerWebOnboardingTheme.REF_H * scale
        canvas.save()
        canvas.translate(0.0f, dy.toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat())
        val grid = paint(C.white, 0.035).apply { strokeWidth = 1.0f }
        var gridX = 12.0
        while (gridX < CareerWebOnboardingTheme.REF_W) {
            canvas.drawLine(gridX.toFloat(), (-dy / scale).toFloat(), gridX.toFloat(), 900.0f, grid)
            gridX += 26.0
        }
        var gridY = 10.0
        while (gridY < 900.0) {
            canvas.drawLine(0.0f, gridY.toFloat(), CareerWebOnboardingTheme.REF_W.toFloat(), gridY.toFloat(), grid)
            gridY += 26.0
        }
        drawTitle(canvas, resources, frame)
        drawArrow(canvas, frame)
        drawLinks(canvas, live, homeLengths, frame.t)
        drawNodes(canvas, resources, live)
        drawButton(canvas, resources, frame, live)
        canvas.restore()
    }
}
