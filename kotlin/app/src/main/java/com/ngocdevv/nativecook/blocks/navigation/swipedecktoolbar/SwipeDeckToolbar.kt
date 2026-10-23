package com.ngocdevv.nativecook.blocks.navigation.swipedecktoolbar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

data class Profile(val name: String, val note: String, val photo: String)

data class PageRow(val title: String, val note: String)

data class Page(val title: String, val rows: List<PageRow>)

private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=626&h=840&q=80"

val SampleProfiles = listOf(
    Profile("Lena, 27", "2 km away", photo("photo-1544005313-94ddf0286df2")),
    Profile("Theo, 31", "5 km away", photo("photo-1506794778202-cad84cf45f1d")),
    Profile("Mira, 29", "1 km away", photo("photo-1517841905240-472988babdf9")),
)

val SamplePages = listOf(
    Page(
        "Profile",
        listOf(
            PageRow("Edit profile", "Photos, bio and interests"),
            PageRow("Discovery", "Within 10 km, ages 24 to 34"),
            PageRow("Notifications", "Matches and messages"),
            PageRow("Privacy", "Who can see you"),
        ),
    ),
    Page(
        "Recent",
        listOf(
            PageRow("Lena", "Passed 2 minutes ago"),
            PageRow("Theo", "Liked an hour ago"),
            PageRow("Mira", "Liked yesterday"),
            PageRow("Jonas", "Passed on Monday"),
        ),
    ),
    Page(
        "Likes",
        listOf(
            PageRow("Ada likes you", "3 km away"),
            PageRow("Noor likes you", "6 km away"),
            PageRow("Iris likes you", "2 km away"),
            PageRow("Sam likes you", "9 km away"),
        ),
    ),
)

private val PageColor = Color(0xFF0B0D0E)
private val Glow = Color(0xFF2C5C82)
private val GlowFar = Color(0xFF172836)

// The glow is centred low on the screen, so that the page is at its bluest behind the band's valley.
private const val GLOW_X = 0.55f
private const val GLOW_Y = 0.78f
private const val GLOW_MID = 260f
private const val GLOW_END = 600f

// The deck's tab is the last of the four.
private const val DECK = 3
private const val MARGIN = 24f
private const val TITLE_TOP = 22f
private const val ROW = 72f
private const val ROW_GAP = 12f
private const val CARD_WIDTH = 313f
private const val CARD_HEIGHT = 420f
private const val CARD_RADIUS = 28f

// Each card behind the top one is this much lower, smaller and darker.
private const val BEHIND = 2
private const val STACK_STEP = 12f
private const val STACK_SHRINK = 0.05f
private const val STACK_DIM = 0.3f

// Degrees of tilt for each dp the top card is pulled.
private const val TILT = 1f / 18f

// A card let go this far from its place leaves; the verdict is lit fully a little further.
private const val THRESHOLD = 90f
private const val FULL = 120f
private const val TARGET = 64f

const val LOOP = 10f

// Mass 1 with stiffness 180 and damping 20, stiffness 260 and damping 26, stiffness 300 and damping 26.
private val ValleySpring = spring(dampingRatio = 0.7454f, stiffness = 180f, visibilityThreshold = 0.001f)
private val RingSpring = spring(dampingRatio = 0.8062f, stiffness = 260f, visibilityThreshold = 0.001f)
private val BackSpring = spring<Float>(dampingRatio = 0.7506f, stiffness = 300f)

// A card leaves in FLY seconds, gathering speed; the cards behind move up meanwhile.
private const val FLY = 0.4f
private const val PAGE_FADE = 0.2f
private val Fade = tween<Float>((PAGE_FADE * 1000).toInt(), easing = LinearEasing)
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// The loop starts again this long after the last touch.
private const val USER_HOLD = 3f

// The autoPlay loop pulls a card this far over PULL seconds and lets go, presses tabs, and puts
// the first card back on top while the deck is out of sight.
private const val PULL_TO = 130f
private const val PULL = 0.8f

private class Step(val at: Float, val pull: Float? = null, val tab: Int? = null)

private val Script = listOf(
    Step(1.0f, pull = -1f),
    Step(3.0f, pull = 1f),
    Step(5.2f, tab = 0),
    Step(5.8f),
    Step(6.2f, tab = 1),
    Step(7.0f, tab = 2),
    Step(8.0f, tab = DECK),
)

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun deckText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = deckText(34f, 41f, FontWeight.SemiBold, Color.White)
private val RowTitleStyle = deckText(17f, 22f, FontWeight.SemiBold, Color.White)
private val RowNoteStyle = deckText(14f, 18f, FontWeight.Normal, Color.White.copy(alpha = 0.55f))
private val NameStyle = deckText(26f, 32f, FontWeight.SemiBold, Color.White)
private val NoteStyle = deckText(15f, 20f, FontWeight.Normal, Color.White.copy(alpha = 0.7f))

/**
 * A swipe deck with a toolbar that changes shape for it. On the deck's tab the band's top edge
 * sinks into a valley and the two middle icons meet there as a cross and a heart in a glass
 * capsule; dragging the top card lights the side it leans to. On the other tabs the band is flat
 * and a ring slides to the tab that was pressed.
 *
 * @param profiles The people of the deck, top card first; they come round again after the last.
 * @param pages The screens of the first three tabs: profile, recent, likes. The fourth tab is the deck.
 * @param autoPlay Swipes two cards and goes through the tabs by itself every [LOOP] seconds.
 * @param onVerdict Called when a card has left the deck, with the verdict on it.
 */
@Composable
fun SwipeDeckToolbar(
    profiles: List<Profile> = SampleProfiles,
    pages: List<Page> = SamplePages,
    accent: Color = Color(0xFF4FA3E0),
    likeColor: Color = Color(0xFF2FA56C),
    rejectColor: Color = Color(0xFFB5304A),
    autoPlay: Boolean = false,
    onSelectTab: ((index: Int) -> Unit)? = null,
    onVerdict: ((profile: Profile, liked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The gesture and the loop outlive a recomposition: they read what the caller passes through these.
    val people by rememberUpdatedState(profiles)
    val onTab by rememberUpdatedState(onSelectTab)
    val onJudged by rememberUpdatedState(onVerdict)

    var tab by remember { mutableIntStateOf(DECK) }
    // The place in the endless deck of the card on top.
    var first by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The autoPlay loop makes no haptics; a touch does.
    var byHand by remember { mutableStateOf(false) }

    // 0 flat, 1 with the valley open.
    val valley = remember { Animatable(1f) }
    val ring = remember { Animatable(DECK.toFloat()) }
    var ringFrom by remember { mutableFloatStateOf(DECK.toFloat()) }
    var ringTo by remember { mutableFloatStateOf(DECK.toFloat()) }
    val deck = remember { Animatable(1f) }
    // How far a finger has pulled the top card.
    val pull = remember { Animatable(0f) }
    // A card that is leaving: 0 to 1, from where it was let go, toward a side (-1 or 1).
    val fly = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(false) }
    var flyFrom by remember { mutableFloatStateOf(0f) }
    var side by remember { mutableFloatStateOf(0f) }
    var flight by remember { mutableStateOf<Job?>(null) }
    // The autoPlay loop is pulling the card; a finger has it.
    var pulling by remember { mutableStateOf<Job?>(null) }
    var grabbed by remember { mutableStateOf(false) }

    /** The top card leaves to a side, and the next one is on top when it has gone. */
    fun decide(to: Float) {
        if (flying) return
        flying = true
        flyFrom = pull.value
        side = to
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        flight = scope.launch {
            pull.stop()
            fly.snapTo(0f)
            fly.animateTo(1f, tween((FLY * 1000).toInt(), easing = LinearEasing))
            val place = first
            first = place + 1
            pull.snapTo(0f)
            fly.snapTo(0f)
            flying = false
            onJudged?.invoke(people[place % people.size], to > 0f)
        }
    }

    /** A card let go: off to the side it was pulled to, or back to its place. */
    fun release() {
        val x = pull.value
        if (abs(x) > THRESHOLD) decide(sign(x)) else scope.launch { pull.animateTo(0f, BackSpring) }
    }

    fun select(index: Int) {
        if (index == tab) return
        val open = if (index == DECK) 1f else 0f
        ringFrom = ring.value
        ringTo = index.toFloat()
        scope.launch { if (reducedMotion) ring.snapTo(index.toFloat()) else ring.animateTo(index.toFloat(), RingSpring) }
        scope.launch { valley.animateTo(open, if (reducedMotion) Fade else ValleySpring) }
        scope.launch { deck.animateTo(open, Fade) }
        tab = index
        onTab?.invoke(index)
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        byHand = true
        pulling?.let {
            it.cancel()
            pulling = null
            scope.launch { pull.animateTo(0f, BackSpring) }
        }
        touches++
    }

    fun press(index: Int) {
        touched()
        if (index == tab) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        select(index)
    }

    /** The first card is on top again. */
    fun restack() {
        flight?.cancel()
        flying = false
        scope.launch {
            fly.snapTo(0f)
            pull.snapTo(0f)
        }
        first = 0
    }

    /** The loop does what a finger does, through the same functions. */
    fun perform(step: Step) {
        when {
            step.pull != null -> pulling = scope.launch {
                pull.animateTo(step.pull * PULL_TO, tween((PULL * 1000).toInt(), easing = EaseInOut))
                pulling = null
                release()
            }
            step.tab != null -> select(step.tab)
            else -> restack()
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // A finger that still has the card starts the wait again when it lets go.
            if (grabbed) return@LaunchedEffect
            // The loop starts over on the deck with the first card on top.
            byHand = false
            restack()
            select(DECK)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for (step in Script) {
                until(step.at)
                perform(step)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val paints = remember(rejectColor, likeColor) { BandPaints(rejectColor, likeColor) }
    val onDeck = tab == DECK

    BoxWithConstraints(
        modifier.fillMaxSize().background(PageColor).drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to Glow,
                    GLOW_MID / GLOW_END to GlowFar,
                    1f to PageColor,
                    center = Offset(size.width * GLOW_X, size.height * GLOW_Y),
                    radius = GLOW_END.dp.toPx(),
                ),
            )
        },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val width = maxWidth.value
        val bandHeight = BAND_HEIGHT + max(BAND_INSET, insets.calculateBottomPadding().value)
        // The deck stands a little above the middle of the room over the band.
        val room = maxHeight.value - top - bandHeight
        val deckHeight = CARD_HEIGHT + BEHIND * STACK_STEP
        val deckTop = top + max(12f, (room - deckHeight) * 0.4f)
        val away = width + 80f

        /** Where the top card is: under the finger, or on its way off the screen. */
        fun cardX(): Float {
            val q = fly.value
            if (q <= 0f) return pull.value
            // Without movement the card stays where it was let go and fades.
            return if (reducedMotion) flyFrom else flyFrom + (side * away - flyFrom) * q * q
        }

        // A new tab's screen fades in over the old one; the deck stays, so its photos do too.
        Crossfade(tab, Modifier.padding(top = (top + TITLE_TOP).dp, start = MARGIN.dp, end = MARGIN.dp), animationSpec = Fade, label = "page") { shown ->
            if (shown != DECK) PageView(pages[min(shown, pages.lastIndex)])
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = deckTop.dp)
                .size(CARD_WIDTH.dp, deckHeight.dp)
                .graphicsLayer { alpha = deck.value }
                // A hidden deck takes no touches.
                .then(
                    if (!onDeck) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    grabbed = true
                                    touched()
                                },
                                onDragEnd = {
                                    grabbed = false
                                    release()
                                    touched()
                                },
                                onDragCancel = {
                                    grabbed = false
                                    release()
                                    touched()
                                },
                            ) { change, amount ->
                                change.consume()
                                if (!flying) scope.launch { pull.snapTo(pull.value + amount / density) }
                            }
                        }
                    },
                ),
        ) {
            // The last card is drawn first, so that the top one lies over the others.
            for (n in BEHIND + 1 downTo 0) {
                val place = first + n
                key(place) {
                    DeckCard(
                        profiles[place % profiles.size],
                        dim = {
                            val behind = place - first
                            if (behind <= 0) 0f else max(0f, behind - if (reducedMotion) 0f else easeOut(fly.value)) * STACK_DIM
                        },
                        Modifier.graphicsLayer {
                            val behind = place - first
                            val q = fly.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            if (behind == 0) {
                                val x = cardX()
                                translationX = x.dp.toPx()
                                rotationZ = x * TILT
                                alpha = if (reducedMotion) 1f - q else 1f
                            } else {
                                // The cards behind move up one place while the top one leaves; a new one fades in at the back.
                                val depth = behind - if (reducedMotion) 0f else easeOut(q)
                                translationY = (depth * STACK_STEP).dp.toPx()
                                scaleX = 1f - depth * STACK_SHRINK
                                scaleY = scaleX
                                alpha = if (behind < 0) 0f else if (behind > BEHIND) q else 1f
                            }
                        },
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(bandHeight.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawToolbarBand(
                    valley = valley.value,
                    ring = ring.value,
                    ringFrom = ringFrom,
                    ringTo = ringTo,
                    cardX = cardX(),
                    fly = fly.value,
                    side = side,
                    full = FULL,
                    accent = accent,
                    likeColor = likeColor,
                    rejectColor = rejectColor,
                    paints = paints,
                    still = reducedMotion,
                )
            }
            for (i in 0..DECK) {
                // On the deck the two middle tabs have become the cross and the heart.
                if (onDeck && i != 0 && i != DECK) continue
                Box(
                    Modifier
                        .offset(x = (iconX(i.toFloat(), width) - TARGET / 2f).dp, y = (ICON_Y - TARGET / 2f).dp)
                        .size(TARGET.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { press(i) }
                        .semantics {
                            contentDescription = if (i < pages.size) pages[i].title else "Deck"
                            selected = i == tab
                        },
                )
            }
            if (onDeck) {
                val capsuleLeft = width / 2f - CAPSULE_WIDTH / 2f
                for (to in listOf(-1f, 1f)) {
                    Box(
                        Modifier
                            .offset(
                                x = ((if (to < 0f) capsuleLeft + MARK_INSET else capsuleLeft + CAPSULE_WIDTH - MARK_INSET) - TARGET / 2f).dp,
                                y = (ICON_Y - CAPSULE_HEIGHT / 2f).dp,
                            )
                            .size(TARGET.dp, CAPSULE_HEIGHT.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                touched()
                                decide(to)
                            }
                            .semantics { contentDescription = if (to < 0f) "Pass" else "Like" },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageView(page: Page) {
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        BasicText(page.title, Modifier.padding(bottom = (24f - ROW_GAP).dp), style = TitleStyle, maxLines = 1)
        for (row in page.rows) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(ROW.dp)
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(row.title, style = RowTitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(row.note, Modifier.padding(top = 2.dp), style = RowNoteStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A card of the deck: a photo with a name and one line over a shade, and [dim] of black on top. */
@Composable
private fun DeckCard(profile: Profile, dim: () -> Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(profile.photo, context) { ImageRequest.Builder(context).data(profile.photo).crossfade(200).build() }
    Box(
        modifier
            .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
            .clip(RoundedCornerShape(CARD_RADIUS.dp))
            .background(Color(0xFF16222B))
            .semantics(mergeDescendants = true) {},
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(profile.name, style = NameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(profile.note, style = NoteStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = dim()) })
    }
}
