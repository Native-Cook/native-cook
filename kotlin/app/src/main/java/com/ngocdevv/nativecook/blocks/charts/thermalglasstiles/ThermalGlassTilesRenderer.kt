package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesData as D
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesMotion as M
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesTheme as Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The screen's drawing (RN `RunReplay`, `GlassTile`, `ThermalField` and `TileContent`): the thermal field over the page, three glass
 * tiles lensing a frosted copy of it, then the route, the marker, the gauges and the figures (three of them rolling like an odometer).
 * Everything is in dp: the caller scales the canvas by the density.
 */
internal class ThermalGlassTilesRenderer(
    assets: AssetManager,
    private val layout: Theme.Layout,
    private val fontScale: Double,
    private val engine: ThermalGlassTilesEngine,
    private val routePath: Path,
) {
    private val light = Typeface.createFromAsset(assets, "thermalglasstiles/thermalglasstiles_Urbanist-Light.ttf")
    private val regular = Typeface.createFromAsset(assets, "thermalglasstiles/thermalglasstiles_Urbanist-Regular.ttf")
    private val sharp: Bitmap? = decode(assets, "thermalglasstiles/thermalglasstiles_runner-heat-atlas.png")
    private val soft: Bitmap? = decode(assets, "thermalglasstiles/thermalglasstiles_runner-heat-atlas-blur.png")
    private val palette: Bitmap? = decode(assets, "thermalglasstiles/thermalglasstiles_thermal-palette.png")
    val ready get() = sharp != null && soft != null && palette != null

    private fun decode(assets: AssetManager, name: String): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        assets.open(name).use { BitmapFactory.decodeStream(it, null, o) }
    } catch (error: Exception) {
        Log.w("ThermalGlassTiles", "Image unavailable: $name", error)
        null
    }

    private var fieldSharp: ThermalGlassTilesFieldShader? = null
    private var fieldSoft: ThermalGlassTilesFieldShader? = null
    private var glass: Array<ThermalGlassTilesGlassShader>? = null
    private var shaderFailed = false
    private val sharpU = ThermalGlassTilesFieldUniforms()
    private val softU = ThermalGlassTilesFieldUniforms()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rr = RectF()
    private val path = Path()
    private val arc: Path = Path().apply {
        for (i in 0..40) {
            val a = (-150 + i * 3) * PI / 180
            val x = (280 + 54 * cos(a)).toFloat()
            val y = (625 + 54 * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    private val triangle = Path().apply { moveTo(-3f, -4f); lineTo(3f, -4f); lineTo(0f, 4f); close() }

    // the hit rects (dp, screen space)
    val large = rect(Theme.LARGE)
    val contact = rect(Theme.CONTACT)
    val vertical = rect(Theme.VERTICAL)
    fun rect(r: ThermalGlassTilesRect) = ThermalGlassTilesRect(layout.ox + r.x * layout.s, layout.oy + r.y * layout.s, r.w * layout.s, r.h * layout.s, r.r * layout.s)

    private fun fillFieldUniforms(u: ThermalGlassTilesFieldUniforms, e: ThermalGlassTilesEngine, atlas: Bitmap, rows: Int) {
        val effort = e.thermalEffort.value
        u.width = layout.w.toFloat(); u.height = layout.h.toFloat()
        u.ox = layout.ox.toFloat(); u.oy = layout.oy.toFloat()
        u.scale = layout.s.toFloat()
        u.phase = e.phase.toFloat()
        u.effort = effort.toFloat()
        u.streak = e.streak.toFloat()
        u.time = e.fieldTime.toFloat()
        u.touch[0] = (if (e.reduced) 0.0 else e.touch[0]).toFloat()
        u.touch[1] = (if (e.reduced) 0.0 else e.touch[1]).toFloat()
        u.touch[2] = (if (e.reduced) -1.0 else e.touch[2]).toFloat()
        u.peak = e.peak.toFloat()
        u.grain = Theme.GRAIN.toFloat()
        val wake = if (e.reduced) 0.0 else Theme.PEAK_WAKE
        u.wake = (if (wake == 0.0) 0.0 else 20 + (wake - 20) * M.smooth(0.4, 0.8, effort)).toFloat()
        u.direction = e.direction.toFloat()
        u.sourceW = atlas.width.toFloat(); u.sourceH = atlas.height.toFloat()
        u.frame = ((e.phase * 24) / (2 * PI)).toFloat()
        u.paletteRows = rows.toFloat()
    }

    fun draw(c: Canvas, density: Float) {
        val e = engine
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        fill.shader = null
        fill.color = Theme.PAGE
        c.drawRect(0f, 0f, w, h, fill)
        if (!ready) return
        if (!drawShaders(c, e)) drawFallback(c, e)

        // tile contents, in the reference's coordinates
        c.save()
        c.translate(layout.ox.toFloat(), layout.oy.toFloat())
        c.scale(layout.s.toFloat(), layout.s.toFloat())
        drawContent(c, e)
        c.restore()
    }

    private fun drawShaders(c: Canvas, e: ThermalGlassTilesEngine): Boolean {
        if (Build.VERSION.SDK_INT < 33 || shaderFailed || !c.isHardwareAccelerated) return false
        val sharpB = sharp ?: return false
        val softB = soft ?: return false
        val pal = palette ?: return false
        if (fieldSharp == null) {
            try {
                fieldSharp = ThermalGlassTilesFieldShader(sharpB, pal)
                val sf = ThermalGlassTilesFieldShader(softB, pal)
                fieldSoft = sf
                glass = Array(3) { ThermalGlassTilesGlassShader(sf.shader) }
            } catch (error: IllegalArgumentException) {
                Log.w("ThermalGlassTiles", "Runtime shader unavailable; using the gradient fallback", error)
                shaderFailed = true
                return false
            }
        }
        val fs = fieldSharp ?: return false
        val fo = fieldSoft ?: return false
        val g = glass ?: return false
        fillFieldUniforms(sharpU, e, sharpB, pal.height)
        fillFieldUniforms(softU, e, softB, pal.height)
        fs.apply(sharpU)
        fo.apply(softU)
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        c.drawRect(0f, 0f, w, h, fs.paint)
        val s = layout.s
        val dark = if (e.effort > 0.82) 1f else 0f
        val tiles = arrayOf(large, contact, vertical)
        val labels = arrayOf(Theme.LABEL_LARGE, Theme.LABEL_CONTACT, Theme.LABEL_VERTICAL)
        val peaks = doubleArrayOf(e.peak, e.circlePeak, e.smallPeak)
        for (i in 0 until 3) {
            val t = tiles[i]
            val l = labels[i]
            val label = floatArrayOf((layout.ox + l[0] * s).toFloat(), (layout.oy + l[1] * s).toFloat(), (l[2] * s).toFloat(), (l[3] * s).toFloat())
            g[i].apply(t.x.toFloat(), t.y.toFloat(), t.w.toFloat(), t.h.toFloat(), t.r.toFloat(), i == 1, peaks[i].toFloat(), e.fieldTime.toFloat(), dark, label, s.toFloat())
            rr.set(t.x.toFloat(), t.y.toFloat(), (t.x + t.w).toFloat(), (t.y + t.h).toFloat())
            c.drawRoundRect(rr, t.r.toFloat(), t.r.toFloat(), g[i].paint)
        }
        return true
    }

    /** Below API 33: a warm gradient for the heat and plain frosted tiles. */
    private fun drawFallback(c: Canvas, e: ThermalGlassTilesEngine) {
        val effort = e.thermalEffort.value
        val h = layout.h.toFloat()
        val cool = 0xFF5B8CFF.toInt()
        val hot = 0xFFF46A2E.toInt()
        fun mixC(a: Int, b: Int, t: Double): Int {
            fun ch(sh: Int) = (((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * t).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        fill.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(mixC(0xFF14232A.toInt(), cool, 0.3), mixC(Theme.PAGE, hot, effort), mixC(0xFFC8B5B5.toInt(), hot, effort * 0.7)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, layout.w.toFloat(), h, fill)
        fill.shader = null
        for (t in arrayOf(large, contact, vertical)) {
            rr.set(t.x.toFloat(), t.y.toFloat(), (t.x + t.w).toFloat(), (t.y + t.h).toFloat())
            fill.color = 0x40FFFFFF
            c.drawRoundRect(rr, t.r.toFloat(), t.r.toFloat(), fill)
            stroke.color = 0x70F8F5EB
            stroke.strokeWidth = 1f
            c.drawRoundRect(rr, t.r.toFloat(), t.r.toFloat(), stroke)
        }
    }

    // ---- tile content -----------------------------------------------------------------------------------------------------------

    private fun withAlpha(color: Int, a: Double) = (((color ushr 24) * M.clamp(a)).roundToInt() shl 24) or (color and 0xFFFFFF)

    private fun drawContent(c: Canvas, e: ThermalGlassTilesEngine) {
        val ink = if (e.effort > 0.82) Theme.INK_DARK else Theme.INK
        // the route and its marker
        stroke.strokeWidth = Theme.ROUTE_STROKE.toFloat()
        stroke.color = withAlpha(Theme.ROUTE_COLOR, 0.8)
        c.drawPath(routePath, stroke)
        val pts = engine.points
        val i = (M.clamp(e.progress.value) * (pts.size - 1)).roundToInt()
        val mx = pts[i].x.toFloat()
        val my = pts[i].y.toFloat()
        stroke.color = withAlpha(Theme.MARKER, 0.8)
        stroke.strokeWidth = 0.8f
        c.drawCircle(mx, my, e.ring.toFloat(), stroke)
        fill.color = Theme.MARKER
        c.drawCircle(mx, my, 3f, fill)
        // the vertical-oscillation gauge
        stroke.color = withAlpha(ink, 0.4)
        stroke.strokeWidth = 0.8f
        c.drawPath(arc, stroke)
        val angle = ((-150 + 120 * M.clamp((e.sample.oscillation - 8) / 5)) * PI) / 180
        val tx = Theme.VERTICAL_X + Theme.VERTICAL_R * cos(angle)
        val ty = Theme.VERTICAL_Y + Theme.VERTICAL_R * sin(angle)
        c.save()
        c.translate(tx.toFloat(), ty.toFloat())
        fill.color = Theme.MARKER
        c.drawPath(triangle, fill)
        c.restore()
        // the contact-time dots
        val lit = (M.clamp((e.sample.contact - 245) / 50) * 6).roundToInt()
        for (k in 0 until 7) {
            fill.color = withAlpha(ink, if (k <= lit) 1.0 else 0.3)
            c.drawCircle((74 + k * 16).toFloat(), (Theme.CONTACT_Y - 16 * sin(k * PI / 6)).toFloat(), Theme.DOT.toFloat(), fill)
        }
        // the figures
        for (item in Theme.textItemsAt(fontScale)) {
            when (item.id) {
                "distance" -> drawRolling(c, item, 0, 2, 2, ink)
                "contact" -> drawRolling(c, item, 1, 0, 3, ink)
                "oscillation" -> drawRolling(c, item, 2, 1, 2, ink)
                else -> drawMetric(c, item, ink)
            }
        }
    }

    private fun metricText(id: String): String {
        val s = engine.sample
        val mi = engine.units == "mi"
        return when (id) {
            "run" -> Theme.RUN
            "unit" -> if (mi) Theme.MI else Theme.KM
            "elapsed" -> D.timeText(s.elapsed)
            "bpm" -> "${s.bpm.roundToInt()} bpm"
            "pace" -> "${D.timeText(s.pace * (if (mi) 1.609344 else 1.0))} /${engine.units}"
            "contactLabel" -> Theme.CONTACT_LABEL
            "ms" -> Theme.MS
            "verticalLabel" -> Theme.VERTICAL_LABEL
            "cm" -> Theme.CM
            else -> ""
        }
    }

    private fun drawMetric(c: Canvas, item: ThermalGlassTilesTextItem, ink: Int) {
        val size = item.size * min(fontScale, if (item.light) 1.35 else 1.6)
        text.typeface = if (item.light) light else regular
        text.textSize = size.toFloat()
        text.fontFeatureSettings = ""
        text.textAlign = Paint.Align.LEFT
        text.color = ink
        val str = metricText(item.id)
        val width = text.measureText(str)
        val x = when (item.align) { 1 -> item.x - width; 2 -> item.x - width / 2; else -> item.x }
        val fit = min(1.0, item.width / max(1f, width))
        val pulse = if (item.id == "bpm") engine.bpmScale else 1.0
        c.save()
        c.translate(item.x.toFloat(), item.y.toFloat())
        c.scale((fit * pulse).toFloat(), pulse.toFloat())
        c.translate(-item.x.toFloat(), -item.y.toFloat())
        c.drawText(str, x.toFloat(), item.y.toFloat(), text)
        c.restore()
    }

    /** The big figures roll like an odometer: fixed tabular columns, each sliding up (or down) into its next digit; no glyph is masked. */
    private fun drawRolling(c: Canvas, item: ThermalGlassTilesTextItem, idx: Int, decimals: Int, ints: Int, ink: Int) {
        val size = item.size * min(fontScale, 1.35)
        text.typeface = light
        text.textSize = size.toFloat()
        text.fontFeatureSettings = ""
        text.textAlign = Paint.Align.LEFT
        val digits = "0123456789"
        val widths = FloatArray(10) { text.measureText(digits[it].toString()) }
        val slotW = widths.max()
        val dotW = text.measureText(".")
        val totalW = ints * slotW + (if (decimals > 0) dotW + decimals * slotW else 0f)
        val fit = min(1.0, item.width / max(1f, totalW))
        val u = max(0.0, engine.shown[idx].value.let { if (it < 0) engine.rollValue(idx) * Math.pow(10.0, decimals.toDouble()) else it })
        val leadUnit = Math.pow(10.0, (decimals + ints - 1).toDouble())
        val vis = min(1.0, max(0.0, u - (leadUnit - 1)))
        val travel = size * 0.55
        c.save()
        c.translate((-(1 - vis) * slotW * fit).toFloat(), 0f)
        c.translate(item.x.toFloat(), item.y.toFloat())
        c.scale(fit.toFloat(), 1f)
        c.translate(-item.x.toFloat(), -item.y.toFloat())
        var cx = item.x.toFloat()
        var dotX = 0f
        text.color = ink
        fun column(place: Int, x: Float, lead: Boolean) {
            val unit = Math.pow(10.0, place.toDouble())
            val base = Math.floor(u / unit).toInt()
            val r = min(1.0, max(0.0, (u % unit) - (unit - 1)))
            val a = if (lead && base == 0) -1 else base % 10
            val b = (base + 1) % 10
            val aOp = 1 - smoothstep(min(1.0, r / 0.45))
            val bOp = smoothstep(max(0.0, (r - 0.55) / 0.45))
            if (a >= 0 && aOp > 0.003) {
                text.color = withAlpha(ink, aOp)
                c.drawText(digits[a].toString(), x + (slotW - widths[a]) / 2, (item.y - r * travel).toFloat(), text)
            }
            if (bOp > 0.003) {
                text.color = withAlpha(ink, bOp)
                c.drawText(digits[b].toString(), x + (slotW - widths[b]) / 2, (item.y + (1 - r) * travel).toFloat(), text)
            }
        }
        for (k in 0 until ints) {
            column(decimals + ints - 1 - k, cx, k < ints - 1)
            cx += slotW
        }
        dotX = cx
        cx += dotW
        for (k in 0 until decimals) {
            column(decimals - 1 - k, cx, false)
            cx += slotW
        }
        if (decimals > 0) {
            text.color = ink
            c.drawText(".", dotX, item.y.toFloat(), text)
        }
        c.restore()
    }

    private fun smoothstep(t: Double) = t * t * (3 - 2 * t)
}

