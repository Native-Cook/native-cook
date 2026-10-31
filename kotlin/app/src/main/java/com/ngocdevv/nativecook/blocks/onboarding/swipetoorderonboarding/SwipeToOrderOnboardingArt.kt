package com.ngocdevv.nativecook.blocks.onboarding.swipetoorderonboarding

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class SwipeToOrderOnboardingArt(context: Context) {
    private val display = Typeface.createFromAsset(context.assets, "swipetoorderonboarding/swipetoorderonboarding_Epilogue-400.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "swipetoorderonboarding/swipetoorderonboarding_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(context.assets, "swipetoorderonboarding/swipetoorderonboarding_Inter-SemiBold.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lime = Color.rgb(200, 245, 74)
    private val ink = Color.rgb(29, 26, 43)
    private val purple = Color.rgb(106, 55, 239)
    private val oval = RectF()
    private val rounded = Path()
    private val cards: List<Bitmap>
    private val badge: Bitmap
    private val labels = mutableMapOf<String, Bitmap>()
    private val confetti = Array(46) { i -> doubleArrayOf(78.233, 156.466, 234.699).map { val n = sin((i + 1) * 12.9898 + it) * 43758.5453; n - floor(n) }.toDoubleArray() }
    private val colors = intArrayOf(lime, Color.WHITE, Color.rgb(255,138,216), Color.rgb(255,216,74), Color.rgb(143,208,255))
    private val bolt = Path().apply { moveTo(14f,2f); lineTo(5f,14f); lineTo(11f,14f); lineTo(10f,22f); lineTo(19f,10f); lineTo(13f,10f); close() }
    private val tick = Path().apply { moveTo(5f,12.5f); lineTo(9.5f,17f); lineTo(19f,7.5f) }
    private val arrow = Path().apply { moveTo(6f,18f); lineTo(18f,6f); moveTo(9f,6f); lineTo(18f,6f); lineTo(18f,15f) }
    private fun configure(face: Typeface, size: Float, color: Int, kern: Float = 0f) {
        paint.reset(); paint.isAntiAlias = true; paint.typeface = face; paint.textSize = size
        paint.color = color; paint.letterSpacingCompat(kern)
    }
    // Android Canvas has no tracking property; draw measured glyph runs when tracking is nonzero.
    private var tracking = 0f
    private fun Paint.letterSpacingCompat(kern: Float) { tracking = kern }
    private fun width(s: String, face: Typeface, size: Float, kern: Float = 0f): Float {
        configure(face, size, Color.WHITE, kern)
        return paint.measureText(s) + kern * s.length
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, face: Typeface, size: Float, color: Int, kern: Float = 0f, line: Float = 0f) {
        configure(face, size, color, kern)
        val fm = paint.fontMetrics
        val h = fm.descent - fm.ascent
        val baseline = y + (if (line == 0f) h else line) / 2 - (fm.ascent + fm.descent) / 2
        if (tracking == 0f) c.drawText(s, x, baseline, paint)
        else {
            var xx = x
            s.forEach { char -> val a = char.toString(); c.drawText(a, xx, baseline, paint); xx += paint.measureText(a) + tracking }
        }
    }
    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, rad: Float, color: Int) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        oval.set(x,y,x+w,y+h); c.drawRoundRect(oval, rad, rad, paint)
    }
    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; c.drawCircle(x,y,r,paint)
    }
    private fun image(w: Float, h: Float, draw: (Canvas) -> Unit): Bitmap {
        val bm = Bitmap.createBitmap(ceil(w*3).toInt(), ceil(h*3).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bm).apply { scale(3f,3f); draw(this) }; return bm
    }
    private fun label(c: Canvas, key: String, x: Float, y: Float, alpha: Float = 1f, center: Boolean = false) {
        val bm = labels[key] ?: return
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.alpha = (alpha.coerceIn(0f,1f)*255).roundToInt()
        val w = bm.width/3f; val h = bm.height/3f
        oval.set(x - if (center) w/2 else 0f, y, x + if (center) w/2 else w, y+h)
        c.drawBitmap(bm,null,oval,paint)
    }
    private val takeWidth = width("Take ",display,50f,-1.5f)
    private val ofWidth = width("of ",display,50f,-1.5f)
    private val pillWidth = width("control",display,50f,-1.5f)+32
    init {
        listOf("brand" to "Taskloop", "skip" to "Skip →", "take" to "Take ", "of" to "of ", "your" to " your", "schedule" to "schedule", "control" to "control", "controlInk" to "control", "start" to "Swipe to get started", "done" to "You're all set").forEach { (key,s) ->
            val head = key in listOf("take","of","your","schedule","control","controlInk")
            val face = if (head || key == "brand") display else if (key == "skip") medium else semibold
            val size = if (head) 50f else if (key == "brand") 20f else if (key == "skip") 15f else 17f
            val kern = if (head) -1.5f else if (key == "brand") -0.3f else 0f
            val w = width(s,face,size,kern)+2
            val h = if (head) 58f else paint.fontMetrics.descent - paint.fontMetrics.ascent
            labels[key] = image(w,h) { text(it,s,0f,0f,face,size,if (key in listOf("controlInk","start","done")) ink else Color.WHITE,kern,h) }
        }
        val titles = listOf("Client Call ABC Corp.", "Focus Block · Code Review", "Leisure Reading")
        cards = List(3) { i -> image(300f,86f) { c ->
            rr(c,0f,0f,300f,86f,18f,if (i == 0) Color.rgb(145,111,244) else if (i == 1) lime else Color.rgb(36,32,47))
            val col = if (i == 1) ink else Color.WHITE
            c.save(); c.clipRect(19f,0f,if (i == 1) 282f else 224f,86f)
            text(c,titles[i],19f,20f,medium,18f,col,-0.2f,22f)
            c.restore()
            if (i != 1) {
                text(c,if(i == 0) "12 Hours Needed" else "2 Hours Needed",19f,48f,medium,13f,0xb3ffffff.toInt(),line=16f)
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                oval.set(237f,22f,279f,64f)
                paint.color = 0x2effffff; c.drawArc(oval,-135f,180f,false,paint)
                paint.color = lime; c.drawArc(oval,45f,180f,false,paint)
                val pct = if (i == 0) "24%" else "60%"
                val w = width(pct,semibold,12f)
                text(c,pct,258f-w/2,35f,semibold,12f,Color.WHITE,line=16f)
            } else {
                var x = 19f
                for (tag in listOf("DESIGN", "REVIEW")) {
                    val w = width(tag,semibold,10f)+14
                    rr(c,x,48f,w,19f,9f,ink)
                    text(c,tag,x+7,51f,semibold,10f,lime,line=13f)
                    x += w+6
                }
                text(c,"Nov 12",x,49.5f,medium,13f,0xa61d1a2b.toInt(),line=16f)
            }
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
            paint.color = if (i == 0) 0x47ffffff else if (i == 1) 0x141d1a2b else 0x1affffff
            oval.set(0.5f,0.5f,299.5f,85.5f); c.drawRoundRect(oval,17.5f,17.5f,paint)
        } }
        badge = image(128f,128f) { c ->
            val s = "PLAN SMARTER • TAKE CONTROL • "
            configure(semibold,9.5f,ink)
            val advances = FloatArray(s.length); paint.getTextWidths(s,advances)
            val scale = 2*PI*47/advances.sum()
            var distance = 0.0
            s.forEachIndexed { i, ch ->
                val w = advances[i]
                val a = (distance+w/2)*scale/47-PI/2
                val cs = cos(a+PI/2); val sn = sin(a+PI/2)
                c.save(); c.translate((64+cos(a)*47-cs*w/2-sn*3.4).toFloat(),(64+sin(a)*47-sn*w/2+cs*3.4).toFloat())
                c.rotate(((a+PI/2)*180/PI).toFloat()); c.drawText(ch.toString(),0f,0f,paint); c.restore(); distance += w
            }
        }
    }
    fun draw(c: Canvas, w: Float, h: Float, topInset: Float, bottom: Float, f: SwipeToOrderOnboardingMotion.Frame, reduced: Boolean) {
        val top = topInset+56
        c.drawColor(purple)
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = 92f; paint.color = Color.rgb(91,43,224)
        c.drawCircle(40f,top+230,144f,paint)
        label(c,"brand",22f,topInset+12)
        label(c,"skip",w-22-labels["skip"]!!.width/3f,topInset+17)
        for (i in listOf(2,1,0)) {
            val b = f.cards[i].p
            c.save(); c.translate(w/2+b[0].toFloat(),top+b[1].toFloat()); c.rotate(b[2].toFloat()); c.scale(b[3].toFloat(),b[3].toFloat())
            paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; oval.set(-150f,-43f,150f,43f)
            c.drawBitmap(cards[i],null,oval,paint); c.restore()
        }
        val q = SwipeToOrderOnboardingMotion.smooth(f.s).toFloat()
        c.save(); c.translate(88+(w-152)*q,top+60+395*q); c.scale(1-0.38f*q,1-0.38f*q)
        c.rotate(((f.spin+if (reduced) 0.0 else 2*PI*f.s)*180/PI).toFloat())
        circle(c,0f,0f,59f,lime)
        paint.reset(); paint.isFilterBitmap = true; oval.set(-64f,-64f,64f,64f); c.drawBitmap(badge,null,oval,paint)
        circle(c,0f,0f,31f,ink); c.translate(-12f,-12f)
        paint.reset(); paint.isAntiAlias=true; paint.color=lime
        if (f.done) { paint.style=Paint.Style.STROKE; paint.strokeWidth=2.6f; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND }
        c.drawPath(if(f.done) tick else bolt,paint); c.restore()
        val hy = top+368; val px = 24+takeWidth
        label(c,"take",24f,hy+1)
        c.save(); rounded.reset(); oval.set(px,hy,px+pillWidth,hy+60); rounded.addRoundRect(oval,32f,32f,Path.Direction.CW); c.clipPath(rounded)
        label(c,"control",px+16,hy+3)
        c.save(); c.clipRect(px+2,hy+2,px+2+q*(pillWidth-4),hy+58)
        rr(c,px+2,hy+2,pillWidth-4,56f,0f,lime); label(c,"controlInk",px+16,hy+5); c.restore(); c.restore()
        paint.reset(); paint.isAntiAlias = true; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.color=Color.WHITE
        oval.set(px+1,hy+1,px+pillWidth-1,hy+59); c.drawRoundRect(oval,31f,31f,paint)
        label(c,"of",24f,hy+60)
        val dotX=24+ofWidth
        circle(c,dotX+23,hy+89,23f,lime)
        c.save(); rounded.reset(); rounded.addCircle(dotX+23,hy+89,23f,Path.Direction.CW); c.clipPath(rounded); rr(c,dotX+23,hy+66,23f,46f,0f,ink); c.restore()
        label(c,"your",dotX+46,hy+60); label(c,"schedule",24f,hy+118)
        val bw=w-40; val by=h-bottom-84; val range=bw-62
        rr(c,20f,by,bw,62f,31f,lime)
        rr(c,20f,by,62+(f.s*range).toFloat(),62f,31f,Color.rgb(180,224,54))
        label(c,"start",w/2+(f.s*30).toFloat(),by+(62-labels["start"]!!.height/3f)/2,(1-SwipeToOrderOnboardingMotion.smooth(f.s/0.4)).toFloat(),true)
        label(c,"done",w/2,by+(62-labels["done"]!!.height/3f)/2,SwipeToOrderOnboardingMotion.smooth((f.s-0.8)/0.2).toFloat(),true)
        val kx=51+(f.s*range).toFloat()
        circle(c,kx,by+31,27f,ink)
        c.save(); c.translate(kx-12,by+19)
        paint.reset(); paint.isAntiAlias=true; paint.color=Color.WHITE; paint.style=Paint.Style.STROKE; paint.strokeWidth=2.4f; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        c.drawPath(if(f.done) tick else arrow,paint); c.restore()
        if (!reduced && f.since >= 0 && f.since <= 1.6) {
            val t=f.since
            confetti.forEachIndexed { i, seed ->
                val a=-PI/2+(seed[0]-0.62)*2.2; val v=380+seed[1]*520
                c.save(); c.translate((w-51+cos(a)*v*t*(1-t*0.25)).toFloat(),(by+31+sin(a)*v*t+900*t*t).toFloat())
                c.rotate((t*720*(seed[2]-0.5)+i*40).toFloat()); c.scale(1f,(abs(cos(t*9+i))+0.15).toFloat())
                paint.reset(); paint.isAntiAlias=true; paint.color=colors[i%5]; paint.alpha=((1-SwipeToOrderOnboardingMotion.smooth((t-1)/0.6))*255).roundToInt()
                c.drawRect(-4f,-2.5f,4f,2.5f,paint); c.restore()
            }
        }
    }
}
