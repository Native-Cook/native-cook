package com.ngocdevv.nativecook.blocks.sheets.sheetcardhandoff

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Stage { Idle, Open, Closing, Added }

private val TASK = Task(
    name = "Founder Playbook",
    caption = "Short lessons on building from scratch, read every morning before work.",
    time = "7:30AM",
    color = Color(0xFF4DD0CB),
)
private val EXISTING = Task(
    name = "Investor Mindset",
    caption = "Mental models for calm decisions, with one worked case study a day.",
    time = "9:00AM",
    color = Color(0xFFF58A4B),
    progress = 0.6f,
)
private val LATER = listOf(
    Task(
        name = "Deep Work Sprint",
        caption = "Ninety focused minutes on the one thing that moves the week forward.",
        time = "1:30PM",
        color = Color(0xFF8C7CF0),
        status = TaskStatus.Later,
        progress = 0f,
    ),
    Task(
        name = "Evening Reflection",
        caption = "Three questions to close the day, and one line to remember it by.",
        time = "9:00PM",
        color = Color(0xFFF2B63D),
        status = TaskStatus.Later,
        progress = 0f,
    ),
)
private val FRONT_LINES = listOf(
    "Ship the smallest thing that teaches you something." to "Day 1, on first versions",
    "Talk to ten users before you write a line of code." to "Day 2, on listening",
    "Say no to a hundred good ideas." to "Day 3, on focus",
)

/** One timeline for the autoPlay loop, in seconds from its start. */
const val LOOP = 9f
private const val TAP_NEW_AT = 0.9f
private const val TAP_FOR = 0.15f
private const val TAP_DONE_AT = 3.9f
private const val CLEAR_AT = 7.3f
private const val REST_AT = 8.2f
// Seconds a touch keeps the loop away.
private const val USER_HOLD = 3f

private val PAGE = Color.White
private val INK = Color(0xFF17181A)
private val MUTE = Color(0xFF8D8F94)
private val OUTLINE = Color(0xFFE3E4E6)
private val WELL = Color(0xFFF5F5F6)
private val WELL_INK = Color(0xFFA9ABB0)
private val DASH = Color(0xFFD6D7DA)
private val DOT = Color(0xFF34C759)
private val GRABBER = Color(0xFFE2E3E6)
private val BLUE = Color(0xFF4AB8F0)
private val BLUE_SOFT = Color(0xFF8CCBF0)
private val DONE_SHADOW = Color(0xFF1D8FD0)
private val BACK_LEFT = Color(0xFF3FC2BC)
private val BACK_RIGHT = Color(0xFF38B5AF)
private val FRONT_SHADOW = Color(0xFF0B6F6B)
private val BADGE = Color(0xFFF2364C)
private const val SCRIM = 0.55f
// React Native's hairline width doubled, on a 3x screen.
private val HAIRLINE = (2f / 3f).dp

private const val MARGIN = 20f
private const val PILL_HEIGHT = 28f
private const val TITLE_TOP = 48f
private const val NEW_TOP = 132f
private const val NEW_HEIGHT = 52f
private const val GAP = 16f
private const val LIST_TOP = NEW_TOP + NEW_HEIGHT + 16f
private const val SLOT = 112f + GAP
// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
private const val SHEET_HEIGHT = 432f
private const val SHEET_OVERSHOOT = 48f
private const val SHEET_RADIUS = 28f
private const val BUTTON_HEIGHT = 56f
private const val FAN_TOP = 132f
private const val FAN_WIDTH = 150f
private const val FAN_HEIGHT = 186f
private const val FAN_TILT = 7f
private const val FAN_SPREAD = 22f
private const val TAB_HEIGHT = 60f
private const val FAN_STAGGER = 0.08f
private const val FAN_DURATION = 0.5f
private const val FAN_AT = 700
private const val PRESS_SCALE = 0.95f
private const val DISMISS_DRAG = 90f
private const val DISMISS_VELOCITY = 800f

// Reanimated's damping 17, stiffness 190 and mass 1, as a damping ratio.
private val SHEET_SPRING = spring<Float>(dampingRatio = 17f / (2f * Math.sqrt(190.0).toFloat()), stiffness = 190f)
// A 450 ms spring with damping ratio 0.8: stiffness is (2π / duration)².
private val FLIGHT_SPRING = spring<Float>(dampingRatio = 0.8f, stiffness = 195f)
private const val CLOSE_MS = 350
private val CLOSE_EASING = CubicBezierEasing(0.5f, 0f, 1f, 1f)
private const val FADE_MS = 250
// The list starts to move this long after the card leaves the sheet.
private const val LIST_AFTER = 250L
private const val BADGE_AFTER = 1200L
private const val CLEAR_MS = 600
// Reanimated's default easing for withTiming.
private val QUAD = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private val CUBIC = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
private fun ramp(value: Float, from: Float, to: Float) = min(1f, max(0f, (value - from) / (to - from)))
/** Soft start and end of a 0 → 1 run. */
private fun smooth(t: Float) = t * t * (3f - 2f * t)

/**
 * A task list with a bottom sheet over it. The sheet shows a fan of cards and a Done button; Done
 * drops the sheet and the front card flies out of it into the first slot of the list, growing from
 * 150 dp to the width of the list. The card is drawn in an overlay above both, since it belongs to
 * neither, and its small and large layouts cross-fade so that text is never scaled.
 *
 * @param task The task the sheet hands to the list.
 * @param existing The task already in the list.
 * @param later Later tasks under it, which make room too.
 * @param autoPlay Plays the whole interaction by itself, over and over, and gives way to a touch.
 * @param onAdd Called when the card has arrived in the list.
 */
@Composable
fun SheetCardHandoff(
    task: Task = TASK,
    existing: Task = EXISTING,
    later: List<Task> = LATER,
    autoPlay: Boolean = false,
    newLabel: String = "+ NEW TASK",
    title: String = "ALL SET",
    caption: String = "This is a preview. The full cards will be delivered later.",
    doneLabel: String = "DONE",
    onAdd: ((Task) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    SheetCardHandoffSystemBars()
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val handed by rememberUpdatedState(task)
    val added by rememberUpdatedState(onAdd)
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInset = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val sheetHeight = SHEET_HEIGHT.dp + bottomInset
    val sheetHeightPx by rememberUpdatedState(with(localDensity) { sheetHeight.toPx() })

    // 0 hidden, 1 resting; the fan, the flight and the list each have a run of their own.
    val sheet = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    val fan = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(false) }
    var flyFrom by remember { mutableFloatStateOf(0f) }
    val shift = remember { Animatable(0f) }
    val landed = remember { Animatable(0f) }
    val badge = remember { Animatable(0f) }
    var stage by remember { mutableStateOf(Stage.Idle) }
    var listed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var autoNew by remember { mutableStateOf(false) }
    var autoDone by remember { mutableStateOf(false) }
    var badgeJob by remember { mutableStateOf<Job?>(null) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var dragRaw by remember { mutableFloatStateOf(0f) }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoNew = false
        autoDone = false
        touches++
    }

    fun open() {
        if (stage != Stage.Idle) return
        stage = Stage.Open
        if (reducedMotion) {
            scope.launch { sheet.animateTo(1f, tween(FADE_MS)) }
            scope.launch { fan.snapTo(1f) }
            return
        }
        scope.launch { sheet.animateTo(1f, SHEET_SPRING) }
        scope.launch { fan.animateTo(1f, tween(((FAN_DURATION + FAN_STAGGER * 2) * 1000).toInt(), FAN_AT, LinearEasing)) }
    }

    fun arrived() {
        stage = Stage.Added
        listed = true
        added?.invoke(handed)
    }

    fun close() {
        if (stage != Stage.Open) return
        stage = Stage.Closing
        if (reducedMotion) {
            scope.launch { sheet.animateTo(0f, tween(FADE_MS)) }
            scope.launch { landed.animateTo(1f, tween(FADE_MS)) }
            scope.launch { shift.animateTo(1f, tween(FADE_MS)) }
            scope.launch { badge.animateTo(1f, tween(FADE_MS)) }
            arrived()
            return
        }
        scope.launch { sheet.animateTo(0f, tween(CLOSE_MS, easing = CLOSE_EASING)) }
        flying = true
        scope.launch {
            fly.animateTo(1f, FLIGHT_SPRING)
            // The overlay and the list's own card swap in the same frame.
            landed.snapTo(1f)
            flying = false
            arrived()
            badgeJob = launch {
                delay(BADGE_AFTER)
                badge.animateTo(1.15f, tween(160, easing = QUAD))
                badge.animateTo(1f, tween(120, easing = QUAD))
            }
        }
        scope.launch {
            delay(LIST_AFTER)
            shift.animateTo(1f, FLIGHT_SPRING)
        }
    }

    fun clear() {
        if (stage != Stage.Added) return
        stage = Stage.Idle
        badgeJob?.cancel()
        scope.launch { landed.animateTo(0f, tween((CLEAR_MS * 0.7f).toInt(), easing = QUAD)) }
        scope.launch { badge.animateTo(0f, tween((CLEAR_MS * 0.5f).toInt(), easing = QUAD)) }
        scope.launch { shift.animateTo(0f, tween(CLEAR_MS, easing = CUBIC)) }
        listed = false
    }

    /** Everything back as it was at the start, with no animation. */
    fun rest() {
        badgeJob?.cancel()
        dragJob?.cancel()
        scope.launch {
            for (value in listOf(sheet, fan, fly, shift, landed, badge)) value.snapTo(0f)
        }
        drag = 0f
        dragRaw = 0f
        flying = false
        flyFrom = 0f
        autoNew = false
        autoDone = false
        stage = Stage.Idle
        listed = false
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        val steps = listOf(
            TAP_NEW_AT to { autoNew = true },
            TAP_NEW_AT + TAP_FOR to { autoNew = false; open() },
            TAP_DONE_AT to { autoDone = true },
            TAP_DONE_AT + TAP_FOR to { autoDone = false; close() },
            CLEAR_AT to { clear() },
            REST_AT to { rest() },
        )
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, run) in steps) {
                until(at)
                run()
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    fun far(velocity: Float): Boolean = drag > DISMISS_DRAG * localDensity.density || velocity > DISMISS_VELOCITY * localDensity.density

    BoxWithConstraints(modifier.fillMaxSize().background(PAGE)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val cardWidth = screenWidth - MARGIN.dp * 2
        val listTop = topInset + LIST_TOP.dp
        val count = 2 + later.size + if (listed) 1 else 0

        // Header
        Row(
            Modifier.offset(y = topInset + 8.dp).fillMaxWidth().padding(horizontal = MARGIN.dp).height(PILL_HEIGHT.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill {
                Box(Modifier.size(7.dp).clip(CircleShape).background(DOT))
                PillText("$count ${if (count == 1) "TASK" else "TASKS"}")
            }
            Box(Modifier.weight(1f))
            Pill {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = INK, modifier = Modifier.size(12.dp))
                PillText("27 Jan")
            }
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(PILL_HEIGHT.dp)
                    .clip(CircleShape)
                    .background(PAGE)
                    .border(HAIRLINE, OUTLINE, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = INK, modifier = Modifier.size(15.dp))
            }
        }

        Column(Modifier.offset(x = MARGIN.dp, y = topInset + TITLE_TOP.dp)) {
            Text("Today", color = INK, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(
                "Monday · Your focus plan",
                modifier = Modifier.padding(top = 2.dp),
                color = MUTE,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // New task
        PressScale(
            forced = autoNew,
            onDown = { touched() },
            onTap = {
                clear()
                open()
            },
            modifier = Modifier.offset(x = MARGIN.dp, y = topInset + NEW_TOP.dp).size(cardWidth, NEW_HEIGHT.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(WELL)
                    .dashedBorder(DASH, 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(newLabel, color = WELL_INK, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        }

        // The list: the new task lands in the first slot, the one that was there moves down.
        Box(Modifier.offset(x = MARGIN.dp, y = listTop).graphicsLayer { alpha = landed.value }) {
            TaskCard(task, cardWidth, Modifier.lift(task.color))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .graphicsLayer {
                        scaleX = badge.value
                        scaleY = badge.value
                        alpha = min(1f, badge.value * 4f)
                    }
                    .height(20.dp)
                    .dropShadow(CircleShape, Shadow(radius = 6.dp, color = BADGE, offset = DpOffset(0.dp, 2.dp), alpha = 0.4f))
                    .clip(CircleShape)
                    .background(BADGE)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("NEW", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            }
        }
        Column(
            Modifier.offset(x = MARGIN.dp, y = listTop).graphicsLayer { translationY = shift.value * SLOT * density },
            verticalArrangement = Arrangement.spacedBy(GAP.dp),
        ) {
            for (item in listOf(existing) + later) TaskCard(item, cardWidth, Modifier.lift(item.color))
        }

        // The app's own tab bar, over a fade so the list slides under it.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(TAB_HEIGHT.dp + bottomInset + 72.dp)
                .background(FADE),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxOf(bottomInset, 12.dp) + 4.dp)
                .height(TAB_HEIGHT.dp)
                .dropShadow(CircleShape, Shadow(radius = 20.dp, color = Color.Black, offset = DpOffset(0.dp, 10.dp), alpha = 0.12f))
                .clip(CircleShape)
                .background(Color.White)
                .border(HAIRLINE / 2, Color.Black.copy(alpha = 0.06f), CircleShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.height(44.dp).clip(CircleShape).background(INK).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Tasks", color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            Tab(Icons.Outlined.ViewAgenda, 20.dp)
            Tab(Icons.Outlined.BarChart, 21.dp)
            Tab(Icons.Outlined.AccountCircle, 23.dp)
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = SCRIM * min(1f, sheet.value) * (1f - ramp(drag, 0f, sheetHeightPx)) }
                .background(Color.Black),
        )

        // The sheet, built taller than it shows.
        Box(
            Modifier
                .offset(y = screenHeight - sheetHeight)
                .size(screenWidth, sheetHeight + SHEET_OVERSHOOT.dp)
                .graphicsLayer { translationY = (1f - sheet.value) * sheetHeightPx + drag }
                .dropShadow(SHEET_SHAPE, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, (-8).dp), alpha = 0.18f))
                .clip(SHEET_SHAPE)
                .background(Color.White)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragRaw += delta
                        drag = max(0f, dragRaw)
                    },
                    onDragStarted = {
                        dragJob?.cancel()
                        dragRaw = drag
                        touched()
                    },
                    onDragStopped = { velocity ->
                        dragRaw = 0f
                        if (far(velocity)) {
                            // From here the sheet is where the finger left it, and the card starts from there too.
                            flyFrom = drag / localDensity.density
                            sheet.snapTo(1f - drag / sheetHeightPx)
                            drag = 0f
                            close()
                        } else {
                            dragJob = scope.launch { animate(drag, 0f, animationSpec = SHEET_SPRING) { value, _ -> drag = value } }
                        }
                    },
                ),
        ) {
            val titleSpan = FAN_DURATION + FAN_STAGGER * 2
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(36.dp, 5.dp)
                    .clip(CircleShape)
                    .background(GRABBER),
            )
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    // Under the grabber: 8 above it, 5 for it, 18 below.
                    .padding(top = 31.dp)
                    .padding(horizontal = 40.dp)
                    .graphicsLayer {
                        val t = smooth(ramp(fan.value, 0f, 0.5f))
                        alpha = t
                        scaleX = lerp(0.8f, 1f, t)
                        scaleY = lerp(0.8f, 1f, t)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = BLUE, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                Text(
                    caption,
                    modifier = Modifier.padding(top = 6.dp),
                    color = BLUE_SOFT,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            Box(Modifier.align(Alignment.TopCenter).offset(y = FAN_TOP.dp).size(FAN_WIDTH.dp, FAN_HEIGHT.dp)) {
                for (index in 0..1) {
                    val sign = if (index == 0) -1f else 1f
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = smooth(ramp(fan.value, index * FAN_STAGGER / titleSpan, (index * FAN_STAGGER + FAN_DURATION) / titleSpan))
                                alpha = t
                                translationX = sign * FAN_SPREAD * t * density
                                translationY = 8f * t * density
                                rotationZ = sign * FAN_TILT * t
                            }
                            .clip(FAN_SHAPE)
                            .background(if (index == 0) BACK_LEFT else BACK_RIGHT),
                    ) {
                        BackFace()
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (flying) 0f else 1f }
                        .dropShadow(FAN_SHAPE, Shadow(radius = 16.dp, color = FRONT_SHADOW, offset = DpOffset(0.dp, 10.dp), alpha = 0.3f))
                        .clip(FAN_SHAPE)
                        .background(task.color),
                ) {
                    FanFace(task)
                }
            }

            PressScale(
                forced = autoDone,
                onDown = { touched() },
                onTap = { close() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SHEET_OVERSHOOT.dp + maxOf(bottomInset, 0.dp) + 20.dp)
                    .size(screenWidth - MARGIN.dp * 2, BUTTON_HEIGHT.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .dropShadow(CircleShape, Shadow(radius = 12.dp, color = DONE_SHADOW, offset = DpOffset(0.dp, 6.dp), alpha = 0.35f))
                        .clip(CircleShape)
                        .background(BLUE),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(doneLabel, color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }

        // The flying card, above both the sheet and the list.
        if (flying) {
            Box(
                Modifier
                    .offset {
                        val p = fly.value
                        IntOffset(
                            lerp(((screenWidth - FAN_WIDTH.dp) / 2).toPx(), MARGIN.dp.toPx(), p).roundToInt(),
                            lerp((screenHeight - sheetHeight + FAN_TOP.dp).toPx() + flyFrom * density, listTop.toPx(), p).roundToInt(),
                        )
                    }
                    .layout { measurable, _ ->
                        val p = fly.value
                        val w = lerp(FAN_WIDTH.dp.toPx(), cardWidth.toPx(), p).roundToInt()
                        val h = lerp(FAN_HEIGHT.dp.toPx(), CARD_HEIGHT.toPx(), p).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.place(0, 0) }
                    }
                    .dropShadow(RoundedCornerShape(16.dp), Shadow(radius = 14.dp, color = task.color, offset = DpOffset(0.dp, 8.dp), alpha = 0.3f))
                    .graphicsLayer {
                        shape = RoundedCornerShape(lerp(16f, 20f, fly.value).dp)
                        clip = true
                    }
                    .background(task.color),
                contentAlignment = Alignment.TopStart,
            ) {
                Box(Modifier.graphicsLayer { alpha = 1f - ramp(fly.value, 0.15f, 0.55f) }.requiredSize(FAN_WIDTH.dp, FAN_HEIGHT.dp)) { FanFace(task) }
                Box(Modifier.graphicsLayer { alpha = ramp(fly.value, 0.15f, 0.55f) }) { TaskCard(task, cardWidth) }
            }
        }
    }
}

private val SHEET_SHAPE = RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)
private val FAN_SHAPE = RoundedCornerShape(16.dp)
private val FADE = Brush.verticalGradient(0f to Color.White.copy(alpha = 0f), 0.55f to Color.White.copy(alpha = 0.92f), 1f to PAGE)

/** A coloured glow under each card, large and soft. */
private fun Modifier.lift(color: Color) =
    dropShadow(RoundedCornerShape(20.dp), Shadow(radius = 14.dp, color = color, offset = DpOffset(0.dp, 8.dp), alpha = 0.28f))

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Row(
        Modifier
            .height(PILL_HEIGHT.dp)
            .clip(CircleShape)
            .background(PAGE)
            .border(HAIRLINE, OUTLINE, CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun PillText(text: String) {
    Text(text, color = INK, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
}

@Composable
private fun Tab(icon: ImageVector, size: Dp) {
    Box(Modifier.height(44.dp).width(52.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MUTE, modifier = Modifier.size(size))
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val width = 1.5.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(width / 2f, width / 2f),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Squashes to [PRESS_SCALE] while pressed, or while the autoPlay loop holds it. */
@Composable
private fun PressScale(
    forced: Boolean,
    onDown: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val down = pressed || forced
    val scale by animateFloatAsState(if (down) PRESS_SCALE else 1f, tween(if (down) 80 else 140), label = "press")
    val latestDown by rememberUpdatedState(onDown)
    val latestTap by rememberUpdatedState(onTap)
    Box(
        modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        latestDown()
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { latestTap() },
                )
            },
    ) { content() }
}

/** The front card of the fan: a small printed card with a title, a lede and three dated lines. */
@Composable
private fun FanFace(task: Task) {
    Column(
        Modifier
            .requiredSize(FAN_WIDTH.dp, FAN_HEIGHT.dp)
            .clip(FAN_SHAPE)
            .background(FACE_LIGHT)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp),
    ) {
        Text(
            task.name,
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.2).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            "Lessons on building from scratch",
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        // The rule runs to the card's edges, past the padding.
        Box(
            Modifier
                .padding(top = 9.dp, bottom = 7.dp)
                .layout { measurable, constraints ->
                    val bleed = 12.dp.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + bleed * 2, maxWidth = constraints.maxWidth + bleed * 2))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                }
                .height(HAIRLINE)
                .background(Color.White.copy(alpha = 0.35f)),
        )
        for ((line, note) in FRONT_LINES) {
            Column(Modifier.padding(bottom = 5.dp)) {
                Text("– $line", color = Color.White, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(note, color = Color.White.copy(alpha = 0.72f), fontSize = 7.sp, lineHeight = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

// From the top left to 70 % across, 80 % down.
private val FACE_LIGHT = object : ShaderBrush() {
    override fun createShader(size: Size) = LinearGradientShader(
        from = Offset.Zero,
        to = Offset(size.width * 0.7f, size.height * 0.8f),
        colors = listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0f)),
    )
}

/** A card behind the front one: only its top edge shows, so a ruled title is enough. */
@Composable
private fun BackFace() {
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.width(FAN_WIDTH.dp * 0.7f).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        Box(Modifier.width(FAN_WIDTH.dp * 0.48f).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f * 0.6f)))
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun SheetCardHandoffSystemBars(light: Boolean = true) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, light) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
