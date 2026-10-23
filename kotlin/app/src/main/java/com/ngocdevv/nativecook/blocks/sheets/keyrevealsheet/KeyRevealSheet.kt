package com.ngocdevv.nativecook.blocks.sheets.keyrevealsheet

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SampleWords = listOf("river", "amber", "falcon", "lunar", "maple", "quartz", "harbor", "velvet", "cactus", "ember", "orbit", "willow")
private val SampleBody = listOf("Your Private Key is the key used to", "back up your wallet. Keep it secret", "and secure at all times.")
private val SampleNotes = listOf("Keep your Private Key safe", "Don't share it with anyone else", "If you lose it, we can't recover it")
private val NoteIcons = listOf(Icons.Outlined.VerifiedUser, Icons.Outlined.Description, Icons.Outlined.Block)

/** Seconds for one pass of the autoPlay loop. */
const val LOOP = 9f

// The timeline, in seconds from the start of a loop.
private const val PRESS_ROW_AT = 0.3f
private const val OPEN_AT = 0.5f
private const val PRESS_REVEAL_AT = 2.2f
private const val REVEAL_AT = 2.5f
private const val COPY_AT = 5.2f
private const val HIDE_AT = 6.5f
private const val CLOSE_AT = 7.5f
/** Seconds after the last touch before the loop starts again from rest. */
private const val USER_HOLD = 3f

private val Ink = Color(0xFF16181B)
private val Mute = Color(0xFF7D8187)
private val NoteColor = Color(0xFF6B7076)
private val Well = Color(0xFFF4F6F8)
private val Line = Color(0xFFE9ECEF)
private val ItemLine = Color(0xFFDDE1E5)
private val Chevron = Color(0xFFB4B8BD)
private val Cancel = Color(0xFFF1F3F6)
private val Cyan = Color(0xFF14B8FF)
private val CyanPressed = Color(0xFF0A9FE0)
private val Green = Color(0xFF22B07D)
private val ToastFill = Color(0xFFE6F7FF)
private val ToastInk = Color(0xFF0A7FB8)
private val Amber = arrayOf(0f to Color(0xFFF6C04A), 0.55f to Color(0xFFEAA31C), 1f to Color(0xFFD8870A))
private val Dot = Color(0xFFE5334B)

private class Setting(val icon: ImageVector, val tint: Color, val title: String, val caption: String)

private val Settings = listOf(
    Setting(Icons.Outlined.Visibility, Color(0xFF14B8FF), "Private Key", "Back up your wallet"),
    Setting(Icons.Outlined.Public, Color(0xFF7C5CFF), "Network", "Ethereum Mainnet"),
    Setting(Icons.Outlined.Apps, Color(0xFFFF8A3D), "Connected apps", "3 apps can see this wallet"),
)

private class Activity(val title: String, val caption: String, val amount: String, val received: Boolean)

private val Activities = listOf(
    Activity("Received", "From 0x8f3…a21 · 2h ago", "+0.01 ETH", true),
    Activity("Sent", "To mira.eth · Yesterday", "−0.005 ETH", false),
)

private const val MARGIN = 20f
private const val PAD = 22f
private const val CARD_HEIGHT = 208f
private const val CARD_RADIUS = 24f
private const val SHEET_RADIUS = 32f
private const val SHEET_BOTTOM = 16f
// The sheet's height with the warning, and with the key.
private const val FRONT = 372f
private const val BACK = 318f
private const val BUTTON_HEIGHT = 50f
private const val CANCEL_WIDTH = 128f
private const val BOX_PAD = 12f
private const val CHIP_HEIGHT = 36f
private const val CHIP_GAP = 8f
private const val BOX_RADIUS = 14f
private const val BOX_HEIGHT = BOX_PAD * 2 + CHIP_HEIGHT * 4 + CHIP_GAP * 3
private const val COLUMNS = 3
private const val GLOW_WIDTH = 2f
private const val HAIRLINE = 1f / 3f
/** The bright edge that leads the unblur across the key. */
private const val EDGE = 36f

// Response 0.5 s, damping ratio 0.82. Damping ratio is Reanimated's damping over 2 * sqrt(stiffness * mass).
private val Spring = spring<Float>(dampingRatio = 20.7f / (2f * sqrt(158f)), stiffness = 158f)
private const val FADE = 180
private const val SCAN = 500
private const val SWITCH_AFTER = 560L
private const val SWITCH = 320
private const val UNBLUR_AFTER = 480L
private const val UNBLUR = 800
private const val REBLUR = 350
private const val GLOW = 950
private const val TOAST_FOR = 1400L
private const val INTRO_AFTER = 700L
private const val INTRO = 1000f
private const val LINE_STAGGER = 80f
private const val NOTE_STAGGER = 100f
private const val NOTE_START = 300f
private const val NOTE_IN = 300f
private const val NOTE_SLIDE = 12f
private const val CLOSE_MS = 450
private const val REST_FADE = 350
private const val SCRIM = 0.32f

private val CubicOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val CubicIn = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
private val QuadInOut = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private fun timed(ms: Int, easing: Easing = CubicOut) = tween<Float>(ms, easing = easing)

private enum class Pressed { None, Row, Reveal }

/** The part of the box right of a share of its width: what is still blurred. */
private class RightOf(private val share: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(size.width * share(), 0f, size.width, size.height))
}

/**
 * A wallet page and a floating sheet that warns about the private key. Reveal scans a face,
 * swaps the warning for the key, which unblurs from left to right while a light runs once around
 * its box; tapping the key copies it. Nothing real is kept here: [words] is whatever you pass.
 * The blur needs Android 12; below that a pale veil covers the key instead.
 *
 * @param words The words of the key, shown in a grid of three columns.
 * @param body The body in three lines, which rise one after the other.
 * @param notes The three reminders under the body.
 * @param autoPlay Opens the sheet, reveals the key, copies it and closes again by itself every [LOOP] seconds.
 * @param onReveal Called when Reveal is pressed.
 * @param onCopy Called with the key's words when the key is tapped to copy.
 */
@Composable
fun KeyRevealSheet(
    words: List<String> = SampleWords,
    walletName: String = "BFF",
    balance: String = "0.02 ETH",
    title: String = "Private Key",
    body: List<String> = SampleBody,
    notes: List<String> = SampleNotes,
    cancelLabel: String = "Cancel",
    revealLabel: String = "Reveal",
    copiedLabel: String = "Copied",
    autoPlay: Boolean = false,
    onReveal: (() -> Unit)? = null,
    onCopy: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    KeyRevealSheetLightBars()
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val key by rememberUpdatedState(words)
    val revealedCallback by rememberUpdatedState(onReveal)
    val copiedCallback by rememberUpdatedState(onCopy)

    var open by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(Pressed.None) }
    var closing by remember { mutableStateOf<Job?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    val sheet = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val intro = remember { Animatable(0f) }
    val mode = remember { Animatable(0f) }
    val scan = remember { Animatable(1f) }
    val keys = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val toast = remember { Animatable(0f) }
    val tap = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val animations = remember { HashMap<Animatable<Float, *>, Job>() }

    /** Animates [value] after [after] ms, then calls [then]; starting another animation of it cancels this one. */
    fun play(value: Animatable<Float, *>, to: Float, spec: AnimationSpec<Float>, after: Long = 0, then: (suspend () -> Unit)? = null) {
        animations[value]?.cancel()
        animations[value] = scope.launch {
            if (after > 0) delay(after)
            value.animateTo(to, spec)
            then?.invoke()
        }
    }

    fun set(value: Animatable<Float, *>, to: Float) {
        animations[value]?.cancel()
        animations[value] = scope.launch { value.snapTo(to) }
    }

    fun move(value: Animatable<Float, *>, to: Float, after: Long = 0) =
        play(value, to, if (reducedMotion) timed(FADE) else Spring, after)

    fun show() {
        if (open) return
        closing?.cancel()
        open = true
        pressed = Pressed.None
        for (value in listOf(intro, mode, keys, glow, toast, tap, stretch)) set(value, 0f)
        set(scan, 1f)
        move(sheet, 1f)
        play(intro, 1f, tween(if (reducedMotion) FADE else INTRO.roundToInt(), easing = LinearEasing), if (reducedMotion) 0 else INTRO_AFTER)
        play(pulse, 1.3f, timed(180), INTRO_AFTER + NOTE_START.toLong()) { pulse.animateTo(1f, timed(260)) }
    }

    fun reveal(auto: Boolean) {
        if (revealed) return
        pressed = Pressed.None
        revealed = true
        if (!auto) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        revealedCallback?.invoke()
        val wait = if (reducedMotion) 0L else SWITCH_AFTER
        animations[scan]?.cancel()
        animations[scan] = scope.launch {
            scan.snapTo(0f)
            scan.animateTo(1f, tween(if (reducedMotion) 0 else SCAN, easing = LinearEasing))
        }
        play(mode, 1f, timed(if (reducedMotion) FADE else SWITCH), wait)
        move(stretch, 1f, wait)
        val unblurAt = wait + if (reducedMotion) 0 else UNBLUR_AFTER
        play(keys, 1f, tween(if (reducedMotion) FADE else UNBLUR, easing = LinearEasing), unblurAt)
        play(glow, if (reducedMotion) 0f else 1f, timed(GLOW, QuadInOut), unblurAt)
    }

    fun copy(auto: Boolean) {
        if (!revealed) return
        if (!auto) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
        copiedCallback?.invoke(key)
        play(tap, 1f, timed(90)) { tap.animateTo(0f, timed(180)) }
        val rise = if (reducedMotion) FADE else 260
        play(toast, 1f, timed(rise)) {
            delay(TOAST_FOR)
            toast.animateTo(0f, timed(FADE))
        }
    }

    fun hide() {
        if (!revealed) return
        revealed = false
        play(keys, 0f, timed(if (reducedMotion) FADE else REBLUR))
        set(glow, 0f)
        val wait = if (reducedMotion) 0L else REBLUR.toLong()
        play(mode, 0f, timed(if (reducedMotion) FADE else SWITCH), wait)
        move(stretch, 0f, wait)
    }

    fun dismiss(ms: Int = if (reducedMotion) FADE else CLOSE_MS) {
        open = false
        pressed = Pressed.None
        play(sheet, 0f, timed(ms, CubicIn)) {
            // Out of sight: the next opening starts from the warning again.
            if (!open) for (value in listOf(mode, stretch, keys, intro)) value.snapTo(0f)
        }
        closing?.cancel()
        closing = scope.launch {
            delay(ms.toLong())
            revealed = false
        }
    }

    fun rest() {
        closing?.cancel()
        open = false
        revealed = false
        pressed = Pressed.None
        for (value in listOf(sheet, stretch, intro, mode, keys, glow, toast, tap)) play(value, 0f, timed(REST_FADE))
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
            until(PRESS_ROW_AT)
            pressed = Pressed.Row
            until(OPEN_AT)
            show()
            until(PRESS_REVEAL_AT)
            pressed = Pressed.Reveal
            until(REVEAL_AT)
            reveal(auto = true)
            until(COPY_AT)
            copy(auto = true)
            until(HIDE_AT)
            hide()
            until(CLOSE_AT)
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
        val top = insets.calculateTopPadding().value
        val bottomInset = insets.calculateBottomPadding().value
        val sheetWidth = maxWidth.value - MARGIN * 2
        val boxWidth = sheetWidth - PAD * 2
        val chipWidth = (boxWidth - BOX_PAD * 2 - CHIP_GAP * (COLUMNS - 1)) / COLUMNS
        val around = 2 * (boxWidth - 2 * BOX_RADIUS) + 2 * (BOX_HEIGHT - 2 * BOX_RADIUS) + 2 * PI.toFloat() * BOX_RADIUS

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height((top + 52f).dp).padding(horizontal = 24.dp).padding(top = (top + 8f).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Filled.Close, null, Modifier.size(24.dp), tint = Ink)
                Box {
                    Icon(Icons.Outlined.CardGiftcard, null, Modifier.size(24.dp), tint = Ink)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(3.dp, (-3).dp)
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                            .background(Dot, CircleShape),
                    )
                }
            }

            val cardFar = remember { SoftRoundRect(rgba(200, 120, 0, 0.32f), 16f) }
            val cardNear = remember { SoftRoundRect(rgba(120, 70, 0, 0.18f), 3f) }
            Box(
                Modifier
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    .height(CARD_HEIGHT.dp)
                    .graphicsLayer {
                        val p = sheet.value
                        translationY = -8f * p * this.density
                        scaleX = 1f - 0.04f * p
                        scaleY = scaleX
                    }
                    .drawBehind {
                        val r = CARD_RADIUS.dp.toPx()
                        drawIntoCanvas {
                            cardFar.draw(it.nativeCanvas, 0f, 16.dp.toPx(), size.width, size.height, r)
                            cardNear.draw(it.nativeCanvas, 0f, 2.dp.toPx(), size.width, size.height, r)
                        }
                    }
                    .clip(RoundedCornerShape(CARD_RADIUS.dp))
                    .drawBehind {
                        drawRect(Brush.linearGradient(*Amber, start = Offset.Zero, end = Offset(size.width, size.height)))
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
                                start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 0.7f),
                            ),
                        )
                    },
            ) {
                // A large faint heart in the corner, the card's own pattern.
                Icon(
                    Icons.Filled.Favorite, null,
                    Modifier.align(Alignment.BottomEnd).offset(40.dp, 56.dp).size(210.dp).graphicsLayer { rotationZ = -14f },
                    tint = Color.White.copy(alpha = 0.10f),
                )
                Box(
                    Modifier.padding(start = 18.dp, top = 18.dp).size(40.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Favorite, null, Modifier.size(22.dp), tint = Color.White) }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("Copy Address", 15f, 20f, FontWeight.SemiBold, Color.White.copy(alpha = 0.92f))
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.9f))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp)) {
                    Label(walletName, 22f, 28f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.3f)
                    Label(balance, 15f, 20f, FontWeight.SemiBold, Color.White.copy(alpha = 0.78f), features = "tnum")
                }
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 20.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Palette, null, Modifier.size(14.dp), tint = Color.White)
                    Label("Customize", 13f, 17f, FontWeight.Bold, Color.White)
                }
                Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(CARD_RADIUS.dp)))
            }

            Section("Wallet settings")
            ListCard {
                Settings.forEachIndexed { index, item ->
                    if (index == 0) {
                        val source = remember { MutableInteractionSource() }
                        val touching by source.collectIsPressedAsState()
                        Item(
                            item.icon, item.tint, title, item.caption, line = false,
                            modifier = Modifier
                                .background(if (touching || pressed == Pressed.Row) Color(0xFFE8EBEF) else Color.Transparent)
                                .clickable(source, null, enabled = !open, role = Role.Button, onClick = ::show),
                        ) { Icon(Icons.Filled.ChevronRight, null, Modifier.size(26.dp), tint = Chevron) }
                    } else {
                        Item(item.icon, item.tint, item.title, item.caption, line = true) {
                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(26.dp), tint = Chevron)
                        }
                    }
                }
            }
            Section("Recent activity")
            ListCard {
                Activities.forEachIndexed { index, item ->
                    Item(
                        if (item.received) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        if (item.received) Green else NoteColor,
                        item.title, item.caption, line = index > 0,
                    ) { Label(item.amount, 15f, 20f, FontWeight.Bold, if (item.received) Green else Ink, features = "tnum") }
                }
            }
        }

        // The scrim, which closes the sheet when it is touched.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = SCRIM * sheet.value }
                .background(Color.Black)
                .semantics { contentDescription = "Close" }
                .clickable(enabled = open, indication = null, interactionSource = remember { MutableInteractionSource() }) { dismiss() },
        )

        // Sized by `stretch`, and out of sight below the screen until `sheet` brings it up.
        val sheetFar = remember { SoftRoundRect(Color.Black.copy(alpha = 0.22f), 30f) }
        val sheetNear = remember { SoftRoundRect(Color.Black.copy(alpha = 0.08f), 4f) }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = SHEET_BOTTOM.dp)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val h = (lerp(FRONT, BACK, stretch.value) * density).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                    layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                }
                .graphicsLayer { translationY = (1f - sheet.value) * (FRONT + SHEET_BOTTOM + bottomInset + 24f) * this.density }
                .drawBehind {
                    val r = SHEET_RADIUS.dp.toPx()
                    drawIntoCanvas {
                        sheetFar.draw(it.nativeCanvas, 0f, 24.dp.toPx(), size.width, size.height, r)
                        sheetNear.draw(it.nativeCanvas, 0f, 2.dp.toPx(), size.width, size.height, r)
                    }
                }
                .clip(RoundedCornerShape(SHEET_RADIUS.dp))
                .background(Color.White),
        ) {
            // The warning.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - min(1f, mode.value * 2.2f) }.padding(PAD.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        KeyIcon()
                        CloseButton(enabled = open && !revealed) { dismiss() }
                    }
                    Title(title, Modifier.padding(top = 10.dp))
                    Column(Modifier.padding(top = 6.dp)) {
                        body.forEachIndexed { index, line ->
                            Box(
                                Modifier.graphicsLayer {
                                    val t = min(1f, max(0f, (intro.value * INTRO - index * LINE_STAGGER) / NOTE_IN))
                                    alpha = t
                                    translationY = 8f * (1f - t) * this.density
                                },
                            ) { Label(line, 16f, 22f, FontWeight.Normal, Mute) }
                        }
                    }
                    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        notes.forEachIndexed { index, text ->
                            Row(
                                Modifier.graphicsLayer {
                                    val t = min(1f, max(0f, (intro.value * INTRO - (NOTE_START + index * NOTE_STAGGER)) / NOTE_IN))
                                    alpha = t
                                    translationX = -NOTE_SLIDE * (1f - t) * this.density
                                },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    NoteIcons[index % NoteIcons.size], null,
                                    Modifier.size(18.dp).graphicsLayer {
                                        if (index == 0) {
                                            scaleX = pulse.value
                                            scaleY = pulse.value
                                        }
                                    },
                                    tint = NoteColor,
                                )
                                Label(text, 15f, 20f, FontWeight.Normal, NoteColor)
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(BUTTON_HEIGHT.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .width(CANCEL_WIDTH.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(50))
                            .background(Cancel)
                            .clickable(enabled = open && !revealed, role = Role.Button) { dismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Label(cancelLabel, 17f, 22f, FontWeight.SemiBold, Ink) }
                    val source = remember { MutableInteractionSource() }
                    val touching by source.collectIsPressedAsState()
                    val down = touching || pressed == Pressed.Reveal
                    val revealGlow = remember { SoftRoundRect(Cyan.copy(alpha = 0.35f), 9f) }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = if (down) 0.96f else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .drawBehind {
                                drawIntoCanvas { revealGlow.draw(it.nativeCanvas, 0f, 8.dp.toPx(), size.width, size.height, size.height / 2f) }
                            }
                            .clip(RoundedCornerShape(50))
                            .background(if (down) CyanPressed else Cyan)
                            .clickable(source, null, enabled = open && !revealed, role = Role.Button) { reveal(auto = false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FaceScan({ scan.value }, Color.White)
                            Label(revealLabel, 17f, 22f, FontWeight.Bold, Color.White)
                        }
                    }
                }
            }

            // The key.
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val m = mode.value
                        alpha = min(1f, max(0f, m * 2.2f - 1.2f))
                        translationY = 8f * (1f - m) * this.density
                    }
                    .padding(PAD.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Title(title)
                    CloseButton(enabled = open && revealed) { dismiss() }
                }
                Box(
                    Modifier
                        .padding(top = 16.dp)
                        .width(boxWidth.dp)
                        .height(BOX_HEIGHT.dp)
                        .graphicsLayer {
                            val s = 1f - 0.02f * tap.value
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(BOX_RADIUS.dp))
                        .background(Well)
                        .semantics { contentDescription = "Copy the key" }
                        .clickable(enabled = open && revealed, role = Role.Button) { copy(auto = false) },
                ) {
                    val chipShadow = remember { SoftRoundRect(rgba(16, 24, 40, 0.06f), 1f) }
                    val edgeGlow = remember { SoftRoundRect(Cyan.copy(alpha = 0.9f), 4f) }
                    val chips = @Composable {
                        Column(Modifier.padding(BOX_PAD.dp), verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                            words.chunked(COLUMNS).forEachIndexed { row, line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                                    line.forEachIndexed { column, word ->
                                        Row(
                                            Modifier
                                                .size(chipWidth.dp, CHIP_HEIGHT.dp)
                                                .drawBehind {
                                                    drawIntoCanvas { chipShadow.draw(it.nativeCanvas, 0f, 1.dp.toPx(), size.width, size.height, 11.dp.toPx()) }
                                                }
                                                .background(Color.White, RoundedCornerShape(11.dp))
                                                .border(HAIRLINE.dp, Line, RoundedCornerShape(11.dp))
                                                .padding(horizontal = 9.dp),
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Label(
                                                "${row * COLUMNS + column + 1}", 11f, 14f, FontWeight.Bold, Cyan, FontFamily.Monospace,
                                                Modifier.width(14.dp), align = TextAlign.End,
                                            )
                                            Label(word, 14f, 18f, FontWeight.SemiBold, Ink, FontFamily.Monospace, Modifier.weight(1f), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    chips()
                    // The bright edge that leads the unblur across the key.
                    Box(
                        Modifier
                            .width(EDGE.dp)
                            .fillMaxHeight()
                            .graphicsLayer {
                                alpha = sin(PI.toFloat() * keys.value)
                                translationX = (boxWidth * keys.value - EDGE) * this.density
                            }
                            .drawBehind {
                                drawRect(Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0f), Cyan.copy(alpha = 0.22f))))
                                val w = 2.dp.toPx()
                                drawIntoCanvas { edgeGlow.draw(it.nativeCanvas, size.width - w, 0f, w, size.height, 0f) }
                                drawRect(Cyan, Offset(size.width - w, 0f), Size(w, size.height))
                            },
                    )
                    // The blur covers the key from the right and pulls back towards it.
                    Box(Modifier.fillMaxSize().graphicsLayer { clip = true; shape = RightOf { keys.value } }) {
                        val canBlur = Build.VERSION.SDK_INT >= 31
                        Box(Modifier.fillMaxSize().background(Well)) {
                            Box(Modifier.fillMaxSize().then(if (canBlur) Modifier.blur(9.dp) else Modifier)) { chips() }
                        }
                        Box(Modifier.fillMaxSize().background(Well.copy(alpha = if (canBlur) 0.45f else 0.94f)))
                    }
                    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = sin(PI.toFloat() * glow.value) }) {
                        val dash = floatArrayOf(around * 0.25f, around * 0.75f)
                        val phase = (1f - glow.value) * around
                        for ((width, opacity) in listOf(6f to 0.22f, GLOW_WIDTH to 1f)) {
                            val w = width * density
                            drawRoundRect(
                                Cyan.copy(alpha = opacity),
                                Offset(w / 2f, w / 2f),
                                Size(size.width - w, size.height - w),
                                CornerRadius(BOX_RADIUS * density),
                                style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(dash.map { it * density }.toFloatArray(), phase * density)),
                            )
                        }
                    }
                }
                Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.graphicsLayer { alpha = 1f - toast.value }) { Label("Tap the key to copy it", 14f, 18f, FontWeight.Normal, Mute) }
                    Row(
                        Modifier
                            .graphicsLayer {
                                val t = toast.value
                                alpha = t
                                translationY = 8f * (1f - t) * this.density
                            }
                            .height(28.dp)
                            .background(ToastFill, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = Cyan)
                        Label(copiedLabel, 14f, 18f, FontWeight.Bold, ToastInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) = Label(
    text, 13f, 18f, FontWeight.SemiBold, Mute,
    modifier = Modifier.padding(start = (MARGIN + 4f).dp, end = (MARGIN + 4f).dp, top = 24.dp, bottom = 8.dp),
    letterSpacing = 0.2f,
)

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) = Label(text, 24f, 30f, FontWeight.Bold, Ink, modifier = modifier, letterSpacing = -0.5f)

/** A rounded grey card that holds rows separated by hairlines. */
@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = MARGIN.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Well),
    ) { content() }
}

@Composable
private fun Item(
    icon: ImageVector,
    tint: Color,
    title: String,
    caption: String,
    line: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .drawBehind { if (line) drawRect(ItemLine, size = Size(size.width, HAIRLINE.dp.toPx())) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Label(title, 16f, 21f, FontWeight.SemiBold, Ink, letterSpacing = -0.2f)
            Label(caption, 14f, 18f, FontWeight.Normal, Mute)
        }
        trailing()
    }
}

/** A card with two lines of text and an eye over its corner, as the design draws it. */
@Composable
private fun KeyIcon() {
    Canvas(Modifier.size(40.dp, 34.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            val stroke = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(Mute, Offset(1.5f, 1.5f), Size(30f, 22f), CornerRadius(6f), style = stroke)
            drawLine(Mute, Offset(8f, 9.5f), Offset(18f, 9.5f), 2f, StrokeCap.Round)
            drawLine(Mute, Offset(8f, 15f), Offset(14f, 15f), 2f, StrokeCap.Round)
            val eye = Path().apply {
                moveTo(18f, 24.5f)
                cubicTo(20.6f, 20.1f, 23.9f, 17.9f, 27.8f, 17.9f)
                cubicTo(31.7f, 17.9f, 35f, 20.1f, 37.6f, 24.5f)
                cubicTo(35f, 28.9f, 31.7f, 31.1f, 27.8f, 31.1f)
                cubicTo(23.9f, 31.1f, 20.6f, 28.9f, 18f, 24.5f)
                close()
            }
            drawPath(eye, Color.White)
            drawPath(eye, Mute, style = stroke)
            drawCircle(Mute, 2.8f, Offset(27.8f, 24.5f), style = stroke)
        }
    }
}

@Composable
private fun CloseButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Cancel)
            .semantics { contentDescription = "Close" }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Close, null, Modifier.size(16.dp), tint = Mute) }
}

@Composable
private fun Label(
    text: String,
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    family: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: Float = 0f,
    features: String? = null,
    align: TextAlign = TextAlign.Unspecified,
) = BasicText(
    text,
    modifier,
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        fontFamily = family,
        letterSpacing = letterSpacing.sp,
        fontFeatureSettings = features,
        textAlign = align,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

/** The page is white, so the status bar icons go dark while the block is on screen. */
@Composable
private fun KeyRevealSheetLightBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = true
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
