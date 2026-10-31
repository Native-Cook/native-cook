package com.ngocdevv.nativecook.blocks.navigation.claycategorytabs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun ClayCategoryTabs(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(view) {
        val window = clayCategoryTabsActivity(view.context)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it,view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    val driver = remember(autoPlay,reduced) {
        ClayCategoryTabsDriver(autoPlay,reduced) { landing ->
            haptic.performHapticFeedback(if (landing) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
    }
    val artwork = remember(context) { ClayCategoryTabsArtwork(context) }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    driver.step(if (previous == 0L) 0.0 else (now-previous)/1e9)
                    previous = now
                }
            }
        }
    }
    val top = WindowInsets.statusBars.getTop(displayDensity)/displayDensity.density
    val bottom = WindowInsets.navigationBars.getBottom(displayDensity)/displayDensity.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth/displayDensity.density
        val h = constraints.maxHeight/displayDensity.density
        val layout = ClayCategoryTabsTheme.Layout(w,top)
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }.pointerInput(driver,layout) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis,down.position)
                driver.down(down.position.x/displayDensity.density,down.position.y/displayDensity.density)
                var last = down
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change
                        tracker.addPosition(change.uptimeMillis,change.position)
                        if (!change.pressed) { released = true; break }
                        if (driver.move(change.position.x/displayDensity.density,change.position.y/displayDensity.density,layout)) change.consume()
                    }
                } finally {
                    driver.up(last.position.x/displayDensity.density,last.position.y/displayDensity.density,
                        tracker.calculateVelocity().x/displayDensity.density,layout,!released)
                }
            }
        }) {
            artwork.draw(drawContext.canvas.nativeCanvas,layout,h,bottom,displayDensity.density,driver.frame,driver.time,reduced)
        }
        ClayCategoryTabsAccessibleBox(16f,layout.searchTop,w-88,56f) { contentDescription = "Where to? Anywhere, any week, add guests" }
        ClayCategoryTabsAccessibleBox(w-60,layout.searchTop+6,44f,44f) { contentDescription = "Filters"; role = Role.Button }
        Box(Modifier.offset(y=layout.stageTop.dp).width(w.dp).height(244.dp).selectableGroup()) {
            repeat(3) { i ->
                ClayCategoryTabsAccessibleBox(layout.xs[i]-layout.slot/2,0f,layout.slot,244f) {
                    contentDescription = ClayCategoryTabsTheme.categories[i].name
                    role = Role.Tab; selected = driver.selected == i
                    onClick { driver.tap(i); true }
                }
            }
        }
        val category = ClayCategoryTabsTheme.categories[driver.selected]
        ClayCategoryTabsAccessibleBox(16f,layout.pagerTop+20,w-32,26f) { contentDescription = category.heading; heading() }
        category.listings.forEachIndexed { i,item ->
            ClayCategoryTabsAccessibleBox(16f,layout.pagerTop+58+i*106,w-32,94f) { contentDescription = item.label }
        }
        ClayCategoryTabsAccessibleBox(w/2-48,h-maxOf(bottom,16f)-56,96f,48f) { contentDescription = "Show map"; role = Role.Button }
    }
}

@Composable
private fun ClayCategoryTabsAccessibleBox(x: Float,y: Float,w: Float,h: Float,semantics: SemanticsPropertyReceiver.() -> Unit) {
    Box(Modifier.offset(x.dp,y.dp).size(w.dp,h.dp).semantics(properties=semantics))
}

private fun clayCategoryTabsActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
