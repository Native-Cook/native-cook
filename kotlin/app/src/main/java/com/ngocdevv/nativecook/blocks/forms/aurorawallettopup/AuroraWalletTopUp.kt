package com.ngocdevv.nativecook.blocks.forms.aurorawallettopup

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * @param balance What the wallet holds, in units of the currency.
 * @param sky The sky of its round picture, top to bottom.
 * @param ridge The far ridge of the picture.
 * @param peak Its near mountain.
 */
data class Wallet(val name: String, val balance: Double, val sky: List<Color>, val ridge: Color, val peak: Color)

val SampleWallets = listOf(
    Wallet("Wallet 02", 478.0, listOf(Color(0xFFC59AF2), Color(0xFF7F73EE), Color(0xFF3D5BD8)), Color(0xFF2F49C4), Color(0xFF14246E)),
    Wallet("Wallet 03", 1250.4, listOf(Color(0xFFF2734F), Color(0xFFF7A65A), Color(0xFFFBDC9A)), Color(0xFFB5486A), Color(0xFF5A2152)),
    Wallet("Wallet 01", 62.15, listOf(Color(0xFF1F9E8F), Color(0xFF5CC7A4), Color(0xFFD7EFB0)), Color(0xFF1D7A80), Color(0xFF0F3F52)),
)

private val Background = Color(0xFF171717)

// The screen behind the spinner and the result.
private val Dim = Color(0xFF121212)

// The wallet row and the currency pill sit in wells darker than the screen.
private val Well = Color(0xFF101010)
private val Chip = Color(0xFF222222)
private val Ink = Color(0xFFEDEDED)
private val Mute = Color(0xFF7C7C7C)
private val Status = Color(0xFFD6D6D6)
private val ButtonFill = Color(0xFFF9F9F9)
private val ButtonPressed = Color(0xFF8C8C8C)
private val ButtonInk = Color(0xFF262626)

private const val MARGIN = 16f
private const val HEADER = 44f
private const val AMOUNT = 84f

// The widest the amount may get before it shrinks.
private const val AMOUNT_WIDTH = 330f
private const val CARD = 80f
private const val PAD_GAP = 24f
private const val KEY_ROW = 68f
private const val KEY_ROW_MIN = 48f
private const val KEY_WIDTH = 80f
private const val FLASH = 64f
private const val BUTTON_GAP = 58f
private const val BUTTON = 56f
private const val BUTTON_MARGIN = 14f

// Everything but the pad's rows, with 160 dp for the amount: what is left sets the row height.
private const val FIXED_HEIGHT = HEADER + 2f + 160f + CARD + PAD_GAP + BUTTON_GAP + BUTTON

// Blur of the form behind the spinner, and how much of its brightness the wait takes away:
// more where the system cannot blur (before Android 12).
private const val BLUR = 16f
private const val FORM_DIM = 0.5f
private const val FORM_DIM_UNBLURRED = 0.8f

// Degrees a second.
private const val SPIN = 400f
private const val DIGITS = 6
private const val DECIMALS = 2
private val Keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "back"))

// A thin left arrow in an 18 × 14 box, and a check in an 18 × 18 box.
private val Arrow = Path().apply {
    moveTo(17f, 7f); lineTo(1f, 7f)
    moveTo(7f, 1f); lineTo(1f, 7f); lineTo(7f, 13f)
}
private val Check = Path().apply {
    moveTo(5.2f, 9.4f); lineTo(8f, 12.1f); lineTo(12.9f, 6.4f)
}

// The wallet's picture in a unit box: a far ridge, a mountain, and the snow on it.
private const val PICTURE = 48f

private fun outline(vararg points: Pair<Float, Float>) = Path().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * PICTURE, y * PICTURE) else lineTo(x * PICTURE, y * PICTURE) }
    close()
}

private val Ridge = outline(0f to 0.6f, 0.16f to 0.5f, 0.3f to 0.57f, 0.5f to 0.4f, 0.68f to 0.55f, 0.84f to 0.47f, 1f to 0.56f, 1f to 1f, 0f to 1f)
private val Peak = outline(0f to 0.88f, 0.2f to 0.7f, 0.42f to 0.5f, 0.52f to 0.6f, 0.62f to 0.53f, 0.82f to 0.76f, 1f to 0.84f, 1f to 1f, 0f to 1f)
private val Snow = outline(0.42f to 0.5f, 0.52f to 0.6f, 0.455f to 0.63f, 0.41f to 0.59f, 0.35f to 0.625f, 0.33f to 0.585f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun topUpText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = topUpText(17f, 22f, FontWeight.Medium, Ink)
private val CurrencyStyle = topUpText(16f, 20f, FontWeight.SemiBold, Ink)
private val WalletNameStyle = topUpText(17f, 21f, FontWeight.SemiBold, Color.White)
private val WalletBalanceStyle = topUpText(14f, 17f, FontWeight.Normal, Mute)
private val WalletsStyle = topUpText(14f, 18f, FontWeight.SemiBold, Ink)
private val DigitStyle = topUpText(32f, 38f, FontWeight.Medium, Color.White)
private val ContinueStyle = topUpText(17f, 22f, FontWeight.SemiBold, ButtonInk)
private val StatusStyle = topUpText(20f, 25f, FontWeight.Normal, Status)

/** "1250.5" → "1,250.5": thousands separators on the whole part, the rest as typed. */
private fun grouped(draft: String): String {
    var whole = draft.substringBefore('.')
    var out = ""
    while (whole.length > 3) {
        out = "," + whole.takeLast(3) + out
        whole = whole.dropLast(3)
    }
    return whole + out + if ('.' in draft) "." + draft.substringAfter('.') else ""
}

private fun money(cents: Long) = grouped((cents / 100).toString()) + "." + (cents % 100).toString().padStart(2, '0')

private fun draftFor(amount: Double): String {
    val cents = (max(0.0, amount) * 100).roundToLong()
    val whole = (cents / 100).toString()
    if (cents % 100 == 0L) return whole
    return whole + "." + (cents % 100).toString().padStart(2, '0').removeSuffix("0")
}

/** The amount gets smaller as it gets longer, so that six digits and two decimals still fit. */
private fun amountSize(draft: String): Float {
    val shown = grouped(draft)
    val marks = shown.count { it == ',' || it == '.' }
    return min(AMOUNT, floor(AMOUNT_WIDTH / ((shown.length - marks * 0.6f) * 0.62f)))
}

/** The top-up in progress: what it adds, to which wallet, and the balance before and after. */
private class Run(val sum: Double, val index: Int, val auto: Boolean, val from: Double, val to: Double)

/**
 * A top-up screen: an amount typed on a number pad, the wallet it goes to, and Continue. Continue
 * blurs the form behind a spinner while light grows out of the button, fills the bottom of the
 * screen and breathes there, then gathers and sweeps up through the screen, leaving the result.
 * Nothing is paid here: [onContinue] is where the real request goes.
 *
 * @param amount The amount the pad starts with.
 * @param symbol Written before a balance.
 * @param wallets The wallets the "Wallets" button goes through; the first one is shown.
 * @param autoPlay Presses Continue by itself every [LOOP] seconds and leaves the balance as it was.
 * @param processingDuration Seconds from Continue to the moment the light sweeps up and the result shows.
 * @param onContinue Called with the amount and the wallet when Continue is released and the wait starts.
 * @param onDone Called when the result shows.
 */
@Composable
fun AuroraWalletTopUp(
    amount: Double = 100.0,
    currency: String = "USD",
    symbol: String = "$",
    wallets: List<Wallet> = SampleWallets,
    autoPlay: Boolean = false,
    processingDuration: Float = 2.8f,
    title: String = "Add money",
    availableLabel: String = "Available",
    walletsLabel: String = "Wallets",
    continueLabel: String = "Continue",
    processingLabel: String = "Topping up wallet",
    doneLabel: String = "Wallet topped up",
    onContinue: ((amount: Double, wallet: Wallet) -> Unit)? = null,
    onDone: ((amount: Double, wallet: Wallet) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val duration = processingDuration
    val back = returnAt(duration)
    // The loop and the run outlive a recomposition: they read what the caller passes through these.
    val given by rememberUpdatedState(amount)
    val purses by rememberUpdatedState(wallets)
    val started by rememberUpdatedState(onContinue)
    val finished by rememberUpdatedState(onDone)

    // The amount as typed, digits with at most one point; null until a key is pressed.
    var typed by remember { mutableStateOf<String?>(null) }
    var walletIndex by remember { mutableIntStateOf(0) }
    // What was added to each wallet here, by its place in `wallets`.
    var added by remember { mutableStateOf(mapOf<Int, Double>()) }
    // The top-up in progress, from Continue until the form is back.
    var run by remember { mutableStateOf<Run?>(null) }
    var arrived by remember { mutableStateOf(false) }
    // Continue held down by the autoPlay loop.
    var autoPressed by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Seconds since Continue was released; negative while the form is idle. Read while drawing only.
    val elapsed = remember { mutableFloatStateOf(-1f) }

    val draft = typed ?: draftFor(amount)
    val value = draft.toDoubleOrNull() ?: 0.0
    val busy = run != null

    /** What a wallet holds now: its balance plus the top-ups made here. */
    fun held(index: Int) = purses[index].balance + (added[index] ?: 0.0)

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoPressed = false
        touches++
    }

    fun start(auto: Boolean) {
        val sum = (typed ?: draftFor(given)).toDoubleOrNull() ?: 0.0
        if (run != null || sum <= 0.0) return
        val index = walletIndex
        val from = held(index)
        // A loop that plays by itself must come back to the frame it started from, and is silent.
        run = Run(sum, index, auto, from = from, to = if (auto) from else from + sum)
        if (!auto) tick()
        started?.invoke(sum, purses[index])
    }

    fun press(key: String) {
        touched()
        tick()
        when {
            key == "back" -> typed = if (draft.length > 1) draft.dropLast(1) else "0"
            key == "." -> if ('.' !in draft) typed = "$draft."
            '.' in draft -> if (draft.length - draft.indexOf('.') <= DECIMALS) typed = draft + key
            draft == "0" -> typed = key
            draft.length < DIGITS -> typed = draft + key
        }
    }

    LaunchedEffect(run) {
        val now = run ?: return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) {
            val e = (withFrameNanos { it } - origin) / 1e9f
            elapsed.floatValue = e
            if (!arrived && e >= duration + RESULT_AFTER) {
                arrived = true
                if (!now.auto) {
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
                    added = added + (now.index to (added[now.index] ?: 0.0) + now.sum)
                }
                finished?.invoke(now.sum, purses[now.index])
            }
            if (e >= endAt(duration)) break
        }
        elapsed.floatValue = -1f
        arrived = false
        run = null
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (run != null) delay(100)
            // The loop starts over from the amount it was given.
            typed = null
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(PRESS_AT)
            autoPressed = true
            until(PRESS_AT + PRESS_FOR)
            autoPressed = false
            start(auto = true)
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The balance in cents: it counts up as the form comes back.
    val balance by remember(back) {
        derivedStateOf {
            val now = run
            val shown = if (now == null) held(walletIndex) else now.from + (now.to - now.from) * easeOut((elapsed.floatValue - back - COUNT_AFTER) / COUNT_FOR)
            (shown * 100).roundToLong()
        }
    }

    fun blur() = if (elapsed.floatValue < 0f) 0f else smooth(elapsed.floatValue, 0f, BLUR_IN)
    fun returned() = if (elapsed.floatValue < 0f) 1f else smooth(elapsed.floatValue, back + FORM_BACK_AFTER, back + FORM_BACK_AFTER + FORM_BACK_IN)
    fun done() = smooth(elapsed.floatValue, duration + RESULT_AFTER, duration + RESULT_AFTER + RESULT_IN)

    val canBlur = Build.VERSION.SDK_INT >= 31
    val paint = remember { AuroraPaint() }
    val checkMeasure = remember { PathMeasure().apply { setPath(Check, false) } }
    val checkDrawn = remember { Path() }

    BoxWithConstraints(modifier.fillMaxSize().background(Dim)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = max(insets.calculateBottomPadding().value - 4f, 16f)
        val rowHeight = ((maxHeight.value - top.value - bottom - FIXED_HEIGHT) / 4f).coerceIn(KEY_ROW_MIN, KEY_ROW)
        val screenHeight = maxHeight.value

        Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - blur() * (1f - returned()) }.background(Background))

        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val e = elapsed.floatValue
                    alpha = when {
                        e < 0f -> 1f
                        e >= back -> returned()
                        else -> (1f - (if (canBlur) FORM_DIM else FORM_DIM_UNBLURRED) * blur()) * (1f - smooth((e - duration) / SWEEP, 0.05f, 0.4f))
                    }
                    val sigma = if (e < back) BLUR.dp.toPx() * blur() else 0f
                    renderEffect = if (canBlur && sigma > 0.5f) {
                        val radius = sigmaToRadius(sigma)
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                }
                .then(if (busy) Modifier.clearAndSetSemantics {} else Modifier)
                .padding(top = top + 2.dp, bottom = (BUTTON_GAP + BUTTON + bottom).dp),
        ) {
            Box(Modifier.fillMaxWidth().height(HEADER.dp), contentAlignment = Alignment.Center) {
                BasicText(title, style = TitleStyle, maxLines = 1)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(44.dp, HEADER.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !busy, role = Role.Button) {
                            touched()
                            onBack?.invoke()
                        }
                        .semantics { contentDescription = "Back" },
                    contentAlignment = Alignment.Center,
                ) {
                    ArrowIcon(width = 18f, stroke = 2f, color = Ink)
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).offset(y = (-8).dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    grouped(draft),
                    style = topUpText(amountSize(draft), 100f, FontWeight.SemiBold, Color.White, -2.5f).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.height(100.dp).semantics {
                        contentDescription = "Amount"
                        stateDescription = "${grouped(draft)} $currency"
                    },
                )
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier.height(32.dp).softWell(10f, 3f).padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(currency, style = CurrencyStyle, maxLines = 1)
                }
            }

            Box(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth().height(CARD.dp).softWell(24f, 5f)) {
                // A new wallet fades in over the old one.
                Crossfade(walletIndex, animationSpec = tween(180), label = "wallet") { index ->
                    val shown = wallets[index]
                    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 100.dp), verticalAlignment = Alignment.CenterVertically) {
                        WalletPicture(shown)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            BasicText(shown.name, style = WalletNameStyle, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            val cents = if (index == walletIndex) balance else (held(index) * 100).roundToLong()
                            BasicText("$symbol${money(cents)} $availableLabel", style = WalletBalanceStyle, maxLines = 1)
                        }
                    }
                }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val dim by animateFloatAsState(if (pressed) 0.6f else 1f, tween(120), label = "wallets")
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 15.dp)
                        .graphicsLayer { alpha = dim }
                        .height(34.dp)
                        .background(Chip, CircleShape)
                        .clickable(interaction, indication = null, enabled = !busy, role = Role.Button) {
                            touched()
                            tick()
                            walletIndex = (walletIndex + 1) % wallets.size
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(walletsLabel, style = WalletsStyle, maxLines = 1)
                }
            }

            Column(Modifier.padding(top = PAD_GAP.dp, start = MARGIN.dp, end = MARGIN.dp)) {
                for (row in Keys) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (key in row) Key(key, rowHeight, enabled = !busy) { press(key) }
                    }
                }
            }
        }

        if (busy) {
            // A layer of its own, so the lights blend with each other and not with the form.
            Canvas(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                surface(0.dp) { _, _, w, h ->
                    paint.draw(
                        this, elapsed.floatValue, duration, reducedMotion, w, h,
                        barX = BUTTON_MARGIN, barY = screenHeight - bottom - BUTTON, barWidth = w - BUTTON_MARGIN * 2f, barHeight = BUTTON,
                    )
                }
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val fill by animateColorAsState(if (pressed || autoPressed) ButtonPressed else ButtonFill, tween(100), label = "continue")
        val enabled by animateFloatAsState(if (value > 0.0) 1f else 0.35f, tween(150), label = "enabled")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = BUTTON_MARGIN.dp, end = BUTTON_MARGIN.dp, bottom = bottom.dp)
                .fillMaxWidth()
                .height(BUTTON.dp)
                .graphicsLayer {
                    val e = elapsed.floatValue
                    alpha = enabled * if (e < 0f) 1f else if (e < back) 1f - smooth(e, 0f, 0.18f) else returned()
                }
                .background(fill, CircleShape)
                .clip(CircleShape)
                .clickable(interaction, indication = null, enabled = !busy && value > 0.0, role = Role.Button) {
                    touched()
                    start(auto = false)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(continueLabel, style = ContinueStyle, maxLines = 1)
        }

        Row(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val e = elapsed.floatValue
                    alpha = if (e < 0f) 0f else smooth(e, 0.05f, BLUR_IN) * (1f - smooth(e, back, back + STATUS_OUT))
                }
                .then(if (busy) Modifier.clearAndSetSemantics { contentDescription = if (arrived) doneLabel else processingLabel } else Modifier.clearAndSetSemantics {}),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(18.dp)) {
                surface(0.dp) { _, _, _, _ ->
                    val done = done()
                    val centre = Offset(9f, 9f)
                    drawCircle(Status, 8.25f, centre, alpha = 0.3f * (1f - done), style = Stroke(1.5f))
                    rotate(if (reducedMotion) 0f else max(0f, elapsed.floatValue) * SPIN, centre) {
                        drawArc(Status, -90f, 108f, false, Offset(0.75f, 0.75f), Size(16.5f, 16.5f), alpha = 1f - done, style = Stroke(1.5f, cap = StrokeCap.Round))
                    }
                    drawCircle(Color.White, 9f * (0.6f + 0.4f * done), centre, alpha = done)
                    checkDrawn.rewind()
                    checkMeasure.getSegment(0f, checkMeasure.length * done, checkDrawn, true)
                    drawPath(checkDrawn, Dim, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.width(10.dp))
            Box {
                BasicText(processingLabel, style = StatusStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 1f - done() })
                BasicText(doneLabel, style = StatusStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = done() })
            }
        }
    }
}

/** A dark well whose edge is soft: it sinks into the screen. */
@Composable
private fun Modifier.softWell(radius: Float, soft: Float): Modifier {
    val edge = remember(soft) { SoftRoundRect(Well, sigma = soft) }
    return drawBehind { surface(0.dp) { _, _, w, h -> drawIntoCanvas { edge.draw(it.nativeCanvas, 0f, 0f, w, h, radius) } } }
        .background(Well, RoundedCornerShape(radius.dp))
}

@Composable
private fun ArrowIcon(width: Float, stroke: Float, color: Color) {
    val k = width / 18f
    Canvas(Modifier.size(width.dp, (14f * k).dp)) {
        surface(0.dp) { _, _, _, _ ->
            scale(k, k, pivot = Offset.Zero) {
                drawPath(Arrow, color, style = Stroke(stroke / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The wallet's round picture: a sky, a far ridge and a mountain with snow on it. */
@Composable
private fun WalletPicture(wallet: Wallet) {
    Canvas(Modifier.size(PICTURE.dp).clip(CircleShape).clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            drawRect(Brush.verticalGradient(wallet.sky, startY = 0f, endY = PICTURE * 0.7f), size = Size(PICTURE, PICTURE))
            drawPath(Ridge, wallet.ridge)
            drawPath(Peak, wallet.peak)
            drawPath(Snow, Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun Key(label: String, height: Float, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // On at once, off slowly: a flash.
    val flash by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 40 else 350), label = "flash")
    Box(
        Modifier
            .size(KEY_WIDTH.dp, height.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (label == "back") "Delete" else if (label == ".") "Point" else label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(FLASH.dp).graphicsLayer { alpha = flash }.background(Color.White.copy(alpha = 0.1f), CircleShape))
        if (label == "back") ArrowIcon(width = 24f, stroke = 2.4f, color = Color.White) else BasicText(label, style = DigitStyle, maxLines = 1)
    }
}
