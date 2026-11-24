package com.ngocdevv.nativecook.blocks.forms.addresslookupmap

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.CRESCENT_CTRL
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.CRESCENT_FROM
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.CRESCENT_TO
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.DISTRICTS
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.MAIN_ROAD
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.PARKS
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.RIVER_LABEL
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.RIVER_NORTH
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.RIVER_SOUTH
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.STREETS
import kotlin.math.max
import kotlin.math.min

/*
 StreetMap — a town drawn in code (a port of StreetMap.tsx): river, parks, house footprints, streets
 with casings, one main road and labels. Nothing comes from a map service. A camera (centre + scale,
 in the town's world units) looks at it; the pin is drawn in screen space at the middle of the map,
 so a drag moves the town under it, the way delivery apps let you fine-tune a drop-off point.

 The town's fixed shapes are recorded once into a Picture in world units and replayed under the
 camera's matrix every frame; only the labels (which fade with the zoom) and the pin are drawn live.
 Everything is in dp; [draw] is handed the screen density.
*/

/** Camera in world units: centre (x, y) and scale s (dp per world unit). */
class AddressLookupMapCamera(val x: Float, val y: Float, val s: Float)

class AddressLookupMapPinPose(
    /** Height above the ground, dp (negative = up). */
    val y: Float,
    /** Touch-down squash 0 ..1. */
    val squash: Float,
    val alpha: Float,
    /** 0 ..1 while the map is held: the pin floats off the ground. */
    val lift: Float,
    val ring1: Float,
    val ring2: Float,
)

internal class AddressLookupMapStreet(private val accent: Int = 0xFFE0562E.toInt()) {
    private object C {
        const val LAND = 0xFFECE9E1.toInt()
        const val HOUSE = 0xFFDAD5C8.toInt()
        const val HOUSE_SHADE = 0xFFCDC6B6.toInt()
        const val CASING = 0xFFD6D0C1.toInt()
        const val STREET = 0xFFFBFAF6.toInt()
        const val MAIN = 0xFFF7DFA6.toInt()
        const val MAIN_CASING = 0xFFE2C27E.toInt()
        const val WATER = 0xFFBCD5E4.toInt()
        const val WATER_EDGE = 0xFFA6C4D8.toInt()
        const val PARK = 0xFFD2E2BE.toInt()
        const val PARK_PATH = 0xFFE4ECD6.toInt()
        const val LABEL = 0xFF7E776A.toInt()
        const val WATER_LABEL = 0xFF5F86A1.toInt()
        const val DISTRICT = 0xFF6E675B.toInt()
        const val MAIN_LABEL = 0xFF8C6A22.toInt()
        const val PARK_LABEL = 0xFF6C8A55.toInt()
    }

    private companion object {
        const val PIN_HEAD_Y = -36f
        const val PIN_R = 14f
        const val LIFT_PT = 14f

        fun weight(w: Int): Typeface =
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, w, false)
            else if (w >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        fun textPaint(size: Float, w: Int, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = weight(w)
            textSize = size
            this.color = color
        }

        /** Smooth path through points (Catmull-Rom as cubic Béziers), appended to [path]. */
        fun smooth(path: Path, points: List<FloatArray>, move: Boolean) {
            if (move) path.moveTo(points[0][0], points[0][1]) else path.lineTo(points[0][0], points[0][1])
            for (i in 0 until points.size - 1) {
                val p0 = points[max(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[min(points.size - 1, i + 2)]
                path.cubicTo(
                    p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6,
                    p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6,
                    p2[0], p2[1],
                )
            }
        }

        fun lighten(c: Int): Int {
            val r = min(255, ((c shr 16) and 255) + 38)
            val g = min(255, ((c shr 8) and 255) + 38)
            val b = min(255, (c and 255) + 30)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    // ---- Fonts and labels (world units, the same numbers as the React Native version) ---------
    private val streetFont = textPaint(12f, 500, C.LABEL)
    private val streetHalo = textPaint(12f, 500, C.STREET).apply { style = Paint.Style.STROKE; strokeWidth = 3f; strokeJoin = Paint.Join.ROUND }
    private val parkFont = textPaint(12f, 600, C.PARK_LABEL)
    private val districtFont = textPaint(40f, 700, C.DISTRICT)
    private val districtHalo = textPaint(40f, 700, C.LAND).apply { style = Paint.Style.STROKE; strokeWidth = 8f; strokeJoin = Paint.Join.ROUND }
    private val mainFont = textPaint(13f, 600, C.MAIN_LABEL)
    private val waterFont = textPaint(12f, 500, C.WATER_LABEL)

    private class Label(val text: String, val x: Float, val y: Float, val angle: Float, val w: Float)

    private val labels: List<Label> = STREETS.map { Label(it.name, it.label[0], it.label[1], it.angle, streetFont.measureText(it.name)) } +
        Label("Lark Rise", 805f, 872f, 0f, streetFont.measureText("Lark Rise"))
    private val mainW = mainFont.measureText(MAIN_ROAD.name)
    private val districtW = DISTRICTS.map { districtFont.measureText(it.name) }
    private val parkW = PARKS.map { parkFont.measureText(it.name) }
    private val riverW = waterFont.measureText("Wendle Water")

    // ---- The town, recorded once ----------------------------------------------------------------
    private val town: Picture = Picture().also { pic ->
        val c = pic.beginRecording(2400, 2400)
        c.translate(400f, 400f)
        drawTown(c)
        pic.endRecording()
    }

    private fun drawTown(c: Canvas) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = C.LAND
        c.drawRect(-400f, -400f, 1600f, 1600f, fill)

        val north = Path().also { smooth(it, RIVER_NORTH, true) }
        val river = Path().also {
            smooth(it, RIVER_NORTH, true)
            smooth(it, RIVER_SOUTH.reversed(), false)
            it.close()
        }
        fill.color = C.WATER
        c.drawPath(river, fill)
        val edge = Path(north).also { smooth(it, RIVER_SOUTH, true) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        stroke.color = C.WATER_EDGE
        stroke.strokeWidth = 2f
        c.drawPath(edge, stroke)

        fill.color = C.PARK
        for (p in PARKS) c.drawRoundRect(p.x0 + 9, p.y0 + 9, p.x1 - 9, p.y1 - 9, 10f, 10f, fill)
        val parkPaths = Path()
        for (p in PARKS) {
            val cx = (p.x0 + p.x1) / 2
            val cy = (p.y0 + p.y1) / 2
            parkPaths.moveTo(p.x0 + 14, p.y1 - 18)
            parkPaths.quadTo(cx, cy - 10, p.x1 - 14, p.y0 + 20)
            parkPaths.moveTo(p.x0 + 20, p.y0 + 16)
            parkPaths.quadTo(cx - 10, cy + 20, p.x1 - 18, p.y1 - 14)
        }
        stroke.color = C.PARK_PATH
        stroke.strokeWidth = 3f
        stroke.strokeCap = Paint.Cap.ROUND
        c.drawPath(parkPaths, stroke)

        val footprint = AddressLookupMapData.houses()
        fill.color = C.HOUSE_SHADE
        for (h in footprint) c.drawRect(h[0] + 1.6f, h[1] + 2.2f, h[0] + 1.6f + h[2], h[1] + 2.2f + h[3], fill)
        fill.color = C.HOUSE
        for (h in footprint) c.drawRect(h[0], h[1], h[0] + h[2], h[1] + h[3], fill)

        val streets = Path()
        for (r in STREETS) {
            streets.moveTo(r.points[0][0], r.points[0][1])
            for (k in 1 until r.points.size) streets.lineTo(r.points[k][0], r.points[k][1])
        }
        streets.moveTo(CRESCENT_FROM[0], CRESCENT_FROM[1])
        streets.quadTo(CRESCENT_CTRL[0], CRESCENT_CTRL[1], CRESCENT_TO[0], CRESCENT_TO[1])
        val main = Path().apply {
            moveTo(MAIN_ROAD.points[0][0], MAIN_ROAD.points[0][1])
            lineTo(MAIN_ROAD.points[1][0], MAIN_ROAD.points[1][1])
        }
        stroke.color = C.CASING; stroke.strokeWidth = 15f; stroke.strokeCap = Paint.Cap.ROUND
        c.drawPath(streets, stroke)
        stroke.color = C.MAIN_CASING; stroke.strokeWidth = 28f; stroke.strokeCap = Paint.Cap.BUTT
        c.drawPath(main, stroke)
        stroke.color = C.STREET; stroke.strokeWidth = 12f; stroke.strokeCap = Paint.Cap.ROUND
        c.drawPath(streets, stroke)
        stroke.color = C.MAIN; stroke.strokeWidth = 24f; stroke.strokeCap = Paint.Cap.BUTT
        c.drawPath(main, stroke)
    }

    // ---- Pin ------------------------------------------------------------------------------------
    private val pinPath = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-2f, -6f, -PIN_R, PIN_HEAD_Y + 14, -PIN_R, PIN_HEAD_Y)
        arcTo(RectF(-PIN_R, PIN_HEAD_Y - PIN_R, PIN_R, PIN_HEAD_Y + PIN_R), 180f, 180f, false)
        cubicTo(PIN_R, PIN_HEAD_Y + 14, 2f, -6f, 0f, 0f)
        close()
    }
    private val pinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -PIN_R, PIN_HEAD_Y - PIN_R, PIN_R, 0f,
            intArrayOf(lighten(accent), accent, 0xFFB23D1C.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pinShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E000000 }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8F1.toInt() }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x52FFFFFF }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = accent }
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Light ----------------------------------------------------------------------------------
    private var lightW = -1f
    private var lightH = -1f
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun light(w: Float, h: Float): Paint {
        if (w != lightW || h != lightH) {
            lightW = w; lightH = h
            lightPaint.shader = RadialGradient(
                w * 0.3f, h * 0.2f, max(w, h) * 0.95f,
                intArrayOf(0x2EFFFFFF, 0x00FFFFFF, 0x1A3C3428), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        return lightPaint
    }

    private fun alpha(base: Int, a: Float): Int {
        val a0 = (base ushr 24) and 255
        return ((a0 * a.coerceIn(0f, 1f)).toInt() shl 24) or (base and 0xFFFFFF)
    }

    /**
     Draws the map into [c] (pixels) for a card [width] × [height] dp at [density].
     [rotation] is the town's turn on screen in radians.
     */
    fun draw(c: Canvas, density: Float, width: Float, height: Float, cam: AddressLookupMapCamera, pin: AddressLookupMapPinPose, rotation: Float) {
        c.save()
        c.scale(density, density)
        val cx = width / 2
        val cy = height / 2
        paint.color = C.LAND
        paint.shader = null
        c.drawRect(0f, 0f, width, height, paint)

        c.save()
        c.clipRect(0f, 0f, width, height)
        c.translate(cx, cy)
        c.rotate(Math.toDegrees(rotation.toDouble()).toFloat())
        c.scale(cam.s, cam.s)
        c.translate(-cam.x, -cam.y)
        c.save()
        c.translate(-400f, -400f)
        c.drawPicture(town)
        c.restore()

        // Labels fade in with the zoom; district names fade out.
        val streetA = AddressLookupMapMotion.clamp01((cam.s - 0.46f) / 0.24f)
        val districtA = 1 - AddressLookupMapMotion.clamp01((cam.s - 0.4f) / 0.22f)
        if (streetA > 0.001f) {
            val save = if (streetA < 0.999f) c.saveLayerAlpha(-400f, -400f, 1600f, 1600f, (streetA * 255).toInt()) else c.save()
            for (l in labels) {
                c.save()
                c.translate(l.x, l.y)
                c.rotate(l.angle)
                c.drawText(l.text, -l.w / 2, 4f, streetHalo)
                c.drawText(l.text, -l.w / 2, 4f, streetFont)
                c.restore()
            }
            c.save()
            c.translate(MAIN_ROAD.label[0], MAIN_ROAD.label[1])
            c.rotate(MAIN_ROAD.angle)
            c.drawText(MAIN_ROAD.name, -mainW / 2, 4.5f, mainFont)
            c.restore()
            PARKS.forEachIndexed { i, p ->
                c.drawText(p.name, (p.x0 + p.x1) / 2 - parkW[i] / 2, (p.y0 + p.y1) / 2 + 4, parkFont)
            }
            c.drawText("Wendle Water", RIVER_LABEL[0] - riverW / 2, RIVER_LABEL[1] + 4, waterFont)
            c.restoreToCount(save)
        }
        if (districtA > 0.001f) {
            val save = if (districtA < 0.999f) c.saveLayerAlpha(-400f, -400f, 1600f, 1600f, (districtA * 255).toInt()) else c.save()
            districtFont.alpha = (0.62f * 255).toInt()
            DISTRICTS.forEachIndexed { i, d ->
                c.drawText(d.name, d.x - districtW[i] / 2, d.y, districtHalo)
                c.drawText(d.name, d.x - districtW[i] / 2, d.y, districtFont)
            }
            c.restoreToCount(save)
        }
        c.restore()

        // A soft light from the top-left and a vignette, so the drawing sits in the card like a lit surface.
        c.drawRect(0f, 0f, width, height, light(width, height))

        // Rings.
        val r1 = 6 + 34 * (1 - (1 - pin.ring1) * (1 - pin.ring1))
        val r2 = 6 + 34 * (1 - (1 - pin.ring2) * (1 - pin.ring2))
        val a1 = if (pin.ring1 <= 0f || pin.ring1 >= 1f) 0f else 0.5f * (1 - pin.ring1) * pin.alpha
        val a2 = if (pin.ring2 <= 0f || pin.ring2 >= 1f) 0f else 0.38f * (1 - pin.ring2) * pin.alpha
        if (a1 > 0f) { ringPaint.strokeWidth = 2f; ringPaint.color = alpha(accent, a1); c.drawCircle(cx, cy, r1, ringPaint) }
        if (a2 > 0f) { ringPaint.strokeWidth = 1.5f; ringPaint.color = alpha(accent, a2); c.drawCircle(cx, cy, r2, ringPaint) }

        // Contact shadow: shrinks and fades while the pin is up.
        val fall = min(1f, -pin.y / 84f)
        val k = 1 - 0.55f * max(fall, pin.lift * 0.6f)
        val sw = 22 * k
        val sh = 7 * k
        val sa = pin.alpha * (0.32f - 0.2f * max(fall, pin.lift * 0.6f))
        if (sa > 0f) {
            contactPaint.color = alpha(0xFF2A2015.toInt(), sa)
            c.drawOval(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2, contactPaint)
        }

        // The pin, in screen space.
        if (pin.alpha > 0.001f) {
            val save = if (pin.alpha < 0.999f) c.saveLayerAlpha(cx - 40, cy - 160, cx + 40, cy + 20, (pin.alpha * 255).toInt()) else c.save()
            c.translate(cx, cy + pin.y - LIFT_PT * pin.lift)
            c.scale(1 + 0.1f * pin.squash, 1 - 0.14f * pin.squash)
            c.save()
            c.translate(0f, 1.5f)
            c.drawPath(pinPath, pinShadow)
            c.restore()
            c.drawPath(pinPath, pinFill)
            c.drawCircle(0f, PIN_HEAD_Y, 5.2f, dotPaint)
            c.drawCircle(-5f, PIN_HEAD_Y - 6, 3.4f, shinePaint)
            c.restoreToCount(save)
        }
        c.restore()
    }
}
