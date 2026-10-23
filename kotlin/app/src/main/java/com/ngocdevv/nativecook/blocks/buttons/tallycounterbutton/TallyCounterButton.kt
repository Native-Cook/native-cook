package com.ngocdevv.nativecook.blocks.buttons.tallycounterbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// All in dp, the same numbers as the React Native and SwiftUI versions.
private const val WIDTH = 164f
private const val HEIGHT = 64f
private const val KNOB_R = 26f

// Distance of the − and + glyphs from the pill's ends.
private const val GLYPH_INSET = 33f
private const val GLYPH_ARM = 8.5f
private const val FLASH_R = 15f

// How far the knob can be dragged sideways (±1) and pulled down (reset).
private const val MAX_X = 48f
private const val MAX_Y = 38f

// The whole pill gives a little when the knob is pulled down.
private const val PILL_GIVE = 7f
private const val BALL_R = 10f

// The canvas overflows the button by this much on every side to fit the pulled knob, its shadow and the ball.
private val BLEED = 64.dp
private const val MIN = 0
private const val MAX = 99
private const val DRAG_SLOP = 6f
private const val COMMIT = 0.6f
private const val GHOST_RESUME_MS = 3000L

private const val CX = WIDTH / 2
private const val CY = HEIGHT / 2
private const val REST_X = CX + 4
private const val REST_Y = CY + 6
private const val AWAY_Y = HEIGHT + 60

private val EaseInOut = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

// Overshoots a little: the knob snaps back like it is on a spring.
private val EaseBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val DigitStyle = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)

private enum class TallySide { Minus, Plus }

/** Every animated number of the button, shared by the finger and the ghost. */
private class TallyMotion {
    val kx = Animatable(0f)
    val ky = Animatable(0f)
    val press = Animatable(0f)
    val flashMinus = Animatable(0f)
    val flashPlus = Animatable(0f)
    val ballX = Animatable(REST_X)
    val ballY = Animatable(AWAY_Y)
    val ballAlpha = Animatable(0f)
    val ballScale = Animatable(1f)

    val lift = SoftRoundRect(rgba(0, 0, 0, 0.55f), sigma = 14f)
    val rest = SoftRoundRect(rgba(0, 0, 0, 0.4f), sigma = 3f)

    fun pressKnob(scope: CoroutineScope, down: Boolean) {
        scope.launch { press.animateTo(if (down) 1f else 0f, tween(if (down) 130 else 260, easing = EaseOutStrong)) }
    }

    fun releaseKnob(scope: CoroutineScope) {
        scope.launch { kx.animateTo(0f, tween(380, easing = EaseBack)) }
        scope.launch { ky.animateTo(0f, tween(380, easing = EaseBack)) }
        pressKnob(scope, false)
    }

    fun flash(scope: CoroutineScope, side: TallySide, down: Boolean) {
        val target = if (side == TallySide.Minus) flashMinus else flashPlus
        scope.launch { target.animateTo(if (down) 0.92f else 0f, tween(if (down) 90 else 380, easing = EaseOutStrong)) }
    }

    fun moveBall(scope: CoroutineScope, x: Float, y: Float, ms: Int, easing: Easing = EaseInOut) {
        scope.launch { ballX.animateTo(x, tween(ms, easing = easing)) }
        scope.launch { ballY.animateTo(y, tween(ms, easing = easing)) }
    }

    fun touchBall(scope: CoroutineScope, down: Boolean) {
        scope.launch { ballScale.animateTo(if (down) 0.82f else 1f, tween(120, easing = EaseOutStrong)) }
    }

    fun hideBall(scope: CoroutineScope) {
        scope.launch { ballAlpha.animateTo(0f, tween(160, easing = LinearEasing)) }
        scope.launch { ballScale.snapTo(1f) }
    }
}

/**
 * Stepper pill "−  0  +" whose number sits on a draggable knob. Tap − or + (or the knob) to count,
 * drag the knob sideways and let go for ±1, pull it down and let go to reset.
 *
 * @param value Controlled count. Leave null to let the button keep its own.
 * @param autoPlay Plays the reference's demo by itself: a ghost fingertip taps, drags and resets the counter.
 */
@Composable
fun TallyCounterButton(
    value: Int? = null,
    onValueChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    contentDescription: String = "Counter",
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember { TallyMotion() }

    var inner by remember { mutableIntStateOf(0) }
    val count = value ?: inner
    // The count as the gesture and the ghost see it, updated the moment either changes it.
    val current = remember { mutableIntStateOf(count) }
    SideEffect { current.intValue = count }
    val controlled by rememberUpdatedState(value != null)
    val onChange by rememberUpdatedState(onValueChange)
    val setCount = remember {
        { next: Int ->
            val clamped = next.coerceIn(MIN, MAX)
            if (clamped != current.intValue) {
                current.intValue = clamped
                if (!controlled) inner = clamped
                onChange(clamped)
            }
        }
    }

    var touching by remember { mutableStateOf(false) }
    var ghostPaused by remember { mutableStateOf(false) }
    var resumeToken by remember { mutableIntStateOf(0) }
    val ghostActive = autoPlay && !reducedMotion && !ghostPaused

    LaunchedEffect(resumeToken) {
        if (resumeToken == 0) return@LaunchedEffect
        delay(GHOST_RESUME_MS)
        ghostPaused = false
    }

    // Ghost fingertip: replays the reference's demo while autoPlay is on.
    LaunchedEffect(ghostActive) {
        if (!ghostActive) return@LaunchedEffect
        val ghost = this

        suspend fun tapKnob() {
            motion.touchBall(ghost, true)
            motion.pressKnob(ghost, true)
            delay(170)
            setCount(current.intValue + 1)
            motion.touchBall(ghost, false)
            motion.pressKnob(ghost, false)
            delay(360)
        }

        suspend fun tapSide(side: TallySide) {
            val x = if (side == TallySide.Minus) GLYPH_INSET + 4 else WIDTH - GLYPH_INSET + 4
            motion.moveBall(ghost, x, CY + 5, 420)
            delay(520)
            repeat(2) {
                motion.touchBall(ghost, true)
                motion.flash(ghost, side, true)
                delay(170)
                setCount(current.intValue + if (side == TallySide.Minus) -1 else 1)
                motion.touchBall(ghost, false)
                motion.flash(ghost, side, false)
                delay(420)
            }
        }

        suspend fun drag(dx: Float, dy: Float) {
            motion.moveBall(ghost, REST_X, REST_Y, 420)
            delay(480)
            motion.touchBall(ghost, true)
            motion.pressKnob(ghost, true)
            delay(160)
            launch { motion.kx.animateTo(dx, tween(420, easing = EaseOutStrong)) }
            launch { motion.ky.animateTo(dy, tween(420, easing = EaseOutStrong)) }
            motion.moveBall(ghost, REST_X + dx, REST_Y + dy, 420, EaseOutStrong)
            delay(700)
            setCount(if (dy > 0) MIN else current.intValue + sign(dx).toInt())
            motion.touchBall(ghost, false)
            motion.releaseKnob(ghost)
            delay(240)
        }

        try {
            while (true) {
                delay(900)
                motion.ballX.snapTo(REST_X)
                motion.ballY.snapTo(AWAY_Y)
                launch { motion.ballAlpha.animateTo(1f, tween(250, easing = LinearEasing)) }
                motion.moveBall(ghost, REST_X, REST_Y, 560)
                delay(660)
                tapKnob()
                tapKnob()
                drag(MAX_X, 0f)
                drag(MAX_X, 0f)
                drag(-MAX_X, 0f)
                tapSide(TallySide.Plus)
                tapSide(TallySide.Minus)
                drag(0f, MAX_Y)
                motion.moveBall(ghost, REST_X, AWAY_Y, 520)
                launch { motion.ballAlpha.animateTo(0f, tween(420, easing = LinearEasing)) }
                delay(1100)
            }
        } finally {
            // Cancelled: the user took over or the button went away. Let go of whatever the ghost held.
            motion.hideBall(scope)
            if (!touching) {
                motion.releaseKnob(scope)
                motion.flash(scope, TallySide.Minus, false)
                motion.flash(scope, TallySide.Plus, false)
            }
        }
    }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                stateDescription = count.toString()
                customActions = listOf(
                    CustomAccessibilityAction("Increment") {
                        setCount(current.intValue + 1)
                        true
                    },
                    CustomAccessibilityAction("Decrement") {
                        setCount(current.intValue - 1)
                        true
                    },
                )
            }
            // One gesture for the whole pill, split into knob / − / + by where the finger lands.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val startX = down.position.x / density
                    val startY = down.position.y / density

                    // Take over from the ghost, wherever it was.
                    touching = true
                    ghostPaused = true
                    motion.hideBall(scope)
                    motion.flash(scope, TallySide.Minus, false)
                    motion.flash(scope, TallySide.Plus, false)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

                    val onKnob = hypot(startX - CX, startY - CY) <= KNOB_R + 4
                    val side = if (startX < CX) TallySide.Minus else TallySide.Plus
                    if (onKnob) {
                        motion.pressKnob(scope, true)
                    } else {
                        motion.releaseKnob(scope)
                        motion.flash(scope, side, true)
                    }

                    // null until the finger has moved past the slop, then true for a sideways drag.
                    var horizontal: Boolean? = null
                    var dx = 0f
                    var dy = 0f
                    var commit = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            commit = true
                            break
                        }
                        change.consume()
                        if (!onKnob) continue
                        dx = (change.position.x - down.position.x) / density
                        dy = (change.position.y - down.position.y) / density
                        if (horizontal == null) {
                            if (max(abs(dx), abs(dy)) < DRAG_SLOP) continue
                            horizontal = abs(dx) >= abs(dy)
                        }
                        if (horizontal == true) {
                            val to = dx.coerceIn(-MAX_X, MAX_X)
                            scope.launch { motion.kx.snapTo(to) }
                        } else {
                            val to = dy.coerceIn(0f, MAX_Y)
                            scope.launch { motion.ky.snapTo(to) }
                        }
                    }

                    if (onKnob) {
                        if (commit) {
                            when (horizontal) {
                                null -> setCount(current.intValue + 1)
                                true -> if (abs(dx) > MAX_X * COMMIT) setCount(current.intValue + sign(dx).toInt())
                                false -> if (dy > MAX_Y * COMMIT) setCount(MIN)
                            }
                        }
                        motion.releaseKnob(scope)
                    } else {
                        if (commit) setCount(current.intValue + if (side == TallySide.Minus) -1 else 1)
                        motion.flash(scope, side, false)
                    }
                    touching = false
                    resumeToken++
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ -> drawTally(motion, x, y) }
            }
        }
        // The number rides on the knob, above the ball like in the reference.
        BasicText(
            text = count.toString(),
            style = DigitStyle,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                translationX = motion.kx.value * density
                translationY = motion.ky.value * density
                val scale = 1f - 0.04f * motion.press.value
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

private fun DrawScope.drawTally(m: TallyMotion, x: Float, y: Float) {
    val cx = x + CX
    val cy = y + CY
    val kx = m.kx.value
    val ky = m.ky.value
    val press = m.press.value
    val flashMinus = m.flashMinus.value
    val flashPlus = m.flashPlus.value
    // -1..1 sideways, 0..1 downwards.
    val side = kx / MAX_X
    val down = ky / MAX_Y

    translate(top = PILL_GIVE * down) {
        val pill = CornerRadius(HEIGHT / 2)
        drawRoundRect(Color(0xFF282828), Offset(x, y), Size(WIDTH, HEIGHT), pill)
        drawRoundRect(
            Color(0xFF1E1E1E), Offset(x, y), Size(WIDTH, HEIGHT), pill,
            alpha = max(abs(side), down).coerceIn(0f, 1f),
        )

        val minusX = x + GLYPH_INSET
        val plusX = x + WIDTH - GLYPH_INSET
        drawCircle(Color.White, FLASH_R, Offset(minusX, cy), alpha = flashMinus)
        drawCircle(Color.White, FLASH_R, Offset(plusX, cy), alpha = flashPlus)

        // The glyph the knob moves away from dims, the one it approaches lights up; both go when
        // pulling down. A tapped glyph turns dark on its white flash.
        drawGlyph(minusX, cy, plus = false, GlyphIdle, (1f - 0.75f * max(side, 0f) - down).coerceIn(0f, 1f))
        drawGlyph(minusX, cy, plus = false, GlyphLit, max(-side, 0f).coerceIn(0f, 1f))
        drawGlyph(minusX, cy, plus = false, GlyphTapped, flashMinus)
        drawGlyph(plusX, cy, plus = true, GlyphIdle, (1f - 0.75f * max(-side, 0f) - down).coerceIn(0f, 1f))
        drawGlyph(plusX, cy, plus = true, GlyphLit, max(side, 0f).coerceIn(0f, 1f))
        drawGlyph(plusX, cy, plus = true, GlyphTapped, flashPlus)

        val cross = down.coerceIn(0f, 1f)
        drawLine(CrossColor, Offset(cx - 6, cy - 6), Offset(cx + 6, cy + 6), strokeWidth = 1.5f, alpha = cross)
        drawLine(CrossColor, Offset(cx + 6, cy - 6), Offset(cx - 6, cy + 6), strokeWidth = 1.5f, alpha = cross)
    }

    // A tight shadow at rest and a wide soft one while the knob is held.
    val knobX = cx + kx
    val knobY = cy + ky
    drawIntoCanvas {
        m.lift.draw(it.nativeCanvas, knobX - KNOB_R, knobY + 14 - KNOB_R, KNOB_R * 2, KNOB_R * 2, KNOB_R, alpha = press)
        m.rest.draw(it.nativeCanvas, knobX - KNOB_R, knobY + 2 - KNOB_R, KNOB_R * 2, KNOB_R * 2, KNOB_R)
    }
    drawCircle(Color(0xFF3B3B3B), KNOB_R * (1f - 0.04f * press), Offset(knobX, knobY))

    val ballAlpha = m.ballAlpha.value
    if (ballAlpha > 0f) {
        val bx = x + m.ballX.value
        val by = y + m.ballY.value
        val r = BALL_R * m.ballScale.value
        drawCircle(
            brush = Brush.linearGradient(
                listOf(rgba(178, 178, 178, 0.9f), rgba(84, 84, 84, 0.9f)),
                start = Offset(bx - 3, by - r),
                end = Offset(bx + 3, by + r),
            ),
            radius = r,
            center = Offset(bx, by),
            alpha = ballAlpha,
        )
        drawCircle(rgba(255, 255, 255, 0.22f), r - 0.5f, Offset(bx, by), alpha = ballAlpha, style = Stroke(1f))
    }
}

private val GlyphIdle = Color(0xFF7A7A7A)
private val GlyphLit = Color(0xFFF2F2F2)
private val GlyphTapped = Color(0xFF4A4A4A)
private val CrossColor = Color(0xFF9A9A9A)

private fun DrawScope.drawGlyph(cx: Float, cy: Float, plus: Boolean, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    drawLine(color, Offset(cx - GLYPH_ARM, cy), Offset(cx + GLYPH_ARM, cy), strokeWidth = 1.5f, alpha = alpha)
    if (plus) drawLine(color, Offset(cx, cy - GLYPH_ARM), Offset(cx, cy + GLYPH_ARM), strokeWidth = 1.5f, alpha = alpha)
}
