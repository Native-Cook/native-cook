package com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder.GlassPlaylistFolderMotion as M

@Composable
fun GlassPlaylistFolder(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val d = remember(reduced) { M.Driver(reduced) }
    val art = remember(context) { GlassPlaylistFolderArt(context) }
    val audio = remember(context, reduced) { if (reduced) null else GlassPlaylistFolderAudio(context) }
    val tick = remember(d) { mutableIntStateOf(0) }
    var interacting by remember(d) { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
    fun buzz(kind: Int) { view.performHapticFeedback(if (kind == 2) HapticFeedbackConstants.CLOCK_TICK else if (kind == 1) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.VIRTUAL_KEY) }
    fun play() { audio?.play("tap"); buzz(1) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) controller?.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(d, lifecycle, autoPlay, interacting) {
        if ((!autoPlay || reduced) && !interacting) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) .016 else (nanos-previous)/1e9, .05)
                previous = nanos; d.step(dt, autoPlay && !reduced, reduced); tick.intValue++
                if (interacting && ((!autoPlay || reduced) && d.drag < 0 && d.now-d.last > 3 && abs(d.press) < .00001)) interacting = false
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF050806))) {
        val width = maxWidth.value.toDouble(); val cx = width/2; val cy = top+318
        Canvas(Modifier.fillMaxSize().pointerInput(d, reduced, cx, cy) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position/density.density
                if (abs(start.x-cx) > 170 || abs(start.y-cy) > 200) return@awaitEachGesture
                d.drag=d.s.open;d.last=d.now;interacting=true;tick.intValue++
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                var current=down; var dy=0.0;var dx=0.0
                do {
                    val event=awaitPointerEvent();current=event.changes.firstOrNull { it.id==down.id } ?: break
                    tracker.addPosition(current.uptimeMillis,current.position)
                    dx=(current.position.x-down.position.x)/density.density.toDouble()
                    dy=(current.position.y-down.position.y)/density.density.toDouble()
                    if(current.pressed) { d.drag=M.clamp(d.s.target-dy/160);d.last=d.now;current.consume() }
                } while(current.pressed)
                val amount=d.drag;d.drag=-1.0;d.last=d.now
                val p=current.position/density.density
                if(hypot(dx,dy)<=10) {
                    if(d.s.target==1.0 && p.y<cy-60) {
                        d.s.hovered=floor((p.x-(cx-120))/60).toInt().coerceIn(0,3)
                        d.s.liftTarget=if(d.s.liftTarget==1.0)0.0 else 1.0;buzz(2)
                    } else {
                        d.s.liftTarget=0.0;d.s.target(if(d.s.target==1.0)0.0 else 1.0,d.now)
                        audio?.play(if(d.s.target==1.0)"open" else "close");buzz(0)
                    }
                } else {
                    val vy=tracker.calculateVelocity().y/density.density.toDouble()
                    val target=if(amount>.4||vy < -500)1.0 else 0.0
                    d.s.ov=-vy/160;d.s.open=amount;d.s.liftTarget=0.0;d.s.target(target,d.now)
                    audio?.play(if(target==1.0)"open" else "close");buzz(0)
                }
                tick.intValue++
            }
        }) {
            tick.intValue
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas;c.save();c.scale(density.density,density.density)
                art.draw(c,size.width/density.density.toDouble(),size.height/density.density.toDouble(),top,d);c.restore()
            }
        }
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=(top+12).dp),verticalAlignment=Alignment.Bottom) {
            Text("Library",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp,modifier=Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text("Folders",color=Color(0x99C8FFD7),fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.alignByBaseline())
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start=24.dp,end=24.dp,bottom=(bottom+28).dp).graphicsLayer {
            tick.intValue;translationY=(M.clamp(d.s.open)*6*density.density).toFloat();alpha=(1-M.clamp(d.s.open)*.15).toFloat()
        }) {
            Text("FOLDER · 4 PLAYLISTS",color=Color(0xB396FFB4),fontSize=12.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.6.sp)
            Spacer(Modifier.height(6.dp))
            Text("Late Night Drive",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
            Spacer(Modifier.height(4.dp))
            Text("96 songs · 5 h 12 min · updated today",color=Color(0x8CFFFFFF),fontSize=15.sp)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.graphicsLayer { tick.intValue;scaleX=(1-d.press*.04).toFloat();scaleY=scaleX }
                .height(50.dp).background(Color(0xFF4CE07A),RoundedCornerShape(25.dp))
                .semantics { role=Role.Button;contentDescription="Play";onClick { play();true } }
                .pointerInput(d,reduced) {
                    awaitEachGesture {
                        val down=awaitFirstDown();val at=down.uptimeMillis
                        if (reduced) d.press=1.0 else d.press(1.0,.15,1.0);interacting=true
                        var success=true;var current=down
                        do {
                            val event=awaitPointerEvent();current=event.changes.firstOrNull { it.id==down.id } ?: break
                            if(current.position.x<0 || current.position.y<0 || current.position.x>size.width || current.position.y>size.height) success=false
                            current.consume()
                        } while(current.pressed)
                        if (reduced) d.press=0.0 else d.press(0.0,.32,.7);if(success && current.uptimeMillis-at<=2000)play()
                    }
                }.padding(horizontal=26.dp),contentAlignment=Alignment.Center) {
                Text("▶  Play all",color=Color(0xFF04170A),fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clearAndSetSemantics {})
            }
        }
    }
}
