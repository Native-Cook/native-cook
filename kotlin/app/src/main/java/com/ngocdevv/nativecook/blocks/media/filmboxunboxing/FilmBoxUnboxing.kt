package com.ngocdevv.nativecook.blocks.media.filmboxunboxing

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FilmDetail(val label: String, val value: String)

/**
 * @param expiresIn Seconds left before the filter expires, counted down from the moment it is shown.
 * @param tint The colour laid over a photo taken with it.
 */
data class Film(
    val name: String,
    val description: String,
    val expiresIn: Int,
    val details: List<FilmDetail>,
    val tint: Color,
    val tintOpacity: Float,
)

val AlienFilm = Film(
    name = "Alien",
    description = "Otherworldly filter with vibrant, alien-like colors.",
    expiresIn = 17 * 3600 + 21 * 60 + 58,
    details = listOf(FilmDetail("ISO", "Medium"), FilmDetail("Contrast", "High"), FilmDetail("Saturation", "Vibrant")),
    tint = Color(0xFF0F5560),
    tintOpacity = 0.4f,
)

private const val PHOTO = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=600&h=600&q=80"

private val Black = Color(0xFF000000)
private val TitleInk = Color(0xFFE6E6E6)
private val Mute = Color(0xFF8A8A8A)
private val DescriptionInk = Color(0xFFB0B0B0)
private val RowLabel = Color(0xFF6E6E6E)
private val RowValue = Color(0xFFE8D44D)
private val Rule = Color(0xFF222222)
private val ButtonFill = Color(0xFF1C1C1C)
private val RoundFill = Color(0xFF141414)
private val Accent = Color(0xFFFFD60A)
private val KnobTop = Color(0xFFFFE259)
private val KnobBottom = Color(0xFFFFC800)

// Layout, in dp. The box and the filter's name sit on the middle of the screen; the slider, the
// spinner, Load film and the camera's controls share one row above the bottom edge.
private const val HEADER_TOP = 54f
private const val CANISTER_ABOVE = 150f
/** The small canister at the top of the camera: its centre below the safe area. */
private const val SMALL_TOP = 40f
private const val TEXT_X = 40f
private const val DESCRIPTION_TOP = 22f
private const val ROWS_TOP = 70f
private const val ROW_HEIGHT = 26f
private const val ACTION = 88f
private const val PILL_WIDTH = 216f
private const val PILL_HEIGHT = 52f
private const val PILL_STROKE = 3f
private const val KNOB = 44f
private const val KNOB_INSET = 4f
private const val TRAVEL = PILL_WIDTH - KNOB_INSET * 2 - KNOB
/** Released past this share of the track, the knob opens the box; before it, it springs back. */
private const val UNBOX_THRESHOLD = 0.85f
private const val LOAD_WIDTH = 104f
private const val LOAD_HEIGHT = 38f
private const val ROUND_SIZE = 44f
private const val SIDE = 112f
private const val SHUTTER = 66f
private const val SHUTTER_RING = 3f
private const val SHUTTER_GAP = 3f
private const val RETAKE_WIDTH = 88f
private const val PHOTO_MAX = 300f
private const val PHOTO_MARGIN = 56f
private const val PHOTO_RADIUS = 6f
private const val BRACKET = 18f
private const val BRACKET_INSET = 10f
private const val ICON = 22f
private const val PRESSED = 0.92f
private const val PRESSED_ALPHA = 0.7f
/** The knob snaps the rest of the way in this long, and springs back like SwiftUI's spring(response: 0.35, dampingFraction: 0.75). */
private const val SNAP = 0.12
private const val SPRING_RESPONSE = 0.35
private const val SPRING_DAMPING = 0.75

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun filmText(size: Float, lineHeight: Float, color: Color, align: TextAlign = TextAlign.Start) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFamily = FontFamily.Monospace,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The knob moving from [from] to [to] since [at]: a short snap, or the spring back. */
private class Knob(val from: Float, val to: Float, val at: Double, val spring: Boolean) {
    fun at(now: Double): Float {
        val t = now - at
        val p = if (spring) springAt(t, SPRING_RESPONSE, SPRING_DAMPING) else clamp01(t / SNAP)
        return from + (to - from) * p.toFloat()
    }

    companion object {
        fun held(value: Float, now: Double) = Knob(value, value, now, false)
    }
}

/**
 * A daily filter, unboxed: a slider opens a film box drawn in perspective, the view dives into it,
 * and the filter's canister comes out turning, with its details. Load film flies the canister up
 * into a camera; the shutter takes a photo with the filter's colour on it.
 *
 * @param photo What the camera sees.
 * @param accent The ring of the slider, the knob and the shutter.
 * @param autoPlay Plays the whole flow by itself every [LOOP] seconds, from the closed box back to it.
 */
@Composable
fun FilmBoxUnboxing(
    modifier: Modifier = Modifier,
    title: String = "Unbox your filter for today",
    date: String = "5/7/26",
    slideLabel: String = "Unbox filter",
    expiresLabel: String = "Expires in",
    loadLabel: String = "Load film",
    retakeLabel: String = "Retake",
    film: Film = AlienFilm,
    photo: String = PHOTO,
    accent: Color = Accent,
    autoPlay: Boolean = false,
    onUnbox: () -> Unit = {},
    onLoad: (Film) -> Unit = {},
    onCapture: () -> Unit = {},
    onRetake: () -> Unit = {},
    onSave: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    val view = LocalView.current
    val context = LocalContext.current
    val still by rememberUpdatedState(rememberReducedMotion())
    val playing by rememberUpdatedState(autoPlay)
    val unboxed by rememberUpdatedState(onUnbox)
    val loaded by rememberUpdatedState(onLoad)
    val captured by rememberUpdatedState(onCapture)
    val retaken by rememberUpdatedState(onRetake)
    val currentFilm by rememberUpdatedState(film)

    // Seconds since the first frame: the clock every motion reads.
    var now by remember { mutableDoubleStateOf(0.0) }
    var events by remember { mutableStateOf(FilmEvents()) }
    // When the autoPlay loop started; -1 while touches have the screen.
    var origin by remember { mutableDoubleStateOf(if (autoPlay) 0.0 else -1.0) }
    var knob by remember { mutableStateOf(Knob.held(0f, 0.0)) }
    var touching by remember { mutableStateOf(false) }
    var panning by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableDoubleStateOf(0.0) }
    var dragFrom by remember { mutableFloatStateOf(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var timerOn by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }

    /** The actions at [t]: the loop's while it plays, else those touches made. */
    fun eventsAt(t: Double): FilmEvents {
        if (origin >= 0 && t >= origin) {
            val base = loopBase(t, origin)
            return loopEvents(t - base, base)
        }
        return events
    }

    fun dragAt(t: Double): Float {
        if (origin >= 0 && t >= origin) {
            val base = loopBase(t, origin)
            return loopDrag(t - base, still).toFloat()
        }
        return knob.at(t)
    }

    val scene by remember { derivedStateOf { sceneAt(now, eventsAt(now), still) } }
    val stage by remember { derivedStateOf { scene.stage } }
    val shownSeconds by remember { derivedStateOf { scene.shown.toInt() } }
    val drag by remember { derivedStateOf { dragAt(now) } }

    fun light() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun medium() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    /** A touch takes the screen from the autoPlay loop; a knob it left midway springs back. */
    fun takeOver(springBack: Boolean) {
        lastTouch = now
        if (origin < 0) return
        val d = dragAt(now)
        events = eventsAt(now)
        origin = -1.0
        knob = if (springBack && events.unbox < 0) Knob(d, 0f, now, true) else Knob.held(d, now)
    }

    fun unbox() {
        knob = Knob(dragAt(now), 1f, now, false)
        events = FilmEvents(unbox = now)
        medium()
        unboxed()
    }

    fun load() {
        if (stage != FilmStage.Film) return
        light()
        events = FilmEvents(unbox = events.unbox, load = now)
        loaded(currentFilm)
    }

    fun shoot() {
        if (stage != FilmStage.Camera) return
        medium()
        events = FilmEvents(unbox = events.unbox, load = events.load, shot = now, retake = events.retake)
        captured()
    }

    fun retake() {
        if (stage != FilmStage.Photo) return
        light()
        events = FilmEvents(unbox = events.unbox, load = events.load, shot = events.shot, retake = now)
        retaken()
    }

    LaunchedEffect(autoPlay) { origin = if (autoPlay) now else -1.0 }

    LaunchedEffect(Unit) {
        val first = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame -> now = (frame - first) / 1e9 }
            // A while after the last touch, the screen goes back to rest and the loop starts again.
            if (playing && origin < 0 && !touching && !panning && now >= max(lastTouch, settledAt(events)) + USER_HOLD) {
                val atRest = (events.unbox < 0 || (events.reset >= 0 && now >= events.reset + RESET_HALF * 2)) && dragAt(now) == 0f
                if (atRest) {
                    origin = now
                } else {
                    // Fade through black; the closed box and the knob are back by its darkest moment.
                    events = events.copy(reset = now)
                    knob = Knob.held(0f, now)
                    origin = now + RESET_HALF * 2
                }
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    takeOver(springBack = true)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    touching = false
                    lastTouch = now
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        val midY = h / 2
        val actionY = h - max(insets.calculateBottomPadding().value, 16f) - ACTION
        val smallY = top + SMALL_TOP
        val photoSize = minOf(w - PHOTO_MARGIN * 2, PHOTO_MAX, actionY - SHUTTER / 2 - smallY - 72)
        val photoY = (smallY + 24 + actionY - SHUTTER / 2) / 2
        val rows = listOf(FilmDetail(expiresLabel, countdown(film.expiresIn - shownSeconds))) + film.details

        Canvas(Modifier.fillMaxSize()) {
            val s = scene
            scale(density, density, pivot = Offset.Zero) {
                if (s.boxAlpha > 0.001f) {
                    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w, h), Paint().apply { alpha = s.boxAlpha }) }
                    drawFilmBox(
                        Offset(w / 2, midY + s.lift),
                        Offset(w / 2, midY),
                        BoxPose(s.yaw, s.tilt, s.roll, s.flap, s.glow, s.sweep, s.dive),
                    )
                    drawIntoCanvas { it.restore() }
                }
                if (s.spinner > 0.001f) drawFilmSpinner(Offset(w / 2, actionY), s.spinnerTime, s.spinner)
                if (s.canister > 0.001f) {
                    val from = midY - CANISTER_ABOVE
                    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w, h), Paint().apply { alpha = min(1f, s.canister) }) }
                    drawFilmCanister(Offset(w / 2, from + (smallY - from) * s.fly), s.canisterScale * (1 + (SMALL - 1) * s.fly), s.spin)
                    drawIntoCanvas { it.restore() }
                }
            }
        }

        Column(
            Modifier
                .offset(24.dp, (top + HEADER_TOP).dp)
                .width((w - 48).dp)
                .graphicsLayer { alpha = scene.header },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(title, Modifier.height(16.dp), style = filmText(12f, 16f, TitleInk), maxLines = 1)
            BasicText(date, Modifier.height(14.dp), style = filmText(11f, 14f, Mute), maxLines = 1)
        }

        // The slider: drag the knob along the pill to open the box.
        Box(
            Modifier
                .offset(((w - PILL_WIDTH) / 2).dp, (actionY - PILL_HEIGHT / 2).dp)
                .size(PILL_WIDTH.dp, PILL_HEIGHT.dp)
                .graphicsLayer { alpha = scene.pill }
                .border(PILL_STROKE.dp, accent, CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (stage == FilmStage.Box) {
                                panning = true
                                takeOver(springBack = false)
                                dragFrom = dragAt(now)
                                dragged = 0f
                            }
                        },
                        onDragEnd = {
                            if (panning) {
                                panning = false
                                lastTouch = now
                                if (dragAt(now) >= UNBOX_THRESHOLD && events.unbox < 0) unbox()
                                else knob = Knob(dragAt(now), 0f, now, true)
                            }
                        },
                        onDragCancel = {
                            if (panning) {
                                panning = false
                                knob = Knob(dragAt(now), 0f, now, true)
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            if (panning) {
                                change.consume()
                                dragged += amount.toDp().value
                                knob = Knob.held((dragFrom + dragged / TRAVEL).coerceIn(0f, 1f), now)
                            }
                        },
                    )
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = slideLabel
                    onClick {
                        if (stage == FilmStage.Box) {
                            takeOver(springBack = false)
                            unbox()
                        }
                        true
                    }
                },
        ) {
            BasicText(
                slideLabel,
                Modifier
                    .align(Alignment.Center)
                    .padding(start = (KNOB_INSET + KNOB).dp, end = KNOB_INSET.dp)
                    .width((PILL_WIDTH - KNOB_INSET * 2 - KNOB).dp)
                    // The label gives way as the knob passes over it.
                    .graphicsLayer { alpha = 1 - smooth(drag.toDouble(), 0.05, 0.45).toFloat() },
                style = filmText(12f, 16f, Color.White, TextAlign.Center),
                maxLines = 1,
            )
            Box(
                Modifier
                    .offset { IntOffset(((KNOB_INSET + drag * TRAVEL).dp.toPx()).roundToInt(), KNOB_INSET.dp.roundToPx()) }
                    .size(KNOB.dp)
                    .background(Brush.verticalGradient(listOf(KnobTop, KnobBottom)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                FilmIcon(FilmIcons.Arrow, ICON, Color.Black, 2f)
            }
        }

        Box(
            Modifier
                .offset(24.dp, (midY - 11).dp)
                .size((w - 48).dp, 22.dp)
                .graphicsLayer { alpha = scene.name * (1 - scene.cardOut) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(film.name, style = filmText(17f, 22f, Color.White), maxLines = 1)
        }

        BasicText(
            film.description,
            Modifier
                .offset(TEXT_X.dp, (midY + DESCRIPTION_TOP).dp)
                .width((w - TEXT_X * 2).dp)
                .reveal({ scene }, 0),
            style = filmText(11f, 16f, DescriptionInk),
        )
        rows.forEachIndexed { i, row ->
            Row(
                Modifier
                    .offset(TEXT_X.dp, (midY + ROWS_TOP + i * ROW_HEIGHT).dp)
                    .size((w - TEXT_X * 2).dp, ROW_HEIGHT.dp)
                    .reveal({ scene }, min(i + 1, 4))
                    .drawBehind { if (i > 0) drawRect(Rule, size = size.copy(height = 1.dp.toPx())) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(row.label, style = filmText(10f, 14f, RowLabel), maxLines = 1)
                BasicText(row.value, style = filmText(10f, 14f, RowValue).copy(fontFeatureSettings = "tnum"), maxLines = 1)
            }
        }

        val loadPress = remember { MutableInteractionSource() }
        val loadPressed by loadPress.collectIsPressedAsState()
        Box(
            Modifier
                .offset(((w - LOAD_WIDTH) / 2).dp, (actionY - LOAD_HEIGHT / 2).dp)
                .size(LOAD_WIDTH.dp, LOAD_HEIGHT.dp)
                .graphicsLayer {
                    val s = scene
                    alpha = s.rows[5] * (1 - s.cardOut)
                    translationY = (ROW_LIFT * (1 - s.rows[5])).dp.toPx()
                    scaleX = s.loadScale
                    scaleY = s.loadScale
                }
                .clickable(loadPress, indication = null, enabled = stage == FilmStage.Film, role = Role.Button) { load() }
                .graphicsLayer { alpha = if (loadPressed) PRESSED_ALPHA else 1f }
                .background(ButtonFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                loadLabel,
                Modifier.graphicsLayer { alpha = 1 - 0.62f * scene.grey },
                style = filmText(12f, 16f, Color.White),
                maxLines = 1,
            )
        }

        val request = remember(photo, context) { ImageRequest.Builder(context).data(photo).crossfade(200).build() }
        Box(
            Modifier
                .offset(((w - photoSize) / 2).dp, (photoY - photoSize / 2).dp)
                .size(photoSize.dp)
                .clearAndSetSemantics {},
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = scene.camera }) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(PHOTO_RADIUS.dp)).background(RoundFill),
                )
                Canvas(Modifier.fillMaxSize().padding(BRACKET_INSET.dp)) {
                    val b = BRACKET.dp.toPx()
                    val stroke = Stroke(2.dp.toPx())
                    val color = Color.White.copy(alpha = 0.9f)
                    for (corner in 0 until 4) {
                        val right = corner == 1 || corner == 3
                        val bottom = corner >= 2
                        val x = if (right) size.width else 0f
                        val y = if (bottom) size.height else 0f
                        val path = Path().apply {
                            moveTo(if (right) x - b else x + b, y)
                            lineTo(x, y)
                            lineTo(x, if (bottom) y - b else y + b)
                        }
                        drawPath(path, color, style = stroke)
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = scene.photo
                        scaleX = scene.photoScale
                        scaleY = scene.photoScale
                    }
                    .clip(RoundedCornerShape(PHOTO_RADIUS.dp))
                    .background(RoundFill),
            ) {
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(film.tint.copy(alpha = film.tintOpacity)))
            }
        }

        // The camera: timer, shutter, flash. Controls out of sight are out of the semantics too.
        val cameraOn = stage == FilmStage.Camera
        Box(
            Modifier
                .offset(0.dp, (actionY - SHUTTER / 2).dp)
                .size(w.dp, SHUTTER.dp)
                .graphicsLayer {
                    val s = scene
                    alpha = s.camera * s.cameraRow
                    translationY = (CAMERA_LIFT * (1 - s.camera) + 12 * (1 - s.cameraRow)).dp.toPx()
                }
                .then(if (cameraOn) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            val enabled = cameraOn
            RoundButton(Modifier.offset((w / 2 - SIDE - ROUND_SIZE / 2).dp, ((SHUTTER - ROUND_SIZE) / 2).dp), "Timer", enabled, {
                light()
                timerOn = !timerOn
            }) { FilmIcon(FilmIcons.Timer, ICON, if (timerOn) accent else Color.White, 1.6f) }
            val shutterPress = remember { MutableInteractionSource() }
            val shutterPressed by shutterPress.collectIsPressedAsState()
            Box(
                Modifier
                    .offset(((w - SHUTTER) / 2).dp, 0.dp)
                    .size(SHUTTER.dp)
                    .graphicsLayer {
                        scaleX = scene.shutter
                        scaleY = scene.shutter
                    }
                    .clickable(shutterPress, indication = null, enabled = enabled, role = Role.Button) { shoot() }
                    .clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = "Take photo"
                        onClick {
                            shoot()
                            true
                        }
                    }
                    .background(Black, CircleShape)
                    .border(SHUTTER_RING.dp, accent, CircleShape)
                    .padding((SHUTTER_RING + SHUTTER_GAP).dp)
                    .graphicsLayer { alpha = if (shutterPressed) PRESSED_ALPHA else 1f }
                    .background(Brush.verticalGradient(listOf(KnobTop, KnobBottom)), CircleShape),
            )
            RoundButton(
                Modifier.offset((w / 2 + SIDE - ROUND_SIZE / 2).dp, ((SHUTTER - ROUND_SIZE) / 2).dp),
                if (flashOn) "Flash on" else "Flash off",
                enabled,
                {
                    light()
                    flashOn = !flashOn
                },
            ) {
                FilmIcon(if (flashOn) FilmIcons.FlashOn else FilmIcons.FlashOff, ICON, if (flashOn) accent else Color.White, 1.6f, if (flashOn) accent else null)
            }
        }

        // The photo: save, retake, share.
        val photoOn = stage == FilmStage.Photo
        Box(
            Modifier
                .offset(0.dp, (actionY - SHUTTER / 2).dp)
                .size(w.dp, SHUTTER.dp)
                .graphicsLayer {
                    alpha = scene.photoRow
                    translationY = (12 * (1 - scene.photoRow)).dp.toPx()
                }
                .then(if (photoOn) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            val enabled = photoOn
            RoundButton(Modifier.offset((w / 2 - SIDE - ROUND_SIZE / 2).dp, ((SHUTTER - ROUND_SIZE) / 2).dp), "Save", enabled, {
                light()
                onSave()
            }) { FilmIcon(FilmIcons.Download, ICON, Color.White, 1.6f) }
            val retakePress = remember { MutableInteractionSource() }
            val retakePressed by retakePress.collectIsPressedAsState()
            Box(
                Modifier
                    .offset(((w - RETAKE_WIDTH) / 2).dp, ((SHUTTER - ROUND_SIZE) / 2).dp)
                    .size(RETAKE_WIDTH.dp, ROUND_SIZE.dp)
                    .graphicsLayer {
                        scaleX = if (retakePressed) PRESSED else 1f
                        scaleY = scaleX
                    }
                    .clickable(retakePress, indication = null, enabled = enabled, role = Role.Button) { retake() }
                    .background(ButtonFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(retakeLabel, style = filmText(12f, 16f, Color.White), maxLines = 1)
            }
            RoundButton(Modifier.offset((w / 2 + SIDE - ROUND_SIZE / 2).dp, ((SHUTTER - ROUND_SIZE) / 2).dp), "Share", enabled, {
                light()
                onShare()
            }) { FilmIcon(FilmIcons.Share, ICON, Color.White, 1.6f) }
        }

        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.White.copy(alpha = scene.flash)) })
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Black.copy(alpha = scene.overlay)) })
    }
}

/** One of the card's lines, coming in after the ones above it. */
private fun Modifier.reveal(scene: () -> FilmScene, index: Int) = graphicsLayer {
    val s = scene()
    val shown = s.rows[index]
    alpha = shown * (1 - s.cardOut)
    translationY = (ROW_LIFT * (1 - shown)).dp.toPx()
}

@Composable
private fun RoundButton(modifier: Modifier, label: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    Box(
        modifier
            .size(ROUND_SIZE.dp)
            .graphicsLayer {
                scaleX = if (pressed) PRESSED else 1f
                scaleY = scaleX
            }
            .clickable(press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                if (enabled) {
                    onClick {
                        onClick()
                        true
                    }
                }
            }
            .background(RoundFill, CircleShape),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
