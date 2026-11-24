package com.ngocdevv.nativecook.blocks.modals.peekpoppreview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

// PeekPreview: long-press an item and it lifts into a preview card; swipe up and the card becomes the page.
// Reusable: this file knows nothing about articles. It owns the gestures, the frame that morphs between the three
// rects, the blurred backdrop and the quick actions. What is drawn inside the frame is the caller's.

internal data class PeekRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun contains(px: Float, py: Float, slop: Float = 0f) =
        px >= x - slop && px <= x + width + slop && py >= y - slop && py <= y + height + slop
}

/** Where an item rests, where its preview floats and what the open page fills. */
internal class PeekGeometry(val rows: List<PeekRect>, val card: PeekRect, val full: PeekRect)

/** The frame's rect now: row, preview card, page. Springs' overshoot shows, so nothing is clamped. */
internal fun PeekState.frameRect(geometry: PeekGeometry): PeekRect {
    val row = geometry.rows.getOrNull(max(active, 0)) ?: geometry.card
    val card = geometry.card
    val full = geometry.full
    val pk = pk
    val pp = pop.value
    return PeekRect(
        x = mix3(row.x, card.x, full.x, pk, pp),
        y = mix3(row.y, card.y, full.y, pk, pp),
        width = max(1f, mix3(row.width, card.width, full.width, pk, pp)),
        height = max(1f, mix3(row.height, card.height, full.height, pk, pp)),
    )
}

/** Everything behind the preview: it sinks back a little and blurs as the preview comes up (blur from API 31). */
@Composable
internal fun PeekScene(state: PeekState, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val k = clampTo(state.pk, 0f, 1f)
                val s = mix(1f, SCENE_SCALE, k)
                scaleX = s
                scaleY = s
                // Nothing behind the open page can be seen, so the blur is let go once it fills the screen.
                val blur = BACKDROP_BLUR * k * (1f - seg(state.pop.value, 0.9f, 1f)) * density
                renderEffect = if (blur > 0.25f) BlurEffect(blur, blur, TileMode.Clamp) else null
            },
        content = content,
    )
}

/** The blur's light wash and the dim, between the scene and the preview. */
@Composable
internal fun PeekBackdrop(state: PeekState, tint: Color = Color(0xFF14110D)) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val k = clampTo(state.pk, 0f, 1f)
                if (k <= 0f) return@drawBehind
                drawRect(Color.White.copy(alpha = BACKDROP_WASH * k * (1f - seg(state.pop.value, 0.9f, 1f))))
                drawRect(tint.copy(alpha = SCENE_DIM * k))
            },
    )
}

/**
 * The frame: one rounded, shadowed box that is the row, then the preview card, then the page. It is a full-screen
 * layer that clips to the rect, so nothing inside is measured again while it moves; [content] is placed from the
 * frame's top-left corner.
 */
@Composable
internal fun PeekFrame(
    state: PeekState,
    geometry: PeekGeometry,
    backgroundColor: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val restNear = remember { SoftRoundRect(Color(26 / 255f, 24 / 255f, 18 / 255f, 0.08f), sigma = 1f) }
    val restFar = remember { SoftRoundRect(Color(26 / 255f, 24 / 255f, 18 / 255f, 0.05f), sigma = 5f) }
    val liftNear = remember { SoftRoundRect(Color(26 / 255f, 24 / 255f, 18 / 255f, 0.16f), sigma = 3f) }
    val liftFar = remember { SoftRoundRect(Color(26 / 255f, 24 / 255f, 18 / 255f, 0.30f), sigma = 26f) }
    val clip = remember { Path() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (state.active < 0) 0f else 1f
                // The long press swells the row, content and all, about its centre.
                val r = state.frameRect(geometry)
                val s = 1f + PRESS_SCALE * state.press.value
                scaleX = s
                scaleY = s
                if (size.width > 0f && size.height > 0f) {
                    transformOrigin = TransformOrigin(
                        (r.x + r.width / 2f) * density / size.width,
                        (r.y + r.height / 2f) * density / size.height,
                    )
                }
            }
            .drawWithContent {
                if (state.active < 0) return@drawWithContent
                val r = state.frameRect(geometry)
                val pk = clampTo(state.pk, 0f, 1f)
                val pp = clampTo(state.pop.value, 0f, 1f)
                val radius = max(0f, mix3(RADIUS_ROW, RADIUS_CARD, 0f, pk, pp))
                val lifted = clampTo(max(state.press.value, state.pk), 0f, 1f) * (1f - seg(state.pop.value, 0.7f, 1f))
                val d = density
                clip.rewind()
                clip.addRoundRect(
                    RoundRect(r.x * d, r.y * d, (r.x + r.width) * d, (r.y + r.height) * d, CornerRadius(radius * d)),
                )
                clipPath(clip, ClipOp.Difference) {
                    scale(d, d, pivot = Offset.Zero) {
                        drawIntoCanvas {
                            val canvas = it.nativeCanvas
                            if (lifted > 0.003f) {
                                liftFar.draw(canvas, r.x, r.y + 26f, r.width, r.height, radius, lifted)
                                liftNear.draw(canvas, r.x, r.y + 2f, r.width, r.height, radius, lifted)
                            }
                            restFar.draw(canvas, r.x, r.y + 4f, r.width, r.height, radius)
                            restNear.draw(canvas, r.x, r.y + 1f, r.width, r.height, radius)
                        }
                    }
                }
                clipPath(clip) {
                    drawRect(backgroundColor, Offset(r.x * d, r.y * d), Size(r.width * d, r.height * d))
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val r = state.frameRect(geometry)
                    translationX = r.x * density
                    translationY = r.y * density
                },
            content = content,
        )
    }
}

internal class PeekActionItem(val label: String, val icon: ImageVector, val destructive: Boolean)

/** The quick actions under the preview: a panel that rises in, its rows a beat apart. Touch comes through [peekGestures]. */
@Composable
internal fun PeekActions(
    state: PeekState,
    rect: PeekRect,
    items: List<PeekActionItem>,
    pressed: Int,
    style: TextStyle,
    ink: Color,
    danger: Color,
    backgroundColor: Color,
    onAction: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val hairline = with(LocalDensity.current) { 1f.toDp() }
    Column(
        Modifier
            .offset(rect.x.dp, rect.y.dp)
            .width(rect.width.dp)
            .then(if (state.active < 0) Modifier.clearAndSetSemantics { } else Modifier)
            .graphicsLayer {
                val pk = clampTo(state.pk, 0f, 1f)
                val gone = seg(state.pop.value, 0f, 0.35f)
                val from = ACTION_AT[0] - 0.12f
                val u = easeOutCubic(seg(state.reveal.value, from, from + REVEAL_SPAN)) * seg(pk, 0.5f, 0.85f)
                alpha = if (state.active < 0) 0f else u * (1f - gone)
                translationY = (-PANEL_RISE * (1f - u) + 36f * gone) * density
                val s = mix(PANEL_SCALE, 1f, u)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .cssBoxShadow(Color(20 / 255f, 17 / 255f, 13 / 255f, 0.22f), 18.dp, 40.dp, 16.dp)
            .cssBoxShadow(Color(20 / 255f, 17 / 255f, 13 / 255f, 0.14f), 1.dp, 3.dp, 16.dp)
            .clip(shape)
            .background(backgroundColor),
    ) {
        items.forEachIndexed { i, item ->
            val tint = if (item.destructive) danger else ink
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((rect.height / items.size).dp)
                    .background(if (pressed == i) Color(26 / 255f, 27 / 255f, 30 / 255f, 0.07f) else Color.Transparent)
                    .semantics {
                        role = Role.Button
                        contentDescription = item.label
                        onClick {
                            onAction(i)
                            true
                        }
                    },
            ) {
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(hairline)
                            .background(Color(26 / 255f, 27 / 255f, 30 / 255f, 0.12f)),
                    )
                }
                val start = ACTION_AT[minOf(i, ACTION_AT.lastIndex)]
                Row(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val u = easeOutCubic(seg(state.reveal.value, start, start + REVEAL_SPAN))
                            alpha = u
                            translationY = ACTION_RISE * (1f - u) * density
                        }
                        .padding(start = 16.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = style.copy(color = tint), modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

/** What the single touch handler needs to know about the screen, read fresh on every touch. */
internal class PeekTouchTargets(
    val geometry: () -> PeekGeometry,
    val reduced: () -> Boolean,
    /** The page's back button, in screen dp. */
    val back: () -> PeekRect,
    /** The quick actions panel, in screen dp, and how many rows it has. */
    val actions: () -> PeekRect,
    val actionCount: Int,
    val onPressedAction: (Int) -> Unit,
    val onPeek: () -> Unit,
    val onPop: () -> Unit,
    val onClose: () -> Unit,
    val onBack: () -> Unit,
    val onAction: (Int) -> Unit,
)

/**
 * Every touch of the screen, in one handler, because the targets are layers that move: the row (long press to peek,
 * a tap to open), the frame (drag and tap), the page's back button, the actions and the backdrop.
 */
internal fun Modifier.peekGestures(state: PeekState, targets: PeekTouchTargets): Modifier = pointerInput(state, targets) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val d = density
        val x = down.position.x / d
        val y = down.position.y / d
        val reduced = targets.reduced()
        val geometry = targets.geometry()
        state.peekTouch(reduced)

        val frameLive = state.active >= 0 && (state.pk > 0.3f || state.pop.value > 0.3f)
        val actionsLive = state.active >= 0 && state.reveal.value > 0.9f && state.pop.value < 0.1f
        val actionsRect = targets.actions()
        when {
            frameLive && state.pop.value > 0.8f && targets.back().contains(x, y, slop = 8f) -> {
                val up = awaitRelease(down.id, viewConfiguration.touchSlop)
                if (up) targets.onBack()
            }
            frameLive && state.frameRect(geometry).contains(x, y) -> frameGesture(state, down, targets, reduced)
            actionsLive && actionsRect.contains(x, y) -> {
                val index = ((y - actionsRect.y) / actionsRect.height * targets.actionCount)
                    .toInt().coerceIn(0, targets.actionCount - 1)
                targets.onPressedAction(index)
                val up = try {
                    awaitRelease(down.id, viewConfiguration.touchSlop)
                } finally {
                    targets.onPressedAction(-1)
                }
                if (up) targets.onAction(index)
            }
            frameLive -> {
                // The backdrop: a tap closes.
                if (awaitRelease(down.id, viewConfiguration.touchSlop)) {
                    state.peekClose(0f, reduced)
                    targets.onClose()
                }
            }
            else -> {
                val index = geometry.rows.indexOfFirst { it.contains(x, y) }
                if (index >= 0) rowGesture(state, index, down, targets, reduced)
            }
        }
    }
}

/** Waits for the finger to lift; true if it stayed within [slop] px of where it landed. */
private suspend fun AwaitPointerEventScope.awaitRelease(id: PointerId, slop: Float): Boolean {
    var start: Offset? = null
    var inside = true
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        val origin = start ?: change.previousPosition.also { start = it }
        if ((change.position - origin).getDistance() > slop) inside = false
        if (!change.pressed) return inside
    }
}

/**
 * An item that can be peeked. Press and hold: it swells, then its preview comes up under the finger; keep the
 * finger down and swipe up to pop, or pull down to let it fall back. A tap opens the page directly.
 */
private suspend fun AwaitPointerEventScope.rowGesture(
    state: PeekState,
    index: Int,
    down: PointerInputChange,
    targets: PeekTouchTargets,
    reduced: Boolean,
) {
    val d = density
    state.held += 1
    var opened = false
    try {
        if (state.active != index) {
            // Another item was still on its way home: it is home now.
            state.peek.set(0f)
            state.sink.set(0f)
            state.pop.set(0f)
            state.reveal.set(0f)
        }
        state.active = index
        state.wants = false
        state.press.timing(1f, PRESS, ::easeOutCubic)

        // Until the long press fires: a lift is a tap (if quick enough), a move fails it.
        val slop = viewConfiguration.touchSlop
        var lifted: PointerInputChange? = null
        var moved = false
        val fired = withTimeoutOrNull((PRESS * 1000f).toLong()) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    lifted = change
                    break
                }
                if ((change.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
            false
        } ?: true

        if (!fired) {
            val up = lifted
            if (!moved && up != null && up.uptimeMillis - down.uptimeMillis < (PRESS * 1000f).toLong() - 40L) {
                state.active = index
                state.peekPop(0f, reduced)
                opened = true
                targets.onPop()
            }
            return
        }

        opened = true
        state.peekOpen(reduced)
        targets.onPeek()
        val tracker = VelocityTracker()
        var startY = Float.NaN
        var travel = 0f
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (startY.isNaN()) startY = change.previousPosition.y
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!change.pressed) break
            change.consume()
            travel = (change.position.y - startY) / d
            state.peekDrag(travel)
        }
        val velocity = tracker.calculateVelocity().y / d
        when (state.peekRelease(travel, velocity, reduced)) {
            PeekRelease.Popped -> targets.onPop()
            PeekRelease.Closed -> targets.onClose()
            PeekRelease.Stayed -> Unit
        }
    } finally {
        state.held = max(0, state.held - 1)
        if (!opened && !state.wants) state.press.timing(0f, 0.14f)
    }
}

/** Drag the frame: while peeking, up pops and down falls back; on the page it scrolls, and a pull from the top closes. */
private suspend fun AwaitPointerEventScope.frameGesture(
    state: PeekState,
    down: PointerInputChange,
    targets: PeekTouchTargets,
    reduced: Boolean,
) {
    val d = density
    state.held += 1
    try {
        val onPage = state.pop.value > 0.5f
        state.scroll.cancel()
        val grab = state.scroll.value
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var dragging = false
        var travel = 0f
        var far = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!change.pressed) break
            travel = (change.position.y - down.position.y) / d
            if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) > viewConfiguration.touchSlop) far = true
            if (!dragging && abs(travel) >= FRAME_SLOP) dragging = true
            if (dragging) {
                change.consume()
                if (onPage) state.pageDrag(grab, travel) else state.peekDrag(travel)
            }
        }
        if (dragging) {
            val velocity = tracker.calculateVelocity().y / d
            val result = if (onPage) state.pageRelease(grab, travel, velocity, reduced) else state.peekRelease(travel, velocity, reduced)
            when (result) {
                PeekRelease.Popped -> targets.onPop()
                PeekRelease.Closed -> targets.onClose()
                PeekRelease.Stayed -> Unit
            }
        } else if (!far && state.pop.value <= 0.5f && state.pk >= 0.5f) {
            // A tap on the preview opens it.
            state.peekPop(0f, reduced)
            targets.onPop()
        }
    } finally {
        state.held = max(0, state.held - 1)
    }
}

/** Blur radius behind the preview, dp: what the React Native BlurView's intensity 16 reads as. */
private const val BACKDROP_BLUR = 6f
private const val BACKDROP_WASH = 0.12f
private const val FRAME_SLOP = 6f
