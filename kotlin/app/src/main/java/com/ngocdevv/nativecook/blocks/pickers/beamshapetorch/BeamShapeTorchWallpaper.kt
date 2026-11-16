package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchTheme as Theme
import kotlin.math.max
import kotlin.math.roundToInt

/** The film grain's runtime shader (API 33+). */
@RequiresApi(33)
internal class BeamShapeTorchGrainPass {
    private val shader = RuntimeShader(BeamShapeTorchGrainSource)
    private val paint = Paint()

    fun draw(c: Canvas, w: Float, h: Float, density: Float) {
        shader.setFloatUniform("uPx", density)
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}

/**
 * The page behind the control: the pin's magenta wallpaper (a generated 1206 x 2622 image drawn to cover the screen, anchored to the
 * top, so a shorter phone loses the bottom of the dome), its own colours standing in while the image decodes so nothing shifts,
 * and a film grain over it (one speck per device pixel, +-3.5 % of white or black).
 */
internal class BeamShapeTorchWallpaper(private val assets: AssetManager) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val grainPass: BeamShapeTorchGrainPass? = if (Build.VERSION.SDK_INT >= 33) {
        try {
            BeamShapeTorchGrainPass()
        } catch (error: IllegalArgumentException) {
            Log.w("BeamShapeTorch", "Runtime shader unavailable; using a noise tile", error)
            null
        }
    } else null
    private val noise: Paint? by lazy {
        if (grainPass != null) {
            null
        } else {
            // a 256 px tile of the same grain: per device pixel, +-3.5 % white or black
            val tile = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val rnd = java.util.Random(7)
            for (y in 0 until 256) {
                for (x in 0 until 256) {
                    val g = rnd.nextFloat() - 0.5f
                    val a = kotlin.math.abs(g) * 0.07f
                    val lum = if (g > 0) 255 else 0
                    tile.setPixel(x, y, Color.argb((a * 255).roundToInt(), lum, lum, lum))
                }
            }
            Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        }
    }

    fun decode(): Bitmap? = try {
        assets.open("beamshapetorch/beamshapetorch_wallpaper-pro.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (error: Exception) {
        Log.w("BeamShapeTorch", "Wallpaper unavailable", error)
        null
    }

    /** `c` is scaled to dp; `density` is pixels per dp. */
    fun draw(c: Canvas, w: Float, h: Float, density: Float, image: Bitmap?) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, h, intArrayOf(Theme.wallTop, Theme.wallMid, Theme.wallLow), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        if (image != null) {
            val k = max(w / 1206f, h / 2622f)
            val iw = 1206f * k
            dst.set((w - iw) / 2, 0f, (w - iw) / 2 + iw, 2622f * k)
            c.drawBitmap(image, null, dst, paint)
        }
        val pass = grainPass
        if (pass != null && Build.VERSION.SDK_INT >= 33) {
            pass.draw(c, w, h, density)
        } else {
            val n = noise ?: return
            c.save()
            c.scale(1f / density, 1f / density)
            c.drawRect(0f, 0f, w * density, h * density, n)
            c.restore()
        }
    }
}
