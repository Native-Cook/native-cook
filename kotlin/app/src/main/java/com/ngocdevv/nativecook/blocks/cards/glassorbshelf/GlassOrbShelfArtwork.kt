package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import kotlin.math.ceil

internal class GlassOrbShelfArtwork(context: Context, private val l: GlassOrbShelfLayout, private val density: Float, private val fontScale: Float) {
    private val regular=Typeface.createFromAsset(context.assets,"glassorbshelf/glassorbshelf_Inter-Regular.ttf")
    private val semi=Typeface.createFromAsset(context.assets,"glassorbshelf/glassorbshelf_Inter-SemiBold.ttf")
    private val bold=Typeface.createFromAsset(context.assets,"glassorbshelf/glassorbshelf_Inter-Bold.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cards=GlassOrbShelfTheme.apps.map(::card)
    private fun textPaint(size: Float,color: Int,font: Typeface,spacing: Float=0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=size; this.color=color; typeface=font; letterSpacing=spacing/size
    }
    private fun layout(text: CharSequence,p: TextPaint,width: Float,lineHeight: Float,maxLines: Int=3): StaticLayout {
        val fm=p.fontMetrics
        return StaticLayout.Builder.obtain(text,0,text.length,p,width.toInt()).setIncludePad(false)
            .setLineSpacing(lineHeight-(fm.descent-fm.ascent),1f).setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END).build()
    }
    private fun card(app: GlassOrbShelfTheme.App): Bitmap {
        val bitmap=Bitmap.createBitmap(ceil(l.cardW*density).toInt(),ceil(196*density).toInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap); c.scale(density,density)
        paint.shader=null; paint.alpha=255; paint.color=GlassOrbShelfTheme.CARD; paint.style=Paint.Style.FILL
        val r=RectF(0.5f,0.5f,l.cardW-0.5f,195.5f)
        c.drawRoundRect(r,26f,26f,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=1f; paint.color=0x0FFFFFFF
        c.drawRoundRect(r,26f,26f,paint); paint.style=Paint.Style.FILL
        val titleScale=minOf(fontScale,1.2f); val bodyScale=minOf(fontScale,1.25f)
        val titleLine=26*titleScale; val bodyLine=20*bodyScale
        val title=SpannableString("${app.first} ${app.rest}")
        title.setSpan(ForegroundColorSpan(app.tint),0,app.first.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val titleLayout=layout(title,textPaint(22f*titleScale,GlassOrbShelfTheme.INK,semi,-0.45f*titleScale),l.cardW-42,titleLine,2)
        c.save(); c.translate(21f,21f); titleLayout.draw(c); c.restore()
        val titleHeight=ceil(titleLayout.height/titleLine.toDouble()).toFloat()*titleLine
        c.save(); c.translate(21f,21+titleHeight+8)
        layout(app.body,textPaint(15f*bodyScale,GlassOrbShelfTheme.BODY,regular),l.cardW-42,bodyLine).draw(c); c.restore()
        paint.color=GlassOrbShelfTheme.INK
        val open=textPaint(15f*fontScale,GlassOrbShelfTheme.PAGE,semi)
        val pillWidth=open.measureText("Open")+36
        c.drawRoundRect(RectF(l.cardW-21-pillWidth,139f,l.cardW-21,175f),18f,18f,paint)
        c.drawText("Open",l.cardW-39-open.measureText("Open"),157-(open.fontMetrics.ascent+open.fontMetrics.descent)/2,open)
        val meta=textPaint(13f*fontScale,GlassOrbShelfTheme.MUTE,regular)
        c.drawText("Mini app · Free",21f,157-(meta.fontMetrics.ascent+meta.fontMetrics.descent)/2,meta)
        return bitmap
    }
    fun draw(c: Canvas,o: Double) {
        val title=textPaint(28f*minOf(fontScale,1.3f),GlassOrbShelfTheme.INK,bold,-0.6f*minOf(fontScale,1.3f))
        c.drawText("Featured",24f,l.headerTop-title.fontMetrics.ascent,title)
        cards.forEachIndexed { i,bitmap ->
            val d=(i-o).toFloat(); val k=kotlin.math.min(1f,kotlin.math.abs(d))
            c.save(); c.translate(l.w/2+d*l.step,l.cardTop+98); c.scale(1-0.06f*k,1-0.06f*k)
            paint.shader=null; paint.alpha=((1-0.45f*k)*255).toInt()
            c.drawBitmap(bitmap,null,RectF(-l.cardW/2,-98f,l.cardW/2,98f),paint); c.restore()
        }
        val widths=List(4) { 6+12*kotlin.math.max(0.0,1-kotlin.math.abs(it-o)).toFloat() }
        var x=(l.w-widths.sum()-18)/2
        widths.forEachIndexed { i,w ->
            val k=kotlin.math.max(0.0,1-kotlin.math.abs(i-o))
            paint.shader=null; paint.color=GlassOrbShelfTheme.INK; paint.alpha=((0.35+0.65*k)*255).toInt()
            c.drawRoundRect(RectF(x,l.dotsTop,x+w,l.dotsTop+6),3f,3f,paint); x+=w+6
        }
        paint.alpha=255
    }
}
