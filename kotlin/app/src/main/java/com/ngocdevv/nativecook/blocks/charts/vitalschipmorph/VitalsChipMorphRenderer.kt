package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.blocks.charts.vitalschipmorph.VitalsChipMorphMotion as M
import com.ngocdevv.nativecook.blocks.charts.vitalschipmorph.VitalsChipMorphTheme as T
import kotlin.math.roundToInt

internal class VitalsChipMorphRenderer(context: Context) {
    private val hero = VitalsChipMorphHero(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glass = VitalsChipMorphGlass()
    private val numberFrame = M.Hero()
    private val roundedBounds = RectF()
    private val tabBounds = RectF(
        T.layout.tabBar.x.toFloat(),
        T.layout.tabBar.y.toFloat(),
        (T.layout.tabBar.x + T.layout.tabBar.w).toFloat(),
        (T.layout.tabBar.y + T.layout.tabBar.h).toFloat(),
    )
    private val tabClip = Path().apply {
        addRoundRect(tabBounds, T.radius.tabBar.toFloat(), T.radius.tabBar.toFloat(), Path.Direction.CW)
    }
    private val bpmTexts = arrayOf("6", "2", "BPM")
    private val readinessDigits = Array(8) { it.toString() }
    private val bpmWidths = DoubleArray(2) { index ->
        VitalsChipMorphText.measure(
            bpmTexts[index],
            T.type.bpm.size * 3,
            T.type.bpm.weight.toInt(),
            T.type.bpm.ls * 3,
        )
    }
    private val tabKeys = arrayOf("heart", "longevity", "browse")
    private val tabWidths = doubleArrayOf(81.0, 78.0, 72.0)
    private val tabTitles = arrayOf("Insights", "Longevity", "Browse")

    fun rowOffset(c: VitalsChipMorphController): Double {
        val progress = if (c.quiet) (if (c.u < .5) 0.0 else 1.0) else M.easeInOut(M.seg(c.u, 0.0, .85))
        return M.lerp(T.layout.rowOffset[c.lo], T.layout.rowOffset[c.hi], progress)
    }

    fun draw(canvas: Canvas, c: VitalsChipMorphController, refToPx: Double) {
        VitalsChipMorphPages.configureScale(refToPx, contextDensity)
        chips(canvas, c, refToPx)
        canvas.save()
        canvas.translate(c.shift.toFloat(), 0f)
        canvas.save()
        canvas.clipRect(0f, T.layout.heroTop.toFloat(), T.REF_W.toFloat(), (T.layout.heroTop + T.layout.heroH).toFloat())
        hero.draw(canvas, c.lo, c.hi, c.u, c.beat, c.quiet, c.fxTime, c.speed, c.beatCount)
        canvas.restore()
        numbers(canvas, c)
        canvas.restore()
        VitalsChipMorphPages.draw(canvas, c.lo, c.hi, c.u, c.quiet)
        tabs(canvas, c, refToPx)
    }

    private fun chips(canvas: Canvas, c: VitalsChipMorphController, refToPx: Double) {
        val a = T.chips[T.SCREEN_CHIP[c.lo]]
        val b = T.chips[T.SCREEN_CHIP[c.hi]]
        val lead = if (c.quiet) (if (c.u < .5) 0.0 else 1.0) else M.easeInOut(M.seg(c.u, 0.0, .7))
        val trail = if (c.quiet) lead else M.easeInOut(M.seg(c.u, .15, .85))
        val left = M.lerp(a.x, b.x, if (b.x > a.x) trail else lead)
        val right = M.lerp(a.x + a.w, b.x + b.w, if (b.x > a.x) lead else trail)
        val fly = if (c.quiet || c.lo == c.hi) 0.0 else M.bump(c.u, 0.0, 1.0)
        canvas.save()
        canvas.translate(rowOffset(c).toFloat(), T.layout.chipTop.toFloat())
        rounded(
            canvas,
            left,
            0.0,
            right - left,
            T.layout.chipH,
            T.radius.chip,
            T.colors.chipActive.copy(alpha = (1.0 - 0.8 * fly).toFloat()),
        )
        if (fly > .0005) {
            val centreX = (left + right) / 2.0
            val scale = 1.0 + (T.lens.liftScale - 1.0) * fly
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat(), centreX.toFloat(), (T.layout.chipH / 2.0).toFloat())
            glass.drawChipLens(canvas, left, 0.0, right - left, T.layout.chipH, fly)
            canvas.restore()
        }
        for (index in T.chips.indices) {
            val chip = T.chips[index]
            val automatic = if (c.pressChip == index) c.press else 0.0
            val scale = (1 - .03 * automatic) * (1 - .03 * c.chipPressValue(index))
            canvas.save()
            canvas.translate(chip.x.toFloat(), 0f)
            canvas.scale(scale.toFloat(), scale.toFloat(), (chip.w / 2).toFloat(), (T.layout.chipH / 2).toFloat())
            val screen = T.SCREEN_CHIP.indexOf(index)
            val opacity = if (screen < 0) 1.0 else 1 - M.weight(screen, c.lo, c.hi, c.u)
            canvas.saveLayerAlpha(0f, 0f, chip.w.toFloat(), T.layout.chipH.toFloat(), (255 * opacity).roundToInt())
            rounded(canvas, 0.0, 0.0, chip.w, T.layout.chipH, T.radius.chip, T.colors.chip)
            // RN's two hairline widths are two physical pixels on either platform.
            rounded(canvas, 0.0, 0.0, chip.w, T.layout.chipH, T.radius.chip, T.colors.chipBorder, 2 / refToPx)
            canvas.restore()
            if (chip.key == "summary") {
                VitalsChipMorphIcons.draw(canvas, chip.key, chip.w / 2, T.layout.chipH / 2, 90.0)
            } else {
                val toward = if (c.target == c.hi) c.u else 1 - c.u
                val pop = if (screen < 0 || c.quiet || c.lo == c.hi || c.target != screen) 1.0 else 1 + .12 * M.bump(toward, .5, .95)
                val iconSize = when (chip.key) {
                    "heart" -> 93.0
                    "vitals" -> 90.0
                    else -> 96.0
                }
                VitalsChipMorphIcons.draw(canvas, chip.key, T.space.chipIconCentre, T.layout.chipH / 2, iconSize * pop)
                label(canvas, chip.title, T.space.chipLabel, T.space.chipTitleCap, T.type.chipTitle)
                label(canvas, chip.value, T.space.chipLabel, T.space.chipValueCap, T.type.chipValue)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    private fun numbers(canvas: Canvas, c: VitalsChipMorphController) {
        val f = M.heroFrame(c.lo, c.hi, if (c.quiet) (if (c.u < .5) 0.0 else 1.0) else c.u, numberFrame)
        val scoreSize = T.type.readiness.size * 3
        val lineHeight = scoreSize * 1.2
        val roll = if (c.quiet) 7.0 else f.sevenRoll
        val top = T.layout.seven.capTop - .247 * scoreSize
        canvas.saveLayerAlpha(0f, top.toFloat(), T.REF_W.toFloat(), (top + lineHeight).toFloat(), (255 * f.sevenOpacity).roundToInt())
        canvas.clipRect((T.layout.seven.cx - 150).toFloat(), top.toFloat(), (T.layout.seven.cx + 150).toFloat(), (top + lineHeight).toFloat())
        for (digit in 0..7) {
            label(canvas, readinessDigits[digit], T.layout.seven.cx, T.layout.seven.capTop + (digit - roll) * lineHeight, T.type.readiness, center = true)
        }
        canvas.restore()
        var x = (T.REF_W - bpmWidths[0] - bpmWidths[1]) / 2
        bpmTexts.forEachIndexed { index, text ->
            val alpha = when (index) {
                0 -> f.bpm0
                1 -> f.bpm1
                else -> f.bpmUnit
            }
            val spec = if (index == 2) T.type.bpmUnit else T.type.bpm
            val capTop = (if (index == 2) T.layout.bpm.unitCapTop else T.layout.bpm.capTop) + (1 - alpha) * spec.size * 3 * .3
            canvas.saveLayerAlpha(0f, 400f, T.REF_W.toFloat(), 1200f, (255 * alpha).roundToInt())
            label(canvas, text, if (index == 2) T.REF_W / 2 else x, capTop, spec, center = index == 2)
            canvas.restore()
            if (index < 2) x += bpmWidths[index]
        }
    }

    private fun tabs(canvas: Canvas, c: VitalsChipMorphController, refToPx: Double) {
        val t = T.layout.tabBar
        glass.shadow(canvas)
        canvas.save()
        canvas.clipPath(tabClip)
        glass.draw(canvas, c)
        rounded(canvas, t.x, t.y, t.w, t.h, T.radius.tabBar, T.colors.tabGlass)
        rounded(canvas, t.pill.x, t.pill.y, t.pill.w, t.pill.h, T.radius.tabPill, T.colors.tabActive)
        for (index in 0..2) {
            VitalsChipMorphIcons.draw(canvas, tabKeys[index], t.centres[index], t.iconCy, tabWidths[index], if (index == 0) T.colors.tabBlue else T.colors.ink)
            label(canvas, tabTitles[index], t.centres[index], t.labelCapTop, T.type.tab, center = true, color = if (index == 0) T.colors.tabBlue else T.colors.ink)
        }
        canvas.restore()
        // RN borderWidth=1 logical point scales with device density, not the reference width.
        rounded(canvas, t.x, t.y, t.w, t.h, T.radius.tabBar, T.colors.tabRim, contextDensity / refToPx)
    }

    private val contextDensity = context.resources.displayMetrics.density.toDouble()

    private fun label(canvas: Canvas, text: String, x: Double, capTop: Double, spec: T.TextSpec, center: Boolean = false, color: Color = spec.color) {
        VitalsChipMorphText.draw(
            canvas = canvas,
            text = text,
            x = x,
            capTop = capTop,
            size = spec.size * 3,
            weight = spec.weight.toInt(),
            color = color,
            ls = spec.ls * 3,
            center = center,
            rounded = spec.rounded,
        )
    }

    private fun rounded(canvas: Canvas, x: Double, y: Double, w: Double, h: Double, radius: Double, color: Color, stroke: Double = 0.0) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color.toArgb()
        paint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke.toFloat()
        val inset = stroke / 2
        roundedBounds.set(
            (x + inset).toFloat(),
            (y + inset).toFloat(),
            (x + w - inset).toFloat(),
            (y + h - inset).toFloat(),
        )
        canvas.drawRoundRect(roundedBounds, (radius - inset).toFloat(), (radius - inset).toFloat(), paint)
    }
}
