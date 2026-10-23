package com.ngocdevv.nativecook.blocks.gestures.worldsunprojection

import android.graphics.*
import kotlin.math.*

internal class WorldSunProjectionPainter(private val resources:WorldSunProjectionResources){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val normal=Typeface.create("sans-serif",Typeface.NORMAL)
    private val medium=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private val white=0xFAF9F3
    private fun rgb(c:Int)=c or -0x1000000
    private fun fill(color:Int,alpha:Double=1.0){paint.shader=null;paint.style=Paint.Style.FILL;paint.color=rgb(color);paint.alpha=(alpha*255).roundToInt().coerceIn(0,255)}
    private fun stroke(color:Int,width:Double,alpha:Double=1.0){fill(color,alpha);paint.style=Paint.Style.STROKE;paint.strokeWidth=width.toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND}
    private fun line(c:Canvas,x:Double,y:Double,xx:Double,yy:Double,color:Int=white,width:Double=1.0,alpha:Double=1.0){stroke(color,width,alpha);c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),paint)}
    private fun text(c:Canvas,value:String,x:Double,y:Double,size:Double,color:Int=white,bold:Boolean=false,center:Boolean=true,alpha:Double=1.0,tracking:Float=0f){textPaint.typeface=if(bold)medium else normal;textPaint.textSize=size.toFloat();textPaint.color=rgb(color);textPaint.alpha=(alpha*255).roundToInt();textPaint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT;val fm=textPaint.fontMetrics;val baseline=(y-(fm.ascent+fm.descent)/2).toFloat();if(tracking==0f)c.drawText(value,x.toFloat(),baseline,textPaint)else{val widths=FloatArray(value.length);textPaint.getTextWidths(value,widths);var at=if(center)(x-(widths.sum()+tracking*(value.length-1))/2).toFloat()else x.toFloat();textPaint.textAlign=Paint.Align.LEFT;for(i in value.indices){c.drawText(value,i,i+1,at,baseline,textPaint);at+=widths[i]+tracking}}}
    private fun rounded(c:Canvas,x:Double,y:Double,w:Double,h:Double,r:Double,color:Int,alpha:Double=1.0){fill(color,alpha);c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)}
    private fun chip(c:Canvas,x:Double,y:Double,w:Double,h:Double){rounded(c,x,y,w,h,h/2,0x293432,.75);stroke(0xFFFFFF,.6,.10);c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),(h/2).toFloat(),(h/2).toFloat(),paint)}
    private fun projected(directions:List<WorldSunProjectionVector>,p:WorldSunProjectionPose):Path{
        path.reset();val camera=WorldSunProjectionCamera(p);val near=.0600001;var previous:WorldSunProjectionVector?=null
        for(d in directions){previous?.let{a->val z0=a.dot(camera.forward);val z1=d.dot(camera.forward);if(z0>near||z1>near){val denominator=z1-z0;val u=if(abs(denominator)>1e-12)(near-z0)/denominator else 0.0;val cross=a*(1-u)+d*u;val aa=camera.project(if(z0>near)a else cross);val bb=camera.project(if(z1>near)d else cross);if(aa!=null&&bb!=null){path.moveTo(aa.x.toFloat(),aa.y.toFloat());path.lineTo(bb.x.toFloat(),bb.y.toFloat())}}};previous=d};return path
    }
    private val guide=(0..63).map{WorldSunProjectionMath.sun(-.1+it/63.0*1.22)}
    private val grids=doubleArrayOf(-15.0,0.0,15.0,30.0,45.0,60.0,75.0).map{el->(0..96).map{i->val a=(-100+i*200.0/96)*PI/180;val e=el*PI/180;WorldSunProjectionVector(sin(a)*cos(e),sin(e),cos(a)*cos(e))}}
    private fun world(c:Canvas,p:WorldSunProjectionPose,grid:Boolean,markerAlpha:Double){
        if(grid)for(g in grids){stroke(0xCBD9D8,.55,.4);c.drawPath(projected(g,p),paint)}
        val guidePath=projected(guide,p);stroke(0x000000,2.4,.26);c.drawPath(guidePath,paint);stroke(white,1.1,.72);c.drawPath(guidePath,paint)
        val camera=WorldSunProjectionCamera(p);camera.project(WorldSunProjectionMath.sun(.12))?.let{v->line(c,v.x-4,v.y,v.x+4,v.y,0xD5CC47);line(c,v.x,v.y-4,v.x,v.y+4,0xD5CC47)}
        camera.project(WorldSunProjectionMath.sun(p.q))?.let{v->fill(white,markerAlpha);c.drawCircle(v.x.toFloat(),v.y.toFloat(),11f,paint);stroke(0x000000,.8,.23*markerAlpha);c.drawCircle(v.x.toFloat(),v.y.toFloat(),11f,paint)}
    }
    fun draw(c:Canvas,p:WorldSunProjectionPose,d:WorldSunProjectionDriver){
        c.save();c.clipRect(0f,0f,393f,852f)
        val samples=if(resources.shader!=null)listOf(p to 1.0)else WorldSunProjectionMath.weights(p).map{(i,w)->WorldSunProjectionPose(WorldSunProjectionMath.yawSamples[i%7],WorldSunProjectionMath.pitchSamples[i/7],p.q) to w}
        if(resources.shader!=null){resources.pose(p);fill(white);paint.shader=resources.shader;c.drawRect(0f,0f,393f,852f,paint);paint.shader=null;world(c,p,d.grid,d.markerAlpha())}
        else{
            var accumulated=0.0
            for((sample,weight)in samples){val index=WorldSunProjectionMath.pitchSamples.indexOfFirst{it==sample.pitch}*7+WorldSunProjectionMath.yawSamples.indexOfFirst{it==sample.yaw};accumulated+=weight
                val layer=c.saveLayerAlpha(0f,0f,393f,852f,(255*weight/accumulated).roundToInt())
                fill(white);c.drawBitmap(resources.views[index],null,RectF(0f,0f,393f,852f),paint);world(c,sample,d.grid,d.markerAlpha());c.restoreToCount(layer)
            }
        }
        fun anchor(q:Double):WorldSunProjectionPoint?{var x=0.0;var y=0.0;var weight=0.0;for((sample,w)in samples){val a=WorldSunProjectionCamera(sample).project(WorldSunProjectionMath.sun(q));if(a!=null){x+=a.x*w;y+=a.y*w;weight+=w}};return if(weight>0)WorldSunProjectionPoint(x/weight,y/weight)else null}
        var previous:WorldSunProjectionPoint?=null
        for(hour in 6..11){val q=(hour*60-309)/350.0;val a=anchor(q)?:continue;if(a.x<= -25||a.x>=360||a.y<=110||a.y>=710)continue;if(previous?.let{hypot(a.x-it.x,a.y-it.y)<28}==true)continue;text(c,"%02d:00".format(java.util.Locale.US,hour),a.x+10,a.y,9.5,center=false);previous=a}
        anchor(p.q)?.let{a->val x=WorldSunProjectionMath.clamp(a.x+36,52.0,341.0);val y=WorldSunProjectionMath.clamp(a.y-20,111.0,698.0);val alpha=d.markerAlpha();if(abs(x-a.x-36)>.1||abs(y-a.y+20)>.1){val dx=a.x-x;val dy=a.y-y;val length=max(1.0,hypot(dx,dy));line(c,x+dx/length*13,y+dy/length*13,x+dx/length*22,y+dy/length*22,width=.8,alpha=.8*alpha)};rounded(c,x-36,y-12.5,72.0,25.0,12.5,white,alpha);text(c,WorldSunProjectionMath.time(p.q),x,y,10.5,0x293432,true,alpha=alpha)}
        for(sign in listOf(-1,1)){line(c,196.5+sign*5,426.0,196.5+sign*8,426.0,width=.7,alpha=.44);line(c,196.5,426.0+sign*5,196.5,426.0+sign*8,width=.7,alpha=.44)}
        controls(c,d);c.restore()
    }
    private fun sunIcon(c:Canvas,x:Double,y:Double,r:Double,color:Int=white){stroke(color,1.0);c.drawCircle(x.toFloat(),y.toFloat(),(r*.5).toFloat(),paint);for(i in 0..7){val a=i*PI/4;line(c,x+cos(a)*r*.8,y+sin(a)*r*.8,x+cos(a)*r*1.12,y+sin(a)*r*1.12,color,1.0)}}
    private fun lock(c:Canvas,x:Double,y:Double){stroke(white,.7);c.drawRoundRect((x-2).toFloat(),y.toFloat(),(x+2).toFloat(),(y+4).toFloat(),.5f,.5f,paint);c.drawArc((x-1.5).toFloat(),(y-3).toFloat(),(x+1.5).toFloat(),(y+2).toFloat(),180f,180f,false,paint)}
    private fun controls(c:Canvas,d:WorldSunProjectionDriver){
        chip(c,12.0,58.0,185.0,28.0);sunIcon(c,27.0,72.0,4.0,0xD5CC47);text(c,"Golden hour",66.0,72.0,9.0);text(c,"04:52 → 05:33",143.0,72.0,9.0)
        chip(c,203.0,58.0,85.0,28.0);text(c,"Bearing",241.0,72.0,10.0);lock(c,267.0,70.0)
        chip(c,294.0,58.0,87.0,28.0);text(c,"Altitude",333.0,72.0,10.0);lock(c,361.0,70.0)
        chip(c,12.0,517.0,63.0,26.0);text(c,"Today",43.5,530.0,11.0,bold=true)
        for((i,y)in listOf(238.0,270.0,524.0).withIndex()){chip(c,344.0,y-12,37.0,24.0);text(c,listOf("Rise","Now","Set")[i],362.5,y,10.0)}
        rounded(c,12.0,735.0,369.0,83.0,21.0,0x303531,.82);stroke(0xFFFFFF,.7,.1);c.drawRoundRect(12f,735f,381f,818f,21f,21f,paint)
        text(c,"Alder Reach",196.5,755.0,12.0,bold=true);text(c,if(d.information)"OFFLINE SOLAR PLAN"else"EAST COAST · UTC+0",196.5,771.0,8.5,0xC7CDC7,tracking=.6f)
        for(x in listOf(30.0,34.0,38.0)){fill(white);c.drawCircle(x.toFloat(),758f,1f,paint)}
        line(c,354.0,763.0,364.0,753.0,width=1.1);line(c,354.0,763.0,354.0,759.0,width=1.1);line(c,354.0,763.0,358.0,763.0,width=1.1);line(c,364.0,753.0,360.0,753.0,width=1.1);line(c,364.0,753.0,364.0,757.0,width=1.1)
        for(i in 0..2){val x=76.5+i*120;val selected=if(i==0)d.grid else if(i==1)true else d.frozen;rounded(c,x-52.5,786.0,105.0,24.0,12.0,0x5C635C,if(selected).75 else .40)
            when(i){0->{for(sx in listOf(-1,1))for(sy in listOf(-1,1)){line(c,x+sx*7,798.0+sy*3,x+sx*7,798.0+sy*6,width=1.0);line(c,x+sx*7,798.0+sy*6,x+sx*4,798.0+sy*6,width=1.0)}};1->{stroke(white,.9);path.reset();path.moveTo((x-6).toFloat(),795f);path.lineTo(x.toFloat(),791f);path.lineTo((x+6).toFloat(),795f);path.lineTo((x+6).toFloat(),802f);path.lineTo(x.toFloat(),806f);path.lineTo((x-6).toFloat(),802f);path.close();path.moveTo((x-6).toFloat(),795f);path.lineTo(x.toFloat(),799f);path.lineTo((x+6).toFloat(),795f);path.moveTo(x.toFloat(),799f);path.lineTo(x.toFloat(),806f);c.drawPath(path,paint)};2->sunIcon(c,x,798.0,6.0)}
        }
    }
}
