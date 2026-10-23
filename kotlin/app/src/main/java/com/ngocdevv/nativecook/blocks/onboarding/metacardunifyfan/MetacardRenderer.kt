package com.ngocdevv.nativecook.blocks.onboarding.metacardunifyfan

import android.content.Context
import android.graphics.*
import android.os.Build
import java.io.File
import kotlin.math.*

internal class MetacardRenderer(context: Context) {
    private val faces = Array(8) { bitmap("card-$it.png") }
    private val chip = bitmap("hero-chip.png")
    private val shadow = bitmap("card-shadow.png")
    private val bold = font(context, "Inter-Bold.ttf")
    private val regular = font(context, "Inter-Regular.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 244, 242) }
    private val mark = makeMark()
    private val gpu = if (Build.VERSION.SDK_INT >= 33) MetacardGpu(faces, shadow, chip) else null

    fun draw(canvas: Canvas, width: Float, height: Float, motion: MetacardMotion) {
        val k = min(width / 402f, height / 874f)
        val x = (width - 402 * k) / 2
        val y = (height - 874 * k) / 2
        canvas.drawColor(Color.BLACK)
        gpu?.drawPage(canvas, motion, width, height, x, y, k)
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(k, k)
        if (gpu != null) gpu.draw(canvas, motion) else fallback(canvas, motion.u)
        drawText(canvas, motion.u, motion.reduced || gpu == null)
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, u: Double, reduced: Boolean) {
        val t = u * 1900
        val out = MetacardTimeline.ease(MetacardTimeline.phase(t, 60.0, 300.0))
        val title = MetacardTimeline.ease(MetacardTimeline.phase(t, 1430.0, 1880.0))
        val word = MetacardTimeline.ease(MetacardTimeline.phase(t, 1350.0, 1800.0))
        val hintOut = MetacardTimeline.ease(MetacardTimeline.phase(t, 100.0, 340.0))
        val hintIn = MetacardTimeline.ease(MetacardTimeline.phase(t, 1470.0, 1920.0))
        val fanAlpha = if (reduced) 1 - u else 1 - out
        val heroAlpha = if (reduced) u else title
        line(
            canvas,
            "Unify the limit of",
            616.0 + (if (reduced) 0.0 else -6 * out),
            28f,
            -.4f,
            fanAlpha,
            if (reduced) 0.0 else 6 * out,
        )
        line(
            canvas,
            "all your cards",
            650.0 + (if (reduced) 0.0 else -6 * out),
            28f,
            -.4f,
            fanAlpha,
            if (reduced) 0.0 else 6 * out,
        )
        line(
            canvas,
            "Meet the first",
            326.0 + (if (reduced) 0.0 else 10 * (1 - title)),
            28f,
            -.4f,
            heroAlpha,
            if (reduced) 0.0 else 6 * (1 - title),
        )
        line(
            canvas,
            "Metacard",
            360.0 + (if (reduced) 0.0 else 10 * (1 - title)),
            28f,
            -.4f,
            heroAlpha,
            if (reduced) 0.0 else 6 * (1 - title),
        )
        line(
            canvas,
            "onefold",
            256.0 + (if (reduced) 0.0 else 10 * (1 - word)),
            24f,
            -.6f,
            if (reduced) u else word,
            if (reduced) 0.0 else 6 * (1 - word),
            22f,
            8f,
        )
        line(
            canvas,
            "Tap the hub to unify",
            806.0 + (if (reduced) 0.0 else -6 * hintOut),
            13f,
            .2f,
            .46 * (if (reduced) 1 - u else 1 - hintOut),
            if (reduced) 0.0 else 6 * hintOut,
            10f,
            8f,
        )
        line(
            canvas,
            "Tap the card to split",
            806.0 + (if (reduced) 0.0 else 10 * (1 - hintIn)),
            13f,
            .2f,
            .46 * (if (reduced) u else hintIn),
            if (reduced) 0.0 else 6 * (1 - hintIn),
            10f,
            8f,
        )
    }

    private fun line(
        canvas: Canvas,
        text: String,
        y: Double,
        size: Float,
        tracking: Float,
        alpha: Double,
        blur: Double,
        logoSize: Float = 0f,
        gap: Float = 0f,
    ) {
        if (alpha < .001) return
        ink.typeface = if (size == 13f) regular else bold
        ink.textSize = size
        ink.letterSpacing = tracking / size
        ink.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        ink.maskFilter =
            if (blur > .05)
                BlurMaskFilter(
                    ((blur - .5) / .57735).coerceAtLeast(.01).toFloat(),
                    BlurMaskFilter.Blur.NORMAL,
                )
            else null
        val width = ink.measureText(text)
        val left = (402 - width - logoSize - gap) / 2
        if (logoSize > 0) {
            canvas.save()
            canvas.translate(left + logoSize / 2, y.toFloat() + if (size == 24f) 1f else 0f)
            canvas.scale(logoSize / 24, logoSize / 24)
            canvas.drawPath(mark, ink)
            canvas.restore()
        }
        val baseline = y.toFloat() - (ink.fontMetrics.ascent + ink.fontMetrics.descent) / 2
        canvas.drawText(text, left + logoSize + gap, baseline, ink)
        ink.maskFilter = null
    }

    // Older Android renders the two endpoint designs without a shader or a simulated 3D lift.
    private fun fallback(canvas: Canvas, u: Double) {
        canvas.drawColor(Color.rgb((16 * u).toInt(), (16 * u).toInt(), (17 * u).toInt()))
        val layer = canvas.saveLayerAlpha(0f, 0f, 402f, 874f, ((1 - u) * 255).toInt())
        for (i in 0..7) {
            val p = MetacardTimeline.fanCardPose(i, -49.0, 0.0)
            canvas.save()
            canvas.translate(p.x.toFloat(), p.y.toFloat())
            canvas.rotate(p.rotation.toFloat())
            canvas.drawBitmap(faces[i], null, RectF(-72f, -45.5f, 72f, 45.5f), paint)
            canvas.restore()
        }
        paint.shader =
            LinearGradient(
                140f,
                290f,
                260f,
                410f,
                intArrayOf(0xff37373b.toInt(), 0xff050506.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        val hub = Path()
        for (i in 0..7) {
            val a = (22.5 + 45 * i) * PI / 180
            val px = 201 + 67 * cos(a).toFloat()
            val py = 356 + 67 * sin(a).toFloat()
            if (i == 0) hub.moveTo(px, py) else hub.lineTo(px, py)
        }
        hub.close()
        canvas.drawPath(hub, paint)
        paint.shader = null
        ink.alpha = 255
        canvas.save()
        canvas.translate(201f, 356f)
        canvas.scale(34f / 24, 34f / 24)
        canvas.drawPath(mark, ink)
        canvas.restore()
        canvas.restoreToCount(layer)
        val hero = canvas.saveLayerAlpha(0f, 0f, 402f, 874f, (u * 255).toInt())
        canvas.save()
        canvas.translate(201f, 522f)
        canvas.skew(-.10f, -.09f)
        paint.shader =
            LinearGradient(
                -100f,
                -95f,
                40f,
                95f,
                intArrayOf(0xff333338.toInt(), 0xff08080a.toInt(), 0xff020203.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(-137f, -91f, 137f, 91f, 13f, 13f, paint)
        paint.shader = null
        canvas.drawBitmap(chip, null, RectF(-112f, -38f, -40f, 0f), paint)
        canvas.translate(98f, -51f)
        canvas.scale(1.5f, 1.5f)
        canvas.drawPath(mark, ink)
        canvas.restore()
        canvas.restoreToCount(hero)
    }

    private fun bitmap(name: String): Bitmap {
        val bytes = MetacardAssetData.bytes(name)
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun font(context: Context, name: String): Typeface {
        val file = File(context.cacheDir, "metacard-$name")
        if (!file.exists()) file.writeBytes(MetacardAssetData.bytes(name))
        return Typeface.createFromFile(file)
    }

    private fun makeMark(): Path {
        val a = 10.5
        val b = a * tan(PI / 8)
        val points = arrayOf(-b to -a, b to -a, a to -b, a to b, b to a, -b to a, -a to b, -a to -b)
        val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (i in 0..7) {
            val p = points[i]
            val before = points[(i + 7) % 8]
            val after = points[(i + 1) % 8]
            val trim = tan(PI / 8)
            val bx =
                p.first +
                    (before.first - p.first) /
                        hypot(before.first - p.first, before.second - p.second) * trim
            val by =
                p.second +
                    (before.second - p.second) /
                        hypot(before.first - p.first, before.second - p.second) * trim
            val ax =
                p.first +
                    (after.first - p.first) /
                        hypot(after.first - p.first, after.second - p.second) * trim
            val ay =
                p.second +
                    (after.second - p.second) /
                        hypot(after.first - p.first, after.second - p.second) * trim
            if (i == 0) path.moveTo(bx.toFloat(), by.toFloat())
            else path.lineTo(bx.toFloat(), by.toFloat())
            path.quadTo(p.first.toFloat(), p.second.toFloat(), ax.toFloat(), ay.toFloat())
        }
        path.close()
        val slot =
            Path().apply {
                addRoundRect(-6.25f, -3.95f, 6.25f, 3.95f, 1.5f, 1.5f, Path.Direction.CW)
            }
        slot.transform(Matrix().apply { setRotate(-22f) })
        path.addPath(slot)
        return path
    }
}
