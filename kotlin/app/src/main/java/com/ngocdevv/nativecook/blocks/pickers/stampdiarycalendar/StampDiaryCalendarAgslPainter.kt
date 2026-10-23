package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.IdentityHashMap

/** The AGSL painter: one RuntimeShader per draw slot, bitmap shaders cached per bitmap. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class StampDiaryCalendarAgslPainter : StampDiaryCalendarPainter {
    private val stampShaders = ArrayList<RuntimeShader>()
    private val shadowShaders = ArrayList<RuntimeShader>()
    private val bitmapShaders = IdentityHashMap<Bitmap, BitmapShader>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var stampSlot = 0
    private var shadowSlot = 0
    private val blank: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun begin() {
        stampSlot = 0
        shadowSlot = 0
    }

    private fun bitmapShader(bitmap: Bitmap): BitmapShader {
        return bitmapShaders.getOrPut(bitmap) {
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                it.filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    }

    override fun stamp(canvas: Canvas, space: StampDiaryCalendarSpace, a: StampDiaryCalendarStampArgs) {
        val rect = a.projection.bounds(a.halfWidth, a.halfHeight)
        val left = (space.x(rect[0]) - 1).toFloat()
        val top = (space.y(rect[1]) - 1).toFloat()
        val right = (space.x(rect[2]) + 1).toFloat()
        val bottom = (space.y(rect[3]) + 1).toFloat()
        if (stampSlot == stampShaders.size) stampShaders.add(RuntimeShader(StampDiaryCalendarShaderSource.STAMP))
        val shader = stampShaders[stampSlot++]
        val rows = a.projection.inverse()
        val crop = stampDiaryCalendarCrop(a.w, a.h, a.art.width.toDouble())
        val p = StampDiaryCalendarPostmarkSpec
        val k = p.OUTER / (StampDiaryCalendarInkMark.RADIUS * a.w)
        shader.setInputShader("art", bitmapShader(a.art))
        shader.setInputShader("mark", bitmapShader(a.mark ?: blank))
        shader.setFloatUniform("view", 0f, space.oyPx.toFloat(), space.s.toFloat(), 0f)
        shader.setFloatUniform("h0", rows[0], rows[1], rows[2])
        shader.setFloatUniform("h1", rows[3], rows[4], rows[5])
        shader.setFloatUniform("h2", rows[6], rows[7], rows[8])
        shader.setFloatUniform("bounds", a.halfWidth.toFloat(), a.halfHeight.toFloat())
        shader.setFloatUniform("box", a.w.toFloat(), a.h.toFloat(), a.aa.toFloat(), a.dim.toFloat())
        shader.setFloatUniform("crop", crop[0], crop[1], crop[2], crop[3])
        shader.setFloatUniform("holes", a.holes.first.toFloat(), a.holes.second.toFloat(), StampDiaryCalendarTheme.PERFORATION_RADIUS.toFloat(), a.shadow.toFloat())
        shader.setFloatUniform("curl", a.curl.nx.toFloat(), a.curl.ny.toFloat(), a.curl.a.toFloat(), a.curl.r.toFloat())
        shader.setFloatUniform("sheen", a.sheenPos.toFloat(), a.sheenAmt.toFloat(), a.inkWet.toFloat(), a.inkOn.toFloat())
        shader.setFloatUniform(
            "ink",
            (StampDiaryCalendarInkMark.X * a.w).toFloat(), (StampDiaryCalendarInkMark.Y * a.h).toFloat(),
            k.toFloat(), StampDiaryCalendarInkMark.ROTATION.toFloat(),
        )
        shader.setFloatUniform("inkfx", a.inkSpread.toFloat(), a.inkThreshold.toFloat(), p.RING_X.toFloat(), p.RING_Y.toFloat())
        paint.shader = shader
        paint.alpha = (a.opacity * 255).toInt().coerceIn(0, 255)
        canvas.drawRect(left, top, right, bottom, paint)
        paint.shader = null
    }

    override fun shadow(canvas: Canvas, space: StampDiaryCalendarSpace, projection: StampDiaryCalendarProjection, w: Double, h: Double, sigma: Double, opacity: Double) {
        val halfW = w / 2 + 3.2 * sigma
        val halfH = h / 2 + 3.2 * sigma
        val rect = projection.bounds(halfW, halfH)
        if (shadowSlot == shadowShaders.size) shadowShaders.add(RuntimeShader(StampDiaryCalendarShaderSource.SHADOW))
        val shader = shadowShaders[shadowSlot++]
        val rows = projection.inverse()
        shader.setFloatUniform("view", 0f, space.oyPx.toFloat(), space.s.toFloat(), 0f)
        shader.setFloatUniform("h0", rows[0], rows[1], rows[2])
        shader.setFloatUniform("h1", rows[3], rows[4], rows[5])
        shader.setFloatUniform("h2", rows[6], rows[7], rows[8])
        shader.setFloatUniform("box", w.toFloat(), h.toFloat(), sigma.toFloat(), opacity.toFloat())
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(
            (space.x(rect[0]) - 1).toFloat(), (space.y(rect[1]) - 1).toFloat(),
            (space.x(rect[2]) + 1).toFloat(), (space.y(rect[3]) + 1).toFloat(), paint,
        )
        paint.shader = null
    }
}
