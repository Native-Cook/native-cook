package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/**
 * The curl and the foil as AGSL runtime shaders (API 33+), over the four page snapshots. The shaders work in
 * points, so the canvas is scaled by the density and each snapshot gets the inverse scale as its local matrix:
 * a point of the shader's space reads the device pixel that lies there.
 */
@RequiresApi(33)
internal class PageCurlGpu(
    private val pages: List<Bitmap>,
    private val density: Float,
    private val widthDp: Float,
    private val heightDp: Float,
    private val foil: FoilRect,
) {
    private val toPoints = Matrix().apply { setScale(1f / density, 1f / density) }
    private val children = pages.map { page ->
        BitmapShader(page, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply { setLocalMatrix(toPoints) }
    }

    // One shader and one paint per sheet: a riffle draws up to three at once.
    private val curlShaders = Array(RIFFLE.SLOTS) {
        RuntimeShader(CURL_AGSL).also { shader ->
            children.forEachIndexed { index, child -> shader.setInputShader("p$index", child) }
        }
    }
    private val curlPaints = Array(RIFFLE.SLOTS) { index ->
        Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = curlShaders[index] }
    }

    private val foilShader = RuntimeShader(FOIL_AGSL).also { shader ->
        // The cover snapshot is read in screen points, whatever the rectangle's origin: shift it back.
        val cover = BitmapShader(pages[LAST], Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
            setLocalMatrix(Matrix().apply {
                setScale(1f / density, 1f / density)
                postTranslate(-foil.left, -foil.top)
            })
        }
        shader.setInputShader("cover", cover)
    }
    private val foilPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = foilShader }

    fun drawCurl(canvas: Canvas, slot: Int, u: CurlUniforms) {
        val shader = curlShaders[slot]
        shader.setFloatUniform("size", u.sizeW, u.sizeH)
        shader.setFloatUniform("grab", u.grabX, u.grabY)
        shader.setFloatUniform("tip", u.tipX, u.tipY)
        shader.setFloatUniform("r0", u.r0)
        shader.setFloatUniform("r1", u.r1)
        shader.setFloatUniform("rmin", u.rmin)
        shader.setFloatUniform("frontIdx", u.frontIdx)
        shader.setFloatUniform("underIdx", u.underIdx)
        shader.setFloatUniform("shA", u.shA)
        shader.setFloatUniform("shW", u.shW)
        shader.setFloatUniform("eA", u.eA)
        shader.setFloatUniform("eW", u.eW)
        shader.setFloatUniform("amb1", u.amb1)
        shader.setFloatUniform("amb2", u.amb2)
        shader.setFloatUniform("g2", u.g2)
        shader.setFloatUniform("bg", u.bg)
        shader.setFloatUniform("ba", u.ba)
        shader.setFloatUniform("grain", u.grain)
        shader.setFloatUniform("edgeInk", u.edgeInk)
        shader.setFloatUniform("px", u.px)
        shader.setFloatUniform("fade", u.fade)
        shader.setFloatUniform("clearUnder", u.clearUnder)
        shader.setFloatUniform("spec", u.spec)
        canvas.save()
        canvas.scale(density, density)
        canvas.drawRect(0f, 0f, widthDp, heightDp, curlPaints[slot])
        canvas.restore()
    }

    /** The foil band over the cover's ink, inside the foil rectangle. */
    fun drawFoil(canvas: Canvas, u: FoilUniforms) {
        foilShader.setFloatUniform("dir", u.dirX, u.dirY)
        foilShader.setFloatUniform("center", u.center)
        foilShader.setFloatUniform("width", u.width)
        foilShader.setFloatUniform("amount", u.amount)
        canvas.save()
        canvas.scale(density, density)
        canvas.translate(foil.left, foil.top)
        canvas.clipRect(0f, 0f, foil.width, foil.height)
        canvas.drawRect(0f, 0f, foil.width, foil.height, foilPaint)
        canvas.restore()
    }
}
