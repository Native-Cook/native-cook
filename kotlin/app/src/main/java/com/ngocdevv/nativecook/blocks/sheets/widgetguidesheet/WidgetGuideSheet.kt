package com.ngocdevv.nativecook.blocks.sheets.widgetguidesheet

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CAPTIONS = listOf(
    "On your Home Screen, press and hold an empty space.",
    "Tap the plus button in the top left.",
    "Find Daybreak, then choose a widget.",
    "Place your widget(s), then press the Done button in the top right.",
    "Press and hold a widget to customize it.",
)

/** The autoPlay loop, in seconds from its start. */
const val LOOP = 10.5f
private const val OPEN_AT = 0.8f
private val SLIDE_AT = listOf(2.8f, 4.4f, 6f, 7.8f)
private const val CLOSE_AT = 9.3f
private const val REST_AT = 9.9f
// Seconds a touch keeps the loop away.
private const val USER_HOLD = 3f

private val PAGE = Color(0xFFF4F4F6)
private val INK = Color(0xFF111111)
private val MUTE = Color(0xFF888888)
private val DOT = Color(0xFFD0D0D0)
private val CLOSE_FILL = Color(0xFFEFEFF2)
private val CLOSE_INK = Color(0xFF6D6D72)
private val PHONE_SHADOW = Color(0xFF1D2A8A)
private const val SCRIM = 0.45f
private val ROW_FILL = Color.White

private class Event(val name: String, val time: String, val days: String, val color: Color, val icon: ImageVector)

private val EVENTS = listOf(
    Event("Road trip", "Tomorrow · Big Sur", "1", Color(0xFFF4A000), Icons.Filled.DirectionsCar),
    Event("Graduation day", "Mon, 20 Oct · Campus hall", "7", Color(0xFFF0476B), Icons.Filled.School),
    Event("Lena's birthday", "Wed, 22 Oct", "9", Color(0xFF1FB3D6), Icons.Filled.CardGiftcard),
    Event("Mia's birthday", "Mon, 3 Nov", "21", Color(0xFF2FCF8A), Icons.Filled.Celebration),
    Event("Concert night", "Sun, 16 Nov · The Fillmore", "34", Color(0xFF7A55D9), Icons.Filled.MusicNote),
    Event("Winter break", "Fri, 19 Dec", "67", Color(0xFF3A6FE0), Icons.Filled.AcUnit),
)

private const val MARGIN = 20f
private const val SHEET_RADIUS = 28f
// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
private const val OVERSHOOT = 40f
private const val HEADER = 52f
private const val CAPTION_HEIGHT = 56f
private const val NAV = 44f
private const val NAV_GAP = 24f
private const val DOT_SIZE = 7f
private const val DOT_WIDE = 20f
private const val DOT_MS = 250
private const val SWIPE_DISTANCE = 40f
private const val DISMISS_VELOCITY = 900f

// Reanimated's damping 18, stiffness 170 and mass 1, as a damping ratio.
private val RISE_SPRING = spring<Float>(dampingRatio = 18f / (2f * Math.sqrt(170.0).toFloat()), stiffness = 170f)
private const val DROP_MS = 400
private val DROP_EASING = CubicBezierEasing(0.5f, 0f, 1f, 1f)
private const val SLIDE_MS = 700
private val SLIDE_EASING = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)
private const val DASH_MS = 1000
private const val DASH_PERIOD = 8f
private const val SHIMMER_AFTER = 500
private const val SHIMMER_MS = 700
private val SHIMMER_EASING = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private const val FADE_MS = 160

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

/**
 * A guide in a sheet: a miniature home screen shows, one slide at a time, how to add a widget. A
 * single number moves between slides and every part of the picture (the empty slot, the plus,
 * the picker that rises inside it, the widgets that drop in, the blurred page and its menu) is a
 * function of it, so the slides cross-fade, step backwards, and follow a swipe or the chevrons.
 *
 * @param captions What the guide says under the picture, one line of text per slide.
 * @param autoPlay Plays the whole guide by itself, over and over, and gives way to a touch.
 * @param onSlideChange Called when a slide settles, with its place in [captions].
 */
@Composable
fun WidgetGuideSheet(
    captions: List<String> = CAPTIONS,
    autoPlay: Boolean = false,
    title: String = "Widget Guide",
    onSlideChange: ((Int) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    WidgetGuideSheetSystemBars()
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val lines by rememberUpdatedState(captions)
    val slideChanged by rememberUpdatedState(onSlideChange)
    val closed by rememberUpdatedState(onClose)
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInset = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }

    // 0 hidden, 1 up; the slide number; the dashes' march; the picker's shimmer.
    val sheet = remember { Animatable(0f) }
    val step = remember { Animatable(0f) }
    val dash = remember { Animatable(0f) }
    val shimmer = remember { Animatable(1f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var slide by remember { mutableIntStateOf(0) }
    var isOpen by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var closeJob by remember { mutableStateOf<Job?>(null) }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun go(index: Int) {
        val next = min(lines.size - 1, max(0, index))
        slide = next
        scope.launch { step.animateTo(next.toFloat(), tween(if (reducedMotion) FADE_MS else SLIDE_MS, easing = SLIDE_EASING)) }
        if (next == 2 && !reducedMotion) {
            scope.launch {
                shimmer.snapTo(0f)
                shimmer.animateTo(1f, tween(SHIMMER_MS, SHIMMER_AFTER, SHIMMER_EASING))
            }
        }
        slideChanged?.invoke(next)
    }

    fun open() {
        closeJob?.cancel()
        isOpen = true
        scope.launch { if (reducedMotion) sheet.animateTo(1f, tween(FADE_MS)) else sheet.animateTo(1f, RISE_SPRING) }
        if (reducedMotion) return
        scope.launch {
            dash.snapTo(0f)
            dash.animateTo(DASH_PERIOD, infiniteRepeatable(tween(DASH_MS, easing = LinearEasing), RepeatMode.Restart))
        }
    }

    fun close() {
        isOpen = false
        closed?.invoke()
        closeJob = scope.launch {
            sheet.animateTo(0f, tween(if (reducedMotion) FADE_MS else DROP_MS, easing = DROP_EASING))
            // Out of sight now: the next opening starts from the first slide.
            dash.snapTo(0f)
            step.snapTo(0f)
            drag = 0f
            slide = 0
        }
    }

    /** Everything back as it was at the start, with no animation. */
    fun rest() {
        closeJob?.cancel()
        dragJob?.cancel()
        scope.launch {
            for (value in listOf(sheet, step, dash)) value.snapTo(0f)
            shimmer.snapTo(1f)
        }
        drag = 0f
        isOpen = false
        slide = 0
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        val steps = listOf(OPEN_AT to { open() }) +
            SLIDE_AT.mapIndexed { index, at -> at to { go(index + 1) } } +
            listOf(CLOSE_AT to { close() }, REST_AT to { rest() })
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

    BoxWithConstraints(modifier.fillMaxSize().background(PAGE)) {
        val screenWidth = maxWidth
        val sheetHeight = maxHeight - topInset
        val phoneWidth = screenWidth - MARGIN.dp * 2
        val navBottom = max(bottomInset.value, 12f) + 4f
        val sheetHeightPx by rememberUpdatedState(with(localDensity) { sheetHeight.toPx() })
        // The caption sits halfway between the picture and the buttons, however tall the screen is.
        val captionTop = min(
            sheetHeight.value - navBottom - NAV - NAV_GAP - CAPTION_HEIGHT,
            HEADER + PHONE_HEIGHT.value + (sheetHeight.value - navBottom - NAV - HEADER - PHONE_HEIGHT.value - CAPTION_HEIGHT) / 2f,
        )

        // The page behind: a list of coming events. A tap anywhere opens the guide.
        Column(
            Modifier
                .fillMaxSize()
                .clickable(enabled = !isOpen, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    touched()
                    open()
                }
                .padding(top = topInset + 8.dp, start = MARGIN.dp, end = MARGIN.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("MONDAY, 13 OCTOBER", color = MUTE, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text("Up next", color = INK, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                }
                Box(Modifier.padding(bottom = 2.dp).size(44.dp).background(INK, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            for (event in EVENTS) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .dropShadow(ROW_SHAPE, Shadow(radius = 8.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.04f))
                        .background(ROW_FILL, ROW_SHAPE)
                        .padding(start = 12.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        Modifier.size(52.dp).background(event.color, RoundedCornerShape(15.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(event.days, color = Color.White, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (event.days == "1") "DAY" else "DAYS",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(event.name, color = INK, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                        Text(event.time, color = MUTE, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(event.icon, null, tint = event.color, modifier = Modifier.size(20.dp))
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = SCRIM * min(1f, sheet.value) * (1f - min(1f, drag / sheetHeightPx)) }
                .background(Color.Black),
        )

        // The sheet, built taller than it shows.
        Box(
            Modifier
                .offset(y = topInset)
                .size(screenWidth, sheetHeight + OVERSHOOT.dp)
                .graphicsLayer { translationY = (1f - sheet.value) * (sheetHeightPx + OVERSHOOT * density) + drag }
                .dropShadow(SHEET_SHAPE, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, (-6).dp), alpha = 0.2f))
                .graphicsLayer {
                    shape = SHEET_SHAPE
                    clip = true
                }
                .background(Color.White)
                .pointerInput(Unit) {
                    // One drag for both jobs: sideways over the picture changes the slide, downwards drags the sheet away.
                    val slop = 12.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var axis = ' '
                        var total = Offset.Zero
                        val phoneTop = HEADER * density
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp() || !change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            total += change.position - change.previousPosition
                            if (axis == ' ' && total.getDistance() > slop) {
                                val sideways = abs(total.x) > abs(total.y)
                                val overPhone = down.position.y > phoneTop && down.position.y < phoneTop + PHONE_HEIGHT.toPx()
                                axis = if (sideways) (if (overPhone) 'x' else 'n') else 'y'
                                if (axis == 'y') dragJob?.cancel()
                                if (axis != 'n') touched()
                            }
                            if (axis == 'y') {
                                drag = max(0f, total.y)
                                change.consume()
                            } else if (axis == 'x') {
                                change.consume()
                            }
                        }
                        val velocity = tracker.calculateVelocity()
                        if (axis == 'x') {
                            if (total.x < -SWIPE_DISTANCE * density) go(slide + 1) else if (total.x > SWIPE_DISTANCE * density) go(slide - 1)
                        } else if (axis == 'y') {
                            if (drag > sheetHeightPx / 5f || velocity.y > DISMISS_VELOCITY * density) {
                                // From here the sheet is where the finger left it.
                                val from = 1f - drag / sheetHeightPx
                                drag = 0f
                                scope.launch {
                                    sheet.snapTo(from)
                                    close()
                                }
                            } else {
                                dragJob = scope.launch { animate(drag, 0f, animationSpec = RISE_SPRING) { value, _ -> drag = value } }
                            }
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth().height(HEADER.dp), contentAlignment = Alignment.Center) {
                Text(title, color = MUTE, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = 11.dp)
                        .size(30.dp)
                        .background(CLOSE_FILL, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            touched()
                            close()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = CLOSE_INK, modifier = Modifier.size(17.dp))
                }
            }

            Scene(
                phoneWidth,
                { step.value },
                { dash.value },
                { shimmer.value },
                Labels(),
                Modifier
                    .offset(x = MARGIN.dp, y = HEADER.dp)
                    .dropShadow(PHONE_SHAPE, Shadow(radius = 20.dp, color = PHONE_SHADOW, offset = DpOffset(0.dp, 10.dp), alpha = 0.25f)),
            )

            Box(
                Modifier
                    .offset(y = captionTop.dp)
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .height(CAPTION_HEIGHT.dp),
            ) {
                captions.forEachIndexed { index, caption ->
                    Text(
                        caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val near = max(0f, 1f - abs(step.value - index) * 2.2f)
                                alpha = near
                                translationY = lerp(6f, 0f, near) * density
                            },
                        color = INK,
                        fontSize = 19.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (OVERSHOOT + navBottom).dp)
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    .height(NAV.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Chevron(left = true, label = "Previous", disabled = slide == 0) {
                    touched()
                    go(slide - 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    captions.indices.forEach { index ->
                        val on = index == slide
                        val dotWidth by animateDpAsState(if (on) DOT_WIDE.dp else DOT_SIZE.dp, tween(DOT_MS), label = "dot")
                        val dotColor by animateColorAsState(if (on) INK else DOT, tween(DOT_MS), label = "dotColor")
                        Box(Modifier.size(dotWidth, DOT_SIZE.dp).background(dotColor, CircleShape))
                    }
                }
                Chevron(left = false, label = "Next", disabled = slide == captions.size - 1) {
                    touched()
                    go(slide + 1)
                }
            }
        }
    }
}

@Composable
private fun Chevron(left: Boolean, label: String, disabled: Boolean, onPress: () -> Unit) {
    Box(
        Modifier
            .graphicsLayer { alpha = if (disabled) 0.35f else 1f }
            .size(NAV.dp)
            .background(CLOSE_FILL, CircleShape)
            .clickable(enabled = !disabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = label,
            tint = INK,
            modifier = Modifier.size(26.dp),
        )
    }
}

private val SHEET_SHAPE = RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)
private val PHONE_SHAPE = RoundedCornerShape(30.dp)
private val ROW_SHAPE = RoundedCornerShape(20.dp)

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun WidgetGuideSheetSystemBars(light: Boolean = true) {
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
