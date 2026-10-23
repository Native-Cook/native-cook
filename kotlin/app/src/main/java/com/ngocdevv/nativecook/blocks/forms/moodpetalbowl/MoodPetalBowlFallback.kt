package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import android.graphics.*
import kotlin.math.*

/** API 26–32: bounded, reusable half-resolution premultiplied aggregate Gaussian. */
internal class MoodPetalBowlFallback(private val painter: MoodPetalBowlPainter) {
    private val w=188;private val h=173;private val ratio=w/375f
    private val raw=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    private val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    private val canvas=Canvas(raw)
    private val pixels=IntArray(w*h)
    private val source=FloatArray(w*h*4);private val scratch=FloatArray(w*h*4);private val blurred=FloatArray(w*h*4)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination=RectF(0f,0f,375f,345f)
    private val radius=ceil(3*8.5*ratio).toInt()
    private val kernel=FloatArray(radius*2+1) {i -> exp(-.5*((i-radius)/(8.5*ratio)).pow(2)).toFloat()}.also {a -> val sum=a.sum();for(i in a.indices) a[i]/=sum}
    private val sharpWeights=FloatArray(h) {y -> stripAlpha(painter.resources.sharpStrip,y)}
    private val softWeights=FloatArray(h) {y -> stripAlpha(painter.resources.softStrip,y)}
    private var lastScene: MoodPetalBowlScene?=null
    private fun stripAlpha(bitmap: Bitmap,y: Int): Float {
        val row=(((y+.5)/ratio/345)*bitmap.height-.5).coerceIn(0.0,(bitmap.height-1).toDouble())
        val lo=floor(row).toInt();val hi=min(bitmap.height-1,lo+1);val q=(row-lo).toFloat()
        return (Color.alpha(bitmap.getPixel(0,lo))*(1-q)+Color.alpha(bitmap.getPixel(0,hi))*q)/255
    }
    fun draw(c: Canvas,scene: MoodPetalBowlScene) {
        if(scene!=lastScene) {render(scene);lastScene=scene}
        paint.alpha=255;paint.shader=null;paint.colorFilter=null;paint.xfermode=null;c.drawBitmap(output,null,destination,paint)
    }
    private fun render(scene: MoodPetalBowlScene) {
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);canvas.save();canvas.scale(ratio,ratio);painter.pile(canvas,scene);canvas.restore()
        raw.getPixels(pixels,0,w,0,0,w,h)
        for(i in pixels.indices) {val p=pixels[i];val a=Color.alpha(p)/255f;val at=i*4
            source[at]=Color.red(p)/255f*a;source[at+1]=Color.green(p)/255f*a;source[at+2]=Color.blue(p)/255f*a;source[at+3]=a}
        for(y in 0..<h) for(x in 0..<w) for(channel in 0..3) {var total=0f
            for(k in -radius..radius) {val sx=x+k;if(sx in 0..<w) total+=source[(y*w+sx)*4+channel]*kernel[k+radius]}
            scratch[(y*w+x)*4+channel]=total}
        for(y in 0..<h) for(x in 0..<w) for(channel in 0..3) {var total=0f
            for(k in -radius..radius) {val sy=y+k;if(sy in 0..<h) total+=scratch[(sy*w+x)*4+channel]*kernel[k+radius]}
            blurred[(y*w+x)*4+channel]=total}
        for(y in 0..<h) for(x in 0..<w) {
            val gx=(x+.5)/ratio+9;val gy=(y+.5)/ratio+111
            val disk=hypot(gx-196.5,gy-273)<=153
            val sharpWeight=if(gy<273||disk) sharpWeights[y] else 0f
            val softWeight=if(disk) softWeights[y]*.86f else 0f
            val displaced=x-1.6*((gy-273)/153).coerceIn(0.0,1.0)*ratio
            val lo=floor(displaced).toInt();val hi=lo+1;val q=(displaced-lo).toFloat();val at=(y*w+x)*4
            fun channel(k: Int): Float {val a=if(lo in 0..<w) blurred[(y*w+lo)*4+k] else 0f;val b=if(hi in 0..<w) blurred[(y*w+hi)*4+k] else 0f;return a+(b-a)*q}
            val r=channel(0);val g=channel(1);val b=channel(2);val a=channel(3)
            val luma=.213f*r+.715f*g+.072f*b
            val alpha=(source[at+3]*sharpWeight+a*softWeight).coerceIn(0f,1f)
            fun out(value: Float,k: Int): Int {val premul=source[at+k]*sharpWeight+(luma+(value-luma)*.95f)*softWeight
                return if(alpha<=0) 0 else (premul/alpha*255).roundToInt().coerceIn(0,255)}
            pixels[y*w+x]=Color.argb((alpha*255).roundToInt(),out(r,0),out(g,1),out(b,2))
        }
        output.setPixels(pixels,0,w,0,0,w,h)
    }
}
