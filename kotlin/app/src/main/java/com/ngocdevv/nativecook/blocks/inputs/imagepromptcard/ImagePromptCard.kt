package com.ngocdevv.nativecook.blocks.inputs.imagepromptcard

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * A picture attached to the prompt.
 *
 * @param photo Address of the photo; it is shown square.
 * @param tint Flat colour shown until the photo arrives.
 */
data class ImagePromptCardImage(val title: String, val photo: String, val tint: Color)

private const val WIDTH = 350f

/** The box the component is laid out in: the pickers, and the card with its row of images open. */
private const val HEIGHT = 208f
private const val HEADER_HEIGHT = 30f
private const val CARD_Y = 42f

/** Height of the card without images; the row adds `ROW_HEIGHT`. */
private const val REST_HEIGHT = 110f
private const val ROW_HEIGHT = 56f
private const val RADIUS = 24f

private const val THUMB = 44f

/** Centre of the first thumbnail, and the distance to the next. */
private const val THUMB_X = 38f
private const val THUMB_Y = 38f
private const val THUMB_STEP = 54f

/** The most thumbnails the row holds. */
private const val MOST = 5
private const val TEXT_X = 18f
private const val TEXT_Y = 15f

/** Centre of the bottom row from the card's bottom edge. */
private const val TOOL_Y = 30f
private const val PLUS_X = 30f
private const val SEND_X = 319f
private const val SEND_SIZE = 38f
private const val PEEK = 136f

/** Room between the preview and the card's top edge. */
private const val PEEK_GAP = 8f

// A layer that fades cuts off what is drawn outside it, so a thumbnail's layer and the preview's are
// this much larger on every side: room for the shadow, and for the badge on a thumbnail's corner.
private const val THUMB_BLEED = 28f
private const val PEEK_BLEED = 56f

private const val FONT = 16f
private val Ink = Color(0xFF111111)

/** The bare icons are a shade lighter than the text. */
private val IconInk = Color(0xFF2B2B33)

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
private val ADD_AT = floatArrayOf(0.8f, 1.25f, 1.7f)
private const val LIFT_AT = 2.6f
private const val DROP_AT = 3.5f
private const val TYPE_AT = 4f

/** The caret shows in the field this long before the first key. */
private const val CARET_LEAD = 0.35f
private const val KEY = 0.048f
private val RHYTHM = floatArrayOf(1f, 0.72f, 1.3f, 0.85f, 1.12f, 0.64f, 1.42f, 0.95f)

/** From the last key to the tap on the arrow. */
private const val SEND_AFTER = 0.5f
private const val LOOP = 9.4f

/** After the last touch, the card empties itself and the script waits this long. */
private const val RESUME_AFTER = 4f

/** Time the row takes to close. */
private const val ROW_SETTLE = 0.5f

private const val BLINK = 0.53f
private const val PRESS = 0.12f
private const val POP = 0.16f
private const val WIPE = 0.18f
private const val HOLD_TIME = 0.3f

/** A finger may move this far and still be holding, as far as SwiftUI's long press allows by default. */
private const val HOLD_REACH = 10f
private const val PRESSED = 0.94f

// A thumbnail's flight in: from 0.3 scale and a tilt, bulging to the right of a straight path.
private const val FLY_SCALE = 0.3f
private const val FLY_TILT = -20f
private const val FLY_ARC = 26f
private const val LIFT_SCALE = 1.1f

// A send: the thumbnails close up into a fanned pile, then dive into the button one after another.
private const val PILE_STEP = 7f
private const val PILE_TILT = 7f
private const val STACK_TIME = 0.26f
private const val SINK_TIME = 0.42f
private const val SINK_STAGGER = 0.05f
private const val SINK_SCALE = 0.15f
private const val SINK_ARC = 22f

/** From the first thumbnail diving to the arrow turning into a ring. */
private const val RING_AFTER = 0.3f
private const val SPIN_TIME = 0.7f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val Land = physical(stiffness = 210f, damping = 19f)
private val Slide = physical(stiffness = 320f, damping = 26f)
private val RowMove = physical(stiffness = 260f, damping = 24f)
private val Lift = physical(stiffness = 300f, damping = 20f)
private val Turn = physical(stiffness = 260f, damping = 18f)
private val Settle = physical(stiffness = 420f, damping = 14f)
private val Sink = CubicBezierEasing(0.5f, 0f, 0.85f, 0.6f)
private val Pop = tween<Float>(ms(POP), easing = EaseOut)

/**
 * A spring of mass 1 given by its stiffness and damping, the numbers every stack of this block shares.
 * Most of them drive a share between 0 and 1 that is stretched over a long way, so they settle much
 * closer to their target than Compose's default before they are snapped onto it.
 */
private fun physical(stiffness: Float, damping: Float) = spring(
    dampingRatio = damping / (2f * sqrt(stiffness)),
    stiffness = stiffness,
    visibilityThreshold = 0.0005f,
)

private fun ms(seconds: Float) = (seconds * 1000f).roundToInt()

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

// Icons, as outlines in a 20 dp box (the model's mark in 16 dp, the badge's cross in 10 dp).
private val MarkIcon = outline("M8 2.5 L8 13.5 M3.24 5.25 L12.76 10.75 M3.24 10.75 L12.76 5.25")
private val XIcon = outline("M3 3 L7 7 M7 3 L3 7")
private val PlusIcon = outline("M10 4.5 L10 15.5 M4.5 10 L15.5 10")
private val ArrowIcon = outline("M10 15.5 L10 4.5 M5.5 9 L10 4.5 L14.5 9")
private val BulbIcon = outline(
    "M7 12.3 C5.28 11.01 4.58 8.76 5.26 6.72 C5.94 4.68 7.85 3.3 10 3.3 C12.15 3.3 14.06 4.68 14.74 6.72 " +
        "C15.42 8.76 14.72 11.01 13 12.3 L13 14.5 L7 14.5 Z M8.25 17 L11.75 17",
)
private val ScopeIcon = outline(
    "M3.6 10.2 L11.6 6 L13.1 8.9 L5.1 13.1 Z M11.6 6 L15 4.2 L16.5 7.1 L13.1 8.9 M8.4 11.6 L6 17 M8.4 11.6 L11 17",
)
private val GlobeIcon = outline(
    "M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z " +
        "M3.5 10 L16.5 10 M10 3.5 C7.4 5.5 7.4 14.5 10 16.5 M10 3.5 C12.6 5.5 12.6 14.5 10 16.5",
)
private val TargetIcon = outline(
    "M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z " +
        "M12.5 10 C12.5 11.38 11.38 12.5 10 12.5 C8.62 12.5 7.5 11.38 7.5 10 C7.5 8.62 8.62 7.5 10 7.5 C11.38 7.5 12.5 8.62 12.5 10 Z",
)
private val MicIcon = outline(
    "M10 2.5 C11.38 2.5 12.5 3.62 12.5 5 L12.5 9.5 C12.5 10.88 11.38 12 10 12 C8.62 12 7.5 10.88 7.5 9.5 L7.5 5 " +
        "C7.5 3.62 8.62 2.5 10 2.5 Z M5 9.5 C5 12.26 7.24 14.5 10 14.5 C12.76 14.5 15 12.26 15 9.5 M10 14.5 L10 17.5",
)
private val WandIcon = outline(
    "M3.5 16.5 L13.5 6.5 M15.5 2.5 L15.5 5.5 M14 4 L17 4 M16.75 10 L16.75 12.5 M15.5 11.25 L18 11.25 M8 2.75 L8 5.25 M6.75 4 L9.25 4",
)
private val SlidersIcon = outline(
    "M3.5 6.5 L10.5 6.5 M14.5 6.5 L16.5 6.5 M3.5 13.5 L5.5 13.5 M9.5 13.5 L16.5 13.5 " +
        "M14.5 6.5 C14.5 7.6 13.6 8.5 12.5 8.5 C11.4 8.5 10.5 7.6 10.5 6.5 C10.5 5.4 11.4 4.5 12.5 4.5 C13.6 4.5 14.5 5.4 14.5 6.5 Z " +
        "M9.5 13.5 C9.5 14.6 8.6 15.5 7.5 15.5 C6.4 15.5 5.5 14.6 5.5 13.5 C5.5 12.4 6.4 11.5 7.5 11.5 C8.6 11.5 9.5 12.4 9.5 13.5 Z",
)
private val ClockIcon = outline(
    "M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z " +
        "M10 6.5 L10 10 L12.5 11.5",
)

private fun outline(data: String) = PathParser().parsePathString(data).toPath()

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun cardText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldStyle = cardText(FONT, 24f, Ink)
private val PlaceholderStyle = FieldStyle.copy(color = Color(0xFFA2A2AD))
private val GhostStyle = FieldStyle.copy(color = Color.Transparent)
private val ChipStyle = cardText(13f, 18f, Ink, FontWeight.Medium)

private data class ImagePromptCardThumb(
    val id: Int,
    val image: ImagePromptCardImage,
    /** The slot it flew to, and where the plus was when it took off. */
    val home: Int,
    val from: Offset,
    /** Popped out by its badge, about to be dropped from the row. */
    val leaving: Boolean = false,
)

/** Draws in dp, the unit every number of this block is given in. */
private inline fun DrawScope.inDp(block: DrawScope.() -> Unit) = scale(density, density, pivot = Offset.Zero, block = block)

/** A square of [side] dp with its centre at ([x], [y]). */
private fun Modifier.centredAt(x: Float, y: Float, side: Float) =
    offset((x - side / 2f).dp, (y - side / 2f).dp).size(side.dp)

/**
 * A prompt card that takes reference images. Each one flies in from the plus along a short arc and
 * lands in a row that opens above the text; a badge pops it out again and a long press holds it up
 * larger above the card. On send the thumbnails pile up and sink into the send button, which turns
 * a ring once.
 *
 * @param value Controlled text. Leave it null and the component keeps its own.
 * @param autoPlay Adds three images, holds one up, types the script, sends and starts over.
 * @param script The prompt autoPlay types.
 * @param images The images the plus attaches, in turn.
 * @param accent Colour of the enhance wand.
 * @param onSend Called with the prompt and its images when the arrow or the return key is pressed.
 * @param onTool Called with "settings", "history", "ideas", "explore", "web", "focus" or "mic" for the bare icons.
 */
@Composable
fun ImagePromptCard(
    value: String? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Describe what to make",
    script: String = "Make a moodboard from these photos",
    model: String = "Nova 2.1",
    agent: String = "Creative Agent",
    images: List<ImagePromptCardImage> = listOf(
        ImagePromptCardImage(
            title = "Temple on a lake, Bali",
            photo = "https://images.unsplash.com/photo-1537996194471-e657df975ab4?auto=format&fit=crop&w=400&h=400&q=80",
            tint = Color(0xFF999787),
        ),
        ImagePromptCardImage(
            title = "White houses, Santorini",
            photo = "https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?auto=format&fit=crop&w=400&h=400&q=80",
            tint = Color(0xFF8F9AA2),
        ),
        ImagePromptCardImage(
            title = "Balloons over Cappadocia",
            photo = "https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=400&h=400&q=80",
            tint = Color(0xFF7C8986),
        ),
    ),
    accent: Color = Color(0xFF6B4EE6),
    onValueChange: ((String) -> Unit)? = null,
    onSend: ((String, List<ImagePromptCardImage>) -> Unit)? = null,
    onAdd: ((ImagePromptCardImage) -> Unit)? = null,
    onRemove: ((ImagePromptCardImage) -> Unit)? = null,
    onEnhance: (() -> Unit)? = null,
    onModel: (() -> Unit)? = null,
    onAgent: (() -> Unit)? = null,
    onTool: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val controlled = value != null

    // The script and what follows a send outlive a recomposition, so they read these.
    val latestValue by rememberUpdatedState(value)
    val latestScript by rememberUpdatedState(script)
    val latestImages by rememberUpdatedState(images)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnSend by rememberUpdatedState(onSend)
    val latestOnAdd by rememberUpdatedState(onAdd)
    val latestOnRemove by rememberUpdatedState(onRemove)

    var inner by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(autoPlay && !controlled) }
    var caretOn by remember { mutableStateOf(true) }
    // autoPlay has put its caret in the field.
    var caretShown by remember { mutableStateOf(false) }
    // The arrow is held down by the script.
    var pressed by remember { mutableStateOf(false) }
    var thumbs by remember { mutableStateOf(listOf<ImagePromptCardThumb>()) }
    var nextThumb by remember { mutableIntStateOf(0) }
    // Counts the images attached so far: it picks the next one and turns the plus.
    var added by remember { mutableIntStateOf(0) }
    // The thumbnail held up by a long press, and the image the preview shows while it fades.
    var lifted by remember { mutableStateOf<Int?>(null) }
    var peek by remember { mutableStateOf<ImagePromptCardImage?>(null) }
    var peekX by remember { mutableFloatStateOf(0f) }
    // A send is under way: the thumbnails pile up, then sink into the button.
    var busy by remember { mutableStateOf(false) }
    var stacked by remember { mutableStateOf(false) }
    var departed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var spun by remember { mutableStateOf(false) }
    var gulp by remember { mutableStateOf(false) }
    // The prompt is faded out, just before the field empties.
    var wiped by remember { mutableStateOf(false) }
    // Counts sends and resets, to start and cancel what follows a send.
    var run by remember { mutableIntStateOf(0) }
    // Counts touches, to restart the wait before autoPlay resumes.
    var touches by remember { mutableIntStateOf(0) }

    val text = value ?: inner
    val rowOpen = thumbs.any { !it.leaving }

    fun current() = latestValue ?: inner

    fun live() = thumbs.filter { !it.leaving }

    /** Any touch outside the field stops the script where it is. */
    fun touch() {
        playing = false
        caretShown = false
        pressed = false
        lifted = null
        touches += 1
    }

    fun edit(new: String) {
        if (new == current()) return
        if (latestValue == null) inner = new
        latestOnValueChange?.invoke(new)
    }

    /** Attaches the next image: it starts at the plus and flies to the end of the row. */
    fun add(byHand: Boolean) {
        val pool = latestImages
        val row = live()
        if (busy || pool.isEmpty() || row.size >= MOST) return
        val image = pool[added % pool.size]
        val from = Offset(PLUS_X, REST_HEIGHT + (if (row.isEmpty()) 0f else ROW_HEIGHT) - TOOL_Y)
        thumbs = thumbs + ImagePromptCardThumb(id = nextThumb, image = image, home = row.size, from = from)
        nextThumb += 1
        added += 1
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        latestOnAdd?.invoke(image)
    }

    /** Pops a thumbnail out; the ones after it slide over, and the row closes after the last. */
    fun remove(id: Int, byHand: Boolean) {
        if (busy) return
        val thumb = thumbs.firstOrNull { it.id == id && !it.leaving } ?: return
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        latestOnRemove?.invoke(thumb.image)
        // It keeps its place while it pops; the thumbnail drops itself from the row when that is over.
        thumbs = thumbs.map { if (it.id == id) it.copy(leaving = true) else it }
    }

    fun lift(id: Int, byHand: Boolean) {
        if (busy) return
        val index = thumbs.indexOfFirst { it.id == id && !it.leaving }
        if (index < 0) return
        peek = thumbs[index].image
        peekX = THUMB_X + thumbs.take(index).count { !it.leaving } * THUMB_STEP
        lifted = id
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun send(byHand: Boolean) {
        val prompt = current().trim()
        val row = live()
        if (focused) focusManager.clearFocus()
        if (busy || (prompt.isEmpty() && row.isEmpty())) return
        busy = true
        pressed = false
        caretShown = false
        lifted = null
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        latestOnSend?.invoke(prompt, row.map { it.image })
        run += 1
    }

    /** Back to the empty card at once, whatever was going on. */
    fun reset() {
        busy = false
        pressed = false
        caretShown = false
        lifted = null
        thumbs = emptyList()
        stacked = false
        departed = false
        sending = false
        spun = false
        gulp = false
        wiped = false
        run += 1
        edit("")
    }

    fun tool(name: String) {
        touch()
        onTool?.invoke(name)
    }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        // Every step is timed from the start of its loop, so the period does not drift.
        var origin = System.nanoTime()

        /** Waits for [at] seconds into the loop; false once a touch has taken over from the script. */
        suspend fun until(at: Float): Boolean {
            val wait = origin + (at * 1e9).toLong() - System.nanoTime()
            if (wait > 0) delay(wait / 1_000_000)
            return playing
        }

        while (true) {
            for (at in ADD_AT) {
                if (!until(at)) return@LaunchedEffect
                add(byHand = false)
            }
            if (!until(LIFT_AT)) return@LaunchedEffect
            live().getOrNull(1)?.let { lift(it.id, byHand = false) }
            if (!until(DROP_AT)) return@LaunchedEffect
            lifted = null
            var at = TYPE_AT
            if (!until(at - CARET_LEAD)) return@LaunchedEffect
            caretShown = true
            var typed = ""
            for (character in latestScript) {
                if (!until(at)) return@LaunchedEffect
                typed += character
                edit(typed)
                at += KEY * RHYTHM[(typed.length - 1) % RHYTHM.size]
            }
            at += SEND_AFTER
            if (!until(at - PRESS)) return@LaunchedEffect
            pressed = true
            if (!until(at)) return@LaunchedEffect
            send(byHand = false)
            // The ring has turned and the row has closed before the loop is over.
            origin += (max(LOOP, at + STACK_TIME + RING_AFTER + SPIN_TIME + ROW_SETTLE + ADD_AT[0]) * 1e9).toLong()
            if (!until(0f)) return@LaunchedEffect
        }
    }

    // What a send sets off: the pile, its dive into the button, the ring, and the empty card.
    LaunchedEffect(run) {
        if (!busy) return@LaunchedEffect
        val mine = run
        val origin = System.nanoTime()

        /** Waits until [at] seconds after the send; false once a reset has called it off. */
        suspend fun until(at: Float): Boolean {
            val wait = origin + (at * 1e9).toLong() - System.nanoTime()
            if (wait > 0) delay(wait / 1_000_000)
            return run == mine
        }

        val had = thumbs.isNotEmpty() && !still
        var at = 0f
        wiped = true
        if (had) {
            stacked = true
            at += STACK_TIME
            if (!until(at)) return@LaunchedEffect
        }
        departed = true
        at += if (had) RING_AFTER else WIPE
        if (!until(at)) return@LaunchedEffect
        edit("")
        wiped = false
        sending = true
        spun = true
        val dive = SINK_TIME + max(0, thumbs.size - 1) * SINK_STAGGER
        val swallowAfter = if (had) dive - RING_AFTER else POP
        at += swallowAfter
        if (!until(at)) return@LaunchedEffect
        // The pile is inside the button: it swallows, and the row closes.
        gulp = had
        thumbs = emptyList()
        stacked = false
        departed = false
        at += PRESS
        if (!until(at)) return@LaunchedEffect
        gulp = false
        at += SPIN_TIME - swallowAfter - PRESS
        if (!until(at)) return@LaunchedEffect
        sending = false
        spun = false
        busy = false
    }

    LaunchedEffect(touches, focused) {
        // Left alone with an empty field, the card empties itself and the script resumes.
        if (!autoPlay || controlled || playing || focused) return@LaunchedEffect
        val mine = touches
        delay(ms(RESUME_AFTER).toLong())
        if (touches != mine || focused || current().isNotEmpty() || busy) return@LaunchedEffect
        for (thumb in live()) remove(thumb.id, byHand = false)
        delay(ms(ROW_SETTLE).toLong())
        if (touches == mine && !focused) playing = true
    }

    LaunchedEffect(text, playing, caretShown) {
        // The caret stays solid while keys arrive and blinks once they stop.
        caretOn = true
        if (!playing || !caretShown) return@LaunchedEffect
        while (true) {
            delay(ms(BLINK).toLong())
            caretOn = !caretOn
        }
    }

    // One number moves the text and the bottom row down and grows the card.
    val shift = animateFloatAsState(if (rowOpen) ROW_HEIGHT else 0f, if (still) snap() else RowMove, label = "row")
    val turn = animateFloatAsState(added * 90f, if (still) snap() else Turn, label = "plus")
    val blink = animateFloatAsState(if (caretOn) 1f else 0f, tween(80, easing = EaseOut), label = "caret")
    // The prompt fades out before the field empties and is back at once.
    val wipe = animateFloatAsState(
        targetValue = if (wiped) 0f else 1f,
        animationSpec = if (wiped) tween(ms(WIPE), easing = EaseOut) else snap(),
        label = "wipe",
    )
    val swap = animateFloatAsState(if (sending) 1f else 0f, tween(150, easing = EaseOut), label = "swap")
    val hold = animateFloatAsState(if (pressed) 1f else 0f, tween(ms(PRESS), easing = EaseOut), label = "hold")
    // The button swells quickly and springs back.
    val swallow = animateFloatAsState(
        targetValue = if (gulp) 1f else 0f,
        animationSpec = if (gulp || still) tween(ms(PRESS), easing = EaseOut) else Settle,
        label = "gulp",
    )
    val spin = remember { Animatable(0f) }
    LaunchedEffect(spun) {
        if (spun) spin.animateTo(360f, tween(ms(SPIN_TIME), easing = EaseInOut)) else spin.snapTo(0f)
    }
    val shown = lifted != null
    val rise = animateFloatAsState(if (shown) 1f else 0f, if (shown && !still) Lift else Pop, label = "preview")

    val cardShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.04f), sigma = 7f) }
    val peekShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.22f), sigma = 14f) }
    val selection = remember(accent) { TextSelectionColors(handleColor = accent, backgroundColor = accent.copy(alpha = 0.4f)) }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // The pickers above the card: model and agent on the left, two bare icons on the right.
        Row(
            Modifier.size(WIDTH.dp, HEADER_HEIGHT.dp).padding(end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImagePromptCardChip(model, onClick = { onModel?.invoke() }) {
                ImagePromptCardIcon(MarkIcon, box = 16f, width = 1.7f, color = Ink)
            }
            ImagePromptCardChip(agent, onClick = { onAgent?.invoke() }) { ImagePromptCardAvatar() }
            Spacer(Modifier.weight(1f))
            ImagePromptCardTool(SlidersIcon, "Settings", onClick = { tool("settings") })
            ImagePromptCardTool(ClockIcon, "History", onClick = { tool("history") })
        }

        // The card. Its box is as tall as it gets; the white panel drawn in it grows with the row.
        Box(
            Modifier
                .offset(y = CARD_Y.dp)
                .size(WIDTH.dp, (REST_HEIGHT + ROW_HEIGHT).dp)
                .drawBehind {
                    val height = REST_HEIGHT + shift.value
                    inDp {
                        drawIntoCanvas { cardShadow.draw(it.nativeCanvas, 0f, 4f, WIDTH, height, RADIUS) }
                        drawRoundRect(Color.White, size = Size(WIDTH, height), cornerRadius = CornerRadius(RADIUS))
                        drawRoundRect(
                            color = Color(0xFFE4E2F0),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(WIDTH - 1f, height - 1f),
                            cornerRadius = CornerRadius(RADIUS - 0.5f),
                            style = Stroke(1f),
                        )
                    }
                },
        ) {
            ImagePromptCardPressable(
                onClick = {
                    touch()
                    onEnhance?.invoke()
                },
                modifier = Modifier.centredAt(WIDTH - 28f, 28f, 36f),
                label = "Enhance the prompt",
            ) {
                ImagePromptCardIcon(WandIcon, box = 20f, width = 1.6f, color = accent)
            }

            // The real field, with autoPlay's caret hanging off an invisible copy of the text.
            Box(
                Modifier
                    .offset { IntOffset(TEXT_X.dp.roundToPx(), (TEXT_Y + shift.value).dp.roundToPx()) }
                    .layout { measurable, _ ->
                        // Next to the wand the line stops 30 dp short; under the row of images it runs the card's width.
                        val width = WIDTH - TEXT_X * 2f - 30f * (1f - shift.value / ROW_HEIGHT)
                        val placeable = measurable.measure(Constraints.fixed(width.dp.roundToPx(), 24.dp.roundToPx()))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    BasicText(placeholder, style = PlaceholderStyle, overflow = TextOverflow.Ellipsis, maxLines = 1)
                }
                Row(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Non-breaking spaces, so that a space at the end moves the caret too.
                    BasicText(text.replace(' ', '\u00A0'), style = GhostStyle, softWrap = false, maxLines = 1)
                    Box(
                        Modifier
                            // Before the placeholder's first letter, not on it.
                            .offset(x = if (text.isEmpty()) (-2).dp else 1.dp)
                            .size(1.5.dp, 20.dp)
                            .graphicsLayer { alpha = if (playing && caretShown && !focused) blink.value else 0f }
                            .background(Ink, RoundedCornerShape(0.75.dp)),
                    )
                }
                CompositionLocalProvider(LocalTextSelectionColors provides selection) {
                    BasicTextField(
                        value = text,
                        onValueChange = { edit(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = wipe.value }
                            .onFocusChanged {
                                focused = it.isFocused
                                // A touch takes the field over from the script and starts from an empty card.
                                if (it.isFocused && playing) {
                                    playing = false
                                    reset()
                                }
                            }
                            .semantics { contentDescription = placeholder },
                        textStyle = FieldStyle,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { send(byHand = true) }),
                        singleLine = true,
                        cursorBrush = SolidColor(accent),
                    )
                }
            }

            // The bottom row: the plus, three tools, and on the right two more and the send button.
            Box(
                Modifier
                    .offset { IntOffset(0, (REST_HEIGHT + shift.value - TOOL_Y * 2f).dp.roundToPx()) }
                    .size(WIDTH.dp, (TOOL_Y * 2f).dp),
            ) {
                ImagePromptCardPressable(
                    onClick = {
                        touch()
                        add(byHand = true)
                    },
                    modifier = Modifier.centredAt(PLUS_X, TOOL_Y, 36f),
                    label = "Add an image",
                ) {
                    // A quarter turn for every image it hands over.
                    ImagePromptCardIcon(
                        PlusIcon,
                        box = 20f,
                        width = 1.6f,
                        color = Ink,
                        modifier = Modifier.graphicsLayer { rotationZ = turn.value },
                    )
                }
                Box(Modifier.offset(53.5.dp, (TOOL_Y - 10f).dp).size(1.dp, 20.dp).background(Color(0xFFE6E6EC)))
                ImagePromptCardTool(BulbIcon, "Ideas", Modifier.centredAt(78f, TOOL_Y, 34f)) { tool("ideas") }
                ImagePromptCardTool(ScopeIcon, "Explore", Modifier.centredAt(114f, TOOL_Y, 34f)) { tool("explore") }
                ImagePromptCardTool(GlobeIcon, "Search the web", Modifier.centredAt(150f, TOOL_Y, 34f)) { tool("web") }
                ImagePromptCardTool(TargetIcon, "Focus", Modifier.centredAt(244f, TOOL_Y, 34f)) { tool("focus") }
                ImagePromptCardTool(MicIcon, "Dictate", Modifier.centredAt(280f, TOOL_Y, 34f)) { tool("mic") }

                ImagePromptCardPressable(
                    onClick = {
                        touch()
                        send(byHand = true)
                    },
                    modifier = Modifier.centredAt(SEND_X, TOOL_Y, SEND_SIZE),
                    label = "Send",
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Held down by the script, or swelling as it swallows the pile.
                                val scale = lerp(1f, 0.9f, hold.value) * lerp(1f, 1.1f, swallow.value)
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(Color(0xFF0F1115), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImagePromptCardIcon(
                            ArrowIcon,
                            box = 20f,
                            width = 2f,
                            color = Color.White,
                            modifier = Modifier.graphicsLayer {
                                val s = swap.value
                                val scale = if (still) 1f else lerp(1f, 0.5f, s)
                                scaleX = scale
                                scaleY = scale
                                alpha = clamp01(1f - s)
                            },
                        )
                        // A three-quarter ring that turns once. Its box leaves room for the line's width.
                        Canvas(
                            Modifier.size(19.dp).graphicsLayer {
                                val s = swap.value
                                val scale = if (still) 1f else lerp(0.5f, 1f, s)
                                rotationZ = spin.value
                                scaleX = scale
                                scaleY = scale
                                alpha = clamp01(s)
                            },
                        ) {
                            inDp {
                                drawArc(
                                    color = Color.White,
                                    startAngle = 0f,
                                    sweepAngle = 360f * 0.72f,
                                    useCenter = false,
                                    topLeft = Offset(2f, 2f),
                                    size = Size(15f, 15f),
                                    style = Stroke(2f, cap = StrokeCap.Round),
                                )
                            }
                        }
                    }
                }
            }

            // A thumbnail's slot is its place among those that are staying.
            var slot = 0
            for (thumb in thumbs) {
                val at = slot
                if (!thumb.leaving) slot += 1
                val held = lifted == thumb.id
                key(thumb.id) {
                    ImagePromptCardTile(
                        thumb = thumb,
                        slot = at,
                        stacked = stacked,
                        departed = departed,
                        held = held,
                        still = still,
                        onHold = {
                            touch()
                            lift(thumb.id, byHand = true)
                        },
                        onDrop = { lifted = null },
                        onRemove = {
                            touch()
                            remove(thumb.id, byHand = true)
                        },
                        onGone = { thumbs = thumbs.filterNot { it.id == thumb.id } },
                        modifier = Modifier.zIndex(if (held) 10f else at.toFloat()),
                    )
                }
            }
        }

        // The held thumbnail shown larger, standing on the card's top edge above it. It takes no touches.
        Box(
            Modifier
                .offset(
                    (peekX.coerceIn(PEEK / 2f + 4f, WIDTH - PEEK / 2f - 4f) - PEEK / 2f - PEEK_BLEED).dp,
                    (CARD_Y - PEEK_GAP - PEEK - PEEK_BLEED).dp,
                )
                .size((PEEK + PEEK_BLEED * 2f).dp)
                .graphicsLayer {
                    // It grows from its bottom edge.
                    val scale = if (still) 1f else lerp(0.3f, 1f, rise.value)
                    transformOrigin = TransformOrigin(0.5f, (PEEK_BLEED + PEEK) / (PEEK + PEEK_BLEED * 2f))
                    scaleX = scale
                    scaleY = scale
                    alpha = clamp01(rise.value)
                }
                .drawBehind {
                    inDp {
                        drawIntoCanvas { peekShadow.draw(it.nativeCanvas, PEEK_BLEED, PEEK_BLEED + 12f, PEEK, PEEK, 22f) }
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(PEEK_BLEED, PEEK_BLEED),
                            size = Size(PEEK, PEEK),
                            cornerRadius = CornerRadius(22f),
                        )
                    }
                }
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            peek?.let { ImagePromptCardPhoto(it, Modifier.size((PEEK - 6f).dp).clip(RoundedCornerShape(19.dp))) }
        }
    }
}

/** One thumbnail, placed by its slot and moved by its flight in and its way out. */
@Composable
private fun ImagePromptCardTile(
    thumb: ImagePromptCardThumb,
    slot: Int,
    stacked: Boolean,
    departed: Boolean,
    held: Boolean,
    still: Boolean,
    onHold: () -> Unit,
    onDrop: () -> Unit,
    onRemove: () -> Unit,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnHold by rememberUpdatedState(onHold)
    val latestOnDrop by rememberUpdatedState(onDrop)
    val latestOnGone by rememberUpdatedState(onGone)

    val piled = stacked && !still
    val landed = remember { Animatable(0f) }
    val sunk = remember { Animatable(0f) }
    val gone = remember { Animatable(0f) }
    val x = animateFloatAsState(
        targetValue = THUMB_X + slot * (if (piled) PILE_STEP else THUMB_STEP),
        animationSpec = if (still) snap() else Slide,
        label = "slot",
    )
    val tilt = animateFloatAsState(
        targetValue = if (piled) (slot - 1) * PILE_TILT else 0f,
        animationSpec = if (still) snap() else Slide,
        label = "pile",
    )
    val lift = animateFloatAsState(if (held) 1f else 0f, if (still) Pop else Lift, label = "lift")
    val badge = animateFloatAsState(if (stacked || held) 0f else 1f, if (stacked || still) Pop else Lift, label = "badge")

    LaunchedEffect(Unit) {
        // It is laid at the plus first; a frame later it takes off for its slot.
        delay(20)
        landed.animateTo(1f, if (still) Pop else Land)
    }
    LaunchedEffect(departed) {
        if (!departed) return@LaunchedEffect
        // The pile dives into the button one thumbnail after another, the first slot first.
        sunk.animateTo(1f, if (still) Pop else tween(ms(SINK_TIME), ms(slot * SINK_STAGGER), Sink))
    }
    LaunchedEffect(thumb.leaving) {
        if (!thumb.leaving) return@LaunchedEffect
        gone.animateTo(1f, Pop)
        latestOnGone()
    }

    val rest = remember { SoftRoundRect(rgba(0, 0, 0, 0.14f), sigma = 4f) }
    val raised = remember { SoftRoundRect(rgba(0, 0, 0, 0.24f), sigma = 9f) }
    val badgeShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.18f), sigma = 2f) }
    // Where the send button is while the row is open.
    val sendY = REST_HEIGHT + ROW_HEIGHT - TOOL_Y

    Box(
        modifier
            .offset((THUMB_X - THUMB / 2f - THUMB_BLEED).dp, (THUMB_Y - THUMB / 2f - THUMB_BLEED).dp)
            .size((THUMB + THUMB_BLEED * 2f).dp)
            .graphicsLayer {
                val a = landed.value
                val d = sunk.value
                // Under reduced motion a thumbnail fades in and out where it stands.
                val away = if (still) 0f else 1f - a
                val dive = if (still) 0f else d
                val pileX = THUMB_X + slot * PILE_STEP
                val homeX = THUMB_X + thumb.home * THUMB_STEP
                val arc = if (still) 0f else FLY_ARC * sin(PI.toFloat() * clamp01(1f - away))
                val flightX = (thumb.from.x - homeX) * away + arc + (SEND_X - pileX) * dive
                val flightY = (thumb.from.y - THUMB_Y) * away + (sendY - THUMB_Y) * dive -
                    SINK_ARC * sin(PI.toFloat() * clamp01(dive))
                // The pile's tilt turns the thumbnail only, not the path it dives along; the pop shrinks it about its place in the row.
                val pop = lerp(1f, 0.6f, gone.value)
                val scale = lerp(1f, FLY_SCALE, away) * lerp(1f, SINK_SCALE, dive) * pop
                translationX = (x.value - THUMB_X + flightX * pop).dp.toPx()
                translationY = (flightY * pop).dp.toPx()
                rotationZ = FLY_TILT * away + tilt.value
                scaleX = scale
                scaleY = scale
                alpha = clamp01(a * 4f) * clamp01((1f - d) / 0.25f) * clamp01(1f - gone.value)
            },
    ) {
        // The photo in a white frame. A long press lifts it for as long as the finger stays down.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(THUMB.dp)
                .graphicsLayer {
                    val scale = if (still) 1f else lerp(1f, LIFT_SCALE, lift.value)
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    val up = clamp01(lift.value)
                    inDp {
                        // The shadow deepens as the thumbnail lifts: one fades into the other.
                        drawIntoCanvas {
                            rest.draw(it.nativeCanvas, 0f, 2f, THUMB, THUMB, 8f, alpha = 1f - up)
                            raised.draw(it.nativeCanvas, 0f, 6f, THUMB, THUMB, 8f, alpha = up)
                        }
                        drawRoundRect(Color.White, size = Size(THUMB, THUMB), cornerRadius = CornerRadius(8f))
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val reach = HOLD_REACH.dp.toPx()
                        val heldLongEnough = try {
                            withTimeout(ms(HOLD_TIME).toLong()) {
                                // A finger that lifts or strays before the time is up was not holding.
                                do {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                } while (change != null && change.pressed && (change.position - down.position).getDistance() <= reach)
                            }
                            false
                        } catch (_: PointerEventTimeoutCancellationException) {
                            true
                        }
                        if (heldLongEnough) {
                            latestOnHold()
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                latestOnDrop()
                            }
                        }
                    }
                }
                .semantics { contentDescription = thumb.image.title },
        ) {
            ImagePromptCardPhoto(thumb.image, Modifier.padding(2.dp).fillMaxSize().clip(RoundedCornerShape(6.5.dp)))
        }

        // The badge sits on the top right corner, and goes while the thumbnail is held up or piled.
        ImagePromptCardPressable(
            onClick = onRemove,
            modifier = Modifier
                .offset((THUMB_BLEED + THUMB - 28f + 12f).dp, (THUMB_BLEED - 12f).dp)
                .size(28.dp)
                .graphicsLayer { alpha = clamp01(badge.value) },
            label = "Remove ${thumb.image.title}",
            enabled = !stacked && !held,
        ) {
            Box(
                Modifier.size(18.dp).drawBehind {
                    inDp {
                        drawIntoCanvas { badgeShadow.draw(it.nativeCanvas, 0f, 1f, 18f, 18f, 9f) }
                        drawCircle(Color.White, radius = 9f, center = Offset(9f, 9f))
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                ImagePromptCardIcon(XIcon, box = 10f, width = 1.4f, color = IconInk)
            }
        }
    }
}

/** A remote photo over its flat tint, faded in when it arrives. */
@Composable
private fun ImagePromptCardPhoto(image: ImagePromptCardImage, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    // Decoded once, at its own size, so the preview finds in memory what the thumbnail loaded.
    val request = remember(context, image.photo) {
        ImageRequest.Builder(context).data(image.photo).size(coil3.size.Size.ORIGINAL).crossfade(200).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(image.tint),
    )
}

/** A picker above the card: a white chip with its mark and a 13 sp label. */
@Composable
private fun ImagePromptCardChip(label: String, onClick: () -> Unit, mark: @Composable () -> Unit) {
    ImagePromptCardPressable(onClick = onClick) {
        Row(
            Modifier
                .height(HEADER_HEIGHT.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark()
            BasicText(label, style = ChipStyle, maxLines = 1, softWrap = false)
        }
    }
}

/** A small lilac avatar: a head and shoulders. */
@Composable
private fun ImagePromptCardAvatar() {
    Canvas(Modifier.size(18.dp).clip(CircleShape)) {
        inDp {
            drawCircle(Color(0xFFDDD6FE), radius = 9f, center = Offset(9f, 9f))
            drawCircle(Color(0xFF8B7CF6), radius = 3.2f, center = Offset(9f, 7f))
            drawOval(Color(0xFF8B7CF6), topLeft = Offset(3f, 12f), size = Size(12f, 8f))
        }
    }
}

/** One of the bare icons: a 20 dp outline in a 34 dp box, even where its row is lower than that. */
@Composable
private fun ImagePromptCardTool(icon: Path, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ImagePromptCardPressable(onClick = onClick, modifier = modifier.requiredSize(34.dp), label = label) {
        ImagePromptCardIcon(icon, box = 20f, width = 1.5f, color = IconInk)
    }
}

/** An outline drawn in a [box] dp square, stroked [width] dp wide with round caps and joins. */
@Composable
private fun ImagePromptCardIcon(path: Path, box: Float, width: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(box.dp)) {
        scale(size.width / box, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A tappable part of the block: it gives a little while it is pressed. */
@Composable
private fun ImagePromptCardPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}
