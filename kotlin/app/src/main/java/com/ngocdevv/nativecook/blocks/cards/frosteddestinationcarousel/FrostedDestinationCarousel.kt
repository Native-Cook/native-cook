package com.ngocdevv.nativecook.blocks.cards.frosteddestinationcarousel

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param caption The small line under the name.
 * @param photo Address of the photo.
 * @param tint The photo's main colour: the lower half of the card melts into it, and its shadow is cast in it.
 * @param dot The dot after the name, in a colour of the country's flag.
 */
data class Place(val name: String, val caption: String, val photo: String, val tint: Color, val dot: Color)

private const val WIDTH = 350f
private const val HEIGHT = 380f
private const val RADIUS = 30f

private const val CARD_W = 176f
private const val CARD_H = 244f
private const val CARD_RADIUS = 16f

// From one card's middle to the next.
private const val STEP = CARD_W + 14f

// Top of a waiting card; the selected one is `LIFT` higher.
private const val CARD_Y = 78f
private const val LIFT = 20f

// How much the selected card's photo grows.
private const val ZOOM = 0.07f

// Opacity of the text on a waiting card.
private const val DIM = 0.72f

// The shadow: narrower than the card and lower, growing and darkening under the raised card.
private const val SHADOW_INSET = 18f
private const val SHADOW_DROP = 16f
private const val SHADOW_GROW = 0.1f
private const val SHADOW_DIM = 0.4f
private const val SHADOW_FULL = 0.75f

// Past either end the row follows the finger at this share of its speed.
private const val RUBBER = 0.35f

// A flick carries the row on for this many seconds of its speed.
private const val COAST = 0.18f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val ScrollSpring = physical(170f, 20f)
private val LiftSpring = physical(260f, 18f)

// autoPlay (seconds): three steps to the right, then three back, `STEP_TIME` apart.
private const val AUTO_STEPS = 3
private const val STEP_AT = 0.8f
private const val STEP_TIME = 1.1f
private const val BACK_AT = 4.4f
private const val LOOP = 8.4f

// After a touch the loop waits this long, then goes back to the first card and starts over.
private const val RESUME_AFTER = 2f

private fun photoUrl(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=540&h=750&q=80"

private val Places = listOf(
    Place("Turkey", "1,991 Hotels • 42 Packages", photoUrl("1641128324972-af3212f0f6bd"), Color(0xFFA8672D), Color(0xFFE30A17)),
    Place("Indonesia", "1,345 Hotels • 24 Packages", photoUrl("1537996194471-e657df975ab4"), Color(0xFF3F6B54), Color(0xFFFF4D4D)),
    Place("Dubai", "2,345 Hotels • 54 Packages", photoUrl("1518684079-3c830dcef090"), Color(0xFF155E75), Color(0xFF22C55E)),
    Place("Vietnam", "2,178 Hotels • 32 Packages", photoUrl("1528127269322-539801943592"), Color(0xFF2E7366), Color(0xFFFFCD00)),
    Place("Greece", "1,764 Hotels • 38 Packages", photoUrl("1570077188670-e3a8d69ac5ff"), Color(0xFF4A5AA8), Color(0xFF60A5FA)),
)

private fun type(size: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val NameStyle = type(17f, Color.White, FontWeight.Bold)
private val CaptionStyle = type(9.5f, Color.White.copy(alpha = 0.85f))
private val ExploreStyle = type(12.5f, Color.White.copy(alpha = 0.92f), FontWeight.Medium)

// A chevron pointing right, in a 4 × 8 box.
private val Chevron = Path().apply {
    moveTo(0f, 0f)
    lineTo(4f, 4f)
    lineTo(0f, 8f)
}

/**
 * A row of tall photo cards on a rounded panel. The lower half of each card melts into a frosted
 * tint of its photo, under the country's name, a small line and an "Explore Now" bar. The row
 * scrolls sideways and snaps: the card that becomes the selected one rises on a spring over a
 * growing shadow of its own colour, its text brightens and its photo zooms a little, while the panel
 * behind takes on its colour.
 *
 * @param initialIndex The card selected at rest.
 * @param autoPlay Steps to the right three times and back again by itself, in a loop.
 * @param onSelect Called with the index of the card that became the selected one.
 * @param onExplore Called with the index of the card whose bar was tapped.
 */
@Composable
fun FrostedDestinationCarousel(
    places: List<Place> = Places,
    initialIndex: Int = 1,
    exploreLabel: String = "Explore Now",
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    onExplore: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val count = places.size
    val first = initialIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    val auto = autoPlay && !still && count > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelect)
    val explored by rememberUpdatedState(onExplore)

    // The row's position, in cards: 2 means the third card is in the middle.
    val scroll = remember(count, first) { Animatable(first.toFloat()) }
    // How far each card is raised, 0…1.
    val lifts = remember(count, first) { List(count) { Animatable(if (it == first) 1f else 0f) } }
    // Where the row is heading.
    var target by remember(count, first) { mutableIntStateOf(first) }
    // A touch stops the loop; `touches` restarts the wait before it resumes.
    var touching by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    val shadows = remember(places) { places.map { SoftRoundRect(it.tint, sigma = 14f) } }

    // Scrolls to card `index`, carrying on at `speed` cards per second if a drag just ended.
    fun scrollTo(index: Int, speed: Float? = null) {
        val next = index.coerceIn(0, count - 1)
        if (next != target) selected?.invoke(next)
        target = next
        scope.launch {
            if (still) scroll.snapTo(next.toFloat()) else scroll.animateTo(next.toFloat(), ScrollSpring, initialVelocity = speed ?: scroll.velocity)
        }
    }

    fun select(index: Int) {
        touches += 1
        if (index == target) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scrollTo(index)
    }

    // Raises the card nearest the middle and lowers the others.
    LaunchedEffect(scroll, lifts, still) {
        snapshotFlow { scroll.value.roundToInt().coerceIn(0, count - 1) }.collect { nearest ->
            lifts.forEachIndexed { i, lift ->
                val raised = if (i == nearest) 1f else 0f
                launch { if (still) lift.snapTo(raised) else lift.animateTo(raised, LiftSpring) }
            }
        }
    }

    // The script a finger would follow: three steps to the right, then three back.
    LaunchedEffect(auto, touching, touches, count, first) {
        val steps = min(AUTO_STEPS, count - 1 - first)
        if (!auto || touching || steps < 1) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, then go back to the first card before the script restarts.
            delay((RESUME_AFTER * 1000).toLong())
            scrollTo(first)
            delay((STEP_TIME * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((turn * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            for (k in 0 until steps) {
                until(STEP_AT + k * STEP_TIME)
                scrollTo(first + k + 1)
            }
            for (k in 0 until steps) {
                until(BACK_AT + k * STEP_TIME)
                scrollTo(first + steps - k - 1)
            }
            turn += 1
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF1F4F9), Color(0xFFDFE5EE))))
            // Dragging sideways scrolls the row; taps go to the cards.
            .pointerInput(count, still) {
                var from = 0f
                var moved = 0f
                var position = 0f
                var nearest = 0
                val tracker = VelocityTracker()
                fun release() {
                    val speed = -tracker.calculateVelocity().x / density / STEP
                    // A flick carries the row on, but never more than one card past where the drag began.
                    val landing = (position + speed * COAST).roundToInt()
                    val start = from.roundToInt()
                    touching = false
                    touches += 1
                    scrollTo(landing.coerceIn(start - 1, start + 1), speed)
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        from = scroll.value
                        moved = 0f
                        position = from
                        nearest = from.roundToInt()
                        tracker.resetTracking()
                        touching = true
                        scope.launch { scroll.stop() }
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                ) { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    moved += amount / density
                    val free = from - moved / STEP
                    // Past either end the row follows the finger at a third of its speed.
                    val last = count - 1f
                    position = if (free < 0f) free * RUBBER else if (free > last) last + (free - last) * RUBBER else free
                    if (position.roundToInt() != nearest) {
                        nearest = position.roundToInt()
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    scope.launch { scroll.snapTo(position) }
                }
            },
    ) {
        // The panel takes on the colour of whichever card is raised.
        places.forEachIndexed { i, place ->
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = lifts[i].value }
                    .background(Brush.linearGradient(listOf(place.tint.copy(alpha = 0.34f), place.tint.copy(alpha = 0.1f)))),
            )
        }

        // Each card's shadow, in its colour. It stays on the panel while the card rises, and spreads.
        Canvas(Modifier.fillMaxSize()) {
            // Drawn in dp, like the Skia canvases of the React Native version.
            scale(density, density, pivot = Offset.Zero) {
                places.forEachIndexed { i, _ ->
                    if (abs(i - scroll.value) < 2.2f) {
                        val lift = lifts[i].value
                        withTransform({
                            translate(WIDTH / 2f + (i - scroll.value) * STEP, CARD_Y + CARD_H / 2f + SHADOW_DROP)
                            scale(1f + SHADOW_GROW * lift, 1f + SHADOW_GROW * lift, Offset.Zero)
                        }) {
                            drawIntoCanvas {
                                shadows[i].draw(
                                    it.nativeCanvas,
                                    -CARD_W / 2f + SHADOW_INSET, -CARD_H / 2f + SHADOW_INSET,
                                    CARD_W - SHADOW_INSET * 2f, CARD_H - SHADOW_INSET, CARD_RADIUS,
                                    alpha = SHADOW_DIM + (SHADOW_FULL - SHADOW_DIM) * lift,
                                )
                            }
                        }
                    }
                }
            }
        }

        places.forEachIndexed { i, place ->
            Card(
                place = place,
                exploreLabel = exploreLabel,
                lift = { lifts[i].value },
                x = { WIDTH / 2f + (i - scroll.value) * STEP - CARD_W / 2f },
                onPress = { select(i) },
                onExplore = {
                    if (i != target) {
                        select(i)
                    } else {
                        touches += 1
                        explored?.invoke(i)
                    }
                },
            )
        }

        Box(Modifier.matchParentSize().border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(RADIUS.dp)))
    }
}

@Composable
private fun Card(place: Place, exploreLabel: String, lift: () -> Float, x: () -> Float, onPress: () -> Unit, onExplore: () -> Unit) {
    val shape = RoundedCornerShape(CARD_RADIUS.dp)
    Box(
        Modifier
            .size(CARD_W.dp, CARD_H.dp)
            .graphicsLayer {
                translationX = x().dp.toPx()
                translationY = (CARD_Y - LIFT * lift()).dp.toPx()
            }
            .clip(shape)
            .background(place.tint)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onPress),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current).data(place.photo).crossfade(200).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val grow = 1f + ZOOM * lift()
                scaleX = grow
                scaleY = grow
            },
        )
        // The lower half melts into the photo's colour, a little darker towards the bottom.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.36f to place.tint.copy(alpha = 0f), 0.6f to place.tint.copy(alpha = 0.94f), 1f to place.tint))
                .background(Brush.verticalGradient(0.55f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.28f))),
        )

        Column(
            Modifier
                .offset(14.dp, 150.dp)
                .width((CARD_W - 28f).dp)
                // The text is dimmer on the cards that wait.
                .graphicsLayer { alpha = DIM + (1f - DIM) * lift().coerceIn(0f, 1f) },
        ) {
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(place.name, style = NameStyle, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Box(Modifier.size(10.dp).background(place.dot, CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape))
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.height(12.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(place.caption, style = CaptionStyle, maxLines = 1, softWrap = false)
            }
        }

        Row(
            Modifier
                .offset(12.dp, 196.dp)
                .size((CARD_W - 24f).dp, 36.dp)
                .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onExplore)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(exploreLabel, style = ExploreStyle, maxLines = 1, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(4.dp, 8.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(Chevron, Color.White.copy(alpha = 0.92f), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.16f), shape))
    }
}
