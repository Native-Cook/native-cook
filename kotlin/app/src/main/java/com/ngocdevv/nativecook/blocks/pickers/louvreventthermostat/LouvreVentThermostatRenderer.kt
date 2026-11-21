package com.ngocdevv.nativecook.blocks.pickers.louvreventthermostat

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class LouvreVentThermostatRenderer(context:Context) {
    private val m=LouvreVentThermostatMotion
    private val root="louvreventthermostat/louvreventthermostat_"
    private val mono=Typeface.createFromAsset(context.assets,root+"MartianMono_Regular.ttf")
    private val number=Typeface.createFromAsset(context.assets,root+"BigShouldersDisplay_Black.ttf")
    private val unit=Typeface.createFromAsset(context.assets,root+"BigShouldersDisplay_ExtraBold.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var gpu=if(Build.VERSION.SDK_INT>=33) {
        try { Shaders() } catch(error: RuntimeException) {
            Log.w("LouvreVentThermostat", "Using the Canvas steel renderer", error)
            null
        }
    } else null
    fun draw(c:Canvas,s:LouvreVentThermostatMotion.Scene,reduced:Boolean,x:Float,y:Float,w:Float,h:Float) {
        val values=LouvreVentThermostatUniforms.values(s,reduced)
        val shader=gpu
        if(Build.VERSION.SDK_INT>=33&&shader!=null) {
            try { shader.draw(c,values,x,y,w,h,paint) } catch(error: RuntimeException) {
                gpu=null
                Log.w("LouvreVentThermostat", "Using the Canvas steel renderer", error)
                fallback(c,s,values,x,y,w,h)
            }
        } else fallback(c,s,values,x,y,w,h)
        paint.shader=null; paint.color=0xFFE0453A.toInt(); paint.alpha=140; paint.maskFilter=BlurMaskFilter(4f,BlurMaskFilter.Blur.NORMAL)
        c.drawRect(69f,485.5f,332f,490.5f,paint); paint.maskFilter=null; paint.alpha=255
        c.drawRoundRect(68f,486.75f,332f,489.25f,1.25f,1.25f,paint)
        val tri=Path(); tri.moveTo((62-s.nudge).toFloat(),484f); tri.lineTo((69-s.nudge).toFloat(),488f); tri.lineTo((62-s.nudge).toFloat(),492f); tri.close(); c.drawPath(tri,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=1f; paint.strokeJoin=Paint.Join.ROUND; c.drawPath(tri,paint); paint.style=Paint.Style.FILL
        val save=c.save(); c.clipRect(333f,252f,393f,724f)
        for(value in 16..32 step 2) {
            val baseline=488-(value-s.temperature)*41.6+4.7; val d=abs(baseline-4.7-488); val k=m.smooth(60.0,200.0,d); val red=1-m.smooth(3.0,12.0,d)
            val near=doubleArrayOf(.718,.663,.655); val far=doubleArrayOf(.29,.235,.227); val hot=doubleArrayOf(.878,.271,.227)
            val col=DoubleArray(3){m.mix(m.mix(near[it],far[it],k),hot[it],red)}
            text(c,"$value°",337f,baseline.toFloat(),13f,mono,color(col))
        }
        c.restoreToCount(save)
        paint.typeface=number; paint.textSize=69f
        val bounds=Rect(); val widths=FloatArray(10){ paint.getTextBounds(it.toString(),0,1,bounds); bounds.width().toFloat() }; paint.getTextBounds(".",0,1,bounds)
        val cell=widths.max()+2; val dot=bounds.width().toFloat(); val total=3*cell+dot-2; val x0=201-total/2
        val index=floor(s.s0-s.k0+.5).toInt().coerceIn(0,25); val open=abs(sin(s.blades[2*index]*PI/180)); val intensity=values.getValue("uI")[0]/(1+.25*abs(tanh(s.velocity/260)))
        val a=min(.5,intensity*(.25+.6*open)*.6); val heat=values.getValue("uHeat"); val ink=doubleArrayOf(.984,.965,.949)
        val lit=DoubleArray(3){m.mix(ink[it],heat[it].toDouble(),a)}
        val gradient=LinearGradient(0f,123f,0f,178.2f,intArrayOf(0xFFFBF6F2.toInt(),0xFFFBF6F2.toInt(),color(lit)),floatArrayOf(0f,.58f,1f),Shader.TileMode.CLAMP)
        drum(c,s.drums[2],(0..9).map(Int::toString),widths,x0,cell,gradient)
        drum(c,s.drums[1],(0..9).map(Int::toString),widths,x0+cell,cell,gradient)
        paint.shader=gradient; paint.typeface=number; paint.textSize=69f; c.drawText(".",x0+2*cell-1,178.2f,paint)
        drum(c,s.drums[0],listOf("0","5"),floatArrayOf(widths[0],widths[5]),x0+2*cell+dot-2,cell,gradient)
        text(c,"°C",x0+total+7,142.2f,24f,unit,0xFF5A4A48.toInt())
        paint.typeface=mono; paint.textSize=13f; val modeWidth=paint.measureText("Celsius"); val modeX=201-(modeWidth+18)/2
        text(c,"Celsius",modeX,214.7f,13f,mono,0xFF8A7C7A.toInt()); dots(c,modeX,220.5f,58f,0xFF8A7C7A.toInt())
        paint.color=0xFF8A7C7A.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.25f; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        val arrow=Path(); arrow.moveTo(modeX+modeWidth+6.4f,206.1f); arrow.lineTo(modeX+modeWidth+10.5f,210.2f); arrow.lineTo(modeX+modeWidth+14.6f,206.1f); c.drawPath(arrow,paint); paint.style=Paint.Style.FILL
        footer(c)
    }
    private fun color(rgb:DoubleArray)=Color.rgb((rgb[0]*255).roundToInt().coerceIn(0,255),(rgb[1]*255).roundToInt().coerceIn(0,255),(rgb[2]*255).roundToInt().coerceIn(0,255))
    private fun text(c:Canvas,label:String,x:Float,baseline:Float,size:Float,font:Typeface,color:Int) {
        paint.shader=null; paint.typeface=font; paint.textSize=size; paint.color=color; paint.alpha=255; paint.textAlign=Paint.Align.LEFT; c.drawText(label,x,baseline,paint)
    }
    private fun drum(c:Canvas,pos:Double,glyphs:List<String>,widths:FloatArray,x:Float,cell:Float,gradient:Shader) {
        val n=glyphs.size; val index=((floor(pos).toInt()%n)+n)%n; val f=(pos-floor(pos)).toFloat(); val save=c.save(); c.clipRect(x,121.5f,x+cell,179.7f)
        paint.typeface=number; paint.textSize=69f; paint.color=Color.WHITE; paint.shader=gradient; paint.textAlign=Paint.Align.LEFT
        c.drawText(glyphs[index],x+(cell-widths[index])/2,178.2f+f*65.2f,paint)
        val next=(index+1)%n; c.drawText(glyphs[next],x+(cell-widths[next])/2,178.2f+f*65.2f-65.2f,paint); paint.shader=null; c.restoreToCount(save)
    }
    private fun dots(c:Canvas,x:Float,y:Float,width:Float,color:Int) {
        paint.color=color; paint.alpha=153; paint.style=Paint.Style.STROKE; paint.strokeWidth=1f; paint.pathEffect=DashPathEffect(floatArrayOf(1f,2f),0f)
        c.drawLine(x+.5f,y,x+width,y,paint); paint.pathEffect=null; paint.alpha=255; paint.style=Paint.Style.FILL
    }
    private fun footer(c:Canvas) {
        val labels=listOf("67%","Schedule","22°C"); paint.typeface=mono; paint.textSize=13f
        val widths=labels.map { 24+paint.measureText(it) }; val gap=(314-widths.sum())/2; var x=44f
        for(i in labels.indices) {
            paint.color=0xFFF5EDE4.toInt(); paint.style=Paint.Style.FILL
            when(i) {
                0->{ val p=Path(); p.moveTo(x+8,780.55f); p.cubicTo(x+7.4f,780.55f,x+3.55f,785.75f,x+3.55f,788.5f); p.cubicTo(x+3.55f,794.43f,x+12.45f,794.43f,x+12.45f,788.5f); p.cubicTo(x+12.45f,785.75f,x+8.6f,780.55f,x+8,780.55f); c.drawPath(p,paint) }
                1->{ paint.style=Paint.Style.STROKE; paint.strokeWidth=1.25f; c.drawCircle(x+8,787f,6.2f,paint); val p=Path(); p.moveTo(x+8,783.5f); p.lineTo(x+8,787f); p.lineTo(x+10.35f,788.6f); c.drawPath(p,paint); paint.style=Paint.Style.FILL }
                else->{ c.drawRoundRect(x+6.5f,780.6f,x+9.5f,789.2f,1.5f,1.5f,paint); c.drawCircle(x+8,789.6f,3.6f,paint) }
            }
            text(c,labels[i],x+24,791.7f,13f,mono,0xFFF5EDE4.toInt()); if(i==1)dots(c,x+24,797.5f,66f,0xFFF5EDE4.toInt()); x+=widths[i]+gap
        }
    }
    private fun fallback(c:Canvas,s:LouvreVentThermostatMotion.Scene,u:Map<String,FloatArray>,x:Float,y:Float,w:Float,h:Float) {
        paint.color=0xFF0D0101.toInt(); c.drawRect(x,y,x+w,y+h,paint)
        val heat=u.getValue("uHeat"); val glow=Color.rgb((heat[0]*255).toInt(),(heat[1]*255).toInt(),(heat[2]*255).toInt())
        paint.shader=RadialGradient(150f,888f,u.getValue("uGlowH")[0],intArrayOf(glow,Color.TRANSPARENT),null,Shader.TileMode.CLAMP); c.drawRect(x,y,x+w,y+h,paint); paint.shader=null
        paint.color=0xFF3A201B.toInt(); c.drawRoundRect(78f,246f,324f,730f,26f,26f,paint)
        val save=c.save(); val clip=Path(); clip.addRoundRect(86f,254f,316f,722f,18f,18f,Path.Direction.CW); c.clipPath(clip); paint.color=glow; c.drawRect(86f,254f,316f,722f,paint)
        for(i in 0..<26) {
            val center=(488-(s.k0+i-s.s0)*20.8).toFloat(); val height=max(2.0,19*abs(cos(s.blades[i*2]*PI/180))).toFloat()
            paint.shader=LinearGradient(0f,center-height/2,0f,center+height/2,intArrayOf(0xFF8A8A8A.toInt(),0xFF2A2A2A.toInt(),0xFF161616.toInt()),floatArrayOf(0f,.2f,1f),Shader.TileMode.CLAMP); c.drawRect(92f,center-height/2,310f,center+height/2,paint)
        }
        paint.shader=null; c.restoreToCount(save)
    }
    @RequiresApi(33)
    private class Shaders {
        private val page=RuntimeShader(LouvreVentThermostatShaders.PAGE)
        private val vent=RuntimeShader(LouvreVentThermostatShaders.VENT)
        fun draw(c:Canvas,values:Map<String,FloatArray>,x:Float,y:Float,w:Float,h:Float,paint:Paint) {
            for(name in LouvreVentThermostatUniforms.PAGE_FIELDS)page.setFloatUniform(name,values.getValue(name))
            for(name in LouvreVentThermostatUniforms.VENT_FIELDS)vent.setFloatUniform(name,values.getValue(name))
            paint.color=Color.WHITE; paint.alpha=255; paint.shader=page; c.drawRect(x,y,x+w,y+h,paint)
            paint.shader=vent; c.drawRect(76f,244f,326f,732f,paint); paint.shader=null
        }
    }
}
