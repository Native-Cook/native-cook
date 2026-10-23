package com.ngocdevv.nativecook.blocks.navigation.accountscrubtabbar

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Message(val sender: String, val subject: String, val time: String)

/** @param color The badge and the pictures of its senders. */
data class Account(val address: String, val color: Color, val messages: List<Message>)

enum class TabIcon { Home, Search, Tray, Compose }

data class Tab(val title: String, val icon: TabIcon)

val SampleAccounts = listOf(
    Account(
        "nora.lind@inbox.example", Color(0xFFF5B5E6),
        listOf(
            Message("Ida Holm", "Proofs for Friday", "9:41"),
            Message("Billing", "Your receipt for October", "9:05"),
            Message("Marek Dvorak", "Dinner on Saturday?", "8:15"),
            Message("Trail Club", "Route notes for the weekend", "Yesterday"),
            Message("Lena Ortiz", "Photos from the lake", "Yesterday"),
            Message("Library", "Your hold is ready", "Mon"),
            Message("Sam Whitlow", "Re: spare keys", "Sun"),
        ),
    ),
    Account(
        "nora@lind.studio", Color(0xFFF2D675),
        listOf(
            Message("Jonas Brandt", "Logo, round two", "10:02"),
            Message("Print Room", "Quote for 200 posters", "9:20"),
            Message("Aiko Tanabe", "Invoice 0142", "8:40"),
            Message("Studio Notes", "Shared folder updated", "Yesterday"),
            Message("Petra Novak", "Type samples attached", "Tue"),
            Message("Hale & Finch", "Contract, signed copy", "Mon"),
            Message("Workshop", "Seats for November", "Sun"),
        ),
    ),
    Account(
        "team@oakline.example", Color(0xFF62E6C3),
        listOf(
            Message("Deploy Bot", "Build 418 passed", "10:14"),
            Message("Rhea Malik", "Sprint review moved", "9:36"),
            Message("Support", "Three new tickets", "8:58"),
            Message("Tomas Reyes", "Design handoff", "Yesterday"),
            Message("Calendar", "Planning, Thursday 10:00", "Yesterday"),
            Message("Noor Haddad", "Budget sheet v3", "Tue"),
            Message("Oakline HR", "Holiday schedule", "Mon"),
        ),
    ),
)

val SampleTabs = listOf(
    Tab("Inbox", TabIcon.Home),
    Tab("Search", TabIcon.Search),
    Tab("Archive", TabIcon.Tray),
    Tab("Drafts", TabIcon.Compose),
)

private val Page = Color(0xFF0A0A0A)
private val Bar = Color(0xFF1A1A1A)
private val Ink = Color.White
private val Mute = Color(0xFF8E8E93)
private val Faint = Color(0xFF636366)
private val Hairline = Color(0xFF1C1C1E)
private val IconInk = Color(0xFFB4B4B8)
private val Address = Color(0xFFD0D0D4)

private const val MARGIN = 20f
private const val TITLE_TOP = 14f
private const val ROW = 72f
private const val ROWS = 7
private const val AVATAR = 40f
private const val BAR_MARGIN = 16f
private const val BAR_HEIGHT = 56f
private const val BAR_RADIUS = 24f

// From the bottom of the screen, where the navigation bar leaves room for it.
private const val BAR_BOTTOM = 30f
private const val ICON_BOX = 22f
private const val STROKE = 1.8f

// The open tab's lines are this much heavier.
private const val STROKE_ON = 1.35f
private const val BADGE = 22f

// The badge under the finger.
private const val SWELL = 1.25f
private const val DOT = 13f

// How far apart two accounts are in the column of badges and in the column of addresses: a
// neighbouring badge is outside the bar, a neighbouring address is cut in half by its edge.
private const val BADGE_PITCH = 44f
private const val ADDRESS_PITCH = 28f
private const val ADDRESS_GAP = 20f
private const val ADDRESS_SLIDE = 12f

// How much of an address is left one place away from the slot.
private const val ADDRESS_AWAY = 0.44f

// Finger travel that moves the wheel by one account.
private const val STEP = 34f

// How far past its ends the wheel can be pulled, in accounts.
private const val RUBBER = 0.8f

// The icons behind the switcher: blurred where the system can (Android 12), smaller where not.
private const val ICON_BLUR = 10f
private const val ICON_FADE = 0.15f
private const val ICON_SHRINK = 0.9f
private const val FINGER = 44f
private const val FINGER_OPACITY = 0.45f

const val LOOP = 10f

// Seconds a press on the badge lasts before it becomes a scrub.
private const val HOLD = 0.25f
private const val OPEN_IN = 0.25f
private const val CLOSE_AFTER = 0.25f
private const val CLOSE_IN = 0.3f
private const val PAGE_FADE = 0.15f

// Mass 1, stiffness 300, damping 26.
private val Snap = spring<Float>(dampingRatio = 0.7506f, stiffness = 300f)
private val Fade = tween<Float>((PAGE_FADE * 1000).toInt(), easing = LinearEasing)
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// The loop starts again this long after the last touch.
private const val USER_HOLD = 3f

// Each drag of the drawn finger takes this long.
private const val MOVE = 0.4f
private const val FINGER_IN = 0.15f
private const val FINGER_OUT = 0.2f

private enum class Finger { Down, Hold, Drag, Up }

private class Step(val at: Float, val finger: Finger, val to: Float = 0f)

// The finger of the autoPlay loop: when it lands on the badge, how many accounts it has dragged
// to since (up the screen is later in the list), and when it lifts.
private val Script = listOf(
    Step(0.6f, Finger.Down),
    Step(1.1f, Finger.Drag, 1f),
    Step(1.8f, Finger.Drag, 2f),
    Step(2.5f, Finger.Drag, 3f),
    Step(3.5f, Finger.Drag, 2f),
    Step(4.2f, Finger.Drag, 1f),
    Step(5.0f, Finger.Up),
    Step(6.3f, Finger.Down),
    Step(6.8f, Finger.Drag, 1f),
    Step(7.5f, Finger.Drag, 0f),
    Step(8.1f, Finger.Drag, -1f),
    Step(8.9f, Finger.Up),
)

// The script with the moment each press has lasted long enough to engage.
private val Steps = Script.flatMap { if (it.finger == Finger.Down) listOf(it, Step(it.at + HOLD, Finger.Hold)) else listOf(it) }

// Icons in a 22 dp box. A body is an outline that fills when its tab is open; an inner line lies
// on a body and is cut out of the filled one; a gap clears the bar's colour under a line.
private enum class Kind { Body, Line, Inner, Gap }

private class Part(val kind: Kind, d: String, val width: Float = STROKE) {
    val path = PathParser().parsePathString(d).toPath()
}

private val Icons = mapOf(
    TabIcon.Home to listOf(Part(Kind.Body, "M3.6 9.9 L11 3.4 L18.4 9.9 L18.4 17.6 Q18.4 18.9 17.1 18.9 L4.9 18.9 Q3.6 18.9 3.6 17.6 Z")),
    TabIcon.Search to listOf(
        Part(Kind.Line, "M16.2 9.9 C16.2 13.38 13.38 16.2 9.9 16.2 C6.42 16.2 3.6 13.38 3.6 9.9 C3.6 6.42 6.42 3.6 9.9 3.6 C13.38 3.6 16.2 6.42 16.2 9.9 Z"),
        Part(Kind.Line, "M14.6 14.6 L19 19"),
    ),
    TabIcon.Tray to listOf(
        Part(Kind.Body, "M6.2 3.6 L15.8 3.6 Q18.8 3.6 18.8 6.6 L18.8 15.4 Q18.8 18.4 15.8 18.4 L6.2 18.4 Q3.2 18.4 3.2 15.4 L3.2 6.6 Q3.2 3.6 6.2 3.6 Z"),
        Part(Kind.Inner, "M3.2 11.6 L7.4 11.6 Q7.9 14.6 11 14.6 Q14.1 14.6 14.6 11.6 L18.8 11.6"),
    ),
    TabIcon.Compose to listOf(
        Part(Kind.Body, "M6.2 5.6 L13.4 5.6 Q16.4 5.6 16.4 8.6 L16.4 15.8 Q16.4 18.8 13.4 18.8 L6.2 18.8 Q3.2 18.8 3.2 15.8 L3.2 8.6 Q3.2 5.6 6.2 5.6 Z"),
        Part(Kind.Gap, "M10 12 L17.8 4.2", width = 6.4f),
        Part(Kind.Line, "M10 12 L17.8 4.2", width = 2.6f),
    ),
)

private fun DrawScope.drawIcon(icon: TabIcon, active: Boolean) {
    val ink = if (active) Ink else IconInk
    // The paths are in dp.
    scale(density, density, pivot = Offset.Zero) {
        fun stroke(part: Part, color: Color, width: Float) =
            drawPath(part.path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (part in Icons.getValue(icon)) {
            when (part.kind) {
                Kind.Gap -> stroke(part, Bar, part.width)
                Kind.Inner -> stroke(part, if (active) Bar else ink, STROKE)
                Kind.Line -> stroke(part, ink, part.width * if (active) STROKE_ON else 1f)
                Kind.Body -> {
                    if (active) drawPath(part.path, ink)
                    stroke(part, ink, STROKE)
                }
            }
        }
    }
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun mailText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = mailText(34f, 41f, FontWeight.Bold, Ink)
private val AccountStyle = mailText(15f, 20f, FontWeight.Normal, Mute)
private val InitialStyle = mailText(16f, 20f, FontWeight.SemiBold, Ink)
private val SenderStyle = mailText(16f, 21f, FontWeight.SemiBold, Ink)
private val TimeStyle = mailText(13f, 18f, FontWeight.Normal, Faint)
private val SubjectStyle = mailText(15f, 20f, FontWeight.Normal, Mute)
private val AddressStyle = mailText(15f, 20f, FontWeight.Normal, Address)
private val NumberStyle = mailText(12f, 15f, FontWeight.Bold, Bar)

/** Where the wheel stands for a finger that asks for [raw]: held back more and more past its ends. */
private fun rubber(raw: Float, last: Int): Float {
    val over = if (raw < 0f) -raw else if (raw > last) raw - last else 0f
    val give = (1f - 1f / (over * 0.55f / RUBBER + 1f)) * RUBBER
    return if (raw < 0f) -give else if (raw > last) last + give else raw
}

private class MailRow(val message: Message, val color: Color)

/** The rows of an entry: an account's own mail, or every account's in turn for the last entry. */
private fun rowsFor(accounts: List<Account>, entry: Int, tab: Int): List<MailRow> {
    val lists = accounts.map { account -> account.messages.map { MailRow(it, account.color) } }
    val rows = if (entry < accounts.size) {
        lists[entry]
    } else {
        val longest = lists.maxOfOrNull { it.size } ?: 0
        (0 until longest).flatMap { i -> lists.reversed().mapNotNull { it.getOrNull(i) } }
    }
    // A stand-in for the other tabs' own screens: the same mail, starting further down.
    val from = if (rows.isEmpty()) 0 else (tab * 2) % rows.size
    return (rows.drop(from) + rows.take(from)).take(ROWS)
}

/**
 * A mail screen whose tab bar ends in an account badge. Holding the badge turns the whole bar into
 * an account switcher: the icons blur away, the addresses stand in a wheel beside the badge, and
 * dragging up or down scrubs through them while the page follows. A tap on the badge steps to the
 * next account.
 *
 * @param accounts The accounts the badge scrubs through, first to last.
 * @param allLabel The entry after the last account, which shows every account's mail.
 * @param tabs The items before the badge; the first one is open.
 * @param autoPlay Holds the badge and scrubs through the accounts by itself every [LOOP] seconds.
 * @param onSelectAccount Called with the place of the account under the slot; `accounts.size` is "All accounts".
 */
@Composable
fun AccountScrubTabBar(
    accounts: List<Account> = SampleAccounts,
    allLabel: String = "All accounts",
    tabs: List<Tab> = SampleTabs,
    autoPlay: Boolean = false,
    onSelectAccount: ((index: Int) -> Unit)? = null,
    onSelectTab: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The gesture and the loop outlive a recomposition: they read what the caller passes through these.
    val last by rememberUpdatedState(accounts.size)
    val onAccount by rememberUpdatedState(onSelectAccount)
    val onTab by rememberUpdatedState(onSelectTab)

    var tab by remember { mutableIntStateOf(0) }
    // The entry under the slot: an account, or `accounts.size` for all of them.
    var entry by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The autoPlay loop makes no haptics; a touch does.
    var byHand by remember { mutableStateOf(false) }

    // 0 as a tab bar, 1 as the account switcher.
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf<Job?>(null) }
    // Where the columns of badges and addresses stand, in entries; whole numbers at rest.
    val wheel = remember { Animatable(0f) }
    // The entry the wheel stood on when the hold began.
    var anchor by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableStateOf(false) }
    // The finger the autoPlay loop draws: down on the badge, how far it has dragged, how visible.
    var auto by remember { mutableStateOf(false) }
    val finger = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }

    fun select(index: Int) {
        if (index == entry) return
        entry = index
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onAccount?.invoke(index)
    }

    fun engage() {
        closing?.cancel()
        anchor = entry.toFloat()
        held = true
        scope.launch { open.animateTo(1f, tween((OPEN_IN * 1000).toInt(), easing = EaseOutStrong)) }
    }

    fun scrub(dy: Float) {
        val raw = anchor - dy / STEP
        val nearest = raw.roundToInt().coerceIn(0, last)
        if (!reducedMotion) {
            scope.launch { wheel.snapTo(rubber(raw, last)) }
        } else if (nearest != entry) {
            // Without movement the badge and the address change by a short fade, one step at a time.
            scope.launch { wheel.animateTo(nearest.toFloat(), Fade) }
        }
        select(nearest)
    }

    fun release() {
        held = false
        val to = entry.toFloat()
        scope.launch { wheel.animateTo(to, if (reducedMotion) Fade else Snap) }
        // The bar stays a switcher for a moment, as it stands, and then closes.
        closing?.cancel()
        closing = scope.launch {
            open.stop()
            delay((CLOSE_AFTER * 1000).toLong())
            open.animateTo(0f, tween((CLOSE_IN * 1000).toInt(), easing = EaseOutStrong))
        }
    }

    /** The wheel goes to an entry with no finger on it. */
    fun moveTo(index: Int) {
        select(index)
        scope.launch { wheel.animateTo(index.toFloat(), if (reducedMotion) Fade else Snap) }
    }

    /** A plain tap on the badge: the next entry, and round to the first after the last. */
    fun step() = moveTo((entry + 1) % (last + 1))

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        byHand = true
        if (auto) {
            auto = false
            scope.launch { finger.stop() }
            release()
        }
        scope.launch { shown.animateTo(0f, tween((FINGER_OUT * 1000).toInt(), easing = LinearEasing)) }
        touches++
    }

    fun press(index: Int) {
        touched()
        // The icons are not there to be pressed while the bar is the switcher.
        if (open.value > 0.5f || index == tab) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        tab = index
        onTab?.invoke(index)
    }

    /** The finger the autoPlay loop draws does what a real one does, through the same functions. */
    fun perform(step: Step) {
        when (step.finger) {
            Finger.Down -> {
                scope.launch { finger.snapTo(0f) }
                scope.launch { shown.animateTo(1f, tween((FINGER_IN * 1000).toInt(), easing = LinearEasing)) }
            }
            Finger.Hold -> {
                engage()
                auto = true
            }
            Finger.Drag -> scope.launch { finger.animateTo(-step.to * STEP, tween((MOVE * 1000).toInt(), easing = EaseInOut)) }
            Finger.Up -> {
                auto = false
                release()
                scope.launch { shown.animateTo(0f, tween((FINGER_OUT * 1000).toInt(), easing = LinearEasing)) }
            }
        }
    }

    // The drawn finger scrubs through the same code as a real one.
    LaunchedEffect(Unit) {
        snapshotFlow { finger.value }.collect { if (auto) scrub(it) }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // A finger that is still holding the badge starts the wait again when it lifts.
            if (held) return@LaunchedEffect
            // The loop starts over from the first account and the first tab.
            byHand = false
            tab = 0
            moveTo(0)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for (step in Steps) {
                until(step.at)
                perform(step)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val canBlur = Build.VERSION.SDK_INT >= 31
    val labels = accounts.map { it.address } + allLabel

    BoxWithConstraints(modifier.fillMaxSize().background(Page)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = max(BAR_BOTTOM, insets.calculateBottomPadding().value - 4f)
        val barWidth = maxWidth.value - BAR_MARGIN * 2
        val slot = barWidth / (tabs.size + 1)

        Box(Modifier.matchParentSize().padding(top = top + TITLE_TOP.dp).clipToBounds()) {
            // A new tab or account fades in over the old one.
            Crossfade(tab, Modifier.padding(horizontal = MARGIN.dp), animationSpec = Fade, label = "title") { shownTab ->
                BasicText(tabs[shownTab].title, style = TitleStyle, maxLines = 1)
            }
            Crossfade(tab to entry, Modifier.padding(top = 45.dp), animationSpec = Fade, label = "mail") { (shownTab, shownEntry) ->
                Column {
                    BasicText(
                        labels[shownEntry.coerceAtMost(labels.lastIndex)],
                        Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, bottom = 16.dp),
                        style = AccountStyle,
                        maxLines = 1,
                    )
                    for (row in rowsFor(accounts, shownEntry, shownTab)) MailRowView(row)
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = BAR_MARGIN.dp, y = (-bottom).dp)
                .size(barWidth.dp, BAR_HEIGHT.dp)
                .clip(RoundedCornerShape(BAR_RADIUS.dp))
                .background(Bar),
        ) {
            Row(
                Modifier.graphicsLayer {
                    val opened = open.value
                    alpha = 1f - (if (reducedMotion) 1f else 1f - ICON_FADE) * opened
                    val sigma = if (reducedMotion) 0f else ICON_BLUR.dp.toPx() * opened
                    renderEffect = if (canBlur && sigma > 0.5f) {
                        val radius = sigmaToRadius(sigma)
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                    if (!canBlur && !reducedMotion) {
                        scaleX = 1f - (1f - ICON_SHRINK) * opened
                        scaleY = scaleX
                    }
                },
            ) {
                tabs.forEachIndexed { i, item ->
                    Box(
                        Modifier
                            .size(slot.dp, BAR_HEIGHT.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { press(i) }
                            .semantics {
                                contentDescription = item.title
                                selected = i == tab
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(ICON_BOX.dp)) { drawIcon(item.icon, active = i == tab) }
                    }
                }
            }

            // The wheel of addresses: each rides past the slot, bright only while it is in it.
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .size(max(0f, barWidth - slot / 2f - BADGE * SWELL / 2f - ADDRESS_GAP).dp, BAR_HEIGHT.dp)
                        .graphicsLayer {
                            val away = min(1f, abs(i - wheel.value))
                            if (reducedMotion) {
                                alpha = open.value * (1f - away)
                            } else {
                                alpha = open.value * (1f - (1f - ADDRESS_AWAY) * away)
                                translationX = (1f - open.value) * ADDRESS_SLIDE.dp.toPx()
                                translationY = (i - wheel.value) * ADDRESS_PITCH.dp.toPx()
                            }
                        }
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    BasicText(label, style = AddressStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Box(
                Modifier
                    .offset(x = (slot * tabs.size).dp)
                    .size(slot.dp, BAR_HEIGHT.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            touched()
                            // Lifted, or lost to another gesture, before the hold engaged.
                            var early = false
                            val up = withTimeoutOrNull((HOLD * 1000).toLong()) { waitForUpOrCancellation().also { early = true } }
                            if (early) {
                                if (up != null) step()
                            } else {
                                // From here on the drag is the bar's, whatever lies under the finger.
                                engage()
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    scrub((change.position.y - down.position.y) / density)
                                    change.consume()
                                }
                                release()
                            }
                            touched()
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Account"
                        stateDescription = labels[entry.coerceAtMost(labels.lastIndex)]
                        role = Role.Button
                        onClick("Next account") {
                            step()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                labels.indices.forEach { i ->
                    Box(
                        Modifier.size(BADGE.dp).graphicsLayer {
                            scaleX = 1f + (SWELL - 1f) * open.value
                            scaleY = scaleX
                            if (reducedMotion) alpha = 1f - min(1f, abs(i - wheel.value)) else translationY = (i - wheel.value) * BADGE_PITCH.dp.toPx()
                        },
                    ) {
                        if (i < accounts.size) {
                            Box(Modifier.matchParentSize().background(accounts[i].color, CircleShape), contentAlignment = Alignment.Center) {
                                BasicText((i + 1).toString(), style = NumberStyle)
                            }
                        } else {
                            // A dot for each account: they step down to the right, each over the one before.
                            val colors = accounts.take(3).map { it.color }.reversed()
                            colors.forEachIndexed { n, color ->
                                val at = if (colors.size > 1) n / (colors.size - 1f) else 0.5f
                                Box(
                                    Modifier
                                        .offset(x = (at * (BADGE - DOT + 2f) - 1f).dp, y = (1.5f + at * (BADGE - DOT - 3f)).dp)
                                        .size(DOT.dp)
                                        .background(Bar, CircleShape)
                                        .padding(1.5.dp)
                                        .background(color, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (autoPlay) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (BAR_MARGIN + barWidth - slot / 2f - FINGER / 2f).dp, y = -(bottom + (BAR_HEIGHT - FINGER) / 2f).dp)
                    .size(FINGER.dp)
                    .graphicsLayer {
                        alpha = FINGER_OPACITY * shown.value
                        translationY = finger.value.dp.toPx()
                        scaleX = 1.25f - 0.25f * shown.value
                        scaleY = scaleX
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun MailRowView(row: MailRow) {
    Row(Modifier.fillMaxWidth().height(ROW.dp).padding(horizontal = MARGIN.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(AVATAR.dp).background(row.color.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            BasicText(row.message.sender.take(1), style = InitialStyle.copy(color = row.color))
        }
        Column(
            Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    // A hairline under the text, one pixel whatever the density.
                    drawLine(Hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
                },
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(row.message.sender, Modifier.weight(1f), style = SenderStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(row.message.time, Modifier.padding(start = 8.dp), style = TimeStyle, maxLines = 1)
            }
            BasicText(row.message.subject, Modifier.padding(top = 2.dp), style = SubjectStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
