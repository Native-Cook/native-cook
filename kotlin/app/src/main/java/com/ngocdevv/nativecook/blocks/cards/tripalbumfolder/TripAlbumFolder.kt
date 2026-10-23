package com.ngocdevv.nativecook.blocks.cards.tripalbumfolder

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param photo Address of the photo.
 * @param tint The photo's main colour: shown until the photo arrives, and as its smudge behind the frosted front.
 */
data class AlbumPhoto(val photo: String, val tint: Color)

/** The flag on the postage stamp: a red disc on white, or a red cross edged in white on blue. */
enum class AlbumFlag { Disc, Cross }

/** The second sticker: a red shrine gate, or two snowy peaks. */
enum class AlbumEmblem { Gate, Peak }

/** @param photos Four photos, left to right. */
data class Album(
    val title: String,
    val count: Int,
    val photos: List<AlbumPhoto>,
    val flag: AlbumFlag = AlbumFlag.Disc,
    val emblem: AlbumEmblem = AlbumEmblem.Gate,
)

private const val WIDTH = 340f
private const val HEIGHT = 400f

private const val FOLDER_X = 44f
private const val FOLDER_Y = 96f
private const val FOLDER_W = 252f
private const val FOLDER_H = 190f

// Y of the two embossed lines.
private val LINES = floatArrayOf(160f, 172f)

// Top of the title, measured from the top of the component.
private const val TEXT_Y = 310f
private const val TEXT_SHIFT = 6f

private const val PHOTO_W = 104f
private const val PHOTO_H = 132f
private const val PHOTO_BORDER = 6f
private const val PHOTO_RADIUS = 18f

/** Where a photo or a sticker sits: `x`, `y`, degrees. */
private class Place(val x: Float, val y: Float, val r: Float)

// Each photo at rest: centre x from the folder's centre, top from the folder's top, degrees.
private val REST = listOf(Place(-100f, 12f, -11f), Place(-52f, -38f, -3f), Place(36f, -27f, 5f), Place(102f, 4f, 9f))

// What a full press adds to that.
private val LIFT = listOf(Place(-16f, -20f, -7f), Place(-10f, -30f, -4f), Place(10f, -30f, 4f), Place(18f, -22f, 8f))

// Back to front: the outer photos lie over their neighbours.
private val DRAW_ORDER = intArrayOf(2, 1, 0, 3)

// Inside the folder the photos pull towards the middle, sink below the tab and shrink a little.
private const val DROP_PULL = 0.45f
private const val DROP_Y = 44f
private const val DROP_SCALE = 0.92f

private const val FOLDER_GROW = 0.04f
private const val PHOTO_GROW = 0.03f
private const val STICKER_GROW = 0.07f

// Opacity of a photo's smudge on the frosted front.
private const val SMUDGE = 0.16f

// The two stickers on the folder: centre and degrees, and what a full press adds.
private val STICKERS = listOf(Place(64f, 78f, -8f), Place(188f, 112f, 8f))
private val STICKER_LIFT = listOf(Place(-3f, -5f, -4f), Place(4f, -6f, 5f))

private const val PRESS_STIFFNESS = 210f
private const val PRESS_DAMPING = 17f

// The flip, in seconds from its start.
private const val DROP_TIME = 0.26f
private const val DROP_STAGGER = 0.03f
private const val RISE_AT = 0.3f
private const val RISE_STAGGER = 0.07f
private const val RISE_STIFFNESS = 180f
private const val RISE_DAMPING = 16f
private const val PEEL_TIME = 0.16f
private const val PEEL_GROW = 0.15f
private val STICK_AT = floatArrayOf(0.5f, 0.62f)
private const val STICK_FADE = 0.1f
private const val STICK_GROW = 0.4f
private const val STICK_STIFFNESS = 300f
private const val STICK_DAMPING = 18f
private const val TEXT_OUT = 0.2f
private const val TEXT_AT = 0.26f
private const val TEXT_IN = 0.3f

// A second flip is ignored until this one has settled.
private const val FLIP_TIME = 1.2f

// The flip's clock runs this long: every spring above has come to rest by then.
private const val FLIP_CLOCK = 2f

// Length of the cross-fade that replaces the flip under reduced motion.
private const val FADE_TIME = 0.25f

// A press shorter than this is a tap.
private const val TAP_WINDOW = 0.3f

// autoPlay (seconds into one album's turn): a long press, then a tap that flips to the next album.
private const val PRESS_AT = 0.8f
private const val RELEASE_AT = 2.8f
private const val TAP_AT = 3.4f
private const val TAP_HOLD = 0.12f

// One loop goes through the default two albums and is back at the first.
private const val LOOP = 10f
private const val CYCLE = LOOP / 2f

// After a touch the loop waits this long, then starts over from the first album.
private const val RESUME_AFTER = 2f

private const val HIDDEN = 0
private const val LEAVING = 1
private const val ENTERING = 2

private fun photoUrl(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=300&h=390&q=80"

private val Albums = listOf(
    Album(
        title = "Japan 2024",
        count = 83,
        photos = listOf(
            AlbumPhoto(photoUrl("1540959733332-eab4deabeeaf"), Color(0xFF4E8690)),
            AlbumPhoto(photoUrl("1493976040374-85c8e12f0c0e"), Color(0xFF5B5353)),
            AlbumPhoto(photoUrl("1441974231531-c6227db76b6e"), Color(0xFF645F3D)),
            AlbumPhoto(photoUrl("1519681393784-d120267933ba"), Color(0xFF3B4A66)),
        ),
    ),
    Album(
        title = "Iceland 2023",
        count = 124,
        photos = listOf(
            AlbumPhoto(photoUrl("1433086966358-54859d0ed716"), Color(0xFF58683F)),
            AlbumPhoto(photoUrl("1531366936337-7c912a4589a7"), Color(0xFF4E5C80)),
            AlbumPhoto(photoUrl("1504829857797-ddff29c27927"), Color(0xFF8E9DA3)),
            AlbumPhoto(photoUrl("1470071459604-3b5ec3a7fe05"), Color(0xFF6E756C)),
        ),
        flag = AlbumFlag.Cross,
        emblem = AlbumEmblem.Peak,
    ),
)

private fun outline(commands: String): Path = PathParser().parsePathString(commands).toPath()

// The front of the folder, 252 × 190, with its tab on the upper left.
private val Folder = outline(
    "M 0 16 Q 0 0 16 0 L 100 0 C 120 0 120 28 140 28 L 236 28 Q 252 28 252 44 L 252 174 Q 252 190 236 190 L 16 190 Q 0 190 0 174 Z",
)

// A 54 × 50 stamp around its centre, with perforations bitten out of every side.
private val Stamp = run {
    val paper = Path().apply { addRect(Rect(-27f, -25f, 27f, 25f)) }
    val holes = Path()
    for (n in 0 until 8) {
        val x = -27f + 54f * (n + 0.5f) / 8f
        holes.addOval(Rect(Offset(x, -25f), 2.2f))
        holes.addOval(Rect(Offset(x, 25f), 2.2f))
    }
    for (n in 0 until 7) {
        val y = -25f + 50f * (n + 0.5f) / 7f
        holes.addOval(Rect(Offset(-27f, y), 2.2f))
        holes.addOval(Rect(Offset(27f, y), 2.2f))
    }
    Path.combine(PathOperation.Difference, paper, holes)
}
private val StampField = Rect(-20f, -18f, 20f, 18f)

/** A sticker: its white backing, then its coloured parts. */
private class EmblemArt(val backing: Path, val layers: List<Pair<Path, Color>>)

/**
 * The art is given in a 60 × 56 box and moved to sit around its centre. The white backing follows the
 * silhouette 4.5 dp out, with round corners.
 */
private fun emblem(silhouette: String, vararg layers: Pair<String, Color>): EmblemArt {
    val shape = outline(silhouette).apply { translate(Offset(-30f, -28f)) }
    val rim = android.graphics.Path()
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeJoin = Paint.Join.ROUND
    }.getFillPath(shape.asAndroidPath(), rim)
    return EmblemArt(
        backing = Path.combine(PathOperation.Union, rim.asComposePath(), shape),
        layers = layers.map { (commands, color) -> outline(commands).apply { translate(Offset(-30f, -28f)) } to color },
    )
}

private val Red = Color(0xFFE8452C)
private val Ink = Color(0xFF2A2A2E)
private val Snow = Color(0xFFDBE7F4)

private val Gate = emblem(
    "M 4 9 Q 30 15 56 9 L 54 17 L 51 24 L 51 29.5 L 46 29.5 L 47.5 52 L 12.5 52 L 14 29.5 L 9 29.5 L 9 24 L 6 17 Z",
    "M 15.5 17 L 21.5 17 L 19.5 50 L 12.5 50 Z M 38.5 17 L 44.5 17 L 47.5 50 L 40.5 50 Z" to Red,
    "M 9 24 L 51 24 L 51 29.5 L 9 29.5 Z M 27.5 18 L 32.5 18 L 32.5 24 L 27.5 24 Z" to Red,
    "M 4 9 Q 30 15 56 9 L 54 17 Q 30 21 6 17 Z" to Red,
    "M 4 9 Q 30 15 56 9 L 55.3 11.8 Q 30 17.6 4.7 11.8 Z" to Ink,
    "M 11.5 46.5 L 20.5 46.5 L 20.5 52 L 11.5 52 Z M 39.5 46.5 L 48.5 46.5 L 48.5 52 L 39.5 52 Z" to Ink,
)

private val Peak = emblem(
    "M 3 50 L 24 8 L 33.5 27 L 43 18 L 57 50 Z",
    "M 26 50 L 43 18 L 57 50 Z" to Color(0xFF7FA0CF),
    "M 43 18 L 47.6 28.5 L 44.5 26.5 L 42 30 L 39.6 27 L 37.4 28.6 Z" to Snow,
    "M 3 50 L 24 8 L 45 50 Z" to Color(0xFF3F66A3),
    "M 24 8 L 31.5 23 L 27.5 20.5 L 24 25.5 L 20.5 20.5 L 16.5 23 Z" to Snow,
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val TitleStyle = TextStyle(
    color = Color(0xFF1C1C1E),
    fontSize = 24.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.3).sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CountStyle = TextStyle(
    color = Color(0xFF8A8A8F),
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/** Ease-in that first backs up a little: the photos lift before they drop. */
private fun backIn(t: Float) = t * t * (2.2f * t - 1.2f)

/** Where a spring of mass 1 released at 0 towards 1 is after [t] seconds. */
private fun settle(t: Float, stiffness: Float, damping: Float): Float {
    if (t <= 0f) return 0f
    val omega = sqrt(stiffness)
    val decay = damping / 2f
    val ring = sqrt(omega * omega - decay * decay)
    return 1f - exp(-decay * t) * (cos(ring * t) + decay / ring * sin(ring * t))
}

private fun roleOf(album: Int, from: Int, to: Int) = if (album == to) ENTERING else if (album == from) LEAVING else HIDDEN

// --- The flip, as functions of the seconds since it began. `still` is reduced motion. ---

/** 0 down in the folder … 1 standing at rest; the spring carries it a little past 1 on the way up. */
private fun rise(role: Int, slot: Int, t: Float, still: Boolean): Float = when {
    role == HIDDEN -> 0f
    still -> 1f
    role == LEAVING -> 1f - backIn(clamp01((t - slot * DROP_STAGGER) / DROP_TIME))
    else -> settle(t - RISE_AT - slot * RISE_STAGGER, RISE_STIFFNESS, RISE_DAMPING)
}

/** Under reduced motion the two albums cross-fade in place instead. */
private fun fade(role: Int, t: Float, still: Boolean): Float = when {
    role == HIDDEN -> 0f
    !still -> 1f
    role == LEAVING -> 1f - clamp01(t / FADE_TIME)
    else -> clamp01(t / FADE_TIME)
}

private fun textOpacity(role: Int, t: Float, still: Boolean): Float = when {
    still || role == HIDDEN -> fade(role, t, still)
    role == LEAVING -> 1f - clamp01(t / TEXT_OUT)
    else -> smooth(clamp01((t - TEXT_AT) / TEXT_IN))
}

private class StickerLook(val scale: Float, val opacity: Float)

/** A sticker is peeled off (grows and fades) and the next one is slapped on (lands from larger). */
private fun stickerLook(role: Int, slot: Int, t: Float, still: Boolean): StickerLook {
    if (still || role == HIDDEN) return StickerLook(1f, fade(role, t, still))
    if (role == LEAVING) {
        val u = clamp01(t / PEEL_TIME)
        return StickerLook(1f + PEEL_GROW * u, 1f - u)
    }
    val since = t - STICK_AT[slot]
    val landed = settle(since, STICK_STIFFNESS, STICK_DAMPING)
    return StickerLook(1f + STICK_GROW * (1f - landed), clamp01(since / STICK_FADE))
}

private class Pose(val x: Float, val y: Float, val rotation: Float, val scale: Float)

/**
 * Centre, rotation in degrees and scale of photo [i], between its place inside the folder ([up] 0)
 * and its place standing behind it ([up] 1), fanned further out by [press].
 */
private fun photoPose(i: Int, up: Float, press: Float): Pose {
    val rest = REST[i]
    val lift = LIFT[i]
    return Pose(
        x = FOLDER_X + FOLDER_W / 2f + mix(rest.x * DROP_PULL, rest.x + lift.x * press, up),
        y = FOLDER_Y + PHOTO_H / 2f + mix(DROP_Y, rest.y + lift.y * press, up),
        rotation = (rest.r + lift.r * press) * up,
        scale = mix(DROP_SCALE, 1f + PHOTO_GROW * press, up),
    )
}

/** What moves. Everything on screen is drawn from these four values. */
private class FolderMotion {
    /** 0 at rest … 1 pressed, on a spring. */
    val press = Animatable(0f)

    /** Seconds since the last flip began. */
    var flip by mutableFloatStateOf(FLIP_CLOCK)

    /** The album being left and the album shown. */
    var from by mutableIntStateOf(0)
    var to by mutableIntStateOf(0)
    var clock: Job? = null
}

/** Blurred shapes: mask filters only work on hardware canvases from API 28. */
private class FolderPaints(albums: List<Album>) {
    val photoShadow = SoftRoundRect(Color.Black.copy(alpha = 0.16f), sigma = 7f)
    val folderShadow = SoftRoundRect(Color.Black, sigma = 16f)
    val smudges = albums.map { album -> album.photos.map { SoftRoundRect(it.tint, sigma = 14f) } }
    private val stickerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.toArgb()
        maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
    }

    fun drawStickerShadow(canvas: android.graphics.Canvas, shape: Path, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        stickerShadow.alpha = (0.2f * alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawPath(shape.asAndroidPath(), stickerShadow)
    }
}

/**
 * A photo album shown as a folder: four bordered photos stick out behind a frosted front that carries
 * two stickers, with the album's name and photo count underneath. While it is pressed the folder grows,
 * the photos rise and fan out and the stickers lift; a quick tap drops the photos into the folder and
 * brings up the next album's.
 *
 * @param albums The albums a tap flips through, in order.
 * @param unit The word after the count in the pill.
 * @param autoPlay Presses, releases and taps by itself, flipping through the albums and back to the first.
 * @param onSelect Called with the index of the album a tap flipped to.
 */
@Composable
fun TripAlbumFolder(
    albums: List<Album> = Albums,
    unit: String = "photos",
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && albums.size > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val motion = remember { FolderMotion() }
    val paints = remember(albums) { FolderPaints(albums) }
    // A touch stops the loop; `touches` restarts the wait before it resumes.
    var touching by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }

    fun pressTo(value: Float) {
        if (still) return
        scope.launch {
            motion.press.animateTo(
                value,
                spring(dampingRatio = PRESS_DAMPING / (2f * sqrt(PRESS_STIFFNESS)), stiffness = PRESS_STIFFNESS, visibilityThreshold = 0.001f),
            )
        }
    }

    fun flipTo(album: Int) {
        motion.from = motion.to
        motion.to = album
        motion.flip = 0f
        motion.clock?.cancel()
        motion.clock = scope.launch {
            animate(0f, FLIP_CLOCK, animationSpec = tween((FLIP_CLOCK * 1000).toInt(), easing = LinearEasing)) { value, _ ->
                motion.flip = value
            }
        }
    }

    fun advance() {
        if (albums.size < 2 || motion.flip < FLIP_TIME) return
        val next = (motion.to + 1) % albums.size
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        flipTo(next)
        select?.invoke(next)
    }

    // The script a finger would follow: a long press, then a tap, once per album.
    LaunchedEffect(auto, touching, touches, albums.size) {
        if (!auto || touching) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, then go back to the first album before the script restarts.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.to != 0) {
                flipTo(0)
                delay((FLIP_TIME * 1000).toLong())
            }
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val start = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = start + ((turn * CYCLE + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            until(PRESS_AT)
            pressTo(1f)
            until(RELEASE_AT)
            pressTo(0f)
            until(TAP_AT)
            pressTo(1f)
            until(TAP_AT + TAP_HOLD)
            pressTo(0f)
            flipTo((motion.to + 1) % albums.size)
            turn += 1
        }
    }

    val shown = albums[motion.to.coerceIn(albums.indices)]

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        albums.forEachIndexed { a, album ->
            for (slot in DRAW_ORDER) {
                if (slot < album.photos.size) PhotoCard(album.photos[slot], a, slot, motion, paints, still)
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            // Drawn in dp, like the Skia canvas of the React Native version.
            scale(density, density, pivot = Offset.Zero) {
                drawFront(albums, paints, motion.press.value, motion.flip, motion.from, motion.to, still)
            }
        }

        albums.forEachIndexed { a, album ->
            Column(
                Modifier
                    .offset(0.dp, TEXT_Y.dp)
                    .width(WIDTH.dp)
                    .graphicsLayer {
                        val seen = textOpacity(roleOf(a, motion.from, motion.to), motion.flip, still)
                        alpha = seen
                        translationY = if (still) 0f else ((1f - seen) * TEXT_SHIFT).dp.toPx()
                    }
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(album.title, style = TitleStyle, maxLines = 1)
                Spacer(Modifier.height(11.dp))
                Box(
                    Modifier.height(34.dp).background(Color(0xFFE9E9EB), CircleShape).padding(horizontal = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("${album.count} $unit", style = CountStyle, maxLines = 1)
                }
            }
        }

        Box(
            Modifier
                .size(WIDTH.dp, (FOLDER_Y + FOLDER_H).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "${shown.title}, ${shown.count} $unit"
                    onClick(label = "Shows the next album") {
                        advance()
                        true
                    }
                }
                .pointerInput(still, albums.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touching = true
                        pressTo(1f)
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        val up = waitForUpOrCancellation()
                        touching = false
                        touches += 1
                        pressTo(0f)
                        // A press that ends at once, where it began, is a tap: on to the next album.
                        if (up != null && up.uptimeMillis - down.uptimeMillis < TAP_WINDOW * 1000) advance()
                    }
                },
        )
    }
}

@Composable
private fun PhotoCard(item: AlbumPhoto, album: Int, slot: Int, motion: FolderMotion, paints: FolderPaints, still: Boolean) {
    Box(
        Modifier
            .size(PHOTO_W.dp, PHOTO_H.dp)
            .graphicsLayer {
                val role = roleOf(album, motion.from, motion.to)
                val pose = photoPose(slot, rise(role, slot, motion.flip, still), motion.press.value)
                alpha = fade(role, motion.flip, still)
                translationX = (pose.x - PHOTO_W / 2f).dp.toPx()
                translationY = (pose.y - PHOTO_H / 2f).dp.toPx()
                rotationZ = pose.rotation
                scaleX = pose.scale
                scaleY = pose.scale
            }
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { paints.photoShadow.draw(it.nativeCanvas, 0f, 4f, PHOTO_W, PHOTO_H, PHOTO_RADIUS) }
                }
            }
            .background(Color.White, RoundedCornerShape(PHOTO_RADIUS.dp))
            .padding(PHOTO_BORDER.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current).data(item.photo).crossfade(200).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((PHOTO_RADIUS - PHOTO_BORDER).dp)).background(item.tint),
        )
    }
}

/** The folder's front with everything on it: shadow, panel, the photos' smudges, embossed lines, stickers. */
private fun DrawScope.drawFront(albums: List<Album>, paints: FolderPaints, press: Float, t: Float, from: Int, to: Int, still: Boolean) {
    withTransform({
        // The folder grows about its centre while it is pressed.
        translate(FOLDER_X + FOLDER_W / 2f, FOLDER_Y + FOLDER_H / 2f)
        scale(1f + FOLDER_GROW * press, 1f + FOLDER_GROW * press, Offset.Zero)
        translate(-FOLDER_W / 2f, -FOLDER_H / 2f)
    }) {
        drawIntoCanvas {
            paints.folderShadow.draw(it.nativeCanvas, 16f, 44f + 8f * press, FOLDER_W - 32f, FOLDER_H - 40f, 16f, alpha = 0.13f + 0.05f * press)
        }
        drawPath(Folder, Brush.verticalGradient(listOf(Color(0xFFF0F0F2), Color(0xFFF8F8F9)), startY = 0f, endY = FOLDER_H))

        clipPath(Folder) {
            // The front is frosted: each photo behind it shows through as a soft smudge of its colour.
            albums.forEachIndexed { a, album ->
                val role = roleOf(a, from, to)
                if (role != HIDDEN) {
                    for (slot in 0 until minOf(album.photos.size, REST.size)) {
                        val up = rise(role, slot, t, still)
                        val pose = photoPose(slot, up, press)
                        withTransform({
                            translate(pose.x - FOLDER_X, pose.y - FOLDER_Y)
                            rotate(pose.rotation, Offset.Zero)
                        }) {
                            drawIntoCanvas {
                                paints.smudges[a][slot].draw(
                                    it.nativeCanvas, -PHOTO_W / 2f, -PHOTO_H / 2f, PHOTO_W, PHOTO_H, PHOTO_RADIUS,
                                    alpha = SMUDGE * clamp01(up) * fade(role, t, still),
                                )
                            }
                        }
                    }
                }
            }
            // Two embossed lines: a dark hairline with a light one under it.
            for (y in LINES) {
                drawRect(Color.Black.copy(alpha = 0.055f), Offset(20f, y), Size(FOLDER_W - 40f, 1f))
                drawRect(Color.White.copy(alpha = 0.9f), Offset(20f, y + 1f), Size(FOLDER_W - 40f, 1f))
            }
            // Half of this stroke is clipped away, which leaves a bright rim just inside the edge.
            drawPath(Folder, Color.White.copy(alpha = 0.95f), style = Stroke(3f))
        }
        drawPath(Folder, Color.Black.copy(alpha = 0.07f), style = Stroke(0.5f))

        albums.forEachIndexed { a, album ->
            val role = roleOf(a, from, to)
            if (role != HIDDEN) {
                drawSticker(paints, 0, Stamp, stickerLook(role, 0, t, still), press) { alpha -> drawFlag(album.flag, alpha) }
                val art = if (album.emblem == AlbumEmblem.Gate) Gate else Peak
                drawSticker(paints, 1, art.backing, stickerLook(role, 1, t, still), press) { alpha ->
                    for ((path, color) in art.layers) drawPath(path, color, alpha = alpha)
                }
            }
        }
    }
}

/** Draws sticker [slot] where it sits (it lifts, turns and grows with [press]): shadow, white backing, then [art]. */
private inline fun DrawScope.drawSticker(
    paints: FolderPaints,
    slot: Int,
    shape: Path,
    look: StickerLook,
    press: Float,
    art: DrawScope.(alpha: Float) -> Unit,
) {
    val place = STICKERS[slot]
    val lift = STICKER_LIFT[slot]
    val grow = look.scale * (1f + STICKER_GROW * press)
    withTransform({
        translate(place.x + lift.x * press, place.y + lift.y * press)
        rotate(place.r + lift.r * press, Offset.Zero)
        scale(grow, grow, Offset.Zero)
    }) {
        // The shadow stays on the folder while the sticker lifts, so it slides out from under it.
        withTransform({ translate(0f, 1.5f + 5f * press) }) {
            drawIntoCanvas { paints.drawStickerShadow(it.nativeCanvas, shape, look.opacity) }
        }
        drawPath(shape, Color.White, alpha = look.opacity)
        art(look.opacity)
    }
}

private fun DrawScope.drawFlag(flag: AlbumFlag, alpha: Float) {
    clipRect(StampField.left, StampField.top, StampField.right, StampField.bottom) {
        when (flag) {
            AlbumFlag.Disc -> {
                drawRect(Color(0xFFF7F7F8), StampField.topLeft, StampField.size, alpha = alpha)
                drawCircle(Color(0xFFE5383B), 9.5f, Offset.Zero, alpha = alpha)
            }
            AlbumFlag.Cross -> {
                drawRect(Color(0xFF2B5BA8), StampField.topLeft, StampField.size, alpha = alpha)
                drawRect(Color.White, Offset(-9.5f, -18f), Size(8f, 36f), alpha = alpha)
                drawRect(Color.White, Offset(-20f, -4f), Size(40f, 8f), alpha = alpha)
                drawRect(Color(0xFFD72F34), Offset(-7.3f, -18f), Size(3.6f, 36f), alpha = alpha)
                drawRect(Color(0xFFD72F34), Offset(-20f, -1.8f), Size(40f, 3.6f), alpha = alpha)
            }
        }
    }
    drawRect(Color.Black.copy(alpha = 0.1f * alpha), StampField.topLeft, StampField.size, style = Stroke(0.5f))
}
