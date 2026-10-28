package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.*

/** The exact source-visible table and fragments. The streamer's source curl is not in this table. */
internal object TrophySavingsMilestonePaper {
    private val palette=intArrayOf(0xFFF37E87.toInt(),0xFFEF98CD.toInt(),0xFF68BFE8.toInt(),0xFFA5DC63.toInt(),0xFFF5D264.toInt())
    class Piece(val x: Float,val y: Float,val length: Float,val width: Float,val angle: Float,val bend: Float,
        colorIndex: Int,val kind: String,val opacity: Float) {
        val color=palette[colorIndex]
        val face=Path().apply {
            val h=length/2; val s=width/2
            when(kind) {
                "yellowCurl" -> { moveTo(10f,-29f); cubicTo(-2f,-28f,-7f,-23f,-2f,-14f); cubicTo(13f,6f,7f,9f,-5f,16f); cubicTo(-10f,20f,-12f,23f,-12f,29f) }
                "greenCurl" -> { moveTo(2f,-23f); cubicTo(-7f,-22f,-10f,-8f,0f,-12f); cubicTo(11f,-18f,13f,-6f,3f,-6f); cubicTo(-10f,-7f,-12f,9f,0f,8f); cubicTo(12f,7f,11f,19f,9f,23f) }
                "speck" -> addOval(-s,-h,s,h,Path.Direction.CW)
                "arc" -> { moveTo(-h,-s); quadTo(0f,-bend-s,h,-s); lineTo(h,s); quadTo(0f,-bend+s,-h,s); close() }
                else -> { moveTo(-s,-h); quadTo(0f,-h+bend,s,-h); cubicTo(s*0.9f,-h*0.28f,s*0.3f,h*0.36f,s*0.8f,h); quadTo(0f,h-bend,-s,h); cubicTo(-s*0.3f,h*0.26f,-s*1.3f,-h*0.24f,-s,-h); close() }
            }
        }
        val fold=if(kind=="chip") Path().apply { val s=width/2; val h=length/2; moveTo(-s,-h); quadTo(0f,-h+bend,s,-h); lineTo(s*0.3f,-h*0.4f); close() } else null
        val lip=if(kind=="arc") LinearGradient(0f,-width/2-bend,0f,width/2-bend,intArrayOf(0x00FFFFFF,0x00FFFFFF,0xCCFFF9ED.toInt(),0x73501C3A),floatArrayOf(0f,0.72f,0.86f,1f),Shader.TileMode.CLAMP)
            else LinearGradient(-width/2,0f,width/2,0f,intArrayOf(0x00FFFFFF,0x00FFFFFF,0xCCFFF9ED.toInt(),0x73501C3A),floatArrayOf(0f,0.72f,0.86f,1f),Shader.TileMode.CLAMP)
        fun reverse(near: Boolean): Int {
            val f=if(near) 0.84 else 0.78
            return NativeColor.rgb((NativeColor.red(color)*f).roundToInt(),(NativeColor.green(color)*f).roundToInt(),(NativeColor.blue(color)*f).roundToInt())
        }
    }
    val pieces=listOf(
        Piece(169.5f,3.5f,9.0f,3.5f,18.0f,1.0f,4,"chip",0.85f),
        Piece(266.5f,5.5f,11.0f,5.5f,25.0f,2.0f,2,"chip",0.86f),
        Piece(114.5f,6.5f,12.0f,5.0f,-8.0f,1.5f,3,"chip",0.83f),
        Piece(312.0f,6.5f,7.0f,2.5f,25.0f,1.0f,3,"chip",0.74f),
        Piece(230.0f,7.5f,7.0f,2.5f,29.0f,0.6f,3,"chip",0.72f),
        Piece(15.0f,8.5f,7.0f,4.0f,35.0f,1.0f,0,"chip",0.86f),
        Piece(375.0f,12.0f,12.0f,6.0f,-35.0f,2.0f,0,"chip",0.88f),
        Piece(56.0f,17.0f,11.0f,5.5f,-50.0f,2.5f,1,"chip",0.8f),
        Piece(347.0f,37.5f,11.0f,5.0f,43.0f,1.5f,2,"chip",0.87f),
        Piece(133.0f,38.5f,10.0f,6.0f,25.0f,1.5f,2,"chip",0.84f),
        Piece(4.5f,42.0f,6.0f,3.0f,-55.0f,0.7f,4,"chip",0.55f),
        Piece(238.0f,45.0f,6.0f,2.4f,-8.0f,0.5f,2,"chip",0.73f),
        Piece(79.5f,48.0f,4.0f,1.6f,32.0f,0.3f,4,"chip",0.63f),
        Piece(306.0f,49.0f,4.0f,1.4f,-25.0f,0.3f,4,"chip",0.61f),
        Piece(155.0f,52.0f,58.0f,1.35f,0.0f,0.0f,4,"yellowCurl",0.8f),
        Piece(197.0f,52.5f,9.0f,3.2f,18.0f,1.3f,0,"chip",0.88f),
        Piece(378.5f,57.0f,3.0f,1.2f,-40.0f,0.2f,3,"speck",0.35f),
        Piece(38.5f,62.5f,9.0f,3.2f,-20.0f,1.9f,1,"arc",0.78f),
        Piece(375.5f,65.0f,12.0f,3.7f,37.0f,1.5f,0,"chip",0.85f),
        Piece(42.0f,68.5f,2.0f,1.0f,20.0f,0.0f,1,"speck",0.29f),
        Piece(157.0f,69.0f,2.0f,1.0f,0.0f,0.0f,2,"speck",0.28f),
        Piece(105.5f,69.5f,3.0f,1.0f,-10.0f,0.2f,2,"chip",0.38f),
        Piece(100.0f,71.5f,9.0f,2.3f,18.0f,1.1f,2,"chip",0.78f),
        Piece(270.5f,74.0f,4.0f,1.8f,-33.0f,0.5f,4,"chip",0.71f),
        Piece(346.5f,82.0f,8.0f,3.6f,-4.0f,1.3f,1,"chip",0.82f),
        Piece(310.5f,82.5f,2.0f,0.9f,0.0f,0.0f,4,"speck",0.28f),
        Piece(231.5f,85.0f,14.0f,4.8f,15.0f,2.6f,0,"arc",0.87f),
        Piece(12.0f,88.0f,11.0f,5.0f,22.0f,1.9f,4,"chip",0.87f),
        Piece(2.0f,91.5f,3.0f,1.0f,0.0f,0.0f,2,"chip",0.35f),
        Piece(56.5f,97.0f,13.0f,6.0f,-38.0f,1.7f,0,"chip",0.88f),
        Piece(311.5f,98.5f,47.0f,1.45f,0.0f,0.0f,3,"greenCurl",0.83f),
        Piece(380.5f,103.0f,8.0f,2.7f,-10.0f,0.8f,1,"chip",0.82f),
        Piece(331.5f,113.0f,3.0f,1.0f,20.0f,0.2f,4,"chip",0.39f),
        Piece(334.5f,113.0f,6.0f,2.0f,-15.0f,0.6f,2,"chip",0.77f),
        Piece(96.5f,125.0f,14.0f,4.5f,-12.0f,2.1f,3,"arc",0.82f),
        Piece(44.5f,130.5f,8.0f,3.0f,-18.0f,2.2f,0,"arc",0.83f),
        Piece(18.0f,139.0f,7.0f,2.8f,42.0f,0.9f,4,"chip",0.7f),
        Piece(71.5f,146.0f,4.0f,1.5f,-18.0f,0.4f,1,"chip",0.65f),
        Piece(346.5f,158.0f,13.0f,6.5f,-35.0f,2.6f,3,"chip",0.85f),
        Piece(311.5f,165.0f,7.0f,2.7f,12.0f,1.5f,1,"arc",0.71f),
        Piece(80.5f,176.5f,5.0f,2.8f,-23.0f,0.5f,2,"chip",0.72f),
        Piece(9.0f,183.0f,8.0f,3.5f,-45.0f,1.0f,3,"chip",0.78f),
        Piece(384.0f,187.5f,9.0f,3.6f,-32.0f,1.0f,3,"chip",0.75f),
        Piece(362.5f,199.5f,3.0f,1.8f,-25.0f,0.2f,1,"chip",0.62f),
        Piece(325.5f,203.5f,11.0f,5.5f,28.0f,2.2f,2,"chip",0.88f),
        Piece(43.5f,212.0f,12.0f,4.7f,20.0f,2.7f,0,"arc",0.86f),
        Piece(17.0f,231.5f,9.0f,3.6f,24.0f,1.0f,4,"chip",0.77f),
        Piece(372.0f,234.5f,7.0f,2.4f,-20.0f,0.7f,0,"chip",0.78f),
        Piece(14.5f,11.5f,1.2f,1.2f,0.0f,0.0f,4,"speck",0.25f),
        Piece(114.0f,14.5f,1.3f,1.0f,0.0f,0.0f,2,"speck",0.25f),
        Piece(341.5f,33.0f,2.0f,1.0f,-20.0f,0.0f,2,"speck",0.28f),
        Piece(340.5f,43.0f,1.2f,1.0f,0.0f,0.0f,4,"speck",0.25f),
        Piece(315.0f,87.5f,1.4f,1.2f,0.0f,0.0f,4,"speck",0.25f),
        Piece(324.0f,96.5f,1.2f,1.8f,0.0f,0.0f,3,"speck",0.23f),
        Piece(329.0f,113.5f,1.0f,1.8f,0.0f,0.0f,2,"speck",0.23f),
        Piece(371.0f,143.5f,5.0f,1.9f,-70.0f,0.5f,4,"chip",0.66f),
        Piece(381.5f,191.5f,1.0f,1.0f,0.0f,0.0f,4,"speck",0.23f),
        Piece(325.0f,210.5f,1.5f,1.0f,0.0f,0.0f,4,"speck",0.23f)
    )
    val moving=intArrayOf(29,34,27,45,38,44,9,26,15,33,39,24,17,47,7,43,22,36)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val blur=BlurMaskFilter(1.5f,BlurMaskFilter.Blur.NORMAL)
    val backdrop=Picture().apply {
        val canvas=beginRecording(393,852); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=0xFFEFEFEF.toInt(); canvas.drawRect(0f,0f,393f,852f,p)
        var y=7.25f
        while(y<380f) {
            val fade=((380-y)/30).coerceIn(0f,1f)
            p.color=NativeColor.argb((255*0.09f*fade).roundToInt(),0,0,0)
            var x=0.25f
            while(x<393f) { canvas.drawCircle(x,y,0.55f,p); x+=11.5f }
            y+=11.5f
        }
        endRecording()
    }
    val staticPicture=Picture().apply {
        val canvas=beginRecording(393,350)
        for(i in pieces.indices) if(i !in moving) source(canvas,pieces[i])
        endRecording()
    }
    private fun source(canvas: NativeCanvas,piece: Piece) {
        canvas.save(); canvas.translate(piece.x,piece.y); canvas.rotate(piece.angle)
        val bound=piece.length+piece.width+12
        canvas.saveLayerAlpha(-bound,-bound,bound,bound,(255*piece.opacity).roundToInt())
        paint.shader=null; paint.maskFilter=null; paint.color=piece.color; paint.alpha=255
        paint.style=if(piece.kind.endsWith("Curl")) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth=piece.width; paint.strokeCap=Paint.Cap.ROUND
        canvas.drawPath(piece.face,paint)
        piece.fold?.let { paint.style=Paint.Style.FILL; paint.color=NativeColor.argb((255*0.18).roundToInt(),255,255,255); canvas.drawPath(it,paint) }
        canvas.restore(); canvas.restore()
    }
    private data class Pose(val x: Double=0.0,val y: Double=0.0,val rotation: Double=0.0,
        val sx: Double=1.0,val sy: Double=1.0,val lift: Double=0.0,val reverse: Double=0.0)
    private val travel=arrayOf(
        doubleArrayOf(-24.0,-22.0,-1.0),doubleArrayOf(-27.0,-14.0,1.0),doubleArrayOf(28.0,-22.0,-1.0),doubleArrayOf(18.0,30.0,1.0),doubleArrayOf(-30.0,12.0,-1.0),doubleArrayOf(26.0,30.0,1.0),
        doubleArrayOf(-10.0,10.0,1.0),doubleArrayOf(12.0,-12.0,-1.0),doubleArrayOf(-6.0,-12.0,1.0),doubleArrayOf(13.0,6.0,-1.0),doubleArrayOf(12.0,10.0,1.0),doubleArrayOf(12.0,-8.0,-1.0),
        doubleArrayOf(9.0,-9.0,1.0),doubleArrayOf(-13.0,12.0,-1.0),doubleArrayOf(-7.0,12.0,-1.0),doubleArrayOf(-12.0,11.0,1.0),doubleArrayOf(-9.0,-8.0,1.0),doubleArrayOf(10.0,8.0,-1.0))
    private val crestFacing=doubleArrayOf(0.24,PI-0.3,-0.32,PI+0.24,0.36,-0.24)
    private fun smooth(x: Double)=TrophySavingsMilestoneMotion.smooth(x)
    private fun pose(scene: TrophySavingsMilestoneCelebration.Frame,index: Int,near: Boolean): Pose {
        val start=scene.wakeStarts[index]; val cadence=scene.cadence
        val age=(scene.clock-start)/cadence-if(near) 0.0 else 0.04
        if(!scene.active || scene.reduced || start<0 || age<0) return Pose()
        val flutter=if(age<=0.72) sin(PI*age/0.72)*exp(-1.7*age) else 0.0
        val held=smooth(scene.progress/0.65)*smooth(age/0.16)
        val breeze=smooth(age/0.1)*(1-smooth((age-0.56)/0.28))
        val after=scene.contactStart>=0 && scene.clock-scene.contactStart>0.12*cadence
        val recoil=if(after) smooth((0.98-scene.progress)/0.25)*sin(PI*scene.progress) else 0.0
        val wind=max(breeze*(0.25+0.75*smooth(scene.progress/0.5)),0.6*recoil)
        val energy=(0.8*held+0.3*flutter).coerceIn(0.0,1.0)
        val tr=travel[index]; val scale=1+(if(near) 0.64 else 0.22)*energy
        val raw=tr[2]*((if(near) 3.3 else 2.6)*wind*sin(age*7.5+index*0.81)+1.8*flutter)
        val crest=if(near) smooth((scene.progress-0.78)/0.22)*smooth(age/0.16) else 0.0
        val flip=raw*(1-0.95*crest)+(crestFacing.getOrElse(index){0.0})*crest+0.12*wind*sin(age*6+index)*crest
        val facing=cos(flip)
        return Pose(tr[0]*energy+(if(near) 10.0 else 5.5)*wind*sin(age*9+index*0.81),
            tr[1]*energy+(if(near) 9.0 else 5.0)*wind*cos(age*7.5+index*0.67),
            tr[2]*0.55*energy+(if(near) 1.05 else 0.75)*wind*sin(age*8.5+index*0.73),
            scale*max(0.075,abs(facing))*(if(near) 1+0.72*energy else 1.0),scale*(1-0.06*sin(flip).pow(2)),energy,smooth(-facing/0.16))
    }
    fun drawMoving(canvas: NativeCanvas,scene: TrophySavingsMilestoneCelebration.Frame,foreground: Boolean) {
        val range=if(foreground) 0..5 else 6..17
        for(index in range) {
            val piece=pieces[moving[index]]; val near=index<6; val p=pose(scene,index,near)
            val localAxis=piece.angle+(if(piece.kind=="arc") 90 else 0)
            canvas.save(); canvas.translate(piece.x,piece.y)
            if(!near || p.lift!=0.0) canvas.translate(p.x.toFloat(),p.y.toFloat())
            if(near && p.lift!=0.0) {
                canvas.rotate((p.rotation*180/PI+localAxis).toFloat()); canvas.scale(p.sx.toFloat(),p.sy.toFloat()); canvas.rotate(-localAxis)
            } else if(!near) {
                canvas.rotate((p.rotation*180/PI).toFloat()); canvas.scale(p.sx.toFloat(),p.sy.toFloat())
            }
            canvas.translate(-piece.x,-piece.y)
            if(p.lift>0) {
                canvas.save(); canvas.translate(piece.x+1,piece.y+(if(near) 3f else 1.5f)); canvas.rotate(piece.angle)
                paint.shader=null; paint.maskFilter=if(near) blur else null; paint.style=Paint.Style.FILL
                paint.color=0xFF66515A.toInt(); paint.alpha=(255*p.lift*(if(near) 0.19 else 0.07)).roundToInt()
                canvas.drawPath(piece.face,paint); canvas.restore()
            }
            source(canvas,piece)
            if(p.lift>0 || p.reverse>0) {
                canvas.save(); canvas.translate(piece.x,piece.y); canvas.rotate(piece.angle)
                paint.shader=null; paint.maskFilter=null; paint.style=Paint.Style.FILL
                paint.color=piece.reverse(near); paint.alpha=(255*p.reverse).roundToInt(); canvas.drawPath(piece.face,paint)
                if(near) { paint.color=NativeColor.WHITE; paint.alpha=(255*p.lift*0.28).roundToInt(); paint.shader=piece.lip; canvas.drawPath(piece.face,paint) }
                paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeWidth=0.55f
                paint.color=0xFFFFF4DF.toInt(); paint.alpha=(255*p.lift*(if(near) 0.45 else 0.2)).roundToInt(); canvas.drawPath(piece.face,paint)
                canvas.restore()
            }
            canvas.restore()
        }
    }
}

@Composable
internal fun TrophySavingsMilestonePaperField(modifier: Modifier=Modifier) {
    Canvas(modifier) { drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save(); canvas.nativeCanvas.scale(density,density)
        canvas.nativeCanvas.drawPicture(TrophySavingsMilestonePaper.staticPicture); canvas.nativeCanvas.restore()
    } }
}
