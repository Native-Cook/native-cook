package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** A single liquid island: hold to listen, pull the drop, pluck its line, tap to answer, swipe the reply. */
@Composable
fun IslandGlassOrb(
    autoPlay: Boolean = true,
    level: Float? = null,
    reply: String = "Two events tomorrow. First at 6:00.",
    replyHoldMs: Long = 6000,
    onListen: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onCancel: () -> Unit = {},
    onReplyEnd: () -> Unit = {},
) {
    val context=LocalContext.current; val view=LocalView.current
    val activity=LocalActivity.current
    val window=activity?.window
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    // Like RN's hidden StatusBar, the screen draws its own bars so the glass can pass over them.
    val topInset=remember(view) { (ViewCompat.getRootWindowInsets(view)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top?:0)/density.density.toDouble() }
    DisposableEffect(window,view) {
        val controller=window?.let { WindowCompat.getInsetsController(it,view) }
        val wasVisible=ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.statusBars())?:true
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { if(wasVisible)controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    val playback=remember { IslandGlassOrbPlayback(autoPlay,reduced) }
    val pin=remember(activity) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) null
        else activity?.intent?.getStringExtra("islandGlassOrbTime")?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    LaunchedEffect(playback) { playback.pin=pin }
    val tick=remember { mutableLongStateOf(0L) }
    SideEffect {
        playback.level=level?.toDouble(); playback.holdSeconds=replyHoldMs/1000.0
        playback.onListen=onListen; playback.onSubmit=onSubmit; playback.onCancel=onCancel; playback.onReplyEnd=onReplyEnd
        playback.buzz={ kind -> view.performHapticFeedback(if(kind=="sel")HapticFeedbackConstants.CLOCK_TICK else if(kind=="soft")HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.KEYBOARD_TAP) }
    }
    LaunchedEffect(autoPlay,reduced) { playback.configure(autoPlay,reduced) }
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription="Nori assistant. Hold the island to listen. Drag the glass drop or pluck its spectrum line. Tap to submit. Swipe up on the answer to dismiss." }) {
        val unit=maxWidth.value/402.0; val logicalH=maxHeight.value/unit
        playback.height=logicalH
        val ppp=density.density*unit
        val oy=if(topInset>=54 || topInset==0.0)0.0 else topInset+4-11
        val page by produceState<IslandGlassOrbPage?>(null,context,logicalH,ppp) {
            value=withContext(Dispatchers.Default) { IslandGlassOrbPage(context.assets,logicalH,ppp) }
        }
        val renderer=remember(page,reply) { page?.let { IslandGlassOrbRenderer(it,reply) } }
        LaunchedEffect(playback,renderer,lifecycle) {
            if(renderer==null)return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while(true) {
                    var previous=withFrameNanos { it }
                    do {
                        val frame=withFrameNanos { it }; playback.advance((frame-previous)/1e9); previous=frame; tick.longValue=frame
                    } while(playback.needsFrames())
                    playback.wake.receive()
                }
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(playback,ppp,oy) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                val start=down.position
                playback.down(start.x/ppp,start.y/ppp,oy)
                var released=false
                try {
                    do {
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        val pointer=event.changes.firstOrNull { it.id==down.id }?:break
                        tracker.addPosition(pointer.uptimeMillis,pointer.position)
                        val delta=pointer.position-start
                        playback.move(pointer.position.x/ppp,pointer.position.y/ppp,delta.x/ppp,delta.y/ppp,hypot(delta.x.toDouble(),delta.y.toDouble())/density.density)
                        pointer.consume()
                        if(event.changes.none { it.pressed })released=true
                    } while(event.changes.any { it.pressed })
                } finally {
                    val velocity=tracker.calculateVelocity()
                    playback.up(velocity.x/ppp,velocity.y/ppp,released)
                }
            }
        }) {
            tick.longValue
            val c=drawContext.canvas.nativeCanvas
            if(renderer==null)c.drawColor(android.graphics.Color.rgb(145,158,144))
            else {
                val save=c.save(); c.scale(ppp.toFloat(),ppp.toFloat())
                renderer.draw(c,playback.frame(),oy,playback.lobeTime())
                c.restoreToCount(save)
            }
        }
    }
}
