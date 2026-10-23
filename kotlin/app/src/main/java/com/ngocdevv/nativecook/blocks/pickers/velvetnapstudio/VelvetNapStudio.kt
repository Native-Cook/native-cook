package com.ngocdevv.nativecook.blocks.pickers.velvetnapstudio

import android.database.ContentObserver
import android.graphics.Path
import android.graphics.Paint
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

/** Fixed folded velvet whose local nap retains and reverses the finger's direction. */
@Composable
fun VelvetNapStudio(autoPlay: Boolean=false,previewTime: Double?=null,modifier: Modifier=Modifier) {
    val context=LocalContext.current;val density=LocalDensity.current;val view=LocalView.current
    val resources=remember(context.applicationContext){VelvetNapStudioResources.load(context.applicationContext)}
    val painter=remember(resources){VelvetNapStudioPainter(resources)}
    val driver=remember{VelvetNapStudioDriver().apply{epoch=velvetNapStudioNow()}}
    val clock=remember{mutableDoubleStateOf(velvetNapStudioNow())}
    var revision by remember{mutableIntStateOf(0)}
    var visible by remember{mutableStateOf(true)}
    val reduced=rememberVelvetNapStudioReduced()
    val latestAuto=rememberUpdatedState(autoPlay);val latestReduced=rememberUpdatedState(reduced);val latestPreview=rememberUpdatedState(previewTime)
    val lifecycle=LocalLifecycleOwner.current
    val back=LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    VelvetNapStudioSystemBars()
    fun wake(reschedule: Boolean=true){clock.doubleValue=velvetNapStudioNow();if(reschedule)revision++}
    fun changed(reschedule: Boolean=true){painter.prepare(driver);wake(reschedule)}
    fun feedback(){view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)}
    fun press(value: Boolean){if(visible){driver.controlPressed(value,velvetNapStudioNow(),latestAuto.value,latestReduced.value);changed()}}
    fun choose(index: Int){if(visible){if(driver.choose(index,velvetNapStudioNow(),latestAuto.value,latestReduced.value))feedback();changed()}}
    fun capture(){driver.captureControl(velvetNapStudioNow(),latestAuto.value,latestReduced.value)}
    fun save(){if(visible){driver.save(velvetNapStudioNow(),latestAuto.value,latestReduced.value);feedback();changed()}}
    DisposableEffect(lifecycle,painter) {
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_STOP){visible=false;driver.clear(velvetNapStudioNow())}
            else if(event==Lifecycle.Event.ON_START){visible=true;driver.epoch=velvetNapStudioNow();driver.rendered=null}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer);driver.clear(velvetNapStudioNow());painter.close()}
    }
    LaunchedEffect(reduced,autoPlay,previewTime){driver.clear(velvetNapStudioNow());changed()}
    LaunchedEffect(autoPlay,reduced,previewTime,revision,visible){
        while(isActive&&visible){
            val now=velvetNapStudioNow()
            if(driver.complete(now,reduced))revision++
            clock.doubleValue=now
            if(driver.needsFrames(now,autoPlay,reduced,previewTime))withFrameNanos{clock.doubleValue=it/1_000_000_000.0}
            else{val deadline=driver.nextDeadline(now,reduced)?:break;delay(max(1L,((deadline-now)*1000).toLong()))}
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale=min(maxWidth.value/393f,maxHeight.value/852f);val pixels=scale*density.density
        val x=(maxWidth.value-393*scale)/2;val y=(maxHeight.value-852*scale)/2
        Canvas(Modifier.fillMaxSize()){
            val now=clock.doubleValue;val frame=driver.frame(now,autoPlay,reduced,previewTime);val tint=driver.tint(now)
            driver.rendered=VelvetNapStudioRendered(frame,tint,now)
            val c=drawContext.canvas.nativeCanvas;c.save();c.translate(x*density.density,y*density.density);c.scale(pixels,pixels)
            painter.draw(c,frame,tint,driver,now,reduced,maxHeight.value/scale);c.restore()
        }
        fun Modifier.frame(px: Float,py: Float,w: Float,h: Float)=offset((x+px*scale).dp,(y+py*scale).dp).size((w*scale).dp,(h*scale).dp)
        val selected=driver.selected
        val hit=remember{velvetNapStudioHitRegion()}
        Box(Modifier.frame(31f,109f,331f,298f).pointerInput(pixels,visible,reduced,previewTime){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                fun point(p: Offset)=VelvetNapStudioPoint(p.x/pixels-12.0,p.y/pixels-12.0)
                var last=point(down.position)
                if(!visible||latestPreview.value!=null||!hit.contains((last.x*4).roundToInt(),(last.y*4).roundToInt())){waitForUpOrCancellation();return@awaitEachGesture}
                val owner=down.id.value
                if(!driver.begin(owner,last,velvetNapStudioNow(),latestAuto.value,latestReduced.value)){waitForUpOrCancellation();return@awaitEachGesture}
                down.consume();changed();var normal=false
                try{
                    while(true){
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break
                        if(change.isConsumed)break
                        last=point(change.position)
                        val wasSettled=driver.session?.settled==true
                        if(driver.move(owner,last,velvetNapStudioNow(),latestReduced.value)){
                            // Restart only when a stopped held clock acquires a new finite90ms stamp.
                            changed(wasSettled&&driver.session?.settled==false)
                        }
                        change.consume();if(!change.pressed){normal=true;break}
                    }
                }finally{
                    if(driver.session?.id==owner){driver.finish(owner,last,velvetNapStudioNow(),latestReduced.value,!normal);changed()}
                }
            }
        }.semantics{
            contentDescription="Folded crushed velvet fabric"
            stateDescription="Directional pile, ${VelvetNapStudioDriver.names[selected]}"
            customActions=listOf(CustomAccessibilityAction("Brush up"){driver.accessibleBrush(true,velvetNapStudioNow(),latestAuto.value,latestReduced.value);changed();true},CustomAccessibilityAction("Brush down"){driver.accessibleBrush(false,velvetNapStudioNow(),latestAuto.value,latestReduced.value);changed();true})
        })
        VelvetNapStudioControl(Modifier.frame(16f,55f,44f,44f),"Back",::press){if(visible){driver.clear(velvetNapStudioNow());changed();back?.onBackPressed()}}
        VelvetNapStudioControl(Modifier.frame(333f,55f,44f,44f),"Sample bag",::press,onClick=::save)
        for(i in 0..7) VelvetNapStudioControl(Modifier.frame(23f+43*i,400f,44f,44f),"${VelvetNapStudioDriver.names[i]} fabric",::press,driver.selected==i){choose(i)}
        VelvetNapStudioControl(Modifier.frame(329f,457f,44f,44f),if(driver.liked)"Remove favorite" else "Favorite fabric",::press){capture();driver.liked=!driver.liked;changed()}
        Box(Modifier.frame(24f,463f,303f,44f).semantics{contentDescription="Crushed velvet, ${VelvetNapStudioDriver.names[selected]} No. ${selected+1}"})
        VelvetNapStudioControl(Modifier.frame(24f,556f,167f,44f),"Color, ${VelvetNapStudioDriver.names[driver.selected]}",::press){choose((driver.selected+1)%8)}
        VelvetNapStudioControl(Modifier.frame(202f,556f,167f,44f),"Yards, ${driver.yards}",::press){capture();driver.yards=if(driver.yards==8)1 else driver.yards+1;changed()}
        VelvetNapStudioControl(Modifier.frame(24f,755f,44f,52f),"Decrease yards",::press){capture();driver.yards=max(1,driver.yards-1);changed()}
        VelvetNapStudioControl(Modifier.frame(79f,755f,44f,52f),"Increase yards",::press){capture();driver.yards=min(8,driver.yards+1);changed()}
        VelvetNapStudioControl(Modifier.frame(135f,755f,234f,52f),if(driver.addedAt!=null)"Sample added" else "Add to samples",::press,onClick=::save)
    }
}

@Composable
private fun VelvetNapStudioControl(modifier: Modifier,label: String,onPress: (Boolean)->Unit,selected: Boolean=false,onClick: ()->Unit){
    val pressed=rememberUpdatedState(onPress)
    Box(modifier.pointerInput(Unit){
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            var held=true;pressed.value(true)
            try{
                while(true){
                    val event=awaitPointerEvent(PointerEventPass.Initial);val change=event.changes.firstOrNull{it.id==down.id}?:break
                    if(!change.pressed)break
                    val inside=change.position.x>=0&&change.position.y>=0&&change.position.x<=size.width&&change.position.y<=size.height
                    if(inside!=held){held=inside;pressed.value(inside)}
                }
            }finally{if(held)pressed.value(false)}
        }
    }.semantics{contentDescription=label;this.selected=selected}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null,role=Role.Button,onClick=onClick))
}
private fun velvetNapStudioHitRegion(): Region {
    val p=Path().apply{moveTo(30f,45f);cubicTo(105f,42f,196f,30f,244f,22f);quadTo(260f,116f,271f,223f);quadTo(159f,244f,61f,258f);quadTo(28f,237f,20f,217f);quadTo(22f,112f,30f,45f);close()}
    val stroke=Path();Paint().apply{style=Paint.Style.STROKE;strokeWidth=24f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}.getFillPath(p,stroke)
    p.op(stroke,Path.Op.UNION);p.transform(android.graphics.Matrix().apply{setScale(4f,4f)})
    return Region().apply{setPath(p,Region(-48,-48,1280,1144))}
}
private fun velvetNapStudioNow()=System.nanoTime()/1_000_000_000.0
@Composable
private fun rememberVelvetNapStudioReduced(): Boolean {
    val resolver=LocalContext.current.contentResolver
    fun read()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var reduced by remember(resolver){mutableStateOf(read())}
    DisposableEffect(resolver){val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange: Boolean){reduced=read()}};resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);reduced=read();onDispose{resolver.unregisterContentObserver(observer)}}
    return reduced
}
@Composable
private fun VelvetNapStudioSystemBars(){
    val window=LocalActivity.current?.window?:return;val view=LocalView.current
    DisposableEffect(window,view){val c=WindowCompat.getInsetsController(window,view);val status=c.isAppearanceLightStatusBars;val nav=c.isAppearanceLightNavigationBars;c.isAppearanceLightStatusBars=true;c.isAppearanceLightNavigationBars=true;c.show(WindowInsetsCompat.Type.statusBars());onDispose{c.isAppearanceLightStatusBars=status;c.isAppearanceLightNavigationBars=nav}}
}
