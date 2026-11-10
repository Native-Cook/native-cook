package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import android.app.Activity
import android.content.ContextWrapper
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs

@Composable
fun GoldEmblemMembership(autoPlay: Boolean = false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val audio=remember(context) { GoldEmblemMembershipAudio(context) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    val driver=remember(autoPlay,reduced) {
        GoldEmblemMembershipDriver(autoPlay,reduced,audio::play) { stamp ->
            view.performHapticFeedback(if(stamp)HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        }
    }
    val buttonFont=remember(context) { FontFamily(Font("goldemblemmembership/goldemblemmembership_Outfit-Medium.ttf",context.assets)) }
    val art=remember(context) { GoldEmblemMembershipArt(context) }
    var tick by remember { mutableLongStateOf(0L) }
    var wake by remember { mutableIntStateOf(0) }
    var shown by remember(driver) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null)controller.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,wake) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=0L
            while(driver.moving()) withFrameNanos { nanos ->
                val dt=if(previous==0L)0.0 else ((nanos-previous)/1e9).coerceIn(0.0,0.5)
                previous=nanos; driver.step(dt); tick++
                if(shown!=driver.frame.shown)shown=driver.frame.shown
            }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l=GoldEmblemMembershipTheme.Layout(maxWidth.value,maxHeight.value,top,bottom)
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            tick
            drawIntoCanvas { canvas ->
                val c=canvas.nativeCanvas
                c.save(); c.scale(density.density,density.density)
                art.draw(c,l,driver.frame,density.density,driver.now,reduced)
                c.restore()
            }
        }
        val tier=GoldEmblemMembershipTheme.tiers[shown]
        Box(Modifier.offset(y=(l.cardY-10).dp).size(l.w.dp,(l.cardH+60).dp)
            .semantics {
                contentDescription="${tier.name}, ${tier.range}"
                stateDescription="Swipe to see the other tiers"
                progressBarRangeInfo=ProgressBarRangeInfo(shown.toFloat(),0f..2f,1)
                setProgress { value -> driver.adjust(if(value>shown)1 else -1); wake++; true }
                customActions=listOf(CustomAccessibilityAction("Increment") { driver.adjust(1); wake++; true },
                    CustomAccessibilityAction("Decrement") { driver.adjust(-1); wake++; true })
            }.pointerInput(driver) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val start=down.position
                    val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                    driver.begin(); wake++
                    var active=false; var failed=false; var finished=false; var dx=0.0; var activationX=0.0
                    try {
                        do {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            tracker.addPosition(change.uptimeMillis,change.position)
                            dx=((change.position.x-start.x)/density.density).toDouble()
                            val dy=((change.position.y-start.y)/density.density).toDouble()
                            if(!active && abs(dy)>14) { failed=true; break }
                            if(!active && abs(dx)>8) { active=true; activationX=dx; driver.start() }
                            if(active && change.pressed)driver.drag(dx-activationX)
                            if(active)change.consume()
                        } while(change.pressed)
                        driver.release(dx-activationX,(tracker.calculateVelocity().x/density.density).toDouble(),!active || failed); finished=true
                    } finally {
                        if(!finished)driver.release(dx-activationX,(tracker.calculateVelocity().x/density.density).toDouble(),true)
                        wake++
                    }
                }
            })
        // The reference's two Receive controls only have press feedback, no purchase action.
        GoldEmblemMembershipPress(reduced,Modifier.offset((282*l.sx).dp,l.y(425f).dp).size((85*l.sx).dp,33.dp),"Receive the gift coupon",Color(0xffe9c17f),Color(0xff4a361c),buttonFont)
        GoldEmblemMembershipPress(reduced,Modifier.offset((l.cardX+l.cardW-109).dp,(l.y(735f)+(105*l.k-33)/2).dp).size(91.dp,33.dp),"Receive the member package",Color.White,Color(0xffd19a4a),buttonFont)
        Box(Modifier.offset((18*l.sx).dp,(l.y(529f)-12).dp).size(140.dp,25.dp).semantics { heading(); contentDescription="My privilege" })
        Box(Modifier.offset((17*l.sx).dp,(l.y(700f)-12).dp).size(170.dp,25.dp).semantics { heading(); contentDescription="Member benefits" })
    }
}
