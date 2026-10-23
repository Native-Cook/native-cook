package com.ngocdevv.nativecook.blocks.cards.swipedowndeck

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private val Background = Color(0xFF121212)
private val ChipFill = Color(0xFF222222)
private val ChipInk = Color(0xFF8A8A8A)
private val ChipOn = Color(0xFFEDEDED)
private val ChipOnInk = Color(0xFF111111)
private val CountFill = Color(0xFF2A2A2A)
private val TrayFill = Color(0xFF1B1B1B)
private val PocketFill = Color(0xFF232323)
private val HintInk = Color(0xFFE5E5E5)

// Layout, in points.
private const val MARGIN = 24f
// How much of the cards on either side shows at the screen's edges.
private const val PEEK = 6f
private const val HEADER = 44f
private const val CHIPS_GAP = 4f
private const val CHIP_H = 32f
private const val CARD_GAP = 16f
private const val CARD_RATIO = 1.4f
private const val TRAY_H = 92f
// The least room between the card and the tray, for the drag down.
private const val TRAY_GAP = 28f
// Where the deck's front card shows its top edge, below the tray's top, and how the cards behind it
// step up and shrink; three of them show.
private const val DECK_TOP = 18f
private const val DECK_STEP = 7f
private const val DECK_SCALE = 0.9f
private const val DECK_SHRINK = 0.04f
private const val DECK_SHOWN = 3f
private const val PILL_Y = 52f
// The tray rises this much and glows while a card comes down to it.
private const val LIFT = 8f
private const val GLOW_H = 160f

// The drag.
private const val STASH_AT = 130f
private const val STASH_MIN = 50f
private const val FLING = 900f
private const val BROWSE_AT = 70f
private const val RUBBER = 0.3f
// A card dragged down leans after the finger by this share of the sideways movement, shrinks and tilts.
private const val LEAN = 0.35f
private const val DRAG_SHRINK = 0.1f
private const val TILT = 3f

// Motion, in seconds; the spring is mass 1, stiffness 195, damping 23.74 (a ratio of 0.85).
private val Spring = spring<Float>(dampingRatio = 0.85f, stiffness = 195f)
private const val FLY = 0.42f
private val FlyEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val PULSE_OUT = 0.7f
private const val PRESENCE = 0.22f
private const val TINT_FADE = 0.4f
private const val CLEAR_FOR = 0.5f
private const val CLEAR_STAGGER = 0.06f
private const val CLEAR_TOTAL = CLEAR_FOR + CLEAR_STAGGER * 3
private const val VEIL_OUT = 0.3f
private const val VEIL_IN = 0.45f
private const val VEIL_DROP = 14f
private const val VEIL_SHRINK = 0.05f
private val QuadIn = Easing { it * it }
private val QuadOut = Easing { 1f - (1f - it) * (1f - it) }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private val QuadInOut = Easing { if (it < 0.5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val TintEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// The autoPlay loop: two cards stashed, a browse past the third, two more stashed, then the deck
// empties and the first card comes back. Each drag takes DRAG_FOR, each browse BROWSE_FOR.
private const val LOOP = 10f
private enum class Step { Stash, Next }
private val Script = listOf(0.9f to Step.Stash, 2.5f to Step.Stash, 4.1f to Step.Next, 5.3f to Step.Stash, 6.9f to Step.Stash)
private const val CLEAR_AT = 8.55f
private const val DRAG_FOR = 0.55f
private const val DRAG_X = 14f
private const val DRAG_Y = 170f
private const val BROWSE_FOR = 0.34f
private const val BROWSE_X = 120f
private val DragEase = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private const val USER_HOLD = 3f

private val HeaderStyle = deckText(20f, 25f, FontWeight.Medium, Color.White)
private val CountStyle = deckText(12f, 15f, FontWeight.SemiBold, Color(0xFFDADADA))
private val ChipStyle = deckText(13f, 16f, FontWeight.SemiBold, ChipInk)
private val HintStyle = deckText(12f, 15f, FontWeight.Normal, HintInk)
private val PillStyle = deckText(12f, 15f, FontWeight.SemiBold, Color.White)

private fun clamp01(value: Float) = value.coerceIn(0f, 1f)
private fun progress(dy: Float) = clamp01(dy / STASH_AT)
private fun tilt(dx: Float, progress: Float) = TILT * progress + (dx / 120f).coerceIn(-1f, 1f) * TILT
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private class Layout(val width: Float, val cardW: Float, val cardH: Float, val pitch: Float, val cardTop: Float, val trayTop: Float)

private class Pose(val x: Float, val y: Float, val scale: Float, val rotation: Float, val alpha: Float)

/**
 * Everything the screen moves, with the steps that move it. Kept in one object so the gesture, the
 * timers and the loop always read the current values, never those of the composition they began in.
 */
private class DeckState(count: Int, private val scope: CoroutineScope) {
    var cards: List<SwipeDownDeckCard> = emptyList()
    var chips: List<String> = emptyList()
    var reduceMotion = false
    var onAdd: ((SwipeDownDeckCard) -> Unit)? = null
    var onSelect: ((SwipeDownDeckCard) -> Unit)? = null
    var haptic: (Int) -> Unit = {}
    var pitch = 1f

    var chip by mutableIntStateOf(0)
    // Cards in the deck, in the order they went in.
    var stashed by mutableStateOf(listOf<Int>())
    var focus by mutableIntStateOf(0)
    // Cards that have landed in the deck, for its count.
    var landed by mutableIntStateOf(0)
    var epoch by mutableIntStateOf(0)
    var touches by mutableIntStateOf(0)
    // The card whose colour the tray glows in.
    var glowId by mutableIntStateOf(0)

    // The carousel's position, in cards, and each card's place in it, depth in the deck and presence.
    val scroll = Animatable(0f)
    private var scrollTarget = 0
    val slots = List(count) { Animatable(it.toFloat()) }
    val depths = List(count) { Animatable(0f) }
    val presences = List(count) { Animatable(1f) }
    private val wasShown = BooleanArray(count) { true }
    // The drags: sideways for the whole carousel, and the front card pulled down.
    val bx = Animatable(0f)
    val dx = Animatable(0f)
    val dy = Animatable(0f)
    // 0 no drag, 1 browsing sideways, 2 pulling a card down.
    var mode = 0
    var dragging = false
    // The card in flight to the deck, its progress, and where it left from (x, y, pull, tilt).
    var flyId by mutableIntStateOf(-1)
    val fly = Animatable(1f)
    val pulse = Animatable(0f)
    var flyFrom = floatArrayOf(0f, 0f, 0f, 0f)
    // Seconds into the deck's slide out, and the carousel's fade while the loop starts over.
    val clear = Animatable(0f)
    val veil = Animatable(0f)
    private var clearing = false
    private var restoreJob: Job? = null
    private var scripted = false

    fun visible(chip: Int = this.chip): List<Int> =
        cards.indices.filter { it !in stashed && (chip == 0 || chips[chip] in cards[it].filters) }

    fun glow() = max(progress(dy.value), pulse.value)

    private fun ms(seconds: Float) = if (reduceMotion) 0 else (seconds * 1000).toInt()

    private fun springTo(value: Animatable<Float, AnimationVector1D>, target: Float, velocity: Float = value.velocity) {
        scope.launch { if (reduceMotion) value.snapTo(target) else value.animateTo(target, Spring, velocity) }
    }

    private fun tweenTo(value: Animatable<Float, AnimationVector1D>, target: Float, seconds: Float, easing: Easing) {
        scope.launch { value.animateTo(target, if (reduceMotion) snap() else tween(ms(seconds), easing = easing)) }
    }

    private fun snapTo(value: Animatable<Float, AnimationVector1D>, target: Float) {
        scope.launch { value.snapTo(target) }
    }

    fun pose(i: Int, layout: Layout): Pose {
        val order = stashed.indexOf(i)
        val flying = flyId == i
        if (order >= 0 || flying) {
            val d = depths[i].value
            val lift = LIFT * glow()
            val scale = DECK_SCALE - d * DECK_SHRINK
            val top = layout.trayTop + DECK_TOP - d * DECK_STEP - lift
            val deckY = top + layout.cardH * scale / 2 - (layout.cardTop + layout.cardH / 2)
            val out = clamp01((clear.value - d * CLEAR_STAGGER) / CLEAR_FOR)
            val deckX = -(layout.width + 40f) * out * out * out
            val t = if (flying) fly.value else 1f
            return Pose(
                mix(flyFrom[0], deckX, t), mix(flyFrom[1], deckY, t),
                mix(1f - DRAG_SHRINK * flyFrom[2], scale, t), mix(flyFrom[3], 0f, t),
                clamp01(DECK_SHOWN - d),
            )
        }
        val v = veil.value
        var x = (slots[i].value - scroll.value) * layout.pitch + bx.value
        var y = VEIL_DROP * v
        var s = 1f - VEIL_SHRINK * v
        var r = 0f
        if (focus == i) {
            val p = progress(dy.value)
            x += dx.value * LEAN
            y += dy.value
            s *= 1f - DRAG_SHRINK * p
            r = tilt(dx.value, p)
        }
        return Pose(x, y, s, r, presences[i].value * (1f - v))
    }

    /** Brings every card's place, depth and presence in step with the state, with springs unless [snap]. */
    fun sync(snap: Boolean = false) {
        val list = visible()
        for (i in cards.indices) {
            val place = list.indexOf(i)
            val shown = place >= 0
            if (shown) {
                if (snap || !wasShown[i]) snapTo(slots[i], place.toFloat())
                else if (slots[i].targetValue != place.toFloat()) springTo(slots[i], place.toFloat())
            }
            val presence = if (shown) 1f else 0f
            if (snap) snapTo(presences[i], presence)
            else if (presences[i].targetValue != presence) tweenTo(presences[i], presence, PRESENCE, QuadInOut)
            val order = stashed.indexOf(i)
            if (order >= 0 && !snap) {
                val depth = (stashed.size - 1 - order).toFloat()
                if (depths[i].targetValue != depth) springTo(depths[i], depth)
            } else {
                snapTo(depths[i], 0f)
            }
            wasShown[i] = shown
        }
        val target = max(0, list.indexOf(focus))
        if (scrollTarget != target) {
            scrollTarget = target
            springTo(scroll, target.toFloat())
        }
    }

    fun dragStart(vertical: Boolean) {
        dragging = true
        if (focus !in visible()) {
            mode = 0
            return
        }
        mode = if (vertical) 2 else 1
        if (vertical) glowId = focus
    }

    fun dragTo(tx: Float, ty: Float) {
        when (mode) {
            2 -> {
                snapTo(dx, tx)
                snapTo(dy, if (ty > 0f) ty else ty * RUBBER)
            }
            1 -> {
                val last = visible().size - 1
                snapTo(bx, if ((scrollTarget <= 0 && tx > 0f) || (scrollTarget >= last && tx < 0f)) tx * RUBBER else tx)
            }
        }
    }

    /**
     * Ends a drag: a card pulled far enough drops into the deck, a sideways drag pages. [force] makes
     * the outcome certain whatever the distance: the add button, and the loop.
     */
    fun release(vx: Float, vy: Float, auto: Boolean, force: Boolean) {
        dragging = false
        // After the drag's own updates, which are queued on the same scope.
        scope.launch {
            val kind = mode
            mode = 0
            val ids = visible()
            if (kind == 2 || (force && kind == 0)) {
                val id = focus
                val y = dy.value
                val k = ids.indexOf(id)
                if (k >= 0 && fly.value >= 1f && (force || y > STASH_AT || (vy > FLING && y > STASH_MIN))) {
                    val x = dx.value
                    val p = progress(y)
                    flyFrom = floatArrayOf(x * LEAN, y, p, tilt(x, p))
                    dx.snapTo(0f)
                    dy.snapTo(0f)
                    fly.snapTo(0f)
                    val next = if (k + 1 < ids.size) ids[k + 1] else if (k > 0) ids[k - 1] else -1
                    flyId = id
                    stashed = stashed + id
                    focus = next
                    sync()
                    val round = epoch
                    scope.launch {
                        fly.animateTo(1f, if (reduceMotion) snap() else tween(ms(FLY), easing = FlyEase))
                        if (round == epoch && flyId == id) {
                            landed++
                            if (!auto) haptic(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    if (!reduceMotion) {
                        scope.launch {
                            pulse.snapTo(0f)
                            pulse.animateTo(1f, tween(ms(FLY), easing = QuadIn))
                            pulse.animateTo(0f, tween(ms(PULSE_OUT), easing = QuadOut))
                        }
                    }
                    if (!auto) haptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    onAdd?.invoke(cards[id])
                    if (next >= 0) onSelect?.invoke(cards[next])
                    return@launch
                }
                springTo(dx, 0f)
                springTo(dy, 0f)
                return@launch
            }
            if (kind == 1) {
                val k = scrollTarget
                val offset = bx.value
                var target = k
                if (force || offset < -BROWSE_AT || vx < -FLING) target = k + 1
                else if (offset > BROWSE_AT || vx > FLING) target = k - 1
                target = target.coerceIn(0, max(0, ids.size - 1))
                // The drag becomes part of the position, so the spring starts where the finger let go.
                scroll.snapTo(scroll.value - offset / pitch)
                bx.snapTo(0f)
                scrollTarget = target
                springTo(scroll, target.toFloat(), -vx / pitch)
                if (target != k && target < ids.size) {
                    focus = ids[target]
                    if (!auto) haptic(HapticFeedbackConstants.CLOCK_TICK)
                    onSelect?.invoke(cards[ids[target]])
                }
            }
        }
    }

    fun add(id: Int) {
        if (focus != id) return
        glowId = id
        release(0f, 0f, auto = false, force = true)
    }

    fun selectChip(index: Int) {
        if (index == chip) return
        haptic(HapticFeedbackConstants.CLOCK_TICK)
        val next = visible(index)
        chip = index
        if (focus !in next) focus = next.firstOrNull() ?: -1
        sync()
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
        if (scripted) {
            scripted = false
            mode = 0
            springTo(dx, 0f)
            springTo(dy, 0f)
            springTo(bx, 0f)
        }
        if (clearing) restore()
    }

    /** Slides the deck out to the left and fades the carousel, then puts every card back. */
    fun clearDeck() {
        clearing = true
        scope.launch {
            clear.snapTo(0f)
            clear.animateTo(CLEAR_TOTAL, if (reduceMotion) snap() else tween(ms(CLEAR_TOTAL), easing = LinearEasing))
        }
        tweenTo(veil, 1f, VEIL_OUT, QuadIn)
        restoreJob?.cancel()
        restoreJob = scope.launch {
            delay((CLEAR_TOTAL * 1000).toLong())
            restore()
        }
    }

    fun restore() {
        restoreJob?.cancel()
        clearing = false
        flyId = -1
        snapTo(fly, 1f)
        snapTo(pulse, 0f)
        snapTo(scroll, 0f)
        scrollTarget = 0
        snapTo(bx, 0f)
        snapTo(dx, 0f)
        snapTo(dy, 0f)
        mode = 0
        stashed = emptyList()
        focus = if (cards.isEmpty()) -1 else 0
        landed = 0
        chip = 0
        epoch++
        snapTo(clear, 0f)
        scope.launch { veil.animateTo(0f, if (reduceMotion) snap() else tween(ms(VEIL_IN), easing = CubicOut)) }
        sync(snap = true)
    }

    /** The loop's hand: starts the same drags a finger makes. */
    fun play(step: Step) {
        if (focus !in visible()) return
        scripted = true
        when (step) {
            Step.Stash -> {
                glowId = focus
                mode = 2
                tweenTo(dx, DRAG_X, DRAG_FOR, DragEase)
                tweenTo(dy, DRAG_Y, DRAG_FOR, DragEase)
            }
            Step.Next -> {
                mode = 1
                tweenTo(bx, -BROWSE_X, BROWSE_FOR, DragEase)
            }
        }
    }

    fun letGo() {
        if (!scripted) return
        scripted = false
        release(0f, 0f, auto = true, force = true)
    }

    val atRest get() = stashed.isEmpty() && focus == 0 && chip == 0
}

/**
 * A card browser with a deck at the bottom: swipe sideways to browse, swipe a card down to put it in
 * the deck. The card shrinks and tilts as it is pulled, the tray lifts and glows in its colour, and on
 * release the card drops into the tray as the front of a stack while the next one springs in.
 */
@Composable
fun SwipeDownDeck(
    cards: List<SwipeDownDeckCard> = SwipeDownDeckSamples,
    title: String = "Available Cards",
    /** The filter chips; the first one shows every card, the others match a card's `filters`. */
    chips: List<String> = listOf("All", "New", "Protocols", "Supplements", "Diets"),
    hint: String = "Swipe the card down to add to your deck",
    deckLabel: String = "My Deck",
    /** Stashes and browses cards by itself every LOOP seconds, then empties the deck again. */
    autoPlay: Boolean = false,
    /** Called when a card is put in the deck. */
    onAdd: ((SwipeDownDeckCard) -> Unit)? = null,
    /** Called when another card comes to the front. */
    onSelect: ((SwipeDownDeckCard) -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val state = remember(cards.size) { DeckState(cards.size, scope) }
    SideEffect {
        state.cards = cards
        state.chips = chips
        state.reduceMotion = reduceMotion
        state.onAdd = onAdd
        state.onSelect = onSelect
        state.haptic = { view.performHapticFeedback(it) }
    }
    // The first composition needs them before the side effect runs.
    if (state.cards.isEmpty()) {
        state.cards = cards
        state.chips = chips
    }

    LaunchedEffect(autoPlay, state.touches) {
        if (!autoPlay) return@LaunchedEffect
        var origin = System.nanoTime()
        if (state.touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (state.dragging) delay(500)
            origin = System.nanoTime()
            if (!state.atRest) {
                // Back to rest the way the loop ends, then the loop starts over from there.
                state.clearDeck()
                origin += ((LOOP - CLEAR_AT) * 1e9f).toLong()
            }
        }
        val events = buildList {
            for ((time, step) in Script) {
                add(time to { state.play(step) })
                add(time + (if (step == Step.Stash) DRAG_FOR else BROWSE_FOR) to { state.letGo() })
            }
            add(CLEAR_AT to { state.clearDeck() })
        }.sortedBy { it.first }
        while (true) {
            for ((time, run) in events) {
                delay(max(0L, (origin + (time * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                run()
            }
            origin += (LOOP * 1e9f).toLong()
            delay(max(0L, (origin - System.nanoTime()) / 1_000_000))
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Background)
            // Any touch, on a card or a chip, takes over from the autoPlay loop.
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    state.touched()
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val width = maxWidth.value
        val cardW = width - MARGIN * 2
        val cardTop = top + HEADER + CHIPS_GAP + CHIP_H + CARD_GAP
        val trayTop = maxHeight.value - max(insets.calculateBottomPadding().value, 16f) - TRAY_H
        val layout = Layout(width, cardW, min(cardW * CARD_RATIO, trayTop - TRAY_GAP - cardTop), cardW + MARGIN - PEEK, cardTop, trayTop)
        state.pitch = layout.pitch
        val tint = cards.getOrNull(state.glowId)?.color ?: Color.White

        for (i in cards.indices) {
            val on = state.focus == i && i !in state.stashed
            val alpha by animateFloatAsState(if (on) 1f else 0f, tween((TINT_FADE * 1000).toInt(), easing = TintEase), label = "tint")
            val color = cards[i].color
            Box(
                Modifier
                    .offset((width / 2 - cardW * 0.8f).dp, (cardTop + layout.cardH * 0.42f - layout.cardH * 0.62f).dp)
                    .size((cardW * 1.6f).dp, (layout.cardH * 1.24f).dp)
                    .graphicsLayer { this.alpha = alpha }
                    .drawBehind {
                        // A circle's gradient squeezed into the ellipse.
                        scale(1f, size.height / size.width, pivot = center) {
                            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0f)), center, size.width / 2), size.width / 2, center)
                        }
                    },
            )
        }

        Row(
            Modifier.offset(y = top.dp).fillMaxWidth().height(HEADER.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(title, style = HeaderStyle, maxLines = 1)
            Box(
                Modifier.padding(start = 8.dp).height(22.dp).widthIn(min = 30.dp).clip(CircleShape).background(CountFill).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("${cards.size - state.stashed.size}", style = CountStyle)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onMenu?.invoke() }
                    .semantics { contentDescription = "Menu" },
                contentAlignment = Alignment.Center,
            ) {
                SwipeDownDeckIcon(SwipeDownDeckIcons.Menu, Color.White.copy(alpha = 0.8f), 18.dp, 1.7f)
            }
        }

        Row(
            Modifier
                .offset(y = (top + HEADER + CHIPS_GAP).dp)
                .fillMaxWidth()
                .height(CHIP_H.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(CHIP_H.dp).clip(CircleShape).background(ChipFill), contentAlignment = Alignment.Center) {
                SwipeDownDeckIcon(SwipeDownDeckIcons.Search, ChipInk, 15.dp, 1.7f)
            }
            chips.forEachIndexed { i, label ->
                val on = state.chip == i
                val fill by animateColorAsState(if (on) ChipOn else ChipFill, tween(200, easing = CssEase), label = "chip")
                Box(
                    Modifier
                        .height(CHIP_H.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { state.selectChip(i) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = ChipStyle.copy(color = if (on) ChipOnInk else ChipInk))
                }
            }
        }

        Box(
            Modifier
                .offset(y = (trayTop - GLOW_H).dp)
                .fillMaxWidth()
                .height(GLOW_H.dp)
                .graphicsLayer { alpha = state.glow() }
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0f), tint.copy(alpha = 0.5f)))),
        )

        Box(
            Modifier
                .offset(y = trayTop.dp)
                .fillMaxWidth()
                .height((TRAY_H + 200f).dp)
                .graphicsLayer { translationY = -LIFT * state.glow() * density }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(TrayFill),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(1.5.dp).graphicsLayer { alpha = state.glow() }.clip(CircleShape).background(tint))
            val pocket by animateFloatAsState(if (state.stashed.isEmpty()) 1f else 0f, tween(250, easing = CssEase), label = "pocket")
            Column(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    .fillMaxWidth()
                    .height((TRAY_H - 24f).dp)
                    .graphicsLayer { alpha = pocket }
                    .clip(RoundedCornerShape(22.dp))
                    .background(PocketFill)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                    SwipeDownDeckIcon(SwipeDownDeckIcons.Tray, HintInk, 16.dp, 1.5f)
                }
                BasicText(hint, style = HintStyle, maxLines = 1)
            }
        }

        Box(
            Modifier
                .offset(y = cardTop.dp)
                .fillMaxWidth()
                .height((trayTop - cardTop).dp)
                .pointerInput(state) {
                    val tracker = VelocityTracker()
                    var total = Offset.Zero
                    var started = false
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            total = Offset.Zero
                            started = false
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            state.release(v.x / density, v.y / density, auto = false, force = false)
                        },
                        onDragCancel = { state.release(0f, 0f, auto = false, force = false) },
                    ) { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        total += amount
                        if (!started) {
                            started = true
                            state.dragStart(abs(total.y) > abs(total.x))
                        }
                        state.dragTo(total.x / density, total.y / density)
                    }
                },
        ) {
            for (i in cards.indices) {
                val order = state.stashed.indexOf(i)
                val interactive = order < 0 && i in state.visible()
                Box(
                    Modifier
                        .offset(x = ((width - cardW) / 2).dp)
                        .zIndex(if (order >= 0) 20f + order else if (state.focus == i) 40f else 10f)
                        .graphicsLayer {
                            val pose = state.pose(i, layout)
                            translationX = pose.x * density
                            translationY = pose.y * density
                            scaleX = pose.scale
                            scaleY = pose.scale
                            rotationZ = pose.rotation
                            alpha = pose.alpha
                            shadowElevation = 18.dp.toPx()
                            shape = RoundedCornerShape(28.dp)
                            clip = true
                            spotShadowColor = Color.Black
                            ambientShadowColor = Color.Black
                        },
                ) {
                    SwipeDownDeckCardFace(cards[i], cardW.dp, layout.cardH.dp, enabled = interactive) { state.add(i) }
                }
            }
        }

        // It fades in with the first card and goes at once when the deck is emptied.
        val pillOn by animateFloatAsState(
            if (state.landed > 0) 1f else 0f,
            if (state.landed > 0) tween(200, easing = CssEase) else snap(),
            label = "pill",
        )
        Box(
            Modifier
                .offset(y = (trayTop + DECK_TOP + PILL_Y).dp)
                .fillMaxWidth()
                .graphicsLayer {
                    // It gives way to a card falling into the deck and comes back with the new count.
                    alpha = pillOn * (1f - clamp01(state.clear.value / CLEAR_FOR)) * (1f - state.pulse.value)
                    translationY = -LIFT * state.glow() * density
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(
                Modifier.height(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SwipeDownDeckIcon(SwipeDownDeckIcons.Up, Color.White, 12.dp, 1.6f)
                BasicText("$deckLabel · ${state.landed}", style = PillStyle)
            }
        }
    }
}
