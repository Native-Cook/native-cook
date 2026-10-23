package com.ngocdevv.nativecook.blocks.progress.orbitimport

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas as NativeCanvas
import android.graphics.BlurMaskFilter
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

const val LOOP = 10.0

private fun ease(a: Double, b: Double, t: Double): Double { val x=((t-a)/(b-a)).coerceIn(0.0,1.0);return x*x*x*(10+x*(-15+6*x)) }
private fun sphere(t: Double,weight: Double): List<Triple<Double,Double,Double>> {
    val ay=(t-1.4)*1.8;val ax=.45+(t-1.4)*.65;val radius=72*ease(1.4,1.9,t)*(1-ease(4.05,4.45,t))*weight
    return (0..<48).map { i ->
        val seed=i*2.39996323;val by=1-2*(i+.5)/48;val ring=sqrt(max(0.0,1-by*by));val bx=cos(seed)*ring;val bz=sin(seed)*ring
        val rx=bx*cos(ay)+bz*sin(ay);val rz=-bx*sin(ay)+bz*cos(ay);val ry=by*cos(ax)-rz*sin(ax);val z=by*sin(ax)+rz*cos(ax);val perspective=240/(240-z*72)
        Triple(196+rx*radius*perspective,365+ry*radius*.85*perspective,z)
    }.sortedBy { it.third }
}
private fun color(hex: String) = android.graphics.Color.parseColor(hex)
private fun rect(c: NativeCanvas,p: Paint,x: Double,y: Double,w: Double,h: Double,r: Double,hex: String,alpha: Double=1.0) { p.shader=null;p.style=Paint.Style.FILL;p.color=color(hex);p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt();c.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),p) }
private fun text(c: NativeCanvas,p: Paint,label: String,x: Double,y: Double,size: Float,hex: String="#FFFFFF",alpha: Double=1.0,bold: Boolean=false,center: Boolean=false) { p.shader=null;p.style=Paint.Style.FILL;p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);p.textSize=size;p.color=color(hex);p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt();c.drawText(label,(x-if(center)p.measureText(label)/2 else 0f).toFloat(),y.toFloat(),p) }
private fun activity(context: Context): Activity? = when(context) { is Activity -> context; is ContextWrapper -> activity(context.baseContext);else -> null }

@Composable
fun OrbitImport(autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced=rememberReducedMotion()
    val context=LocalContext.current
    val view=LocalView.current
    val haptics=LocalHapticFeedback.current
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val haloPaint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;maskFilter=BlurMaskFilter(3.5f,BlurMaskFilter.Blur.NORMAL) } }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var lastTouch by remember { mutableDoubleStateOf(-1.0) }
    var manual by remember { mutableDoubleStateOf(0.0) }
    var touching by remember { mutableStateOf(false) }
    var releaseVelocity by remember { mutableDoubleStateOf(0.0) }
    var tapTarget by remember { mutableDoubleStateOf(-1.0) }
    val rearBlur=remember { BlurMaskFilter(1.4f,BlurMaskFilter.Blur.NORMAL) }
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
        if(reduced)return 8.2
        val d=if(lastTouch<0) -1.0 else clock-lastTouch
        if(touching)return manual
        if(d>=0&&d<2.7)return if(tapTarget>=0)manual+(tapTarget-manual)*ease(0.0,.7,d) else (manual+releaseVelocity*.16*(1-exp(-d/.16))).coerceIn(0.0,9.3)
        return if(autoPlay)(if(d>=2.7)d-2.7 else clock)%LOOP else 0.0
    }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription="OrbitImport. Drag to explore the motion or tap to continue." }.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val start=down.position
            val t=phase()
            manual=t;releaseVelocity=0.0;tapTarget=-1.0;touching=true;lastTouch=clock
            var previousY=start.y;var previousTime=down.uptimeMillis
            var moved=false
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull { it.id==down.id }
                if(change!=null) {
                    val dy=(change.position.y-start.y)/(size.width/393f)
                    if(abs(dy)>8) { moved=true;manual=(t-dy/75).coerceIn(0.0,9.2);change.consume()
                        val dt=(change.uptimeMillis-previousTime)/1000.0
                        if(dt>0)releaseVelocity=(-(change.position.y-previousY)/(size.width/393f)/75/dt).coerceIn(-3.0,3.0)
                        previousY=change.position.y;previousTime=change.uptimeMillis }
                }
            } while(event.changes.any { it.pressed })
            if(!moved) { tapTarget=if(t<1.5)2.4 else if(t<4.5)5.8 else if(t<6.8)8.1 else 9.3;manual=t;releaseVelocity=0.0;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            touching=false;lastTouch=clock
        }
    }) {
        val t=phase()
        val c=drawContext.canvas.nativeCanvas
        val s=size.width/393f
        val h=size.height/s.toDouble()
        c.save();c.scale(s,s)
        val dt=clock-lastTouch;val weight=if(previewTime!=null||reduced||touching||lastTouch<0||dt>=2.7)1.0 else 1-ease(2.0,2.7,dt);val reset=1-weight;val slide=reset*(h+80)
        val a=max(1-ease(1.3,1.65,t)+ease(8.7,9.3,t),reset)
        val b=ease(1.4,1.9,t)*(1-ease(4.2,4.6,t))
        val l=ease(4.2,4.6,t)*(1-ease(6.65,7.15,t))
        val k=ease(6.4,6.8,t)*(1-ease(8.7,9.3,t))
        val headerExit=1-ease(0.0,.45,reset)
        val importHeader=max(1-ease(4.2,4.4,t)+ease(9.0,9.3,t),ease(.55,1.0,reset))
        val listHeader=ease(4.4,4.6,t)*(1-ease(6.45,6.65,t))*headerExit
        val calendarHeader=ease(6.65,6.85,t)*(1-ease(8.7,9.0,t))*headerExit
        rect(c,paint,0.0,0.0,393.0,h,0.0,"#242424")
        rect(c,paint,10.0,70.0,373.0,h-105,34.0,"#383838")
        rect(c,paint,26.0,88.0,34.0,34.0,17.0,"#434343")
        text(c,paint,"‹",43.0,113.0,26f,alpha=.8,center=true)
        text(c,paint,"Import from Folio",196.0,110.0,15f,alpha=.94*importHeader,bold=true,center=true)
        text(c,paint,"Subscriptions",196.0,110.0,15f,alpha=.94*listHeader,bold=true,center=true)
        text(c,paint,"October 2026",196.0,110.0,15f,alpha=.94*calendarHeader,bold=true,center=true)
        if(a>0){
            c.save();c.translate(0f,(-24*reset*(1-reset)).toFloat());c.save();c.rotate(-5f,196f,269f);rect(c,paint,155.0,228.0,82.0,82.0,21.0,"#141414",a);text(c,paint,"F",196.0,286.0,48f,alpha=a,bold=true,center=true);c.restore()
            rect(c,paint,218.0,288.0,34.0,34.0,17.0,"#242424",a);text(c,paint,"⟳",235.0,312.0,24f,"#CCB854",a,center=true)
            text(c,paint,"Step 1",196.0,378.0,13f,alpha=a*.74,bold=true,center=true)
            text(c,paint,"Enable public access",196.0,400.0,13f,alpha=a*.74,center=true)
            text(c,paint,"Share → General access → Anyone",196.0,421.0,12f,alpha=a*.48,center=true)
            text(c,paint,"Step 2",196.0,465.0,13f,alpha=a*.74,bold=true,center=true)
            text(c,paint,"Copy the link to your Folio page",196.0,487.0,13f,alpha=a*.74,center=true)
            button(c,paint,"Paste from Clipboard",h-183,a);text(c,paint,"Cancel",196.0,h-97,13f,alpha=a*.42,center=true);c.restore()
        }
        if(b>0){
            c.save();c.translate(0f,slide.toFloat())
            for(point in sphere(t,weight)) {
                val x=point.first;val y=point.second;val depth=(point.third+1)/2;val r=1+2.8*depth;val alpha=(.18+.8*depth)*b
                haloPaint.alpha=(alpha*.25*255).toInt();c.drawCircle(x.toFloat(),y.toFloat(),(r*2).toFloat(),haloPaint)
                paint.maskFilter=if(depth<.5)rearBlur else null;rect(c,paint,x-r,y-r,r*2,r*2,r,"#FFFFFF",alpha);paint.maskFilter=null
            }
            text(c,paint,"Processing",196.0,530.0,20f,alpha=b,bold=true,center=true);text(c,paint,"Analyzing with AI…",196.0,557.0,14f,alpha=b*.42,center=true)
            c.restore()
        }
        val names=listOf("Vela","Moss","Frame","Folio","Aster","Vault");val prices=listOf("$ 10.99","$ 12.00","$ 10.00","$ 120.00","$ 8.00","$ 35.00")
        if(l>0){
            text(c,paint,"6 subscriptions found",196.0,135.0+slide,11f,alpha=l*.4,center=true)
            for(i in 0..<6){
                val enter=ease(4.25+i*.045,4.65+i*.045,t);val exit=ease(6.65+i*.035,7+i*.035,t);val rowAlpha=l*enter*(1-exit);val y=170.0+i*76+24*(1-enter)+220*exit+slide;val check=if(i<3)ease(5+i*.4,5.3+i*.4,t) else 0.0
                rect(c,paint,30.0,y,333.0,68.0,12.0,"#414141",rowAlpha)
                rect(c,paint,42.0,y+24,20.0,20.0,10.0,"#656565",rowAlpha);rect(c,paint,43.0,y+25,18.0,18.0,9.0,"#414141",rowAlpha)
                if(check>0){rect(c,paint,42.0,y+24,20.0,20.0,10.0,"#FFFFFF",check*rowAlpha);text(c,paint,"✓",52.0,y+40,14f,"#383838",check*rowAlpha,true,true)}
                icon(c,paint,i,94.0,y+34,20.0,rowAlpha*(if(i<3)1-ease(6.3+i*.12,6.35+i*.12,t)else 1.0))
                text(c,paint,names[i],126.0,y+27,15f,alpha=rowAlpha,bold=true);text(c,paint,prices[i],126.0,y+48,12f,alpha=rowAlpha*.48)
                text(c,paint,"${4+i*3} Oct 2026",286.0,y+25,10f,alpha=rowAlpha*.5,center=true);text(c,paint,if(i==3||i==5)"Yearly" else "Monthly",310.0,y+49,9f,alpha=rowAlpha*.6,center=true)
            }
            val n=if(t>6.1)3 else if(t>5.7)2 else if(t>5.3)1 else 0
            button(c,paint,if(n==0)"Import Subscriptions" else "Import $n Subscription${if(n==1)"" else "s"}",h-123+slide,l*(1-k),n>0)
        }
        if(k>0){
            c.save();c.translate(0f,slide.toFloat())
            text(c,paint,"YOUR MONTH, IN VIEW",35.0,190.0,10f,alpha=k*.4)
            for(i in 0..<7)text(c,paint,listOf("M","T","W","T","F","S","S")[i],58.0+i*46,220.0,11f,alpha=k*.4,center=true)
            paint.shader=null;paint.color=android.graphics.Color.WHITE;paint.alpha=(k*.055*255).toInt();paint.strokeWidth=1f
            for(col in 0..7)c.drawLine(35f+col*46,238f,35f+col*46,628f,paint)
            for(row in 0..6)c.drawLine(35f,238f+row*65,357f,238f+row*65,paint)
            for(day in 1..30)text(c,paint,"$day",44.0+(day+2)%7*46,256.0+(day+2)/7*65,10f,alpha=k*.25)
            val route=Path().apply{moveTo(105f,320f);cubicTo(290f,290f,160f,430f,287f,451f);cubicTo(355f,495f,115f,510f,151f,568f)}
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.3f;paint.color=android.graphics.Color.WHITE;paint.alpha=(k*.3*255).toInt();paint.pathEffect=android.graphics.DashPathEffect(floatArrayOf(3f,5f),0f);val measure=android.graphics.PathMeasure(route,false);val revealed=Path();measure.getSegment(0f,(measure.length*ease(6.7,7.8,t)*weight).toFloat(),revealed,true);c.drawPath(revealed,paint);paint.pathEffect=null;paint.style=Paint.Style.FILL
            text(c,paint,"3 subscriptions added",196.0,h-132,13f,alpha=k*.72*ease(7.6,7.95,t),center=true);button(c,paint,"Import complete",h-123,k*ease(7.6,7.95,t))
            c.restore()
        }
        for(i in 0..2) {
            val delay=i*.12;val v=ease(6.35+delay,7.45+delay,t)*weight;val q=1-v;val tx=listOf(105.0,287.0,151.0)[i];val ty=listOf(320.0,451.0,568.0)[i];val sy=204+i*76.0
            val x=q*q*q*94+3*q*q*v*(50+i*60)+3*q*v*v*(tx+80)+v*v*v*tx
            val y=q*q*q*sy+3*q*q*v*(sy+90)+3*q*v*v*(ty-80)+v*v*v*ty+slide;val land=ease(7.35+delay,7.8+delay,t)
            c.save();c.rotate((6*sin(PI*v)).toFloat(),x.toFloat(),y.toFloat());icon(c,paint,i,x,y,(20-2*v)*(1+.08*sin(PI*land)),ease(6.3+delay,6.35+delay,t)*(1-ease(8.7,9.3,t)));c.restore()
        }
        c.restore()
    }
}

private val colors=listOf("#BF3847","#33E673","#F73352","#EBE8D6","#9E99DB","#C2A161")
private fun icon(c: NativeCanvas,p: Paint,i: Int,x: Double,y: Double,r: Double,alpha: Double=1.0){rect(c,p,x-r,y-r,r*2,r*2,r,colors[i],alpha);text(c,p,listOf("V","m","▶","F","+","◈")[i],x,y+7,22f,if(i==3)"#000000" else "#FFFFFF",alpha,true,true)}
private fun button(c: NativeCanvas,p: Paint,label: String,y: Double,alpha: Double,light: Boolean=true){
    val ink=if(light)"#000000" else "#FFFFFF"
    rect(c,p,36.0,y,321.0,52.0,12.0,if(light)"#FAFAF5" else "#454545",alpha)
    p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textSize=14f
    val x=196.0-(p.measureText(label).toDouble()+23)/2
    val path=Path().apply { moveTo((x+7).toFloat(),(y+16).toFloat());lineTo((x+7).toFloat(),(y+27).toFloat());moveTo((x+3).toFloat(),(y+23).toFloat());lineTo((x+7).toFloat(),(y+27).toFloat());lineTo((x+11).toFloat(),(y+23).toFloat());moveTo(x.toFloat(),(y+25).toFloat());lineTo(x.toFloat(),(y+32).toFloat());lineTo((x+14).toFloat(),(y+32).toFloat());lineTo((x+14).toFloat(),(y+25).toFloat()) }
    p.color=color(ink);p.alpha=(alpha*255).toInt();p.style=Paint.Style.STROKE;p.strokeWidth=1.6f;c.drawPath(path,p);p.style=Paint.Style.FILL
    text(c,p,label,x+23,y+31,14f,ink,alpha,true)
}
