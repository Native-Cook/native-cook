package com.ngocdevv.nativecook.blocks.navigation.holoagendacarousel

import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Draws in the same 402-point coordinate system as the source. */
internal class HoloRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val materials = if (Build.VERSION.SDK_INT >= 33) Array(32) { HoloShader() } else null
    private val fallback = if (materials == null) HoloStillMaterial() else null
    private val path = Path()
    private val camera = Camera()
    private val matrix = Matrix()
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val mono = Typeface.create("monospace", Typeface.NORMAL)
    private val white = Color.WHITE
    private val dim = Color.rgb(142, 142, 153)
    private val ink = Color.rgb(11, 11, 16)
    // API 37's glyph atlas aborts when near-zero perspective and affine text batches meet.
    // These four fixed labels stay vector-sharp without entering that atlas pipeline.
    private val labelPaths by lazy {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = 26f
        }
        HoloTheme.sessions.map { session ->
            val label = session[0]
            val tracking = -.5f
            var x = 96f-(labelPaint.measureText(label)+(label.length-1)*tracking)/2
            val metrics = labelPaint.fontMetrics
            val baseline = (192f-metrics.descent-metrics.ascent)/2
            Path().apply {
                label.forEach { char ->
                    val glyph = char.toString()
                    val glyphPath = Path()
                    labelPaint.getTextPath(glyph, 0, glyph.length, x, baseline, glyphPath)
                    addPath(glyphPath)
                    x += labelPaint.measureText(glyph)+tracking
                }
            }
        }
    }

    private fun fill(slot: Int, kind: Int, width: Float, height: Float, radius: Float,
                     motion: HoloMotion, detail: Float = 0f, accent: Boolean = false, fade: Float = 0f) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        if (Build.VERSION.SDK_INT >= 33 && materials != null) {
            val material = materials[slot]
            material.update(kind, width, height, radius, if (accent) 0f else motion.clock,
                HoloTheme.wrap(kind-motion.position), if (accent) 0f else motion.velocity, detail,
                if (HoloTheme.index(motion.centre) == kind) motion.age else 900f, fade,
                if (accent) 0f else motion.surge[kind])
            paint.shader = material.shader
        } else {
            paint.shader = fallback?.shader(kind, width, height)
        }
    }
    private fun solid(color: Int, alpha: Float = 1f) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f)*Color.alpha(color)).roundToInt()
    }
    private fun blur(sigma: Float) {
        paint.maskFilter = BlurMaskFilter(((sigma-.5f)/.57735f).coerceAtLeast(.01f), BlurMaskFilter.Blur.NORMAL)
    }
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float,
                     line: Float, color: Int = white, weight: Int = 0, tracking: Float = 0f,
                     centered: Boolean = true, alpha: Float = 1f) {
        textPaint.textSize = size
        textPaint.typeface = when (weight) {
            1 -> bold
            2 -> medium
            3 -> mono
            else -> regular
        }
        textPaint.color = color
        textPaint.alpha = (255*alpha.coerceIn(0f, 1f)).roundToInt()
        val metrics = textPaint.fontMetrics
        val baseline = y + (line - metrics.descent - metrics.ascent)/2
        val width = textPaint.measureText(value) + max(0, value.length-1)*tracking
        var left = if (centered) x-width/2 else x
        if (tracking == 0f) canvas.drawText(value, left, baseline, textPaint)
        else value.forEach { char ->
            val glyph = char.toString()
            canvas.drawText(glyph, left, baseline, textPaint)
            left += textPaint.measureText(glyph)+tracking
        }
    }
    private fun width(value: String, size: Float, face: Typeface): Float {
        textPaint.textSize = size
        textPaint.typeface = face
        return textPaint.measureText(value)
    }
    fun draw(canvas: Canvas, motion: HoloMotion, grid: (Float) -> Float) {
        val centre = grid(264f)
        val order = (0..3).sortedBy { HoloTheme.pose(HoloTheme.wrap(it-motion.position)).z }
        // Mirror, wide bloom and contact bloom have independent shaders: display-list recording
        // must not reuse a mutable RuntimeShader for different material uniforms in one frame.
        order.forEach { i ->
            val pose = HoloTheme.pose(HoloTheme.wrap(i-motion.position))
            val follow = if (HoloTheme.index(motion.centre) == i) HoloTheme.arrivalScale(motion.age) else 1f
            val side = pose.side*motion.squeeze*follow
            canvas.save()
            canvas.translate(201f+pose.x-side/2, centre+side/2+12+side)
            canvas.scale(side/192, -side/192)
            fill(i, i, 192f, 192f, 20f, motion, fade = 36*192/side)
            paint.alpha = (pose.opacity*.26f*255).roundToInt()
            blur(3f)
            if (materials == null) {
                val layer = canvas.saveLayer(0f, 0f, 192f, 192f, null)
                canvas.drawRoundRect(0f, 0f, 192f, 192f, 20f, 20f, paint)
                solid(Color.WHITE)
                val fade = 36*192/side
                paint.shader = LinearGradient(0f, 192f-fade, 0f, 192f,
                    intArrayOf(0, 0x80FFFFFF.toInt(), Color.WHITE), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                canvas.drawRect(0f, 0f, 192f, 192f, paint)
                canvas.restoreToCount(layer)
            } else {
                canvas.drawRoundRect(0f, 0f, 192f, 192f, 20f, 20f, paint)
            }
            canvas.restore()
        }
        repeat(2) { pass ->
            repeat(4) { i ->
                val pose = HoloTheme.pose(HoloTheme.wrap(i-motion.position))
                val selected = HoloTheme.index(motion.centre) == i
                val pulse = if (selected) motion.arrival else 0f
                val follow = if (selected) HoloTheme.arrivalScale(motion.age) else 1f
                val spread = if (pass == 0) 1.10f+pulse*.08f else 1.02f
                val side = pose.side*spread*motion.squeeze*follow
                canvas.save()
                canvas.translate(201+pose.x-side/2, centre-side/2)
                canvas.scale(side/192, side/192)
                fill(4+pass*4+i, i, 192f, 192f, 20f, motion)
                paint.alpha = (255*pose.opacity*(if (pass == 0) .26f else .55f)*(1+pulse*.8f)).roundToInt().coerceIn(0, 255)
                if (Build.VERSION.SDK_INT < 28 && fallback != null) {
                    val alpha = paint.alpha
                    solid(Color.WHITE)
                    paint.alpha = alpha
                    canvas.drawBitmap(fallback.glow(i, pass == 0), -96f, -96f, paint)
                } else {
                    blur(if (pass == 0) 34f else 10f)
                    canvas.drawRoundRect(0f, 0f, 192f, 192f, 20f, 20f, paint)
                }
                canvas.restore()
            }
        }
        // The black edge feather keeps the halo off the physical screen edge.
        solid(Color.BLACK)
        paint.shader = LinearGradient(0f, 0f, 32f, 0f, intArrayOf(Color.BLACK, 0xF2000000.toInt(), 0x80000000.toInt(), 0), floatArrayOf(0f, .15f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 32f, grid(480f), paint)
        paint.shader = LinearGradient(402f, 0f, 370f, 0f, intArrayOf(Color.BLACK, 0xF2000000.toInt(), 0x80000000.toInt(), 0), floatArrayOf(0f, .15f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(370f, 0f, 402f, grid(480f), paint)
        order.forEach { i -> drawCard(canvas, motion, i, centre) }
        drawPager(canvas, motion, grid(408f))
        text(canvas, "IRIDIA SUMMIT", 24f, grid(12f), 13f, 28f, 0xEBFFFFFF.toInt(), 1, 1.6f, false)
        text(canvas, "OCT 14–15", 378f-width("OCT 14–15", 12f, mono), grid(12f), 12f, 28f, dim, 3, centered = false)
        repeat(4) { i -> drawDetails(canvas, motion, i, grid) }
        drawButton(canvas, motion, grid(706f))
    }
    private fun drawCard(canvas: Canvas, motion: HoloMotion, i: Int, cy: Float) {
        val pose = HoloTheme.pose(HoloTheme.wrap(i-motion.position))
        if (pose.opacity <= 0) return
        val follow = if (HoloTheme.index(motion.centre) == i) HoloTheme.arrivalScale(motion.age) else 1f
        val scale = pose.side/192*follow*motion.squeeze*(if (motion.pressed == i) motion.press else 1f)
        canvas.save()
        canvas.translate(201+pose.x, cy)
        camera.save()
        camera.setLocation(0f, 0f, -700f/72f)
        val tilt = ((-motion.velocity*2.2f).coerceIn(-11f, 11f)*1000).roundToInt()/1000f
        camera.rotateY(tilt)
        camera.getMatrix(matrix)
        camera.restore()
        canvas.concat(matrix)
        canvas.scale(scale, scale)
        canvas.translate(-96f, -96f)
        solid(Color.BLACK, .5f*pose.opacity)
        blur(16f)
        canvas.drawRoundRect(0f, 8f, 192f, 200f, 20f, 20f, paint)
        fill(12+i, i, 192f, 192f, 20f, motion, detail = 1f)
        paint.alpha = (pose.opacity*255).roundToInt()
        canvas.drawRoundRect(0f, 0f, 192f, 192f, 20f, 20f, paint)
        solid(ink, pose.opacity)
        canvas.drawPath(labelPaths[i], paint)
        canvas.restore()
    }
    private fun drawPager(canvas: Canvas, motion: HoloMotion, y: Float) {
        var x = 168f
        repeat(4) { i ->
            val amount = max(0f, 1-abs(HoloTheme.wrap(i-motion.position)))
            val width = 6+18*amount
            canvas.save()
            canvas.translate(x, y-3)
            solid(0x38FFFFFF)
            canvas.drawRoundRect(0f, 0f, width, 6f, 3f, 3f, paint)
            fill(16+i, i, 24f, 6f, 3f, motion, accent = true)
            paint.alpha = (255*amount).roundToInt()
            canvas.drawRoundRect(0f, 0f, width, 6f, 3f, 3f, paint)
            canvas.restore()
            x += width+8
        }
    }
    private fun drawDetails(canvas: Canvas, motion: HoloMotion, i: Int, grid: (Float) -> Float) {
        val distance = HoloTheme.wrap(i-motion.position)
        val alpha = 1-HoloTheme.smooth(.12f, .5f, abs(distance))
        if (alpha <= 0) return
        val session = HoloTheme.sessions[i]
        canvas.save()
        canvas.translate(distance*28, 0f)
        text(canvas, session[1], 201f, grid(456f), 12f, 16f, dim, 3, 1.2f, alpha = alpha)
        text(canvas, session[2], 201f, grid(480f), 32f, 40f, white, 1, -.6f, alpha = alpha)
        val words = session[3].split(" ")
        var line = ""
        var lineIndex = 0
        words.forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (width(next, 15f, regular) > 300) {
                text(canvas, line, 201f, grid(532f)+lineIndex*22, 15f, 22f, dim, alpha = alpha)
                lineIndex++
                line = word
            } else line = next
        }
        text(canvas, line, 201f, grid(532f)+lineIndex*22, 15f, 22f, dim, alpha = alpha)
        val labelWidth = max(width(session[4], 15f, medium), width(session[5], 13f, regular))
        val x = (402-48-labelWidth)/2
        canvas.save()
        canvas.translate(x, grid(600f))
        fill(20+i, i, 36f, 36f, 18f, motion, accent = true)
        paint.alpha = (255*alpha).roundToInt()
        canvas.drawCircle(18f, 18f, 18f, paint)
        text(canvas, session[6], 18f, 0f, 13f, 36f, ink, 1, alpha = alpha)
        text(canvas, session[4], 48f, 0f, 15f, 20f, white, 2, centered = false, alpha = alpha)
        text(canvas, session[5], 48f, 20f, 13f, 16f, dim, centered = false, alpha = alpha)
        canvas.restore()
        canvas.restore()
    }
    private fun drawButton(canvas: Canvas, motion: HoloMotion, top: Float) {
        canvas.save()
        canvas.translate(201f, top+28)
        val scale = motion.buttonScale*motion.buttonPress
        canvas.scale(scale, scale)
        canvas.translate(-177f, -28f)
        val i = motion.selection
        fill(24, i, 354f, 56f, 28f, motion, accent = true)
        paint.alpha = (255*motion.morph).roundToInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        blur(5f)
        canvas.drawRoundRect(0f, 0f, 354f, 56f, 28f, 28f, paint)
        solid(Color.rgb(19, 19, 22))
        canvas.drawRoundRect(0f, 0f, 354f, 56f, 28f, 28f, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, 56f, 0x24FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(.5f, .5f, 353.5f, 55.5f, 27.5f, 27.5f, paint)
        fill(25, i, 354f, 56f, 28f, motion, accent = true)
        paint.alpha = (255*motion.morph).roundToInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(.75f, .75f, 353.25f, 55.25f, 27.25f, 27.25f, paint)
        val labelWidth = max(width("Add to agenda", 17f, medium), width("In your agenda", 17f, medium))
        val x = (354-28-labelWidth)/2+4
        text(canvas, "Add to agenda", x+28, 17f-4*motion.labelMix, 17f, 22f, white, 2,
            centered = false, alpha = (1-2*motion.labelMix).coerceIn(0f, 1f))
        text(canvas, "In your agenda", x+28, 17f+4*(1-motion.labelMix), 17f, 22f, white, 2,
            centered = false, alpha = (2*motion.labelMix-1).coerceIn(0f, 1f))
        canvas.translate(x, 18f)
        val p = if (motion.reduced) if (motion.morph >= .5f) 1f else 0f else motion.morph
        path.reset()
        path.moveTo(3f, 10f)
        path.lineTo(17-9*p, 10+5*p)
        path.moveTo(10-2*p, 3+12*p)
        path.lineTo(10+7*p, 17-12*p)
        fill(26, i, 20f, 20f, 0f, motion, accent = true)
        if (motion.reduced) paint.alpha = (255*abs(motion.morph*2-1)).roundToInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.25f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
