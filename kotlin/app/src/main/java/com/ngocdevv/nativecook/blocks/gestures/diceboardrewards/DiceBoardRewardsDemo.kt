package com.ngocdevv.nativecook.blocks.gestures.diceboardrewards

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun DiceBoardRewardsDemo() { DiceBoardRewards(autoPlay=true) }

@Composable
fun DiceBoardRewards(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art=remember(context) { DiceBoardRewardsArt(context) }
    val driver=remember(autoPlay,reduced) { DiceBoardRewardsDriver() }
    val tick=remember { mutableDoubleStateOf(0.0) }
    var generation by remember { mutableIntStateOf(0) }
    var rolls by remember { mutableIntStateOf(3) }
    var pressed by remember { mutableStateOf(false) }
    var prize by remember { mutableStateOf("") }
    val vibrator=remember(context) { context.getSystemService(Vibrator::class.java) }
    val buzz: (Int)->Unit=remember(vibrator) { { kind ->
        if(Build.VERSION.SDK_INT>=29)vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==0)VibrationEffect.EFFECT_TICK else if(kind==1)VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_DOUBLE_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==1)18 else 12,if(kind==1)80 else 45))
    } }
    fun roll() { if(driver.throwDie(buzz)) { rolls=driver.rolls;generation++ } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(before!=null)controller?.isAppearanceLightStatusBars=before }
    }
    LaunchedEffect(driver,lifecycle,generation) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            do {
                withFrameNanos {
                    driver.sample(autoPlay && !reduced,buzz)
                    val index=DiceBoardRewardsMotion.toastIndex(driver.out)
                    val next=if(index>=0)DiceBoardRewardsMotion.prizes[index] else ""
                    if(prize!=next)prize=next
                    tick.doubleValue=driver.now
                }
            } while(driver.moving(autoPlay && !reduced))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w=maxWidth.value.toDouble();val h=maxHeight.value.toDouble()
        val top=with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toDouble() }
        val bottom=with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value.toDouble() }
        val g=remember(w,h,top,bottom) { DiceBoardRewardsArt.Geo(w,h,top,bottom) }
        Canvas(Modifier.fillMaxSize().pointerInput(driver,g) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val x=start.x/density.density;val y=start.y/density.density
                val button=x>=g.w-20-art.rollWidth() && x<=g.w-20 && y>=g.h-g.bottomInset-72 && y<=g.h-g.bottomInset-16
                val tray=abs(x-g.cx)<=80 && y>=g.trayY-70 && y<=g.trayY+50
                if(!button && !tray)return@awaitEachGesture
                if(button)pressed=true
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,start)
                down.consume();var end=start;var cancelled=false
                try {
                    do {
                        val e=awaitPointerEvent();val change=e.changes.firstOrNull { it.id==down.id }
                        if(change==null) { cancelled=true;break }
                        end=change.position;tracker.addPosition(change.uptimeMillis,end);change.consume()
                        if(button) {
                            val ex=end.x/density.density;val ey=end.y/density.density
                            pressed=ex>=g.w-40-art.rollWidth() && ex<=g.w && ey>=g.h-g.bottomInset-92 && ey<=g.h-g.bottomInset+4
                        }
                    } while(change.pressed)
                    val dy=(end.y-start.y)/density.density;val vy=tracker.calculateVelocity().y/density.density
                    if(!cancelled && ((tray && (dy < -30 || vy < -500)) || (button && pressed)))roll()
                } finally { pressed=false }
            }
        }) {
            tick.doubleValue
            drawIntoCanvas { c ->
                val native=c.nativeCanvas;native.save();native.scale(density.density,density.density)
                art.draw(native,g,driver.out,reduced,rolls,pressed);native.restore()
            }
        }
        Box(Modifier.offset(16.dp,(top+8).dp).size(44.dp).semantics { contentDescription="Close";role=Role.Button;onClick { true } })
        Box(Modifier.offset((g.cx-80).dp,(g.trayY-70).dp).size(160.dp,120.dp).semantics { contentDescription="Die. Flick up to roll";role=Role.Button;onClick { roll();true } })
        Box(Modifier.offset((g.w-20-art.rollWidth()).dp,(g.h-g.bottomInset-72).dp).size(art.rollWidth().dp,56.dp).semantics { contentDescription="Roll";role=Role.Button;onClick { roll();true } })
        Box(Modifier.offset((g.cx-60).dp,(top+8).dp).size(140.dp,44.dp).semantics { contentDescription="Lucky Lap";heading() })
        Box(Modifier.offset((g.w-110).dp,(top+8).dp).size(94.dp,44.dp).semantics { contentDescription="2,480 coins" })
        Box(Modifier.offset(24.dp,(top+62).dp).size((g.w-48).dp,24.dp).semantics { contentDescription="Land on a coupon to win it for your basket" })
        Box(Modifier.offset(20.dp,(g.h-g.bottomInset-60).dp).size(180.dp,24.dp).semantics { contentDescription="$rolls rolls left today" })
        if(prize.isNotEmpty())Box(Modifier.offset(24.dp,(top+96).dp).size((g.w-48).dp,44.dp).semantics { contentDescription=prize;liveRegion=LiveRegionMode.Polite })
    }
}
