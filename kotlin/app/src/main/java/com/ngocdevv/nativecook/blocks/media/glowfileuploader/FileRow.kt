package com.ngocdevv.nativecook.blocks.media.glowfileuploader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val ROW_HEIGHT = 48f
internal const val ROW_GAP = 10f
private val RowShape = RoundedCornerShape(18.dp)
private val RowFill = Color(0xFF181B1F)
private val ProgressFill = Color(0xFF22262B)
private val FailedFill = Color(0xFF3A1A1C).copy(alpha = 0.7f)

/** The bright leading edge of the progress fill: this wide, up to this white. */
private const val EDGE = 24f
private val EdgeLight = Color.White.copy(alpha = 0.1f)

private const val CLIP_CENTER = 20f
private const val CLIP_SIZE = 14f
private val ClipColor = Color(0xFF7E8187)
private const val CLIP_STROKE = 1.2f
private const val NAME_START = 40f
private const val NAME_SIZE = 11f
private val NameColor = Color(0xFFE8E9EA)
private const val PILL_GAP = 8f
private const val STATUS_END = 13f

/** Room kept for the status on the right, so a long name gives way instead of running under it. */
private const val STATUS_ROOM = 48f

// The ring while uploading, and the done disc with its check.
private const val STATUS = 18f
private const val RING_STROKE = 2f
private val RingTrack = Color.White.copy(alpha = 0.1f)
private val RingArc = Color(0xFFE8E9EA)
private val DoneFill = Color(0xFF1E3A23)
private val DoneCheck = Color(0xFF46C35A)
private const val CHECK_STROKE = 1.4f

/** The check's path is drawn for a 20 dp disc. */
private const val CHECK_BOX = 20f

// "Upload failed".
private const val PILL_HEIGHT = 21f
private const val PILL_TEXT = 9.5f
private const val PILL_SPACING = 5f
private val PillFill = Color(0xFF3A1A1C)
private const val BANG_DISC = 10.5f

/** The "!" is drawn for a 14 dp disc. */
private const val BANG_BOX = 14f
private val BangFill = Color(0xFFE5484D)
private val PillText = Color(0xFFFF5F57)

// "Retry".
private val RetryColor = Color(0xFF8B8E93)
private const val ARROWS = 13f
private const val ARROWS_STROKE = 1.8f
private const val RETRY_GAP = 6f
private const val RETRY_TEXT = 11f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * One file in the list. Its slot grows and collapses with the clock (a layout change only), and
 * everything in it is drawn from [row]'s times, so a frame does not recompose it.
 */
@Composable
internal fun FileRow(
    row: Upload,
    clock: State<Double>,
    still: Boolean,
    canRetry: Boolean,
    retryLabel: String,
    failedLabel: String,
    onRetryDown: () -> Unit,
    onRetryLift: () -> Unit,
    onRetry: () -> Unit,
) {
    val retryTouch = remember { MutableInteractionSource() }
    val down by rememberUpdatedState(onRetryDown)
    val lift by rememberUpdatedState(onRetryLift)
    LaunchedEffect(retryTouch) {
        retryTouch.interactions.collect {
            when (it) {
                is PressInteraction.Press -> down()
                is PressInteraction.Release, is PressInteraction.Cancel -> lift()
            }
        }
    }
    val check = remember { TrimmedPath(DiscCheck) }

    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val share = row.slot(clock.value, still).coerceAtLeast(0.0).toFloat()
                val card = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val slot = ((ROW_GAP + ROW_HEIGHT) * share).dp.roundToPx()
                layout(card.width, slot) { card.place(0, (ROW_GAP * share).dp.roundToPx()) }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .graphicsLayer {
                    val now = clock.value
                    val look = row.card(now, still)
                    alpha = look.alpha
                    translationX = row.shake(now, still).toFloat().dp.toPx()
                    translationY = look.dy.dp.toPx()
                    scaleX = look.scale
                    scaleY = look.scale
                    clip = true
                    shape = RowShape
                }
                .background(RowFill)
                .drawBehind {
                    val f = row.fill(clock.value)
                    val w = size.width * f.width
                    if (w > 0f && f.alpha > 0f) {
                        drawRect(lerp(ProgressFill, FailedFill, f.tint), size = Size(w, size.height), alpha = f.alpha)
                        if (f.edge > 0f) {
                            val edge = EDGE.dp.toPx()
                            clipRect(right = w) {
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Transparent, EdgeLight), startX = w - edge, endX = w),
                                    Offset(w - edge, 0f),
                                    Size(edge, size.height),
                                    alpha = f.alpha * f.edge,
                                )
                            }
                        }
                    }
                    scale(density, density, pivot = Offset.Zero) {
                        paperclip(Offset(CLIP_CENTER, ROW_HEIGHT / 2f), CLIP_SIZE, ClipColor, CLIP_STROKE)
                    }
                },
        ) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = NAME_START.dp, end = (STATUS_END + STATUS_ROOM).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    color = NameColor,
                    fontSize = NAME_SIZE.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.failPoint != null) {
                    Spacer(Modifier.width(PILL_GAP.dp))
                    FailedPill(row, clock, still, failedLabel)
                }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = STATUS_END.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Canvas(Modifier.size(STATUS.dp)) {
                    scale(density, density, pivot = Offset.Zero) {
                        val now = clock.value
                        val c = Offset(STATUS / 2f, STATUS / 2f)
                        val ring = row.ring(now, still)
                        if (ring.alpha > 0f && ring.scale > 0f) {
                            scale(ring.scale, ring.scale, pivot = c) {
                                val r = STATUS / 2f - RING_STROKE / 2f
                                drawCircle(RingTrack, r, c, ring.alpha, Stroke(RING_STROKE))
                                if (ring.progress > 0.002f) {
                                    drawArc(
                                        RingArc,
                                        -90f,
                                        360f * ring.progress,
                                        false,
                                        Offset(c.x - r, c.y - r),
                                        Size(r * 2f, r * 2f),
                                        ring.alpha,
                                        Stroke(RING_STROKE, cap = StrokeCap.Round),
                                    )
                                }
                            }
                        }
                        val done = row.done(now, still)
                        if (done.alpha > 0f) {
                            scale(done.scale, done.scale, pivot = c) {
                                drawCircle(DoneFill, STATUS / 2f, c, done.alpha)
                                val k = STATUS / CHECK_BOX
                                scale(k, k, Offset.Zero) { check.draw(this, done.trim, DoneCheck, CHECK_STROKE / k, done.alpha) }
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .graphicsLayer {
                            val look = row.retryLook(clock.value, still)
                            alpha = look.alpha
                            translationX = look.dx.dp.toPx()
                            scaleX = look.scale
                            scaleY = look.scale
                        }
                        .clickable(interactionSource = retryTouch, indication = null, enabled = canRetry, role = Role.Button, onClick = onRetry)
                        .semantics { contentDescription = "$retryLabel ${row.name}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(ARROWS.dp)) {
                        scale(density, density, pivot = Offset.Zero) {
                            circleArrows(ARROWS, RetryColor, ARROWS_STROKE, row.retryLook(clock.value, still).spin)
                        }
                    }
                    Spacer(Modifier.width(RETRY_GAP.dp))
                    Text(retryLabel, color = RetryColor, fontSize = RETRY_TEXT.sp, maxLines = 1)
                }
            }
        }
    }
}

/** "Upload failed": uncovered from the left as a capsule that widens, with its "!" popping. */
@Composable
private fun FailedPill(row: Upload, clock: State<Double>, still: Boolean, label: String) {
    val window = remember { Path() }
    Row(
        Modifier
            .graphicsLayer { alpha = row.pill(clock.value, still).alpha }
            .drawWithContent {
                val open = row.pill(clock.value, still).open
                if (open <= 0f) return@drawWithContent
                if (open >= 1f) {
                    drawContent()
                    return@drawWithContent
                }
                window.reset()
                window.addRoundRect(RoundRect(0f, 0f, size.width * open, size.height, CornerRadius(size.height / 2f)))
                clipPath(window) { this@drawWithContent.drawContent() }
            }
            .height(PILL_HEIGHT.dp)
            .background(PillFill, CircleShape)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(BANG_DISC.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                val b = row.pill(clock.value, still).bang
                val c = Offset(BANG_DISC / 2f, BANG_DISC / 2f)
                scale(b, b, pivot = c) {
                    drawCircle(BangFill, BANG_DISC / 2f, c)
                    scale(BANG_DISC / BANG_BOX, BANG_DISC / BANG_BOX, Offset.Zero) { bang(Color.White) }
                }
            }
        }
        Spacer(Modifier.width(PILL_SPACING.dp))
        Text(
            label,
            color = PillText,
            fontSize = PILL_TEXT.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp,
            maxLines = 1,
            style = TextStyle(lineHeightStyle = Centered),
        )
    }
}
