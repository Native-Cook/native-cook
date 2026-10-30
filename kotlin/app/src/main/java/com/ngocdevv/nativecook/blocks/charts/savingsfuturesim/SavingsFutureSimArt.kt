package com.ngocdevv.nativecook.blocks.charts.savingsfuturesim

import android.content.Context
import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.savingsfuturesim.SavingsFutureSimMotion.Frame
import com.ngocdevv.nativecook.blocks.charts.savingsfuturesim.SavingsFutureSimMotion.balance
import com.ngocdevv.nativecook.blocks.charts.savingsfuturesim.SavingsFutureSimMotion.mix
import com.ngocdevv.nativecook.blocks.charts.savingsfuturesim.SavingsFutureSimMotion.smooth

/** Coordinates and font sizes remain in the pin's 736-pixel coordinate system. */
internal class SavingsFutureSimArt(context: Context) {
    private val faces = listOf("Regular", "Medium", "SemiBold", "Bold").associateWith {
        Typeface.createFromAsset(context.assets,"savingsfuturesim/savingsfuturesim_Inter-$it.ttf")
    }
    private val fonts = mutableMapOf<String, Paint>()
    private lateinit var canvas: Canvas
    private fun color(hex: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0,1.0)*255).roundToInt() shl 24) or hex
    private fun paint(hex: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(hex,alpha) }
    private fun stroke(hex: Int, width: Double, alpha: Double = 1.0) = paint(hex,alpha).apply {
        style = Paint.Style.STROKE; strokeWidth = width.toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun font(size: Double, weight: String): Paint = fonts.getOrPut("$size:$weight") {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { textSize = size.toFloat(); typeface = faces.getValue(weight) }
    }
    private fun width(value: String, size: Double, weight: String) = font(size,weight).measureText(value).toDouble()
    private fun text(value: String, x: Double, y: Double, size: Double, weight: String, hex: Int, alpha: Double = 1.0, anchor: Double = 0.0) {
        val p = font(size,weight)
        p.color = color(hex,alpha)
        canvas.drawText(value,(x-width(value,size,weight)*anchor).toFloat(),(y+size*0.36).toFloat(),p)
    }
    private fun rr(x: Double, y: Double, w: Double, h: Double, r: Double, hex: Int, alpha: Double = 1.0, outline: Double = 0.0) {
        canvas.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),
            if (outline > 0) stroke(hex,outline,alpha) else paint(hex,alpha))
    }
    private fun circle(x: Double, y: Double, r: Double, hex: Int, alpha: Double = 1.0, outline: Double = 0.0) {
        canvas.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),if (outline > 0) stroke(hex,outline,alpha) else paint(hex,alpha))
    }
    private fun line(x: Double, y: Double, xx: Double, yy: Double, hex: Int, width: Double, alpha: Double = 1.0) {
        canvas.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),stroke(hex,width,alpha))
    }
    private fun gradient(colors: IntArray, stops: FloatArray?, y0: Double, y1: Double) = LinearGradient(0f,y0.toFloat(),0f,y1.toFloat(),colors,stops,Shader.TileMode.CLAMP)
    private fun point(u: Double, k: Double, rate: Double): Pair<Double,Double> {
        val angle = PI+PI*u
        val v = (balance(u*30,rate)-10000)/(balance(30.0,8.0)-10000)
        val ku = smooth(k*1.35-u*0.35)
        return mix(368+260*cos(angle),92+u*552,ku) to mix(770+260*sin(angle),782-150*v,ku)
    }
    private fun path(points: List<Pair<Double,Double>>, from: Int, through: Int): Path {
        return Path().apply {
            moveTo(points[from].first.toFloat(),points[from].second.toFloat())
            for (i in from..through) lineTo(points[i].first.toFloat(),points[i].second.toFloat())
        }
    }
    private fun shape(f: Frame): Pair<Double,Double> {
        val k = f.morph
        val split = mix(SavingsFutureSimTheme.rateFrac(f.rate),f.year/30,smooth(k))
        val points = (0..72).map { point(it/72.0,k,f.rate) }
        val width = mix(28.0,7.0,smooth(k))
        val si = floor(split*72).toInt().coerceIn(0,72)
        if (k > 0.02) {
            val fill = Path().apply {
                moveTo(points[0].first.toFloat(),782f)
                for (p in points) lineTo(p.first.toFloat(),p.second.toFloat())
                lineTo(points[72].first.toFloat(),782f); close()
            }
            val sx = 92+split*552
            canvas.save(); canvas.clipRect(72f,572f,sx.toFloat(),802f)
            canvas.drawPath(fill,paint(0xA39BF0).apply { shader = gradient(intArrayOf(color(0xA39BF0,0.38*k),color(0xA39BF0,0.0)),null,632.0,782.0) })
            canvas.restore()
            canvas.save(); canvas.clipRect(sx.toFloat(),572f,(sx+552).toFloat(),802f)
            canvas.drawPath(fill,paint(0xA39BF0,0.06*k)); canvas.restore()
        }
        val rest = stroke(0x3A3A4A,width).apply {
            strokeCap = Paint.Cap.BUTT
            shader = gradient(intArrayOf(color(0x3A3A4A),color(0x3A3A4A,mix(0.15,1.0,k))),floatArrayOf(0.6f,1f),510.0,780.0)
            if (smooth(k) > 0.6) { strokeCap = Paint.Cap.ROUND; pathEffect = DashPathEffect(floatArrayOf(12f,12f),0f); shader = null; color = color(0x6D6A95) }
        }
        canvas.drawPath(path(points,si,72),rest)
        if (k > 0.5) {
            val ma = smooth((k-0.5)*2)
            for (year in listOf(10.0,20.0,30.0)) {
                val p = point(year/30,k,f.rate)
                val pulse = exp(-(f.year-year).pow(2)/1.2)
                circle(p.first,p.second,6.0,if (f.year >= year) 0xA39BF0 else 0x6D6A95,ma)
                if (pulse > 0.02) circle(p.first,p.second,8+16*pulse,0xA39BF0,0.7*pulse*ma,3.0)
            }
        }
        val knob = point(split,k,f.rate)
        val lead = path(points,0,si).apply { lineTo(knob.first.toFloat(),knob.second.toFloat()) }
        canvas.drawPath(lead,stroke(0xA39BF0,width).apply {
            strokeCap = Paint.Cap.BUTT
            shader = gradient(intArrayOf(color(0x7A70E0,mix(0.0,1.0,k)),color(0xA39BF0)),floatArrayOf(0f,0.6f),780.0,510.0)
        })
        if (k > 0.5) {
            var y = knob.second+26
            while (y < 782) { line(knob.first,y,knob.first,min(782.0,y+7),0xA39BF0,2.0,0.6*smooth((k-0.5)*2)); y += 14 }
        }
        val kr = 22*(1+0.06*sin(2*PI*3*f.amb/SavingsFutureSimMotion.LOOP))*(1+0.2*f.knob)
        circle(knob.first,knob.second,kr+7,0x232331); circle(knob.first,knob.second,kr,0xA39BF0)
        return knob
    }
    private fun money(value: Double): String {
        val s = floor(value+0.5).toLong().toString()
        return "$"+s.mapIndexed { index, c -> "$c"+if (s.length-1-index > 0 && (s.length-1-index)%3 == 0) "," else "" }.joinToString("")
    }
    private fun value(f: Frame, knob: Pair<Double,Double>) {
        val k = smooth(f.morph*1.3-0.15)
        if (k < 0.99) {
            val rt = SavingsFutureSimTheme.rateText(f.rate)
            val vw = width(rt,104.0,"Bold")
            val pw = width("%",62.0,"Bold")
            val x = 368-(vw+pw+6)/2
            text(rt,x,680-12*k,104.0,"Bold",0xFFFFFF,1-k)
            text("%",x+vw+6,692-12*k,62.0,"Bold",0xFFFFFF,1-k)
            text("Your Savings Rate",368.0,760-12*k,30.0,"Regular",0x9B9BAA,1-k,0.5)
        }
        if (k > 0.01) {
            val yi = max(1,floor(f.year).toInt())
            val roll = smooth((f.year-yi)/0.35)
            val now = money(balance(yi.toDouble(),f.rate))
            val prev = money(balance(max(0,yi-1).toDouble(),f.rate))
            val y = 548+12*(1-k)
            val w = width(now,104.0,"Bold")*0.82
            var x = 368-w/2
            canvas.save(); canvas.translate(x.toFloat(),y.toFloat()); canvas.scale(0.82f,0.82f); canvas.translate(-x.toFloat(),-y.toFloat())
            canvas.clipRect((x-10).toFloat(),(y-70).toFloat(),(x+w/0.82+10).toFloat(),(y+70).toFloat())
            if (prev.length != now.length || f.year < 1.02) text(now,x,y,104.0,"Bold",0xFFFFFF,k)
            else {
                for (i in now.indices) {
                    val a = prev[i].toString()
                    val b = now[i].toString()
                    if (a == b || roll >= 1) text(b,x,y,104.0,"Bold",0xFFFFFF,k)
                    else {
                        text(a,x,y-90*roll,104.0,"Bold",0xFFFFFF,k*(1-roll))
                        text(b,x,y+90*(1-roll),104.0,"Bold",0xFFFFFF,k*roll)
                    }
                    x += width(b,104.0,"Bold")
                }
            }
            canvas.restore()
            text("in $yi ${if (yi == 1) "year" else "years"}",368.0,604+12*(1-k),30.0,"Regular",0x9B9BAA,k,0.5)
            for (year in listOf(1,10,20,30)) text("${year}y",92+year/30.0*552,808.0,21.0,"Medium",0x9B9BAA,k*0.8,0.5)
            line(92.0,782.0,644.0,782.0,0x383846,2.0,k)
            rr(knob.first-52,knob.second-73,104.0,42.0,21.0,0x2B2C3D,k)
            text("Year $yi",knob.first,knob.second-52,22.0,"SemiBold",0xFFFFFF,k,0.5)
        }
    }
    private fun share(index: Int, x: Double) {
        val y = 1490.0
        circle(x,y,52.0,if (index == 0) 0x5AD27A else if (index == 2) 0x4DA8F0 else 0x2B2C3D)
        when (index) {
            0 -> {
                rr(x-24,y-20,48.0,34.0,16.0,0xFFFFFF)
                canvas.drawPath(Path().apply { moveTo((x-12).toFloat(),(y+12).toFloat()); lineTo((x-18).toFloat(),(y+24).toFloat()); lineTo((x-2).toFloat(),(y+14).toFloat()); close() },paint(0xFFFFFF))
            }
            1 -> {
                line(x-8,y+8,x+8,y-8,0xFFFFFF,5.0)
                canvas.save(); canvas.rotate(-45f,(x-15).toFloat(),(y+7).toFloat()); rr(x-30,y-1,30.0,16.0,8.0,0xFFFFFF,1.0,5.0); canvas.restore()
                canvas.save(); canvas.rotate(-45f,(x+15).toFloat(),(y-7).toFloat()); rr(x,y-15,30.0,16.0,8.0,0xFFFFFF,1.0,5.0); canvas.restore()
            }
            2 -> canvas.drawPath(Path().apply { moveTo((x-24).toFloat(),y.toFloat()); lineTo((x+24).toFloat(),(y-20).toFloat()); lineTo((x+10).toFloat(),(y+22).toFloat()); lineTo((x+2).toFloat(),(y+6).toFloat()); close() },paint(0xFFFFFF))
            else -> for (i in -1..1) circle(x+i*15,y,5.0,0xC9C9D6)
        }
    }
    fun draw(c: Canvas, width: Float, height: Float, f: Frame) {
        canvas = c
        val scale = width/736f
        val h = height/scale
        c.save(); c.scale(scale,scale)
        c.drawRect(0f,0f,736f,h,paint(0x17182C).apply { shader = gradient(intArrayOf(color(0x17182C),color(0x0E0F1B)),null,0.0,h.toDouble()) })
        for (x in listOf(84.0,652.0)) circle(x,161.0,40.0,0x2B2C3D)
        for (i in -1..1) circle(84.0+i*12,161.0,4.0,0xFFFFFF)
        circle(652.0,161.0,20.0,0xFFFFFF,1.0,3.5); text("?",652.0,161.0,22.0,"SemiBold",0xFFFFFF,1.0,0.5)
        val jx = 368-(width("Joined Waitlist",30.0,"SemiBold")+54)/2
        circle(jx+18,257.0,18.0,0xA39BF0)
        line(jx+10,257.0,jx+16,263.0,0xFFFFFF,3.4); line(jx+16,263.0,jx+26,251.0,0xFFFFFF,3.4)
        text("Joined Waitlist",jx+54,257.0,30.0,"SemiBold",0xFFFFFF)
        text("You’re on your way to the",368.0,328.0,40.0,"SemiBold",0xFFFFFF,1.0,0.5)
        text("future of Savings.",368.0,383.0,40.0,"SemiBold",0xFFFFFF,1.0,0.5)
        rr(38.0,455.0,660.0,785.0,56.0,0x232331)
        val knob = shape(f); value(f,knob)
        line(80.0,833.0,656.0,833.0,0x383846,2.0)
        text("See your savings grow.",81.0,900.0,32.0,"SemiBold",0xFFFFFF)
        listOf("Explore what ${SavingsFutureSimTheme.rateText(f.rate)}% could look like over","time, and see how compounding can","help your savings grow.").forEachIndexed { index, s -> text(s,81.0,listOf(955.0,999.0,1043.0)[index],29.0,"Regular",0x8E8E9E) }
        c.save(); c.translate(368f,1150.5f)
        val s = (1-0.04*f.press).toFloat(); c.scale(s,s)
        rr(-288.0,-45.5,576.0,91.0,45.5,0x3A3A4A)
        val ok = smooth(f.morph*2-0.6)
        listOf("Simulate My Future","Back to My Rate").forEachIndexed { index, label ->
            val a = if (index == 0) 1-ok else ok
            if (a >= 0.01) {
                val x = -(width(label,32.0,"SemiBold")+46)/2
                val dy = if (index == 0) -10*ok else 10*(1-ok)
                for (b in 0..2) rr(x+b*10,dy+14-(b+1)*9,5.0,(b+1)*9.0,2.5,0xFFFFFF,a)
                text(label,x+46,dy,32.0,"SemiBold",0xFFFFFF,a)
            }
        }
        c.restore()
        rr(38.0,1278.0,660.0,340.0,56.0,0x232331)
        text("Skip the Waitlist",74.0,1338.0,31.0,"Medium",0xFFFFFF)
        text("Learn More",662.0,1338.0,31.0,"Medium",0xA39BF0,1.0,1.0)
        line(74.0,1390.0,662.0,1390.0,0x383846,2.0)
        listOf(148.0,295.0,441.0,588.0).forEachIndexed { index, x -> share(index,x) }
        c.restore()
    }
}
