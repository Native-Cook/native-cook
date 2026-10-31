package com.ngocdevv.nativecook.blocks.forms.saladgaugebuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ART_BOWL = 270f
private const val ART_INNER = 150f
private const val ART_OUTER = 168f
private const val ART_E = 195.0 * PI / 180.0
private const val ART_F = 345.0 * PI / 180.0
private const val ART_INK = 0xFF2B1838.toInt()
private const val ART_GREEN = 0xFF33C27A.toInt()

private data class SaladGaugeBuilderArtItem(
    val name: String,
    val kcal: Int,
    val angle: Double,
    val radius: Float,
    val size: Float,
)

private val ART_ITEMS = listOf(
    SaladGaugeBuilderArtItem("tomato", 30, PI, 52f, 112f),
    SaladGaugeBuilderArtItem("salmon", 210, 1.5 * PI, 54f, 112f),
    SaladGaugeBuilderArtItem("greens", 20, 0.0, 52f, 122f),
    SaladGaugeBuilderArtItem("egg", 80, 0.5 * PI, 54f, 100f),
    SaladGaugeBuilderArtItem("avocado", 160, 0.0, 0f, 96f),
)

private fun artClamp(value: Double) = value.coerceIn(0.0, 1.0)

private fun artEaseOutBack(value: Double): Double {
    val shifted = value - 1.0
    return 1.0 + 2.7 * shifted.pow(3) + 1.7 * shifted.pow(2)
}

/** Decoded artwork and paints are retained; [draw] receives a canvas whose coordinates are dp. */
internal class SaladGaugeBuilderArt(private val context: Context, density: Float) {
    val typeface: Typeface = Typeface.createFromAsset(
        context.assets,
        "saladgaugebuilder/saladgaugebuilder_ArchivoExpanded-Black.ttf",
    )

    private fun image(name: String): Bitmap = context.assets.open(
        "saladgaugebuilder/saladgaugebuilder_$name.png",
    ).use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }))
    }

    private val bowl = image("bowl")
    private val foods = ART_ITEMS.map { image(it.name) }
    private val destination = RectF()
    private val textBounds = Rect()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 255
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = this@SaladGaugeBuilderArt.typeface
        textSize = 100f
        color = ART_INK
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 255
        shader = RadialGradient(
            0f,
            0f,
            ART_BOWL * 0.56f,
            intArrayOf(Color.argb(89, 90, 40, 110), Color.argb(0, 90, 40, 110)),
            floatArrayOf(0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    // A software bitmap preserves the solid Skia blur on hardware-accelerated Android canvases.
    private val glowRadius = 18f + 4f * 14f
    private val glow = run {
        val side = ceil(glowRadius * 2f * density).toInt().coerceAtLeast(1)
        val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ART_GREEN
            // Android's radius maps to Skia sigma as 0.57735 * radius + 0.5.
            maskFilter = BlurMaskFilter(
                ((14f * density - 0.5f) / 0.57735f).coerceAtLeast(0.01f),
                BlurMaskFilter.Blur.SOLID,
            )
        }
        Canvas(result).drawCircle(side / 2f, side / 2f, 18f * density, paint)
        result
    }

    fun draw(canvas: Canvas, width: Float, height: Float, amounts: List<Double>, needle: Double) {
        val cx = width / 2f
        val cy = height * 0.47f

        for (k in 0 until 11) {
            val angle = ART_E + (ART_F - ART_E) * k / 10.0
            val major = k % 5 == 0
            val inner = if (major) ART_INNER - 6f else ART_INNER
            strokePaint.color = if (k == 0) 0xFFFF4D6D.toInt() else Color.WHITE
            strokePaint.alpha = 255
            strokePaint.strokeWidth = if (major) 7f else 5f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * ART_OUTER,
                cy + sin(angle).toFloat() * ART_OUTER,
                strokePaint,
            )
        }

        val full = artClamp((needle - 0.9) / 0.1)
        drawLabel(
            canvas,
            "E",
            cx + cos(ART_E).toFloat() * (ART_OUTER - 6f) - 8f,
            cy + sin(ART_E).toFloat() * (ART_OUTER - 6f) + 34f,
            Color.WHITE,
        )
        drawLabel(
            canvas,
            "F",
            cx + cos(ART_F).toFloat() * (ART_OUTER - 6f) - 6f,
            cy + sin(ART_F).toFloat() * (ART_OUTER - 6f) + 34f,
            if (full > 0.5) ART_GREEN else Color.WHITE,
        )
        if (full > 0.0) {
            val x = cx + cos(ART_F).toFloat() * ART_OUTER
            val y = cy + sin(ART_F).toFloat() * ART_OUTER
            destination.set(x - glowRadius, y - glowRadius, x + glowRadius, y + glowRadius)
            bitmapPaint.alpha = (255.0 * 0.45 * full).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(glow, null, destination, bitmapPaint)
        }

        canvas.save()
        canvas.translate(cx - 26f, cy + 30f)
        canvas.drawCircle(0f, 0f, ART_BOWL * 0.56f, shadowPaint)
        canvas.restore()
        destination.set(cx - ART_BOWL / 2f, cy - ART_BOWL / 2f, cx + ART_BOWL / 2f, cy + ART_BOWL / 2f)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(bowl, null, destination, bitmapPaint)

        ART_ITEMS.forEachIndexed { index, item ->
            val amount = amounts[index]
            if (amount == 0.0) return@forEachIndexed
            val scale: Double
            val alpha: Double
            if (amount > 0.0) {
                scale = 1.0 + 0.35 * (1.0 - artEaseOutBack(amount))
                alpha = artClamp(amount * 3.0)
            } else {
                scale = 1.0 + 0.15 * (1.0 + amount)
                alpha = -amount
            }
            val x = cx + cos(item.angle).toFloat() * item.radius
            val y = cy + sin(item.angle).toFloat() * item.radius
            val half = (item.size * scale / 2.0).toFloat()
            destination.set(x - half, y - half, x + half, y + half)
            bitmapPaint.alpha = (255.0 * artClamp(alpha)).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(foods[index], null, destination, bitmapPaint)
        }

        val angle = ART_E + (ART_F - ART_E) * needle.coerceIn(-0.03, 1.04)
        val innerX = cx + cos(angle).toFloat() * (ART_INNER - 22f)
        val innerY = cy + sin(angle).toFloat() * (ART_INNER - 22f)
        strokePaint.color = ART_GREEN
        strokePaint.alpha = 255
        strokePaint.strokeWidth = 5f
        canvas.drawLine(
            innerX,
            innerY,
            cx + cos(angle).toFloat() * (ART_OUTER + 8f),
            cy + sin(angle).toFloat() * (ART_OUTER + 8f),
            strokePaint,
        )
        fillPaint.color = ART_GREEN
        fillPaint.alpha = 255
        canvas.drawCircle(innerX, innerY, 4f, fillPaint)

        val kcal = ART_ITEMS.indices.sumOf { index ->
            val amount = amounts[index]
            ART_ITEMS[index].kcal * if (amount > 0.0) artClamp(amount * 2.0) else -amount
        }
        val label = "${kcal.roundToInt()} kcal"
        textPaint.color = ART_INK
        textPaint.alpha = 255
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val textWidth = textBounds.width() * 0.3f
        canvas.save()
        canvas.translate(cx - textWidth / 2f, cy + ART_BOWL / 2f + 62f)
        canvas.scale(0.3f, 0.3f)
        canvas.drawText(label, 0f, 0f, textPaint)
        canvas.restore()
    }

    private fun drawLabel(canvas: Canvas, label: String, x: Float, y: Float, color: Int) {
        textPaint.color = color
        textPaint.alpha = 255
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(0.2f, 0.2f)
        canvas.drawText(label, 0f, 0f, textPaint)
        canvas.restore()
    }
}
