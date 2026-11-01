package com.ngocdevv.nativecook.blocks.gestures.boxcatfeed

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class BoxCatFeedArt(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fonts = mapOf(
        "bold" to Typeface.createFromAsset(context.assets,"boxcatfeed/boxcatfeed_Nunito-ExtraBold.ttf"),
        "black" to Typeface.createFromAsset(context.assets,"boxcatfeed/boxcatfeed_Nunito-Black.ttf"),
        "title" to Typeface.createFromAsset(context.assets,"boxcatfeed/boxcatfeed_TitanOne-Regular.ttf"),
        "icon" to Typeface.createFromAsset(context.assets,"boxcatfeed/boxcatfeed_Ionicons.ttf")
    )
    private val paths = mutableMapOf<String,Path>()
    private val colors = mutableMapOf<String,Int>()
    private var width = 0.0
    private var height = 0.0
    private var page: LinearGradient? = null
    private var box: LinearGradient? = null
    private fun color(hex: String) = colors.getOrPut(hex) { Color.parseColor("#$hex") }
    private fun setup(hex: String, alpha: Double = 1.0, stroke: Double = 0.0) {
        paint.shader = null; paint.color = color(hex); paint.alpha = (alpha.coerceIn(0.0,1.0)*255).roundToInt()
        paint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke.toFloat(); paint.strokeCap = Paint.Cap.ROUND
    }
    private fun rect(x: Double,y: Double,w: Double,h: Double) = RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
    private fun oval(cv: Canvas,x: Double,y: Double,w: Double,h: Double,hex: String,a: Double = 1.0) { setup(hex,a); cv.drawOval(rect(x,y,w,h),paint) }
    private fun circle(cv: Canvas,x: Double,y: Double,r: Double,hex: String,a: Double = 1.0) { oval(cv,x-r,y-r,r*2,r*2,hex,a) }
    private fun line(cv: Canvas,x: Double,y: Double,x2: Double,y2: Double,hex: String,w: Double,a: Double = 1.0) { setup(hex,a,w); cv.drawLine(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),paint) }
    private fun round(cv: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,hex: String) { setup(hex); cv.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),paint) }
    private fun polygon(cv: Canvas,points: DoubleArray,hex: String,key: String) {
        val p = paths.getOrPut(key) { Path().apply { moveTo(points[0].toFloat(),points[1].toFloat()); for (i in 2 until points.size step 2) lineTo(points[i].toFloat(),points[i+1].toFloat()); close() } }
        setup(hex); cv.drawPath(p,paint)
    }
    private fun text(cv: Canvas,t: String,font: String,size: Double,hex: String,x: Double,y: Double,center: Boolean = false) {
        setup(hex); paint.typeface = fonts[font]; paint.textSize = size.toFloat()
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        cv.drawText(t,x.toFloat(),(y-paint.fontMetrics.ascent).toFloat(),paint)
    }
    private fun measure(t: String,font: String,size: Double): Double { paint.typeface = fonts[font]; paint.textSize = size.toFloat(); return paint.measureText(t).toDouble() }
    private fun fish(cv: Canvas,x: Double,y: Double,k: Double,rotation: Double) {
        cv.save(); cv.translate(x.toFloat(),y.toFloat()); cv.rotate(rotation.toFloat()); cv.scale(k.toFloat(),k.toFloat())
        oval(cv,-16.0,-9.0,28.0,18.0,"FFB25C")
        polygon(cv,doubleArrayOf(10.0,0.0,22.0,-9.0,22.0,9.0),"FF9A3D","fish")
        circle(cv,-8.0,-2.0,2.4,"5A3A20"); line(cv,-2.0,-6.0,-2.0,6.0,"E8893A",1.6); cv.restore()
    }
    fun draw(cv: Canvas,w: Double,h: Double,top: Double,bottom: Double,o: BoxCatFeedMotion.Frame,reduced: Boolean) {
        val cx = w/2; val rim = floor(h*0.56+0.5); val s = floor(w*0.2+0.5); val bw = s*2.3; val bh = s*1.5
        if (w != width || h != height) {
            width = w; height = h; paths.clear()
            page = LinearGradient(0f,0f,0f,h.toFloat(),intArrayOf(color("EDE6FF"),color("F6F2FF"),color("FFF4EC")),null,Shader.TileMode.CLAMP)
            box = LinearGradient(0f,rim.toFloat(),0f,(rim+bh).toFloat(),color("E3A86C"),color("CF9258"),Shader.TileMode.CLAMP)
        }
        setup("FFFFFF"); paint.shader = page; cv.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint)
        val c = BoxCatFeedMotion.cat(o.u); val lift = if (reduced) 0.0 else c.lift
        oval(cv,cx-bw*0.62,rim+bh-10,bw*1.24,30.0,"5B3F8C",0.14)
        polygon(cv,doubleArrayOf(cx-bw/2+8,rim,cx-bw/2+20,rim-s*0.5,cx+bw/2-20,rim-s*0.5,cx+bw/2-8,rim),"C78C55","back")
        setup("8E5A33"); cv.drawRect(rect(cx-bw/2,rim-6,bw,14.0),paint)
        val headY = rim-s*0.42-lift*s*1.7+o.duck*s*0.5; val sy = 1+c.stretch
        cv.save(); cv.clipRect(rect(cx-bw,-2000.0,bw*2,rim+4+2000)); cv.translate(cx.toFloat(),headY.toFloat()); cv.scale((1/sqrt(sy)).toFloat(),sy.toFloat())
        round(cv,-s*0.55,s*0.3,s*1.1,s*1.6,s*0.5,"8E97A8")
        round(cv,-s*0.3,s*0.45,s*0.6,s*1.2,s*0.3,"E9ECF2")
        for (k in listOf(-1.0,1.0)) {
            polygon(cv,doubleArrayOf(k*s*0.55,-s*0.2,k*s*0.42,-s*0.78,k*s*0.08,-s*0.42),"8E97A8","ear$k")
            polygon(cv,doubleArrayOf(k*s*0.45,-s*0.28,k*s*0.38,-s*0.62,k*s*0.18,-s*0.42),"F4A9B8","inner$k")
        }
        oval(cv,-s*0.68,-s*0.52,s*1.36,s*1.04,"98A1B2")
        for (k in listOf(-1.0,1.0)) {
            if (c.munch > 0 || o.blink) { setup("2B2433",stroke=3.0); cv.drawArc(rect(k*s*0.27-s*0.1,-s*0.12,s*0.2,s*0.14),200f,140f,false,paint) }
            else { circle(cv,k*s*0.27,-s*0.04,s*0.09,"2B2433"); circle(cv,k*s*0.27+s*0.03,-s*0.07,s*0.03,"FFFFFF") }
            line(cv,k*s*0.42,s*0.12,k*s*0.82,s*0.06,"6E7686",1.4)
            line(cv,k*s*0.42,s*0.2,k*s*0.82,s*0.24,"6E7686",1.4)
        }
        circle(cv,-s*0.4,s*0.12,s*0.09,"F4A9B8",0.6); circle(cv,s*0.4,s*0.12,s*0.09,"F4A9B8",0.6)
        oval(cv,-s*0.06,s*0.06,s*0.12,s*0.08,"F48BA0")
        val mouth = 0.05+0.1*c.munch+if (lift > 0.6 && !c.caught) 0.12 else 0.0
        oval(cv,-s*0.1,s*0.16,s*0.2,s*mouth,"5A2A3A")
        if (c.caught && c.munch > 0) fish(cv,s*0.02,s*0.26,0.7*(1-BoxCatFeedMotion.clamp((o.u-0.7-1.2)/0.4)),0.0)
        cv.restore()
        for (k in listOf(-1.0,1.0)) oval(cv,cx+k*s*0.4-s*0.18,rim-s*0.08-lift*s*0.6,s*0.36,s*0.22,"A7AFBE",1-lift)
        setup("FFFFFF"); paint.shader = box; cv.drawRoundRect(rect(cx-bw/2,rim,bw,bh),10f,10f,paint)
        polygon(cv,doubleArrayOf(cx-bw/2,rim,cx-bw/2-s*0.55,rim+s*0.28,cx-bw/2-s*0.45,rim+s*0.55,cx-bw/2,rim+s*0.22),"D69A5E","flapL")
        polygon(cv,doubleArrayOf(cx+bw/2,rim,cx+bw/2+s*0.55,rim+s*0.28,cx+bw/2+s*0.45,rim+s*0.55,cx+bw/2,rim+s*0.22),"C48650","flapR")
        line(cv,cx-bw*0.3,rim+bh*0.55,cx+bw*0.3,rim+bh*0.55,"A86F3E",2.0,0.5)
        if (o.u >= 0 && o.u < 0.7 && !reduced) {
            val k = o.u/0.7
            fish(cv,cx+s*0.9*(1-k),(h-bottom-120)*(1-k)+(rim-s*0.42-s*1.7+s*0.26)*k-sin(PI*k)*s*2.2,1.0,k*540)
        }
        if (c.hearts >= 0 && c.hearts < 1.4) for (i in 0..2) {
            val age = c.hearts-i*0.18
            if (age < 0) continue
            val hx = cx+(i-1)*s*0.6+sin(age*6+i)*6; val hy = rim-s*1.6-age*s*1.2
            val sz = s*0.16*(0.6+0.4*BoxCatFeedMotion.clamp(age*4))
            val p = Path().apply { moveTo(hx.toFloat(),(hy+sz).toFloat()); cubicTo((hx-sz*2).toFloat(),(hy-sz*0.4).toFloat(),(hx-sz*0.8).toFloat(),(hy-sz*1.6).toFloat(),hx.toFloat(),(hy-sz*0.5).toFloat()); cubicTo((hx+sz*0.8).toFloat(),(hy-sz*1.6).toFloat(),(hx+sz*2).toFloat(),(hy-sz*0.4).toFloat(),hx.toFloat(),(hy+sz).toFloat()) }
            setup("FF6B8B",1-BoxCatFeedMotion.clamp(age)); cv.drawPath(p,paint)
        }
        text(cv,"Your bank buddy","bold",14.0,"8A7FA8",20.0,top+10)
        text(cv,"Mochi","title",30.0,"2B2433",20.0,top+30)
        val keep = ((1-o.dip)*255).roundToInt()
        cv.saveLayerAlpha(null,keep)
        text(cv,if (o.count > 0) "Purring. Thanks for the snack!" else "Peeking out for a snack…","bold",15.0,"6E6488",20.0,top+86)
        cv.restore()
        val pts = "${1240+o.count*10} pts"; val pw = 48+ceil(measure(pts,"black",15.0)); val px = w-20-pw; val py = top+23
        val u = o.u-0.7; val scale = 1+0.18*(if (u >= 0 && u < 0.4) sin(u/0.4*PI) else 0.0)
        cv.saveLayerAlpha(null,keep); cv.translate((px+pw/2).toFloat(),(py+18).toFloat()); cv.scale(scale.toFloat(),scale.toFloat()); cv.translate((-pw/2).toFloat(),-18f)
        round(cv,0.0,0.0,pw,36.0,18.0,"FFFFFF")
        text(cv,62860.toChar().toString(),"icon",14.0,"7A4DFF",14.0,10.0); text(cv,pts,"black",15.0,"2B2433",34.0,8.0); cv.restore()
        val dy = h-bottom-110
        round(cv,20.0,dy,w-40,84.0,26.0,"FFFFFF")
        text(cv,"Daily treats","black",16.0,"2B2433",40.0,dy+20)
        cv.saveLayerAlpha(null,keep)
        for (i in 0..2) round(cv,40+i*32.0,dy+49,26.0,8.0,4.0,if (i < o.count) "7A4DFF" else "EDE6FF")
        cv.restore()
        val shown = BoxCatFeedMotion.treatShown(o.u)
        if (shown > 0) {
            val x = w-140; val y = dy+10; val ts = (0.8+0.2*shown).toFloat()
            cv.saveLayerAlpha(null,(shown*255).roundToInt()); cv.scale(ts,ts,(x+55).toFloat(),(y+32).toFloat())
            round(cv,x,y,110.0,64.0,20.0,"FFF1DE")
            text(cv,62218.toChar().toString(),"icon",26.0,"FF9A3D",x+55,y+9,true)
            text(cv,"Flick up","black",12.0,"C9772F",x+55,y+36,true)
            cv.restore()
        }
    }
}
