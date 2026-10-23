package com.ngocdevv.nativecook.blocks.sheets.placeholderrevealsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds for one pass of the autoPlay loop. */
const val LOOP = 10f

// The timeline, in seconds from the start of a loop.
private const val PRESS_AT = 0.8f
private const val OPEN_AT = 0.95f
private const val PRESS_DONE_AT = 8.5f
private const val CLOSE_AT = 8.8f
/** Seconds after the last touch before the loop starts again from rest. */
private const val USER_HOLD = 3f

private val Page = Color(0xFF121214)
private val PageCard = Color(0xFF232326)
private val PageInk = Color(0xFFF4F4F5)
private val PageMute = Color(0xFF8E8E93)
private val Running = Color(0xFF3DDC84)
private val Paused = Color(0xFFFF9F0A)
private val Blue = Color(0xFF41AFF3)
private val BluePressed = Color(0xFF2F9EE4)
private val Teal = Color(0xFF4FD6D0)
private val TealDeep = Color(0xFF2FBAB4)
private val TealBack = Color(0xFF38BFB9)
private val TealBackDeep = Color(0xFF249E99)
private val Caption = Color(0xFF7FC3EE)
private val CardShadow = rgba(15, 110, 106, 1f)
private val BlobHalo = rgba(220, 245, 255, 0.7f)
private const val HAIRLINE = 1f / 3f

/** The lip goes from deep blue to light grey with the sheet: a straight mix of the two. */
private fun lipColor(t: Float) = Color(
    lerp(0x2A.toFloat(), 0xDF.toFloat(), t) / 255f,
    lerp(0x8F.toFloat(), 0xE4.toFloat(), t) / 255f,
    lerp(0xD3.toFloat(), 0xEA.toFloat(), t) / 255f,
)

private const val MARGIN = 16f
// The sheet floats inside the screen's corners with a darker lip under its bottom edge.
private const val SHEET_INSET = 8f
private const val SHEET_RADIUS = 44f
private const val SHEET_HEIGHT = 488f
private const val LIP = 5f
private const val PLACEHOLDER_WIDTH = 160f
private const val PLACEHOLDER_HEIGHT = 210f
private const val PLACEHOLDER_TOP = 132f
private const val PLACEHOLDER_RADIUS = 22f
private const val DASH = 9f
private const val GAP = 7f
private const val STROKE = 2.5f
private const val CARD_WIDTH = 156f
private const val CARD_HEIGHT = 204f
private const val CARD_TOP = 142f
private const val CARD_RADIUS = 18f
private const val STOP_TOP = 372f
private const val BUTTON_HEIGHT = 52f
private const val PAGE_BUTTON_HEIGHT = 52f
private const val BLOB = 120f

private const val SLIDE = 550
/** Seconds for one turn of the wobble clock; the wobble and the float are one cycle of it. */
private const val WOBBLE = 1.6f
private const val WOBBLE_DEGREES = 3f
private const val FLOAT = 4f
// Damping ratio is Reanimated's damping over 2 * sqrt(stiffness * mass).
private val PopSpring = spring<Float>(dampingRatio = 8f / (2f * sqrt(150f)), stiffness = 150f)
private val FanSpring = spring<Float>(dampingRatio = 11f / (2f * sqrt(150f)), stiffness = 150f)
private const val POP_AFTER = 250L
private const val STOP_AFTER = 1000L
private const val STOP_IN = 300
private const val BLOB_IN = 450
private const val RECOLOR = 350
private const val FAN_AFTER = 110
private const val FAN_ANGLE = 9f
private const val FAN_SPREAD = 26f
private const val FADE = 180
private const val REST_FADE = 350
/** How dark the page gets under the sheet. */
private const val DIM = 0.55f

private class Automation(val title: String, val text: String, val time: String, val every: String, val running: Boolean)

private val Automations = listOf(
    Automation("Mental Models Daily", "Short lessons on the thinking tools careful investors lean on, one model at a time.", "9:00AM", "Every 2 days", true),
    Automation("Stoic Mornings", "A passage and one question to start the day calmly, with a short reflection.", "7:30AM", "Every day", true),
    Automation("Founder Playbook", "Hard-won lessons on building a company, from the first idea to the first hire.", "6:00PM", "Weekly", false),
)

private val Bullets = listOf("Talk to ten users before writing code", "Ship something small every week", "Hire for slope, not intercept")

private val CubicOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val CubicInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val QuadInOut = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private enum class Phase { Closed, Preparing, Done }
private enum class Pressed { None, Generate, Done }

/**
 * A page with a "Generate cards" button. The sheet that rises is blue and holds a dashed
 * placeholder that wobbles while something is made; then a white blob swells inside it, the sheet
 * turns white and three real cards fan out of the placeholder with a Done button. Nothing is
 * generated here: [preparing] seconds stand in for the work.
 *
 * @param cardTitle The title of the front card of the result.
 * @param preparing Seconds the placeholder wobbles before the real cards replace it.
 * @param autoPlay Presses "Generate cards" and "Done" by itself every [LOOP] seconds.
 * @param onGenerate Called when the generation starts.
 * @param onReady Called when the cards are shown.
 * @param onClose Called when the sheet is dismissed, by Stop, Done or the loop.
 */
@Composable
fun PlaceholderRevealSheet(
    pageTitle: String = "Automations",
    generateLabel: String = "Generate cards",
    title: String = "Preparing",
    subtitle: String = "Generating card preview",
    doneTitle: String = "ALL SET",
    doneCaption: String = "This is a preview. The full cards will be delivered later.",
    cardTitle: String = "Founder Playbook",
    stopLabel: String = "STOP",
    doneLabel: String = "DONE",
    preparing: Float = 5.15f,
    autoPlay: Boolean = false,
    onGenerate: (() -> Unit)? = null,
    onReady: (() -> Unit)? = null,
    onClose: ((finished: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val wait by rememberUpdatedState(preparing)
    val generated by rememberUpdatedState(onGenerate)
    val ready by rememberUpdatedState(onReady)
    val closed by rememberUpdatedState(onClose)

    var phase by remember { mutableStateOf(Phase.Closed) }
    var autoPressed by remember { mutableStateOf(Pressed.None) }
    var pending by remember { mutableStateOf<Job?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    val sheet = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val stop = remember { Animatable(0f) }
    val blob = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val fan = remember { Animatable(0f) }
    // The wobble clock, 0 to 1 and round again; read while drawing only.
    val clock = remember { mutableFloatStateOf(0f) }
    var clockOn by remember { mutableStateOf(false) }
    val running = remember { HashMap<Animatable<Float, *>, Job>() }

    /** Animates [value] after [after] ms; starting another animation of it cancels this one. */
    fun play(value: Animatable<Float, *>, to: Float, spec: AnimationSpec<Float>, after: Long = 0) {
        running[value]?.cancel()
        running[value] = scope.launch {
            if (after > 0) delay(after)
            value.animateTo(to, spec)
        }
    }

    fun set(value: Animatable<Float, *>, to: Float) {
        running[value]?.cancel()
        scope.launch { value.snapTo(to) }
    }

    fun timed(ms: Int, easing: Easing = CubicOut) = tween<Float>(ms, easing = easing)

    fun show() {
        for (value in listOf(pop, stop, blob, reveal, fan)) set(value, 0f)
        clockOn = false
        play(sheet, 1f, timed(if (reducedMotion) FADE else SLIDE))
        if (reducedMotion) {
            play(pop, 1f, timed(FADE))
            play(stop, 1f, timed(FADE))
            return
        }
        play(pop, 1f, PopSpring, POP_AFTER)
        play(stop, 1f, timed(STOP_IN), STOP_AFTER)
        clockOn = true
    }

    fun showCards() {
        pending = null
        phase = Phase.Done
        val blobIn = if (reducedMotion) 0 else BLOB_IN
        play(blob, 1f, timed(blobIn))
        play(reveal, 1f, timed(if (reducedMotion) FADE else RECOLOR, QuadInOut), blobIn.toLong())
        if (reducedMotion) play(fan, 1f, timed(FADE)) else play(fan, 1f, FanSpring, (BLOB_IN + FAN_AFTER).toLong())
        ready?.invoke()
    }

    fun generate() {
        if (phase != Phase.Closed) return
        autoPressed = Pressed.None
        phase = Phase.Preparing
        show()
        generated?.invoke()
        pending = scope.launch {
            delay((wait * 1000).toLong())
            showCards()
        }
    }

    fun dismiss(finished: Boolean, ms: Int) {
        pending?.cancel()
        pending = null
        autoPressed = Pressed.None
        phase = Phase.Closed
        running[sheet]?.cancel()
        running[sheet] = scope.launch {
            sheet.animateTo(0f, timed(ms, CubicInOut))
            // Only a sheet that reached the bottom resets what it holds; opening again cancels this.
            clockOn = false
            for (value in listOf(pop, stop, blob, reveal, fan)) value.snapTo(0f)
        }
        closed?.invoke(finished)
    }

    fun rest() {
        if (phase != Phase.Closed) dismiss(false, REST_FADE)
        autoPressed = Pressed.None
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoPressed = Pressed.None
        touches++
    }

    LaunchedEffect(clockOn) {
        if (!clockOn) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        val origin = withFrameNanos { it }
        while (true) clock.floatValue = (((withFrameNanos { it } - origin) / 1e9f) % WOBBLE) / WOBBLE
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
            until(PRESS_AT)
            autoPressed = Pressed.Generate
            until(OPEN_AT)
            generate()
            until(PRESS_DONE_AT)
            autoPressed = Pressed.Done
            until(CLOSE_AT)
            dismiss(true, if (reducedMotion) FADE else SLIDE)
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
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
        val bottom = max(insets.calculateBottomPadding().value, 16f)
        val sheetHeight = min(SHEET_HEIGHT, height - top - 160f)
        val sheetWidth = width - 2f * SHEET_INSET
        val sheetRest = height - SHEET_INSET - sheetHeight - LIP
        val density = LocalDensity.current.density
        val sheetShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.45f), 20f) }
        val glow = remember { SoftRoundRect(Color.White.copy(alpha = 0.95f), 9f) }
        val halo = remember { SoftRoundRect(BlobHalo, 24f) }

        Column(
            Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = (top + 12f).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(pageTitle, 34f, 40f, FontWeight.ExtraBold, PageInk, TextAlign.Start, letterSpacing = -0.8f)
                    Label("2 running · next at 7:30AM", 15f, 20f, FontWeight.Medium, PageMute, TextAlign.Start)
                }
                Box(Modifier.size(40.dp).background(PageCard, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, Modifier.size(22.dp), tint = PageInk)
                }
            }
            for (item in Automations) AutomationCard(item)
        }

        PillButton(
            generateLabel, 17f, 22f, FontWeight.Bold, 0f, PAGE_BUTTON_HEIGHT, 12f,
            autoPressed == Pressed.Generate, phase == Phase.Closed, ::generate,
            Modifier.offset(x = MARGIN.dp, y = (height - bottom - PAGE_BUTTON_HEIGHT).dp).width((width - 2f * MARGIN).dp),
            icon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = Color.White) },
        )

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = DIM * sheet.value }.background(Color.Black))

        Box(
            Modifier
                .offset(x = SHEET_INSET.dp, y = sheetRest.dp)
                .size(sheetWidth.dp, (sheetHeight + LIP).dp)
                .graphicsLayer { translationY = (1f - sheet.value) * (sheetHeight + SHEET_INSET + 24f) * this.density }
                // A darker lip under the sheet's bottom edge, which turns grey with the sheet.
                .drawBehind {
                    val r = SHEET_RADIUS * this.density
                    drawIntoCanvas { sheetShadow.draw(it.nativeCanvas, 0f, 18f * this.density, size.width, size.height, r) }
                    drawRoundRect(lipColor(reveal.value), cornerRadius = CornerRadius(r))
                },
        ) {
            Box(
                Modifier
                    .size(sheetWidth.dp, sheetHeight.dp)
                    .clip(RoundedCornerShape(SHEET_RADIUS.dp))
                    .background(Blue),
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal.value }.background(Color.White))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = 36.dp)
                        .graphicsLayer {
                            val p = reveal.value
                            alpha = (1f - min(1f, p * 2.5f)) * (1f - min(1f, p * 2f))
                            translationY = -8f * p * this.density
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Label(title, 34f, 40f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.6f)
                    Label(subtitle, 17f, 22f, FontWeight.SemiBold, Color.White.copy(alpha = 0.62f))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = 30.dp)
                        .padding(horizontal = 44.dp)
                        .graphicsLayer {
                            val t = min(1f, max(0f, reveal.value * 2f - 0.6f))
                            alpha = t
                            translationY = 8f * (1f - t) * this.density
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Label(doneTitle, 32f, 38f, FontWeight.Black, Blue, letterSpacing = 0.2f)
                    Label(doneCaption, 15f, 20f, FontWeight.SemiBold, Caption)
                }

                // The dashed placeholder: wobbles and floats on the clock, pops in, swells with the blob.
                Canvas(
                    Modifier
                        .offset(x = ((sheetWidth - PLACEHOLDER_WIDTH) / 2f).dp, y = PLACEHOLDER_TOP.dp)
                        .size(PLACEHOLDER_WIDTH.dp, PLACEHOLDER_HEIGHT.dp)
                        .graphicsLayer {
                            val wave = clock.floatValue * 2f * PI.toFloat()
                            val p = pop.value
                            scaleX = (0.6f + 0.4f * p) * (1f + 0.1f * blob.value)
                            scaleY = scaleX
                            rotationZ = WOBBLE_DEGREES * sin(wave)
                            translationY = FLOAT * sin(wave + 1.2f) * this.density
                            alpha = min(1f, p * 3f)
                        },
                ) {
                    scale(density, density, pivot = Offset.Zero) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.16f),
                            size = Size(PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT),
                            cornerRadius = CornerRadius(PLACEHOLDER_RADIUS),
                        )
                        drawRoundRect(
                            Color.White.copy(alpha = 0.62f),
                            Offset(STROKE / 2f, STROKE / 2f),
                            Size(PLACEHOLDER_WIDTH - STROKE, PLACEHOLDER_HEIGHT - STROKE),
                            CornerRadius(PLACEHOLDER_RADIUS),
                            style = Stroke(
                                STROKE,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, GAP), (1f - clock.floatValue) * (DASH + GAP)),
                            ),
                        )
                        val b = blob.value
                        val alpha = min(1f, b * 3f) * (1f - min(1f, reveal.value * 2f))
                        if (alpha > 0f) {
                            val s = 1.4f * b
                            val cx = PLACEHOLDER_WIDTH / 2f
                            val cy = PLACEHOLDER_HEIGHT / 2f
                            val r = BLOB / 2f
                            scale(s, s, pivot = Offset(cx, cy)) {
                                // Two glows: a wide pale-blue one grown by 8 pt and a tight white one.
                                drawIntoCanvas {
                                    halo.draw(it.nativeCanvas, cx - r - 8f, cy - r - 8f, BLOB + 16f, BLOB + 16f, r + 8f, alpha)
                                    glow.draw(it.nativeCanvas, cx - r, cy - r, BLOB, BLOB, r, alpha)
                                }
                                drawCircle(Color.White.copy(alpha = 0.94f * alpha), r, Offset(cx, cy))
                            }
                        }
                    }
                }

                val stopSource = remember { MutableInteractionSource() }
                val stopPressed by stopSource.collectIsPressedAsState()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (STOP_TOP - 10f).dp)
                        .graphicsLayer { alpha = stop.value * (1f - min(1f, reveal.value * 4f)) },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .clickable(stopSource, null, enabled = phase == Phase.Preparing, role = Role.Button) {
                                dismiss(false, if (reducedMotion) FADE else SLIDE)
                            }
                            .padding(10.dp),
                    ) {
                        Row(
                            Modifier
                                .height(36.dp)
                                .background(Color.White.copy(alpha = if (stopPressed) 0.2f else 0f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.StopCircle, null, Modifier.size(20.dp), tint = Color.White)
                            Label(stopLabel, 17f, 22f, FontWeight.Bold, Color.White, letterSpacing = 0.4f)
                        }
                    }
                }

                for (side in listOf(-1f, 1f, 0f)) Card(side, cardTitle, (sheetWidth - CARD_WIDTH) / 2f, fan)

                PillButton(
                    doneLabel, 17f, 22f, FontWeight.ExtraBold, 1f, BUTTON_HEIGHT, 10f,
                    autoPressed == Pressed.Done, phase == Phase.Done,
                    { dismiss(true, if (reducedMotion) FADE else SLIDE) },
                    Modifier
                        .offset(x = 20.dp, y = (sheetHeight - 24f - BUTTON_HEIGHT).dp)
                        .width((sheetWidth - 40f).dp)
                        .graphicsLayer {
                            val t = min(1f, max(0f, (reveal.value - 0.3f) / 0.7f))
                            alpha = t
                            translationY = (1f - t) * 90f * this.density
                        },
                )
            }
        }
    }
}

@Composable
private fun AutomationCard(item: Automation) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PageCard, RoundedCornerShape(28.dp))
            .border(HAIRLINE.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(28.dp))
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp),
    ) {
        Label(item.title, 24f, 30f, FontWeight.ExtraBold, PageInk, TextAlign.Start, letterSpacing = -0.5f, maxLines = 1)
        Label(item.text, 15f, 20f, FontWeight.Medium, PageMute, TextAlign.Start, Modifier.padding(top = 4.dp), maxLines = 2)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label(item.time, 17f, 22f, FontWeight.ExtraBold, PageInk, TextAlign.Start, letterSpacing = 0.2f)
                Label(item.every, 15f, 20f, FontWeight.Medium, PageMute, TextAlign.Start)
            }
            val color = if (item.running) Running else Paused
            Row(Modifier.padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Label(if (item.running) "RUNNING" else "PAUSED", 13f, 18f, FontWeight.ExtraBold, color, letterSpacing = 0.8f)
            }
        }
    }
}

/**
 * One of the three cards fanned out of the placeholder: [side] is -1 for the left back card, 1 for
 * the right one, 0 for the front, which carries the content. Its face is a vertical teal gradient
 * with a soft light along its top edge, a fine light border and a teal shadow.
 */
@Composable
private fun Card(side: Float, title: String, left: Float, fan: Animatable<Float, *>) {
    val front = side == 0f
    val far = remember { SoftRoundRect(CardShadow.copy(alpha = 0.28f), 14f) }
    val near = remember { SoftRoundRect(CardShadow.copy(alpha = 0.2f), 2f) }
    Column(
        Modifier
            .offset(x = left.dp, y = CARD_TOP.dp)
            .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
            .graphicsLayer {
                val f = fan.value
                alpha = min(1f, f * 3f)
                scaleX = 0.6f + 0.4f * f
                scaleY = scaleX
                rotationZ = FAN_ANGLE * side * f
                translationX = FAN_SPREAD * side * f * this.density
            }
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas {
                        far.draw(it.nativeCanvas, 0f, 14f, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS)
                        near.draw(it.nativeCanvas, 0f, 2f, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS)
                    }
                    val face = if (front) listOf(Teal, TealDeep) else listOf(TealBack, TealBackDeep)
                    drawRoundRect(Brush.verticalGradient(face, 0f, CARD_HEIGHT), size = Size(CARD_WIDTH, CARD_HEIGHT), cornerRadius = CornerRadius(CARD_RADIUS))
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)), 0f, CARD_HEIGHT * 0.4f),
                        size = Size(CARD_WIDTH, CARD_HEIGHT * 0.4f),
                        cornerRadius = CornerRadius(CARD_RADIUS),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.3f), Offset(0.5f, 0.5f), Size(CARD_WIDTH - 1f, CARD_HEIGHT - 1f),
                        CornerRadius(CARD_RADIUS - 0.5f), style = Stroke(1f),
                    )
                }
            }
            .padding(start = 14.dp, end = 14.dp, top = 16.dp),
    ) {
        if (front) {
            Label(title, 16f, 20f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.2f, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            Label(
                "Hard-won lessons on building from zero", 10.5f, 13f, FontWeight.SemiBold, Color.White.copy(alpha = 0.82f),
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp), maxLines = 2,
            )
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height((HAIRLINE * 2f).dp).background(Color.White.copy(alpha = 0.4f)))
            for (line in Bullets) {
                Row(Modifier.padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(4.dp).background(Color.White, CircleShape))
                    Label(line, 10f, 13f, FontWeight.SemiBold, Color.White, TextAlign.Start, Modifier.weight(1f), maxLines = 2)
                }
            }
        }
    }
}

/** A blue pill with a label and a blue glow; darker while it is pressed or [held] by the autoPlay loop. */
@Composable
private fun PillButton(
    label: String,
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    letterSpacing: Float,
    height: Float,
    glowSigma: Float,
    held: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val glow = remember(glowSigma) { SoftRoundRect(Blue.copy(alpha = 0.35f), glowSigma) }
    Row(
        modifier
            .height(height.dp)
            .drawBehind {
                val r = this.size.height / 2f
                drawIntoCanvas { glow.draw(it.nativeCanvas, 0f, 8f * this.density, this.size.width, this.size.height, r) }
                drawRoundRect(if (held || pressed) BluePressed else Blue, cornerRadius = CornerRadius(r))
            }
            .clip(RoundedCornerShape(50))
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Label(label, size, lineHeight, weight, Color.White, letterSpacing = letterSpacing)
    }
}

@Composable
private fun Label(
    text: String,
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    align: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text,
    modifier,
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        textAlign = align,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)
