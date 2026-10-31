package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

// API 26–32: the same bays, path, colours and proximity bands, rendered as flat shapes.
internal object ParkAssistScrubFallback {
    fun draw(canvas: Canvas,paint: Paint,w: Float,h: Float,f: ParkAssistScrubFrame) {
        paint.shader = null; paint.alpha = 255; paint.style = Paint.Style.FILL; paint.color = 0xFF222329.toInt()
        canvas.drawRect(0f,0f,w,h,paint)
        canvas.save(); canvas.translate(w/2-0.75f*58,h/2+1.9f*cos(0.62f)*58)
        canvas.scale(58f,-58f*cos(0.62f))
        paint.color = 0xFFBDBDB5.toInt(); paint.strokeWidth = 0.12f
        for (i in -2..2) { val x = (i+0.5f)*2.7f; canvas.drawLine(x,-3.3f,x,2.4f,paint) }
        fun car(x: Float,y: Float,heading: Float,color: Int,ours: Boolean) {
            canvas.save(); canvas.translate(x,y); canvas.rotate(heading*180/PI.toFloat())
            paint.style = Paint.Style.FILL; paint.color = 0xFF101114.toInt()
            canvas.drawRoundRect(-1.04f,-1.72f,1.04f,1.72f,0.3f,0.3f,paint)
            paint.color = color; canvas.drawRoundRect(-0.92f,-2.15f,0.92f,2.15f,0.3f,0.3f,paint)
            paint.color = 0xFF293340.toInt(); canvas.drawRoundRect(-0.76f,-1.35f,0.76f,0.95f,0.3f,0.3f,paint)
            if (ours) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.06f
                paint.color = ParkAssistScrubTheme.gapColor(ParkAssistScrubMotion.gapAt(f.s)).toArgb()
                paint.alpha = (255*f.assist).toInt()
                for (i in 1..4) {
                    val r = i*0.4f
                    canvas.drawArc(-r,-2.2f-r,r,-2.2f+r,225f,90f,false,paint)
                }
                paint.style = Paint.Style.FILL; paint.alpha = 255
                paint.color = if (f.hazard > 0.1) 0xFFFFA626.toInt() else if (f.brake > 0.1) 0xFFFF4D45.toInt() else 0xFFE0E0D8.toInt()
                for (side in listOf(-1,1)) canvas.drawRoundRect(side*0.62f-0.22f,-2.22f,side*0.62f+0.22f,-2.12f,0.04f,0.04f,paint)
            }
            canvas.restore()
        }
        car(-2.7f,-0.75f,0f,0xFF9E9FAB.toInt(),false)
        car(2.7f,-0.55f,0f,0xFF9E9FAB.toInt(),false)
        val pose = ParkAssistScrubMotion.poseAt(f.s)
        car(pose.x.toFloat(),pose.y.toFloat(),pose.heading.toFloat(),0xFFFF8533.toInt(),true)
        paint.color = 0xFF6B6B73.toInt(); canvas.drawRoundRect(-12f,-3.8f,12f,-3.3f,0.08f,0.08f,paint)
        canvas.restore()
    }
}
