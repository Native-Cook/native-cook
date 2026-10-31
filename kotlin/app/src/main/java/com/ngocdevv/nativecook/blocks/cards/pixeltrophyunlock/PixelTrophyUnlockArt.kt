package com.ngocdevv.nativecook.blocks.cards.pixeltrophyunlock

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class PixelTrophyUnlockArt(context: Context) {
    private val display = Typeface.createFromAsset(context.assets,"pixeltrophyunlock/pixeltrophyunlock_LilitaOne-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets,"pixeltrophyunlock/pixeltrophyunlock_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(context.assets,"pixeltrophyunlock/pixeltrophyunlock_Inter-SemiBold.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = ColorMatrix()
    private val big = mutableMapOf<String,Bitmap>()
    private val small = mutableMapOf<String,Bitmap>()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK;maskFilter = BlurMaskFilter(14f,BlurMaskFilter.Blur.NORMAL) }
    private var glowW = 0f;private var glowTop = 0f;private var glow: Shader? = null
    init {
        for((i,name) in PixelTrophyUnlockMotion.recent.withIndex())big[name] = card(name,128f,176f,PixelTrophyUnlockMotion.frames[i],PixelTrophyUnlockMotion.titles[i])
        big["tenfold"] = card("tenfold",128f,176f,"#ff9a2e",listOf("TENFOLD","TRIUMPH"))
        for((i,name) in PixelTrophyUnlockMotion.grid.withIndex())small[name] = card(name,80f,98f,if(i==0)"#ffb23a" else "#5b5d66",null)
        small["tenfold-color"] = card("tenfold",80f,98f,"#ff9a2e",null)
    }
    private fun color(s: String) = Color.parseColor(s)
    private fun fill(c: Canvas,r: RectF,color: Int,radius: Float = 0f) { paint.reset();paint.isAntiAlias = true;paint.color = color;c.drawRoundRect(r,radius,radius,paint) }
    private fun path(x: Float,y: Float,w: Float,h: Float,r: Float) = Path().apply {
        moveTo(x,y+r);quadTo(x,y,x+r,y);lineTo(x+w-r,y);quadTo(x+w,y,x+w,y+r);lineTo(x+w,y+h-h*0.14f);lineTo(x+w/2,y+h);lineTo(x,y+h-h*0.14f);close()
    }
    private fun random(i: Double): Double { val s = sin(i*12.9898)*43758.5453;return s-floor(s) }
    private val bayer = intArrayOf(0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5)
    private fun lerp(a: String,b: String,k: Double): Int {
        val A = color(a);val B = color(b)
        fun ch(shift: Int): Int { val x = A shr shift and 255;val y = B shr shift and 255;return floor(x+(y-x)*k+0.5).toInt().coerceIn(0,255) }
        return Color.rgb(ch(16),ch(8),ch(0))
    }
    private fun pixel(s: PixelTrophyUnlockScenes.Scene,x: Int,y: Int,W: Int,H: Int): Int {
        val u = (x+0.5)/W;val v = (y+0.5)/H;val horizon = 0.66
        val top = when(s.skyline) {
            "mountains" -> horizon-0.16*abs(sin(u*7.1+0.6))-0.08*abs(sin(u*13.3))
            "city" -> horizon-(0.08+0.2*random(floor(u*7)+3))*(if(floor(u*7).toInt()%2==0)1.0 else 0.7)
            "pines" -> horizon-0.18*max(0.0,1-abs((u*6)%1-0.5)*2.4)-0.03
            "hills" -> horizon-0.07-0.06*sin(u*5+1)
            else -> horizon-0.02
        }
        val d = bayer[(y%4)*4+x%4]/16.0
        if(v>=horizon) {
            val g = (v-horizon)/(1-horizon)
            if(s.skyline=="track" && y%3==0)return color("#f6efe6")
            if(s.skyline=="road" && abs(u-0.5)<0.06*(0.4+g) && y%3!=0)return color("#fff2a8")
            return color(if(g+(d-0.5)*0.25>0.5)s.land[1] else s.land[0])
        }
        if(v>=top) {
            if(s.skyline=="city" && x%2==1 && y%3==1 && random((x*31+y*7).toDouble())>0.45)return color("#ffd86a")
            return color(if(v-top<0.025 && s.skyline!="city")s.land[1] else s.land[0])
        }
        if(s.sun[2]>0) {
            val dd = hypot((u-s.sun[0])*W,(v-s.sun[1])*H);val r = s.sun[2]*W
            if(dd<r)return color(s.sunColor)
            if(dd<r*1.5 && d<1-(dd-r)/(r*0.5))return color(s.glow)
        }
        if(s.moon) { val dx = (u-0.76)*W;val dy = (v-0.2)*H;val r = W*0.11;if(hypot(dx,dy)<r && hypot(dx+r*0.45,dy-r*0.2)>r*0.85)return color("#f4f1d0") }
        if(s.stars && random((x*17+y*131).toDouble())>0.965)return Color.WHITE
        val k = v/horizon
        return if(k+(d-0.5)*0.18<0.5)lerp(s.sky[0],s.sky[1],min(1.0,(k+(d-0.5)*0.18)*2)) else lerp(s.sky[1],s.sky[2],min(1.0,(k-0.5)*2+(d-0.5)*0.18))
    }
    private fun card(name: String,w: Float,h: Float,frame: String,title: List<String>?): Bitmap {
        val bitmap = Bitmap.createBitmap((w*3).toInt(),(h*3).toInt(),Bitmap.Config.ARGB_8888);val c = Canvas(bitmap);c.scale(3f,3f)
        val r = w*0.1f;val b = w*0.045f;val ib = w*0.075f
        paint.reset();paint.isAntiAlias = true;paint.color = color(frame);c.drawPath(path(0f,0f,w,h,r),paint)
        paint.color = color("#101218");c.drawPath(path(b,b,w-2*b,h-2*b,r*0.8f),paint)
        c.save();c.clipPath(path(ib,ib,w-2*ib,h-2*ib,r*0.6f));c.translate(ib,ib)
        val W = if(title==null)24 else 32;val H = if(title==null)30 else 44;val px = if(title==null)3f else 4f
        c.scale((w-2*ib)/(W*px),(h-2*ib)/(H*px));paint.isAntiAlias = false
        val s = PixelTrophyUnlockScenes.scenes.getValue(name)
        for(y in 0 until H)for(x in 0 until W) { paint.color = pixel(s,x,y,W,H);c.drawRect(x*px,y*px,x*px+px+0.35f,y*px+px+0.35f,paint) }
        val bm = PixelTrophyUnlockScenes.emblems.getValue(s.emblem);val ex = floor((W-11)/2.0).toInt();val ey = floor(H*0.2).toInt()
        paint.color = Color.argb(217,10,8,20)
        for(j in 0..10)for(i in 0..10)if(bm[j][i]!='.')for((ox,oy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1))c.drawRect((ex+i+ox)*px,(ey+j+oy)*px,(ex+i+ox+1)*px,(ey+j+oy+1)*px,paint)
        for(j in 0..10)for(i in 0..10)if(bm[j][i]!='.') { paint.color = color(if(bm[j][i]=='#')s.ink else s.shade);c.drawRect((ex+i)*px,(ey+j)*px,(ex+i+1)*px+0.35f,(ey+j+1)*px+0.35f,paint) }
        c.restore()
        if(title!=null) {
            paint.reset();paint.isAntiAlias = true;paint.typeface = display;paint.textSize = 23f;paint.textAlign = Paint.Align.CENTER;paint.strokeJoin = Paint.Join.ROUND;paint.strokeWidth = 4f
            for((i,text) in title.withIndex()) {
                val scale = min(1f,(w-2*ib-8)/paint.measureText(text));c.save();c.translate(w/2,h*0.72f+i*23*0.92f*scale);c.scale(scale,scale)
                paint.style = Paint.Style.STROKE;paint.color = color("#14101e");c.drawText(text,0f,0f,paint)
                paint.style = Paint.Style.FILL;paint.color = Color.WHITE;c.drawText(text,0f,0f,paint);c.restore()
            }
        }
        return bitmap
    }
    private fun bitmap(c: Canvas,b: Bitmap,x: Float,y: Float,w: Float,h: Float,sat: Double = 1.0) {
        imagePaint.alpha = 255;imagePaint.colorFilter = null
        if(sat<1) {
            val k = (0.62+0.38*sat).toFloat();val a = ((1-sat)*0.2126).toFloat();val d = ((1-sat)*0.7152).toFloat();val e = ((1-sat)*0.0722).toFloat();val s = sat.toFloat()
            matrix.set(floatArrayOf((a+s)*k,d*k,e*k,0f,0f,a*k,(d+s)*k,e*k,0f,0f,a*k,d*k,(e+s)*k,0f,0f,0f,0f,0f,1f,0f));imagePaint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        c.drawBitmap(b,null,RectF(x,y,x+w,y+h),imagePaint)
    }
    fun draw(c: Canvas,w: Float,h: Float,top: Float,bottom: Float,k: PixelTrophyUnlockMotion.Channels,shake: Int,shakeTime: Double) {
        c.drawColor(color("#0e0e10"));paint.reset();paint.isAntiAlias = true
        if(glow==null || w!=glowW || top!=glowTop) { glowW = w;glowTop = top;glow = RadialGradient(w/2,top-20,w*0.9f,intArrayOf(Color.argb(140,150,110,40),Color.argb(51,60,45,20),Color.TRANSPARENT),floatArrayOf(0f,0.45f,1f),Shader.TileMode.CLAMP) }
        paint.shader = glow;c.drawRect(0f,0f,w,h,paint);paint.shader = null
        val row = top+186;val gridY = row+258;val gap = (w-368)/3
        for(i in 0..2) { val x = 24+(i+k.shift).toFloat()*146;if(x<=w+10)bitmap(c,big.getValue(PixelTrophyUnlockMotion.recent[i]),x,row,128f,176f) }
        for(i in 0..7) {
            val x = 24+(i%4)*(80+gap);val y = gridY+(i/4)*156
            if(y>h)continue
            val sat = if(i==0)1.0 else if(i==1)k.colour else 0.0;val scale = if(i==1)(1+0.08*k.pulse).toFloat() else 1f
            val dx = if(i==shake && shakeTime>=0 && shakeTime<0.45)(sin(shakeTime*40)*5*exp(-shakeTime*7)).toFloat() else 0f
            c.save();c.translate(x+40+dx,y+49);c.scale(scale,scale);c.translate(-40f,-49f)
            val name = PixelTrophyUnlockMotion.grid[i]+if(i==1 && k.colour>0.02)"-color" else ""
            bitmap(c,small.getValue(name),0f,0f,80f,98f,sat)
            if(i==1 && k.sweep>=0 && k.sweep<=1) {
                c.save();c.clipPath(path(0f,0f,80f,98f,8f));val bx = (-48+k.sweep*176).toFloat()
                paint.reset();paint.shader = LinearGradient(bx-24,0f,bx+24,39.2f,intArrayOf(Color.TRANSPARENT,Color.argb(217,255,255,240),Color.TRANSPARENT),floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP);paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD);c.drawRect(0f,0f,80f,98f,paint);paint.reset();c.restore()
            }
            val lock = if(i==0)0.0 else if(i==1)1-min(1.0,k.colour*3) else 1.0
            if(lock>0.01) {
                paint.reset();paint.isAntiAlias = true;paint.color = color("#1a1b20");paint.alpha = (255*0.85*lock).roundToInt();c.drawCircle(40f,45.08f,15f,paint)
                paint.color = color("#d9dbe2");paint.alpha = (255*lock).roundToInt();c.drawRoundRect(RectF(33f,43.08f,47f,54.08f),2.5f,2.5f,paint)
                val lift = if(i==1)(min(1.0,k.colour*3)*4).toFloat() else 0f
                paint.style = Paint.Style.STROKE;paint.strokeWidth = 2.4f;c.drawArc(RectF(35.5f,36.08f-lift,44.5f,48.08f-lift),180f,180f,false,paint);paint.reset()
            }
            c.restore()
        }
        if(k.fly>0.001 && k.gone<0.999) {
            val x = PixelTrophyUnlockMotion.mix((24+80+gap+40).toDouble(),88.0,k.fly).toFloat()
            val y = (PixelTrophyUnlockMotion.mix((gridY+49).toDouble(),(row+88).toDouble(),k.fly)-sin(k.fly*PI)*90).toFloat()
            val s = (PixelTrophyUnlockMotion.mix(80.0/128,1.0,PixelTrophyUnlockMotion.smooth(k.fly))*(1+k.bounce)*(1-0.25*k.gone)).toFloat()
            c.save();c.translate(x,y);c.rotate((sin(k.fly*PI)*-10).toFloat());c.scale(s,s);c.saveLayerAlpha(null,((1-k.gone)*255).roundToInt())
            shadow.alpha = (max(0.0,0.45*sin(k.fly*PI))*255).roundToInt();c.drawRect(-54f,-64f,54f,92f,shadow)
            bitmap(c,big.getValue("tenfold"),-64f,-88f,128f,176f);c.restore();c.restore()
        }
        copy(c,w,h,top,bottom,k.earned)
    }
    private fun text(c: Canvas,s: String,font: Typeface,size: Float,x: Float,y: Float,color: Int = Color.WHITE,align: Paint.Align = Paint.Align.LEFT,spacing: Float = 0f) {
        paint.reset();paint.isAntiAlias = true;paint.typeface = font;paint.textSize = size;paint.textAlign = align;paint.color = color
        if(spacing==0f)c.drawText(s,x,y-paint.fontMetrics.top,paint)
        else {
            val widths = FloatArray(s.length);paint.getTextWidths(s,widths);val width = widths.sum()+spacing*s.length
            var xx = x-width/2;paint.textAlign = Paint.Align.LEFT
            for(i in s.indices) { c.drawText(s.substring(i,i+1),xx,y-paint.fontMetrics.top,paint);xx += widths[i]+spacing }
        }
    }
    private fun copy(c: Canvas,w: Float,h: Float,top: Float,bottom: Float,earned: Boolean) {
        val row = top+186;val gridY = row+258;val count = if(earned)5 else 4
        text(c,"Achievements",display,32f,w/2,top+26,align = Paint.Align.CENTER,spacing = 0.3f)
        text(c,"$count / 67 earned",medium,17f,24f,top+84,Color.argb(209,255,255,255));text(c,if(earned)"7%" else "5%",medium,17f,w-24,top+84,Color.argb(209,255,255,255),Paint.Align.RIGHT)
        val barY = top+84+22+12
        fill(c,RectF(24f,barY,w-24,barY+16),color("#24331c"),8f);fill(c,RectF(24f,barY,24+(w-48)*count/67,barY+16),color("#6ee03a"),8f)
        paint.reset();paint.typeface = semibold;paint.textSize = 16f;val pillW = paint.measureText("Recently earned")+36
        fill(c,RectF((w-pillW)/2,row-44,(w+pillW)/2,row-8),Color.argb(31,255,255,255),18f)
        text(c,"Recently earned",semibold,16f,w/2,row-44+7,Color.argb(217,255,255,255),Paint.Align.CENTER)
        paint.typeface = display;paint.textSize = 16f;val commonW = paint.measureText("COMMON")+6;val ruleW = (w-48-commonW-28)/2
        fill(c,RectF(24f,gridY-38,24+ruleW,gridY-35),color("#ff6a3a"),1.5f);fill(c,RectF(w-24-ruleW,gridY-38,w-24,gridY-35),color("#ffc23a"),1.5f)
        text(c,"COMMON",display,16f,w/2,gridY-46,align = Paint.Align.CENTER,spacing = 1f)
        val gap = (w-368)/3
        for(i in 0..7) {
            val x = 24+(i%4)*(80+gap)+40;val y = gridY+(i/4)*156+106
            val col = if(i==0 || i==1 && earned)Color.WHITE else Color.argb(102,255,255,255)
            val name = PixelTrophyUnlockMotion.names[i]
            paint.reset();paint.typeface = semibold;paint.textSize = 13f
            if(paint.measureText(name)<=100)text(c,name,semibold,13f,x,y,col,Paint.Align.CENTER)
            else { val split = name.lastIndexOf(' ');text(c,name.take(split),semibold,13f,x,y,col,Paint.Align.CENTER);text(c,name.substring(split+1),semibold,13f,x,y+16,col,Paint.Align.CENTER) }
        }
        paint.reset();paint.typeface = semibold;paint.textSize = 15f;val closeW = paint.measureText("Close")+48
        val closeY = h-bottom-54
        fill(c,RectF((w-closeW)/2,closeY,(w+closeW)/2,closeY+40),Color.argb(235,60,60,66),20f)
        text(c,"Close",semibold,15f,w/2,closeY+10,align = Paint.Align.CENTER)
    }
}
