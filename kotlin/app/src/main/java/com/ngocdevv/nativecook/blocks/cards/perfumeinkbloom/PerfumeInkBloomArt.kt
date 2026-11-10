package com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom.PerfumeInkBloomMotion as M

@RequiresApi(33)
internal class PerfumeInkBloomRuntime {
    private val shader=RuntimeShader(PerfumeInkBloomShader.source)
    fun update(l: M.Layout,s: M.Frame): Shader {
        shader.setFloatUniform("Fbottle",l.cx,l.cy,l.sx,s.tilt.toFloat())
        shader.setFloatUniform("Fliquid",s.slosh.toFloat(),s.press.toFloat(),.375f,0f)
        return shader
    }
}

internal class PerfumeInkBloomArt(context: Context) {
    private val runtime=if(Build.VERSION.SDK_INT>=33)PerfumeInkBloomRuntime() else null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val softMist=RadialGradient(0f,0f,1.4f,IntArray(17) { i ->
        val r=i*1.4/16
        val k=exp(-r*r*2.2)*(1-smooth(.9,1.4,r))
        Color.argb((255*k).toInt(),140,194,250)
    },FloatArray(17) { it/16f },Shader.TileMode.CLAMP)
    private val faces=listOf("Regular","SemiBold","Bold").associateWith { Typeface.createFromAsset(context.assets,"perfumeinkbloom/perfumeinkbloom_Nunito-$it.ttf") }
    private var layout: M.Layout?=null
    private var page: Bitmap?=null
    private var goals=emptyArray<PointF>()
    private val count=1536
    // The same static Skia glyph samples as Swift; only the first 1536 droplets are drawn here.
    private val notePoints=ByteBuffer.wrap(context.assets.open("perfumeinkbloom/perfumeinkbloom_note-points.bin").use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    private val hashes=Array(count) { i -> DoubleArray(5) { j -> hash((i+j+1).toDouble()) } }
    private val back=Path().apply { moveTo(9f,2f); lineTo(2f,9f); lineTo(9f,16f); moveTo(2.6f,9f); lineTo(20.5f,9f) }
    private val bag=Path().apply { moveTo(3.5f,7.5f); lineTo(16.5f,7.5f); lineTo(16.5f,21f); lineTo(3.5f,21f); close(); moveTo(6.8f,7.5f); lineTo(6.8f,5.6f); arcTo(6.8f,2.4f,13.2f,8.8f,180f,180f,false); lineTo(13.2f,7.5f) }
    private val heart=Path().apply { moveTo(11f,18.2f); cubicTo(4f,13.4f,1.6f,10.2f,1.6f,6.6f); cubicTo(1.6f,.8f,8.7f,.1f,11f,4.6f); cubicTo(13.3f,.1f,20.4f,.8f,20.4f,6.6f); cubicTo(20.4f,10.2f,18f,13.4f,11f,18.2f); close() }
    private fun hash(n: Double): Double { val v=sin(n*91.3458)*47453.5453; return v-floor(v) }
    private fun smooth(a: Double,b: Double,v: Double): Double { val x=((v-a)/(b-a)).coerceIn(0.0,1.0); return x*x*(3-2*x) }
    private fun solid(color: Int,stroke: Float=0f) {
        paint.shader=null; paint.color=color; paint.alpha=Color.alpha(color)
        paint.style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth=stroke; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
    }
    private fun text(c: Canvas,value: String,x: Float,top: Float,size: Float,weight: String,color: Int,width: Float=10000f,line: Float?=null) {
        solid(color); paint.typeface=faces[weight]; paint.textSize=size; paint.textAlign=Paint.Align.LEFT
        val step=line?:paint.fontSpacing
        var row=""; var y=top-paint.fontMetrics.ascent+(if(line!=null)(step-paint.fontSpacing)/2 else 0f)
        for(word in value.split(' ')) {
            val candidate=if(row.isEmpty())word else "$row $word"
            if(row.isNotEmpty()&&paint.measureText(candidate)>width) { c.drawText(row,x,y,paint); y+=step; row=word } else row=candidate
        }
        c.drawText(row,x,y,paint)
    }
    private fun prepare(l: M.Layout) {
        if(layout==l)return
        layout=l; page?.recycle()
        page=Bitmap.createBitmap(ceil(l.w*3).toInt(),ceil(l.h*3).toInt(),Bitmap.Config.ARGB_8888).also { bitmap ->
            val c=Canvas(bitmap); c.scale(3f,3f)
            text(c,M.NAME,32*l.sx,l.y(476f)-21,30f,"SemiBold",0xFF0B0B0F.toInt())
            text(c,"Eau de Parfum",32*l.sx,l.y(513f)-14,19.5f,"Regular",0xFF0B0B0F.toInt())
            text(c,"Reviews",32*l.sx,l.y(568f)-12,16f,"SemiBold",0xFF4B4B55.toInt())
            text(c,"Price",288*l.sx,l.y(567f)-12,16f,"SemiBold",0xFF4B4B55.toInt())
            text(c,"$148",288*l.sx,l.y(603f)-15,22f,"Bold",0xFF0B0B0F.toInt())
            text(c,M.BODY,32*l.sx,l.y(657f)-12.75f,13.2f,"Regular",0xFF8C8C96.toInt(),312*l.sx,25.5f)
            val star=floatArrayOf(12f,1.8f,15.1f,8.3f,22.2f,9.2f,17f,14.1f,18.3f,21.2f,12f,17.8f,5.7f,21.2f,7f,14.1f,1.8f,9.2f,8.9f,8.3f)
            repeat(5) { i -> val p=Path(); for(j in 0..9) { val x=32*l.sx+i*28+star[j*2]*21/24; val y=l.y(603f)-10.5f+star[j*2+1]*21/24; if(j==0)p.moveTo(x,y) else p.lineTo(x,y) }; p.close(); solid(if(i<4)0xFFFF9C1A.toInt() else 0xFFE6E6EC.toInt()); c.drawPath(p,paint) }
            c.save(); c.translate(39*l.sx-11,l.y(83f)-9); solid(0xFF0B0B0F.toInt(),1.9f); c.drawPath(back,paint); c.restore()
            c.save(); c.translate(358*l.sx-11,l.y(85f)-12); solid(0xFF0B0B0F.toInt(),1.7f); c.drawPath(bag,paint); solid(0xFF0A5CFF.toInt()); c.drawCircle(17f,4.5f,3.6f,paint); c.restore()
            solid(0xFF8F96BC.toInt(),1.2f); c.drawRoundRect(31*l.sx+.6f,l.y(754f)+.6f,108*l.sx-.6f,l.y(754f)+76*l.k-.6f,19.4f,19.4f,paint)
            c.save(); c.translate(69.5f*l.sx-11,l.y(754f)+38*l.k-10); solid(0xFF8F96BC.toInt(),1.6f); c.drawPath(heart,paint); c.restore()
            solid(0xFF0A5CFF.toInt()); c.drawRoundRect(135*l.sx,l.y(754f),370*l.sx,l.y(754f)+76*l.k,22f,22f,paint)
            paint.typeface=faces["SemiBold"]; paint.textSize=19f; solid(Color.WHITE)
            val cy=l.y(754f)+38*l.k-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
            c.drawText("Buy now",252.5f*l.sx-paint.measureText("Buy now")/2,cy,paint)
        }
        goals=Array(count) { i ->
            val x=notePoints.get(i*2); val y=notePoints.get(i*2+1)
            val row=floor((y-322)/33+.5f)
            PointF(x*l.sx,l.y(322f)+row*33+(y-322-row*33)*l.sx)
        }
    }
    private fun mist(c: Canvas,l: M.Layout,s: M.Frame) {
        if(s.age<0)return
        val lean=.375+s.tilt; val co=cos(lean); val si=sin(lean)
        val localY=cos(.28)*(164-s.press*7)-sin(.28)*20
        val nx=l.cx+(co*30+si*localY)*l.sx; val ny=l.cy-(-si*30+co*localY)*l.sx
        var dx=co*.55+si*cos(.28)*.84; var dy=-(-si*.55+co*cos(.28)*.84)
        val length=hypot(dx,dy); dx/=length; dy/=length
        repeat(count) { i ->
            val h=hashes[i]; val tau=s.age-h[0]*.42
            if(tau<0)return@repeat
            val a=(h[1]-.5)*.7+(h[2]-.5)*.3
            val speed=(130+150*h[2])*l.sx
            val dist=speed*(1-exp(-2.6*tau))/2.6
            var x=nx+(cos(a)*dx-sin(a)*dy)*dist
            var y=ny+(sin(a)*dx+cos(a)*dy)*dist
            val sw=min(tau,2.0)
            x+=sin(tau*(1.3+h[3])+h[4]*2*PI)*16*sw*l.sx
            y+=(cos(tau*(1.1+h[4])+h[3]*2*PI)*16*sw-10*tau)*l.sx
            var r=(1.2+3.2*min(tau/1.2,1.0)*(.5+h[3]))*l.sx
            var alpha=smooth(0.0,.05,tau)*(1-.55*smooth(.4,2.0,tau))
            val cc=smooth(0.0,1.0,(s.condense-h[0]*.35)/.65)
            x+=(goals[i].x-x)*cc; y+=(goals[i].y-y)*cc; r+=(.95*l.sx-r)*cc; alpha+=(1-alpha)*cc
            val e=smooth(0.0,1.0,(s.evaporate-h[1]*.4)/.6)
            x+=(sin(h[3]*2*PI)*50*e+sin(e*3+h[4]*2*PI)*12*e)*l.sx
            y+=-90*(.6+h[2])*e*l.sx; r+=1.6*e*l.sx
            alpha*=(1-e)*(1-e)
            val tint=cc*(1-e)
            // The full compute cloud has four times as many points; preserve its optical density.
            solid(Color.WHITE); paint.shader=softMist
            paint.alpha=(255*(.16*alpha*(1-tint)*4).coerceIn(0.0,1.0)).toInt()
            c.save(); c.translate(x.toFloat(),y.toFloat()); c.scale(r.toFloat(),r.toFloat()); c.drawCircle(0f,0f,1.4f,paint); c.restore()
            solid(Color.argb((.85*alpha*tint*255).toInt(),((.55+(.11-.55)*tint)*255).toInt(),((.76+(.39-.76)*tint)*255).toInt(),((.98+(.95-.98)*tint)*255).toInt()))
            c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint)
        }
    }
    fun draw(c: Canvas,l: M.Layout,s: M.Frame) {
        prepare(l); c.drawColor(Color.WHITE)
        if(Build.VERSION.SDK_INT>=33&&runtime!=null) { solid(Color.WHITE); paint.shader=runtime.update(l,s); c.drawRect(0f,0f,l.w,l.h,paint); paint.shader=null; mist(c,l,s) }
        else {
            c.save(); c.translate(l.cx,l.cy); c.rotate(((.375+s.tilt)*180/PI).toFloat())
            solid(0xFFC9CDD6.toInt()); c.drawRoundRect(-33*l.sx,-190*l.sx,33*l.sx,-100*l.sx,12f,12f,paint)
            solid(0xFF5CCBF2.toInt()); c.drawCircle(0f,0f,100*l.sx,paint); c.restore()
        }
        solid(Color.WHITE); page?.let { c.drawBitmap(it,null,RectF(0f,0f,l.w,l.h),paint) }
        if(s.finger>0) {
            val x=if(s.fingerAt==0)l.capX else l.cx+90*s.fingerU*l.sx
            val y=if(s.fingerAt==0)l.capY+6*s.press else l.cy+20*l.sx
            val r=(22*(1-.12*s.press2)).toFloat()
            solid(Color.argb((.08*s.finger*255).toInt(),20,30,60)); c.drawCircle(x.toFloat(),y.toFloat(),r,paint)
            solid(Color.argb((.3*s.finger*255).toInt(),20,30,60),1.5f); c.drawCircle(x.toFloat(),y.toFloat(),r,paint)
        }
    }
}
