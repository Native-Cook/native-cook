package com.ngocdevv.nativecook.blocks.cards.seasontiermedals

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlin.math.*

@Composable
fun SeasonTierMedals(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val top = WindowInsets.statusBars.getTop(LocalDensity.current) / density.toDouble()
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val driver = remember { SeasonTierMedalsDriver() }
    val art = remember(context) { SeasonTierMedalsArt(context) }
    var revision by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(1) }
    var wake by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val audio = remember(context,reduced) { if(reduced)null else SeasonTierMedalsAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    fun buzz(kind: Int) {
        if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(when(kind) {
            0 -> VibrationEffect.EFFECT_TICK; 2 -> VibrationEffect.EFFECT_HEAVY_CLICK; else -> VibrationEffect.EFFECT_CLICK
        })) else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==2)18 else 12,if(kind==2)80 else 45))
    }
    SideEffect {
        driver.audio = { audio?.play(it) }
        driver.buzz = { buzz(it) }
    }
    DisposableEffect(audio) { onDispose { driver.audio = null; audio?.close() } }
    DisposableEffect(art) { onDispose { art.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if(old!=null)controller?.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(driver,autoPlay,reduced,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while(true) {
                if((!autoPlay || reduced) && !driver.moving) {
                    val oldWake = wake
                    snapshotFlow { wake }.first { it != oldWake }
                    previous = 0L
                }
                withFrameNanos { nanos ->
                    val dt = min(if(previous==0L)0.016 else (nanos-previous)/1e9,0.05)
                    previous = nanos
                    driver.step(dt,autoPlay,reduced)
                    if(current!=driver.current)current = driver.current
                    revision++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toDouble()
        art.prepare(width,maxHeight.value.toDouble(),top,density)
        val my = top+248; val rc = min(84.0,width*0.21)
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,density,top) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false)
                val start = down.position/density
                val inRail = start.y >= my-rc*1.3 && start.y <= my+rc*1.3
                val inClaim = start.x>=32 && start.x<=width-32 && start.y>=art.claimY && start.y<=art.claimY+48
                if(!inRail && !inClaim)return@awaitEachGesture
                val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                var lastPoint = down.position
                if(inRail) { driver.begin(); wake++ } else pressed = true
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id==down.id } ?: break
                    lastPoint = change.position; tracker.addPosition(change.uptimeMillis,change.position)
                    if(inRail)driver.drag((change.position.x-down.position.x)/density.toDouble(),min(172.0,width*0.43))
                    else {
                        val point = change.position/density
                        pressed = point.x>=32 && point.x<=width-32 && point.y>=art.claimY && point.y<=art.claimY+48
                    }
                    change.consume()
                    if(!change.pressed)break
                } while(true)
                val end = lastPoint/density
                if(inRail) {
                    val velocity = tracker.calculateVelocity()
                    driver.end(end.x.toDouble(),end.y-(my-rc*1.3),(lastPoint.x-down.position.x)/density.toDouble(),velocity.x/density.toDouble(),width,my,reduced)
                    wake++
                } else { if(pressed)buzz(1); pressed=false }
            }
        }) {
            revision
            val isPressed = pressed
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.scale(density,density)
                art.draw(canvas.nativeCanvas,size.width/density.toDouble(),size.height/density.toDouble(),top,density,driver,isPressed)
                canvas.nativeCanvas.restore()
            }
        }
        fun Modifier.placed(x: Double,y: Double,w: Double,h: Double) = offset(x.dp,y.dp).size(w.dp,h.dp)
        Box(Modifier.placed(0.0,top-6,60.0,60.0).semantics { role=Role.Button; contentDescription="Back" })
        Box(Modifier.placed(width-60,top-6,60.0,60.0).semantics { role=Role.Button; contentDescription="How levels work" })
        Box(Modifier.placed(0.0,top+62,width,78.0).semantics { contentDescription="${SeasonTierMedalsTheme.kickers[current]}. ${SeasonTierMedalsTheme.names[current]}" })
        Box(Modifier.placed(0.0,my-rc*1.3,width,rc*2.6).semantics {
            contentDescription="Season level"
            stateDescription="${SeasonTierMedalsTheme.names[current].lowercase()}, level ${current+1} of 3"
            progressBarRangeInfo=ProgressBarRangeInfo(current.toFloat(),0f..2f,1)
            setProgress { value -> driver.adjust(SeasonTierMedalsMotion.round(value.toDouble())-driver.rail.target,reduced); wake++; true }
            customActions=listOf(CustomAccessibilityAction("Increment") { driver.adjust(1,reduced); wake++; true },CustomAccessibilityAction("Decrement") { driver.adjust(-1,reduced); wake++; true })
        })
        Box(Modifier.placed(24.0,top+354,width-48,140.0).semantics {
            contentDescription="${SeasonTierMedalsTheme.titles[current]}. ${SeasonTierMedalsTheme.nums[current]}. ${SeasonTierMedalsTheme.feet[current]}"
        })
        Box(Modifier.placed(0.0,top+532,width,32.0).semantics { contentDescription="Claim your rewards" })
        Box(Modifier.placed(16.0,top+578,width-32,66.0).semantics { contentDescription="7% back on clothing and shoes. Paid as bonus points with a Season card" })
        // The claim's layout follows font wrapping, just as its painted art does.
        Box(Modifier.placed(32.0,art.claimY,width-64,48.0).semantics {
            role=Role.Button; contentDescription="Claim 7% back on clothing and shoes"
            onClick { buzz(1); true }
        })
        Box(Modifier.placed(16.0,top+726,width-32,76.0).semantics { contentDescription="Double points at cafés. Every weekend until the season ends" })
    }
}
