package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.min

const val LOOP: Long = SaveToCollectionTokens.loopMs

@Composable
fun SaveToCollection(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val density = LocalDensity.current.density
    val resources = LocalContext.current.resources
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val assets = remember(resources) {
        val resourceIds = buildSet {
            SaveToCollectionTokens.cards.forEach { card ->
                add(card.art)
                add(card.soft)
            }
            addAll(SaveToCollectionTokens.streaks)
        }
        resourceIds.associateWith { resource ->
            requireNotNull(BitmapFactory.decodeResource(resources, resource)) {
                "Missing SaveToCollection drawable $resource"
            }
        }
    }
    val renderer = remember(assets) {
        SaveToCollectionRenderer(assets)
    }
    val engine = remember(autoPlay, reducedMotion, view) {
        SaveToCollectionEngine(
            autoPlay = autoPlay,
            reducedMotion = reducedMotion,
            onHaptic = { event ->
                view.performHapticFeedback(saveFeedbackConstant(event))
            },
        )
    }
    var drawTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(engine) {
        var previousNanos = withFrameNanos { nanos -> nanos }
        while (isActive) {
            val nowNanos = withFrameNanos { nanos -> nanos }
            val rawSeconds = (nowNanos - previousNanos).coerceAtLeast(0L) / 1_000_000_000f
            previousNanos = nowNanos
            engine.step(min(SaveToCollectionTokens.maxFrameSeconds, rawSeconds))
            drawTick += 1L
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SaveToCollectionTokens.background),
    ) {
        val stageWidth = maxWidth.value
        val stageHeight = maxHeight.value
        engine.setSize(stageWidth, stageHeight)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Art cards that can be carried or tapped into a bookmark"
                    customActions = listOf(
                        CustomAccessibilityAction("Save every card") {
                            val now = System.nanoTime() / 1_000_000_000.0
                            engine.touchDown(engine.bookmarkX, engine.bookmarkY, now)
                            engine.touchUp(0f, 0f)
                            true
                        },
                    )
                }
                .pointerInput(engine, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        engine.touchDown(
                            x = down.position.x / density,
                            y = down.position.y / density,
                            eventSeconds = down.uptimeMillis / 1000.0,
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { pointer -> pointer.id == down.id }
                            if (change == null) {
                                engine.touchUp(0f, 0f)
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val velocity = tracker.calculateVelocity()
                            if (!change.pressed) {
                                engine.touchUp(
                                    velocityX = velocity.x / density,
                                    velocityY = velocity.y / density,
                                )
                                break
                            }
                            engine.touchMove(
                                x = change.position.x / density,
                                y = change.position.y / density,
                                eventSeconds = change.uptimeMillis / 1000.0,
                                velocityX = velocity.x / density,
                                velocityY = velocity.y / density,
                            )
                            change.consume()
                        }
                    }
                },
        ) {
            drawTick
            val canvas = drawContext.canvas.nativeCanvas
            val saveCount = canvas.save()
            canvas.scale(density, density)
            renderer.draw(canvas, engine)
            canvas.restoreToCount(saveCount)
        }
    }
}

private fun saveFeedbackConstant(event: SaveHaptic): Int {
    return when (event) {
        SaveHaptic.Lift -> HapticFeedbackConstants.LONG_PRESS
        SaveHaptic.Magnet -> HapticFeedbackConstants.VIRTUAL_KEY
        SaveHaptic.Land -> HapticFeedbackConstants.VIRTUAL_KEY
        SaveHaptic.Select -> HapticFeedbackConstants.CLOCK_TICK
        SaveHaptic.Commit -> HapticFeedbackConstants.CONTEXT_CLICK
        SaveHaptic.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
    }
}
