package com.ngocdevv.nativecook.blocks.cards.sunrisecocktailpour

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

private val SunriseCocktailPourCtas=listOf("Pour Grenadine","Stir","Serve")
private val SunriseCocktailPourNotes=listOf("Pour slowly down the side. It sinks.","Or leave it: the sunrise is the point.","Garnish with an orange wheel.")
private val SunriseCocktailPourLabels=listOf("A highball of orange juice on ice","A tequila sunrise, red settling under orange","A stirred tequila sunrise")

@Composable
fun SunriseCocktailPour(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val haptic=LocalHapticFeedback.current
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val auto=autoPlay && !reduced
    val driver=remember(auto,reduced) { SunriseCocktailPourDriver(auto) { success-> haptic.performHapticFeedback(if(success) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) } }
    val art=remember { SunriseCocktailPourArt() }
    val frame=remember(driver) { mutableStateOf(driver.f.copy()) }
    val noteClock=remember(driver) { mutableDoubleStateOf(0.0) }
    var step by remember(driver) { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(0) }
    val fonts=remember(context) {
        listOf("InstrumentSerif-Regular","Inter-Regular","Inter-Medium","Inter-SemiBold").map {
            FontFamily(Font("sunrisecocktailpour/sunrisecocktailpour_$it.ttf",context.assets))
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null) controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,wake) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(!reduced || driver.running) withFrameNanos { nanos->
                val dt=if(previous==0L) 0.0 else min(0.5,(nanos-previous)/1e9)
                previous=nanos
                frame.value=driver.tick(dt,auto,reduced)
                noteClock.doubleValue=driver.now
                val next=min(2,driver.f.step)
                if(next!=step) step=next
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF100D0B))) {
        val l=SunriseCocktailPourLayout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom)
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas->
                val c=canvas.nativeCanvas
                c.save();c.scale(density.density,density.density)
                art.draw(c,l,frame.value,density.density);c.restore()
            }
        }
        Box(Modifier.offset((l.heroX-l.heroS*0.9).dp,(l.heroY-l.heroS*1.2).dp)
            .size((l.heroS*1.8).dp,(l.heroS*2.5).dp)
            .semantics { contentDescription=SunriseCocktailPourLabels[step] }
            .pointerInput(driver,l,reduced) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    driver.takeOver();wake++
                    val start=down.position; var started=false
                    val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,start)
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        tracker.addPosition(change.uptimeMillis,change.position)
                        val delta=(change.position-start)/density.density
                        if(!started && delta.getDistance()>=6f) { started=true; driver.stirring=true }
                        if(started && change.pressed) {
                            driver.drag(delta.x.toDouble(),delta.y.toDouble(),(tracker.calculateVelocity().x/density.density).toDouble(),l.heroS,reduced)
                            change.consume()
                        }
                        if(!change.pressed) break
                    } while(event.changes.any { it.pressed })
                    driver.end()
                }
            })
        SunriseCocktailPourButton(Modifier.offset(8.dp,l.top.dp).size(44.dp),reduced,"Back",{}) {
            Canvas(Modifier.size(12.dp,20.dp)) {
                val p=androidx.compose.ui.graphics.Path().apply { moveTo(10.dp.toPx(),2.dp.toPx());lineTo(2.dp.toPx(),10.dp.toPx());lineTo(10.dp.toPx(),18.dp.toPx()) }
                drawPath(p,Color(0xFFF5EEE4),style=Stroke(2.2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        Text("$14",Modifier.offset(0.dp,l.top.dp).fillMaxWidth().height(44.dp).wrapContentHeight().padding(end=16.dp).semantics { contentDescription="14 dollars" },color=Color(0xFFC9A46A),fontFamily=fonts[3],fontSize=17.sp,textAlign=androidx.compose.ui.text.style.TextAlign.End)
        fun textModifier(y: Double,height: Int)=Modifier.offset(0.dp,y.dp).fillMaxWidth().height(height.dp).wrapContentHeight()
        Text("NO. 07 · HOUSE CLASSIC",textModifier(l.eyebrowTop,16),color=Color(0xFFC9A46A),fontFamily=fonts[2],fontSize=12.sp,lineHeight=16.sp,letterSpacing=1.4.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text("Tequila Sunrise",textModifier(l.nameTop,44).semantics { heading() },color=Color(0xFFF5EEE4),fontFamily=fonts[0],fontSize=40.sp,lineHeight=44.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text("Blanco tequila, fresh orange, grenadine",textModifier(l.recipeTop,20),color=Color(0xFF9A8E80),fontFamily=fonts[1],fontSize=15.sp,lineHeight=20.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        SunriseCocktailPourNotes.forEachIndexed { i,note->
            Text(note,Modifier.offset(24.dp,l.noteTop.dp).width((l.w-48).dp).height(20.dp).graphicsLayer {
                noteClock.doubleValue // invalidate only the copy layer, including reduced-motion fades
                val n=driver.note(i,reduced);alpha=n.first.toFloat();translationY=(n.second*density.density).toFloat()
            }.semantics { if(i!=step) hideFromAccessibility() else liveRegion=LiveRegionMode.Polite },color=Color(0xFFC9A46A),fontFamily=fonts[2],fontSize=14.sp,lineHeight=20.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        SunriseCocktailPourButton(Modifier.offset(16.dp,l.ctaTop.dp).size((l.w-32).dp,56.dp),reduced,SunriseCocktailPourCtas[step],{
            driver.next(reduced);frame.value=driver.f.copy();step=min(2,driver.f.step);wake++
        },Color(0xFFF5EEE4)) {
            Text(SunriseCocktailPourCtas[step],color=Color(0xFF14100C),fontFamily=fonts[3],fontSize=17.sp)
        }
        Canvas(Modifier.fillMaxSize()) {
            val f=frame.value
            val center=Offset((l.w/2*density.density).toFloat(),((l.ctaTop+28)*density.density).toFloat())
            val radius=(22*(1-0.12*f.fingerPress)*density.density).toFloat()
            drawCircle(Color(0xFFF5EEE4).copy(alpha=(0.14*f.finger).toFloat()),radius,center)
            drawCircle(Color(0xFFF5EEE4).copy(alpha=(0.5*f.finger).toFloat()),radius,center,style=Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun SunriseCocktailPourButton(modifier: Modifier,reduced: Boolean,label: String,onClick: ()->Unit,surface: Color?=null,content: @Composable ()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val amount by animateFloatAsState(if(pressed) 1f else 0f,tween(150,easing=CubicBezierEasing(0.23f,1f,0.32f,1f)),label="press")
    Box(modifier.graphicsLayer {
        scaleX=if(reduced) 1f else 1f-0.04f*amount;scaleY=scaleX
        alpha=if(reduced) 1f-0.28f*amount else 1f
    }.clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick).semantics { contentDescription=label },contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxSize().then(if(surface!=null) Modifier.background(surface,CircleShape) else Modifier),contentAlignment=Alignment.Center) { content() }
    }
}
