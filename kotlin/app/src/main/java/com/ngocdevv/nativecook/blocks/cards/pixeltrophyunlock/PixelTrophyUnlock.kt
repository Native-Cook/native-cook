package com.ngocdevv.nativecook.blocks.cards.pixeltrophyunlock

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
fun PixelTrophyUnlock(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val art = remember(context) { PixelTrophyUnlockArt(context) }
    val driver = remember { PixelTrophyUnlockDriver() }
    val audio = remember(context,reduced) { if(reduced)null else PixelTrophyUnlockAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    var tick by remember { mutableIntStateOf(0) }
    var interaction by remember { mutableIntStateOf(0) }
    var earned by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(density)/density.density
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density
    fun buzz(kind: Int) {
        if(Build.VERSION.SDK_INT>=29) vibrator?.vibrate(VibrationEffect.createPredefined(if(kind==1)VibrationEffect.EFFECT_HEAVY_CLICK else if(kind==2)VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if(kind==1)18 else 12,if(kind==1)80 else 45))
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if(old!=null)controller?.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(lifecycle,autoPlay,reduced,interaction) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while(autoPlay && !reduced || driver.moving()) {
                withFrameNanos { frame ->
                    val dt = if(previous==0L)0.016 else ((frame-previous)/1e9).coerceIn(0.0,0.5)
                    previous = frame
                    driver.step(dt,autoPlay && !reduced,reduced,{ audio?.play(it) },::buzz)
                    if(earned != driver.channels.earned)earned = driver.channels.earned
                    tick++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        Canvas(Modifier.fillMaxSize().pointerInput(reduced,top,width) {
            detectTapGestures { point ->
                val x = point.x/density.density;val y = point.y/density.density
                val gap = (width-368)/3
                val index = (0..7).firstOrNull { i ->
                    val xx = 24+(i%4)*(80+gap);val yy = top+444+(i/4)*156
                    x>xx && x<xx+80 && y>yy && y<yy+98
                }
                driver.tap(index,{ audio?.play(it) },::buzz);interaction++
            }
        }) {
            tick // Invalidation is confined to drawing; no screen-wide frame recomposition.
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save();c.scale(density.density,density.density)
                art.draw(c,size.width/density.density,size.height/density.density,top,bottom,driver.channels,driver.shake,driver.now-driver.shakeAt)
                c.restore()
            }
        }
        Box(Modifier.offset(y=(top+26).dp).fillMaxWidth().height(40.dp).semantics { heading();contentDescription = "Achievements" })
        for(i in 0..7) {
            val x = 14+(i%4)*(80+(width-368)/3)
            val y = top+444+(i/4)*156+106
            Box(Modifier.offset(x=x.dp,y=y.dp).size(100.dp,32.dp).semantics {
                role = Role.Button
                contentDescription = "${PixelTrophyUnlockMotion.names[i]}, ${if(i==0 || i==1 && earned)"earned" else "locked"}"
            })
        }
        Box(Modifier.offset(x=(width/2-45).dp,y=(height-bottom-54).dp).size(90.dp,40.dp).semantics { role = Role.Button;contentDescription = "Close" })
    }
}
