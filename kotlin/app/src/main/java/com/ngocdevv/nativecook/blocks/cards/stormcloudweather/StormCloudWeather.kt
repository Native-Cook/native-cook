package com.ngocdevv.nativecook.blocks.cards.stormcloudweather

import android.provider.Settings
import android.view.HapticFeedbackConstants
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.hypot

@Composable
fun StormCloudWeather(autoPlay:Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current.density
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val resources=remember(context){StormCloudWeatherResources(context)}
    val audio=remember(context){StormCloudWeatherAudio(context)}
    val art=remember(resources){StormCloudWeatherArt(resources)}
    val driver=remember(autoPlay,reduced){StormCloudWeatherDriver()}
    val frame=remember(driver){mutableStateOf(StormCloudWeatherMotion.Frame())}
    var invalidation by remember{mutableIntStateOf(0)}
    val top=WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val bottom=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val cue:(String)->Unit={if(!reduced)audio.play(it)}
    val buzz:()->Unit={view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);Unit}
    DisposableEffect(audio){onDispose{audio.close()}}
    LaunchedEffect(driver,lifecycle){
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            var previous=0L
            while(true)withFrameNanos{now->
                val dt=if(previous==0L).016 else ((now-previous)/1e9).coerceIn(0.0,.5)
                previous=now;frame.value=driver.step(dt,autoPlay,reduced,cue)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
        val width=constraints.maxWidth/density
        val sy=top+78
        val panel=sy+430
        val cw=(width-68)/3
        Canvas(Modifier.fillMaxSize().pointerInput(driver,width,top){
            awaitEachGesture{
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position/density
                val cardY=panel+57
                val card=(0..2).firstOrNull{i->start.x>=24+i*(cw+10)&&start.x<=24+i*(cw+10)+cw&&start.y>=cardY-(if(driver.to==i)6 else 0)&&start.y<=cardY+104}?:-1
                val turn=start.y in sy..(sy+350)
                if(turn)driver.begin()
                driver.pressed=card;invalidation++
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                var pointer=down
                do{
                    val event=awaitPointerEvent()
                    pointer=event.changes.firstOrNull{it.id==down.id}?:break
                    tracker.addPosition(pointer.uptimeMillis,pointer.position)
                    if(turn&&hypot((pointer.position.x-down.position.x)/density,(pointer.position.y-down.position.y)/density)>=4){driver.drag(((pointer.position.x-down.position.x)/density).toDouble());pointer.consume()}
                }while(pointer.pressed)
                if(turn)driver.end(tracker.calculateVelocity().x/density.toDouble())
                if(card>=0&&!pointer.isConsumed){
                    val end=pointer.position/density
                    if(end.x>=24+card*(cw+10)&&end.x<=24+card*(cw+10)+cw&&end.y>=cardY-6&&end.y<=cardY+104)driver.pick(card,reduced,cue,buzz)
                }
                driver.pressed= -1;invalidation++
            }
        }){
            invalidation
            drawIntoCanvas{canvas->
                val native=canvas.nativeCanvas
                native.save();native.scale(density,density)
                art.draw(native,size.width/density,size.height/density,top,bottom,frame.value,driver.pressed)
                native.restore()
            }
        }
        // Accessibility targets track exactly the same hour boxes as the native canvas.
        val current by remember{derivedStateOf{frame.value.to}}
        val degrees by remember{derivedStateOf{frame.value.number{it.temp}}}
        Box(Modifier.offset(y=sy.dp).fillMaxWidth().height(350.dp).semantics{
            role=Role.Image;contentDescription="${StormCloudWeatherMotion.copy[current].sky}, $degrees degrees"
        })
        for(i in 0..2){
            val cp=StormCloudWeatherMotion.copy[i]
            Box(Modifier.offset(x=(24+i*(cw+10)).dp,y=(panel+57-if(current==i)6 else 0).dp).size(cw.dp,104.dp).semantics{
                role=Role.Button;selected=current==i;contentDescription="${cp.label}, ${cp.temp} degrees, ${cp.sky}"
                onClick{driver.pick(i,reduced,cue,buzz);invalidation++;true}
            })
        }
    }
}
