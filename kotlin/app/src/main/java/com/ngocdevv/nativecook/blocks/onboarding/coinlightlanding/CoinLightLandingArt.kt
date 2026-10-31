package com.ngocdevv.nativecook.blocks.onboarding.coinlightlanding

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import kotlin.math.*

internal class CoinLightLandingArt(context: Context) {
    private val fonts=listOf("InterDisplay-Bold","Inter-Regular","Inter-Medium","Inter-SemiBold").associateWith { Typeface.createFromAsset(context.assets,"coinlightlanding/coinlightlanding_$it.ttf") }
    private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val runtime=if(Build.VERSION.SDK_INT>=33) CoinLightLandingShader() else null
    private var page: Bitmap?=null
    private var pageKey=""
    private val payouts=mutableMapOf<Int,Bitmap>()
    private val secondary=Bitmap.createBitmap(176*3,44*3,Bitmap.Config.ARGB_8888).also { bitmap ->
        val c=Canvas(bitmap); c.scale(3f,3f)
        rr(c,0f,0f,176f,44f,22f,0xff1d1d21.toInt())
        center(c,"Watch the overview",0f,0f,176f,44f,15f,"Inter-SemiBold",Color.WHITE)
    }
    private val primary=Bitmap.createBitmap(164*3,44*3,Bitmap.Config.ARGB_8888).also { bitmap ->
        val c=Canvas(bitmap); c.scale(3f,3f)
        center(c,"Become a creator",0f,0f,164f,44f,15f,"Inter-SemiBold",0xff050506.toInt())
    }
    private val glow=Bitmap.createBitmap(272*3,152*3,Bitmap.Config.ARGB_8888).also { bitmap ->
        val c=Canvas(bitmap); c.scale(3f,3f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xffffc24a.toInt(); maskFilter=BlurMaskFilter(18f,BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(54f,54f,218f,98f,22f,22f,p)
    }
    private fun center(c: Canvas,s: String,x: Float,y: Float,w: Float,h: Float,size: Float,face: String,color: Int) {
        textPaint.typeface=fonts[face]; textPaint.textSize=size; textPaint.color=color; textPaint.letterSpacing=0f
        c.drawText(s,x+(w-textPaint.measureText(s))/2,y+h/2-(textPaint.ascent()+textPaint.descent())/2,textPaint)
    }
    private fun rr(c: Canvas,x: Float,y: Float,w: Float,h: Float,r: Float,color: Int) {
        paint.reset(); paint.isAntiAlias=true; paint.color=color; c.drawRoundRect(x,y,x+w,y+h,r,r,paint)
    }
    private class Face(private val face: Typeface): MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.typeface=face }
        override fun updateMeasureState(p: TextPaint) { p.typeface=face }
    }
    private fun row(c: Canvas,i: Int,y: Float,paid: Int,width: Int) {
        rr(c,22f,y+2,18f,18f,4f,Color.WHITE)
        center(c,listOf("▣","♥","∞","$")[i],22f,y+2,18f,18f,11f,"Inter-SemiBold",0xff050506.toInt())
        val lead=listOf("Sell your work on the ","Keep 100% of your ","Refer creators and get up to ","$${String.format(java.util.Locale.US,"%,d",paid)} paid to creators ")[i]
        val end=listOf("Plinth Market","Market earnings","50% commission","in October")[i]
        val s=SpannableString(lead+end)
        s.setSpan(Face(fonts.getValue("Inter-Medium")),lead.length,s.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(android.text.style.ForegroundColorSpan(Color.WHITE),lead.length,s.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textPaint.typeface=fonts["Inter-Regular"]; textPaint.textSize=15f; textPaint.color=0x99ffffff.toInt(); textPaint.letterSpacing=0f
        val layout=StaticLayout.Builder.obtain(s,0,s.length,textPaint,width-72).setIncludePad(false).setLineSpacing(20f-(textPaint.descent()-textPaint.ascent()),1f).build()
        c.save(); c.translate(50f,y); layout.draw(c); c.restore()
    }
    private fun payout(paid: Int,w: Int): Bitmap = payouts.getOrPut(paid) {
        if(payouts.size>160)payouts.clear()
        Bitmap.createBitmap(w*3,42*3,Bitmap.Config.ARGB_8888).also { val c=Canvas(it); c.scale(3f,3f); row(c,3,0f,paid,w) }
    }
    private fun makePage(w: Int,h: Int,top: Float,bottom: Float): Bitmap {
        val b=Bitmap.createBitmap(w*3,h*3,Bitmap.Config.ARGB_8888)
        val c=Canvas(b); c.scale(3f,3f); c.drawColor(0xff050506.toInt())
        listOf(10f,16f,22f).forEachIndexed { i,bw -> rr(c,33-bw/2,top+18.5f+i*8,bw,5f,1.5f,Color.WHITE) }
        listOf(top+23.5f,top+30.5f).forEach { y -> rr(c,w-50f,y,20f,2f,1f,Color.WHITE) }
        textPaint.typeface=fonts["InterDisplay-Bold"]; textPaint.textSize=36f; textPaint.color=Color.WHITE; textPaint.letterSpacing=-0.8f/36
        val title="Get paid\nto create"
        val layout=StaticLayout.Builder.obtain(title,0,title.length,textPaint,w-44).setIncludePad(false).setLineSpacing(40f-(textPaint.descent()-textPaint.ascent()),1f).build()
        c.save(); c.translate(22f,top+58); layout.draw(c); c.restore()
        for(i in 0..2)row(c,i,top+156+i*40,733000,w)
        textPaint.typeface=fonts["Inter-Medium"]; textPaint.textSize=12f; textPaint.letterSpacing=0f
        val dw=textPaint.measureText("plinth.market")+28
        rr(c,(w-dw)/2,h-bottom-48,dw,30f,15f,0xff17171a.toInt())
        center(c,"plinth.market",(w-dw)/2,h-bottom-48,dw,30f,12f,"Inter-Medium",0xbfffffff.toInt())
        for(n in 733000..733048)payout(n,w)
        return b
    }
    fun draw(c: Canvas,w: Float,h: Float,top: Float,bottom: Float,f: CoinLightLandingMotion.Frame,reduced: Boolean,overviewPressed: Boolean) {
        val key="$w/$h/$top/$bottom"
        if(pageKey!=key) { pageKey=key; payouts.clear(); page=makePage(w.roundToInt(),h.roundToInt(),top,bottom) }
        paint.reset(); paint.isAntiAlias=true; paint.isFilterBitmap=true
        page?.let { c.drawBitmap(it,null,RectF(0f,0f,w,h),paint) }
        paint.alpha=((1-f.dip)*255).roundToInt().coerceIn(0,255)
        c.drawBitmap(payout(f.pay.roundToInt(),w.roundToInt()),null,RectF(0f,top+276+(f.dip*4).toFloat(),w,top+318+(f.dip*4).toFloat()),paint)
        c.save(); c.translate(104f,top+354)
        val scale=(1+0.04*f.button).toFloat(); c.scale(scale,scale)
        rr(c,-82f,-22f,164f,44f,22f,Color.rgb(255,(255-50*f.button).roundToInt(),(255-150*f.button).roundToInt()))
        paint.reset(); paint.isFilterBitmap=true; c.drawBitmap(primary,null,RectF(-82f,-22f,82f,22f),paint); c.restore()
        c.save(); c.translate(284f,top+354)
        val secondaryScale=if(overviewPressed)0.97f else 1f; c.scale(secondaryScale,secondaryScale)
        paint.reset(); paint.isFilterBitmap=true
        c.drawBitmap(secondary,null,RectF(-88f,-22f,88f,22f),paint); c.restore()
        val lx=CoinLightLandingMotion.mix(104.0,f.x,f.a).toFloat(); val ly=CoinLightLandingMotion.mix((top+354).toDouble(),f.y,f.a).toFloat()
        val power=if(reduced)0f else max(f.a,f.button).toFloat()
        fun radial(x: Float,y: Float,r: Float,colors: IntArray,stops: FloatArray) {
            paint.reset(); paint.isAntiAlias=true; paint.shader=RadialGradient(x,y,r,colors,stops,Shader.TileMode.CLAMP); c.drawRect(0f,0f,w,h,paint)
        }
        if(!reduced)radial(lx,ly,220+140*power,intArrayOf(0x00050506,0x33050506,0x7a050506),floatArrayOf(0f,0.55f,1f))
        radial(lx,ly,190f,intArrayOf(Color.argb((0.22*power*255).roundToInt(),255,170,70),0x00ffaa46),floatArrayOf(0f,1f))
        for((x,bw) in listOf(104f to 164f,284f to 176f)) {
            val y=top+354; val dx=lx-x; val dy=ly-y; val d=max(1f,hypot(dx,dy)); val k=(1-d/360).coerceIn(0f,1f)*power
            if(k>=0.02f) {
                paint.reset(); paint.isAntiAlias=true; paint.style=Paint.Style.STROKE; paint.strokeWidth=1.6f
                paint.shader=LinearGradient(x+dx/d*bw/2,y+dy/d*22,x-dx/d*bw/2,y-dy/d*22,intArrayOf(Color.argb((255*k).roundToInt(),255,205,120),0x00ffcd78),floatArrayOf(0f,0.7f),Shader.TileMode.CLAMP)
                c.drawRoundRect(x-bw/2,y-22,x+bw/2,y+22,22f,22f,paint)
            }
        }
        if(f.button>0.01) {
            paint.reset(); paint.isFilterBitmap=true; paint.alpha=(255*0.55*f.button).roundToInt()
            c.drawBitmap(glow,null,RectF(-32f,top+278,240f,top+430),paint)
        }
        if(f.a>0.01) {
            radial(f.x.toFloat(),f.y.toFloat(),(182*f.s).toFloat(),intArrayOf(Color.argb((255*0.55*f.a).roundToInt(),255,190,90),Color.argb((255*0.12*f.a).roundToInt(),255,150,50),0x00ff9632),floatArrayOf(0f,0.45f,1f))
            paint.reset(); paint.isAntiAlias=true
            if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
                paint.shader=runtime.update(f); paint.alpha=(255*f.a).roundToInt(); c.drawRect(0f,0f,w,h,paint)
            } else {
                c.save(); c.translate(f.x.toFloat(),f.y.toFloat())
                c.scale((abs(cos(f.yaw+f.tx))+0.09*abs(sin(f.yaw+f.tx))).toFloat()*f.s.toFloat(),cos(0.55).toFloat()*f.s.toFloat())
                paint.color=Color.rgb(233,175,71); paint.alpha=(255*f.a).roundToInt(); c.drawCircle(0f,0f,70f,paint)
                paint.style=Paint.Style.STROKE; paint.strokeWidth=3f; paint.color=Color.rgb(255,206,114); c.drawCircle(0f,0f,59f,paint)
                paint.style=Paint.Style.FILL
                for((i,bw) in listOf(31f,50f,70f).withIndex())c.drawRoundRect(-bw/2,-27f+i*20,bw/2,-14f+i*20,2f,2f,paint)
                c.restore()
            }
        }
    }
}
