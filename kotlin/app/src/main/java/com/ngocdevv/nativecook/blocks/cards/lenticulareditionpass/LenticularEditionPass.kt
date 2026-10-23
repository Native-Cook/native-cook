package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import android.database.ContentObserver
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
import kotlin.math.max

/** A bowed laminated edition: material-coordinate ridges switch between two original prints. */
@Composable
fun LenticularEditionPass(autoPlay: Boolean=false,previewTime: Double?=null,modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val resources=remember(context.applicationContext) {LenticularEditionPassResources.load(context.applicationContext)}
    val painter=remember(resources) {LenticularEditionPassPainter(resources)}
    val driver=remember {LenticularEditionPassDriver()}
    val clock=remember {mutableDoubleStateOf(lenticularEditionPassNow())}
    var revision by remember {mutableIntStateOf(0)}
    var visible by remember {mutableStateOf(true)}
    val reduced=rememberLenticularEditionPassReduced()
    val latestAuto=rememberUpdatedState(autoPlay);val latestReduced=rememberUpdatedState(reduced);val latestPreview=rememberUpdatedState(previewTime)
    val view=LocalView.current;val density=LocalDensity.current;val lifecycle=LocalLifecycleOwner.current
    val backDispatcher=LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LenticularEditionPassSystemBars()
    fun wake(reschedule: Boolean=true) {clock.doubleValue=lenticularEditionPassNow();if(reschedule) revision++}
    fun impact() {view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)}
    fun choose(target: Int): Boolean {
        val acted=visible&&driver.chooseView(target,lenticularEditionPassNow(),latestAuto.value,latestReduced.value,latestPreview.value)
        if(acted) {impact();wake()};return acted
    }
    fun collect() {
        if(visible&&driver.collect(lenticularEditionPassNow(),latestAuto.value,latestReduced.value,latestPreview.value)) {impact();wake()}
    }
    DisposableEffect(lifecycle,driver) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP) {driver.clear(lenticularEditionPassNow(),latestReduced.value);visible=false}
            else if(event==Lifecycle.Event.ON_START) {driver.epoch=lenticularEditionPassNow();driver.rendered=null;visible=true}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);driver.clear(lenticularEditionPassNow(),latestReduced.value)}
    }
    LaunchedEffect(reduced) {driver.reducedChanged(lenticularEditionPassNow());wake()}
    LaunchedEffect(autoPlay,previewTime) {driver.clear(lenticularEditionPassNow(),reduced);wake()}
    LaunchedEffect(autoPlay,reduced,previewTime,revision,visible) {
        while(isActive&&visible) {
            val now=lenticularEditionPassNow()
            if(driver.complete(now)) revision++
            clock.doubleValue=now
            if(driver.haptic(now)) impact()
            if(driver.needsFrames(autoPlay,reduced,previewTime)) withFrameNanos {clock.doubleValue=it/1_000_000_000.0}
            else {val deadline=driver.nextDeadline()?:break;delay(max(1L,((deadline-now)*1000).toLong()))}
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale=maxWidth.value/393f;val pixels=scale*density.density
        val height=max(852f,maxHeight.value/scale)
        val bottom=max(0f,WindowInsets.safeDrawing.getBottom(density)/pixels-34f)
        Canvas(Modifier.fillMaxSize()) {
            val frame=driver.frame(clock.doubleValue,autoPlay,reduced,previewTime)
            driver.rendered=frame
            val c=drawContext.canvas.nativeCanvas;c.save();c.scale(pixels,pixels)
            painter.draw(c,frame,height,bottom);c.restore()
        }
        fun Modifier.frame(x: Float,y: Float,w: Float,h: Float)=offset((x*scale).dp,(y*scale).dp).size((w*scale).dp,(h*scale).dp)
        // awaitFirstDown owns the actual Down; no touch-slop, Move, or Up is needed to freeze Live.
        Box(Modifier.frame(21.5f,242.5f,350f,265f).pointerInput(pixels,reduced,previewTime,visible) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                if(!visible||latestReduced.value||latestPreview.value!=null) {waitForUpOrCancellation();return@awaitEachGesture}
                fun point(p: androidx.compose.ui.geometry.Offset)=LenticularEditionPassPoint(p.x/pixels+21.5,p.y/pixels+242.5)
                var last=point(down.position)
                if(!driver.begin(last,lenticularEditionPassNow(),latestAuto.value,latestReduced.value,latestPreview.value)) {waitForUpOrCancellation();return@awaitEachGesture}
                val owner=driver.session!!.id;var normal=false
                down.consume();wake()
                try {
                    while(true) {
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id}?:break
                        if(change.isConsumed) break
                        last=point(change.position)
                        val supportWasActive=driver.session?.supportActive==true
                        if(driver.move(last,lenticularEditionPassNow())) {
                            // Only the finite support's idle→active boundary reschedules composition.
                            wake(!supportWasActive)
                        }
                        change.consume()
                        if(!change.pressed) {normal=true;break}
                    }
                } finally {
                    if(driver.session?.id==owner) {driver.finish(last,lenticularEditionPassNow(),!normal);wake()}
                }
            }
        }.semantics {
            val current=driver.frame(clock.doubleValue,autoPlay,reduced,previewTime)
            contentDescription="Quiet Orbit, edition 7 of 128"
            stateDescription=if(LenticularEditionPassGeometry.meanViewB(current)>.5) "View two of two" else "View one of two"
            customActions=listOf(CustomAccessibilityAction("View two") {choose(1)},CustomAccessibilityAction("View one") {choose(0)})
        })
        fun Modifier.button(label: String,action: () -> Unit)=semantics {contentDescription=label}.clickable(
            interactionSource=MutableInteractionSource(),indication=null,role=Role.Button,onClick=action)
        Box(Modifier.frame(14f,70f,44f,44f).button("Back") {
            if(visible&&previewTime==null&&driver.session==null) {driver.clear(lenticularEditionPassNow(),reduced);wake();backDispatcher?.onBackPressed()}
        })
        Box(Modifier.frame(24f,121f,345f,76f).semantics {contentDescription="Between two views"})
        for(target in 0..1) Box(Modifier.frame(24f+target*70,647.5f,58f,44f).semantics {
            val current=driver.frame(clock.doubleValue,autoPlay,reduced,previewTime)
            contentDescription=if(target==0) "View one" else "View two"
            selected=(target==1)==(LenticularEditionPassGeometry.meanViewB(current)>.5)
        }.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button) {choose(target)})
        Box(Modifier.frame(24f,755f-bottom,345f,54f).semantics {
            val current=driver.frame(clock.doubleValue,autoPlay,reduced,previewTime)
            contentDescription=if(current.collectionMix>=.5) "Collected" else "Add to collection"
        }.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,onClick=::collect))
    }
}

@Composable
private fun rememberLenticularEditionPassReduced(): Boolean {
    val resolver=LocalContext.current.contentResolver
    fun read()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var reduced by remember(resolver) {mutableStateOf(read())}
    DisposableEffect(resolver) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange: Boolean) {reduced=read()}}
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        reduced=read();onDispose {resolver.unregisterContentObserver(observer)}
    }
    return reduced
}
@Composable
private fun LenticularEditionPassSystemBars() {
    val window=LocalActivity.current?.window?:return;val view=LocalView.current
    DisposableEffect(window,view) {
        val c=WindowCompat.getInsetsController(window,view);val status=c.isAppearanceLightStatusBars;val navigation=c.isAppearanceLightNavigationBars
        c.isAppearanceLightStatusBars=true;c.isAppearanceLightNavigationBars=true;c.show(WindowInsetsCompat.Type.statusBars())
        onDispose {c.isAppearanceLightStatusBars=status;c.isAppearanceLightNavigationBars=navigation}
    }
}
