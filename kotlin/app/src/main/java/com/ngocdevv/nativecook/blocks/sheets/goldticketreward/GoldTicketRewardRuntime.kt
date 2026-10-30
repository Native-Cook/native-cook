package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import android.graphics.*
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.Box

@RequiresApi(33)
internal class GoldTicketRewardRuntime(cars: Bitmap) {
    private val foil = RuntimeShader(GoldTicketRewardShaders.FOIL)
    private val dissolve = RuntimeShader(GoldTicketRewardShaders.DISSOLVE)
    private val image = BitmapShader(cars, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
    private val carWidth = cars.width.toFloat()
    private val carHeight = cars.height.toFloat()

    fun foil(b: Box, wipe: Double, sweep: Double, tx: Double, ty: Double): Shader {
        foil.setFloatUniform("rect", b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat())
        foil.setFloatUniform("wipe", wipe.toFloat())
        foil.setFloatUniform("sweep", sweep.toFloat())
        foil.setFloatUniform("tilt", tx.toFloat(), ty.toFloat())
        return foil
    }

    fun dissolve(b: Box, prog: Double, glint: Double): Shader {
        image.setLocalMatrix(Matrix().apply {
            setScale(b.w.toFloat() / carWidth, b.h.toFloat() / carHeight)
            postTranslate(b.x.toFloat(), b.y.toFloat())
        }
        )
        dissolve.setInputShader("image", image)
        dissolve.setFloatUniform("rect", b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat())
        dissolve.setFloatUniform("prog", prog.toFloat())
        dissolve.setFloatUniform("glint", glint.toFloat())
        return dissolve
    }
}
