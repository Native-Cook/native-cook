package com.ngocdevv.nativecook.blocks.media.voiceorbscanner

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class VoiceOrbScannerArt(context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "voiceorbscanner/voiceorbscanner_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "voiceorbscanner/voiceorbscanner_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(context.assets, "voiceorbscanner/voiceorbscanner_Inter-SemiBold.ttf")
    private val title = Typeface.createFromAsset(context.assets, "voiceorbscanner/voiceorbscanner_Outfit-Medium.ttf")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val runtime = if (Build.VERSION.SDK_INT >= 33) VoiceOrbScannerRuntime() else null
    private var warm: Shader? = null
    private var cachedW = 0.0
    private var cachedOY = 0.0
    private val rect = RectF()
    private val colors = intArrayOf(Color.rgb(255,111,183), Color.rgb(255,176,74), Color.rgb(183,140,255), Color.rgb(255,111,183))
    private fun fill(color: Int) { p.reset(); p.isAntiAlias = true; p.color = color }
    private fun box(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) {
        fill(color); rect.set(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat()); c.drawRoundRect(rect,r.toFloat(),r.toFloat(),p)
    }
    private fun label(c: Canvas, value: String, face: Typeface, fontSize: Double, x: Double, y: Double, color: Int = Color.WHITE, center: Boolean = false): Double {
        text.typeface = face; text.textSize = fontSize.toFloat(); text.color = color
        text.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        val fm = text.fontMetrics
        c.drawText(value,x.toFloat(),y.toFloat()-fm.ascent,text)
        return (fm.descent-fm.ascent).toDouble()
    }
    fun draw(c: Canvas, w: Double, h: Double, top: Double, bottom: Double, density: Float, f: VoiceOrbScannerMotion.Frame, reduced: Boolean, auto: Boolean, pressed: Boolean) {
        c.save(); c.scale(density,density)
        val cx = w/2; val oy = top+270; val r = min(118.0,w*0.29); val cw = (w-64)/3; val cy = oy+r+100
        val sp = if (reduced) if (f.s > 0.5) 1.0 else 0.0 else f.s
        c.drawColor(Color.rgb(23,18,31))
        box(c,w-58,top+8,38.0,38.0,19.0,0x1affffff)
        label(c,"✕",medium,15.0,w-39,top+17,center=true)
        val titleHeight = label(c,"AI Voice Scanning",title,22.0,cx,top+56,center=true)
        val chip = if (f.phase == 2) "Completed" else if (f.phase == 1) "Listening…" else "Ready"
        text.typeface = medium; text.textSize = 12f
        val chipWidth = 20+7+6+text.measureText(chip).toDouble(); val chipY = top+56+titleHeight+10
        box(c,cx-chipWidth/2,chipY,chipWidth,26.0,13.0,0x1affffff)
        fill(if (f.phase == 2) 0xff8ff0b4.toInt() else if (f.phase == 1) 0xffff6fb7.toInt() else 0x80ffffff.toInt())
        c.drawCircle((cx-chipWidth/2+13.5).toFloat(),(chipY+13).toFloat(),3.5f,p)
        label(c,chip,medium,12.0,cx-chipWidth/2+23,chipY+5)
        c.saveLayerAlpha(null, ((1-VoiceOrbScannerMotion.smooth(f.s*2))*255).toInt())
        label(c,"Hold the orb and sing a note",regular,14.0,cx,oy+r+30,0x99ffffff.toInt(),true); c.restore()
        label(c,"Voice resume",title,17.0,cx,cy-32,center=true)
        val labels = listOf("Voice tier","Vocal range","Breath"); val values = listOf("Soprano","2 octaves","2.2 sec")
        for (i in 0..2) {
            val k = VoiceOrbScannerMotion.smooth((f.s-0.55-i*0.08)/0.3)
            if (k <= 0) continue
            val x = 24+i*(cw+8); val y = cy+(1-k)*14
            c.saveLayerAlpha(null,(k*255).toInt()); c.translate((x+cw/2).toFloat(),(y+64).toFloat()); val scale = (0.94+0.06*k).toFloat(); c.scale(scale,scale); c.translate((-cw/2).toFloat(),-64f)
            box(c,0.0,0.0,cw,128.0,20.0,0xeb1c1626.toInt())
            fill(0x14ffffff); p.style = Paint.Style.STROKE; p.strokeWidth = 1f; rect.set(0.5f,0.5f,(cw-0.5).toFloat(),127.5f); c.drawRoundRect(rect,20f,20f,p)
            fill(0x0dffffff); c.drawCircle(27f,29f,17f,p)
            text.typeface = semibold; text.textSize = 16f; val valueHeight = text.fontMetrics.descent-text.fontMetrics.ascent
            text.typeface = regular; text.textSize = 12f; val labelHeight = text.fontMetrics.descent-text.fontMetrics.ascent
            label(c,labels[i],regular,12.0,13.0,115-valueHeight-labelHeight-4.0,0x99ffffff.toInt())
            label(c,values[i],semibold,16.0,13.0,115-valueHeight.toDouble())
            c.restore()
        }
        if (warm == null || cachedW != w || cachedOY != oy) {
            warm = RadialGradient(cx.toFloat(),(oy+40).toFloat(),(w*0.85).toFloat(), intArrayOf(0x8c782e5c.toInt(),0x403c1846,0x0017121f),floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP)
            cachedW = w; cachedOY = oy
        }
        fill(Color.WHITE); p.shader = warm; c.drawRect(0f,0f,w.toFloat(),h.toFloat(),p)
        if (f.listening && !reduced) for (i in 0..2) {
            val k = (f.now*0.9+i/3.0)%1; val rr = r*(1.02+0.55*k)
            fill(0xffff7ad0.toInt()); p.style = Paint.Style.STROKE; p.strokeWidth = (2+6*(1-k)).toFloat(); p.alpha = (255*0.35*(1-k)*min(1.0,f.l*6)).toInt(); p.maskFilter = BlurMaskFilter((3+6*k).toFloat(), BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(cx.toFloat(),oy.toFloat(),rr.toFloat(),p)
        }
        if (f.l > 0.001) {
            fill(Color.WHITE); p.shader = SweepGradient(cx.toFloat(),oy.toFloat(),colors,null); p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.strokeCap = Paint.Cap.ROUND; p.alpha = ((1-VoiceOrbScannerMotion.smooth(sp*3))*255).toInt()
            val rr = r+22; rect.set((cx-rr).toFloat(),(oy-rr).toFloat(),(cx+rr).toFloat(),(oy+rr).toFloat()); c.drawArc(rect,-90f,(360*f.l).toFloat(),false,p)
        }
        val orbR = r*(1-0.14*sp)
        val vo = if (reduced) 0.0 else if (f.listening) 0.55+0.45*abs(sin(f.now*5.3)*sin(f.now*2.1+1)) else 0.12
        val droplets = (0..2).map { i ->
            val k = if (reduced) sp else VoiceOrbScannerMotion.ease((sp-i*0.08)/0.84)
            doubleArrayOf(VoiceOrbScannerMotion.mix(cx+(i-1)*r*0.25,24+i*(cw+8)+26,k),VoiceOrbScannerMotion.mix(oy+r*0.3,cy+28,k)-sin(k*PI)*60,if (k <= 0.001) 0.0 else VoiceOrbScannerMotion.mix(r*0.42,17.0,VoiceOrbScannerMotion.smooth(k*1.2)))
        }
        fill(Color.WHITE)
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
            p.shader = runtime.shader(2*PI*(if (f.useLoopPhase) f.t else f.now)/9,cx,oy,orbR,vo,droplets,f.listening)
            c.drawRect(0f,(top+40).toFloat(),w.toFloat(),h.toFloat(),p)
        } else {
            p.shader = RadialGradient(cx.toFloat(),(oy+orbR*0.28).toFloat(),orbR.toFloat(),intArrayOf(0xffffe0af.toInt(),0xffff8f3d.toInt(),0xffe746a8.toInt(),0xff572472.toInt()),null,Shader.TileMode.CLAMP)
            c.drawCircle(cx.toFloat(),oy.toFloat(),orbR.toFloat(),p)
            for (d in droplets) if (d[2] > 0) c.drawCircle(d[0].toFloat(),d[1].toFloat(),d[2].toFloat(),p)
        }
        c.save()
        if (pressed) { c.scale(0.98f,0.98f,cx.toFloat(),(h-bottom-56).toFloat()) }
        box(c,20.0,h-bottom-94,w-40,76.0,22.0,0xffd9c9ff.toInt())
        val ty = h-bottom-94+16
        val lh = label(c,"Your voice, your playlist",title,18.0,38.0,ty,0xff1b1326.toInt())
        label(c,"Songs picked for your range · tap to scan again",regular,12.5,38.0,ty+lh+3,0xa61b1326.toInt())
        c.restore(); c.restore()
    }
}

@RequiresApi(33)
internal class VoiceOrbScannerRuntime {
    private val orb = RuntimeShader(VoiceOrbScannerShaders.ORB)
    fun shader(t: Double, x: Double, y: Double, r: Double, v: Double, droplets: List<DoubleArray>, listening: Boolean): Shader {
        orb.setFloatUniform("t",t.toFloat()); orb.setFloatUniform("orb",x.toFloat(),y.toFloat(),r.toFloat(),v.toFloat())
        for (i in 0..2) { val d = droplets[i]; orb.setFloatUniform("d$i",d[0].toFloat(),d[1].toFloat(),d[2].toFloat(),0f) }
        orb.setFloatUniform("glow",if (listening) 1f else 0f)
        return orb
    }
}
