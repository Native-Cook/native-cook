package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.graphics.*
import kotlin.math.*

/** API 26–32 fallback: a connected vector silhouette and a real radial bitmap-mesh lens.
 * AGSL on API 33+ supplies the exact smooth union, RGB dispersion and emissive spectrum field.
 */
internal class IslandGlassOrbFallback(private val page: IslandGlassOrbPage) {
    private val m=IslandGlassOrbMotion
    private val filter=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val colors=intArrayOf(0xffff5a1f.toInt(),0xfff5cd96.toInt(),0xff64f4f9.toInt(),0xff2b5bff.toInt(),0xffff2d8a.toInt())
    fun draw(c: Canvas,f: IslandGlassOrbFrame,oy: Double,time: Double) {
        val s=m.shapes(f,oy); val mc=m.clamp(f.m)
        val box=Path().apply { addRoundRect(RectF(s.x.toFloat(),s.y.toFloat(),(s.x+s.w).toFloat(),(s.y+s.h).toFloat()),(s.h/2).toFloat(),(s.h/2).toFloat(),Path.Direction.CW) }
        val shape=Path(box); val drop=Path()
        if(f.m>.001) {
            for(i in 0..96) {
                val a=2*PI*i/96; val cs=cos(a); val sn=sin(a)
                val x=s.cx+s.a*sign(cs)*abs(cs).pow(2/m.mix(2.6,2.0,f.free)); val y=s.cy+s.b*sign(sn)*abs(sn).pow(2/m.mix(2.6,2.0,f.free))
                if(i==0)drop.moveTo(x.toFloat(),y.toFloat()) else drop.lineTo(x.toFloat(),y.toFloat())
            }
            drop.close(); shape.op(drop,Path.Op.UNION)
            val neck=Path(); val ay=37+oy; val vx=s.cx-201; val vy=s.cy-ay; val len=max(1.0,hypot(vx,vy)); val nx=-vy/len; val ny=vx/len
            val mid=f.neck*mc/2; val end=if(mid>.05)mid*1.2+1 else 0.0
            neck.moveTo((201+nx*end).toFloat(),(ay+ny*end).toFloat())
            neck.cubicTo((201+vx*.4+nx*mid).toFloat(),(ay+vy*.4+ny*mid).toFloat(),(s.cx+nx*mid).toFloat(),(s.cy+ny*mid).toFloat(),(s.cx+nx*end).toFloat(),(s.cy+ny*end).toFloat())
            neck.lineTo((s.cx-nx*end).toFloat(),(s.cy-ny*end).toFloat())
            neck.cubicTo((s.cx-nx*mid).toFloat(),(s.cy-ny*mid).toFloat(),(201+vx*.4-nx*mid).toFloat(),(ay+vy*.4-ny*mid).toFloat(),(201-nx*end).toFloat(),(ay-ny*end).toFloat())
            neck.close(); if(mid>.05)shape.op(neck,Path.Op.UNION)
        }
        val shadow=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(1,1,1); setShadowLayer((18+6*m.clamp(f.ban)).toFloat(),0f,(6+2*m.clamp(f.ban)).toFloat(),Color.argb(((.25*mc+.22*m.clamp(f.ban))*255).roundToInt(),42,51,43)) }
        c.drawPath(shape,shadow)
        val saved=c.save(); c.clipPath(shape)
        c.drawColor(Color.rgb(1,1,1))
        if(f.ban>.001) {
            val bs=c.save(); c.clipRect(s.x.toFloat(),(s.y+s.h*.84).toFloat(),(s.x+s.w).toFloat(),(s.y+s.h).toFloat())
            val p=Paint(filter).apply { alpha=(m.clamp(f.ban)*255).roundToInt(); colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(.45f,0f,0f,0f,0f,0f,.45f,0f,0f,0f,0f,0f,.45f,0f,0f,0f,0f,0f,1f,0f))) }
            c.drawBitmap(page.blurred,null,RectF(0f,0f,402f,page.height.toFloat()),p)
            c.drawRect(s.x.toFloat(),(s.y+s.h*.84).toFloat(),(s.x+s.w).toFloat(),(s.y+s.h*.96).toFloat(),Paint().apply { shader=LinearGradient(0f,(s.y+s.h*.84).toFloat(),0f,(s.y+s.h*.96).toFloat(),Color.BLACK,Color.TRANSPARENT,Shader.TileMode.CLAMP) })
            c.restoreToCount(bs)
        }
        if(f.m>.15) {
            val ds=c.save(); c.clipPath(drop); c.clipRect(0f,(s.cy-s.b+2*s.b*f.cap).toFloat(),402f,page.height.toFloat())
            val nx=48; val ny=max(72,(page.height/8).roundToInt()); val verts=FloatArray((nx+1)*(ny+1)*2)
            for(iy in 0..ny)for(ix in 0..nx) {
                val sx=402.0*ix/nx; val sy=page.height*iy/ny; val rx=sx-s.cx; val ry=sy-s.cy
                val sourceR=(abs(rx/s.a).pow(2.6)+abs(ry/s.b).pow(2.6)).pow(1/2.6)
                var r=sourceR
                repeat(5) { r-=(.75*r+.45*r.pow(4)-sourceR)/(.75+1.8*r.pow(3)) }
                val ratio=if(sourceR>.0001)r/sourceR else 1.0
                val i=(iy*(nx+1)+ix)*2; verts[i]=(s.cx+rx*ratio).toFloat(); verts[i+1]=(s.cy+ry*ratio).toFloat()
            }
            c.drawBitmapMesh(page.image,nx,ny,verts,0,null,0,filter)
            c.drawRect(0f,(s.cy-s.b).toFloat(),402f,(s.cy+s.b).toFloat(),Paint().apply { shader=LinearGradient(0f,(s.cy-s.b+2*s.b*f.cap).toFloat(),0f,(s.cy-s.b+2*s.b*.62).toFloat(),Color.BLACK,Color.TRANSPARENT,Shader.TileMode.CLAMP) })
            c.restoreToCount(ds)
        }
        if(f.lineLen>.5) {
            val half=f.lineLen*(1+.04*f.amp)/2; val thick=(4+22*f.amp)/2; val path=Path()
            for(side in intArrayOf(-1,1))for(j in 0..60) {
                val u=if(side<0)-1+j/30.0 else 1-j/30.0; val x=f.lineX+u*half
                val env=max(1-u*u,0.0).pow(.75); val bend=f.pluckD*exp(-((x-f.pluckX)/22).pow(2)); val y=f.lineY+oy+bend+side*thick*env
                if(side<0 && j==0)path.moveTo(x.toFloat(),y.toFloat()) else path.lineTo(x.toFloat(),y.toFloat())
            }
            path.close()
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=LinearGradient((f.lineX-half).toFloat(),0f,(f.lineX+half).toFloat(),0f,colors,null,Shader.TileMode.MIRROR) }
            c.drawPath(path,p)
            val core=Path(); for(j in 0..60) { val x=f.lineX-half+j*half/30; val y=f.lineY+oy+f.pluckD*exp(-((x-f.pluckX)/22).pow(2)); if(j==0)core.moveTo(x.toFloat(),y.toFloat()) else core.lineTo(x.toFloat(),y.toFloat()) }
            c.drawPath(core,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; alpha=200; style=Paint.Style.STROKE; strokeWidth=1.2f })
        }
        if(f.arcA>.001) {
            val x=s.x+s.w/2; val y=s.y+s.h-2.4
            c.drawOval(RectF((x-f.arcW/2).toFloat(),(y-4).toFloat(),(x+f.arcW/2).toFloat(),(y+4).toFloat()),Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha=(f.arcA*255).roundToInt(); shader=LinearGradient((x-f.arcW/2).toFloat(),0f,(x+f.arcW/2).toFloat(),0f,colors,null,Shader.TileMode.CLAMP) })
        }
        c.restoreToCount(saved)
        if(mc>.001 || f.ban>.001 || f.pill>.001) {
            c.drawPath(shape,Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=1.25f; shader=LinearGradient(0f,s.y.toFloat(),0f,(s.cy+s.b).toFloat(),Color.argb((.3*mc*255).roundToInt(),255,255,255),Color.argb((max(.88*mc,max(.25*m.clamp(f.pill),.45*m.clamp(f.ban)))*255).roundToInt(),255,255,255),Shader.TileMode.CLAMP) })
        }
    }
}
