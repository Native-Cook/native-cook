package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

@Composable
fun GlassOrbShelf(autoPlay: Boolean=false, modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val density=LocalDensity.current.density
    val fontScale=LocalDensity.current.fontScale
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val driver=remember { GlassOrbShelfDriver() }
    val reduced=rememberGlassOrbShelfReducedMotion()
    SideEffect { driver.onReducedSelection={ view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> active=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val window=LocalActivity.current?.window
    DisposableEffect(window,view) {
        val controller=window?.let { WindowCompat.getInsetsController(it,view) }
        val oldStatus=controller?.isAppearanceLightStatusBars; val oldNavigation=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=false; controller?.isAppearanceLightNavigationBars=false
        onDispose {
            if (oldStatus!=null) controller?.isAppearanceLightStatusBars=oldStatus
            if (oldNavigation!=null) controller?.isAppearanceLightNavigationBars=oldNavigation
        }
    }
    LaunchedEffect(autoPlay,reduced,active) {
        driver.autoPlay=autoPlay; driver.reduced=reduced
        if (!active) return@LaunchedEffect
        if (reduced) { driver.spin=0.0; driver.pop=0.0; driver.lean=0.0; return@LaunchedEffect }
        var previous=0L
        while (true) withFrameNanos { now ->
            val dt=if (previous==0L) 0.016 else min(0.05,(now-previous)/1e9)
            previous=now
            if (driver.frame(dt)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    BoxWithConstraints(modifier.fillMaxSize().background(Color(GlassOrbShelfTheme.PAGE)).clipToBounds()) {
        val l=remember(maxWidth.value,top) { GlassOrbShelfLayout(maxWidth.value,top) }
        val art=remember(context,l,density,fontScale) { GlassOrbShelfArtwork(context,l,density,fontScale) }
        val orb=remember { if (Build.VERSION.SDK_INT>=33) GlassOrbShelfOrb() else null }
        Canvas(Modifier.fillMaxSize()) {
            val o=driver.offset
            // Reading the clock here invalidates only drawing, never the whole screen composition.
            driver.clock
            val c=drawContext.canvas.nativeCanvas
            if (Build.VERSION.SDK_INT>=33 && orb!=null) orb.draw(c,l,density,driver)
            else {
                val i=GlassOrbShelfMotion.rounded(o).toInt().coerceIn(0,3)
                drawCircle(Color(GlassOrbShelfTheme.apps[i].tint).copy(alpha=0.35f),l.orbR*density,Offset(l.orbX*density,l.orbY*density))
                drawRoundRect(Color(GlassOrbShelfTheme.apps[i].tint),Offset((l.orbX-55)*density,(l.orbY-48)*density),androidx.compose.ui.geometry.Size(110*density,96*density),androidx.compose.ui.geometry.CornerRadius(20*density))
            }
            c.save(); c.scale(density,density); art.draw(c,o); c.restore()
        }
        val stripTop=l.orbY-l.orbR-8
        Box(Modifier.offset(y=stripTop.dp).fillMaxWidth().height((l.cardTop+l.cardH+12-stripTop).dp)
            .semantics {
                val i=GlassOrbShelfMotion.rounded(driver.offset).toInt().coerceIn(0,3)
                contentDescription=GlassOrbShelfTheme.apps[i].label
                stateDescription="App ${i+1} of 4"
                progressBarRangeInfo=ProgressBarRangeInfo(i.toFloat(),0f..3f,2)
                setProgress { value -> driver.adjust(value.roundToInt()-i); true }
                customActions=listOf(CustomAccessibilityAction("Next app") { driver.adjust(1); true },CustomAccessibilityAction("Previous app") { driver.adjust(-1); true })
            }.pointerInput(l,density,reduced) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    driver.begin()
                    val tracker=VelocityTracker(); tracker.addPosition(down.uptimeMillis,down.position)
                    var pan=false; var failed=false; var ended=false; var last=down.position
                    try {
                        while (true) {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            last=change.position
                            tracker.addPosition(change.uptimeMillis,last)
                            if (!change.pressed) { ended=true; break }
                            val dx=(last.x-down.position.x)/density; val dy=(last.y-down.position.y)/density
                            if (!pan && abs(dy)>14) failed=true
                            if (!failed && abs(dx)>8) pan=true
                            if (pan) { change.consume(); driver.drag(dx.toDouble(),l.step) }
                        }
                    } finally {
                        if (pan) driver.release(tracker.calculateVelocity().x/density.toDouble(),l.step)
                        else if (ended && !failed) {
                            if (driver.tap(last.x/density,last.y/density+stripTop,l)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        } else driver.release(0.0,l.step)
                    }
                }
            })
    }
}

@Composable
private fun rememberGlassOrbShelfReducedMotion(): Boolean {
    val resolver=LocalContext.current.contentResolver
    fun read()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
