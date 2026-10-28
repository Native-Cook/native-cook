package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.*

/** Native Skia vertices and the unchanged, cached Blender RGBA endcap. */
internal class TrophySavingsMilestoneRibbon(private val rollImage: Bitmap?) {
    private class Mesh {
        val positions = FloatArray(1024)
        val colors = IntArray(512)
        val indices = ShortArray(1536)
        var vertices = 0
        var indexCount = 0
        fun reset() { vertices = 0; indexCount = 0 }
    }
    private val rear = Mesh()
    private val front = Mesh()
    private val coil = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val imageRect = RectF(-16f,-25f,48f,39f)
    private var lastProgress = Double.NaN
    private var lastPose = Double.NaN
    private var model = TrophySavingsMilestoneRibbonGeometry.frame(0.0)
    private val face = intArrayOf(0xFFFBA4BA.toInt(),0xFFF57D9E.toInt(),0xFFF57D9E.toInt(),0xFFD9507C.toInt())
    private val reverse = intArrayOf(0xFFD25D82.toInt(),0xFFA92C56.toInt(),0xFFA92C56.toInt(),0xFF8E2048.toInt())
    private val edge = intArrayOf(0xFFD25078.toInt(),0xFFC34C70.toInt(),0xFFC34C70.toInt(),0xFFB13E64.toInt())
    private val seam = intArrayOf(0xFFD46C88.toInt(),0xFFD56D89.toInt(),0xFFED7A99.toInt(),0xFFEE7B9A.toInt())
    fun update(progress: Double, pose: Double) {
        if (progress == lastProgress && pose == lastPose) return
        lastProgress = progress; lastPose = pose
        model = TrophySavingsMilestoneRibbonGeometry.frame(progress,pose)
        rear.reset(); front.reset()
        for (span in model.spans) append(if(span.foreground) front else rear,span)
        val end=model.coilJoint; val joint=TrophySavingsMilestoneRibbonGeometry.FEED_JOINT
        fun point(x: Double,y: Double) = TrophySavingsMilestoneRibbonGeometry.Point(
            end.x+(x-joint.x)*model.coilScale,end.y+(y-joint.y)*model.coilScale)
        val a=point(44.08,138.8); val b=point(44.76,172.05); val c=point(40.0,154.0); val d=point(37.28,141.65)
        coil.rewind(); coil.moveTo(a.x.toFloat(),a.y.toFloat())
        coil.quadTo(b.x.toFloat(),b.y.toFloat(),c.x.toFloat(),c.y.toFloat())
        coil.quadTo(d.x.toFloat(),d.y.toFloat(),end.x.toFloat(),end.y.toFloat())
    }
    private fun append(mesh: Mesh, span: TrophySavingsMilestoneRibbonGeometry.Span) {
        val base=mesh.vertices
        for (station in arrayOf(span.a,span.b)) {
            val normal=station.tangent.normal(); val half=station.width*0.5
            val left=station.center+normal*half; val right=station.center-normal*half
            val e=min(0.08,0.65/max(0.8,station.width))
            val points=arrayOf(left,left+(right-left)*e,right+(left-right)*e,right)
            val palette=if(abs(station.facing)<0.09) edge else if(station.facing>=0) face else reverse
            for (i in 0..3) {
                val at=mesh.vertices++
                mesh.positions[at*2]=points[i].x.toFloat(); mesh.positions[at*2+1]=points[i].y.toFloat()
                val a=palette[i]; val b=seam[i]; val mix=station.joinBlend
                mesh.colors[at]=Color.rgb((Color.red(a)+(Color.red(b)-Color.red(a))*mix).roundToInt(),
                    (Color.green(a)+(Color.green(b)-Color.green(a))*mix).roundToInt(),
                    (Color.blue(a)+(Color.blue(b)-Color.blue(a))*mix).roundToInt())
            }
        }
        // Every final RN station has zero depth; its stable triangle sort preserves this order.
        for (i in 0..2) for (index in intArrayOf(i,4+i,5+i,i,5+i,1+i)) mesh.indices[mesh.indexCount++]=(base+index).toShort()
    }
    fun draw(canvas: Canvas, foreground: Boolean) {
        paint.shader=null; paint.maskFilter=null; paint.style=Paint.Style.FILL
        if (!foreground && model.coilOpacity>0) {
            paint.color=0xFFFF7F8C.toInt(); paint.alpha=(255*model.coilOpacity).roundToInt()
            paint.strokeWidth=model.coilWidth.toFloat(); paint.strokeCap=Paint.Cap.ROUND; paint.style=Paint.Style.STROKE
            canvas.drawPath(coil,paint); paint.style=Paint.Style.FILL
        }
        val mesh=if(foreground) front else rear
        if(mesh.vertices>0 && model.meshOpacity>0) {
            if(foreground) {
                canvas.save(); canvas.clipPath(TrophySavingsMilestoneArtwork.bowl)
                canvas.translate(0f,1.2f)
                paint.color=Color.argb((255*0.13*model.meshOpacity).roundToInt(),92,41,31)
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES,mesh.vertices*2,mesh.positions,0,null,0,null,0,mesh.indices,0,mesh.indexCount,paint)
                canvas.restore()
            }
            paint.color=Color.WHITE; paint.alpha=(255*model.meshOpacity).roundToInt()
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES,mesh.vertices*2,mesh.positions,0,null,0,mesh.colors,0,mesh.indices,0,mesh.indexCount,paint)
        }
        val roll=model.roll
        val alpha=if(roll.foreground==foreground) roll.opacity*model.meshOpacity*TrophySavingsMilestoneMotion.smooth((roll.width-3)/6) else 0.0
        if(rollImage!=null && alpha>0) {
            canvas.save(); canvas.translate(roll.center.x.toFloat(),roll.center.y.toFloat())
            canvas.rotate((atan2(roll.tangent.y,roll.tangent.x)*180/PI).toFloat())
            canvas.scale((model.tipLength/36).toFloat(),(roll.width/34).toFloat())
            paint.color=Color.WHITE; paint.alpha=(255*alpha).roundToInt()
            canvas.drawBitmap(rollImage,null,imageRect,paint); canvas.restore()
        }
    }
}
