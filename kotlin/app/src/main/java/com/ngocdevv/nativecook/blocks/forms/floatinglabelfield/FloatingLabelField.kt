package com.ngocdevv.nativecook.blocks.forms.floatinglabelfield

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/*
 Floating Label Field: the sign-up of Larch, a trail journal: name, email, password, a button.

 The loop types the form without focusing anything (no keyboard): the name, then an address with
 its dot missing, which fails when Return is pressed (one shake, red outline, a helper line), is
 fixed and passes; then a password whose strength bar fills segment by segment. With all three
 valid the button comes alive under a gleam and is pressed. A real tap focuses the real field and
 the same animations run from what is typed.

 One frame loop drives everything. It writes the poses, which only draw and layer lambdas read,
 so the screen does not recompose while it plays.
*/

private data class FieldSpec(
    val label: String,
    val keyboard: KeyboardType,
    val capitalize: KeyboardCapitalization,
)

private val FIELDS = listOf(
    FieldSpec("Full name", KeyboardType.Text, KeyboardCapitalization.Words),
    FieldSpec("Email", KeyboardType.Email, KeyboardCapitalization.None),
    FieldSpec("Password", KeyboardType.Password, KeyboardCapitalization.None),
)
private val DEFAULT_HELPERS = listOf(fieldMessage(0, ""), fieldMessage(1, "mara@larchco"), fieldMessage(2, "x"))
private const val DOT_SIZE = 44f
/** Seconds after mount before the loop starts. */
private const val START_DELAY = 0.35f
private val INK = FieldColors.ink
private val MUTED = FieldColors.muted

private class Geo(val W: Float, val H: Float, top: Float, bottom: Float) {
    val compact = H < 740f
    val margin = max(24f, (W - 420f) / 2f)
    val width = W - margin * 2f
    val artH = top + if (compact) 92f else 209f
    val markY = top + if (compact) 8f else 14f
    val titleTop = artH + if (compact) 8f else 18f
    val titleSize = if (compact) 26f else 30f
    val subTop = titleTop + if (compact) 34f else 42f
    val fieldH = if (compact) 52f else 62f
    val slot = if (compact) 24f else 30f
    val pitch = fieldH + slot
    val fieldsTop = subTop + if (compact) 36f else 47f
    val buttonTop = fieldsTop + pitch * 3f + if (compact) 2f else 8f
    val buttonH = if (compact) 50f else 56f
    val legalTop = buttonTop + buttonH + 12f
    val footerTop = H - bottom - if (compact) 36f else 46f
}

/** The numbers a person's input animates for one field. */
private class FieldValues {
    val focus = Tween()
    val float = Tween()
    val lineStart = Tween()
    val lineEnd = Tween()
    val valid = Tween()
    val error = Tween()
    val shake = Tween()
    val count = Tween()
    val caret = Tween()
    val strength = Tween()
    val text = Tween(1f)

    fun step(dt: Float) {
        focus.step(dt)
        float.step(dt)
        lineStart.step(dt)
        lineEnd.step(dt)
        valid.step(dt)
        error.step(dt)
        shake.step(dt)
        count.step(dt)
        caret.step(dt)
        strength.step(dt)
        text.step(dt)
    }

    fun publish(out: FieldPose) {
        out.focus = focus.v
        out.float = float.v
        out.lineStart = lineStart.v
        out.lineEnd = lineEnd.v
        out.valid = valid.v
        out.error = error.v
        out.shake = shake.v
        out.frame = -1
        out.count = count.v
        out.caret = caret.v
        out.strength = strength.v
        out.text = text.v
    }
}

/**
 The screen's state and behaviour. A class, not local functions: nothing here can read a value
 captured by an earlier composition.
*/
private class FormController(val auto: Boolean, val still: Boolean, val reduced: Boolean) {
    val poses = List(3) { FieldPose() }
    val button = ButtonPose()
    val dot = DotPose()
    val states = List(3) { TextFieldState() }
    val requesters = List(3) { FocusRequester() }
    val helpers = mutableStateListOf<String>().apply { addAll(DEFAULT_HELPERS) }
    var welcome by mutableStateOf("Mara")
    /** Keyboard lift of the page, dp. */
    val lift = Tween()

    private val us = List(3) { FieldValues() }
    private val uReady = Tween()
    private val uGleam = Tween()
    private val uPress = Tween()
    private val uDone = Tween()

    private var now = 0f
    private var clock = 0f
    /** 0 = the loop (or rest), 1 = a person drives, 2 = easing back to the empty form. */
    var mode by mutableIntStateOf(0)
        private set
    private var lastTouch = 0f
    private var returnAt = 0f
    private var focused = -1
    private var keyboard = 0f
    private var liftTarget = 0f
    private var level = 0
    private var ready = false
    private val shownFrame = intArrayOf(-1, -1, -1)
    val values = arrayOf("", "", "")
    var haptics: Haptics? = null
    var geo: Geo? = null

    private fun ms(duration: Float) = if (reduced) 0f else duration

    // ---- The frame ---------------------------------------------------------------------------
    fun step(dt: Float, keyboardDp: Float) {
        now += dt
        us.forEach { it.step(dt) }
        uReady.step(dt)
        uGleam.step(dt)
        uPress.step(dt)
        uDone.step(dt)
        lift.step(dt)
        if (keyboardDp != keyboard) {
            keyboard = keyboardDp
            raise()
        }

        when (mode) {
            0 -> if (auto && now > START_DELAY) clock = (clock + dt) % LOOP
            1 -> if (focused < 0 && now - lastTouch > IDLE) beginReturn()
            else -> if (now - returnAt > RETURN_LEN) finishReturn()
        }
        publish()
    }

    private fun publish() {
        if (mode == 0) {
            val t = if (still) T_FILLED else clock
            for (i in 0 until 3) {
                val p = poses[i]
                autoField(i, t, p)
                if (still) p.caret = 0f
                // The loop writes the value straight into the field: nothing takes the focus.
                if (p.frame != shownFrame[i]) {
                    shownFrame[i] = p.frame
                    val v = VALUES[i][p.frame]
                    values[i] = v
                    if (states[i].text.toString() != v) states[i].setTextAndPlaceCursorAtEnd(v)
                }
            }
            autoButton(t, button)
            if (auto) {
                autoDot(clock, dot)
            } else {
                dot.alpha = 0f
            }
        } else {
            for (i in 0 until 3) us[i].publish(poses[i])
            button.ready = uReady.v
            button.gleam = uGleam.v
            button.press = uPress.v
            button.done = uDone.v
            dot.alpha = 0f
        }
    }

    private fun restField(u: FieldValues, d: Float) {
        u.focus.to(0f, d * 0.4f)
        u.caret.set(0f)
        u.text.to(0f, d * 0.45f)
        u.valid.to(0f, d * 0.45f)
        u.error.to(0f, d * 0.45f)
        u.strength.to(0f, d * 0.6f)
        u.lineStart.to(1f, d * 0.8f)
        u.float.to(0f, d * 0.65f, InOutCubic, d * 0.35f)
    }

    private fun beginReturn() {
        mode = 2
        returnAt = now
        val d = if (reduced) 0f else RETURN_LEN * 1000f * 0.9f
        us.forEach { restField(it, d) }
        uReady.to(0f, d * 0.6f)
        uDone.to(0f, d * 0.4f)
        uPress.set(0f)
    }

    private fun finishReturn() {
        us.forEach { it.count.set(0f) }
        clock = 0f
        mode = 0
        for (i in 0 until 3) {
            values[i] = ""
            shownFrame[i] = -1
            helpers[i] = DEFAULT_HELPERS[i]
        }
        level = 0
        ready = false
        welcome = "Mara"
    }

    private fun touch() {
        lastTouch = now
    }

    // ---- Driving a field from real input -----------------------------------------------------
    private fun drawOutline(u: FieldValues) {
        val start = u.lineStart.v
        val end = u.lineEnd.v
        if (end - start > 0.98f) return
        if (start >= end - 0.001f) {
            u.lineStart.set(0f)
            u.lineEnd.set(0f)
        } else {
            u.lineStart.to(0f, ms(300f), OutCubic)
        }
        u.lineEnd.to(1f, ms(DRAW_MS), InOutCubic)
    }

    private fun focusField(u: FieldValues, secure: Boolean) {
        u.focus.to(1f, ms(FOCUS_MS), OutCubic)
        u.float.to(1f, ms(FLOAT_MS), OutCubic)
        drawOutline(u)
        if (secure) {
            if (reduced) u.caret.set(1f) else u.caret.blink()
        }
    }

    private fun failField(u: FieldValues) {
        u.valid.to(0f, ms(150f))
        u.error.to(1f, ms(ERROR_MS), OutCubic)
        if (!reduced) {
            u.shake.set(0f)
            u.shake.to(1f, SHAKE_MS)
        }
        drawOutline(u)
    }

    private fun blurField(u: FieldValues, empty: Boolean, ok: Boolean) {
        u.focus.to(0f, ms(FOCUS_MS))
        u.caret.stop()
        u.caret.to(0f, ms(100f))
        if (empty) {
            u.float.to(0f, ms(FLOAT_MS), InOutCubic)
            u.valid.to(0f, ms(150f))
            if (u.error.v < 0.5f) u.lineStart.to(1f, ms(RETRACT_MS), InOutCubic)
            return
        }
        if (ok) {
            u.valid.to(1f, ms(CHECK_MS), OutCubic)
            u.lineStart.to(1f, ms(RETRACT_MS), InOutCubic)
            return
        }
        failField(u)
    }

    private fun changeField(u: FieldValues, ok: Boolean, length: Int = -1, newLevel: Int = -1) {
        if (u.error.v > 0f) u.error.to(0f, ms(ERROR_CLEAR_MS))
        u.valid.to(if (ok) 1f else 0f, ms(if (ok) CHECK_MS else 150f), OutCubic)
        if (length >= 0) u.count.to(length.toFloat(), ms(DOT_MS), OutCubic)
        if (newLevel >= 0) u.strength.to(newLevel.toFloat(), ms(STRENGTH_MS), OutCubic)
    }

    /** Enables the button when all three values pass, with a gleam the moment it happens. */
    private fun syncReady() {
        val ok = (0 until 3).all { fieldOk(it, values[it]) }
        if (ok == ready) return
        ready = ok
        uReady.to(if (ok) 1f else 0f, ms(READY_MS), InOutCubic)
        if (ok && !reduced) {
            uGleam.set(0f)
            uGleam.to(1f, GLEAM_MS, Linear, 250f)
        }
    }

    private fun setHelper(i: Int, message: String) {
        if (helpers[i] != message) helpers[i] = message
    }

    private fun fail(i: Int) {
        setHelper(i, fieldMessage(i, values[i]))
        failField(us[i])
        haptics?.error()
    }

    private fun settle(i: Int) {
        val v = values[i]
        val ok = fieldOk(i, v)
        if (v.isNotEmpty() && !ok) {
            setHelper(i, fieldMessage(i, v))
            haptics?.error()
        }
        blurField(us[i], v.isEmpty(), ok)
    }

    /** The loop's frame at `t` becomes the person's state, so taking over never jumps. */
    private fun freeze(t: Float, keep: Int, ok: Boolean) {
        val p = FieldPose()
        for (j in 0 until 3) {
            autoField(j, t, p)
            val u = us[j]
            u.focus.set(p.focus)
            u.float.set(p.float)
            u.lineStart.set(p.lineStart)
            u.lineEnd.set(p.lineEnd)
            u.valid.set(p.valid)
            u.error.set(p.error)
            u.shake.set(0f)
            u.count.set(p.count)
            u.caret.set(0f)
            u.strength.set(p.strength)
            u.text.set(1f)
            // A field the loop was typing in gives up the focus quietly.
            if (p.focus > 0.5f && j != keep) {
                u.focus.to(0f, FOCUS_MS)
                if (p.error < 0.5f) u.lineStart.to(1f, RETRACT_MS)
                if (p.frame == 0) u.float.to(0f, FLOAT_MS)
            }
        }
        val b = ButtonPose()
        autoButton(t, b)
        uReady.set(b.ready)
        uReady.to(if (ok) 1f else 0f, READY_MS)
        uGleam.set(b.gleam)
        uPress.set(0f)
        uDone.set(b.done)
        uDone.to(0f, 200f)
        mode = 1
        lastTouch = now
    }

    /** Caught on the way back to rest, the form comes back as the person left it. */
    private fun resume(ok: Boolean) {
        for (j in 0 until 3) {
            us[j].text.to(1f, 150f)
            us[j].float.to(if (values[j].isNotEmpty()) 1f else 0f, FLOAT_MS)
            us[j].valid.to(if (fieldOk(j, values[j])) 1f else 0f, 200f)
        }
        us[2].count.set(values[2].length.toFloat())
        us[2].strength.to(level.toFloat(), 200f)
        uReady.to(if (ok) 1f else 0f, READY_MS)
        mode = 1
        lastTouch = now
    }

    private fun enterUser(keep: Int) {
        val m = mode
        if (m == 1) return
        if (m == 0) {
            val t = if (still) T_FILLED else if (auto) clock else 0f
            for (j in 0 until 3) {
                val v = VALUES[j][frameAt(j, t)]
                values[j] = v
                if (states[j].text.toString() != v) states[j].setTextAndPlaceCursorAtEnd(v)
            }
            level = passwordLevel(values[2])
            ready = (0 until 3).all { fieldOk(it, values[it]) }
            freeze(t, keep, ready)
            return
        }
        ready = (0 until 3).all { fieldOk(it, values[it]) }
        resume(ready)
    }

    /** Keeps the focused field and its slot 16 dp above the keyboard. */
    private fun raise() {
        val g = geo ?: return
        val need = if (focused < 0 || keyboard <= 0f) {
            0f
        } else {
            g.fieldsTop + focused * g.pitch + g.fieldH + g.slot + 16f - (g.H - keyboard)
        }
        val target = max(0f, need)
        if (target != liftTarget) {
            liftTarget = target
            lift.to(target, ms(280f), OutCubic)
        }
    }

    // ---- Events ------------------------------------------------------------------------------
    fun onFocusChange(i: Int, isFocused: Boolean) {
        if (isFocused) {
            enterUser(i)
            focused = i
            touch()
            focusField(us[i], i == 2)
            raise()
        } else {
            if (focused == i) focused = -1
            touch()
            settle(i)
            raise()
        }
    }

    /** A value changed in the field. The loop's own writes never get here. */
    fun onValue(i: Int, v: String) {
        if (mode != 1 || v == values[i]) return
        values[i] = v
        touch()
        if (i == 2) {
            val next = passwordLevel(v)
            if (next > level) haptics?.tick()
            level = next
            changeField(us[2], next >= 3, v.length, next)
        } else {
            changeField(us[i], fieldOk(i, v))
        }
        syncReady()
    }

    fun onSubmit(i: Int, focusManager: FocusManager) {
        touch()
        if (!fieldOk(i, values[i])) {
            fail(i)
            return
        }
        if (i < 2) requesters[i + 1].requestFocus() else focusManager.clearFocus()
    }

    fun onCreate(focusManager: FocusManager) {
        enterUser(-1)
        touch()
        val wrong = (0 until 3).filter { !fieldOk(it, values[it]) }
        if (wrong.isNotEmpty()) {
            wrong.forEach { fail(it) }
            return
        }
        focusManager.clearFocus()
        welcome = values[0].trim().split(Regex("\\s+")).firstOrNull() ?: "Mara"
        uDone.to(1f, ms(350f), OutCubic)
        haptics?.success()
    }

    /** Whether a point of the screen, dp, is on one of the fields or the button. */
    fun hitsControl(x: Float, y: Float): Boolean {
        val g = geo ?: return false
        if (x < g.margin || x > g.margin + g.width) return false
        val py = y + lift.v
        for (i in 0 until 3) {
            val top = g.fieldsTop + i * g.pitch
            if (py >= top && py <= top + g.fieldH) return true
        }
        return py >= g.buttonTop && py <= g.buttonTop + g.buttonH
    }

    fun press(down: Boolean) {
        if (down) uPress.to(1f, 100f) else uPress.to(0f, 180f)
    }
}

/** Touch feedback for the events of the form. */
private class Haptics(private val view: View) {
    fun error() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    fun success() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

private fun reducedMotion(context: Context): Boolean {
    val resolver = context.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
}

private val BodyStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
fun FloatingLabelField(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val reduced = remember { reducedMotion(context) }
    val controller = remember(autoPlay, reduced) {
        FormController(auto = autoPlay && !reduced, still = autoPlay && reduced, reduced = reduced)
    }
    controller.haptics = remember(view) { Haptics(view) }
    // a light page: dark status bar icons, as the RN block's <StatusBar style="dark" />
    BlockSystemBars(darkIcons = true)

    val ime = WindowInsets.ime
    LaunchedEffect(controller) {
        var last = withFrameNanosCompat()
        while (true) {
            val nanos = withFrameNanosCompat()
            val dt = ((nanos - last) / 1e9f).coerceIn(0f, 0.064f)
            last = nanos
            controller.step(dt, ime.getBottom(density) / density.density)
        }
    }
    for (i in 0 until 3) {
        LaunchedEffect(controller, i) {
            snapshotFlow { controller.states[i].text.toString() }.collect { controller.onValue(i, it) }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val measurer = rememberTextMeasurer(cacheSize = 0)
    /** Where the caret stands after every value the loop types, measured with the field's style. */
    val caretTables = remember(measurer, density) {
        List(2) { field ->
            FloatArray(VALUES[field].size) { k ->
                val value = VALUES[field][k]
                if (value.isEmpty()) {
                    1f
                } else {
                    val layout = measurer.measure(value, FieldTextStyle, density = density, softWrap = false)
                    layout.getCursorRect(value.length).left / density.density + 1f
                }
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PAGE)
            .pointerInput(controller, focusManager) {
                detectTapGestures { tap ->
                    // A tap on a field or the button belongs to it; anywhere else closes the keyboard.
                    if (!controller.hitsControl(tap.x / density.density, tap.y / density.density)) focusManager.clearFocus()
                }
            },
    ) {
        val g = remember(maxWidth, maxHeight, top, bottom) { Geo(maxWidth.value, maxHeight.value, top, bottom) }
        controller.geo = g

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -controller.lift.v * this.density },
        ) {
            LarchArt(g.W.dp, g.artH.dp, g.margin.dp, g.markY.dp)
            BasicText(
                text = "Larch",
                modifier = Modifier.offset((g.margin + 34f).dp, (g.markY + 2f).dp),
                style = BodyStyle.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = INK,
                    letterSpacing = (-0.2).sp,
                ),
            )
            BasicText(
                text = "Create your account",
                modifier = Modifier.offset(g.margin.dp, g.titleTop.dp).width(g.width.dp),
                style = BodyStyle.copy(
                    fontSize = g.titleSize.sp,
                    lineHeight = (g.titleSize + 6f).sp,
                    fontWeight = FontWeight.Bold,
                    color = INK,
                    letterSpacing = (-0.6).sp,
                ),
            )
            BasicText(
                text = "Trail logs, maps and gear lists in one place.",
                modifier = Modifier.offset(g.margin.dp, g.subTop.dp).width(g.width.dp),
                style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 20.sp, color = MUTED),
                maxLines = 1,
            )

            FIELDS.forEachIndexed { i, f ->
                FloatingField(
                    label = f.label,
                    pose = controller.poses[i],
                    width = g.width.dp,
                    height = g.fieldH.dp,
                    helper = controller.helpers[i],
                    secure = i == 2,
                    state = controller.states[i],
                    caretTable = caretTables.getOrNull(i),
                    focusRequester = controller.requesters[i],
                    keyboardOptions = KeyboardOptions(
                        capitalization = f.capitalize,
                        autoCorrectEnabled = false,
                        keyboardType = f.keyboard,
                        imeAction = if (i == 2) ImeAction.Done else ImeAction.Next,
                    ),
                    maxLength = if (i == 1) 40 else 28,
                    onFocusChange = { controller.onFocusChange(i, it) },
                    onSubmit = { controller.onSubmit(i, focusManager) },
                    modifier = Modifier.offset(g.margin.dp, (g.fieldsTop + i * g.pitch).dp),
                )
            }

            CreateButton(
                pose = controller.button,
                width = g.width.dp,
                height = g.buttonH.dp,
                welcome = controller.welcome,
                onPress = { controller.onCreate(focusManager) },
                onDown = { controller.press(it) },
                modifier = Modifier.offset(g.margin.dp, g.buttonTop.dp),
            )

            BasicText(
                text = buildAnnotatedString {
                    append("By continuing you agree to the ")
                    withStyle(SpanStyle(color = INK, fontWeight = FontWeight.Medium)) { append("Terms") }
                    append(" and ")
                    withStyle(SpanStyle(color = INK, fontWeight = FontWeight.Medium)) { append("Privacy Policy") }
                    append(".")
                },
                modifier = Modifier.offset(g.margin.dp, g.legalTop.dp).width(g.width.dp),
                style = BodyStyle.copy(fontSize = 12.5.sp, lineHeight = 17.sp, color = MUTED, textAlign = TextAlign.Center),
            )
            BasicText(
                text = buildAnnotatedString {
                    append("Already have an account? ")
                    withStyle(SpanStyle(color = FieldColors.accent, fontWeight = FontWeight.SemiBold)) { append("Sign in") }
                },
                modifier = Modifier.offset(0.dp, g.footerTop.dp).fillMaxWidth(),
                style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 20.sp, color = MUTED, textAlign = TextAlign.Center),
            )
        }

        GhostFinger(controller.dot, g)
    }
}

private suspend fun withFrameNanosCompat(): Long = androidx.compose.runtime.withFrameNanos { it }

// ---- The button --------------------------------------------------------------------------------

private const val GLEAM_W = 90f
private val GLEAM_TAN = tan(-20f * PI.toFloat() / 180f)
private val LABEL_OFF = Color(0xFFA7A698)
private val LABEL_ON = Color(0xFFF6F3EA)

/** Disabled and flat until `ready`; then it takes its colour and shadow, and a gleam crosses it. */
@Composable
private fun CreateButton(
    pose: ButtonPose,
    width: Dp,
    height: Dp,
    welcome: String,
    onPress: () -> Unit,
    onDown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember { RoundedCornerShape(16.dp) }
    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                val s = 1f - 0.03f * pose.press
                scaleX = s
                scaleY = s
            }
            .semantics {
                role = Role.Button
                contentDescription = "Create account"
            }
            .pointerInput(onPress, onDown) {
                detectTapGestures(
                    onPress = {
                        onDown(true)
                        tryAwaitRelease()
                        onDown(false)
                    },
                    onTap = { onPress() },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = pose.ready }
                .dropShadow(shape, Shadow(radius = 2.dp, color = Color(20, 60, 44).copy(alpha = 0.35f), offset = DpOffset(0.dp, 1.dp)))
                .dropShadow(shape, Shadow(radius = 22.dp, color = Color(20, 60, 44).copy(alpha = 0.26f), offset = DpOffset(0.dp, 10.dp)))
                .background(Color(0xFF1F5C45), shape),
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xFFE4DECF)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pose.ready }
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF2C7257),
                            0.55f to Color(0xFF1F5C45),
                            1f to Color(0xFF184B38),
                        ),
                    ),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.22f)))
            }
            // The gleam: a band of light skewed by 20 degrees about its own middle.
            Canvas(Modifier.fillMaxSize()) {
                val p = pose.gleam
                val alpha = sin(PI.toFloat() * p) * pose.ready
                if (alpha <= 0.001f) return@Canvas
                val u = density
                val band = GLEAM_W * u
                val x = (-GLEAM_W + (width.value + GLEAM_W) * easeInOutCubic(p)) * u
                val cy = size.height / 2f
                val skew = Matrix().apply {
                    translate(x, cy)
                    val m = Matrix()
                    m.values[Matrix.SkewX] = GLEAM_TAN
                    timesAssign(m)
                    translate(0f, -cy)
                }
                withTransform({ transform(skew) }) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = 0f),
                            0.5f to Color.White.copy(alpha = 0.4f),
                            1f to Color.White.copy(alpha = 0f),
                            startX = 0f,
                            endX = band,
                        ),
                        topLeft = Offset(0f, -10f * u),
                        size = Size(band, size.height + 20f * u),
                        alpha = alpha,
                    )
                }
            }
            BasicText(
                text = "Create account",
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - pose.done
                    translationY = -8f * pose.done * density
                },
                style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
                color = { lerp(LABEL_OFF, LABEL_ON, pose.ready) },
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val d = pose.done
                        alpha = d
                        translationY = 8f * (1f - d) * density
                    },
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = LABEL_ON, modifier = Modifier.size(15.dp))
                BasicText(
                    text = "Welcome, $welcome",
                    style = BodyStyle.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LABEL_ON,
                        letterSpacing = (-0.2).sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A soft touch dot that taps each field and the button while the loop plays. */
@Composable
private fun GhostFinger(pose: DotPose, g: Geo) {
    Box(
        Modifier
            .size(DOT_SIZE.dp)
            .graphicsLayer {
                val fieldX = g.margin + g.width * 0.64f
                val target = pose.target
                val atX = if (target < 3) fieldX else g.W / 2f + 34f
                val atY = if (target < 3) {
                    g.fieldsTop + target * g.pitch + g.fieldH / 2f
                } else {
                    g.buttonTop + g.buttonH / 2f + 2f
                }
                alpha = pose.alpha
                translationX = (atX - DOT_SIZE / 2f) * density
                translationY = (atY - DOT_SIZE / 2f + (1f - pose.alpha) * 8f) * density
                scaleX = pose.scale
                scaleY = pose.scale
            }
            .dropShadow(CircleShape, Shadow(radius = 12.dp, color = Color(30, 42, 36).copy(alpha = 0.14f), offset = DpOffset(0.dp, 4.dp)))
            .background(Color(30, 42, 36).copy(alpha = 0.16f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
    )
}
