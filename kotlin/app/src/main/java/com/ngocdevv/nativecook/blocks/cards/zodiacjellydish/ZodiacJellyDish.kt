package com.ngocdevv.nativecook.blocks.cards.zodiacjellydish

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun ZodiacJellyDish(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    val resources = remember(context,density.density) { ZodiacJellyDishResources(context,density.density) }
    val audio = remember(context,reduced) { if (reduced) null else ZodiacJellyDishAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val driver = remember { ZodiacJellyDishMotion.Driver() }
    val revision = remember { mutableIntStateOf(0) }
    val uiClock = remember { doubleArrayOf(0.0) }
    var wake by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(density)/density.density
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density
    fun buzz(success: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (success) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if (success) 24 else 12,45))
    }
    fun add() {
        driver.addedAt = driver.clock; driver.last = driver.clock
        if (!reduced) driver.jelly.sv += 1.6
        audio?.play("add"); buzz(true); wake = true; revision.intValue++
    }
    fun save() { audio?.play("tap"); buzz(false) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) controller.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(driver,lifecycle,autoPlay,reduced,wake) {
        if ((!autoPlay || reduced) && !wake) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            var idle = 0.0
            while (true) {
                withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos-previous)/1e9,0.05)
                    previous = nanos; uiClock[0] += dt
                    driver.step(dt,autoPlay,reduced); revision.intValue++
                    if (!driver.held && ((!reduced && driver.clock-driver.last >= 2) || reduced)) idle += dt else idle = 0.0
                }
                if ((!autoPlay || reduced) && idle > 6) { wake = false; break }
            }
        }
    }
    // RN uses system text without Material tracking and disables font scaling for every label.
    CompositionLocalProvider(
        LocalDensity provides Density(density.density,1f),
        LocalTextStyle provides TextStyle.Default,
    ) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth/density.density; val h = constraints.maxHeight/density.density
        val buyY = h-bottom-121f
        val gestures = Modifier.pointerInput(driver,reduced,w,h,top,bottom) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false)
                val start = down.position/density.density
                val buyHit = abs(start.x-w/2) <= 142.5 && start.y >= buyY && start.y <= buyY+46
                val saveHit = abs(start.x-w/2) <= 142.5 && start.y >= buyY+61 && start.y <= buyY+107
                val cx = w/2-2; val cy = top+293
                val slabHit = abs(start.x-cx) <= 159.5 && abs(start.y-cy) <= 225.5
                val hit = if (buyHit) 2 else if (saveHit) 3 else if (slabHit) 1 else 0
                val j = driver.jelly
                if (hit == 1) {
                    j.gx = (start.x-cx).toDouble(); j.gy = (start.y-cy).toDouble()
                    j.hold = true; j.tx = j.px; j.ty = j.py; driver.held = true; driver.last = driver.clock
                } else if (hit == 2) driver.buy.set(1.0,uiClock[0],reduced)
                else if (hit == 3) driver.save.set(1.0,uiClock[0],reduced)
                if (hit != 0) { wake = true; revision.intValue++ }
                var end = start
                var tapValid = true
                var released = false
                var elapsed = 0L
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position/density.density
                        elapsed = change.uptimeMillis-down.uptimeMillis
                        val dx = end.x-start.x; val dy = end.y-start.y
                        if (abs(dx) > 10 || abs(dy) > 10 || elapsed > 2000) tapValid = false
                        if (hit == 1 && change.pressed) {
                            val distance = hypot(dx.toDouble(),dy.toDouble())
                            val k = if (distance > 0) 70*tanh(distance/90)/distance else 0.0
                            j.tx = dx*k*(if (reduced) 0.3 else 1.0); j.ty = dy*k*(if (reduced) 0.3 else 1.0)
                            driver.last = driver.clock
                        }
                        released = !change.pressed
                        change.consume()
                    } while (!released)
                } finally {
                    if (hit == 1) {
                        j.hold = false; driver.held = false; driver.last = driver.clock
                        if (hypot(j.px,j.py)>12) { audio?.play("squish"); buzz(false) }
                    } else if (hit == 2 || hit == 3) {
                        if (hit == 2) driver.buy.set(0.0,uiClock[0],reduced) else driver.save.set(0.0,uiClock[0],reduced)
                        if (released && tapValid && elapsed <= 2000) { if (hit == 2) add() else save() }
                    }
                    revision.intValue++
                }
            }
        }
        Box(Modifier.fillMaxSize().then(gestures)) {
            Canvas(Modifier.fillMaxSize()) {
                revision.intValue
                drawIntoCanvas { resources.draw(it.nativeCanvas,w,h,top,driver) }
            }
            Box(Modifier.offset { IntOffset(((w-54)*density.density).roundToInt(),((top+26)*density.density).roundToInt()) }
                .size(36.dp).background(Color(0xff24252c),CircleShape)
                .border((1/density.density).dp,Color.White.copy(alpha=0.18f),CircleShape)
                .semantics { role = Role.Button; contentDescription = "Close" },contentAlignment=Alignment.Center) {
                Text("×",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Light,lineHeight=26.sp,modifier=Modifier.offset(y=(-1).dp))
            }
            Text("Virgos, don't let anyone crush your raspberries — keep them safe inside.",
                color=Color.White.copy(alpha=0.78f),fontSize=13.5.sp,lineHeight=19.sp,textAlign=TextAlign.Center,
                modifier=Modifier.offset(x=40.dp,y=(h-bottom-174).dp).width((w-80).dp))
            Box(Modifier.offset(x=((w-285)/2).dp,y=buyY.dp).size(285.dp,46.dp)
                .graphicsLayer { revision.intValue; val s = driver.buyScale(uiClock[0]); scaleX=s; scaleY=s }
                .background(Color(0xfff26a1b),CircleShape)
                .clearAndSetSemantics { role = Role.Button; contentDescription = "+ $3.20 $4.00. Added to your order"; onClick { add(); true } },contentAlignment=Alignment.Center) {
                Row(Modifier.graphicsLayer { revision.intValue; val a = driver.added.toFloat(); alpha=1-a; translationY=-a*10*density.density },verticalAlignment=Alignment.CenterVertically) {
                    Text("+",fontSize=22.sp,fontWeight=FontWeight.Light,color=Color.White,modifier=Modifier.offset(y=(-1).dp).padding(end=8.dp))
                    Text("$3.20",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                    Text("$4.00",fontSize=15.sp,fontWeight=FontWeight.Medium,color=Color.White.copy(alpha=0.7f),textDecoration=TextDecoration.LineThrough,modifier=Modifier.padding(start=8.dp))
                }
                Text("Added to your order",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Color.White,
                    modifier=Modifier.graphicsLayer { revision.intValue; val a = driver.added.toFloat(); alpha=a; translationY=(1-a)*10*density.density })
            }
            Box(Modifier.offset(x=((w-285)/2).dp,y=(buyY+61).dp).size(285.dp,46.dp)
                .graphicsLayer { revision.intValue; val s = (1-driver.save.value(uiClock[0])*0.04).toFloat(); scaleX=s; scaleY=s }
                .background(Color(0xff2a2b31),CircleShape).border((1/density.density).dp,Color.White.copy(alpha=0.12f),CircleShape)
                .clearAndSetSemantics { role = Role.Button; contentDescription = "Save for later"; onClick { save(); true } },contentAlignment=Alignment.Center) {
                Text("Save for later",fontSize=16.sp,fontWeight=FontWeight.Medium,color=Color.White)
            }
        }
      }
    }
}
