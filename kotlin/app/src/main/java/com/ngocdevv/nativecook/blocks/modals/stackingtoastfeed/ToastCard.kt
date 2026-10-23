package com.ngocdevv.nativecook.blocks.modals.stackingtoastfeed

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ToastKind { Error, Warning, Info, Success }

/** The text before the link, the link itself, and what comes after it. */
class ToastMessage(val kind: ToastKind, val title: String, val message: String, val link: String? = null, val tail: String? = null)

/** [leaving] is set while the toast leaves: 1 to the right, -1 to the left, 0 shrinks and fades. */
internal class ToastItem(val id: Int, val message: ToastMessage, val leaving: Int? = null) {
    fun leaving(direction: Int) = ToastItem(id, message, direction)
}

internal const val TOAST_HEIGHT = 84f
internal const val TOAST_GAP = 12f
internal const val SPREAD_TOP = 64f

// Collapsed, each toast behind the front one shows this much, and is this much smaller.
private const val STACK_STEP = 12f
private const val STACK_SHRINK = 0.04f
private const val STACK_DIM = 0.04f
private const val STACK_MIN_SCALE = 0.8f

// A spring of 450 ms with damping ratio 0.8: stiffness (2π / 0.45)².
private const val SLIDE_DAMPING = 0.8f
private const val SLIDE_STIFFNESS = 195f
private const val SHAKE_POINTS = 4f
private const val SHAKE_MS = 50
private const val SWIPE_MS = 450
private const val FADE_MS = 350
private const val SHRINK_MS = 250
private const val SHRINK_TO = 0.9f
private const val TILT_DEGREES = 12f

// A drag beyond this distance, or this fast, sends the toast away.
private const val SWIPE_DISTANCE = 90f
private const val SWIPE_SPEED = 800f

private class Look(val band: Color, val ink: Color, val icon: ImageVector)

private fun look(kind: ToastKind) = when (kind) {
    ToastKind.Error -> Look(Color(0xFFFFDADD), Color(0xFFD6212A), Icons.Outlined.ErrorOutline)
    ToastKind.Warning -> Look(Color(0xFFFFF8C4), Color(0xFF857400), Icons.Outlined.WarningAmber)
    ToastKind.Info -> Look(Color(0xFFD8F1FF), Color(0xFF0B4C8C), Icons.Outlined.Info)
    ToastKind.Success -> Look(Color(0xFFDDFBD7), Color(0xFF1E8A1E), Icons.Outlined.CheckCircle)
}

private const val RADIUS = 14f

// Two shadows: a tight one under the toast and a wide one further down.
private val ShadowNear = SoftRoundRect(Color(0xFF111318).copy(alpha = 0.08f), 1.5f)
private val ShadowFar = SoftRoundRect(Color(0xFF111318).copy(alpha = 0.14f), 14f)
private val Edge = Color(0xFF111318).copy(alpha = 0.08f)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun toastText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, tracking: Float = 0f) = TextStyle(
    color = color,
    letterSpacing = tracking.sp,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = toastText(17f, 22f, FontWeight.Bold, Color(0xFF111318), -0.3f)
private val BodyStyle = toastText(14f, 19f, FontWeight.Normal, Color(0xFF5F636B))

private fun copyOf(message: ToastMessage): AnnotatedString = buildAnnotatedString {
    append(message.message)
    message.link?.let { withStyle(SpanStyle(color = Color(0xFF2B2E35), textDecoration = TextDecoration.Underline)) { append(it) } }
    message.tail?.let { append(it) }
}

/**
 * One toast. Its place comes from [index] alone: a spring moves `slot` there, so a toast that
 * enters or leaves lets the others reflow without separate animations.
 *
 * @param index Place in the pile among the toasts that stay: 0 is the front.
 * @param stack 0 when the toasts are spread out, 1 when they are collapsed into a pile. Read while drawing only.
 */
@Composable
internal fun ToastCard(
    item: ToastItem,
    index: Int,
    stack: () -> Float,
    width: Float,
    screenWidth: Float,
    reduced: Boolean,
    onLeave: (id: Int, direction: Int) -> Unit,
    onGone: (id: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val look = look(item.message.kind)
    val leaving = item.leaving
    val slot = remember { Channel(index.toFloat(), scope) }
    val enter = remember { Channel(0f, scope) }
    val shake = remember { Channel(0f, scope) }
    val drag = remember { Channel(0f, scope) }
    val shrink = remember { Channel(1f, scope) }
    val gone = remember { Channel(0f, scope) }
    val leave by rememberUpdatedState(onLeave)
    val finished by rememberUpdatedState(onGone)
    val slop = LocalViewConfiguration.current.touchSlop

    LaunchedEffect(Unit) {
        if (reduced) {
            enter.go { animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) }
        } else {
            enter.go { animateTo(1f, spring(SLIDE_DAMPING, SLIDE_STIFFNESS)) }
            shake.go {
                for (point in listOf(SHAKE_POINTS, -SHAKE_POINTS, SHAKE_POINTS, -SHAKE_POINTS, 0f)) {
                    animateTo(point, tween(SHAKE_MS, easing = EaseInOutQuad))
                }
            }
        }
    }

    LaunchedEffect(index, leaving) {
        if (leaving != null) return@LaunchedEffect
        slot.go { animateTo(index.toFloat(), if (reduced) tween(SHRINK_MS, easing = EaseInOutQuad) else spring(SLIDE_DAMPING, SLIDE_STIFFNESS)) }
    }

    // Runs the exit when `leaving` is set, and tells the feed once the toast is gone.
    LaunchedEffect(leaving) {
        if (leaving == null) return@LaunchedEffect
        val ms: Int
        if (leaving == 0 || reduced) {
            ms = if (reduced) FADE_MS else SHRINK_MS
            shrink.go { animateTo(if (reduced) 1f else SHRINK_TO, tween(ms, easing = EaseInOutQuad)) }
            gone.go { animateTo(1f, tween(ms, easing = EaseInOutQuad)) }
        } else {
            // The swipe starts as a finger would: slowly, then it lets go.
            ms = SWIPE_MS
            drag.go { animateTo(leaving * screenWidth * 1.1f * 1f, tween(ms, easing = EaseInQuad)) }
            gone.go { animateTo(1f, tween(ms, easing = EaseInCubic)) }
        }
        delay(ms.toLong())
        finished(item.id)
    }

    Box(
        Modifier
            .zIndex((100 - index).toFloat())
            .width(width.dp)
            .height(TOAST_HEIGHT.dp)
            .graphicsLayer {
                val s = stack()
                val p = max(slot.value, 0f)
                val spreadY = SPREAD_TOP + (TOAST_HEIGHT + TOAST_GAP) * p
                val stackedY = SPREAD_TOP + STACK_STEP * p
                val scale = (1f + (max(STACK_MIN_SCALE, 1f - STACK_SHRINK * p) - 1f) * s) * shrink.value
                val y = spreadY + (stackedY - spreadY) * s - (1f - enter.value) * (SPREAD_TOP + TOAST_HEIGHT + 24f)
                transformOrigin = TransformOrigin(0.5f, 0f)
                translationX = (20f + shake.value + drag.value) * density
                translationY = y * density
                rotationZ = if (reduced) 0f else drag.value / screenWidth * TILT_DEGREES
                scaleX = scale
                scaleY = scale
                alpha = if (reduced) {
                    min(1f, enter.value) * (1f - gone.value)
                } else {
                    (1f - gone.value * (if (leaving == 0) 1f else 0.9f)) * min(1f, enter.value * 4f)
                }
            }
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas {
                        ShadowNear.draw(it.nativeCanvas, 0f, 1f, width, TOAST_HEIGHT, RADIUS)
                        ShadowFar.draw(it.nativeCanvas, 0f, 12f, width, TOAST_HEIGHT, RADIUS)
                    }
                }
            }
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(Color.White)
            .border(Dp.Hairline, Edge, RoundedCornerShape(RADIUS.dp))
            .pointerInput(leaving == null) {
                if (leaving != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                    drag.set((start.position.x - down.position.x) / density)
                    horizontalDrag(start.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        drag.set((change.position.x - down.position.x) / density)
                        change.consume()
                    }
                    val moved = drag.value
                    if (abs(moved) > SWIPE_DISTANCE || abs(tracker.calculateVelocity().x / density) > SWIPE_SPEED) {
                        leave(item.id, if (moved > 0f) 1 else -1)
                    } else {
                        drag.go { animateTo(0f, spring(0.8f, 322f)) }
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxHeight()) {
            Box(Modifier.width(52.dp).fillMaxHeight().background(look.band), contentAlignment = Alignment.Center) {
                Icon(look.icon, null, Modifier.size(27.dp), tint = look.ink)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 40.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                BasicText(item.message.title, Modifier.height(22.dp), style = TitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(copyOf(item.message), Modifier.padding(top = 2.dp), style = BodyStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .size(28.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { leave(item.id, 0) }
                .semantics { contentDescription = "Dismiss" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, null, Modifier.size(22.dp), tint = Color(0xFF8A8D93))
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = STACK_DIM * max(slot.value, 0f) * stack() }
                .background(Color.Black),
        )
    }
}
