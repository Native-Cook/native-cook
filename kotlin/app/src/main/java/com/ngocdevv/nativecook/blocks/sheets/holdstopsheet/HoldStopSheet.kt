package com.ngocdevv.nativecook.blocks.sheets.holdstopsheet

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.DisposableEffect
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Seconds for one pass of the autoPlay loop. */
const val LOOP = 10f

// The timeline, in seconds from the start of a loop.
// The pile is on screen from the end of one loop to Start, and again from the stop on.
private const val PRESS_START_AT = 1.25f
private const val START_AT = 1.4f
private const val HOLD_AT = 2.6f
private const val PRESS_GOT_IT_AT = 7.3f
private const val DISMISS_AT = 7.5f
/** Seconds after the last touch before the loop starts again from rest. */
private const val USER_HOLD = 3f

private val IdleTop = Color(0xFFFDE3D2)
private val PeachTop = Color(0xFFF7AE88)
private val PeachMid = Color(0xFFFDE3D1)
private val Ink = Color(0xFF4A3B35)
private val Mute = Color(0xFF8C7A71)
private val Bookmark = Color(0xFFD9A58B)
private val Track = rgba(74, 59, 53, 0.16f)
private val ButtonColor = Color(0xFF4A403B)
private val ButtonPressed = Color(0xFF2E2724)
private val Card = Color(0xFFF6ECE4)
private val CardTitle = Color(0xFF3E2F28)
private val CardBody = Color(0xFF77675E)
private val Grabber = rgba(62, 47, 40, 0.18f)
private val GotIt = Color(0xFFD0504A)
private val GotItPressed = Color(0xFFAB3C37)

private const val TIMER_SIZE = 84f
private const val DIGIT_WIDTH = 52f
private const val COLON_WIDTH = 24f
private const val TIMER_HEIGHT = 100f
private const val START_WIDTH = 156f
private const val START_HEIGHT = 56f
private const val HOLD_LINE_WIDTH = 140f
private const val HOLD_LINE_HEIGHT = 4f
private const val FOOTER_HEIGHT = 8f + 4f + 12f + 20f + 8f
private const val SHEET_MARGIN = 10f
private const val SHEET_HEIGHT = 244f
private const val SHEET_RADIUS = 36f
private const val GOT_IT_WIDTH = 188f
private const val GOT_IT_HEIGHT = 52f

// The durations of the pieces, in milliseconds.
private const val HOLD = 2400
private const val BACKGROUND = 500
private const val FALL = 650
private const val RISE = 1500
private const val ROLL = 200
private const val ROLL_RISE = 12f
private const val DIM = 0.45f
private const val DIM_IN = 300
private const val SHEET_AFTER = 700L
private const val PULSE_AFTER = 700L
private const val FOOTER_AFTER = 700L
private const val FADE = 180
private const val REST_FADE = 350
// Damping ratio is Reanimated's damping over 2 * sqrt(stiffness * mass).
private val SheetSpring = spring<Float>(dampingRatio = 15f / (2f * kotlin.math.sqrt(180f)), stiffness = 180f)
/** Milliseconds between the light ticks while the hold fills. */
private const val TICK = 500L

private val CubicOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val CubicIn = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
private val QuadIn = CubicBezierEasing(0.11f, 0f, 0.5f, 0f)
private val QuadInOut = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private enum class Pressed { None, Start, GotIt }

private fun timed(ms: Int, easing: Easing = CubicOut) = tween<Float>(ms, easing = easing)

private fun clock(seconds: Int): String {
    val left = max(0, seconds)
    return "%02d:%02d".format(left / 60, left % 60)
}

/**
 * A focus timer. "Start Focus" drops the tomatoes away and the day turns peach; "Hold To Stop
 * Focus" fills a line while it is held, and stopping early resets the timer, drops the tomatoes
 * back with bounces and warns in a sheet. The autoPlay loop presses and holds the same controls.
 *
 * @param duration The length of a focus session, in seconds.
 * @param autoPlay Starts, holds Stop to the end and dismisses the sheet by itself every [LOOP] seconds.
 * @param onStop Called when the hold completes, with the seconds that were left.
 */
@Composable
fun HoldStopSheet(
    duration: Int = 300,
    startLabel: String = "Start Focus",
    holdLabel: String = "Hold To Stop Focus",
    sheetTitle: String = "Focus time is less than 5 minutes",
    sheetBody: String = "Pomodoros lasting less than 5 minutes will not be recorded. Keep going! Every focus session will get better and better (^_^)",
    gotItLabel: String = "Got it",
    autoPlay: Boolean = false,
    onStart: (() -> Unit)? = null,
    onStop: ((secondsLeft: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    HoldStopSheetLightBars()
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val session by rememberUpdatedState(duration)
    val started by rememberUpdatedState(onStart)
    val stopped by rememberUpdatedState(onStop)

    var running by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(duration) }
    var sheetOpen by remember { mutableStateOf(false) }
    var autoPressed by remember { mutableStateOf(Pressed.None) }
    var holding by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf<Job?>(null) }
    var ticking by remember { mutableStateOf<Job?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    val run = remember { Animatable(0f) }
    val footer = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    val sheet = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val animations = remember { HashMap<Animatable<Float, *>, Job>() }

    /** Animates [value] after [after] ms, then calls [then]; starting another animation of it cancels this one. */
    fun play(value: Animatable<Float, *>, to: Float, spec: AnimationSpec<Float>, after: Long = 0, then: (() -> Unit)? = null) {
        animations[value]?.cancel()
        animations[value] = scope.launch {
            if (after > 0) delay(after)
            value.animateTo(to, spec)
            then?.invoke()
        }
    }

    fun set(value: Animatable<Float, *>, to: Float) {
        animations[value]?.cancel()
        scope.launch { value.snapTo(to) }
    }

    fun clearTimers() {
        countdown?.cancel()
        countdown = null
        ticking?.cancel()
        ticking = null
    }

    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun start() {
        if (running) return
        autoPressed = Pressed.None
        running = true
        left = session
        set(rise, 0f)
        play(fall, 1f, timed(if (reducedMotion) FADE else FALL, QuadIn))
        play(run, 1f, timed(BACKGROUND, QuadInOut))
        play(footer, 1f, timed(FADE), FOOTER_AFTER)
        // One second off the timer, each at its own absolute time from the start.
        countdown = scope.launch {
            val origin = System.nanoTime()
            for (second in 1..max(1, session)) {
                delay(max(0L, (origin + second * 1_000_000_000L - System.nanoTime()) / 1_000_000))
                left = session - second
            }
        }
        started?.invoke()
    }

    fun stop() {
        holding = false
        clearTimers()
        stopped?.invoke(left)
        running = false
        left = session
        sheetOpen = true
        play(run, 0f, timed(BACKGROUND, QuadInOut))
        play(footer, 0f, timed(FADE))
        set(fill, 0f)
        set(rise, 0f)
        play(rise, 1f, tween(if (reducedMotion) FADE else RISE, easing = LinearEasing))
        play(dim, DIM, timed(DIM_IN))
        if (reducedMotion) play(sheet, 1f, timed(FADE), FADE.toLong()) else play(sheet, 1f, SheetSpring, SHEET_AFTER)
        animations[pulse]?.cancel()
        animations[pulse] = scope.launch {
            delay(PULSE_AFTER + SHEET_AFTER)
            pulse.animateTo(1.08f, timed(150))
            pulse.animateTo(1f, timed(250))
        }
    }

    fun beginHold(felt: Boolean) {
        if (!running || holding) return
        holding = true
        play(fill, 1f, tween(HOLD, easing = LinearEasing)) { stop() }
        if (felt) {
            haptic()
            ticking = scope.launch {
                while (true) {
                    delay(TICK)
                    haptic()
                }
            }
        }
    }

    fun endHold() {
        if (!holding) return
        holding = false
        ticking?.cancel()
        ticking = null
        play(fill, 0f, timed(250))
    }

    fun dismiss() {
        autoPressed = Pressed.None
        sheetOpen = false
        val ms = if (reducedMotion) FADE else 450
        play(dim, 0f, timed(ms))
        play(sheet, 0f, timed(ms, CubicIn)) {
            // The pile is back in place behind the sheet; only the pieces of the animation reset.
            if (!sheetOpen) {
                set(fall, 0f)
                set(rise, 0f)
            }
        }
    }

    fun rest() {
        clearTimers()
        holding = false
        running = false
        left = session
        sheetOpen = false
        autoPressed = Pressed.None
        for (value in listOf(run, footer, fill, dim, sheet)) play(value, 0f, timed(REST_FADE))
        // A pile that is back in place stays; one that is away rises back from below.
        if (rise.value > 0.99f) set(fall, 0f) else play(fall, 0f, timed(REST_FADE))
        set(rise, 0f)
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoPressed = Pressed.None
        touches++
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(PRESS_START_AT)
            autoPressed = Pressed.Start
            until(START_AT)
            start()
            until(HOLD_AT)
            beginHold(felt = false)
            until(PRESS_GOT_IT_AT)
            autoPressed = Pressed.GotIt
            until(DISMISS_AT)
            dismiss()
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.White)
            // Sees every touch without taking it from the buttons.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched()
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val width = maxWidth.value
        val height = maxHeight.value
        val top = insets.calculateTopPadding().value
        val bottom = max(insets.calculateBottomPadding().value, 12f)
        val text = clock(left)

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to IdleTop, 0.6f to Color.White)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = run.value }
                .background(Brush.verticalGradient(0f to PeachTop, 0.6f to PeachMid, 1f to Color.White)),
        )

        Icon(Icons.Filled.Bookmark, null, Modifier.offset(x = (width - 24f - 20f).dp, y = (top + 6f).dp).size(20.dp), tint = Bookmark)

        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = height * (0.22f + 0.1f * run.value) * this.density },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rollRise = with(LocalDensity.current) { ROLL_RISE.dp.roundToPx() }
            Row(Modifier.semantics { contentDescription = text }) {
                text.forEach { char ->
                    // A new character rolls up into place while the old one rolls away.
                    AnimatedContent(
                        targetState = char,
                        modifier = Modifier.size(if (char == ':') COLON_WIDTH.dp else DIGIT_WIDTH.dp, TIMER_HEIGHT.dp),
                        transitionSpec = {
                            if (reducedMotion) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                (fadeIn(tween(ROLL, easing = LinearEasing)) + slideInVertically(tween(ROLL, easing = LinearEasing)) { rollRise }) togetherWith
                                    (fadeOut(tween(ROLL, easing = LinearEasing)) + slideOutVertically(tween(ROLL, easing = LinearEasing)) { -rollRise })
                            }
                        },
                        contentAlignment = Alignment.Center,
                        label = "glyph",
                    ) { shown ->
                        Label(
                            shown.toString(), TIMER_SIZE, TIMER_HEIGHT, FontWeight.ExtraBold, Ink,
                            Modifier.wrapContentWidth(unbounded = true), letterSpacing = -1f, features = "tnum",
                        )
                    }
                }
            }
            Row(
                Modifier.padding(top = 4.dp).graphicsLayer { alpha = 1f - run.value },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("Focus", 20f, 26f, FontWeight.Bold, Ink)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Mute)
            }
        }

        Tomatoes(
            width, height, fall, rise, reducedMotion,
            Modifier.offset(y = (height - PILE_HEIGHT).dp).fillMaxWidth().height(PILE_HEIGHT.dp),
        )

        val startSource = remember { MutableInteractionSource() }
        val startPressed by startSource.collectIsPressedAsState()
        val startFar = remember { SoftRoundRect(rgba(74, 40, 25, 0.35f), 12f) }
        val startNear = remember { SoftRoundRect(rgba(74, 40, 25, 0.25f), 2f) }
        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = (height - PILE_HEIGHT * 0.5f - START_HEIGHT).dp)
                .graphicsLayer {
                    val down = min(1f, fall.value * 1.6f)
                    val r = rise.value
                    val back = min(1f, max(0f, (r - 0.55f) / 0.45f))
                    when {
                        reducedMotion -> alpha = 1f - down * (1f - r)
                        r > 0f -> translationY = -height * (1f - bounce(back)) * this.density
                        else -> translationY = 480f * down * down * this.density
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .size(START_WIDTH.dp, START_HEIGHT.dp)
                    .drawBehind {
                        val r = size.height / 2f
                        drawIntoCanvas {
                            startFar.draw(it.nativeCanvas, 0f, 10f * this.density, size.width, size.height, r)
                            startNear.draw(it.nativeCanvas, 0f, 2f * this.density, size.width, size.height, r)
                        }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(if (startPressed || autoPressed == Pressed.Start) ButtonPressed else ButtonColor)
                    .clickable(startSource, null, enabled = !running && !sheetOpen, role = Role.Button, onClick = ::start),
                contentAlignment = Alignment.Center,
            ) { Label(startLabel, 18f, 22f, FontWeight.Bold, Color.White, letterSpacing = -0.2f) }
        }

        // The hold's hit area reaches 16 dp past the label, as `hitSlop` did.
        Box(
            Modifier
                .offset(y = (height - (bottom + 12f) - FOOTER_HEIGHT - 16f).dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = footer.value * (1f - 0.45f * fill.value) }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        beginHold(felt = true)
                        tryAwaitRelease()
                        endHold()
                    })
                }
                .padding(16.dp)
                .semantics { contentDescription = holdLabel },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(HOLD_LINE_WIDTH.dp, HOLD_LINE_HEIGHT.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Track)
                        .drawBehind {
                            drawRoundRect(Ink, size = Size(size.width * fill.value, size.height), cornerRadius = CornerRadius(size.height / 2f))
                        },
                )
                Label(holdLabel, 16f, 20f, FontWeight.Bold, Ink)
            }
        }

        // The dim also takes the touches while the sheet is up.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dim.value }
                .background(Color.Black)
                .pointerInput(sheetOpen) {
                    if (!sheetOpen) return@pointerInput
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
                },
        )

        val sheetFar = remember { SoftRoundRect(rgba(40, 20, 10, 0.35f), 24f) }
        val sheetNear = remember { SoftRoundRect(rgba(40, 20, 10, 0.15f), 3f) }
        Column(
            Modifier
                .offset(x = SHEET_MARGIN.dp, y = (height - max(SHEET_MARGIN, bottom - 8f) - SHEET_HEIGHT).dp)
                .width((width - 2 * SHEET_MARGIN).dp)
                .height(SHEET_HEIGHT.dp)
                .graphicsLayer {
                    val p = sheet.value
                    if (reducedMotion) alpha = p else translationY = (1f - p) * (SHEET_HEIGHT + bottom + SHEET_MARGIN + 24f) * this.density
                }
                .drawBehind {
                    val r = SHEET_RADIUS * this.density
                    drawIntoCanvas {
                        sheetFar.draw(it.nativeCanvas, 0f, 20f * this.density, size.width, size.height, r)
                        sheetNear.draw(it.nativeCanvas, 0f, 2f * this.density, size.width, size.height, r)
                    }
                }
                .clip(RoundedCornerShape(SHEET_RADIUS.dp))
                .background(Card)
                .padding(start = 28.dp, end = 28.dp, top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(bottom = 18.dp).size(40.dp, 5.dp).background(Grabber, RoundedCornerShape(3.dp)))
            Label(sheetTitle, 20f, 26f, FontWeight.ExtraBold, CardTitle, letterSpacing = -0.3f)
            Label(sheetBody, 14f, 20f, FontWeight.Normal, CardBody, Modifier.padding(top = 8.dp, bottom = 20.dp))
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val glow = remember { SoftRoundRect(rgba(208, 80, 74, 0.35f), 9f) }
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .size(GOT_IT_WIDTH.dp, GOT_IT_HEIGHT.dp)
                    .drawBehind {
                        drawIntoCanvas { glow.draw(it.nativeCanvas, 0f, 8f * this.density, size.width, size.height, size.height / 2f) }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(if (pressed || autoPressed == Pressed.GotIt) GotItPressed else GotIt)
                    .clickable(source, null, enabled = sheetOpen, role = Role.Button, onClick = ::dismiss),
                contentAlignment = Alignment.Center,
            ) { Label(gotItLabel, 17f, 22f, FontWeight.ExtraBold, Color.White) }
        }
    }
}

@Composable
private fun Label(
    text: String,
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    letterSpacing: Float = 0f,
    features: String? = null,
) = BasicText(
    text,
    modifier,
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        fontFeatureSettings = features,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
)

/** The page is light, so the status bar icons go dark while the block is on screen. */
@Composable
private fun HoldStopSheetLightBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = true
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
