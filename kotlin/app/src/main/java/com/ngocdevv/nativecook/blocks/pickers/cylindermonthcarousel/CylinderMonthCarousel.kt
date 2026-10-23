package com.ngocdevv.nativecook.blocks.pickers.cylindermonthcarousel

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The landscape drawn on a card.
 *
 * @param sky with [haze], the sky from the top of the card down to the horizon.
 * @param far with [mid] and [near], three ranges of hills, from the furthest to the ground in front.
 * @param tree with [pine], the round trees and the pines that stand on the middle range.
 * @param sunX where the sun stands across the card, 0…1.
 * @param seed any number: it shapes the hills and places the trees.
 */
data class CarouselScene(
    val sky: Color,
    val haze: Color,
    val sun: Color,
    val far: Color,
    val mid: Color,
    val near: Color,
    val tree: Color,
    val pine: Color,
    val sunX: Float,
    val seed: Float,
)

/**
 * @param locked a lock in the card's corner instead of a heart.
 * @param glow the colour the card casts under the carousel.
 */
data class CarouselCard(val title: String, val artist: String, val locked: Boolean, val glow: Color, val scene: CarouselScene)

private const val MAX_WIDTH = 430f
private const val HEIGHT = 510f

// The cards and the drum they stand on.
private const val CARD_W = 225f
private const val CARD_H = 400f
private const val CARD_RADIUS = 28f
private const val RADIUS = 210f

// Degrees between two cards.
private const val TURN = 56f
private const val EYE = 800f

// How dark a card one place from the front is.
private const val SHADE = 0.4f
private const val CAPTION_RISE = 8f

// The ticks, the month and the glow.
private const val TICKS_Y = 436f
private const val TICK_GAP = 18f

// How many places from the front the ticks start to bunch.
private const val TICK_BUNCH = 4.5f
private const val HAND_OVER = 0.15f
private const val MONTH_Y = 482f
private const val GLOW_Y = 448f
private const val GLOW_W = 200f
private const val GLOW_H = 62f

// Turning.
// Points of drag that turn the drum by one card.
private const val DRAG_STEP = 205f

// Seconds of the finger's speed the drum coasts for.
private const val COAST = 0.2f
private const val STIFFNESS = 180f
private const val DAMPING_RATIO = 0.63f
private const val SPIN_TIME = 1.2f
private const val BAND = 0.35f
private const val TAP_SLOP = 6f

// The drum has landed once it is this close (in cards) to its target.
private const val LAND_WITHIN = 0.03f
private const val CAPTION_IN = 0.25f
private const val CAPTION_OUT = 0.12f

// autoPlay (seconds): when each turn starts, and how many cards from the resting one it goes to.
private const val LOOP = 8f

private class Step(val at: Float, val by: Int, val spin: Boolean)

private val Script = listOf(
    Step(1.0f, 1, false),
    Step(2.3f, 4, false),
    Step(4.1f, 5, false),
    Step(5.3f, 0, true),
)

// After a touch the loop waits this long, then takes `HOME_TIME` to return to the resting card.
private const val RESUME_AFTER = 2f
private const val HOME_TIME = 1.2f

// Ease-in-out that runs a little past the end and comes back.
private val SpinCurve = CubicBezierEasing(0.5f, 0f, 0.3f, 1.1f)

private const val ARTIST = "Mara Lindqvist"

private val Cards = listOf(
    CarouselCard(
        "Autumn Aura", ARTIST, locked = true, glow = Color(0xFFB3261A),
        scene = CarouselScene(Color(0xFF3A3D42), Color(0xFFB9A58C), Color(0xFFF3D9A4), Color(0xFF5A4A44), Color(0xFF8F2F1C), Color(0xFFC2691F), Color(0xFFC8331B), Color(0xFF1F2326), 0.7f, 0.4f),
    ),
    CarouselCard(
        "Rust Valley", ARTIST, locked = true, glow = Color(0xFFC2541D),
        scene = CarouselScene(Color(0xFF2B2F3A), Color(0xFFE09A5A), Color(0xFFFFD9A0), Color(0xFF6B3A2E), Color(0xFFA8431F), Color(0xFF3B2A25), Color(0xFFE2582A), Color(0xFF2A1D1A), 0.3f, 1.7f),
    ),
    CarouselCard(
        "First Frost", ARTIST, locked = false, glow = Color(0xFF6F8FA8),
        scene = CarouselScene(Color(0xFF5B6B7A), Color(0xFFDFE6EA), Color(0xFFFFFFFF), Color(0xFF8394A3), Color(0xFF51616F), Color(0xFFC9D3D9), Color(0xFFA23A26), Color(0xFF2B3A45), 0.55f, 2.9f),
    ),
    CarouselCard(
        "Ember Lake", ARTIST, locked = true, glow = Color(0xFFD2441C),
        scene = CarouselScene(Color(0xFF1C1E2A), Color(0xFFE2552B), Color(0xFFFFB36B), Color(0xFF40222B), Color(0xFF7A1F1F), Color(0xFF1A1416), Color(0xFFF07A2A), Color(0xFF120E10), 0.42f, 4.2f),
    ),
    CarouselCard(
        "Cinder Ridge", ARTIST, locked = false, glow = Color(0xFF8A3A22),
        scene = CarouselScene(Color(0xFF23252B), Color(0xFF7A7F88), Color(0xFFD8D2C4), Color(0xFF4A4D55), Color(0xFF2E3036), Color(0xFF8A3A22), Color(0xFFB8421F), Color(0xFF16181C), 0.78f, 5.6f),
    ),
    CarouselCard(
        "Maple Hollow", ARTIST, locked = true, glow = Color(0xFFE07A1F),
        scene = CarouselScene(Color(0xFF40342F), Color(0xFFF0C27A), Color(0xFFFFF0C0), Color(0xFFA05A2C), Color(0xFFD1452A), Color(0xFF5A2A1C), Color(0xFFF2A23A), Color(0xFF3A1C14), 0.25f, 6.8f),
    ),
    CarouselCard(
        "Amber Dusk", ARTIST, locked = false, glow = Color(0xFFC8641F),
        scene = CarouselScene(Color(0xFF2A2440), Color(0xFFF2A65A), Color(0xFFFFE1A8), Color(0xFF5A3A5A), Color(0xFF8A3A3A), Color(0xFF2A1C26), Color(0xFFF26A3A), Color(0xFF1C1422), 0.62f, 8.1f),
    ),
    CarouselCard(
        "Charcoal Pines", ARTIST, locked = true, glow = Color(0xFF56606A),
        scene = CarouselScene(Color(0xFF15181C), Color(0xFF56606A), Color(0xFFC8D0D6), Color(0xFF2E353C), Color(0xFF1D2227), Color(0xFF3A4148), Color(0xFF7A2A1C), Color(0xFF0E1114), 0.35f, 9.3f),
    ),
)

private val MonthStyle = TextStyle(
    color = Color.White,
    fontSize = 21.sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ArtistStyle = TextStyle(color = Color.White, fontSize = 11.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** What moves. */
private class DrumMotion(home: Int) {
    /** The drum's position as a card index; whole numbers are the snap points. */
    val pos = Animatable(home.toFloat())

    /** The front card's title and artist: 0 hidden … 1 shown. */
    val caption = Animatable(1f)

    /** The card the drum is at, or heading for. */
    var target by mutableIntStateOf(home)

    /** The card that carries the captions: the one the drum last landed on. */
    var captioned by mutableIntStateOf(home)

    /** A turn is under way; it ends when the drum first reaches its target. */
    var moving by mutableStateOf(false)
    var held by mutableStateOf(false)

    /** The card in front at the last frame, for the haptic ticks. */
    var ticked = home

    /** The turns come from a finger, so they tick; the script's do not. */
    var felt = false
}

/**
 * A month of tall picture cards standing on a drum seen from outside. The card in front faces the
 * viewer; its neighbours are turned away around the vertical axis, pushed back and darkened. A
 * horizontal drag turns the drum, which snaps to the nearest card with a small overshoot. Under it a
 * strip of ticks, one per card and bunched towards its far end, slides to keep the front card's tick
 * in the middle, above the month's name and a glow in the front card's colour.
 *
 * @param initial Index of the card in front at first, and the one the self-playing demo rests on.
 * @param autoPlay Turns and flings the drum by itself, in a loop.
 * @param onSelect Called with the index of the card the drum is sent to.
 */
@Composable
fun CylinderMonthCarousel(
    month: String = "Sep 2024",
    cards: List<CarouselCard> = Cards,
    initial: Int = 0,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && cards.size > 1
    val last = cards.lastIndex
    val home = initial.coerceIn(0, last)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember(cards.size, home) { DrumMotion(home) }
    val selected by rememberUpdatedState(onSelect)
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // Sends the drum to card `index`: on a spring that is softer the further it has to turn, or, for the
    // script's long way home, on an ease-in-out spin that overshoots a little.
    fun select(index: Int, velocity: Float = 0f, spin: Boolean = false) {
        val to = index.coerceIn(0, last)
        val far = abs(to - motion.pos.value)
        if (far > LAND_WITHIN) scope.launch { motion.caption.animateTo(0f, tween((CAPTION_OUT * 1000).toInt(), easing = LinearEasing)) }
        val changed = to != motion.target
        motion.target = to
        motion.moving = true
        scope.launch {
            if (still) {
                motion.pos.snapTo(to.toFloat())
            } else if (spin) {
                motion.pos.animateTo(to.toFloat(), tween((SPIN_TIME * 1000).toInt(), easing = SpinCurve))
            } else {
                val stiffness = STIFFNESS / max(1f, far).pow(0.75f)
                motion.pos.animateTo(to.toFloat(), spring(dampingRatio = DAMPING_RATIO, stiffness = stiffness, visibilityThreshold = 0.0005f), initialVelocity = velocity)
            }
        }
        if (changed) selected?.invoke(to)
    }

    // Ticks as cards pass the front, and notices the landing: the captions come back.
    LaunchedEffect(motion, last) {
        snapshotFlow { Triple(motion.pos.value, motion.moving, motion.held) }.collect { (p, moving, held) ->
            val tick = p.roundToInt().coerceIn(0, last)
            if (tick != motion.ticked) {
                motion.ticked = tick
                if (motion.felt) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            if (moving && !held && abs(p - motion.target) < LAND_WITHIN) {
                motion.moving = false
                motion.captioned = motion.target
                launch { motion.caption.animateTo(1f, tween((CAPTION_IN * 1000).toInt(), easing = EaseOutStrong)) }
            }
        }
    }

    // The script a finger would follow: a turn, a fling, a turn, and a long spin home.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the resting card, and start over from there.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.held) return@LaunchedEffect
            motion.felt = false
            select(home)
            delay((HOME_TIME * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for (step in Script) {
                val due = begin + ((turn * LOOP + step.at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                select(home + step.by, spin = step.spin)
            }
            turn += 1
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(HEIGHT.dp), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth.value, MAX_WIDTH)
        Box(
            Modifier
                .size(width.dp, HEIGHT.dp)
                .clipToBounds()
                .pointerInput(motion, width) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // The finger catches the drum wherever it is.
                        touches += 1
                        motion.held = true
                        motion.felt = true
                        scope.launch { motion.pos.stop() }
                        val from = motion.pos.value
                        var p = from
                        var dragged = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val moved = (change.position.x - down.position.x) / density
                            if (!dragged && abs(moved) > TAP_SLOP) {
                                dragged = true
                                motion.moving = true
                                scope.launch { motion.caption.animateTo(0f, tween((CAPTION_OUT * 1000).toInt(), easing = LinearEasing)) }
                            }
                            if (dragged) {
                                change.consume()
                                // Past either end the drum follows the finger at a third of its speed.
                                val raw = from - moved / DRAG_STEP
                                p = if (raw < 0f) raw * BAND else if (raw > last) last + (raw - last) * BAND else raw
                                scope.launch { motion.pos.snapTo(p) }
                            }
                        }
                        touches += 1
                        motion.held = false
                        val x = down.position.x / density - width / 2f
                        if (dragged) {
                            // The drum keeps the finger's speed and settles on the card it would coast to.
                            val speed = -tracker.calculateVelocity().x / density / DRAG_STEP
                            select((p + speed * COAST).roundToInt(), velocity = speed)
                        } else if (down.position.y / density < CARD_H && abs(x) > CARD_W / 2f) {
                            // A tap on a card at the side brings it to the front.
                            select(p.roundToInt() + sign(x).toInt())
                        } else {
                            select(p.roundToInt())
                        }
                    }
                },
        ) {
            // The glow the front card casts, and the ticks over it.
            Canvas(Modifier.size(width.dp, HEIGHT.dp)) {
                val p = motion.pos.value
                val middle = width / 2f
                val glow = if (cards.size < 2) {
                    cards.first().glow
                } else {
                    val i = floor(p).toInt().coerceIn(0, cards.size - 2)
                    lerp(cards[i].glow, cards[i + 1].glow, clamp01(p - i))
                }
                scale(density, density, pivot = Offset.Zero) {
                    withTransform({
                        translate(middle, GLOW_Y)
                        scale(GLOW_W, GLOW_H, pivot = Offset.Zero)
                    }) {
                        drawCircle(
                            Brush.radialGradient(
                                0f to glow.copy(alpha = 0.55f), 0.5f to glow.copy(alpha = 0.2f), 1f to glow.copy(alpha = 0f),
                                center = Offset.Zero, radius = 1f,
                            ),
                            radius = 1f, center = Offset.Zero,
                        )
                    }
                    for (index in cards.indices) {
                        val d = index - p
                        // The tick under the front card is white, thicker and taller; it hands over half-way.
                        val lit = clamp01((0.5f - abs(d)) / HAND_OVER + 0.5f)
                        val wide = mix(2f, 3f, lit)
                        val tall = mix(20f, 26f, lit)
                        val x = middle + TICK_GAP * TICK_BUNCH * tanh(d / TICK_BUNCH)
                        drawRoundRect(
                            Color.White.copy(alpha = mix(0.35f, 1f, lit)),
                            topLeft = Offset(x - wide / 2f, TICKS_Y - tall / 2f),
                            size = Size(wide, tall),
                            cornerRadius = CornerRadius(1f),
                        )
                    }
                }
            }

            cards.forEachIndexed { index, card ->
                Card(index, card, width, motion)
            }

            BasicText(month, Modifier.offset(0.dp, (MONTH_Y - 14f).dp).size(width.dp, 28.dp), style = MonthStyle, maxLines = 1)
        }
    }
}

/** One card: its picture, the shade it takes as it turns away, its badge and, in front, its captions. */
@Composable
private fun Card(index: Int, card: CarouselCard, width: Float, motion: DrumMotion) {
    // Cards nearer the front are drawn over the others.
    val order by remember(index, motion) { derivedStateOf { -abs(index - motion.pos.value.roundToInt()).toFloat() } }
    val density = LocalDensity.current.density
    val titleStyle = remember(density) {
        TextStyle(
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f * density), 6f * density),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
    Box(
        Modifier
            .zIndex(order)
            .offset(((width - CARD_W) / 2f).dp, 0.dp)
            .size(CARD_W.dp, CARD_H.dp)
            // Where the card stands: on a drum of radius `RADIUS`, seen from `EYE` dp away, so it moves
            // sideways less and shrinks as it goes round.
            .graphicsLayer {
                val d = index - motion.pos.value
                val angle = d * TURN * Math.PI.toFloat() / 180f
                val shrink = EYE / (EYE + RADIUS * (1f - cos(angle)))
                alpha = if (abs(d) * TURN < 89f) 1f else 0f
                translationX = RADIUS * sin(angle) * shrink * this.density
                scaleX = shrink
                scaleY = shrink
                rotationY = d * TURN
                // The layer counts the eye's distance in units of 160 dp.
                cameraDistance = EYE / 160f
                shape = RoundedCornerShape(CARD_RADIUS.dp)
                clip = true
            },
    ) {
        // The picture never changes, so it keeps its own layer and is only moved afterwards.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            scale(density, density, pivot = Offset.Zero) { drawScene(card.scene) }
        }
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = SHADE * min(1f, abs(index - motion.pos.value))) })

        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(34.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                if (card.locked) Icons.Filled.Lock else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(if (card.locked) 15.dp else 17.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val shown = if (motion.captioned == index) motion.caption.value else 0f
                alpha = shown
                translationY = (1f - shown) * CAPTION_RISE * this.density
            },
        ) {
            BasicText(card.title, Modifier.offset(0.dp, (CARD_H / 3f - 13f).dp).size(CARD_W.dp, 26.dp), style = titleStyle, maxLines = 1)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(0.dp, (CARD_H - 54f).dp)
                    .height(28.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(card.artist, style = ArtistStyle, maxLines = 1)
            }
        }
    }
}

/** A range of hills: a sine wave across the card at the height [level], filled down to the bottom. */
private fun ridge(level: Float, swell: Float, waves: Float, turn: Float) = Path().apply {
    moveTo(0f, CARD_H)
    for (step in 0..24) {
        val u = step / 24f
        lineTo(u * CARD_W, CARD_H * (level + swell * sin(u * TAU * waves + turn)))
    }
    lineTo(CARD_W, CARD_H)
    close()
}

/** The picture on a card, in a 225 × 400 dp box. */
private fun DrawScope.drawScene(scene: CarouselScene) {
    val w = CARD_W
    val h = CARD_H
    val seed = scene.seed
    drawRect(Brush.verticalGradient(listOf(scene.sky, scene.haze), startY = 0f, endY = h * 0.62f), size = Size(w, h))
    val sun = Offset(w * scene.sunX, h * (0.16f + 0.04f * sin(seed * 5f)))
    drawCircle(scene.sun.copy(alpha = 0.22f), 62f, sun)
    drawCircle(scene.sun, 30f, sun)

    drawPath(ridge(0.5f, 0.05f, 1.1f, seed), scene.far)
    drawPath(ridge(0.62f, 0.06f, 0.8f, seed * 2.3f + 1f), scene.mid)
    for (k in 0 until 6) {
        val u = (k + 0.5f + 0.35f * sin(seed * 7f + k * 2.4f)) / 6f
        val x = u * w
        val base = h * (0.62f + 0.06f * sin(u * TAU * 0.8f + seed * 2.3f + 1f)) + 8f
        val tall = 44f + 26f * (0.5f + 0.5f * sin(seed * 3f + k * 1.7f))
        if (k % 2 == 0) {
            drawRect(scene.pine, topLeft = Offset(x - 1.5f, base - tall * 0.5f), size = Size(3f, tall * 0.5f))
            drawOval(scene.tree, topLeft = Offset(x - tall * 0.3f, base - tall), size = Size(tall * 0.6f, tall * 0.78f))
        } else {
            val pine = Path().apply {
                moveTo(x - tall * 0.24f, base)
                lineTo(x, base - tall * 1.15f)
                lineTo(x + tall * 0.24f, base)
                close()
            }
            drawPath(pine, scene.pine)
        }
    }
    drawPath(ridge(0.8f, 0.035f, 0.6f, seed * 3.7f + 2f), scene.near)
    // The bottom darkens so the artist's pill reads on any picture.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), startY = h * 0.6f, endY = h),
        topLeft = Offset(0f, h * 0.6f),
        size = Size(w, h * 0.4f),
    )
}
