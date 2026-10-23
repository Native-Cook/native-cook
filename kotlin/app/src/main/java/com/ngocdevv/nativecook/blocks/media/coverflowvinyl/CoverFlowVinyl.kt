package com.ngocdevv.nativecook.blocks.media.coverflowvinyl

import android.graphics.Matrix
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.launch

/** @param length Track length and [startAt] where playback starts, in seconds. */
data class VinylAlbum(val title: String, val artist: String, val cover: String, val length: Float, val startAt: Float)

private fun cover(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=600&h=600&q=80"

private val Albums = listOf(
    VinylAlbum("Low Tide Radio", "Mara Quell", cover("photo-1644855640845-ab57a047320e"), 228f, 64f),
    VinylAlbum("Yellow Room", "June Calloway", cover("photo-1645919268978-35253dfb1d75"), 201f, 41f),
    VinylAlbum("Glass Choir", "Ilse Varga", cover("photo-1651443039959-582bbea6be6a"), 254f, 72f),
    VinylAlbum("Half Moon Club", "Corvo & Lune", cover("photo-1614283868067-b4e06385bf93"), 236f, 38f),
    VinylAlbum("Paper Suns", "The Halvorsens", cover("photo-1717598604697-bb2e9e841c1c"), 192f, 55f),
    VinylAlbum("Number Nine", "The Sundries", cover("photo-1645677042909-907678e3a40a"), 213f, 47f),
    VinylAlbum("Field Notes", "Tomas Rieu", cover("photo-1598909688344-fbe5e6d4c869"), 244f, 80f),
    VinylAlbum("Velvet Interval", "Odile Fenn", cover("photo-1611506420749-9cbb8e5a1057"), 262f, 59f),
    VinylAlbum("Hand-Me-Down Heat", "Desert Bloom Society", cover("photo-1694355450043-04721056e2f1"), 207f, 33f),
    VinylAlbum("Long Distance", "Kite Assembly", cover("photo-1588066077857-70494c21533c"), 219f, 50f),
)

private val Bg = Color(0xFF0D0C0B)
private val Ink = Color(0xFFF3EEE6)
private val Muted = Color(0xFF8F877C)
private val Track = Color(0xFF2C2925)
private val Accent = Color(0xFFE8A24A)

// The cover in front is SIZE_SHARE of the width (at most MAX_SIZE dp), centred at CENTRE_AT of
// the height. Its neighbours stand FIRST covers to each side, the rest NEXT further on, turned
// TILT° to face the middle, shrunk by SIDE_SCALE, darkened by SIDE_DIM, and seen through a lens
// PERSPECTIVE covers away. Covers further than VISIBLE from the front fade out.
private const val SIZE_SHARE = 0.56f
private const val MAX_SIZE = 250f
private const val CENTRE_AT = 0.4f
private const val FIRST = 0.62f
private const val NEXT = 0.16f
private const val TILT = 70f
private const val SIDE_SCALE = 0.2f
private const val SIDE_DIM = 0.5f
private const val DIM_PER_COVER = 0.1f
private const val PERSPECTIVE = 4.5f
private const val VISIBLE = 3.6f
// The floor reflection: a flipped copy fading into the background over REFLECTION of a cover.
private const val REFLECTION = 0.42f
// The caption sits this far under the covers, the player under it.
private const val CAPTION_GAP = 100f
// How strong the reflection is just under the cover.
private const val REFLECTION_STRENGTH = 0.42f

// Scrolling: one cover per DRAG_STEP of the cover's size under the finger; a release is thrown
// THROW seconds ahead and a spring (STIFFNESS, DAMPING; ζ ≈ 0.9) settles on the nearest cover.
private const val DRAG_STEP = 0.72f
private const val THROW = 0.16f
private const val STIFFNESS = 190f
private const val DAMPING = 25f
private const val PAN_SLOP = 8f
private const val TAP_SLOP = 10f
private const val TAP_MS = 500L

// Pulling the record out: a spring on 0…1 (OPEN_STIFFNESS, OPEN_DAMPING; ζ ≈ 0.78, so the
// record overshoots a touch as it stops). The cover lifts LIFT dp and moves left by SHIFT of
// its size; the record slides out to RECORD_OUT of the size right of the cover's centre; the
// other covers spread SPREAD of a cover outwards and fade.
private const val OPEN_STIFFNESS = 170f
private const val OPEN_DAMPING = 20f
private const val LIFT = 26f
private const val SHIFT = 0.24f
private const val RECORD_OUT = 0.56f
private const val RECORD_SIZE = 0.95f
private const val SPREAD = 0.45f
// 33⅓ rpm, reached and lost at SPIN_RATE (1/s).
private const val RPM = 100f / 3f
private const val SPIN_RATE = 2.4f
private const val LABEL = 0.36f
private const val IMAGE_FADE = 0.3f

// autoPlay (seconds): two covers on, the record out and playing, back in, a flick back home.
const val LOOP = 12f
private val Steps = listOf(0.7f to 1, 1.8f to 1, 9.0f to -2)
private const val OPEN_AT = 3.0f
private const val CLOSE_AT = 7.6f
private const val USER_HOLD = 4f

// The grooves: rings of slightly different greys, with three gaps between tracks.
private const val GROOVES = 46
private val Gaps = setOf(11, 23, 34)

private fun centred(x: Float, n: Int) = x - n * floor(x / n + 0.5f)

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Float) = floor(x + 0.5f)

/** Whether the clock, going from `before` to `now` (wrapping at LOOP), went past `at`. */
private fun passedAt(before: Float, now: Float, at: Float) = (before < at && now >= at) || (now < before && at <= now)

/** The album in front when the flow is at `x`. */
private fun indexAt(x: Float, n: Int): Int {
    if (n <= 0) return 0
    val r = jsRound(x)
    // Clamped, since every caller indexes the albums with it.
    return (r - n * floor(r / n)).toInt().coerceIn(0, n - 1)
}

private class Placement(val x: Float, val turn: Float, val scale: Float, val dim: Float, val alpha: Float)

/** Where a cover `d` places from the front sits: sideways offset (in covers), turn and shade. */
private fun placement(d: Float): Placement {
    val a = abs(d)
    val s = sign(d)
    val near = min(a, 1f)
    return Placement(
        x = s * (near * FIRST + max(a - 1f, 0f) * NEXT),
        turn = -s * near * TILT,
        scale = 1f - near * SIDE_SCALE,
        dim = near * SIDE_DIM + max(a - 1f, 0f) * DIM_PER_COVER,
        alpha = (VISIBLE + 0.6f - a).coerceIn(0f, 1f),
    )
}

/**
 * A cover's perspective, for a layer `size` wide and twice as tall (the sleeve over its
 * reflection), turned about its middle, which is the floor line: scaled, turned `turn`° about the
 * vertical, moved `x` sideways and `lift` up, then seen through a lens PERSPECTIVE covers away. All
 * lengths in pixels.
 */
private fun coverMatrix(matrix: Matrix, size: Float, s: Float, turn: Float, x: Float, lift: Float) {
    val w = size
    val h = size * 2f
    val theta = turn * PI.toFloat() / 180f
    val c = cos(theta)
    val g = sin(theta) * s / (size * PERSPECTIVE)
    val k0 = 1f - g * w / 2f
    matrix.setValues(
        floatArrayOf(
            w / 2f * g + c * s, 0f, w / 2f * k0 - c * s * w / 2f + x,
            h / 2f * g, s, h / 2f * k0 - s * h / 2f - lift,
            g, 0f, k0,
        ),
    )
}

private fun time(s: Float): String {
    val whole = max(0f, s).toInt()
    return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
}

/** Everything the frame loop, the gestures and the drawing share. Lengths in dp. */
private class VinylMotion(startAt: Float) {
    var scroll = 0f
    var velocity = 0f
    var target = 0f
    var dragging = false
    var dragFrom = 0f
    var seen = 0f
    var open = 0f
    var openV = 0f
    var openTarget = 0f
    var spin = 0f
    var spinSpeed = 0f
    var track = startAt
    var byUser = false
    var clock = 0f
    var looping = false
    var hold = 0f
    // Seconds since the block appeared; everything that moves reads it, so it is drawn every frame.
    var elapsed by mutableFloatStateOf(0f)
    // What the composition shows: the album in front, the whole seconds played, and the button.
    var front by mutableIntStateOf(0)
    var seconds by mutableIntStateOf(startAt.toInt())
    var playing by mutableStateOf(true)
    // The layout the gestures read.
    var width = 0f
    var size = 0f
    var centreY = 0f
}

/**
 * Albums in a cover flow on a dark floor: the one in front faces you, the rest stand turned
 * towards it on each side, overlapping, with their reflections fading into the floor. Swipe to
 * flip through. Tap the front cover and its record slides out to the right and spins, the cover
 * lifts and a small player fades in under it; tap again to slide the record back.
 *
 * @param autoPlay Flips through the covers, pulls a record out, plays it, puts it back and flips back.
 * @param onPlay Called with an album when its record is pulled out.
 */
@Composable
fun CoverFlowVinyl(
    modifier: Modifier = Modifier,
    albums: List<VinylAlbum> = Albums,
    autoPlay: Boolean = false,
    onPlay: ((VinylAlbum) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val playCallback by rememberUpdatedState(onPlay)
    val count = albums.size
    val motion = remember(albums) { VinylMotion(albums[0].startAt) }
    val photos = remember(albums) { mutableStateMapOf<String, Pair<ImageBitmap, Float>>() }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, autoPlay) { motion.looping = autoPlay }

    LaunchedEffect(albums, context) {
        val loader = SingletonImageLoader.get(context)
        albums.map { it.cover }.distinct().forEach { address ->
            launch {
                // A software bitmap, so the covers can also be drawn into a software canvas without throwing.
                val request = ImageRequest.Builder(context).data(address).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                photos[address] = result.image.toBitmap().asImageBitmap() to motion.elapsed
            }
        }
    }

    /** Pulls the front record out, or slides it back in. */
    fun toggle(user: Boolean) {
        val m = motion
        val out = m.openTarget < 0.5f
        m.openTarget = if (out) 1f else 0f
        if (out) {
            val index = indexAt(m.scroll, count)
            // The record left in the sleeve is wound back to where this album starts.
            if (m.open < 0.02f) {
                m.spin = 0f
                m.track = albums[index].startAt
            }
            m.playing = true
            playCallback?.invoke(albums[index])
        }
        if (user) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun advance(dt: Float) {
        val m = motion
        m.elapsed += dt
        if (m.hold > 0f) {
            m.hold -= dt
            // The script starts over from a resting state: closed at its start, open just before it closes.
            if (m.hold <= 0f) m.clock = if (m.openTarget > 0.5f) CLOSE_AT - 0.1f else 0f
        } else if (m.looping && !m.dragging) {
            val before = m.clock
            val t = (before + dt) % LOOP
            m.clock = t
            for ((at, by) in Steps) {
                if (passedAt(before, t, at) && m.openTarget < 0.5f) {
                    m.byUser = false
                    m.target = jsRound(m.target) + by
                    // A flick back carries its speed into the spring, like a finger's would.
                    if (by < -1) m.velocity = by * 3.2f
                }
            }
            if (passedAt(before, t, OPEN_AT) && m.openTarget < 0.5f) toggle(false)
            if (passedAt(before, t, CLOSE_AT) && m.openTarget > 0.5f) toggle(false)
        }

        // The flow.
        if (m.dragging) {
            m.velocity += ((m.scroll - m.seen) / dt - m.velocity) * 0.5f
        } else if (reducedMotion) {
            m.scroll = m.target
            m.velocity = 0f
        } else {
            val v = m.velocity + (STIFFNESS * (m.target - m.scroll) - DAMPING * m.velocity) * dt
            m.velocity = v
            m.scroll += v * dt
        }
        m.seen = m.scroll
        // Whole turns of the flow change nothing on screen, so the numbers are kept small.
        val turns = floor(m.target / count) * count
        if (turns != 0f && !m.dragging) {
            m.scroll -= turns
            m.seen -= turns
            m.target -= turns
        }

        // The record.
        if (reducedMotion) {
            m.open += (m.openTarget - m.open) * (1f - exp(-dt * 12f))
        } else {
            val ov = m.openV + (OPEN_STIFFNESS * (m.openTarget - m.open) - OPEN_DAMPING * m.openV) * dt
            m.openV = ov
            m.open += ov * dt
        }
        val turning = m.openTarget > 0.5f && m.playing
        val goal = if (turning && !reducedMotion) RPM / 60f * 360f else 0f
        m.spinSpeed += (goal - m.spinSpeed) * (1f - exp(-dt * SPIN_RATE))
        m.spin = (m.spin + m.spinSpeed * dt) % 360f
        val index = indexAt(m.scroll, count)
        if (turning) m.track = (m.track + dt) % albums[index].length
        if (m.track.toInt() != m.seconds) m.seconds = m.track.toInt()

        // The album in front names the record and the player; a tick when the user brought it there.
        if (index != m.front) {
            m.front = index
            if (m.byUser) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) advance(dt)
            }
        }
    }

    fun touched() {
        motion.hold = USER_HOLD
        motion.byUser = true
    }

    fun tap(x: Float, y: Float) {
        val m = motion
        touched()
        val size = m.size
        if (m.openTarget > 0.5f) {
            // The player's middle button pauses; anywhere else puts the record back.
            val controlsY = m.centreY + size / 2f + CAPTION_GAP + 121f
            if (abs(y - controlsY) < 34f && abs(x - m.width / 2f) < 34f) {
                m.playing = !m.playing
                return
            }
            toggle(true)
            return
        }
        // Before the first layout there are no covers to tap (and dx would divide by zero).
        if (size <= 0f) return
        // A tap on a side cover brings it to the front; on the front cover, pulls its record.
        val dx = (x - m.width / 2f) / size
        if (abs(dx) < 0.5f && abs(y - m.centreY) < size / 2f) {
            m.target = jsRound(m.scroll)
            toggle(true)
            return
        }
        if (abs(y - m.centreY) > size * 0.7f) return
        val a = abs(dx)
        val places = if (a < FIRST + 0.2f) 1f else 1f + jsRound((a - FIRST) / NEXT)
        m.target = jsRound(m.scroll) + sign(dx) * places
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Bg)
            .clipToBounds()
            .semantics {
                val album = albums[motion.front]
                contentDescription = "${album.title} by ${album.artist}. Swipe to flip through albums, tap the cover to play its record"
            }
            // One recogniser stands in for the exclusive pan (8 dp sideways) and tap (within 10 dp).
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m = motion
                    val start = down.position / density.density
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    try { while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val p = change.position / density.density
                        val dx = p.x - start.x
                        val dy = p.y - start.y
                        val step = m.size * DRAG_STEP
                        if (!change.pressed) {
                            if (panning) {
                                change.consume()
                                if (m.dragging && step > 0f) {
                                    m.dragging = false
                                    val thrown = -tracker.calculateVelocity().x / density.density / step
                                    m.velocity = if (thrown.isFinite()) thrown else 0f
                                    m.target = jsRound(m.scroll + m.velocity * THROW)
                                }
                            } else if (hypot(dx, dy) <= TAP_SLOP && change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                tap(p.x, p.y)
                            }
                            break
                        }
                        if (!panning && abs(dx) >= PAN_SLOP) {
                            panning = true
                            touched()
                            if (m.openTarget <= 0.5f) {
                                m.dragFrom = m.scroll
                                m.seen = m.scroll
                                m.dragging = true
                            }
                        }
                        if (panning) {
                            change.consume()
                            if (m.dragging && step > 0f) m.scroll = m.dragFrom - dx / step
                        }
                    } } finally {
                        // A drag lost to the system (or cancelled with the gesture) still settles on a cover.
                        if (m.dragging) {
                            m.dragging = false
                            m.target = jsRound(m.scroll)
                        }
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val size = min(width * SIZE_SHARE, MAX_SIZE)
        val centreY = height * CENTRE_AT
        motion.width = width
        motion.size = size
        motion.centreY = centreY
        val album = albums[motion.front]

        // A warm wall down to the floor the covers stand on; the floor is flat, so reflections melt into it.
        Box(
            Modifier
                .fillMaxWidth()
                .height((centreY + size / 2f).dp)
                .background(Brush.verticalGradient(0f to Color(0xFF1D1A16), 0.6f to Color(0xFF13110F), 1f to Bg)),
        )

        Row(
            Modifier
                .zIndex(200f)
                .offset(y = (safeTop + 14f).dp)
                .padding(horizontal = 22.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText("Crate", style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp))
            Spacer(Modifier.weight(1f))
            BasicText("$count records", style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp))
        }

        albums.forEachIndexed { index, item ->
            Sleeve(item, index, count, motion, photos[item.cover], size, width, centreY, reducedMotion)
        }

        Record(motion, photos[album.cover], size, width, centreY, reducedMotion)

        // The title and artist of each album, crossfading with the flow as its cover comes to the front.
        Box(
            Modifier
                .zIndex(200f)
                .offset(y = (centreY + size / 2f + CAPTION_GAP).dp)
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .height(54.dp)
                .graphicsLayer { translationY = (-motion.open * LIFT * 0.6f).dp.toPx().also { motion.elapsed } },
        ) {
            albums.forEachIndexed { index, item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val d = centred(index - motion.scroll, count).also { motion.elapsed }
                            alpha = (1f - abs(d) * 2.2f).coerceIn(0f, 1f)
                            translationX = (d * -24f).dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(item.title, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp), maxLines = 1)
                    BasicText(item.artist, style = TextStyle(color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }

        // The player fades in over the second half of the opening spring.
        Column(
            Modifier
                .zIndex(200f)
                .offset(y = (centreY + size / 2f + CAPTION_GAP + 66f).dp)
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val open = motion.open.also { motion.elapsed }
                    val o = ((open - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    alpha = o
                    translationY = ((1f - o) * 14f - open * LIFT * 0.6f).dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                motion.elapsed
                val fill = motion.track / albums[indexAt(motion.scroll, count)].length
                val r = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
                drawRoundRect(Track, cornerRadius = r)
                drawRoundRect(Accent, size = this.size.copy(width = this.size.width * fill.coerceIn(0f, 1f)), cornerRadius = r)
            }
            Row(Modifier.fillMaxWidth()) {
                val style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
                val seconds = motion.seconds.toFloat()
                BasicText(time(seconds), style = style)
                Spacer(Modifier.weight(1f))
                BasicText("-" + time(album.length - seconds), style = style)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.FastRewind, null, Modifier.size(22.dp), tint = Ink)
                Box(Modifier.size(58.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (motion.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(24.dp), tint = Bg)
                }
                Icon(Icons.Filled.FastForward, null, Modifier.size(22.dp), tint = Ink)
            }
        }

        BasicText(
            "Swipe to browse · Tap a cover to play",
            modifier = Modifier
                .zIndex(200f)
                .align(Alignment.BottomCenter)
                .padding(bottom = (safeBottom + 22f).dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - (motion.open * 2f).coerceIn(0f, 1f).also { motion.elapsed } },
            style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

/**
 * One sleeve standing on the floor, with its reflection under it: laid out flat (two covers tall)
 * and mapped through its perspective as a whole, in a layer stacked by its distance from the front.
 */
@Composable
private fun Sleeve(
    album: VinylAlbum,
    index: Int,
    count: Int,
    motion: VinylMotion,
    photo: Pair<ImageBitmap, Float>?,
    size: Float,
    width: Float,
    centreY: Float,
    reducedMotion: Boolean,
) {
    val matrix = remember { Matrix() }
    // Recomposed only when the cover moves into another tenth of a place from the front.
    val order by remember(count) {
        derivedStateOf {
            motion.elapsed
            100f - jsRound(abs(centred(index - motion.scroll, count)) * 10f)
        }
    }
    Box(
        Modifier
            .zIndex(order)
            .offset(x = (width / 2f - size / 2f).dp, y = (centreY - size / 2f).dp)
            .size(size.dp, (size * 2f).dp)
            .graphicsLayer {
                val d = centred(index - motion.scroll, count).also { motion.elapsed }
                val p = placement(d)
                val o = motion.open.coerceIn(0f, 1.2f)
                val front = max(0f, 1f - abs(d))
                alpha = p.alpha * (1f - (1f - front) * o.coerceIn(0f, 1f) * 0.85f)
            }
            .drawWithContent {
                // Read so the cover is drawn again every frame.
                motion.elapsed
                val d = centred(index - motion.scroll, count)
                val p = placement(d)
                val o = motion.open.coerceIn(0f, 1.2f)
                val front = max(0f, 1f - abs(d))
                // Opening moves the front cover up and left, and spreads the rest outwards.
                val x = p.x * size + sign(d) * o * SPREAD * size * (1f - front) - front * o * SHIFT * size
                val lift = if (reducedMotion) 0f else front * motion.open * LIFT
                coverMatrix(
                    matrix,
                    size.dp.toPx(),
                    p.scale,
                    p.turn,
                    (if (reducedMotion) p.x * size else x).dp.toPx(),
                    lift.dp.toPx(),
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.concat(matrix)
                    drawContent()
                    canvas.nativeCanvas.restore()
                }
            },
    ) {
        val fade = { photo?.let { ((motion.elapsed - it.second) / IMAGE_FADE).coerceIn(0f, 1f) } ?: 0f }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1C1A17)),
            ) {
                if (photo != null) {
                    Image(photo.first, null, Modifier.fillMaxSize().graphicsLayer { alpha = fade() }, contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(4.dp)))
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = placement(centred(index - motion.scroll, count)).dim.also { motion.elapsed } }
                        .background(Color.Black),
                )
            }
            // The reflection is dimmed through its own opacity, so under it is always exactly the
            // floor's colour and no box shows round it.
            Box(
                Modifier
                    .width(size.dp)
                    .height((size - 3f).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Bg),
            ) {
                if (photo != null) {
                    Image(
                        photo.first,
                        null,
                        Modifier
                            .size(size.dp)
                            .graphicsLayer {
                                scaleY = -1f
                                val dim = placement(centred(index - motion.scroll, count)).dim.also { motion.elapsed }
                                alpha = REFLECTION_STRENGTH * (1f - dim) * (1f - motion.open.coerceIn(0f, 1f) * 0.6f) * fade()
                            },
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    Modifier
                        .size(size.dp)
                        .background(Brush.verticalGradient(0f to Bg.copy(alpha = 0f), REFLECTION to Bg, 1f to Bg)),
                )
            }
        }
    }
}

/**
 * A black record behind the front sleeve; it slides out to the right and spins. Only its label
 * turns: the grooves are rings, and the light on them stays put.
 */
@Composable
private fun Record(
    motion: VinylMotion,
    photo: Pair<ImageBitmap, Float>?,
    size: Float,
    width: Float,
    centreY: Float,
    reducedMotion: Boolean,
) {
    val disc = size * RECORD_SIZE
    val r = disc / 2f
    val label = disc * LABEL
    Box(
        Modifier
            // Between the front cover (100) and its neighbours (90 and below).
            .zIndex(96.5f)
            .offset(x = (width / 2f - r).dp, y = (centreY - r).dp)
            .size(disc.dp)
            .graphicsLayer {
                val o = motion.open.also { motion.elapsed }
                val lift = if (reducedMotion) 0f else o.coerceIn(0f, 1.2f) * LIFT
                alpha = if (reducedMotion) o.coerceIn(0f, 1f) else (o * 6f).coerceIn(0f, 1f)
                translationX = ((if (reducedMotion) 1f else o) * (RECORD_OUT - SHIFT) * size).dp.toPx()
                translationY = (-lift).dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val px = density
            val centre = Offset(r * px, r * px)
            drawCircle(Color(0xFF090909), r * px, centre)
            for (k in 0 until GROOVES) {
                val radius = label / 2f + 6f + (r - label / 2f - 10f) * k / (GROOVES - 1)
                val gap = k in Gaps
                val color = if (gap) Color.Black.copy(alpha = 0.95f) else Color.White.copy(alpha = if (k % 2 == 0) 0.075f else 0.04f)
                drawCircle(color, radius * px, centre, style = Stroke((if (gap) 1.6f else 0.7f) * px))
            }
            drawCircle(Color.White.copy(alpha = 0.12f), (r - 1f) * px, centre, style = Stroke(1.4f * px))
            // The smooth run-out round the label.
            drawCircle(Color(0xFF050505), (label / 2f + 5f) * px, centre)
            // The light on the grooves stays put while the record turns under it.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.White.copy(alpha = 0f),
                    0.04f to Color.White.copy(alpha = 0f),
                    0.12f to Color.White.copy(alpha = 0.2f),
                    0.22f to Color.White.copy(alpha = 0f),
                    0.54f to Color.White.copy(alpha = 0f),
                    0.62f to Color.White.copy(alpha = 0.15f),
                    0.72f to Color.White.copy(alpha = 0f),
                    1f to Color.White.copy(alpha = 0f),
                    center = centre,
                ),
                (r - 2f) * px,
                centre,
            )
        }
        Box(
            Modifier
                .size(label.dp)
                .graphicsLayer { rotationZ = motion.spin.also { motion.elapsed } }
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .padding(2.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo.first, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.size(8.dp).background(Bg, CircleShape))
        }
    }
}
