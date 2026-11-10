package com.ngocdevv.nativecook.blocks.cards.sapphirewatchcrown

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.PathParser
import kotlin.math.*

/** Static page, glyphs, gradients and SVG paths are cached outside the animation loop. */
internal class SapphireWatchCrownArt(context: Context) {
    companion object {
        val metalNames=listOf("Black","Rose gold","Yellow gold","White gold")
        val metals=intArrayOf(0xff2e2e31.toInt(),0xffb9977d.toInt(),0xffe8b33d.toInt(),0xfff4f4f2.toInt())
        val sizes=listOf("11.5","12.3","13.6","14.79")
    }
    private val fonts=listOf("Regular","Medium","SemiBold").associateWith { Typeface.createFromAsset(context.assets,"sapphirewatchcrown/sapphirewatchcrown_Poppins-$it.ttf") }
    private val labels=mutableMapOf<String,Paint>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private val runtime=if(Build.VERSION.SDK_INT>=33)SapphireWatchCrownRuntime() else null
    private var cachedLayout: SapphireWatchCrownLayout?=null
    private var background: Bitmap?=null
    private val back=PathParser.createPathFromPathData("M8.5 1.5 L1.5 9 L8.5 16.5")!!
    private val cart=PathParser.createPathFromPathData("M1.5 3 H5 L7.5 15 H19 L21.5 7 H6.2")!!
    private val arrows=Path().apply {
        val a=PathParser.createPathFromPathData("M109 10 l-8 4 l8 4")!!
        a.transform(Matrix().apply { setRotate(-20f,109f,10f) }); addPath(a)
        val b=PathParser.createPathFromPathData("M347 109 l4 -7 l4 7")!!
        b.transform(Matrix().apply { setRotate(-15f,351f,109f) }); addPath(b)
    }
    init {
        for((size,weight) in listOf(17f to "SemiBold",12f to "Regular",19f to "SemiBold",11f to "Regular",17f to "Medium",13f to "Regular",15f to "Regular",16f to "SemiBold",11.5f to "Regular",13f to "SemiBold")) {
            labels["$weight-$size"]=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=fonts.getValue(weight); textSize=size }
        }
    }
    private fun label(c: Canvas,s: String,x: Double,y: Double,weight: String,size: Float,alpha: Float=1f,align: Paint.Align=Paint.Align.LEFT,color: Int=Color.WHITE) {
        val p=labels.getValue("$weight-$size"); p.color=color; p.alpha=(alpha*255).roundToInt(); p.textAlign=align
        c.drawText(s,x.toFloat(),y.toFloat()-p.fontMetrics.top,p)
    }
    private fun centered(c: Canvas,s: String,x: Double,y: Double,w: String,size: Float,color: Int=Color.WHITE) {
        val p=labels.getValue("$w-$size")
        label(c,s,x,y+(p.fontMetrics.top-p.fontMetrics.bottom)/2,w,size,1f,Paint.Align.CENTER,color)
    }
    private fun circle(c: Canvas,x: Double,y: Double,r: Double,color: Int) { paint.color=color; c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint) }
    private fun round(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int) {
        paint.color=color; c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun page(l: SapphireWatchCrownLayout,scale: Float): Bitmap {
        if(cachedLayout==l)return background!!
        cachedLayout=l
        background?.recycle()
        val b=Bitmap.createBitmap((l.w*scale).roundToInt(),(l.h*scale).roundToInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(b); c.scale(scale,scale)
        c.drawColor(0xff050505.toInt())
        stroke.strokeWidth=1f
        stroke.color=0xff1a1a1c.toInt(); c.drawCircle(l.cx.toFloat(),l.y(355.0).toFloat(),(175*l.sx).toFloat(),stroke)
        stroke.color=0xff111113.toInt(); c.drawCircle(l.cx.toFloat(),l.y(355.0).toFloat(),(146*l.sx).toFloat(),stroke)
        paint.color=Color.WHITE
        paint.shader=RadialGradient((l.cx-30).toFloat(),(l.y(355.0)-40).toFloat(),(128*l.sx*1.3).toFloat(),intArrayOf(0xff2a2a2e.toInt(),0xff141416.toInt()),null,Shader.TileMode.CLAMP)
        c.drawCircle(l.cx.toFloat(),l.y(355.0).toFloat(),(128*l.sx).toFloat(),paint); paint.shader=null
        label(c,"Store Online",118*l.sx,l.y(77.0)-13,"SemiBold",17f)
        label(c,"Easy Shopping",118*l.sx,l.y(112.0)-9,"Regular",12f,.45f)
        label(c,"Maison Aurel",26*l.sx,l.y(580.0)-15,"SemiBold",19f)
        val p=labels.getValue("SemiBold-19.0"); val small=labels.getValue("Regular-11.0")
        label(c,"(Swiss Made)",26*l.sx+p.measureText("Maison Aurel")+8,l.y(580.0)-15-p.fontMetrics.top+small.fontMetrics.top,"Regular",11f,.32f)
        label(c,"7.9/10",376*l.sx,l.y(580.0)-14,"Medium",17f,1f,Paint.Align.RIGHT)
        paint.color=0x1affffff
        c.drawRect(26f,l.y(612.0).toFloat(),(l.w-26).toFloat(),(l.y(612.0)+1/scale).toFloat(),paint)
        c.drawRect((213*l.sx).toFloat(),(l.y(612.0)+8).toFloat(),(213*l.sx+1/scale).toFloat(),l.y(720.0).toFloat(),paint)
        label(c,"Color",26*l.sx,l.y(640.0)-10,"Regular",13f,.45f)
        label(c,"Sizing",228*l.sx,l.y(640.0)-10,"Regular",13f,.45f)
        background=b; return b
    }
    fun draw(c: Canvas,l: SapphireWatchCrownLayout,d: SapphireWatchCrownDriver,pixelRatio: Float,pressed: Int=0) {
        paint.color=Color.WHITE; c.drawBitmap(page(l,pixelRatio),null,RectF(0f,0f,l.w.toFloat(),l.h.toFloat()),paint)
        val f=d.frame
        c.save(); c.translate(l.cx.toFloat(),l.y(355.0).toFloat()); c.rotate((f.flip*180).toFloat()); c.scale(l.sx.toFloat(),l.sx.toFloat()); c.translate(-175f,-175f)
        c.clipRect(0f,0f,350f,350f)
        stroke.color=0xb3ffffff.toInt(); stroke.strokeWidth=1.4f; c.drawPath(arrows,stroke); c.restore()
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
            paint.color=Color.WHITE; paint.shader=runtime.shader(l,d); c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint); paint.shader=null
        } else {
            circle(c,l.cx,l.cy,l.r,metals[f.selectedMetal]); circle(c,l.cx,l.cy,l.r-8,0xffeeedea.toInt())
        }
        repeat(2) { i ->
            val x=(if(i==0)26 else 307)*l.sx; val y=l.y(62.0); val h=68*l.k
            c.save()
            if(pressed==i+1) { if(d.reduced)c.saveLayerAlpha(null,184) else c.scale(.96f,.96f,(x+34).toFloat(),(y+h/2).toFloat()) }
            round(c,x,y,68.0,h,16.0,0xff1c1c1e.toInt())
            c.translate((x+(68-(if(i==0)10 else 26))/2).toFloat(),(y+(h-(if(i==0)18 else 24))/2).toFloat())
            stroke.color=Color.WHITE; stroke.strokeWidth=if(i==0)1.8f else 1.7f; c.drawPath(if(i==0)back else cart,stroke)
            if(i==1) { circle(c,9.0,19.5,1.6,Color.WHITE); circle(c,17.5,19.5,1.6,Color.WHITE); circle(c,21.0,4.0,3.8,0xffe6b76b.toInt()) }
            if(pressed==i+1 && d.reduced)c.restore()
            c.restore()
        }
        repeat(4) { i ->
            val x=SapphireWatchCrownLayout.swatches[i]*l.sx; val sy=l.y(683.0)
            if(i==f.selectedMetal) { stroke.strokeWidth=1.2f; stroke.color=0xbfffffff.toInt(); c.drawCircle(x.toFloat(),sy.toFloat(),17.4f,stroke) }
            circle(c,x,sy,if(i==f.selectedMetal)13.0 else 9.0,metals[i])
            centered(c,sizes[i],SapphireWatchCrownLayout.sizes[i]*l.sx,sy,if(i==d.sizeAt)"SemiBold" else "Regular",if(i==d.sizeAt)13f else 11.5f,if(i==d.sizeAt)Color.WHITE else 0x52ffffff)
        }
        round(c,26*l.sx,l.y(768.0),350*l.sx,72*l.k,30.0,0xff1c1c1e.toInt())
        centered(c,"$ 450.23",(26+149.0/2)*l.sx,l.y(768.0)+36*l.k,"Regular",15f)
        c.save()
        if(pressed==3) { if(d.reduced)c.saveLayerAlpha(null,184) else c.scale(.97f,.97f,(275.5*l.sx).toFloat(),(l.y(768.0)+36*l.k).toFloat()) }
        round(c,175*l.sx,l.y(768.0),201*l.sx,72*l.k,30.0,0xffffc56e.toInt())
        centered(c,"Buy Online",275.5*l.sx,l.y(768.0)+36*l.k,"SemiBold",16f,0xff111111.toInt())
        if(pressed==3 && d.reduced)c.restore()
        c.restore()
        if(f.finger>0) {
            val x=when(f.fingerAt) { 0 -> l.crownX+14-30*f.fingerU; 1 -> l.cx+70-140*f.fingerU; 2 -> 112*l.sx; else -> 73*l.sx }
            val y=when(f.fingerAt) { 0 -> l.crownY+10+30*f.fingerU; 1 -> l.cy+40; else -> l.y(683.0) }
            val r=22*(1-.12*f.press)
            circle(c,x,y,r,(f.finger*.1*255).roundToInt().shl(24) or 0xffffff)
            stroke.color=(f.finger*.6*255).roundToInt().shl(24) or 0xffffff; stroke.strokeWidth=(1.5*(1-.12*f.press)).toFloat()
            c.drawCircle(x.toFloat(),y.toFloat(),(r-stroke.strokeWidth/2).toFloat(),stroke)
        }
    }
}

@RequiresApi(33)
internal class SapphireWatchCrownRuntime {
    private val effect=RuntimeShader(SapphireWatchCrownShader.source)
    fun shader(l: SapphireWatchCrownLayout,d: SapphireWatchCrownDriver): Shader {
        val f=d.frame
        effect.setFloatUniform("uView",l.w.toFloat(),l.h.toFloat(),1f,(d.now%600).toFloat())
        effect.setFloatUniform("uWatch",l.cx.toFloat(),l.cy.toFloat(),l.r.toFloat(),f.flip.toFloat())
        effect.setFloatUniform("uHands",SapphireWatchCrownMotion.mod(f.hours,12.0).toFloat(),f.crown.toFloat(),f.metalFrom.toFloat(),f.metalTo.toFloat())
        effect.setFloatUniform("uMetal",f.metalK.toFloat(),d.time.toFloat(),0f,0f)
        return effect
    }
}
