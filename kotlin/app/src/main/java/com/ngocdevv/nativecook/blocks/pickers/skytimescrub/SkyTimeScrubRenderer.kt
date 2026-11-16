package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.pickers.skytimescrub.SkyTimeScrubMotion as M
import com.ngocdevv.nativecook.blocks.pickers.skytimescrub.SkyTimeScrubTheme as Theme
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where a touch landed. */
internal enum class SkyTimeScrubHit { NONE, SUN_BUTTON, CITY_BUTTON, GRID_BUTTON, CHIP, HINT_CLOSE, SUN_GRAB, RULER }

/**
 * The screen's drawing (RN `SkyTimeScrubber`): the sky and the instrument from the AGSL shader (API 33+, a gradient fallback below),
 * then the hint that folds into the time chip, the chip, the schedule panel and its rows, and the three controls. Everything is in dp:
 * the caller scales the canvas by the density.
 */
internal class SkyTimeScrubRenderer(assets: AssetManager, private val hintText: String?, private val fontScale: Double) {
    private val regular = Typeface.createFromAsset(assets, "skytimescrub/skytimescrub_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "skytimescrub/skytimescrub_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(assets, "skytimescrub/skytimescrub_Inter-SemiBold.ttf")

    private val skyNode: RenderNode? = if (Build.VERSION.SDK_INT >= 33) RenderNode("SkyTimeScrubSky") else null
    private var skyShader: SkyTimeScrubSkyShader? = null
    private var uiShader: SkyTimeScrubSkyShader? = null
    private val uniforms = SkyTimeScrubUniforms()
    private var shaderFailed = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rr = RectF()
    private val tint = DoubleArray(9)
    private var groupAlpha = 1.0
    private var lastClock = 0.0

    private val sunPath = PathParser.createPathFromPathData("M12 2V4M12 20V22M2 12H4M20 12H22M5 5L6.5 6.5M17.5 17.5L19 19M5 19L6.5 17.5M17.5 6.5L19 5")
    private val arrowPath = PathParser.createPathFromPathData("M3 10.3L21 3L13.6 21L11.1 12.9Z")
    private val closeX = PathParser.createPathFromPathData("M5.5 5.5L10.5 10.5M10.5 5.5L5.5 10.5")
    private val dash = DashPathEffect(floatArrayOf(2f, 2.5f), 0f)

    // layout-dependent hit rects, rebuilt every frame by draw()
    private val chipRect = RectF()
    private val controlRects = Array(3) { RectF() }
    private val hintCloseRect = RectF()
    private val rulerRect = RectF()

    private fun hintLive(e: SkyTimeScrubEngine) = hintText != null && !e.hintDismissed && !e.scheduleOpen

    fun chipWidthPx(l: Theme.Layout, label: String, e: SkyTimeScrubEngine): Double {
        text.typeface = medium
        text.textSize = (Theme.TIME_SIZE * l.s * fontScale).toFloat()
        val w = (Theme.CHIP_PADDING * 2 + 8) * l.s + text.measureText(label)
        return M.clamp(w, Theme.CHIP_MIN_W * l.s, min(l.width - 32 * l.s, Theme.CHIP_MAX_W * l.s * max(1.0, fontScale)))
    }

    // ---- hit testing (dp) -----------------------------------------------------------------------------------------------------

    fun hit(x: Double, y: Double, l: Theme.Layout, e: SkyTimeScrubEngine): SkyTimeScrubHit {
        val fx = x.toFloat()
        val fy = y.toFloat()
        for (i in 0 until 3) if (controlRects[i].contains(fx, fy)) return SkyTimeScrubHit.values()[1 + i]
        if (hintLive(e) && groupAlpha > 0.01 && hintCloseRect.contains(fx, fy)) return SkyTimeScrubHit.HINT_CLOSE
        if (e.chipVisible && chipRect.contains(fx, fy)) return SkyTimeScrubHit.CHIP
        if (rulerRect.contains(fx, fy)) return SkyTimeScrubHit.RULER
        val sun = M.solarAt(e.minutes, e.day)
        val night = sun.elevation < -2
        if (!(night && e.held < 0.3)) {
            val sy = if (night) M.archY(sun.x, l.arcCentre / l.s, l.arcAmplitude / l.s) * l.s
            else l.horizon - M.sunHeight(sun.elevation, e.day, e.projection) * l.s
            val r = M.GRAB_RADIUS
            if (x >= sun.x * l.s - r && x <= sun.x * l.s + r && y >= sy - r && y <= sy + r) return SkyTimeScrubHit.SUN_GRAB
        }
        return SkyTimeScrubHit.NONE
    }

    fun rect(hit: SkyTimeScrubHit): RectF = when (hit) {
        SkyTimeScrubHit.SUN_BUTTON -> controlRects[0]
        SkyTimeScrubHit.CITY_BUTTON -> controlRects[1]
        SkyTimeScrubHit.GRID_BUTTON -> controlRects[2]
        SkyTimeScrubHit.CHIP -> chipRect
        SkyTimeScrubHit.HINT_CLOSE -> hintCloseRect
        else -> rulerRect
    }

    // ---- draw -----------------------------------------------------------------------------------------------------------------

    fun draw(c: Canvas, density: Float, l: Theme.Layout, e: SkyTimeScrubEngine, reduce: Boolean) {
        val s = l.s
        val w = l.width.toFloat()
        val h = l.height.toFloat()
        val dtClock = max(0.0, min(0.05, e.clock - lastClock))
        lastClock = e.clock
        val minutes = e.minutes
        val sun = M.solarAt(minutes, e.day)
        SkyTimeScrubTint.at(sun.elevation, tint)
        val sunH = M.sunHeight(sun.elevation, e.day, e.projection)

        fill.shader = null
        fill.color = Theme.PAGE
        c.drawRect(0f, 0f, w, h, fill)

        // the sky
        if (!drawShaders(c, density, l, e, sun, sunH)) drawFallback(c, l, e, sun, sunH)

        // the chip
        val label = M.timeLabel(minutes, e.day, e.rest)
        val chipW = chipWidthPx(l, label, e).toFloat()
        val chipH = (Theme.CHIP_H * s).toFloat()
        val chipLeft = ((l.width - chipW) / 2).toFloat()
        val chipTop = l.chipY.toFloat()
        val chipBase = if (M.circularDiff(M.wrapMinutes(minutes), e.rest) > 1) 1.0 else M.clamp(e.held * 4)
        val hintLive = hintLive(e)
        val chipAlpha = chipBase * (if (hintLive && !reduce) 1 - M.smooth((e.hintOn - 0.12) / 0.26) else 1.0)
        val k = Theme.RIM_CHIP
        val chipRgb = floatArrayOf(((Theme.CHIP shr 16) and 255).toFloat(), ((Theme.CHIP shr 8) and 255).toFloat(), (Theme.CHIP and 255).toFloat())
        if (chipAlpha > 0.003) {
            fill.color = SkyTimeScrubTint.argb(M.mix(chipRgb[0].toDouble(), tint[3], k), M.mix(chipRgb[1].toDouble(), tint[4], k), M.mix(chipRgb[2].toDouble(), tint[5], k), chipAlpha)
            rr.set(chipLeft, chipTop, chipLeft + chipW, chipTop + chipH)
            c.drawRoundRect(rr, chipH / 2, chipH / 2, fill)
            text.typeface = medium
            text.textSize = (Theme.TIME_SIZE * s * fontScale).toFloat()
            text.fontFeatureSettings = "'tnum'"
            text.color = withAlpha(Theme.INK, chipAlpha)
            text.textAlign = Paint.Align.CENTER
            val fm = text.fontMetrics
            c.drawText(label, chipLeft + chipW / 2, chipTop + chipH / 2 - (fm.ascent + fm.descent) / 2, text)
        }
        // the chip's touch target: a 48 pt box from the chip's top, as wide as its widest box
        val boxW = min(l.width - 32 * s, Theme.CHIP_MAX_W * s * max(1.0, fontScale))
        chipRect.set(((l.width - boxW) / 2).toFloat(), chipTop, ((l.width + boxW) / 2).toFloat(), chipTop + (Theme.CHIP_TARGET * s).toFloat())

        // the schedule panel and rows
        drawSchedule(c, l, e)

        // the hint
        if (hintText != null) drawHint(c, l, e, reduce, hintLive, dtClock)

        // the controls
        drawControls(c, l, e, sun)

        val rl = (20 * s).toFloat()
        rulerRect.set(rl, (l.rulerY - 40 * s).toFloat(), (l.width - 20 * s).toFloat(), (l.rulerY - 40 * s + 88 * s).toFloat())
    }

    private fun withAlpha(color: Int, a: Double): Int {
        val alpha = ((color ushr 24) * M.clamp(a)).roundToInt()
        return (alpha shl 24) or (color and 0xFFFFFF)
    }

    // ---- sky ------------------------------------------------------------------------------------------------------------------

    private fun fillUniforms(l: Theme.Layout, e: SkyTimeScrubEngine, sun: M.SolarState, sunH: Double, layer: Int) {
        val s = l.s
        val u = uniforms
        u.horizon = (l.horizon / s).toFloat()
        u.arcCentre = (l.arcCentre / s).toFloat()
        u.arcAmplitude = (l.arcAmplitude / s).toFloat()
        u.rulerY = (l.rulerY / s).toFloat()
        u.rulerOffset = ((M.wrapMinutes(e.minutes) - e.rest) / M.MINUTES_PER_POINT).toFloat()
        u.sunX = sun.x.toFloat()
        u.sunH = sunH.toFloat()
        u.elevation = sun.elevation.toFloat()
        u.ev = e.ev.toFloat()
        u.evTarget = M.evTargetAt(sun.elevation).toFloat()
        u.held = e.held.toFloat()
        u.press = if (e.down) 1f else 0f
        u.turbidity = 2f
        u.layer = layer.toFloat()
        u.tick = floatArrayOf(((Theme.TICK shr 16) and 255) / 255f, ((Theme.TICK shr 8) and 255) / 255f, (Theme.TICK and 255) / 255f)
        u.ground = floatArrayOf(((Theme.PAGE shr 16) and 255) / 255f, ((Theme.PAGE shr 8) and 255) / 255f, (Theme.PAGE and 255) / 255f)
    }

    private fun drawShaders(c: Canvas, density: Float, l: Theme.Layout, e: SkyTimeScrubEngine, sun: M.SolarState, sunH: Double): Boolean {
        val node = skyNode ?: return false
        if (Build.VERSION.SDK_INT < 33 || shaderFailed || !c.isHardwareAccelerated) return false
        if (skyShader == null) {
            try {
                skyShader = SkyTimeScrubSkyShader()
                uiShader = SkyTimeScrubSkyShader()
            } catch (error: IllegalArgumentException) {
                Log.w("SkyTimeScrub", "Runtime shader unavailable; using the gradient fallback", error)
                shaderFailed = true
                return false
            }
        }
        val sky = skyShader ?: return false
        val ui = uiShader ?: return false
        val raster = Theme.SKY_RASTER_SCALE.toFloat()
        val wpx = ceil(l.width * density / raster).toInt().coerceAtLeast(1)
        val hpx = ceil(l.height * density / raster).toInt().coerceAtLeast(1)
        fillUniforms(l, e, sun, sunH, 0)
        uniforms.scale = (l.s * density / raster).toFloat()
        sky.apply(uniforms)
        node.setPosition(0, 0, wpx, hpx)
        node.setUseCompositingLayer(true, null)
        val rc = node.beginRecording(wpx, hpx)
        try {
            rc.drawRect(0f, 0f, wpx.toFloat(), hpx.toFloat(), sky.paint)
        } finally {
            node.endRecording()
        }
        c.save()
        c.scale(raster / density, raster / density)
        c.drawRenderNode(node)
        c.restore()

        // the instrument layer at full resolution, only over the band the shader draws in
        fillUniforms(l, e, sun, sunH, 1)
        uniforms.scale = l.s.toFloat()
        ui.apply(uniforms)
        val sunY = (l.horizon / l.s) - sunH
        val top = min(min(l.arcCentre / l.s - l.arcAmplitude / l.s - 10, l.rulerY / l.s - 32), sunY - 10) * l.s - 2
        val bottom = max(l.arcCentre / l.s + l.arcAmplitude / l.s + 10, l.rulerY / l.s + 32) * l.s + 2
        c.drawRect(0f, max(0.0, top).toFloat(), l.width.toFloat(), min(l.height, bottom).toFloat(), ui.paint)
        return true
    }

    /** Below API 33: the same picture from gradients and paths (sky, ground, sun, dotted arch, ruler, handle). */
    private fun drawFallback(c: Canvas, l: Theme.Layout, e: SkyTimeScrubEngine, sun: M.SolarState, sunH: Double) {
        val s = l.s.toFloat()
        val w = l.width.toFloat()
        val hz = l.horizon.toFloat()
        val zen = SkyTimeScrubTint.argb(tint[0], tint[1], tint[2])
        val hor = SkyTimeScrubTint.argb(tint[3], tint[4], tint[5])
        fill.shader = LinearGradient(0f, 0f, 0f, hz, zen, hor, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, hz, fill)
        fill.shader = LinearGradient(0f, hz, 0f, hz + 150 * s, withAlpha(hor, 0.55), Theme.PAGE, Shader.TileMode.CLAMP)
        c.drawRect(0f, hz, w, l.height.toFloat(), fill)
        fill.shader = null
        // the sun: a glow and a disc, flattened at the horizon, cut by the ground
        val sx = (sun.x * l.s).toFloat()
        val sy = hz - (sunH * l.s).toFloat()
        val vis = M.clamp((sun.elevation + 2.5) / 2.5)
        if (vis > 0) {
            val sc = SkyTimeScrubTint.argb(tint[6], tint[7], tint[8])
            val glowR = 90f * s
            fill.shader = RadialGradient(sx, sy, glowR, intArrayOf(withAlpha(sc, 0.55 * vis), withAlpha(sc, 0.0)), null, Shader.TileMode.CLAMP)
            c.save(); c.clipRect(0f, 0f, w, hz + 18 * s)
            c.drawCircle(sx, sy, glowR, fill)
            fill.shader = null
            fill.color = withAlpha(sc, vis)
            val flat = M.mix(0.72, 1.0, M.smooth(sun.elevation / 2))
            c.clipRect(0f, 0f, w, hz)
            rr.set(sx - 7.7f * s, sy - (7.7 * flat).toFloat() * s, sx + 7.7f * s, sy + (7.7 * flat).toFloat() * s)
            c.drawOval(rr, fill)
            c.restore()
        }
        // the dotted arch
        val path = Path()
        var x = 0.0
        var first = true
        while (x <= 402.0) {
            val y = M.archY(x, l.arcCentre / l.s, l.arcAmplitude / l.s) * l.s
            if (first) { path.moveTo((x * l.s).toFloat(), y.toFloat()); first = false } else path.lineTo((x * l.s).toFloat(), y.toFloat())
            x += 3.0
        }
        stroke.color = 0x38919EA6
        stroke.strokeWidth = 1f
        stroke.pathEffect = dash
        c.drawPath(path, stroke)
        stroke.pathEffect = null
        // the ruler and its handle
        val offset = (M.wrapMinutes(e.minutes) - e.rest) / M.MINUTES_PER_POINT
        val ry = l.rulerY.toFloat()
        val cell0 = Math.floor((26 - 26 + offset) / 12.0).toInt() - 1
        for (cell in cell0..cell0 + 40) {
            val tx = 26 + cell * 12.0 - offset
            if (tx < 25 || tx > 377) continue
            val crossed = tx < 201
            fill.color = if (crossed) Theme.TICK else 0xFF171A1C.toInt()
            val half = (7 + 10 * exp(-((tx - 201) / 22).let { it * it }) * M.clamp(e.held * 1.0)).toFloat()
            rr.set((tx * l.s - 1.1 * l.s).toFloat(), ry - half * s, (tx * l.s + 1.1 * l.s).toFloat(), ry + half * s)
            c.drawRoundRect(rr, 0.8f * s, 0.8f * s, fill)
        }
        if (e.held > 0.05) {
            stroke.color = withAlpha(0xFF2A343C.toInt(), e.held)
            stroke.strokeWidth = 1.2f
            val hw = M.mix(1.1, 14.0, M.smooth(e.held)).toFloat()
            val hh = M.mix(7.0, 26.0, M.smooth(e.held)).toFloat()
            rr.set(w / 2 - hw * s, ry - hh * s, w / 2 + hw * s, ry + hh * s)
            c.drawRoundRect(rr, min(hw, 13.5f) * s, min(hw, 13.5f) * s, stroke)
        }
    }

    // ---- hint -----------------------------------------------------------------------------------------------------------------

    private fun drawHint(c: Canvas, l: Theme.Layout, e: SkyTimeScrubEngine, reduce: Boolean, live: Boolean, dt: Double) {
        val s = l.s
        // the group: fully on while live (a fade tied to the fold under Reduce Motion), a 160 ms fade out otherwise
        val target = if (live) (if (reduce) e.hintOn else 1.0) else 0.0
        groupAlpha = if (live) target else max(0.0, groupAlpha - dt / 0.16)
        if (groupAlpha <= 0.003) return
        val h = if (reduce) 1.0 else e.hintOn
        val open = M.smooth((h - 0.35) / 0.45)
        val cx = l.width / 2
        // stem and its dot
        val len = max(0.0, l.tipY - (l.hintY + l.hintH))
        val grow = M.smooth((h - 0.1) / 0.3) * len
        fill.color = withAlpha(Theme.ACCENT, groupAlpha)
        if (grow > 0.1) c.drawRect((cx - Theme.HINT_STEM * s / 2).toFloat(), (l.tipY - grow).toFloat(), (cx + Theme.HINT_STEM * s / 2).toFloat(), l.tipY.toFloat(), fill)
        val tipA = M.smooth((h - 0.06) / 0.1)
        if (tipA > 0.003) {
            val tipS = 0.4 + 0.6 * M.smooth((h - 0.06) / 0.14)
            fill.color = withAlpha(Theme.ACCENT, groupAlpha * tipA)
            c.drawCircle(cx.toFloat(), l.tipY.toFloat(), (Theme.HINT_TIP_R * s * tipS).toFloat(), fill)
        }
        // the pill
        val pw = M.mix(Theme.CHIP_MIN_W * s, l.hintW, open)
        val ph = M.mix(Theme.CHIP_H * s, l.hintH, open)
        val left = (l.width - pw) / 2
        val top = l.hintY + l.hintH - ph
        val radius = M.mix(Theme.CHIP_H * s / 2, Theme.HINT_RADIUS * s, open)
        val pillA = groupAlpha * M.smooth((h - 0.26) / 0.12)
        if (pillA > 0.003) {
            fill.color = withAlpha(Theme.ACCENT, pillA)
            rr.set(left.toFloat(), top.toFloat(), (left + pw).toFloat(), (top + ph).toFloat())
            c.drawRoundRect(rr, radius.toFloat(), radius.toFloat(), fill)
            val labelA = pillA * (if (reduce) 1.0 else M.smooth((e.hintOn - 0.72) / 0.28))
            if (labelA > 0.003) {
                c.save()
                c.clipRect(rr)
                val gl = cx - l.hintW / 2
                text.typeface = medium
                text.textSize = (Theme.LABEL_SIZE * s * fontScale).toFloat()
                text.fontFeatureSettings = ""
                text.color = withAlpha(Theme.ON_ACCENT, labelA)
                text.textAlign = Paint.Align.LEFT
                val fm = text.fontMetrics
                val cy = top + ph / 2
                c.drawText(hintText ?: "", (gl + 10 * s).toFloat(), (cy - (fm.ascent + fm.descent) / 2).toFloat(), text)
                // the close mark: 16 pt in a 24 pt box at the label group's right edge
                val k = (Theme.HINT_CLOSE * s / 16).toFloat()
                val ix = (gl + l.hintW - 4 * s - max(24 * s, Theme.HINT_CLOSE * s) / 2).toFloat()
                c.save()
                c.translate(ix - 8 * k, (cy - 8 * k).toFloat())
                c.scale(k, k)
                fill.color = withAlpha(Theme.ON_ACCENT, labelA)
                c.drawCircle(8f, 8f, 7f, fill)
                stroke.color = withAlpha(Theme.ACCENT, labelA)
                stroke.strokeWidth = 1.4f
                c.drawPath(closeX, stroke)
                c.restore()
                c.restore()
            }
        }
        // the dismiss target: 48 dp square at the pill's right end
        val dl = (l.width - l.hintX - 16 * s - 24).toFloat()
        val dtop = (l.hintY + l.hintH / 2 - 24).toFloat()
        hintCloseRect.set(dl, dtop, dl + 48f, dtop + 48f)
    }

    // ---- schedule -------------------------------------------------------------------------------------------------------------

    private fun drawSchedule(c: Canvas, l: Theme.Layout, e: SkyTimeScrubEngine) {
        val s = l.s
        val reveal = e.revealValue
        if (reveal > 0.003) {
            fill.color = withAlpha(Theme.PANEL, reveal)
            rr.set((Theme.SCHEDULE_X * s).toFloat(), l.scheduleY.toFloat(), ((Theme.SCHEDULE_X + Theme.SCHEDULE_W) * s).toFloat(), (l.scheduleY + l.scheduleH).toFloat())
            val r = (Theme.SCHEDULE_H / 8 * s).toFloat()
            c.drawRoundRect(rr, r, r, fill)
        }
        val left = ((Theme.SCHEDULE_X + Theme.SCHEDULE_PAD) * s).toFloat()
        val right = (l.width - (Theme.SCHEDULE_X + Theme.SCHEDULE_PAD) * s).toFloat()
        val titles = arrayOf(Theme.NOON_TITLE, Theme.SUNSET_TITLE, Theme.NIGHT_TITLE)
        val rowT = M.rowTimes(e.day)
        c.save()
        c.clipRect(left - 4f, l.scheduleY.toFloat(), right + 4f, (l.scheduleY + l.scheduleH).toFloat())
        for (i in 0 until 3) {
            val p = M.smooth((e.wake - i * M.ROWS_STAGGER) / M.ROWS_FADE)
            val alpha = M.ROWS_REST + (1 - M.ROWS_REST) * max(p, reveal)
            val rowTop = (l.scheduleY + i * l.rowH).toFloat()
            val cy = rowTop + (l.rowH / 2).toFloat()
            // title
            text.typeface = regular
            text.textSize = (Theme.BODY_SIZE * s * fontScale).toFloat()
            text.fontFeatureSettings = ""
            text.color = withAlpha(Theme.INK, alpha)
            text.textAlign = Paint.Align.LEFT
            var fm = text.fontMetrics
            c.drawText(titles[i], left, cy - (fm.ascent + fm.descent) / 2, text)
            // value: dims to 78 % of its colour and flares as the scrub crosses it, swelling from its right edge
            val color = Theme.ROW_ACCENTS[i]
            val age = e.clock - e.tickAt[i]
            val env = if (age >= 0 && age < M.ROWS_TICK) exp(-age / 0.09) else 0.0
            val cr = (color shr 16) and 255
            val cg = (color shr 8) and 255
            val cb = color and 255
            val dim = 0.78
            text.typeface = semibold
            text.textSize = (Theme.VALUE_SIZE * s * fontScale).toFloat()
            text.fontFeatureSettings = "'tnum'"
            text.color = SkyTimeScrubTint.argb(M.mix(cr * dim, cr.toDouble(), env), M.mix(cg * dim, cg.toDouble(), env), M.mix(cb * dim, cb.toDouble(), env), alpha)
            text.textAlign = Paint.Align.RIGHT
            fm = text.fontMetrics
            c.save()
            val sc = (1 + 1.5 * M.ROWS_TICK_SCALE * env).toFloat()
            c.scale(sc, sc, right, cy)
            c.drawText(M.timeLabel(rowT[i], e.day, e.rest, false), right, cy - (fm.ascent + fm.descent) / 2, text)
            c.restore()
        }
        c.restore()
    }

    // ---- controls -------------------------------------------------------------------------------------------------------------

    private fun drawControls(c: Canvas, l: Theme.Layout, e: SkyTimeScrubEngine, sun: M.SolarState) {
        val s = l.s
        val h = max(48.0, Theme.CONTROLS_H * s).toFloat()
        val radius = (Theme.CONTROLS_H * s / 2).toFloat()
        val xs = doubleArrayOf(Theme.CONTROLS_LEFT, Theme.CONTROLS_CITY_X, Theme.CONTROLS_RIGHT_X)
        for (i in 0 until 3) {
            val wide = i == 1
            val left = (xs[i] * s).toFloat()
            val width = ((if (wide) Theme.CONTROLS_CITY_W else Theme.CONTROLS_H) * s).toFloat()
            val top = l.controlsY.toFloat()
            controlRects[i].set(left, top, left + width, top + h)
            val sc = e.press[i].value.toFloat()
            val alpha = if (sc < 1f) 0.7 else 1.0
            c.save()
            c.scale(sc, sc, left + width / 2, top + h / 2)
            rr.set(left, top, left + width, top + h)
            fill.color = withAlpha(Theme.CONTROL, alpha)
            c.drawRoundRect(rr, radius, radius, fill)
            stroke.strokeWidth = 0.5f
            stroke.color = withAlpha(Theme.OUTLINE, alpha)
            rr.inset(0.25f, 0.25f)
            c.drawRoundRect(rr, radius, radius, stroke)
            // the icon (and the city's name beside it)
            val icon = (Theme.CONTROLS_ICON * s).toFloat()
            val gap = (8 * s).toFloat()
            var nameW = 0f
            if (wide) {
                text.typeface = regular
                text.textSize = (Theme.BODY_SIZE * s * fontScale).toFloat()
                text.fontFeatureSettings = ""
                nameW = text.measureText(Theme.CITY)
            }
            val total = icon + (if (wide) gap + nameW else 0f)
            val ix = left + (width - total) / 2
            val iy = top + (h - icon) / 2
            c.save()
            c.translate(ix, iy)
            val k = icon / 24f
            c.scale(k, k)
            when (i) {
                1 -> {
                    val kk = Theme.RIM_ARROW
                    fill.color = withAlpha(SkyTimeScrubTint.argb(M.mix(0x44.toDouble(), tint[3], kk), M.mix(0xc2.toDouble(), tint[4], kk), M.mix(0xf5.toDouble(), tint[5], kk)), alpha)
                    c.drawPath(arrowPath, fill)
                }
                2 -> {
                    fill.color = withAlpha(Theme.MUTED, alpha)
                    rr.set(3f, 4f, 13f, 11f); c.drawRoundRect(rr, 1f, 1f, fill)
                    rr.set(3f, 14f, 13f, 20f); c.drawRoundRect(rr, 1f, 1f, fill)
                    rr.set(16f, 4f, 21f, 20f); c.drawRoundRect(rr, 1f, 1f, fill)
                }
                else -> {
                    fill.color = withAlpha(Theme.MUTED, alpha)
                    c.drawCircle(12f, 12f, 4f, fill)
                    stroke.color = withAlpha(Theme.MUTED, alpha)
                    stroke.strokeWidth = 1.5f
                    c.drawPath(sunPath, stroke)
                }
            }
            c.restore()
            if (wide) {
                text.color = withAlpha(Theme.INK, alpha)
                text.textAlign = Paint.Align.LEFT
                val fm = text.fontMetrics
                c.drawText(Theme.CITY, ix + icon + gap, top + h / 2 - (fm.ascent + fm.descent) / 2, text)
            }
            // the sky lights the UI: a 1 dp rim along the top edge in the horizon's colour, as bright as the horizon is
            val rimA = Theme.RIM_ALPHA * SkyTimeScrubTint.luminance(tint[3], tint[4], tint[5])
            stroke.color = withAlpha(SkyTimeScrubTint.argb(tint[3], tint[4], tint[5]), rimA)
            stroke.strokeWidth = Theme.RIM_WIDTH.toFloat()
            c.save()
            c.clipRect(left - 2, top - 2, left + width + 2, top + h / 2)
            rr.set(left, top, left + width, top + h)
            rr.inset(0.5f, 0.5f)
            c.drawRoundRect(rr, radius, radius, stroke)
            c.restore()
            c.restore()
        }
    }
}

