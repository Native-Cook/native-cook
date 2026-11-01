package com.ngocdevv.nativecook.blocks.cards.questmapunroll

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class QuestMapUnrollArt(context: Context) {
    private val assets = context.assets
    private val pixelScale = context.resources.displayMetrics.density
    private var cachedGeometry: QuestMapUnrollGeometry? = null
    private var headerImage: Bitmap? = null
    private var shopImages = emptyList<Bitmap>()
    private var cardImages = emptyList<Bitmap>()
    private fun controls(g: QuestMapUnrollGeometry) {
        val old = cachedGeometry
        if(old != null && old.width == g.width && old.height == g.height && old.top == g.top && old.bottom == g.bottom) return
        headerImage?.recycle(); shopImages.forEach { it.recycle() }; cardImages.forEach { it.recycle() }
        fun bitmap(w: Double, h: Double, draw: (Canvas) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(ceil(w * pixelScale).toInt(), ceil(h * pixelScale).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(b); c.scale(pixelScale, pixelScale); draw(c)
            return b
        }
        headerImage = bitmap(g.width, 44.0) { c -> c.translate(0f, -(g.top + 18).toFloat()); header(c,g) }
        shopImages = (-1..3).map { pressed -> bitmap(g.width,72.0) { c ->
            c.translate(0f, -(g.height - g.bottom - 86).toFloat()); shop(c,g,pressed)
        } }
        cardImages = (0..5).map { i -> bitmap(g.width,76.0) { c ->
            c.translate(0f, -(g.height - g.bottom - 172).toFloat())
            card(c,g,QuestMapUnrollMotion.Frame(card=1.0,level=i))
        } }
        cachedGeometry = g
    }
    private fun image(name: String) = assets.open("questmapunroll/questmapunroll_$name").use { BitmapFactory.decodeStream(it) }
    private val page = image("map-page.webp")
    private val edge = image("paper-edge.png")
    private fun font(name: String) = Typeface.createFromAsset(assets, "questmapunroll/questmapunroll_$name.ttf")
    private val title = font("TitanOne-Regular")
    private val black = font("Nunito-Black")
    private val bold = font("Nunito-ExtraBold")
    private val icons = font("Ionicons")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val star = Path().apply {
        for (i in 0..9) {
            val a = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 1) 3 else 7
            if (i == 0) moveTo((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
            else lineTo((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
        }
        close()
    }
    private val glow = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawCircle(54f, 54f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(128, 255, 227, 138)
            maskFilter = BlurMaskFilter(((12 - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
        })
    }
    private fun paint(rgb: Int, alpha: Double = 1.0): Paint {
        p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
        p.color = (0xff000000.toInt() or rgb); p.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return p
    }
    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    private fun circle(c: Canvas, x: Double, y: Double, r: Double, rgb: Int, a: Double = 1.0) = c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), paint(rgb, a))
    private fun text(c: Canvas, s: String, x: Double, y: Double, face: Typeface, size: Double, rgb: Int, center: Boolean = false, tracking: Double = 0.0) {
        val pp = paint(rgb).apply { typeface = face; textSize = size.toFloat() }
        val w = pp.measureText(s) + tracking * max(0, s.length - 1)
        var tx = x - if (center) w / 2 else 0.0
        if (tracking == 0.0) c.drawText(s, tx.toFloat(), y.toFloat(), pp)
        else for (char in s) { c.drawText(char.toString(), tx.toFloat(), y.toFloat(), pp); tx += pp.measureText(char.toString()) + tracking }
    }
    private fun centered(c: Canvas, s: String, x: Double, y: Double, face: Typeface, size: Double, rgb: Int) {
        val pp = paint(rgb).apply { typeface = face; textSize = size.toFloat() }
        text(c, s, x, y - (pp.ascent() + pp.descent()) / 2, face, size, rgb, true)
    }
    private fun icon(c: Canvas, code: Int, x: Double, y: Double, size: Double = 22.0, rgb: Int = 0xffffff) = centered(c, code.toChar().toString(), x, y, icons, size, rgb)
    private fun round(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, rgb: Int, border: Int? = null) {
        val box = rect(x,y,w,h)
        c.drawRoundRect(box,r.toFloat(),r.toFloat(),paint(rgb))
        if (border != null) {
            c.save(); c.clipPath(Path().apply { addRoundRect(box,r.toFloat(),r.toFloat(),Path.Direction.CW) })
            c.drawRect(rect(x,y+h-3,w,3.0),paint(border)); c.restore()
        }
    }
    fun draw(c: Canvas, g: QuestMapUnrollGeometry, f: QuestMapUnrollMotion.Frame, reduced: Boolean, pressed: Int) {
        c.drawColor(Color.rgb(122,46,74))
        val u = if (reduced) { if (f.u > 0.5) 1.0 else 0.0 } else f.u
        val ey = g.y + g.h * u
        if (u > 0.001) {
            c.save(); c.clipRect(rect(g.x,g.y,g.w,ey-g.y))
            c.drawBitmap(page,null,rect(g.x,g.y,g.w,g.h),paint(0xffffff,if (reduced) f.u else 1.0)); c.restore()
        }
        g.nodes.forEachIndexed { i, (nx,ny) ->
            val pass = (ey - ny) / 60
            if (pass > 0) {
                val k = if (reduced) 1.0 else QuestMapUnrollMotion.backOut(pass)
                c.save(); c.translate(nx.toFloat(),ny.toFloat()); c.scale(k.toFloat(),k.toFloat())
                if (abs(f.wisp-i)<0.02 || (i==2 && f.wisp<2.02)) {
                    val pulse = if (reduced) 0.5 else 0.5+0.5*sin(f.t*2*PI*3/9)
                    circle(c,0.0,0.0,30+6*pulse,0xffe38a,0.35+0.25*pulse)
                }
                circle(c,0.0,3.0,22.0,0x8b4a2b,0.35)
                circle(c,0.0,0.0,22.0,if (i<=2) 0xf4e3c8 else 0xe7d2b1)
                circle(c,0.0,0.0,18.0,if (i<=2) 0xffffff else 0xf1e2c8)
                text(c,"${i+1}",0.0,7.0,black,18.0,if(i<=2) 0xb4532b else 0xa58f70,true)
                for(s in 0..2) {
                    val a = -PI/2+(s-1)*0.55
                    c.save();c.translate((cos(a)*32).toFloat(),(sin(a)*32).toFloat())
                    c.drawPath(star,paint(if(s<QuestMapUnrollMotion.stars[i])0xffc23d else 0xd9c3a0));c.restore()
                }
                c.restore()
            }
        }
        if (u > 0.95) {
            val (x,y) = g.along(f.wisp)
            c.drawBitmap(glow,null,rect(x-54,y-84,108.0,108.0),paint(0xffffff))
            circle(c,x,y-30,6.0,0xfffbe6)
            for(s in 1..5) { val tw=g.along(max(0.0,f.wisp-s*0.04)); circle(c,tw.first,tw.second-30,4-s*0.6,0xffe38a,0.6-s*0.1) }
        }
        val rh=34+26*(1-u)
        c.drawRect(rect(g.x-6,ey-8,g.w+12,18.0),paint(0x5a2e18,0.18))
        c.drawBitmap(edge,null,rect(g.x-8,ey-rh*0.55,g.w+16,rh),paint(0xffffff))
        controls(g)
        c.drawBitmap(headerImage!!,null,rect(0.0,g.top+18,g.width,44.0),paint(0xffffff))
        c.drawBitmap(shopImages[(pressed+1).coerceIn(0,4)],null,rect(0.0,g.height-g.bottom-86,g.width,72.0),paint(0xffffff))
        if(f.card>0.0001) c.drawBitmap(cardImages[f.level.coerceIn(0,5)],null,
            rect(0.0,g.height-g.bottom-172+(1-f.card)*30,g.width,76.0),paint(0xffffff,f.card))
    }
    private fun header(c: Canvas,g: QuestMapUnrollGeometry) {
        val y=g.top+18
        round(c,16.0,y,44.0,44.0,14.0,0xffd45a,0xd99a1f);icon(c,61735,38.0,y+20.5,22.0,0x5a2e18)
        round(c,g.width-60,y,44.0,44.0,14.0,0xffd45a,0xd99a1f);icon(c,62827,g.width-38,y+20.5,20.0,0x5a2e18)
        val nw=paint(0xffffff).apply{typeface=title;textSize=20f}.measureText("334").toDouble()
        val tw=paint(0xffffff).apply{typeface=black;textSize=11f}.measureText("MAGIC POWER")+10*0.8
        val w=32+16+6+nw+6+tw;val x=(g.width-w)/2
        round(c,x,y,w,44.0,14.0,0x4fb548,0x2f8a2c);icon(c,62230,x+24,y+20.5,16.0)
        centered(c,"334",x+38+nw/2,y+20.5,title,20.0,0xffffff)
        text(c,"MAGIC POWER",x+38+nw+6,y+24.5,black,11.0,0xe8ffd8,tracking=0.8)
    }
    private fun shop(c: Canvas,g: QuestMapUnrollGeometry,pressed: Int) {
        val y=g.height-g.bottom-14-72
        val colors=listOf(0xff8a5b,0xb26bff,0xffc23d,0x4fb548)
        val codes=listOf(62179,62263,61888,62887)
        val labels=listOf("Daily puzzle","Gift bag","Free hints","Hint shop")
        for(i in 0..3) {
            val x=16+i*(g.width-114)/3
            c.save()
            if(pressed==i) { c.translate((x+41).toFloat(),(y+36).toFloat());c.scale(0.96f,0.96f);c.translate((-x-41).toFloat(),(-y-36).toFloat()) }
            round(c,x+15,y,52.0,52.0,16.0,colors[i])
            c.save();c.clipPath(Path().apply{addRoundRect(rect(x+15,y,52.0,52.0),16f,16f,Path.Direction.CW)})
            c.drawRect(rect(x+15,y+49,52.0,3.0),paint(0,0.18));c.restore()
            icon(c,codes[i],x+41,y+24.5)
            text(c,labels[i],x+41,y+67,black,11.0,0xffe3ec,true);c.restore()
        }
    }
    private fun card(c: Canvas,g: QuestMapUnrollGeometry,f: QuestMapUnrollMotion.Frame) {
        c.saveLayerAlpha(null,(f.card*255).roundToInt())
        val y=g.height-g.bottom-172+(1-f.card)*30;val i=f.level.coerceIn(0,5)
        round(c,20.0,y,g.width-40,76.0,24.0,0xfff4dd)
        circle(c,57.0,y+38,23.0,0xf4e3c8);circle(c,57.0,y+38,20.0,0xffffff)
        centered(c,"${i+1}",57.0,y+38,title,18.0,0xb4532b)
        text(c,QuestMapUnrollMotion.names[i],92.0,y+33,black,17.0,0x5a2e18)
        val n=QuestMapUnrollMotion.stars[i]
        text(c,if(n>0) "$n of 3 stars" else "New level · 4 spells to learn",92.0,y+52,bold,13.0,0x9a7050)
        circle(c,g.width-59,y+38,25.0,0x4fb548);icon(c,62662,g.width-59,y+38,20.0);c.restore()
    }
}
