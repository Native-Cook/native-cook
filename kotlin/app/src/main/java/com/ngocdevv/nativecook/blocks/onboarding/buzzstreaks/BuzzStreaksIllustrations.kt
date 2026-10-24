package com.ngocdevv.nativecook.blocks.onboarding.buzzstreaks

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Source-traced prize glyphs and card-local flame paths, cached before playback. */
internal class BuzzStreaksIllustrations(assets: AssetManager) {
    private val prize: Array<Layer>
    private val bonusFrames: Array<BonusFrame>
    private val coin: FloatArray
    private val coinPath: Path
    private val glyphPath: Path
    private val haloRect = RectF(67f, 90f, 143f, 176f)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coinEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shineMatrix = Matrix()
    private val shineShader = BuzzStreaksTextMotion.shineShader(190f, 0.42f)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = shineShader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    init {
        val scene = JSONObject(assets.open("buzzstreaks/buzzstreaks_illustrations.json").bufferedReader().use { it.readText() })
        val layers = scene.getJSONObject("prize").getJSONArray("layers")
        prize = Array(layers.length()) { index ->
            val layer = layers.getJSONObject(index)
            val path = cubicPath(layer.getJSONArray("curves"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val gradient = layer.optJSONObject("gradient")
            if (gradient != null) {
                val start = gradient.getJSONArray("start")
                val end = gradient.getJSONArray("end")
                val colors = gradient.getJSONArray("colors")
                paint.shader = LinearGradient(start.getDouble(0).toFloat(), start.getDouble(1).toFloat(),
                    end.getDouble(0).toFloat(), end.getDouble(1).toFloat(),
                    rgb(colors.getJSONArray(0)), rgb(colors.getJSONArray(1)), Shader.TileMode.CLAMP)
            } else {
                paint.color = rgb(layer.getJSONArray("fillRGB"))
            }
            Layer(path, paint)
        }
        val bonus = scene.getJSONObject("bonus")
        glyphPath = cubicPath(bonus.getJSONArray("glyphCurves"))
        val frames = bonus.getJSONArray("frames")
        bonusFrames = Array(frames.length()) { index ->
            val frame = frames.getJSONObject(index)
            BonusFrame(frame.getBoolean("visible"), softPath(frame.getJSONArray("flame")), softPath(frame.getJSONArray("white")))
        }
        val colors = bonus.getJSONObject("colors")
        coin = floats(bonus.getJSONArray("coin"))
        haloPaint.color = rgb(colors.getJSONArray("halo"))
        haloPaint.alpha = (0.18f * 255).roundToInt()
        flamePaint.shader = LinearGradient(110f, 75f, 125f, 158f,
            rgb(colors.getJSONArray("flameTop")), rgb(colors.getJSONArray("flameBottom")), Shader.TileMode.CLAMP)
        whitePaint.color = rgb(colors.getJSONArray("white"))
        coinPath = Path().apply { addOval(-coin[2], -coin[3], coin[2], coin[3], Path.Direction.CW) }
        coinPaint.shader = LinearGradient(0f, -coin[3], 0f, coin[3],
            rgb(colors.getJSONArray("coinTop")), rgb(colors.getJSONArray("coinBottom")), Shader.TileMode.CLAMP)
        coinEdge.color = rgb(colors.getJSONArray("coinTop"))
        coinEdge.alpha = (0.65f * 255).roundToInt()
        glyphPaint.color = rgb(colors.getJSONArray("glyph"))
    }

    fun drawPrize(time: Double, enhanced: Boolean, canvas: Canvas) {
        for (layer in prize) canvas.drawPath(layer.path, layer.paint)
        if (!enhanced) return
        val center = BuzzStreaksTextMotion.sweepCenter(time, 0.45)
        if (center.isNaN()) return
        shineMatrix.setTranslate(center * 190f, 0f)
        shineShader.setLocalMatrix(shineMatrix)
        val saved = canvas.save()
        canvas.clipPath(prize[3].path)
        canvas.drawRect(0f, 0f, 190f, 99f, shinePaint)
        canvas.restoreToCount(saved)
    }

    fun drawBonus(time: Double, canvas: Canvas) {
        val frame = bonusFrames[(time * 60 + 1e-7).toInt().coerceIn(0, bonusFrames.lastIndex)]
        if (!frame.visible) return
        canvas.drawOval(haloRect, haloPaint)
        canvas.drawPath(frame.flame, flamePaint)
        canvas.drawPath(frame.white, whitePaint)
        val saved = canvas.save()
        canvas.translate(coin[0], coin[1])
        canvas.rotate(coin[4])
        canvas.drawPath(coinPath, coinPaint)
        canvas.drawPath(coinPath, coinEdge)
        canvas.restoreToCount(saved)
        canvas.drawPath(glyphPath, glyphPaint)
    }

    private class Layer(val path: Path, val paint: Paint)
    private class BonusFrame(val visible: Boolean, val flame: Path, val white: Path)

    private companion object {
        fun cubicPath(contours: JSONArray): Path {
            val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
            for (index in 0 until contours.length()) {
                val points = contours.getJSONArray(index)
                path.moveTo(points.getDouble(0).toFloat(), points.getDouble(1).toFloat())
                var vertex = 2
                while (vertex < points.length()) {
                    path.cubicTo(points.getDouble(vertex).toFloat(), points.getDouble(vertex + 1).toFloat(),
                        points.getDouble(vertex + 2).toFloat(), points.getDouble(vertex + 3).toFloat(),
                        points.getDouble(vertex + 4).toFloat(), points.getDouble(vertex + 5).toFloat())
                    vertex += 6
                }
                path.close()
            }
            return path
        }

        fun floats(values: JSONArray): FloatArray {
            return FloatArray(values.length()) { values.getDouble(it).toFloat() }
        }

        fun rgb(values: JSONArray): Int {
            return Color.rgb(values.getDouble(0).roundToInt().coerceIn(0, 255),
                values.getDouble(1).roundToInt().coerceIn(0, 255), values.getDouble(2).roundToInt().coerceIn(0, 255))
        }

        fun softPath(points: JSONArray): Path {
            val path = Path()
            if (points.length() < 3) return path
            val first = points.getJSONArray(0)
            val last = points.getJSONArray(points.length() - 1)
            path.moveTo(((last.getDouble(0) + first.getDouble(0)) / 2).toFloat(),
                ((last.getDouble(1) + first.getDouble(1)) / 2).toFloat())
            for (index in 0 until points.length()) {
                val point = points.getJSONArray(index)
                val next = points.getJSONArray((index + 1) % points.length())
                path.quadTo(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(),
                    ((point.getDouble(0) + next.getDouble(0)) / 2).toFloat(),
                    ((point.getDouble(1) + next.getDouble(1)) / 2).toFloat())
            }
            path.close()
            return path
        }
    }
}
