/** Compose surface and touch handling with observable immutable poses for each frame. */
package com.ngocdevv.nativecook.blocks.gestures.crumpletodelete

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.BACK
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.DRAG
import kotlin.math.*

@Composable
/** Renders live autoplay and finger-driven paper using the same draw-phase pose snapshot. */
fun CrumpleToDelete(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val reduced =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    val art = remember(context) { CrumpleToDeleteArt(context) }
    val audio = remember(context, reduced) { if (reduced) null else CrumpleToDeleteAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    DisposableEffect(art) { onDispose { art.close() } }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity =
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun buzz(kind: Int) {
        if (Build.VERSION.SDK_INT >= 29)
            vibrator?.vibrate(
                VibrationEffect.createPredefined(
                    if (kind == 0) VibrationEffect.EFFECT_TICK
                    else if (kind == 1) VibrationEffect.EFFECT_HEAVY_CLICK
                    else VibrationEffect.EFFECT_CLICK
                )
            )
        else
            vibrator?.vibrate(
                VibrationEffect.createOneShot(if (kind == 1) 18 else 12, if (kind == 1) 80 else 45)
            )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
        val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
        val layout =
            remember(maxWidth, maxHeight, top, bottom) {
                CrumpleToDeleteLayout(
                    maxWidth.value.toDouble(),
                    maxHeight.value.toDouble(),
                    top,
                    bottom,
                )
            }
        val driver =
            remember(layout, autoPlay, reduced, previewTime) { CrumpleToDeleteDriver(layout.geo) }
        // Publish an immutable snapshot; Canvas and graphicsLayer observe it in their draw phases.
        var currentPose by
            remember(driver) {
                mutableStateOf(
                    if (previewTime != null) CrumpleToDeleteMotion.script(previewTime, layout.geo)
                    else driver.pose()
                )
            }
        var gone by remember(driver) { mutableStateOf(false) }
        var undoPressed by remember(driver) { mutableStateOf(false) }
        val g = layout.geo
        fun undo() {
            driver.undo(g)
            audio?.play("pop")
            currentPose = driver.pose()
        }
        fun delete() {
            driver.interrupt(g)
            driver.delete()
            driver.rspin = 0.0
            currentPose = driver.pose()
        }
        LaunchedEffect(driver, lifecycle) {
            if (previewTime != null) {
                driver.apply(CrumpleToDeleteMotion.script(previewTime, g))
                currentPose = driver.pose()
                driver.gone = previewTime >= 3.45 && previewTime < 6.3
                gone = driver.gone
                return@LaunchedEffect
            }
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous = 0L
                while (true) withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    driver.step(dt, g, autoPlay, reduced, { audio?.play(it) }, ::buzz)
                    if (gone != driver.gone) gone = driver.gone
                    currentPose = driver.pose()
                }
            }
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(driver, previewTime) {
                if (previewTime != null) return@pointerInput
                val d = density.density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val x = start.x / d
                    val y = start.y / d
                    val uw = art.undoWidth(density.fontScale)
                    val ty = layout.top + 96 + 16 * (1 - driver.toast)
                    val undoHit =
                        driver.gone &&
                            x >= layout.w - 28 - uw - 10 &&
                            x <= layout.w - 18 &&
                            y >= ty - 2 &&
                            y <= ty + 54
                    val noteHit =
                        !driver.gone &&
                            x >= driver.x - layout.nw / 2 &&
                            x <= driver.x + layout.nw / 2 &&
                            y >= driver.y - layout.nh / 2 &&
                            y <= driver.y + layout.nh / 2
                    if (!noteHit && !undoHit) return@awaitEachGesture
                    if (noteHit) driver.interrupt(g)
                    if (undoHit) undoPressed = true
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, start)
                    var end = start
                    var moved = false
                    var released = false
                    down.consume()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            end = change.position
                            tracker.addPosition(change.uptimeMillis, end)
                            val dx = (end.x - start.x) / d
                            val dy = (end.y - start.y) / d
                            if (noteHit && change.pressed) {
                                if (!moved && hypot(dx, dy) >= 2) {
                                    moved = true
                                    driver.beginDrag()
                                }
                                if (moved) driver.drag(dx.toDouble(), dy.toDouble(), g)
                            }
                            change.consume()
                            if (!change.pressed) released = true
                        } while (change.pressed && event.changes.any { it.pressed })
                    } finally {
                        undoPressed = false
                        if (noteHit) {
                            if (released)
                                driver.release((tracker.calculateVelocity().y / d).toDouble())
                            if (driver.mode == DRAG) driver.mode = BACK
                        }
                        if (
                            undoHit &&
                                released &&
                                hypot((end.x - start.x) / d, (end.y - start.y) / d) < 12
                        )
                            undo()
                    }
                }
            }
        ) {
            drawIntoCanvas {
                art.draw(
                    it.nativeCanvas,
                    layout,
                    currentPose,
                    density.density,
                    density.fontScale,
                    undoPressed,
                )
            }
        }
        // These are native accessibility targets over the exact visual note and toast.
        Box(
            Modifier.offset(24.dp, (layout.top + 34).dp).size((layout.w - 48).dp, 42.dp).semantics {
                heading()
                contentDescription = "NOTES · TODAY. Notes. Pull a note into the bin to delete it"
            }
        )
        if (!gone) {
            Box(
                Modifier.size(layout.nw.dp, layout.nh.dp)
                    .graphicsLayer {
                        val p = currentPose
                        translationX = ((p.x - layout.nw / 2) * density.density).toFloat()
                        translationY = ((p.y - layout.nh / 2) * density.density).toFloat()
                    }
                    .semantics {
                        contentDescription =
                            "Note: Saturday market. Drag down into the bin to delete"
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Delete note") {
                                    delete()
                                    true
                                }
                            )
                    }
            )
        } else {
            val uw = art.undoWidth(density.fontScale)
            Box(
                Modifier.offset(40.dp, (layout.top + 104).dp)
                    .size((layout.w - 80 - uw).dp, 36.dp)
                    .semantics {
                        contentDescription = "Note deleted"
                        liveRegion = LiveRegionMode.Polite
                    }
            )
            Box(
                Modifier.offset((layout.w - 28 - uw).dp, (layout.top + 104).dp)
                    .size(uw.dp, 36.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Undo"
                        onClick("Undo") {
                            undo()
                            true
                        }
                    }
            )
        }
    }
}
