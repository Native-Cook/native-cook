package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.annotation.TargetApi
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val FontAsset = "pullcordtheme/pullcordtheme_Newsreader.ttf"

private object PullCordThemeColors {
    const val DayPaper = 0xFFF1EBE0.toInt()
    const val DayInk = 0xFF27231E.toInt()
    const val DayMute = 0xFF6E6558.toInt()
    const val DayAccent = 0xFF2F5D48.toInt()
    const val NightPaper = 0xFF121211.toInt()
    const val NightInk = 0xFFD8D0C2.toInt()
    const val NightMute = 0xFF8E877C.toInt()
    const val NightAccent = 0xFF83B097.toInt()
}

private data class PullCordThemeTextLine(
    val text: String,
    val x: Float,
    val baseline: Float,
    val size: Float,
    val role: Int,
    val align: Paint.Align = Paint.Align.LEFT,
    val tracking: Float = 0f,
)

private data class PullCordThemeBar(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val role: Int,
    val alpha: Int = 255,
)

/** Static page layout: line breaking and glyph placement are computed once for each screen size. */
private class PullCordThemePageLayout(
    private val typeface: Typeface,
    width: Float,
    height: Float,
    scale: Float,
) {
    val lines = mutableListOf<PullCordThemeTextLine>()
    val bars = mutableListOf<PullCordThemeBar>()

    init {
        val margin = 24f * scale
        val measure = width - margin * 2f
        lines += PullCordThemeTextLine("CHAPTER SEVEN", margin, baseline(124f * scale, 12f * scale), 12f * scale, 2, tracking = 2.2f / 12f)
        lines += PullCordThemeTextLine("Low Water", margin, baseline(144f * scale, 42f * scale), 42f * scale, 1)

        bars += PullCordThemeBar(margin, 192f * scale, margin + 28f * scale, 194f * scale, 3)

        val labelTop = height - (48f + 16f) * scale
        val barTop = labelTop - (12f + 2f) * scale
        val bodyTop = 296f * scale
        val bodyLine = 29f * scale
        val maxBodyLines = floor((barTop - 24f * scale - bodyTop) / bodyLine).toInt().coerceAtLeast(1)
        val paragraphs = listOf(
            "By the time the tide turned, Mara had counted the boats twice. Eleven out, ten back. She wrote the number in the ledger anyway, in pencil, the way her father had taught her: nothing in ink until morning.",
            "\u2003The lamp above the desk swung a little whenever the door opened, and the light slid across the page and back. Outside, fog had taken the breakwater. The last ferry’s horn came through it late and flattened, like a voice from another room.",
            "\u2003She left the window open. If the eleventh boat came in, she wanted to hear it before she saw it. Her father used to say the sea kept its own books, and that it was never late with a payment, only with the news.",
        )
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = this@PullCordThemePageLayout.typeface
            textSize = 18f * scale
        }
        val wrapped = paragraphs.flatMap { wrap(it, bodyPaint, measure) }.take(maxBodyLines)
        wrapped.forEachIndexed { index, text ->
            lines += PullCordThemeTextLine(
                text,
                margin,
                baseline(bodyTop + bodyLine * index, 18f * scale),
                18f * scale,
                1,
            )
        }

        val footBaseline = baseline(labelTop, 13f * scale)
        lines += PullCordThemeTextLine("Page 112 of 304", margin, footBaseline, 13f * scale, 2)
        lines += PullCordThemeTextLine("9 min left in chapter", width - margin, footBaseline, 13f * scale, 2, Paint.Align.RIGHT)
        bars += PullCordThemeBar(margin, barTop, width - margin, barTop + 2f * scale, 2, 76)
        bars += PullCordThemeBar(margin, barTop, margin + measure * (112f / 304f), barTop + 2f * scale, 3)
    }

    private fun baseline(top: Float, size: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = this@PullCordThemePageLayout.typeface
            textSize = size
        }
        return top - paint.fontMetrics.top
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val words = text.split(' ')
        val result = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= width || line.isEmpty()) {
                line = candidate
            } else {
                result += line
                line = word
            }
        }
        if (line.isNotEmpty()) result += line
        return result
    }
}

internal class PullCordThemePainter(context: Context) {
    private val typeface = Typeface.createFromAsset(context.assets, FontAsset)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@PullCordThemePainter.typeface }
    private val pageShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PullCordThemePageShader() else null
    private val airShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PullCordThemeAirShader() else null
    private var layoutKey = ""
    private var pageLayout: PullCordThemePageLayout? = null

    private val dome = Path().apply {
        moveTo(-8f, 12f)
        cubicTo(-11f, 28f, -51f, 37.44f, -68f, 72f)
        lineTo(68f, 72f)
        cubicTo(51f, 37.44f, 11f, 28f, 8f, 12f)
        close()
    }
    private val domeSpec = Path().apply {
        moveTo(-6f, 16f)
        cubicTo(-10f, 30f, -32.64f, 40.32f, -42.16f, 66f)
    }
    private val pullShape = Path().apply {
        moveTo(-1.6f, 0f)
        lineTo(1.6f, 0f)
        lineTo(1.8f, 4f)
        cubicTo(2.6f, 8f, 5f, 13f, 5f, 19f)
        quadTo(5f, 22f, 0f, 22f)
        quadTo(-5f, 22f, -5f, 19f)
        cubicTo(-5f, 13f, -2.6f, 8f, -1.8f, 4f)
        close()
    }

    fun draw(canvas: Canvas, engine: PullCordThemeEngine) {
        val geometry = engine.geometry
        val key = "${geometry.width.toInt()}x${geometry.height.toInt()}"
        if (layoutKey != key) {
            layoutKey = key
            pageLayout = PullCordThemePageLayout(typeface, geometry.width, geometry.height, geometry.scale)
        }
        drawPage(canvas, engine, pageLayout ?: return)
        drawAir(canvas, engine)
        drawLamp(canvas, engine)
        drawChain(canvas, engine)
    }

    private fun drawPage(canvas: Canvas, engine: PullCordThemeEngine, layout: PullCordThemePageLayout) {
        val shader = pageShader
        if (shader != null) {
            shader.update(engine)
            shader.role(0f)
            fill.shader = shader.shader
            fill.alpha = 255
            canvas.drawRect(0f, 0f, engine.geometry.width, engine.geometry.height, fill)
            drawShaderText(canvas, layout, shader)
            fill.shader = null
            text.shader = null
            return
        }
        drawFallbackPage(canvas, engine, layout)
    }

    @TargetApi(33)
    private fun drawShaderText(canvas: Canvas, layout: PullCordThemePageLayout, shader: PullCordThemePageShader) {
        for (role in 1..3) {
            shader.role(role.toFloat())
            text.shader = shader.shader
            layout.lines.filter { it.role == role }.forEach { line ->
                text.typeface = typeface
                text.textSize = line.size
                text.textAlign = line.align
                text.letterSpacing = line.tracking
                text.alpha = 255
                canvas.drawText(line.text, line.x, line.baseline, text)
            }
            fill.shader = shader.shader
            layout.bars.filter { it.role == role }.forEach { bar ->
                fill.alpha = bar.alpha
                canvas.drawRect(bar.left, bar.top, bar.right, bar.bottom, fill)
            }
        }
        fill.alpha = 255
        text.letterSpacing = 0f
    }

    private fun drawFallbackPage(canvas: Canvas, engine: PullCordThemeEngine, layout: PullCordThemePageLayout) {
        val mix = engine.front.coerceIn(0f, 1f)
        val paper = blend(PullCordThemeColors.NightPaper, PullCordThemeColors.DayPaper, mix)
        canvas.drawColor(paper)
        val bulb = pullCordThemeLampPoint(engine.geometry, engine.angle, 0f, PullCordThemeBulbY)
        val bright = pullCordThemeBrightness(engine.heat) * engine.filamentFlicker()
        if (bright > 0.001f) {
            val lamp = pullCordThemeLampColor(engine.heat)
            fill.shader = RadialGradient(
                bulb.x,
                bulb.y,
                250f * engine.geometry.scale,
                intArrayOf(rgba(lamp, (0.18f * bright)), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, engine.geometry.width, engine.geometry.height, fill)
            fill.shader = null
        }
        for (role in 1..3) {
            val night = when (role) {
                1 -> PullCordThemeColors.NightInk
                2 -> PullCordThemeColors.NightMute
                else -> PullCordThemeColors.NightAccent
            }
            val day = when (role) {
                1 -> PullCordThemeColors.DayInk
                2 -> PullCordThemeColors.DayMute
                else -> PullCordThemeColors.DayAccent
            }
            text.color = blend(night, day, mix)
            text.shader = null
            layout.lines.filter { it.role == role }.forEach { line ->
                text.typeface = typeface
                text.textSize = line.size
                text.textAlign = line.align
                text.letterSpacing = line.tracking
                text.alpha = 255
                canvas.drawText(line.text, line.x, line.baseline, text)
            }
            fill.color = blend(night, day, mix)
            layout.bars.filter { it.role == role }.forEach { bar ->
                fill.alpha = bar.alpha
                canvas.drawRect(bar.left, bar.top, bar.right, bar.bottom, fill)
            }
        }
        fill.alpha = 255
        text.letterSpacing = 0f
    }

    private fun drawAir(canvas: Canvas, engine: PullCordThemeEngine) {
        if (engine.reducedMotion) return
        val brightness = pullCordThemeBrightness(engine.heat) * engine.filamentFlicker()
        if (brightness < 0.002f) return
        val shader = airShader
        if (shader != null) {
            shader.update(engine)
            fill.shader = shader.shader
            fill.blendMode = BlendMode.SCREEN
            canvas.drawRect(0f, 0f, engine.geometry.width, engine.geometry.height, fill)
            fill.blendMode = null
            fill.shader = null
            return
        }
        val geometry = engine.geometry
        val bulb = pullCordThemeLampPoint(geometry, engine.angle, 0f, PullCordThemeBulbY)
        val axisX = sin(engine.angle)
        val axisY = cos(engine.angle)
        val sideX = axisY
        val sideY = -axisX
        val lamp = pullCordThemeLampColor(engine.heat)
        val cone = Path().apply {
            moveTo(bulb.x, bulb.y)
            lineTo(bulb.x + (axisX - sideX * 1.25f) * 280f * geometry.scale, bulb.y + (axisY - sideY * 1.25f) * 280f * geometry.scale)
            lineTo(bulb.x + (axisX + sideX * 1.25f) * 280f * geometry.scale, bulb.y + (axisY + sideY * 1.25f) * 280f * geometry.scale)
            close()
        }
        fill.shader = RadialGradient(
            bulb.x,
            bulb.y,
            270f * geometry.scale,
            intArrayOf(rgba(lamp, 0.055f * brightness), Color.TRANSPARENT),
            floatArrayOf(0.05f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(cone, fill)
        fill.shader = null

        val phase = (2f * PI.toFloat() * (engine.clock % LOOP.toFloat()) / LOOP.toFloat())
        repeat(12) { index ->
            val h1 = hash(index.toFloat(), 1.7f)
            val h2 = hash(index.toFloat(), 9.3f)
            val h3 = hash(index.toFloat(), 4.1f)
            val along = (40f + 230f * h1) * geometry.scale
            val across = (h2 - 0.5f) * 1.6f * along
            val driftX = sin(phase * (1f + floor(h3 * 2f)) + h1 * 2f * PI.toFloat()) * 6f * geometry.scale
            val driftY = sin(phase + h2 * 2f * PI.toFloat()) * 6f * geometry.scale
            val cx = bulb.x + axisX * along + sideX * across + driftX
            val cy = bulb.y + axisY * along + sideY * across + driftY
            val radius = (0.7f + 0.6f * h3) * geometry.scale
            val twinkle = 0.65f + 0.35f * sin(phase * (1f + floor(h2 * 3f)) + h3 * 2f * PI.toFloat())
            fill.color = rgba(lamp, brightness * 0.28f * twinkle)
            canvas.drawCircle(cx, cy, radius, fill)
        }
    }

    private fun drawLamp(canvas: Canvas, engine: PullCordThemeEngine) {
        val geometry = engine.geometry
        val heat = engine.heat.coerceIn(0f, 1f)
        val front = engine.front.coerceIn(0f, 1f) * (0.45f + 0.55f * heat)
        val bloom = pullCordThemeBrightness(heat) * engine.filamentFlicker()
        val lamp = pullCordThemeLampColor(heat)

        canvas.save()
        canvas.translate(geometry.pivotX, geometry.pivotY)
        canvas.rotate(-engine.angle * 180f / PI.toFloat())
        canvas.translate(0f, geometry.arm)
        canvas.scale(geometry.scale, geometry.scale)

        fill.color = 0xFF2A2926.toInt()
        canvas.drawRect(-1.1f, -PullCordThemeCord, 1.1f, 1f, fill)
        fill.color = withAlpha(0xFF1F1D1A.toInt(), front)
        canvas.drawRect(-1.1f, -PullCordThemeCord, 1.1f, 1f, fill)

        drawShell(canvas, false, 1f)
        drawShell(canvas, true, front)

        if (bloom > 0.001f) {
            fill.shader = LinearGradient(0f, 32f, 0f, 72f, Color.TRANSPARENT, withAlpha(0xFFFFCC96.toInt(), 0.16f * bloom), Shader.TileMode.CLAMP)
            canvas.drawPath(dome, fill)
            fill.shader = null
        }

        fill.shader = RadialGradient(0f, 72f, 68f, intArrayOf(0xFF34322D.toInt(), 0xFF1C1B18.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(-67f, 63.5f, 67f, 80.5f), fill)
        if (heat > 0.001f) {
            val centre = blend(rgba(lamp, 1f), 0xFFFBEBD0.toInt(), heat)
            val edge = blend(blend(rgba(lamp, 1f), Color.BLACK, 0.4f), 0xFFD9AD72.toInt(), heat)
            fill.shader = RadialGradient(0f, 72f, 68f, intArrayOf(centre, edge), null, Shader.TileMode.CLAMP)
            fill.alpha = (255f * heat * heat).toInt().coerceIn(0, 255)
            canvas.drawOval(RectF(-67f, 63.5f, 67f, 80.5f), fill)
            fill.alpha = 255
        }
        fill.shader = null

        drawHousing(canvas, false, 1f)
        drawHousing(canvas, true, front)

        stroke.strokeWidth = 2.4f
        stroke.color = 0xFF363A36.toInt()
        val lip = Path().apply { addArc(RectF(-66.8f, 64.2f, 66.8f, 79.8f), 0f, 180f) }
        canvas.drawPath(lip, stroke)
        stroke.color = withAlpha(0xFFE2DACB.toInt(), front)
        canvas.drawPath(lip, stroke)
        stroke.strokeWidth = 1.44f
        stroke.color = withAlpha(0xFFF7D49E.toInt(), heat * heat)
        canvas.drawPath(lip, stroke)

        canvas.save()
        canvas.clipPath(Path().apply {
            addOval(RectF(-66f, 64f, 66f, 80f), Path.Direction.CW)
            addRect(-68f, 72f, 68f, 132f, Path.Direction.CW)
        })
        fill.shader = RadialGradient(-6f, 76f, 21f, intArrayOf(0xFF3A3833.toInt(), 0xFF1A1916.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, PullCordThemeBulbY, PullCordThemeBulbRadius, fill)
        if (heat > 0.001f) {
            val white = floatArrayOf(1f, 0.988f, 0.957f)
            val black = floatArrayOf(0f, 0f, 0f)
            val colours = intArrayOf(
                rgba(mix(lamp, white, heat), 1f),
                rgba(mix(lamp, white, heat * 0.8f), 1f),
                rgba(lamp, 1f),
                rgba(mix(lamp, black, 0.3f), 1f),
            )
            fill.shader = RadialGradient(0f, 75f, 16f, colours, floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = (255f * heat * heat).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, PullCordThemeBulbY, PullCordThemeBulbRadius, fill)
            fill.alpha = 255
        }
        if (bloom > 0.001f) {
            fill.shader = null
            fill.color = withAlpha(Color.WHITE, bloom * 0.7f)
            fill.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(0f, PullCordThemeBulbY + 5f, PullCordThemeBulbRadius * 0.55f, fill)
            fill.maskFilter = null
        }
        canvas.restore()

        if (bloom > 0.001f) {
            canvas.save()
            canvas.clipRect(-68f, 65f, 68f, 145f)
            fill.shader = RadialGradient(0f, 78f, 28f, intArrayOf(rgba(lamp, 0.32f * bloom), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 78f, 24f, fill)
            fill.shader = null
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawShell(canvas: Canvas, day: Boolean, alpha: Float) {
        if (alpha <= 0f) return
        val colours = if (day) {
            intArrayOf(0xFF4E7362.toInt(), 0xFF23402F.toInt(), 0xFF23402F.toInt(), 0xFF0E1B14.toInt())
        } else {
            intArrayOf(0xFF2A3A32.toInt(), 0xFF121A16.toInt(), 0xFF121A16.toInt(), 0xFF090D0B.toInt())
        }
        fill.shader = LinearGradient(-68f, 0f, 68f, 0f, colours, floatArrayOf(0f, 0.3f, 0.62f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawPath(dome, fill)
        fill.shader = LinearGradient(0f, 12f, 0f, 72f, intArrayOf(0x52000000, Color.TRANSPARENT, 0x10FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawPath(dome, fill)
        fill.shader = null
        fill.alpha = 255

        stroke.strokeWidth = 3f
        stroke.color = withAlpha(if (day) 0xFFFFE8C4.toInt() else 0xFFC4DCD0.toInt(), (if (day) 0.3f else 0.16f) * alpha)
        canvas.drawPath(domeSpec, stroke)

        fill.shader = LinearGradient(-7f, 0f, 7f, 0f, intArrayOf(colours[0], colours[1], colours[3]), null, Shader.TileMode.CLAMP)
        fill.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(RectF(-7f, 0f, 7f, 13f), 2f, 2f, fill)
        fill.shader = null
        fill.alpha = 255
    }

    private fun drawHousing(canvas: Canvas, day: Boolean, alpha: Float) {
        if (alpha <= 0f) return
        val colors = if (day) {
            intArrayOf(0xFF4A4844.toInt(), 0xFFE2D6C2.toInt(), 0xFF8E8A82.toInt(), 0xFF3E3C38.toInt())
        } else {
            intArrayOf(0xFF22211F.toInt(), 0xFF6E6A63.toInt(), 0xFF45423D.toInt(), 0xFF1C1B19.toInt())
        }
        val left = PullCordThemeSwitchX - 4.5f
        fill.shader = LinearGradient(left, 0f, left + 9f, 0f, colors, floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(RectF(left, 60f, left + 9f, 86f), 2.5f, 2.5f, fill)
        canvas.drawRoundRect(RectF(left - 0.6f, 82f, left + 9.6f, 86f), 1.6f, 1.6f, fill)
        fill.shader = null
        fill.color = withAlpha(Color.BLACK, 0.28f * alpha)
        repeat(4) { index -> canvas.drawRect(left, 66f + index * 2f, left + 9f, 66.8f + index * 2f, fill) }
        fill.color = withAlpha(Color.BLACK, 0.75f * alpha)
        canvas.drawOval(RectF(PullCordThemeSwitchX - 1.6f, 84.8f, PullCordThemeSwitchX + 1.6f, 86.4f), fill)
        fill.alpha = 255
    }

    private fun drawChain(canvas: Canvas, engine: PullCordThemeEngine) {
        drawNickel(canvas, engine, false, 1f)
        val lit = engine.front.coerceIn(0f, 1f) * (0.45f + 0.55f * engine.heat)
        drawNickel(canvas, engine, true, lit)
    }

    private fun drawNickel(canvas: Canvas, engine: PullCordThemeEngine, day: Boolean, alpha: Float) {
        if (alpha <= 0f) return
        val rim = if (day) 0xFF3F3D39.toInt() else 0xFF1F1E1C.toInt()
        val body = if (day) 0xFFA8A196.toInt() else 0xFF5E5B55.toInt()
        val spec = if (day) 0xFFFFF4DE.toInt() else 0xFFE8E2D8.toInt()
        val wire = if (day) 0xFF5C5A55.toInt() else 0xFF3A3935.toInt()
        val geometry = engine.geometry
        val radius = PullCordThemeBallRadius * geometry.scale
        val foot = switchFootForDraw(geometry, engine.angle)

        val wirePath = Path().apply {
            moveTo(foot.x, foot.y)
            lineTo(engine.x[0], engine.y[0])
            for (index in 1 until PullCordThemeBalls) lineTo(engine.x[index], engine.y[index])
        }
        stroke.strokeWidth = 0.6f * geometry.scale
        stroke.color = withAlpha(wire, alpha)
        canvas.drawPath(wirePath, stroke)

        val outDx = engine.x[0] - foot.x
        val outDy = engine.y[0] - foot.y
        val out = hypot(outDx, outDy)
        if (out > radius) {
            for (step in floor(out / geometry.segment).toInt() downTo 1) {
                val distance = out - step * geometry.segment
                if (distance > radius) drawBall(canvas, foot.x + outDx / out * distance, foot.y + outDy / out * distance, radius, rim, body, spec, alpha)
            }
            drawBall(canvas, engine.x[0], engine.y[0], radius, rim, body, spec, alpha)
        }
        for (index in 1 until PullCordThemeBalls) {
            drawBall(canvas, engine.x[index], engine.y[index], radius, rim, body, spec, alpha)
        }

        val last = PullCordThemeBalls - 1
        val dx = engine.x[last] - engine.x[last - 1]
        val dy = engine.y[last] - engine.y[last - 1]
        val angle = atan2(-dx, dy) * 180f / PI.toFloat()
        canvas.save()
        canvas.translate(engine.x[last], engine.y[last])
        canvas.rotate(angle)
        canvas.scale(geometry.scale, geometry.scale)
        canvas.translate(0f, PullCordThemeBallRadius)
        fill.shader = LinearGradient(
            -PullCordThemePullWidth / 2f,
            0f,
            PullCordThemePullWidth / 2f,
            0f,
            intArrayOf(rim, body, spec, body, rim),
            floatArrayOf(0f, 0.22f, 0.36f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        fill.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawPath(pullShape, fill)
        fill.shader = null
        fill.alpha = 255
        stroke.strokeWidth = 0.9f
        stroke.color = withAlpha(spec, alpha)
        canvas.drawPath(Path().apply { moveTo(-1.2f, 5f); cubicTo(-1.8f, 9f, -3f, 13f, -3f, 18f) }, stroke)
        canvas.restore()
    }

    private fun drawBall(canvas: Canvas, x: Float, y: Float, radius: Float, rim: Int, body: Int, spec: Int, alpha: Float) {
        fill.color = withAlpha(rim, alpha)
        canvas.drawCircle(x, y, radius, fill)
        fill.color = withAlpha(body, alpha)
        canvas.drawCircle(x - radius * 0.16f, y - radius * 0.18f, radius * 0.74f, fill)
        fill.color = withAlpha(spec, alpha)
        canvas.drawCircle(x - radius * 0.36f, y - radius * 0.4f, radius * 0.28f, fill)
    }
}

@TargetApi(33)
private class PullCordThemeAirShader {
    val shader = RuntimeShader(Source)

    fun update(engine: PullCordThemeEngine) {
        val geometry = engine.geometry
        val bulb = pullCordThemeLampPoint(geometry, engine.angle, 0f, PullCordThemeBulbY)
        val lamp = pullCordThemeLampColor(engine.heat)
        shader.setFloatUniform("uBulb", bulb.x, bulb.y)
        shader.setFloatUniform("uAxis", sin(engine.angle), cos(engine.angle))
        shader.setFloatUniform("uBright", pullCordThemeBrightness(engine.heat) * engine.filamentFlicker())
        shader.setFloatUniform("uLamp", lamp[0], lamp[1], lamp[2])
        shader.setFloatUniform("uTime", engine.clock % LOOP.toFloat())
        shader.setFloatUniform("uScale", geometry.scale)
    }

    companion object {
        private const val Source = """
uniform float2 uBulb;
uniform float2 uAxis;
uniform float uBright;
uniform float3 uLamp;
uniform float uTime;
uniform float uScale;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(float2 p) {
    float2 i = floor(p);
    float2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float a = hash12(i);
    float b = hash12(i + float2(1.0, 0.0));
    float c = hash12(i + float2(0.0, 1.0));
    float d = hash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float beam(float2 p) {
    float2 d = p - uBulb;
    float r = max(length(d), 0.001);
    float cone = smoothstep(0.3420201, 0.5591929, dot(d, uAxis) / r);
    float reach = 140.0 * uScale;
    float falloff = 1.0 / (1.0 + (r * r) / (reach * reach));
    return cone * falloff * smoothstep(12.0 * uScale, 44.0 * uScale, r);
}

half4 main(float2 p) {
    const float TAU = 6.2831853;
    float phase = TAU * uTime / 12.0;
    float amount = beam(p);
    float2 drift = float2(sin(phase), cos(phase)) * 0.7;
    float haze = amount * (0.7 + 0.6 * vnoise(p / (64.0 * uScale) + drift)) * 0.07;
    float2 side = float2(uAxis.y, -uAxis.x);
    float reference = max(beam(uBulb + uAxis * 60.0 * uScale), 0.001);
    float motes = 0.0;
    for (int i = 0; i < 12; i++) {
        float fi = float(i);
        float h1 = hash12(float2(fi, 1.7));
        float h2 = hash12(float2(fi, 9.3));
        float h3 = hash12(float2(fi, 4.1));
        float along = (40.0 + 230.0 * h1) * uScale;
        float across = (h2 - 0.5) * 1.6 * along;
        float2 centre = uBulb + uAxis * along + side * across;
        centre += float2(sin(phase * (1.0 + floor(h3 * 2.0)) + h1 * TAU), sin(phase + h2 * TAU)) * 6.0 * uScale;
        float size = mix(0.7, 1.3, h3) * uScale;
        float2 offset = p - centre;
        float glow = exp(-dot(offset, offset) / (size * size));
        float twinkle = 0.65 + 0.35 * sin(phase * (1.0 + floor(h2 * 3.0)) + h3 * TAU);
        motes += glow * twinkle * beam(centre) / reference;
    }
    float3 color = uLamp * uBright * (haze + 0.32 * min(motes, 1.0));
    color += (hash12(p * 1.37) - 0.5) / 255.0;
    color = max(color, float3(0.0));
    return half4(half3(color), half(max(color.r, max(color.g, color.b))));
}
"""
    }
}

private fun switchFootForDraw(geometry: PullCordThemeGeometry, angle: Float) = pullCordThemeLampPoint(
    geometry,
    angle,
    PullCordThemeSwitchX,
    PullCordThemeSwitchTop + PullCordThemeSwitchHeight,
)

@TargetApi(33)
private class PullCordThemePageShader {
    val shader = RuntimeShader(Source)

    fun update(engine: PullCordThemeEngine) {
        val geometry = engine.geometry
        val bulb = pullCordThemeLampPoint(geometry, engine.angle, 0f, PullCordThemeBulbY)
        val range = fieldRange(geometry)
        val lamp = pullCordThemeLampColor(engine.heat)
        val shadowPoints = FloatArray((PullCordThemeBalls + 1) * 2)
        for (index in 0 until PullCordThemeBalls) {
            shadowPoints[index * 2] = engine.x[index]
            shadowPoints[index * 2 + 1] = engine.y[index]
        }
        val last = PullCordThemeBalls - 1
        val dx = engine.x[last] - engine.x[last - 1]
        val dy = engine.y[last] - engine.y[last - 1]
        val length = hypot(dx, dy).coerceAtLeast(1f)
        val reach = (PullCordThemePullHeight + PullCordThemeBallRadius) * geometry.scale
        shadowPoints[last * 2 + 2] = engine.x[last] + dx / length * reach
        shadowPoints[last * 2 + 3] = engine.y[last] + dy / length * reach

        shader.setFloatUniform("uBulb", bulb.x, bulb.y)
        shader.setFloatUniform("uAxis", sin(engine.angle), cos(engine.angle))
        shader.setFloatUniform("uDepth", PullCordThemeDepth * geometry.scale)
        shader.setFloatUniform("uRange", range[0], range[1])
        shader.setFloatUniform("uFront", engine.front)
        shader.setFloatUniform("uBand", PullCordThemeFrontBand)
        shader.setFloatUniform("uFlat", if (engine.reducedMotion) 1f else 0f)
        shader.setFloatUniform("uBright", pullCordThemeBrightness(engine.heat) * engine.filamentFlicker())
        shader.setFloatUniform("uLamp", lamp[0], lamp[1], lamp[2])
        shader.setFloatUniform("uScale", geometry.scale)
        shader.setFloatUniform("uAdapt", engine.adaptation)
        shader.setFloatUniform("uChain", shadowPoints)
    }

    fun role(role: Float) {
        shader.setFloatUniform("uRole", role)
    }

    companion object {
        private const val Source = """
uniform float2 uBulb;
uniform float2 uAxis;
uniform float uDepth;
uniform float2 uRange;
uniform float uFront;
uniform float uBand;
uniform float uFlat;
uniform float uBright;
uniform float3 uLamp;
uniform float uScale;
uniform float uAdapt;
uniform float uRole;
uniform float2 uChain[26];

float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(float2 p) {
    float2 i = floor(p);
    float2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float a = hash12(i);
    float b = hash12(i + float2(1.0, 0.0));
    float c = hash12(i + float2(0.0, 1.0));
    float d = hash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float segDist(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h);
}
float2 project(float2 p) { return uBulb + (p - uBulb) * 2.0; }
float chainShadow(float2 p) {
    float d = 100000.0;
    for (int i = 0; i < 24; i++) {
        d = min(d, segDist(p, project(uChain[i]), project(uChain[i + 1])));
    }
    float tip = segDist(p, project(uChain[24]), project(uChain[25]));
    float w = 10.0 * uScale;
    return min(0.45 * exp(-(d * d) / (w * w)) + 0.7 * exp(-(tip * tip) / (w * w * 1.6)), 0.6);
}
float luminance(float3 c) {
    return dot(pow(clamp(c, 0.0, 1.0), float3(2.2)), float3(0.2126, 0.7152, 0.0722));
}
float contrast(float3 a, float3 b) {
    float x = luminance(a);
    float y = luminance(b);
    return (max(x, y) + 0.05) / (min(x, y) + 0.05);
}

half4 main(float2 p) {
    float2 delta = p - uBulb;
    float distance = sqrt(dot(delta, delta) + uDepth * uDepth);
    float k = uDepth / distance;
    float direct = smoothstep(0.2756374, 0.5150381, dot(delta, uAxis) / distance) * k * k * k;
    float d2 = 4.0 * uDepth * uDepth;
    float bounce = d2 / (dot(delta, delta) + d2);
    float energy = direct + 0.03 * bounce;
    float rank = 1.0 - clamp((log(energy) - uRange.x) / (uRange.y - uRange.x), 0.0, 1.0);
    float mixFront = clamp((uFront * (1.0 + uBand) - rank) / uBand, 0.0, 1.0);
    mixFront = mixFront * mixFront * (3.0 - 2.0 * mixFront);
    mixFront = mix(mixFront, uFront, uFlat);

    float lit = direct * (1.0 - chainShadow(p));
    float glow = pow(clamp(lit / 0.55, 0.0, 1.0), 0.45) * uBright;
    float2 q = (p - uBulb + float2(0.0, 30.0 * uScale)) / (float2(110.0, 60.0) * uScale);
    float shade = 1.0 - smoothstep(0.55, 1.45, length(q));
    float heat = pow(clamp(uBright, 0.0, 1.0), 0.25);
    float3 tint = uLamp / max(max(uLamp.r, uLamp.g), max(uLamp.b, 0.001));
    float room = (0.83 + 0.06 * bounce * (1.0 - 0.7 * shade)) * mix(0.5, 1.0, heat);
    float3 light = room * mix(float3(1.0), float3(1.0, 0.8, 0.58), 0.35 * (1.0 - heat)) + 0.15 * glow * tint;
    float lift = 1.0 + 0.35 * uAdapt;
    float3 nightPaper = float3(0.0705882, 0.0705882, 0.0666667) * lift + uLamp * glow * 0.5;
    float3 dayPaper = float3(0.9450980, 0.9215686, 0.8784314) * light;
    float3 paper = mix(nightPaper, dayPaper, mixFront);

    float3 color;
    if (uRole < 0.5) {
        color = paper + (vnoise(p * float2(0.7, 2.2) / uScale) - 0.5) * 0.016 + (hash12(floor(p * 3.0)) - 0.5) * 0.008;
    } else {
        float3 dayInk = float3(0.1529412, 0.1372549, 0.1176471) * light;
        float3 nightInk = float3(0.8470588, 0.8156863, 0.7607843) * lift + uLamp * glow * 0.12;
        bool useDay = contrast(dayInk, paper) >= contrast(nightInk, paper);
        float3 dayOwn = uRole < 1.5 ? float3(0.1529412, 0.1372549, 0.1176471) :
                        (uRole < 2.5 ? float3(0.4313725, 0.3960784, 0.3450980) : float3(0.1843137, 0.3647059, 0.2823529));
        float3 nightOwn = uRole < 1.5 ? float3(0.8470588, 0.8156863, 0.7607843) :
                          (uRole < 2.5 ? float3(0.5568627, 0.5294118, 0.4862745) : float3(0.5137255, 0.6901961, 0.5921569));
        float3 own = useDay ? dayOwn * light : nightOwn * lift + uLamp * glow * 0.12;
        float3 farColor;
        if (uRole > 2.5) {
            farColor = useDay ? own * 0.5 : mix(own, float3(1.0), 0.5);
        } else {
            farColor = useDay ? dayInk : nightInk;
        }
        color = mix(farColor, own, smoothstep(3.0, 4.2, contrast(own, paper)));
    }
    return half4(half3(max(color, float3(0.0))), 1.0);
}
"""
    }
}

private fun fieldRange(geometry: PullCordThemeGeometry): FloatArray {
    val bulb = pullCordThemeLampPoint(geometry, 0f, 0f, PullCordThemeBulbY)
    val depth = PullCordThemeDepth * geometry.scale
    var low = Float.POSITIVE_INFINITY
    var high = Float.NEGATIVE_INFINITY
    for (ix in 0..40) {
        for (iy in 0..80) {
            val value = ln(illuminance(geometry.width * ix / 40f, geometry.height * iy / 80f, bulb.x, bulb.y, depth))
            low = min(low, value)
            high = max(high, value)
        }
    }
    return floatArrayOf(low, high - 0.04f * (high - low))
}

private fun illuminance(px: Float, py: Float, bulbX: Float, bulbY: Float, depth: Float): Float {
    val dx = px - bulbX
    val dy = py - bulbY
    val distance = sqrt(dx * dx + dy * dy + depth * depth)
    val cosine = dy / distance
    val k = depth / distance
    val direct = smooth(0.2756374f, 0.5150381f, cosine) * k * k * k
    val d2 = 4f * depth * depth
    return direct + 0.03f * d2 / (dx * dx + dy * dy + d2)
}

private fun smooth(a: Float, b: Float, value: Float): Float {
    val t = ((value - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { index -> a[index] + (b[index] - a[index]) * t }

private fun rgba(color: FloatArray, alpha: Float): Int = Color.argb(
    (alpha.coerceIn(0f, 1f) * 255f).toInt(),
    (color[0].coerceIn(0f, 1f) * 255f).toInt(),
    (color[1].coerceIn(0f, 1f) * 255f).toInt(),
    (color[2].coerceIn(0f, 1f) * 255f).toInt(),
)

private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
    (alpha.coerceIn(0f, 1f) * 255f).toInt(),
    Color.red(color),
    Color.green(color),
    Color.blue(color),
)

private fun blend(from: Int, to: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )
}

private fun hash(x: Float, y: Float): Float {
    val value = sin(x * 12.9898f + y * 78.233f) * 43758.5453f
    return value - floor(value)
}
