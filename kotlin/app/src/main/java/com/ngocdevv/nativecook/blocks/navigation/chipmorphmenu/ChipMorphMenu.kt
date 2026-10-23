package com.ngocdevv.nativecook.blocks.navigation.chipmorphmenu

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

data class Topic(val label: String, val emoji: String)

/** @param members How many members are online in it. */
data class Category(val name: String, val icon: ImageVector, val members: Int, val topics: List<Topic>)

data class Person(val name: String, val photo: String, val interest: String)

private fun photo(id: String, width: Int, height: Int) =
    "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=$width&h=$height&q=80"

val SampleCategories = listOf(
    Category(
        "Career", Icons.Filled.Work, 12,
        listOf(Topic("Speak with confidence", "🎤"), Topic("Plan your next move", "🧭"), Topic("Get ahead at work", "🏆"), Topic("Side projects", "💻")),
    ),
    Category(
        "Wellness", Icons.Filled.Spa, 11,
        listOf(Topic("Sleep better", "🌙"), Topic("Skincare routine", "🍃"), Topic("Manage stress", "🌿"), Topic("Fitness & nutrition", "🍎")),
    ),
    Category(
        "Travel", Icons.AutoMirrored.Filled.Send, 7,
        listOf(Topic("Weekend escapes", "⛺"), Topic("Travel on a budget", "🎟"), Topic("Going solo", "🎒"), Topic("Food to fly for", "🍜")),
    ),
    Category(
        "Money", Icons.Filled.MonetizationOn, 9,
        listOf(Topic("Budget basics", "💰"), Topic("Save for a trip", "🏦"), Topic("Investing 101", "📈"), Topic("Split the bills", "🧾")),
    ),
)

val SamplePeople = listOf(
    Person("Noor Haddad", photo("1544005313-94ddf0286df2", 360, 360), "Meeting new people"),
    Person("Leo Marsh", photo("1506794778202-cad84cf45f1d", 360, 360), "Weekend escapes"),
    Person("Mina Okoye", photo("1487412720507-e7ab37603c6f", 360, 360), "Sleep better"),
)

val SampleMatches = listOf(
    Person("Jonas Reed", photo("1535713875002-d1d0cf377fde", 360, 360), ""),
    Person("Clara Wynn", photo("1517841905240-472988babdf9", 360, 360), ""),
    Person("Theo Barnes", photo("1531891437562-4301cf35b7e4", 360, 360), ""),
)

val SampleTakes = listOf(
    photo("1522202176988-66273c2fd55f", 324, 600),
    photo("1521572267360-ee0c2909d518", 324, 600),
    photo("1504593811423-6dd665756598", 324, 600),
    photo("1526772662000-3f88f10405ff", 324, 600),
)

private val Page = Color.White
private val Ink = Color(0xFF1C1C1E)
private val Mute = Color(0xFF8E8E93)
private val Soft = Color(0xFFF4F4F4)
private val Panel = Color(0xFFF6F6F6)
private val Rule = Color(0xFFEFEFEF)
private val Green = Color(0xFF2DBE6C)
private val Pale = Color(0xFFDDF7E8)
private val PaleEdge = Color(0xFFBFEFD3)

// The little faces of the members line, and of the people in the header.
private val Faces = listOf(Color(0xFFF2B8A0), Color(0xFFA9C7F5), Color(0xFFBFE3B4))
private val FaceOnline = Color(0xFFF5C9A8)
private val FaceYou = Color(0xFFC9C2F0)

private const val MARGIN = 20f

// The page, from the safe area's top.
private const val HEADER_TOP = 14f
private const val LIVE = 40f
private const val ROUND = 38f
private const val ONLINE_TOP = 87f
private const val CHIPS_TOP = 144f
private const val HEADING_TOP = 217f
private const val PEOPLE_TOP = 263f
private const val TAKES_TOP = 585f
private const val THUMBS_TOP = 629f

// A chip: its glyph, its label and a chevron.
private const val CHIP = 38f
private const val CHIP_GAP = 10f
private const val CHIP_PAD = 12f
private const val CHIP_END = 10f
private const val GLYPH = 18f
private const val LABEL_GAP = 6f
private const val CHEVRON = 16f
private const val CHEVRON_GAP = 4f
private const val TITLE_LINE = 20f

// What a chip is wider than its glyph and label by.
private const val TITLE_TRIM = CHIP_PAD + CHEVRON_GAP + CHEVRON + CHIP_END
private const val PERSON_WIDTH = 247f
private const val PERSON_HEIGHT = 302f
private const val PERSON_GAP = 14f
private const val PORTRAIT = 118f
private const val RING = 4f
private const val THUMB_WIDTH = 108f
private const val THUMB_HEIGHT = 200f
private const val THUMB_GAP = 10f
private const val TABS_WIDTH = 184f
private const val TABS_HEIGHT = 54f

// The card a chip opens into, and where its parts sit from its top.
private const val CARD_WIDTH = 340f
private const val CARD_HEIGHT = 388f
private const val CARD_TOP = 152f
private const val CARD_RADIUS = 28f
private const val TITLE_AT = 38f
private const val TITLE_GROWN = 17f / 14f
private const val MEMBERS_AT = 82f
private const val TOPICS_AT = 144f
private const val TOPIC_STEP = 58f
private const val TOPIC = 40f

// The chip's frame springs to the card's and back: response 0.4 s, about 3% past its target
// (mass 1, stiffness 247, damping 23.4).
private const val STIFFNESS = 247f
private const val DAMPING_RATIO = 0.745f

// The page under an open card.
private const val COVER = 0.72f
private const val COVER_FOR = 300
private val CoverEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

// The members line and the topics come up one after another, and leave together.
private const val RISE_AFTER = 150
private const val RISE_STEP = 60
private const val RISE_FOR = 400
private const val RISE_BY = 10f
private const val LEAVE_FOR = 120
private const val CHEVRON_GONE = 0.3f

// The card is white but for the last part of its way back, where it takes the chip's colour.
private const val TINT_BY = 0.4f

// The heading and the profile cards change with the chosen topic.
private const val SWAP = 250
private const val PRESS = 0.95f

/** autoPlay: Career, a topic, Wellness and out again, then the choice is cleared. */
const val LOOP = 10f
private const val OPEN_AT = 0.8f
private const val PRESS_AT = 2.45f
private const val PICK_AT = 2.6f
private const val PICKED = 2
private const val AGAIN_AT = 4.4f
private const val OUT_AT = 6.2f
private const val CLEAR_AT = 7.4f

/** After a touch autoPlay waits this long, and for the card to be closed, before it starts over. */
private const val USER_HOLD = 3f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun menuText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val LiveStyle = menuText(11f, 14f, FontWeight.Bold, Green, spacing = 1f)
private val InitialsStyle = menuText(12f, 16f, FontWeight.SemiBold, Ink)
private val OnlineInitialStyle = menuText(9f, 12f, FontWeight.Bold, Ink)
private val NameStyle = menuText(13f, 18f, FontWeight.Medium, Ink)
private val NoteStyle = menuText(13f, 18f, FontWeight.Normal, Mute)
private val ChipStyle = menuText(14f, TITLE_LINE, FontWeight.Medium, Ink)
private val ChipLightStyle = ChipStyle.copy(color = Color.White)
private val HeadingStyle = menuText(18f, 24f, FontWeight.SemiBold, Ink, spacing = -0.2f)
private val PersonStyle = menuText(16f, 22f, FontWeight.SemiBold, Ink)
private val TagStyle = menuText(9f, 12f, FontWeight.Bold, Ink, spacing = 0.4f)
private val TabStyle = menuText(11f, 14f, FontWeight.SemiBold, Ink)
private val EmojiStyle = menuText(16f, 20f, FontWeight.Normal, Ink)

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/**
 * A community page with a row of category chips. A chip does not drop a menu: it swells into a
 * white card in the middle of the screen, over the darkened page, with its glyph and label as the
 * card's title and the category's topics under it. A topic, or a tap outside, sends the card back
 * into the chip; a picked topic turns the chip dark and changes the heading and the profile cards.
 *
 * @param categories The chips; each opens into a card of its topics.
 * @param people The profile cards shown while no topic is chosen.
 * @param matches The profile cards shown for a chosen topic, which is written on them.
 * @param takes Photos of the row at the bottom.
 * @param online Who the line under the header says came online.
 * @param autoPlay Opens a chip, picks a topic, opens another and clears the choice by itself every [LOOP] seconds.
 * @param onSelect Called when a topic is picked, and with null when the dark chip is tapped to clear it.
 */
@Composable
fun ChipMorphMenu(
    categories: List<Category> = SampleCategories,
    people: List<Person> = SamplePeople,
    matches: List<Person> = SampleMatches,
    takes: List<String> = SampleTakes,
    title: String = "For your first chat",
    takesTitle: String = "Member Takes",
    online: String = "Asha Verma",
    autoPlay: Boolean = false,
    onSelect: ((category: Category, topic: Topic?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val chipsNow by rememberUpdatedState(categories)
    val picked by rememberUpdatedState(onSelect)

    // The chip whose card is on the screen, opening, open or closing; `shown` is the last one.
    var active by remember { mutableStateOf<Int?>(null) }
    var shown by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    // The topic that was picked, and the chip it belongs to.
    var choice by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    // The topic held down by the autoPlay loop.
    var autoPressed by remember { mutableStateOf<Int?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Changes when the loop starts over, which sends the rows back to their start.
    var resets by remember { mutableIntStateOf(0) }

    // Where the chips are, read when one opens and again when it closes; the frame is in pixels.
    val placed = remember { mutableMapOf<Int, LayoutCoordinates>() }
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var chip by remember { mutableStateOf(Rect.Zero) }
    // 0 is the chip, 1 the card.
    val morph = remember { Animatable(0f) }
    val cover = animateFloatAsState(if (open) 1f else 0f, tween(COVER_FOR, easing = CoverEase), label = "cover")
    val chipScroll = rememberScrollState()
    val peopleScroll = rememberScrollState()
    val shadow = remember(density) { SoftRoundRect(Color.Black.copy(alpha = 0.3f), with(density) { 25.dp.toPx() }) }

    // The page is light: the status bar's icons are dark while it is on the screen.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Reads the chip's frame from the row as it is now; false while the row is not laid out. */
    fun find(index: Int): Boolean {
        val from = placed[index]?.takeIf { it.isAttached } ?: return false
        val onto = root[0]?.takeIf { it.isAttached } ?: return false
        chip = onto.localBoundingBoxOf(from, clipBounds = false)
        return true
    }

    fun show(index: Int) {
        if (!find(index)) return
        active = index
        shown = index
        open = true
        scope.launch { morph.animateTo(1f, spring(DAMPING_RATIO, STIFFNESS)) }
    }

    fun close() {
        val index = active ?: return
        find(index)
        open = false
        scope.launch {
            morph.animateTo(0f, spring(DAMPING_RATIO, STIFFNESS))
            // A card opened again meanwhile cancels this before it gets here.
            active = null
        }
    }

    fun choose(index: Int, topic: Int?) {
        choice = topic?.let { index to it }
        picked?.invoke(chipsNow[index], topic?.let { chipsNow[index].topics[it] })
    }

    fun pressChip(index: Int) {
        // A card on its way back does not hold up the next one.
        if (active != null && open) return
        tick()
        if (choice?.first == index) choose(index, null) else show(index)
    }

    fun pressTopic(topic: Int) {
        val index = active ?: return
        if (!open) return
        tick()
        choose(index, topic)
        close()
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            if (open) close()
            while (active != null) delay(100)
            // The loop starts over from the page as it was given.
            choice = null
            tab = 0
            resets++
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(OPEN_AT)
            show(0)
            until(PRESS_AT)
            autoPressed = PICKED
            until(PICK_AT)
            autoPressed = null
            choose(0, PICKED)
            close()
            until(AGAIN_AT)
            show(1)
            until(OUT_AT)
            close()
            until(CLEAR_AT)
            choose(0, null)
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    LaunchedEffect(resets) {
        if (resets == 0) return@LaunchedEffect
        launch { chipScroll.animateScrollTo(0) }
        peopleScroll.animateScrollTo(0)
    }

    val chosen = choice?.let { categories[it.first].topics[it.second] }
    val headline = chosen?.label ?: title

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
            .clipToBounds()
            .onGloballyPositioned { root[0] = it }
            // A touch takes over from the autoPlay loop, which starts again from rest a while later.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    autoPressed = null
                    touches++
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val tabsBottom = max(insets.calculateBottomPadding().value, 16f)
        val cardWidth = min(CARD_WIDTH, maxWidth.value - MARGIN * 2f)
        // The card's frame in pixels.
        val card by rememberUpdatedState(
            with(density) {
                Rect(
                    Offset(((maxWidth.value - cardWidth) / 2f).dp.toPx(), (top + CARD_TOP).dp.toPx()),
                    Size(cardWidth.dp.toPx(), CARD_HEIGHT.dp.toPx()),
                )
            },
        )

        // Reduced motion keeps the card in its place and fades it.
        fun grown() = if (reducedMotion) 1f else morph.value
        fun frame(): Rect {
            val g = grown()
            return Rect(
                Offset(mix(chip.left, card.left, g), mix(chip.top, card.top, g)),
                Size(max(0f, mix(chip.width, card.width, g)), max(0f, mix(chip.height, card.height, g))),
            )
        }

        // The header.
        Row(
            Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth().offset(y = (top + HEADER_TOP).dp).height(LIVE.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .height(LIVE.dp)
                    .background(Pale, CircleShape)
                    .border(1.dp, PaleEdge, CircleShape)
                    .padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).border(2.dp, Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(6.dp).background(Ink, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(7.dp).background(Green, CircleShape))
                Spacer(Modifier.width(6.dp))
                BasicText("LIVE NOW", style = LiveStyle, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(ROUND.dp).background(Soft, CircleShape).semantics { contentDescription = "Inbox" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Inbox, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(ROUND.dp).background(FaceYou, CircleShape).semantics { contentDescription = "Your profile" }, contentAlignment = Alignment.Center) {
                BasicText("YO", style = InitialsStyle, modifier = Modifier.clearAndSetSemantics {})
            }
        }

        Row(
            Modifier.padding(horizontal = MARGIN.dp).offset(y = (top + ONLINE_TOP).dp).height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(18.dp).background(FaceOnline, CircleShape), contentAlignment = Alignment.Center) {
                BasicText(online.take(1), style = OnlineInitialStyle, modifier = Modifier.clearAndSetSemantics {})
            }
            Spacer(Modifier.width(8.dp))
            BasicText(online, style = NameStyle, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).background(Green, CircleShape))
            Spacer(Modifier.width(5.dp))
            BasicText("came online", style = NoteStyle, maxLines = 1)
        }

        // The chips.
        Row(
            Modifier
                .offset(y = (top + CHIPS_TOP).dp)
                .height(CHIP.dp)
                .horizontalScroll(chipScroll)
                .padding(horizontal = MARGIN.dp),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
        ) {
            Box(
                Modifier
                    .size(CHIP.dp)
                    .background(Soft, CircleShape)
                    .clip(CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {}
                    .semantics { contentDescription = "Filters" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            categories.forEachIndexed { index, category ->
                val dark = choice?.first == index && active != index
                Box(
                    Modifier
                        .onGloballyPositioned { placed[index] = it }
                        // Its place stays empty while it is the card.
                        .graphicsLayer { alpha = if (active == index) 0f else 1f }
                        .height(CHIP.dp)
                        .background(if (dark) Ink else Soft, CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { pressChip(index) }
                        .semantics {
                            contentDescription = if (dark) "${category.name}. Clear the topic" else "${category.name}. Show topics"
                            selected = dark
                        }
                        .padding(start = CHIP_PAD.dp, end = CHIP_END.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ChipTitle(category, light = dark)
                }
            }
        }

        // The heading and the profile cards change with the chosen topic.
        Crossfade(
            headline,
            animationSpec = tween(SWAP),
            modifier = Modifier.padding(horizontal = MARGIN.dp).offset(y = (top + HEADING_TOP).dp).height(24.dp),
            label = "heading",
        ) { text ->
            BasicText(text, style = HeadingStyle, maxLines = 1, modifier = Modifier.semantics { heading() })
        }

        Row(Modifier.offset(y = (top + PEOPLE_TOP).dp).height(PERSON_HEIGHT.dp).horizontalScroll(peopleScroll).padding(horizontal = MARGIN.dp)) {
            Crossfade(chosen, animationSpec = tween(SWAP), label = "people") { topic ->
                Row(horizontalArrangement = Arrangement.spacedBy(PERSON_GAP.dp)) {
                    (if (topic == null) people else matches).forEach { person ->
                        PersonCard(person, interest = topic?.label ?: person.interest)
                    }
                }
            }
        }

        BasicText(
            takesTitle,
            style = HeadingStyle,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = MARGIN.dp).offset(y = (top + TAKES_TOP).dp).height(24.dp).semantics { heading() },
        )
        Row(
            Modifier
                .offset(y = (top + THUMBS_TOP).dp)
                .height(THUMB_HEIGHT.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MARGIN.dp),
            horizontalArrangement = Arrangement.spacedBy(THUMB_GAP.dp),
        ) {
            takes.forEachIndexed { index, url ->
                Box(Modifier.size(THUMB_WIDTH.dp, THUMB_HEIGHT.dp).clip(RoundedCornerShape(20.dp)).background(Soft)) {
                    Photo(url, Modifier.fillMaxSize())
                    if (index == 0) {
                        Box(
                            Modifier.padding(10.dp).height(18.dp).background(Pale, CircleShape).padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText("NEW", style = TagStyle)
                        }
                    }
                }
            }
        }

        // The tab capsule.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = tabsBottom.dp)
                .size(TABS_WIDTH.dp, TABS_HEIGHT.dp)
                .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(Color.White, CircleShape),
        ) {
            listOf("Home", "Chats").forEachIndexed { index, label ->
                val tint = if (tab == index) Ink else Mute
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            tick()
                            tab = index
                        }
                        .semantics { selected = tab == index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (index == 0) Icons.Filled.Home else Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    BasicText(label, style = TabStyle.copy(color = tint), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // The cover lies on everything, the tab capsule too; a tap on it closes the card.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawRect(Color.Black, alpha = COVER * cover.value) }
                .then(
                    if (open) {
                        Modifier
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { close() }
                            .semantics { contentDescription = "Close" }
                    } else {
                        Modifier
                    },
                ),
        )

        if (active != null) {
            val category = categories[shown]
            val dark = choice?.first == shown

            // The card: one frame and one fill, from the chip's to the card's.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (reducedMotion) cover.value else 1f }
                    .drawBehind {
                        val at = frame()
                        val flat = clamp01(grown())
                        val radius = mix(CHIP / 2f, CARD_RADIUS, flat).dp.toPx()
                        drawIntoCanvas {
                            shadow.draw(it.nativeCanvas, at.left, at.top + 20.dp.toPx(), at.width, at.height, radius, alpha = flat)
                        }
                        drawRoundRect(
                            lerp(if (dark) Ink else Soft, Color.White, clamp01(grown() / TINT_BY)),
                            at.topLeft,
                            at.size,
                            CornerRadius(radius),
                        )
                    },
            )

            // The card's parts keep their places on the screen while its frame grows round them.
            val clip = remember { Path() }
            Box(
                Modifier
                    .offset { IntOffset(card.left.toInt(), card.top.toInt()) }
                    .size(cardWidth.dp, CARD_HEIGHT.dp)
                    .graphicsLayer { alpha = if (reducedMotion) cover.value else 1f }
                    // A tap on the card itself is not a tap on the cover under it.
                    .pointerInput(Unit) { detectTapGestures {} }
                    .drawWithContent {
                        val at = frame().translate(-card.left.toInt().toFloat(), -card.top.toInt().toFloat())
                        clip.rewind()
                        clip.addRoundRect(RoundRect(at, CornerRadius(mix(CHIP / 2f, CARD_RADIUS, clamp01(grown())).dp.toPx())))
                        clipPath(clip) { this@drawWithContent.drawContent() }
                    },
            ) {
                Rise(open, order = 0, Modifier.align(Alignment.TopCenter).offset(y = (MEMBERS_AT - 10f).dp)) {
                    Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            Faces.forEach { color ->
                                Box(Modifier.size(18.dp).background(Color.White, CircleShape).padding(1.5.dp).background(color, CircleShape))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        BasicText("${category.members} Members", style = NameStyle, maxLines = 1)
                        BasicText(" online", style = NoteStyle, maxLines = 1)
                    }
                }
                category.topics.forEachIndexed { index, topic ->
                    Rise(open, order = index + 1, Modifier.align(Alignment.TopCenter).offset(y = (TOPICS_AT + index * TOPIC_STEP - TOPIC / 2f).dp)) {
                        TopicCapsule(topic, enabled = open, held = autoPressed == index) { pressTopic(index) }
                    }
                }
            }

            // The chip's glyph and label are the card's title: one row that travels and grows.
            Box(
                Modifier.graphicsLayer {
                    val g = grown()
                    val scale = mix(1f, TITLE_GROWN, g)
                    val titleWidth = chip.width - TITLE_TRIM.dp.toPx()
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = mix(chip.left + CHIP_PAD.dp.toPx(), card.center.x - titleWidth * scale / 2f, g)
                    translationY = mix(chip.center.y, card.top + TITLE_AT.dp.toPx(), g) - TITLE_LINE.dp.toPx() / 2f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (reducedMotion) cover.value else 1f
                },
            ) {
                ChipTitle(category, light = false, chevron = { 1f - clamp01(grown() / CHEVRON_GONE) })
                // Over a dark chip the row is white, and turns to ink as the chip turns to the card.
                if (dark) ChipTitle(category, light = true, Modifier.graphicsLayer { alpha = 1f - clamp01(grown() / TINT_BY) })
            }
        }
    }
}

/** A chip's glyph, label and chevron: the row that becomes the card's title. [chevron] is the chevron's opacity. */
@Composable
private fun ChipTitle(category: Category, light: Boolean, modifier: Modifier = Modifier, chevron: () -> Float = { 1f }) {
    Row(modifier.height(TITLE_LINE.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(category.icon, contentDescription = null, tint = if (light) Color.White else Ink, modifier = Modifier.size(GLYPH.dp))
        Spacer(Modifier.width(LABEL_GAP.dp))
        BasicText(category.name, style = if (light) ChipLightStyle else ChipStyle, maxLines = 1)
        Spacer(Modifier.width(CHEVRON_GAP.dp))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = if (light) Color.White else Mute,
            modifier = Modifier.size(CHEVRON.dp).graphicsLayer { alpha = chevron() },
        )
    }
}

/** A part of the card that comes up after the card has started to open: [order] sets its turn. */
@Composable
private fun Rise(shown: Boolean, order: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val risen = animateFloatAsState(
        if (shown) 1f else 0f,
        if (shown) tween(RISE_FOR, delayMillis = RISE_AFTER + order * RISE_STEP, easing = EaseOutStrong) else tween(LEAVE_FOR, easing = LinearEasing),
        label = "rise",
    )
    Box(
        modifier.graphicsLayer {
            alpha = risen.value
            translationY = RISE_BY.dp.toPx() * (1f - risen.value)
        },
    ) {
        content()
    }
}

@Composable
private fun TopicCapsule(topic: Topic, enabled: Boolean, held: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed || held) PRESS else 1f, tween(100), label = "press")
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .height(TOPIC.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .background(Color.White, CircleShape)
            .border(1.dp, Rule, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(topic.emoji, style = EmojiStyle)
        Spacer(Modifier.width(8.dp))
        BasicText(topic.label, style = NameStyle, maxLines = 1)
    }
}

@Composable
private fun PersonCard(person: Person, interest: String) {
    Column(
        Modifier.size(PERSON_WIDTH.dp, PERSON_HEIGHT.dp).background(Panel, RoundedCornerShape(30.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 25.dp)
                .size((PORTRAIT + RING * 2f).dp)
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Photo(person.photo, Modifier.size(PORTRAIT.dp).clip(CircleShape).background(Rule))
        }
        BasicText(person.name, style = PersonStyle, maxLines = 1, modifier = Modifier.padding(top = 18.dp))
        BasicText("Because you are interested in", style = NoteStyle, maxLines = 1, modifier = Modifier.padding(top = 9.dp))
        BasicText(interest, style = NameStyle, maxLines = 1)
        Row(
            Modifier
                .padding(top = 15.dp)
                .height(30.dp)
                .background(Color.White, CircleShape)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {}
                .semantics { contentDescription = "Chat with ${person.name}" }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Green, CircleShape))
            Spacer(Modifier.width(8.dp))
            BasicText("Chat", style = NameStyle, maxLines = 1)
        }
    }
}

/** A photo from the network; what is behind it shows until it arrives. */
@Composable
private fun Photo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).crossfade(200).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
