package com.ngocdevv.nativecook.blocks.gestures.boxcatfeed

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
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun BoxCatFeed(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    val art = remember(context) { BoxCatFeedArt(context) }
    val driver = remember(autoPlay,reduced) { BoxCatFeedDriver() }
    var revision by remember { mutableIntStateOf(0) }
    val frame = remember(driver) { mutableStateOf(BoxCatFeedMotion.Frame()) }
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDouble()/density.density }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDouble()/density.density }
    fun pet() { driver.pet(); revision++; view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun flick() { if (driver.flick()) { revision++; view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (old != null) controller?.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(driver,revision,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val wall = if (last == 0L) 0.016 else ((nanos-last)/1e9).coerceIn(0.0,0.5)
                    last = nanos
                    frame.value = driver.step(wall,autoPlay && !reduced)
                }
                if ((!autoPlay || reduced) && driver.now-driver.at > 2.8 && kotlin.math.abs(driver.duckV) < 0.0001 && kotlin.math.abs(driver.duck) < 0.0001) break
            }
        }
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if ((!autoPlay || reduced) && driver.now-driver.at > 2.8) frame.value = frame.value.copy(blink = true)
                delay(140)
                if ((!autoPlay || reduced) && driver.now-driver.at > 2.8) frame.value = frame.value.copy(blink = false)
                delay(3260)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value.toDouble(); val h = maxHeight.value.toDouble()
        Canvas(Modifier.fillMaxSize()) {
            val out = frame.value
            drawIntoCanvas { target ->
                val cv = target.nativeCanvas
                cv.save(); cv.scale(density.density,density.density)
                art.draw(cv,w,h,top,bottom,out,reduced)
                cv.restore()
            }
        }
        Box(Modifier.offset(60.dp,((h*0.56).toInt()-(w*0.2).toInt()*1.3).dp).size((w-120).dp,((w*0.2).toInt()*2.4).dp)
            .semantics { contentDescription = "Mochi the cat. Tap to pet"; role = Role.Button; onClick { pet(); true } }
            .pointerInput(driver) { awaitEachGesture { val down = awaitFirstDown(); var valid = true; do { val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break; if (change.position.x < 0 || change.position.y < 0 || change.position.x > size.width || change.position.y > size.height) valid = false; if (!change.pressed) { if (valid && change.uptimeMillis-down.uptimeMillis <= 500) pet(); break } } while (true) } })
        Box(Modifier.offset((w-140).dp,(h-bottom-100).dp).size(110.dp,64.dp)
            .semantics { contentDescription = "Fish treat. Flick it up to Mochi"; role = Role.Button; onClick("Swipe up") { flick(); true } }
            .pointerInput(driver,reduced) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (frame.value.u >= 0 && frame.value.u < 1.4) return@awaitEachGesture
                    val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                    do {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis,change.position)
                        if (!change.pressed) {
                            val dy = (change.position.y-down.position.y)/density.density
                            val vy = tracker.calculateVelocity().y/density.density
                            if (dy < -24 || vy < -300) flick()
                            break
                        }
                        change.consume()
                    } while (true)
                }
            })
        Box(Modifier.offset((w-160).dp,(top+20).dp).size(140.dp,40.dp).semantics { contentDescription = "${1240+frame.value.count*10} points" })
    }
}
