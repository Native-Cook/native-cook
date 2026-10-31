package com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder

import android.content.Context
import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder.GlassPlaylistFolderMotion as M

internal class GlassPlaylistFolderMatrix(val v: DoubleArray = doubleArrayOf(1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0)) {
    operator fun times(b: GlassPlaylistFolderMatrix): GlassPlaylistFolderMatrix {
        val r = DoubleArray(16)
        for (i in 0..3) for (j in 0..3) for (k in 0..3) r[i*4+j] += v[i*4+k]*b.v[k*4+j]
        return GlassPlaylistFolderMatrix(r)
    }
    fun native() = Matrix().apply { setValues(floatArrayOf(v[0].toFloat(),v[1].toFloat(),v[3].toFloat(),v[4].toFloat(),v[5].toFloat(),v[7].toFloat(),v[12].toFloat(),v[13].toFloat(),v[15].toFloat())) }
    companion object {
        fun translate(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0) = GlassPlaylistFolderMatrix().apply { v[3]=x;v[7]=y;v[11]=z }
        fun scale(s: Double) = GlassPlaylistFolderMatrix().apply { v[0]=s;v[5]=s }
        fun rotate(axis: Int, deg: Double): GlassPlaylistFolderMatrix {
            val a=deg*PI/180; val c=cos(a);val s=sin(a)
            return GlassPlaylistFolderMatrix().apply {
                when(axis) {
                    0 -> { v[5]=c;v[6]=-s;v[9]=s;v[10]=c }
                    1 -> { v[0]=c;v[2]=s;v[8]=-s;v[10]=c }
                    2 -> { v[0]=c;v[1]=-s;v[4]=s;v[5]=c }
                }
            }
        }
        fun base(cx: Double, cy: Double) = translate(cx,cy) * GlassPlaylistFolderMatrix().apply { v[14]=-1.0/760 } * rotate(1,-20.0) * rotate(0,12.0) * scale(1.14)
    }
}

internal class GlassPlaylistFolderArt(context: Context) {
    private val backdrop = if (android.os.Build.VERSION.SDK_INT >= 31) GlassPlaylistFolderBackdrop() else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rasterScale = context.resources.displayMetrics.density
    private fun raster(draw: (Canvas) -> Unit): Bitmap {
        val pixels = ceil(400 * rasterScale).toInt()
        return Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(rasterScale, rasterScale)
            canvas.translate(200f, 200f)
            draw(canvas)
        }
    }
    private val atlasRect = RectF(-200f, -200f, 200f, 200f)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(115,0,0,0);maskFilter=BlurMaskFilter(8f,BlurMaskFilter.Blur.NORMAL) }
    private val backPath = Path().apply { addRoundRect(RectF(-128f,-84f,128f,100f),20f,20f,Path.Direction.CW);addRoundRect(RectF(-128f,-102f,-28f,-62f),14f,14f,Path.Direction.CW) }
    private val flapRect = RectF(-134f,-38f,134f,102f)
    private val cursorPath = Path().apply { moveTo(0f,0f);lineTo(56f,26f);quadTo(60f,28f,56f,31f);lineTo(31f,38f);lineTo(22f,62f);quadTo(19f,66f,17f,61f);close() }
    private val backGradient = linear(0f,-102f,0f,100f,intArrayOf(rgb(0x2f9a4a),rgb(0x16602b),rgb(0x0b3a19)))
    private val tintGradient = linear(-134f,-38f,26.8f,102f,intArrayOf(rgb(0x6eeb87,.42),rgb(0x28aa50,.5),rgb(0x146e32,.62)))
    private val rimGradient = linear(0f,-38f,0f,102f,intArrayOf(rgb(0xdcffe1,.75),rgb(0x78e696,.25),rgb(0xa0ffb4,.5)))
    private val bandGradient = linear(-134f,0f,-64f,120f,intArrayOf(rgb(0xffffff,0.0),rgb(0xe6ffeb,.28),rgb(0xffffff,0.0)))
    private val bandMatrix = Matrix()
    private var glowCenter = Pair(Double.NaN,Double.NaN)
    private var glowGradient: Shader? = null
    private val arcs = (0..2).map { val r=4f+it*4;RectF(-104-r,66-r,-104+r,66+r) }
    private val cursor = raster { c ->
        reset();paint.color=rgb(0x3dff7a,.4);paint.maskFilter=BlurMaskFilter(14f,BlurMaskFilter.Blur.NORMAL);c.drawPath(cursorPath,paint)
        reset();paint.shader=linear(0f,0f,50f,60f,intArrayOf(rgb(0xb6ffc8),rgb(0x43d96c),rgb(0x1b8d3c)));c.drawPath(cursorPath,paint)
        reset();paint.style=Paint.Style.STROKE;paint.strokeWidth=1.4f;paint.color=rgb(0xe6ffec,.7);c.drawPath(cursorPath,paint)
    }
    private val coverClip = Path().apply {
        addRoundRect(RectF(-52f,-66f,52f,66f),9f,9f,Path.Direction.CW)
    }
    private val coverSheenRect = RectF(-52f,-66f,52f,4f)
    private val coverSheen = linear(0f,-70f,0f,0f,
        intArrayOf(Color.argb(56,255,255,255),rgb(0xffffff,0.0)))
    private val coverFrame = raster { c ->
        c.drawRoundRect(RectF(-54f,-64f,58f,76f),12f,12f,shadow)
        reset();paint.color=Color.rgb(13,15,16)
        c.drawRoundRect(RectF(-56f,-70f,56f,70f),12f,12f,paint)
    }
    private val photos = (1..4).map { i ->
        context.assets.open("glassplaylistfolder/glassplaylistfolder_cover-$i.webp").use {
            BitmapFactory.decodeStream(it)
        }
    }
    private val photoRects = photos.map {
        val width = 132f * it.width / it.height
        RectF(-width/2,-66f,width/2,66f)
    }
    private val fan = arrayOf(doubleArrayOf(-54.0,-70.0,-12.0,-18.0),doubleArrayOf(-18.0,-24.0,-4.0,-6.0),doubleArrayOf(18.0,24.0,4.0,7.0),doubleArrayOf(52.0,70.0,11.0,19.0))
    private fun reset() { paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true }
    private fun linear(x0: Float,y0: Float,x1: Float,y1: Float,colors: IntArray) = LinearGradient(x0,y0,x1,y1,colors,null,Shader.TileMode.CLAMP)
    private fun rgb(hex: Int, a: Double = 1.0) = Color.argb((a*255).roundToInt(),(hex shr 16)and 255,(hex shr 8)and 255,hex and 255)
    fun draw(destination: Canvas, width: Double, height: Double, top: Double, d: M.Driver) {
        val cx=width/2; val cy=top+318;val s=d.s;val t=d.t
        val frost = if (android.os.Build.VERSION.SDK_INT >= 31 && destination.isHardwareAccelerated) backdrop else null
        val c = if (android.os.Build.VERSION.SDK_INT >= 31 && frost != null) frost.begin(ceil(width).toInt(),ceil(height).toInt()) else destination
        c.drawColor(rgb(0x050806))
        if (glowCenter.first != cx || glowCenter.second != cy) {
            glowCenter=Pair(cx,cy)
            glowGradient=RadialGradient(cx.toFloat(),(cy-120).toFloat(),320f,intArrayOf(rgb(0x28aa46,.62),rgb(0x145a28,.25),rgb(0x05140a,0.0)),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
        }
        reset();paint.shader=glowGradient;paint.alpha=((.75+.25*M.clamp(s.open))*255).roundToInt()
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        val b=GlassPlaylistFolderMatrix.base(cx,cy)
        c.save();c.concat((b*GlassPlaylistFolderMatrix.translate(z=-16.0)).native())
        reset();paint.alpha=235;paint.shader=backGradient;c.drawPath(backPath,paint)
        reset();paint.style=Paint.Style.STROKE;paint.strokeWidth=1.4f;paint.color=rgb(0xaaffbe,.35);c.drawPath(backPath,paint);c.restore()
        repeat(4) { i ->
            val k=s.covers[i];val f=fan[i];val lift=if(s.hovered==i)s.lift else 0.0
            val bob=sin(2*PI*2*t/8+i*1.3)*2.5*M.clamp(k)
            val m=b*GlassPlaylistFolderMatrix.translate(z=-9.0+i*2+lift*40)*GlassPlaylistFolderMatrix.translate(M.mix(f[0],f[1],k),M.mix(-32.0,-116.0,k)+bob-lift*16)*GlassPlaylistFolderMatrix.rotate(2,M.mix(f[2],f[3],k))*GlassPlaylistFolderMatrix.scale(1+lift*.06)
            c.save();c.concat(m.native())
            reset();c.drawBitmap(coverFrame,null,atlasRect,paint)
            c.save();c.clipPath(coverClip)
            c.drawBitmap(photos[i],null,photoRects[i],paint);c.restore()
            reset();paint.shader=coverSheen;c.drawRoundRect(coverSheenRect,9f,9f,paint)
            c.restore()
        }
        val flapMatrix=b*GlassPlaylistFolderMatrix.translate(z=8.0)*GlassPlaylistFolderMatrix.translate(y=102.0)*GlassPlaylistFolderMatrix.rotate(0,-s.open*50)*GlassPlaylistFolderMatrix.translate(y=-102.0)
        if (android.os.Build.VERSION.SDK_INT >= 31 && frost != null) frost.finish(destination,flapMatrix)
        drawFront(destination,flapMatrix,cx,cy,d)
    }
    private fun drawFront(c: Canvas, flapMatrix: GlassPlaylistFolderMatrix, cx: Double, cy: Double, d: M.Driver) {
        val s=d.s;val t=d.t
        c.save();c.concat(flapMatrix.native())
        reset();paint.shader=tintGradient;c.drawRoundRect(flapRect,22f,22f,paint)
        val by=M.mix(-78.0,102.0,M.clamp(s.open)).toFloat()
        bandMatrix.setTranslate(0f,by);bandGradient.setLocalMatrix(bandMatrix);paint.shader=bandGradient;c.drawRoundRect(flapRect,22f,22f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.6f;paint.shader=rimGradient;c.drawRoundRect(flapRect,22f,22f,paint)
        reset();paint.color=rgb(0x0f3b1d);c.drawCircle(-100f,66f,15f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2.4f;paint.strokeCap=Paint.Cap.ROUND;paint.color=rgb(0x7df59b)
        arcs.forEach { c.drawArc(it,-50f,100f,false,paint) };c.restore()
        var x=M.mix(cx+112,cx+40,M.ease(M.seg(t,1.1,2.0)));var y=M.mix(cy+196,cy+50,M.ease(M.seg(t,1.1,2.0)))
        val hover=M.ease(M.seg(t,2.9,3.4));x=M.mix(x,cx-4,hover);y=M.mix(y,cy-140,hover)
        val leave=M.ease(M.seg(t,5.0,5.7));x=M.mix(x,cx+150,leave);y=M.mix(y,cy+90,leave)
        val home=M.ease(M.seg(t,5.7,6.8));x=M.mix(x,cx+112,home);y=M.mix(y,cy+196,home)
        val press=if(t>=1.92&&t<2.22)sin(M.seg(t,1.92,2.22)*PI) else 0.0
        if(d.drag<0) {
            c.save();c.translate(x.toFloat(),y.toFloat());val scale=(1-press*.16).toFloat();c.scale(scale,scale);c.rotate(-8f)
            reset();c.drawBitmap(cursor,null,atlasRect,paint);c.restore()
        }
        val rp=M.seg(t,2.0,2.45)
        if(rp>0&&rp<1) { reset();paint.style=Paint.Style.STROKE;paint.strokeWidth=1.6f;paint.color=rgb(0x8dffb0,.7*(1-rp));c.drawCircle((cx+40).toFloat(),(cy+50).toFloat(),(8+rp*34).toFloat(),paint) }
    }
}
