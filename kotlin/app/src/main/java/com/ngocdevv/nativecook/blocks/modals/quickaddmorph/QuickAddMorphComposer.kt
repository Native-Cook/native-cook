package com.ngocdevv.nativecook.blocks.modals.quickaddmorph

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// The reusable piece: a round + button that morphs into a composer card, and collapses into a pill that flies to a row.
// It owns no list. The parent gives it the landing row's position (rowY) and receives the draft in onSubmit.

internal val INK = Color(0xFF211E1A)
internal val CARD = Color(0xFFFFFDF9)
internal val PAPER = Color(0xFFF3EFE7)
internal val ACCENT = Color(0xFF1F6F5C)
internal val MUTE = Color(0xFF857D71)
internal val AMBER = Color(0xFFC9772A)
private val FILL = Color(0xFFF1ECE3)
private val CREAM = Color(0xFFF7F3EA)

// Inside the card, in dp.
internal const val CARD_HEIGHT = 170f
internal const val PAD = 16f
internal const val FIELD_TOP = 14f
internal const val FIELD_HEIGHT = 40f
internal const val CHIPS_TOP = 62f
internal const val CHIP_HEIGHT = 34f
internal const val CHIP_GAP = 8f
internal val CHIP_WIDTHS = floatArrayOf(72f, 88f, 92f)
internal const val ADD_TOP = 108f
internal const val ADD_HEIGHT = 48f
private const val ICON = 24f
private const val ICON_INSET = 18f
private const val BAR = 16f
private const val BAR_THICK = 2.4f
private const val LABEL_LINE = 18f
private const val MAX_TITLE = 40

internal class QuickAddRect(val x: Float, val y: Float, val width: Float, val height: Float)

internal class ComposerGeometry(
    val fab: QuickAddRect,
    val card: QuickAddRect,
    val pill: QuickAddRect,
    val rowX: Float,
    val rowWidth: Float,
    val rowHeight: Float,
)

data class QuickAddDraft(val title: String, val today: Boolean, val priority: Boolean, val list: String)

/** The morphing view's frame and look at one moment, in dp. */
internal class QuickAddCardFrame {
    var x = 0f
    var y = 0f
    var width = 1f
    var height = 1f
    var radius = 0f
    var color = INK
    var alpha = 0f
    var scale = 1f
}

/**
 * The composer's animated values (Reanimated shared values in RN, stepped by the screen's frame loop) and its draft
 * (React state in RN, Compose state here).
 */
internal class QuickAddComposerState(val host: QuickAddHost, lists: List<String>) {
    val active = QuickAddValue(host, 0f) // 1 while the morphing view exists
    val openW = QuickAddValue(host, 0f)
    val openH = QuickAddValue(host, 0f)
    val content = QuickAddValue(host, 0f)
    val collapse = QuickAddValue(host, 0f)
    val fly = QuickAddValue(host, 0f)
    val fabIn = QuickAddValue(host, 1f)
    val press = QuickAddValue(host, 0f)
    val addPress = QuickAddValue(host, 0f)
    val drag = QuickAddValue(host, 0f)
    val lift = QuickAddValue(host, 0f)
    val landed = QuickAddValue(host, 0f)
    val todayAmount = QuickAddValue(host, 1f)
    val priorityAmount = QuickAddValue(host, 0f)
    val listRoll = QuickAddValue(host, 0f)

    /** The landing row's top on screen, dp. */
    var rowY = 0f
        set(value) {
            if (field != value) {
                field = value
                host.dirty = true
            }
        }
    var landId = -1
    var scripted = true
    var scriptTitle = ""
    var typed = 0
        private set

    var lists: List<String> = lists

    // The draft.
    var title by mutableStateOf("")
    var isOpen by mutableStateOf(false)
    var today by mutableStateOf(true)
    var priority by mutableStateOf(false)
    var listIndex by mutableIntStateOf(0)
    var labels by mutableStateOf(firstLabels(lists))

    val canAdd get() = title.trim().isNotEmpty()

    private fun firstLabels(lists: List<String>) = lists[0] to lists.getOrElse(1) { lists[0] }

    /** autoPlay types into the value without focusing, so no keyboard shows. */
    fun setTyped(count: Int) {
        if (count == typed) return
        typed = count
        if (scripted) title = scriptTitle.take(count)
    }

    fun resetDraft() {
        title = ""
        isOpen = false
        today = true
        priority = false
        listIndex = 0
        labels = firstLabels(lists)
    }

    fun adopt(nextToday: Boolean, nextPriority: Boolean, nextList: Int) {
        isOpen = true
        today = nextToday
        priority = nextPriority
        listIndex = nextList
    }

    /** Back to the resting button: called when a flight lands or a close finishes. */
    fun finishMorph() {
        active.set(0f)
        openW.set(0f)
        openH.set(0f)
        content.set(0f)
        collapse.set(0f)
        fly.set(0f)
        drag.set(0f)
        fabIn.set(1f)
        landId = -1
        landed.set(0f)
    }

    /** The height's spring back to the button; the morph ends when it settles. */
    fun springClosed(reduced: Boolean = false) {
        if (reduced) {
            openH.timing(0f, REDUCED_SECONDS) { finishMorph() }
        } else {
            openH.spring(0f, WIDTH_SPRING, overshootClamping = true) { finishMorph() }
        }
    }

    fun open(reduced: Boolean) {
        isOpen = true
        active.set(1f)
        fabIn.set(0f)
        drag.set(0f)
        openW.to(1f, WIDTH_SPRING, reduced)
        openH.to(1f, HEIGHT_SPRING, reduced, delay = if (reduced) 0f else HEIGHT_DELAY)
        content.timing(1f, CONTENT_IN, delay = if (reduced) 0f else CONTENT_DELAY)
    }

    fun close(reduced: Boolean) {
        isOpen = false
        content.timing(0f, CONTENT_OUT)
        drag.to(0f, HEIGHT_SPRING, reduced)
        openW.to(0f, HEIGHT_SPRING, reduced)
        springClosed(reduced)
    }

    /** Add: the card collapses into a pill that flies to `rowY`. False when the title is empty. */
    fun submit(reduced: Boolean, onSubmit: (QuickAddDraft) -> Unit): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        onSubmit(QuickAddDraft(clean, today, priority, lists[listIndex.coerceIn(0, lists.size - 1)]))
        isOpen = false
        title = ""
        today = true
        priority = false
        todayAmount.timing(1f, 0.2f)
        priorityAmount.timing(0f, 0.2f)
        content.timing(0f, CONTENT_OUT)
        collapse.to(1f, COLLAPSE_SPRING, reduced)
        val lead = if (reduced) 0f else QuickAddT.FLY - QuickAddT.ADD
        fly.timing(1f, if (reduced) REDUCED_SECONDS else QuickAddT.FLY_SPAN, delay = lead) { finishMorph() }
        landed.timing(1f, LAND_SPAN, delay = lead + LAND_DELAY)
        fabIn.to(1f, WIDTH_SPRING, reduced, delay = lead + FAB_DELAY)
        return true
    }

    fun toggleToday(reduced: Boolean) {
        val next = !today
        today = next
        todayAmount.to(if (next) 1f else 0f, CHIP_SPRING, reduced)
    }

    fun togglePriority(reduced: Boolean) {
        val next = !priority
        priority = next
        priorityAmount.to(if (next) 1f else 0f, CHIP_SPRING, reduced)
    }

    /** The roll has two slots: the next name goes into the hidden one, then the roll moves to it. */
    fun cycleList(reduced: Boolean) {
        val next = (listIndex + 1) % lists.size
        val showingSecond = listRoll.value > 0.5f
        listIndex = next
        val current = labels
        labels = if (showingSecond) lists[next] to current.second else current.first to lists[next]
        listRoll.to(if (showingSecond) 0f else 1f, CHIP_SPRING, reduced)
    }

    private val frame = QuickAddCardFrame()
    private var frameTick = -1L
    private var frameGeometry: ComposerGeometry? = null

    /** The morphing view at this frame (RN `cardStyle`). Read from layout and draw lambdas, cached per tick. */
    fun frame(g: ComposerGeometry): QuickAddCardFrame {
        val tick = host.tick.longValue
        if (tick == frameTick && g === frameGeometry) return frame
        frameTick = tick
        frameGeometry = g
        val w = openW.value
        val h = openH.value
        val co = clamp01(collapse.value)
        val f = clamp01(fly.value)
        val bottom = g.fab.y + g.fab.height

        // The button's rect to the card's: anchored at the bottom right, width and height on their own springs.
        val width0 = mix(w, g.fab.width, g.card.width)
        val height0 = mix(h, g.fab.height, g.card.height)
        val x0 = g.fab.x + g.fab.width - width0
        val y0 = bottom - height0 - lift.value * clamp01(h) + max(drag.value, -40f)
        // The card's rect to the pill's.
        val x1 = mix(co, x0, g.pill.x)
        val y1 = mix(co, y0, g.pill.y)
        val width1 = mix(co, width0, g.pill.width)
        val height1 = mix(co, height0, g.pill.height)
        // The pill's rect to the row's, on a slight bow.
        frame.x = mix(f, x1, g.rowX) - sin(f * PI.toFloat()) * FLY_ARC
        frame.y = mix(f, y1, rowY)
        frame.width = max(1f, mix(f, width1, g.rowWidth))
        frame.height = max(1f, mix(f, height1, g.rowHeight))
        frame.radius = when {
            f > 0f -> mix(f, RADIUS_PILL, RADIUS_ROW)
            co > 0f -> mix(co, RADIUS_CARD, RADIUS_PILL)
            else -> mix(clamp01(h), RADIUS_FAB, RADIUS_CARD)
        }
        val opened = mixColor(clamp01(h) / 0.6f, INK, CARD)
        val pill = mixColor(co, opened, ACCENT)
        frame.color = mixColor(seg(f, 0.62f, 0.3f), pill, CARD)
        frame.alpha = if (active.value == 1f) 1f - seg(f, 0.88f, 0.12f) else 0f
        frame.scale = 1f - 0.08f * press.value * (1f - clamp01(h))
        return frame
    }
}

internal fun quickAddStyle(size: Float, weight: FontWeight, color: Color, line: Float = 0f, tabular: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = if (line > 0f) line.sp else TextUnit.Unspecified,
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val FIELD_STYLE = quickAddStyle(19f, FontWeight.SemiBold, INK)
private val CHIP_STYLE = quickAddStyle(14f, FontWeight.SemiBold, INK)
private val ADD_STYLE = quickAddStyle(17f, FontWeight.SemiBold, CREAM)
private val PILL_STYLE = quickAddStyle(15f, FontWeight.SemiBold, CREAM)

/** CSS `inset 0 1px 0 color`: the box minus itself moved 1 dp down, inside the rounded box. */
internal fun Modifier.quickAddInsetTop(color: Color, radius: Dp) = drawBehind {
    val r = radius.toPx()
    val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val moved = Path().apply { addRoundRect(RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), CornerRadius(r))) }
    clipPath(box) {
        clipPath(moved, ClipOp.Difference) { drawRect(color) }
    }
}

/** The + of the button: two 16 × 2.4 bars, centred. */
private fun DrawScope.drawPlusBars(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val long = BAR.dp.toPx()
    val thick = BAR_THICK.dp.toPx()
    val r = CornerRadius(thick / 2f)
    drawRoundRect(color, Offset(c.x - long / 2f, c.y - thick / 2f), Size(long, thick), r)
    drawRoundRect(color, Offset(c.x - thick / 2f, c.y - long / 2f), Size(thick, long), r)
}

@Composable
internal fun MorphComposer(
    state: QuickAddComposerState,
    geometry: ComposerGeometry,
    pillTitle: String,
    reducedMotion: Boolean,
    onSubmit: (QuickAddDraft) -> Unit,
    onActivity: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    /** Distance from the button's bottom edge to the screen's: the keyboard lifts the card by what exceeds it. */
    keyboardInset: Float,
    placeholder: String = "New task",
) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val g = geometry
    val tick = state.host.tick
    val isOpen = state.isOpen

    fun light() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun dismissKeyboard() {
        keyboard?.hide()
        focus.clearFocus(force = true)
    }

    val open = {
        onActivity()
        light()
        state.open(reducedMotion)
    }
    val close = {
        onActivity()
        dismissKeyboard()
        state.close(reducedMotion)
    }
    val submit = {
        if (state.canAdd) {
            light()
            dismissKeyboard()
            state.submit(reducedMotion, onSubmit)
        }
    }
    val closeNow by rememberUpdatedState(close)
    val reducedNow by rememberUpdatedState(reducedMotion)

    // The keyboard lifts the open card by what exceeds the button's distance from the bottom edge.
    val ime = WindowInsets.ime
    LaunchedEffect(state, keyboardInset, density) {
        snapshotFlow { ime.getBottom(density) / density.density }.collect { bottom ->
            state.lift.set(max(0f, bottom - keyboardInset))
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The button. Under the card: while the card exists it fades out and the card plays the button.
        Box(
            Modifier
                .absoluteOffset(g.fab.x.dp, g.fab.y.dp)
                .size(g.fab.width.dp, g.fab.height.dp)
                .graphicsLayer {
                    tick.longValue
                    val shown = if (state.active.value == 1f) state.fabIn.value else 1f
                    alpha = clamp01(shown)
                    val s = (0.6f + 0.4f * shown) * (1f - 0.08f * state.press.value)
                    scaleX = s
                    scaleY = s
                }
                .cssBoxShadow(rgba(33, 30, 26, 0.28f), 1.dp, 2.dp, RADIUS_FAB.dp)
                .cssBoxShadow(rgba(33, 30, 26, 0.24f), 10.dp, 22.dp, RADIUS_FAB.dp)
                .background(INK, CircleShape)
                .quickAddInsetTop(rgba(255, 255, 255, 0.14f), RADIUS_FAB.dp)
                .drawBehind { drawPlusBars(CREAM) }
                .semantics { contentDescription = "New task" }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { open() },
        )

        // The morphing view: button → card → pill → row.
        val shadowNear = remember { SoftRoundRect(rgba(33, 30, 26, 0.14f), sigma = 1.5f) }
        val shadowFar = remember { SoftRoundRect(rgba(33, 30, 26, 0.20f), sigma = 18f) }
        Box(
            Modifier
                .layout { measurable, _ ->
                    val f = state.frame(g)
                    val w = (f.width * density.density).roundToInt().coerceAtLeast(1)
                    val h = (f.height * density.density).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val f = state.frame(g)
                    translationX = f.x * density.density
                    translationY = f.y * density.density
                    alpha = f.alpha
                    scaleX = f.scale
                    scaleY = f.scale
                }
                .drawBehind {
                    val f = state.frame(g)
                    val r = f.radius.dp.toPx()
                    val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                    clipPath(box, ClipOp.Difference) {
                        scale(density.density, density.density, pivot = Offset.Zero) {
                            val wd = size.width / density.density
                            val hd = size.height / density.density
                            drawIntoCanvas {
                                shadowNear.draw(it.nativeCanvas, 0f, 1f, wd, hd, f.radius)
                                shadowFar.draw(it.nativeCanvas, 0f, 16f, wd, hd, f.radius)
                            }
                        }
                    }
                    drawRoundRect(f.color, cornerRadius = CornerRadius(r))
                }
                .graphicsLayer {
                    val f = state.frame(g)
                    shape = RoundedCornerShape(f.radius.dp)
                    clip = true
                }
                .then(
                    if (!isOpen) {
                        Modifier
                    } else {
                        Modifier.pointerInput(state) {
                            // Drag down to dismiss: active past 8 dp vertically, failed past 18 dp sideways first.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var dragging = false
                                var failed = false
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val dx = (change.position.x - down.position.x) / density.density
                                    val dy = (change.position.y - down.position.y) / density.density
                                    if (!dragging && !failed) {
                                        if (abs(dx) > 18f) failed = true else if (abs(dy) > 8f) dragging = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        state.drag.set(if (dy > 0f) dy else dy * DRAG_UP_RUBBER)
                                    }
                                    if (!change.pressed) {
                                        if (dragging) {
                                            val vy = tracker.calculateVelocity().y / density.density
                                            if (dy > DRAG_CLOSE_DISTANCE || vy > DRAG_CLOSE_VELOCITY) {
                                                closeNow()
                                            } else if (reducedNow) {
                                                state.drag.timing(0f, REDUCED_SECONDS)
                                            } else {
                                                state.drag.spring(0f, HEIGHT_SPRING, velocity = vy)
                                            }
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            // The card's content, anchored to the morphing view's bottom right at the card's full size.
            val cardWidth = g.card.width
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .wrapContentSize(Alignment.BottomEnd, unbounded = true)
                    .size(cardWidth.dp, g.card.height.dp)
                    .graphicsLayer {
                        tick.longValue
                        alpha = if (state.content.value > 0.001f) 1f else 0f
                    },
            ) {
                // The field.
                Box(
                    Modifier
                        .absoluteOffset(PAD.dp, FIELD_TOP.dp)
                        .size((cardWidth - PAD - (PAD + ICON + 6f)).dp, FIELD_HEIGHT.dp)
                        .graphicsLayer { rise(this@graphicsLayer, state, 0) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (isOpen) {
                        DisposableEffect(Unit) { onDispose { onFocusChange(false) } }
                        BasicTextField(
                            value = state.title,
                            onValueChange = {
                                onActivity()
                                state.title = it.take(MAX_TITLE)
                            },
                            singleLine = true,
                            textStyle = FIELD_STYLE,
                            cursorBrush = SolidColor(ACCENT),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { onFocusChange(it.isFocused) }
                                .semantics { contentDescription = "Task title" },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.title.isEmpty()) BasicText(placeholder, style = FIELD_STYLE.copy(color = MUTE), maxLines = 1)
                                    inner()
                                }
                            },
                        )
                    } else {
                        // Not touchable while the card is closed or scripted: the same text, without an input.
                        val text = state.title
                        BasicText(
                            if (text.isEmpty()) placeholder else text,
                            style = if (text.isEmpty()) FIELD_STYLE.copy(color = MUTE) else FIELD_STYLE,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }

                // The chips.
                Row(
                    Modifier
                        .absoluteOffset(PAD.dp, CHIPS_TOP.dp)
                        .height(CHIP_HEIGHT.dp)
                        .graphicsLayer { rise(this@graphicsLayer, state, 1) },
                    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
                ) {
                    QuickAddChip("Today", CHIP_WIDTHS[0], state, { state.todayAmount.value }, if (isOpen) {
                        {
                            onActivity()
                            light()
                            state.toggleToday(reducedMotion)
                        }
                    } else null)
                    QuickAddChip("Priority", CHIP_WIDTHS[1], state, { state.priorityAmount.value }, if (isOpen) {
                        {
                            onActivity()
                            light()
                            state.togglePriority(reducedMotion)
                        }
                    } else null)
                    QuickAddListChip(state, CHIP_WIDTHS[2], if (isOpen) {
                        {
                            onActivity()
                            light()
                            state.cycleList(reducedMotion)
                        }
                    } else null)
                }

                // Add.
                val canAdd = state.canAdd
                val addSource = remember { MutableInteractionSource() }
                val pressed by addSource.collectIsPressedAsState()
                Box(
                    Modifier
                        .absoluteOffset(PAD.dp, ADD_TOP.dp)
                        .size((cardWidth - PAD * 2f).dp, ADD_HEIGHT.dp)
                        .graphicsLayer {
                            rise(this@graphicsLayer, state, 2)
                            val s = 1f - 0.04f * state.addPress.value
                            scaleX = s
                            scaleY = s
                        }
                        .graphicsLayer { alpha = if (canAdd) (if (pressed) 0.85f else 1f) else 0.42f }
                        .cssBoxShadow(rgba(20, 70, 58, 0.30f), 1.dp, 2.dp, (ADD_HEIGHT / 2f).dp)
                        .background(ACCENT, RoundedCornerShape(ADD_HEIGHT / 2f))
                        .quickAddInsetTop(rgba(255, 255, 255, 0.16f), (ADD_HEIGHT / 2f).dp)
                        .semantics { contentDescription = "Add task" }
                        .then(
                            if (isOpen) {
                                Modifier.clickable(addSource, indication = null, enabled = canAdd, role = Role.Button) { submit() }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Add", style = ADD_STYLE)
                }
            }

            // The pill's title while it flies.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        tick.longValue
                        alpha = seg(state.collapse.value, 0.5f, 0.4f) * (1f - seg(state.fly.value, 0.66f, 0.24f))
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(pillTitle, style = PILL_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // The + that turns into the close mark.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .absoluteOffset((-ICON_INSET).dp, ICON_INSET.dp)
                    .size(ICON.dp)
                    .graphicsLayer {
                        tick.longValue
                        alpha = 1f - clamp01(state.collapse.value * 3f)
                        rotationZ = clamp01(state.openW.value) * PLUS_TURN
                    }
                    .drawBehind {
                        tick.longValue
                        drawPlusBars(mixColor(clamp01(state.openH.value) / 0.6f, CREAM, INK))
                    },
            )
            if (isOpen) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .absoluteOffset((-8f).dp, 8f.dp)
                        .size(44.dp)
                        .semantics { contentDescription = "Close" }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { close() },
                )
            }
        }
    }
}

/** Field (0), chips (1) and Add (2) rise in on their shares of the content clock. */
private fun rise(scope: androidx.compose.ui.graphics.GraphicsLayerScope, state: QuickAddComposerState, index: Int) {
    state.host.tick.longValue
    val u = seg(state.content.value, CONTENT_STAGGER[index], CONTENT_ITEM)
    scope.alpha = u
    scope.translationY = (1f - u) * CONTENT_RISE * scope.density
}

@Composable
private fun QuickAddChip(label: String, width: Float, state: QuickAddComposerState, on: () -> Float, onClick: (() -> Unit)?) {
    val tick = state.host.tick
    Box(
        Modifier
            .size(width.dp, CHIP_HEIGHT.dp)
            .graphicsLayer {
                tick.longValue
                val s = 1f + 0.05f * sin(clamp01(on()) * PI.toFloat())
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                tick.longValue
                drawRoundRect(mixColor(clamp01(on()), FILL, ACCENT), cornerRadius = CornerRadius(size.height / 2f))
            }
            .semantics { contentDescription = label }
            .then(
                if (onClick != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onClick() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = CHIP_STYLE, maxLines = 1, color = {
            tick.longValue
            mixColor(clamp01(on()), INK, CREAM)
        })
    }
}

@Composable
private fun QuickAddListChip(state: QuickAddComposerState, width: Float, onClick: (() -> Unit)?) {
    val tick = state.host.tick
    val labels = state.labels
    val current = state.lists.getOrElse(state.listIndex) { "" }
    Row(
        Modifier
            .size(width.dp, CHIP_HEIGHT.dp)
            .background(FILL, RoundedCornerShape(CHIP_HEIGHT / 2f))
            .semantics { contentDescription = "List, $current" }
            .then(
                if (onClick != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onClick() }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false).height(LABEL_LINE.dp).clipToBounds()) {
            Column(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .graphicsLayer {
                        tick.longValue
                        translationY = -clamp01(state.listRoll.value) * LABEL_LINE * density
                    },
            ) {
                for (text in listOf(labels.first, labels.second)) {
                    Box(Modifier.height(LABEL_LINE.dp), contentAlignment = Alignment.CenterStart) {
                        BasicText(text, style = CHIP_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // The caret: a 6 dp square's right and bottom borders, turned 45°, lifted 3 dp.
        Box(
            Modifier
                .absoluteOffset(0.dp, (-3f).dp)
                .size(6.dp)
                .drawBehind {
                    val b = 1.6f.dp.toPx()
                    withTransform({ rotate(45f, center) }) {
                        drawRect(MUTE, Offset(size.width - b, 0f), Size(b, size.height))
                        drawRect(MUTE, Offset(0f, size.height - b), Size(size.width, b))
                    }
                },
        )
    }
}
