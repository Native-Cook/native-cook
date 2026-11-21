package com.ngocdevv.nativecook.blocks.progress.neonboltstreak

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.cos

internal class NeonBoltStreakArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isSubpixelText = true }
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tube = 0xFFFFD91A.toInt()
    private val core = 0xFFFFF8C9.toInt()
    private val dead = 0xFF57585E.toInt()
    private val heroOutline = PathParser().parsePathString(
        "M133.509 2.582 L2.685 131.354 A9 9 0 0 0 6.418 146.390 L63.853 163.584 A6 6 0 0 1 67.910 170.950 L36.838 281.841 A8 8 0 0 0 50.055 289.796 L187.207 159.329 A9 9 0 0 0 183.680 144.215 L128.252 126.957 A5 5 0 0 1 124.851 121.129 L148.620 10.893 A9 9 0 0 0 133.509 2.582 Z",
    ).toPath().asAndroidPath()
    private val dayOutline = PathParser().parsePathString(
        "M23.746 3.118 L2.827 26.245 A3.2 3.2 0 0 0 4.180 31.425 L13.024 34.398 A2.2 2.2 0 0 1 14.458 37.016 L10.368 53.409 A3.7 3.7 0 0 0 16.660 56.833 L37.136 34.956 A3.2 3.2 0 0 0 35.856 29.748 L27.364 26.779 A2 2 0 0 1 26.060 24.514 L29.607 6.040 A3.4 3.4 0 0 0 23.746 3.118 Z",
    ).toPath().asAndroidPath()
    private val zinc = LinearGradient(
        4f, 5f, 29f, 58f,
        intArrayOf(0xFFC9C8C0.toInt(), 0xFF8B8A84.toInt(), 0xFF55544F.toInt()),
        floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP,
    )
    private val shader: RuntimeShader? = if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(NeonBoltStreakShaders.BACKDROP) }.getOrNull() else null

    private val hazeShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(NeonBoltStreakShaders.HAZE) }.getOrNull() else null

    fun draw(canvas: Canvas, width: Float, height: Float, safeInset: Float, frame: NeonBoltStreakFrame) {
        val scale = neonBoltStreakScale(width, height, safeInset)
        canvas.save()
        canvas.translate((width - 402f * scale) / 2, (height - 874f * scale) / 2)
        canvas.scale(scale, scale)
        backdrop(canvas, frame.time); grain(canvas, frame.time)
        val zoom = 3.5f.pow(frame.camera)
        canvas.save()
        canvas.translate(181f + 20f * frame.camera, 629f - 159f * frame.camera)
        canvas.rotate(-3f * frame.camera)
        canvas.scale(zoom, zoom * cos(Math.toRadians(14.0 * frame.camera)).toFloat())
        canvas.translate(-181f, -629f + frame.jolt)
        hero(canvas, frame); week(canvas, frame); canvas.restore(); canvas.restore()
    }

    private fun backdrop(canvas: Canvas, time: Double) {
        val runtime = shader
        if (runtime != null && Build.VERSION.SDK_INT >= 33) {
            runtime.setFloatUniform("uSize", 402f, 874f); runtime.setFloatUniform("uTime", time.toFloat())
            fill.alpha = 255
            fill.shader = runtime; canvas.drawRect(0f, -30f, 402f, 900f, fill); fill.shader = null
        } else canvas.drawColor(0xFF26272B.toInt())
    }

    private fun hero(canvas: Canvas, frame: NeonBoltStreakFrame) {
        val visibility = 1f - smooth(.53f, .92f, frame.camera)
        if (visibility <= .001f) return
        radial(canvas, 201f, 296f, 100f, tube, (.06f + .13f * frame.flood) * visibility)
        for (center in ghostCenters) {
            canvas.save()
            canvas.translate(center - 95f * .62f, 296f - 146f * .62f)
            canvas.scale(.62f, .62f)
            fill.color = Color.argb((255 * frame.ghost * visibility).roundToInt(), 255, 255, 238)
            canvas.drawPath(heroOutline, fill)
            canvas.restore()
        }
        canvas.save()
        canvas.translate(106f, 150f)
        val level = (.42f + 1.43f * (frame.count / 7f + frame.flood * .12f)).coerceAtMost(1.85f)
        fill.color = Color.WHITE
        fill.alpha = (255 * visibility).roundToInt()
        fill.shader = LinearGradient(
            20f, 0f, 52f, 292f * level,
            intArrayOf(0xFFFFE16D.toInt(), 0xFFC9A516.toInt(), 0x00C9A516),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(heroOutline, fill)
        fill.shader = null
        fill.alpha = 255
        stroke.color = Color.argb((31 * visibility).roundToInt(), 255, 255, 255)
        stroke.strokeWidth = 1f
        canvas.drawPath(heroOutline, stroke)
        canvas.restore()
        text.color = Color.WHITE
        text.textSize = 128f
        text.alpha = (255 * visibility).roundToInt()
        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(frame.count.roundToInt().toString(), 207f, 440f, text)
        text.color = 0xFF8C8D92.toInt()
        text.alpha = (255 * visibility).roundToInt()
        text.textSize = 28f
        canvas.drawText("days streak", 201f, 495f, text)
        text.typeface = android.graphics.Typeface.DEFAULT
        if (frame.flash > 0f) radial(canvas, 201f, 295f, 180f, tube, frame.flash * .42f)
    }

    private fun week(canvas: Canvas, frame: NeonBoltStreakFrame) {
        val rect = RectF(16f, 560f, 386f, 728f); fill.color = 0xFF17181B.toInt(); canvas.drawRoundRect(rect, 20f, 20f, fill)
        stroke.color = 0xFF3B3C41.toInt(); stroke.strokeWidth = 2f; canvas.drawRoundRect(rect, 20f, 20f, stroke)
        if (frame.borderLit) border(canvas, rect, frame.border)
        ignitionHaze(canvas, frame)
        for (index in 0..6) {
            val x = 16f + 370f / 7f * (index + .5f); dayBolt(canvas, x, 629f, frame, index)
            text.textSize = 13f
            val light = (frame.fill[index] * frame.lit[index]).coerceIn(0f, 1f)
            text.color = Color.rgb((109 + 133 * light).roundToInt(), (110 + 97 * light).roundToInt(), (115 - 73 * light).roundToInt())
            canvas.drawText(NeonBoltStreakMotion.DAYS[index], x, 689f, text)
        }
        if (frame.strikeDay >= 0 && frame.strikeDrop < 1f) { val x = 16f + 370f / 7f * (frame.strikeDay + .5f); glowPath(canvas, boltPath(x - 7f, 574f + 44f * frame.strikeDrop, 14f, 22f), core, 12f, 1f) }
        if (frame.strikeDay >= 0 && frame.charge > .02f) { stroke.color = tube; stroke.alpha = (255 * (.3f + frame.charge * .7f)).roundToInt(); stroke.strokeWidth = 2f; val r = RectF(16f + 370f / 7f * (frame.strikeDay + .5f) - 25f, 604f, 16f + 370f / 7f * (frame.strikeDay + .5f) + 25f, 654f); canvas.drawArc(r, -62f, frame.charge * 302f, false, stroke); stroke.alpha = 255 }
    }

    private fun dayBolt(canvas: Canvas, x: Float, y: Float, frame: NeonBoltStreakFrame, index: Int) {
        val amount = frame.fill[index].coerceIn(0f, 1f)
        if (amount < 1f) {
            canvas.save()
            canvas.translate(x - 20f, y - 30f)
            fill.color = Color.WHITE
            fill.alpha = (255f * (1f - smooth(.6f, 1f, amount))).roundToInt()
            fill.shader = zinc
            canvas.drawPath(dayOutline, fill)
            fill.shader = null
            fill.alpha = 255
            canvas.restore()
        }
        val chains = NeonBoltStreakTube.chains.map { chain -> Path().apply {
            chain.forEachIndexed { point, xy -> if (point == 0) moveTo(x + xy[0], y + xy[1]) else lineTo(x + xy[0], y + xy[1]) }
        } }
        stroke.color = dead; stroke.strokeWidth = 2.5f
        if (amount > 0f) chains.forEach { canvas.drawPath(it, stroke) }
        if (amount <= 0f) return
        for (chain in chains) {
            val measure = android.graphics.PathMeasure(chain, false)
            val lit = Path(); measure.getSegment(0f, measure.length * amount, lit, true)
            glowPath(canvas, lit, tube, 15f, frame.lit[index])
            glowPath(canvas, lit, tube, 3.5f, .85f * frame.lit[index])
            stroke.color = tube; stroke.strokeWidth = 3.5f; stroke.alpha = (255f * frame.lit[index]).roundToInt().coerceIn(0, 255); canvas.drawPath(lit, stroke)
            stroke.color = core; stroke.strokeWidth = 1.35f; stroke.alpha = (255f * frame.lit[index]).roundToInt().coerceIn(0, 255); canvas.drawPath(lit, stroke); stroke.alpha = 255
            if (frame.head[index] > 0f) {
                val head = FloatArray(2); measure.getPosTan(measure.length * amount, head, null)
                radial(canvas, head[0], head[1], 9f, core, frame.head[index])
            }
        }
    }

    /** Broad ignition haze is present only while a discharge head is active. The isolated LIGHTEN layer keeps the
     * strongest overlapping head rather than flattening the panel with additive yellow. */
    private fun ignitionHaze(canvas: Canvas, frame: NeonBoltStreakFrame) {
        if (frame.head.none { it > .001f }) return
        val runtime = hazeShader
        if (runtime != null && Build.VERSION.SDK_INT >= 33) {
            runtime.setFloatUniform("uLit", frame.lit)
            runtime.setFloatUniform("uFill", frame.fill)
            runtime.setFloatUniform("uHead", frame.head)
            haze.shader = runtime
            haze.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            canvas.drawRect(-32f, 512f, 434f, 776f, haze)
            haze.shader = null; haze.xfermode = null
            return
        }
        val layer = canvas.saveLayer(RectF(-32f, 512f, 434f, 776f), null)
        haze.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        for (index in 0..6) {
            val head = frame.head[index]
            val fillAmount = frame.fill[index]
            if (head <= .001f || fillAmount <= 0f) continue
            val gain = .065f * head * frame.lit[index].coerceAtLeast(0f) * smooth(0f, .3f, fillAmount)
            if (gain <= .001f) continue
            val x = 16f + 370f / 7f * (index + .5f)
            val path = tubePath(x - 20f, 599f, 40f, 60f)
            val measure = android.graphics.PathMeasure(path, false)
            val amount = (fillAmount * .5f).coerceIn(0f, .5f)
            val first = FloatArray(2); val second = FloatArray(2)
            measure.getPosTan(measure.length * amount, first, null)
            measure.getPosTan(measure.length * (1f - amount), second, null)
            drawHaze(canvas, first[0], first[1], gain)
            drawHaze(canvas, second[0], second[1], gain)
        }
        haze.xfermode = null
        canvas.restoreToCount(layer)
    }

    private fun drawHaze(canvas: Canvas, x: Float, y: Float, alpha: Float) {
        fun color(amount: Float) = Color.argb((alpha * amount * 255f).roundToInt().coerceIn(0, 255), 245, 196, 0)
        haze.shader = RadialGradient(x, y, 110f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, color(1f), color(.37f), color(.15f), Color.TRANSPARENT), floatArrayOf(0f, 4f / 110f, 9f / 110f, 42f / 110f, 80f / 110f, 1f), Shader.TileMode.CLAMP)
        haze.alpha = 255
        canvas.drawCircle(x, y, 110f, haze)
        haze.shader = null
    }

    private fun smooth(start: Float, end: Float, value: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun border(canvas: Canvas, rect: RectF, progress: Float) { val path = Path(); path.addRoundRect(RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f), 17f, 17f, Path.Direction.CW); val measure = android.graphics.PathMeasure(path, false); val segment = Path(); measure.getSegment(0f, measure.length * progress.coerceAtMost(1f), segment, true); glowPath(canvas, segment, tube, 10f, 1f); stroke.color = core; stroke.strokeWidth = 1.2f; canvas.drawPath(segment, stroke) }
    private fun glowPath(canvas: Canvas, path: Path, color: Int, radius: Float, alpha: Float) { stroke.color = color; stroke.alpha = (255 * alpha.coerceIn(0f, 1.3f)).roundToInt().coerceAtMost(255); stroke.strokeWidth = 5f; stroke.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL); canvas.drawPath(path, stroke); stroke.maskFilter = null; stroke.alpha = 255 }
    private fun radial(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) { fill.alpha = 255; fill.shader = RadialGradient(x, y, radius, intArrayOf(Color.argb((255 * alpha).roundToInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT), null, Shader.TileMode.CLAMP); canvas.drawCircle(x, y, radius, fill); fill.shader = null }
    private fun grain(canvas: Canvas, time: Double) { fill.color = 0x08FFFFFF; for (i in 0 until 90) { val x = (i * 47 % 401).toFloat(); val y = (i * 71 % 870).toFloat(); canvas.drawRect(x, y, x + 1f, y + 1f, fill) } }
    private fun boltPath(x: Float, y: Float, w: Float, h: Float) = Path().apply { moveTo(x + .54f * w, y); lineTo(x + .12f * w, y + .43f * h); lineTo(x + .42f * w, y + .53f * h); lineTo(x + .28f * w, y + h); lineTo(x + w, y + .56f * h); lineTo(x + .68f * w, y + .46f * h); close() }
    private fun tubePath(x: Float, y: Float, w: Float, h: Float) = Path().apply { moveTo(x + .29f * w, y + h - 2f); lineTo(x + .42f * w, y + .54f * h); lineTo(x + .10f * w, y + .45f * h); lineTo(x + .62f * w, y + 2f); lineTo(x + .47f * w, y + .42f * h); lineTo(x + w - 2f, y + .56f * h); lineTo(x + .29f * w, y + h - 2f) }

    private companion object {
        val ghostCenters = floatArrayOf(24f, 92f, 310f, 378f)
    }
}
