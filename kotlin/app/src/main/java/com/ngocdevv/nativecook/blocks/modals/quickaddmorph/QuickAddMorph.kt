package com.ngocdevv.nativecook.blocks.modals.quickaddmorph

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Tally, a tasks app: the screen, the list and the autoPlay script around the reusable MorphComposer.

// Layout, in dp.
private const val SIDE = 16f
private const val HEADER = 118f
private const val ROW = 64f
private const val ROW_HEIGHT = 56f
private const val INSERT = 1 // new tasks land in this slot
private const val FAB = 60f
private const val FAB_SIDE = 20f
private const val FAB_BOTTOM = 12f
private const val PILL_WIDTH = 188f
private const val PILL_HEIGHT = 44f
private const val CARD_MAX = 440f
private const val CHECK = 26f
private const val CHECK_LEFT = 14f
private const val CHECK_LENGTH = 20f
private const val CHECK_HIT = 10f
private const val GHOST = 44f

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2

private val TAPS = floatArrayOf(QuickAddT.OPEN, QuickAddT.PRIORITY, QuickAddT.LIST, QuickAddT.ADD, QuickAddT.CHECK)

private val BRAND_STYLE = quickAddStyle(15f, FontWeight.Bold, ACCENT)
private val COUNT_STYLE = quickAddStyle(14f, FontWeight.Medium, MUTE, tabular = true)
private val TITLE_STYLE = quickAddStyle(34f, FontWeight.Bold, INK, line = 40f)
private val DATE_STYLE = quickAddStyle(15f, FontWeight.Normal, MUTE)
private val ROW_TITLE_STYLE = quickAddStyle(16f, FontWeight.SemiBold, INK)
private val ROW_META_STYLE = quickAddStyle(13f, FontWeight.Normal, MUTE)

private fun ghostPress(t: Float): Float {
    var press = 0f
    for (tap in TAPS) {
        val d = t - tap
        if (d > -0.1f && d < 0.12f) press = max(press, if (d < 0f) 1f + d / 0.1f else 1f - d / 0.12f)
    }
    return press
}

private fun ghostOpacity(t: Float): Float {
    val a = seg(t, QuickAddT.GHOST_IN, 0.2f) * (1f - seg(t, QuickAddT.OPEN + 0.3f, 0.22f))
    val b = seg(t, QuickAddT.PRIORITY - 0.38f, 0.2f) * (1f - seg(t, QuickAddT.ADD + 0.14f, 0.2f))
    val c = seg(t, QuickAddT.CHECK - 0.45f, 0.22f) * (1f - seg(t, QuickAddT.CHECK + 0.2f, 0.2f))
    return max(a, max(b, c))
}

/** One task row's motion: its slot in the list, its y spring and its done amount. */
internal class QuickAddRowMotion(host: QuickAddHost, val task: QuickAddTask, var slot: Int, done: QuickAddValue?) {
    val y = QuickAddValue(host, slot * ROW)
    val done = done ?: QuickAddValue(host, if (task.done) 1f else 0f)
    var checked by mutableStateOf(task.done)
}

/** The screen's frame loop (RN `useFrameCallback`) and its autoPlay script. Nothing here is Compose state but the rows. */
internal class QuickAddEngine(val host: QuickAddHost, val composer: QuickAddComposerState, tasks: List<QuickAddTask>, playing: Boolean) {
    val part = QuickAddValue(host, 0f)
    val scriptDone = QuickAddValue(host, 0f)
    val ghostO = QuickAddValue(host, 0f)
    var ghostX = 0f
    var ghostY = 0f
    var ghostS = 0f

    val rows = mutableStateListOf<QuickAddRowMotion>().apply {
        tasks.forEachIndexed { index, task -> add(QuickAddRowMotion(host, task, index, null)) }
    }
    val scriptRow = QuickAddRowMotion(
        host,
        QuickAddTask("script", QuickAddScript.TITLE, QuickAddScript.META, priority = QuickAddScript.PRIORITY),
        INSERT,
        scriptDone,
    )
    var doneCount by mutableIntStateOf(tasks.count { it.done })
    var pillTitle by mutableStateOf(QuickAddScript.TITLE)
    private var pending: IntArray? = null
    private var flyPast = false
    private var nextId = 0

    var mode = if (playing) MODE_AUTO else MODE_MANUAL
        private set
    private var playing = playing
    var touches = 0
    private var seen = 0
    var held = 0
    var focused = false
    private var idle = 0f
    private var clock = 0f
    private var lastFrame = -1L

    var geometry: ComposerGeometry? = null
        private set
    private var listTop = 0f
    var scrollDp: () -> Float = { 0f }
    private var path = Array(0) { FloatArray(3) }

    fun layout(g: ComposerGeometry, top: Float) {
        if (g === geometry && top == listTop) return
        geometry = g
        listTop = top
        val fabX = g.fab.x + FAB / 2f
        val fabY = g.fab.y + FAB / 2f
        val card = g.card
        val chipY = card.y + CHIPS_TOP + CHIP_HEIGHT / 2f
        val priorityX = card.x + PAD + CHIP_WIDTHS[0] + CHIP_GAP + CHIP_WIDTHS[1] / 2f
        val listX = card.x + PAD + CHIP_WIDTHS[0] + CHIP_WIDTHS[1] + CHIP_GAP * 2f + CHIP_WIDTHS[2] / 2f
        val addY = card.y + ADD_TOP + ADD_HEIGHT / 2f
        val addX = card.x + card.width / 2f
        val fieldY = card.y + FIELD_TOP + FIELD_HEIGHT / 2f
        val checkX = SIDE + CHECK_LEFT + CHECK / 2f
        val checkY = top + INSERT * ROW + ROW_HEIGHT / 2f
        path = arrayOf(
            floatArrayOf(QuickAddT.GHOST_IN, fabX + 26f, fabY + 34f),
            floatArrayOf(QuickAddT.OPEN - 0.08f, fabX, fabY),
            floatArrayOf(QuickAddT.OPEN + 0.12f, fabX, fabY),
            floatArrayOf(QuickAddT.OPEN + 0.52f, card.x + 120f, fieldY),
            floatArrayOf(QuickAddT.PRIORITY - 0.38f, priorityX + 16f, chipY + 26f),
            floatArrayOf(QuickAddT.PRIORITY - 0.06f, priorityX, chipY),
            floatArrayOf(QuickAddT.PRIORITY + 0.1f, priorityX, chipY),
            floatArrayOf(QuickAddT.LIST - 0.06f, listX, chipY),
            floatArrayOf(QuickAddT.LIST + 0.1f, listX, chipY),
            floatArrayOf(QuickAddT.ADD - 0.06f, addX, addY),
            floatArrayOf(QuickAddT.ADD + 0.34f, addX, addY),
            floatArrayOf(QuickAddT.CHECK - 0.45f, checkX + 30f, checkY + 34f),
            floatArrayOf(QuickAddT.CHECK - 0.06f, checkX, checkY),
            floatArrayOf(QuickAddT.CHECK + 0.4f, checkX, checkY),
        )
        host.dirty = true
    }

    private fun ghostAt(t: Float, axis: Int): Float {
        if (t <= path[0][0]) return path[0][axis]
        for (i in 1 until path.size) {
            if (t <= path[i][0]) {
                val u = easeInOutCubic((t - path[i - 1][0]) / (path[i][0] - path[i - 1][0]))
                return mix(u, path[i - 1][axis], path[i][axis])
            }
        }
        return path[path.size - 1][axis]
    }

    fun setPlaying(next: Boolean) {
        if (next == playing) return
        playing = next
        if (next) return
        // Reduced motion or autoPlay off: the resting screen, nothing scripted.
        val wasScripted = mode == MODE_AUTO
        mode = MODE_MANUAL
        composer.scripted = false
        ghostO.set(0f)
        if (wasScripted) {
            restPose()
            composer.resetDraft()
        }
        host.dirty = true
    }

    fun frame(nanos: Long) {
        val dt = if (lastFrame < 0) 1f / 60f else min((nanos - lastFrame) / 1e9f, 0.048f)
        lastFrame = nanos
        if (dt <= 0f) return
        composer.rowY = listTop + INSERT * ROW - scrollDp()
        if (playing) script(dt)
        host.step(dt)
        // Rows part once the pill is on its way.
        val past = composer.fly.value > 0.2f
        if (past && !flyPast) applyPending()
        flyPast = past
        host.publish()
    }

    /**
     * A finger went down or a control was used. The takeover runs at once, not on the next frame: a tap whose press
     * and release land in one frame would otherwise open the composer and then have the late takeover close it.
     */
    fun touched() {
        touches += 1
        if (playing) noteTouches()
    }

    private fun noteTouches() {
        if (touches == seen) return
        seen = touches
        idle = 0f
        if (mode != MODE_MANUAL) {
            if (mode == MODE_AUTO) takeOver()
            mode = MODE_MANUAL
            host.dirty = true
        }
    }

    private fun script(dt: Float) {
        noteTouches()
        when (mode) {
            MODE_MANUAL -> {
                if (focused || held > 0) {
                    idle = 0f
                    return
                }
                // A flight in progress finishes first.
                if (composer.fly.value > 0f) return
                idle += dt
                if (idle >= RESUME_AFTER) {
                    if (composer.active.value == 1f) closeForReturn()
                    mode = MODE_RETURN
                }
            }
            MODE_RETURN -> {
                if (composer.active.value == 0f && part.value < 0.01f) {
                    clock = 0f
                    composer.scripted = true
                    composer.resetDraft()
                    pillTitle = QuickAddScript.TITLE
                    mode = MODE_AUTO
                    host.dirty = true
                }
            }
            else -> {
                var t = clock + dt
                if (t >= LOOP) t -= LOOP
                clock = t
                applyPose(t)
                val x = ghostAt(t, 1)
                val y = ghostAt(t, 2)
                val s = ghostPress(t)
                if (x != ghostX || y != ghostY || s != ghostS) {
                    ghostX = x
                    ghostY = y
                    ghostS = s
                    host.dirty = true
                }
                ghostO.set(ghostOpacity(t))
            }
        }
    }

    /** Every animated value of the composer at loop time t. */
    private fun applyPose(t: Float) {
        val c = composer
        val on = t >= QuickAddT.OPEN && t < QuickAddT.FLY + QuickAddT.FLY_SPAN
        c.active.set(if (on) 1f else 0f)
        if (on) {
            val since = t - QuickAddT.OPEN
            c.openW.set(springAt(since, WIDTH_SPRING))
            c.openH.set(springAt(since - HEIGHT_DELAY, HEIGHT_SPRING))
            c.collapse.set(clamp01(springAt(t - QuickAddT.ADD - 0.06f, COLLAPSE_SPRING)))
            c.content.set(easeOutCubic(seg(since, CONTENT_DELAY, CONTENT_IN)) * (1f - seg(t, QuickAddT.ADD + 0.02f, CONTENT_OUT)))
            c.fly.set(easeInOutCubic(seg(t, QuickAddT.FLY, QuickAddT.FLY_SPAN)))
        } else {
            c.openW.set(0f)
            c.openH.set(0f)
            c.collapse.set(0f)
            c.content.set(0f)
            c.fly.set(0f)
        }
        c.fabIn.set(
            when {
                t < QuickAddT.OPEN -> 1f
                t < QuickAddT.FLY + FAB_DELAY -> 0f
                else -> min(1.04f, springAt(t - QuickAddT.FLY - FAB_DELAY, FAB_SPAN, 0.8f))
            },
        )
        c.press.set(if (t < QuickAddT.OPEN + 0.05f) ghostPress(t) else 0f)
        c.addPress.set(if (t > QuickAddT.ADD - 0.2f && t < QuickAddT.ADD + 0.2f) ghostPress(t) else 0f)

        val fall = easeInOutCubic(seg(t, QuickAddT.REMOVE, QuickAddT.REMOVE_SPAN))
        part.set(springAt(t - QuickAddT.FLY - PART_DELAY, PART_SPRING) * (1f - fall))
        c.landed.set(easeOutCubic(seg(t, QuickAddT.FLY + LAND_DELAY, LAND_SPAN)) * (1f - fall))
        scriptDone.set(
            if (t < QuickAddT.REMOVE + QuickAddT.REMOVE_SPAN) easeInOutCubic(seg(t, QuickAddT.CHECK, QuickAddT.CHECK_SPAN)) else 0f,
        )

        val chars = QuickAddScript.TITLE.length
        val count = if (t < QuickAddT.TYPE_START || t >= QuickAddT.FLY) {
            0
        } else {
            min(chars, floor((t - QuickAddT.TYPE_START) / QuickAddT.TYPE_STEP).toInt() + 1)
        }
        c.setTyped(count)
        c.todayAmount.set(1f)
        c.priorityAmount.set(if (t >= QuickAddT.FLY) 0f else easeOutCubic(seg(t, QuickAddT.PRIORITY, 0.22f)))
        c.listRoll.set(if (t >= QuickAddT.FLY) 0f else easeInOutCubic(seg(t, QuickAddT.LIST, 0.26f)))
    }

    private fun restPose() {
        val c = composer
        c.active.set(0f)
        c.openW.set(0f)
        c.openH.set(0f)
        c.content.set(0f)
        c.collapse.set(0f)
        c.fly.set(0f)
        c.fabIn.set(1f)
        c.press.set(0f)
        c.addPress.set(0f)
        c.landed.set(0f)
        c.setTyped(0)
        c.priorityAmount.set(0f)
        c.listRoll.set(0f)
        part.set(0f)
        scriptDone.set(0f)
    }

    /** A touch mid-loop: an open composer stays open and becomes real; anything else settles back to rest. */
    private fun takeOver() {
        val c = composer
        c.scripted = false
        ghostO.timing(0f, 0.12f)
        c.press.set(0f)
        c.addPress.set(0f)
        part.spring(0f, PART_SPRING)
        c.landed.timing(0f, 0.16f)
        scriptDone.timing(0f, 0.16f)
        val composerOpen = c.active.value == 1f && c.collapse.value < 0.05f && c.openH.value > 0.5f
        if (composerOpen) {
            c.openW.spring(1f, WIDTH_SPRING)
            c.openH.spring(1f, HEIGHT_SPRING)
            c.content.timing(1f, 0.16f)
            // A chip caught mid-change settles to the state the draft adopts, not a half tint.
            c.priorityAmount.timing(if (c.priorityAmount.value > 0.5f) 1f else 0f, 0.16f)
            c.listRoll.timing(if (c.listRoll.value > 0.5f) 1f else 0f, 0.16f)
            c.adopt(c.todayAmount.value > 0.5f, c.priorityAmount.value > 0.5f, if (c.listRoll.value > 0.5f) 1 else 0)
        } else if (c.active.value == 1f) {
            c.content.timing(0f, CONTENT_OUT)
            c.collapse.timing(0f, 0.16f)
            c.fly.timing(0f, 0.16f)
            c.openW.spring(0f, HEIGHT_SPRING)
            c.springClosed()
            c.setTyped(0)
            c.priorityAmount.timing(0f, 0.16f)
            c.listRoll.timing(0f, 0.16f)
            c.resetDraft()
        } else {
            c.fabIn.timing(1f, 0.16f)
            c.setTyped(0)
            c.priorityAmount.timing(0f, 0.16f)
            c.listRoll.timing(0f, 0.16f)
            c.resetDraft()
        }
    }

    private fun closeForReturn() {
        val c = composer
        c.content.timing(0f, CONTENT_OUT)
        c.openW.spring(0f, HEIGHT_SPRING)
        c.springClosed()
        c.priorityAmount.timing(0f, 0.16f)
        c.listRoll.timing(0f, 0.16f)
    }

    private fun applyPending() {
        val next = pending ?: return
        pending = null
        for (i in rows.indices) {
            val slot = next.getOrElse(i) { rows[i].slot }
            val row = rows[i]
            if (slot != row.slot) {
                row.slot = slot
                row.y.spring(slot * ROW, PART_SPRING)
            }
        }
    }

    /** Add: the new row waits under the pill in slot 1, the rows below part when the pill takes off. */
    fun add(draft: QuickAddDraft): QuickAddTask {
        nextId += 1
        val task = QuickAddTask(
            id = "new-$nextId",
            title = draft.title,
            meta = if (draft.today) "${draft.list} · Today" else draft.list,
            priority = draft.priority,
        )
        val current = pending ?: IntArray(rows.size) { rows[it].slot }
        pending = IntArray(rows.size + 1) { i -> if (i < rows.size) current.getOrElse(i) { rows[i].slot }.let { if (it >= INSERT) it + 1 else it } else INSERT }
        composer.landId = rows.size
        pillTitle = draft.title
        rows.add(QuickAddRowMotion(host, task, INSERT, null))
        host.dirty = true
        return task
    }

    fun toggle(row: QuickAddRowMotion) {
        val next = !row.checked
        row.checked = next
        row.done.spring(if (next) 1f else 0f, DONE_SPRING)
        doneCount += if (next) 1 else -1
    }

    /** The ghost finger: hidden outside autoPlay. */
    fun ghostAlpha() = if (mode == MODE_AUTO) ghostO.value * 0.9f else 0f
}

/**
 * @param autoPlay plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch.
 */
@Composable
fun QuickAddMorph(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    tasks: List<QuickAddTask> = QuickAddTasks,
    lists: List<String> = QuickAddLists,
    onAdd: (QuickAddTask) -> Unit = {},
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val host = remember { QuickAddHost() }
    val composer = remember { QuickAddComposerState(host, lists).also { it.scriptTitle = QuickAddScript.TITLE } }
    composer.lists = lists
    val engine = remember { QuickAddEngine(host, composer, tasks, playing) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val added by rememberUpdatedState(onAdd)

    LaunchedEffect(engine, playing) { engine.setPlaying(playing) }
    LaunchedEffect(engine) {
        while (true) withFrameNanos { engine.frame(it) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PAPER)
            .clipToBounds()
            // Any finger on the screen interrupts autoPlay; the loop waits while one is down.
            .pointerInput(engine) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) {
                            engine.held = event.changes.count { it.pressed }
                            engine.touched()
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val width = maxWidth.value
        val height = maxHeight.value
        val bars = WindowInsets.systemBars
        val top = bars.getTop(density) / density.density
        val insetBottom = bars.getBottom(density) / density.density
        val listTop = top + HEADER
        val bottom = max(insetBottom, 16f) + FAB_BOTTOM
        val geometry = remember(width, height, bottom) {
            val fab = QuickAddRect(width - FAB_SIDE - FAB, height - bottom - FAB, FAB, FAB)
            val cardWidth = min(width - SIDE * 2f, CARD_MAX)
            val card = QuickAddRect(width - SIDE - cardWidth, height - bottom - CARD_HEIGHT, cardWidth, CARD_HEIGHT)
            val pill = QuickAddRect(card.x + (cardWidth - PILL_WIDTH) / 2f, card.y + card.height - PILL_HEIGHT - 14f, PILL_WIDTH, PILL_HEIGHT)
            ComposerGeometry(fab, card, pill, SIDE, width - SIDE * 2f, ROW_HEIGHT)
        }
        engine.layout(geometry, listTop)
        engine.scrollDp = { scroll.value / density.density }

        val rowWidth = width - SIDE * 2f
        val total = engine.rows.size

        // Header.
        Column(Modifier.fillMaxWidth().padding(top = (top + 8f).dp, start = (SIDE + 4f).dp, end = (SIDE + 4f).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Tally", Modifier.alignByBaseline(), style = BRAND_STYLE)
                BasicText("${engine.doneCount} of $total done", Modifier.alignByBaseline(), style = COUNT_STYLE)
            }
            BasicText("Today", Modifier.padding(top = 2.dp), style = TITLE_STYLE)
            BasicText("Tuesday, November 24", Modifier.padding(top = 1.dp), style = DATE_STYLE)
        }

        // The list.
        Box(Modifier.fillMaxSize().padding(top = listTop.dp)) {
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(((total + 1) * ROW + bottom + FAB + 24f).dp)) {
                    engine.rows.forEachIndexed { index, row ->
                        key(row.task.id) {
                            QuickAddTaskRow(engine, row, index, rowWidth, script = false) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                engine.toggle(row)
                            }
                        }
                    }
                    QuickAddTaskRow(engine, engine.scriptRow, -2, rowWidth, script = true, onToggle = null)
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((bottom + FAB + 44f).dp)
                .background(
                    Brush.verticalGradient(
                        0f to PAPER.copy(alpha = 0f),
                        0.55f to PAPER.copy(alpha = 0.82f),
                        1f to PAPER,
                    ),
                ),
        )

        MorphComposer(
            state = composer,
            geometry = geometry,
            pillTitle = engine.pillTitle,
            reducedMotion = reduced,
            onSubmit = { draft ->
                val task = engine.add(draft)
                scope.launch { scroll.animateScrollTo(0) }
                added(task)
            },
            onActivity = { engine.touched() },
            onFocusChange = { engine.focused = it },
            keyboardInset = bottom - 8f,
        )

        // The ghost finger.
        val tick = host.tick
        Box(
            Modifier
                .size(GHOST.dp)
                .graphicsLayer {
                    tick.longValue
                    alpha = engine.ghostAlpha()
                    translationX = (engine.ghostX - GHOST / 2f) * density.density
                    translationY = (engine.ghostY - GHOST / 2f) * density.density
                    val s = 1f - 0.16f * engine.ghostS
                    scaleX = s
                    scaleY = s
                }
                .background(rgba(120, 114, 104, 0.34f), CircleShape)
                .border(1.dp, rgba(255, 255, 255, 0.72f), CircleShape),
        )
    }
}

@Composable
private fun QuickAddTaskRow(
    engine: QuickAddEngine,
    row: QuickAddRowMotion,
    index: Int,
    width: Float,
    script: Boolean,
    onToggle: (() -> Unit)?,
) {
    val tick = engine.host.tick
    val task = row.task
    val checkPath = remember { Path() }
    val checkPart = remember { Path() }
    val measure = remember { PathMeasure() }
    Row(
        Modifier
            .absoluteOffset(x = SIDE.dp)
            .size(width.dp, ROW_HEIGHT.dp)
            .graphicsLayer {
                tick.longValue
                val c = engine.composer
                // The scripted row only stands in for the autoPlay add (landId < 0), never under a real one.
                alpha = when {
                    script -> if (c.landId < 0) c.landed.value else 0f
                    c.landId == index -> c.landed.value
                    else -> 1f
                }
                val parted = if (!script && row.slot >= INSERT) engine.part.value * ROW else 0f
                translationY = (row.y.value + parted) * density
            }
            .cssBoxShadow(rgba(58, 44, 28, 0.07f), 1.dp, 2.dp, RADIUS_ROW.dp)
            .cssBoxShadow(rgba(58, 44, 28, 0.05f), 6.dp, 14.dp, RADIUS_ROW.dp)
            .background(CARD, RoundedCornerShape(RADIUS_ROW.dp))
            .quickAddInsetTop(rgba(255, 255, 255, 0.8f), RADIUS_ROW.dp)
            .then(if (script) Modifier.clearAndSetSemantics { } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check, with 10 dp of slop around the 26 dp ring.
        Box(
            Modifier
                .fillMaxHeight()
                .width((CHECK_LEFT + CHECK + CHECK_HIT).dp)
                .then(
                    if (onToggle != null) {
                        Modifier
                            .semantics {
                                role = Role.Checkbox
                                contentDescription = task.title
                                toggleableState = ToggleableState(row.checked)
                            }
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onToggle() }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.padding(start = CHECK_LEFT.dp).size(CHECK.dp)) {
                tick.longValue
                val d = clamp01(row.done.value)
                val ring = 1.6f.dp.toPx()
                drawCircle(rgba(33, 30, 26, 0.22f), radius = size.width / 2f - ring / 2f, style = Stroke(ring))
                if (d > 0f) {
                    scale(0.7f + 0.3f * d) { drawCircle(ACCENT.copy(alpha = d), radius = size.width / 2f) }
                    val k = size.width / CHECK
                    checkPath.reset()
                    checkPath.moveTo(7.5f * k, 13.6f * k)
                    checkPath.lineTo(11.4f * k, 17.4f * k)
                    checkPath.lineTo(18.6f * k, 9.2f * k)
                    measure.setPath(checkPath, false)
                    checkPart.reset()
                    // A 20 dp dash pulled in by its offset: the first 20·d dp of the mark.
                    measure.getSegment(0f, min(measure.length, CHECK_LENGTH * k * d), checkPart, true)
                    drawPath(checkPart, CARD, style = Stroke(2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = (12f - CHECK_HIT).dp)) {
            Box(
                Modifier.drawWithContent {
                    drawContent()
                    tick.longValue
                    val d = clamp01(row.done.value)
                    if (d > 0f) {
                        val h = 1.5f.dp.toPx()
                        drawRect(MUTE.copy(alpha = d), Offset(0f, size.height / 2f - h / 2f), Size(size.width * d, h))
                    }
                },
            ) {
                BasicText(
                    task.title,
                    Modifier.graphicsLayer {
                        tick.longValue
                        alpha = 1f - 0.5f * clamp01(row.done.value)
                    },
                    style = ROW_TITLE_STYLE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(task.meta, Modifier.padding(top = 1.dp), style = ROW_META_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (task.priority) {
            Box(Modifier.padding(start = 10.dp).size(8.dp).background(AMBER, CircleShape).semantics { contentDescription = "Priority" })
        }
        Spacer(Modifier.width(16.dp))
    }
}
