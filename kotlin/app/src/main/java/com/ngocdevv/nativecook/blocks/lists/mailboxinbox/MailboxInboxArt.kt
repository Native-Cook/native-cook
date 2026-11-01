package com.ngocdevv.nativecook.blocks.lists.mailboxinbox

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

internal class MailboxInboxArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun color(c: Int, alpha: Double = 1.0) {
        paint.color = c or (0xFF shl 24)
        paint.alpha = (alpha.coerceIn(0.0, 1.0)*255).roundToInt()
    }
    private fun rect(cv: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, c: Int, a: Double = 1.0) {
        color(c,a)
        cv.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun circle(cv: Canvas, x: Double, y: Double, r: Double, c: Int) {
        color(c)
        cv.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint)
    }
    fun box(cv: Canvas, cx: Double, cy: Double, open: Double, reduced: Boolean) {
        val s = 88.0
        val door = if (reduced) 0.0 else MailboxInboxMotion.smoother(open*2.2)
        val flag = 1-MailboxInboxMotion.smoother(open*2.4)
        color(0x8C6A50,0.14)
        cv.drawOval((cx-s*0.9).toFloat(),(cy+s*1.55).toFloat(),(cx+s*0.9).toFloat(),(cy+s*1.85).toFloat(),paint)
        rect(cv,cx-s*0.12,cy+s*0.4,s*0.24,s*1.3,6.0,0x9C7356)
        rect(cv,cx-s*0.62,cy-s*0.5,s*1.46,s*0.95,s*0.47,0xC8382C)
        rect(cv,cx-s*0.7,cy-s*0.55,s*1.4,s*0.98,s*0.47,0xE8473A)
        rect(cv,cx-s*0.5,cy-s*0.48,s*0.9,s*0.18,s*0.09,0xFF7A6B,0.55)
        val mw=s*0.62; val mh=s*0.82; val mx=cx-s*0.7-mw*0.1; val my=cy-s*0.48
        rect(cv,mx,my,mw,mh,mw/2,0x7A1E18,door)
        cv.save()
        cv.scale(1f,max(0.05,cos(door*PI*0.55)).toFloat(),(mx+mw/2).toFloat(),(my+mh).toFloat())
        rect(cv,mx,my,mw,mh,mw/2,if (door > 0.5) 0xC8382C else 0xF25A4C)
        circle(cv,mx+mw/2,my+mh*0.3,s*0.06,0xFFE07A)
        cv.restore()
        val fx=cx+s*0.62; val fy=cy-s*0.05
        cv.save()
        cv.translate(fx.toFloat(),fy.toFloat())
        cv.rotate((-90*flag).toFloat())
        rect(cv,-s*0.04,-s*0.05,s*0.62,s*0.1,3.0,0x3B3F4A)
        rect(cv,s*0.4,-s*0.05,s*0.26,s*0.26,3.0,0xFFC23D)
        cv.restore()
        circle(cv,fx,fy,s*0.05,0x3B3F4A)
    }
}
