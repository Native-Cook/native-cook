package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsTheme as T
import kotlin.math.ceil

/** API 33+: the ray-traced gem and the caustic under it, both AGSL. Construction throws if a shader does not compile. */
@RequiresApi(33)
internal class FrostHeartRewardsRuntime(
    private val gem: FrostHeartRewardsGem,
    bear: Bitmap,
    hug: Bitmap,
    heart: Bitmap,
) {
    private val shader = RuntimeShader(FrostHeartRewardsShaders.gem(gem.table, T.AMETHYST, T.BEAR_MATRIX))
    private val caustic = RuntimeShader(FrostHeartRewardsShaders.CAUSTIC)
    private val paint = Paint().apply { shader = this@FrostHeartRewardsRuntime.shader }
    private val causticPaint = Paint().apply { shader = caustic; xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val xf = FloatArray(36 * 4)
    private val node = RenderNode("FrostHeartRewards gem")
    private var pageShader: Shader? = null
    /** The ray trace covers the projected gem across tilt, turn and the case's spread. */
    private val area = RectF(20f, 143f, 333f, 403f)

    init {
        shader.setFloatUniform("ext", gem.ext)
        shader.setFloatUniform("rad", gem.rad)
        shader.setFloatUniform("grp", gem.grp)
        shader.setInputShader("bear", sprite(bear, T.BEAR))
        shader.setInputShader("hug", sprite(hug, T.BEAR))
        shader.setInputShader("skin", sprite(heart, T.HEART))
        // Until the rays are rasterized the backdrop is the page's flat ground.
        shader.setInputShader("page", BitmapShader(Bitmap.createBitmap(intArrayOf(T.BACKGROUND), 1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }

    private fun sprite(bitmap: Bitmap, s: T.Sprite) = BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
        setLocalMatrix(Matrix().apply {
            setScale((s.w / bitmap.width).toFloat(), (s.h / bitmap.height).toFloat())
            postTranslate(s.x.toFloat(), s.y.toFloat())
            postRotate(s.rotation.toFloat(), (s.x + s.w / 2).toFloat(), (s.y + s.h / 2).toFloat())
        })
    }

    /** The rays bitmap is [scale] pixels per point. */
    fun setPage(rays: Bitmap, scale: Double) {
        if (pageShader != null) return
        val s = BitmapShader(rays, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
            setLocalMatrix(Matrix().apply { setScale((1 / scale).toFloat(), (1 / scale).toFloat()) })
        }
        pageShader = s
        shader.setInputShader("page", s)
    }

    class Uniforms {
        var t = 0.0; var gem = 0.0; var display = 0.0; var spotlight = 0.0
        var reset = 0.0; var polish = 0.0; var hue = 0.0; var reduced = false
        var pitch = 0.0; var yaw = 0.0; var tiltX = 0.0; var tiltY = 0.0
        var polishX = 0.0; var breath = 0.0; var breathX = 0.0; var breathY = 0.0
        var core = 0.0; var completion = 0.0
    }

    /** Draws in design points; [scale] is device pixels per point of the current canvas. */
    fun draw(c: Canvas, scale: Float, u: Uniforms) {
        gem.transforms(FrostHeartRewardsGem.Pose(u.pitch, u.yaw), u.t, u.display, xf)
        shader.setFloatUniform("xf", xf)
        shader.setFloatUniform("uA", u.t.toFloat(), u.gem.toFloat(), u.display.toFloat(), u.spotlight.toFloat())
        shader.setFloatUniform("uB", u.reset.toFloat(), u.polish.toFloat(), u.hue.toFloat(), if (u.reduced) 1f else 0f)
        shader.setFloatUniform("uC", u.pitch.toFloat(), u.yaw.toFloat(), u.tiltX.toFloat(), u.tiltY.toFloat())
        shader.setFloatUniform("uD", u.polishX.toFloat(), u.breath.toFloat(), u.breathX.toFloat(), u.breathY.toFloat())
        val assembled = FrostHeartRewardsMotion.assembled(u.t, u.display)
        if (c.isHardwareAccelerated) {
            // Like the reference's half-density crystal target: trace at 0.7× and let the layer scale up.
            val ps = scale * QUALITY
            val w = ceil(area.width() * ps).toInt().coerceAtLeast(1)
            val h = ceil(area.height() * ps).toInt().coerceAtLeast(1)
            shader.setFloatUniform("uE", u.core.toFloat(), if (assembled) 1f else 0f, ps, u.completion.toFloat())
            paint.shader = shader
            node.setPosition(0, 0, w, h)
            node.setUseCompositingLayer(true, null)
            val rc = node.beginRecording(w, h)
            try {
                rc.scale(ps, ps)
                rc.translate(-area.left, -area.top)
                rc.drawRect(area, paint)
            } finally {
                node.endRecording()
            }
            c.save()
            c.translate(area.left, area.top)
            c.scale(1 / ps, 1 / ps)
            c.drawRenderNode(node)
            c.restore()
        } else {
            shader.setFloatUniform("uE", u.core.toFloat(), if (assembled) 1f else 0f, scale, u.completion.toFloat())
            paint.shader = shader
            c.drawRect(area, paint)
        }
    }

    fun drawCaustic(c: Canvas, time: Double, light: Double, display: Double, polish: Double) {
        caustic.setFloatUniform("uA", time.toFloat(), light.toFloat(), display.toFloat(), polish.toFloat())
        causticPaint.shader = caustic
        c.drawRect(42f, 285f, 328f, 393f, causticPaint)
    }

    companion object { const val QUALITY = 0.7f }
}
