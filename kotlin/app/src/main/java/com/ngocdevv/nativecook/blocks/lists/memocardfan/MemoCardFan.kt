package com.ngocdevv.nativecook.blocks.lists.memocardfan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MemoCardFan(
    autoPlay: Boolean = false,
    freezeAt: Float? = null,
) {
    val regular = remember {
        FontFamily(Font(R.font.memocardfan_inter_regular, FontWeight.Normal))
    }
    val medium = remember {
        FontFamily(Font(R.font.memocardfan_inter_medium, FontWeight.Medium))
    }
    val semibold = remember {
        FontFamily(Font(R.font.memocardfan_inter_semibold, FontWeight.SemiBold))
    }
    val topOpen = remember(freezeAt) { Animatable(topOpenAt(freezeAt ?: 0f)) }
    val bottomOpen = remember(freezeAt) { Animatable(1f) }
    val topPress = remember { Animatable(0f) }
    val bottomPress = remember { Animatable(0f) }
    val bottomPull = remember(freezeAt) { Animatable(pullAt(freezeAt ?: 0f)) }
    val bottomSpread = remember(freezeAt) { Animatable(spreadAt(freezeAt ?: 0f)) }
    val still = remember { Animatable(0f) }
    val playing = remember { mutableStateOf(autoPlay) }
    val reducedMotion = remember { mutableStateOf(false) }
    val contactCount = remember { mutableIntStateOf(0) }
    val resumeGeneration = remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        val durationScale = currentCoroutineContext()[MotionDurationScale]
        // Compose may lazily initialize its scale observer by writing snapshot state.
        // Prime it outside snapshotFlow's read-only snapshot before observing changes.
        reducedMotion.value = (durationScale?.scaleFactor ?: 1f) == 0f
        snapshotFlow { durationScale?.scaleFactor ?: 1f }
            .collect { scale -> reducedMotion.value = scale == 0f }
    }

    LaunchedEffect(autoPlay, freezeAt, reducedMotion.value) {
        resumeGeneration.intValue += 1
        playing.value = autoPlay && freezeAt == null && !reducedMotion.value
        topOpen.snapTo(topOpenAt(freezeAt ?: 0f))
        bottomOpen.snapTo(1f)
        bottomPull.snapTo(pullAt(freezeAt ?: 0f))
        bottomSpread.snapTo(spreadAt(freezeAt ?: 0f))
    }

    LaunchedEffect(playing.value, autoPlay, freezeAt, reducedMotion.value) {
        if (!playing.value || !autoPlay || freezeAt != null || reducedMotion.value) return@LaunchedEffect
        var loopStartMillis = 0L
        while (currentCoroutineContext().isActive) {
            val nowMillis = withFrameNanos { it / 1_000_000L }
            if (!playing.value || !autoPlay) return@LaunchedEffect
            if (loopStartMillis == 0L) {
                loopStartMillis = nowMillis
            }
            val time = (nowMillis - loopStartMillis) % LOOP_MILLIS
            topOpen.snapTo(topOpenAt(time))
            bottomOpen.snapTo(1f)
            bottomPull.snapTo(pullAt(time))
            bottomSpread.snapTo(spreadAt(time))
        }
    }

    LaunchedEffect(resumeGeneration.intValue, autoPlay, freezeAt, reducedMotion.value) {
        val generation = resumeGeneration.intValue
        if (generation == 0 || playing.value || !autoPlay || freezeAt != null || reducedMotion.value || contactCount.intValue != 0) return@LaunchedEffect
        delay(RESUME_AFTER)
        if (generation != resumeGeneration.intValue || playing.value || contactCount.intValue != 0) return@LaunchedEffect
        playing.value = false
        coroutineScope {
            launch { topOpen.animateTo(0f, tween(RETURN_TO_REST, easing = LinearOutSlowInEasing)) }
            launch { bottomOpen.animateTo(1f, tween(RETURN_TO_REST, easing = LinearOutSlowInEasing)) }
            launch { bottomPull.animateTo(0f, tween(RETURN_TO_REST, easing = LinearOutSlowInEasing)) }
            launch { bottomSpread.animateTo(0f, tween(RETURN_TO_REST, easing = LinearOutSlowInEasing)) }
        }
        if (generation == resumeGeneration.intValue && !playing.value && contactCount.intValue == 0) {
            playing.value = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MemoCardFanColors.page)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var active = 0
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val next = event.changes.count { it.pressed }
                        if (active == 0 && next > 0) {
                            resumeGeneration.intValue += 1
                            playing.value = false
                        }
                        if (active > 0 && next == 0) {
                            resumeGeneration.intValue += 1
                        }
                        active = next
                        contactCount.intValue = next
                    }
                }
            },
    ) {
        val safePadding = WindowInsets.safeDrawing.asPaddingValues()
        val topInset = safePadding.calculateTopPadding()
        val bottomInset = safePadding.calculateBottomPadding()
        val middleY = topInset + (maxHeight - topInset - bottomInset) / 2
        val topLeft = maxWidth / 2 + (327f - PIN_CENTER_X).dp * SCALE
        val topTop = middleY + (173f - PIN_CENTER_Y).dp * SCALE
        val bottomLeft = maxWidth / 2 + (344f - PIN_CENTER_X).dp * SCALE
        val bottomTop = middleY + (691f - PIN_CENTER_Y).dp * SCALE

        MemoCardFanFolder(
            left = topLeft,
            top = topTop,
            open = topOpen,
            press = topPress,
            pull = still,
            spread = still,
            reducedMotion = reducedMotion,
            regular = regular,
            medium = medium,
            semibold = semibold,
            scope = scope,
            frozen = freezeAt != null,
            index = 0,
            onAccessibilityInteraction = {
                resumeGeneration.intValue += 1
                playing.value = false
                it()
                resumeGeneration.intValue += 1
            },
            onHaptic = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
        )
        MemoCardFanFolder(
            left = bottomLeft,
            top = bottomTop,
            open = bottomOpen,
            press = bottomPress,
            pull = bottomPull,
            spread = bottomSpread,
            reducedMotion = reducedMotion,
            regular = regular,
            medium = medium,
            semibold = semibold,
            scope = scope,
            frozen = freezeAt != null,
            index = 1,
            onAccessibilityInteraction = {
                resumeGeneration.intValue += 1
                playing.value = false
                it()
                resumeGeneration.intValue += 1
            },
            onHaptic = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
        )
    }
}

@Composable
private fun MemoCardFanFolder(
    left: androidx.compose.ui.unit.Dp,
    top: androidx.compose.ui.unit.Dp,
    open: Animatable<Float, AnimationVector1D>,
    press: Animatable<Float, AnimationVector1D>,
    pull: Animatable<Float, AnimationVector1D>,
    spread: Animatable<Float, AnimationVector1D>,
    reducedMotion: MutableState<Boolean>,
    regular: FontFamily,
    medium: FontFamily,
    semibold: FontFamily,
    scope: CoroutineScope,
    frozen: Boolean,
    index: Int,
    onAccessibilityInteraction: (() -> Unit) -> Unit,
    onHaptic: () -> Unit,
) {
    val density = LocalDensity.current
    val shape = remember { RoundedCornerShape((CARD_RADIUS * SCALE).dp) }
    val images = remember {
        listOf(
            R.drawable.memocardfan_photo_profile,
            R.drawable.memocardfan_photo_shades_v2,
            R.drawable.memocardfan_photo_up_v4,
        )
    }
    val openValue = { shownOpen(open.value, reducedMotion.value) }
    val toggle = {
        val target = if (open.value > 0.5f) 0f else 1f
        scope.launch {
            if (reducedMotion.value) {
                withContext(MemoCardFanAlwaysAnimate) {
                    open.animateTo(target, tween(REDUCE_MOTION_FADE))
                }
            } else {
                open.animateTo(
                    target,
                    if (target > open.value) OPEN_SPRING else CLOSE_SPRING,
                )
            }
        }
        onHaptic()
    }

    Box(
        modifier = Modifier
            .offset(left, top)
            .size((CARD_WIDTH * SCALE).dp, (CARD_HEIGHT * SCALE).dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.72f),
                spotColor = Color.Black.copy(alpha = 0.72f),
            )
            .graphicsLayer {
                val pressed = press.value
                scaleX = 1f - (1f - PRESS_SCALE) * pressed
                scaleY = scaleX
                alpha = if (reducedMotion.value) {
                    1f - sin(PI.toFloat() * open.value)
                } else {
                    1f
                }
            }
            .then(
                if (frozen) {
                    Modifier
                } else {
                    Modifier.memoCardFanGestures(
                        index = index,
                        open = open,
                        press = press,
                        pull = pull,
                        spread = spread,
                        reducedMotion = reducedMotion,
                        scope = scope,
                        onTap = toggle,
                        onHaptic = onHaptic,
                    )
                },
            )
            .semantics {
                role = Role.Button
                contentDescription = "Daily Memo, Notes & Journaling, 09 documents, 11th Notes"
                stateDescription = if (open.value > 0.5f) "Expanded" else "Collapsed"
                if (!frozen) {
                    onClick {
                        onAccessibilityInteraction(toggle)
                        true
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MemoCardFanColors.folder),
        )

        listOf(2, 1, 0).forEach { photoIndex ->
            MemoCardFanPrint(
                image = images[photoIndex],
                index = photoIndex,
                open = open,
                pull = pull,
                spread = spread,
                reducedMotion = reducedMotion,
                overPocket = false,
            )
        }

        Text(
            text = "Daily Task\nCard Design",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = (38.5f * SCALE).dp)
                .offset(y = ((46f - 19.9f * 0.62f) * SCALE).dp)
                .graphicsLayer {
                    alpha = 1f - min(1f, openValue() * 3f)
                },
            color = MemoCardFanColors.ink,
            fontFamily = regular,
            fontSize = (19.9f * SCALE).sp,
            lineHeight = (23f * SCALE).sp,
            textAlign = TextAlign.Start,
        )

        Box(
            Modifier
                .fillMaxSize()
                .zIndex(1f)
                .drawBehind {
                    scale(scaleX = density.density, scaleY = density.density, pivot = Offset.Zero) {
                        drawPath(
                            path = memoCardFanPocketPath(openValue()),
                            color = MemoCardFanColors.folder,
                        )
                    }
                },
        )

        MemoCardFanPrint(
            image = images[0],
            index = 0,
            open = open,
            pull = pull,
            spread = spread,
            reducedMotion = reducedMotion,
            overPocket = true,
        )

        MemoCardFanType(
            openValue = openValue,
            regular = regular,
            medium = medium,
            semibold = semibold,
        )
    }
}

@Composable
private fun MemoCardFanPrint(
    image: Int,
    index: Int,
    open: Animatable<Float, AnimationVector1D>,
    pull: Animatable<Float, AnimationVector1D>,
    spread: Animatable<Float, AnimationVector1D>,
    reducedMotion: MutableState<Boolean>,
    overPocket: Boolean,
) {
    val openTargets = remember {
        listOf(
            MemoCardFanPhotoTarget(189.5f, 178f, 0f),
            MemoCardFanPhotoTarget(23f, 202f, -22.5f),
            MemoCardFanPhotoTarget(381f, 202f, 22.5f),
        )
    }
    val target = openTargets[index]
    val side = if (index == 1) -1f else if (index == 2) 1f else 0f
    val frameShape = remember { RoundedCornerShape((PHOTO_RADIUS * SCALE).dp) }
    val photoShape = remember {
        RoundedCornerShape(((PHOTO_RADIUS - PHOTO_FRAME) * SCALE).dp)
    }
    val frame = PHOTO_FRAME * SCALE

    Box(
        modifier = Modifier
            .size((PHOTO_WIDTH * SCALE).dp, (PHOTO_HEIGHT * SCALE).dp)
            .zIndex(if (overPocket) 2f else 0f)
            .graphicsLayer {
                val shown = shownOpen(open.value, reducedMotion.value)
                val lag = if (index == 0) 0f else STAGGER
                val progress = ((shown - lag) / (1f - lag)).coerceIn(0f, 1f)
                val cardPull = if (index == 0) pull.value else 0f
                val lifted = max(0f, cardPull)
                val wider = spread.value
                val x = 217.5f + (target.x - 217.5f) * progress + side * 14f * wider
                val y = 217f + (target.y - 217f) * progress +
                    side * side * -12f * wider - PULL_RISE * cardPull
                val closedScale = CARD_WIDTH / PHOTO_WIDTH
                val cardScale = closedScale + (1f - closedScale) * progress

                translationX = ((x - PHOTO_WIDTH / 2f) * SCALE).dp.toPx()
                translationY = ((y - PHOTO_HEIGHT / 2f) * SCALE).dp.toPx()
                rotationZ = target.rotation * progress + side * 10f * wider - 4f * lifted
                scaleX = cardScale * (1f + 0.1f * lifted)
                scaleY = scaleX
                alpha = if (index != 0 || ((cardPull > 0.9f) == overPocket)) 1f else 0f
                shadowElevation = (6f + (22f - 6f) * lifted).dp.toPx()
                shape = frameShape
                ambientShadowColor = Color.Black.copy(alpha = 0.55f * min(1f, lifted))
                spotShadowColor = ambientShadowColor
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = min(1f, shownOpen(open.value, reducedMotion.value) * 2.5f)
                }
                .clip(frameShape)
                .background(MemoCardFanColors.frame),
        )

        Box(
            modifier = Modifier
                .offset(frame.dp, frame.dp)
                .size(
                    ((PHOTO_WIDTH - PHOTO_FRAME * 2f) * SCALE).dp,
                    ((PHOTO_HEIGHT - PHOTO_FRAME * 2f) * SCALE).dp,
                )
                .graphicsLayer {
                    val fillProgress = min(
                        1f,
                        shownOpen(open.value, reducedMotion.value) * 2.5f,
                    )
                    val fill = PHOTO_WIDTH / (PHOTO_WIDTH - PHOTO_FRAME * 2f)
                    val photoScale = fill + (1f - fill) * fillProgress
                    scaleX = photoScale
                    scaleY = photoScale
                    clip = true
                    shape = photoShape
                },
        ) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (index == 0) {
                            0f
                        } else {
                            0.3f * pull.value.coerceIn(0f, 1f)
                        }
                    }
                    .background(Color.Black),
            )
            Box(
                Modifier
                    .offset(y = (-PHOTO_HEIGHT * 0.3f * SCALE).dp)
                    .size(
                        (PHOTO_WIDTH * 0.35f * SCALE).dp,
                        (PHOTO_HEIGHT * 1.6f * SCALE).dp,
                    )
                    .graphicsLayer {
                        val progress = if (index == 0) {
                            max(0f, pull.value).coerceAtMost(1f)
                        } else {
                            0f
                        }
                        alpha = 0.28f * sin(PI.toFloat() * progress)
                        translationX = (
                            (-PHOTO_WIDTH * 0.7f + PHOTO_WIDTH * 1.4f * progress) * SCALE
                            ).dp.toPx()
                        rotationZ = 20f
                    }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun MemoCardFanType(
    openValue: () -> Float,
    regular: FontFamily,
    medium: FontFamily,
    semibold: FontFamily,
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(5f),
    ) {
        Text(
            text = "Daily Memo",
            modifier = Modifier
                .offset(
                    x = (18f * SCALE).dp,
                    y = ((179f - 20f * 0.62f) * SCALE).dp,
                )
                .graphicsLayer {
                    val value = openValue()
                    translationX = (-88f * SCALE * value).dp.toPx()
                    translationY = (94f * SCALE * value).dp.toPx()
                    scaleX = 1f + 0.05f * value
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            color = MemoCardFanColors.ink,
            fontFamily = semibold,
            fontSize = (20f * SCALE).sp,
        )
        Text(
            text = "Notes & Journaling",
            modifier = Modifier
                .offset(
                    x = (18f * SCALE).dp,
                    y = ((210f - 19f * 0.62f) * SCALE).dp,
                )
                .graphicsLayer {
                    val value = openValue()
                    translationX = (-68f * SCALE * value).dp.toPx()
                    translationY = (94f * SCALE * value).dp.toPx()
                    scaleX = 1f + 0.05f * value
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            color = MemoCardFanColors.mute,
            fontFamily = regular,
            fontSize = (19f * SCALE).sp,
            letterSpacing = 0.2.sp,
        )
        Row(
            modifier = Modifier
                .offset(
                    x = (25f * SCALE).dp,
                    y = ((390f - 44f * 0.62f) * SCALE).dp,
                )
                .graphicsLayer {
                    val value = openValue()
                    translationX = (-10f * SCALE * value).dp.toPx()
                    translationY = (7f * SCALE * value).dp.toPx()
                    scaleX = 1f + 0.05f * value
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "09",
                modifier = Modifier.alignByBaseline(),
                color = MemoCardFanColors.ink,
                fontFamily = regular,
                fontSize = (44f * SCALE).sp,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "Doc",
                modifier = Modifier
                    .padding(start = (7f * SCALE).dp)
                    .offset(y = (-9.5f * SCALE).dp)
                    .alignByBaseline(),
                color = MemoCardFanColors.mute,
                fontFamily = regular,
                fontSize = (19f * SCALE).sp,
            )
        }
        Text(
            text = "11th Notes",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = (51f * SCALE).dp)
                .offset(y = ((390f - 19f * 0.62f) * SCALE).dp)
                .graphicsLayer {
                    val value = openValue()
                    translationX = (27f * SCALE * value).dp.toPx()
                    translationY = (5f * SCALE * value).dp.toPx()
                    scaleX = 1f + 0.05f * value
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(1f, 0f)
                },
            color = MemoCardFanColors.ink,
            fontFamily = semibold,
            fontSize = (19f * SCALE).sp,
        )
    }
}

private fun Modifier.memoCardFanGestures(
    index: Int,
    open: Animatable<Float, AnimationVector1D>,
    press: Animatable<Float, AnimationVector1D>,
    pull: Animatable<Float, AnimationVector1D>,
    spread: Animatable<Float, AnimationVector1D>,
    reducedMotion: MutableState<Boolean>,
    scope: CoroutineScope,
    onTap: () -> Unit,
    onHaptic: () -> Unit,
): Modifier = pointerInput(index) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        val densityValue = density
        var verticalDrag = false
        var horizontalHandoff = false
        var dragStart = open.value
        var pullingPrint = false
        var pressJob: Job? = null
        var dragUpdateJob: Job? = null

        pressJob = scope.launch {
            withContext(MemoCardFanAlwaysAnimate) {
                press.animateTo(1f, tween(PRESS_IN))
            }
        }

        var finished = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) {
                break
            }
            val change = event.changes.find { it.id == down.id }
            if (change == null || change.isConsumed) {
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            val translation = change.position - down.position
            val horizontalDp = translation.x / densityValue
            val verticalDp = translation.y / densityValue

            if (!verticalDrag && abs(horizontalDp) > 18f) {
                horizontalHandoff = true
                pressJob.cancel()
                scope.launch {
                    withContext(MemoCardFanAlwaysAnimate) {
                        press.animateTo(0f, tween(PRESS_OUT))
                    }
                }
            }
            if (!horizontalHandoff &&
                !reducedMotion.value &&
                !verticalDrag &&
                abs(verticalDp) > 10f
            ) {
                verticalDrag = true
                dragStart = open.value
                pullingPrint = false
            }

            if (verticalDrag) {
                change.consume()
                if (index == 1 &&
                    dragStart > 0.5f &&
                    (pullingPrint || verticalDp < 0f)
                ) {
                    pullingPrint = true
                    val value = rubberBanded(-verticalDp / (PULL_RISE * SCALE))
                    dragUpdateJob?.cancel()
                    dragUpdateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        pull.snapTo(value)
                        spread.snapTo(value.coerceIn(0f, 1.08f))
                    }
                } else {
                    val value = rubberBanded(dragStart - verticalDp / DRAG_RANGE)
                    dragUpdateJob?.cancel()
                    dragUpdateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        open.snapTo(value)
                    }
                }
            }

            if (!change.pressed) {
                dragUpdateJob?.cancel()
                val velocity = tracker.calculateVelocity()
                val velocityY = velocity.y / densityValue
                if (verticalDrag) {
                    if (index == 1 && pullingPrint) {
                        val normalizedVelocity = -velocityY / (PULL_RISE * SCALE)
                        scope.launch {
                            pull.animateTo(0f, CLOSE_SPRING, normalizedVelocity)
                        }
                        scope.launch {
                            spread.animateTo(0f, SPREAD_HOME_SPRING)
                        }
                    } else {
                        val target = when {
                            velocityY < -DRAG_VELOCITY -> 1f
                            velocityY > DRAG_VELOCITY -> 0f
                            open.value > DRAG_COMMIT -> 1f
                            else -> 0f
                        }
                        val normalizedVelocity = -velocityY / DRAG_RANGE
                        scope.launch {
                            open.animateTo(target, FLING_SPRING, normalizedVelocity)
                        }
                    }
                    onHaptic()
                } else if (!horizontalHandoff) {
                    onTap()
                }

                pressJob.cancel()
                scope.launch {
                    withContext(MemoCardFanAlwaysAnimate) {
                        press.animateTo(0f, tween(PRESS_OUT))
                    }
                }
                finished = true
                break
            }
        }
        if (!finished) {
            dragUpdateJob?.cancel()
            if (verticalDrag) {
                if (index == 1 && pullingPrint) {
                    scope.launch { pull.animateTo(0f, CLOSE_SPRING, 0f) }
                    scope.launch { spread.animateTo(0f, SPREAD_HOME_SPRING) }
                } else {
                    val target = if (open.value > DRAG_COMMIT) 1f else 0f
                    scope.launch { open.animateTo(target, FLING_SPRING, 0f) }
                }
            }
            pressJob.cancel()
            scope.launch {
                withContext(MemoCardFanAlwaysAnimate) {
                    press.animateTo(0f, tween(PRESS_OUT))
                }
            }
        }
    }
}

private fun memoCardFanPocketPath(open: Float): Path {
    fun point(x: Float, y: Float, openX: Float, openY: Float): Offset = Offset(
        x = (x + (openX - x) * open) * SCALE,
        y = (y + (openY - y) * open) * SCALE,
    )

    return Path().apply {
        val start = point(0f, 173f, -101f, 257f)
        moveTo(start.x, start.y)
        val tabControl = point(0f, 155f, -106f, 240f)
        val tabEnd = point(18f, 155f, -85f, 240f)
        quadraticTo(tabControl.x, tabControl.y, tabEnd.x, tabEnd.y)
        val flatEnd = point(150f, 155f, 158f, 240f)
        lineTo(flatEnd.x, flatEnd.y)
        val slopeControl1 = point(172f, 155f, 183f, 240f)
        val slopeControl2 = point(176f, 202f, 185f, 274f)
        val slopeEnd = point(221f, 202f, 226f, 274f)
        cubicTo(
            slopeControl1.x,
            slopeControl1.y,
            slopeControl2.x,
            slopeControl2.y,
            slopeEnd.x,
            slopeEnd.y,
        )
        val topEnd = point(413f, 202f, 508f, 275f)
        lineTo(topEnd.x, topEnd.y)
        val topCorner = point(435f, 202f, 535f, 276f)
        val rightTop = point(435f, 224f, 523f, 310f)
        quadraticTo(topCorner.x, topCorner.y, rightTop.x, rightTop.y)
        val rightBottom = point(435f, 394f, 439f, 418f)
        lineTo(rightBottom.x, rightBottom.y)
        val bottomRightControl = point(435f, 434f, 425f, 442f)
        val bottomRight = point(395f, 434f, 415f, 442f)
        quadraticTo(
            bottomRightControl.x,
            bottomRightControl.y,
            bottomRight.x,
            bottomRight.y,
        )
        val bottomLeft = point(40f, 434f, 24f, 442f)
        lineTo(bottomLeft.x, bottomLeft.y)
        val leftBottomControl = point(0f, 434f, 6f, 442f)
        val leftBottom = point(0f, 394f, -8f, 420f)
        quadraticTo(
            leftBottomControl.x,
            leftBottomControl.y,
            leftBottom.x,
            leftBottom.y,
        )
        val leftEdgeControl1 = point(0f, 320.3f, -33.7f, 365.7f)
        val leftEdgeControl2 = point(0f, 246.7f, -82.1f, 311.3f)
        cubicTo(leftEdgeControl1.x, leftEdgeControl1.y, leftEdgeControl2.x, leftEdgeControl2.y, start.x, start.y)
        close()
    }
}

private fun shownOpen(open: Float, reducedMotion: Boolean): Float {
    return if (reducedMotion) {
        if (open < 0.5f) 0f else 1f
    } else {
        open
    }
}

private fun rubberBanded(value: Float): Float {
    if (value < 0f) return value * DRAG_RESISTANCE
    if (value > 1f) return 1f + (value - 1f) * DRAG_RESISTANCE
    return value
}

private fun springStep(
    time: Float,
    at: Float,
    omega: Float,
    zeta: Float,
): Float {
    val seconds = (time - at) / 1_000f
    if (seconds <= 0f) return 0f
    if (zeta >= 1f) {
        return 1f - (1f + omega * seconds) * exp(-omega * seconds)
    }
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - exp(-zeta * omega * seconds) *
        (
            cos(damped * seconds) +
                ((zeta * omega) / damped) * sin(damped * seconds)
            )
}

private fun pullAt(input: Float): Float {
    val time = wrap(input)
    val dip = if (time > 900f && time < 1_140f) {
        -0.05f * sin(min(1f, (time - 900f) / 240f) * PI.toFloat())
    } else {
        0f
    }
    return dip + springStep(time, 1_020f, 12.5f, 0.78f) -
        springStep(time, 2_700f, 13f, 0.92f)
}

private fun spreadAt(input: Float): Float {
    val time = wrap(input)
    return springStep(time, 1_120f, 15f, 0.6f) -
        springStep(time, 2_850f, 14f, 1f)
}

private fun topOpenAt(input: Float): Float {
    val time = wrap(input) - 2_800f
    if (time < 900f) return 0f
    if (time < 1_050f) {
        return -0.025f * sin(((time - 900f) / 150f) * PI.toFloat())
    }
    if (time < 1_740f) {
        return springRise((time - 1_050f) / 690f)
    }
    if (time < 2_600f) return 1f
    if (time < 3_240f) {
        return 1f - smoother((time - 2_600f) / 640f)
    }
    return 0f
}

private fun springRise(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    val end = 1f - exp(-6.4f) * cos(9.4f)
    return (1f - exp(-6.4f * x) * cos(9.4f * x)) / end
}

private fun smoother(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private fun wrap(input: Float): Float {
    return ((input % LOOP_MILLIS) + LOOP_MILLIS) % LOOP_MILLIS
}

private data class MemoCardFanPhotoTarget(
    val x: Float,
    val y: Float,
    val rotation: Float,
)

private object MemoCardFanAlwaysAnimate : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

private object MemoCardFanColors {
    val page = Color(0xFF010101)
    val folder = Color(0xFF24211F)
    val ink = Color.White
    val mute = Color(0xFF918F8D)
    val frame = Color(0xFFFFFEFB)
}

private const val SCALE = 0.51f
private const val PIN_CENTER_X = 545f
private const val PIN_CENTER_Y = 655f
private const val CARD_WIDTH = 435f
private const val CARD_HEIGHT = 434f
private const val CARD_RADIUS = 40f
private const val PHOTO_WIDTH = 327f
private const val PHOTO_HEIGHT = 330f
private const val PHOTO_RADIUS = 26f
private const val PHOTO_FRAME = 8f
private const val PULL_RISE = 118f
private const val LOOP_MILLIS = 8_000f
private const val RESUME_AFTER = 3_500L
private const val RETURN_TO_REST = 320
private const val DRAG_RANGE = 140f
private const val DRAG_RESISTANCE = 0.3f
private const val DRAG_COMMIT = 0.5f
private const val DRAG_VELOCITY = 600f
private const val PRESS_SCALE = 0.97f
private const val PRESS_IN = 120
private const val PRESS_OUT = 150
private const val REDUCE_MOTION_FADE = 200
private const val STAGGER = 0.12f

private val OPEN_SPRING = spring<Float>(
    dampingRatio = 0.84f,
    stiffness = 180f,
)
private val CLOSE_SPRING = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = 280f,
)
private val FLING_SPRING = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 240f,
)
private val SPREAD_HOME_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 280f,
)
