package com.ngocdevv.nativecook.blocks.pickers.monsteragepicker

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun MonsterAgePicker(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val titleFont=remember(context) { FontFamily(Font("monsteragepicker/monsteragepicker_TitanOne-Regular.ttf",context.assets)) }
    val blackFont=remember(context) { FontFamily(Font("monsteragepicker/monsteragepicker_Nunito-Black.ttf",context.assets)) }
    val driver=remember { MonsterAgePickerDriver() }
    val art=remember(context) { MonsterAgePickerArt(context) }
    val frame=remember { mutableStateOf(MonsterAgePickerMotion.Frame()) }
    var age by remember { mutableIntStateOf(5) }
    var chosen by remember { mutableIntStateOf(-1) }
    val pressed=remember { mutableStateOf(false) }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun chomp() { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); driver.chomp() }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null)controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,autoPlay,reduced,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true)withFrameNanos { nanos ->
                val wall=if(previous==0L)0.016 else ((nanos-previous)/1e9).coerceIn(0.0,0.5)
                previous=nanos
                frame.value=driver.step(wall,autoPlay,reduced,::tick)
                if(age!=driver.age)age=driver.age
                if(chosen!=driver.chosen)chosen=driver.chosen
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W=maxWidth.value.toDouble();val H=maxHeight.value.toDouble()
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,W,H,top,bottom) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/density.density
                val onButton=start.x>=24 && start.x<=W-24 && start.y>=H-bottom-78 && start.y<=H-bottom-20
                val onMouth=start.x>=24 && start.x<=W-24 && start.y>=round(H*0.5)-W*0.12 && start.y<=round(H*0.5)+W*0.38
                if(!onButton && !onMouth)return@awaitEachGesture
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                var end=start;var dragging=false;var tapEligible=true
                pressed.value=onButton
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    end=change.position/density.density
                    if(change.uptimeMillis-down.uptimeMillis>500 || hypot(end.x-start.x,end.y-start.y)>10)tapEligible=false
                    tracker.addPosition(change.uptimeMillis,change.position)
                    if(onMouth && change.pressed) {
                        if(!dragging && abs(end.x-start.x)>6) {
                            dragging=true;driver.drag=true;driver.scripted=false;driver.off0=driver.off
                        }
                        if(dragging)driver.pan((end.x-start.x).toDouble(),tracker.calculateVelocity().x/density.density.toDouble())
                    }
                    change.consume()
                }while(change.pressed)
                pressed.value=false
                if(onMouth) {
                    if(dragging)driver.release(tracker.calculateVelocity().x/density.density.toDouble())
                    else if(tapEligible)driver.tap(end.x.toDouble(),W)
                } else if(onButton && end.x>=24 && end.x<=W-24 && end.y>=H-bottom-78 && end.y<=H-bottom-20)chomp()
            }
        }) {
            val sample=frame.value;val selected=chosen;val push=pressed.value
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save();canvas.nativeCanvas.scale(density.density,density.density)
                art.draw(canvas.nativeCanvas,W,H,top,bottom,sample,selected,push)
                canvas.nativeCanvas.restore()
            }
        }
        Box(Modifier.offset(x=24.dp,y=(round(H*0.5)-W*0.12).dp).size((W-48).dp,(W*0.5).dp).semantics {
            contentDescription="Age";stateDescription="$age years old"
            progressBarRangeInfo=ProgressBarRangeInfo(age.toFloat(),3f..14f,10)
            setProgress { value -> driver.adjust(value.roundToInt()-driver.target.roundToInt()-3);true }
            customActions=listOf(CustomAccessibilityAction("Increment") { driver.adjust(1);true },CustomAccessibilityAction("Decrement") { driver.adjust(-1);true })
        })
        Box(Modifier.offset(x=24.dp,y=(H-bottom-78).dp).size((W-48).dp,58.dp).semantics {
            role=Role.Button;contentDescription="Continue with $age years old";onClick { chomp();true }
        })
        Box(Modifier.offset(y=(top+10).dp).fillMaxWidth().height(6.dp).semantics { contentDescription="Step 2 of 4" })
        Column(Modifier.fillMaxWidth().padding(top=(top+40).dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("How old is Mia?",fontFamily=titleFont,fontSize=32.sp,color=Color.White,modifier=Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(if(chosen>=0)"Yum! ${chosen+3} years old" else "Slide along the teeth",fontFamily=blackFont,fontSize=16.sp,color=Color.White.copy(alpha=0.75f))
        }
        Box(Modifier.offset(x=24.dp,y=(H-bottom-78).dp).size((W-48).dp,54.dp).graphicsLayer {
            val scale=if(pressed.value)0.96f else 1f;scaleX=scale;scaleY=scale
        },contentAlignment=Alignment.Center) {
            Text("Continue",fontFamily=blackFont,fontSize=18.sp,color=Color(0xFF3B1460),modifier=Modifier.clearAndSetSemantics {})
        }
    }
}
