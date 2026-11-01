package com.ngocdevv.nativecook.blocks.gestures.evolveholdmorph

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

@RequiresApi(33)
internal class EvolveHoldMorphRuntime {
    private val shader = RuntimeShader(EvolveHoldMorphShaders.PET)
    fun pet(w: Float, cy: Float, s: Float, density: Float, f: EvolveHoldMorphFrame): Shader {
        shader.setFloatUniform("c", w/2, cy)
        shader.setFloatUniform("S",s)
        shader.setFloatUniform("yaw", f.yaw.toFloat())
        shader.setFloatUniform("fa", f.stage.toFloat())
        shader.setFloatUniform("fb", ((f.stage+1)%3).toFloat())
        shader.setFloatUniform("m", f.m.toFloat())
        shader.setFloatUniform("sq", f.sq.toFloat())
        shader.setFloatUniform("charge", f.c.toFloat())
        shader.setFloatUniform("blink", f.blink.toFloat())
        shader.setFloatUniform("px", 1/(s*density))
        return shader
    }
}
internal class EvolveHoldMorphArt(context: Context) {
    private val bold = Typeface.createFromAsset(context.assets,"evolveholdmorph/evolveholdmorph_Nunito-ExtraBold.ttf")
    private val black = Typeface.createFromAsset(context.assets,"evolveholdmorph/evolveholdmorph_Nunito-Black.ttf")
    private val title = Typeface.createFromAsset(context.assets,"evolveholdmorph/evolveholdmorph_TitanOne-Regular.ttf")
    private val icons = Typeface.createFromAsset(context.assets,"evolveholdmorph/evolveholdmorph_Ionicons.ttf")
    private val runtime = if(Build.VERSION.SDK_INT >= 33) EvolveHoldMorphRuntime() else null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private val button = Path()
    private var background: Bitmap? = null
    private var bgW = 0f; private var bgH = 0f
    private fun fill(hex: Int, alpha: Double = 1.0): Paint {
        p.reset(); p.isAntiAlias = true; p.color = (alpha.coerceIn(0.0,1.0)*255).roundToInt().shl(24) or hex
        return p
    }
    private fun text(c: Canvas, value: String, face: Typeface, size: Float, hex: Int, x: Float, y: Float) {
        val paint = fill(hex); paint.typeface = face; paint.textSize = size
        c.drawText(value,x,y-paint.fontMetrics.ascent,paint)
    }
    private fun width(value: String, face: Typeface, size: Float): Float {
        p.typeface = face; p.textSize = size
        return p.measureText(value)
    }
    private fun line(face: Typeface, size: Float): Float {
        p.typeface = face; p.textSize = size
        return ceil(p.fontMetrics.descent-p.fontMetrics.ascent)
    }
    private fun rr(c: Canvas,x: Float,y: Float,w: Float,h: Float,r: Float,hex: Int) {
        c.drawRoundRect(x,y,x+w,y+h,r,r,fill(hex))
    }
    private fun makeBackground(w: Float, h: Float, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(ceil(w*density).toInt(),ceil(h*density).toInt(),Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap); c.scale(density,density)
        val paint = fill(0xffffff)
        paint.shader = LinearGradient(0f,0f,0f,h,intArrayOf(0xffddf3e8.toInt(),0xfff3faf2.toInt(),0xfffff7ea.toInt()),floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint)
        val cy = round(h*0.43f); val s = round(w*0.42f); val y = cy+s*0.62f
        c.drawOval(w/2-s*0.62f,y-s*0.05f,w/2+s*0.62f,y+s*0.17f,fill(0x2c5a44,0.14))
        for(i in 0..8) {
            val a = -0.15f+i*0.04f
            val paint2 = fill(if(i%2==1)0xc98e52 else 0xb57a40)
            paint2.style = Paint.Style.STROKE; paint2.strokeWidth = 5f; paint2.strokeCap = Paint.Cap.ROUND
            val x = w/2-s*(0.56f-a*0.2f); val yy = y-s*0.12f+i*1.6f
            arc.set(x,yy,x+s*(1.12f-a*0.4f),yy+s*0.26f)
            c.drawArc(arc,(10+i*4).toFloat(),(160-i*6).toFloat(),false,paint2)
        }
        return bitmap
    }
    fun draw(c: Canvas,w: Float,h: Float,density: Float,top: Float,bottom: Float,f: EvolveHoldMorphFrame) {
        val cy = round(h*0.43f); val s = round(w*0.42f)
        if(background==null || bgW!=w || bgH!=h) { background?.recycle(); background=makeBackground(w,h,density);bgW=w;bgH=h }
        c.drawBitmap(background!!,null,RectF(0f,0f,w,h),fill(0xffffff))
        c.save(); c.translate(0f,-f.hop.toFloat())
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
            val paint = fill(0xffffff); paint.shader=runtime.pet(w,cy,s,density,f)
            c.drawRect(0f,cy-s*1.2f,w,cy+s*1.1f,paint)
        } else {
            // Older Android keeps the same pose and charge, with a simple clay silhouette.
            val stage=if(f.m>0.5)(f.stage+1)%3 else f.stage
            c.save(); c.translate(w/2,cy); c.scale((1+f.sq*0.5).toFloat(),(1-f.sq).toFloat())
            val color=when(stage){0->0xffedc7;1->0xffdb4d;else->0x42a8f5}
            c.drawOval(-s*0.52f,-s*0.7f,s*0.52f,s*0.65f,fill(color))
            if(stage>0){
                c.drawCircle(0f,-s*0.42f,s*0.32f,fill(color))
                for(sign in listOf(-1,1)) c.drawCircle(sign*s*0.13f,-s*0.48f,s*0.045f,fill(0x201a24))
                c.drawOval(-s*0.09f,-s*0.36f,s*0.09f,-s*0.25f,fill(0xff8f2e))
            }; c.restore()
        }; c.restore()
        if(f.c>0.001){
            val r=s*0.82f;arc.set(w/2-r,cy-r-4,w/2+r,cy+r-4)
            val paint=fill(0xffb53b,0.25+0.75*(f.c*3).coerceIn(0.0,1.0));paint.style=Paint.Style.STROKE;paint.strokeWidth=6f;paint.strokeCap=Paint.Cap.ROUND
            c.drawArc(arc,-90f,(360*f.c).toFloat(),false,paint)
        }
        if(f.burst>=0 && f.burst<0.9){
            val k=f.burst/0.9
            repeat(14){i->
                val a=i/14.0*PI*2+0.3;val d=s*(0.45+0.75*(1-(1-k).pow(3)))
                c.drawCircle((w/2+cos(a)*d).toFloat(),(cy-4+sin(a)*d*0.9).toFloat(),((if(i%3==0)7.0 else 4.5)*(1-k*0.6)).toFloat(),fill(if(i%2==1)0xffd34d else 0xff8fb1,1-k))
            }
        }
        val smallH=line(bold,14f); val titleH=line(title,26f)
        text(c,"Your pet",bold,14f,0x6e8a7c,20f,top+8)
        text(c,"Pip the ${EvolveHoldMorphMotion.names[f.stage]}",title,26f,0x22362c,20f,top+10+smallH)
        val lv="Lv ${4+f.stage}"; val lvW=width(lv,black,14f);val ly=top+8+(smallH+2+titleH-34)/2
        rr(c,w-20-44-lvW,ly,44+lvW,34f,17f,0xffffff)
        text(c,"\uf3b2",icons,14f,0x2f9e62,w-20-44-lvW+12,ly+(34-line(icons,14f))/2)
        text(c,lv,black,14f,0x22362c,w-20-lvW-12,ly+(34-line(black,14f))/2)
        val widths=EvolveHoldMorphMotion.names.map {width(it,bold,13f)}
        var x=(w-widths.sum()-56)/2
        repeat(3){i->
            c.drawCircle(x+widths[i]/2,cy+s*0.9f+5,5f,fill(if(i<=f.stage)0x2f9e62 else 0xc9dcd1))
            text(c,EvolveHoldMorphMotion.names[i],bold,13f,if(i==f.stage)0x22362c else 0x8aa396,x,cy+s*0.9f+16)
            x+=widths[i]+28
        }
        val cardY=h-bottom-164
        rr(c,20f,cardY,w-40,64f,20f,0xffffff)
        rr(c,34f,cardY+14,36f,36f,12f,0xfff2d6)
        val iw=width("\uf316",icons,18f)
        text(c,"\uf316",icons,18f,0xf2a516,52-iw/2,cardY+(64-line(icons,18f))/2)
        val ch=line(black,14f);val nw=width("120/120",black,14f)
        text(c,"Evolution energy",black,14f,0x22362c,82f,cardY+(64-ch-12)/2)
        rr(c,82f,cardY+(64-ch-12)/2+ch+6,w-34-nw-12-82,6f,3f,0xf2b63a)
        text(c,"120/120",black,14f,0x8a6a1e,w-34-nw,cardY+(64-ch)/2)
        val by=h-bottom-80
        c.save()
        if(f.hold)c.scale(0.97f,0.97f,w/2,by+30)
        button.reset();button.addRoundRect(20f,by,w-20,by+60,30f,30f,Path.Direction.CW);c.clipPath(button)
        c.drawColor(0xffffe29a.toInt())
        c.drawRect(20f,by,(20+(w-40)*f.c).toFloat(),by+56,fill(0xffc93d))
        c.drawRect(20f,by+56,w-20,by+60,fill(0xe8b94a))
        val label=EvolveHoldMorphMotion.cta[f.stage]; val tw=width(label,black,18f);val tx=(w-tw-26)/2
        text(c,"\uf58c",icons,18f,0x3b2a06,tx,by+(56-line(icons,18f))/2)
        text(c,label,black,18f,0x3b2a06,tx+26,by+(56-line(black,18f))/2)
        c.restore()
    }
}
