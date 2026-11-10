package com.ngocdevv.nativecook.blocks.media.glassaperturecamera

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class GlassApertureCameraArt(context: Context) {
    private val bitmap=context.assets.open("glassaperturecamera/glassaperturecamera_street.jpg").use { BitmapFactory.decodeStream(it)!! }
    private val shader=if(Build.VERSION.SDK_INT>=33) GlassApertureCameraShader(bitmap) else null
    private val regular=Typeface.create("sans-serif",Typeface.NORMAL)
    private val bold=Typeface.create("sans-serif",Typeface.BOLD)
    private val star=Path().apply { for(i in 0..9){val a=-PI/2+i*PI/5;val r=if(i%2==0)9f else 4f;val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r;if(i==0)moveTo(x,y) else lineTo(x,y)};close() }
    private val pen=Path().apply {moveTo(-6f,7f);lineTo(-5f,3f);lineTo(4f,-6f);lineTo(7f,-3f);lineTo(-2f,6f);close()}
    private val bolt=Path().apply {moveTo(2f,-8f);lineTo(-4f,1f);lineTo(0f,1f);lineTo(-1f,8f);lineTo(5f,-2f);lineTo(1f,-2f);close()}
    private val check=Path().apply {moveTo(-3.4f,.3f);lineTo(-1f,2.6f);lineTo(3.4f,-2.2f)}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=regular;textAlign=Paint.Align.CENTER;isSubpixelText=true }
    private fun paint(c:Int,alpha:Double=1.0,stroke:Float=0f): Paint {p.reset();p.isAntiAlias=true;p.isFilterBitmap=true;p.color=c;p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt();p.style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL;p.strokeWidth=stroke;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;return p}
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,weight:Boolean=false,alpha:Double=1.0){ text.typeface=if(weight)bold else regular;text.textSize=size;text.color=Color.WHITE;text.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt(); c.drawText(s,x,y-(text.ascent()+text.descent())/2,text) }
    fun draw(c:Canvas,w:Float,h:Float,top:Float,bottom:Float,f:GlassApertureCameraMotion.Frame,ringX:Float,ringY:Float,automatic:Boolean,reduced:Boolean,time:Double){
        val sx=w/402f; val k=(h-top-bottom)/778f; fun y(v:Float)=if(v<62) v*top/62 else top+(v-62)*k
        val vh=y(770f); val ph=w*2622f/1206f; val ix=201*sx;val iy=y(360f);val ir=128*sx; val sy=y(710f)
        c.drawColor(Color.BLACK); c.save();c.clipRect(0f,0f,w,vh)
        c.drawBitmap(bitmap, null, RectF(0f, 0f, w, ph), paint(Color.WHITE))
        if (shader != null && Build.VERSION.SDK_INT >= 33) {
            val q = paint(Color.WHITE)
            q.shader = shader.at(w, ph, f, ix, iy, ir)
            // Draw in viewfinder points without vertically rescaling the photo or iris.
            c.drawRect(0f, 0f, w, vh, q)
            q.shader = null
        }
        c.restore()
        if(f.iris>.001){c.drawRoundRect(ix-34,iy-ir-44+(1-f.iris).toFloat()*6,ix+34,iy-ir-14+(1-f.iris).toFloat()*6,15f,15f,paint(0xff0c0c0c.toInt(),.66*f.iris));val label=if(f.fnum<9.95)String.format(java.util.Locale.US,"%.1f",f.fnum) else "${f.fnum.roundToInt()}";label(c,"ƒ$label",ix,iy-ir-29+(1-f.iris).toFloat()*6,14f,true,f.iris)}
        val toolsY=y(607f);c.save();c.translate(48*sx,toolsY);c.drawPath(star,paint(0xff1fd3f2.toInt()));c.restore()
        c.drawCircle(148*sx,toolsY,4.5f,paint(Color.WHITE));for(row in 0..2)for(col in 0..2)c.drawRoundRect(240*sx+col*5,toolsY-7+row*5,243*sx+col*5,toolsY-4+row*5,.6f,.6f,paint(Color.WHITE));label(c,"5",263*sx,toolsY,13f,true)
        c.save();c.translate(349*sx,toolsY);c.drawPath(pen,paint(Color.WHITE));c.restore()
        c.drawCircle(82*sx,sy,20f,paint(0xff101010.toInt(),.72));c.drawCircle(316*sx,sy,20f,paint(0xff101010.toInt(),.72));c.drawCircle(199*sx,sy,26f,paint(0xfff4f4f2.toInt()))
        c.save();c.translate(82*sx,sy);c.drawPath(bolt,paint(Color.WHITE));c.restore(); c.drawArc(310*sx,sy-6,322*sx,sy+6,-80f,310f,false,paint(Color.WHITE,1.0,1.6f));c.drawLine(319*sx,sy-8,322*sx,sy-6,paint(Color.WHITE,1.0,1.6f));c.drawLine(322*sx,sy-6,319*sx,sy-3,paint(Color.WHITE,1.0,1.6f))
        val shot=GlassApertureCameraMotion.shotAt(f.shot)
        val alpha=if(f.shot<0)0.0 else if(reduced)if(f.shot<GlassApertureCameraMotion.Shot.GO)1.0 else 1-((f.shot-GlassApertureCameraMotion.Shot.GO)/.25).coerceIn(0.0,1.0) else shot.alpha
        if(alpha>0){
            val st=1+.14*abs(shot.stretch)
            val ty=if(reduced)0f else ((vh+GlassApertureCameraMotion.Shot.K_TO*vh/2+6-vh/2)*shot.y).toFloat()
            c.save();c.translate(w/2,vh/2+ty)
            if(!reduced){c.rotate(shot.rot.toFloat());c.scale((shot.k/sqrt(st)).toFloat(),(shot.k*st).toFloat())}
            c.translate(-w/2,-vh/2)
            c.saveLayerAlpha(0f,0f,w,vh,(alpha*255).roundToInt())
            val clip=Path().apply{addRoundRect(RectF(0f,0f,w,vh),18f,18f,Path.Direction.CW)}
            c.clipPath(clip)
            c.drawBitmap(bitmap,null,RectF(7f,7f,w+7,ph+7),paint(Color.WHITE))
            c.drawRoundRect(3.5f,3.5f,w-3.5f,vh-3.5f,14.5f,14.5f,paint(Color.WHITE,.92,7f))
            c.restore();c.restore()
        }
        // The footer covers the print as it lands in the slot.
        c.drawRect(0f,vh,w,h,paint(0xff161616.toInt()))
        val bumpY=if(reduced || f.shot<0)0f else (5*shot.bump).toFloat()
        c.save();c.translate(0f,bumpY);val restScale=1-.012f*bumpY;c.scale(restScale,restScale,w/2,vh+35)
        label(c,"Friends @mira",w/2,vh+24,13f,true,1-f.sent);label(c,"Sharable with other apps",w/2,vh+45.5f,12f,false,(1-f.sent)*.62);c.restore()
        if(f.sent>.5){ val cy=vh+35+(1-f.sent).toFloat()*6+bumpY; c.drawCircle(w/2-58,cy,7f,paint(0xff1fd3f2.toInt(),f.sent));c.save();c.translate(w/2-58,cy);c.drawPath(check,paint(0xff0b0b0b.toInt(),f.sent,1.7f));c.restore();label(c,"Sent to 4 friends",w/2+9,cy,13f,true,f.sent) }
        if(f.ring>=0){val rx=if(automatic)if(time%10.0>=7.0) .75f*w else .36f*w else ringX;val ry=if(automatic)if(rx>.5*w) .64f*ph else .6f*ph else ringY;val a=if(f.ring<.9).95 else .95*(1-(f.ring-.9)/.3);c.drawCircle(rx,ry,(46-18*min(1.0,f.ring/.25)).toFloat(),paint(0xffffd65a.toInt(),a,1.5f))}
        if(f.finger>0){val x=when(f.fingerAt){1->.36f*w;3->.75f*w;2->199*sx;else->ix-f.spread.toFloat()/2};val yy=when(f.fingerAt){1->.6f*ph;3->.64f*ph;2->sy;else->iy+f.spread.toFloat()*.35f};val r=(22*(1-.12*f.press)).toFloat();fun finger(xp:Float,yp:Float){c.drawCircle(xp,yp,r,paint(Color.WHITE,.12*f.finger));c.drawCircle(xp,yp,r,paint(Color.WHITE,.6*f.finger,1.5f))};finger(x,yy);if(f.fingerAt==0)finger(ix+f.spread.toFloat()/2,iy-f.spread.toFloat()*.35f)}
    }
}
