package com.ngocdevv.nativecook.blocks.onboarding.buzzstreaks

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/** Six independent reward flights, source wave contours, and native Gilroy badges. */
internal class BuzzStreaksRewards(assets: AssetManager) {
    private val coins: Array<Coin>
    private val badges: Array<Badge>
    private val waves: Array<Array<Wave>>
    private val glow: Array<FloatArray>
    private val glowValue = FloatArray(4)
    private val glowMatrix = Matrix()
    private val glowShader: RadialGradient
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val badgeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val strokeGlowOpacity: Float
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 1f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.createFromAsset(assets, "buzzstreaks/buzzstreaks_Gilroy-ExtraBold.ttf")
    }
    private val textBaselineUnit = badgeText.fontMetrics.let { -(it.ascent + it.descent) / 2f }
    private val destination = RectF()

    init {
        val scene = JSONObject(assets.open("buzzstreaks/buzzstreaks_effects.json").bufferedReader().use { it.readText() })
        val metadata = scene.getJSONObject("images")
        val images = metadata.keys().asSequence().associateWith { name ->
            val image = assets.open("buzzstreaks/buzzstreaks_$name.png").use { stream ->
                requireNotNull(BitmapFactory.decodeStream(stream)) { "Cannot decode the $name reward coin" }
            }
            val item = metadata.getJSONObject(name)
            Sprite(image, floats(item.getJSONArray("size")), floats(item.getJSONArray("rect")))
        }
        val coinData = scene.getJSONArray("coins")
        coins = Array(coinData.length()) { index ->
            val coin = coinData.getJSONObject(index)
            Coin(coin.getString("id"), images.getValue(coin.getString("image")), track(coin.getJSONArray("frames")))
        }
        val badgeData = scene.getJSONArray("badges")
        badges = Array(badgeData.length()) { index ->
            val badge = badgeData.getJSONObject(index)
            val strokeFrames = badge.getJSONArray("strokes")
            val strokes = Array(strokeFrames.length()) { frame ->
                val contours = strokeFrames.getJSONArray(frame)
                Array(contours.length()) { contourIndex ->
                    val contour = contours.getJSONObject(contourIndex)
                    Wave(contourPath(contour.getJSONArray("points"), false), alpha(contour.getDouble("opacity").toFloat()))
                }
            }
            Badge(badge.getString("id"), badge.getString("text"), track(badge.getJSONArray("frames")),
                track(badge.getJSONArray("appearance")), badge.getDouble("fontSize").toFloat(),
                rgb(badge.getJSONArray("fillRGB")), badge.getDouble("fillOpacity").toFloat(), strokes)
        }
        val strokeStyle = scene.getJSONObject("strokeStyle")
        badgeEdge.color = rgb(strokeStyle.getJSONArray("rgb"))
        badgeGlow.color = badgeEdge.color
        badgeEdge.strokeWidth = strokeStyle.getDouble("width").toFloat()
        badgeGlow.strokeWidth = badgeEdge.strokeWidth
        badgeGlow.maskFilter = BlurMaskFilter(strokeStyle.getDouble("glowRadius").toFloat(), BlurMaskFilter.Blur.NORMAL)
        strokeGlowOpacity = strokeStyle.getDouble("glowOpacity").toFloat()
        val waveStyle = scene.getJSONObject("waveStyle")
        wavePaint.strokeWidth = waveStyle.getDouble("width").toFloat()
        wavePaint.maskFilter = BlurMaskFilter(waveStyle.getDouble("blur").toFloat(), BlurMaskFilter.Blur.NORMAL)
        val waveAlphaGain = waveStyle.getDouble("alphaGain").toFloat()
        val waveData = scene.getJSONArray("waves")
        waves = Array(waveData.length()) { frame ->
            val contours = waveData.getJSONArray(frame)
            Array(contours.length()) { index ->
                val contour = contours.getJSONObject(index)
                val points = contour.getJSONArray("points")
                val path = Path()
                for (vertex in 0 until points.length()) {
                    val point = points.getJSONArray(vertex)
                    val x = point.getDouble(0).toFloat()
                    val y = point.getDouble(1).toFloat()
                    if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (contour.getBoolean("closed")) path.close()
                Wave(path, alpha(contour.getDouble("opacity").toFloat() * waveAlphaGain))
            }
        }
        val glowData = scene.getJSONObject("glow")
        val rgb = glowData.getJSONArray("rgb")
        val color = Color.rgb(rgb.getInt(0), rgb.getInt(1), rgb.getInt(2))
        glow = track(glowData.getJSONArray("frames"))
        glowShader = RadialGradient(0f, 0f, 1f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        glowPaint.shader = glowShader
    }

    fun drawBackground(time: Double, canvas: Canvas) {
        sample(glow, glowValue, time)
        if (glowValue[3] > 0f && glowValue[2] > 0f) {
            glowMatrix.setScale(glowValue[2], glowValue[2])
            glowMatrix.postTranslate(glowValue[0], glowValue[1])
            glowShader.setLocalMatrix(glowMatrix)
            glowPaint.alpha = alpha(glowValue[3])
            canvas.drawCircle(glowValue[0], glowValue[1], glowValue[2], glowPaint)
        }
        val frame = (time * 60 + 1e-7).toInt().coerceIn(0, waves.lastIndex)
        for (wave in waves[frame]) {
            wavePaint.alpha = wave.alpha
            canvas.drawPath(wave.path, wavePaint)
        }
    }

    fun drawForeground(time: Double, canvas: Canvas, signature: BuzzStreaksSignature? = null) {
        for (coin in coins) {
            sample(coin.frames, coin.value, time)
            val value = coin.value
            signature?.adjustCoin(coin.id, time, value)
            val radius = value[2]
            if (value[3] <= 0.001f || radius <= 0.01f) continue
            val sprite = coin.sprite
            val sx = 2f * radius / sprite.rect[2]
            val sy = 2f * radius / sprite.rect[3]
            val left = value[0] - radius - sprite.rect[0] * sx
            val top = value[1] - radius - sprite.rect[1] * sy
            destination.set(left, top, left + sprite.size[0] * sx, top + sprite.size[1] * sy)
            bitmapPaint.alpha = alpha(value[3])
            canvas.drawBitmap(sprite.image, null, destination, bitmapPaint)
            signature?.drawCoinGloss(coin.id, time, value[0], value[1], radius, value[3], canvas)
        }
        for (badge in badges) {
            sample(badge.frames, badge.value, time)
            sample(badge.appearance, badge.shape, time)
            val value = badge.value
            val shape = badge.shape
            val gate = signature?.badgeGate(badge.id, time) ?: 1f
            value[2] *= gate
            if (value[2] > 0.001f) {
                destination.set(value[0] - shape[1] / 2f, value[1] - shape[2] / 2f,
                    value[0] + shape[1] / 2f, value[1] + shape[2] / 2f)
                badgeFill.color = badge.fillColor
                badgeFill.alpha = alpha(value[2] * badge.fillOpacity)
                badgeText.alpha = alpha(value[2])
                badgeText.textSize = badge.fontSize * shape[0]
                canvas.drawRoundRect(destination, shape[3], shape[3], badgeFill)
            }
            // The measured open cyan strokes can precede the badge's fill and text.
            val frame = (time * 60 + 1e-7).toInt().coerceIn(0, badge.strokes.lastIndex)
            for (stroke in badge.strokes[frame]) {
                badgeGlow.alpha = (stroke.alpha * strokeGlowOpacity * gate).roundToInt().coerceIn(0, 255)
                canvas.drawPath(stroke.path, badgeGlow)
                badgeEdge.alpha = (stroke.alpha * gate).roundToInt().coerceIn(0, 255)
                canvas.drawPath(stroke.path, badgeEdge)
            }
            if (value[2] > 0.001f) {
                canvas.drawText(badge.text, value[0], value[1] + textBaselineUnit * badgeText.textSize, badgeText)
            }
        }
    }

    private class Sprite(val image: Bitmap, val size: FloatArray, val rect: FloatArray)
    private class Coin(val id: String, val sprite: Sprite, val frames: Array<FloatArray>) { val value = FloatArray(4) }
    private class Badge(val id: String, val text: String, val frames: Array<FloatArray>, val appearance: Array<FloatArray>,
        val fontSize: Float, val fillColor: Int, val fillOpacity: Float, val strokes: Array<Array<Wave>>) {
        val value = FloatArray(3)
        val shape = FloatArray(4)
    }
    private class Wave(val path: Path, val alpha: Int)

    private companion object {
        fun rgb(values: JSONArray): Int {
            return Color.rgb(values.getInt(0), values.getInt(1), values.getInt(2))
        }

        fun contourPath(points: JSONArray, closed: Boolean): Path {
            val path = Path()
            for (index in 0 until points.length()) {
                val point = points.getJSONArray(index)
                val x = point.getDouble(0).toFloat()
                val y = point.getDouble(1).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (closed) path.close()
            return path
        }

        fun floats(values: JSONArray): FloatArray {
            return FloatArray(values.length()) { values.getDouble(it).toFloat() }
        }

        fun track(frames: JSONArray): Array<FloatArray> {
            return Array(frames.length()) { floats(frames.getJSONArray(it)) }
        }

        fun alpha(value: Float): Int {
            return (value.coerceIn(0f, 1f) * 255).roundToInt()
        }

        fun sample(frames: Array<FloatArray>, output: FloatArray, time: Double) {
            val frame = (time * 60).coerceIn(0.0, frames.lastIndex.toDouble())
            val current = frame.toInt()
            val next = min(current + 1, frames.lastIndex)
            val progress = (frame - current).toFloat()
            val a = frames[current]
            val b = frames[next]
            for (channel in output.indices) output[channel] = a[channel] + (b[channel] - a[channel]) * progress
        }
    }
}
