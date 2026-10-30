package com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar

import android.content.Context
import android.graphics.*
import com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar.CabinClimateCarTheme as C
import com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar.CabinClimateCarMotion as M
import kotlin.math.*

/** Coordinates, layer order and font metrics follow ccc-draw.ts in pin pixels. */
internal class CabinClimateCarArt(context: Context) {
    private val car = context.assets.open("cabinclimatecar/cabinclimatecar_car.webp").use { BitmapFactory.decodeStream(it) }
    private val faces = listOf("Regular","Medium","SemiBold","Bold").associateWith {
        Typeface.createFromAsset(context.assets,"cabinclimatecar/cabinclimatecar_Inter-$it.ttf")
    }
    private lateinit var canvas: Canvas
    private fun paint(rgb: Int = 0xFFFFFF,alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.color(rgb,alpha) }
    private fun rect(x: Double,y: Double,w: Double,h: Double) = RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
    private fun rr(x: Double,y: Double,w: Double,h: Double,r: Double,rgb: Int,a: Double = 1.0) {
        canvas.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),paint(rgb,a))
    }
    private fun oval(x: Double,y: Double,w: Double,h: Double,color: Int) {
        canvas.drawOval(rect(x,y,w,h),Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
    private fun stroke(path: Path,color: Int,width: Double) {
        canvas.drawPath(path,Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = width.toFloat()
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
    }
    private fun line(x: Double,y: Double,xx: Double,yy: Double,rgb: Int,width: Double) {
        stroke(Path().apply { moveTo(x.toFloat(),y.toFloat()); lineTo(xx.toFloat(),yy.toFloat()) },C.color(rgb),width)
    }
    private fun translate(x: Double,y: Double) { canvas.translate(x.toFloat(),y.toFloat()) }
    private fun scale(value: Double) { canvas.scale(value.toFloat(),value.toFloat()) }
    private fun rotate(degrees: Double) { canvas.rotate(degrees.toFloat()) }
    private fun font(size: Double,weight: String,rgb: Int,alpha: Double = 1.0) = paint(rgb,alpha).apply {
        typeface = faces.getValue(weight); textSize = size.toFloat()
    }
    private fun textWidth(value: String,size: Double,weight: String) = font(size,weight,0).measureText(value).toDouble()
    private fun text(value: String,x: Double,cy: Double,size: Double,weight: String,rgb: Int,
                     ax: Double = 0.0,alpha: Double = 1.0) {
        val p = font(size,weight,rgb,alpha)
        canvas.drawText(value,(x-p.measureText(value)*ax).toFloat(),(cy+size*0.36).toFloat(),p)
    }
    private fun linear(x: Double,y: Double,xx: Double,yy: Double,colors: IntArray) = paint().apply {
        shader = LinearGradient(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),colors,null,Shader.TileMode.CLAMP)
    }
    private fun radial(x: Double,y: Double,radius: Double,colors: IntArray) = paint().apply {
        shader = RadialGradient(x.toFloat(),y.toFloat(),radius.toFloat(),colors,null,Shader.TileMode.CLAMP)
    }
    private val shadows = mutableMapOf<String,Bitmap>()
    private fun shadow(x: Double,y: Double,w: Double,h: Double,r: Double,sigma: Float,alpha: Double) {
        // Software masks are cached once: Android hardware Canvas ignores BlurMaskFilter.
        val key = "$w:$h:$r:$sigma:$alpha"
        val pad = ceil(sigma*3.0).toInt()
        val bitmap = shadows.getOrPut(key) {
            val image = Bitmap.createBitmap(ceil(w).toInt()+pad*2,ceil(h).toInt()+pad*2,Bitmap.Config.ARGB_8888)
            val blur = paint(0x8A93A8,alpha).apply {
                maskFilter = BlurMaskFilter((sigma-0.5f)/0.57735f,BlurMaskFilter.Blur.NORMAL)
            }
            Canvas(image).drawRoundRect(rect(pad.toDouble(),pad.toDouble(),w,h),r.toFloat(),r.toFloat(),blur)
            image
        }
        canvas.drawBitmap(bitmap,(x-pad).toFloat(),(y-pad).toFloat(),paint().apply { isFilterBitmap = true })
    }
    private fun card(x: Double,y: Double,w: Double,h: Double,press: Double = 0.0) {
        canvas.save(); translate(x+w/2,y+h/2); scale(1-0.04*press); translate(-x-w/2,-y-h/2)
        shadow(x+4,y+10,w-8,h,40.0,14f,0.12)
        rr(x,y,w,h,40.0,C.card)
    }
    private val flows = arrayOf(
        arrayOf(455.0 to 268.0,400.0 to 236.0,320.0 to 238.0,265.0 to 268.0,235.0 to 300.0),
        arrayOf(470.0 to 285.0,410.0 to 262.0,340.0 to 262.0,280.0 to 286.0,250.0 to 318.0),
        arrayOf(445.0 to 300.0,395.0 to 288.0,330.0 to 290.0,285.0 to 308.0,262.0 to 330.0)
    )
    private fun catmull(points: Array<Pair<Double,Double>>,u: Double): Pair<Double,Double> {
        val n = points.size-1; val position = min(n-1e-4,max(0.0,u*n))
        val index = floor(position).toInt(); val t = position-index; val t2 = t*t; val t3 = t2*t
        val p0 = points[max(0,index-1)]; val p1 = points[index]; val p2 = points[index+1]; val p3 = points[min(n,index+2)]
        fun q(a: Double,b: Double,c: Double,d: Double) = 0.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t2+(-a+3*b-3*c+d)*t3)
        return q(p0.first,p1.first,p2.first,p3.first) to q(p0.second,p1.second,p2.second,p3.second)
    }
    private fun drawCar(f: M.Frame) {
        val height = 540.0*car.height/car.width
        canvas.drawBitmap(car,null,rect(92.0,318-height/2,540.0,height),paint().apply { isFilterBitmap = true })
        val gx = 362.0+(350-365)*540.0/660; val gy = 318.0+(270-318)*540.0/660
        canvas.drawOval(rect(gx-115,gy-68,230.0,136.0),radial(gx,gy,115.0,intArrayOf(C.air(f.heat,0.32),C.air(f.heat,0.0))))
        val strength = 0.55+0.45*M.clamp01(abs(f.heat-f.temp)+abs(f.temp-70)/6)
        for (k in 0..2) for (j in 0..3) {
            val u0 = (f.amb/M.LOOP*6+j/4.0+k*0.13)%1
            val path = Path(); var count = 0
            for (sample in 0..8) {
                val u = u0-0.16+sample/8.0*0.16
                if (u < 0) continue
                val q = catmull(flows[k],u)
                val x = (362+(q.first-365)*540/660).toFloat()
                val y = (318+(q.second-318)*540/660).toFloat()
                if (count++ == 0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            if (count > 1) stroke(path,C.air(f.heat,sin(PI*u0)*0.75*strength),4.0)
        }
    }
    private fun drawPill(f: M.Frame) {
        canvas.save(); translate(360.0,181.0); scale(if (f.card == 5) 1-0.04*f.cardK else 1.0)
        rr(-85.0,-29.0,170.0,58.0,29.0,C.pill)
        stroke(Path().apply { addCircle(-51f,0f,23f,Path.Direction.CW) },C.color(C.teal),3.0)
        text("RL",-51.0,0.0,30.0,"SemiBold",C.teal,0.5)
        text("Edit",-15.0,0.0,30.0,"SemiBold",0xFFFFFF)
        line(55.0,-7.0,62.0,0.0,0xFFFFFF,2.8); line(55.0,7.0,62.0,0.0,0xFFFFFF,2.8)
        canvas.restore()
    }
    private fun drawTemp(f: M.Frame) {
        card(30.0,580.0,322.0,490.0)
        canvas.save(); canvas.clipPath(Path().apply { addRoundRect(rect(30.0,580.0,322.0,490.0),40f,40f,Path.Direction.CW) })
        canvas.drawRect(rect(30.0,580.0,322.0,490.0),linear(0.0,800.5,0.0,1070.0,intArrayOf(C.air(f.heat,0.0),C.air(f.heat,0.16))))
        canvas.restore()
        val cx = 191.0
        for ((sign,y,k) in listOf(Triple("+",670.0,f.plusK),Triple("−",978.0,f.minusK))) {
            if (k > 0.01) { val r = 46*(0.8+0.2*k); oval(cx-r,y-r,r*2,r*2,C.color(0xEEF1F6,k)) }
            canvas.save(); translate(cx,y); scale(1-0.04*k)
            text(sign,0.0,-4.0,54.0,"Regular",C.ink,0.5); canvas.restore()
        }
        val roll = M.smooth(f.tempAt/M.ROLL); val a = f.from.toInt().toString(); val b = f.temp.toInt().toString()
        val width = textWidth(b,116.0,"SemiBold")+textWidth("°",116.0,"SemiBold")*0.85
        var px = cx-width/2
        val up = if (f.temp > f.from) 1 else -1
        canvas.save(); canvas.clipRect(rect(30.0,745.0,322.0,160.0))
        for (i in b.indices) {
            val old = if (a.length == b.length) a[i].toString() else ""; val next = b[i].toString()
            if (roll >= 1 || old == next) text(next,px,825.0,116.0,"SemiBold",C.ink)
            else {
                text(old,px,825-up*120*roll,116.0,"SemiBold",C.ink,0.0,1-roll)
                text(next,px,825+up*120*(1-roll),116.0,"SemiBold",C.ink,0.0,roll)
            }
            px += textWidth(next,116.0,"SemiBold")
        }
        text("°",px-4,825.0,116.0,"SemiBold",C.ink)
        canvas.restore(); canvas.restore()
    }
    private fun seat(x: Double,y: Double) {
        canvas.save(); translate(x,y+16); scale(1.1); translate(-x,-y-16)
        val p = Path().apply {
            moveTo((x-30).toFloat(),(y-64).toFloat())
            cubicTo((x-40).toFloat(),(y-66).toFloat(),(x-46).toFloat(),(y-56).toFloat(),(x-42).toFloat(),(y-44).toFloat())
            lineTo((x-30).toFloat(),(y+4).toFloat())
            cubicTo((x-26).toFloat(),(y+14).toFloat(),(x-18).toFloat(),(y+16).toFloat(),(x-8).toFloat(),(y+16).toFloat())
            lineTo((x+36).toFloat(),(y+16).toFloat())
            cubicTo((x+46).toFloat(),(y+16).toFloat(),(x+48).toFloat(),(y+2).toFloat(),(x+38).toFloat(),(y-2).toFloat())
            lineTo((x-6).toFloat(),(y-6).toFloat()); lineTo((x-18).toFloat(),(y-56).toFloat())
            cubicTo((x-20).toFloat(),(y-62).toFloat(),(x-24).toFloat(),(y-64).toFloat(),(x-30).toFloat(),(y-64).toFloat()); close()
        }
        canvas.drawPath(p,linear(x-40,y-60,x+40,y+16,intArrayOf(C.color(C.seatHi),C.color(C.seat))))
        rr(x-36,y-80,14.0,18.0,6.0,C.seat)
        oval(x-46,y+30,92.0,8.0,C.color(0xAEB4C2,0.35)); canvas.restore()
    }
    private fun drawLegroom(f: M.Frame) {
        card(369.0,580.0,321.0,235.0,if (f.card == 3) f.cardK else 0.0)
        val bars = doubleArrayOf(M.mix(1.0,0.6,f.leg),M.mix(1.0,1.45,f.leg))
        for (i in 0..1) {
            val sx = doubleArrayOf(445.0,559.0)[i]+(if (i == 0) -16 else 16)*f.leg*0.4
            seat(sx,662.0)
            // set warm, the seats heat up: a red glow on the cushion and the back
            val warm = M.clamp01((f.heat - 71) / 3)
            if (warm > 0.01) {
                canvas.drawOval(rect(sx - 26, 662.0 - 14, 80.0, 40.0), radial(sx + 14, 662.0 + 6, 40.0, intArrayOf(
                    C.color(0xFF5A3C, 0.55 * warm), C.color(0xFF5A3C, 0.0),
                )))
                canvas.drawOval(rect(sx - 54, 662.0 - 70, 48.0, 72.0), radial(sx - 30, 662.0 - 34, 30.0, intArrayOf(
                    C.color(0xFF5A3C, 0.45 * warm), C.color(0xFF5A3C, 0.0),
                )))
            }
            val bh = 40*bars[i]
            rr(sx+59,682-bh,13.0,bh,6.5,C.green)
        }
        val k = M.smooth((f.legAt-0.05)/0.28)
        val labels = listOf("Distributed legroom","Extra rear legroom"); val on = if (f.legOn) 1 else 0
        if (k >= 1) text(labels[on],529.5,765.0,28.0,"Medium",C.mute,0.5)
        else {
            text(labels[1-on],529.5,765-10*k,28.0,"Medium",C.mute,0.5,1-k)
            text(labels[on],529.5,765+10*(1-k),28.0,"Medium",C.mute,0.5,k)
        }
        canvas.restore()
    }
    private fun drawFan(f: M.Frame) {
        card(369.0,833.0,321.0,237.0,if (f.card == 4) f.cardK else 0.0)
        canvas.save(); translate(529.5,918.0); rotate(360*doubleArrayOf(6.0,3.0,12.0)[f.fan]*f.amb/M.LOOP)
        for (i in 0..3) {
            canvas.save(); rotate(i*90.0)
            stroke(Path().apply { moveTo(0f,-4f); cubicTo(-4f,-16f,4f,-32f,16f,-30f); cubicTo(24f,-28f,22f,-16f,8f,-8f) },C.color(C.ink),3.6)
            canvas.restore()
        }
        canvas.restore()
        val k = M.smooth(f.fanAt/0.25)
        text(listOf("Auto","Low","High")[f.fan],529.5,1010+8*(1-k),28.0,"Medium",C.mute,0.5,k)
        canvas.restore()
    }
    private fun drawBelow() {
        text("Preferences",73.0,1131.0,30.0,"Medium",C.label)
        card(30.0,1187.0,660.0,113.0)
        text("In-car announcements",74.0,1243.5,29.0,"Regular",C.ink)
        text("All",646.0,1243.5,29.0,"Regular",C.blue,1.0); canvas.restore()
        card(30.0,1312.0,660.0,230.0)
        rr(74.0,1346.0,textWidth("Beta feature",27.0,"Medium")+26,50.0,10.0,C.chip)
        text("Beta feature",87.0,1371.0,27.0,"Medium",C.ink)
        text("Quiet mode on longer rides",74.0,1440.0,29.0,"Regular",C.ink,0.0,0.5)
        canvas.restore()
    }
    private fun tabIcon(i: Int,x: Double,y: Double) {
        canvas.save(); translate(x,y)
        val p = Path()
        when (i) {
            0 -> {
                stroke(Path().apply { addCircle(-10f,-9f,6f,Path.Direction.CW); addCircle(10f,9f,6f,Path.Direction.CW) },C.color(C.ink),3.2)
                p.moveTo(-4f,-9f); p.lineTo(10f,-9f); p.quadTo(18f,-9f,18f,-1f); p.quadTo(18f,4f,4f,4f); p.lineTo(-10f,4f)
                stroke(p,C.color(C.ink),3.2)
            }
            1 -> {
                rr(-18.0,-8.0,36.0,20.0,6.0,C.ink)
                p.moveTo(-13f,-8f); p.lineTo(-8f,-18f); p.lineTo(8f,-18f); p.lineTo(13f,-8f); p.close()
                canvas.drawPath(p,paint(C.ink))
                oval(-12.2,-1.2,6.4,6.4,C.color(0xFFFFFF)); oval(5.8,-1.2,6.4,6.4,C.color(0xFFFFFF))
                rr(-16.0,10.0,7.0,7.0,2.0,C.ink); rr(9.0,10.0,7.0,7.0,2.0,C.ink)
            }
            2 -> {
                p.addCircle(0f,-2f,16f,Path.Direction.CW); stroke(p,C.color(C.ink),3.2)
                line(-7.0,-6.0,7.0,-6.0,C.ink,2.6); line(-7.0,1.0,4.0,1.0,C.ink,2.6)
            }
            else -> {
                p.addCircle(0f,-9f,8f,Path.Direction.CW); stroke(p,C.color(C.ink),3.2)
                p.reset(); p.moveTo(-16f,16f); p.cubicTo(-14f,2f,14f,2f,16f,16f); stroke(p,C.color(C.ink),3.2)
            }
        }
        canvas.restore()
    }
    private fun drawTabs() {
        shadow(46.0,1428.0,628.0,106.0,53.0,18f,0.16)
        rr(40.0,1418.0,640.0,106.0,53.0,0xFFFFFF,0.92)
        rr(199.0,1426.0,168.0,90.0,45.0,C.tabSel)
        doubleArrayOf(130.0,283.0,436.0,589.0).forEachIndexed { i,x ->
            tabIcon(i,x,1456.0); text(listOf("My trip","My car","Help","Account")[i],x,1494.0,21.0,"Medium",C.ink,0.5)
        }
    }
    fun draw(target: Canvas,width: Float,height: Float,f: M.Frame) {
        canvas = target; canvas.save(); scale(width/720.0)
        val h = height/(width/720.0)
        canvas.drawRect(rect(0.0,0.0,720.0,h),linear(0.0,0.0,0.0,560.0,intArrayOf(C.color(C.bgTop),C.color(C.bg))))
        canvas.drawRect(rect(0.0,60.0,720.0,520.0),radial(362.0,338.0,300.0,intArrayOf(C.air(f.heat,0.14),C.air(f.heat,0.0))))
        drawPill(f); drawCar(f); text("Cabin controls",73.0,532.0,30.0,"Medium",C.label)
        drawTemp(f); drawLegroom(f); drawFan(f); drawBelow(); drawTabs()
        canvas.restore()
    }
}
