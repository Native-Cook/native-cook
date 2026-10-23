package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Retained AGSL materials, including the real stage as the crystal's refracted child. */
@android.annotation.TargetApi(33)
internal class CrystalTrophyRenderer(context: Context) {
    private val back = RuntimeShader(BACK_AGSL)
    private val curtain = RuntimeShader(CURTAIN_AGSL)
    private val dust = RuntimeShader(DUST_AGSL)
    private val trophy = RuntimeShader(TROPHY_AGSL)
    private val share = RuntimeShader(CONTROLS_AGSL)
    private val close = RuntimeShader(CONTROLS_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        fun bitmap(name: String, repeat: Boolean): BitmapShader {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPremultiplied = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val image = context.assets.open("crystaltrophyreveal/crystaltrophyreveal_$name").use {
                requireNotNull(BitmapFactory.decodeStream(it, null, options))
            }
            val tile = if (repeat) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            return BitmapShader(image, tile, tile).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
        }
        trophy.setInputBuffer("engraving", bitmap("trophy-engrave.png", false))
        trophy.setInputShader("marble", bitmap("trophy-marble.png", true))
        trophy.setFloatUniform("uPlanes", CrystalTrophyMesh.CRYSTAL_PLANES)
        trophy.setFloatUniform("uBase", CrystalTrophyMesh.PLINTH_PLANES)
        trophy.setFloatUniform("uCamera", 11f, 0.15f)
    }

    private fun uniform(shader: RuntimeShader, name: String, vararg values: Double) {
        shader.setFloatUniform(name, values.map { it.toFloat() }.toFloatArray())
    }

    private fun rect(canvas: Canvas, shader: RuntimeShader, x: Double, y: Double, w: Double, h: Double, alpha: Double = 1.0) {
        paint.shader = shader
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        paint.shader = null
        paint.alpha = 255
    }

    fun stage(canvas: Canvas, f: CrystalTrophyFrame, s: CrystalTrophyState, density: Float) {
        val time = if (s.reduced) 0.0 else if (s.auto && !s.paused) s.demoTime else s.clock
        for (shader in listOf(back, curtain, dust)) {
            uniform(shader, "uSize", f.width, f.height)
            uniform(shader, "uOrigin", f.originX, f.originY)
            uniform(shader, "uUnit", f.unit)
            uniform(shader, "uTime", time % 6000)
            uniform(shader, "uLamp", max(0.0, s.lamp))
            uniform(shader, "uBeam", s.beam)
        }
        uniform(back, "uUnder", s.under)
        uniform(back, "uSeam", s.seam)
        uniform(back, "uRise", s.rise)
        uniform(back, "uFlash", s.flash)
        back.setFloatUniform("uFlecks", CrystalTrophyOptics.flecks(f, s.yaw))
        uniform(curtain, "uOpen", s.open)
        uniform(curtain, "uHem", s.hem)
        uniform(curtain, "uShiver", s.shiver)
        uniform(curtain, "uSeam", s.seam)
        uniform(trophy, "uOrigin", f.originX, f.originY)
        uniform(trophy, "uUnit", f.unit)
        uniform(trophy, "uPixelRatio", density.toDouble())
        uniform(trophy, "uTurn", cos(s.yaw), sin(s.yaw))
        trophy.setFloatUniform("uRim", CrystalTrophyOptics.rim(s.yaw, s.rise, f))
        trophy.setFloatUniform("uFire", CrystalTrophyOptics.sparkles(s, f))
        uniform(trophy, "uDrop", (1 - s.rise) * 2.96)
        uniform(trophy, "uLamp", max(0.0, s.lamp))
        uniform(trophy, "uUnder", max(0.0, s.under))
        uniform(trophy, "uEtch", s.etch)
        uniform(trophy, "uTouch", s.touch)
        uniform(trophy, "uFlash", s.flash)
        uniform(trophy, "uFinger", s.touchX, s.touchY)
        uniform(trophy, "uTime", time % 15)
        trophy.setInputShader("stage", back)
        val centerY = (f.originY - 1.45 * f.unit).toFloat()
        canvas.save()
        canvas.translate(0f, s.cameraY.toFloat())
        canvas.scale(s.cameraScale.toFloat(), s.cameraScale.toFloat(), f.originX.toFloat(), centerY)
        rect(canvas, back, 0.0, 0.0, f.width, f.height)
        rect(canvas, trophy, f.originX - f.unit * 1.6, f.originY - f.unit * 3.65, f.unit * 3.2, f.unit * 4.4)
        val b = s.beam.coerceIn(0.0, 1.0)
        val poolX = f.width * 0.24 * (1 - b) + f.originX * b - 0.08 * f.width * sin(b * PI)
        val poolY = f.height * 0.17 * (1 - b) + f.originY * b
        val lampX = f.originX + 0.32 * f.unit
        val left = max(0.0, min(poolX - 115 * f.k, lampX - 10 * f.k))
        val right = min(f.width, max(poolX + 115 * f.k, lampX + 10 * f.k))
        rect(canvas, dust, left, 0.0, right - left, max(0.0, poolY))
        val extent = min(f.width / 2, max(f.width * (0.5 - 0.35 * s.open), f.width * (0.5 - 0.385 * s.hem)) + 32 * f.k)
        rect(canvas, curtain, 0.0, 0.0, extent, f.height)
        rect(canvas, curtain, f.width - extent, 0.0, extent, f.height)
        canvas.restore()
    }

    fun control(canvas: Canvas, f: CrystalTrophyFrame, s: CrystalTrophyState, gem: Boolean, alpha: Double) {
        val shader = if (gem) close else share
        val x = if (gem) f.closeX + 2 else f.shareX
        val y = if (gem) f.closeY + 2 else f.shareY
        val width = if (gem) 36.0 else f.shareWidth
        val height = if (gem) 36.0 else 56.0
        uniform(shader, "uOrigin", x, y)
        uniform(shader, "uSize", width, height)
        uniform(shader, "uRadius", if (gem) 18.0 else 16.0)
        uniform(shader, "uYaw", s.yaw)
        uniform(shader, "uLamp", s.lamp)
        uniform(shader, "uFlash", s.flash)
        uniform(shader, "uPress", if (gem) s.closePress else s.sharePress)
        uniform(shader, "uSheen", if (gem) -1.0 else s.sheen)
        uniform(shader, "uGem", if (gem) 1.0 else 0.0)
        uniform(shader, "uPressPoint", if (gem) s.closeX else s.shareX, if (gem) s.closeY else s.shareY)
        rect(canvas, shader, x - 8, y - 8, width + 16, height + 26, alpha)
    }
}
