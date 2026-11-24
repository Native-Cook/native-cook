package com.ngocdevv.nativecook.blocks.forms.addresslookupmap

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.ADDRESSES
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.matchRange
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapData.rank
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.CHOOSE_LEN
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.DROP_LEN
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.FILL_GAP
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.FILL_LEN
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.FLY_LEN
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.IDLE
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.LOOP
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.MAP_ROT
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.MAX_SCALE
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.MIN_SCALE_K
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.NEAR
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.OVERVIEW_X
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.OVERVIEW_Y
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.Pose
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.RETURN_LEN
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.RING_LEN
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.ROW_H
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.SCRIPT
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.TYPED
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.T_STILL
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.autoDot
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.autoPose
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.clamp01
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.distanceKm
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.dragToWorld
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.dropSquash
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.dropY
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.easeInOutCubic
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.overviewScale
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.seg
import com.ngocdevv.nativecook.blocks.forms.addresslookupmap.AddressLookupMapMotion.stepAt
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Address Lookup Map — the delivery step of Parcelo's checkout (a port of AddressLookupMap.tsx).

 Typing an address shows suggestions that rise in with the matched letters in bold and re-rank as
 the query grows. Picking one folds the list into the field, the drawn map (AddressLookupMapStreet,
 no map tiles) flies to the address, the pin drops with a bounce and a soft ring, and unit, postcode
 and city fill in one after the other. Dragging the map slides the town under the pin to fine-tune
 the drop-off point; pinching zooms.

 The loop (AddressLookupMapMotion) and a person drive the same values: while the loop plays every
 value comes from `autoPose(t)`; a touch copies the frame at that moment into Animatables and the
 person takes over. Two seconds after the last touch (and no field focused) it eases back in 0.8 s
 and the loop starts again at t = 0. Reduced motion: still at t = 6.18, nothing plays.

 Per-frame values live in `AddressLookupMapController.pose` and are read only in graphicsLayer /
 draw / layout lambdas; composition changes only on keystrokes, picks and script steps.
*/

private val Accent = Color(0xFFE0562E)
private val Ink = Color(0xFF1E1D1A)
private val Muted = Color(0xFF77736A)
private val Page = Color(0xFFF6F4EF)
private val Card = Color(0xFFFFFEFB)
private val Hairline = Color(0x1F282218) // rgba(40,34,24,0.12)
private val PlaceholderInk = Color(0xFFB3AEA3)
private const val DOT_SIZE = 44f
private const val ICON_X = 44f
private const val CALLOUT_W = 112f
private const val CALLOUT_H = 44f
/**
 * How far the camera's centre may go on one axis at scale s: far enough that the (turned) map window stays on the
 * drawn town, so a pan or a fling never shows the blank paper past its edge (cameraBound in AddressLookupMap.tsx).
 */
private fun cameraBound(s: Float, w: Float, h: Float, axis: Int): FloatArray {
    val c = kotlin.math.abs(kotlin.math.cos(MAP_ROT))
    val n = kotlin.math.abs(kotlin.math.sin(MAP_ROT))
    val half = (if (axis == 0) w * c + h * n else w * n + h * c) / 2f / s
    val lo = min(AddressLookupMapData.WORLD / 2f, half)
    return floatArrayOf(lo, AddressLookupMapData.WORLD - lo)
}
private val Ease: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOut: Easing = Easing { easeInOutCubic(it) }
/** Reanimated's default withTiming curve. */
private val EaseInOutQuad: Easing = Easing { if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) / 2 }
private val EaseOutCubicE: Easing = Easing { val u = 1 - it; 1 - u * u * u }

private val Sans = FontFamily.SansSerif
private val Plain = PlatformTextStyle(includeFontPadding = false)
private fun style(size: Float, weight: FontWeight = FontWeight.Normal, color: Color = Ink, tracking: Float = 0f) =
    TextStyle(fontFamily = Sans, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = tracking.sp, platformStyle = Plain)

/** A Reanimated duration-based spring, approximated: natural period = duration. */
private fun springOf(ms: Int, ratio: Float): AnimationSpec<Float> {
    val w = 2 * PI / (ms / 1000.0)
    return spring(dampingRatio = ratio, stiffness = (w * w).toFloat())
}

private fun timing(ms: Int, easing: Easing = EaseInOutQuad): AnimationSpec<Float> = tween(ms, easing = easing)

/** A value a person drives: an Animatable with one running job (a new write cancels the last). */
private class AddressLookupMapVal(private val scope: CoroutineScope, init: Float) {
    val a = Animatable(init)
    private var job: Job? = null
    val v: Float get() = a.value

    fun snap(x: Float) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { a.snapTo(x) }
    }

    fun to(x: Float, spec: AnimationSpec<Float>, delayMs: Long = 0, from: Float? = null) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (from != null) a.snapTo(from)
            if (delayMs > 0) delay(delayMs)
            a.animateTo(x, spec)
        }
    }

    /** Fling with the finger's velocity; past the bounds it settles on the bound instead (a soft rubber band). */
    fun fling(velocity: Float, lo: Float, hi: Float) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Reanimated's withDecay (deceleration 0.998 per ms) ≈ friction 2 /s; Compose's base friction is ~4.2 /s.
            val decay = exponentialDecay<Float>(frictionMultiplier = 0.48f)
            val target = decay.calculateTargetValue(a.value, velocity)
            if (a.value in lo..hi && target in lo..hi) a.animateDecay(velocity, decay)
            else a.animateTo(target.coerceIn(lo, hi), spring(dampingRatio = 1f, stiffness = 90f), initialVelocity = velocity)
        }
    }
}

/** The loop clock, the hand-over state and every value the screen draws. */
@Stable
private class AddressLookupMapController(val scope: CoroutineScope) {
    var s0 = 0.3f
    var t by mutableFloatStateOf(0f)
    /** 0 = the loop, 1 = a person, 2 = easing back to rest, 3 = resetting. */
    var mode = 0
    var user by mutableStateOf(false)
    var now = 0f
    var lastTouch = 0f
    /** When a pick's film (fly, drop, fills, Continue ready) ends: the idle return counts from there too. */
    var filmEnd = 0f
    var returnAt = 0f
    var editing = false

    var pose by mutableStateOf(autoPose(0f, 0.3f), referentialEqualityPolicy())
    var dot by mutableStateOf(AddressLookupMapMotion.Dot(0, 0f, 1f, 0f, 0f), referentialEqualityPolicy())

    private fun v(x: Float) = AddressLookupMapVal(scope, x)
    val focus = v(0f)
    val caret = v(0f)
    val text = v(1f)
    val placeholder = v(1f)
    val list = v(0f)
    val count = v(0f)
    val choose = v(0f)
    var chosen = 0
    val camX = v(OVERVIEW_X)
    val camY = v(OVERVIEW_Y)
    val camS = v(0.3f)
    val drop = v(0f)
    val pin = v(0f)
    val ring1 = v(0f)
    val ring2 = v(0f)
    val lift = v(0f)
    val callA = v(0f)
    val callB = v(0f)
    val fills = arrayOf(v(0f), v(0f), v(0f))
    val ready = v(0f)
    val rowY = Array(ADDRESSES.size) { v(0f) }
    val rowA = Array(ADDRESSES.size) { v(0f) }

    fun userPose() = Pose(
        focus.v, caret.v, text.v, placeholder.v, list.v, count.v, choose.v, chosen,
        FloatArray(rowY.size) { rowY[it].v }, FloatArray(rowA.size) { rowA[it].v },
        camX.v, camY.v, camS.v, drop.v, pin.v, ring1.v, ring2.v, lift.v, callA.v, callB.v,
        fills[0].v, fills[1].v, fills[2].v, ready.v,
    )

    /** Copies a pose into the person's values (the moment a touch takes over). */
    fun writePose(p: Pose) {
        focus.snap(p.focus); caret.snap(0f); text.snap(p.text); placeholder.snap(p.placeholder)
        list.snap(p.list); count.snap(p.count); choose.snap(p.choose); chosen = p.chosen
        camX.snap(p.camX); camY.snap(p.camY); camS.snap(p.camS); drop.snap(p.drop); pin.snap(p.pin)
        ring1.snap(0f); ring2.snap(0f); lift.snap(0f); callA.snap(p.callA); callB.snap(p.callB)
        for (i in 0..2) fills[i].snap(p.fill(i))
        ready.snap(p.ready)
        for (i in rowY.indices) { rowY[i].snap(p.rowY[i]); rowA[i].snap(p.rowA[i]) }
    }

    /** Eases every value to the loop's first frame. */
    fun easeToRest(p: Pose, d: Int) {
        val o = timing(d, EaseInOut)
        val quick = timing((d * 0.4f).toInt())
        focus.to(0f, quick); caret.snap(0f); text.to(0f, quick); list.to(0f, quick)
        callA.to(0f, quick); callB.to(0f, quick)
        for (f in fills) f.to(0f, quick)
        ready.to(0f, quick); pin.to(0f, quick); lift.to(0f, quick)
        camX.to(p.camX, o); camY.to(p.camY, o); camS.to(p.camS, o)
    }
}

private class AddressLookupMapGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val compact = h < 740f
    val margin = max(20f, (w - 440f) / 2)
    val width = w - margin * 2
    val headerTop = top + 2
    val titleTop = headerTop + if (compact) 52f else 62f
    val titleSize = if (compact) 23f else 27f
    val fieldTop = titleTop + if (compact) 38f else 46f
    val fieldH = if (compact) 50f else 54f
    val mapTop = fieldTop + fieldH + 12
    val buttonH = if (compact) 50f else 54f
    val buttonTop = h - max(bottom, 14f) - 8 - buttonH
    val boxH = if (compact) 54f else 58f
    val row2 = buttonTop - 14 - boxH
    val row1 = row2 - 10 - boxH
    val mapH = row1 - 14 - mapTop
    val listTop = fieldTop + fieldH + 6
}

private fun rubber(x: Float, lo: Float, hi: Float): Float = when {
    x < lo -> lo - (lo - x) / (1 + (lo - x) / 160)
    x > hi -> hi + (x - hi) / (1 + (x - hi) / 160)
    else -> x
}

private fun View.selectionTick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
private fun View.lightTap() = performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
private fun View.success() = performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
private fun View.warning() = performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

/** Details as the person left them, for [AddressLookupMap]'s onContinue. */
data class AddressLookupMapDetails(val unit: String, val postcode: String, val city: String, val leaveAtDoor: Boolean)

@Composable
fun AddressLookupMap(
    modifier: Modifier = Modifier,
    /** Types, picks and fine-tunes an address every LOOP seconds until the screen is touched. */
    autoPlay: Boolean = true,
    /** Called by Continue with the chosen address and the details as filled in. */
    onContinue: ((AddressLookupMapAddress, AddressLookupMapDetails) -> Unit)? = null,
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val still = autoPlay && reduced
    val scope = rememberCoroutineScope()
    val ctl = remember { AddressLookupMapController(scope) }
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bars = WindowInsets.systemBars.asPaddingValues()

    BoxWithConstraints(modifier.fillMaxSize().background(Page)) {
        val g = remember(maxWidth, maxHeight, bars) {
            AddressLookupMapGeo(maxWidth.value, maxHeight.value, bars.calculateTopPadding().value, bars.calculateBottomPadding().value)
        }
        val s0 = overviewScale(g.width)
        ctl.s0 = s0

        // ---- Composition: changes on keystrokes and picks only ------------------------------------
        val loopTime by remember(still) { derivedStateOf { if (still) T_STILL else ctl.t } }
        val step by remember { derivedStateOf { stepAt(loopTime) } }
        var takeT by remember { mutableFloatStateOf(0f) }
        var uQuery by remember { mutableStateOf(TextFieldValue("")) }
        var uSelected by remember { mutableStateOf<Int?>(null) }
        var unit by remember { mutableStateOf("") }
        var postcode by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("") }
        var leaveAtDoor by remember { mutableStateOf(true) }
        /** Top of the detail row being edited (the rows rise above the keyboard), and which field it is. */
        val raisedTop = remember { mutableFloatStateOf(-1f) }
        var raisedId by remember { mutableStateOf(-1) }
        val imeDp = remember { mutableFloatStateOf(0f) }
        val fieldFocus = remember { FocusRequester() }

        val user = ctl.user
        val query = if (user) uQuery.text else SCRIPT[step].query
        val selected = if (user) uSelected else SCRIPT[step].selected
        val picked = selected?.let { ADDRESSES[it] }
        val filled = picked != null && SCRIPT[step].filled
        val shownUnit = if (user) unit else if (filled) picked.unit else ""
        val shownPostcode = if (user) postcode else if (filled) picked.postcode else ""
        val shownCity = if (user) city else if (filled) picked.city else ""
        val listOpen = if (user) uQuery.text.isNotEmpty() && uSelected == null else step in 1..TYPED
        val slots = remember(listOpen, user, uQuery.text, step) {
            val q = if (user) (if (listOpen) uQuery.text else "") else SCRIPT[min(step, TYPED)].query
            val l = rank(q)
            IntArray(ADDRESSES.size) { l.indexOf(it) }
        }

        fun resetToAuto() {
            ctl.user = false
            uQuery = TextFieldValue("")
            uSelected = null
            unit = ""; postcode = ""; city = ""
            leaveAtDoor = true
            ctl.t = 0f
            ctl.mode = 0
        }

        // ---- The frame loop ------------------------------------------------------------------------
        LaunchedEffect(ctl, auto, still, reduced) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = if (previous == 0L) 0f else min((nanos - previous) / 1e9f, 0.064f)
                previous = nanos
                ctl.now += dt
                when (ctl.mode) {
                    0 -> if (auto) ctl.t = (ctl.t + dt) % LOOP
                    1 -> if (!ctl.editing && ctl.now - ctl.lastTouch > IDLE && ctl.now - ctl.filmEnd > IDLE) {
                        ctl.mode = 2
                        ctl.returnAt = ctl.now
                        ctl.easeToRest(autoPose(0f, ctl.s0), if (reduced) 0 else (RETURN_LEN * 1000).toInt())
                    }
                    2 -> if (ctl.now - ctl.returnAt > RETURN_LEN) {
                        ctl.mode = 3
                        resetToAuto()
                    }
                }
                ctl.pose = if (ctl.mode == 0) autoPose(if (still) T_STILL else ctl.t, ctl.s0) else ctl.userPose()
                ctl.dot = if (ctl.mode == 0 && auto) autoDot(ctl.t) else AddressLookupMapMotion.Dot(0, 0f, 1f, 0f, 0f)
            }
        }

        // ---- Hand-over -----------------------------------------------------------------------------
        /** React-side of a take-over at loop time t: the loop's values become the person's. */
        fun adopt(t: Float) {
            val s = SCRIPT[stepAt(t)]
            val a = s.selected?.let { ADDRESSES[it] }
            uQuery = TextFieldValue(s.query, TextRange(s.query.length))
            uSelected = s.selected
            unit = if (s.filled && a != null) a.unit else ""
            postcode = if (s.filled && a != null) a.postcode else ""
            city = if (s.filled && a != null) a.city else ""
            takeT = t
            ctl.user = true
        }

        /** Any touch: the loop stops where it is and the person drives from there. */
        fun takeOver() {
            when (ctl.mode) {
                0 -> {
                    val t = if (still) T_STILL else if (auto) ctl.t else 0f
                    adopt(t)
                    ctl.writePose(autoPose(t, ctl.s0))
                    ctl.mode = 1
                    ctl.pose = ctl.userPose()
                }
                2 -> ctl.mode = 1
            }
            ctl.lastTouch = ctl.now
        }

        // ---- The person's actions ------------------------------------------------------------------
        fun listUI(open: Boolean, n: Int, d: Int) {
            ctl.list.to(if (open) 1f else 0f, timing((d * 0.3f).toInt(), Ease))
            ctl.count.to(n.toFloat(), springOf(max(d, 1), 0.82f))
            ctl.choose.snap(0f)
            ctl.text.snap(1f)
            ctl.placeholder.snap(1f)
        }

        fun onChangeQuery(v: TextFieldValue) {
            val changed = v.text != uQuery.text
            takeOver()
            uQuery = v
            if (!changed) return
            if (uSelected != null) uSelected = null
            listUI(v.text.isNotEmpty(), rank(v.text).size, if (reduced) 0 else 420)
        }

        fun chooseUI(i: Int, x: Float, y: Float, fast: Boolean) {
            val k = if (fast) 0f else 1f
            fun ms(v: Float) = (v * k).toInt()
            fun ml(v: Float) = (v * k).toLong()
            ctl.chosen = i
            ctl.choose.to(1f, timing(ms(CHOOSE_LEN * 1000), EaseInOut), from = 0f)
            ctl.focus.to(0f, timing(ms(200f)))
            ctl.text.to(1f, timing(ms(230f)), delayMs = ml(200f), from = 0f)
            ctl.camX.to(x, timing(ms(FLY_LEN * 1000), EaseInOut))
            ctl.camY.to(y, timing(ms(FLY_LEN * 1000), EaseInOut))
            ctl.camS.to(NEAR, timing(ms(FLY_LEN * 1000), EaseInOut))
            val land = (FLY_LEN - 0.1f) * 1000 * k
            ctl.pin.to(1f, timing(ms(80f)), delayMs = land.toLong(), from = 0f)
            ctl.drop.to(1f, timing(ms(DROP_LEN * 1000), LinearEasing), delayMs = land.toLong(), from = 0f)
            ctl.ring1.to(1f, timing(ms(RING_LEN * 1000), LinearEasing), delayMs = (land + 330 * k).toLong(), from = 0f)
            ctl.ring2.to(1f, timing(ms(RING_LEN * 1000), LinearEasing), delayMs = (land + 750 * k).toLong(), from = 0f)
            ctl.callB.to(0f, timing(ms(150f)))
            ctl.callA.to(1f, timing(ms(300f), Ease), delayMs = (land + 550 * k).toLong(), from = 0f)
            for (j in 0..2) ctl.fills[j].to(1f, timing(ms(FILL_LEN * 1000), Ease), delayMs = (land + (600 + j * FILL_GAP * 1000) * k).toLong(), from = 0f)
            ctl.ready.to(1f, timing(ms(400f)), delayMs = (land + 1050 * k).toLong())
            // The idle return counts from the end of the pick's film, or it would ease back while the fields fill.
            ctl.filmEnd = ctl.now + (land + 1450 * k) / 1000f
        }

        fun choose(i: Int) {
            takeOver()
            val a = ADDRESSES[i]
            uQuery = TextFieldValue(a.street, TextRange(a.street.length))
            uSelected = i
            unit = a.unit; postcode = a.postcode; city = a.city
            focusManager.clearFocus()
            keyboard?.hide()
            view.selectionTick()
            chooseUI(i, a.x, a.y, reduced)
            if (!reduced) scope.launch {
                delay(((FLY_LEN - 0.1f + DROP_LEN * 0.5f) * 1000).toLong())
                if (ctl.mode == 1) view.lightTap()
            }
        }

        fun clearQuery() {
            takeOver()
            uQuery = TextFieldValue("")
            uSelected = null
            listUI(false, 0, if (reduced) 0 else 300)
            fieldFocus.requestFocus()
        }

        fun recenter() {
            takeOver()
            view.lightTap()
            val o = timing(700, EaseInOut)
            val p = picked
            if (p != null) { ctl.camX.to(p.x, o); ctl.camY.to(p.y, o); ctl.camS.to(NEAR, o) }
            else { ctl.camX.to(OVERVIEW_X, o); ctl.camY.to(OVERVIEW_Y, o); ctl.camS.to(s0, o) }
        }

        fun onDetail(j: Int, v: String) {
            takeOver()
            when (j) {
                0 -> unit = v
                1 -> postcode = v.filter { it.isDigit() }.take(4)
                else -> city = v
            }
            ctl.fills[j].snap(1f)
        }

        val ok = if (user) uSelected != null && postcode.length == 4 && city.trim().isNotEmpty() else true
        LaunchedEffect(ok, user, reduced) {
            if (user) ctl.ready.to(if (ok) 1f else 0f, timing(if (reduced) 0 else 300))
        }

        fun onContinuePress() {
            takeOver()
            val p = picked
            if (!ok || p == null) { view.warning(); return }
            view.success()
            onContinue?.invoke(p, AddressLookupMapDetails(unit, postcode, city, leaveAtDoor))
        }

        fun detailFocus(id: Int, top: Float, on: Boolean) {
            if (on) {
                takeOver()
                raisedTop.floatValue = top
                raisedId = id
            } else if (raisedId == id) {
                raisedTop.floatValue = -1f
                raisedId = -1
            }
            ctl.editing = on
            ctl.lastTouch = ctl.now
        }

        // ---- Keyboard: the lower fields rise above it while one of them is being edited -----------
        ImeHeight { imeDp.floatValue = it }

        // ---- Map gestures ----------------------------------------------------------------------------
        val mapGesture = Modifier.pointerInput(s0, reduced) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                takeOver()
                val slop = 4.dp.toPx()
                var panning = false
                var pinching = false
                var total = Offset.Zero
                var zoom = 1f
                var startX = 0f
                var startY = 0f
                var startS = 1f
                val tracker = VelocityTracker()
                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed == 0) break
                    val pan = ev.calculatePan()
                    val centroid = ev.calculateCentroid(useCurrent = true)
                    if (centroid != Offset.Unspecified) tracker.addPosition(ev.changes.first().uptimeMillis, centroid)
                    total += pan
                    if (!panning && total.getDistance() > slop) {
                        panning = true
                        takeOver()
                        startX = ctl.camX.v - dragToWorld(total.x / density.density, total.y / density.density, ctl.camS.v)[0]
                        startY = ctl.camY.v - dragToWorld(total.x / density.density, total.y / density.density, ctl.camS.v)[1]
                        ctl.lift.to(1f, springOf(300, 0.8f))
                        ctl.callA.to(0f, timing(150))
                        ctl.callB.to(0f, timing(150))
                    }
                    if (pressed >= 2 && !pinching) {
                        pinching = true
                        takeOver()
                        startS = ctl.camS.v
                        zoom = 1f
                    }
                    if (pinching) {
                        zoom *= ev.calculateZoom()
                        ctl.camS.snap(rubber(startS * zoom, s0 * MIN_SCALE_K, MAX_SCALE))
                    }
                    if (panning) {
                        val o = dragToWorld(total.x / density.density, total.y / density.density, ctl.camS.v)
                        val bx = cameraBound(ctl.camS.v, g.width, g.mapH, 0)
                        val by = cameraBound(ctl.camS.v, g.width, g.mapH, 1)
                        ctl.camX.snap(rubber(startX + o[0], bx[0], bx[1]))
                        ctl.camY.snap(rubber(startY + o[1], by[0], by[1]))
                    }
                    ctl.lastTouch = ctl.now
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                if (panning) {
                    val vel = tracker.calculateVelocity()
                    val v = dragToWorld(vel.x / density.density, vel.y / density.density, ctl.camS.v)
                    val bx = cameraBound(ctl.camS.v, g.width, g.mapH, 0)
                    val by = cameraBound(ctl.camS.v, g.width, g.mapH, 1)
                    ctl.camX.fling(v[0], bx[0], bx[1])
                    ctl.camY.fling(v[1], by[0], by[1])
                    ctl.lift.to(0f, springOf(450, 0.55f))
                    if (ctl.pin.v > 0.5f) {
                        ctl.callB.to(1f, timing(280), delayMs = 260)
                        view.lightTap()
                    }
                }
                if (pinching) {
                    val lo = s0 * MIN_SCALE_K
                    val target = ctl.camS.v.coerceIn(lo, MAX_SCALE)
                    ctl.camS.to(target, springOf(400, 0.85f))
                    // Zooming out widens the window: bring the centre back inside the town at the scale it settles on.
                    val bx = cameraBound(target, g.width, g.mapH, 0)
                    val by = cameraBound(target, g.width, g.mapH, 1)
                    val cx = ctl.camX.v
                    val cy = ctl.camY.v
                    if (cx < bx[0] || cx > bx[1]) ctl.camX.to(cx.coerceIn(bx[0], bx[1]), springOf(400, 0.85f))
                    if (cy < by[0] || cy > by[1]) ctl.camY.to(cy.coerceIn(by[0], by[1]), springOf(400, 0.85f))
                }
                ctl.lastTouch = ctl.now
            }
        }

        // ---- The screen ------------------------------------------------------------------------------
        Box(Modifier.fillMaxSize().graphicsLayer {
            val top = raisedTop.floatValue
            val ime = imeDp.floatValue
            val lift = if (top < 0 || ime <= 0f) 0f else max(0f, top + g.boxH + 16 - (g.h - ime))
            translationY = -lift * density.density
        }) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus(); keyboard?.hide() }
            })

            AddressLookupMapHeader(g)

            BasicText(
                "Where should we deliver?",
                Modifier.offset(g.margin.dp, g.titleTop.dp).width(g.width.dp),
                style = style(g.titleSize, FontWeight.Bold, tracking = -0.6f).copy(lineHeight = (g.titleSize + 6).sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )

            // The map.
            Box(
                Modifier.offset(g.margin.dp, g.mapTop.dp).size(g.width.dp, g.mapH.dp)
                    .cssBoxShadow(Color(0x1F281E12), 1.dp, 3.dp, 22.dp)
                    .cssBoxShadow(Color(0x1F281E12), 14.dp, 32.dp, 22.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .then(mapGesture)
                    .semantics { contentDescription = "Map of the delivery address. Drag to move the drop-off point." },
            ) {
                val art = remember { AddressLookupMapStreet() }
                Canvas(Modifier.fillMaxSize()) {
                    val p = ctl.pose
                    drawIntoCanvas {
                        art.draw(
                            it.nativeCanvas, density.density, g.width, g.mapH,
                            AddressLookupMapCamera(p.camX, p.camY, p.camS),
                            AddressLookupMapPinPose(dropY(p.drop), dropSquash(p.drop), p.pin, p.lift, p.ring1, p.ring2),
                            MAP_ROT,
                        )
                    }
                }
                AddressLookupMapCallouts(ctl, g.width, g.mapH)
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp).size(36.dp)
                        .cssBoxShadow(Color(0x29281E12), 1.dp, 2.dp, 18.dp)
                        .cssBoxShadow(Color(0x1F281E12), 6.dp, 14.dp, 18.dp)
                        .background(Card, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Recenter map", role = Role.Button) { recenter() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Recenter map", tint = Ink, modifier = Modifier.size(15.dp))
                }
                Box(Modifier.fillMaxSize().border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(22.dp)))
            }

            // The details.
            Row(Modifier.offset(g.margin.dp, g.row1.dp).width(g.width.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddressLookupMapDetailBox("Unit or flat", shownUnit, ctl, 0, g.boxH, 1.25f) {
                    AddressLookupMapDetailInput(shownUnit, "Optional", tabular = false, number = false,
                        onChange = { onDetail(0, it) }, onFocus = { detailFocus(0, g.row1, it) })
                }
                AddressLookupMapDetailBox("Postcode", shownPostcode, ctl, 1, g.boxH, 1f) {
                    AddressLookupMapDetailInput(shownPostcode, "0000", tabular = true, number = true,
                        onChange = { onDetail(1, it) }, onFocus = { detailFocus(1, g.row1, it) })
                }
            }
            Row(Modifier.offset(g.margin.dp, g.row2.dp).width(g.width.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddressLookupMapDetailBox("City", shownCity, ctl, 2, g.boxH, 1.25f) {
                    AddressLookupMapDetailInput(shownCity, "Town or city", tabular = false, number = false,
                        onChange = { onDetail(2, it) }, onFocus = { detailFocus(2, g.row2, it) })
                }
                Row(
                    Modifier.weight(1f).height(g.boxH.dp)
                        .cssBoxShadow(Color(0x0F281E12), 1.dp, 2.dp, 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Card)
                        .border(0.5.dp, Hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText("Leave at door", Modifier.weight(1f), style = style(15f, FontWeight.Medium, tracking = -0.2f), maxLines = 2)
                    Switch(
                        checked = leaveAtDoor,
                        onCheckedChange = { takeOver(); leaveAtDoor = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White, checkedBorderColor = Accent),
                        modifier = Modifier.semantics { contentDescription = "Leave at door" },
                    )
                }
            }

            AddressLookupMapButton(g, ctl, ::onContinuePress)

            // Suggestions sit over the map.
            Box(
                Modifier.offset(g.margin.dp, g.listTop.dp).width(g.width.dp)
                    .layout { m, c ->
                        val p = ctl.pose
                        val hh = ((8 + p.count * ROW_H) * (1 - p.choose * p.choose)).coerceAtLeast(0f)
                        val px = (hh * density.density).toInt()
                        val pl = m.measure(Constraints.fixed(c.maxWidth, px))
                        layout(pl.width, pl.height) { pl.place(0, 0) }
                    }
                    .graphicsLayer {
                        val p = ctl.pose
                        alpha = p.list * (1 - seg(p.choose, 0.5f, 1f))
                    }
                    .cssBoxShadow(Color(0x14281E12), 2.dp, 4.dp, 18.dp)
                    .cssBoxShadow(Color(0x29281E12), 18.dp, 40.dp, 18.dp)
                    .background(Card, RoundedCornerShape(18.dp))
                    .border(0.5.dp, Hairline, RoundedCornerShape(18.dp)),
            )
            ADDRESSES.forEachIndexed { i, a ->
                AddressLookupMapSuggestion(
                    index = i, address = a, query = query, slot = slots[i], open = listOpen,
                    picked = user && uSelected == i, user = user, ctl = ctl, g = g, onPress = { choose(i) },
                )
            }

            // The field.
            AddressLookupMapField(
                g = g, ctl = ctl, query = query, value = if (user) uQuery else TextFieldValue(query, TextRange(query.length)),
                showClear = user && uQuery.text.isNotEmpty(), focusRequester = fieldFocus,
                onChange = ::onChangeQuery,
                onFocus = { on ->
                    if (on) { takeOver(); raisedTop.floatValue = -1f; raisedId = -1 }
                    ctl.editing = on
                    ctl.focus.to(if (on) 1f else 0f, timing(200))
                    ctl.lastTouch = ctl.now
                },
                onSubmit = { rank(uQuery.text).firstOrNull()?.let { choose(it) } },
                onClear = ::clearQuery,
            )
        }

        AddressLookupMapGhostFinger(ctl, g)
    }
}

/** Reports the keyboard's height in dp; recomposes only itself while the keyboard moves. */
@Composable
private fun ImeHeight(onHeight: (Float) -> Unit) {
    val density = LocalDensity.current
    val px = WindowInsets.ime.getBottom(density)
    SideEffect { onHeight(px / density.density) }
}

@Composable
private fun AddressLookupMapHeader(g: AddressLookupMapGeo) {
    Row(
        Modifier.offset((g.margin - 6).dp, g.headerTop.dp).width((g.width + 6).dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(72.dp).fillMaxHeight().padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText("Checkout", style = style(17f, FontWeight.SemiBold, tracking = -0.3f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in listOf(Ink, Accent, Color(0x24282218))) Box(Modifier.size(22.dp, 3.dp).background(c, RoundedCornerShape(2.dp)))
            }
        }
        Box(Modifier.width(72.dp).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            BasicText("2 of 3", style = style(13f, FontWeight.Medium, Muted).copy(fontFeatureSettings = "tnum"))
        }
    }
}

/** The address field: a real text field, the loop's own caret, the search icon that becomes a pin. */
@Composable
private fun AddressLookupMapField(
    g: AddressLookupMapGeo,
    ctl: AddressLookupMapController,
    query: String,
    value: TextFieldValue,
    showClear: Boolean,
    focusRequester: FocusRequester,
    onChange: (TextFieldValue) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val inputStyle = style(17f, tracking = -0.2f)
    val measurer = rememberTextMeasurer()
    // Caret of the loop's typing: the advance of the typed text, measured with a trailing bar so a
    // trailing space is kept.
    val caretX = remember(query) {
        val r = measurer.measure("$query|", inputStyle)
        r.getBoundingBox(query.length).left
    }
    var wasFocused by remember { mutableStateOf(false) }
    Box(Modifier.offset(g.margin.dp, g.fieldTop.dp).size(g.width.dp, g.fieldH.dp)) {
        // Focus ring and outline.
        Box(
            Modifier.offset((-4).dp, (-4).dp).size((g.width + 8).dp, (g.fieldH + 8).dp)
                .graphicsLayer { alpha = ctl.pose.focus }
                .background(Color(0x1FE0562E), RoundedCornerShape(20.dp)),
        )
        Box(
            Modifier.fillMaxSize()
                .cssBoxShadow(Color(0x14281E12), 1.dp, 2.dp, 16.dp)
                .cssBoxShadow(Color(0x12281E12), 8.dp, 22.dp, 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .drawBehind {
                    val f = ctl.pose.focus
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        lerp(Hairline, Accent, f), topLeft = Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(16.dp.toPx() - w / 2), style = Stroke(w),
                    )
                },
        ) {
            Box(Modifier.width(ICON_X.dp).fillMaxHeight().graphicsLayer { alpha = 1 - ctl.pose.pin }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
            }
            Box(
                Modifier.width(ICON_X.dp).fillMaxHeight().graphicsLayer {
                    val p = ctl.pose.pin
                    alpha = p
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            if (query.isEmpty()) {
                Box(
                    Modifier.padding(start = ICON_X.dp, end = 14.dp).fillMaxSize().graphicsLayer { alpha = ctl.pose.placeholder },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText("Street, building or postcode", style = style(17f, color = Color(0xFFA9A498), tracking = -0.2f), maxLines = 1)
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ctl.pose.text }) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = inputStyle,
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxSize()
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused != wasFocused) { wasFocused = it.isFocused; onFocus(it.isFocused) } }
                        .semantics { contentDescription = "Delivery address" },
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxSize().padding(start = ICON_X.dp, end = if (showClear) 40.dp else 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { inner() }
                    },
                )
            }
            // The loop's caret (the real one belongs to the text field when a person types).
            Box(
                Modifier.offset((ICON_X + caretX / LocalDensity.current.density + 1).dp, ((g.fieldH - 22) / 2).dp)
                    .size(2.dp, 22.dp)
                    .graphicsLayer { alpha = ctl.pose.caret }
                    .background(Accent, RoundedCornerShape(1.dp)),
            )
            if (showClear) {
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(32.dp, g.fieldH.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Clear address") { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Clear address", tint = PlaceholderInk, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

/** One suggestion: matched letters in bold. Placed by the loop, or by springs when a person types. */
@Composable
private fun AddressLookupMapSuggestion(
    index: Int,
    address: AddressLookupMapAddress,
    query: String,
    slot: Int,
    open: Boolean,
    picked: Boolean,
    user: Boolean,
    ctl: AddressLookupMapController,
    g: AddressLookupMapGeo,
    onPress: () -> Unit,
) {
    LaunchedEffect(user, slot, open, picked) {
        if (!user) return@LaunchedEffect
        val visible = (open && slot >= 0) || picked
        if (slot >= 0) ctl.rowY[index].to(slot.toFloat(), springOf(420, 0.78f))
        ctl.rowA[index].to(if (visible) 1f else 0f, timing(if (visible) 260 else 160))
    }
    val range = matchRange(address.street, query)
    val street: AnnotatedString = remember(address.street, range) {
        buildAnnotatedString {
            if (range == null) append(address.street)
            else {
                append(address.street.substring(0, range.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) { append(address.street.substring(range.first, range.last + 1)) }
                append(address.street.substring(range.last + 1))
            }
        }
    }
    val visible = open && slot >= 0
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.offset((g.margin + 4).dp, (g.listTop + 4).dp).size((g.width - 8).dp, ROW_H.dp)
            .graphicsLayer {
                val p = ctl.pose
                val yy = p.rowY[index]
                val aa = clamp01(p.rowA[index])
                val mine = p.chosen == index
                val c = p.choose
                val fade = if (mine) 1 - seg(c, 0.55f, 0.9f) else 1 - seg(c, 0f, 0.45f)
                val rowTop = g.listTop + 4 + yy * ROW_H
                val fly = if (mine) -c * (rowTop - g.fieldTop + (ROW_H - g.fieldH) / 2) else 0f
                alpha = aa * fade * p.list
                translationY = (yy * ROW_H + 10 * (1 - aa) + fly) * density
                val s = if (mine) 1 - 0.04f * c else 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) Color(0x0D282218) else Color.Transparent)
            .then(
                if (visible) Modifier.clickable(interaction, indication = null, role = Role.Button, onClickLabel = "${address.street}, ${address.area}") { onPress() }
                else Modifier,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).background(Color(0xFFF1EEE7), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(street, style = style(16f, color = Color(0xFF4E4A42), tracking = -0.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("${address.area} ${address.postcode}", style = style(13f, color = Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicText(distanceKm(address), style = style(13f, color = Muted).copy(fontFeatureSettings = "tnum"))
    }
}

/** "Front door" after the pin lands; "Side gate · Moved 9 m" after the map was nudged under it. */
@Composable
private fun AddressLookupMapCallouts(ctl: AddressLookupMapController, w: Float, h: Float) {
    val top = h / 2 - 50 - CALLOUT_H - 10
    for (b in listOf(false, true)) {
        Box(
            Modifier.offset((w / 2 - CALLOUT_W / 2).dp, top.dp).size(CALLOUT_W.dp, CALLOUT_H.dp)
                .graphicsLayer {
                    val v = if (b) ctl.pose.callB else ctl.pose.callA
                    alpha = v
                    translationY = 6 * (1 - v) * density
                    scaleX = 0.94f + 0.06f * v
                    scaleY = 0.94f + 0.06f * v
                    clip = false
                },
        ) {
            Column(
                Modifier.fillMaxSize()
                    .cssBoxShadow(Color(0x24281E12), 1.dp, 2.dp, 12.dp)
                    .cssBoxShadow(Color(0x24281E12), 8.dp, 18.dp, 12.dp)
                    .background(Card, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(if (b) "Side gate" else "Front door", style = style(14f, FontWeight.SemiBold, tracking = -0.2f))
                BasicText(
                    if (b) "Moved 9 m" else "Drop-off point",
                    style = style(11.5f, color = Muted).let { if (b) it.copy(fontFeatureSettings = "tnum") else it },
                )
            }
            // The tail: a square turned 45°, 5 dp below the card's bottom edge.
            Box(
                Modifier.offset((CALLOUT_W / 2 - 6).dp, (CALLOUT_H - 12 + 5).dp).size(12.dp)
                    .graphicsLayer { rotationZ = 45f }
                    .background(Card, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** A detail field: label on top, value under it, filled in by the lookup with a short rise. */
@Composable
private fun RowScope.AddressLookupMapDetailBox(
    label: String,
    value: String,
    ctl: AddressLookupMapController,
    index: Int,
    h: Float,
    flex: Float,
    content: @Composable () -> Unit,
) {
    val empty = value.isEmpty()
    Box(
        Modifier.weight(flex).height(h.dp)
            .cssBoxShadow(Color(0x0F281E12), 1.dp, 2.dp, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(0.5.dp, Hairline, RoundedCornerShape(14.dp)),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (empty) 0f else 0.9f * sin(PI.toFloat() * ctl.pose.fill(index)) }.background(Color(0x14E0562E)))
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
            BasicText(label, Modifier.padding(bottom = 1.dp), style = style(12f, FontWeight.Medium, Muted))
            Box(
                Modifier.graphicsLayer {
                    val p = ctl.pose
                    val f = p.fill(index)
                    if (empty) {
                        alpha = p.placeholder * (1 - f)
                        translationY = 0f
                    } else {
                        alpha = f
                        translationY = 7 * (1 - f) * density
                    }
                },
            ) { content() }
        }
    }
}

@Composable
private fun AddressLookupMapDetailInput(
    value: String,
    placeholder: String,
    tabular: Boolean,
    number: Boolean,
    onChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    var was by remember { mutableStateOf(false) }
    val base = style(16f, tracking = -0.2f).let { if (tabular) it.copy(fontFeatureSettings = "tnum") else it }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = base,
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = if (number) KeyboardType.NumberPassword else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth().height(22.dp)
            .onFocusChanged { if (it.isFocused != was) { was = it.isFocused; onFocus(it.isFocused) } },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) BasicText(placeholder, style = base.copy(color = PlaceholderInk), maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun AddressLookupMapButton(g: AddressLookupMapGeo, ctl: AddressLookupMapController, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed) { press.animateTo(if (pressed) 1f else 0f, tween(if (pressed) 100 else 160, easing = EaseInOutQuad)) }
    Box(
        Modifier.offset(g.margin.dp, g.buttonTop.dp).size(g.width.dp, g.buttonH.dp)
            .graphicsLayer { val s = 1 - 0.03f * press.value; scaleX = s; scaleY = s }
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Continue to payment") { onPress() },
    ) {
        Box(
            Modifier.fillMaxSize()
                .cssBoxShadow(Color(0x59963214), 1.dp, 2.dp, 27.dp) { ctl.pose.ready }
                .cssBoxShadow(Color(0x42963214), 10.dp, 24.dp, 27.dp) { ctl.pose.ready },
        )
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(27.dp)).background(Color(0xFFE4DFD5)), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = ctl.pose.ready }
                    .background(Brush.verticalGradient(0f to Color(0xFFEC6A40), 0.5f to Accent, 1f to Color(0xFFC9461F))),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x47FFFFFF)))
            }
            BasicText(
                "Continue to payment",
                style = style(17f, FontWeight.SemiBold, tracking = -0.2f),
                color = { lerp(Color(0xFFA29C90), Color(0xFFFFF7F1), ctl.pose.ready) },
            )
        }
    }
}

/** A soft touch dot showing what the loop's finger does. */
@Composable
private fun AddressLookupMapGhostFinger(ctl: AddressLookupMapController, g: AddressLookupMapGeo) {
    Box(
        Modifier.size(DOT_SIZE.dp)
            .graphicsLayer {
                val d = ctl.dot
                var x = g.margin + g.width * 0.62f
                var y = g.fieldTop + g.fieldH / 2
                if (d.target == 1) {
                    x = g.margin + g.width * 0.45f
                    y = g.listTop + 4 + ROW_H / 2
                } else if (d.target == 2) {
                    x = g.margin + g.width * 0.66f + d.dx
                    y = g.mapTop + g.mapH * 0.66f + d.dy
                }
                alpha = d.alpha
                translationX = (x - DOT_SIZE / 2) * density
                translationY = (y - DOT_SIZE / 2 + (1 - d.alpha) * 8) * density
                scaleX = d.scale
                scaleY = d.scale
            }
            .cssBoxShadow(Color(0x241E1A14), 4.dp, 12.dp, (DOT_SIZE / 2).dp)
            .background(Color(0x291E1A14), CircleShape)
            .border(1.dp, Color(0xBFFFFFFF), CircleShape),
    )
}
