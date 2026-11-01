package com.ngocdevv.nativecook.blocks.sheets.bundlepackbox

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class BundlePackBoxArt(context: Context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val faces=listOf("Regular","SemiBold","Bold").associateWith { Typeface.createFromAsset(context.assets,"bundlepackbox/bundlepackbox_Inter-$it.ttf") }
    private val icons=Typeface.createFromAsset(context.assets,"bundlepackbox/bundlepackbox_Ionicons.ttf")
    private var page: Bitmap?=null; private var cacheKey=""
    var cta=RectF(); private set
    var sheetTop=0.0; private set
    private fun color(h: Int,a: Double=1.0)=((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24) or h
    private fun fill(h: Int,a: Double=1.0): Paint { paint.reset(); paint.isAntiAlias=true; paint.color=color(h,a); return paint }
    private fun stroke(h: Int,w: Double,a: Double=1.0): Paint=fill(h,a).apply { style=Paint.Style.STROKE; strokeWidth=w.toFloat(); strokeCap=Paint.Cap.ROUND }
    private fun rect(x: Double,y: Double,w: Double,h: Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
    private fun rr(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,hex: Int,a: Double=1.0) { c.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),fill(hex,a)) }
    private fun oval(c: Canvas,x: Double,y: Double,w: Double,h: Double,hex: Int,a: Double=1.0,sw: Double=0.0) { c.drawOval(rect(x,y,w,h),if(sw>0)stroke(hex,sw,a) else fill(hex,a)) }
    private fun line(c: Canvas,x: Double,y: Double,x2: Double,y2: Double,hex: Int,w: Double,a: Double=1.0) { c.drawLine(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),stroke(hex,w,a)) }
    private fun face(weight: String,size: Double)=fill(0).apply { typeface=if(weight=="Icon")icons else faces[weight]; textSize=size.toFloat() }
    private fun textWidth(s: String,weight: String,size: Double,track: Double=0.0): Double { val p=face(weight,size); return p.measureText(s).toDouble()+max(0,s.length-1)*track }
    private fun text(c: Canvas,s: String,weight: String,size: Double,hex: Int,x: Double,y: Double,track: Double=0.0) {
        val p=face(weight,size); p.color=color(hex); val baseline=(y-p.fontMetrics.ascent).toFloat()
        if(track==0.0)c.drawText(s,x.toFloat(),baseline,p)
        else { var xx=x.toFloat(); s.forEach { val letter=it.toString(); c.drawText(letter,xx,baseline,p); xx+=p.measureText(letter)+track.toFloat() } }
    }
    private fun centered(c: Canvas,s: String,weight: String,size: Double,hex: Int,cx: Double,y: Double,track: Double=0.0) { text(c,s,weight,size,hex,cx-textWidth(s,weight,size,track)/2,y,track) }
    private fun lines(s: String,width: Double): List<String> {
        val result=mutableListOf<String>(); var row=""
        for(word in s.split(" ")) { val next=if(row.isEmpty())word else "$row $word"; if(textWidth(next,"Regular",15.0)>width && row.isNotEmpty()) {result.add(row); row=word} else row=next }
        result.add(row); return result
    }
    private fun page(w: Double,h: Double,top: Double,bottom: Double): Bitmap {
        val key="$w:$h:$top:$bottom"; if(key==cacheKey)return requireNotNull(page)
        val bitmap=Bitmap.createBitmap(ceil(w*3).toInt(),ceil(h*3).toInt(),Bitmap.Config.ARGB_8888); val c=Canvas(bitmap);c.scale(3f,3f)
        val desc=lines("Kettlebell 8 kg, adjustable dumbbell, travel mat, 750 ml bottle and a light band.",w-44)
        val sheetH=22+15+6+29+6+21*desc.size+14+32+16+56+10+16+bottom+14
        val sy=h-sheetH; sheetTop=sy; val path=Path(); path.addRoundRect(rect(0.0,sy,w,sheetH),floatArrayOf(30f,30f,30f,30f,0f,0f,0f,0f),Path.Direction.CW); c.drawPath(path,fill(0xFFFFFF))
        oval(c,16.0,top+6,44.0,44.0,0xFFFFFF,0.8);oval(c,w-60,top+6,44.0,44.0,0xFFFFFF,0.8)
        centered(c,61993.toChar().toString(),"Icon",22.0,0x2A2420,38.0,top+17)
        centered(c,61791.toChar().toString(),"Icon",21.0,0x2A2420,w-38,top+17)
        centered(c,"Kitform","Bold",18.0,0x2A2420,w/2,top+17,-0.3)
        text(c,"BUNDLE · 5 PIECES","SemiBold",12.0,0xB07A45,22.0,sy+22,1.2)
        text(c,"Home Gym Starter","Bold",24.0,0x2A2420,22.0,sy+43)
        desc.forEachIndexed { i,s -> text(c,s,"Regular",15.0,0x7A706A,22.0,sy+78+i*21) }
        val py=sy+78+desc.size*21+14
        text(c,"$129","Bold",26.0,0x2A2420,22.0,py)
        val oldX=22+textWidth("$129","Bold",26.0)+10
        text(c,"$166","Regular",16.0,0xA79D96,oldX,py+6)
        line(c,oldX,py+18,oldX+textWidth("$166","Regular",16.0),py+18,0xA79D96,1.0)
        val sx=oldX+textWidth("$166","Regular",16.0)+10
        rr(c,sx,py+3,textWidth("Save 22%","SemiBold",13.0)+20,26.0,13.0,0xFFE7DE)
        text(c,"Save 22%","SemiBold",13.0,0xE2574C,sx+10,py+8)
        cta=rect(22.0,py+32+16,w-44,56.0)
        centered(c,"Tip: pinch the kit together to pack it yourself","Regular",13.0,0xA79D96,w/2,cta.bottom+10.0)
        page?.recycle(); page=bitmap; cacheKey=key; return bitmap
    }
    fun draw(c: Canvas,w: Double,h: Double,top: Double,bottom: Double,o: BundlePackBoxMotion.Frame,reduced: Boolean,pressed: Boolean) {
        c.drawColor(color(0xF6E9DA)); scene(c,w,h,o,reduced)
        if(o.stamp>0) {
            val sw=textWidth("TO SHIP","Bold",13.0,1.2)+26;val sh=44.0
            c.save(); c.translate((w/2+30+sw/2).toFloat(),(round(h*0.29)-40+sh/2).toFloat());c.rotate(-8f);val sc=(1.4-0.4*o.stamp).toFloat();c.scale(sc,sc)
            c.saveLayerAlpha(rect(-sw/2,-sh/2,sw,sh),(o.stamp*255).roundToInt())
            c.drawRoundRect(rect(-sw/2+1.5,-sh/2+1.5,sw-3,sh-3),8.5f,8.5f,stroke(0xE2574C,3.0))
            centered(c,"READY","Bold",13.0,0xE2574C,0.0,-sh/2+7,1.2);centered(c,"TO SHIP","Bold",13.0,0xE2574C,0.0,-sh/2+22,1.2)
            c.restore();c.restore()
        }
        val bitmap=page(w,h,top,bottom);c.drawBitmap(bitmap,null,rect(0.0,0.0,w,h),null)
        c.save();c.translate(cta.centerX(),cta.centerY());if(pressed)c.scale(0.96f,0.96f)
        rr(c,-cta.width()/2.0,-28.0,cta.width().toDouble(),56.0,28.0,if(o.packed)0x2F9E62 else 0x2A2420)
        val label=if(o.packed)"Packed · Add bundle to bag" else "Pack bundle"
        val tw=textWidth(label,"SemiBold",17.0); val ix=-(tw+28)/2
        centered(c,(if(o.packed)61982 else 62105).toChar().toString(),"Icon",20.0,0xFFFFFF,ix+10,-10.0)
        text(c,label,"SemiBold",17.0,0xFFFFFF,ix+28,-10.0)
        c.restore()
    }
    private fun item(c: Canvas,kind: Int,u: Double) {
        when(kind) {
            0 -> {
                oval(c,-u*0.5,-u*0.32,u,u,0x2F3A4A);oval(c,-u*0.31,-u*0.14,u*0.32,u*0.32,0x5A6B82,0.6)
                c.drawArc(rect(-u*0.32,-u*0.62,u*0.64,u*0.6),180f,180f,false,stroke(0x2F3A4A,u*0.14))
                rr(c,-u*0.22,u*0.05,u*0.44,u*0.22,u*0.06,0xFF8A3D)
            }
            1 -> {
                rr(c,-u*0.5,-u*0.06,u,u*0.12,u*0.05,0xA9B4C2)
                for(k in listOf(-1,1)) {rr(c,k*u*0.42-u*0.12,-u*0.3,u*0.24,u*0.6,u*0.08,0x3D7BFF);rr(c,k*u*0.42-u*0.12,-u*0.3,u*0.08,u*0.6,u*0.04,0x7FA9FF,0.6)}
            }
            2 -> {rr(c,-u*0.7,-u*0.2,u*1.4,u*0.4,u*0.2,0x7CC97E);oval(c,u*0.55,-u*0.2,u*0.26,u*0.4,0x5CAE60);oval(c,u*0.62,-u*0.1,u*0.12,u*0.2,0x3F8C44);line(c,-u*0.5,-u*0.2,-u*0.5,u*0.2,0xFFFFFF,2.0,0.35)}
            3 -> {rr(c,-u*0.2,-u*0.5,u*0.4,u,u*0.14,0xFF6B8B);rr(c,-u*0.12,-u*0.66,u*0.24,u*0.2,u*0.06,0x2F3A4A);rr(c,-u*0.2,-u*0.05,u*0.4,u*0.2,0.0,0xFFFFFF,0.85)}
            else -> {oval(c,-u*0.5,-u*0.3,u,u*0.6,0xFFC23D,1.0,u*0.12);oval(c,-u*0.32,-u*0.18,u*0.64,u*0.36,0xFFD978,1.0,u*0.08)}
        }
    }
    private fun scene(c: Canvas,W: Double,H: Double,o: BundlePackBoxMotion.Frame,reduced: Boolean) {
        val cx=W/2;val cy=round(H*0.29);val k=W*0.19;val u=W*0.17;val spread=W*0.38;val by=round(H*0.5)
        val w=0.9;val h=0.62;val d=0.7
        fun p(x: Double,y: Double,z: Double): PointF {val a=x*cos(-0.6)+z*sin(-0.6);val cc= -x*sin(-0.6)+z*cos(-0.6);return PointF((cx+a*k).toFloat(),(by-(y*cos(0.42)-cc*sin(0.42))*k).toFloat())}
        fun quad(pts: List<PointF>,hex: Int,a: Double=1.0) {val path=Path();path.moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach {path.lineTo(it.x,it.y)};path.close();c.drawPath(path,fill(hex,a))}
        oval(c,cx-k*1.35,by-k*0.22,k*2.7,k*0.66,0x7A4B2A,0.13)
        quad(listOf(p(-w,0.0,-d),p(w,0.0,-d),p(w,h*2,-d),p(-w,h*2,-d)),0xB9834F)
        quad(listOf(p(-w,0.0,-d),p(-w,0.0,d),p(-w,h*2,d),p(-w,h*2,-d)),0xA8733F)
        val positions=arrayOf(doubleArrayOf(-0.62,-0.52,-0.3),doubleArrayOf(0.6,-0.6,0.5),doubleArrayOf(-0.7,0.02,0.2),doubleArrayOf(0.68,-0.05,-0.25),doubleArrayOf(0.02,-0.62,0.1))
        for(pass in 0..1) {
            for(i in 0..4) {
                val it=positions[i];val ki=if(reduced) {if(o.p>0.5)1.0 else 0.0} else BundlePackBoxMotion.smooth(o.p*1.45-i*0.11)
                if((pass==0)!=(ki>0.78))continue
                val bob=if(reduced)0.0 else sin(o.t*2*PI*(1+i%2)/9+i)*6*(1-ki)
                val ex=cx+it[0]*spread;val ey=cy+it[1]*spread+bob
                val top=p((i-2)*0.22,h*2+0.3,if(i%2==1)0.15 else -0.15);val deep=p((i-2)*0.22,h*0.9,if(i%2==1)0.15 else -0.15)
                val f=if(ki<0.78)ki/0.78 else (ki-0.78)/0.22
                val x=BundlePackBoxMotion.mix(if(ki<0.78)ex else top.x.toDouble(),if(ki<0.78)top.x.toDouble() else deep.x.toDouble(),f)
                val y=BundlePackBoxMotion.mix(if(ki<0.78)ey else top.y.toDouble(),if(ki<0.78)top.y.toDouble() else deep.y.toDouble(),f)-(if(ki<0.78)sin(PI*f)*u*0.9 else 0.0)
                c.save();c.translate(x.toFloat(),y.toFloat());c.rotate(((it[2]*(1-ki)+(if(o.burst>0)sin(o.burst*9+i)*0.4*(1-o.burst) else 0.0))*57.3).toFloat());val sc=BundlePackBoxMotion.mix(1.0,0.62,ki).toFloat();c.scale(sc,sc);item(c,i,u);c.restore()
            }
            if(pass==0) {
                quad(listOf(p(-w,0.0,d),p(w,0.0,d),p(w,h*2,d),p(-w,h*2,d)),0xD9A066)
                quad(listOf(p(w,0.0,d),p(w,0.0,-d),p(w,h*2,-d),p(w,h*2,d)),0xC48A50)
                val a=p(-w*0.6,h*1.1,d);val b=p(w*0.6,h*1.1,d);line(c,a.x.toDouble(),a.y.toDouble(),b.x.toDouble(),b.y.toDouble(),0x8A5A30,2.0,0.35)
                val lg=p(0.0,h*0.75,d);oval(c,lg.x-k*0.12,lg.y-k*0.12,k*0.24,k*0.24,0x8A5A30,0.28)
            }
        }
        val fl=if(reduced) {if(o.p>0.95)1.0 else 0.0} else BundlePackBoxMotion.smooth((o.p-0.72)/0.22);val ang=(1-fl)*2.2
        for(f in 0..1) {val z=if(f==0)d else -d;val dir=if(f==0)1.0 else -1.0;val hy=h*2+d*sin(ang);val z2=z-dir*d*cos(ang);quad(listOf(p(-w,h*2,z),p(w,h*2,z),p(w,hy,z2),p(-w,hy,z2)),if(f==0)0xE3AE73 else 0xC9925A)}
        for(f in 0..1) {val sx=if(f==0)-w else w;val dir=if(f==0)-1.0 else 1.0;val hy=h*2+w*0.55*sin(ang);val x2=sx-dir*w*0.55*cos(ang);quad(listOf(p(sx,h*2,-d),p(sx,h*2,d),p(x2,hy,d),p(x2,hy,-d)),if(f==0)0xD29B60 else 0xDDA86C)}
        if(o.tape>0) {
            quad(listOf(p(-0.12,h*2+0.002,d+0.02),p(-0.12,h*2+0.002,d-2*d*o.tape),p(0.12,h*2+0.002,d-2*d*o.tape),p(0.12,h*2+0.002,d+0.02)),0xF4E2B8,0.92)
            quad(listOf(p(-0.12,h*2,d),p(0.12,h*2,d),p(0.12,h*2-0.3*o.tape,d),p(-0.12,h*2-0.3*o.tape,d)),0xF4E2B8,0.92)
        }
    }
}
