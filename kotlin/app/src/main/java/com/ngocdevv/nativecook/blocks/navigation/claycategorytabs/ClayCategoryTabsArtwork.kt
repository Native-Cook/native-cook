package com.ngocdevv.nativecook.blocks.navigation.claycategorytabs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.text.TextPaint
import kotlin.math.*

/** Static UI uses native text metrics and the bundled Inter faces, in the reference's layout units. */
internal class ClayCategoryTabsArtwork(context: Context) {
    private val fonts = listOf("Regular","Medium","SemiBold").map {
        Typeface.createFromAsset(context.assets,"claycategorytabs/claycategorytabs_Inter-$it.ttf")
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader = if (Build.VERSION.SDK_INT >= 33) ClayCategoryTabsShader() else null
    private val page = Color.rgb(17,16,21)
    private val card = Color.rgb(27,26,33)
    private val line = Color.rgb(42,41,50)
    private val ink = Color.rgb(244,243,247)
    private val mute = Color.rgb(142,140,153)
    private val star = Color.rgb(255,194,74)
    private fun font(size: Float,weight: Int): TextPaint {
        textPaint.typeface = fonts[weight]; textPaint.textSize = size; textPaint.color = ink
        textPaint.textAlign = Paint.Align.LEFT; textPaint.alpha = 255
        return textPaint
    }
    private fun text(c: Canvas,value: String,x: Float,y: Float,size: Float,lineH: Float,color: Int,
        weight: Int = 0,width: Float = Float.POSITIVE_INFINITY,align: Paint.Align = Paint.Align.LEFT) {
        val p = font(size,weight); p.color = color; p.textAlign = align
        val rendered = if (width.isFinite()) TextUtils.ellipsize(value,p,width,TextUtils.TruncateAt.END).toString() else value
        val m = p.fontMetrics
        c.drawText(rendered,x,y+(lineH-(m.descent-m.ascent))/2-m.ascent,p)
    }
    private fun round(c: Canvas,x: Float,y: Float,w: Float,h: Float,radius: Float,color: Int,stroke: Float = 0f) {
        paint.shader = null; paint.color = color; paint.alpha = Color.alpha(color)
        paint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL; paint.strokeWidth = stroke
        c.drawRoundRect(x,y,x+w,y+h,radius,radius,paint)
    }
    private fun svg(c: Canvas,kind: String,x: Float,y: Float,size: Float,color: Int) {
        c.save(); c.translate(x,y)
        val base = if (kind == "star") 12f else if (kind == "map") 16f else 18f
        c.scale(size/base,size/base)
        val p = Path()
        fun segment(x: Float,y: Float,a: Float,b: Float) { p.moveTo(x,y); p.lineTo(a,b) }
        when (kind) {
            "search" -> { p.addCircle(7.5f,7.5f,5.5f,Path.Direction.CW); segment(11.6f,11.6f,16f,16f) }
            "filter" -> {
                segment(2f,5f,11f,5f); segment(15f,5f,16f,5f); segment(2f,13f,3f,13f); segment(7f,13f,16f,13f)
                p.addCircle(13f,5f,2f,Path.Direction.CW); p.addCircle(5f,13f,2f,Path.Direction.CW)
            }
            "map" -> {
                p.moveTo(1.5f,3.5f); p.lineTo(5.5f,2f); p.lineTo(10.5f,4f); p.lineTo(14.5f,2.5f)
                p.lineTo(14.5f,12.5f); p.lineTo(10.5f,14f); p.lineTo(5.5f,12f); p.lineTo(1.5f,13.5f); p.close()
                segment(5.5f,2f,5.5f,12f); segment(10.5f,4f,10.5f,14f)
            }
            else -> {
                p.moveTo(6f,0.8f); p.lineTo(7.6f,4.1f); p.lineTo(11.2f,4.6f); p.lineTo(8.6f,7.1f)
                p.lineTo(9.2f,10.7f); p.lineTo(6f,9f); p.lineTo(2.8f,10.7f); p.lineTo(3.4f,7.1f)
                p.lineTo(0.8f,4.6f); p.lineTo(4.4f,4.1f); p.close()
            }
        }
        paint.shader = null; paint.color = color; paint.alpha = 255
        paint.style = if (kind == "star") Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = if (kind == "map") 1.5f else if (kind == "filter") 1.8f else 2f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        c.drawPath(p,paint); c.restore()
    }
    private fun mixedLabel(near: Double): Int {
        fun channel(a: Double,b: Double) = ((a/255).pow(2.2)*(1-near)+(b/255).pow(2.2)*near).pow(1/2.2).times(255).roundToInt().coerceIn(0,255)
        return Color.rgb(channel(142.0,244.0),channel(140.0,243.0),channel(153.0,247.0))
    }
    fun draw(c: Canvas,l: ClayCategoryTabsTheme.Layout,height: Float,bottom: Float,density: Float,
        f: ClayCategoryTabsMotion.Frame,time: Double,reduced: Boolean) {
        c.save(); c.scale(density,density)
        val w = l.w; val xs = l.xs; val k = f.r; val kc = k.coerceIn(0.0,2.0); val frac = kc-floor(kc)
        c.drawColor(page)
        round(c,16f,l.searchTop,w-88,56f,28f,card)
        round(c,16.5f,l.searchTop+0.5f,w-89,55f,27.5f,line,1f)
        svg(c,"search",36f,l.searchTop+19,18f,ink)
        text(c,"Where to?",68f,l.searchTop+10,15f,20f,ink,2)
        text(c,"Anywhere · Any week · Add guests",68f,l.searchTop+30,12f,16f,mute)
        round(c,w-59.5f,l.searchTop+6.5f,43f,43f,21.5f,line,1f)
        svg(c,"filter",w-47,l.searchTop+19,18f,ink)
        if (Build.VERSION.SDK_INT >= 33) shader?.draw(c,l,f,time,density,reduced)
        else {
            c.save(); c.translate(0f,l.stageTop)
            repeat(3) { i ->
                fallback(c,xs[i],108f,24f,i,true,ClayCategoryTabsMotion.smooth(0.15,0.85,abs(k-i)).toFloat())
            }
            val hop = if (reduced) 0f else 30f*sin(PI*frac).toFloat()
            fallback(c,w/2-l.slot+k.toFloat()*l.slot,108f-hop,76f*(1-0.08f*sin(PI*frac).toFloat()),floor(kc+0.5).toInt(),false,1f)
            c.restore()
        }
        repeat(3) { i -> text(c,ClayCategoryTabsTheme.categories[i].name,xs[i],l.stageTop+202,14f,20f,mixedLabel(max(0.0,1-abs(k-i))),2,align=Paint.Align.CENTER) }
        round(c,0f,l.stageTop+244-1/density,w,1/density,0f,line)
        val stretch = 1+0.9f*sin(PI*(k-floor(k)).coerceIn(0.0,1.0)).toFloat()
        val bx = w/2-l.slot+k.toFloat()*l.slot
        round(c,bx-20*stretch,l.stageTop+241,40*stretch,3f,1.5f,ink)
        c.save(); c.clipRect(0f,l.pagerTop,w,height)
        ClayCategoryTabsTheme.categories.forEachIndexed { i,category ->
            val px = i*w-k.toFloat()*w
            if (px >= -w && px <= w) {
                text(c,category.heading,px+16,l.pagerTop+20,20f,26f,ink,2)
                category.listings.forEachIndexed { j,item ->
                    val x = px+16; val y = l.pagerTop+58+j*106
                    round(c,x,y,w-32,94f,20f,card)
                    val priceW = font(18f,2).measureText(item.price)
                    val unitW = font(12f,0).measureText(item.unit)
                    val mainW = w-64-12-max(priceW,unitW)
                    text(c,item.title,x+16,y+14,16f,22f,ink,2,mainW)
                    text(c,item.meta,x+16,y+38,13f,18f,mute,width=mainW)
                    text(c,item.time,x+16,y+62,13f,18f,ink,1)
                    val whenW = font(13f,1).measureText(item.time)
                    val dotW = font(13f,0).measureText("·")
                    text(c,"·",x+22+whenW,y+62,13f,18f,mute)
                    val sx = x+28+whenW+dotW
                    svg(c,"star",sx,y+65,12f,star)
                    text(c,item.rating,sx+18,y+62,13f,18f,ink,1)
                    text(c,item.price,x+w-48,y+27,18f,24f,ink,2,align=Paint.Align.RIGHT)
                    text(c,item.unit,x+w-48,y+51,12f,16f,mute,align=Paint.Align.RIGHT)
                }
            }
        }
        c.restore()
        val mapW = font(15f,2).measureText("Map"); val mw = mapW+64; val my = height-max(bottom,16f)-56
        paint.setShadowLayer(20f,0f,6f,Color.argb(115,0,0,0))
        round(c,(w-mw)/2,my,mw,48f,24f,ink); paint.clearShadowLayer()
        text(c,"Map",(w-mw)/2+20,my+14,15f,20f,page,2)
        svg(c,"map",(w-mw)/2+28+mapW,my+16,16f,page)
        if (f.finger > 0) {
            val x = if (f.onPage == 1.0) w*(0.78-0.42*f.dragX).toFloat() else xs[f.tab]
            val y = if (f.onPage == 1.0) l.pagerTop+150 else l.stageTop+212
            c.save(); c.translate(x,y)
            val s = (1-0.12*f.press).toFloat(); c.scale(s,s)
            // Layer opacity composites the fill, stroke and shadow together, like the RN view.
            val alpha = (255*0.85*f.finger).roundToInt().coerceIn(0,255)
            val layer = c.saveLayerAlpha(RectF(-35f,-35f,35f,35f),alpha)
            paint.setShadowLayer(6f,0f,1f,Color.argb(51,0,0,0))
            round(c,-22f,-22f,44f,44f,22f,Color.argb(102,255,255,255)); paint.clearShadowLayer()
            round(c,-21.25f,-21.25f,42.5f,42.5f,21.25f,Color.argb(204,255,255,255),1.5f)
            c.restoreToCount(layer); c.restore()
        }
        c.restore()
    }
    // API 26–32: same silhouettes, positions, colors and hop; no ray-march or intermediate surface.
    private fun fallback(c: Canvas,x: Float,y: Float,s: Float,category: Int,ghost: Boolean,alpha: Float) {
        if (alpha <= 0) return
        c.save(); c.translate(x,y); c.scale(s,s)
        fun color(rgb: Int) = (if (ghost) 0xFF99969E.toInt() else rgb) and 0x00FFFFFF or ((255*alpha).roundToInt().coerceIn(0,255) shl 24)
        when (category) {
            0 -> {
                round(c,-0.6f,-0.1f,1.2f,0.88f,0.1f,color(0xFFFFE6C7.toInt()))
                val roof = Path().apply { moveTo(-0.78f,-0.08f); lineTo(0f,-0.76f); lineTo(0.78f,-0.08f); close() }
                paint.style = Paint.Style.FILL; paint.color = color(0xFFF55C47.toInt()); c.drawPath(roof,paint)
                round(c,-0.15f,0.28f,0.3f,0.5f,0.05f,color(0xFF29999E.toInt()))
                round(c,-0.47f,0.15f,0.22f,0.22f,0.04f,color(0xFFFFD166.toInt()))
                round(c,0.25f,0.15f,0.22f,0.22f,0.04f,color(0xFFFFD166.toInt()))
            }
            1 -> {
                paint.style = Paint.Style.FILL; paint.color = color(0xFFFF6B33.toInt()); c.drawOval(-0.74f,-0.96f,0.74f,0.6f,paint)
                paint.color = color(0xFFFFD64D.toInt()); c.drawOval(-0.3f,-0.96f,0.3f,0.6f,paint)
                paint.color = color(0xFF4D3829.toInt()); paint.strokeWidth = 0.03f
                c.drawLine(-0.2f,0.45f,-0.15f,0.76f,paint); c.drawLine(0.2f,0.45f,0.15f,0.76f,paint)
                round(c,-0.19f,0.73f,0.38f,0.26f,0.05f,color(0xFFA86B38.toInt()))
            }
            else -> {
                round(c,-0.86f,0.58f,1.72f,0.16f,0.05f,color(0xFF573324.toInt()))
                paint.style = Paint.Style.FILL; paint.color = color(0xFFFFBD47.toInt())
                c.drawArc(-0.7f,-0.2f,0.7f,1.32f,180f,180f,true,paint)
                c.drawCircle(0f,-0.4f,0.12f,paint)
            }
        }
        c.restore()
    }
}
