package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

/** Shared contact-led environment/facet response and the exact RN SkSL on API33+. */
internal class TrophySavingsMilestoneLighting {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val poolShaders=HashMap<Int,Shader>()
    private val facet=requireNotNull(PathParser.createPathFromPathData("M159 111C157 151 165 178 172 192C176 201 181 209 185 214C176 195 172 154 174 110Z"))
    private val glint=requireNotNull(PathParser.createPathFromPathData("M174 122L176.5 127.5L183.5 130L176.5 132.5L174 138L171.5 132.5L164.5 130L171.5 127.5Z"))
    private val facetShader=LinearGradient(158f,128f,184f,199f,intArrayOf(0xFFFFFCE3.toInt(),0xFFFFF4BB.toInt()),null,Shader.TileMode.CLAMP)
    private val shimmer: Shader? = if(Build.VERSION.SDK_INT>=33) runCatching { RuntimeShader(SHIMMER_SOURCE) }.getOrNull() else null
    private val fallbackMatrix=Matrix()
    private val fallback: Shader = run {
        val offsets=floatArrayOf(-24f,-16f,-9f,-5f,-3f,0f,3f,5f,9f,16f,24f)
        val colors=IntArray(offsets.size) { i ->
            val d=offsets[i].toDouble(); val core=exp(-0.5*(d/3.4).pow(2)); val shoulder=exp(-0.5*(d/9).pow(2))
            val alpha=0.5*core+0.14*shoulder
            Color.argb((255*alpha).roundToInt(),255,(255*(0.84+0.14*core)).roundToInt(),(255*(0.30+0.52*core)).roundToInt())
        }
        LinearGradient(-24f,0f,24f,0f,colors,FloatArray(offsets.size){(offsets[it]+24)/48},Shader.TileMode.CLAMP)
    }
    private fun smooth(x: Double)=TrophySavingsMilestoneMotion.smooth(x)
    fun drawPool(canvas: Canvas,scene: TrophySavingsMilestoneCelebration.Frame) {
        val power=if(scene.active && !scene.reduced) smooth((scene.progress-0.08)/0.74) else 0.0
        if(power<=0) return
        val a=(255*0.35*power).roundToInt(); val b=(255*0.24*power).roundToInt(); val c=(255*0.055*power).roundToInt()
        val key=(a shl 16) or (b shl 8) or c
        val shader=poolShaders.getOrPut(key) { RadialGradient(196f,157f,190f,
            intArrayOf(Color.argb(a,255,216,135),Color.argb(b,255,227,173),Color.argb(c,255,240,203),Color.argb(0,255,240,203)),
            floatArrayOf(0f,0.45f,0.82f,1f),Shader.TileMode.CLAMP) }
        paint.color=Color.WHITE; paint.alpha=255; paint.style=Paint.Style.FILL; paint.shader=shader
        canvas.drawRect(0f,0f,393f,350f,paint); paint.shader=null
    }
    fun drawCup(canvas: Canvas,scene: TrophySavingsMilestoneCelebration.Frame) {
        if(!scene.active || scene.reduced || scene.contactStart<0) return
        val age=(scene.clock-scene.contactStart)/scene.cadence
        if(age<0 || age>=0.42) return
        val flash=smooth(age/0.07)*(1-smooth((age-0.11)/0.31))
        canvas.save(); canvas.clipPath(TrophySavingsMilestoneArtwork.bowl)
        paint.shader=facetShader; paint.color=Color.WHITE; paint.alpha=(255*0.28*flash).roundToInt()
        canvas.drawPath(facet,paint)
        paint.shader=null; paint.color=Color.WHITE; paint.alpha=(255*0.65*flash).roundToInt(); canvas.drawPath(glint,paint)
        canvas.restore()
    }
    fun drawShimmer(canvas: Canvas,scene: TrophySavingsMilestoneCelebration.Frame) {
        if(!scene.active || scene.reduced || scene.contactStart<0) return
        val age=(scene.clock-scene.contactStart)/max(0.001,scene.cadence)
        if(age<0 || age>=0.30) return
        val travel=(age/0.22).coerceIn(0.0,1.0)
        val sweep=(123+160*(1-(1-travel)*(1-travel))).toFloat()
        val strength=(smooth(age/0.035)*(1-smooth((age-0.08)/0.22))).toFloat()
        if(strength<=0) return
        canvas.save(); canvas.clipPath(TrophySavingsMilestoneArtwork.gold)
        paint.color=Color.WHITE; paint.alpha=255
        if(Build.VERSION.SDK_INT>=33 && shimmer is RuntimeShader) {
            shimmer.setFloatUniform("sweep",sweep); shimmer.setFloatUniform("strength",strength)
            paint.shader=shimmer; canvas.drawRect(130f,96f,260f,266f,paint)
        } else {
            // Finite warm reflection on older Android; same clock/mask, sampled Gaussian profile.
            fallbackMatrix.setValues(floatArrayOf(1f,-0.28f,sweep+44.8f,0f,1f,0f,0f,0f,1f))
            fallback.setLocalMatrix(fallbackMatrix)
            paint.shader=fallback; paint.alpha=(255*strength).roundToInt(); canvas.drawRect(130f,96f,260f,266f,paint)
        }
        canvas.restore(); paint.shader=null
    }
    companion object {
        private const val SHIMMER_SOURCE = """
  uniform float sweep;
  uniform float strength;

  half4 main(float2 xy) {
    float curvedFace = (xy.x - 195.5) * (xy.x - 195.5) * 0.0007;
    float distanceToBand = xy.x + 0.28 * (xy.y - 160.0) + curvedFace - sweep;
    float narrow = distanceToBand / 3.4;
    float broad = distanceToBand / 9.0;
    float core = exp(-0.5 * narrow * narrow);
    float shoulder = exp(-0.5 * broad * broad);
    float stemFalloff = 1.0 - 0.18 * smoothstep(218.0, 266.0, xy.y);
    float alpha = strength * stemFalloff * (0.50 * core + 0.14 * shoulder);
    float3 gold = mix(float3(1.0, 0.84, 0.30), float3(1.0, 0.98, 0.82), core);
    return half4(gold * alpha, alpha);
  }

"""
    }
}
