package com.ngocdevv.nativecook.blocks.progress.stitchweavepreview

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

/** One native draw pass: cached yarn data, GPU material, and system-font product controls. */
internal class StitchWeavePreviewPainter(private val resources: StitchWeavePreviewResources) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val type = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val hero = RectF(22f, 184f, 371f, 595f)
    private val grain = BitmapShader(resources.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val gpu = if (Build.VERSION.SDK_INT >= 33) StitchWeavePreviewGpu(resources) else null
    private val fallback = if (Build.VERSION.SDK_INT < 33) StitchWeavePreviewFallback(resources) else null
    private var backgroundHeight = 0f
    private var background: Shader? = null
    private var ambience: Shader? = null
    private val styles = FloatArray(48 * 4)
    private val rowWeights = DoubleArray(6)
    private val faces = arrayOf(400, 500, 600).associateWith { Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), it, false) }
    private val trackingWidths = HashMap<String, FloatArray>()
    private val m = StitchWeavePreviewMath
    private val foreground = color(0xF2F1E7)

    fun draw(canvas: Canvas, pose: StitchWeavePreviewPose, driver: StitchWeavePreviewDriver,
        now: Double, reduced: Boolean, preview: Double?, height: Float, bottom: Float) {
        if (backgroundHeight != height) {
            backgroundHeight = height
            background = LinearGradient(196.5f, 0f, 196.5f, height,
                intArrayOf(color(0x929C9C), color(0x4E595A), color(0x20292B)), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
            ambience = RadialGradient(196.5f, height * .43f, 290f,
                intArrayOf(color(0xD9E4DD, .10), color(0xD9E4DD, .10), color(0xD9E4DD, 0.0)),
                floatArrayOf(0f, 20f / 290, 1f), Shader.TileMode.CLAMP)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255; paint.shader = background
        canvas.drawRect(0f, 0f, 393f, height, paint)
        paint.shader = ambience; canvas.drawRect(0f, 0f, 393f, height, paint)
        paint.shader = grain; paint.alpha = 10; canvas.drawRect(0f, 0f, 393f, height, paint)
        paint.shader = null; paint.alpha = 255

        text(canvas, "THREAD / STUDIO", 24f, 84.5f, 11f, 600, tracking = 2f)
        text(canvas, "Make something soft", 24f, 124f, 29f, 600, tracking = -.6f)
        text(canvas, "A small pattern, one row at a time.", 24f, 159.5f, 13f, 400, color(0xC4CBC7))
        val canonical = m.canonicalTime(pose.p)
        val projector = resources.projector(pose)
        canvas.save(); canvas.clipRect(hero)
        val hem = projector.project(.5, 1.0)
        paint.alpha = 41
        rect.set((hem.x - 174).toFloat(), (hem.y - 13).toFloat(), (hem.x + 174).toFloat(), (hem.y + 65).toFloat())
        canvas.drawBitmap(resources.shadow, null, rect, paint); paint.alpha = 255
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        repeat(52) { column ->
            val formed = m.columnRows(pose.p, canonical, column)
            var last = min(47, max(2, ceil(formed).toInt() - 1))
            var formation = if (last < 3) 1.0 else m.clamp((formed - (last + 1) + .45) / .45)
            if (formation <= 0 && last > 2) { last--; formation = 1.0 }
            val crown = 572 - (last + 1) * 6.8 + .5 - 1.8 * (1 - formation)
            val end = projector.project((column + .5) / 52, (crown - 245.6) / 326.4)
            val start = (38.4 + column * 6.2).toFloat()
            path.rewind(); path.moveTo(start, 184f)
            path.quadTo((start + (end.x - start) * .35).toFloat(), (184 + (end.y - 184) * .56).toFloat(), end.x.toFloat(), end.y.toFloat())
            paint.color = color(0xE6E9E0, .18 + .24 * m.hash(column + 91))
            paint.strokeWidth = (.42 + .28 * m.hash(column + 7)).toFloat()
            canvas.drawPath(path, paint)
        }
        materialStyles(driver, now, reduced, preview)
        paint.style = Paint.Style.FILL; paint.alpha = 255
        if (Build.VERSION.SDK_INT >= 33) gpu!!.draw(canvas, pose, canonical, styles)
        else fallback!!.draw(canvas, pose, canonical, styles)
        canvas.restore()
        paint.shader = null; paint.style = Paint.Style.FILL; paint.alpha = 255

        val rows = 3 + 45 * pose.p
        text(canvas, "Row %02d of 48".format(java.util.Locale.US, min(48, floor(rows + .000001).toInt())), 24f, 620f, 12f, 500, tabular = true)
        text(canvas, "${floor(rows / 48 * 100 + .000001).toInt()}%", 369f, 620f, 12f, 500, align = Paint.Align.RIGHT, tabular = true)
        round(canvas, 24f, 640f, 345f, 3f, 1.5f, color(0xC4CBC7, .20))
        round(canvas, 24f, 640f, (345 * rows / 48).toFloat(), 3f, 1.5f, color(0xEAE8DC))
        repeat(3) { index ->
            val selected = driver.pattern == index
            round(canvas, (24 + 112 * index).toFloat(), 667f, 104f, 35f, 17.5f,
                if (selected) color(0xEAE8DC) else color(0x263235, .7))
            text(canvas, arrayOf("Sprig", "Ripple", "Plain")[index], (76 + 112 * index).toFloat(), 684.5f, 12.5f, 500,
                if (selected) color(0x263235) else foreground, Paint.Align.CENTER)
        }
        text(canvas, "YARN", 24f, 733 - bottom, 10f, 600, tracking = 1.5f)
        repeat(3) { index ->
            val x = (258 + index * 44).toFloat(); val y = 733 - bottom
            paint.style = Paint.Style.FILL; paint.color = color(m.yarns[index]); canvas.drawCircle(x, y, 11f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (driver.yarn == index) 1.5f else .8f
            paint.color = color(0xF2F1E7, if (driver.yarn == index) .85 else .12); canvas.drawCircle(x, y, 15f, paint)
        }
        paint.style = Paint.Style.FILL
        round(canvas, 24f, 755 - bottom, 345f, 54f, 27f, color(0xEAE8DC))
        if (driver.saved) {
            text(canvas, "Pattern saved", 207.5f, 782 - bottom, 15.5f, 600, color(0x263234), Paint.Align.CENTER)
            type.textSize = 15.5f; type.typeface = faces.getValue(600)
            val start = 207.5f - type.measureText("Pattern saved") / 2 - 20
            paint.color = color(0x263234); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            path.rewind(); path.moveTo(start, 782 - bottom); path.lineTo(start + 4f, 786 - bottom); path.lineTo(start + 12f, 777 - bottom); canvas.drawPath(path, paint)
        } else text(canvas, "Save pattern", 196.5f, 782 - bottom, 15.5f, 600, color(0x263234), Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
    }

    private fun materialStyles(driver: StitchWeavePreviewDriver, now: Double, reduced: Boolean, preview: Double?) {
        val age = driver.materialStarted?.let { max(0.0, now - it) } ?: -1.0
        val reset = driver.reset(now, reduced, preview)
        repeat(48) { row ->
            val wave = m.materialMix(row, age, driver.materialRows, reduced)
            repeat(6) { channel ->
                val target = if (channel == driver.pattern || channel == driver.yarn + 3) 1.0 else 0.0
                val w = driver.materialBase[row * 6 + channel] * (1 - wave) + target * wave
                rowWeights[channel] = w * (1 - reset) + if (channel == 0 || channel == 3) reset else 0.0
            }
            val offset = row * 4
            styles[offset] = ((222 * rowWeights[3] + 136 * rowWeights[4] + 125 * rowWeights[5]) / 255).toFloat()
            styles[offset + 1] = ((220 * rowWeights[3] + 154 * rowWeights[4] + 140 * rowWeights[5]) / 255).toFloat()
            styles[offset + 2] = ((211 * rowWeights[3] + 140 * rowWeights[4] + 146 * rowWeights[5]) / 255).toFloat()
            styles[offset + 3] = (floor(m.clamp(rowWeights[0]) * 4095 + .5) + 4096 * floor(m.clamp(rowWeights[1]) * 4095 + .5)).toFloat()
        }
    }
    private fun round(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, tint: Int) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = tint
        rect.set(x, y, x + w, y + h); c.drawRoundRect(rect, r, r, paint)
    }
    private fun text(c: Canvas, value: String, x: Float, centerY: Float, size: Float, weight: Int,
        tint: Int = foreground, align: Paint.Align = Paint.Align.LEFT, tracking: Float = 0f, tabular: Boolean = false) {
        type.color = tint; type.textSize = size; type.textAlign = align
        type.typeface = faces.getValue(weight)
        type.fontFeatureSettings = if (tabular) "tnum" else null
        val metrics = type.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2
        if (tracking == 0f) c.drawText(value, x, baseline, type) else {
            val widths = trackingWidths.getOrPut(value) { FloatArray(value.length).also { type.getTextWidths(value, it) } }
            val total = widths.sum() + tracking * (value.length - 1)
            var cursor = x - when (align) { Paint.Align.CENTER -> total / 2; Paint.Align.RIGHT -> total; else -> 0f }
            type.textAlign = Paint.Align.LEFT
            value.indices.forEach { i -> c.drawText(value, i, i + 1, cursor, baseline, type); cursor += widths[i] + tracking }
        }
    }
    fun close() { /* Immutable resource bitmaps belong to the shared cache. */ }
    private fun color(rgb: Int, alpha: Double = 1.0) = Color.argb((alpha * 255).roundToInt().coerceIn(0, 255), (rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
}

@RequiresApi(33)
private class StitchWeavePreviewGpu(private val resources: StitchWeavePreviewResources) {
    private val shader = RuntimeShader(STITCH_WEAVE_PREVIEW_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = FloatArray(18)
    init {
        val atlas = BitmapShader(resources.atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        atlas.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        shader.setInputBuffer("uAtlas", atlas)
        paint.shader = shader
    }
    fun draw(canvas: Canvas, pose: StitchWeavePreviewPose, canonical: Double, styles: FloatArray) {
        resources.uniforms(pose, matrix)
        shader.setFloatUniform("phase", pose.p.toFloat(), canonical.toFloat(), pose.crownElapsed.toFloat(), pose.topologySpeed.toFloat())
        shader.setFloatUniform("shape", pose.lift.toFloat(), pose.hem.toFloat(), pose.anticipation.toFloat(), pose.lightPosition.toFloat())
        shader.setFloatUniform("light", pose.light.toFloat(), -1f, 0f, 0f)
        shader.setFloatUniform("selection", 0f, 0f, 0f, 0f)
        shader.setFloatUniform("matrix", matrix); shader.setFloatUniform("styles", styles)
        paint.alpha = 255; canvas.drawRect(22f, 184f, 371f, 595f, paint)
    }
}

/** API26–32: the same sixteen physical yarn poses travel on a forward-projected cloth mesh. */
private class StitchWeavePreviewFallback(private val resources: StitchWeavePreviewResources) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val vertices = FloatArray(52 * 48 * 12)
    private val tex = FloatArray(vertices.size)
    private val colors = IntArray(vertices.size / 2)
    private val localX = doubleArrayOf(-1.8, 8.05, 8.05, -1.8, 8.05, -1.8)
    private val localY = doubleArrayOf(-2.0, -2.0, 10.3, -2.0, 10.3, 10.3)
    private val m = StitchWeavePreviewMath
    init { paint.shader = BitmapShader(resources.fallbackAtlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    fun draw(canvas: Canvas, pose: StitchWeavePreviewPose, canonical: Double, styles: FloatArray) {
        val projector = resources.projector(pose)
        // Add only the two neighboring formation samples inside one isolated layer.
        val layer = canvas.saveLayer(22f, 184f, 371f, 595f, null)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        repeat(2) { pass ->
            var count = 0
            repeat(48) { row -> repeat(52) cell@ { column ->
                if (row >= 3 && pose.p < .999999 && (pose.p <= 0 || row > 4 + pose.p * 45)) return@cell
                val formed = m.columnRows(pose.p, canonical, column)
                val formation = if (row < 3 || pose.p >= .999999) 1.0 else m.clamp((formed - (row + 1) + .45) / .45)
                if (formation <= 0) return@cell
                val f = formation * 15; val a = floor(f).toInt(); val b = min(15, a + 1)
                val blend = if (pass == 0) 1 - (f - a) else f - a
                if (blend <= 0) return@cell
                val frame = if (pass == 0) a else b
                val elapsed = (canonical - resources.completion[row] - resources.delays[column]) / max(.05, pose.topologySpeed) + pose.crownElapsed
                val settle = if (row >= 3 && formation >= .999 && elapsed > 0 && elapsed < .16) -1.8 * sin(PI * elapsed / .16) * exp(-elapsed / .06) else 0.0
                val packed = styles[row * 4 + 3].toInt()
                val marked = (packed % 4096) / 4095.0 * resources.motifs[row][column * 2] + (packed / 4096) / 4095.0 * resources.motifs[row][column * 2 + 1]
                val lighting = .92 + pose.light * exp(-((35.3 + column * 6.2 - pose.lightPosition) / 27).pow(2))
                fun channel(k: Int, ink: Int) = ((styles[row * 4 + k] * 255 * (1 - marked) + ink * marked) * lighting).roundToInt().coerceIn(0, 255)
                val tint = Color.argb((255 * blend).roundToInt(), channel(0, 52), channel(1, 61), channel(2, 61))
                repeat(6) { vertex ->
                    val x = localX[vertex]; val y = localY[vertex]
                    val adjustedY = (y - 3.4) * (1 + .03 * pose.anticipation) + 3.4 + settle * m.clamp((6.3 - y) / 5.8)
                    val point = projector.project((column * 6.2 + x) / 322.4, (572 - (row + 1) * 6.8 + adjustedY - 245.6) / 326.4)
                    val offset = count * 2
                    vertices[offset] = point.x.toFloat(); vertices[offset + 1] = point.y.toFloat()
                    tex[offset] = ((frame % 8) * 32 + (x + 2.2) * 3).toFloat()
                    tex[offset + 1] = ((frame / 8) * 40 + (y + 2.4) * 3).toFloat()
                    colors[count] = tint; count++
                }
            } }
            paint.alpha = 255
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 2, vertices, 0, tex, 0, colors, 0, null, 0, 0, paint)
        }
        paint.xfermode = null; canvas.restoreToCount(layer)
    }
}
