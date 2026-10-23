package com.ngocdevv.nativecook.blocks.media.cubestoryviewer

import android.graphics.Matrix
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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

data class CubeStory(val photo: String, val caption: String)

/** @param ago How long ago the stories were posted, e.g. "2h". */
data class CubeStoryUser(
    val name: String,
    val place: String,
    val ago: String,
    val avatar: String,
    val stories: List<CubeStory>,
)

private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1000&h=2100&q=80"
private fun avatar(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=120&h=120&q=80"

private val Users = listOf(
    CubeStoryUser(
        name = "Maren Holt",
        place = "Larch Hollow",
        ago = "2h",
        avatar = avatar("photo-1453959022778-cfda85dbe0f9"),
        stories = listOf(
            CubeStory(photo("photo-1523712999610-f77fbcfc3843"), "Morning comes in sideways through the larches"),
            CubeStory(photo("photo-1543683840-c66117bdb1f8"), "One gust, and it rained copper"),
            CubeStory(photo("photo-1573776396359-5576727fa835"), "The last leaves on the old oak"),
            CubeStory(photo("photo-1637162330294-28cc8540d41d"), "The whole path, gold"),
        ),
    ),
    CubeStoryUser(
        name = "Ines Calder",
        place = "Ocotillo Flats",
        ago = "5h",
        avatar = avatar("photo-1573689705887-bc0763c82ea2"),
        stories = listOf(
            CubeStory(photo("photo-1517025423291-770fb99ae547"), "Four hundred years of standing still"),
            CubeStory(photo("photo-1533066636271-fdbe3e84ad80"), "Pink hour at the trailhead"),
            CubeStory(photo("photo-1517408191923-f82a669f4ea1"), "Moonrise over the red ridge"),
        ),
    ),
    CubeStoryUser(
        name = "Tove Lind",
        place = "Kvasstind",
        ago = "9h",
        avatar = avatar("photo-1614358606268-aa86853578b4"),
        stories = listOf(
            CubeStory(photo("photo-1565992441121-4367c2967103"), "Air off the cornice, nobody watching"),
            CubeStory(photo("photo-1516351950028-b976f7373d70"), "North face, first tracks"),
            CubeStory(photo("photo-1609952769887-7905b6b02c0f"), "Waist deep between the pines"),
            CubeStory(photo("photo-1610131279204-6d859b4b0563"), "Our lines from today"),
        ),
    ),
)

// Each photo is up for PHOTO seconds while its bar fills; then the next one shows.
private const val PHOTO = 2f
// The cube turns a quarter turn to the next person in TURN seconds on an ease-in-out curve.
private const val TURN = 0.9f
private const val TURN_REDUCED = 0.35f
private val EaseTurn = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
// Lens distance as a share of the screen width: the faces swing through real perspective.
private const val PERSPECTIVE = 2.2f
// A face turned a full quarter away is this dark.
private const val DIM = 0.72f
// The photo on show drifts in by this much over its time (Ken Burns), and sideways by DRIFT dp.
private const val KEN_BURNS = 0.06f
private const val DRIFT = 10f

// Dragging turns the cube with the finger, one face per screen width. A release is thrown
// THROW seconds ahead at its speed, then a spring carries it to the nearest face (no further
// than one face from where the drag began). Past one face the drag only stretches, at RUBBER.
private const val DRAG_SLOP = 12f
private const val DRAG_FAIL = 28f
private const val THROW = 0.22f
private const val RUBBER = 0.22f
private const val SPRING_STIFFNESS = 210f
private const val SPRING_DAMPING = 26f
// Taps in the left TAP_BACK of the screen go back a photo, anywhere else forward.
private const val TAP_BACK = 0.3f
private const val TAP_SLOP = 12f
private const val LONG_PRESS_MS = 260L
private const val LONG_PRESS_SLOP = 14f
// The header and the footer fade out with this time constant while a story is held.
private const val FADE_TIME = 0.09f
// Photos fade in over this long once they arrive.
private const val IMAGE_FADE = 0.3f

// autoPlay. Everything else plays by itself; the script adds one press-and-hold, on the second
// person's second photo, HOLD_AT seconds in, for HOLD seconds. A touch hands the viewer to the
// user for USER_HOLD seconds. One loop: 11 photos × 2 s + 3 turns × 0.9 s + 1.3 s held = 26 s.
private const val HOLD_USER = 1
private const val HOLD_PHOTO = 1
private const val HOLD_AT = 0.8f
private const val HOLD = 1.3f
private const val USER_HOLD = 4f
const val LOOP = 26f

// What the viewer is doing between frames.
private const val PLAYING = 0
private const val TURNING = 1
private const val DRAGGING = 2
private const val SETTLING = 3

/** `x` moved by whole turns of `n` into [-n/2, n/2). */
private fun centred(x: Float, n: Int) = x - n * floor(x / n + 0.5f)

private fun mod(i: Int, n: Int) = ((i % n) + n) % n

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Float) = floor(x + 0.5f)

/** The rubber band past one face from where a drag began. */
private fun stretch(d: Float): Float {
    val a = abs(d)
    return if (a <= 1f) d else sign(d) * (1f + (a - 1f) * RUBBER)
}

/** Everything the frame loop, the gestures and the drawing share. */
private class CubeMotion(val lengths: IntArray) {
    val count = lengths.size
    // The cube's turn in faces (unbounded; face u is in front at cube = u + count·k).
    var cube by mutableFloatStateOf(0f)
    var cubeVelocity = 0f
    var mode = PLAYING
    var active = 0
    var turnFrom = 0f
    var turnTo = 0f
    var turnTime = 0f
    var dragFrom = 0f
    var target = 0f
    // Per person: the photo on show (observed, so a face recomposes when it changes) and how far
    // its bar has filled (read while drawing).
    val index = Array(count) { mutableIntStateOf(0) }
    val fill = FloatArray(count)
    // A finger is down (the bars wait), or a story is held (the bars wait and the chrome fades).
    var touching = false
    var pressed = false
    var chrome by mutableFloatStateOf(1f)
    var scriptHold = 0f
    var scriptDone = false
    var userHold = 0f
    var looping = false
    // Seconds since the block appeared; the drawing reads it, so it is drawn again every frame.
    var elapsed by mutableFloatStateOf(0f)
    var width = 0f
    // The touch under way.
    var downAt = -1L
    var panning = false
    var pressFailed = false

    fun setMark(user: Int, i: Int, f: Float) {
        index[user].intValue = i
        fill[user] = f
    }
}

/**
 * Stories of a few people on the faces of a cube. Each person's photos play full screen under a
 * row of progress bars, their avatar and name; swiping sideways turns the cube to the next person
 * with the finger, and the turning face darkens as it swings away. Tap the right side for the next
 * photo, the left for the one before, and hold to pause and see the photo alone.
 *
 * @param users The people whose stories sit on the faces of the cube, in order. Three or more.
 * @param autoPlay Plays every story, turns the cube to the next person after the last one and starts over.
 * @param onUserChange Called with a person's index each time the cube comes to rest on them.
 */
@Composable
fun CubeStoryViewer(
    modifier: Modifier = Modifier,
    users: List<CubeStoryUser> = Users,
    autoPlay: Boolean = false,
    onUserChange: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val arrived by rememberUpdatedState(onUserChange)
    val motion = remember(users) { CubeMotion(users.map { it.stories.size }.toIntArray()) }
    val photos = remember(users) { mutableStateMapOf<String, Pair<ImageBitmap, Float>>() }

    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, autoPlay) { motion.looping = autoPlay }

    // Each photo arrives on its own and fades in; the first photo of each person first.
    LaunchedEffect(users, context) {
        val loader = SingletonImageLoader.get(context)
        val first = users.mapNotNull { it.stories.firstOrNull()?.photo } + users.map { it.avatar }
        val rest = users.flatMap { user -> user.stories.drop(1).map { it.photo } }
        for (batch in listOf(first, rest)) {
            batch.map { address ->
                launch {
                    val request = ImageRequest.Builder(context).data(address).build()
                    val result = loader.execute(request) as? SuccessResult ?: return@launch
                    photos[address] = result.image.toBitmap().asImageBitmap() to motion.elapsed
                }
            }.forEach { it.join() }
        }
    }

    fun touched() {
        motion.userHold = USER_HOLD
        motion.scriptHold = 0f
    }

    /** Starts a quarter turn of the cube by `step` faces; the person it turns to starts over. */
    fun turn(step: Int) {
        val from = jsRound(motion.cube)
        motion.turnFrom = motion.cube
        motion.turnTo = from + step
        motion.turnTime = 0f
        if (motion.count <= 0 || !from.isFinite()) return
        motion.setMark(mod(from.toInt() + step, motion.count), 0, 0f)
        motion.mode = TURNING
    }

    /** The cube has come to rest on face `at`. */
    fun land(at: Float) {
        val n = motion.count.toFloat()
        if (motion.count <= 0 || !at.isFinite()) return
        // Whole turns of the cube change nothing on screen, so the number is kept small.
        val wrapped = at - n * floor(at / n)
        motion.cube -= at - wrapped
        motion.cubeVelocity = 0f
        motion.mode = PLAYING
        val face = wrapped.toInt().coerceIn(0, motion.count - 1)
        if (face != motion.active) {
            motion.active = face
            motion.scriptDone = false
            arrived?.invoke(face)
        }
    }

    fun advance(dt: Float) {
        val m = motion
        if (m.count <= 0) return
        m.elapsed += dt
        if (m.userHold > 0f) m.userHold = max(0f, m.userHold - dt)
        // The long press: 260 ms of a finger that neither moved far nor started a drag.
        if (m.downAt >= 0 && !m.panning && !m.pressed && !m.pressFailed &&
            SystemClock.uptimeMillis() - m.downAt >= LONG_PRESS_MS
        ) {
            m.pressFailed = true
            touched()
            m.pressed = true
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        when (m.mode) {
            TURNING -> {
                val duration = if (reducedMotion) TURN_REDUCED else TURN
                val t = m.turnTime + dt
                m.turnTime = t
                val e = EaseTurn.transform(min(1f, t / duration))
                m.cube = m.turnFrom + (m.turnTo - m.turnFrom) * e
                if (t >= duration) {
                    m.cube = m.turnTo
                    land(m.turnTo)
                    // The time left over from this frame already counts towards the new person's first photo.
                    m.setMark(m.active, 0, (t - duration) / PHOTO)
                }
            }
            SETTLING -> {
                // Long frames are split in two: this spring is not stable over a 64 ms step. At 60 fps
                // nothing changes.
                val steps = if (dt > 0.034f) 2 else 1
                val h = dt / steps
                var x = m.cube
                var v = m.cubeVelocity
                repeat(steps) {
                    v += (SPRING_STIFFNESS * (m.target - x) - SPRING_DAMPING * v) * h
                    x += v * h
                }
                m.cubeVelocity = v
                m.cube = x
                if (reducedMotion || (abs(m.target - m.cube) < 0.0015f && abs(v) < 0.02f)) {
                    m.cube = m.target
                    land(m.target)
                }
            }
            PLAYING -> {
                val u = m.active
                val index = m.index[u].intValue
                val fill = m.fill[u]
                // The script's one press-and-hold, when its moment comes round.
                if (m.looping && m.userHold <= 0f && !m.scriptDone && u == HOLD_USER % m.count &&
                    index == HOLD_PHOTO && fill * PHOTO >= HOLD_AT
                ) {
                    m.scriptDone = true
                    m.scriptHold = HOLD
                }
                if (m.scriptHold > 0f) {
                    m.scriptHold = max(0f, m.scriptHold - dt)
                } else if (!m.touching && !m.pressed) {
                    val next = fill + dt / PHOTO
                    if (next < 1f) {
                        m.fill[u] = next
                    } else if (index < m.lengths[u] - 1) {
                        m.setMark(u, index + 1, next - 1f)
                    } else {
                        m.setMark(u, index, 1f)
                        // After the last person the viewer stops, unless it is playing by itself.
                        if (u < m.count - 1 || m.looping) turn(1)
                    }
                }
            }
        }
        // The header and footer fade while a story is held.
        val show = if (m.pressed || m.scriptHold > 0f) 0f else 1f
        m.chrome += (show - m.chrome) * (1f - exp(-dt / FADE_TIME))
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

    fun panStart() {
        // A drag can catch the cube mid-turn; it starts from wherever the cube is.
        motion.dragFrom = jsRound(motion.cube)
        motion.target = motion.cube
        motion.mode = DRAGGING
    }

    fun panUpdate(translationX: Float) {
        if (motion.width <= 0f) return
        val d = (motion.target - motion.dragFrom) - translationX / motion.width
        motion.cube = motion.dragFrom + stretch(d)
    }

    fun panEnd(velocityX: Float) {
        val m = motion
        if (m.width <= 0f || m.count <= 0) return
        // A velocity the tracker could not measure counts as none.
        val v = if (velocityX.isFinite()) -velocityX / m.width else 0f
        val thrown = m.cube + v * THROW
        val to = max(m.dragFrom - 1f, min(m.dragFrom + 1f, jsRound(thrown)))
        val next = mod(to.toInt(), m.count)
        if (next != m.active) {
            // The person turned to picks up where they were left, or starts over once all was seen.
            val seen = m.index[next].intValue
            m.setMark(next, if (seen >= m.lengths[next] - 1 && m.fill[next] >= 1f) 0 else seen, 0f)
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        m.target = to
        m.cubeVelocity = v
        m.mode = SETTLING
    }

    fun tap(x: Float) {
        val m = motion
        if (m.mode != PLAYING || m.count <= 0) return
        touched()
        val u = m.active
        val index = m.index[u].intValue
        if (x < m.width * TAP_BACK) {
            if (index > 0) m.setMark(u, index - 1, 0f) else turn(-1)
        } else if (index < m.lengths[u] - 1) {
            m.setMark(u, index + 1, 0f)
        } else {
            m.setMark(u, index, 1f)
            turn(1)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .semantics {
                contentDescription = "Stories from ${users.joinToString(", ") { it.name }}. " +
                    "Swipe sideways for the next person, tap for the next photo, hold to pause"
            }
            // One recogniser stands in for the race of a pan (12 dp sideways before 28 dp up or
            // down), a long press (260 ms within 14 dp) and a tap (lifted within 240 ms, within 12 dp).
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    val m = motion
                    m.downAt = SystemClock.uptimeMillis()
                    m.panning = false
                    m.pressFailed = false
                    var panFailed = false
                    touched()
                    m.touching = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - start.x) / density.density
                            val dy = (change.position.y - start.y) / density.density
                            if (!change.pressed) {
                                if (m.panning) {
                                    change.consume()
                                    panEnd(tracker.calculateVelocity().x / density.density)
                                } else if (!m.pressed && hypot(dx, dy) <= TAP_SLOP &&
                                    change.uptimeMillis - down.uptimeMillis <= LONG_PRESS_MS - 20
                                ) {
                                    tap(change.position.x / density.density)
                                }
                                break
                            }
                            if (hypot(dx, dy) > LONG_PRESS_SLOP) {
                                m.pressFailed = true
                                // A hold that moves too far ends.
                                m.pressed = false
                            }
                            if (!m.panning && !panFailed && !m.pressed) {
                                if (abs(dy) >= DRAG_FAIL) {
                                    panFailed = true
                                    m.touching = false
                                } else if (abs(dx) >= DRAG_SLOP) {
                                    m.panning = true
                                    m.pressFailed = true
                                    panStart()
                                }
                            }
                            if (m.panning) {
                                change.consume()
                                panUpdate(dx)
                            }
                        }
                    } finally {
                        // A drag that never moved the cube far enough to start leaves it where it was.
                        if (m.mode == DRAGGING) {
                            m.target = jsRound(m.cube)
                            m.mode = SETTLING
                        }
                        m.downAt = -1L
                        m.panning = false
                        m.touching = false
                        m.pressed = false
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        users.forEachIndexed { u, user ->
            CubeFace(
                user = user,
                u = u,
                motion = motion,
                photos = photos,
                width = width,
                height = height,
                top = top,
                bottom = bottom,
                reducedMotion = reducedMotion,
            )
        }
    }
}

/**
 * The matrix that puts a face on the cube. `p` is its distance from the front in faces: 0 in
 * front, -1 a quarter turn to the left, +1 to the right. The face swings about the edge it shares
 * with the face in front, so the cube's corner stays at the screen while the far edges recede. The
 * lens sits over the middle of the screen, PERSPECTIVE widths away. All lengths in pixels.
 */
private fun faceMatrix(matrix: Matrix, p: Float, w: Float, h: Float) {
    val half = w / 2f
    val pivot = if (p > 0f) -half else half
    val theta = p * PI.toFloat() / 2f
    // No size yet (first frame): the face stays flat rather than taking a NaN matrix.
    if (w <= 0f || h <= 0f || !p.isFinite()) {
        matrix.reset()
        return
    }
    val s = sin(theta) / (w * PERSPECTIVE)
    val c = cos(theta)
    val k = -half - pivot
    val cx = w / 2f
    val cy = h / 2f
    // A point (x, y) of the face: turned about the pivot, moved by p widths, divided by the lens.
    matrix.setValues(
        floatArrayOf(
            cx * s + c, 0f, cx * (1f + s * k) + c * k + p * w + pivot,
            cy * s, 1f, cy * (1f + s * k) - h / 2f,
            s, 0f, 1f + s * k,
        ),
    )
}

/** One person's stories: a face of the cube, laid out flat at the screen's size and turned as a whole. */
@Composable
private fun CubeFace(
    user: CubeStoryUser,
    u: Int,
    motion: CubeMotion,
    photos: Map<String, Pair<ImageBitmap, Float>>,
    width: Float,
    height: Float,
    top: Float,
    bottom: Float,
    reducedMotion: Boolean,
) {
    // An empty person (never in the samples) shows only the dark ground.
    if (user.stories.isEmpty()) return
    val index = motion.index[u].intValue.coerceIn(0, user.stories.size - 1)
    val matrix = remember { Matrix() }
    val first = user.name.substringBefore(' ')
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = centred(u - motion.cube, motion.count)
                alpha = if (abs(p) >= 1f) 0f else if (reducedMotion) 1f - abs(p) else 1f
            }
            .drawWithContent {
                val p = centred(u - motion.cube, motion.count)
                if (abs(p) >= 1f) return@drawWithContent
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    if (!reducedMotion) {
                        faceMatrix(matrix, p, size.width, size.height)
                        canvas.nativeCanvas.concat(matrix)
                    }
                    drawContent()
                    // The face darkens as it turns away.
                    val shade = min(1f, abs(p)) * DIM
                    if (shade > 0.002f) drawRect(Color.Black.copy(alpha = shade))
                    canvas.nativeCanvas.restore()
                }
            }
            .clipToBounds()
            .background(Color(0xFF0D0D0D)),
    ) {
        // The photo on show drifts in while its bar fills; alternate photos drift the other way.
        val shown = photos[user.stories[index].photo]
        if (shown != null) {
            Image(
                bitmap = shown.first,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = if (reducedMotion) 0f else motion.fill[u].also { motion.elapsed }
                        val side = if (index % 2 == 0) 1f else -1f
                        val s = 1f + KEN_BURNS * t + DRIFT / 400f
                        scaleX = s
                        scaleY = s
                        translationX = (side * DRIFT * (t - 0.5f)).dp.toPx()
                        alpha = ((motion.elapsed - shown.second) / IMAGE_FADE).coerceIn(0f, 1f)
                    },
            )
        }

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.chrome }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((top + 150f).dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.55f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0f),
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height((bottom + 300f).dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0f),
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )

            // One segment per photo: full for photos seen, filling for the one on show.
            val count = user.stories.size
            Canvas(
                Modifier
                    .offset(y = (top + 8f).dp)
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .height(2.5.dp),
            ) {
                motion.elapsed
                val gap = 4.dp.toPx()
                val segment = (size.width - gap * (count - 1)) / count
                val radius = CornerRadius(2.dp.toPx())
                val fill = motion.fill[u]
                for (k in 0 until count) {
                    val x = k * (segment + gap)
                    drawRoundRect(Color.White.copy(alpha = 0.32f), Offset(x, 0f), Size(segment, size.height), radius)
                    val f = if (k < index) 1f else if (k == index) min(1f, fill) else 0f
                    if (f > 0f) drawRoundRect(Color.White, Offset(x, 0f), Size(segment * f, size.height), radius)
                }
            }

            Row(
                Modifier
                    .offset(y = (top + 24f).dp)
                    .padding(start = 12.dp, end = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(38.dp).border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val face = photos[user.avatar]
                    Box(Modifier.size(31.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                        if (face != null) {
                            Image(face.first, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        buildAnnotatedString {
                            append(user.name)
                            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)) {
                                append("  ${user.ago}")
                            }
                        },
                        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.8f))
                        BasicText(
                            user.place,
                            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Icon(Icons.Filled.MoreHoriz, null, Modifier.size(20.dp), tint = Color.White)
                    Icon(Icons.Filled.Close, null, Modifier.size(19.dp), tint = Color.White)
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = (bottom + 10f).dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                // The line under a photo, with its number; it changes with the photo.
                Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.BottomStart) {
                    Column(
                        Modifier.padding(start = 4.dp, end = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicText(
                            "%02d / %02d".format(index + 1, count),
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        BasicText(
                            user.stories[index].caption,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 27.sp,
                                lineHeight = 31.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.6).sp,
                            ),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(23.dp))
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            "Reply to $first…",
                            style = TextStyle(color = Color.White.copy(alpha = 0.88f), fontSize = 15.sp),
                            maxLines = 1,
                        )
                    }
                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(25.dp), tint = Color.White)
                    Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(23.dp), tint = Color.White)
                }
            }
        }
    }
}
