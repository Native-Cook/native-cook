package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ngocdevv.nativecook.shared.rememberReducedMotion

/**
 * A printed book, four pages, turned with a page curl: drag the right edge to the left to turn forward, drag
 * to the right to turn back, tap the margins or "Turn the page". Let go past half a turn, or flick, and the
 * sheet goes over on a mass-spring; a hard flick right turns every page back to the title in one riffle.
 * With [autoPlay] a scripted finger plays one 10 s loop (see LOOP) through the same input as a real one;
 * a real touch takes over and 2 s after the last touch the script resumes.
 */
@Composable
fun PageCurlJournal(autoPlay: Boolean = false) {
    val density = LocalDensity.current.density
    val view = LocalView.current
    val context = LocalContext.current
    val fonts = LocalFontFamilyResolver.current
    val reduced = rememberReducedMotion()
    val roman = remember(context) {
        FontFamily(Font(PageCurlJournalAssets.ROMAN, context.assets))
    }
    val italic = remember(context) {
        FontFamily(Font(PageCurlJournalAssets.ITALIC, context.assets))
    }
    val paper = remember(context) {
        requireNotNull(
            context.assets.open(PageCurlJournalAssets.PAPER).use { stream -> BitmapFactory.decodeStream(stream) },
        ).asImageBitmap()
    }
    val page = remember { mutableIntStateOf(0) }
    // Bumped once a frame that changed something, so only the canvas draws again.
    val tick = remember { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize().background(PAPER.base)) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val widthDp = widthPx / density
        val heightDp = heightPx / density
        val s = widthDp / REF.WIDTH

        val art = remember(widthPx, heightPx, density, fonts, roman, italic, paper) {
            PageCurlPages(widthDp, heightDp, density, fonts, roman, italic, paper)
        }
        val engine = remember(widthPx, heightPx, density, reduced, autoPlay, view) {
            PageCurlEngine(
                width = widthDp,
                height = heightDp,
                density = density,
                reduced = reduced,
                autoPlay = autoPlay && !reduced,
                haptic = { kind -> view.performHapticFeedback(hapticConstant(kind)) },
                onPage = { page.intValue = it },
            )
        }
        val foil = remember(widthDp) { FoilRect(40f * s, 120f * s, 322f * s, 180f * s) }
        var renderer by remember(art, engine) { mutableStateOf<PageCurlRenderer?>(null) }
        val uniforms = remember { CurlUniforms() }
        val foilUniforms = remember { FoilUniforms() }
        val spine = remember(density, s) {
            Brush.horizontalGradient(
                listOf(Color(0xFF463018).copy(alpha = 0.08f), Color.Transparent),
                startX = 0f,
                endX = SPACE.MD * s * density,
            )
        }

        // Snapshot the four pages once they are laid out: the curl samples them as textures.
        LaunchedEffect(art, engine) {
            renderer = PageCurlRenderer(art.snapshots(), density, widthDp, heightDp, foil)
            engine.markReady()
        }

        // One clock: the engine runs a frame, and the canvas draws again when something moved.
        LaunchedEffect(engine) {
            while (true) {
                val nanos = withFrameNanos { it }
                engine.frame(nanos / 1_000_000.0)
                if (engine.needsDraw()) tick.intValue += 1
            }
        }

        // The phone's tilt moves the foil's light on the cover.
        DisposableEffect(engine) {
            val manager = context.getSystemService(SensorManager::class.java)
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val matrix = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, orientation)
                    engine.onRoll(orientation[2])
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (manager != null && sensor != null) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose { manager?.unregisterListener(listener) }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Journal"
                    stateDescription = "${COPY.PAGES[page.intValue]}, page ${page.intValue + 1} of 4"
                    customActions = listOf(
                        CustomAccessibilityAction("Next page") {
                            engine.turnBy(1)
                            true
                        },
                        CustomAccessibilityAction("Previous page") {
                            engine.turnBy(-1)
                            true
                        },
                    )
                }
                .pointerInput(engine) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        engine.touchDown(down.position.x / density, down.position.y / density)
                        var last = down.position
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                last = change.position
                                tracker.addPointerInputChange(change)
                                if (change.changedToUp() || !change.pressed) break
                                if (change.positionChanged()) {
                                    engine.touchMove(change.position.x / density, change.position.y / density)
                                }
                                change.consume()
                            }
                        } finally {
                            // A cancelled gesture still ends: the sheet is let go with the speed it had.
                            val velocity = tracker.calculateVelocity()
                            engine.touchUp(last.x / density, last.y / density, velocity.x / density, velocity.y / density)
                        }
                    }
                },
        ) {
            val frame = tick.intValue
            if (frame < 0) return@Canvas
            val canvas = drawContext.canvas.nativeCanvas
            val gpu = renderer
            // A turning sheet covers every pixel of the resting page, so it is not drawn again while one is out.
            if (gpu == null || !engine.slotShown(0)) {
                art.draw(this, engine.viewPage)
            }
            if (gpu != null) {
                if (engine.foilVisible()) {
                    engine.foilUniforms(foilUniforms)
                    gpu.drawFoil(canvas, foilUniforms)
                }
                for (slot in 0 until RIFFLE.SLOTS) {
                    if (engine.slotShown(slot)) {
                        engine.curlUniforms(slot, uniforms)
                        gpu.drawCurl(canvas, slot, uniforms)
                    }
                }
            }
            // The binding's shadow and the edges of the pages still to turn sit over everything, like the book itself.
            drawRect(spine, size = Size(SPACE.MD * s * density, size.height))
            for (index in 0 until 3) {
                val alpha = engine.stackAlpha(index)
                if (alpha > 0f) {
                    val right = size.width - (index * STACK.GAP + 0.4f) * s * density
                    drawRect(
                        STACK_INK,
                        topLeft = Offset(right - STACK.LINE * density, 0f),
                        size = Size(STACK.LINE * density, size.height),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/** Selection tick, light impact, soft impact: the nearest of Android's own. */
private fun hapticConstant(kind: Int): Int = when (kind) {
    HAPTIC_TICK -> HapticFeedbackConstants.CLOCK_TICK
    HAPTIC_HALF -> HapticFeedbackConstants.CONTEXT_CLICK
    else -> HapticFeedbackConstants.VIRTUAL_KEY
}
