package com.ngocdevv.nativecook.blocks.cards.dollhousefloors

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import kotlin.math.*

/** Static projected faces are recorded once; live frames translate entire opaque storeys. */
internal class DollhouseFloorsArt(val width: Double, val height: Double) {
    private val s = round(width * 0.074)
    private val cx = width / 2 + 6
    private val base = round(height * 0.63)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun point(x: Double, y: Double, z: Double) = (cx + (x-y)*s*0.866).toFloat() to (base-(x+y)*s*0.5-z*s).toFloat()
    private fun record(draw: (Canvas) -> Unit): Picture {
        val p = Picture(); val c = p.beginRecording(ceil(width).toInt(), ceil(height).toInt()); draw(c); p.endRecording(); return p
    }
    private val plate = record { c ->
        val p = point(2.0,1.5,-0.3)
        oval(c,p.first-s*4.2,p.second-s*1.6,s*8.4,s*3.6,0xE7DCCB,1.0)
        oval(c,p.first-s*3.2,p.second-s,s*6.4,s*2.4,0x3A2E22,0.08)
    }
    private val floors = (0..1).map { i -> record { storey(it,i*2.65,i,1.0) } }
    private val roof = record { drawRoof(it,5.3,1.0) }
    private fun oval(c: Canvas, x: Double, y: Double, w: Double, h: Double, color: Int, alpha: Double) {
        paint.style=Paint.Style.FILL; paint.color=color or 0xFF000000.toInt(); paint.alpha=(alpha*255).roundToInt().coerceIn(0,255)
        c.drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint)
    }
    private fun polygon(c: Canvas, pts: Array<DoubleArray>, color: Int, a: Double) {
        val path=Path()
        pts.forEachIndexed { i,p -> val q=point(p[0],p[1],p[2]); if(i==0)path.moveTo(q.first,q.second) else path.lineTo(q.first,q.second) }
        path.close(); paint.style=Paint.Style.FILL; paint.color=color or 0xFF000000.toInt(); paint.alpha=(a*255).roundToInt().coerceIn(0,255); c.drawPath(path,paint)
    }
    private fun box(c: Canvas,x: Double,y: Double,z: Double,w: Double,d: Double,h: Double,top: Int,left: Int,right: Int,a: Double) {
        polygon(c,arrayOf(doubleArrayOf(x,y,z),doubleArrayOf(x+w,y,z),doubleArrayOf(x+w,y,z+h),doubleArrayOf(x,y,z+h)),left,a)
        polygon(c,arrayOf(doubleArrayOf(x,y,z),doubleArrayOf(x,y+d,z),doubleArrayOf(x,y+d,z+h),doubleArrayOf(x,y,z+h)),right,a)
        polygon(c,arrayOf(doubleArrayOf(x,y,z+h),doubleArrayOf(x+w,y,z+h),doubleArrayOf(x+w,y+d,z+h),doubleArrayOf(x,y+d,z+h)),top,a)
    }
    private fun storey(c: Canvas,z: Double,kind: Int,a: Double) {
        box(c, 0.0, 0.0, z - 0.25, 4.0, 3.0, 0.25, 0xE9C99B, 0xC9A574, 0xD8B585, a)
        polygon(c, arrayOf(doubleArrayOf(0.0, 3.0, z), doubleArrayOf(4.0, 3.0, z), doubleArrayOf(4.0, 3.0, z + 2.4), doubleArrayOf(0.0, 3.0, z + 2.4)), 0xF6EFE4, a)
        polygon(c, arrayOf(doubleArrayOf(4.0, 0.0, z), doubleArrayOf(4.0, 3.0, z), doubleArrayOf(4.0, 3.0, z + 2.4), doubleArrayOf(4.0, 0.0, z + 2.4)), 0xEDE3D4, a)
        polygon(c, arrayOf(doubleArrayOf(0.0, 3.0 - 0.12, z + 2.4), doubleArrayOf(4.0, 3.0 - 0.12, z + 2.4), doubleArrayOf(4.0, 3.0, z + 2.4), doubleArrayOf(0.0, 3.0, z + 2.4)), 0xD9CBB6, a)
        polygon(c, arrayOf(doubleArrayOf(4.0 - 0.12, 0.0, z + 2.4), doubleArrayOf(4.0, 0.0, z + 2.4), doubleArrayOf(4.0, 3.0, z + 2.4), doubleArrayOf(4.0 - 0.12, 3.0, z + 2.4)), 0xD9CBB6, a)
        polygon(c, arrayOf(doubleArrayOf(1.3, 3.0 - 0.01, z + 0.9), doubleArrayOf(2.4, 3.0 - 0.01, z + 0.9), doubleArrayOf(2.4, 3.0 - 0.01, z + 1.9), doubleArrayOf(1.3, 3.0 - 0.01, z + 1.9)), 0xBFE3F2, a)
        polygon(c, arrayOf(doubleArrayOf(4.0 - 0.01, 0.8, z + 0.9), doubleArrayOf(4.0 - 0.01, 1.8, z + 0.9), doubleArrayOf(4.0 - 0.01, 1.8, z + 1.9), doubleArrayOf(4.0 - 0.01, 0.8, z + 1.9)), 0xBFE3F2, a)
        if (kind == 0) {
            polygon(c, arrayOf(doubleArrayOf(0.4, 0.4, z + 0.01), doubleArrayOf(2.0, 0.4, z + 0.01), doubleArrayOf(2.0, 1.8, z + 0.01), doubleArrayOf(0.4, 1.8, z + 0.01)), 0xF2B8A2, a)
            box(c, 0.5, 2.1, z, 1.8, 0.7, 0.45, 0x5FA8A0, 0x4C8E86, 0x56998F, a)
            box(c, 0.5, 2.6, z + 0.45, 1.8, 0.2, 0.4, 0x5FA8A0, 0x4C8E86, 0x56998F, a)
            box(c, 0.9, 0.8, z, 0.8, 0.6, 0.35, 0xB07A4A, 0x8E5E35, 0x9D6A3E, a)
            box(c, 4.0 - 0.7, 0.3, z, 0.6, 2.4, 0.9, 0xF4F6F8, 0xD5DBE2, 0xE3E8EE, a)
            box(c, 4.0 - 0.7, 0.3, z + 0.9, 0.6, 2.4, 0.06, 0x7C8898, 0x66717F, 0x707C8B, a)
            box(c, 2.4, 0.5, z, 0.7, 0.7, 0.7, 0x5DBB6B, 0x46A65A, 0x52B062, a)
        } else {
            box(c, 0.3, 1.4, z, 1.6, 1.4, 0.4, 0xF7F9FC, 0xD6DEE8, 0xE4EAF2, a)
            box(c, 0.3, 1.4, z + 0.4, 1.6, 0.6, 0.12, 0x7E9CFF, 0x6A86E6, 0x7590F2, a)
            box(c, 0.3, 2.7, z + 0.4, 1.6, 0.25, 0.5, 0xB98BFF, 0x9D72E6, 0xAA7EF2, a)
            box(c, 2.1, 2.3, z, 0.5, 0.5, 0.5, 0xB07A4A, 0x8E5E35, 0x9D6A3E, a)
            box(c, 4.0 - 1.2, 0.3, z, 1.0, 1.6, 0.55, 0xFFFFFF, 0xDCE3EA, 0xE8EDF2, a)
            polygon(c, arrayOf(doubleArrayOf(4.0 - 1.1, 0.4, z + 0.56), doubleArrayOf(4.0 - 0.3, 0.4, z + 0.56), doubleArrayOf(4.0 - 0.3, 1.8, z + 0.56), doubleArrayOf(4.0 - 1.1, 1.8, z + 0.56)), 0xBFE3F2, a)
        }
    }
    private fun drawRoof(c: Canvas, z: Double, a: Double) {
        val h = 1.7
        box(c, 0.0, 0.0, z - 0.2, 4.0, 3.0, 0.2, 0xD9CBB6, 0xBCAE98, 0xC9BBA5, a)
        polygon(c, arrayOf(doubleArrayOf(0.0, 0.0, z), doubleArrayOf(0.0, 3.0, z), doubleArrayOf(0.0, 3.0 / 2.0, z + h)), 0xF0E6D8, a)
        polygon(c, arrayOf(doubleArrayOf(-0.15, -0.15, z), doubleArrayOf(4.0 + 0.15, -0.15, z), doubleArrayOf(4.0 + 0.15, 3.0 / 2.0, z + h), doubleArrayOf(-0.15, 3.0 / 2.0, z + h)), 0x5B6475, a)
        for (i in 1..4) {
            val k = i.toDouble() / 5
            val q0 = point(-0.15, -0.15 + 1.65 * k, z + h * k)
            val q1 = point(4.15, -0.15 + 1.65 * k, z + h * k)
            paint.color = 0xFF4A5262.toInt(); paint.alpha = (a * 255).roundToInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f
            c.drawLine(q0.first, q0.second, q1.first, q1.second, paint)
        }
        box(c, 4.0 - 1.1, 3.0 / 2.0 - 0.2, z + h * 0.5, 0.45, 0.45, 1.2, 0xC97B5A, 0xA9624A, 0xB86D51, a)
        polygon(c, arrayOf(doubleArrayOf(0.6, 0.0, z + 0.6), doubleArrayOf(1.4, 0.0, z + 0.6), doubleArrayOf(1.4, 0.4, z + 0.98), doubleArrayOf(0.6, 0.4, z + 0.98)), 0x7FB4E8, a)
    }
    fun draw(c: Canvas, f: DollhouseFloorsMotion.Frame, reduced: Boolean) {
        val (l1,l2)=DollhouseFloorsMotion.lifts(f.e,reduced)
        c.drawPicture(plate)
        fun layer(p: Picture,lift: Double,alpha: Double) {
            c.save(); c.translate(0f,(-lift*s).toFloat()); c.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(alpha*255).roundToInt().coerceIn(0,255)); c.drawPicture(p); c.restore(); c.restore()
        }
        fun shadow(z: Double,amount: Double) {
            val p=point(2.0,1.5,z)
            oval(c,p.first-s*2.6,p.second-s*0.9,s*5.2,s*1.8,0x3A2E22,0.1*min(1.0,amount))
        }
        layer(floors[0],0.0,1-0.62*f.selK[0])
        if(l1>0.05)shadow(2.42,l1)
        layer(floors[1],l1,1-0.62*f.selK[1])
        if(l2-l1>0.05)shadow(2.65+l1+2.42,l2-l1)
        layer(roof,l2,if(reduced)1-f.e*0.9 else 1-0.62*f.selK[2])
    }
}
