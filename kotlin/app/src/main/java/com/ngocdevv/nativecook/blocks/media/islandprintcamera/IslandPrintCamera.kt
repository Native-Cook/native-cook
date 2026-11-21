package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Faithful port of the RN 11-second camera, shutter and developing-print loop. */
@Composable
fun IslandPrintCamera(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val window = activity?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    val assets by produceState<IslandPrintCameraAssets?>(null, context) {
        value = withContext(Dispatchers.IO) { IslandPrintCameraAssets(context.assets) }
    }
    val renderer = remember(assets, context) { assets?.let { IslandPrintCameraRenderer(context, it) } }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    val engine = remember { IslandPrintCameraEngine(autoPlay, reduced) }
    val audio=remember(context) { IslandPrintCameraAudio(context.assets) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    SideEffect { engine.view = view; engine.cue=audio::play }
    LaunchedEffect(engine, activity) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            engine.pin = activity?.intent?.getStringExtra("islandPrintCameraTime")?.toDoubleOrNull()?.takeIf { it.isFinite() }
        }
    }
    LaunchedEffect(autoPlay, reduced) { engine.configure(autoPlay, reduced) }
    val tick = remember { mutableLongStateOf(0L) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(engine, lifecycle, renderer) {
        if (renderer == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                var previous = withFrameNanos { it }
                do {
                    val current = withFrameNanos { it }
                    engine.advance((current - previous) / 1e9)
                    previous = current
                    open = engine.frame().p > .9 && engine.sequence < 0
                    tick.longValue = current
                } while (engine.needsFrames())
                engine.wake.receive()
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val s = maxWidth.value / 402f
        val pixelsPerPoint = density.density * s
        val topInset = WindowInsets.statusBars.getTop(density) / density.density
        val islandTop = if (topInset >= 54) 11f else topInset + 4
        Canvas(Modifier.fillMaxSize()
            .semantics { contentDescription = "Island Print Camera. Drag down to open the camera." }
            .pointerInput(engine, pixelsPerPoint, density.density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    engine.down((down.position.x / pixelsPerPoint).toDouble(), (down.position.y / pixelsPerPoint).toDouble())
                    var last = down
                    var completed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.position - down.position
                            engine.move((delta.x / pixelsPerPoint).toDouble(), (delta.y / pixelsPerPoint).toDouble(), s.toDouble())
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity()
                                engine.up((change.position.x / pixelsPerPoint).toDouble(), (change.position.y / pixelsPerPoint).toDouble(),
                                    (velocity.y / pixelsPerPoint).toDouble(), change.uptimeMillis - down.uptimeMillis, false, (velocity.x/pixelsPerPoint).toDouble())
                                completed = true
                                break
                            }
                            change.consume()
                        }
                    } finally {
                        if (!completed) engine.up((last.position.x / pixelsPerPoint).toDouble(), (last.position.y / pixelsPerPoint).toDouble(),
                            0.0, last.uptimeMillis - down.uptimeMillis, true)
                    }
                }
            }) {
            tick.longValue
            val r = renderer
            if (r == null) drawContext.canvas.nativeCanvas.drawColor(0xFFE82822.toInt())
            else r.draw(drawContext.canvas.nativeCanvas, maxWidth.value, pixelsPerPoint, islandTop, engine)
        }
        // Invisible semantic targets expose the same hit regions without adding gesture handlers or visual layers.
        Box(Modifier.offset((141 * s).dp, (83 * s).dp).size((120 * s).dp, (60 * s).dp)
            .graphicsLayer {
                tick.longValue
                translationY = (engine.frame().p.coerceAtLeast(0.0) * 371 * pixelsPerPoint).toFloat()
            }
            .semantics {
                contentDescription = "Open or close camera"
                role = Role.Button
                onClick {
                    val y = 113 + engine.frame().p.coerceAtLeast(0.0) * 371
                    engine.down(201.0, y); engine.up(201.0, y, 0.0, 0L, false)
                    true
                }
            })
        if (open) {
            Box(Modifier.offset((148 * s).dp, (309 * s).dp).size((106 * s).dp)
                .semantics {
                    contentDescription = "Shutter"
                    role = Role.Button
                    onClick { engine.down(201.0, 362.0); engine.up(201.0, 362.0, 0.0, 0L, false); true }
                })
            Box(Modifier.offset((41 * s).dp, (330 * s).dp).size((64 * s).dp)
                .semantics {
                    contentDescription = "Toggle flash"
                    role = Role.Button
                    onClick { engine.down(73.0, 362.0); engine.up(73.0, 362.0, 0.0, 0L, false); true }
                })
        }
    }
}
