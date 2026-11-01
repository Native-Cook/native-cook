package com.ngocdevv.nativecook.blocks.charts.sproutstats

import android.provider.Settings
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
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
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import android.graphics.Typeface
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlin.math.*

@Composable
fun SproutStats(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current.density
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val resolver=LocalFontFamilyResolver.current
    val boldFamily=remember(context) { FontFamily(Font("sproutstats/sproutstats_Nunito-ExtraBold.ttf", context.assets)) }
    val blackFamily=remember(context) { FontFamily(Font("sproutstats/sproutstats_Nunito-Black.ttf", context.assets)) }
    val bold=resolver.resolve(boldFamily).value as Typeface
    val black=resolver.resolve(blackFamily).value as Typeface
    val art=remember(bold,black) { SproutStatsArt(bold,black) }
    val driver=remember { SproutStatsDriver { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } }
    val frame=remember { mutableStateOf(SproutStatsMotion.Frame()) }
    val wake=remember { Channel<Unit>(Channel.CONFLATED) }
    val insets=ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars())
    val top=(insets?.top?:0)/density.toDouble()
    val bottom=(insets?.bottom?:0)/density.toDouble()
    fun mode(to: Double) { driver.mode(to); wake.trySend(Unit) }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(previous!=null)controller.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(autoPlay,reduced,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(true) {
                if(reduced && !driver.moving) { wake.receive(); previous=0L }
                withFrameNanos { nanos ->
                    val wall=min(0.5,max(0.0,if(previous==0L)0.016 else (nanos-previous)/1e9)); previous=nanos
                    frame.value=driver.step(wall,autoPlay,reduced)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().semantics {
            val d=if(frame.value.scrub>=0)floor(frame.value.scrub+0.5).toInt() else -1
            liveRegion=LiveRegionMode.Polite
            contentDescription="THIS WEEK. Mindful minutes. "+(if(d>=0)"${SproutStatsMotion.days[d]} · ${SproutStatsMotion.minutes[d]} min" else "128 min · 18% more than last week")+". 5 day streak. 30 best day · Sat. 18 daily average."
        }) {
            // Snapshot state is consumed in the draw phase, not in whole-screen composition.
            val sample=frame.value
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas
                c.save(); c.scale(density,density)
                art.draw(c,size.width/density.toDouble(),size.height/density.toDouble(),top,bottom,sample,reduced)
                c.restore()
            }
        }
        Row(Modifier.offset(28.dp,(top+art.segmentY+4).dp).width(204.dp).height(32.dp)) {
            for(i in 0..1)Box(Modifier.weight(1f).fillMaxHeight().semantics {
                contentDescription=if(i==0)"Garden" else "Chart"
                role=Role.Tab
                selected=(i==1)==(frame.value.m>0.5)
                onClick { mode(i.toDouble()); true }
            }.pointerInput(i) {
                awaitEachGesture {
                    val down=awaitFirstDown(); down.consume()
                    var inside=true
                    do {
                        val e=awaitPointerEvent()
                        val change=e.changes.firstOrNull { it.id==down.id }?:break
                        inside=change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()
                        change.consume()
                    }while(change.pressed)
                    if(inside)mode(i.toDouble())
                }
            })
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height=maxHeight.value
            Box(Modifier.offset(y=(round(height*0.7)-round(height*0.34)-60).dp)
                .fillMaxWidth().height((round(height*0.34)+100).dp)
                .semantics {
                    contentDescription="Mindful minutes this week: Monday 12, Tuesday 20, Wednesday 8, Thursday 25, Friday 18, Saturday 30, Sunday 15"
                    stateDescription="Drag up for the chart, sideways to read a day"
                }
                .pointerInput(driver) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        val start=down.position
                        val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,start)
                        var begun=false
                        try {
                            do {
                                val event=awaitPointerEvent()
                                val change=event.changes.firstOrNull { it.id==down.id }?:break
                                tracker.addPosition(change.uptimeMillis,change.position)
                                val delta=change.position-start
                                if(!begun && delta.getDistance()>viewConfiguration.touchSlop) {
                                    begun=true; driver.begin(delta.x/density.toDouble(),delta.y/density.toDouble()); wake.trySend(Unit)
                                }
                                if(begun) {
                                    driver.update(delta.y/density.toDouble(),change.position.x/density.toDouble(),(size.width/density.toDouble()-92)/6)
                                    change.consume()
                                }
                            }while(change.pressed)
                        }finally { if(begun)driver.end(tracker.calculateVelocity().y/density.toDouble()) }
                    }
                })
        }
    }
}
