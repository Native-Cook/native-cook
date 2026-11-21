package com.ngocdevv.nativecook.blocks.animations.gearpuckspeedline

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Native shader renderer. API 26–32 keep a Canvas line/curtain and refracting-letter fallback. */
internal class GearPuckSpeedLineArt(context: Context) {
    private val num = Typeface.createFromAsset(context.assets,"gearpuckspeedline/gearpuckspeedline_Exo2Num_Light.ttf")
    private val letter = Typeface.createFromAsset(context.assets,"gearpuckspeedline/gearpuckspeedline_Exo2_Regular.ttf")
    private val unit = Typeface.createFromAsset(context.assets,"gearpuckspeedline/gearpuckspeedline_SairaCondensed_Medium.ttf")
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {isSubpixelText=true;isLinearText=true}
    private val path=Path()
    private val rect=RectF()
    private val scene=if(Build.VERSION.SDK_INT>=33)RuntimeShader(GearPuckSpeedLineShaders.SCENE) else null
    private val lens=if(Build.VERSION.SDK_INT>=33)RuntimeShader(GearPuckSpeedLineShaders.LENS) else null
    private val scenePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {shader=scene}
    private val lensPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {shader=lens}
    private val atlas:Bitmap=Bitmap.createBitmap(1206,2622,Bitmap.Config.ARGB_8888).also { bitmap ->
        val c=Canvas(bitmap);c.scale(3f,3f);drawLetters(c,Color.WHITE)
        stroke.color=0x73FFFFFF;stroke.strokeWidth=2f;c.drawRoundRect(RectF(24f,628f,378f,700f),36f,36f,stroke)
    }
    private val arc=FloatArray(64)
    private val unitWidth:Double get() {text.typeface=unit;text.textSize=13f;text.letterSpacingCompat();return measureUnit().toDouble()}
    init {
        if(Build.VERSION.SDK_INT>=33) {
            scene?.setFloatUniform("uP3",0f)
            lens?.setFloatUniform("uP3",0f)
            lens?.setInputShader("letters",BitmapShader(atlas,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply {setLocalMatrix(Matrix().apply {setScale(1f/3f,1f/3f)})})
        }
    }
    fun draw(canvas:Canvas,f:GearPuckSpeedLineFrame,h:Double) {
        val l=f.readout;val uw=unitWidth
        val parked=((1-l.scale)/(1-34.0/76)).coerceIn(0.0,1.0)*(1-f.drop)
        val ux=mix(l.cx-uw/2,22.0,parked)
        val top=l.base-53.428*l.scale;val unitTop=l.base+18*l.scale-8.97
        val below=smooth(318.0,336.0,top)
        val left=min(ux,mix(ux,l.cx-l.width*l.scale/2,below))
        val right=max(ux+uw,mix(ux+uw,l.cx+l.width*l.scale/2,below))
        val by=mix(unitTop,top,below)
        val box=floatArrayOf((left-4).toFloat(),(by-4).toFloat(),(right-left+8).toFloat(),(l.base+18*l.scale+6-by).toFloat())
        val sceneShader=scene
        if(Build.VERSION.SDK_INT>=33&&sceneShader!=null) {
            val wide=1+.3*abs(f.lean)
            sceneShader.setFloatUniform("uH",h.toFloat());sceneShader.setFloatUniform("uRest",330f)
            sceneShader.setFloatUniform("uHump",f.crest.toFloat(),(f.amplitude+f.jitter).toFloat(),(68*(if(f.lean>0)wide else 1.0)).toFloat(),(68*(if(f.lean<0)wide else 1.0)).toFloat())
            sceneShader.setFloatUniform("uPluck",f.pluckX.toFloat(),f.pluck.toFloat(),f.pluckLag.toFloat(),0f)
            sceneShader.setFloatUniform("uPhase",f.phase.toFloat())
            for(i in 0..2)sceneShader.setFloatUniform("uC$i",GearPuckSpeedLineFrame.BANDS[f.values[13+i].roundToInt().coerceIn(0,3)])
            sceneShader.setFloatUniform("uFront",f.frontPX.toFloat(),f.frontPR.toFloat(),f.frontLX.toFloat(),f.frontLR.toFloat())
            sceneShader.setFloatUniform("uPacket",f.packetX.toFloat(),f.packetGain.toFloat())
            sceneShader.setFloatUniform("uCurt",f.curtain.toFloat(),f.whip.toFloat(),f.time.toFloat(),f.sway.toFloat())
            sceneShader.setFloatUniform("uTrail",(-.08*f.speed).toFloat());sceneShader.setFloatUniform("uBox",box)
            sceneShader.setFloatUniform("uBright",mix(220.0,f.crest,(f.amplitude/96).coerceIn(0.0,1.0)).toFloat())
            arc[0]=0f;var acc=0.0;val dx=402.0/63
            for(i in 1..63){for(j in 0..3){val x=(i-1)*dx+(j+.5)*dx/4;val sl=f.curve(x+.5)-f.curve(x-.5);acc+=(sqrt(1+sl*sl)-1)*dx/4};arc[i]=acc.toFloat()}
            sceneShader.setFloatUniform("uArc",arc)
            canvas.drawRect(-40f,0f,442f,h.toFloat(),scenePaint)
        } else drawSceneFallback(canvas,f,h,box)
        stroke.shader=LinearGradient(0f,628f,0f,700f,intArrayOf(0x9EAAB0BA.toInt(),0x579AA0AA,0x809AA0AA.toInt()),null,Shader.TileMode.CLAMP)
        stroke.strokeWidth=2f;canvas.drawRoundRect(RectF(24f,628f,378f,700f),36f,36f,stroke);stroke.shader=null
        drawLetters(canvas,0xFFA7ABB3.toInt())
        val ballX=(69+66*f.g).toFloat();val line=f.colorAt(ballX.toDouble())
        val lensShader=lens
        if(Build.VERSION.SDK_INT>=33&&lensShader!=null) {
            lensShader.setFloatUniform("uC",ballX,664f);lensShader.setFloatUniform("uR",44f)
            lensShader.setFloatUniform("uRing",(1+.4*f.flash).toFloat());lensShader.setFloatUniform("uFlash",f.flash.toFloat())
            lensShader.setFloatUniform("uLine",line);lensShader.setFloatUniform("uRoll",((ballX-69)/44));lensShader.setFloatUniform("uPress",f.press.coerceIn(0.0,1.0).toFloat())
            canvas.drawRect(ballX-84,580f,ballX+84,748f,lensPaint)
        } else drawLensFallback(canvas,f,ballX,line)
        drawNumeral(canvas,f,ux)
    }
    private fun drawLetters(canvas:Canvas,color:Int) {
        text.typeface=letter;text.textSize=20f;text.color=color;text.textAlign=Paint.Align.CENTER
        listOf("P","R","D","S","M").forEachIndexed {i,s->canvas.drawText(s,69f+66f*i,670.9f,text)}
    }
    private fun drawNumeral(canvas:Canvas,f:GearPuckSpeedLineFrame,ux:Double) {
        val l=f.readout;val d=f.drums;val cells=l.width/47.12;val color=f.colorAt(l.cx)
        canvas.save();canvas.translate((l.cx-l.width*l.scale/2).toFloat(),l.base.toFloat());canvas.scale(l.scale.toFloat(),l.scale.toFloat())
        text.typeface=num;text.textSize=76f;text.textAlign=Paint.Align.LEFT
        for(i in 0..2) {
            val fl=floor(d[i]);val roll=d[i]-fl;val x=((cells-3+i)*47.12).toFloat();val digit=fl.toInt()%10
            fun digit(value:Int,y:Float,alpha:Double) {
                text.color=Color.argb((255*alpha).roundToInt().coerceIn(0,255),244,247,255)
                text.setShadowLayer(8f,0f,0f,Color.argb((128*alpha).roundToInt(),(color[0]*255).roundToInt(),(color[1]*255).roundToInt(),(color[2]*255).roundToInt()))
                canvas.drawText(value.toString(),x,y,text);text.clearShadowLayer()
            }
            if(!(i<2&&fl==0.0))digit(digit,(-roll*12).toFloat(),1-roll)
            if(roll>.001)digit((digit+1)%10,((1-roll)*12).toFloat(),roll)
        }
        canvas.restore()
        text.typeface=unit;text.textSize=13f;text.color=0xFF8B909A.toInt();text.textAlign=Paint.Align.LEFT
        var x=ux.toFloat();for(ch in "MP/H") {val c=ch.toString();canvas.drawText(c,x,(l.base+18*l.scale).toFloat(),text);x+=text.measureText(c)+1.04f}
    }
    private fun measureUnit():Float {var w=0f;for(ch in "MP/H")w+=text.measureText(ch.toString())+1.04f;return w-1.04f}
    private fun Paint.letterSpacingCompat() { /* Native glyph spacing is applied explicitly to match RN. */ }
    private fun drawSceneFallback(canvas:Canvas,f:GearPuckSpeedLineFrame,h:Double,box:FloatArray) {
        fill.shader=RadialGradient(f.crest.toFloat(),330f,350f,intArrayOf(0xFF0C1119.toInt(),0xFF070A10.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawRect(-40f,0f,442f,h.toFloat(),fill);fill.shader=null
        for(i in -8..76) {
            val x=i*6.0+3-(f.phase%6);val env=exp(-(x-f.crest).pow(2)/(2*110.0*110))
            val id=((i+floor(f.phase/6))%168+168)%168
            val hash=sin(id*127.1+11.7)*43758.5453
            val noise=hash-floor(hash)
            val length=(40+f.curtain*env)*(1+.18*(2*noise-1))
            var top=333.0
            if(x>=box[0]&&x<=box[0]+box[2]&&top<box[1]+box[3])top=max(top,(box[1]+box[3]+6).toDouble())
            val bottom=333+length;if(bottom<=top)continue
            val c=f.colorAt(x);val tint=Color.rgb((c[0]*255).roundToInt(),(c[1]*255).roundToInt(),(c[2]*255).roundToInt())
            stroke.shader=LinearGradient(x.toFloat(),top.toFloat(),x.toFloat(),bottom.toFloat(),intArrayOf((tint and 0x00FFFFFF) or 0x70000000,tint and 0x00FFFFFF),null,Shader.TileMode.CLAMP)
            stroke.strokeWidth=2f;canvas.drawLine(x.toFloat(),top.toFloat(),(x-.08*f.speed+f.sway).toFloat(),bottom.toFloat(),stroke)
        }
        stroke.shader=null
        for(i in 0 until 100) {
            val x=i*4.02;val x2=x+4.02;val c=f.colorAt(x);val tint=Color.rgb((c[0]*255).roundToInt(),(c[1]*255).roundToInt(),(c[2]*255).roundToInt())
            for(pass in 0..2) {stroke.color=(tint and 0x00FFFFFF) or ((if(pass==0)20 else if(pass==1)80 else 255) shl 24);stroke.strokeWidth=if(pass==0)24f else if(pass==1)8f else 3.5f;canvas.drawLine(x.toFloat(),f.curve(x).toFloat(),x2.toFloat(),f.curve(x2).toFloat(),stroke)}
        }
    }
    private fun drawLensFallback(canvas:Canvas,f:GearPuckSpeedLineFrame,x:Float,line:FloatArray) {
        stroke.color=Color.rgb((line[0]*255).roundToInt(),(line[1]*255).roundToInt(),(line[2]*255).roundToInt());stroke.strokeWidth=1.5f;canvas.drawCircle(x,664f,(52+20*f.flash).toFloat(),stroke)
        fill.shader=RadialGradient(x-13,646f,67f,intArrayOf(0xFFB12942.toInt(),0xFF3A060E.toInt(),0xFF12030A.toInt()),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(x,664f,44f,fill);fill.shader=null
        canvas.save();path.rewind();path.addCircle(x,664f,43f,Path.Direction.CW);canvas.clipPath(path);canvas.scale(2.2f,2.2f,x,664f);drawLetters(canvas,0xFFFFE9EC.toInt());canvas.restore()
        fill.shader=RadialGradient(x-15,645f,13f,intArrayOf(0xBFFFFFFF.toInt(),0x00FFFFFF),null,Shader.TileMode.CLAMP);canvas.drawOval(x-30,634f,x,655f,fill);fill.shader=null
    }
}
