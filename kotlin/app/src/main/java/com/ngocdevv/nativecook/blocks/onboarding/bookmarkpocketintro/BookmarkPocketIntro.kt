package com.ngocdevv.nativecook.blocks.onboarding.bookmarkpocketintro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * @param photo A picture's address.
 * @param tint The colour shown until the picture has loaded.
 * @param height The card is 140 dp wide and this tall.
 * @param overlay Up to three short lines over the picture; the middle one is the loud one.
 * @param headline With a headline the card is an article: the picture on top, the words on black below.
 */
data class PocketCard(
    val photo: String,
    val tint: Color,
    val height: Float,
    val overlay: List<String> = emptyList(),
    val badge: String? = null,
    val headline: String? = null,
)

private const val PHOTO = "?auto=format&fit=crop&w=420&h=600&q=80"

val SamplePocketCards = listOf(
    PocketCard("https://images.unsplash.com/photo-1516483638261-f4dbaf036963$PHOTO", Color(0xFFB9A48C), 170f),
    PocketCard(
        "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad$PHOTO", Color(0xFF7D8794), 186f,
        overlay = listOf("Best value", "RESTAURANTS", "in London"), badge = "Part 16",
    ),
    PocketCard(
        "https://images.unsplash.com/photo-1502602898657-3e91760cbb34$PHOTO", Color(0xFF6F6A86), 220f,
        headline = "53 unmissable\nthings to do in\nParis right now",
    ),
)

private val Background = Color(0xFFF6F3F7)
private val Ink = Color(0xFF1C1A1D)
private val TaglineInk = Color(0xFF3A3A3C)
private val Accent = Color(0xFFE5484D)
private val ButtonFill = Color(0xFF050505)
private val Ring = Color(0xFFDCD8DE)
private val BookShade = listOf(Color(0xFF484649), Color(0xFF141113))
private val Glows = listOf(Color(0xFFFF5FA2), Color(0xFFFFD34D))

// The bookmark's corners: at the top, at its two tips, and in the notch between them.
private const val TOP_RADIUS = 12f
private const val TIP_RADIUS = 8f
private const val NOTCH = 35f
private const val NOTCH_RADIUS = 5f
private const val CARD_WIDTH = 140f
private const val CARD_RADIUS = 10f

// Cards are cut off at this height, well behind the bookmark, so none shows through its notch.
private const val CLIP = 430f

// A touch that moves less than this is a tap.
private const val SLOP = 4f

private const val MARGIN = 16f
private const val NAME_LINE = 32f
private const val TAG_LINE = 27f
private const val TEXT_GAP = 3f
private const val BUTTON_HEIGHT = 43f
private const val BUTTON_GAP = 6f

/** A place things are saved from: which ring, where on it in degrees, its tile and its glyph. */
private class OrbitIcon(
    val ring: Int,
    val angle: Double,
    val size: Float,
    val radius: Float,
    val colors: List<Color>,
    val ink: Color,
    val glyph: ImageVector,
    val glyphSize: Float,
)

private val OrbitIcons = listOf(
    OrbitIcon(0, -131.0, 64f, 15f, listOf(Color(0xFFD62F8A), Color(0xFFF7A23B)), Color.White, Icons.Outlined.PhotoCamera, 34f),
    OrbitIcon(0, 19.0, 66f, 33f, listOf(Color(0xFF39B5F5), Color(0xFF0960EA)), Color.White, Icons.Filled.Language, 38f),
    OrbitIcon(0, 164.0, 44f, 22f, listOf(Color(0xFFFFF3C4), Color(0xFFFFF3C4)), Color(0xFFF2B600), Icons.Filled.Lightbulb, 23f),
    OrbitIcon(0, -39.0, 44f, 22f, listOf(Color(0xFFDCEBFF), Color(0xFFDCEBFF)), Color(0xFF1877F2), Icons.Filled.NearMe, 23f),
    OrbitIcon(1, -54.0, 58f, 13f, listOf(Color(0xFF18181A), Color(0xFF050505)), Color.White, Icons.Filled.MusicNote, 32f),
    OrbitIcon(1, -140.0, 44f, 22f, listOf(Color(0xFFECDFFB), Color(0xFFECDFFB)), Color(0xFF8E44EC), Icons.Filled.Theaters, 23f),
    OrbitIcon(1, 145.0, 44f, 22f, listOf(Color(0xFFFDE0E0), Color(0xFFFDE0E0)), Color(0xFFF0443F), Icons.Filled.Restaurant, 23f),
    OrbitIcon(1, 36.0, 44f, 22f, listOf(Color(0xFFDAF5E0), Color(0xFFDAF5E0)), Color(0xFF22B455), Icons.AutoMirrored.Filled.DirectionsRun, 23f),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun introText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TaglineStyle = introText(20f, TAG_LINE, FontWeight.Normal, TaglineInk, -0.2f)
private val ButtonStyle = introText(14f, 18f, FontWeight.SemiBold, Color.White)
private val HeadlineStyle = introText(11f, 14f, FontWeight.Bold, Color.White)
private val OverlayStyle = introText(9f, 12f, FontWeight.SemiBold, Color.White)
private val OverlayLoudStyle = introText(12f, 16f, FontWeight.ExtraBold, Color.White, 0.6f)
private val BadgeStyle = introText(7f, 10f, FontWeight.Bold, Ink)

/**
 * The bookmark with its top edge on y = 0 and its middle on x = 0. [bulge] is how far each side
 * bows out; the top edge dips in proportion.
 */
private fun Path.bookmark(bulge: Float) {
    val w = BOOK_WIDTH.toFloat() / 2f
    val h = BOOK_HEIGHT.toFloat()
    val dip = DIP.toFloat() * bulge / BULGE.toFloat()
    // The unit step from a tip towards the notch.
    val run = hypot(w, NOTCH)
    val ux = w / run
    val uy = NOTCH / run
    rewind()
    moveTo(-w + TOP_RADIUS, 0f)
    quadraticTo(0f, 2f * dip, w - TOP_RADIUS, 0f)
    quadraticTo(w, 0f, w, TOP_RADIUS)
    quadraticTo(w + 2f * bulge, h / 2f, w, h - TIP_RADIUS)
    quadraticTo(w, h, w - TIP_RADIUS * ux, h - TIP_RADIUS * uy)
    lineTo(NOTCH_RADIUS * ux, h - NOTCH + NOTCH_RADIUS * uy)
    quadraticTo(0f, h - NOTCH, -NOTCH_RADIUS * ux, h - NOTCH + NOTCH_RADIUS * uy)
    lineTo(-w + TIP_RADIUS * ux, h - TIP_RADIUS * uy)
    quadraticTo(-w, h, -w, h - TIP_RADIUS)
    quadraticTo(-w - 2f * bulge, h / 2f, -w, TOP_RADIUS)
    quadraticTo(-w, 0f, -w + TOP_RADIUS, 0f)
    close()
}

/** A paint whose shapes come out blurred by [sigma], in the units they are drawn in. */
private fun softPaint(sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    // Hardware canvases only honour mask filters from API 28.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** What changed the picture: a tap, the loop that plays by itself, or the return to rest. */
private enum class How { Tap, Auto, Rest }

/** What a touch has hold of. */
private enum class Grip { Nothing, Card, Rings }

/**
 * A welcome screen. Saved posts fall into a big bookmark, which gulps each one; a tap on it turns
 * it into the hub of two rings of the places things are saved from, and the tagline is typed
 * again for each picture. By hand the card at the top edge is dragged into the bookmark, and a
 * finger turns the rings.
 *
 * @param taglines What the tagline says under the big bookmark, and among the orbits.
 * @param cards The saved posts that fall into the bookmark, in turn.
 * @param autoPlay Feeds three cards, opens the orbits and comes back, every [LOOP] seconds.
 * @param onFeed Called with the card's place in [cards] when it is let go into the bookmark.
 * @param onModeChange Called when the bookmark is tapped: true for the orbits, false for the pocket.
 */
@Composable
fun BookmarkPocketIntro(
    name: String = "Stash",
    taglines: List<String> = listOf("A new home for your internet hoarding tendencies", "A new way to explore a new city"),
    buttonLabel: String = "Get Started",
    cards: List<PocketCard> = SamplePocketCards,
    backgroundColor: Color = Background,
    inkColor: Color = Ink,
    accentColor: Color = Accent,
    autoPlay: Boolean = false,
    onFeed: ((card: Int) -> Unit)? = null,
    onModeChange: ((orbit: Boolean) -> Unit)? = null,
    onStart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The functions below outlive a recomposition: they read what the caller passes through these.
    val lines by rememberUpdatedState(taglines)
    val count by rememberUpdatedState(cards.size)
    val fedCard by rememberUpdatedState(onFeed)
    val modeChanged by rememberUpdatedState(onModeChange)

    // The screen is light: the status bar's icons are dark while it shows.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    // The clock's zero: every time below is in seconds since then.
    val origin = remember { System.nanoTime() }
    var mode by remember { mutableStateOf(Mode(orbit = false, at = -100.0, spun = 0.0)) }
    var drops by remember { mutableStateOf(List(cards.size) { Drop() }) }
    // By itself the screen has no card waiting; one comes once a finger has touched it.
    var wait by remember { mutableStateOf(Wait(on = !autoPlay, at = if (autoPlay) -100.0 else 0.0)) }
    var hold by remember { mutableStateOf(Hold()) }
    var next by remember { mutableIntStateOf(0) }
    // How many cards the pocket has had since it was last at rest: the glow grows with it.
    var fed by remember { mutableIntStateOf(0) }
    // What a finger has added to the rings' turning, in degrees.
    var turn by remember { mutableDoubleStateOf(0.0) }
    // At rest the tagline is empty when the screen plays by itself, and types itself otherwise.
    var tagline by remember { mutableStateOf(Tagline("", if (autoPlay) "" else taglines[0], if (autoPlay) -100.0 else TYPE_AFTER)) }
    // The stage's y at the top of the screen.
    var topEdge by remember { mutableDoubleStateOf(0.0) }
    // When nothing moves any more: the clock stops there.
    var settled by remember { mutableDoubleStateOf(if (autoPlay) 0.0 else typedBy(tagline)) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    var lineCount by remember { mutableIntStateOf(1) }
    // Seconds since `origin`, while something moves. Read while drawing only.
    val clock = remember { mutableDoubleStateOf(0.0) }

    fun now() = (System.nanoTime() - origin) / 1e9

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Runs the clock until nothing moves any more; the rings never stop. */
    fun settle() {
        var until = maxOf(mode.at + ORBIT_IN, typedBy(tagline), wait.at + WAIT_IN, now() + BACK)
        for (drop in drops) until = max(until, drop.at + 1.5)
        settled = if (mode.orbit && !still) Double.POSITIVE_INFINITY else until
    }

    /** Changes what the tagline is going to say, from [at] on. */
    fun retag(to: String, at: Double) {
        tagline = Tagline(typedAt(tagline, at, still), to, at)
    }

    /** Lets a card go with its lower edge at [bottom], [dx] aside of the middle. */
    fun feed(card: Int, bottom: Double, dx: Double, byHand: Boolean) {
        if (card !in drops.indices) return
        val t = now()
        fed++
        val glow = min(1.0, fed / 3.0).pow(2)
        drops = drops.mapIndexed { index, drop -> if (index == card) Drop(t, bottom, dx, glow) else drop }
        if (tagline.to.isEmpty()) retag(lines[0], t + TYPE_AFTER)
        settle()
        if (byHand) tick()
        fedCard?.invoke(card)
    }

    /** Goes to the orbits or back to the pocket; [How.Rest] leaves the tagline to `rest`. */
    fun show(orbit: Boolean, how: How) {
        val t = now()
        // One change at a time: a tap during a change is ignored.
        if (mode.orbit == orbit || t < mode.at + (if (mode.orbit) ORBIT_IN else POCKET_IN)) return
        mode = Mode(orbit, t, spun = if (mode.orbit) max(0.0, t - mode.at - SHRINK - LIFT) else 0.0)
        fed = 0
        if (orbit) {
            turn = 0.0
            wait = Wait(false, t)
            retag(lines[1], t + ORBIT_IN)
        } else if (how != How.Rest) {
            if (how == How.Tap) wait = Wait(true, t + POCKET_IN - WAIT_IN)
            retag(lines[0], t + COLLAPSE)
        }
        settle()
        if (how == How.Tap) tick()
        modeChanged?.invoke(orbit)
    }

    /** Back to the screen as it first appears. */
    fun rest() {
        val t = now()
        show(false, How.Rest)
        wait = Wait(false, t)
        hold = Hold()
        fed = 0
        retag("", t)
        settle()
    }

    /** A finger is on the screen: the card it can feed comes to the top edge. */
    fun touched() {
        touches++
        if (wait.on || mode.orbit) return
        // The card that fell longest ago is the one to wait.
        next = drops.indices.minByOrNull { drops[it].at } ?: 0
        wait = Wait(true, now())
        settle()
    }

    /** A tap at a point of the stage: on the bookmark it changes the picture. */
    fun tapped(x: Double, y: Double) {
        val hit = if (mode.orbit) {
            hypot(x - CENTER_X, y - ORBIT_Y) <= 56
        } else {
            abs(x - CENTER_X) <= BOOK_WIDTH / 2 && y >= BOOK_TOP && y <= BOOK_TOP + BOOK_HEIGHT
        }
        if (hit) show(!mode.orbit, How.Tap)
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val t = now()
            clock.doubleValue = min(t, settled)
            // At rest nothing is drawn again until a touch or the loop sets something moving.
            if (t >= settled) snapshotFlow { settled }.first { it > now() }
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        var start = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // The way back to rest takes what is left of a loop after AUTO_REST.
            rest()
            start = System.nanoTime() + ((LOOP - AUTO_REST) * 1e9).toLong()
        }
        while (true) {
            AUTO_FEEDS.forEachIndexed { card, at ->
                until(at)
                // By itself a card falls from just above the screen.
                feed(card % count, topEdge, 0.0, byHand = false)
            }
            until(AUTO_ORBIT)
            show(true, How.Auto)
            until(AUTO_REST)
            rest()
            start += (LOOP * 1e9).toLong()
        }
    }

    // The tagline as typed so far: it changes with the letters, not with the frames.
    val typed by remember { derivedStateOf { typedAt(tagline, clock.doubleValue, still) } }
    // The cursor is steady for a moment after each keystroke, then blinks.
    val cursorOn by produceState(true, typed, still) {
        value = true
        while (!still) {
            delay((BLINK * 1000).toLong())
            value = !value
        }
    }
    // The name sits one line lower while the tagline has one line.
    val low by animateFloatAsState(if (lineCount > 1) 0f else 1f, tween(if (still) 0 else (LINE_SHIFT * 1000).toInt()), label = "lines")

    val outline = remember { Path() }
    val shadowPaint = remember { softPaint(12f).apply { color = Color.Black.copy(alpha = 0.18f).toArgb() } }
    val glowPaint = remember { softPaint(30f) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            // One touch is a tap on the bookmark, a pull on the waiting card, or a finger turning the rings.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Pixels to one of the stage's dp, and the stage's y at the top of the screen.
                    val unit = size.width / STAGE_WIDTH
                    val lead = (size.height - STAGE_HEIGHT * unit) / 2
                    fun stageX(point: Offset) = point.x / unit
                    fun stageY(point: Offset) = (point.y - lead) / unit
                    val top = -lead / unit
                    touched()
                    var moved = false
                    var grip = Grip.Nothing
                    var angle = 0.0
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val pull = change.position - down.position
                        if (!change.pressed) {
                            if (!moved) {
                                tapped(stageX(change.position), stageY(change.position))
                            } else if (grip == Grip.Card) {
                                val t = now()
                                val held = hold
                                if (held.dy > FEED_AFTER) {
                                    val card = next
                                    next = (card + 1) % count
                                    hold = Hold()
                                    wait = Wait(true, t + WAIT_AFTER)
                                    feed(card, top + PEEK + held.dy, held.dx, byHand = true)
                                } else {
                                    hold = held.copy(at = t)
                                    settle()
                                }
                            }
                            break
                        }
                        if (!moved && pull.getDistance() > SLOP.dp.toPx()) {
                            moved = true
                            val x = stageX(down.position)
                            val y = stageY(down.position)
                            val t = now()
                            if (mode.orbit) {
                                // Anywhere above the words turns the rings, once they are open.
                                if (t >= mode.at + ORBIT_IN && y <= BOOK_TOP + BOOK_HEIGHT + 40) {
                                    grip = Grip.Rings
                                    angle = atan2(y - ORBIT_Y, x - CENTER_X)
                                }
                            } else if (wait.on && t >= wait.at + WAIT_IN && abs(x - CENTER_X) <= CARD_WIDTH / 2 + 12 && y <= top + PEEK + 12) {
                                grip = Grip.Card
                            }
                        }
                        if (grip == Grip.Card) {
                            hold = Hold(pull.x / unit, max(-PEEK / 2, pull.y / unit), Double.POSITIVE_INFINITY)
                            change.consume()
                        } else if (grip == Grip.Rings) {
                            val here = atan2(stageY(change.position) - ORBIT_Y, stageX(change.position) - CENTER_X)
                            // The short way round from the last angle to this one.
                            val step = atan2(sin(here - angle), cos(here - angle))
                            angle = here
                            turn += Math.toDegrees(step)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val fit = maxWidth.value / STAGE_WIDTH.toFloat()
        // The stage's middle is on the screen's middle, so on most screens its top is a little off the edge.
        val lead = (maxHeight.value - STAGE_HEIGHT.toFloat() * fit) / 2f
        val top = -lead / fit
        SideEffect { topEdge = top.toDouble() }
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val bottom = max(insets.calculateBottomPadding().value + 23f, 28f)

        // Rings, cards and icons sit in the stage's own dp, scaled with it from the top left corner.
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(STAGE_WIDTH.dp, STAGE_HEIGHT.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = fit
                    scaleY = fit
                    translationY = lead.dp.toPx()
                }
                .clearAndSetSemantics {},
        ) {
            for (radius in RINGS) {
                Box(
                    Modifier
                        .offset((CENTER_X - radius).dp, (ORBIT_Y - radius).dp)
                        .size((radius * 2).dp)
                        .graphicsLayer {
                            val open = ringsAt(mode, clock.doubleValue, still)
                            scaleX = if (still) 1f else open
                            scaleY = scaleX
                            alpha = min(1f, open * 3f)
                        }
                        .border(1.dp, Ring, CircleShape),
                )
            }

            Box(Modifier.offset(y = top.dp).size(STAGE_WIDTH.dp, (CLIP - top).dp).clipToBounds()) {
                cards.forEachIndexed { index, card ->
                    fun pose() = cardPose(card.height.toDouble(), next == index, wait, hold, drops.getOrElse(index) { Drop() }, top.toDouble(), clock.doubleValue, still)
                    Box(
                        Modifier
                            .offset((CENTER_X - CARD_WIDTH / 2).dp, (-card.height).dp)
                            .size(CARD_WIDTH.dp, card.height.dp)
                            .graphicsLayer {
                                val now = pose()
                                // Everything a card does is measured from its lower edge.
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                translationX = now.x.dp.toPx()
                                translationY = (now.bottom - top).dp.toPx()
                                scaleX = now.scale
                                scaleY = now.scale
                                alpha = now.opacity
                            }
                            .clip(RoundedCornerShape(CARD_RADIUS.dp)),
                    ) {
                        CardFace(card)
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = pose().dark }.background(Color.Black))
                    }
                }
            }

            OrbitIcons.forEachIndexed { index, icon ->
                Box(
                    Modifier
                        .offset((CENTER_X - icon.size / 2).dp, (ORBIT_Y - icon.size / 2).dp)
                        .size(icon.size.dp)
                        .graphicsLayer {
                            val now = iconPose(index, icon.ring, icon.angle, mode, clock.doubleValue, turn, still)
                            translationX = now.x.dp.toPx()
                            translationY = now.y.dp.toPx()
                            scaleX = now.scale
                            scaleY = now.scale
                            alpha = now.opacity
                        }
                        .drawBehind {
                            drawRoundRect(
                                Brush.linearGradient(icon.colors, Offset(size.width * 0.2f, 0f), Offset(size.width * 0.8f, size.height)),
                                cornerRadius = CornerRadius(icon.radius.dp.toPx()),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon.glyph, contentDescription = null, tint = icon.ink, modifier = Modifier.size(icon.glyphSize.dp))
                }
            }
        }

        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = bookmarkPose(mode, clock.doubleValue, still).opacity }.clearAndSetSemantics {}) {
            val t = clock.doubleValue
            val pose = bookmarkPose(mode, t, still)
            val gulp = gulpAt(drops, t, still)
            val unit = size.width / STAGE_WIDTH.toFloat()
            val middle = (BOOK_TOP + BOOK_HEIGHT / 2).toFloat()
            outline.bookmark(gulp.bulge)
            translate(0f, (size.height - STAGE_HEIGHT.toFloat() * unit) / 2f) {
                scale(unit, unit, pivot = Offset.Zero) {
                    withTransform({
                        translate(CENTER_X.toFloat(), middle + (ORBIT_Y.toFloat() - middle) * pose.lift)
                        rotate(pose.tilt, pivot = Offset.Zero)
                        scale(pose.scale, pose.scale, pivot = Offset.Zero)
                        translate(0f, -BOOK_HEIGHT.toFloat() / 2f)
                    }) {
                        val w = BOOK_WIDTH.toFloat()
                        val h = BOOK_HEIGHT.toFloat()
                        drawIntoCanvas {
                            // Colour leaks from under the bookmark when it has had its fill.
                            if (gulp.glow > 0f) {
                                glowPaint.color = Glows[0].copy(alpha = 0.6f * gulp.glow).toArgb()
                                it.nativeCanvas.drawOval(-w / 2f - 14f, h - 70f, -w / 2f + 96f, h + 10f, glowPaint)
                                glowPaint.color = Glows[1].copy(alpha = 0.6f * gulp.glow).toArgb()
                                it.nativeCanvas.drawOval(w / 2f - 96f, h - 70f, w / 2f + 14f, h + 10f, glowPaint)
                            }
                            it.nativeCanvas.translate(0f, 10f)
                            it.nativeCanvas.drawPath(outline.asAndroidPath(), shadowPaint)
                            it.nativeCanvas.translate(0f, -10f)
                        }
                        drawPath(outline, Brush.verticalGradient(BookShade, startY = 0f, endY = h))
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = (bottom + BUTTON_HEIGHT + BUTTON_GAP).dp)
                .fillMaxWidth()
                .height((NAME_LINE + TEXT_GAP + TAG_LINE * 2f).dp)
                // The second line of the tagline comes up from under this edge.
                .clipToBounds(),
        ) {
            Column(Modifier.graphicsLayer { translationY = (TAG_LINE * low).dp.toPx() }) {
                BasicText(
                    buildAnnotatedString {
                        append(name)
                        withStyle(SpanStyle(color = accentColor)) { append(".") }
                    },
                    style = introText(26f, NAME_LINE, FontWeight.Bold, inkColor, -0.4f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(TEXT_GAP.dp))
                BasicText(
                    buildAnnotatedString {
                        append(typed)
                        withStyle(SpanStyle(color = if (cursorOn) TaglineInk else Color.Transparent)) { append("_") }
                    },
                    style = TaglineStyle,
                    maxLines = 2,
                    onTextLayout = { lineCount = it.lineCount },
                    modifier = Modifier.semantics { contentDescription = typed },
                )
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val press by animateFloatAsState(if (pressed) 1f else 0f, tween(120), label = "press")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = bottom.dp)
                .fillMaxWidth()
                .height(BUTTON_HEIGHT.dp)
                .graphicsLayer {
                    alpha = 1f - 0.2f * press
                    scaleX = if (still) 1f else 1f - 0.02f * press
                    scaleY = scaleX
                }
                .background(ButtonFill, RoundedCornerShape(14.dp))
                .clickable(interaction, indication = null, role = Role.Button) {
                    touches++
                    tick()
                    onStart?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(buttonLabel, style = ButtonStyle, maxLines = 1)
        }
    }
}

/** What a saved post looks like: a picture, a picture under a few words, or an article. */
@Composable
private fun CardFace(card: PocketCard) {
    val context = LocalContext.current
    val request = remember(card.photo) { ImageRequest.Builder(context).data(card.photo).crossfade(200).build() }
    if (card.headline != null) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
            AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(92.dp).background(card.tint))
            Column(Modifier.padding(start = 9.dp, end = 9.dp, top = 9.dp)) {
                BasicText(card.headline, style = HeadlineStyle)
                Spacer(Modifier.height(8.dp))
                for (width in listOf(104f, 112f, 72f)) {
                    Box(Modifier.size(width.dp, 3.dp).background(Color.White.copy(alpha = 0.28f), CircleShape))
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(62.dp, 16.dp).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape))
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().background(card.tint)) {
        AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (card.overlay.isNotEmpty()) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                card.overlay.forEachIndexed { index, line ->
                    BasicText(line, style = if (index == 1) OverlayLoudStyle else OverlayStyle, maxLines = 1)
                }
            }
            if (card.badge != null) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).height(16.dp).background(Color.White, CircleShape).padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(card.badge, style = BadgeStyle, maxLines = 1)
                }
            }
        }
    }
}
