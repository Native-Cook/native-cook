package com.ngocdevv.nativecook.blocks.cards.seasontiermedals

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.seasontiermedals.SeasonTierMedalsMotion as M
import com.ngocdevv.nativecook.blocks.cards.seasontiermedals.SeasonTierMedalsTheme as H

internal class SeasonTierMedalsArt(context: Context) {
    private val fonts = listOf("Regular", "Medium", "SemiBold", "Display").associateWith {
        val file = if (it == "Display") "BarlowCondensed-ExtraBold" else "Inter-$it"
        Typeface.createFromAsset(context.assets, "seasontiermedals/seasontiermedals_$file.ttf")
    }
    private val paints = mutableMapOf<String, TextPaint>()
    private val widths = mutableMapOf<String, Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val runtime = if (Build.VERSION.SDK_INT >= 33) SeasonTierMedalsRuntime() else null
    private val tee = Path().apply {
        moveTo(9f, 6f); lineTo(13f, 4f); cubicTo(14f, 6.5f, 18f, 6.5f, 19f, 4f)
        lineTo(23f, 6f); lineTo(26f, 11f); lineTo(22.5f, 13f); lineTo(21.5f, 11.5f)
        lineTo(21.5f, 24f); lineTo(10.5f, 24f); lineTo(10.5f, 11.5f); lineTo(9.5f, 13f); lineTo(6f, 11f); close()
    }
    private val chevron = Path().apply { moveTo(4.48f, 9.92f); lineTo(8f, 6.4f); lineTo(11.52f, 9.92f) }
    private val emblem = Path().apply { moveTo(-0.40f, -0.15f); lineTo(0f, 0.16f); lineTo(0.40f, -0.15f) }
    private var base: Bitmap? = null
    private var halo: Bitmap? = null
    private var core: Bitmap? = null
    private var key = ""
    var claimY = 0.0; private set
    private var w = 0.0
    private var k = 0.0
    private val rect = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun color(hex: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or hex
    private fun blend(colors: Array<DoubleArray>, p: Double, a: Double): Int {
        val i = floor(p).toInt().coerceIn(0, 1); val f = M.clamp(p - i)
        val values = (0..2).map { (M.mix(colors[i][it], colors[i+1][it], f) * 255).roundToInt() }
        return Color.argb((a * 255).roundToInt(), values[0], values[1], values[2])
    }
    private fun solid(hex: Int, a: Double = 1.0): Paint {
        paint.shader = null; paint.colorFilter = null; paint.color = color(hex, a); return paint
    }
    private fun rr(c: Canvas, x: Double, y: Double, width: Double, height: Double, r: Double, hex: Int, a: Double = 1.0) {
        c.drawRoundRect(x.toFloat(), y.toFloat(), (x+width).toFloat(), (y+height).toFloat(), r.toFloat(), r.toFloat(), solid(hex,a))
    }
    private fun textPaint(weight: String, size: Double, hex: Int, a: Double, tracking: Double = 0.0, tabular: Boolean = false): TextPaint {
        val id = "$weight:$size:$tracking:$tabular"
        val p = paints.getOrPut(id) { TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.getValue(weight); textSize = size.toFloat()
            if (tabular) fontFeatureSettings = "tnum"
        } }
        p.color = color(hex, a); return p
    }
    private fun text(c: Canvas, value: String, x: Double, y: Double, width: Double, weight: String, size: Double,
                     line: Double, hex: Int = 0xffffff, a: Double = 1.0, align: Int = 0, tracking: Double = 0.0, tabular: Boolean = false) {
        val p = textPaint(weight,size,hex,a,tracking,tabular)
        val naturalWidth = widths.getOrPut("$value:$weight:$size:$tabular") { p.measureText(value) }
        val fullWidth = naturalWidth + tracking * (value.length - 1)
        val left = when(align) { -1 -> x; 1 -> x + width - fullWidth; else -> x + (width - fullWidth) / 2 }
        val fm = p.fontMetrics
        val baseline = y + (line - (fm.descent - fm.ascent)) / 2 - fm.ascent
        if (tracking == 0.0) c.drawText(value, left.toFloat(), baseline.toFloat(), p)
        else {
            var tx = left.toFloat()
            for (index in value.indices) {
                c.drawText(value, index, index+1, tx, baseline.toFloat(), p)
                tx += p.measureText(value, index, index+1) + tracking.toFloat()
            }
        }
    }
    private fun paragraph(value: String, weight: String, size: Double, line: Double, width: Double, alpha: Double): StaticLayout {
        val p = TextPaint(textPaint(weight,size,0xffffff,alpha))
        return StaticLayout.Builder.obtain(value,0,value.length,p,width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).setLineSpacing((line - (p.fontMetrics.descent - p.fontMetrics.ascent)).toFloat(),1f).build()
    }
    private fun image(c: Canvas, bitmap: Bitmap, width: Double, height: Double, alpha: Double = 1.0, tint: Int? = null) {
        rect.set(0f,0f,width.toFloat(),height.toFloat())
        bitmapPaint.alpha = (alpha * 255).roundToInt().coerceIn(0,255)
        bitmapPaint.colorFilter = tint?.let { PorterDuffColorFilter(it,PorterDuff.Mode.SRC_IN) }
        c.drawBitmap(bitmap,null,rect,bitmapPaint)
        bitmapPaint.colorFilter = null; bitmapPaint.alpha = 255
    }
    fun prepare(width: Double, height: Double, top: Double, density: Float) {
        val next = "$width:$height:$top:$density"
        if (key == next) return
        key = next; w = width; k = top + 62
        base?.recycle(); halo?.recycle(); core?.recycle()
        fun bitmap() = Bitmap.createBitmap(ceil(width * density).toInt().coerceAtLeast(1),ceil(height * density).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        base = bitmap().also { bmp ->
            val c = Canvas(bmp); c.scale(density,density)
            val b = 1.0 / density
            text(c,"‹",8.0,top+3,44.0,"Regular",34.0,38.0)
            text(c,"Season perks",52.0,top+13,width-104,"SemiBold",17.0,22.0)
            stroke.color = Color.WHITE; stroke.strokeWidth = 2f
            c.drawCircle((width-30).toFloat(),(top+24).toFloat(),12f,stroke)
            text(c,"?",width-43,top+13,26.0,"SemiBold",15.0,22.0)
            rr(c,24.0,k+292,width-48,140.0,32.0,0x42444a,0.62)
            stroke.color = color(0xffffff,0.14); stroke.strokeWidth = 1f/density
            c.drawRoundRect((24+b/2).toFloat(),(k+292+b/2).toFloat(),(width-24-b/2).toFloat(),(k+432-b/2).toFloat(),32f,32f,stroke)
            rr(c,48+b,k+361+b,width-234-2*b,10.0,5.0,0xffffff,0.16)
            c.drawCircle((width-57-b).toFloat(),(k+366+b).toFloat(),9f,solid(0xffffff))
            c.save(); c.translate((width-65-b).toFloat(),(k+358+b).toFloat())
            stroke.color = color(0x141416); stroke.strokeWidth = 2.2f; c.drawPath(chevron,stroke); c.restore()
            text(c,"Claim your rewards",0.0,k+470,width,"SemiBold",26.0,32.0)
            for (i in 0..1) {
                val y = k + 516 + i * 148
                val title = if(i==0) "7% back on clothing and shoes" else "Double points at cafés"
                val subtitle = if(i==0) "Paid as bonus points with a Season card" else "Every weekend until the season ends"
                val titleLayout = paragraph(title,"Medium",17.0,22.0,width-122,1.0)
                val subLayout = paragraph(subtitle,"Regular",14.0,19.0,width-122,0.6)
                val rowH = max(44.0,titleLayout.lineCount * 22.0+2+subLayout.lineCount * 19)
                rr(c,16.0,y,width-32,32+rowH+(if(i==0)62 else 0),26.0,0x29292d)
                val iconY = y+16+(rowH-44)/2
                c.drawCircle(54f,(iconY+22).toFloat(),22f,solid(if(i==0)0xc070d8 else 0xe0a33c))
                if(i==0) { c.save(); c.translate(38f,(iconY+8).toFloat()); c.drawPath(tee,solid(0xffffff)); c.restore() }
                else text(c,"×2",32.0,iconY+11,44.0,"SemiBold",16.0,22.0)
                val textY = y+16+(rowH-(titleLayout.lineCount*22+2+subLayout.lineCount*19))/2
                c.save(); c.translate(90f,textY.toFloat()); titleLayout.draw(c); c.restore()
                c.save(); c.translate(90f,(textY+titleLayout.lineCount*22+2).toFloat()); subLayout.draw(c); c.restore()
                if(i==0) claimY = y+16+rowH+14
            }
        }
        val rc = min(84.0,width*0.21); val my = k+186
        fun blur(rx: Double, ry: Double, sigma: Double): Bitmap {
            val bmp = bitmap(); val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE
                maskFilter = BlurMaskFilter(((sigma*density-0.5)/0.57735).toFloat().coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL) }
            c.drawOval(((width/2-rx)*density).toFloat(),((my-ry)*density).toFloat(),((width/2+rx)*density).toFloat(),((my+ry)*density).toFloat(),p)
            return bmp
        }
        halo = blur(rc*1.55,rc*1.05,rc*0.75); core = blur(rc*1.05,rc*0.95,rc*0.35)
        // Warm caches for every dynamic label before frames start.
        for(i in 0..2) {
            textPaint("Display",50.0,0xffffff,1.0,0.5)
            textPaint("Medium",15.0,0xffffff,1.0); textPaint("Medium",20.0,0xffffff,1.0)
            textPaint("Regular",16.0,0xffffff,1.0,tabular=true); textPaint("Regular",14.0,0xffffff,1.0)
        }
    }
    fun draw(c: Canvas, width: Double, height: Double, top: Double, density: Float, d: SeasonTierMedalsDriver, pressed: Boolean) {
        prepare(width,height,top,density)
        val p = d.rail.p; val now = d.now; val t = d.time; val my = k+186
        val rc = min(84.0,w*0.21); val sp = min(172.0,w*0.43)
        c.drawColor(color(0x121214))
        paint.color = Color.WHITE; paint.alpha = 255
        paint.shader = RadialGradient((w/2).toFloat(),(my-40).toFloat(),(height*0.75).toFloat(),intArrayOf(blend(H.pages,p,0.85),color(0x121214,0.0)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w.toFloat(),height.toFloat(),paint); paint.shader = null
        val flash = d.rail.spins.maxOf { M.flash(now-it) }
        image(c,halo!!,w,height,0.2+0.12*flash,blend(H.halos,p,1.0))
        image(c,core!!,w,height,0.1+0.1*flash)
        val sw = M.clamp((t-6)/1.1); val sweep = if(sw>0 && sw<1) M.mix(-2.2,2.2,M.smooth(sw)) else 9.0
        for(i in listOf(0,1,2).sortedByDescending { abs(it-p) }) {
            val o = i-p; val ao = abs(o); if(ao>2.3) continue
            val side = min(ao,1.0); val far = max(0.0,ao-1)
            val r = rc*M.mix(1.0,0.64,side)*M.mix(1.0,0.8,min(far,1.0))
            val x = w/2+sign(o)*(sp*side+far*110); val y = my+side*4
            val since = now-d.rail.spins[i]
            val yaw = -o.coerceIn(-1.5,1.5)*0.62+M.yaw(since)+0.05*sin(2*PI*2*t/M.LOOP+i*1.7)
            if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
                paint.color = Color.WHITE
                paint.shader = runtime.shader(i,x,y,r,yaw,if(ao<0.5)sweep else 9.0,-0.42*side+0.5*M.flash(since))
                c.drawRect((x-r*1.25).toFloat(),(y-r*1.25).toFloat(),(x+r*1.25).toFloat(),(y+r*1.25).toFloat(),paint)
                paint.shader = null
            } else fallback(c,i,x,y,r,yaw)
        }
        image(c,base!!,w,height)
        val b = 1.0 / density
        val cp = p.coerceIn(0.0,2.0); val index = floor(cp).toInt().coerceAtMost(1)
        val fill = M.mix(H.fills[index],H.fills[index+1],cp-index)
        for(i in 0..2) rr(c,48+b,k+361+b,(w-234-2*b)*fill,10.0,5.0,H.bars[i],M.clamp(1-abs(p-i)))
        for(i in 0..2) {
            val weight = M.clamp(1-abs(p-i)*2.1); val alpha = weight.pow(1.3); val dx = (i-p)*28
            text(c,H.kickers[i],dx,k,w,"Medium",15.0,19.0,0xe8e8ea,alpha)
            c.save(); c.translate((w/2+(i-p)*70).toFloat(),(k+50).toFloat())
            val scale = M.mix(0.92,1.0,weight).toFloat(); c.scale(scale,scale)
            text(c,H.names[i],-w/2,-28.0,w,"Display",50.0,56.0,0xf4f4f6,alpha,tracking=0.5); c.restore()
            text(c,H.titles[i],48+b+dx,k+314+b,w-96-2*b,"Medium",20.0,26.0,a=alpha)
            text(c,H.nums[i],w-176-b+dx,k+354+b,100.0,"Regular",16.0,24.0,a=alpha,align=1,tabular=true)
            text(c,H.feet[i],48+b+dx,k+390+b,w-96-2*b,"Regular",14.0,20.0,a=alpha*0.72)
        }
        val widths = (0..2).map { M.mix(22.0,30.0,M.clamp(1-abs(p-it))) }
        var x = (w-widths.sum()-12)/2
        for(i in 0..2) { rr(c,x,k+446,widths[i],4.0,2.0,0xffffff,M.mix(0.28,1.0,M.clamp(1-abs(p-i)))); x+=widths[i]+6 }
        c.save()
        if(pressed) { c.scale(0.97f,0.97f,(w/2).toFloat(),(claimY+24).toFloat()) }
        rr(c,32.0,claimY,w-64,48.0,16.0,0x0d0d0f,if(pressed)0.9 else 1.0)
        text(c,"Claim",32.0,claimY+13,w-64,"SemiBold",17.0,22.0,a=if(pressed)0.9 else 1.0)
        c.restore()
    }
    private fun fallback(c: Canvas, i: Int, x: Double, y: Double, r: Double, yaw: Double) {
        c.save(); c.translate(x.toFloat(),y.toFloat())
        c.scale((abs(cos(yaw))+0.17*abs(sin(yaw))).toFloat(),1f)
        val col = H.bars[i]; paint.color = Color.WHITE
        paint.shader = LinearGradient((-r).toFloat(),(-r).toFloat(),r.toFloat(),r.toFloat(),intArrayOf(color(0xfff1db),color(col),color(0x30241e)),null,Shader.TileMode.CLAMP)
        c.drawCircle(0f,0f,r.toFloat(),paint); paint.shader = null
        stroke.color = color(0xffffff,0.55); stroke.strokeWidth = (r*0.075).toFloat()
        c.drawCircle(0f,0f,(r*0.89).toFloat(),stroke)
        if(cos(yaw)>0) {
            c.scale(r.toFloat(),r.toFloat()); stroke.color = color(0xffffff,0.7); stroke.strokeWidth = 0.16f
            val s = if(i==2)0.74f else if(i==1)0.88f else 1f
            c.scale(s,s)
            for(j in 0..i) { c.save(); c.translate(0f,((i*0.32/2-j*0.32)-0.04).toFloat()); c.drawPath(emblem,stroke); c.restore() }
        }
        c.restore()
    }
    fun close() { base?.recycle(); halo?.recycle(); core?.recycle() }
}
