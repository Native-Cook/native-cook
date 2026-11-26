package com.ngocdevv.nativecook.blocks.buttons.turbineboostbutton

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.floor

internal fun turbineBoostButtonPhoto(context: Context): Bitmap = context.assets.open("turbineboostbutton/turbineboostbutton_fan.webp").use {
    BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {
        inScaled=false; inPreferredConfig=Bitmap.Config.ARGB_8888; inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
    })!!
}

@RequiresApi(33)
internal class TurbineBoostButtonRoom(context: Context): View(context) {
    private val shader=RuntimeShader(TURBINE_BOOST_BUTTON_AGSL)
    private val paint=Paint().apply { shader=this@TurbineBoostButtonRoom.shader }
    private var frame=TurbineBoostButtonMotion.frame(0.0)
    init {
        val image=BitmapShader(turbineBoostButtonPhoto(context),Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
        image.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        // Input buffer bypasses automatic colour conversion; lin() linearises the intended sRGB samples once.
        shader.setInputBuffer("uFan",image)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun show(f: TurbineBoostButtonMotion.Frame) { frame=f; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val d=resources.displayMetrics.density; val w=width/d; val h=height/d
        shader.setFloatUniform("uA",frame.angle.toFloat()); shader.setFloatUniform("uArc",frame.arc.toFloat()); shader.setFloatUniform("uB",frame.boost.toFloat())
        shader.setFloatUniform("uOff",(w-393)/2,252.1f*(h/852-1))
        canvas.save(); canvas.scale(d,d); canvas.drawRect(0f,0f,w,h,paint); canvas.restore()
    }
}

internal class TurbineBoostButtonChrome(context: Context): View(context) {
    private val archivo=font("ArchivoExpanded-Black")
    private val display=font("InterDisplay-Bold")
    private val medium=font("Inter-Medium")
    private val semibold=font("Inter-SemiBold")
    private fun font(name: String)=Typeface.createFromAsset(context.assets,"turbineboostbutton/turbineboostbutton_$name.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private var frame=TurbineBoostButtonMotion.frame(0.0)
    private data class Line(val text: String,val positions: FloatArray,val width: Float)
    private val lines=mutableMapOf<String,Line>()
    // Android's BlurMaskFilter takes radius, then maps it to sigma = radius*0.57735 + 0.5.
    private fun blur(sigma: Float): BlurMaskFilter? = if(sigma<=.5f) null else BlurMaskFilter((sigma-.5f)/.57735f,BlurMaskFilter.Blur.NORMAL)
    init { setLayerType(LAYER_TYPE_SOFTWARE,null); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun show(f: TurbineBoostButtonMotion.Frame) { frame=f; invalidate() }
    private fun text(canvas: Canvas,s: String,font: Typeface,size: Float,tracking: Float,cx: Float,y: Float,color: Int) {
        paint.typeface=font; paint.textSize=size; paint.color=color
        val key="$s|$font|$size|$tracking"
        val line=lines.getOrPut(key) {
            val widths=FloatArray(s.length); paint.getTextWidths(s,widths); var x=0f
            val positions=FloatArray(s.length) { i -> val at=x; x+=widths[i]+tracking; at }
            Line(s,positions,x)
        }
        s.forEachIndexed { i,c -> canvas.drawText(c.toString(),cx-line.width/2+line.positions[i],y,paint) }
    }
    override fun onDraw(canvas: Canvas) {
        val d=resources.displayMetrics.density; val w=width/d; val sy=height/d/852; val cx=w/2; val f=frame; val b=f.boost.toFloat()
        canvas.save(); canvas.scale(d,d)
        text(canvas,"VOLT · PERFORMANCE MODE",semibold,12f,4f,cx,92*sy,0xFF6F9BFF.toInt())
        if(b>.002f) {
            paint.maskFilter=blur(15*b)
            text(canvas,"BOOST",archivo,66f,-4f,cx,166*sy,Color.argb((.9*b*255).toInt(),61,123,255)); paint.maskFilter=null
        }
        text(canvas,"BOOST",archivo,66f,-4f,cx,166*sy,Color.WHITE)
        text(canvas,if(b>.98f) "Full boost. Let go to coast." else "Hold to spool up. Let go to coast.",medium,15f,0f,cx,204*sy,0xFF8EA6D6.toInt())
        text(canvas,"${floor(100*f.share+.5).toInt()}%",display,40f,-1f,cx,726*sy,0xFFCFE0FF.toInt())
        canvas.translate(cx,793*sy); if(f.held) canvas.scale(.97f,.97f)
        paint.style=Paint.Style.FILL; paint.color=if(f.held) 0xFF1A3BA8.toInt() else 0xFF0E1A36.toInt()
        canvas.drawRoundRect(-164.5f,-29f,164.5f,29f,29f,29f,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=1f; paint.color=if(f.held) 0xFF5C8DFF.toInt() else 0xFF2B4A8F.toInt()
        canvas.drawRoundRect(-164.5f,-29f,164.5f,29f,29f,29f,paint); paint.style=Paint.Style.FILL
        paint.color=0xFF3D7BFF.toInt(); paint.maskFilter=blur(6+10*b)
        canvas.drawCircle(-58f,0f,5f,paint); paint.maskFilter=null; canvas.drawCircle(-58f,0f,5f,paint)
        text(canvas,if(f.held) "Boosting…" else "Hold to boost",semibold,17f,0f,8f,6f,0xFFE4EDFF.toInt()); canvas.restore()
    }
}
