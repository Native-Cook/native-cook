package com.ngocdevv.nativecook.blocks.pickers.velvetnapstudio

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class VelvetNapStudioPainter(private val resource: VelvetNapStudioResources) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val type=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path=Path()
    private val fonts=mutableMapOf<Int,Typeface>()
    private val data=Bitmap.createBitmap(64,192,Bitmap.Config.ARGB_8888).apply{setPremultiplied(false)}
    private val dataPixels=IntArray(64*192)
    private var prepared: List<VelvetNapStudioField> = emptyList()
    private val gpu=if(Build.VERSION.SDK_INT>=33) VelvetNapStudioGpu(resource,data) else null
    private val weights=FloatArray(4)
    private val vertices=FloatArray(64*64*2).also {for(i in 0 until 4096) {it[i*2]=(i%64)*307f/63;it[i*2+1]=(i/64)*274f/63}}
    private val baseColors=IntArray(4096)
    private val darkColors=IntArray(4096)
    private val fallbackPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD)}
    private val colorMatrix=FloatArray(20)
    private val white=Color.WHITE

    /** Call on input events, never on a frame callback. One reused raw numerical bitmap. */
    fun prepare(driver: VelvetNapStudioDriver) {
        val s=driver.session?:driver.event?.session
        val fields=if(s!=null)s.from.fields+listOfNotNull(s.target) else driver.control?.frame?.fields?:emptyList()
        val unique=fields.distinct().take(3)
        if(unique.size==prepared.size&&unique.indices.all{unique[it]===prepared[it]}) return
        prepared=unique
        for(row in 0..2) {val bytes=unique.getOrNull(row)?.bytes?:VelvetNapStudioField.EMPTY.bytes
            for(i in 0 until 4096) dataPixels[row*4096+i]=((bytes[i*4+3].toInt()and 255)shl 24) or ((bytes[i*4].toInt()and 255)shl 16) or ((bytes[i*4+1].toInt()and 255)shl 8) or (bytes[i*4+2].toInt()and 255)
        }
        data.setPixels(dataPixels,0,64,0,0,64,192)
    }
    fun draw(c: Canvas,frame: VelvetNapStudioFrame,tint: VelvetNapStudioTint,driver: VelvetNapStudioDriver,now: Double,reduced: Boolean,height: Float) {
        c.drawColor(0xFFFAFBF7.toInt())
        art(c,frame,tint,43f,121f,1f)
        for(i in 0..7) art(c,VelvetNapStudioFrame.REST,VelvetNapStudioDriver.paletteTint(i),33f+43*i,411.29f,24f/307)
        paint.shader=null;paint.colorFilter=null;paint.xfermode=null;paint.alpha=255;paint.color=0xFFBDC8AB.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
        c.drawCircle((45+43*driver.ring(now,reduced)).toFloat(),422f,20f,paint)
        icon(c,"back",38f,77f,18f)
        icon(c,"bag",355f,77f,18f)
        text(c,"Material studio",196.5f,79f,13f,500,centerY=true,align=Paint.Align.CENTER)
        text(c,"Crushed velvet",24f,463f,18f,600)
        text(c,"${VelvetNapStudioDriver.names[driver.selected]} No. ${String.format(java.util.Locale.US,"%02d",driver.selected+1)}",24f,485f,18f,600)
        icon(c,if(driver.liked)"heartFill" else "heart",351f,479f,18f)
        text(c,"$8.40 / yd",24f,516f,18f,600)
        text(c,"4.8 (126 reviews)",369f,531f,10f,400,centerY=true,align=Paint.Align.RIGHT)
        icon(c,"star",278f,531f,8f)
        capsule(c,24f,559f,167f,38f,0xF0F2EB)
        text(c,"Color",41f,578f,10f,400,centerY=true)
        paint.style=Paint.Style.FILL;paint.color=0xFF000000.toInt() or VelvetNapStudioDriver.colors[driver.selected]
        c.drawOval(89f,571f,121f,585f,paint);icon(c,"down",170.5f,578f,8f)
        capsule(c,202f,559f,167f,38f,0xF0F2EB)
        text(c,"Yards",219f,578f,11f,400,centerY=true);text(c,driver.yards.toString(),331f,578f,12f,400,centerY=true,align=Paint.Align.RIGHT);icon(c,"down",348.5f,578f,8f)
        text(c,"Description",24f,617f,13f,600)
        text(c,"A soft crushed pile with a quiet sheen.",24f,647f,12f,400,0x787E72)
        text(c,"Brush both ways to feel the difference.",24f,663f,12f,400,0x787E72)
        val colors=intArrayOf(0xD6D8CA,0xBFC4B1,0xE0DECC,0xC9CAB9)
        for(i in 0..3) {paint.style=Paint.Style.FILL;paint.color=0xFF000000.toInt() or colors[i];c.drawCircle(31f+14*i,707f,11.5f,paint);icon(c,arrayOf("leaf","spark","grid","cut")[i],31f+14*i,707f,9f,0x626A58)}
        text(c,"Made for slow projects",96f,707f,11f,400,0x787E72,centerY=true)
        capsule(c,24f,755f,99f,52f,0xF0F2EB);icon(c,"minus",46f,781f,12f);icon(c,"plus",101f,781f,12f)
        text(c,driver.yards.toString(),73.5f,781f,12f,500,centerY=true,align=Paint.Align.CENTER)
        capsule(c,135f,755f,234f,52f,0xAFCC4F)
        val added=driver.confirmation(now);val label=if(added)"Sample added" else "Add to samples"
        font(14f,600);val width=type.measureText(label);val left=252f-(width+25)/2
        icon(c,if(added)"check" else "bag",left+8,781f,16f,0x25310F)
        text(c,label,left+25,781f,14f,600,0x25310F,centerY=true)
        // Background already fills the actual Canvas; authored content remains in its852pt coordinates.
        @Suppress("UNUSED_VARIABLE") val logicalHeight=height
    }
    private fun art(c: Canvas,frame: VelvetNapStudioFrame,tint: VelvetNapStudioTint,x: Float,y: Float,scale: Float) {
        c.save();c.translate(x,y);c.scale(scale,scale)
        if(scale<1)c.clipRect(0f,0f,307f,274f)
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.alpha=255
        c.drawBitmap(resource.shadow,null,RectF(-24f,-24f,331f,298f),paint)
        if(gpu!=null) {
            weights.fill(0f);weights[0]=frame.authorWeight.toFloat()
            for(i in frame.fields.indices) {val at=prepared.indexOfFirst{it===frame.fields[i]};if(at>=0)weights[at+1]+=frame.weights[i].toFloat()}
            gpu.draw(c,frame,tint,weights)
        } else fallback(c,frame,tint)
        c.restore()
    }
    private fun fallback(c: Canvas,f: VelvetNapStudioFrame,tint: VelvetNapStudioTint) {
        val resting=f.fields.isEmpty()&&(f.time<=1||f.time>=7.55||f.authorWeight==0.0||f.strength==0.0)
        colorMatrix.fill(0f);colorMatrix[0]=tint.r.toFloat();colorMatrix[6]=tint.g.toFloat();colorMatrix[12]=tint.b.toFloat();colorMatrix[18]=1f
        paint.colorFilter=ColorMatrixColorFilter(colorMatrix);paint.alpha=255;paint.style=Paint.Style.FILL
        if(resting) {c.drawBitmap(resource.atlas,null,RectF(0f,0f,307f,274f),paint);paint.colorFilter=null;return}
        for(i in 0 until 4096) {
            val ratio=VelvetNapStudioMath.value(f,i).coerceIn(.53,1.55);val dark=((1-ratio)/.42).coerceIn(0.0,1.0);val component=(ratio/1.55*255).roundToInt().coerceIn(0,255)
            val rgb=(component shl 16)or(component shl 8)or component
            baseColors[i]=(((1-dark)*255).roundToInt()shl 24)or rgb;darkColors[i]=((dark*255).roundToInt()shl 24)or rgb
        }
        colorMatrix[0]*=1.55f;colorMatrix[6]*=1.55f;colorMatrix[12]*=1.55f
        fallbackPaint.color=white;fallbackPaint.alpha=255;fallbackPaint.colorFilter=ColorMatrixColorFilter(colorMatrix)
        c.saveLayer(0f,0f,307f,274f,null)
        c.drawBitmapMesh(resource.atlas,63,63,vertices,0,baseColors,0,fallbackPaint)
        c.drawBitmapMesh(resource.contrastAtlas!!,63,63,vertices,0,darkColors,0,fallbackPaint)
        c.restore();fallbackPaint.colorFilter=null;paint.colorFilter=null
    }
    private fun font(size: Float,weight: Int) {
        type.textSize=size;type.color=0xFF171C16.toInt();type.textAlign=Paint.Align.LEFT
        type.typeface=fonts.getOrPut(weight){if(Build.VERSION.SDK_INT>=28)Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),weight,false) else Typeface.create(if(weight>=500)"sans-serif-medium" else "sans-serif",if(weight>=600)Typeface.BOLD else Typeface.NORMAL)}
    }
    private fun text(c: Canvas,value: String,x: Float,y: Float,size: Float,weight: Int,color: Int=0x171C16,centerY: Boolean=false,align: Paint.Align=Paint.Align.LEFT) {
        font(size,weight);type.color=0xFF000000.toInt() or color;type.textAlign=align
        val baseline=if(centerY)y-(type.fontMetrics.ascent+type.fontMetrics.descent)/2 else y-type.fontMetrics.ascent
        c.drawText(value,x,baseline,type)
    }
    private fun capsule(c: Canvas,x: Float,y: Float,w: Float,h: Float,color: Int) {paint.reset();paint.isAntiAlias=true;paint.color=0xFF000000.toInt()or color;c.drawRoundRect(x,y,x+w,y+h,h/2,h/2,paint)}
    private fun icon(c: Canvas,name: String,x: Float,y: Float,size: Float,color: Int=0x171C16) {
        c.save();c.translate(x,y);c.scale(size/18,size/18);paint.reset();paint.isAntiAlias=true;paint.color=0xFF000000.toInt()or color;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.25f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;path.reset()
        when(name) {
            "back"->{path.moveTo(3f,-7f);path.lineTo(-3f,0f);path.lineTo(3f,7f)}
            "down"->{path.moveTo(-5f,-2f);path.lineTo(0f,3f);path.lineTo(5f,-2f)}
            "plus","minus"->{path.moveTo(-5f,0f);path.lineTo(5f,0f);if(name=="plus"){path.moveTo(0f,-5f);path.lineTo(0f,5f)}}
            "bag"->{path.addRoundRect(-6.5f,-4f,6.5f,8f,1.2f,1.2f,Path.Direction.CW);path.moveTo(-3.5f,-3f);path.cubicTo(-3.5f,-11f,3.5f,-11f,3.5f,-3f)}
            "check"->{path.moveTo(-6f,0f);path.lineTo(-1.5f,4.5f);path.lineTo(7f,-5f);paint.strokeWidth=1.8f}
            "heart","heartFill"->{path.moveTo(0f,7f);path.cubicTo(-14f,-2f,-7f,-12f,0f,-5f);path.cubicTo(7f,-12f,14f,-2f,0f,7f);if(name=="heartFill")paint.style=Paint.Style.FILL}
            "star","spark"->{for(i in 0 until 10){val a=-PI/2+i*PI/5;val r=if(i%2==0)8.0 else 3.3;val xx=(cos(a)*r).toFloat();val yy=(sin(a)*r).toFloat();if(i==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)};path.close();paint.style=Paint.Style.FILL}
            "grid"->{for(yy in listOf(-4f,4f))for(xx in listOf(-4f,4f))path.addCircle(xx,yy,2.4f,Path.Direction.CW)}
            "leaf"->{path.moveTo(-6f,6f);path.cubicTo(-9f,-5f,3f,-8f,7f,-7f);path.cubicTo(9f,4f,0f,8f,-6f,6f);path.moveTo(-6f,6f);path.lineTo(4f,-4f)}
            "cut"->{path.addCircle(-4f,4f,2.5f,Path.Direction.CW);path.addCircle(4f,4f,2.5f,Path.Direction.CW);path.moveTo(-3f,2f);path.lineTo(6f,-7f);path.moveTo(3f,2f);path.lineTo(-6f,-7f)}
        };c.drawPath(path,paint);c.restore()
    }
    fun close(){data.recycle()}
}

@RequiresApi(33)
private class VelvetNapStudioGpu(resource: VelvetNapStudioResources,data: Bitmap) {
    private val shader=RuntimeShader(VELVET_NAP_STUDIO_AGSL)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        shader.setInputShader("artwork",BitmapShader(resource.atlas,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply{setFilterMode(BitmapShader.FILTER_MODE_LINEAR)})
        shader.setInputBuffer("pathData",BitmapShader(resource.pathBuffer,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply{setFilterMode(BitmapShader.FILTER_MODE_NEAREST)})
        shader.setInputBuffer("fields",BitmapShader(data,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply{setFilterMode(BitmapShader.FILTER_MODE_NEAREST)})
        paint.shader=shader
    }
    fun draw(c: Canvas,f: VelvetNapStudioFrame,tint: VelvetNapStudioTint,weights: FloatArray) {
        shader.setFloatUniform("times",f.time.toFloat(),f.strength.toFloat(),VelvetNapStudioMath.pathLength.toFloat(),0f)
        shader.setFloatUniform("weights",weights);shader.setFloatUniform("tint",tint.r.toFloat(),tint.g.toFloat(),tint.b.toFloat())
        paint.alpha=255;c.drawRect(0f,0f,307f,274f,paint)
    }
}
