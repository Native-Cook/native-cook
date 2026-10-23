package com.ngocdevv.nativecook.blocks.media.portraitfilmstrip

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Shader

const val LOOP = 8.0

private fun ease(a: Double, b: Double, t: Double): Double { val x=((t-a)/(b-a)).coerceIn(0.0,1.0);return x*x*x*(10+x*(-15+6*x)) }
private fun color(hex: String) = android.graphics.Color.parseColor(hex)
private fun rect(c: NativeCanvas,p: Paint,x: Double,y: Double,w: Double,h: Double,r: Double,hex: String,alpha: Double=1.0) { p.shader=null;p.style=Paint.Style.FILL;p.color=color(hex);p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt();c.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),p) }
private fun text(c: NativeCanvas,p: Paint,label: String,x: Double,y: Double,size: Float,hex: String="#FFFFFF",alpha: Double=1.0,bold: Boolean=false,center: Boolean=false,tracking: Float=0f) {
    p.shader=null;p.style=Paint.Style.FILL
    p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    p.textSize=size;p.color=color(hex);p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt()
    if(tracking==0f) {
        c.drawText(label,(x-if(center)p.measureText(label)/2 else 0f).toFloat(),y.toFloat(),p)
    } else {
        // Tracked labels contain ASCII, so each glyph can keep the reference's point spacing.
        val widths=label.map { p.measureText(it.toString()) }
        val width=widths.sum()+tracking*(label.length-1).coerceAtLeast(0)
        var at=(x-if(center)width/2 else 0f).toFloat()
        label.forEachIndexed { index,char -> c.drawText(char.toString(),at,y.toFloat(),p);at+=widths[index]+tracking }
    }
}
private fun activity(context: Context): Activity? = when(context) { is Activity -> context; is ContextWrapper -> activity(context.baseContext);else -> null }

@Composable
fun PortraitFilmstrip(autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced=rememberReducedMotion()
    val context=LocalContext.current
    val view=LocalView.current
    val haptics=LocalHapticFeedback.current
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var lastTouch by remember { mutableDoubleStateOf(-1.0) }
    var manual by remember { mutableDoubleStateOf(0.0) }
    var touching by remember { mutableStateOf(false) }
    var releaseVelocity by remember { mutableDoubleStateOf(0.0) }
    var tapDuration by remember { mutableDoubleStateOf(0.0) }
    val camera=remember { android.graphics.Camera().apply { setLocation(0f,0f,-850f/72f) } }
    val matrix=remember { android.graphics.Matrix() }
    val photos=remember(context) { listOf("portrait","mountain","forest").map { name -> context.assets.open("portraitfilmstrip/portraitfilmstrip_${name}.jpg").use { BitmapFactory.decodeStream(it) } } }
    DisposableEffect(view) {
        val controller=activity(context)?.window?.let { WindowCompat.getInsetsController(it,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(old!=null)controller?.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(reduced,previewTime) {
        if(reduced||previewTime!=null)return@LaunchedEffect
        val origin=SystemClock.uptimeMillis()
        while(isActive) { withFrameNanos { clock=(SystemClock.uptimeMillis()-origin)/1000.0 } }
    }
    fun phase(): Double {
        if(previewTime!=null)return previewTime%LOOP
        if(reduced)return 2.5
        val d=if(lastTouch<0) -1.0 else clock-lastTouch
        if(touching)return manual
        if(d>=0&&d<2.7)return (manual+min(tapDuration,d)+releaseVelocity*.16*(1-exp(-d/.16))).coerceIn(0.0,7.6)
        return if(autoPlay)(if(d>=2.7)d-2.7 else clock)%LOOP else 0.0
    }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription="PortraitFilmstrip. Drag to explore the motion or tap to continue." }.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val start=down.position
            val t=phase()
            manual=t;releaseVelocity=0.0;tapDuration=0.0;touching=true;lastTouch=clock
            var previousY=start.y;var previousTime=down.uptimeMillis
            var moved=false
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull { it.id==down.id }
                if(change!=null) {
                    val dy=(change.position.y-start.y)/(size.width/393f)
                    if(abs(dy)>8) { moved=true;manual=(t-dy/135).coerceIn(0.0,7.6);change.consume()
                        val dt=(change.uptimeMillis-previousTime)/1000.0
                        if(dt>0)releaseVelocity=(-(change.position.y-previousY)/(size.width/393f)/135/dt).coerceIn(-3.0,3.0)
                        previousY=change.position.y;previousTime=change.uptimeMillis }
                }
            } while(event.changes.any { it.pressed })
            if(!moved) { val next=t>3.75&&t<6.85;manual=if(next)4.85 else 1.05;tapDuration=if(next).6 else .8;releaseVelocity=0.0;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            touching=false;lastTouch=clock
        }
    }) {
        val t=phase()
        val c=drawContext.canvas.nativeCanvas
        val s=size.width/393f
        val h=size.height/s.toDouble()
        c.save();c.scale(s,s)
        val d=clock-lastTouch
        val weight=if(previewTime!=null||reduced||touching||lastTouch<0||d>=2.7)1.0 else 1-ease(2.0,2.7,d)
        val zoom=ease(1.05,1.85,t)*(1-ease(2.8,3.55,t))*weight
        val zoomNext=ease(4.85,5.45,t)*(1-ease(6.05,6.65,t))*weight
        val scroll=ease(3.75,4.55,t)*(1-ease(6.85,7.6,t))*weight
        rect(c,paint,0.0,0.0,393.0,h,0.0,"#10100F")
        val chrome=1-max(zoom,zoomNext)
        text(c,paint,"DRAG TO EXPLORE",196.5,h-47,9f,alpha=chrome*.35,center=true,tracking=2f)
        val titles=listOf("Quiet mornings","Beyond the ridge","A softer world")
        val order=(-1..2).sortedBy { it+2+20*(if(it==0)zoom else if(it==1)zoomNext else 0.0) }
        for(index in order) {
            val z=if(index==0)zoom else if(index==1)zoomNext else 0.0
            val w=271+122*z;val fh=530+(h-530)*z
            val x=(393-w)/2;val y=(160+index*550-550*scroll)*(1-z)
            val n=(index+3)%3
            val bend=sin(PI*z);val closing=if(index==0)t>2.8 else t>6.05
            val yaw=5.5*bend*(if(closing)-1 else 1)-3*sin(PI*scroll)*(1-z)
            val labelAlpha=if(index==0)1-ease(.15,.75,scroll) else if(index==1)ease(.25,.8,scroll) else 0.0
            c.save();camera.save();camera.rotateY(yaw.toFloat());camera.rotateX((-2*bend).toFloat());camera.getMatrix(matrix);camera.restore()
            matrix.preTranslate(-(x+w/2).toFloat(),-(y+fh/2).toFloat());matrix.postTranslate((x+w/2).toFloat(),(y+fh/2).toFloat());c.concat(matrix)
            val clip=Path().apply { addRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+fh).toFloat()),(30*(1-z)).toFloat(),(30*(1-z)).toFloat(),Path.Direction.CW) }
            c.save();c.clipPath(clip)
            val bitmap=photos[n];val scale=max(w/bitmap.width,fh/bitmap.height)*(1+36*bend/w)
            val dw=bitmap.width*scale;val dh=bitmap.height*scale
            paint.shader=null;paint.alpha=255
            c.drawBitmap(bitmap,null,RectF((x+(w-dw)/2-9*bend).toFloat(),(y+(fh-dh)/2).toFloat(),(x+(w+dw)/2-9*bend).toFloat(),(y+(fh+dh)/2).toFloat()),paint)
            paint.shader=LinearGradient(0f,(y+fh*.5).toFloat(),0f,(y+fh).toFloat(),intArrayOf(0,0x99000000.toInt()),null,Shader.TileMode.CLAMP);paint.alpha=255;c.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+fh).toFloat(),paint);paint.shader=null
            if(labelAlpha>0) {
                text(c,paint,"JOURNAL / 0${n+1}",x+20,y+fh-53,9f,alpha=.7*labelAlpha,tracking=1.9f)
                text(c,paint,titles[n],x+20,y+fh-28,18f,alpha=labelAlpha,bold=true)
                rect(c,paint,x+w-46,y+fh-56,28.0,28.0,14.0,"#FFFFFF",labelAlpha)
                text(c,paint,"↗",x+w-32,y+fh-35,18f,"#000000",alpha=labelAlpha,center=true)
            }
            c.restore()
            paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=.7f
            paint.color=android.graphics.Color.WHITE;paint.alpha=(.12*255).toInt()
            c.drawPath(clip,paint)
            paint.style=Paint.Style.FILL;paint.alpha=255
            c.restore()
        }
        text(c,paint,"FIELD NOTES",25.0,154.0,12f,alpha=chrome*.64,tracking=2.2f)
        text(c,paint,"01 / 03",313.0,154.0,11f,alpha=chrome*.64*(1-ease(.3,.7,scroll)))
        text(c,paint,"02 / 03",313.0,154.0,11f,alpha=chrome*.64*ease(.3,.7,scroll))
        c.restore()
    }
}
