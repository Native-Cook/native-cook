package com.ngocdevv.nativecook.blocks.onboarding.coincardonboarding

import android.graphics.*
import kotlin.math.*

internal class CoinCardOnboardingMatrix(private val a: DoubleArray = doubleArrayOf(1.0,0.0,0.0,0.0, 0.0,1.0,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0)) {
    fun times(b: CoinCardOnboardingMatrix): CoinCardOnboardingMatrix {
        val out = DoubleArray(16)
        for (r in 0..3) for (c in 0..3) for (k in 0..3) out[r * 4 + c] += a[r * 4 + k] * b.a[k * 4 + c]
        return CoinCardOnboardingMatrix(out)
    }
    fun projection() = Matrix().apply { setValues(floatArrayOf(a[0].toFloat(), a[1].toFloat(), a[3].toFloat(), a[4].toFloat(), a[5].toFloat(), a[7].toFloat(), a[12].toFloat(), a[13].toFloat(), a[15].toFloat())) }
    companion object {
        fun translate(x: Double, y: Double, z: Double = 0.0): CoinCardOnboardingMatrix {
            val m = CoinCardOnboardingMatrix(); m.a[3] = x; m.a[7] = y; m.a[11] = z; return m
        }
        fun perspective(d: Double): CoinCardOnboardingMatrix { val m = CoinCardOnboardingMatrix(); m.a[14] = -1 / d; return m }
        fun rotate(axis: Int, degrees: Double): CoinCardOnboardingMatrix {
            val t = degrees * PI / 180; val c = cos(t); val s = sin(t)
            return CoinCardOnboardingMatrix(when (axis) {
                0 -> doubleArrayOf(1.0,0.0,0.0,0.0, 0.0,c,-s,0.0, 0.0,s,c,0.0, 0.0,0.0,0.0,1.0)
                1 -> doubleArrayOf(c,0.0,s,0.0, 0.0,1.0,0.0,0.0, -s,0.0,c,0.0, 0.0,0.0,0.0,1.0)
                else -> doubleArrayOf(c,-s,0.0,0.0, s,c,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0)
            })
        }
    }
}

internal class CoinCardOnboardingArt(private val r: CoinCardOnboardingResources) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x8c787c88.toInt() }
    private val cardBox = RectF(0f, 0f, 252f, 160f)
    private val edgeDepths = intArrayOf(-1, -2, -3, -4, -5, -6, -7, -8)
    private val tops = arrayOf(doubleArrayOf(247.0,211.0,234.0), doubleArrayOf(207.0,240.0,226.0), doubleArrayOf(226.0,214.0,252.0))
    private val blooms = arrayOf(doubleArrayOf(244.0,170.0,214.0), doubleArrayOf(140.0,222.0,190.0), doubleArrayOf(190.0,165.0,250.0))
    fun draw(c: Canvas, width: Float, height: Float, top: Float, p: Double, reduced: Boolean) {
        val q = p.coerceIn(0.0, 2.0); val i = min(1, floor(q).toInt()); val k = CoinCardOnboardingMotion.smooth(q - i)
        fun color(values: Array<DoubleArray>, alpha: Int) = Color.argb(alpha, CoinCardOnboardingMotion.mix(values[i][0], values[i + 1][0], k).roundToInt(), CoinCardOnboardingMotion.mix(values[i][1], values[i + 1][1], k).roundToInt(), CoinCardOnboardingMotion.mix(values[i][2], values[i + 1][2], k).roundToInt())
        paint.alpha = 255
        paint.shader = LinearGradient(0f, 0f, 0f, height, intArrayOf(color(tops,255), 0xfffbf7fb.toInt(), Color.WHITE), floatArrayOf(0f,0.62f,1f), Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width,height,paint)
        paint.shader = RadialGradient(width/2, top+266, width*0.7f, intArrayOf(color(blooms,140), color(blooms,0)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width,height,paint)
        paint.shader = null
        c.save(); c.translate(0f,top); c.clipRect(0f,0f,width,520f)
        imagePaint.alpha = (CoinCardOnboardingMotion.mix(0.16,0.26,CoinCardOnboardingMotion.clamp(p-1))*255).roundToInt()
        c.drawBitmap(r.shadow,null,RectF(width/2-208,250f,width/2+208,452f),imagePaint)
        imagePaint.alpha = 255
        card(c,width/2.0,236.0,p,false)
        card(c,width/2.0,236.0,p,true)
        coin(c,width/2.0,236.0,p,false,reduced)
        coin(c,width/2.0,236.0,p,true,reduced)
        c.restore()
    }
    private fun card(c: Canvas, cx: Double, cy: Double, p: Double, front: Boolean) {
        val keys = if (front) CoinCardOnboardingMotion.front else CoinCardOnboardingMotion.back
        fun key(i: Int) = CoinCardOnboardingMotion.key(keys[i], p)
        val ry = key(3)
        val base = CoinCardOnboardingMatrix.translate(cx+key(0),cy+key(1)).times(CoinCardOnboardingMatrix.perspective(640.0)).times(CoinCardOnboardingMatrix.rotate(2,key(2))).times(CoinCardOnboardingMatrix.rotate(0,key(4))).times(CoinCardOnboardingMatrix.rotate(1,ry)).times(CoinCardOnboardingMatrix.translate(-126.0,-80.0))
        c.save(); c.concat(base.projection())
        c.drawBitmap(r.bodies[if (front) 1 else 0],null,cardBox,imagePaint)
        val k = CoinCardOnboardingMotion.clamp(0.5+ry/60)
        paint.alpha = 255
        paint.shader = LinearGradient((252*(k-0.6)).toFloat(),0f,(252*(k+0.4)).toFloat(),160f,intArrayOf(0x00ffffff,0x52ffffff,0x00ffffff),floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP)
        c.drawRoundRect(cardBox,18f,18f,paint)
        paint.shader = null
        c.drawBitmap(r.details[if (front) 1 else 0],null,cardBox,imagePaint)
        c.restore()
    }
    private fun coin(c: Canvas, cx: Double, cy: Double, p: Double, euro: Boolean, reduced: Boolean) {
        val keys = if (euro) CoinCardOnboardingMotion.euro else CoinCardOnboardingMotion.dollar
        fun key(i: Int) = CoinCardOnboardingMotion.key(keys[i],p)
        val radius = key(2).toFloat()
        val flip = if (reduced) 0.0 else key(3)
        val rx = if (euro) flip+14 else 0.0
        val ry = if (euro) -16.0 else flip+18
        val base = CoinCardOnboardingMatrix.translate(cx+key(0),cy+key(1)).times(CoinCardOnboardingMatrix.perspective(520.0)).times(CoinCardOnboardingMatrix.rotate(0,rx)).times(CoinCardOnboardingMatrix.rotate(1,ry))
        val facing = cos(rx*PI/180)*cos(ry*PI/180)>=0
        face(c,base,if (facing) -9 else 0,radius,if (euro) "€" else "$")
        // RN concatenates base and translateZ separately. Skia flattens each to 3x3,
        // so translateZ is identity; preserve that behavior rather than adding new thickness.
        for (z in edgeDepths) {
            c.save(); c.concat(base.projection())
            paint.shader = null; paint.color = if (z%2==0) 0xff9ca1ab.toInt() else 0xff868b96.toInt()
            c.drawCircle(0f,0f,radius,paint); c.restore()
        }
        face(c,base,if (facing) 0 else -9,radius,if (euro) "€" else "$")
    }
    private fun face(c: Canvas, base: CoinCardOnboardingMatrix, z: Int, radius: Float, symbol: String) {
        c.save(); c.concat(base.projection())
        paint.color = Color.WHITE; paint.alpha = 255
        paint.shader = LinearGradient(-radius,-radius,radius,radius,intArrayOf(Color.WHITE,0xffc9ccd4.toInt(),0xff8e939e.toInt(),0xffe9ebf0.toInt()),floatArrayOf(0f,0.4f,0.75f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(0f,0f,radius,paint); paint.shader = null
        c.drawCircle(0f,0f,radius*0.8f,ring)
        if (z == 0) {
            val widths = FloatArray(symbol.length); r.symbol.getTextWidths(symbol,widths)
            val sx = -widths.sum()/2; val sy = radius*0.36f
            r.symbol.color = 0xff7d828d.toInt(); c.drawText(symbol,sx+1,sy+1.2f,r.symbol)
            r.symbol.color = Color.WHITE; c.drawText(symbol,sx-0.8f,sy-0.8f,r.symbol)
            r.symbol.color = 0xffc3c7cf.toInt(); c.drawText(symbol,sx,sy,r.symbol)
        }
        c.restore()
    }
}
