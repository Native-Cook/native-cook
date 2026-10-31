package com.ngocdevv.nativecook.blocks.lists.clayrelightsettings

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

internal class ClayRelightSettingsDrawing(
    private val c: Canvas, private val fonts: List<Typeface>, private val density: Float,
    private val shaders: ClayRelightSettingsRenderer?, private val fontScale: Float,
) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private fun tone(key: String, f: ClayRelightSettingsMotion.Frame) = ClayRelightSettingsTheme.tone(key,f.m,f.theme)
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, colour: Color) {
        p.shader = null; p.color = colour.toArgb(); p.style = Paint.Style.FILL
        c.drawRoundRect(x,y,x+w,y+h,r,r,p)
    }
    private fun label(value: String, x: Float, cy: Float, size: Float, font: Int, colour: Color,
                      alpha: Double = 1.0, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float? = null,
                      tracking: Float = 0f, tabular: Boolean = false) {
        if (alpha <= 0) return
        text.typeface = fonts[font]; text.textSize = size*(if (tabular) 1f else fontScale); text.color = colour.toArgb()
        text.alpha = (255*alpha.coerceIn(0.0,1.0)*colour.alpha).roundToInt()
        text.textAlign = align
        text.fontFeatureSettings = if (tabular) "tnum" else null
        val content = if (maxWidth == null) value else TextUtils.ellipsize(value,text,maxWidth,TextUtils.TruncateAt.END).toString()
        val baseline = cy-(text.fontMetrics.ascent+text.fontMetrics.descent)/2
        if (tracking == 0f) c.drawText(content,x,baseline,text)
        else {
            var cursor = x
            for (ch in content) {
                c.drawText(ch.toString(),cursor,baseline,text)
                cursor += text.measureText(ch.toString())+tracking
            }
        }
    }
    fun draw(width: Float, height: Float, top: Float, f: ClayRelightSettingsMotion.Frame, drift: Double, reduced: Boolean) {
        val cardW = width-32
        val titleTop = top+8
        val heroTop = titleTop+60
        val sectionTop = heroTop+332
        val listTop = sectionTop+26
        val phase = (2*PI*drift/10).toFloat()
        rect(0f,0f,width,height,0f,tone("page",f))
        label("Settings",20f,titleTop+22,34f,2,tone("ink",f),tracking = -0.6f)
        rect(16f,heroTop,cardW,308f,28f,tone("card",f))
        c.save()
        val clip = Path().apply { addRoundRect(RectF(16f,heroTop,width-16,heroTop+308),28f,28f,Path.Direction.CW) }
        c.clipPath(clip)
        if (shaders != null) shaders.draw(c,16f,heroTop,cardW,232f,density,f,phase,false)
        else fallbackHero(16f,heroTop,cardW,f)
        text.typeface = fonts[2]; text.textSize = 13f; text.fontFeatureSettings = "tnum"
        val time = ClayRelightSettingsMotion.clockText(f.m)
        val pillW = max(60f,text.measureText(time))+20
        rect(28f,heroTop+12,pillW,28f,14f,tone("pill",f))
        label(time,28f+pillW/2,heroTop+26,13f,2,tone("pillInk",f),align = Paint.Align.CENTER,tabular = true)
        label("Dark mode",36f,heroTop+260,16f,1,tone("ink",f))
        val k = ClayRelightSettingsMotion.smooth(0.56,0.66,f.m)
        label("Off · sunset at 6:50 PM",36f,heroTop+282-6*k.toFloat(),13f,0,tone("mute",f),1-ClayRelightSettingsMotion.smooth(0.0,0.6,k))
        label("On until sunrise, 6:20 AM",36f,heroTop+282+6*(1-k).toFloat(),13f,0,tone("mute",f),ClayRelightSettingsMotion.smooth(0.4,1.0,k))
        val sx = width-16-20-51
        c.save()
        val trackScale = if (reduced) 1f else (1-0.04*f.press).toFloat()
        c.scale(trackScale,trackScale,sx+25.5f,heroTop+270)
        rect(sx,heroTop+254.5f,51f,31f,15.5f,ClayRelightSettingsTheme.track(tone("trackOff",f),f.knob))
        val knobX = sx+2+20*f.knob.toFloat()
        c.save()
        c.scale((1+0.12*f.press).toFloat(),1f,knobX+13.5f,heroTop+270)
        p.color = android.graphics.Color.WHITE; p.setShadowLayer(3f,0f,2f,0x2E000000)
        c.drawCircle(knobX+13.5f,heroTop+270,13.5f,p)
        p.clearShadowLayer()
        c.restore(); c.restore(); c.restore()
        label("GENERAL",32f,sectionTop+9,13f,1,tone("mute",f),tracking = 0.6f)
        rect(16f,listTop,cardW,300f,28f,tone("card",f))
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(16f,listTop,width-16,listTop+300),28f,28f,Path.Direction.CW) })
        if (shaders != null) shaders.draw(c,16f,listTop,64f,300f,density,f,phase,true)
        else fallbackIcons(16f,listTop,f)
        ClayRelightSettingsTheme.rows.forEachIndexed { i,row ->
            val y = listTop+i*60
            text.typeface = fonts[0]; text.textSize = 15f*fontScale; text.fontFeatureSettings = null
            val longValue = if (row.second.length > row.third.length) row.second else row.third
            val valueW = if (row.second.isEmpty()) 0f else text.measureText(longValue)
            val chevronX = width-16-16-8
            val valueRight = chevronX-8
            label(row.first,84f,y+30,16f,1,tone("ink",f),
                maxWidth = cardW-68-16-8-(if (valueW > 0) valueW+8 else 0f)-12)
            val lamp = ClayRelightSettingsMotion.lamp(f.m,i)
            label(row.second,valueRight,y+30-5*lamp.toFloat(),15f,0,tone("mute",f),
                1-ClayRelightSettingsMotion.smooth(0.0,0.6,lamp),Paint.Align.RIGHT)
            label(row.third,valueRight,y+30+5*(1-lamp).toFloat(),15f,0,tone("mute",f),
                ClayRelightSettingsMotion.smooth(0.4,1.0,lamp),Paint.Align.RIGHT)
            p.color = 0xFF9AA0AE.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f
            p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            c.drawPath(Path().apply {
                moveTo(chevronX+1.5f,y+25); lineTo(chevronX+6.5f,y+30); lineTo(chevronX+1.5f,y+35)
            },p)
            p.style = Paint.Style.FILL
            if (i < 4) rect(84f,y+60-1/density,cardW-68,1/density,0f,tone("line",f))
        }
        c.restore()
        val fx = if (f.fingerAt == 1.0) 16+cardW*(0.78-0.4*f.fingerX) else 16+cardW-16-25.5
        val fy = if (f.fingerAt == 1.0) heroTop+232*0.36 else heroTop+270.0
        c.save()
        c.scale((1-0.12*f.press).toFloat(),(1-0.12*f.press).toFloat(),fx.toFloat(),fy.toFloat())
        val alpha = 0.85*f.finger
        p.color = Color(1f,1f,1f,(0.4*alpha).toFloat()).toArgb()
        p.setShadowLayer(3f,0f,1f,Color(0f,0f,0f,(0.2*alpha).toFloat()).toArgb())
        c.drawCircle(fx.toFloat(),fy.toFloat(),22f,p); p.clearShadowLayer()
        p.color = Color(1f,1f,1f,(0.8*alpha).toFloat()).toArgb(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f
        c.drawCircle(fx.toFloat(),fy.toFloat(),21.25f,p)
        p.style = Paint.Style.FILL
        c.restore()
    }
    // API 26–32: a readable flat scene in the same palette; no RuntimeShader class is instantiated.
    private fun fallbackHero(x: Float, y: Float, w: Float, f: ClayRelightSettingsMotion.Frame) {
        val n = ClayRelightSettingsMotion.night(f.m).toFloat()
        rect(x,y,w,232f,0f,Color(0.47f*(1-n)+0.035f*n,0.67f*(1-n)+0.05f*n,0.93f*(1-n)+0.13f*n))
        p.color = Color(0.4f*(1-n)+0.08f*n,0.66f*(1-n)+0.16f*n,0.55f*(1-n)+0.19f*n).toArgb()
        c.drawOval(x-60,y+92,x+w+70,y+300,p)
        rect(x+w*0.53f,y+155,w*0.19f,53f,3f,Color(0xFFF7E3C2))
        p.color = Color(0xFFDE6B54).toArgb()
        c.drawPath(Path().apply {
            moveTo(x+w*0.49f,y+158); lineTo(x+w*0.625f,y+120); lineTo(x+w*0.76f,y+158); close()
        },p)
        for (j in 0..1) {
            val tx = x+w*(if (j == 0) 0.22f else 0.9f)
            rect(tx-3,y+173,6f,37f,0f,Color(0xFF8C6142))
            p.color = Color(0xFF5CA85C).toArgb(); c.drawCircle(tx,y+166,if (j == 0) 26f else 18f,p)
        }
        val a = if (n > 0.5) ClayRelightSettingsMotion.moonAngle(f.m) else ClayRelightSettingsMotion.sunAngle(f.m)
        p.color = if (n > 0.5) Color(0xFFEDEDF7).toArgb() else Color(0xFFFFED9E).toArgb()
        c.drawCircle(x+w/2+(cos(a)*w*0.38).toFloat(),y+95-(sin(a)*66).toFloat(),14f,p)
        val win = ClayRelightSettingsMotion.windows(f.m)
        val wc = if (win > 0.5) Color(0xFFFFCC73) else Color(0xFF6B8FB3)
        rect(x+w*0.55f,y+170,10f,12f,0f,wc); rect(x+w*0.66f,y+170,10f,12f,0f,wc)
        rect(x+w*0.61f,y+184,9f,24f,0f,Color(0xFF8C593D))
    }
    private fun fallbackIcons(x: Float, y: Float, f: ClayRelightSettingsMotion.Frame) {
        val colours = listOf(0xFFF56E5E,0xFF7366ED,0xFF4591FA,0xFF45C473,0xFF6B7A99)
        for (i in 0..4) {
            val cx = x+32; val cy = y+i*60+30
            rect(cx-20,cy-20,40f,40f,5f,Color(colours[i]))
            p.color = if (ClayRelightSettingsMotion.lamp(f.m,i) > 0.5) Color(0xFFFFE68C).toArgb() else android.graphics.Color.WHITE
            when (i) {
                0 -> { c.drawOval(cx-7,cy-10,cx+7,cy+8,p); c.drawRect(cx-10,cy+5,cx+10,cy+9,p); c.drawCircle(cx,cy+12,2f,p) }
                1 -> { c.drawCircle(cx,cy,12f,p); p.color = Color(colours[i]).toArgb(); c.drawCircle(cx+6,cy-5,11f,p) }
                2 -> { c.drawCircle(cx,cy-4,8f,p); c.drawRect(cx-4,cy+2,cx+4,cy+12,p) }
                3 -> { c.drawRoundRect(cx-13,cy-7,cx+11,cy+7,3f,3f,p); c.drawRect(cx+11,cy-3,cx+14,cy+3,p) }
                else -> { p.style = Paint.Style.STROKE; p.strokeWidth = 3f; c.drawCircle(cx,cy-6,6f,p); p.style = Paint.Style.FILL; c.drawRoundRect(cx-10,cy-4,cx+10,cy+12,3f,3f,p) }
            }
        }
    }
}
