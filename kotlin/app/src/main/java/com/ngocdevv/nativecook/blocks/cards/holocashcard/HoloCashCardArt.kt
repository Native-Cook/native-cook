package com.ngocdevv.nativecook.blocks.cards.holocashcard

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

internal data class HoloCashCardLayout(val w:Float,val h:Float,val topInset:Float,val bottomInset:Float) {
    val cw=w-36; val ch=(cw/1.6f).roundToInt().toFloat(); val top=topInset+6
    val cardY=top+52; val balanceY=cardY+ch+8; val actionsY=balanceY+136; val loyalY=balanceY+196
    val tabsY=h-bottomInset-8-64
}
internal class HoloCashCardArt(context:Context) {
    private val fonts=listOf("Medium","SemiBold","Bold","Anton").associateWith {
        Typeface.createFromAsset(context.assets,"holocashcard/holocashcard_${if(it=="Anton")"Anton-Regular" else "Inter-$it"}.ttf")
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val shadow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.BLACK; alpha=(255*0.26).roundToInt(); maskFilter=BlurMaskFilter(((12-0.5)/0.57735).toFloat(),BlurMaskFilter.Blur.NORMAL)
    }
    private val shaders=if(Build.VERSION.SDK_INT>=33)HoloCashCardShaders() else null
    private var page:Bitmap?=null
    private var pageKey=""
    private val matrix=Matrix()
    private val rect=RectF()
    private val icons=ICON_DATA.mapValues { PathParser.createPathFromPathData(it.value)!! }
    private fun ink(hex:Int)=hex or (0xff shl 24)
    private fun setup(size:Float,weight:String="Bold",color:Int=0x111111,tabular:Boolean=false) {
        paint.reset(); paint.isAntiAlias=true; paint.isSubpixelText=true; paint.typeface=fonts[weight]
        paint.textSize=size; paint.color=ink(color); paint.fontFeatureSettings=if(tabular)"'tnum'" else null
    }
    private fun width(text:String,size:Float,weight:String="Bold",tracking:Float=0f,tabular:Boolean=false):Float {
        setup(size,weight,tabular=tabular); return paint.measureText(text)+tracking*text.length
    }
    private fun lineHeight(size:Float,weight:String="Bold"):Float {
        setup(size,weight); val fm=paint.fontMetrics; return ceil(fm.descent-fm.ascent)
    }
    private fun text(c:Canvas,text:String,x:Float,y:Float,size:Float,weight:String="Bold",color:Int=0x111111,
                     line:Float?=null,tracking:Float=0f,tabular:Boolean=false,center:Boolean=false,alpha:Float=1f):Float {
        setup(size,weight,color,tabular)
        val w=paint.measureText(text)+tracking*text.length
        val fm=paint.fontMetrics; val h=line?:ceil(fm.descent-fm.ascent)
        var xx=x-if(center)w/2 else 0f
        val baseline=y+(h-(fm.descent-fm.ascent))/2-fm.ascent
        paint.alpha=(alpha.coerceIn(0f,1f)*255).roundToInt()
        if(tracking==0f)c.drawText(text,xx,baseline,paint)
        else for(ch in text) { val s=ch.toString(); c.drawText(s,xx,baseline,paint); xx+=paint.measureText(s)+tracking }
        return w
    }
    private fun rr(c:Canvas,x:Float,y:Float,w:Float,h:Float,r:Float,color:Int) {
        paint.reset(); paint.isAntiAlias=true; paint.color=ink(color)
        rect.set(x,y,x+w,y+h); c.drawRoundRect(rect,r,r,paint)
    }
    private fun icon(c:Canvas,name:String,x:Float,y:Float,size:Float=22f,color:Int=0x111111,stroke:Float=1.9f) {
        paint.reset(); paint.isAntiAlias=true; paint.color=ink(color); paint.style=Paint.Style.STROKE
        paint.strokeWidth=stroke; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        c.save(); c.translate(x,y); c.scale(size/24,size/24); c.drawPath(icons.getValue(name),paint); c.restore()
    }
    fun draw(c:Canvas,l:HoloCashCardLayout,s:HoloCashCardMotion.State,density:Float,pressed:Int) {
        val key="${l.w},${l.h},${l.topInset},${l.bottomInset},$density"
        if(key!=pageKey) {
            page?.recycle(); page=Bitmap.createBitmap((l.w*density).roundToInt(),(l.h*density).roundToInt(),Bitmap.Config.ARGB_8888)
            val pc=Canvas(page!!); pc.scale(density,density); static(pc,l); pageKey=key
        }
        c.drawBitmap(page!!,0f,0f,null)
        c.save(); c.scale(density,density)
        dynamic(c,l,s); card(c,l,s)
        for(index in 0..1) {
            val w=(l.cw-10)/2; val x=18+index*(w+10); val y=l.actionsY
            c.save()
            if(pressed==index+5)c.scale(0.97f,0.97f,x+w/2,y+25)
            rr(c,x,y,w,50f,14f,0xffffff)
            val label=if(index==0)"Add Balance" else "Withdraw"
            val tw=width(label,15f); val start=x+(w-18-8-tw)/2
            icon(c,if(index==0)"down" else "up",start,y+16,18f)
            text(c,label,start+26,y+(50-lineHeight(15f))/2,15f)
            c.restore()
        }
        c.restore()
    }
    private fun static(c:Canvas,l:HoloCashCardLayout) {
        c.drawColor(ink(0xeeeeef))
        c.save(); c.rotate(-6f,33f,l.top+20)
        rr(c,18f,l.top+5,30f,30f,8f,0x111111)
        text(c,"LC",33f,l.top+20-lineHeight(11f)/2,11f,color=0xffffff,center=true); c.restore()
        for((name,x) in listOf("eye" to l.w-104,"chat" to l.w-58)) {
            rr(c,x,l.top,40f,40f,12f,0xffffff); icon(c,name,x+9,l.top+9)
        }
        rr(c,18f,l.balanceY,l.cw,126f,16f,0xffffff)
        val bw=text(c,"Balance",32f,l.balanceY+12,14f)
        icon(c,"info",32+bw+6,l.balanceY+13.5f,15f,0x8a8a8f,2f)
        text(c,"Total rewarded: $100",l.w/2,l.balanceY+84,12f,"Medium",0x8a8a8f,line=16f,center=true)
        text(c,"Balance is held in full in dUSD",l.w/2,l.balanceY+100,12f,"Medium",0x8a8a8f,line=16f,center=true)
        rr(c,18f,l.loyalY,l.cw,76f,16f,0xffffff)
        text(c,"Loyalty program",32f,l.loyalY+12,14f)
        val sw=width("Cashback",13f,"Medium"); val tagw=width("1%",12f)+8; val tx=l.w-32-sw-6-tagw
        rr(c,tx,l.loyalY+12,tagw,18f,4f,0xe93ad8)
        text(c,"1%",tx+4,l.loyalY+12,12f,color=0xffffff,line=18f)
        text(c,"Cashback",l.w-32-sw,l.loyalY+12+(18-lineHeight(13f,"Medium"))/2,13f,"Medium")
        val ph=max(lineHeight(14f),18f); val py=l.loyalY+12+ph+8
        val pw=text(c,"2,000",32f,py,20f,tabular=true); val cy=py+lineHeight(20f)/2
        rr(c,32+pw+6,cy-6,12f,12f,6f,0xbcd0ff); rr(c,34+pw+6,cy-4,8f,8f,4f,0x2d6bff)
        rr(c,18f,l.tabsY,l.cw,64f,22f,0xffffff)
        val space=(l.cw-64-4*22)/5; var x=18+space/2
        for((index,name) in listOf("wallet","grid","send","ring","check").withIndex()) {
            if(index==2) {
                c.save(); c.translate(x+32,l.tabsY+19); c.rotate(-10f)
                rr(c,-33f,-33f,66f,66f,33f,0xffffff); rr(c,-30f,-30f,60f,60f,30f,0xf04fd8)
                text(c,"SEND",0f,-lineHeight(20f,"Anton")/2,20f,"Anton",tracking=0.5f,center=true)
                c.restore(); x+=64+space
            } else { icon(c,name,x,l.tabsY+21,22f,if(index==0)0x111111 else 0x9a9aa0); x+=22+space }
        }
        val rw=max(width("REFER",8f),width("AND EARN",8f))+12
        c.save(); c.translate(l.w-12-rw/2,l.tabsY-13); c.rotate(14f)
        rr(c,-rw/2,-13f,rw,26f,0f,0x5ec8f2)
        text(c,"REFER",0f,-9f,8f,line=9f,center=true); text(c,"AND EARN",0f,0f,8f,line=9f,center=true); c.restore()
    }
    private fun dynamic(c:Canvas,l:HoloCashCardLayout,s:HoloCashCardMotion.State) {
        val rw=width("Rewards of",13f,"Medium"); val aw=width("APY",13f,"Medium")
        val dw=width("8",12f); val pw=width("%",12f); val tagw=dw+pw+8
        val x=l.w-32-(rw+6+tagw+6+aw); val y=l.balanceY+12
        text(c,"Rewards of",x,y+(18-lineHeight(13f,"Medium"))/2,13f,"Medium")
        rr(c,x+rw+6,y,tagw,18f,4f,0xe93ad8)
        c.save(); c.clipRect(x+rw+10,y,x+rw+10+dw,y+18)
        val sy=y-((s.apy-3)/5*18).toFloat()
        text(c,"3",x+rw+10,sy,12f,color=0xffffff,line=18f); text(c,"8",x+rw+10,sy+18,12f,color=0xffffff,line=18f)
        c.restore()
        text(c,"%",x+rw+10+dw,y,12f,color=0xffffff,line=18f)
        text(c,"APY",l.w-32-aw,y+(18-lineHeight(13f,"Medium"))/2,13f,"Medium")
        val bigw=width("0",38f,tracking = -1f,tabular=true); val midw=width("0",20f,tabular=true)
        val dollarw=width("$",20f); val commaw=width(",",38f,tracking = -1f,tabular=true); val dotw=width(".",20f)
        var dx=(l.w-dollarw-2-bigw*4-commaw-dotw-midw*5)/2
        val bottom=l.balanceY+84; val a=(1-s.hide).toFloat()
        text(c,"$",dx,bottom-30,20f,line=30f,alpha=a); dx+=dollarw+2
        fun digit(k:Int,w:Float,size:Float,lh:Float,y0:Float,grey:Boolean=false) {
            val yy=y0+if(grey)(s.dip*6).toFloat() else 0f
            val alpha=if(grey)(a*(1-s.dip)*(1-s.hide)).toFloat() else a
            val m=((s.odo.disp[k]%10)+10)%10
            c.save(); c.clipRect(dx,yy,dx+w,yy+lh)
            for(n in 0..10) {
                val ny=yy+(n-m).toFloat()*lh
                if(ny+lh>=yy && ny<=yy+lh)text(c,"${n%10}",dx,ny,size,color=if(grey)0xa5a5aa else 0x111111,
                    line=lh,tracking=if(size==38f)-1f else 0f,tabular=true,alpha=alpha)
            }
            c.restore(); dx+=w
        }
        digit(0,bigw,38f,40f,bottom-40)
        text(c,",",dx,bottom-lineHeight(38f),38f,tracking = -1f,tabular=true,alpha=a); dx+=commaw
        for(k in 1..3)digit(k,bigw,38f,40f,bottom-40)
        text(c,".",dx,bottom-28,20f,line=24f,tabular=true,alpha=a); dx+=dotw
        for(k in 4..8)digit(k,midw,20f,24f,bottom-28,k>=6)
        text(c,"$ • • • • •",l.w/2,l.balanceY+80-lineHeight(30f),30f,tracking=2f,center=true,alpha=s.hide.toFloat())
        val small=if(s.boost)"BOOSTED" else "BOOST TO"; val big=if(s.boost)"ON" else "8%"
        val w=max(width(small,7f),width(big,18f,"Anton"))+14; val h=lineHeight(7f)+28
        c.save(); c.translate(l.w-36-w/2,l.balanceY+42+h/2); c.rotate((12-s.boostK*6).toFloat())
        val scale=s.stickerScale().toFloat(); c.scale(scale,scale)
        rr(c,-w/2,-h/2,w,h,3f,0xf5ec3d)
        text(c,small,0f,-h/2+4,7f,center=true); text(c,big,0f,-h/2+4+lineHeight(7f),18f,"Anton",line=20f,center=true)
        c.restore()
    }
    private fun card(c:Canvas,l:HoloCashCardLayout,s:HoloCashCardMotion.State) {
        c.save(); c.translate(18f,l.cardY)
        rect.set((8-s.ry*0.5).toFloat(),(12+s.rx*0.5).toFloat(),(l.cw-8-s.ry*0.5).toFloat(),(l.ch+2+s.rx*0.5).toFloat())
        c.drawRoundRect(rect,16f,16f,shadow)
        val x=s.rx*PI/180; val y=s.ry*PI/180; val cx=l.cw/2; val cy=l.ch/2
        val src=floatArrayOf(0f,0f,l.cw,0f,l.cw,l.ch,0f,l.ch); val dst=FloatArray(8)
        for(i in 0..3) {
            val xx=src[i*2]-cx; val yy=src[i*2+1]-cy
            val z = -cos(x)*sin(y)*xx+sin(x)*yy; val den=1-z/700
            dst[i*2]=(cx+cos(y)*xx/den).toFloat(); dst[i*2+1]=(cy+(sin(x)*sin(y)*xx+cos(x)*yy)/den).toFloat()
        }
        matrix.setPolyToPoly(src,0,dst,0,4); c.concat(matrix)
        rr(c,0f,0f,l.cw,l.ch,16f,0x0b0b0d)
        if(Build.VERSION.SDK_INT>=33 && shaders!=null) {
            shaders.update(l.cw,l.ch,x.toFloat(),y.toFloat(),s.boostK.toFloat())
            paint.reset(); paint.isAntiAlias=true; paint.shader=shaders.lines
            rect.set(0f,0f,l.cw,l.ch); c.drawRoundRect(rect,16f,16f,paint)
            paint.shader=shaders.rim; paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
            paint.alpha=((0.5+0.3*s.boostK)*255).roundToInt()
            rect.set(0.75f,0.75f,l.cw-0.75f,l.ch-0.75f); c.drawRoundRect(rect,15.5f,15.5f,paint)
        } else {
            paint.reset(); paint.isAntiAlias=true; paint.color=ink(0xc4b6de); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
            rect.set(0.75f,0.75f,l.cw-0.75f,l.ch-0.75f); c.drawRoundRect(rect,15.5f,15.5f,paint)
        }
        setup(l.ch*0.58f,"Anton",0xffffff)
        val wordw=paint.measureText("LOUDCASH"); val sx=(l.cw-28)/wordw
        if(Build.VERSION.SDK_INT>=33 && shaders!=null)paint.shader=shaders.foil
        else paint.shader=LinearGradient(0f,-l.ch,l.cw,0f,intArrayOf(ink(0xf5b3e5),ink(0xb8a8ff),ink(0x9eebff),ink(0xc7ffd6)),null,Shader.TileMode.CLAMP)
        c.save(); c.translate(14f,l.ch*0.69f); c.scale(sx,1f); c.drawText("LOUDCASH",0f,0f,paint); c.restore()
        setup(10f,color=0xffffff); c.drawText("YOUR BANK",14f,l.ch-26,paint); c.drawText("WON'T DO THIS",14f,l.ch-14,paint)
        c.drawText("DEBIT",l.cw-14-paint.measureText("DEBIT"),l.ch-14,paint)
        c.restore()
    }
    fun close() { page?.recycle(); page=null }
    companion object {
        private val ICON_DATA=mapOf(
            "eye" to "M2 12c2.6-4.4 6-6.6 10-6.6S19.4 7.6 22 12c-2.6 4.4-6 6.6-10 6.6S4.6 16.4 2 12z M12 9a3 3 0 1 0 0.01 0",
            "chat" to "M4 12a8 8 0 1 1 3.4 6.5L4 20l1.2-3.4A7.9 7.9 0 0 1 4 12z",
            "down" to "M12 4v12 M6.5 11 12 16.5 17.5 11 M5 20h14",
            "up" to "M12 20V8 M6.5 13 12 7.5 17.5 13 M5 4h14",
            "info" to "M12 3a9 9 0 1 0 0.01 0 M12 11v6 M12 7.5v0.5",
            "wallet" to "M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M3 7l12-3v3 M16 13.5h2",
            "grid" to "M12 3l3 3-3 3-3-3z M6 9l3 3-3 3-3-3z M18 9l3 3-3 3-3-3z M12 15l3 3-3 3-3-3z",
            "ring" to "M12 4a8 8 0 1 0 0.01 0 M12 8a4 4 0 1 0 0.01 0",
            "check" to "M12 3a9 9 0 1 0 0.01 0 M8 12.5l2.8 2.8L16.5 9.5",
        )
    }
}
