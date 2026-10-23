package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

/** A tactile daily check-in: seven opaque petals settle behind a frosted bowl. */
@Composable
fun MoodPetalBowl(autoPlay: Boolean=false,previewTime: Double?=null,modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val resources=remember(context.applicationContext) {MoodPetalBowlResources.load(context.applicationContext)}
    val painter=remember(resources) {MoodPetalBowlPainter(resources)}
    val driver=remember {MoodPetalBowlDriver()}
    val clock=remember {mutableDoubleStateOf(moodPetalBowlNow())}
    var revision by remember {mutableIntStateOf(0)}
    var visible by remember {mutableStateOf(true)}
    val reduced=rememberMoodPetalBowlReduced()
    val latestAuto=rememberUpdatedState(autoPlay);val latestReduced=rememberUpdatedState(reduced);val latestPreview=rememberUpdatedState(previewTime)
    val view=LocalView.current;val density=LocalDensity.current;val lifecycle=LocalLifecycleOwner.current
    MoodPetalBowlSystemBars()
    fun wake(reschedule: Boolean=true) {clock.doubleValue=moodPetalBowlNow();if(reschedule) revision++}
    fun impact() {view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)}
    fun chooseMood(step: Int): Boolean {
        if(!visible) return false
        val changed=driver.chooseMood(step,moodPetalBowlNow(),latestAuto.value,latestReduced.value,latestPreview.value)
        if(changed) {impact();wake()};return changed
    }
    fun chooseDay(day: Int) {
        if(visible&&driver.chooseDay(day,moodPetalBowlNow(),latestAuto.value,latestReduced.value,latestPreview.value)) {impact();wake()}
    }
    fun save() {
        if(visible&&driver.save(moodPetalBowlNow(),latestAuto.value,latestReduced.value,latestPreview.value)) {impact();wake()}
    }
    DisposableEffect(lifecycle,driver) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP) {driver.clear(moodPetalBowlNow());visible=false}
            else if(event==Lifecycle.Event.ON_START) {driver.epoch=moodPetalBowlNow();visible=true}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);driver.clear(moodPetalBowlNow())}
    }
    LaunchedEffect(reduced) {driver.clear(moodPetalBowlNow());clock.doubleValue=driver.epoch;revision++}
    LaunchedEffect(autoPlay) {if(driver.session==null&&driver.event==null) {driver.epoch=moodPetalBowlNow();clock.doubleValue=driver.epoch}}
    LaunchedEffect(autoPlay,reduced,previewTime,revision,visible) {
        while(isActive&&visible) {
            val now=moodPetalBowlNow()
            if(driver.complete(now)) revision++
            clock.doubleValue=now
            val frames=!reduced&&previewTime==null&&driver.session==null&&(autoPlay||driver.event!=null)
            if(frames) withFrameNanos {clock.doubleValue=it/1_000_000_000.0}
            else {val deadline=driver.nextDeadline()?:break;delay(max(1L,((deadline-now)*1000).toLong()))}
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale=maxWidth.value/393f;val pixels=scale*density.density
        val height=max(852f,maxHeight.value/scale)
        val bottom=max(0f,WindowInsets.safeDrawing.getBottom(density)/pixels-34f)
        MoodPetalBowlArt(painter,clock,driver,autoPlay,reduced,previewTime,scale,pixels,height,bottom)
        fun Modifier.frame(x: Float,y: Float,w: Float,h: Float)=offset((x*scale).dp,(y*scale).dp).size((w*scale).dp,(h*scale).dp)
        fun Modifier.gesture(owner: MoodPetalBowlOwner,origin: Offset)=pointerInput(owner,pixels,reduced,previewTime,visible) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                if(!visible||latestReduced.value||latestPreview.value!=null) {waitForUpOrCancellation();return@awaitEachGesture}
                fun point(p: Offset)=MoodPetalBowlPoint((p.x/pixels+origin.x).toDouble(),(p.y/pixels+origin.y).toDouble())
                if(!driver.begin(owner,point(down.position),moodPetalBowlNow(),latestAuto.value,latestReduced.value,latestPreview.value,down.uptimeMillis/1000.0)) {waitForUpOrCancellation();return@awaitEachGesture}
                val generation=driver.session!!.generation;var normal=false;var sample=down.uptimeMillis/1000.0
                down.consume();wake()
                try {
                    while(true) {
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id}?:break
                        if(change.isConsumed) break
                        sample=change.uptimeMillis/1000.0
                        val oldPoint=driver.session?.lastPoint
                        if(driver.changed(owner,point(change.position),sample)) impact()
                        if(oldPoint!=driver.session?.lastPoint) wake(false)
                        change.consume()
                        if(!change.pressed) {normal=true;break}
                    }
                } finally {
                    if(driver.session?.generation==generation) {
                        if(driver.finish(owner,moodPetalBowlNow(),!normal,latestReduced.value,sample)) impact()
                        wake()
                    }
                }
            }
        }
        Box(Modifier.frame(35f,137f,323f,293f).gesture(MoodPetalBowlOwner.Bowl,Offset(35f,137f)).semantics {
            val s=driver.scene(clock.doubleValue,autoPlay,reduced,previewTime)
            contentDescription="${s.dominantMood.title.lowercase().replaceFirstChar {it.uppercase()} } mood, selected ${MoodPetalBowlMath.days[s.selectedDay]} ${12+s.selectedDay}"
            stateDescription="Drag to stir the bowl."
            customActions=listOf(CustomAccessibilityAction("Next mood") {chooseMood(1)},CustomAccessibilityAction("Previous mood") {chooseMood(-1)})
        })
        Box(Modifier.frame(60f,451f,273f,55f).gesture(MoodPetalBowlOwner.Label,Offset(60f,451f)))
        fun Modifier.button(label: String,action: () -> Unit)=semantics {contentDescription=label}.clickable(
            interactionSource=MutableInteractionSource(),indication=null,role=Role.Button,onClick=action)
        Box(Modifier.frame(21f,457.5f,44f,44f).button("Previous mood") {chooseMood(-1)})
        Box(Modifier.frame(328f,457.5f,44f,44f).button("Next mood") {chooseMood(1)})
        for(day in 0..6) {
            Box(Modifier.frame((MoodPetalBowlMath.dateX[day]-22).toFloat(),559f,44f,88f).semantics {
                val s=driver.scene(clock.doubleValue,autoPlay,reduced,previewTime)
                contentDescription="${MoodPetalBowlMath.days[day]} ${12+day}"
                stateDescription=driver.records[day]?.title?.lowercase()?.replaceFirstChar {it.uppercase()}?:"No mood saved"
                selected=s.selectedDay==day
            }.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button) {chooseDay(day)})
        }
        Box(Modifier.frame(60f,763f-bottom,44f,44f).button("Save mood",::save))
        Box(Modifier.frame(84f,517f,225f,18f).semantics {
            val s=driver.scene(clock.doubleValue,autoPlay,reduced,previewTime)
            if(s.captionAlpha>=.5) contentDescription="Saved for ${MoodPetalBowlMath.days[s.captionDay]}"
            else hideFromAccessibility()
        })
    }
}

@Composable
private fun rememberMoodPetalBowlReduced(): Boolean {
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
private fun MoodPetalBowlSystemBars() {
    val window=LocalActivity.current?.window?:return;val view=LocalView.current
    DisposableEffect(window,view) {
        val c=WindowCompat.getInsetsController(window,view);val status=c.isAppearanceLightStatusBars;val navigation=c.isAppearanceLightNavigationBars
        c.isAppearanceLightStatusBars=true;c.isAppearanceLightNavigationBars=true;c.show(WindowInsetsCompat.Type.statusBars())
        onDispose {c.isAppearanceLightStatusBars=status;c.isAppearanceLightNavigationBars=navigation}
    }
}
