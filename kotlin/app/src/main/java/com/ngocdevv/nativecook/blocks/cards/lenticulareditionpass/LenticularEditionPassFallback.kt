package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import android.graphics.*
import kotlin.math.*

/** API26–32: reusable two-times print buffer and fixed 24×16 bowed perspective mesh.
 * Ridge-local switching remains physical; only inverse-ray sampling is approximated by the mesh.
 */
internal class LenticularEditionPassFallback(resources: LenticularEditionPassResources) {
    private val width=666;private val height=422
    private val sourceWidth=resources.atlas.width;private val sourceHeight=resources.atlas.height
    private val source=IntArray(sourceWidth*sourceHeight).also {resources.atlas.getPixels(it,0,sourceWidth,0,0,sourceWidth,sourceHeight)}
    private val a=IntArray(width*height);private val b=IntArray(width*height)
    private val coverage=FloatArray(width*height);private val border=FloatArray(width*height)
    private val output=IntArray(width*height);private val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    private val vertices=FloatArray(25*17*2)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val laminate=Path()
    private var previous: LenticularEditionPassFrame?=null
    init {
        for(y in 0 until height) for(x in 0 until width) {
            val u=(x+.5)/2-166.5;val v=(y+.5)/2-105.5;val index=y*width+x
            val phase=((u+166.5)/1.45)%1
            val displacement=.29267578125*sin(2*PI*phase)
            val shade=(1+.055*cos(2*PI*phase)+.025*cos(4*PI*phase+.7))*(1-.045*LenticularEditionPassMath.smooth((phase-.9)/.1))
            fun shaded(art: Int): Int {
                val color=sample((u+175+displacement)*3,(v+106.5+art*213)*3)
                return Color.rgb((Color.red(color)*shade).roundToInt().coerceIn(0,255),
                    (Color.green(color)*shade).roundToInt().coerceIn(0,255),(Color.blue(color)*shade).roundToInt().coerceIn(0,255))
            }
            a[index]=shaded(0);b[index]=shaded(1)
            val qx=abs(u)-160.5;val qy=abs(v)-99.5
            val distance=hypot(max(qx,0.0),max(qy,0.0))+min(max(qx,qy),0.0)-6
            coverage[index]=(1-LenticularEditionPassMath.smooth((distance+.32)/.64)).toFloat()
            border[index]=(.35*LenticularEditionPassMath.smooth((distance+1.2)/.4)).toFloat()
        }
    }
    private fun sample(x: Double,y: Double): Int {
        val px=x-.5;val py=y-.5;val x0=floor(px).toInt().coerceIn(0,sourceWidth-1);val y0=floor(py).toInt().coerceIn(0,sourceHeight-1)
        val x1=(x0+1).coerceAtMost(sourceWidth-1);val y1=(y0+1).coerceAtMost(sourceHeight-1)
        val fx=px-floor(px);val fy=py-floor(py)
        fun channel(shift: Int): Int {
            val aa=(source[y0*sourceWidth+x0] ushr shift) and 255;val bb=(source[y0*sourceWidth+x1] ushr shift) and 255
            val cc=(source[y1*sourceWidth+x0] ushr shift) and 255;val dd=(source[y1*sourceWidth+x1] ushr shift) and 255
            return ((aa+(bb-aa)*fx)*(1-fy)+(cc+(dd-cc)*fx)*fy).roundToInt()
        }
        return Color.rgb(channel(16),channel(8),channel(0))
    }
    private fun composite(frame: LenticularEditionPassFrame) {
        if(previous?.pose==frame.pose&&previous?.explicitViewB==frame.explicitViewB) return
        previous=frame
        val center=22.5*LenticularEditionPassMath.clamp(frame.pose.yaw/14,-1.0,1.0)
        val strength=.06+.08*LenticularEditionPassMath.clamp(abs(frame.pose.yaw)/14)
        for(x in 0 until width) {
            val u=(x+.5)/2-166.5
            val j=floor((u+166.5)/1.45).toInt().coerceIn(0,229)
            val weight=LenticularEditionPassGeometry.ridgeWeight(j,frame)
            val reflection=strength*exp(-.5*((u-center)/33.972872).pow(2))
            for(y in 0 until height) {
                val i=y*width+x;val ca=a[i];val cb=b[i];val alpha=(coverage[i]*255).roundToInt()
                fun channel(shift: Int,borderColor: Double): Int {
                    val ac=(ca ushr shift) and 255;val bc=(cb ushr shift) and 255
                    val print=ac+(bc-ac)*weight;val reflected=print+(255-print)*reflection
                    return (reflected+(borderColor*255-reflected)*border[i]).roundToInt().coerceIn(0,255)
                }
                output[i]=Color.argb(alpha,channel(16,.718),channel(8,.776),channel(0,.808))
            }
        }
        bitmap.setPixels(output,0,width,0,0,width,height)
    }
    fun draw(c: Canvas,frame: LenticularEditionPassFrame) {
        composite(frame)
        val m=LenticularEditionPassGeometry.rotation(frame.pose)
        fun project(x: Double,y: Double,depthOffset: Double=0.0): LenticularEditionPassPoint {
            val z=3.5*(1-(2*x/333).pow(2))*(1-(2*y/211).pow(2))+depthOffset
            val xx=m[0]*x+m[1]*y+m[2]*z;val yy=m[3]*x+m[4]*y+m[5]*z;val zz=m[6]*x+m[7]*y+m[8]*z
            val scale=800/(800-zz)
            return LenticularEditionPassPoint(196.5+xx*scale,366.5+yy*scale)
        }
        laminate.rewind()
        val centers=arrayOf(160.5 to -99.5,160.5 to 99.5,-160.5 to 99.5,-160.5 to -99.5)
        for(corner in 0..3) for(step in 0..12) {
            val angle=(-90+corner*90+step*7.5)*PI/180
            val p=project(centers[corner].first+6*cos(angle),centers[corner].second+6*sin(angle),-1.4)
            if(corner==0&&step==0) laminate.moveTo(p.x.toFloat(),p.y.toFloat()) else laminate.lineTo(p.x.toFloat(),p.y.toFloat())
        }
        laminate.close();paint.shader=LinearGradient(0f,336.5f,0f,396.5f,0xffCCD9D4.toInt(),0xff637A8C.toInt(),Shader.TileMode.CLAMP)
        paint.alpha=255;c.drawPath(laminate,paint);paint.shader=null
        for(y in 0..16) for(x in 0..24) {
            val p=project(-166.5+333.0*x/24,-105.5+211.0*y/16);val i=(y*25+x)*2
            vertices[i]=p.x.toFloat();vertices[i+1]=p.y.toFloat()
        }
        c.drawBitmapMesh(bitmap,24,16,vertices,0,null,0,paint)
    }
}
