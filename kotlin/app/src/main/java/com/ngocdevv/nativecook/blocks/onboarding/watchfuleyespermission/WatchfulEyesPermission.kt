package com.ngocdevv.nativecook.blocks.onboarding.watchfuleyespermission

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.max
import kotlin.math.min

/** @param mine The user's own reply, on the right. */
data class ChatMessage(val text: String, val mine: Boolean = false)

val SampleMessages = listOf(
    ChatMessage("Hmm, sounds great!", mine = true),
    ChatMessage("☝️ Statistically, 81% of my users feel significant sleep improvement during the first 4 weeks. And I have a question that perfectly predicts how well we match"),
    ChatMessage("Good idea 🤝", mine = true),
    ChatMessage("Wow, we have a few interesting things in common! Usually, people who choose these options are impressed with what I have to offer 😎"),
    ChatMessage("Intriguing! Please, show me 🚀", mine = true),
    ChatMessage("Super! Let's start by quickly analyzing your sleep and habits 🕵️ Sometimes, it helps to uncover curious insights right away"),
    ChatMessage("Let's see what you can find out 🤓", mine = true),
)

// Night: black, a band of navy low on the screen, black again.
private val Sky = arrayOf(
    0f to Color(0xFF050508),
    0.28f to Color(0xFF050508),
    0.55f to Color(0xFF0A1124),
    0.7f to Color(0xFF11182D),
    0.86f to Color(0xFF05070D),
    1f to Color(0xFF030305),
)
private val EyeInk = Color(0xFFF4EDE2)

// The eyes' glow is a disc that fades to nothing at its rim, not a blur. Drawn one unit large, and scaled.
private val Glow = Brush.radialGradient(
    0f to EyeInk.copy(alpha = 0.34f), 0.4f to EyeInk.copy(alpha = 0.13f), 0.75f to EyeInk.copy(alpha = 0.035f), 1f to EyeInk.copy(alpha = 0f),
    center = Offset.Zero, radius = 1f,
)
private val HaloLight = Brush.radialGradient(
    0f to Color.White.copy(alpha = 0.1f), 0.5f to Color.White.copy(alpha = 0.035f), 1f to Color.White.copy(alpha = 0f),
    center = Offset.Zero, radius = 1f,
)

/** A smiling eye is flatter than an open one. */
private const val SQUINT = 0.5
private val HouseFill = Color(0xFF191E21)
private val HouseEdge = Color.White.copy(alpha = 0.1f)
private val RainInk = Color.White.copy(alpha = 0.12f)
private val Outline = Color.White.copy(alpha = 0.22f)
private val Ink = Color(0xFFD9DEE8)
private val Faint = Color.White.copy(alpha = 0.35f)
private val Panel = Color.White.copy(alpha = 0.05f)
private val Soft = Color.White.copy(alpha = 0.04f)
private val HeartInk = Color(0xFFFF2D55)
private val Bar = Color(0xFF212A41)
private val Track = Color(0xFF2A3145)
private val AllInk = Color(0xFF5B6B8C)
private val Disc = Brush.radialGradient(0f to Color(0xFF142030), 0.55f to Color(0xFF142030), 1f to Color(0x00142030), center = Offset.Zero, radius = 1f)
private val CheckInk = Color(0xFF5FE0BC)

// The chat's bubbles keep clear of the screen's sides by these, the assistant's stop short of the
// user's on the right, and the small tile on the button is this large.
private const val CHAT_LEFT = 32f
private const val CHAT_RIGHT = 43f
private const val CHAT_SHORT = 5f
private const val SMALL_TILE = 26f
private const val SMALL_TILE_RADIUS = 7f
private const val SMALL_HEART = 13f

private val Hairline = Stroke(1f)
private val CheckLine = Stroke(CHECK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val SmileLine = Stroke(SMILE_STROKE, cap = StrokeCap.Round)
private val Sides = floatArrayOf(-1f, 1f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun eyesText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Medium) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val BubbleStyle = eyesText(13.5f, 21f, Ink)
private val MessageStyle = eyesText(16.5f, 26f, Ink)
private val LabelStyle = eyesText(14f, 18f, Color.White)
private val LaterStyle = eyesText(13f, 18f, Faint)
private val TitleStyle = eyesText(17f, 22f, Color.White)
private val AllStyle = eyesText(13f, 18f, AllInk, FontWeight.Normal)

/** The last tap that did something. */
private class Press(val target: Int, val at: Double)

/** Where a finger makes the eyes look, since when, and whether it is still down. */
private class Look(val x: Double, val y: Double, val since: Double, val held: Boolean)

/** The eyes as drawn: their middle, and the rest of them. */
private class Pose(val x: Double, val y: Double, val eyes: EyesAt)

/** autoPlay: when its loop began, which of its taps is next, and when it may start over after a touch. */
private class Plan {
    var start = Double.POSITIVE_INFINITY
    var step = 0
    var resume = Double.POSITIVE_INFINITY
}

/** A disc of light [radius] dp large: [light] is drawn one unit large. */
private fun DrawScope.light(light: Brush, x: Float, y: Float, radius: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(radius, radius, Offset.Zero)
    }) {
        drawCircle(light, 1f, Offset.Zero, alpha)
    }
}

/**
 * A permission primer told by a pair of glowing eyes. They sit over a chat, come down and grow
 * to ask for health data from inside a little home, fly back up while a phone's outline draws
 * itself, watch six switches flip on, and smile at the check mark. They blink, glance, startle
 * and shake a "no" on their own clock, and look towards a finger.
 *
 * @param messages The chat the screen opens on, top to bottom.
 * @param message What the eyes say when they ask.
 * @param title The name under the tile on the switches' page.
 * @param tint The colour of a switch that is on.
 * @param autoPlay Plays the four pages by itself, back at the chat every [LOOP] seconds.
 * @param onDone Called when every switch is on and the check mark shows.
 */
@Composable
fun WatchfulEyesPermission(
    messages: List<ChatMessage> = SampleMessages,
    message: String = "Don't worry, this analysis happens right on your phone, and your data remains securely protected on device.",
    startLabel: String = "Start Health analysis",
    laterLabel: String = "Later",
    title: String = "Health",
    allOnLabel: String = "Turn on all",
    allOffLabel: String = "Turn off all",
    buttonLabel: String = "Turn on all categories",
    doneLabel: String = "Perfect!",
    tint: Color = Color(0xFF7FE3C4),
    autoPlay: Boolean = false,
    onStart: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The frame loop and the touch handlers outlive a recomposition: they read what the caller passes through these.
    val auto by rememberUpdatedState(autoPlay)
    val started by rememberUpdatedState(onStart)
    val later by rememberUpdatedState(onLater)
    val finished by rememberUpdatedState(onDone)

    // Seconds since the first frame. Read while drawing, and when a touch needs the time.
    val clock = remember { mutableDoubleStateOf(0.0) }
    // The chat, as if it had been arrived at a while ago: every loop begins like this.
    var stage by remember { mutableStateOf(Stage(DONE, CHAT, -REST)) }
    var switches by remember { mutableStateOf(switchesOff()) }
    // The last tap that did something, the last "no", and the finger the eyes look at.
    var press by remember { mutableStateOf(Press(TAP_NONE, -REST)) }
    var shook by remember { mutableDoubleStateOf(-REST) }
    var look by remember { mutableStateOf(Look(0.0, 0.0, -REST, false)) }
    val plan = remember { Plan() }

    fun go(to: Int, at: Double) {
        stage = Stage(stage.to, to, at)
        // The switches' page always opens with everything off; it is not on the screen yet.
        if (to == SWITCHES) switches = switchesOff()
    }

    /** Does what a tap means, as of [at]. A touch and autoPlay both come through here. */
    fun act(action: Int, at: Double): Boolean {
        val s = stage
        if (action == TAP_NONE || !ready(s.to, at - s.t0)) return false
        when {
            s.to == CHAT -> go(ASK, at)
            s.to == ASK && action == TAP_START -> {
                go(SWITCHES, at)
                started?.invoke()
            }
            s.to == ASK && action == TAP_LATER -> {
                shook = at
                later?.invoke()
            }
            s.to == SWITCHES && action == TAP_ALL -> switches = flip(switches, if (switches.all) ALL_OFF else ALL_ON, at)
            s.to == SWITCHES && action == TAP_PILL -> switches = flip(switches, ALL_ON, at)
            s.to == SWITCHES && action >= TAP_SWITCH -> switches = flip(switches, action - TAP_SWITCH, at)
            s.to == DONE && action == TAP_PILL -> go(CHAT, at)
            else -> return false
        }
        press = Press(action, at)
        return true
    }

    /** A touch takes over from autoPlay, which goes back to the chat and starts again a while later. */
    fun touched() {
        if (!auto) return
        plan.start = Double.POSITIVE_INFINITY
        plan.resume = clock.doubleValue + USER_HOLD
    }

    LaunchedEffect(autoPlay) {
        plan.start = Double.POSITIVE_INFINITY
        plan.resume = if (autoPlay) 0.0 else Double.POSITIVE_INFINITY
    }

    LaunchedEffect(Unit) {
        val first = withFrameNanos { it }
        while (true) {
            val now = (withFrameNanos { it } - first) / 1e9
            clock.doubleValue = now
            // Every switch on: the last page follows, from when it was due.
            val due = switches.doneAt
            if (stage.to == SWITCHES && now >= due) {
                go(DONE, due)
                finished?.invoke()
            }
            if (!auto) continue
            if (now >= plan.resume) {
                // autoPlay begins, or the touches are over: back to the chat, and on with the loop from there.
                plan.resume = Double.POSITIVE_INFINITY
                if (stage.to == CHAT) stage = Stage(CHAT, CHAT, now - REST) else go(CHAT, now)
                plan.start = stage.t0 + REST
                plan.step = 0
                continue
            }
            val index = plan.step
            val next = plan.start + (if (index < Play.size) Play[index].at else LOOP)
            if (now < next) continue
            // Timed from when it was due, not from this frame: every loop is the same length.
            if (index < Play.size) {
                act(Play[index].tap, next)
                plan.step = index + 1
            } else {
                plan.start = next
                plan.step = 0
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Brush.verticalGradient(*Sky))) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val base = max(insets.calculateBottomPadding().value, 16f)
        val middle = maxHeight.value / 2
        // What the handlers and the drawing read of the layout, as state: they outlive this composition.
        val width by rememberUpdatedState(maxWidth.value.toDouble())
        val height by rememberUpdatedState(maxHeight.value.toDouble())
        val ground by rememberUpdatedState((maxHeight.value - base).toDouble())
        val eyesTop by rememberUpdatedState(top + EYES_TOP)

        fun seenNow() = seen(stage, clock.doubleValue, still)

        /** The eyes as drawn: with what a "no", a flipped switch and a finger add to their place. */
        fun pose(): Pose {
            val v = seenNow()
            val e = eyes(v.from, v.to, v.e, still, eyesTop, height / 2 + EYES_HOVER, height / 2 + EYES_ASK)
            if (still) return Pose(width / 2, e.y, e)
            val now = clock.doubleValue
            val l = look
            val f = follow(l.held, now - l.since)
            return Pose(width / 2 + e.dx + shake(now - shook) + l.x * f, e.y + lookDown(now - switches.flippedAt) + l.y * f, e)
        }

        /** How much a tapped button gives. */
        fun give(target: Int): Float {
            val p = press
            return if (still || p.target != target) 1f else pressed(clock.doubleValue - p.at).toFloat()
        }

        /** What a screen reader needs of a button that is drawn, not pressed: its page, and what a tap on it means. */
        fun Modifier.control(page: Int, action: Int) =
            if (stage.to != page) clearAndSetSemantics {}
            else semantics(mergeDescendants = true) {
                role = Role.Button
                onClick { act(action, clock.doubleValue) }
            }

        fun Modifier.shownOn(page: Int) = if (stage.to == page) this else clearAndSetSemantics {}

        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = seenNow().opacity.toFloat() }
                // The eyes look towards a finger for as long as it is down; one that has not moved has tapped.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touched()
                        fun gaze(at: Offset, since: Double) =
                            Look(toward(at.x / density - width / 2), toward(at.y / density - pose().y), since, true)
                        look = gaze(down.position, clock.doubleValue)
                        var tapped = false
                        try {
                            var moved = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    tapped = !moved
                                    break
                                }
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                look = gaze(change.position, look.since)
                            }
                        } finally {
                            look = Look(look.x, look.y, clock.doubleValue, false)
                        }
                        if (tapped) {
                            touched()
                            val action = hit(stage.to, (down.position.x / density).toDouble(), (down.position.y / density).toDouble(), width, height, ground)
                            if (act(action, clock.doubleValue)) {
                                view.performHapticFeedback(if (action >= TAP_SWITCH) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                    }
                },
        ) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = CHAT_LEFT.dp, end = CHAT_RIGHT.dp, top = (top + CHAT_TOP).dp)
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        val v = seenNow()
                        val c = chat(v.from, v.to, v.e)
                        alpha = c.opacity.toFloat()
                        translationY = c.shift.dp.toPx()
                    }
                    .control(CHAT, TAP_CHAT),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (one in messages) {
                    Box(
                        Modifier
                            .align(if (one.mine) Alignment.End else Alignment.Start)
                            .padding(end = if (one.mine) 0.dp else CHAT_SHORT.dp)
                            .background(Panel, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.5.dp),
                    ) {
                        BasicText(one.text, style = BubbleStyle)
                    }
                }
            }

            val home = remember { housePath() }
            val drops = remember { rainPath() }
            val heart = remember { heartPath() }
            val tick = remember { checkPath() }
            val grin = remember { smilePath() }
            val phone = remember(width, height) { outlinePath(width / 2, height / 2 + PHONE_TOP, height + PHONE_RADIUS) }
            // A stroke that is partly drawn: measured, and cut into `part`.
            val measure = remember { PathMeasure() }
            val part = remember { Path() }

            // Everything that is drawn, back to front: the eyes' halo, their home, the phone and its switches, the check mark, the eyes.
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                surface(0.dp) { _, _, w, h ->
                    val now = clock.doubleValue
                    val v = seenNow()
                    val p = pose()
                    val e = p.eyes
                    val cx = w / 2f
                    val cy = h / 2f

                    // The soft light the eyes throw while they are large.
                    light(HaloLight, p.x.toFloat(), p.y.toFloat(), HALO.toFloat(), e.halo.toFloat())

                    val around = house(v.from, v.to, v.e)
                    if (around.opacity > 0) {
                        val a = around.opacity.toFloat()
                        withTransform({
                            translate(cx, cy + EYES_ASK.toFloat())
                            scale(around.scale.toFloat(), around.scale.toFloat(), Offset.Zero)
                        }) {
                            drawPath(home, HouseFill, a)
                            clipPath(home) {
                                translate(top = rain(now, still).toFloat()) { drawPath(drops, RainInk, a) }
                            }
                            drawPath(home, HouseEdge, a, Hairline)
                        }
                    }

                    // The phone's outline, drawn from the middle of the notch to both sides at once.
                    val edge = outline(v.from, v.to, v.e)
                    if (edge.opacity > 0 && edge.drawn > 0) {
                        measure.setPath(phone, false)
                        part.rewind()
                        measure.getSegment(0f, measure.length * edge.drawn.toFloat(), part, true)
                        drawPath(part, Outline, edge.opacity.toFloat(), Hairline)
                        scale(-1f, 1f, Offset(cx, 0f)) { drawPath(part, Outline, edge.opacity.toFloat(), Hairline) }
                    }

                    val tile = item(v.from, v.to, v.e, ITEM_TILE)
                    val s = switches
                    scale(tile.scale.toFloat(), Offset(cx, cy + MARK_Y.toFloat())) {
                        if (tile.opacity > 0) {
                            val a = tile.opacity.toFloat()
                            val half = (TILE_SIZE / 2).toFloat()
                            translate(cx, cy + (TILE_Y + tile.rise).toFloat()) {
                                drawRoundRect(Color.White, Offset(-half, -half), Size(half * 2, half * 2), CornerRadius(TILE_RADIUS.toFloat()), alpha = a)
                                scale((TILE_HEART / 2).toFloat(), Offset.Zero) { drawPath(heart, HeartInk, a) }
                            }
                        }
                        for (row in 0 until COUNT) {
                            val line = item(v.from, v.to, v.e, ITEM_ROW + row)
                            if (line.opacity > 0) {
                                val a = line.opacity.toFloat()
                                val on = knob(s.on[row], now - s.at[row], still)
                                val left = (LIST_WIDTH - TOGGLE_WIDTH).toFloat()
                                val tall = TOGGLE_HEIGHT.toFloat()
                                val track = Size(TOGGLE_WIDTH.toFloat(), tall)
                                translate(cx - (LIST_WIDTH / 2).toFloat(), cy + (LIST_TOP + row * LIST_PITCH + line.rise).toFloat()) {
                                    drawRoundRect(Bar, Offset(0f, (-LIST_THICK / 2).toFloat()), Size(LIST_BAR.toFloat(), LIST_THICK.toFloat()), CornerRadius((LIST_THICK / 2).toFloat()), alpha = a)
                                    drawRoundRect(Track, Offset(left, -tall / 2), track, CornerRadius(tall / 2), alpha = a)
                                    // The track of a switch that is on, faded in over the one that is off.
                                    drawRoundRect(tint, Offset(left, -tall / 2), track, CornerRadius(tall / 2), alpha = a * clamp01(on).toFloat())
                                    drawCircle(Color.White, (TOGGLE_KNOB / 2).toFloat(), Offset(left + (TOGGLE_HEIGHT / 2 + (TOGGLE_WIDTH - TOGGLE_HEIGHT) * on).toFloat(), 0f), a)
                                }
                            }
                        }
                    }

                    val done = check(v.from, v.to, v.e)
                    if (done.opacity > 0) {
                        val a = done.opacity.toFloat()
                        withTransform({
                            translate(cx, cy + MARK_Y.toFloat())
                            scale(done.scale.toFloat(), done.scale.toFloat(), Offset.Zero)
                        }) {
                            light(Disc, 0f, 0f, (MARK_DISC / 2).toFloat(), a)
                            measure.setPath(tick, false)
                            part.rewind()
                            measure.getSegment(0f, measure.length * done.drawn.toFloat(), part, true)
                            drawPath(part, CheckInk, a, CheckLine)
                        }
                    }

                    val wide = e.size.toFloat()
                    val tall = (e.size * e.tall * (1 - SQUINT * e.smile)).toFloat()
                    for (side in Sides) {
                        val x = (p.x + side * e.gap / 2).toFloat()
                        val y = p.y.toFloat()
                        // A shut eye gives no light.
                        light(Glow, x, y, (e.size / 2 + e.glow).toFloat(), min(1.0, e.tall).toFloat())
                        drawOval(EyeInk, Offset(x - wide / 2, y - tall / 2), Size(wide, tall), (1 - e.smile).toFloat())
                        if (e.smile > 0) {
                            withTransform({
                                translate(x, y)
                                scale(wide, wide, Offset.Zero)
                            }) {
                                drawPath(grin, EyeInk, e.smile.toFloat(), SmileLine)
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = BUBBLE_SIDE.dp, end = BUBBLE_SIDE.dp, bottom = (base + BUBBLE_BOTTOM).dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val v = seenNow()
                        val a = ask(v.from, v.to, v.e, 0)
                        alpha = a.opacity.toFloat()
                        translationY = a.shift.dp.toPx()
                    }
                    .background(Soft, RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 13.5.dp)
                    .shownOn(ASK),
            ) {
                BasicText(message, style = MessageStyle)
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = ROW_SIDE.dp, end = ROW_SIDE.dp, bottom = (base + ROW_BOTTOM).dp)
                    .fillMaxWidth()
                    .height(ROW_HEIGHT.dp)
                    .graphicsLayer {
                        val v = seenNow()
                        val a = ask(v.from, v.to, v.e, 1)
                        alpha = a.opacity.toFloat()
                        translationY = a.shift.dp.toPx()
                        scaleX = give(TAP_START)
                        scaleY = scaleX
                    }
                    .background(Soft, RoundedCornerShape(18.dp))
                    .control(ASK, TAP_START),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(SMALL_TILE.dp)
                        .background(Color.White, RoundedCornerShape(SMALL_TILE_RADIUS.dp))
                        .drawBehind {
                            val half = (SMALL_HEART / 2).dp.toPx()
                            withTransform({
                                translate(size.width / 2, size.height / 2)
                                scale(half, half, Offset.Zero)
                            }) {
                                drawPath(heart, HeartInk)
                            }
                        },
                )
                BasicText(startLabel, style = LabelStyle, maxLines = 1)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (base + LATER_BOTTOM - LATER_HEIGHT / 2).dp)
                    .size((LATER_REACH * 2).dp, LATER_HEIGHT.dp)
                    .graphicsLayer {
                        val v = seenNow()
                        val a = ask(v.from, v.to, v.e, 2)
                        alpha = a.opacity.toFloat()
                        translationY = a.shift.dp.toPx()
                        scaleX = give(TAP_LATER)
                        scaleY = scaleX
                    }
                    .control(ASK, TAP_LATER),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(laterLabel, style = LaterStyle, maxLines = 1)
            }

            // What shrinks into the check mark moves towards it as well.
            BasicText(
                title, style = TitleStyle, maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (middle + TITLE_Y - 11).dp)
                    .graphicsLayer {
                        val v = seenNow()
                        val i = item(v.from, v.to, v.e, ITEM_TITLE)
                        alpha = i.opacity.toFloat()
                        translationY = (i.rise + (TITLE_Y - MARK_Y) * (i.scale - 1)).dp.toPx()
                        scaleX = i.scale.toFloat()
                        scaleY = scaleX
                    }
                    .shownOn(SWITCHES),
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (middle + ALL_TOP).dp)
                    .size(LIST_WIDTH.dp, ALL_HEIGHT.dp)
                    .graphicsLayer {
                        val v = seenNow()
                        val i = item(v.from, v.to, v.e, ITEM_ALL)
                        alpha = i.opacity.toFloat()
                        translationY = (i.rise + (ALL_TOP + ALL_HEIGHT / 2 - MARK_Y) * (i.scale - 1)).dp.toPx()
                        scaleX = i.scale.toFloat() * give(TAP_ALL)
                        scaleY = scaleX
                    }
                    .background(Panel, RoundedCornerShape(8.dp))
                    .padding(start = 12.dp)
                    .control(SWITCHES, TAP_ALL),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(allOnLabel, style = AllStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 1 - allOn(switches, clock.doubleValue).toFloat() })
                BasicText(allOffLabel, style = AllStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = allOn(switches, clock.doubleValue).toFloat() })
            }
            BasicText(
                doneLabel, style = TitleStyle, maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (middle + LABEL_Y - 11).dp)
                    .graphicsLayer {
                        val v = seenNow()
                        val c = check(v.from, v.to, v.e)
                        alpha = c.label.toFloat()
                        translationY = c.rise.dp.toPx()
                    }
                    .shownOn(DONE),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = PILL_SIDE.dp, end = PILL_SIDE.dp, bottom = (base + PILL_BOTTOM).dp)
                    .fillMaxWidth()
                    .height(PILL_HEIGHT.dp)
                    .graphicsLayer {
                        val v = seenNow()
                        alpha = pill(v.from, v.to, v.e).toFloat()
                        scaleX = give(TAP_PILL)
                        scaleY = scaleX
                    }
                    .background(Soft, CircleShape)
                    .control(if (stage.to == DONE) DONE else SWITCHES, TAP_PILL),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(buttonLabel, style = LabelStyle, maxLines = 1)
            }
        }
    }
}
