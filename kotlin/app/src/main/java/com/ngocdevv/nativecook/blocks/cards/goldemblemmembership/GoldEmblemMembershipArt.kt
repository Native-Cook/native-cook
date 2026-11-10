package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

internal class GoldEmblemMembershipArt(context: Context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val fonts=listOf("Regular","Medium","SemiBold","Bold").associateWith {
        Typeface.createFromAsset(context.assets,"goldemblemmembership/goldemblemmembership_Outfit-$it.ttf")
    }
    private val runtime=if(Build.VERSION.SDK_INT>=33)GoldEmblemMembershipRuntime() else null
    private var layout: GoldEmblemMembershipTheme.Layout?=null
    private var pages=emptyList<Bitmap>()
    private var pixelScale=0f
    private fun path(s: String)=requireNotNull(PathParser.createPathFromPathData(s))
    private val ticket=path("M6 6 H40 A2 2 0 0 1 42 8 V12 A4 4 0 0 0 42 20 V24 A2 2 0 0 1 40 26 H6 A2 2 0 0 1 4 24 V20 A4 4 0 0 0 4 12 V8 A2 2 0 0 1 6 6 Z")
    private val ticketDash=path("M16 8 V24")
    private val gift=path("M6 10 H28 V16 H6 Z M8 16 H26 V29 H8 Z M17 10 V29 M17 10 C13 4 9 6 12 10 M17 10 C21 4 25 6 22 10")
    private val welfare=path("M12 3 H22 V9 H12 Z M13 18 L17 22 L21 18")
    private val redeem=path("M11 21 L7 28 L11 27.5 L13 31 L15.5 23 M23 21 L27 28 L23 27.5 L21 31 L18.5 23")
    private val starSmall=path("M17 8.5 L18.4 11.5 L21.6 11.9 L19.2 14 L19.9 17.2 L17 15.6 L14.1 17.2 L14.8 14 L12.4 11.9 L15.6 11.5 Z")
    private val ribbons=path("M10 22 L4 36 L11 33 L14 39 L19 26 Z M26 22 L32 36 L25 33 L22 39 L17 26 Z")
    private val starBadge=path("M18 9.5 L19.9 13.6 L24.3 14.1 L21 17.1 L21.9 21.5 L18 19.3 L14.1 21.5 L15 17.1 L11.7 14.1 L16.1 13.6 Z")
    private val gold=0xfff1c987.toInt()
    private fun fill(color: Int) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color; paint.alpha=255 }
    private fun stroke(color: Int, width: Float) { fill(color); paint.style=Paint.Style.STROKE; paint.strokeWidth=width; paint.strokeCap=Paint.Cap.BUTT; paint.strokeJoin=Paint.Join.MITER }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, weight: String="Regular", color: Int=Color.WHITE, centered: Boolean=false, alpha: Double=1.0): Float {
        fill(color); paint.typeface=fonts[weight]; paint.textSize=size; paint.alpha=(alpha*Color.alpha(color)).roundToInt().coerceIn(0,255)
        paint.textAlign=if(centered)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s,x,y-paint.fontMetrics.ascent,paint)
        return paint.measureText(s)
    }
    private fun round(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        fill(color); c.drawRoundRect(x,y,x+w,y+h,r,r,paint)
    }
    private fun icon(c: Canvas, kind: Int) {
        val ink=0xff2a2a2c.toInt()
        fill(gold)
        when(kind) {
            0 -> { c.drawRect(10f,13f,30f,30f,paint); stroke(ink,1.8f); c.drawPath(gift,paint) }
            1 -> { c.drawCircle(19f,21f,9f,paint); stroke(ink,1.8f); c.drawCircle(17f,19f,10f,paint); paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND; c.drawPath(welfare,paint) }
            2 -> { c.drawCircle(19f,15f,9f,paint); stroke(ink,1.8f); c.drawCircle(17f,13f,9.5f,paint); paint.strokeJoin=Paint.Join.ROUND; c.drawPath(redeem,paint); paint.strokeWidth=1.4f; c.drawPath(starSmall,paint) }
            3 -> {
                fill(0xffe9c27e.toInt()); c.drawPath(ticket,paint)
                stroke(0xffc99a55.toInt(),1.2f); paint.pathEffect=DashPathEffect(floatArrayOf(2f,2f),0f); c.drawPath(ticketDash,paint); paint.pathEffect=null
                paint.strokeWidth=1.4f; c.drawCircle(29f,16f,5f,paint)
            }
            else -> { fill(0xfff07a34.toInt()); c.drawPath(ribbons,paint); fill(0xfff7a341.toInt()); c.drawCircle(18f,16f,12f,paint); fill(Color.WHITE); c.drawPath(starBadge,paint) }
        }
        paint.style=Paint.Style.FILL
    }
    private fun staticPage(c: Canvas, l: GoldEmblemMembershipTheme.Layout, shown: Int) {
        val sx=l.sx; val tier=GoldEmblemMembershipTheme.tiers[shown]
        c.drawColor(Color.WHITE); fill(0xff26272b.toInt()); c.drawRect(0f,0f,l.w,l.y(483f),paint)
        round(c,0f,l.cardY+17,9f,l.cardH-34,4f,if(shown>0)GoldEmblemMembershipTheme.tiers[shown-1].sliver else 0xff4a4b50.toInt())
        round(c,l.w-9,l.cardY+17,9f,l.cardH-34,4f,if(shown<2)GoldEmblemMembershipTheme.tiers[shown+1].sliver else 0xff4a4b50.toInt())
        listOf(Triple("81.57","Withdraw",68f),Triple("869.23","Interest expense",205f),Triple("559.33","Reward",333f)).forEach { (value,label,x) ->
            text(c,value,x*sx,l.y(333f)-11,15f,"SemiBold",centered=true)
            val height=ceil(-paint.fontMetrics.ascent+paint.fontMetrics.descent)
            text(c,label,x*sx,l.y(333f)-11+height+6,11.5f,color=0xd1ffffff.toInt(),centered=true)
        }
        round(c,17*sx,l.y(400f),367*sx,l.y(483f)-l.y(400f)+10,10f,0xff37383d.toInt())
        // The dark strip has square bottom corners and ends at the white section.
        fill(Color.WHITE); c.drawRect(0f,l.y(483f),l.w,l.h,paint)
        c.save(); c.translate(70*sx-23,l.y(445f)-16); icon(c,3); c.restore()
        val pctW=text(c,tier.coupon,112*sx,l.y(445f)-22,30f,"Bold")
        // The smaller percent shares the large number's baseline.
        fill(Color.WHITE); paint.typeface=fonts["Bold"]; paint.textSize=30f; val baseline=l.y(445f)-22-paint.fontMetrics.ascent
        paint.typeface=fonts["SemiBold"]; paint.textSize=15f; c.drawText("%",112*sx+pctW,baseline,paint)
        text(c,"Gift coupon",162*sx+(tier.coupon.length-1)*12,l.y(445f)-3,13f)
        text(c,"My privilege",18*sx,l.y(529f)-12,17f,"Bold",0xff1d1d1f.toInt())
        fill(0xff9a9a9e.toInt()); paint.typeface=fonts["Regular"]; paint.textSize=13f
        val allW=paint.measureText("View all ›"); text(c,"View all ›",l.w-26*sx-allW,l.y(529f)-9,13f,color=0xff9a9a9e.toInt())
        listOf(Triple("Gift package","$60",66f),Triple("Welfare day","$150",200f),Triple("Redeem","7.6%",333f)).forEachIndexed { i,(name,value,x) ->
            c.save(); c.translate(x*sx-17,l.y(580f)-16); icon(c,i); c.restore()
            text(c,name,x*sx,l.y(618f)-8,13f,color=0xff333336.toInt(),centered=true)
            val height=ceil(-paint.fontMetrics.ascent+paint.fontMetrics.descent)
            text(c,value,x*sx,l.y(618f)-8+height+6,11.5f,color=0xffd4a866.toInt(),centered=true)
        }
        text(c,"Member benefits",17*sx,l.y(700f)-12,17f,"Bold",0xff1d1d1f.toInt())
        val x=17*sx; val y=l.y(735f); val w=l.cardW; val h=105*l.k
        fill(Color.WHITE); paint.shader=LinearGradient(x,y,x+w,y+h,0xfff3d79f.toInt(),0xffddb373.toInt(),Shader.TileMode.CLAMP)
        c.drawRoundRect(x,y,x+w,y+h,10f,10f,paint); paint.shader=null
        round(c,x+18,y+(h-61)/2,62f,61f,10f,Color.WHITE)
        c.save(); c.translate(x+18+13,y+(h-40)/2); icon(c,4); c.restore()
        val tx=x+96; val amountY=y+h/2-29
        val signW=text(c,"$",tx,amountY+11,15f,"Bold",0xff2a1e10.toInt())
        text(c,"10",tx+signW,amountY,26f,"Bold",0xff2a1e10.toInt())
        text(c,"Member package",tx,amountY+35,13f,color=0xff3a2a14.toInt())
    }
    fun draw(c: Canvas, l: GoldEmblemMembershipTheme.Layout, f: GoldEmblemMembershipMotion.Frame, density: Float, now: Double, reduced: Boolean) {
        if(layout!=l || pixelScale!=density) {
            pages.forEach { it.recycle() }; layout=l; pixelScale=density
            pages=(0..2).map { tier ->
                Bitmap.createBitmap(ceil(l.w*density).toInt(),ceil(l.h*density).toInt(),Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas=Canvas(bitmap); canvas.scale(density,density); staticPage(canvas,l,tier)
                }
            }
        }
        fill(Color.WHITE); c.save(); c.scale(1/density,1/density); c.drawBitmap(pages[f.shown],0f,0f,paint); c.restore()
        c.save(); c.translate(f.drag.toFloat(),l.cardY-10)
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
            runtime.update(l,f,now,reduced); fill(Color.WHITE); paint.shader=runtime.shader
            c.drawRect(0f,0f,l.w,l.cardH+60,paint); paint.shader=null
        } else {
            val colors=intArrayOf(0xffd5b985.toInt(),0xffc4cdd1.toInt(),0xff2c2d31.toInt())
            round(c,l.cardX,10f,l.cardW,l.cardH,10f,colors[f.shown])
        }
        val tier=GoldEmblemMembershipTheme.tiers[f.shown]
        text(c,tier.name,50*l.sx,(10+37*l.k-16+f.wordsY).toFloat(),23f,"SemiBold",tier.ink,alpha=f.words)
        text(c,tier.range,50*l.sx,(10+66*l.k-9+f.wordsY).toFloat(),12.5f,color=tier.sub,alpha=f.words)
        text(c,tier.next,50*l.sx,(10+151*l.k-8+f.wordsY).toFloat(),11.5f,color=tier.sub,alpha=f.words)
        c.restore()
        if(f.finger>0) {
            val x=(l.cardX+l.cardW/2+f.fingerX).toFloat(); val y=l.cardY+l.cardH/2; val radius=(22*(1-0.12*f.press)).toFloat()
            fill(Color.WHITE); paint.alpha=(25.5*f.finger).roundToInt(); c.drawCircle(x,y,radius,paint)
            stroke(Color.WHITE,1.5f); paint.alpha=(153*f.finger).roundToInt(); c.drawCircle(x,y,radius-0.75f,paint)
        }
        fill(Color.WHITE)
    }
}
