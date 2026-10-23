package com.ngocdevv.nativecook.blocks.gestures.stickerboard

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min
import kotlinx.coroutines.launch

private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=400&q=80"

private val Stickers = listOf(
    StickerDef(StickerKind.Circle, 56f, 56f, 0.27f, 0.17f, -6f, photo = "photo-1559181567-c3190ca9959b"),
    StickerDef(StickerKind.Rounded, 108f, 132f, 0.72f, 0.19f, 7f, photo = "photo-1518020382113-a7e8fc38eac9"),
    StickerDef(StickerKind.Star, 48f, 48f, 0.17f, 0.36f, -14f, color = 0xFFC531),
    StickerDef(StickerKind.Circle, 46f, 46f, 0.52f, 0.37f, 4f, photo = "photo-1629740067905-bd3f515aa739"),
    StickerDef(StickerKind.Bubble, 124f, 60f, 0.8f, 0.41f, 8f, color = 0xFF6F91, text = "good boy!"),
    StickerDef(StickerKind.Rounded, 128f, 96f, 0.33f, 0.56f, -5f, photo = "photo-1609639643505-3c158a56de42"),
    StickerDef(StickerKind.Smiley, 40f, 40f, 0.75f, 0.58f, 10f, color = 0xFFD23F),
    StickerDef(StickerKind.Badge, 42f, 42f, 0.18f, 0.75f, -9f, color = 0x2F9E64, text = "FRESH"),
    StickerDef(StickerKind.Circle, 50f, 50f, 0.5f, 0.74f, 3f, photo = "photo-1558788353-f76d92427f16"),
    StickerDef(StickerKind.Heart, 44f, 40f, 0.81f, 0.75f, -8f, color = 0xFF4B5C),
)

/**
 * A cork board covered in die-cut stickers: photo stickers in a thick white border and drawn ones,
 * a star, a smiley, a speech bubble, a seal and a heart. A sticker can be dragged, turned with two
 * fingers and pinched, all at once; it lifts when picked up and slaps down with a squash when let
 * go. A long press lifts a corner that follows the finger, curling back over the sticker, until the
 * sticker comes off. A steel pedal bin rises from the bottom edge: it leans towards a loose sticker,
 * opens its lid as it nears and pulls it in; let go there, the sticker crumples into a paper ball and
 * drops in, the lid slams and the counter on the bin ticks up. A little later a new one is slapped
 * back in its place.
 *
 * @param autoPlay Plays a 15 s loop: two stickers moved, turned and dropped, one peeled, crumpled and binned.
 * @param title The words on the tape at the top of the board.
 */
@Composable
fun StickerBoard(
    modifier: Modifier = Modifier,
    stickers: List<StickerDef> = Stickers,
    autoPlay: Boolean = false,
    title: String = "WEEKEND · Nº 14",
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val shapes = remember(stickers) { stickers.mapIndexed(::shapeOf) }
    val engine = remember(stickers) {
        StickerBoardEngine(stickers, shapes) { kind ->
            val constant = when (kind) {
                Thud.Slam -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                Thud.Snap -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
                Thud.Drop -> HapticFeedbackConstants.CONTEXT_CLICK
                Thud.Rip -> HapticFeedbackConstants.VIRTUAL_KEY
                Thud.Lift -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(constant)
        }
    }
    val paints = remember { StickerPaints() }
    // Read by the picture, so it is drawn again every frame.
    var tick by remember { mutableFloatStateOf(0f) }
    var laidOut by remember { mutableStateOf(false) }

    // Read during composition, so the board is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    SideEffect {
        engine.auto = auto
        engine.reduced = reducedMotion
    }

    LaunchedEffect(engine, auto) { engine.restartLoop() }

    LaunchedEffect(engine, context) {
        val loader = SingletonImageLoader.get(context)
        stickers.forEachIndexed { i, s ->
            val id = s.photo ?: return@forEachIndexed
            launch {
                val request = ImageRequest.Builder(context)
                    .data(photo(id))
                    .size(Size.ORIGINAL)
                    // A software bitmap can carry mipmaps, which keep scaled-down stickers from shimmering.
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                val bitmap = result.image.toBitmap()
                bitmap.setHasMipMap(true)
                engine.loaded[i] = LoadedPhoto(bitmap, engine.elapsed)
            }
        }
    }

    LaunchedEffect(engine, laidOut) {
        if (!laidOut) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) engine.advance(dt)
                tick = engine.elapsed
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "A cork board with ${stickers.size} stickers. Drag, pinch and turn a sticker with two fingers; press and hold to peel one off, and let go of it over the bin to throw it away."
            }
            .pointerInput(engine) {
                val d = density.density
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    engine.touchesBegan(listOf(first.id.value), listOf(floatArrayOf(first.position.x / d, first.position.y / d)))
                    do {
                        val event = awaitPointerEvent()
                        val began = ArrayList<Long>()
                        val beganAt = ArrayList<FloatArray>()
                        val moved = ArrayList<Long>()
                        val movedAt = ArrayList<FloatArray>()
                        val ended = ArrayList<Long>()
                        val endedAt = ArrayList<FloatArray>()
                        for (change in event.changes) {
                            val at = floatArrayOf(change.position.x / d, change.position.y / d)
                            when {
                                change.pressed && !change.previousPressed -> {
                                    began.add(change.id.value)
                                    beganAt.add(at)
                                }
                                !change.pressed && change.previousPressed -> {
                                    ended.add(change.id.value)
                                    endedAt.add(at)
                                }
                                change.pressed && change.position != change.previousPosition -> {
                                    moved.add(change.id.value)
                                    movedAt.add(at)
                                }
                            }
                            change.consume()
                        }
                        if (began.isNotEmpty()) engine.touchesBegan(began, beganAt)
                        if (moved.isNotEmpty()) engine.touchesMoved(moved, movedAt)
                        if (ended.isNotEmpty()) engine.touchesEnded(ended, endedAt)
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect {
            engine.layout(width, height, safeTop, safeBottom)
            laidOut = true
        }
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas {
                    drawBoard(it.nativeCanvas, engine, stickers, shapes, paints, title, reducedMotion, density.density)
                }
            }
        }
    }
}
