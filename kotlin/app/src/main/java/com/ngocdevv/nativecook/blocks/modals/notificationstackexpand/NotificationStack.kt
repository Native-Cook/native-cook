package com.ngocdevv.nativecook.blocks.modals.notificationstackexpand

// The reusable piece: a group of notifications that sits as a collapsed stack and fans out into a list.
// Tap the stack to open it, pull it down to peek, swipe a card left to clear it, Show less to restack.
// All motion lives in StackMotion, so a script (autoPlay) or a finger can drive the same cards.

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class NseCardMotion internal constructor(index: Int) {
    /** Fan-out: 0 stacked, 1 in the list (springs overshoot a little past both). */
    internal val e = NseValue(0f)
    /** Place in the group: 0 front / top. Fractional while the cards close a gap. */
    internal val slot = NseValue(index.toFloat())
    /** Swipe offset, dp. */
    internal val x = NseValue(0f, eps = 0.05f)
    internal val alpha = NseValue(1f)
    /** Drops onto the stack from this many dp above (a returning card). */
    internal val drop = NseValue(0f, eps = 0.05f)
    internal val values = listOf(e, slot, x, alpha, drop)
}

/** Motion for a group of up to MAX_CARDS (4) cards, resting stacked. */
class StackMotion(count: Int) {
    private val n = min(count, MAX_CARDS)
    internal val cards = List(MAX_CARDS) { NseCardMotion(it) }
    /** Drag-down peek, added to every card's fan-out. */
    internal val peek = NseValue(0f)
    /** Cards still in the group, fractional while one leaves (drives the height below). */
    internal val count = NseValue(n.toFloat())
    /** Whole cards in the group, for the header's "4 new". The only value read in composition. */
    internal var countN by mutableIntStateOf(n)
    /** 1 once a finger opened the list (manual), 0 stacked. */
    internal var open = 0
    /** 1 per cleared card. */
    internal var gone = IntArray(MAX_CARDS) { if (it < n) 0 else 1 }
    /** The card resting open on its Clear button, or -1. */
    internal var swiped = -1
    /** 1 while a script poses the cards; the first touch hands them to the finger. */
    internal var driven = 0
    /** Fingers on the stack right now, and a counter of touches (the driver's idle timer). */
    internal var held = 0
    internal var touches = 0
    /** Reduce Motion: short timings instead of springs. */
    internal var calm = 0
    internal var haptic: () -> Unit = {}

    internal val values: List<NseValue> = cards.flatMap { it.values } + listOf(peek, this.count)

    /** Wakes an idle frame loop whenever an animation starts. */
    internal fun onAnimationStart(wake: () -> Unit) {
        values.forEach { it.onStart = wake }
    }

    /** Steps every running animation; true while any still runs. */
    internal fun step(dt: Float): Boolean {
        var any = false
        for (v in values) {
            v.step(dt)
            if (v.active) any = true
        }
        return any
    }
}

/** A spring, or under Reduce Motion a 200 ms timing. */
internal fun move(value: NseValue, to: Float, s: NseSpring, calm: Int, velocity: Float) {
    if (calm == 1) value.timing(to, 0.2f) else value.spring(to, s, velocity)
}

/** Re-ranks the cards still in the group and springs each to its place. */
private fun settleSlots(m: StackMotion) {
    var rank = 0
    for (i in m.cards.indices) {
        if (m.gone[i] == 1) continue
        move(m.cards[i].slot, rank.toFloat(), GAP_SPRING, m.calm, 0f)
        rank += 1
    }
    move(m.count, rank.toFloat(), GAP_SPRING, m.calm, 0f)
    m.countN = rank
}

/** Fans the cards out (top first) or restacks them (bottom first), on staggered springs. */
fun setOpen(m: StackMotion, open: Int, velocity: Float) {
    m.open = open
    val n = m.cards.size
    val calm = m.calm
    for (i in 0 until n) {
        val c = m.cards[i]
        if (m.swiped == i || c.x.get() > 0f) move(c.x, 0f, SWIPE_SPRING, calm, 0f)
        val order = if (open == 1) i else n - 1 - i
        val s = if (open == 1) EXPAND_SPRING else COLLAPSE_SPRING
        if (calm == 1) c.e.timing(open.toFloat(), 0.2f)
        else c.e.spring(open.toFloat(), s, velocity, order * STAGGER)
    }
    m.swiped = -1
}

/** Throws card i off to the left and closes the gap. */
fun clearCard(m: StackMotion, i: Int, velocity: Float) {
    val c = m.cards[i]
    val off = -560f
    if (m.calm == 1) {
        c.alpha.timing(0f, 0.2f)
        // Out of the way of touches once it has faded.
        c.x.timing(off, 0f, delaySeconds = 0.22f)
    } else {
        val speed = max(abs(velocity), 1400f)
        c.x.timing(off, min(0.26f, max(0.14f, (c.x.get() - off) / speed)))
        c.alpha.timing(0f, 0.14f, delaySeconds = 0.14f)
    }
    val next = m.gone.copyOf()
    next[i] = 1
    m.gone = next
    if (m.swiped == i) m.swiped = -1
    settleSlots(m)
    if (next.none { it == 0 }) m.open = 0
    m.haptic()
}

/** The header's clear mark: every card leaves, top first. */
fun clearAll(m: StackMotion) {
    val next = m.gone.copyOf()
    var order = 0
    for (i in m.cards.indices) {
        if (next[i] == 1) continue
        val c = m.cards[i]
        val d = order * STAGGER
        c.alpha.timing(0f, 0.18f, delaySeconds = d)
        c.drop.timing(-12f, 0.18f, delaySeconds = d)
        c.x.timing(-560f, 0f, delaySeconds = d + 0.2f)
        next[i] = 1
        order += 1
    }
    m.gone = next
    m.open = 0
    m.swiped = -1
    move(m.count, 0f, GAP_SPRING, m.calm, 0f)
    m.countN = 0
    m.haptic()
}

/** A script let go: whatever pose it left becomes the finger's starting point. */
fun adoptPose(m: StackMotion) {
    if (m.driven == 0) return
    m.driven = 0
    val open = if (m.cards[0].e.get() > 0.5f) 1 else 0
    val prev = m.gone
    val gone = prev.copyOf()
    for (i in m.cards.indices) {
        val c = m.cards[i]
        if (prev[i] == 0 && (c.alpha.get() < 0.5f || c.x.get() < -260f)) gone[i] = 1
    }
    m.gone = gone
    m.open = open
    m.swiped = -1
    val calm = m.calm
    move(m.peek, 0f, PEEK_SPRING, calm, 0f)
    for (i in m.cards.indices) {
        val c = m.cards[i]
        move(c.e, open.toFloat(), EXPAND_SPRING, calm, 0f)
        move(c.drop, 0f, SWIPE_SPRING, calm, 0f)
        if (gone[i] == 1) {
            c.alpha.timing(0f, 0.12f)
        } else {
            move(c.x, 0f, SWIPE_SPRING, calm, 0f)
            c.alpha.timing(1f, 0.12f)
        }
    }
    settleSlots(m)
}

/** Back to the resting stack with every card in it: cleared cards drop back on in order. */
fun restoreStack(m: StackMotion, total: Int) {
    val gone = m.gone
    val calm = m.calm
    setOpen(m, 0, 0f)
    move(m.peek, 0f, PEEK_SPRING, calm, 0f)
    val next = gone.copyOf()
    for (i in m.cards.indices) {
        val c = m.cards[i]
        if (i >= total) continue
        if (gone[i] == 1) {
            c.x.set(0f)
            c.drop.set(ARRIVE_DROP)
            move(c.drop, 0f, SWIPE_SPRING, calm, 0f)
            c.alpha.timing(1f, 0.26f, delaySeconds = 0.12f)
            next[i] = 0
        } else {
            move(c.x, 0f, SWIPE_SPRING, calm, 0f)
        }
    }
    m.gone = next
    settleSlots(m)
}

/** True once the stack rests closed, whole and still. */
internal fun stackAtRest(m: StackMotion, total: Int): Boolean {
    if (abs(m.peek.get()) > 0.004f) return false
    for (i in 0 until min(total, m.cards.size)) {
        val c = m.cards[i]
        if (abs(c.e.get()) > 0.004f || abs(c.slot.get() - i) > 0.01f || c.alpha.get() < 0.995f) return false
        if (abs(c.x.get()) > 0.5f || abs(c.drop.get()) > 0.5f) return false
    }
    return true
}

/** How far the content under the group moves while it opens, closes or loses a card. */
internal fun belowShift(m: StackMotion, total: Int): Float {
    val last = m.cards[max(0, min(total, m.cards.size) - 1)]
    val e = last.e.get() + m.peek.get()
    return groupHeight(e, m.count.get()) - groupHeight(0f, min(total, m.cards.size).toFloat())
}

private fun grab(m: StackMotion) {
    m.touches += 1
    m.held = 1
    adoptPose(m)
}

private fun letGo(m: StackMotion) {
    m.held = 0
}

/** One card's pose, shared by drawing and hit testing. */
private class Pose(val y: Float, val k: Float, val opacity: Float, val drop: Float)

private fun poseOf(m: StackMotion, c: NseCardMotion): Pose {
    val s = c.slot.get()
    val e = c.e.get() + m.peek.get()
    val eu = clamp01(e)
    val depth = s.coerceIn(0f, MAX_DEPTH)
    val y = mix(e, collapsedY(s), expandedY(s))
    val k = mix(eu, 1f - DEPTH_SCALE * depth, 1f)
    val stacked = if (s <= MAX_DEPTH) 1f else clamp01(MAX_DEPTH + 1f - s)
    val drop = c.drop.get()
    return Pose(y - drop, k * (1f + ARRIVE_SCALE * drop / ARRIVE_DROP), c.alpha.get() * mix(eu, stacked, 1f), drop)
}

private fun clearWidth(c: NseCardMotion, width: Float): Float =
    if (c.alpha.get() < 0.05f) 0f else (-c.x.get() - 8f).coerceIn(0f, width)

private fun headerShown(m: StackMotion): Float {
    val e = m.cards[0].e.get()
    return clamp01((e - 0.35f) / 0.5f) * clamp01(m.count.get())
}

private val CLEAR_MARK = svg("M1.5 1.5 L8.5 8.5 M8.5 1.5 L1.5 8.5")
private val SETTLE = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal const val SHOW_LESS_W = 88f
internal const val CLOSE_W = 28f

/** What a finger landed on. */
private sealed interface Hit {
    data class Card(val index: Int) : Hit
    data class Clear(val index: Int) : Hit
    data object Less : Hit
    data object Close : Hit
}

private fun hitTest(m: StackMotion, total: Int, width: Float, px: Float, py: Float): Hit? {
    // Rows stack front first (card 0 on top); inside a row the card covers its Clear button.
    for (i in 0 until total) {
        val c = m.cards[i]
        val p = poseOf(m, c)
        if (p.opacity < 0.05f) continue
        val h = CARD_H * p.k
        val top = p.y + (CARD_H - h) / 2f
        if (py < top || py > top + h) continue
        val left = width * (1f - p.k) / 2f + c.x.get() * p.k
        if (px >= left && px <= left + width * p.k) return Hit.Card(i)
        val w = clearWidth(c, width)
        if (w > 0f && px >= width - w && px <= width) return Hit.Clear(i)
    }
    if (headerShown(m) > 0.02f) {
        val rowH = HEADER_H - 8f
        val closeL = width - CLOSE_W
        if (px >= closeL - 8f && py >= (rowH - CLOSE_W) / 2f - 8f && py <= (rowH + CLOSE_W) / 2f + 8f) return Hit.Close
        val lessR = closeL - 8f
        if (px >= lessR - SHOW_LESS_W - 6f && px <= lessR + 6f && py >= (rowH - 30f) / 2f - 6f && py <= (rowH + 30f) / 2f + 6f) return Hit.Less
    }
    return null
}

/** A notification group: a stack that fans out into a list with a header (count, Show less, clear). */
@Composable
fun NotificationStack(
    group: NoticeGroup,
    motion: StackMotion,
    width: Float,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val m = motion
    val notices = group.notices.take(MAX_CARDS)
    val total = notices.size
    val haptics = LocalHapticFeedback.current
    SideEffect {
        m.calm = if (reducedMotion) 1 else 0
        m.haptic = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
    }
    val widthNow by rememberUpdatedState(width)
    val totalNow by rememberUpdatedState(total)

    Box(
        modifier
            .width(width.dp)
            .height(groupHeight(1f, total.toFloat()).dp)
            .pointerInput(m) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = density
                    val w = widthNow
                    val hit = hitTest(m, totalNow, w, down.position.x / d, down.position.y / d) ?: return@awaitEachGesture
                    when (hit) {
                        is Hit.Card -> {
                            val i = hit.index
                            val c = m.cards[i]
                            grab(m)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var phase = 0 // 0 undecided, 1 swipe, 2 pull
                            var travelled = 0f
                            var startX = 0f
                            var dx = 0f
                            var dy = 0f
                            var lifted = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    dx = (change.position.x - down.position.x) / d
                                    dy = (change.position.y - down.position.y) / d
                                    travelled = max(travelled, max(abs(dx), abs(dy)))
                                    if (!change.pressed) {
                                        lifted = true
                                        break
                                    }
                                    if (phase == 0) {
                                        if (abs(dx) >= 14f && abs(dx) >= abs(dy)) {
                                            phase = 1
                                            val other = m.swiped
                                            if (other >= 0 && other != i) {
                                                move(m.cards[other].x, 0f, SWIPE_SPRING, m.calm, 0f)
                                                m.swiped = -1
                                            }
                                            startX = c.x.get()
                                        } else if (abs(dy) >= 14f) {
                                            phase = 2
                                        }
                                    }
                                    if (phase == 1) {
                                        change.consume()
                                        if (m.open == 1) c.x.set(rubberRight(startX + dx))
                                    } else if (phase == 2) {
                                        change.consume()
                                        if (m.open == 0) m.peek.set(peekFromDrag(dy))
                                    }
                                }
                                if (lifted) {
                                    val v = tracker.calculateVelocity()
                                    val vx = v.x / d
                                    val vy = v.y / d
                                    when (phase) {
                                        1 -> if (m.open == 1) {
                                            val x = c.x.get()
                                            if (x < -w * FLING_FRACTION || (vx < -FLING_VELOCITY && x < -24f)) {
                                                clearCard(m, i, vx)
                                            } else if (x < -CLEAR_W * OPEN_FRACTION && vx < 400f) {
                                                move(c.x, -CLEAR_W - 8f, SWIPE_SPRING, m.calm, vx)
                                                m.swiped = i
                                            } else {
                                                move(c.x, 0f, SWIPE_SPRING, m.calm, vx)
                                                if (m.swiped == i) m.swiped = -1
                                            }
                                        }
                                        2 -> if (m.open == 0) {
                                            val unit = vy * PEEK_MAX / PEEK_RANGE
                                            if (dy > PEEK_OPEN_DISTANCE || vy > PEEK_OPEN_VELOCITY) {
                                                // The peek becomes the start of the fan-out, carrying the finger's speed.
                                                val p = m.peek.get()
                                                m.peek.set(0f)
                                                for (card in m.cards) card.e.set(card.e.get() + p)
                                                setOpen(m, 1, unit)
                                                m.haptic()
                                            } else {
                                                move(m.peek, 0f, PEEK_SPRING, m.calm, unit)
                                            }
                                        }
                                        else -> if (travelled <= 10f) {
                                            if (m.open == 0) {
                                                setOpen(m, 1, 0f)
                                                m.haptic()
                                            } else if (m.swiped >= 0) {
                                                move(m.cards[m.swiped].x, 0f, SWIPE_SPRING, m.calm, 0f)
                                                m.swiped = -1
                                            }
                                        }
                                    }
                                } else if (phase == 1 && m.open == 1) {
                                    move(c.x, 0f, SWIPE_SPRING, m.calm, 0f)
                                } else if (phase == 2 && m.open == 0) {
                                    move(m.peek, 0f, PEEK_SPRING, m.calm, 0f)
                                }
                            } finally {
                                letGo(m)
                            }
                        }
                        else -> {
                            var travelled = 0f
                            var lifted = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                travelled = max(travelled, (change.position - down.position).getDistance() / d)
                                if (!change.pressed) {
                                    lifted = true
                                    break
                                }
                            }
                            if (!lifted || travelled > 24f) return@awaitEachGesture
                            when (hit) {
                                is Hit.Clear -> clearCard(m, hit.index, 0f)
                                Hit.Less -> {
                                    m.haptic()
                                    setOpen(m, 0, 0f)
                                }
                                Hit.Close -> clearAll(m)
                            }
                        }
                    }
                }
            },
    ) {
        StackHeader(group, m, reducedMotion)
        notices.forEachIndexed { index, notice ->
            StackCard(index, m, width, notice)
        }
    }
}

@Composable
private fun StackHeader(group: NoticeGroup, m: StackMotion, reducedMotion: Boolean) {
    val count = m.countN
    Row(
        Modifier
            .fillMaxWidth()
            .height((HEADER_H - 8f).dp)
            .graphicsLayer {
                val e = m.cards[0].e.get()
                alpha = headerShown(m)
                translationY = (1f - clamp01(e)) * 10f * density
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                group.title,
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = NSE_INK),
            )
            SwapCount(
                if (count == 0) "Cleared" else "$count new",
                reducedMotion,
                Modifier.padding(start = 8.dp).alignByBaseline(),
            )
        }
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(SHOW_LESS_W.dp, 30.dp)
                .background(Color(0x121B1F24), RoundedCornerShape(15.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Show less"
                    onClick { setOpen(m, 0, 0f); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Show less", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NSE_INK))
        }
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(CLOSE_W.dp)
                .background(Color(0x121B1F24), CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Clear ${group.title}"
                    onClick { clearAll(m); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(10.dp)) { inViewBox(10f) { strokePath(CLEAR_MARK, NSE_INK, 1.6f) } }
        }
    }
}

/** RN's SwapText: the old words leave fast (140 ms), the new settle a beat later from 10 dp below (260 ms). */
@Composable
private fun SwapCount(text: String, reduced: Boolean, modifier: Modifier) {
    val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NSE_ACCENT, fontFeatureSettings = "tnum")
    val rise = with(LocalDensity.current) { 10.dp.roundToPx() }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            } else {
                (fadeIn(tween(260, 60, SETTLE)) + slideInVertically(tween(260, 60, SETTLE)) { rise }) togetherWith
                    fadeOut(tween(140, easing = SETTLE))
            } using SizeTransform(clip = false)
        },
        label = "count",
    ) { value ->
        Text(value, style = style, maxLines = 1)
    }
}

@Composable
private fun StackCard(index: Int, m: StackMotion, width: Float, notice: Notice) {
    val c = m.cards[index]
    Box(
        Modifier
            .zIndex((MAX_CARDS - index).toFloat())
            .width(width.dp)
            .height(CARD_H.dp)
            .graphicsLayer {
                val p = poseOf(m, c)
                alpha = p.opacity
                translationY = p.y * density
                scaleX = p.k
                scaleY = p.k
            },
    ) {
        // The Clear button, behind the card at its right edge, as wide as the card has moved.
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics { }
                .drawWithContent {
                    val w = clearWidth(c, width) * density
                    if (w <= 0f) return@drawWithContent
                    val a = clamp01(w / (36f * density))
                    val r = minOf(RADIUS * density, w / 2f, size.height / 2f)
                    val left = size.width - w
                    val shape = Path().apply { addRoundRect(RoundRect(left, 0f, size.width, size.height, CornerRadius(r))) }
                    val inner = Path().apply { addRoundRect(RoundRect(left, density, size.width, size.height + density, CornerRadius(r))) }
                    val band = Path().apply { op(shape, inner, androidx.compose.ui.graphics.PathOperation.Difference) }
                    drawPath(shape, NSE_DANGER, alpha = a)
                    drawPath(band, Color.White, alpha = 0.25f * a)
                    clipPath(shape) { this@drawWithContent.drawContent() }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                "Clear",
                modifier = Modifier
                    .padding(end = 20.dp)
                    .width(44.dp)
                    .graphicsLayer {
                        val w = clearWidth(c, width)
                        alpha = clamp01(w / 36f) * clamp01((-c.x.get() - 8f - 48f) / 20f)
                    },
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFF6F5)),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { translationX = c.x.get() * density }
                .nseCardSurface()
                .semantics {
                    contentDescription = "${notice.from}, ${notice.time}. ${notice.body}"
                    onClick(label = "Show all") { setOpen(m, if (m.open == 1) 0 else 1, 0f); true }
                    customActions = listOf(CustomAccessibilityAction("Clear") { clearCard(m, index, 0f); true })
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val eu = clamp01(c.e.get() + m.peek.get())
                        alpha = mix(eu, clamp01(1f - c.slot.get()), 1f)
                    },
            ) {
                NoticeFace(notice)
            }
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val eu = clamp01(c.e.get() + m.peek.get())
                        val a = (1f - eu) * c.slot.get().coerceIn(0f, MAX_DEPTH) * 0.32f
                        if (a > 0f) drawRoundRect(Color(0xFFC9CFD7), cornerRadius = CornerRadius(RADIUS.dp.toPx()), alpha = a.coerceAtMost(1f))
                    },
            )
        }
    }
}

