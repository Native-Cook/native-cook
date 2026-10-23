package com.ngocdevv.nativecook.blocks.navigation.collapsingplayerheader

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A run of the notes' text; a link is drawn in white. */
data class NoteSpan(val text: String, val link: Boolean = false)

val SampleNotes = listOf(
    listOf(NoteSpan("Every winter the longest ceremony of the season runs past midnight, and every winter somebody promises to fix it. This year the hosts, the caterers and one very tired stage manager let us follow them from the first rehearsal to the last trophy of the night.")),
    listOf(NoteSpan("We count the speeches, time the standing ovations and ask why the technical awards always land after eleven. The answer has less to do with television than with a seating chart nobody has dared to redraw in forty years.")),
    listOf(NoteSpan("In this episode: the rehearsal nobody attends, why the envelope is still made of paper, the orchestra’s polite way of ending a speech, and what happens to four thousand flowers at two in the morning.")),
    listOf(NoteSpan("Chapters: 00:00 The rehearsal. 06:10 Paper envelopes. 13:45 The seating chart. 21:30 The play-off music. 24:05 Four thousand flowers.")),
    listOf(NoteSpan("This episode was produced by Mara Lindqvist, edited by Tomas Oyelaran, fact-checked by Ines Carvalho, engineered by Dev Raman and hosted by Noor Halvorsen.")),
    listOf(NoteSpan("Transcript at "), NoteSpan("fieldnotes.example/longest-night", link = true)),
    listOf(NoteSpan("Support Field Notes Daily by becoming a member today: "), NoteSpan("fieldnotes.example/members", link = true)),
    listOf(NoteSpan("Photo: an empty ballroom on the morning after, with one silver balloon still resting against the ceiling. Photo by Jun Aoki for Field Notes.")),
    listOf(NoteSpan("Correction: an earlier version of these notes gave the running time as four hours and twelve minutes. It was four hours and twenty-one, not counting the encore.")),
    listOf(NoteSpan("Music in this episode by the Halvard Quartet. Additional reporting by Petra Voss and Sami Okafor. Special thanks to everyone who stayed until the lights came up.")),
    listOf(NoteSpan("Field Notes Daily is made in a small studio above a bakery. If an episode smells faintly of cinnamon, that is why. New episodes arrive every weekday morning.")),
    listOf(NoteSpan("Learn more about your ad choices. Visit "), NoteSpan("fieldnotes.example/adchoices", link = true)),
)

private val Page = Color(0xFF110E11)
private val Ink = Color.White
private val Mute = Color(0xFF8C8A8F)
private val Body = Color(0xFFB4B2B7)
private val Rule = Color(0xFF3A383C)
private val Played = Color(0xFFC9C7CC)
private val Button = Color(0xFF2A282C)
private val GlyphDim = Color(0xFF5C5A5F)
private val TabOff = Color(0xFF6C6A6F)

private const val MARGIN = 24f

// The open player's title block, from the safe area's top.
private const val DATE_TOP = 44f
private const val TITLE_SIZE = 40f
private const val TITLE_LINE = 52f

// The open player's lower half hangs from the notes' heading, which sits this far above the
// screen's bottom: the track and the transport row are this far above the heading's centre.
private const val HEADING_UP = 100f
private const val TRACK_ABOVE = 202f
private const val ROW_ABOVE = 95f

// The folded player, from the safe area's top: the mini row, the heading and the tabs under it.
private const val MINI_CENTER = 60f
private const val HEADING_CENTER = 135f
private const val TABS_BELOW = 31f
private const val HEADING_LINE = 20f

// From the heading's centre to the first line of the notes, open and folded (the tabs come between).
private const val NOTES_GAP = 16f
private const val NOTES_GAP_MINI = 57f
private const val NOTES_END = 24f
private const val PARAGRAPH_GAP = 10f
private const val TRACK = 4f

// In the mini row the track sits a little above the centre, with the times under it.
private const val TRACK_RISE = 7f
private const val LABEL_LINE = 16f
private const val LABEL_DROP = 20f
private const val LABEL_DROP_MINI = 16f
private const val LABEL_SHRINK = 11f / 12f
private const val COVER = 48f
private const val BIG = 76f
private const val SMALL = 40f
private const val SKIP = 30f
private const val SKIP_GAP = 85f
private const val HIT = 44f
private const val MARK_SPACE = 18f
private const val TAB_HEIGHT = 22f

// Where the track ends in the mini row: after the cover, before the button.
private const val MINI_LEFT = MARGIN + COVER + 18f
private const val MINI_RIGHT = MARGIN + SMALL + 24f

// The notes pass under the mini row: page colour down to here, then a fade.
private const val HEADER = MINI_CENTER + 34f
private const val HEADER_FADE = 24f

// The fold, as fractions of its travel. The title block leaves 1.2 times as fast as the notes rise.
private const val TITLE_SPEED = 1.2f
private const val TITLE_GONE = 0.35f
private const val SIDES_GONE = 0.15f
private const val SKIPS_GONE = 0.5f
private const val SQUEEZE_FROM = 0.3f
private const val SQUEEZE_TO = 0.8f
private const val COVER_FROM = 0.4f

// The mark beside the heading goes, then the heading slides into its place.
private const val MARK_FROM = 0.5f
private const val MARK_GONE = 0.7f
private const val SLIDE_FROM = 0.6f
private const val SLIDE_TO = 0.9f

// The track keeps its distance above the transport row until here, then comes down into it.
private const val DROP_FROM = 0.6f

// The notes make room under their heading, and the tabs fade into it.
private const val GAP_FROM = 0.6f
private const val GAP_TO = 0.9f
private const val TABS_FROM = 0.85f

// Let go past this much of the travel and the player folds; before it, it opens again.
private const val SNAP_AT = 0.4f

// Seconds of the release speed that count towards that choice.
private const val PROJECT = 0.15f

// The snap: a spring with a response of 0.35 s that passes its target by about 4 dp.
private const val OMEGA = (2 * PI / 0.35).toFloat()
private const val ZETA = 0.82f
private const val SETTLE = 0.7f

// The free scroll after a flick loses speed at this rate a second and stops below REST_SPEED.
private const val DECAY_RATE = 2f
private const val REST_SPEED = 4f
private const val RUBBER = 0.55f
private const val MORPH = 200

/** autoPlay: one fold and one return every LOOP seconds. */
const val LOOP = 9f

// A pull that speeds up, then the snap takes over, as when a finger flicks and lets go.
private const val FOLD_AT = 1f
private const val FOLD_PULL = 0.3f
private const val FOLD_TO = 0.42f

// The notes run on by this much, slowly.
private const val ON_AT = 2f
private const val ON_FOR = 1.2f
private const val ON_BY = 120f

// The way back: a pull down through the notes, let go halfway through the fold.
private const val BACK_AT = 5f
private const val BACK_PULL = 0.55f
private const val BACK_TO = 0.5f

/** After a touch autoPlay waits this long before it returns to the open player and starts over. */
private const val USER_HOLD = 3f

private const val IDLE = 0
private const val AUTO = 1
private const val SPRING = 2
private const val DECAY = 3

// The pause bars and the play triangle of the big button, each as two four-cornered shapes
// (x, y pairs: top left, top right, bottom right, bottom left) so one can turn into the other.
private val Pause = arrayOf(
    floatArrayOf(26.5f, 25f, 33.5f, 25f, 33.5f, 51f, 26.5f, 51f),
    floatArrayOf(42.5f, 25f, 49.5f, 25f, 49.5f, 51f, 42.5f, 51f),
)
private val Play = arrayOf(
    floatArrayOf(29f, 24f, 41f, 31f, 41f, 45f, 29f, 52f),
    floatArrayOf(41f, 31f, 53f, 38f, 53f, 38f, 41f, 45f),
)

// Glyphs in a 24 dp box.
private val Bookmark = Path().apply {
    moveTo(7f, 4.5f); lineTo(17f, 4.5f); lineTo(17f, 20f); lineTo(12f, 16f); lineTo(7f, 20f); close()
}

// Three hundred degrees of a circle from the top, clockwise; the arrowhead sits at its start.
private val SkipArc = Path().apply { arcTo(Rect(3.5f, 4.5f, 20.5f, 21.5f), -90f, 300f, true) }
private val SkipHead = Path().apply {
    moveTo(8.2f, 4.5f); lineTo(12.4f, 1.6f); lineTo(12.4f, 7.4f); close()
}
private val CastOuter = Path().apply { arcTo(Rect(4f, 3.5f, 20f, 19.5f), 150f, 240f, true) }
private val CastInner = Path().apply { arcTo(Rect(7.5f, 7f, 16.5f, 16f), 150f, 240f, true) }
private val CastBase = Path().apply {
    moveTo(12f, 14.5f); lineTo(16.5f, 20.5f); lineTo(7.5f, 20.5f); close()
}

// The cover's three lines of "text": left edge, top, width.
private val CoverLines = arrayOf(floatArrayOf(7f, 10f, 22f), floatArrayOf(7f, 17f, 28f), floatArrayOf(7f, 24f, 16f))

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun playerText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val DateStyle = playerText(14f, 18f, FontWeight.Normal, Mute)
private val ShowStyle = playerText(14f, 18f, FontWeight.SemiBold, Ink)
private val TitleStyle = playerText(TITLE_SIZE, TITLE_LINE, FontWeight.SemiBold, Ink, spacing = -0.6f)
private val TimeStyle = playerText(12f, LABEL_LINE, FontWeight.Normal, Mute)
private val HeadingStyle = playerText(15f, HEADING_LINE, FontWeight.SemiBold, Ink)
private val TabStyle = playerText(9f, 12f, FontWeight.SemiBold, TabOff, spacing = 0.5f)
private val TabOnStyle = TabStyle.copy(color = Ink)
private val ParagraphStyle = playerText(14f, 18f, FontWeight.Normal, Body)
private val SkipStyle = playerText(9.5f, 12f, FontWeight.Bold, Ink)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

private fun smooth(t: Float, from: Float, to: Float): Float {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3f - 2f * u)
}

/** Where a spring released at [from] with [speed] is after [t] seconds on its way to [to]. */
private fun springAt(t: Float, from: Float, to: Float, speed: Float): Float {
    val damped = OMEGA * sqrt(1f - ZETA * ZETA)
    val away = from - to
    val swing = (speed + ZETA * OMEGA * away) / damped
    return to + exp(-ZETA * OMEGA * t) * (away * cos(damped * t) + swing * sin(damped * t))
}

/** A pull past an end gives less and less. */
private fun rubber(over: Float, room: Float) = (1f - 1f / (over * RUBBER / room + 1f)) * room

/** The scroll offset autoPlay asks for [t] seconds into a loop; above [travel] before ON_AT is the snap's overshoot. */
private fun scriptAt(t: Float, travel: Float, end: Float): Float {
    val on = min(ON_BY, end - travel)
    if (t < FOLD_AT) return 0f
    if (t < FOLD_AT + FOLD_PULL) {
        val u = (t - FOLD_AT) / FOLD_PULL
        return FOLD_TO * travel * u * u
    }
    if (t < ON_AT) {
        val at = t - FOLD_AT - FOLD_PULL
        return if (at >= SETTLE) travel else springAt(at, FOLD_TO * travel, travel, 2f * FOLD_TO * travel / FOLD_PULL)
    }
    if (t < BACK_AT) return travel + on * smooth(t, ON_AT, ON_AT + ON_FOR)
    val from = travel + on
    val to = BACK_TO * travel
    if (t < BACK_AT + BACK_PULL) {
        val u = (t - BACK_AT) / BACK_PULL
        return from + (to - from) * u * u
    }
    val at = t - BACK_AT - BACK_PULL
    return if (at >= SETTLE) 0f else springAt(at, to, 0f, 2f * (to - from) / BACK_PULL)
}

private fun clockText(seconds: Float): String {
    val whole = max(0, seconds.roundToInt())
    return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
}

private fun annotated(paragraph: List<NoteSpan>): AnnotatedString = buildAnnotatedString {
    paragraph.forEach { span -> if (span.link) withStyle(SpanStyle(color = Ink)) { append(span.text) } else append(span.text) }
}

/** The screen's measures in dp: centres of the notes' heading and of the transport row, open and folded. */
private data class Measures(val width: Float, val height: Float, val top: Float, val bottom: Float, val notesHeight: Float) {
    val headingOpen = height - HEADING_UP
    val headingMini = top + HEADING_CENTER
    val rowOpen = headingOpen - ROW_ABOVE
    val rowMini = top + MINI_CENTER

    // The scroll that folds the player, and the offset at which the notes end.
    val travel = headingOpen - headingMini
    val end = travel + max(0f, headingMini + NOTES_GAP_MINI + notesHeight + NOTES_END + bottom - height)
}

/** What moves the scroll offset when no finger does, and the motion it is in. Times are in seconds of the frame clock; a negative one is set on the next frame. */
private class Driver(position: Float) {
    var held = false
    var dragging = false
    var grabbed = 0f
    var travelled = 0f
    var mode = IDLE
    var from = 0f
    var to = 0f
    var speed = 0f
    var since = -1.0
    var origin = -1.0
    var idleAt = 0.0
    var lap = -1L

    // Playback: the position at `baseAt`, running on from there.
    var base = position
    var baseAt = -1.0
}

/**
 * A podcast screen whose top is a full player: date, show, a large title, a progress track and
 * the transport row. Scrolling the notes folds it into a one-line mini player that stays as the
 * header: the track travels up and narrows, the cover grows in at its left, the big button shrinks
 * to its right. Everything is placed from one number, the scroll offset.
 *
 * @param title The episode's title; a line break is kept.
 * @param duration The episode's length, in seconds.
 * @param position Where playback starts, in seconds.
 * @param tabs The two tabs over the notes: the first shows them, the second only their links.
 * @param notes The notes, a list of paragraphs.
 * @param accent The colour of the cover and of the mark beside the notes' heading.
 * @param autoPlay Scrolls the notes by itself every [LOOP] seconds: the player folds, the notes run on, and it opens again.
 * @param onSeek Called with the new position, in seconds, when a skip button is pressed.
 */
@Composable
fun CollapsingPlayerHeader(
    date: String = "Yesterday",
    show: String = "Field Notes Daily",
    title: String = "The longest night\nof the awards\nseason",
    duration: Float = 1706f,
    position: Float = 519f,
    notesTitle: String = "Episode Notes",
    tabs: List<String> = listOf("Description", "Links"),
    notes: List<List<NoteSpan>> = SampleNotes,
    accent: Color = Color(0xFFF2E94E),
    autoPlay: Boolean = false,
    onPlayingChange: ((playing: Boolean) -> Unit)? = null,
    onSeek: ((position: Float) -> Unit)? = null,
    onCollapsedChange: ((collapsed: Boolean) -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    // The frame loop and the gestures outlive a recomposition: they read what the caller passes through these.
    val auto by rememberUpdatedState(autoPlay)
    val length by rememberUpdatedState(duration)
    val startsAt by rememberUpdatedState(position)
    val folded by rememberUpdatedState(onCollapsedChange)

    var playing by remember { mutableStateOf(true) }
    // The position shown, in whole seconds.
    var seconds by remember { mutableIntStateOf(position.toInt()) }
    var tab by remember { mutableIntStateOf(0) }
    var notesHeight by remember { mutableFloatStateOf(0f) }
    // Something other than playback is in motion and needs the frame clock.
    var moving by remember { mutableStateOf(false) }
    // The notes' scroll offset in dp: below 0 and past the end while a pull stretches it. Read while placing and drawing only.
    val scroll = remember { mutableFloatStateOf(0f) }
    // How far the snap went past the folded player: the whole screen rides up by this.
    val lift = remember { mutableFloatStateOf(0f) }
    val driver = remember { Driver(position) }
    // 0 is the pause glyph, 1 the play glyph.
    val glyph = animateFloatAsState(if (playing) 0f else 1f, tween(if (reducedMotion) 0 else MORPH), label = "glyph")
    val glyphPath = remember { Path() }

    BoxWithConstraints(modifier.fillMaxSize().background(Page).clipToBounds()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val measures by rememberUpdatedState(
            Measures(maxWidth.value, maxHeight.value, insets.calculateTopPadding().value, insets.calculateBottomPadding().value, notesHeight),
        )

        /** How far the player is folded, 0 to 1. Reduced motion shows one layout or the other. */
        fun progress(): Float {
            val p = clamp01(scroll.floatValue / measures.travel)
            return if (reducedMotion) (if (p >= SNAP_AT) 1f else 0f) else p
        }

        /** A pull at the top carries the whole screen down; the snap's overshoot carries it up. */
        fun shift() = -min(scroll.floatValue, 0f) - lift.floatValue
        fun row() = mix(measures.rowOpen, measures.rowMini, progress()) + shift()
        fun track() = row() - mix(TRACK_ABOVE - ROW_ABOVE, TRACK_RISE, smooth(progress(), DROP_FROM, 1f))
        fun buttonX() = mix(measures.width / 2f, measures.width - MARGIN - SMALL / 2f, smooth(progress(), 0f, 1f))
        fun squeeze() = smooth(progress(), SQUEEZE_FROM, SQUEEZE_TO)
        fun room() = smooth(progress(), GAP_FROM, GAP_TO)
        fun sheet() = measures.headingOpen - progress() * measures.travel - max(0f, scroll.floatValue - measures.travel) + shift() - HEADING_LINE / 2f

        fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        fun springTo(target: Float, velocity: Float) {
            if (reducedMotion) {
                scroll.floatValue = target
                lift.floatValue = 0f
                driver.mode = IDLE
                return
            }
            driver.from = scroll.floatValue + lift.floatValue
            driver.to = target
            driver.speed = velocity
            driver.since = -1.0
            driver.mode = SPRING
        }

        /** The finger left with [velocity] (dp a second, positive towards the notes' end). */
        fun release(velocity: Float) {
            val at = scroll.floatValue
            val travel = measures.travel
            val end = measures.end
            when {
                at < travel -> {
                    val target = if (at + velocity * PROJECT > SNAP_AT * travel) travel else 0f
                    if (at == target) driver.mode = IDLE else springTo(target, velocity)
                }
                at > end -> springTo(end, 0f)
                abs(velocity) < REST_SPEED -> driver.mode = IDLE
                else -> {
                    driver.from = at
                    driver.speed = velocity
                    driver.since = -1.0
                    driver.mode = DECAY
                }
            }
        }

        fun step(now: Double) {
            val travel = measures.travel
            val end = measures.end
            if (driver.idleAt < 0) driver.idleAt = now
            if (!driver.held) {
                when (driver.mode) {
                    AUTO -> {
                        if (driver.origin < 0) driver.origin = now
                        val elapsed = now - driver.origin
                        val n = floor(elapsed / LOOP).toLong()
                        if (n != driver.lap) {
                            // Every loop plays the same stretch of the episode.
                            driver.lap = n
                            driver.base = startsAt
                            driver.baseAt = driver.origin + n * LOOP
                        }
                        val t = (elapsed - n * LOOP).toFloat()
                        val value = scriptAt(t, travel, end)
                        val riding = t < ON_AT
                        scroll.floatValue = if (riding) min(value, travel) else value
                        lift.floatValue = if (riding) max(0f, value - travel) else 0f
                    }
                    SPRING -> {
                        if (driver.since < 0) driver.since = now
                        val t = (now - driver.since).toFloat()
                        val value = if (t >= SETTLE) driver.to else springAt(t, driver.from, driver.to, driver.speed)
                        // Past the folded player the notes do not scroll on: the screen rides up instead.
                        val riding = driver.to == travel
                        scroll.floatValue = if (riding) min(value, travel) else value
                        lift.floatValue = if (riding) max(0f, value - travel) else 0f
                        if (t >= SETTLE) driver.mode = IDLE
                    }
                    DECAY -> {
                        if (driver.since < 0) driver.since = now
                        val fall = exp(-DECAY_RATE * (now - driver.since).toFloat())
                        val value = driver.from + driver.speed / DECAY_RATE * (1f - fall)
                        val inside = value.coerceIn(travel, end)
                        scroll.floatValue = inside
                        if (inside != value || abs(driver.speed) * fall < REST_SPEED) driver.mode = IDLE
                    }
                    else -> if (auto && now - driver.idleAt >= USER_HOLD) {
                        if (scroll.floatValue != 0f) {
                            springTo(0f, 0f)
                        } else {
                            // autoPlay starts over: playback and the tabs are as they were given.
                            playing = true
                            tab = 0
                            driver.origin = now
                            driver.lap = -1
                            driver.mode = AUTO
                        }
                    } else if (!auto) {
                        moving = false
                    }
                }
            }

            if (driver.baseAt < 0) driver.baseAt = now
            val current = driver.base + if (playing) (now - driver.baseAt).toFloat() else 0f
            val second = min(length, floor(current + 0.001f)).toInt()
            if (second != seconds) seconds = second
        }

        val active = autoPlay || playing || moving
        LaunchedEffect(active) {
            if (!active) return@LaunchedEffect
            while (true) step(withFrameNanos { it } / 1e9)
        }

        LaunchedEffect(autoPlay) {
            if (autoPlay) {
                scroll.floatValue = 0f
                lift.floatValue = 0f
                driver.origin = -1.0
                driver.lap = -1
                driver.mode = AUTO
            } else if (driver.mode == AUTO) {
                driver.mode = IDLE
            }
        }

        val collapsed by remember { derivedStateOf { progress() > 0.5f } }
        val told = remember { mutableStateOf(false) }
        LaunchedEffect(collapsed) {
            if (collapsed != told.value) {
                told.value = collapsed
                folded?.invoke(collapsed)
            }
        }

        fun expand() {
            moving = true
            springTo(0f, 0f)
        }

        fun toggle() {
            val next = !playing
            tick()
            driver.base = seconds.toFloat()
            driver.baseAt = -1.0
            playing = next
            onPlayingChange?.invoke(next)
        }

        fun skip(by: Float) {
            val next = (seconds + by).coerceIn(0f, length)
            tick()
            driver.base = next
            driver.baseAt = -1.0
            onSeek?.invoke(next)
        }

        fun choose(index: Int) {
            if (index == tab) return
            tick()
            // The other list starts at its top.
            if (scroll.floatValue > measures.travel) scroll.floatValue = measures.travel
            tab = index
        }

        val drag = rememberDraggableState { delta ->
            driver.travelled += delta / density
            val wanted = driver.grabbed - driver.travelled
            val end = measures.end
            lift.floatValue = 0f
            scroll.floatValue = when {
                wanted < 0f -> -rubber(-wanted, measures.height)
                wanted > end -> end + rubber(wanted - end, measures.height)
                else -> wanted
            }
        }

        // The second tab lists the links of the notes, one to a line.
        val shownNotes = remember(notes, tab) {
            (if (tab == 0) notes else notes.flatMap { paragraph -> paragraph.filter { it.link }.map { listOf(it) } }).map(::annotated)
        }

        Box(
            Modifier
                .matchParentSize()
                // Seen before the drag is: a touch takes over at once, wherever the offset is, and a
                // touch that never becomes a drag still has to leave the player open or folded.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        driver.held = true
                        driver.mode = IDLE
                        moving = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        driver.held = false
                        driver.idleAt = -1.0
                        if (!driver.dragging && driver.mode == IDLE) release(0f)
                    }
                }
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        driver.dragging = true
                        driver.travelled = 0f
                        driver.grabbed = scroll.floatValue.coerceIn(0f, measures.end)
                    },
                    onDragStopped = { velocity ->
                        driver.dragging = false
                        release(-velocity / density)
                    },
                ),
        ) {
            // What scrolls: the heading, the tabs that appear under it, and the notes.
            Row(
                Modifier
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    .height(HEADING_LINE.dp)
                    .graphicsLayer { translationY = sheet().dp.toPx() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp, 14.dp)
                        .graphicsLayer { alpha = 1f - smooth(progress(), MARK_FROM, MARK_GONE) }
                        .background(accent, RoundedCornerShape(2.5.dp)),
                )
                Spacer(Modifier.width((MARK_SPACE - 10f).dp))
                BasicText(
                    notesTitle,
                    style = HeadingStyle,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { translationX = -MARK_SPACE.dp.toPx() * smooth(progress(), SLIDE_FROM, SLIDE_TO) }
                        .semantics { heading() },
                )
                GlyphButton("More", enabled = true, onClick = { onMore?.invoke() }, box = 24f) {
                    listOf(5f, 12f, 19f).forEach { drawCircle(Mute, 1.6f, Offset(it, 12f)) }
                }
            }

            Row(
                Modifier
                    .padding(horizontal = MARGIN.dp)
                    .graphicsLayer {
                        translationY = (sheet() + HEADING_LINE / 2f + TABS_BELOW - TAB_HEIGHT / 2f).dp.toPx()
                        alpha = smooth(progress(), TABS_FROM, 1f)
                    },
            ) {
                tabs.forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .height(TAB_HEIGHT.dp)
                            .background(if (index == tab) Button else Color.Transparent, RoundedCornerShape(5.dp))
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = collapsed, role = Role.Tab) { choose(index) }
                            .semantics { selected = index == tab }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(label.uppercase(), style = if (index == tab) TabOnStyle else TabStyle, maxLines = 1)
                    }
                }
            }

            Column(
                Modifier
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    // The notes are taller than the screen: they are measured without its limit.
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { notesHeight = it.height / density }
                    .graphicsLayer { translationY = (sheet() + HEADING_LINE / 2f + mix(NOTES_GAP, NOTES_GAP_MINI, room())).dp.toPx() },
            ) {
                shownNotes.forEachIndexed { index, paragraph ->
                    BasicText(paragraph, style = ParagraphStyle, modifier = Modifier.padding(top = if (index > 0) PARAGRAPH_GAP.dp else 0.dp))
                }
            }

            // The notes pass under the mini row: page colour, then a fade.
            Box(
                Modifier.fillMaxWidth().height((measures.top + HEADER + HEADER_FADE).dp).drawBehind {
                    val solid = size.height - HEADER_FADE.dp.toPx()
                    drawRect(Page, size = Size(size.width, solid))
                    drawRect(
                        Brush.verticalGradient(listOf(Page, Page.copy(alpha = 0f)), startY = solid, endY = size.height),
                        topLeft = Offset(0f, solid),
                        size = Size(size.width, size.height - solid),
                    )
                },
            )

            Column(
                Modifier
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (measures.top + DATE_TOP - TITLE_SPEED * progress() * measures.travel + shift()).dp.toPx()
                        val left = 1f - clamp01(progress() / TITLE_GONE)
                        alpha = left * left
                    },
            ) {
                BasicText(date, style = DateStyle, maxLines = 1)
                BasicText(show, style = ShowStyle, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                BasicText(title, style = TitleStyle, maxLines = 3, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
            }

            if (collapsed) {
                Box(
                    Modifier
                        .offset(MARGIN.dp, (measures.rowMini - COVER / 2f).dp)
                        .size((measures.width - MARGIN - MINI_RIGHT).dp, COVER.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { expand() }
                        .semantics { contentDescription = "$show. Open the player" },
                )
            }

            // The track, its played part, and the cover that grows in at its left.
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val p = progress()
                        val left = mix(MARGIN, MINI_LEFT, squeeze()).dp.toPx()
                        val right = mix(measures.width - MARGIN, measures.width - MINI_RIGHT, squeeze()).dp.toPx()
                        val height = TRACK.dp.toPx()
                        val top = track().dp.toPx() - height / 2f
                        val round = CornerRadius(height / 2f)
                        drawRoundRect(Rule, Offset(left, top), Size(right - left, height), round)
                        drawRoundRect(Played, Offset(left, top), Size((right - left) * min(1f, seconds / max(1f, length)), height), round)

                        val grown = smooth(p, COVER_FROM, 1f)
                        val side = COVER.dp.toPx()
                        scale(mix(0.3f, 1f, grown), Offset(MARGIN.dp.toPx() + side / 2f, row().dp.toPx())) {
                            val corner = Offset(MARGIN.dp.toPx(), row().dp.toPx() - side / 2f)
                            drawRoundRect(accent, corner, Size(side, side), CornerRadius(5.dp.toPx()), alpha = grown)
                            CoverLines.forEach { (x, y, wide) ->
                                drawRoundRect(
                                    Page,
                                    corner + Offset(x.dp.toPx(), y.dp.toPx()),
                                    Size(wide.dp.toPx(), 3.5.dp.toPx()),
                                    CornerRadius(1.dp.toPx()),
                                    alpha = grown,
                                )
                            }
                        }
                    }
                    .clearAndSetSemantics { contentDescription = "${clockText(seconds.toFloat())} of ${clockText(length)}" },
            )
            BasicText(
                clockText(seconds.toFloat()),
                style = TimeStyle,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = mix(MARGIN, MINI_LEFT, squeeze()).dp.toPx()
                    translationY = (track() + mix(LABEL_DROP, LABEL_DROP_MINI, progress()) - LABEL_LINE / 2f).dp.toPx()
                    scaleX = mix(1f, LABEL_SHRINK, progress())
                    scaleY = scaleX
                },
            )
            BasicText(
                "-" + clockText(length - seconds),
                style = TimeStyle,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).graphicsLayer {
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    translationX = -mix(MARGIN, MINI_RIGHT, squeeze()).dp.toPx()
                    translationY = (track() + mix(LABEL_DROP, LABEL_DROP_MINI, progress()) - LABEL_LINE / 2f).dp.toPx()
                    scaleX = mix(1f, LABEL_SHRINK, progress())
                    scaleY = scaleX
                },
            )

            // Gone by the time the player is half folded, and then out of the way of a tap on the mini row.
            if (!collapsed) {
                // The glyphs' outer edges, not their boxes, stand on the margins.
                GlyphButton(
                    "Bookmark",
                    enabled = true,
                    onClick = { onBookmark?.invoke() },
                    layer = {
                        alpha = 1f - clamp01(progress() / SIDES_GONE)
                        translationX = (MARGIN - 17f).dp.toPx()
                        translationY = (row() - HIT / 2f).dp.toPx()
                    },
                ) {
                    drawPath(Bookmark, GlyphDim, style = Stroke(1.6f, join = StrokeJoin.Round))
                }
                GlyphButton(
                    "Play on another device",
                    enabled = true,
                    onClick = { onCast?.invoke() },
                    layer = {
                        alpha = 1f - clamp01(progress() / SIDES_GONE)
                        translationX = (measures.width - MARGIN + 15f - HIT).dp.toPx()
                        translationY = (row() - HIT / 2f).dp.toPx()
                    },
                ) {
                    drawPath(CastOuter, GlyphDim, style = Stroke(1.6f, cap = StrokeCap.Round))
                    drawPath(CastInner, GlyphDim, style = Stroke(1.6f, cap = StrokeCap.Round))
                    drawPath(CastBase, GlyphDim, style = Fill)
                    drawPath(CastBase, GlyphDim, style = Stroke(1f, join = StrokeJoin.Round))
                }

                listOf(-1f, 1f).forEach { side ->
                    GlyphButton(
                        if (side < 0f) "Back 15 seconds" else "Forward 15 seconds",
                        enabled = true,
                        onClick = { skip(15f * side) },
                        glyph = SKIP,
                        label = "15",
                        layer = {
                            val gone = clamp01(progress() / SKIPS_GONE)
                            alpha = 1f - gone
                            translationX = (buttonX() + side * SKIP_GAP * mix(1f, 0.6f, gone) - HIT / 2f).dp.toPx()
                            translationY = (row() - HIT / 2f).dp.toPx()
                            scaleX = mix(1f, 0.8f, gone)
                            scaleY = scaleX
                        },
                    ) {
                        // A circular arrow: back when it turns to the left, forward when mirrored.
                        scale(-side, 1f, Offset(12f, 12f)) {
                            drawPath(SkipArc, Ink, style = Stroke(1.7f, cap = StrokeCap.Round))
                            drawPath(SkipHead, Ink)
                        }
                    }
                }
            }

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Canvas(
                Modifier
                    .graphicsLayer {
                        translationX = (buttonX() - BIG / 2f).dp.toPx()
                        translationY = (row() - BIG / 2f).dp.toPx()
                        scaleX = mix(1f, SMALL / BIG, smooth(progress(), 0f, 1f))
                        scaleY = scaleX
                        alpha = if (pressed) 0.6f else 1f
                    }
                    .size(BIG.dp)
                    .clickable(interaction, indication = null, role = Role.Button) { toggle() }
                    .semantics { contentDescription = if (playing) "Pause" else "Play" },
            ) {
                drawCircle(Button)
                val m = glyph.value
                glyphPath.rewind()
                for (shape in 0 until 2) {
                    for (corner in 0 until 8 step 2) {
                        val x = mix(Pause[shape][corner], Play[shape][corner], m)
                        val y = mix(Pause[shape][corner + 1], Play[shape][corner + 1], m)
                        if (corner == 0) glyphPath.moveTo(x, y) else glyphPath.lineTo(x, y)
                    }
                    glyphPath.close()
                }
                scale(size.width / BIG, Offset.Zero) {
                    drawPath(glyphPath, Ink)
                    drawPath(glyphPath, Ink, style = Stroke(1.5f, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/**
 * A glyph drawn in a 24-unit box at [glyph] dp, in a [box] dp square that takes the touch and dims
 * while pressed; [layer] places it. [label] is written over the glyph's middle.
 */
@Composable
private fun GlyphButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    box: Float = HIT,
    glyph: Float = 24f,
    label: String? = null,
    layer: GraphicsLayerScope.() -> Unit = {},
    draw: DrawScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .graphicsLayer {
                layer()
                if (pressed) alpha *= 0.6f
            }
            .size(box.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(glyph.dp)) { scale(size.width / 24f, Offset.Zero) { draw() } }
        // The glyph's middle sits a little under the box's.
        if (label != null) BasicText(label, style = SkipStyle, modifier = Modifier.offset(y = (glyph / 24f).dp).clearAndSetSemantics {})
    }
}
