package com.ngocdevv.nativecook.blocks.modals.starcoupontear

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun StarCouponTear(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val haptic=LocalHapticFeedback.current
    val lifecycle=LocalLifecycleOwner.current
    val driver=remember { StarCouponTearDriver() }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val medium=remember { StarCouponTearTheme.font(context,"Medium") }
    val semi=remember { StarCouponTearTheme.font(context,"SemiBold") }
    LaunchedEffect(autoPlay,reduced) { driver.autoPlay=autoPlay; driver.reduced=reduced; driver.manualStart=null; driver.clock=0.0; driver.action=0; driver.phaseOrigin=0.0; driver.hapticAt=Double.POSITIVE_INFINITY }
    LaunchedEffect(driver,lifecycle,reduced) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last=0L
            while (true) withFrameNanos { nanos ->
                val dt=if(last==0L) 0.0 else min(0.05,(nanos-last)/1e9)
                last=nanos
                // Reduced motion rests without repeatedly invalidating the drawing.
                if(!reduced || (driver.action==2 && driver.clock<driver.settleTime)) driver.clock+=dt
                if(driver.clock>=driver.hapticAt) {
                    driver.hapticAt=Double.POSITIVE_INFINITY
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize().background(StarCouponTearTheme.page)) {
        val w=maxWidth.value.toDouble(); val h=maxHeight.value.toDouble()
        val s=floor(w*0.3+0.5); val cy=top+floor((h-top-bottom)*0.43+0.5)
        val chipX=w-76; val chipY=top+30
        Row(Modifier.offset(20.dp,(top+10).dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.height(26.dp).background(StarCouponTearTheme.gold,CircleShape).padding(horizontal=10.dp),contentAlignment=Alignment.Center) {
                StarCouponTearText("Flash coupon",13,semi,StarCouponTearTheme.goldInk)
            }
            StarCouponTearText("Today only · 3 left",14,medium,StarCouponTearTheme.mute)
        }
        Row(Modifier.offset((chipX-56).dp,(chipY-20).dp).size(112.dp,40.dp).graphicsLayer {
            val u=driver.frame().chip
            val pulse=if(u>=1) 0.0 else exp(-u*5)*sin(u*24)
            scaleX=(1+0.1*pulse).toFloat(); scaleY=scaleX
        }.background(StarCouponTearTheme.pill,CircleShape).border(1.dp,StarCouponTearTheme.line,CircleShape)
            .semantics(mergeDescendants=true) { contentDescription="My coupons" },
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
            Canvas(Modifier.size(18.dp,14.dp)) {
                val p=Path().apply {
                    moveTo(2f,1f); lineTo(16f,1f); quadraticTo(17f,1f,17f,2f); lineTo(17f,5f)
                    cubicTo(14.33f,5f,14.33f,9f,17f,9f); lineTo(17f,12f); quadraticTo(17f,13f,16f,13f)
                    lineTo(2f,13f); quadraticTo(1f,13f,1f,12f); lineTo(1f,9f)
                    cubicTo(3.67f,9f,3.67f,5f,1f,5f); lineTo(1f,2f); quadraticTo(1f,1f,2f,1f); close()
                }
                p.transform(androidx.compose.ui.graphics.Matrix().apply { scale(size.width/18,size.height/14) })
                drawPath(p,StarCouponTearTheme.gold)
            }
            StarCouponTearText("Coupons",14,semi,Color.White,Modifier.clearAndSetSemantics {})
        }
        StarCouponTearText("+1",14,semi,StarCouponTearTheme.gold,Modifier.offset((chipX-20).dp,(chipY+18).dp).graphicsLayer {
            val u=driver.frame().chip
            alpha=sin(PI*u).toFloat().coerceIn(0f,1f); translationY=((8-18*u)*density.density).toFloat()
        }.semantics { liveRegion=LiveRegionMode.Polite })
        Text("Half price on your next order",Modifier.offset(24.dp,(top+64).dp).width((w-48).dp).semantics { heading() },
            color=Color.White,fontFamily=semi,fontSize=28.sp,lineHeight=34.sp,
            style=TextStyle(platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false)))
        StarCouponTearArt(driver,w,cy,chipX,chipY,s,reduced,Modifier.fillMaxSize())
        StarCouponTearText("Pull the star down to claim",15,medium,StarCouponTearTheme.mute,
            Modifier.offset(y=(cy+s*1.5).dp).fillMaxWidth().graphicsLayer { alpha=(1-driver.frame().tear).toFloat().coerceIn(0f,1f) },TextAlign.Center)
        Box(Modifier.fillMaxSize().pointerInput(driver,density.density) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                driver.begin()
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                var dy=0.0
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    tracker.addPosition(change.uptimeMillis,change.position)
                    dy=(change.position.y-down.position.y)/density.density.toDouble()
                    if(change.pressed) {
                        if(driver.drag(dy)) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        if(driver.dragging) change.consume()
                    }
                } while(event.changes.any { it.id==down.id && it.pressed })
                driver.end(dy,tracker.calculateVelocity().y/density.density.toDouble())
            }
        })
        Box(Modifier.offset(20.dp,(h-max(bottom,16.0)-8-56).dp).size((w-40).dp,56.dp)
            .background(StarCouponTearTheme.gold,RoundedCornerShape(28.dp))
            .clickable(role=Role.Button,onClickLabel="Claim coupon") { driver.takeOver(); driver.claim() },contentAlignment=Alignment.Center) {
            StarCouponTearText("Claim coupon",17,semi,StarCouponTearTheme.goldInk)
        }
        Box(Modifier.size(44.dp).graphicsLayer {
            val frame=driver.frame()
            alpha=(0.85*frame.finger).toFloat()
            translationX=((w/2-22)*density.density).toFloat()
            translationY=((cy+frame.pull*150*0.85-22)*density.density).toFloat()
        }.background(Color.White.copy(alpha=0.35f),CircleShape).border(1.5.dp,Color.White.copy(alpha=0.8f),CircleShape))
    }
}

@Composable
private fun StarCouponTearText(text: String,size: Int,font: FontFamily,color: Color,modifier: Modifier=Modifier,align: TextAlign=TextAlign.Start) {
    Text(text,modifier,color=color,fontSize=size.sp,fontFamily=font,textAlign=align,
        style=TextStyle(platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false)))
}

@Composable
private fun StarCouponTearArt(driver: StarCouponTearDriver,w: Double,cy: Double,chipX: Double,chipY: Double,s: Double,reduced: Boolean,modifier: Modifier) {
    val shader=remember { if(Build.VERSION.SDK_INT>=33) StarCouponTearRuntime() else null }
    Canvas(modifier) {
        val frame=driver.frame(); val phase=driver.phase()
        val f=frame.fly; val arc=sin(PI*f); val p=StarCouponTearMotion.backOut(frame.pop)
        val baseY=cy+frame.pull*36
        val x=w/2+(chipX-30-w/2)*f+30*arc
        val y=baseY+(chipY-baseY)*f-40*arc
        val radius=s*(1-0.85*f)*p
        if(radius<=0.0001) return@Canvas
        val opacity=1-StarCouponTearMotion.smooth(0.85,1.0,f)
        if(Build.VERSION.SDK_INT>=33 && shader!=null) {
            val turn=if(reduced) 0.2 else 0.2+0.25*sin(phase)*(1-frame.tear)+2*PI*f
            shader.update(floatArrayOf(x.toFloat(),y.toFloat(),radius.toFloat(),opacity.toFloat()),
                floatArrayOf(frame.tear.toFloat(),turn.toFloat(),0.1f,frame.pull.toFloat()),
                floatArrayOf((phase*3).toFloat(),(1-0.5*f).toFloat(),(p*(1-f)).toFloat(),density),density)
            drawIntoCanvas { shader.draw(it.nativeCanvas,size) }
        } else {
            // API 26–32: flat, local artwork retains the same geometry, palette and story.
            val cx=(x*density).toFloat(); val py=(y*density).toFloat(); val r=(radius*density).toFloat()
            val path=Path().apply {
                repeat(10) { i ->
                    val a=-PI/2+i*PI/5; val rr=r*if(i%2==0) 0.95 else 0.475
                    val px=cx+cos(a).toFloat()*rr.toFloat(); val yy=py+sin(a).toFloat()*rr.toFloat()
                    if(i==0) moveTo(px,yy) else lineTo(px,yy)
                }; close()
            }
            drawPath(path,StarCouponTearTheme.gold,alpha=((1-frame.tear)*opacity).toFloat().coerceIn(0f,1f))
            val a=(frame.tear*opacity).toFloat().coerceIn(0f,1f)
            drawRoundRect(Color(0xFFFF8C38),Offset(cx-r,py-r*0.6f),Size(r*2,r*1.2f),androidx.compose.ui.geometry.CornerRadius(r*0.14f),alpha=a)
            drawCircle(StarCouponTearTheme.page,r*0.17f,Offset(cx-r,py),alpha=a)
            drawCircle(StarCouponTearTheme.page,r*0.17f,Offset(cx+r,py),alpha=a)
            drawLine(Color(0xFFB36327),Offset(cx-r*0.55f,py-r*0.52f),Offset(cx-r*0.55f,py+r*0.52f),
                strokeWidth=max(1f,r*0.025f),alpha=a,
                pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(r/12,r/12)))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; alpha=(a*255).toInt(); textSize=r*0.7f; textAlign=Paint.Align.CENTER }
            drawIntoCanvas { it.nativeCanvas.drawText("%",cx+r*0.28f,py+r*0.24f,paint) }
        }
    }
}

@RequiresApi(33)
private class StarCouponTearRuntime {
    private val shader=RuntimeShader(StarCouponTearShader)
    private val paint=Paint().apply { shader=this@StarCouponTearRuntime.shader }
    fun update(obj: FloatArray,shape: FloatArray,live: FloatArray,density: Float) {
        shader.setFloatUniform("uObj",obj); shader.setFloatUniform("uShape",shape)
        shader.setFloatUniform("uLive",live); shader.setFloatUniform("uDensity",density)
    }
    fun draw(canvas: android.graphics.Canvas,size: Size) { canvas.drawRect(0f,0f,size.width,size.height,paint) }
}
