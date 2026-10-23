package com.ngocdevv.nativecook.blocks.lists.contactfilestack

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

const val LOOP = 9.0

private fun ease(a: Double, b: Double, t: Double): Double { val x=((t-a)/(b-a)).coerceIn(0.0,1.0);return x*x*x*(10+x*(-15+6*x)) }
private fun spring(u: Double) = u+.4*sin(PI*u)*sin(PI*u/2)
private fun color(hex: String) = android.graphics.Color.parseColor(hex)
private fun rect(c: NativeCanvas,p: Paint,x: Double,y: Double,w: Double,h: Double,r: Double,hex: String,alpha: Double=1.0) { p.shader=null;p.style=Paint.Style.FILL;p.color=color(hex);p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt();c.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),p) }
private fun text(c: NativeCanvas,p: Paint,label: String,x: Double,y: Double,size: Float,hex: String="#FFFFFF",alpha: Double=1.0,bold: Boolean=false,center: Boolean=false) { p.shader=null;p.style=Paint.Style.FILL;p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);p.textSize=size;p.color=color(hex);p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt();c.drawText(label,(x-if(center)p.measureText(label)/2 else 0f).toFloat(),y.toFloat(),p) }
private fun activity(context: Context): Activity? = when(context) { is Activity -> context; is ContextWrapper -> activity(context.baseContext);else -> null }

@Composable
fun ContactFileStack(autoPlay: Boolean = false, previewTime: Double? = null) {
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
    var tapRun by remember { mutableStateOf(false) }
    val camera=remember { android.graphics.Camera().apply { setLocation(0f,0f,-850f/72f) } }
    val matrix=remember { android.graphics.Matrix() }
    DisposableEffect(view) {
        val controller=activity(context)?.window?.let { WindowCompat.getInsetsController(it,view) }
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(old!=null)controller?.isAppearanceLightStatusBars=old }
    }
    LaunchedEffect(reduced,previewTime) {
        if(reduced||previewTime!=null)return@LaunchedEffect
        val origin=SystemClock.uptimeMillis()
        while(isActive) { withFrameNanos { clock=(SystemClock.uptimeMillis()-origin)/1000.0 } }
    }
    fun phase(): Double {
        if(previewTime!=null)return previewTime%LOOP
        if(reduced)return 5.0
        val d=if(lastTouch<0) -1.0 else clock-lastTouch
        if(touching)return manual
        if(d>=0&&d<2.7)return (manual+if(tapRun)min(3.4,d*3)else releaseVelocity*.16*(1-exp(-d/.16))).coerceIn(0.0,8.3)
        return if(autoPlay)(if(d>=2.7)d-2.7 else clock)%LOOP else 0.0
    }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription="ContactFileStack. Drag to explore the motion or tap to continue." }.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val start=down.position
            val t=phase()
            manual=t;releaseVelocity=0.0;tapRun=false;touching=true;lastTouch=clock
            var previousY=start.y;var previousTime=down.uptimeMillis
            var moved=false
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull { it.id==down.id }
                if(change!=null) {
                    val dy=(change.position.y-start.y)/(size.width/393f)
                    if(abs(dy)>8) { moved=true;manual=(t-dy/90).coerceIn(0.0,8.3);change.consume()
                        val dt=(change.uptimeMillis-previousTime)/1000.0
                        if(dt>0)releaseVelocity=(-(change.position.y-previousY)/(size.width/393f)/90/dt).coerceIn(-3.0,3.0)
                        previousY=change.position.y;previousTime=change.uptimeMillis }
                }
            } while(event.changes.any { it.pressed })
            if(!moved) { manual=1.3;tapRun=true;releaseVelocity=0.0;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            touching=false;lastTouch=clock
        }
    }) {
        val t=phase()
        val c=drawContext.canvas.nativeCanvas
        val s=size.width/393f
        val h=size.height/s.toDouble()
        c.save();c.scale(s,s)
        val dt=clock-lastTouch
        val weight=if(previewTime!=null||reduced||touching||lastTouch<0||dt>=2.7)1.0 else 1-ease(2.0,2.7,dt)
        val compressed=ease(1.3,2.1,t)*(1-ease(7.8,8.4,t))*weight
        val open=ease(2.2,3.0,t)*(1-ease(7.0,7.8,t))*weight
        val drop=ease(3.7,4.4,t)*(1-ease(5.8,6.5,t))*weight
        val stackY=240+240*compressed;val stride=39-17*compressed
        rect(c,paint,0.0,0.0,393.0,h,0.0,"#D0D0C5")
        text(c,paint,"contact files",25.0,98.0,14f,"#11120E",bold=true)
        val names=listOf("Ari m.","Arden p.","Asa m.","Aster n.","Bryn","Bowie p.","Caro m.","Cedar p.","Cleo m.","Cove p.","Dara l.","Demi p.","Drew n.","Eden r.")
        fun selected() {
            val x=20+2*open;val y=(stackY+4*stride)*(1-open)+338*open
            c.save();c.translate(x.toFloat(),y.toFloat());c.saveLayerAlpha(-400f,-400f,800f,1500f,(open*255).toInt())
            c.save();c.translate(0f,(312*drop).toFloat());folder(c,paint,"Bryn",null,349.0,138+107*open,1.0);c.restore()
            for(i in 2 downTo 0){
                val delay=i*.055;val lift=spring(ease(3+delay,3.85+delay,t))*(1-ease(6.05+delay,6.95+delay,t))*weight
                val fan=ease(3.35+delay,4.1+delay,t)*(1-ease(6.05+delay,6.95+delay,t))*weight
                c.save();c.translate((62+i*5+i*18*fan).toFloat(),(32-140*lift+i*7-i*12*fan).toFloat());c.rotate((-2+(-10+i*8.5)*fan).toFloat(),116f,63f)
                paint.setShadowLayer(16f,0f,12f,android.graphics.Color.argb((.16*255).toInt(),0,0,0))
                rect(c,paint,0.0,0.0,232.0,126.0,0.0,"#FAF9F2")
                paint.clearShadowLayer()
                text(c,paint,"Bryn Hale",15.0,28.0,15f,"#151510",1.0,true)
                text(c,paint,if(i==0)"Creative direction" else if(i==1)"A note for later" else "Studio / 04",15.0,46.0,10f,"#151510",.6)
                text(c,paint,"bryn@quiet-studio.example",15.0,111.0,9f,"#151510",.5)
                rect(c,paint,184.0,14.0,30.0,30.0,0.0,if(i==1)"#ED7338" else "#5C1FDE")
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=android.graphics.Color.WHITE;paint.alpha=(.4*255).toInt();c.drawRect(189f,19f,209f,39f,paint);paint.style=Paint.Style.FILL
                c.restore()
            }
            val frontY=28+35*open+drop*312
            c.save();camera.save();camera.rotateX((22*open*(1-drop)).toFloat());camera.getMatrix(matrix);camera.restore();matrix.preTranslate(-174.5f,-frontY.toFloat());matrix.postTranslate(174.5f,frontY.toFloat());c.concat(matrix)
            rect(c,paint,0.0,28+35*open+drop*312,349.0,110+70*open,12.0,"#C3C4B9")
            paint.style=Paint.Style.STROKE;paint.color=color("#85867C");paint.alpha=255;paint.strokeWidth=.9f;c.drawRoundRect(RectF(0f,(28+35*open+drop*312).toFloat(),349f,(138+105*open+drop*312).toFloat()),12f,12f,paint);paint.style=Paint.Style.FILL
            c.restore();c.restore();c.restore()
        }
        for(i in names.indices){
            val hide=ease(2.2+i*.025,2.9+i*.025,t)*(1-ease(7.0,7.8,t))*weight
            c.save();c.translate(20f,(stackY+i*stride+if(i==4)0.0 else 180*hide).toFloat())
            folder(c,paint,names[i],when(i){0->"A";4->"B";6->"C";10->"D";else->null},353.0,138.0,if(i==4)1-open else 1-hide)
            c.restore()
        }
        if(open>0)selected()
        for(i in 0..1){
            val delay=i*.07;val prints=ease(3.7+delay,4.5+delay,t)*(1-ease(5.8+delay,6.5+delay,t))*weight
            c.save();c.translate((230+i*19).toFloat(),(540-85*spring(prints)+i*21).toFloat());c.scale((.82+.18*prints).toFloat(),(.82+.18*prints).toFloat(),45f,55f);c.rotate(((if(i==0)12.0 else -10.0)+(if(i==0)16 else -16)*(1-prints)).toFloat(),45f,55f)
            rect(c,paint,0.0,0.0,90.0,110.0,0.0,"#FCFCF5",prints)
            paint.shader=LinearGradient(0f,6f,0f,84f,intArrayOf(color("#4D8091"),color("#C7C49E")),null,Shader.TileMode.CLAMP);paint.alpha=(prints*255).toInt();c.drawRect(6f,6f,84f,84f,paint);paint.shader=null
            val mountain=Path().apply{moveTo(6f,70f);lineTo(32f,36f);lineTo(48f,60f);lineTo(64f,44f);lineTo(84f,78f);close()};paint.color=color("#334033");paint.alpha=(prints*255).toInt();c.drawPath(mountain,paint);c.restore()
        }
        text(c,paint,"14 PEOPLE / ONE PLACE",25.0,h-44,9f,"#11120E",(1-open)*.35)
        c.restore()
    }
}

private fun folder(c: NativeCanvas,p: Paint,name: String,letter: String?,w: Double,h: Double,alpha: Double){
    if(letter!=null)rect(c,p,w-95,13.0,95.0,45.0,6.0,"#11120E",alpha)
    val path=Path().apply{moveTo(0f,16f);quadTo(0f,0f,12f,4f);lineTo(80f,4f);lineTo(112f,28f);lineTo((w-12).toFloat(),28f);quadTo(w.toFloat(),28f,w.toFloat(),40f);lineTo((w-8).toFloat(),(h-10).toFloat());quadTo((w-8).toFloat(),h.toFloat(),(w-20).toFloat(),h.toFloat());lineTo(20f,h.toFloat());quadTo(8f,h.toFloat(),8f,(h-10).toFloat());close()}
    p.shader=null;p.style=Paint.Style.FILL;p.color=color("#C3C4B9");p.alpha=(alpha*255).toInt();c.drawPath(path,p)
    p.style=Paint.Style.STROKE;p.color=color("#85867C");p.alpha=(alpha*255).toInt();p.strokeWidth=.9f;c.drawPath(path,p);p.style=Paint.Style.FILL
    text(c,p,name,10.0,23.0,13f,"#11120E",alpha)
    if(letter!=null)text(c,p,letter,w-43,26.0,12f,"#D0D0C5",alpha)
}
