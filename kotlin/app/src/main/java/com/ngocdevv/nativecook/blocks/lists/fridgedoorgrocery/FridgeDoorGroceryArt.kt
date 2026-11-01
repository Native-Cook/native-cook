package com.ngocdevv.nativecook.blocks.lists.fridgedoorgrocery

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class FridgeDoorGroceryArt(context: Context) {
    private val black = Typeface.createFromAsset(context.assets,"fridgedoorgrocery/fridgedoorgrocery_Nunito-Black.ttf")
    private val bold = Typeface.createFromAsset(context.assets,"fridgedoorgrocery/fridgedoorgrocery_Nunito-ExtraBold.ttf")
    private val icons = Typeface.createFromAsset(context.assets,"fridgedoorgrocery/fridgedoorgrocery_Ionicons.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val foods=List(8) { makeFood(it) }
    private var interior: LinearGradient?=null
    private var interiorY=Double.NaN
    private var interiorH=Double.NaN
    private fun ink(hex:Int,alpha:Double=1.0):Paint {
        paint.shader=null; paint.style=Paint.Style.FILL; paint.color=hex or 0xFF000000.toInt(); paint.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt(); return paint
    }
    private fun rect(c:Canvas,x:Double,y:Double,w:Double,h:Double,hex:Int,r:Double=0.0,alpha:Double=1.0) {
        c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),ink(hex,alpha))
    }
    private fun circle(c:Canvas,x:Double,y:Double,r:Double,hex:Int,alpha:Double=1.0) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),ink(hex,alpha)) }
    private fun quad(c:Canvas,v:DoubleArray,hex:Int,alpha:Double=1.0) {
        path.reset(); path.moveTo(v[0].toFloat(),v[1].toFloat()); for(i in 2 until v.size step 2)path.lineTo(v[i].toFloat(),v[i+1].toFloat()); path.close(); c.drawPath(path,ink(hex,alpha))
    }
    private fun text(c:Canvas,s:String,x:Double,y:Double,size:Double,hex:Int,font:Typeface=black,center:Boolean=false,baseline:Boolean=false) {
        ink(hex); paint.typeface=font; paint.textSize=size.toFloat(); paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s,x.toFloat(),if(baseline)y.toFloat() else (y-paint.fontMetrics.ascent).toFloat(),paint)
    }
    fun draw(c:Canvas,width:Double,height:Double,top:Double,bottom:Double,f:FridgeDoorGroceryMotion.Frame,reduced:Boolean) {
        c.drawColor(0xFFFBEFE2.toInt())
        val g=FridgeDoorGroceryMotion.Geo(width,height,top)
        fridge(c,g,f,reduced)
        text(c,"Pantry",20.0,top+8,14.0,0xA08A72,bold)
        text(c,"Your fridge",20.0,top+27,28.0,0x2A2A33)
        c.save(); c.translate((width-44).toFloat(),(top+36.5).toFloat()); c.scale(f.bagScale.toFloat(),f.bagScale.toFloat())
        circle(c,0.0,0.0,24.0,0xFFFFFF)
        text(c,"\uF17B",0.0,-11.0,22.0,0x2A2A33,icons,true)
        if(f.bag>0) { circle(c,16.0,-16.0,10.0,0xFF5A4E); text(c,"${f.bag}",16.0,-23.5,11.0,0xFFFFFF,black,true) }
        c.restore()
        val ny=height-bottom-76
        rect(c,20.0,ny,width-40,56.0,0xFFFFFF,20.0)
        text(c,"\uF113",36.0,ny+18,20.0,0xFF5A4E,icons)
        text(c,"2 items running low · tap to restock",66.0,ny+18,15.0,0x2A2A33,bold)
    }
    private fun fridge(c:Canvas,g:FridgeDoorGroceryMotion.Geo,o:FridgeDoorGroceryMotion.Frame,reduced:Boolean) {
        val x=g.x; val y=g.y; val w=g.w; val h=g.h
        rect(c,x+10,y+14,w,h,0x8A6A4A,30.0,0.12)
        rect(c,x+w*0.12,y+h-6,24.0,16.0,0x4A5560,6.0); rect(c,x+w*0.88-24,y+h-6,24.0,16.0,0x4A5560,6.0)
        rect(c,x,y,w,h,0x8FD3C1,30.0)
        val open=FridgeDoorGroceryMotion.clamp(o.a); val ix=x+14; val iy=y+14; val iw=w-28; val ih=h-28
        if(interiorY!=iy || interiorH!=ih) { interior=LinearGradient(0f,iy.toFloat(),0f,(iy+ih).toFloat(),0xFFFFFDF4.toInt(),0xFFEAF3F6.toInt(),Shader.TileMode.CLAMP); interiorY=iy; interiorH=ih }
        ink(0xFFFFFF); paint.shader=interior; c.drawRoundRect(ix.toFloat(),iy.toFloat(),(ix+iw).toFloat(),(iy+ih).toFloat(),20f,20f,paint)
        val sh=ih/4
        for(s in 0..3)rect(c,ix+6,iy+sh*(s+1)-10,iw-12,6.0,0xCFE3EA)
        FridgeDoorGroceryMotion.items.forEachIndexed { i,it ->
            val appear=if(reduced) { if(open>0.5)1.0 else 0.0 } else FridgeDoorGroceryMotion.smoother(open*1.6-0.3-it.shelf*0.08)
            val fl=o.fly[i]
            if(fl<1) {
                val bx=ix+iw*it.x; val by=iy+sh*(it.shelf+1)-10
                val sm=FridgeDoorGroceryMotion.smoother(fl)
                val fx=bx+(g.bagX-bx)*sm; val fy=by+(g.bagY-by)*sm-sin(PI*fl)*90
                c.save(); c.translate(fx.toFloat(),(fy+(1-appear)*12).toFloat()); val sc=((0.85+0.15*appear)*(1-0.6*fl)).toFloat(); c.scale(sc,sc)
                if(it.low && fl==0.0 && appear>0.9) {
                    val pulse=if(reduced)0.5 else 0.5+0.5*sin(o.t*2*PI*3/9)
                    circle(c,0.0,-sh*0.3,sh*(0.3+0.05*pulse),0xFF5A4E,0.08+0.12*pulse)
                }
                c.saveLayerAlpha(null,(appear*255).roundToInt()); c.save(); c.scale((sh*0.55).toFloat(),(sh*0.55).toFloat()); c.translate(-2f,-2f); c.drawPicture(foods[i]); c.restore(); c.restore()
                if(it.low && fl==0.0 && appear>0.9) { rect(c,-20.0,2.0,40.0,18.0,0xFF5A4E,9.0); text(c,"Low",0.0,15.0,11.0,0xFFFFFF,black,true,true) }
                c.restore()
            }
        }
        val ang=if(reduced) { if(open>0.5)1.95 else 0.0 } else o.a*1.95
        val co=cos(ang); val near=1+0.16*sin(ang); val ex=x+w*co; val tr=y-h*(near-1)/2; val br=y+h+h*(near-1)/2
        val fade=if(reduced)1-open else 1.0
        if(co>0) {
            quad(c,doubleArrayOf(x,y,ex,tr,ex,br,x,y+h),0x9FDCCB,fade)
            quad(c,doubleArrayOf(x,y+4,ex,tr+4,ex,tr+10,x,y+10),0xC6EEE2,fade*0.8)
            val hxp=x+(ex-x)*0.86; val hy=y+(tr-y)*0.86+h*0.3
            rect(c,hxp-5,hy,10*max(0.3,co),h*0.24,0xDDE3EA,5.0,fade)
            circle(c,x+(ex-x)*0.3,y+h*0.18,12*max(0.3,co),0xFF8A65,fade)
        } else {
            quad(c,doubleArrayOf(x,y,ex,tr,ex,br,x,y+h),0xDDF1EA)
            for(s in 0..2) { val k=0.28+s*0.28; quad(c,doubleArrayOf(x,y+h*k,ex,tr+(br-tr)*k,ex,tr+(br-tr)*(k+0.05),x,y+h*(k+0.05)),0xCFE3EA) }
        }
    }
    private fun makeFood(kind:Int):Picture {
        val picture=Picture(); val c=picture.beginRecording(4,4); c.translate(2f,2f)
        // Keep the normalized food inside the recording bounds; the commands remain vector paths.
        when(kind) {
            0 -> { rect(c,-0.32,-0.9,0.64,0.9,0xFFFFFF); quad(c,doubleArrayOf(-0.32,-0.9,0.0,-1.15,0.32,-0.9),0xE9EEF5); rect(c,-0.32,-0.55,0.64,0.22,0x5AA9FF) }
            1 -> { rect(c,-0.25,-1.0,0.5,1.0,0xFFB23D,0.12); rect(c,-0.12,-1.15,0.24,0.18,0x2F9E62) }
            2 -> { rect(c,-0.7,-0.3,1.4,0.3,0xC9B9A0,0.06); for(i in 0..3)c.drawOval((-0.62+i*0.32).toFloat(),-0.6f,(-0.36+i*0.32).toFloat(),-0.26f,ink(0xFFF2DE)) }
            3 -> { quad(c,doubleArrayOf(-0.6,0.0,0.6,0.0,0.6,-0.38,-0.6,-0.12),0xFFD54A); circle(c,-0.2,-0.12,0.07,0xE8B52E); circle(c,0.25,-0.2,0.09,0xE8B52E) }
            4 -> { rect(c,-0.08,-0.4,0.16,0.4,0x7CC46A); for(i in 0..3)circle(c,-0.25+i%2*0.5,-0.55-(if(i>1)0.18 else 0.0),0.22,if(i%2==1)0x3F9E4E else 0x4FB35C) }
            5 -> for(i in 0..2) { c.save(); c.translate((-0.3+i*0.3).toFloat(),0f); c.rotate((-10+i*10).toFloat()); quad(c,doubleArrayOf(-0.1,-0.8,0.1,-0.8,0.0,0.0),0xFF8A3D); circle(c,0.0,-0.86,0.08,0x4FB35C); c.restore() }
            6 -> for(i in 0..1) { val d=i*0.55; quad(c,doubleArrayOf(-0.5+d,-0.5,-0.08+d,-0.5,-0.12+d,0.0,-0.46+d,0.0),if(i==1)0xFFB8C8 else 0xC9B8FF); rect(c,-0.52+d,-0.56,0.46,0.08,0xFFFFFF) }
            else -> { rect(c,-0.28,-0.6,0.56,0.6,0xD9435B,0.1); rect(c,-0.3,-0.72,0.6,0.14,0xF2D9A0); rect(c,-0.2,-0.38,0.4,0.18,0xFFFFFF,0.0,0.85) }
        }
        picture.endRecording(); return picture
    }
}
