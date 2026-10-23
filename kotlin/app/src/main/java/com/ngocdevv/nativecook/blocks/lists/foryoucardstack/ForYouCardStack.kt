package com.ngocdevv.nativecook.blocks.lists.foryoucardstack

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
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
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun ForYouCardStack(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val reduced = rememberReducedMotion()
    val canAutoPlay = autoPlay && !reduced
    val motion = remember(canAutoPlay) { ForYouCardStackMotion(canAutoPlay) }
    val painter = remember(context) { ForYouCardStackPainter(context) }

    var frameTick by remember { mutableLongStateOf(0L) }
    var interactionGeneration by remember { mutableIntStateOf(0) }
    var playerVisible by remember(motion) { mutableStateOf(false) }
    var deckRevealed by remember(motion) { mutableStateOf(false) }

    motion.onPlayerVisible = { visible -> playerVisible = visible }
    motion.onDeckRevealed = { revealed -> deckRevealed = revealed }

    DisposableEffect(activity, view, playerVisible) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !playerVisible
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    LaunchedEffect(motion, canAutoPlay, interactionGeneration, reduced) {
        var firstFrame = true
        while (isActive && (canAutoPlay || motion.hasMotion() || firstFrame)) {
            withFrameNanos { now ->
                motion.advance(now, canAutoPlay)
                frameTick += 1L // Publish the terminal manual-animation sample too.
                firstFrame = false
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFFECD7D4))
            .semantics {
                contentDescription = "For You book stack"
            }
            .pointerInput(autoPlay, reduced, motion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downX = down.position.x / density.density
                    val downY = down.position.y / density.density
                    val player = motion.push > .5f
                    val inHomeRow = downY in 366f..552f
                    val inPlay = player && downX in 178f..224f && downY in 663f..709f
                    val inBack = player && downX in 20f..75f && downY in 68f..120f
                    if (!inHomeRow && !inPlay && !inBack) return@awaitEachGesture

                    motion.beginTouch()
                    interactionGeneration += 1
                    if (inPlay) motion.press(ForYouPress.Play, true, reduced)
                    if (inBack) motion.press(ForYouPress.Back, true, reduced)

                    var mode = when {
                        inPlay -> 3
                        inBack -> 4
                        inHomeRow -> 1
                        else -> 0
                    }
                    var released = false
                    var cancelled = false
                    var moved = false
                    var releaseX = downX
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    while (!released) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) {
                            cancelled = true
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        releaseX = change.position.x / density.density
                        val translationX = releaseX - downX
                        val translationY = change.position.y / density.density - downY
                        if (abs(translationX) > 10f || abs(translationY) > 10f) moved = true

                        if (mode == 1 && abs(translationX) >= 10f) {
                            if (!motion.dragging) motion.beginDeckDrag()
                            change.consume()
                            motion.updateDeckDrag(translationX, 135.6f)
                        }
                        if (!change.pressed) {
                            released = true
                            val velocity = tracker.calculateVelocity().x / density.density
                            if (mode == 1 && motion.dragging) {
                                val changed = motion.endDeckDrag(velocity, 135.6f, reduced)
                                if (changed) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                motion.releaseForResume()
                            } else if (!cancelled && mode == 1 && !moved) {
                                val revealedScale = (178f / 186f) + (1f - 178f / 186f) * motion.deck
                                val revealedLeft = 40.7f - 9.5f * (1f - motion.deck) + (124.3f - 124.3f * revealedScale) / 2f
                                val revealedRight = revealedLeft + 124.3f * revealedScale
                                if (motion.deck > .5f && releaseX in revealedLeft..revealedRight) {
                                    motion.openBook(reduced)
                                    motion.releaseForResume()
                                } else {
                                    motion.releaseForResume()
                                }
                            } else if (!cancelled && mode == 3 && !moved) {
                                motion.togglePlay(reduced)
                                motion.press(ForYouPress.Play, false, reduced)
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                motion.releaseForResume()
                            } else if (!cancelled && mode == 4 && !moved) {
                                motion.back(reduced)
                                motion.press(ForYouPress.Back, false, reduced)
                                motion.releaseForResume()
                            } else {
                                if (mode == 3) motion.press(ForYouPress.Play, false, reduced)
                                if (mode == 4) motion.press(ForYouPress.Back, false, reduced)
                                motion.releaseForResume()
                            }
                        }
                    }
                    if (!released) {
                        if (mode == 3) motion.press(ForYouPress.Play, false, reduced)
                        if (mode == 4) motion.press(ForYouPress.Back, false, reduced)
                        motion.releaseForResume()
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            frameTick
            drawIntoCanvas { drawScopeCanvas ->
                val nativeCanvas = drawScopeCanvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.scale(density.density, density.density)
                painter.draw(nativeCanvas, width, height, motion, reduced)
                nativeCanvas.restore()
            }
        }
        // Empty semantic hit regions keep the Canvas as the visual/pointer owner while
        // exposing the same actions that the RN Pressables advertise to TalkBack.
        if (!playerVisible && deckRevealed) {
            Box(
                Modifier
                    .offset(40.7.dp, 366.dp)
                    .size(124.3.dp, 186.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "The Life of Colour"
                        onClick {
                            motion.openBook(reduced)
                            motion.releaseForResume()
                            interactionGeneration += 1
                            true
                        }
                    },
            )
        }
        if (playerVisible) {
            Box(
                Modifier
                    .offset(178.dp, 663.dp)
                    .size(46.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Play or pause"
                        onClick {
                            motion.togglePlay(reduced)
                            motion.press(ForYouPress.Play, false, reduced)
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                            motion.releaseForResume()
                            interactionGeneration += 1
                            true
                        }
                    },
            )
            Box(
                Modifier
                    .offset(20.dp, 68.dp)
                    .size(55.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Back"
                        onClick {
                            motion.back(reduced)
                            motion.press(ForYouPress.Back, false, reduced)
                            motion.releaseForResume()
                            interactionGeneration += 1
                            true
                        }
                    },
            )
        }
    }
}
