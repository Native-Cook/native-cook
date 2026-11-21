package com.ngocdevv.nativecook.blocks.pickers.louvreventthermostat

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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

/** A physical half-degree temperature picker, with the same nine-second louvre/chamber scene as the RN block. */
@Composable
fun LouvreVentThermostat(autoPlay:Boolean=true,onChange:((Double)->Unit)?=null,onCommit:((Double)->Unit)?=null) {
    val context=LocalContext.current; val view=LocalView.current; val activity=LocalActivity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle; val density=LocalDensity.current.density
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(activity,view) {
        val controller=activity?.window?.let { WindowCompat.getInsetsController(it,view) }
        val status=controller?.isAppearanceLightStatusBars; val nav=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=false; controller?.isAppearanceLightNavigationBars=false
        onDispose { status?.let { controller.isAppearanceLightStatusBars=it }; nav?.let { controller.isAppearanceLightNavigationBars=it } }
    }
    val change by rememberUpdatedState(onChange); val commit by rememberUpdatedState(onCommit)
    val engine=remember {
        LouvreVentThermostatEngine().apply {
            val debug=(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
            manual=debug&&activity?.intent?.getBooleanExtra("louvreVentThermostatManual",false)==true
            pin=if(debug)activity?.intent?.getStringExtra("louvreVentThermostatTime")?.toDoubleOrNull() else null
            onHaptic={ kind -> view.performHapticFeedback(when(kind){ 0->HapticFeedbackConstants.CLOCK_TICK; 1->HapticFeedbackConstants.KEYBOARD_TAP; else->HapticFeedbackConstants.LONG_PRESS }) }
        }
    }
    SideEffect { engine.onChange={change?.invoke(it)}; engine.onCommit={commit?.invoke(it)} }
    var scene by remember { mutableStateOf(engine.scene) }
    val renderer=remember(context) { LouvreVentThermostatRenderer(context) }
    LaunchedEffect(autoPlay,reduced,lifecycle) {
        engine.configure(autoPlay,reduced); scene=engine.scene
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=withFrameNanos { it }
            while(true) { val now=withFrameNanos { it }; engine.advance(((now-previous)/1e9).coerceIn(0.0,.05)); previous=now; scene=engine.scene }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale=min(maxWidth.value/402f,maxHeight.value/822f); val pixels=scale*density
        val offsetX=(maxWidth.value-402*scale)/2; val offsetY=max(0f,(maxHeight.value/scale-874)/2)
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription="Set temperature"
            stateDescription=String.format(java.util.Locale.US,"%.1f degrees Celsius",scene.temperature)
            progressBarRangeInfo=ProgressBarRangeInfo(scene.temperature.toFloat().coerceIn(16f,32f),16f..32f,31)
            setProgress { requested -> engine.adjust((round(requested*2)/2-scene.temperature).toDouble()); true }
            customActions=listOf(CustomAccessibilityAction("Increase temperature") { engine.adjust(.5); true },CustomAccessibilityAction("Decrease temperature") { engine.adjust(-.5); true })
        }.pointerInput(pixels,offsetX,offsetY,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(false)
                fun x(value:Float)=value/pixels-offsetX/scale
                fun y(value:Float)=value/pixels-offsetY
                engine.begin(y(down.position.y).toDouble())
                val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                var dragged=false; var completed=false
                try {
                    do {
                        val event=awaitPointerEvent(); val p=event.changes.firstOrNull { it.id==down.id }?:break
                        tracker.addPosition(p.uptimeMillis,p.position)
                        val translation=(p.position.y-down.position.y)/pixels
                        if(abs(translation)>4)dragged=true
                        if(dragged&&p.pressed) { engine.drag(translation.toDouble()); p.consume() }
                        if(!p.pressed) {
                            completed=true
                            if(dragged)engine.end(tracker.calculateVelocity().y/pixels.toDouble()) else engine.tap(x(p.position.x).toDouble(),y(p.position.y).toDouble())
                            break
                        }
                    } while(true)
                } finally { if(!completed)engine.end(0.0,true) }
            }
        }) {
            val c=drawContext.canvas.nativeCanvas; val save=c.save()
            c.translate(offsetX*density,0f); c.scale(pixels,pixels); c.translate(0f,offsetY)
            renderer.draw(c,scene,reduced,-offsetX/scale,-offsetY,size.width/pixels,size.height/pixels)
            c.restoreToCount(save)
        }
    }
}
