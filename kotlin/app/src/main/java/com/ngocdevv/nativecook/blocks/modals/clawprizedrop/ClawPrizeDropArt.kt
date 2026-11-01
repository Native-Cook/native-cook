package com.ngocdevv.nativecook.blocks.modals.clawprizedrop

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class ClawPrizeDropArt(context: Context) {
    private val assets=context.assets
    private val prizes=listOf("bear","bunny","star","capsule","coinbag","ticket").map {
        assets.open("clawprizedrop/clawprizedrop_prize-$it.png").use(BitmapFactory::decodeStream)
    }
    private val title=Typeface.createFromAsset(assets,"clawprizedrop/clawprizedrop_TitanOne-Regular.ttf")
    private val black=Typeface.createFromAsset(assets,"clawprizedrop/clawprizedrop_Nunito-Black.ttf")
    private val bold=Typeface.createFromAsset(assets,"clawprizedrop/clawprizedrop_Nunito-ExtraBold.ttf")
    private val icons=Typeface.createFromAsset(assets,"clawprizedrop/clawprizedrop_Ionicons.ttf")
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path=Path(); private val rect=RectF()
    private var back: Bitmap?=null; private var front: Bitmap?=null; private var key=""
    private fun paint(color: Int, alpha: Double=1.0, stroke: Double=0.0): Paint {
        p.shader=null; p.color=0xff000000.toInt() or color; p.alpha=(alpha*255).roundToInt().coerceIn(0,255)
        p.style=if(stroke>0) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth=stroke.toFloat(); p.strokeCap=Paint.Cap.ROUND
        return p
    }
    private fun rr(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int,alpha: Double=1.0,stroke: Double=0.0) {
        rect.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
        c.drawRoundRect(rect,r.toFloat(),r.toFloat(),paint(color,alpha,stroke))
    }
    private fun border(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int,b: Double,bc: Int=0xffffff) {
        rr(c,x,y,w,h,r,color); rr(c,x+b/2,y+b/2,w-b,h-b,r-b/2,bc,stroke=b)
    }
    private fun text(c: Canvas,s: String,face: Typeface,sz: Double,color: Int,x: Double,cy: Double) {
        val q=paint(color); q.typeface=face; q.textSize=sz.toFloat()
        c.drawText(s,x.toFloat(),(cy-(q.ascent()+q.descent())/2).toFloat(),q)
    }
    private fun tw(s: String,face: Typeface,sz: Double): Double { p.typeface=face; p.textSize=sz.toFloat(); return p.measureText(s).toDouble() }
    private fun circle(c: Canvas,x: Double,y: Double,r: Double,color: Int) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint(color)) }
    private fun prepare(g: ClawPrizeDropMotion.Geo,density: Float) {
        val k="${g.width}-${g.height}-${g.top}-$density"; if(key==k)return
        key=k; back?.recycle(); front?.recycle()
        fun layer(draw: (Canvas)->Unit): Bitmap {
            val b=Bitmap.createBitmap((g.width*density).roundToInt(),(g.height*density).roundToInt(),Bitmap.Config.ARGB_8888)
            val c=Canvas(b); c.scale(density,density); draw(c); return b
        }
        back=layer { c ->
            c.drawColor(0xff8fd3ff.toInt())
            border(c,16.0,g.top+8,44.0,44.0,22.0,0x3d8bff,3.0)
            text(c,"\uf24a",icons,22.0,0xffffff,38-tw("\uf24a",icons,22.0)/2,g.top+30)
            val cw=48+tw("1,200",black,15.0); val cx=g.width-16-cw
            rr(c,cx,g.top+12,cw,36.0,18.0,0xffffff)
            border(c,cx+12,g.top+21,18.0,18.0,9.0,0xffc23d,3.0,0xffde7a)
            text(c,"1,200",black,15.0,0x2c3446,cx+36,g.top+30)
            val sw=tw("Grabbit",title,26.0)+60
            border(c,(g.width-sw)/2,g.top+64,sw,52.0,18.0,0xff5a5a,4.0)
            text(c,"Grabbit",title,26.0,0xffffff,(g.width-tw("Grabbit",title,26.0))/2,g.top+90)
            c.save(); path.reset(); path.addRoundRect(18f,(g.y-26).toFloat(),(g.width-18).toFloat(),(g.y+8).toFloat(),10f,10f,Path.Direction.CW); c.clipPath(path)
            for(i in 0..8) rr(c,18+i*(g.w+20)/9,g.y-26,(g.w+20)/9,34.0,0.0,if(i%2==0)0xff5a5a else 0xffffff)
            c.restore()
        }
        front=layer { c ->
            val by=g.y+g.h-6; val bw=g.w+20
            rr(c,18.0,by,bw,200.0,18.0,0xe84a4a)
            rr(c,32.0,by+14,bw-28,84.0,14.0,0x2c3446)
            text(c,"Slide the claw, then grab · 3 tries left",bold,14.0,0xffe3e3,32.0,by+142)
        }
    }
    fun draw(c: Canvas,g: ClawPrizeDropMotion.Geo,o: ClawPrizeDropMotion.Frame,reduced: Boolean,won: Int,pressed: Boolean,density: Float) {
        prepare(g,density)
        c.save(); c.scale(1/density,1/density); c.drawBitmap(back!!,0f,0f,paint(0xffffff)); c.restore()
        rr(c,g.x,g.y,g.w,g.h,18.0,0xcdebff); rr(c,g.x+8,g.y+8,g.w-16,g.h-16,12.0,0xe5f5ff)
        for(k in 0..6) circle(c,g.x+20+k*(g.w-40)/6,g.floorY+18,34.0,0xffffff)
        for(i in 0..5) {
            var px=g.px[i]; var py=g.py[i]; var sc=1.0; var rot=0.0; var alpha=1.0
            if(i==o.prize) {
                if(o.carry>0) { px=o.x+o.swing*0.6; py=g.railY+40+(g.floorY-g.railY-40)*o.drop+g.ps*0.55; rot=o.swing*0.8 }
                else if(o.fall>=0) {
                    if(o.fall>0.5) { if(o.respawn<=0)continue; sc=0.6+0.4*o.respawn; py=g.py[i]-(1-o.respawn)*80; alpha=o.respawn }
                    else { val f=o.fall/0.5; px=g.chuteX; py=g.railY+40+g.ps*0.55+(g.y+g.h+40-(g.railY+40))*f*f
                        rot=if(reduced)0.0 else f*140; alpha=1-ClawPrizeDropMotion.clamp((f-0.8)/0.2) }
                }
            }
            c.save(); c.translate(px.toFloat(),py.toFloat()); c.rotate(rot.toFloat())
            val half=(g.ps*sc/2).toFloat(); rect.set(-half,-half,half,half)
            c.drawBitmap(prizes[i],null,rect,paint(0xffffff,alpha)); c.restore()
        }
        rr(c,g.x+6,g.railY-8,g.w-12,8.0,0.0,0x9aa9bf)
        val cx=o.x+o.swing; val cy=g.railY+30+(g.floorY-g.railY-40)*o.drop
        c.drawLine(o.x.toFloat(),g.railY.toFloat(),cx.toFloat(),(cy-18).toFloat(),paint(0x5d6b80,stroke=3.0))
        rr(c,cx-18,cy-24,36.0,22.0,8.0,0x3a4558)
        val open=1-o.close
        for(k in listOf(-1,1)) {
            path.reset(); path.moveTo((cx+k*10).toFloat(),(cy-4).toFloat())
            path.quadTo((cx+k*(20+10*open)).toFloat(),(cy+18).toFloat(),(cx+k*(6+10*open)).toFloat(),(cy+34).toFloat())
            c.drawPath(path,paint(0x3a4558,stroke=7.0))
        }
        rr(c,g.x+18,g.y+20,10.0,g.h-60,0.0,0xffffff,0.35)
        rr(c,g.x,g.y,g.w,g.h,18.0,0xff6b6b,stroke=6.0)
        rr(c,g.chuteX-34,g.floorY-6,68.0,16.0,6.0,0x2c3446,0.85)
        c.save(); c.scale(1/density,1/density); c.drawBitmap(front!!,0f,0f,paint(0xffffff)); c.restore()
        val by=g.y+g.h-6
        if(o.coupon>0) {
            c.saveLayerAlpha(0f,0f,g.width.toFloat(),g.height.toFloat(),(o.coupon*255).roundToInt())
            val cw=g.w-32; c.translate((44+cw/2).toFloat(),(by+56+(1-o.coupon)*20).toFloat())
            val s=(0.9+0.1*o.coupon).toFloat(); c.scale(s,s)
            rr(c,-cw/2,-29.0,cw,58.0,12.0,0xfff4e6)
            text(c,"\uf337",icons,18.0,0xff5a5a,-cw/2+12,0.0)
            text(c,ClawPrizeDropMotion.names[won],black,15.0,0x2c3446,-cw/2+40,-8.0)
            text(c,ClawPrizeDropMotion.rewards[won],bold,13.0,0xe84a4a,-cw/2+40,10.0)
            c.restore()
        }
        c.save(); c.translate((g.width-64).toFloat(),(by+142).toFloat()); if(pressed)c.scale(0.96f,0.96f)
        border(c,-32.0,-32.0,64.0,64.0,32.0,0xff8a3d,4.0)
        text(c,"\uf35b",icons,24.0,0xffffff,-tw("\uf35b",icons,24.0)/2,0.0)
        c.restore()
    }
}
