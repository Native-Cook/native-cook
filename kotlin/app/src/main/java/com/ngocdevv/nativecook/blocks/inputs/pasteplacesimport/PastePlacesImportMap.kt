package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.MapColors
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.MapShape

/**
 * The small paper map of the invented city (Velmora) that the text area unfolds into, card-local. Mirrors map.ts:
 * drawn once into a bitmap and shown on each folding panel; the route and the pins' dots and shadows are drawn
 * per frame on top (PastePlacesImportPaper).
 */
internal object PastePlacesImportMap {
    private val sea: Path by lazy { path(MapShape.sea) }
    private val river: Path by lazy { path(MapShape.river) }
    private val majors: List<Path> by lazy { MapShape.major.map(::path) }

    private fun path(data: String): Path = requireNotNull(PathParser.createPathFromPathData(data)) { "Bad map path: $data" }

    private fun stroke(@ColorInt color: Int, width: Float, cap: Paint.Cap = Paint.Cap.ROUND): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = cap
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
    }

    private fun fill(@ColorInt color: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    }

    /** The whole map into `canvas` (card-local), `unit` pixels per reference px. */
    fun draw(canvas: Canvas, unit: Float) {
        val width = PastePlacesImportTheme.Box.card.w
        val height = PastePlacesImportTheme.Box.card.h
        val radius = PastePlacesImportTheme.Radius.card
        val hairline = PastePlacesImportTheme.HAIRLINE
        canvas.save()
        canvas.scale(unit, unit)
        canvas.save()
        val card = Path().apply { addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW) }
        canvas.clipPath(card)

        canvas.drawRect(0f, 0f, width, height, fill(MapColors.land))
        val parkPaint = fill(MapColors.park)
        for (park in MapShape.parks) {
            canvas.drawOval(RectF(park.cx - park.rx, park.cy - park.ry, park.cx + park.rx, park.cy + park.ry), parkPaint)
        }

        // Minor streets: a slightly turned grid.
        canvas.save()
        canvas.rotate(MapShape.minorAngle, width / 2f, height / 2f)
        val minor = stroke(MapColors.minor, MapShape.minorW)
        var x = -width
        while (x < 2f * width) {
            canvas.drawLine(x, -height, x, 2f * height, minor)
            x += MapShape.minorGap
        }
        var y = -height
        while (y < 2f * height) {
            canvas.drawLine(-width, y, 2f * width, y, minor)
            y += MapShape.minorGap * 1.3f
        }
        canvas.restore()

        // River under the avenues, so every avenue crosses it on a bridge.
        canvas.drawPath(river, stroke(MapColors.shore, MapShape.shoreW))
        canvas.drawPath(river, stroke(MapColors.water, MapShape.riverW))

        val casing = stroke(MapColors.casing, MapShape.casingW)
        val street = stroke(MapColors.street, MapShape.majorW)
        majors.forEach { canvas.drawPath(it, casing) }
        majors.forEach { canvas.drawPath(it, street) }

        // Sea over everything it covers.
        canvas.drawPath(sea, fill(MapColors.water))
        canvas.drawPath(sea, stroke(MapColors.shore, MapShape.seaShoreW))

        // Creases where the paper was folded.
        val dark = stroke(MapColors.creaseDark, MapShape.creaseW, Paint.Cap.BUTT)
        val light = stroke(MapColors.creaseLight, MapShape.creaseW, Paint.Cap.BUTT)
        for (i in 1 until PastePlacesImportMotion.Fold.panels) {
            val at = height * i / PastePlacesImportMotion.Fold.panels
            canvas.drawLine(0f, at - MapShape.creaseW / 2f, width, at - MapShape.creaseW / 2f, dark)
            canvas.drawLine(0f, at + MapShape.creaseW / 2f, width, at + MapShape.creaseW / 2f, light)
        }
        canvas.restore()

        // The text area's hairline.
        val border = stroke(PastePlacesImportTheme.Colors.cardBorder, hairline)
        canvas.drawRoundRect(
            RectF(hairline / 2f, hairline / 2f, width - hairline / 2f, height - hairline / 2f),
            radius - hairline / 2f,
            radius - hairline / 2f,
            border,
        )
        canvas.restore()
    }

    /** The route through the places (Catmull-Rom through the dots), card-local pixels. */
    fun route(unit: Float): Path {
        val points = PastePlacesImportTheme.SPOTS.map { it.x * unit to it.y * unit }
        val route = Path()
        route.moveTo(points[0].first, points[0].second)
        for (i in 0 until points.size - 1) {
            val p0 = points[maxOf(0, i - 1)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[minOf(points.size - 1, i + 2)]
            route.cubicTo(
                p1.first + (p2.first - p0.first) / 6f,
                p1.second + (p2.second - p0.second) / 6f,
                p2.first - (p3.first - p1.first) / 6f,
                p2.second - (p3.second - p1.second) / 6f,
                p2.first,
                p2.second,
            )
        }
        return route
    }
}
