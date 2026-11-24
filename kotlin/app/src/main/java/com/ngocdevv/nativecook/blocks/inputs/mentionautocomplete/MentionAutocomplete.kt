package com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.EMPTY_DOC
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.PEOPLE
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.activeQuery
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.applyText
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.insertMention
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.rank
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.runs
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.BREAK_LEN
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.CHIP_START
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.IDLE
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.LABEL
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.LOOP
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.MORPH_FROM
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.MORPH_LEN
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.POP_PAD
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.Pose
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.RETURN_LEN
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.ROW_H
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.SCRIPT
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.SELECT_LEN
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.SEND_MS
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.T_STILL
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.autoDot
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.autoPose
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.clamp01
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.easeOutBack
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.seg
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.stepAt
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Mention Autocomplete — the composer of Huddle, a team chat (a port of MentionAutocomplete.tsx).

 Typing "@" opens a popover of people that grows out of the caret; each letter filters it, with the
 matched letters in bold and the rows sliding to their new places on springs. Return or a tap turns
 the typed letters into a token chip that grows out of them, followed by a space for the next word.
 Backspace takes that space first; the next one on the chip selects it, and another breaks it back
 into plain text. Sending pushes the conversation up by the new message's height while the message
 rises out of the composer: one graphicsLayer translation of the whole thread, eased over 300 ms.

 The composer is a real BasicTextField whose text is drawn as runs through a VisualTransformation
 (plain text, and the chips' names in semibold accent); the chips' backgrounds sit behind it,
 placed by measuring the same runs with a TextMeasurer. The loop (MentionAutocompleteMotion) types
 into the value without focusing it, so no keyboard shows; a touch hands everything to the person,
 and two seconds after the last touch (keyboard down) it eases back in 0.7 s and the loop restarts.
 Reduced motion: still at t = 4.5 (the composed message with its chip).
*/

private val Accent = Color(0xFF2F63D6)
private val ChipBg = Color(0xFFE3EAFB)
private val ChipInk = Color(0xFF2452B9)
private val Ink = Color(0xFF1E1D1A)
private val Muted = Color(0xFF7A766D)
private val Page = Color(0xFFF5F4F0)
private val Bar = Color(0xFFFBFAF7)
private val Card = Color(0xFFFFFEFB)
private val Hairline = Color(0x1F1E1C18) // rgba(30,28,24,0.12)
private const val PILL_H = 44f
private const val PLUS = 36f
private const val TEXT_PAD = 14f
private const val SEND = 32f
private const val CHIP_PAD = 4f
private const val CHIP_H = 24f
private const val FONT = 16f
private const val DOT_SIZE = 44f
private val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private val Plain = PlatformTextStyle(includeFontPadding = false)
private fun style(size: Float, weight: FontWeight = FontWeight.Normal, color: Color = Ink, tracking: Float = 0f) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = tracking.sp, platformStyle = Plain)

private val InputStyle = style(FONT)
private val ChipSpan = SpanStyle(color = ChipInk, fontWeight = FontWeight.SemiBold)

private fun timing(ms: Int, easing: Easing = EaseInOutQuadPop): AnimationSpec<Float> = if (ms <= 0) snap() else tween(ms, easing = easing)

private class MentionAutocompleteMessage(val id: String, val author: MentionAutocompletePerson, val time: String, val doc: MentionAutocompleteDoc)

private val HISTORY = listOf(
    MentionAutocompleteMessage("h1", PEOPLE[0], "9:41", MentionAutocompleteDoc("Hero copy v3 is in the doc. The second line still feels long to me.", emptyList(), -1)),
    MentionAutocompleteMessage("h2", PEOPLE[1], "9:44", MentionAutocompleteDoc("Agreed. It wraps to three lines on the small phones.", emptyList(), -1)),
    MentionAutocompleteMessage("h3", PEOPLE[3], "9:46", MentionAutocompleteDoc("Who signs off on copy before Thursday?", emptyList(), -1)),
)
private val YOU = MentionAutocompletePerson("you", "Sam", "Ito", "You", listOf(0xFF8E95A3, 0xFF666D7C, 0xFF4A505E), true)

/** A value a person drives: an Animatable with one running job (a new write cancels the last). */
private class MentionAutocompleteVal(private val scope: CoroutineScope, init: Float) {
    val a = Animatable(init)
    private var job: Job? = null
    val v: Float get() = a.value

    fun snap(x: Float) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { a.snapTo(x) }
    }

    fun to(x: Float, spec: AnimationSpec<Float>, from: Float? = null) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (from != null) a.snapTo(from)
            a.animateTo(x, spec)
        }
    }
}

@Stable
private class MentionAutocompleteController(val scope: CoroutineScope) {
    var t by mutableFloatStateOf(0f)
    /** 0 = the loop, 1 = a person, 2 = easing back, 3 = resetting. */
    var mode = 0
    var user by mutableStateOf(false)
    var now = 0f
    var lastTouch = 0f
    var returnAt = 0f
    var editing = false
    var pose by mutableStateOf(autoPose(0f), referentialEqualityPolicy())
    var dot by mutableStateOf(MentionAutocompleteMotion.Dot(0, 0f, 1f), referentialEqualityPolicy())

    private fun v(x: Float) = MentionAutocompleteVal(scope, x)
    val focus = v(0f)
    val caret = v(0f)
    val placeholder = v(1f)
    val send = v(0f)
    val pop = v(0f)
    val count = v(0f)
    val morph = v(1f)
    val select = v(0f)
    val breakUp = v(0f)
    /** The thread's height as drawn (its translation), as laid out, and with only the history in it. */
    val thread = v(0f)
    private var threadFull = 0f
    private var threadRest = 0f

    /**
     The thread hangs from its top just under the composer's edge and is lifted by its own height, so a
     sent message is laid out out of sight and nothing in the layout moves: only this translation does.
    */
    fun threadLaidOut(h: Float, rest: Boolean, fast: Boolean) {
        val first = threadFull == 0f
        threadFull = h
        if (rest) threadRest = h
        if (first || fast) thread.snap(h) else if (mode != 2) thread.to(h, timing(SEND_MS, EaseOut))
    }

    /** A touch while easing back: the sent message comes back up. */
    fun threadBack(fast: Boolean) = thread.to(threadFull, timing(if (fast) 0 else SEND_MS, EaseOut))

    fun userPose() = Pose(focus.v, caret.v, placeholder.v, send.v, pop.v, count.v, EMPTY, EMPTY, morph.v, select.v, breakUp.v)

    fun writePose(p: Pose) {
        focus.snap(p.focus); caret.snap(0f); placeholder.snap(p.placeholder); send.snap(p.send)
        pop.snap(p.pop); count.snap(p.count); morph.snap(1f); select.snap(p.select); breakUp.snap(0f)
    }

    fun easeToRest(d: Int) {
        val o = timing(d, EaseOut)
        focus.to(0f, o); pop.to(0f, o); send.to(0f, o); placeholder.to(1f, o); select.to(0f, o)
        // A message the person sent slides back down into the composer; it is dropped once out of sight.
        if (threadRest > 0f) thread.to(threadRest, o)
    }

    companion object {
        val EMPTY = FloatArray(0)
    }
}

private class MentionAutocompleteGeo(val w: Float, val h: Float, val top: Float, bottom: Float) {
    val margin = max(12f, (w - 460f) / 2)
    val composerTop = h - max(bottom, 10f) - 8 - PILL_H
    val pillLeft = margin + PLUS + 8
    val pillW = w - margin - pillLeft
    val textLeft = pillLeft + TEXT_PAD
    val textW = pillW - TEXT_PAD - SEND - 12
    val popW = min(300f, w - 24)
    val headerH = 56f
    val popBottom = composerTop - 8
}

/** Positions measured from the composer's runs, in dp from the start of the text. */
private class MentionAutocompleteMeasured(val end: Float, val at: Float, val from: Float, val chips: List<FloatArray>, val broke: FloatArray)

private class MentionAutocompleteBroke(val prefix: String, val label: String)

private fun View.selectTick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
private fun View.lightTap() = performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

/** The doc's text with the chips' runs styled (semibold accent; white when selected; hidden while one is born). */
private fun styledDoc(doc: MentionAutocompleteDoc, hideChip: Int): AnnotatedString = buildAnnotatedString {
    for (r in runs(doc)) {
        if (r.chip < 0) append(r.text)
        else {
            val color = when {
                r.chip == hideChip -> Color.Transparent
                doc.selected == r.chip -> Color(0xFFFFFDF8)
                else -> ChipInk
            }
            withStyle(ChipSpan.copy(color = color)) { append(r.text) }
        }
    }
}

@Composable
fun MentionAutocomplete(
    modifier: Modifier = Modifier,
    /** Types, mentions and un-mentions someone every LOOP seconds until the screen is touched. */
    autoPlay: Boolean = true,
    /** The channel the composer posts to. */
    channel: String = "#launch-week",
    /** Called with the message's plain text and its mentions when it is sent. */
    onSend: ((String, List<MentionAutocompletePerson>) -> Unit)? = null,
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val still = autoPlay && reduced
    val scope = rememberCoroutineScope()
    val ctl = remember { MentionAutocompleteController(scope) }
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxSize().background(Page)) {
        val g = remember(maxWidth, maxHeight, bars) {
            MentionAutocompleteGeo(maxWidth.value, maxHeight.value, bars.calculateTopPadding().value, bars.calculateBottomPadding().value)
        }

        // ---- Composition: keystrokes, picks, Backspace on chips -------------------------------------
        val loopTime by remember(still) { derivedStateOf { if (still) T_STILL else ctl.t } }
        val step by remember { derivedStateOf { stepAt(loopTime) } }
        var takeT by remember { mutableFloatStateOf(0f) }
        var uDoc by remember { mutableStateOf(EMPTY_DOC) }
        var uValue by remember { mutableStateOf(TextFieldValue("")) }
        var uMode by remember { mutableStateOf(MentionAutocompleteChipMode.None) }
        var uActive by remember { mutableIntStateOf(0) }
        var uQuery by remember { mutableStateOf<MentionAutocompleteData.Query?>(null) }
        var uFrom by remember { mutableStateOf(MORPH_FROM) }
        var uBroke by remember { mutableStateOf(MentionAutocompleteBroke("", "")) }
        val messages = remember { mutableStateListOf<MentionAutocompleteMessage>().also { it.addAll(HISTORY) } }
        val jobs = remember { mutableListOf<Job>() }
        val imeDp = remember { mutableFloatStateOf(0f) }

        val user = ctl.user
        val s = SCRIPT[step]
        val doc = if (user) uDoc else s.doc
        val chipMode = if (user) uMode else s.chip
        val activeChip = if (user) uActive else 0
        val query = if (user) uQuery else s.query?.let { MentionAutocompleteData.Query(s.doc.text.lastIndexOf('@'), it) }
        val morphFrom = if (user) uFrom else MORPH_FROM
        val broke = if (user) uBroke else MentionAutocompleteBroke(s.doc.text.take(CHIP_START), LABEL)
        val open = query != null && rank(query.query).isNotEmpty()
        val slots = remember(query?.query, query == null) {
            val l = if (query == null) emptyList() else rank(query.query)
            IntArray(PEOPLE.size) { l.indexOf(it) }
        }
        val fieldValue = if (user) uValue else TextFieldValue(s.doc.text, TextRange(s.doc.text.length))

        // ---- Measuring the runs (hidden copies laid out with the same type) -------------------------
        val measured = remember(doc, query?.at, morphFrom, broke.prefix, broke.label, density) {
            val d = density.density
            val full = measurer.measure(styledDoc(doc, -1) + AnnotatedString("|"), InputStyle)
            fun xAt(i: Int) = full.getBoundingBox(i.coerceIn(0, doc.text.length)).left / d
            val chips = doc.chips.map { c ->
                val w = measurer.measure(AnnotatedString(doc.text.substring(c.start, c.start + c.len), listOf(AnnotatedString.Range(ChipSpan, 0, c.len))), InputStyle).size.width / d
                floatArrayOf(xAt(c.start), w)
            }
            val from = measurer.measure(AnnotatedString(morphFrom), InputStyle).size.width / d
            val bx = measurer.measure(AnnotatedString(broke.prefix + "|"), InputStyle).getBoundingBox(broke.prefix.length).left / d
            val bw = measurer.measure(AnnotatedString(broke.label, listOf(AnnotatedString.Range(ChipSpan, 0, broke.label.length))), InputStyle).size.width / d
            MentionAutocompleteMeasured(xAt(doc.text.length), if ((query?.at ?: -1) >= 0) xAt(query!!.at) else 0f, from, chips, floatArrayOf(bx, bw))
        }

        fun resetToAuto() {
            jobs.forEach { it.cancel() }
            jobs.clear()
            ctl.user = false
            uDoc = EMPTY_DOC
            uValue = TextFieldValue("")
            uMode = MentionAutocompleteChipMode.None
            uQuery = null
            messages.clear()
            messages.addAll(HISTORY)
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
                    1 -> if (!ctl.editing && ctl.now - ctl.lastTouch > IDLE) {
                        ctl.mode = 2
                        ctl.returnAt = ctl.now
                        ctl.easeToRest(if (reduced) 0 else (RETURN_LEN * 1000).toInt())
                    }
                    2 -> if (ctl.now - ctl.returnAt > RETURN_LEN) {
                        ctl.mode = 3
                        resetToAuto()
                    }
                }
                ctl.pose = if (ctl.mode == 0) autoPose(if (still) T_STILL else ctl.t) else ctl.userPose()
                ctl.dot = if (ctl.mode == 0 && auto) autoDot(ctl.t) else MentionAutocompleteMotion.Dot(0, 0f, 1f)
            }
        }

        // ---- Hand-over -----------------------------------------------------------------------------
        fun takeOver() {
            when (ctl.mode) {
                0 -> {
                    val t = if (still) T_STILL else if (auto) ctl.t else 0f
                    val st = SCRIPT[stepAt(t)]
                    uMode = when (st.chip) {
                        MentionAutocompleteChipMode.Morph -> MentionAutocompleteChipMode.Chip
                        MentionAutocompleteChipMode.Break -> MentionAutocompleteChipMode.None
                        else -> st.chip
                    }
                    // Taken over with the caret right after a chip (the film's Backspace had taken its space): give
                    // the space back, so what the person types next never runs into the name.
                    val last = st.doc.chips.lastOrNull()
                    val doc = if (uMode == MentionAutocompleteChipMode.Chip && last != null && last.start + last.len == st.doc.text.length) {
                        st.doc.copy(text = st.doc.text + " ")
                    } else st.doc
                    uDoc = doc
                    uValue = TextFieldValue(doc.text, TextRange(doc.text.length))
                    uActive = 0
                    uQuery = st.query?.let { MentionAutocompleteData.Query(st.doc.text.lastIndexOf('@'), it) }
                    takeT = t
                    ctl.user = true
                    ctl.writePose(autoPose(t))
                    ctl.mode = 1
                    ctl.pose = ctl.userPose()
                }
                2 -> {
                    ctl.mode = 1
                    ctl.threadBack(reduced)
                }
            }
            ctl.lastTouch = ctl.now
        }

        // ---- The person's typing -------------------------------------------------------------------
        fun popUI(show: Boolean, n: Int, hasText: Boolean, fast: Boolean) {
            val k = if (fast) 0 else 1
            ctl.pop.to(if (show) 1f else 0f, springMs(if (show) 380 * k else 220 * k, if (show) 0.62f else 1f))
            if (show) ctl.count.to(n.toFloat(), springMs(380 * k, 0.8f))
            ctl.send.to(if (hasText) 1f else 0f, timing(180 * k))
            ctl.placeholder.to(if (hasText) 0f else 1f, timing(120 * k))
            ctl.select.to(0f, timing(120 * k))
            ctl.lastTouch = ctl.now
        }

        fun chipUI(kind: MentionAutocompleteChipMode, fast: Boolean) {
            val k = if (fast) 0f else 1f
            when (kind) {
                MentionAutocompleteChipMode.Morph -> {
                    ctl.morph.to(1f, timing((MORPH_LEN * 1000 * k).toInt(), LinearEasing), from = 0f)
                    ctl.pop.to(0f, timing((220 * k).toInt(), EaseOut))
                    ctl.select.snap(0f)
                }
                MentionAutocompleteChipMode.Selected -> ctl.select.to(1f, timing((SELECT_LEN * 1000 * k).toInt(), EaseOut))
                else -> {
                    ctl.breakUp.to(1f, timing((BREAK_LEN * 1000 * k).toInt(), LinearEasing), from = 0f)
                    ctl.select.to(0f, timing((BREAK_LEN * 1000 * k).toInt()))
                }
            }
            ctl.lastTouch = ctl.now
        }

        fun later(ms: Long, fn: () -> Unit) {
            jobs.add(scope.launch { delay(if (reduced) 0 else ms); fn() })
        }

        fun onValueChange(v: TextFieldValue) {
            val base = doc
            val sel = fieldValue.selection
            val modeNow = if (user) uMode else when (s.chip) {
                MentionAutocompleteChipMode.Morph -> MentionAutocompleteChipMode.Chip
                MentionAutocompleteChipMode.Break -> MentionAutocompleteChipMode.None
                else -> s.chip
            }
            takeOver()
            if (v.text == base.text) {
                uValue = v
                return
            }
            when (val e = applyText(base, v.text)) {
                is MentionAutocompleteData.Edit.Select -> {
                    uDoc = base.copy(selected = e.chip)
                    uValue = TextFieldValue(base.text, sel)
                    uMode = MentionAutocompleteChipMode.Selected
                    uActive = e.chip
                    view.selectTick()
                    chipUI(MentionAutocompleteChipMode.Selected, reduced)
                }
                is MentionAutocompleteData.Edit.Break -> {
                    val c = base.chips[e.chip]
                    uBroke = MentionAutocompleteBroke(base.text.substring(0, c.start), base.text.substring(c.start, c.start + c.len))
                    uDoc = e.doc
                    uValue = TextFieldValue(base.text, sel)
                    uMode = MentionAutocompleteChipMode.Break
                    view.lightTap()
                    chipUI(MentionAutocompleteChipMode.Break, reduced)
                    later((BREAK_LEN * 1000 + 40).toLong()) {
                        if (uMode == MentionAutocompleteChipMode.Break) uMode = MentionAutocompleteChipMode.Chip
                    }
                }
                is MentionAutocompleteData.Edit.Text -> {
                    uDoc = e.doc
                    uValue = v
                    if (modeNow == MentionAutocompleteChipMode.Selected) uMode = MentionAutocompleteChipMode.Chip
                    val q = activeQuery(e.doc, e.caret)
                    uQuery = q
                    val n = if (q == null) 0 else rank(q.query).size
                    popUI(n > 0, n, e.doc.text.isNotEmpty(), reduced)
                }
            }
        }

        fun pick(person: Int) {
            val base = doc
            val q = query
            takeOver()
            if (q == null) return
            val r = insertMention(base, q.at, q.at + 1 + q.query.length, person)
            uFrom = "@${q.query}"
            uDoc = r.doc
            uValue = TextFieldValue(r.doc.text, TextRange(r.caret))
            uActive = r.chip
            uMode = MentionAutocompleteChipMode.Morph
            uQuery = null
            view.selectTick()
            chipUI(MentionAutocompleteChipMode.Morph, reduced)
            later((MORPH_LEN * 1000 + 60).toLong()) {
                if (uMode == MentionAutocompleteChipMode.Morph) uMode = MentionAutocompleteChipMode.Chip
            }
        }

        fun send() {
            val base = doc
            takeOver()
            if (base.text.trim().isEmpty()) return
            messages.add(MentionAutocompleteMessage("m${System.nanoTime()}", YOU, "now", base))
            onSend?.invoke(base.text.trim(), base.chips.map { PEOPLE[it.person] })
            uDoc = EMPTY_DOC
            uValue = TextFieldValue("")
            uMode = MentionAutocompleteChipMode.None
            uQuery = null
            view.lightTap()
            popUI(false, 0, false, reduced)
        }

        fun onSubmit() {
            val q = query
            if (q != null) {
                rank(q.query).firstOrNull()?.let { pick(it) }
                return
            }
            send()
        }

        // ---- Layout from measurement ---------------------------------------------------------------
        val atX = measured.at
        val popLeft = min(max(g.textLeft + atX - 26, g.margin), g.w - g.margin - g.popW)
        val anchor = g.textLeft + atX + 5 - popLeft
        val hideChip = if (chipMode == MentionAutocompleteChipMode.Morph) activeChip else -1
        val transformation = remember(doc, hideChip) {
            val styled = styledDoc(doc, hideChip)
            VisualTransformation { text ->
                TransformedText(if (text.text == doc.text) styled else text, OffsetMapping.Identity)
            }
        }

        ImeHeight { imeDp.floatValue = it }

        // ---- The screen ----------------------------------------------------------------------------
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // The conversation and composer ride up with the keyboard.
                translationY = -max(0f, imeDp.floatValue - max(bars.calculateBottomPadding().value, 10f)) * density.density
            },
        ) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboard?.hide() } })

            // Conversation, above the composer: the thread hangs from the clip's bottom edge, lifted by its own
            // height, so a sent message is laid out out of sight and rises into place with the translation.
            val clipH = g.composerTop - 4
            Box(
                Modifier.padding(start = (g.margin + 4).dp, end = (g.margin + 4).dp).fillMaxWidth().height(clipH.dp).clipToBounds(),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { ctl.threadLaidOut(it.height / density.density, messages.size == HISTORY.size, reduced) }
                        .graphicsLayer { translationY = (clipH - ctl.thread.v) * density.density }
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).height(0.5.dp).background(Hairline))
                        BasicText("Today", style = style(12f, FontWeight.Medium, Muted))
                        Box(Modifier.weight(1f).height(0.5.dp).background(Hairline))
                    }
                    for (m in messages) key(m.id) { MentionAutocompleteMessageRow(m) }
                }
            }

            // Composer: the + menu, the pill with its text, chips and send.
            MentionAutocompleteAttach(
                Modifier.offset(g.margin.dp, (g.composerTop + (PILL_H - PLUS) / 2).dp),
                onTouch = ::takeOver,
                onPick = { view.lightTap() },
            )
            Box(Modifier.offset(g.pillLeft.dp, g.composerTop.dp).size(g.pillW.dp, PILL_H.dp)) {
                Box(
                    Modifier.offset((-4).dp, (-4).dp).size((g.pillW + 8).dp, (PILL_H + 8).dp)
                        .graphicsLayer { alpha = ctl.pose.focus }
                        .background(Color(0x1A2F63D6), RoundedCornerShape((PILL_H / 2 + 4).dp)),
                )
                Box(
                    Modifier.fillMaxSize()
                        .cssBoxShadow(Color(0x141E1A14), 1.dp, 2.dp, (PILL_H / 2).dp)
                        .cssBoxShadow(Color(0x121E1A14), 8.dp, 20.dp, (PILL_H / 2).dp)
                        .background(Card, RoundedCornerShape((PILL_H / 2).dp))
                        .drawBehind {
                            val w = 1.dp.toPx()
                            drawRoundRect(
                                lerp(Hairline, Color(0x732F63D6), ctl.pose.focus), topLeft = Offset(w / 2, w / 2),
                                size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(size.height / 2 - w / 2), style = Stroke(w),
                            )
                        },
                ) {
                    Box(Modifier.offset(TEXT_PAD.dp, 0.dp).size(g.textW.dp, PILL_H.dp).clipToBounds()) {
                        MentionAutocompleteChipLayer(measured, doc, chipMode, activeChip, morphFrom, broke, ctl)
                        if (doc.text.isEmpty()) {
                            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ctl.pose.placeholder }, contentAlignment = Alignment.CenterStart) {
                                BasicText("Message $channel", style = style(FONT, color = Color(0xFFA7A297)), maxLines = 1)
                            }
                        }
                        var wasFocused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = fieldValue,
                            onValueChange = ::onValueChange,
                            singleLine = true,
                            textStyle = InputStyle,
                            cursorBrush = SolidColor(Accent),
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                            modifier = Modifier.fillMaxSize()
                                .onFocusChanged {
                                    if (it.isFocused != wasFocused) {
                                        wasFocused = it.isFocused
                                        if (it.isFocused) takeOver()
                                        ctl.editing = it.isFocused
                                        ctl.focus.to(if (it.isFocused) 1f else 0f, timing(200))
                                        ctl.lastTouch = ctl.now
                                    }
                                }
                                .semantics { contentDescription = "Message $channel" },
                            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
                        )
                        // The loop's caret (the text field draws its own while a person types).
                        Box(
                            Modifier.offset((measured.end + 1).dp, ((PILL_H - 22) / 2).dp).size(2.dp, 22.dp)
                                .graphicsLayer { alpha = ctl.pose.caret }
                                .background(Accent, RoundedCornerShape(1.dp)),
                        )
                    }
                    Box(
                        Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(SEND.dp)
                            .graphicsLayer { val v = ctl.pose.send; scaleX = 0.9f + 0.1f * v; scaleY = 0.9f + 0.1f * v }
                            .drawBehind { drawCircle(lerp(Color(0xFFE2DFD8), Accent, ctl.pose.send)) }
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Send") { send() }
                            .semantics { contentDescription = "Send" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color(0xFFFFFDF8), modifier = Modifier.size(15.dp))
                    }
                }
            }

            MentionAutocompletePopover(
                pose = { ctl.pose },
                query = query?.query ?: "",
                slots = slots,
                open = open,
                user = user,
                takeT = takeT,
                left = popLeft,
                bottom = g.popBottom,
                width = g.popW,
                anchor = anchor,
                channel = channel,
                accent = Accent,
                onPick = ::pick,
            )
        }

        MentionAutocompleteHeader(g, channel)
        MentionAutocompleteGhostFinger(ctl, g, popLeft)
    }
}

/** Reports the keyboard's height in dp; recomposes only itself while the keyboard moves. */
@Composable
private fun ImeHeight(onHeight: (Float) -> Unit) {
    val density = LocalDensity.current
    val px = WindowInsets.ime.getBottom(density)
    SideEffect { onHeight(px / density.density) }
}

// ---- Chips -----------------------------------------------------------------------------------------

/** Chip backgrounds behind the input, and the letters of the chip being born or broken. */
@Composable
private fun MentionAutocompleteChipLayer(
    measured: MentionAutocompleteMeasured,
    doc: MentionAutocompleteDoc,
    mode: MentionAutocompleteChipMode,
    active: Int,
    morphFrom: String,
    broke: MentionAutocompleteBroke,
    ctl: MentionAutocompleteController,
) {
    doc.chips.forEachIndexed { i, c ->
        val m = measured.chips.getOrNull(i) ?: return@forEachIndexed
        key(c.start, c.person) {
            if (i == active && (mode == MentionAutocompleteChipMode.Morph || mode == MentionAutocompleteChipMode.Selected || mode == MentionAutocompleteChipMode.Chip)) {
                MentionAutocompleteLiveChip(m[0], m[1], measured.from, doc.text.substring(c.start, c.start + c.len), morphFrom, mode == MentionAutocompleteChipMode.Morph, ctl)
            } else {
                Box(
                    Modifier.offset((m[0] - CHIP_PAD).dp, ((PILL_H - CHIP_H) / 2).dp).size((m[1] + CHIP_PAD * 2).dp, CHIP_H.dp)
                        .background(ChipBg, RoundedCornerShape(7.dp)),
                )
            }
        }
    }
    if (mode == MentionAutocompleteChipMode.Break) MentionAutocompleteBreakingChip(measured.broke[0], measured.broke[1], broke.label, ctl)
}

/** The chip that grows out of the typed letters (morph), sits (chip), or is selected by Backspace. */
@Composable
private fun MentionAutocompleteLiveChip(x: Float, w: Float, fromW: Float, label: String, from: String, morphing: Boolean, ctl: MentionAutocompleteController) {
    fun m() = if (morphing) ctl.pose.morph else 1f
    fun pad() = CHIP_PAD * clamp01(m() * 3)
    Box(
        Modifier
            .offset { IntOffset(((x - pad()) * density).roundToInt(), (((PILL_H - CHIP_H) / 2) * density).roundToInt()) }
            .layout { measurable, _ ->
                val mm = m()
                val width = (fromW + (w - fromW) * easeOutBack(mm) + pad() * 2) * density
                val pl = measurable.measure(Constraints.fixed(width.roundToInt().coerceAtLeast(0), (CHIP_H * density).roundToInt()))
                layout(pl.width, pl.height) { pl.place(0, 0) }
            }
            .graphicsLayer {
                val p = ctl.pose
                val mm = m()
                alpha = if (morphing) seg(mm, 0f, 0.25f) * 0.6f + 0.4f * clamp01(mm * 4) else 1f
                val s = 1 + 0.04f * kotlin.math.sin(Math.PI.toFloat() * seg(p.select, 0f, 1f)) * (1 - p.select)
                scaleX = s
                scaleY = s
            }
            .drawBehind { drawRoundRect(lerp(ChipBg, Accent, ctl.pose.select), cornerRadius = CornerRadius(7.dp.toPx())) }
            .clip(RoundedCornerShape(7.dp)),
    ) {
        Box(
            Modifier.offset { IntOffset((pad() * density).roundToInt(), 0) }.fillMaxHeight()
                .wrapContentWidth(Alignment.Start, unbounded = true),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                from,
                Modifier.graphicsLayer {
                    val mm = m()
                    alpha = if (morphing) 1 - seg(mm, 0.08f, 0.45f) else 0f
                    translationY = -2 * seg(mm, 0f, 0.45f) * density
                },
                style = InputStyle, maxLines = 1, softWrap = false,
            )
            BasicText(
                label,
                Modifier.graphicsLayer {
                    val mm = m()
                    alpha = if (morphing) seg(mm, 0.12f, 0.55f) else 0f
                    translationY = 3 * (1 - seg(mm, 0.12f, 0.6f)) * density
                },
                style = InputStyle.copy(color = ChipInk, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false,
            )
        }
    }
}

/** A selected chip coming apart: its background dissolves and its name relaxes into plain text. */
@Composable
private fun MentionAutocompleteBreakingChip(x: Float, w: Float, label: String, ctl: MentionAutocompleteController) {
    Box(
        Modifier.offset((x - CHIP_PAD).dp, ((PILL_H - CHIP_H) / 2).dp).size((w + CHIP_PAD * 2).dp, CHIP_H.dp)
            .graphicsLayer {
                val b = ctl.pose.breakUp
                alpha = 1 - seg(b, 0.05f, 0.75f)
                scaleX = 1 + 0.06f * b
                scaleY = 1 + 0.14f * b
            }
            .drawBehind { drawRoundRect(lerp(Accent, ChipBg, seg(ctl.pose.breakUp, 0f, 0.5f)), cornerRadius = CornerRadius(7.dp.toPx())) },
    )
    Box(
        Modifier.offset(x.dp, 0.dp).height(PILL_H.dp).wrapContentWidth(Alignment.Start, unbounded = true),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            label,
            Modifier.graphicsLayer {
                val b = ctl.pose.breakUp
                alpha = 1 - seg(b, 0.1f, 0.65f)
                translationY = 2 * b * density
            },
            style = InputStyle.copy(fontWeight = FontWeight.SemiBold),
            color = { lerp(Color(0xFFFFFDF8), ChipInk, seg(ctl.pose.breakUp, 0f, 0.4f)) },
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---- Conversation and chrome ------------------------------------------------------------------------

@Composable
private fun MentionAutocompleteMessageRow(message: MentionAutocompleteMessage) {
    val a = message.author
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        MentionAutocompleteAvatar(a.initials, a.colors, 36f, online = a.online, ring = Page)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BasicText(if (a === YOU) "You" else a.fullName, Modifier.alignByBaseline(), style = style(15f, FontWeight.SemiBold, tracking = -0.2f))
                BasicText(message.time, Modifier.alignByBaseline(), style = style(12f, color = Muted).copy(fontFeatureSettings = "tnum"))
            }
            val text = remember(message.doc) {
                buildAnnotatedString {
                    for (r in runs(message.doc)) {
                        if (r.chip < 0) append(r.text)
                        else withStyle(SpanStyle(color = ChipInk, fontWeight = FontWeight.SemiBold, background = ChipBg)) { append(r.text) }
                    }
                }
            }
            BasicText(text, style = style(16f, color = Color(0xFF34312C), tracking = -0.15f).copy(lineHeight = 21.sp))
        }
    }
}

@Composable
private fun MentionAutocompleteHeader(g: MentionAutocompleteGeo, channel: String) {
    Box(
        Modifier.fillMaxWidth().height((g.top + g.headerH).dp).background(Bar)
            .drawBehind { drawRect(Hairline, topLeft = Offset(0f, size.height - 0.5.dp.toPx()), size = Size(size.width, 0.5.dp.toPx())) }
            .padding(top = g.top.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = (g.margin + 2).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(channel, style = style(17f, FontWeight.SemiBold, tracking = -0.3f), maxLines = 1)
                BasicText("14 members · 5 online", style = style(12.5f, color = Muted), maxLines = 1)
            }
            // Three overlapping avatars, the first on top.
            val stack = listOf(PEOPLE[2], PEOPLE[0], PEOPLE[1])
            Box(Modifier.size((30 + 21 * 2).dp, 30.dp)) {
                for (i in stack.indices.reversed()) {
                    val p = stack[i]
                    Box(
                        Modifier.offset((i * 21).dp, 0.dp).size(30.dp).background(Bar, CircleShape).border(2.dp, Bar, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        MentionAutocompleteAvatar(p.initials, p.colors, 26f)
                    }
                }
            }
        }
    }
}

/** The + button: a Material menu of attachments (the iOS version is a SwiftUI Menu). */
@Composable
private fun MentionAutocompleteAttach(modifier: Modifier, onTouch: () -> Unit, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.size(PLUS.dp)
                .cssBoxShadow(Color(0x141E1A14), 1.dp, 2.dp, (PLUS / 2).dp)
                .background(Card, CircleShape)
                .border(0.5.dp, Hairline, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Attach") { onTouch(); open = true }
                .semantics { contentDescription = "Attach" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false; onTouch() }) {
            for ((label, icon) in listOf("Photo" to Icons.Filled.Photo, "File" to Icons.Filled.Description, "Poll" to Icons.Filled.BarChart)) {
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = { open = false; onTouch(); onPick(label.lowercase()) },
                )
            }
        }
    }
}

@Composable
private fun MentionAutocompleteGhostFinger(ctl: MentionAutocompleteController, g: MentionAutocompleteGeo, popLeft: Float) {
    Box(
        Modifier.size(DOT_SIZE.dp)
            .graphicsLayer {
                val d = ctl.dot
                var x = g.pillLeft + g.pillW * 0.42f
                var y = g.composerTop + PILL_H / 2
                if (d.target == 1) {
                    val n = max(1f, ctl.pose.count)
                    x = popLeft + g.popW * 0.4f
                    y = g.popBottom - POP_PAD - (n - 1) * ROW_H - ROW_H / 2
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
