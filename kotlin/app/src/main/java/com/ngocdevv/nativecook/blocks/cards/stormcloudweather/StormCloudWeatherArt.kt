package com.ngocdevv.nativecook.blocks.cards.stormcloudweather

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class StormCloudWeatherArt(private val r:StormCloudWeatherResources) {
    private val shader = if(Build.VERSION.SDK_INT>=33) StormCloudWeatherShaderRenderer() else null
    private val rect=RectF()
    private val panelPath=Path()
    private val moonPath=Path()
    private val moonCut=Path()
    private fun paint(color:Int,alpha:Float=1f):Paint=r.paint.apply{reset();isAntiAlias=true;this.color=color;this.alpha=(alpha*255).roundToInt()}
    private fun round(c:Canvas,x:Float,y:Float,w:Float,h:Float,radius:Float,color:Int,alpha:Float=1f){c.drawRoundRect(x,y,x+w,y+h,radius,radius,paint(color,alpha))}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,face:Typeface,alpha:Float=1f,center:Boolean=false){
        val p=r.textPaint;p.color=android.graphics.Color.WHITE;p.alpha=(alpha*255).roundToInt();p.textSize=size;p.typeface=face;p.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s,x,y-p.fontMetrics.ascent,p)
    }
    fun draw(c:Canvas,w:Float,h:Float,top:Float,bottom:Float,f:StormCloudWeatherMotion.Frame,pressed:Int){
        val k=f.weather;val sun=k.sun;val rain=k.rain*(1-k.bolt)
        fun rgb(a:DoubleArray,b:DoubleArray,d:DoubleArray):Int {
            val v=IntArray(3){(StormCloudWeatherMotion.mix(StormCloudWeatherMotion.mix(a[it],b[it],rain),d[it],sun)*255).roundToInt()}
            return Color.rgb(v[0],v[1],v[2])
        }
        val a=rgb(doubleArrayOf(.05,.1,.16),doubleArrayOf(.14,.2,.27),doubleArrayOf(.16,.3,.48))
        val b=rgb(doubleArrayOf(.03,.06,.1),doubleArrayOf(.06,.1,.15),doubleArrayOf(.05,.11,.2))
        val p=paint(Color.WHITE);p.shader=LinearGradient(0f,0f,0f,700f,a,b,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
        if(sun>.01){val gp=paint(Color.WHITE,sun.toFloat());gp.shader=RadialGradient(w*.75f,150f,w*.8f,intArrayOf(0x6bffaa50,0x00ff8c3c),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,gp)}
        for(i in 0..4){val ph=i*.27+f.phase/9*(.6+i*.1);val x=((ph-floor(ph))*(w+300)-150).toFloat();c.drawBitmap(r.banks[i%2],x-220,120f+i*70-120,paint(Color.WHITE,(.13*(1-.65*sun)).toFloat()))}
        if(f.to==0&&f.flash>0)c.drawRect(0f,0f,w,h,paint(0xffdbe6ff.toInt(),(.08*f.flash).toFloat()))
        text(c,"Porto, Portugal",w/2,top+14,27f,r.title,center=true)
        text(c,"Today 31 Oct, 2026 · ${StormCloudWeatherMotion.copy[f.to].sky}",w/2,top+54,14f,r.regular,.75f,true)
        val sy=top+78
        scene(c,0f,sy,w,350f,w/2,350*.47f,min(128f,w*.32f),f.yaw,f.time,k,if(f.to==0)f.flash else 0.0)
        val values=listOf("Wind" to "${f.number{it.wind}}km/h","Temp" to "${f.number{it.temp}}°","Humidity" to "${f.number{it.humidity}}%")
        values.forEachIndexed{i,(label,value)->val x=when(i){0->75f;1->w/2;else->w-75};text(c,label,x,sy+344,15f,r.regular,.72f,true);text(c,value,x,sy+366,25f,r.semibold,center=true)}
        val py=sy+430
        panelPath.reset();rect.set(0f,py,w,h+34);panelPath.addRoundRect(rect,floatArrayOf(34f,34f,34f,34f,0f,0f,0f,0f),Path.Direction.CW);c.drawPath(panelPath,paint(0xff0a1621.toInt()))
        text(c,"Today",24f,py+20,17f,r.semibold)
        val np=r.textPaint;np.textSize=14f;np.typeface=r.regular
        text(c,"Next Week ⌄",w-24-np.measureText("Next Week ⌄"),py+22,14f,r.regular,.5f)
        val cw=(w-68)/3
        for(i in 0..2){
            val on=f.to==i;val cp=StormCloudWeatherMotion.copy[i];val x=24+i*(cw+10);val y=py+57+if(on&&pressed!=i)-6 else 0
            c.save();c.translate(x,y)
            if(pressed==i){c.translate(cw/2,52f);c.scale(.97f,.97f);c.translate(-cw/2,-52f)}
            val layer=c.saveLayerAlpha(0f,0f,cw,104f,if(on)255 else 209)
            round(c,0f,0f,cw,104f,18f,if(on)0xfff5a21f.toInt() else 0xffe8861a.toInt())
            val pad=if(on)14f else 12f
            text(c,cp.label,pad,104-pad-47,15f,r.regular);text(c,"${cp.temp}°",pad,104-pad-29,24f,r.semibold)
            scene(c,cw-62-if(on)2 else 0,if(on)8f else 6f,56f,56f,28f,30f,21f,-.2,.6,StormCloudWeatherMotion.states[i],0.0)
            if(on){val bp=paint(Color.WHITE,.75f);bp.style=Paint.Style.STROKE;bp.strokeWidth=2f;c.drawRoundRect(1f,1f,cw-1,103f,17f,17f,bp)}
            c.restoreToCount(layer);c.restore()
        }
        val ty=h-bottom-66;round(c,20f,ty,w-40,60f,24f,0xff13263a.toInt())
        for(i in 0..3){c.save();c.translate(20+(w-40)/4*(i+.5f)-14,ty+16);c.scale(26f/24,26f/24);val tp=paint(Color.WHITE,if(i==0)1f else .45f);tp.style=Paint.Style.STROKE;tp.strokeWidth=1.9f;tp.strokeCap=Paint.Cap.ROUND;tp.strokeJoin=Paint.Join.ROUND;c.drawPath(r.tabs[i],tp);c.restore()}
    }
    private fun scene(c:Canvas,x:Float,y:Float,w:Float,h:Float,cx:Float,cy:Float,s:Float,yaw:Double,t:Double,k:StormCloudWeatherMotion.Weather,flash:Double){
        c.save();c.translate(x,y);c.clipRect(0f,0f,w,h)
        if(Build.VERSION.SDK_INT>=33&&shader!=null){shader.draw(c,w,h,cx,cy,s,yaw,t,k,flash)}else{
            // API 26–32: drawn clay with the same yaw driver and a planar turn fallback.
            c.translate(cx,cy);c.scale(cos(yaw).toFloat(),1f);c.translate(-cx,-cy)
            if(k.moon>.01){
                val radius=(s*.42*k.moon).toFloat()
                moonPath.reset();moonCut.reset()
                moonPath.addCircle(cx+s*.52f,cy-s*.95f,radius,Path.Direction.CW)
                moonCut.addCircle(cx+s*(.52+.2*k.moon).toFloat(),cy-s*(.95+.12*k.moon).toFloat(),radius*.84f,Path.Direction.CW)
                moonPath.op(moonCut,Path.Op.DIFFERENCE)
                c.drawPath(moonPath,paint(0xff7562df.toInt()))
            }
            if(k.sun>.01)c.drawCircle(cx+s*.62f,cy-s*.72f,(s*.42*k.sun).toFloat(),paint(0xffffbf40.toInt()))
            val cols=k.cloud;val color=Color.rgb((cols[0]*255).toInt(),(cols[1]*255).toInt(),(cols[2]*255).toInt())
            for(q in arrayOf(floatArrayOf(-.62f,-.05f,.48f),floatArrayOf(-.18f,.28f,.62f),floatArrayOf(.42f,.12f,.55f),floatArrayOf(.86f,-.12f,.4f),floatArrayOf(.1f,-.28f,.5f))){val p=paint(color);p.shader=RadialGradient(cx+q[0]*s-q[2]*s*.4f,cy-q[1]*s-q[2]*s*.4f,q[2]*s*2,intArrayOf(Color.rgb(min(255,Color.red(color)+65),min(255,Color.green(color)+65),min(255,Color.blue(color)+65)),color),null,Shader.TileMode.CLAMP);c.drawCircle(cx+q[0]*s,cy-q[1]*s,q[2]*s,p)}
            if(k.bolt>.01){val p=Path();p.moveTo(cx+s*.12f,cy+s*.52f);p.lineTo(cx-s*.12f,cy+s*.92f);p.lineTo(cx+s*.18f,cy+s*.96f);p.lineTo(cx-s*.04f,cy+s*1.52f);val bp=paint(0xffffc12a.toInt());bp.style=Paint.Style.STROKE;bp.strokeWidth=(s*.12*k.bolt).toFloat();bp.strokeJoin=Paint.Join.ROUND;c.drawPath(p,bp)}
            if(k.rain>.01)for(i in 0..3){val phase=(t*.7777778+i*.37)%1;val x0=cx+s*(-.62f+i*.42f);val y0=cy+s*(.6+phase*.75).toFloat();val r0=(s*.09*k.rain*sin(PI*phase)).toFloat();c.drawOval(x0-r0,y0-r0*1.6f,x0+r0,y0+r0*1.6f,paint(0xff409fff.toInt()))}
        }
        c.restore()
    }
}
@RequiresApi(33)
internal class StormCloudWeatherShaderRenderer {
    private val shader=RuntimeShader(StormCloudWeatherShaders.scene)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.shader=this@StormCloudWeatherShaderRenderer.shader}
    fun draw(c:Canvas,w:Float,h:Float,cx:Float,cy:Float,s:Float,yaw:Double,t:Double,k:StormCloudWeatherMotion.Weather,flash:Double){
        shader.setFloatUniform("c",cx,cy);shader.setFloatUniform("S",s);shader.setFloatUniform("yaw",yaw.toFloat());shader.setFloatUniform("t",t.toFloat())
        shader.setFloatUniform("cloud",k.cloud[0].toFloat(),k.cloud[1].toFloat(),k.cloud[2].toFloat())
        for((name,v)in listOf("moon" to k.moon,"sun" to k.sun,"bolt" to k.bolt,"rain" to k.rain,"flash" to flash,"puff" to k.puff))shader.setFloatUniform(name,v.toFloat())
        c.drawRect(0f,0f,w,h,paint)
    }
}
