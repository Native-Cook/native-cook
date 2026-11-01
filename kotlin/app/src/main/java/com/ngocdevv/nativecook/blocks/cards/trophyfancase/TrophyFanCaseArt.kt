package com.ngocdevv.nativecook.blocks.cards.trophyfancase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

internal const val CARD_W = 150f
internal const val CARD_H = 228f

internal data class TrophyFanCaseTrophy(
    val title: String,
    val time: String,
    val badge: String,
    val whenRun: String,
    val pace: String,
    val splits: FloatArray,
    val best: String,
    val delta: String,
)

internal val TrophyFanCaseTrophies = listOf(
    TrophyFanCaseTrophy(
        title = "Half marathon PR",
        time = "1:28:14",
        badge = "trophyfancase_badge-pr.png",
        whenRun = "Apr 12 · Riverside loop",
        pace = "4:11",
        splits = floatArrayOf(0.82f, 0.86f, 0.90f, 0.88f, 0.95f),
        best = "Previous best 1:31:02",
        delta = "−2:48",
    ),
    TrophyFanCaseTrophy(
        title = "3rd fastest 10 mile",
        time = "1:17:55",
        badge = "trophyfancase_badge-third.png",
        whenRun = "May 3 · Harbour 10",
        pace = "4:50",
        splits = floatArrayOf(0.88f, 0.84f, 0.86f, 0.80f, 0.90f),
        best = "Your fastest 1:14:20",
        delta = "+3:35",
    ),
    TrophyFanCaseTrophy(
        title = "2026 longest streak",
        time = "41 days",
        badge = "trophyfancase_badge-streak.png",
        whenRun = "Feb 2 – Mar 14",
        pace = "5:24",
        splits = floatArrayOf(0.60f, 0.72f, 0.68f, 0.80f, 0.76f),
        best = "Previous streak 27 days",
        delta = "+14",
    ),
    TrophyFanCaseTrophy(
        title = "2nd fastest 10K",
        time = "45:09",
        badge = "trophyfancase_badge-second.png",
        whenRun = "Jun 21 · Night 10K",
        pace = "4:30",
        splits = floatArrayOf(0.90f, 0.92f, 0.88f, 0.94f, 0.97f),
        best = "Your fastest 44:41",
        delta = "+0:28",
    ),
)

/** Static front and reverse faces. The only per-frame card work is transform, shade and alpha. */
internal class TrophyFanCaseArt(context: Context) {
    private val regular = loadTypeface(context, "trophyfancase_Archivo-Regular.ttf")
    private val medium = loadTypeface(context, "trophyfancase_Archivo-Medium.ttf")
    private val semiBold = loadTypeface(context, "trophyfancase_Archivo-SemiBold.ttf")
    private val frontFaces = Array(TrophyFanCaseTrophies.size) { index ->
        makeFront(TrophyFanCaseTrophies[index], loadBitmap(context, TrophyFanCaseTrophies[index].badge))
    }
    private val backFaces = Array(TrophyFanCaseTrophies.size) { index ->
        makeBack(TrophyFanCaseTrophies[index])
    }

    fun drawFace(canvas: Canvas, index: Int, back: Boolean) {
        canvas.drawPicture(if (back) backFaces[index] else frontFaces[index])
    }

    fun drawKicker(canvas: Canvas, width: Float, baseline: Float) {
        drawText(canvas, "TROPHY CASE", width / 2f, baseline, 13f, medium, 0xFFF2F2F2.toInt(), Paint.Align.CENTER, 1.6f / 13f)
    }

    fun drawFooter(canvas: Canvas, width: Float, baseline: Float) {
        drawText(canvas, "4 of 12 trophies · 2026 season", width / 2f, baseline, 13f, regular, 0xB3FFEBDC.toInt(), Paint.Align.CENTER)
    }

    private fun makeFront(trophy: TrophyFanCaseTrophy, badge: Bitmap): Picture {
        val picture = Picture()
        val canvas = picture.beginRecording(CARD_W.toInt(), CARD_H.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF15161A.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, CARD_W, CARD_H, 20f, 20f, paint)
        paint.color = 0x1AFFFFFF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(0.5f, 0.5f, CARD_W - 0.5f, CARD_H - 0.5f, 20f, 20f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.isFilterBitmap = true
        canvas.drawBitmap(badge, null, android.graphics.RectF(9f, 13f, 97f, 101f), paint)

        val lines = wrapTitle(trophy.title, semiBold, 14f, 122f)
        val titleBaseline = baselineForLine(153f, 18f, 14f, semiBold) - (lines.size - 1) * 18f
        lines.forEachIndexed { line, value ->
            drawText(canvas, value, 15f, titleBaseline + line * 18f, 14f, semiBold, 0xFFFFFFFF.toInt(), Paint.Align.LEFT)
        }
        drawText(canvas, trophy.time, 15f, baselineForLine(175f, 34f, 28f, medium), 28f, medium, 0xFFFFFFFF.toInt(), Paint.Align.LEFT, -0.5f / 28f, tabular = true)
        picture.endRecording()
        return picture
    }

    private fun makeBack(trophy: TrophyFanCaseTrophy): Picture {
        val picture = Picture()
        val canvas = picture.beginRecording(CARD_W.toInt(), CARD_H.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF1A1310.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, CARD_W, CARD_H, 20f, 20f, paint)
        paint.color = 0x59FF7832
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(0.5f, 0.5f, CARD_W - 0.5f, CARD_H - 0.5f, 20f, 20f, paint)
        paint.style = Paint.Style.FILL
        drawText(canvas, truncateToWidth(trophy.title, semiBold, 13f, 120f), 15f, baselineForLine(15f, 17f, 13f, semiBold), 13f, semiBold, 0xFFFFFFFF.toInt(), Paint.Align.LEFT)
        drawText(canvas, trophy.whenRun, 15f, baselineForLine(34f, 14f, 10f, regular), 10f, regular, 0x8CFFFFFF.toInt(), Paint.Align.LEFT)
        drawText(canvas, trophy.pace, 15f, baselineForLine(62f, 34f, 30f, medium), 30f, medium, 0xFFFFFFFF.toInt(), Paint.Align.LEFT, 0f, tabular = true)
        drawText(canvas, "/km", 18f + textWidth(trophy.pace, 30f, medium, true), baselineForLine(76f, 20f, 12f, regular), 12f, regular, 0x99FFFFFF.toInt(), Paint.Align.LEFT)

        val top = trophy.splits.maxOrNull() ?: 1f
        val barWidth = 20f
        val gap = 5f
        trophy.splits.forEachIndexed { index, split ->
            val height = 6f + 28f * split / top
            paint.color = if (split == top) 0xFFFF6A1F.toInt() else 0x38FFFFFF
            paint.style = Paint.Style.FILL
            val left = 15f + index * (barWidth + gap)
            canvas.drawRoundRect(left, 146f - height, left + barWidth, 146f, 3f, 3f, paint)
        }
        drawText(canvas, trophy.best, 15f, baselineForLine(160f, 14f, 10f, regular), 10f, regular, 0x99FFFFFF.toInt(), Paint.Align.LEFT)
        drawText(canvas, trophy.delta, 15f, baselineForLine(176f, 20f, 16f, semiBold), 16f, semiBold, 0xFFFF7A33.toInt(), Paint.Align.LEFT, 0f, tabular = true)
        picture.endRecording()
        return picture
    }

    private fun baselineForLine(top: Float, height: Float, size: Float, typeface: Typeface): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = size }
        val metrics = paint.fontMetrics
        return top + (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
    }

    private fun textWidth(value: String, size: Float, typeface: Typeface, tabular: Boolean = false): Float =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            if (tabular) fontFeatureSettings = "'tnum'"
        }.measureText(value)

    private fun wrapTitle(value: String, typeface: Typeface, size: Float, maxWidth: Float): List<String> {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
        }
        if (paint.measureText(value) <= maxWidth) return listOf(value)
        val words = value.split(' ')
        val first = StringBuilder()
        var second = ""
        for (word in words) {
            val candidate = if (first.isEmpty()) word else "$first $word"
            if (paint.measureText(candidate) <= maxWidth || first.isEmpty()) first.append(if (first.isEmpty()) word else " $word")
            else second = if (second.isEmpty()) word else "$second $word"
        }
        return listOf(first.toString(), second).filter { it.isNotEmpty() }
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
        align: Paint.Align,
        letterSpacing: Float = 0f,
        tabular: Boolean = false,
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = null
        paint.alpha = 255
        paint.color = color
        paint.typeface = typeface
        paint.textSize = size
        if (tabular) paint.fontFeatureSettings = "'tnum'"
        if (letterSpacing == 0f) {
            paint.textAlign = align
            canvas.drawText(value, x, baseline, paint)
        } else {
            val extra = size * letterSpacing
            val total = paint.measureText(value) + extra * max(0, value.length - 1)
            var cursor = when (align) {
                Paint.Align.CENTER -> x - total / 2f
                Paint.Align.RIGHT -> x - total
                else -> x
            }
            value.forEach { character ->
                val glyph = character.toString()
                canvas.drawText(glyph, cursor, baseline, paint)
                cursor += paint.measureText(glyph) + extra
            }
        }
    }

    private fun truncateToWidth(value: String, typeface: Typeface, size: Float, maxWidth: Float): String {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = size }
        if (paint.measureText(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) result = result.dropLast(1)
        return "$result…"
    }

    private fun loadTypeface(context: Context, name: String): Typeface =
        Typeface.createFromAsset(context.assets, "trophyfancase/$name")

    private fun loadBitmap(context: Context, name: String): Bitmap = context.assets.open("trophyfancase/$name").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })!!.apply { prepareToDraw() }
    }
}

private val BACKDROP_AGSL = """
uniform float2 size;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float2 uv = xy / size;
  float glow = exp(-pow(length((uv - float2(0.62, 1.08)) * float2(1.1, 1.6)), 2.0) * 2.4);
  float3 c = mix(float3(0.0), float3(0.22, 0.05, 0.01), smoothstep(0.3, 0.75, uv.y));
  c = mix(c, float3(0.86, 0.33, 0.07), glow * 0.9);
  c += float3(1.0, 0.75, 0.45) * pow(glow, 6.0) * 0.5;
  float g = hash(floor(xy * 1.0)) - 0.5;
  c += g * (0.025 + 0.05 * glow);
  return half4(half3(max(c, 0.0)), 1.0);
}
""".trimIndent()

/** RN background shader; older Android versions cache the same equation into a bitmap. */
internal class TrophyFanCaseBackdrop {
    private val runtime: RuntimeShader? = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { RuntimeShader(BACKDROP_AGSL) }.getOrNull()
    } else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fallback: Bitmap? = null

    fun draw(canvas: Canvas, width: Float, height: Float) {
        paint.shader = null
        paint.alpha = 255
        if (runtime != null) {
            runtime.setFloatUniform("size", width, height)
            paint.shader = runtime
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
            return
        }
        val w = kotlin.math.ceil(width).toInt().coerceAtLeast(1)
        val h = kotlin.math.ceil(height).toInt().coerceAtLeast(1)
        if (fallback?.width != w || fallback?.height != h) {
            val pixels = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val u = (x + 0.5) / width
                val v = (y + 0.5) / height
                val dx = (u - 0.62) * 1.1
                val dy = (v - 1.08) * 1.6
                val glow = kotlin.math.exp(-(dx * dx + dy * dy) * 2.4)
                val k = ((v - 0.3) / 0.45).coerceIn(0.0, 1.0)
                val ramp = k * k * (3.0 - 2.0 * k)
                val noise = sin(x * 12.9898 + y * 78.233) * 43758.5453
                val grain = (noise - kotlin.math.floor(noise) - 0.5) * (0.025 + 0.05 * glow)
                val hot = Math.pow(glow, 6.0) * 0.5
                fun channel(base: Double, ember: Double, highlight: Double): Int {
                    val value = base * ramp * (1.0 - glow * 0.9) + ember * glow * 0.9 + highlight * hot + grain
                    return (value.coerceIn(0.0, 1.0) * 255.0).toInt()
                }
                pixels[y * w + x] = android.graphics.Color.rgb(
                    channel(0.22, 0.86, 1.0), channel(0.05, 0.33, 0.75), channel(0.01, 0.07, 0.45),
                )
            }
            fallback = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
        paint.alpha = 255
        paint.isFilterBitmap = true
        fallback?.let { canvas.drawBitmap(it, null, android.graphics.RectF(0f, 0f, width, height), paint) }
    }
}

/** Shared card drawing: transforms are dynamic, static faces and badges are pictures. */
internal class TrophyFanCaseRenderer(context: Context) {
    private val art = TrophyFanCaseArt(context)
    private val backdrop = TrophyFanCaseBackdrop()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layer = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, width: Float, height: Float, safeTop: Float, safeBottom: Float, engine: TrophyFanCaseEngine) {
        backdrop.draw(canvas, width, height)
        art.drawKicker(canvas, width, safeTop + 76f)
        art.drawFooter(canvas, width, height - safeBottom - 31f)

        val top = engine.topCard()
        for (i in 0 until TROPHY_FAN_CASE_COUNT) {
            if (i != top) drawCard(canvas, i, engine)
        }
        if (top >= 0) drawCard(canvas, top, engine)
    }

    private fun drawCard(canvas: Canvas, index: Int, engine: TrophyFanCaseEngine) {
        val body = engine.hand.cards[index]
        val front = body.f < 90.0
        val radians = Math.PI / 180.0
        val flipRadians = body.f * radians
        val cosFlip = kotlin.math.cos(flipRadians)
        val sinFlip = kotlin.math.sin(flipRadians)
        val scaleX = (body.s * maxOf(0.02, abs(cosFlip))).toFloat()
        val scaleY = (body.s * (1.0 + 0.06 * abs(sinFlip))).toFloat()
        val skewYDegrees = kotlin.math.sin(body.f * radians * 2.0) * -6.0
        val skewXDegrees = (-body.vx * 0.006).coerceIn(-7.0, 7.0)
        val dim = if (engine.hand.sel >= 0 && engine.hand.sel != index) 0.55f else 1f

        canvas.save()
        canvas.translate(body.x.toFloat(), body.y.toFloat())
        canvas.rotate(body.r.toFloat())
        canvas.scale(scaleX, scaleY)
        // RN multiplies skewY then skewX; one combined skew omits their cross term.
        canvas.skew(0f, kotlin.math.tan(skewYDegrees * radians).toFloat())
        canvas.skew(kotlin.math.tan(skewXDegrees * radians).toFloat(), 0f)
        layer.shader = null
        layer.alpha = (dim * 255f).toInt().coerceIn(0, 255)
        canvas.saveLayer(
            android.graphics.RectF(-CARD_W / 2f - 32f, -CARD_H / 2f - 32f, CARD_W / 2f + 32f, CARD_H / 2f + 32f),
            layer,
        )
        shadow.shader = null
        shadow.style = Paint.Style.FILL
        shadow.color = 0xFF15161A.toInt()
        shadow.alpha = 255
        shadow.setShadowLayer(18f, 0f, 10f, 0x8C000000.toInt())
        canvas.drawRoundRect(-CARD_W / 2f, -CARD_H / 2f, CARD_W / 2f, CARD_H / 2f, 20f, 20f, shadow)
        shadow.clearShadowLayer()
        canvas.translate(-CARD_W / 2f, -CARD_H / 2f)
        art.drawFace(canvas, index, back = !front)

        val turnShade = abs(sinFlip) * 0.55
        val overlayAlpha = (turnShade * 255.0).toInt().coerceIn(0, 205)
        if (overlayAlpha > 0) {
            shade.shader = null
            shade.style = Paint.Style.FILL
            shade.color = 0xFF000000.toInt()
            shade.alpha = overlayAlpha
            canvas.drawRoundRect(0f, 0f, CARD_W, CARD_H, 20f, 20f, shade)
        }
        canvas.restore()
        canvas.restore()
    }
}

internal const val TROPHY_FAN_CASE_COUNT = 4

