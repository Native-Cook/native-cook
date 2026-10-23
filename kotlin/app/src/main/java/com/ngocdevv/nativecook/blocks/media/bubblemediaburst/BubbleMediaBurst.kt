package com.ngocdevv.nativecook.blocks.media.bubblemediaburst

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

private fun photo(id: String, size: Int) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=$size&h=$size&q=80"

private val DefaultItems = listOf(
    BubbleItem("Portrait", photo("1517841905240-472988babdf9", 640), Color(0xFF3F6F86), Color(0xFFBFE3F2)),
    BubbleItem("Balloons", photo("1641128324972-af3212f0f6bd", 320), Color(0xFF2F7FD0), Color(0xFFFFD1B8)),
    BubbleItem("Forest", photo("1441974231531-c6227db76b6e", 320), Color(0xFF5D6B3A), Color(0xFFE3F0A8)),
    BubbleItem("City lights", photo("1496442226666-8d4d0e62e6e9", 320), Color(0xFF3A2A26), Color(0xFFFFB38A)),
    BubbleItem("Sky", photo("1506905925346-21bda4d32df4", 120), Color(0xFF9AA6C4), Color(0xFFFFD6E4)),
)

private val Background = Color(0xFFEFEFEF)
private val Accent = Color(0xFFB9B4CF)
private val Ink = Color(0xFF1A1A1A)
private val Mute = Color(0xFF8A8A8A)

// Light from above on the "+": a white wash fading to a faint shade at the bottom.
private val ButtonSheen = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.06f))
private val ButtonRim = Color.White.copy(alpha = 0.3f)
private val ButtonShadow = rgba(70, 60, 110, 0.18f)
private const val BUTTON_SHADOW_Y = 8f
private const val BUTTON_SHADOW_SIGMA = 12f

private const val SIDE = 24f
private const val HEAD_TOP = 24f
private const val TITLE_LINE = 34f
private const val HINT_GAP = 6f
private const val HINT_LINE = 20f

/** The centre of the "+" sits this far above the bottom safe area. */
private const val BUTTON_LIFT = 120f

/** Room kept between the hint and the top of the cluster. */
private const val CLUSTER_GAP = 24f

/** The "+" at cluster scale 1: its diameter, and the length and weight of its strokes. */
private const val BUTTON = 56f
private const val PLUS = 20f
private const val PLUS_STROKE = 2f

/** The cluster grows with the screen up to this scale, and shrinks on small ones down to the other. */
private const val MAX_SCALE = 1.3f
private const val MIN_SCALE = 0.6f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * A media "add" screen: a round "+" that opens a cluster of photos in glass bubbles. They are born
 * behind the "+", travel up along a curve to their places with a springy overshoot, float there, and
 * fall back into it one after the other when it is pressed again; tapping a bubble picks it.
 *
 * @param items Up to five photos, from the big bubble to the tiny one.
 * @param accent The fill of the "+".
 * @param autoPlay Opens the bubbles, picks one and closes them again by itself every [LOOP] seconds.
 */
@Composable
fun BubbleMediaBurst(
    title: String = "Add to your mix",
    hint: String = "Tap + to open your library",
    items: List<BubbleItem> = DefaultItems,
    background: Color = Background,
    accent: Color = Accent,
    autoPlay: Boolean = false,
    openLabel: String = "Open library",
    closeLabel: String = "Close library",
    onOpenChange: ((Boolean) -> Unit)? = null,
    onSelect: ((BubbleItem, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BubbleMediaBurstSystemBars()
    val still = rememberReducedMotion()
    val view = LocalView.current
    val shown = items.take(SLOTS.size)
    // Read by the loop, the flows and the touch handlers, which outlive a recomposition.
    val count by rememberUpdatedState(shown.size)
    val latestItems by rememberUpdatedState(shown)
    val opened by rememberUpdatedState(onOpenChange)
    val selected by rememberUpdatedState(onSelect)

    // The clock in seconds since the block appeared (read only while drawing); the events made by
    // hand; the autoPlay's loop start, null while a touch holds it off.
    val epoch = remember { System.nanoTime() }
    val clock = remember { mutableDoubleStateOf(0.0) }
    var hand by remember { mutableStateOf(restEvents(SLOTS.size)) }
    var loopStart by remember { mutableStateOf<Double?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Keeps the clock running after a touch until everything is still.
    var awake by remember { mutableStateOf(false) }
    val running = autoPlay || awake

    fun time() = (System.nanoTime() - epoch) / 1e9

    fun eventsAt(now: Double): Events = loopStart?.let { autoEvents(now, it, SLOTS.size) } ?: hand

    val events = { eventsAt(clock.doubleValue) }
    val open by remember { derivedStateOf { eventsAt(clock.doubleValue).isOpen } }
    val picks by remember { derivedStateOf { eventsAt(clock.doubleValue).pickOn } }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun takeOver() {
        loopStart?.let {
            hand = autoEvents(time(), it, SLOTS.size)
            loopStart = null
        }
        awake = true
        touches++
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) withFrameNanos { clock.doubleValue = (it - epoch) / 1e9 }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { open }.drop(1).collect { opened?.invoke(it) }
    }
    LaunchedEffect(Unit) {
        var before = picks
        snapshotFlow { picks }.drop(1).collect { now ->
            for (i in now.indices) if (i < count && now[i] != before.getOrElse(i) { false }) selected?.invoke(latestItems[i], now[i])
            before = now
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) {
            // Let the clock stop once a touch has played out and the bubbles are away.
            if (touches == 0) return@LaunchedEffect
            delay((SETTLE * 1000).toLong())
            if (!hand.isOpen) awake = false
            return@LaunchedEffect
        }
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to rest the way a tap on "+" goes there, then the loop starts again.
            if (hand.isOpen) {
                hand = hand.copy(closeAt = time())
                delay((SETTLE * 1000).toLong())
            }
        }
        hand = restEvents(SLOTS.size)
        loopStart = time()
    }

    // The "+" reports when the finger goes down and comes up.
    val plusTouch = remember { MutableInteractionSource() }
    LaunchedEffect(plusTouch) {
        plusTouch.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    takeOver()
                    hand = hand.copy(downAt = time())
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    takeOver()
                    hand = hand.copy(upAt = time())
                }
            }
        }
    }

    val buttonShadow = remember { SoftRoundRect(ButtonShadow, BUTTON_SHADOW_SIGMA) }

    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val width = maxWidth.value
        val height = maxHeight.value
        val bx = width / 2f
        val by = height - bottom - BUTTON_LIFT
        val headBottom = top + HEAD_TOP + TITLE_LINE + HINT_GAP + HINT_LINE
        val cluster = minOf(MAX_SCALE, (width - SIDE * 2f) / CLUSTER_WIDTH, (by - headBottom - CLUSTER_GAP) / CLUSTER_HEIGHT).coerceAtLeast(MIN_SCALE)
        val places = remember(bx, by, cluster) {
            SLOTS.map { slot ->
                val restX = bx + slot.x * cluster
                val restY = by - slot.y * cluster
                // The path bows to the left of the straight line from the "+", so the bubbles swirl out.
                val dx = restX - bx
                val dy = restY - by
                Place(restX, restY, bx + dx / 2f + CURVE * dy, by + dy / 2f - CURVE * dx, bx, by)
            }
        }

        Column(Modifier.padding(start = SIDE.dp, end = SIDE.dp, top = (top + HEAD_TOP).dp)) {
            Text(
                title,
                color = Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                lineHeight = TITLE_LINE.sp,
                maxLines = 1,
                style = TextStyle(lineHeightStyle = Centered),
                modifier = Modifier.height(TITLE_LINE.dp).semantics { heading() },
            )
            Text(
                hint,
                color = Mute,
                fontSize = 15.sp,
                lineHeight = HINT_LINE.sp,
                maxLines = 1,
                style = TextStyle(lineHeightStyle = Centered),
                modifier = Modifier.padding(top = HINT_GAP.dp).height(HINT_LINE.dp),
            )
        }

        shown.forEachIndexed { i, item ->
            Bubble(
                index = i,
                item = item,
                slot = SLOTS[i],
                place = places[i],
                diameter = SLOTS[i].size * cluster,
                clock = clock,
                events = events,
                still = still,
                open = open,
                picked = picks.getOrElse(i) { false },
                onPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    takeOver()
                    hand = hand.copy(
                        pickOn = hand.pickOn.toMutableList().also { it[i] = !it[i] },
                        pickAt = hand.pickAt.toMutableList().also { it[i] = time() },
                    )
                },
            )
        }

        val button = BUTTON * cluster
        val arm = PLUS * cluster
        val stroke = PLUS_STROKE * cluster
        Box(
            Modifier
                .offset((bx - button / 2f).dp, (by - button / 2f).dp)
                .size(button.dp)
                .graphicsLayer {
                    val s = buttonScale(events(), clock.doubleValue, still).toFloat()
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { buttonShadow.draw(it.nativeCanvas, 0f, BUTTON_SHADOW_Y, button, button, button / 2f) }
                    }
                }
                .clip(CircleShape)
                .background(accent)
                .background(Brush.verticalGradient(ButtonSheen))
                .border(1.dp, ButtonRim, CircleShape)
                .clickable(interactionSource = plusTouch, indication = null, role = Role.Button) {
                    takeOver()
                    hand = if (hand.isOpen) hand.copy(closeAt = time()) else hand.copy(openAt = time())
                }
                .semantics { contentDescription = if (open) closeLabel else openLabel },
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = plusTurn(events(), clock.doubleValue, still).toFloat() }) {
                scale(density, density, pivot = Offset.Zero) {
                    val c = button / 2f
                    drawRoundRect(Color.White, Offset(c - arm / 2f, c - stroke / 2f), Size(arm, stroke), CornerRadius(stroke / 2f))
                    drawRoundRect(Color.White, Offset(c - stroke / 2f, c - arm / 2f), Size(stroke, arm), CornerRadius(stroke / 2f))
                }
            }
        }
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun BubbleMediaBurstSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
