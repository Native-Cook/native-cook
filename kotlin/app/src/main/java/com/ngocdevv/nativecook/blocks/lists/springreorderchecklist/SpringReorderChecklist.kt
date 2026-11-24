package com.ngocdevv.nativecook.blocks.lists.springreorderchecklist

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val PAGE = Color(0xFFF5F2EA)
private val CARD = Color(0xFFFFFDF8)
private val INK = Color(0xFF26302B)
private val MUTE = Color(0xFF7B8078)
private val HAIR = Color(0x1A26302B) // rgba(38,48,43,0.1)
private val GREEN = Color(0xFF2F7D5B)
private val RING = Color(38, 48, 43).copy(alpha = 0.28f)
private val STRIKE = Color(38, 48, 43).copy(alpha = 0.55f)
private val GRIP = Color(38, 48, 43).copy(alpha = 0.3f)
private val GHOST = Color(38, 48, 43).copy(alpha = 0.2f)
private val SHADOW = Color(32, 40, 34).copy(alpha = 0.2f)
private val TICK = Color(0xFFFBFFF9)

private const val CHECK_LEN = 16.8f
private const val CHECK_SIZE = 26f
private const val GRIP_X = 34f // from the right edge
private const val CHECK_X = 33f // centre of the circle from the left edge
private const val DOT = 44f

/** One row of the checklist. */
data class ChecklistItem(val id: String, val title: String, val note: String, val packed: Boolean = false)

/**
 * Everything that moves, written by the frame loop and the gestures and read only in layer and draw lambdas (through
 * [tick]), so nothing recomposes per frame. Mirrors the shared values of the React Native version.
 */
@Stable
private class ChecklistState(val count: Int, val homeChecks: IntArray, film: Boolean) {
    val filmTo = min(FILM_TO, count - 1)
    var film = film
    var mode = if (film) MODE_AUTO else MODE_MANUAL

    var tick by mutableLongStateOf(0L)
    val lifted = mutableStateListOf<Boolean>().apply { repeat(count) { add(false) } }
    val checkedList = mutableStateListOf<Boolean>().apply { homeChecks.forEach { add(it == 1) } }

    var clock = 0.0
    var loopStart = -1.0
    var filmT = 0.0
    var lastTouch = 0.0
    var returnAt = 0.0
    var holding = false

    var order = IntArray(count) { it }
    var checked = homeChecks.copyOf()

    var dragId = -1
    var dragY = 0f
    var startY = 0f
    var startFinger = 0f
    var startScroll = 0f
    var finger = 0f // viewport dp
    var scrollY = 0f // dp
    var viewH = 0f
    var viewW = 0f
    var contentH = 0f

    var ghost = 0f
    var ghostX = 0f
    var ghostY = 0f
    var ghostTap = 0f

    // Per row: position (dp), lift (0…1) and the check's draw (0…1), with their springs and timings.
    val y = FloatArray(count) { it * ROW_H }
    val yV = FloatArray(count)
    val yTo = FloatArray(count) { it * ROW_H }
    val yOn = BooleanArray(count)
    val ySpec = Array(count) { PART_SPRING }
    val lift = FloatArray(count)
    val liftV = FloatArray(count)
    val liftTo = FloatArray(count)
    val liftOn = BooleanArray(count)
    val draw = FloatArray(count) { homeChecks[it].toFloat() }
    val drawFrom = FloatArray(count)
    val drawTo = FloatArray(count)
    val drawStart = DoubleArray(count)
    val drawDur = DoubleArray(count)
    val drawOn = BooleanArray(count)

    // The previous value of each "reaction" key, as in React Native's useAnimatedReaction.
    val slotKey = IntArray(count) { slotKeyOf(it) }
    val liftKey = IntArray(count) { liftKeyOf(it) }
    val drawKey = IntArray(count) { drawKeyOf(it) }
    var packedKey = -1

    var onSlotTick: () -> Unit = {}
    var onPacked: (Int) -> Unit = {}
    var scope: CoroutineScope? = null
    var scrollJob: Job? = null

    fun slotKeyOf(i: Int) = if (mode == MODE_AUTO) -1 else if (dragId == i) -2 else order[i]
    fun liftKeyOf(i: Int) = if (mode == MODE_AUTO) -1 else if (dragId == i) 1 else 0
    fun drawKeyOf(i: Int) = if (mode == MODE_AUTO) -1 else checked[i]

    /** The first touch during the film: its order and checks become the real ones. */
    fun takeOver() {
        holding = true
        lastTouch = clock
        if (mode != MODE_AUTO) {
            mode = MODE_MANUAL
            return
        }
        val t = filmT
        val slot = filmHeroSlot(t, FILM_FROM, filmTo).roundToInt().coerceIn(FILM_FROM, filmTo)
        order = moveOrder(order, FILM_FROM, slot)
        if (filmCheck(t) > 0.5) checked = checked.copyOf().also { it[FILM_FROM] = 1 }
        mode = MODE_MANUAL
    }

    fun release() {
        holding = false
        lastTouch = clock
    }

    /** Places the held row under the finger and reorders the table when it crosses into another slot. */
    fun applyDrag() {
        val id = dragId
        if (id < 0) return
        val yy = startY + (finger - startFinger) + (scrollY - startScroll)
        dragY = clampF(yy, -ROW_H * 0.4f, (count - 1) * ROW_H + ROW_H * 0.4f)
        val slot = (dragY / ROW_H).roundToInt().coerceIn(0, count - 1)
        if (order[id] != slot) {
            order = moveOrder(order, id, slot)
            onSlotTick()
        }
        tick++
    }

    fun startDrag(i: Int) {
        val slot = order[i]
        startY = slot * ROW_H
        dragY = startY
        startFinger = finger
        startScroll = scrollY
        dragId = i
        tick++
    }

    fun toggle(i: Int) {
        checked = checked.copyOf().also { it[i] = if (it[i] == 1) 0 else 1 }
        tick++
    }

    private fun scrollBy(scroll: ScrollState, toDp: Float, density: Float) {
        val delta = (toDp - scrollY) * density
        if (abs(delta) > 0.01f) scroll.dispatchRawDelta(delta)
    }

    fun frame(now: Double, scroll: ScrollState, density: Float) {
        val dt = clamp(now - clock, 1.0 / 240, 0.05)
        clock = now
        var moved = false
        when (mode) {
            MODE_AUTO -> {
                if (!film) {
                    mode = MODE_MANUAL
                    ghost = 0f
                    moved = true
                } else if (viewH > 0f) {
                    if (loopStart < 0) loopStart = now
                    val t = (now - loopStart) % LOOP
                    filmT = t
                    val maxScroll = max(0f, contentH - viewH)
                    val distance = min(maxScroll, max(0f, (filmTo + 1) * ROW_H + 18f - viewH))
                    val sy = filmScroll(t, distance.toDouble()).toFloat()
                    scrollBy(scroll, sy, density)
                    scrollY = sy
                    val onCheck = filmGhostOnCheck(t)
                    ghost = clamp(filmGhost(t), 0.0, 1.0).toFloat()
                    ghostX = if (onCheck) CHECK_X else viewW - GRIP_X
                    ghostY = (filmHeroSlot(t, FILM_FROM, filmTo) * ROW_H + ROW_H / 2 - sy).toFloat()
                    ghostTap = filmGhostTap(t).toFloat()
                    for (i in 0 until count) {
                        y[i] = (filmRowSlot(i, t, FILM_FROM, filmTo) * ROW_H).toFloat()
                        yV[i] = 0f
                        yOn[i] = false
                    }
                    lift[FILM_FROM] = filmLift(t).toFloat()
                    liftV[FILM_FROM] = 0f
                    liftOn[FILM_FROM] = false
                    draw[FILM_FROM] = max(homeChecks[FILM_FROM].toDouble(), filmCheck(t)).toFloat()
                    drawOn[FILM_FROM] = false
                    moved = true
                }
            }
            MODE_MANUAL -> {
                if (ghost != 0f) {
                    ghost = 0f
                    moved = true
                }
                if (scroll.isScrollInProgress) lastTouch = now
                if (dragId >= 0) {
                    // Auto-scroll: faster the deeper the row is in the edge zone.
                    val center = dragY - scrollY + ROW_H / 2
                    val h = viewH
                    var v = 0f
                    if (center < EDGE_ZONE) v = -EDGE_SPEED * ((EDGE_ZONE - center) / EDGE_ZONE).pow(2)
                    else if (center > h - EDGE_ZONE) v = EDGE_SPEED * ((center - (h - EDGE_ZONE)) / EDGE_ZONE).pow(2)
                    if (v != 0f) {
                        val maxScroll = max(0f, contentH - h)
                        val next = clampF(scrollY + v * dt.toFloat(), 0f, maxScroll)
                        if (next != scrollY) {
                            scrollBy(scroll, next, density)
                            scrollY = next
                            applyDrag()
                        }
                    }
                } else {
                    scrollY = scroll.value / density
                    if (film && !holding && now - lastTouch > IDLE_S) {
                        mode = MODE_RETURN
                        returnAt = now + RETURN_S
                        order = IntArray(count) { it }
                        checked = homeChecks.copyOf()
                        scrollJob?.cancel()
                        scrollJob = scope?.launch { scroll.animateScrollTo(0) }
                    }
                }
            }
            else -> {
                scrollY = scroll.value / density
                if (now >= returnAt) {
                    scrollJob?.cancel()
                    scrollJob = null
                    if (scroll.value != 0) scroll.dispatchRawDelta(-scroll.value.toFloat())
                    scrollY = 0f
                    filmT = 0.0
                    loopStart = now
                    mode = MODE_AUTO
                }
            }
        }

        // Off the film, a row springs to its slot: parting for a neighbour, settling with an overshoot when dropped.
        for (i in 0 until count) {
            val sk = slotKeyOf(i)
            if (sk >= 0 && sk != slotKey[i]) {
                ySpec[i] = if (slotKey[i] == -2) DROP_SPRING else PART_SPRING
                yTo[i] = sk * ROW_H
                yOn[i] = true
            }
            slotKey[i] = sk
            val lk = liftKeyOf(i)
            if (lk >= 0 && lk != liftKey[i]) {
                liftTo[i] = lk.toFloat()
                liftOn[i] = true
            }
            liftKey[i] = lk
            val dk = drawKeyOf(i)
            if (dk >= 0 && dk != drawKey[i]) {
                drawFrom[i] = draw[i]
                drawTo[i] = dk.toFloat()
                drawStart[i] = now
                drawDur[i] = if (dk == 1) 0.42 else 0.26
                drawOn[i] = true
            }
            drawKey[i] = dk
        }

        val stepDt = dt.toFloat()
        for (i in 0 until count) {
            if (dragId == i) {
                y[i] = dragY
                yV[i] = 0f
                yOn[i] = false
                moved = true
            } else if (yOn[i] && mode != MODE_AUTO) {
                yOn[i] = stepSpring(y, yV, i, yTo[i], ySpec[i], stepDt, 0.02f)
                moved = true
            }
            if (liftOn[i]) {
                liftOn[i] = stepSpring(lift, liftV, i, liftTo[i], LIFT_SPRING, stepDt, 0.0005f)
                moved = true
            }
            if (drawOn[i]) {
                val k = ((now - drawStart[i]) / drawDur[i]).coerceIn(0.0, 1.0)
                draw[i] = mixF(drawFrom[i], drawTo[i], inOutQuad(k).toFloat())
                if (k >= 1.0) drawOn[i] = false
                moved = true
            }
            val up = lift[i] > 0.01f
            if (lifted[i] != up) lifted[i] = up
            val on = checked[i] == 1
            if (checkedList[i] != on) checkedList[i] = on
        }

        // The count the header shows: the table's, plus the film's check.
        var n = checked.sum()
        if (mode == MODE_AUTO && checked[FILM_FROM] == 0 && filmCheck(filmT) > 0.5) n += 1
        if (n != packedKey) {
            packedKey = n
            onPacked(n)
        }

        if (moved) tick++
    }
}

/** Reanimated's spring (mass 1), integrated in small steps; false once it has come to rest on its target. */
private fun stepSpring(x: FloatArray, v: FloatArray, i: Int, to: Float, spec: SpringSpec, dt: Float, rest: Float): Boolean {
    val steps = max(1, (dt * 480f).toInt())
    val h = dt / steps
    var p = x[i]
    var s = v[i]
    repeat(steps) {
        val a = -spec.stiffness * (p - to) - spec.damping * s
        s += a * h
        p += s * h
    }
    return if (abs(p - to) < rest && abs(s) < rest * 20f) {
        x[i] = to
        v[i] = 0f
        false
    } else {
        x[i] = p
        v[i] = s
        true
    }
}

/**
 * A checklist reordered by long-press. The held row lifts, its neighbours part on springs while it moves, it settles
 * with a small overshoot, and the check draws its stroke. Rows are transforms over a fixed order table: nothing is laid
 * out while dragging, and the list scrolls itself when the row nears an edge.
 */
@Composable
fun SpringReorderChecklist(
    items: List<ChecklistItem>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    /** Called after a drop with the ids in their new order. */
    onReorder: ((List<String>) -> Unit)? = null,
    /** Called with the number of checked rows whenever it changes. */
    onPackedChange: ((Int) -> Unit)? = null,
) {
    val reduced = rememberLiveReducedMotion()
    val film = autoPlay && !reduced
    val density = LocalDensity.current.density
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding().value
    val count = items.size
    val bottomPad = max(bottomInset, 12f) + 28f
    val contentH = count * ROW_H + bottomPad
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var locked by remember { mutableStateOf(false) }

    val state = remember(items) {
        ChecklistState(count, IntArray(count) { if (items[it].packed) 1 else 0 }, film)
    }
    val packedCallback by rememberUpdatedState(onPackedChange)
    val reorderCallback by rememberUpdatedState(onReorder)
    SideEffect {
        state.film = film
        state.contentH = contentH
        state.scope = scope
        state.onSlotTick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        state.onPacked = { n -> packedCallback?.invoke(n) }
    }

    LaunchedEffect(state, scroll) {
        while (true) {
            withFrameNanos { nanos -> state.frame(nanos / 1e9, scroll, density) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PAGE)
            .onSizeChanged {
                state.viewH = it.height / density
                state.viewW = it.width / density
            }
            // Watches every touch without taking it: the first one ends the film, and the finger's place drives a drag.
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val first = ev.changes.firstOrNull { it.pressed } ?: ev.changes.firstOrNull()
                        if (first != null) state.finger = first.position.y / density
                        if (ev.changes.any { it.changedToDownIgnoreConsumed() }) state.takeOver()
                        if (ev.changes.none { it.pressed }) state.release()
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = !locked)) {
            Box(Modifier.fillMaxWidth().height((count * ROW_H).dp)) {
                items.forEachIndexed { i, item ->
                    ChecklistRow(
                        item = item,
                        index = i,
                        state = state,
                        onLift = {
                            locked = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrop = { order ->
                            locked = false
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            reorderCallback?.let { cb ->
                                val ids = arrayOfNulls<String>(order.size)
                                order.forEachIndexed { id, slot -> ids[slot] = items[id].id }
                                cb(ids.map { it.orEmpty() })
                            }
                        },
                        onToggled = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    )
                }
            }
            Spacer(Modifier.height(bottomPad.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((44f + bottomInset * 0.5f).dp)
                .background(Brush.verticalGradient(listOf(PAGE.copy(alpha = 0f), PAGE.copy(alpha = 0.86f), PAGE))),
        )
        Box(
            Modifier
                .size(DOT.dp)
                .graphicsLayer {
                    state.tick
                    alpha = state.ghost * 0.9f
                    translationX = (state.ghostX - DOT / 2) * density
                    translationY = (state.ghostY - DOT / 2) * density
                    val s = 1f - 0.14f * state.ghostTap
                    scaleX = s
                    scaleY = s
                }
                .background(GHOST, CircleShape),
        )
    }
}

private val TITLE_STYLE = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Medium)
private val NOTE_STYLE = TextStyle(color = MUTE, fontSize = 13.sp, fontFeatureSettings = "tnum")

/** The check's stroke in its 24 × 24 view box. */
private val CHECK_PATH = Path().apply {
    moveTo(6.4f, 12.6f)
    lineTo(10.5f, 16.5f)
    lineTo(17.8f, 8.2f)
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    index: Int,
    state: ChecklistState,
    onLift: () -> Unit,
    onDrop: (IntArray) -> Unit,
    onToggled: () -> Unit,
) {
    val density = LocalDensity.current.density
    val measure = remember { PathMeasure().apply { setPath(CHECK_PATH, false) } }
    val partial = remember { Path() }
    val isChecked = state.checkedList[index]
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .zIndex(if (state.lifted[index]) 10f else 1f)
            .graphicsLayer {
                state.tick
                translationY = state.y[index] * density
                val s = 1f + (LIFT_SCALE - 1f) * state.lift[index]
                scaleX = s
                scaleY = s
            }
            .clearAndSetSemantics {
                contentDescription = "${item.title}, ${item.note}"
                role = Role.Checkbox
                stateDescription = if (isChecked) "Packed" else "Not packed"
            }
            .pointerInput(state, index) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 0: held long enough (drag), 1: a tap, 2: moved or cancelled (the list scrolls instead).
                    var result = 0
                    withTimeoutOrNull(LONG_PRESS_MS) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null) {
                                result = 2
                                break
                            }
                            if (!c.pressed) {
                                result = if (c.uptimeMillis - down.uptimeMillis <= LONG_PRESS_MS - 20) 1 else 2
                                if (result == 1) c.consume()
                                break
                            }
                            if (c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                result = 2
                                break
                            }
                        }
                    }
                    when (result) {
                        1 -> {
                            state.toggle(index)
                            onToggled()
                        }
                        0 -> {
                            state.startDrag(index)
                            onLift()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed) break
                                c.consume()
                                state.applyDrag()
                            }
                            if (state.dragId == index) {
                                state.dragId = -1
                                state.tick++
                                onDrop(state.order.copyOf())
                            }
                        }
                    }
                }
            },
    ) {
        // The page under a resting row, and the hairline: both give way to the plate as it lifts.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    state.tick
                    alpha = 1f - clampF(state.lift[index] * 2f, 0f, 1f)
                }
                .background(PAGE),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
                .cssBoxShadow(SHADOW, 1.dp, 3.dp, 16.dp) { state.tick; clampF(state.lift[index], 0f, 1f) }
                .cssBoxShadow(SHADOW, 14.dp, 30.dp, 16.dp) { state.tick; clampF(state.lift[index], 0f, 1f) }
                .drawBehind {
                    state.tick
                    val a = clampF(state.lift[index], 0f, 1f)
                    if (a > 0f) drawRoundRect(CARD, cornerRadius = CornerRadius(16.dp.toPx()), alpha = a)
                },
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = (CHECK_X - CHECK_SIZE / 2).dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(CHECK_SIZE.dp)
                    .drawBehind {
                        state.tick
                        val d = state.draw[index]
                        val r = size.minDimension / 2
                        val ring = 1.75.dp.toPx()
                        drawCircle(RING, radius = r - ring / 2, style = Stroke(ring))
                        val discA = clampF(d * 3f, 0f, 1f)
                        if (discA > 0f) drawCircle(GREEN, radius = r * mixF(0.72f, 1f, d), alpha = discA)
                        if (d > 0.02f) {
                            partial.reset()
                            measure.getSegment(0f, CHECK_LEN * d, partial, true)
                            val k = size.width / 24f
                            scale(k, k, pivot = Offset.Zero) {
                                drawPath(
                                    partial,
                                    TICK,
                                    style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    },
            )
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
                Text(
                    text = item.title,
                    style = TITLE_STYLE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .drawWithContent {
                            drawContent()
                            state.tick
                            val d = state.draw[index]
                            if (d > 0f) {
                                val h = 1.5.dp.toPx()
                                drawRoundRect(
                                    STRIKE,
                                    topLeft = Offset(0f, size.height / 2 - h / 2),
                                    size = Size(size.width * d, h),
                                    cornerRadius = CornerRadius(1.dp.toPx()),
                                )
                            }
                        }
                        .graphicsLayer {
                            state.tick
                            alpha = 1f - 0.48f * state.draw[index]
                        },
                )
                Text(
                    text = item.note,
                    style = NOTE_STYLE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(
                Modifier.size(28.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(Modifier.width(16.dp).height(1.75.dp).background(GRIP, RoundedCornerShape(1.dp)))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = (CHECK_X + CHECK_SIZE / 2 + 14f).dp)
                .fillMaxWidth()
                .height((1f / density).dp)
                .graphicsLayer {
                    state.tick
                    alpha = 1f - clampF(state.lift[index] * 2f, 0f, 1f)
                }
                .background(HAIR),
        )
    }
}
