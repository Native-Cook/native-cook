package com.ngocdevv.nativecook.blocks.inputs.scrubnumberfield

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Photo: a valley rendered in Blender for this block (see the block's notes).
private const val PHOTO_ASSET = "scrubnumberfield/scrubnumberfield_valley.jpg"

// ---------------------------------------------------------------- look and layout (dp)

private val PAGE = Color(0xFF111312)
private val PANEL = Color(0xFF1C1F1D)
private val INK = Color(0xFFF0EDE5)
private val MUTE = Color(0xFF8D938E)
private val ACCENT = Color(0xFFF2B441)
private val TICK_MINOR = INK.copy(alpha = 0.30f)
private val TICK_MAJOR = INK.copy(alpha = 0.62f)

private const val SIDE = 16f // screen margin
private const val NAV_H = 44f
private const val CAPTION_H = 20f
private const val FIELD_GAP = 10f
private const val FIELDS_GAP = 16f // caption to the first field
private const val HINT_GAP = 12f
private const val HINT_H = 18f
private const val FIELD_RADIUS = 20f
private const val PHOTO_RADIUS = 22f
private const val NUMBER_RIGHT = 18f // number's right edge from the field's
private const val RULER_BOTTOM = 10f // ruler baseline from the field's bottom edge
private const val RULER_FADE = 0.2f // share of the width the ruler fades out over, each side
private const val DOT = 44f // the ghost finger
private const val DRAG_FROM = 0.44f // where across a field the ghost finger lands
private const val DRAG_UP = 20f // its centre above the field's bottom edge: on the ruler, under the number
private const val TAP_AT_X = 0.56f // where on the photo it double taps
private const val TAP_AT_Y = 0.56f

private const val IDLE_S = 2.0 // after the last touch, before the values ease back to rest
private const val RETURN_S = 0.7f // the ease back, then the film restarts at t = 0
private const val COLUMNS = 3 // digits a field can show
private const val SMEAR_ROOM = 24f // dp above and below a digit's window that its blur may draw from
private const val TAP_MS = 500L // a tap's longest press, and the longest pause inside a double tap
private const val TAP_SLOP = 16f // dp a tap may wander

private const val GLYPHS = "0123456789+−."
private const val PLUS = 10
private const val MINUS = 11
private const val POINT = 12

// ---------------------------------------------------------------- state

/** A value on its way somewhere: a tween or the snap spring, as a function of the frame clock. */
private class ScrubAnim(
    val from: Float,
    val to: Float,
    private val t0: Double,
    private val duration: Float, // tween; ignored by a spring
    private val ease: ((Float) -> Float)?, // null: the snap spring
    private val v0: Float = 0f,
) {
    fun value(now: Double): Float {
        val s = (now - t0).toFloat()
        if (ease == null) return snapTo(from, to, v0, s)
        return if (s <= 0f) from else from + (to - from) * ease.invoke(s / duration)
    }

    fun done(now: Double) = (now - t0) >= if (ease == null) 3.0 else duration.toDouble()
}

/** What one field is drawn from. The state fields are read in layer and draw lambdas only. */
@Stable
private class ScrubField(val index: Int) {
    val spec = FIELDS[index]
    var pos by mutableFloatStateOf(spec.rest.toFloat()) // the value in steps, continuous
    var active by mutableFloatStateOf(0f) // 0..1 pressed: ruler shown, number lifted
    var stretch by mutableFloatStateOf(0f) // -1..1, from the finger's speed
    var speed by mutableFloatStateOf(0f) // steps per second, smoothed
    var shown by mutableIntStateOf(spec.rest) // the settled step, for screen readers

    var raw = spec.rest.toFloat() // pos before rubber-banding, while a finger scrubs
    var prev = spec.rest.toFloat() // pos on the frame before
    var lastTick = 0.0
    var posAnim: ScrubAnim? = null
    var activeAnim: ScrubAnim? = null
    var stretchAnim: ScrubAnim? = null

    fun step(now: Double) {
        posAnim?.let {
            pos = if (it.done(now)) it.to else it.value(now)
            if (it.done(now)) posAnim = null
        }
        activeAnim?.let {
            active = if (it.done(now)) it.to else it.value(now)
            if (it.done(now)) activeAnim = null
        }
        stretchAnim?.let {
            stretch = if (it.done(now)) it.to else it.value(now)
            if (it.done(now)) stretchAnim = null
        }
    }
}

@Stable
private class ScrubState(film: Boolean, reduced: Boolean) {
    val fields = List(FIELD_COUNT) { ScrubField(it) }
    var mode by mutableIntStateOf(if (film) MODE_AUTO else MODE_MANUAL)
    var tau by mutableFloatStateOf(0f) // loop time, seconds
    var film by mutableStateOf(film)
    var reduced by mutableStateOf(reduced)
    var ready by mutableStateOf(false) // photo decoded: the film may start

    private var clock = 0.0 // the frame clock, seconds
    private var loopStart = -1.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var holding = 0 // fingers down

    fun frame(now: Double) {
        val dt = (now - clock).toFloat().coerceIn(1f / 240, 0.05f)
        clock = now
        for (f in fields) f.step(now)
        val m = mode
        if (m == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                for (f in fields) {
                    f.pos = roundStep(f.pos).toFloat()
                    f.active = 0f
                    f.stretch = 0f
                }
            } else {
                var t = 0f
                if (ready) {
                    if (loopStart < 0) loopStart = now
                    t = ((now - loopStart) % LOOP).toFloat()
                }
                tau = t
                for (f in fields) {
                    f.pos = filmPos(f.index, t)
                    f.active = filmActive(f.index, t)
                    f.stretch = filmStretch(f.index, t)
                }
            }
        } else if (film && holding <= 0) {
            if (m == MODE_MANUAL && now - lastTouch > IDLE_S) {
                for (f in fields) f.posAnim = ScrubAnim(f.pos, f.spec.rest.toFloat(), now, RETURN_S, ::easeInOutCubic)
                returnAt = now
                mode = MODE_RETURN
            } else if (m == MODE_RETURN && now - returnAt > RETURN_S + 0.15) {
                loopStart = now
                tau = 0f
                for (f in fields) {
                    f.posAnim = null
                    f.activeAnim = null
                    f.stretchAnim = null
                }
                mode = MODE_AUTO
            }
        }
        // how fast each value is moving, for the digits' smear
        val k = 1f - exp(-dt / SPEED_TAU)
        for (f in fields) {
            val p = f.pos
            val inst = abs(p - f.prev) / dt
            f.prev = p
            val s = f.speed + (inst - f.speed) * k
            f.speed = if (s < 0.05f) 0f else s
        }
    }

    /** A finger anywhere: the film stops and every value settles on the step it is nearest. */
    fun take() {
        lastTouch = clock
        if (mode == MODE_MANUAL) return
        mode = MODE_MANUAL
        for (f in fields) {
            f.posAnim = ScrubAnim(f.pos, roundStep(f.pos).toFloat(), clock, 0f, null)
            f.activeAnim = ScrubAnim(f.active, 0f, clock, 0.2f, ::easeInOutQuad)
            f.stretchAnim = ScrubAnim(f.stretch, 0f, clock, 0.2f, ::easeInOutQuad)
        }
    }

    /** A double tap: one field (or all, index < 0) rolls back to where the screen opened. */
    fun reset(index: Int) {
        take()
        var n = 0
        for (f in fields) {
            if (index >= 0 && f.index != index) continue
            val delay = if (n > 0 && !reduced) n * RESET_STAGGER else 0f
            f.posAnim = ScrubAnim(f.pos, f.spec.rest.toFloat(), clock + delay, if (reduced) 0.18f else RESET_S, ::easeInOutCubic)
            f.shown = f.spec.rest
            n++
        }
    }

    fun panBegin(f: ScrubField) {
        take()
        holding++
        f.activeAnim = ScrubAnim(f.active, 1f, clock, 0.16f, ::easeInOutQuad)
    }

    /** The drag is on: catch the value wherever its spring is. */
    fun panStart(f: ScrubField) {
        f.posAnim = null
        f.raw = f.pos
    }

    /** dx in dp, vx in dp/s. True when a step passed under the finger and a detent is due. */
    fun panChange(f: ScrubField, dx: Float, vx: Float): Boolean {
        val spec = f.spec
        val before = roundStep(f.pos)
        val next = f.raw + dx / TICK * scrubGain(abs(vx))
        // the raw value cannot run away past the limits: keep it within reach of the rubber band
        f.raw = next.coerceIn(spec.min - 60f, spec.max + 60f)
        f.posAnim = null
        f.pos = rubber(f.raw, spec.min.toFloat(), spec.max.toFloat())
        if (!reduced) {
            val target = (vx / STRETCH_SPEED).coerceIn(-1f, 1f)
            f.stretchAnim = null
            f.stretch += (target - f.stretch) * 0.3f
        }
        lastTouch = clock
        // a detent for every step that passes under a finger, at most about 14 a second
        if (roundStep(f.pos) == before || clock - f.lastTick < 0.07) return false
        f.lastTick = clock
        return true
    }

    fun panEnd(f: ScrubField, vx: Float) {
        val spec = f.spec
        val v = vx / TICK * scrubGain(abs(vx)) // steps per second
        val p = f.pos
        val inside = p >= spec.min && p <= spec.max
        val goal = roundStep(p + if (inside) v * PROJECT else 0f).coerceIn(spec.min, spec.max)
        f.posAnim = if (reduced) ScrubAnim(p, goal.toFloat(), clock, 0.16f, ::easeInOutQuad)
        else ScrubAnim(p, goal.toFloat(), clock, 0f, null, if (inside) v else 0f)
        f.shown = goal
    }

    fun panFinalize(f: ScrubField) {
        holding = max(0, holding - 1)
        f.activeAnim = ScrubAnim(f.active, 0f, clock, 0.32f, ::easeInOutQuad)
        f.stretchAnim = ScrubAnim(f.stretch, 0f, clock, 0f, null)
        lastTouch = clock
    }

    /** A screen reader's step up or down. */
    fun nudge(f: ScrubField, by: Int) {
        take()
        val goal = (f.shown + by).coerceIn(f.spec.min, f.spec.max)
        f.shown = goal
        f.posAnim = ScrubAnim(f.pos, goal.toFloat(), clock, 0.16f, ::easeInOutQuad)
    }
}

/** The digit face and what the odometer needs to know about it. All sizes in dp. */
private class ScrubDigitType(size: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        textSize = size
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 600, false)
        else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = INK.toArgb()
    }
    val glyphs = Array(GLYPHS.length) { GLYPHS[it].toString() }
    val widths = FloatArray(GLYPHS.length) { paint.measureText(glyphs[it]) }
    val colW = ceil((0..9).maxOf { widths[it] }) + 1f // one digit column
    val cellH = (size * 1.2f).roundToInt().toFloat() // distance a digit rolls
    val baseline: Float // baseline below a cell's centre

    init {
        val box = AndroidRect()
        paint.getTextBounds("0", 0, 1, box)
        baseline = if (box.height() > 0) -box.exactCenterY() else size * 0.35f
    }

    fun draw(canvas: android.graphics.Canvas, glyph: Int, x: Float, y: Float, alpha: Float) {
        if (alpha <= 0f) return
        paint.alpha = (alpha * INK.alpha * 255f).roundToInt().coerceIn(0, 255) // the paint is shared
        canvas.drawText(glyphs[glyph], x, y, paint)
    }
}

/**
 * Number fields you scrub like in a design tool: press one and drag sideways. A ruler slides under
 * the number, the digits roll like an odometer (and smear when the drag is fast, which also makes
 * the steps coarser), the field stretches after the finger and a spring snaps to the step on
 * release. Three of them edit a photo live through one shader.
 */
@Composable
fun ScrubNumberField(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    val film = autoPlay && !reduced
    val state = remember { ScrubState(film, reduced) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val bitmap by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) { context.assets.open(PHOTO_ASSET).use { BitmapFactory.decodeStream(it) } }
    }
    val painter = remember(bitmap) { bitmap?.let { ScrubPhotoPainter(it) } }
    SideEffect {
        state.film = film
        state.reduced = reduced
        state.ready = painter != null
    }
    LaunchedEffect(state) {
        var base = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (base < 0) base = nanos
                state.frame((nanos - base) / 1e9)
            }
        }
    }
    BlockSystemBars(darkIcons = false)

    BoxWithConstraints(modifier.fillMaxSize().background(PAGE)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val insetTop = insets.calculateTopPadding().value
        val insetBottom = insets.calculateBottomPadding().value
        val compact = height < 740f

        val contentW = min(width, 480f)
        val w = contentW - SIDE * 2
        val fieldH = if (compact) 66f else 76f
        val navTop = insetTop + 4f
        val photoTop = navTop + NAV_H + if (compact) 8f else 12f
        // the photo takes the height the fields leave: close to square on a tall phone
        val fieldsBlock = FIELD_COUNT * fieldH + (FIELD_COUNT - 1) * FIELD_GAP
        val below = 12f + CAPTION_H + FIELDS_GAP + fieldsBlock + HINT_GAP + HINT_H + max(insetBottom, 12f) + 8f
        val photoH = (height - photoTop - below).coerceIn(w * 0.5f, w * 1.25f).roundToInt().toFloat()
        val captionTop = photoTop + photoH + 12f
        val fieldsTop = captionTop + CAPTION_H + FIELDS_GAP
        val hintTop = fieldsTop + fieldsBlock + HINT_GAP
        val left = (width - contentW) / 2f + SIDE

        val digitSize = if (compact) 28f else 30f
        val type = remember(digitSize) { ScrubDigitType(digitSize) }

        Box(Modifier.offset(left.dp, navTop.dp).size(w.dp, NAV_H.dp)) {
            BasicText("Cancel", Modifier.align(Alignment.CenterStart), TextStyle(color = MUTE, fontSize = 17.sp))
            BasicText("Adjust", Modifier.align(Alignment.Center), TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                "Done",
                Modifier.align(Alignment.CenterEnd).background(ACCENT, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                TextStyle(color = PAGE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        val photoShape = RoundedCornerShape(PHOTO_RADIUS.dp)
        Box(
            Modifier
                .offset(left.dp, photoTop.dp)
                .size(w.dp, photoH.dp)
                .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 2.dp, blur = 4.dp, radius = PHOTO_RADIUS.dp)
                .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 18.dp, blur = 40.dp, radius = PHOTO_RADIUS.dp)
                .background(PANEL, photoShape)
                .pointerInput(state) {
                    val slop = TAP_SLOP.dp.toPx()
                    var lastTap = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.take()
                        val up = awaitTapUp(down, slop)
                        if (up == null) {
                            lastTap = 0L
                        } else if (lastTap > 0L && down.uptimeMillis - lastTap <= TAP_MS) {
                            lastTap = 0L
                            state.reset(-1)
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        } else {
                            lastTap = up.uptimeMillis
                        }
                    }
                }
                .clearAndSetSemantics {
                    contentDescription = "A valley between snowy mountains, seen over spruce trees. Double tap twice to reset the adjustments."
                },
        ) {
            if (painter != null) {
                Canvas(Modifier.matchParentSize()) {
                    val f = state.fields
                    drawIntoCanvas {
                        painter.draw(
                            it.nativeCanvas, size.width, size.height, PHOTO_RADIUS.dp.toPx(),
                            exposure = f[EXPOSURE].pos * EXPOSURE_STOPS_PER_STEP,
                            warmth = f[WARMTH].pos * WARMTH_PER_STEP,
                            vignette = max(0f, f[VIGNETTE].pos * VIGNETTE_PER_STEP),
                        )
                    }
                }
            }
            Box(Modifier.matchParentSize().border(Dp.Hairline, Color.White.copy(alpha = 0.16f), photoShape))
        }

        Row(
            Modifier.offset(left.dp, captionTop.dp).size(w.dp, CAPTION_H.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("Valley overlook", style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            BasicText("35 mm · f/8 · 1/640 s · ISO 100", style = TextStyle(color = MUTE, fontSize = 13.sp, fontFeatureSettings = "tnum"))
        }

        for (field in state.fields) {
            ScrubFieldCard(
                field = field,
                state = state,
                type = type,
                modifier = Modifier.offset(left.dp, (fieldsTop + field.index * (fieldH + FIELD_GAP)).dp),
                width = w,
                height = fieldH,
            )
        }

        BasicText(
            "Drag a value sideways to change it. Double-tap to reset.",
            Modifier.offset(left.dp, hintTop.dp).width(w.dp),
            TextStyle(color = MUTE, fontSize = 13.sp, textAlign = TextAlign.Center),
        )

        ScrubGhostTouch(
            state = state,
            dragX = left + w * DRAG_FROM,
            fieldY = fieldsTop + fieldH - DRAG_UP,
            fieldPitch = fieldH + FIELD_GAP,
            tapX = left + w * TAP_AT_X,
            tapY = photoTop + photoH * TAP_AT_Y,
        )
    }
}

/**
 * After a press: the lift that ends a tap, or null when the finger wandered, stayed too long or the
 * gesture was cancelled.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitTapUp(
    down: PointerInputChange,
    slop: Float,
): PointerInputChange? {
    var tap = true
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > slop) tap = false
        if (change.changedToUp()) {
            return if (tap && change.uptimeMillis - down.uptimeMillis <= TAP_MS) change else null
        }
    }
}

// ---------------------------------------------------------------- one field

@Composable
private fun ScrubFieldCard(field: ScrubField, state: ScrubState, type: ScrubDigitType, modifier: Modifier, width: Float, height: Float) {
    val spec = field.spec
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(FIELD_RADIUS.dp)

    // ---- where the ruler and the digits go (dp)
    val cy = height / 2f
    val rulerY = height - RULER_BOTTOM
    val centre = width / 2f
    val colW = type.colW
    val minDigits = spec.decimals + 1
    val xr = width - NUMBER_RIGHT
    val pointW = if (spec.decimals > 0) type.widths[POINT] else 0f
    val colLeft = floatArrayOf(xr - colW, xr - colW * 2, xr - colW * 3 - pointW)
    val signLeft = colLeft[COLUMNS - 1] - max(type.widths[PLUS], type.widths[MINUS]) - 1.5f

    Box(
        modifier
            .size(width.dp, height.dp)
            // outside the stretch's layer: a finger's travel must not be scaled by what it causes
            .pointerInput(field) {
                val dragSlop = 4.dp.toPx()
                val tapSlop = TAP_SLOP.dp.toPx()
                var lastTap = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.panBegin(field)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragging = false
                    var travel = 0f
                    var tap = true
                    var up: PointerInputChange? = null
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > tapSlop) tap = false
                        if (change.changedToUp()) {
                            up = change
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - change.previousPosition.x
                        if (dragging) {
                            val vx = tracker.calculateVelocity().x
                            if (state.panChange(field, dx / density, vx / density)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            change.consume()
                        } else {
                            travel += dx
                            if (abs(travel) > dragSlop) {
                                dragging = true
                                state.panStart(field)
                            }
                        }
                    }
                    if (dragging && up != null) state.panEnd(field, tracker.calculateVelocity().x / density)
                    state.panFinalize(field)
                    // the second tap of a double tap rolls the value back
                    if (up == null || !tap || up.uptimeMillis - down.uptimeMillis > TAP_MS) {
                        lastTap = 0L
                    } else if (lastTap > 0L && down.uptimeMillis - lastTap <= TAP_MS) {
                        lastTap = 0L
                        state.reset(field.index)
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    } else {
                        lastTap = up.uptimeMillis
                    }
                }
            }
            // the field grows towards the finger's direction; its trailing edge stays where it is
            .graphicsLayer {
                val st = field.stretch
                val mag = abs(st)
                val sx = 1f + STRETCH_MAX * mag
                translationX = (if (st < 0f) -1f else 1f) * (sx - 1f) * size.width / 2f
                scaleX = sx
                scaleY = 1f - SQUASH_MAX * mag
            }
            .cssBoxShadow(Color.Black.copy(alpha = 0.40f), offsetY = 1.dp, blur = 2.dp, radius = FIELD_RADIUS.dp)
            .cssBoxShadow(Color.Black.copy(alpha = 0.22f), offsetY = 10.dp, blur = 24.dp, radius = FIELD_RADIUS.dp)
            .background(PANEL, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = spec.label
                stateDescription = "${formatValue(spec, field.shown)} ${spec.accessibilityUnit}".trim()
                customActions = listOf(
                    CustomAccessibilityAction("Increase") { state.nudge(field, 1); true },
                    CustomAccessibilityAction("Decrease") { state.nudge(field, -1); true },
                )
            }
            .drawBehind {
                // a hairline highlight along the top edge
                val r = FIELD_RADIUS.dp.toPx()
                drawRect(Color.White.copy(alpha = 0.10f), Offset(r, 0f), Size(size.width - r * 2, 1f))

                val a = field.active
                if (a <= 0.001f) return@drawBehind
                // pressed: a faint fill and an amber edge
                val corner = CornerRadius(r)
                drawRoundRect(INK, cornerRadius = corner, alpha = 0.045f * a)
                val edge = 1.dp.toPx()
                drawRoundRect(
                    ACCENT, Offset(edge / 2, edge / 2), Size(size.width - edge, size.height - edge),
                    CornerRadius(r - edge / 2), Stroke(edge), alpha = 0.55f * a,
                )

                // The ruler: every step a tick, the value under a fixed index in the middle. Its edges
                // are faded inside one layer (a gradient with dstIn), and the layer fades with the press.
                val d = density
                val y = rulerY * d
                val wPx = size.width
                val band = Rect(0f, y - 16 * d, wPx, y + 2 * d)
                val pos = field.pos
                drawContext.canvas.saveLayer(band, androidx.compose.ui.graphics.Paint().apply { alpha = a })
                val origin = centre * d - pos * TICK * d
                val firstStep = max(spec.min, ceil(pos - centre / TICK).toInt() - 1)
                val lastStep = min(spec.max, floor(pos + centre / TICK).toInt() + 1)
                for (s in firstStep..lastStep) {
                    val x = origin + s * TICK * d
                    if (s == 0) {
                        if (spec.min < 0) drawRoundRect(INK, Offset(x - d, y - 12 * d), Size(2 * d, 12 * d), CornerRadius(d))
                    } else if (s % MAJOR_EVERY == 0) {
                        drawLine(TICK_MAJOR, Offset(x, y), Offset(x, y - 9 * d), 1.5f * d, StrokeCap.Round)
                    } else {
                        drawLine(TICK_MINOR, Offset(x, y), Offset(x, y - 5 * d), d)
                    }
                }
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, RULER_FADE to Color.White, 1f - RULER_FADE to Color.White, 1f to Color.Transparent,
                        startX = 0f, endX = wPx,
                    ),
                    band.topLeft, band.size, blendMode = BlendMode.DstIn,
                )
                drawRoundRect(ACCENT, Offset(centre * d - d, y - 15 * d), Size(2 * d, 15 * d), CornerRadius(d))
                drawContext.canvas.restore()
            },
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 16.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(20.dp).drawBehind { fieldSymbol(field.index) })
            BasicText(spec.label, style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp))
        }

        // The number, always shown: one strip of digits per column, right-aligned at a fixed inset.
        Box(Modifier.matchParentSize().graphicsLayer { translationY = -LIFT * field.active * density }) {
            Canvas(Modifier.matchParentSize()) {
                val pos = field.pos
                val x = abs(pos) * spec.unitsPerStep
                scale(density, density, Offset.Zero) {
                    drawIntoCanvas {
                        val canvas = it.nativeCanvas
                        if (spec.signed) {
                            // the sign sits against the first digit that shows
                            var hidden = 0f
                            for (k in minDigits until COLUMNS) hidden += (1f - clamp01(odoHigh(x, k, spec.unitsPerStep))) * colW
                            type.draw(canvas, PLUS, signLeft + hidden, cy + type.baseline, clamp01(pos / 0.5f))
                            type.draw(canvas, MINUS, signLeft + hidden, cy + type.baseline, clamp01(-pos / 0.5f))
                        }
                        if (spec.decimals > 0) type.draw(canvas, POINT, xr - colW * 2 - pointW, cy + type.baseline, 1f)
                    }
                }
            }
            for (k in 0 until COLUMNS) {
                ScrubDigitColumn(
                    k = k,
                    always = k < minDigits,
                    field = field,
                    state = state,
                    type = type,
                    modifier = Modifier.offset((colLeft[k] - 1f).dp, (cy - type.cellH / 2f - 1f).dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- one digit of the odometer

/**
 * A strip of digits behind a window one digit tall. The strip's position is the odometer's: the last
 * digit turns with the value, the others only while the digits below them wrap. The two fastest
 * columns are drawn through a vertical blur whose size follows the value's speed (from API 31; below
 * it the digits roll without the smear).
 */
@Composable
private fun ScrubDigitColumn(k: Int, always: Boolean, field: ScrubField, state: ScrubState, type: ScrubDigitType, modifier: Modifier) {
    val unitsPerStep = field.spec.unitsPerStep
    // the strip's glyphs, and the first one again for the wrap
    val strip = remember(k, unitsPerStep) {
        val cycle = (0..9).filter { k > 0 || it % unitsPerStep == 0 }
        (cycle + cycle[0]).toIntArray()
    }
    val n = strip.size - 1
    val position = {
        val x = abs(field.pos) * unitsPerStep
        if (k == 0) odoLow(x, unitsPerStep) else odoHigh(x, k, unitsPerStep)
    }
    Box(modifier.size((type.colW + 2f).dp, (type.cellH + 2f).dp).clipToBounds()) {
        Canvas(
            Modifier
                // taller than the window, so the blur has the neighbouring digits to draw from
                .layout { measurable, constraints ->
                    val room = SMEAR_ROOM.dp.roundToPx()
                    val layer = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + room * 2))
                    layout(constraints.maxWidth, constraints.maxHeight) { layer.place(0, -room) }
                }
                .graphicsLayer {
                    var sigma = if (k < 2 && !state.reduced) digitBlur(field.speed) else 0f
                    if (k == 1) {
                        val p = position()
                        val f = p - floor(p)
                        sigma *= 4f * f * (1f - f) // only while this column is turning
                    }
                    // Android's blur radius is not the sigma: sigma = 0.57735 * radius + 0.5
                    val radius = (sigma * density - 0.5f) / 0.57735f
                    renderEffect = if (radius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(0f, radius, TileMode.Decal)
                    } else {
                        null
                    }
                },
        ) {
            val p = position()
            // a digit rests on its line and turns in the middle half of a step
            val v = rollEase(p)
            val shift = (v - floor(v / n) * n) * type.cellH
            // a leading column fades in as it turns from nothing to 1
            val alpha = if (always) 1f else clamp01(p)
            val top = SMEAR_ROOM.dp.roundToPx() / density + 1f + type.cellH / 2f + type.baseline - shift
            scale(density, density, Offset.Zero) {
                drawIntoCanvas {
                    for (i in strip.indices) {
                        val g = strip[i]
                        type.draw(it.nativeCanvas, g, 1f + (type.colW - type.widths[g]) / 2f, top + i * type.cellH, alpha)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the small parts

/** The fields' symbols, drawn in a 20 dp box: plus-minus in a circle, a thermometer, a dashed circle. */
private fun DrawScope.fieldSymbol(index: Int) {
    val u = 1.dp.toPx()
    val c = center
    val line = 1.5f * u
    when (index) {
        EXPOSURE -> {
            drawCircle(MUTE, 8f * u, c, style = Stroke(line))
            drawLine(MUTE, Offset(c.x - 2.8f * u, c.y - 2.2f * u), Offset(c.x + 2.8f * u, c.y - 2.2f * u), line, StrokeCap.Round)
            drawLine(MUTE, Offset(c.x, c.y - 5f * u), Offset(c.x, c.y + 0.6f * u), line, StrokeCap.Round)
            drawLine(MUTE, Offset(c.x - 2.8f * u, c.y + 3.8f * u), Offset(c.x + 2.8f * u, c.y + 3.8f * u), line, StrokeCap.Round)
        }
        WARMTH -> {
            val x = c.x - 1.5f * u
            drawLine(MUTE, Offset(x, c.y - 6.5f * u), Offset(x, c.y + 4f * u), 5f * u, StrokeCap.Round)
            drawCircle(MUTE, 4.3f * u, Offset(x, c.y + 5f * u))
            drawLine(PANEL, Offset(x, c.y - 6.5f * u), Offset(x, c.y - 0.5f * u), 2.2f * u, StrokeCap.Round)
            for (i in 0..2) {
                val y = c.y - 6f * u + i * 3f * u
                drawLine(MUTE, Offset(x + 4.6f * u, y), Offset(x + 7f * u, y), 1.2f * u, StrokeCap.Round)
            }
        }
        else -> {
            val radius = 8f * u
            val dash = (2 * Math.PI * radius / 10).toFloat()
            drawCircle(MUTE, radius, c, style = Stroke(1.6f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.64f, dash * 0.36f))))
        }
    }
}

/** In the film a soft dot shows the gesture: a press and a sideways drag on each field, a double tap on the photo. */
@Composable
private fun ScrubGhostTouch(state: ScrubState, dragX: Float, fieldY: Float, fieldPitch: Float, tapX: Float, tapY: Float) {
    Box(
        Modifier
            .size(DOT.dp)
            .graphicsLayer {
                if (state.mode != MODE_AUTO) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val t = state.tau
                var a = filmTapAlpha(t)
                var x = tapX
                var y = tapY
                var press = filmTapPress(t)
                for (i in 0 until FIELD_COUNT) {
                    val ai = filmDotAlpha(i, t)
                    if (ai > a) {
                        a = ai
                        x = dragX + filmFinger(i, t)
                        y = fieldY + i * fieldPitch
                        press = filmDotPress(i, t)
                    }
                }
                alpha = a
                translationX = (x - DOT / 2f) * density
                translationY = (y - DOT / 2f) * density
                scaleX = 1f - 0.16f * press
                scaleY = 1f - 0.16f * press
            }
            .background(INK.copy(alpha = 0.22f), CircleShape)
            .border(1.5.dp, INK.copy(alpha = 0.55f), CircleShape),
    )
}
