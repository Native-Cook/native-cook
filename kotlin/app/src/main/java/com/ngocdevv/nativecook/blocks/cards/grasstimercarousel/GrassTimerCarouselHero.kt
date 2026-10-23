package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private val GrassLime = Color(0xFFDFFF8A)
private val HeroMuted = Color.White.copy(alpha = 0.60f)

@Composable
internal fun GrassTimerHero(
    runtime: GrassTimerCarouselRuntime,
    assets: GrassTimerCarouselAssets,
    scale: Float,
    textScale: Float,
    referenceOriginPx: Float,
    referenceOrigin: Dp,
    point: (Float) -> Dp,
) {
    HeroArtwork(
        runtime = runtime,
        assets = assets,
        scale = scale,
        referenceOrigin = referenceOrigin,
        point = point,
    )
    NeighborCardCopy(runtime, scale, textScale, referenceOrigin, point)
    LeadCardCopy(runtime, scale, textScale, referenceOrigin, point)
    TimerOverlay(runtime, scale, textScale, referenceOriginPx, point)
}

@Composable
private fun HeroArtwork(
    runtime: GrassTimerCarouselRuntime,
    assets: GrassTimerCarouselAssets,
    scale: Float,
    referenceOrigin: Dp,
    point: (Float) -> Dp,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(point(392f))
            .offset(y = referenceOrigin),
    ) {
        runtime.drawRevision.intValue
        val frame = runtime.frame.get()
        val neighborRect = Rect(
            left = (frame.neighborCardX * scale).toFloat(),
            top = 0f,
            right = ((frame.neighborCardX + 232.0) * scale).toFloat(),
            bottom = 327f * scale,
        )
        val neighborPath = roundedPath(neighborRect, 36f * scale)
        drawSoftShadow(neighborRect, 36f * scale, frame.secondCardOpacity.toFloat(), scale)
        drawCoverPhoto(
            image = assets[if (frame.selectedCard == 0) "card-quiet" else "card-grass"],
            destination = neighborRect,
            clip = neighborPath,
            alpha = frame.secondCardOpacity.toFloat(),
        )

        val aperture = frame.aperture
        val selectedRect = Rect(
            left = ((aperture.x + frame.carouselOffset) * scale).toFloat(),
            top = (aperture.y * scale).toFloat(),
            right = ((aperture.x + frame.carouselOffset + aperture.width) * scale).toFloat(),
            bottom = ((aperture.y + aperture.height) * scale).toFloat(),
        )
        val selectedPath = roundedPath(selectedRect, (aperture.cornerRadius * scale).toFloat())
        drawSoftShadow(selectedRect, (aperture.cornerRadius * scale).toFloat(), 1f, scale)
        drawCoverPhoto(
            image = assets[if (frame.selectedCard == 0) "card-grass" else "card-quiet"],
            destination = selectedRect,
            clip = selectedPath,
            parallaxY = (frame.imageParallax * scale).toFloat(),
        )
        clipPath(selectedPath) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = (0.16 * frame.timerOpacity).toFloat()),
                        Color.Black.copy(alpha = (0.12 + 0.16 * frame.timerOpacity).toFloat()),
                    ),
                    startY = selectedRect.top,
                    endY = selectedRect.bottom,
                ),
                topLeft = selectedRect.topLeft,
                size = selectedRect.size,
            )
        }

        val ringAlpha = frame.ringOpacity.toFloat()
        if (ringAlpha > 0f) {
            val inset = 5f * scale
            val ringRect = Rect(
                selectedRect.left + inset,
                selectedRect.top + inset,
                selectedRect.right - inset,
                selectedRect.bottom - inset,
            )
            val stroke = Stroke(width = 2.5f * scale, cap = StrokeCap.Round)
            drawOval(GrassLime.copy(alpha = 0.42f * ringAlpha), ringRect.topLeft, ringRect.size, style = stroke)
            drawArc(
                color = GrassLime.copy(alpha = ringAlpha),
                startAngle = -90f,
                sweepAngle = (360 * frame.ringProgress).toFloat(),
                useCenter = false,
                topLeft = ringRect.topLeft,
                size = ringRect.size,
                style = stroke,
            )
        }

        val highlight = frame.landingHighlightOpacity.toFloat()
        if (highlight > 0f) {
            val center = Offset(selectedRect.center.x, selectedRect.top + 6f * scale)
            drawCircle(GrassLime.copy(alpha = 0.10f * highlight), 11f * scale, center)
            drawCircle(GrassLime.copy(alpha = 0.24f * highlight), 8f * scale, center)
            drawCircle(GrassLime.copy(alpha = 0.74f * highlight), 6f * scale, center)
        }
    }
}

private fun DrawScope.drawSoftShadow(rect: Rect, radius: Float, alpha: Float, scale: Float) {
    if (alpha <= 0f) return
    val spreads = listOf(18f to 0.035f, 11f to 0.05f, 5f to 0.065f)
    spreads.forEach { (spread, opacity) ->
        val amount = spread * scale
        drawRoundRect(
            color = Color.Black.copy(alpha = opacity * alpha),
            topLeft = Offset(rect.left - amount, rect.top + 15f * scale - amount),
            size = Size(rect.width + amount * 2, rect.height + amount * 2),
            cornerRadius = CornerRadius(radius + amount),
        )
    }
}

private fun DrawScope.drawCoverPhoto(
    image: ImageBitmap,
    destination: Rect,
    clip: Path,
    alpha: Float = 1f,
    parallaxY: Float = 0f,
) {
    if (alpha <= 0f || image.width <= 0 || image.height <= 0) return
    val coverScale = max(destination.width / image.width, destination.height / image.height)
    val drawnWidth = image.width * coverScale
    val drawnHeight = image.height * coverScale
    clipPath(clip) {
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (destination.center.x - drawnWidth * 0.5f).roundToInt(),
                (destination.center.y - drawnHeight * 0.5f + parallaxY).roundToInt(),
            ),
            dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }
}

private fun roundedPath(rect: Rect, radius: Float): Path = Path().apply {
    addRoundRect(RoundRect(rect, CornerRadius(radius)))
}

@Composable
private fun LeadCardCopy(
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
    textScale: Float,
    referenceOrigin: Dp,
    point: (Float) -> Dp,
) {
    val selected = runtime.selectedCard.intValue
    CardCopy(
        index = selected,
        isLead = true,
        runtime = runtime,
        scale = scale,
        textScale = textScale,
        referenceOrigin = referenceOrigin,
        point = point,
    )
}

@Composable
private fun NeighborCardCopy(
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
    textScale: Float,
    referenceOrigin: Dp,
    point: (Float) -> Dp,
) {
    val selected = runtime.selectedCard.intValue
    CardCopy(
        index = if (selected == 0) 1 else 0,
        isLead = false,
        runtime = runtime,
        scale = scale,
        textScale = textScale,
        referenceOrigin = referenceOrigin,
        point = point,
    )
}

@Composable
private fun CardCopy(
    index: Int,
    isLead: Boolean,
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
    textScale: Float,
    referenceOrigin: Dp,
    point: (Float) -> Dp,
) {
    val title = if (index == 0) "Touch Grass" else "Quiet Day"
    val duration = if (index == 0) "8 hrs" else "1 day"
    val accessibilityVisible = if (isLead) {
        runtime.leadCopyAccessible.value
    } else {
        runtime.neighborCopyAccessible.value
    }
    Box(
        Modifier
            .offset(y = referenceOrigin)
            .size(point(232f), point(327f))
            .graphicsLayer {
                runtime.drawRevision.intValue
                val frame = runtime.frame.get()
                translationX = ((if (isLead) 14.0 + frame.carouselOffset else frame.neighborCardX) * scale).toFloat()
                alpha = (if (isLead) frame.copyOpacity else frame.secondCardOpacity).toFloat()
            }
            .then(
                if (accessibilityVisible) Modifier else Modifier.clearAndSetSemantics {
                    hideFromAccessibility()
                },
            ),
    ) {
        Text(
            text = "Timer",
            color = Color.White,
            fontSize = (18f * textScale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(x = point(139f), y = point(76f))
                .width(point(88f)),
        )
        Column(Modifier.offset(x = point(25f), y = point(198f))) {
            Text(
                text = title,
                color = Color.White,
                fontSize = (22f * textScale).sp,
                lineHeight = (26f * textScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "No Distracting Apps",
                color = HeroMuted,
                fontSize = (17f * textScale).sp,
                lineHeight = (21f * textScale).sp,
            )
        }
        StartChip(
            duration = duration,
            isLead = isLead,
            runtime = runtime,
            scale = scale,
            textScale = textScale,
            accessibilityVisible = accessibilityVisible,
            point = point,
            modifier = Modifier.offset(x = point(25f), y = point(260f)),
        )
    }
}

@Composable
private fun StartChip(
    duration: String,
    isLead: Boolean,
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
    textScale: Float,
    accessibilityVisible: Boolean,
    point: (Float) -> Dp,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(point(20f))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(point(80f), point(44f))
            .graphicsLayer {
                runtime.drawRevision.intValue
                val frame = runtime.frame.get()
                val chipScale = if (isLead) frame.chipScale.toFloat() else 1f
                scaleX = chipScale
                scaleY = chipScale
            }
            .then(if (isLead) Modifier.chipPressGesture(runtime, scale) else Modifier)
            .then(
                if (isLead && accessibilityVisible) {
                    Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Start $duration focus timer"
                        onClick(label = "Start focus timer") {
                            runtime.mutate { at, reduced -> activate(at, reduced) }
                            true
                        }
                    }
                } else {
                    Modifier.clearAndSetSemantics { hideFromAccessibility() }
                },
            ),
    ) {
        Box(
            Modifier
                .size(point(80f), point(40f))
                .clip(shape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(point(1f), Color.White.copy(alpha = 0.12f), shape),
        )
        RowChipContent(duration, textScale, point)
        if (isLead) {
            Canvas(Modifier.size(point(80f), point(40f))) {
                runtime.drawRevision.intValue
                val progress = runtime.frame.get().pressProgress.toFloat()
                if (progress <= 0f) return@Canvas
                val path = Path().apply {
                    addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(size.height / 2)))
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val segment = Path()
                measure.getSegment(0f, measure.length * progress, segment, true)
                drawPath(
                    segment,
                    GrassLime.copy(alpha = 0.90f),
                    style = Stroke(width = 1.5f * scale, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun RowChipContent(duration: String, textScale: Float, point: (Float) -> Dp) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(point(14f))) {
            val path = Path().apply {
                moveTo(size.width * 0.25f, size.height * 0.12f)
                lineTo(size.width * 0.87f, size.height * 0.50f)
                lineTo(size.width * 0.25f, size.height * 0.88f)
                close()
            }
            drawPath(path, Color.White)
        }
        Spacer(Modifier.width(point(8f)))
        Text(
            text = duration,
            color = Color.White,
            fontSize = (16f * textScale).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun Modifier.chipPressGesture(
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
): Modifier = pointerInput(runtime, scale) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val visible = runtime.frame.get().copyOpacity > 0.10
        if (!visible) return@awaitEachGesture
        down.consume()
        runtime.mutate { at, reduced -> beginPress(at, reduced) }
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - down.position
            if (!cancelled && hypot(delta.x, delta.y) > 18f * scale) {
                cancelled = true
                runtime.carouselMutation { at -> cancelPress(at) }
            }
            change.consume()
            if (!change.pressed) {
                if (cancelled) {
                    runtime.carouselMutation { at -> cancelPress(at) }
                } else {
                    runtime.mutate { at, reduced -> endPress(at, reduced) }
                }
                break
            }
        }
    }
}

@Composable
private fun TimerOverlay(
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
    textScale: Float,
    referenceOriginPx: Float,
    point: (Float) -> Dp,
) {
    val remaining = runtime.remainingSeconds.intValue
    val card = runtime.timerCard.intValue
    val accessibilityVisible = runtime.timerAccessible.value
    val timerText = remember(remaining) { formatTimer(remaining) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(point(190f))
            .height(point(64f))
            .graphicsLayer {
                runtime.drawRevision.intValue
                val frame = runtime.frame.get()
                val aperture = frame.aperture
                translationX = ((aperture.x + frame.carouselOffset + aperture.width / 2) * scale).toFloat() -
                    size.width / 2
                translationY = referenceOriginPx + ((aperture.y + aperture.height / 2) * scale).toFloat() -
                    size.height / 2
                alpha = frame.timerOpacity.toFloat()
            }
            .clearAndSetSemantics {
                if (accessibilityVisible) {
                    contentDescription = "Focus timer, $timerText remaining"
                } else {
                    hideFromAccessibility()
                }
            },
    ) {
        Text(
            text = timerText,
            color = Color.White,
            fontSize = (31f * textScale).sp,
            lineHeight = (34f * textScale).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
        )
        Text(
            text = if (card == 0) "TOUCH GRASS" else "QUIET DAY",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = (10f * textScale).sp,
            lineHeight = (14f * textScale).sp,
            letterSpacing = (1.4f * textScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    val action by runtime.action
    if (action != GrassTimerAction.Hidden) {
        val label = if (action == GrassTimerAction.Resume) "Resume" else "Stop"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(point(82f), point(44f))
                .graphicsLayer {
                    runtime.drawRevision.intValue
                    val frame = runtime.frame.get()
                    val aperture = frame.aperture
                    translationX = ((aperture.x + frame.carouselOffset + aperture.width / 2) * scale).toFloat() -
                        size.width / 2
                    translationY = referenceOriginPx +
                        ((aperture.y + aperture.height / 2 + 54) * scale).toFloat() - size.height / 2
                    alpha = frame.timerOpacity.toFloat()
                }
                .clip(RoundedCornerShape(point(22f)))
                .background(Color.Black.copy(alpha = 0.25f))
                .border(
                    point(1f),
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(point(22f)),
                )
                .clickable(role = Role.Button) {
                    if (action == GrassTimerAction.Resume) {
                        runtime.mutate { at, reduced -> activate(at, reduced) }
                    } else {
                        runtime.mutate { at, reduced -> stop(at, reduced) }
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = if (action == GrassTimerAction.Resume) {
                        "Resume focus timer"
                    } else {
                        "Stop focus timer"
                    }
                },
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = (14f * textScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatTimer(seconds: Int): String = "%d:%02d:%02d".format(
    seconds / 3600,
    (seconds % 3600) / 60,
    seconds % 60,
)
