package com.ngocdevv.nativecook.blocks.sheets.detentmethodsheet

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Method(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    /** Shown in the trailing pill. */
    val badge: String,
    val instant: Boolean = false,
    /** The pill carries a green tick. */
    val enabled: Boolean = false,
)

val SampleMethods = listOf(
    Method(Icons.Outlined.AccountBalance, "Connect bank account", "4-5 business days", "Free"),
    Method(Icons.Outlined.CreditCard, "Debit card", "Instant transfer", "2% fee", instant = true),
    Method(Icons.Filled.SwapHoriz, "Regular bank transfer", "0-1 business days", "Free"),
    Method(Icons.Filled.Diamond, "Get paid from partner", "Instant transfer", "Enabled", instant = true, enabled = true),
    Method(Icons.Outlined.AccountBalanceWallet, "Crypto wallet", "Instant transfer", "1% fee", instant = true),
)

/** Seconds for one pass of the autoPlay loop. */
const val LOOP = 10f

// The timeline, in seconds from the start of a loop.
private const val TOUCH_BUTTON_AT = 0.35f
private const val PRESS_BUTTON_AT = 0.5f
private const val OPEN_AT = 0.65f
private const val TOUCH_GRABBER_AT = 2.2f
private const val DRAG_UP_AT = 2.4f
private const val RELEASE_UP_AT = 3.15f
private const val TOUCH_ROW_AT = 5.0f
private const val PRESS_ROW_AT = 5.4f
private const val SELECT_AT = 5.7f
private const val TOUCH_GRABBER_AGAIN_AT = 6.5f
private const val DRAG_DOWN_AT = 6.7f
private const val RELEASE_DOWN_AT = 7.6f
private const val CLEAR_AT = 8.3f
/** Seconds after the last touch before the loop starts again from rest. */
private const val USER_HOLD = 3f

private val Ink = Color(0xFF111113)
private val Mute = Color(0xFF6B6B70)
private val Faint = Color(0xFF9A9AA0)
private val Line = Color(0xFFEBEBEE)
private val PillLine = Color(0xFFE2E2E6)
private val IconWell = Color(0xFFF3F3F5)
private val Bolt = Color(0xFFF5A623)
private val Green = Color(0xFF1FA855)
private val GreenWell = Color(0xFFE8F7EE)
private val Teal = Color(0xFF0FB5A4)
private val TealWell = Color(0xFFE3F7F4)
private val Grabber = Color(0xFFD6D6DA)
private val Tip = Color(0xFFFFF8E8)
private val TipWell = Color(0xFFFFEEC2)

private class Account(val name: String, val digits: String, val letter: String, val light: Color, val dark: Color, val primary: Boolean = false)

private val Accounts = listOf(
    Account("Northgate Bank", "4793", "N", Color(0xFF2C5CC5), Color(0xFF1B3A8A), primary = true),
    Account("Harbor Credit Union", "2208", "H", Color(0xFFC2410C), Color(0xFF8A2C08)),
)
private val Recent = listOf(
    Triple("From Northgate Bank", "Today, 9:41 AM", "+$250.00"),
    Triple("From debit card", "Mon, 6:12 PM", "+$80.00"),
)

private const val SHEET_RADIUS = 28f
private const val PAGE_RADIUS = 24f
private const val GRABBER_WIDTH = 36f
private const val GRABBER_HEIGHT = 4f
private const val MARGIN = 20f
// From the sheet's top edge to the first row.
private const val HEADER = 128f
private const val ROW_MEDIUM = 68f
private const val ROW_LARGE = 76f
private const val ROW_GAP = 10f
private const val ROW_RADIUS = 18f
// Grabber, title and caption take 108 dp; the first row's own gap and this one reach HEADER.
private const val ROWS_TOP = HEADER - 108f - ROW_GAP
private const val ICON = 40f
private const val BUTTON_HEIGHT = 52f
private const val FINGER = 40f

// Where the sheet's top edge rests, as a share of the screen's height; the page's scale there.
private const val MEDIUM_AT = 0.32f
private const val LARGE_AT = 0.14f
private const val SCALE_MEDIUM = 0.94f
private const val SCALE_LARGE = 0.92f
private const val SCRIM = 0.36f
// The sheet follows a finger 1:1 until the large detent, and a quarter of the way beyond it.
private const val RUBBER = 0.25f
// Dp a second above which a release picks the next detent in its direction; how far ahead the
// release looks otherwise.
private const val FLING = 600f
private const val PROJECT = 0.12f
private const val NEAR = 24f
// Response 0.5 s with damping ratios 0.8 and 0.7: the large detent overshoots a little more.
// Damping ratio is Reanimated's damping over 2 * sqrt(stiffness * mass).
private val MediumSpring = spring<Float>(dampingRatio = 20f / (2f * sqrt(158f)), stiffness = 158f)
private val LargeSpring = spring<Float>(dampingRatio = 17.6f / (2f * sqrt(158f)), stiffness = 158f)
private val CloseSpring = spring<Float>(dampingRatio = 28f / (2f * sqrt(190f)), stiffness = 190f)
private const val FADE = 180
private const val ENTER_AFTER = 650L
private const val ENTER_IN = 450f
private const val ROW_STAGGER = 70f
private const val ROW_ROOM = 24f
private const val DRAG_UP = 150f
private const val DRAG_UP_MS = 700
private const val DRAG_DOWN_MS = 900
private const val FINGER_IN = 150
private const val FINGER_OUT = 220
private const val REST_FADE = 350

private val CubicOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val QuadIn = CubicBezierEasing(0.11f, 0f, 0.5f, 0f)
private val QuadInOut = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private fun timed(ms: Int, easing: Easing = CubicOut) = tween<Float>(ms, easing = easing)

/** Piecewise-linear map of [x] through [xs] (ascending or descending) to [ys], clamped at the ends. */
private fun interpolate(x: Float, xs: FloatArray, ys: FloatArray): Float {
    val ascending = xs.first() < xs.last()
    val n = xs.size
    fun xAt(i: Int) = if (ascending) xs[i] else xs[n - 1 - i]
    fun yAt(i: Int) = if (ascending) ys[i] else ys[n - 1 - i]
    if (x <= xAt(0)) return yAt(0)
    for (i in 1 until n) if (x <= xAt(i)) return lerp(yAt(i - 1), yAt(i), (x - xAt(i - 1)) / (xAt(i) - xAt(i - 1)))
    return yAt(n - 1)
}

/** What the handlers need to know about the screen. Written while composing, so not state. */
private class Place {
    var width = 0f
    var bottomInset = 0f
    var height = 0f
    var medium = 0f
    var large = 0f
}

/**
 * A page with a button that raises a sheet of transfer methods. The sheet rests at a middle
 * detent and a large one and follows a finger, letting go on a fling or on the nearest detent;
 * the page behind scales and darkens with the sheet's position, and at the large detent the rows
 * grow and a fifth one appears. One value, the sheet's top edge, drives all of it.
 *
 * @param methods The methods of the sheet; the last one only shows at the large detent.
 * @param autoPlay Opens and closes the sheet, pulls it to the large detent and picks a method by itself.
 * @param onSelect Called with the index of the method that was tapped.
 * @param onDetentChange Called when the sheet is sent to a detent: 0 closed, 1 medium, 2 large.
 */
@Composable
fun DetentMethodSheet(
    methods: List<Method> = SampleMethods,
    pageTitle: String = "Select your source of fund",
    pageCaption: String = "Pick a method to add money to your account",
    buttonLabel: String = "Add money",
    sheetTitle: String = "Select transfer method",
    sheetCaption: String = "Select your preferred transfer method to add your money into your account",
    autoPlay: Boolean = false,
    onSelect: ((Int) -> Unit)? = null,
    onDetentChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion by rememberUpdatedState(com.ngocdevv.nativecook.shared.rememberReducedMotion())
    val scope = rememberCoroutineScope()
    // The loop and the gestures outlive a recomposition: they read what the caller passes through these.
    val count by rememberUpdatedState(methods.size)
    val chosen by rememberUpdatedState(onSelect)
    val moved by rememberUpdatedState(onDetentChange)
    val place = remember { Place() }

    var detent by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var pressedRow by remember { mutableStateOf<Int?>(null) }
    var buttonPressed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    // The sheet's top edge, in dp from the top of the screen; the screen's height is closed.
    val top = remember { Animatable(100000f) }
    val enter = remember { Animatable(0f) }
    // The simulated finger of the autoPlay loop.
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var fingerFollows by remember { mutableStateOf(false) }
    val fingerOn = remember { Animatable(0f) }
    val fingerDown = remember { Animatable(0f) }
    val animations = remember { HashMap<Animatable<Float, *>, Job>() }

    DetentMethodSheetBars(light = detent == 0)

    // The title and every row rise in turn over this long.
    fun enterMs() = ENTER_IN + ROW_STAGGER * count

    /** Animates [value] after [after] ms; starting another animation of it cancels this one. */
    fun play(value: Animatable<Float, *>, to: Float, spec: AnimationSpec<Float>, after: Long = 0, velocity: Float? = null) {
        animations[value]?.cancel()
        animations[value] = scope.launch {
            if (after > 0) delay(after)
            if (velocity != null) value.animateTo(to, spec, velocity) else value.animateTo(to, spec)
        }
    }

    fun set(value: Animatable<Float, *>, to: Float) {
        animations[value]?.cancel()
        animations[value] = scope.launch { value.snapTo(to) }
    }

    /** Springs to a detent (0 closed, 1 medium, 2 large), carrying the finger's speed in. */
    fun go(target: Int, velocity: Float) {
        if (target != detent) moved?.invoke(target)
        detent = target
        val to = when (target) { 2 -> place.large; 1 -> place.medium; else -> place.height }
        if (reducedMotion) {
            play(top, to, timed(FADE))
        } else {
            play(top, to, when (target) { 2 -> LargeSpring; 1 -> MediumSpring; else -> CloseSpring }, velocity = velocity)
        }
        // The rows rise again the next time the sheet opens.
        if (target == 0) play(enter, 0f, timed(FADE), 250)
    }

    /** Lets go of the sheet: a fling picks the next detent that way, a slow release the nearest. */
    fun settle(velocity: Float) {
        val y = top.value
        val target = if (abs(velocity) > FLING) {
            if (velocity < 0) (if (y > place.medium + NEAR) 1 else 2) else (if (y < place.medium - NEAR) 1 else 0)
        } else {
            val ahead = y + velocity * PROJECT
            val toLarge = abs(ahead - place.large)
            val toMedium = abs(ahead - place.medium)
            val toClosed = abs(ahead - place.height)
            if (toLarge < toMedium && toLarge < toClosed) 2 else if (toMedium < toClosed) 1 else 0
        }
        go(target, velocity)
    }

    fun open() {
        if (detent != 0) return
        set(enter, 0f)
        set(top, place.height)
        go(1, 0f)
        play(enter, 1f, tween(if (reducedMotion) FADE else enterMs().roundToInt(), easing = LinearEasing), if (reducedMotion) 0 else ENTER_AFTER)
    }

    fun close() = go(0, 0f)

    fun choose(index: Int) {
        selected = index
        chosen?.invoke(index)
    }

    fun touchAt(x: Float, y: Float, follows: Boolean = false) {
        fingerX = x
        fingerY = y
        fingerFollows = follows
        play(fingerOn, 1f, timed(FINGER_IN))
    }

    fun press() = play(fingerDown, 1f, timed(100))

    fun lift() {
        play(fingerDown, 0f, timed(120))
        play(fingerOn, 0f, timed(FINGER_OUT))
    }

    fun rest() {
        detent = 0
        play(top, place.height, timed(REST_FADE))
        play(enter, 0f, timed(FADE))
        play(fingerOn, 0f, timed(FADE))
        set(fingerDown, 0f)
        selected = null
        pressedRow = null
        buttonPressed = false
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
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
            until(TOUCH_BUTTON_AT)
            touchAt(place.width / 2f, place.height - place.bottomInset - 16f - BUTTON_HEIGHT / 2f)
            until(PRESS_BUTTON_AT)
            buttonPressed = true
            press()
            until(OPEN_AT)
            buttonPressed = false
            lift()
            open()
            until(TOUCH_GRABBER_AT)
            touchAt(place.width / 2f, place.medium + 14f, follows = true)
            until(DRAG_UP_AT)
            press()
            play(top, place.medium - DRAG_UP, timed(DRAG_UP_MS, QuadInOut))
            until(RELEASE_UP_AT)
            settle(-700f)
            lift()
            until(TOUCH_ROW_AT)
            touchAt(place.width * 0.72f, place.large + HEADER + 1 * (ROW_LARGE + ROW_GAP) + ROW_LARGE / 2f)
            until(PRESS_ROW_AT)
            pressedRow = 1
            press()
            until(SELECT_AT)
            pressedRow = null
            choose(1)
            lift()
            go(1, 0f)
            until(TOUCH_GRABBER_AGAIN_AT)
            touchAt(place.width / 2f, place.medium + 14f, follows = true)
            until(DRAG_DOWN_AT)
            press()
            play(top, place.height, timed(DRAG_DOWN_MS, QuadIn))
            until(RELEASE_DOWN_AT)
            settle(900f)
            lift()
            until(CLEAR_AT)
            selected = null
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
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
        val topInset = insets.calculateTopPadding().value
        val bottom = max(insets.calculateBottomPadding().value, 16f)
        val medium = (height * MEDIUM_AT).roundToInt().toFloat()
        val large = max((height * LARGE_AT).roundToInt().toFloat(), topInset + 8f)
        place.width = width
        place.height = height
        place.medium = medium
        place.large = large
        place.bottomInset = insets.calculateBottomPadding().value
        val density = LocalDensity.current.density
        // Puts the sheet away on the first pass.
        LaunchedEffect(height) { if (top.value > height) top.snapTo(height) }

        // The page, button included: scaled and rounded as the sheet rises.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val y = top.value
                    val s = interpolate(y, floatArrayOf(large, medium, height), floatArrayOf(SCALE_LARGE, SCALE_MEDIUM, 1f))
                    scaleX = s
                    scaleY = s
                    val r = interpolate(y, floatArrayOf(medium, height), floatArrayOf(PAGE_RADIUS, 0f)) * this.density
                    shape = RoundedCornerShape(topStart = CornerSize(r), topEnd = CornerSize(r), bottomEnd = CornerSize(0f), bottomStart = CornerSize(0f))
                    clip = true
                }
                .background(Color.White),
        ) {
            Column(Modifier.fillMaxSize().padding(start = MARGIN.dp, end = MARGIN.dp, top = (topInset + 8f).dp)) {
                Row(
                    Modifier.padding(top = 4.dp).offset(x = (-6).dp).fillMaxWidth().height(44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, null, Modifier.padding(start = 6.dp).size(22.dp), tint = Ink)
                    Column(Modifier.offset(x = 6.dp), horizontalAlignment = Alignment.End) {
                        Label("Balance", 12f, 16f, FontWeight.Medium, Faint)
                        Label("$1,284.50", 16f, 20f, FontWeight.Bold, Ink, features = "tnum")
                    }
                }
                Label(pageTitle, 28f, 34f, FontWeight.Bold, Ink, Modifier.padding(top = 16.dp), letterSpacing = -0.7f)
                Label(pageCaption, 16f, 22f, FontWeight.Normal, Mute, Modifier.padding(top = 6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Section("Bank accounts")
                    Row(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, PillLine), RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClick = ::open)
                            .padding(start = 14.dp, end = 10.dp)
                            .semantics { contentDescription = "Add a bank account" },
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label("Add", 14f, 18f, FontWeight.SemiBold, Ink)
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = Ink)
                    }
                }
                for (account in Accounts) {
                    Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(account.dark)
                                .drawBehind { drawCircle(account.light, 22.dp.toPx(), Offset(16.dp.toPx(), 12.dp.toPx())) },
                            contentAlignment = Alignment.Center,
                        ) { Label(account.letter, 18f, 22f, FontWeight.ExtraBold, Color.White) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RowTitle(account.name)
                                if (account.primary) {
                                    Box(Modifier.height(20.dp).background(GreenWell, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                                        Label("Primary", 11f, 14f, FontWeight.Bold, Green)
                                    }
                                }
                            }
                            Label("Checking  ••••  ${account.digits}", 14f, 19f, FontWeight.Normal, Faint, features = "tnum")
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(26.dp), tint = Faint)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Section("Recent top-ups")
                    Label("See all", 15f, 20f, FontWeight.Medium, Mute)
                }
                for ((title, whenText, amount) in Recent) {
                    Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(GreenWell, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ArrowDownward, null, Modifier.size(18.dp), tint = Green)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RowTitle(title)
                            Label(whenText, 14f, 19f, FontWeight.Normal, Faint, features = "tnum")
                        }
                        Label(amount, 16f, 21f, FontWeight.Bold, Green, features = "tnum")
                    }
                }
                Row(
                    Modifier.padding(top = 20.dp).fillMaxWidth().background(Tip, RoundedCornerShape(18.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(TipWell, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Bolt, null, Modifier.size(20.dp), tint = Bolt)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label("Instant top-ups are on", 15f, 20f, FontWeight.SemiBold, Ink)
                        Label("Free up to $500 a month with a debit card.", 14f, 19f, FontWeight.Normal, Mute)
                    }
                }
            }
            val buttonSource = remember { MutableInteractionSource() }
            val buttonGlow = remember { SoftRoundRect(Ink.copy(alpha = 0.22f), 10f) }
            val buttonDown by buttonSource.collectIsPressedAsState()
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = MARGIN.dp, end = MARGIN.dp)
                    .padding(bottom = bottom.dp)
                    .fillMaxWidth()
                    .height(BUTTON_HEIGHT.dp)
                    .drawBehind {
                        drawIntoCanvas { buttonGlow.draw(it.nativeCanvas, 0f, 8.dp.toPx(), size.width, size.height, size.height / 2f) }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(if (buttonDown || buttonPressed) Color(0xFF333333) else Ink)
                    .clickable(buttonSource, null, enabled = detent == 0, role = Role.Button, onClick = ::open),
                contentAlignment = Alignment.Center,
            ) { Label(buttonLabel, 17f, 22f, FontWeight.SemiBold, Color.White) }
        }

        // The scrim, which closes the sheet when it is touched.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = interpolate(top.value, floatArrayOf(medium, height), floatArrayOf(SCRIM, 0f)) }
                .background(Color.Black)
                .semantics { contentDescription = "Close" }
                .clickable(enabled = detent != 0, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = ::close),
        )

        // The sheet's edge is where `top` is; the box hangs 200 dp below the screen for overshoot.
        val sheetShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.14f), 16f) }
        Column(
            Modifier
                .width(width.dp)
                .height((height - large + 200f).dp)
                .graphicsLayer { translationY = top.value * this.density }
                .drawBehind {
                    val r = SHEET_RADIUS.dp.toPx()
                    drawIntoCanvas { sheetShadow.draw(it.nativeCanvas, 0f, -8.dp.toPx(), size.width, size.height, r) }
                }
                .clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    var tracker = VelocityTracker()
                    var translation = 0f
                    var start = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker = VelocityTracker()
                            translation = 0f
                            // Takes hold of the sheet where it is.
                            start = when (detent) { 2 -> place.large; 1 -> place.medium; else -> place.height }
                            animations[top]?.cancel()
                        },
                        onDragEnd = { settle(tracker.calculateVelocity().y / density) },
                        onDragCancel = { settle(0f) },
                    ) { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        translation += amount / density
                        val next = start + translation
                        set(top, if (next < place.large) place.large - (place.large - next) * RUBBER else next)
                    }
                }
                .padding(horizontal = MARGIN.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(GRABBER_WIDTH.dp, GRABBER_HEIGHT.dp)
                    .background(Grabber, RoundedCornerShape(50)),
            )
            Column(
                Modifier.graphicsLayer {
                    val t = min(1f, enter.value * enterMs() / ENTER_IN)
                    alpha = t
                    translationY = 8f * (1f - t) * this.density
                },
            ) {
                Label(sheetTitle, 21f, 26f, FontWeight.Bold, Ink, Modifier.padding(top = 22.dp), letterSpacing = -0.4f)
                Label(sheetCaption, 15f, 20f, FontWeight.Normal, Mute, Modifier.padding(top = 4.dp).height(40.dp), maxLines = 2)
            }
            Column(Modifier.padding(top = ROWS_TOP.dp)) {
                methods.forEachIndexed { index, method ->
                    MethodRow(
                        method, index, index == methods.size - 1, top, enter, medium, large, enterMs(),
                        selected == index, pressedRow == index, enabled = detent != 0 && (index < methods.size - 1 || detent == 2),
                    ) { choose(index) }
                }
            }
        }

        // The simulated finger.
        Box(
            Modifier
                .size(FINGER.dp)
                .graphicsLayer {
                    alpha = fingerOn.value
                    translationX = (fingerX - FINGER / 2f) * this.density
                    translationY = ((if (fingerFollows) top.value + 14f else fingerY) - FINGER / 2f) * this.density
                    val s = 1f - 0.14f * fingerDown.value
                    scaleX = s
                    scaleY = s
                }
                .shadow(5.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun MethodRow(
    method: Method,
    index: Int,
    last: Boolean,
    top: Animatable<Float, *>,
    enter: Animatable<Float, *>,
    medium: Float,
    large: Float,
    enterMs: Float,
    selected: Boolean,
    held: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed || held) 0.97f else 1f, tween(120, easing = CubicOut), label = "scale")
    val border by animateColorAsState(if (selected) Ink else Line, tween(150, easing = CubicOut), label = "border")
    val partner = method.icon == Icons.Filled.Diamond
    val well by animateColorAsState(if (selected) Ink else if (partner) TealWell else IconWell, tween(150, easing = CubicOut), label = "well")
    val far = remember { SoftRoundRect(Ink.copy(alpha = 0.05f), 8f) }
    val near = remember { SoftRoundRect(Ink.copy(alpha = 0.04f), 1f) }
    // 0 at the middle detent, 1 at the large one.
    fun grow() = interpolate(top.value, floatArrayOf(large, medium), floatArrayOf(1f, 0f))
    // The last row exists only at the large detent.
    fun extra() = if (last) min(1f, max(0f, (grow() - 0.5f) / 0.5f)) else 1f
    Box(
        Modifier
            .layout { measurable, constraints ->
                val e = extra()
                val h = ((ROW_MEDIUM + (ROW_LARGE - ROW_MEDIUM) * grow()) * e * density).roundToInt()
                val gap = (ROW_GAP * e * density).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                layout(constraints.maxWidth, h + gap) { placeable.place(0, gap) }
            }
            .graphicsLayer {
                val t = min(1f, max(0f, (enter.value * enterMs - (index + 1) * ROW_STAGGER) / ENTER_IN))
                alpha = t * extra()
                translationY = (1f - t) * ROW_ROOM * this.density
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    val r = ROW_RADIUS.dp.toPx()
                    drawIntoCanvas {
                        far.draw(it.nativeCanvas, 0f, 6.dp.toPx(), size.width, size.height, r)
                        near.draw(it.nativeCanvas, 0f, 1.dp.toPx(), size.width, size.height, r)
                    }
                }
                .background(Color.White, RoundedCornerShape(ROW_RADIUS.dp))
                .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), RoundedCornerShape(ROW_RADIUS.dp))
                .clip(RoundedCornerShape(ROW_RADIUS.dp))
                .semantics { this.selected = selected }
                .clickable(source, null, enabled = enabled && extra() > 0f, role = Role.Button, onClick = onPress)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ICON.dp).background(well, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    if (selected) Icons.Filled.Check else method.icon, null, Modifier.size(20.dp),
                    tint = if (selected) Color.White else if (partner) Teal else Ink,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(method.title, 16f, 21f, FontWeight.SemiBold, Ink, maxLines = 1, letterSpacing = -0.2f)
                Row(
                    Modifier.graphicsLayer { alpha = 0.72f + 0.28f * grow() },
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (method.instant) Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp), tint = Bolt)
                    Label(method.subtitle, 14f, 19f, FontWeight.Normal, Mute, maxLines = 1)
                }
            }
            Row(
                Modifier
                    .height(30.dp)
                    .background(Color.White, RoundedCornerShape(15.dp))
                    .border(BorderStroke(1.dp, PillLine), RoundedCornerShape(15.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(method.badge, 13f, 17f, FontWeight.SemiBold, Ink)
                if (method.enabled) Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = Green)
            }
        }
    }
}

@Composable
private fun Section(text: String) = Label(text, 17f, 22f, FontWeight.SemiBold, Ink, letterSpacing = -0.2f)

@Composable
private fun RowTitle(text: String) = Label(text, 16f, 21f, FontWeight.SemiBold, Ink, maxLines = 1, letterSpacing = -0.2f)

@Composable
private fun Label(
    text: String,
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
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
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

/** The status bar icons are dark over the white page and light once the sheet dims it. */
@Composable
private fun DetentMethodSheetBars(light: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, light) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = light
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
