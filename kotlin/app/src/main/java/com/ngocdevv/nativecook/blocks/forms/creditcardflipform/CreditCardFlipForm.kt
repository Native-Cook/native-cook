package com.ngocdevv.nativecook.blocks.forms.creditcardflipform

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val BACKGROUND = Color(0xFF0D0E12)
private val TEXT = Color(0xFFF4F5F8)
private val MUTED = Color(0xFF8A8E9C)
private val FIELD_BG = Color(0xFF15161C)
private val FIELD_BORDER = Color(0xFF25272F)
private val PLACEHOLDER = Color(0xFF4B4E5A)
private val SUCCESS = Color(0xFF3CCF7E)
private val DANGER = Color(0xFFFF6369)
private val PAY_OFF = Color(0xFF1D1E25)
private val PAY_OFF_TEXT = Color(0xFF5D6170)

// Layout, in dp. Fields carry their label inside, above the value.
private const val SIDE = 24f
// With the keyboard up the page rises; before the big header would reach the top inset (the camera
// cutout), it gives way to a compact bar pinned under the inset, and the page slides under it.
private const val BAR_H = 44f // the compact bar's height under the top inset
private const val HEADER_ROOM = 14f // dp of rise over which the big header hands over to the bar
private const val CARD_MAX = 380f
private const val CARD_RATIO = 1.586f
private const val CARD_TOP = 84f
private const val FORM_GAP = 30f
private const val FIELD_H = 60f
private const val FIELD_GAP = 14f
private const val FIELD_RADIUS = 14f
private const val LABEL_TOP = 10f
private const val VALUE_TOP = 26f
private const val VALUE_H = 26f
private const val SAVE_GAP = 18f
private const val PAY_HEIGHT = 56f
// Dragging on the card tilts it towards the finger, up to TILT_MAX_Y° about its upright axis and
// TILT_MAX_X° about its horizontal one (TILT_PER_DP° per dp, eased into the limit); it springs flat
// on release with the finger's speed.
private const val TILT_PER_DP = 0.09
private const val TILT_MAX_Y = 12.0
private const val TILT_MAX_X = 9.0
private const val PERSPECTIVE = 1100f
// While it turns the card rises LIFT dp and grows by LIFT_SCALE.
private const val LIFT = 14f
private const val LIFT_SCALE = 0.05f

private const val PAID_HOLD = 1.8
// A field left by blur is let go after BLUR_GRACE ms, so moving between fields never drops the outline.
private const val BLUR_GRACE = 60L

// autoPlay (seconds): the card it types and when each step happens. LOOP is the whole period.
private const val LOOP = 14.2
private const val SCRIPT_NUMBER = "5412753488102697"
private const val SCRIPT_NAME = "JONAH VERRIER"
private const val SCRIPT_EXPIRY = "0829"
private const val SCRIPT_CVV = "417"
private val RHYTHM = listOf(1.0, 0.8, 1.25, 0.9, 1.1, 0.75, 1.2, 1.0)
// After a touch, autoPlay starts over this long after the form is left alone.
private const val RESUME_AFTER_MS = 4000L

private sealed interface Act {
    data class Focus(val field: Field?) : Act
    data class Type(val field: Field, val text: String) : Act
    data object Press : Act
    data object Pay : Act
}

private fun scriptSteps(): List<Pair<Double, Act>> {
    val steps = mutableListOf<Pair<Double, Act>>(0.6 to Act.Focus(Field.Number))
    fun typing(field: Field, text: String, from: Double, key: Double) {
        var at = from
        for (i in 1..text.length) {
            steps.add(at to Act.Type(field, text.take(i)))
            at += key * RHYTHM[i % RHYTHM.size]
        }
    }
    typing(Field.Number, SCRIPT_NUMBER, 0.95, 0.15)
    steps.add(3.55 to Act.Focus(Field.Name))
    typing(Field.Name, SCRIPT_NAME, 3.8, 0.085)
    steps.add(5.15 to Act.Focus(Field.Expiry))
    typing(Field.Expiry, SCRIPT_EXPIRY, 5.4, 0.2)
    steps.add(6.4 to Act.Focus(Field.Cvv))
    typing(Field.Cvv, SCRIPT_CVV, 6.95, 0.24)
    steps.add(7.9 to Act.Focus(null))
    steps.add(8.22 to Act.Press)
    steps.add(8.4 to Act.Pay)
    return steps
}

private fun digitsOnly(text: String) = text.filter { it in '0'..'9' }
private fun groupNumber(digits: String) = digits.chunked(4).joinToString(" ")
private fun showExpiry(digits: String) = if (digits.length > 2) "${digits.take(2)}/${digits.drop(2)}" else digits

/** Month first: a first digit above 1 is a single-digit month, and the month never passes 12. */
private fun cleanExpiry(text: String): String {
    var digits = digitsOnly(text).take(4)
    if (digits.isNotEmpty() && digits[0] > '1') digits = "0$digits".take(4)
    if (digits.length >= 2 && (digits.take(2).toInt() > 12 || digits.take(2) == "00")) digits = digits.take(1)
    return digits
}

/** A full MMYY whose month has already ended. */
private fun isExpired(digits: String): Boolean {
    if (digits.length != 4) return false
    val month = digits.take(2).toIntOrNull() ?: return false
    val year = 2000 + (digits.drop(2).toIntOrNull() ?: return false)
    val now = java.util.Calendar.getInstance()
    val thisYear = now.get(java.util.Calendar.YEAR)
    return year < thisYear || (year == thisYear && month < now.get(java.util.Calendar.MONTH) + 1)
}

/** Eases [v] into ±[limit]: linear near zero, never past the limit. */
private fun soft(v: Double, limit: Double) = limit * kotlin.math.tanh(v / limit)

/** Like JS's `\b\w` → upper case: a letter that starts a word is capitalised, the rest lowered. */
private fun titleCase(name: String): String {
    val out = StringBuilder()
    var inWord = false
    for (c in name.lowercase()) {
        val word = c.isLetterOrDigit() || c == '_'
        out.append(if (word && !inWord) c.uppercaseChar() else c)
        inWord = word
    }
    return out.toString()
}

/**
 * A payment form under a live preview of the card. Digits roll onto the card like slot reels as
 * the number is typed, the card's colours spread out from its mark once the first digit tells the
 * network, the name and expiry fill in under a frame of light that glides to whichever part is
 * being edited (a ring on the form glides with it), and the CVV turns the card over. The card tilts
 * under a finger and its light slides across the brushed surface. Pay folds the button into a
 * spinner, then a check.
 *
 * @param autoPlay types a whole card, turns it over for the CVV, pays and starts over.
 */
@Composable
fun CreditCardFlipForm(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    amount: String = "€248.00",
    issuer: String = "Halcyon",
    onPay: ((number: String, name: String, expiry: String, cvv: String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val still = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pay by rememberUpdatedState(onPay)
    val engine = remember { CardEngine() }
    val requesters = remember { Field.entries.associateWith { FocusRequester() } }

    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameTyped by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }
    var field by remember { mutableStateOf<Field?>(null) }
    // The name and the expiry on the card, measured (dp) so the outline hugs them.
    var nameBox by remember { mutableStateOf(Size(96f, 34f)) }
    var expiryBox by remember { mutableStateOf(Size(52f, 34f)) }
    var fieldRects by remember { mutableStateOf<Map<Field, Rect>>(emptyMap()) }
    var stage by remember { mutableStateOf(Stage.Idle) }
    var playing by remember { mutableStateOf(autoPlay) }
    var scriptPress by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var byUserPay by remember { mutableStateOf(false) }
    var blurJob by remember { mutableStateOf<Job?>(null) }
    var now by remember { mutableDoubleStateOf(nowSeconds()) }
    var wake by remember { mutableIntStateOf(0) }
    var rects by remember { mutableStateOf<Map<Field, Rect>>(emptyMap()) }
    // What the card lists (marks, letters), copied out of the engine so its parts compose again.
    var cast by remember { mutableStateOf(CardCast.of(engine, nowSeconds())) }
    fun recast(at: Double) {
        val next = CardCast.of(engine, at)
        if (next != cast) cast = next
    }

    val network = Network.of(number)
    val expired = isExpired(expiry)
    fun done(target: Field) = when (target) {
        Field.Number -> number.length == 16
        Field.Name -> name.trim().length > 1
        Field.Expiry -> expiry.length == 4 && !isExpired(expiry)
        Field.Cvv -> cvv.length == 3
    }
    // Read through a function: the autoPlay loop outlives the composition it started in.
    fun isValid() = Field.entries.all { done(it) }
    val valid = isValid()

    // The clock ticks while something moves, then rests.
    LaunchedEffect(wake) {
        while (true) {
            withFrameNanos { now = it / 1e9 }
            engine.prune(now)
            recast(now)
            if (!engine.moving(now)) break
        }
    }
    fun wakeUp() {
        recast(nowSeconds())
        wake++
    }

    fun haptic(kind: Int) {
        view.performHapticFeedback(kind)
    }

    // The script's focus is only make-believe (no keyboard), so it is dropped with the script.
    fun takeOver() {
        if (!playing) return
        playing = false
        if (field != null) {
            field = null
            engine.focus(null, rects, fieldRects, nowSeconds(), still)
            wakeUp()
        }
    }

    /** What each field's text becomes when typed, by hand or by the script. */
    fun edit(target: Field, text: String, byUser: Boolean) {
        if (stage != Stage.Idle && byUser) return
        val at = nowSeconds()
        when (target) {
            Field.Number -> {
                val digits = digitsOnly(text).take(16)
                if (byUser && Network.of(digits) != Network.of(number) && digits.isNotEmpty()) haptic(HapticFeedbackConstants.CLOCK_TICK)
                // Typed by hand, a number that just became complete moves the keyboard on to the name.
                val advance = byUser && digits.length == 16 && number.length < 16
                if (digits != number) {
                    number = digits
                    engine.showNumber(digits, at, still)
                    engine.turnTo(Network.of(digits), at, still)
                }
                if (advance) requesters.getValue(Field.Name).requestFocus()
            }
            Field.Name -> {
                // The field keeps the case it was typed in and only shows it upper case, so the
                // keyboard's own text never disagrees with the field's (which would drop keys).
                val typed = text.filter { it.uppercaseChar() in 'A'..'Z' || it == '\'' || it == ' ' || it == '-' }.take(20)
                val clean = typed.uppercase()
                nameTyped = typed
                if (clean != name) {
                    name = clean
                    engine.showName(clean, at)
                }
            }
            Field.Expiry -> {
                val clean = cleanExpiry(text)
                // Typed by hand, an expiry that just became complete (and good) moves on to the CVV.
                val advance = byUser && clean.length == 4 && expiry.length < 4 && !isExpired(clean)
                if (clean != expiry) {
                    expiry = clean
                    engine.showExpiry(clean, at, still)
                }
                if (advance) requesters.getValue(Field.Cvv).requestFocus()
            }
            Field.Cvv -> {
                val clean = digitsOnly(text).take(3)
                if (clean != cvv) {
                    cvv = clean
                    engine.showCvv(clean, at, still)
                }
            }
        }
        wakeUp()
    }

    fun setField(target: Field?) {
        if (target == field) return
        field = target
        engine.focus(target, rects, fieldRects, nowSeconds(), still)
        wakeUp()
    }

    fun setStage(next: Stage) {
        stage = next
        engine.pay(next, nowSeconds())
        wakeUp()
    }

    fun reset() {
        // A field focused while the payment ran gives its keyboard back with the emptied form.
        keyboard?.hide()
        focusManager.clearFocus()
        edit(Field.Number, "", false)
        edit(Field.Name, "", false)
        edit(Field.Expiry, "", false)
        edit(Field.Cvv, "", false)
        setField(null)
        setStage(Stage.Idle)
    }

    fun doPay(byUser: Boolean) {
        if (!isValid() || stage != Stage.Idle) return
        byUserPay = byUser
        if (byUser) {
            keyboard?.hide()
            focusManager.clearFocus()
            pay?.invoke(number, name, expiry, cvv)
        }
        setField(null)
        setStage(Stage.Paying)
    }

    // Paying: fold, spin, check, hold, then empty the form.
    LaunchedEffect(stage) {
        when (stage) {
            Stage.Idle -> Unit
            Stage.Paying -> {
                delay(((CardEngine.PAY_FOLD + CardEngine.PAY_DURATION) * 1000).toLong())
                setStage(Stage.Paid)
            }
            Stage.Paid -> {
                if (byUserPay) {
                    haptic(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                }
                engine.crossLight(nowSeconds(), still)
                wakeUp()
                delay(((PAID_HOLD + CardEngine.CHECK_DURATION) * 1000).toLong())
                reset()
            }
        }
    }

    // autoPlay: every step is timed from the start of its loop, so the period never drifts.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val steps = scriptSteps()
        var origin = System.nanoTime()
        while (true) {
            for ((at, act) in steps) {
                delay(((origin + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                when (act) {
                    is Act.Focus -> setField(act.field)
                    is Act.Type -> edit(act.field, act.text, false)
                    Act.Press -> scriptPress = true
                    Act.Pay -> {
                        scriptPress = false
                        doPay(false)
                    }
                }
            }
            delay(((origin + (LOOP * 1e9).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            origin += (LOOP * 1e9).toLong()
        }
    }
    // A touch takes over; once the form is left alone again the loop starts over from an empty form.
    LaunchedEffect(playing) { if (!playing) scriptPress = false }
    LaunchedEffect(valid) {
        engine.setReady(valid, nowSeconds())
        wakeUp()
    }
    LaunchedEffect(autoPlay, playing, field, stage, number, name, expiry, cvv, save) {
        if (autoPlay && !playing && field == null && stage == Stage.Idle) {
            delay(RESUME_AFTER_MS)
            reset()
            playing = true
        }
    }

    fun gotFocus(target: Field) {
        takeOver()
        blurJob?.cancel()
        if (target == Field.Cvv && field != Field.Cvv) haptic(HapticFeedbackConstants.CONTEXT_CLICK)
        setField(target)
    }

    fun lostFocus(target: Field) {
        blurJob = scope.launch {
            delay(BLUR_GRACE)
            if (field == target) setField(null)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BACKGROUND)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            },
    ) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value
        val top = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density) / density.density
        val bottom = WindowInsets.navigationBars.getBottom(density) / density.density
        val cardW = max(200f, min(screenW - SIDE * 2, CARD_MAX))
        val cardH = (cardW / CARD_RATIO).roundToInt().toFloat()
        val cardTop = top + CARD_TOP
        val formTop = cardTop + cardH + FORM_GAP
        val formW = screenW - SIDE * 2
        val half = (formW - 12) / 2
        val rowStep = FIELD_H + FIELD_GAP
        val formH = rowStep * 2 + FIELD_H
        // Each field's box on the form, from the form's top left.
        val fields = remember(formW) {
            mapOf(
                Field.Number to Rect(0f, 0f, formW, FIELD_H),
                Field.Name to Rect(0f, rowStep, formW, rowStep + FIELD_H),
                Field.Expiry to Rect(0f, rowStep * 2, half, rowStep * 2 + FIELD_H),
                Field.Cvv to Rect(half + 12, rowStep * 2, formW, rowStep * 2 + FIELD_H),
            )
        }
        val fieldBottom = fields.mapValues { formTop + it.value.bottom }
        val payTop = screenH - bottom - 16 - PAY_HEIGHT
        val payW = formW
        val slotW = (cardW - CARD_PAD * 2 - GROUP_GAP * 3) / 16
        val numberTop = (cardH * NUMBER_AT).roundToInt().toFloat()
        val bottomTop = cardH - BOTTOM_ROW
        val markOrigin = Offset(cardW - CARD_PAD - 40, 30f)
        // Where the outline frames each part of the front: the number row is fixed; the name and the
        // expiry are measured, so the outline hugs them (and grows with the name as it is typed).
        val outlines = remember(cardW, cardH, nameBox, expiryBox) {
            mapOf(
                Field.Number to Rect(CARD_PAD - OUTLINE_PAD_X, numberTop - 6, cardW - CARD_PAD + OUTLINE_PAD_X, numberTop - 6 + NUMBER_LINE + 12),
                Field.Name to Rect(
                    CARD_PAD - OUTLINE_PAD_X, bottomTop - OUTLINE_PAD_Y,
                    CARD_PAD + nameBox.width + OUTLINE_PAD_X, bottomTop + nameBox.height + OUTLINE_PAD_Y,
                ),
                Field.Expiry to Rect(
                    cardW - CARD_PAD - expiryBox.width - OUTLINE_PAD_X, bottomTop - OUTLINE_PAD_Y,
                    cardW - CARD_PAD + OUTLINE_PAD_X, bottomTop + expiryBox.height + OUTLINE_PAD_Y,
                ),
            )
        }
        LaunchedEffect(outlines, fields) {
            if (rects.isEmpty()) engine.placeOutline(outlines.getValue(Field.Number))
            rects = outlines
            fieldRects = fields
            engine.reframe(field, outlines, nowSeconds(), still)
            wakeUp()
        }
        val ime = WindowInsets.ime
        val fieldNow by rememberUpdatedState(field)

        // Whatever is focused stays above the keyboard: the whole page rises as far as it has to.
        // Read in the layers' draw blocks, so it follows the keyboard frame by frame.
        fun riseNow(): Float {
            val kb = ime.getBottom(density) / density.density
            val f = fieldNow
            return if (kb > 0 && f != null) max(0f, (fieldBottom[f] ?: 0f) + 16 - (screenH - kb)) else 0f
        }
        // The big header fades out before it rises into the top inset, and the compact bar takes its place.
        fun collapseNow(): Float {
            val k = (riseNow() / HEADER_ROOM).coerceIn(0f, 1f)
            return k * k * (3 - 2 * k)
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -riseNow() * density.density },
        ) {
            Header(
                amount,
                Modifier
                    .offset(SIDE.dp, (top + 14).dp)
                    .width((screenW - SIDE * 2).dp)
                    .graphicsLayer { alpha = 1f - collapseNow() },
            )
            CardShadow(
                color = { mixColor(engine.fromNetwork.color(2), engine.toNetwork.color(2), engine.mix.value(now)) },
                tilt = { engine.tiltAt(now) },
                width = cardW,
                height = cardH,
                modifier = Modifier.offset(((screenW - cardW) / 2 - 40).dp, (cardTop - 20).dp),
            )
            Card(
                engine = engine,
                now = { now },
                still = still,
                cardW = cardW,
                cardH = cardH,
                slotW = slotW,
                numberTop = numberTop,
                bottomTop = bottomTop,
                markOrigin = markOrigin,
                issuer = issuer,
                name = name,
                cast = cast,
                onNameBox = { if (abs(it.width - nameBox.width) >= 0.5f || abs(it.height - nameBox.height) >= 0.5f) nameBox = it },
                onExpiryBox = { if (abs(it.width - expiryBox.width) >= 0.5f || abs(it.height - expiryBox.height) >= 0.5f) expiryBox = it },
                modifier = Modifier
                    .offset(((screenW - cardW) / 2).dp, cardTop.dp)
                    .semantics { contentDescription = "Card preview" + if (network != Network.None) ", ${network.name.lowercase()}" else "" }
                    .pointerInput(cardW, still) {
                        // A tap edits what was tapped; a drag tilts the card towards the finger.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var tilting = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val d = (change.position - down.position) / density.density
                                if (!tilting && d.getDistance() >= 6f) {
                                    tilting = true
                                    takeOver()
                                }
                                if (tilting) {
                                    change.consume()
                                    if (!still) {
                                        engine.hold(soft(-d.y * TILT_PER_DP, TILT_MAX_X), soft(d.x * TILT_PER_DP, TILT_MAX_Y))
                                        wakeUp()
                                    }
                                }
                                if (!change.pressed) break
                            }
                            if (tilting) {
                                val v = tracker.calculateVelocity()
                                engine.letGo(-v.y / density.density * TILT_PER_DP, v.x / density.density * TILT_PER_DP, nowSeconds())
                                wakeUp()
                            } else {
                                takeOver()
                                if (stage == Stage.Idle) {
                                    val x = down.position.x / density.density
                                    val y = down.position.y / density.density
                                    val target = when {
                                        field == Field.Cvv -> Field.Number
                                        y > bottomTop - 12 -> if (x > cardW * 0.62f) Field.Expiry else Field.Name
                                        else -> Field.Number
                                    }
                                    requesters.getValue(target).requestFocus()
                                }
                            }
                        }
                    },
            )
            Box(
                Modifier
                    .offset(SIDE.dp, formTop.dp)
                    .size(formW.dp, formH.dp),
            ) {
                for (target in Field.entries) {
                    val r = fields.getValue(target)
                    FormField(
                        label = when (target) {
                            Field.Number -> "Card number"
                            Field.Name -> "Cardholder name"
                            Field.Expiry -> if (expired) "Card expired" else "Expiry"
                            Field.Cvv -> "CVV"
                        },
                        text = when (target) {
                            Field.Number -> number
                            Field.Name -> nameTyped
                            Field.Expiry -> expiry
                            Field.Cvv -> cvv
                        },
                        shown = when (target) {
                            Field.Number -> Shown.Number
                            Field.Name -> Shown.Upper
                            Field.Expiry -> Shown.Expiry
                            Field.Cvv -> Shown.Plain
                        },
                        placeholder = when (target) {
                            Field.Number -> "0000 0000 0000 0000"
                            Field.Name -> "As printed on the card"
                            Field.Expiry -> "MM/YY"
                            Field.Cvv -> "3 digits"
                        },
                        numeric = target != Field.Name,
                        focused = field == target,
                        caret = playing && field == target,
                        done = done(target),
                        error = target == Field.Expiry && expired,
                        requester = requesters.getValue(target),
                        onFocus = { gotFocus(target) },
                        onBlur = { lostFocus(target) },
                        onChange = { edit(target, it, true) },
                        onNext = if (target == Field.Name) ({ requesters.getValue(Field.Expiry).requestFocus() }) else null,
                        modifier = Modifier
                            .offset(r.left.dp, r.top.dp)
                            .size(r.width.dp, r.height.dp),
                    )
                }
                // The one focus ring, in the card's light colour, gliding between the fields.
                Canvas(Modifier.size(formW.dp, formH.dp)) {
                    val t = now
                    val on = engine.formRingOn.value(t).toFloat()
                    if (on > 0.002f) {
                        scale(density.density, density.density, pivot = Offset.Zero) {
                            val x = engine.formRing[0].value(t).toFloat()
                            val y = engine.formRing[1].value(t).toFloat()
                            val w = engine.formRing[2].value(t).toFloat()
                            val h = engine.formRing[3].value(t).toFloat()
                            val tint = mixColor(engine.fromNetwork.color(2), engine.toNetwork.color(2), engine.mix.value(t))
                            drawRoundRect(
                                tint.copy(alpha = on),
                                topLeft = Offset(x + 0.75f, y + 0.75f),
                                size = Size(max(0f, w - 1.5f), max(0f, h - 1.5f)),
                                cornerRadius = CornerRadius(FIELD_RADIUS - 0.75f),
                                style = Stroke(1.5f),
                            )
                        }
                    }
                }
            }
            SaveRow(
                issuer = issuer,
                save = save,
                onChange = {
                    takeOver()
                    save = it
                },
                modifier = Modifier
                    .offset(SIDE.dp, (formTop + formH + SAVE_GAP).dp)
                    .width(formW.dp),
            )
        }

        // The compact bar: the page slides under it while the keyboard is up.
        Box(
            Modifier
                .fillMaxWidth()
                .height((top + BAR_H).dp)
                .graphicsLayer { alpha = collapseNow() }
                .background(BACKGROUND)
                .clearAndSetSemantics {},
        ) {
            Row(
                Modifier
                    .offset(0.dp, top.dp)
                    .fillMaxWidth()
                    .height(BAR_H.dp)
                    .padding(horizontal = SIDE.dp)
                    .graphicsLayer { translationY = if (still) 0f else 6f * (1f - collapseNow()) * density.density },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Card details", color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                Box(Modifier.weight(1f))
                Text("Total", color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
                Box(Modifier.width(6.dp))
                Text(
                    amount,
                    color = TEXT,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }

        Row(
            Modifier
                .offset(0.dp, (payTop - 34).dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Lock(MUTED, Modifier.size(11.dp))
            Box(Modifier.width(6.dp))
            Text("Encrypted end to end · never stored on this device", color = MUTED, fontSize = 12.sp)
        }

        PayButton(
            label = "Pay $amount",
            width = payW,
            enabled = valid && stage == Stage.Idle,
            down = (scriptPress || held) && valid && stage == Stage.Idle,
            ready = { engine.ready.value(now).toFloat() },
            stage = stage,
            engine = engine,
            now = { now },
            modifier = Modifier
                .offset(SIDE.dp, payTop.dp)
                .pointerInput(valid, stage) {
                    detectTapGestures(
                        onPress = {
                            if (valid && stage == Stage.Idle) {
                                held = true
                                tryAwaitRelease()
                                held = false
                            }
                        },
                        onTap = {
                            if (valid && stage == Stage.Idle) {
                                takeOver()
                                doPay(true)
                            }
                        },
                    )
                },
        )
    }
}

/** The "lock.fill" symbol: a filled body under an open loop. */
@Composable
private fun Lock(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawArc(
            color, 180f, 180f, false,
            topLeft = Offset(w * 0.24f, h * 0.04f), size = Size(w * 0.52f, h * 0.62f),
            style = Stroke(w * 0.13f),
        )
        for (x in listOf(0.24f, 0.76f)) drawLine(color, Offset(w * x, h * 0.34f), Offset(w * x, h * 0.46f), w * 0.13f)
        drawRoundRect(color, Offset(w * 0.1f, h * 0.42f), Size(w * 0.8f, h * 0.58f), CornerRadius(w * 0.14f))
    }
}

@Composable
private fun Header(amount: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CHECKOUT · STEP 2 OF 2", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
            Text("Card details", color = TEXT, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        }
        Box(Modifier.weight(1f))
        Column(Modifier.padding(bottom = 3.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Total", color = MUTED, fontSize = 12.sp)
            Text(amount, color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
        }
    }
}

/**
 * The shadow on the "table" under the card: a dark contact shadow and a wider one tinted with the
 * card's own light colour, both sliding a little against the tilt.
 */
@Composable
private fun CardShadow(color: () -> Color, tilt: () -> Pair<Double, Double>, width: Float, height: Float, modifier: Modifier) {
    val tinted = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(28f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val contact = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.Black.copy(alpha = 0.7f).toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(12f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    Canvas(modifier.size((width + 80).dp, (height + 90).dp)) {
        scale(density, density, pivot = Offset.Zero) {
            val (tx, ty) = tilt()
            translate((-ty * 1.2).toFloat(), (tx * 0.8).toFloat()) {
                tinted.color = color().copy(alpha = 0.3f).toArgb()
                drawIntoCanvas {
                    it.nativeCanvas.drawRoundRect(64f, 44f, 64f + width - 48, 44f + height - 10, CARD_RADIUS, CARD_RADIUS, tinted)
                    it.nativeCanvas.drawRoundRect(52f, 34f, 52f + width - 24, 34f + height - 8, CARD_RADIUS, CARD_RADIUS, contact)
                }
            }
        }
    }
}

@Composable
private fun Card(
    engine: CardEngine,
    now: () -> Double,
    still: Boolean,
    cardW: Float,
    cardH: Float,
    slotW: Float,
    numberTop: Float,
    bottomTop: Float,
    markOrigin: Offset,
    issuer: String,
    name: String,
    cast: CardCast,
    onNameBox: (Size) -> Unit,
    onExpiryBox: (Size) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val turn = { engine.turn(now(), still) }
    val tilt = { engine.tiltAt(now()).let { it.first.toFloat() to it.second.toFloat() } }
    // Where the comet is round an outline (0…1); still under reduced motion.
    val orbit = { if (still) 0.62f else ((now() % ORBIT) / ORBIT).toFloat() }
    val boxTop = STRIP_Y + (STRIP_H - CVV_H) / 2
    val boxLeft = cardW - STRIP_X - 4 - CVV_W
    val numberStyle = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val smallStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val cvvStyle = TextStyle(color = Color(0xFF15161C), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontFeatureSettings = "tnum")
    Box(
        modifier
            .size(cardW.dp, cardH.dp)
            .graphicsLayer {
                val up = if (still) 0f else sin(turn().coerceIn(0.0, 1.0) * PI).toFloat()
                translationY = -LIFT * up * density.density
                scaleX = 1 + LIFT_SCALE * up
                scaleY = 1 + LIFT_SCALE * up
                // The tilt under a finger, through the same 1100 dp perspective.
                val (tx, ty) = tilt()
                rotationX = tx
                rotationY = ty
                cameraDistance = PERSPECTIVE * density.density / 72f
            },
    ) {
        // The front.
        Box(
            Modifier
                .size(cardW.dp, cardH.dp)
                .graphicsLayer {
                    val f = turn().toFloat()
                    rotationY = if (still) 0f else f * 180
                    cameraDistance = PERSPECTIVE * density.density / 72f
                    alpha = if (still) 1 - f.coerceIn(0f, 1f) else if (f < 0.5f) 1f else 0f
                    shape = RoundedCornerShape(CARD_RADIUS.dp)
                    clip = true
                },
        ) {
            CardArt(
                cardW, cardH, false, { engine.fromNetwork }, { engine.toNetwork }, { engine.mix.value(now()).toFloat() },
                { engine.shine.value(now()).toFloat() }, tilt,
                {
                    val t = now()
                    OutlineState(
                        Rect(
                            engine.outline[0].value(t).toFloat(), engine.outline[1].value(t).toFloat(),
                            (engine.outline[0].value(t) + engine.outline[2].value(t)).toFloat(),
                            (engine.outline[1].value(t) + engine.outline[3].value(t)).toFloat(),
                        ),
                        engine.outlineOn.value(t).toFloat(), orbit(),
                    )
                },
                markOrigin,
            )
            Text(
                issuer.uppercase(),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.2.sp,
                modifier = Modifier.offset(CARD_PAD.dp, 24.dp),
            )
            Marks(cast, { engine.markScale.value(it).toFloat() }, now, Modifier.align(Alignment.TopEnd).padding(end = CARD_PAD.dp, top = 18.dp).height(26.dp))
            Contactless(Modifier.offset((CHIP_X + CHIP_W + 12).dp, (CHIP_Y + 6).dp))
            Row(Modifier.offset(CARD_PAD.dp, numberTop.dp), horizontalArrangement = Arrangement.spacedBy(GROUP_GAP.dp)) {
                for (group in 0 until 4) {
                    Row {
                        for (k in 0 until 4) {
                            val reel = engine.numberReels[group * 4 + k]
                            ReelView({ reel.roll(now()) }, { reel.speed(now()) }, "•", slotW, NUMBER_LINE, numberStyle, shadow = true)
                        }
                    }
                }
            }
            Row(Modifier.offset(CARD_PAD.dp, bottomTop.dp).width((cardW - CARD_PAD * 2).dp)) {
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.onSizeChanged { onNameBox(Size(it.width / density.density, it.height / density.density)) },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardLabel("CARD HOLDER")
                        NameLine(cast, now, still)
                    }
                }
                Column(
                    Modifier.onSizeChanged { onExpiryBox(Size(it.width / density.density, it.height / density.density)) },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CardLabel("EXPIRES")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until 2) {
                            val reel = engine.expiryReels[i]
                            ReelView({ reel.roll(now()) }, { reel.speed(now()) }, "M", 10f, SMALL_LINE, smallStyle)
                        }
                        Text("/", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 2.dp))
                        for (i in 2 until 4) {
                            val reel = engine.expiryReels[i]
                            ReelView({ reel.roll(now()) }, { reel.speed(now()) }, "Y", 10f, SMALL_LINE, smallStyle)
                        }
                    }
                }
            }
        }
        // The back.
        Box(
            Modifier
                .size(cardW.dp, cardH.dp)
                .graphicsLayer {
                    val f = turn().toFloat()
                    rotationY = if (still) 0f else f * 180 - 180
                    cameraDistance = PERSPECTIVE * density.density / 72f
                    alpha = if (still) f.coerceIn(0f, 1f) else if (f >= 0.5f) 1f else 0f
                    shape = RoundedCornerShape(CARD_RADIUS.dp)
                    clip = true
                },
        ) {
            // The CVV's outline: the same frame of light, round the box, while the CVV is typed.
            CardArt(
                cardW, cardH, true, { engine.fromNetwork }, { engine.toNetwork }, { engine.mix.value(now()).toFloat() },
                { engine.shine.value(now()).toFloat() }, tilt,
                {
                    OutlineState(
                        Rect(boxLeft - 5, boxTop - 5, boxLeft + CVV_W + 5, boxTop + CVV_H + 5),
                        engine.ring.value(now()).toFloat(), orbit(), dark = true,
                    )
                },
                markOrigin,
            )
            Text(
                if (name.isEmpty()) "" else titleCase(name),
                color = Color(0xFF2A2F45),
                fontFamily = FontFamily.Cursive,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = TextStyle(lineHeight = 28.sp),
                modifier = Modifier
                    .offset((STRIP_X + 12).dp, (STRIP_Y + 4).dp)
                    .width((cardW - STRIP_X * 2 - CVV_W - 30).dp),
            )
            Text(
                "CVV",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = (STRIP_X + 4).dp, top = (STRIP_Y - 16).dp),
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = (STRIP_X + 4).dp, top = boxTop.dp)
                    .size(CVV_W.dp, CVV_H.dp)
                    .background(Color.White, RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 3) {
                    val reel = engine.cvvReels[i]
                    ReelView({ reel.roll(now()) }, { reel.speed(now()) }, "•", 13f, 22f, cvvStyle)
                }
            }
            Text(
                "Authorised signature. This card stays the property of $issuer and is to be returned on request.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(lineHeight = 11.sp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CARD_PAD.dp, end = CARD_PAD.dp, bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

/** What the card lists, copied out of the engine: the marks and the letters, and when each came. */
internal data class CardCast(
    val mark: Network,
    val markAt: Double,
    val leaving: List<Pair<Network, Double>>,
    val name: String,
    val letterTimes: List<Double>,
    val ghosts: List<CardEngine.Ghost>,
    val placeholderAt: Double,
    val placeholderLeftAt: Double?,
    val placeholderFading: Boolean,
) {
    companion object {
        fun of(engine: CardEngine, at: Double) = CardCast(
            engine.mark,
            engine.markAt,
            engine.leavingMarks.toList(),
            engine.name,
            engine.letterTimes,
            engine.ghosts.toList(),
            engine.placeholderAt,
            engine.placeholderLeftAt,
            engine.placeholderLeftAt?.let { at - it < 0.2 } ?: false,
        )
    }
}

/** The mark of the network, popping in as the old one fades and grows away. */
@Composable
private fun Marks(cast: CardCast, scale: (Double) -> Float, now: () -> Double, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        for ((network, at) in cast.leaving) {
            key(network, at) {
                NetworkMark(
                    network,
                    Modifier.graphicsLayer {
                        val t = now() - at
                        val s = 1 + 0.15f * InOutQuad.at(t / 0.2).toFloat()
                        scaleX = s
                        scaleY = s
                        alpha = 1 - InOutQuad.at(t / 0.16).toFloat()
                    },
                )
            }
        }
        key(cast.mark, cast.markAt) {
            NetworkMark(
                cast.mark,
                Modifier.graphicsLayer {
                    val t = now() - cast.markAt
                    val s = scale(now())
                    scaleX = s
                    scaleY = s
                    translationY = 6 * (1 - EaseOut.at(t / 0.3).toFloat()) * density
                    alpha = InOutQuad.at(t / 0.26).toFloat()
                },
            )
        }
    }
}

/**
 * The name on the card, letter by letter: each rises 9 dp and grows from 0.86 as it comes and fades
 * up as it goes; "YOUR NAME" stands in while it is empty.
 */
@Composable
private fun NameLine(cast: CardCast, now: () -> Double, still: Boolean) {
    val style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, lineHeight = SMALL_LINE.sp)
    fun letter(c: Char) = if (c == ' ') "\u00a0" else c.toString()
    Box(Modifier.height(SMALL_LINE.dp).clipToBounds()) {
        val name = cast.name
        val left = cast.placeholderLeftAt
        if (name.isEmpty() || cast.placeholderFading) {
            val gone = name.isNotEmpty()
            Text(
                "YOUR NAME",
                style = style.copy(color = Color.White.copy(alpha = 0.4f)),
                modifier = Modifier.graphicsLayer {
                    if (gone) {
                        val k = InOutQuad.at((now() - (left ?: 0.0)) / 0.18).toFloat()
                        alpha = 1 - k
                        translationY = -6 * k * density
                    } else if (!still) {
                        val t = now() - cast.placeholderAt
                        val k = EaseOut.at(t / 0.3).toFloat()
                        alpha = InOutQuad.at(t / 0.2).toFloat()
                        translationY = 9 * (1 - k) * density
                        scaleX = 0.86f + 0.14f * k
                        scaleY = 0.86f + 0.14f * k
                    }
                },
            )
        }
        for (ghost in cast.ghosts) {
            key(ghost) {
                Row {
                    ghost.text.forEachIndexed { i, c ->
                        val leaving = i in ghost.leaving
                        Text(
                            letter(c),
                            style = style,
                            modifier = Modifier.graphicsLayer {
                                val k = InOutQuad.at((now() - ghost.at) / 0.18).toFloat()
                                alpha = if (leaving) 1 - k else 0f
                                translationY = if (leaving) -6 * k * density else 0f
                            },
                        )
                    }
                }
            }
        }
        Row {
            name.forEachIndexed { i, c ->
                val at = cast.letterTimes.getOrElse(i) { 0.0 }
                Text(
                    letter(c),
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        if (!still) {
                            val t = now() - at
                            val k = EaseOut.at(t / 0.3).toFloat()
                            alpha = InOutQuad.at(t / 0.2).toFloat()
                            translationY = 9 * (1 - k) * density
                            scaleX = 0.86f + 0.14f * k
                            scaleY = 0.86f + 0.14f * k
                        }
                    },
                )
            }
        }
    }
}

/** A field with its label inside, above the value, and a check (or a warning) at its end. */
@Composable
private fun FormField(
    label: String,
    text: String,
    shown: Shown,
    placeholder: String,
    numeric: Boolean,
    focused: Boolean,
    caret: Boolean,
    done: Boolean,
    error: Boolean,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onBlur: () -> Unit,
    onChange: (String) -> Unit,
    /** The keyboard's action key: moves on to another field when given, else "done". */
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(if (error) DANGER else FIELD_BORDER, tween(200), label = "border")
    val labelColor by animateColorAsState(if (error) DANGER else if (focused) TEXT else MUTED, tween(200), label = "label")
    var hasFocus by remember { mutableStateOf(false) }
    val display = shown.format(text)
    val inputStyle = TextStyle(color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    Box(
        modifier
            .background(FIELD_BG, RoundedCornerShape(FIELD_RADIUS.dp))
            .border(1.dp, border, RoundedCornerShape(FIELD_RADIUS.dp)),
    ) {
        Text(label, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(16.dp, LABEL_TOP.dp))
        BasicTextField(
            // The field holds the bare text (digits, or the name as typed); [shown] formats it.
            value = TextFieldValue(text, TextRange(text.length)),
            onValueChange = { value -> if (value.text != text) onChange(value.text) },
            visualTransformation = shown,
            textStyle = inputStyle,
            cursorBrush = SolidColor(TEXT),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = if (onNext != null) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
            modifier = Modifier
                .offset(0.dp, (VALUE_TOP - 4).dp)
                .fillMaxWidth()
                .height((VALUE_H + 8).dp)
                .padding(start = 16.dp, end = 44.dp)
                .focusRequester(requester)
                .onFocusChanged { state ->
                    if (state.isFocused && !hasFocus) onFocus()
                    if (!state.isFocused && hasFocus) onBlur()
                    hasFocus = state.isFocused
                }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (display.isEmpty()) Text(placeholder, style = inputStyle.copy(color = PLACEHOLDER), maxLines = 1)
                    inner()
                    // autoPlay's caret: the field is "focused" without a keyboard.
                    if (caret) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(display.replace(' ', '\u00a0'), style = inputStyle.copy(color = Color.Transparent), maxLines = 1)
                            Caret(TEXT, Modifier.padding(start = 1.dp))
                        }
                    }
                }
            },
        )
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(18.dp), contentAlignment = Alignment.Center) {
            Badge(visible = error, ok = false)
            Badge(visible = done && !error, ok = true)
        }
    }
}

/** A field's check or warning: pops in from 60% on a lively spring, leaves in 140 ms. */
@Composable
private fun Badge(visible: Boolean, ok: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.8f),
    ) {
        Canvas(Modifier.size(18.dp)) {
            val color = if (ok) SUCCESS else DANGER
            drawCircle(color)
            val u = size.width / 18f
            if (ok) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(5.2f * u, 9.3f * u)
                    lineTo(7.9f * u, 12f * u)
                    lineTo(12.9f * u, 6.6f * u)
                }
                drawPath(path, BACKGROUND, style = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawLine(BACKGROUND, Offset(9f * u, 4.8f * u), Offset(9f * u, 10.2f * u), 2f * u, StrokeCap.Round)
                drawCircle(BACKGROUND, 1.15f * u, Offset(9f * u, 13.2f * u))
            }
        }
    }
}

/** "Save card for next time" and its switch. */
@Composable
private fun SaveRow(issuer: String, save: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Save card for next time", color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Kept by $issuer, never on this phone", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.width(16.dp))
        Switch(
            checked = save,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SUCCESS,
                checkedBorderColor = SUCCESS,
                uncheckedThumbColor = Color(0xFF8A8E9C),
                uncheckedTrackColor = Color(0xFF2A2C35),
                uncheckedBorderColor = Color(0xFF2A2C35),
            ),
            modifier = Modifier.semantics { contentDescription = "Save card for next time" },
        )
    }
}

/** How a field shows its bare text: grouped in fours, as MM/YY, upper case, or as it is. */
private enum class Shown : VisualTransformation {
    Number, Expiry, Upper, Plain;

    fun format(text: String) = when (this) {
        Number -> groupNumber(text)
        Expiry -> showExpiry(text)
        Upper -> text.uppercase()
        Plain -> text
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = format(raw)
        val mapping = when (this) {
            Number -> object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = if (offset <= 0) 0 else (offset + (offset - 1) / 4).coerceAtMost(out.length)
                override fun transformedToOriginal(offset: Int) = (offset - offset / 5).coerceIn(0, raw.length)
            }
            Expiry -> object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = (if (offset <= 2) offset else offset + 1).coerceAtMost(out.length)
                override fun transformedToOriginal(offset: Int) = (if (offset <= 2) offset else offset - 1).coerceIn(0, raw.length)
            }
            else -> OffsetMapping.Identity
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** The caret autoPlay types behind: on for 45% of a second, then off. */
@Composable
private fun Caret(color: Color, modifier: Modifier = Modifier) {
    var phase by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { phase = (it / 1e9) % 1.0 }
    }
    Box(
        modifier
            .size(2.dp, 22.dp)
            .graphicsLayer {
                alpha = when {
                    phase < 0.45 -> 1f
                    phase < 0.55 -> (1 - (phase - 0.45) / 0.1).toFloat()
                    else -> 0f
                }
            }
            .background(color, RoundedCornerShape(1.dp)),
    )
}

/** The pay button: dark until the card is complete, then white; it folds into a spinning ring, then fills green behind a check. */
@Composable
private fun PayButton(
    label: String,
    width: Float,
    enabled: Boolean,
    down: Boolean,
    ready: () -> Float,
    stage: Stage,
    engine: CardEngine,
    now: () -> Double,
    modifier: Modifier,
) {
    val press by animateFloatAsState(if (down) 0.97f else 1f, tween(140, easing = InOutQuad), label = "press")
    val spinning = stage == Stage.Paying
    val ringOut by animateFloatAsState(if (spinning) 1f else 0f, tween(if (spinning) 0 else 120, easing = InOutQuad), label = "ring")
    // RN dashes the check 26 long, so it starts to show a little into the stroke.
    val checkLength = hypot(4.5f, 4.5f) + hypot(8.5f, 9.5f)
    Box(
        modifier
            .size(width.dp, PAY_HEIGHT.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .semantics { contentDescription = if (stage == Stage.Paid) "Paid" else if (stage == Stage.Paying) "Paying" else label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width.dp, PAY_HEIGHT.dp)) {
            val t = now()
            val f = engine.fold.value(t).toFloat()
            val c = engine.check.value(t).toFloat()
            val w = (width + (PAY_HEIGHT - width) * f) * density
            // Dark until the card is complete, then white; green once paid.
            drawRoundRect(
                mixColor(mixColor(PAY_OFF, TEXT, ready().toDouble()), SUCCESS, c.toDouble()),
                topLeft = Offset((size.width - w) / 2, 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
            val ringAlpha = if (spinning) ((f - 0.6f) * 4).coerceIn(0f, 1f) * (1 - c) else ringOut
            scale(density, density, pivot = center) {
                translate(center.x - 13, center.y - 13) {
                    if (ringAlpha > 0f) {
                        rotate((engine.spin(t) * 360).toFloat(), Offset(13f, 13f)) {
                            drawCircle(Color(13, 14, 18).copy(alpha = 0.15f * ringAlpha), 10f, Offset(13f, 13f), style = Stroke(2.6f))
                            drawArc(BACKGROUND.copy(alpha = ringAlpha), -90f, 90f, false, Offset(3f, 3f), Size(20f, 20f), style = Stroke(2.6f, cap = StrokeCap.Round))
                        }
                    }
                    val shown = ((checkLength - 26 * (1 - c)) / checkLength).coerceIn(0f, 1f)
                    if (shown > 0f) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(6.5f, 13.5f)
                            lineTo(11f, 18f)
                            lineTo(19.5f, 8.5f)
                        }
                        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                        val part = androidx.compose.ui.graphics.Path()
                        measure.getSegment(0f, measure.length * shown, part, true)
                        drawPath(part, Color.White, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        BasicText(
            label,
            color = { mixColor(PAY_OFF_TEXT, BACKGROUND, ready().toDouble()) },
            maxLines = 1,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            modifier = Modifier.graphicsLayer { alpha = max(0f, 1 - engine.fold.value(now()).toFloat() * 3) },
        )
    }
}
