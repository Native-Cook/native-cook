package com.ngocdevv.nativecook.blocks.inputs.ghostcompletefield

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 GhostField: a text field that offers the rest of the sentence.

 After a short pause in typing, a suggestion appears behind the caret in a muted tone, word by
 word out of a blur. Swiping right over the field, or tapping the keycap chip next to the
 suggestion, accepts it: ink runs through the grey text from left to right behind a coloured
 front, and the caret jumps to the end with a small overshoot. Typing on dissolves it; typing the
 suggested letters simply walks into it.

 The field draws its own text over an invisible, real text field, so typed text and suggestion
 are one paragraph and accepting moves nothing.
*/

internal object GhostColors {
    val ink = Color(0xFF0E121A)
    val ghost = Color(0xFFB7BCC8)
    val hint = Color(0xFFA3A9B6)
    val accent = Color(0xFFD9472B)
    val front = Color(0xFFE0714F)
}

private const val CARET_W = 2f
private const val KEYCAP_H = 26f
private val SWEEP_STOPS = floatArrayOf(0f, 0.3f, 0.72f, 1f)

internal fun lightTap(view: View?) {
    view?.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
    )
}

/** What a person's typing does to a GhostField: the state behind its pose and layout. */
internal class GhostController(
    private val font: GhostFont,
    private val width: Float,
    private val lineHeight: Float,
    private val suggest: ((String) -> String?)? = null,
    private val multiline: Boolean = false,
    private val keycap: Boolean = true,
    private val still: Boolean = false,
    private val onChange: ((String) -> Unit)? = null,
) {
    val state = TextFieldState()
    var layout by mutableStateOf(EMPTY_LAYOUT)
        private set
    var typed = ""
        private set
    private var ghost = ""
    private var focused = false
    /** Seconds until the pending suggestion is asked for; negative when none is. */
    private var timer = -1f
    var view: View? = null

    private var n = 0
    var run = -1
        private set
    private var caretFrom = 0
    private var caretTo = 0
    private val appear = Tween()
    val fade = Tween(1f)
    val sweep = Tween()
    private val caretMix = Tween(1f)
    private val caret = Tween()
    private val keycapPop = Tween()
    val keycapPress = Tween()
    val ink = Tween(1f)

    fun step(dt: Float) {
        appear.step(dt)
        fade.step(dt)
        sweep.step(dt)
        caretMix.step(dt)
        caret.step(dt)
        keycapPop.step(dt)
        keycapPress.step(dt)
        ink.step(dt)
        if (timer >= 0f) {
            timer -= dt
            if (timer < 0f) propose()
        }
    }

    fun publish(out: GhostPose) {
        out.n = n
        out.run = run
        out.appear = appear.v
        out.fade = fade.v
        out.sweep = sweep.v
        out.caretFrom = caretFrom
        out.caretTo = caretTo
        out.caretMix = caretMix.v
        out.caret = caret.v
        out.keycap = keycapPop.v
        out.keycapPress = keycapPress.v
        out.ink = ink.v
        out.hint = if (n == 0) 1f else 1f - ink.v
    }

    private fun kickCaret(on: Boolean) {
        if (!on) {
            caret.to(0f, 100f)
            return
        }
        if (still) caret.set(1f) else caret.blink()
    }

    /** Shows `text` as typed, with `rest` behind it as a suggestion that has not arrived yet. */
    private fun lay(text: String, rest: String): GhostLayout {
        val ranges = if (rest.isNotEmpty()) listOf(text.length to text.length + rest.length) else emptyList()
        val next = makeGhostLayout(font, text + rest, ranges, width, lineHeight, keycap)
        layout = next
        n = text.length
        caretFrom = text.length
        caretTo = text.length
        caretMix.set(1f)
        sweep.set(0f)
        return next
    }

    /** Puts a suggestion behind the caret. `quiet` skips the blur-in and the keycap (a picked chip). */
    fun offer(rest: String, quiet: Boolean = false) {
        val clean = cleanText(rest, multiline)
        if (clean.isEmpty()) return
        ghost = clean
        val next = lay(typed, clean)
        run = 0
        fade.set(1f)
        if (quiet || still) {
            appear.set(9f)
        } else {
            appear.set(0f)
            appear.to(3f, 3000f)
        }
        val words = next.runs.firstOrNull()?.words?.size ?: 1
        if (quiet) {
            keycapPop.set(0f)
        } else {
            keycapPop.to(
                1f,
                if (still) 0f else KEYCAP_MS,
                outBack(1.6f),
                if (still) 0f else words * WORD_GAP * 1000f + 120f,
            )
        }
    }

    private fun propose() {
        timer = -1f
        val ask = suggest ?: return
        if (ghost.isNotEmpty()) return
        val rest = ask(typed)
        if (rest != null) offer(rest)
    }

    private fun arm(delayMs: Float = DEBOUNCE_MS) {
        timer = if (suggest != null) delayMs / 1000f else -1f
    }

    /** The real field's text changed. Text this controller wrote itself is ignored. */
    fun onFieldText(raw: String) {
        val v = cleanText(raw, multiline)
        if (v == typed) return
        val prev = typed
        val rest = ghost
        typed = v
        onChange?.invoke(v)
        timer = -1f
        if (focused) kickCaret(true)

        val added = if (v.length > prev.length && v.startsWith(prev)) v.substring(prev.length) else ""
        if (rest.isNotEmpty() && added.isNotEmpty() && rest.startsWith(added)) {
            // Typing the suggested letters: the layout already has them, the ink just advances.
            ghost = rest.substring(added.length)
            n = v.length
            caretFrom = v.length
            caretTo = v.length
            caretMix.set(1f)
            if (ghost.isEmpty()) {
                run = -1
                keycapPop.set(0f)
                arm()
            }
            return
        }
        if (rest.isNotEmpty()) {
            // Anything else rejects it: it dissolves where the text now puts it.
            ghost = ""
            lay(v, rest)
            fade.to(0f, if (still) 0f else DISSOLVE_MS)
            keycapPop.to(0f, 100f)
        } else {
            lay(v, "")
            run = -1
        }
        arm()
    }

    /**
     Takes the suggestion. `velocity` (dp/s) is the swipe's, and shortens the sweep; `fresh` says
     the suggestion was offered in this same call, so the sweep starts from nothing.
    */
    fun accept(velocity: Float = 0f, fresh: Boolean = false) {
        val rest = ghost
        if (rest.isEmpty()) return
        timer = -1f
        val from = typed.length
        val full = typed + rest
        typed = full
        ghost = ""
        state.setTextAndPlaceCursorAtEnd(full)
        onChange?.invoke(full)
        // The accepted text stays a fully swept suggestion until the next keystroke lays it out as ink.
        val left = if (fresh) 1f else 1f - clamp01(sweep.v)
        val duration = if (still) 0f else max(160f, SWEEP_MS * left / (1f + max(0f, velocity) / 1200f))
        sweep.to(1f, duration, InOutCubic)
        caretFrom = from
        caretTo = full.length
        if (still) {
            caretMix.set(1f)
        } else {
            caretMix.set(0f)
            caretMix.to(1f, JUMP_MS, outBack(JUMP_BACK))
        }
        keycapPop.to(0f, 140f)
        if (focused) kickCaret(true)
        lightTap(view)
        arm(duration + DEBOUNCE_MS)
    }

    /** Replaces the text from outside (a demo handing over, or a reset with ""). */
    fun adopt(text: String, thenSuggest: Boolean = true) {
        timer = -1f
        val v = cleanText(text, multiline)
        typed = v
        ghost = ""
        if (state.text.toString() != v) state.setTextAndPlaceCursorAtEnd(v)
        lay(v, "")
        run = -1
        fade.set(1f)
        keycapPop.set(0f)
        ink.set(1f)
        if (thenSuggest && v.isNotEmpty()) arm()
    }

    fun onFocus() {
        focused = true
        kickCaret(true)
    }

    fun onBlur() {
        focused = false
        kickCaret(false)
    }

    // ---- Swipe right to accept ----------------------------------------------------------------
    private var live = false
    private var reach = SWIPE_DIST

    fun swipeStart() {
        val first = layout.runs.firstOrNull()
        live = run == 0 && first != null && fade.v > 0.5f && sweep.v < 0.02f
        if (live && first != null) reach = max(60f, min(SWIPE_DIST, first.length))
    }

    fun swipeMove(dx: Float) {
        if (live) sweep.set(min(0.94f, max(0f, dx / reach)))
    }

    fun swipeEnd(velocity: Float) {
        if (!live) return
        live = false
        if (sweep.v > SWIPE_ACCEPT || velocity > 600f) {
            accept(velocity)
        } else {
            sweep.spring(0f, -velocity / reach, 22f, 260f)
        }
    }

    fun swipeCancel() {
        if (live) {
            live = false
            sweep.to(0f, 200f, OutCubic)
        }
    }
}

/** The paints of one field, reused every frame. */
private class FieldPaints(font: GhostFont) {
    val ink = Paint(font.paint).apply { color = GhostColors.ink.toArgb() }
    val word = Paint(font.paint)
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /** Skia's sigma to the radius Android's blur takes; cached in steps of half a pixel. */
    fun blur(sigmaPx: Float): BlurMaskFilter? {
        if (sigmaPx < 0.25f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val radius = max(0.5f, (sigmaPx - 0.5f) / 0.57735f)
        val key = (radius * 2f).roundToInt()
        return blurs.getOrPut(key) { BlurMaskFilter(key / 2f, BlurMaskFilter.Blur.NORMAL) }
    }
}

@Composable
internal fun GhostField(
    pose: GhostPose,
    layout: () -> GhostLayout,
    controller: GhostController,
    font: GhostFont,
    width: Dp,
    height: Dp,
    lineHeight: Float,
    placeholder: String,
    multiline: Boolean = false,
    keycap: Boolean = true,
    ghostColor: Color = GhostColors.ghost,
    maxLength: Int,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onTouch: () -> Unit,
    onFocus: () -> Unit,
    onBlur: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val caretH = min(lineHeight, font.descent - font.ascent + 2f)
    val paints = remember(font) { FieldPaints(font) }
    val colors = remember(ghostColor) {
        intArrayOf(GhostColors.ink.toArgb(), GhostColors.accent.toArgb(), GhostColors.front.toArgb(), ghostColor.toArgb())
    }
    val ghostArgb = remember(ghostColor) { ghostColor.toArgb() }
    val transformation = remember(maxLength) { InputTransformation.maxLength(maxLength) }
    val wasFocused = remember { booleanArrayOf(false) }

    LaunchedEffect(controller) {
        snapshotFlow { controller.state.text.toString() }.collect { controller.onFieldText(it) }
    }

    Box(
        modifier
            .size(width, height)
            // Watches every touch before the text field does; it only takes over once a swipe is sure.
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onTouch()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var active = false
                    var failed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            if (active) {
                                change.consume()
                                controller.swipeEnd(tracker.calculateVelocity().x / this.density)
                            }
                            active = false
                            break
                        }
                        val dx = (change.position.x - down.position.x) / this.density
                        val dy = (change.position.y - down.position.y) / this.density
                        if (!active && !failed) {
                            if (abs(dy) > 14f) {
                                failed = true
                            } else if (dx > 12f) {
                                active = true
                                controller.swipeStart()
                            }
                        }
                        if (active) {
                            change.consume()
                            controller.swipeMove(dx)
                        }
                    }
                    if (active) controller.swipeCancel()
                }
            },
    ) {
        BasicText(
            text = placeholder,
            modifier = Modifier.graphicsLayer { alpha = pose.hint },
            style = TextStyle(
                // The drawn text ignores the font scale, so the placeholder that stands in for it does too.
                fontSize = (font.size / fontScale).sp,
                lineHeight = (lineHeight / fontScale).sp,
                color = GhostColors.hint,
                fontWeight = FontWeight.Normal,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
            maxLines = 1,
        )

        Canvas(Modifier.fillMaxSize()) {
            val d = density.density
            val L = layout()
            val inkAlpha = pose.ink
            val count = min(pose.n, L.text.length)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (inkAlpha > 0.001f) {
                    paints.ink.alpha = (inkAlpha * 255f).roundToInt()
                    for (i in 0 until count) {
                        if (L.text[i] == ' ') continue
                        native.drawText(L.text, i, i + 1, L.xs[i] * d, L.ys[i] * d, paints.ink)
                    }
                    val run = pose.run
                    if (run >= 0 && run < L.runs.size) {
                        val r = L.runs[run]
                        val paint = paints.word
                        for (slot in r.words.indices) {
                            val word = r.words[slot]
                            val arrived = easeOutCubic(clamp01((pose.appear - slot * WORD_GAP) / (WORD_MS / 1000f)))
                            val opacity = arrived * pose.fade * inkAlpha
                            if (opacity <= 0.003f) continue
                            val sigma = WORD_BLUR * (1f - arrived) + DISSOLVE_BLUR * (1f - pose.fade)
                            paint.maskFilter = paints.blur(sigma * d)
                            val sweep = pose.sweep
                            if (sweep <= 0f) {
                                // Nothing has been reached: the muted tone alone.
                                paint.shader = null
                                paint.color = ghostArgb
                                if (paint.alpha == 0) continue
                                paint.alpha = (paint.alpha * opacity).roundToInt()
                            } else {
                                val front = (word.x0 + sweep * (r.length + SWEEP_BAND) - word.off) * d
                                // Opaque first: a paint keeps the alpha of its previous colour.
                                paint.color = android.graphics.Color.BLACK
                                paint.shader = LinearGradient(
                                    front - SWEEP_BAND * d,
                                    0f,
                                    front,
                                    0f,
                                    colors,
                                    SWEEP_STOPS,
                                    Shader.TileMode.CLAMP,
                                )
                                paint.alpha = (opacity * 255f).roundToInt()
                            }
                            for (i in max(word.a, pose.n) until word.b) {
                                if (L.text[i] == ' ') continue
                                native.drawText(L.text, i, i + 1, L.xs[i] * d, L.ys[i] * d, paint)
                            }
                        }
                        paint.maskFilter = null
                        paint.shader = null
                    }
                }
            }
            val caretOpacity = pose.caret * inkAlpha
            if (caretOpacity > 0.003f) {
                val last = L.cx.size - 1
                val from = pose.caretFrom.coerceIn(0, last)
                val to = pose.caretTo.coerceIn(0, last)
                val mix = pose.caretMix
                val x = L.cx[from] + (L.cx[to] - L.cx[from]) * mix - CARET_W / 2f
                val y = L.cy[from] + (L.cy[to] - L.cy[from]) * clamp01(mix) + (lineHeight - caretH) / 2f
                drawRoundRect(
                    GhostColors.accent,
                    Offset(x * d, y * d),
                    Size(CARET_W * d, caretH * d),
                    CornerRadius(d),
                    alpha = caretOpacity,
                )
            }
        }

        // The real input: invisible, it holds the text, the focus and the keyboard.
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(Color.Transparent, Color.Transparent),
        ) {
            BasicTextField(
                state = controller.state,
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { focus ->
                        if (focus.isFocused != wasFocused[0]) {
                            wasFocused[0] = focus.isFocused
                            if (focus.isFocused) {
                                onTouch()
                                controller.onFocus()
                                onFocus()
                            } else {
                                controller.onBlur()
                                onBlur()
                            }
                        }
                    },
                inputTransformation = transformation,
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = (font.size / fontScale).sp,
                    lineHeight = (lineHeight / fontScale).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                keyboardOptions = keyboardOptions.copy(autoCorrectEnabled = false),
                lineLimits = if (multiline) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(Color.Transparent),
            )
        }

        if (keycap) Keycap(pose, layout, controller, lineHeight, onTouch)
    }
}

/** The "Tab →" chip beside a suggestion: a key with a little depth that accepts it. */
@Composable
private fun Keycap(
    pose: GhostPose,
    layout: () -> GhostLayout,
    controller: GhostController,
    lineHeight: Float,
    onTouch: () -> Unit,
) {
    val shape = remember { RoundedCornerShape(8.dp) }
    Box(
        Modifier
            .size(KEYCAP_W.dp, KEYCAP_H.dp)
            .graphicsLayer {
                val L = layout()
                val r = L.runs.getOrNull(pose.run)
                val shown = r != null && pose.keycap > 0.01f
                alpha = if (shown) clamp01(pose.keycap * 2f) * pose.fade * pose.ink else 0f
                translationX = if (shown) r.chipX * density else -4000f * density
                translationY = ((r?.chipY ?: 0f) + (lineHeight - KEYCAP_H) / 2f) * density
                val s = 0.7f + 0.3f * pose.keycap
                scaleX = s
                scaleY = s
            }
            .semantics {
                role = Role.Button
                contentDescription = "Accept suggestion"
            }
            .pointerInput(controller) {
                detectTapGestures(
                    onPress = {
                        controller.keycapPress.to(1f, 60f)
                        tryAwaitRelease()
                        controller.keycapPress.to(0f, 140f)
                    },
                    onTap = {
                        onTouch()
                        controller.accept()
                    },
                )
            },
    ) {
        Box(
            Modifier
                .offset(y = 2.dp)
                .size(KEYCAP_W.dp, KEYCAP_H.dp)
                .graphicsLayer { alpha = 1f - 0.7f * pose.keycapPress }
                .dropShadow(shape, Shadow(radius = 10.dp, color = Color(21, 26, 36).copy(alpha = 0.12f), offset = DpOffset(0.dp, 4.dp)))
                .background(Color(0xFFC9CDD6), shape),
        )
        Row(
            Modifier
                .size(KEYCAP_W.dp, KEYCAP_H.dp)
                .graphicsLayer { translationY = 1.5f * pose.keycapPress * density }
                .background(Color.White, shape)
                .border(0.5.dp, Color(0xFFD5D8DF), shape),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "Tab",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5B6170),
                    letterSpacing = 0.1.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
            BasicText(
                text = "→",
                modifier = Modifier.offset(y = (-1).dp),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GhostColors.accent,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}
