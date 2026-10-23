package com.ngocdevv.nativecook.blocks.charts.growthodometertimeline

import android.graphics.*
import android.os.Build
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.growthodometertimeline.GrowthSimulation.Companion.smooth

/** Canvas coordinates are points, matching the original Skia layout and shader inputs. */
internal class GrowthRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val rail = Path()
    private val path = Path()
    private val rect = RectF()
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu() else null
    private var oldWidth = 0f
    private var oldHeight = 0f
    private var railGradient: Shader? = null
    private var tickGradient: Shader? = null
    private var glowGradient: Shader? = null
    // Android accepts blur radius; the source Skia BlurMask values are sigma.
    private val blur6 = BlurMaskFilter((6f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
    private val blur5 = BlurMaskFilter((5f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
    private val blur2 = BlurMaskFilter((2f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
    private val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val moteTint = ColorMatrixColorFilter(floatArrayOf(
        211f / 255, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 120f / 255, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))

    fun draw(canvas: Canvas, width: Float, height: Float, ratio: Float, wheel: GrowthSimulation) {
        val cy = floor(height / 8 + .5f) * 4
        val railX = floor(width * .318f + .5f)
        val x0 = railX + 80
        if (oldWidth != width || oldHeight != height) {
            oldWidth = width
            oldHeight = height
            rail.reset()
            rail.moveTo(railX, 0f)
            var y = 2f
            while (y <= height) {
                rail.lineTo(railAt(y, cy, railX), y)
                y += 2
            }
            rail.lineTo(railX, height)
            railGradient = LinearGradient(0f, 0f, 0f, height, intArrayOf(0x0027323A, 0x8027323A.toInt(), 0x8C5E8F2A.toInt(), 0xFFA8F03C.toInt(), 0x8C5E8F2A.toInt(), 0x8027323A.toInt(), 0x0027323A), floatArrayOf(0f, 40 / height, (cy - 120) / height, cy / height, (cy + 120) / height, 1 - 40 / height, 1f), Shader.TileMode.CLAMP)
            tickGradient = LinearGradient(0f, 0f, 0f, height, intArrayOf(0x0039424A, 0x7339424A, 0x8074806A.toInt(), 0xE6A8F03C.toInt(), 0x8074806A.toInt(), 0x7339424A, 0x0039424A), floatArrayOf(0f, 72 / height, (cy - 70) / height, cy / height, (cy + 70) / height, 1 - 72 / height, 1f), Shader.TileMode.CLAMP)
            glowGradient = LinearGradient(0f, cy - 100, 0f, cy + 100, intArrayOf(0x00A8F03C, 0xFFA8F03C.toInt(), 0x00A8F03C), null, Shader.TileMode.CLAMP)
        }
        resetPaint()
        if (gpu != null && Build.VERSION.SDK_INT >= 33) gpu.stage(canvas, paint, width, height, cy, railX, x0, ratio, wheel)
        else fallbackStage(canvas, width, height, cy, railX, wheel)
        val light = (1 + .35 * wheel.grip + .4 * wheel.tick).toFloat()
        resetPaint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.shader = glowGradient
        paint.alpha = (255 * .6f * light).toInt().coerceIn(0, 255)
        paint.maskFilter = blur6
        canvas.drawPath(rail, paint)
        paint.maskFilter = null
        paint.alpha = 255
        paint.shader = railGradient
        paint.strokeWidth = 1.5f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(rail, paint)
        val age = wheel.age - .3
        val heroAlpha = if (wheel.reduced) 0.0 else (1 - smooth(.5, 1.3, age)) * smooth(0.0, .08, age)
        if (heroAlpha > 0) {
            val radius = max(1f, (age / .5).coerceIn(0.0, 1.0).toFloat() * max(cy, height - cy))
            paint.alpha = (heroAlpha * 255).toInt()
            paint.shader = LinearGradient(0f, cy - radius, 0f, cy + radius, intArrayOf(0x00CAFF63, 0xFFCAFF63.toInt(), 0xFFCAFF63.toInt(), 0x00CAFF63), floatArrayOf(0f, .12f, .88f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(rail, paint)
            paint.shader = LinearGradient(0f, cy - radius, 0f, cy + radius, intArrayOf(0x00A8F03C, 0xFFA8F03C.toInt(), 0x00A8F03C), null, Shader.TileMode.CLAMP)
            paint.strokeWidth = 4f
            paint.maskFilter = blur5
            canvas.drawPath(rail, paint)
        }
        resetPaint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.shader = tickGradient
        for (index in 0..60) {
            val y = cy + (index / 5.0 - wheel.p).toFloat() * 64
            if (y < 0 || y > height) continue
            val right = railAt(y, cy, railX) - 16
            val major = index % 5 == 0
            paint.alpha = (255 * light * if (major) 1f else .35f).toInt().coerceIn(0, 255)
            canvas.drawLine(right - if (major) 14 else 7, y, right, y, paint)
        }
        knob(canvas, railX + 34, cy, wheel)
        motes(canvas, width, height, cy, x0, wheel)
        resetPaint()
        paint.xfermode = additive
        if (gpu != null && Build.VERSION.SDK_INT >= 33) gpu.number(canvas, paint, x0, cy, ratio, wheel)
        else fallbackNumber(canvas, x0, cy, wheel)
        resetPaint()
        labels(canvas, width, height, cy, railX, ratio, wheel)
        resetPaint()
        rect.set(x0, cy - 66, x0 + 28, cy - 38)
        canvas.drawBitmap(GrowthAssets.avatar, null, rect, paint)
        text.textScaleX = 1f
        text.textSize = 15f
        text.typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.color = Color.WHITE
        text.alpha = 255
        val nameX = x0 + 36
        canvas.drawText("Nova Lin", nameX, cy - 52 - (text.ascent() + text.descent()) / 2, text)
        val badgeX = nameX + text.measureText("Nova Lin") + 4
        rect.set(badgeX, cy - 59, badgeX + 14, cy - 45)
        canvas.drawBitmap(GrowthAssets.badge, null, rect, paint)
        text.textScaleX = 1.055f
        text.textSize = 13f
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        text.color = Color.rgb(169, 176, 184)
        canvas.drawText("Followers", x0, cy + 43 - (text.ascent() + text.descent()) / 2, text)
    }

    private fun labels(canvas: Canvas, width: Float, height: Float, cy: Float, railX: Float, ratio: Float, wheel: GrowthSimulation) {
        text.textSize = 11f
        text.typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 450, false) else Typeface.DEFAULT
        text.textScaleX = 1.09f
        GrowthSimulation.months.forEachIndexed { index, label ->
            val y = cy + (index - wheel.p).toFloat() * 64
            if (y < -11 || y > height + 11) return@forEachIndexed
            val focus = bell((y - cy) / 52)
            val fade = min(1f, min(y / 72, (height - y) / 72)).coerceAtLeast(0f)
            val alpha = (.72f + .28f * focus) * fade
            val pillWidth = text.measureText(label) + 16 + 2 / ratio
            val right = max(railX - 36 - 16.1f * focus, pillWidth + 4)
            rect.set(right - pillWidth, y - 11, right, y + 11)
            resetPaint()
            paint.color = Color.WHITE
            paint.alpha = (255 * .035f * alpha).toInt()
            canvas.drawRoundRect(rect, 11f, 11f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1 / ratio
            paint.color = Color.rgb(168, 240, 60)
            paint.alpha = (255 * (.09 + focus * (.21 + .6 * wheel.flash)) * alpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, 11f, 11f, paint)
            text.color = if (focus > .8) Color.WHITE else Color.rgb(221, 227, 232)
            text.alpha = (255 * alpha).toInt()
            canvas.drawText(label, right - pillWidth + 8 + 1 / ratio, y - (text.ascent() + text.descent()) / 2, text)
        }
    }

    private fun knob(canvas: Canvas, x: Float, y: Float, wheel: GrowthSimulation) {
        canvas.save()
        val scale = (1 - .04 * wheel.press).toFloat()
        canvas.scale(scale, scale, x, y)
        resetPaint()
        paint.shader = RadialGradient(x, y, 34f, intArrayOf(0xE6161C24.toInt(), 0xE6161C24.toInt(), 0x00161C24), floatArrayOf(0f, .59f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, 34f, paint)
        paint.shader = RadialGradient(x - 10, y - 12, 42f, intArrayOf(0xFF252C36.toInt(), 0xFF141920.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, 20f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        rect.set(x - 20, y - 20, x + 20, y + 20)
        paint.strokeWidth = 1f
        paint.color = 0x1AFFFFFF
        canvas.drawArc(rect, 200f, 105f, false, paint)
        paint.color = 0x8805080B.toInt()
        canvas.drawArc(rect, 20f, 100f, false, paint)
        paint.strokeWidth = .7f
        paint.color = 0x40A8F03C
        canvas.drawArc(rect, 155f, 55f, false, paint)
        paint.color = 0xFFE5FF70.toInt()
        paint.alpha = (255 * (.08 + .5 * wheel.halo)).toInt().coerceIn(0, 255)
        canvas.drawArc(rect, -35f, 70f, false, paint)
        for (index in 0..2) {
            val phase = ((wheel.chevronPhase + index / 3.0) % 1 + 1) % 1
            val pulse = max(0.0, cos(phase * PI * 2)).pow(8)
            val brightness = if (wheel.pressed || wheel.age < if (wheel.reduced) .2 else .6) 1.0 else .22 + .78 * pulse
            val chevronY = y - 7 + index * 5.5f
            path.reset()
            path.moveTo(x - 6, chevronY + 3)
            path.lineTo(x, chevronY - 2)
            path.lineTo(x + 6, chevronY + 3)
            paint.color = 0xFFB8F53E.toInt()
            paint.alpha = (brightness * 255).toInt()
            paint.strokeWidth = 2.4f
            paint.maskFilter = blur2
            canvas.drawPath(path, paint)
            paint.strokeWidth = 1.75f
            paint.maskFilter = null
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }

    private fun motes(canvas: Canvas, width: Float, height: Float, cy: Float, x0: Float, wheel: GrowthSimulation) {
        if (wheel.reduced || wheel.halo <= .2) return
        resetPaint()
        paint.xfermode = additive
        paint.colorFilter = moteTint
        paint.alpha = (255 * .46 * smooth(.2, .7, wheel.halo)).toInt()
        val push = GrowthSimulation.envelope(wheel.age, .55) * 15
        for (index in 0 until 26) {
            val seed = index * .61803398875 % 1
            val run = wheel.time * (6 + seed * 8)
            val x = width - (seed * 120 + run + push) % 120
            val y = (index * 83.13 - run - push + height * 10) % height
            val field = GrowthHalo.field(x, y, width.toDouble(), height.toDouble(), cy.toDouble(), x0.toDouble(), wheel)
            val size = (1.2 + seed * 1.8) * smooth(.25, .6, field)
            if (size < .01) continue
            rect.set((x - size / 2).toFloat(), (y - size / 2).toFloat(), (x + size / 2).toFloat(), (y + size / 2).toFloat())
            canvas.drawBitmap(GrowthAssets.mote, null, rect, paint)
        }
    }

    // API 26–32: stationary, soft illumination and the original strip's resting glyphs.
    private fun fallbackStage(canvas: Canvas, width: Float, height: Float, cy: Float, railX: Float, wheel: GrowthSimulation) {
        canvas.drawColor(0xFF06080C.toInt())
        paint.shader = RadialGradient(railX - 12, cy, 110f, intArrayOf(0x403F7F1F, 0x003F7F1F), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.clipRect(0f, 0f, railX, height)
        canvas.drawRect(0f, 0f, width, height, paint)
        canvas.restore()
        val level = wheel.halo.toFloat()
        paint.shader = RadialGradient(width + 25, cy - height * .28f, 190f, intArrayOf(Color.argb((220 * level).toInt(), 221, 238, 85), Color.argb((180 * level).toInt(), 70, 127, 39), 0x001E4A1A), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun fallbackNumber(canvas: Canvas, x0: Float, cy: Float, wheel: GrowthSimulation) {
        paint.alpha = (255 * wheel.numberAlpha).toInt()
        paint.colorFilter = ColorMatrixColorFilter(floatArrayOf(0f, 0f, 0f, 0f, 251f, 0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 254f, 1f, 0f, 0f, 0f, 0f))
        val value = GrowthSimulation.values[wheel.month]
        val label = String.format(java.util.Locale.US, "%,d", value)
        var x = x0
        label.forEach { character ->
            val row = if (character == ',') 10 else character.digitToInt()
            val cellWidth = if (character == ',') 10 else if (label.length == 6 && x == x0) 16 else 24
            val left = if (label.length == 6 && x == x0) 12 else 0
            val source = Rect(left, row * 156, left + cellWidth * 3, (row + 1) * 156)
            rect.set(x, cy - 26, x + cellWidth, cy + 26)
            canvas.drawBitmap(GrowthAssets.digits, source, rect, paint)
            x += cellWidth
        }
    }

    private fun resetPaint() {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
    }

    private fun bell(value: Float) = if (abs(value) < 1) .5f + .5f * cos(PI.toFloat() * value) else 0f
    private fun railAt(y: Float, cy: Float, x: Float) = x - 14 * bell((y - cy) / 44)

    @android.annotation.TargetApi(33)
    private class Gpu {
        private val stage = RuntimeShader(GROWTH_STAGE_SHADER)
        private val odometer = RuntimeShader(GROWTH_ODOMETER_SHADER)

        init {
            val grain = BitmapShader(GrowthAssets.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            grain.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            stage.setInputBuffer("grain", grain)
            val digits = BitmapShader(GrowthAssets.digits, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
            digits.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            odometer.setInputBuffer("digits", digits)
        }

        fun stage(canvas: Canvas, paint: Paint, width: Float, height: Float, cy: Float, railX: Float, x0: Float, ratio: Float, wheel: GrowthSimulation) {
            stage.setFloatUniform("geometry", width, height, cy, railX)
            stage.setFloatUniform("screen", ratio, x0, 0f, 0f)
            stage.setFloatUniform("light", wheel.halo.toFloat(), wheel.time.toFloat(), wheel.grip.toFloat(), wheel.tick.toFloat())
            stage.setFloatUniform("hero", wheel.age.toFloat(), wheel.surge.toFloat(), max(0.0, wheel.p - 12).toFloat(), if (wheel.reduced) 1f else 0f)
            paint.shader = stage
            canvas.drawRect(0f, 0f, width, height, paint)
        }

        fun number(canvas: Canvas, paint: Paint, x0: Float, cy: Float, ratio: Float, wheel: GrowthSimulation) {
            val drums = wheel.drums
            fun blur(index: Int) = (drums[index].omega / 100).coerceIn(-.22, .22).toFloat()
            odometer.setFloatUniform("geometry", floor(x0 * ratio + .5f) / ratio, cy, ratio, wheel.halo.toFloat())
            odometer.setFloatUniform("angles", drums[0].theta.toFloat(), drums[1].theta.toFloat(), drums[2].theta.toFloat(), drums[3].theta.toFloat())
            odometer.setFloatUniform("extra", drums[4].theta.toFloat(), blur(0), blur(1), blur(2))
            odometer.setFloatUniform("motion", blur(3), blur(4), wheel.flash.toFloat(), wheel.age.toFloat())
            odometer.setFloatUniform("face", wheel.numberAlpha.toFloat(), if (wheel.reduced) 1f else 0f, 0f, 0f)
            paint.shader = odometer
            canvas.drawRect(x0, cy - 26, x0 + 122, cy + 26, paint)
        }
    }
}
