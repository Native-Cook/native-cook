package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Interactive by default; the demo opts into the reference's ten-second loop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassFolderBurst(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    var reduced by remember(context) {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Build.VERSION.SDK_INT < 33 || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val window = (context as? Activity)?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // Preserve the reference portrait composition; larger system cutouts still win.
    // Ignoring visibility retains safe areas while the chrome is hidden.
    val top = WindowInsets.statusBarsIgnoringVisibility.getTop(LocalDensity.current) / density
    val cutout = WindowInsets.displayCutout.getTop(LocalDensity.current) / density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(LocalDensity.current) / density
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val layout = remember(maxWidth, maxHeight, top, bottom, cutout) {
            BurstLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), maxOf(top, cutout, 62f).toDouble(), maxOf(bottom, 34f).toDouble())
        }
        val scene = remember(layout, reduced, autoPlay) { BurstScene(layout.n, reduced, autoPlay) }
        val renderer = remember(layout, density) { BurstRenderer(context, layout, density) }
        val frame = remember(scene) { mutableIntStateOf(0) }
        var expanded by remember(scene) { mutableStateOf(false) }
        var selected by remember(scene) { mutableIntStateOf(-1) }
        var landed by remember(scene) { mutableIntStateOf(0) }

        fun publish() {
            if (expanded != scene.expanded) expanded = scene.expanded
            if (selected != scene.selected) selected = scene.selected
            if (landed != scene.landed) landed = scene.landed
            if ((!autoPlay || (scene.paused && !scene.recovering))) {
                if ((scene.events and BurstScene.LIGHT) != 0) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                if ((scene.events and BurstScene.MEDIUM) != 0) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                if ((scene.events and BurstScene.SELECTION) != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if ((scene.events and BurstScene.SOFT) != 0) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            scene.events = 0
        }
        fun send(kind: BurstScene.Input, index: Int = -1) {
            scene.input(kind, index)
            publish()
        }
        DisposableEffect(renderer) { onDispose { renderer.close() } }
        LaunchedEffect(scene) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                scene.step((now - last) / 1_000_000_000.0, layout)
                last = now
                publish()
                frame.intValue++
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(scene) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                send(BurstScene.Input.TouchStart)
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
                send(BurstScene.Input.TouchEnd)
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                frame.intValue
                drawIntoCanvas { renderer.draw(it.nativeCanvas, scene) }
            }
            Box(Modifier
                .size((layout.width - 100).dp, 24.dp)
                .graphicsLayer {
                    frame.intValue
                    translationX = (24 + 52 * scene.dock).toFloat() * density
                    translationY = (layout.restY - 154 + (layout.headerTop - layout.restY + 154) * scene.dock).toFloat() * density
                }
                .semantics { heading(); contentDescription = BurstTracks.title })
            for (i in 0 until layout.n) {
                if ((landed and (1 shl i)) == 0) continue
                val slot = layout.slots[i]
                Box(Modifier.offset(slot.x.dp, slot.y.dp).size(slot.w.dp, slot.h.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = BurstTracks.tracks[i].description
                        this.selected = selected == i
                        stateDescription = if (selected == i) "Playing" else "Not playing"
                        onClick(if (selected == i) "Stop playing" else "Play track") {
                            send(BurstScene.Input.Select, i)
                            true
                        }
                    }
                    .pointerInput(scene, i) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            send(BurstScene.Input.RowDown, i)
                            val up = waitForUpOrCancellation()
                            send(BurstScene.Input.RowUp, i)
                            if (up != null) {
                                up.consume()
                                send(BurstScene.Input.Select, i)
                            }
                        }
                    })
            }
            Box(Modifier.size(240.dp, 180.dp)
                .graphicsLayer {
                    frame.intValue
                    val f = BurstPose.folder(scene, layout)
                    // Matches the RN hit rectangle, including twelve points around the panels.
                    scaleX = ((144 * f.scale + 24) / 240).toFloat()
                    scaleY = ((104 * f.scale + 24) / 180).toFloat()
                    translationX = (f.cx - 120).toFloat() * density
                    translationY = (f.cy - 90).toFloat() * density
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Low Tide, ${layout.n} tracks"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                    onClick(if (expanded) "Puts the tracks away" else "Shows the tracks") {
                        send(BurstScene.Input.Toggle)
                        true
                    }
                }
                .pointerInput(scene) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        send(BurstScene.Input.FolderDown)
                        val up = waitForUpOrCancellation()
                        send(BurstScene.Input.FolderUp)
                        if (up != null) {
                            up.consume()
                            send(BurstScene.Input.Toggle)
                        }
                    }
                })
        }
    }
}
