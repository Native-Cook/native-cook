package com.ngocdevv.nativecook.blocks.onboarding.ribbonroadonboarding

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

/** Static text/art is rasterized once for a size; only the road and button transforms change. */
internal class RibbonRoadOnboardingArt(context: Context,val w: Float,val h: Float,val top: Float,val bottom: Float,val density: Float) {
    private val regular=Typeface.createFromAsset(context.assets,"ribbonroadonboarding/ribbonroadonboarding_Manrope_400Regular.ttf")
    private val semi=Typeface.createFromAsset(context.assets,"ribbonroadonboarding/ribbonroadonboarding_Manrope_600SemiBold.ttf")
    private val archivo=Typeface.createFromAsset(context.assets,"ribbonroadonboarding/ribbonroadonboarding_Archivo-ExtraBold.ttf")
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=archivo; textSize=21f }
    private val pattern="NEW EVENTS FOR YOU  ·  "
    private val advances=FloatArray(pattern.length).also { text.getTextWidths(pattern,it); for(i in it.indices)it[i]+=0.6f }
    private val patternLength=advances.sum().toDouble()
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=46f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private val shadowPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x73000000; style=Paint.Style.STROKE; strokeWidth=50f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; maskFilter=BlurMaskFilter(9f,BlurMaskFilter.Blur.NORMAL) }
    private val bitmapPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadow=Bitmap.createBitmap(ceil(w).toInt(),ceil(h).toInt(),Bitmap.Config.ARGB_8888)
    private val shadowCanvas=Canvas(shadow)
    private val path=Path(); private val part=Path(); private val measure=PathMeasure()
    private val pos=FloatArray(2); private val tan=FloatArray(2)
    private val page=Bitmap.createBitmap(ceil(w*density).toInt(),ceil(h*density).toInt(),Bitmap.Config.ARGB_8888)
    private val controlImages=Array(3) { i -> makeControl(i,if(i==0)w-180 else 56f) }
    init { Canvas(page).apply { scale(this@RibbonRoadOnboardingArt.density,this@RibbonRoadOnboardingArt.density); staticPage(this) } }
    private fun face(size: Float,semibold: Boolean=true) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=if(semibold)semi else regular; textSize=size; color=Color.WHITE }
    private fun width(value: String,p: Paint,spacing: Float=0f)=p.measureText(value)+spacing*value.length
    private fun label(c: Canvas,value: String,x: Float,y: Float,p: Paint,line: Float,spacing: Float=0f) {
        val fm=p.fontMetrics; val baseline=y+(line-(fm.descent-fm.ascent))/2-fm.ascent
        var xx=x
        for(ch in value) { val s=ch.toString(); c.drawText(s,xx,baseline,p); xx+=p.measureText(s)+spacing }
    }
    private fun icon(c: Canvas,kind: Int) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=if(kind==0)2.4f else if(kind==1)2.2f else 2f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; color=if(kind==1)0xff17171a.toInt() else Color.WHITE }
        val path=Path()
        when(kind) {
            0 -> { path.moveTo(5f,11f); path.lineTo(17f,11f); path.moveTo(12f,6f); path.lineTo(17f,11f); path.lineTo(12f,16f) }
            1 -> { path.moveTo(4f,14f); path.lineTo(14f,4f); path.moveTo(6f,4f); path.lineTo(14f,4f); path.lineTo(14f,12f) }
            else -> { path.addCircle(8f,12f,4.5f,Path.Direction.CW); path.moveTo(12.5f,12f); path.lineTo(20f,12f); path.moveTo(17f,12f); path.lineTo(17f,15.5f); path.moveTo(20f,12f); path.lineTo(20f,15f) }
        }
        c.drawPath(path,p)
    }
    private fun staticPage(c: Canvas) {
        val p=face(28f); val y=top+22
        val tw=width("Try new ",p,-0.4f); val sw=width("social",p,-0.4f); val ow=width("our ",p,-0.4f)
        label(c,"Try new ",28f,y,p,36f,-0.4f)
        val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xff3d34ec.toInt() }
        c.drawRoundRect(28+tw,y+2,28+tw+sw+16,y+34,10f,10f,fill)
        label(c,"social",36+tw,y+1,p,34f,-0.4f)
        label(c,"connections with",28f,y+36,p,36f,-0.4f)
        label(c,"our ",28f,y+72,p,36f,-0.4f)
        fill.color=0xffff6a3d.toInt(); c.drawCircle(28+ow+15,y+90,15f,fill)
        c.save(); c.translate(28+ow+4,y+79); icon(c,0); c.restore()
        label(c," assistance",28+ow+30,y+72,p,36f,-0.4f)
        val copy=face(15f,false).apply { color=0xc7ffffff.toInt() }
        val copyText="Your personal assistant to enrich your social life"
        val layout=StaticLayout.Builder.obtain(copyText,0,copyText.length,copy,(w-176).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(21-(copy.fontMetrics.descent-copy.fontMetrics.ascent),1f).build()
        c.save(); c.translate(88f,h-bottom-108-layout.height); layout.draw(c); c.restore()
    }
    private fun makeControl(kind: Int,width: Float): Bitmap {
        val bitmap=Bitmap.createBitmap(ceil(width*density).toInt(),ceil(56*density).toInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap); c.scale(density,density)
        val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=if(kind==0)0xffffe14d.toInt() else 0xff2a2a2f.toInt() }
        c.drawRoundRect(0f,0f,width,56f,28f,28f,fill)
        if(kind==0) {
            val p=face(17f).apply { color=0xff17171a.toInt() }; val tw=p.measureText("Get started"); val x=(width-tw-26)/2
            label(c,"Get started",x,0f,p,56f); c.translate(x+tw+8,19f); icon(c,1)
        } else if(kind==1) { val p=face(22f); label(c,"@",(56-p.measureText("@"))/2,15f,p,26f) }
        else { c.translate(16f,16f); icon(c,2) }
        return bitmap
    }
    fun draw(c: Canvas,d: RibbonRoadOnboardingDriver,reduced: Boolean) {
        c.drawColor(0xff18181b.toInt())
        val head=if(reduced)1.0 else RibbonRoadOnboardingMotion.easeInOut(RibbonRoadOnboardingMotion.seg(d.e,0.0,1.1))
        val tail=if(d.z>=0 && !reduced)RibbonRoadOnboardingMotion.seg(d.z,0.25,1.45).pow(3) else 0.0
        if(head>tail+0.001)for(r in 0..1) {
            val pts=d.lines[r]; val off=r*170
            path.reset(); path.moveTo((pts[0][0]+d.field.ox[off]).toFloat(),(pts[0][1]+d.field.oy[off]).toFloat())
            for(i in 1 until 169) {
                val x=pts[i][0]+d.field.ox[off+i]; val y=pts[i][1]+d.field.oy[off+i]
                val x1=pts[i+1][0]+d.field.ox[off+i+1]; val y1=pts[i+1][1]+d.field.oy[off+i+1]
                path.quadTo(x.toFloat(),y.toFloat(),((x+x1)/2).toFloat(),((y+y1)/2).toFloat())
            }
            path.lineTo((pts[169][0]+d.field.ox[off+169]).toFloat(),(pts[169][1]+d.field.oy[off+169]).toFloat())
            measure.setPath(path,false)
            val a=(tail*measure.length).toFloat(); val b=(head*measure.length).toFloat()
            part.reset(); measure.getSegment(a,b,part,true)
            if(r==1) {
                shadow.eraseColor(Color.TRANSPARENT); shadowCanvas.save(); shadowCanvas.translate(0f,6f); shadowCanvas.drawPath(part,shadowPaint); shadowCanvas.restore()
                c.drawBitmap(shadow,0f,0f,bitmapPaint)
            }
            stroke.color=if(r==0)0xff3d34ec.toInt() else 0xfff3f3f1.toInt(); c.drawPath(part,stroke)
            text.color=if(r==0)Color.WHITE else 0xff17171a.toInt()
            val ph=((((if(r==0)1 else -1)*d.phase)%patternLength)+patternLength)%patternLength
            var s=ph-patternLength; var j=0
            while(s<b) {
                val adv=advances[j]; val mid=s+adv/2
                if(mid>a+14 && mid<b-14) {
                    measure.getPosTan(mid.toFloat(),pos,tan)
                    val ox=pos[0]-tan[0]*adv/2-tan[1]*(21*0.7f/2)
                    val oy=pos[1]-tan[1]*adv/2+tan[0]*(21*0.7f/2)
                    c.save(); c.translate(ox,oy); c.rotate(atan2(tan[1],tan[0])*180f/PI.toFloat()); c.drawText(pattern,j,j+1,0f,0f,text); c.restore()
                }
                s+=adv; j=(j+1)%pattern.length
            }
        }
        c.drawBitmap(page,null,RectF(0f,0f,w,h),bitmapPaint)
        for(i in 0..2) {
            val cw=if(i==0)w-180 else 56f
            val x=when(i) { 0 -> 24f; 1 -> w-146; else -> w-80 }
            val y=h-bottom-80
            val pressed=d.pressed==i
            val scale=(if(i==0)(1-0.04*d.button).toFloat() else 1f)*(if(pressed)0.96f else 1f)
            c.save(); c.scale(scale,scale,x+cw/2,y+28)
            bitmapPaint.alpha=if(pressed)235 else 255
            c.drawBitmap(controlImages[i],null,RectF(x,y,x+cw,y+56),bitmapPaint)
            bitmapPaint.alpha=255; c.restore()
        }
    }
    fun close() { page.recycle(); shadow.recycle(); controlImages.forEach { it.recycle() } }
}
