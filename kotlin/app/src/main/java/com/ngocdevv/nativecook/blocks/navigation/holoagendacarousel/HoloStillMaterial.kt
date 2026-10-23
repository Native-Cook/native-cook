package com.ngocdevv.nativecook.blocks.navigation.holoagendacarousel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BlurMaskFilter
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import kotlin.math.*

/** API 26–32: a still sample of each original ring field, with the same uneven palette stops. */
internal class HoloStillMaterial {
    private val bitmaps = Array(4) { bitmap(it) }
    private val shaders = mutableMapOf<Triple<Int, Float, Float>, Shader>()
    fun shader(kind: Int, width: Float, height: Float): Shader {
        return shaders.getOrPut(Triple(kind, width, height)) {
            BitmapShader(bitmaps[kind], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(width/192, height/192) })
            }
        }
    }
    private val glows = mutableMapOf<Pair<Int, Boolean>, Bitmap>()
    fun glow(kind: Int, wide: Boolean): Bitmap {
        return glows.getOrPut(kind to wide) {
            val image = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(bitmaps[kind], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setTranslate(96f, 96f) })
                }
                val sigma = if (wide) 34f else 10f
                maskFilter = BlurMaskFilter((sigma-.5f)/.57735f, BlurMaskFilter.Blur.NORMAL)
            }
            Canvas(image).drawRoundRect(96f, 96f, 288f, 288f, 20f, 20f, paint)
            image
        }
    }
    private fun bitmap(kind: Int): Bitmap {
        val result = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(192*192) { pixel -> sample(kind, (pixel%192+.5f)/192, (pixel/192+.5f)/192) }
        result.setPixels(pixels, 0, 192, 0, 0, 192, 192)
        return result
    }
    private fun ring(x: Float, y: Float, dx: Float, dy: Float): Float {
        val a = 1-dx*dx-dy*dy
        val b = x*dx+y*dy
        return max(0f, (-b+sqrt(max(b*b+a*(x*x+y*y), 0f)))/a)
    }
    private fun sample(kind: Int, x: Float, y: Float): Int {
        val ring = when (kind) {
            0 -> min(ring((x-.5f)/1.9f, y-.4f, 0f, -.85f), ring((x-.5f)/1.9f, y-.62f, 0f, .85f))
            1 -> ring(.819f*(x-.34f)-.574f*(y-.7f), (.574f*(x-.34f)+.819f*(y-.7f))/.52f, .4f, -.08f)
            2 -> ring(x-.5f, y-.12f, 0f, .85f)
            else -> max(0f, y+.05f)
        }
        val rings = floatArrayOf(.17f, .15f, .143f, .14f)[kind]
        val phase = HoloTheme.mod(rings*ln(1+ring/.045f)-HoloTheme.phases[kind], 1f)
        val palette = HoloTheme.palettes[kind]
        var color = rgb(palette[if (kind == 1) 1 else 0])
        fun ramp(index: Int, lo: Float, hi: Float, u: Float = phase) {
            val target = rgb(palette[index])
            val amount = HoloTheme.smooth(lo, hi, u)
            repeat(3) { color[it] += (target[it]-color[it])*amount }
        }
        when (kind) {
            0 -> {
                val u = HoloTheme.mod(phase*2, 1f)
                ramp(1,.30f,.44f,u)
                ramp(2,.44f,.58f,u)
                ramp(3,.64f,.80f,u)
                ramp(4,.80f,.88f,u)
                ramp(0,.91f,1f,u)
                val pale = HoloTheme.smooth(.07f,.26f,ring)*.42f
                val far = HoloTheme.smooth(.22f,.42f,ring)*.85f
                val flare = .22f*(x-.5f)*(x-.5f)
                val waist = 1-HoloTheme.smooth(.073f+flare,.118f+flare,abs(y-.51f))
                val mint = rgb(palette[4])
                repeat(3) {
                    color[it] += (1-color[it])*.55f*pale
                    color[it] += (mint[it]*.4f+.6f-color[it])*far
                    color[it] += (1-color[it])*waist
                }
            }
            1 -> {
                ramp(0,0f,.28f)
                ramp(2,.355f,.39f)
                ramp(3,.41f,.45f)
                ramp(4,.48f,.53f)
                ramp(0,.55f,.60f)
                ramp(2,.655f,.69f)
                ramp(3,.71f,.75f)
                ramp(4,.78f,.84f)
                ramp(1,.87f,.95f)
            }
            2 -> {
                ramp(2,.045f,.075f)
                ramp(3,.075f,.10f)
                ramp(4,.10f,.13f)
                ramp(3,.15f,.18f)
                ramp(5,.18f,.21f)
                ramp(1,.21f,.26f)
                ramp(0,.26f,.40f)
                ramp(4,.495f,.515f)
                ramp(0,.525f,.555f)
                val mint = HoloTheme.smooth(.10f,.55f,color[1]-color[0])*HoloTheme.smooth(.25f,.70f,y)
                val cyan = rgb(palette[3])
                repeat(3) { color[it] += (cyan[it]-color[it])*mint }
            }
            else -> {
                ramp(1,.12f,.24f)
                ramp(2,.27f,.31f)
                ramp(1,.35f,.40f)
                ramp(0,.44f,.56f)
                ramp(3,.60f,.70f)
                ramp(4,.70f,.82f)
                ramp(5,.82f,.90f)
                ramp(0,.92f,1f)
            }
        }
        val light = exp(-(x*.76f+y*.34f-.5f).pow(2)/.016f)*.14f
        repeat(3) { color[it] = (color[it]+(1-color[it])*light)*(1-HoloTheme.smooth(.78f,1f,y)*.07f) }
        return android.graphics.Color.rgb((color[0]*255).toInt(), (color[1]*255).toInt(), (color[2]*255).toInt())
    }
    private fun rgb(color: Int) = floatArrayOf((color shr 16)/255f, ((color shr 8) and 255)/255f, (color and 255)/255f)
}
