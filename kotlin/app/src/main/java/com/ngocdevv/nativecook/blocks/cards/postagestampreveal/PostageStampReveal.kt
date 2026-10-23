package com.ngocdevv.nativecook.blocks.cards.postagestampreveal

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One flat shape of a stamp's illustration.
 *
 * @param path An SVG path (absolute M, L, Q, C and Z) drawn in the stamp's 156 × 204 window. Let it
 * run past the window on both sides: the layers slide against each other.
 * @param depth 0 for the horizon, 1 for the foreground: nearer layers travel further.
 */
class PostageStampLayer(path: String, val color: Color, val depth: Float) {
    internal val shape = svgPath(path)
}

/**
 * @param top The two words of the name, set above and below the stamp.
 * @param caption The line of coordinates along the bottom edge.
 * @param paper Colour of the panel while this place is shown.
 * @param ink Colour of the big name.
 * @param regionInk Colour of the region printed on the illustration.
 * @param ground Ground colour of the small stamp that peeks in from a corner.
 * @param layers The illustration, back to front.
 */
class PostageStampPlace(
    val top: String,
    val bottom: String,
    val region: String,
    val caption: String,
    val paper: Color,
    val ink: Color,
    val regionInk: Color,
    val sky: Color,
    val ground: Color,
    val layers: List<PostageStampLayer>,
    val country: String = "ITALIA",
)

private const val WIDTH = 340f
private const val HEIGHT = 420f
private const val RADIUS = 10f

// The stamp with its perforated border, and the window its illustration shows through.
private val STAMP = Rect(80f, 96f, 260f, 324f)
private val WINDOW = Rect(92f, 108f, 248f, 312f)

// Perforations: circles of this radius, centred on the edge, one per pitch.
private const val PITCH = 12f
private const val HOLE = 3.6f

// Soft shadows the stamps cast on the paper.
private val SHADOW = Offset(4f, 7f)
private const val SHADOW_BLUR = 6f
private val MINI_SHADOW = Offset(3f, 5f)
private const val MINI_SHADOW_BLUR = 5f

// The small stamps of the neighbouring places in two corners.
private val MINI = Size(96f, 120f)
private const val MINI_BORDER = 9f
private const val MINI_HOLE = 3.2f
private val MINI_TOP_LEFT = Offset(-34f, -22f)
private val MINI_BOTTOM_RIGHT = Offset(294f, 350f)
private val MINI_TRAVEL = Offset(70f, 80f)

// Scale at which the window covers the whole panel.
private const val OPEN_SCALE = 2.2f

// Opening pushes near layers down and towards the viewer more than far ones.
private const val OPEN_LIFT = 14f
private const val OPEN_ZOOM = 0.14f
private val PIVOT = Offset(78f, 122f)

// How far a far layer lags behind its scene while it slides in.
private const val SLIDE_LAG = 44f

// Centre lines of the two words: behind the stamp, and stacked at the top once the place is open.
private const val TITLE_SIZE = 64f
private const val TITLE_LINE = 76f
private const val TITLE_TOP = 89f
private const val TITLE_BOTTOM = 332f
private const val OPEN_TITLE_TOP = 68f
private const val OPEN_TITLE_BOTTOM = 126f

// The words slide further than the illustration, the lower one further still.
private const val TITLE_SHIFT = 300f
private const val TITLE_STAGGER = 1.2f

// Finger travel that moves one place.
private const val DRAG_SPAN = 200f

// Seconds of travel a release is credited with, as SwiftUI predicts the end of a drag.
private const val THROW = 0.25f

private const val SLIDE_STIFFNESS = 130f
private const val SLIDE_DAMPING = 19f
private const val OPEN_STIFFNESS = 90f
private const val OPEN_DAMPING = 14f
private const val REDUCED_MS = 200

// autoPlay (seconds): next place, open, close, next, next, which is the first place again.
private val SCRIPT = listOf(0.8f to false, 2.3f to true, 4.3f to true, 5.7f to false, 7.0f to false)
private const val LOOP = 8.4f

// After a touch the loop waits this long, returns to the first place, and starts over.
private const val IDLE = 2.5f
private const val SETTLE = 1.2f

private const val GRAIN = 700
private val Cream = Color(0xFFEFE6CF)
private val TrailColor = Color(0xFFF2C64F)
private val Label = rgba(255, 255, 255, 0.92f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun stampText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val WordStyle = stampText(TITLE_SIZE, TITLE_LINE, FontWeight.Black, Color.White, -1.5f)
private val CountryStyle = stampText(11f, 14f, FontWeight.ExtraBold, Color.White, 0.4f)
private val RegionStyle = stampText(10f, 13f, FontWeight.ExtraBold, Color.White, 0.6f)
private val EmblemStyle = stampText(10f, 12f, FontWeight.ExtraBold, Color.White)
private val CloseStyle = stampText(8f, 10f, FontWeight.ExtraBold, Color(0xFF2C2A26), 0.8f)
private val LabelStyle = stampText(7.5f, 10f, FontWeight.ExtraBold, Label, 0.5f)
private val CaptionStyle = stampText(6.5f, 9f, FontWeight.Bold, Label, 0.4f).copy(textAlign = TextAlign.Center)

private val NumberOrCommand = Regex("[MLQCZ]|-?\\d+(?:\\.\\d+)?")

private fun svgPath(svg: String): Path {
    val path = Path()
    val tokens = NumberOrCommand.findAll(svg).map { it.value }.toList()
    var at = 0
    fun number() = tokens[at++].toFloat()
    while (at < tokens.size) {
        when (tokens[at++]) {
            "M" -> path.moveTo(number(), number())
            "L" -> path.lineTo(number(), number())
            "Q" -> path.quadraticTo(number(), number(), number(), number())
            "C" -> path.cubicTo(number(), number(), number(), number(), number(), number())
            else -> path.close()
        }
    }
    return path
}

/**
 * A rectangle and a circle on every perforation; filled even-odd and clipped to the rectangle,
 * that leaves a half-round bite at each one.
 */
private fun perforated(rect: Rect, hole: Float) = Path().apply {
    addRect(rect)
    for (i in 0 until (rect.width / PITCH).roundToInt()) {
        val x = rect.left + PITCH * (i + 0.5f)
        addOval(Rect(Offset(x, rect.top), hole))
        addOval(Rect(Offset(x, rect.bottom), hole))
    }
    for (i in 0 until (rect.height / PITCH).roundToInt()) {
        val y = rect.top + PITCH * (i + 0.5f)
        addOval(Rect(Offset(rect.left, y), hole))
        addOval(Rect(Offset(rect.right, y), hole))
    }
    fillType = PathFillType.EvenOdd
}

private class StampArt {
    val edge = perforated(STAMP, HOLE)
    val miniEdge = perforated(Rect(Offset.Zero, MINI), MINI_HOLE)
    val miniGround = svgPath("M0 70 C24 54 60 50 96 66 L96 120 L0 120 Z")
    val trail = svgPath("M-10 236 C4 200 22 192 34 216 C44 238 60 240 66 216 C72 190 92 186 110 214")
    val dashes = Stroke(width = 5.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.5f, 6.5f)))
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = SHADOW_BLUR)
    val miniShadow = SoftRoundRect(rgba(0, 0, 0, 0.26f), sigma = MINI_SHADOW_BLUR)

    // Two sets of one-point specks from a fixed sequence (Park–Miller), so the grain never changes.
    val light = Path()
    val dark = Path()

    init {
        var seed = 7L
        fun next(): Float {
            seed = seed * 16807L % 2147483647L
            return seed / 2147483647f
        }
        for (i in 0 until GRAIN * 2) {
            val x = next() * WIDTH
            val y = next() * HEIGHT
            (if (i % 2 == 0) light else dark).addRect(Rect(x, y, x + 1f, y + 1f))
        }
    }
}

/** Signed distance of place [i] from the one on show, wrapped so the last place neighbours the first. */
private fun relOf(i: Int, pos: Float, n: Int): Float {
    var r = (i - pos) % n
    if (r < -n / 2f) r += n else if (r >= n / 2f) r -= n
    return r
}

private fun wrap(i: Int, n: Int) = ((i % n) + n) % n

/** 1 at rest on a place, 0 halfway between two. */
private fun settledOf(pos: Float): Float {
    val d = min(1f, abs(pos - pos.roundToInt()) * 2f)
    return 1f - d * d * (3f - 2f * d)
}

/** Under reduced motion the stamp jumps open instead of growing. */
private fun scaleOf(open: Float, reduced: Boolean) = when {
    reduced -> if (open > 0.5f) OPEN_SCALE else 1f
    else -> 1f + (OPEN_SCALE - 1f) * open
}

/** A spring of mass 1, or a short ease under reduced motion. */
private fun motionSpec(reduced: Boolean, stiffness: Float, damping: Float): AnimationSpec<Float> = when {
    reduced -> tween(REDUCED_MS, easing = EaseOutStrong)
    else -> spring(damping / (2f * sqrt(stiffness)), stiffness)
}

/** The two animated numbers, and where they are heading. */
private class StampMotion(private val scope: CoroutineScope) {
    /** Index of the place on show; fractional while one slides into the other, and it keeps counting past the last place. */
    val pos = mutableFloatStateOf(0f)
    val open = mutableFloatStateOf(0f)
    var target = 0
    var isOpen = false
    var pressed = false

    // Where a drag started, and how far the finger has gone since, in pixels.
    var dragFrom = 0
    var dragged = 0f
    private var sliding: Job? = null
    private var opening: Job? = null

    fun hold() {
        sliding?.cancel()
    }

    fun slide(to: Int, reduced: Boolean, velocity: Float = 0f) {
        target = to
        sliding?.cancel()
        sliding = scope.launch {
            animate(pos.floatValue, to.toFloat(), velocity, motionSpec(reduced, SLIDE_STIFFNESS, SLIDE_DAMPING)) { value, _ -> pos.floatValue = value }
        }
    }

    /** Opens or closes the place; true when it opens. */
    fun toggle(reduced: Boolean): Boolean {
        isOpen = !isOpen
        val to = if (isOpen) 1f else 0f
        opening?.cancel()
        opening = scope.launch {
            animate(open.floatValue, to, 0f, motionSpec(reduced, OPEN_STIFFNESS, OPEN_DAMPING)) { value, _ -> open.floatValue = value }
        }
        return isOpen
    }

    /** The same place under a smaller number, so the counter never grows. */
    fun rewind() {
        sliding?.cancel()
        target = 0
        pos.floatValue = 0f
    }
}

/**
 * A place shown as a postage stamp on a panel of coloured paper, its name in huge capitals behind
 * the stamp. A horizontal swipe slides the next illustration into the stamp while the paper and the
 * name change with it; a tap opens the place: the stamp grows until its illustration fills the
 * panel, the perforated border leaving through the edges, and the name comes forward over the scene.
 *
 * @param autoPlay Plays the interaction by itself: next place, open, close, next, next.
 * @param labels The three small labels along the top edge.
 * @param emblem The letter in the boxed emblem of the stamp.
 * @param onSelect Called with the index of the place a swipe lands on.
 * @param onOpen Called with the index of the place that was opened.
 * @param onClose Called with the index of the place that was closed.
 */
@Composable
fun PostageStampReveal(
    places: List<PostageStampPlace> = PostageStampPlaces,
    autoPlay: Boolean = false,
    labels: List<String> = listOf("INDEX", "ALL", "ABOUT"),
    emblem: String = "B",
    closeLabel: String = "CLOSE",
    onSelect: ((index: Int) -> Unit)? = null,
    onOpen: ((index: Int) -> Unit)? = null,
    onClose: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val count = places.size
    if (count == 0) return
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember { StampMotion(scope) }
    val art = remember { StampArt() }
    // Counts touches: a change restarts the autoPlay loop after a pause.
    var touches by remember { mutableIntStateOf(0) }
    val select by rememberUpdatedState(onSelect)
    val opened by rememberUpdatedState(onOpen)
    val closed by rememberUpdatedState(onClose)

    LaunchedEffect(auto, touches) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // After a touch the loop waits, walks back to the first place and starts over.
            delay((IDLE * 1000).toLong())
            if (motion.pressed) return@LaunchedEffect
            if (motion.isOpen) motion.toggle(reduced)
            motion.slide((motion.target.toFloat() / count).roundToInt() * count, reduced)
            delay((SETTLE * 1000).toLong())
        }
        while (true) {
            motion.rewind()
            var clock = 0f
            for ((at, opens) in SCRIPT) {
                delay(((at - clock) * 1000).toLong())
                clock = at
                if (opens) motion.toggle(reduced) else motion.slide(motion.target + 1, reduced)
            }
            delay(((LOOP - clock) * 1000).toLong())
        }
    }

    val pos = motion.pos
    val open = motion.open
    val near by remember(count) { derivedStateOf { wrap(pos.floatValue.roundToInt(), count) } }
    // Under reduced motion nothing slides or grows: scenes cross-fade and the stamp jumps open.
    val travel = if (reduced) 0f else 1f

    val density = LocalDensity.current.density
    val drag = rememberDraggableState { delta ->
        if (!motion.isOpen) {
            motion.dragged += delta
            motion.pos.floatValue = motion.dragFrom - motion.dragged / (DRAG_SPAN * density)
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .clip(RoundedCornerShape(RADIUS.dp))
            .clearAndSetSemantics {
                contentDescription = "${places[near].top} ${places[near].bottom}, ${places[near].region}"
                role = Role.Button
            }
            .pointerInput(count) {
                detectTapGestures {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val index = wrap(motion.target, count)
                    if (motion.toggle(reduced)) opened?.invoke(index) else closed?.invoke(index)
                    touches++
                }
            }
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    motion.pressed = true
                    motion.hold()
                    motion.dragFrom = motion.target
                    motion.dragged = 0f
                    touches++
                },
                onDragStopped = { velocityPx ->
                    motion.pressed = false
                    if (!motion.isOpen) {
                        // Pixels per second to places per second.
                        val velocity = -velocityPx / (DRAG_SPAN * density)
                        // A swipe moves one place at most, however far it is thrown.
                        val thrown = (motion.pos.floatValue + velocity * THROW).roundToInt()
                        val target = thrown.coerceIn(motion.dragFrom - 1, motion.dragFrom + 1)
                        motion.slide(target, reduced, velocity)
                        if (target != motion.dragFrom) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            select?.invoke(wrap(target, count))
                        }
                    }
                    touches++
                },
            ),
    ) {
        // The paper, the dotted trail and the two corner stamps: everything behind the name.
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, _, _ ->
                val p = pos.floatValue
                val o = open.floatValue.coerceIn(0f, 1f)
                val base = floor(p).toInt()
                // The next place's colour is laid over this one's as it slides in.
                drawRect(places[wrap(base, count)].paper, size = Size(WIDTH, HEIGHT))
                drawRect(places[wrap(base + 1, count)].paper, size = Size(WIDTH, HEIGHT), alpha = p - base)

                // Both leave while a place slides or is open, and come back for the next one.
                val shown = settledOf(p) * (1f - o)
                translate((p.roundToInt() - p) * 30f * travel, 0f) {
                    drawPath(art.trail, TrailColor, alpha = shown, style = art.dashes)
                }
                val away = (1f - shown) * travel
                val alpha = if (reduced) shown else 1f
                translate(MINI_TOP_LEFT.x - away * MINI_TRAVEL.x, MINI_TOP_LEFT.y - away * MINI_TRAVEL.y) {
                    drawMini(art, places[wrap(p.roundToInt() - 1, count)], alpha)
                }
                translate(MINI_BOTTOM_RIGHT.x + away * MINI_TRAVEL.x, MINI_BOTTOM_RIGHT.y + away * MINI_TRAVEL.y) {
                    drawMini(art, places[wrap(p.roundToInt() + 1, count)], alpha)
                }
            }
        }

        places.forEachIndexed { i, place ->
            StampWord(place.top, place.ink, TITLE_TOP) {
                val r = relOf(i, pos.floatValue, count)
                alpha = if (reduced) max(0f, 1f - abs(r)) else 1f
                translationX = (r * TITLE_SHIFT * travel).dp.toPx()
            }
            StampWord(place.bottom, place.ink, TITLE_BOTTOM) {
                val r = relOf(i, pos.floatValue, count)
                alpha = if (reduced) max(0f, 1f - abs(r)) else 1f
                translationX = (r * TITLE_SHIFT * TITLE_STAGGER * travel).dp.toPx()
            }
        }

        // The stamp: shadow, perforated paper and the illustrations sliding through its window.
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, _, _ ->
                val p = pos.floatValue
                val o = open.floatValue
                scale(scaleOf(o, reduced), Offset(WIDTH / 2f, HEIGHT / 2f)) {
                    drawIntoCanvas {
                        art.shadow.draw(
                            it.nativeCanvas, STAMP.left + SHADOW.x, STAMP.top + SHADOW.y, STAMP.width, STAMP.height, 0f,
                            alpha = 1f - o.coerceIn(0f, 1f),
                        )
                    }
                    clipRect(STAMP.left, STAMP.top, STAMP.right, STAMP.bottom) { drawPath(art.edge, Cream) }
                    clipRect(WINDOW.left, WINDOW.top, WINDOW.right, WINDOW.bottom) {
                        places.forEachIndexed { i, place ->
                            val r = relOf(i, p, count)
                            if (abs(r) < 1f) {
                                val alpha = if (reduced) 1f - abs(r) else 1f
                                translate(WINDOW.left + r * WINDOW.width * travel, WINDOW.top) {
                                    clipRect(0f, 0f, WINDOW.width, WINDOW.height) {
                                        drawRect(place.sky, size = WINDOW.size, alpha = alpha)
                                        for (layer in place.layers) {
                                            withTransform({
                                                translate(
                                                    -r * (1f - layer.depth) * SLIDE_LAG * travel,
                                                    o * (layer.depth - 0.5f) * OPEN_LIFT * travel,
                                                )
                                                scale(1f + o * layer.depth * OPEN_ZOOM * travel, PIVOT)
                                            }) {
                                                drawPath(layer.shape, layer.color, alpha = alpha)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Paper grain over the whole panel, the same specks every frame.
                drawPath(art.light, rgba(255, 255, 255, 0.07f))
                drawPath(art.dark, rgba(0, 0, 0, 0.06f))
            }
        }

        // What is printed on the stamp: it grows with it and is gone before the border is.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val scale = scaleOf(open.floatValue, reduced)
                scaleX = scale
                scaleY = scale
                alpha = max(0f, 1f - open.floatValue.coerceIn(0f, 1f) * 3f)
            },
        ) {
            BasicText(
                places[near].country,
                style = CountryStyle,
                maxLines = 1,
                modifier = Modifier.offset((WINDOW.left + 9f).dp, (WINDOW.top + 9f).dp),
            )
            Box(
                Modifier
                    .offset((WINDOW.right - 9f - 15f).dp, (WINDOW.top + 9f).dp)
                    .size(15.dp)
                    .border(1.3.dp, Color.White, RoundedCornerShape(2.5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(emblem, style = EmblemStyle, maxLines = 1)
            }
            places.forEachIndexed { i, place ->
                BasicText(
                    place.region,
                    style = RegionStyle.copy(color = place.regionInk),
                    maxLines = 1,
                    modifier = Modifier
                        .offset((WINDOW.left + 9f).dp, (WINDOW.bottom - 10f - 13f).dp)
                        .graphicsLayer { alpha = max(0f, 1f - abs(relOf(i, pos.floatValue, count)) * 2f) },
                )
            }
        }

        // The name again, in front of the scene once the place is open.
        StampWord(places[near].top, places[near].ink, TITLE_TOP) {
            alpha = ((open.floatValue.coerceIn(0f, 1f) - 0.15f) / 0.35f).coerceIn(0f, 1f)
            translationY = ((OPEN_TITLE_TOP - TITLE_TOP) * open.floatValue * travel).dp.toPx()
        }
        StampWord(places[near].bottom, places[near].ink, TITLE_BOTTOM) {
            alpha = ((open.floatValue.coerceIn(0f, 1f) - 0.15f) / 0.35f).coerceIn(0f, 1f)
            translationY = ((OPEN_TITLE_BOTTOM - TITLE_BOTTOM) * open.floatValue * travel).dp.toPx()
        }

        Box(
            Modifier
                .offset((WIDTH / 2f - 31f).dp, (HEIGHT - 50f - 12f).dp)
                .size(62.dp, 24.dp)
                .graphicsLayer {
                    alpha = ((open.floatValue.coerceIn(0f, 1f) - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    translationY = ((1f - open.floatValue) * 10f * travel).dp.toPx()
                }
                .background(Cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(closeLabel, style = CloseStyle, maxLines = 1)
        }

        Row(
            Modifier.offset(14.dp, 11.dp).width((WIDTH - 28f).dp).height(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(labels.getOrElse(0) { "" }, style = LabelStyle, maxLines = 1)
                Box(Modifier.size(4.dp).background(Label, CircleShape))
            }
            BasicText(labels.getOrElse(2) { "" }, style = LabelStyle, maxLines = 1)
        }
        BasicText(
            labels.getOrElse(1) { "" },
            style = LabelStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = Modifier.offset(14.dp, 11.dp).width((WIDTH - 28f).dp),
        )

        BasicText(
            places[near].caption,
            style = CaptionStyle,
            maxLines = 1,
            modifier = Modifier
                .offset(14.dp, (HEIGHT - 15f - 4.5f).dp)
                .width((WIDTH - 28f).dp)
                .graphicsLayer { alpha = settledOf(pos.floatValue) },
        )
    }
}

/** One word of the name, centred on the panel at [centreY] however wide it is. */
@Composable
private fun StampWord(text: String, color: Color, centreY: Float, layer: GraphicsLayerScope.() -> Unit) {
    BasicText(
        text,
        style = WordStyle.copy(color = color),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .offset(0.dp, (centreY - TITLE_LINE / 2f).dp)
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
            .graphicsLayer(layer),
    )
}

private fun DrawScope.drawMini(art: StampArt, place: PostageStampPlace, alpha: Float) {
    drawIntoCanvas {
        art.miniShadow.draw(it.nativeCanvas, MINI_SHADOW.x, MINI_SHADOW.y, MINI.width, MINI.height, 0f, alpha = alpha)
    }
    clipRect(0f, 0f, MINI.width, MINI.height) { drawPath(art.miniEdge, Cream, alpha = alpha) }
    clipRect(MINI_BORDER, MINI_BORDER, MINI.width - MINI_BORDER, MINI.height - MINI_BORDER) {
        drawRect(place.sky, size = MINI, alpha = alpha)
        drawPath(art.miniGround, place.ground, alpha = alpha)
    }
}

/** A snowy hill with a hut, rock towers at sunset, and sea stacks. */
val PostageStampPlaces = listOf(
    PostageStampPlace(
        top = "MONTE", bottom = "TOMBA", region = "MONTI LESSINI",
        caption = "BOSCO CHIESANUOVA · N 45°41′31″ · E 11°03′27″",
        paper = Color(0xFF5D8FC0), ink = Color(0xFFE58BB5), regionInk = Color(0xFF3F6FC4),
        sky = Color(0xFF5B97EA), ground = Color(0xFFF4F1E8),
        layers = listOf(
            PostageStampLayer("M-40 124 C0 106 40 100 80 110 C120 120 160 102 200 98 L200 250 L-40 250 Z", Color(0xFF86B4F1), 0.25f),
            PostageStampLayer("M106.4 50 L107.6 50 L107.6 72 L106.4 72 Z", Color(0xFF2E1F6E), 0.6f),
            PostageStampLayer("M103 55 L111 55 L111 56.2 L103 56.2 Z", Color(0xFF2E1F6E), 0.6f),
            PostageStampLayer("M104.5 60 L109.5 60 L109.5 61.2 L104.5 61.2 Z", Color(0xFF2E1F6E), 0.6f),
            PostageStampLayer("M100 80 L114 80 L114 110 L100 110 Z", Color(0xFF5A3FB5), 0.6f),
            PostageStampLayer("M114 80 L127 84 L127 110 L114 110 Z", Color(0xFFD77FB4), 0.6f),
            PostageStampLayer("M98 81 L107 71 L116 81 Z", Color(0xFF2E1F6E), 0.6f),
            PostageStampLayer("M107 71 L120 75 L129 85 L116 81 Z", Color(0xFF40298C), 0.6f),
            PostageStampLayer("M-40 140 C10 120 60 100 104 99 C140 98 170 106 200 116 L200 250 L-40 250 Z", Color(0xFFF4F1E8), 1f),
            PostageStampLayer("M-40 186 C30 160 90 162 200 184 L200 250 L-40 250 Z", Color(0xFFE2E8F3), 1f),
        ),
    ),
    PostageStampPlace(
        top = "CINQUE", bottom = "TORRI", region = "DOLOMITI",
        caption = "CORTINA D'AMPEZZO · N 46°30′38″ · E 12°03′07″",
        paper = Color(0xFFCF7FAE), ink = Color(0xFFF2C44E), regionInk = Color(0xFFFBE9D2),
        sky = Color(0xFFEF9A76), ground = Color(0xFF8A2F55),
        layers = listOf(
            PostageStampLayer("M124 40 C131.73 40 138 46.27 138 54 C138 61.73 131.73 68 124 68 C116.27 68 110 61.73 110 54 C110 46.27 116.27 40 124 40 Z", Color(0xFFFDE2B4), 0.1f),
            PostageStampLayer("M-40 138 L-6 112 L18 126 L46 100 L70 122 L104 104 L134 126 L160 108 L200 130 L200 250 L-40 250 Z", Color(0xFFDC7A80), 0.3f),
            PostageStampLayer("M26 160 L30 104 L41 94 L52 102 L54 160 Z", Color(0xFFEE9DBD), 0.65f),
            PostageStampLayer("M52 102 L61 109 L62 160 L54 160 Z", Color(0xFFA94A76), 0.65f),
            PostageStampLayer("M64 160 L68 70 L81 60 L94 70 L97 160 Z", Color(0xFFEE9DBD), 0.65f),
            PostageStampLayer("M94 70 L105 80 L107 160 L97 160 Z", Color(0xFFA94A76), 0.65f),
            PostageStampLayer("M112 160 L115 110 L124 102 L133 110 L135 160 Z", Color(0xFFEE9DBD), 0.65f),
            PostageStampLayer("M133 110 L141 117 L142 160 L135 160 Z", Color(0xFFA94A76), 0.65f),
            PostageStampLayer("M-40 150 C10 140 50 152 90 146 C130 140 160 150 200 142 L200 250 L-40 250 Z", Color(0xFFC4577A), 0.85f),
            PostageStampLayer("M-40 178 C20 160 70 176 110 170 C150 164 170 176 200 168 L200 250 L-40 250 Z", Color(0xFF8A2F55), 1f),
        ),
    ),
    PostageStampPlace(
        top = "ISOLA", bottom = "BELLA", region = "TAORMINA",
        caption = "BAIA DI TAORMINA · N 37°51′02″ · E 15°18′01″",
        paper = Color(0xFF3F9C80), ink = Color(0xFFF4A3C6), regionInk = Color(0xFFFFFFFF),
        sky = Color(0xFFA8DDE0), ground = Color(0xFF2389A0),
        layers = listOf(
            PostageStampLayer("M118 43 C126.28 43 133 49.72 133 58 C133 66.28 126.28 73 118 73 C109.72 73 103 66.28 103 58 C103 49.72 109.72 43 118 43 Z", Color(0xFFFFF4CF), 0.1f),
            PostageStampLayer("M-40 124 L200 124 L200 250 L-40 250 Z", Color(0xFF2389A0), 0.3f),
            PostageStampLayer("M22 160 L28 100 L40 88 L58 92 L64 108 L68 160 Z", Color(0xFF3F5C94), 0.65f),
            PostageStampLayer("M58 92 L70 100 L78 160 L68 160 L64 108 Z", Color(0xFF24365E), 0.65f),
            PostageStampLayer("M28 100 L40 88 L58 92 L52 99 L38 102 Z", Color(0xFF5CC08A), 0.65f),
            PostageStampLayer("M88 160 L92 72 L101 62 L110 72 L112 160 Z", Color(0xFF3F5C94), 0.65f),
            PostageStampLayer("M110 72 L118 81 L120 160 L112 160 Z", Color(0xFF24365E), 0.65f),
            PostageStampLayer("M92 72 L101 62 L110 72 L103 76 Z", Color(0xFF5CC08A), 0.65f),
            PostageStampLayer("M126 160 L128 122 L135 115 L142 122 L144 160 Z", Color(0xFF3F5C94), 0.65f),
            PostageStampLayer("M142 122 L148 128 L149 160 L144 160 Z", Color(0xFF24365E), 0.65f),
            PostageStampLayer("M-40 158 C0 150 30 164 70 157 C110 150 140 164 200 154 L200 250 L-40 250 Z", Color(0xFF45B5B0), 1f),
            PostageStampLayer("M-40 158 C0 150 30 164 70 157 C110 150 140 164 200 154 L200 158 C140 168 110 154 70 161 C30 168 0 154 -40 162 Z", Color(0xFFEEFAF3), 1f),
        ),
    ),
)
