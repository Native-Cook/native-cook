package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.ngocdevv.nativecook.blocks.pickers.neoncontourmood.NeonContourMoodMotion as M
import com.ngocdevv.nativecook.blocks.pickers.neoncontourmood.NeonContourMoodTheme as Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class NeonContourMoodHit { NONE, NEXT, SLIDER, CLOSE, HERO }

/**
 * The screen's drawing (RN `NcmScene` and the contour field): the field shader inside the rounded sheet (API 33+; rings drawn as glowing
 * strokes below), then the grabber, title, close, the mood word as neon tubes and its tag, the question, the slider with the thumb and
 * the stops, the endpoint labels and the button. Everything is in dp: the caller scales the canvas by the density.
 */
internal class NeonContourMoodRenderer(
    assets: AssetManager,
    private val moods: List<NeonContourMoodMood>,
    private val pack: NeonContourMoodPack,
    private val l: Theme.Layout,
) {
    private val regular = Typeface.createFromAsset(assets, "neoncontourmood/neoncontourmood_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "neoncontourmood/neoncontourmood_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(assets, "neoncontourmood/neoncontourmood_Inter-SemiBold.ttf")
    private val display = Typeface.createFromAsset(assets, "neoncontourmood/neoncontourmood_Nunito-ExtraBold.ttf")

    private var fieldShader: NeonContourMoodFieldShader? = null
    private var thumbShader: NeonContourMoodThumbShader? = null
    private var shaderFailed = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rr = RectF()
    private val path = Path()
    private val tmp = DoubleArray(3)

    private val s = l.s
    private val fs = l.fs
    private val w = l.w.toFloat()
    private val last = max(1, moods.size - 1)

    // glyph widths of every word at the hero size, and the letters' x positions
    private val displaySize = (Theme.DISPLAY_SIZE * l.heroS).toFloat()
    private val displayTracking = (Theme.DISPLAY_TRACKING * l.heroS).toFloat()
    private val wordX: List<FloatArray>
    private val wordLetters: List<List<String>>
    private val tagTracking = (Theme.TAG_TRACKING * s).toFloat()
    val tagWidths: DoubleArray
    val lens: IntArray = IntArray(moods.size) { moods[it].word.length }

    init {
        text.typeface = display
        text.textSize = displaySize
        val xs = ArrayList<FloatArray>()
        val letters = ArrayList<List<String>>()
        for (m in moods) {
            val chars = m.word.map { it.toString() }
            val adv = FloatArray(chars.size) { text.measureText(chars[it]) }
            val total = adv.sum() + max(0, chars.size - 1) * displayTracking
            var cursor = w / 2 - total / 2
            val out = FloatArray(chars.size)
            for (i in chars.indices) { out[i] = cursor; cursor += adv[i] + displayTracking }
            xs.add(out); letters.add(chars)
        }
        wordX = xs
        wordLetters = letters
        text.typeface = medium
        text.textSize = (Theme.TAG_SIZE * s * fs).toFloat()
        tagWidths = DoubleArray(moods.size) { i ->
            val t = moods[i].tag
            if (t == null) 0.0 else trackedWidth(t) + Theme.TAG_PX * 2 * s
        }
    }

    private fun trackedWidth(t: String): Double {
        var sum = 0f
        for (ch in t) sum += text.measureText(ch.toString())
        return (sum + max(0, t.length - 1) * tagTracking).toDouble()
    }

    // hit rects
    private val nextRect = RectF(
        (Theme.BUTTON_X * s).toFloat(), l.buttonY.toFloat(), ((Theme.BUTTON_X + Theme.BUTTON_W) * s).toFloat(), (l.buttonY + max(48.0, l.buttonH)).toFloat(),
    )
    private val sliderRect = RectF(l.sliderX.toFloat(), (l.sliderY - 4).toFloat(), (l.sliderX + l.sliderW).toFloat(), (l.sliderY + l.sliderH + 4).toFloat())
    private val closeRect = RectF(
        (l.closeX - 2 * s).toFloat(), (l.closeY - 2 * s).toFloat(), (l.closeX - 2 * s + max(48.0, 48 * s)).toFloat(), (l.closeY - 2 * s + max(48.0, 48 * s)).toFloat(),
    )
    private val heroRect = RectF(0f, l.heroTouchTop.toFloat(), w, max(l.heroTouchTop, l.heroTouchBottom).toFloat())

    fun hit(x: Double, y: Double): NeonContourMoodHit {
        val fx = x.toFloat()
        val fy = y.toFloat()
        return when {
            nextRect.contains(fx, fy) -> NeonContourMoodHit.NEXT
            sliderRect.contains(fx, fy) -> NeonContourMoodHit.SLIDER
            closeRect.contains(fx, fy) -> NeonContourMoodHit.CLOSE
            heroRect.contains(fx, fy) -> NeonContourMoodHit.HERO
            else -> NeonContourMoodHit.NONE
        }
    }

    // ---- colours --------------------------------------------------------------------------------------------------------------

    private fun argb(r: Double, g: Double, b: Double, a: Double): Int {
        fun c(v: Double) = min(255, max(0, (v * 255).roundToInt()))
        return (c(a) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    private fun rgb(color: Int) = doubleArrayOf(((color shr 16) and 255) / 255.0, ((color shr 8) and 255) / 255.0, (color and 255) / 255.0)
    private val inkRgb = rgb(Theme.INK)
    private val secondaryRgb = rgb(Theme.SECONDARY)
    private val outlineRgb = rgb(Theme.OUTLINE)
    private val white = doubleArrayOf(1.0, 1.0, 1.0)
    private val groundRgb = rgb(Theme.PAGE)

    /** The ramp colour of the mood under the thumb (sRGB) mixed over [base] by [amount]. */
    private fun hueMix(mood: Double, stop: Double, base: DoubleArray, amount: Double, alpha: Double): Int {
        NeonContourMoodShaderMath.rampAt(tmp, 0, pack, mood, stop, false)
        return argb(base[0] + (tmp[0] - base[0]) * amount, base[1] + (tmp[1] - base[1]) * amount, base[2] + (tmp[2] - base[2]) * amount, alpha)
    }

    private fun withAlpha(color: Int, a: Double): Int = (((color ushr 24) * M.clamp(a)).roundToInt() shl 24) or (color and 0xFFFFFF)

    private fun blurFilter(sigma: Double): BlurMaskFilter? =
        if (sigma < 0.05) null else BlurMaskFilter(max(((sigma - 0.5) / 0.57735).toFloat(), 0.1f), BlurMaskFilter.Blur.NORMAL)

    private val blurCache = HashMap<Int, BlurMaskFilter>()
    private fun blur(sigma: Double): BlurMaskFilter? {
        val key = (sigma * 8).roundToInt()
        return blurCache.getOrPut(key) { blurFilter(key / 8.0) ?: return null }
    }

    // ---- draw -----------------------------------------------------------------------------------------------------------------

    fun draw(c: Canvas, e: NeonContourMoodEngine, density: Float) {
        val look = e.look
        val lab = e.lab
        val mood = look.mood
        val fade = e.reduceFade.value
        val h = l.h.toFloat()

        fill.shader = null
        fill.maskFilter = null
        fill.color = Theme.PAGE
        c.drawRect(0f, 0f, w, h, fill)

        // the sheet with the field
        val sheetY = l.sheetY.toFloat()
        val sheetR = (Theme.SHEET_RADIUS * s).toFloat()
        val layer = c.saveLayerAlpha(0f, 0f, w, h, (255 * M.clamp(fade)).roundToInt())
        path.reset()
        rr.set(0f, sheetY, w, h)
        path.addRoundRect(rr, sheetR, sheetR, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        if (!drawField(c, e)) drawFieldFallback(c, e)
        c.restore()
        c.restoreToCount(layer)

        drawChrome(c, e, mood)
        drawWordsAndTags(c, e, lab, fade)
        drawQuestionAndSlider(c, e, mood)
        drawEndpointsAndButton(c, e, mood)
    }

    private fun drawField(c: Canvas, e: NeonContourMoodEngine): Boolean {
        if (Build.VERSION.SDK_INT < 33 || shaderFailed) return false
        if (fieldShader == null) {
            try {
                fieldShader = NeonContourMoodFieldShader()
                thumbShader = NeonContourMoodThumbShader()
            } catch (error: IllegalArgumentException) {
                Log.w("NeonContourMood", "Runtime shader unavailable; using the stroke fallback", error)
                shaderFailed = true
                return false
            }
        }
        val fsh = fieldShader ?: return false
        fsh.apply(e.field)
        c.drawRect(0f, 0f, w, l.h.toFloat(), fsh.paint)
        return true
    }

    /** Below API 33: the rings as glowing strokes of the notched rounded boxes, on the sheet colour. */
    private fun drawFieldFallback(c: Canvas, e: NeonContourMoodEngine) {
        fill.color = Theme.SHEET_TOP
        c.drawRect(0f, 0f, w, l.h.toFloat(), fill)
        val cx = e.field.center[0]
        val cy = e.field.center[1]
        val hs = l.heroS.toFloat()
        val look = e.look
        // core
        val a0 = e.field.shapeA
        fill.color = Theme.CORE
        rr.set(cx - a0[0] * hs, cy - a0[1] * hs, cx + a0[0] * hs, cy + a0[1] * hs)
        c.drawRoundRect(rr, a0[2] * hs, a0[2] * hs, fill)
        for (k in 6 downTo 0) {
            val off = Theme.RING_OFFSETS[k] * (e.field.spacing / 26.0)
            val sa = e.field.shapeA
            val o = k * 4
            val strength = Theme.RING_STRENGTH[k]
            NeonContourMoodShaderMath.rampAt(tmp, 0, pack, look.echoes[k], min(2.0, if (k < 2) 1.0 else if (k < 4) 1.0 + (k - 2) / 2.0 else 2.0), false)
            val colour = argb(tmp[0], tmp[1], tmp[2], 1.0)
            val bx = (sa[o] + off).toFloat() * hs
            val by = (sa[o + 1] + off).toFloat() * hs
            val corner = (sa[o + 2] + off).toFloat() * hs
            rr.set(cx - bx, cy - by, cx + bx, cy + by)
            stroke.maskFilter = blur(9.0 * (0.4 + strength))
            stroke.strokeWidth = (6 * hs * (0.5 + strength)).toFloat()
            stroke.color = withAlpha(colour, 0.55 * (0.25 + strength))
            c.drawRoundRect(rr, corner, corner, stroke)
            stroke.maskFilter = null
            stroke.strokeWidth = (0.9 * hs).toFloat()
            stroke.color = withAlpha(colour, min(1.0, 0.35 + strength))
            c.drawRoundRect(rr, corner, corner, stroke)
        }
        stroke.maskFilter = null
    }

    private fun drawChrome(c: Canvas, e: NeonContourMoodEngine, mood: Double) {
        // grabber
        fill.color = Theme.GRABBER
        rr.set(((l.w - Theme.GRABBER_W * s) / 2).toFloat(), (l.sheetY + 16 * s).toFloat(), ((l.w + Theme.GRABBER_W * s) / 2).toFloat(), (l.sheetY + 16 * s + Theme.GRABBER_H * s).toFloat())
        c.drawRoundRect(rr, (2 * s).toFloat(), (2 * s).toFloat(), fill)
        // title
        text.typeface = medium
        text.textSize = (Theme.TITLE_SIZE * s * fs).toFloat()
        text.letterSpacing = 0f
        text.color = Theme.INK
        text.textAlign = Paint.Align.CENTER
        text.maskFilter = null
        for ((i, line) in l.titleLines.withIndex()) c.drawText(line, w / 2, (l.titleY + i * Theme.TITLE_LINE * s * fs).toFloat(), text)
        // close
        val op = 1 - e.closePress.value * 0.4
        val cx = (l.closeX + 22 * s).toFloat()
        val cy = (l.closeY + 22 * s).toFloat()
        stroke.maskFilter = null
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = (0.65 * s).toFloat()
        stroke.color = withAlpha(hueMix(mood, 0.0, outlineRgb, Theme.CLOSE_MIX, 1.0), op)
        c.drawCircle(cx, cy, (22 * s).toFloat(), stroke)
        stroke.strokeWidth = (Theme.CLOSE_STROKE * s).toFloat()
        stroke.color = withAlpha(Theme.CLOSE, op)
        val x0 = (l.closeX + 14 * s).toFloat()
        val x1 = (l.closeX + 30 * s).toFloat()
        val y0 = (l.closeY + 14 * s).toFloat()
        val y1 = (l.closeY + 30 * s).toFloat()
        c.drawLine(x0, y0, x1, y1, stroke)
        c.drawLine(x1, y0, x0, y1, stroke)
    }

    private fun drawWordsAndTags(c: Canvas, e: NeonContourMoodEngine, lab: DoubleArray, fade: Double) {
        val layer = c.saveLayerAlpha(0f, 0f, w, l.h.toFloat(), (255 * M.clamp(fade)).roundToInt())
        val baseline = (l.heroY + (Theme.DISPLAY_BASELINE - Theme.HERO_Y) * l.heroS).toFloat()
        val cyWord = baseline - displaySize * 0.36f
        text.typeface = display
        text.textSize = displaySize
        text.textAlign = Paint.Align.LEFT
        for (index in moods.indices) {
            val from = lab[M.L_FROM].toInt()
            val to = lab[M.L_TO].toInt()
            // a word draws only while it is the incoming one, the outgoing one, or settled on
            val shown = index == to || index == from
            if (!shown) continue
            val scale = M.labWord(lab, index).toFloat()
            c.save()
            c.translate(w / 2, cyWord)
            c.scale(scale, scale)
            c.translate(-w / 2, -cyWord)
            val color = moods[index].ramp[0]
            val chars = wordLetters[index]
            for (g in chars.indices) {
                val level = M.labLetter(lab, index, g, 0, 0.0)
                val bloom = M.labLetter(lab, index, g, 1, 0.0)
                val blurV = M.labLetter(lab, index, g, 2, M.GLOW_BLUR_TO)
                val x = wordX[index][g]
                if (bloom > 0.003) {
                    text.maskFilter = blur(blurV)
                    text.color = withAlpha(color, bloom)
                    c.drawText(chars[g], x, baseline, text)
                }
                if (level > 0.003) {
                    text.maskFilter = blur(Theme.HERO_WORD_GLOW)
                    text.color = withAlpha(color, level)
                    c.drawText(chars[g], x, baseline, text)
                    text.maskFilter = null
                    c.drawText(chars[g], x, baseline, text)
                }
            }
            text.maskFilter = null
            c.restore()
        }
        // the tag capsule
        val capW = lab[M.L_WIDTH]
        val capH = Theme.TAG_H * s * fs
        val capOp = M.clamp(capW / (Theme.TAG_PX * 2 * s))
        if (capOp > 0.003 && capW > 0.5) {
            stroke.maskFilter = null
            stroke.strokeWidth = (Theme.TAG_STROKE * s * fs).toFloat()
            stroke.color = withAlpha(Theme.INK, capOp)
            rr.set(((l.w - capW) / 2).toFloat(), (l.tagY - capH / 2).toFloat(), ((l.w + capW) / 2).toFloat(), (l.tagY + capH / 2).toFloat())
            c.drawRoundRect(rr, (Theme.TAG_R * s * fs).toFloat(), (Theme.TAG_R * s * fs).toFloat(), stroke)
        }
        text.typeface = medium
        text.textSize = (Theme.TAG_SIZE * s * fs).toFloat()
        text.maskFilter = null
        val lift = s * fs
        for ((index, m) in moods.withIndex()) {
            val t = m.tag ?: continue
            val op = M.labTag(lab, index, 0)
            if (op <= 0.003) continue
            c.save()
            val dy = (M.labTag(lab, index, 1) * lift).toFloat()
            val sc = M.labTag(lab, index, 2).toFloat()
            c.translate(w / 2, l.tagY.toFloat() + dy)
            c.scale(sc, sc)
            c.translate(-w / 2, -l.tagY.toFloat())
            text.color = withAlpha(Theme.INK, op)
            var cursor = w / 2 - (trackedWidth(t) / 2).toFloat()
            for (ch in t) {
                val g = ch.toString()
                c.drawText(g, cursor, (l.tagY + 4.5 * lift).toFloat(), text)
                cursor += text.measureText(g) + tagTracking
            }
            c.restore()
        }
        c.restoreToCount(layer)
    }

    private fun thumbX(e: NeonContourMoodEngine) =
        l.firstX + M.clamp(e.look.mood, 0.0, last.toDouble()) / last * (l.lastX - l.firstX) + e.edgeOffset()

    private fun drawQuestionAndSlider(c: Canvas, e: NeonContourMoodEngine, mood: Double) {
        // the question, lit by the mood under the thumb
        text.typeface = semibold
        text.textSize = (Theme.QUESTION_SIZE * s * fs).toFloat()
        text.textAlign = Paint.Align.CENTER
        text.color = hueMix(mood, 0.0, inkRgb, Theme.INK_MIX, 1.0)
        for ((i, line) in l.questionLines.withIndex()) c.drawText(line, w / 2, (l.questionY + i * l.questionLine).toFloat(), text)

        val sx = l.sliderX.toFloat()
        val sy = l.sliderY.toFloat()
        val sw = l.sliderW.toFloat()
        val sh = l.sliderH.toFloat()
        val sr = (Theme.SLIDER_R * s).toFloat()
        rr.set(sx, sy, sx + sw, sy + sh)
        fill.shader = null
        fill.color = Theme.TRACK
        c.drawRoundRect(rr, sr, sr, fill)

        val tx = thumbX(e).toFloat()
        val ty = sy + sh / 2
        val tr = (Theme.SLIDER_THUMB * s * (1 + (Theme.THUMB_SCALE - 1) * max(e.look.press, e.press.value))).toFloat()
        val hue0 = hueMix(mood, 0.0, white, 1.0, 1.0)
        path.reset()
        rr.set(sx, sy, sx + sw, sy + sh)
        path.addRoundRect(rr, sr, sr, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        // the trail: half-strength gradient from the track's colour to the mood's and back
        val trailW = max(0f, tx - sx)
        if (trailW > 0.5f) {
            fill.shader = LinearGradient(sx, sy, tx, sy, intArrayOf(Theme.TRACK, hue0, Theme.TRACK), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = 128
            c.drawRect(sx, sy, sx + trailW, sy + sh, fill)
            fill.shader = null
            fill.alpha = 255
        }
        // the reflected pool
        val poolR = (Theme.THUMB_POOL * s).toFloat()
        fill.shader = RadialGradient(
            tx, ty, poolR, intArrayOf(hueMix(mood, 0.0, white, 1.0, Theme.THUMB_POOL_ALPHA), hueMix(mood, 0.0, white, 1.0, 0.0)), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(tx, ty, poolR, fill)
        fill.shader = null
        // the stops
        val dotR = (Theme.SLIDER_DOT * s).toFloat()
        for ((k, m) in moods.withIndex()) {
            val cx = (l.firstX + k.toDouble() / last * (l.lastX - l.firstX)).toFloat()
            val clear = M.smooth((abs(cx - tx) - tr) / (Theme.DOT_CLEAR * s))
            val passed = Theme.DOT_PASSED * M.smooth((mood - k) / 0.6) * clear
            fill.color = withAlpha(Theme.DOT, clear)
            c.drawCircle(cx, ty, dotR, fill)
            fill.color = withAlpha(m.ramp[0], passed)
            c.drawCircle(cx, ty, dotR, fill)
        }
        c.restore()

        // the thumb
        val ts = thumbShader
        if (Build.VERSION.SDK_INT >= 33 && ts != null && !shaderFailed) {
            NeonContourMoodShaderMath.rampAt(tmp, 0, pack, mood, 0.0, false)
            ts.apply(tx, ty, tr, floatArrayOf(tmp[0].toFloat(), tmp[1].toFloat(), tmp[2].toFloat()), groundRgb.map { it.toFloat() }.toFloatArray(), e.press.value.toFloat())
            c.drawCircle(tx, ty, tr, ts.paint)
        } else {
            fill.color = Theme.DOT
            c.drawCircle(tx, ty, tr, fill)
            stroke.maskFilter = null
            stroke.strokeWidth = (1.2 * s).toFloat()
            stroke.color = withAlpha(hue0, 0.8)
            c.drawCircle(tx, ty, tr * 0.97f, stroke)
        }
    }

    private fun drawEndpointsAndButton(c: Canvas, e: NeonContourMoodEngine, mood: Double) {
        text.typeface = regular
        text.textSize = (Theme.ENDPOINT_SIZE * s * fs).toFloat()
        text.textAlign = Paint.Align.LEFT
        val lk = M.clamp(1 - mood / Theme.END_REACH)
        val rk = M.clamp(1 - (last - mood) / Theme.END_REACH)
        fun mixInk(k: Double) = argb(
            secondaryRgb[0] + (inkRgb[0] - secondaryRgb[0]) * k, secondaryRgb[1] + (inkRgb[1] - secondaryRgb[1]) * k,
            secondaryRgb[2] + (inkRgb[2] - secondaryRgb[2]) * k, 1.0,
        )
        for ((i, line) in l.endpointLines[0].withIndex()) {
            text.color = mixInk(lk)
            val x = if (l.endpointStacked) (l.w - text.measureText(line)) / 2 else 49 * s
            c.drawText(line, x.toFloat(), (l.endpointY + i * Theme.ENDPOINT_LINE * s * fs).toFloat(), text)
        }
        for ((i, line) in l.endpointLines[1].withIndex()) {
            text.color = mixInk(rk)
            val tw = text.measureText(line)
            val x = if (l.endpointStacked) (l.w - tw) / 2 else l.w - 43 * s - tw
            c.drawText(line, x.toFloat(), (l.endpointY + (i + (if (l.endpointStacked) 1 else 0)) * Theme.ENDPOINT_LINE * s * fs).toFloat(), text)
        }
        // the button, ducking under the finger
        val bx = (Theme.BUTTON_X * s).toFloat()
        val bw = (Theme.BUTTON_W * s).toFloat()
        val by = l.buttonY.toFloat()
        val bh = l.buttonH.toFloat()
        val br = (Theme.BUTTON_R * s).toFloat()
        c.save()
        val sc = (1 - (1 - Theme.PRESS_SCALE) * e.buttonPress.value).toFloat()
        c.scale(sc, sc, w / 2, by + bh / 2)
        fill.shader = null
        fill.color = Theme.BUTTON
        rr.set(bx, by, bx + bw, by + bh)
        c.drawRoundRect(rr, br, br, fill)
        path.reset()
        path.addRoundRect(rr, br, br, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        stroke.shader = LinearGradient(
            0f, by, 0f, by + bh,
            intArrayOf(hueMix(mood, 1.0, white, 1.0, Theme.RIM_ALPHA), hueMix(mood, 1.0, white, 1.0, 0.0), hueMix(mood, 1.0, white, 1.0, 0.0)),
            floatArrayOf(0f, Theme.RIM_FADE.toFloat(), 1f), Shader.TileMode.CLAMP,
        )
        stroke.strokeWidth = (Theme.RIM_HEIGHT * 2 * s).toFloat()
        c.drawRoundRect(rr, br, br, stroke)
        stroke.shader = null
        c.restore()
        text.typeface = semibold
        text.textSize = (Theme.BUTTON_SIZE * s * fs).toFloat()
        text.textAlign = Paint.Align.CENTER
        text.color = Theme.BUTTON_INK
        c.drawText(Theme.NEXT, w / 2, (l.buttonY + l.buttonH / 2 + 6 * s * fs).toFloat(), text)
        c.restore()
    }
}

